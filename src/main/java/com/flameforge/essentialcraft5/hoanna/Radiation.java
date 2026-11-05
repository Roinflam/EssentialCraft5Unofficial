package com.flameforge.essentialcraft5.hoanna;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.api.MRUResistance;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.corruption.CorruptionTicker;
import com.flameforge.essentialcraft5.effect.RadiationEffect;
import com.flameforge.essentialcraft5.item.creative.CreativeWardItem;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECBaubles;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHoannaHazards;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.common.util.FakePlayer;

/**
 * 海纳尔辐射（原作 {@code RadiationManager}，78 行；第六批 6a M4，batch6a.md §5.4）：剂量存在玩家数据 {@code PlayerData.radiation}
 * （已存盘、死亡不丢、变化后 10 tick 内同步给本人）。压生命上限直接按剂量算（{@link #pulse}），不挂药水效果。
 * <p>
 * 每 {@value #CYCLE} tick 结算一次（{@code HoannaHazards.tickPlayer}，按玩家 tickCount 天然错开）：
 * <ol>
 *   <li><b>积累</b>（在海纳尔、不是创造 / 旁观、{@code general.radiation.enabled}）：{@code floor((区块底数 + 群系加成) × 海纳尔辐射易伤)}，
 *       戴 μ 指环再 +{@value #MU_GAIN}（不乘易伤，原作 {@code ItemBaublesSpecial} L162 每 tick +1）。</li>
 *   <li><b>消退</b>（加完以后剂量 &gt; 0）：在海纳尔 −{@value #DECAY_IN_HOANNA}，不在 −{@value #DECAY_OUTSIDE}；不在海纳尔又戴 λ 指环再 −{@value #LAMBDA_PURGE}
 *       （原作 L156 每 tick −3）。</li>
 *   <li><b>收尾</b>：撤掉旧版留在身上的「辐射」药水效果，剂量 &gt; 0 时补手册步骤（{@link #refresh}）。</li>
 * </ol>
 * 等级（放大值）= 剂量 / {@value #PER_LEVEL}。原作是按剂量挂一个无限时长的「辐射」药水效果，由效果每 tick 压生命上限；
 * 那样不死图腾、/effect clear、净化类饰品、「免疫负面效果」的模组都能把它去掉或拦下，而剂量还在。现在改成服务端每 tick 直接按剂量判
 * （{@link #pulse}，判法同原作），客户端在快捷栏左边画图标（{@code client/AfflictionHud}），什么都去不掉，只能等剂量退下去。
 * 区块底数照原作 {@code new Random(Long.parseLong(|cx|×128 + "" + |cz|×128)).nextInt(6)}，改成不拼字符串、不 new Random 的等价算法
 * （{@link #chunkBase}，两端都能算，客户端显示「此地 +N / 秒」用）；群系加成走两个群系标签（数据包可调）：
 * {@link #PLUS_2}（海纳尔沙漠 +2）、{@link #PLUS_6}（恐惧之地 +6），原作按群系对象比较。
 * <p>
 * 剂量上限 {@value #MAX}（等级 ≤ 100，放大值一个字节放得下；原作没有上限，ξ 打人能把 int 加爆成负数），所有写入夹在 [0, MAX]；
 * FakePlayer 读 0、写忽略（同原作）。
 */
public final class Radiation {

    /** 每多少剂量升一级 */
    public static final int PER_LEVEL = 10_000;
    /** 剂量上限 */
    public static final int MAX = 1_000_000;
    /** 结算间隔（tick） */
    public static final int CYCLE = 20;
    public static final int DECAY_IN_HOANNA = 1;
    public static final int DECAY_OUTSIDE = 5;
    /** λ 指环：不在海纳尔时每次结算额外消退 */
    public static final int LAMBDA_PURGE = 60;
    /** μ 指环：在海纳尔每次结算额外积累（不乘易伤） */
    public static final int MU_GAIN = 20;
    /** 区块底数的范围：nextInt(6) = 0~5 */
    public static final int CHUNK_BASE_BOUND = 6;

    /** 群系加成 +2（海纳尔沙漠；{@code data/essentialcraft5/tags/worldgen/biome/hoanna_radiation_plus_2.json}） */
    public static final TagKey<Biome> PLUS_2 = TagKey.create(Registries.BIOME, EssentialCraft5.id("hoanna_radiation_plus_2"));
    /** 群系加成 +6（恐惧之地；{@code hoanna_radiation_plus_6.json}） */
    public static final TagKey<Biome> PLUS_6 = TagKey.create(Registries.BIOME, EssentialCraft5.id("hoanna_radiation_plus_6"));

    private static final long LCG_MULTIPLIER = 0x5DEECE66DL;
    private static final long LCG_ADDEND = 0xBL;
    private static final long LCG_MASK = (1L << 48) - 1;

    private Radiation() {
    }

    // ------------------------------------------------------------------ 读写

    /** 玩家现在的剂量（没有数据、假玩家 0） */
    public static int get(Player player) {
        if (player instanceof FakePlayer) {
            return 0;
        }
        IPlayerData data = PlayerDataEvents.get(player);
        return data == null ? 0 : data.getRadiation();
    }

    /** 设剂量（夹在 [0, MAX]；假玩家忽略）；返回设完的值 */
    public static int set(Player player, long dose) {
        if (player instanceof FakePlayer) {
            return 0;
        }
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            return 0;
        }
        int value = clamp(dose);
        data.setRadiation(value);
        return value;
    }

    /**
     * 加剂量（可以是负数；夹在 [0, MAX]）；返回加完的值。amount 先夹到 [−MAX, MAX] 再加：调用方传 {@code Long.MAX_VALUE} 之类的巨大值
     * （例：{@code (long) (Float.MAX_VALUE × 1000)} 饱和成 Long.MAX_VALUE）时，原剂量 &gt; 0 也不会 long 溢出成负数、被夹成 0（6a 审查 M4-2 同根）。
     */
    public static int add(Player player, long amount) {
        if (player instanceof FakePlayer) {
            return 0;
        }
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            return 0;
        }
        // 带着无尽护佑护符（只放创造栏，item/creative/CreativeWardItem）时剂量保持 0：毒雾、ξ 命中都加不上去
        if (CreativeWardItem.protects(player)) {
            if (data.getRadiation() != 0) {
                data.setRadiation(0);
            }
            return 0;
        }
        int value = sum(data.getRadiation(), amount);
        data.setRadiation(value);
        return value;
    }

    /**
     * 第十三批 13d 的新辐射来源（batch13.md §6.1 挖辐晶簇、§6.2 地脉风暴）：加 {@code round(base × max(易伤, floor))}，返回加完的剂量。
     * 易伤和普通辐射积累同一条链（{@link MRUResistance#vulnerability(Player, MRUResistance.Type)} 的 {@code HOANNA_RADIATION}，
     * 已含盔甲、饰品、古卷、矩阵阶和 13b 虚空全套 2 阶的 ×0.5 倍率），所以 floor 0.5 = 「任何盔甲都至少吃一半」。
     * 只对服务端的真玩家生效：客户端、假玩家、创造 / 旁观（同普通辐射积累的口径）、{@code general.radiation.enabled} 关掉、带无尽护佑护符时
     * 什么都不加，返回现在的剂量；base ≤ 0 也不加。
     *
     * @param base  这一次的基础剂量（挖辐晶簇 1 500、风暴每秒 2）
     * @param floor 易伤的下限（0~1；13d 都传 0.5）
     */
    public static int addFloored(Player player, long base, float floor) {
        if (!(player instanceof ServerPlayer) || player instanceof FakePlayer) {
            return get(player);
        }
        if (base <= 0L || player.isCreative() || player.isSpectator() || !HoannaHazardsConfig.radiationEnabled()) {
            return get(player);
        }
        return add(player, flooredAmount(base, MRUResistance.vulnerability(player, MRUResistance.Type.HOANNA_RADIATION), floor));
    }

    /** 纯函数：{@code round(base × max(vulnerability, floor))}（非有限的易伤按 1 算；结果夹在 [0, MAX]） */
    public static long flooredAmount(long base, float vulnerability, float floor) {
        float v = Float.isFinite(vulnerability) ? Math.max(0F, vulnerability) : 1F;
        float f = Float.isFinite(floor) ? Math.max(0F, floor) : 0F;
        long amount = Math.round((double) Math.max(0L, base) * Math.max(v, f));
        return Math.min(MAX, amount);
    }

    /** {@code clamp(dose + amount)}，amount 先夹到 [−MAX, MAX]，不会溢出（纯函数，GameTest 测） */
    public static int sum(int dose, long amount) {
        long bounded = Math.max(-(long) MAX, Math.min(MAX, amount));
        return clamp((long) dose + bounded);
    }

    public static int clamp(long dose) {
        return (int) Math.max(0L, Math.min(MAX, dose));
    }

    /** 剂量对应的放大值（0 = 「辐射」不带罗马数字、不压上限，1 = 辐射 II …） */
    public static int level(int dose) {
        return Math.max(0, dose) / PER_LEVEL;
    }

    // ------------------------------------------------------------------ 积累与消退（纯函数，GameTest 直接测）

    /**
     * 区块底数 0~5：等价于原作 {@code new java.util.Random(Long.parseLong(|cx|×128 + "" + |cz|×128)).nextInt(6)}
     * （种子 = a × 10^位数(b) + b，再照 {@code java.util.Random} 的 48 位线性同余走一次 {@code nextInt(6)}）。
     * 核对值（真 java.util.Random 跑的）：(0,0)=0、(1,0)=2、(0,1)=1、(1,1)=2、(−1,0)=2、(5,−3)=1、(100,200)=2、(−1234,5678)=1、(1875000,1875000)=1。
     */
    public static int chunkBase(int chunkX, int chunkZ) {
        long a = Math.abs((long) chunkX) * 128L;
        long b = Math.abs((long) chunkZ) * 128L;
        long multiplier = 10L;
        while (multiplier <= b) {
            multiplier *= 10L;
        }
        long seed = ((a * multiplier + b) ^ LCG_MULTIPLIER) & LCG_MASK;
        while (true) {
            seed = (seed * LCG_MULTIPLIER + LCG_ADDEND) & LCG_MASK;
            int bits = (int) (seed >>> 17);
            int value = bits % CHUNK_BASE_BOUND;
            if (bits - value + (CHUNK_BASE_BOUND - 1) >= 0) {
                return value;
            }
        }
    }

    /** 群系加成（两个标签可以同时挂，加起来） */
    public static int biomeBonus(Holder<Biome> biome) {
        int bonus = 0;
        if (biome.is(PLUS_2)) {
            bonus += 2;
        }
        if (biome.is(PLUS_6)) {
            bonus += 6;
        }
        return bonus;
    }

    /** 一次结算的区块积累：{@code (int) ((底数 + 加成) × 易伤)}（原作 {@code (int)(rndRad * getGenResistance(2, player))}，float 乘、截断） */
    public static int gain(int chunkBase, int biomeBonus, float vulnerability) {
        return Math.max(0, (int) ((chunkBase + biomeBonus) * vulnerability));
    }

    /**
     * 一次结算：先加 gain（已含 μ），加完剂量 &gt; 0 就按在不在海纳尔消退，不在海纳尔又戴 λ 再额外消退；结果夹在 [0, MAX]。
     *
     * @param lambda 戴着 λ 指环（在海纳尔里无效，这里再判一次）
     */
    public static int step(int dose, int gain, boolean inHoanna, boolean lambda) {
        long next = (long) dose + gain;
        if (next > 0) {
            next -= inHoanna ? DECAY_IN_HOANNA : DECAY_OUTSIDE;
        }
        if (lambda && !inHoanna) {
            next -= LAMBDA_PURGE;
        }
        return clamp(next);
    }

    /**
     * 这名玩家此刻所在位置一次结算的积累（区块 + 群系 × 易伤 + μ）；不在海纳尔、创造 / 旁观、辐射关掉时 0。两端都能调
     * （客户端用同步过来的群系标签、饰品、盔甲算，给背包里的「此地 +N / 秒」用）。
     */
    public static int gainHere(Player player) {
        Level level = player.level();
        if (!ECHoanna.isHoanna(level) || player.isCreative() || player.isSpectator() || !HoannaHazardsConfig.radiationEnabled()
                || CreativeWardItem.protects(player)) {
            return 0;
        }
        int base = chunkBase(player.chunkPosition().x, player.chunkPosition().z);
        int bonus = biomeBonus(level.getBiome(player.blockPosition()));
        int gain = gain(base, bonus, MRUResistance.vulnerability(player, MRUResistance.Type.HOANNA_RADIATION));
        if (CuriosCompat.has(player, ECBaubles.HOANNA_ATTUNED)) {
            gain += MU_GAIN;
        }
        return gain;
    }

    /** 此刻剂量每秒（每次结算）的净变化：{@code step(剂量, 积累, …) − 剂量}；给背包文字用，两端都能调 */
    public static int netPerCycle(Player player, int dose) {
        boolean inHoanna = ECHoanna.isHoanna(player.level());
        boolean lambda = !inHoanna && dose > 0 && CuriosCompat.has(player, ECBaubles.PURGES_RADIATION);
        return step(dose, gainHere(player), inHoanna, lambda) - dose;
    }

    // ------------------------------------------------------------------ 服务端结算

    /**
     * 服务端每 {@value #CYCLE} tick 调一次（{@code HoannaHazards.tickPlayer}；GameTest 直接调）：积累、消退、收尾。
     * 返回结算后的剂量。
     */
    public static int tick(Player player, IPlayerData data, boolean inHoanna) {
        int dose = data.getRadiation();
        int gain = inHoanna ? gainHere(player) : 0;
        boolean lambda = !inHoanna && dose > 0 && CuriosCompat.has(player, ECBaubles.PURGES_RADIATION);
        // 无尽护佑护符：剂量保持 0（已有的也清掉）
        int next = CreativeWardItem.protects(player) ? 0 : step(dose, gain, inHoanna, lambda);
        if (next != dose) {
            data.setRadiation(next);
        }
        refresh(player, next);
        return next;
    }

    /** 指令改完剂量后立刻收尾（不用等下一次结算） */
    public static void refresh(Player player) {
        refresh(player, get(player));
    }

    /**
     * 撤掉旧版挂在身上的「辐射」药水效果（换 jar 前存下的；现在不再加，留着只会在效果栏里多一个「∞」）；
     * 剂量 &gt; 0 且辐射开着时补上手册步骤 guide/hoanna/radiation（以前靠「身上出现辐射效果」的进度条件触发）。
     */
    public static void refresh(Player player, int dose) {
        MobEffect legacy = ECHoannaHazards.RADIATION.get();
        if (player.hasEffect(legacy)) {
            player.removeEffect(legacy);
        }
        if (dose > 0 && player instanceof ServerPlayer serverPlayer && HoannaHazardsConfig.radiationEnabled()) {
            CorruptionTicker.award(serverPlayer, ECHoannaHazards.GUIDE_RADIATION);
        }
    }

    /**
     * 服务端每 tick 调一次（{@code HoannaHazards.onHazardsPlayerTick}；GameTest 直接调）：放大值 = 剂量 / 1 万，
     * 以 {@code nextInt(16) < 放大值} 的概率把生命压到上限（{@link RadiationEffect#hits} / {@link RadiationEffect#capHealth}，
     * 判法和原作药水效果的每 tick 判定一样，戴 δ 压的级数减半）。不到 1 万、辐射关掉、假玩家、死了都不判。
     *
     * @return 这一 tick 判中没有
     */
    public static boolean pulse(Player player) {
        if (player instanceof FakePlayer || !player.isAlive()) {
            return false;
        }
        int amplifier = level(get(player));
        if (amplifier <= 0 || !HoannaHazardsConfig.radiationEnabled() || !RadiationEffect.hits(player.getRandom(), amplifier)) {
            return false;
        }
        RadiationEffect.capHealth(player, amplifier);
        return true;
    }
}
