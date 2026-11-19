package com.flameforge.essentialcraft5.recipe;

import com.flameforge.essentialcraft5.item.BuilderMode;
import com.flameforge.essentialcraft5.item.BuilderModuleItem;
import com.flameforge.essentialcraft5.item.BuilderModules;
import com.flameforge.essentialcraft5.registry.ECBuilder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * 把模块装到创世神之杖上（builder_upgrade.md §2.2），写法照 {@link ArmorModuleRecipe}：工作台里正好一根创世神之杖或铺设魔杖（任何模式、任何档位）
 * + 正好一块魔杖模块，别的格子全空，形状不限 → 产物是装上模块的那根（整份 NBT 照抄，末尾追加注册名，见 {@link BuilderModules#install}）。
 * 槽满、已有同种、回收装一阶、魔杖叠了不止一件都不出产物（{@link BuilderModules#check}）。原料照常各扣一件，模块装进 NBT 后只存在这一份。
 * <p>
 * 特殊配方（{@code isSpecial} 为真）：配方书、合成框架的反查都不列它，模块物品的提示写怎么装。魔力合成器没装框架时按配方表匹配，
 * 也能装（魔杖、模块各扣一件，不会多出东西）。序列化器 {@code essentialcraft5:builder_module_install}（原版 {@code SimpleCraftingRecipeSerializer}），
 * 数据 {@code data/essentialcraft5/recipes/builder/builder_module_install.json}。拆不走配方，走魔杖的操作包。
 */
public class BuilderModuleRecipe extends CustomRecipe {

    public BuilderModuleRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        Pair pair = find(container);
        return pair != null && BuilderModules.check(pair.wand, pair.kind) == BuilderModules.Check.OK;
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess access) {
        Pair pair = find(container);
        return pair == null ? ItemStack.EMPTY : BuilderModules.install(pair.wand, pair.kind);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ECBuilder.BUILDER_MODULE_INSTALL.get();
    }

    private record Pair(ItemStack wand, BuilderModules.Kind kind) {
    }

    /** 正好一根魔杖 + 正好一块模块、别的全空；不是这样返回 null */
    private static Pair find(CraftingContainer container) {
        ItemStack wand = ItemStack.EMPTY;
        BuilderModules.Kind kind = null;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            BuilderModules.Kind found = BuilderModuleItem.kindOf(stack);
            if (found != null) {
                if (kind != null) {
                    return null;
                }
                kind = found;
            } else if (BuilderMode.of(stack) != null) {
                if (!wand.isEmpty()) {
                    return null;
                }
                wand = stack;
            } else {
                return null;
            }
        }
        return wand.isEmpty() || kind == null ? null : new Pair(wand, kind);
    }
}
