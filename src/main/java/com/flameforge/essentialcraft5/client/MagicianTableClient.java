package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.MagicianTableUpgrades;
import com.flameforge.essentialcraft5.client.render.MagicianTableRenderer;
import com.flameforge.essentialcraft5.client.screen.MagicianTableScreen;
import com.flameforge.essentialcraft5.registry.ECMagicianTable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** 魔力工作台的客户端注册（模组总线）：界面、升级方块渲染。 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MagicianTableClient {

    private MagicianTableClient() {
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ECMagicianTable.MAGICIAN_TABLE_MENU.get(), MagicianTableScreen::new));
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ECMagicianTable.MAGICIAN_TABLE_BE.get(), MagicianTableRenderer::new);
    }

    /**
     * 游戏总线：升级物品的提示里加一行「魔力工作台升级：速度 ×N」。
     * 原作没有这行（只在研究书里讲），本项目不做 JEI，不写出来玩家很难知道哪些物品能当升级。
     */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class UpgradeTooltips {

        private UpgradeTooltips() {
        }

        @SubscribeEvent
        public static void onTooltip(ItemTooltipEvent event) {
            MagicianTableUpgrades.Upgrade upgrade = MagicianTableUpgrades.get(event.getItemStack());
            if (upgrade != null) {
                event.getToolTip().add(Component.translatable("tooltip.essentialcraft5.magician_table_upgrade",
                        MagicianTableUpgrades.format(upgrade.efficiency())).withStyle(ChatFormatting.GRAY));
            }
        }
    }
}
