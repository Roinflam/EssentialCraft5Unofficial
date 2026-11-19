package com.flameforge.essentialcraft5.client.vfx.weapon;

import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxEffect;
import com.flameforge.essentialcraft5.client.vfx.VfxEffects;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import com.flameforge.essentialcraft5.client.vfx.VfxParticles;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 弹丸的拖尾（MRU 箭、二分弹、疾风弹）：跟着实体，记最近几刻的位置画成往后收细的等离子拖尾，头上一团光；实体没了在最后的位置散开。
 * 同时最多 {@value #MAX_ACTIVE} 条（风暴术士成群放疾风弹时不会越攒越多），64 格外只画头上那团光。
 */
public final class ProjectileTrailFx implements VfxEffect {

    private static final int MAX_ACTIVE = 48;
    private static int active;

    /** 拖尾的样子：几刻长、头部半宽、头上光团大小、散开时的爆点大小，风系的不画电晕 */
    public record Style(VfxPlasma.Tint tint, int length, float width, float head, float pop, boolean wind) {
    }

    public static final Style MRU_ARROW = new Style(VfxPlasma.MRU, 10, 0.13F, 0.2F, 0.55F, false);
    public static final Style DIVIDER = new Style(VfxPlasma.MRU, 9, 0.12F, 0.22F, 0.6F, false);
    public static final Style STORM_GUST = new Style(WeaponTints.WIND, 8, 0.2F, 0.3F, 0.7F, true);

    private final Entity entity;
    private final Style style;
    private final double[] history;
    private final double[] path;
    private final int seed = Vfx.random().nextInt(100000);
    private int count;
    private Vec3 last;
    private int after = -1;
    private int age;

    private ProjectileTrailFx(Entity entity, Style style) {
        this.entity = entity;
        this.style = style;
        this.history = new double[style.length() * 3];
        this.path = new double[(style.length() + 1) * 3];
        this.last = center(entity, 1F);
    }

    static void resetCount() {
        active = 0;
    }

    public static void attach(Entity entity, Style style) {
        if (active >= MAX_ACTIVE) {
            return;
        }
        active++;
        VfxEffects.add(new ProjectileTrailFx(entity, style));
    }

    private static Vec3 center(Entity e, float pt) {
        return e.getPosition(pt).add(0D, e.getBbHeight() * 0.5D, 0D);
    }

    @Override
    public boolean tick() {
        age++;
        if (after < 0) {
            if (entity.isRemoved()) {
                after = 0;
                pop();
            } else {
                Vec3 p = center(entity, 1F);
                // 新的放在最前面
                System.arraycopy(history, 0, history, 3, history.length - 3);
                history[0] = p.x;
                history[1] = p.y;
                history[2] = p.z;
                count = Math.min(count + 1, style.length());
                last = p;
                if (style.wind() && Vfx.visibleAt(p)) {
                    motes(p);
                }
                return true;
            }
        }
        after++;
        if (after >= 6) {
            active--;
            return false;
        }
        return true;
    }

    /** 风系：身后甩出几粒往外飘的光尘（不冒电） */
    private void motes(Vec3 p) {
        RandomSource r = Vfx.random();
        int n = Vfx.count(1.5F, p);
        for (int i = 0; i < n; i++) {
            Vec3 d = VfxPlasma.randomDir(r).scale(0.04D);
            VfxParticles.spawn(p.x, p.y, p.z, d.x, d.y, d.z, 12 + r.nextInt(8), 0.06F, 0.01F,
                    Vfx.argb(style.tint().inner(), 0.8F), Vfx.argb(style.tint().glow(), 0F), VfxMode.DOT, 300, 0.92F, -0.002F, 0F, 0F);
        }
    }

    private void pop() {
        if (!Vfx.visibleAt(last)) {
            return;
        }
        VfxPlasma.sparks(last, Vec3.ZERO, 0D, style.wind() ? 6F : 8F, 0.08D, 0.3D, 4, 10, style.tint());
        if (style.wind()) {
            VfxPlasma.embers(last, 0.5D, 6F, style.tint());
        }
    }

    @Override
    public void render(VfxDraw draw) {
        long t0 = WeaponVfxStats.start();
        float pt = draw.partialTick();
        if (after >= 0) {
            float t = after + pt;
            VfxPlasma.burst(draw, draw.towardCamera(last, 0.3D), style.pop(), t, 6F, seed & 1023, style.tint());
            WeaponVfxStats.stop(t0);
            return;
        }
        Vec3 head = center(entity, pt);
        float flick = 0.85F + 0.15F * Mth.sin((age + pt) * 1.7F);
        draw.billboard(head, style.head(), 0F, VfxMode.DOT, 600, Vfx.argb(style.tint().core(), 0.9F * flick));
        draw.billboard(head, style.head() * 2.4F, 0F, VfxMode.DOT, 0, Vfx.argb(style.tint().glow(), 0.35F * flick));
        if (count >= 2 && draw.camera().distanceToSqr(head) < 64D * 64D) {
            // history[0] 是这一刻末的位置，插值出来的头还没走到那，从 history[1] 往后接
            path[0] = head.x;
            path[1] = head.y;
            path[2] = head.z;
            System.arraycopy(history, 3, path, 3, (count - 1) * 3);
            VfxPlasma.trail(draw, path, count, style.width(), 0.9F, seed, style.tint());
        }
        WeaponVfxStats.stop(t0);
    }

    @Override
    public AABB bounds() {
        return new AABB(last, last).inflate(style.length() * 1.6D + 1D);
    }
}
