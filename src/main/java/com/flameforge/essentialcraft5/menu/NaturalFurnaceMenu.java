package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.blockentity.NaturalFurnaceBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.registry.ECGenerators;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 自然熔炉界面。原作 ContainerFlowerBurner 只有玩家背包（canInteractWith 恒为 true、走多远都不关，这里按距离判断）；
 * 第四批改造后多一个植物格 (108, 41)：原作界面在这个位置画了一个槽位框、里面显示正在烧的那株植物，现在这个框就是真的格子，
 * 燃烧火苗仍在它正上方（界面见 NaturalFurnaceScreen）。
 * 额外数值（都拆两格）：剩余燃烧时间、这一株的总燃烧时间、正在烧的植物的物品 id、每 tick 产量。
 * 第三批加的主人状态（EXTRA_OWNER）随主人规则一起去掉了。
 */
public class NaturalFurnaceMenu extends MachineMenu {

    public static final int EXTRA_BURN = 0;
    public static final int EXTRA_BURN_MAX = 2;
    public static final int EXTRA_ITEM = 4;
    public static final int EXTRA_GENERATION = 6;
    private static final int EXTRA_COUNT = 8;

    /** 植物格在界面上的位置（槽位左上角，框从它往左上各退 1 像素画） */
    public static final int PLANT_SLOT_X = 108;
    public static final int PLANT_SLOT_Y = 41;

    public NaturalFurnaceMenu(int id, Inventory playerInventory, NaturalFurnaceBlockEntity be) {
        this(id, playerInventory, be.getBlockPos(), be.getInventory(), MachineData.server(be, HeatGeneratorMenu.concat(
                MachineData.wide(be::getBurnTime),
                MachineData.wide(be::getMaxBurnTime),
                MachineData.wide(be::getBurningItemId),
                MachineData.wide(() -> ECConfig.get(NaturalFurnaceBlockEntity.Config.mruGenerated)))), be);
    }

    public NaturalFurnaceMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos(), clientHandler(1, NaturalFurnaceBlockEntity::slotRule),
                MachineData.client(EXTRA_COUNT), null);
    }

    private NaturalFurnaceMenu(int id, Inventory playerInventory, BlockPos pos, IItemHandler handler, MachineData data,
                               @Nullable NaturalFurnaceBlockEntity be) {
        super(ECGenerators.NATURAL_FURNACE_MENU.get(), id, playerInventory, pos, data, ECGenerators.NATURAL_FURNACE.get(), be);
        addMachineSlot(new SlotItemHandler(handler, NaturalFurnaceBlockEntity.SLOT_PLANT, PLANT_SLOT_X, PLANT_SLOT_Y));
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
