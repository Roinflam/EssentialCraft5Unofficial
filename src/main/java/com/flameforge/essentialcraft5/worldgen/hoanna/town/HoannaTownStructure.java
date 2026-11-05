package com.flameforge.essentialcraft5.worldgen.hoanna.town;

import com.flameforge.essentialcraft5.worldgen.hoanna.HoannaStructurePiece;
import com.flameforge.essentialcraft5.worldgen.hoanna.StructureForecast;
import com.flameforge.essentialcraft5.worldgen.hoanna.StructureRuin;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntBinaryOperator;

/**
 * 城镇废墟 {@code essentialcraft5:hoanna_town}（M5，batch6a §5.5；原作 {@code MapGenTown}）。
 * <p>
 * <b>放置</b>（数据包 {@code worldgen/structure_set/hoanna_town.json}）：random_spread 32 / 8（D13 A，= 原作 {@code distance 32}、格内偏移 nextInt(24)），
 * salt 202609291，每 1 024 区块（约每 512 格）一座、相邻至少隔 8 区块；群系 {@code #essentialcraft5:has_structure/hoanna_town} = 海纳尔 6 个陆地群系
 * （原作不查群系，海上也生成）；{@code terrain_adaptation none}（不用 beard：城镇 7 × 7 区块都要多算一遍噪声）、不写 spawn_overrides。
 * 只挂海纳尔的群系标签，主世界、下界、末地连判定都不做（{@code ChunkGeneratorStructureState.createForNormal} 按群系源过滤结构集）。
 * <p>
 * <b>{@link #findGenerationPoint}</b>（每个候选区块、/locate 逐格试的时候都会调）只做便宜的事：起点一列的地面高度（1 次噪声列）、起点群系、
 * 布局（{@link HoannaTownPieces#layout}，只算包围盒）；非道路件 ≤ 2 返回空（原作 isSizeableStructure）。结构预测照同一个布局否决（{@link #forecastVeto}）。
 * 逐件定高放在 {@link Structure.GenerationStub} 的拼件回调里，在起点区块生成到 STRUCTURE_STARTS 时才算。
 * <p>
 * <b>定高的开销</b>（6a 审查 M5-1）：每件只取包围盒中心 1 列，每座城约 17~18 列（非道路件平均 15.5 个各 1 列，起点那一列在判定和生成时各算一次）。
 * 一列就是一次 {@code getFirstFreeHeight}：每次新建 NoiseChunk、把整棵密度函数重新包一遍、整列采样，按实测折算约 3 ms（f4fb1d7 时每件取 5 列、每座约 80 列，
 * /locate 平均 235 ms、最长 358 ms）。这段不一定在世界生成线程上：{@code /locate}、探索类指南针（整合包里的 ExplorersCompass 跑在 Forge 的
 * WorldWorkerManager 里，也就是服务端主线程）、主线程同步要 FULL 区块时，都会在主线程上等候选区块生成到 STRUCTURE_STARTS，每座没生成过的城镇卡一次。
 * 所以<b>别再往定高里加列</b>；同一座城镇缓存列高也没用（各件的中心几乎不会落在同一列上）。
 * <p>
 * <b>定高放在生成阶段</b>：原作在 addComponentParts 里用「第一个生成到这个件的区块」里的方块算平均地面（L204~211），1.20.1 跨区块的件由并行的
 * 世界生成线程各自 postProcess，在那里算有竞态、塔会断成两截。这里取包围盒中心那一列的 {@code getFirstFreeHeight(OCEAN_FLOOR_WG)}，
 * 不低于海平面（对应原作「地面上第一格空气、最低 63」，L786）。只取一列不比原作差（原作也只用第一个生成的区块里那部分列）：
 * 塔最底层的栅栏和角柱会 fillDown 补到地面，房子四角地下是悬浮器，地面有起伏时和原作一样会有一边悬空或埋进地里。
 * <p>
 * <b>弹坑</b>（D14 A）：拼件表末尾追加 {@code town_crater}——每栋房子 min(3, F) 个（楼层 i 在 [⌈F/2⌉, F] 里随机、半径 3 + i/3，照原作 L417 的半径；
 * 强化石 / 强化玻璃系数放大到 0.5、只挖二楼楼板及以上，见 {@link HoannaTownPieces.Crater}），
 * 每个爆炸件 1 个半径 15 的坑（原作 L918）。坑只挖当前区块、结果和区块生成顺序无关（{@link StructureRuin#carveCrater}）。
 */
public class HoannaTownStructure extends Structure implements StructureForecast.Vetoed {

    public static final Codec<HoannaTownStructure> CODEC = simpleCodec(HoannaTownStructure::new);

    /** 爆炸件的弹坑半径（原作 Explosion L918） */
    public static final float EXPLOSION_RADIUS = 15F;
    /** 每栋房子最多几个坑 */
    public static final int MAX_HOUSE_CRATERS = 3;
    /** 道路、起点件包围盒的半高：起点地面 ±3（batch6a §5.5 第 5 条） */
    public static final int ROAD_HALF_HEIGHT = 3;

    /** 派生每件种子、房子弹坑随机用的常数 */
    private static final long PIECE_SEED_STEP = 0x9E3779B97F4A7C15L;
    private static final long HOUSE_CRATER_SALT = 0xA54FF53A5F1D36F1L;

    public HoannaTownStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    public StructureType<?> type() {
        return TownStructures.HOANNA_TOWN.get();
    }

    /**
     * 一座城镇的布局结果。
     *
     * @param townSeed 城镇种子（结构生成随机的第一个 long），各件的种子从它派生
     */
    public record Plan(long townSeed, HoannaTownPieces.Layout layout) {
    }

    /**
     * 按结构生成随机布局一座城镇：先取城镇种子，再照原作布局。{@link #findGenerationPoint} 和 GameTest 共用，
     * 同一个区块、同一个世界种子算出来一样（随机是 {@code setLargeFeatureSeed(种子, 区块 x, 区块 z)}）。
     */
    public static Plan plan(RandomSource random, ChunkPos chunk) {
        long townSeed = random.nextLong();
        HoannaTownPieces.Layout layout = HoannaTownPieces.layout(random, chunk.getMinBlockX() + 2, chunk.getMinBlockZ() + 2);
        return new Plan(townSeed, layout);
    }

    /**
     * 结构预测的第 4 步：布局不成立（非道路件 ≤ 2）的候选不算。和 {@link #findGenerationPoint} 抽的是同一个布局：结构生成随机是
     * {@code setLargeFeatureSeed(世界种子, 区块 x, 区块 z)}，findGenerationPoint 在布局之前只取了地面列和群系，没动随机。纯计算，不取噪声列
     */
    @Override
    public StructureForecast.StartVeto forecastVeto() {
        return new StructureForecast.StartVeto((world, chunk) -> !plan(largeFeatureRandom(world.seed(), chunk), chunk).layout().isValid(), false);
    }

    /** 和结构生成用的随机一样（{@code Structure.GenerationContext} 里的 makeRandom） */
    public static WorldgenRandom largeFeatureRandom(long seed, ChunkPos chunk) {
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(seed, chunk.x, chunk.z);
        return random;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        int seaLevel = context.chunkGenerator().getSeaLevel();
        int centerX = chunk.getMinBlockX() + 4;
        int centerZ = chunk.getMinBlockZ() + 4;
        int startGround = columnGround(context, centerX, centerZ, seaLevel);
        // 起点的群系先看：海上的格子不布局、不定高（/locate 逐格试的时候省事）。原版 findValidGenerationPoint 之后还会按同一个位置再判一次
        Holder<Biome> biome = context.chunkGenerator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(centerX), QuartPos.fromBlock(startGround),
                QuartPos.fromBlock(centerZ), context.randomState().sampler());
        if (!context.validBiome().test(biome)) {
            return Optional.empty();
        }
        Plan plan = plan(context.random(), chunk);
        if (!plan.layout().isValid()) {
            return Optional.empty();
        }
        return Optional.of(new GenerationStub(new BlockPos(centerX, startGround, centerZ),
                builder -> createPieces(context, plan, startGround).forEach(builder::addPiece)));
    }

    /**
     * 把布局变成拼件：逐件定高（每件 1 列噪声，见类注释「定高的开销」），末尾追加弹坑。拼件顺序 = 原作 components 的顺序，同一区块里按这个顺序 postProcess。
     *
     * @param startGround 起点中心一列的地面（道路、起点件的包围盒高度用它）
     */
    public static List<HoannaStructurePiece> createPieces(GenerationContext context, Plan plan, int startGround) {
        int seaLevel = context.chunkGenerator().getSeaLevel();
        return createPieces(plan, startGround, (x, z) -> columnGround(context, x, z, seaLevel));
    }

    /**
     * 同 {@link #createPieces(GenerationContext, Plan, int)}，地面由 groundAt 给（世界 x, z → 地面 y）。
     * 生成时传 {@link #columnGround}（每次调用算一列噪声）；GameTest 传计数的假地面，守着「每件只取中心 1 列」。
     */
    public static List<HoannaStructurePiece> createPieces(Plan plan, int startGround, IntBinaryOperator groundAt) {
        List<HoannaStructurePiece> pieces = new ArrayList<>();
        List<HoannaStructurePiece> craters = new ArrayList<>();
        int index = 0;
        for (HoannaTownPieces.Planned planned : plan.layout().pieces()) {
            long seed = pieceSeed(plan.townSeed(), index++);
            BoundingBox box = planned.box();
            switch (planned.kind()) {
                case START -> pieces.add(onGround(new HoannaTownPieces.Start(planned.depth(),
                        vertical(box, startGround - ROAD_HALF_HEIGHT, startGround + ROAD_HALF_HEIGHT), planned.facing(), seed), startGround));
                case ROAD -> pieces.add(onGround(new HoannaTownPieces.Road(planned.depth(),
                        vertical(box, startGround - ROAD_HALF_HEIGHT, startGround + ROAD_HALF_HEIGHT), planned.facing(), seed), startGround));
                case TOWER -> {
                    int ground = pieceGround(box, groundAt);
                    pieces.add(onGround(new HoannaTownPieces.Tower(planned.depth(),
                            vertical(box, ground, ground + HoannaTownPieces.TOWER_HEIGHT - 1), planned.facing(), seed), ground));
                }
                case HOUSE -> {
                    int ground = pieceGround(box, groundAt);
                    int bottom = ground - HoannaTownPieces.HOUSE_FLOOR_Y;
                    HoannaTownPieces.House house = new HoannaTownPieces.House(planned.depth(),
                            vertical(box, bottom, bottom + HoannaTownPieces.House.height(planned.floors()) - 1), planned.facing(), seed,
                            planned.floors(), planned.radius());
                    pieces.add(onGround(house, ground));
                    craters.addAll(houseCraters(house, ground));
                }
                case SPREADER -> {
                    int ground = pieceGround(box, groundAt);
                    pieces.add(onGround(new HoannaTownPieces.Spreader(planned.depth(), vertical(box, ground - 1, ground + 6), planned.facing(), seed),
                            ground));
                }
                case TORCH -> {
                    int ground = pieceGround(box, groundAt);
                    pieces.add(onGround(new HoannaTownPieces.Torch(planned.depth(), vertical(box, ground, ground + 3), planned.facing(), seed), ground));
                }
                case EXPLOSION -> {
                    int ground = pieceGround(box, groundAt);
                    BlockPos center = new BlockPos(box.minX() + 2, ground, box.minZ() + 2);
                    craters.add(onGround(new HoannaTownPieces.Crater(planned.depth(), center, EXPLOSION_RADIUS, seed), ground));
                }
            }
        }
        pieces.addAll(craters);
        return pieces;
    }

    /**
     * 一栋房子的弹坑（D14 A）：min(3, F) 个，楼层 i 在 [⌈F/2⌉, F] 里随机、半径 3 + i/3（照原作 L417），坑心在这一层里随机一格。
     * 随机只由房子的种子决定（不占结构生成随机）。都是「房子的坑」：强化石 / 强化玻璃另给系数、只挖二楼楼板（{@link #houseCraterFloor}）及以上，
     * 见 {@link HoannaTownPieces.Crater}。
     *
     * @param ground 房子一楼楼板的世界 y
     */
    public static List<HoannaTownPieces.Crater> houseCraters(HoannaTownPieces.House house, int ground) {
        int floors = house.floors();
        int count = Math.min(MAX_HOUSE_CRATERS, floors);
        int lowest = (floors + 1) / 2;
        int side = house.radius() * 2 + 1;
        int floorY = houseCraterFloor(ground);
        BoundingBox box = house.getBoundingBox();
        RandomSource random = RandomSource.create(HoannaStructurePiece.mix(house.seed() ^ HOUSE_CRATER_SALT));
        List<HoannaTownPieces.Crater> craters = new ArrayList<>(count);
        for (int k = 0; k < count; k++) {
            int storey = lowest + random.nextInt(floors - lowest + 1);
            BlockPos center = new BlockPos(box.minX() + random.nextInt(side), ground + storey * HoannaTownPieces.STOREY_HEIGHT + random.nextInt(5),
                    box.minZ() + random.nextInt(side));
            HoannaTownPieces.Crater crater = new HoannaTownPieces.Crater(house.getGenDepth(), center, houseCraterRadius(storey), random.nextLong(), floorY);
            crater.setGroundY(ground);
            craters.add(crater);
        }
        return craters;
    }

    /** 房子的坑最低挖到哪一格：二楼楼板（地面 + 5）；一楼天花板在地面 + 4，不挖 */
    public static int houseCraterFloor(int ground) {
        return ground + HoannaTownPieces.STOREY_HEIGHT;
    }

    /** 第 i 层的弹坑半径：3 + i/3（整数除，原作 L417 {@code new ECExplosion(…, 3+i/3)}） */
    public static float houseCraterRadius(int storey) {
        return 3 + storey / 3;
    }

    /** 第 index 件的种子：由城镇种子派生，同一座城镇每次都一样 */
    public static long pieceSeed(long townSeed, int index) {
        return HoannaStructurePiece.mix(townSeed + (index + 1L) * PIECE_SEED_STEP);
    }

    /**
     * 件的地面：包围盒中心那一列（groundAt 给的，生成时 = {@link #columnGround}）。只取 1 列，原因见类注释「定高的开销」
     * （以前取四角 + 中心 5 列平均，/locate 和探索类指南针会卡主线程约 0.2 秒）。
     * 不用框架的 {@code averageGroundY}：那个取 5 列、先平均再托底。
     */
    public static int pieceGround(BoundingBox box, IntBinaryOperator groundAt) {
        BlockPos center = box.getCenter();
        return groundAt.applyAsInt(center.getX(), center.getZ());
    }

    /** 一列按噪声算的第一格空位（水底算地面），不低于海平面。每次调用算一列噪声（约 3 ms），只在生成阶段调 */
    public static int columnGround(GenerationContext context, int x, int z, int seaLevel) {
        return Math.max(seaLevel, HoannaStructurePiece.groundYAt(context, x, z, Heightmap.Types.OCEAN_FLOOR_WG));
    }

    /** 同一个平面范围、换成 [minY, maxY] 的新包围盒 */
    private static BoundingBox vertical(BoundingBox box, int minY, int maxY) {
        return new BoundingBox(box.minX(), minY, box.minZ(), box.maxX(), maxY, box.maxZ());
    }

    private static <P extends HoannaStructurePiece> P onGround(P piece, int ground) {
        piece.setGroundY(ground);
        return piece;
    }
}
