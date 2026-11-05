package com.flameforge.essentialcraft5.client.model.mob.anim;

import com.flameforge.essentialcraft5.client.model.mob.HoannaScavengerModel;
import net.minecraft.client.animation.AnimationDefinition;

import static com.flameforge.essentialcraft5.client.model.mob.anim.MobKeyframes.*;

/**
 * 遗民拾荒者的关键帧（13d-N1）：
 * <ul>
 *   <li>{@link #SWING}：撬棍从肩上往下抡（0.5 秒，近战命中时播）；</li>
 *   <li>{@link #AIM}：举枪抵肩瞄准（2 秒 = {@code rifle_aim_ticks} 40 刻），左手托住枪身、头往枪上贴；</li>
 *   <li>{@link #SHOOT}：开枪的后坐（0.3 秒）；</li>
 *   <li>{@link #STIM}：左手往脖子上扎一针（0.8 秒）；</li>
 *   <li>{@link #FLARE}：遗民队长右手把信号弹举过头顶打上天（1 秒）。</li>
 * </ul>
 */
public final class HoannaScavengerAnimations {

    public static final AnimationDefinition SWING = clip(0.5F)
            .addAnimation(HoannaScavengerModel.RIGHT_ARM, rotation(
                    rot(0F, 0F, 0F, 0F), rot(0.15F, -150F, 0F, 15F), rot(0.3F, 10F, 0F, -5F, true), rot(0.5F, 0F, 0F, 0F)))
            .addAnimation(HoannaScavengerModel.BODY, rotation(
                    rot(0F, 0F, 0F, 0F), rot(0.15F, 0F, 15F, 0F), rot(0.3F, 10F, -15F, 0F), rot(0.5F, 0F, 0F, 0F)))
            .build();

    public static final AnimationDefinition AIM = clip(2.0F)
            .addAnimation(HoannaScavengerModel.RIGHT_ARM, rotation(
                    rot(0F, 0F, 0F, 0F), rot(0.3F, -55F, -10F, 0F), rot(2.0F, -55F, -10F, 0F)))
            .addAnimation(HoannaScavengerModel.LEFT_ARM, rotation(
                    rot(0F, 0F, 0F, 0F), rot(0.3F, -50F, 35F, 0F), rot(2.0F, -50F, 35F, 0F)))
            .addAnimation(HoannaScavengerModel.HEAD, rotation(
                    rot(0F, 0F, 0F, 0F), rot(0.3F, 5F, -8F, 0F), rot(2.0F, 5F, -8F, 0F)))
            .build();

    public static final AnimationDefinition SHOOT = clip(0.3F)
            .addAnimation(HoannaScavengerModel.RIGHT_ARM, rotation(
                    rot(0F, -70F, -10F, 0F), rot(0.06F, -85F, -10F, 0F, true), rot(0.3F, 0F, 0F, 0F)))
            .addAnimation(HoannaScavengerModel.BODY, position(
                    pos(0F, 0F, 0F, 0F), pos(0.06F, 0F, 0F, 0.8F, true), pos(0.3F, 0F, 0F, 0F)))
            .build();

    public static final AnimationDefinition STIM = clip(0.8F)
            .addAnimation(HoannaScavengerModel.LEFT_ARM, rotation(
                    rot(0F, 0F, 0F, 0F), rot(0.3F, -120F, -30F, 30F), rot(0.45F, -135F, -30F, 35F, true), rot(0.8F, 0F, 0F, 0F)))
            .addAnimation(HoannaScavengerModel.HEAD, rotation(
                    rot(0F, 0F, 0F, 0F), rot(0.45F, 0F, 0F, 18F), rot(0.8F, 0F, 0F, 0F)))
            .build();

    public static final AnimationDefinition FLARE = clip(1.0F)
            .addAnimation(HoannaScavengerModel.RIGHT_ARM, rotation(
                    rot(0F, 0F, 0F, 0F), rot(0.4F, -175F, 0F, 10F), rot(0.7F, -175F, 0F, 10F), rot(1.0F, 0F, 0F, 0F)))
            .addAnimation(HoannaScavengerModel.HEAD, rotation(
                    rot(0F, 0F, 0F, 0F), rot(0.4F, -35F, 0F, 0F), rot(0.8F, -35F, 0F, 0F), rot(1.0F, 0F, 0F, 0F)))
            .build();

    private HoannaScavengerAnimations() {
    }
}
