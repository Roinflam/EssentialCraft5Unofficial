package com.flameforge.essentialcraft5.client.model.mob.anim;

import com.flameforge.essentialcraft5.client.model.mob.CatacombRevenantModel;
import com.flameforge.essentialcraft5.entity.mob.hoanna.CatacombRevenantEntity;
import net.minecraft.client.animation.AnimationDefinition;

import static com.flameforge.essentialcraft5.client.model.mob.anim.MobKeyframes.clip;
import static com.flameforge.essentialcraft5.client.model.mob.anim.MobKeyframes.rot;
import static com.flameforge.essentialcraft5.client.model.mob.anim.MobKeyframes.rotation;

/**
 * 墓穴守灵的关键帧（第十三批 13d-N3）。挥剑对着原版近战的出手（打中那一刻开始播），召灵刃的前摇是
 * {@link CatacombRevenantEntity#CALL_WIND_UP} 刻（0.5 秒），举到最高时灵刃出现。
 * 展示厅定格的关键姿势：SLASH 0.15 秒（剑举过肩）、CALL 0.5 秒（剑尖朝天）。
 */
public final class CatacombRevenantAnimations {

    /** 挥剑（0.5 秒）：右臂从肩后斜劈到身前，身子跟着拧 */
    public static final AnimationDefinition SLASH = clip(0.5F)
            .addAnimation(CatacombRevenantModel.RIGHT_ARM, rotation(rot(0F, 0, 0, 0), rot(0.15F, -110, -20, 25), rot(0.3F, 40, 15, -10, true),
                    rot(0.5F, 0, 0, 0)))
            .addAnimation(CatacombRevenantModel.BODY, rotation(rot(0F, 0, 0, 0), rot(0.15F, 0, -15, 0), rot(0.3F, 5, 15, 0), rot(0.5F, 0, 0, 0)))
            .addAnimation(CatacombRevenantModel.HEAD, rotation(rot(0F, 0, 0, 0), rot(0.3F, 8, 0, 0), rot(0.5F, 0, 0, 0)))
            .build();

    /** 守灵长召灵刃（1 秒）：剑尖朝天举起、左手往外一摊，停一下再放下 */
    public static final AnimationDefinition CALL = clip(1F)
            .addAnimation(CatacombRevenantModel.RIGHT_ARM, rotation(rot(0F, 0, 0, 0), rot(0.5F, -150, 0, 10), rot(0.8F, -150, 0, 10), rot(1F, 0, 0, 0)))
            .addAnimation(CatacombRevenantModel.LEFT_ARM, rotation(rot(0F, 0, 0, 0), rot(0.5F, -40, 0, -45), rot(0.8F, -40, 0, -45), rot(1F, 0, 0, 0)))
            .addAnimation(CatacombRevenantModel.HEAD, rotation(rot(0F, 0, 0, 0), rot(0.5F, -20, 0, 0), rot(0.8F, -20, 0, 0), rot(1F, 0, 0, 0)))
            .build();

    private CatacombRevenantAnimations() {
    }
}
