package com.flameforge.essentialcraft5.worldgen.hoanna.sunken;

import com.flameforge.essentialcraft5.worldgen.hoanna.HoannaStructurePiece;
import com.flameforge.essentialcraft5.worldgen.hoanna.StructureForecast;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntBinaryOperator;

/**
 * 沉没城区 {@code essentialcraft5:sunken_district}（第十三批 13e-S2，batch13.md §6.4；HS §2.4.2）：海底一座空气穹顶 + 3~5 栋沉房 + 1~2 段倒塌的塔身，
 * 整座在中心 {@value #MAX_RADIUS} 格以内；穹顶中央的沉城祭坛召渊潮领主，穹顶里 2 个箱子、两侧各 1 个巢穴核心（沉城卫兵），沉房 50% 各 1 个箱子。
 * <p>
 * <b>放置</b>（数据包 {@code worldgen/structure_set/sunken_district.json}）：{@code spread_excluding} 40 / 20、salt 202610266，排城镇、宝库、前哨各 8 区块
 * （沉城只按起点判群系，海岸边的沉房、塔身会落到陆地上，城镇的件又离中心 60 多格，不排的话约 2~3% 的沉城会和它们的件叠在一起；
 * 群系不对的对方候选不挡，见 {@code SpreadExcludingPlacement}）；群系 {@code #essentialcraft5:has_structure/sunken_district}（海纳尔之海）；
 * {@code terrain_adaptation none}、不写 spawn_overrides。只挂海纳尔的群系标签，主世界、下界、末地连判定都不做（{@code ChunkGeneratorStructureState.createForNormal} 按群系源过滤结构集）。
 * <p>
 * <b>{@link #findGenerationPoint}</b>：先判群系（起点区块最小角 +4、海平面高度一次采样，和结构预测同一点；海纳尔七个群系深度参数相同），再布局（{@link #layout}，纯计算、只出包围盒），
 * 再定高。<b>定高一共 ≤ 6 列，别再加</b>：穹顶中心 1 列（判定时取，生成时复用）+ 每栋沉房中心 1 列（≤ 5，在拼件回调里取）；
 * 塔身不另取，用离它最近的那一件（穹顶或沉房）的地面。一列 = 一次 {@code getFirstFreeHeight(OCEAN_FLOOR_WG)}（水底算地面，约 3 ms），
 * 一座约 18 ms，{@code /locate} 每座约 20 ms。除群系外不拒绝：水浅时穹顶顶部露出水面（半沉的穹顶），水深多少都照放。
 * <p>
 * <b>布局</b>：穹顶在中心；沉房、塔身按随机顺序排一圈（等分角度 + 小幅抖动），离中心的距离从一个随机起点往外加，直到和已经放下的件（含穹顶）
 * 隔开 {@value #GAP} 格、又不出 {@value #MAX_RADIUS} 格；塔身顺着离中心的方向横躺（放不下再换个朝向试一次）。还放不下的件就不放
 * （GameTest {@code SunkenDistrictTests.sunkenLayoutSeeds} 守着 2 000 个种子都是 3~5 栋沉房 + 1~2 段塔身）。
 * 各件的种子从布局种子派生（{@link #pieceSeed}），同一座结构每次布局都一样。
 */
public class SunkenDistrictStructure extends Structure {

    public static final Codec<SunkenDistrictStructure> CODEC = simpleCodec(SunkenDistrictStructure::new);

    /** 整座在中心这么多格以内（各件包围盒的水平边，按切比雪夫距离） */
    public static final int MAX_RADIUS = 40;
    /** 件与件之间至少空几格 */
    public static final int GAP = 2;
    public static final int MIN_HOUSES = 3;
    public static final int MAX_HOUSES = 5;
    public static final int MIN_TOWERS = 1;
    public static final int MAX_TOWERS = 2;
    /** 沉房的半径（边长 2r + 1）范围 */
    public static final int MIN_HOUSE_RADIUS = 3;
    public static final int MAX_HOUSE_RADIUS = 4;
    /** 沉房的层数 F（实际盖 F + 1 层，同城镇房子） */
    public static final int MIN_FLOORS = 1;
    public static final int MAX_FLOORS = 2;
    /** 塔身长度范围 */
    public static final int MIN_TOWER_LENGTH = 10;
    public static final int MAX_TOWER_LENGTH = 16;
    /** 沉房离中心的起始距离范围、塔身的起始距离范围、往外推的上限 */
    public static final int HOUSE_DISTANCE_MIN = 16;
    public static final int HOUSE_DISTANCE_SPAN = 8;
    public static final int TOWER_DISTANCE_MIN = 20;
    public static final int TOWER_DISTANCE_SPAN = 6;
    public static final int MAX_DISTANCE = 38;
    /** 一圈里每件的角度抖动（占等分角的比例，正负各一半） */
    public static final double JITTER = 0.3D;
    /** 定高最多几列（穹顶 1 + 沉房 ≤ 5） */
    public static final int MAX_COLUMNS = 1 + MAX_HOUSES;

    private static final long PIECE_SEED_STEP = 0x9E3779B97F4A7C15L;

    public SunkenDistrictStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    public StructureType<?> type() {
        return SunkenStructures.SUNKEN_DISTRICT.get();
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!biomeAllows(context)) {
            return Optional.empty();
        }
        ChunkPos chunk = context.chunkPos();
        Layout layout = layout(context.random(), chunk.getMiddleBlockX(), chunk.getMiddleBlockZ());
        int ground = columnGround(context, layout.centerX(), layout.centerZ());
        // 生成桩放在群系采样点上：原版还会按桩的位置再判一次群系，和 biomeAllows、结构预测对的是同一点
        return Optional.of(new GenerationStub(new BlockPos(StructureForecast.sampleX(chunk), ground, StructureForecast.sampleZ(chunk)),
                builder -> createPieces(layout, ground, (x, z) -> columnGround(context, x, z)).forEach(builder::addPiece)));
    }

    /**
     * 群系：起点区块最小角 +{@value StructureForecast#SAMPLE_OFFSET}、海平面那一点（和 {@link StructureForecast} 采样的同一点）在不在这座结构的群系标签里。
     * 一次群系采样，不算地面。别改回区块中心：预测（勘测图、普查）按最小角 +4 采样，两点群系不同时预测会对不上实际生成
     */
    public static boolean biomeAllows(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        int y = context.chunkGenerator().getSeaLevel();
        Holder<Biome> biome = context.chunkGenerator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(StructureForecast.sampleX(chunk)), QuartPos.fromBlock(y),
                QuartPos.fromBlock(StructureForecast.sampleZ(chunk)), context.randomState().sampler());
        return context.validBiome().test(biome);
    }

    /** 一列按噪声算的水底（第一格不是实心方块的位置，水算空）。每次调用算一列噪声（约 3 ms），只在生成阶段调 */
    public static int columnGround(GenerationContext context, int x, int z) {
        return HoannaStructurePiece.groundYAt(context, x, z, Heightmap.Types.OCEAN_FLOOR_WG);
    }

    // ================================================================== 布局（纯计算）

    public enum Kind {
        DOME,
        HOUSE,
        TOWER
    }

    /**
     * 布局里的一件（只有水平位置；高度在 {@link #createPieces} 里定）。
     *
     * @param x      中心 x（塔身是长边的中点）
     * @param z      中心 z
     * @param radius 沉房的半径（边长 2r + 1）；别的件是 0
     * @param floors 沉房的层数 F；别的件是 0
     * @param length 塔身的长度；别的件是 0
     * @param alongX 塔身沿 x 轴横躺（否则沿 z）
     * @param chest  沉房里放不放箱子（50%）
     */
    public record Planned(Kind kind, int x, int z, int radius, int floors, int length, boolean alongX, boolean chest) {

        /** 水平范围（y 随便给，只比 x / z） */
        public BoundingBox footprint() {
            return switch (kind) {
                case DOME -> new BoundingBox(x - SunkenPieces.DOME_RADIUS, 0, z - SunkenPieces.DOME_RADIUS,
                        x + SunkenPieces.DOME_RADIUS, 0, z + SunkenPieces.DOME_RADIUS);
                case HOUSE -> new BoundingBox(x - radius, 0, z - radius, x + radius, 0, z + radius);
                case TOWER -> {
                    int start = -length / 2;
                    int half = SunkenPieces.TOWER_HALF_WIDTH;
                    yield alongX ? new BoundingBox(x + start, 0, z - half, x + start + length - 1, 0, z + half)
                            : new BoundingBox(x - half, 0, z + start, x + half, 0, z + start + length - 1);
                }
            };
        }

        /** 同一个水平范围、换成 [minY, maxY] */
        public BoundingBox box(int minY, int maxY) {
            BoundingBox f = footprint();
            return new BoundingBox(f.minX(), minY, f.minZ(), f.maxX(), maxY, f.maxZ());
        }
    }

    /**
     * 一座沉没城区的布局。
     *
     * @param seed   布局种子（结构生成随机的第一个 long），各件的种子从它派生
     * @param pieces 第一件是穹顶，后面是沉房、塔身（按一圈里的先后）
     */
    public record Layout(int centerX, int centerZ, long seed, List<Planned> pieces) {

        public int count(Kind kind) {
            int n = 0;
            for (Planned piece : pieces) {
                if (piece.kind() == kind) {
                    n++;
                }
            }
            return n;
        }

        /** 各件水平范围里离中心最远的那条边（切比雪夫距离） */
        public int radius() {
            int max = 0;
            for (Planned piece : pieces) {
                BoundingBox f = piece.footprint();
                max = Math.max(max, Math.max(Math.max(Math.abs(f.minX() - centerX), Math.abs(f.maxX() - centerX)),
                        Math.max(Math.abs(f.minZ() - centerZ), Math.abs(f.maxZ() - centerZ))));
            }
            return max;
        }
    }

    /**
     * 按 random 布局一座沉没城区（见类注释），中心 (centerX, centerZ)。{@link #findGenerationPoint} 和 GameTest 共用：
     * 同一个区块、同一个世界种子算出来一样。随机的取法和先后是存档格式的一部分（改了老世界里还没生成的那部分会对不上），上线后别动。
     */
    public static Layout layout(RandomSource random, int centerX, int centerZ) {
        long seed = random.nextLong();
        int houses = MIN_HOUSES + random.nextInt(MAX_HOUSES - MIN_HOUSES + 1);
        int towers = MIN_TOWERS + random.nextInt(MAX_TOWERS - MIN_TOWERS + 1);
        List<Kind> ring = new ArrayList<>(houses + towers);
        for (int i = 0; i < houses; i++) {
            ring.add(Kind.HOUSE);
        }
        for (int i = 0; i < towers; i++) {
            ring.add(Kind.TOWER);
        }
        for (int i = ring.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Kind tmp = ring.get(i);
            ring.set(i, ring.get(j));
            ring.set(j, tmp);
        }
        List<Planned> pieces = new ArrayList<>(ring.size() + 1);
        pieces.add(new Planned(Kind.DOME, centerX, centerZ, 0, 0, 0, false, false));
        double step = Math.PI * 2D / ring.size();
        double base = random.nextDouble() * Math.PI * 2D;
        for (int i = 0; i < ring.size(); i++) {
            double angle = base + i * step + (random.nextDouble() - 0.5D) * step * JITTER;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            Planned placed;
            if (ring.get(i) == Kind.HOUSE) {
                int radius = MIN_HOUSE_RADIUS + random.nextInt(MAX_HOUSE_RADIUS - MIN_HOUSE_RADIUS + 1);
                int floors = MIN_FLOORS + random.nextInt(MAX_FLOORS - MIN_FLOORS + 1);
                boolean chest = random.nextBoolean();
                int distance = HOUSE_DISTANCE_MIN + random.nextInt(HOUSE_DISTANCE_SPAN);
                placed = push(pieces, centerX, centerZ, cos, sin, distance,
                        (x, z) -> new Planned(Kind.HOUSE, x, z, radius, floors, 0, false, chest));
            } else {
                int length = MIN_TOWER_LENGTH + random.nextInt(MAX_TOWER_LENGTH - MIN_TOWER_LENGTH + 1);
                boolean alongX = Math.abs(cos) >= Math.abs(sin);
                int distance = TOWER_DISTANCE_MIN + random.nextInt(TOWER_DISTANCE_SPAN);
                placed = push(pieces, centerX, centerZ, cos, sin, distance, (x, z) -> new Planned(Kind.TOWER, x, z, 0, 0, length, alongX, false));
                if (placed == null) {
                    placed = push(pieces, centerX, centerZ, cos, sin, distance, (x, z) -> new Planned(Kind.TOWER, x, z, 0, 0, length, !alongX, false));
                }
            }
            if (placed != null) {
                pieces.add(placed);
            }
        }
        return new Layout(centerX, centerZ, seed, List.copyOf(pieces));
    }

    private interface PieceAt {
        Planned at(int x, int z);
    }

    /** 沿 (cos, sin) 方向从 distance 往外推，找第一个不出范围、和已有各件隔开 GAP 的位置；推到 MAX_DISTANCE 还不行返回 null */
    private static Planned push(List<Planned> placed, int centerX, int centerZ, double cos, double sin, int distance, PieceAt make) {
        for (int d = distance; d <= MAX_DISTANCE; d++) {
            Planned piece = make.at(centerX + (int) Math.round(cos * d), centerZ + (int) Math.round(sin * d));
            BoundingBox f = piece.footprint();
            if (Math.max(Math.max(Math.abs(f.minX() - centerX), Math.abs(f.maxX() - centerX)),
                    Math.max(Math.abs(f.minZ() - centerZ), Math.abs(f.maxZ() - centerZ))) > MAX_RADIUS) {
                return null;
            }
            BoundingBox grown = f.inflatedBy(GAP);
            boolean clear = true;
            for (Planned other : placed) {
                if (grown.intersects(other.footprint())) {
                    clear = false;
                    break;
                }
            }
            if (clear) {
                return piece;
            }
        }
        return null;
    }

    /**
     * 把布局变成拼件：穹顶用 domeGround（判定时取过的那一列），每栋沉房取自己中心 1 列（groundAt：生成时 = {@link #columnGround}，
     * GameTest 传计数的假地面），塔身用离它最近的那一件的地面。拼件顺序 = 布局顺序（穹顶第一）。
     */
    public static List<HoannaStructurePiece> createPieces(Layout layout, int domeGround, IntBinaryOperator groundAt) {
        List<HoannaStructurePiece> pieces = new ArrayList<>(layout.pieces().size());
        List<int[]> grounds = new ArrayList<>();
        int index = 0;
        for (Planned planned : layout.pieces()) {
            long seed = pieceSeed(layout.seed(), index++);
            switch (planned.kind()) {
                case DOME -> {
                    pieces.add(SunkenPieces.Dome.create(planned, domeGround, seed));
                    grounds.add(new int[]{planned.x(), planned.z(), domeGround});
                }
                case HOUSE -> {
                    int ground = groundAt.applyAsInt(planned.x(), planned.z());
                    pieces.add(SunkenPieces.House.create(planned, ground, seed));
                    grounds.add(new int[]{planned.x(), planned.z(), ground});
                }
                case TOWER -> pieces.add(SunkenPieces.Tower.create(planned, nearestGround(grounds, planned), seed));
            }
        }
        return pieces;
    }

    /** 已经定过高的件里离 planned 最近的那一件的地面（穹顶一定在最前面，所以总有一件） */
    private static int nearestGround(List<int[]> grounds, Planned planned) {
        int best = grounds.get(0)[2];
        long bestDistance = Long.MAX_VALUE;
        for (int[] g : grounds) {
            long dx = g[0] - planned.x();
            long dz = g[1] - planned.z();
            long distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = g[2];
            }
        }
        return best;
    }

    /** 第 index 件的种子：由布局种子派生，同一座结构每次都一样 */
    public static long pieceSeed(long layoutSeed, int index) {
        return HoannaStructurePiece.mix(layoutSeed + (index + 1L) * PIECE_SEED_STEP);
    }
}
