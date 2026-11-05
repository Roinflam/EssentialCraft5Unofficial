package com.flameforge.essentialcraft5.util;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.reflect.Field;

/**
 * 目标的受击无敌窗口（{@code invulnerableTime} + {@code lastHurt}）快照：「本刻末再补一下」这类额外伤害，打之前记下、打完原样放回，
 * 目标的状态就和只挨了原来那一下一样（裁决之刃的裁断、ι 指环，batch13.md §4.4）。
 * <p>
 * 以前补完把 {@code invulnerableTime} 清 0。原版 {@code Player.attack} 在 hurt 之前就把攻击蓄力归零，没蓄满的一下（0.2 倍伤害）
 * 本来会被 10 刻的无敌窗口挡掉（不大于 {@code lastHurt} 时 hurt 直接返回 false）；清 0 以后下一刻就能整下打实，连点每刻都打得进去。
 * 放回以后照原版挡；补的那下被插件 / 领地拦下时也一样放回，原来那一下的无敌帧不会白丢。
 * <p>
 * {@code lastHurt} 是 protected 字段，按 SRG 名反射（同 {@code AccumulatorEvents}）；找不到时只放回 {@code invulnerableTime}，日志报一次。
 */
public record ECHurtWindow(int invulnerableTime, float lastHurt, boolean hasLastHurt) {

    /** {@code LivingEntity.lastHurt} 的 SRG 名（build/createSrgToMcp/output.srg：f_20898_ → lastHurt） */
    public static final String LAST_HURT_SRG = "f_20898_";

    private static final Logger LOGGER = LogUtils.getLogger();
    @Nullable
    private static Field lastHurtField;
    private static boolean lastHurtFailed;

    /** 记下 entity 现在的无敌窗口 */
    public static ECHurtWindow of(LivingEntity entity) {
        Field field = lastHurtField();
        if (field != null) {
            try {
                return new ECHurtWindow(entity.invulnerableTime, field.getFloat(entity), true);
            } catch (IllegalAccessException | IllegalArgumentException ignored) {
                // 落到下面：只记无敌帧
            }
        }
        return new ECHurtWindow(entity.invulnerableTime, 0F, false);
    }

    /** 原样放回（补的那下打没打成都放） */
    public void restore(LivingEntity entity) {
        entity.invulnerableTime = invulnerableTime;
        Field field = hasLastHurt ? lastHurtField() : null;
        if (field != null) {
            try {
                field.setFloat(entity, lastHurt);
            } catch (IllegalAccessException | IllegalArgumentException ignored) {
                // 放不回 lastHurt 只是窗口里的差额按补的那下算，不影响别的
            }
        }
    }

    @Nullable
    private static Field lastHurtField() {
        if (lastHurtField == null && !lastHurtFailed) {
            try {
                lastHurtField = ObfuscationReflectionHelper.findField(LivingEntity.class, LAST_HURT_SRG);
            } catch (RuntimeException e) {
                lastHurtFailed = true;
                LOGGER.error("[EC5] 找不到 LivingEntity.lastHurt（{}），补伤害以后只放回无敌帧", LAST_HURT_SRG, e);
            }
        }
        return lastHurtField;
    }
}
