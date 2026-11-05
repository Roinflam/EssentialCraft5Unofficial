package com.flameforge.essentialcraft5.entity.anim;

import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;

import java.util.Arrays;

/**
 * 新怪物的动画状态与实体事件号的对照（第十三批 13c-Fb，batch13.md §5.1「框架」、§10「模型」）。动画用原版 1.20 的关键帧
 * （{@code HierarchicalModel} + {@code AnimationDefinition} + {@link AnimationState}），不加 GeckoLib、不加包：
 * 服务端要放一段动画就 {@link #play}，它用 {@code level.broadcastEntityEvent} 把「起第几段」发给看得见这只怪的客户端（一个字节，原版的实体事件包），
 * 客户端在实体的 {@code handleEntityEvent} 里交给 {@link #handle}，起对应的 {@link AnimationState}；模型在 {@code setupAnim} 里按状态播
 * （{@code client/model/mob/anim/ECAnimatedModel}）。common 类：两端都建，专用服上的那份只用来发事件。
 *
 * <h2>事件号</h2>
 * 第 i 段 = {@value #EVENT_BASE} + i（i 从 0 起，最多 {@value #MAX_SLOTS} 段，到 123）。原版 1.20.1 的实体事件号用到 63，
 * {@code handleEntityEvent} 又是按实体类各管各的，别的模组的实体不会收到；本模组的怪先拦下自己的号，其余照常交给父类。
 * <pre>{@code
 * public static final int ANIM_SLAM = 0, ANIM_ROAR = 1;
 * public final MobAnimStates anims = new MobAnimStates(2).channel(ANIM_SLAM, 1).channel(ANIM_ROAR, 1);
 * // 服务端出招时
 * anims.play(this, ANIM_SLAM);
 * // 实体类里
 * @Override public void handleEntityEvent(byte id) { if (!anims.handle(id, tickCount)) super.handleEntityEvent(id); }
 * }</pre>
 *
 * <h2>通道</h2>
 * 同一个通道（{@link #channel}）里的几段互斥：起一段就停掉同通道的别的段（重锤和吼叫不会同时播）；通道 0（默认）的各段互不影响。
 * 走路、待机这类持续动画不用事件，客户端每刻按状态 {@link #animateWhen}。
 * <p>
 * 展示厅拍定格姿势时 {@link #freeze} 让模型按 0 速度播（{@code ECAnimatedModel} 读 {@link #speed()}），画面停在那一帧。
 */
public final class MobAnimStates {

    /** 第 0 段的事件号 */
    public static final byte EVENT_BASE = 100;
    /** 最多几段（100~123） */
    public static final int MAX_SLOTS = 24;

    private final AnimationState[] states;
    private final int[] channels;
    private boolean frozen;

    /** slots 段动画（1~{@value #MAX_SLOTS}） */
    public MobAnimStates(int slots) {
        if (slots < 1 || slots > MAX_SLOTS) {
            throw new IllegalArgumentException("动画段数要在 1~" + MAX_SLOTS + " 之间：" + slots);
        }
        states = new AnimationState[slots];
        for (int i = 0; i < slots; i++) {
            states[i] = new AnimationState();
        }
        channels = new int[slots];
    }

    /** 把第 slot 段放进互斥通道 channel（≥ 1；0 = 不互斥）。返回自己，方便连着写 */
    public MobAnimStates channel(int slot, int channel) {
        check(slot);
        channels[slot] = Math.max(0, channel);
        return this;
    }

    /** 一共几段 */
    public int size() {
        return states.length;
    }

    /** 第 slot 段的状态（模型播的就是它） */
    public AnimationState get(int slot) {
        check(slot);
        return states[slot];
    }

    /** 第 slot 段的事件号 */
    public static byte eventId(int slot) {
        if (slot < 0 || slot >= MAX_SLOTS) {
            throw new IllegalArgumentException("动画段号超出范围：" + slot);
        }
        return (byte) (EVENT_BASE + slot);
    }

    /** 事件号对应第几段；不是动画事件返回 -1 */
    public static int slotOf(byte eventId) {
        int slot = eventId - EVENT_BASE;
        return slot >= 0 && slot < MAX_SLOTS ? slot : -1;
    }

    /** 服务端：让看得见它的客户端起第 slot 段（客户端上调用时直接本地起） */
    public void play(Entity entity, int slot) {
        check(slot);
        if (entity.level().isClientSide) {
            start(slot, entity.tickCount);
        } else {
            entity.level().broadcastEntityEvent(entity, eventId(slot));
        }
    }

    /**
     * 客户端：实体的 {@code handleEntityEvent} 里先交给这里。是本对照表里的动画事件就起那一段、返回 true（调用方不再交给父类）；
     * 别的事件号、超出本只怪段数的返回 false。
     */
    public boolean handle(byte eventId, int tickCount) {
        int slot = slotOf(eventId);
        if (slot < 0 || slot >= states.length) {
            return false;
        }
        start(slot, tickCount);
        return true;
    }

    /** 起第 slot 段（同通道的别的段停掉） */
    public void start(int slot, int tickCount) {
        check(slot);
        int channel = channels[slot];
        if (channel > 0) {
            for (int i = 0; i < states.length; i++) {
                if (i != slot && channels[i] == channel) {
                    states[i].stop();
                }
            }
        }
        states[slot].start(tickCount);
    }

    /** 持续动画：条件成立时开着（没开就起），不成立就停（原版 {@code AnimationState.animateWhen}） */
    public void animateWhen(int slot, boolean condition, int tickCount) {
        get(slot).animateWhen(condition, tickCount);
    }

    public void stop(int slot) {
        get(slot).stop();
    }

    public void stopAll() {
        Arrays.stream(states).forEach(AnimationState::stop);
    }

    /** 展示厅：定格（模型按 0 速度播） */
    public void freeze(boolean value) {
        frozen = value;
    }

    /** 模型播放速度：定格时 0，平时 1 */
    public float speed() {
        return frozen ? 0F : 1F;
    }

    private void check(int slot) {
        if (slot < 0 || slot >= states.length) {
            throw new IllegalArgumentException("动画段号超出范围：" + slot + "（共 " + states.length + " 段）");
        }
    }
}
