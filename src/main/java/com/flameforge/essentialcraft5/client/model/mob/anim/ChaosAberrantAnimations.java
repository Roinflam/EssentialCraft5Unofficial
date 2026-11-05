package com.flameforge.essentialcraft5.client.model.mob.anim;

import com.flameforge.essentialcraft5.client.model.mob.ChaosAberrantModel;
import net.minecraft.client.animation.AnimationDefinition;

import static com.flameforge.essentialcraft5.client.model.mob.anim.MobKeyframes.*;

/**
 * 混沌畸变体的关键帧（13d-N2）。蓄力、冲撞、眩晕三段是循环动画，跟着实体同步下来的冲撞阶段开关（时长随配置变，不用事件）；咬、产卵是一次性的：
 * <ul>
 *   <li>{@link #WINDUP}：低头、身子往下压，前腿往前蹬地，整只微微发抖；</li>
 *   <li>{@link #DASH}：四条腿大幅度快摆、头压得更低往前顶；</li>
 *   <li>{@link #STUN}：头左右晃、身子歪着；</li>
 *   <li>{@link #BITE}：0.4 秒，头往前一探、下巴张开再合上；</li>
 *   <li>{@link #BROOD}：0.8 秒，背上的肉瘤鼓起来再瘪下去（精英产幼体）。</li>
 * </ul>
 */
public final class ChaosAberrantAnimations {

    public static final AnimationDefinition WINDUP = loop(0.5F)
            .addAnimation(ChaosAberrantModel.HEAD, rotation(rot(0F, 25F, 0F, 0F), rot(0.25F, 28F, 0F, 0F), rot(0.5F, 25F, 0F, 0F)))
            .addAnimation(ChaosAberrantModel.BODY, position(pos(0F, 0F, 1.5F, 0F), pos(0.12F, 0.3F, 1.6F, 0F), pos(0.25F, -0.3F, 1.5F, 0F),
                    pos(0.5F, 0F, 1.5F, 0F)))
            .addAnimation(ChaosAberrantModel.FRONT_LEFT_LEG, rotation(rot(0F, -25F, 0F, 0F), rot(0.25F, -10F, 0F, 0F), rot(0.5F, -25F, 0F, 0F)))
            .addAnimation(ChaosAberrantModel.FRONT_RIGHT_LEG, rotation(rot(0F, -10F, 0F, 0F), rot(0.25F, -25F, 0F, 0F), rot(0.5F, -10F, 0F, 0F)))
            .build();

    public static final AnimationDefinition DASH = loop(0.4F)
            .addAnimation(ChaosAberrantModel.HEAD, rotation(rot(0F, 35F, 0F, 0F), rot(0.4F, 35F, 0F, 0F)))
            .addAnimation(ChaosAberrantModel.BODY, position(pos(0F, 0F, 0.5F, 0F), pos(0.2F, 0F, -0.5F, 0F), pos(0.4F, 0F, 0.5F, 0F)))
            .addAnimation(ChaosAberrantModel.FRONT_LEFT_LEG, rotation(rot(0F, -55F, 0F, 0F), rot(0.2F, 45F, 0F, 0F), rot(0.4F, -55F, 0F, 0F)))
            .addAnimation(ChaosAberrantModel.FRONT_RIGHT_LEG, rotation(rot(0F, -40F, 0F, 0F), rot(0.2F, 55F, 0F, 0F), rot(0.4F, -40F, 0F, 0F)))
            .addAnimation(ChaosAberrantModel.HIND_LEFT_LEG, rotation(rot(0F, 45F, 0F, 0F), rot(0.2F, -45F, 0F, 0F), rot(0.4F, 45F, 0F, 0F)))
            .addAnimation(ChaosAberrantModel.HIND_RIGHT_LEG, rotation(rot(0F, 55F, 0F, 0F), rot(0.2F, -40F, 0F, 0F), rot(0.4F, 55F, 0F, 0F)))
            .build();

    public static final AnimationDefinition STUN = loop(1.0F)
            .addAnimation(ChaosAberrantModel.HEAD, rotation(rot(0F, 15F, 0F, -15F), rot(0.5F, 15F, 0F, 15F), rot(1.0F, 15F, 0F, -15F)))
            .addAnimation(ChaosAberrantModel.BODY, rotation(rot(0F, 0F, 0F, -4F), rot(0.5F, 0F, 0F, 4F), rot(1.0F, 0F, 0F, -4F)))
            .addAnimation(ChaosAberrantModel.BODY, position(pos(0F, 0F, 1F, 0F), pos(1.0F, 0F, 1F, 0F)))
            .build();

    public static final AnimationDefinition BITE = clip(0.4F)
            .addAnimation(ChaosAberrantModel.HEAD, rotation(rot(0F, 0F, 0F, 0F), rot(0.12F, -15F, 0F, 0F), rot(0.22F, 15F, 0F, 0F, true), rot(0.4F, 0F, 0F, 0F)))
            .addAnimation(ChaosAberrantModel.HEAD, position(pos(0F, 0F, 0F, 0F), pos(0.22F, 0F, 0F, -2F), pos(0.4F, 0F, 0F, 0F)))
            .addAnimation(ChaosAberrantModel.JAW, rotation(rot(0F, 0F, 0F, 0F), rot(0.12F, 35F, 0F, 0F), rot(0.22F, 0F, 0F, 0F, true), rot(0.4F, 0F, 0F, 0F)))
            .build();

    public static final AnimationDefinition BROOD = clip(0.8F)
            .addAnimation(ChaosAberrantModel.HUMP, scaling(scale(0F, 1F, 1F, 1F), scale(0.35F, 1.3F, 1.5F, 1.3F), scale(0.5F, 0.9F, 0.8F, 0.9F),
                    scale(0.8F, 1F, 1F, 1F)))
            .addAnimation(ChaosAberrantModel.BODY, position(pos(0F, 0F, 0F, 0F), pos(0.35F, 0F, -1F, 0F), pos(0.8F, 0F, 0F, 0F)))
            .build();

    private ChaosAberrantAnimations() {
    }
}
