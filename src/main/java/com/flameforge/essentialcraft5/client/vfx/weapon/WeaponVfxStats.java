package com.flameforge.essentialcraft5.client.vfx.weapon;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.vfx.VfxEffects;
import com.flameforge.essentialcraft5.client.vfx.VfxParticles;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

/**
 * 量武器特效每帧花多少（开发用）：本包特效的 render 前后各记一次时间，按帧累计，再记底座的面片数、粒子数。
 * 只量往缓冲里写顶点的 CPU 时间，上传、画的那部分不在里面。演示开关 {@code wstat} 读、{@code wstat reset} 清。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class WeaponVfxStats {

    public static boolean enabled;
    private static long frameNanos;
    private static long totalNanos;
    private static long peakNanos;
    private static int frames;
    private static long quadSum;
    private static int quadPeak;
    private static long particleSum;
    private static int particlePeak;
    private static long lastStamp;
    private static long frameTimeSum;

    private WeaponVfxStats() {
    }

    static long start() {
        return enabled ? System.nanoTime() : 0L;
    }

    static void stop(long t0) {
        if (enabled) {
            frameNanos += System.nanoTime() - t0;
        }
    }

    @SubscribeEvent
    public static void onWeaponVfxStatsFrame(RenderLevelStageEvent event) {
        if (!enabled || event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        long now = System.nanoTime();
        if (lastStamp != 0L && now - lastStamp < 1_000_000_000L) {
            frameTimeSum += now - lastStamp;
        }
        lastStamp = now;
        frames++;
        totalNanos += frameNanos;
        peakNanos = Math.max(peakNanos, frameNanos);
        frameNanos = 0L;
        quadSum += VfxEffects.lastQuads;
        quadPeak = Math.max(quadPeak, VfxEffects.lastQuads);
        particleSum += VfxParticles.count();
        particlePeak = Math.max(particlePeak, VfxParticles.count());
    }

    static void reset() {
        enabled = true;
        frameNanos = totalNanos = peakNanos = 0L;
        frames = 0;
        quadSum = particleSum = 0L;
        quadPeak = particlePeak = 0;
        lastStamp = frameTimeSum = 0L;
    }

    static String report() {
        int f = Math.max(1, frames);
        return String.format(Locale.ROOT, "frames %d frameMs %.2f weaponMs avg %.3f peak %.3f quads avg %d peak %d particles avg %d peak %d effects %d",
                frames, frameTimeSum / 1.0E6D / f, totalNanos / 1.0E6D / f, peakNanos / 1.0E6D, quadSum / f, quadPeak, particleSum / f, particlePeak, VfxEffects.size());
    }
}
