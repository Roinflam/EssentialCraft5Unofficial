package com.flameforge.essentialcraft5.worldgen.hoanna.outpost;

import com.flameforge.essentialcraft5.worldgen.hoanna.HoannaStructurePiece;
import com.flameforge.essentialcraft5.worldgen.hoanna.StructureForecast;
import com.flameforge.essentialcraft5.worldgen.hoanna.SurfaceColumn;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 审判庭前哨 {@code essentialcraft5:inquisitorium_outpost}（第十三批 13e-S3，batch13.md §6.4；HS §2.4.3）：暗影腐化之地里一片被毁的审判庭场地：
 * 半径 18 的圆盘、外圈 8 根虚空石柱、中央 5 × 5 信标座、场外两个营地巢穴；第十九章解码器的坐标多半指着它（{@code GlitchyCoreDecoder.SiteSearch}）。
 * <p>
 * <b>放置</b>（数据包 {@code worldgen/structure_set/inquisitorium_outpost.json}）：{@code spread_excluding}、salt {@value OutpostStructures#SALT}，
 * 排除城镇集、宝库集各 8 区块；群系 {@code #essentialcraft5:has_structure/inquisitorium_outpost}（= {@code #essentialcraft5:inquisitorium_sites}，默认暗影腐化之地）；
 * {@code terrain_adaptation none}、不写 spawn_overrides。只挂海纳尔的群系标签，主世界、下界、末地连判定都不做
 * （{@code ChunkGeneratorStructureState.createForNormal} 按群系源过滤结构集）。spacing / separation 由暗影区普查定（{@link OutpostCensus}，结果在 {@code tools/gen/hoanna_bench.md}）。
 * <p>
 * <b>{@link #findGenerationPoint}</b>：
 * <ol>
 *   <li>先判群系：起点区块最小角 +4、海平面那一点（{@link #biomeAllows}），和结构预测（{@link StructureForecast}）采样的是同一点；
 *       生成桩的位置也放在这一点上（原版 {@code findValidGenerationPoint} 之后按桩的位置再判一次群系；海纳尔的群系不随高度变，高度不影响结论），
 *       所以预测 = 实际。群系对不上直接返回空，不算地面。</li>
 *   <li>定高 <b>1 列</b>：场地中心（起点区块中心 = 预测结果 {@code StructureForecast.resultPos}）那一列（{@link SurfaceColumn}，一次噪声列同时得出
 *       {@code WORLD_SURFACE_WG} 和 {@code OCEAN_FLOOR_WG}，约 3 ms；/locate 逐格试的时候只有群系过了的候选才算这一列）。
 *       这一列顶上是液体（湖、河、暗影区边上的海）就不放，结构预测照同一列否决（{@link #forecastVeto}），解码器不会把坐标指到水面上；
 *       否则地面 = {@code OCEAN_FLOOR_WG}，不低于海平面。石柱、信标座、场地都用这一个高度；
 *       营地在场外、地面可能高低不同，但每个营地整个落在一个区块里，到它自己那个区块 postProcess 时按那个区块里已有的方块找地面（只有一个区块写它，没有竞态），不另算噪声列。</li>
 *   <li>除群系和中心液体外不拒绝任何地形：悬崖、山顶、场地边上的湖照样放，场地削平和补地基见 {@link OutpostPieces.Arena}。</li>
 * </ol>
 * <b>布局</b>（{@link #plan}，纯计算，GameTest 拿 200 个种子跑）：
 * <ul>
 *   <li>场地：中心 = 起点区块中心，半径 {@value #ARENA_RADIUS}；场地面（地板那一层）= 地面 − 1。</li>
 *   <li>石柱：8 根 2 × 2，圈半径 {@value #PYLON_RING}，起始角随机、每 45° 一根；完好的高 {@value #PYLON_MIN}~{@value #PYLON_MAX}、顶上一格魔力光源，
 *       2~3 根断的高 {@value #BROKEN_MIN}~{@value #BROKEN_MAX}、没有光源。</li>
 *   <li>信标座：中心 5 × 5，一格高（站在上面召审判庭哨兵：离坐标 0 格、露天）。</li>
 *   <li>营地 2 个：离中心 {@value #CAMP_MIN_DISTANCE}~{@value #CAMP_MAX_DISTANCE} 格，两个大致在相对的两侧；7 × 7 整个落在一个区块里（试几次随机角度和距离，
 *       都不行就把中心挪进区块内侧）。整座结构离中心不超过 {@value #MAX_RADIUS} 格（各件包围盒到中心的 x / z 方向距离；营地 7 × 7 的角离中心最远约 34.2 格）。</li>
 * </ul>
 * 各件的随机都从结构生成随机（{@code setLargeFeatureSeed(世界种子, 区块)}）里取，同一个世界种子、同一个区块每次一样。
 */
public class InquisitoriumOutpostStructure extends Structure implements StructureForecast.Vetoed {

    public static final Codec<InquisitoriumOutpostStructure> CODEC = simpleCodec(InquisitoriumOutpostStructure::new);

    /** 场地圆盘半径 */
    public static final int ARENA_RADIUS = 18;
    /** 场地面以上削平多高（天然方块） */
    public static final int CLEAR_HEIGHT = 16;
    /** 场地外缘向下补地基最多几格 */
    public static final int FILL_DEPTH = 8;
    /** 石柱圈半径（2 × 2 的中心到场地中心） */
    public static final int PYLON_RING = 15;
    public static final int PYLON_COUNT = 8;
    public static final int PYLON_MIN = 6;
    public static final int PYLON_MAX = 12;
    public static final int BROKEN_MIN = 2;
    public static final int BROKEN_MAX = 4;
    /** 石柱往下当支撑最多补几格（场地地基补不到的地方靠它） */
    public static final int PYLON_SUPPORT = 32;
    /** 信标座半边（5 × 5） */
    public static final int DAIS_HALF = 2;
    public static final int CAMP_COUNT = 2;
    public static final int CAMP_MIN_DISTANCE = 26;
    public static final int CAMP_MAX_DISTANCE = 30;
    /** 营地半边（7 × 7） */
    public static final int CAMP_HALF = 3;
    /** 营地包围盒在场地面上下各留多少格找地面 */
    public static final int CAMP_REACH = 40;
    /** 营地挑位置试几次 */
    public static final int CAMP_TRIES = 32;
    /** 整座结构离中心最远（各件包围盒在 x / z 方向上离中心几格） */
    public static final int MAX_RADIUS = 34;

    /** 派生每件种子的步长（同城镇） */
    private static final long PIECE_SEED_STEP = 0x9E3779B97F4A7C15L;

    public InquisitoriumOutpostStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    public StructureType<?> type() {
        return OutpostStructures.INQUISITORIUM_OUTPOST.get();
    }

    /**
     * 一根石柱。
     *
     * @param x      2 × 2 的最小角
     * @param z      2 × 2 的最小角
     * @param height 场地面以上几格（不含顶上的光源）
     * @param broken 断的：矮、没有光源
     */
    public record PylonSpec(int x, int z, int height, boolean broken) {
    }

    /** 一个营地（7 × 7 的中心） */
    public record CampSpec(int x, int z) {
    }

    /**
     * 一座前哨的布局。
     *
     * @param centerX 场地中心
     * @param centerZ 场地中心
     * @param ground  中心那一列的地面（第一格空气）；场地面 = ground − 1
     * @param seed    结构种子，各件的种子从它派生
     */
    public record Plan(int centerX, int centerZ, int ground, long seed, List<PylonSpec> pylons, List<CampSpec> camps) {

        public int floorY() {
            return ground - 1;
        }

        /** 信标座上面那一格（召哨兵站的地方） */
        public BlockPos daisTop() {
            return new BlockPos(centerX, ground + 1, centerZ);
        }
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!biomeAllows(context)) {
            return Optional.empty();
        }
        ChunkPos chunk = context.chunkPos();
        int seaLevel = context.chunkGenerator().getSeaLevel();
        SurfaceColumn column = SurfaceColumn.of(context, centerX(chunk), centerZ(chunk));
        if (column.liquidTop()) {
            return Optional.empty();
        }
        int ground = Math.max(seaLevel, column.floor());
        Plan plan = plan(context.random(), chunk, ground);
        BlockPos stub = new BlockPos(StructureForecast.sampleX(chunk), ground, StructureForecast.sampleZ(chunk));
        return Optional.of(new GenerationStub(stub, builder -> createPieces(plan).forEach(builder::addPiece)));
    }

    /** 结构预测的第 4 步：场地中心那一列顶上是液体就不放（和 findGenerationPoint 同一列、同一个判定） */
    @Override
    public StructureForecast.StartVeto forecastVeto() {
        return new StructureForecast.StartVeto((world, chunk) -> SurfaceColumn.sample(world.generator(), centerX(chunk), centerZ(chunk), world.height(),
                world.randomState()).liquidTop(), true);
    }

    /** 群系：起点区块最小角 +4、海平面那一点（和 {@link StructureForecast} 采样的同一点）在不在这座结构的群系标签里。一次群系采样，不算地面 */
    public static boolean biomeAllows(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        int y = context.chunkGenerator().getSeaLevel();
        Holder<Biome> biome = context.chunkGenerator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(StructureForecast.sampleX(chunk)), QuartPos.fromBlock(y),
                QuartPos.fromBlock(StructureForecast.sampleZ(chunk)), context.randomState().sampler());
        return context.validBiome().test(biome);
    }

    /** 场地中心：起点区块中心（= 结构预测给的坐标） */
    public static int centerX(ChunkPos chunk) {
        return chunk.getMiddleBlockX();
    }

    public static int centerZ(ChunkPos chunk) {
        return chunk.getMiddleBlockZ();
    }

    /**
     * 布局（纯计算）：findGenerationPoint 和 GameTest 共用；随机按 结构种子 → 石柱 → 营地 的顺序取。
     *
     * @param ground 中心那一列的地面
     */
    public static Plan plan(RandomSource random, ChunkPos chunk, int ground) {
        long seed = random.nextLong();
        int cx = centerX(chunk);
        int cz = centerZ(chunk);
        float base = random.nextFloat() * Mth.TWO_PI;
        int brokenCount = 2 + random.nextInt(2);
        boolean[] broken = new boolean[PYLON_COUNT];
        for (int picked = 0; picked < brokenCount; ) {
            int index = random.nextInt(PYLON_COUNT);
            if (!broken[index]) {
                broken[index] = true;
                picked++;
            }
        }
        List<PylonSpec> pylons = new ArrayList<>(PYLON_COUNT);
        for (int i = 0; i < PYLON_COUNT; i++) {
            float angle = base + i * Mth.TWO_PI / PYLON_COUNT;
            // 2 × 2 的中心在 (cx + 0.5 + cos·R, cz + 0.5 + sin·R) 附近：最小角取它减一
            int x = Mth.floor(cx + 0.5F + Mth.cos(angle) * PYLON_RING) - 1;
            int z = Mth.floor(cz + 0.5F + Mth.sin(angle) * PYLON_RING) - 1;
            int height = broken[i] ? BROKEN_MIN + random.nextInt(BROKEN_MAX - BROKEN_MIN + 1) : PYLON_MIN + random.nextInt(PYLON_MAX - PYLON_MIN + 1);
            pylons.add(new PylonSpec(x, z, height, broken[i]));
        }
        float campBase = random.nextFloat() * Mth.TWO_PI;
        List<CampSpec> camps = new ArrayList<>(CAMP_COUNT);
        for (int k = 0; k < CAMP_COUNT; k++) {
            float aim = campBase + k * Mth.PI;
            CampSpec camp = null;
            CampSpec last = null;
            for (int attempt = 0; attempt < CAMP_TRIES && camp == null; attempt++) {
                float angle = aim + (random.nextFloat() - 0.5F) * 1.0F;
                float distance = CAMP_MIN_DISTANCE + random.nextFloat() * (CAMP_MAX_DISTANCE - CAMP_MIN_DISTANCE);
                int x = Mth.floor(cx + 0.5F + Mth.cos(angle) * distance);
                int z = Mth.floor(cz + 0.5F + Mth.sin(angle) * distance);
                last = new CampSpec(x, z);
                double d = Math.sqrt((double) (x - cx) * (x - cx) + (double) (z - cz) * (z - cz));
                if (campFitsChunk(x, z) && d >= CAMP_MIN_DISTANCE && d <= CAMP_MAX_DISTANCE) {
                    camp = last;
                }
            }
            camps.add(camp != null ? camp : intoChunk(last));
        }
        return new Plan(cx, cz, ground, seed, List.copyOf(pylons), List.copyOf(camps));
    }

    /** 7 × 7 整个落在 (x, z) 所在的那个区块里 */
    public static boolean campFitsChunk(int x, int z) {
        int lx = x & 15;
        int lz = z & 15;
        return lx >= CAMP_HALF && lx <= 15 - CAMP_HALF && lz >= CAMP_HALF && lz <= 15 - CAMP_HALF;
    }

    /** 挪进所在区块的内侧（试了几次都不行时兜底，距离会差几格） */
    private static CampSpec intoChunk(CampSpec camp) {
        int x = (camp.x() & ~15) + Mth.clamp(camp.x() & 15, CAMP_HALF, 15 - CAMP_HALF);
        int z = (camp.z() & ~15) + Mth.clamp(camp.z() & 15, CAMP_HALF, 15 - CAMP_HALF);
        return new CampSpec(x, z);
    }

    /**
     * 把布局变成拼件，顺序：场地 → 信标座 → 8 根石柱 → 2 个营地（同一区块里按这个顺序 postProcess：先削平、铺地板，再立柱子）。
     * 场地、信标座、石柱的地面都是中心那一列（存进各件的 {@code ec5_ground}）。
     */
    public static List<HoannaStructurePiece> createPieces(Plan plan) {
        int floor = plan.floorY();
        int cx = plan.centerX();
        int cz = plan.centerZ();
        List<HoannaStructurePiece> pieces = new ArrayList<>();
        int index = 0;
        OutpostPieces.Arena arena = new OutpostPieces.Arena(0, new BoundingBox(cx - ARENA_RADIUS, floor - FILL_DEPTH, cz - ARENA_RADIUS,
                cx + ARENA_RADIUS, floor + CLEAR_HEIGHT, cz + ARENA_RADIUS), pieceSeed(plan.seed(), index++));
        arena.setGroundY(plan.ground());
        pieces.add(arena);
        OutpostPieces.Dais dais = new OutpostPieces.Dais(1, new BoundingBox(cx - DAIS_HALF, floor, cz - DAIS_HALF, cx + DAIS_HALF, floor + 1, cz + DAIS_HALF),
                pieceSeed(plan.seed(), index++));
        dais.setGroundY(plan.ground());
        pieces.add(dais);
        for (PylonSpec spec : plan.pylons()) {
            OutpostPieces.Pylon pylon = new OutpostPieces.Pylon(1, new BoundingBox(spec.x(), floor - PYLON_SUPPORT, spec.z(), spec.x() + 1,
                    floor + spec.height() + 1, spec.z() + 1), pieceSeed(plan.seed(), index++), spec.height(), spec.broken());
            pylon.setGroundY(plan.ground());
            pieces.add(pylon);
        }
        for (CampSpec spec : plan.camps()) {
            OutpostPieces.Camp camp = new OutpostPieces.Camp(1, new BoundingBox(spec.x() - CAMP_HALF, floor - CAMP_REACH, spec.z() - CAMP_HALF,
                    spec.x() + CAMP_HALF, floor + CAMP_REACH, spec.z() + CAMP_HALF), pieceSeed(plan.seed(), index++));
            camp.setGroundY(plan.ground());
            pieces.add(camp);
        }
        return pieces;
    }

    /** 第 index 件的种子：由结构种子派生 */
    public static long pieceSeed(long seed, int index) {
        return HoannaStructurePiece.mix(seed + (index + 1L) * PIECE_SEED_STEP);
    }
}
