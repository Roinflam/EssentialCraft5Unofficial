package com.flameforge.essentialcraft5.client.vfx.gear;

import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxEffect;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import com.flameforge.essentialcraft5.client.vfx.VfxParticles;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.function.BooleanSupplier;

/**
 * 魔力翅膀：用翅膀缓降 / 往上飞时，背后张开两扇等离子光羽（翅膀贴图的品红 + 白羽，配 {@link VfxPlasma#MRU}）。
 * 每扇几根羽骨从肩胛往外、往上、往后扇开，羽尖下垂，相邻羽骨之间连一层淡光膜；往上飞时扇得快、缓降时张平了慢慢扇；停用时收拢淡掉。
 * <p>
 * 主城里几十个人同时开着也要扛得住：近档每扇 5 根、三层；中档 3 根、两层；远档 3 根一层；再远不画（{@link GearVfx#lod}）。
 * 自己第一人称时翅膀在镜头后面，整个不画。羽骨每帧按身体位置算，只用基本类型、不新建对象。
 */
final class WingsFx implements VfxEffect {

    private static final VfxPlasma.Tint TINT = VfxPlasma.MRU;
    private static final int FEATHERS = 5;
    private static final int POINTS = 7;
    /** 羽骨仰角（度）：最下面那根往下斜一点，最上面那根接近竖直 */
    private static final float[] ELEVATION = {-18F, 6F, 28F, 50F, 70F};
    private static final float[] LENGTH = {0.9F, 1.25F, 1.5F, 1.55F, 1.3F};
    /** 中档、远档只画这三根（下、中、上） */
    private static final int[] SPARSE = {0, 2, 4};

    private final GearAnchor owner;
    private final BooleanSupplier active;
    private final double[] xyz = new double[POINTS * 3];
    private final double[] prev = new double[POINTS * 3];
    private final float[] half = new float[POINTS];
    private final int[] argb = new int[POINTS];
    private final float[] u = new float[POINTS];
    private final int seed;
    private float open;
    private float openO;
    private boolean rising;
    private int age;

    WingsFx(GearAnchor owner, BooleanSupplier active, int seed) {
        this.owner = owner;
        this.active = active;
        this.seed = seed;
    }

    /** 往上飞（扇得快）还是缓降（张平了慢慢扇） */
    void setRising(boolean rising) {
        this.rising = rising;
    }

    @Override
    public boolean tick() {
        age++;
        openO = open;
        boolean on = active.getAsBoolean() && owner.alive();
        open = Mth.clamp(open + (on ? 0.2F : -0.12F), 0F, 1F);
        if (on && open > 0.5F && age % 3 == 0 && !owner.firstPerson()) {
            Vec3 p = owner.pos(1F);
            if (GearVfx.lod(p) == 0) {
                tipEmber(p);
            }
        }
        return on || open > 0F;
    }

    /** 羽尖偶尔落下一点余辉 */
    private void tipEmber(Vec3 feet) {
        float yaw = owner.bodyYaw(1F) * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        int side = Vfx.random().nextBoolean() ? 1 : -1;
        double a = Mth.DEG_TO_RAD * ELEVATION[1 + Vfx.random().nextInt(3)];
        double reach = 1.3D;
        double x = feet.x - fz * side * (0.12D + Math.cos(a) * reach) - fx * 0.5D;
        double y = feet.y + rootHeight() + Math.sin(a) * reach - 0.3D;
        double z = feet.z + fx * side * (0.12D + Math.cos(a) * reach) - fz * 0.5D;
        if (Vfx.count(1F, feet) > 0) {
            VfxParticles.spawn(x, y, z, 0D, -0.02D, 0D, 20 + Vfx.random().nextInt(15), 0.06F, 0F, Vfx.argb(TINT.core(), 0.9F),
                    Vfx.argb(TINT.glow(), 0F), VfxMode.DOT, 450, 0.96F, 0.0015F, 0F, 0F);
        }
    }

    private double rootHeight() {
        return owner.crouching() ? 1.05D : 1.3D;
    }

    @Override
    public void render(VfxDraw draw) {
        if (owner.firstPerson()) {
            return;
        }
        float pt = draw.partialTick();
        Vec3 feet = owner.pos(pt);
        int lod = GearVfx.lod(feet);
        float o = Mth.lerp(pt, openO, open);
        if (lod > 2 || o <= 0.01F) {
            return;
        }
        long start = GearVfx.beginTiming();
        float t = age + pt;
        int tier = Vfx.tier();
        float yaw = owner.bodyYaw(pt) * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        // 身体右手方向（朝 +z 时是 -x）
        double sx = -fz, sz = fx;
        double rootY = feet.y + rootHeight();
        float ease = Vfx.easeOutCubic(o);
        float flap = rising ? Mth.sin(t * 0.55F + seed) : Mth.sin(t * 0.16F + seed) * 0.45F;
        int count = lod == 0 ? FEATHERS : SPARSE.length;
        for (int side = -1; side <= 1; side += 2) {
            double rx = feet.x - fx * 0.22D + sx * 0.12D * side;
            double rz = feet.z - fz * 0.22D + sz * 0.12D * side;
            for (int c = 0; c < count; c++) {
                int i = lod == 0 ? c : SPARSE[c];
                if (c > 0) {
                    System.arraycopy(xyz, 0, prev, 0, xyz.length);
                }
                // 收拢时羽骨贴着背往下合，张开时扇到设计的仰角；扇动绕前后轴转
                double elev = Mth.DEG_TO_RAD * (Mth.lerp(ease, -55F, ELEVATION[i]) + flap * (8F + i * 3F));
                double len = LENGTH[i] * (0.35D + 0.65D * ease);
                double out = Math.cos(elev), up = Math.sin(elev);
                // 羽骨方向：往外 + 往上 + 往后 0.45
                double dx = sx * side * out - fx * 0.45D;
                double dy = up;
                double dz = sz * side * out - fz * 0.45D;
                double norm = Math.sqrt(dx * dx + dy * dy + dz * dz);
                dx /= norm;
                dy /= norm;
                dz /= norm;
                for (int k = 0; k < POINTS; k++) {
                    double s = k / (double) (POINTS - 1);
                    // 羽尖往下垂、往后拖一点
                    double droop = s * s * len;
                    xyz[k * 3] = rx + dx * len * s - fx * 0.15D * droop;
                    xyz[k * 3 + 1] = rootY + dy * len * s - 0.28D * droop;
                    xyz[k * 3 + 2] = rz + dz * len * s - fz * 0.15D * droop;
                    u[k] = (float) (len * s);
                }
                float a = ease * (0.75F + 0.25F * (i / (float) (FEATHERS - 1)));
                if (c > 0 && lod <= 1) {
                    membrane(draw, ease);
                }
                layer(draw, 0.2F, Vfx.argb(TINT.glow(), 0.5F * a), VfxMode.STRIP, 70);
                if (tier >= 2 && lod == 0) {
                    layer(draw, 0.05F, Vfx.argb(TINT.inner(), 0.85F * a), VfxMode.PLASMA, (seed + i * 13 + side * 7) & 1023);
                }
                if (lod <= 1) {
                    layer(draw, 0.022F, Vfx.argb(TINT.core(), a), VfxMode.STRIP, 650);
                }
                if (lod == 0) {
                    int tip = (POINTS - 1) * 3;
                    float twinkle = 0.7F + 0.3F * Mth.sin(t * 0.9F + i * 1.7F + side);
                    draw.billboard(xyz[tip], xyz[tip + 1], xyz[tip + 2], 0.12F * twinkle, 0.12F * twinkle, 0F, VfxMode.DOT, 600,
                            Vfx.argb(TINT.core(), 0.8F * a), false);
                }
            }
            if (lod == 0 && tier >= 1) {
                // 肩胛处一团辉光，把几根羽骨的根收在一起
                draw.billboard(rx, rootY, rz, 0.32F, 0.32F, 0F, VfxMode.DOT, 0, Vfx.argb(TINT.glow(), 0.35F * ease), false);
            }
        }
        GearVfx.endTiming(start);
    }

    /** 上一根羽骨和这根之间的光膜：根部浓、往外淡掉。v 固定在 0.25，光带横截面那里只剩柔光（约四成亮），噪声顺着羽骨往外流 */
    private void membrane(VfxDraw draw, float ease) {
        for (int k = 0; k < POINTS - 1; k++) {
            float s0 = k / (float) (POINTS - 1), s1 = (k + 1) / (float) (POINTS - 1);
            int c0 = membraneColor(s0, ease), c1 = membraneColor(s1, ease);
            int a = k * 3, b = a + 3;
            draw.quad(prev[a], prev[a + 1], prev[a + 2], u[k], 0.25F, c0,
                    xyz[a], xyz[a + 1], xyz[a + 2], u[k], 0.25F, c0,
                    xyz[b], xyz[b + 1], xyz[b + 2], u[k + 1], 0.25F, c1,
                    prev[b], prev[b + 1], prev[b + 2], u[k + 1], 0.25F, c1, VfxMode.BEAM, seed);
        }
    }

    private static int membraneColor(float s, float ease) {
        float fall = (float) Math.pow(1F - s, 1.2D);
        return Vfx.argb(Vfx.lerp(TINT.glow(), TINT.outer(), s) & 0xFFFFFF, Math.min(1F, ease * (0.75F * fall + 0.12F)));
    }

    /** 当前羽骨画一层：根部宽、往羽尖收细变淡 */
    private void layer(VfxDraw draw, float width, int color, int mode, int param) {
        int rgb = color & 0xFFFFFF;
        float alpha = ((color >>> 24) & 255) / 255F;
        for (int k = 0; k < POINTS; k++) {
            float f = k / (float) (POINTS - 1);
            half[k] = width * (1F - 0.6F * f);
            argb[k] = Vfx.argb(rgb, alpha * (1F - 0.55F * f * f));
        }
        draw.ribbon(xyz, POINTS, half, argb, u, mode, param);
    }

    @Override
    public AABB bounds() {
        return owner.box().inflate(2.5D);
    }
}
