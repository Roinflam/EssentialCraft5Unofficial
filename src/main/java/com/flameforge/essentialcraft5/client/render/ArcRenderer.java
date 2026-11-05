package com.flameforge.essentialcraft5.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 把 {@link ArcEffect} 画出来（第四批框架 F4-14，只在客户端用）。线圈、反应堆的方块实体渲染器在 {@code render} 里直接调：
 * <pre>{@code
 * // 方块实体里：List<ArcEffect> arcs（客户端 tick 里 ArcEffect.tickAll(arcs)），坐标相对方块角
 * for (ArcEffect arc : be.arcs()) ArcRenderer.render(arc, poseStack, buffers, partialTick);
 * }</pre>
 * 世界坐标、没有方块实体的电弧（线圈基座的火花、打向远处目标的放电）交给 {@link ArcEffects#spawn}，它自己每帧画。
 *
 * <h2>画法</h2>
 * 原作用 GL_LINES 画两遍：1 像素宽、颜色 × 0.8 不透明，再 3 像素宽、白色 × 0.2（DummyCore {@code Lightning.render}）。1.20.1 的 {@link RenderType#lightning()}
 * 是四边形模式（加色混合 SRC_ALPHA / ONE，原版闪电和末影龙死亡光芒用的），所以每段画成<b>朝向镜头的细条</b>：宽度方向 = 线段方向 × 线段中点指向镜头的方向，
 * 镜头位置由当前姿态矩阵的逆推回（方块实体渲染器和世界坐标渲染都适用），顶点按「正面朝镜头」的顺序排，不怕背面剔除。
 * 两层：芯（样式颜色、不透明 0.8）宽 {@link #CORE_HALF_WIDTH} × 2，光晕（白、0.2）宽 {@link #GLOW_HALF_WIDTH} × 2，都乘样式的粗细倍数和淡出系数。
 * <p>
 * 开销：每段 2 个四边形；一道电弧最多 8 + 4 + 24 = 36 段 → 72 个四边形。渲染盒 / 距离由调用方管（方块实体的 {@code getRenderBoundingBox}
 * 要包住电弧，别用无限渲染盒：policy §1.2「客户端渲染」）。
 */
public final class ArcRenderer {

    /** 芯的半宽（格） */
    public static final float CORE_HALF_WIDTH = 0.015F;
    /** 光晕的半宽（格） */
    public static final float GLOW_HALF_WIDTH = 0.045F;
    private static final float CORE_ALPHA = 0.8F;
    private static final float GLOW_ALPHA = 0.2F;

    private ArcRenderer() {
    }

    /** 用 {@link RenderType#lightning()} 画一道电弧 */
    public static void render(ArcEffect arc, PoseStack poseStack, MultiBufferSource buffers, float partialTick) {
        render(arc, poseStack, buffers.getBuffer(RenderType.lightning()), partialTick);
    }

    /** 画进给定的顶点缓冲（格式要是 POSITION_COLOR、四边形模式，例如 {@link RenderType#lightning()}） */
    public static void render(ArcEffect arc, PoseStack poseStack, VertexConsumer consumer, float partialTick) {
        float fade = arc.alpha(partialTick);
        if (fade <= 0F || arc.segments().isEmpty()) {
            return;
        }
        Matrix4f matrix = poseStack.last().pose();
        // 镜头在观察空间的原点：用姿态矩阵的逆把它换回当前坐标系
        Vector3f camera = new Matrix4f(matrix).invert().transformPosition(new Vector3f());
        ArcEffect.Style style = arc.style();
        float width = style.width();
        for (ArcEffect.Segment segment : arc.segments()) {
            strip(consumer, matrix, camera, segment.from(), segment.to(), GLOW_HALF_WIDTH * width,
                    1F, 1F, 1F, GLOW_ALPHA * fade);
            strip(consumer, matrix, camera, segment.from(), segment.to(), CORE_HALF_WIDTH * width,
                    style.red(), style.green(), style.blue(), CORE_ALPHA * fade);
        }
    }

    /** 一段朝向镜头的细条（四个顶点：a+s、b+s、b−s、a−s，法线朝镜头） */
    private static void strip(VertexConsumer consumer, Matrix4f matrix, Vector3f camera, Vec3 a, Vec3 b, float halfWidth,
                              float r, float g, float bl, float alpha) {
        float dx = (float) (b.x - a.x);
        float dy = (float) (b.y - a.y);
        float dz = (float) (b.z - a.z);
        // 线段中点 → 镜头
        float wx = camera.x - (float) ((a.x + b.x) * 0.5D);
        float wy = camera.y - (float) ((a.y + b.y) * 0.5D);
        float wz = camera.z - (float) ((a.z + b.z) * 0.5D);
        // s = d × w（垂直于线段、也垂直于视线）
        float sx = dy * wz - dz * wy;
        float sy = dz * wx - dx * wz;
        float sz = dx * wy - dy * wx;
        float length = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (length < 1.0E-6F) {
            // 正对着线段看（或线段长度为 0）：这一段在屏幕上本来就是一个点
            return;
        }
        float scale = halfWidth / length;
        sx *= scale;
        sy *= scale;
        sz *= scale;
        float ax = (float) a.x, ay = (float) a.y, az = (float) a.z;
        float bx = (float) b.x, by = (float) b.y, bz = (float) b.z;
        consumer.vertex(matrix, ax + sx, ay + sy, az + sz).color(r, g, bl, alpha).endVertex();
        consumer.vertex(matrix, bx + sx, by + sy, bz + sz).color(r, g, bl, alpha).endVertex();
        consumer.vertex(matrix, bx - sx, by - sy, bz - sz).color(r, g, bl, alpha).endVertex();
        consumer.vertex(matrix, ax - sx, ay - sy, az - sz).color(r, g, bl, alpha).endVertex();
    }
}
