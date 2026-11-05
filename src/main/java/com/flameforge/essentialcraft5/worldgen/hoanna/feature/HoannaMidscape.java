package com.flameforge.essentialcraft5.worldgen.hoanna.feature;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.hoanna.Radiation;
import com.flameforge.essentialcraft5.hoanna.nest.HoannaNests;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.worldgen.hoanna.HoannaStructurePiece;
import com.flameforge.essentialcraft5.worldgen.hoanna.StructureRuin;
import com.flameforge.essentialcraft5.worldgen.hoanna.placement.CellSpread;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 三种中景观（第十三批 13d-H1b，batch13.md §6.1 表下、§7.2；HS §2.1.4）：冰封营地、混沌裂隙、辐射陨坑，各带一个巢穴核心。
 * <p>
 * <b>放在哪</b>：三种<b>共用一套</b> {@code cell_spread}（{@value #SPACING} / {@value #SEPARATION}，salt {@value #SALT}，写在
 * {@code placed_feature/hoanna_midscape.json}，和这里的常量要一致——普查测试核对），每格挑中的那个区块看<b>区块中心</b>的群系（海平面那一层的四分之一格，
 * 和普查、结构预测同一个采样点）决定放哪种：冰冻腐化之地 → 营地、混沌腐化之地 → 裂隙、恐惧之地且辐射底数 ≥ {@value #CRATER_MIN_BASE} → 陨坑，别的不放
 * （{@link #kindFor}）。所以跨种也两两至少隔 {@value #SEPARATION} + 1 个区块、任意 16 × 16 区块窗口至多 4 处。
 * 起点区块和四周 8 个区块里有 {@link #BLOCKS_MIDSCAPE} 结构（城镇；13e 加宝库、前哨）的引用就不放（{@link #blockedByStructures}）。
 * <p>
 * <b>形状确定、跨区块一致</b>：中心是起点区块中心那一列的地面，所有随机都按「世界种子 + 区块坐标」派生的种子取格子坐标哈希
 * （{@link HoannaStructurePiece#posHash}），不用生成时的顺序随机；每一格放不放只看这一格（和同一列）在这一步之前的方块。
 * 所以把同一处中景观按区块分几次放（每次只写一个区块的 box），拼起来和一次放完逐格相同（GameTest {@code midscapeCraterChunkSeamless}）。
 * 写的范围离中心 ≤ {@value #MAX_REACH} 格（3 × 3 区块里），只写调用方给的 box。
 */
public final class HoannaMidscape {

    public static final int SPACING = 14;
    public static final int SEPARATION = 7;
    public static final int SALT = 202610261;
    /** 恐惧之地里放陨坑要的辐射底数 */
    public static final int CRATER_MIN_BASE = 3;
    /** 离中心最远写到几格（水平） */
    public static final int MAX_REACH = 12;
    /** 地表大结构：区块里有它们的引用就不放中景观 / 里程碑（数据包可加） */
    public static final TagKey<Structure> BLOCKS_MIDSCAPE = TagKey.create(Registries.STRUCTURE, EssentialCraft5.id("blocks_midscape"));

    private static final long SEED_SALT = 0x510E527FADE682D1L;
    private static final long CAMP_SALT = 0x9B05688C2B3E6C1FL;
    private static final long FISSURE_SALT = 0x1F83D9ABFB41BD6BL;
    private static final long CRATER_SALT = 0x5BE0CD19137E2179L;

    /** 种类与它的巢穴类型 id（{@code data/essentialcraft5/hoanna_nests/<id>.json}） */
    public enum Kind {
        FROZEN_CAMP("frozen_camp"), CHAOS_FISSURE("chaos_fissure"), FALLOUT_CRATER("fallout_crater");

        private final ResourceLocation nestType;

        Kind(String name) {
            this.nestType = EssentialCraft5.id(name);
        }

        public ResourceLocation nestType() {
            return nestType;
        }
    }

    private HoannaMidscape() {
    }

    // ================================================================== 放哪种

    /** 区块中心的群系 + 区块坐标 → 种类；不放返回 null（纯函数，普查直接调） */
    @Nullable
    public static Kind kindFor(Holder<Biome> biome, int chunkX, int chunkZ) {
        if (biome.is(ECHoanna.FROZEN_CORRUPTION)) {
            return Kind.FROZEN_CAMP;
        }
        if (biome.is(ECHoanna.CHAOS_CORRUPTION)) {
            return Kind.CHAOS_FISSURE;
        }
        if (biome.is(ECHoanna.DREADLANDS) && Radiation.chunkBase(chunkX, chunkZ) >= CRATER_MIN_BASE) {
            return Kind.FALLOUT_CRATER;
        }
        return null;
    }

    /** 区块中心那一格的方块坐标（x、z） */
    public static int centerX(ChunkPos chunk) {
        return chunk.getMinBlockX() + 8;
    }

    public static int centerZ(ChunkPos chunk) {
        return chunk.getMinBlockZ() + 8;
    }

    /**
     * 离 from 最近的一处 kind 中景观的区块（按格子由近到远一圈圈找，最多 maxRing 圈；纯哈希 + 群系源采样，不生成区块）；没找到返回 null。
     * 不看城镇引用、地面（那些要真地形），所以是「格子挑中、群系对上」的候选——展示厅、排查用它定坐标。
     *
     * @param seaLevel 采群系的高度（生成器的海平面）
     */
    @Nullable
    public static ChunkPos nearest(long worldSeed, BiomeSource source, Climate.Sampler sampler, int seaLevel, ChunkPos from, Kind kind, int maxRing) {
        int fromCellX = Math.floorDiv(from.x, SPACING);
        int fromCellZ = Math.floorDiv(from.z, SPACING);
        int qy = QuartPos.fromBlock(seaLevel);
        ChunkPos best = null;
        long bestDistance = Long.MAX_VALUE;
        for (int ring = 0; ring <= maxRing; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    ChunkPos pick = CellSpread.pick(worldSeed, fromCellX + dx, fromCellZ + dz, SPACING, SEPARATION, SALT);
                    Holder<Biome> biome = source.getNoiseBiome(QuartPos.fromBlock(centerX(pick)), qy, QuartPos.fromBlock(centerZ(pick)), sampler);
                    if (kindFor(biome, pick.x, pick.z) != kind) {
                        continue;
                    }
                    long distance = (long) (pick.x - from.x) * (pick.x - from.x) + (long) (pick.z - from.z) * (pick.z - from.z);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pick;
                    }
                }
            }
            // 再外一圈的格子离 from 至少 (ring) × SPACING 个区块：已经找到更近的就不用再找
            if (best != null && bestDistance <= (long) ring * SPACING * ring * SPACING) {
                return best;
            }
        }
        return best;
    }

    /** 这处中景观的种子（世界种子 + 区块坐标） */
    public static long seedFor(long worldSeed, ChunkPos chunk) {
        return HoannaStructurePiece.posHash(chunk.x, 0, chunk.z, worldSeed ^ SEED_SALT);
    }

    /** 这个区块是不是中景观格子挑中的区块（里程碑用它避开） */
    public static boolean isMidscapeChunk(long worldSeed, int chunkX, int chunkZ) {
        return CellSpread.isCellChunk(worldSeed, chunkX, chunkZ, SPACING, SEPARATION, SALT);
    }

    /**
     * chunk 和它四周 radius 个区块里，有没有 {@link #BLOCKS_MIDSCAPE} 结构的引用（{@code ChunkAccess.getAllReferences()}：
     * 结构包围盒和这个区块相交就有引用，到 features 这一步时已经算好）。只读引用表，不加载、不生成区块（世界生成里四周区块就在 WorldGenRegion 里）。
     */
    public static boolean blockedByStructures(WorldGenLevel level, ChunkPos chunk, int radius) {
        Registry<Structure> structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                ChunkAccess access = level.getChunk(chunk.x + dx, chunk.z + dz);
                for (Map.Entry<Structure, LongSet> entry : access.getAllReferences().entrySet()) {
                    if (!entry.getValue().isEmpty() && structures.wrapAsHolder(entry.getKey()).is(BLOCKS_MIDSCAPE)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 按种类放；center 是中心那一列的地面（最上面一格实心方块）。seaLevel 是生成器的海平面（陨坑不挖到它下面；
     * 不用 {@code level.getSeaLevel()}：完整世界里那是写死的 63，和生成器的不一定一样）。
     */
    public static boolean place(Kind kind, WorldGenLevel level, BoundingBox box, BlockPos center, long seed, int seaLevel) {
        return switch (kind) {
            case FROZEN_CAMP -> placeCamp(level, box, center, seed);
            case CHAOS_FISSURE -> placeFissure(level, box, center, seed);
            case FALLOUT_CRATER -> placeCrater(level, box, center, seed, seaLevel);
        };
    }

    // ================================================================== 冰封营地

    /** 营地中间雪地的半径（再加 0~1 格的抖动） */
    public static final float CAMP_SNOW_RADIUS = 5.5F;
    /** 冰刺圈的半径 */
    public static final int CAMP_RING_RADIUS = 8;

    /**
     * 冰封营地（HS §2.1.4，半径 ≤ 10）：中间一片雪地（地表换成雪块）、外面一圈 7~9 根浮冰刺（3~6 格，三根里有一根 6~8 格，大约一半嵌一块霜髓），
     * 正中间的地面上放巢穴核心 {@code frozen_camp}。
     */
    public static boolean placeCamp(WorldGenLevel level, BoundingBox box, BlockPos center, long seed) {
        long salted = seed ^ CAMP_SALT;
        int cx = center.getX();
        int cy = center.getY();
        int cz = center.getZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int reach = (int) Math.ceil(CAMP_SNOW_RADIUS + 1F);
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                float limit = CAMP_SNOW_RADIUS + HoannaStructurePiece.unit(HoannaStructurePiece.posHash(x, 0, z, salted));
                if (dx * dx + dz * dz > limit * limit) {
                    continue;
                }
                int ground = FeatureSupport.groundY(level, x, z, cy + 6, cy - 6);
                if (ground == Integer.MIN_VALUE) {
                    continue;
                }
                pos.set(x, ground, z);
                if (box.isInside(pos) && LeyVentFeature.isSurfaceSoil(level.getBlockState(pos))) {
                    FeatureSupport.set(level, box, pos.immutable(), Blocks.SNOW_BLOCK.defaultBlockState());
                    FeatureSupport.clearAbove(level, box, pos.immutable());
                }
            }
        }
        int spikes = 7 + FeatureSupport.intOf(HoannaStructurePiece.posHash(cx, 1, cz, salted), 3);
        float turn = HoannaStructurePiece.unit(HoannaStructurePiece.posHash(cx, 2, cz, salted)) * (float) (Math.PI * 2);
        for (int i = 0; i < spikes; i++) {
            long hash = HoannaStructurePiece.posHash(cx, 10 + i, cz, salted);
            float angle = turn + (float) (Math.PI * 2 * i / spikes) + (HoannaStructurePiece.unit(hash) - 0.5F) * 0.4F;
            int x = cx + Math.round(Mth.cos(angle) * CAMP_RING_RADIUS);
            int z = cz + Math.round(Mth.sin(angle) * CAMP_RING_RADIUS);
            // 找地面时跳过冰刺自己（分块放时别的区块可能已经把这根刺立起来了）
            int ground = FeatureSupport.groundY(level, x, z, cy + MarrowSpikeFeature.MAX_HEIGHT + 2, cy - 6, MarrowSpikeFeature::isSpikeBlock);
            if (ground == Integer.MIN_VALUE) {
                continue;
            }
            int height = i % 3 == 0 ? 6 + FeatureSupport.intOf(hash ^ 1L, 3) : 3 + FeatureSupport.intOf(hash ^ 2L, 4);
            List<BlockPos> cells = new ArrayList<>();
            MarrowSpikeFeature.placeSpike(level, box, new BlockPos(x, ground + 1, z), height, cells);
            if (HoannaStructurePiece.unit(hash ^ 3L) < 0.5F && height >= 3) {
                // 刺身第 1~(高 − 2) 格里挑一格嵌霜髓（不放刺尖、不放刺根）
                int dy = 1 + FeatureSupport.intOf(hash ^ 4L, height - 2);
                BlockPos marrow = new BlockPos(x, ground + 1 + dy, z);
                if (level.getBlockState(marrow).is(Blocks.PACKED_ICE)) {
                    FeatureSupport.set(level, box, marrow, HoannaFeatureBlocks.frostMarrow());
                }
            }
        }
        return placeNestCore(level, box, cx, cy, cz, Kind.FROZEN_CAMP);
    }

    // ================================================================== 混沌裂隙

    public static final int FISSURE_LENGTH = 18;
    public static final int FISSURE_HALF_WIDTH = 1;
    public static final int FISSURE_DEPTH = 6;

    /**
     * 混沌裂隙（HS §2.1.4）：一条 3 × {@value #FISSURE_LENGTH} 的地裂（沿 x 或 z，按种子），中段深 {@value #FISSURE_DEPTH}、两头 2 → 4 收窄，
     * 每一列挖空后底下那格换成岩浆块（<b>不放液体</b>）；两侧（离中线 2 格）摆 4~6 个满充能（{@code charge = 3}）的地脉喷口；
     * 中段旁边（离中线 3 格）的地面上放巢穴核心 {@code chaos_fissure}。
     * 地裂挖到的范围和四周一格里有任何液体就整处不放（不挖出口子让水 / 岩浆流进来）；挖不动的（硬度 &lt; 0、带方块实体的）那一格不挖。
     */
    public static boolean placeFissure(WorldGenLevel level, BoundingBox box, BlockPos center, long seed) {
        long salted = seed ^ FISSURE_SALT;
        int cx = center.getX();
        int cy = center.getY();
        int cz = center.getZ();
        boolean alongX = HoannaStructurePiece.unit(HoannaStructurePiece.posHash(cx, 1, cz, salted)) < 0.5F;
        int half = FISSURE_LENGTH / 2;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // 先查液体：地裂范围外扩一格、从最深处下一格查到 cy + 5——每列找地面从 cy + 4 往下找、从地面往下挖，
        // 最高的一列挖到 cy + 4，它头顶那格、旁边列的同一高度都要查到
        for (int t = -half - 1; t <= half; t++) {
            for (int w = -FISSURE_HALF_WIDTH - 1; w <= FISSURE_HALF_WIDTH + 1; w++) {
                for (int y = cy - FISSURE_DEPTH - 3; y <= cy + 5; y++) {
                    fissurePos(pos, cx, cz, alongX, t, w).setY(y);
                    if (!level.getFluidState(pos).isEmpty()) {
                        return false;
                    }
                }
            }
        }
        for (int t = -half; t < half; t++) {
            int index = t + half;
            int fromEnd = Math.min(index, FISSURE_LENGTH - 1 - index);
            int depth = fromEnd == 0 ? 2 : fromEnd == 1 ? 4 : FISSURE_DEPTH;
            for (int w = -FISSURE_HALF_WIDTH; w <= FISSURE_HALF_WIDTH; w++) {
                fissurePos(pos, cx, cz, alongX, t, w);
                int ground = FeatureSupport.groundY(level, pos.getX(), pos.getZ(), cy + 4, cy - 4);
                if (ground == Integer.MIN_VALUE) {
                    continue;
                }
                int x = pos.getX();
                int z = pos.getZ();
                if (!box.isInside(new BlockPos(x, ground, z))) {
                    continue;
                }
                FeatureSupport.clearAbove(level, box, new BlockPos(x, ground, z));
                for (int y = ground; y > ground - depth; y--) {
                    pos.set(x, y, z);
                    if (carvable(level, pos)) {
                        FeatureSupport.set(level, box, pos.immutable(), Blocks.AIR.defaultBlockState());
                    }
                }
                pos.set(x, ground - depth, z);
                if (carvable(level, pos)) {
                    FeatureSupport.set(level, box, pos.immutable(), Blocks.MAGMA_BLOCK.defaultBlockState());
                }
            }
        }
        // 两侧的喷口：从 8 个槽位里按哈希挑 4~6 个，左右按哈希
        int[] slots = {-7, -5, -3, -1, 1, 3, 5, 7};
        int vents = 4 + FeatureSupport.intOf(HoannaStructurePiece.posHash(cx, 2, cz, salted), 3);
        for (int i = slots.length - 1; i > 0; i--) {
            int j = FeatureSupport.intOf(HoannaStructurePiece.posHash(cx, 20 + i, cz, salted), i + 1);
            int swap = slots[i];
            slots[i] = slots[j];
            slots[j] = swap;
        }
        for (int i = 0; i < vents; i++) {
            int side = HoannaStructurePiece.unit(HoannaStructurePiece.posHash(cx, 40 + i, cz, salted)) < 0.5F ? -2 : 2;
            fissurePos(pos, cx, cz, alongX, slots[i], side);
            int ground = FeatureSupport.groundY(level, pos.getX(), pos.getZ(), cy + 4, cy - 4);
            if (ground != Integer.MIN_VALUE) {
                LeyVentFeature.placeVent(level, box, new BlockPos(pos.getX(), ground, pos.getZ()), 3, salted);
            }
        }
        int coreSide = HoannaStructurePiece.unit(HoannaStructurePiece.posHash(cx, 3, cz, salted)) < 0.5F ? -3 : 3;
        fissurePos(pos, cx, cz, alongX, 0, coreSide);
        int ground = FeatureSupport.groundY(level, pos.getX(), pos.getZ(), cy + 4, cy - 4);
        return ground != Integer.MIN_VALUE && placeNestCore(level, box, pos.getX(), ground, pos.getZ(), Kind.CHAOS_FISSURE);
    }

    /** 沿地裂的坐标 (t 沿长, w 横向) → 世界 x、z */
    private static BlockPos.MutableBlockPos fissurePos(BlockPos.MutableBlockPos pos, int cx, int cz, boolean alongX, int t, int w) {
        return alongX ? pos.set(cx + t, pos.getY(), cz + w) : pos.set(cx + w, pos.getY(), cz + t);
    }

    /** 能挖：不是空气、不含液体、硬度 ≥ 0、没有方块实体 */
    private static boolean carvable(WorldGenLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && state.getFluidState().isEmpty() && state.getDestroySpeed(level, pos) >= 0F && !state.hasBlockEntity();
    }

    // ================================================================== 辐射陨坑

    /** 陨坑的球半径范围 [MIN, MIN + SPAN)：泥土类有效半径 ×0.6，地表开口约 8~11 格；最远写到 {@code StructureRuin.craterReach} ≤ 12 */
    public static final float CRATER_RADIUS_MIN = 14.5F;
    public static final float CRATER_RADIUS_SPAN = 1.4F;
    /** 坑最深挖到地面下几格（坑底以下不挖，浅坑） */
    public static final int CRATER_DEPTH = 3;

    /**
     * 辐射陨坑（HS §2.1.4）：一个浅坑——球心在地面上一格，半径 {@value #CRATER_RADIUS_MIN}~{@code +}{@value #CRATER_RADIUS_SPAN}，
     * 走 {@link StructureRuin#carveCrater}（按方块硬度挖、跨区块一致、挨着液体不挖、海平面下不挖），地面下 {@value #CRATER_DEPTH} 格以下不挖；
     * 坑底（离中心 ≈ 7 格以内）的地表换成焦土（粗泥 / 灵魂土 2 : 1），撒 3~5 个辐晶簇（不看区块底数），正中间放巢穴核心 {@code fallout_crater}。
     */
    public static boolean placeCrater(WorldGenLevel level, BoundingBox box, BlockPos center, long seed, int seaLevel) {
        long salted = seed ^ CRATER_SALT;
        int cx = center.getX();
        int cy = center.getY();
        int cz = center.getZ();
        float radius = craterRadius(seed, cx, cz);
        int floor = Math.max(seaLevel, cy - CRATER_DEPTH);
        StructureRuin.carveCrater(level, box, new BlockPos(cx, cy + 1, cz), radius, salted, floor, StructureRuin::hardnessFactor);
        float scorch = radius * StructureRuin.K_SOFT * StructureRuin.JITTER_MIN - 0.5F;
        int reach = (int) Math.ceil(scorch);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                if (dx * dx + dz * dz > scorch * scorch) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                int ground = FeatureSupport.groundY(level, x, z, cy + 3, floor - 2);
                if (ground == Integer.MIN_VALUE) {
                    continue;
                }
                pos.set(x, ground, z);
                if (box.isInside(pos) && LeyVentFeature.isSurfaceSoil(level.getBlockState(pos))) {
                    boolean coarse = FeatureSupport.intOf(HoannaStructurePiece.posHash(x, ground, z, salted ^ 1L), 3) < 2;
                    FeatureSupport.set(level, box, pos.immutable(), (coarse ? Blocks.COARSE_DIRT : Blocks.SOUL_SOIL).defaultBlockState());
                    FeatureSupport.clearAbove(level, box, pos.immutable());
                }
            }
        }
        int clusters = 3 + FeatureSupport.intOf(HoannaStructurePiece.posHash(cx, 1, cz, salted), 3);
        float turn = HoannaStructurePiece.unit(HoannaStructurePiece.posHash(cx, 2, cz, salted)) * (float) (Math.PI * 2);
        for (int i = 0; i < clusters; i++) {
            // 均分方向再抖一点：几个簇不会落在同一格
            long hash = HoannaStructurePiece.posHash(cx, 10 + i, cz, salted);
            float angle = turn + (float) (Math.PI * 2 * i / clusters) + (HoannaStructurePiece.unit(hash) - 0.5F) * 0.5F;
            float distance = 2F + 4F * HoannaStructurePiece.unit(hash ^ 1L);
            int x = cx + Math.round(Mth.cos(angle) * distance);
            int z = cz + Math.round(Mth.sin(angle) * distance);
            int ground = FeatureSupport.groundY(level, x, z, cy + 3, floor - 2);
            if (ground == Integer.MIN_VALUE) {
                continue;
            }
            BlockPos at = new BlockPos(x, ground + 1, z);
            BlockState cluster = HoannaFeatureBlocks.radiantCluster(Direction.UP);
            if (!cluster.isAir() && level.getBlockState(at).isAir() && cluster.canSurvive(level, at)) {
                FeatureSupport.set(level, box, at, cluster);
            }
        }
        int ground = FeatureSupport.groundY(level, cx, cz, cy + 3, floor - 2);
        return ground != Integer.MIN_VALUE && placeNestCore(level, box, cx, ground, cz, Kind.FALLOUT_CRATER);
    }

    /** 这处陨坑的球半径（按种子） */
    public static float craterRadius(long seed, int cx, int cz) {
        return CRATER_RADIUS_MIN + CRATER_RADIUS_SPAN * HoannaStructurePiece.unit(HoannaStructurePiece.posHash(cx, 0, cz, seed ^ CRATER_SALT));
    }

    // ================================================================== 巢穴核心

    /** 在 (x, ground, z) 这格地面上面放核心（那一格要能占）；box 外的不放（由覆盖它的那次放） */
    private static boolean placeNestCore(WorldGenLevel level, BoundingBox box, int x, int ground, int z, Kind kind) {
        BlockPos at = new BlockPos(x, ground + 1, z);
        if (!box.isInside(at)) {
            return true;
        }
        if (!FeatureSupport.isClearable(level.getBlockState(at))) {
            return false;
        }
        return HoannaNests.placeCore(level, at, kind.nestType());
    }
}
