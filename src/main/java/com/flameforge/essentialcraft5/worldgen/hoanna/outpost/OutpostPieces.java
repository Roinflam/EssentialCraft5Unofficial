package com.flameforge.essentialcraft5.worldgen.hoanna.outpost;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.hoanna.nest.HoannaNests;
import com.flameforge.essentialcraft5.registry.ECEssence;
import com.flameforge.essentialcraft5.registry.ECInquisitoriumOutpost;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.worldgen.hoanna.HoannaStructureBlocks;
import com.flameforge.essentialcraft5.worldgen.hoanna.HoannaStructurePiece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
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

/**
 * 审判庭前哨的拼件（第十三批 13e-S3，batch13.md §6.4；HS §2.4.3）。布局见 {@link InquisitoriumOutpostStructure}；基类 {@link HoannaStructurePiece}：
 * 种子、地面高度存进 NBT，跨区块一致的随机（{@link #unitAt}）、只在 chunkBox 里写的 {@code placeBlock}、{@code fillDown} 都在那里。
 * 所有拼件都不设朝向，局部坐标就是世界坐标。
 * <p>
 * <b>只动天然方块</b>（policy §4.19；HS §2.4.3「场地」）：削平、铺地板、补地基只碰 {@link #clearable} 认的格子——方块标签
 * {@code essentialcraft5:outpost_clearable}（石头类、泥土类、草、树叶、原木、沙砾、沙和地上的花草，数据包可改）加上「可替换、不含液体」的
 * （草丛、雪层、藤蔓这种本来就是一放东西就没的）；<b>有方块实体的格子一律不动</b>（{@code BlockState.hasBlockEntity}），别的方块（黑曜石、影晶簇、别的模组放的东西）原样留着。
 * 只在世界生成时写，只写当前区块（chunkBox），同一格不管哪个区块先生成、分几次生成结果都一样（随机全按坐标哈希）。
 * <p>
 * NBT：场地、信标座、营地只有基类两项（{@code ec5_seed}、{@code ec5_ground}）；石柱另存 {@value Pylon#TAG_HEIGHT}、{@value Pylon#TAG_BROKEN}。上线后只能加、不能改含义。
 */
public final class OutpostPieces {

    /** 营地箱子的战利品表（{@code tools/gen/hoanna_loot_outpost.py} 出） */
    public static final ResourceLocation CHEST_LOOT = EssentialCraft5.id("chests/inquisitorium_outpost");
    /** 营地巢穴的类型（13d 的 {@code data/essentialcraft5/hoanna_nests/outpost_camp.json}） */
    public static final ResourceLocation CAMP_NEST = EssentialCraft5.id("outpost_camp");
    /** 地板缺损的比例 */
    public static final float MISSING_TILE = 0.15F;
    /** 营地地板缺损的比例 */
    public static final float CAMP_MISSING_TILE = 0.2F;

    private static final long SALT_MISSING = 0x5DEECE66DL;
    private static final long SALT_BROKEN = 0x2545F4914F6CDD1DL;

    private OutpostPieces() {
    }

    // ================================================================== 共用

    /** 能削、能铺地板、能补地基的「天然方块」：标签 {@code outpost_clearable}，或可替换且不含液体；有方块实体的一律不算 */
    @SuppressWarnings("deprecation")
    public static boolean clearable(BlockState state) {
        if (state.hasBlockEntity()) {
            return false;
        }
        return state.is(ECInquisitoriumOutpost.OUTPOST_CLEARABLE) || (state.canBeReplaced() && state.getFluidState().isEmpty());
    }

    /** 能铺地板 / 补地基的格子：空气、液体、可替换的、天然方块；有方块实体的不算 */
    public static boolean fillable(BlockState state) {
        if (state.hasBlockEntity()) {
            return false;
        }
        return state.isAir() || !state.getFluidState().isEmpty() || state.canBeReplaced() || state.is(ECInquisitoriumOutpost.OUTPOST_CLEARABLE);
    }

    /** 往下补地基能穿过的格子：空气、液体、可替换的（同基类 fillDown；碰到实心的天然地面就停） */
    public static boolean openBelow(BlockState state) {
        return !state.hasBlockEntity() && HoannaStructurePiece.canFillThrough(state);
    }

    /** 前哨自己铺的方块（地板、地基、碎石）：石柱、信标座可以盖在上面 */
    public static boolean outpostBlock(BlockState state) {
        return state.is(ECMaterials.FORTIFIED_STONE.get()) || state.equals(HoannaStructureBlocks.voidStone()) || state.is(Blocks.COBBLED_DEEPSLATE);
    }

    /** 石柱、信标座能占的格子：{@link #fillable} 认的，或前哨自己铺的 */
    public static boolean buildable(BlockState state) {
        return fillable(state) || (!state.hasBlockEntity() && outpostBlock(state));
    }

    /** 场地地板的花纹：按到中心的距离每 3 格一圈、每 45° 一扇，虚空石 / 强化石交错 */
    public static BlockState tile(int dx, int dz) {
        int ring = (int) (Math.sqrt(dx * dx + dz * dz) / 3D);
        double angle = Math.atan2(dz, dx) + Math.PI;
        int sector = Mth.floor(angle / (Math.PI / 4D)) & 7;
        return ((ring + sector) & 1) == 0 ? HoannaStructureBlocks.voidStone() : fortified();
    }

    static BlockState fortified() {
        return ECMaterials.FORTIFIED_STONE.get().defaultBlockState();
    }

    /** 缺损的地板下面是空的时候填的碎石 */
    static BlockState rubble() {
        return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
    }

    /** 写一格（只写 chunkBox 里、不动有方块实体的格子；不通知邻居，同 placeBlock） */
    static void put(WorldGenLevel level, BoundingBox chunkBox, BlockPos pos, BlockState state) {
        if (chunkBox.isInside(pos)) {
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
    }

    // ================================================================== 场地

    /**
     * {@code outpost_arena}：半径 18 的圆盘。逐列：
     * <ol>
     *   <li>场地面以上 {@value InquisitoriumOutpostStructure#CLEAR_HEIGHT} 格里的天然方块削成空气（更高的山顶照留）；</li>
     *   <li>场地面那一格铺地板（{@link #tile} 的花纹）；15% 的格子是「缺损」：原来是实心的天然地面就留着，空的填一块碎石；</li>
     *   <li>地板下面是空的（空气、液体、草丛）就往下补强化石地基，最多 {@value InquisitoriumOutpostStructure#FILL_DEPTH} 格；补不到底的悬崖照留，石柱当支撑。</li>
     * </ol>
     * 三步都只动 {@link #clearable} / {@link #fillable} 认的格子，有方块实体的、非天然的方块原样留着。
     */
    public static final class Arena extends HoannaStructurePiece {

        public Arena(int genDepth, BoundingBox box, long seed) {
            super(OutpostStructures.OUTPOST_ARENA.get(), genDepth, box, seed);
        }

        public Arena(CompoundTag tag) {
            super(OutpostStructures.OUTPOST_ARENA.get(), tag);
        }

        public int centerX() {
            return boundingBox.minX() + InquisitoriumOutpostStructure.ARENA_RADIUS;
        }

        public int centerZ() {
            return boundingBox.minZ() + InquisitoriumOutpostStructure.ARENA_RADIUS;
        }

        public int floorY() {
            return groundY() - 1;
        }

        /** (x, z) 在不在圆盘里 */
        public boolean inDisc(int x, int z) {
            int dx = x - centerX();
            int dz = z - centerZ();
            double r = InquisitoriumOutpostStructure.ARENA_RADIUS + 0.5D;
            return dx * dx + dz * dz <= r * r;
        }

        @Override
        @SuppressWarnings("deprecation")
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                                BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            if (!hasGround()) {
                return;
            }
            int floor = floorY();
            int minX = Math.max(boundingBox.minX(), chunkBox.minX());
            int maxX = Math.min(boundingBox.maxX(), chunkBox.maxX());
            int minZ = Math.max(boundingBox.minZ(), chunkBox.minZ());
            int maxZ = Math.min(boundingBox.maxZ(), chunkBox.maxZ());
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            BlockState air = Blocks.AIR.defaultBlockState();
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!inDisc(x, z)) {
                        continue;
                    }
                    for (int y = floor + 1; y <= floor + InquisitoriumOutpostStructure.CLEAR_HEIGHT; y++) {
                        BlockState state = level.getBlockState(pos.set(x, y, z));
                        if (!state.isAir() && clearable(state)) {
                            put(level, chunkBox, pos, air);
                        }
                    }
                    BlockState at = level.getBlockState(pos.set(x, floor, z));
                    if (fillable(at)) {
                        BlockState tile;
                        if (unitAt(x, floor, z, SALT_MISSING) < MISSING_TILE) {
                            tile = at.isSolid() && at.getFluidState().isEmpty() && !at.canBeReplaced() && !at.is(BlockTags.LEAVES) ? at : rubble();
                        } else {
                            tile = tile(x - centerX(), z - centerZ());
                        }
                        if (tile != at) {
                            put(level, chunkBox, pos, tile);
                        }
                    }
                    BlockState foundation = fortified();
                    for (int y = floor - 1; y >= floor - InquisitoriumOutpostStructure.FILL_DEPTH && y > level.getMinBuildHeight(); y--) {
                        BlockState below = level.getBlockState(pos.set(x, y, z));
                        if (!openBelow(below)) {
                            break;
                        }
                        put(level, chunkBox, pos, foundation);
                    }
                }
            }
        }
    }

    // ================================================================== 石柱

    /**
     * {@code outpost_pylon}：2 × 2 的虚空石柱，从场地面立起 height 格；完好的顶上一格魔力光源，断的四列高低不齐（每列少 0~1 格）、没有光源。
     * 柱脚往下当支撑：穿过空气 / 液体 / 草丛和场地的强化石地基，一直补到碰到实心的东西，最多 {@value InquisitoriumOutpostStructure#PYLON_SUPPORT} 格。
     */
    public static final class Pylon extends HoannaStructurePiece {

        public static final String TAG_HEIGHT = "ec5_height";
        public static final String TAG_BROKEN = "ec5_broken";

        private final int height;
        private final boolean broken;

        public Pylon(int genDepth, BoundingBox box, long seed, int height, boolean broken) {
            super(OutpostStructures.OUTPOST_PYLON.get(), genDepth, box, seed);
            this.height = height;
            this.broken = broken;
        }

        /** 完好、高 {@value InquisitoriumOutpostStructure#PYLON_MIN} 的一根（测试用） */
        public Pylon(int genDepth, BoundingBox box, long seed) {
            this(genDepth, box, seed, InquisitoriumOutpostStructure.PYLON_MIN, false);
        }

        public Pylon(CompoundTag tag) {
            super(OutpostStructures.OUTPOST_PYLON.get(), tag);
            this.height = tag.getInt(TAG_HEIGHT);
            this.broken = tag.getBoolean(TAG_BROKEN);
        }

        @Override
        protected void saveExtra(StructurePieceSerializationContext context, CompoundTag tag) {
            tag.putInt(TAG_HEIGHT, height);
            tag.putBoolean(TAG_BROKEN, broken);
        }

        public int height() {
            return height;
        }

        public boolean broken() {
            return broken;
        }

        /** (x, z) 这一列在场地面以上几格（断的柱子每列按坐标哈希少 0~1 格） */
        public int columnHeight(int x, int z) {
            if (!broken) {
                return height;
            }
            return Math.max(1, height - (unitAt(x, 0, z, SALT_BROKEN) < 0.5F ? 1 : 0));
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                                BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            if (!hasGround()) {
                return;
            }
            int floor = groundY() - 1;
            BlockState stone = HoannaStructureBlocks.voidStone();
            Block foundation = ECMaterials.FORTIFIED_STONE.get();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = boundingBox.minX(); x <= boundingBox.maxX(); x++) {
                for (int z = boundingBox.minZ(); z <= boundingBox.maxZ(); z++) {
                    if (!chunkBox.isInside(pos.set(x, floor, z))) {
                        continue;
                    }
                    int top = floor + columnHeight(x, z);
                    for (int y = floor; y <= top; y++) {
                        if (buildable(level.getBlockState(pos.set(x, y, z)))) {
                            put(level, chunkBox, pos, stone);
                        }
                    }
                    for (int y = floor - 1; y >= floor - InquisitoriumOutpostStructure.PYLON_SUPPORT && y > level.getMinBuildHeight(); y--) {
                        BlockState below = level.getBlockState(pos.set(x, y, z));
                        if (!openBelow(below) && !below.is(foundation)) {
                            break;
                        }
                        put(level, chunkBox, pos, stone);
                    }
                }
            }
            if (!broken) {
                BlockPos light = new BlockPos(boundingBox.minX(), floor + height + 1, boundingBox.minZ());
                if (chunkBox.isInside(light) && level.getBlockState(light).isAir()) {
                    put(level, chunkBox, light, ECEssence.MAGIC_LIGHT.get().defaultBlockState());
                }
            }
        }
    }

    // ================================================================== 信标座

    /** {@code outpost_dais}：中央 5 × 5、一格高的信标座（底下垫一层强化石，不留缺损）。站在中心召审判庭哨兵：离坐标 0 格、露天 */
    public static final class Dais extends HoannaStructurePiece {

        public Dais(int genDepth, BoundingBox box, long seed) {
            super(OutpostStructures.OUTPOST_DAIS.get(), genDepth, box, seed);
        }

        public Dais(CompoundTag tag) {
            super(OutpostStructures.OUTPOST_DAIS.get(), tag);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                                BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            if (!hasGround()) {
                return;
            }
            int floor = groundY() - 1;
            BlockState dais = ECInquisitoriumOutpost.INQUISITORIUM_DAIS.get().defaultBlockState();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = boundingBox.minX(); x <= boundingBox.maxX(); x++) {
                for (int z = boundingBox.minZ(); z <= boundingBox.maxZ(); z++) {
                    if (buildable(level.getBlockState(pos.set(x, floor, z)))) {
                        put(level, chunkBox, pos, fortified());
                    }
                    if (buildable(level.getBlockState(pos.set(x, floor + 1, z)))) {
                        put(level, chunkBox, pos, dais);
                    }
                }
            }
        }
    }

    // ================================================================== 营地

    /**
     * {@code outpost_camp}：场外的营地，7 × 7、整个在一个区块里（{@link InquisitoriumOutpostStructure#campFitsChunk}），所以地面在这里按本区块已有的方块找
     * （{@link #findGround}），只有这一个区块写它、没有竞态，不在布局时多算噪声列。
     * 中间 5 × 5 铺地板（20% 缺损）、四角各一根矮桩（虚空石 + 虚空栅栏），正中间巢穴核心 {@code outpost_camp}（13d 的类型：审判庭巡卫 ×4 + 执行机），
     * 核心东边两格一个箱子 {@code chests/inquisitorium_outpost}。地面以上 4 格的天然方块削掉。包围盒上下各 {@value InquisitoriumOutpostStructure#CAMP_REACH} 格里找不到地面就不放。
     */
    public static final class Camp extends HoannaStructurePiece {

        private static final long SALT_CHEST = 0x632BE59BD9B4E019L;

        public Camp(int genDepth, BoundingBox box, long seed) {
            super(OutpostStructures.OUTPOST_CAMP.get(), genDepth, box, seed);
        }

        public Camp(CompoundTag tag) {
            super(OutpostStructures.OUTPOST_CAMP.get(), tag);
        }

        public int centerX() {
            return boundingBox.minX() + InquisitoriumOutpostStructure.CAMP_HALF;
        }

        public int centerZ() {
            return boundingBox.minZ() + InquisitoriumOutpostStructure.CAMP_HALF;
        }

        /**
         * 营地中心这一列的地面（实心的那一格）：从包围盒顶下 6 格往下找，跳过空气、液体、草丛、树叶、原木，第一格实心的就是。
         * 不读高度图（世界生成里的 WG 高度图和完整区块里的不是同一张）；一开始就是实心的（地面比场地高出 {@value InquisitoriumOutpostStructure#CAMP_REACH} 格以上）
         * 或一路找不到，返回 {@link Integer#MIN_VALUE}，这个营地不放。
         */
        @SuppressWarnings("deprecation")
        public int findGround(WorldGenLevel level) {
            int x = centerX();
            int z = centerZ();
            int top = Math.min(boundingBox.maxY() - 6, level.getMaxBuildHeight() - 6);
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int y = top; y >= Math.max(boundingBox.minY(), level.getMinBuildHeight()); y--) {
                BlockState state = level.getBlockState(pos.set(x, y, z));
                if (state.isAir() || !state.getFluidState().isEmpty() || state.canBeReplaced() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) {
                    continue;
                }
                return y < top && state.isSolid() ? y : Integer.MIN_VALUE;
            }
            return Integer.MIN_VALUE;
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                                BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            int cx = centerX();
            int cz = centerZ();
            if (!chunkBox.isInside(new BlockPos(cx, chunkBox.minY(), cz))) {
                return;
            }
            int ground = findGround(level);
            if (ground == Integer.MIN_VALUE) {
                return;
            }
            int half = InquisitoriumOutpostStructure.CAMP_HALF;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            BlockState air = Blocks.AIR.defaultBlockState();
            for (int dx = -half; dx <= half; dx++) {
                for (int dz = -half; dz <= half; dz++) {
                    int x = cx + dx;
                    int z = cz + dz;
                    for (int y = ground + 1; y <= ground + 4; y++) {
                        BlockState state = level.getBlockState(pos.set(x, y, z));
                        if (!state.isAir() && clearable(state)) {
                            put(level, chunkBox, pos, air);
                        }
                    }
                    if (Math.abs(dx) <= 2 && Math.abs(dz) <= 2) {
                        BlockState at = level.getBlockState(pos.set(x, ground, z));
                        if (fillable(at) && unitAt(x, ground, z, SALT_MISSING) >= CAMP_MISSING_TILE) {
                            put(level, chunkBox, pos, ((dx + dz) & 1) == 0 ? fortified() : HoannaStructureBlocks.voidStone());
                        }
                    }
                }
            }
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    BlockPos post = new BlockPos(cx + sx * half, ground + 1, cz + sz * half);
                    if (level.getBlockState(post).isAir()) {
                        put(level, chunkBox, post, HoannaStructureBlocks.voidStone());
                        placeBlock(level, HoannaStructureBlocks.voidFence(), post.getX(), post.getY() + 1, post.getZ(), chunkBox);
                    }
                }
            }
            BlockPos core = new BlockPos(cx, ground + 1, cz);
            if (chunkBox.isInside(core) && !level.getBlockState(core).hasBlockEntity()) {
                HoannaNests.placeCore(level, core, CAMP_NEST);
            }
            BlockPos chest = new BlockPos(cx + 2, ground + 1, cz);
            if (chunkBox.isInside(chest) && !level.getBlockState(chest).hasBlockEntity()) {
                createChest(level, chunkBox, RandomSource.create(mix(seed() ^ SALT_CHEST)), chest, CHEST_LOOT,
                        Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST));
            }
        }
    }
}
