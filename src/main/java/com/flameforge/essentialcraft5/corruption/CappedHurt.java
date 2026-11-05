package com.flameforge.essentialcraft5.corruption;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.function.ToDoubleFunction;

/**
 * 「这一下最多扣多少血」：腐化 #13 温和版本人那一下最多掉打之前生命的一半，「MRU 腐化」状态最多打到剩 1 点。
 * <p>
 * 只在 {@code hurt} 之前按当时的生命算好伤害量不够：别的模组在 {@code LivingHurtEvent} / {@code LivingDamageEvent} 里加成
 * （难度、诅咒、按环境伤害加倍的战斗调整模组），或者混合端插件改了 Bukkit 的伤害事件，这一下照样会超过上限、把人打死。
 * 所以在 {@code hurt} 前记下这一下的伤害源实例和上限，{@code LivingDamageEvent}（LOWEST：别的模组的加成、减免都算完以后，
 * 也是混合端插件放行以后才发的那一个，扣血用的就是它的结果）里认出同一个伤害源实例，把扣血量夹到上限。
 * 上限在事件里现算（事件里的量已经扣过吸收，直接从生命里扣）。
 * <p>
 * 按调用压栈：{@code hurt} 里又触发了别的受上限的 {@code hurt}（例如别的监听器连带打了一下）时互不覆盖，返回前弹出自己那一层。
 * 被插件或 {@code LivingAttackEvent} 拦下的伤害不发 {@code LivingDamageEvent}，这里也就什么都不做。只在服务端线程用。
 * <p>
 * LOWEST 不保证是最后一个：同一档里谁先登记看模组加载的先后（Forge 并行构造模组），整合包里有的伤害结算库
 * 在 {@code LivingDamageEvent} LOWEST 里也会改伤害量。两个上限都打不死人（剩 1 点、最多掉一半），所以再加一道兜底：
 * 同一个目标、同一个伤害源实例在受上限的这一下里死了（{@link LivingDeathEvent}，最高优先级），取消死亡、生命设回 1 点。
 * 死亡是在 {@code hurt} 里面发的，这时这一层还在栈上。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class CappedHurt {

    private record Frame(LivingEntity target, DamageSource source, ToDoubleFunction<LivingEntity> cap) {
    }

    private static final ArrayDeque<Frame> FRAMES = new ArrayDeque<>();

    private CappedHurt() {
    }

    /**
     * 用 source 打 target 一下 amount，这一下真正扣的血不超过 cap（在 {@code LivingDamageEvent} 里对受伤者现算，小于 0 按 0）。
     *
     * @return {@code hurt} 的返回值（被拦下、无敌帧里时是 false）
     */
    public static boolean hurt(LivingEntity target, DamageSource source, float amount, ToDoubleFunction<LivingEntity> cap) {
        Frame frame = new Frame(target, source, cap);
        FRAMES.push(frame);
        try {
            return target.hurt(source, amount);
        } finally {
            FRAMES.removeFirstOccurrence(frame);
        }
    }

    /** 最多掉打之前生命的一半（腐化 #13 温和版本人那一下） */
    public static boolean hurtAtMostHalf(LivingEntity target, DamageSource source, float amount) {
        float limit = target.getHealth() * 0.5F;
        return hurt(target, source, amount, entity -> limit);
    }

    /** 最多打到只剩 1 点生命（像原版中毒；「MRU 腐化」状态默认这样） */
    public static boolean hurtLeavingOne(LivingEntity target, DamageSource source, float amount) {
        return hurt(target, source, amount, entity -> leavingOne(entity.getHealth()));
    }

    /**
     * 打到只剩 1 点要扣多少：生命 − 1。生命 ≥ 2^24 时 float 减 1 会舍入回生命本身（整合包的 AttributeFix 让最大生命能到 int 上限），
     * 那样一下就打到 0；这时改取比生命小的最大 float，扣完至少还剩一个最小单位
     */
    public static float leavingOne(float health) {
        float cap = health - 1F;
        return cap < health ? cap : Math.nextDown(health);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCappedHurtDamage(LivingDamageEvent event) {
        if (FRAMES.isEmpty()) {
            return;
        }
        // 从最里层（最近压进来的）往外找
        for (Iterator<Frame> it = FRAMES.iterator(); it.hasNext(); ) {
            Frame frame = it.next();
            if (frame.target == event.getEntity() && frame.source == event.getSource()) {
                float limit = (float) Math.max(0D, frame.cap.applyAsDouble(event.getEntity()));
                if (event.getAmount() > limit) {
                    event.setAmount(limit);
                }
                return;
            }
        }
    }

    /** 兜底（见类注释）：受上限的这一下还是把人打死了，就不让他死 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCappedHurtDeath(LivingDeathEvent event) {
        if (FRAMES.isEmpty()) {
            return;
        }
        LivingEntity entity = event.getEntity();
        for (Frame frame : FRAMES) {
            if (frame.target == entity && frame.source == event.getSource()) {
                event.setCanceled(true);
                entity.setHealth(1F);
                return;
            }
        }
    }
}
