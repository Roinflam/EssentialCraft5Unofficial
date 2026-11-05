package com.flameforge.essentialcraft5.compat.jei;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.dev.DevShowroom;
import com.flameforge.essentialcraft5.enchant.EnchantUpgrades;
import com.flameforge.essentialcraft5.item.AncientScrollItem;
import com.flameforge.essentialcraft5.recipe.MagicianTableRecipe;
import com.flameforge.essentialcraft5.recipe.MithrilineFurnaceRecipe;
import com.flameforge.essentialcraft5.recipe.OreSmeltingRecipe;
import com.flameforge.essentialcraft5.recipe.RadiatingChamberRecipe;
import com.flameforge.essentialcraft5.recipe.WindImbueRecipe;
import com.flameforge.essentialcraft5.registry.ECCultivator;
import com.flameforge.essentialcraft5.registry.ECDecomposer;
import com.flameforge.essentialcraft5.registry.ECEnchantUpgrade;
import com.flameforge.essentialcraft5.registry.ECMagicianTable;
import com.flameforge.essentialcraft5.registry.ECMagmatic;
import com.flameforge.essentialcraft5.registry.ECMithriline;
import com.flameforge.essentialcraft5.registry.ECRadiatingChamber;
import com.flameforge.essentialcraft5.registry.ECRecipes;
import com.flameforge.essentialcraft5.registry.ECScrolls;
import com.flameforge.essentialcraft5.registry.ECWind;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.ingredients.subtypes.IIngredientSubtypeInterpreter;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI 里显示本模组自己的加工方式：魔力工作台、辐射仓、米斯莱熔炉、岩浆冶炼炉 / 岩浆熔炉、风之符文注风、魔力栽培器，
 * 以及附魔增幅台 / 升华台每升一级要付的东西（这两台不是配方，是按等级算出来的价格表）、元素分解器的四种元素（按物品标签分，也不是配方）。
 * <p>
 * 只在装了 JEI 时由 JEI 按 {@link JeiPlugin} 注解找到并加载，本模组别处不引用这个包，没装 JEI 时这里的类一个都不会被加载。
 * 编译期只依赖 JEI 的 api 构件（build.gradle 的 compileOnly）。配方数据取客户端当前世界的 RecipeManager，
 * 数据包改了配方、重新进服后 JEI 重建时跟着变。米斯莱熔炉、矿石加工、注风三种用研究手册登记的画法（{@code GuideRecipeViews}），
 * 两边写的说明文字（ESPE、产量）始终一致。
 */
@JeiPlugin
public final class EC5JeiPlugin implements IModPlugin {

    private static final ResourceLocation UID = EssentialCraft5.id("jei");

    static final RecipeType<MagicianTableRecipe> MAGICIAN_TABLE =
            RecipeType.create(EssentialCraft5.MODID, "magician_table", MagicianTableRecipe.class);
    static final RecipeType<RadiatingChamberRecipe> RADIATING_CHAMBER =
            RecipeType.create(EssentialCraft5.MODID, "radiating_chamber", RadiatingChamberRecipe.class);
    static final RecipeType<MithrilineFurnaceRecipe> MITHRILINE_FURNACE =
            RecipeType.create(EssentialCraft5.MODID, "mithriline_furnace", MithrilineFurnaceRecipe.class);
    static final RecipeType<OreSmeltingRecipe> ORE_SMELTING =
            RecipeType.create(EssentialCraft5.MODID, "ore_smelting", OreSmeltingRecipe.class);
    static final RecipeType<WindImbueRecipe> WIND_IMBUE =
            RecipeType.create(EssentialCraft5.MODID, "wind_imbue", WindImbueRecipe.class);
    static final RecipeType<EnchantStepCategory.Upgrade> ENCHANT_AMPLIFIER =
            RecipeType.create(EssentialCraft5.MODID, "enchant_amplifier", EnchantStepCategory.Upgrade.class);
    static final RecipeType<EnchantStepCategory.Upgrade> ENCHANT_ASCENDER =
            RecipeType.create(EssentialCraft5.MODID, "enchant_ascender", EnchantStepCategory.Upgrade.class);
    static final RecipeType<CultivatorCategory.Page> CULTIVATOR =
            RecipeType.create(EssentialCraft5.MODID, "cultivator", CultivatorCategory.Page.class);
    static final RecipeType<DecomposerCategory.Page> DECOMPOSER =
            RecipeType.create(EssentialCraft5.MODID, "elemental_decomposer", DecomposerCategory.Page.class);

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    /**
     * 源质残卷按卷号（NBT {@code Volume}）分子类型：创造栏里放的七卷各占一格，不然 JEI 按物品去重只剩一张。
     * 不带卷号的无字残卷不在创造栏里，JEI 的物品列表只收创造栏的东西，所以也不会列出来
     */
    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.registerSubtypeInterpreter(ECScrolls.ANCIENT_SCROLL.get(), (stack, context) -> {
            int volume = AncientScrollItem.volume(stack);
            return volume < 0 ? IIngredientSubtypeInterpreter.NONE : Integer.toString(volume);
        });
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
                new MagicianTableCategory(gui),
                new RadiatingChamberCategory(gui),
                new ViewCategory<>(gui, MITHRILINE_FURNACE, new ItemStack(ECMithriline.MITHRILINE_FURNACE.get()),
                        ECMithriline.MITHRILINE_FURNACE.get().getName()),
                new ViewCategory<>(gui, ORE_SMELTING, new ItemStack(ECMagmatic.MAGMATIC_SMELTERY.get()),
                        Component.translatable("jei.essentialcraft5.category.ore_smelting")),
                new ViewCategory<>(gui, WIND_IMBUE, new ItemStack(ECWind.WIND_RUNE.get()),
                        Component.translatable("jei.essentialcraft5.category.wind_imbue")),
                new EnchantStepCategory(gui, ENCHANT_AMPLIFIER, EnchantUpgrades.Tier.AMPLIFIER),
                new EnchantStepCategory(gui, ENCHANT_ASCENDER, EnchantUpgrades.Tier.ASCENDER),
                new CultivatorCategory(gui),
                new DecomposerCategory(gui));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        Minecraft minecraft = Minecraft.getInstance();
        RecipeManager recipes = minecraft.level == null ? null : minecraft.level.getRecipeManager();
        if (recipes != null) {
            registration.addRecipes(MAGICIAN_TABLE, sorted(recipes.getAllRecipesFor(ECRecipes.MAGICIAN_TABLE.get())));
            registration.addRecipes(RADIATING_CHAMBER, sorted(recipes.getAllRecipesFor(ECRecipes.RADIATING_CHAMBER.get())));
            registration.addRecipes(MITHRILINE_FURNACE, sorted(recipes.getAllRecipesFor(ECMithriline.MITHRILINE_FURNACE_TYPE.get())));
            registration.addRecipes(ORE_SMELTING, sorted(recipes.getAllRecipesFor(ECMagmatic.ORE_SMELTING.get())));
            registration.addRecipes(WIND_IMBUE, sorted(recipes.getAllRecipesFor(ECWind.WIND_IMBUE.get())));
            registration.addRecipes(CULTIVATOR, CultivatorCategory.pages(recipes));
        }
        registration.addRecipes(DECOMPOSER, DecomposerCategory.pages());
        registration.addRecipes(ENCHANT_AMPLIFIER, EnchantStepCategory.steps(EnchantUpgrades.Tier.AMPLIFIER));
        registration.addRecipes(ENCHANT_ASCENDER, EnchantStepCategory.steps(EnchantUpgrades.Tier.ASCENDER));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ECMagicianTable.MAGICIAN_TABLE.get()), MAGICIAN_TABLE);
        registration.addRecipeCatalyst(new ItemStack(ECRadiatingChamber.RADIATING_CHAMBER.get()), RADIATING_CHAMBER);
        registration.addRecipeCatalyst(new ItemStack(ECMithriline.MITHRILINE_FURNACE.get()), MITHRILINE_FURNACE);
        registration.addRecipeCatalyst(new ItemStack(ECMagmatic.MAGMATIC_SMELTERY.get()), ORE_SMELTING);
        registration.addRecipeCatalyst(new ItemStack(ECMagmatic.FORTIFIED_MAGMATIC_FURNACE.get()), ORE_SMELTING);
        registration.addRecipeCatalyst(new ItemStack(ECMagmatic.MAGIC_MAGMATIC_FURNACE.get()), ORE_SMELTING);
        registration.addRecipeCatalyst(new ItemStack(ECMagmatic.PALE_MAGMATIC_FURNACE.get()), ORE_SMELTING);
        registration.addRecipeCatalyst(new ItemStack(ECMagmatic.VOID_MAGMATIC_FURNACE.get()), ORE_SMELTING);
        registration.addRecipeCatalyst(new ItemStack(ECWind.WIND_RUNE.get()), WIND_IMBUE);
        registration.addRecipeCatalyst(new ItemStack(ECEnchantUpgrade.ENCHANT_AMPLIFIER.get()), ENCHANT_AMPLIFIER);
        registration.addRecipeCatalyst(new ItemStack(ECEnchantUpgrade.ENCHANT_ASCENDER.get()), ENCHANT_ASCENDER);
        registration.addRecipeCatalyst(new ItemStack(ECCultivator.MAGICAL_CULTIVATOR.get()), CULTIVATOR);
        registration.addRecipeCatalyst(new ItemStack(ECDecomposer.ELEMENTAL_DECOMPOSER.get()), DECOMPOSER);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        // 展示厅的 JEI 模式（-PecShowroomJei，只在开发环境）：逐个打开本模组的分类拍照
        if (DevShowroom.JEI_ONLY) {
            DevJeiShowroom.start(runtime);
        }
    }

    /** 按配方 id 排序：JEI 里同一种机器的配方翻页顺序固定，和数据包加载顺序无关 */
    private static <T extends Recipe<?>> List<T> sorted(List<T> recipes) {
        List<T> list = new ArrayList<>(recipes);
        list.sort((a, b) -> a.getId().compareTo(b.getId()));
        return list;
    }
}
