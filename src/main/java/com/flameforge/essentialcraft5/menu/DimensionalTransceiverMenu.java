package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.blockentity.DimensionalTransceiverBlockEntity;
import com.flameforge.essentialcraft5.registry.ECVoidMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * MRU 跨维度收发器界面。槽位坐标照搬原作 ContainerMRUDimTransciever：唯一的绑定宝石格 (29, 32)。
 * 没有额外数值：宝石状态（跨维度也算好）随 {@link MachineData} 的基础几格同步。
 */
public class DimensionalTransceiverMenu extends MachineMenu {

    public DimensionalTransceiverMenu(int id, Inventory playerInventory, DimensionalTransceiverBlockEntity be) {
        this(id, playerInventory, be.getBlockPos(), be.getInventory(), MachineData.server(be), be);
    }

    public DimensionalTransceiverMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos(),
                clientHandler(DimensionalTransceiverBlockEntity.SLOTS, DimensionalTransceiverBlockEntity::slotRule),
                MachineData.client(0), null);
    }

    private DimensionalTransceiverMenu(int id, Inventory playerInventory, BlockPos pos, IItemHandler handler, MachineData data,
                                       @Nullable DimensionalTransceiverBlockEntity be) {
        super(ECVoidMachines.DIMENSIONAL_TRANSCEIVER_MENU.get(), id, playerInventory, pos, data, ECVoidMachines.DIMENSIONAL_TRANSCEIVER.get(), be);
        addMachineSlot(new SlotItemHandler(handler, 0, 29, 32));
        addPlayerInventory(playerInventory);
    }
}
