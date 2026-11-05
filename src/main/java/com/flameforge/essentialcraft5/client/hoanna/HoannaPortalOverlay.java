package com.flameforge.essentialcraft5.client.hoanna;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.hoanna.portal.HoannaPortalTracker;
import com.flameforge.essentialcraft5.hoanna.portal.PortalOverlayCurve;
import com.flameforge.essentialcraft5.registry.ECHoannaPortal;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import javax.annotation.Nullable;

/**
 * 进海纳尔之门时屏幕边缘的绿色暗角（第六批 6a M2，batch6a.md §3.4「进门画面」、§5.8 裁定 26）：
 * 站在门里按等待进度渐显，最浓 0.40 ×「屏幕扭曲效果」（曲线在 common 的 {@link PortalOverlayCurve}），离开后 0.5 秒淡出；
 * <b>不旋转、不反胃、不整屏铺色</b>（原版下界门的紫色旋转叠层不会出现：海纳尔之门不调 {@code handleInsidePortal}）。
 * 开始等待时放一次 {@code block.portal.trigger}（音量 0.25，同原版 {@code LocalPlayer.handleNetherPortalClient}）。
 * <p>
 * 客户端自己计时、不发包：门方块的 {@code entityInside} 在客户端也会对本地玩家触发（{@link #inside}），这里每个客户端刻结算一次；
 * 等待时间、开关读服务端配置（Forge 进服时同步给客户端）。服务端「站在门里冷却不减」的规则这里照做一份（客户端的冷却由重生包带过来、本地每刻减），
 * 所以到了对面站在门里不会看到绿色；服务端拒绝传送（建门全被拦下等）时，站满等待时间再过 2 秒就淡掉。
 * HUD 层：{@code registerAbove(VanillaGuiOverlay.PORTAL, "hoanna_portal")}，画在 z = −90（同原版暗角、南瓜头）。
 * 展示厅用 {@link #setShowroomStrength}（{@code 05_hoanna_portal_overlay_half}：0.5）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class HoannaPortalOverlay {

    /** 暗角颜色：门贴图的绿 #00C02A */
    private static final float COLOR_R = 0x00 / 255F;
    private static final float COLOR_G = 0xC0 / 255F;
    private static final float COLOR_B = 0x2A / 255F;
    /** HUD 覆盖层的深度（原版暗角、霜冻覆盖层都画在 −90） */
    private static final float OVERLAY_Z = -90F;

    /** 客户端刻数（暂停时不走） */
    private static int ticks;
    /** 最后一次站在门里的客户端刻 */
    private static int lastInside = Integer.MIN_VALUE / 2;
    /** 已经等了几刻（同服务端的计数：在门里 +1，离开每刻 −4） */
    private static int insideTicks;
    /** 站在门里时按住的冷却（0 = 没在按） */
    private static int heldCooldown;
    /** 这一刻、上一刻的强度（0~1，渲染时按 partialTick 插值） */
    private static float strength;
    private static float previousStrength;
    @Nullable
    private static Float showroomStrength;

    private HoannaPortalOverlay() {
    }

    /** 门方块的 entityInside 在客户端调（经 DistExecutor）：只记本地玩家 */
    public static void inside(Entity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (entity == minecraft.player) {
            lastInside = ticks;
        }
    }

    /** 展示厅：固定强度（0~1；null 恢复正常） */
    public static void setShowroomStrength(@Nullable Float value) {
        showroomStrength = value;
    }

    @SubscribeEvent
    public static void onHoannaPortalClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            reset();
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }
        previousStrength = strength;
        // 开关同服务端：关掉时只有主世界这边不计时，在海纳尔照样有暗角（回程照常）
        boolean in = lastInside == ticks && !player.isPassenger() && !player.isVehicle() && ECHoannaPortal.Config.portalEnabledIn(minecraft.level);
        if (in && player.isOnPortalCooldown()) {
            // 同服务端：站在门里冷却不减、不低于 20，不计时
            if (heldCooldown == 0) {
                heldCooldown = Math.max(player.getPortalCooldown(), HoannaPortalTracker.MIN_HELD_COOLDOWN);
            }
            player.setPortalCooldown(heldCooldown);
            in = false;
        } else if (!in) {
            heldCooldown = 0;
        }
        int wait = HoannaPortalTracker.waitTicks(player);
        float target = 0F;
        if (in && insideTicks < wait + PortalOverlayCurve.STALL_TICKS) {
            if (insideTicks == 0 && strength <= 0F) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forLocalAmbience(SoundEvents.PORTAL_TRIGGER,
                        player.getRandom().nextFloat() * 0.4F + 0.8F, 0.25F));
            }
            insideTicks++;
            target = PortalOverlayCurve.progress(insideTicks, wait);
        } else if (!in) {
            insideTicks = Math.max(0, insideTicks - HoannaPortalTracker.DECAY_PER_TICK);
        }
        strength = PortalOverlayCurve.step(strength, target, wait);
        ticks++;
    }

    @SubscribeEvent
    public static void onHoannaPortalLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    private static void reset() {
        insideTicks = 0;
        heldCooldown = 0;
        strength = 0F;
        previousStrength = 0F;
    }

    /** HUD 层：屏幕四边往里 {@code 短边 × BAND} 的梯形带，外沿不透明度 alpha、内沿 0，中间一大片不碰 */
    static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft minecraft = gui.getMinecraft();
        float value = showroomStrength != null ? showroomStrength : Mth.lerp(partialTick, previousStrength, strength);
        float alpha = PortalOverlayCurve.alpha(value, minecraft.options.screenEffectScale().get());
        if (alpha <= 0.004F) {
            return;
        }
        gui.setupOverlayRenderState(true, false);
        float t = Math.min(width, height) * PortalOverlayCurve.BAND;
        float w = width;
        float h = height;
        Matrix4f pose = graphics.pose().last().pose();
        VertexConsumer buffer = graphics.bufferSource().getBuffer(RenderType.gui());
        // 四条梯形带，在对角线上接缝；顶点顺序和 GuiGraphics.fill 同一个绕向（同 MatrixHitEffects.renderVignette）
        band(buffer, pose, 0F, 0F, alpha, t, t, 0F, w - t, t, 0F, w, 0F, alpha);
        band(buffer, pose, 0F, 0F, alpha, 0F, h, alpha, t, h - t, 0F, t, t, 0F);
        band(buffer, pose, t, h - t, 0F, 0F, h, alpha, w, h, alpha, w - t, h - t, 0F);
        band(buffer, pose, w - t, t, 0F, w - t, h - t, 0F, w, h, alpha, w, 0F, alpha);
        graphics.flush();
    }

    private static void band(VertexConsumer buffer, Matrix4f pose, float x0, float y0, float a0, float x1, float y1, float a1,
                             float x2, float y2, float a2, float x3, float y3, float a3) {
        buffer.vertex(pose, x0, y0, OVERLAY_Z).color(COLOR_R, COLOR_G, COLOR_B, a0).endVertex();
        buffer.vertex(pose, x1, y1, OVERLAY_Z).color(COLOR_R, COLOR_G, COLOR_B, a1).endVertex();
        buffer.vertex(pose, x2, y2, OVERLAY_Z).color(COLOR_R, COLOR_G, COLOR_B, a2).endVertex();
        buffer.vertex(pose, x3, y3, OVERLAY_Z).color(COLOR_R, COLOR_G, COLOR_B, a3).endVertex();
    }

    /** 模组总线：登记 HUD 层（排在原版下界门叠层之后） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class HoannaPortalOverlayRegistration {

        private HoannaPortalOverlayRegistration() {
        }

        @SubscribeEvent
        public static void registerHoannaPortalOverlay(RegisterGuiOverlaysEvent event) {
            event.registerAbove(VanillaGuiOverlay.PORTAL.id(), "hoanna_portal", HoannaPortalOverlay::render);
        }
    }
}
