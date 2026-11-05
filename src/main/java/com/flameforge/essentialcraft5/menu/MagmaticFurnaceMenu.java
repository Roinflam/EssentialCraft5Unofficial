package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.blockentity.MagmaticFurnaceBlockEntity;
import com.flameforge.essentialcraft5.blockentity.OreProcessing;
import com.flameforge.essentialcraft5.registry.ECMagmatic;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntSupplier;

/**
 * 岩浆熔炉界面（四档共用一个菜单类型）。槽位坐标照搬原作 ContainerFurnaceMagic：绑定宝石 (108, 41)，输入 (108, 5)，输出 (153, 5)。
 * 原作输出格是普通槽，这里改成只能取。
 * <p>
 * 额外数值：进度、这一档每道工序的 tick 数（服务端按档位算好；原作界面读客户端配置）、状态、当前工序、每 tick 耗能。
 */
public class MagmaticFurnaceMenu extends MachineMenu {

    public static final int EXTRA_PROGRESS = 0;
    public static final int EXTRA_REQUIRED = 2;
    public static final int EXTRA_STATUS = 4;
    public static final int EXTRA_MODE = 5;
    public static final int EXTRA_COST = 6;
    public static final int EXTRA_COUNT = 8;

    public MagmaticFurnaceMenu(int id, Inventory playerInventory, MagmaticFurnaceBlockEntity be) {
        this(id, playerInventory, be.getBlockPos(), be.getInventory(), MachineData.server(be, HeatGeneratorMenu.concat(
                        MachineData.wide(be::getProgress),
                        MachineData.wide(be::requiredTicks),
                        new IntSupplier[] {() -> be.getStatus().ordinal(), () -> be.getMode().ordinal()},
                        MachineData.wide(MagmaticFurnaceBlockEntity::cost))),
                be.getBlockState().getBlock(), be);
    }

    public MagmaticFurnaceMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos(),
                clientHandler(MagmaticFurnaceBlockEntity.SLOTS, MagmaticFurnaceBlockEntity::slotRule),
                MachineData.client(EXTRA_COUNT), null, null);
    }

    /**
     * @param block 服务端传方块实体所在的方块；客户端传 null，按坐标从客户端世界里取（四档共用这个菜单）
     */
    private MagmaticFurnaceMenu(int id, Inventory playerInventory, BlockPos pos, IItemHandler handler, MachineData data,
                                @Nullable Block block, @Nullable MagmaticFurnaceBlockEntity be) {
        super(ECMagmatic.MAGMATIC_FURNACE_MENU.get(), id, playerInventory, pos, data,
                block != null ? block : playerInventory.player.level().getBlockState(pos).getBlock(), be);
        addMachineSlot(FilteredSlot.boundGem(handler, MagmaticFurnaceBlockEntity.SLOT_GEM, 108, 41));
        addMachineSlot(new SlotItemHandler(handler, MagmaticFurnaceBlockEntity.SLOT_INPUT, 108, 5));
        addMachineSlot(new OutputSlot(handler, MagmaticFurnaceBlockEntity.SLOT_OUTPUT, 153, 5));
        addPlayerInventory(playerInventory);
    }

    // ---- 客户端读数 ----

    public int progress() {
        return data.extraWide(EXTRA_PROGRESS);
    }

    public int required() {
        return data.extraWide(EXTRA_REQUIRED);
    }

    public OreProcessing.Status status() {
        return OreProcessing.Status.byId(data.extra(EXTRA_STATUS));
    }

    public MagmaticFurnaceBlockEntity.Mode mode() {
        return MagmaticFurnaceBlockEntity.Mode.byId(data.extra(EXTRA_MODE));
    }

    public int cost() {
        return data.extraWide(EXTRA_COST);
    }
}
