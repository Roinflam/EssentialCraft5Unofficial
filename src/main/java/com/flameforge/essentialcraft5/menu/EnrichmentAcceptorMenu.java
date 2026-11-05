package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.blockentity.EnrichmentAcceptorBlockEntity;
import com.flameforge.essentialcraft5.registry.ECEnrichment;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 富集仓 MRU 接收器界面。槽位坐标照搬原作 ContainerMRUAcceptor：绑定宝石 (80, 32)。
 * 额外数值 1 格：是否连着一座已成形的富集仓（0/1）。
 */
public class EnrichmentAcceptorMenu extends MachineMenu {

    public EnrichmentAcceptorMenu(int id, Inventory playerInventory, EnrichmentAcceptorBlockEntity be) {
        this(id, playerInventory, be.getBlockPos(), be.getInventory(),
                MachineData.server(be, () -> be.isLinked() ? 1 : 0), be);
    }

    public EnrichmentAcceptorMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos(), clientHandler(1, EnrichmentAcceptorBlockEntity::slotRule),
                MachineData.client(1), null);
    }

    private EnrichmentAcceptorMenu(int id, Inventory playerInventory, BlockPos pos, IItemHandler handler, MachineData data,
                                   @Nullable EnrichmentAcceptorBlockEntity be) {
        super(ECEnrichment.EC_ACCEPTOR_MENU.get(), id, playerInventory, pos, data, ECEnrichment.EC_ACCEPTOR.get(), be);
        addMachineSlot(FilteredSlot.boundGem(handler, EnrichmentAcceptorBlockEntity.SLOT_GEM, 80, 32));
        addPlayerInventory(playerInventory);
    }

    /** 是否连着一座已成形的富集仓 */
    public boolean linked() {
        return data.extra(0) != 0;
    }
}
