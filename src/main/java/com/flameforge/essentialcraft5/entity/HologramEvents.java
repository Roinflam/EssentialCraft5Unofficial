package com.flameforge.essentialcraft5.entity;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.entity.boss.BossRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 全息投影的事件规则。
 *
 * <h2>不经投影台出现时（batch8.md §4.1 末、policy §4.6「刷怪蛋」）</h2>
 * <ul>
 *   <li>来自刷怪笼的一律取消（刷怪蛋点刷怪笼本来不改类型，这里再挡住用命令、结构 NBT 改出来的刷怪笼）；</li>
 *   <li>来自刷怪蛋、发射器的照样查维度（海纳尔，或者开了 {@code other_dimensions}）和「64 格内已有一只」，不满足就取消。</li>
 * </ul>
 * 不带 NBT 的 {@code /summon} 不拦（管理员的指令）。没被取消的，最大生命按配置 {@code general.hologram.max_health} 设好（实体属性登记时用的是默认 400）。
 * 监听在 LOW：别的模组先决定要不要取消，这里只在还没被取消时再判一次。也收已取消的事件：{@code FinalizeSpawn} 被取消只表示跳过
 * {@code finalizeSpawn}（例如别的模组不想让怪带默认装备），实体照样生成；这里判的是 {@code isSpawnCancelled()}，不是 {@code isCanceled()}。
 *
 * <h2>单次封顶的最后一道</h2>
 * {@link HologramEntity#hurt} 只夹得住传给父类的输入值；别的模组在 {@code LivingHurtEvent} / {@code LivingDamageEvent} 里再乘一次（暴击、元素加成），
 * 最终掉血就会超过 {@code max_damage_per_hit}。这里在 {@code LivingDamageEvent} LOWEST 再夹一次：它是扣血前最后一个事件，混合端的 Bukkit 伤害事件
 * 夹在 Hurt 和 Damage 之间，两端都管用。同在 LOWEST、比本模组登记得晚的监听器和直接 {@code setHealth} 的模组仍绕得过去，事件层只能做到这里。
 * 带 {@code bypasses_invulnerability} 的（{@code /kill}、掉出世界）不夹。
 *
 * <h2>死了就退出战斗</h2>
 * 玩家死亡时从所有全息投影的名单里移出（{@link HologramEntity#forgetFighter}）。开了立即重生时，从死到重生只隔 1~3 刻，
 * 名单每 10 刻一次的 isAlive 检查看到的往往已经是重生出来的新对象。不收已取消的死亡事件：被别的模组救回来的不算死。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class HologramEvents {

    private HologramEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOW, receiveCanceled = true)
    public static void onHologramFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (!(event.getEntity() instanceof HologramEntity hologram) || event.isSpawnCancelled()) {
            return;
        }
        MobSpawnType type = event.getSpawnType();
        if (type == MobSpawnType.SPAWNER) {
            event.setSpawnCancelled(true);
            return;
        }
        if (type == MobSpawnType.SPAWN_EGG || type == MobSpawnType.DISPENSER) {
            ServerLevel level = event.getLevel().getLevel();
            // 审判庭哨兵按它自己的维度名单（HologramEntity.allowedIn 被它覆盖）
            if (!hologram.allowedIn(level) || HologramEntity.anyNearby(level, hologram.position())) {
                event.setSpawnCancelled(true);
                return;
            }
        }
        hologram.applyConfiguredHealth();
    }

    /** 单次封顶的最后一道：别的模组在 LivingHurtEvent / LivingDamageEvent 里再乘一次，最终掉血也不超过 max_damage_per_hit */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHologramDamageCap(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof HologramEntity hologram) || BossRules.unblockable(event.getEntity(), event.getSource())) {
            return;
        }
        float cap = hologram.damageCap();
        if (cap > 0F && event.getAmount() > cap) {
            event.setAmount(cap);
        }
    }

    /** 玩家死了就从名单里移出（不收已取消的：被救回来的不算） */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHologramFighterDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            HologramEntity.forgetFighter(player.getUUID());
        }
    }
}
