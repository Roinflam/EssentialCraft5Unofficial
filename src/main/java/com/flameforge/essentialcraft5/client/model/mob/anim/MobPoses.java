package com.flameforge.essentialcraft5.client.model.mob.anim;

import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * 常用姿势工具（{@link ECAnimatedModel#pose} 里用；也给不继承它的模型用）。转角输入都是度，内部换成弧度。
 */
public final class MobPoses {

    private static final float DEG = Mth.DEG_TO_RAD;
    private static final Vector3f CACHE = new Vector3f();

    private MobPoses() {
    }

    /** 所有部位回到 {@code LayerDefinition} 里的初始姿势 */
    public static void resetPose(ModelPart root) {
        root.getAllParts().forEach(ModelPart::resetPose);
    }

    /** 头跟着视线转（netHeadYaw / headPitch 原样传进来，单位度）；maxYaw、maxPitch 夹住转角 */
    public static void look(ModelPart head, float netHeadYaw, float headPitch, float maxYaw, float maxPitch) {
        head.yRot += Mth.clamp(netHeadYaw, -maxYaw, maxYaw) * DEG;
        head.xRot += Mth.clamp(headPitch, -maxPitch, maxPitch) * DEG;
    }

    /** 走路时一对肢体前后摆（同原版人形：左右相位差半圈）；amplitude 是最大摆角（度） */
    public static void swingPair(ModelPart left, ModelPart right, float limbSwing, float limbSwingAmount, float speed, float amplitude) {
        float swing = Mth.cos(limbSwing * speed) * amplitude * DEG * limbSwingAmount;
        left.xRot += swing;
        right.xRot -= swing;
    }

    /** 上下悬浮：周期 periodTicks 刻、振幅 amplitude 像素（向下为正，同模型坐标） */
    public static void hover(ModelPart part, float ageInTicks, float periodTicks, float amplitude) {
        part.y += Mth.sin(ageInTicks * Mth.TWO_PI / Math.max(1F, periodTicks)) * amplitude;
    }

    /** 绕 Y 轴匀速转：每刻 degreesPerTick 度 */
    public static void spin(ModelPart part, float ageInTicks, float degreesPerTick) {
        part.yRot += Mth.wrapDegrees(ageInTicks * degreesPerTick) * DEG;
    }

    /** 轻微呼吸：沿 Y 缩放 1 ± amount，周期 periodTicks 刻 */
    public static void breathe(ModelPart part, float ageInTicks, float periodTicks, float amount) {
        float s = 1F + Mth.sin(ageInTicks * Mth.TWO_PI / Math.max(1F, periodTicks)) * amount;
        part.yScale *= s;
    }

    /**
     * 把一段动画定格在第 seconds 秒（展示厅拍关键姿势、手册预览用）：先回到初始姿势，再按那一刻的关键帧摆好。
     * 要叠在程序姿势上的，先调这个再自己加。
     */
    public static void applyAt(HierarchicalModel<?> model, AnimationDefinition definition, float seconds) {
        resetPose(model.root());
        float clamped = Math.max(0F, Math.min(seconds, definition.lengthInSeconds()));
        KeyframeAnimations.animate(model, definition, (long) (clamped * 1000F), 1F, CACHE);
    }
}
