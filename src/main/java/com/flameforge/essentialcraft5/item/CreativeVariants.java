package com.flameforge.essentialcraft5.item;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/** 在创造模式物品栏里放多个变体（对应原作 getSubItems）。 */
public interface CreativeVariants {

    List<ItemStack> creativeVariants();
}
