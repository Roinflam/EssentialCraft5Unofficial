package com.flameforge.essentialcraft5.client.model.mob.anim;

import com.flameforge.essentialcraft5.client.model.mob.ElementalWispModel;
import net.minecraft.client.animation.AnimationDefinition;

import static com.flameforge.essentialcraft5.client.model.mob.anim.MobKeyframes.*;

/**
 * 元素游灵的关键帧（13c-M1）。时长和实体上的阶段对齐：
 * <ul>
 *   <li>{@link #CHARGE}：蓄力 0.5 秒（{@code CHARGE_TICKS} = 10）——缩一下、碎晶收拢猛转，然后鼓起来；最后一帧回到原样（原版不循环的动画停在最后一帧，蓄完没冲出去也不会一直鼓着）；</li>
 *   <li>{@link #DASH}：冲撞 0.7 秒（{@code DASH_TICKS} = 14）——整团前倾拉长，尾巴甩直；</li>
 *   <li>{@link #CAST}：精英出弹 0.4 秒——光壳一胀。</li>
 * </ul>
 */
public final class ElementalWispAnimations {

    public static final AnimationDefinition CHARGE = clip(0.5F)
            .addAnimation(ElementalWispModel.WISP, scaling(
                    scale(0F, 1F, 1F, 1F), scale(0.25F, 0.8F, 0.8F, 0.8F), scale(0.42F, 1.2F, 1.2F, 1.2F), scale(0.5F, 1F, 1F, 1F)))
            .addAnimation(ElementalWispModel.SHARDS, scaling(
                    scale(0F, 1F, 1F, 1F), scale(0.3F, 0.55F, 1F, 0.55F), scale(0.5F, 1F, 1F, 1F)))
            .addAnimation(ElementalWispModel.SHARDS, rotation(
                    rot(0F, 0F, 0F, 0F), rot(0.5F, 0F, 270F, 0F, true)))
            .build();

    public static final AnimationDefinition DASH = clip(0.7F)
            .addAnimation(ElementalWispModel.WISP, rotation(
                    rot(0F, -35F, 0F, 0F), rot(0.5F, -35F, 0F, 0F), rot(0.7F, 0F, 0F, 0F)))
            .addAnimation(ElementalWispModel.WISP, scaling(
                    scale(0F, 0.9F, 0.9F, 1.35F), scale(0.5F, 0.9F, 0.9F, 1.3F), scale(0.7F, 1F, 1F, 1F)))
            .addAnimation(ElementalWispModel.TAIL, rotation(
                    rot(0F, 70F, 0F, 0F), rot(0.5F, 65F, 0F, 0F), rot(0.7F, 0F, 0F, 0F)))
            .addAnimation(ElementalWispModel.TAIL_TIP, rotation(
                    rot(0F, 20F, 0F, 0F), rot(0.7F, 0F, 0F, 0F)))
            .build();

    public static final AnimationDefinition CAST = clip(0.4F)
            .addAnimation(ElementalWispModel.VEIL, scaling(
                    scale(0F, 1F, 1F, 1F), scale(0.12F, 1.45F, 1.45F, 1.45F), scale(0.4F, 1F, 1F, 1F)))
            .addAnimation(ElementalWispModel.WISP, position(
                    pos(0F, 0F, 0F, 0F), pos(0.1F, 0F, 0F, 1.5F), pos(0.4F, 0F, 0F, 0F)))
            .build();

    private ElementalWispAnimations() {
    }
}
