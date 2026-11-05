package com.flameforge.essentialcraft5.worldgen.hoanna.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * 霜髓冰刺（第十三批 13d-H1b，HS §2.1.1 / §2.1.3 {@code frost_marrow_spikes}，冰冻腐化之地 rarity 14）：一丛 3~6 根浮冰刺，
 * 主刺 6~{@value #MAX_HEIGHT} 格、其余 3~6 格，6 格以上的刺下面三分之一加粗成十字；刺里嵌 2~5 块霜髓（不放在刺尖）。写 ≈ 40 格。
 * <p>
 * 刺只长在 {@link FeatureSupport#isClearable} 的格子里（不盖掉别的方块），刺根要站在地面上；同一丛的刺都在起点 3 格内，写的范围 ≤ 4 格。
 * 冰封营地（中景观）也用 {@link #placeSpike} 摆一圈刺。
 */
public class MarrowSpikeFeature extends Feature<NoneFeatureConfiguration> {

    public static final int MAX_HEIGHT = 9;
    /** 从高度图那一格往下找地面的格数 */
    public static final int GROUND_SEARCH = 6;

    public MarrowSpikeFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();
        BoundingBox box = FeatureSupport.writableBox(level, origin);
        int spikes = 3 + random.nextInt(4);
        // 先决定每根刺的位置和高度，再按总格数撒霜髓
        List<BlockPos> cells = new ArrayList<>();
        boolean any = false;
        for (int i = 0; i < spikes; i++) {
            int x = origin.getX() + (i == 0 ? 0 : random.nextInt(7) - 3);
            int z = origin.getZ() + (i == 0 ? 0 : random.nextInt(7) - 3);
            int height = i == 0 ? 6 + random.nextInt(MAX_HEIGHT - 5) : 3 + random.nextInt(4);
            int ground = FeatureSupport.groundY(level, x, z, origin.getY() + 2, origin.getY() - GROUND_SEARCH, MarrowSpikeFeature::isSpikeBlock);
            if (ground == Integer.MIN_VALUE) {
                continue;
            }
            any |= placeSpike(level, box, new BlockPos(x, ground + 1, z), height, cells);
        }
        int marrow = Math.min(cells.size(), 2 + random.nextInt(4));
        for (int i = 0; i < marrow; i++) {
            FeatureSupport.set(level, box, cells.remove(random.nextInt(cells.size())), HoannaFeatureBlocks.frostMarrow());
        }
        return any;
    }

    /** 冰刺用的方块（浮冰、霜髓） */
    public static boolean isSpikeBlock(BlockState state) {
        return state.is(Blocks.PACKED_ICE) || state.is(HoannaFeatureBlocks.frostMarrow().getBlock());
    }

    /**
     * 从 base（地面上一格）往上放一根 height 格的浮冰刺；6 格以上的刺下面三分之一四周各加一格。只占能占的格子、碰到别的方块就停。
     *
     * @param marrowCells 能嵌霜髓的格子（刺身，不含刺尖）收集到这里；不要就传 null
     * @return 放了至少一格
     */
    public static boolean placeSpike(WorldGenLevel level, BoundingBox box, BlockPos base, int height, List<BlockPos> marrowCells) {
        BlockState ice = Blocks.PACKED_ICE.defaultBlockState();
        boolean placed = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dy = 0; dy < height; dy++) {
            pos.set(base.getX(), base.getY() + dy, base.getZ());
            BlockState current = level.getBlockState(pos);
            if (isSpikeBlock(current)) {
                // 已经立着（分块放时别的区块先放了这一截）：照样算放过，后面的加粗照常做
                placed = true;
                continue;
            }
            if (!FeatureSupport.isClearable(current)) {
                break;
            }
            if (FeatureSupport.set(level, box, pos.immutable(), ice)) {
                placed = true;
                if (marrowCells != null && dy < height - 1) {
                    marrowCells.add(pos.immutable());
                }
            }
        }
        if (height >= 6 && placed) {
            int thick = height / 3;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                for (int dy = 0; dy < thick; dy++) {
                    pos.set(base.getX() + direction.getStepX(), base.getY() + dy, base.getZ() + direction.getStepZ());
                    if (dy == 0 && !FeatureSupport.isGround(level.getBlockState(pos.below()))) {
                        break;
                    }
                    BlockState current = level.getBlockState(pos);
                    if (isSpikeBlock(current)) {
                        continue;
                    }
                    if (!FeatureSupport.isClearable(current)) {
                        break;
                    }
                    FeatureSupport.set(level, box, pos.immutable(), ice);
                }
            }
        }
        return placed;
    }
}
