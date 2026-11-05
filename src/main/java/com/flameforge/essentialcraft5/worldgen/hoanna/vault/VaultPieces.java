package com.flameforge.essentialcraft5.worldgen.hoanna.vault;

import com.flameforge.essentialcraft5.block.FancyMaterial;
import com.flameforge.essentialcraft5.block.FancyPattern;
import com.flameforge.essentialcraft5.block.vault.VaultPedestalBlock;
import com.flameforge.essentialcraft5.block.vault.VaultSealBlock;
import com.flameforge.essentialcraft5.blockentity.VaultCoreBlockEntity;
import com.flameforge.essentialcraft5.registry.ECAlloys;
import com.flameforge.essentialcraft5.registry.ECDecorations;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.registry.ECSealedVault;
import com.flameforge.essentialcraft5.worldgen.hoanna.HoannaStructureBlocks;
import com.flameforge.essentialcraft5.worldgen.hoanna.HoannaStructurePiece;
import com.flameforge.essentialcraft5.worldgen.hoanna.StructureRuin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * 封印宝库的拼件（第十三批 13e-S1，batch13.md §6.4；布局见 {@link VaultLayout}）。基类 {@link HoannaStructurePiece}：种子、地面高度存进 NBT，
 * 跨区块一致的随机都在那里。朝向固定（{@code setOrientation(null)}），本类里的坐标一律是世界坐标，写方块走 {@link Base#put}
 * （只写 chunkBox 和自己包围盒的交集）。
 * <p>
 * 放的顺序（{@link SealedVaultStructure} 里加拼件的顺序，同一区块里按这个顺序写）：前厅 → 井 → 宝库室 → 平台和碑。
 * 井和前厅的包围盒在前厅东南角上下重叠：前厅先挖空、再由井在里面摆台阶、在天花板上开口。
 * <p>
 * 墙按 {@link #WALL_DECAY} 缺损（{@link StructureRuin#decay}，只挑墙体材料：强化石、虚空石；地板、谜题方块、灯、台阶不缺）。
 * 谜题方块（碑、祭座、锁孔、封印石、核心）挖不动，{@code StructureRuin.isRuinable} 本来就跳过硬度 &lt; 0 的方块。
 * <p>
 * NBT：前厅、宝库室多存一个 {@value #TAG_PUZZLE}（谜题种子：前厅按它给四座祭座刻元素，宝库室按它初始化核心）；井、平台和碑靠基类的地面高度。
 * <b>上线后只能加键、不能改含义</b>。
 */
public final class VaultPieces {

    /** NBT：谜题种子（long） */
    public static final String TAG_PUZZLE = "ec5_puzzle";
    /** 墙的缺损几率（batch13.md §6.4） */
    public static final float WALL_DECAY = 0.15F;

    static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private VaultPieces() {
    }

    // ================================================================== 共用

    private abstract static class Base extends HoannaStructurePiece {

        Base(StructurePieceType type, int genDepth, BoundingBox box, long seed) {
            super(type, genDepth, box, seed);
        }

        Base(StructurePieceType type, CompoundTag tag) {
            super(type, tag);
        }

        /** 世界坐标写一格：只写 chunkBox 和本拼件包围盒的交集 */
        protected final void put(WorldGenLevel level, BoundingBox chunkBox, BlockState state, int x, int y, int z) {
            if (chunkBox.isInside(x, y, z) && boundingBox.isInside(x, y, z)) {
                level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS);
            }
        }

        protected final void put(WorldGenLevel level, BoundingBox chunkBox, BlockState state, BlockPos pos) {
            put(level, chunkBox, state, pos.getX(), pos.getY(), pos.getZ());
        }

        /** 墙体缺损（只挑 {@link #isWall} 的方块） */
        protected final void decayWalls(WorldGenLevel level, BoundingBox chunkBox, BoundingBox region) {
            StructureRuin.decay(level, chunkBox, region, WALL_DECAY, seed(), VaultPieces::isWall);
        }
    }

    /** 缺损只挑的墙体材料：强化石、虚空石（地板的魔力护板不缺） */
    public static boolean isWall(BlockState state) {
        return state.is(ECMaterials.FORTIFIED_STONE.get()) || state.is(HoannaStructureBlocks.voidStone().getBlock());
    }

    static BlockState fortified() {
        return ECMaterials.FORTIFIED_STONE.get().defaultBlockState();
    }

    static BlockState plating() {
        return ECAlloys.MAGIC_PLATING.get().defaultBlockState();
    }

    static BlockState voidStone() {
        return HoannaStructureBlocks.voidStone();
    }

    static BlockState runic() {
        return ECDecorations.fancy(FancyMaterial.FORTIFIED_STONE, FancyPattern.RUNIC).get().defaultBlockState();
    }

    // ================================================================== 平台和碑

    /** {@code vault_stele}：地面 3 × 3 平台 + 1 × 1 × 5 先辈碑（井口南边） */
    public static final class Stele extends Base {

        public Stele(int genDepth, BoundingBox box, long seed) {
            super(VaultStructures.VAULT_STELE.get(), genDepth, box, seed);
        }

        public Stele(CompoundTag tag) {
            super(VaultStructures.VAULT_STELE.get(), tag);
        }

        /** 布局用：按宝库的布局建，地面记进基类 */
        public static Stele of(VaultLayout.Plan plan, long seed) {
            Stele piece = new Stele(0, plan.steleBox(), seed);
            piece.setGroundY(plan.ground());
            return piece;
        }

        /** 碑最下面那块（平台面上一格） */
        public BlockPos steleBase() {
            int ground = hasGround() ? groundY() : boundingBox.minY() + 1 + VaultLayout.PLATFORM_FILL;
            return new BlockPos(boundingBox.getCenter().getX(), ground, boundingBox.getCenter().getZ());
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                                BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            BlockPos stele = steleBase();
            int ground = stele.getY();
            BlockState tile = runic();
            BlockState fill = fortified();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = stele.getX() - 1; x <= stele.getX() + 1; x++) {
                for (int z = stele.getZ() - 1; z <= stele.getZ() + 1; z++) {
                    put(level, chunkBox, tile, x, ground - 1, z);
                    for (int y = ground; y < ground + VaultLayout.STELE_HEIGHT; y++) {
                        put(level, chunkBox, AIR, x, y, z);
                    }
                    if (chunkBox.isInside(x, ground - 2, z)) {
                        // 平台下面悬空的往下补，最多 PLATFORM_FILL 格（包围盒留够了这么深）
                        pos.set(x, ground - 2, z);
                        for (int i = 0; i < VaultLayout.PLATFORM_FILL && canFillThrough(level.getBlockState(pos)); i++) {
                            put(level, chunkBox, fill, pos);
                            pos.move(Direction.DOWN);
                        }
                    }
                }
            }
            BlockState steleBlock = ECSealedVault.ELDER_STELE.get().defaultBlockState();
            for (int i = 0; i < VaultLayout.STELE_HEIGHT; i++) {
                put(level, chunkBox, steleBlock, stele.above(i));
            }
        }
    }

    // ================================================================== 螺旋楼梯井

    /** {@code vault_shaft}：3 × 3 螺旋楼梯井（外墙 5 × 5），从井口一路下到前厅东南角 */
    public static final class Shaft extends Base {

        public Shaft(int genDepth, BoundingBox box, long seed) {
            super(VaultStructures.VAULT_SHAFT.get(), genDepth, box, seed);
        }

        public Shaft(CompoundTag tag) {
            super(VaultStructures.VAULT_SHAFT.get(), tag);
        }

        public static Shaft of(VaultLayout.Plan plan, long seed) {
            Shaft piece = new Shaft(1, plan.shaftBox(), seed);
            piece.setGroundY(plan.ground());
            return piece;
        }

        private int ground() {
            return hasGround() ? groundY() : boundingBox.maxY() - VaultLayout.SHAFT_CLEAR;
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                                BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            int ox = boundingBox.getCenter().getX();
            int oz = boundingBox.getCenter().getZ();
            int ground = ground();
            int floor = boundingBox.minY() - 1;
            int ceiling = floor + VaultLayout.ANTE_HEIGHT - 1;
            BlockState wall = fortified();
            // 前厅天花板往上：外墙一圈、里面挖空（台阶后面再摆）；井口以上只清里面
            for (int y = ceiling; y <= boundingBox.maxY(); y++) {
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        boolean rim = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                        if (rim) {
                            if (y <= ground - 1) {
                                put(level, chunkBox, wall, ox + dx, y, oz + dz);
                            }
                        } else if (dx != 0 || dz != 0 || y >= ground) {
                            put(level, chunkBox, AIR, ox + dx, y, oz + dz);
                        }
                    }
                }
            }
            // 井口南边那段墙上面清出一条路（从平台走过来）
            for (int dx = -1; dx <= 1; dx++) {
                for (int y = ground; y <= ground + 2; y++) {
                    put(level, chunkBox, AIR, ox + dx, y, oz + 2);
                }
            }
            // 中间的柱子：前厅地上到井口
            for (int y = floor + 1; y <= ground - 1; y++) {
                put(level, chunkBox, wall, ox, y, oz);
            }
            decayWalls(level, chunkBox, new BoundingBox(ox - 2, ceiling + 1, oz - 2, ox + 2, Math.max(ceiling + 1, ground - 1), oz + 2));
            // 台阶：第 i 级在外圈第 i 格、高 ground − 1 − i，最后一级落在前厅地上
            BlockState stairs = Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState();
            int steps = ground - 1 - floor;
            for (int i = 0; i < steps; i++) {
                int[] cell = VaultLayout.ringCell(i);
                put(level, chunkBox, stairs.setValue(StairBlock.FACING, VaultLayout.stairFacing(i)), ox + cell[0], ground - 1 - i, oz + cell[1]);
            }
        }
    }

    // ================================================================== 前厅

    /** {@code vault_antechamber}：前厅 11 × 7 × 11（东南西北四座祭座、中央锁孔，北墙门洞通封印门） */
    public static final class Antechamber extends Base {

        private final long puzzle;

        public Antechamber(int genDepth, BoundingBox box, long seed) {
            this(genDepth, box, seed, seed);
        }

        public Antechamber(int genDepth, BoundingBox box, long seed, long puzzle) {
            super(VaultStructures.VAULT_ANTECHAMBER.get(), genDepth, box, seed);
            this.puzzle = puzzle;
        }

        public Antechamber(CompoundTag tag) {
            super(VaultStructures.VAULT_ANTECHAMBER.get(), tag);
            this.puzzle = tag.contains(TAG_PUZZLE, Tag.TAG_LONG) ? tag.getLong(TAG_PUZZLE) : seed();
        }

        public static Antechamber of(VaultLayout.Plan plan, long seed, long puzzle) {
            return new Antechamber(1, plan.antechamberBox(), seed, puzzle);
        }

        public long puzzle() {
            return puzzle;
        }

        public BlockPos keyhole() {
            return new BlockPos(boundingBox.getCenter().getX(), boundingBox.minY() + 1, boundingBox.getCenter().getZ());
        }

        @Override
        protected void saveExtra(StructurePieceSerializationContext context, CompoundTag tag) {
            tag.putLong(TAG_PUZZLE, puzzle);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                                BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            BoundingBox b = boundingBox;
            BlockPos keyhole = keyhole();
            int cx = keyhole.getX();
            int cz = keyhole.getZ();
            int floor = b.minY();
            BlockState wall = fortified();
            BlockState plate = plating();
            BlockState ceiling = voidStone();
            for (int x = b.minX(); x <= b.maxX(); x++) {
                for (int z = b.minZ(); z <= b.maxZ(); z++) {
                    boolean side = x == b.minX() || x == b.maxX() || z == b.minZ() || z == b.maxZ();
                    for (int y = b.minY(); y <= b.maxY(); y++) {
                        BlockState state;
                        if (y == b.minY()) {
                            state = plate;
                        } else if (y == b.maxY()) {
                            state = ceiling;
                        } else if (side) {
                            boolean door = z == b.minZ() && Math.abs(x - cx) <= 1 && y <= floor + 3;
                            state = door ? AIR : wall;
                        } else {
                            state = AIR;
                        }
                        put(level, chunkBox, state, x, y, z);
                    }
                }
            }
            decayWalls(level, chunkBox, b);
            // 灯：东西墙、南墙正中各一盏，天花板西北一盏（东南角是井口）
            BlockState lamp = ECAlloys.ELEMENTAL_CRYSTAL_LAMP.get().defaultBlockState();
            put(level, chunkBox, lamp, b.minX(), floor + 3, cz);
            put(level, chunkBox, lamp, b.maxX(), floor + 3, cz);
            put(level, chunkBox, lamp, cx, floor + 3, b.maxZ());
            put(level, chunkBox, lamp, cx - 3, b.maxY(), cz - 3);
            put(level, chunkBox, ECSealedVault.VAULT_KEYHOLE.get().defaultBlockState(), keyhole);
            VaultPuzzle data = VaultPuzzle.of(puzzle);
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockState pedestal = ECSealedVault.VAULT_PEDESTAL.get().defaultBlockState()
                        .setValue(VaultPedestalBlock.ELEMENT, data.elementAt(side));
                put(level, chunkBox, pedestal, keyhole.relative(side, VaultLayout.PEDESTAL_DISTANCE));
            }
        }
    }

    // ================================================================== 宝库室

    /** {@code vault_chamber}：封印门 3 × 3（九块封印石）后的宝库室 9 × 6 × 9 + 两个侧龛、中央宝库核心 */
    public static final class Chamber extends Base {

        private final long puzzle;

        public Chamber(int genDepth, BoundingBox box, long seed) {
            this(genDepth, box, seed, seed);
        }

        public Chamber(int genDepth, BoundingBox box, long seed, long puzzle) {
            super(VaultStructures.VAULT_CHAMBER.get(), genDepth, box, seed);
            this.puzzle = puzzle;
        }

        public Chamber(CompoundTag tag) {
            super(VaultStructures.VAULT_CHAMBER.get(), tag);
            this.puzzle = tag.contains(TAG_PUZZLE, Tag.TAG_LONG) ? tag.getLong(TAG_PUZZLE) : seed();
        }

        public static Chamber of(VaultLayout.Plan plan, long seed, long puzzle) {
            return new Chamber(2, plan.chamberBox(), seed, puzzle);
        }

        public long puzzle() {
            return puzzle;
        }

        /** 宝库核心：包围盒东西正中、地板上一格，南北在主室正中（包围盒的南北边就是主室外墙） */
        public BlockPos core() {
            return new BlockPos(boundingBox.getCenter().getX(), boundingBox.minY() + 1, boundingBox.minZ() + VaultLayout.CHAMBER_HALF);
        }

        @Override
        protected void saveExtra(StructurePieceSerializationContext context, CompoundTag tag) {
            tag.putLong(TAG_PUZZLE, puzzle);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                                BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            BlockPos core = core();
            int cx = core.getX();
            int cz = core.getZ();
            int floor = boundingBox.minY();
            int top = floor + VaultLayout.CHAMBER_HEIGHT - 1;
            int half = VaultLayout.CHAMBER_HALF;
            BlockState wall = voidStone();
            BlockState plate = plating();
            BlockState pillar = fortified();
            // 主室
            for (int x = cx - half; x <= cx + half; x++) {
                for (int z = cz - half; z <= cz + half; z++) {
                    boolean side = Math.abs(x - cx) == half || Math.abs(z - cz) == half;
                    boolean corner = Math.abs(x - cx) == half - 1 && Math.abs(z - cz) == half - 1;
                    for (int y = floor; y <= top; y++) {
                        BlockState state;
                        if (y == floor) {
                            state = plate;
                        } else if (y == top || side) {
                            state = wall;
                        } else if (corner) {
                            state = pillar;
                        } else {
                            state = AIR;
                        }
                        put(level, chunkBox, state, x, y, z);
                    }
                }
            }
            // 两个侧龛（东西墙正中，3 宽 3 高 1 深）
            for (int dir = -1; dir <= 1; dir += 2) {
                int wallX = cx + dir * half;
                int nicheX = cx + dir * (half + 1);
                int backX = cx + dir * (half + VaultLayout.ALCOVE_DEPTH);
                for (int z = cz - 2; z <= cz + 2; z++) {
                    for (int y = floor; y <= floor + 4; y++) {
                        boolean inner = Math.abs(z - cz) <= 1 && y > floor && y < floor + 4;
                        put(level, chunkBox, y == floor ? plate : inner ? AIR : wall, nicheX, y, z);
                        put(level, chunkBox, wall, backX, y, z);
                        if (inner) {
                            put(level, chunkBox, AIR, wallX, y, z);
                        }
                    }
                }
            }
            decayWalls(level, chunkBox, boundingBox);
            // 侧龛里的灯：东边火、地，西边水、风
            put(level, chunkBox, ECAlloys.FIRE_CRYSTAL_LAMP.get().defaultBlockState(), cx + half + 1, floor + 1, cz - 1);
            put(level, chunkBox, ECAlloys.EARTH_CRYSTAL_LAMP.get().defaultBlockState(), cx + half + 1, floor + 1, cz + 1);
            put(level, chunkBox, ECAlloys.WATER_CRYSTAL_LAMP.get().defaultBlockState(), cx - half - 1, floor + 1, cz - 1);
            put(level, chunkBox, ECAlloys.AIR_CRYSTAL_LAMP.get().defaultBlockState(), cx - half - 1, floor + 1, cz + 1);
            // 封印门（南墙）和核心
            BlockState seal = ECSealedVault.VAULT_SEAL.get().defaultBlockState().setValue(VaultSealBlock.OPEN, false);
            for (BlockPos cell : VaultLayout.doorCells(core)) {
                put(level, chunkBox, seal, cell);
            }
            if (chunkBox.isInside(core)) {
                put(level, chunkBox, ECSealedVault.VAULT_CORE.get().defaultBlockState(), core);
                if (level.getBlockEntity(core) instanceof VaultCoreBlockEntity be) {
                    be.initPuzzle(VaultPuzzle.of(puzzle), puzzle);
                }
            }
        }
    }
}
