package com.flameforge.essentialcraft5.worldgen.hoanna.underground;

import com.flameforge.essentialcraft5.loot.HoannaLoot;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.worldgen.hoanna.HoannaStructureBlocks;
import com.flameforge.essentialcraft5.worldgen.hoanna.HoannaStructurePiece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePieceAccessor;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntBinaryOperator;

/**
 * 古代墓穴的拼件（原作 {@code StructureOldCatacombs}，442 行；M6，batch6a §5.6）：房间 {@link Room}、走廊 {@link Corridor}、出口烟囱 {@link Exit}。
 * <p>
 * <b>布局</b>（{@link #layout}，照原作）：起点是一间 5 × 5 × 5 的强化石房间，向四个方向（来的那边除外）各试一条 5 × 5 × 32~63 的走廊，
 * 走廊尽头再试一间房间……房间深度 ≤ {@value #MAX_ROOM_DEPTH} 才出走廊、走廊深度 ≤ {@value #MAX_CORRIDOR_DEPTH} 才出房间，
 * 和已有的件相交、或超出起点房间中心 {@value #CATACOMB_MAX_RADIUS} 格（切比雪夫距离，按包围盒最远的边算）就不放。所有件同一高度。
 * 房间三种标记：破损 1/8（室内混凝土碎块）、长草 1/8（根系 + 不会腐烂的橡树叶）、出口 {@link #EXIT_CHANCE}（原作 1/16，D13 改 1/10）。
 * <p>
 * <b>出口</b>（{@link #addExits}）：出口房间 (2, 2, 2) 放 {@code chests/catacombs} 箱子、下面 (2, 1, 2) 垫一块虚空石；
 * 房间正上方按噪声地面高度（{@code WORLD_SURFACE_WG} 的第一格空位，只在布局阶段算一次）加一个 {@link Exit} 烟囱件：5 × 5 强化石壳、
 * 一侧整列梯子，从房顶一直通到地面上一格（露出地面一圈强化石，就是入口的标志）。地面不比房顶高（超平坦之类）就不加烟囱，箱子照放。
 * <p>
 * <b>修掉的原作问题</b>（batch6a §8）：
 * <ul>
 *   <li>向南的走廊把下一间房放在走廊起点的东侧（L316），不连通 → 放在走廊南端 (minX, minY, maxZ + 1)。</li>
 *   <li>长草房间的根系三个坐标都用了 rootVec.x，vi = 0 时除以 0（L232~235）→ 按推断意图画一条直线 (3 + rx·vi/6, 1 − vi, 3 + rz·vi/6)，
 *       水平方向夹在房间的 0~4 里（不伸到房间所在的区块以外）；根系方块没注册时不放（不然会在地板上挖洞）。</li>
 *   <li>出口烟囱的高度在每个区块各搜一遍（L203~212），房间跨两个区块时只有一边搜得到地面、另一边只有 10 格高 → 布局时算一次存进拼件。</li>
 *   <li>破损（混凝土）和长草（树叶）会把出口房间的箱子、垫底的虚空石换掉（原作破损房约 1/5、长草房约 1/3 的箱子没了）→ 出口的东西最后放；
 *       箱子正上方那格不放碎块（压着实心方块的箱子打不开）；箱子正面朝梯子（原作按四周的墙自动定朝向）。</li>
 *   <li>梯子只从房顶那层开始（L213），站在地板上够不着 → 梯子那一面墙没开口时一直通到地板；四面都开口时照原作从房顶开始（站到箱子上能跳上去）。</li>
 *   <li>最远 267 格会被 1.20.1 的 8 区块引用范围截断（半截走廊）→ 半径上限 112 格。</li>
 *   <li>随机按区块播种：同一间房跨两个区块时两边抽出来的碎块、树叶对不上 → 全部按位置哈希（{@link HoannaStructurePiece#unitAt}），分几次生成结果都一样。</li>
 * </ul>
 * 所有件的朝向都是 null（局部坐标 = 世界坐标，方块状态不镜像），写方块一律经过 chunkBox（只写正在生成的区块）。
 */
public final class OldCatacombsPieces {

    /** 起点房间的高度范围（原作 {@code MathHelper.getInt(rand, 6, 32)}，1.12 的绝对高度） */
    public static final int MIN_Y = 6;
    public static final int MAX_Y = 32;
    /** 房间边长 */
    public static final int ROOM_SIZE = 5;
    /** 走廊长度 32~63（原作 {@code nextInt(64 - 32) + 32}） */
    public static final int CORRIDOR_MIN_LENGTH = 32;
    public static final int CORRIDOR_MAX_LENGTH = 63;
    /** 房间深度不超过它才往外出走廊（原作 {@code index > 7} 返回） */
    public static final int MAX_ROOM_DEPTH = 7;
    /** 走廊深度不超过它才在尽头放房间（原作 {@code index > 6} 返回） */
    public static final int MAX_CORRIDOR_DEPTH = 6;
    /** 件离起点房间中心的最远距离（切比雪夫，格）：1.20.1 只在起点周围 8 区块内引用拼件，超出的会被截断（D13） */
    public static final int CATACOMB_MAX_RADIUS = 112;
    /** 破损房间（室内混凝土碎块）的概率（原作 L67） */
    public static final double BROKEN_CHANCE = 0.125D;
    /** 长草房间（根系 + 树叶）的概率（原作 L68） */
    public static final double GROWN_CHANCE = 0.125D;
    /** 出口房间（箱子 + 通到地面的烟囱）的概率（原作 1/16，D13 A 改 1/10：箱子总量约原作的 89%，没入口的墓穴从 40% 降到 22%） */
    public static final double EXIT_CHANCE = 0.1D;

    // 位置哈希的 salt（同一拼件里互不相关的几种随机）
    private static final long SALT_RUBBLE = 0x5EEDC0DE0001L;
    private static final long SALT_LEAVES = 0x5EEDC0DE0002L;
    private static final long SALT_ROOT = 0x5EEDC0DE0003L;
    private static final long SALT_EXIT = 0x5EEDC0DE0004L;

    private OldCatacombsPieces() {
    }

    // ---------------------------------------------------------------- 布局

    /**
     * 从起点房间 (x, y, z)（最小角）开始布局，件依次加进 pieces（深度优先，和原作的递归顺序一样）。只算包围盒和标记，不碰世界。
     *
     * @return 起点房间
     */
    public static Room layout(StructurePieceAccessor pieces, RandomSource random, int x, int y, int z) {
        Room start = new Room(0, random, new BoundingBox(x, y, z, x + ROOM_SIZE - 1, y + ROOM_SIZE - 1, z + ROOM_SIZE - 1), null);
        pieces.addPiece(start);
        start.addChildren(start, pieces, random);
        return start;
    }

    /**
     * 给每个出口房间加烟囱件：surfaceTop(x, z) 返回那一列地面上第一格空位的高度（结构生成时是
     * {@code getFirstFreeHeight(…, WORLD_SURFACE_WG, …)}，按噪声算、不要区块已生成），高过房顶才加。每个出口房间只算一次。
     *
     * @return 加了几个烟囱
     */
    public static int addExits(StructurePiecesBuilder builder, IntBinaryOperator surfaceTop) {
        List<Exit> exits = new ArrayList<>();
        for (StructurePiece piece : builder.build().pieces()) {
            if (piece instanceof Room room && room.isExit()) {
                BlockPos center = room.getBoundingBox().getCenter();
                int top = surfaceTop.applyAsInt(center.getX(), center.getZ());
                if (top > room.getBoundingBox().maxY()) {
                    exits.add(new Exit(room, top));
                }
            }
        }
        exits.forEach(builder::addPiece);
        return exits.size();
    }

    /** 包围盒离起点房间中心不超过 {@link #CATACOMB_MAX_RADIUS}（切比雪夫距离，按最远的边） */
    public static boolean withinRadius(StructurePiece start, BoundingBox box) {
        BlockPos center = start.getBoundingBox().getCenter();
        return radiusOf(center, box) <= CATACOMB_MAX_RADIUS;
    }

    /** 包围盒最远的边离 center 的切比雪夫距离（水平） */
    public static int radiusOf(BlockPos center, BoundingBox box) {
        int dx = Math.max(Math.abs(box.minX() - center.getX()), Math.abs(box.maxX() - center.getX()));
        int dz = Math.max(Math.abs(box.minZ() - center.getZ()), Math.abs(box.maxZ() - center.getZ()));
        return Math.max(dx, dz);
    }

    private static boolean accept(StructurePieceAccessor pieces, StructurePiece start, BoundingBox box) {
        return withinRadius(start, box) && pieces.findCollisionPiece(box) == null;
    }

    /**
     * 出口梯子挂在哪面墙上：按 西、东、北、南 找第一面没开口的墙（原作固定挂西墙）；四面都开口时还是西墙（梯子只从房顶那层开始）。
     * 房间的箱子正面朝这面墙（朝着顺梯子下来的人）。
     */
    public static Direction ladderSide(int openings) {
        for (Direction side : new Direction[]{Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH}) {
            if ((openings & bit(side)) == 0) {
                return side;
            }
        }
        return Direction.WEST;
    }

    static int bit(Direction side) {
        return 1 << side.get2DDataValue();
    }

    private static BlockState shell() {
        return ECMaterials.FORTIFIED_STONE.get().defaultBlockState();
    }

    // ---------------------------------------------------------------- 房间

    /**
     * 墓穴房间：5 × 5 × 5 强化石壳，室内空气，连着走廊的那几面墙开 3 × 3 的口。
     * NBT：{@code From}（进来的那面，2D 编号，起点 −1）、{@code Broken}、{@code Grown}、{@code Exit}、{@code Open}（开口位：南 1、西 2、北 4、东 8）。
     */
    public static final class Room extends HoannaStructurePiece {

        @Nullable
        private final Direction from;
        private final boolean broken;
        private final boolean grown;
        private final boolean exit;
        private int openings;

        /** 布局时新建：依次抽种子、破损、长草、出口（和原作的抽取顺序一样，种子多抽一次） */
        public Room(int genDepth, RandomSource random, BoundingBox box, @Nullable Direction from) {
            super(UndergroundStructures.CATACOMB_ROOM.get(), genDepth, box, random.nextLong());
            this.from = from;
            this.broken = random.nextDouble() < BROKEN_CHANCE;
            this.grown = random.nextDouble() < GROWN_CHANCE;
            this.exit = random.nextDouble() < EXIT_CHANCE;
            if (from != null) {
                open(from);
            }
        }

        /** 指定标记（测试、展示厅用） */
        public Room(int genDepth, long seed, BoundingBox box, @Nullable Direction from, boolean broken, boolean grown, boolean exit) {
            super(UndergroundStructures.CATACOMB_ROOM.get(), genDepth, box, seed);
            this.from = from;
            this.broken = broken;
            this.grown = grown;
            this.exit = exit;
            if (from != null) {
                open(from);
            }
        }

        /** 读档 */
        public Room(CompoundTag tag) {
            super(UndergroundStructures.CATACOMB_ROOM.get(), tag);
            int dir = tag.getInt("From");
            this.from = dir < 0 ? null : Direction.from2DDataValue(dir);
            this.broken = tag.getBoolean("Broken");
            this.grown = tag.getBoolean("Grown");
            this.exit = tag.getBoolean("Exit");
            this.openings = tag.getByte("Open") & 15;
        }

        @Override
        protected void saveExtra(StructurePieceSerializationContext context, CompoundTag tag) {
            tag.putInt("From", from == null ? -1 : from.get2DDataValue());
            tag.putBoolean("Broken", broken);
            tag.putBoolean("Grown", grown);
            tag.putBoolean("Exit", exit);
            tag.putByte("Open", (byte) openings);
        }

        @Nullable
        public Direction from() {
            return from;
        }

        public boolean isBroken() {
            return broken;
        }

        public boolean isGrown() {
            return grown;
        }

        public boolean isExit() {
            return exit;
        }

        /** 开口位：南 1、西 2、北 4、东 8 */
        public int openings() {
            return openings;
        }

        public boolean isOpen(Direction side) {
            return (openings & bit(side)) != 0;
        }

        /** 在 side 那面墙开口（测试里摆两间相连的房间用） */
        public void open(Direction side) {
            openings |= bit(side);
        }

        /** 找位置（原作 {@code Room.getValidPlacement}）：facing 是走廊的方向，房间接在走廊尽头；相交或超出半径返回 null */
        @Nullable
        static BoundingBox findPlacement(StructurePieceAccessor pieces, StructurePiece start, int x, int y, int z, Direction facing) {
            int s = ROOM_SIZE - 1;
            BoundingBox box = switch (facing) {
                case WEST -> new BoundingBox(x - s, y, z, x, y + s, z + s);
                case NORTH -> new BoundingBox(x, y, z - s, x + s, y + s, z);
                default -> new BoundingBox(x, y, z, x + s, y + s, z + s);   // SOUTH、EAST
            };
            return accept(pieces, start, box) ? box : null;
        }

        @Override
        public void addChildren(StructurePiece start, StructurePieceAccessor pieces, RandomSource random) {
            if (genDepth > MAX_ROOM_DEPTH) {
                return;
            }
            BoundingBox b = boundingBox;
            if (from != Direction.SOUTH) {
                branch(start, pieces, random, Direction.SOUTH, b.minX(), b.minY(), b.maxZ() + 1);
            }
            if (from != Direction.WEST) {
                branch(start, pieces, random, Direction.WEST, b.minX() - 1, b.minY(), b.minZ());
            }
            if (from != Direction.NORTH) {
                branch(start, pieces, random, Direction.NORTH, b.minX(), b.minY(), b.minZ() - 1);
            }
            if (from != Direction.EAST) {
                branch(start, pieces, random, Direction.EAST, b.maxX() + 1, b.minY(), b.minZ());
            }
        }

        private void branch(StructurePiece start, StructurePieceAccessor pieces, RandomSource random, Direction dir, int x, int y, int z) {
            BoundingBox box = Corridor.findPlacement(pieces, start, random, x, y, z, dir);
            if (box != null) {
                Corridor corridor = new Corridor(genDepth + 1, random, box, dir);
                pieces.addPiece(corridor);
                corridor.addChildren(start, pieces, random);
                open(dir);
            }
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                                BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            int x0 = boundingBox.minX();
            int y0 = boundingBox.minY();
            int z0 = boundingBox.minZ();
            generateBox(level, chunkBox, x0, y0, z0, x0 + 4, y0 + 4, z0 + 4, shell(), CAVE_AIR, false);
            if (isOpen(Direction.SOUTH)) {
                generateAirBox(level, chunkBox, x0 + 1, y0 + 1, z0 + 4, x0 + 3, y0 + 3, z0 + 4);
            }
            if (isOpen(Direction.WEST)) {
                generateAirBox(level, chunkBox, x0, y0 + 1, z0 + 1, x0, y0 + 3, z0 + 3);
            }
            if (isOpen(Direction.NORTH)) {
                generateAirBox(level, chunkBox, x0 + 1, y0 + 1, z0, x0 + 3, y0 + 3, z0);
            }
            if (isOpen(Direction.EAST)) {
                generateAirBox(level, chunkBox, x0 + 4, y0 + 1, z0 + 1, x0 + 4, y0 + 3, z0 + 3);
            }
            if (broken) {
                placeRubble(level, chunkBox, x0, y0, z0);
            }
            if (grown) {
                overgrow(level, chunkBox, x0, y0, z0);
            }
            if (exit) {
                // 出口的东西最后放：碎块、树叶都盖不掉它们（原作先放箱子，破损 / 长草会把箱子换掉）
                placeBlock(level, HoannaStructureBlocks.voidStone(), x0 + 2, y0 + 1, z0 + 2, chunkBox);
                BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, ladderSide(openings));
                createChest(level, chunkBox, random, new BlockPos(x0 + 2, y0 + 2, z0 + 2), HoannaLoot.CATACOMBS, chest);
            }
        }

        /**
         * 室内 3 × 3 × 3 的混凝土碎块：越低越多（第 j 层概率 1/(j + 3)，原作 L219~229）。
         * 出口房间箱子正上方那格不放（压着实心方块的箱子打不开）；箱子和它下面的虚空石之后才放，碎块盖不掉。
         */
        private void placeRubble(WorldGenLevel level, BoundingBox chunkBox, int x0, int y0, int z0) {
            BlockState concrete = HoannaStructureBlocks.concrete();
            for (int i = 1; i < 4; i++) {
                for (int j = 1; j < 4; j++) {
                    for (int k = 1; k < 4; k++) {
                        if (exit && i == 2 && k == 2 && j == 3) {
                            continue;
                        }
                        if (unitAt(x0 + i, y0 + j, z0 + k, SALT_RUBBLE) < 1F / (j + 3)) {
                            placeBlock(level, concrete, x0 + i, y0 + j, z0 + k, chunkBox);
                        }
                    }
                }
            }
        }

        /**
         * 长草：一根从室内 (3, 1, 3) 斜着扎进地板下面的根系（7 格），然后房间里约 1/3 的实心格（墙、地板、天花板）换成不会腐烂的橡树叶
         * （混凝土碎块和根系不换，原作 L231~248）。
         */
        private void overgrow(WorldGenLevel level, BoundingBox chunkBox, int x0, int y0, int z0) {
            BlockState root = HoannaStructureBlocks.root();
            if (!root.isAir()) {
                long hash = mix(seed() ^ SALT_ROOT);
                double rx = (unit(hash) * 2D - 1D) * 3D;
                double rz = (unit(mix(hash)) * 2D - 1D) * 3D;
                for (int vi = 0; vi <= 6; vi++) {
                    int lx = Mth.clamp(3 + Mth.floor(rx * vi / 6D), 0, 4);
                    int lz = Mth.clamp(3 + Mth.floor(rz * vi / 6D), 0, 4);
                    placeBlock(level, root, x0 + lx, y0 + 1 - vi, z0 + lz, chunkBox);
                }
            }
            BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, Boolean.TRUE);
            Block concrete = HoannaStructureBlocks.concrete().getBlock();
            for (int i = 0; i < 5; i++) {
                for (int j = 0; j < 5; j++) {
                    for (int k = 0; k < 5; k++) {
                        int x = x0 + i;
                        int y = y0 + j;
                        int z = z0 + k;
                        if (unitAt(x, y, z, SALT_LEAVES) < 1F / 3F) {
                            BlockState here = getBlock(level, x, y, z, chunkBox);
                            if (!here.isAir() && !here.is(concrete) && !here.is(root.getBlock())) {
                                placeBlock(level, leaves, x, y, z, chunkBox);
                            }
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- 走廊

    /** 墓穴走廊：5 宽 × 5 高 × 32~63 长的强化石壳，沿走向的两端开 3 × 3 的口。NBT：{@code Dir}（走向，2D 编号） */
    public static final class Corridor extends HoannaStructurePiece {

        private final Direction direction;

        public Corridor(int genDepth, RandomSource random, BoundingBox box, Direction direction) {
            super(UndergroundStructures.CATACOMB_CORRIDOR.get(), genDepth, box, random.nextLong());
            this.direction = direction;
        }

        /** 指定种子（测试用） */
        public Corridor(int genDepth, long seed, BoundingBox box, Direction direction) {
            super(UndergroundStructures.CATACOMB_CORRIDOR.get(), genDepth, box, seed);
            this.direction = direction;
        }

        /** 读档 */
        public Corridor(CompoundTag tag) {
            super(UndergroundStructures.CATACOMB_CORRIDOR.get(), tag);
            this.direction = Direction.from2DDataValue(tag.getInt("Dir"));
        }

        @Override
        protected void saveExtra(StructurePieceSerializationContext context, CompoundTag tag) {
            tag.putInt("Dir", direction.get2DDataValue());
        }

        public Direction direction() {
            return direction;
        }

        /** 找位置（原作 {@code Corridor.getValidPlacement}）：先抽长度再判相交，(x, y, z) 是紧挨着房间的那一格；相交或超出半径返回 null */
        @Nullable
        static BoundingBox findPlacement(StructurePieceAccessor pieces, StructurePiece start, RandomSource random, int x, int y, int z, Direction facing) {
            int length = CORRIDOR_MIN_LENGTH + random.nextInt(CORRIDOR_MAX_LENGTH - CORRIDOR_MIN_LENGTH + 1);
            int s = ROOM_SIZE - 1;
            BoundingBox box = switch (facing) {
                case SOUTH -> new BoundingBox(x, y, z, x + s, y + s, z + length - 1);
                case WEST -> new BoundingBox(x - length + 1, y, z, x, y + s, z + s);
                case EAST -> new BoundingBox(x, y, z, x + length - 1, y + s, z + s);
                default -> new BoundingBox(x, y, z - length + 1, x + s, y + s, z);   // NORTH
            };
            return accept(pieces, start, box) ? box : null;
        }

        @Override
        public void addChildren(StructurePiece start, StructurePieceAccessor pieces, RandomSource random) {
            if (genDepth > MAX_CORRIDOR_DEPTH) {
                return;
            }
            BoundingBox b = boundingBox;
            BoundingBox box = switch (direction) {
                // 原作 L316 放在 (maxX + 1, minY, minZ)，也就是走廊起点的东侧，和走廊不连通；接到走廊南端
                case SOUTH -> Room.findPlacement(pieces, start, b.minX(), b.minY(), b.maxZ() + 1, Direction.SOUTH);
                case WEST -> Room.findPlacement(pieces, start, b.minX() - 1, b.minY(), b.minZ(), Direction.WEST);
                case EAST -> Room.findPlacement(pieces, start, b.maxX() + 1, b.minY(), b.minZ(), Direction.EAST);
                default -> Room.findPlacement(pieces, start, b.minX(), b.minY(), b.minZ() - 1, Direction.NORTH);
            };
            if (box != null) {
                Room room = new Room(genDepth + 1, random, box, direction.getOpposite());
                pieces.addPiece(room);
                room.addChildren(start, pieces, random);
            }
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                                BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            BoundingBox b = boundingBox;
            generateBox(level, chunkBox, b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ(), shell(), CAVE_AIR, false);
            if (direction.getAxis() == Direction.Axis.X) {
                generateAirBox(level, chunkBox, b.minX(), b.minY() + 1, b.minZ() + 1, b.minX(), b.minY() + 3, b.minZ() + 3);
                generateAirBox(level, chunkBox, b.maxX(), b.minY() + 1, b.minZ() + 1, b.maxX(), b.minY() + 3, b.minZ() + 3);
            } else {
                generateAirBox(level, chunkBox, b.minX() + 1, b.minY() + 1, b.minZ(), b.minX() + 3, b.minY() + 3, b.minZ());
                generateAirBox(level, chunkBox, b.minX() + 1, b.minY() + 1, b.maxZ(), b.minX() + 3, b.minY() + 3, b.maxZ());
            }
        }
    }

    // ---------------------------------------------------------------- 出口烟囱

    /**
     * 出口烟囱：出口房间正上方 5 × 5 的强化石壳，从房顶那层（打通）一直到 {@link #top()}（地面上第一格空位，打通、露出地面一圈），
     * 一侧整列梯子（挂在 {@link #side()} 那面墙上，那面墙没开口时从房间地板上一格开始）。
     * 包围盒 = 房间的水平范围 × [房间地板 + 1, top]（和房间重叠；在布局之后才加，不影响布局时的相交判断）。
     * NBT：{@code Floor}（房间地板高度）、{@code Top}、{@code Side}（2D 编号）、{@code FromFloor}。
     */
    public static final class Exit extends HoannaStructurePiece {

        private final int floorY;
        private final int top;
        private final Direction side;
        private final boolean fromFloor;

        /** top 必须高过房顶（{@link #addExits} 判断过） */
        public Exit(Room room, int top) {
            super(UndergroundStructures.CATACOMB_EXIT.get(), room.getGenDepth(), bounds(room.getBoundingBox(), top), mix(room.seed() ^ SALT_EXIT));
            this.floorY = room.getBoundingBox().minY();
            this.top = top;
            this.side = ladderSide(room.openings());
            this.fromFloor = !room.isOpen(side);
        }

        /** 读档 */
        public Exit(CompoundTag tag) {
            super(UndergroundStructures.CATACOMB_EXIT.get(), tag);
            this.floorY = tag.getInt("Floor");
            this.top = tag.getInt("Top");
            this.side = Direction.from2DDataValue(tag.getInt("Side"));
            this.fromFloor = tag.getBoolean("FromFloor");
        }

        private static BoundingBox bounds(BoundingBox room, int top) {
            if (top <= room.maxY()) {
                throw new IllegalArgumentException("墓穴出口烟囱的顶 " + top + " 不高于房顶 " + room.maxY());
            }
            return new BoundingBox(room.minX(), room.minY() + 1, room.minZ(), room.maxX(), top, room.maxZ());
        }

        @Override
        protected void saveExtra(StructurePieceSerializationContext context, CompoundTag tag) {
            tag.putInt("Floor", floorY);
            tag.putInt("Top", top);
            tag.putInt("Side", side.get2DDataValue());
            tag.putBoolean("FromFloor", fromFloor);
        }

        /** 地面上第一格空位（烟囱顶那一圈的高度） */
        public int top() {
            return top;
        }

        /** 梯子挂的那面墙 */
        public Direction side() {
            return side;
        }

        /** 梯子从房间地板上一格开始（否则从房顶那层开始） */
        public boolean fromFloor() {
            return fromFloor;
        }

        public int floorY() {
            return floorY;
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                                BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            int x0 = boundingBox.minX();
            int z0 = boundingBox.minZ();
            int ceiling = floorY + ROOM_SIZE - 1;
            BlockState shell = shell();
            BlockState air = Blocks.AIR.defaultBlockState();
            // 烟囱壳（原作 L210~212）：上下两层打通
            generateBox(level, chunkBox, x0, ceiling, z0, x0 + 4, top, z0 + 4, shell, air, false);
            generateAirBox(level, chunkBox, x0 + 1, ceiling, z0 + 1, x0 + 3, ceiling, z0 + 3);
            generateBox(level, chunkBox, x0 + 1, top, z0 + 1, x0 + 3, top, z0 + 3, air, air, false);
            // 梯子：先把挂梯子的那一格墙补回强化石（长草房间的墙可能是树叶，梯子挂不住），再放梯子
            int wallX = x0 + 2 + 2 * side.getStepX();
            int wallZ = z0 + 2 + 2 * side.getStepZ();
            int ladderX = x0 + 2 + side.getStepX();
            int ladderZ = z0 + 2 + side.getStepZ();
            BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, side.getOpposite());
            for (int y = fromFloor ? floorY + 1 : ceiling; y <= top; y++) {
                placeBlock(level, shell, wallX, y, wallZ, chunkBox);
                placeBlock(level, ladder, ladderX, y, ladderZ, chunkBox);
            }
        }
    }
}
