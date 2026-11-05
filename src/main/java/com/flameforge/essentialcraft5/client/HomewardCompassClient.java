package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.item.HomewardCompassItem;
import com.flameforge.essentialcraft5.registry.ECHomewardCompass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.jetbrains.annotations.Nullable;

/**
 * 归途罗盘的客户端部分（模组总线，batch12.md §4.5.3）：物品属性 {@link HomewardCompassItem#BOUND_PREDICATE}（绑定了是 1，模型换成表盘发光、指针定住的那张），
 * 以及提示框算距离用的本地玩家位置。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HomewardCompassClient {

    private HomewardCompassClient() {
    }

    @SubscribeEvent
    public static void homewardClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(ECHomewardCompass.HOMEWARD_COMPASS.get(), HomewardCompassItem.BOUND_PREDICATE,
                (stack, level, entity, seed) -> HomewardCompassItem.isBound(stack) ? 1F : 0F));
    }

    /** 本地玩家脚下那格（没进世界时 null）；提示框按看的人自己的位置算距离和价格 */
    @Nullable
    public static BlockPos viewerPosition() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? null : player.blockPosition();
    }
}
