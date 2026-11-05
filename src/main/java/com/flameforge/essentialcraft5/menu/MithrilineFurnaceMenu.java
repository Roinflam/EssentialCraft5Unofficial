package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.blockentity.MithrilineFurnaceBlockEntity;
import com.flameforge.essentialcraft5.registry.ECMithriline;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * 米斯莱熔炉界面。槽位坐标照搬原作 ContainerMithrilineFurnace：原料 (80, 64)，产物 (80, 21)（只能取）。
 * <p>
 * 额外数值：进度、所需 ESPE（×100 定点）、状态、结构第一个不符的格子（北向偏移，有没有另占一格）、ESPE 源个数、原料要几个；
 * 之后是框架追加的 ESPE 存量与上限（{@link MachineData#serverWithESPE}）。
 */
public class MithrilineFurnaceMenu extends MachineMenu {

    public static final int EXTRA_PROGRESS = 0;
    public static final int EXTRA_REQUIRED = 2;
    public static final int EXTRA_STATUS = 4;
    public static final int EXTRA_HAS_MISMATCH = 5;
    public static final int EXTRA_MISMATCH_X = 6;
    public static final int EXTRA_MISMATCH_Y = 7;
    public static final int EXTRA_MISMATCH_Z = 8;
    public static final int EXTRA_SOURCES = 9;
    public static final int EXTRA_COUNT_NEEDED = 10;
    public static final int EXTRA_COUNT = 11;

    public MithrilineFurnaceMenu(int id, Inventory playerInventory, MithrilineFurnaceBlockEntity be) {
        this(id, playerInventory, be.getBlockPos(), be.getInventory(), MachineData.serverWithESPE(be, extras(be)), be);
    }

    public MithrilineFurnaceMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos(),
                clientHandler(MithrilineFurnaceBlockEntity.SLOTS, MithrilineFurnaceBlockEntity::slotRule),
                MachineData.clientWithESPE(EXTRA_COUNT), null);
    }

    private MithrilineFurnaceMenu(int id, Inventory playerInventory, BlockPos pos, IItemHandler handler, MachineData data,
                                  @Nullable MithrilineFurnaceBlockEntity be) {
        super(ECMithriline.MITHRILINE_FURNACE_MENU.get(), id, playerInventory, pos, data, ECMithriline.MITHRILINE_FURNACE.get(), be);
        addMachineSlot(new SlotItemHandler(handler, MithrilineFurnaceBlockEntity.SLOT_INPUT, 80, 64));
        addMachineSlot(new OutputSlot(handler, MithrilineFurnaceBlockEntity.SLOT_OUTPUT, 80, 21));
        addPlayerInventory(playerInventory);
    }

    /** 服务端的额外数值，顺序和上面的 EXTRA_* 一致 */
    private static IntSupplier[] extras(MithrilineFurnaceBlockEntity be) {
        List<IntSupplier> list = new ArrayList<>(EXTRA_COUNT);
        Collections.addAll(list, MachineData.wideFixed(be::getDisplayProgress));
        Collections.addAll(list, MachineData.wideFixed(be::getDisplayRequired));
        list.add(() -> be.getStatus().ordinal());
        list.add(() -> be.getMismatchOffset() == null ? 0 : 1);
        list.add(() -> be.getMismatchOffset() == null ? 0 : be.getMismatchOffset().getX());
        list.add(() -> be.getMismatchOffset() == null ? 0 : be.getMismatchOffset().getY());
        list.add(() -> be.getMismatchOffset() == null ? 0 : be.getMismatchOffset().getZ());
        list.add(be::getSourceCount);
        list.add(be::getDisplayCount);
        return list.toArray(IntSupplier[]::new);
    }

    // ---- 客户端读数 ----

    /** 已投入的 ESPE（精度 0.01） */
    public double progress() {
        return data.extraFixed(EXTRA_PROGRESS);
    }

    /** 当前配方要多少 ESPE；0 表示没有配方 */
    public double required() {
        return data.extraFixed(EXTRA_REQUIRED);
    }

    public MithrilineFurnaceBlockEntity.Status status() {
        return MithrilineFurnaceBlockEntity.Status.byId(data.extra(EXTRA_STATUS));
    }

    /** 结构第一个不符的格子（北向偏移，熔炉四面对称、按北向判定，所以就是相对熔炉的世界偏移）；没有时为 null */
    @Nullable
    public BlockPos mismatchOffset() {
        if (data.extra(EXTRA_HAS_MISMATCH) == 0) {
            return null;
        }
        return new BlockPos(data.extra(EXTRA_MISMATCH_X), data.extra(EXTRA_MISMATCH_Y), data.extra(EXTRA_MISMATCH_Z));
    }

    public int sources() {
        return data.extra(EXTRA_SOURCES);
    }

    /** 当前配方要几个原料；0 表示没有配方 */
    public int countNeeded() {
        return data.extra(EXTRA_COUNT_NEEDED);
    }
}
