package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.demon.DemonTrades;
import com.flameforge.essentialcraft5.entity.WindMageEntity;
import com.flameforge.essentialcraft5.entity.mob.ECFlyingMonster;
import com.flameforge.essentialcraft5.entity.mob.ECMonster;
import com.flameforge.essentialcraft5.entity.mob.MobDamageShare;
import com.flameforge.essentialcraft5.entity.mob.MobSpawnRules;
import com.flameforge.essentialcraft5.entity.mob.NetherImpEmberBoltEntity;
import com.flameforge.essentialcraft5.entity.mob.NetherImpEntity;
import com.flameforge.essentialcraft5.entity.mob.VoidStalkerEntity;
import com.flameforge.essentialcraft5.entity.mob.VoidStalkerRift;
import com.flameforge.essentialcraft5.entity.mob.VoidStalkerSpawner;
import com.flameforge.essentialcraft5.entity.mob.VoidStalkerSpawner.Outcome;
import com.flameforge.essentialcraft5.gametest.ProtectionFrameworkTests.Claims;
import com.flameforge.essentialcraft5.item.CapturedSoulItem;
import com.flameforge.essentialcraft5.loot.FairKillCondition;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECDemonic;
import com.flameforge.essentialcraft5.registry.ECMobFramework;
import com.flameforge.essentialcraft5.registry.ECNetherEndMobs;
import com.flameforge.essentialcraft5.registry.ECNetherEndMobs.Config.ImpValues;
import com.flameforge.essentialcraft5.registry.ECNetherEndMobs.Config.StalkerValues;
import com.flameforge.essentialcraft5.registry.ECWindMage;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.flameforge.essentialcraft5.util.SpawnOrigin;
import com.flameforge.essentialcraft5.util.SpawnOrigin.Origin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 第十三批 13c-M3「炼狱小鬼、虚空潜行者」的 GameTest（batch13.md §5.1、§5.2、§12.2 13c 行）：
 * <ul>
 *   <li>炼狱小鬼：生成时按配置设属性、改配置不动已有的、精英读档不叠两层；余火弹只打活物（穿过盔甲架和别的小鬼）、伤害类型是 {@code mob_bolt}、
 *       打中才点火、落地不放火、命中事件被取消就什么都不做、飞过压力板和绊线不触发；残血逃（真跑 AI）与配置填 0；小鬼头目叫帮手（来源「召唤」、不公平、不出精英、
 *       没经验、每只头目各自封顶、生成被取消不留东西）；掉落表（公平击杀、刷怪笼和召唤来的不掉、精英多一池）、能收魂；刷怪表的挤占。</li>
 *   <li>虚空潜行者：属性与读档；相位循环（预警、相位、填 0）；相位时不吃伤害、穿得过去、不出手，只认无视无敌的；裂隙爪只在打实时闪人、
 *       落点脚下三格内有地、不踩危险方块、传送被取消不动；裂隙行者拉人（看不见拉不动、非精英 / 冷却填 0 / 太近都不拉、传送被取消不动）；
 *       闪人、拉人的落点要连线看得通（不闪进封闭石屋、不隔墙拉进屋里）、玩家对落点有权限（别人的领地、本刻预算用完都不挪）；
 *       独立生成器（只给外岛上的生存 / 冒险玩家、和平 / 到上限 / 太挤 / 生成被取消都不刷、刷出来的来源是自然且公平、不进任何群系的刷怪表）。</li>
 * </ul>
 * 改配置一律用 {@code ECNetherEndMobs.Config.setTestImp / setTestStalker}（同一段同步代码里设、测、置回）。潜行者的测试大多不把潜行者放进世界
 * （闪人、拉人只读它的位置和视线），生成器那条单独一批，免得数「附近有几只」时数到别的测试的。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class NetherEndMobsTests {

    private static final String EMPTY = "empty";
    private static final String LARGE = "empty_large";
    /** 生成器那条自己一批：它要往世界里放潜行者、数 64 格内有几只 */
    private static final String SPAWNER_BATCH = "ec5_void_stalker_spawner";
    private static final double EPS = 1.0E-4D;
    private static final ImpValues IMP = ECNetherEndMobs.Config.IMP_DEFAULTS;
    private static final StalkerValues STALKER = ECNetherEndMobs.Config.STALKER_DEFAULTS;

    private NetherEndMobsTests() {
    }

    // ================================================================== 工具

    private static NetherImpEntity imp(GameTestHelper helper, Vec3 relative) {
        NetherImpEntity imp = ECNetherEndMobs.NETHER_IMP.get().create(helper.getLevel());
        if (imp == null) {
            throw new IllegalStateException("炼狱小鬼应能创建");
        }
        Vec3 at = helper.absoluteVec(relative);
        imp.moveTo(at.x, at.y, at.z, 0F, 0F);
        return imp;
    }

    private static VoidStalkerEntity stalker(GameTestHelper helper, Vec3 relative) {
        VoidStalkerEntity stalker = ECNetherEndMobs.VOID_STALKER.get().create(helper.getLevel());
        if (stalker == null) {
            throw new IllegalStateException("虚空潜行者应能创建");
        }
        Vec3 at = helper.absoluteVec(relative);
        stalker.moveTo(at.x, at.y, at.z, 0F, 0F);
        stalker.applyConfiguredAttributes();
        return stalker;
    }

    /** 走 Forge 的生成事件初始化（精英几率钉成 eliteChance，同步代码里置回） */
    private static void finalizeSpawn(Mob mob, ServerLevel level, MobSpawnType type, double eliteChance) {
        ECMobFramework.Config.setTestEliteChance(eliteChance);
        try {
            ForgeEventFactory.onFinalizeSpawn(mob, level, level.getCurrentDifficultyAt(mob.blockPosition()), type, null, null);
        } finally {
            ECMobFramework.Config.setTestEliteChance(null);
        }
    }

    /** 真玩家：过了出生无敌、生存模式、满血、在 relativeFeet */
    private static ServerPlayer survivor(GameTestHelper helper, String name, Vec3 relativeFeet) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        for (int i = 0; i <= 60; i++) {
            player.tick();
        }
        player.setGameMode(GameType.SURVIVAL);
        moveTo(helper, player, relativeFeet);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    private static void moveTo(GameTestHelper helper, Entity entity, Vec3 relative) {
        Vec3 at = helper.absoluteVec(relative);
        entity.moveTo(at.x, at.y, at.z, 0F, 0F);
    }

    /** 测试结构里 y = 1 那一层铺满石头（脚踩在 y = 2） */
    private static void stoneFloor(GameTestHelper helper, int size) {
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
    }

    private static Husk husk(GameTestHelper helper, Vec3 relative) {
        Husk husk = EntityType.HUSK.create(helper.getLevel());
        if (husk == null) {
            throw new IllegalStateException("尸壳应能创建");
        }
        moveTo(helper, husk, relative);
        husk.setNoAi(true);
        helper.getLevel().addFreshEntity(husk);
        return husk;
    }

    /** 从 shooter 眼前朝 aim 发一颗余火弹（不进世界，测试自己推） */
    private static NetherImpEmberBoltEntity bolt(NetherImpEntity shooter, Vec3 aim, float damage) {
        Vec3 from = new Vec3(shooter.getX(), shooter.getEyeY() - 0.15D, shooter.getZ());
        Vec3 dir = aim.subtract(from);
        return new NetherImpEmberBoltEntity(shooter.level(), shooter, dir.x, dir.y, dir.z, damage);
    }

    /** 手动推余火弹，最多 maxTicks 刻或者它没了为止 */
    private static void fly(NetherImpEmberBoltEntity bolt, int maxTicks) {
        for (int i = 0; i < maxTicks && !bolt.isRemoved(); i++) {
            bolt.tick();
        }
    }

    private static void discard(Entity... entities) {
        for (Entity entity : entities) {
            if (entity != null && !entity.isRemoved()) {
                entity.discard();
            }
        }
    }

    private static ImpValues impWith(double fleeHealth, int burnSeconds, int eliteSummonCount) {
        return new ImpValues(IMP.maxHealth(), IMP.armor(), IMP.boltDamage(), burnSeconds, IMP.shootInterval(), IMP.shootRange(), fleeHealth,
                IMP.fleeTicks(), IMP.fleeCooldown(), IMP.eliteBurst(), eliteSummonCount, IMP.eliteSummonCooldown(), IMP.nearbyCap());
    }

    private static StalkerValues stalkerPhases(int solid, int warning, int phase) {
        return new StalkerValues(STALKER.maxHealth(), STALKER.armor(), STALKER.attackDamage(), solid, warning, phase, STALKER.riftClawCooldown(),
                STALKER.blinkMin(), STALKER.blinkMax(), STALKER.pullCooldown(), STALKER.pullWarning(), STALKER.pullRange(), STALKER.spawnInterval(),
                STALKER.spawnChance(), STALKER.spawnMin(), STALKER.spawnMax(), STALKER.spawnMaxNearby());
    }

    private static StalkerValues stalkerMoves(int riftClawCooldown, int blinkMin, int blinkMax, int pullCooldown) {
        return new StalkerValues(STALKER.maxHealth(), STALKER.armor(), STALKER.attackDamage(), STALKER.solidTicks(), STALKER.warningTicks(),
                STALKER.phaseTicks(), riftClawCooldown, blinkMin, blinkMax, pullCooldown, STALKER.pullWarning(), STALKER.pullRange(),
                STALKER.spawnInterval(), STALKER.spawnChance(), STALKER.spawnMin(), STALKER.spawnMax(), STALKER.spawnMaxNearby());
    }

    private static StalkerValues stalkerSpawner(double chance, int min, int max, int maxNearby) {
        return new StalkerValues(STALKER.maxHealth(), STALKER.armor(), STALKER.attackDamage(), STALKER.solidTicks(), STALKER.warningTicks(),
                STALKER.phaseTicks(), STALKER.riftClawCooldown(), STALKER.blinkMin(), STALKER.blinkMax(), STALKER.pullCooldown(),
                STALKER.pullWarning(), STALKER.pullRange(), STALKER.spawnInterval(), chance, min, max, maxNearby);
    }

    private static boolean near(double actual, double expected) {
        return Math.abs(actual - expected) < 1.0E-3D;
    }

    private static long eliteModifierCount(LivingEntity entity) {
        long count = 0;
        for (var attribute : List.of(Attributes.MAX_HEALTH, Attributes.ATTACK_DAMAGE, Attributes.ARMOR, Attributes.KNOCKBACK_RESISTANCE)) {
            AttributeInstance instance = entity.getAttribute(attribute);
            if (instance != null) {
                count += instance.getModifiers().stream().filter(m -> m.getId().equals(ECMonster.ELITE_HEALTH_ID) || m.getId().equals(ECMonster.ELITE_ATTACK_ID)
                        || m.getId().equals(ECMonster.ELITE_ARMOR_ID) || m.getId().equals(ECMonster.ELITE_KNOCKBACK_ID)).count();
            }
        }
        return count;
    }

    private static long phaseSpeedModifiers(VoidStalkerEntity stalker) {
        AttributeInstance speed = stalker.getAttribute(Attributes.MOVEMENT_SPEED);
        return speed == null ? 0 : speed.getModifiers().stream().map(AttributeModifier::getId).filter(VoidStalkerEntity.PHASE_SPEED_ID::equals).count();
    }

    // ================================================================== 炼狱小鬼

    /**
     * 生成：走生成事件时按配置设成 16 / 2 / 4、满血；会飞、不受重力、免火；改了配置只影响以后生成的；
     * 精英生命 ×2.5、余火弹伤害（攻击属性）×1.4，读档两遍属性不叠；精英吐的余火弹伤害跟着 ×1.4。
     */
    @GameTest(template = EMPTY)
    public static void netherImpSpawnsWithConfiguredStats(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        NetherImpEntity imp = imp(helper, new Vec3(2.5D, 2D, 2.5D));
        finalizeSpawn(imp, level, MobSpawnType.NATURAL, 0D);
        helper.assertTrue(near(imp.getMaxHealth(), 16D) && near(imp.getHealth(), 16D), "生命 16，实际 " + imp.getMaxHealth());
        helper.assertTrue(near(imp.getAttributeValue(Attributes.ARMOR), 2D), "护甲 2");
        helper.assertTrue(near(imp.getAttributeValue(Attributes.ATTACK_DAMAGE), 4D), "余火弹伤害 4");
        helper.assertTrue(imp instanceof ECFlyingMonster && imp.isNoGravity() && imp.fireImmune(), "会飞、不受重力、免火");
        helper.assertTrue(SpawnOrigin.of(imp) == Origin.NATURAL && SpawnOrigin.isFair(imp) && !imp.isElite(), "自然生成：来源公平、几率 0 时不是精英");
        helper.assertTrue(imp.getType().is(DemonTrades.SOUL_CAPTURABLE), "灵魂能收（soul_capturable）");

        NetherImpEntity later = imp(helper, new Vec3(1.5D, 2D, 1.5D));
        ECNetherEndMobs.Config.setTestImp(new ImpValues(20D, 5D, 6D, IMP.burnSeconds(), IMP.shootInterval(), IMP.shootRange(), IMP.fleeHealth(),
                IMP.fleeTicks(), IMP.fleeCooldown(), IMP.eliteBurst(), IMP.eliteSummonCount(), IMP.eliteSummonCooldown(), IMP.nearbyCap()));
        try {
            finalizeSpawn(later, level, MobSpawnType.NATURAL, 0D);
        } finally {
            ECNetherEndMobs.Config.setTestImp(null);
        }
        helper.assertTrue(near(later.getMaxHealth(), 20D) && near(later.getAttributeValue(Attributes.ARMOR), 5D)
                && near(later.getAttributeValue(Attributes.ATTACK_DAMAGE), 6D), "改了配置以后生成的按新值");
        helper.assertTrue(near(imp.getMaxHealth(), 16D), "已经在世界里的不跟着改");

        NetherImpEntity chief = imp(helper, new Vec3(3.5D, 2D, 3.5D));
        finalizeSpawn(chief, level, MobSpawnType.NATURAL, 1D);
        helper.assertTrue(chief.isElite(), "几率钉成 1 时自然生成的是精英");
        helper.assertTrue(near(chief.getMaxHealth(), 40D) && near(chief.getAttributeValue(Attributes.ATTACK_DAMAGE), 5.6D),
                "小鬼头目生命 40、余火弹伤害 5.6，实际 " + chief.getMaxHealth() + " / " + chief.getAttributeValue(Attributes.ATTACK_DAMAGE));
        chief.setHealth(25F);
        NetherImpEntity reloaded = ECNetherEndMobs.NETHER_IMP.get().create(level);
        reloaded.load(chief.saveWithoutId(new CompoundTag()));
        NetherImpEntity again = ECNetherEndMobs.NETHER_IMP.get().create(level);
        again.load(reloaded.saveWithoutId(new CompoundTag()));
        helper.assertTrue(again.isElite() && near(again.getMaxHealth(), 40D) && near(again.getHealth(), 25D)
                && near(again.getAttributeValue(Attributes.ATTACK_DAMAGE), 5.6D) && eliteModifierCount(again) == 4,
                "读档两遍：还是精英、生命和伤害不叠、四个精英修饰符各一个，实际 " + again.getMaxHealth() + " / " + eliteModifierCount(again));

        Husk target = husk(helper, new Vec3(3.5D, 2D, 1.5D));
        try {
            NetherImpEmberBoltEntity shot = chief.shootAt(target);
            helper.assertTrue(shot != null && near(shot.damage(), 5.6D) && shot.getOwner() == chief, "头目吐的余火弹伤害 5.6、记着是谁吐的");
            discard(shot);
        } finally {
            discard(target);
        }
        helper.succeed();
    }

    /**
     * 余火弹打活物：从盔甲架和另一只小鬼中间穿过去，打中后面的尸壳；伤害按护甲算过的 4 点、伤害类型 {@code mob_bolt}（算弹射物、
     * 直接实体是弹、攻击者是小鬼）、目标烧起来；打中就没了。
     */
    @GameTest(template = LARGE)
    public static void netherImpEmberBoltHitsLivingOnly(GameTestHelper helper) {
        NetherImpEntity shooter = imp(helper, new Vec3(1.5D, 2D, 5.5D));
        shooter.applyConfiguredAttributes();
        ArmorStand stand = new ArmorStand(helper.getLevel(), 0D, 0D, 0D);
        moveTo(helper, stand, new Vec3(3.5D, 2D, 5.5D));
        helper.getLevel().addFreshEntity(stand);
        NetherImpEntity friend = imp(helper, new Vec3(5.5D, 2.5D, 5.5D));
        friend.setNoAi(true);
        helper.getLevel().addFreshEntity(friend);
        Husk target = husk(helper, new Vec3(8.5D, 2D, 5.5D));
        try {
            float before = target.getHealth();
            NetherImpEmberBoltEntity bolt = bolt(shooter, new Vec3(target.getX(), target.getY(0.5D), target.getZ()), 4F);
            fly(bolt, 60);
            helper.assertTrue(bolt.isRemoved(), "打中就没了");
            float expected = CombatRules.getDamageAfterAbsorb(4F, (float) target.getArmorValue(), (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
            helper.assertTrue(Math.abs(before - target.getHealth() - expected) < 1.0E-3F,
                    "穿过盔甲架和别的小鬼、打中后面的尸壳，掉 " + expected + "，实际 " + (before - target.getHealth()));
            DamageSource source = target.getLastDamageSource();
            helper.assertTrue(source != null && source.is(ECDamageTypes.MOB_BOLT) && source.is(DamageTypeTags.IS_PROJECTILE)
                    && source.getDirectEntity() == bolt && source.getEntity() == shooter, "伤害类型 mob_bolt、算弹射物、攻击者是小鬼：" + source);
            helper.assertTrue(target.getRemainingFireTicks() > 0, "打中的目标烧起来");
            helper.assertTrue(stand.isAlive() && friend.isAlive() && near(friend.getHealth(), friend.getMaxHealth()), "盔甲架、别的小鬼不受影响");
        } finally {
            discard(stand, friend, target);
        }
        helper.succeed();
    }

    /** 余火弹落地不放火、不碰方块；{@code burn_seconds} 填 0 时打中也不点火；命中事件被拦下（别的模组 / 混合端插件）时什么都不做 */
    @GameTest(template = LARGE)
    public static void netherImpEmberBoltNoFire(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.NETHERRACK);
        NetherImpEntity shooter = imp(helper, new Vec3(2.5D, 5D, 2.5D));
        NetherImpEmberBoltEntity down = bolt(shooter, helper.absoluteVec(new Vec3(2.5D, 2D, 2.5D)), 4F);
        fly(down, 60);
        helper.assertTrue(down.isRemoved(), "落地就熄");
        helper.assertBlockPresent(Blocks.NETHERRACK, new BlockPos(2, 1, 2));
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(2, 2, 2));

        Husk cold = husk(helper, new Vec3(8.5D, 2D, 5.5D));
        Husk shielded = husk(helper, new Vec3(8.5D, 2D, 8.5D));
        NetherImpEmberBoltEntity blocked = null;
        Consumer<ProjectileImpactEvent> cancel = null;
        try {
            NetherImpEntity side = imp(helper, new Vec3(1.5D, 2D, 5.5D));
            ECNetherEndMobs.Config.setTestImp(impWith(IMP.fleeHealth(), 0, IMP.eliteSummonCount()));
            float before = cold.getHealth();
            try {
                NetherImpEmberBoltEntity bolt = bolt(side, new Vec3(cold.getX(), cold.getY(0.5D), cold.getZ()), 4F);
                fly(bolt, 60);
            } finally {
                ECNetherEndMobs.Config.setTestImp(null);
            }
            helper.assertTrue(cold.getHealth() < before && cold.getRemainingFireTicks() <= 0, "burn_seconds 0：照样掉血、不点火");

            NetherImpEntity third = imp(helper, new Vec3(1.5D, 2D, 8.5D));
            blocked = bolt(third, new Vec3(shielded.getX(), shielded.getY(0.5D), shielded.getZ()), 4F);
            NetherImpEmberBoltEntity watched = blocked;
            cancel = event -> {
                if (event.getProjectile() == watched) {
                    event.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
                }
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ProjectileImpactEvent.class, cancel);
            float shieldedBefore = shielded.getHealth();
            fly(blocked, 25);
            helper.assertTrue(near(shielded.getHealth(), shieldedBefore) && shielded.getRemainingFireTicks() <= 0, "命中事件被拦下：不掉血、不点火");
        } finally {
            if (cancel != null) {
                MinecraftForge.EVENT_BUS.unregister(cancel);
            }
            discard(blocked, cold, shielded);
        }
        helper.succeed();
    }

    /**
     * 余火弹穿过的方块一概不碰：贴着地飞过木压力板和绊线（同 {@code WildMobsTests.wildBoltsIgnoreBlockTriggers} 那条道），
     * 板子不压下、绊线和钩子不通电；不换世界。
     */
    @GameTest(template = LARGE)
    public static void netherImpEmberBoltIgnoresBlockTriggers(GameTestHelper helper) {
        WildMobsTests.triggerLane(helper);
        NetherImpEntity shooter = imp(helper, new Vec3(2.5D, 2D, 0.5D));
        NetherImpEmberBoltEntity bolt = new NetherImpEmberBoltEntity(helper.getLevel(), shooter, 0D, 0D, 1D, 4F);
        helper.assertFalse(WildMobsTests.flyThroughLane(helper, bolt, new Vec3(0D, 0D, 0.3D), 12), "余火弹飞过压力板和绊线：都不触发");
        helper.assertTrue(bolt.isIgnoringBlockTriggers() && !bolt.canChangeDimensions(), "余火弹不踩机关、不换世界");
        helper.succeed();
    }

    /**
     * 残血逃：满血不逃；生命到 30% 以下、有目标时要逃，逃的方向背着目标、往高处；真跑 AI 时会进入逃跑状态；
     * {@code flee_health} 填 0 不逃；冷却存盘、读档后冷却中不逃。另核「不规则飞」：换位置的点在目标 4~9 格、高出 1.5~4.5 格。
     */
    @GameTest(template = LARGE, timeoutTicks = 100)
    public static void netherImpFleesWhenLow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Husk target = husk(helper, new Vec3(2.5D, 2D, 5.5D));
        NetherImpEntity imp = imp(helper, new Vec3(5.5D, 3D, 5.5D));
        imp.applyConfiguredAttributes();
        imp.setTarget(target);
        helper.assertFalse(imp.wantsToFlee(), "满血不逃");
        imp.setHealth(4F);
        helper.assertTrue(imp.wantsToFlee(), "生命 4 / 16 < 30%：要逃");
        Vec3 dest = imp.fleeDestination(target);
        if (dest != null) {
            Vec3 away = imp.position().subtract(target.position()).multiply(1D, 0D, 1D).normalize();
            Vec3 move = dest.subtract(imp.position());
            helper.assertTrue(move.multiply(1D, 0D, 1D).normalize().dot(away) > 0.6D && move.y >= 2D - EPS, "往背离目标的方向、往上逃：" + move);
        }
        Vec3 strafe = imp.strafePoint(target);
        if (strafe != null) {
            double horizontal = Math.sqrt(Math.pow(strafe.x - target.getX(), 2) + Math.pow(strafe.z - target.getZ(), 2));
            double above = strafe.y - target.getY();
            helper.assertTrue(horizontal >= 4D - EPS && horizontal <= 9D + EPS && above >= 1.5D - EPS && above <= 4.5D + EPS,
                    "绕着目标换位置：水平 " + horizontal + "、高出 " + above);
        }
        ECNetherEndMobs.Config.setTestImp(impWith(0D, IMP.burnSeconds(), IMP.eliteSummonCount()));
        try {
            helper.assertFalse(imp.wantsToFlee(), "flee_health 0：不逃");
        } finally {
            ECNetherEndMobs.Config.setTestImp(null);
        }
        CompoundTag saved = imp.saveWithoutId(new CompoundTag());
        saved.putInt("flee_cooldown", 100);
        NetherImpEntity cooling = ECNetherEndMobs.NETHER_IMP.get().create(level);
        cooling.load(saved);
        cooling.setTarget(target);
        helper.assertTrue(near(cooling.getHealth(), 4D) && !cooling.wantsToFlee(), "读档后逃跑冷却还在：不逃");

        level.addFreshEntity(imp);
        imp.setTarget(target);
        helper.succeedWhen(() -> {
            helper.assertTrue(imp.isFleeing(), "跑起 AI 以后进入逃跑状态");
            discard(imp, target);
        });
    }

    /**
     * 小鬼头目叫帮手：普通小鬼不叫；头目叫来 2 只（精英几率钉成 1 也不是精英），来源「召唤」、不公平（玩家全程亲手打死也拿不到关键掉落）、
     * 没经验、记着头目；活着的够数就不再叫，死一只补一只；两只头目各叫各的；{@code elite_summon_count} 填 0 不叫；
     * 生成被取消（混合端插件）时一只都不进世界。
     */
    @GameTest(template = LARGE)
    public static void netherImpChieftainSummons(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        NetherImpEntity plain = imp(helper, new Vec3(2.5D, 3D, 2.5D));
        NetherImpEntity chief = imp(helper, new Vec3(5.5D, 3D, 5.5D));
        chief.setElite(true);
        NetherImpEntity rival = imp(helper, new Vec3(7.5D, 3D, 7.5D));
        rival.setElite(true);
        ServerPlayer player = survivor(helper, "imp-chief", new Vec3(5.5D, 2D, 9.5D));
        List<NetherImpEntity> spawned = new ArrayList<>();
        Consumer<MobSpawnEvent.FinalizeSpawn> cancel = event -> {
            if (event.getEntity() instanceof NetherImpEntity minion && chief.getUUID().equals(minion.chieftain())) {
                event.setSpawnCancelled(true);
            }
        };
        try {
            helper.assertTrue(plain.summonMinions(player) == 0, "普通小鬼不叫帮手");
            int count;
            ECMobFramework.Config.setTestEliteChance(1D);
            try {
                count = chief.summonMinions(player);
            } finally {
                ECMobFramework.Config.setTestEliteChance(null);
            }
            helper.assertTrue(count == IMP.eliteSummonCount() && chief.minionCount() == count, "头目叫来 " + IMP.eliteSummonCount() + " 只，实际 " + count);
            spawned.addAll(level.getEntitiesOfClass(NetherImpEntity.class, chief.getBoundingBox().inflate(8D), e -> chief.getUUID().equals(e.chieftain())));
            helper.assertTrue(spawned.size() == count, "叫来的都进了世界、记着头目");
            for (NetherImpEntity minion : spawned) {
                helper.assertTrue(SpawnOrigin.of(minion) == Origin.SUMMONED && !SpawnOrigin.isFair(minion), "来源是召唤、不公平");
                helper.assertTrue(!minion.isElite() && minion.getExperienceReward() == 0, "叫来的不是精英、不给经验");
                helper.assertTrue(minion.getTarget() == player, "叫来就盯着头目的目标");
                MobDamageShare.record(minion, player.getUUID(), 16F);
                helper.assertFalse(FairKillCondition.test(minion, player), "玩家亲手打死叫来的也不算公平击杀");
            }
            helper.assertTrue(chief.summonMinions(player) == 0, "活着的够数就不再叫");
            spawned.get(0).discard();
            int refill = chief.summonMinions(player);
            spawned.addAll(level.getEntitiesOfClass(NetherImpEntity.class, chief.getBoundingBox().inflate(8D),
                    e -> chief.getUUID().equals(e.chieftain()) && !spawned.contains(e)));
            helper.assertTrue(refill == 1 && chief.minionCount() == IMP.eliteSummonCount(), "死一只补一只，实际补了 " + refill);
            int rivalCount = rival.summonMinions(player);
            spawned.addAll(level.getEntitiesOfClass(NetherImpEntity.class, rival.getBoundingBox().inflate(8D), e -> rival.getUUID().equals(e.chieftain())));
            helper.assertTrue(rivalCount == IMP.eliteSummonCount() && chief.minionCount() == IMP.eliteSummonCount(), "另一只头目按自己的数叫，不算别人的");

            for (NetherImpEntity minion : List.copyOf(spawned)) {
                minion.discard();
            }
            ECNetherEndMobs.Config.setTestImp(impWith(IMP.fleeHealth(), IMP.burnSeconds(), 0));
            try {
                helper.assertTrue(chief.summonMinions(player) == 0, "elite_summon_count 0：不叫");
            } finally {
                ECNetherEndMobs.Config.setTestImp(null);
            }
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, MobSpawnEvent.FinalizeSpawn.class, cancel);
            int canceled;
            try {
                canceled = chief.summonMinions(player);
            } finally {
                MinecraftForge.EVENT_BUS.unregister(cancel);
            }
            helper.assertTrue(canceled == 0 && chief.minionCount() == 0, "生成被取消：一只都不进世界");
        } finally {
            for (NetherImpEntity minion : spawned) {
                discard(minion);
            }
            for (NetherImpEntity left : level.getEntitiesOfClass(NetherImpEntity.class, chief.getBoundingBox().inflate(16D),
                    e -> chief.getUUID().equals(e.chieftain()) || rival.getUUID().equals(e.chieftain()))) {
                discard(left);
            }
        }
        helper.succeed();
    }

    private static List<ItemStack> lootFor(ServerLevel level, LivingEntity victim, ServerPlayer killer, String table, long seed) {
        LootTable loot = level.getServer().getLootData().getLootTable(EssentialCraft5.id(table));
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, victim)
                .withParameter(LootContextParams.ORIGIN, victim.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, killer.damageSources().playerAttack(killer))
                .withOptionalParameter(LootContextParams.KILLER_ENTITY, killer)
                .withOptionalParameter(LootContextParams.DIRECT_KILLER_ENTITY, killer)
                .withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer)
                .create(LootContextParamSets.ENTITY);
        return loot.getRandomItems(params, seed);
    }

    private static int countOf(List<ItemStack> drops, net.minecraft.world.item.Item item) {
        int count = 0;
        for (ItemStack stack : drops) {
            if (!stack.is(item)) {
                return -1;
            }
            count += stack.getCount();
        }
        return count;
    }

    /**
     * 掉落：野外的、玩家亲手打死的掉烈焰粉 / 末影珍珠（1~2 + 抢夺）；刷怪笼来的、伤害大半不是玩家打的什么都不掉；
     * 精英多一池（2~4）；表里只有这两样东西。
     */
    @GameTest(template = EMPTY)
    public static void netherEndMobsLootTables(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        record Case(String table, java.util.function.Supplier<? extends ECMonster> make, net.minecraft.world.item.Item item) {
        }
        List<Case> cases = List.of(
                new Case("entities/nether_imp", () -> imp(helper, new Vec3(2.5D, 2D, 2.5D)), Items.BLAZE_POWDER),
                new Case("entities/void_stalker", () -> stalker(helper, new Vec3(2.5D, 2D, 2.5D)), Items.ENDER_PEARL));
        long seed = 13L;
        for (Case c : cases) {
            ECMonster fair = c.make().get();
            SpawnOrigin.set(fair, Origin.NATURAL);
            ServerPlayer killer = survivor(helper, "loot-a", new Vec3(1.5D, 2D, 1.5D));
            MobDamageShare.record(fair, killer.getUUID(), 20F);
            int normal = countOf(lootFor(level, fair, killer, c.table(), seed++), c.item());
            helper.assertTrue(normal >= 1 && normal <= 2, c.table() + "：公平击杀掉 1~2 个，实际 " + normal);

            ECMonster caged = c.make().get();
            SpawnOrigin.set(caged, Origin.SPAWNER);
            ServerPlayer farmer = survivor(helper, "loot-b", new Vec3(1.5D, 2D, 1.5D));
            MobDamageShare.record(caged, farmer.getUUID(), 20F);
            helper.assertTrue(lootFor(level, caged, farmer, c.table(), seed++).isEmpty(), c.table() + "：刷怪笼来的什么都不掉");

            ECMonster lava = c.make().get();
            SpawnOrigin.set(lava, Origin.NATURAL);
            ServerPlayer finisher = survivor(helper, "loot-c", new Vec3(1.5D, 2D, 1.5D));
            MobDamageShare.record(lava, finisher.getUUID(), 4F);
            MobDamageShare.record(lava, null, 16F);
            helper.assertTrue(lootFor(level, lava, finisher, c.table(), seed++).isEmpty(), c.table() + "：伤害大半是环境打的，补刀也不掉");

            ECMonster elite = c.make().get();
            elite.setElite(true);
            SpawnOrigin.set(elite, Origin.NATURAL);
            ServerPlayer hunter = survivor(helper, "loot-d", new Vec3(1.5D, 2D, 1.5D));
            MobDamageShare.record(elite, hunter.getUUID(), 50F);
            int eliteCount = countOf(lootFor(level, elite, hunter, c.table(), seed++), c.item());
            helper.assertTrue(eliteCount >= 3 && eliteCount <= 6, c.table() + "：精英多一池，共 3~6 个，实际 " + eliteCount);
        }
        helper.succeed();
    }

    /**
     * 刷怪表（batch13.md §5.1 挤占表）：下界荒地权重 5、灵魂沙峡谷 2，各占本群系怪物抽选不到 3%；灵魂沙峡谷有刷怪花费（同那里的原版怪）；
     * 绯红森林、诡异森林、玄武岩三角洲不刷。虚空潜行者不在任何群系的刷怪表里，末地外岛的末影人照旧是权重 10。
     */
    @GameTest(template = EMPTY)
    public static void netherEndMobsSpawnTables(GameTestHelper helper) {
        Registry<Biome> biomes = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        EntityType<NetherImpEntity> imp = ECNetherEndMobs.NETHER_IMP.get();
        record Expect(ResourceKey<Biome> biome, int weight) {
        }
        for (Expect expect : List.of(new Expect(Biomes.NETHER_WASTES, 5), new Expect(Biomes.SOUL_SAND_VALLEY, 2), new Expect(Biomes.CRIMSON_FOREST, 0),
                new Expect(Biomes.WARPED_FOREST, 0), new Expect(Biomes.BASALT_DELTAS, 0))) {
            MobSpawnSettings settings = biomes.getHolderOrThrow(expect.biome()).value().getMobSettings();
            int total = 0;
            int mine = 0;
            for (MobSpawnSettings.SpawnerData data : settings.getMobs(MobCategory.MONSTER).unwrap()) {
                total += data.getWeight().asInt();
                if (data.type == imp) {
                    mine += data.getWeight().asInt();
                }
            }
            helper.assertTrue(mine == expect.weight(), expect.biome().location() + " 里炼狱小鬼权重应是 " + expect.weight() + "，实际 " + mine);
            helper.assertTrue(total > 0 && (double) mine / total <= 0.03D, expect.biome().location() + " 挤占 " + mine + " / " + total + " 超过 3%");
        }
        MobSpawnSettings.MobSpawnCost cost = biomes.getHolderOrThrow(Biomes.SOUL_SAND_VALLEY).value().getMobSettings().getMobSpawnCost(imp);
        helper.assertTrue(cost != null && near(cost.charge(), 0.7D) && near(cost.energyBudget(), 0.15D), "灵魂沙峡谷的刷怪花费：" + cost);

        EntityType<VoidStalkerEntity> stalker = ECNetherEndMobs.VOID_STALKER.get();
        for (Holder.Reference<Biome> holder : biomes.holders().toList()) {
            MobSpawnSettings settings = holder.value().getMobSettings();
            for (MobCategory category : MobCategory.values()) {
                for (MobSpawnSettings.SpawnerData data : settings.getMobs(category).unwrap()) {
                    helper.assertTrue(data.type != stalker, "虚空潜行者不该进 " + holder.key().location() + " 的刷怪表");
                }
            }
        }
        for (ResourceKey<Biome> outer : List.of(Biomes.END_HIGHLANDS, Biomes.END_MIDLANDS, Biomes.SMALL_END_ISLANDS, Biomes.END_BARRENS)) {
            List<MobSpawnSettings.SpawnerData> monsters = biomes.getHolderOrThrow(outer).value().getMobSettings().getMobs(MobCategory.MONSTER).unwrap();
            int enderman = monsters.stream().filter(d -> d.type == EntityType.ENDERMAN).mapToInt(d -> d.getWeight().asInt()).sum();
            helper.assertTrue(enderman == 10, outer.location() + " 的末影人权重应照旧是 10，实际 " + enderman);
        }
        helper.succeed();
    }

    // ================================================================== 虚空潜行者

    /** 生成：45 / 8 / 6；精英 112.5 / 12 / 8.4，读档两遍不叠；相位、剩余刻、两个冷却都存盘，相位的加速读档后补上且只有一个 */
    @GameTest(template = EMPTY)
    public static void voidStalkerSpawnsWithConfiguredStats(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VoidStalkerEntity plain = ECNetherEndMobs.VOID_STALKER.get().create(level);
        moveTo(helper, plain, new Vec3(2.5D, 2D, 2.5D));
        finalizeSpawn(plain, level, MobSpawnType.NATURAL, 0D);
        helper.assertTrue(near(plain.getMaxHealth(), 45D) && near(plain.getHealth(), 45D) && near(plain.getAttributeValue(Attributes.ARMOR), 8D)
                && near(plain.getAttributeValue(Attributes.ATTACK_DAMAGE), 6D), "45 / 8 / 6，实际 " + plain.getMaxHealth());
        helper.assertTrue(plain.phase() == VoidStalkerEntity.SOLID, "一出生是实体化的");

        VoidStalkerEntity elite = ECNetherEndMobs.VOID_STALKER.get().create(level);
        moveTo(helper, elite, new Vec3(1.5D, 2D, 1.5D));
        finalizeSpawn(elite, level, MobSpawnType.NATURAL, 1D);
        helper.assertTrue(elite.isElite() && near(elite.getMaxHealth(), 112.5D) && near(elite.getAttributeValue(Attributes.ARMOR), 12D)
                && near(elite.getAttributeValue(Attributes.ATTACK_DAMAGE), 8.4D), "裂隙行者 112.5 / 12 / 8.4，实际 " + elite.getMaxHealth());
        elite.setHealth(60F);
        elite.setPhase(VoidStalkerEntity.PHASED, 17);
        elite.setCooldowns(33, 44);
        helper.assertTrue(phaseSpeedModifiers(elite) == 1, "相位时挂着加速");
        VoidStalkerEntity once = ECNetherEndMobs.VOID_STALKER.get().create(level);
        once.load(elite.saveWithoutId(new CompoundTag()));
        VoidStalkerEntity twice = ECNetherEndMobs.VOID_STALKER.get().create(level);
        twice.load(once.saveWithoutId(new CompoundTag()));
        helper.assertTrue(twice.isElite() && near(twice.getMaxHealth(), 112.5D) && near(twice.getHealth(), 60D) && eliteModifierCount(twice) == 4,
                "读档两遍：精英属性不叠，实际 " + twice.getMaxHealth());
        helper.assertTrue(twice.isPhased() && twice.phaseTimer() == 17 && twice.riftClawCooldown() == 33 && twice.pullCooldown() == 44,
                "相位、剩余刻、冷却原样接上");
        helper.assertTrue(phaseSpeedModifiers(twice) == 1, "读档后相位加速补上、只有一个，实际 " + phaseSpeedModifiers(twice));
        twice.setPhase(VoidStalkerEntity.SOLID, 10);
        helper.assertTrue(phaseSpeedModifiers(twice) == 0, "回到实体化就摘掉加速");
        helper.succeed();
    }

    /** 相位循环：实体化（扣掉预警）→ 预警 → 相位 → 实体化，刻数照配置；预警填 0 直接进相位；{@code phase_ticks} 填 0 一直实体化 */
    @GameTest(template = EMPTY)
    public static void voidStalkerPhaseCycle(GameTestHelper helper) {
        VoidStalkerEntity stalker = stalker(helper, new Vec3(2.5D, 2D, 2.5D));
        ECNetherEndMobs.Config.setTestStalker(stalkerPhases(8, 2, 4));
        try {
            stalker.setPhase(VoidStalkerEntity.SOLID, 6);
            List<Byte> seen = new ArrayList<>();
            for (int i = 0; i < 24; i++) {
                stalker.tickPhase();
                seen.add(stalker.phase());
            }
            // 6 刻实体化 → 2 刻预警 → 4 刻相位 → 6 刻实体化 → 2 刻预警 → 4 刻相位
            byte[] expected = new byte[24];
            int[] lengths = {5, 2, 4, 6, 2, 4, 1};
            byte[] phases = {VoidStalkerEntity.SOLID, VoidStalkerEntity.WARNING, VoidStalkerEntity.PHASED, VoidStalkerEntity.SOLID,
                    VoidStalkerEntity.WARNING, VoidStalkerEntity.PHASED, VoidStalkerEntity.SOLID};
            int index = 0;
            for (int k = 0; k < lengths.length; k++) {
                for (int j = 0; j < lengths[k] && index < expected.length; j++) {
                    expected[index++] = phases[k];
                }
            }
            for (int i = 0; i < expected.length; i++) {
                helper.assertTrue(seen.get(i) == expected[i], "第 " + (i + 1) + " 刻应是 " + expected[i] + "，实际 " + seen);
            }

            ECNetherEndMobs.Config.setTestStalker(stalkerPhases(8, 0, 4));
            stalker.setPhase(VoidStalkerEntity.SOLID, 1);
            stalker.tickPhase();
            helper.assertTrue(stalker.isPhased(), "预警填 0：实体化完直接进相位");

            ECNetherEndMobs.Config.setTestStalker(stalkerPhases(8, 2, 0));
            stalker.tickPhase();
            helper.assertTrue(stalker.phase() == VoidStalkerEntity.SOLID && phaseSpeedModifiers(stalker) == 0, "phase_ticks 0：马上回到实体化");
            for (int i = 0; i < 50; i++) {
                stalker.tickPhase();
            }
            helper.assertTrue(stalker.phase() == VoidStalkerEntity.SOLID, "phase_ticks 0：一直实体化");
        } finally {
            ECNetherEndMobs.Config.setTestStalker(null);
        }
        helper.succeed();
    }

    /**
     * 相位时什么都不吃（玩家砍、箭、爆炸、火都算）、准星和弹射物穿过去、推不动、不出手、看得出来（半透明）；
     * 只有 {@code /kill}、掉进虚空这类无视无敌的照样死。预警时还吃伤害。
     */
    @GameTest(template = EMPTY)
    public static void voidStalkerPhasedIgnoresDamage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VoidStalkerEntity stalker = stalker(helper, new Vec3(2.5D, 2D, 2.5D));
        ServerPlayer player = survivor(helper, "stalk-hit", new Vec3(3.5D, 2D, 2.5D));
        Husk husk = EntityType.HUSK.create(level);
        moveTo(helper, husk, new Vec3(1.5D, 2D, 2.5D));
        stalker.setPhase(VoidStalkerEntity.PHASED, 40);
        float full = stalker.getHealth();
        List<DamageSource> sources = List.of(player.damageSources().playerAttack(player), level.damageSources().arrow(null, player),
                level.damageSources().explosion(null, player), level.damageSources().inFire(), level.damageSources().magic());
        for (DamageSource source : sources) {
            stalker.invulnerableTime = 0;
            helper.assertFalse(stalker.hurt(source, 5F), "相位时不吃 " + source.getMsgId());
        }
        helper.assertTrue(near(stalker.getHealth(), full), "相位时一点血都不掉");
        helper.assertTrue(!stalker.isPickable() && !stalker.isPushable(), "准星、弹射物穿过去，推不动");
        helper.assertFalse(stalker.doHurtTarget(husk), "相位时不出手");
        helper.assertTrue(near(husk.getHealth(), husk.getMaxHealth()), "相位时打不到人");
        helper.assertTrue(stalker.phaseAlpha(0F) <= VoidStalkerEntity.PHASED_ALPHA + EPS, "相位时画面半透明：" + stalker.phaseAlpha(0F));
        stalker.invulnerableTime = 0;
        helper.assertTrue(stalker.hurt(level.damageSources().fellOutOfWorld(), 5F) && stalker.getHealth() < full, "掉进虚空照样掉血");

        stalker.setPhase(VoidStalkerEntity.WARNING, 10);
        float warned = stalker.getHealth();
        stalker.invulnerableTime = 0;
        helper.assertTrue(stalker.hurt(player.damageSources().playerAttack(player), 5F) && stalker.getHealth() < warned, "预警时还吃伤害");
        helper.assertTrue(stalker.isPickable() && stalker.phaseAlpha(0F) < 1F, "预警时打得着、身子发虚");
        helper.succeed();
    }

    /**
     * 裂隙爪：把人闪到 2~4 格外（测试配置），落点脚下三格内有地；四周脚下都悬空时闪不了、人不动；落点脚下是岩浆块、岩浆、
     * 落点泡在水里、着着火、落下去要穿过浆果丛时不算落点；传送被取消（混合端插件）时不动；创造模式的不闪。
     */
    @GameTest(template = LARGE)
    public static void voidStalkerRiftClawBlinks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        stoneFloor(helper, 11);
        VoidStalkerEntity stalker = stalker(helper, new Vec3(5.5D, 2D, 3.5D));
        ServerPlayer player = survivor(helper, "stalk-blink", new Vec3(5.5D, 2D, 5.5D));
        Consumer<EntityTeleportEvent> cancel = event -> {
            if (event.getEntity() == player) {
                event.setCanceled(true);
            }
        };
        ECNetherEndMobs.Config.setTestStalker(stalkerMoves(STALKER.riftClawCooldown(), 2, 4, STALKER.pullCooldown()));
        try {
            Vec3 before = player.position();
            helper.assertTrue(stalker.riftClaw(player), "四周有地：闪得走");
            double moved = VoidStalkerRift.horizontalDistance(before, player.position());
            double floorY = helper.absoluteVec(new Vec3(0D, 2D, 0D)).y;
            helper.assertTrue(moved >= 1.2D && moved <= 4.8D && player.getY() >= floorY - EPS && player.getY() <= floorY + 2D + EPS,
                    "闪到 2~4 格外、脚下三格内有地：水平 " + moved + "、高 " + player.getY());
            helper.assertTrue(player.fallDistance == 0F, "闪完清摔落距离");

            moveTo(helper, player, new Vec3(5.5D, 7D, 5.5D));
            Vec3 floating = player.position();
            helper.assertFalse(stalker.riftClaw(player), "四周脚下三格都是空的：不闪");
            helper.assertTrue(player.position().distanceToSqr(floating) < EPS, "闪不了就不动");

            moveTo(helper, player, new Vec3(5.5D, 2D, 5.5D));
            Vec3 standing = player.position();
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EntityTeleportEvent.class, cancel);
            try {
                helper.assertFalse(stalker.riftClaw(player), "传送被取消：算没闪");
            } finally {
                MinecraftForge.EVENT_BUS.unregister(cancel);
            }
            helper.assertTrue(player.position().distanceToSqr(standing) < EPS, "传送被取消：人不动");

            player.setGameMode(GameType.CREATIVE);
            helper.assertFalse(stalker.riftClaw(player), "创造模式的不是技能目标");
            player.setGameMode(GameType.SURVIVAL);

            BlockPos feet = new BlockPos(2, 2, 8);
            helper.assertTrue(VoidStalkerRift.landing(level, player, helper.absolutePos(feet)) != null, "石头上能落");
            helper.setBlock(new BlockPos(2, 1, 8), Blocks.MAGMA_BLOCK);
            helper.assertTrue(VoidStalkerRift.landing(level, player, helper.absolutePos(feet)) == null, "岩浆块上不落");
            helper.setBlock(new BlockPos(2, 1, 8), Blocks.LAVA);
            helper.assertTrue(VoidStalkerRift.landing(level, player, helper.absolutePos(feet)) == null, "岩浆上不落");
            helper.setBlock(new BlockPos(2, 1, 8), Blocks.STONE);
            helper.setBlock(feet, Blocks.WATER);
            helper.assertTrue(VoidStalkerRift.landing(level, player, helper.absolutePos(feet)) == null, "泡在水里的不算落点");
            helper.setBlock(feet, Blocks.FIRE);
            helper.assertTrue(VoidStalkerRift.landing(level, player, helper.absolutePos(feet)) == null, "落点着着火：不落");
            helper.setBlock(feet, Blocks.SWEET_BERRY_BUSH);
            helper.assertTrue(VoidStalkerRift.landing(level, player, helper.absolutePos(feet.above())) == null, "落下去要穿过浆果丛：不落");
            helper.setBlock(feet, Blocks.AIR);
            helper.assertTrue(VoidStalkerRift.landing(level, player, helper.absolutePos(feet.above())) != null, "高一格、脚下一格空着照样能落");
        } finally {
            ECNetherEndMobs.Config.setTestStalker(null);
        }
        helper.succeed();
    }

    /**
     * 裂隙爪只在打实的时候闪人：攻击被取消（插件 / 别的模组拦下）时人不动、冷却不消耗；打实了掉血、闪走、冷却挂上；
     * 冷却中的下一爪只是普通一爪；{@code rift_claw_cooldown} 填 0 时从不闪人。
     */
    @GameTest(template = LARGE)
    public static void voidStalkerRiftClawOnlyOnLandedHit(GameTestHelper helper) {
        stoneFloor(helper, 11);
        VoidStalkerEntity stalker = stalker(helper, new Vec3(5.5D, 2D, 4.5D));
        stalker.setPhase(VoidStalkerEntity.SOLID, 200);
        ServerPlayer player = survivor(helper, "stalk-claw", new Vec3(5.5D, 2D, 5.5D));
        Consumer<LivingAttackEvent> cancel = event -> {
            if (event.getEntity() == player) {
                event.setCanceled(true);
            }
        };
        ECNetherEndMobs.Config.setTestStalker(stalkerMoves(STALKER.riftClawCooldown(), 2, 4, STALKER.pullCooldown()));
        try {
            Vec3 start = player.position();
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingAttackEvent.class, cancel);
            boolean hit;
            try {
                hit = stalker.doHurtTarget(player);
            } finally {
                MinecraftForge.EVENT_BUS.unregister(cancel);
            }
            helper.assertTrue(!hit && player.position().distanceToSqr(start) < EPS && stalker.riftClawCooldown() == 0,
                    "攻击被拦下：人不动、冷却没用掉");

            float health = player.getHealth();
            player.invulnerableTime = 0;
            helper.assertTrue(stalker.doHurtTarget(player), "打得着");
            helper.assertTrue(player.getHealth() < health, "打实掉血");
            helper.assertTrue(VoidStalkerRift.horizontalDistance(start, player.position()) >= 1.2D, "打实了就闪走：" + player.position());
            helper.assertTrue(stalker.riftClawCooldown() == STALKER.riftClawCooldown(), "冷却挂上 " + STALKER.riftClawCooldown());

            moveTo(helper, player, new Vec3(5.5D, 2D, 5.5D));
            Vec3 again = player.position();
            player.invulnerableTime = 0;
            player.setHealth(player.getMaxHealth());
            helper.assertTrue(stalker.doHurtTarget(player) && player.position().distanceToSqr(again) < EPS, "冷却中：普通一爪，不闪");

            stalker.setCooldowns(0, 0);
            ECNetherEndMobs.Config.setTestStalker(stalkerMoves(0, 2, 4, STALKER.pullCooldown()));
            player.invulnerableTime = 0;
            player.setHealth(player.getMaxHealth());
            helper.assertTrue(stalker.doHurtTarget(player) && player.position().distanceToSqr(again) < EPS, "rift_claw_cooldown 0：从不闪人");
        } finally {
            ECNetherEndMobs.Config.setTestStalker(null);
        }
        helper.succeed();
    }

    /**
     * 裂隙行者拉人：目标在 5~16 格、看得见时起手，拉到自己跟前 2.5 格内；隔着墙（看不见）拉不动；普通潜行者、
     * {@code pull_cooldown} 填 0、目标太近（5 格内）、相位时、创造模式的都不起手；传送被取消时人不动。另一名旁观的玩家不受影响。
     */
    @GameTest(template = LARGE)
    public static void voidStalkerRiftwalkerPulls(GameTestHelper helper) {
        stoneFloor(helper, 11);
        VoidStalkerEntity walker = stalker(helper, new Vec3(1.5D, 2D, 5.5D));
        walker.setElite(true);
        walker.setPhase(VoidStalkerEntity.SOLID, 200);
        VoidStalkerEntity plain = stalker(helper, new Vec3(1.5D, 2D, 3.5D));
        plain.setPhase(VoidStalkerEntity.SOLID, 200);
        ServerPlayer target = survivor(helper, "stalk-pull", new Vec3(9.5D, 2D, 5.5D));
        ServerPlayer bystander = survivor(helper, "stalk-look", new Vec3(9.5D, 2D, 8.5D));
        Consumer<EntityTeleportEvent> cancel = event -> {
            if (event.getEntity() == target) {
                event.setCanceled(true);
            }
        };
        helper.assertTrue(walker.canStartPull(target), "8 格外、看得见：起手");
        helper.assertFalse(plain.canStartPull(target), "普通潜行者不拉人");
        Vec3 standing = target.position();
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EntityTeleportEvent.class, cancel);
        try {
            helper.assertFalse(walker.pull(target), "传送被取消：算没拉");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(cancel);
        }
        helper.assertTrue(target.position().distanceToSqr(standing) < EPS, "传送被取消：人不动");

        Vec3 lookerBefore = bystander.position();
        helper.assertTrue(walker.pull(target), "拉得动");
        helper.assertTrue(target.position().distanceTo(walker.position()) <= 2.6D + EPS, "拉到跟前：" + target.position().distanceTo(walker.position()));
        helper.assertTrue(bystander.position().distanceToSqr(lookerBefore) < EPS, "旁边的玩家不受影响");

        helper.assertFalse(walker.canStartPull(target), "贴身 5 格内不拉，直接打");
        moveTo(helper, target, new Vec3(9.5D, 2D, 5.5D));
        ECNetherEndMobs.Config.setTestStalker(stalkerMoves(STALKER.riftClawCooldown(), STALKER.blinkMin(), STALKER.blinkMax(), 0));
        try {
            helper.assertFalse(walker.canStartPull(target), "pull_cooldown 0：不拉人");
        } finally {
            ECNetherEndMobs.Config.setTestStalker(null);
        }
        walker.setPhase(VoidStalkerEntity.PHASED, 40);
        helper.assertTrue(!walker.canStartPull(target) && !walker.pull(target), "相位时不起手、不拉");
        walker.setPhase(VoidStalkerEntity.SOLID, 200);
        target.setGameMode(GameType.CREATIVE);
        helper.assertFalse(walker.canStartPull(target), "创造模式的不拉");
        target.setGameMode(GameType.SURVIVAL);

        for (int y = 2; y <= 5; y++) {
            for (int z = 3; z <= 7; z++) {
                helper.setBlock(new BlockPos(5, y, z), Blocks.STONE);
            }
        }
        Vec3 hidden = target.position();
        helper.assertTrue(!walker.canStartPull(target) && !walker.pull(target), "隔着墙看不见：不起手、到点也拉不动");
        helper.assertTrue(target.position().distanceToSqr(hidden) < EPS, "躲开了就不动");
        helper.succeed();
    }

    /** 挑落点用的随机数（种子乘个大奇数打散：挨着的小种子头一个数几乎一样） */
    private static RandomSource seeded(int i) {
        return RandomSource.create((i + 1) * 0x9E3779B97F4A7C15L);
    }

    /**
     * 裂隙爪不把人闪进封闭的屋子（batch13.md §7.5，policy §1.1 第 5 条）：目标在一间屋里 3×3、三格高、带顶的石屋正中，
     * 屋外 3~4 格全是能站的平地。闪不出去、人不动，换 16 个种子挑也都挑不出落点（不看连线的话，墙外的平地全都合格）。
     * 拆掉东墙以后挑得出落点，挑出来的全在东边、从目标眼睛连过去看得通，裂隙爪也闪得走。
     */
    @GameTest(template = LARGE)
    public static void voidStalkerRiftClawNoBlinkIntoSealedRoom(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        stoneFloor(helper, 11);
        // 石屋：外圈 x、z 3~7，墙 y 2~4，屋顶 y 5
        for (int x = 3; x <= 7; x++) {
            for (int z = 3; z <= 7; z++) {
                helper.setBlock(new BlockPos(x, 5, z), Blocks.STONE);
                if (x == 3 || x == 7 || z == 3 || z == 7) {
                    for (int y = 2; y <= 4; y++) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                    }
                }
            }
        }
        VoidStalkerEntity stalker = stalker(helper, new Vec3(4.5D, 2D, 5.5D));
        ServerPlayer player = survivor(helper, "stalk-room", new Vec3(5.5D, 2D, 5.5D));
        ECNetherEndMobs.Config.setTestStalker(stalkerMoves(STALKER.riftClawCooldown(), 3, 4, STALKER.pullCooldown()));
        try {
            Vec3 inside = player.position();
            helper.assertTrue(VoidStalkerRift.landing(level, player, helper.absolutePos(new BlockPos(9, 2, 5))) != null, "屋外的平地本身能站");
            helper.assertFalse(stalker.riftClaw(player), "关在封闭的石屋里：闪不出去");
            helper.assertTrue(player.position().distanceToSqr(inside) < EPS, "闪不出去就不动");
            for (int i = 0; i < 16; i++) {
                helper.assertTrue(VoidStalkerRift.blinkSpot(level, player, seeded(i), 3, 4, new Object()) == null,
                        "第 " + i + " 个种子也挑不出落点");
            }

            for (int z = 4; z <= 6; z++) {
                for (int y = 2; y <= 4; y++) {
                    helper.setBlock(new BlockPos(7, y, z), Blocks.AIR);
                }
            }
            double wallX = helper.absoluteVec(new Vec3(7D, 0D, 0D)).x;
            int found = 0;
            for (int i = 0; i < 16; i++) {
                Vec3 spot = VoidStalkerRift.blinkSpot(level, player, seeded(i), 3, 4, new Object());
                if (spot != null) {
                    found++;
                    helper.assertTrue(spot.x > wallX, "拆了东墙：落点都在东边，实际 " + spot);
                    helper.assertTrue(VoidStalkerRift.clearPath(level, player.getEyePosition(), spot.add(0D, player.getEyeHeight(), 0D)),
                            "从目标眼睛连到落点看得通：" + spot);
                }
            }
            helper.assertTrue(found > 0, "拆了东墙应挑得出落点");
            boolean blinked = false;
            for (int i = 0; i < 20 && !blinked; i++) {
                blinked = stalker.riftClaw(player);
            }
            helper.assertTrue(blinked && player.getX() > wallX, "拆了东墙：裂隙爪闪得走、落在东边，实际 " + player.position());
        } finally {
            ECNetherEndMobs.Config.setTestStalker(null);
        }
        helper.succeed();
    }

    /**
     * 裂隙行者不隔着墙拉人：潜行者贴着一间带顶石屋的北墙外站着，目标在东边 7 格、看得见；地面抬在 y = 4，
     * 屋外的候选点脚下都是空的，能站的只有墙里面那几格。隔着墙拉不进去、人不动。拆掉北墙以后拉得动，落在原来墙那一排、
     * 从潜行者眼睛连过去看得通。
     */
    @GameTest(template = LARGE)
    public static void voidStalkerRiftwalkerNoPullThroughWall(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(2, 4, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(9, 4, 2), Blocks.STONE);
        // 石屋：地板 y 4、外圈 x 2~6、z 3~6，墙 y 5~6，屋顶 y 7
        for (int x = 2; x <= 6; x++) {
            for (int z = 3; z <= 6; z++) {
                helper.setBlock(new BlockPos(x, 4, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, 7, z), Blocks.STONE);
                if (x == 2 || x == 6 || z == 3 || z == 6) {
                    helper.setBlock(new BlockPos(x, 5, z), Blocks.STONE);
                    helper.setBlock(new BlockPos(x, 6, z), Blocks.STONE);
                }
            }
        }
        VoidStalkerEntity walker = stalker(helper, new Vec3(2.5D, 5D, 2.5D));
        walker.setElite(true);
        walker.setPhase(VoidStalkerEntity.SOLID, 200);
        ServerPlayer target = survivor(helper, "stalk-wall", new Vec3(9.5D, 5D, 2.5D));
        Vec3 standing = target.position();
        helper.assertTrue(walker.hasLineOfSight(target), "看得见目标");
        helper.assertTrue(VoidStalkerRift.landing(level, target, helper.absolutePos(new BlockPos(3, 5, 4))) != null, "墙里面那格本身能站");
        helper.assertFalse(walker.pull(target), "能站的只有墙里面：拉不进去");
        helper.assertTrue(target.position().distanceToSqr(standing) < EPS, "拉不动就不动");

        for (int x = 3; x <= 5; x++) {
            helper.setBlock(new BlockPos(x, 5, 3), Blocks.AIR);
            helper.setBlock(new BlockPos(x, 6, 3), Blocks.AIR);
        }
        helper.assertTrue(walker.pull(target), "拆了北墙：拉得动");
        Vec3 at = target.position();
        helper.assertTrue(at.distanceTo(walker.position()) <= 2.6D + EPS, "拉到跟前：" + at.distanceTo(walker.position()));
        helper.assertTrue(VoidStalkerRift.clearPath(level, walker.getEyePosition(), at.add(0D, target.getEyeHeight(), 0D)),
                "从潜行者眼睛连到落点看得通");
        helper.succeed();
    }

    /**
     * 落点要对被挪的玩家放行（眼睛的连线能从 1.5 格高的栅栏上越过去，别人领地里用栅栏围的畜栏光看连线拦不住）：
     * 整块测试区都是别人的领地时，平地上裂隙爪闪不走、裂隙行者拉不动，人都不动，探测是以被挪的玩家的身份发的。
     * 这一刻潜行者的探测预算用完（给不出结论）也不闪、人不动；下一刻预算回来了照常闪。
     */
    @GameTest(template = LARGE)
    public static void voidStalkerMovesNeedPermission(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        stoneFloor(helper, 11);
        VoidStalkerEntity stalker = stalker(helper, new Vec3(4.5D, 2D, 5.5D));
        VoidStalkerEntity walker = stalker(helper, new Vec3(1.5D, 2D, 8.5D));
        walker.setElite(true);
        walker.setPhase(VoidStalkerEntity.SOLID, 200);
        ServerPlayer clawed = survivor(helper, "stalk-claim", new Vec3(5.5D, 2D, 5.5D));
        ServerPlayer pulled = survivor(helper, "stalk-claim2", new Vec3(9.5D, 2D, 8.5D));
        BoundingBox all = BoundingBox.fromCorners(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(10, 7, 10)));
        ECNetherEndMobs.Config.setTestStalker(stalkerMoves(STALKER.riftClawCooldown(), 3, 4, STALKER.pullCooldown()));
        try (Claims claims = Claims.open(helper).denyBox(all)) {
            Vec3 a = clawed.position();
            Vec3 b = pulled.position();
            helper.assertFalse(stalker.riftClaw(clawed), "四周都是别人的领地：闪不走");
            helper.assertFalse(walker.pull(pulled), "落点在别人的领地：拉不动");
            helper.assertTrue(clawed.position().distanceToSqr(a) < EPS && pulled.position().distanceToSqr(b) < EPS, "人都不动");
            int asClawed = 0;
            int asPulled = 0;
            for (BlockPos cell : BlockPos.betweenClosed(BlockPos.ZERO, new BlockPos(10, 5, 10))) {
                asClawed += claims.breaksBy(cell, clawed.getUUID());
                asPulled += claims.breaksBy(cell, pulled.getUUID());
            }
            helper.assertTrue(asClawed > 0 && asPulled > 0, "探测以被挪的玩家的身份发，实际 " + asClawed + " / " + asPulled);
        } finally {
            ECNetherEndMobs.Config.setTestStalker(null);
        }

        // 预算用完：先用这只潜行者的名义把本刻的探测预算花光（y 6~7 的格子不是落点）
        ServerPlayer later = survivor(helper, "stalk-budget", new Vec3(5.5D, 2D, 5.5D));
        VoidStalkerEntity tired = stalker(helper, new Vec3(4.5D, 2D, 5.5D));
        boolean exhausted = false;
        for (BlockPos cell : BlockPos.betweenClosed(new BlockPos(0, 6, 0), new BlockPos(10, 7, 10))) {
            if (ECProtection.check(later, level, helper.absolutePos(cell), tired) == ECProtection.Verdict.UNKNOWN) {
                exhausted = true;
                break;
            }
        }
        helper.assertTrue(exhausted, "本刻的探测预算应能花光");
        Vec3 start = later.position();
        ECNetherEndMobs.Config.setTestStalker(stalkerMoves(STALKER.riftClawCooldown(), 3, 4, STALKER.pullCooldown()));
        try {
            helper.assertFalse(tired.riftClaw(later), "这一刻给不出结论：不闪");
            helper.assertTrue(later.position().distanceToSqr(start) < EPS, "不闪就不动");
        } finally {
            ECNetherEndMobs.Config.setTestStalker(null);
        }
        helper.runAfterDelay(1, () -> {
            ECNetherEndMobs.Config.setTestStalker(stalkerMoves(STALKER.riftClawCooldown(), 3, 4, STALKER.pullCooldown()));
            try {
                boolean blinked = false;
                for (int i = 0; i < 10 && !blinked; i++) {
                    blinked = tired.riftClaw(later);
                }
                helper.assertTrue(blinked && later.position().distanceToSqr(start) > 1D, "下一刻预算回来了：照常闪");
            } finally {
                ECNetherEndMobs.Config.setTestStalker(null);
            }
            helper.succeed();
        });
    }

    /**
     * 独立生成器（照幻翼按玩家判，batch13.md §5.2、R-9）：只给外岛上的生存 / 冒险玩家刷，主岛、创造模式不刷；配置填 0 关掉；
     * 和平、到了原版怪物上限不刷；落脚处不在外岛不刷；刷出来的是「自然」来源、公平；64 格内已有 3 只不刷；生成被取消（混合端插件）时什么都不留。
     * 不进群系刷怪表（{@link #netherEndMobsSpawnTables}），所以不占末影人的刷怪尝试。
     */
    @GameTest(template = LARGE, batch = SPAWNER_BATCH)
    public static void voidStalkerSpawnerRules(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = survivor(helper, "stalk-spawn", new Vec3(5.5D, 2D, 5.5D));
        BlockPos center = player.blockPosition();
        // 找一个种子，让生成器第一次挑的那一列落在测试结构里（离结构边至少 2 格，四周好砌墙），而且那一列的正中离玩家不到 3 格不行
        // （findSpot 会因为「离玩家太近」跳过它）。候选种子乘一个大奇数打散：挨着的小种子第一次 nextDouble 几乎一样，挨个试找不到
        // 绝对坐标换相对坐标自己减：1.20.1 的 GameTestHelper.relativePos 多转了 180°，算出来的是相反数
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        long seed = -1L;
        BlockPos column = null;
        for (long s = 1; s < 2000 && column == null; s++) {
            long mixed = s * 0x9E3779B97F4A7C15L;
            RandomSource r = RandomSource.create(mixed);
            double angle = r.nextDouble() * Math.PI * 2D;
            double dist = 3D + r.nextDouble();
            int x = (int) Math.floor(center.getX() + 0.5D + Math.cos(angle) * dist);
            int z = (int) Math.floor(center.getZ() + 0.5D + Math.sin(angle) * dist);
            BlockPos relative = new BlockPos(x - origin.getX(), center.getY() - origin.getY(), z - origin.getZ());
            boolean farEnough = Math.hypot(x + 0.5D - player.getX(), z + 0.5D - player.getZ()) >= 3.1D;
            if (farEnough && relative.getX() >= 2 && relative.getX() <= 8 && relative.getZ() >= 2 && relative.getZ() <= 8) {
                seed = mixed;
                column = new BlockPos(relative.getX(), 1, relative.getZ());
            }
        }
        helper.assertTrue(column != null, "应能找到落在结构里的种子");
        // 落脚的那一列：末地石，四周砌一圈 4 格高的石墙挡住别处的方块光（自然生成要方块光 0）
        helper.setBlock(column, Blocks.END_STONE);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    for (int y = 1; y <= 4; y++) {
                        helper.setBlock(column.offset(dx, y - 1, dz), Blocks.STONE);
                    }
                }
            }
        }
        BlockPos feet = helper.absolutePos(column.above());
        final long spotSeed = seed;
        List<VoidStalkerEntity> crowd = new ArrayList<>();
        Consumer<MobSpawnEvent.FinalizeSpawn> cancel = event -> {
            if (event.getEntity() instanceof VoidStalkerEntity && event.getEntity().blockPosition().equals(feet)) {
                event.setSpawnCancelled(true);
            }
        };
        try {
            VoidStalkerSpawner.setTestOverrides(level, pos -> true, false, null);
            ECNetherEndMobs.Config.setTestStalker(stalkerSpawner(0D, 3, 4, 3));
            helper.assertTrue(VoidStalkerSpawner.trySpawnFor(player, RandomSource.create(spotSeed), false) == Outcome.OFF, "spawn_chance 0：关掉");
            ECNetherEndMobs.Config.setTestStalker(stalkerSpawner(1D, 3, 4, 0));
            helper.assertTrue(VoidStalkerSpawner.trySpawnFor(player, RandomSource.create(spotSeed), false) == Outcome.OFF, "spawn_max_nearby 0：关掉");
            ECNetherEndMobs.Config.setTestStalker(stalkerSpawner(1D, 3, 4, 3));

            VoidStalkerSpawner.setTestOverrides(level, pos -> false, false, null);
            helper.assertTrue(VoidStalkerSpawner.trySpawnFor(player, RandomSource.create(spotSeed), false) == Outcome.NOT_ELIGIBLE, "玩家在主岛：不刷");
            BlockPos playerPos = player.blockPosition();
            VoidStalkerSpawner.setTestOverrides(level, playerPos::equals, false, null);
            helper.assertTrue(VoidStalkerSpawner.trySpawnFor(player, RandomSource.create(spotSeed), false) == Outcome.NO_SPOT, "落脚处不在外岛：不刷");

            VoidStalkerSpawner.setTestOverrides(level, pos -> true, false, null);
            player.setGameMode(GameType.CREATIVE);
            helper.assertTrue(VoidStalkerSpawner.trySpawnFor(player, RandomSource.create(spotSeed), false) == Outcome.NOT_ELIGIBLE, "创造模式：不刷");
            player.setGameMode(GameType.ADVENTURE);
            VoidStalkerSpawner.setTestOverrides(level, pos -> true, false, Difficulty.PEACEFUL);
            helper.assertTrue(VoidStalkerSpawner.trySpawnFor(player, RandomSource.create(spotSeed), false) == Outcome.PEACEFUL, "和平：不刷");
            VoidStalkerSpawner.setTestOverrides(level, pos -> true, true, null);
            helper.assertTrue(VoidStalkerSpawner.trySpawnFor(player, RandomSource.create(spotSeed), false) == Outcome.MONSTER_CAP, "到了原版怪物上限：不刷");
            helper.assertTrue(VoidStalkerSpawner.capReached(70, 289) && !VoidStalkerSpawner.capReached(69, 289), "上限照原版 70 × 区块 / 289");

            VoidStalkerSpawner.setTestOverrides(level, pos -> true, false, null);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, MobSpawnEvent.FinalizeSpawn.class, cancel);
            Outcome canceled;
            try {
                canceled = VoidStalkerSpawner.trySpawnFor(player, RandomSource.create(spotSeed), false);
            } finally {
                MinecraftForge.EVENT_BUS.unregister(cancel);
            }
            helper.assertTrue(canceled == Outcome.BLOCKED && MobSpawnRules.countExact(level, ECNetherEndMobs.VOID_STALKER.get(), feet, 64) == 0,
                    "生成被取消：算没刷、世界里没有，实际 " + canceled);

            Outcome outcome;
            ECMobFramework.Config.setTestEliteChance(0D);
            try {
                outcome = VoidStalkerSpawner.trySpawnFor(player, RandomSource.create(spotSeed), false);
            } finally {
                ECMobFramework.Config.setTestEliteChance(null);
            }
            List<VoidStalkerEntity> spawned = level.getEntitiesOfClass(VoidStalkerEntity.class, new net.minecraft.world.phys.AABB(feet).inflate(1D));
            crowd.addAll(spawned);
            helper.assertTrue(outcome == Outcome.SPAWNED && spawned.size() == 1, "冒险模式、外岛、露天末地石：刷一只，实际 " + outcome + " / " + spawned.size());
            VoidStalkerEntity fresh = spawned.get(0);
            helper.assertTrue(SpawnOrigin.of(fresh) == Origin.NATURAL && SpawnOrigin.isFair(fresh), "来源是自然、公平");
            helper.assertTrue(near(fresh.getMaxHealth(), 45D) && fresh.blockPosition().equals(feet), "按配置初始化、站在末地石上");

            for (int i = 0; i < 2; i++) {
                VoidStalkerEntity extra = stalker(helper, new Vec3(1.5D + i * 8D, 2D, 9.5D));
                extra.setNoAi(true);
                level.addFreshEntity(extra);
                crowd.add(extra);
            }
            helper.assertTrue(VoidStalkerSpawner.trySpawnFor(player, RandomSource.create(spotSeed), false) == Outcome.CROWDED, "64 格内已有 3 只：不刷");
        } finally {
            VoidStalkerSpawner.setTestOverrides(null, null, null, null);
            ECNetherEndMobs.Config.setTestStalker(null);
            for (VoidStalkerEntity stalker : crowd) {
                discard(stalker);
            }
        }
        helper.succeed();
    }

    /**
     * 收魂与交易池：交易池里有一条「炼狱小鬼灵魂 ×2」（池子总数由 DemonTests 守着）；灵魂刻刀对残血的炼狱小鬼收得到它的灵魂，
     * 对残血的小鬼头目（精英）收不到、也不补那一刀；虚空潜行者不能收魂。
     */
    @GameTest(template = EMPTY)
    public static void netherImpSoulTrade(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<DemonTrades.Entry> souls = DemonTrades.entries().stream()
                .filter(e -> e.isSoul() && EssentialCraft5.id("nether_imp").equals(e.soul())).toList();
        helper.assertTrue(souls.size() == 1 && souls.get(0).min() == 2 && souls.get(0).max() == 2, "交易池里有一条炼狱小鬼灵魂 ×2，实际 " + souls.size());
        helper.assertFalse(ECNetherEndMobs.VOID_STALKER.get().is(DemonTrades.SOUL_CAPTURABLE), "虚空潜行者不能收魂");

        ServerPlayer player = survivor(helper, "imp-soul", new Vec3(1.5D, 2D, 1.5D));
        ItemStack scriber = new ItemStack(ECDemonic.SOUL_SCRIBER.get());
        NetherImpEntity imp = imp(helper, new Vec3(2.5D, 2D, 2.5D));
        imp.applyConfiguredAttributes();
        imp.setHealth(2F);
        NetherImpEntity chief = imp(helper, new Vec3(3.5D, 2D, 3.5D));
        chief.applyConfiguredAttributes();
        chief.setElite(true);
        chief.setHealth(2F);
        List<ItemEntity> drops = new ArrayList<>();
        try {
            scriber.getItem().hurtEnemy(scriber, imp, player);
            drops.addAll(level.getEntitiesOfClass(ItemEntity.class, imp.getBoundingBox().inflate(1D),
                    item -> CapturedSoulItem.matches(item.getItem(), ECNetherEndMobs.NETHER_IMP.get())));
            helper.assertTrue(imp.isDeadOrDying() && drops.size() == 1, "残血的炼狱小鬼：收到一个它的灵魂，实际 " + drops.size());
            scriber.getItem().hurtEnemy(scriber, chief, player);
            List<ItemEntity> chiefDrops = level.getEntitiesOfClass(ItemEntity.class, chief.getBoundingBox().inflate(1D),
                    item -> CapturedSoulItem.matches(item.getItem(), ECNetherEndMobs.NETHER_IMP.get()) && !drops.contains(item));
            drops.addAll(chiefDrops);
            helper.assertTrue(chief.isAlive() && near(chief.getHealth(), 2D) && chiefDrops.isEmpty(), "小鬼头目收不了魂、也不补刀");
        } finally {
            for (ItemEntity drop : drops) {
                discard(drop);
            }
        }
        helper.succeed();
    }

    /**
     * 叫来的帮手不收魂：来源是召唤的炼狱小鬼（小鬼头目叫来的）残血时收不到灵魂、也不补那一刀；自然来源的照收；
     * 风之号角召来的风法师（来源也是召唤，但不是新怪基类）照收。
     */
    @GameTest(template = EMPTY)
    public static void netherImpSummonedSoulRefused(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = survivor(helper, "imp-minion-soul", new Vec3(1.5D, 2D, 1.5D));
        ItemStack scriber = new ItemStack(ECDemonic.SOUL_SCRIBER.get());
        NetherImpEntity minion = imp(helper, new Vec3(1.5D, 2D, 3.5D));
        minion.applyConfiguredAttributes();
        SpawnOrigin.set(minion, Origin.SUMMONED);
        minion.setHealth(2F);
        NetherImpEntity wild = imp(helper, new Vec3(3.5D, 2D, 1.5D));
        wild.applyConfiguredAttributes();
        SpawnOrigin.set(wild, Origin.NATURAL);
        wild.setHealth(2F);
        WindMageEntity mage = ECWindMage.WIND_MAGE.get().create(level);
        if (mage == null) {
            throw new IllegalStateException("风法师应能创建");
        }
        moveTo(helper, mage, new Vec3(3.5D, 2D, 3.5D));
        mage.setSummonedBy(player.getUUID());
        SpawnOrigin.set(mage, Origin.SUMMONED);
        mage.setHealth(2F);
        List<ItemEntity> drops = new ArrayList<>();
        try {
            scriber.getItem().hurtEnemy(scriber, minion, player);
            List<ItemEntity> minionDrops = level.getEntitiesOfClass(ItemEntity.class, minion.getBoundingBox().inflate(1D),
                    item -> item.getItem().is(ECDemonic.CAPTURED_SOUL.get()));
            drops.addAll(minionDrops);
            helper.assertTrue(minion.isAlive() && near(minion.getHealth(), 2D) && minionDrops.isEmpty(), "叫来的小鬼收不了魂、也不补刀");

            scriber.getItem().hurtEnemy(scriber, wild, player);
            List<ItemEntity> wildDrops = level.getEntitiesOfClass(ItemEntity.class, wild.getBoundingBox().inflate(1D),
                    item -> CapturedSoulItem.matches(item.getItem(), ECNetherEndMobs.NETHER_IMP.get()));
            drops.addAll(wildDrops);
            helper.assertTrue(wild.isDeadOrDying() && wildDrops.size() == 1, "自然来源的小鬼照收，实际 " + wildDrops.size());

            scriber.getItem().hurtEnemy(scriber, mage, player);
            List<ItemEntity> mageDrops = level.getEntitiesOfClass(ItemEntity.class, mage.getBoundingBox().inflate(1D),
                    item -> CapturedSoulItem.matches(item.getItem(), ECWindMage.WIND_MAGE.get()));
            drops.addAll(mageDrops);
            helper.assertTrue(mage.isDeadOrDying() && mageDrops.size() == 1, "风之号角召来的风法师照收，实际 " + mageDrops.size());
        } finally {
            for (ItemEntity drop : drops) {
                discard(drop);
            }
        }
        helper.succeed();
    }
}
