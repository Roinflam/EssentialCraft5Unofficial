package com.flameforge.essentialcraft5.worldgen.hoanna.underground;

import com.flameforge.essentialcraft5.worldgen.hoanna.HoannaStructureBlocks;
import com.flameforge.essentialcraft5.worldgen.hoanna.HoannaStructurePiece;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePieceAccessor;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 现代矿井的拼件（原作 {@code StructureModernShaftPieces}，700 行，是 1.12 原版废弃矿井换了材料；M6，batch6a §5.6）：
 * 起点大厅 {@link Room}、走廊 {@link Corridor}、十字路口 {@link Crossing}、阶梯 {@link Stairs}。写法照 1.20.1 原版 {@code MineshaftPieces}。
 * <p>
 * <b>布局</b>（{@link #layout}）：大厅在起点区块 (minX + 2, minZ + 2)、y {@value #START_Y}、边长 8~13，四面随机出走廊；
 * 每个新件 70% 走廊（3 × 3 × 5~20）、10% 阶梯（往下 5 格）、20% 十字路口（1/4 是两层的）；深度 ≤ {@value #MAX_DEPTH}（新件深度 ≤ 9）、
 * 新件起点离大厅 ±{@value #MAX_OFFSET} 格以内（原作就有，最远约 105 格，在 8 区块引用范围内）。
 * 布局完整体挪到海平面以下（{@link #moveBelowSeaLevel}，同原版矿井：最高点在海平面 − 10 以下随机）。
 * <p>
 * <b>材料</b>：走廊每 5 格一组虚空栅栏立柱 + 虚空石横梁（1/4 只有两头的虚空石），地板下是空气的格子补虚空石；1/3 的走廊铺铁轨、
 * 铁轨那一排和两侧 70% 换成虚空石；其余走廊 1/23 是洞穴蜘蛛走廊（蜘蛛网 + 一个洞穴蜘蛛刷怪笼，同原版矿井）；每组两侧各 1/100 一辆
 * 运输矿车（箱子，原版 {@code minecraft:chests/abandoned_mineshaft}）；十字路口四角虚空石柱。虚空石 / 虚空栅栏按注册名取
 * （{@link HoannaStructureBlocks}，还没注册时退回石头）。碰到液体的件在那个区块里不生成（同原版）。
 * <p>
 * <b>修掉的原作问题</b>（batch6a §8）：
 * <ul>
 *   <li>大厅第 4 圈支路写成从西边出、朝西（L570），东边永远没有支路 → 从 maxX + 1 出、朝东（同原版）。</li>
 *   <li>阶梯挖通道的循环从 −20 开始（L694），有 20 步挖到包围盒外 → 0~4（同原版；1.20.1 只在包围盒相交的区块执行，照原作会挖出半截通道）。</li>
 *   <li>火把立在半空（L302~303，形状后处理会把它拆成空气）→ 挂在横梁两侧的墙上火把（同原版），只有整根横梁时才挂。</li>
 *   <li>铁轨下换虚空石写成 x = −1、0、1（L348~350，−1 在走廊包围盒外、2 漏了）→ 0、1、2。</li>
 *   <li>刷怪笼「放过没有」记在拼件字段里、每个区块生成时各抽一遍位置（结果看区块生成顺序，并行生成时还有竞态）→ 位置按拼件种子定死，只在它所在的区块放一次。</li>
 *   <li>随机按区块播种：同一段走廊跨两个区块时横梁、蜘蛛网两边对不上 → 按位置哈希（{@link HoannaStructurePiece#localUnit}）。</li>
 *   <li>原作 /locate 逐区块扫 1 000 圈（约 400 万区块）会卡死 → 数据包 random_spread（16 / 8），按格子算。</li>
 * </ul>
 */
public final class ModernShaftPieces {

    static final Logger LOGGER = LogUtils.getLogger();

    /** 大厅的初始高度（布局完再整体挪到海平面以下） */
    public static final int START_Y = 50;
    /** 新件的深度上限：父件深度超过它就不再往外长（新件深度 ≤ 9） */
    public static final int MAX_DEPTH = 8;
    /** 新件起点离大厅最小角的最远水平距离 */
    public static final int MAX_OFFSET = 80;
    /** 挪到海平面以下多少格（同原版矿井） */
    public static final int SEA_LEVEL_MARGIN = 10;

    // 位置哈希的 salt
    private static final long SALT_CEILING = 0x5EEDBEEF0001L;
    private static final long SALT_WEB = 0x5EEDBEEF0002L;
    private static final long SALT_BEAM = 0x5EEDBEEF0003L;
    private static final long SALT_TORCH = 0x5EEDBEEF0004L;
    private static final long SALT_CART = 0x5EEDBEEF0005L;
    private static final long SALT_SPAWNER = 0x5EEDBEEF0006L;
    private static final long SALT_RAIL = 0x5EEDBEEF0007L;
    private static final long SALT_RAIL_BED = 0x5EEDBEEF0008L;

    private ModernShaftPieces() {
    }

    // ---------------------------------------------------------------- 布局

    /** 从大厅 (x, z)（最小角）开始布局，件依次加进 pieces（深度优先，同原版）。只算包围盒和标记，不碰世界。返回大厅 */
    public static Room layout(StructurePieceAccessor pieces, RandomSource random, int x, int z) {
        Room room = new Room(0, random, x, z);
        pieces.addPiece(room);
        room.addChildren(room, pieces, random);
        return room;
    }

    /**
     * 整座矿井挪到海平面以下（原版 {@code MineshaftStructure} 的写法：最高点在 [整体高度 + minY + 1, 海平面 − 10) 里随机）。
     *
     * @return 挪了多少格（负数往下）
     */
    @SuppressWarnings("deprecation")
    public static int moveBelowSeaLevel(StructurePiecesBuilder builder, int seaLevel, int minY, RandomSource random) {
        return builder.moveBelowSeaLevel(seaLevel, minY, random, SEA_LEVEL_MARGIN);
    }

    @Nullable
    private static ShaftPiece createRandomShaftPiece(StructurePieceAccessor pieces, RandomSource random, int x, int y, int z, Direction direction,
                                                     int genDepth) {
        int roll = random.nextInt(100);
        if (roll >= 80) {
            BoundingBox box = Crossing.findCrossing(pieces, random, x, y, z, direction);
            if (box != null) {
                return new Crossing(genDepth, random, box, direction);
            }
        } else if (roll >= 70) {
            BoundingBox box = Stairs.findStairs(pieces, x, y, z, direction);
            if (box != null) {
                return new Stairs(genDepth, random, box, direction);
            }
        } else {
            BoundingBox box = Corridor.findCorridorSize(pieces, random, x, y, z, direction);
            if (box != null) {
                return new Corridor(genDepth, random, box, direction);
            }
        }
        return null;
    }

    /** 原版 {@code generateAndAddPiece}：深度、离大厅的距离超了就不放；放了就接着往外长 */
    @Nullable
    static ShaftPiece generateAndAddPiece(StructurePiece start, StructurePieceAccessor pieces, RandomSource random, int x, int y, int z,
                                          Direction direction, int genDepth) {
        if (genDepth > MAX_DEPTH) {
            return null;
        }
        BoundingBox origin = start.getBoundingBox();
        if (Math.abs(x - origin.minX()) > MAX_OFFSET || Math.abs(z - origin.minZ()) > MAX_OFFSET) {
            return null;
        }
        ShaftPiece piece = createRandomShaftPiece(pieces, random, x, y, z, direction, genDepth + 1);
        if (piece != null) {
            pieces.addPiece(piece);
            piece.addChildren(start, pieces, random);
        }
        return piece;
    }

    // ---------------------------------------------------------------- 公共基类

    /** 四种矿井拼件的公共部分 */
    abstract static class ShaftPiece extends HoannaStructurePiece {

        ShaftPiece(StructurePieceType type, int genDepth, BoundingBox box, long seed) {
            super(type, genDepth, box, seed);
        }

        ShaftPiece(StructurePieceType type, CompoundTag tag) {
            super(type, tag);
        }

        /**
         * 包围盒外扩一圈、和 chunkBox 相交的那六个面上有没有液体（原版 {@code isInInvalidLocation} 去掉深暗之域判断；原作
         * {@code isLiquidInStructureBoundingBox}）。有就整件在这个区块里不生成，免得挖开地下水、岩浆湖。
         */
        protected boolean touchesLiquid(LevelAccessor level, BoundingBox chunkBox) {
            int x0 = Math.max(boundingBox.minX() - 1, chunkBox.minX());
            int y0 = Math.max(boundingBox.minY() - 1, chunkBox.minY());
            int z0 = Math.max(boundingBox.minZ() - 1, chunkBox.minZ());
            int x1 = Math.min(boundingBox.maxX() + 1, chunkBox.maxX());
            int y1 = Math.min(boundingBox.maxY() + 1, chunkBox.maxY());
            int z1 = Math.min(boundingBox.maxZ() + 1, chunkBox.maxZ());
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    if (level.getBlockState(pos.set(x, y0, z)).liquid() || level.getBlockState(pos.set(x, y1, z)).liquid()) {
                        return true;
                    }
                }
            }
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    if (level.getBlockState(pos.set(x, y, z0)).liquid() || level.getBlockState(pos.set(x, y, z1)).liquid()) {
                        return true;
                    }
                }
            }
            for (int z = z0; z <= z1; z++) {
                for (int y = y0; y <= y1; y++) {
                    if (level.getBlockState(pos.set(x0, y, z)).liquid() || level.getBlockState(pos.set(x1, y, z)).liquid()) {
                        return true;
                    }
                }
            }
            return false;
        }

        /** 局部坐标 (x, y, z) 按位置哈希以 chance 的概率放 state（原作 {@code randomlyPlaceBlock}，跨区块一致） */
        protected void maybePlace(WorldGenLevel level, BoundingBox chunkBox, float chance, int x, int y, int z, BlockState state, long salt) {
            if (localUnit(x, y, z, salt) < chance) {
                placeBlock(level, state, x, y, z, chunkBox);
            }
        }

        /** 局部坐标的长方体里每格按位置哈希以 chance 的概率放 state（原作 {@code generateMaybeBox}，跨区块一致） */
        protected void maybeBox(WorldGenLevel level, BoundingBox chunkBox, float chance, int x0, int y0, int z0, int x1, int y1, int z1,
                                BlockState state, long salt) {
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        if (chunkBox.isInside(getWorldPos(x, y, z))) {
                            maybePlace(level, chunkBox, chance, x, y, z, state, salt);
                        }
                    }
                }
            }
        }

        /** 世界坐标 (x, minY − 1, z) 这一行下面是空气就补虚空石（没有朝向的件：大厅、十字路口） */
        protected void fillFloorWorld(WorldGenLevel level, BoundingBox chunkBox, int x0, int z0, int x1, int z1, int y) {
            BlockState voidStone = HoannaStructureBlocks.voidStone();
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    if (getBlock(level, x, y, z, chunkBox).isAir()) {
                        placeBlock(level, voidStone, x, y, z, chunkBox);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- 走廊

    /**
     * 走廊：3 宽 × 3 高 × (2~4) × 5 长，朝向 = 走向（局部 z 沿走向，方块状态随朝向镜像 / 旋转，同原版）。
     * NBT：{@code hr}（铺铁轨）、{@code sc}（洞穴蜘蛛走廊）、{@code Num}（几组支架）；朝向在原版的 {@code O} 里。
     */
    public static final class Corridor extends ShaftPiece {

        private final boolean hasRails;
        private final boolean spiderCorridor;
        private final int numSections;

        public Corridor(int genDepth, RandomSource random, BoundingBox box, Direction direction) {
            super(UndergroundStructures.SHAFT_CORRIDOR.get(), genDepth, box, random.nextLong());
            setOrientation(direction);
            this.hasRails = random.nextInt(3) == 0;
            this.spiderCorridor = !hasRails && random.nextInt(23) == 0;
            this.numSections = sections(box, direction);
        }

        /** 指定标记（测试、展示厅用） */
        public Corridor(int genDepth, long seed, BoundingBox box, Direction direction, boolean hasRails, boolean spiderCorridor) {
            super(UndergroundStructures.SHAFT_CORRIDOR.get(), genDepth, box, seed);
            setOrientation(direction);
            this.hasRails = hasRails;
            this.spiderCorridor = spiderCorridor;
            this.numSections = sections(box, direction);
        }

        /** 读档 */
        public Corridor(CompoundTag tag) {
            super(UndergroundStructures.SHAFT_CORRIDOR.get(), tag);
            this.hasRails = tag.getBoolean("hr");
            this.spiderCorridor = tag.getBoolean("sc");
            this.numSections = tag.getInt("Num");
        }

        private static int sections(BoundingBox box, Direction direction) {
            return (direction.getAxis() == Direction.Axis.Z ? box.getZSpan() : box.getXSpan()) / 5;
        }

        @Override
        protected void saveExtra(StructurePieceSerializationContext context, CompoundTag tag) {
            tag.putBoolean("hr", hasRails);
            tag.putBoolean("sc", spiderCorridor);
            tag.putInt("Num", numSections);
        }

        public boolean hasRails() {
            return hasRails;
        }

        public boolean isSpiderCorridor() {
            return spiderCorridor;
        }

        public int numSections() {
            return numSections;
        }

        /** 原版 {@code findCorridorSize}：长度 4、3、2 组依次试，放得下就用 */
        @Nullable
        static BoundingBox findCorridorSize(StructurePieceAccessor pieces, RandomSource random, int x, int y, int z, Direction direction) {
            for (int i = random.nextInt(3) + 2; i > 0; i--) {
                int length = i * 5;
                BoundingBox box = switch (direction) {
                    case SOUTH -> new BoundingBox(0, 0, 0, 2, 2, length - 1);
                    case WEST -> new BoundingBox(-(length - 1), 0, 0, 0, 2, 2);
                    case EAST -> new BoundingBox(0, 0, 0, length - 1, 2, 2);
                    default -> new BoundingBox(0, 0, -(length - 1), 2, 2, 0);   // NORTH
                };
                box.move(x, y, z);
                if (pieces.findCollisionPiece(box) == null) {
                    return box;
                }
            }
            return null;
        }

        @Override
        public void addChildren(StructurePiece start, StructurePieceAccessor pieces, RandomSource random) {
            int depth = getGenDepth();
            int roll = random.nextInt(4);
            Direction direction = getOrientation();
            BoundingBox b = boundingBox;
            if (direction != null) {
                switch (direction) {
                    case SOUTH -> {
                        if (roll <= 1) {
                            generateAndAddPiece(start, pieces, random, b.minX(), b.minY() - 1 + random.nextInt(3), b.maxZ() + 1, direction, depth);
                        } else if (roll == 2) {
                            generateAndAddPiece(start, pieces, random, b.minX() - 1, b.minY() - 1 + random.nextInt(3), b.maxZ() - 3, Direction.WEST, depth);
                        } else {
                            generateAndAddPiece(start, pieces, random, b.maxX() + 1, b.minY() - 1 + random.nextInt(3), b.maxZ() - 3, Direction.EAST, depth);
                        }
                    }
                    case WEST -> {
                        if (roll <= 1) {
                            generateAndAddPiece(start, pieces, random, b.minX() - 1, b.minY() - 1 + random.nextInt(3), b.minZ(), direction, depth);
                        } else if (roll == 2) {
                            generateAndAddPiece(start, pieces, random, b.minX(), b.minY() - 1 + random.nextInt(3), b.minZ() - 1, Direction.NORTH, depth);
                        } else {
                            generateAndAddPiece(start, pieces, random, b.minX(), b.minY() - 1 + random.nextInt(3), b.maxZ() + 1, Direction.SOUTH, depth);
                        }
                    }
                    case EAST -> {
                        if (roll <= 1) {
                            generateAndAddPiece(start, pieces, random, b.maxX() + 1, b.minY() - 1 + random.nextInt(3), b.minZ(), direction, depth);
                        } else if (roll == 2) {
                            generateAndAddPiece(start, pieces, random, b.maxX() - 3, b.minY() - 1 + random.nextInt(3), b.minZ() - 1, Direction.NORTH, depth);
                        } else {
                            generateAndAddPiece(start, pieces, random, b.maxX() - 3, b.minY() - 1 + random.nextInt(3), b.maxZ() + 1, Direction.SOUTH, depth);
                        }
                    }
                    default -> {   // NORTH
                        if (roll <= 1) {
                            generateAndAddPiece(start, pieces, random, b.minX(), b.minY() - 1 + random.nextInt(3), b.minZ() - 1, direction, depth);
                        } else if (roll == 2) {
                            generateAndAddPiece(start, pieces, random, b.minX() - 1, b.minY() - 1 + random.nextInt(3), b.minZ(), Direction.WEST, depth);
                        } else {
                            generateAndAddPiece(start, pieces, random, b.maxX() + 1, b.minY() - 1 + random.nextInt(3), b.minZ(), Direction.EAST, depth);
                        }
                    }
                }
            }
            if (depth < 8) {
                if (direction != Direction.NORTH && direction != Direction.SOUTH) {
                    for (int x = b.minX() + 3; x + 3 <= b.maxX(); x += 5) {
                        int side = random.nextInt(5);
                        if (side == 0) {
                            generateAndAddPiece(start, pieces, random, x, b.minY(), b.minZ() - 1, Direction.NORTH, depth + 1);
                        } else if (side == 1) {
                            generateAndAddPiece(start, pieces, random, x, b.minY(), b.maxZ() + 1, Direction.SOUTH, depth + 1);
                        }
                    }
                } else {
                    for (int z = b.minZ() + 3; z + 3 <= b.maxZ(); z += 5) {
                        int side = random.nextInt(5);
                        if (side == 0) {
                            generateAndAddPiece(start, pieces, random, b.minX() - 1, b.minY(), z, Direction.WEST, depth + 1);
                        } else if (side == 1) {
                            generateAndAddPiece(start, pieces, random, b.maxX() + 1, b.minY(), z, Direction.EAST, depth + 1);
                        }
                    }
                }
            }
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                                BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            if (touchesLiquid(level, chunkBox)) {
                return;
            }
            int last = numSections * 5 - 1;
            BlockState voidStone = HoannaStructureBlocks.voidStone();
            BlockState fence = HoannaStructureBlocks.voidFence();
            BlockState web = Blocks.COBWEB.defaultBlockState();
            // 下面两层挖空，顶层 80% 挖空（原作 L266~268）
            generateBox(level, chunkBox, 0, 0, 0, 2, 1, last, CAVE_AIR, CAVE_AIR, false);
            maybeBox(level, chunkBox, 0.8F, 0, 2, 0, 2, 2, last, CAVE_AIR, SALT_CEILING);
            if (spiderCorridor) {
                maybeBox(level, chunkBox, 0.6F, 0, 0, 0, 2, 1, last, web, SALT_WEB);
            }
            for (int section = 0; section < numSections; section++) {
                int k = 2 + section * 5;
                // 支架：两根虚空栅栏立柱，上面虚空石横梁；1/4 只有两头的虚空石（原作 L281~291）
                generateBox(level, chunkBox, 0, 0, k, 0, 1, k, fence, CAVE_AIR, false);
                generateBox(level, chunkBox, 2, 0, k, 2, 1, k, fence, CAVE_AIR, false);
                if (localUnit(1, 2, k, SALT_BEAM) < 0.25F) {
                    placeBlock(level, voidStone, 0, 2, k, chunkBox);
                    placeBlock(level, voidStone, 2, 2, k, chunkBox);
                } else {
                    generateBox(level, chunkBox, 0, 2, k, 2, 2, k, voidStone, CAVE_AIR, false);
                    // 墙上火把挂在横梁两侧（原版 L409~410 的朝向写法：局部 SOUTH / NORTH，放下时随朝向变换，都背对横梁）
                    maybePlace(level, chunkBox, 0.05F, 1, 2, k - 1, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH), SALT_TORCH);
                    maybePlace(level, chunkBox, 0.05F, 1, 2, k + 1, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH), SALT_TORCH);
                }
                maybePlace(level, chunkBox, 0.1F, 0, 2, k - 1, web, SALT_WEB);
                maybePlace(level, chunkBox, 0.1F, 2, 2, k - 1, web, SALT_WEB);
                maybePlace(level, chunkBox, 0.1F, 0, 2, k + 1, web, SALT_WEB);
                maybePlace(level, chunkBox, 0.1F, 2, 2, k + 1, web, SALT_WEB);
                maybePlace(level, chunkBox, 0.05F, 0, 2, k - 2, web, SALT_WEB);
                maybePlace(level, chunkBox, 0.05F, 2, 2, k - 2, web, SALT_WEB);
                maybePlace(level, chunkBox, 0.05F, 0, 2, k + 2, web, SALT_WEB);
                maybePlace(level, chunkBox, 0.05F, 2, 2, k + 2, web, SALT_WEB);
                // 运输矿车（箱子），每组两侧各 1/100（原作 L305~311）
                if (localUnit(2, 0, k - 1, SALT_CART) < 0.01F) {
                    placeCart(level, chunkBox, random, 2, 0, k - 1);
                }
                if (localUnit(0, 0, k + 1, SALT_CART) < 0.01F) {
                    placeCart(level, chunkBox, random, 0, 0, k + 1);
                }
            }
            if (spiderCorridor) {
                placeSpawner(level, chunkBox, random);
            }
            // 地板下是空气的格子补虚空石（原作 L331~341）
            for (int x = 0; x <= 2; x++) {
                for (int z = 0; z <= last; z++) {
                    if (getBlock(level, x, -1, z, chunkBox).isAir()) {
                        placeBlock(level, voidStone, x, -1, z, chunkBox);
                    }
                }
            }
            // 铁轨 70%，铁轨那一排和两侧的地板 70% 换虚空石（原作 L343~351，原作写成 x = −1、0、1）
            if (hasRails) {
                BlockState rail = Blocks.RAIL.defaultBlockState();
                for (int z = 0; z <= last; z++) {
                    if (localUnit(1, 0, z, SALT_RAIL) < 0.7F) {
                        placeBlock(level, rail, 1, 0, z, chunkBox);
                    }
                    for (int x = 0; x <= 2; x++) {
                        if (localUnit(x, -1, z, SALT_RAIL_BED) < 0.7F) {
                            placeBlock(level, voidStone, x, -1, z, chunkBox);
                        }
                    }
                }
            }
        }

        /** 运输矿车：那一格是空气才放（垫一块虚空石、一段铁轨、一辆带原版废弃矿井战利品的运输矿车，原作 L243~258） */
        private void placeCart(WorldGenLevel level, BoundingBox chunkBox, RandomSource random, int x, int y, int z) {
            BlockPos pos = getWorldPos(x, y, z);
            if (!chunkBox.isInside(pos) || !level.getBlockState(pos).isAir()) {
                return;
            }
            placeBlock(level, HoannaStructureBlocks.voidStone(), x, y - 1, z, chunkBox);
            placeBlock(level, Blocks.RAIL.defaultBlockState(), x, y, z, chunkBox);
            MinecartChest cart = new MinecartChest(level.getLevel(), pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
            cart.setLootTable(BuiltInLootTables.ABANDONED_MINESHAFT, random.nextLong());
            level.addFreshEntity(cart);
        }

        /**
         * 洞穴蜘蛛刷怪笼：位置按拼件种子定死（第几组、组内偏移 −1~+1），只有包含它的那个区块放一次。
         * 原作「第一个抽到本区块位置的那组放、放过就记一笔」，结果看区块生成顺序。
         */
        private void placeSpawner(WorldGenLevel level, BoundingBox chunkBox, RandomSource random) {
            BlockPos pos = spawnerPos();
            if (chunkBox.isInside(pos)) {
                level.setBlock(pos, Blocks.SPAWNER.defaultBlockState(), 2);
                if (level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner) {
                    spawner.setEntityId(EntityType.CAVE_SPIDER, random);
                }
            }
        }

        /** 刷怪笼的世界坐标（只对洞穴蜘蛛走廊有意义） */
        public BlockPos spawnerPos() {
            long hash = mix(seed() ^ SALT_SPAWNER);
            int section = Math.min(numSections - 1, (int) (unit(hash) * numSections));
            int offset = Math.min(2, (int) (unit(mix(hash)) * 3));
            return getWorldPos(1, 0, 2 + section * 5 - 1 + offset).immutable();
        }
    }

    // ---------------------------------------------------------------- 十字路口

    /**
     * 十字路口：5 × 5（或两层 5 × 7 高），没有朝向（局部坐标 = 世界坐标），四角虚空石柱，脚下是空气的补虚空石。
     * NBT：{@code tf}（两层）、{@code D}（进来的方向，2D 编号）。
     */
    public static final class Crossing extends ShaftPiece {

        private final Direction direction;
        private final boolean twoFloored;

        public Crossing(int genDepth, RandomSource random, BoundingBox box, Direction direction) {
            super(UndergroundStructures.SHAFT_CROSSING.get(), genDepth, box, random.nextLong());
            this.direction = direction;
            this.twoFloored = box.getYSpan() > 3;
        }

        /** 读档 */
        public Crossing(CompoundTag tag) {
            super(UndergroundStructures.SHAFT_CROSSING.get(), tag);
            this.twoFloored = tag.getBoolean("tf");
            this.direction = Direction.from2DDataValue(tag.getInt("D"));
        }

        @Override
        protected void saveExtra(StructurePieceSerializationContext context, CompoundTag tag) {
            tag.putBoolean("tf", twoFloored);
            tag.putInt("D", direction.get2DDataValue());
        }

        public boolean isTwoFloored() {
            return twoFloored;
        }

        /** 原版 {@code findCrossing}：1/4 两层（高 7） */
        @Nullable
        static BoundingBox findCrossing(StructurePieceAccessor pieces, RandomSource random, int x, int y, int z, Direction direction) {
            int height = random.nextInt(4) == 0 ? 6 : 2;
            BoundingBox box = switch (direction) {
                case SOUTH -> new BoundingBox(-1, 0, 0, 3, height, 4);
                case WEST -> new BoundingBox(-4, 0, -1, 0, height, 3);
                case EAST -> new BoundingBox(0, 0, -1, 4, height, 3);
                default -> new BoundingBox(-1, 0, -4, 3, height, 0);   // NORTH
            };
            box.move(x, y, z);
            return pieces.findCollisionPiece(box) != null ? null : box;
        }

        @Override
        public void addChildren(StructurePiece start, StructurePieceAccessor pieces, RandomSource random) {
            int depth = getGenDepth();
            BoundingBox b = boundingBox;
            switch (direction) {
                case SOUTH -> {
                    generateAndAddPiece(start, pieces, random, b.minX() + 1, b.minY(), b.maxZ() + 1, Direction.SOUTH, depth);
                    generateAndAddPiece(start, pieces, random, b.minX() - 1, b.minY(), b.minZ() + 1, Direction.WEST, depth);
                    generateAndAddPiece(start, pieces, random, b.maxX() + 1, b.minY(), b.minZ() + 1, Direction.EAST, depth);
                }
                case WEST -> {
                    generateAndAddPiece(start, pieces, random, b.minX() + 1, b.minY(), b.minZ() - 1, Direction.NORTH, depth);
                    generateAndAddPiece(start, pieces, random, b.minX() + 1, b.minY(), b.maxZ() + 1, Direction.SOUTH, depth);
                    generateAndAddPiece(start, pieces, random, b.minX() - 1, b.minY(), b.minZ() + 1, Direction.WEST, depth);
                }
                case EAST -> {
                    generateAndAddPiece(start, pieces, random, b.minX() + 1, b.minY(), b.minZ() - 1, Direction.NORTH, depth);
                    generateAndAddPiece(start, pieces, random, b.minX() + 1, b.minY(), b.maxZ() + 1, Direction.SOUTH, depth);
                    generateAndAddPiece(start, pieces, random, b.maxX() + 1, b.minY(), b.minZ() + 1, Direction.EAST, depth);
                }
                default -> {   // NORTH
                    generateAndAddPiece(start, pieces, random, b.minX() + 1, b.minY(), b.minZ() - 1, Direction.NORTH, depth);
                    generateAndAddPiece(start, pieces, random, b.minX() - 1, b.minY(), b.minZ() + 1, Direction.WEST, depth);
                    generateAndAddPiece(start, pieces, random, b.maxX() + 1, b.minY(), b.minZ() + 1, Direction.EAST, depth);
                }
            }
            if (twoFloored) {
                if (random.nextBoolean()) {
                    generateAndAddPiece(start, pieces, random, b.minX() + 1, b.minY() + 3 + 1, b.minZ() - 1, Direction.NORTH, depth);
                }
                if (random.nextBoolean()) {
                    generateAndAddPiece(start, pieces, random, b.minX() - 1, b.minY() + 3 + 1, b.minZ() + 1, Direction.WEST, depth);
                }
                if (random.nextBoolean()) {
                    generateAndAddPiece(start, pieces, random, b.maxX() + 1, b.minY() + 3 + 1, b.minZ() + 1, Direction.EAST, depth);
                }
                if (random.nextBoolean()) {
                    generateAndAddPiece(start, pieces, random, b.minX() + 1, b.minY() + 3 + 1, b.maxZ() + 1, Direction.SOUTH, depth);
                }
            }
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                                BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            if (touchesLiquid(level, chunkBox)) {
                return;
            }
            BoundingBox b = boundingBox;
            if (twoFloored) {
                generateBox(level, chunkBox, b.minX() + 1, b.minY(), b.minZ(), b.maxX() - 1, b.minY() + 3 - 1, b.maxZ(), CAVE_AIR, CAVE_AIR, false);
                generateBox(level, chunkBox, b.minX(), b.minY(), b.minZ() + 1, b.maxX(), b.minY() + 3 - 1, b.maxZ() - 1, CAVE_AIR, CAVE_AIR, false);
                generateBox(level, chunkBox, b.minX() + 1, b.maxY() - 2, b.minZ(), b.maxX() - 1, b.maxY(), b.maxZ(), CAVE_AIR, CAVE_AIR, false);
                generateBox(level, chunkBox, b.minX(), b.maxY() - 2, b.minZ() + 1, b.maxX(), b.maxY(), b.maxZ() - 1, CAVE_AIR, CAVE_AIR, false);
                generateBox(level, chunkBox, b.minX() + 1, b.minY() + 3, b.minZ() + 1, b.maxX() - 1, b.minY() + 3, b.maxZ() - 1, CAVE_AIR, CAVE_AIR, false);
            } else {
                generateBox(level, chunkBox, b.minX() + 1, b.minY(), b.minZ(), b.maxX() - 1, b.maxY(), b.maxZ(), CAVE_AIR, CAVE_AIR, false);
                generateBox(level, chunkBox, b.minX(), b.minY(), b.minZ() + 1, b.maxX(), b.maxY(), b.maxZ() - 1, CAVE_AIR, CAVE_AIR, false);
            }
            // 四角虚空石柱（原作 L478~481）
            BlockState voidStone = HoannaStructureBlocks.voidStone();
            generateBox(level, chunkBox, b.minX() + 1, b.minY(), b.minZ() + 1, b.minX() + 1, b.maxY(), b.minZ() + 1, voidStone, CAVE_AIR, false);
            generateBox(level, chunkBox, b.minX() + 1, b.minY(), b.maxZ() - 1, b.minX() + 1, b.maxY(), b.maxZ() - 1, voidStone, CAVE_AIR, false);
            generateBox(level, chunkBox, b.maxX() - 1, b.minY(), b.minZ() + 1, b.maxX() - 1, b.maxY(), b.minZ() + 1, voidStone, CAVE_AIR, false);
            generateBox(level, chunkBox, b.maxX() - 1, b.minY(), b.maxZ() - 1, b.maxX() - 1, b.maxY(), b.maxZ() - 1, voidStone, CAVE_AIR, false);
            // 脚下是空气的补虚空石（原作 L483~489）
            fillFloorWorld(level, chunkBox, b.minX(), b.minZ(), b.maxX(), b.maxZ(), b.minY() - 1);
        }
    }

    // ---------------------------------------------------------------- 大厅

    /**
     * 起点大厅：(x, 50, z) 起、8~13 × 5~10 × 8~13，泥土地面（原作如此，只换掉原本不是空气的格子）、下面三层挖空、上面挖成半球穹顶，
     * 连着支路的地方在墙上挖出和支路一样高的口。没有朝向（局部坐标 = 世界坐标）。NBT：{@code Entrances}（支路口的包围盒列表）。
     */
    public static final class Room extends ShaftPiece {

        private final List<BoundingBox> entrances = new ArrayList<>();

        public Room(int genDepth, RandomSource random, int x, int z) {
            super(UndergroundStructures.SHAFT_ROOM.get(), genDepth,
                    new BoundingBox(x, START_Y, z, x + 7 + random.nextInt(6), START_Y + 4 + random.nextInt(6), z + 7 + random.nextInt(6)),
                    random.nextLong());
        }

        /** 读档 */
        public Room(CompoundTag tag) {
            super(UndergroundStructures.SHAFT_ROOM.get(), tag);
            BoundingBox.CODEC.listOf().parse(NbtOps.INSTANCE, tag.getList("Entrances", Tag.TAG_INT_ARRAY))
                    .resultOrPartial(LOGGER::error).ifPresent(entrances::addAll);
        }

        @Override
        protected void saveExtra(StructurePieceSerializationContext context, CompoundTag tag) {
            BoundingBox.CODEC.listOf().encodeStart(NbtOps.INSTANCE, entrances).resultOrPartial(LOGGER::error)
                    .ifPresent(list -> tag.put("Entrances", list));
        }

        /** 支路口（测试用：东面的口 minX = 大厅 maxX − 1） */
        public List<BoundingBox> entrances() {
            return List.copyOf(entrances);
        }

        @Override
        public void addChildren(StructurePiece start, StructurePieceAccessor pieces, RandomSource random) {
            int depth = getGenDepth();
            BoundingBox b = boundingBox;
            int yRange = Math.max(1, b.getYSpan() - 3 - 1);
            for (int k = 0; k < b.getXSpan(); k += 4) {
                k += random.nextInt(b.getXSpan());
                if (k + 3 > b.getXSpan()) {
                    break;
                }
                ShaftPiece piece = generateAndAddPiece(start, pieces, random, b.minX() + k, b.minY() + random.nextInt(yRange) + 1, b.minZ() - 1, Direction.NORTH, depth);
                if (piece != null) {
                    BoundingBox c = piece.getBoundingBox();
                    entrances.add(new BoundingBox(c.minX(), c.minY(), b.minZ(), c.maxX(), c.maxY(), b.minZ() + 1));
                }
            }
            for (int k = 0; k < b.getXSpan(); k += 4) {
                k += random.nextInt(b.getXSpan());
                if (k + 3 > b.getXSpan()) {
                    break;
                }
                ShaftPiece piece = generateAndAddPiece(start, pieces, random, b.minX() + k, b.minY() + random.nextInt(yRange) + 1, b.maxZ() + 1, Direction.SOUTH, depth);
                if (piece != null) {
                    BoundingBox c = piece.getBoundingBox();
                    entrances.add(new BoundingBox(c.minX(), c.minY(), b.maxZ() - 1, c.maxX(), c.maxY(), b.maxZ()));
                }
            }
            for (int k = 0; k < b.getZSpan(); k += 4) {
                k += random.nextInt(b.getZSpan());
                if (k + 3 > b.getZSpan()) {
                    break;
                }
                ShaftPiece piece = generateAndAddPiece(start, pieces, random, b.minX() - 1, b.minY() + random.nextInt(yRange) + 1, b.minZ() + k, Direction.WEST, depth);
                if (piece != null) {
                    BoundingBox c = piece.getBoundingBox();
                    entrances.add(new BoundingBox(b.minX(), c.minY(), c.minZ(), b.minX() + 1, c.maxY(), c.maxZ()));
                }
            }
            // 原作 L570 这一圈写成从 minX − 1 出、朝西（和上一圈重复），东边永远没有支路；改成从 maxX + 1 出、朝东（同原版）
            for (int k = 0; k < b.getZSpan(); k += 4) {
                k += random.nextInt(b.getZSpan());
                if (k + 3 > b.getZSpan()) {
                    break;
                }
                ShaftPiece piece = generateAndAddPiece(start, pieces, random, b.maxX() + 1, b.minY() + random.nextInt(yRange) + 1, b.minZ() + k, Direction.EAST, depth);
                if (piece != null) {
                    BoundingBox c = piece.getBoundingBox();
                    entrances.add(new BoundingBox(b.maxX() - 1, c.minY(), c.minZ(), b.maxX(), c.maxY(), c.maxZ()));
                }
            }
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                                BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            if (touchesLiquid(level, chunkBox)) {
                return;
            }
            BoundingBox b = boundingBox;
            generateBox(level, chunkBox, b.minX(), b.minY(), b.minZ(), b.maxX(), b.minY(), b.maxZ(), Blocks.DIRT.defaultBlockState(), CAVE_AIR, true);
            generateBox(level, chunkBox, b.minX(), b.minY() + 1, b.minZ(), b.maxX(), Math.min(b.minY() + 3, b.maxY()), b.maxZ(), CAVE_AIR, CAVE_AIR, false);
            for (BoundingBox entrance : entrances) {
                generateBox(level, chunkBox, entrance.minX(), entrance.maxY() - 2, entrance.minZ(), entrance.maxX(), entrance.maxY(), entrance.maxZ(),
                        CAVE_AIR, CAVE_AIR, false);
            }
            generateUpperHalfSphere(level, chunkBox, b.minX(), b.minY() + 4, b.minZ(), b.maxX(), b.maxY(), b.maxZ(), CAVE_AIR, false);
        }

        @Override
        public void move(int x, int y, int z) {
            super.move(x, y, z);
            for (BoundingBox entrance : entrances) {
                entrance.move(x, y, z);
            }
        }
    }

    // ---------------------------------------------------------------- 阶梯

    /** 阶梯：3 宽、往下 5 格的斜通道，朝向 = 走向（同原版）。没有自己的 NBT 字段 */
    public static final class Stairs extends ShaftPiece {

        public Stairs(int genDepth, RandomSource random, BoundingBox box, Direction direction) {
            super(UndergroundStructures.SHAFT_STAIRS.get(), genDepth, box, random.nextLong());
            setOrientation(direction);
        }

        /** 读档 */
        public Stairs(CompoundTag tag) {
            super(UndergroundStructures.SHAFT_STAIRS.get(), tag);
        }

        /** 原版 {@code findStairs} */
        @Nullable
        static BoundingBox findStairs(StructurePieceAccessor pieces, int x, int y, int z, Direction direction) {
            BoundingBox box = switch (direction) {
                case SOUTH -> new BoundingBox(0, -5, 0, 2, 2, 8);
                case WEST -> new BoundingBox(-8, -5, 0, 0, 2, 2);
                case EAST -> new BoundingBox(0, -5, 0, 8, 2, 2);
                default -> new BoundingBox(0, -5, -8, 2, 2, 0);   // NORTH
            };
            box.move(x, y, z);
            return pieces.findCollisionPiece(box) != null ? null : box;
        }

        @Override
        public void addChildren(StructurePiece start, StructurePieceAccessor pieces, RandomSource random) {
            int depth = getGenDepth();
            Direction direction = getOrientation();
            BoundingBox b = boundingBox;
            if (direction != null) {
                switch (direction) {
                    case SOUTH -> generateAndAddPiece(start, pieces, random, b.minX(), b.minY(), b.maxZ() + 1, Direction.SOUTH, depth);
                    case WEST -> generateAndAddPiece(start, pieces, random, b.minX() - 1, b.minY(), b.minZ(), Direction.WEST, depth);
                    case EAST -> generateAndAddPiece(start, pieces, random, b.maxX() + 1, b.minY(), b.minZ(), Direction.EAST, depth);
                    default -> generateAndAddPiece(start, pieces, random, b.minX(), b.minY(), b.minZ() - 1, Direction.NORTH, depth);
                }
            }
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                                BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            if (touchesLiquid(level, chunkBox)) {
                return;
            }
            generateBox(level, chunkBox, 0, 5, 0, 2, 7, 1, CAVE_AIR, CAVE_AIR, false);
            generateBox(level, chunkBox, 0, 0, 7, 2, 2, 8, CAVE_AIR, CAVE_AIR, false);
            // 原作 L694 从 i = −20 开始，有 20 步挖到包围盒外；照原版只挖 0~4
            for (int i = 0; i < 5; i++) {
                generateBox(level, chunkBox, 0, 5 - i - (i < 4 ? 1 : 0), 2 + i, 2, 7 - i, 2 + i, CAVE_AIR, CAVE_AIR, false);
            }
        }
    }
}
