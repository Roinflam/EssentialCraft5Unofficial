package com.flameforge.essentialcraft5.client.boss;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.entity.boss.CalamityHeartEntity;
import com.flameforge.essentialcraft5.network.S2CBossFxPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 天灾之心的大字和镜头（tools/plan/calamity_heart_v2.md §4）：开场名字（{@link S2CBossFxPacket#INTRO}）、阶段转换的横幅（{@link S2CBossFxPacket#BANNER}）、
 * 镜头震动（{@link S2CBossFxPacket#SHAKE}）。三种都用 {@link BossFxClient#registerUnbound} 登记（打倒那一刻实体已经在死），
 * HUD 层登记在 {@code client/BossFrameworkClient}。
 * <ul>
 *   <li>名字（碑刻样式）：「天灾之心」四个大字带刻痕阴影、左右两道细线，下面一行「四灾同源 · 主灾：××」用主灾颜色（黑暗、地震的相色太暗，用 {@link #bright} 提亮）；
 *       淡入 {@value #TITLE_FADE_IN} 刻、淡出 {@value #TITLE_FADE_OUT} 刻，不闪、不做亮度突变；</li>
 *   <li>横幅：小一号的标题 + 一行说明，排队一条一条放（最多排 {@value #BANNER_QUEUE} 条，名字在显示时先等着）；</li>
 *   <li>镜头：只加 roll / pitch 偏移（不改真实朝向和准星），最多 {@value #SHAKE_MAX_DEGREES}°、按收到的刻数衰减到 0；乘原版「屏幕扭曲效果」，
 *       客户端配置 {@code boss_fx.camera_shake} 关掉就不晃；创造 / 旁观不晃。</li>
 * </ul>
 * 名字、横幅跟着 F1 一起隐藏（这是 HUD 文字），镜头震动不受 F1 影响。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class CalamityHeartOverlay {

    private static final int TITLE_FADE_IN = 10;
    private static final int TITLE_FADE_OUT = 15;
    private static final int BANNER_TICKS = 60;
    private static final int BANNER_FADE = 8;
    private static final int BANNER_QUEUE = 3;
    public static final float SHAKE_MAX_DEGREES = 1.2F;
    /** 名字字号（GUI 倍数）、字间距（未缩放的像素） */
    private static final float TITLE_SCALE = 4F;
    private static final float SUB_SCALE = 1.5F;
    private static final float BANNER_SCALE = 2F;
    private static final int TITLE_SPACING = 3;
    /** 刻痕：阴影色、字色 */
    private static final int ENGRAVE_SHADOW = 0x1A100A;
    private static final int ENGRAVE_FACE = 0xEFE6D2;
    /** 横幅编号 → 语言键里的名字（只追加；编号见 {@code CalamityHeartEntity.BANNER_*}） */
    private static final String[] BANNERS = {
            "pillar", "pillar", "pillar", "pillar",
            "unbalanced", "converge", "collapse", "reraise", "unraveling", "defeated",
            "combo.eclipse", "combo.nightfall", "combo.miasma_shade", "combo.molten_rift"
    };

    private record Banner(int id, int color) {
    }

    private static long clientTicks;
    private static int titleAspect = -1;
    private static long titleStart;
    private static int titleTicks;
    private static final Deque<Banner> QUEUE = new ArrayDeque<>();
    private static Banner banner;
    private static long bannerStart;
    private static float shakeStrength;
    private static long shakeStart;
    private static int shakeTicks;

    private CalamityHeartOverlay() {
    }

    /** 登记三种画面事件（{@code CalamityHeartClient} 的 FMLClientSetupEvent 里调） */
    public static void register() {
        BossFxClient.registerUnbound(S2CBossFxPacket.INTRO, packet -> title(Math.round(packet.a()), Math.round(packet.b())));
        BossFxClient.registerUnbound(S2CBossFxPacket.BANNER, packet -> banner(Math.round(packet.a()), Math.round(packet.b())));
        BossFxClient.registerUnbound(S2CBossFxPacket.SHAKE, packet -> shake(packet.a(), Math.round(packet.b())));
    }

    /** 开场名字：主灾那一相、显示多少刻 */
    public static void title(int aspect, int ticks) {
        titleAspect = Math.floorMod(aspect, 4);
        titleStart = clientTicks;
        titleTicks = Math.max(TITLE_FADE_IN + TITLE_FADE_OUT, ticks);
    }

    /** 一条横幅（编号不认识的忽略） */
    public static void banner(int id, int color) {
        if (id < 0 || id >= BANNERS.length) {
            return;
        }
        if (QUEUE.size() >= BANNER_QUEUE) {
            QUEUE.pollFirst();
        }
        QUEUE.addLast(new Banner(id, color & 0xFFFFFF));
    }

    /** 镜头震一下（强的盖过弱的、同样强的从头算） */
    public static void shake(float strength, int ticks) {
        float s = Mth.clamp(strength, 0F, 1F);
        if (s <= 0F || ticks <= 0) {
            return;
        }
        if (s >= currentShake(0F)) {
            shakeStrength = s;
            shakeStart = clientTicks;
            shakeTicks = Math.min(20, ticks);
        }
    }

    /**
     * 太暗的颜色往白里提，直到相对亮度够在暗处读清（黑暗 #4A1E7A、地震 #8A3E22 这类）；已经够亮的原样返回。
     * 物品提示、锁图标、横幅共用。
     */
    public static int bright(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        for (int i = 0; i < 12 && (0.2126F * r + 0.7152F * g + 0.0722F * b) / 255F < 0.42F; i++) {
            r += (255 - r) / 4;
            g += (255 - g) / 4;
            b += (255 - b) / 4;
        }
        return (r << 16) | (g << 8) | b;
    }

    // ------------------------------------------------------------------
    // 每刻、镜头
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void calamityOverlayTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || Minecraft.getInstance().isPaused()) {
            return;
        }
        clientTicks++;
        if (titleAspect >= 0 && clientTicks - titleStart > titleTicks) {
            titleAspect = -1;
        }
        if (banner != null && clientTicks - bannerStart > BANNER_TICKS) {
            banner = null;
        }
        if (banner == null && titleAspect < 0 && !QUEUE.isEmpty()) {
            banner = QUEUE.pollFirst();
            bannerStart = clientTicks;
        }
    }

    @SubscribeEvent
    public static void calamityOverlayLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    /** 撤掉名字、横幅（含排队的）和镜头震动（下线时；展示厅每张开拍前也调，免得上一张的横幅挡着排队） */
    public static void clear() {
        titleAspect = -1;
        banner = null;
        QUEUE.clear();
        shakeStrength = 0F;
    }

    private static float currentShake(float partialTick) {
        if (shakeStrength <= 0F || shakeTicks <= 0) {
            return 0F;
        }
        float t = (clientTicks - shakeStart + partialTick) / shakeTicks;
        if (t >= 1F) {
            return 0F;
        }
        float left = 1F - Math.max(0F, t);
        return shakeStrength * left * left;
    }

    @SubscribeEvent
    public static void calamityOverlayCamera(ViewportEvent.ComputeCameraAngles event) {
        float partial = (float) event.getPartialTick();
        float strength = currentShake(partial);
        if (strength <= 0.001F || !BossFxClientConfig.cameraShake()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || player.isCreative() || player.isSpectator()) {
            return;
        }
        float screen = minecraft.options.screenEffectScale().get().floatValue();
        float amplitude = SHAKE_MAX_DEGREES * strength * screen;
        if (amplitude <= 0.001F) {
            return;
        }
        float time = clientTicks + partial;
        float roll = (Mth.sin(time * 2.9F) * 0.7F + Mth.sin(time * 4.7F + 1.3F) * 0.3F) * amplitude;
        float pitch = (Mth.sin(time * 3.6F + 0.7F) * 0.6F + Mth.sin(time * 5.3F + 2.1F) * 0.4F) * amplitude * 0.6F;
        event.setRoll(event.getRoll() + roll);
        event.setPitch(event.getPitch() + pitch);
    }

    // ------------------------------------------------------------------
    // 画
    // ------------------------------------------------------------------

    /** HUD 层（{@code client/BossFrameworkClient} 登记，在所有原版层之上） */
    public static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || titleAspect < 0 && banner == null) {
            return;
        }
        Font font = minecraft.font;
        if (titleAspect >= 0) {
            float t = clientTicks - titleStart + partialTick;
            float alpha = fade(t, titleTicks, TITLE_FADE_IN, TITLE_FADE_OUT);
            if (alpha > 0.02F) {
                drawTitle(graphics, font, width, height, CalamityHeartEntity.Aspect.byId(titleAspect), alpha);
            }
        } else if (banner != null) {
            float t = clientTicks - bannerStart + partialTick;
            float alpha = fade(t, BANNER_TICKS, BANNER_FADE, BANNER_FADE);
            if (alpha > 0.02F) {
                drawBanner(graphics, font, width, height, banner, alpha);
            }
        }
    }

    private static float fade(float t, int total, int in, int out) {
        if (t < 0F || t > total) {
            return 0F;
        }
        float a = Math.min(1F, t / Math.max(1, in));
        return Math.min(a, Math.max(0F, (total - t) / Math.max(1, out)));
    }

    /** 碑刻样式的名字 */
    private static void drawTitle(GuiGraphics graphics, Font font, int width, int height, CalamityHeartEntity.Aspect main, float alpha) {
        String name = Component.translatable("entity.essentialcraft5.calamity_heart").getString();
        int spaced = spacedWidth(font, name);
        float centerY = height * 0.30F;
        graphics.pose().pushPose();
        graphics.pose().translate(width / 2F, centerY, 0F);
        graphics.pose().scale(TITLE_SCALE, TITLE_SCALE, 1F);
        int x = -spaced / 2;
        int y = -font.lineHeight / 2;
        // 刻痕：先往右下压一层深色，再画字面（字面稍微往左上让半格，像凿进石头）
        drawSpaced(graphics, font, name, x + 1, y + 1, argb(ENGRAVE_SHADOW, alpha * 0.85F));
        drawSpaced(graphics, font, name, x, y, argb(ENGRAVE_FACE, alpha));
        graphics.pose().popPose();

        int color = bright(main.color());
        Component sub = Component.translatable("title.essentialcraft5.calamity_heart.sub", Component.translatable(main.event().nameKey()));
        int subWidth = font.width(sub);
        float subY = centerY + font.lineHeight * TITLE_SCALE * 0.5F + 10F;
        graphics.pose().pushPose();
        graphics.pose().translate(width / 2F, subY, 0F);
        graphics.pose().scale(SUB_SCALE, SUB_SCALE, 1F);
        graphics.drawString(font, sub, -subWidth / 2 + 1, 1, argb(ENGRAVE_SHADOW, alpha * 0.8F), false);
        graphics.drawString(font, sub, -subWidth / 2, 0, argb(color, alpha), false);
        graphics.pose().popPose();

        // 两道细线：从名字两侧往外淡出
        int half = (int) (spaced * TITLE_SCALE / 2F) + 12;
        int lineY = (int) (subY + font.lineHeight * SUB_SCALE * 0.5F);
        int reach = Math.min(120, width / 2 - half - 8);
        for (int i = 0; i < reach; i += 2) {
            float a = alpha * 0.8F * (1F - i / (float) Math.max(1, reach));
            int c = argb(color, a);
            graphics.fill(width / 2 + half + i, lineY, width / 2 + half + i + 2, lineY + 1, c);
            graphics.fill(width / 2 - half - i - 2, lineY, width / 2 - half - i, lineY + 1, c);
        }
    }

    /** 横幅：标题（相色，提亮）+ 一行说明 */
    private static void drawBanner(GuiGraphics graphics, Font font, int width, int height, Banner banner, float alpha) {
        String name = BANNERS[banner.id()];
        Component title;
        Component sub;
        if (banner.id() < 4) {
            CalamityHeartEntity.Aspect aspect = CalamityHeartEntity.Aspect.byId(banner.id());
            title = Component.translatable("banner.essentialcraft5.calamity_heart.pillar", Component.translatable(aspect.event().nameKey()));
            // 解开了哪一处由封印那边的动作栏说（最后一根倒下时没有这条横幅，那一句照样有）
            sub = Component.translatable("message.essentialcraft5.calamity_heart.shield_down");
        } else {
            title = Component.translatable("banner.essentialcraft5.calamity_heart." + name);
            sub = Component.translatable("banner.essentialcraft5.calamity_heart." + name + ".sub");
        }
        int color = bright(banner.color());
        float y = height * 0.24F;
        int titleWidth = font.width(title);
        graphics.pose().pushPose();
        graphics.pose().translate(width / 2F, y, 0F);
        graphics.pose().scale(BANNER_SCALE, BANNER_SCALE, 1F);
        graphics.drawString(font, title, -titleWidth / 2 + 1, 1, argb(ENGRAVE_SHADOW, alpha * 0.8F), false);
        graphics.drawString(font, title, -titleWidth / 2, 0, argb(color, alpha), false);
        graphics.pose().popPose();
        FormattedCharSequence line = sub.getVisualOrderText();
        int subWidth = font.width(line);
        int subY = (int) (y + font.lineHeight * BANNER_SCALE + 4);
        graphics.drawString(font, line, width / 2 - subWidth / 2, subY, argb(0xE0DCD4, alpha), true);
    }

    private static int spacedWidth(Font font, String text) {
        int w = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            w += font.width(new String(Character.toChars(cp))) + TITLE_SPACING;
            i += Character.charCount(cp);
        }
        return Math.max(0, w - TITLE_SPACING);
    }

    private static void drawSpaced(GuiGraphics graphics, Font font, String text, int x, int y, int argb) {
        int cx = x;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            graphics.drawString(font, ch, cx, y, argb, false);
            cx += font.width(ch) + TITLE_SPACING;
            i += Character.charCount(cp);
        }
    }

    /** 带透明度的颜色；alpha 太小时给 0x05（字体把 &lt; 4 的透明度当成不透明） */
    private static int argb(int rgb, float alpha) {
        int a = Mth.clamp((int) (alpha * 255F), 5, 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
