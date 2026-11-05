package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.screen.WeaponMakerScreen;
import com.flameforge.essentialcraft5.registry.ECWeaponMaker;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** 武器工作台的客户端注册（模组总线）：界面。 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WeaponMakerClient {

    private WeaponMakerClient() {
    }

    @SubscribeEvent
    public static void weaponMakerClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ECWeaponMaker.WEAPON_MAKER_MENU.get(), WeaponMakerScreen::new));
    }
}
