package com.flameforge.essentialcraft5.client.model.mob.anim;

import com.flameforge.essentialcraft5.entity.anim.MobAnimStates;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * 新怪物模型的基类（第十三批 13c-Fb，batch13.md §10「模型」）：手写 {@code LayerDefinition} + 原版 1.20 关键帧，按实体上的
 * {@link MobAnimStates} 播。每帧的顺序：
 * <ol>
 *   <li>所有部位回到初始姿势（{@code resetPose}，关键帧都是在初始姿势上叠加的）；</li>
 *   <li>{@link #pose}：程序算的常驻姿势（转头、走路摆臂、悬浮，用 {@link MobPoses} 里的工具）；</li>
 *   <li>{@link MobAnimStates} 里每一段正在播的动画（{@link #animation} 给出第几段对应哪个 {@link AnimationDefinition}），
 *       按 {@link MobAnimStates#speed()} 播（展示厅定格时是 0，画面停住）。</li>
 * </ol>
 * 子类给 {@link #root()} 以外只要实现 {@link #animStates} 和 {@link #animation}；没有动画的模型 {@link #animStates} 返回 null。只在客户端加载。
 */
public abstract class ECAnimatedModel<T extends Entity> extends HierarchicalModel<T> {

    private final ModelPart root;

    protected ECAnimatedModel(ModelPart root) {
        this(root, RenderType::entityCutoutNoCull);
    }

    protected ECAnimatedModel(ModelPart root, Function<ResourceLocation, RenderType> renderType) {
        super(renderType);
        this.root = root;
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        MobPoses.resetPose(root);
        pose(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        MobAnimStates states = animStates(entity);
        if (states == null) {
            return;
        }
        float speed = states.speed();
        for (int slot = 0; slot < states.size(); slot++) {
            AnimationDefinition definition = animation(slot);
            if (definition != null) {
                animate(states.get(slot), definition, ageInTicks, speed);
            }
        }
    }

    /** 程序算的常驻姿势（关键帧之前；默认什么都不做） */
    protected void pose(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
    }

    /** 这只实体的动画状态（通常是实体上的 {@code public final MobAnimStates anims}）；没有返回 null */
    protected abstract @Nullable MobAnimStates animStates(T entity);

    /** 第 slot 段对应的动画；这一段不在这个模型上播返回 null */
    protected abstract @Nullable AnimationDefinition animation(int slot);
}
