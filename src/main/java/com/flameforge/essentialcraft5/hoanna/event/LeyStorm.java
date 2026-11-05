package com.flameforge.essentialcraft5.hoanna.event;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.advancement.ECAdvancements;
import com.flameforge.essentialcraft5.entity.LeyStarfallEntity;
import com.flameforge.essentialcraft5.hoanna.HoannaHazards;
import com.flameforge.essentialcraft5.hoanna.HoannaHazardsConfig;
import com.flameforge.essentialcraft5.hoanna.HoannaTide;
import com.flameforge.essentialcraft5.hoanna.Radiation;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHoannaMaterials;
import com.flameforge.essentialcraft5.registry.ECHoannaStorm;
import com.flameforge.essentialcraft5.util.ECItemEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 地脉风暴（第五种天灾 {@link HoannaEvent#LEY_STORM}，第十三批 13d，batch13.md §6.2、D27 A；HS §2.2.3）自己的规则。开始 / 结束、倒计时、
 * 存档和事件包仍走 {@link HoannaEvents} / {@link HoannaEventData}，这里放风暴才有的几条：
 * <ul>
 *   <li><b>掷骰</b>（{@link #rollDusk}）：只在<b>大潮夜</b>（{@link HoannaTide#isSpringTide}）的黄昏、海纳尔的日内刻跨过 {@value #DUSK} 的那一刻，
 *       单独掷一次 {@code ley_storm_chance × chance_multiplier}；已经有别的天灾就不掷（{@link HoannaEvents#duskRoll}）。口径同日阵的黎明掷：
 *       海纳尔里有人、上一刻也有人（连续看到的）才算；没人时错过的黄昏不补。常规掷骰的四种和频率一点不动（风暴不在 {@code rollable()} 里）。
 *       八天三个大潮夜 × 0.3，平均每游戏日约 11%，四种天灾加上它约 28% / 日。</li>
 *   <li><b>时长</b>：6 000 刻，截到黎明（{@link #ticksToDawn}；开始时记下截止的黎明，没人时停住、/time 往后拨也按它结束）。</li>
 *   <li><b>落晶</b>（{@link #trySpawnStarfall}）：每 {@code ley_storm_starfall_interval}（100）刻，对海纳尔里头顶露天（同日阵的判定
 *       {@link HoannaHazards#exposedToSky}）的生存 / 冒险真玩家掷一次 {@value #STARFALL_CHANCE}：在 {@value #STARFALL_MIN_DISTANCE}~{@value #STARFALL_MAX_DISTANCE}
 *       格外（水平）随机一列，落点 = 那一列的 MOTION_BLOCKING 高度（区块没加载、不能 tick 实体就不落，不加载区块；那一列顶上是液体也不落），
 *       从地表上方 {@value #STARFALL_DROP_HEIGHT} 格缓缓落到地表上方一格（下落只是客户端的画法，服务端一生成就在悬停高度、落地以前碰到不收，
 *       见 {@link LeyStarfallEntity}）。每人名下同时 ≤ {@code ley_storm_starfall_per_player}（8），计数表按 UUID，实体加进 / 离开世界时登记 / 摘掉
 *       （{@link #track}、{@link #untrack}），不做实体查询；判名额时过期的不算（落晶停刻时自己删不掉，不能让它占着名额）。</li>
 *   <li><b>拾取</b>（{@link #collect}）：真玩家碰到就收，地脉结晶 1 个，满月（潮位 1.0）2 个，放不下的掉在脚下；发手册步骤 {@link #GUIDE_STARFALL}。
 *       假玩家、旁观者收不了。</li>
 *   <li><b>惩罚</b>（{@link #exposure}，{@code HoannaHazards.tickPlayer} 每秒调）：露天每秒 {@code Radiation.addFloored(玩家, ley_storm_radiation（2）, 0.5)}，
 *       任何盔甲都至少吃一半；<b>不造成伤害</b>（不带攻击者的伤害要避开，policy P1）。</li>
 * </ul>
 * 地震的喷口充能（风暴中 1.0）由喷口自己读 {@link HoannaEvents#isActive}；风暴里浮游棱晶的追加由海纳尔刷怪钩子读同一个状态。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class LeyStorm {

    /** 黄昏：一天的第几刻（同日阵结束的那一刻） */
    public static final int DUSK = HoannaEvents.SUN_ARRAY_DUSK;
    /** 每次掷落晶的几率 */
    public static final float STARFALL_CHANCE = 0.6F;
    /** 落点离玩家的水平距离（格） */
    public static final int STARFALL_MIN_DISTANCE = 12;
    public static final int STARFALL_MAX_DISTANCE = 40;
    /** 从地表上方几格开始往下落 */
    public static final int STARFALL_DROP_HEIGHT = 12;
    /** 风暴的辐射至少吃这么多易伤 */
    public static final float RADIATION_FLOOR = 0.5F;
    /** 满月拾取给几个、平时几个 */
    public static final int CRYSTALS_FULL_MOON = 2;
    public static final int CRYSTALS = 1;
    /** 手册步骤「捡到一颗地脉落晶」（impossible 进度，这里发） */
    public static final ResourceLocation GUIDE_STARFALL = EssentialCraft5.id("guide/hoanna_explore/starfall");

    /** 落给谁的 UUID → 他名下的落晶（落晶 UUID → 过期的主世界 gameTime；加进世界登记、离开世界摘掉；关服清） */
    private static final Map<UUID, Map<UUID, Long>> OWNED = new HashMap<>();
    @Nullable
    private static volatile Float testStarfallChance;
    /** GameTest 用的落点距离（&lt; 0 = 照常 12~40）：测试结构只有自己的区块能 tick 实体 */
    private static volatile int testMinDistance = -1;
    private static volatile int testMaxDistance = -1;

    private LeyStorm() {
    }

    // ------------------------------------------------------------------ 时间

    /** 黄昏序号 floor((dayTime − 12000) / 24000)：日内刻跨过 12000 时加一 */
    public static long duskIndex(long dayTime) {
        return Math.floorDiv(dayTime - DUSK, HoannaEvents.DAY_TICKS);
    }

    /** 下一个黎明（绝对 dayTime，严格大于现在） */
    public static long nextDawn(long dayTime) {
        return (Math.floorDiv(dayTime, HoannaEvents.DAY_TICKS) + 1L) * HoannaEvents.DAY_TICKS;
    }

    /** 离下一个黎明还有多少刻（1~24000） */
    public static int ticksToDawn(long dayTime) {
        return (int) (nextDawn(dayTime) - dayTime);
    }

    // ------------------------------------------------------------------ 掷骰

    /**
     * 黄昏那一次（纯函数，GameTest 测概率）：不是大潮、关着、几率 × 倍数 ≤ 0 时不掷（不用随机数）；否则一次 {@code nextFloat() < 几率 × 倍数}。
     *
     * @return {@link HoannaEvent#LEY_STORM} 或 NONE
     */
    public static HoannaEvent rollDusk(RandomSource random, double multiplier, boolean springTide, boolean enabled, double chance) {
        double p = chance * multiplier;
        if (!springTide || !enabled || !(p > 0D)) {
            return HoannaEvent.NONE;
        }
        return random.nextFloat() < p ? HoannaEvent.LEY_STORM : HoannaEvent.NONE;
    }

    // ------------------------------------------------------------------ 落晶

    /** 这名玩家名下登记着几个落晶（含已经过期、还没被删掉的） */
    public static int owned(UUID owner) {
        Map<UUID, Long> mine = OWNED.get(owner);
        return mine == null ? 0 : mine.size();
    }

    /**
     * 这名玩家名下没过期的落晶有几个（now 是主世界 gameTime）：过期的顺手摘掉。落晶停刻（区块不 tick 实体、混合端的实体激活距离）时
     * 自己删不掉，靠这里不让它占着名额；它之后走刻、被碰到或者区块卸载时照常删掉。
     */
    public static int owned(UUID owner, long now) {
        Map<UUID, Long> mine = OWNED.get(owner);
        if (mine == null) {
            return 0;
        }
        mine.values().removeIf(expireAt -> now >= expireAt);
        if (mine.isEmpty()) {
            OWNED.remove(owner);
            return 0;
        }
        return mine.size();
    }

    /** 落晶加进世界时登记（{@link LeyStarfallEntity}；只在服务端；同一颗再登记一次只更新过期时刻） */
    public static void track(UUID owner, UUID starfall, long expireAt) {
        OWNED.computeIfAbsent(owner, id -> new HashMap<>()).put(starfall, expireAt);
    }

    /** 落晶离开世界时摘掉 */
    public static void untrack(UUID owner, UUID starfall) {
        Map<UUID, Long> mine = OWNED.get(owner);
        if (mine != null) {
            mine.remove(starfall);
            if (mine.isEmpty()) {
                OWNED.remove(owner);
            }
        }
    }

    /** 这名玩家现在能不能被落晶：真玩家、活着、生存 / 冒险、在海纳尔、风暴中、头顶露天 */
    public static boolean canReceiveStarfall(ServerPlayer player) {
        if (player instanceof FakePlayer || !player.isAlive() || player.isCreative() || player.isSpectator()) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        return HoannaEvents.isActive(level, HoannaEvent.LEY_STORM) && HoannaHazards.exposedToSky(level, player);
    }

    /**
     * 给一名玩家掷一次落晶（{@link #onLeyStormPlayerTick} 每 {@code ley_storm_starfall_interval} 刻调；GameTest 直接调、给定随机源）。
     * 随机数用量：先一次 nextFloat（{@value #STARFALL_CHANCE}），掷中再两次 nextDouble（方向、距离）。
     *
     * @return 生成的落晶；没落（条件不满足、到上限、没掷中、落点不能用、被别的模组拦下）返回 null
     */
    @Nullable
    public static LeyStarfallEntity trySpawnStarfall(ServerPlayer player, RandomSource random) {
        if (!canReceiveStarfall(player)) {
            return null;
        }
        int cap = HoannaHazardsConfig.starfallPerPlayer();
        if (cap <= 0 || owned(player.getUUID(), LeyStarfallEntity.now(player.serverLevel())) >= cap) {
            return null;
        }
        Float forced = testStarfallChance;
        if (random.nextFloat() >= (forced != null ? forced : STARFALL_CHANCE)) {
            return null;
        }
        double angle = random.nextDouble() * Math.PI * 2D;
        int min = testMinDistance;
        int max = testMaxDistance;
        if (min < 0 || max < min) {
            min = STARFALL_MIN_DISTANCE;
            max = STARFALL_MAX_DISTANCE;
        }
        double distance = min + random.nextDouble() * (max - min);
        int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
        int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
        ServerLevel level = player.serverLevel();
        BlockPos column = new BlockPos(x, player.getBlockY(), z);
        // 先问区块在不在、能不能 tick 实体，再读高度图：读一个没加载的区块会把它加载出来
        if (!level.hasChunkAt(column) || !level.isPositionEntityTicking(column)) {
            return null;
        }
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        if (ground <= level.getMinBuildHeight() || !level.getFluidState(new BlockPos(x, ground - 1, z)).isEmpty()) {
            return null;
        }
        double hover = ground + LeyStarfallEntity.HOVER;
        double start = Math.min(ground + (double) STARFALL_DROP_HEIGHT, level.getMaxBuildHeight() - 1D);
        LeyStarfallEntity starfall = new LeyStarfallEntity(level, x + 0.5D, Math.max(start, hover), z + 0.5D, hover, player.getUUID());
        if (!level.addFreshEntity(starfall)) {
            return null;
        }
        level.playSound(null, starfall.getX(), starfall.getY(), starfall.getZ(), ECHoannaStorm.STARFALL_SPAWN.get(), SoundSource.AMBIENT, 1F, 1F);
        return starfall;
    }

    /** 拾取给几个地脉结晶：满月（潮位 1.0）2 个，平时 1 个（潮汐关掉时恒 1 个） */
    public static int crystalsFor(@Nullable Level level) {
        return HoannaTide.value(level) >= 1F ? CRYSTALS_FULL_MOON : CRYSTALS;
    }

    /**
     * 真玩家碰到落晶（{@link LeyStarfallEntity#playerTouch}；GameTest 直接调）：先移除落晶（同一刻两个人碰到只给一份），再给地脉结晶、放声音、发手册步骤。
     * 还没落地（看着还在空中）的不收；已经过期（停刻没来得及自己删）的不收、顺手删掉。
     *
     * @return 收没收（假玩家、旁观者、死了的、落晶已经没了 / 还没落地 / 过期了返回 false）
     */
    public static boolean collect(LeyStarfallEntity starfall, ServerPlayer player) {
        if (starfall.isRemoved() || player instanceof FakePlayer || player.isSpectator() || !player.isAlive()) {
            return false;
        }
        if (starfall.expired()) {
            starfall.discard();
            return false;
        }
        if (!starfall.landed()) {
            return false;
        }
        int count = crystalsFor(player.level());
        starfall.discard();
        ECItemEntities.giveOrDrop(player, new ItemStack(ECHoannaMaterials.LEY_CRYSTAL.get(), count));
        player.level().playSound(null, starfall.getX(), starfall.getY(), starfall.getZ(), ECHoannaStorm.STARFALL_COLLECT.get(),
                SoundSource.PLAYERS, 0.8F, 1F);
        ECAdvancements.awardIfPresent(player, GUIDE_STARFALL);
        return true;
    }

    // ------------------------------------------------------------------ 惩罚

    /**
     * 风暴中的一秒（{@code HoannaHazards.tickPlayer} 调，调用方保证在海纳尔、风暴中）：头顶露天就加 {@code round(剂量 × max(易伤, 0.5))}，不造成伤害。
     *
     * @return 这一秒实际加了多少剂量
     */
    public static int exposure(ServerPlayer player, ServerLevel level) {
        int base = HoannaHazardsConfig.leyStormRadiation();
        if (base <= 0 || player instanceof FakePlayer || !player.isAlive() || player.isCreative() || player.isSpectator()
                || !HoannaHazards.exposedToSky(level, player)) {
            return 0;
        }
        int before = Radiation.get(player);
        return Radiation.addFloored(player, base, RADIATION_FLOOR) - before;
    }

    // ------------------------------------------------------------------ 事件

    @SubscribeEvent
    public static void onLeyStormPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER || !(event.player instanceof ServerPlayer player)
                || player instanceof FakePlayer) {
            return;
        }
        if (player.tickCount % HoannaHazardsConfig.starfallInterval() == 0 && ECHoanna.isHoanna(player.serverLevel())) {
            trySpawnStarfall(player, player.getRandom());
        }
    }

    @SubscribeEvent
    public static void onLeyStormServerStopped(ServerStoppedEvent event) {
        OWNED.clear();
    }

    // ------------------------------------------------------------------ GameTest

    /** GameTest 用：固定落晶的掷中几率（null 还原）；同一段同步代码里设、测、还原 */
    public static void setTestStarfallChance(@Nullable Float chance) {
        testStarfallChance = chance;
    }

    /** GameTest 用：落点离玩家的水平距离改成 min~max 格（传 −1, −1 还原）；同一段同步代码里设、测、还原 */
    public static void setTestStarfallDistance(int min, int max) {
        testMinDistance = min;
        testMaxDistance = max;
    }
}
