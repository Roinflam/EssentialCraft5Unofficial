package com.flameforge.essentialcraft5.effect;

import com.flameforge.essentialcraft5.registry.ECWind;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 轻盈（原作 PotionUnnormalLightness，喝风之药水得到）：
 * <ul>
 *   <li>移速 +40% × 等级（原作 registerPotionAttributeModifier(MOVEMENT_SPEED, …, 0.4, 2)：运算 2 = MULTIPLY_TOTAL，原版按等级自动乘）。</li>
 *   <li>跳得更高：等同同级的跳跃提升（每级起跳竖直速度 +0.1、摔落距离少算 1 格）。</li>
 *   <li>风之眷顾后的风之好感事件几率 × (等级 + 1)（见 {@link com.flameforge.essentialcraft5.player.WindRelations}）。</li>
 * </ul>
 *
 * <h2>和原作的区别（policy §4.2）</h2>
 * <ul>
 *   <li>原作 performEffect 每 tick 给自己补一个同时长同等级的跳跃提升（每 tick 一个效果包，还多挂一个跳跃提升图标）；
 *       现在不加效果，起跳时在 LivingJumpEvent 里直接加竖直速度（{@link #applyJump}），摔落时在 LivingFallEvent 里少算距离（{@link #reduceFall}）。
 *       客户端也要加：玩家的移动由客户端算，效果本来就会同步给本人。</li>
 *   <li>叠喝：原作每喝一瓶时长 +2 分钟、等级 = 剩余时长 ÷ 8 分钟（没有上限，喝上几十瓶就是几十级的移速和跳跃）；
 *       现在等级封顶 III、时长封顶 60 分钟（配置 general.wind.lightness_max_level / lightness_max_minutes），见 {@link #drink}。</li>
 *   <li>原作标成负面效果（isBad=true），这里是正面（药水提示不显示红字，别的模组的「清除负面效果」不会清掉它）。</li>
 * </ul>
 */
public class ParanormalLightnessEffect extends MobEffect {

    /** 原作的修饰符 UUID */
    public static final String SPEED_MODIFIER_UUID = "91AEAA56-376B-4498-935B-2F7F68070636";
    /** 原作每级的移速加成（MULTIPLY_TOTAL） */
    public static final double SPEED_PER_LEVEL = 0.4D;
    /** 原版跳跃提升每级的起跳速度加成（LivingEntity#getJumpBoostPower） */
    public static final double JUMP_PER_LEVEL = 0.1D;
    /** 每喝一瓶加的时长（原作 2*60*20） */
    public static final int DRINK_TICKS = 2 * 60 * 20;
    /** 每多少剩余时长升一级（原作 8*60*20） */
    public static final int TICKS_PER_LEVEL = 8 * 60 * 20;

    public ParanormalLightnessEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFFFFCC);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, SPEED_MODIFIER_UUID, SPEED_PER_LEVEL, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    /** 喝一瓶风之药水（原作 ECUtils.calculateAndAddPE(player, paranormalLightness, 8 分钟, 2 分钟)，加了上限） */
    public static void drink(LivingEntity entity) {
        ParanormalLightnessEffect effect = ECWind.PARANORMAL_LIGHTNESS.get();
        MobEffectInstance next = next(entity.getEffect(effect), ECWind.Config.lightnessMaxLevel(), ECWind.Config.lightnessMaxMinutes() * 60 * 20);
        if (next == null) {
            return;
        }
        // 原作先删后加：等级按剩余时长重算，可能比现在低（原版 addEffect 不会把高等级换成低等级）
        entity.removeEffect(effect);
        entity.addEffect(next);
    }

    /**
     * 叠喝的规则（纯函数，GameTest 直接测）：没有效果时 I 级 2 分钟；有效果时等级 = min(剩余时长 ÷ 8 分钟, 上限 − 1)，时长 = min(剩余 + 2 分钟, 上限)。
     * 无限时长的（指令给的）不动，返回 null。
     *
     * @param maxLevel 等级上限（III 就是 3）
     * @param maxTicks 时长上限
     */
    @Nullable
    public static MobEffectInstance next(@Nullable MobEffectInstance current, int maxLevel, int maxTicks) {
        ParanormalLightnessEffect effect = ECWind.PARANORMAL_LIGHTNESS.get();
        int cap = Math.max(DRINK_TICKS, maxTicks);
        int maxAmplifier = Math.max(0, maxLevel - 1);
        if (current == null) {
            return new MobEffectInstance(effect, Math.min(DRINK_TICKS, cap), 0, true, true);
        }
        if (current.isInfiniteDuration()) {
            return null;
        }
        int duration = Math.max(0, current.getDuration());
        int amplifier = Math.min(duration / TICKS_PER_LEVEL, maxAmplifier);
        int newDuration = (int) Math.min((long) duration + DRINK_TICKS, cap);
        return new MobEffectInstance(effect, newDuration, amplifier, true, true);
    }

    /** 起跳：同级跳跃提升的竖直速度（原版 getJumpBoostPower = 0.1 × (等级 + 1)） */
    public static void applyJump(LivingEntity entity) {
        MobEffectInstance instance = entity.getEffect(ECWind.PARANORMAL_LIGHTNESS.get());
        if (instance != null) {
            Vec3 motion = entity.getDeltaMovement();
            entity.setDeltaMovement(motion.x, motion.y + JUMP_PER_LEVEL * (instance.getAmplifier() + 1), motion.z);
        }
    }

    /** 摔落：少算 (等级 + 1) 格（原版跳跃提升在 calculateFallDamage 里的做法） */
    public static float reduceFall(LivingEntity entity, float distance) {
        MobEffectInstance instance = entity.getEffect(ECWind.PARANORMAL_LIGHTNESS.get());
        return instance == null ? distance : Math.max(0F, distance - (instance.getAmplifier() + 1));
    }
}
