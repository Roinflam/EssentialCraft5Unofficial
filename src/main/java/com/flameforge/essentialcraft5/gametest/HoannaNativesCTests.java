package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.item.armor.IMRUShieldArmor;
import com.flameforge.essentialcraft5.entity.OrbitalStrikeEntity;
import com.flameforge.essentialcraft5.entity.mob.ECFlyingMonster;
import com.flameforge.essentialcraft5.entity.mob.MobDamageShare;
import com.flameforge.essentialcraft5.entity.mob.MobSpawnRules;
import com.flameforge.essentialcraft5.entity.mob.MobWeakness;
import com.flameforge.essentialcraft5.entity.mob.hoanna.CatacombRevenantEntity;
import com.flameforge.essentialcraft5.entity.mob.hoanna.InquisitoriumPatrollerEntity;
import com.flameforge.essentialcraft5.entity.mob.hoanna.PatrollerDestroyerEntity;
import com.flameforge.essentialcraft5.entity.mob.hoanna.RevenantBladeEntity;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvents;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.registry.ECComputer;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesC;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesC.Config.PatrollerValues;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesC.Config.RevenantValues;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.registry.ECMobDrops;
import com.flameforge.essentialcraft5.registry.ECMobFramework;
import com.flameforge.essentialcraft5.util.SpawnOrigin;
import com.flameforge.essentialcraft5.util.SpawnOrigin.Origin;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSpawnOverride;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraft.core.registries.Registries;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 第十三批 13d-N3「审判庭巡卫、墓穴守灵」的 GameTest（batch13.md §5.2、§12.2 13d 行）：
 * <ul>
 *   <li>巡卫：生成时按配置设属性、会飞、弹射物只吃一半；护甲破坏者照全息投影的效果磨装备、MRU 盔甲按 0 阶原价扣电、只撞技能目标；
 *       定标是 2 秒预警、4 点、落在目标脚下、只打技能目标，执行机连着三记、同时 ≤ 3 记；掉落表没有故障核心；不看亮度、附近上限（黑暗天灾时按 64 格）。</li>
 *   <li>守灵：弹射物约 30% 穿过（按 UUID 定死，同一发一直穿过）、事件结果改成 SKIP_ENTITY；只在墓穴拼件的刷怪覆盖里、不在任何群系表里；
 *       亮度 &gt; 7 不刷；骨誓（8 格内同伴力量 I、填 0 关掉、死亡事件被取消不发）；守灵长的灵刃（上限、能被打掉、非精英不放）；掉落表。</li>
 * </ul>
 * 改配置一律用 {@code ECHoannaNativesC.Config.setTest*}（同一段同步代码里设、测、置回）。要往世界里放怪的几条各自一批，免得数到别的测试的。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class HoannaNativesCTests {

    private static final String EMPTY = "empty";
    private static final String LARGE = "empty_large";
    private static final PatrollerValues PATROLLER = ECHoannaNativesC.Config.PATROLLER_DEFAULTS;
    private static final RevenantValues REVENANT = ECHoannaNativesC.Config.REVENANT_DEFAULTS;
    private static final double EPS = 1.0E-4D;

    private HoannaNativesCTests() {
    }

    // ================================================================== 工具

    private static <T extends Mob> T create(GameTestHelper helper, EntityType<T> type, Vec3 relative) {
        T mob = type.create(helper.getLevel());
        if (mob == null) {
            throw new IllegalStateException(type + " 应能创建");
        }
        Vec3 at = helper.absoluteVec(relative);
        mob.moveTo(at.x, at.y, at.z, 0F, 0F);
        return mob;
    }

    private static InquisitoriumPatrollerEntity patroller(GameTestHelper helper, Vec3 relative) {
        InquisitoriumPatrollerEntity mob = create(helper, ECHoannaNativesC.INQUISITORIUM_PATROLLER.get(), relative);
        mob.applyConfiguredAttributes();
        return mob;
    }

    private static CatacombRevenantEntity revenant(GameTestHelper helper, Vec3 relative) {
        CatacombRevenantEntity mob = create(helper, ECHoannaNativesC.CATACOMB_REVENANT.get(), relative);
        mob.applyConfiguredAttributes();
        return mob;
    }

    /** 真玩家：生存模式、满血、在 relativeFeet（不进世界） */
    private static ServerPlayer survivor(GameTestHelper helper, String name, Vec3 relativeFeet) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        player.setGameMode(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(relativeFeet);
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    private static void finalizeSpawn(Mob mob, ServerLevel level, MobSpawnType type, double eliteChance) {
        ECMobFramework.Config.setTestEliteChance(eliteChance);
        try {
            ForgeEventFactory.onFinalizeSpawn(mob, level, level.getCurrentDifficultyAt(mob.blockPosition()), type, null, null);
        } finally {
            ECMobFramework.Config.setTestEliteChance(null);
        }
    }

    private static void discardAll(List<? extends net.minecraft.world.entity.Entity> entities) {
        for (net.minecraft.world.entity.Entity entity : entities) {
            if (entity != null && !entity.isRemoved()) {
                entity.discard();
            }
        }
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

    private static PatrollerValues patrollerWith(int strikeInterval, int maxStrikes, int eliteStrikes) {
        PatrollerValues p = PATROLLER;
        return new PatrollerValues(p.maxHealth(), p.armor(), p.destroyerInterval(), p.destroyerRange(), strikeInterval, p.strikeWarning(),
                p.strikeDamage(), p.strikeRange(), eliteStrikes, p.eliteStrikeGap(), maxStrikes, p.nearbyCap());
    }

    private static RevenantValues revenantWith(double passChance, int oathTicks, int bladeCount) {
        RevenantValues r = REVENANT;
        return new RevenantValues(r.maxHealth(), r.armor(), r.attackDamage(), passChance, r.oathRadius(), oathTicks, r.bladeInterval(), bladeCount,
                r.bladeDamage(), r.bladeHoming(), r.maxLight(), r.nearbyCap());
    }

    // ================================================================== 巡卫

    /** 生成时按配置设生命 / 护甲（改配置不动已在世界里的）、会飞、不受重力；弹射物只吃一半（resists_projectile），近战照常 */
    @GameTest(template = EMPTY)
    public static void patrollerStatsAndProjectileResistance(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        InquisitoriumPatrollerEntity mob = create(helper, ECHoannaNativesC.INQUISITORIUM_PATROLLER.get(), new Vec3(2.5D, 2D, 2.5D));
        finalizeSpawn(mob, level, MobSpawnType.NATURAL, 0D);
        helper.assertTrue(Math.abs(mob.getMaxHealth() - 24F) < EPS && mob.getHealth() == mob.getMaxHealth() && mob.getArmorValue() == 0,
                "巡卫 24 / 0，实际 " + mob.getMaxHealth() + " / " + mob.getArmorValue());
        helper.assertTrue(mob instanceof ECFlyingMonster && mob.isNoGravity(), "巡卫悬停（飞行怪基类、不受重力）");
        helper.assertTrue(mob.hasTrait(MobWeakness.RESISTS_PROJECTILE), "巡卫带弹射物抗性");
        Arrow arrow = new Arrow(level, mob.getX(), mob.getY() + 5D, mob.getZ());
        float projectile = mob.weaknessMultiplier(level.damageSources().arrow(arrow, null));
        float melee = mob.weaknessMultiplier(level.damageSources().generic());
        helper.assertTrue(projectile < 1F && Math.abs(melee - 1F) < EPS, "弹射物打折、普通伤害不变：" + projectile + " / " + melee);
        try {
            ECHoannaNativesC.Config.setTestPatroller(new PatrollerValues(50D, 3D, 80, 16, 120, 40, 4D, 24, 3, 10, 3, 6));
            helper.assertTrue(Math.abs(mob.getMaxHealth() - 24F) < EPS, "改配置不动已经生成的");
            InquisitoriumPatrollerEntity fresh = create(helper, ECHoannaNativesC.INQUISITORIUM_PATROLLER.get(), new Vec3(2.5D, 2D, 2.5D));
            finalizeSpawn(fresh, level, MobSpawnType.NATURAL, 0D);
            helper.assertTrue(Math.abs(fresh.getMaxHealth() - 50F) < EPS && fresh.getArmorValue() == 3, "新生成的照新配置");
        } finally {
            ECHoannaNativesC.Config.setTestPatroller(null);
        }
        helper.succeed();
    }

    /**
     * 护甲破坏者：只撞技能目标（生存玩家、不是当前目标的村民穿过去）；打中主手扣 5 点耐久、普通盔甲扣 5 点耐久，宅男胸甲按 0 阶原价扣 5 × 单价的 MRU、不掉耐久。
     */
    @GameTest(template = EMPTY)
    public static void patrollerDestroyerWearsLikeHologram(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        InquisitoriumPatrollerEntity mob = patroller(helper, new Vec3(2.5D, 3D, 2.5D));
        ServerPlayer player = survivor(helper, "n3-destroy", new Vec3(1.5D, 2D, 1.5D));
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        ItemStack boots = new ItemStack(Items.IRON_BOOTS);
        Item chestItem = ECComputer.armor().get(ArmorItem.Type.CHESTPLATE.ordinal()).get();
        ItemStack chest = MRUStaffItem.full(chestItem);
        player.setItemSlot(EquipmentSlot.MAINHAND, sword);
        player.setItemSlot(EquipmentSlot.FEET, boots);
        player.setItemSlot(EquipmentSlot.CHEST, chest);
        int before = MRUStaffItem.storedMRU(chest);

        PatrollerDestroyerEntity bolt = new PatrollerDestroyerEntity(level, mob);
        Villager villager = EntityType.VILLAGER.create(level);
        helper.assertTrue(villager != null, "村民应能创建");
        helper.assertTrue(bolt.hits(player), "生存玩家是技能目标，撞得上");
        helper.assertFalse(bolt.hits(villager), "不是当前目标的村民穿过去");
        bolt.applyHit(player);
        helper.assertTrue(sword.getDamageValue() == 5, "主手扣 5 点耐久，实际 " + sword.getDamageValue());
        helper.assertTrue(boots.getDamageValue() == 5, "普通盔甲扣 5 点耐久，实际 " + boots.getDamageValue());
        int spent = before - MRUStaffItem.storedMRU(chest);
        int price = 5 * ((IMRUShieldArmor) chestItem).mruPerDamage(chest);
        helper.assertTrue(price > 0 && spent == price && chest.getDamageValue() == 0, "宅男胸甲按 0 阶原价扣 " + price + " MRU、不掉耐久，实际扣 " + spent);
        helper.assertTrue(bolt.getItem().is(ECMaterials.MAGICAL_SLAG.get()), "画面同全息投影那一发（魔力熔渣）");
        helper.succeed();
    }

    /**
     * 定标：在目标脚下放一记 2 秒预警、4 点、攻击者是巡卫的轨道打击，只打技能目标（创造模式的不定）；执行机连着三记（相隔 10 刻），同时 ≤ max_strikes。
     */
    @GameTest(template = EMPTY)
    public static void patrollerStrikeWarnsAndCaps(GameTestHelper helper) {
        InquisitoriumPatrollerEntity mob = patroller(helper, new Vec3(2.5D, 4D, 2.5D));
        ServerPlayer player = survivor(helper, "n3-strike", new Vec3(1.5D, 2D, 1.5D));
        List<OrbitalStrikeEntity> made = new java.util.ArrayList<>();
        try {
            OrbitalStrikeEntity strike = mob.markTarget(player);
            helper.assertTrue(strike != null, "应放下一记轨道打击");
            made.add(strike);
            helper.assertTrue(strike.delayTicks() == 40 && Math.abs(strike.damage() - 4F) < EPS && strike.attacker() == mob,
                    "2 秒预警、4 点、攻击者是巡卫：" + strike.delayTicks() + " / " + strike.damage());
            helper.assertTrue(strike.position().distanceTo(player.position()) < EPS, "落点在目标脚下");
            mob.discard();
            discardAll(made);
            made.clear();

            ServerPlayer creative = survivor(helper, "n3-creative", new Vec3(1.5D, 2D, 1.5D));
            creative.setGameMode(GameType.CREATIVE);
            InquisitoriumPatrollerEntity other = patroller(helper, new Vec3(2.5D, 4D, 2.5D));
            helper.assertTrue(other.markTarget(creative) == null, "创造模式的玩家不是技能目标，不定标");

            InquisitoriumPatrollerEntity executor = patroller(helper, new Vec3(2.5D, 4D, 2.5D));
            executor.setElite(true);
            executor.setTarget(player);
            executor.beginMarking(player);
            helper.assertTrue(executor.activeStrikes() == 1 && executor.pendingStrikes() == 2, "执行机先落一记、还剩两记");
            for (int i = 0; i < PATROLLER.eliteStrikeGap() * 2 + 1; i++) {
                executor.tickTimers();
            }
            helper.assertTrue(executor.activeStrikes() == 3 && executor.pendingStrikes() == 0, "隔 10 刻一记，共三记，实际 " + executor.activeStrikes());
            helper.assertTrue(executor.markTarget(player) == null, "同时 ≤ 3 记");
            made.addAll(helper.getLevel().getEntitiesOfClass(OrbitalStrikeEntity.class, executor.getBoundingBox().inflate(16D), s -> s.attacker() == executor));
            helper.assertTrue(made.size() == 3, "世界里正好三记执行机的打击");
        } finally {
            discardAll(made);
        }
        helper.succeed();
    }

    /** 掉落：公平击杀只有红石、异变残质；<b>永远不掉故障核心</b>；刷怪笼来的只剩被玩家打死的红石；精英多一池异变之心 */
    @GameTest(template = EMPTY)
    public static void patrollerLootNoGlitchyCore(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Set<Item> allowed = Set.of(Items.REDSTONE, ECMobDrops.ABERRANT_RESIDUE.get(), ECMobDrops.ABERRANT_HEART.get());
        boolean residue = false;
        boolean heart = false;
        long seed = 0x9E3779B97F4A7C15L;
        for (int i = 0; i < 40; i++) {
            ServerPlayer killer = survivor(helper, "n3-loot" + i, new Vec3(1.5D, 2D, 1.5D));
            InquisitoriumPatrollerEntity mob = patroller(helper, new Vec3(2.5D, 2D, 2.5D));
            mob.setElite(i % 2 == 0);
            SpawnOrigin.set(mob, Origin.NATURAL);
            MobDamageShare.record(mob, killer.getUUID(), 40F);
            for (ItemStack stack : lootFor(level, mob, killer, "entities/inquisitorium_patroller", seed * (i + 1))) {
                if (stack.isEmpty()) {
                    continue;
                }
                helper.assertFalse(stack.is(ECMaterials.GLITCHY_CORE.get()), "巡卫不掉故障核心");
                helper.assertTrue(allowed.contains(stack.getItem()), "巡卫只掉红石 / 残质 / 异变之心，掉了 " + stack);
                residue |= stack.is(ECMobDrops.ABERRANT_RESIDUE.get());
                heart |= stack.is(ECMobDrops.ABERRANT_HEART.get());
                helper.assertFalse(!mob.isElite() && stack.is(ECMobDrops.ABERRANT_HEART.get()), "普通的不掉异变之心");
            }
        }
        helper.assertTrue(residue && heart, "40 次里应见过残质和异变之心");
        ServerPlayer farmer = survivor(helper, "n3-farm", new Vec3(1.5D, 2D, 1.5D));
        InquisitoriumPatrollerEntity caged = patroller(helper, new Vec3(2.5D, 2D, 2.5D));
        SpawnOrigin.set(caged, Origin.SPAWNER);
        MobDamageShare.record(caged, farmer.getUUID(), 40F);
        for (long s = 1; s <= 20; s++) {
            for (ItemStack stack : lootFor(level, caged, farmer, "entities/inquisitorium_patroller", s * 0x9E3779B97F4A7C15L)) {
                if (stack.isEmpty()) {
                    continue;
                }
                helper.assertTrue(stack.is(Items.REDSTONE), "刷怪笼来的只可能掉红石，掉了 " + stack);
            }
        }
        helper.succeed();
    }

    /**
     * 生成判定：亮处也放行（不看亮度）；附近上限平时 48 格 ≤ nearby_cap（预订满了就拒）；海纳尔黑暗天灾时改按 64 格计；和平难度拒。
     */
    @GameTest(template = EMPTY)
    public static void patrollerSpawnRulesIgnoreLight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 2, 3), Blocks.GLOWSTONE);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        EntityType<InquisitoriumPatrollerEntity> type = ECHoannaNativesC.INQUISITORIUM_PATROLLER.get();
        RandomSource random = RandomSource.create(42L);
        MobSpawnRules.clearCache();
        try {
            int passed = 0;
            for (int i = 0; i < PATROLLER.nearbyCap() + 2; i++) {
                if (InquisitoriumPatrollerEntity.checkPatrollerSpawnRules(type, level, MobSpawnType.NATURAL, pos, random)) {
                    passed++;
                }
            }
            helper.assertTrue(passed == PATROLLER.nearbyCap(), "亮处照刷，48 格内预订满 " + PATROLLER.nearbyCap() + " 只就拒，实际 " + passed);
            helper.assertTrue(MobSpawnRules.isCached(level, type, pos, InquisitoriumPatrollerEntity.NEARBY_RADIUS), "平时按 48 格计");
            MobSpawnRules.clearCache();
            ECHoanna.setTestHoannaOverride(level);
            try {
                HoannaEvents.start(level, HoannaEvent.DARKNESS, 200);
                helper.assertTrue(InquisitoriumPatrollerEntity.checkPatrollerSpawnRules(type, level, MobSpawnType.NATURAL, pos, random),
                        "黑暗天灾里照刷");
                helper.assertTrue(MobSpawnRules.isCached(level, type, pos, 64) && !MobSpawnRules.isCached(level, type, pos, 48),
                        "黑暗天灾时按 64 格计");
            } finally {
                HoannaEvents.stop(level);
                ECHoanna.clearTestOverride();
            }
        } finally {
            MobSpawnRules.clearCache();
        }
        helper.succeed();
    }

    // ================================================================== 守灵

    /**
     * 弹射物穿过：几率按（弹射物, 守灵）的 UUID 定死——固定随机源造 4 000 对，穿过的占 27%~33%；同一对永远一样；填 0 永不、填 1 永远。
     * 事件：穿过的那一发结果改成 SKIP_ENTITY，不穿过的照旧 DEFAULT；别的模组已经改过结果的不碰。
     */
    @GameTest(template = EMPTY)
    public static void revenantProjectilesPassThrough(GameTestHelper helper) {
        RandomSource random = RandomSource.create(0x5DEECE66DL);
        UUID revenantId = new UUID(random.nextLong(), random.nextLong());
        int passed = 0;
        int total = 4000;
        for (int i = 0; i < total; i++) {
            UUID projectile = new UUID(random.nextLong(), random.nextLong());
            boolean first = CatacombRevenantEntity.passesThrough(projectile, revenantId, 0.3D);
            helper.assertTrue(first == CatacombRevenantEntity.passesThrough(projectile, revenantId, 0.3D), "同一发、同一只每次一样");
            if (first) {
                passed++;
            }
            helper.assertFalse(CatacombRevenantEntity.passesThrough(projectile, revenantId, 0D), "填 0 永不穿过");
            helper.assertTrue(CatacombRevenantEntity.passesThrough(projectile, revenantId, 1D), "填 1 永远穿过");
        }
        double share = (double) passed / total;
        helper.assertTrue(share > 0.27D && share < 0.33D, "穿过的约占 30%，实际 " + share);

        ServerLevel level = helper.getLevel();
        CatacombRevenantEntity revenant = revenant(helper, new Vec3(2.5D, 2D, 2.5D));
        Arrow passing = null;
        Arrow hitting = null;
        for (int i = 0; i < 64 && (passing == null || hitting == null); i++) {
            Arrow arrow = new Arrow(level, revenant.getX(), revenant.getY() + 3D, revenant.getZ());
            if (revenant.passesThrough(arrow)) {
                passing = passing == null ? arrow : passing;
            } else {
                hitting = hitting == null ? arrow : hitting;
            }
        }
        helper.assertTrue(passing != null && hitting != null, "64 支箭里应有穿过的、也有打中的");
        ProjectileImpactEvent pass = new ProjectileImpactEvent(passing, new EntityHitResult(revenant));
        MinecraftForge.EVENT_BUS.post(pass);
        helper.assertTrue(pass.getImpactResult() == ProjectileImpactEvent.ImpactResult.SKIP_ENTITY, "穿过的那一发结果是 SKIP_ENTITY");
        ProjectileImpactEvent hit = new ProjectileImpactEvent(hitting, new EntityHitResult(revenant));
        MinecraftForge.EVENT_BUS.post(hit);
        helper.assertTrue(hit.getImpactResult() == ProjectileImpactEvent.ImpactResult.DEFAULT, "不穿过的照常打中");
        helper.succeed();
    }

    /**
     * 只在墓穴里：结构 {@code old_catacombs} 覆盖怪物类别、按拼件包围盒，守灵 60 + 骷髅 30 + 僵尸 20；任何群系的刷怪表里都没有守灵；
     * 生成判定：亮度 7 放行、8 拒（盖住的石屋里用光源方块定亮度，等光照更新）。
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void revenantOnlyInCatacombsAndDark(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Structure catacombs = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(EssentialCraft5.id("old_catacombs"));
        helper.assertTrue(catacombs != null, "应有古代墓穴结构");
        StructureSpawnOverride override = catacombs.spawnOverrides().get(MobCategory.MONSTER);
        helper.assertTrue(override != null && override.boundingBox() == StructureSpawnOverride.BoundingBoxType.PIECE, "怪物类别按拼件包围盒覆盖");
        Map<EntityType<?>, Integer> weights = new java.util.HashMap<>();
        for (MobSpawnSettings.SpawnerData data : override.spawns().unwrap()) {
            weights.put(data.type, data.getWeight().asInt());
        }
        helper.assertTrue(weights.equals(Map.of(ECHoannaNativesC.CATACOMB_REVENANT.get(), 60, EntityType.SKELETON, 30, EntityType.ZOMBIE, 20)),
                "墓穴里守灵 60、骷髅 30、僵尸 20，实际 " + weights);
        for (Map.Entry<net.minecraft.resources.ResourceKey<Biome>, Biome> entry : level.registryAccess().registryOrThrow(Registries.BIOME).entrySet()) {
            for (MobSpawnSettings.SpawnerData data : entry.getValue().getMobSettings().getMobs(MobCategory.MONSTER).unwrap()) {
                helper.assertFalse(data.type == ECHoannaNativesC.CATACOMB_REVENANT.get(), entry.getKey().location() + " 的刷怪表里不该有守灵");
            }
        }

        // 3 × 3 × 3 的石屋，中间那格放光源方块
        for (int x = 0; x <= 4; x++) {
            for (int y = 1; y <= 5; y++) {
                for (int z = 0; z <= 4; z++) {
                    boolean inside = x >= 1 && x <= 3 && y >= 2 && y <= 4 && z >= 1 && z <= 3;
                    helper.setBlock(new BlockPos(x, y, z), inside ? Blocks.AIR : Blocks.STONE);
                }
            }
        }
        BlockPos rel = new BlockPos(2, 2, 2);
        BlockPos pos = helper.absolutePos(rel);
        EntityType<CatacombRevenantEntity> type = ECHoannaNativesC.CATACOMB_REVENANT.get();
        helper.setBlock(rel, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 7));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(level.getMaxLocalRawBrightness(pos) == 7, "等光照更新到 7"))
                .thenExecute(() -> {
                    helper.assertTrue(CatacombRevenantEntity.checkRevenantSpawnRules(type, level, MobSpawnType.NATURAL, pos, RandomSource.create(1L)),
                            "亮度 7 放行");
                    helper.setBlock(rel, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 8));
                })
                .thenWaitUntil(() -> helper.assertTrue(level.getMaxLocalRawBrightness(pos) == 8, "等光照更新到 8"))
                .thenExecute(() -> helper.assertFalse(CatacombRevenantEntity.checkRevenantSpawnRules(type, level, MobSpawnType.NATURAL, pos,
                        RandomSource.create(2L)), "亮度 8 拒"))
                .thenSucceed();
    }

    /** 骨誓：一只死时 8 格内别的守灵得到力量 I 10 秒，远处的、别的怪不得；oath_ticks 填 0 时没有 */
    @GameTest(template = LARGE, batch = "hoanna_n3_oath")
    public static void revenantBoneOath(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CatacombRevenantEntity dying = revenant(helper, new Vec3(1.5D, 2D, 1.5D));
        CatacombRevenantEntity near = revenant(helper, new Vec3(5.5D, 2D, 1.5D));
        CatacombRevenantEntity far = revenant(helper, new Vec3(10.5D, 2D, 10.5D));
        InquisitoriumPatrollerEntity other = patroller(helper, new Vec3(3.5D, 2D, 1.5D));
        List<Mob> all = List.of(dying, near, far, other);
        try {
            for (Mob mob : all) {
                mob.setNoAi(true);
                level.addFreshEntity(mob);
            }
            dying.hurt(level.damageSources().genericKill(), 1000F);
            helper.assertTrue(dying.isDeadOrDying(), "测试前提：它死了");
            helper.assertTrue(near.hasEffect(MobEffects.DAMAGE_BOOST) && near.getEffect(MobEffects.DAMAGE_BOOST).getAmplifier() == 0
                    && near.getEffect(MobEffects.DAMAGE_BOOST).getDuration() <= REVENANT.oathTicks(), "8 格内的同伴得到力量 I");
            helper.assertFalse(far.hasEffect(MobEffects.DAMAGE_BOOST), "远处的不得");
            helper.assertFalse(other.hasEffect(MobEffects.DAMAGE_BOOST), "别的怪不得");
            near.removeAllEffects();
            ECHoannaNativesC.Config.setTestRevenant(revenantWith(0.3D, 0, 2));
            try {
                helper.assertTrue(far.boneOath(level) == 0 && !near.hasEffect(MobEffects.DAMAGE_BOOST), "oath_ticks 填 0 关掉骨誓");
            } finally {
                ECHoannaNativesC.Config.setTestRevenant(null);
            }
        } finally {
            discardAll(all);
        }
        helper.succeed();
    }

    /** 守灵长的灵刃：一次两把、活着的不超过两把；有攻击者的一下打掉，摔落打不掉；普通守灵不放；灵刃伤害 5、追 3 秒 */
    @GameTest(template = LARGE, batch = "hoanna_n3_blades")
    public static void revenantWardenBlades(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CatacombRevenantEntity warden = revenant(helper, new Vec3(5.5D, 2D, 5.5D));
        warden.setElite(true);
        warden.setNoAi(true);
        CatacombRevenantEntity plain = revenant(helper, new Vec3(2.5D, 2D, 2.5D));
        plain.setNoAi(true);
        ServerPlayer player = survivor(helper, "n3-blades", new Vec3(8.5D, 2D, 8.5D));
        List<RevenantBladeEntity> blades = new java.util.ArrayList<>();
        try {
            level.addFreshEntity(warden);
            level.addFreshEntity(plain);
            helper.assertTrue(warden.summonBlades(player) == 2, "守灵长一次放两把");
            blades.addAll(level.getEntitiesOfClass(RevenantBladeEntity.class, warden.getBoundingBox().inflate(8D)));
            helper.assertTrue(blades.size() == 2 && warden.bladeCount() == 2, "世界里两把");
            RevenantBladeEntity blade = blades.get(0);
            helper.assertTrue(Math.abs(blade.damage() - 5F) < EPS && blade.homingTicks() == 60 && blade.target() == player, "5 点、追 60 刻、追玩家");
            helper.assertTrue(warden.summonBlades(player) == 0, "已有两把在飞就不再放");
            helper.assertFalse(blade.hurt(level.damageSources().fall(), 5F) || blade.isRemoved(), "摔落这类没有攻击者的打不掉");
            helper.assertTrue(blade.hurt(player.damageSources().playerAttack(player), 1F) && blade.isRemoved(), "玩家一下打掉");
            helper.assertTrue(warden.bladeCount() == 1 && warden.summonBlades(player) == 1, "打掉一把以后补一把");
            blades.addAll(level.getEntitiesOfClass(RevenantBladeEntity.class, warden.getBoundingBox().inflate(8D)));
            helper.assertTrue(plain.summonBlades(player) == 0, "普通守灵不放灵刃");
        } finally {
            discardAll(blades);
            discardAll(List.of(warden, plain));
        }
        helper.succeed();
    }

    /** 守灵掉落：骨头、铁粒、异变残质；普通的不掉异变之心、精英会掉；刷怪笼来的残质拿不到 */
    @GameTest(template = EMPTY)
    public static void revenantLoot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Set<Item> allowed = Set.of(Items.BONE, Items.IRON_NUGGET, ECMobDrops.ABERRANT_RESIDUE.get(), ECMobDrops.ABERRANT_HEART.get());
        Set<Item> seen = new HashSet<>();
        for (int i = 0; i < 40; i++) {
            ServerPlayer killer = survivor(helper, "n3-rloot" + i, new Vec3(1.5D, 2D, 1.5D));
            CatacombRevenantEntity mob = revenant(helper, new Vec3(2.5D, 2D, 2.5D));
            mob.setElite(i % 2 == 0);
            SpawnOrigin.set(mob, Origin.STRUCTURE);
            MobDamageShare.record(mob, killer.getUUID(), 40F);
            for (ItemStack stack : lootFor(level, mob, killer, "entities/catacomb_revenant", (i + 7) * 0x9E3779B97F4A7C15L)) {
                if (stack.isEmpty()) {
                    continue;
                }
                helper.assertTrue(allowed.contains(stack.getItem()), "守灵只掉骨头 / 铁粒 / 残质 / 异变之心，掉了 " + stack);
                helper.assertFalse(!mob.isElite() && stack.is(ECMobDrops.ABERRANT_HEART.get()), "普通的不掉异变之心");
                seen.add(stack.getItem());
            }
        }
        helper.assertTrue(seen.contains(ECMobDrops.ABERRANT_RESIDUE.get()) && seen.contains(ECMobDrops.ABERRANT_HEART.get()), "40 次里应见过残质、异变之心");
        ServerPlayer farmer = survivor(helper, "n3-rfarm", new Vec3(1.5D, 2D, 1.5D));
        CatacombRevenantEntity caged = revenant(helper, new Vec3(2.5D, 2D, 2.5D));
        SpawnOrigin.set(caged, Origin.SPAWNER);
        MobDamageShare.record(caged, farmer.getUUID(), 40F);
        for (long s = 1; s <= 20; s++) {
            for (ItemStack stack : lootFor(level, caged, farmer, "entities/catacomb_revenant", s * 0x9E3779B97F4A7C15L)) {
                if (stack.isEmpty()) {
                    continue;
                }
                helper.assertTrue(stack.is(Items.BONE) || stack.is(Items.IRON_NUGGET), "刷怪笼来的只剩骨头、铁粒，掉了 " + stack);
            }
        }
        helper.succeed();
    }
}
