package com.flameforge.essentialcraft5.client.model.mob.anim;

import com.flameforge.essentialcraft5.client.model.mob.NetherImpModel;
import com.flameforge.essentialcraft5.entity.mob.NetherImpEntity;
import net.minecraft.client.animation.AnimationDefinition;

import static com.flameforge.essentialcraft5.client.model.mob.anim.MobKeyframes.clip;
import static com.flameforge.essentialcraft5.client.model.mob.anim.MobKeyframes.pos;
import static com.flameforge.essentialcraft5.client.model.mob.anim.MobKeyframes.position;
import static com.flameforge.essentialcraft5.client.model.mob.anim.MobKeyframes.rot;
import static com.flameforge.essentialcraft5.client.model.mob.anim.MobKeyframes.rotation;

/**
 * 炼狱小鬼的关键帧（第十三批 13c-M3）。时间对着服务端的出手节奏：第一颗余火弹在起手后 {@link NetherImpEntity#WIND_UP} 刻（0.2 秒）发出，
 * 连发每颗隔 {@link NetherImpEntity#BURST_GAP} 刻（0.3 秒），叫帮手念 {@link NetherImpEntity#SUMMON_CAST_TICKS} 刻（1 秒）。
 * 展示厅定格的关键姿势：CAST 0.2 秒（甩手那一下）、BURST 0.5 秒（左手第二颗）、SUMMON 0.6 秒（举手张翅）。
 */
public final class NetherImpAnimations {

    /** 普通出手（0.5 秒）：右手往后一抡、往前甩出去 */
    public static final AnimationDefinition CAST = clip(0.5F)
            .addAnimation(NetherImpModel.RIGHT_ARM, rotation(rot(0F, 0, 0, 0), rot(0.15F, -140, 0, 20), rot(0.25F, -40, 0, 0, true),
                    rot(0.5F, 0, 0, 0)))
            .addAnimation(NetherImpModel.BODY, rotation(rot(0F, 0, 0, 0), rot(0.15F, -8, 10, 0), rot(0.25F, 10, -10, 0), rot(0.5F, 0, 0, 0)))
            .addAnimation(NetherImpModel.HEAD, rotation(rot(0F, 0, 0, 0), rot(0.2F, -10, 0, 0), rot(0.5F, 0, 0, 0)))
            .build();

    /** 小鬼头目连发（1 秒）：右、左、右三下，身子跟着左右拧 */
    public static final AnimationDefinition BURST = clip(1F)
            .addAnimation(NetherImpModel.RIGHT_ARM, rotation(rot(0F, 0, 0, 0), rot(0.12F, -140, 0, 20), rot(0.22F, -40, 0, 0, true),
                    rot(0.4F, 0, 0, 0), rot(0.68F, -140, 0, 20), rot(0.8F, -40, 0, 0, true), rot(1F, 0, 0, 0)))
            .addAnimation(NetherImpModel.LEFT_ARM, rotation(rot(0F, 0, 0, 0), rot(0.3F, 0, 0, 0), rot(0.42F, -140, 0, -20),
                    rot(0.52F, -40, 0, 0, true), rot(0.7F, 0, 0, 0), rot(1F, 0, 0, 0)))
            .addAnimation(NetherImpModel.BODY, rotation(rot(0F, 0, 0, 0), rot(0.2F, 5, -15, 0), rot(0.5F, 5, 15, 0), rot(0.8F, 5, -15, 0),
                    rot(1F, 0, 0, 0)))
            .build();

    /** 叫帮手（1 秒）：两手举过头、翅膀张到最开、身子往上一提 */
    public static final AnimationDefinition SUMMON = clip(1F)
            .addAnimation(NetherImpModel.RIGHT_ARM, rotation(rot(0F, 0, 0, 0), rot(0.3F, -170, 0, -30), rot(0.8F, -170, 0, -30), rot(1F, 0, 0, 0)))
            .addAnimation(NetherImpModel.LEFT_ARM, rotation(rot(0F, 0, 0, 0), rot(0.3F, -170, 0, 30), rot(0.8F, -170, 0, 30), rot(1F, 0, 0, 0)))
            .addAnimation(NetherImpModel.HEAD, rotation(rot(0F, 0, 0, 0), rot(0.3F, -25, 0, 0), rot(0.8F, -25, 0, 0), rot(1F, 0, 0, 0)))
            .addAnimation(NetherImpModel.RIGHT_WING, rotation(rot(0F, 0, 0, 0), rot(0.3F, 0, -35, 0), rot(0.8F, 0, -35, 0), rot(1F, 0, 0, 0)))
            .addAnimation(NetherImpModel.LEFT_WING, rotation(rot(0F, 0, 0, 0), rot(0.3F, 0, 35, 0), rot(0.8F, 0, 35, 0), rot(1F, 0, 0, 0)))
            .addAnimation(NetherImpModel.BODY, position(pos(0F, 0, 0, 0), pos(0.4F, 0, 1.5F, 0), pos(0.8F, 0, 1.5F, 0), pos(1F, 0, 0, 0)))
            .build();

    private NetherImpAnimations() {
    }
}
