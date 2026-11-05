package com.flameforge.essentialcraft5.client.model.mob.anim;

import com.flameforge.essentialcraft5.client.model.mob.FrostbittenModel;
import net.minecraft.client.animation.AnimationDefinition;

import static com.flameforge.essentialcraft5.client.model.mob.anim.MobKeyframes.*;

/**
 * 霜蚀者的关键帧（13d-N1）：
 * <ul>
 *   <li>{@link #ATTACK}：右臂往下劈（0.4 秒，近战命中时播）；</li>
 *   <li>{@link #BREATH}：冰息（1.4 秒）——前 1 秒（{@code breath_charge} = 20 刻）头往后仰、双臂往后收、胸口鼓起来，是给你闪开的预警；
 *       1.0 秒处头往前一探把气喷出去；</li>
 *   <li>{@link #SEAL}：霜棺督卫的冰封（1.2 秒）——双臂慢慢举过头（预警 {@code elite_seal_warning} = 20 刻），1.0 秒处往下一砸。</li>
 * </ul>
 */
public final class FrostbittenAnimations {

    public static final AnimationDefinition ATTACK = clip(0.4F)
            .addAnimation(FrostbittenModel.RIGHT_ARM, rotation(
                    rot(0F, 0F, 0F, 0F), rot(0.12F, -110F, 0F, 10F), rot(0.25F, 20F, 0F, 0F, true), rot(0.4F, 0F, 0F, 0F)))
            .addAnimation(FrostbittenModel.BODY, rotation(
                    rot(0F, 0F, 0F, 0F), rot(0.25F, 8F, -10F, 0F), rot(0.4F, 0F, 0F, 0F)))
            .build();

    public static final AnimationDefinition BREATH = clip(1.4F)
            .addAnimation(FrostbittenModel.HEAD, rotation(
                    rot(0F, 0F, 0F, 0F), rot(0.8F, -30F, 0F, 0F), rot(1.0F, -32F, 0F, 0F), rot(1.1F, 20F, 0F, 0F, true), rot(1.4F, 0F, 0F, 0F)))
            .addAnimation(FrostbittenModel.HEAD, position(
                    pos(0F, 0F, 0F, 0F), pos(1.0F, 0F, 0F, 1F), pos(1.1F, 0F, 0F, -2F, true), pos(1.4F, 0F, 0F, 0F)))
            .addAnimation(FrostbittenModel.RIGHT_ARM, rotation(
                    rot(0F, 0F, 0F, 0F), rot(0.8F, 30F, 0F, 20F), rot(1.0F, 30F, 0F, 22F), rot(1.1F, -20F, 0F, 10F), rot(1.4F, 0F, 0F, 0F)))
            .addAnimation(FrostbittenModel.LEFT_ARM, rotation(
                    rot(0F, 0F, 0F, 0F), rot(0.8F, 30F, 0F, -20F), rot(1.0F, 30F, 0F, -22F), rot(1.1F, -20F, 0F, -10F), rot(1.4F, 0F, 0F, 0F)))
            .addAnimation(FrostbittenModel.BODY, scaling(
                    scale(0F, 1F, 1F, 1F), scale(1.0F, 1.08F, 1.02F, 1.15F), scale(1.15F, 1F, 1F, 1F)))
            .build();

    public static final AnimationDefinition SEAL = clip(1.2F)
            .addAnimation(FrostbittenModel.RIGHT_ARM, rotation(
                    rot(0F, 0F, 0F, 0F), rot(0.9F, -170F, 0F, 15F), rot(1.0F, -40F, 0F, 5F, true), rot(1.2F, 0F, 0F, 0F)))
            .addAnimation(FrostbittenModel.LEFT_ARM, rotation(
                    rot(0F, 0F, 0F, 0F), rot(0.9F, -170F, 0F, -15F), rot(1.0F, -40F, 0F, -5F, true), rot(1.2F, 0F, 0F, 0F)))
            .addAnimation(FrostbittenModel.BODY, rotation(
                    rot(0F, 0F, 0F, 0F), rot(0.9F, -8F, 0F, 0F), rot(1.0F, 15F, 0F, 0F, true), rot(1.2F, 0F, 0F, 0F)))
            .addAnimation(FrostbittenModel.HEAD, rotation(
                    rot(0F, 0F, 0F, 0F), rot(0.9F, -20F, 0F, 0F), rot(1.0F, 15F, 0F, 0F, true), rot(1.2F, 0F, 0F, 0F)))
            .build();

    private FrostbittenAnimations() {
    }
}
