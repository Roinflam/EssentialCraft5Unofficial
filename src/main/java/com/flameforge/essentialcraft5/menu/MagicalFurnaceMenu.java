package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.blockentity.MagicalFurnaceBlockEntity;
import com.flameforge.essentialcraft5.registry.ECVoidMachines;
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
 * 魔力熔炉界面。槽位坐标照搬原作 ContainerMagicalFurnace：唯一的绑定宝石格 (108, 23)，没有输入输出格（东西扔在机器顶上）。
 * <p>
 * 额外数值：状态、进度与所需刻数（刻数配置上限 72000，超出 short，拆两格）、结构第一个不符的格子（北向偏移，有没有另占一格）。
 */
public class MagicalFurnaceMenu extends MachineMenu {

    public static final int EXTRA_STATUS = 0;
    public static final int EXTRA_PROGRESS = 1;
    public static final int EXTRA_TICKS = 3;
    public static final int EXTRA_HAS_MISMATCH = 5;
    public static final int EXTRA_MISMATCH_X = 6;
    public static final int EXTRA_MISMATCH_Y = 7;
    public static final int EXTRA_MISMATCH_Z = 8;
    public static final int EXTRA_COUNT = 9;

    public MagicalFurnaceMenu(int id, Inventory playerInventory, MagicalFurnaceBlockEntity be) {
        this(id, playerInventory, be.getBlockPos(), be.getInventory(), MachineData.server(be, extras(be)), be);
    }

    public MagicalFurnaceMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos(),
                clientHandler(MagicalFurnaceBlockEntity.SLOTS, MagicalFurnaceBlockEntity::slotRule),
                MachineData.client(EXTRA_COUNT), null);
    }

    private MagicalFurnaceMenu(int id, Inventory playerInventory, BlockPos pos, IItemHandler handler, MachineData data,
                               @Nullable MagicalFurnaceBlockEntity be) {
        super(ECVoidMachines.MAGICAL_FURNACE_MENU.get(), id, playerInventory, pos, data, ECVoidMachines.MAGICAL_FURNACE.get(), be);
        addMachineSlot(new SlotItemHandler(handler, 0, 108, 23));
        addPlayerInventory(playerInventory);
    }

    /** 服务端的额外数值，顺序和上面的 EXTRA_* 一致 */
    private static IntSupplier[] extras(MagicalFurnaceBlockEntity be) {
        List<IntSupplier> list = new ArrayList<>(EXTRA_COUNT);
        list.add(() -> be.status().ordinal());
        Collections.addAll(list, MachineData.wide(be::progress));
        Collections.addAll(list, MachineData.wide(be::ticksRequired));
        list.add(() -> be.mismatchOffset() == null ? 0 : 1);
        list.add(() -> be.mismatchOffset() == null ? 0 : be.mismatchOffset().getX());
        list.add(() -> be.mismatchOffset() == null ? 0 : be.mismatchOffset().getY());
        list.add(() -> be.mismatchOffset() == null ? 0 : be.mismatchOffset().getZ());
        return list.toArray(IntSupplier[]::new);
    }

    // ---- 客户端读数 ----

    public MagicalFurnaceBlockEntity.Status status() {
        return MagicalFurnaceBlockEntity.Status.byId(data.extra(EXTRA_STATUS));
    }

    public int progress() {
        return data.extraWide(EXTRA_PROGRESS);
    }

    public int ticksRequired() {
        return data.extraWide(EXTRA_TICKS);
    }

    /** 结构第一个不符的格子（北向偏移，就是相对机器的世界偏移）；没有时为 null */
    @Nullable
    public BlockPos mismatchOffset() {
        if (data.extra(EXTRA_HAS_MISMATCH) == 0) {
            return null;
        }
        return new BlockPos(data.extra(EXTRA_MISMATCH_X), data.extra(EXTRA_MISMATCH_Y), data.extra(EXTRA_MISMATCH_Z));
    }
}
