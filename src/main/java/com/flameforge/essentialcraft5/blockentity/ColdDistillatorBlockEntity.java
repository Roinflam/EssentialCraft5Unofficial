package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.ambience.MachineAmbience;
import com.flameforge.essentialcraft5.ambience.MachineAmbiences;
import com.flameforge.essentialcraft5.api.IColdBlock;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.ColdDistillatorMenu;
import com.flameforge.essentialcraft5.registry.ECGenerators;
import com.flameforge.essentialcraft5.util.ECEntityRules;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 冷凝器。对应原作 TileColdDistillator。
 * <p>
 * 把以自己为中心 7×7×7 范围内所有方块的冷值加起来、向下取整，再乘产量倍率，就是每 tick 的产量：
 * 蓝冰 0.4、浮冰 0.3、雪块和细雪 0.2、冰 0.15、雪（雪层）0.05、实现了 {@link IColdBlock} 的按接口（霜冻石 0.5）。
 * 平衡值固定为配置值（默认 0，纯冰冻）。有红石信号时不产出。
 * 产出时每 {@value #HARM_INTERVAL} tick 随机挑 3 格内的一个生物：给挖掘疲劳 III、虚弱 III、缓慢 III（150 秒），60% 概率受 1 点饥饿伤害（原作每 tick 一次、20%）
 * （挑中不受影响的就这 tick 作罢，见 {@link #sparedFromCold}）。
 * <p>
 * 移植改动：
 * <ul>
 *   <li>原作每 tick 扫 343 格（而且每格读 5 次方块状态），这里每 {@value #SCAN_INTERVAL} tick 扫一次、结果缓存。
 *       摆放冰块后最多 1 秒产量才变，其余不变。</li>
 *   <li>负面效果：原作每 tick 把三个效果重新加一遍，每次都给玩家发一个效果更新包；这里剩余时间少于
 *       {@value #EFFECT_REFRESH_MARGIN} tick 才续，效果上没有区别。挑生物、给效果、判伤害每 {@value #HARM_INTERVAL} tick 做一次（原作每 tick），伤害概率按间隔折算。</li>
 *   <li>1.12 → 1.20：原作的 Blocks.SNOW 是雪块、Blocks.SNOW_LAYER 是雪层，1.20 分别是 snow_block 和 snow。</li>
 *   <li>界面上的「MRU/t」显示实际产量（原作显示的是冷值之和，产量倍率不是 1 时对不上）。</li>
 *   <li>寒气只伤主人和无主的生物（第三批审查 RB-1，全模组安全原则 P1，policy.md §4.1）：原作对 3 格内所有生物（只除了创造模式的玩家）
 *       上三个 III 级负面效果、造成不带攻击者的饥饿伤害，领地插件、PvP 开关都拦不住，贴着邻居交易所的外墙放一台就能冻死墙里的村民。
 *       现在玩家里只影响机器的主人本人（没有主人的机器谁都不影响），别的生物跳过太阳棱镜光束不烧的那一份名单
 *       （驯服、拴绳、起了名字的，装了鞍的骆驼，村民、玩家造的铁傀儡和雪傀儡、认了主人的悦灵）。不探测领地：每个目标都查一次权限太贵，
 *       名单已经把「别人的」生物排除在外。</li>
 * </ul>
 */
public class ColdDistillatorBlockEntity extends MRUBlockEntity {

    /** 原作 tileentities.colddistillator */
    public static final class Config {
        public static ForgeConfigSpec.IntValue maxMRU;
        public static ForgeConfigSpec.DoubleValue balance;
        public static ForgeConfigSpec.DoubleValue mruGenModifier;
        public static ForgeConfigSpec.BooleanValue harmEntities;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("cold_distillator");
            maxMRU = builder.defineInRange("max_mru", ECConfig.GENERATOR_MAX_MRU * 10, 1, Integer.MAX_VALUE);
            balance = builder.comment("Balance of the MRU produced.", "产出 MRU 的平衡值（0 为纯冰冻）")
                    .defineInRange("balance", 0D, 0D, 2D);
            mruGenModifier = builder.comment("MRU per tick = floor(sum of cold values within 3 blocks) x this.",
                            "每 tick 产量 = 3 格内冷值之和取整 × 这个倍率")
                    .defineInRange("mru_gen_modifier", 1D, 0D, 1000D);
            harmEntities = builder.comment("While producing, give a random creature within 3 blocks mining fatigue, weakness and slowness"
                                    + " (only the owner among players; villagers, player-built golems, saddled camels and tamed, leashed or named creatures are spared).",
                            "产出时给 3 格内随机一个生物上挖掘疲劳、虚弱、缓慢，并有概率造成伤害（玩家只影响主人本人；村民、玩家造的傀儡、装了鞍的骆驼和驯服、拴绳、起了名字的生物不受影响）")
                    .define("damage_entities_around", true);
            builder.pop();
        }
    }

    /** 扫描半径（原作 -3..3） */
    public static final int RADIUS = 3;
    /** 多少 tick 扫一次周围 */
    public static final int SCAN_INTERVAL = 20;
    /** 查周围生物、给寒气效果的间隔（刻） */
    public static final int HARM_INTERVAL = 5;
    /** 负面效果的时长（原作 3000 tick）与续期余量 */
    private static final int EFFECT_DURATION = 3000;
    private static final int EFFECT_REFRESH_MARGIN = 20;
    private static final int EFFECT_AMPLIFIER = 2;

    /** 周围冷值之和（缓存，不存盘：加载后第一个 tick 就会重扫） */
    private double coldSum;
    private int scanCooldown;
    /** 当前每 tick 产量，给界面看 */
    private int generation;

    /** 这一刻真的产出了 MRU，存满了不算（工作声音和粒子看它，见 {@link #ambientWorking}） */
    private boolean generatedThisTick;

    public ColdDistillatorBlockEntity(BlockPos pos, BlockState state) {
        super(ECGenerators.COLD_DISTILLATOR_BE.get(), pos, state, () -> ECConfig.get(Config.maxMRU), 0);
    }

    @Override
    protected void serverTick() {
        mru.setBalance((float) ECConfig.get(Config.balance));
        if (level == null) {
            return;
        }
        if (--scanCooldown <= 0) {
            scanCooldown = SCAN_INTERVAL;
            coldSum = scanCold(level, worldPosition);
        }
        // 原作 getMRU() 先把冷值之和取整，再乘倍率取整。冷值是 0.3、0.15 这种二进制除不尽的小数，
        // 累加会差一点点（10 块浮冰加出来是 2.9999…，直接取整成 2），取整前补一个极小量
        generation = (int) ((int) (coldSum + 1e-6) * ECConfig.get(Config.mruGenModifier) + 1e-6);
        generatedThisTick = false;
        if (!neighborPowered()) {
            generatedThisTick = mru.addMRU(generation, true) > 0;
            // 15 格范围的查实体不用每刻做：隔 HARM_INTERVAL 刻做一次，各台错开（效果本来就一次续好几秒）
            if (generation > 0 && Math.floorMod(level.getGameTime() + worldPosition.hashCode(), HARM_INTERVAL) == 0
                    && ECConfig.get(Config.harmEntities)) {
                harmAround(level);
            }
        }
    }

    /** 周围 7×7×7（含自己那格）的冷值之和 */
    public static double scanCold(Level level, BlockPos center) {
        double sum = 0D;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int y = -RADIUS; y <= RADIUS; y++) {
                for (int z = -RADIUS; z <= RADIUS; z++) {
                    pos.setWithOffset(center, x, y, z);
                    sum += coldValue(level, pos, level.getBlockState(pos));
                }
            }
        }
        return sum;
    }

    public static double coldValue(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof IColdBlock cold) {
            return cold.getColdModifier(level, pos, state);
        }
        // 蓝冰、细雪是 1.13 以后的方块，原作没有，这里也算进来。原作的压缩比是冰 0.15 → 浮冰 0.3（×2，不是按 9 块算），
        // 蓝冰照这个比例本该 0.6，但不能超过本模组的霜冻石（0.5），取 0.4；细雪同雪块
        if (state.is(Blocks.BLUE_ICE)) {
            return 0.4D;
        }
        if (state.is(Blocks.PACKED_ICE)) {
            return 0.3D;
        }
        if (state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) {
            return 0.2D;
        }
        if (state.is(Blocks.ICE)) {
            return 0.15D;
        }
        if (state.is(Blocks.SNOW)) {
            return 0.05D;
        }
        return 0D;
    }

    /** 原作 damageAround：随机挑 3 格内的一个生物（挑中不受影响的就这 tick 作罢，同原作挑中创造模式的玩家） */
    private void harmAround(Level level) {
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, new AABB(worldPosition).inflate(RADIUS));
        if (entities.isEmpty()) {
            return;
        }
        LivingEntity target = entities.get(level.random.nextInt(entities.size()));
        if (sparedFromCold(target, ECProtection.owner(this))) {
            return;
        }
        refreshEffect(target, MobEffects.DIG_SLOWDOWN);
        refreshEffect(target, MobEffects.WEAKNESS);
        refreshEffect(target, MobEffects.MOVEMENT_SLOWDOWN);
        // 原作每刻 20%；现在隔 HARM_INTERVAL 刻来一次，挨一下以后还有受伤无敌，折成 60% 大致一样疼
        if (level.random.nextFloat() < 0.6F) {
            target.hurt(level.damageSources().starve(), 1F);
        }
    }

    /**
     * 寒气不影响的对象（RB-1）。玩家：创造模式的不影响（原作）；不是机器主人的不影响，所以没有主人的机器（/setblock、结构、别的模组放的）
     * 不影响任何玩家。别的生物：谁都不许碰的那些（{@link ECEntityRules#isSpared}，原太阳棱镜 sparedFromBeam：驯服、拴绳、起了名字的，装了鞍的骆驼，
     * 村民、玩家造的铁傀儡和雪傀儡、认了主人的悦灵），野怪、普通动物、村庄自己刷的铁傀儡照旧。
     *
     * @param owner 机器的主人（{@link ECProtection#owner}），没有主人为 null
     */
    public static boolean sparedFromCold(LivingEntity target, @Nullable UUID owner) {
        if (target instanceof Player player) {
            return player.isCreative() || !player.getUUID().equals(owner);
        }
        return ECEntityRules.isSpared(target);
    }

    private static void refreshEffect(LivingEntity target, MobEffect effect) {
        MobEffectInstance current = target.getEffect(effect);
        if (current == null || current.getAmplifier() < EFFECT_AMPLIFIER
                || current.getDuration() < EFFECT_DURATION - EFFECT_REFRESH_MARGIN) {
            target.addEffect(new MobEffectInstance(effect, EFFECT_DURATION, EFFECT_AMPLIFIER));
        }
    }

    public int getGeneration() {
        return generation;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ColdDistillatorMenu(id, inventory, this);
    }

    @Override
    protected boolean ambientWorking() {
        return generatedThisTick;
    }

    @Override
    protected MachineAmbience.Profile ambience() {
        return MachineAmbiences.COLD_DISTILLATOR;
    }
}
