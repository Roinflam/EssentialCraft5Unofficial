package com.flameforge.essentialcraft5.entity;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.gun.RayHitGate;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.living.ZombieEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * 轨道打击、二分场结算伤害时共用的一步（第八批 8-Fa，batch8 §3.1）：{@link RayHitGate#observe} 打这一下，
 * 期间<b>压掉这个目标的击退</b>（{@code LivingKnockBackEvent} 最先处理、按「当前正在结算的目标」认）。
 * <p>
 * 原作两个实体的伤害源是 1.12 的匿名伤害源，本来没有击退；这里伤害源带了攻击者，原版 {@code LivingEntity.hurt} 会按攻击者的方向推一下，
 * 而且不管伤害有没有生效——纯 Forge 的领地模组在 {@code LivingHurtEvent} 拦下伤害后，{@code hurt} 照样往下走到击退。
 * 所以击退统一在这里拦掉，领地拦下的攻击不留「被推开」的副作用，放行的也照原作不推。
 * 只拦正在结算的那一个目标；结算中顺带发生的别的击退（例：荆棘反弹到攻击者身上）不受影响。
 * <p>
 * 另外压掉被拦下的这一下引来的<b>僵尸叫增援</b>（{@code ZombieEvent.SummonAidEvent} 置 DENY）：原版 {@code Zombie.hurt} 只要 {@code super.hurt}
 * 返回真、困难难度下就按增援几率在旁边刷新的僵尸，并让它们盯上攻击者。纯 Forge 的领地模组在 {@code LivingHurtEvent} 才拦时 hurt 照样返回真，
 * 被拦下的攻击也会叫来增援。所以二分场的 0 伤害探测一律拦（原作直接改生命，不经过 hurt，没有这回事）；轨道打击的真伤害只在没落下时拦
 * （{@link RayHitGate.Watch#landed()} 为假），真的落下了照原版叫增援。监听器在 LOWEST：结果字段后设的盖前设的，排在最后别的模组的 ALLOW 才盖不掉。
 * <br>蠹虫叫醒同伴不归这里管：{@code Silverfish.hurt} 在调 {@code LivingEntity.hurt} 之前就叫，早于一切伤害事件，模组拦不住。
 * <p>
 * 只在服务端主线程用（静态字段不加锁）；可嵌套（结算里别的模组又让另一个目标挨了一下也不串）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class HoloStrikeEvents {

    @Nullable
    private static LivingEntity resolving;
    /** 正在结算的这一下是不是 0 伤害的探测 */
    private static boolean resolvingProbe;
    /** 正在结算的这一下的观察记录（hurt 中途看落没落下） */
    @Nullable
    private static RayHitGate.Watch resolvingWatch;

    private HoloStrikeEvents() {
    }

    /** 打一下并观察结果（同 {@link RayHitGate#observe}），期间这个目标不会被击退，被拦下时不叫增援 */
    static RayHitGate.Watch strike(LivingEntity target, DamageSource source, float amount) {
        LivingEntity outer = resolving;
        boolean outerProbe = resolvingProbe;
        RayHitGate.Watch outerWatch = resolvingWatch;
        resolving = target;
        resolvingProbe = amount <= 0F;
        resolvingWatch = null;
        try {
            return RayHitGate.observe(target, source, amount, watch -> resolvingWatch = watch);
        } finally {
            resolving = outer;
            resolvingProbe = outerProbe;
            resolvingWatch = outerWatch;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHoloStrikeSummonAid(ZombieEvent.SummonAidEvent event) {
        if (resolving == null || event.getEntity() != resolving) {
            return;
        }
        // Zombie.hurt 在 super.hurt 返回以后才发这个事件，这时 LivingDamageEvent 已经发过（或者没发到），landed 是定论
        if (resolvingProbe || resolvingWatch == null || !resolvingWatch.landed()) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHoloStrikeKnockBack(LivingKnockBackEvent event) {
        if (resolving != null && event.getEntity() == resolving) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onHoloStrikeServerStopping(ServerStoppingEvent event) {
        resolving = null;
        resolvingProbe = false;
        resolvingWatch = null;
    }
}
