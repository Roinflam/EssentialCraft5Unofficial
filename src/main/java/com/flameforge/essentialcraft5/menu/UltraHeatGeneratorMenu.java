package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.blockentity.UltraHeatGeneratorBlockEntity;
import com.flameforge.essentialcraft5.registry.ECUltraGenerators;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 终极热力产能器界面。槽位坐标照搬原作 ContainerUltraHeatGenerator：燃料 (108, 41)，魔力炉渣 (144, 41)。
 * 额外数值（都拆两格）：剩余燃烧时间、这个燃料的总燃烧时间、当前温度下的每刻产量、温度（取整）。
 */
public class UltraHeatGeneratorMenu extends MachineMenu {

    public static final int EXTRA_BURN = 0;
    public static final int EXTRA_BURN_MAX = 2;
    public static final int EXTRA_GENERATION = 4;
    public static final int EXTRA_HEAT = 6;
    private static final int EXTRA_COUNT = 8;

    public UltraHeatGeneratorMenu(int id, Inventory playerInventory, UltraHeatGeneratorBlockEntity be) {
        this(id, playerInventory, be.getBlockPos(), be.getInventory(), MachineData.server(be, HeatGeneratorMenu.concat(
                MachineData.wide(be::getBurnTime),
                MachineData.wide(be::getMaxBurnTime),
                MachineData.wide(be::getGeneration),
                MachineData.wide(be::getDisplayHeat))), be);
    }

    public UltraHeatGeneratorMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos(), clientHandler(2, UltraHeatGeneratorBlockEntity::slotRule),
                MachineData.client(EXTRA_COUNT), null);
    }

    private UltraHeatGeneratorMenu(int id, Inventory playerInventory, BlockPos pos, IItemHandler handler, MachineData data,
                                   @Nullable UltraHeatGeneratorBlockEntity be) {
        super(ECUltraGenerators.ULTRA_HEAT_GENERATOR_MENU.get(), id, playerInventory, pos, data,
                ECUltraGenerators.ULTRA_HEAT_GENERATOR.get(), be);
        addMachineSlot(new SlotItemHandler(handler, UltraHeatGeneratorBlockEntity.SLOT_FUEL, 108, 41));
        addMachineSlot(new OutputSlot(handler, UltraHeatGeneratorBlockEntity.SLOT_SLAG, 144, 41));
        addPlayerInventory(playerInventory);
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

    public int heat() {
        return data.extraWide(EXTRA_HEAT);
    }
}
