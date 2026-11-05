package com.flameforge.essentialcraft5.client.model.mob.anim;

import com.flameforge.essentialcraft5.client.model.mob.MatrixWraithModel;
import net.minecraft.client.animation.AnimationDefinition;

import static com.flameforge.essentialcraft5.client.model.mob.anim.MobKeyframes.*;

/**
 * 矩阵游魂的关键帧（13c-M1）：
 * <ul>
 *   <li>{@link #ATTACK}：双爪往下一抓（0.5 秒，近战命中时播）；</li>
 *   <li>{@link #BLINK_OUT}：精英闪身前的 0.5 秒（{@code BLINK_WINDUP} = 10 刻）——缩成一团、双臂交叉护身，给你反应的时间；</li>
 *   <li>{@link #BLINK_IN}：在你身后现身（0.4 秒）——从小胀回来，略冲过头。</li>
 * </ul>
 */
public final class MatrixWraithAnimations {

    public static final AnimationDefinition ATTACK = clip(0.5F)
            .addAnimation(MatrixWraithModel.RIGHT_ARM, rotation(
                    rot(0F, 0F, 0F, 0F), rot(0.15F, -85F, 0F, -10F), rot(0.3F, 35F, 0F, 0F, true), rot(0.5F, 0F, 0F, 0F)))
            .addAnimation(MatrixWraithModel.LEFT_ARM, rotation(
                    rot(0F, 0F, 0F, 0F), rot(0.15F, -85F, 0F, 10F), rot(0.3F, 35F, 0F, 0F, true), rot(0.5F, 0F, 0F, 0F)))
            .addAnimation(MatrixWraithModel.BODY, rotation(
                    rot(0F, 0F, 0F, 0F), rot(0.3F, 15F, 0F, 0F), rot(0.5F, 0F, 0F, 0F)))
            .addAnimation(MatrixWraithModel.HEAD, position(
                    pos(0F, 0F, 0F, 0F), pos(0.3F, 0F, -1F, -1.5F), pos(0.5F, 0F, 0F, 0F)))
            .build();

    public static final AnimationDefinition BLINK_OUT = clip(0.5F)
            .addAnimation(MatrixWraithModel.WRAITH, scaling(
                    scale(0F, 1F, 1F, 1F), scale(0.4F, 0.75F, 0.85F, 0.75F), scale(0.5F, 0.4F, 0.4F, 0.4F, true)))
            .addAnimation(MatrixWraithModel.RIGHT_ARM, rotation(
                    rot(0F, 0F, 0F, 0F), rot(0.3F, -60F, 0F, 45F), rot(0.5F, -60F, 0F, 45F)))
            .addAnimation(MatrixWraithModel.LEFT_ARM, rotation(
                    rot(0F, 0F, 0F, 0F), rot(0.3F, -60F, 0F, -45F), rot(0.5F, -60F, 0F, -45F)))
            .build();

    public static final AnimationDefinition BLINK_IN = clip(0.4F)
            .addAnimation(MatrixWraithModel.WRAITH, scaling(
                    scale(0F, 0.4F, 0.4F, 0.4F), scale(0.2F, 1.12F, 1.08F, 1.12F), scale(0.4F, 1F, 1F, 1F)))
            .addAnimation(MatrixWraithModel.RIGHT_ARM, rotation(
                    rot(0F, -40F, 0F, 30F), rot(0.4F, 0F, 0F, 0F)))
            .addAnimation(MatrixWraithModel.LEFT_ARM, rotation(
                    rot(0F, -40F, 0F, -30F), rot(0.4F, 0F, 0F, 0F)))
            .build();

    private MatrixWraithAnimations() {
    }
}
