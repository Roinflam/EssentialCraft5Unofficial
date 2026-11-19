package com.flameforge.essentialcraft5.client.vfx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 一帧里画特效用的画笔：坐标一律传世界坐标（double），这里减掉镜头位置、乘镜头旋转再写进顶点，远离原点也不抖。
 * 由 {@link VfxEffects} 每帧建一支，交给各特效的 {@link VfxEffect#render}。
 * <h2>面片</h2>
 * <ul>
 *   <li>{@link #billboard}：正对镜头的方片（光点、星芒、符文、烟团）；</li>
 *   <li>{@link #strip}：两点之间、绕连线转到正对镜头的长条（光带、光柱、火花拖尾、电弧的一段）；</li>
 *   <li>{@link #ribbon}：一串点连成的、拐角平滑的长条（拖尾、电弧主干），相邻两节共用一对顶点；</li>
 *   <li>{@link #flat} / {@link #plane}：躺在给定平面上的方片（地面法阵、预警圈、冲击波）；</li>
 *   <li>{@link #annulus}：贴地的环带 / 扇形（圆面图案照常对得上），{@link #groundStrip}：贴地的长条，{@link #quad}：任意四点。</li>
 * </ul>
 * 每个面片带一个模式（{@link VfxMode}）和参数；完整画法把它们写进顶点给着色器，稳妥画法换算成 {@link VfxAtlas} 里那一格的贴图坐标。
 * {@link #distort} 系列只在 {@link Vfx#distortionAllowed()} 时画（否则什么都不做）。
 */
public final class VfxDraw {

    private final MultiBufferSource buffers;
    private final Matrix4f pose;
    private final Vec3 camera;
    private final Vector3f left;
    private final Vector3f up;
    private final boolean safe;
    private final boolean distortion;
    private final float partialTick;
    private final float time;
    @Nullable
    private VertexConsumer add;
    @Nullable
    private VertexConsumer alpha;
    @Nullable
    private VertexConsumer distortBuffer;
    private int quads;

    VfxDraw(MultiBufferSource buffers, Matrix4f pose, Camera camera, float partialTick, float time, boolean safe, boolean distortion) {
        this.buffers = buffers;
        this.pose = pose;
        this.camera = camera.getPosition();
        this.left = new Vector3f(camera.getLeftVector());
        this.up = new Vector3f(camera.getUpVector());
        this.partialTick = partialTick;
        this.time = time;
        this.safe = safe;
        this.distortion = distortion;
    }

    public Vec3 camera() {
        return camera;
    }

    public float partialTick() {
        return partialTick;
    }

    /** 游戏刻 + 帧内插值（秒数 = time / 20），给特效自己做按时间的动画 */
    public float time() {
        return time;
    }

    public boolean safe() {
        return safe;
    }

    public boolean distortionEnabled() {
        return distortion;
    }

    /** 这一帧画了多少个四边形 */
    public int quads() {
        return quads;
    }

    /** 把一个点往镜头方向挪 d 格（贴在实体身上的星芒、闪光要挪到身体前面，不然被模型挡掉一半） */
    public Vec3 towardCamera(Vec3 p, double d) {
        Vec3 to = camera.subtract(p);
        double len = to.length();
        return len < 1.0E-4D ? p : p.add(to.scale(Math.min(d, len * 0.5D) / len));
    }

    // ------------------------------------------------------------------ 面片

    /** 正对镜头的方片：中心、半边长、绕视线转的角度（弧度） */
    public void billboard(Vec3 c, float half, float rotation, int mode, int param, int argb) {
        billboard(c.x, c.y, c.z, half, half, rotation, mode, param, argb, false);
    }

    public void billboard(double x, double y, double z, float halfW, float halfH, float rotation, int mode, int param, int argb, boolean translucent) {
        float cos = (float) Math.cos(rotation);
        float sin = (float) Math.sin(rotation);
        // 方片的两条轴：镜头左、上转过 rotation
        float ax = (left.x * cos + up.x * sin) * halfW;
        float ay = (left.y * cos + up.y * sin) * halfW;
        float az = (left.z * cos + up.z * sin) * halfW;
        float bx = (up.x * cos - left.x * sin) * halfH;
        float by = (up.y * cos - left.y * sin) * halfH;
        float bz = (up.z * cos - left.z * sin) * halfH;
        VertexConsumer vc = translucent ? alphaBuffer() : addBuffer();
        // 局部坐标：+x 朝镜头左边的反方向（屏幕右），+y 朝上
        vertex(vc, x + ax - bx, y + ay - by, z + az - bz, -1F, -1F, mode, param, argb);
        vertex(vc, x - ax - bx, y - ay - by, z - az - bz, 1F, -1F, mode, param, argb);
        vertex(vc, x - ax + bx, y - ay + by, z - az + bz, 1F, 1F, mode, param, argb);
        vertex(vc, x + ax + bx, y + ay + by, z + az + bz, -1F, 1F, mode, param, argb);
        quads++;
    }

    /** 两点之间正对镜头的长条：两端各自的半宽、颜色，局部坐标 x 从 u0 到 u1 */
    public void strip(Vec3 a, Vec3 b, float halfA, float halfB, int argbA, int argbB, int mode, int param, float u0, float u1) {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double dz = b.z - a.z;
        double mx = camera.x - (a.x + b.x) * 0.5D;
        double my = camera.y - (a.y + b.y) * 0.5D;
        double mz = camera.z - (a.z + b.z) * 0.5D;
        double sx = dy * mz - dz * my;
        double sy = dz * mx - dx * mz;
        double sz = dx * my - dy * mx;
        double len = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (len < 1.0E-9D) {
            return;
        }
        sx /= len;
        sy /= len;
        sz /= len;
        VertexConsumer vc = addBuffer();
        vertex(vc, a.x - sx * halfA, a.y - sy * halfA, a.z - sz * halfA, u0, -1F, mode, param, argbA);
        vertex(vc, b.x - sx * halfB, b.y - sy * halfB, b.z - sz * halfB, u1, -1F, mode, param, argbB);
        vertex(vc, b.x + sx * halfB, b.y + sy * halfB, b.z + sz * halfB, u1, 1F, mode, param, argbB);
        vertex(vc, a.x + sx * halfA, a.y + sy * halfA, a.z + sz * halfA, u0, 1F, mode, param, argbA);
        quads++;
    }

    /**
     * 一串点连成的长条：{@code xyz} 依次是 n 个点的坐标（3n 个数），每个点自己的半宽、颜色、局部坐标 x。
     * 每个点的横向取「前后两点连线 × 指向镜头」，相邻两节共用顶点，拐弯处不裂缝、不叠亮。
     */
    public void ribbon(double[] xyz, int n, float[] half, int[] argb, float[] u, int mode, int param) {
        if (n < 2) {
            return;
        }
        VertexConsumer vc = addBuffer();
        double prevLx = 0, prevLy = 0, prevLz = 0, prevRx = 0, prevRy = 0, prevRz = 0;
        for (int i = 0; i < n; i++) {
            int ia = Math.max(0, i - 1) * 3;
            int ib = Math.min(n - 1, i + 1) * 3;
            double tx = xyz[ib] - xyz[ia];
            double ty = xyz[ib + 1] - xyz[ia + 1];
            double tz = xyz[ib + 2] - xyz[ia + 2];
            double px = xyz[i * 3];
            double py = xyz[i * 3 + 1];
            double pz = xyz[i * 3 + 2];
            double cx = camera.x - px;
            double cy = camera.y - py;
            double cz = camera.z - pz;
            double sx = ty * cz - tz * cy;
            double sy = tz * cx - tx * cz;
            double sz = tx * cy - ty * cx;
            double len = Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (len < 1.0E-9D) {
                sx = 0D;
                sy = 1D;
                sz = 0D;
            } else {
                sx /= len;
                sy /= len;
                sz /= len;
            }
            double h = half[i];
            double lx = px - sx * h, ly = py - sy * h, lz = pz - sz * h;
            double rx = px + sx * h, ry = py + sy * h, rz = pz + sz * h;
            if (i > 0) {
                vertex(vc, prevLx, prevLy, prevLz, u[i - 1], -1F, mode, param, argb[i - 1]);
                vertex(vc, lx, ly, lz, u[i], -1F, mode, param, argb[i]);
                vertex(vc, rx, ry, rz, u[i], 1F, mode, param, argb[i]);
                vertex(vc, prevRx, prevRy, prevRz, u[i - 1], 1F, mode, param, argb[i - 1]);
                quads++;
            }
            prevLx = lx;
            prevLy = ly;
            prevLz = lz;
            prevRx = rx;
            prevRy = ry;
            prevRz = rz;
        }
    }

    /** 水平躺着的方片（法阵、预警圈）：中心、半边长、绕竖轴转的角度（弧度），抬高 0.02 格免得和地面打架 */
    public void flat(Vec3 c, float half, float rotation, int mode, int param, int argb) {
        float cos = (float) Math.cos(rotation) * half;
        float sin = (float) Math.sin(rotation) * half;
        plane(c.x, c.y + 0.02D, c.z, cos, 0F, sin, -sin, 0F, cos, mode, param, argb, false);
    }

    /** 任意平面上的方片：中心 + 两条半轴（局部 +x、+y 的方向和长度） */
    public void plane(double x, double y, double z, float ax, float ay, float az, float bx, float by, float bz,
                      int mode, int param, int argb, boolean translucent) {
        VertexConsumer vc = translucent ? alphaBuffer() : addBuffer();
        vertex(vc, x - ax - bx, y - ay - by, z - az - bz, -1F, -1F, mode, param, argb);
        vertex(vc, x + ax - bx, y + ay - by, z + az - bz, 1F, -1F, mode, param, argb);
        vertex(vc, x + ax + bx, y + ay + by, z + az + bz, 1F, 1F, mode, param, argb);
        vertex(vc, x - ax + bx, y - ay + by, z - az + bz, -1F, 1F, mode, param, argb);
        quads++;
    }

    /** 任意四个点的面片（按顺序绕一圈），每个点自己的局部坐标；拼地面环带、扇形用 */
    public void quad(double x0, double y0, double z0, float u0, float v0, double x1, double y1, double z1, float u1, float v1,
                     double x2, double y2, double z2, float u2, float v2, double x3, double y3, double z3, float u3, float v3,
                     int mode, int param, int argb, boolean translucent) {
        VertexConsumer vc = translucent ? alphaBuffer() : addBuffer();
        vertex(vc, x0, y0, z0, u0, v0, mode, param, argb);
        vertex(vc, x1, y1, z1, u1, v1, mode, param, argb);
        vertex(vc, x2, y2, z2, u2, v2, mode, param, argb);
        vertex(vc, x3, y3, z3, u3, v3, mode, param, argb);
        quads++;
    }

    /**
     * 水平环带的一段：圆心 c、内外半径 r0~r1、角度 a0~a1（弧度，从 +x 往 +z 转），分 segments 段。
     * 局部坐标按「半径 uvRadius、圆心在 c 的圆面」算（同 {@link #flat} 不转时），所以圆面类图案（填充、熔岩、冲击波、细环）
     * 画在扇形、环带上和画在整个圆上一样对得上；uvRadius 取外半径就是「整圆里切出来的这一块」。
     */
    public void annulus(Vec3 c, float r0, float r1, float a0, float a1, int segments, float uvRadius, int mode, int param, int argb) {
        if (r1 <= r0 || segments < 1 || uvRadius <= 0F) {
            return;
        }
        double y = c.y + 0.02D;
        float inv = 1F / uvRadius;
        double step = (a1 - a0) / segments;
        double ca = Math.cos(a0), sa = Math.sin(a0);
        for (int i = 0; i < segments; i++) {
            double b = a0 + step * (i + 1);
            double cb = Math.cos(b), sb = Math.sin(b);
            quad(c.x + ca * r0, y, c.z + sa * r0, (float) (ca * r0 * inv), (float) (sa * r0 * inv),
                    c.x + ca * r1, y, c.z + sa * r1, (float) (ca * r1 * inv), (float) (sa * r1 * inv),
                    c.x + cb * r1, y, c.z + sb * r1, (float) (cb * r1 * inv), (float) (sb * r1 * inv),
                    c.x + cb * r0, y, c.z + sb * r0, (float) (cb * r0 * inv), (float) (sb * r0 * inv),
                    mode, param, argb, false);
            ca = cb;
            sa = sb;
        }
    }

    /** 贴地的水平长条（线预警的光带）：a → b，半宽 half，局部坐标 x 从 u0 到 u1、y 横向 −1~1；高度取两端各自的 y + 0.02 */
    public void groundStrip(Vec3 a, Vec3 b, float half, int argbA, int argbB, int mode, int param, float u0, float u1) {
        double dx = b.x - a.x;
        double dz = b.z - a.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-6D) {
            return;
        }
        double sx = -dz / len * half;
        double sz = dx / len * half;
        VertexConsumer vc = addBuffer();
        vertex(vc, a.x - sx, a.y + 0.02D, a.z - sz, u0, -1F, mode, param, argbA);
        vertex(vc, b.x - sx, b.y + 0.02D, b.z - sz, u1, -1F, mode, param, argbB);
        vertex(vc, b.x + sx, b.y + 0.02D, b.z + sz, u1, 1F, mode, param, argbB);
        vertex(vc, a.x + sx, a.y + 0.02D, a.z + sz, u0, 1F, mode, param, argbA);
        quads++;
    }

    // ------------------------------------------------------------------ 扭曲

    /** 正对镜头的扭曲方片：模式 0 冲击环（参数 = 环宽千分比）、1 热浪团；strength 约 0~1 */
    public void distortBillboard(Vec3 c, float half, int mode, int param, float strength) {
        if (!distortion || strength <= 0F) {
            return;
        }
        VertexConsumer vc = distortBuffer();
        int argb = Vfx.argb(0xFFFFFF, Math.min(1F, strength));
        float ax = left.x * half, ay = left.y * half, az = left.z * half;
        float bx = up.x * half, by = up.y * half, bz = up.z * half;
        rawVertex(vc, c.x + ax - bx, c.y + ay - by, c.z + az - bz, -1F, -1F, mode, param, argb);
        rawVertex(vc, c.x - ax - bx, c.y - ay - by, c.z - az - bz, 1F, -1F, mode, param, argb);
        rawVertex(vc, c.x - ax + bx, c.y - ay + by, c.z - az + bz, 1F, 1F, mode, param, argb);
        rawVertex(vc, c.x + ax + bx, c.y + ay + by, c.z + az + bz, -1F, 1F, mode, param, argb);
        quads++;
    }

    /** 水平躺着的扭曲方片（地面冲击波） */
    public void distortFlat(Vec3 c, float half, int mode, int param, float strength) {
        if (!distortion || strength <= 0F) {
            return;
        }
        VertexConsumer vc = distortBuffer();
        int argb = Vfx.argb(0xFFFFFF, Math.min(1F, strength));
        double y = c.y + 0.05D;
        rawVertex(vc, c.x - half, y, c.z - half, -1F, -1F, mode, param, argb);
        rawVertex(vc, c.x + half, y, c.z - half, 1F, -1F, mode, param, argb);
        rawVertex(vc, c.x + half, y, c.z + half, 1F, 1F, mode, param, argb);
        rawVertex(vc, c.x - half, y, c.z + half, -1F, 1F, mode, param, argb);
        quads++;
    }

    // ------------------------------------------------------------------ 顶点

    private VertexConsumer addBuffer() {
        if (add == null) {
            add = buffers.getBuffer(VfxRenderTypes.additive(safe));
        }
        return add;
    }

    private VertexConsumer alphaBuffer() {
        if (alpha == null) {
            alpha = buffers.getBuffer(VfxRenderTypes.translucent(safe));
        }
        return alpha;
    }

    private VertexConsumer distortBuffer() {
        if (distortBuffer == null) {
            distortBuffer = buffers.getBuffer(VfxRenderTypes.DISTORT);
        }
        return distortBuffer;
    }

    boolean hasDistortion() {
        return distortBuffer != null;
    }

    private void vertex(VertexConsumer vc, double x, double y, double z, float u, float v, int mode, int param, int argb) {
        float fx = (float) (x - camera.x);
        float fy = (float) (y - camera.y);
        float fz = (float) (z - camera.z);
        int a = (argb >>> 24) & 255;
        int r = (argb >> 16) & 255;
        int g = (argb >> 8) & 255;
        int b = argb & 255;
        if (safe) {
            float lu;
            if (VfxMode.isStrip(mode)) {
                // 长条沿长度方向的图案是均匀的（火花除外），取中间一列
                lu = mode == VfxMode.SPARK ? Math.max(0F, Math.min(1F, u)) : 0.5F;
            } else {
                lu = (u + 1F) * 0.5F;
            }
            float lv = (v + 1F) * 0.5F;
            vc.vertex(pose, fx, fy, fz).color(r, g, b, a).uv(VfxAtlas.u(mode, lu), VfxAtlas.v(mode, lv))
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(0F, 1F, 0F).endVertex();
        } else {
            vc.vertex(pose, fx, fy, fz).color(r, g, b, a).uv(u, v).uv2(mode, param).endVertex();
        }
    }

    private void rawVertex(VertexConsumer vc, double x, double y, double z, float u, float v, int mode, int param, int argb) {
        vc.vertex(pose, (float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z))
                .color((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >>> 24) & 255).uv(u, v).uv2(mode, param).endVertex();
    }

    /** 同 {@link #quad}，四个角各自的颜色（球壳按视角渐变的透明度要用），只画加色 */
    public void quad(double x0, double y0, double z0, float u0, float v0, int argb0, double x1, double y1, double z1, float u1, float v1, int argb1,
                     double x2, double y2, double z2, float u2, float v2, int argb2, double x3, double y3, double z3, float u3, float v3, int argb3,
                     int mode, int param) {
        VertexConsumer vc = addBuffer();
        vertex(vc, x0, y0, z0, u0, v0, mode, param, argb0);
        vertex(vc, x1, y1, z1, u1, v1, mode, param, argb1);
        vertex(vc, x2, y2, z2, u2, v2, mode, param, argb2);
        vertex(vc, x3, y3, z3, u3, v3, mode, param, argb3);
        quads++;
    }
}
