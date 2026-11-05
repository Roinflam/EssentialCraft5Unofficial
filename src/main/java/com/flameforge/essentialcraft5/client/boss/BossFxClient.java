package com.flameforge.essentialcraft5.client.boss;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.entity.boss.BossFxRules;
import com.flameforge.essentialcraft5.network.S2CBossFxPacket;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 首领整屏画面的客户端（第十三批 13c-Fb，batch13.md §5.3.5「网络」「护眼」）：{@link S2CBossFxPacket} 收到后在客户端主线程调 {@link #handle}，
 * 按类型交给登记的处理器（{@link #register}）。服务端只在事件发生时（开始、换相、结束）发一次，画面怎么淡、什么时候消失由这里管。
 *
 * <h2>收到一个事件</h2>
 * 按实体 id 找首领：还没同步到（刚进追踪范围、包先到）或已经没了就整个忽略；类型没人登记的也忽略（日志记一次）。
 * 各路在自己的客户端注册类里登记（{@code FMLClientSetupEvent} 里调）：
 * <ul>
 *   <li>{@link #registerEdge}：最常用的一种，「边缘晕影」——参数 a = 强度 0~1（0 = 淡出），参数 b = 颜色（RGB 整数直接写成 float，24 位以内是精确的）；
 *       同一只首领、同一类型只有一层，再来一个事件就改它的目标；</li>
 *   <li>{@link #register}：自己写处理器，里面调 {@link #edge} / {@link #clear} / {@link #flash} 这几个画法（13e 的熄光、天色压暗另做时也照这几条护眼规矩）；</li>
 *   <li>{@link #registerUnbound}：不要首领实体的事件（名字大字、横幅、镜头震动：打倒那一刻实体已经在死、可能先于包没了），找不到实体也照样处理。</li>
 * </ul>
 *
 * <h2>护眼（{@link BossFxRules}，policy §1.2 / §4.18）</h2>
 * <ul>
 *   <li>边缘晕影：强度和颜色每次改目标都各自走满 200 刻（10 秒）淡过去（换相只换颜色时颜色也慢慢变，不跳色）；
 *       只画屏幕四边、往里短边 × 0.22 淡到透明，中间不碰；最浓 0.20，几层合计也不超过；</li>
 *   <li>闪光：唯一的亮度突变，全局至少隔 200 刻一次（≤ 0.1 次 / 秒），峰值 ≤ 0.35、12 刻淡完；打开「隐藏闪电闪烁」时峰值只剩 1/3、间隔翻倍；</li>
 *   <li>都乘原版「屏幕扭曲效果」滑块；客户端配置 {@code [boss_fx] screen_effects} 关掉就一律不画（同原版暗角、辐射晕影，按 F1 不隐藏：这是画面效果，不是 HUD）；</li>
 *   <li>首领没了（死了、散了、走出追踪范围）而结束事件没收到时，这里每刻查一遍，自己淡出，不会一直挂在屏幕上；退出世界全部清掉。</li>
 * </ul>
 * 叠层登记在 {@code client/BossFrameworkClient}（霜冻覆盖层之上，同辐射晕影）。只在客户端加载。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class BossFxClient {

    private static final Logger LOGGER = LoggerFactory.getLogger("EssentialCraft5/BossFx");
    /** HUD 覆盖层的深度（同辐射晕影：原版暗角、霜冻覆盖层都画在 −90） */
    private static final float OVERLAY_Z = -90F;
    /** 展示厅摆的那层用的类型（不和真的画面类型撞：类型 255 留给它） */
    private static final int SHOWROOM_TYPE = 255;

    /** 一个画面事件的处理器（首领一定找得到、还活着时才调） */
    @FunctionalInterface
    public interface Handler {
        void handle(S2CBossFxPacket packet, Entity boss);
    }

    private record Key(int type, int entityId) {
    }

    private static final class Edge {
        final BossFxRules.ColorFade color = new BossFxRules.ColorFade();
        final BossFxRules.Fade fade = new BossFxRules.Fade();
    }

    private static final Map<Integer, Handler> HANDLERS = new HashMap<>();
    private static final Map<Integer, Consumer<S2CBossFxPacket>> UNBOUND = new HashMap<>();
    private static final Set<Integer> WARNED = new HashSet<>();
    private static final Map<Key, Edge> EDGES = new LinkedHashMap<>();
    private static final BossFxRules.FlashLimiter LIMITER = new BossFxRules.FlashLimiter();
    private static long clientTicks;
    private static float flashPeak;
    private static int flashRgb;
    private static long flashStart = Long.MIN_VALUE / 2;

    private BossFxClient() {
    }

    // ------------------------------------------------------------------
    // 登记与分发
    // ------------------------------------------------------------------

    /** 登记一种画面的处理器（同一类型后登记的顶掉前一个）。在客户端主线程调（{@code FMLClientSetupEvent.enqueueWork} 里） */
    public static void register(int type, Handler handler) {
        HANDLERS.put(type & 0xFF, handler);
    }

    /** 登记一种不要首领实体的事件（见类注释）；同一类型不要再用 {@link #register} 登记 */
    public static void registerUnbound(int type, Consumer<S2CBossFxPacket> handler) {
        UNBOUND.put(type & 0xFF, handler);
    }

    /** 登记成「边缘晕影」：a = 强度 0~1（0 = 淡出），b = 颜色 RGB（整数写成 float） */
    public static void registerEdge(int type) {
        register(type, (packet, boss) -> edge(packet.type(), packet.entityId(), Math.round(packet.b()), packet.a()));
    }

    /** 收到一个画面事件（客户端主线程，{@link S2CBossFxPacket#handle} 调） */
    public static void handle(S2CBossFxPacket packet) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Consumer<S2CBossFxPacket> unbound = UNBOUND.get(packet.type());
        if (unbound != null) {
            unbound.accept(packet);
            return;
        }
        Entity boss = level.getEntity(packet.entityId());
        if (boss == null || !boss.isAlive()) {
            return;
        }
        Handler handler = HANDLERS.get(packet.type());
        if (handler == null) {
            if (WARNED.add(packet.type())) {
                LOGGER.warn("首领画面：类型 {} 没有登记处理器，忽略", packet.type());
            }
            return;
        }
        handler.handle(packet, boss);
    }

    // ------------------------------------------------------------------
    // 画法
    // ------------------------------------------------------------------

    /**
     * 这只首领这一类型的边缘晕影改到 strength（0~1，0 = 淡出）、颜色 rgb；强度和颜色各自 200 刻淡过去。
     * 新出现的一层直接用这个颜色（强度本来就从 0 淡入）；已经在的一层换色时从当前显示的颜色慢慢变过去，不跟着强度有没有变。
     */
    public static void edge(int type, int entityId, int rgb, float strength) {
        Key key = new Key(type & 0xFF, entityId);
        Edge edge = EDGES.get(key);
        if (edge == null) {
            if (!(strength > 0F)) {
                return;
            }
            edge = new Edge();
            edge.color.snap(rgb);
            EDGES.put(key, edge);
        } else {
            edge.color.retarget(rgb);
        }
        edge.fade.retarget(strength);
    }

    /** 这只首领这一类型的晕影淡出 */
    public static void clear(int type, int entityId) {
        Edge edge = EDGES.get(new Key(type & 0xFF, entityId));
        if (edge != null) {
            edge.fade.retarget(0F);
        }
    }

    /** 闪一下（全局限频，见类注释）；被限频挡掉、配置关了返回 false */
    public static boolean flash(int rgb, float peak) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean reduced = minecraft.options.hideLightningFlash().get();
        if (!BossFxClientConfig.screenEffects() || !LIMITER.tryFlash(clientTicks, BossFxRules.flashInterval(reduced))) {
            return false;
        }
        flashRgb = rgb & 0xFFFFFF;
        flashPeak = BossFxRules.flashPeak(peak, reduced);
        flashStart = clientTicks;
        return true;
    }

    /** 展示厅：直接摆一层已经淡到位的晕影（不等 10 秒）；strength ≤ 0 清掉所有 */
    public static void showroomEdge(int rgb, float strength) {
        EDGES.clear();
        if (strength > 0F) {
            Edge edge = new Edge();
            edge.color.snap(rgb);
            edge.fade.retarget(strength);
            for (int i = 0; i <= BossFxRules.FADE_TICKS; i++) {
                edge.fade.tick();
            }
            EDGES.put(new Key(SHOWROOM_TYPE, -1), edge);
        }
    }

    // ------------------------------------------------------------------
    // 每刻
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onBossFxClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isPaused()) {
            return;
        }
        clientTicks++;
        if (EDGES.isEmpty()) {
            return;
        }
        ClientLevel level = minecraft.level;
        Iterator<Map.Entry<Key, Edge>> it = EDGES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Key, Edge> entry = it.next();
            Edge edge = entry.getValue();
            if (entry.getKey().type() != SHOWROOM_TYPE) {
                Entity boss = level == null ? null : level.getEntity(entry.getKey().entityId());
                if (boss == null || !boss.isAlive()) {
                    edge.fade.retarget(0F);
                }
            }
            edge.fade.tick();
            edge.color.tick();
            if (edge.fade.finished()) {
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onBossFxLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        EDGES.clear();
        LIMITER.reset();
        flashStart = Long.MIN_VALUE / 2;
    }

    // ------------------------------------------------------------------
    // 画
    // ------------------------------------------------------------------

    /** HUD 层（{@code client/BossFrameworkClient} 登记在霜冻覆盖层之上） */
    public static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        if (!BossFxClientConfig.screenEffects()) {
            return;
        }
        float screen = Minecraft.getInstance().options.screenEffectScale().get().floatValue();
        if (screen <= 0F) {
            return;
        }
        float flash = BossFxRules.flashAlpha(flashPeak, clientTicks - flashStart + partialTick) * screen;
        if (EDGES.isEmpty() && flash <= 0.004F) {
            return;
        }
        gui.setupOverlayRenderState(true, false);
        Matrix4f pose = graphics.pose().last().pose();
        VertexConsumer buffer = graphics.bufferSource().getBuffer(RenderType.gui());
        if (!EDGES.isEmpty()) {
            float sum = 0F;
            for (Edge edge : EDGES.values()) {
                sum += BossFxRules.edgeAlpha(edge.fade.value(partialTick));
            }
            float scale = BossFxRules.edgeScale(sum) * screen;
            float band = Math.min(width, height) * BossFxRules.EDGE_BAND;
            for (Edge edge : EDGES.values()) {
                float alpha = BossFxRules.edgeAlpha(edge.fade.value(partialTick)) * scale;
                if (alpha > 0.002F) {
                    edgeBands(buffer, pose, edge.color.value(partialTick), alpha, band, width, height);
                }
            }
        }
        if (flash > 0.004F) {
            float r = ((flashRgb >> 16) & 0xFF) / 255F;
            float g = ((flashRgb >> 8) & 0xFF) / 255F;
            float b = (flashRgb & 0xFF) / 255F;
            quad(buffer, pose, r, g, b, 0F, 0F, flash, 0F, height, flash, width, height, flash, width, 0F, flash);
        }
        graphics.flush();
    }

    /** 四条梯形带（外沿 alpha、内沿 0），在对角线上接缝（照辐射晕影） */
    private static void edgeBands(VertexConsumer buffer, Matrix4f pose, int rgb, float alpha, float band, float w, float h) {
        float r = ((rgb >> 16) & 0xFF) / 255F;
        float g = ((rgb >> 8) & 0xFF) / 255F;
        float b = (rgb & 0xFF) / 255F;
        quad(buffer, pose, r, g, b, 0F, 0F, alpha, band, band, 0F, w - band, band, 0F, w, 0F, alpha);
        quad(buffer, pose, r, g, b, 0F, 0F, alpha, 0F, h, alpha, band, h - band, 0F, band, band, 0F);
        quad(buffer, pose, r, g, b, band, h - band, 0F, 0F, h, alpha, w, h, alpha, w - band, h - band, 0F);
        quad(buffer, pose, r, g, b, w - band, band, 0F, w - band, h - band, 0F, w, h, alpha, w, 0F, alpha);
    }

    private static void quad(VertexConsumer buffer, Matrix4f pose, float r, float g, float b,
                             float x0, float y0, float a0, float x1, float y1, float a1,
                             float x2, float y2, float a2, float x3, float y3, float a3) {
        buffer.vertex(pose, x0, y0, OVERLAY_Z).color(r, g, b, a0).endVertex();
        buffer.vertex(pose, x1, y1, OVERLAY_Z).color(r, g, b, a1).endVertex();
        buffer.vertex(pose, x2, y2, OVERLAY_Z).color(r, g, b, a2).endVertex();
        buffer.vertex(pose, x3, y3, OVERLAY_Z).color(r, g, b, a3).endVertex();
    }
}
