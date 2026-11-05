package com.flameforge.essentialcraft5.worldgen.hoanna.sunken;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.hoanna.nest.HoannaNests;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.registry.ECSunkenDistrict;
import com.flameforge.essentialcraft5.registry.ECVoid;
import com.flameforge.essentialcraft5.worldgen.hoanna.HoannaStructureBlocks;
import com.flameforge.essentialcraft5.worldgen.hoanna.HoannaStructurePiece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * 沉没城区的拼件（第十三批 13e-S2，batch13.md §6.4；HS §2.4.2）。基类 {@link HoannaStructurePiece}：种子、地面高度存进 NBT，跨区块一致的随机都在那里。
 * <p>
 * <b>只在自己的包围盒里写</b>：三种件都只经 {@link SunkenPiece#put} 写方块，它同时查本拼件的包围盒和这一次的 chunkBox；往下补的地基也算在包围盒里
 * （包围盒底下预留了地基那几层）。按位置决定的随机一律用 {@link #posHash}（哪个区块先生成、分几次生成都一样）。
 * <p>
 * <b>水</b>：沉房、塔身里面和缺损处「整体含水」——那一格原来是水，或者在海平面以下，就放水；在海平面以上、原来也不是水（穹顶露出水面那种浅滩）就放空气，
 * 不在陆地上凭空放水。穹顶里永远是空气。
 * <p>
 * <b>NBT</b>（上线后只能加、不能改含义）：穹顶不存额外字段（中心、地板高度从包围盒算）；沉房 {@code F}（层数）、{@code R}（半径）、{@code C}（有没有箱子）、
 * {@code D}（门朝哪边，0~3 = 北东南西）；塔身 {@code X}（沿 x 轴横躺）。高度一律从包围盒的底算（底下是地基那几层）。
 */
public final class SunkenPieces {

    /** 箱子表 {@code chests/sunken_district}（{@code tools/gen/hoanna_loot_sunken.py} 出） */
    public static final ResourceLocation CHEST_TABLE = EssentialCraft5.id("chests/sunken_district");
    /** 穹顶两侧巢穴核心的类型（{@code data/essentialcraft5/hoanna_nests/sunken_guard.json}） */
    public static final ResourceLocation NEST_TYPE = EssentialCraft5.id("sunken_guard");

    /** 穹顶外壳的外半径（水平最远 9 格、顶在地板上 9 格） */
    public static final int DOME_RADIUS = 9;
    /** 穹顶内部：到中心（地板那一层的中心格）距离的平方小于这个数、且在地板以上的格子是空气（内半径 8.5） */
    public static final double DOME_INNER_SQ = 8.5D * 8.5D;
    /** 穹顶地板底下补地基最多几格（算在包围盒里） */
    public static final int DOME_FOUNDATION = 6;
    /** 穹顶里两个箱子、两个巢穴核心离中心几格（在地板上一层） */
    public static final int DOME_FIXTURE_OFFSET = 6;
    /** 地板上嵌海晶灯的四个点（±4, ±4） */
    public static final int DOME_LAMP_OFFSET = 4;

    /** 沉房：每层高 5（dy 0 / 4 楼板），同城镇房子 */
    public static final int STOREY_HEIGHT = 5;
    /** 沉房一楼楼板底下补地基最多几格 */
    public static final int HOUSE_FOUNDATION = 4;
    /** 沉房缺损：墙和楼板的最大概率（顶层，按 i / F 递增；一楼不动） */
    public static final float HOUSE_SHELL_DECAY = 0.35F;
    /** 沉房缺损：室内碎石的最大概率 */
    public static final float HOUSE_DEBRIS_DECAY = 0.6F;

    /** 塔身横截面 5 × 5（半宽 2） */
    public static final int TOWER_HALF_WIDTH = 2;
    public static final int TOWER_SIZE = TOWER_HALF_WIDTH * 2 + 1;
    /** 塔身底下补地基最多几格 */
    public static final int TOWER_FOUNDATION = 3;
    /** 塔身从这一段（占全长的比例）往后开始塌，越往尾端缺得越多 */
    public static final float TOWER_BREAK_FROM = 0.45F;
    public static final float TOWER_BREAK_MAX = 0.7F;

    private static final long DEBRIS_SALT = 0x3C6EF372FE94F82BL;
    private static final long DECAY_SALT = 0xA54FF53A5F1D36F1L;
    private static final long TOWER_SALT = 0x510E527FADE682D1L;

    private SunkenPieces() {
    }

    // ================================================================== 穹顶的形状（纯函数，GameTest 逐格查）

    /** 穹顶局部 (dx, dy, dz)（dy 0 是地板那一层，中心在 (0, 0, 0)）是不是里面的空气 */
    public static boolean domeInterior(int dx, int dy, int dz) {
        return dy >= 1 && dx * dx + dy * dy + dz * dz < DOME_INNER_SQ;
    }

    /** 是不是外壳：不是里面、dy ≥ 0、上下左右前后六个邻格里有一个是里面（所以里面的每一格六面都挨着里面或外壳，水进不来） */
    public static boolean domeShell(int dx, int dy, int dz) {
        if (dy < 0 || domeInterior(dx, dy, dz)) {
            return false;
        }
        return domeInterior(dx + 1, dy, dz) || domeInterior(dx - 1, dy, dz) || domeInterior(dx, dy + 1, dz) || domeInterior(dx, dy - 1, dz)
                || domeInterior(dx, dy, dz + 1) || domeInterior(dx, dy, dz - 1);
    }

    /** 外壳这一格放什么：地板（dy 0）强化石、四个点海晶灯；正东西南北两条经线强化石做肋；其余虚空玻璃 */
    public static BlockState domeShellBlock(int dx, int dy, int dz) {
        if (dy == 0) {
            return Math.abs(dx) == DOME_LAMP_OFFSET && Math.abs(dz) == DOME_LAMP_OFFSET ? Blocks.SEA_LANTERN.defaultBlockState()
                    : ECMaterials.FORTIFIED_STONE.get().defaultBlockState();
        }
        if (dx == 0 || dz == 0) {
            return ECMaterials.FORTIFIED_STONE.get().defaultBlockState();
        }
        return ECVoid.VOID_GLASS.get().defaultBlockState();
    }

    /** 沉房、塔身里面「含水」时放什么：原来是水或在海平面以下放水，否则空气（见类注释） */
    static BlockState fluidFor(WorldGenLevel level, BlockPos pos, int seaLevel) {
        return pos.getY() < seaLevel || level.getFluidState(pos).is(FluidTags.WATER) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
    }

    // ================================================================== 共用

    /** 三种件的共同写法：只经 {@link #put} 写方块（同时查自己的包围盒和 chunkBox） */
    abstract static class SunkenPiece extends HoannaStructurePiece {

        SunkenPiece(StructurePieceType type, int genDepth, BoundingBox box, long seed) {
            super(type, genDepth, box, seed);
        }

        SunkenPiece(StructurePieceType type, CompoundTag tag) {
            super(type, tag);
        }

        /** pos 在本拼件包围盒里、也在这一次的 chunkBox 里才写；写了返回 true */
        protected final boolean put(WorldGenLevel level, BoundingBox chunkBox, BlockPos pos, BlockState state) {
            if (!boundingBox.isInside(pos) || !chunkBox.isInside(pos)) {
                return false;
            }
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
            if (needsShapeUpdate(state)) {
                requestShapeUpdate(level, pos);
            }
            return true;
        }

        /** 从 (x, y, z) 往下补 state，直到碰到实心的东西或出了包围盒底 */
        protected final void foundation(WorldGenLevel level, BoundingBox chunkBox, int x, int y, int z, BlockState state) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
            while (pos.getY() >= boundingBox.minY() && pos.getY() > level.getMinBuildHeight() && canFillThrough(level.getBlockState(pos))) {
                if (!put(level, chunkBox, pos.immutable(), state)) {
                    return;
                }
                pos.move(Direction.DOWN);
            }
        }

        /** 放一个箱子（朝 facing，在水里就含水），表是 {@link #CHEST_TABLE}；不在包围盒 / chunkBox 里不放 */
        protected final void chest(WorldGenLevel level, BoundingBox chunkBox, RandomSource random, BlockPos pos, Direction facing) {
            if (!boundingBox.isInside(pos) || !chunkBox.isInside(pos)) {
                return;
            }
            boolean water = level.getFluidState(pos).is(FluidTags.WATER);
            BlockState state = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing).setValue(ChestBlock.WATERLOGGED, water);
            createChest(level, chunkBox, random, pos, CHEST_TABLE, state);
        }
    }

    // ================================================================== 穹顶

    /**
     * {@code sunken_dome}：空气穹顶。地板在包围盒底往上 {@value #DOME_FOUNDATION} 格（= 水底 − 1，嵌进海底一格），半球外壳 {@link #domeShell}
     * （地板强化石、两条经线强化石、其余虚空玻璃），里面一律空气（只在生成时写一次）；地板底下补强化石地基到海底。
     * 地板上一层：正中沉城祭坛，南北两侧各一个箱子（{@link #CHEST_TABLE}）、东西两侧各一个巢穴核心（{@link #NEST_TYPE}，ARMED）。
     * 外壳逐格完整，缺损不作用于穹顶。
     */
    public static final class Dome extends SunkenPiece {

        public Dome(int genDepth, BoundingBox box, long seed) {
            super(SunkenStructures.SUNKEN_DOME.get(), genDepth, box, seed);
        }

        public Dome(CompoundTag tag) {
            super(SunkenStructures.SUNKEN_DOME.get(), tag);
        }

        /** 按布局建：水底 ground、地板 ground − 1 */
        static Dome create(SunkenDistrictStructure.Planned planned, int ground, long seed) {
            int floor = ground - 1;
            Dome dome = new Dome(0, planned.box(floor - DOME_FOUNDATION, floor + DOME_RADIUS), seed);
            dome.setGroundY(ground);
            return dome;
        }

        public int centerX() {
            return (boundingBox.minX() + boundingBox.maxX()) / 2;
        }

        public int centerZ() {
            return (boundingBox.minZ() + boundingBox.maxZ()) / 2;
        }

        /** 地板那一层的 y */
        public int floorY() {
            return boundingBox.minY() + DOME_FOUNDATION;
        }

        /** 祭坛的位置（地板上一层的正中） */
        public BlockPos altarPos() {
            return new BlockPos(centerX(), floorY() + 1, centerZ());
        }

        /** 两个巢穴核心（东、西） */
        public BlockPos[] nestPositions() {
            return new BlockPos[]{altarPos().east(DOME_FIXTURE_OFFSET), altarPos().west(DOME_FIXTURE_OFFSET)};
        }

        /** 两个箱子（南、北） */
        public BlockPos[] chestPositions() {
            return new BlockPos[]{altarPos().south(DOME_FIXTURE_OFFSET), altarPos().north(DOME_FIXTURE_OFFSET)};
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                                BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            int cx = centerX();
            int cz = centerZ();
            int floor = floorY();
            BlockState air = Blocks.AIR.defaultBlockState();
            BlockState fortified = ECMaterials.FORTIFIED_STONE.get().defaultBlockState();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int dx = -DOME_RADIUS; dx <= DOME_RADIUS; dx++) {
                for (int dz = -DOME_RADIUS; dz <= DOME_RADIUS; dz++) {
                    int x = cx + dx;
                    int z = cz + dz;
                    if (x < chunkBox.minX() || x > chunkBox.maxX() || z < chunkBox.minZ() || z > chunkBox.maxZ()) {
                        continue;
                    }
                    for (int dy = DOME_RADIUS; dy >= 0; dy--) {
                        pos.set(x, floor + dy, z);
                        if (domeInterior(dx, dy, dz)) {
                            put(level, chunkBox, pos.immutable(), air);
                        } else if (domeShell(dx, dy, dz)) {
                            put(level, chunkBox, pos.immutable(), domeShellBlock(dx, dy, dz));
                        }
                    }
                    if (domeShell(dx, 0, dz)) {
                        foundation(level, chunkBox, x, floor - 1, z, fortified);
                    }
                }
            }
            BlockPos altar = altarPos();
            put(level, chunkBox, altar, ECSunkenDistrict.SUNKEN_ALTAR.get().defaultBlockState());
            BlockPos[] chests = chestPositions();
            chest(level, chunkBox, random, chests[0], Direction.NORTH);
            chest(level, chunkBox, random, chests[1], Direction.SOUTH);
            for (BlockPos nest : nestPositions()) {
                if (boundingBox.isInside(nest) && chunkBox.isInside(nest)) {
                    HoannaNests.placeCore(level, nest, NEST_TYPE);
                }
            }
        }
    }

    // ================================================================== 沉房

    /**
     * {@code sunken_house}：沉房，外壳照城镇房子（{@code HoannaTownPieces.House}）：F + 1 层、每层 5 格高，dy 0 / 4 强化石楼板，四壁强化石、
     * 墙中段强化玻璃窗，室内碎石（混凝土）概率 1 / (dy + 1)；里面整体含水（见类注释）。一楼楼板在包围盒底往上 {@value #HOUSE_FOUNDATION} 格
     * （= 水底 − 1），四角补地基到海底；一楼朝 {@code D} 那面墙正中开一个 1 × 2 的门洞。
     * <b>缺损</b>（一楼不动）：第 i 层墙和楼板 {@value #HOUSE_SHELL_DECAY} × i / F、碎石 {@value #HOUSE_DEBRIS_DECAY} × i / F 的概率变成水
     * （城镇房子的缺损是变空气、挨着水不挖，在水底不适用，所以这里逐格算）。有箱子（50%）时放在一楼的一个角上。
     */
    public static final class House extends SunkenPiece {
        private static final String TAG_FLOORS = "F";
        private static final String TAG_RADIUS = "R";
        private static final String TAG_CHEST = "C";
        private static final String TAG_DOOR = "D";
        private static final Direction[] DOOR_DIRECTIONS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

        private final int floors;
        private final int radius;
        private final boolean hasChest;
        private final int door;

        /** 只给包围盒时：1 层、半径按包围盒的短边、没有箱子、门朝北 */
        public House(int genDepth, BoundingBox box, long seed) {
            this(genDepth, box, seed, 1, Math.max(1, (Math.min(box.getXSpan(), box.getZSpan()) - 1) / 2), false, 0);
        }

        public House(int genDepth, BoundingBox box, long seed, int floors, int radius, boolean hasChest, int door) {
            super(SunkenStructures.SUNKEN_HOUSE.get(), genDepth, box, seed);
            this.floors = Math.max(1, floors);
            this.radius = Math.max(1, radius);
            this.hasChest = hasChest;
            this.door = Math.floorMod(door, 4);
        }

        public House(CompoundTag tag) {
            super(SunkenStructures.SUNKEN_HOUSE.get(), tag);
            this.floors = Math.max(1, tag.getInt(TAG_FLOORS));
            this.radius = Math.max(1, tag.getInt(TAG_RADIUS));
            this.hasChest = tag.getBoolean(TAG_CHEST);
            this.door = Math.floorMod(tag.getInt(TAG_DOOR), 4);
        }

        /** 按布局建：水底 ground，一楼楼板 ground − 1；门的朝向由件种子定 */
        static House create(SunkenDistrictStructure.Planned planned, int ground, long seed) {
            int slab = ground - 1;
            BoundingBox box = planned.box(slab - HOUSE_FOUNDATION, slab + (planned.floors() + 1) * STOREY_HEIGHT - 1);
            House house = new House(1, box, seed, planned.floors(), planned.radius(), planned.chest(), (int) Math.floorMod(mix(seed), 4L));
            house.setGroundY(ground);
            return house;
        }

        @Override
        protected void saveExtra(StructurePieceSerializationContext context, CompoundTag tag) {
            tag.putInt(TAG_FLOORS, floors);
            tag.putInt(TAG_RADIUS, radius);
            tag.putBoolean(TAG_CHEST, hasChest);
            tag.putInt(TAG_DOOR, door);
        }

        public int floors() {
            return floors;
        }

        public int radius() {
            return radius;
        }

        public boolean hasChest() {
            return hasChest;
        }

        public int door() {
            return door;
        }

        /** 门洞开在哪面墙（{@code D} 0~3 = 北、东、南、西）；箱子正面朝这边 */
        public Direction doorDirection() {
            return DOOR_DIRECTIONS[door];
        }

        /** 一楼楼板的 y */
        public int slabY() {
            return boundingBox.minY() + HOUSE_FOUNDATION;
        }

        /** 箱子的位置（一楼、门对面的一个角上） */
        public BlockPos chestPos() {
            int far = radius * 2;
            int lx = door == 1 ? 1 : far - 1;
            int lz = door == 2 ? 1 : far - 1;
            return new BlockPos(boundingBox.minX() + lx, slabY() + 1, boundingBox.minZ() + lz);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                                BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            int seaLevel = generator.getSeaLevel();
            BlockState fortified = ECMaterials.FORTIFIED_STONE.get().defaultBlockState();
            BlockState glass = ECMaterials.FORTIFIED_GLASS.get().defaultBlockState();
            BlockState concrete = HoannaStructureBlocks.concrete();
            int far = radius * 2;
            int slab = slabY();
            int x0 = boundingBox.minX();
            int z0 = boundingBox.minZ();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            // 箱子那一格先留成水（不放碎石），箱子按这一格的水决定含不含水
            BlockPos chestAt = hasChest ? chestPos() : null;
            for (int i = 0; i <= floors; i++) {
                float share = i / (float) floors;
                for (int dy = 0; dy < STOREY_HEIGHT; dy++) {
                    int y = slab + i * STOREY_HEIGHT + dy;
                    for (int dx = 0; dx <= far; dx++) {
                        for (int dz = 0; dz <= far; dz++) {
                            pos.set(x0 + dx, y, z0 + dz);
                            if (!chunkBox.isInside(pos)) {
                                continue;
                            }
                            BlockState state = cell(dx, y, dz, dy, far, fortified, glass, concrete);
                            boolean shell = state == fortified || state == glass;
                            boolean decayed = i >= 1 && state != null
                                    && unit(posHash(pos.getX(), y, pos.getZ(), seed() ^ DECAY_SALT)) < (shell ? HOUSE_SHELL_DECAY : HOUSE_DEBRIS_DECAY) * share;
                            boolean doorway = i == 0 && isDoorway(dx, dy, dz, far);
                            if (state == null || decayed || doorway || pos.equals(chestAt)) {
                                state = fluidFor(level, pos, seaLevel);
                            }
                            put(level, chunkBox, pos.immutable(), state);
                        }
                    }
                }
            }
            for (int dx : new int[]{0, far}) {
                for (int dz : new int[]{0, far}) {
                    foundation(level, chunkBox, x0 + dx, slab - 1, z0 + dz, fortified);
                }
            }
            if (hasChest) {
                chest(level, chunkBox, random, chestPos(), doorDirection());
            }
        }

        /** 门洞：一楼 dy 1~2、朝 door 那面墙的正中 */
        private boolean isDoorway(int dx, int dy, int dz, int far) {
            if (dy < 1 || dy > 2) {
                return false;
            }
            int mid = far / 2;
            return switch (door) {
                case 0 -> dz == 0 && dx == mid;
                case 1 -> dx == far && dz == mid;
                case 2 -> dz == far && dx == mid;
                default -> dx == 0 && dz == mid;
            };
        }

        /** 一格是什么（同城镇房子的外壳算法）；室内没抽到碎石的返回 null（= 水 / 空气，由 {@link #fluidFor} 定） */
        private BlockState cell(int dx, int y, int dz, int dy, int far, BlockState fortified, BlockState glass, BlockState concrete) {
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
            return null;
        }
    }

    // ================================================================== 塔身

    /**
     * {@code sunken_tower}：横倒在海底的塔身，5 × 5 的方管（强化石，每隔 4 格一圈虚空石箍、两侧墙中段间隔开强化玻璃窗），里面含水、两头敞着；
     * 底面在包围盒底往上 {@value #TOWER_FOUNDATION} 格（= 水底 − 1），每隔 3 格往下补地基到海底。从全长 {@value #TOWER_BREAK_FROM} 处往尾端越塌越多
     * （底面不塌，最多 {@value #TOWER_BREAK_MAX}），塌掉的格子变成水。
     */
    public static final class Tower extends SunkenPiece {
        private static final String TAG_ALONG_X = "X";

        private final boolean alongX;

        /** 只给包围盒时：按长边定朝向 */
        public Tower(int genDepth, BoundingBox box, long seed) {
            this(genDepth, box, seed, box.getXSpan() >= box.getZSpan());
        }

        public Tower(int genDepth, BoundingBox box, long seed, boolean alongX) {
            super(SunkenStructures.SUNKEN_TOWER.get(), genDepth, box, seed);
            this.alongX = alongX;
        }

        public Tower(CompoundTag tag) {
            super(SunkenStructures.SUNKEN_TOWER.get(), tag);
            this.alongX = tag.getBoolean(TAG_ALONG_X);
        }

        /** 按布局建：水底 ground，底面 ground − 1 */
        static Tower create(SunkenDistrictStructure.Planned planned, int ground, long seed) {
            int base = ground - 1;
            Tower tower = new Tower(1, planned.box(base - TOWER_FOUNDATION, base + TOWER_SIZE - 1), seed, planned.alongX());
            tower.setGroundY(ground);
            return tower;
        }

        @Override
        protected void saveExtra(StructurePieceSerializationContext context, CompoundTag tag) {
            tag.putBoolean(TAG_ALONG_X, alongX);
        }

        public boolean alongX() {
            return alongX;
        }

        public int length() {
            return alongX ? boundingBox.getXSpan() : boundingBox.getZSpan();
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                                BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            int seaLevel = generator.getSeaLevel();
            BlockState fortified = ECMaterials.FORTIFIED_STONE.get().defaultBlockState();
            BlockState glass = ECMaterials.FORTIFIED_GLASS.get().defaultBlockState();
            BlockState band = HoannaStructureBlocks.voidStone();
            int length = length();
            int base = boundingBox.minY() + TOWER_FOUNDATION;
            int across = Math.min(TOWER_SIZE, alongX ? boundingBox.getZSpan() : boundingBox.getXSpan());
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int a = 0; a < length; a++) {
                float t = length <= 1 ? 0F : a / (float) (length - 1);
                float breakChance = t <= TOWER_BREAK_FROM ? 0F : (t - TOWER_BREAK_FROM) / (1F - TOWER_BREAK_FROM) * TOWER_BREAK_MAX;
                for (int u = 0; u < across; u++) {
                    for (int v = 0; v < TOWER_SIZE; v++) {
                        int x = alongX ? boundingBox.minX() + a : boundingBox.minX() + u;
                        int z = alongX ? boundingBox.minZ() + u : boundingBox.minZ() + a;
                        pos.set(x, base + v, z);
                        if (!chunkBox.isInside(pos)) {
                            continue;
                        }
                        boolean side = u == 0 || u == across - 1;
                        boolean shell = side || v == 0 || v == TOWER_SIZE - 1;
                        BlockState state;
                        if (!shell) {
                            state = fluidFor(level, pos, seaLevel);
                        } else if (v > 0 && breakChance > 0F && unit(posHash(x, pos.getY(), z, seed() ^ TOWER_SALT)) < breakChance) {
                            state = fluidFor(level, pos, seaLevel);
                        } else if (a % 4 == 0) {
                            state = band;
                        } else if (side && v == TOWER_SIZE / 2 && a % 4 == 2) {
                            state = glass;
                        } else {
                            state = fortified;
                        }
                        put(level, chunkBox, pos.immutable(), state);
                    }
                }
                if (a % 3 == 0) {
                    for (int u = 0; u < across; u += Math.max(1, across - 1)) {
                        int x = alongX ? boundingBox.minX() + a : boundingBox.minX() + u;
                        int z = alongX ? boundingBox.minZ() + u : boundingBox.minZ() + a;
                        foundation(level, chunkBox, x, base - 1, z, fortified);
                    }
                }
            }
        }
    }
}
