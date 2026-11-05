package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.registry.ECHoannaNature;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.world.level.GrassColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 海纳尔植物的客户端注册（第六批 6a M1，模组总线）：草丛按草地颜色染色（原作 BlockHoannaTallGrass L62~64）。
 * 模型是 {@code minecraft:block/tinted_cross}（面带 tintindex 0），物品是 item/generated 的 layer0（同样 tintindex 0）。
 * 海纳尔的草地颜色写在群系 JSON（{@code tools/gen/hoanna_biomes.py} 的 PALETTE），这里只取当地的混合色；渲染层写在模型里（cutout），不在这里设。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HoannaNatureClient {

    private HoannaNatureClient() {
    }

    @SubscribeEvent
    public static void hoannaNatureBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex != 0) {
                return -1;
            }
            return level != null && pos != null ? BiomeColors.getAverageGrassColor(level, pos) : GrassColor.getDefaultColor();
        }, ECHoannaNature.HOANNA_TALL_GRASS.get());
    }

    @SubscribeEvent
    public static void hoannaNatureItemColors(RegisterColorHandlersEvent.Item event) {
        // 同原版矮草的物品色（ItemColors：GrassColor.get(0.5, 1.0)）
        event.register((stack, tintIndex) -> tintIndex == 0 ? GrassColor.get(0.5D, 1.0D) : -1, ECHoannaNature.HOANNA_TALL_GRASS.get());
    }
}
