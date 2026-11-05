package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.blockentity.MagicalEnchanterBlockEntity;
import com.flameforge.essentialcraft5.registry.ECDevices;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 魔力附魔台界面。槽位坐标照搬原作 ContainerMagicalEnchanter：宝石 (155, 23)，输入 (26, 23)，输出 (94, 23)。
 * 原作输出槽是 SlotGeneric（能往里放东西），这里改成只能取。
 * <p>
 * 额外数值（都在 short 范围内：等级上限 1000，耗时最多 10000 tick）：状态码、书架等级、正在附的等级、进度、所需进度。
 */
public class MagicalEnchanterMenu extends MachineMenu {

    public static final int EXTRA_STATUS = 0;
    public static final int EXTRA_POWER = 1;
    public static final int EXTRA_LEVEL = 2;
    public static final int EXTRA_PROGRESS = 3;
    public static final int EXTRA_REQUIRED = 4;
    private static final int EXTRA_COUNT = 5;

    public MagicalEnchanterMenu(int id, Inventory playerInventory, MagicalEnchanterBlockEntity be) {
        this(id, playerInventory, be.getBlockPos(), be.getInventory(),
                MachineData.server(be, be::statusCode, be::power, be::lockedLevel, be::getProgress, be::requiredProgress), be);
    }

    public MagicalEnchanterMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos(),
                clientHandler(MagicalEnchanterBlockEntity.SLOTS, MagicalEnchanterBlockEntity::slotRule),
                MachineData.client(EXTRA_COUNT), null);
    }

    private MagicalEnchanterMenu(int id, Inventory playerInventory, BlockPos pos, IItemHandler handler, MachineData data,
                                 @Nullable MagicalEnchanterBlockEntity be) {
        super(ECDevices.MAGICAL_ENCHANTER_MENU.get(), id, playerInventory, pos, data, ECDevices.MAGICAL_ENCHANTER.get(), be);
        addMachineSlot(FilteredSlot.boundGem(handler, MagicalEnchanterBlockEntity.SLOT_GEM, 155, 23));
        addMachineSlot(new SlotItemHandler(handler, MagicalEnchanterBlockEntity.SLOT_INPUT, 26, 23));
        addMachineSlot(new OutputSlot(handler, MagicalEnchanterBlockEntity.SLOT_OUTPUT, 94, 23));
        addPlayerInventory(playerInventory);
    }

    public int status() {
        return data.extra(EXTRA_STATUS);
    }

    /** 周围书架现在能提供的等级 */
    public int power() {
        return data.extra(EXTRA_POWER);
    }

    /** 正在附的等级；没开工为 0 */
    public int level() {
        return data.extra(EXTRA_LEVEL);
    }

    public int progress() {
        return data.extra(EXTRA_PROGRESS);
    }

    public int required() {
        return data.extra(EXTRA_REQUIRED);
    }
}
