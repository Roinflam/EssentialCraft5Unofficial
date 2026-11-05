package com.flameforge.essentialcraft5.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiPredicate;

/**
 * 机器菜单基类。对应原作 DummyCore 的 ContainerInventory。
 * <p>
 * 客户端的菜单不依赖客户端方块实体：槽位用同样大小的空 ItemStackHandler（内容由原版菜单同步填），
 * 数值从 {@link MachineData} 取。这样即使客户端方块实体还没到/已卸载也不会出错。
 * 客户端的假 ItemStackHandler 用 {@link #clientHandler} 套上和方块实体相同的槽位规则，两端的 mayPlace 才一致。
 * <p>
 * 原作 ContainerInventory 在父类构造器里调 setupSlots（子类字段还没初始化）、构造时调 openInventory，
 * 这些坑这里都没有：子类在自己构造器里按顺序加槽位。
 */
public abstract class MachineMenu extends AbstractContainerMenu {

    public static final int PLAYER_INVENTORY_X = 8;
    public static final int PLAYER_INVENTORY_Y = 84;

    protected final BlockPos pos;
    protected final MachineData data;
    /** 服务端才有；客户端为 null */
    @Nullable
    private final BlockEntity blockEntity;
    private final ContainerLevelAccess access;
    private final Block block;
    private int machineSlotCount;

    protected MachineMenu(MenuType<?> type, int id, Inventory playerInventory, BlockPos pos, MachineData data, Block block,
                          @Nullable BlockEntity blockEntity) {
        super(type, id);
        this.pos = pos;
        this.blockEntity = blockEntity;
        this.data = data;
        this.block = block;
        this.access = ContainerLevelAccess.create(playerInventory.player.level(), pos);
        addDataSlots(data);
    }

    public BlockPos getPos() {
        return pos;
    }

    public MachineData getData() {
        return data;
    }

    /** 机器槽位必须在玩家背包之前全部加完 */
    protected void addMachineSlot(Slot slot) {
        addSlot(slot);
        machineSlotCount++;
    }

    /** 原作 setupPlayerInventory：背包从 (8, 84) 开始，快捷栏在 y=142 */
    protected void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, PLAYER_INVENTORY_X + col * 18, PLAYER_INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, PLAYER_INVENTORY_X + col * 18, PLAYER_INVENTORY_Y + 58));
        }
    }

    /**
     * 服务端按方块实体本身判断：机器被挖掉、原地又放了一台同种机器时，旧菜单必须立即失效，
     * 否则它还指着旧方块实体的物品栏（原作只比方块类型，这里能刷物品）。
     */
    @Override
    public boolean stillValid(Player player) {
        return blockEntity != null ? Container.stillValidBlockEntity(blockEntity, player) : stillValid(access, player, block);
    }

    /** 客户端菜单用的物品栏：内容由原版菜单同步填，槽位规则和服务端方块实体用同一个 */
    public static ItemStackHandler clientHandler(int size, BiPredicate<Integer, ItemStack> slotRule) {
        return new ItemStackHandler(size) {
            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                return slotRule.test(slot, stack);
            }
        };
    }

    /**
     * Shift 点击：机器槽 → 背包（从后往前）；背包 → 机器里肯接收它的槽。
     * 不用原版 moveItemStackTo 往机器里塞：它合并已有堆叠时不看 mayPlace，会把东西塞进输出槽。
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < machineSlotCount) {
            if (!moveItemStackTo(stack, machineSlotCount, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveIntoMachine(stack)) {
            return ItemStack.EMPTY;
        }
        // 用 set 写回而不是 setChanged：SlotItemHandler 的 setChanged 不通知 ItemStackHandler，
        // 原地改了数量的机器槽不会被标记存盘
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.set(stack);
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    private boolean moveIntoMachine(ItemStack stack) {
        boolean moved = false;
        // 先并入已有的同种堆叠，再放空槽，两轮都只考虑肯接收它的槽
        for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
            for (int i = 0; i < machineSlotCount && !stack.isEmpty(); i++) {
                Slot target = slots.get(i);
                if (!target.mayPlace(stack)) {
                    continue;
                }
                ItemStack existing = target.getItem();
                if (pass == 0 && !existing.isEmpty() && ItemStack.isSameItemSameTags(existing, stack)) {
                    int limit = Math.min(target.getMaxStackSize(stack), stack.getMaxStackSize());
                    int move = Math.min(stack.getCount(), limit - existing.getCount());
                    if (move > 0) {
                        ItemStack merged = existing.copy();
                        merged.grow(move);
                        stack.shrink(move);
                        target.set(merged);
                        moved = true;
                    }
                } else if (pass == 1 && existing.isEmpty()) {
                    int move = Math.min(stack.getCount(), target.getMaxStackSize(stack));
                    target.setByPlayer(stack.split(move));
                    moved = true;
                }
            }
        }
        return moved;
    }
}
