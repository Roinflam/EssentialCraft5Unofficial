package com.flameforge.essentialcraft5.worldgen.hoanna.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/**
 * 榍石露头（第十三批 13d-H1b，HS §2.1.3 {@code titanite_outcrop}，海纳尔沙漠 rarity 12）：一块半埋在沙里的砂岩 / 平滑砂岩（2 : 1）巨石，
 * 水平半径 2~3、竖直半径约 0.7 倍、球心在地面下一格；露在外面（上方或四周挨着空气）的石面里挑 4~8 块换成榍石矿。写 ≈ 60 格。
 * <p>
 * 只换掉空气、可替换的草花、沙、砂岩这几种（地面那一层的沙被石头顶掉），不碰别的方块；起点在水里不放。
 */
public class OutcropFeature extends Feature<NoneFeatureConfiguration> {

    public static final int MIN_ORES = 4;
    public static final int MAX_ORES = 8;
    public static final int GROUND_SEARCH = 6;

    public OutcropFeature(Codec<NoneFeatureConfiguration> codec) {
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
        float radius = 2F + random.nextFloat();
        float vertical = radius * 0.7F;
        int reach = (int) Math.ceil(radius);
        int cx = origin.getX();
        int cy = ground - 1;
        int cz = origin.getZ();
        List<BlockPos> stone = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                for (int dy = -reach; dy <= reach; dy++) {
                    float d = (dx * dx + dz * dz) / (radius * radius) + (dy * dy) / (vertical * vertical);
                    if (d > 1F + random.nextFloat() * 0.25F) {
                        continue;
                    }
                    pos.set(cx + dx, cy + dy, cz + dz);
                    BlockState current = level.getBlockState(pos);
                    if (!replaceable(current)) {
                        continue;
                    }
                    BlockState rock = (random.nextInt(3) < 2 ? Blocks.SANDSTONE : Blocks.SMOOTH_SANDSTONE).defaultBlockState();
                    if (FeatureSupport.set(level, box, pos.immutable(), rock)) {
                        stone.add(pos.immutable());
                    }
                }
            }
        }
        if (stone.isEmpty()) {
            return false;
        }
        List<BlockPos> exposed = new ArrayList<>();
        for (BlockPos cell : stone) {
            if (isExposed(level, cell)) {
                exposed.add(cell);
            }
        }
        int ores = Math.min(exposed.size(), MIN_ORES + random.nextInt(MAX_ORES - MIN_ORES + 1));
        for (int i = 0; i < ores; i++) {
            FeatureSupport.set(level, box, exposed.remove(random.nextInt(exposed.size())), HoannaFeatureBlocks.titaniteOre());
        }
        return true;
    }

    /** 巨石能顶掉的：空气、草花、沙、砂岩类 */
    private static boolean replaceable(BlockState state) {
        return FeatureSupport.isClearable(state) || state.is(BlockTags.SAND) || state.is(Blocks.SANDSTONE) || state.is(Blocks.SMOOTH_SANDSTONE);
    }

    /** 上面或四周挨着空气 / 草花（露在外面） */
    private static boolean isExposed(WorldGenLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (direction == Direction.DOWN) {
                continue;
            }
            BlockState neighbour = level.getBlockState(pos.relative(direction));
            if (neighbour.isAir() || (neighbour.canBeReplaced() && neighbour.getFluidState().isEmpty())) {
                return true;
            }
        }
        return false;
    }
}
