package com.flameforge.essentialcraft5.ambience;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.Level;

/**
 * 机器在客户端冒的粒子统一从这里出（炉子的火和烟、尘粒子、电火花……）。客户端装上自己的实现（发光粒子，见 {@code client/vfx/machine/AmbientGlow}），
 * 没装的时候（专用服、GameTest）照原样走 {@link Level#addParticle}。这个类不引用客户端类，两端都能加载。
 */
public final class AmbienceParticles {

    @FunctionalInterface
    public interface Sink {
        void add(Level level, ParticleOptions type, double x, double y, double z, double vx, double vy, double vz);
    }

    private static final Sink VANILLA = Level::addParticle;
    private static volatile Sink sink = VANILLA;

    private AmbienceParticles() {
    }

    /** 客户端启动时装一次 */
    public static void install(Sink custom) {
        sink = custom == null ? VANILLA : custom;
    }

    /** 同 {@link Level#addParticle}，参数含义也一样 */
    public static void add(Level level, ParticleOptions type, double x, double y, double z, double vx, double vy, double vz) {
        sink.add(level, type, x, y, z, vx, vy, vz);
    }

    /** 原样走原版（客户端实现里不认识的粒子类型用） */
    public static void vanilla(Level level, ParticleOptions type, double x, double y, double z, double vx, double vy, double vz) {
        VANILLA.add(level, type, x, y, z, vx, vy, vz);
    }
}
