package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.enrichment.EnrichmentLink;
import com.flameforge.essentialcraft5.enrichment.EnrichmentPart;
import com.flameforge.essentialcraft5.menu.EnrichmentStateCheckerMenu;
import com.flameforge.essentialcraft5.mru.MRUStorage;
import com.flameforge.essentialcraft5.registry.ECEnrichment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 富集仓状态检测器：右键打开界面，看整座仓的存量、平衡值和外壳的 MROV（各外壳方块 MRU 抗性之和）。
 * 对应原作 TileMRUCUECStateChecker + GuiMRUInfo（MRU 条 (7,4)、MROV (25,22)、平衡值 (25,40)、存量 (25,58)）。
 * <p>
 * 继承 MRUBlockEntity 只为用机器界面框架：自己的存储上限是 0、没有物品槽、不 tick（方块不给 ticker），
 * {@link #getMRUStorage()} 连着仓时返回控制器的存储，界面数据就是整座仓的。
 * 平衡值是真实值（原作包装类少写 return，恒显示 1.0）。原作不对外暴露 MRU，这里也不暴露（绑定宝石绑它是「不是魔法方块」）。
 */
public class EnrichmentStateCheckerBlockEntity extends MRUBlockEntity implements EnrichmentPart {

    private final EnrichmentLink link = new EnrichmentLink(this);

    public EnrichmentStateCheckerBlockEntity(BlockPos pos, BlockState state) {
        super(ECEnrichment.EC_STATE_CHECKER_BE.get(), pos, state, () -> 0, 0);
    }

    @Override
    public EnrichmentLink enrichmentLink() {
        return link;
    }

    public boolean isLinked() {
        return link.isLinked();
    }

    /** 连着的控制器（只认成形的仓）；没连上时为 null。界面数据里坍缩的几格从它取 */
    @Nullable
    public EnrichmentControllerBlockEntity controller() {
        return link.controller();
    }

    /** 外壳的 MROV；没连上时为 0 */
    public double resistance() {
        EnrichmentControllerBlockEntity controller = link.controller();
        return controller == null ? 0D : controller.resistance();
    }

    @Override
    public MRUStorage getMRUStorage() {
        EnrichmentControllerBlockEntity controller = link.controller();
        return controller != null ? controller.storage() : super.getMRUStorage();
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ECCapabilities.MRU) {
            return LazyOptional.empty();
        }
        return super.getCapability(cap, side);
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

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new EnrichmentStateCheckerMenu(id, inventory, this);
    }
}
