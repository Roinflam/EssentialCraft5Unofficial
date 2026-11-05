package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IMRUHandler;
import com.flameforge.essentialcraft5.enrichment.ControllerMRUView;
import com.flameforge.essentialcraft5.registry.ECEnrichment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 富集仓 MRU 输出器：别的机器把绑定宝石绑到它上面，就从整座仓里拉 MRU。对应原作 TileMRUCUECEjector。
 * 对外的 MRU 只出不进，平衡值报控制器的真实值（原作恒报 1.0，见 {@link ControllerMRUView}）；
 * 仓没成形、控制器拆了时读数为 0、取不出。
 */
public class EnrichmentEjectorBlockEntity extends EnrichmentPartBlockEntity {

    private final LazyOptional<IMRUHandler> mruCap = LazyOptional.of(() -> new ControllerMRUView(link, ControllerMRUView.Mode.OUTPUT));

    public EnrichmentEjectorBlockEntity(BlockPos pos, BlockState state) {
        super(ECEnrichment.EC_EJECTOR_BE.get(), pos, state);
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ECCapabilities.MRU) {
            return mruCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        mruCap.invalidate();
    }
}
