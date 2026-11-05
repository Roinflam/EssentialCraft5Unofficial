package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.guide.GuideRecipeViews;
import com.flameforge.essentialcraft5.client.guide.RecipeView;
import com.flameforge.essentialcraft5.client.render.MithrilineCrystalPillarRenderer;
import com.flameforge.essentialcraft5.client.screen.MithrilineFurnaceScreen;
import com.flameforge.essentialcraft5.espe.ESPEStorage;
import com.flameforge.essentialcraft5.recipe.MithrilineFurnaceRecipe;
import com.flameforge.essentialcraft5.registry.ECMithriline;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 米斯莱模块的客户端注册（模组总线，Forge 自动发现）：熔炉界面、水晶柱 BER 和它要的额外 OBJ 模型、
 * 研究手册里米斯莱熔炉配方的画法（原料 × 数量 → 产物，下面写「60 ESPE」）。
 * 米斯莱护板的方块 / 物品染色由框架的 MaterialsClient 按 DyeableBlock 自动注册，这里不用管。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MithrilineClient {

    private MithrilineClient() {
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ECMithriline.MITHRILINE_FURNACE_MENU.get(), MithrilineFurnaceScreen::new));
        GuideRecipeViews.register(ECMithriline.MITHRILINE_FURNACE_TYPE.get(), (MithrilineFurnaceRecipe recipe) -> RecipeView.builder()
                .station(ECMithriline.MITHRILINE_FURNACE.get())
                .input(recipe.getInput(), recipe.getCount())
                .output(recipe.getResultItem(GuideRecipeViews.registryAccess()))
                .note(Component.translatable("gui.essentialcraft5.espe", ESPEStorage.format(recipe.getEspe())))
                .build());
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ECMithriline.MITHRILINE_CRYSTAL_PILLAR_BE.get(), MithrilineCrystalPillarRenderer::new);
    }

    /** 水晶柱 BER 画的 OBJ 水晶不属于任何方块状态，要单独登记才会被烘焙 */
    @SubscribeEvent
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(MithrilineCrystalPillarRenderer.MODEL);
    }
}
