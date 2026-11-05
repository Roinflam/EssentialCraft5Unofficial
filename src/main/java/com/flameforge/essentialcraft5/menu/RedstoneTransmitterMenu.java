package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.blockentity.RedstoneTransmitterBlockEntity;
import com.flameforge.essentialcraft5.registry.ECRedstoneTools;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 红石传递器界面。槽位坐标照搬原作 ContainerRedstoneTransmitter：绑定宝石 (29, 32)。
 * 额外数值（原作界面 GuiCommon 什么都不显示，这里给宝石右边的状态条用）：连接状态（{@link RedstoneTransmitterBlockEntity.Link} 的序号）、当前输出。
 */
public class RedstoneTransmitterMenu extends MachineMenu {

    public static final int EXTRA_LINK = 0;
    public static final int EXTRA_POWER = 1;
    private static final int EXTRA_COUNT = 2;

    public RedstoneTransmitterMenu(int id, Inventory playerInventory, RedstoneTransmitterBlockEntity be) {
        this(id, playerInventory, be.getBlockPos(), be.getInventory(),
                MachineData.server(be, () -> be.link().ordinal(), be::power), be);
    }

    public RedstoneTransmitterMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos(),
                clientHandler(RedstoneTransmitterBlockEntity.SLOTS, RedstoneTransmitterBlockEntity::slotRule),
                MachineData.client(EXTRA_COUNT), null);
    }

    private RedstoneTransmitterMenu(int id, Inventory playerInventory, BlockPos pos, IItemHandler handler, MachineData data,
                                    @Nullable RedstoneTransmitterBlockEntity be) {
        super(ECRedstoneTools.REDSTONE_TRANSMITTER_MENU.get(), id, playerInventory, pos, data, ECRedstoneTools.REDSTONE_TRANSMITTER.get(), be);
        addMachineSlot(FilteredSlot.boundGem(handler, RedstoneTransmitterBlockEntity.SLOT_GEM, 29, 32));
        addPlayerInventory(playerInventory);
    }

    public RedstoneTransmitterBlockEntity.Link link() {
        return RedstoneTransmitterBlockEntity.Link.byId(data.extra(EXTRA_LINK));
    }

    public int power() {
        return data.extra(EXTRA_POWER);
    }
}
