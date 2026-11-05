package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.blockentity.MagicalRepairerBlockEntity;
import com.flameforge.essentialcraft5.registry.ECDevices;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 魔力修复器界面。槽位坐标照搬原作 ContainerMagicalRepairer：宝石 (108, 23)，要修的东西 (140, 41)。
 * 额外数值：状态码（{@link MagicalRepairerBlockEntity} 的 STATUS_*）。
 */
public class MagicalRepairerMenu extends MachineMenu {

    public static final int EXTRA_STATUS = 0;
    private static final int EXTRA_COUNT = 1;

    public MagicalRepairerMenu(int id, Inventory playerInventory, MagicalRepairerBlockEntity be) {
        this(id, playerInventory, be.getBlockPos(), be.getInventory(), MachineData.server(be, be::statusCode), be);
    }

    public MagicalRepairerMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos(),
                clientHandler(MagicalRepairerBlockEntity.SLOTS, MagicalRepairerBlockEntity::slotRule),
                MachineData.client(EXTRA_COUNT), null);
    }

    private MagicalRepairerMenu(int id, Inventory playerInventory, BlockPos pos, IItemHandler handler, MachineData data,
                                @Nullable MagicalRepairerBlockEntity be) {
        super(ECDevices.MAGICAL_REPAIRER_MENU.get(), id, playerInventory, pos, data, ECDevices.MAGICAL_REPAIRER.get(), be);
        addMachineSlot(FilteredSlot.boundGem(handler, MagicalRepairerBlockEntity.SLOT_GEM, 108, 23));
        addMachineSlot(new SlotItemHandler(handler, MagicalRepairerBlockEntity.SLOT_ITEM, 140, 41));
        addPlayerInventory(playerInventory);
    }

    public int status() {
        return data.extra(EXTRA_STATUS);
    }
}
