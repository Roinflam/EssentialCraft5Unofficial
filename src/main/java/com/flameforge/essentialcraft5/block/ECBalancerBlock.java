package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.enrichment.EnrichmentControllers;
import com.flameforge.essentialcraft5.enrichment.EnrichmentStabilizer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 富集仓稳定器（原作 BlockMRUCUECBalancer + TileMRUCUECBalancer）：放在富集仓外壳上（方块在 essentialcraft5:mrucuec_casing 标签里），
 * 控制器扫外壳时数到它（{@link EnrichmentStabilizer}），成形期间每 20 tick 给仓里的 MRU 聚集体续 40 tick 稳定期，期间聚集体不给玩家加矩阵损伤。
 * <p>
 * 原作的方块实体每 tick 把控制器找到的聚集体的 flag 设成 true（每 tick 一次 AABB 实体查询，policy §4.2 P2）；
 * 这里不要方块实体、不 tick，由控制器按自己的复查节奏统一做（框架 G10）。
 * 放下 / 拆掉时通知附近的控制器重扫外壳：玩家放、挖的由 EnrichmentControllers 的事件监听管，这里再管 /setblock、活塞之类不发事件的情况。
 */
public class ECBalancerBlock extends Block implements EnrichmentStabilizer {

    public ECBalancerBlock(Properties properties) {
        super(properties);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && !oldState.is(state.getBlock())) {
            EnrichmentControllers.notifyChanged(level, pos);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            EnrichmentControllers.notifyChanged(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
