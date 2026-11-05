package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.registry.ECEnrichment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 富集仓存储方块：嵌在外壳上，每个给控制器加一份容量（默认 100000 MRU）。对应原作 TileMRUCUECHoldingChamber。
 * 自己不存 MRU、不 tick；拆掉时通知控制器立即扣容量（控制器里超出的 MRU 保留，只出不进）。
 */
public class EnrichmentHoldingChamberBlockEntity extends EnrichmentPartBlockEntity {

    public EnrichmentHoldingChamberBlockEntity(BlockPos pos, BlockState state) {
        super(ECEnrichment.EC_HOLDING_CHAMBER_BE.get(), pos, state);
    }

    @Override
    public boolean countsAsHoldingChamber() {
        return true;
    }
}
