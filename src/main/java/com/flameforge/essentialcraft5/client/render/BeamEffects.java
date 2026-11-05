package com.flameforge.essentialcraft5.client.render;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 枪械射线的一次性光束（第五批框架 F5-5 建的入口，ECGuns 模块实现）。只在客户端：{@code ECGuns.init()} 登记的 {@code S2CGunBeamPacket}
 * 处理器收到包就调 {@link #spawn}（{@code ECNetwork.registerS2C} 已经套了 {@code DistExecutor}）。写法照 {@link ArcEffects}。
 * <ul>
 *   <li>外形照原作 {@code RenderMRURay}：8 边形细管，贴图 {@code special/mru_beam.png}，沿长度每 32 格平铺一次；寿命 {@value #LIFETIME} 刻，
 *       半径 {@code 0.075 × (1 + age / 60)}（慢慢变粗），透明度 {@code 1 − age / 60}（慢慢变淡）；
 *       颜色按透镜 balance：0 中性 (0, 1, 1)、1 混沌 (1, 0, 0)、2 冰冻 (0, 0, 1)、3 魔力 (1, 0, 1)、4 暗影 (0.3, 0.3, 0.3)。</li>
 *   <li>加色混合（SRC_ALPHA, ONE，同闪电）、只写颜色不写深度、两面都画（从侧面看是一道芯亮边淡的光）；在 {@link RenderLevelStageEvent.Stage#AFTER_PARTICLES} 画。
 *       RenderType 是自建的带贴图版（{@code POSITION_COLOR_TEX} + {@code position_color_tex} 着色器），只借了 {@code MRUPresenceRenderer.RayRenderType}
 *       「继承 RenderType 取 protected 状态常量」的写法。</li>
 *   <li><b>镜头不能在管子里</b>（审查 GUNS-3）：管子两面都画、加色，镜头在管口或管子里时，视线往哪看都会碰到管壁，整屏被染成透镜色（加特林几十道叠起来接近饱和）。
 *     <ul>
 *       <li>起点由调用方让开：枪的光束包里，射手自己的从枪口画、别人的从眼睛往前让开半格（{@code client/GunsClient.onBeamPacket}），射线判定照旧从眼睛算；</li>
 *       <li>{@code fadeIn}：开头这么多格从全透明渐变到正常，管口不是一圈硬边；</li>
 *       <li>镜头离光束（线段）不到 {@value #NEAR_FADE_OUTER} 格时整条按距离变淡，{@value #NEAR_FADE_INNER} 格以内不画——被射线穿过脑袋的人、走进还没散的光束里的人不会被染屏。</li>
 *       <li>锥形起点（{@link #spawn(Vec3, Vec3, int, float, float, float)}）：开狙击镜时自己的光束从镜子正下方出来，起点一段按斜率收细，
 *           放大几倍后在镜里仍是一条细线，不糊满下半边。</li>
 *     </ul>
 *   </li>
 *   <li>上限（policy §1.2「客户端渲染」）：同时最多 {@value #MAX_BEAMS} 条（满了挤掉最老的）；离镜头 {@value #MAX_SPAWN_DISTANCE} 格外不生成、
 *       {@value #MAX_RENDER_DISTANCE} 格外不画、视锥外不画；换世界、断开连接时清空。顶点相对镜头算（双精度减完再转 float），远离原点也不抖。</li>
 * </ul>
 * 展示厅可以直接调 {@link #spawn} 画 5 种颜色的光束。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class BeamEffects {

    /** 同时存在的光束上限 */
    public static final int MAX_BEAMS = 256;
    /** 寿命（刻，原作射线实体 60 刻消失） */
    public static final int LIFETIME = 60;
    /** 光束离镜头多远以内才生成（格，按线段上最近的点算） */
    public static final double MAX_SPAWN_DISTANCE = 64D;
    /** 光束离镜头多远以内才画（格） */
    public static final double MAX_RENDER_DISTANCE = 96D;
    /** 初始半径（原作 0.75 × size，size = 0.1 × (1 + age / 60)） */
    public static final float BASE_RADIUS = 0.075F;
    /** 管子的边数 */
    private static final int SIDES = 8;
    /** 贴图沿长度每多少格平铺一次 */
    private static final float TEXTURE_LENGTH = 32F;
    /** 镜头离光束这么近（格）以内不画（最粗时半径 0.15，留出余量） */
    static final double NEAR_FADE_INNER = 0.2D;
    /** 镜头离光束这么近（格）以内开始变淡 */
    static final double NEAR_FADE_OUTER = 0.6D;
    private static final ResourceLocation TEXTURE = EssentialCraft5.id("textures/special/mru_beam.png");

    /** 透镜 balance → 颜色（原作 RenderMRURay） */
    private static final float[][] COLORS = {
            {0F, 1F, 1F},
            {1F, 0F, 0F},
            {0F, 0F, 1F},
            {1F, 0F, 1F},
            {0.3F, 0.3F, 0.3F}};

    private static final class Beam {
        final ResourceKey<Level> dimension;
        final Vec3 from;
        final Vec3 to;
        final int balance;
        final float fadeIn;
        /** 起点一段收成锥形：半径 = min(正常半径, taperSlope × (离起点的距离 + taperOffset))；0 = 不收 */
        final float taperSlope;
        final float taperOffset;
        final AABB bounds;
        int age;

        Beam(ResourceKey<Level> dimension, Vec3 from, Vec3 to, int balance, float fadeIn, float taperSlope, float taperOffset) {
            this.dimension = dimension;
            this.from = from;
            this.to = to;
            this.balance = balance;
            this.fadeIn = fadeIn;
            this.taperSlope = taperSlope;
            this.taperOffset = taperOffset;
            this.bounds = new AABB(from, to).inflate(BASE_RADIUS * 2F);
        }
    }

    private static final Deque<Beam> BEAMS = new ArrayDeque<>();

    private BeamEffects() {
    }

    /** 在世界坐标 from → to 之间生成一道光束，开头不渐显（展示厅用；见 {@link #spawn(Vec3, Vec3, int, float)}） */
    public static void spawn(Vec3 from, Vec3 to, int balance) {
        spawn(from, to, balance, 0F);
    }

    /**
     * 在世界坐标 from → to 之间生成一道光束（客户端主线程调；balance 夹到 0~4）。
     * 没有客户端世界、长度为 0、离镜头太远时什么都不做；满了挤掉最老的一条。
     *
     * @param fadeIn 从起点起多少格从全透明渐变到正常（0 = 不渐变；比光束还长时整条都在渐变里）
     */
    public static void spawn(Vec3 from, Vec3 to, int balance, float fadeIn) {
        spawn(from, to, balance, fadeIn, 0F, 0F);
    }

    /**
     * 同 {@link #spawn(Vec3, Vec3, int, float)}，起点一段收成锥形：离起点 s 格处的半径 = min(正常半径, {@code taperSlope} × (s + {@code taperOffset}))。
     * 开狙击镜时自己的光束用（{@code client/GunsClient.onBeamPacket}）：起点就在镜头前，倍率一放大，照常粗细的管子会糊满镜子下半边；
     * 取 taperOffset = 起点离镜头的距离时，锥形那段在屏幕上是一条等宽的细线。
     *
     * @param taperSlope  锥形的斜率（格 / 格，0 = 不收）
     * @param taperOffset 锥尖在起点后面多远（格）
     */
    public static void spawn(Vec3 from, Vec3 to, int balance, float fadeIn, float taperSlope, float taperOffset) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || from.distanceToSqr(to) < 1.0E-6D) {
            return;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        if (distanceToSegmentSqr(camera, from, to) > MAX_SPAWN_DISTANCE * MAX_SPAWN_DISTANCE) {
            return;
        }
        while (BEAMS.size() >= MAX_BEAMS) {
            BEAMS.pollFirst();
        }
        BEAMS.addLast(new Beam(level.dimension(), from, to, Mth.clamp(balance, 0, COLORS.length - 1), Math.max(0F, fadeIn),
                Math.max(0F, taperSlope), Math.max(0F, taperOffset)));
    }

    /** 现存的光束数（调试、展示厅用） */
    public static int count() {
        return BEAMS.size();
    }

    public static void clear() {
        BEAMS.clear();
    }

    /** 镜头离光束（线段）的距离平方为 distanceSqr 时的透明度系数：{@value #NEAR_FADE_OUTER} 格外 1，{@value #NEAR_FADE_INNER} 格内 0，中间线性 */
    static float nearFade(double distanceSqr) {
        if (distanceSqr >= NEAR_FADE_OUTER * NEAR_FADE_OUTER) {
            return 1F;
        }
        return (float) Mth.clamp((Math.sqrt(distanceSqr) - NEAR_FADE_INNER) / (NEAR_FADE_OUTER - NEAR_FADE_INNER), 0D, 1D);
    }

    /** 点到线段的最近距离的平方 */
    static double distanceToSegmentSqr(Vec3 point, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double lengthSqr = ab.lengthSqr();
        double t = lengthSqr <= 0D ? 0D : Mth.clamp(point.subtract(a).dot(ab) / lengthSqr, 0D, 1D);
        return point.distanceToSqr(a.add(ab.scale(t)));
    }

    @SubscribeEvent
    public static void onBeamClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || BEAMS.isEmpty() || Minecraft.getInstance().isPaused()) {
            return;
        }
        BEAMS.removeIf(beam -> ++beam.age >= LIFETIME);
    }

    @SubscribeEvent
    public static void onBeamRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || BEAMS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        float partialTick = event.getPartialTick();
        Frustum frustum = event.getFrustum();
        PoseStack poseStack = event.getPoseStack();
        Matrix4f pose = poseStack.last().pose();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = null;
        double maxSqr = MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE;
        for (Beam beam : BEAMS) {
            if (beam.dimension != level.dimension()) {
                continue;
            }
            double distanceSqr = distanceToSegmentSqr(camera, beam.from, beam.to);
            float near = nearFade(distanceSqr);
            if (distanceSqr > maxSqr || near <= 0F || (frustum != null && !frustum.isVisible(beam.bounds))) {
                continue;
            }
            if (consumer == null) {
                consumer = buffers.getBuffer(BeamRenderType.BEAM);
            }
            renderBeam(consumer, pose, camera, beam, partialTick, near);
        }
        if (consumer != null) {
            buffers.endBatch(BeamRenderType.BEAM);
        }
    }

    /**
     * 一条光束：8 个四边形围成的管子，顶点相对镜头。沿长度按「渐显结束处」「锥形结束处」切成至多三节，
     * 每节里透明度和半径都是线性的（渐显：起点全透明、到 fadeIn 处正常；锥形：半径 = min(正常, 斜率 × (s + 偏移))），所以分节画和逐点算一样。
     *
     * @param near 镜头太近时的透明度系数（{@link #nearFade}）
     */
    private static void renderBeam(VertexConsumer consumer, Matrix4f pose, Vec3 camera, Beam beam, float partialTick, float near) {
        float age = Math.min(LIFETIME, beam.age + partialTick);
        float alpha = Mth.clamp(1F - age / LIFETIME, 0F, 1F) * near;
        if (alpha <= 0F) {
            return;
        }
        float radius = BASE_RADIUS * (1F + age / LIFETIME);
        float[] color = COLORS[beam.balance];
        Vec3 axis = beam.to.subtract(beam.from);
        double length = axis.length();
        Vec3 direction = axis.scale(1D / length);
        // 和轴垂直的两个单位向量
        Vec3 helper = Math.abs(direction.y) < 0.99D ? new Vec3(0D, 1D, 0D) : new Vec3(1D, 0D, 0D);
        Vec3 u = direction.cross(helper).normalize();
        Vec3 v = direction.cross(u).normalize();
        Vec3 start = beam.from.subtract(camera);
        double fade = Math.min(beam.fadeIn, length);
        double coneEnd = beam.taperSlope > 0F ? Mth.clamp(radius / beam.taperSlope - beam.taperOffset, 0D, length) : 0D;
        double[] cuts = {0D, Math.min(fade, coneEnd), Math.max(fade, coneEnd), length};
        for (int i = 0; i + 1 < cuts.length; i++) {
            double s0 = cuts[i];
            double s1 = cuts[i + 1];
            if (s1 - s0 <= 1.0E-6D) {
                continue;
            }
            tube(consumer, pose, start.add(direction.scale(s0)), start.add(direction.scale(s1)), u, v,
                    radiusAt(beam, radius, s0), radiusAt(beam, radius, s1), color,
                    alpha * fadeAt(beam, s0), alpha * fadeAt(beam, s1), (float) (s0 / TEXTURE_LENGTH), (float) (s1 / TEXTURE_LENGTH));
        }
    }

    /** 离起点 s 格处的半径（锥形那段按斜率收细） */
    private static float radiusAt(Beam beam, float radius, double s) {
        if (beam.taperSlope <= 0F) {
            return radius;
        }
        return (float) Math.min(radius, beam.taperSlope * (s + beam.taperOffset));
    }

    /** 离起点 s 格处的渐显系数 */
    private static float fadeAt(Beam beam, double s) {
        if (beam.fadeIn <= 1.0E-6F || s >= beam.fadeIn) {
            return 1F;
        }
        return (float) (s / beam.fadeIn);
    }

    /** 管子的一节 start → end（u、v 是和轴垂直的两个单位向量），两头的半径、透明度、贴图 v 坐标分别给 */
    private static void tube(VertexConsumer consumer, Matrix4f pose, Vec3 start, Vec3 end, Vec3 u, Vec3 v, float startRadius, float endRadius,
                             float[] color, float startAlpha, float endAlpha, float vStart, float vEnd) {
        for (int i = 0; i < SIDES; i++) {
            double a0 = Math.PI * 2D * i / SIDES;
            double a1 = Math.PI * 2D * (i + 1) / SIDES;
            Vec3 r0 = u.scale(Math.cos(a0)).add(v.scale(Math.sin(a0)));
            Vec3 r1 = u.scale(Math.cos(a1)).add(v.scale(Math.sin(a1)));
            float u0 = (float) i / SIDES;
            float u1 = (float) (i + 1) / SIDES;
            vertex(consumer, pose, start.add(r0.scale(startRadius)), color, startAlpha, u0, vStart);
            vertex(consumer, pose, start.add(r1.scale(startRadius)), color, startAlpha, u1, vStart);
            vertex(consumer, pose, end.add(r1.scale(endRadius)), color, endAlpha, u1, vEnd);
            vertex(consumer, pose, end.add(r0.scale(endRadius)), color, endAlpha, u0, vEnd);
        }
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, Vec3 at, float[] color, float alpha, float u, float v) {
        consumer.vertex(pose, (float) at.x, (float) at.y, (float) at.z).color(color[0], color[1], color[2], alpha).uv(u, v).endVertex();
    }

    @SubscribeEvent
    public static void onBeamLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            BEAMS.clear();
        }
    }

    @SubscribeEvent
    public static void onBeamLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        BEAMS.clear();
    }

    /** 继承 RenderType 只是为了用 RenderStateShard 里 protected 的状态常量 */
    private static final class BeamRenderType extends RenderType {

        /** 带贴图的加色光束：SRC_ALPHA/ONE、不写深度（照常做深度测试，被方块挡住的部分不画）、不剔背面 */
        static final RenderType BEAM = create(EssentialCraft5.MODID + ":gun_beam",
                DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS, 256, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(POSITION_COLOR_TEX_SHADER)
                        .setTextureState(new TextureStateShard(TEXTURE, false, false))
                        .setTransparencyState(LIGHTNING_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));

        private BeamRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                               boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
        }
    }
}
