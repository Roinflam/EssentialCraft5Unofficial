package com.flameforge.essentialcraft5.worldgen.hoanna;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

import java.util.Set;

/**
 * 海纳尔三种结构（城镇废墟、古代墓穴、现代矿井）拼件的基类（第六批 F6b，batch6a §6.2 第 2 条）。M5 / M6 的 14 种拼件都继承它。
 * <p>
 * 它补了原版 {@link StructurePiece} 三处不够用的地方：
 * <ol>
 *   <li><b>形状后处理</b>：原版 {@code placeBlock} 只给自己名单里的原版栅栏 / 火把 / 梯子 / 铁栏杆（{@code SHAPE_CHECK_BLOCKS}）登记区块后处理，
 *       EC 的栅栏（{@code FenceBlock} 的新实例）不在名单里，放下去是四面不连的孤柱。这里覆盖 {@link #placeBlock}：放下的是
 *       {@link #needsShapeUpdate} 认的方块（所有 {@link CrossCollisionBlock}（栅栏、铁栏杆、玻璃板）、{@link WallBlock}、{@link TorchBlock}（含墙上火把）、
 *       {@link LadderBlock}）时，调 {@link #requestShapeUpdate}：生成中的区块（ProtoChunk）登记后处理，等区块进入 tick 时（四周邻居都已生成完）按邻居重算；
 *       已经是完整区块（{@code /place structure}、GameTest 直接调 postProcess）没有后处理阶段，当场按邻居重算。
 *       所以拼件里放栅栏、墙、梯子一律放默认状态，连接交给这里。</li>
 *   <li><b>跨区块一致的随机</b>：{@code postProcess} 拿到的 random 按「区块」播种，同一个拼件跨两个区块时两边抽出来的数对不上。
 *       要「这一格挖不挖、缺不缺」这种按位置决定的随机，用 {@link #posHash(int, int, int, long)} / {@link #unit(long)}：只看世界坐标和拼件的
 *       {@link #seed()}（布局时从生成随机里取、存进 NBT），哪个区块先生成、分几次生成，结果都一样。</li>
 *   <li><b>地面高度</b>：跨区块的拼件由并行的世界生成线程各自 postProcess，在那里现算地面高度会有竞态（塔断成两截）。
 *       高度一律在生成阶段（{@code findGenerationPoint} / 布局）按 {@link #groundYAt} 算好，存进 {@link #groundY()}（NBT 字段 {@value #TAG_GROUND}），
 *       或者直接 {@link #placeOnGround} 把包围盒挪上去。{@link #groundYAt} 每次约 3 ms，而且 /locate、探索类指南针会在主线程上触发，
 *       能少取就少取（城镇每件只取中心 1 列，见 {@code HoannaTownStructure}「定高的开销」）。</li>
 * </ol>
 * 另有 {@link #fillDown}：带深度上限、能穿过草丛雪层的「往下补柱子」（原版 {@code fillColumnDown} 没上限，悬崖边能一路补到世界底部附近）。
 * 挖坑、按楼层缺损见 {@link StructureRuin}；拼件用到的别的模块的方块见 {@link HoannaStructureBlocks}，箱子表见 {@code loot/HoannaLoot}。
 * <p>
 * <b>存档</b>：{@link #addAdditionalSaveData} 是 final，先写基类字段（{@value #TAG_SEED}、{@value #TAG_GROUND}），再调子类的 {@link #saveExtra}；
 * 子类的 NBT 构造器先 {@code super(type, tag)} 再读自己的字段。拼件类型的注册名写进区块存档，<b>上线后不能改名</b>（注册见 {@code ECHoannaStructures}）。
 * <p>
 * <b>只写当前区块</b>：postProcess 里所有写方块都要落在传进来的 chunkBox 里（{@link #placeBlock}、{@link #fillDown}、{@link StructureRuin} 都自己检查），
 * 生成时不改别的区块（policy §4.4：结构只在生成时写当前区块）。
 */
public abstract class HoannaStructurePiece extends StructurePiece {

    /** {@link #groundY()} 没设过 */
    public static final int NO_GROUND = Integer.MIN_VALUE;
    /** NBT：拼件的随机种子（long） */
    public static final String TAG_SEED = "ec5_seed";
    /** NBT：生成阶段定好的地面高度（int；没设过就不写） */
    public static final String TAG_GROUND = "ec5_ground";
    /** {@link #fillDown} 的默认深度上限 */
    public static final int DEFAULT_FILL_DEPTH = 64;

    /** 原版 StructurePiece.SHAPE_CHECK_BLOCKS（私有）的副本：这些方块原版 placeBlock 已经在生成中的区块登记过后处理，不重复登记 */
    private static final Set<Block> VANILLA_SHAPE_CHECK = Set.of(Blocks.NETHER_BRICK_FENCE, Blocks.TORCH, Blocks.WALL_TORCH, Blocks.OAK_FENCE,
            Blocks.SPRUCE_FENCE, Blocks.DARK_OAK_FENCE, Blocks.ACACIA_FENCE, Blocks.BIRCH_FENCE, Blocks.JUNGLE_FENCE, Blocks.LADDER, Blocks.IRON_BARS);

    private final long seed;
    private int groundY = NO_GROUND;

    /**
     * 布局时新建。
     *
     * @param seed 拼件自己的随机种子：从结构生成随机里取（{@code context.random().nextLong()}），同一座结构每次布局都一样
     */
    protected HoannaStructurePiece(StructurePieceType type, int genDepth, BoundingBox box, long seed) {
        super(type, genDepth, box);
        this.seed = seed;
        // 原版这个构造器不设镜像 / 旋转（留 null），子类忘了 setOrientation 时 placeBlock 会空指针；先按「无朝向」设好，子类要朝向再覆盖
        setOrientation(null);
    }

    /** 读档（拼件类型的 load 调它）；子类先 super 再读自己的字段 */
    protected HoannaStructurePiece(StructurePieceType type, CompoundTag tag) {
        super(type, tag);
        this.seed = tag.getLong(TAG_SEED);
        this.groundY = tag.contains(TAG_GROUND, Tag.TAG_INT) ? tag.getInt(TAG_GROUND) : NO_GROUND;
    }

    // ---------------------------------------------------------------- 存档

    @Override
    protected final void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putLong(TAG_SEED, seed);
        if (groundY != NO_GROUND) {
            tag.putInt(TAG_GROUND, groundY);
        }
        saveExtra(context, tag);
    }

    /** 子类写自己的字段（键名别和 {@value #TAG_SEED}、{@value #TAG_GROUND} 以及原版的 id / BB / O / GD 撞） */
    protected void saveExtra(StructurePieceSerializationContext context, CompoundTag tag) {
    }

    // ---------------------------------------------------------------- 跨区块一致的随机

    public final long seed() {
        return seed;
    }

    /**
     * 按世界坐标的哈希：{@code Mth.getSeed(x, y, z) ^ seed}。只看坐标和种子，同一格在哪个区块、第几次生成算出来都一样。
     * 要取 [0, 1) 的数用 {@link #unit(long)}；同一个拼件里几种互不相关的随机，给种子异或不同的常数（salt）。
     */
    public static long posHash(int x, int y, int z, long seed) {
        return Mth.getSeed(x, y, z) ^ seed;
    }

    /** 用本拼件的种子按世界坐标取哈希 */
    public final long posHash(int x, int y, int z) {
        return posHash(x, y, z, seed);
    }

    /** 哈希 → [0, 1) 的 float（先过一遍 SplitMix64 的混合，挨着的格子也不相关） */
    public static float unit(long hash) {
        return (mix(hash) >>> 40) * 0x1.0p-24F;
    }

    /** 本拼件在世界坐标 (x, y, z) 的 [0, 1) 随机数；salt 区分同一拼件里不同用途的随机 */
    public final float unitAt(int x, int y, int z, long salt) {
        return unit(posHash(x, y, z, seed ^ salt));
    }

    /** 同上，坐标是拼件自己的局部坐标（和 placeBlock 一样按朝向换算成世界坐标） */
    protected final float localUnit(int x, int y, int z, long salt) {
        return unitAt(getWorldX(x, z), getWorldY(y), getWorldZ(x, z), salt);
    }

    /** SplitMix64 的混合函数 */
    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    // ---------------------------------------------------------------- 地面高度（生成阶段算，postProcess 里只读）

    public final int groundY() {
        return groundY;
    }

    public final boolean hasGround() {
        return groundY != NO_GROUND;
    }

    public final void setGroundY(int groundY) {
        this.groundY = groundY;
    }

    /**
     * 世界坐标 (x, z) 这一列按噪声算出的第一格空位（{@code ChunkGenerator.getFirstFreeHeight}，不需要区块已经生成）。
     * 只在生成阶段调（{@code findGenerationPoint}、布局）；每次都要新建 NoiseChunk、按噪声算一整列（实测约 3 ms），
     * {@code /locate}、探索类指南针、主线程同步要区块时会在服务端主线程上跑。城镇每座约 17~18 次（每件中心 1 列），墓穴每出口 1 次。
     */
    public static int groundYAt(Structure.GenerationContext context, int x, int z, Heightmap.Types type) {
        return context.chunkGenerator().getFirstFreeHeight(x, z, type, context.heightAccessor(), context.randomState());
    }

    /**
     * 包围盒四角 + 中心 5 列的第一格空位取平均（向下取整），不低于 floor（对应原作「地面上第一格空气、最低 63」）。
     * 5 列 = 5 次噪声列（约 15 ms），件多的结构别用（城镇改成每件只取中心 1 列，见 {@code HoannaTownStructure}「定高的开销」）。
     * 用 {@link Heightmap.Types#OCEAN_FLOOR_WG} 时水底算地面，要传海平面托底。
     */
    public static int averageGroundY(Structure.GenerationContext context, BoundingBox box, Heightmap.Types type, int floor) {
        BlockPos center = box.getCenter();
        int sum = groundYAt(context, box.minX(), box.minZ(), type)
                + groundYAt(context, box.maxX(), box.minZ(), type)
                + groundYAt(context, box.minX(), box.maxZ(), type)
                + groundYAt(context, box.maxX(), box.maxZ(), type)
                + groundYAt(context, center.getX(), center.getZ(), type);
        return Math.max(floor, Math.floorDiv(sum, 5));
    }

    /**
     * 生成阶段把本拼件放到地面上：按自己的包围盒算 {@link #averageGroundY}，记进 {@link #groundY()}，再把包围盒上下挪到
     * 「底面 = 地面 + yOffset」（yOffset 传 −1 就是底层楼板埋进地面一格）。返回算出的地面高度。
     */
    public final int placeOnGround(Structure.GenerationContext context, Heightmap.Types type, int floor, int yOffset) {
        int ground = averageGroundY(context, boundingBox, type, floor);
        setGroundY(ground);
        move(0, ground + yOffset - boundingBox.minY(), 0);
        return ground;
    }

    // ---------------------------------------------------------------- 放方块与形状后处理

    /**
     * 放下后形状要按邻居重算的方块：所有 {@link CrossCollisionBlock}（栅栏、铁栏杆、玻璃板）、{@link WallBlock}、{@link TorchBlock}
     * （含墙上火把、红石火把：放在半空会被拆掉——所以火把要挂墙的用墙上火把）、{@link LadderBlock}。
     */
    public static boolean needsShapeUpdate(BlockState state) {
        Block block = state.getBlock();
        return block instanceof CrossCollisionBlock || block instanceof WallBlock || block instanceof TorchBlock || block instanceof LadderBlock;
    }

    /** 原版 placeBlock（只写 chunkBox 里、按朝向镜像旋转）+ 对 {@link #needsShapeUpdate} 的方块补形状后处理 */
    @Override
    protected void placeBlock(WorldGenLevel level, BlockState state, int x, int y, int z, BoundingBox chunkBox) {
        super.placeBlock(level, state, x, y, z, chunkBox);
        if (needsShapeUpdate(state)) {
            BlockPos pos = getWorldPos(x, y, z);
            if (chunkBox.isInside(pos) && level.getBlockState(pos).is(state.getBlock())) {
                requestShapeUpdate(level, level.getChunk(pos), pos, VANILLA_SHAPE_CHECK.contains(state.getBlock()));
            }
        }
    }

    /**
     * 让 pos 这一格按邻居重算形状（栅栏连接、火把 / 花草没了依托就拆掉）。只对 pos 所在的区块起作用，调用方保证 pos 在 chunkBox 里。
     * 生成中的区块：登记区块后处理（区块进入 tick 时、四周邻居都已生成完才算，所以跨区块的连接也对）；完整区块：当场算
     * （{@code Block.updateFromNeighbourShapes}，写回时不再连带更新邻居）。
     */
    public static void requestShapeUpdate(WorldGenLevel level, BlockPos pos) {
        requestShapeUpdate(level, level.getChunk(pos), pos, false);
    }

    /**
     * 同上，区块由调用方给（测试用它直接喂一个 ProtoChunk）。
     *
     * @param alreadyMarked 原版已经在生成中的区块里登记过（原版名单里的方块），这里不重复登记；完整区块照样当场算（原版在完整区块上只打一条警告）
     */
    public static void requestShapeUpdate(LevelAccessor level, ChunkAccess chunk, BlockPos pos, boolean alreadyMarked) {
        if (chunk instanceof LevelChunk || chunk instanceof ImposterProtoChunk) {
            BlockState state = level.getBlockState(pos);
            BlockState updated = Block.updateFromNeighbourShapes(state, level, pos);
            if (updated != state) {
                level.setBlock(pos, updated, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        } else if (!alreadyMarked) {
            chunk.markPosForPostprocessing(pos);
        }
    }

    /**
     * 从拼件局部坐标 (x, y, z) 开始往下补 state，直到碰到实心的东西（不是空气、液体、草丛雪层这类可替换的方块），最多 maxDepth 格，只写 chunkBox 里。
     * 对应原作 {@code replaceAirAndLiquidDownwards}（塔的四角、柱脚补到地面）。原版 {@code fillColumnDown} 没有深度上限、碰到草丛就停（柱子悬在草上）。
     *
     * @return 补了几格
     */
    protected int fillDown(WorldGenLevel level, BlockState state, int x, int y, int z, BoundingBox chunkBox, int maxDepth) {
        BlockPos.MutableBlockPos pos = getWorldPos(x, y, z);
        int bottom = level.getMinBuildHeight() + 1;
        boolean shape = needsShapeUpdate(state);
        int placed = 0;
        while (placed < maxDepth && pos.getY() > bottom && chunkBox.isInside(pos) && canFillThrough(level.getBlockState(pos))) {
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
            if (shape) {
                requestShapeUpdate(level, pos);
            }
            placed++;
            pos.move(Direction.DOWN);
        }
        return placed;
    }

    /** {@link #fillDown}，深度上限 {@value #DEFAULT_FILL_DEPTH} */
    protected int fillDown(WorldGenLevel level, BlockState state, int x, int y, int z, BoundingBox chunkBox) {
        return fillDown(level, state, x, y, z, chunkBox, DEFAULT_FILL_DEPTH);
    }

    /** {@link #fillDown} 能穿过去的：空气、液体、可替换的（草丛、雪层、藤蔓……）、原版能被结构替换的（发光地衣、海草） */
    public static boolean canFillThrough(BlockState state) {
        return state.isAir() || state.liquid() || state.canBeReplaced() || state.is(Blocks.GLOW_LICHEN)
                || state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS);
    }
}
