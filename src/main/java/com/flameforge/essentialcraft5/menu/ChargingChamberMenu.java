package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.blockentity.ChargingChamberBlockEntity;
import com.flameforge.essentialcraft5.registry.ECBlocks;
import com.flameforge.essentialcraft5.registry.ECMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;
import net.minecraftforge.items.SlotItemHandler;

/** 充能仓界面。槽位坐标照搬原作 ContainerChargingChamber：宝石 (108, 23)，充能物品 (140, 41)。 */
public class ChargingChamberMenu extends MachineMenu {

    public ChargingChamberMenu(int id, Inventory playerInventory, ChargingChamberBlockEntity be) {
        this(id, playerInventory, be.getBlockPos(), be.getInventory(), MachineData.server(be), be);
    }

    public ChargingChamberMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos(),
                clientHandler(2, ChargingChamberBlockEntity::slotRule), MachineData.client(0), null);
    }

    private ChargingChamberMenu(int id, Inventory playerInventory, BlockPos pos, IItemHandler handler, MachineData data,
                                @Nullable ChargingChamberBlockEntity be) {
        super(ECMenus.CHARGING_CHAMBER.get(), id, playerInventory, pos, data, ECBlocks.CHARGING_CHAMBER.get(), be);
        addMachineSlot(FilteredSlot.boundGem(handler, ChargingChamberBlockEntity.SLOT_GEM, 108, 23));
        addMachineSlot(new SlotItemHandler(handler, ChargingChamberBlockEntity.SLOT_ITEM, 140, 41));
        addPlayerInventory(playerInventory);
    }
}
