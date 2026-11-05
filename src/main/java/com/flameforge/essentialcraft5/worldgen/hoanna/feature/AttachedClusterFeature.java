package com.flameforge.essentialcraft5.worldgen.hoanna.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.List;

/**
 * 贴着方块长的晶簇（第十三批 13d-H1b，HS §2.1.3 {@code radiant_cluster_hotspot} / {@code umbral_cluster_patch}）：放一格 {@code to_place}，
 * 朝向按附着面定——脚下是实心的面就朝上（贴地），否则挑一面挨着实心墙的朝外（贴洞壁 / 坡面，四个方向随机先后），再不行挨着实心顶就朝下；
 * 都没有就不放。这一格有水就带水（含水）。只占空气或水源，不盖别的方块。
 * <p>
 * 本身只放一格，外面套原版 {@code minecraft:random_patch} 撒成一簇（次数、散布半径在 JSON 里）。方块没有 {@code facing} 属性（没注册、退回空气）时什么都不放。
 */
public class AttachedClusterFeature extends Feature<SimpleBlockConfiguration> {

    public AttachedClusterFeature(Codec<SimpleBlockConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<SimpleBlockConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos pos = context.origin();
        BlockState state = context.config().toPlace().getState(context.random(), pos);
        return placeAttached(level, pos, state, context.random());
    }

    /** 按附着面放 state（state 要带 facing）；放了返回 true */
    public static boolean placeAttached(WorldGenLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (!state.hasProperty(BlockStateProperties.FACING)) {
            return false;
        }
        BlockState current = level.getBlockState(pos);
        boolean water = current.is(Blocks.WATER) && current.getFluidState().is(Fluids.WATER) && current.getFluidState().isSource();
        if (!current.isAir() && !water) {
            return false;
        }
        Direction facing = attachFacing(level, pos, random);
        if (facing == null) {
            return false;
        }
        BlockState placed = state.setValue(BlockStateProperties.FACING, facing);
        placed = HoannaFeatureBlocks.with(placed, BlockStateProperties.WATERLOGGED, water);
        if (!placed.canSurvive(level, pos)) {
            return false;
        }
        return level.setBlock(pos, placed, FeatureSupport.FLAGS);
    }

    /** 朝向：先贴地（朝上），再贴墙（朝外，四个方向随机先后），最后贴顶（朝下）；都没有返回 null */
    public static Direction attachFacing(WorldGenLevel level, BlockPos pos, RandomSource random) {
        if (sturdy(level, pos, Direction.DOWN)) {
            return Direction.UP;
        }
        List<Direction> sides = new ArrayList<>(Direction.Plane.HORIZONTAL.stream().toList());
        for (int i = sides.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Direction swap = sides.get(i);
            sides.set(i, sides.get(j));
            sides.set(j, swap);
        }
        for (Direction side : sides) {
            if (sturdy(level, pos, side)) {
                return side.getOpposite();
            }
        }
        return sturdy(level, pos, Direction.UP) ? Direction.DOWN : null;
    }

    /** pos 朝 direction 那一格朝向 pos 的面是实心的 */
    private static boolean sturdy(WorldGenLevel level, BlockPos pos, Direction direction) {
        BlockPos neighbour = pos.relative(direction);
        return level.getBlockState(neighbour).isFaceSturdy(level, neighbour, direction.getOpposite());
    }
}
