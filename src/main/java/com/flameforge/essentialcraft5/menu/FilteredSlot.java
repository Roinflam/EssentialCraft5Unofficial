package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.item.BoundGemItem;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

/** 只接受特定物品的槽位（对应原作 SlotGeneric / SlotBoundEssence 等）。 */
public class FilteredSlot extends SlotItemHandler {

    private final Predicate<ItemStack> filter;

    public FilteredSlot(IItemHandler handler, int index, int x, int y, Predicate<ItemStack> filter) {
        super(handler, index, x, y);
        this.filter = filter;
    }

    /** 绑定宝石槽，空着时显示原作 SlotBoundEssence 的底图（风元素掉落物） */
    public static FilteredSlot boundGem(IItemHandler handler, int index, int x, int y) {
        FilteredSlot slot = new FilteredSlot(handler, index, x, y, stack -> stack.getItem() instanceof BoundGemItem);
        slot.setBackground(InventoryMenu.BLOCK_ATLAS, EssentialCraft5.id("item/elemental/drop_air"));
        return slot;
    }

    @Override
    public boolean mayPlace(@NotNull ItemStack stack) {
        return filter.test(stack) && super.mayPlace(stack);
    }
}
