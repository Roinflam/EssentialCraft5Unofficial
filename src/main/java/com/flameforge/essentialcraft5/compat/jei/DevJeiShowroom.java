package com.flameforge.essentialcraft5.compat.jei;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.dev.DevShowroom;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.List;
import java.util.function.Consumer;

/**
 * 展示厅的 JEI 模式（-PecShowroomJei）：等 {@link DevShowroom} 进了世界，逐个打开本模组的 JEI 分类，每个拍一张
 * （run/screenshots/ec5_06_jei_*.png），最后拍一张「锋利 XII 怎么来」（看增幅台那页左右两本书是不是联动到同一种附魔），拍完交回展示厅退出。
 * 只在开了这个模式时由 {@link EC5JeiPlugin#onRuntimeAvailable} 挂上，平时不注册任何监听。
 */
final class DevJeiShowroom {

    /** 打开一页以后等这么多刻再拍（JEI 的页面第一帧要排版、物品图标要加载） */
    private static final int SETTLE = 30;

    private record Shot(String name, Consumer<IJeiRuntime> open) {
    }

    private static boolean started;
    private static IJeiRuntime runtime;
    private static int index = -1;
    private static int timer;

    private DevJeiShowroom() {
    }

    static void start(IJeiRuntime jei) {
        runtime = jei;
        if (started) {
            return;
        }
        started = true;
        MinecraftForge.EVENT_BUS.addListener(DevJeiShowroom::tick);
        EssentialCraft5.LOGGER.info("[showroom] JEI 已就绪，等进世界后开拍");
    }

    private static List<Shot> shots() {
        return List.of(
                type("magician_table", EC5JeiPlugin.MAGICIAN_TABLE),
                type("radiating_chamber", EC5JeiPlugin.RADIATING_CHAMBER),
                type("mithriline_furnace", EC5JeiPlugin.MITHRILINE_FURNACE),
                type("ore_smelting", EC5JeiPlugin.ORE_SMELTING),
                type("wind_imbue", EC5JeiPlugin.WIND_IMBUE),
                type("enchant_amplifier", EC5JeiPlugin.ENCHANT_AMPLIFIER),
                type("enchant_ascender", EC5JeiPlugin.ENCHANT_ASCENDER),
                type("cultivator", EC5JeiPlugin.CULTIVATOR),
                type("elemental_decomposer", EC5JeiPlugin.DECOMPOSER),
                new Shot("sharpness_12", jei -> jei.getRecipesGui().show(jei.getJeiHelpers().getFocusFactory().createFocus(
                        RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK,
                        EnchantedBookItem.createForEnchantment(new EnchantmentInstance(Enchantments.SHARPNESS, 12))))));
    }

    private static Shot type(String name, RecipeType<?> type) {
        return new Shot(name, jei -> jei.getRecipesGui().showTypes(List.of(type)));
    }

    private static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !DevShowroom.jeiStageReady()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        List<Shot> shots = shots();
        timer++;
        if (index < 0) {
            index = 0;
            timer = 0;
            open(shots.get(0));
            return;
        }
        if (timer < SETTLE) {
            return;
        }
        DevShowroom.shotNamed(mc, "06_jei_" + shots.get(index).name());
        index++;
        timer = 0;
        if (index >= shots.size()) {
            mc.setScreen(null);
            EssentialCraft5.LOGGER.info("[showroom] JEI 拍完 {} 张", shots.size());
            DevShowroom.jeiFinished();
            return;
        }
        open(shots.get(index));
    }

    private static void open(Shot shot) {
        try {
            shot.open().accept(runtime);
        } catch (RuntimeException e) {
            EssentialCraft5.LOGGER.warn("[showroom] JEI 打开 {} 出错：{}", shot.name(), e.toString());
        }
    }
}
