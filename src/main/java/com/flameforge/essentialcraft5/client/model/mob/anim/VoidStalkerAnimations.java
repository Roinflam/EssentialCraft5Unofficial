package com.flameforge.essentialcraft5.client.model.mob.anim;

import com.flameforge.essentialcraft5.client.model.mob.VoidStalkerModel;
import net.minecraft.client.animation.AnimationDefinition;

import static com.flameforge.essentialcraft5.client.model.mob.anim.MobKeyframes.clip;
import static com.flameforge.essentialcraft5.client.model.mob.anim.MobKeyframes.pos;
import static com.flameforge.essentialcraft5.client.model.mob.anim.MobKeyframes.position;
import static com.flameforge.essentialcraft5.client.model.mob.anim.MobKeyframes.rot;
import static com.flameforge.essentialcraft5.client.model.mob.anim.MobKeyframes.rotation;
import static com.flameforge.essentialcraft5.client.model.mob.anim.MobKeyframes.scale;
import static com.flameforge.essentialcraft5.client.model.mob.anim.MobKeyframes.scaling;

/**
 * 虚空潜行者的关键帧（第十三批 13c-M3）。CLAW、RIFT_CLAW 在出手那一刻（服务端 doHurtTarget）开播，PHASE 在进入预警时开播（预警默认 0.5 秒），
 * PULL 在裂隙行者开始拉人时开播（拉人前的预警默认 0.75 秒，到点把人拉过来）。
 * 展示厅定格的关键姿势：CLAW 0.08 秒（抬爪顶点；0.15 秒已经挥过头、在收势）、RIFT_CLAW 0.3 秒（撕开那一下）、PHASE 0.3 秒（缩身抖）、PULL 0.5 秒（伸手扯）。
 */
public final class VoidStalkerAnimations {

    /** 普通一爪（0.6 秒）：右手从上往下斜劈 */
    public static final AnimationDefinition CLAW = clip(0.6F)
            .addAnimation(VoidStalkerModel.RIGHT_ARM, rotation(rot(0F, 0, 0, 0), rot(0.08F, -110, -15, 25), rot(0.15F, 20, 10, -10, true),
                    rot(0.6F, 0, 0, 0)))
            .addAnimation(VoidStalkerModel.BODY, rotation(rot(0F, 0, 0, 0), rot(0.08F, -5, 10, 0), rot(0.15F, 10, -12, 0), rot(0.6F, 0, 0, 0)))
            .build();

    /** 裂隙爪（0.8 秒）：两只爪子一起举高、往两边一撕 */
    public static final AnimationDefinition RIFT_CLAW = clip(0.8F)
            .addAnimation(VoidStalkerModel.RIGHT_ARM, rotation(rot(0F, 0, 0, 0), rot(0.2F, -150, 0, 20), rot(0.3F, -70, 0, 60, true),
                    rot(0.8F, 0, 0, 0)))
            .addAnimation(VoidStalkerModel.LEFT_ARM, rotation(rot(0F, 0, 0, 0), rot(0.2F, -150, 0, -20), rot(0.3F, -70, 0, -60, true),
                    rot(0.8F, 0, 0, 0)))
            .addAnimation(VoidStalkerModel.RIGHT_CLAW, scaling(scale(0F, 1, 1, 1), scale(0.25F, 1.3F, 1.3F, 1.3F), scale(0.8F, 1, 1, 1)))
            .addAnimation(VoidStalkerModel.LEFT_CLAW, scaling(scale(0F, 1, 1, 1), scale(0.25F, 1.3F, 1.3F, 1.3F), scale(0.8F, 1, 1, 1)))
            .addAnimation(VoidStalkerModel.BODY, rotation(rot(0F, 0, 0, 0), rot(0.2F, -15, 0, 0), rot(0.3F, 15, 0, 0, true), rot(0.8F, 0, 0, 0)))
            .build();

    /** 进相位前的预警（0.5 秒）：缩身、抖两下、碎晶往外飘 */
    public static final AnimationDefinition PHASE = clip(0.5F)
            .addAnimation(VoidStalkerModel.BODY, rotation(rot(0F, 0, 0, 0), rot(0.1F, 15, 6, 0), rot(0.2F, 15, -6, 0), rot(0.3F, 15, 6, 0),
                    rot(0.4F, 15, -6, 0), rot(0.5F, 0, 0, 0)))
            .addAnimation(VoidStalkerModel.HEAD, rotation(rot(0F, 0, 0, 0), rot(0.25F, 20, 0, 0), rot(0.5F, 0, 0, 0)))
            .addAnimation(VoidStalkerModel.SHARD_UPPER, position(pos(0F, 0, 0, 0), pos(0.3F, 0, 1.5F, 2F), pos(0.5F, 0, 0, 0)))
            .addAnimation(VoidStalkerModel.SHARD_LOWER, position(pos(0F, 0, 0, 0), pos(0.3F, 0, -1F, 2F), pos(0.5F, 0, 0, 0)))
            .build();

    /** 裂隙行者拉人（0.75 秒）：左手往前伸直、五指张开，到点往回一扯 */
    public static final AnimationDefinition PULL = clip(0.75F)
            .addAnimation(VoidStalkerModel.LEFT_ARM, rotation(rot(0F, 0, 0, 0), rot(0.25F, -95, 10, 0), rot(0.65F, -95, 10, 0),
                    rot(0.72F, -40, 0, 0, true), rot(0.75F, 0, 0, 0)))
            .addAnimation(VoidStalkerModel.LEFT_CLAW, scaling(scale(0F, 1, 1, 1), scale(0.25F, 1.35F, 1.35F, 1.35F), scale(0.65F, 1.35F, 1.35F, 1.35F),
                    scale(0.75F, 1, 1, 1)))
            .addAnimation(VoidStalkerModel.RIGHT_ARM, rotation(rot(0F, 0, 0, 0), rot(0.25F, 20, 0, 30), rot(0.75F, 0, 0, 0)))
            .addAnimation(VoidStalkerModel.BODY, rotation(rot(0F, 0, 0, 0), rot(0.25F, 5, -20, 0), rot(0.65F, 5, -20, 0), rot(0.72F, -10, 10, 0),
                    rot(0.75F, 0, 0, 0)))
            .build();

    private VoidStalkerAnimations() {
    }
}
