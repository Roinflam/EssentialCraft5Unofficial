package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.screen.ChargingChamberScreen;
import com.flameforge.essentialcraft5.client.screen.MoonWellScreen;
import com.flameforge.essentialcraft5.item.BoundGemItem;
import com.flameforge.essentialcraft5.item.MRUStorageItem;
import com.flameforge.essentialcraft5.registry.ECItems;
import com.flameforge.essentialcraft5.registry.ECMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** 客户端初始化（模组总线）。 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ECClient {

    private ECClient() {
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ECMenus.MOON_WELL.get(), MoonWellScreen::new);
            MenuScreens.register(ECMenus.CHARGING_CHAMBER.get(), ChargingChamberScreen::new);
            ItemProperties.register(ECItems.BOUND_GEM.get(), BoundGemItem.BOUND_PREDICATE,
                    (stack, level, entity, seed) -> BoundGemItem.isBound(stack) ? 1F : 0F);
        });
    }

    @SubscribeEvent
    public static void itemColors(RegisterColorHandlersEvent.Item event) {
        Item[] storages = {
                ECItems.SOUL_SHARD.get(), ECItems.SOUL_SPHERE.get(), ECItems.DARK_SOUL_MATTER.get(),
                ECItems.RED_SOUL_MATTER.get(), ECItems.MATTER_OF_ETERNITY.get()
        };
        event.register((stack, tintIndex) -> tintIndex == 0 ? MRUStorageItem.fillColor(stack) : 0xFFFFFF, storages);
    }
}
