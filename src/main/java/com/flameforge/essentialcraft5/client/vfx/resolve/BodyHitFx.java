package com.flameforge.essentialcraft5.client.vfx.resolve;

import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxEffect;
import com.flameforge.essentialcraft5.client.vfx.VfxEffects;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * 首领本体挨打（天灾之心）：被打的那一面炸一个相色的爆点，火花往外溅，身上跳几道短电弧；自己打中的那一下镜头轻轻一震（卡肉感）。
 * 客户端收到原版受击事件时由实体调（{@code handleDamageEvent}），不走网络；同一只首领 {@value #MIN_GAP} 刻内只放一次，连击不糊成一片。
 */
public final class BodyHitFx implements VfxEffect {

    private static final int LIFE = 9;
    private static final int MIN_GAP = 3;
    private static final Map<Integer, Long> LAST = new HashMap<>();

    private final Vec3 at;
    private final Vec3 out;
    private final Vec3 center;
    private final float reach;
    private final VfxPlasma.Tint tint;
    private final RandomSource random = RandomSource.create();
    private final int burstSeed = random.nextInt(1024);
    private int age;

    private BodyHitFx(Vec3 at, Vec3 out, Vec3 center, float reach, VfxPlasma.Tint tint) {
        this.at = at;
        this.out = out;
        this.center = center;
        this.reach = reach;
        this.tint = tint;
    }

    /** 客户端：entity 挨了 source 一下；rgb = 它这时的主色 */
    public static void onHurt(LivingEntity entity, DamageSource source, int rgb) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        Long last = LAST.get(entity.getId());
        if (last != null && now - last < MIN_GAP && now >= last) {
            return;
        }
        if (LAST.size() > 32) {
            LAST.entrySet().removeIf(e -> now - e.getValue() > 200L || e.getValue() > now);
        }
        LAST.put(entity.getId(), now);

        Vec3 center = entity.position().add(0D, entity.getBbHeight() * 0.5D, 0D);
        Vec3 from = source.getSourcePosition();
        Entity attacker = source.getEntity();
        if (from == null && source.getDirectEntity() != null) {
            from = source.getDirectEntity().getEyePosition();
        }
        if (from == null) {
            from = mc.gameRenderer.getMainCamera().getPosition();
        }
        Vec3 d = from.subtract(center);
        Vec3 out = d.lengthSqr() < 1.0E-4D ? new Vec3(0D, 0D, 1D) : d.normalize();
        float reach = Math.max(entity.getBbWidth(), entity.getBbHeight()) * 0.45F;
        Vec3 at = center.add(out.scale(reach));
        VfxPlasma.Tint tint = VfxPlasma.tint(rgb);
        VfxPlasma.sparks(at, out, 0.8D, 16F, 0.18D, 0.5D, 6, 14, tint);
        VfxEffects.add(new BodyHitFx(at, out, center, reach, tint));
        if (attacker != null && attacker == mc.player) {
            // 普通攻击会连着打：只轻震一下，不加冲击帧、速度线
            ResolveFx.shake(at, 64D, 0.14F, 3);
        }
    }

    @Override
    public boolean tick() {
        return ++age < LIFE;
    }

    @Override
    public void render(VfxDraw draw) {
        float t = age + draw.partialTick();
        Vec3 p = draw.towardCamera(at, 0.6D);
        if (t < 1F) {
            // 打中那一帧：白热的星芒定住
            draw.billboard(p, 1.6F, 0F, VfxMode.FLARE, 1000, Vfx.argb(tint.core(), 1F));
        }
        VfxPlasma.burst(draw, p, 1.5F, Math.max(0F, t - 1F), LIFE - 1F, burstSeed, tint);
        float k = 1F - Mth.clamp(t / 6F, 0F, 1F);
        if (k > 0F && Vfx.tier() >= 1) {
            VfxPlasma.corona(draw, RandomSource.create(burstSeed + (age >> 1) * 31L), at, reach * 0.9F, 3, 0.03F, k, tint);
        }
    }

    @Override
    public AABB bounds() {
        return new AABB(center, center).inflate(reach + 2D);
    }
}
