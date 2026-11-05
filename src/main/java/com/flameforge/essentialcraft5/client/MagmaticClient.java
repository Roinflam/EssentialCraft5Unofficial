package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.guide.GuideRecipeViews;
import com.flameforge.essentialcraft5.client.guide.RecipeView;
import com.flameforge.essentialcraft5.client.screen.MagmaticFurnaceScreen;
import com.flameforge.essentialcraft5.client.screen.MagmaticSmelteryScreen;
import com.flameforge.essentialcraft5.item.MagmaticAlloyItem;
import com.flameforge.essentialcraft5.recipe.OreSmeltingRecipe;
import com.flameforge.essentialcraft5.registry.ECMagmatic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RecipesUpdatedEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 矿石加工模块的客户端注册（模组总线，Forge 自动发现）：两个界面、岩浆合金第二层染色、研究手册里矿石加工配方的画法。
 * 创造模式物品栏里按配方展开岩浆合金要等配方表到了客户端，见下面 Forge 总线上的 {@link Recipes}。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MagmaticClient {

    private MagmaticClient() {
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ECMagmatic.MAGMATIC_SMELTERY_MENU.get(), MagmaticSmelteryScreen::new);
            MenuScreens.register(ECMagmatic.MAGMATIC_FURNACE_MENU.get(), MagmaticFurnaceScreen::new);
        });
        GuideRecipeViews.register(ECMagmatic.ORE_SMELTING.get(), MagmaticClient::recipeView);
    }

    /**
     * 手册里的一条矿石加工配方：矿石 → 冶炼炉里一块矿最后能出的成品数（掉落数 × 2 × 2），
     * 说明写冶炼炉 / 岩浆熔炉各出几个，有粗矿的再写一行粗矿。整张表（compact）只显示第一行说明。
     */
    private static RecipeView recipeView(OreSmeltingRecipe recipe) {
        ItemStack product = recipe.product();
        int perAlloy = OreSmeltingRecipe.PRODUCTS_PER_ALLOY;
        int smeltery = recipe.yieldOf(false, OreSmeltingRecipe.SMELTERY_MULTIPLIER).alloys() * perAlloy;
        int furnace = recipe.yieldOf(false, OreSmeltingRecipe.FURNACE_MULTIPLIER).alloys() * perAlloy;
        RecipeView.Builder builder = RecipeView.builder()
                .station(ECMagmatic.MAGMATIC_SMELTERY.get())
                .input(recipe.getOre(), 1)
                .output(product.copyWithCount(Math.max(1, Math.min(smeltery, product.getMaxStackSize()))))
                .note(Component.translatable("guide.essentialcraft5.magmatic.recipe.note", smeltery, furnace));
        if (recipe.hasRaw()) {
            OreSmeltingRecipe.Yield raw = recipe.yieldOf(true, OreSmeltingRecipe.SMELTERY_MULTIPLIER);
            OreSmeltingRecipe.Yield rawFurnace = recipe.yieldOf(true, OreSmeltingRecipe.FURNACE_MULTIPLIER);
            builder.note(Component.translatable("guide.essentialcraft5.magmatic.recipe.raw",
                    firstName(recipe.getRaw()),
                    Component.literal(ratio(raw, perAlloy)), Component.literal(ratio(rawFurnace, perAlloy))));
        }
        return builder.build();
    }

    /** 「2」或「1/2」这种：每个粗矿平均出几个成品 */
    private static String ratio(OreSmeltingRecipe.Yield yield, int perAlloy) {
        int products = yield.alloys() * perAlloy;
        return yield.consumed() == 1 ? Integer.toString(products)
                : products % yield.consumed() == 0 ? Integer.toString(products / yield.consumed())
                : products + "/" + yield.consumed();
    }

    private static Component firstName(Ingredient ingredient) {
        ItemStack first = MagmaticAlloyItem.firstReal(ingredient.getItems());
        return first.isEmpty() ? Component.literal("?") : first.getHoverName();
    }

    /** 岩浆合金：0 层原色，1 层按配方颜色（原作 OreSmeltingRecipe.getColorFromItemStack，认不出的配方白色） */
    @SubscribeEvent
    public static void itemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> tintIndex == 1 ? alloyColor(stack) : -1, ECMagmatic.MAGMATIC_ALLOY.get());
    }

    private static int alloyColor(ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return 0xFFFFFF;
        }
        OreSmeltingRecipe recipe = OreSmeltingRecipe.byId(minecraft.level.getRecipeManager(), MagmaticAlloyItem.recipeId(stack));
        return recipe == null ? 0xFFFFFF : recipe.getColor();
    }

    /** Forge 总线：收到配方表后告诉岩浆合金，创造模式物品栏按它展开 */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class Recipes {

        private Recipes() {
        }

        @SubscribeEvent
        public static void recipesUpdated(RecipesUpdatedEvent event) {
            MagmaticAlloyItem.setKnownRecipes(OreSmeltingRecipe.all(event.getRecipeManager()));
        }
    }
}
