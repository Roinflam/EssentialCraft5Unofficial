package com.flameforge.essentialcraft5.client.guide;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * 非矩形图元：六边形、粗线。{@code GuiGraphics} 只能画轴对齐矩形，这里直接往 {@link Tesselator} 里写三角形。
 *
 * <p>六边形徽记是尖顶、黑色底板、金色描边的节点造型。
 * 用法：{@link #begin} → 画若干图元 → {@link #end}，一批只产生一次绘制调用。
 */
public final class Shapes {

    /** 单位尖顶六边形（顶点朝上下）六个角的余弦 / 正弦，预先算好。 */
    private static final float[] HEX_COS = new float[6];
    private static final float[] HEX_SIN = new float[6];

    static {
        for (int i = 0; i < 6; i++) {
            double angle = Math.PI / 6.0 + Math.PI / 3.0 * i;
            HEX_COS[i] = (float) Math.cos(angle);
            HEX_SIN[i] = (float) Math.sin(angle);
        }
    }

    private Shapes() {
    }

    /**
     * 开始一批图元。
     *
     * <p>会先 {@code flush} 掉 GuiGraphics 里攒着的矩形和文字，否则它们会在这批三角形之后才提交，层次就反了。
     */
    public static BufferBuilder begin(GuiGraphics graphics) {
        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // 顶点按屏幕坐标顺时针排，GUI 投影下会被背面剔除整个吃掉
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        return buffer;
    }

    public static void end() {
        Tesselator.getInstance().end();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    public static float[][] hexagon(float cx, float cy, float radius) {
        float[][] points = new float[6][2];
        for (int i = 0; i < 6; i++) {
            points[i][0] = cx + HEX_COS[i] * radius;
            points[i][1] = cy + HEX_SIN[i] * radius;
        }
        return points;
    }

    /** 扇形三角化填充凸多边形。 */
    public static void polygon(BufferBuilder buffer, Matrix4f matrix, float[][] points, int argb) {
        if (points.length < 3 || (argb >>> 24) == 0) {
            return;
        }
        for (int i = 1; i < points.length - 1; i++) {
            vertex(buffer, matrix, points[0][0], points[0][1], argb);
            vertex(buffer, matrix, points[i][0], points[i][1], argb);
            vertex(buffer, matrix, points[i + 1][0], points[i + 1][1], argb);
        }
    }

    /**
     * 只填充多边形在 {@code top} 以下的部分（Sutherland–Hodgman 单边裁剪）。
     * 用来做「进度从底部往上把六边形填满」。
     */
    public static void polygonBelow(BufferBuilder buffer, Matrix4f matrix, float[][] points, float top, int argb) {
        List<float[]> output = new ArrayList<>(points.length + 2);
        for (int i = 0; i < points.length; i++) {
            float[] current = points[i];
            float[] next = points[(i + 1) % points.length];
            boolean currentIn = current[1] >= top;
            boolean nextIn = next[1] >= top;
            if (currentIn) {
                output.add(current);
            }
            if (currentIn != nextIn) {
                float dy = next[1] - current[1];
                if (Math.abs(dy) > 1.0E-5f) {
                    float t = (top - current[1]) / dy;
                    output.add(new float[]{current[0] + (next[0] - current[0]) * t, top});
                }
            }
        }
        if (output.size() >= 3) {
            polygon(buffer, matrix, output.toArray(new float[0][]), argb);
        }
    }

    public static void outline(BufferBuilder buffer, Matrix4f matrix, float[][] points, float width, int argb) {
        for (int i = 0; i < points.length; i++) {
            float[] a = points[i];
            float[] b = points[(i + 1) % points.length];
            line(buffer, matrix, a[0], a[1], b[0], b[1], width, argb);
        }
    }

    public static void line(BufferBuilder buffer, Matrix4f matrix, float x1, float y1, float x2, float y2, float width, int argb) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1.0E-4f || (argb >>> 24) == 0) {
            return;
        }
        // 两端不外延：外延能补上拐角的小缺口，但半透明淡入时拐角会叠两层、亮出一个点，更扎眼
        float nx = -dy / length * (width / 2f);
        float ny = dx / length * (width / 2f);
        vertex(buffer, matrix, x1 + nx, y1 + ny, argb);
        vertex(buffer, matrix, x1 - nx, y1 - ny, argb);
        vertex(buffer, matrix, x2 - nx, y2 - ny, argb);
        vertex(buffer, matrix, x1 + nx, y1 + ny, argb);
        vertex(buffer, matrix, x2 - nx, y2 - ny, argb);
        vertex(buffer, matrix, x2 + nx, y2 + ny, argb);
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, float x, float y, int argb) {
        buffer.vertex(matrix, x, y, 0f)
                .color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF)
                .endVertex();
    }
}
