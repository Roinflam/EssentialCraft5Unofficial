package com.flameforge.essentialcraft5.hoanna;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.entity.mob.ECMonster;
import com.flameforge.essentialcraft5.entity.mob.MobSpawnRules;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvents;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesA;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesC;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesC.Config.SpawnValues;
import com.flameforge.essentialcraft5.registry.ECTags;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSpawnOverride;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 海纳尔的刷怪钩子（第十三批 13d-N3，batch13.md §6.5 表下四行、§0.4 第 11 条）：{@code LevelEvent.PotentialSpawns} 只在海纳尔、怪物类别时改刷怪表。
 * 基础的刷怪表是群系 JSON（{@code tools/gen/hoanna_spawns.py} 生成，服主可以用数据包覆盖），这里只做 JSON 表达不了的、跟着潮汐和天灾变的部分：
 * <ul>
 *   <li><b>潮汐</b>（{@code tide_weights}）：表里的海纳尔本地怪（{@code #essentialcraft5:hoanna_natives}）权重 ×(0.75 + 0.5T)，T = {@link HoannaTide#value}；</li>
 *   <li><b>毒雾</b>：遗民拾荒者的权重再 ×{@code fumes_scavenger_multiplier}（默认 2；填 0 时毒雾期间表里去掉拾荒者）；</li>
 *   <li><b>黑暗天灾</b>：所有陆地群系（{@code #essentialcraft5:hoanna_land}）追加审判庭巡卫（权重 {@code darkness_patroller_weight}，每群 2~3），
 *       那里 64 格内已有 {@code darkness_patroller_max_nearby} 只就不追加；巡卫本身不看亮度，它的刷怪判定在黑暗天灾时也按 64 格这个上限预订；</li>
 *   <li>两条天灾追加都只加在群系表上：这一格落在结构自带的怪物刷怪覆盖里（古代墓穴的拼件，§6.5「守灵 60 + 骷髅 30 + 僵尸 20」）时不追加，
 *       不让巡卫、棱晶混进墓穴（{@link #inMonsterOverride}）；</li>
 *   <li><b>地脉风暴</b>：所有陆地群系追加浮游棱晶（权重 {@code storm_prism_weight}，每群 1~2），64 格内已有 {@code storm_prism_max_nearby} 只就不追加；</li>
 *   <li><b>天灾精英</b>：任何天灾（含地脉风暴）进行中，海纳尔里本模组怪的精英几率 ×{@code calamity_elite_multiplier}（{@link #eliteChanceMultiplier}，
 *       在 {@code ECHoannaNativesC.init} 登记进 {@code ECMonster.addEliteChanceMultiplier}；它只对 {@link ECMonster} 的子类起作用）。</li>
 * </ul>
 * <b>换出来的条目必须是固定的实例</b>：原版 {@code NaturalSpawner.canSpawnMobAt} 刷每一只之前会再发一次这个事件，用 {@code List.contains}
 * 按引用比对选中的那一条还在不在（{@link FumeSpawns} 类注释写了这个坑）。权重不可变，缩放过的副本按（原条目, 倍数）缓存成同一个实例——
 * 倍数只有潮位 5 种 × 毒雾有无，缓存很小；追加的巡卫、棱晶条目也各是一个静态实例（配置的权重变了才换新的）。
 * 先缩放表里原有的本地怪、再追加天灾条目：追加的条目不跟着潮汐缩放（它们本身就是天灾期间才有的）。
 * <p>
 * 和 {@link FumeSpawns} 共存：毒雾条目是它在同一个事件里另加的，不在本地怪标签里，这里不碰；它在 LOWEST 整批取消的只是被动类别，不含怪物类别。
 * 只在服务端主线程用。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class HoannaSpawnHooks {

    /** 黑暗巡卫、风暴棱晶「附近已有几只」的半径（格）：和巡卫在黑暗天灾时的刷怪判定、棱晶自己的附近上限共用 {@code MobSpawnRules} 的缓存 */
    public static final int CALAMITY_RADIUS = 64;
    public static final int PATROLLER_MIN_GROUP = 2;
    public static final int PATROLLER_MAX_GROUP = 3;
    public static final int PRISM_MIN_GROUP = 1;
    public static final int PRISM_MAX_GROUP = 2;
    /** 缓存最多记多少个原条目 */
    static final int MAX_CACHED = 1024;

    /** 原条目 → (倍数的位表示 → 缩放过的副本) */
    private static final Map<MobSpawnSettings.SpawnerData, Map<Long, MobSpawnSettings.SpawnerData>> SCALED = new IdentityHashMap<>();
    @Nullable
    private static MobSpawnSettings.SpawnerData darknessPatroller;
    @Nullable
    private static MobSpawnSettings.SpawnerData stormPrism;
    /** GameTest：把事件位置当成陆地 / 海（测试世界是超平坦的原版群系）；null = 按真实群系 */
    @Nullable
    private static volatile Boolean testLand;
    /** GameTest：把事件位置当成在 / 不在结构的怪物刷怪覆盖里；null = 按真实结构 */
    @Nullable
    private static volatile Boolean testOverride;

    private HoannaSpawnHooks() {
    }

    // ------------------------------------------------------------------ 刷怪表

    @SubscribeEvent
    public static void hoannaSpawnHooksPotentialSpawns(LevelEvent.PotentialSpawns event) {
        if (event.getMobCategory() != MobCategory.MONSTER || !(event.getLevel() instanceof ServerLevel level) || !ECHoanna.isHoanna(level)) {
            return;
        }
        apply(event, level, event.getPos());
    }

    /** 按现在的潮汐、天灾改 event 的刷怪表（见类注释；GameTest 直接调） */
    public static void apply(LevelEvent.PotentialSpawns event, ServerLevel level, BlockPos pos) {
        SpawnValues cfg = ECHoannaNativesC.Config.spawns();
        HoannaEvent calamity = HoannaEvents.current(level);
        double tide = tideFactor(level, cfg);
        double scavenger = calamity == HoannaEvent.FUMES ? cfg.fumesScavengerMultiplier() : 1D;
        // 潮位 0.5（或关掉 tide_weights）、又没有毒雾时本地怪一条都不用换：不复制刷怪表、不逐条判标签。
        // 这个事件在怪物没到上限时每个可刷区块每刻都可能发好几次（getRandomSpawnMobAt 一次 + 每次尝试的 canSpawnMobAt 一次）
        if (tide != 1D || scavenger != 1D) {
            for (MobSpawnSettings.SpawnerData data : new ArrayList<>(event.getSpawnerDataList())) {
                if (!data.type.is(ECTags.HOANNA_NATIVES)) {
                    continue;
                }
                double factor = data.type == ECHoannaNativesA.HOANNA_SCAVENGER.get() ? tide * scavenger : tide;
                if (factor == 1D) {
                    continue;
                }
                event.removeSpawnerData(data);
                if (factor > 0D) {
                    event.addSpawnerData(scaled(data, factor));
                }
                // 倍数 0（毒雾倍数填 0）就是这一条整个去掉：缩放副本的权重至少是 1，换进去等于没关掉
            }
        }
        // 结构覆盖放在最后判：只在天灾里、条件都满足时才查（原版取刷怪表时刚查过同一格，结构起点已在缓存里）
        if (calamity == HoannaEvent.DARKNESS && cfg.darknessPatrollerWeight() > 0 && isLand(level, pos)
                && MobSpawnRules.nearbyCount(level, ECHoannaNativesC.INQUISITORIUM_PATROLLER.get(), pos, CALAMITY_RADIUS) < cfg.darknessPatrollerMaxNearby()
                && !inMonsterOverride(level, pos)) {
            event.addSpawnerData(darknessPatroller(cfg.darknessPatrollerWeight()));
        }
        if (calamity == HoannaEvent.LEY_STORM && cfg.stormPrismWeight() > 0 && isLand(level, pos)
                && MobSpawnRules.nearbyCount(level, ECHoannaNativesA.MANA_PRISM.get(), pos, CALAMITY_RADIUS) < cfg.stormPrismMaxNearby()
                && !inMonsterOverride(level, pos)) {
            event.addSpawnerData(stormPrism(cfg.stormPrismWeight()));
        }
    }

    /** 潮汐倍数 0.75 + 0.5T（关掉 {@code tide_weights} 时 1） */
    public static double tideFactor(ServerLevel level, SpawnValues cfg) {
        return cfg.tideWeights() ? 0.75D + 0.5D * HoannaTide.value(level) : 1D;
    }

    /** data 按 factor 缩放过的副本（同一个原条目、同一个倍数永远是同一个实例）；权重四舍五入、至少 1 */
    public static MobSpawnSettings.SpawnerData scaled(MobSpawnSettings.SpawnerData data, double factor) {
        if (SCALED.size() >= MAX_CACHED && !SCALED.containsKey(data)) {
            // 群系表里的条目是固定的几十个；别的模组每次新建条目塞进来才会涨到这里，清掉重来（清的那一刻最多让一次刷怪落空）
            SCALED.clear();
        }
        Map<Long, MobSpawnSettings.SpawnerData> byFactor = SCALED.computeIfAbsent(data, d -> new HashMap<>(8));
        return byFactor.computeIfAbsent(Double.doubleToLongBits(factor), bits -> new MobSpawnSettings.SpawnerData(data.type,
                scaledWeight(data.getWeight().asInt(), factor), data.minCount, data.maxCount));
    }

    /** 缩放以后的权重：四舍五入，至少 1（权重 0 的条目原版会报错） */
    public static int scaledWeight(int weight, double factor) {
        return (int) Math.max(1L, Math.round(weight * factor));
    }

    /** 黑暗天灾追加的巡卫条目（同一个权重永远是同一个实例） */
    public static MobSpawnSettings.SpawnerData darknessPatroller(int weight) {
        MobSpawnSettings.SpawnerData data = darknessPatroller;
        if (data == null || data.getWeight().asInt() != weight) {
            data = new MobSpawnSettings.SpawnerData(ECHoannaNativesC.INQUISITORIUM_PATROLLER.get(), weight, PATROLLER_MIN_GROUP, PATROLLER_MAX_GROUP);
            darknessPatroller = data;
        }
        return data;
    }

    /** 地脉风暴追加的棱晶条目（同一个权重永远是同一个实例） */
    public static MobSpawnSettings.SpawnerData stormPrism(int weight) {
        MobSpawnSettings.SpawnerData data = stormPrism;
        if (data == null || data.getWeight().asInt() != weight) {
            data = new MobSpawnSettings.SpawnerData(ECHoannaNativesA.MANA_PRISM.get(), weight, PRISM_MIN_GROUP, PRISM_MAX_GROUP);
            stormPrism = data;
        }
        return data;
    }

    /** pos 在不在海纳尔的陆地群系（刷怪时那一格所在区块已经加载；测试覆盖优先） */
    public static boolean isLand(ServerLevel level, BlockPos pos) {
        Boolean test = testLand;
        if (test != null) {
            return test;
        }
        return level.getBiome(pos).is(ECHoanna.HOANNA_LAND);
    }

    /**
     * pos 是不是落在某个结构的怪物刷怪覆盖里（这时原版用结构的表、不用群系表）。判法照原版 {@code ChunkGenerator.getMobsAt}：
     * 结构有 MONSTER 覆盖，按覆盖的范围（拼件 / 整个外框）看 pos 在不在某个起点里。测试覆盖优先。
     */
    public static boolean inMonsterOverride(ServerLevel level, BlockPos pos) {
        Boolean test = testOverride;
        if (test != null) {
            return test;
        }
        StructureManager structures = level.structureManager();
        for (Map.Entry<Structure, LongSet> entry : structures.getAllStructuresAt(pos).entrySet()) {
            StructureSpawnOverride override = entry.getKey().spawnOverrides().get(MobCategory.MONSTER);
            if (override == null) {
                continue;
            }
            boolean piece = override.boundingBox() == StructureSpawnOverride.BoundingBoxType.PIECE;
            boolean[] inside = {false};
            structures.fillStartsForStructure(entry.getKey(), entry.getValue(), start -> {
                if (!inside[0] && (piece ? structures.structureHasPieceAt(pos, start) : start.getBoundingBox().isInside(pos))) {
                    inside[0] = true;
                }
            });
            if (inside[0]) {
                return true;
            }
        }
        return false;
    }

    /** 黑暗天灾时巡卫的附近上限按 64 格算：给巡卫的刷怪判定用（level 是海纳尔、正在黑暗天灾） */
    public static boolean darknessActive(ServerLevel level) {
        return HoannaEvents.isActive(level, HoannaEvent.DARKNESS);
    }

    // ------------------------------------------------------------------ 精英

    /** 精英几率倍数：海纳尔里有任何天灾时 ×{@code calamity_elite_multiplier}，否则 1（{@code ECMonster.addEliteChanceMultiplier} 登记的就是它） */
    public static double eliteChanceMultiplier(Mob mob) {
        if (!(mob.level() instanceof ServerLevel level) || !ECHoanna.isHoanna(level) || HoannaEvents.current(level).isNone()) {
            return 1D;
        }
        return ECHoannaNativesC.Config.spawns().calamityEliteMultiplier();
    }

    // ------------------------------------------------------------------ 测试、清理

    /** GameTest：把事件位置当成陆地（true）/ 海（false）；null 还原 */
    public static void setTestLand(@Nullable Boolean land) {
        testLand = land;
    }

    /** GameTest：把事件位置当成在（true）/ 不在（false）结构的怪物刷怪覆盖里；null 还原 */
    public static void setTestStructureOverride(@Nullable Boolean inside) {
        testOverride = inside;
    }

    /** 缓存里有几个原条目（GameTest 看缓存不会无限长） */
    public static int cachedEntries() {
        return SCALED.size();
    }

    /** 这种实体是不是海纳尔本地怪（标签） */
    public static boolean isNative(EntityType<?> type) {
        return type.is(ECTags.HOANNA_NATIVES);
    }

    @SubscribeEvent
    public static void hoannaSpawnHooksServerStopped(ServerStoppedEvent event) {
        SCALED.clear();
        darknessPatroller = null;
        stormPrism = null;
    }
}
