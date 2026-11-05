package com.flameforge.essentialcraft5.client.model.mob.anim;

import com.flameforge.essentialcraft5.client.model.mob.AbyssalEelModel;
import net.minecraft.client.animation.AnimationDefinition;

import static com.flameforge.essentialcraft5.client.model.mob.anim.MobKeyframes.*;

/**
 * 渊鳗的关键帧（13d-N2）：
 * <ul>
 *   <li>{@link #BITE}：0.4 秒——头往前一窜、下颌张开再咬合；</li>
 *   <li>{@link #DRAG}：1 秒（同默认下拽时长 {@code drag_ticks} = 20）——整条往下扎，身子卷起来，尾鳍使劲摆；</li>
 *   <li>{@link #CALL}：0.6 秒——渊鳗群首张大嘴、尾巴甩两下（叫帮手）。</li>
 * </ul>
 */
public final class AbyssalEelAnimations {

    public static final AnimationDefinition BITE = clip(0.4F)
            .addAnimation(AbyssalEelModel.HEAD, position(pos(0F, 0F, 0F, 0F), pos(0.15F, 0F, 0F, -2.5F), pos(0.4F, 0F, 0F, 0F)))
            .addAnimation(AbyssalEelModel.JAW, rotation(rot(0F, 0F, 0F, 0F), rot(0.12F, 40F, 0F, 0F), rot(0.2F, 0F, 0F, 0F, true), rot(0.4F, 0F, 0F, 0F)))
            .build();

    public static final AnimationDefinition DRAG = clip(1.0F)
            .addAnimation(AbyssalEelModel.EEL, rotation(rot(0F, 0F, 0F, 0F), rot(0.2F, 35F, 0F, 0F), rot(0.8F, 35F, 0F, 0F), rot(1.0F, 0F, 0F, 0F)))
            .addAnimation(AbyssalEelModel.BODY_FRONT, rotation(rot(0F, 0F, 0F, 0F), rot(0.2F, -20F, 0F, 0F), rot(0.8F, -20F, 0F, 0F), rot(1.0F, 0F, 0F, 0F)))
            .addAnimation(AbyssalEelModel.TAIL, rotation(rot(0F, 0F, 0F, 0F), rot(0.15F, 0F, 40F, 0F), rot(0.3F, 0F, -40F, 0F), rot(0.45F, 0F, 40F, 0F),
                    rot(0.6F, 0F, -40F, 0F), rot(0.75F, 0F, 40F, 0F), rot(1.0F, 0F, 0F, 0F)))
            .build();

    public static final AnimationDefinition CALL = clip(0.6F)
            .addAnimation(AbyssalEelModel.JAW, rotation(rot(0F, 0F, 0F, 0F), rot(0.15F, 50F, 0F, 0F), rot(0.45F, 50F, 0F, 0F), rot(0.6F, 0F, 0F, 0F)))
            .addAnimation(AbyssalEelModel.HEAD, rotation(rot(0F, 0F, 0F, 0F), rot(0.15F, -15F, 0F, 0F), rot(0.6F, 0F, 0F, 0F)))
            .addAnimation(AbyssalEelModel.TAIL, rotation(rot(0F, 0F, 0F, 0F), rot(0.2F, 0F, 45F, 0F), rot(0.4F, 0F, -45F, 0F), rot(0.6F, 0F, 0F, 0F)))
            .build();

    private AbyssalEelAnimations() {
    }
}
