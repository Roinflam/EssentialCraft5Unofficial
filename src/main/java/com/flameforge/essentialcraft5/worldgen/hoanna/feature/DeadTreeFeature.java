package com.flameforge.essentialcraft5.worldgen.hoanna.feature;

import com.flameforge.essentialcraft5.registry.ECHoannaNature;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * 枯树（第十三批 13d-H1b，HS §2.1.3 {@code dead_tree}，恐惧之地、混沌腐化之地 rarity 6）：一根 3~6 格的深色橡木原木（最上面一两格三成几率是去皮的），
 * 上半截伸出 0~2 根 1~2 格的横枝，<b>没有树叶</b>。写 ≈ 8 格。也给这两个没树的群系补了木头来源。
 * <p>
 * 只长在泥土类 / 恐惧泥土上；树干和横枝只占空的格子，碰到别的方块就截短。
 */
public class DeadTreeFeature extends Feature<NoneFeatureConfiguration> {

    public static final int MIN_HEIGHT = 3;
    public static final int MAX_HEIGHT = 6;
    public static final int GROUND_SEARCH = 6;

    public DeadTreeFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();
        BoundingBox box = FeatureSupport.writableBox(level, origin);
        int ground = FeatureSupport.groundY(level, origin.getX(), origin.getZ(), origin.getY(), origin.getY() - GROUND_SEARCH);
        if (ground == Integer.MIN_VALUE) {
            return false;
        }
        BlockPos base = new BlockPos(origin.getX(), ground, origin.getZ());
        BlockState soil = level.getBlockState(base);
        if (!soil.is(BlockTags.DIRT) && !soil.is(ECHoannaNature.DREAD_DIRT.get())) {
            return false;
        }
        int height = MIN_HEIGHT + random.nextInt(MAX_HEIGHT - MIN_HEIGHT + 1);
        int stripped = random.nextFloat() < 0.3F ? 1 + random.nextInt(2) : 0;
        int trunk = 0;
        for (int dy = 1; dy <= height; dy++) {
            BlockPos pos = base.above(dy);
            if (!FeatureSupport.isClearable(level.getBlockState(pos))) {
                break;
            }
            BlockState log = (dy > height - stripped ? Blocks.STRIPPED_DARK_OAK_LOG : Blocks.DARK_OAK_LOG).defaultBlockState();
            FeatureSupport.set(level, box, pos, log);
            trunk = dy;
        }
        if (trunk == 0) {
            return false;
        }
        int branches = trunk >= 4 ? random.nextInt(3) : 0;
        for (int i = 0; i < branches; i++) {
            Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            int y = trunk / 2 + 1 + random.nextInt(Math.max(1, trunk - trunk / 2 - 1));
            int length = 1 + random.nextInt(2);
            BlockState branch = Blocks.DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, direction.getAxis());
            for (int step = 1; step <= length; step++) {
                BlockPos pos = base.above(y).relative(direction, step);
                if (!FeatureSupport.isClearable(level.getBlockState(pos))) {
                    break;
                }
                FeatureSupport.set(level, box, pos, branch);
            }
        }
        return true;
    }
}
