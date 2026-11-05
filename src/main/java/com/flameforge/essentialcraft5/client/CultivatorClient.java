package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.MagicalCultivatorBlockEntity;
import com.flameforge.essentialcraft5.client.guide.Figures;
import com.flameforge.essentialcraft5.client.guide.GuideRecipeViews;
import com.flameforge.essentialcraft5.client.guide.RecipeView;
import com.flameforge.essentialcraft5.client.render.MagicalCultivatorRenderer;
import com.flameforge.essentialcraft5.client.screen.MagicalCultivatorScreen;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.recipe.CultivatorRecipe;
import com.flameforge.essentialcraft5.registry.ECCultivator;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 魔力栽培器的客户端注册（模组总线）：界面、作物渲染（BER），以及配方类型 {@code essentialcraft5:cultivator} 在研究手册里的画法
 * （样本 × 株数上限 → 每种产物，下面三行说明：每轮时长 · 每株 MRU · 株数上限；每株产物的数量和几率；要不要水、能不能用骨粉）。
 * JEI 的栽培器分类用同一份说明文字（{@link #notes}）。机器方块的渲染层（cutout）写在模型 JSON 里。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CultivatorClient {

    private static final String KEY = "gui.essentialcraft5.magical_cultivator.recipe.";

    private CultivatorClient() {
    }

    @SubscribeEvent
    public static void cultivatorClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ECCultivator.MAGICAL_CULTIVATOR_MENU.get(), MagicalCultivatorScreen::new));
        GuideRecipeViews.register(ECCultivator.CULTIVATOR.get(), CultivatorClient::view);
    }

    @SubscribeEvent
    public static void cultivatorRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ECCultivator.MAGICAL_CULTIVATOR_BE.get(), MagicalCultivatorRenderer::new);
    }

    /** 手册配方图：样本画成「株数上限」个，产物画成每株的上限个数 */
    public static RecipeView view(CultivatorRecipe recipe) {
        RecipeView.Builder builder = RecipeView.builder()
                .station(ECCultivator.MAGICAL_CULTIVATOR.get())
                .input(recipe.input(), recipe.maxPlants());
        for (CultivatorRecipe.Output output : recipe.outputs()) {
            builder.output(output.display());
        }
        for (Component note : notes(recipe)) {
            builder.note(note);
        }
        return builder.build();
    }

    /** 三行说明（第一行也是 compact 转化表里那一行） */
    public static List<Component> notes(CultivatorRecipe recipe) {
        List<Component> lines = new ArrayList<>();
        // 时长和每株 MRU 乘上全局倍率，和机器真正用的数一致（通用路线的说明页同样）
        lines.add(round(recipe.ticks(), recipe.mruPerPlant(), recipe.maxPlants()));
        if (!recipe.outputs().isEmpty()) {
            MutableComponent pieces = Component.empty();
            boolean first = true;
            for (CultivatorRecipe.Output output : recipe.outputs()) {
                if (!first) {
                    pieces.append(Component.translatable(KEY + "sep"));
                }
                first = false;
                pieces.append(piece(output));
            }
            lines.add(Component.translatable(KEY + "per_plant", pieces));
        }
        lines.add(Component.translatable(KEY + (recipe.needsWater() ? "water" : "no_water"))
                .append(" · ")
                .append(Component.translatable(KEY + (recipe.bonemeal() ? "bonemeal" : "no_bonemeal"))));
        return lines;
    }

    /** 「每轮 2 分钟 · 每株 800 MRU · 最多 16 株」：时长 × time_multiplier、每株 MRU × mru_multiplier */
    public static Component round(int ticks, int mruPerPlant, int maxPlants) {
        int shown = MagicalCultivatorBlockEntity.roundTicks(ticks, ECConfig.get(MagicalCultivatorBlockEntity.Config.timeMultiplier), false, 1.0D);
        long mru = MagicalCultivatorBlockEntity.roundCost(mruPerPlant, 1, ECConfig.get(MagicalCultivatorBlockEntity.Config.mruMultiplier));
        return Component.translatable(KEY + "round", duration(shown), Figures.whole(mru), maxPlants);
    }

    /** 一种产物：「小麦 ×1」「胡萝卜 ×1～2」「小麦种子 ×1（25%）」 */
    public static Component piece(CultivatorRecipe.Output output) {
        Component name = output.item().getDescription();
        Component amount = output.min() == output.max()
                ? Component.literal(Integer.toString(output.max()))
                : Component.translatable(KEY + "range", output.min(), output.max());
        if (output.chance() < 1F) {
            return Component.translatable(KEY + "piece_chance", name, amount, Figures.number(output.chance() * 100F) + "%");
        }
        return Component.translatable(KEY + "piece", name, amount);
    }

    /** 时长：整分钟写「N 分钟」，否则写「N 秒」 */
    public static Component duration(int ticks) {
        if (ticks >= 1200 && ticks % 1200 == 0) {
            return Component.translatable("gui.essentialcraft5.magical_cultivator.minutes", ticks / 1200);
        }
        return Component.translatable("gui.essentialcraft5.magical_cultivator.seconds", Figures.number(ticks / 20F));
    }
}
