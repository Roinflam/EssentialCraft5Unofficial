package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.blockentity.HeatGeneratorBlockEntity;
import com.flameforge.essentialcraft5.registry.ECGenerators;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.function.IntSupplier;
import java.util.stream.Stream;

/**
 * 热力产能器界面。槽位坐标照搬原作 ContainerHeatGenerator：燃料 (108, 41)，魔力炉渣 (144, 41)。
 * 额外数值（都拆两格）：剩余燃烧时间、这个燃料的总燃烧时间、当前四周方块下的每 tick 产量。
 */
public class HeatGeneratorMenu extends MachineMenu {

    public static final int EXTRA_BURN = 0;
    public static final int EXTRA_BURN_MAX = 2;
    public static final int EXTRA_GENERATION = 4;
    private static final int EXTRA_COUNT = 6;

    public HeatGeneratorMenu(int id, Inventory playerInventory, HeatGeneratorBlockEntity be) {
        this(id, playerInventory, be.getBlockPos(), be.getInventory(), MachineData.server(be, concat(
                MachineData.wide(be::getBurnTime),
                MachineData.wide(be::getMaxBurnTime),
                MachineData.wide(be::getGeneration))), be);
    }

    public HeatGeneratorMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos(), clientHandler(2, HeatGeneratorBlockEntity::slotRule),
                MachineData.client(EXTRA_COUNT), null);
    }

    private HeatGeneratorMenu(int id, Inventory playerInventory, BlockPos pos, IItemHandler handler, MachineData data,
                              @Nullable HeatGeneratorBlockEntity be) {
        super(ECGenerators.HEAT_GENERATOR_MENU.get(), id, playerInventory, pos, data, ECGenerators.HEAT_GENERATOR.get(), be);
        addMachineSlot(new SlotItemHandler(handler, HeatGeneratorBlockEntity.SLOT_FUEL, 108, 41));
        addMachineSlot(new OutputSlot(handler, HeatGeneratorBlockEntity.SLOT_SLAG, 144, 41));
        addPlayerInventory(playerInventory);
    }

    /** 把几组 MachineData.wide 拼成一个参数数组 */
    static IntSupplier[] concat(IntSupplier[]... parts) {
        return Stream.of(parts).flatMap(Arrays::stream).toArray(IntSupplier[]::new);
    }

    public int burnTime() {
        return data.extraWide(EXTRA_BURN);
    }

    public int maxBurnTime() {
        return data.extraWide(EXTRA_BURN_MAX);
    }

    public int generation() {
        return data.extraWide(EXTRA_GENERATION);
    }
}
