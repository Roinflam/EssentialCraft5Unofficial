package com.flameforge.essentialcraft5.client.model.mob.anim;

import com.flameforge.essentialcraft5.client.model.mob.ManaPrismModel;
import net.minecraft.client.animation.AnimationDefinition;

import static com.flameforge.essentialcraft5.client.model.mob.anim.MobKeyframes.*;

/**
 * 浮游棱晶的关键帧（13d-N1）：
 * <ul>
 *   <li>{@link #LOCK}：锁定（1.5 秒 = {@code lock_ticks} 30 刻）——小棱片越转越快、往外张开，光点胀大，晶体微微收紧；看见它这样就该横移了；</li>
 *   <li>{@link #FIRE}：开火（0.4 秒）——光点一缩、整块晶体往后一顿。</li>
 * </ul>
 */
public final class ManaPrismAnimations {

    public static final AnimationDefinition LOCK = clip(1.5F)
            .addAnimation(ManaPrismModel.SHARDS, rotation(
                    rot(0F, 0F, 0F, 0F), rot(1.5F, 0F, 720F, 0F, true)))
            .addAnimation(ManaPrismModel.SHARDS, scaling(
                    scale(0F, 1F, 1F, 1F), scale(1.2F, 1.35F, 1F, 1.35F), scale(1.5F, 1.4F, 1F, 1.4F)))
            .addAnimation(ManaPrismModel.CORE, scaling(
                    scale(0F, 1F, 1F, 1F), scale(1.3F, 1.7F, 1.7F, 1.7F), scale(1.5F, 1.9F, 1.9F, 1.9F)))
            .addAnimation(ManaPrismModel.CRYSTAL, scaling(
                    scale(0F, 1F, 1F, 1F), scale(1.5F, 0.94F, 1.04F, 0.94F)))
            .build();

    public static final AnimationDefinition FIRE = clip(0.4F)
            .addAnimation(ManaPrismModel.CORE, scaling(
                    scale(0F, 0.6F, 0.6F, 0.6F), scale(0.4F, 1F, 1F, 1F)))
            .addAnimation(ManaPrismModel.PRISM, position(
                    pos(0F, 0F, 0F, 0F), pos(0.08F, 0F, -0.5F, 1.5F, true), pos(0.4F, 0F, 0F, 0F)))
            .build();

    private ManaPrismAnimations() {
    }
}
