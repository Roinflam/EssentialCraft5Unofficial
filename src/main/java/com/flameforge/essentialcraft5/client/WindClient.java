package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.guide.GuideRecipeViews;
import com.flameforge.essentialcraft5.client.guide.RecipeView;
import com.flameforge.essentialcraft5.client.render.WindRuneRenderer;
import com.flameforge.essentialcraft5.espe.ESPEStorage;
import com.flameforge.essentialcraft5.recipe.WindImbueRecipe;
import com.flameforge.essentialcraft5.registry.ECWind;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.jetbrains.annotations.Nullable;

/**
 * 风模块的客户端注册（模组总线，Forge 自动发现）：风之符文的 BER（地上的符文图案）、研究手册里注风配方的画法
 * （风之符文 · 原料 → 产物，下面写「N ESPE」，眷顾那条另写一行「授予风之眷顾（原料不消耗）」，要先受眷顾的（风之号角）另写一行「要已受风之眷顾」）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WindClient {

    private WindClient() {
    }

    @SubscribeEvent
    public static void windClientSetup(FMLClientSetupEvent event) {
        GuideRecipeViews.register(ECWind.WIND_IMBUE.get(), (WindImbueRecipe recipe) -> {
            RecipeView.Builder view = RecipeView.builder()
                    .station(ECWind.WIND_RUNE.get())
                    .input(recipe.getInput())
                    .output(recipe.getResultItem(GuideRecipeViews.registryAccess()))
                    .note(Component.translatable("gui.essentialcraft5.espe", ESPEStorage.format(recipe.getEspe())));
            if (recipe.grantsWindbound()) {
                view.note(Component.translatable("gui.essentialcraft5.wind_imbue.windbound"));
            }
            if (recipe.requiresWindbound()) {
                view.note(Component.translatable("gui.essentialcraft5.wind_imbue.requires_windbound"));
            }
            return view.build();
        });
    }

    @SubscribeEvent
    public static void windRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ECWind.WIND_RUNE_BE.get(), WindRuneRenderer::new);
    }

    /** 物品提示里读本人的风之好感（WindTabletItem 经 DistExecutor 调，服务端不会加载这个类） */
    @Nullable
    public static Player localPlayer() {
        return Minecraft.getInstance().player;
    }
}
