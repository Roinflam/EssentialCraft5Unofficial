package com.flameforge.essentialcraft5.client.vfx;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * 等离子画风的共用画法：武器、机器、首领招式、装备的特效都从这里取层次和配色，换一处全模组一起变。
 * <p>
 * 层次从里到外：白热细芯 → 等离子芯（两股细丝在流）→ 辉光 → 外晕；命中处是爆点放射光 + 双冲击波 + 地面电流 + 火花 / 余辉。
 * 配色用 {@link Tint}：{@link #MRU}（模组本色：粉白、品红、蓝紫外晕）和 {@link #DANGER}（首领招式：暖白、危险红、暗红外晕），
 * 服务端只给一个颜色时用 {@link #tint(int)} 现配一套。
 * <p>
 * 部件：电弧 {@link #arc} / {@link #forkedArc}、能量团 {@link #pulse}、电晕 {@link #corona}、爆点 {@link #burst}、冲击波 {@link #shockwave}、
 * 地面电流 {@link #groundCrawl}、圈边电弧 {@link #rimArcs}、火花 {@link #sparks}、余辉 {@link #embers}；光束 {@link #beam}、拖尾 {@link #trail}；
 * 首领预警：地上不画圈，范围靠贴地裂纹网标（{@link VfxCracks}，组件在 {@code client/vfx/warn}），快落下时的呼吸节奏 {@link #warningPulse}，
 * 安全区的天光 {@link #safeLight} 和光雨 {@link #lightRain}。
 * <p>
 * 质量档位怎么减负：粒子数一律走 {@link Vfx#count}（低档 0.35 倍），分叉数走 {@link Vfx#detail}；
 * 结构档 {@link Vfx#tier()} 为 1 时去掉外晕、二级分叉，为 0 时再去掉等离子细丝层、地面电流、圈边电弧、第二道冲击波，只留芯 + 一层辉光。
 * <p>
 * 带 {@code RandomSource} 参数的方法每帧都要传一个「同一个种子新建」的随机源（形状才不会每帧乱跳）；想让它噼啪变形就隔几刻换种子。
 * 这里的方法只画、只放客户端粒子，不碰世界。
 */
public final class VfxPlasma {

    /** 一套配色：芯（几乎白）、内层（等离子细丝）、辉光、外晕 */
    public record Tint(int core, int inner, int glow, int outer) {
    }

    /** 模组本色（MRU 紫） */
    public static final Tint MRU = new Tint(VfxPalette.WHITE_HOT, VfxPalette.PINK, VfxPalette.MAGENTA, VfxPalette.ORB_BLUE);
    /** 首领招式（危险红，芯偏暖） */
    public static final Tint DANGER = new Tint(0xFFF4E8, 0xFFB08A, VfxPalette.DANGER, 0x8A1030);
    /**
     * 日光金（天灾之心日阵、四灾归心）：金色本身当等离子层、辉光往橙里压。按通用算法配的话金色往白里调两次，
     * 几根光柱一叠就成了一片白，看不出是太阳
     */
    public static final Tint SOLAR = new Tint(0xFFF6DC, VfxPalette.GOLD, 0xFFA43A, 0x8A4A12);
    /**
     * 毒雾的病态黄绿（天灾之心毒雾相、腐蚀吐息）：辉光取本体发光层的橄榄、压低饱和度。按通用算法提亮的话 #A8C83A 会变成荧光黄绿，
     * 比雾团本身艳一倍多
     */
    public static final Tint FUME = new Tint(0xF2F6DC, 0xC8D67A, 0x9CB040, 0x3E4A18);
    /** 毒雾相的颜色（= 腐蚀吐息预警色），{@link #tint} 认到它返回 {@link #FUME} */
    private static final int FUME_RGB = 0xA8C83A;

    private static final double[] BRANCH = new double[VfxPaths.jaggedPoints(3) * 3];
    private static final double[] TWIG = new double[VfxPaths.jaggedPoints(2) * 3];
    private static final double[] CRAWL = new double[VfxPaths.jaggedPoints(3) * 3];
    private static final float[] TRAIL_HALF = new float[VfxPaths.MAX_POINTS];
    private static final int[] TRAIL_ARGB = new int[VfxPaths.MAX_POINTS];
    private static final float[] TRAIL_U = new float[VfxPaths.MAX_POINTS];
    private static final double[] SHARD = new double[VfxPaths.MAX_POINTS * 3];

    private VfxPlasma() {
    }

    // ------------------------------------------------------------------ 配色

    /**
     * 按一个主色配一套层次（服务端发来的预警只带一个颜色）：主色当辉光，往白里调出等离子层和芯，往暗里调出外晕。
     * 太暗的颜色（深紫、土褐这类相色）先等比提亮到最亮的通道 0xE0，加色混合下才看得见。危险红、品红、金色、毒雾黄绿原样返回
     * {@link #DANGER}、{@link #MRU}、{@link #SOLAR}、{@link #FUME}。
     */
    public static Tint tint(int rgb) {
        rgb &= 0xFFFFFF;
        if (rgb == VfxPalette.DANGER) {
            return DANGER;
        }
        if (rgb == VfxPalette.GOLD) {
            return SOLAR;
        }
        if (rgb == VfxPalette.MAGENTA) {
            return MRU;
        }
        if (rgb == FUME_RGB) {
            return FUME;
        }
        int r = (rgb >> 16) & 255;
        int g = (rgb >> 8) & 255;
        int b = rgb & 255;
        int max = Math.max(r, Math.max(g, b));
        if (max == 0) {
            r = g = b = 0xE0;
        } else if (max < 0xE0) {
            float s = 0xE0 / (float) max;
            r = Math.min(255, Math.round(r * s));
            g = Math.min(255, Math.round(g * s));
            b = Math.min(255, Math.round(b * s));
        }
        int glow = (r << 16) | (g << 8) | b;
        return new Tint(Vfx.lerp(glow, 0xFFFFFF, 0.88F) & 0xFFFFFF, Vfx.lerp(glow, 0xFFFFFF, 0.45F) & 0xFFFFFF, glow,
                Vfx.lerp(glow, 0x000000, 0.5F) & 0xFFFFFF);
    }

    // ------------------------------------------------------------------ 光束、拖尾

    /**
     * 等离子光束（光柱、火柱、枪械光束共用）：外晕、流动辉光、等离子芯、白热细芯四层，a → b。
     *
     * @param width   辉光的半宽（格），其它层按它的倍数
     * @param fadeEnd b 端是否淡到 0（往天上散的光柱要；打到目标的光束不要）
     * @param seed    流动噪声、等离子细丝的种子
     */
    public static void beam(VfxDraw draw, Vec3 a, Vec3 b, float width, float alpha, boolean fadeEnd, int seed, Tint tint) {
        if (alpha <= 0.01F || width <= 0F) {
            return;
        }
        float len = (float) a.distanceTo(b);
        float tail = fadeEnd ? 0F : 1F;
        int tier = Vfx.tier();
        if (tier >= 2) {
            draw.strip(a, b, width * 1.75F, width * (fadeEnd ? 1.2F : 1.75F), Vfx.argb(tint.outer(), 0.22F * alpha),
                    Vfx.argb(tint.outer(), 0.22F * alpha * tail), VfxMode.STRIP, 30, 0F, 1F);
        }
        draw.strip(a, b, width, width * (fadeEnd ? 0.7F : 1F), Vfx.argb(tint.glow(), 0.6F * alpha), Vfx.argb(tint.glow(), 0.6F * alpha * tail),
                VfxMode.BEAM, seed & 1023, 0F, len);
        if (tier >= 1) {
            draw.strip(a, b, width * 0.45F, width * 0.4F, Vfx.argb(tint.inner(), alpha), Vfx.argb(tint.inner(), alpha * tail),
                    VfxMode.PLASMA, (seed * 7 + 3) & 1023, 0F, len);
        }
        draw.strip(a, b, width * 0.15F, width * 0.12F, Vfx.argb(tint.core(), alpha), Vfx.argb(tint.core(), alpha * tail),
                VfxMode.STRIP, 650, 0F, 1F);
    }

    /**
     * 拖尾：一串点（第 0 个是头）画成往尾巴收细、变淡的四层长条（外晕、辉光、等离子芯、白热细芯）。
     * 局部坐标 x 用沿路径走过的格数，细丝顺着拖尾流。
     *
     * @param width 头部辉光的半宽（格）
     */
    public static void trail(VfxDraw draw, double[] xyz, int n, float width, float alpha, int seed, Tint tint) {
        n = Math.min(n, VfxPaths.MAX_POINTS);
        if (n < 2 || alpha <= 0.01F) {
            return;
        }
        int tier = Vfx.tier();
        if (tier >= 2) {
            trailLayer(draw, xyz, n, width * 1.8F, tint.outer(), 0.18F * alpha, VfxMode.STRIP, 20);
        }
        trailLayer(draw, xyz, n, width, tint.glow(), 0.45F * alpha, VfxMode.STRIP, 70);
        if (tier >= 1) {
            trailLayer(draw, xyz, n, width * 0.4F, tint.inner(), alpha, VfxMode.PLASMA, seed & 1023);
        }
        trailLayer(draw, xyz, n, width * 0.12F, tint.core(), alpha, VfxMode.STRIP, 650);
    }

    private static void trailLayer(VfxDraw draw, double[] xyz, int n, float width, int rgb, float alpha, int mode, int param) {
        double walked = 0D;
        for (int i = 0; i < n; i++) {
            float k = 1F - i / (float) (n - 1);
            TRAIL_HALF[i] = width * (0.15F + 0.85F * k);
            TRAIL_ARGB[i] = Vfx.argb(rgb, alpha * k * k);
            if (i > 0) {
                double dx = xyz[i * 3] - xyz[i * 3 - 3];
                double dy = xyz[i * 3 + 1] - xyz[i * 3 - 2];
                double dz = xyz[i * 3 + 2] - xyz[i * 3 - 1];
                walked += Math.sqrt(dx * dx + dy * dy + dz * dz);
            }
            TRAIL_U[i] = (float) walked;
        }
        draw.ribbon(xyz, n, TRAIL_HALF, TRAIL_ARGB, TRAIL_U, mode, param);
    }

    // ------------------------------------------------------------------ 预警

    /**
     * 首领预警的呼吸节奏（left = 还剩几刻）：最后 10 刻越闪越快，最后 3 刻整体压到四成（憋一口气再落，「一暗就落」）。
     * 只改亮度，不改范围标记的位置和大小。
     */
    public static float warningPulse(float left, float time) {
        if (left < 3F) {
            return 0.4F + 0.2F * (left / 3F);
        }
        if (left < 10F) {
            float f = (10F - left) / 7F;
            return 0.85F + 0.15F * Mth.sin(time * (1.6F + 2.4F * f));
        }
        return 1F;
    }

    /** 落进一块地的光雨：半径 radius 以内、离地 h0~h1 生出，慢慢落下（rise 为正时反过来往上飘），落地前淡掉 */
    public static void lightRain(Vec3 c, float radius, double h0, double h1, float designed, float rise, Tint tint) {
        if (!Vfx.visibleAt(c)) {
            return;
        }
        RandomSource random = Vfx.random();
        int n = Vfx.count(designed, c);
        for (int i = 0; i < n; i++) {
            double a = random.nextDouble() * Mth.TWO_PI;
            double r = Math.sqrt(random.nextDouble()) * radius;
            double y = rise > 0F ? c.y + 0.2D + random.nextDouble() * 0.8D : c.y + h0 + random.nextDouble() * (h1 - h0);
            VfxParticles.spawn(c.x + Math.cos(a) * r, y, c.z + Math.sin(a) * r, 0D, rise * (0.7D + 0.6D * random.nextDouble()), 0D,
                    30 + random.nextInt(20), 0.05F + random.nextFloat() * 0.03F, 0.02F, Vfx.argb(tint.core(), 0.9F), Vfx.argb(tint.glow(), 0F),
                    VfxMode.DOT, 450, 1F, 0F, 0F, 0F);
        }
    }

    /**
     * 安全区的天光：count 根细的光柱落在圆心 radius 以内（随机位置、慢慢飘，不排在边上），往上淡掉；
     * 远看是「那块地被照亮了」，不是一个圈。
     */
    public static void safeLight(VfxDraw draw, long seed, Vec3 c, float radius, int count, float height, float time, float alpha, Tint tint) {
        if (alpha <= 0.01F) {
            return;
        }
        RandomSource random = RandomSource.create(seed);
        int k = Math.max(2, Math.round(count * Vfx.detail(c)));
        for (int i = 0; i < k; i++) {
            double a = random.nextDouble() * Mth.TWO_PI + time * 0.004D * (random.nextBoolean() ? 1D : -1D);
            double r = Math.sqrt(random.nextDouble()) * radius;
            float flick = 0.75F + 0.25F * Mth.sin(time * 0.15F + i * 1.7F);
            Vec3 foot = new Vec3(c.x + Math.cos(a) * r, c.y + 0.05D, c.z + Math.sin(a) * r);
            beam(draw, foot, foot.add(0D, height * (0.7F + 0.3F * random.nextFloat()), 0D), 0.3F, 0.3F * flick * alpha, true, i * 37, tint);
            draw.flat(foot, 0.9F, 0F, VfxMode.DOT, 0, Vfx.argb(tint.glow(), 0.25F * flick * alpha));
        }
    }

    // ------------------------------------------------------------------ 电弧

    /** 一道完整的电弧，见 {@link #arc(VfxDraw, double[], int, float, float, float, int, Tint)} */
    public static void arc(VfxDraw draw, double[] path, int n, float width, float alpha, int seed, Tint tint) {
        arc(draw, path, n, 1F, width, alpha, seed, tint);
    }

    /**
     * 一道电弧（路径已经生成好），只画到 head（0~1，出手那一刻从起点冲出去用）：窄内晕、等离子细丝、贴芯的热白、硬边白芯。
     * 光的主体是够粗够亮的白芯；不铺宽的半透明辉光面片——那种面片远看糊成一条扁平的色带，闪电反而没了锐利的边。
     *
     * @param width 等离子芯的半宽（格），其它层按它的倍数
     * @param seed  等离子细丝的种子
     */
    public static void arc(VfxDraw draw, double[] path, int n, float head, float width, float alpha, int seed, Tint tint) {
        if (alpha <= 0.01F || n < 2 || head <= 0.01F) {
            return;
        }
        float to = Math.min(1F, head);
        int tier = Vfx.tier();
        VfxPaths.drawPath(draw, path, n, 0F, to, width * 3F, 0.1F, Vfx.argb(tint.glow(), 0.42F * alpha), VfxMode.STRIP, 70);
        if (tier >= 1) {
            VfxPaths.drawPath(draw, path, n, 0F, to, width * 1.2F, 0.06F, Vfx.argb(tint.inner(), alpha), VfxMode.PLASMA, seed & 1023);
            VfxPaths.drawPath(draw, path, n, 0F, to, width * 1.5F, 0.05F, Vfx.argb(tint.core(), 0.5F * alpha), VfxMode.STRIP, 300);
        }
        VfxPaths.drawPath(draw, path, n, 0F, to, width * 0.75F, 0.04F, Vfx.argb(tint.core(), alpha), VfxMode.STRIP, 650);
    }

    /**
     * 散架的电弧（收掉时用，比整条一起淡掉更「噼啪」）：路径每 run 个点断成一段、每段两头收尖，drop 的比例整段不亮，
     * 每段沿垂直于主干的随机方向挪开 jump 格（随时间加大就是碎电往外跳）。种子定断法和跳的方向。
     */
    public static void shatteredArc(VfxDraw draw, double[] path, int n, int run, float drop, float jump, float width, float alpha, int seed,
                                    Tint tint) {
        n = Math.min(n, VfxPaths.MAX_POINTS);
        if (alpha <= 0.01F || n < 3 || run < 2) {
            return;
        }
        Vec3 axis = new Vec3(path[(n - 1) * 3] - path[0], path[(n - 1) * 3 + 1] - path[1], path[(n - 1) * 3 + 2] - path[2]);
        axis = axis.lengthSqr() < 1.0E-8D ? new Vec3(0D, 1D, 0D) : axis.normalize();
        Vec3 e1 = axis.cross(Math.abs(axis.y) < 0.9D ? new Vec3(0D, 1D, 0D) : new Vec3(1D, 0D, 0D)).normalize();
        Vec3 e2 = axis.cross(e1);
        RandomSource r = RandomSource.create(seed);
        boolean glow = Vfx.tier() >= 1;
        for (int i0 = 0; i0 < n - 1; i0 += run) {
            int m = Math.min(n - 1, i0 + run) - i0 + 1;
            boolean off = r.nextFloat() < drop;
            double ph = r.nextDouble() * Mth.TWO_PI;
            double j = jump * (0.4D + 0.6D * r.nextDouble());
            if (off || m < 2) {
                continue;
            }
            double ox = (e1.x * Math.cos(ph) + e2.x * Math.sin(ph)) * j;
            double oy = (e1.y * Math.cos(ph) + e2.y * Math.sin(ph)) * j;
            double oz = (e1.z * Math.cos(ph) + e2.z * Math.sin(ph)) * j;
            for (int k = 0; k < m; k++) {
                SHARD[k * 3] = path[(i0 + k) * 3] + ox;
                SHARD[k * 3 + 1] = path[(i0 + k) * 3 + 1] + oy;
                SHARD[k * 3 + 2] = path[(i0 + k) * 3 + 2] + oz;
            }
            if (glow) {
                VfxPaths.drawPath(draw, SHARD, m, 0F, 1F, width * 2.6F, 0.35F, Vfx.argb(tint.glow(), 0.4F * alpha), VfxMode.STRIP, 70);
            }
            VfxPaths.drawPath(draw, SHARD, m, 0F, 1F, width * 0.7F, 0.35F, Vfx.argb(tint.core(), alpha), VfxMode.STRIP, 650);
        }
    }

    /**
     * 带分叉的电弧：主干 + 若干一级分叉（顺着主干前进的方向斜着岔出去，越靠近起点越长），高档每个一级分叉再带一根细枝；分叉末端一个小光点。
     *
     * @param branches  近处、高档时的一级分叉数（远了、档低了按 {@link Vfx#detail} 少画）
     * @param branchLen 一级分叉的大概长度（格）
     */
    public static void forkedArc(VfxDraw draw, RandomSource random, double[] path, int n, float width, float alpha, int seed,
                                 int branches, float branchLen, Tint tint) {
        forkedArc(draw, random, path, n, 1F, width, alpha, seed, branches, branchLen, tint);
    }

    /** 同上，只画到 head（0~1）：主干冲到哪，分叉就只在那之前长出来 */
    public static void forkedArc(VfxDraw draw, RandomSource random, double[] path, int n, float head, float width, float alpha, int seed,
                                 int branches, float branchLen, Tint tint) {
        arc(draw, path, n, head, width, alpha, seed, tint);
        if (alpha <= 0.05F || n < 2) {
            return;
        }
        int tier = Vfx.tier();
        int count = Math.max(1, Math.round(branches * Vfx.detail(VfxPaths.at(path, n, 0.5D))));
        for (int b = 0; b < count; b++) {
            double f = 0.08D + 0.84D * random.nextDouble();
            // 还没冲到的分叉照样算（随机数照样取），只是不画：冲到头以后形状和整条画出来时一样
            boolean show = f <= head;
            Vec3 from = VfxPaths.at(path, n, f);
            Vec3 ahead = VfxPaths.at(path, n, Math.min(1D, f + 0.08D)).subtract(from);
            Vec3 dir = (ahead.lengthSqr() < 1.0E-8D ? randomDir(random) : ahead.normalize()).add(randomDir(random).scale(1.1D)).normalize();
            double len = branchLen * (0.45D + random.nextDouble() * 0.75D) * (1.1D - f * 0.4D);
            Vec3 to = from.add(dir.scale(len));
            int bn = VfxPaths.jagged(random, from, to, 3, 0.28F, BRANCH);
            float ba = alpha * (0.65F + 0.35F * random.nextFloat());
            if (show) {
                // 分叉也是白芯为主（细而亮的毛刺），外面只包一层窄辉光
                if (tier >= 1) {
                    VfxPaths.drawPath(draw, BRANCH, bn, 0F, 1F, width * 1.4F, 0.4F, Vfx.argb(tint.glow(), 0.3F * ba), VfxMode.STRIP, 70);
                }
                VfxPaths.drawPath(draw, BRANCH, bn, 0F, 1F, width * 0.5F, 0.5F, Vfx.argb(tint.core(), ba), VfxMode.STRIP, 520);
                draw.billboard(to, 0.07F + width * 0.6F, 0F, VfxMode.DOT, 600, Vfx.argb(tint.inner(), 0.6F * ba));
            }
            if (tier >= 2) {
                Vec3 twigFrom = VfxPaths.at(BRANCH, bn, 0.3D + 0.4D * random.nextDouble());
                Vec3 twigDir = dir.add(randomDir(random).scale(1.3D)).normalize();
                int tn = VfxPaths.jagged(random, twigFrom, twigFrom.add(twigDir.scale(len * 0.45D)), 2, 0.3F, TWIG);
                if (show) {
                    VfxPaths.drawPath(draw, TWIG, tn, 0F, 1F, width * 0.3F, 0.5F, Vfx.argb(tint.core(), 0.7F * ba), VfxMode.STRIP, 520);
                }
            }
        }
    }

    /** 沿电弧跑的一团能量（f = 0~1 在路径上的位置），后面拖一小段亮尾 */
    public static void pulse(VfxDraw draw, double[] path, int n, float f, float size, float alpha, Tint tint) {
        if (f < 0F || f > 1F || alpha <= 0.01F || n < 2) {
            return;
        }
        Vec3 p = VfxPaths.at(path, n, f);
        draw.billboard(p, size, 0F, VfxMode.DOT, 700, Vfx.argb(tint.core(), alpha));
        draw.billboard(p, size * 2.6F, 0F, VfxMode.DOT, 0, Vfx.argb(tint.glow(), 0.45F * alpha));
        float from = Math.max(0F, f - 0.14F);
        if (f - from > 0.02F) {
            VfxPaths.drawPath(draw, path, n, from, f, size * 0.7F, 0.5F, Vfx.argb(tint.core(), 0.8F * alpha), VfxMode.STRIP, 500);
        }
    }

    /** 电晕：从 c 往四周随机方向跳几道短电弧（长约 reach 格） */
    public static void corona(VfxDraw draw, RandomSource random, Vec3 c, float reach, int count, float width, float alpha, Tint tint) {
        if (alpha <= 0.02F) {
            return;
        }
        int k = Math.max(1, Math.round(count * Vfx.detail(c)));
        for (int i = 0; i < k; i++) {
            Vec3 to = c.add(randomDir(random).scale(reach * (0.5D + 0.5D * random.nextDouble())));
            int n = VfxPaths.jagged(random, c, to, 3, 0.25F, BRANCH);
            VfxPaths.drawPath(draw, BRANCH, n, 0F, 1F, width * 2.4F, 0.4F, Vfx.argb(tint.glow(), 0.35F * alpha), VfxMode.STRIP, 70);
            VfxPaths.drawPath(draw, BRANCH, n, 0F, 1F, width * 0.5F, 0.45F, Vfx.argb(tint.inner(), alpha), VfxMode.STRIP, 480);
        }
    }

    /** 真闪电那样的亮度：0~2 刻满、2~3 刻暗一下（回击前的空档）、3~6 刻再亮、之后 fade 刻淡掉 */
    public static float strokeAlpha(float t, float fade) {
        if (t < 0F) {
            return 0F;
        }
        if (t < 2F) {
            return 1F;
        }
        if (t < 3F) {
            return 0.3F;
        }
        if (t < 6F) {
            return 0.9F;
        }
        return Math.max(0F, 1F - (t - 6F) / fade) * 0.85F;
    }

    // ------------------------------------------------------------------ 命中、炸开

    /**
     * 命中爆点：放射光线 + 星芒 + 两层光晕。t = 命中后过了几刻（含帧内插值），life = 多久散完。
     * 贴在实体身上的先用 {@link VfxDraw#towardCamera} 挪到身前，不然被模型挡掉一半。
     */
    public static void burst(VfxDraw draw, Vec3 c, float size, float t, float life, int seed, Tint tint) {
        if (t < 0F || t >= life) {
            return;
        }
        float k = 1F - t / life;
        float s = size * (0.75F + 0.45F * Vfx.easeOutExpo(Math.min(1F, t / (life * 0.35F))));
        draw.billboard(c.x, c.y, c.z, s, s, seed * 0.7F, VfxMode.BURST, seed & 1023, Vfx.argb(tint.inner(), k * k), false);
        draw.billboard(c, size * 0.55F * (0.6F + 0.4F * k), t * 0.15F, VfxMode.FLARE, 900, Vfx.argb(tint.core(), k));
        draw.billboard(c, size * 1.5F, 0F, VfxMode.DOT, 0, Vfx.argb(tint.glow(), 0.35F * k));
        if (Vfx.tier() >= 2) {
            draw.billboard(c, size * 2.8F, 0F, VfxMode.DOT, 0, Vfx.argb(tint.outer(), 0.08F * k));
        }
    }

    /**
     * 地面冲击波：一道白热的快波（细亮外沿 + 辉光色拖尾，后面跟一圈热浪扭曲），2.5 刻后再一道慢一点、淡一点的。
     * t = 炸开后的刻数，reach = 多少刻扩到 radius，life = 多久散完。
     */
    public static void shockwave(VfxDraw draw, Vec3 c, float radius, float t, float reach, float life, Tint tint) {
        for (int w = 0; w < 2; w++) {
            float tw = t - w * 2.5F;
            if (tw < 0F || tw > life || (w == 1 && Vfx.tier() == 0)) {
                continue;
            }
            float r = Math.max(0.3F, radius * Vfx.easeOutExpo(tw / reach) * (w == 0 ? 1F : 0.82F));
            float a = 1F - tw / life;
            if (w == 0) {
                draw.flat(c, r, 0F, VfxMode.SHOCK, 90, Vfx.argb(tint.core(), a));
                draw.flat(c, r * 0.985F, 0F, VfxMode.SHOCK, 380, Vfx.argb(tint.glow(), 0.85F * a));
                draw.distortFlat(c, r * 1.08F, 0, 140, a * 0.9F);
            } else {
                draw.flat(c, r, 0F, VfxMode.SHOCK, 300, Vfx.argb(tint.outer(), 0.6F * a));
            }
        }
    }

    /**
     * 结算用的地面冲击波：没有亮边的一圈柔光往外推，越往外越淡，推到 radius 时已经看不见——只表现「震开了」，
     * 不在地上留下招式范围的边（预警阶段的圈才负责标范围）。后面跟一圈很淡的热浪扭曲。
     * t = 炸开后的刻数，reach = 多少刻推到 radius，life = 多久散完；strength 0~1 调整体亮度。
     */
    public static void wave(VfxDraw draw, Vec3 c, float radius, float t, float reach, float life, float strength, Tint tint) {
        if (t < 0F || t > life || strength <= 0.01F) {
            return;
        }
        float f = Vfx.easeOutExpo(t / reach);
        float r = Math.max(0.4F, radius * (0.25F + 0.75F * f));
        // 随推出去的距离淡、随时间淡，两者都到头时正好没了
        float a = strength * (1F - f * f) * (1F - t / life);
        if (a <= 0.01F) {
            return;
        }
        // 着色器里光带的峰在面片半径的 0.7 处
        float half = r / 0.7F;
        int band = Mth.clamp(Math.round(1.6F / half * 1000F), 60, 450);
        draw.flat(c, half, 0F, VfxMode.WAVE, band, Vfx.argb(tint.glow(), 0.9F * a));
        if (Vfx.tier() >= 1) {
            draw.flat(c, half * 0.97F, 0F, VfxMode.WAVE, Math.min(700, band * 2), Vfx.argb(tint.outer(), 0.5F * a));
        }
        draw.distortFlat(c, r * 1.05F, 0, Mth.clamp(band / 2, 30, 300), 0.5F * a);
    }

    /**
     * 地面裂纹：从 rMin 到 rMax 之间往外辐射的几道折线裂缝，热时发白、冷下去变成辉光色再到外晕色（heat 1 → 0）。
     * 每道长短不一、起点也错开，连起来不成圈；形状按 seed 定下来整段不变（别每两刻换）。
     */
    public static void groundCracks(VfxDraw draw, long seed, Vec3 c, float rMin, float rMax, int count, float heat, float alpha, Tint tint) {
        if (alpha <= 0.02F || heat <= 0.01F || rMax <= rMin) {
            return;
        }
        RandomSource random = RandomSource.create(seed);
        int k = Math.max(2, Math.round(count * Vfx.detail(c)));
        double y = c.y + 0.05D;
        float h = Mth.clamp(heat, 0F, 1F);
        int hot = Vfx.lerp(tint.glow(), tint.inner(), h * h) & 0xFFFFFF;
        int cold = Vfx.lerp(tint.outer(), tint.glow(), h) & 0xFFFFFF;
        for (int i = 0; i < k; i++) {
            double a = (i + random.nextDouble() * 0.8D) * Mth.TWO_PI / k;
            double r0 = rMin + (rMax - rMin) * 0.15D * random.nextDouble();
            double r1 = rMin + (rMax - rMin) * (0.35D + 0.6D * random.nextDouble());
            Vec3 from = new Vec3(c.x + Math.cos(a) * r0, y, c.z + Math.sin(a) * r0);
            Vec3 to = new Vec3(c.x + Math.cos(a) * r1, y, c.z + Math.sin(a) * r1);
            int n = VfxPaths.jagged(random, from, to, 3, 0.2F, CRAWL);
            flatten(CRAWL, n, y);
            float w = 0.05F + 0.05F * random.nextFloat();
            VfxPaths.drawPath(draw, CRAWL, n, 0F, 1F, w * 3F, 0.2F, Vfx.argb(cold, 0.3F * h * alpha), VfxMode.STRIP, 60);
            VfxPaths.drawPath(draw, CRAWL, n, 0F, 1F, w, 0.15F, Vfx.argb(hot, (0.5F + 0.5F * h) * alpha), VfxMode.STRIP, 420);
        }
    }

    /** 地面电流：从中心沿地面往外爬的几道小电弧（中档以上才画） */
    public static void groundCrawl(VfxDraw draw, RandomSource random, Vec3 c, float radius, int count, float width, float alpha, Tint tint) {
        if (Vfx.tier() < 1 || alpha <= 0.02F) {
            return;
        }
        int k = Math.max(1, Math.round(count * Vfx.detail(c)));
        double y = c.y + 0.06D;
        for (int i = 0; i < k; i++) {
            double a = random.nextDouble() * Mth.TWO_PI;
            double r0 = radius * 0.15D * random.nextDouble();
            double r1 = radius * (0.55D + 0.45D * random.nextDouble());
            Vec3 from = new Vec3(c.x + Math.cos(a) * r0, y, c.z + Math.sin(a) * r0);
            Vec3 to = new Vec3(c.x + Math.cos(a) * r1, y, c.z + Math.sin(a) * r1);
            int n = VfxPaths.jagged(random, from, to, 3, 0.18F, CRAWL);
            flatten(CRAWL, n, y);
            VfxPaths.drawPath(draw, CRAWL, n, 0F, 1F, width * 2.6F, 0.3F, Vfx.argb(tint.glow(), 0.35F * alpha), VfxMode.STRIP, 70);
            VfxPaths.drawPath(draw, CRAWL, n, 0F, 1F, width * 0.5F, 0.3F, Vfx.argb(tint.inner(), alpha), VfxMode.STRIP, 480);
        }
    }

    /** 圈边电弧：沿一个圈的边贴地跳的几道短电弧（预警圈「通了电」的样子，中档以上才画） */
    public static void rimArcs(VfxDraw draw, RandomSource random, Vec3 c, float radius, int count, float width, float alpha, Tint tint) {
        rimArcs(draw, random, c, radius, 0F, Mth.TWO_PI, count, width, alpha, tint);
    }

    /** 只在一段圆弧（角度 angleFrom~angleTo，弧度）上跳的圈边电弧（扇形预警的外弧） */
    public static void rimArcs(VfxDraw draw, RandomSource random, Vec3 c, float radius, float angleFrom, float angleTo, int count, float width,
                               float alpha, Tint tint) {
        if (Vfx.tier() < 1 || alpha <= 0.02F) {
            return;
        }
        int k = Math.max(1, Math.round(count * Vfx.detail(c)));
        double y = c.y + 0.08D;
        for (int i = 0; i < k; i++) {
            double a0 = angleFrom + random.nextDouble() * (angleTo - angleFrom);
            double span = (0.6D + random.nextDouble() * 0.9D) / Math.max(1D, radius);
            Vec3 from = new Vec3(c.x + Math.cos(a0) * radius, y, c.z + Math.sin(a0) * radius);
            Vec3 to = new Vec3(c.x + Math.cos(a0 + span) * radius, y, c.z + Math.sin(a0 + span) * radius);
            int n = VfxPaths.jagged(random, from, to, 3, 0.3F, CRAWL);
            flatten(CRAWL, n, y);
            VfxPaths.drawPath(draw, CRAWL, n, 0F, 1F, width * 2.4F, 0.3F, Vfx.argb(tint.glow(), 0.4F * alpha), VfxMode.STRIP, 70);
            VfxPaths.drawPath(draw, CRAWL, n, 0F, 1F, width * 0.5F, 0.3F, Vfx.argb(tint.inner(), alpha), VfxMode.STRIP, 480);
        }
    }

    // ------------------------------------------------------------------ 粒子

    /**
     * 火花：从 at 往 dir 方向喷（dir 为零向量时四面八方；spread 越大锥越散），白热的头、往 tint 辉光色淡掉的拖尾，带重力。
     *
     * @param designed 近处、高档时的数量
     */
    public static void sparks(Vec3 at, Vec3 dir, double spread, float designed, double speedMin, double speedMax, int lifeMin, int lifeMax, Tint tint) {
        RandomSource random = Vfx.random();
        int n = Vfx.count(designed, at);
        boolean omni = dir.lengthSqr() < 1.0E-6D;
        Vec3 d0 = omni ? Vec3.ZERO : dir.normalize();
        for (int i = 0; i < n; i++) {
            Vec3 d = omni ? randomDir(random) : d0.add(randomDir(random).scale(spread)).normalize();
            double s = speedMin + random.nextDouble() * (speedMax - speedMin);
            int life = lifeMin + random.nextInt(Math.max(1, lifeMax - lifeMin));
            int end = random.nextFloat() < 0.3F ? tint.outer() : tint.glow();
            VfxParticles.spawn(at.x, at.y, at.z, d.x * s, d.y * s, d.z * s, life, 0.07F, 0.015F,
                    Vfx.argb(tint.core(), 1F), Vfx.argb(end, 0F), VfxMode.SPARK, 0, 0.87F, 0.04F, 2.6F, 0F);
        }
    }

    /** 余辉：在 at 周围（水平半径 radius）留下慢慢往上漂、慢慢淡掉的小光点 */
    public static void embers(Vec3 at, double radius, float designed, Tint tint) {
        RandomSource random = Vfx.random();
        int n = Vfx.count(designed, at);
        for (int i = 0; i < n; i++) {
            double a = random.nextDouble() * Mth.TWO_PI;
            double r = Math.sqrt(random.nextDouble()) * radius;
            VfxParticles.spawn(at.x + Math.cos(a) * r, at.y + random.nextDouble() * 0.3D, at.z + Math.sin(a) * r,
                    (random.nextDouble() - 0.5D) * 0.02D, 0.015D + random.nextDouble() * 0.035D, (random.nextDouble() - 0.5D) * 0.02D,
                    22 + random.nextInt(30), 0.05F + random.nextFloat() * 0.04F, 0F,
                    Vfx.argb(tint.core(), 0.95F), Vfx.argb(tint.glow(), 0F), VfxMode.DOT, 450, 0.97F, -0.0008F, 0F, 0F);
        }
    }

    // ------------------------------------------------------------------ 小工具

    /** 均匀分布的随机单位向量 */
    public static Vec3 randomDir(RandomSource random) {
        double y = random.nextDouble() * 2D - 1D;
        double a = random.nextDouble() * Mth.TWO_PI;
        double r = Math.sqrt(1D - y * y);
        return new Vec3(Math.cos(a) * r, y, Math.sin(a) * r);
    }

    /** 贴地画的折线：中点位移会往上下推，把 y 压回地面附近（只留一点起伏） */
    private static void flatten(double[] xyz, int n, double y) {
        for (int i = 0; i < n; i++) {
            xyz[i * 3 + 1] = y + Math.abs(xyz[i * 3 + 1] - y) * 0.15D;
        }
    }
}
