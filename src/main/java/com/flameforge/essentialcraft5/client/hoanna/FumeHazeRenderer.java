package com.flameforge.essentialcraft5.client.hoanna;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.hoanna.HoannaClientConfig;
import com.flameforge.essentialcraft5.hoanna.HoannaVisuals;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 毒雾雾团（第六批 6a M3，batch6a.md §3.4、§5.8 裁定 8 / 13；游戏总线、只在客户端）。毒雾实体 {@code essentialcraft5:poison_fume} 由 M4 注册
 * （渲染器是 NoopRenderer），这里<b>按注册名</b>认它、自己画雾团——不用粒子：粒子着色器「alpha < 0.1 丢弃」会把软边切成硬圈。
 * <ul>
 *   <li>跟踪：客户端 {@code EntityJoinLevelEvent / EntityLeaveLevelEvent} 维护集合（世界卸载、退出世界清空；每刻顺手丢掉已移除的）。</li>
 *   <li>每刻：离镜头近的先挑至多 {@code fume_haze_max}（客户端配置，默认 32）只、各采一次光照（夜里跟着暗；黑暗事件照光照图的系数压暗）；
 *       算附近强度 p = Σ max(0, 1 − d / 10)，夹到 1、每刻往目标平滑 0.075（雾色染色和近处拉近地形雾用它，见 {@link HoannaEventVisuals}）。</li>
 *   <li>每帧（{@code RenderLevelStageEvent.AFTER_PARTICLES}，一次绘制）：每只 3 团，中心 = 插值位置 + (0, 2, 0) + 慢漂移
 *       (1.2·sin(t/7.3 + s), 0.5·sin(t/9.1 + s), 1.2·cos(t/8.2 + s))（t 秒、s 按实体 id 和团号），每团一个朝镜头的 8 段扇、半径 1.6 / 1.9 / 2.2 格，
 *       中心不透明度 {@link HoannaVisuals#fumeHazeAlpha}、外圈 0；{@code position_color}、普通混合、开深度测试、不写深度、不剔除。
 *       离镜头 3.5 格内淡出：镜头永远不会埋进整屏雾团。</li>
 *   <li>{@code position_color} 不吃雾：镜头实体有失明 / 黑暗效果时（和天空不画是同一个口径）按效果强度整体乘
 *       {@link HoannaVisuals#fumeHazeVisibility}，完全遮住时整帧不画，不然一片黑雾里毒雾还亮着。</li>
 * </ul>
 * 开销：32 只 × 3 团 × 24 顶点 ≈ 2 300 顶点 / 帧，顶点直接按视图矩阵变换（复用向量，不分配）。
 * 刷怪蛋 / 指令放的毒雾在任何维度都能出现（展示厅、创造模式），雾团照画；雾色和拉近地形雾只在海纳尔。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class FumeHazeRenderer {

    /** 毒雾实体的注册名（M4 注册；没注册时这里什么都不做） */
    public static final ResourceLocation POISON_FUME_ID = EssentialCraft5.id("poison_fume");
    /** 跟踪上限（防止异常情况下集合无限长；正常每名玩家附近 ≤ 24 只） */
    private static final int MAX_TRACKED = 512;
    private static final int MAX_DRAWN = 64;
    private static final int FAN_SEGMENTS = 8;
    private static final float NEARBY_RATE = 0.075F;
    /** 挑选时多看一点（雾团漂移、半径） */
    private static final float PICK_RANGE = HoannaVisuals.FUME_FAR_END + 4F;

    private static final List<Entity> TRACKED = new ArrayList<>();
    private static final Entity[] CHOSEN = new Entity[MAX_DRAWN];
    private static final float[] CHOSEN_LIGHT = new float[MAX_DRAWN];
    private static final float[] CHOSEN_DIST = new float[MAX_DRAWN];
    private static int chosenCount;
    private static float nearbyPrev;
    private static float nearbyNow;
    private static final float[] FAN_COS = new float[FAN_SEGMENTS + 1];
    private static final float[] FAN_SIN = new float[FAN_SEGMENTS + 1];
    private static final Vector3f TMP = new Vector3f();
    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();

    @Nullable
    private static EntityType<?> fumeType;
    private static boolean fumeTypeResolved;

    static {
        for (int i = 0; i <= FAN_SEGMENTS; i++) {
            float angle = i * (float) Math.PI * 2F / FAN_SEGMENTS;
            FAN_COS[i] = Mth.cos(angle);
            FAN_SIN[i] = Mth.sin(angle);
        }
    }

    private FumeHazeRenderer() {
    }

    /** 附近强度 p（0~1，按 partialTick 插值；没有毒雾时 0） */
    public static float nearby(float partialTick) {
        return Mth.lerp(partialTick, nearbyPrev, nearbyNow);
    }

    /** 正在跟踪几只（测试 / 调试） */
    public static int trackedCount() {
        return TRACKED.size();
    }

    @Nullable
    private static EntityType<?> fumeType() {
        if (!fumeTypeResolved) {
            fumeTypeResolved = true;
            fumeType = ForgeRegistries.ENTITY_TYPES.containsKey(POISON_FUME_ID) ? ForgeRegistries.ENTITY_TYPES.getValue(POISON_FUME_ID) : null;
        }
        return fumeType;
    }

    // ==================================================================
    // 跟踪
    // ==================================================================

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFumeHazeJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        EntityType<?> type = fumeType();
        if (type == null || !event.getLevel().isClientSide() || entity.getType() != type) {
            return;
        }
        if (TRACKED.size() < MAX_TRACKED && !TRACKED.contains(entity)) {
            TRACKED.add(entity);
        }
    }

    @SubscribeEvent
    public static void onFumeHazeLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() && !TRACKED.isEmpty()) {
            TRACKED.remove(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onFumeHazeLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            reset();
        }
    }

    @SubscribeEvent
    public static void onFumeHazeLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    private static void reset() {
        TRACKED.clear();
        Arrays.fill(CHOSEN, null);
        chosenCount = 0;
        nearbyPrev = nearbyNow = 0F;
    }

    // ==================================================================
    // 每刻：挑要画的、采光照、算附近强度
    // ==================================================================

    @SubscribeEvent
    public static void onFumeHazeClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            if (!TRACKED.isEmpty() || chosenCount > 0) {
                reset();
            }
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }
        nearbyPrev = nearbyNow;
        if (TRACKED.isEmpty()) {
            chosenCount = 0;
            nearbyNow = HoannaVisuals.approach(nearbyNow, 0F, NEARBY_RATE);
            return;
        }
        for (int i = TRACKED.size() - 1; i >= 0; i--) {
            Entity entity = TRACKED.get(i);
            if (entity.isRemoved() || entity.level() != level) {
                TRACKED.remove(i);
            }
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        int limit = Math.min(MAX_DRAWN, HoannaClientConfig.fumeHazeMax());
        float target = 0F;
        chosenCount = 0;
        float darkness = HoannaClientEvents.fade(HoannaEvent.DARKNESS, 0F);
        for (Entity entity : TRACKED) {
            double dx = entity.getX() - camera.x;
            double dy = entity.getY() + 2D - camera.y;
            double dz = entity.getZ() - camera.z;
            float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            target += HoannaVisuals.fumeNearbyWeight(distance);
            if (limit > 0 && distance < PICK_RANGE) {
                insertNearest(entity, distance, limit);
            }
        }
        nearbyNow = HoannaVisuals.approach(nearbyNow, HoannaVisuals.clamp01(target), NEARBY_RATE);
        for (int i = 0; i < chosenCount; i++) {
            CHOSEN_LIGHT[i] = sampleLight(level, CHOSEN[i], darkness);
        }
        for (int i = chosenCount; i < MAX_DRAWN && CHOSEN[i] != null; i++) {
            CHOSEN[i] = null;
        }
    }

    /** 按距离插进「最近的 limit 只」（插入排序，limit ≤ 64） */
    private static void insertNearest(Entity entity, float distance, int limit) {
        int at = chosenCount;
        while (at > 0 && CHOSEN_DIST[at - 1] > distance) {
            at--;
        }
        if (at >= limit) {
            return;
        }
        int last = Math.min(chosenCount, limit - 1);
        for (int i = last; i > at; i--) {
            CHOSEN[i] = CHOSEN[i - 1];
            CHOSEN_DIST[i] = CHOSEN_DIST[i - 1];
        }
        CHOSEN[at] = entity;
        CHOSEN_DIST[at] = distance;
        chosenCount = Math.min(chosenCount + 1, limit);
    }

    /** 雾团处的光照 0~1：max(天光 − 天色变暗, 方块光) / 15，黑暗事件按光照图同样的系数压 */
    private static float sampleLight(ClientLevel level, Entity entity, float darkness) {
        POS.set(entity.getBlockX(), entity.getBlockY() + 2, entity.getBlockZ());
        float sky = Math.max(0, level.getBrightness(LightLayer.SKY, POS) - level.getSkyDarken()) / 15F;
        float block = level.getBrightness(LightLayer.BLOCK, POS) / 15F;
        sky *= Mth.lerp(darkness, 1F, HoannaVisuals.DARK_SKY_LIGHT);
        block *= Mth.lerp(darkness, 1F, HoannaVisuals.DARK_BLOCK_LIGHT);
        return Math.max(sky, block);
    }

    // ==================================================================
    // 每帧：画
    // ==================================================================

    @SubscribeEvent
    public static void onFumeHazeRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || chosenCount == 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        Camera camera = event.getCamera();
        float partialTick = event.getPartialTick();
        // 雾团不吃雾：失明 / 黑暗把四周压成黑雾时跟着淡掉，完全遮住就不画
        float visibility = HoannaVisuals.fumeHazeVisibility(HoannaVisuals.visionBlocked(camera.getEntity(), partialTick));
        if (visibility <= 0F) {
            return;
        }
        Vec3 cam = camera.getPosition();
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        Matrix4f pose = event.getPoseStack().last().pose();
        double seconds = (level.getGameTime() + (double) partialTick) / 20D;
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = null;
        float baseR = HoannaVisuals.red(HoannaVisuals.FUME_HAZE_COLOR);
        float baseG = HoannaVisuals.green(HoannaVisuals.FUME_HAZE_COLOR);
        float baseB = HoannaVisuals.blue(HoannaVisuals.FUME_HAZE_COLOR);
        for (int i = 0; i < chosenCount; i++) {
            Entity entity = CHOSEN[i];
            if (entity == null || entity.isRemoved()) {
                continue;
            }
            float light = CHOSEN_LIGHT[i];
            float shade = HoannaVisuals.fumeHazeShade(light);
            double ex = Mth.lerp(partialTick, entity.xo, entity.getX());
            double ey = Mth.lerp(partialTick, entity.yo, entity.getY()) + 2D;
            double ez = Mth.lerp(partialTick, entity.zo, entity.getZ());
            for (int k = 0; k < HoannaVisuals.FUME_PUFFS; k++) {
                double s = entity.getId() * 1.7D + k * 2.1D;
                double cx = ex + 1.2D * Math.sin(seconds / 7.3D + s) - cam.x;
                double cy = ey + 0.5D * Math.sin(seconds / 9.1D + s) - cam.y;
                double cz = ez + 1.2D * Math.cos(seconds / 8.2D + s) - cam.z;
                float distance = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
                float alpha = HoannaVisuals.fumeHazeAlpha(light, distance) * visibility;
                if (alpha < 0.004F) {
                    continue;
                }
                if (consumer == null) {
                    consumer = buffers.getBuffer(FumeRenderType.HAZE);
                }
                float radius = HoannaVisuals.FUME_PUFF_MIN_RADIUS
                        + (HoannaVisuals.FUME_PUFF_MAX_RADIUS - HoannaVisuals.FUME_PUFF_MIN_RADIUS) * k / (HoannaVisuals.FUME_PUFFS - 1F);
                puff(consumer, pose, (float) cx, (float) cy, (float) cz, radius, left, up, baseR * shade, baseG * shade, baseB * shade, alpha);
            }
        }
        if (consumer != null) {
            buffers.endBatch(FumeRenderType.HAZE);
        }
    }

    /** 一团：朝镜头的 8 段扇（8 个三角形），中心 alpha、外圈 0 */
    private static void puff(VertexConsumer consumer, Matrix4f pose, float cx, float cy, float cz, float radius, Vector3f left, Vector3f up,
                             float r, float g, float b, float alpha) {
        for (int i = 0; i < FAN_SEGMENTS; i++) {
            vertex(consumer, pose, cx, cy, cz, r, g, b, alpha);
            rim(consumer, pose, cx, cy, cz, radius, left, up, i, r, g, b);
            rim(consumer, pose, cx, cy, cz, radius, left, up, i + 1, r, g, b);
        }
    }

    private static void rim(VertexConsumer consumer, Matrix4f pose, float cx, float cy, float cz, float radius, Vector3f left, Vector3f up, int i,
                            float r, float g, float b) {
        float c = FAN_COS[i] * radius;
        float s = FAN_SIN[i] * radius;
        vertex(consumer, pose, cx + c * left.x() + s * up.x(), cy + c * left.y() + s * up.y(), cz + c * left.z() + s * up.z(), r, g, b, 0F);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, float x, float y, float z, float r, float g, float b, float a) {
        pose.transformPosition(x, y, z, TMP);
        consumer.vertex(TMP.x(), TMP.y(), TMP.z()).color(r, g, b, a).endVertex();
    }

    /** 雾团的渲染类型：position_color、三角形、普通半透明混合、不剔除、只写颜色（照常做深度测试） */
    private static final class FumeRenderType extends RenderType {

        static final RenderType HAZE = create(EssentialCraft5.MODID + ":fume_haze",
                DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES, 4096, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(POSITION_COLOR_SHADER)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));

        private FumeRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                               boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
        }
    }
}
