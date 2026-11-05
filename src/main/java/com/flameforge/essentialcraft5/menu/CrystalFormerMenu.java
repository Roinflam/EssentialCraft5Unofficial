package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.blockentity.CrystalFormerBlockEntity;
import com.flameforge.essentialcraft5.registry.ECCrystal;
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
 * 水晶成型器界面。槽位坐标和添加顺序照搬原作 ContainerCrystalFormer：
 * 宝石 (26, 5)；水桶 (108, 41)、(126, 41)、(144, 41)；玻璃 (108, 23)、(144, 23)；钻石 (126, 5)；产物 (82, 5)。
 * 原作产物格是普通槽（能放东西进去把机器堵死），这里改成只能取。
 * <p>
 * 额外数值（原作界面没有进度显示，给新加的进度条用）：进度、所需 tick、状态、每 tick 耗能。
 */
public class CrystalFormerMenu extends MachineMenu {

    public static final int EXTRA_PROGRESS = 0;
    public static final int EXTRA_REQUIRED = 2;
    public static final int EXTRA_STATUS = 4;
    public static final int EXTRA_COST = 5;
    public static final int EXTRA_COUNT = 7;

    public CrystalFormerMenu(int id, Inventory playerInventory, CrystalFormerBlockEntity be) {
        this(id, playerInventory, be.getBlockPos(), be.getInventory(), MachineData.server(be, extras(be)), be);
    }

    public CrystalFormerMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos(),
                clientHandler(CrystalFormerBlockEntity.SLOTS, CrystalFormerBlockEntity::slotRule),
                MachineData.client(EXTRA_COUNT), null);
    }

    private CrystalFormerMenu(int id, Inventory playerInventory, BlockPos pos, IItemHandler handler, MachineData data,
                              @Nullable CrystalFormerBlockEntity be) {
        super(ECCrystal.CRYSTAL_FORMER_MENU.get(), id, playerInventory, pos, data, ECCrystal.CRYSTAL_FORMER.get(), be);
        addMachineSlot(FilteredSlot.boundGem(handler, CrystalFormerBlockEntity.SLOT_GEM, 26, 5));
        addMachineSlot(new SlotItemHandler(handler, 2, 108, 41));
        addMachineSlot(new SlotItemHandler(handler, 3, 126, 41));
        addMachineSlot(new SlotItemHandler(handler, 4, 144, 41));
        addMachineSlot(new SlotItemHandler(handler, 5, 108, 23));
        addMachineSlot(new SlotItemHandler(handler, 6, 144, 23));
        addMachineSlot(new SlotItemHandler(handler, CrystalFormerBlockEntity.SLOT_DIAMOND, 126, 5));
        addMachineSlot(new OutputSlot(handler, CrystalFormerBlockEntity.SLOT_OUTPUT, 82, 5));
        addPlayerInventory(playerInventory);
    }

    /** 服务端的额外数值，顺序和上面的 EXTRA_* 一致（大数拆两格） */
    private static IntSupplier[] extras(CrystalFormerBlockEntity be) {
        List<IntSupplier> list = new ArrayList<>(EXTRA_COUNT);
        Collections.addAll(list, MachineData.wide(be::getProgress));
        Collections.addAll(list, MachineData.wide(CrystalFormerBlockEntity::required));
        list.add(() -> be.getStatus().ordinal());
        Collections.addAll(list, MachineData.wide(CrystalFormerBlockEntity::costPerTick));
        return list.toArray(IntSupplier[]::new);
    }

    // ---- 客户端读数 ----

    public int progress() {
        return data.extraWide(EXTRA_PROGRESS);
    }

    public int required() {
        return data.extraWide(EXTRA_REQUIRED);
    }

    public CrystalFormerBlockEntity.Status status() {
        return CrystalFormerBlockEntity.Status.byId(data.extra(EXTRA_STATUS));
    }

    public int costPerTick() {
        return data.extraWide(EXTRA_COST);
    }
}
