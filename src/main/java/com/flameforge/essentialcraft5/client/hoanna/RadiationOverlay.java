package com.flameforge.essentialcraft5.client.hoanna;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.hoanna.HoannaVisuals;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import javax.annotation.Nullable;

/**
 * 辐射晕影（第六批 6a M3，batch6a.md §3.4；原作没有，已定新增）：本地玩家的海纳尔辐射（已同步的玩家数据 {@code PlayerData.radiation}）
 * 到 1 000 起，屏幕四周泛起一圈暗紫（{@link HoannaVisuals#RADIATION_VIGNETTE_COLOR} #6B4A7E，EC 自己的紫，不用核绿），告诉玩家「在累积」。
 * <ul>
 *   <li>浓度 {@link HoannaVisuals#vignetteAlpha}：1 000 起 0.05，1 万约 0.10，3 万封顶 0.20；每刻往目标平滑 0.1（辐射每秒才变一次，画面不跳）。</li>
 *   <li>从屏幕边缘往里 {@code 短边 × 0.22} 线性淡到全透明，中间一大片不碰；不闪、不呼吸；不限在海纳尔（离开后辐射慢慢消退，晕影跟着淡）。</li>
 *   <li>HUD 层 {@code essentialcraft5:hoanna_radiation}，排在原版霜冻覆盖层之上（{@link HoannaClient}），按 F1 时和其它 HUD 一起隐藏。</li>
 * </ul>
 * 展示厅：{@link #setShowroomRadiation}（直接到位、不平滑）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class RadiationOverlay {

    /** HUD 覆盖层的深度（原版暗角、霜冻覆盖层都画在 −90；RenderType.gui() 会写深度，画在 0 会挡住之后画在 −90 的快捷栏） */
    private static final float OVERLAY_Z = -90F;

    private static float alphaPrev;
    private static float alphaNow;
    @Nullable
    private static Integer showroomRadiation;

    private RadiationOverlay() {
    }

    /** 展示厅：按这个剂量显示晕影（null = 按玩家数据） */
    public static void setShowroomRadiation(@Nullable Integer radiation) {
        showroomRadiation = radiation;
        if (radiation != null) {
            alphaPrev = alphaNow = HoannaVisuals.vignetteAlpha(radiation);
        }
    }

    @SubscribeEvent
    public static void onRadiationOverlayClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isPaused()) {
            return;
        }
        LocalPlayer player = minecraft.player;
        alphaPrev = alphaNow;
        Integer forced = showroomRadiation;
        if (forced != null) {
            alphaNow = HoannaVisuals.vignetteAlpha(forced);
            return;
        }
        int radiation = 0;
        if (player != null) {
            IPlayerData data = PlayerDataEvents.get(player);
            radiation = data != null ? data.getRadiation() : 0;
        }
        alphaNow = HoannaVisuals.approach(alphaNow, HoannaVisuals.vignetteAlpha(radiation), HoannaVisuals.RADIATION_VIGNETTE_RATE);
    }

    @SubscribeEvent
    public static void onRadiationOverlayLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        alphaPrev = alphaNow = 0F;
        showroomRadiation = null;
    }

    /** HUD 层（{@link HoannaClient} 登记在霜冻覆盖层之上） */
    public static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        float alpha = Mth.lerp(partialTick, alphaPrev, alphaNow);
        if (alpha <= 0.004F) {
            return;
        }
        gui.setupOverlayRenderState(true, false);
        float band = Math.min(width, height) * HoannaVisuals.RADIATION_VIGNETTE_BAND;
        float w = width;
        float h = height;
        float r = HoannaVisuals.red(HoannaVisuals.RADIATION_VIGNETTE_COLOR);
        float g = HoannaVisuals.green(HoannaVisuals.RADIATION_VIGNETTE_COLOR);
        float b = HoannaVisuals.blue(HoannaVisuals.RADIATION_VIGNETTE_COLOR);
        Matrix4f pose = graphics.pose().last().pose();
        VertexConsumer buffer = graphics.bufferSource().getBuffer(RenderType.gui());
        // 四条梯形带（外沿 alpha、内沿 0），在对角线上接缝；顶点顺序和 GuiGraphics.fill 同一个绕向（照 MatrixHitEffects）
        band(buffer, pose, r, g, b, 0F, 0F, alpha, band, band, 0F, w - band, band, 0F, w, 0F, alpha);
        band(buffer, pose, r, g, b, 0F, 0F, alpha, 0F, h, alpha, band, h - band, 0F, band, band, 0F);
        band(buffer, pose, r, g, b, band, h - band, 0F, 0F, h, alpha, w, h, alpha, w - band, h - band, 0F);
        band(buffer, pose, r, g, b, w - band, band, 0F, w - band, h - band, 0F, w, h, alpha, w, 0F, alpha);
        graphics.flush();
    }

    private static void band(VertexConsumer buffer, Matrix4f pose, float r, float g, float b,
                             float x0, float y0, float a0, float x1, float y1, float a1,
                             float x2, float y2, float a2, float x3, float y3, float a3) {
        buffer.vertex(pose, x0, y0, OVERLAY_Z).color(r, g, b, a0).endVertex();
        buffer.vertex(pose, x1, y1, OVERLAY_Z).color(r, g, b, a1).endVertex();
        buffer.vertex(pose, x2, y2, OVERLAY_Z).color(r, g, b, a2).endVertex();
        buffer.vertex(pose, x3, y3, OVERLAY_Z).color(r, g, b, a3).endVertex();
    }
}
