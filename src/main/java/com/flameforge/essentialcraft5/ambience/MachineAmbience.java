package com.flameforge.essentialcraft5.ambience;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 机器工作时的声音和粒子（只在客户端跑，用的都是 {@link Level} 上两端通用的接口：{@code playLocalSound}、{@code addParticle}，
 * 所以这个包里没有任何客户端专属的类，专用服加载也没事）。
 * <p>
 * 一台机器的氛围由一个 {@link Profile} 描述，由它的方块实体在 {@code ambience()} 里返回；服务端用 {@code ambientWorking()} 说「现在在干活」，
 * 这一位随更新包同步给客户端（见 MRUBlockEntity），客户端只在这一位为真、且 {@value #RANGE} 格内有人时才调 {@link Profile#tick}。
 * <ul>
 *   <li><b>环形底噪</b>：{@code tools/gen/machine_sounds.py} 合成的 1.1 秒片段，内容周期恰好 1 秒、首尾各 0.1 秒淡入淡出；
 *       每 {@value #LOOP_TICKS} 刻重播一次，相邻两段在接缝处叠 0.1 秒，淡入淡出之和恒为 1，听起来是连续的。音高必须是 1。
 *       机器停了以后最多还会响完手上这一段（不到 1 秒）。</li>
 *   <li><b>点缀音</b>：每刻按「每秒几次」掷一次，位置在机器附近抖一点，音高在给定范围里随机。</li>
 *   <li><b>粒子</b>：每刻按「每秒几个」掷一次；全局粒子档位（减少 / 最少）由原版粒子引擎自己处理。</li>
 * </ul>
 */
public final class MachineAmbience {

    /** 环形片段的重播间隔，和合成脚本里的周期一致（1.0 秒） */
    public static final int LOOP_TICKS = 20;
    /** 离机器多远的范围内有玩家才放 */
    public static final double RANGE = 24.0D;

    private MachineAmbience() {
    }

    /** 粒子怎么生成：x / y / z 是机器方块的基准点（方块中心偏上），随机量由实现自己加 */
    @FunctionalInterface
    public interface Fx {
        void spawn(Level level, double x, double y, double z, RandomSource random);
    }

    /** 每台机器自己的播放进度（只在客户端用） */
    public static final class State {
        private long loopAt;
    }

    private record Accent(Supplier<SoundEvent> sound, float perSecond, float volume, float pitchLow, float pitchHigh) {
    }

    private record Spark(float perSecond, double height, Fx fx) {
    }

    /** 一类机器的氛围。创建后不可变，可以放在静态字段里多台机器共用 */
    public static final class Profile {
        private final Supplier<SoundEvent> loop;
        private final float loopVolume;
        private final List<Accent> accents;
        private final List<Spark> sparks;

        private Profile(Builder builder) {
            this.loop = builder.loop;
            this.loopVolume = builder.loopVolume;
            this.accents = List.copyOf(builder.accents);
            this.sparks = List.copyOf(builder.sparks);
        }

        public static Builder builder() {
            return new Builder();
        }

        /** 客户端每刻调一次（只在这台机器正在干活时） */
        public void tick(Level level, BlockPos pos, State state) {
            float volumeScale = MachineAmbienceConfig.sounds() ? MachineAmbienceConfig.volume() : 0.0F;
            boolean particlesOn = MachineAmbienceConfig.particles();
            if (volumeScale <= 0.0F && !particlesOn) {
                return;
            }
            double x = pos.getX() + 0.5D;
            double y = pos.getY() + 0.5D;
            double z = pos.getZ() + 0.5D;
            if (level.getNearestPlayer(x, y, z, RANGE, false) == null) {
                return;
            }
            RandomSource random = level.random;
            if (volumeScale > 0.0F && loop != null) {
                long now = level.getGameTime();
                if (now >= state.loopAt) {
                    // 没连着上一段播（刚开机、卡过顿）就从现在起算，否则接着上一段的节拍
                    state.loopAt = (now - state.loopAt > 5L ? now : state.loopAt) + LOOP_TICKS;
                    level.playLocalSound(x, y, z, loop.get(), SoundSource.BLOCKS, loopVolume * volumeScale, 1.0F, false);
                }
            }
            for (Accent accent : volumeScale > 0.0F ? accents : List.<Accent>of()) {
                if (random.nextFloat() < accent.perSecond / 20.0F) {
                    float pitch = accent.pitchLow + random.nextFloat() * (accent.pitchHigh - accent.pitchLow);
                    level.playLocalSound(x + (random.nextDouble() - 0.5D) * 0.8D, y + (random.nextDouble() - 0.5D) * 0.8D,
                            z + (random.nextDouble() - 0.5D) * 0.8D, accent.sound.get(), SoundSource.BLOCKS, accent.volume * volumeScale, pitch, false);
                }
            }
            for (Spark spark : particlesOn ? sparks : List.<Spark>of()) {
                if (random.nextFloat() < spark.perSecond / 20.0F) {
                    spark.fx.spawn(level, x, pos.getY() + spark.height, z, random);
                }
            }
        }
    }

    public static final class Builder {
        private Supplier<SoundEvent> loop;
        private float loopVolume;
        private final List<Accent> accents = new ArrayList<>();
        private final List<Spark> sparks = new ArrayList<>();

        private Builder() {
        }

        /** 环形底噪（音高固定 1） */
        public Builder loop(Supplier<SoundEvent> sound, float volume) {
            this.loop = sound;
            this.loopVolume = volume;
            return this;
        }

        /** 随机点缀音：每秒平均 perSecond 次 */
        public Builder accent(Supplier<SoundEvent> sound, float perSecond, float volume, float pitchLow, float pitchHigh) {
            accents.add(new Accent(sound, perSecond, volume, pitchLow, pitchHigh));
            return this;
        }

        /** 粒子：每秒平均 perSecond 个，基准点在方块底面以上 height 格的正中 */
        public Builder spark(float perSecond, double height, Fx fx) {
            sparks.add(new Spark(perSecond, height, fx));
            return this;
        }

        public Profile build() {
            return new Profile(this);
        }
    }
}
