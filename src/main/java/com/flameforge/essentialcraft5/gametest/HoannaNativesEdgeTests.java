package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.config.CachedServerValues;
import com.flameforge.essentialcraft5.entity.mob.MobSpawnRules;
import com.flameforge.essentialcraft5.entity.mob.hoanna.AbyssalEelEntity;
import com.flameforge.essentialcraft5.hoanna.HoannaSpawnHooks;
import com.flameforge.essentialcraft5.hoanna.HoannaTide;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvents;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesA;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesA.Config.ScavengerValues;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesB;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesB.Config.AberrantValues;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesB.Config.EelValues;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesC;
import com.flameforge.essentialcraft5.registry.ECTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSpawnOverride;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 第十三批 13d 海纳尔本地怪的边界情形（batch13.md §5.2、§6.5）：
 * <ul>
 *   <li>实体那一关（Forge {@code checkSpawnPosition}）：正午露天逐种过一遍，写了「不看亮度 / 白天也刷」的种类放行、其余拦下；
 *       渊鳗在水里放行（原版 {@code Mob} 默认要求碰撞箱里没有液体，水里的怪要自己覆盖）。新加进 {@code hoanna_natives} 的种类
 *       没写进下面的表就直接报错，免得同类问题漏过去。</li>
 *   <li>渊鳗下拽玩家：服务端那份速度停在上一次发过去的值时照样往下拉，玩家已经在往下沉、够快了就不再加。</li>
 *   <li>天灾追加的巡卫、棱晶不加进结构的怪物刷怪覆盖（古代墓穴）。</li>
 *   <li>配置记录按版本号缓存：同一版读到同一个实例，配置重载后换新的，测试覆盖值照旧优先。</li>
 * </ul>
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class HoannaNativesEdgeTests {

    private static final String EMPTY = "empty";
    private static final String LARGE = "empty_large";
    private static final ScavengerValues SCAV = ECHoannaNativesA.Config.SCAVENGER_DEFAULTS;
    private static final EelValues EEL = ECHoannaNativesB.Config.EEL_DEFAULTS;

    /** 正午露天、没有日阵时实体那一关该不该放行（按注册名）；渊鳗看的是水里那一格 */
    private static final Map<String, Boolean> NOON_OPEN_SKY = Map.of(
            "frostbitten", false,
            "mana_prism", false,
            "chaos_aberrant", false,
            "ember_spirit", false,
            "hoanna_scavenger", true,
            "abyssal_eel", true,
            "inquisitorium_patroller", true,
            "catacomb_revenant", false);

    private HoannaNativesEdgeTests() {
    }

    // ================================================================== 工具

    private static void moveTo(GameTestHelper helper, Entity entity, Vec3 relative) {
        Vec3 at = helper.absoluteVec(relative);
        entity.moveTo(at.x, at.y, at.z, 0F, 0F);
    }

    /** 一根 1 × 1 的水柱（四周玻璃），底在 floorY，水深 depth */
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

    /** 把（全局的）时间拨到正午、雨停，刷新天空亮度跑 body，finally 里放回 */
    private static void atClearNoon(ServerLevel level, Runnable body) {
        long savedTime = level.getDayTime();
        float savedRain = level.getRainLevel(1F);
        float savedThunder = level.getThunderLevel(1F);
        level.setDayTime(Math.floorDiv(savedTime, HoannaEvents.DAY_TICKS) * HoannaEvents.DAY_TICKS + 6_000L);
        level.setRainLevel(0F);
        level.setThunderLevel(0F);
        level.updateSkyBrightness();
        try {
            body.run();
        } finally {
            level.setDayTime(savedTime);
            level.setRainLevel(savedRain);
            level.setThunderLevel(savedThunder);
            level.updateSkyBrightness();
        }
    }

    private static ScavengerValues scavengerSurfaceFrom(int minY) {
        return new ScavengerValues(SCAV.maxHealth(), SCAV.armor(), SCAV.crowbarDamage(), SCAV.rifleDamage(), SCAV.rifleChance(), SCAV.rifleAim(),
                SCAV.rifleRange(), SCAV.rifleRest(), SCAV.crowbarDose(), SCAV.rifleDose(), SCAV.stimHealth(), SCAV.stimTicks(), SCAV.stimCooldown(),
                SCAV.eliteSummonCount(), SCAV.eliteFlareHealth(), minY, SCAV.nearbyCap());
    }

    private static boolean entityCheck(ServerLevel level, Mob mob) {
        return ForgeEventFactory.checkSpawnPosition(mob, level, MobSpawnType.NATURAL);
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

    // ================================================================== 实体那一关

    /**
     * 正午、雨停、露天亮度 ≥ 13：逐种建实例，Forge 的 {@code checkSpawnPosition}（{@code checkSpawnRules && checkSpawnObstruction}）
     * 照 {@link #NOON_OPEN_SKY} 放行或拦下；余烬灵开了日阵改为放行、日阵停了又拦下；渊鳗在 7 格深的水柱里放行。
     */
    @GameTest(template = LARGE)
    public static void nativesEntitySpawnCheckAtNoon(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        waterColumn(helper, 8, 8, 1, 7);
        Vec3 landRel = new Vec3(2.5D, 2D, 2.5D);
        Vec3 waterRel = new Vec3(8.5D, 3D, 8.5D);
        BlockPos land = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.succeedWhen(() -> atClearNoon(level, () -> {
            helper.assertTrue(level.canSeeSky(land) && level.getMaxLocalRawBrightness(land) >= 13,
                    "测试前提：露天、正午亮度 ≥ 13，实际 " + level.getMaxLocalRawBrightness(land));
            ECHoanna.setTestHoannaOverride(level);
            ECHoannaNativesA.Config.setTestScavenger(scavengerSurfaceFrom(land.getY()));
            List<Mob> made = new ArrayList<>();
            try {
                int seen = 0;
                for (Holder<EntityType<?>> holder : BuiltInRegistries.ENTITY_TYPE.getTagOrEmpty(ECTags.HOANNA_NATIVES)) {
                    EntityType<?> type = holder.value();
                    String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
                    Boolean expected = NOON_OPEN_SKY.get(id);
                    helper.assertTrue(expected != null, id + " 进了 hoanna_natives，要在 NOON_OPEN_SKY 里写明正午露天该不该刷");
                    Entity created = type.create(level);
                    helper.assertTrue(created instanceof Mob, id + " 应能创建成 Mob");
                    Mob mob = (Mob) created;
                    made.add(mob);
                    boolean eel = mob instanceof AbyssalEelEntity;
                    moveTo(helper, mob, eel ? waterRel : landRel);
                    if (eel) {
                        helper.assertTrue(mob.isInWater() || level.getFluidState(mob.blockPosition()).isSource(), "测试前提：渊鳗在水里");
                    }
                    helper.assertTrue(entityCheck(level, mob) == expected,
                            id + " 正午" + (eel ? "水里" : "露天") + "应" + (expected ? "放行" : "拦下"));
                    seen++;
                }
                helper.assertTrue(seen == NOON_OPEN_SKY.size(), "hoanna_natives 应有 " + NOON_OPEN_SKY.size() + " 种，实际 " + seen);

                Mob ember = ECHoannaNativesB.EMBER_SPIRIT.get().create(level);
                made.add(ember);
                moveTo(helper, ember, landRel);
                helper.assertFalse(entityCheck(level, ember), "没有日阵：余烬灵正午露天拦下");
                helper.assertTrue(HoannaEvents.start(level, HoannaEvent.SUN_ARRAY, 200), "正午开得起日阵");
                helper.assertTrue(entityCheck(level, ember), "日阵里露天：余烬灵放行");
                HoannaEvents.stop(level);
                helper.assertFalse(entityCheck(level, ember), "日阵停了：又拦下");

                Mob patroller = ECHoannaNativesC.INQUISITORIUM_PATROLLER.get().create(level);
                made.add(patroller);
                moveTo(helper, patroller, landRel);
                helper.assertTrue(patroller.checkSpawnRules(level, MobSpawnType.NATURAL) && entityCheck(level, patroller), "巡卫正午露天放行");
            } finally {
                HoannaEvents.stop(level);
                ECHoanna.clearTestOverride();
                ECHoannaNativesA.Config.setTestScavenger(null);
                for (Mob mob : made) {
                    mob.discard();
                }
            }
        }));
    }

    /** 渊鳗：水里不看挡没挡液体，只看有没有实体挡着；离了水照原版（亮处拦下） */
    @GameTest(template = LARGE)
    public static void eelSpawnObstructionInWater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        waterColumn(helper, 5, 5, 1, 7);
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        helper.succeedWhen(() -> atClearNoon(level, () -> {
            AbyssalEelEntity eel = ECHoannaNativesB.ABYSSAL_EEL.get().create(level);
            try {
                moveTo(helper, eel, new Vec3(5.5D, 3D, 5.5D));
                helper.assertTrue(level.containsAnyLiquid(eel.getBoundingBox()), "测试前提：碰撞箱在水里");
                helper.assertTrue(eel.checkSpawnObstruction(level), "水里没别的实体挡着：放行");
                helper.assertTrue(eel.checkSpawnRules(level, MobSpawnType.NATURAL), "水里不看亮度");
                moveTo(helper, eel, new Vec3(1.5D, 2D, 1.5D));
                helper.assertTrue(level.getMaxLocalRawBrightness(eel.blockPosition()) >= 13, "测试前提：岸上正午亮度 ≥ 13");
                helper.assertFalse(eel.checkSpawnRules(level, MobSpawnType.NATURAL), "离了水：亮处照原版拦下");
            } finally {
                eel.discard();
            }
        }));
    }

    // ================================================================== 渊鳗下拽

    /**
     * 拽玩家：服务端那份速度已经是最快往下（上一次发过去的值）、玩家其实停在原地时，每刻照样往下拉、发给客户端；
     * 玩家这一刻已经往下沉了 drag_speed 格，就不再加速；拽非玩家照旧看它自己的速度。
     */
    @GameTest(template = LARGE)
    public static void eelDragKeepsPullingStillPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        waterColumn(helper, 5, 5, 1, 8);
        ServerPlayer swimmer = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "n2-drag-still");
        for (int i = 0; i <= 60; i++) {
            swimmer.tick();
        }
        swimmer.setGameMode(GameType.SURVIVAL);
        moveTo(helper, swimmer, new Vec3(5.5D, 6D, 5.5D));
        swimmer.setHealth(swimmer.getMaxHealth());
        AbyssalEelEntity eel = ECHoannaNativesB.ABYSSAL_EEL.get().create(level);
        moveTo(helper, eel, new Vec3(5.5D, 7D, 5.5D));
        eel.applyConfiguredAttributes();
        try {
            swimmer.baseTick();
            helper.assertTrue(swimmer.isInWater(), "测试前提：玩家在水里");
            helper.assertTrue(eel.startDrag(swimmer), "咬中后开始下拽");
            double speed = EEL.dragSpeed();
            for (int i = 0; i < 4; i++) {
                swimmer.setDeltaMovement(0D, -speed, 0D);
                swimmer.hurtMarked = false;
                eel.tickDrag();
                helper.assertTrue(eel.dragTarget() == swimmer, "第 " + i + " 刻还拽着");
                helper.assertTrue(swimmer.hurtMarked && swimmer.getDeltaMovement().y < 0D,
                        "第 " + i + " 刻：玩家停在原地、服务端速度是旧值，照样往下拉并发给客户端");
            }
            Vec3 at = swimmer.position();
            // 多沉 0.01 格，免得位置相减的浮点误差让它看起来差一点点没到 drag_speed
            swimmer.setPos(at.x, at.y - speed - 0.01D, at.z);
            swimmer.setDeltaMovement(0D, -speed, 0D);
            swimmer.hurtMarked = false;
            eel.tickDrag();
            helper.assertTrue(eel.dragTarget() == swimmer && !swimmer.hurtMarked, "玩家这一刻已经沉了 " + speed + " 格以上：够快了不再加");
            helper.assertTrue(AbyssalEelEntity.pullDown(swimmer, speed, new Vec3(0D, 0D, 0D)), "按传进来的速度算：停着就往下拉");
            helper.assertFalse(AbyssalEelEntity.pullDown(swimmer, 0D, Vec3.ZERO), "drag_speed 填 0 不拉");
        } finally {
            eel.discard();
        }
        helper.succeed();
    }

    // ================================================================== 天灾追加与结构覆盖

    /** 墓穴的覆盖表：黑暗天灾、地脉风暴都不往里加巡卫 / 棱晶；同一张表不在结构里时照加；超平坦测试世界里真查结构是 false */
    @GameTest(template = EMPTY)
    public static void calamityAdditionsSkipStructureOverrides(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        Structure catacombs = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(EssentialCraft5.id("old_catacombs"));
        helper.assertTrue(catacombs != null, "有古代墓穴结构");
        StructureSpawnOverride override = catacombs.spawnOverrides().get(MobCategory.MONSTER);
        helper.assertTrue(override != null, "古代墓穴有怪物刷怪覆盖");
        List<MobSpawnSettings.SpawnerData> table = override.spawns().unwrap();
        EntityType<?> patroller = ECHoannaNativesC.INQUISITORIUM_PATROLLER.get();
        EntityType<?> prism = ECHoannaNativesA.MANA_PRISM.get();
        helper.assertFalse(HoannaSpawnHooks.inMonsterOverride(level, pos), "超平坦里没有结构");
        MobSpawnRules.clearCache();
        ECHoanna.setTestHoannaOverride(level);
        try {
            HoannaTide.setTestOverride(0.5F);
            HoannaSpawnHooks.setTestLand(true);
            for (HoannaEvent calamity : List.of(HoannaEvent.DARKNESS, HoannaEvent.LEY_STORM)) {
                EntityType<?> added = calamity == HoannaEvent.DARKNESS ? patroller : prism;
                helper.assertTrue(HoannaEvents.start(level, calamity, 400), "应能开天灾 " + calamity);
                try {
                    HoannaSpawnHooks.setTestStructureOverride(true);
                    LevelEvent.PotentialSpawns inside = new LevelEvent.PotentialSpawns(level, MobCategory.MONSTER, pos, WeightedRandomList.create(table));
                    HoannaSpawnHooks.apply(inside, level, pos);
                    helper.assertTrue(count(inside, added) == 0, calamity + "：墓穴的覆盖表里不追加 " + added);
                    HoannaSpawnHooks.setTestStructureOverride(false);
                    LevelEvent.PotentialSpawns outside = new LevelEvent.PotentialSpawns(level, MobCategory.MONSTER, pos, WeightedRandomList.create(table));
                    HoannaSpawnHooks.apply(outside, level, pos);
                    helper.assertTrue(count(outside, added) == 1, calamity + "：不在结构里照加");
                } finally {
                    HoannaEvents.stop(level);
                }
            }
        } finally {
            HoannaSpawnHooks.setTestStructureOverride(null);
            HoannaSpawnHooks.setTestLand(null);
            HoannaTide.setTestOverride(null);
            ECHoanna.clearTestOverride();
            MobSpawnRules.clearCache();
        }
        helper.succeed();
    }

    // ================================================================== 配置缓存

    /** 同一版配置读到同一个实例（命中不分配）；版本号变了换新的、数值不变；测试覆盖值照旧原样返回 */
    @GameTest(template = EMPTY)
    public static void nativeConfigRecordsAreCached(GameTestHelper helper) {
        AberrantValues first = ECHoannaNativesB.Config.aberrant();
        helper.assertTrue(ECHoannaNativesB.Config.aberrant() == first, "同一版配置读到同一个实例");
        helper.assertTrue(ECHoannaNativesA.Config.frost() == ECHoannaNativesA.Config.frost()
                && ECHoannaNativesC.Config.spawns() == ECHoannaNativesC.Config.spawns(), "别的几组也缓存");
        int before = CachedServerValues.revision();
        CachedServerValues.bumpForTest();
        helper.assertTrue(CachedServerValues.revision() == before + 1, "版本号 +1");
        AberrantValues second = ECHoannaNativesB.Config.aberrant();
        helper.assertTrue(second != first && second.equals(first), "版本号变了重建一份，数值相同");
        AberrantValues custom = new AberrantValues(first.maxHealth() + 1D, first.armor(), first.attackDamage(), 0, first.chargeWindup(),
                first.chargeDistance(), first.chargeDamage(), first.chargeHealthPercent(), first.stunTicks(), first.stunDamageTaken(), first.frenzyHealth(),
                first.frenzySpeed(), first.quakeAttack(), first.splitCount(), first.larvaLifetime(), first.broodInterval(), first.broodMax(),
                first.residueTicks(), first.nearbyCap());
        ECHoannaNativesB.Config.setTestAberrant(custom);
        try {
            helper.assertTrue(ECHoannaNativesB.Config.aberrant() == custom, "测试覆盖值原样返回");
        } finally {
            ECHoannaNativesB.Config.setTestAberrant(null);
        }
        helper.assertTrue(ECHoannaNativesB.Config.aberrant() == second, "覆盖值撤掉后回到缓存");
        helper.succeed();
    }
}
