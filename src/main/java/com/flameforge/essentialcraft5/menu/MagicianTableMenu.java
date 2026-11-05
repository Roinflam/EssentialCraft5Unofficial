package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.blockentity.MagicianTableBlockEntity;
import com.flameforge.essentialcraft5.registry.ECMagicianTable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntSupplier;

/**
 * 魔力工作台界面。槽位坐标照搬原作 ContainerMagicianTable：
 * 宝石 (153, 5)；中心 (44, 23)、左上 (26, 5)、右上 (62, 5)、左下 (26, 41)、右下 (62, 41)；输出 (89, 5)。
 * 原作输出槽是 SlotGeneric（能往里放东西），这里改成只能取。
 * <p>
 * 额外数值：进度（拆两格）、所需进度（拆两格）、速度系数 ×100。
 */
public class MagicianTableMenu extends MachineMenu {

    public static final int EXTRA_PROGRESS = 0;
    public static final int EXTRA_REQUIRED = 2;
    public static final int EXTRA_SPEED = 4;
    private static final int EXTRA_COUNT = 5;

    public MagicianTableMenu(int id, Inventory playerInventory, MagicianTableBlockEntity be) {
        this(id, playerInventory, be.getBlockPos(), be.getInventory(), serverData(be), be);
    }

    public MagicianTableMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos(),
                clientHandler(MagicianTableBlockEntity.SLOTS, MagicianTableBlockEntity::slotRule),
                MachineData.client(EXTRA_COUNT), null);
    }

    private MagicianTableMenu(int id, Inventory playerInventory, BlockPos pos, IItemHandler handler, MachineData data,
                              @Nullable MagicianTableBlockEntity be) {
        super(ECMagicianTable.MAGICIAN_TABLE_MENU.get(), id, playerInventory, pos, data, ECMagicianTable.MAGICIAN_TABLE.get(), be);
        addMachineSlot(FilteredSlot.boundGem(handler, MagicianTableBlockEntity.SLOT_GEM, 153, 5));
        addMachineSlot(new SlotItemHandler(handler, MagicianTableBlockEntity.SLOT_CENTER, 44, 23));
        addMachineSlot(new SlotItemHandler(handler, MagicianTableBlockEntity.SLOT_TOP_LEFT, 26, 5));
        addMachineSlot(new SlotItemHandler(handler, MagicianTableBlockEntity.SLOT_TOP_RIGHT, 62, 5));
        addMachineSlot(new SlotItemHandler(handler, MagicianTableBlockEntity.SLOT_BOTTOM_LEFT, 26, 41));
        addMachineSlot(new SlotItemHandler(handler, MagicianTableBlockEntity.SLOT_BOTTOM_RIGHT, 62, 41));
        addMachineSlot(new OutputSlot(handler, MagicianTableBlockEntity.SLOT_OUTPUT, 89, 5));
        addPlayerInventory(playerInventory);
    }

    private static MachineData serverData(MagicianTableBlockEntity be) {
        IntSupplier[] progress = MachineData.wide(() -> (int) be.getProgress());
        IntSupplier[] required = MachineData.wide(be::requiredProgress);
        return MachineData.server(be, progress[0], progress[1], required[0], required[1], be::speedPercent);
    }

    /** 当前进度（向下取整） */
    public int progress() {
        return data.extraWide(EXTRA_PROGRESS);
    }

    /** 正在做的配方要多少进度；没在做为 0 */
    public int required() {
        return data.extraWide(EXTRA_REQUIRED);
    }

    /** 速度系数 ×100（没装升级为 100） */
    public int speedPercent() {
        return data.extra(EXTRA_SPEED);
    }
}
