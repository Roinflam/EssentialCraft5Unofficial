package com.flameforge.essentialcraft5.item.bauble;

import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECBaubles;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * 特殊饰品的效果实现（原作 ItemBaublesSpecial.getModifiedValue / onWornTick 和 ECEventHandler 里的饰品分支），事件类和物品类都调这里。
 * 数值照原作，改动写在各方法注释里。
 *
 * <h2>击杀 UBMRU 修正（原作 getModifiedValue，由 UBMRUEvents 依次调用，前一件的结果传给后一件、每次向下取整）</h2>
 * 稳定 +0~99；经验 −10%（拿走的部分每 {@code bauble_limits.experience_ring_ubmru_per_xp} 点变 1 点经验）；生命挂坠 −30%（这部分 ÷30 回血）；医师腰带 → 0；
 * ν −25%（外加范围伤害）；χ ÷2；ω ×2。
 * 只在杀非玩家生物、所得大于 0 时调用（夺取别的玩家的 UBMRU 不吃饰品倍率，见 UBMRUEvents）。
 *
 * <h2>递归标记（policy §1.2「递归」行）</h2>
 * ν 的范围伤害、ι 转出的魔法伤害、σ 的反伤都在 {@link #asBaubleDamage} 里打出：这期间的 LivingHurtEvent 不再进 ζ / η / ι / σ 的逻辑
 * （两个戴 σ 的人互打不会来回弹、ι 的魔法伤害不会再被拆一次）。ν 打死的怪不再触发 ν 的范围伤害（{@link #nuChain} 自己的标记）。
 * θ 是防御效果、不会递归，照常生效。
 * <p>
 * 挨打以后自动打回去的那几种（σ 的反弹、宅男全套的反伤）另外套一层 {@link #asReflectDamage}（它里面照样算饰品伤害）：σ 的反弹和普通近战是同一个伤害类型
 * （{@code playerAttack}），只能靠这个标记认出来。交手记录（util/ECCombatTags）不把它当成「对方动过手」，削减模块不给它叠破甲（{@code ECDamageKinds.isReflected}）；
 * ν 的连锁、ι 的魔法伤害是佩戴者自己这一下引出来的，不在这一层里。
 */
public final class BaubleEffects {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** ζ 每击的 UBMRU（原作 300） */
    public static final int ZETA_COST = 300;
    /** θ 每点伤害的 UBMRU（原作 5000） */
    public static final int THETA_COST_PER_DAMAGE = 5000;
    /** ο 每次的 UBMRU（原作 500） */
    public static final int OMICRON_COST = 500;
    /** ρ 每点经验换的 UBMRU：现在读 {@code bauble_limits.rho_ubmru_per_xp}（{@link ECBaubles.Limits#rhoUbmruPerXp()}），这里只是原作的数 */
    public static final int RHO_UBMRU_PER_XP = ECBaubles.Limits.DEFAULT_RHO_UBMRU_PER_XP;
    /** τ 每次的 UBMRU（原作 1000） */
    public static final int TAU_COST = 1000;
    /** φ 每次的 UBMRU 和剪刀耐久（原作 1000、32） */
    public static final int PHI_COST = 1000;
    public static final int PHI_SHEARS_DAMAGE = 32;
    /** ν 每只的伤害（原作 3 点） */
    public static final float NU_DAMAGE = 3F;
    /** κ：每 10 tick +1 UBMRU、每 tick 0.01 饥饿度消耗 */
    public static final int KAPPA_INTERVAL = 10;
    public static final float KAPPA_EXHAUSTION = 0.01F;
    /** 医师腰带：每多少 tick 结算一次（每 tick 额外 −1 合并成每 20 tick −20） */
    public static final int DOCTOR_INTERVAL = 20;

    /** 医师腰带缩短的负面效果（原作 isPotionBad 的八种） */
    public static final Set<MobEffect> DOCTOR_EFFECTS = Set.of(MobEffects.WITHER, MobEffects.POISON, MobEffects.HUNGER, MobEffects.BLINDNESS,
            MobEffects.CONFUSION, MobEffects.DIG_SLOWDOWN, MobEffects.MOVEMENT_SLOWDOWN, MobEffects.WEAKNESS);

    private static final ThreadLocal<int[]> BAUBLE_DAMAGE = ThreadLocal.withInitial(() -> new int[1]);
    private static final ThreadLocal<int[]> REFLECT_DAMAGE = ThreadLocal.withInitial(() -> new int[1]);
    private static final ThreadLocal<int[]> NU_CHAIN = ThreadLocal.withInitial(() -> new int[1]);

    private BaubleEffects() {
    }

    // ---------------------------------------------------------------- 通用

    /** 戴着这件饰品（走 CuriosCompat 的每玩家缓存，被禁用的不算，同种只算一件） */
    public static boolean has(LivingEntity entity, Item bauble) {
        return SpecialBaubleItem.isWearer(entity) && CuriosCompat.has(entity, bauble);
    }

    @Nullable
    public static IPlayerData data(Player player) {
        return PlayerDataEvents.get(player);
    }

    /** 扣 UBMRU：够就扣并返回 true，不够什么都不做 */
    public static boolean spendUBMRU(Player player, long cost) {
        IPlayerData data = data(player);
        if (data == null || data.getUBMRU() < cost) {
            return false;
        }
        data.setUBMRU((int) Math.max(0L, data.getUBMRU() - cost));
        return true;
    }

    /** 加 UBMRU（夹到 int 上限） */
    public static void addUBMRU(Player player, long amount) {
        IPlayerData data = data(player);
        if (data != null && amount > 0) {
            data.setUBMRU((int) Math.min(Integer.MAX_VALUE, Math.max(0L, (long) data.getUBMRU() + amount)));
        }
    }

    /** 首领：实体类型在 {@code #forge:bosses} 里（τ、ψ 默认对它们不生效） */
    public static boolean isBoss(Entity entity) {
        return entity.getType().is(Tags.EntityTypes.BOSSES);
    }

    /** 现在打出的伤害是饰品自己造成的（ν、ι、σ），不再进饰品逻辑 */
    public static boolean isBaubleDamage() {
        return BAUBLE_DAMAGE.get()[0] > 0;
    }

    /** 以「饰品伤害」的身份执行（见类注释） */
    public static boolean asBaubleDamage(BooleanSupplier action) {
        int[] depth = BAUBLE_DAMAGE.get();
        depth[0]++;
        try {
            return action.getAsBoolean();
        } finally {
            depth[0]--;
        }
    }

    /** 现在打出的伤害是挨打以后自动打回去的反伤（σ 的反弹、宅男全套的反伤），见类注释 */
    public static boolean isReflectDamage() {
        return REFLECT_DAMAGE.get()[0] > 0;
    }

    /** 以「反伤」的身份执行：同时也是饰品伤害（{@link #isBaubleDamage} 为真），见类注释 */
    public static boolean asReflectDamage(BooleanSupplier action) {
        int[] depth = REFLECT_DAMAGE.get();
        depth[0]++;
        try {
            return asBaubleDamage(action);
        } finally {
            depth[0]--;
        }
    }

    /**
     * 这次挖方块是玩家自己在挖：方块在他的触及距离内（多给 3 格，照顾延迟和连锁挖掘）。
     * 本模组的法杖替玩家挖（{@code ECProtection.breakGuarded}：岩浆法杖）另用 {@code ECProtection.isGuardedBreak()} 挡掉，
     * 不管离得多近都不算（π / ρ 在 BaubleHarvestEvents、ο 在 BaubleLootModifiers 里查）；这里再挡别的模组用玩家身份远程发的
     * BreakEvent / 按玩家算的掉落（远处的机器、采石场之类）。不挡的话 ρ 会把一份根本不会掉的经验换成 UBMRU。
     */
    public static boolean isMiningInReach(Player player, BlockPos pos) {
        return player.canReach(pos, 3D);
    }

    // ---------------------------------------------------------------- 击杀 UBMRU 修正

    /** 稳定指环：+rand(0~99)（原作 original + rng.nextInt(100)） */
    public static float stability(float original, ItemStack stack, RandomSource random, Player player) {
        return original + random.nextInt(100);
    }

    /**
     * 经验指环：−10%，减掉的部分每 {@code bauble_limits.experience_ring_ubmru_per_xp}（默认 20）点在身边变成 1 点经验（小数按几率进位；
     * 位置在玩家周围 ±1 格）。1.20 用 ExperienceOrb.award（会和附近的经验球合并）；原作那个经验球 5 秒后才能捡，1.20 的经验球没有这个字段，去掉。
     * <p>
     * 第十批饰品平衡：原作每 4 点换 1 点（{@code floor(divide10 / 4)}）。UBMRU 平均是基础最大生命的 20 倍，那样一只僵尸（20 生命、本身掉 5 点经验）
     * 白送 10 点，末影人 20 点，全息投影这类大血量的怪上百点，刷怪塔里等于三倍以上的经验。改成 20 点换 1 点：僵尸约 +2、末影人约 +4（四到八成）。
     */
    public static float experience(float original, ItemStack stack, RandomSource random, Player player) {
        float tenth = original / 10F;
        int xp = ECBaubles.Limits.roundRandomly(tenth / (double) ECBaubles.Limits.experienceRingUbmruPerXp(), random);
        if (xp > 0 && player.level() instanceof ServerLevel server) {
            Vec3 at = player.position().add(random.nextFloat() - random.nextFloat(), random.nextFloat() - random.nextFloat(),
                    random.nextFloat() - random.nextFloat());
            ExperienceOrb.award(server, at, xp);
        }
        return original - tenth;
    }

    /** 生命挂坠：−30%，回复这部分 ÷30 的生命（僵尸约 400 UBMRU → 回 4 点） */
    public static float lifePendant(float original, ItemStack stack, RandomSource random, Player player) {
        float part = original / 100F * 30F;
        player.heal(part / 30F);
        return original - part;
    }

    /** 医师腰带：击杀不得 UBMRU */
    public static float doctorsBelt(float original, ItemStack stack, RandomSource random, Player player) {
        return 0F;
    }

    /** χ 指环：减半 */
    public static float chi(float original, ItemStack stack, RandomSource random, Player player) {
        return original / 2F;
    }

    /** ω 指环：×2 */
    public static float omega(float original, ItemStack stack, RandomSource random, Player player) {
        return original * 2F;
    }

    /** ν 护身符：−25%，并对周围敌对生物打一圈（{@link #nuChain}） */
    public static float nu(float original, ItemStack stack, RandomSource random, Player player) {
        float part = original / 100F * 25F;
        nuChain(player);
        return original - part;
    }

    /**
     * ν 的范围伤害：玩家周围 13×7×13（原作玩家位置 ±0.5 再 grow(6, 3, 6)）里活着的敌对生物（原作 EntityMob → Monster），各受 3 点玩家攻击伤害。
     * 移植改动（policy §4.2）：近的先，最多 {@code bauble_limits.nu_max_targets}（默认 16）只；这圈伤害打死的怪不再触发这圈伤害（原作在刷怪塔里会一路连锁下去）。
     * 伤害记在玩家头上，PvP / 领地插件照常管得到。
     *
     * @return 打了几只
     */
    public static int nuChain(Player player) {
        int[] chaining = NU_CHAIN.get();
        int max = ECBaubles.Limits.nuMaxTargets();
        if (chaining[0] > 0 || max <= 0 || !(player.level() instanceof ServerLevel level)) {
            return 0;
        }
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        AABB box = new AABB(x - 0.5D, y - 0.5D, z - 0.5D, x + 0.5D, y + 0.5D, z + 0.5D).inflate(6D, 3D, 6D);
        List<Monster> mobs = level.getEntitiesOfClass(Monster.class, box, LivingEntity::isAlive);
        mobs.sort(Comparator.comparingDouble(mob -> mob.distanceToSqr(player)));
        DamageSource source = player.damageSources().playerAttack(player);
        int hit = 0;
        chaining[0]++;
        try {
            for (Monster mob : mobs) {
                if (hit >= max) {
                    break;
                }
                if (!mob.isAlive()) {
                    continue;
                }
                hit++;
                asBaubleDamage(() -> mob.hurt(source, NU_DAMAGE));
            }
        } finally {
            chaining[0]--;
        }
        return hit;
    }

    // ---------------------------------------------------------------- 戴着时每 tick

    /** κ 指环（原作 onWornTick meta 17）：每 tick 0.01 饥饿度消耗，每 10 tick +1 UBMRU */
    public static void kappaTick(Player player, ItemStack stack) {
        player.causeFoodExhaustion(KAPPA_EXHAUSTION);
        if (player.tickCount % KAPPA_INTERVAL == 0) {
            addUBMRU(player, 1);
        }
    }

    /** 医师腰带（原作 onWornTick meta 7）：每 20 tick 把八种负面效果各缩短 20 tick，等于每 tick 额外 −1，持续时间减半 */
    public static void doctorsBeltTick(Player player, ItemStack stack) {
        if (player.tickCount % DOCTOR_INTERVAL == 0) {
            shortenHarmfulEffects(player, DOCTOR_INTERVAL);
        }
    }

    @Nullable
    private static Field durationField;
    private static boolean durationFieldFailed;

    /**
     * 把 entity 身上 {@link #DOCTOR_EFFECTS} 里的效果各缩短 ticks（至少留 1 tick，让它自己按原版流程到期、移除属性修饰符）；无限时长的不动。
     * 原作每 tick 用反射给私有的 duration 减 1；这里同样反射（SRG 名 f_19503_），但每 20 tick 才改一次、改完给本人发一个效果更新包，
     * 不重新加效果（重新加会重算属性、发事件）。
     *
     * @return 缩短了几个效果
     */
    public static int shortenHarmfulEffects(LivingEntity entity, int ticks) {
        Field field = durationField();
        if (field == null || ticks <= 0) {
            return 0;
        }
        int changed = 0;
        for (MobEffectInstance instance : entity.getActiveEffects()) {
            if (!DOCTOR_EFFECTS.contains(instance.getEffect()) || instance.isInfiniteDuration()) {
                continue;
            }
            int duration = instance.getDuration();
            int cut = Math.min(ticks, duration - 1);
            if (cut <= 0) {
                continue;
            }
            try {
                field.setInt(instance, duration - cut);
            } catch (IllegalAccessException e) {
                return changed;
            }
            changed++;
            if (entity instanceof ServerPlayer serverPlayer && serverPlayer.connection != null) {
                serverPlayer.connection.send(new ClientboundUpdateMobEffectPacket(serverPlayer.getId(), instance));
            }
        }
        return changed;
    }

    @Nullable
    private static Field durationField() {
        if (durationField == null && !durationFieldFailed) {
            try {
                durationField = ObfuscationReflectionHelper.findField(MobEffectInstance.class, "f_19503_");
            } catch (RuntimeException e) {
                durationFieldFailed = true;
                LOGGER.error("[EC5] 医师腰带找不到 MobEffectInstance.duration（f_19503_），缩短负面效果失效", e);
            }
        }
        return durationField;
    }

    // ---------------------------------------------------------------- 掉落

    /** ο 加的「非方块掉落」：不是方块物品，或者是种子、胡萝卜、红石粉这种名字自成一类的方块物品（1.12 里它们都不是 ItemBlock） */
    public static boolean isNonBlockDrop(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        return !(item instanceof BlockItem) || item instanceof ItemNameBlockItem;
    }

    /**
     * 这组掉落是被挖方块自己的物品形态、又不是「长成了的作物」：放下去再挖回来的东西（红石线 → 红石粉、绊线 → 线、
     * 0 龄作物 → 种子 / 胡萝卜、火把花作物 → 火把花种子、瓶子草荚阶段 → 瓶子草荚），ο 不加，不然放下再挖一次就翻倍。
     * 方块有 age 属性且已到最大值（成熟的胡萝卜、马铃薯、下界疣、可可豆、小麦掉的种子）照样加：那是长出来的收成。
     * 作物（{@link CropBlock}）按它自己的 {@code isMaxAge} 判断（火把花作物的 age 属性只有 0~1、最大龄是 2，永远不算长成），
     * 别的方块看名叫 {@code age} 的整数属性。
     */
    public static boolean isReplantedForm(ItemStack stack, @Nullable BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        Item own = state.getBlock().asItem();
        if (own == Items.AIR || !stack.is(own)) {
            return false;
        }
        return !isGrown(state);
    }

    /** 方块有 age 属性且已长到最大（作物按 {@link CropBlock#isMaxAge}） */
    public static boolean isGrown(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty age && "age".equals(property.getName())) {
                return state.getValue(age) >= Collections.max(age.getPossibleValues());
            }
        }
        return false;
    }

    /** ο 能给这组 +1：非方块掉落、不是放下再挖回来的方块自身物品（{@link #isReplantedForm}）、没满一组 */
    private static boolean omicronCanAdd(ItemStack stack, @Nullable BlockState state) {
        return isNonBlockDrop(stack) && stack.getCount() < stack.getMaxStackSize() && !isReplantedForm(stack, state);
    }

    /**
     * ο 护身符（原作 HarvestDropsEvent）：挖方块的非方块掉落每组 +1，每次 500 UBMRU。
     * 移植改动：原作每挖一格都先扣 500（挖泥土也扣）；这里只在确实有东西可加时才扣。
     * 被挖方块自己的物品形态、方块又没长成的不加（{@link #isReplantedForm}，原作放下红石 / 种子再挖就翻倍的漏洞顺手修）。
     * 方块实体里的动态掉落（陶罐的陶片）、机器和法杖代挖的不加，在 {@link BaubleLootModifiers.OmicronModifier} 里挡。
     *
     * @param state 被挖的方块（战利品参数 {@code block_state}）；null 时不做「方块自身物品」的判断
     * @return 是否加了
     */
    public static boolean omicronBonus(Player player, BlockPos pos, @Nullable BlockState state, List<ItemStack> loot) {
        if (!has(player, ECBaubles.OMICRON_AMULET.get()) || player.level().isClientSide || !isMiningInReach(player, pos)) {
            return false;
        }
        boolean any = false;
        for (ItemStack stack : loot) {
            if (omicronCanAdd(stack, state)) {
                any = true;
                break;
            }
        }
        if (!any || !spendUBMRU(player, OMICRON_COST)) {
            return false;
        }
        for (ItemStack stack : loot) {
            if (omicronCanAdd(stack, state)) {
                stack.grow(1);
            }
        }
        return true;
    }

    /** 同 {@link #tauBonus(Player, Entity, List, RandomSource)}，不知道死者是谁（不做首领判断） */
    public static boolean tauBonus(Player player, List<ItemStack> loot, RandomSource random) {
        return tauBonus(player, null, loot, random);
    }

    /**
     * τ 指环（原作 LivingDropsEvent）：击杀掉落每组 +0~2，每次 1000 UBMRU。
     * 移植改动：只加生物<b>自己掉落表</b>里的东西（全局战利品修改器）。原作加在全部掉落上，连生物捡起来的装备、被杀玩家的整个背包都会多出来：
     * 把一组钻石扔给僵尸再杀掉就能复制。没有掉落时不扣。
     * <p>
     * 第十批饰品平衡：死者是首领（{@code #forge:bosses}：凋灵、末影龙、全息投影、别的模组登记的首领）时默认不加
     * （{@code bauble_limits.tau_affects_bosses}）：凋灵的下界之星平均变两颗、全息投影的遗物和数学核心多掉，都是主线上故意设的门槛。
     *
     * @param victim 死者；null 时不做首领判断
     * @return 是否加了
     */
    public static boolean tauBonus(Player player, @Nullable Entity victim, List<ItemStack> loot, RandomSource random) {
        if (!has(player, ECBaubles.TAU_RING.get()) || player.level().isClientSide) {
            return false;
        }
        if (victim != null && isBoss(victim) && !ECBaubles.Limits.tauAffectsBosses()) {
            return false;
        }
        boolean any = false;
        for (ItemStack stack : loot) {
            if (!stack.isEmpty()) {
                any = true;
                break;
            }
        }
        if (!any || !spendUBMRU(player, TAU_COST)) {
            return false;
        }
        for (ItemStack stack : loot) {
            if (!stack.isEmpty()) {
                stack.setCount(Math.max(stack.getCount(), Math.min(stack.getMaxStackSize(), stack.getCount() + random.nextInt(3))));
            }
        }
        return true;
    }
}
