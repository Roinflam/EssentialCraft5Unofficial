package com.flameforge.essentialcraft5.recipe;

import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 3×3 的合成格副本，给魔力合成器和合成框架拿去匹配工作台配方（{@code CraftingRecipe.matches / assemble / getRemainingItems}）。
 * <p>
 * 不用原版 {@code TransientCraftingContainer}：它的 {@code setItem / removeItem} 会调 {@code menu.slotsChanged}，菜单不能为空，
 * 而合成器是在没有界面的时候（红石脉冲里）合成的。这里只是一份独立的副本，改它不影响机器的物品栏。
 */
public final class CrafterGrid implements CraftingContainer {

    public static final int WIDTH = 3;
    public static final int HEIGHT = 3;
    public static final int SIZE = WIDTH * HEIGHT;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

    /** 空的 3×3 */
    public CrafterGrid() {
    }

    /** 按行排的 9 格（多出的忽略、不够的当空格），每格存一份副本 */
    public CrafterGrid(ItemStack[] cells) {
        for (int i = 0; i < SIZE && i < cells.length; i++) {
            ItemStack cell = cells[i];
            items.set(i, cell == null ? ItemStack.EMPTY : cell.copy());
        }
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public List<ItemStack> getItems() {
        return List.copyOf(items);
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < SIZE ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return ContainerHelper.removeItem(items, slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot >= 0 && slot < SIZE) {
            items.set(slot, stack);
        }
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    @Override
    public void fillStackedContents(StackedContents contents) {
        for (ItemStack stack : items) {
            contents.accountSimpleStack(stack);
        }
    }
}
