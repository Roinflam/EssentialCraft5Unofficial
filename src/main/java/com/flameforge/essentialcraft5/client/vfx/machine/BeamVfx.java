package com.flameforge.essentialcraft5.client.vfx.machine;

import com.flameforge.essentialcraft5.blockentity.LeyCondenserBlockEntity;
import com.flameforge.essentialcraft5.blockentity.LeyProjectorBlockEntity;
import com.flameforge.essentialcraft5.blockentity.RayTowerBlockEntity;
import com.flameforge.essentialcraft5.blockentity.SolarPrismBlockEntity;
import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import com.flameforge.essentialcraft5.mru.MRULink;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;

/**
 * 光束一类的常驻特效：太阳棱镜光柱（日阵金等离子光束，代替原来的信标光柱）、射线塔之间的射线、地脉投影仪 / 凝聚器往地下打的光束外加一层辉光。
 * 射线塔的连线每台隔一秒（按位置错开）才重新读一次绑定宝石，不每帧解析物品数据。
 */
public final class BeamVfx {

    private static final VfxPlasma.Tint SOLAR = VfxPlasma.SOLAR;
    private static final VfxPlasma.Tint MRU = VfxPlasma.MRU;
    /** 射线塔顶上水晶的中心比方块底面高多少（同 RayTowerRenderer 算出来的 2.1 格） */
    private static final double CRYSTAL_Y = 2.1D;
    private static final long NO_LINK = Long.MIN_VALUE;

    private static final Long2LongOpenHashMap RAY_SOURCE = new Long2LongOpenHashMap();
    private static final Long2LongOpenHashMap RAY_CHECKED = new Long2LongOpenHashMap();
    private static final Long2LongOpenHashMap PRISM_SEEN = new Long2LongOpenHashMap();
    private static WeakReference<Level> cacheLevel = new WeakReference<>(null);

    private BeamVfx() {
    }

    private static void checkLevel(Level level) {
        if (cacheLevel.get() != level || RAY_SOURCE.size() > 4096 || PRISM_SEEN.size() > 4096) {
            RAY_SOURCE.clear();
            RAY_CHECKED.clear();
            PRISM_SEEN.clear();
            cacheLevel = new WeakReference<>(level);
        }
    }

    // ------------------------------------------------------------------ 太阳棱镜

    /**
     * 棱镜光柱（20 刻）：落点到棱镜一段满亮，往上 24 格照旧，再往上一直淡到世界顶；前 3 刻冲出来、最后 6 刻收细淡掉。
     * 落点一团金色爆点 + 贴地光晕，光柱开始那一刻冒一把火花和余烬。
     */
    static void prism(VfxDraw draw, Object ref, float dist) {
        if (!(ref instanceof SolarPrismBlockEntity be) || be.getLevel() == null) {
            return;
        }
        Level level = be.getLevel();
        checkLevel(level);
        long until = be.clientBeamUntil();
        float t = SolarPrismBlockEntity.BEAM_DURATION - (until - level.getGameTime()) + draw.partialTick();
        if (t < 0F || t > SolarPrismBlockEntity.BEAM_DURATION) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        double x = pos.getX() + 0.5D;
        double z = pos.getZ() + 0.5D;
        double bottom = pos.getY() - be.clientBeamDepth();
        double hit = bottom + 1D;
        double top = level.getMaxBuildHeight();
        float in = Vfx.easeOutCubic(Mth.clamp(t / 3F, 0F, 1F));
        float out = 1F - Mth.clamp((t - 14F) / 6F, 0F, 1F);
        float alpha = in * out * (0.9F + 0.1F * Mth.sin(t * 2.3F));
        float width = 0.34F * (0.55F + 0.45F * out);
        int seed = (int) (pos.asLong() & 1023);
        Vec3 a = new Vec3(x, bottom, z);
        Vec3 b = new Vec3(x, pos.getY() + 1D, z);
        Vec3 c = new Vec3(x, Math.min(top, b.y + 24D), z);
        VfxPlasma.beam(draw, a, b, width, alpha, false, seed, SOLAR);
        if (c.y > b.y) {
            VfxPlasma.beam(draw, b, c, width, alpha, false, seed + 1, SOLAR);
        }
        if (top > c.y) {
            VfxPlasma.beam(draw, c, new Vec3(x, top, z), width, alpha * 0.85F, true, seed + 2, SOLAR);
        }
        Vec3 land = new Vec3(x, hit, z);
        VfxPlasma.burst(draw, draw.towardCamera(land, 0.4D), 1.6F, t, 12F, seed, SOLAR);
        draw.flat(land, 1.4F, 0F, VfxMode.DOT, 0, Vfx.argb(SOLAR.glow(), 0.35F * alpha));
        draw.billboard(x, pos.getY() + 0.5D, z, 0.9F, 0.9F, 0F, VfxMode.DOT, 0, Vfx.argb(SOLAR.inner(), 0.3F * alpha), false);
        long key = pos.asLong();
        if (PRISM_SEEN.get(key) != until) {
            PRISM_SEEN.put(key, until);
            VfxPlasma.sparks(land, new Vec3(0D, 1D, 0D), 1.1D, 16F, 0.12D, 0.35D, 8, 18, SOLAR);
            VfxPlasma.embers(land, 0.8D, 10F, SOLAR);
        }
    }

    // ------------------------------------------------------------------ 射线塔

    /**
     * 射线塔：从塔顶水晶到绑定宝石指向的源头一道细射线（源头也是射线塔就连到它的水晶上），
     * 塔里有 MRU 时整条亮、隔一阵有一团光从源头流过来；没有 MRU 时只剩一道暗线。
     */
    static void ray(VfxDraw draw, Object ref, float dist) {
        if (!(ref instanceof RayTowerBlockEntity be) || be.getLevel() == null) {
            return;
        }
        Level level = be.getLevel();
        checkLevel(level);
        BlockPos pos = be.getBlockPos();
        long key = pos.asLong();
        long epoch = ArcCache.epoch(key, 20);
        long source;
        if (RAY_CHECKED.containsKey(key) && RAY_CHECKED.get(key) == epoch) {
            source = RAY_SOURCE.get(key);
        } else {
            BlockPos found = MRULink.visibleSource(level, pos, be.getInventory().getStackInSlot(RayTowerBlockEntity.SLOT_GEM),
                    MRULink.DEFAULT_RANGE);
            source = found == null ? NO_LINK : found.asLong();
            RAY_SOURCE.put(key, source);
            RAY_CHECKED.put(key, epoch);
        }
        if (source == NO_LINK) {
            return;
        }
        BlockPos src = BlockPos.of(source);
        boolean tower = level.getBlockEntity(src) instanceof RayTowerBlockEntity;
        Vec3 from = new Vec3(src.getX() + 0.5D, src.getY() + (tower ? CRYSTAL_Y : 0.5D), src.getZ() + 0.5D);
        Vec3 to = new Vec3(pos.getX() + 0.5D, pos.getY() + CRYSTAL_Y, pos.getZ() + 0.5D);
        boolean powered = be.getMRUStorage().getMRU() > 0;
        float time = draw.time() + (key & 255);
        float shimmer = 0.85F + 0.15F * Mth.sin(time * 0.3F);
        VfxPlasma.beam(draw, from, to, 0.075F, (powered ? 0.75F : 0.25F) * shimmer, false, (int) (key & 1023), MRU);
        draw.billboard(to.x, to.y, to.z, 0.35F, 0.35F, 0F, VfxMode.DOT, 0, Vfx.argb(MRU.glow(), (powered ? 0.35F : 0.12F) * shimmer), false);
        if (powered && dist < 40F) {
            // 一团光从源头流到这座塔，一趟 30 刻
            float f = (time % 30F) / 30F;
            double px = Mth.lerp(f, from.x, to.x);
            double py = Mth.lerp(f, from.y, to.y);
            double pz = Mth.lerp(f, from.z, to.z);
            float bump = Mth.sin(f * Mth.PI);
            draw.billboard(px, py, pz, 0.1F, 0.1F, 0F, VfxMode.DOT, 700, Vfx.argb(MRU.core(), 0.9F * bump), false);
            draw.billboard(px, py, pz, 0.3F, 0.3F, 0F, VfxMode.DOT, 0, Vfx.argb(MRU.glow(), 0.4F * bump), false);
        }
    }

    // ------------------------------------------------------------------ 地脉

    /** 地脉投影仪 / 凝聚器往地下打的光束：原来的流动光管照旧，外面加一层等离子辉光，出光口一团光晕 */
    static void ley(VfxDraw draw, Object ref, float dist) {
        int length;
        BlockPos pos;
        Level level;
        if (ref instanceof LeyProjectorBlockEntity p) {
            length = LeyProjectorBlockEntity.BEAM_LENGTH;
            pos = p.getBlockPos();
            level = p.getLevel();
        } else if (ref instanceof LeyCondenserBlockEntity c) {
            length = LeyCondenserBlockEntity.BEAM_LENGTH;
            pos = c.getBlockPos();
            level = c.getLevel();
        } else {
            return;
        }
        if (level == null) {
            return;
        }
        double x = pos.getX() + 0.5D;
        double z = pos.getZ() + 0.5D;
        double top = pos.getY() + 0.3D;
        double bottom = Math.max(level.getMinBuildHeight(), pos.getY() - length);
        if (top <= bottom) {
            return;
        }
        float pulse = 0.8F + 0.2F * Mth.sin((draw.time() + (pos.asLong() & 127)) * 0.2F);
        VfxPlasma.beam(draw, new Vec3(x, top, z), new Vec3(x, bottom, z), 0.2F, 0.45F * pulse, true, (int) (pos.asLong() & 1023), MRU);
        draw.billboard(x, pos.getY() + 0.05D, z, 0.45F, 0.45F, 0F, VfxMode.DOT, 0, Vfx.argb(MRU.glow(), 0.3F * pulse), false);
    }
}
