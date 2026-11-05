package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.render.MithrilineCrystalPillarRenderer;
import com.flameforge.essentialcraft5.registry.ECCrystalPillars;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 虚空 / 恶魔 / 暗渎水晶柱的客户端注册（模组总线，Forge 自动发现）：柱子里浮着转的水晶用米斯莱柱的渲染器，
 * 换成各自 MTL 的额外模型（{@code models/block/<注册名>_floating.json}，datagen 随方块生成，照 {@code PentacleClient} 的淡青柱）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CrystalPillarsClient {

    public static final ResourceLocation VOID_CRYSTAL_MODEL = floating("void_crystal_pillar");
    public static final ResourceLocation DEMONIC_CRYSTAL_MODEL = floating("demonic_crystal_pillar");
    public static final ResourceLocation SHADE_CRYSTAL_MODEL = floating("shade_crystal_pillar");

    private CrystalPillarsClient() {
    }

    private static ResourceLocation floating(String registryName) {
        return EssentialCraft5.id("block/" + ECCrystalPillars.floatingModelName(registryName));
    }

    @SubscribeEvent
    public static void crystalPillarRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ECCrystalPillars.VOID_CRYSTAL_PILLAR_BE.get(),
                context -> new MithrilineCrystalPillarRenderer(context, VOID_CRYSTAL_MODEL));
        event.registerBlockEntityRenderer(ECCrystalPillars.DEMONIC_CRYSTAL_PILLAR_BE.get(),
                context -> new MithrilineCrystalPillarRenderer(context, DEMONIC_CRYSTAL_MODEL));
        event.registerBlockEntityRenderer(ECCrystalPillars.SHADE_CRYSTAL_PILLAR_BE.get(),
                context -> new MithrilineCrystalPillarRenderer(context, SHADE_CRYSTAL_MODEL));
    }

    /** BER 画的 OBJ 水晶不属于任何方块状态，要单独登记才会被烘焙 */
    @SubscribeEvent
    public static void crystalPillarModels(ModelEvent.RegisterAdditional event) {
        event.register(VOID_CRYSTAL_MODEL);
        event.register(DEMONIC_CRYSTAL_MODEL);
        event.register(SHADE_CRYSTAL_MODEL);
    }
}
