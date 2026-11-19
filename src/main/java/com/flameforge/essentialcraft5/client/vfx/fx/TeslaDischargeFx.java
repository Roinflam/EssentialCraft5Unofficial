package com.flameforge.essentialcraft5.client.vfx.fx;

import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxEffect;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import com.flameforge.essentialcraft5.client.vfx.VfxPalette;
import com.flameforge.essentialcraft5.client.vfx.VfxPaths;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 特斯拉线圈一次放电的特效（等离子画风样片）：从线圈顶上的出电点打向每个目标。
 * <ul>
 *   <li>出电点：爆点放射光 + 一圈乱跳的电晕；</li>
 *   <li>每道电弧：四层（外晕、辉光、等离子芯、白热细芯）+ 一级 / 二级分叉，前 8 刻每刻换形状，亮度照真闪电回击（中间暗一下再亮），
 *       一团能量顺着电弧冲到目标身上；</li>
 *   <li>落点：爆点 + 小冲击波 + 地面电流 + 火花、余辉 + 热浪，地上留一块烧痕。</li>
 * </ul>
 * 线圈一秒打一次，不加屏幕闪光（站在旁边会一直闪）。
 */
public final class TeslaDischargeFx implements VfxEffect {

    private static final int LIFE = 18;
    private static final VfxPlasma.Tint TINT = VfxPlasma.MRU;

    private final Vec3 emit;
    private final List<Entity> targets;
    private final double[][] paths;
    private final int[] seeds;
    private final RandomSource random = RandomSource.create();
    private int coronaSeed;
    private int age;

    public TeslaDischargeFx(Vec3 emit, List<Entity> targets) {
        this.emit = emit;
        this.targets = targets;
        this.paths = new double[Math.max(1, targets.size())][VfxPaths.jaggedPoints(5) * 3];
        this.seeds = new int[Math.max(1, targets.size())];
        for (int i = 0; i < seeds.length; i++) {
            seeds[i] = random.nextInt(100000);
        }
        coronaSeed = random.nextInt(100000);
    }

    @Override
    public boolean tick() {
        age++;
        if (age < 9 || age % 2 == 0) {
            for (int i = 0; i < seeds.length; i++) {
                seeds[i] = random.nextInt(100000);
            }
            coronaSeed = random.nextInt(100000);
        }
        if (age == 1) {
            VfxPlasma.sparks(emit, Vec3.ZERO, 0D, 14F, 0.15D, 0.45D, 6, 14, TINT);
            for (Entity target : targets) {
                Vec3 c = target.getBoundingBox().getCenter();
                Vec3 away = c.subtract(emit).multiply(1D, 0D, 1D);
                Vec3 dir = (away.lengthSqr() < 1.0E-4D ? Vec3.ZERO : away.normalize()).add(0D, 0.8D, 0D);
                VfxPlasma.sparks(c, dir, 0.9D, 22F, 0.18D, 0.55D, 8, 20, TINT);
                VfxPlasma.embers(target.position(), 0.6D, 8F, TINT);
            }
        }
        return age < LIFE;
    }

    @Override
    public void render(VfxDraw draw) {
        float pt = draw.partialTick();
        float t = age + pt;
        // 出电点：爆点 + 电晕
        VfxPlasma.burst(draw, emit, 1.7F, t, 11F, 53, TINT);
        float crown = 1F - Mth.clamp(t / 14F, 0F, 1F);
        VfxPlasma.corona(draw, RandomSource.create(coronaSeed), emit, 1.0F, 4, 0.03F, crown, TINT);
        float a = VfxPlasma.strokeAlpha(t, 6F);
        for (int i = 0; i < targets.size(); i++) {
            Entity target = targets.get(i);
            Vec3 feet = target.getPosition(pt);
            Vec3 c = feet.add(0D, target.getBbHeight() * 0.5D, 0D);
            if (a > 0F) {
                RandomSource r = RandomSource.create(seeds[i]);
                int n = VfxPaths.jagged(r, emit, c, 5, 0.12F, paths[i]);
                VfxPlasma.forkedArc(draw, r, paths[i], n, 0.085F, a, seeds[i], 6, 1.6F, TINT);
                VfxPlasma.pulse(draw, paths[i], n, t / 2F, 0.25F, 1F, TINT);
            }
            VfxPlasma.burst(draw, draw.towardCamera(c, 1.1D), 1.8F, t, 10F, (seeds[0] + i * 37) & 1023, TINT);
            VfxPlasma.shockwave(draw, feet, 1.8F, t, 4F, 10F, TINT);
            float hit = 1F - Mth.clamp(t / 10F, 0F, 1F);
            VfxPlasma.groundCrawl(draw, RandomSource.create(coronaSeed + i * 7919L), feet, 1.5F, 3, 0.022F, hit, TINT);
            float scorch = 1F - Mth.clamp(t / LIFE, 0F, 1F);
            draw.flat(feet, 1.3F, 0F, VfxMode.DOT, 0, Vfx.argb(VfxPalette.MAGENTA, 0.5F * scorch));
            draw.distortBillboard(c, 1.6F, 1, 0, 0.7F * hit);
        }
    }

    @Override
    public AABB bounds() {
        AABB box = new AABB(emit, emit);
        for (Entity target : targets) {
            box = box.minmax(target.getBoundingBox());
        }
        return box.inflate(3D);
    }
}
