package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.config.ECConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 所有特斯拉线圈共用的目标表（只在服务端线程用；表不存盘、关服清空，只有「待保护」另在玩家存档里记一个标记）：
 * <ul>
 *   <li><b>同一目标 20 tick 只挨一台</b>（{@link #TARGET_COOLDOWN}，policy §1.2「同一个实体不被多台重复处理」）：多台线圈 = 覆盖更广、目标更多，
 *       不叠伤害。按实体记（WeakHashMap，实体没了自动丢）。</li>
 *   <li><b>重生保护</b>（D2 ⑦）：被线圈打死的玩家，重生（或死后下线、再上线时已经活着）起 {@code respawn_grace_ticks}（默认 600 = 30 秒）
 *       内所有线圈都不打他。重生无敌只有 3 秒，C 档 1~3 秒就能打死人，重生点在射程里的人会被循环电死、每次全身物品掉在射程里。
 *       死亡时先记「待保护」，在死亡界面下线再上线仍是死的，等真的点了重生才开始计时。「待保护」除了记在内存表里，还在玩家存档里记一个标记
 *       （{@link #TAG_GRACE_PENDING}，在 {@link Player#PERSISTED_NBT_TAG} 子标签下）：在死亡界面下线的玩家连同标记一起存盘，
 *       服务器中途重启（内存表清空）后上线点重生照样有保护；重生时 Forge 的 {@code ServerPlayer.restoreFrom} 把这个子标签复制给新的玩家对象。
 *       开始计时后标记删掉。截止时间只在内存里：保护期（30 秒）当中服务器重启，剩下的保护就没了。</li>
 *   <li><b>警告重新计时</b>：玩家死亡、重生、登录、换维度时记一个时间点，各线圈的「首次进射程先警告」表里早于它的记录作废（重新警告）。</li>
 * </ul>
 * <b>时钟</b>：表里的时间一律是主世界的游戏时间（{@link #now}）。原版和纯 Forge 的各维度共用这个时间；混合端上插件建的世界有自己的时间，
 * 所以线圈先把所在世界的时间换算成主世界时间再来查（{@code MRUCoilBlockEntity.globalTime}），否则那些世界里警告永远重来、或重生保护提前失效。
 * 事件处理器名字带 tesla 前缀（同包撞名会静默失效，见 tools/check_event_handlers.py）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class MRUCoilTargets {

    /** 同一目标两次挨打（不论哪台线圈）至少隔几 tick */
    public static final int TARGET_COOLDOWN = 20;
    /** 玩家存档里「待保护」的标记（{@link Player#PERSISTED_NBT_TAG} 子标签下的布尔） */
    public static final String TAG_GRACE_PENDING = EssentialCraft5.MODID + ":coil_grace_pending";

    private static final Map<Entity, Long> LAST_STRUCK = new WeakHashMap<>();
    /** 被线圈打死、还没重生的玩家（缓存；玩家存档里另有 {@link #TAG_GRACE_PENDING}，重启后靠它） */
    private static final Set<UUID> PENDING_GRACE = new HashSet<>();
    /** 重生保护截止的游戏时间 */
    private static final Map<UUID, Long> GRACE_UNTIL = new HashMap<>();
    /** 警告重新计时的时间点 */
    private static final Map<UUID, Long> WARN_RESET = new HashMap<>();

    private MRUCoilTargets() {
    }

    // ------------------------------------------------------------------
    // 线圈调用（时间都是主世界时间，见类注释「时钟」）
    // ------------------------------------------------------------------

    /** 这个实体刚被（任一台）线圈打过，还在冷却里 */
    public static boolean recentlyStruck(Entity entity, long now) {
        Long last = LAST_STRUCK.get(entity);
        return last != null && now - last < TARGET_COOLDOWN && now >= last;
    }

    /** 记下一次命中（伤害生效后调） */
    public static void markStruck(Entity entity, long now) {
        LAST_STRUCK.put(entity, now);
    }

    /** 还在重生保护期里 */
    public static boolean inRespawnGrace(UUID player, long now) {
        Long until = GRACE_UNTIL.get(player);
        if (until == null) {
            return false;
        }
        if (now >= until) {
            GRACE_UNTIL.remove(player);
            return false;
        }
        return true;
    }

    /** 这名玩家的警告最近一次重新计时的时间点；没有记录返回 Long.MIN_VALUE */
    public static long warningReset(UUID player) {
        Long at = WARN_RESET.get(player);
        return at == null ? Long.MIN_VALUE : at;
    }

    // ------------------------------------------------------------------
    // 状态变化（事件和测试都走这里）
    // ------------------------------------------------------------------

    /** 玩家死了：警告重新计时；死于线圈的记成「待保护」 */
    public static void onPlayerDied(UUID player, boolean byCoil, long now) {
        WARN_RESET.put(player, now);
        if (byCoil) {
            PENDING_GRACE.add(player);
            GRACE_UNTIL.remove(player);
        }
    }

    /** 玩家重生了（alive = 真的活着）：警告重新计时；「待保护」（内存表里有他）的开始计时 */
    public static void onPlayerBack(UUID player, boolean alive, long now) {
        onPlayerBack(player, alive, false, now);
    }

    /** 同上；persistedPending = 玩家存档里带着「待保护」标记（服务器重启过，内存表里已经没有他） */
    private static void onPlayerBack(UUID player, boolean alive, boolean persistedPending, long now) {
        WARN_RESET.put(player, now);
        if (alive) {
            boolean pending = PENDING_GRACE.remove(player);
            if (pending || persistedPending) {
                int ticks = ECConfig.get(MRUCoilBlockEntity.Config.respawnGraceTicks);
                if (ticks > 0) {
                    GRACE_UNTIL.put(player, now + ticks);
                }
            }
        }
        GRACE_UNTIL.values().removeIf(until -> until <= now);
    }

    /** 事件用：活着的话连同玩家存档里的「待保护」标记一起看、看完删掉标记；还在死亡界面的不动标记（等他点重生） */
    private static void onPlayerBack(ServerPlayer player, long now) {
        boolean alive = player.isAlive();
        onPlayerBack(player.getUUID(), alive, alive && takePersistedPending(player), now);
    }

    /** 玩家存档里有没有「待保护」标记（测试用） */
    public static boolean hasPersistedPending(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getBoolean(TAG_GRACE_PENDING);
    }

    private static void markPersistedPending(Player player) {
        CompoundTag data = player.getPersistentData();
        // getCompound 在没有这个子标签时返回一个新的空标签，没挂在 data 上，改完要 put 回去
        CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.putBoolean(TAG_GRACE_PENDING, true);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    /** 取走「待保护」标记：有就删掉并返回 true */
    private static boolean takePersistedPending(Player player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(Player.PERSISTED_NBT_TAG)) {
            return false;
        }
        CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.getBoolean(TAG_GRACE_PENDING)) {
            return false;
        }
        persisted.remove(TAG_GRACE_PENDING);
        return true;
    }

    /** 只给测试和换维度用：警告重新计时 */
    public static void resetWarning(UUID player, long now) {
        WARN_RESET.put(player, now);
    }

    /** 清空全部（关服） */
    public static void clear() {
        LAST_STRUCK.clear();
        PENDING_GRACE.clear();
        GRACE_UNTIL.clear();
        WARN_RESET.clear();
    }

    /** 只给 GameTest 用：模拟服务器重启后内存表清空，只清这一名玩家（全清会冲掉并行的别的测试） */
    public static void forgetForTest(UUID player) {
        PENDING_GRACE.remove(player);
        GRACE_UNTIL.remove(player);
        WARN_RESET.remove(player);
    }

    /** 这几张表用的时钟：主世界的游戏时间（见类注释「时钟」） */
    public static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    // ------------------------------------------------------------------
    // 事件
    // ------------------------------------------------------------------

    /** LOWEST、不收已取消的：被别的模组救活（取消了死亡）的不算。死于线圈的另在玩家存档里记「待保护」标记 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void teslaOnLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            boolean byCoil = event.getSource() instanceof MRUCoilBlockEntity.CoilDischargeSource;
            if (byCoil) {
                markPersistedPending(player);
            }
            onPlayerDied(player.getUUID(), byCoil, now(player.server));
        }
    }

    /** 重生：新的玩家对象带着 restoreFrom 复制过来的「待保护」标记 */
    @SubscribeEvent
    public static void teslaOnRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            onPlayerBack(player, now(player.server));
        }
    }

    /**
     * 死后下线再上线：上线时已经活着（例如别的模组替他重生了）就从现在起算；还在死亡界面的等点了重生再算。
     * 中间服务器重启过的，内存表里已经没有他，靠存档里的标记认出来。
     */
    @SubscribeEvent
    public static void teslaOnLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            onPlayerBack(player, now(player.server));
        }
    }

    @SubscribeEvent
    public static void teslaOnChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            resetWarning(player.getUUID(), now(player.server));
        }
    }

    @SubscribeEvent
    public static void teslaOnServerStopped(ServerStoppedEvent event) {
        clear();
    }
}
