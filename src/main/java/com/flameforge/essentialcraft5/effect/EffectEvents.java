package com.flameforge.essentialcraft5.effect;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.registry.ECEffects;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

/**
 * 第十批 10b 四个机制效果的事件处理（batch10.md §4.2.1）。效果身上有就生效，不管是谁、用什么加上去的
 * （透镜、二分枪、削减 / 相位锚模块、指令、药水箭、别的模组），所以全写在这里、按效果查，不按来源查。
 * <ul>
 *   <li><b>腐蚀</b>（{@link ECEffects#CORROSION}）：{@code LivingHealEvent} 取消（HIGH：后面的监听器不收已取消的，别的模组的「治疗时……」也不触发）。
 *       只拦走 {@code LivingEntity#heal} 的回血（自然回复、生命恢复、瞬间治疗，以及大多数模组的吸血和回血）；直接 {@code setHealth} 的拦不住。</li>
 *   <li><b>二分</b>（{@link ECEffects#BISECTED}）：
 *       <ul>
 *         <li>造成的伤害减半：{@code LivingHurtEvent} HIGH，伤害源的「真正来源」（{@code getEntity()}，弹射物算射手；没有时看直接来源）带着二分就 ×{@value #BISECTED_FACTOR}。
 *             排在 HIGH 是为了先于本模组 NORMAL 的 η 翻倍 / ι 拆分和 LOW 的 MRU 盔甲挡伤害：相当于攻击者打出来的伤害从源头减半，后面的加成、减伤照常按减半后的数算。</li>
 *         <li>受到的治疗减半：{@code LivingHealEvent} LOW，排在别的模组加减治疗量之后，整个治疗量 ×{@value #BISECTED_FACTOR}。腐蚀同时在身上时腐蚀先取消，这里收不到。</li>
 *       </ul>
 *       只改数值、不另造伤害，伤害事件被拦下（插件、别的模组）时这一下本来就没了，没有别的后果。</li>
 *   <li><b>锚定</b>（{@link ECEffects#ANCHORED}）：Forge 的三种「瞬移」传送事件 HIGH 取消——
 *       {@code EntityTeleportEvent.EnderEntity}（末影人随机瞬移、躲弹射物和水；潜影贝换位置）、
 *       {@code EntityTeleportEvent.EnderPearl}（珍珠落地，看扔珍珠的玩家；珍珠照常用掉，和原版被取消时一样）、
 *       {@code EntityTeleportEvent.ChorusFruit}（紫颂果吃掉了、不传送）。指令传送、传送门、本模组的魔力传送器（{@code ECTeleport} 发的是基类事件）、
 *       别的模组自己的传送都不管：锚定拦的是「瞬移」，不是把人困住。</li>
 * </ul>
 * 破甲没有事件处理，修饰符由原版的效果机制挂上、摘掉（{@link ArmorShredEffect}）。
 * <p>
 * 这些都不分纯 Forge / 混合端：只改事件里的数值或取消事件，混合端插件在后面拦下伤害时同样没有别的后果。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class EffectEvents {

    /** 二分：造成的伤害、受到的治疗乘的系数 */
    public static final float BISECTED_FACTOR = 0.5F;

    private EffectEvents() {
    }

    /** entity 身上有没有这个效果（效果还没注册时当没有） */
    public static boolean has(@Nullable LivingEntity entity, RegistryObject<? extends MobEffect> effect) {
        return entity != null && effect.isPresent() && entity.hasEffect(effect.get());
    }

    // ---------------------------------------------------------------- 腐蚀

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onCorrosionHeal(LivingHealEvent event) {
        if (has(event.getEntity(), ECEffects.CORROSION)) {
            event.setCanceled(true);
        }
    }

    // ---------------------------------------------------------------- 二分

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBisectedHurt(LivingHurtEvent event) {
        LivingEntity dealer = dealer(event.getSource());
        if (has(dealer, ECEffects.BISECTED)) {
            event.setAmount(event.getAmount() * BISECTED_FACTOR);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onBisectedHeal(LivingHealEvent event) {
        if (has(event.getEntity(), ECEffects.BISECTED)) {
            event.setAmount(event.getAmount() * BISECTED_FACTOR);
        }
    }

    /** 这一下伤害算谁打的：真正来源（弹射物的射手、召唤物的主人由各自的伤害源决定）；没有真正来源时取直接来源；都不是活体时 null */
    @Nullable
    public static LivingEntity dealer(DamageSource source) {
        if (source.getEntity() instanceof LivingEntity living) {
            return living;
        }
        return source.getEntity() == null && source.getDirectEntity() instanceof LivingEntity direct ? direct : null;
    }

    // ---------------------------------------------------------------- 锚定

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAnchoredEnderTeleport(EntityTeleportEvent.EnderEntity event) {
        if (has(event.getEntityLiving(), ECEffects.ANCHORED)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAnchoredPearl(EntityTeleportEvent.EnderPearl event) {
        if (has(event.getPlayer(), ECEffects.ANCHORED)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAnchoredChorus(EntityTeleportEvent.ChorusFruit event) {
        if (has(event.getEntityLiving(), ECEffects.ANCHORED)) {
            event.setCanceled(true);
        }
    }
}
