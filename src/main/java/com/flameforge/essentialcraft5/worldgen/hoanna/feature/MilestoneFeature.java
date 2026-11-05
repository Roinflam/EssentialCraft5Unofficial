package com.flameforge.essentialcraft5.worldgen.hoanna.feature;

import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * 里程碑（第十三批 13d-H1b，batch13.md §6.1 / §8.2「里程碑」，HS §2.3.2）：一根 1 × 1 × {@value #HEIGHT} 的柱——下面 3 格强化石、顶上 1 格
 * {@code ruin_milestone}（方块的外观和右键找城镇归 13d-H3，这里只按注册名摆）。
 * <p>
 * placed feature {@code hoanna_milestone}：{@code cell_spread} 24 / 12（每 576 区块一格，和中景观不是一套格子）→ in_square →
 * heightmap MOTION_BLOCKING → biome，挂六个陆地群系。高度图那一格就是柱脚：
 * <ul>
 *   <li>脚下要是实心地面（不是树叶、原木，也不是水——MOTION_BLOCKING 把水面算进去，落在水上时脚下是水）；</li>
 *   <li>柱子占的 4 格都要空着（草花可以清），顶上露天（MOTION_BLOCKING 不高于柱脚，也就是头顶没有树冠、悬崖）；</li>
 *   <li>这个区块有 {@code blocks_midscape} 结构的引用、或者正好是中景观挑中的区块，都不放。</li>
 * </ul>
 * 只写柱子那一列（在起点区块里）。
 */
public class MilestoneFeature extends Feature<NoneFeatureConfiguration> {

    public static final int HEIGHT = 4;

    public MilestoneFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        ChunkPos chunk = new ChunkPos(origin);
        if (HoannaMidscape.isMidscapeChunk(level.getSeed(), chunk.x, chunk.z) || HoannaMidscape.blockedByStructures(level, chunk, 0)) {
            return false;
        }
        return placeAt(level, origin);
    }

    /** foot 是柱脚那一格（地面上一格）；条件见类注释（结构引用、中景观区块除外） */
    public static boolean placeAt(WorldGenLevel level, BlockPos foot) {
        if (!canPlaceAt(level, foot)) {
            return false;
        }
        BoundingBox box = FeatureSupport.writableBox(level, foot);
        BlockState stone = ECMaterials.FORTIFIED_STONE.get().defaultBlockState();
        for (int dy = 0; dy < HEIGHT - 1; dy++) {
            FeatureSupport.set(level, box, foot.above(dy), stone);
        }
        FeatureSupport.set(level, box, foot.above(HEIGHT - 1), HoannaFeatureBlocks.ruinMilestone());
        return true;
    }

    /** 柱脚这里能不能立柱 */
    public static boolean canPlaceAt(WorldGenLevel level, BlockPos foot) {
        BlockState below = level.getBlockState(foot.below());
        if (!FeatureSupport.isGround(below) || below.is(BlockTags.LOGS) || below.is(BlockTags.LEAVES)) {
            return false;
        }
        for (int dy = 0; dy < HEIGHT; dy++) {
            BlockState state = level.getBlockState(foot.above(dy));
            if (!FeatureSupport.isClearable(state)) {
                return false;
            }
        }
        // 头顶露天：这一列挡路的最高一格不高于柱脚下面那格
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, foot.getX(), foot.getZ()) <= foot.getY();
    }
}
