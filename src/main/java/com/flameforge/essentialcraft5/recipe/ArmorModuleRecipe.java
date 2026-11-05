package com.flameforge.essentialcraft5.recipe;

import com.flameforge.essentialcraft5.item.module.ArmorModuleItem;
import com.flameforge.essentialcraft5.item.module.ArmorModules;
import com.flameforge.essentialcraft5.item.module.IModularArmor;
import com.flameforge.essentialcraft5.item.module.ModuleType;
import com.flameforge.essentialcraft5.registry.ECModules;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * 把模块装到宅男套装上（batch10.md §4.3 第 5 条），写法照原版染皮甲的 {@code ArmorDyeRecipe}：工作台里正好一件能装模块的盔甲（{@link IModularArmor}）
 * + 正好一块模块（{@link ArmorModuleItem}），别的格子全空，形状不限 → 产物是装上模块的那件（拷贝原来的 NBT、电量、耐久、附魔，末尾追加 {@code {id, on:1b}}，
 * 见 {@link ArmorModules#install}）。槽满、部位不对、这件已有同种、盔甲叠了不止一件、不是能装模块的盔甲、模块被 {@code general.computer_modules.disabled}
 * 停用了都不出产物（{@link ArmorModules#check}；停用的装上去只会白占一格，产物格的预览读的是同步过来的服务端配置）。
 * <p>
 * 特殊配方（{@code isSpecial} 为真）：配方书里不显示，手册「终端与模块」一章写怎么装。序列化器 {@code essentialcraft5:armor_module_install}
 * （原版 {@code SimpleCraftingRecipeSerializer}，JSON 只有 {@code type} 和可选的 {@code category}），数据
 * {@code data/essentialcraft5/recipes/terminal/armor_module_install.json}。拆模块不走配方，走宅男终端。
 */
public class ArmorModuleRecipe extends CustomRecipe {

    public ArmorModuleRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        Pair pair = find(container);
        return pair != null && ArmorModules.check(pair.armor, pair.module) == ArmorModules.InstallCheck.OK;
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess access) {
        Pair pair = find(container);
        ModuleType type = pair == null ? null : ArmorModules.moduleOf(pair.module);
        return type == null ? ItemStack.EMPTY : ArmorModules.install(pair.armor, type);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ECModules.ARMOR_MODULE_INSTALL.get();
    }

    private record Pair(ItemStack armor, ItemStack module) {
    }

    /** 正好一件能装模块的盔甲 + 正好一块模块、别的全空；不是这样返回 null */
    private static Pair find(CraftingContainer container) {
        ItemStack armor = ItemStack.EMPTY;
        ItemStack module = ItemStack.EMPTY;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (ArmorModules.moduleOf(stack) != null) {
                if (!module.isEmpty()) {
                    return null;
                }
                module = stack;
            } else if (stack.getItem() instanceof IModularArmor) {
                if (!armor.isEmpty()) {
                    return null;
                }
                armor = stack;
            } else {
                return null;
            }
        }
        return armor.isEmpty() || module.isEmpty() ? null : new Pair(armor, module);
    }
}
