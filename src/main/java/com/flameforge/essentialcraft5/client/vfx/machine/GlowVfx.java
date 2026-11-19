package com.flameforge.essentialcraft5.client.vfx.machine;

import com.flameforge.essentialcraft5.blockentity.DemonicPentacleBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MatrixCrucibleBlockEntity;
import com.flameforge.essentialcraft5.blockentity.PlayerPentacleBlockEntity;
import com.flameforge.essentialcraft5.blockentity.WindRuneBlockEntity;
import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import com.flameforge.essentialcraft5.entity.MRUPresenceEntity;
import com.flameforge.essentialcraft5.mru.MRUBalance;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * 辉光一类的常驻特效：MRU 聚集体（光线簇原样，只加一团辉光）、恶魔 / 灵魂五芒星和风之符文（地面贴图下垫一层随贴图呼吸的辉光，
 * 两种五芒星的圈边隔一阵跳几道电弧；风之符文不冒电，换成慢慢绕圈升起的光点）、矩阵淬炼台（灵魂石外的光晕，试炼中加电晕）。
 * 不另画法阵：图案还是原来的贴图。
 */
public final class GlowVfx {

    /** 五芒星贴图里外圈的半径（贴图方片半对角线 1.5√2，圈刚好贴着方片的边） */
    private static final float PENTACLE_RIM = 1.45F;
    /** 圈边电弧：每 60 刻里亮 10 刻，按位置错开 */
    private static final int RIM_PERIOD = 60;
    private static final int RIM_ON = 10;
    private static final int DEMON_RED = 0xFF2A22;
    /** 风之符文贴图的绿（windrune.png 亮像素 #07FF07，往白里收一点，免得比贴图还艳） */
    private static final int WIND_GREEN = 0x4CFF6A;
    private static final RandomSource SHAPE = RandomSource.create();
    private static final Int2ObjectOpenHashMap<VfxPlasma.Tint> TINTS = new Int2ObjectOpenHashMap<>();

    private GlowVfx() {
    }

    /** 按颜色配一套层次，缓存起来（聚集体的平衡色是连续变化的，先量化到每通道 16 级） */
    static VfxPlasma.Tint tint(int rgb) {
        int q = rgb & 0xF0F0F0;
        VfxPlasma.Tint t = TINTS.get(q);
        if (t == null) {
            if (TINTS.size() > 2048) {
                TINTS.clear();
            }
            t = VfxPlasma.tint(q | 0x080808);
            TINTS.put(q, t);
        }
        return t;
    }

    private static int rgb(float[] c) {
        return (Mth.clamp((int) (c[0] * 255F), 0, 255) << 16) | (Mth.clamp((int) (c[1] * 255F), 0, 255) << 8) | Mth.clamp((int) (c[2] * 255F), 0, 255);
    }

    // ------------------------------------------------------------------ MRU 聚集体

    static void presence(VfxDraw draw, Object ref, float dist) {
        if (!(ref instanceof MRUPresenceEntity entity) || entity.getMRU() <= 0) {
            return;
        }
        float pt = draw.partialTick();
        Vec3 p = entity.getPosition(pt);
        VfxPlasma.Tint tint = tint(rgb(MRUBalance.rgb(entity.getBalance())));
        // 光线簇最长约 25 × 0.0000075 × MRU 格；辉光取它的一半上下
        float reach = Mth.clamp(entity.getMRU() * 0.0000075F * 14F, 0.35F, 3F);
        float breathe = 0.8F + 0.2F * Mth.sin((draw.time() + entity.getId() * 13) * 0.08F);
        draw.billboard(p.x, p.y, p.z, reach * 1.6F, reach * 1.6F, 0F, VfxMode.DOT, 0, Vfx.argb(tint.outer(), 0.3F * breathe), false);
        draw.billboard(p.x, p.y, p.z, reach * 0.8F, reach * 0.8F, 0F, VfxMode.DOT, 0, Vfx.argb(tint.glow(), 0.35F * breathe), false);
        draw.billboard(p.x, p.y, p.z, reach * 0.22F, reach * 0.22F, 0F, VfxMode.DOT, 700, Vfx.argb(tint.core(), 0.8F * breathe), false);
    }

    // ------------------------------------------------------------------ 五芒星、风之符文

    static void pentacle(VfxDraw draw, Object ref, float dist) {
        BlockPos pos;
        VfxPlasma.Tint tint;
        float bright;
        if (ref instanceof DemonicPentacleBlockEntity demon && demon.getLevel() != null) {
            if (!demon.isFormed()) {
                return;
            }
            pos = demon.getBlockPos();
            tint = tint(DEMON_RED);
            // 和贴图同一个节拍：0~30 刻变亮、30~60 刻变暗
            float m = (demon.getLevel().getGameTime() % 60L) + draw.partialTick();
            bright = 0.25F + 0.75F * ((m > 30F ? 60F - m : m) / 30F);
        } else if (ref instanceof PlayerPentacleBlockEntity soul) {
            int tier = soul.getTier();
            if (tier < 0) {
                return;
            }
            pos = soul.getBlockPos();
            tint = tint(rgb(PlayerPentacleBlockEntity.tierColor(tier)));
            bright = 0.8F + 0.2F * Mth.sin(draw.time() * 0.1F);
        } else {
            return;
        }
        // 方块顶面在 1/16 格，贴得比它低会被盖住
        Vec3 c = new Vec3(pos.getX() + 0.5D, pos.getY() + 0.08D, pos.getZ() + 0.5D);
        draw.flat(c, 2.3F, 0F, VfxMode.DOT, 0, Vfx.argb(tint.glow(), 0.45F * bright));
        draw.flat(c, 1.1F, 0F, VfxMode.DOT, 0, Vfx.argb(tint.inner(), 0.3F * bright));
        if (dist > 24F) {
            return;
        }
        long key = pos.asLong();
        long phase = (MachineVfx.ticks() + (ArcCache.mix(key) & 1023L)) % RIM_PERIOD;
        if (phase < RIM_ON) {
            float a = (1F - phase / (float) RIM_ON) * bright;
            SHAPE.setSeed(key * 31L + (MachineVfx.ticks() + (ArcCache.mix(key) & 1023L)) / 2L);
            VfxPlasma.rimArcs(draw, SHAPE, c, PENTACLE_RIM, 3, 0.02F, a, tint);
        }
    }

    static void wind(VfxDraw draw, Object ref, float dist) {
        if (!(ref instanceof WindRuneBlockEntity be) || be.getLevel() == null || !be.isFormed()) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        VfxPlasma.Tint tint = tint(WIND_GREEN);
        float m = (be.getLevel().getGameTime() % 60L) + draw.partialTick();
        float bright = Math.max(0.2F, (m > 30F ? 60F - m : m) / 30F);
        double cx = pos.getX() + 0.5D;
        double cy = pos.getY() + 0.08D;
        double cz = pos.getZ() + 0.5D;
        draw.flat(new Vec3(cx, cy, cz), 2.0F, 0F, VfxMode.DOT, 0, Vfx.argb(tint.glow(), 0.4F * bright));
        if (dist > 24F) {
            return;
        }
        // 四颗光点绕着圈慢慢升起（画出来的，不进粒子池）
        float time = draw.time() + (pos.asLong() & 255);
        for (int i = 0; i < 4; i++) {
            float f = ((time * 0.012F) + i * 0.25F) % 1F;
            float ang = time * 0.06F + i * Mth.HALF_PI;
            float r = 1.3F - 0.5F * f;
            float a = Mth.sin(f * Mth.PI) * 0.8F;
            draw.billboard(cx + Mth.cos(ang) * r, cy + 0.1D + f * 1.6D, cz + Mth.sin(ang) * r, 0.1F, 0.1F, 0F, VfxMode.DOT, 600,
                    Vfx.argb(tint.inner(), a), false);
        }
    }

    // ------------------------------------------------------------------ 矩阵淬炼台

    static void crucible(VfxDraw draw, Object ref, float dist) {
        if (!(ref instanceof MatrixCrucibleBlockEntity be) || be.getLevel() == null) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        boolean active = be.isTrialActiveClient();
        float time = (be.getLevel().getGameTime() % 3600L) + draw.partialTick();
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 1.15D + Mth.sin(time / 20F) * 0.05D;
        double z = pos.getZ() + 0.5D;
        VfxPlasma.Tint tint = VfxPlasma.MRU;
        float breathe = 0.8F + 0.2F * Mth.sin(time * (active ? 0.4F : 0.1F));
        draw.billboard(x, y, z, active ? 0.9F : 0.55F, active ? 0.9F : 0.55F, 0F, VfxMode.DOT, 0,
                Vfx.argb(tint.glow(), (active ? 0.55F : 0.32F) * breathe), false);
        if (!active) {
            return;
        }
        draw.billboard(x, y, z, 0.16F, 0.16F, 0F, VfxMode.DOT, 700, Vfx.argb(tint.core(), 0.8F * breathe), false);
        draw.flat(new Vec3(x, pos.getY() + 1.02D, z), 0.9F, time * 0.05F, VfxMode.DOT, 0, Vfx.argb(tint.glow(), 0.25F * breathe));
        if (dist <= 24F) {
            SHAPE.setSeed(pos.asLong() * 17L + (long) (time / 3F));
            VfxPlasma.corona(draw, SHAPE, new Vec3(x, y, z), 0.7F, 3, 0.022F, 0.8F, tint);
        }
    }
}
