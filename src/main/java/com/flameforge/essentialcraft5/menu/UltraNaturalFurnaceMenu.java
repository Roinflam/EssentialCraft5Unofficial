package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.blockentity.UltraNaturalFurnaceBlockEntity;
import com.flameforge.essentialcraft5.registry.ECUltraGenerators;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 终极自然熔炉界面。原作 ContainerUltraFlowerBurner 只有玩家背包（canInteractWith 恒为 true、走多远都不关，这里按距离判断），
 * 界面在 (107, 40) 画一个槽位框、里面显示正在烧的那株植物；现在那个框就是真的格子 (108, 41)，燃烧火苗仍在它正上方（同自然熔炉）。
 * 额外数值（都拆两格）：剩余燃烧时间、这一件的总燃烧时间、正在烧的物品 id、正在烧的这件每刻产量（没在烧是 0）。
 */
public class UltraNaturalFurnaceMenu extends MachineMenu {

    public static final int EXTRA_BURN = 0;
    public static final int EXTRA_BURN_MAX = 2;
    public static final int EXTRA_ITEM = 4;
    public static final int EXTRA_GENERATION = 6;
    private static final int EXTRA_COUNT = 8;

    /** 格子在界面上的位置（槽位左上角，框从它往左上各退 1 像素画） */
    public static final int PLANT_SLOT_X = 108;
    public static final int PLANT_SLOT_Y = 41;

    public UltraNaturalFurnaceMenu(int id, Inventory playerInventory, UltraNaturalFurnaceBlockEntity be) {
        this(id, playerInventory, be.getBlockPos(), be.getInventory(), MachineData.server(be, HeatGeneratorMenu.concat(
                MachineData.wide(be::getBurnTime),
                MachineData.wide(be::getMaxBurnTime),
                MachineData.wide(be::getBurningItemId),
                MachineData.wide(be::getBurnMRU))), be);
    }

    public UltraNaturalFurnaceMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos(), clientHandler(1, UltraNaturalFurnaceBlockEntity::slotRule),
                MachineData.client(EXTRA_COUNT), null);
    }

    private UltraNaturalFurnaceMenu(int id, Inventory playerInventory, BlockPos pos, IItemHandler handler, MachineData data,
                                    @Nullable UltraNaturalFurnaceBlockEntity be) {
        super(ECUltraGenerators.ULTRA_NATURAL_FURNACE_MENU.get(), id, playerInventory, pos, data,
                ECUltraGenerators.ULTRA_NATURAL_FURNACE.get(), be);
        addMachineSlot(new SlotItemHandler(handler, UltraNaturalFurnaceBlockEntity.SLOT_PLANT, PLANT_SLOT_X, PLANT_SLOT_Y));
        addPlayerInventory(playerInventory);
    }

    public int burnTime() {
        return data.extraWide(EXTRA_BURN);
    }

    public int maxBurnTime() {
        return data.extraWide(EXTRA_BURN_MAX);
    }

    public int burningItemId() {
        return data.extraWide(EXTRA_ITEM);
    }

    public int generation() {
        return data.extraWide(EXTRA_GENERATION);
    }
}
