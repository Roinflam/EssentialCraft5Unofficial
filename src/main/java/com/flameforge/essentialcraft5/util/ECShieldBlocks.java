package com.flameforge.essentialcraft5.util;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * 本刻被盾牌整下挡掉的伤害（第十批饰品）：给「这一下打实了才算」的效果（ζ、ι、σ、ξ、MRU 盔甲在混合端上的延后扣电、宅男套装反伤）排除举盾挡下的那一下。
 * <p>
 * <b>为什么要单独记</b>：两种环境里举盾结算的时机不同。纯 Forge 在 {@code LivingEntity.hurt} 里、{@code LivingHurtEvent} 之前就扣掉盾牌挡住的部分，
 * 挡满时 {@code LivingHurtEvent} 看到的是 0，{@code Player.actuallyHurt} 直接返回、不发 {@code LivingDamageEvent}，上面那些效果都不会触发。
 * Mohist 系混合端把举盾挪到 {@code damageEntity0} 里、{@code LivingHurtEvent} <b>之后</b>才算（盾牌只是一项 BLOCKING 修正），
 * 挡满以后照样发 {@code LivingDamageEvent(0)}，「伤害事件来过」就不能再当成「打实了」。
 * 到 {@code LivingDamageEvent} 那一刻也不能用 {@code isDamageSourceBlocked} 现查：混合端在发它之前已经调过 {@code blockUsingShield}，
 * 攻击者拿斧子时盾牌当场被打掉、{@code isBlocking()} 已经是 false。所以认 Forge 标准的 {@link ShieldBlockEvent}：两种环境里它都排在
 * {@code blockUsingShield} 之前。
 * <p>
 * <b>记什么</b>：{@link EventPriority#LOWEST}、不收已取消的（取消了盾牌就不挡）、只在服务端；挡下的量不少于这一下（{@code getBlockedDamage() ≥ getOriginalBlockedDamage()}，
 * 别的模组改成只挡一部分的不算整下挡掉）。按「实体 + 伤害源对象 + 这一刻」记，本刻末清空，最多 {@value #MAX} 条（满了丢最早的）。只在服务端线程读写。
 * <p>
 * 用法：在 {@code LivingDamageEvent} 里调 {@link #fullyShielded}。纯 Forge 上挡满时根本不发 {@code LivingDamageEvent}，只挡一部分时数值 &gt; 0，结果不变；
 * 混合端上挡满时数值是 0、又有这一刻的记录，正好排除。不按环境分支。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class ECShieldBlocks {

    /** 一刻最多记这么多条（正常只有 0~2 条） */
    public static final int MAX = 256;

    private record Blocked(LivingEntity entity, DamageSource source, long tick) {
    }

    private static final List<Blocked> BLOCKED = new ArrayList<>();

    private ECShieldBlocks() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onECShieldBlockRecord(ShieldBlockEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || event.getBlockedDamage() < event.getOriginalBlockedDamage()) {
            return;
        }
        if (BLOCKED.size() >= MAX) {
            BLOCKED.remove(0);
        }
        BLOCKED.add(new Blocked(entity, event.getDamageSource(), entity.level().getGameTime()));
    }

    /**
     * 这一下是不是被盾牌整下挡掉的：amount（{@code LivingDamageEvent} 里的数）≤ 0，而且这一刻有同一个实体、同一个伤害源对象的整下格挡记录。
     */
    public static boolean fullyShielded(LivingEntity target, DamageSource source, float amount) {
        if (amount > 0F || BLOCKED.isEmpty()) {
            return false;
        }
        long now = target.level().getGameTime();
        for (Blocked blocked : BLOCKED) {
            if (blocked.entity() == target && blocked.source() == source && blocked.tick() == now) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onECShieldBlocksServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !BLOCKED.isEmpty()) {
            BLOCKED.clear();
        }
    }

    @SubscribeEvent
    public static void onECShieldBlocksStopped(ServerStoppedEvent event) {
        BLOCKED.clear();
    }
}
