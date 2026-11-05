package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.entity.DividerFieldEntity;
import com.flameforge.essentialcraft5.entity.OrbitalStrikeEntity;
import com.flameforge.essentialcraft5.gun.RayHitGate;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECHoloWeapons;
import com.flameforge.essentialcraft5.util.ECDamageKinds;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.ZombieEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 第八批框架 8-Fa「技能实体」的 GameTest（batch8 §3.1 第 6 条）：
 * 轨道打击（按 affects 筛、攻击者一律跳过、伤害类型与标签、抗性提升挡不住而护甲照算、攻击被拦 / 伤害被拦时不着火、不击退、不改方块、
 * 攻击者没了就熄灭、到点才落下、不存盘）；二分场（半径、四种效果、旁观 / 创造 / 攻击者跳过、0 伤害探测被拦不减半、不探测时直接生效、
 * 无敌帧里照样问得到且无敌帧计时放回、混合端要 LivingDamageEvent、攻击者没了就熄灭）；{@code RayHitGate.observe} 的 landed / probePassed / 嵌套。
 * <p>
 * 用尸壳（不怕太阳，跨刻的测试不会自己着火；护甲 2，和僵尸一样）。结算大多直接调 {@code detonate()}（同到点，同步测）；
 * 筛选写成「是不是这几只」，免得碰到隔壁测试结构里的生物。混合端用 {@code RayHitGate.setHybridForTest} 模拟，同一段同步代码里设、测、置回。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class HoloStrikeFrameworkTests {

    private static final String EMPTY = "empty";
    private static final double EPS = 1.0E-6D;
    /** 8 点伤害打护甲 2、韧性 0 的尸壳：8 × (1 − 0.4 / 25) */
    private static final float DAMAGE = 8F;
    private static final float AFTER_ARMOR = 7.872F;
    /** 不会在测试里自己到点的延迟（结算靠 detonate） */
    private static final int LONG_DELAY = 1000;

    private HoloStrikeFrameworkTests() {
    }

    // ================================================================== 伤害类型

    /** 两个伤害类型：带攻击者、没有直接实体和位置、不随难度放大；orbital_strike 带 bypasses_effects / shield（不带 armor）；divider 带 bypasses_cooldown、no_anger */
    @GameTest(template = EMPTY)
    public static void holoStrikeDamageTypes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Husk attacker = husk(helper, new Vec3(1.5D, 1D, 1.5D));
        Husk victim = husk(helper, new Vec3(3.5D, 1D, 3.5D));
        DamageSource strike = ECDamageTypes.orbitalStrike(level, attacker);
        DamageSource divider = ECDamageTypes.divider(level, attacker);

        helper.assertTrue(strike.is(ECDamageTypes.ORBITAL_STRIKE) && strike.getEntity() == attacker && strike.getDirectEntity() == null
                && strike.sourcePositionRaw() == null && strike.getSourcePosition() == null, "轨道打击：攻击者在、没有直接实体和位置");
        helper.assertTrue(strike.is(DamageTypeTags.BYPASSES_EFFECTS) && strike.is(DamageTypeTags.BYPASSES_SHIELD),
                "轨道打击应带 bypasses_effects、bypasses_shield");
        helper.assertFalse(strike.is(DamageTypeTags.BYPASSES_ARMOR) || strike.is(DamageTypeTags.IS_PROJECTILE) || strike.is(DamageTypeTags.IS_FIRE)
                || strike.is(DamageTypeTags.IS_EXPLOSION) || strike.is(ECDamageTypes.NO_DEATH_LOOT), "轨道打击：护甲照算、不是弹射物 / 火 / 爆炸、打死照常掉落");
        helper.assertFalse(strike.scalesWithDifficulty(), "轨道打击不随难度放大（scaling never）");
        helper.assertFalse(ECDamageKinds.isPhysical(strike) || ECDamageKinds.isElemental(strike) || ECDamageKinds.isMelee(strike),
                "轨道打击既不算物理也不算元素（虚空 / 宅男套装有电时不挡它）、也不算近战");
        helper.assertTrue(strike.getMsgId().equals("essentialcraft5.orbital_strike"), "message_id，实际 " + strike.getMsgId());
        Component death = strike.getLocalizedDeathMessage(victim);
        helper.assertTrue(death.getContents() instanceof TranslatableContents contents
                        && contents.getKey().equals("death.attack.essentialcraft5.orbital_strike") && contents.getArgs().length == 2,
                "有攻击者时用基础键、两个参数，实际 " + death);

        helper.assertTrue(divider.is(ECDamageTypes.DIVIDER) && divider.getEntity() == attacker && divider.getDirectEntity() == null
                && divider.sourcePositionRaw() == null, "二分场探测：攻击者在、没有直接实体和位置");
        helper.assertTrue(divider.is(DamageTypeTags.BYPASSES_COOLDOWN) && divider.is(DamageTypeTags.NO_ANGER),
                "二分场探测应带 bypasses_cooldown、no_anger");
        helper.assertFalse(divider.is(DamageTypeTags.BYPASSES_ARMOR) || divider.is(ECDamageTypes.NO_DEATH_LOOT) || divider.scalesWithDifficulty(),
                "二分场探测不带别的标签、不随难度放大");
        helper.assertTrue(divider.getMsgId().equals("essentialcraft5.divider"), "message_id，实际 " + divider.getMsgId());
        helper.assertTrue(ForgeRegistries.ENTITY_TYPES.tags().isKnownTagName(ECHoloWeapons.DIVIDER_IMMUNE), "实体标签 divider_immune 应已加载");
        helper.succeed();
    }

    // ================================================================== 轨道打击

    /**
     * 按 affects 筛：立方内的目标挨伤害（护甲照算）、着火、不被击退，伤害记在攻击者头上；affects 不放行的村民、立方外的尸壳不受伤；
     * 攻击者本人在立方里、affects 也放行，照样跳过；落下以后实体消失
     */
    @GameTest(template = EMPTY)
    public static void holoStrikeAffectsFilter(GameTestHelper helper) {
        Husk attacker = husk(helper, new Vec3(0.5D, 1D, 0.5D));
        Husk target = husk(helper, new Vec3(2.5D, 1D, 1.5D));
        Husk outside = husk(helper, new Vec3(4.7D, 1D, 1.5D));
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(1.5D, 1D, 3D));
        Set<LivingEntity> allowed = Set.of(attacker, target, outside);
        OrbitalStrikeEntity strike = strike(helper, new Vec3(1.5D, 1D, 1.5D), attacker, allowed::contains);

        strike.detonate();
        helper.assertTrue(Math.abs(target.getMaxHealth() - target.getHealth() - AFTER_ARMOR) < 0.01F,
                "目标应挨 8 点（护甲 2 后 7.872），实际少了 " + (target.getMaxHealth() - target.getHealth()));
        helper.assertTrue(target.getRemainingFireTicks() > 0, "伤害落下后应着火");
        helper.assertTrue(target.getDeltaMovement().horizontalDistanceSqr() < EPS, "不应被击退，实际 " + target.getDeltaMovement());
        DamageSource last = target.getLastDamageSource();
        helper.assertTrue(last != null && last.is(ECDamageTypes.ORBITAL_STRIKE) && last.getEntity() == attacker, "伤害应记在攻击者头上");
        helper.assertTrue(full(attacker) && attacker.getRemainingFireTicks() <= 0, "攻击者本人一律跳过");
        helper.assertTrue(full(villager) && villager.getRemainingFireTicks() <= 0, "affects 不放行的村民不受伤、不着火");
        helper.assertTrue(full(outside), "立方外的尸壳不受伤");
        helper.assertTrue(strike.isRemoved(), "落下以后应消失");
        helper.succeed();
    }

    /** bypasses_effects：抗性提升 V（平时全免）挡不住，护甲照算——和没有抗性的一样挨 7.872 */
    @GameTest(template = EMPTY)
    public static void holoStrikeIgnoresResistance(GameTestHelper helper) {
        Husk attacker = husk(helper, new Vec3(0.5D, 1D, 0.5D));
        Husk resistant = husk(helper, new Vec3(2.5D, 1D, 2.5D));
        resistant.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 4));
        OrbitalStrikeEntity strike = strike(helper, new Vec3(2.5D, 1D, 2.5D), attacker, living -> living == resistant);

        strike.detonate();
        float lost = resistant.getMaxHealth() - resistant.getHealth();
        helper.assertTrue(Math.abs(lost - AFTER_ARMOR) < 0.01F, "抗性提升 V 不该减免、护甲照算（7.872），实际少了 " + lost);
        helper.succeed();
    }

    /** 攻击在 LivingAttackEvent 被拦（Forge 端领地模组的做法）：不掉血、不着火、不被推；同一击里没被拦的照常 */
    @GameTest(template = EMPTY)
    public static void holoStrikeAttackDenied(GameTestHelper helper) {
        Husk attacker = husk(helper, new Vec3(0.5D, 1D, 0.5D));
        Husk denied = husk(helper, new Vec3(2.5D, 1D, 1.5D));
        Husk open = husk(helper, new Vec3(1.5D, 1D, 2.5D));
        OrbitalStrikeEntity strike = strike(helper, new Vec3(1.5D, 1D, 1.5D), attacker, living -> living == denied || living == open);
        Runnable off = listen(LivingAttackEvent.class, event -> {
            if (event.getEntity() == denied) {
                event.setCanceled(true);
            }
        });
        try {
            strike.detonate();
        } finally {
            off.run();
        }
        helper.assertTrue(full(denied) && denied.getRemainingFireTicks() <= 0, "被拦下的不掉血、不着火");
        helper.assertTrue(denied.getDeltaMovement().lengthSqr() < EPS, "被拦下的不被推");
        helper.assertTrue(!full(open) && open.getRemainingFireTicks() > 0, "没被拦的照常挨打、着火");
        helper.succeed();
    }

    /**
     * 伤害在攻击放行之后才被拦：LivingHurtEvent 被取消（纯 Forge 的领地模组；混合端模拟下同样不会再有 LivingDamageEvent）、
     * LivingDamageEvent 被取消——都不着火、不被击退（原版 hurt 在 actuallyHurt 之后照样会走到击退）
     */
    @GameTest(template = EMPTY)
    public static void holoStrikeHurtDeniedNoSideEffects(GameTestHelper helper) {
        Husk attacker = husk(helper, new Vec3(0.5D, 1D, 0.5D));
        Husk hurtDenied = husk(helper, new Vec3(2.5D, 1D, 1.5D));
        Husk damageDenied = husk(helper, new Vec3(1.5D, 1D, 2.5D));
        Husk hybridDenied = husk(helper, new Vec3(3.5D, 1D, 2.5D));
        Husk open = husk(helper, new Vec3(2.5D, 1D, 3.5D));
        Set<LivingEntity> plain = Set.of(hurtDenied, damageDenied);
        OrbitalStrikeEntity first = strike(helper, new Vec3(1.5D, 1D, 1.5D), attacker, plain::contains);
        Set<LivingEntity> hybrid = Set.of(hybridDenied, open);
        OrbitalStrikeEntity second = strike(helper, new Vec3(2.5D, 1D, 2.5D), attacker, hybrid::contains);
        Runnable offHurt = listen(LivingHurtEvent.class, event -> {
            if (event.getEntity() == hurtDenied || event.getEntity() == hybridDenied) {
                event.setCanceled(true);
            }
        });
        Runnable offDamage = listen(LivingDamageEvent.class, event -> {
            if (event.getEntity() == damageDenied) {
                event.setCanceled(true);
            }
        });
        try {
            first.detonate();
            RayHitGate.setHybridForTest(true);
            second.detonate();
        } finally {
            RayHitGate.setHybridForTest(null);
            offHurt.run();
            offDamage.run();
        }
        for (Husk husk : List.of(hurtDenied, damageDenied, hybridDenied)) {
            helper.assertTrue(full(husk), "伤害被拦下的不掉血");
            helper.assertTrue(husk.getRemainingFireTicks() <= 0, "伤害被拦下的不着火");
            helper.assertTrue(husk.getDeltaMovement().horizontalDistanceSqr() < EPS, "伤害被拦下的不被推，实际 " + husk.getDeltaMovement());
        }
        helper.assertTrue(!full(open) && open.getRemainingFireTicks() > 0, "混合端模拟下没被拦的照常挨打、着火");
        helper.assertTrue(open.getDeltaMovement().horizontalDistanceSqr() < EPS, "挨打的也不被推");
        helper.succeed();
    }

    /** 不改方块：立方里的水、冰、沙、铁矿石、原木原样不动，也没有掉落物冒出来（原作删水冰雪、就地熔炼） */
    @GameTest(template = EMPTY)
    public static void holoStrikeLeavesBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Husk attacker = husk(helper, new Vec3(0.5D, 1D, 4.5D));
        BlockPos[] spots = {new BlockPos(1, 1, 1), new BlockPos(3, 1, 1), new BlockPos(2, 2, 2), new BlockPos(1, 1, 3), new BlockPos(3, 1, 3)};
        Block[] blocks = {Blocks.WATER, Blocks.ICE, Blocks.SAND, Blocks.IRON_ORE, Blocks.OAK_LOG};
        for (int i = 0; i < spots.length; i++) {
            helper.setBlock(spots[i], blocks[i]);
        }
        OrbitalStrikeEntity strike = strike(helper, new Vec3(2.5D, 1D, 2.5D), attacker, living -> false);
        try {
            strike.detonate();
            for (int i = 0; i < spots.length; i++) {
                Block now = level.getBlockState(helper.absolutePos(spots[i])).getBlock();
                helper.assertTrue(now == blocks[i], spots[i] + " 应仍是 " + blocks[i] + "，实际 " + now);
            }
            AABB cube = new AABB(helper.absoluteVec(new Vec3(2.5D, 1D, 2.5D)), helper.absoluteVec(new Vec3(2.5D, 1D, 2.5D)))
                    .inflate(OrbitalStrikeEntity.REACH);
            helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, cube).isEmpty(), "不该冒出掉落物");
        } finally {
            // 水留着会往外流、沙子下一刻会掉下去
            helper.setBlock(spots[0], Blocks.AIR);
            helper.setBlock(spots[2], Blocks.AIR);
        }
        helper.succeed();
    }

    /** 攻击者没了就熄灭：攻击者已移除、或者死了，到点时不结算（目标不受伤），实体照样消失 */
    @GameTest(template = EMPTY)
    public static void holoStrikeFizzlesWithoutAttacker(GameTestHelper helper) {
        Husk gone = husk(helper, new Vec3(0.5D, 1D, 0.5D));
        Husk dead = husk(helper, new Vec3(4.5D, 1D, 0.5D));
        Husk target = husk(helper, new Vec3(2.5D, 1D, 2.5D));
        OrbitalStrikeEntity first = strike(helper, new Vec3(2.5D, 1D, 2.5D), gone, living -> living == target);
        OrbitalStrikeEntity second = strike(helper, new Vec3(2.5D, 1D, 2.5D), dead, living -> living == target);
        gone.discard();
        dead.setHealth(0F);

        first.detonate();
        second.detonate();
        helper.assertTrue(full(target) && target.getRemainingFireTicks() <= 0, "攻击者没了，不该结算");
        helper.assertTrue(first.isRemoved() && second.isRemoved(), "熄灭的打击照样消失");
        helper.succeed();
    }

    /** 真走刻数：到点之前不结算，到点落下、实体消失；不存盘、不能 /summon，延迟写在同步字段里 */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void holoStrikeTicksAndNotSaved(GameTestHelper helper) {
        Husk attacker = husk(helper, new Vec3(0.5D, 1D, 0.5D));
        Husk target = husk(helper, new Vec3(2.5D, 1D, 2.5D));
        OrbitalStrikeEntity strike = OrbitalStrikeEntity.spawn(helper.getLevel(), helper.absoluteVec(new Vec3(2.5D, 1D, 2.5D)),
                DAMAGE, 1F, 6, attacker, living -> living == target);
        if (strike == null) {
            helper.fail("轨道打击没生成出来");
            return;
        }
        helper.assertTrue(strike.delayTicks() == 6, "延迟应是 6，实际 " + strike.delayTicks());
        helper.assertFalse(strike.shouldBeSaved(), "不该存盘");
        helper.assertFalse(strike.save(new CompoundTag()), "实体类型 noSave：save 应返回 false");
        helper.assertFalse(ECHoloWeapons.ORBITAL_STRIKE.get().canSummon() || ECHoloWeapons.DIVIDER_FIELD.get().canSummon(), "两个技能实体都不能 /summon");
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(!strike.isRemoved() && full(target), "还没到点，不该结算");
            helper.assertTrue(strike.remainingTicks(0F) > 0F, "还没到点，剩余刻数应 > 0");
        });
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(strike.isRemoved(), "到点后应消失");
            helper.assertFalse(full(target), "到点后应结算");
            helper.succeed();
        });
    }

    // ================================================================== 二分场

    /**
     * 不探测的场（全息投影放的）：脚下到中心 2.9 格的减半 + 四种效果，3.2 格的不变；affects 不放行的村民、攻击者本人、
     * 旁观 / 创造模式的玩家不变；生存玩家减半；结算后实体消失
     */
    @GameTest(template = EMPTY)
    public static void dividerRadiusAndEffects(GameTestHelper helper) {
        Husk attacker = husk(helper, new Vec3(1.5D, 1D, 0.5D));
        Husk near = husk(helper, new Vec3(3.4D, 1D, 0.5D));
        Husk far = husk(helper, new Vec3(0.5D, 1D, 3.7D));
        Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(2.5D, 1D, 2.5D));
        ServerPlayer survival = player(helper, "ec5-dv-s", new Vec3(1.5D, 1D, 1.5D), GameType.SURVIVAL);
        ServerPlayer creative = player(helper, "ec5-dv-c", new Vec3(2D, 1D, 0.5D), GameType.CREATIVE);
        ServerPlayer spectator = player(helper, "ec5-dv-p", new Vec3(0.5D, 1D, 2D), GameType.SPECTATOR);
        Set<LivingEntity> allowed = Set.of(attacker, near, far, survival, creative, spectator);
        DividerFieldEntity field = field(helper, new Vec3(0.5D, 1D, 0.5D), attacker, allowed::contains, false);

        field.detonate(List.of(survival, creative, spectator));
        helper.assertTrue(near.getHealth() == near.getMaxHealth() / 2F, "2.9 格的应减半，实际 " + near.getHealth());
        assertDivided(helper, near);
        helper.assertTrue(full(far) && far.getActiveEffects().isEmpty(), "3.2 格的不变");
        helper.assertTrue(full(villager) && villager.getActiveEffects().isEmpty(), "affects 不放行的村民不变");
        helper.assertTrue(full(attacker) && attacker.getActiveEffects().isEmpty(), "攻击者本人一律跳过");
        helper.assertTrue(survival.getHealth() == survival.getMaxHealth() / 2F, "生存玩家应减半，实际 " + survival.getHealth());
        helper.assertTrue(full(creative) && creative.getActiveEffects().isEmpty(), "创造模式玩家不变");
        helper.assertTrue(full(spectator) && spectator.getActiveEffects().isEmpty(), "旁观者不变");
        helper.assertTrue(field.isRemoved(), "结算后应消失");
        helper.succeed();
    }

    /**
     * 探测的场（玩家射出的）：0 伤害探测被拦（LivingAttackEvent）的不减半、没有效果；放行的减半、有效果，不被推、不记仇，
     * 无敌帧计时原样放回；玩家挨 0 伤害时 Player.hurt 直接返回，探测不放行
     */
    @GameTest(template = EMPTY)
    public static void dividerProbeGate(GameTestHelper helper) {
        ServerPlayer shooter = player(helper, "ec5-dv-sh", new Vec3(4.5D, 1D, 4.5D), GameType.SURVIVAL);
        Husk denied = husk(helper, new Vec3(2D, 1D, 0.5D));
        Husk open = husk(helper, new Vec3(0.5D, 1D, 2D));
        ServerPlayer bystander = player(helper, "ec5-dv-by", new Vec3(1.5D, 1D, 1.5D), GameType.SURVIVAL);
        Set<LivingEntity> allowed = Set.of(denied, open, bystander);
        DividerFieldEntity field = field(helper, new Vec3(0.5D, 1D, 0.5D), shooter, allowed::contains, true);
        Runnable off = listen(LivingAttackEvent.class, event -> {
            if (event.getEntity() == denied) {
                event.setCanceled(true);
            }
        });
        try {
            field.detonate(List.of(bystander));
        } finally {
            off.run();
        }
        helper.assertTrue(full(denied) && denied.getActiveEffects().isEmpty(), "探测被拦的不减半、没有效果");
        helper.assertTrue(open.getHealth() == open.getMaxHealth() / 2F, "探测放行的应减半，实际 " + open.getHealth());
        assertDivided(helper, open);
        helper.assertTrue(open.getDeltaMovement().horizontalDistanceSqr() < EPS, "探测不该把目标推开，实际 " + open.getDeltaMovement());
        helper.assertTrue(open.getLastHurtByMob() == null, "探测带 no_anger，不该让目标记仇");
        helper.assertTrue(open.invulnerableTime == 0, "无敌帧计时应放回探测前的 0，实际 " + open.invulnerableTime);
        helper.assertTrue(full(bystander) && bystander.getActiveEffects().isEmpty(), "玩家挨 0 伤害时探测不放行，不减半");
        helper.succeed();
    }

    /**
     * 探测打到僵尸类（尸壳）时，原版 Zombie.hurt 发的叫增援事件被置成 DENY：困难难度下探测不会刷出盯上射手的增援。
     * 轨道打击的真伤害被纯 Forge 领地模组在 LivingHurtEvent 拦下（hurt 照样返回真）时同样 DENY；真的落下了照原版（DEFAULT）。
     * 观察监听器挂在 LOWEST、在模组的静态监听器之后登记，看到的是最终结果（别的模组改不回来）。
     * 只看事件结果，不靠真的刷怪（白天的超平坦本来就刷不出来，测不出）
     */
    @GameTest(template = EMPTY)
    public static void dividerProbeNoZombieAid(GameTestHelper helper) {
        Husk attacker = husk(helper, new Vec3(4.5D, 1D, 4.5D));
        Husk target = husk(helper, new Vec3(2D, 1D, 0.5D));
        DividerFieldEntity field = field(helper, new Vec3(0.5D, 1D, 0.5D), attacker, living -> living == target, true);
        Event.Result[] seen = new Event.Result[1];
        Consumer<ZombieEvent.SummonAidEvent> watcher = event -> {
            if (event.getEntity() == target) {
                seen[0] = event.getResult();
            }
        };
        Runnable off = listen(EventPriority.LOWEST, ZombieEvent.SummonAidEvent.class, watcher);
        try {
            field.detonate();
        } finally {
            off.run();
        }
        helper.assertTrue(seen[0] == Event.Result.DENY, "探测时叫增援事件应是 DENY，实际 " + seen[0]);
        helper.assertTrue(target.getHealth() == target.getMaxHealth() / 2F, "探测放行后照样减半，实际 " + target.getHealth());
        // 不经结算的伤害不管：同一只尸壳直接挨一下轨道打击伤害源，叫增援事件照原版（DEFAULT）
        seen[0] = null;
        target.invulnerableTime = 0;
        Runnable off2 = listen(EventPriority.LOWEST, ZombieEvent.SummonAidEvent.class, watcher);
        try {
            target.hurt(ECDamageTypes.orbitalStrike(helper.getLevel(), attacker), 1F);
        } finally {
            off2.run();
        }
        helper.assertTrue(seen[0] == Event.Result.DEFAULT, "不在结算中的伤害不该被改，实际 " + seen[0]);

        // 轨道打击结算的真伤害被 LivingHurtEvent 拦下：没掉血、hurt 照样返回真、照样发叫增援事件 → DENY
        seen[0] = null;
        target.invulnerableTime = 0;
        float before = target.getHealth();
        OrbitalStrikeEntity blocked = strike(helper, new Vec3(2D, 1D, 0.5D), attacker, living -> living == target);
        Runnable offHurt = listen(LivingHurtEvent.class, event -> {
            if (event.getEntity() == target) {
                event.setCanceled(true);
            }
        });
        Runnable off3 = listen(EventPriority.LOWEST, ZombieEvent.SummonAidEvent.class, watcher);
        try {
            blocked.detonate();
        } finally {
            offHurt.run();
            off3.run();
        }
        helper.assertTrue(target.getHealth() == before, "自检：伤害在 LivingHurtEvent 被拦下，不掉血");
        helper.assertTrue(seen[0] == Event.Result.DENY, "被拦下的轨道打击不该叫来增援，实际 " + seen[0]);
        helper.assertFalse(target.isOnFire(), "被拦下的不着火");

        // 落下的轨道打击：照原版
        seen[0] = null;
        target.invulnerableTime = 0;
        OrbitalStrikeEntity landed = strike(helper, new Vec3(2D, 1D, 0.5D), attacker, living -> living == target);
        Runnable off4 = listen(EventPriority.LOWEST, ZombieEvent.SummonAidEvent.class, watcher);
        try {
            landed.detonate();
        } finally {
            off4.run();
        }
        helper.assertTrue(target.getHealth() < before, "自检：这一下落下了");
        helper.assertTrue(seen[0] == Event.Result.DEFAULT, "落下的轨道打击照原版叫增援（DEFAULT），实际 " + seen[0]);
        helper.succeed();
    }

    /** 不探测的场：同样的拦截监听器在，也照样生效（全息投影放的场对象由调用方收窄，不问插件） */
    @GameTest(template = EMPTY)
    public static void dividerUngatedSkipsProbe(GameTestHelper helper) {
        Husk attacker = husk(helper, new Vec3(4.5D, 1D, 4.5D));
        Husk target = husk(helper, new Vec3(2D, 1D, 0.5D));
        DividerFieldEntity field = field(helper, new Vec3(0.5D, 1D, 0.5D), attacker, living -> living == target, false);
        Runnable off = listen(LivingAttackEvent.class, event -> {
            if (event.getEntity() == target) {
                event.setCanceled(true);
            }
        });
        try {
            field.detonate();
        } finally {
            off.run();
        }
        helper.assertTrue(target.getHealth() == target.getMaxHealth() / 2F, "不探测的场应直接减半，实际 " + target.getHealth());
        assertDivided(helper, target);
        helper.succeed();
    }

    /** 目标在无敌帧里（刚挨过一下、计时 15 &gt; 10）：探测带 bypasses_cooldown 照样问得到、放行后减半；无敌帧计时放回 15 */
    @GameTest(template = EMPTY)
    public static void dividerProbeInInvulnerabilityFrames(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Husk attacker = husk(helper, new Vec3(4.5D, 1D, 4.5D));
        Husk target = husk(helper, new Vec3(2D, 1D, 0.5D));
        target.hurt(level.damageSources().mobAttack(attacker), 2F);
        target.invulnerableTime = 15;
        float before = target.getHealth();
        helper.assertTrue(before < target.getMaxHealth(), "自检：先挨的那一下应掉血");
        DividerFieldEntity field = field(helper, new Vec3(0.5D, 1D, 0.5D), attacker, living -> living == target, true);

        field.detonate();
        helper.assertTrue(Math.abs(target.getHealth() - before / 2F) < 1.0E-4F, "无敌帧里探测也应放行、减半，实际 " + target.getHealth() + "（之前 " + before + "）");
        helper.assertTrue(target.invulnerableTime == 15, "无敌帧计时应放回 15，实际 " + target.invulnerableTime);
        helper.succeed();
    }

    /**
     * 混合端模拟：0 伤害探测还要等到 LivingDamageEvent（混合端在插件放行后照常发）。纯 Forge 里 0 伤害不会有这个事件，
     * 所以没人补发时当成插件拦了、不减半；测试监听器在 LivingHurtEvent 里补发一个（模拟插件放行）的减半；补发的又被取消的不减半
     */
    @GameTest(template = EMPTY)
    public static void dividerHybridNeedsDamageEvent(GameTestHelper helper) {
        Husk attacker = husk(helper, new Vec3(4.5D, 1D, 4.5D));
        Husk silent = husk(helper, new Vec3(2D, 1D, 0.5D));
        Husk passed = husk(helper, new Vec3(0.5D, 1D, 2D));
        Husk cancelled = husk(helper, new Vec3(1.5D, 1D, 1.5D));
        Set<LivingEntity> allowed = Set.of(silent, passed, cancelled);
        DividerFieldEntity field = field(helper, new Vec3(0.5D, 1D, 0.5D), attacker, allowed::contains, true);
        Runnable offHurt = listen(LivingHurtEvent.class, event -> {
            if (event.getEntity() == passed || event.getEntity() == cancelled) {
                MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(event.getEntity(), event.getSource(), event.getAmount()));
            }
        });
        Runnable offDamage = listen(LivingDamageEvent.class, event -> {
            if (event.getEntity() == cancelled) {
                event.setCanceled(true);
            }
        });
        try {
            RayHitGate.setHybridForTest(true);
            field.detonate();
        } finally {
            RayHitGate.setHybridForTest(null);
            offHurt.run();
            offDamage.run();
        }
        helper.assertTrue(full(silent) && silent.getActiveEffects().isEmpty(), "混合端没到 LivingDamageEvent = 插件拦了，不减半");
        helper.assertTrue(passed.getHealth() == passed.getMaxHealth() / 2F, "混合端到了 LivingDamageEvent 的应减半，实际 " + passed.getHealth());
        helper.assertTrue(full(cancelled), "LivingDamageEvent 被取消的不减半");
        helper.succeed();
    }

    /** 攻击者没了就熄灭：二分场什么都不做、照样消失 */
    @GameTest(template = EMPTY)
    public static void dividerFizzlesWithoutAttacker(GameTestHelper helper) {
        Husk attacker = husk(helper, new Vec3(4.5D, 1D, 4.5D));
        Husk target = husk(helper, new Vec3(2D, 1D, 0.5D));
        DividerFieldEntity field = field(helper, new Vec3(0.5D, 1D, 0.5D), attacker, living -> living == target, false);
        attacker.discard();
        field.detonate();
        helper.assertTrue(full(target) && target.getActiveEffects().isEmpty(), "攻击者没了，不该生效");
        helper.assertTrue(field.isRemoved() && !field.shouldBeSaved(), "熄灭的场照样消失、不存盘");
        helper.succeed();
    }

    // ================================================================== RayHitGate.observe

    /**
     * landed：正常落下为真；LivingAttackEvent / LivingHurtEvent / LivingDamageEvent 任一被取消都为假。
     * 射线的 allowed 口径不变：LivingDamageEvent 被取消（伤害没生效）时 allowed 仍为真，landed 为假
     */
    @GameTest(template = EMPTY)
    public static void holoStrikeGateLanded(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Husk attacker = husk(helper, new Vec3(4.5D, 1D, 4.5D));
        Husk normal = husk(helper, new Vec3(0.5D, 1D, 0.5D));
        Husk attackDenied = husk(helper, new Vec3(2.5D, 1D, 0.5D));
        Husk hurtDenied = husk(helper, new Vec3(0.5D, 1D, 2.5D));
        Husk damageDenied = husk(helper, new Vec3(2.5D, 1D, 2.5D));
        Runnable offAttack = listen(LivingAttackEvent.class, event -> {
            if (event.getEntity() == attackDenied) {
                event.setCanceled(true);
            }
        });
        Runnable offHurt = listen(LivingHurtEvent.class, event -> {
            if (event.getEntity() == hurtDenied) {
                event.setCanceled(true);
            }
        });
        Runnable offDamage = listen(LivingDamageEvent.class, event -> {
            if (event.getEntity() == damageDenied) {
                event.setCanceled(true);
            }
        });
        RayHitGate.Watch[] watches = new RayHitGate.Watch[4];
        try {
            watches[0] = RayHitGate.observe(normal, ECDamageTypes.orbitalStrike(level, attacker), 4F);
            watches[1] = RayHitGate.observe(attackDenied, ECDamageTypes.orbitalStrike(level, attacker), 4F);
            watches[2] = RayHitGate.observe(hurtDenied, ECDamageTypes.orbitalStrike(level, attacker), 4F);
            watches[3] = RayHitGate.observe(damageDenied, ECDamageTypes.orbitalStrike(level, attacker), 4F);
        } finally {
            offAttack.run();
            offHurt.run();
            offDamage.run();
        }
        helper.assertTrue(watches[0].landed() && watches[0].hurtResult() && !full(normal), "正常落下：landed、hurt 返回 true、掉血");
        helper.assertFalse(watches[1].landed() || watches[1].hurtResult(), "攻击被拦：没落下");
        helper.assertFalse(watches[2].landed(), "LivingHurtEvent 被取消：没落下");
        helper.assertFalse(watches[3].landed(), "LivingDamageEvent 被取消：没落下");
        helper.assertTrue(full(attackDenied) && full(hurtDenied) && full(damageDenied), "三个被拦的都不掉血");
        int tick = level.getServer().getTickCount();
        helper.assertTrue(RayHitGate.allowed(watches[3], attacker, tick), "射线口径不变：伤害没生效也算打中");
        helper.assertFalse(RayHitGate.allowed(watches[1], attacker, tick), "射线口径不变：攻击被拦不算");
        helper.succeed();
    }

    /**
     * probePassed（0 伤害）：纯 Forge 放行为真；LivingAttackEvent / LivingHurtEvent 被取消为假；玩家挨 0 伤害为假；
     * 混合端模拟下没有 LivingDamageEvent 为假、补发了为真
     */
    @GameTest(template = EMPTY)
    public static void holoStrikeGateProbe(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Husk attacker = husk(helper, new Vec3(4.5D, 1D, 4.5D));
        Husk open = husk(helper, new Vec3(0.5D, 1D, 0.5D));
        Husk attackDenied = husk(helper, new Vec3(2.5D, 1D, 0.5D));
        Husk hurtDenied = husk(helper, new Vec3(0.5D, 1D, 2.5D));
        Husk hybridSilent = husk(helper, new Vec3(2.5D, 1D, 2.5D));
        Husk hybridPassed = husk(helper, new Vec3(1.5D, 1D, 3.5D));
        ServerPlayer player = player(helper, "ec5-dv-pr", new Vec3(3.5D, 1D, 1.5D), GameType.SURVIVAL);
        Runnable offAttack = listen(LivingAttackEvent.class, event -> {
            if (event.getEntity() == attackDenied) {
                event.setCanceled(true);
            }
        });
        Runnable offHurt = listen(LivingHurtEvent.class, event -> {
            if (event.getEntity() == hurtDenied) {
                event.setCanceled(true);
            } else if (event.getEntity() == hybridPassed) {
                MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(event.getEntity(), event.getSource(), event.getAmount()));
            }
        });
        boolean[] passed = new boolean[6];
        try {
            passed[0] = RayHitGate.observe(open, ECDamageTypes.divider(level, attacker), 0F).probePassed();
            passed[1] = RayHitGate.observe(attackDenied, ECDamageTypes.divider(level, attacker), 0F).probePassed();
            passed[2] = RayHitGate.observe(hurtDenied, ECDamageTypes.divider(level, attacker), 0F).probePassed();
            passed[3] = RayHitGate.observe(player, ECDamageTypes.divider(level, attacker), 0F).probePassed();
            RayHitGate.setHybridForTest(true);
            passed[4] = RayHitGate.observe(hybridSilent, ECDamageTypes.divider(level, attacker), 0F).probePassed();
            passed[5] = RayHitGate.observe(hybridPassed, ECDamageTypes.divider(level, attacker), 0F).probePassed();
        } finally {
            RayHitGate.setHybridForTest(null);
            offAttack.run();
            offHurt.run();
        }
        helper.assertTrue(passed[0], "纯 Forge 放行的 0 伤害探测：通过");
        helper.assertFalse(passed[1], "攻击被拦：不通过");
        helper.assertFalse(passed[2], "LivingHurtEvent 被取消：不通过");
        helper.assertFalse(passed[3], "玩家挨 0 伤害：Player.hurt 直接返回，不通过");
        helper.assertFalse(passed[4], "混合端没到 LivingDamageEvent：不通过");
        helper.assertTrue(passed[5], "混合端到了 LivingDamageEvent：通过");
        helper.succeed();
    }

    /** 嵌套：A 的伤害流程里又让 B 挨了一下（B 的 LivingDamageEvent 被取消），两条记录互不串 */
    @GameTest(template = EMPTY)
    public static void holoStrikeGateNested(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Husk attacker = husk(helper, new Vec3(4.5D, 1D, 4.5D));
        Husk outer = husk(helper, new Vec3(0.5D, 1D, 0.5D));
        Husk inner = husk(helper, new Vec3(2.5D, 1D, 2.5D));
        RayHitGate.Watch[] nested = new RayHitGate.Watch[1];
        Runnable offHurt = listen(LivingHurtEvent.class, event -> {
            if (event.getEntity() == outer && nested[0] == null) {
                nested[0] = RayHitGate.observe(inner, ECDamageTypes.orbitalStrike(level, attacker), 4F);
            }
        });
        Runnable offDamage = listen(LivingDamageEvent.class, event -> {
            if (event.getEntity() == inner) {
                event.setCanceled(true);
            }
        });
        RayHitGate.Watch watch;
        try {
            watch = RayHitGate.observe(outer, ECDamageTypes.orbitalStrike(level, attacker), 4F);
        } finally {
            offHurt.run();
            offDamage.run();
        }
        helper.assertTrue(nested[0] != null, "自检：里层那一下应发生");
        helper.assertTrue(watch.landed() && !full(outer), "外层：落下、掉血");
        helper.assertFalse(nested[0].landed(), "里层：LivingDamageEvent 被取消，没落下");
        helper.assertTrue(full(inner), "里层不掉血");
        helper.succeed();
    }

    // ================================================================== 工具

    private static Husk husk(GameTestHelper helper, Vec3 relativeFeet) {
        return helper.spawnWithNoFreeWill(EntityType.HUSK, relativeFeet);
    }

    /** 真玩家（不在世界的实体列表里，结算时用 detonate(extra) 交进去），过掉出生无敌，脚在 relativeFeet */
    private static ServerPlayer player(GameTestHelper helper, String name, Vec3 relativeFeet, GameType mode) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        for (int i = 0; i <= 60; i++) {
            player.tick();
        }
        player.setGameMode(mode);
        Vec3 at = helper.absoluteVec(relativeFeet);
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    private static OrbitalStrikeEntity strike(GameTestHelper helper, Vec3 relative, LivingEntity attacker,
                                              Predicate<? super LivingEntity> affects) {
        OrbitalStrikeEntity strike = OrbitalStrikeEntity.spawn(helper.getLevel(), helper.absoluteVec(relative), DAMAGE, 1F, LONG_DELAY,
                attacker, affects);
        if (strike == null) {
            throw new IllegalStateException("轨道打击没生成出来");
        }
        return strike;
    }

    private static DividerFieldEntity field(GameTestHelper helper, Vec3 relative, LivingEntity attacker,
                                            Predicate<? super LivingEntity> affects, boolean gated) {
        DividerFieldEntity field = DividerFieldEntity.spawn(helper.getLevel(), helper.absoluteVec(relative), LONG_DELAY, attacker, affects, gated);
        if (field == null) {
            throw new IllegalStateException("二分场没生成出来");
        }
        return field;
    }

    private static boolean full(LivingEntity living) {
        return living.getHealth() == living.getMaxHealth();
    }

    /** 四种效果照原作：虚弱 / 缓慢 / 挖掘疲劳 V 各 200 刻、失明 I 100 刻（刚加上，一刻都没走） */
    private static void assertDivided(GameTestHelper helper, LivingEntity target) {
        assertEffect(helper, target, MobEffects.WEAKNESS, 200, 4);
        assertEffect(helper, target, MobEffects.MOVEMENT_SLOWDOWN, 200, 4);
        assertEffect(helper, target, MobEffects.DIG_SLOWDOWN, 200, 4);
        assertEffect(helper, target, MobEffects.BLINDNESS, 100, 0);
    }

    private static void assertEffect(GameTestHelper helper, LivingEntity target, MobEffect effect, int ticks, int amplifier) {
        MobEffectInstance instance = target.getEffect(effect);
        helper.assertTrue(instance != null && instance.getDuration() == ticks && instance.getAmplifier() == amplifier,
                target.getName().getString() + " 应有 " + effect.getDescriptionId() + " " + amplifier + " / " + ticks + " 刻，实际 " + instance);
    }

    /** 挂一个只在测试期间有效的事件监听器（NORMAL、不收已取消的），返回摘掉它的动作 */
    private static <E extends Event> Runnable listen(Class<E> type, Consumer<E> listener) {
        return listen(EventPriority.NORMAL, type, listener);
    }

    /**
     * 同上，指定优先级。同一个监听器对象登记一次摘一次（unregister 按对象认）；
     * 观察最终结果的挂 LOWEST，同优先级下排在模组静态监听器之后
     */
    private static <E extends Event> Runnable listen(EventPriority priority, Class<E> type, Consumer<E> listener) {
        MinecraftForge.EVENT_BUS.addListener(priority, false, type, listener);
        return () -> MinecraftForge.EVENT_BUS.unregister(listener);
    }
}
