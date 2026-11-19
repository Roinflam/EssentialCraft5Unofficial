package com.flameforge.essentialcraft5.client.vfx.machine;

import com.flameforge.essentialcraft5.blockentity.MRUCoilBlockEntity;
import com.flameforge.essentialcraft5.blockentity.ReactorBlockEntity;
import com.flameforge.essentialcraft5.client.render.ArcEffect;
import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxEffects;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import com.flameforge.essentialcraft5.client.vfx.VfxPaths;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 特斯拉线圈和反应堆的电弧：待机电弧、反应堆上方那团电弧（常驻，{@link MachineVfx} 每帧画），线圈放电（{@link CoilStrikeFx}）、基座火花（{@link SparkArcFx}）。
 * 原作的电弧几何（起点、终点、寿命、淡出）还是方块实体里的 {@link ArcEffect}，这里只换画法：折线用中点位移重新生成、按台错开几刻换一次形状，
 * 层数按质量档和远近减：近处四层（窄内晕、等离子细丝、热白、白芯），二十格外两层，三十六格外只剩白芯。
 */
public final class CoilVfx {

    private static final VfxPlasma.Tint TINT = VfxPlasma.MRU;
    /** 同时在放的线圈放电特效上限：一排线圈一起打怪时多出来的只响声、不画 */
    static final int MAX_STRIKES = 24;
    /** 放电特效离镜头多远以内才生成 */
    static final double STRIKE_RANGE = 64D;
    private static final long[] STARTED = new long[MAX_STRIKES];
    private static int cursor;

    static {
        java.util.Arrays.fill(STARTED, Long.MIN_VALUE / 2);
    }

    private CoilVfx() {
    }

    /** 最近一次放电寿命（18 刻）内开始的放电数 */
    static int activeStrikes() {
        long now = MachineVfx.ticks();
        int n = 0;
        for (long t : STARTED) {
            if (now - t < CoilStrikeFx.LIFE) {
                n++;
            }
        }
        return n;
    }

    /** 线圈放电：从出电点打到每个目标；超过上限或离得太远返回 false（声音照放） */
    public static boolean strike(Vec3 emit, List<Entity> targets) {
        // 按开始时间记：特效被清掉（换世界）也不会漏减
        long now = MachineVfx.ticks();
        if (!MachineVfx.enabled || targets.isEmpty() || now - STARTED[cursor] < CoilStrikeFx.LIFE || distance(emit) > STRIKE_RANGE) {
            return false;
        }
        STARTED[cursor] = now;
        cursor = (cursor + 1) % MAX_STRIKES;
        VfxEffects.add(new CoilStrikeFx(emit, targets, (float) distance(emit)));
        return true;
    }

    /** 基座侧面一道一格高的小火花 */
    public static void spark(Vec3 from, Vec3 to) {
        if (MachineVfx.enabled && distance(from) <= 32D) {
            VfxEffects.add(new SparkArcFx(from, to));
        }
    }

    /** 一道一闪而过的短电弧（矩阵受损时从身上窜出来的那几道） */
    public static void shortArc(Vec3 from, Vec3 to, int life) {
        if (MachineVfx.enabled && Vfx.visibleAt(from)) {
            VfxEffects.add(new SparkArcFx(from, to, life, 0.03F, 0.25F));
        }
    }

    static double distance(Vec3 p) {
        return net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceTo(p);
    }

    // ------------------------------------------------------------------ 常驻

    /** 线圈待机：一道接一道从水晶往外窜的短电弧 + 水晶上的一团辉光 */
    static void idle(VfxDraw draw, Object ref, float dist) {
        if (!(ref instanceof MRUCoilBlockEntity be)) {
            return;
        }
        ArcEffect arc = be.idleArc();
        if (arc == null) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        long key = pos.asLong();
        float pt = draw.partialTick();
        double ox = pos.getX(), oy = pos.getY(), oz = pos.getZ();
        Vec3 s = arc.start();
        Vec3 e = arc.end();
        double[] path = ArcCache.path(ArcCache.key(key, System.identityHashCode(arc)), ArcCache.epoch(key, 3),
                ox + s.x, oy + s.y, oz + s.z, ox + e.x, oy + e.y, oz + e.z, 0.22F);
        float flicker = flicker(arc.age(), pt, key);
        float a = arc.alpha(pt) * flicker;
        arc(draw, path, ArcCache.POINTS, 0.04F, a, dist, (int) key & 1023);
        float breathe = 0.75F + 0.25F * Mth.sin((draw.time() + (key & 63)) * 0.21F);
        draw.billboard(ox + s.x, oy + s.y, oz + s.z, 0.38F, 0.38F, 0F, VfxMode.DOT, 0, Vfx.argb(TINT.glow(), 0.4F * breathe), false);
        if (dist < 24F) {
            draw.billboard(ox + s.x, oy + s.y, oz + s.z, 0.09F, 0.09F, 0F, VfxMode.DOT, 700, Vfx.argb(TINT.core(), 0.7F * breathe), false);
        }
    }

    /** 反应堆：浮球上方那团电弧（最多 21 道，各活 21 刻）+ 浮球外一圈辉光 */
    static void reactor(VfxDraw draw, Object ref, float dist) {
        if (!(ref instanceof ReactorBlockEntity be)) {
            return;
        }
        List<ArcEffect> arcs = be.arcs();
        if (arcs.isEmpty()) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        long key = pos.asLong();
        float pt = draw.partialTick();
        double ox = pos.getX(), oy = pos.getY(), oz = pos.getZ();
        // 远了只画一半的电弧，免得糊成一团
        int step = dist > 32F ? 2 : 1;
        for (int i = 0; i < arcs.size(); i += step) {
            ArcEffect arc = arcs.get(i);
            Vec3 s = arc.start();
            Vec3 e = arc.end();
            int id = System.identityHashCode(arc);
            double[] path = ArcCache.path(ArcCache.key(key, id), ArcCache.epoch(key + id, 3),
                    ox + s.x, oy + s.y, oz + s.z, ox + e.x, oy + e.y, oz + e.z, 0.25F);
            float a = arc.alpha(pt) * flicker(arc.age(), pt, id);
            arc(draw, path, ArcCache.POINTS, 0.038F, a, dist, id & 1023);
        }
        double cy = oy + 1.05D;
        float breathe = 0.8F + 0.2F * Mth.sin(draw.time() * 0.15F);
        draw.billboard(ox + 0.5D, cy, oz + 0.5D, 1.25F, 1.25F, 0F, VfxMode.DOT, 0, Vfx.argb(TINT.outer(), 0.32F * breathe), false);
        draw.billboard(ox + 0.5D, cy, oz + 0.5D, 0.8F, 0.8F, 0F, VfxMode.DOT, 0, Vfx.argb(TINT.glow(), 0.22F * breathe), false);
    }

    /** 真闪电那样一闪一闪：刚出现最亮，之后在 0.55~1 之间跳（各道错开） */
    private static float flicker(int age, float pt, long salt) {
        if (age < 2) {
            return 1F;
        }
        int h = (int) ((age * 0x9E3779B1L + salt) >>> 7) & 7;
        return 0.55F + h * (0.45F / 7F);
    }

    /**
     * 按远近减层的电弧：层次和倍数同 {@link VfxPlasma#arc}（窄内晕 3 倍、等离子细丝 1.2 倍、热白 1.5 倍、白芯 0.75 倍），
     * 二十格外去掉细丝和热白，三十六格外只剩白芯。
     */
    static void arc(VfxDraw draw, double[] path, int n, float width, float alpha, float dist, int seed) {
        if (alpha <= 0.02F) {
            return;
        }
        int tier = Vfx.tier();
        if (dist <= 36F) {
            VfxPaths.drawPath(draw, path, n, 0F, 1F, width * 3F, 0.1F, Vfx.argb(TINT.glow(), 0.42F * alpha), VfxMode.STRIP, 70);
        }
        if (tier >= 1 && dist <= 20F) {
            VfxPaths.drawPath(draw, path, n, 0F, 1F, width * 1.2F, 0.06F, Vfx.argb(TINT.inner(), alpha), VfxMode.PLASMA, seed);
            VfxPaths.drawPath(draw, path, n, 0F, 1F, width * 1.5F, 0.05F, Vfx.argb(TINT.core(), 0.5F * alpha), VfxMode.STRIP, 300);
        }
        VfxPaths.drawPath(draw, path, n, 0F, 1F, width * 0.75F, 0.04F, Vfx.argb(TINT.core(), alpha), VfxMode.STRIP, 650);
    }
}
