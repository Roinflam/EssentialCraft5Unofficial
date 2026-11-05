package com.flameforge.essentialcraft5.corruption;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.entity.MRUPresenceEntity;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 「这名玩家能不能动这团聚集体所在的那一格」的长缓存（{@code MRUPresenceEntity.mayAffect}，{@code general.corruption.respect_claims}）。
 * <p>
 * 为什么不直接用 {@link ECProtection#check} 自己的缓存（200 刻）：聚集体每 20 刻结算一轮，站在旁边的人每 10 秒就要被问一次，
 * 而每问一次都是以他的身份对聚集体那一格发一次破坏事件——混合端上领地插件会给访客发「没有权限」、挂机插件把它当成玩家在活动、
 * 挖掘排行榜给他加一格，日志插件记一条「破坏」。这里把结论（能动、不能动都记）按（玩家、聚集体）记 {@value #REMEMBER_TICKS} 刻，
 * 聚集体换了格子（被移动魔杖挪过）就不算数、重新问。
 * <p>
 * 只在内存里：玩家下线删他的条目，开服 / 关服整个清空（服务器刻从 0 重新数）。只在服务端线程上用。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class PresenceClaimMemory {

    /** 一次结论记多久（刻）：5 分钟 */
    public static final int REMEMBER_TICKS = 6000;
    /** 超过这么多条就先删过期的，还多就整个清掉（每名玩家身边通常只有几团） */
    private static final int LIMIT = 16384;
    /** 多久扫一遍、删掉过期的（刻） */
    private static final int SWEEP_INTERVAL = 1200;

    private record Key(UUID player, UUID presence) {
    }

    /** 结论：聚集体当时所在的格子、能不能动、记到哪一刻（服务器刻） */
    private record Entry(long cell, boolean allowed, int until) {
    }

    private static final Map<Key, Entry> MEMORY = new HashMap<>();
    private static int lastSweep;

    private PresenceClaimMemory() {
    }

    /**
     * 这名真玩家能不能动 presence 所在的那一格：记着且没过期、聚集体还在那一格就直接用记着的；否则以他的身份探测一次
     * （{@link ECProtection#check}，预算按聚集体分），ALLOW / DENY 记下来，UNKNOWN（区块没加载全、预算用完）不记。
     */
    public static ECProtection.Verdict check(ServerPlayer player, MRUPresenceEntity presence) {
        if (!(presence.level() instanceof ServerLevel level)) {
            return ECProtection.Verdict.DENY;
        }
        int now = level.getServer().getTickCount();
        sweep(now);
        BlockPos pos = presence.blockPosition();
        Key key = new Key(player.getUUID(), presence.getUUID());
        Entry entry = MEMORY.get(key);
        if (entry != null && entry.cell == pos.asLong() && entry.until - now > 0) {
            return entry.allowed ? ECProtection.Verdict.ALLOW : ECProtection.Verdict.DENY;
        }
        ECProtection.Verdict verdict = ECProtection.check(player, level, pos, presence);
        if (verdict != ECProtection.Verdict.UNKNOWN) {
            MEMORY.put(key, new Entry(pos.asLong(), verdict == ECProtection.Verdict.ALLOW, now + REMEMBER_TICKS));
        }
        return verdict;
    }

    /** 忘掉这名玩家的全部结论（下线时；测试里换了模拟领地时） */
    public static void forget(UUID player) {
        MEMORY.keySet().removeIf(key -> key.player().equals(player));
    }

    /** 记着几条（调试与 GameTest） */
    public static int size() {
        return MEMORY.size();
    }

    /** GameTest 用：所有结论的到期时间提前 ticks 刻 */
    public static void ageForTest(int ticks) {
        MEMORY.replaceAll((key, entry) -> new Entry(entry.cell, entry.allowed, entry.until - ticks));
    }

    private static void sweep(int now) {
        if (now - lastSweep < SWEEP_INTERVAL && MEMORY.size() <= LIMIT) {
            return;
        }
        lastSweep = now;
        MEMORY.values().removeIf(entry -> entry.until - now <= 0);
        if (MEMORY.size() > LIMIT) {
            MEMORY.clear();
        }
    }

    @SubscribeEvent
    public static void onPresenceClaimLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!event.getEntity().level().isClientSide) {
            forget(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onPresenceClaimServerStarting(ServerStartingEvent event) {
        MEMORY.clear();
        lastSweep = 0;
    }

    @SubscribeEvent
    public static void onPresenceClaimServerStopped(ServerStoppedEvent event) {
        MEMORY.clear();
        lastSweep = 0;
    }
}
