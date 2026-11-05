package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.render.MithrilineCrystalPillarRenderer;
import com.flameforge.essentialcraft5.client.render.PlayerPentacleRenderer;
import com.flameforge.essentialcraft5.client.screen.PlayerPentacleScreen;
import com.flameforge.essentialcraft5.registry.ECPentacle;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 灵魂五芒星模块的客户端注册（模组总线，Forge 自动发现）：五芒星界面、地上转动的符文（BER）、
 * 淡青水晶柱里浮着转的水晶（米斯莱柱的渲染器，换成淡青贴图的额外模型 {@link #PALE_CRYSTAL_MODEL}）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PentacleClient {

    /** 淡青柱的浮动水晶：米斯莱柱的 OBJ + 淡青 MTL（models/block/pale_crystal_pillar_floating.json，datagen 随方块生成） */
    public static final ResourceLocation PALE_CRYSTAL_MODEL = EssentialCraft5.id("block/pale_crystal_pillar_floating");

    private PentacleClient() {
    }

    @SubscribeEvent
    public static void pentacleClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ECPentacle.PLAYER_PENTACLE_MENU.get(), PlayerPentacleScreen::new));
    }

    @SubscribeEvent
    public static void pentacleRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ECPentacle.PLAYER_PENTACLE_BE.get(), PlayerPentacleRenderer::new);
        event.registerBlockEntityRenderer(ECPentacle.PALE_CRYSTAL_PILLAR_BE.get(),
                context -> new MithrilineCrystalPillarRenderer(context, PALE_CRYSTAL_MODEL));
    }

    /** BER 画的 OBJ 水晶不属于任何方块状态，要单独登记才会被烘焙 */
    @SubscribeEvent
    public static void pentacleModels(ModelEvent.RegisterAdditional event) {
        event.register(PALE_CRYSTAL_MODEL);
    }
}
