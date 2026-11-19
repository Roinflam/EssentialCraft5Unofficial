package com.flameforge.essentialcraft5.client.vfx;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 特效的调度：管 {@link VfxEffect} 列表和 {@link VfxParticles}，客户端刻末尾推进，{@link RenderLevelStageEvent.Stage#AFTER_PARTICLES} 一次画完。
 * <h2>一帧怎么画</h2>
 * <ol>
 *   <li>{@link Vfx#beginFrame}：定这一帧用完整画法还是稳妥画法，量帧时间；</li>
 *   <li>所有特效（视锥内、{@value Vfx#LOD_CULL} 格以内）和粒子往自己的缓冲里写面片（{@link VfxDraw}），每种渲染类型一块固定缓冲，互不打断；</li>
 *   <li>有扭曲面片就先拷场景（{@link VfxScreen#captureScene}）、画扭曲，再画半透明（烟），最后画加色（光）——光不被扭曲，边缘才利落。</li>
 * </ol>
 * 光影包画阴影图的那一遍不画（{@link VfxCompat#renderingShadowPass()}）。换世界、断开连接时全部清掉。
 * 同时最多 {@value #MAX_EFFECTS} 个特效，满了新来的不加（同粒子池）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class VfxEffects {

    public static final int MAX_EFFECTS = 512;
    private static final Logger LOGGER = LoggerFactory.getLogger("EssentialCraft5/Vfx");
    private static final List<VfxEffect> EFFECTS = new ArrayList<>();
    // 特效在自己的 tick 里再加特效（蓄满放出、命中再炸一下）时先放这里，等这一轮 removeIf 走完再并进去，不然会抛并发修改
    private static final List<VfxEffect> PENDING = new ArrayList<>();
    private static boolean ticking;
    private static MultiBufferSource.BufferSource buffers;
    private static boolean failed;
    /** 上一帧画了多少个四边形（调试 / 性能统计用） */
    public static int lastQuads;

    private VfxEffects() {
    }

    /** 加一个特效（只在客户端主线程调） */
    public static void add(VfxEffect effect) {
        if (EFFECTS.size() + PENDING.size() < MAX_EFFECTS) {
            (ticking ? PENDING : EFFECTS).add(effect);
        }
    }

    public static int size() {
        return EFFECTS.size();
    }

    /** 清掉全部特效和粒子 */
    public static void clearAll() {
        EFFECTS.clear();
        PENDING.clear();
        VfxParticles.clear();
        VfxScreen.clear();
    }

    @SubscribeEvent
    public static void onVfxClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) {
            return;
        }
        if (!EFFECTS.isEmpty()) {
            ticking = true;
            try {
                EFFECTS.removeIf(effect -> !effect.tick());
            } finally {
                ticking = false;
            }
        }
        if (!PENDING.isEmpty()) {
            EFFECTS.addAll(PENDING);
            PENDING.clear();
        }
        VfxParticles.tick();
        VfxScreen.tick();
    }

    @SubscribeEvent
    public static void onVfxRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || failed) {
            return;
        }
        if (EFFECTS.isEmpty() && VfxParticles.count() == 0) {
            lastQuads = 0;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || VfxCompat.renderingShadowPass()) {
            return;
        }
        try {
            render(event, level);
        } catch (RuntimeException e) {
            // 画特效出了错只关掉特效，不让整个游戏崩；日志里留一次完整堆栈
            failed = true;
            LOGGER.error("[特效] 渲染出错，本次游戏里不再画特效", e);
        }
    }

    private static void render(RenderLevelStageEvent event, ClientLevel level) {
        Vfx.beginFrame();
        boolean safe = Vfx.safeMode();
        boolean distortion = Vfx.distortionAllowed();
        float partialTick = event.getPartialTick();
        float time = level.getGameTime() + partialTick;
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource source = buffers();
        // 冲击帧压暗在特效之前单独提交：背景沉下去，特效画在上面照样亮（稳妥画法下不画）
        if (!safe && VfxScreen.drawImpactShade(source, poseStack.last().pose(), event.getCamera(), partialTick)) {
            source.endBatch(VfxRenderTypes.SCREEN_SHADE);
        }
        VfxDraw draw = new VfxDraw(source, poseStack.last().pose(), event.getCamera(), partialTick, time, safe, distortion);
        Frustum frustum = event.getFrustum();
        ticking = true;
        try {
            for (VfxEffect effect : EFFECTS) {
                if (frustum == null || frustum.isVisible(effect.bounds())) {
                    effect.render(draw);
                }
            }
        } finally {
            ticking = false;
        }
        VfxParticles.render(draw);
        if (draw.hasDistortion()) {
            if (distortion) {
                VfxScreen.captureScene();
            }
            source.endBatch(VfxRenderTypes.DISTORT);
        }
        for (RenderType type : VfxRenderTypes.all()) {
            if (type != VfxRenderTypes.DISTORT) {
                source.endBatch(type);
            }
        }
        // 速度线压在所有特效上面
        if (!safe && VfxScreen.drawSpeedLines(source, poseStack.last().pose(), event.getProjectionMatrix(), event.getCamera(), partialTick)) {
            source.endBatch(VfxRenderTypes.SCREEN_LINES);
        }
        lastQuads = draw.quads();
    }

    private static MultiBufferSource.BufferSource buffers() {
        if (buffers == null) {
            Map<RenderType, BufferBuilder> fixed = new HashMap<>();
            for (RenderType type : VfxRenderTypes.all()) {
                fixed.put(type, new BufferBuilder(type.bufferSize()));
            }
            buffers = MultiBufferSource.immediateWithBuffers(fixed, new BufferBuilder(256));
        }
        return buffers;
    }

    @SubscribeEvent
    public static void onVfxLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            clearAll();
        }
    }

    @SubscribeEvent
    public static void onVfxLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clearAll();
        failed = false;
    }
}
