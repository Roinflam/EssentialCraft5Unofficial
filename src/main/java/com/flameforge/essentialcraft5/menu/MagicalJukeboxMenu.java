package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.blockentity.MagicalJukeboxBlockEntity;
import com.flameforge.essentialcraft5.registry.ECDevices;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 魔力唱片机界面。槽位坐标照搬原作 ContainerMagicalJukebox：宝石 (108, 23)，唱片 (26, 5)。
 * 额外数值：是否正在放（0 / 1；原作界面没有，这里给唱片槽旁的状态条用）。
 */
public class MagicalJukeboxMenu extends MachineMenu {

    public static final int EXTRA_PLAYING = 0;
    private static final int EXTRA_COUNT = 1;

    public MagicalJukeboxMenu(int id, Inventory playerInventory, MagicalJukeboxBlockEntity be) {
        this(id, playerInventory, be.getBlockPos(), be.getInventory(),
                MachineData.server(be, () -> be.isPlaying() ? 1 : 0), be);
    }

    public MagicalJukeboxMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos(),
                clientHandler(MagicalJukeboxBlockEntity.SLOTS, MagicalJukeboxBlockEntity::slotRule),
                MachineData.client(EXTRA_COUNT), null);
    }

    private MagicalJukeboxMenu(int id, Inventory playerInventory, BlockPos pos, IItemHandler handler, MachineData data,
                               @Nullable MagicalJukeboxBlockEntity be) {
        super(ECDevices.MAGICAL_JUKEBOX_MENU.get(), id, playerInventory, pos, data, ECDevices.MAGICAL_JUKEBOX.get(), be);
        addMachineSlot(FilteredSlot.boundGem(handler, MagicalJukeboxBlockEntity.SLOT_GEM, 108, 23));
        addMachineSlot(new SlotItemHandler(handler, MagicalJukeboxBlockEntity.SLOT_RECORD, 26, 5));
        addPlayerInventory(playerInventory);
    }

    public boolean playing() {
        return data.extra(EXTRA_PLAYING) != 0;
    }
}
