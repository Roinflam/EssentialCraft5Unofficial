package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.entity.mob.MobSpawnRules;
import com.flameforge.essentialcraft5.entity.mob.hoanna.InquisitoriumPatrollerEntity;
import com.flameforge.essentialcraft5.hoanna.FumeSpawns;
import com.flameforge.essentialcraft5.hoanna.HoannaSpawnHooks;
import com.flameforge.essentialcraft5.hoanna.HoannaTide;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvents;
import com.flameforge.essentialcraft5.hoanna.nest.HoannaNestType;
import com.flameforge.essentialcraft5.hoanna.nest.HoannaNestTypes;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesA;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesB;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesC;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesC.Config.SpawnValues;
import com.flameforge.essentialcraft5.registry.ECMobDrops;
import com.flameforge.essentialcraft5.registry.ECMobFramework;
import com.flameforge.essentialcraft5.util.SpawnOrigin;
import com.flameforge.essentialcraft5.util.SpawnOrigin.Origin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 第十三批 13d-N3 海纳尔刷怪表与巢穴类型的 GameTest（batch13.md §6.4、§6.5）：
 * <ul>
 *   <li>七个群系的刷怪表：原版那一份（权重合计 515）不动，陆地每个群系本地怪合计 220、海里只加渊鳗；本地怪都有刷怪花费（frostbitten / mana_prism /
 *       chaos_aberrant / ember_spirit 这四种由本批补上）。</li>
 *   <li>{@link HoannaSpawnHooks}：本地怪按潮汐 0.75 + 0.5T 缩放（同一条目同一倍数永远是同一个实例，关掉 tide_weights 不动）；毒雾时拾荒者再 ×2；
 *       黑暗天灾陆地追加巡卫、地脉风暴陆地追加棱晶（64 格内已满就不加）；海纳尔有天灾时精英几率 ×2。</li>
 *   <li>五种巢穴类型都读得到（这种怪若干 + 1 只领头），奖励表只认巢穴放出的、被玩家清掉的。</li>
 * </ul>
 * 天灾一律在同一段同步代码里 {@code ECHoanna.setTestHoannaOverride} → 开 → 测 → {@code HoannaEvents.stop} + {@code ECHoanna.clearTestOverride}。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class HoannaSpawnTests {

    private static final String EMPTY = "empty";
    private static final String LARGE = "empty_large";
    private static final SpawnValues SPAWNS = ECHoannaNativesC.Config.SPAWN_DEFAULTS;

    /** 原版怪物表（和主世界平原一样）的权重合计 */
    private static final int VANILLA_TOTAL = 515;
    private static final int LAND_NATIVE_TOTAL = 220;

    private HoannaSpawnTests() {
    }

    // ================================================================== 工具

    private static Biome biome(ServerLevel level, String id) {
        Biome biome = level.registryAccess().registryOrThrow(Registries.BIOME).get(EssentialCraft5.id(id));
        if (biome == null) {
            throw new IllegalStateException("没有群系 " + id);
        }
        return biome;
    }

    private static List<MobSpawnSettings.SpawnerData> monsters(ServerLevel level, String biome) {
        return biome(level, biome).getMobSettings().getMobs(MobCategory.MONSTER).unwrap();
    }

    private static LevelEvent.PotentialSpawns event(ServerLevel level, BlockPos pos, List<MobSpawnSettings.SpawnerData> list) {
        return new LevelEvent.PotentialSpawns(level, MobCategory.MONSTER, pos, WeightedRandomList.create(list));
    }

    private static MobSpawnSettings.SpawnerData find(LevelEvent.PotentialSpawns event, EntityType<?> type) {
        for (MobSpawnSettings.SpawnerData data : event.getSpawnerDataList()) {
            if (data.type == type) {
                return data;
            }
        }
        return null;
    }

    private static int count(LevelEvent.PotentialSpawns event, EntityType<?> type) {
        int n = 0;
        for (MobSpawnSettings.SpawnerData data : event.getSpawnerDataList()) {
            if (data.type == type) {
                n++;
            }
        }
        return n;
    }

    private static SpawnValues spawnsWith(boolean tideWeights) {
        SpawnValues s = SPAWNS;
        return new SpawnValues(tideWeights, s.darknessPatrollerWeight(), s.darknessPatrollerMaxNearby(), s.stormPrismWeight(), s.stormPrismMaxNearby(),
                s.fumesScavengerMultiplier(), s.calamityEliteMultiplier());
    }

    /** 在海纳尔覆盖下开 event 天灾，跑 body，同步收尾 */
    private static void withCalamity(GameTestHelper helper, HoannaEvent event, Runnable body) {
        ServerLevel level = helper.getLevel();
        ECHoanna.setTestHoannaOverride(level);
        try {
            if (event != null && !event.isNone()) {
                helper.assertTrue(HoannaEvents.start(level, event, 400), "应能开天灾 " + event);
            }
            body.run();
        } finally {
            HoannaEvents.stop(level);
            ECHoanna.clearTestOverride();
        }
    }

    // ================================================================== 群系刷怪表

    /**
     * 七个群系：原版那份合计 515 原样留着；陆地本地怪合计 220、和设计表一致；海里本地怪只有渊鳗 120；任何群系都没有守灵（它只在墓穴里）；
     * 本地怪都带刷怪花费。
     */
    @GameTest(template = EMPTY)
    public static void hoannaBiomeSpawnTables(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Map<String, Map<String, int[]>> expected = new LinkedHashMap<>();
        expected.put("frozen_corruption", Map.of("frostbitten", new int[]{180, 2, 3}, "mana_prism", new int[]{40, 1, 1}));
        expected.put("magic_corruption", Map.of("mana_prism", new int[]{140, 1, 2}, "frostbitten", new int[]{80, 1, 2}));
        expected.put("chaos_corruption", Map.of("chaos_aberrant", new int[]{180, 1, 2}, "ember_spirit", new int[]{40, 1, 1}));
        expected.put("dreadlands", Map.of("ember_spirit", new int[]{120, 1, 2}, "hoanna_scavenger", new int[]{100, 1, 3}));
        expected.put("hoanna_desert", Map.of("hoanna_scavenger", new int[]{120, 1, 3}, "ember_spirit", new int[]{100, 1, 2}));
        expected.put("shadow_corruption", Map.of("inquisitorium_patroller", new int[]{160, 2, 3}, "chaos_aberrant", new int[]{60, 1, 1}));
        expected.put("hoanna_sea", Map.of("abyssal_eel", new int[]{120, 1, 3}));
        for (Map.Entry<String, Map<String, int[]>> entry : expected.entrySet()) {
            String id = entry.getKey();
            Biome biome = biome(level, id);
            int vanilla = 0;
            int natives = 0;
            Map<String, int[]> seen = new HashMap<>();
            for (MobSpawnSettings.SpawnerData data : monsters(level, id)) {
                ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(data.type);
                helper.assertFalse(data.type == ECHoannaNativesC.CATACOMB_REVENANT.get(), id + " 里不该有守灵");
                if (HoannaSpawnHooks.isNative(data.type)) {
                    natives += data.getWeight().asInt();
                    seen.put(key.getPath(), new int[]{data.getWeight().asInt(), data.minCount, data.maxCount});
                    helper.assertTrue(biome.getMobSettings().getMobSpawnCost(data.type) != null || data.type == ECHoannaNativesB.ABYSSAL_EEL.get()
                                    || data.type == ECHoannaNativesA.HOANNA_SCAVENGER.get() || data.type == ECHoannaNativesC.INQUISITORIUM_PATROLLER.get(),
                            id + " 的 " + key + " 应带刷怪花费");
                } else if ("minecraft".equals(key.getNamespace())) {
                    vanilla += data.getWeight().asInt();
                }
            }
            helper.assertTrue(vanilla == VANILLA_TOTAL, id + " 的原版怪物表应原样（合计 515），实际 " + vanilla);
            int want = "hoanna_sea".equals(id) ? 120 : LAND_NATIVE_TOTAL;
            helper.assertTrue(natives == want, id + " 的本地怪合计应为 " + want + "，实际 " + natives);
            helper.assertTrue(seen.keySet().equals(entry.getValue().keySet()), id + " 的本地怪应为 " + entry.getValue().keySet() + "，实际 " + seen.keySet());
            for (Map.Entry<String, int[]> want2 : entry.getValue().entrySet()) {
                int[] got = seen.get(want2.getKey());
                helper.assertTrue(java.util.Arrays.equals(got, want2.getValue()),
                        id + " 的 " + want2.getKey() + " 应为 " + java.util.Arrays.toString(want2.getValue()) + "，实际 " + java.util.Arrays.toString(got));
            }
        }
        // 本批补的四种刷怪花费
        MobSpawnSettings frozen = biome(level, "frozen_corruption").getMobSettings();
        MobSpawnSettings.MobSpawnCost frost = frozen.getMobSpawnCost(ECHoannaNativesA.FROSTBITTEN.get());
        MobSpawnSettings.MobSpawnCost prism = frozen.getMobSpawnCost(ECHoannaNativesA.MANA_PRISM.get());
        helper.assertTrue(frost != null && Math.abs(frost.charge() - 0.7D) < 1.0E-6D && Math.abs(frost.energyBudget() - 0.15D) < 1.0E-6D, "霜蚀者花费 0.7 / 0.15");
        helper.assertTrue(prism != null && Math.abs(prism.charge() - 1.0D) < 1.0E-6D && Math.abs(prism.energyBudget() - 0.1D) < 1.0E-6D, "棱晶花费 1.0 / 0.1");
        helper.succeed();
    }

    // ================================================================== 潮汐

    /**
     * 潮位 1 → 本地怪 ×1.25，潮位 0 → ×0.75，0.5 不动；原版怪不动；同一条目同一倍数第二次拿到的是同一个实例（原版 canSpawnMobAt 按引用比对）；
     * 关掉 tide_weights 一律不动。
     */
    @GameTest(template = EMPTY)
    public static void hoannaTideScalesNatives(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        List<MobSpawnSettings.SpawnerData> table = monsters(level, "frozen_corruption");
        EntityType<?> frost = ECHoannaNativesA.FROSTBITTEN.get();
        try {
            withCalamity(helper, null, () -> {
                HoannaTide.setTestOverride(1F);
                LevelEvent.PotentialSpawns high = event(level, pos, table);
                HoannaSpawnHooks.apply(high, level, pos);
                MobSpawnSettings.SpawnerData scaled = find(high, frost);
                helper.assertTrue(scaled != null && scaled.getWeight().asInt() == 225 && scaled.minCount == 2 && scaled.maxCount == 3,
                        "潮位 1：霜蚀者 180 → 225，组 2~3 不变");
                helper.assertTrue(find(high, EntityType.ZOMBIE).getWeight().asInt() == 95, "原版僵尸不动");
                helper.assertTrue(high.getSpawnerDataList().size() == table.size(), "条目数不变（换掉、不叠加）");
                LevelEvent.PotentialSpawns again = event(level, pos, table);
                HoannaSpawnHooks.apply(again, level, pos);
                helper.assertTrue(find(again, frost) == scaled, "同一条目同一倍数是同一个实例");

                HoannaTide.setTestOverride(0F);
                LevelEvent.PotentialSpawns low = event(level, pos, table);
                HoannaSpawnHooks.apply(low, level, pos);
                helper.assertTrue(find(low, frost).getWeight().asInt() == 135, "潮位 0：180 → 135");

                HoannaTide.setTestOverride(0.5F);
                LevelEvent.PotentialSpawns mid = event(level, pos, table);
                HoannaSpawnHooks.apply(mid, level, pos);
                helper.assertTrue(table.contains(find(mid, frost)) && find(mid, frost).getWeight().asInt() == 180, "潮位 0.5：原条目不动");

                HoannaTide.setTestOverride(1F);
                ECHoannaNativesC.Config.setTestSpawns(spawnsWith(false));
                LevelEvent.PotentialSpawns off = event(level, pos, table);
                HoannaSpawnHooks.apply(off, level, pos);
                helper.assertTrue(find(off, frost).getWeight().asInt() == 180, "关掉 tide_weights 不缩放");
                helper.assertTrue(HoannaSpawnHooks.scaledWeight(1, 0.75D) == 1 && HoannaSpawnHooks.scaledWeight(3, 0.75D) == 2, "权重四舍五入、至少 1");
            });
        } finally {
            HoannaTide.setTestOverride(null);
            ECHoannaNativesC.Config.setTestSpawns(null);
        }
        helper.succeed();
    }

    // ================================================================== 天灾

    /** 黑暗天灾：陆地追加巡卫（权重 60、组 2~3），海里不加；没有天灾时不加；追加的条目每次是同一个实例 */
    @GameTest(template = EMPTY)
    public static void hoannaDarknessAddsPatrollers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        List<MobSpawnSettings.SpawnerData> table = monsters(level, "frozen_corruption");
        EntityType<?> patroller = ECHoannaNativesC.INQUISITORIUM_PATROLLER.get();
        MobSpawnRules.clearCache();
        try {
            HoannaTide.setTestOverride(0.5F);
            HoannaSpawnHooks.setTestLand(true);
            withCalamity(helper, null, () -> {
                LevelEvent.PotentialSpawns calm = event(level, pos, table);
                HoannaSpawnHooks.apply(calm, level, pos);
                helper.assertTrue(count(calm, patroller) == 0, "没有天灾不加巡卫");
            });
            withCalamity(helper, HoannaEvent.DARKNESS, () -> {
                LevelEvent.PotentialSpawns land = event(level, pos, table);
                HoannaSpawnHooks.apply(land, level, pos);
                MobSpawnSettings.SpawnerData added = find(land, patroller);
                helper.assertTrue(added != null && added.getWeight().asInt() == SPAWNS.darknessPatrollerWeight()
                                && added.minCount == HoannaSpawnHooks.PATROLLER_MIN_GROUP && added.maxCount == HoannaSpawnHooks.PATROLLER_MAX_GROUP,
                        "黑暗天灾陆地追加巡卫 60、组 2~3");
                LevelEvent.PotentialSpawns again = event(level, pos, table);
                HoannaSpawnHooks.apply(again, level, pos);
                helper.assertTrue(find(again, patroller) == added, "追加的条目是同一个实例");
                helper.assertTrue(HoannaSpawnHooks.darknessActive(level), "黑暗天灾时巡卫按 64 格计");
                HoannaSpawnHooks.setTestLand(false);
                LevelEvent.PotentialSpawns sea = event(level, pos, monsters(level, "hoanna_sea"));
                HoannaSpawnHooks.apply(sea, level, pos);
                helper.assertTrue(count(sea, patroller) == 0, "海里不加巡卫");
            });
        } finally {
            HoannaTide.setTestOverride(null);
            HoannaSpawnHooks.setTestLand(null);
            MobSpawnRules.clearCache();
        }
        helper.succeed();
    }

    /** 地脉风暴：陆地追加棱晶（60、组 1~2）；64 格内已有 4 只棱晶就不加 */
    @GameTest(template = LARGE, batch = "hoanna_n3_storm")
    public static void hoannaLeyStormAddsPrisms(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(5, 2, 5));
        List<MobSpawnSettings.SpawnerData> table = monsters(level, "chaos_corruption");
        EntityType<?> prismType = ECHoannaNativesA.MANA_PRISM.get();
        List<Mob> prisms = new ArrayList<>();
        MobSpawnRules.clearCache();
        try {
            HoannaTide.setTestOverride(0.5F);
            HoannaSpawnHooks.setTestLand(true);
            withCalamity(helper, HoannaEvent.LEY_STORM, () -> {
                LevelEvent.PotentialSpawns open = event(level, pos, table);
                HoannaSpawnHooks.apply(open, level, pos);
                MobSpawnSettings.SpawnerData added = find(open, prismType);
                helper.assertTrue(added != null && added.getWeight().asInt() == SPAWNS.stormPrismWeight() && added.minCount == 1 && added.maxCount == 2,
                        "地脉风暴陆地追加棱晶 60、组 1~2");
                for (int i = 0; i < SPAWNS.stormPrismMaxNearby(); i++) {
                    Mob prism = (Mob) prismType.create(level);
                    helper.assertTrue(prism != null, "棱晶应能创建");
                    Vec3 at = helper.absoluteVec(new Vec3(2.5D + 2 * i, 3D, 8.5D));
                    prism.moveTo(at.x, at.y, at.z, 0F, 0F);
                    prism.setNoAi(true);
                    SpawnOrigin.set(prism, Origin.SUMMONED);
                    level.addFreshEntity(prism);
                    prisms.add(prism);
                }
                MobSpawnRules.clearCache();
                LevelEvent.PotentialSpawns full = event(level, pos, table);
                HoannaSpawnHooks.apply(full, level, pos);
                helper.assertTrue(count(full, prismType) == 0, "64 格内已有 4 只棱晶就不加");
            });
        } finally {
            for (Mob prism : prisms) {
                prism.discard();
            }
            HoannaTide.setTestOverride(null);
            HoannaSpawnHooks.setTestLand(null);
            MobSpawnRules.clearCache();
        }
        helper.succeed();
    }

    /** 毒雾：拾荒者再 ×2（潮位 0.5 时 120 → 240），别的本地怪不变；毒雾那一条照旧在表里（FumeSpawns）；怪物类别不取消、水生类别照旧取消 */
    @GameTest(template = EMPTY)
    public static void hoannaFumesDoubleScavengers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        List<MobSpawnSettings.SpawnerData> table = monsters(level, "hoanna_desert");
        try {
            HoannaTide.setTestOverride(0.5F);
            withCalamity(helper, HoannaEvent.FUMES, () -> {
                LevelEvent.PotentialSpawns spawns = event(level, pos, table);
                MinecraftForge.EVENT_BUS.post(spawns);
                helper.assertTrue(find(spawns, ECHoannaNativesA.HOANNA_SCAVENGER.get()).getWeight().asInt() == 240, "毒雾时拾荒者 120 → 240");
                helper.assertTrue(find(spawns, ECHoannaNativesB.EMBER_SPIRIT.get()).getWeight().asInt() == 100, "别的本地怪不变");
                helper.assertTrue(!FumeSpawns.fumesSpawning(level) || spawns.getSpawnerDataList().contains(FumeSpawns.spawnerData()), "毒雾那一条照旧");
                helper.assertFalse(spawns.isCanceled(), "怪物类别不取消");
                LevelEvent.PotentialSpawns water = new LevelEvent.PotentialSpawns(level, MobCategory.WATER_CREATURE, pos,
                        WeightedRandomList.create(new ArrayList<>(biome(level, "hoanna_sea").getMobSettings().getMobs(MobCategory.WATER_CREATURE).unwrap())));
                MinecraftForge.EVENT_BUS.post(water);
                helper.assertTrue(water.isCanceled(), "水生类别照旧整批取消");
            });
        } finally {
            HoannaTide.setTestOverride(null);
        }
        helper.succeed();
    }

    /**
     * 毒雾倍数填 0：毒雾期间表里一条拾荒者都没有（不是换成权重 1 的副本），别的本地怪照常；潮位 1 时也一样（倍数相乘还是 0）；
     * 毒雾结束后拾荒者回到表里。
     */
    @GameTest(template = EMPTY)
    public static void hoannaFumesZeroMultiplierRemovesScavengers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        List<MobSpawnSettings.SpawnerData> table = monsters(level, "hoanna_desert");
        EntityType<?> scavenger = ECHoannaNativesA.HOANNA_SCAVENGER.get();
        SpawnValues s = SPAWNS;
        try {
            ECHoannaNativesC.Config.setTestSpawns(new SpawnValues(true, s.darknessPatrollerWeight(), s.darknessPatrollerMaxNearby(), s.stormPrismWeight(),
                    s.stormPrismMaxNearby(), 0D, s.calamityEliteMultiplier()));
            withCalamity(helper, HoannaEvent.FUMES, () -> {
                for (float tide : new float[]{0.5F, 1F}) {
                    HoannaTide.setTestOverride(tide);
                    LevelEvent.PotentialSpawns spawns = event(level, pos, table);
                    HoannaSpawnHooks.apply(spawns, level, pos);
                    helper.assertTrue(count(spawns, scavenger) == 0, "潮位 " + tide + "、毒雾倍数 0：表里不该有拾荒者，实际 " + count(spawns, scavenger) + " 条");
                    MobSpawnSettings.SpawnerData ember = find(spawns, ECHoannaNativesB.EMBER_SPIRIT.get());
                    helper.assertTrue(ember != null && ember.getWeight().asInt() == (tide == 1F ? 125 : 100), "余烬之灵照常按潮汐缩放");
                }
            });
            HoannaTide.setTestOverride(0.5F);
            withCalamity(helper, null, () -> {
                LevelEvent.PotentialSpawns calm = event(level, pos, table);
                HoannaSpawnHooks.apply(calm, level, pos);
                MobSpawnSettings.SpawnerData back = find(calm, scavenger);
                helper.assertTrue(back != null && back.getWeight().asInt() == 120, "没有毒雾时拾荒者照旧 120");
            });
        } finally {
            HoannaTide.setTestOverride(null);
            ECHoannaNativesC.Config.setTestSpawns(null);
        }
        helper.succeed();
    }

    /** 潮位 0.5、没有天灾（或关掉 tide_weights）时什么都不用改：表原样、顺序也不变（不拆了重排），里面还是群系表里的那些实例 */
    @GameTest(template = EMPTY)
    public static void hoannaSpawnHooksNoOpKeepsTable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        List<MobSpawnSettings.SpawnerData> table = monsters(level, "dreadlands");
        try {
            withCalamity(helper, null, () -> {
                HoannaTide.setTestOverride(0.5F);
                LevelEvent.PotentialSpawns mid = event(level, pos, table);
                HoannaSpawnHooks.apply(mid, level, pos);
                helper.assertTrue(mid.getSpawnerDataList().equals(table), "潮位 0.5、没有天灾：表原样");
                HoannaTide.setTestOverride(1F);
                ECHoannaNativesC.Config.setTestSpawns(spawnsWith(false));
                LevelEvent.PotentialSpawns off = event(level, pos, table);
                HoannaSpawnHooks.apply(off, level, pos);
                helper.assertTrue(off.getSpawnerDataList().equals(table), "关掉 tide_weights、没有天灾：表原样");
            });
        } finally {
            HoannaTide.setTestOverride(null);
            ECHoannaNativesC.Config.setTestSpawns(null);
        }
        helper.succeed();
    }

    /** 海纳尔有天灾时精英几率 ×2（夹在 1 以内），没有天灾、不在海纳尔都是 ×1 */
    @GameTest(template = EMPTY)
    public static void hoannaCalamityDoublesEliteChance(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        InquisitoriumPatrollerEntity mob = ECHoannaNativesC.INQUISITORIUM_PATROLLER.get().create(level);
        helper.assertTrue(mob != null, "巡卫应能创建");
        ECMobFramework.Config.setTestEliteChance(0.1D);
        try {
            helper.assertTrue(HoannaSpawnHooks.eliteChanceMultiplier(mob) == 1D, "不在海纳尔 ×1");
            withCalamity(helper, null, () ->
                    helper.assertTrue(HoannaSpawnHooks.eliteChanceMultiplier(mob) == 1D && Math.abs(mob.eliteChance() - 0.1D) < 1.0E-9D, "海纳尔没有天灾 ×1"));
            withCalamity(helper, HoannaEvent.EARTHQUAKE, () ->
                    helper.assertTrue(HoannaSpawnHooks.eliteChanceMultiplier(mob) == SPAWNS.calamityEliteMultiplier()
                            && Math.abs(mob.eliteChance() - 0.2D) < 1.0E-9D, "海纳尔有天灾 ×2：0.1 → " + mob.eliteChance()));
            ECMobFramework.Config.setTestEliteChance(0.8D);
            withCalamity(helper, HoannaEvent.DARKNESS, () -> helper.assertTrue(mob.eliteChance() == 1D, "夹在 1 以内"));
        } finally {
            ECMobFramework.Config.setTestEliteChance(null);
            mob.discard();
        }
        helper.succeed();
    }

    // ================================================================== 巢穴类型

    /**
     * 五种巢穴类型都读得到：这一种怪若干 + 1 只同种领头、半径 6 / 16 / 48、奖励表同名；奖励表对巢穴放出的、被玩家清掉的那只掷：
     * 异变之心 1、异变残质 2~4、一件遗物，辐射陨坑另加辐晶 2~4、沉城卫兵另加渊盐 4~8；同一只怪换成自然生成的什么都不给。
     */
    @GameTest(template = EMPTY)
    public static void hoannaNestTypesAndRewards(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Map<String, Object[]> expected = new LinkedHashMap<>();
        expected.put("frozen_camp", new Object[]{ECHoannaNativesA.FROSTBITTEN.get(), 4, null, 0, 0});
        expected.put("chaos_fissure", new Object[]{ECHoannaNativesB.CHAOS_ABERRANT.get(), 3, null, 0, 0});
        expected.put("fallout_crater", new Object[]{ECHoannaNativesA.HOANNA_SCAVENGER.get(), 4, "radiant_crystal", 2, 4});
        expected.put("outpost_camp", new Object[]{ECHoannaNativesC.INQUISITORIUM_PATROLLER.get(), 4, null, 0, 0});
        expected.put("sunken_guard", new Object[]{ECHoannaNativesC.CATACOMB_REVENANT.get(), 3, "abyssal_salt", 4, 8});
        int round = 0;
        for (Map.Entry<String, Object[]> entry : expected.entrySet()) {
            ResourceLocation id = EssentialCraft5.id(entry.getKey());
            HoannaNestType type = HoannaNestTypes.get(id);
            helper.assertTrue(type != null, "应有巢穴类型 " + id);
            EntityType<?> mobType = (EntityType<?>) entry.getValue()[0];
            int normal = (Integer) entry.getValue()[1];
            helper.assertTrue(type.members().size() == 2, id + " 两行：普通 + 领头");
            HoannaNestType.Member plain = type.members().get(0);
            HoannaNestType.Member leader = type.members().get(1);
            helper.assertTrue(plain.type() == mobType && plain.count() == normal && !plain.leader(), id + " 普通 " + normal + " 只 " + mobType);
            helper.assertTrue(leader.type() == mobType && leader.count() == 1 && leader.leader(), id + " 1 只同种领头");
            // 沉城卫兵的落点半径是 2：核心在穹顶里离中心 6 格，半径再大落点会出穹顶（SunkenDistrictTests.placeSunkenEndToEnd 守着）
            int spawnRadius = "sunken_guard".equals(entry.getKey()) ? 2 : 6;
            helper.assertTrue(type.spawnRadius() == spawnRadius && type.activateRadius() == 16 && type.leashRadius() == 48,
                    id + " 半径 " + spawnRadius + " / 16 / 48");
            helper.assertTrue(type.lootTable().equals(EssentialCraft5.id("hoanna_nests/" + entry.getKey())), id + " 奖励表同名");

            LootTable table = level.getServer().getLootData().getLootTable(type.lootTable());
            String extraId = (String) entry.getValue()[2];
            Item extra = extraId == null ? null : BuiltInRegistries.ITEM.get(EssentialCraft5.id(extraId));
            for (int i = 0; i < 4; i++) {
                round++;
                Mob mob = (Mob) mobType.create(level);
                helper.assertTrue(mob != null, "应能创建 " + mobType);
                Vec3 at = helper.absoluteVec(new Vec3(2.5D, 2D, 2.5D));
                mob.moveTo(at.x, at.y, at.z, 0F, 0F);
                SpawnOrigin.set(mob, Origin.NEST);
                ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "n3-nest" + round);
                player.setGameMode(GameType.SURVIVAL);
                Map<Item, Integer> got = roll(level, table, mob, player, round * 0x9E3779B97F4A7C15L);
                int heart = got.getOrDefault(ECMobDrops.ABERRANT_HEART.get(), 0);
                int residue = got.getOrDefault(ECMobDrops.ABERRANT_RESIDUE.get(), 0);
                helper.assertTrue(heart == 1, id + " 异变之心 1 个，实际 " + heart);
                helper.assertTrue(residue >= 2 && residue <= 4, id + " 异变残质 2~4，实际 " + residue);
                if (extra != null) {
                    int n = got.getOrDefault(extra, 0);
                    helper.assertTrue(n >= (Integer) entry.getValue()[3] && n <= (Integer) entry.getValue()[4], id + " 的 " + extraId + " 数量不对：" + n);
                }
                SpawnOrigin.set(mob, Origin.NATURAL);
                helper.assertTrue(roll(level, table, mob, player, round * 0x9E3779B97F4A7C15L).isEmpty(), id + " 不是巢穴放出的什么都不给");
                mob.discard();
            }
        }
        helper.succeed();
    }

    private static Map<Item, Integer> roll(ServerLevel level, LootTable table, Mob victim, ServerPlayer player, long seed) {
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, victim)
                .withParameter(LootContextParams.ORIGIN, victim.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().playerAttack(player))
                .withParameter(LootContextParams.KILLER_ENTITY, player)
                .withParameter(LootContextParams.DIRECT_KILLER_ENTITY, player)
                .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player)
                .create(LootContextParamSets.ENTITY);
        Map<Item, Integer> out = new HashMap<>();
        for (ItemStack stack : table.getRandomItems(params, seed)) {
            if (stack.isEmpty()) {
                continue;
            }
            out.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        return out;
    }
}
