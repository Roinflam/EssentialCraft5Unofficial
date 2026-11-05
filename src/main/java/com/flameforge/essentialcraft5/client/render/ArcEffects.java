package com.flameforge.essentialcraft5.client.render;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 世界坐标的电弧（第四批框架 F4-14，只在客户端用）：没有方块实体的线圈基座在 {@code animateTick} 里冒的火花、
 * 线圈打向射程里（最远 32 格）目标的放电——挂在方块实体渲染器上就得把渲染盒撑到 32 格，不如在这里统一画。
 * <pre>{@code
 * // 客户端（方块事件 / animateTick 里）
 * ArcEffects.spawn(level, Vec3.atCenterOf(coilPos).add(0, 0.3, 0), target.getEyePosition(), ArcEffect.Style.COIL_STRIKE);
 * level.playLocalSound(x, y, z, ECSounds.LIGHTNING_HIT.get(), SoundSource.BLOCKS, 2F, 2F, false);
 * }</pre>
 * 每个客户端 tick 推进寿命、到期移除；在 {@link RenderLevelStageEvent.Stage#AFTER_PARTICLES} 画（加色混合，画在不透明物体和粒子之后）。
 * 上限（policy §1.2「客户端渲染」）：同时最多 {@link #MAX_ARCS} 道（满了挤掉最老的）；离镜头 {@link #MAX_SPAWN_DISTANCE} 格以外不生成、
 * {@link #MAX_RENDER_DISTANCE} 格以外不画，视锥外不画；换世界、断开连接时清空。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class ArcEffects {

    /** 同时存在的电弧上限 */
    public static final int MAX_ARCS = 256;
    /** 电弧中点离镜头多远以内才生成（格） */
    public static final double MAX_SPAWN_DISTANCE = 64D;
    /** 电弧中点离镜头多远以内才画（格） */
    public static final double MAX_RENDER_DISTANCE = 96D;

    private record Entry(ResourceKey<Level> dimension, ArcEffect arc) {
    }

    private static final Deque<Entry> ARCS = new ArrayDeque<>();

    private ArcEffects() {
    }

    /**
     * 在世界坐标 from → to 之间生成一道电弧（只在客户端世界上有效，服务端调用返回 null、什么都不做）。
     * 离镜头太远返回 null；满了挤掉最老的一道。
     */
    @Nullable
    public static ArcEffect spawn(Level level, Vec3 from, Vec3 to, ArcEffect.Style style) {
        if (!level.isClientSide) {
            return null;
        }
        Vec3 middle = from.add(to).scale(0.5D);
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        if (camera.distanceToSqr(middle) > MAX_SPAWN_DISTANCE * MAX_SPAWN_DISTANCE) {
            return null;
        }
        return add(level, new ArcEffect(level.random, from, to, style));
    }

    /** 放进一道已经建好的电弧（世界坐标） */
    @Nullable
    public static ArcEffect add(Level level, ArcEffect arc) {
        if (!level.isClientSide) {
            return null;
        }
        while (ARCS.size() >= MAX_ARCS) {
            ARCS.pollFirst();
        }
        ARCS.addLast(new Entry(level.dimension(), arc));
        return arc;
    }

    /** 现存的电弧数（调试用） */
    public static int count() {
        return ARCS.size();
    }

    public static void clear() {
        ARCS.clear();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ARCS.isEmpty() || Minecraft.getInstance().isPaused()) {
            return;
        }
        ARCS.removeIf(entry -> entry.arc().tick());
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ARCS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        float partialTick = event.getPartialTick();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Frustum frustum = event.getFrustum();
        VertexConsumer consumer = null;
        double maxSqr = MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE;
        for (Entry entry : ARCS) {
            ArcEffect arc = entry.arc();
            if (entry.dimension() != level.dimension()
                    || camera.distanceToSqr(arc.bounds().getCenter()) > maxSqr
                    || (frustum != null && !frustum.isVisible(arc.bounds()))) {
                continue;
            }
            if (consumer == null) {
                consumer = buffers.getBuffer(RenderType.lightning());
            }
            ArcRenderer.render(arc, poseStack, consumer, partialTick);
        }
        if (consumer != null) {
            buffers.endBatch(RenderType.lightning());
        }
        poseStack.popPose();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            ARCS.clear();
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ARCS.clear();
    }
}
