package com.flameforge.essentialcraft5.client.guide;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RecipesUpdatedEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 研究手册的游戏内事件（Forge 总线）：查阅键、配方同步后的检查。
 * 查阅键默认不绑定（见 GuideClient#LOOKUP），这里的两条路径只有玩家自己绑了键才会走到；
 * 日常查阅是界面里按住 Ctrl（{@link GuideLookup}）和手持手册潜行右键（GuideClient#useBook）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class GuideEvents {

    private GuideEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        boolean pressed = false;
        while (GuideClient.LOOKUP.consumeClick()) {
            pressed = true;
        }
        // 开着界面时的查阅走 onScreenKey；这里只管游戏画面，积压的点击照样清掉，免得关界面后突然触发
        if (pressed && minecraft.screen == null && minecraft.player != null) {
            GuideClient.lookupInWorld(minecraft);
        }
    }

    /**
     * 物品栏类界面里指着物品按查阅键：身上带着研究手册、且这个物品有条目时，打开指南，关掉后回到原界面。
     * 用 Post：界面自己处理了这个键（比如正在输入框里打字）就不会走到这里。
     */
    @SubscribeEvent
    public static void onScreenKey(ScreenEvent.KeyPressed.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) {
            return;
        }
        if (!GuideClient.LOOKUP.isActiveAndMatches(InputConstants.getKey(event.getKeyCode(), event.getScanCode()))) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !GuideClient.hasBook(minecraft.player)) {
            return;
        }
        Slot slot = screen.getSlotUnderMouse();
        if (slot != null && slot.hasItem() && GuideClient.lookup(slot.getItem(), screen)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRecipesUpdated(RecipesUpdatedEvent event) {
        RecipeFinder.invalidate();
        GuideChecks.onRecipesUpdated(event.getRecipeManager());
    }
}
