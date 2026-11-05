package com.flameforge.essentialcraft5.item.module;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

/**
 * 能装模块的盔甲（batch10.md §4.3 第 2 条）。实现它的必须是 {@link ArmorItem}：模块按这件的部位（{@link ArmorItem#getType()}）配。
 * 现在只有宅男套装（{@code ComputerArmorItem}，槽数读 {@code general.computer_modules.slots_*}，默认 2 / 2 / 1 / 1，再加同调阶给的）；虚空套装不开槽（D10 A）。
 * <p>
 * 模块只在盔甲穿在它自己的部位上时才算（{@link ModuleCache} 只看四个盔甲槽）；放在背包里的照样带着 NBT，换上去就生效。
 * 第十三批起槽数按这一件算（同调阶记在物品上，batch13.md §4.4），所以参数是物品不是部位；别处一律经 {@link ArmorModules#slots} 读（夹到 0 ~ {@link ModuleConfig#MAX_SLOTS}）。
 */
public interface IModularArmor {

    /**
     * 这一件有几个模块槽（0 = 不能装）。
     *
     * @param stack 这一件（部位就是它的 {@link ArmorItem#getType()}）
     * @return 0 ~ {@link ModuleConfig#MAX_SLOTS}（超出的由 {@link ArmorModules#slots} 夹掉）
     */
    int moduleSlots(ItemStack stack);
}
