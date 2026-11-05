package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IMRUHandlerItem;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.entity.mob.MobDamageShare;
import com.flameforge.essentialcraft5.entity.mob.hoanna.AbyssalEelEntity;
import com.flameforge.essentialcraft5.entity.mob.hoanna.ChaosAberrantEntity;
import com.flameforge.essentialcraft5.entity.mob.hoanna.ChaosAberrantEntity.ChargeState;
import com.flameforge.essentialcraft5.entity.mob.hoanna.ChaosLarvaEntity;
import com.flameforge.essentialcraft5.entity.mob.hoanna.EmberSpiritBoltEntity;
import com.flameforge.essentialcraft5.entity.mob.hoanna.EmberSpiritEntity;
import com.flameforge.essentialcraft5.entity.mob.hoanna.EmberSpiritHit;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvents;
import com.flameforge.essentialcraft5.item.ember.EmberKind;
import com.flameforge.essentialcraft5.loot.KillFatigueCondition;
import com.flameforge.essentialcraft5.player.MobKillLedger;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesB;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesB.Config.AberrantValues;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesB.Config.EelValues;
import com.flameforge.essentialcraft5.registry.ECItems;
import com.flameforge.essentialcraft5.registry.ECMobDrops;
import com.flameforge.essentialcraft5.registry.ECMobFramework;
import com.flameforge.essentialcraft5.registry.ECTags;
import com.flameforge.essentialcraft5.util.SpawnOrigin;
import com.flameforge.essentialcraft5.util.SpawnOrigin.Origin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 第十三批 13d-N2「混沌畸变体、畸变幼体、余烬灵、渊鳗」的 GameTest（batch13.md §5.2；怪物稿 §2.4.3、§2.4.4、§2.4.6）：
 * <ul>
 *   <li>混沌畸变体：冲撞先蓄力（路线定死、原地不动、不伤人）再冲；伤害 = 固定 + 目标最大生命比例、无视护甲；撞墙眩晕、眩晕时挨打 ×1.5；
 *       死时裂成两只幼体（来源「召唤」、不存盘、不给经验、什么都不掉），刷怪笼 / 精英不裂；地震里出生的狂暴、读档两遍修饰符不叠；
 *       畸变母巢打起来产幼体、最多 3 只；</li>
 *   <li>余烬灵：八种余烬各自的命中效果（含魔力抽 MRU、神圣驱散、混沌抽别的七种）；余烬弹带种类、打中按种类生效；余烬存档读回；
 *       精英第二种和第一种不同；日阵里露天的白天能刷、平常白天不刷；</li>
 *   <li>渊鳗：水深、高度两道生成门槛，放置方式在水里、类别是怪物；下拽脚下到底不拉、目标往上挣就松口；群首挨第一下叫两条帮手、只叫一次；</li>
 *   <li>掉落：不公平来源（刷怪笼）三种都不掉模组物品；公平击杀按余烬种类掉；余烬组击杀递减生效。</li>
 * </ul>
 * 改配置一律用 {@code ECHoannaNativesB.Config.setTestAberrant / setTestEel}，海纳尔天灾在 {@link #inHoanna} 里开、同一段同步代码里停；
 * 全局的日内时间同样只在同一段同步代码里拨、马上放回。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class HoannaNativesBTests {

    private static final String EMPTY = "empty";
    private static final String LARGE = "empty_large";
    private static final AberrantValues ABERRANT = ECHoannaNativesB.Config.ABERRANT_DEFAULTS;
    private static final EelValues EEL = ECHoannaNativesB.Config.EEL_DEFAULTS;
    /** 掉落统计时这几组不递减 */
    private static final String[] LOOT_GROUPS = {"ember", "hoanna_common", "elite"};
    private static final long SEED_STEP = 0x9E3779B97F4A7C15L;

    private HoannaNativesBTests() {
    }

    // ================================================================== 工具

    private static ChaosAberrantEntity aberrant(GameTestHelper helper, Vec3 relative) {
        ChaosAberrantEntity mob = ECHoannaNativesB.CHAOS_ABERRANT.get().create(helper.getLevel());
        if (mob == null) {
            throw new IllegalStateException("混沌畸变体应能创建");
        }
        moveTo(helper, mob, relative);
        mob.applyConfiguredAttributes();
        return mob;
    }

    private static EmberSpiritEntity ember(GameTestHelper helper, Vec3 relative, EmberKind kind) {
        EmberSpiritEntity mob = ECHoannaNativesB.EMBER_SPIRIT.get().create(helper.getLevel());
        if (mob == null) {
            throw new IllegalStateException("余烬灵应能创建");
        }
        moveTo(helper, mob, relative);
        mob.applyConfiguredAttributes();
        mob.setEmber(kind);
        return mob;
    }

    private static AbyssalEelEntity eel(GameTestHelper helper, Vec3 relative) {
        AbyssalEelEntity mob = ECHoannaNativesB.ABYSSAL_EEL.get().create(helper.getLevel());
        if (mob == null) {
            throw new IllegalStateException("渊鳗应能创建");
        }
        moveTo(helper, mob, relative);
        mob.applyConfiguredAttributes();
        return mob;
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

    /** 真玩家：过了出生无敌、生存模式、满血、在 relativeFeet（不在世界的实体列表里） */
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

    /** 放进世界、不动的尸壳 */
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

    private static Pig pig(GameTestHelper helper, Vec3 relative) {
        Pig pig = EntityType.PIG.create(helper.getLevel());
        if (pig == null) {
            throw new IllegalStateException("猪应能创建");
        }
        moveTo(helper, pig, relative);
        return pig;
    }

    /**
     * 在 (x, z) 这一格立一根水柱：从 floorY + 1 到 floorY + depth，四周一圈玻璃（水不往外流）；floorY 这一格放石头。
     */
    private static void waterColumn(GameTestHelper helper, int x, int z, int floorY, int depth) {
        helper.setBlock(new BlockPos(x, floorY, z), Blocks.STONE);
        for (int y = floorY + 1; y <= floorY + depth; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    helper.setBlock(new BlockPos(x + dx, y, z + dz), dx == 0 && dz == 0 ? Blocks.WATER : Blocks.GLASS);
                }
            }
        }
    }

    private static void discard(Entity... entities) {
        for (Entity entity : entities) {
            if (entity != null && !entity.isRemoved()) {
                entity.discard();
            }
        }
    }

    private static void discardAll(List<? extends Entity> entities) {
        for (Entity entity : entities) {
            discard(entity);
        }
    }

    private static boolean near(double actual, double expected) {
        return Math.abs(actual - expected) < 1.0E-3D;
    }

    /** 把测试所在的世界当海纳尔跑 body（同一段同步代码里设、测、停事件、清覆盖） */
    private static void inHoanna(GameTestHelper helper, Runnable body) {
        ServerLevel level = helper.getLevel();
        ECHoanna.setTestHoannaOverride(level);
        try {
            body.run();
        } finally {
            HoannaEvents.stop(level);
            ECHoanna.clearTestOverride();
        }
    }

    /** 把（全局的）日内时间临时拨到当天的第 timeOfDay 刻跑 body，同时刷新天空亮度；finally 里放回 */
    private static void atTimeOfDay(GameTestHelper helper, long timeOfDay, Runnable body) {
        ServerLevel level = helper.getLevel();
        long saved = level.getDayTime();
        level.setDayTime(Math.floorDiv(saved, HoannaEvents.DAY_TICKS) * HoannaEvents.DAY_TICKS + timeOfDay);
        level.updateSkyBrightness();
        try {
            body.run();
        } finally {
            level.setDayTime(saved);
            level.updateSkyBrightness();
        }
    }

    private static AberrantValues aberrantBrood(int broodInterval, int broodMax) {
        AberrantValues d = ABERRANT;
        return new AberrantValues(d.maxHealth(), d.armor(), d.attackDamage(), d.chargeCooldown(), d.chargeWindup(), d.chargeDistance(), d.chargeDamage(),
                d.chargeHealthPercent(), d.stunTicks(), d.stunDamageTaken(), d.frenzyHealth(), d.frenzySpeed(), d.quakeAttack(), d.splitCount(),
                d.larvaLifetime(), broodInterval, broodMax, d.residueTicks(), d.nearbyCap());
    }

    private static EelValues eelHeights(int minY, int maxY) {
        EelValues d = EEL;
        return new EelValues(d.maxHealth(), d.armor(), d.biteDamage(), d.dragTicks(), d.dragSpeed(), d.minDepth(), minY, maxY, d.nearbyCap(),
                d.eliteSummonCount(), d.eliteSlowTicks(), d.dryDamage());
    }

    private static long modifierCount(LivingEntity entity, net.minecraft.world.entity.ai.attributes.Attribute attribute, UUID id) {
        AttributeInstance instance = entity.getAttribute(attribute);
        return instance == null ? 0 : instance.getModifiers().stream().filter(m -> m.getId().equals(id)).count();
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

    private static int count(List<ItemStack> drops, Item item) {
        return drops.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    /** 掉落里有没有模组自己的物品 */
    private static boolean anyModItem(List<ItemStack> drops) {
        return drops.stream().anyMatch(stack -> EssentialCraft5.MODID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace()));
    }

    private static void noFatigue(boolean on) {
        for (String group : LOOT_GROUPS) {
            ECMobFramework.Config.setTestFreeKills(group, on ? 0 : null);
        }
    }

    private static IPlayerData data(ServerPlayer player) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            throw new IllegalStateException("测试玩家应挂着玩家数据");
        }
        return data;
    }

    private static IMRUHandlerItem mru(ItemStack stack) {
        return stack.getCapability(ECCapabilities.MRU_ITEM).orElseThrow(IllegalStateException::new);
    }

    // ================================================================== 混沌畸变体：冲撞

    /**
     * 冲撞先预警：蓄力那 20 刻原地不动、方向定死、谁都没挨打；蓄完才冲，路上的目标挨一下（只一下）、被撞飞；冲完回到空闲，冷却从起冲时算。
     */
    @GameTest(template = LARGE)
    public static void aberrantChargeWarnsBeforeHit(GameTestHelper helper) {
        stoneFloor(helper, 11);
        ChaosAberrantEntity mob = aberrant(helper, new Vec3(1.5D, 2D, 5.5D));
        Husk target = husk(helper, new Vec3(6.5D, 2D, 5.5D));
        try {
            mob.setTarget(target);
            float before = target.getHealth();
            Vec3 start = mob.position();
            helper.assertTrue(mob.startCharge(target), "空闲时起得来冲撞");
            helper.assertFalse(mob.startCharge(target), "已经在冲撞里不能再起一次");
            helper.assertTrue(mob.getChargeState() == ChargeState.WINDUP && near(mob.chargeDirection().x, 1D), "进入蓄力、方向朝目标（+x）定死");
            for (int i = 0; i < ABERRANT.chargeWindup() - 1; i++) {
                mob.tickCharge();
            }
            helper.assertTrue(mob.getChargeState() == ChargeState.WINDUP, "蓄力 " + ABERRANT.chargeWindup() + " 刻没到还在蓄力");
            helper.assertTrue(mob.position().distanceTo(start) < 1.0E-3D, "蓄力时原地不动");
            helper.assertTrue(target.getHealth() == before, "蓄力时谁都没挨打");
            mob.tickCharge();
            helper.assertTrue(mob.getChargeState() == ChargeState.DASH, "蓄满进入冲刺");
            int ticks = 0;
            while (mob.getChargeState() == ChargeState.DASH && ticks++ < 40) {
                mob.tickCharge();
            }
            helper.assertTrue(mob.isChargeIdle(), "冲完回到空闲");
            float expected = ChaosAberrantEntity.chargeDamage(ABERRANT, target);
            helper.assertTrue(Math.abs(before - target.getHealth() - expected) < 1.0E-3F,
                    "路上的目标挨一下 " + expected + "（只一下），实际 " + (before - target.getHealth()));
            helper.assertTrue(lastHitIs(target, ECDamageTypes.ABERRANT_STRIKE), "伤害类型 aberrant_strike");
            helper.assertTrue(target.getDeltaMovement().x > 0.1D, "沿冲撞方向被撞飞，实际 " + target.getDeltaMovement());
            helper.assertTrue(mob.getX() - start.x > ABERRANT.chargeDistance() - 1D, "冲了约 " + ABERRANT.chargeDistance() + " 格，实际 " + (mob.getX() - start.x));
            helper.assertTrue(mob.chargeCooldown() > 0 && mob.chargeCooldown() < ABERRANT.chargeCooldown(), "冷却从起冲时算，还剩 " + mob.chargeCooldown());
        } finally {
            discard(mob, target);
        }
        helper.succeed();
    }

    /** 冲撞伤害 = charge_damage + 目标最大生命 × charge_health_percent，护甲 20 照样全吃（无视护甲） */
    @GameTest(template = EMPTY)
    public static void aberrantChargeDamageIgnoresArmor(GameTestHelper helper) {
        ChaosAberrantEntity mob = aberrant(helper, new Vec3(1.5D, 2D, 1.5D));
        Husk tank = husk(helper, new Vec3(3.5D, 2D, 1.5D));
        try {
            AttributeInstance health = tank.getAttribute(Attributes.MAX_HEALTH);
            if (health == null) {
                throw new IllegalStateException("尸壳应有最大生命属性");
            }
            health.setBaseValue(100D);
            tank.setHealth(100F);
            AttributeInstance armor = tank.getAttribute(Attributes.ARMOR);
            if (armor == null) {
                throw new IllegalStateException("尸壳应有护甲属性");
            }
            armor.setBaseValue(20D);
            helper.assertTrue(tank.getArmorValue() >= 20, "测试前提：尸壳护甲 20");
            float expected = (float) (ABERRANT.chargeDamage() + 100D * ABERRANT.chargeHealthPercent());
            helper.assertTrue(near(ChaosAberrantEntity.chargeDamage(ABERRANT, tank), expected), "公式：12 + 100 × 6% = " + expected);
            helper.assertTrue(mob.strike(tank), "打实了");
            helper.assertTrue(near(100F - tank.getHealth(), expected), "无视护甲全吃 " + expected + "，实际 " + (100F - tank.getHealth()));
        } finally {
            discard(mob, tank);
        }
        helper.succeed();
    }

    /** 撞墙：停下来眩晕 stun_ticks 刻，眩晕时挨打 ×1.5，晕完回到空闲；普通个体撞墙不留残液、母巢留一滩 */
    @GameTest(template = LARGE)
    public static void aberrantWallStunTakesMoreDamage(GameTestHelper helper) {
        stoneFloor(helper, 11);
        for (int z = 0; z < 11; z++) {
            for (int y = 2; y <= 4; y++) {
                helper.setBlock(new BlockPos(5, y, z), Blocks.STONE);
            }
        }
        ChaosAberrantEntity mob = aberrant(helper, new Vec3(2.5D, 2D, 3.5D));
        ChaosAberrantEntity mother = aberrant(helper, new Vec3(2.5D, 2D, 7.5D));
        mother.setElite(true);
        try {
            mob.startCharge(new Vec3(1D, 0D, 0D));
            mother.startCharge(new Vec3(1D, 0D, 0D));
            int ticks = 0;
            while (mob.getChargeState() != ChargeState.STUNNED && ticks++ < ABERRANT.chargeWindup() + 20) {
                mob.tickCharge();
                mother.tickCharge();
            }
            helper.assertTrue(mob.getChargeState() == ChargeState.STUNNED && mother.getChargeState() == ChargeState.STUNNED, "撞上石墙进入眩晕");
            helper.assertTrue(mob.getX() < helper.absoluteVec(new Vec3(5D, 0D, 0D)).x, "停在墙前");
            helper.assertTrue(mob.puddleCount() == 0 && mother.puddleCount() == 1, "普通个体不留残液，母巢撞墙留一滩");

            float hp = mob.getHealth();
            mob.hurt(mob.damageSources().magic(), 4F);
            helper.assertTrue(near(hp - mob.getHealth(), 4F * ABERRANT.stunDamageTaken()), "眩晕时挨 4 点魔法伤害掉 6，实际 " + (hp - mob.getHealth()));
            for (int i = 0; i < ABERRANT.stunTicks(); i++) {
                mob.tickCharge();
            }
            helper.assertTrue(mob.isChargeIdle(), "晕 " + ABERRANT.stunTicks() + " 刻后回到空闲");
            mob.invulnerableTime = 0;
            hp = mob.getHealth();
            mob.hurt(mob.damageSources().magic(), 4F);
            helper.assertTrue(near(hp - mob.getHealth(), 4F), "不眩晕时照常掉 4，实际 " + (hp - mob.getHealth()));
        } finally {
            discard(mob, mother);
        }
        helper.succeed();
    }

    // ================================================================== 混沌畸变体：分裂、狂暴、母巢

    /**
     * 野外的普通个体死时裂成 split_count 只幼体：来源「召唤」（不公平）、记着母体、不存盘、不给经验、掉落表什么都不掉、身上没有精英；
     * 幼体到寿命就散掉。刷怪笼来的、精英死了都不裂。
     */
    @GameTest(template = LARGE)
    public static void aberrantSplitsIntoLarvae(GameTestHelper helper) {
        stoneFloor(helper, 11);
        ServerLevel level = helper.getLevel();
        ChaosAberrantEntity wild = aberrant(helper, new Vec3(2.5D, 2D, 2.5D));
        SpawnOrigin.set(wild, Origin.NATURAL);
        wild.setNoAi(true);
        level.addFreshEntity(wild);
        ChaosAberrantEntity caged = aberrant(helper, new Vec3(8.5D, 2D, 2.5D));
        SpawnOrigin.set(caged, Origin.SPAWNER);
        caged.setNoAi(true);
        level.addFreshEntity(caged);
        ChaosAberrantEntity mother = aberrant(helper, new Vec3(5.5D, 2D, 8.5D));
        SpawnOrigin.set(mother, Origin.NATURAL);
        mother.setElite(true);
        mother.setNoAi(true);
        level.addFreshEntity(mother);
        AABB area = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(16D);
        List<ChaosLarvaEntity> larvae = List.of();
        try {
            helper.assertTrue(wild.splitsOnDeath() && !caged.splitsOnDeath() && !mother.splitsOnDeath(), "只有野外的普通个体会裂");
            wild.hurt(level.damageSources().magic(), 1000F);
            caged.hurt(level.damageSources().magic(), 1000F);
            mother.hurt(level.damageSources().magic(), 1000F);
            helper.assertTrue(wild.isDeadOrDying() && caged.isDeadOrDying() && mother.isDeadOrDying(), "三只都死了");
            Set<UUID> parents = Set.of(wild.getUUID(), caged.getUUID(), mother.getUUID());
            larvae = level.getEntitiesOfClass(ChaosLarvaEntity.class, area, l -> l.mother() != null && parents.contains(l.mother()));
            UUID wildId = wild.getUUID();
            long fromWild = larvae.stream().filter(l -> wildId.equals(l.mother())).count();
            helper.assertTrue(fromWild == ABERRANT.splitCount() && larvae.size() == ABERRANT.splitCount(),
                    "野外那只裂出 " + ABERRANT.splitCount() + " 只、刷怪笼的和母巢不裂，实际 " + fromWild + " / " + larvae.size());
            ServerPlayer killer = survivor(helper, "n2-larva", new Vec3(1.5D, 2D, 1.5D));
            noFatigue(true);
            try {
                long seed = 1L;
                for (ChaosLarvaEntity larva : larvae) {
                    helper.assertTrue(SpawnOrigin.of(larva) == Origin.SUMMONED && !SpawnOrigin.isFair(larva), "幼体来源是召唤、不公平");
                    helper.assertTrue(!larva.shouldBeSaved() && !larva.isElite() && !larva.hasEliteVariant(), "幼体不存盘、没有精英");
                    helper.assertTrue(larva.getExperienceReward() == 0 && !larva.shouldDropExperience(), "幼体不给经验");
                    helper.assertTrue(larva.getType().is(ECTags.GROWTH_EXCLUDED), "幼体进 growth_excluded");
                    MobDamageShare.record(larva, killer.getUUID(), 6F);
                    for (int i = 0; i < 8; i++) {
                        helper.assertTrue(lootFor(level, larva, killer, "entities/chaos_larva", seed++ * SEED_STEP).isEmpty(), "幼体什么都不掉");
                    }
                }
            } finally {
                noFatigue(false);
                KillFatigueCondition.forgetCounted();
            }
            ChaosLarvaEntity first = larvae.get(0);
            first.setLifetime(3);
            for (int i = 0; i < 3; i++) {
                first.tickLifetime();
            }
            helper.assertTrue(first.isRemoved(), "幼体到寿命散掉");
        } finally {
            discardAll(larvae);
            discard(wild, caged, mother);
        }
        helper.succeed();
    }

    /**
     * 地震里生成的一出来就狂暴（移速 ×1.3、近战 ×1.25），读档两遍修饰符各只有一个；平常生成的不狂暴，残血 30% 以下才加移速、不加近战。
     */
    @GameTest(template = EMPTY)
    public static void aberrantQuakeFrenzyReloads(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChaosAberrantEntity[] born = new ChaosAberrantEntity[1];
        inHoanna(helper, () -> {
            helper.assertTrue(HoannaEvents.start(level, HoannaEvent.EARTHQUAKE, 200), "地震开得起来");
            born[0] = aberrant(helper, new Vec3(2.5D, 2D, 2.5D));
            finalizeSpawn(born[0], level, MobSpawnType.NATURAL, 0D);
        });
        ChaosAberrantEntity quake = born[0];
        helper.assertTrue(quake.isQuakeFrenzied() && quake.isFrenzied(), "地震里出生的狂暴");
        double attack = ABERRANT.attackDamage() * (1D + ABERRANT.quakeAttack());
        helper.assertTrue(near(quake.getAttributeValue(Attributes.ATTACK_DAMAGE), attack), "近战 ×1.25 = " + attack + "，实际 " + quake.getAttributeValue(Attributes.ATTACK_DAMAGE));
        helper.assertTrue(modifierCount(quake, Attributes.MOVEMENT_SPEED, ChaosAberrantEntity.FRENZY_SPEED_ID) == 1, "挂着狂暴移速");

        ChaosAberrantEntity once = ECHoannaNativesB.CHAOS_ABERRANT.get().create(level);
        once.load(quake.saveWithoutId(new CompoundTag()));
        ChaosAberrantEntity twice = ECHoannaNativesB.CHAOS_ABERRANT.get().create(level);
        twice.load(once.saveWithoutId(new CompoundTag()));
        helper.assertTrue(twice.isQuakeFrenzied() && near(twice.getAttributeValue(Attributes.ATTACK_DAMAGE), attack)
                        && modifierCount(twice, Attributes.ATTACK_DAMAGE, ChaosAberrantEntity.QUAKE_ATTACK_ID) == 1
                        && modifierCount(twice, Attributes.MOVEMENT_SPEED, ChaosAberrantEntity.FRENZY_SPEED_ID) == 1,
                "读档两遍还狂暴、两个修饰符各一个，实际近战 " + twice.getAttributeValue(Attributes.ATTACK_DAMAGE));

        ChaosAberrantEntity calm = aberrant(helper, new Vec3(1.5D, 2D, 1.5D));
        finalizeSpawn(calm, level, MobSpawnType.NATURAL, 0D);
        helper.assertTrue(!calm.isFrenzied() && modifierCount(calm, Attributes.MOVEMENT_SPEED, ChaosAberrantEntity.FRENZY_SPEED_ID) == 0
                && near(calm.getAttributeValue(Attributes.ATTACK_DAMAGE), ABERRANT.attackDamage()), "平常生成的不狂暴");
        calm.setHealth((float) (calm.getMaxHealth() * ABERRANT.frenzyHealth()) - 1F);
        calm.refreshFrenzy();
        helper.assertTrue(calm.isFrenzied() && modifierCount(calm, Attributes.MOVEMENT_SPEED, ChaosAberrantEntity.FRENZY_SPEED_ID) == 1
                && modifierCount(calm, Attributes.ATTACK_DAMAGE, ChaosAberrantEntity.QUAKE_ATTACK_ID) == 0, "残血狂暴只加移速");
        calm.setHealth(calm.getMaxHealth());
        calm.refreshFrenzy();
        helper.assertTrue(modifierCount(calm, Attributes.MOVEMENT_SPEED, ChaosAberrantEntity.FRENZY_SPEED_ID) == 0, "回满血狂暴摘掉");
        discard(quake, once, twice, calm);
        helper.succeed();
    }

    /** 畸变母巢：有目标时按冷却产幼体，自己产的活着的最多 brood_max 只；幼体不公平；普通个体、没目标的母巢都不产 */
    @GameTest(template = LARGE)
    public static void aberrantBroodIsCapped(GameTestHelper helper) {
        stoneFloor(helper, 11);
        ChaosAberrantEntity mother = aberrant(helper, new Vec3(5.5D, 2D, 5.5D));
        mother.setElite(true);
        ChaosAberrantEntity plain = aberrant(helper, new Vec3(2.5D, 2D, 2.5D));
        Husk target = husk(helper, new Vec3(8.5D, 2D, 8.5D));
        List<ChaosLarvaEntity> brood = List.of();
        ECHoannaNativesB.Config.setTestAberrant(aberrantBrood(1, 3));
        try {
            helper.assertFalse(mother.tickBrood(), "没目标不产");
            mother.setTarget(target);
            plain.setTarget(target);
            int made = 0;
            for (int i = 0; i < 20; i++) {
                if (mother.tickBrood()) {
                    made++;
                }
                helper.assertFalse(plain.tickBrood(), "普通个体不产");
            }
            helper.assertTrue(made == 3 && mother.broodCount() == 3, "最多 3 只，实际产了 " + made + "、数到 " + mother.broodCount());
            brood = helper.getLevel().getEntitiesOfClass(ChaosLarvaEntity.class, mother.getBoundingBox().inflate(ChaosAberrantEntity.BROOD_RADIUS),
                    l -> mother.getUUID().equals(l.mother()));
            helper.assertTrue(brood.stream().allMatch(l -> SpawnOrigin.of(l) == Origin.SUMMONED && !SpawnOrigin.isFair(l) && l.getTarget() == target),
                    "幼体来源是召唤、盯着母巢的目标");
            brood.get(0).discard();
            boolean refilled = false;
            for (int i = 0; i < 4 && !refilled; i++) {
                refilled = mother.tickBrood();
            }
            helper.assertTrue(refilled && mother.broodCount() == 3, "死了一只再补一只");
        } finally {
            ECHoannaNativesB.Config.setTestAberrant(null);
            discardAll(helper.getLevel().getEntitiesOfClass(ChaosLarvaEntity.class, mother.getBoundingBox().inflate(ChaosAberrantEntity.BROOD_RADIUS),
                    l -> mother.getUUID().equals(l.mother())));
            discardAll(brood);
            discard(mother, plain, target);
        }
        helper.succeed();
    }

    // ================================================================== 余烬灵

    /** 八种余烬各自的命中效果：阴燃点火 3 秒、燃烧点火 6 秒再 2 点火伤、酸蚀中毒、腐化虚弱、水晶缓慢、神圣驱散一个有益效果、混沌抽别的七种 */
    @GameTest(template = EMPTY)
    public static void emberKindsApplyTheirEffects(GameTestHelper helper) {
        RandomSource random = RandomSource.create(7L * SEED_STEP);
        Pig common = pig(helper, new Vec3(1.5D, 2D, 1.5D));
        helper.assertTrue(EmberSpiritHit.apply(EmberKind.COMMON, common, null, null, random) == EmberKind.COMMON
                && common.getRemainingFireTicks() > 0 && common.getRemainingFireTicks() <= EmberSpiritHit.COMMON_FIRE_SECONDS * 20, "阴燃：点火 3 秒");

        Pig flame = pig(helper, new Vec3(1.5D, 2D, 1.5D));
        float hp = flame.getHealth();
        EmberSpiritHit.apply(EmberKind.FLAME, flame, null, null, random);
        helper.assertTrue(flame.getRemainingFireTicks() > EmberSpiritHit.COMMON_FIRE_SECONDS * 20, "燃烧：点火 6 秒，实际 " + flame.getRemainingFireTicks());
        helper.assertTrue(flame.getHealth() < hp && lastHitIs(flame, net.minecraft.world.damagesource.DamageTypes.FIREBALL), "燃烧：再挨一下火球伤害");

        Pig acidic = pig(helper, new Vec3(1.5D, 2D, 1.5D));
        EmberSpiritHit.apply(EmberKind.ACIDIC, acidic, null, null, random);
        Pig corrupted = pig(helper, new Vec3(1.5D, 2D, 1.5D));
        EmberSpiritHit.apply(EmberKind.CORRUPTED, corrupted, null, null, random);
        Pig crystal = pig(helper, new Vec3(1.5D, 2D, 1.5D));
        EmberSpiritHit.apply(EmberKind.CRYSTAL, crystal, null, null, random);
        helper.assertTrue(acidic.hasEffect(MobEffects.POISON) && corrupted.hasEffect(MobEffects.WEAKNESS) && crystal.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                "酸蚀中毒、腐化虚弱、水晶缓慢");
        helper.assertTrue(!acidic.hasEffect(MobEffects.WEAKNESS) && !crystal.hasEffect(MobEffects.POISON), "各管各的，不串");

        Pig divine = pig(helper, new Vec3(1.5D, 2D, 1.5D));
        divine.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400));
        divine.addEffect(new MobEffectInstance(MobEffects.POISON, 400));
        EmberSpiritHit.apply(EmberKind.DIVINE, divine, null, null, random);
        helper.assertTrue(!divine.hasEffect(MobEffects.MOVEMENT_SPEED) && divine.hasEffect(MobEffects.POISON), "神圣：驱散有益的速度、不碰中毒");

        Pig magical = pig(helper, new Vec3(1.5D, 2D, 1.5D));
        helper.assertTrue(EmberSpiritHit.apply(EmberKind.MAGICAL, magical, null, null, random) == EmberKind.MAGICAL
                && EmberSpiritHit.drain(magical, 200) == 0, "魔力：不是玩家不抽");

        Set<EmberKind> seen = EnumSet.noneOf(EmberKind.class);
        for (int i = 0; i < 200; i++) {
            seen.add(EmberSpiritHit.chaosPick(RandomSource.create(i * SEED_STEP)));
        }
        helper.assertTrue(!seen.contains(EmberKind.CHAOS) && seen.size() == EmberKind.values().length - 1, "混沌抽得到别的七种、抽不到自己：" + seen);
        Pig chaos = pig(helper, new Vec3(1.5D, 2D, 1.5D));
        EmberKind picked = EmberSpiritHit.apply(EmberKind.CHAOS, chaos, null, null, RandomSource.create(3L * SEED_STEP));
        helper.assertTrue(picked != EmberKind.CHAOS, "混沌按抽中的那种生效：" + picked);
        discard(common, flame, acidic, corrupted, crystal, divine, magical, chaos);
        helper.succeed();
    }

    /** 魔力余烬从玩家身上的储存物品抽 magical_drain（200）MRU，不够就有多少抽多少；创造模式不抽 */
    @GameTest(template = EMPTY)
    public static void emberMagicalDrainsStorage(GameTestHelper helper) {
        ServerPlayer player = survivor(helper, "n2-drain", new Vec3(1.5D, 2D, 1.5D));
        ItemStack shard = new ItemStack(ECItems.SOUL_SHARD.get());
        mru(shard).setMRU(300);
        player.getInventory().setItem(0, shard);
        int drained = EmberSpiritHit.drain(player, ECHoannaNativesB.Config.EMBER_DEFAULTS.magicalDrain());
        helper.assertTrue(drained == 200 && mru(player.getInventory().getItem(0)).getMRU() == 100, "抽走 200，剩 100，实际抽 " + drained);
        helper.assertTrue(EmberSpiritHit.apply(EmberKind.MAGICAL, player, null, null, RandomSource.create(SEED_STEP)) == EmberKind.MAGICAL
                && mru(player.getInventory().getItem(0)).getMRU() == 0, "不够 200 就有多少抽多少");
        mru(player.getInventory().getItem(0)).setMRU(500);
        player.setGameMode(GameType.CREATIVE);
        helper.assertTrue(EmberSpiritHit.drain(player, 200) == 0 && mru(player.getInventory().getItem(0)).getMRU() == 500, "创造模式不抽");
        helper.succeed();
    }

    /** 余烬弹：带着射手的余烬种类、伤害等于射手的攻击属性，打中射手的目标按 mob_bolt 掉血、再按种类生效（水晶 → 缓慢） */
    @GameTest(template = LARGE)
    public static void emberBoltCarriesKind(GameTestHelper helper) {
        EmberSpiritEntity spirit = ember(helper, new Vec3(1.5D, 3D, 5.5D), EmberKind.CRYSTAL);
        spirit.setNoAi(true);
        helper.getLevel().addFreshEntity(spirit);
        Husk target = husk(helper, new Vec3(6.5D, 2D, 5.5D));
        EmberSpiritBoltEntity bolt = null;
        try {
            spirit.setTarget(target);
            float before = target.getHealth();
            bolt = spirit.shootAt(target, spirit.getEmber());
            helper.assertTrue(bolt != null && bolt.kind() == EmberKind.CRYSTAL && near(bolt.damage(), ECHoannaNativesB.Config.EMBER_DEFAULTS.boltDamage()),
                    "余烬弹带水晶、伤害 6");
            for (int i = 0; i < EmberSpiritBoltEntity.LIFETIME && !bolt.isRemoved(); i++) {
                bolt.tick();
            }
            helper.assertTrue(bolt.isRemoved() && target.getHealth() < before, "打中目标、弹没了");
            helper.assertTrue(lastHitIs(target, ECDamageTypes.MOB_BOLT) && target.getLastDamageSource().getEntity() == spirit, "mob_bolt、攻击者是余烬灵");
            helper.assertTrue(target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "水晶余烬带缓慢");
        } finally {
            discard(spirit, target, bolt);
        }
        helper.succeed();
    }

    /** 余烬存档读回（读两遍不变）；精英「余烬双生」的第二种和第一种不同、也读得回；第一种改成和第二种一样时第二种换掉 */
    @GameTest(template = EMPTY)
    public static void emberSpiritSavesEmber(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        EmberSpiritEntity spirit = ember(helper, new Vec3(2.5D, 2D, 2.5D), EmberKind.CORRUPTED);
        helper.assertTrue(spirit.getSecondEmber() == null, "普通的只有一种");
        EmberSpiritEntity once = ECHoannaNativesB.EMBER_SPIRIT.get().create(level);
        once.load(spirit.saveWithoutId(new CompoundTag()));
        EmberSpiritEntity twice = ECHoannaNativesB.EMBER_SPIRIT.get().create(level);
        twice.load(once.saveWithoutId(new CompoundTag()));
        helper.assertTrue(twice.getEmber() == EmberKind.CORRUPTED && twice.getSecondEmber() == null, "读两遍还是腐化，实际 " + twice.getEmber());

        for (int i = 0; i < 16; i++) {
            EmberSpiritEntity twin = ECHoannaNativesB.EMBER_SPIRIT.get().create(level);
            moveTo(helper, twin, new Vec3(2.5D, 2D, 2.5D));
            finalizeSpawn(twin, level, MobSpawnType.NATURAL, 1D);
            helper.assertTrue(twin.isElite() && twin.getSecondEmber() != null && twin.getSecondEmber() != twin.getEmber(), "余烬双生带两种不同的余烬");
            EmberSpiritEntity back = ECHoannaNativesB.EMBER_SPIRIT.get().create(level);
            back.load(twin.saveWithoutId(new CompoundTag()));
            helper.assertTrue(back.getEmber() == twin.getEmber() && back.getSecondEmber() == twin.getSecondEmber(), "两种都读得回");
            EmberKind second = twin.getSecondEmber();
            twin.setEmber(second);
            helper.assertTrue(twin.getSecondEmber() != null && twin.getSecondEmber() != twin.getEmber(), "第一种改成第二种时第二种换掉");
            discard(twin, back);
        }
        discard(spirit, once, twice);
        helper.succeed();
    }

    /** 日阵进行中、露天的白天能刷（亮度放开）；平常的白天不刷；和平难度不刷 */
    @GameTest(template = EMPTY)
    public static void emberSpawnsUnderSunArray(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        EntityType<EmberSpiritEntity> type = ECHoannaNativesB.EMBER_SPIRIT.get();
        helper.assertTrue(level.canSeeSky(pos), "测试前提：头顶露天");
        atTimeOfDay(helper, 6_000L, () -> inHoanna(helper, () -> {
            for (int i = 0; i < 16; i++) {
                helper.assertFalse(EmberSpiritEntity.checkEmberSpawnRules(type, level, MobSpawnType.NATURAL, pos, RandomSource.create(i * SEED_STEP)),
                        "平常的正午不刷");
            }
            helper.assertTrue(HoannaEvents.start(level, HoannaEvent.SUN_ARRAY, 200), "正午开得起日阵");
            helper.assertTrue(EmberSpiritEntity.sunArrayOpenSky(level, pos), "日阵里的露天");
            for (int i = 0; i < 16; i++) {
                helper.assertTrue(EmberSpiritEntity.checkEmberSpawnRules(type, level, MobSpawnType.NATURAL, pos, RandomSource.create(i * SEED_STEP)),
                        "日阵里露天的正午能刷");
            }
        }));
        helper.succeed();
    }

    // ================================================================== 渊鳗

    /** 生成门槛：水柱至少 6 格深、y 在 30~56（含两端）；放置方式在水里、按海底高度图；类别是怪物 */
    @GameTest(template = LARGE)
    public static void eelSpawnPlacement(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        EntityType<AbyssalEelEntity> type = ECHoannaNativesB.ABYSSAL_EEL.get();
        helper.assertTrue(type.getCategory() == MobCategory.MONSTER, "类别是 MONSTER");
        helper.assertTrue(SpawnPlacements.getPlacementType(type) == SpawnPlacements.Type.IN_WATER
                && SpawnPlacements.getHeightmapType(type) == Heightmap.Types.OCEAN_FLOOR, "放置方式 IN_WATER、高度图 OCEAN_FLOOR");
        helper.assertTrue(AbyssalEelEntity.inHeightRange(30, EEL) && AbyssalEelEntity.inHeightRange(56, EEL)
                && !AbyssalEelEntity.inHeightRange(29, EEL) && !AbyssalEelEntity.inHeightRange(57, EEL), "高度 30~56 含两端");

        waterColumn(helper, 2, 2, 0, 6);
        waterColumn(helper, 7, 7, 0, 5);
        BlockPos deep = helper.absolutePos(new BlockPos(2, 3, 2));
        BlockPos shallow = helper.absolutePos(new BlockPos(7, 3, 7));
        EelValues here = eelHeights(deep.getY() - 1, deep.getY() + 1);
        helper.assertTrue(AbyssalEelEntity.waterDepth(level, deep, 16) == 6 && AbyssalEelEntity.waterDepth(level, shallow, 16) == 5, "两根水柱 6 格、5 格深");
        helper.assertTrue(AbyssalEelEntity.placeAllowed(level, deep, here), "6 格深、高度在范围里：能刷");
        helper.assertFalse(AbyssalEelEntity.placeAllowed(level, shallow, here), "5 格深：不刷");
        helper.assertFalse(AbyssalEelEntity.placeAllowed(level, deep, eelHeights(deep.getY() + 1, deep.getY() + 5)), "比最低高度低：不刷");
        helper.assertFalse(AbyssalEelEntity.placeAllowed(level, deep, eelHeights(deep.getY() - 5, deep.getY() - 1)), "比最高高度高：不刷");
        helper.succeed();
    }

    /** 下拽：在水里往下拉（竖直速度变负、发给客户端）；脚下快到底了不拉（不往方块里拽）；目标往上挣、离太远都松口 */
    @GameTest(template = LARGE)
    public static void eelDragRules(GameTestHelper helper) {
        waterColumn(helper, 5, 5, 1, 5);
        ServerPlayer swimmer = survivor(helper, "n2-drag", new Vec3(5.5D, 4D, 5.5D));
        AbyssalEelEntity eel = eel(helper, new Vec3(5.5D, 5D, 5.5D));
        try {
            swimmer.baseTick();
            helper.assertTrue(swimmer.isInWater(), "测试前提：玩家在水里");
            swimmer.setDeltaMovement(Vec3.ZERO);
            swimmer.hurtMarked = false;
            helper.assertTrue(eel.startDrag(swimmer) && eel.dragTarget() == swimmer, "咬中后开始下拽");
            eel.tickDrag();
            helper.assertTrue(eel.dragTarget() == swimmer && swimmer.getDeltaMovement().y < 0D && swimmer.hurtMarked, "往下拉、速度发给客户端");
            helper.assertTrue(swimmer.getDeltaMovement().y >= -EEL.dragSpeed() - 1.0E-6D, "最快 " + EEL.dragSpeed() + " 格 / 刻");

            Vec3 at = swimmer.position();
            swimmer.setPos(at.x, at.y + 0.2D, at.z);
            eel.tickDrag();
            helper.assertTrue(eel.dragTarget() == null, "往上挣（这一刻升了 0.2 格）就松口");

            moveTo(helper, swimmer, new Vec3(5.5D, 2D, 5.5D));
            swimmer.baseTick();
            swimmer.setDeltaMovement(Vec3.ZERO);
            helper.assertTrue(swimmer.isInWater(), "测试前提：玩家站在水底");
            helper.assertFalse(AbyssalEelEntity.pullDown(swimmer, EEL.dragSpeed()), "脚下就是石头：不往方块里拽");
            helper.assertTrue(swimmer.getDeltaMovement().y == 0D, "速度没动");

            moveTo(helper, swimmer, new Vec3(5.5D, 4D, 5.5D));
            swimmer.baseTick();
            eel.startDrag(swimmer);
            moveTo(helper, eel, new Vec3(5.5D, 5D, 10.5D));
            eel.tickDrag();
            helper.assertTrue(eel.dragTarget() == null, "离开 " + AbyssalEelEntity.DRAG_BREAK_RANGE + " 格以外松口");

            Pig dry = pig(helper, new Vec3(1.5D, 2D, 1.5D));
            helper.assertFalse(AbyssalEelEntity.pullDown(dry, EEL.dragSpeed()), "不在水里的不拉");
            discard(dry);
        } finally {
            discard(eel);
        }
        helper.succeed();
    }

    /** 渊鳗群首挨第一下叫 elite_summon_count 条普通渊鳗（来源召唤、记着群首、不给经验），只叫一次；普通渊鳗挨打不叫 */
    @GameTest(template = LARGE)
    public static void eelMatriarchCallsPackOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        waterColumn(helper, 5, 5, 1, 6);
        AbyssalEelEntity matriarch = eel(helper, new Vec3(5.5D, 3D, 5.5D));
        matriarch.setElite(true);
        SpawnOrigin.set(matriarch, Origin.NATURAL);
        AbyssalEelEntity plain = eel(helper, new Vec3(5.5D, 5D, 5.5D));
        ServerPlayer attacker = survivor(helper, "n2-pack", new Vec3(5.5D, 7D, 5.5D));
        AABB area = new AABB(helper.absolutePos(new BlockPos(5, 3, 5))).inflate(8D);
        List<AbyssalEelEntity> called = List.of();
        try {
            plain.hurt(attacker.damageSources().playerAttack(attacker), 1F);
            helper.assertFalse(plain.packCalled(), "普通渊鳗挨打不叫");
            matriarch.hurt(attacker.damageSources().playerAttack(attacker), 1F);
            helper.assertTrue(matriarch.packCalled(), "群首挨第一下叫帮手");
            UUID id = matriarch.getUUID();
            called = level.getEntitiesOfClass(AbyssalEelEntity.class, area, e -> id.equals(e.matriarch()));
            helper.assertTrue(called.size() == EEL.eliteSummonCount(), "叫来 " + EEL.eliteSummonCount() + " 条，实际 " + called.size());
            helper.assertTrue(called.stream().allMatch(e -> SpawnOrigin.of(e) == Origin.SUMMONED && !e.isElite() && e.getExperienceReward() == 0),
                    "叫来的来源是召唤、不是精英、不给经验");
            matriarch.invulnerableTime = 0;
            matriarch.hurt(attacker.damageSources().playerAttack(attacker), 1F);
            helper.assertTrue(level.getEntitiesOfClass(AbyssalEelEntity.class, area, e -> id.equals(e.matriarch())).size() == EEL.eliteSummonCount(),
                    "再挨打不再叫");
            AbyssalEelEntity reloaded = ECHoannaNativesB.ABYSSAL_EEL.get().create(level);
            reloaded.load(matriarch.saveWithoutId(new CompoundTag()));
            helper.assertTrue(reloaded.packCalled(), "叫过帮手存盘");
            discard(reloaded);
        } finally {
            discardAll(level.getEntitiesOfClass(AbyssalEelEntity.class, area, e -> matriarch.getUUID().equals(e.matriarch())));
            discardAll(called);
            discard(matriarch, plain);
        }
        helper.succeed();
    }

    // ================================================================== 掉落

    /**
     * 掉落：刷怪笼来的三种都不掉模组物品（畸变体照样可能掉腐肉、渊鳗掉鳕鱼）；公平击杀的畸变体掉残质、余烬灵只掉自己那种余烬、
     * 渊鳗掉残质；精英多掉心脏、余烬双生两种余烬都可能掉。统计时几组都不递减。
     */
    @GameTest(template = EMPTY)
    public static void hoannaNativesBLoot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer killer = survivor(helper, "n2-loot", new Vec3(1.5D, 2D, 1.5D));
        noFatigue(true);
        try {
            ChaosAberrantEntity cagedAberrant = aberrant(helper, new Vec3(2.5D, 2D, 2.5D));
            EmberSpiritEntity cagedEmber = ember(helper, new Vec3(2.5D, 2D, 2.5D), EmberKind.FLAME);
            AbyssalEelEntity cagedEel = eel(helper, new Vec3(2.5D, 2D, 2.5D));
            List<LivingEntity> caged = List.of(cagedAberrant, cagedEmber, cagedEel);
            List<String> tables = List.of("entities/chaos_aberrant", "entities/ember_spirit", "entities/abyssal_eel");
            long seed = 11L;
            for (int c = 0; c < caged.size(); c++) {
                LivingEntity mob = caged.get(c);
                SpawnOrigin.set(mob, Origin.SPAWNER);
                MobDamageShare.record(mob, killer.getUUID(), 100F);
                for (int i = 0; i < 32; i++) {
                    helper.assertFalse(anyModItem(lootFor(level, mob, killer, tables.get(c), seed++ * SEED_STEP)), tables.get(c) + "：刷怪笼来的不掉模组物品");
                }
            }

            ChaosAberrantEntity wild = aberrant(helper, new Vec3(2.5D, 2D, 2.5D));
            SpawnOrigin.set(wild, Origin.NATURAL);
            MobDamageShare.record(wild, killer.getUUID(), 60F);
            int residue = 0;
            int hearts = 0;
            for (int i = 0; i < 64; i++) {
                List<ItemStack> drops = lootFor(level, wild, killer, "entities/chaos_aberrant", seed++ * SEED_STEP);
                residue += count(drops, ECMobDrops.ABERRANT_RESIDUE.get());
                hearts += count(drops, ECMobDrops.ABERRANT_HEART.get());
            }
            helper.assertTrue(residue > 0 && hearts == 0, "公平击杀的畸变体掉残质（35%）、普通的不掉心脏，实际 " + residue + " / " + hearts);

            ChaosAberrantEntity mother = aberrant(helper, new Vec3(2.5D, 2D, 2.5D));
            mother.setElite(true);
            SpawnOrigin.set(mother, Origin.NATURAL);
            MobDamageShare.record(mother, killer.getUUID(), 150F);
            hearts = 0;
            for (int i = 0; i < 32; i++) {
                hearts += count(lootFor(level, mother, killer, "entities/chaos_aberrant", seed++ * SEED_STEP), ECMobDrops.ABERRANT_HEART.get());
            }
            helper.assertTrue(hearts > 0, "母巢多掉心脏（50%）");

            EmberSpiritEntity crystal = ember(helper, new Vec3(2.5D, 2D, 2.5D), EmberKind.CRYSTAL);
            SpawnOrigin.set(crystal, Origin.NATURAL);
            MobDamageShare.record(crystal, killer.getUUID(), 30F);
            int crystals = 0;
            for (int i = 0; i < 64; i++) {
                List<ItemStack> drops = lootFor(level, crystal, killer, "entities/ember_spirit", seed++ * SEED_STEP);
                crystals += count(drops, EmberKind.CRYSTAL.item());
                for (EmberKind other : EmberKind.values()) {
                    helper.assertTrue(other == EmberKind.CRYSTAL || count(drops, other.item()) == 0, "水晶余烬灵不掉 " + other);
                }
            }
            helper.assertTrue(crystals > 0, "水晶余烬灵掉水晶余烬（20%）");

            EmberSpiritEntity twin = ember(helper, new Vec3(2.5D, 2D, 2.5D), EmberKind.ACIDIC);
            twin.setElite(true);
            twin.setSecondEmber(EmberKind.DIVINE);
            SpawnOrigin.set(twin, Origin.NATURAL);
            MobDamageShare.record(twin, killer.getUUID(), 80F);
            int acidic = 0;
            int divine = 0;
            for (int i = 0; i < 96; i++) {
                List<ItemStack> drops = lootFor(level, twin, killer, "entities/ember_spirit", seed++ * SEED_STEP);
                acidic += count(drops, EmberKind.ACIDIC.item());
                divine += count(drops, EmberKind.DIVINE.item());
                helper.assertTrue(count(drops, EmberKind.CRYSTAL.item()) == 0 && count(drops, EmberKind.FLAME.item()) == 0, "余烬双生只掉自己的两种");
            }
            helper.assertTrue(acidic > 0 && divine > 0, "余烬双生两种都掉，实际 " + acidic + " / " + divine);

            AbyssalEelEntity wildEel = eel(helper, new Vec3(2.5D, 2D, 2.5D));
            SpawnOrigin.set(wildEel, Origin.NATURAL);
            MobDamageShare.record(wildEel, killer.getUUID(), 40F);
            residue = 0;
            for (int i = 0; i < 64; i++) {
                residue += count(lootFor(level, wildEel, killer, "entities/abyssal_eel", seed++ * SEED_STEP), ECMobDrops.ABERRANT_RESIDUE.get());
            }
            helper.assertTrue(residue > 0, "公平击杀的渊鳗掉残质（30%）");
        } finally {
            noFatigue(false);
            KillFatigueCondition.forgetCounted();
        }
        helper.succeed();
    }

    /** 余烬组击杀递减：近期余烬组击杀远多于免递减数时，余烬灵的余烬几乎不掉；不递减时照常掉 */
    @GameTest(template = EMPTY)
    public static void emberLootDiminishes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer tired = survivor(helper, "n2-tired", new Vec3(1.5D, 2D, 1.5D));
        ServerPlayer fresh = survivor(helper, "n2-fresh", new Vec3(1.5D, 2D, 1.5D));
        IPlayerData data = data(tired);
        for (int i = 0; i < 600; i++) {
            MobKillLedger.addKill(data, "ember");
        }
        ECMobFramework.Config.setTestFreeKills("ember", 1);
        try {
            int tiredDrops = 0;
            int freshDrops = 0;
            long seed = 101L;
            for (int i = 0; i < 64; i++) {
                EmberSpiritEntity a = ember(helper, new Vec3(2.5D, 2D, 2.5D), EmberKind.COMMON);
                SpawnOrigin.set(a, Origin.NATURAL);
                MobDamageShare.record(a, tired.getUUID(), 30F);
                tiredDrops += count(lootFor(level, a, tired, "entities/ember_spirit", seed++ * SEED_STEP), EmberKind.COMMON.item());
            }
            ECMobFramework.Config.setTestFreeKills("ember", 0);
            for (int i = 0; i < 64; i++) {
                EmberSpiritEntity b = ember(helper, new Vec3(2.5D, 2D, 2.5D), EmberKind.COMMON);
                SpawnOrigin.set(b, Origin.NATURAL);
                MobDamageShare.record(b, fresh.getUUID(), 30F);
                freshDrops += count(lootFor(level, b, fresh, "entities/ember_spirit", seed++ * SEED_STEP), EmberKind.COMMON.item());
            }
            helper.assertTrue(MobKillLedger.kills(data, "ember") >= 600, "台账记着 600 多只余烬组击杀");
            helper.assertTrue(tiredDrops <= 1, "递减后几乎不掉，实际 " + tiredDrops);
            helper.assertTrue(freshDrops >= 4, "不递减时照常掉（20%），实际 " + freshDrops);
        } finally {
            ECMobFramework.Config.setTestFreeKills("ember", null);
            KillFatigueCondition.forgetCounted();
        }
        helper.succeed();
    }

    private static boolean lastHitIs(LivingEntity entity, net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> type) {
        net.minecraft.world.damagesource.DamageSource source = entity.getLastDamageSource();
        return source != null && source.is(type);
    }
}
