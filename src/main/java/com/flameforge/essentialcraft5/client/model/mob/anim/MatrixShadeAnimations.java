package com.flameforge.essentialcraft5.client.model.mob.anim;

import com.flameforge.essentialcraft5.client.model.mob.MatrixShadeModel;
import com.flameforge.essentialcraft5.entity.boss.MatrixShadeEntity;
import net.minecraft.client.animation.AnimationDefinition;
import org.jetbrains.annotations.Nullable;

import static com.flameforge.essentialcraft5.client.model.mob.anim.MobKeyframes.clip;
import static com.flameforge.essentialcraft5.client.model.mob.anim.MobKeyframes.pos;
import static com.flameforge.essentialcraft5.client.model.mob.anim.MobKeyframes.position;
import static com.flameforge.essentialcraft5.client.model.mob.anim.MobKeyframes.rot;
import static com.flameforge.essentialcraft5.client.model.mob.anim.MobKeyframes.rotation;
import static com.flameforge.essentialcraft5.client.model.mob.anim.MobKeyframes.scale;
import static com.flameforge.essentialcraft5.client.model.mob.anim.MobKeyframes.scaling;

/**
 * 矩阵之影（和镜影）的关键帧，按 {@link MatrixShadeEntity} 的动画段号排。都是一次性的段，最后一帧回到初始姿势（散去那段除外：停在缩小下沉的样子）。
 * 时间单位秒、位移像素、转角度（{@link MobKeyframes}）。只在客户端加载。
 */
public final class MatrixShadeAnimations {

    /** 左拳：抡起来砸下去 */
    public static final AnimationDefinition FIST_LEFT = clip(0.6F)
            .addAnimation(MatrixShadeModel.LEFT_ARM, rotation(
                    rot(0F, 0, 0, 0), rot(0.2F, -120, 0, -10), rot(0.32F, -40, 0, 0, true), rot(0.6F, 0, 0, 0)))
            .addAnimation(MatrixShadeModel.BODY, rotation(rot(0F, 0, 0, 0), rot(0.32F, 8, -12, 0), rot(0.6F, 0, 0, 0)))
            .build();

    /** 右拳 */
    public static final AnimationDefinition FIST_RIGHT = clip(0.6F)
            .addAnimation(MatrixShadeModel.RIGHT_ARM, rotation(
                    rot(0F, 0, 0, 0), rot(0.2F, -120, 0, 10), rot(0.32F, -40, 0, 0, true), rot(0.6F, 0, 0, 0)))
            .addAnimation(MatrixShadeModel.BODY, rotation(rot(0F, 0, 0, 0), rot(0.32F, 8, 12, 0), rot(0.6F, 0, 0, 0)))
            .build();

    /** 迟滞之握：右手伸出去、拳头张开再攥紧 */
    public static final AnimationDefinition GRIP = clip(1.2F)
            .addAnimation(MatrixShadeModel.RIGHT_ARM, rotation(
                    rot(0F, 0, 0, 0), rot(0.4F, -95, 15, 0), rot(0.8F, -90, 10, 0), rot(1.2F, 0, 0, 0)))
            .addAnimation(MatrixShadeModel.RIGHT_FIST, scaling(
                    scale(0F, 1, 1, 1), scale(0.4F, 1.3F, 1.3F, 1.3F), scale(0.5F, 0.8F, 0.8F, 0.8F, true), scale(1.2F, 1, 1, 1)))
            .build();

    /** 坠身：腾起 1 秒，砸下去（硬落） */
    public static final AnimationDefinition SLAM = clip(1.3F)
            .addAnimation(MatrixShadeModel.BODY, position(
                    pos(0F, 0, 0, 0), pos(0.5F, 0, 14, 0), pos(0.95F, 0, 16, 0), pos(1.05F, 0, -2, 0, true), pos(1.3F, 0, 0, 0)))
            .addAnimation(MatrixShadeModel.RIGHT_ARM, rotation(
                    rot(0F, 0, 0, 0), rot(0.6F, -170, 0, 10), rot(1.05F, -20, 0, 0, true), rot(1.3F, 0, 0, 0)))
            .addAnimation(MatrixShadeModel.LEFT_ARM, rotation(
                    rot(0F, 0, 0, 0), rot(0.6F, -170, 0, -10), rot(1.05F, -20, 0, 0, true), rot(1.3F, 0, 0, 0)))
            .build();

    /** 施法（镜影、心智钉、失序之柱）：两臂向两侧张开上举，碎晶环放大 */
    public static final AnimationDefinition CAST = clip(1.1F)
            .addAnimation(MatrixShadeModel.RIGHT_ARM, rotation(rot(0F, 0, 0, 0), rot(0.4F, -60, 0, 70), rot(0.8F, -60, 0, 75), rot(1.1F, 0, 0, 0)))
            .addAnimation(MatrixShadeModel.LEFT_ARM, rotation(rot(0F, 0, 0, 0), rot(0.4F, -60, 0, -70), rot(0.8F, -60, 0, -75), rot(1.1F, 0, 0, 0)))
            .addAnimation(MatrixShadeModel.HALO, scaling(scale(0F, 1, 1, 1), scale(0.5F, 1.6F, 1.6F, 1), scale(1.1F, 1, 1, 1)))
            .addAnimation(MatrixShadeModel.HEAD, rotation(rot(0F, 0, 0, 0), rot(0.5F, -20, 0, 0), rot(1.1F, 0, 0, 0)))
            .build();

    /** 低语：前倾、歪头，两秒 */
    public static final AnimationDefinition WHISPER = clip(2.0F)
            .addAnimation(MatrixShadeModel.BODY, rotation(rot(0F, 0, 0, 0), rot(0.5F, 14, 0, 0), rot(1.6F, 16, 0, 0), rot(2.0F, 0, 0, 0)))
            .addAnimation(MatrixShadeModel.HEAD, rotation(rot(0F, 0, 0, 0), rot(0.5F, 10, 0, 18), rot(1.6F, 12, 0, -14), rot(2.0F, 0, 0, 0)))
            .build();

    /** 矩阵电弧：右手指向前方蓄 1 秒，左手往后收 */
    public static final AnimationDefinition ARC = clip(1.3F)
            .addAnimation(MatrixShadeModel.RIGHT_ARM, rotation(rot(0F, 0, 0, 0), rot(0.5F, -90, -10, 0), rot(1.0F, -95, -10, 0), rot(1.3F, 0, 0, 0)))
            .addAnimation(MatrixShadeModel.LEFT_ARM, rotation(rot(0F, 0, 0, 0), rot(0.5F, 35, 0, -20), rot(1.0F, 40, 0, -20), rot(1.3F, 0, 0, 0)))
            .addAnimation(MatrixShadeModel.CHEST_GRID, scaling(scale(0F, 1, 1, 1), scale(0.9F, 1.4F, 1.4F, 1.4F), scale(1.05F, 1, 1, 1, true)))
            .build();

    /** 收束：两臂张到最开，两秒里往胸前合拢，合上那一下震开 */
    public static final AnimationDefinition CONVERGE = clip(2.3F)
            .addAnimation(MatrixShadeModel.RIGHT_ARM, rotation(rot(0F, 0, 0, 0), rot(0.3F, -10, 0, 95), rot(1.9F, -80, -40, 20), rot(2.0F, -85, -50, 0, true), rot(2.3F, 0, 0, 0)))
            .addAnimation(MatrixShadeModel.LEFT_ARM, rotation(rot(0F, 0, 0, 0), rot(0.3F, -10, 0, -95), rot(1.9F, -80, 40, -20), rot(2.0F, -85, 50, 0, true), rot(2.3F, 0, 0, 0)))
            .addAnimation(MatrixShadeModel.BODY, scaling(scale(0F, 1, 1, 1), scale(1.9F, 0.95F, 0.95F, 0.95F), scale(2.05F, 1.08F, 1.08F, 1.08F, true), scale(2.3F, 1, 1, 1)))
            .build();

    /** 换相：升起、原地转一圈 */
    public static final AnimationDefinition PHASE = clip(2.0F)
            .addAnimation(MatrixShadeModel.BODY, position(pos(0F, 0, 0, 0), pos(0.6F, 0, 6, 0), pos(1.6F, 0, 6, 0), pos(2.0F, 0, 0, 0)))
            .addAnimation(MatrixShadeModel.BODY, rotation(rot(0F, 0, 0, 0), rot(1.6F, 0, 360, 0, true), rot(2.0F, 0, 360, 0)))
            .addAnimation(MatrixShadeModel.HEAD, rotation(rot(0F, 0, 0, 0), rot(0.6F, -30, 0, 0), rot(1.6F, -30, 0, 0), rot(2.0F, 0, 0, 0)))
            .addAnimation(MatrixShadeModel.RIGHT_ARM, rotation(rot(0F, 0, 0, 0), rot(0.6F, 0, 0, 40), rot(1.6F, 0, 0, 40), rot(2.0F, 0, 0, 0)))
            .addAnimation(MatrixShadeModel.LEFT_ARM, rotation(rot(0F, 0, 0, 0), rot(0.6F, 0, 0, -40), rot(1.6F, 0, 0, -40), rot(2.0F, 0, 0, 0)))
            .build();

    /** 散去：10 秒里慢慢缩小、下沉、两臂垂下（停在最后一帧） */
    public static final AnimationDefinition FADE = clip(10.0F)
            .addAnimation(MatrixShadeModel.BODY, position(pos(0F, 0, 0, 0), pos(10F, 0, -10, 0, true)))
            .addAnimation(MatrixShadeModel.BODY, scaling(scale(0F, 1, 1, 1), scale(10F, 0.5F, 0.5F, 0.5F, true)))
            .addAnimation(MatrixShadeModel.HEAD, rotation(rot(0F, 0, 0, 0), rot(2F, 30, 0, 0), rot(10F, 35, 0, 0)))
            .addAnimation(MatrixShadeModel.RIGHT_ARM, rotation(rot(0F, 0, 0, 0), rot(2F, 15, 0, -10), rot(10F, 15, 0, -10)))
            .addAnimation(MatrixShadeModel.LEFT_ARM, rotation(rot(0F, 0, 0, 0), rot(2F, 15, 0, 10), rot(10F, 15, 0, 10)))
            .build();

    private static final AnimationDefinition[] BY_SLOT = new AnimationDefinition[MatrixShadeEntity.ANIM_SLOTS];

    static {
        BY_SLOT[MatrixShadeEntity.ANIM_FIST_LEFT] = FIST_LEFT;
        BY_SLOT[MatrixShadeEntity.ANIM_FIST_RIGHT] = FIST_RIGHT;
        BY_SLOT[MatrixShadeEntity.ANIM_GRIP] = GRIP;
        BY_SLOT[MatrixShadeEntity.ANIM_SLAM] = SLAM;
        BY_SLOT[MatrixShadeEntity.ANIM_CAST] = CAST;
        BY_SLOT[MatrixShadeEntity.ANIM_WHISPER] = WHISPER;
        BY_SLOT[MatrixShadeEntity.ANIM_ARC] = ARC;
        BY_SLOT[MatrixShadeEntity.ANIM_CONVERGE] = CONVERGE;
        BY_SLOT[MatrixShadeEntity.ANIM_PHASE] = PHASE;
        BY_SLOT[MatrixShadeEntity.ANIM_FADE] = FADE;
    }

    private MatrixShadeAnimations() {
    }

    @Nullable
    public static AnimationDefinition forSlot(int slot) {
        return slot >= 0 && slot < BY_SLOT.length ? BY_SLOT[slot] : null;
    }
}
