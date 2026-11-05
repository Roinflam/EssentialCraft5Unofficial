package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.api.ItemFilters;
import com.flameforge.essentialcraft5.blockentity.MagicalHopperBlockEntity;
import com.flameforge.essentialcraft5.registry.ECLogistics;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 魔力漏斗界面：只有过滤器格 (80, 32)（原作 {@code ContainerMagicalHopper} L15，界面是通用的 GuiCommon）+ 玩家背包。
 * 额外数值：主人状态（{@code ECProtection.OwnerState} 的序号 + 1，0 = 还没同步到）、红石停工（1 = 有信号）。
 */
public class MagicalHopperMenu extends MachineMenu {

    public static final int EXTRA_OWNER = 0;
    public static final int EXTRA_REDSTONE = 1;
    private static final int EXTRA_COUNT = 2;
    public static final int FILTER_X = 80;
    public static final int FILTER_Y = 32;

    public MagicalHopperMenu(int id, Inventory playerInventory, MagicalHopperBlockEntity be) {
        this(id, playerInventory, be.getBlockPos(), be.getInventory(),
                MachineData.server(be, be::ownerStateCode, be::redstoneCode), be);
    }

    public MagicalHopperMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos(),
                clientHandler(MagicalHopperBlockEntity.SLOTS, MagicalHopperBlockEntity::slotRule),
                MachineData.client(EXTRA_COUNT), null);
    }

    private MagicalHopperMenu(int id, Inventory playerInventory, BlockPos pos, IItemHandler handler, MachineData data,
                              @Nullable MagicalHopperBlockEntity be) {
        super(ECLogistics.MAGICAL_HOPPER_MENU.get(), id, playerInventory, pos, data, ECLogistics.MAGICAL_HOPPER.get(), be);
        addMachineSlot(new FilteredSlot(handler, MagicalHopperBlockEntity.SLOT_FILTER, FILTER_X, FILTER_Y, ItemFilters::isFilter));
        addPlayerInventory(playerInventory);
    }

    /** 主人状态；还没同步到（或数值不认识）时为 null */
    @Nullable
    public ECProtection.OwnerState ownerState() {
        int id = data.extra(EXTRA_OWNER) - 1;
        ECProtection.OwnerState[] states = ECProtection.OwnerState.values();
        return id >= 0 && id < states.length ? states[id] : null;
    }

    /** 周围有红石信号（停工中） */
    public boolean redstonePaused() {
        return data.extra(EXTRA_REDSTONE) == 1;
    }
}
