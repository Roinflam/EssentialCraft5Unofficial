package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.render.InquisitoriumSentinelRenderer;
import com.flameforge.essentialcraft5.item.InquisitoriumCoordinatesItem;
import com.flameforge.essentialcraft5.registry.ECInquisitorium;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * 审判庭一章的客户端（第十批 10c-M1，batch10.md §5.10）：审判庭哨兵的渲染器（模组总线），审判庭坐标的方向提示（Forge 总线，{@link InquisitoriumClientEvents}）。
 * 哨兵的刷怪蛋颜色由 Forge 的 ForgeSpawnEggItem 自动注册；Boss 战音乐由全息投影的实体 tick 照常放（哨兵继承了它）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class InquisitoriumClient {

    /** 方向提示多久刷新一次（刻；动作栏一条显示 3 秒，10 刻刷一次就一直亮着） */
    public static final int HEADING_INTERVAL = 10;

    private InquisitoriumClient() {
    }

    @SubscribeEvent
    public static void inquisitoriumRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ECInquisitorium.INQUISITORIUM_SENTINEL.get(), InquisitoriumSentinelRenderer::new);
    }

    /**
     * 主手或副手拿着审判庭坐标时动作栏上那一句（客户端按 NBT 自己算，不发包）：人在坐标所在的维度时写八方位和水平距离，
     * 到了 {@code arrive_radius} 以内写「就在这附近」；不在那个维度时写坐标在哪个维度。没拿着返回 null。
     */
    @Nullable
    public static Component headingLine(LocalPlayer player) {
        ItemStack held = player.getMainHandItem().getItem() instanceof InquisitoriumCoordinatesItem ? player.getMainHandItem()
                : player.getOffhandItem().getItem() instanceof InquisitoriumCoordinatesItem ? player.getOffhandItem() : ItemStack.EMPTY;
        InquisitoriumCoordinatesItem.Target target = held.isEmpty() ? null : InquisitoriumCoordinatesItem.target(held);
        if (target == null) {
            return null;
        }
        if (!target.isIn(player.level())) {
            return Component.translatable("message.essentialcraft5.inquisitorium_coordinates.elsewhere",
                    InquisitoriumCoordinatesItem.where(target)).withStyle(ChatFormatting.LIGHT_PURPLE);
        }
        double distance = target.horizontalDistance(player.getX(), player.getZ());
        if (distance <= ECInquisitorium.Rules.values().arriveRadius()) {
            return Component.translatable("message.essentialcraft5.inquisitorium_coordinates.here").withStyle(ChatFormatting.LIGHT_PURPLE);
        }
        int heading = InquisitoriumCoordinatesItem.heading(target.x() + 0.5D - player.getX(), target.z() + 0.5D - player.getZ());
        return Component.translatable("message.essentialcraft5.inquisitorium_coordinates.heading",
                Component.translatable(InquisitoriumCoordinatesItem.headingKey(heading)), Mth.floor(distance)).withStyle(ChatFormatting.LIGHT_PURPLE);
    }

    /** Forge 总线上的客户端事件 */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
    public static final class InquisitoriumClientEvents {

        private InquisitoriumClientEvents() {
        }

        @SubscribeEvent
        public static void onInquisitoriumClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            LocalPlayer player = minecraft.player;
            if (player == null || minecraft.isPaused() || player.tickCount % HEADING_INTERVAL != 0) {
                return;
            }
            Component line = headingLine(player);
            if (line != null) {
                minecraft.gui.setOverlayMessage(line, false);
            }
        }
    }
}
