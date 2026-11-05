package com.flameforge.essentialcraft5.worldgen.hoanna.town;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.loot.HoannaLoot;
import com.flameforge.essentialcraft5.registry.ECAlloys;
import com.flameforge.essentialcraft5.registry.ECEssence;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.worldgen.hoanna.HoannaStructureBlocks;
import com.flameforge.essentialcraft5.worldgen.hoanna.HoannaStructurePiece;
import com.flameforge.essentialcraft5.worldgen.hoanna.StructureRuin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 城镇废墟（M5，batch6a §5.5）的布局与 7 种拼件。原作 {@code StructureTownPieces}（924 行）+ {@code MapGenTown}。
 * <p>
 * <b>两段</b>：
 * <ol>
 *   <li>{@link #layout}：照原作只算包围盒（y 统一是布局高度 64~78，只用来判相交——所有件在 y 74~78 都重叠，相交实际是平面上的）。
 *       件种、权重、上限、道路长度、两侧房子的间隔、随机数的抽法全照原作，所以件数、塔数、城镇大小和原作一样（模拟 {@code tools/gen/hoanna/town_sim.py}：
 *       约 23 件、塔 3.2 座、道路 ≤ 8 条、离中心最远 54 格）。纯函数，GameTest 直接拿 500 个种子跑。</li>
 *   <li>{@link HoannaTownStructure} 把布局变成真的拼件：逐件定高（生成阶段按噪声算，不在 postProcess 里算），末尾追加弹坑件。</li>
 * </ol>
 * <p>
 * <b>原作 bug 照原作保留（D15 取 A）</b>：道路两端分叉时，朝西、朝东的路往北 / 往南分出去的新路方向写反了
 * （原作 L544 / L550 / L562 / L568，和原版 1.12 {@code VillagePieces.Path} 比对过：原版是 WEST → NORTH / SOUTH，EC 写成 SOUTH / NORTH），
 * 新路的包围盒压在自己身上、永远放不下。结果是只有朝北、朝南的路能分叉，城镇实际约 23 件、7 × 7 区块。
 * 修掉的话城镇会长到约 470 件、半径约 300 格（设 64 格上限也有约 76 件、塔的箱子是原作的 2.6 倍）——这个 bug 决定了原作城镇的大小，修掉等于换一个结构。
 * 见 {@link Planner#branchRoads}。
 * <p>
 * <b>方块</b>：强化石、强化玻璃、魔力护板、魔力栅栏、强化石栅栏、魔力光源用前几批的注册字段；虚空石、虚空栅栏、混凝土、悬浮器、散射器是 ECVoid 的，
 * 一律经 {@link HoannaStructureBlocks} 按注册名取（没注册时退回石头 / 空气，模块副本里照样能生成）。
 * <p>
 * <b>跨区块确定</b>：拼件里所有「这一格放不放、放什么」的随机都按世界坐标取哈希（{@link HoannaStructurePiece#localUnit}、{@link StructureRuin}），
 * 不用 postProcess 按区块播种的 random（那个只给箱子的战利品种子用）；只读自己这一列的方块。一个件跨几个区块、哪个区块先生成，结果都一样。
 */
public final class HoannaTownPieces {

    /** 安全网：离起点中心超过这么多格的件不放（原作最远 54 格，模拟 2000 座从没到过；保证整座城镇落在起点 ±8 区块的引用范围里） */
    public static final int TOWN_MAX_RADIUS = 64;
    /** 原作 Intersection 的布局高度（L860）：y 64~78 */
    public static final int LAYOUT_Y = 64;
    /** 原作 generateAndAddComponent 的件深度上限（L157） */
    public static final int MAX_PIECE_DEPTH = 500;
    /** 原作 generateAndAddRoadPiece 的深度上限（L172：50 + terrainType，terrainType 恒为 0） */
    public static final int MAX_ROAD_DEPTH = 50;
    /** 原作 ±600 格限制（L157 / L172） */
    public static final int LEGACY_REACH = 600;
    /** 塔每层 16 格高、4 层（原作 L214~216） */
    public static final int TOWER_HEIGHT = 64;
    /** 房子每层 5 格高 */
    public static final int STOREY_HEIGHT = 5;
    /** 房子拼件局部坐标里一楼楼板所在的 y（y 0 是四角悬浮器那一行，埋在地面里） */
    public static final int HOUSE_FLOOR_Y = 1;
    /** 路面往下最多跳过几格草花雪层找真正的地表 */
    public static final int ROAD_COVER_SKIP = 3;

    /** 房子室内碎石用的哈希盐 */
    static final long DEBRIS_SALT = 0x3C6EF372FE94F82BL;

    private HoannaTownPieces() {
    }

    // ================================================================= 布局

    /** 布局里的件种（{@link #HOUSE} 是原作 House1~4 合一，用 radius 区分） */
    public enum Kind {
        /** 原作 Start / Intersection：不放方块，四方各出一条路 */
        START,
        /** 原作 Path */
        ROAD,
        TOWER,
        HOUSE,
        SPREADER,
        /** 原作 Torch：虚空栅栏灯柱 */
        TORCH,
        /** 原作 Explosion：一个半径 15 的弹坑，布局时占 5 × 5 × 5 */
        EXPLOSION
    }

    /**
     * 布局里的一件：只有包围盒（布局高度）、朝向、深度，房子另有层数和半径。不可变：{@link #box()} 每次返回副本。
     *
     * @param floors 房子的层数 F（原作字段 floors；实际盖 F + 1 层，见 {@link House}），别的件是 0
     * @param radius 房子的半径（边长 2r + 1：House1 5、House2 4、House3 3、House4 7），别的件是 0
     */
    public record Planned(Kind kind, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, Direction facing, int depth, int floors,
                          int radius) {

        static Planned of(Kind kind, BoundingBox box, Direction facing, int depth, int floors, int radius) {
            return new Planned(kind, box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ(), facing, depth, floors, radius);
        }

        /** 包围盒（布局高度；新建的副本，随便改） */
        public BoundingBox box() {
            return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
        }

        public int xSpan() {
            return maxX - minX + 1;
        }

        public int zSpan() {
            return maxZ - minZ + 1;
        }

        boolean intersects(BoundingBox other) {
            return maxX >= other.minX() && minX <= other.maxX() && maxZ >= other.minZ() && minZ <= other.maxZ()
                    && maxY >= other.minY() && minY <= other.maxY();
        }
    }

    /**
     * 一次布局的结果。
     *
     * @param originX 起点件的西北角（区块 minX + 2，原作 MapGenTown L97）
     * @param pieces  按生成顺序（原作 components 的顺序），第一件是起点
     */
    public record Layout(int originX, int originZ, List<Planned> pieces) {

        /** 城镇中心（起点件 6 × 6 的中间偏西北一格） */
        public int centerX() {
            return originX + 2;
        }

        public int centerZ() {
            return originZ + 2;
        }

        public int count(Kind kind) {
            int n = 0;
            for (Planned piece : pieces) {
                if (piece.kind() == kind) {
                    n++;
                }
            }
            return n;
        }

        /** 不是道路的件数（含起点；原作 MapGenTown L121~127） */
        public int nonRoadCount() {
            return pieces.size() - count(Kind.ROAD);
        }

        /** 原作 isSizeableStructure：非道路件 &gt; 2 才生成 */
        public boolean isValid() {
            return nonRoadCount() > 2;
        }

        /** 所有件离中心最远的格数（x、z 分别算，取大的） */
        public int extent() {
            int max = 0;
            for (Planned piece : pieces) {
                max = Math.max(max, Math.max(Math.max(Math.abs(piece.minX() - centerX()), Math.abs(piece.maxX() - centerX())),
                        Math.max(Math.abs(piece.minZ() - centerZ()), Math.abs(piece.maxZ() - centerZ()))));
            }
            return max;
        }
    }

    /**
     * 照原作布局一座城镇（只算包围盒，不碰世界）。
     *
     * @param random 结构生成随机（{@code context.random()}，已经取过城镇种子）
     * @param x      起点件西北角（区块 minX + 2）
     * @param z      起点件西北角（区块 minZ + 2）
     */
    public static Layout layout(RandomSource random, int x, int z) {
        return new Planner(random, x, z).run();
    }

    /** 件的尺寸表与权重（原作 getStructureTownWeightedPieceList L51~57，顺序就是抽签顺序） */
    private enum Template {
        HOUSE4(Kind.HOUSE, 4, 30, 7, 3),
        TOWER(Kind.TOWER, 20, 10, 0, 0),
        HOUSE1(Kind.HOUSE, 20, 30, 5, 2),
        EXPLOSION(Kind.EXPLOSION, 3, 100, 0, 0),
        SPREADER(Kind.SPREADER, 15, 60, 0, 0),
        HOUSE2(Kind.HOUSE, 15, 30, 4, 5),
        HOUSE3(Kind.HOUSE, 8, 30, 3, 9);

        final Kind kind;
        final int weight;
        final int limit;
        /** 房子的半径 */
        final int radius;
        /** 房子层数 = nextInt(floorRoll) + 1 */
        final int floorRoll;

        Template(Kind kind, int weight, int limit, int radius, int floorRoll) {
            this.kind = kind;
            this.weight = weight;
            this.limit = limit;
            this.radius = radius;
            this.floorRoll = floorRoll;
        }
    }

    /** 原作 PieceWeight：权重表里的一项和已经放了几个 */
    private static final class Weight {
        final Template template;
        int spawned;

        Weight(Template template) {
            this.template = template;
        }

        boolean canSpawnMore() {
            return spawned < template.limit;
        }
    }

    /** 原作 Start 上挂的布局状态 + StructureTownPieces 里的几个静态方法 */
    private static final class Planner {
        private final RandomSource random;
        private final List<Planned> all = new ArrayList<>();
        private final List<Planned> pendingRoads = new ArrayList<>();
        private final List<Planned> pendingHouses = new ArrayList<>();
        private final List<Weight> weights = new ArrayList<>();
        private final int originX;
        private final int originZ;
        private final int centerX;
        private final int centerZ;
        private Weight last;

        Planner(RandomSource random, int x, int z) {
            this.random = random;
            this.originX = x;
            this.originZ = z;
            this.centerX = x + 2;
            this.centerZ = z + 2;
            for (Template template : Template.values()) {
                weights.add(new Weight(template));
            }
        }

        Layout run() {
            // 原作 Intersection 构造器：朝向随机（只为照原作消耗一次随机）；包围盒 6 × 15 × 6、y 64~78
            Direction facing = Direction.from2DDataValue(random.nextInt(4));
            BoundingBox startBox = new BoundingBox(originX, LAYOUT_Y, originZ, originX + 5, LAYOUT_Y + 14, originZ + 5);
            Planned start = Planned.of(Kind.START, startBox, facing, 0, 0, 0);
            all.add(start);
            // 原作 Intersection.buildComponent（L868~873）：四方各一条路，路的 y = maxY − 4
            int roadY = startBox.maxY() - 4;
            addRoad(startBox.minX() - 1, roadY, startBox.minZ() + 1, Direction.WEST, 0);
            addRoad(startBox.maxX() + 1, roadY, startBox.minZ() + 1, Direction.EAST, 0);
            addRoad(startBox.minX() + 1, roadY, startBox.minZ() - 1, Direction.NORTH, 0);
            addRoad(startBox.minX() + 1, roadY, startBox.maxZ() + 1, Direction.SOUTH, 0);
            // 原作 MapGenTown.Start L104~117：先把路展开完，再随机取走待处理的房子（房子没有子件，只为照原作消耗随机）
            while (!pendingRoads.isEmpty() || !pendingHouses.isEmpty()) {
                if (pendingRoads.isEmpty()) {
                    pendingHouses.remove(random.nextInt(pendingHouses.size()));
                } else {
                    buildRoad(pendingRoads.remove(random.nextInt(pendingRoads.size())));
                }
            }
            return new Layout(originX, originZ, Collections.unmodifiableList(new ArrayList<>(all)));
        }

        /** 原作 Path.buildComponent（L515~573）：两侧按间隔放房子，放下过至少一件才分叉 */
        private void buildRoad(Planned road) {
            int length = Math.max(road.xSpan(), road.zSpan());
            boolean placed = false;
            for (int i = random.nextInt(18); i < length - 8; i += 2 + random.nextInt(18)) {
                Planned piece = nextNN(road, i);
                if (piece != null) {
                    i += Math.max(piece.xSpan(), piece.zSpan());
                    placed = true;
                }
            }
            for (int i = random.nextInt(18); i < length - 8; i += 2 + random.nextInt(18)) {
                Planned piece = nextPP(road, i);
                if (piece != null) {
                    i += Math.max(piece.xSpan(), piece.zSpan());
                    placed = true;
                }
            }
            branchRoads(road, placed);
        }

        /**
         * 原作 L538~572：路的两端各有 17/18 的机会分出一条新路。
         * <p>
         * <b>照原作保留的 bug（D15 A）</b>：朝西、朝东的路，第一条本该往北（NORTH）、第二条本该往南（SOUTH），原作写反了
         * （L544 / L550 写 SOUTH、L562 / L568 写 NORTH）。新路从这条路的北边沿（minZ − 1）起却朝南长，包围盒压在这条路自己身上，
         * 永远放不下（反之亦然）——所以东西向的路从不分叉，城镇只有约 23 件。这个 bug 决定了原作城镇的实际大小（修掉约 470 件），
         * 定了照原作不修，别「顺手修」。原版 1.12 {@code VillagePieces.Path} 的写法是 WEST / EAST → NORTH、SOUTH。
         */
        private void branchRoads(Planned road, boolean placed) {
            if (placed && random.nextInt(18) > 0) {
                switch (road.facing()) {
                    case SOUTH -> addRoad(road.minX() - 1, road.minY(), road.maxZ() - 2, Direction.WEST, road.depth());
                    case WEST -> addRoad(road.minX(), road.minY(), road.minZ() - 1, Direction.SOUTH, road.depth()); // 原作写反（应为 NORTH），照原作
                    case NORTH -> addRoad(road.minX() - 1, road.minY(), road.minZ(), Direction.WEST, road.depth());
                    case EAST -> addRoad(road.maxX() - 2, road.minY(), road.minZ() - 1, Direction.SOUTH, road.depth()); // 原作写反（应为 NORTH），照原作
                    default -> {
                    }
                }
            }
            if (placed && random.nextInt(18) > 0) {
                switch (road.facing()) {
                    case SOUTH -> addRoad(road.maxX() + 1, road.minY(), road.maxZ() - 2, Direction.EAST, road.depth());
                    case WEST -> addRoad(road.minX(), road.minY(), road.maxZ() + 1, Direction.NORTH, road.depth()); // 原作写反（应为 SOUTH），照原作
                    case NORTH -> addRoad(road.maxX() + 1, road.minY(), road.minZ(), Direction.EAST, road.depth());
                    case EAST -> addRoad(road.maxX() - 2, road.minY(), road.maxZ() + 1, Direction.NORTH, road.depth()); // 原作写反（应为 SOUTH），照原作
                    default -> {
                    }
                }
            }
        }

        /** 原作 Town.getNextComponentNN（L746~759）：路的左手边（南北向的路是西边，东西向的路是北边） */
        private Planned nextNN(Planned road, int offset) {
            return switch (road.facing()) {
                case SOUTH, NORTH -> addPiece(road.minX() - 1, road.minY(), road.minZ() + offset, Direction.WEST, road.depth());
                case WEST, EAST -> addPiece(road.minX() + offset, road.minY(), road.minZ() - 1, Direction.NORTH, road.depth());
                default -> null;
            };
        }

        /** 原作 Town.getNextComponentPP（L761~774）：路的另一边 */
        private Planned nextPP(Planned road, int offset) {
            return switch (road.facing()) {
                case SOUTH, NORTH -> addPiece(road.maxX() + 1, road.minY(), road.minZ() + offset, Direction.EAST, road.depth());
                case WEST, EAST -> addPiece(road.minX() + offset, road.minY(), road.maxZ() + 1, Direction.SOUTH, road.depth());
                default -> null;
            };
        }

        /** 原作 generateAndAddComponent（L156~169） */
        private Planned addPiece(int x, int y, int z, Direction facing, int depth) {
            if (depth > MAX_PIECE_DEPTH || Math.abs(x - originX) > LEGACY_REACH || Math.abs(z - originZ) > LEGACY_REACH) {
                return null;
            }
            Planned piece = choosePiece(x, y, z, facing, depth + 1);
            if (piece != null) {
                all.add(piece);
                pendingHouses.add(piece);
            }
            return piece;
        }

        /** 原作 generateAndAddRoadPiece（L171~185） + Path.findPieceBox（L575~585）：长 7 × (3~5)，放不下就一次短 7 格 */
        private Planned addRoad(int x, int y, int z, Direction facing, int depth) {
            if (depth > MAX_ROAD_DEPTH || Math.abs(x - originX) > LEGACY_REACH || Math.abs(z - originZ) > LEGACY_REACH) {
                return null;
            }
            BoundingBox box = null;
            for (int length = 7 * (random.nextInt(3) + 3); length >= 7; length -= 7) {
                BoundingBox candidate = BoundingBox.orientBox(x, y, z, 0, 0, 0, 5, 5, length, facing);
                if (fits(candidate)) {
                    box = candidate;
                    break;
                }
            }
            if (box != null && box.minY() > 10) {
                // 原作 Path 的深度是父件的深度（L178 直接传 index），所以路的深度恒为 0
                Planned road = Planned.of(Kind.ROAD, box, facing, depth, 0, 0);
                all.add(road);
                pendingRoads.add(road);
                return road;
            }
            return null;
        }

        /**
         * 原作 generateComponent（L113~154）：按剩余权重抽 5 次，抽中的放不下就接着试表里后面的件种（原作的 for 循环没有 break），
         * 抽中的上限满了、或者和上一件同种（表里不止一种时）就重抽；5 次都不行放一根灯柱。
         */
        private Planned choosePiece(int x, int y, int z, Direction facing, int depth) {
            int total = totalWeight();
            if (total <= 0) {
                return null;
            }
            for (int attempt = 0; attempt < 5; attempt++) {
                int roll = random.nextInt(total);
                for (Weight weight : weights) {
                    roll -= weight.template.weight;
                    if (roll < 0) {
                        if (!weight.canSpawnMore() || weight == last && weights.size() > 1) {
                            break;
                        }
                        Planned piece = create(weight.template, x, y, z, facing, depth);
                        if (piece != null) {
                            weight.spawned++;
                            last = weight;
                            if (!weight.canSpawnMore()) {
                                weights.remove(weight);
                            }
                            return piece;
                        }
                    }
                }
            }
            BoundingBox torch = BoundingBox.orientBox(x, y, z, 0, 0, 0, 3, 4, 3, facing);
            return fits(torch) ? Planned.of(Kind.TORCH, torch, facing, depth, 0, 0) : null;
        }

        /** 原作 updatePieceWeight（L70~82）：还有能放的件种时返回表里的总权重，否则 −1 */
        private int totalWeight() {
            boolean any = false;
            int total = 0;
            for (Weight weight : weights) {
                if (weight.canSpawnMore()) {
                    any = true;
                }
                total += weight.template.weight;
            }
            return any ? total : -1;
        }

        /** 原作各件的 createPiece / findPieceBox：房子先抽层数再判相交（照原作的随机顺序）；House4 不查 canTownGoDeeper（原作 L486） */
        private Planned create(Template template, int x, int y, int z, Direction facing, int depth) {
            switch (template) {
                case TOWER -> {
                    BoundingBox box = BoundingBox.orientBox(x, y, z, 0, 0, 0, 9, TOWER_HEIGHT, 9, facing);
                    return box.minY() > 10 && fits(box) ? Planned.of(Kind.TOWER, box, facing, depth, 0, 0) : null;
                }
                case EXPLOSION -> {
                    BoundingBox box = BoundingBox.orientBox(x, y, z, 0, 0, 0, 5, 5, 5, facing);
                    return box.minY() > 10 && fits(box) ? Planned.of(Kind.EXPLOSION, box, facing, depth, 0, 0) : null;
                }
                case SPREADER -> {
                    BoundingBox box = BoundingBox.orientBox(x, y, z, 0, 0, 0, 3, 7, 3, facing);
                    return box.minY() > 10 && fits(box) ? Planned.of(Kind.SPREADER, box, facing, depth, 0, 0) : null;
                }
                default -> {
                    int floors = random.nextInt(template.floorRoll) + 1;
                    int side = template.radius * 2 + 1;
                    BoundingBox box = BoundingBox.orientBox(x, y, z, 0, 0, 0, side, floors * STOREY_HEIGHT + 1, side, facing);
                    boolean deepEnough = template == Template.HOUSE4 || box.minY() > 10;
                    return deepEnough && fits(box) ? Planned.of(Kind.HOUSE, box, facing, depth, floors, template.radius) : null;
                }
            }
        }

        /** 不和已放的件相交（原作 findIntersecting），并且在 {@link #TOWN_MAX_RADIUS} 以内（新增的安全网，原作最远 54 格、从没触发过） */
        private boolean fits(BoundingBox box) {
            if (Math.max(Math.abs(box.minX() - centerX), Math.abs(box.maxX() - centerX)) > TOWN_MAX_RADIUS
                    || Math.max(Math.abs(box.minZ() - centerZ), Math.abs(box.maxZ() - centerZ)) > TOWN_MAX_RADIUS) {
                return false;
            }
            for (Planned piece : all) {
                if (piece.intersects(box)) {
                    return false;
                }
            }
            return true;
        }
    }

    // ================================================================= 拼件

    /** 城镇拼件的共同父类：生成时设朝向（局部坐标按朝向换算，同原作 coordBaseMode） */
    abstract static class TownPiece extends HoannaStructurePiece {

        TownPiece(StructurePieceType type, int genDepth, BoundingBox box, Direction facing,
                  long seed) {
            super(type, genDepth, box, seed);
            setOrientation(facing);
        }

        TownPiece(StructurePieceType type, CompoundTag tag) {
            super(type, tag);
        }
    }

    /** {@code town_start}：原作 Start / Intersection，不放方块（原作 addComponentParts 只定了高度，L876~887）。包围盒是起点地面 ±3 */
    public static final class Start extends TownPiece {

        public Start(int genDepth, BoundingBox box, Direction facing, long seed) {
            super(TownStructures.TOWN_START.get(), genDepth, box, facing, seed);
        }

        public Start(CompoundTag tag) {
            super(TownStructures.TOWN_START.get(), tag);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                                BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        }
    }

    /**
     * {@code town_road}：原作 Path（L490~617）。路宽 5；逐列取地表最上面的方块（跳过上面的草花雪层），
     * 是 {@link #REPLACEABLE} 或水就换成混凝土，跳过的草花清掉。石头、树叶、别的方块不动。
     * <p>
     * 原作只看 y 62~63 两格（L593~610），地势高于海平面的地方没有路；这里跟着地形铺（batch6a §5.5）。
     * 包围盒的高度只是起点地面 ±3（只影响「在不在结构里」的判定），路面可能在包围盒上下，照样铺（postProcess 的 chunkBox 是整个区块的高度）。
     */
    public static final class Road extends TownPiece {
        /**
         * 路面能换成混凝土的方块 {@code #essentialcraft5:town_road_replaceable}（草方块、泥土、沙、红沙、砂岩、红砂岩、沙砾、恐惧泥土（可选项，
         * ECHoannaNature 没注册时跳过）；水另外判断）。放在这里而不是外层类：外层类（布局）不碰注册表，没启动游戏也能单独跑布局统计。
         */
        public static final TagKey<Block> REPLACEABLE = TagKey.create(Registries.BLOCK, EssentialCraft5.id("town_road_replaceable"));

        public Road(int genDepth, BoundingBox box, Direction facing, long seed) {
            super(TownStructures.TOWN_ROAD.get(), genDepth, box, facing, seed);
        }

        public Road(CompoundTag tag) {
            super(TownStructures.TOWN_ROAD.get(), tag);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                                BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            BlockState concrete = HoannaStructureBlocks.concrete();
            int minX = Math.max(boundingBox.minX(), chunkBox.minX());
            int maxX = Math.min(boundingBox.maxX(), chunkBox.maxX());
            int minZ = Math.max(boundingBox.minZ(), chunkBox.minZ());
            int maxZ = Math.min(boundingBox.maxZ(), chunkBox.maxZ());
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    paveColumn(level, chunkBox, pos, x, z, concrete);
                }
            }
        }

        private static void paveColumn(WorldGenLevel level, BoundingBox chunkBox, BlockPos.MutableBlockPos pos, int x, int z, BlockState concrete) {
            int top = surfaceTop(level, x, z);
            int bottom = Math.max(level.getMinBuildHeight(), top - ROAD_COVER_SKIP);
            int y = top;
            BlockState state = level.getBlockState(pos.set(x, y, z));
            while (y > bottom && isLooseCover(state)) {
                y--;
                state = level.getBlockState(pos.set(x, y, z));
            }
            if (!chunkBox.isInside(pos) || !(state.is(Blocks.WATER) || state.is(REPLACEABLE))) {
                return;
            }
            level.setBlock(pos, concrete, Block.UPDATE_CLIENTS);
            for (int above = y + 1; above <= top; above++) {
                if (chunkBox.isInside(pos.set(x, above, z))) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }

        /**
         * 路面上要跳过、铺路时清掉的：草丛、花、雪层这类非流体方块。花（原版 1.20 的 FlowerBlock 不带 replaceable）、蘑菇、树苗、
         * 海纳尔绿花都是 {@link BushBlock}，单靠 {@code canBeReplaced()} 会漏掉（集成时 GameTest townRoadFollowsSurface 挂在罂粟上）。
         */
        private static boolean isLooseCover(BlockState state) {
            return !state.isAir() && state.getFluidState().isEmpty() && (state.canBeReplaced() || state.getBlock() instanceof BushBlock);
        }
    }

    /**
     * 这一列最上面的非空气方块的 y。生成中的区块用 {@code WORLD_SURFACE_WG}；完整区块（{@code /place}、GameTest）没有 WG 高度图，
     * 用同样判据（非空气）的 {@code WORLD_SURFACE}，免得完整区块现建高度图、在开发环境里报「Unprimed heightmap」。
     */
    static int surfaceTop(WorldGenLevel level, int x, int z) {
        ChunkAccess chunk = level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        Heightmap.Types type = chunk instanceof LevelChunk ? Heightmap.Types.WORLD_SURFACE : Heightmap.Types.WORLD_SURFACE_WG;
        return chunk.getHeight(type, x, z);
    }

    /**
     * {@code town_tower}：原作 Tower（L187~280）。4 层（宽 9 / 7 / 5 / 3，每层高 16），逐层往里收一格；每层：四角强化石栅栏立柱、
     * 中心四周四根魔力栅栏、角上 L 形强化石柱、dy = 8 一圈强化石环；最顶层（宽 3）的环上放 4 个 {@code chests/town_tower} 箱子（离地 57 格）。
     * 最底层的栅栏和角柱往下补到地面（{@link #fillDown}，穿过草丛和水）。
     * <p>
     * 原作 bug 顺手修：角柱往下补的 8 格里原作把 (7, 0) 写成了 (8, 0)（L270，(8, 0) 已经是角上的栅栏柱，这一句等于没写），
     * 有一个角少一根往下补的柱子，地面不平时那一角悬空。改成 (7, 0)，四角对称（纯外观，不影响玩法）。
     */
    public static final class Tower extends TownPiece {

        public Tower(int genDepth, BoundingBox box, Direction facing, long seed) {
            super(TownStructures.TOWN_TOWER.get(), genDepth, box, facing, seed);
        }

        public Tower(CompoundTag tag) {
            super(TownStructures.TOWN_TOWER.get(), tag);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                                BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            BlockState magicFence = ECAlloys.MAGIC_FENCE.get().defaultBlockState();          // 原作 fence[1]
            BlockState stoneFence = ECAlloys.FORTIFIED_STONE_FENCE.get().defaultBlockState(); // 原作 fence[2]
            BlockState fortified = ECMaterials.FORTIFIED_STONE.get().defaultBlockState();
            for (int i = 0; i < 4; i++) {
                int rad = 4 - i;
                int far = rad * 2;
                for (int dy = 0; dy < 16; dy++) {
                    int y = i * 16 + dy;
                    for (int dx = 0; dx <= far; dx++) {
                        for (int dz = 0; dz <= far; dz++) {
                            if ((dx == rad + 1 || dx == rad - 1) && (dz == rad + 1 || dz == rad - 1)) {
                                placeBlock(level, magicFence, i + dx, y, i + dz, chunkBox);
                                if (rad == 4 && dy == 0) {
                                    fillDown(level, magicFence, dx, -1, dz, chunkBox);
                                }
                            }
                            if ((dx == far || dx == 0 || dz == 0 || dz == far) && dy == 8) {
                                placeBlock(level, fortified, i + dx, y, i + dz, chunkBox);
                                if (rad == 1 && (dx == rad || dz == rad)) {
                                    createChest(level, chunkBox, random, i + dx, y + 1, i + dz, HoannaLoot.TOWN_TOWER);
                                }
                            }
                            if ((dx == 0 || dx == far) && (dz == 0 || dz == far)) {
                                placeBlock(level, stoneFence, i + dx, y, i + dz, chunkBox);
                                if (dy == 0 && rad == 4) {
                                    fillDown(level, stoneFence, dx, -1, dz, chunkBox);
                                }
                            }
                        }
                    }
                    if (rad > 1) {
                        cornerPillars(level, fortified, i, far, y, dy == 15, chunkBox);
                    }
                }
                if (rad == 4) {
                    int[][] feet = {{1, 0}, {0, 1}, {7, 0}, {8, 1}, {1, 8}, {0, 7}, {7, 8}, {8, 7}};
                    for (int[] foot : feet) {
                        fillDown(level, fortified, foot[0], -1, foot[1], chunkBox);
                    }
                }
            }
        }

        /** 原作 L240~264：四个角旁边各两格强化石（每一层都放），每层最高一格再补角内那一格 */
        private void cornerPillars(WorldGenLevel level, BlockState fortified, int i, int far, int y, boolean top, BoundingBox chunkBox) {
            placeBlock(level, fortified, i + 1, y, i, chunkBox);
            placeBlock(level, fortified, i, y, i + 1, chunkBox);
            placeBlock(level, fortified, i + far - 1, y, i, chunkBox);
            placeBlock(level, fortified, i + far, y, i + 1, chunkBox);
            placeBlock(level, fortified, i + 1, y, i + far, chunkBox);
            placeBlock(level, fortified, i, y, i + far - 1, chunkBox);
            placeBlock(level, fortified, i + far - 1, y, i + far, chunkBox);
            placeBlock(level, fortified, i + far, y, i + far - 1, chunkBox);
            if (top) {
                placeBlock(level, fortified, i + 1, y, i + 1, chunkBox);
                placeBlock(level, fortified, i + far - 1, y, i + 1, chunkBox);
                placeBlock(level, fortified, i + 1, y, i + far - 1, chunkBox);
                placeBlock(level, fortified, i + far - 1, y, i + far - 1, chunkBox);
            }
        }
    }

    /**
     * {@code town_house}：原作 House / House1~4（L344~488）合一，字段 {@link #floors()} F、{@link #radius()} r。
     * <p>
     * 外壳照原作：共 F + 1 层（原作循环 i = 0..F，包含 F；最上一层超出原作自己的包围盒，但照样盖了——这里包围盒按实际盖到的高度算），
     * 每层 5 格高：dy 0 / dy 4 强化石楼板，四壁强化石，dy 1~3 的墙中段是强化玻璃窗；室内碎石是混凝土，概率 1 / (dy + 1)；
     * 一楼四角的地面下一格各一个悬浮器（房子「浮」在悬浮器上）。原作「不是水才清空」查的是 (dx, dy, dz)（L394，漏了 i × 5），这里按实际高度查。
     * <p>
     * <b>被毁</b>（D14 A）：原作每一格有 i / (10F) 的概率就地引爆一次半径 3 + i/3 的爆炸（顶层约每 10 格一次，每镇约 650~1 300 次，
     * 炸进邻区块、结果看区块生成顺序）。这里删掉逐格爆炸，i ≥ 1 的楼层按层缺损（{@link StructureRuin#decay}）：碎石 0.6 × i / F、
     * 墙和楼板（强化石、强化玻璃）{@value #SHELL_DECAY} × i / F 的概率变空气，一楼不动；另由 {@link HoannaTownStructure} 追加每栋 ≤ 3 个确定性弹坑（{@link Crater}）。
     * 缺损是逐格独立抽的，只会出零散的单格小洞；成团的炸洞靠弹坑（房子的坑对强化石 / 强化玻璃另给系数，见 {@link Crater}），
     * 所以墙和楼板的缺损从 0.35 降到 {@value #SHELL_DECAY}：再高的话满墙都是均匀的小洞，弹坑的洞反而看不出来。
     * <p>
     * 局部坐标：y 0 是悬浮器那一行（地面下一格），一楼楼板在 {@link #HOUSE_FLOOR_Y}。
     */
    public static final class House extends TownPiece {
        private static final String TAG_FLOORS = "F";
        private static final String TAG_RADIUS = "R";
        /** 缺损：碎石的最大概率（顶层） */
        public static final float DEBRIS_DECAY = 0.6F;
        /** 缺损：墙和楼板的最大概率（顶层） */
        public static final float SHELL_DECAY = 0.25F;

        private final int floors;
        private final int radius;

        public House(int genDepth, BoundingBox box, Direction facing, long seed, int floors, int radius) {
            super(TownStructures.TOWN_HOUSE.get(), genDepth, box, facing, seed);
            this.floors = floors;
            this.radius = radius;
        }

        public House(CompoundTag tag) {
            super(TownStructures.TOWN_HOUSE.get(), tag);
            this.floors = tag.getInt(TAG_FLOORS);
            this.radius = tag.getInt(TAG_RADIUS);
        }

        @Override
        protected void saveExtra(StructurePieceSerializationContext context, CompoundTag tag) {
            tag.putInt(TAG_FLOORS, floors);
            tag.putInt(TAG_RADIUS, radius);
        }

        public int floors() {
            return floors;
        }

        public int radius() {
            return radius;
        }

        /** F 层的房子包围盒的高度：悬浮器一行 + (F + 1) 层 × 5 */
        public static int height(int floors) {
            return HOUSE_FLOOR_Y + (floors + 1) * STOREY_HEIGHT;
        }

        /** 第 i 层（0 起）楼板的世界 y */
        public int storeyY(int storey) {
            return getWorldY(HOUSE_FLOOR_Y + storey * STOREY_HEIGHT);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                                BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            Block fortifiedBlock = ECMaterials.FORTIFIED_STONE.get();
            Block glassBlock = ECMaterials.FORTIFIED_GLASS.get();
            BlockState fortified = fortifiedBlock.defaultBlockState();
            BlockState glass = glassBlock.defaultBlockState();
            BlockState concrete = HoannaStructureBlocks.concrete();
            BlockState levitator = HoannaStructureBlocks.levitator();
            BlockState air = Blocks.AIR.defaultBlockState();
            int far = radius * 2;
            for (int i = 0; i <= floors; i++) {
                for (int dx = 0; dx <= far; dx++) {
                    for (int dz = 0; dz <= far; dz++) {
                        // 悬浮器没注册时（模块副本）退回空气：别在地面上挖洞，干脆不放
                        if (i == 0 && (dx == 0 || dx == far) && (dz == 0 || dz == far) && !levitator.isAir()) {
                            placeBlock(level, levitator, dx, 0, dz, chunkBox);
                        }
                        for (int dy = 0; dy < STOREY_HEIGHT; dy++) {
                            int y = HOUSE_FLOOR_Y + i * STOREY_HEIGHT + dy;
                            BlockState state = cell(level, chunkBox, dx, y, dz, dy, far, fortified, glass, concrete, air);
                            if (state != null) {
                                placeBlock(level, state, dx, y, dz, chunkBox);
                            }
                        }
                    }
                }
            }
            Block concreteBlock = concrete.getBlock();
            for (int i = 1; i <= floors; i++) {
                float share = i / (float) floors;
                int floorY = storeyY(i);
                BoundingBox storey = new BoundingBox(boundingBox.minX(), floorY, boundingBox.minZ(),
                        boundingBox.maxX(), floorY + STOREY_HEIGHT - 1, boundingBox.maxZ());
                StructureRuin.decay(level, chunkBox, storey, seed(), state -> state.is(concreteBlock) ? DEBRIS_DECAY * share
                        : state.is(fortifiedBlock) || state.is(glassBlock) ? SHELL_DECAY * share : 0F);
            }
        }

        /**
         * 一格最后是什么（原作 L393~415 依次写空气、碎石、楼板、墙、窗，后写的盖前写的；这里一次算出结果只写一次）。返回 null = 不动（室内的水没抽到碎石时留着）。
         */
        private BlockState cell(WorldGenLevel level, BoundingBox chunkBox, int dx, int y, int dz, int dy, int far, BlockState fortified,
                                BlockState glass, BlockState concrete, BlockState air) {
            boolean xWall = dx == 0 || dx == far;
            boolean zWall = dz == 0 || dz == far;
            if (xWall || zWall) {
                boolean window = dy > 0 && dy < 4 && (xWall && !zWall && dz > 1 && dz < far - 1 || zWall && !xWall && dx > 1 && dx < far - 1);
                return window ? glass : fortified;
            }
            if (dy == 0 || dy == 4) {
                return fortified;
            }
            if (localUnit(dx, y, dz, DEBRIS_SALT) * (dy + 1) < 1F) {
                return concrete;
            }
            return getBlock(level, dx, y, dz, chunkBox).is(Blocks.WATER) ? null : air;
        }
    }

    /**
     * {@code town_spreader}：原作 Spreader（L282~342）。3 × 3 的强化石底座（正中那格地面是空的就换魔力护板），上面正中一块魔力护板，
     * 四角强化石栅栏（4 格高）、正中魔力栅栏立柱（5 格高），顶上一个 MRU 散射器。
     * 局部坐标：y 0 是底座（原作 y −1，嵌在地面里），y 1 起是原作的 dy 0。
     */
    public static final class Spreader extends TownPiece {

        public Spreader(int genDepth, BoundingBox box, Direction facing, long seed) {
            super(TownStructures.TOWN_SPREADER.get(), genDepth, box, facing, seed);
        }

        public Spreader(CompoundTag tag) {
            super(TownStructures.TOWN_SPREADER.get(), tag);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                                BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            BlockState air = Blocks.AIR.defaultBlockState();
            BlockState plating = ECAlloys.MAGIC_PLATING.get().defaultBlockState();
            BlockState fortified = ECMaterials.FORTIFIED_STONE.get().defaultBlockState();
            BlockState magicFence = ECAlloys.MAGIC_FENCE.get().defaultBlockState();
            BlockState stoneFence = ECAlloys.FORTIFIED_STONE_FENCE.get().defaultBlockState();
            BlockState spreader = HoannaStructureBlocks.spreader();
            generateBox(level, chunkBox, 0, 1, 0, 2, 7, 2, air, air, false);
            for (int dx = 0; dx < 3; dx++) {
                for (int dz = 0; dz < 3; dz++) {
                    boolean center = dx == 1 && dz == 1;
                    boolean corner = (dx == 0 || dx == 2) && (dz == 0 || dz == 2);
                    if (center) {
                        placeBlock(level, plating, 1, 1, 1, chunkBox);
                        placeBlock(level, getBlock(level, 1, 0, 1, chunkBox).isAir() ? plating : fortified, 1, 0, 1, chunkBox);
                        for (int dy = 1; dy < 6; dy++) {
                            placeBlock(level, magicFence, 1, dy + 1, 1, chunkBox);
                        }
                        if (!spreader.isAir()) {
                            placeBlock(level, spreader, 1, 7, 1, chunkBox);
                        }
                    } else {
                        placeBlock(level, fortified, dx, 0, dz, chunkBox);
                        if (corner) {
                            for (int dy = 1; dy < 5; dy++) {
                                placeBlock(level, stoneFence, dx, dy + 1, dz, chunkBox);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * {@code town_torch}：原作 Torch（L676~715），灯柱。3 根虚空栅栏、顶上一块虚空石、旁边 4 个魔力光源（看不见的光源方块，亮度 15）。
     * 光源位置照原作（(0, 3, 1)、(1, 3, 0)、(0, 3, 2)、(2, 3, 0)，有两个在斜角上，看不见，不影响观感）。
     */
    public static final class Torch extends TownPiece {

        public Torch(int genDepth, BoundingBox box, Direction facing, long seed) {
            super(TownStructures.TOWN_TORCH.get(), genDepth, box, facing, seed);
        }

        public Torch(CompoundTag tag) {
            super(TownStructures.TOWN_TORCH.get(), tag);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                                BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            BlockState air = Blocks.AIR.defaultBlockState();
            BlockState fence = HoannaStructureBlocks.voidFence();
            BlockState light = ECEssence.MAGIC_LIGHT.get().defaultBlockState();
            generateBox(level, chunkBox, 0, 0, 0, 2, 3, 2, air, air, false);
            for (int y = 0; y < 3; y++) {
                placeBlock(level, fence, 1, y, 1, chunkBox);
            }
            placeBlock(level, HoannaStructureBlocks.voidStone(), 1, 3, 1, chunkBox);
            placeBlock(level, light, 0, 3, 1, chunkBox);
            placeBlock(level, light, 1, 3, 0, chunkBox);
            placeBlock(level, light, 0, 3, 2, chunkBox);
            placeBlock(level, light, 2, 3, 0, chunkBox);
        }
    }

    /**
     * {@code town_crater}：一个确定性弹坑（{@link StructureRuin#carveCrater}），替代原作的 {@code ECExplosion}。
     * 房子的坑（每栋 ≤ 3 个，半径 3 + 层 / 3）和爆炸件的坑（半径 15，在地面）都用它；包围盒 = {@link StructureRuin#craterBounds}，
     * 只有和它相交的区块才调 postProcess。放在拼件表末尾：同一区块里先盖完房子、铺完路再挖。坑底不低于海平面。
     * <p>
     * <b>房子的坑</b>（有 {@link #floorY()} 的）另有两条规则：
     * <ul>
     *   <li>强化石、强化玻璃的有效半径系数用 {@value #HOUSE_SHELL_FACTOR}（{@link #houseFactor}），别的方块照抗爆分档。这两种方块抗爆 9，
     *       按分档是 {@link StructureRuin#K_HARD} 0.2，半径 3~6 的坑在墙里的有效半径只有 0.51~1.38 格，一个坑只啃掉一两格，
     *       楼里看到的破损几乎全是按层缺损的零散小洞、像一张均匀的网；0.5 时有效半径约 1.3~3.5 格，上层会有几处成团的炸洞。</li>
     *   <li>只挖 {@link #floorY()}（房子二楼楼板 = 地面 + 5）及以上：F = 1 / 2 的房子坑的楼层最低是 1（二楼），坑心在地面 + 5~9，
     *       系数放大以后坑心落在地面 + 5 的（每个坑五分之一）会把一楼天花板（地面 + 4）和墙顶打穿；F ≥ 3 的坑从三楼起，碰不到一楼。
     *       裁掉以后房子自己的坑和缺损都不碰一楼，看上去是「底下完好、越往上越破」。</li>
     * </ul>
     * 房子的坑在 NBT 里多存一个 {@value #TAG_FLOOR}，有它才按上面两条挖。旧存档里的坑没有这一项，照旧按抗爆分档、只按海平面裁——
     * 已经挖了一半的坑，剩下的区块和前面挖法一致，不会在区块边上断开。
     */
    public static final class Crater extends HoannaStructurePiece {
        private static final String TAG_X = "CX";
        private static final String TAG_Y = "CY";
        private static final String TAG_Z = "CZ";
        private static final String TAG_RADIUS = "Rad";
        private static final String TAG_FLOOR = "Floor";
        /** 房子的坑里强化石、强化玻璃的有效半径系数 */
        public static final float HOUSE_SHELL_FACTOR = 0.5F;
        /** 爆炸件的坑：没有挖掘下限（只按海平面） */
        public static final int NO_FLOOR = Integer.MIN_VALUE;

        private final BlockPos center;
        private final float radius;
        private final int floorY;

        /** 爆炸件的坑：所有方块照抗爆分档，挖到海平面为止 */
        public Crater(int genDepth, BlockPos center, float radius, long seed) {
            this(genDepth, center, radius, seed, NO_FLOOR);
        }

        /**
         * floorY 不是 {@link #NO_FLOOR} 时是房子的坑：强化石、强化玻璃按 {@value #HOUSE_SHELL_FACTOR}，floorY 以下不挖。
         * 包围盒照旧是 {@link StructureRuin#craterBounds}（下限只少挖，不会挖出包围盒）。
         */
        public Crater(int genDepth, BlockPos center, float radius, long seed, int floorY) {
            super(TownStructures.TOWN_CRATER.get(), genDepth, StructureRuin.craterBounds(center, radius), seed);
            this.center = center.immutable();
            this.radius = radius;
            this.floorY = floorY;
        }

        public Crater(CompoundTag tag) {
            super(TownStructures.TOWN_CRATER.get(), tag);
            this.center = new BlockPos(tag.getInt(TAG_X), tag.getInt(TAG_Y), tag.getInt(TAG_Z));
            this.radius = tag.getFloat(TAG_RADIUS);
            this.floorY = tag.contains(TAG_FLOOR, Tag.TAG_INT) ? tag.getInt(TAG_FLOOR) : NO_FLOOR;
        }

        @Override
        protected void saveExtra(StructurePieceSerializationContext context, CompoundTag tag) {
            tag.putInt(TAG_X, center.getX());
            tag.putInt(TAG_Y, center.getY());
            tag.putInt(TAG_Z, center.getZ());
            tag.putFloat(TAG_RADIUS, radius);
            if (floorY != NO_FLOOR) {
                tag.putInt(TAG_FLOOR, floorY);
            }
        }

        public BlockPos center() {
            return center;
        }

        public float radius() {
            return radius;
        }

        /** 挖掘下限（世界 y，这一格也挖）；爆炸件的坑是 {@link #NO_FLOOR} */
        public int floorY() {
            return floorY;
        }

        /** 是不是房子的坑（有挖掘下限） */
        public boolean isHouseCrater() {
            return floorY != NO_FLOOR;
        }

        /** 房子的坑里这一格的有效半径系数：强化石、强化玻璃 {@value #HOUSE_SHELL_FACTOR}，其余照 {@link StructureRuin#hardnessFactor} */
        public static float houseFactor(BlockState state) {
            return state.is(ECMaterials.FORTIFIED_STONE.get()) || state.is(ECMaterials.FORTIFIED_GLASS.get())
                    ? HOUSE_SHELL_FACTOR : StructureRuin.hardnessFactor(state);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                                BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            int seaLevel = generator.getSeaLevel();
            if (isHouseCrater()) {
                StructureRuin.carveCrater(level, chunkBox, center, radius, seed(), Math.max(seaLevel, floorY), Crater::houseFactor);
            } else {
                StructureRuin.carveCrater(level, chunkBox, center, radius, seed(), seaLevel);
            }
        }
    }
}
