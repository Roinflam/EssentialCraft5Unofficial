package com.flameforge.essentialcraft5.client.model.mob.anim;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

/**
 * 写关键帧动画的捷径（原版 {@link AnimationDefinition} 的建造器写起来很长）。时间单位秒，位移单位像素，转角单位度：
 * <pre>{@code
 * static final AnimationDefinition SLAM = MobKeyframes.clip(1.2F)
 *         .addAnimation("right_arm", MobKeyframes.rotation(
 *                 MobKeyframes.rot(0F, 0, 0, 0), MobKeyframes.rot(0.5F, -160, 0, 0), MobKeyframes.rot(0.7F, 20, 0, 0, true), MobKeyframes.rot(1.2F, 0, 0, 0)))
 *         .addAnimation("body", MobKeyframes.position(MobKeyframes.pos(0F, 0, 0, 0), MobKeyframes.pos(0.7F, 0, -2, 0), MobKeyframes.pos(1.2F, 0, 0, 0)))
 *         .build();
 * }</pre>
 * 默认平滑插值（CATMULLROM），最后一个参数传 true 是线性（砸下去那一下要硬）。部位名就是 {@code addOrReplaceChild} 的名字，
 * 名字写错原版只是不动、不报错，所以每个模型的部位名用常量。
 */
public final class MobKeyframes {

    private MobKeyframes() {
    }

    /** 一段 seconds 秒、不循环的动画 */
    public static AnimationDefinition.Builder clip(float seconds) {
        return AnimationDefinition.Builder.withLength(seconds);
    }

    /** 一段 seconds 秒、循环的动画（待机、走路） */
    public static AnimationDefinition.Builder loop(float seconds) {
        return AnimationDefinition.Builder.withLength(seconds).looping();
    }

    public static Keyframe pos(float time, float x, float y, float z) {
        return pos(time, x, y, z, false);
    }

    public static Keyframe pos(float time, float x, float y, float z, boolean linear) {
        return new Keyframe(time, KeyframeAnimations.posVec(x, y, z), interpolation(linear));
    }

    public static Keyframe rot(float time, float x, float y, float z) {
        return rot(time, x, y, z, false);
    }

    public static Keyframe rot(float time, float x, float y, float z, boolean linear) {
        return new Keyframe(time, KeyframeAnimations.degreeVec(x, y, z), interpolation(linear));
    }

    public static Keyframe scale(float time, float x, float y, float z) {
        return scale(time, x, y, z, false);
    }

    /** 缩放写倍数（1 = 原样），原版的 scaleVec 存的是差值，这里换算好 */
    public static Keyframe scale(float time, float x, float y, float z, boolean linear) {
        return new Keyframe(time, KeyframeAnimations.scaleVec(x, y, z), interpolation(linear));
    }

    public static AnimationChannel position(Keyframe... keyframes) {
        return new AnimationChannel(AnimationChannel.Targets.POSITION, keyframes);
    }

    public static AnimationChannel rotation(Keyframe... keyframes) {
        return new AnimationChannel(AnimationChannel.Targets.ROTATION, keyframes);
    }

    public static AnimationChannel scaling(Keyframe... keyframes) {
        return new AnimationChannel(AnimationChannel.Targets.SCALE, keyframes);
    }

    private static AnimationChannel.Interpolation interpolation(boolean linear) {
        return linear ? AnimationChannel.Interpolations.LINEAR : AnimationChannel.Interpolations.CATMULLROM;
    }
}
