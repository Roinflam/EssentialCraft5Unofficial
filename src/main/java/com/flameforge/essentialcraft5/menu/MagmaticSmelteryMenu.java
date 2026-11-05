package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.blockentity.MagmaticSmelteryBlockEntity;
import com.flameforge.essentialcraft5.blockentity.OreProcessing;
import com.flameforge.essentialcraft5.registry.ECMagmatic;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntSupplier;

/**
 * 岩浆冶炼炉界面。槽位坐标照搬原作 ContainerMagmaticSmeltery：绑定宝石 (108, 23)，岩浆桶入 (44, 23)、空桶出 (62, 23)，
 * 矿石 (126, 5)、合金出 (126, 23)，合金入 (155, 5)、成品出 (155, 23)，魔力炉渣 (140, 41)。
 * 原作几个输出格都是普通槽（放进东西会把机器堵死），这里改成只能取。
 * <p>
 * 额外数值（大数都拆两格）：两道工序各自的进度、所需 tick、状态、每 tick 耗能，罐里的岩浆量和容量。
 */
public class MagmaticSmelteryMenu extends MachineMenu {

    public static final int EXTRA_ORE_PROGRESS = 0;
    public static final int EXTRA_ORE_REQUIRED = 2;
    public static final int EXTRA_ORE_STATUS = 4;
    public static final int EXTRA_ORE_COST = 5;
    public static final int EXTRA_ALLOY_PROGRESS = 7;
    public static final int EXTRA_ALLOY_REQUIRED = 9;
    public static final int EXTRA_ALLOY_STATUS = 11;
    public static final int EXTRA_ALLOY_COST = 12;
    public static final int EXTRA_LAVA = 14;
    public static final int EXTRA_CAPACITY = 16;
    public static final int EXTRA_COUNT = 18;

    public MagmaticSmelteryMenu(int id, Inventory playerInventory, MagmaticSmelteryBlockEntity be) {
        this(id, playerInventory, be.getBlockPos(), be.getInventory(), MachineData.server(be, HeatGeneratorMenu.concat(
                MachineData.wide(be::getOreProgress),
                MachineData.wide(MagmaticSmelteryBlockEntity::oreTicks),
                new IntSupplier[] {() -> be.getOreStatus().ordinal()},
                MachineData.wide(MagmaticSmelteryBlockEntity::oreCost),
                MachineData.wide(be::getAlloyProgress),
                MachineData.wide(MagmaticSmelteryBlockEntity::alloyTicks),
                new IntSupplier[] {() -> be.getAlloyStatus().ordinal()},
                MachineData.wide(MagmaticSmelteryBlockEntity::alloyCost),
                MachineData.wide(be::getLavaAmount),
                MachineData.wide(() -> MagmaticSmelteryBlockEntity.TANK_CAPACITY))), be);
    }

    public MagmaticSmelteryMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos(),
                clientHandler(MagmaticSmelteryBlockEntity.SLOTS, MagmaticSmelteryBlockEntity::slotRule),
                MachineData.client(EXTRA_COUNT), null);
    }

    private MagmaticSmelteryMenu(int id, Inventory playerInventory, BlockPos pos, IItemHandler handler, MachineData data,
                                 @Nullable MagmaticSmelteryBlockEntity be) {
        super(ECMagmatic.MAGMATIC_SMELTERY_MENU.get(), id, playerInventory, pos, data, ECMagmatic.MAGMATIC_SMELTERY.get(), be);
        addMachineSlot(FilteredSlot.boundGem(handler, MagmaticSmelteryBlockEntity.SLOT_GEM, 108, 23));
        addMachineSlot(new SlotItemHandler(handler, MagmaticSmelteryBlockEntity.SLOT_LAVA_IN, 44, 23));
        addMachineSlot(new OutputSlot(handler, MagmaticSmelteryBlockEntity.SLOT_LAVA_OUT, 62, 23));
        addMachineSlot(new SlotItemHandler(handler, MagmaticSmelteryBlockEntity.SLOT_ORE, 126, 5));
        addMachineSlot(new OutputSlot(handler, MagmaticSmelteryBlockEntity.SLOT_ALLOY_OUT, 126, 23));
        addMachineSlot(new SlotItemHandler(handler, MagmaticSmelteryBlockEntity.SLOT_ALLOY_IN, 155, 5));
        addMachineSlot(new OutputSlot(handler, MagmaticSmelteryBlockEntity.SLOT_PRODUCT, 155, 23));
        addMachineSlot(new OutputSlot(handler, MagmaticSmelteryBlockEntity.SLOT_SLAG, 140, 41));
        addPlayerInventory(playerInventory);
    }

    // ---- 客户端读数 ----

    public int oreProgress() {
        return data.extraWide(EXTRA_ORE_PROGRESS);
    }

    public int oreRequired() {
        return data.extraWide(EXTRA_ORE_REQUIRED);
    }

    public OreProcessing.Status oreStatus() {
        return OreProcessing.Status.byId(data.extra(EXTRA_ORE_STATUS));
    }

    public int oreCost() {
        return data.extraWide(EXTRA_ORE_COST);
    }

    public int alloyProgress() {
        return data.extraWide(EXTRA_ALLOY_PROGRESS);
    }

    public int alloyRequired() {
        return data.extraWide(EXTRA_ALLOY_REQUIRED);
    }

    public OreProcessing.Status alloyStatus() {
        return OreProcessing.Status.byId(data.extra(EXTRA_ALLOY_STATUS));
    }

    public int alloyCost() {
        return data.extraWide(EXTRA_ALLOY_COST);
    }

    public int lava() {
        return data.extraWide(EXTRA_LAVA);
    }

    public int lavaCapacity() {
        return data.extraWide(EXTRA_CAPACITY);
    }
}
