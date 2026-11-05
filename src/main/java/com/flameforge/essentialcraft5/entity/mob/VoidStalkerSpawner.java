package com.flameforge.essentialcraft5.entity.mob;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.registry.ECNetherEndMobs;
import com.flameforge.essentialcraft5.registry.ECNetherEndMobs.Config.StalkerValues;
import com.flameforge.essentialcraft5.util.ECLevels;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 虚空潜行者的独立生成器（第十三批 13c-M3，batch13.md §5.2、§5.1 挤占表、R-9；照原版幻翼、巡逻队的写法按玩家判）。
 * 潜行者<b>不进任何群系的刷怪表</b>：末地外岛的表里只有末影人（权重 10），潜行者进表的话权重 1 就占约 9% 的刷怪尝试；
 * 走这里就一次都不占末影人的尝试，末影人农场照旧。
 * <p>
 * 规则：末地每隔 {@code spawn_interval} 刻（600）给每名<b>在外岛上</b>（末地维度、脚下群系不是主岛 {@code minecraft:the_end}）的
 * 生存 / 冒险玩家掷一次 {@code spawn_chance}（25%）；掷中就在离他 {@code spawn_min_distance}~{@code spawn_max_distance} 格（24~64）的地方
 * 找露天的末地石地表（那一列最高的方块就是末地石、它上面就是落脚处，也在外岛上），最多试 {@value #SPOT_TRIES} 次。
 * 下面几种情况不刷：和平难度；本维度怪物数已到原版上限（{@code NaturalSpawner} 上一刻算的那份，70 × 可刷区块数 / 289）；
 * 落脚处 {@value #NEARBY_RADIUS} 格内已有 {@code spawn_max_nearby}（3）只；离任何玩家不到 {@code spawn_min_distance} 格；区块没加载、不跑实体。
 * 「游戏规则 doMobSpawning」关掉、服务器关了刷怪物（{@code spawn-monsters}）时整个生成器不跑。
 * <p>
 * 刷的过程和原版自然生成同一套事件：先过刷怪位置判定（{@code SpawnPlacements}，潜行者的是 {@link VoidStalkerEntity#checkStalkerSpawnRules}）、
 * Forge 的 {@code PositionCheck}，再走 {@code ForgeEventFactory.onFinalizeSpawn}（来源记「自然」，公平、会掷精英），
 * 被别的模组 / 插件取消的不进世界、不留任何东西。生成类型是 {@code NATURAL}。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class VoidStalkerSpawner {

    /** 一次掷中最多找几个点 */
    public static final int SPOT_TRIES = 8;
    /** 「附近已有几只」数多大范围 */
    public static final int NEARBY_RADIUS = 64;
    /** 原版 {@code NaturalSpawner.MAGIC_NUMBER}（17 × 17，包级可见，这里照抄） */
    public static final int MOB_CAP_CHUNKS = 289;

    /** 一次尝试的结果（测试看这个） */
    public enum Outcome {
        SPAWNED,
        /** 配置关掉了（几率 0 或上限 0） */
        OFF,
        /** 这名玩家不在外岛上，或者不是生存 / 冒险 */
        NOT_ELIGIBLE,
        PEACEFUL,
        /** 没掷中 */
        NO_ROLL,
        /** 本维度的怪已到原版上限 */
        MONSTER_CAP,
        /** 没找到合格的落脚处 */
        NO_SPOT,
        /** 落脚处附近已经够多了 */
        CROWDED,
        /** 刷怪判定没过、或者被别的模组 / 插件取消 */
        BLOCKED
    }

    private static final Map<ResourceKey<Level>, Integer> COUNTDOWN = new HashMap<>();

    @Nullable
    private static ServerLevel testLevel;
    @Nullable
    private static Predicate<BlockPos> testOuter;
    @Nullable
    private static Boolean testMonsterCap;
    @Nullable
    private static Difficulty testDifficulty;

    private VoidStalkerSpawner() {
    }

    @SubscribeEvent
    public static void onVoidStalkerLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.dimension() != Level.END) {
            return;
        }
        StalkerValues cfg = ECNetherEndMobs.Config.stalker();
        if (cfg.spawnChance() <= 0D || cfg.spawnMaxNearby() <= 0) {
            return;
        }
        int left = COUNTDOWN.getOrDefault(level.dimension(), -1);
        if (left < 0) {
            // 第一次：随机错开，免得每次开服都在同一刻掷
            left = 1 + level.random.nextInt(Math.max(1, cfg.spawnInterval()));
        }
        if (--left > 0) {
            COUNTDOWN.put(level.dimension(), left);
            return;
        }
        COUNTDOWN.put(level.dimension(), cfg.spawnInterval());
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING) || !level.getServer().isSpawningMonsters()) {
            return;
        }
        for (ServerPlayer player : List.copyOf(level.players())) {
            trySpawnFor(player, level.random, true);
        }
    }

    @SubscribeEvent
    public static void onVoidStalkerServerStopped(ServerStoppedEvent event) {
        COUNTDOWN.clear();
    }

    /**
     * 给一名玩家试一次（见类注释的顺序）。roll 为假时不掷几率（测试、指令用），其余条件照查。
     */
    public static Outcome trySpawnFor(ServerPlayer player, RandomSource random, boolean roll) {
        StalkerValues cfg = ECNetherEndMobs.Config.stalker();
        if (cfg.spawnChance() <= 0D || cfg.spawnMaxNearby() <= 0) {
            return Outcome.OFF;
        }
        if (!(player.level() instanceof ServerLevel level) || !eligible(player)) {
            return Outcome.NOT_ELIGIBLE;
        }
        if (difficulty(level) == Difficulty.PEACEFUL) {
            return Outcome.PEACEFUL;
        }
        if (roll && random.nextDouble() >= cfg.spawnChance()) {
            return Outcome.NO_ROLL;
        }
        if (monsterCapReached(level)) {
            return Outcome.MONSTER_CAP;
        }
        BlockPos spot = findSpot(level, player.blockPosition(), random, cfg);
        if (spot == null) {
            return Outcome.NO_SPOT;
        }
        if (MobSpawnRules.countExact(level, ECNetherEndMobs.VOID_STALKER.get(), spot, NEARBY_RADIUS) >= cfg.spawnMaxNearby()) {
            return Outcome.CROWDED;
        }
        return spawnAt(level, spot, random) != null ? Outcome.SPAWNED : Outcome.BLOCKED;
    }

    /** 这名玩家算不算「外岛上的生存 / 冒险玩家」 */
    public static boolean eligible(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && !player.isCreative() && !(player instanceof FakePlayer)
                && player.level() instanceof ServerLevel level && isOuterEnd(level, player.blockPosition());
    }

    /** 这里是不是末地外岛：末地维度、群系不是主岛 */
    public static boolean isOuterEnd(ServerLevel level, BlockPos pos) {
        if (level == testLevel && testOuter != null) {
            return testOuter.test(pos);
        }
        return level.dimension() == Level.END && !level.getBiome(pos).is(Biomes.THE_END);
    }

    /** 本维度的怪物数到没到原版上限（读上一刻自然生成算好的那份；还没算过时当没到） */
    public static boolean monsterCapReached(ServerLevel level) {
        Boolean override = level == testLevel ? testMonsterCap : null;
        if (override != null) {
            return override;
        }
        NaturalSpawner.SpawnState state = level.getChunkSource().getLastSpawnState();
        return state != null && capReached(state.getMobCategoryCounts().getInt(MobCategory.MONSTER), state.getSpawnableChunkCount());
    }

    /** 原版每维度怪物上限：70 × 可刷区块数 / 289（{@code NaturalSpawner.SpawnState.canSpawnForCategory}） */
    public static boolean capReached(int monsters, int spawnableChunks) {
        return monsters >= MobCategory.MONSTER.getMaxInstancesPerChunk() * spawnableChunks / MOB_CAP_CHUNKS;
    }

    /** 离 center 水平 min~max 格的露天末地石地表（落脚格），找不到返回 null */
    @Nullable
    public static BlockPos findSpot(ServerLevel level, BlockPos center, RandomSource random, StalkerValues cfg) {
        int near = Math.min(cfg.spawnMin(), cfg.spawnMax());
        int far = Math.max(cfg.spawnMin(), cfg.spawnMax());
        for (int i = 0; i < SPOT_TRIES; i++) {
            double angle = random.nextDouble() * Math.PI * 2D;
            double dist = near + random.nextDouble() * (far - near);
            int x = (int) Math.floor(center.getX() + 0.5D + Math.cos(angle) * dist);
            int z = (int) Math.floor(center.getZ() + 0.5D + Math.sin(angle) * dist);
            BlockPos column = new BlockPos(x, center.getY(), z);
            if (!level.isInWorldBounds(column) || !ECLevels.isFullyLoaded(level, column)) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            if (y <= level.getMinBuildHeight()) {
                continue;
            }
            BlockPos feet = new BlockPos(x, y, z);
            if (!level.getBlockState(feet.below()).is(Blocks.END_STONE) || !level.isInWorldBounds(feet)
                    || !level.getWorldBorder().isWithinBounds(feet) || !isOuterEnd(level, feet) || !level.isPositionEntityTicking(feet)) {
                continue;
            }
            if (level.getNearestPlayer(x + 0.5D, y, z + 0.5D, near, EntitySelector.NO_SPECTATORS) != null) {
                continue;
            }
            return feet;
        }
        return null;
    }

    /** 在 feet 刷一只（见类注释的事件顺序）；没刷成返回 null */
    @Nullable
    public static VoidStalkerEntity spawnAt(ServerLevel level, BlockPos feet, RandomSource random) {
        if (!SpawnPlacements.checkSpawnRules(ECNetherEndMobs.VOID_STALKER.get(), level, MobSpawnType.NATURAL, feet, random)) {
            return null;
        }
        VoidStalkerEntity stalker = ECNetherEndMobs.VOID_STALKER.get().create(level);
        if (stalker == null) {
            return null;
        }
        stalker.moveTo(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D, random.nextFloat() * 360F, 0F);
        if (!ForgeEventFactory.checkSpawnPosition(stalker, level, MobSpawnType.NATURAL)) {
            return null;
        }
        ForgeEventFactory.onFinalizeSpawn(stalker, level, level.getCurrentDifficultyAt(feet), MobSpawnType.NATURAL, null, null);
        if (stalker.isSpawnCancelled() || !level.addFreshEntity(stalker)) {
            return null;
        }
        return stalker;
    }

    private static Difficulty difficulty(ServerLevel level) {
        Difficulty override = level == testLevel ? testDifficulty : null;
        return override != null ? override : level.getDifficulty();
    }

    /**
     * 只给 GameTest：把 level 当成末地，outer 判哪些格子算外岛；monsterCap / difficulty 非 null 时代替真的怪物上限、难度。
     * 在同一段同步代码里设、测、置回（全传 null）；真的末地照常按维度判。
     */
    public static void setTestOverrides(@Nullable ServerLevel level, @Nullable Predicate<BlockPos> outer, @Nullable Boolean monsterCap,
                                        @Nullable Difficulty difficulty) {
        testLevel = level;
        testOuter = outer;
        testMonsterCap = monsterCap;
        testDifficulty = difficulty;
    }
}
