package com.flameforge.essentialcraft5.entity;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.item.HoloDecoyProjectorItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 全息诱饵的 Forge 总线监听（{@link HoloDecoyEntity}；类名带模块前缀，免得和同包别的 Events 撞名静默失效）：
 * <ul>
 *   <li>{@code LivingChangeTargetEvent}：诱饵在场期间，附近的敌对生物想（重新）去打主人或同队玩家时改成打诱饵。
 *       排在 LOW：别的监听已经把目标换成不是玩家的（比如 ψ 指环换成了别的怪）就不管。没有诱饵在场时第一行返回，这个事件每刻都发很多次。</li>
 *   <li>主人下线、换维度、死亡：当场移除他的诱饵（诱饵自己每刻也会查，这里只是不等到下一刻）。</li>
 *   <li>投影仪的冷却（{@link HoloDecoyProjectorItem#READY_KEY}）：登录、从末地回来时补回物品冷却；死亡重生时清掉。</li>
 *   <li>关服：清空诱饵登记表和首领窗口。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class HoloDecoyEvents {

    private HoloDecoyEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void decoyChangeTarget(LivingChangeTargetEvent event) {
        if (HoloDecoyEntity.noneActive()) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (!(event.getNewTarget() instanceof Player target) || !(entity instanceof Mob mob) || entity.level().isClientSide) {
            return;
        }
        HoloDecoyEntity decoy = HoloDecoyEntity.lureFor(mob, target);
        if (decoy != null) {
            event.setNewTarget(decoy);
        }
    }

    @SubscribeEvent
    public static void decoyOwnerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        dismiss(event.getEntity());
    }

    @SubscribeEvent
    public static void decoyOwnerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        dismiss(event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void decoyOwnerDied(LivingDeathEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof Player player) {
            dismiss(player);
        }
    }

    @SubscribeEvent
    public static void decoyCooldownLogin(PlayerEvent.PlayerLoggedInEvent event) {
        HoloDecoyProjectorItem.restoreCooldown(event.getEntity());
    }

    /** 从末地回来（换了新的玩家对象，物品冷却没了）补回冷却；死亡重生清掉，同 θ 腰带 */
    @SubscribeEvent
    public static void decoyCooldownRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        if (event.isEndConquered()) {
            HoloDecoyProjectorItem.restoreCooldown(player);
        } else {
            HoloDecoyProjectorItem.clearReady(player);
        }
    }

    @SubscribeEvent
    public static void decoyServerStopped(ServerStoppedEvent event) {
        HoloDecoyEntity.clearAll();
    }

    private static void dismiss(Player player) {
        if (!player.level().isClientSide && !HoloDecoyEntity.noneActive()) {
            HoloDecoyEntity.dismiss(player);
        }
    }
}
