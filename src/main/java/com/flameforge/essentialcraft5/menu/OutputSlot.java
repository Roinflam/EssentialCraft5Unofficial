package com.flameforge.essentialcraft5.menu;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

/** 只能取、不能放的槽位。 */
public class OutputSlot extends SlotItemHandler {

    public OutputSlot(IItemHandler handler, int index, int x, int y) {
        super(handler, index, x, y);
    }

    @Override
    public boolean mayPlace(@NotNull ItemStack stack) {
        return false;
    }
}
