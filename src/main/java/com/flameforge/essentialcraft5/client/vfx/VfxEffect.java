package com.flameforge.essentialcraft5.client.vfx;

import net.minecraft.world.phys.AABB;

/**
 * 一个有寿命的特效（一次断罪、一道放电、一个预警圈……），交给 {@link VfxEffects#add} 管：每刻 {@link #tick}、每帧 {@link #render}。
 * 粒子不用自己管，放进 {@link VfxParticles} 就行；特效对象只画那些要按自己的时间轴变形的面片（环、法阵、光柱、拖尾）。
 */
public interface VfxEffect {

    /** 推进一刻；返回 false 就移除 */
    boolean tick();

    /** 画这一帧（只在 {@link #bounds()} 在视锥里、离镜头不太远时调） */
    void render(VfxDraw draw);

    /** 包住整个特效的盒子（视锥剔除用） */
    AABB bounds();
}
