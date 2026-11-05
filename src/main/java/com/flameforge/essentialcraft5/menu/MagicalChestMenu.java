package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.block.MagicalChestBlock;
import com.flameforge.essentialcraft5.blockentity.MagicalChestBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 魔力箱 / 虚空箱的菜单（原作 ContainerMagicalChest）。不继承 {@code MachineMenu}（魔力箱不是 MRU 机器）。
 * <p>
 * 格子坐标取自 {@link MagicalChestBlock.Layout}：魔力箱箱子格 (8, 18)、玩家物品栏 (8, 140)、快捷栏 y 198，比原版 {@code ChestMenu.sixRows} 低 1 像素，
 * 所以不能直接用原版菜单（贴图 magical_chest.png 照原作画）；虚空箱 (12, 8) / (48, 174) / 快捷栏 y 232。
 * <p>
 * 客户端构造不读任何额外数据（原版 {@code openMenu} 打开，旁观者也能看），用一个空的同格数物品栏，内容由菜单同步过来。
 * 服务端菜单建好时 {@link MagicalChestBlockEntity#startOpen}，关掉时 {@link MagicalChestBlockEntity#stopOpen}；
 * 方块没了或人走远（8 格）立刻关界面（{@link Container#stillValidBlockEntity}）。
 */
public class MagicalChestMenu extends AbstractContainerMenu {

    private final MagicalChestBlock.Layout layout;
    private final @Nullable MagicalChestBlockEntity chest;
    private final int chestSlots;

    /** 客户端：注册 MenuType 时用 */
    public static MagicalChestMenu client(MenuType<?> type, int containerId, Inventory playerInventory, MagicalChestBlock.Layout layout) {
        return new MagicalChestMenu(type, containerId, playerInventory, layout, new ItemStackHandler(layout.slots()), null);
    }

    public MagicalChestMenu(MenuType<?> type, int containerId, Inventory playerInventory, MagicalChestBlock.Layout layout,
                            IItemHandler handler, @Nullable MagicalChestBlockEntity chest) {
        super(type, containerId);
        this.layout = layout;
        this.chest = chest;
        this.chestSlots = layout.slots();
        for (int row = 0; row < layout.rows(); row++) {
            for (int col = 0; col < layout.cols(); col++) {
                addSlot(new ChestSlot(handler, col + row * layout.cols(),
                        layout.slotX() + col * 18, layout.slotY() + row * 18, chest));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, layout.invX() + col * 18, layout.invY() + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, layout.invX() + col * 18, layout.invY() + 58));
        }
        if (chest != null) {
            chest.startOpen(playerInventory.player);
        }
    }

    public MagicalChestBlock.Layout layout() {
        return layout;
    }

    /** 服务端菜单对应的箱子；客户端为 null */
    public @Nullable MagicalChestBlockEntity chest() {
        return chest;
    }

    @Override
    public boolean stillValid(Player player) {
        return chest == null || Container.stillValidBlockEntity(chest, player);
    }

    /** Shift 点击：箱子 → 物品栏（从后往前）；物品栏 → 箱子。照原版 ChestMenu */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < chestSlots) {
                if (!moveItemStackTo(stack, chestSlots, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, 0, chestSlots, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (chest != null) {
            chest.stopOpen(player);
        }
    }

    /**
     * 箱子格。{@link SlotItemHandler#setChanged} 什么都不做（它的 container 是个空壳），而原版菜单合并堆叠时是原地改数量再调 setChanged——
     * 不补这一下，Shift 点击并进已有堆叠的改动不会让箱子标记存盘，重启后回到旧数量（复制 / 吞物品）。
     */
    private static final class ChestSlot extends SlotItemHandler {

        private final @Nullable MagicalChestBlockEntity chest;

        ChestSlot(IItemHandler handler, int index, int x, int y, @Nullable MagicalChestBlockEntity chest) {
            super(handler, index, x, y);
            this.chest = chest;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            if (chest != null) {
                chest.setChanged();
            }
        }
    }
}
