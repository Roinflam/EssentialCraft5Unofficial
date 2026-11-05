package com.flameforge.essentialcraft5.entity.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 会飞的新怪物（13c-Fa，batch13.md §5.1）：{@link ECMonster} 的全部规矩照旧，另外换成原版蜜蜂 / 鹦鹉那一套飞行移动——
 * {@link FlyingMoveControl}（能悬停）+ {@link FlyingPathNavigation}（不开门、能穿过开着的门），不受重力、不摔伤、不触发踩踏。
 * 元素游灵、炼狱小鬼这类用它；悬浮的节奏（绕飞、不规则飞）由子类的目标 / 移动控制写。
 * <p>
 * 子类的属性要带 {@code Attributes.FLYING_SPEED}（{@link FlyingMoveControl} 在空中按它走）：{@code ECMonster.createMonsterAttributes().add(Attributes.FLYING_SPEED, …)}。
 */
public abstract class ECFlyingMonster extends ECMonster {

    /** 飞行移动控制的最大转身角（度 / 刻），同原版蜜蜂 */
    public static final int MAX_TURN = 20;

    protected ECFlyingMonster(EntityType<? extends ECFlyingMonster> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, MAX_TURN, true);
        setNoGravity(true);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        navigation.setCanPassDoors(true);
        return navigation;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }
}
