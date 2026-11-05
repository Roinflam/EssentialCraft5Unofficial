package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.enrichment.EnrichmentLink;
import com.flameforge.essentialcraft5.enrichment.EnrichmentPart;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 没有界面的富集仓部件（存储方块、输出器、红石控制器）的基类：只记着自己属于哪个控制器。
 * 对应原作 TileMRUCUECHoldingChamber 等实现 IStructurePiece 的 Tile。有界面的接收器、状态检测器继承 MRUBlockEntity，另见各自的类。
 */
public abstract class EnrichmentPartBlockEntity extends BlockEntity implements EnrichmentPart {

    protected final EnrichmentLink link = new EnrichmentLink(this);

    protected EnrichmentPartBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public EnrichmentLink enrichmentLink() {
        return link;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        link.save(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        link.load(tag);
    }
}
