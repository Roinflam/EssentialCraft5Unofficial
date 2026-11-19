package com.flameforge.essentialcraft5.client.vfx.gear;

import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxEffect;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import com.flameforge.essentialcraft5.client.vfx.VfxPaths;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 饰品封禁场的穹顶：以穿戴者为心、半径同场的柔光球壳。没有硬边——每个顶点按视线和球面的夹角定透明度，
 * 正对着的地方几乎透明，掠过的边缘才亮，所以看上去是一层越往轮廓越浓的光膜；壳上的光纹顺着纬线慢慢流，
 * 隔几秒有一道柔光带从下往上扫过去，中档以上壳面偶尔爬一道小电弧（宅男套装是 MRU 科技，带电合适）。
 * <p>
 * 服务端每次评估发一次 {@code SEAL_FIELD}（{@link #refresh}），两次之间客户端自己续着画；收到 0 或者久没续上就收掉。
 * 第一次出现时从穿戴者身上张开，待机（没人在范围里）只画四成亮、不爬电。站在场里时整层压暗，只留贴地一圈渐隐的光墙，免得一直蒙着一层色。
 */
final class SealDomeFx implements VfxEffect {

    private static final VfxPlasma.Tint TINT = VfxPlasma.MRU;
    private static final int GROW_TICKS = 14;
    private static final int FADE_TICKS = 12;
    private static final int ARCS = 2;
    private static final int[] LON = {20, 32};
    private static final int[] LAT = {10, 16};
    /** 单位球的顶点：[档位][(纬 × (经 + 1) + 经) × 3]，纬从南极到北极 */
    private static final float[][] UNIT = new float[2][];

    static {
        for (int q = 0; q < 2; q++) {
            int lon = LON[q], lat = LAT[q];
            float[] v = new float[(lat + 1) * (lon + 1) * 3];
            for (int j = 0; j <= lat; j++) {
                double phi = -Math.PI / 2D + Math.PI * j / lat;
                for (int i = 0; i <= lon; i++) {
                    double theta = Mth.TWO_PI * i / lon;
                    int k = (j * (lon + 1) + i) * 3;
                    v[k] = (float) (Math.cos(phi) * Math.cos(theta));
                    v[k + 1] = (float) Math.sin(phi);
                    v[k + 2] = (float) (Math.cos(phi) * Math.sin(theta));
                }
            }
            UNIT[q] = v;
        }
    }

    private final GearAnchor wearer;
    private final int[] color = new int[(LAT[1] + 1) * (LON[1] + 1)];
    private final double[][] arcPath = new double[ARCS][VfxPaths.jaggedPoints(4) * 3];
    private final int[] arcPoints = new int[ARCS];
    private final int[] arcBorn = new int[ARCS];
    private final RandomSource random = RandomSource.create();
    private float radius;
    private boolean running;
    private int age;
    private int lastRefresh;
    private int gap = 40;
    private int fadeFrom = -1;

    SealDomeFx(GearAnchor wearer, float radius) {
        this.wearer = wearer;
        this.radius = radius;
        refresh(radius);
        lastRefresh = 0;
    }

    /** 服务端又说了一次「还开着」：param 半径（&gt; 0 运转，&lt; 0 待机），0 = 停了 */
    void refresh(float param) {
        if (param == 0F) {
            stop();
            return;
        }
        if (age > lastRefresh) {
            gap = Mth.clamp(age - lastRefresh, 10, 100);
        }
        lastRefresh = age;
        radius = Math.abs(param);
        running = param > 0F;
        fadeFrom = -1;
    }

    /** 只续命、不改半径和状态（演示用） */
    void keepAlive() {
        lastRefresh = age;
    }

    void stop() {
        if (fadeFrom < 0) {
            fadeFrom = age;
        }
    }

    boolean stopping() {
        return fadeFrom >= 0;
    }

    @Override
    public boolean tick() {
        age++;
        if (fadeFrom < 0 && (!wearer.alive() || age - lastRefresh > gap * 2 + 10)) {
            stop();
        }
        if (fadeFrom >= 0) {
            return age - fadeFrom < FADE_TICKS;
        }
        if (running && Vfx.tier() >= 1 && age % 3 == 0) {
            int slot = (age / 3) % ARCS;
            if (random.nextFloat() < 0.6F) {
                buildArc(slot);
            }
        }
        return true;
    }

    /** 在壳的上半球随便挑一段，沿球面爬一道 3~6 格的小电弧（路径隔几刻才换一次） */
    private void buildArc(int slot) {
        Vec3 c = wearer.pos(1F);
        double theta = random.nextDouble() * Mth.TWO_PI;
        double phi = Math.toRadians(8D + random.nextDouble() * 60D);
        double len = (3D + random.nextDouble() * 3D) / radius;
        double dirA = random.nextDouble() * Mth.TWO_PI;
        Vec3 a = onSphere(c, theta, phi);
        Vec3 b = onSphere(c, theta + Math.cos(dirA) * len / Math.max(0.2D, Math.cos(phi)), Mth.clamp(phi + Math.sin(dirA) * len, 0.05D, 1.4D));
        double[] path = arcPath[slot];
        int n = VfxPaths.jagged(random, a, b, 4, 0.3F, path);
        // 中点位移会把折线拉离球面，按到球面上
        for (int i = 0; i < n; i++) {
            double dx = path[i * 3] - c.x, dy = path[i * 3 + 1] - c.y, dz = path[i * 3 + 2] - c.z;
            double s = radius / Math.max(1.0E-4D, Math.sqrt(dx * dx + dy * dy + dz * dz));
            path[i * 3] = c.x + dx * s;
            path[i * 3 + 1] = c.y + dy * s;
            path[i * 3 + 2] = c.z + dz * s;
        }
        arcPoints[slot] = n;
        arcBorn[slot] = age;
    }

    private Vec3 onSphere(Vec3 c, double theta, double phi) {
        return new Vec3(c.x + Math.cos(phi) * Math.cos(theta) * radius, c.y + Math.sin(phi) * radius, c.z + Math.cos(phi) * Math.sin(theta) * radius);
    }

    @Override
    public void render(VfxDraw draw) {
        float pt = draw.partialTick();
        Vec3 c = wearer.pos(pt);
        Vec3 cam = draw.camera();
        double camDist = cam.distanceTo(c);
        if (camDist > radius + 96D) {
            return;
        }
        long start = GearVfx.beginTiming();
        float t = age + pt;
        float grow = Vfx.easeOutExpo(Math.min(1F, t / GROW_TICKS));
        float fade = fadeFrom < 0 ? 1F : Math.max(0F, 1F - (t - fadeFrom) / FADE_TICKS);
        float r = radius * (fadeFrom < 0 ? grow : 1F + 0.06F * (1F - fade));
        boolean inside = camDist < r;
        float strength = (running ? 1F : 0.4F) * fade * (inside ? 0.7F : 1F);
        // 张开的那一下整层亮一些
        strength *= 1F + 1.2F * (1F - grow);
        if (strength > 0.01F && r > 0.5F) {
            shell(draw, c, r, t, strength, inside);
        }
        if (running && fadeFrom < 0 && Vfx.tier() >= 1 && camDist < radius + 48D) {
            for (int s = 0; s < ARCS; s++) {
                float since = age - arcBorn[s] + pt;
                if (arcPoints[s] >= 2 && since < 6F) {
                    VfxPlasma.arc(draw, arcPath[s], arcPoints[s], Math.min(1F, since / 2F + 0.3F), 0.05F, VfxPlasma.strokeAlpha(since, 3F) * 0.7F,
                            s * 31 + arcBorn[s], TINT);
                }
            }
        }
        GearVfx.endTiming(start);
    }

    private void shell(VfxDraw draw, Vec3 c, float r, float t, float strength, boolean inside) {
        int q = Vfx.tier() >= 1 ? 1 : 0;
        int lon = LON[q], lat = LAT[q];
        float[] unit = UNIT[q];
        Vec3 cam = draw.camera();
        // 扫过去的光带：4 秒一趟，从南到北（纬度 -1 → 1）
        float scan = ((t / 80F) % 1F) * 2.6F - 1.3F;
        for (int j = 0; j <= lat; j++) {
            for (int i = 0; i <= lon; i++) {
                int k = j * (lon + 1) + i;
                double nx = unit[k * 3], ny = unit[k * 3 + 1], nz = unit[k * 3 + 2];
                double vx = c.x + nx * r - cam.x, vy = c.y + ny * r - cam.y, vz = c.z + nz * r - cam.z;
                double len = Math.sqrt(vx * vx + vy * vy + vz * vz);
                double facing = len < 1.0E-4D ? 1D : Math.abs((nx * vx + ny * vy + nz * vz) / len);
                float rim = (float) (1D - facing);
                float rim3 = rim * rim * rim;
                float band = (float) (ny - scan);
                float sweep = (float) Math.exp(-band * band * 40F);
                // 场里面看出去每处都差不多正对着，轮廓光出不来，改成贴地一圈渐隐的光墙
                float wall = inside ? (float) Math.exp(-(ny - 0.08D) * (ny - 0.08D) * 18D) : 0F;
                float a = strength * (0.14F + 0.8F * rim3 + 0.4F * sweep + 0.45F * wall);
                int rgb = Vfx.lerp(TINT.outer(), TINT.glow(), Mth.clamp(rim3 * 1.4F + sweep * 0.5F + wall * 0.6F, 0F, 1F)) & 0xFFFFFF;
                color[k] = Vfx.argb(rgb, Math.min(1F, a));
            }
        }
        // 光纹沿纬线流：局部坐标 x 取经度上走过的格数（按半径缩放，大小场纹路一样粗），y 固定在柔光带的半腰；
        // 噪声不是周期的，x 绕一圈接不上，所以按「离 0 号经线多远」折回来，整圈没有接缝
        float uScale = r * Mth.TWO_PI / lon * 0.15F;
        int halfLon = lon / 2;
        int seed = 211;
        for (int j = 0; j < lat; j++) {
            for (int i = 0; i < lon; i++) {
                int k0 = j * (lon + 1) + i;
                int k1 = k0 + 1;
                int k2 = k0 + lon + 2;
                int k3 = k0 + lon + 1;
                if (((color[k0] | color[k1] | color[k2] | color[k3]) >>> 24) < 3) {
                    continue;
                }
                float u0 = (halfLon - Math.abs(i - halfLon)) * uScale;
                float u1 = (halfLon - Math.abs(i + 1 - halfLon)) * uScale;
                draw.quad(c.x + unit[k0 * 3] * r, c.y + unit[k0 * 3 + 1] * r, c.z + unit[k0 * 3 + 2] * r, u0, 0.25F, color[k0],
                        c.x + unit[k1 * 3] * r, c.y + unit[k1 * 3 + 1] * r, c.z + unit[k1 * 3 + 2] * r, u1, 0.25F, color[k1],
                        c.x + unit[k2 * 3] * r, c.y + unit[k2 * 3 + 1] * r, c.z + unit[k2 * 3 + 2] * r, u1, 0.25F, color[k2],
                        c.x + unit[k3 * 3] * r, c.y + unit[k3 * 3 + 1] * r, c.z + unit[k3 * 3 + 2] * r, u0, 0.25F, color[k3],
                        VfxMode.BEAM, seed);
            }
        }
    }

    @Override
    public AABB bounds() {
        return wearer.box().inflate(radius + 1D);
    }
}
