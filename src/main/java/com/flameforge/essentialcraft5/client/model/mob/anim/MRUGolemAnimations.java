package com.flameforge.essentialcraft5.client.model.mob.anim;

import net.minecraft.client.animation.AnimationDefinition;

import static com.flameforge.essentialcraft5.client.model.mob.anim.MobKeyframes.clip;
import static com.flameforge.essentialcraft5.client.model.mob.anim.MobKeyframes.pos;
import static com.flameforge.essentialcraft5.client.model.mob.anim.MobKeyframes.position;
import static com.flameforge.essentialcraft5.client.model.mob.anim.MobKeyframes.rot;
import static com.flameforge.essentialcraft5.client.model.mob.anim.MobKeyframes.rotation;
import static com.flameforge.essentialcraft5.client.model.mob.anim.MobKeyframes.scale;
import static com.flameforge.essentialcraft5.client.model.mob.anim.MobKeyframes.scaling;

/**
 * 聚能魔像的三段动画（{@code MRUGolemModel}；段号见 {@code MRUGolemEntity.ANIM_*}）。时间按服务端的出招节奏对齐：
 * <ul>
 *   <li>{@link #HAMMER} 重锤 0.6 秒：右拳举过头顶再砸下（伤害在出招那一刻就结算了，动画只是跟上）；</li>
 *   <li>{@link #SLAM} 聚能冲击 1.4 秒：前 1 秒双拳举起、身子后仰蓄力（对上 {@code slam_warning_ticks} 默认 20 刻），第 1 秒砸地，之后收回；</li>
 *   <li>{@link #WHIP} 电弧鞭 0.8 秒：左臂往后扬 0.4 秒，第 0.5 秒甩出去（对上抬手 10 刻）。</li>
 * </ul>
 * 展示厅定格的四个关键姿势：待机、{@link #HAMMER} 0.15 秒（举拳）、{@link #SLAM} 0.95 秒（蓄满）、{@link #WHIP} 0.5 秒（甩出）。
 */
public final class MRUGolemAnimations {

    public static final AnimationDefinition HAMMER = clip(0.6F)
            .addAnimation("right_arm", rotation(
                    rot(0F, 0F, 0F, 0F),
                    rot(0.15F, -150F, 0F, 10F),
                    rot(0.32F, -25F, 0F, 0F),
                    rot(0.6F, 0F, 0F, 0F)))
            .addAnimation("body", rotation(
                    rot(0F, 0F, 0F, 0F),
                    rot(0.15F, -6F, -15F, 0F),
                    rot(0.32F, 14F, 12F, 0F),
                    rot(0.6F, 0F, 0F, 0F)))
            .build();

    public static final AnimationDefinition SLAM = clip(1.4F)
            .addAnimation("right_arm", rotation(
                    rot(0F, 0F, 0F, 0F),
                    rot(0.8F, -165F, 0F, 12F),
                    rot(0.95F, -170F, 0F, 12F),
                    rot(1.05F, -35F, 0F, 0F, true),
                    rot(1.4F, 0F, 0F, 0F)))
            .addAnimation("left_arm", rotation(
                    rot(0F, 0F, 0F, 0F),
                    rot(0.8F, -165F, 0F, -12F),
                    rot(0.95F, -170F, 0F, -12F),
                    rot(1.05F, -35F, 0F, 0F, true),
                    rot(1.4F, 0F, 0F, 0F)))
            .addAnimation("body", rotation(
                    rot(0F, 0F, 0F, 0F),
                    rot(0.8F, -14F, 0F, 0F),
                    rot(0.95F, -16F, 0F, 0F),
                    rot(1.05F, 26F, 0F, 0F, true),
                    rot(1.4F, 0F, 0F, 0F)))
            .addAnimation("waist", position(
                    pos(0F, 0F, 0F, 0F),
                    pos(0.8F, 0F, 1F, 0F),
                    pos(1.05F, 0F, -2.5F, 0F, true),
                    pos(1.4F, 0F, 0F, 0F)))
            .addAnimation("core", scaling(
                    scale(0F, 1F, 1F, 1F),
                    scale(0.9F, 1.45F, 1.45F, 1.45F),
                    scale(1.05F, 1F, 1F, 1F, true),
                    scale(1.4F, 1F, 1F, 1F)))
            .build();

    public static final AnimationDefinition WHIP = clip(0.8F)
            .addAnimation("left_arm", rotation(
                    rot(0F, 0F, 0F, 0F),
                    rot(0.4F, -50F, 20F, -70F),
                    rot(0.5F, -95F, -20F, 25F, true),
                    rot(0.8F, 0F, 0F, 0F)))
            .addAnimation("body", rotation(
                    rot(0F, 0F, 0F, 0F),
                    rot(0.4F, 0F, 22F, 0F),
                    rot(0.5F, 4F, -25F, 0F, true),
                    rot(0.8F, 0F, 0F, 0F)))
            .build();

    private MRUGolemAnimations() {
    }
}
