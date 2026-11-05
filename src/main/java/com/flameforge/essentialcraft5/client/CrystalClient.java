package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.render.CrystalFormerRenderer;
import com.flameforge.essentialcraft5.client.render.ElementalCrystalRenderer;
import com.flameforge.essentialcraft5.client.screen.CrystalFormerScreen;
import com.flameforge.essentialcraft5.item.ElementalCrystalItem;
import com.flameforge.essentialcraft5.registry.ECCrystal;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 元素水晶模块的客户端注册（模组总线，Forge 自动发现）：成型器界面、两个 BER、水晶物品的颜色和大小图标。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CrystalClient {

    private CrystalClient() {
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ECCrystal.CRYSTAL_FORMER_MENU.get(), CrystalFormerScreen::new);
            // 属性值 = 档位 / 20，物品模型的 override 按 ElementalCrystalItem.stageThreshold(1~20) 写
            ItemProperties.register(ECCrystal.ELEMENTAL_CRYSTAL.get().asItem(), ElementalCrystalItem.SIZE_PREDICATE,
                    (ClampedItemPropertyFunction) (stack, level, entity, seed) -> ElementalCrystalItem.sizeProperty(stack));
        });
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ECCrystal.ELEMENTAL_CRYSTAL_BE.get(), ElementalCrystalRenderer::new);
        event.registerBlockEntityRenderer(ECCrystal.CRYSTAL_FORMER_BE.get(), CrystalFormerRenderer::new);
    }

    /** 原作 ItemBlockElementalCrystal 的 IItemColor：按四元素比例染色（通道顺序已修正） */
    @SubscribeEvent
    public static void itemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> tintIndex == 0 ? ElementalCrystalItem.color(stack) : -1, ECCrystal.ELEMENTAL_CRYSTAL.get());
    }
}
