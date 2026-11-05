package com.flameforge.essentialcraft5.hoanna.portal;

import com.flameforge.essentialcraft5.matrix.MatrixRank;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHoannaPortal;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.LogicalSide;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 海纳尔之门的等待计时（第六批 6a M2，batch6a.md §5.2、§4.3）。服务端，只登记正站在门里的真玩家：{@code UUID → 入口、计数、最后一次在门里的刻}。
 * <ul>
 *   <li>{@link #inside}（门方块的 {@code entityInside} 调，一刻里可能被调好几次：门宽 2 格时每格一次、移动包也会走一遍）：同一刻只算一次；
 *       每刻最多 +1，封顶等待时间（创造 / 无敌 1 刻，同原版 {@code Player.getPortalWaitTime}；否则 {@code portal_wait_ticks}，默认 80）。</li>
 *   <li>{@link #onPlayerTick}（PlayerTickEvent END，门方块的 {@code entityInside} 在同一刻的 aiStep 里先跑过了）：这一刻不在门里就每刻 −4（同原版），
 *       减到 0 摘掉；到点了在这里调 {@link HoannaTeleport#travel}（不在 {@code entityInside} 里换维度）。</li>
 *   <li><b>冷却</b>：过门后冷却 {@code portal_cooldown_ticks}（默认 300 = 15 秒）。站在门里期间冷却不减、而且不低于 {@value #MIN_HELD_COOLDOWN}：
 *       进门那一刻记下 max(当前冷却, 20)，之后每刻按回这个值——到了对面站在门里不会被立刻送回去，得先走出来。站着等了一整个等待时间还在冷却，提示一次。</li>
 *   <li><b>骑乘</b>（D9 A）：骑着东西（或身上有乘客）不计时，提示「下来才能过门」；门关了（{@code portal_enabled = false}）时主世界这边也不计时，
 *       提示没有回应——海纳尔那边照常计时，已经在海纳尔的人能回来（{@code ECHoannaPortal.Config.portalEnabledIn}）。
 *       提示每人至多 {@value #HINT_INTERVAL} 刻一次。</li>
 *   <li><b>预加载</b>（{@code portal_preload}）：计时开始那一刻（计数从 0 到 1）给目标处加 PORTAL 票（{@link HoannaTeleport#preload}），
 *       每人每个冷却周期至多一次；只对真在玩家列表里的人做（GameTest 的测试玩家不会去生成海纳尔区块）。</li>
 *   <li>下线、换维度、关服时清掉。</li>
 * </ul>
 * 开销：每名玩家每刻一次 HashMap 查询（不在表里直接返回），O(门里的人数)。
 */
public final class HoannaPortalTracker {

    /** 站在门里时冷却至少按住多少刻（得先走出来） */
    public static final int MIN_HELD_COOLDOWN = 20;
    /** 同一名玩家两次提示至少隔几刻 */
    public static final int HINT_INTERVAL = 60;
    /** 不在门里时计数每刻减多少（同原版 Entity.handleNetherPortal） */
    public static final int DECAY_PER_TICK = 4;

    private static final Map<UUID, Entry> ENTRIES = new HashMap<>();
    private static final Map<UUID, Long> LAST_PRELOAD = new HashMap<>();
    private static final Map<UUID, Long> LAST_HINT = new HashMap<>();

    private static final class Entry {
        BlockPos entrance = BlockPos.ZERO;
        long lastInside = Long.MIN_VALUE;
        int count;
        /** 站在门里期间按住的冷却值（0 = 没在按） */
        int heldCooldown;
        /** 站在门里、冷却中已经等了几刻 */
        int heldTicks;
        boolean cooldownHinted;
    }

    private HoannaPortalTracker() {
    }

    /** 这名玩家要等多少刻：创造 / 无敌 1 刻（同原版 Player.getPortalWaitTime），否则 portal_wait_ticks */
    public static int waitTicks(Player player) {
        return player.getAbilities().invulnerable ? 1 : ECHoannaPortal.Config.waitTicks();
    }

    /** 门方块的 entityInside 在服务端对真玩家调（调用方已经判过 isRealPlayer） */
    public static void inside(ServerPlayer player, BlockPos pos) {
        long now = player.level().getGameTime();
        // 开关关掉只挡主世界这头：已经在海纳尔的人照样计时、照样能回来
        if (!ECHoannaPortal.Config.portalEnabledIn(player.level())) {
            hint(player, now, "message.essentialcraft5.hoanna.disabled");
            return;
        }
        if (player.isPassenger() || player.isVehicle()) {
            hint(player, now, "message.essentialcraft5.hoanna.riding");
            return;
        }
        if (!player.canChangeDimensions()) {
            return;
        }
        Entry entry = ENTRIES.computeIfAbsent(player.getUUID(), id -> new Entry());
        if (entry.lastInside == now) {
            return;
        }
        entry.lastInside = now;
        entry.entrance = pos.immutable();
        if (player.isOnPortalCooldown()) {
            if (entry.heldCooldown == 0) {
                entry.heldCooldown = Math.max(player.getPortalCooldown(), MIN_HELD_COOLDOWN);
            }
            player.setPortalCooldown(entry.heldCooldown);
            entry.count = 0;
            if (++entry.heldTicks >= waitTicks(player) + MIN_HELD_COOLDOWN && !entry.cooldownHinted) {
                entry.cooldownHinted = true;
                hint(player, now, "message.essentialcraft5.hoanna.cooldown");
            }
            return;
        }
        entry.heldCooldown = 0;
        entry.heldTicks = 0;
        entry.count = Math.min(entry.count + 1, waitTicks(player));
        if (entry.count == 1) {
            preload(player, now);
        }
    }

    /** PlayerTickEvent END（ECHoannaPortal.init 登记）：衰减、到点传送 */
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        Entry entry = ENTRIES.get(player.getUUID());
        if (entry == null) {
            return;
        }
        long now = player.level().getGameTime();
        if (entry.lastInside != now) {
            entry.heldCooldown = 0;
            entry.heldTicks = 0;
            entry.cooldownHinted = false;
            entry.count -= DECAY_PER_TICK;
            if (entry.count <= 0) {
                ENTRIES.remove(player.getUUID());
            }
            return;
        }
        if (entry.count > 0 && entry.count >= waitTicks(player)) {
            ENTRIES.remove(player.getUUID());
            HoannaTeleport.travel(player, entry.entrance);
        }
    }

    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        forget(event.getEntity().getUUID());
        LAST_PRELOAD.remove(event.getEntity().getUUID());
    }

    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        ENTRIES.remove(event.getEntity().getUUID());
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        ENTRIES.clear();
        LAST_PRELOAD.clear();
        LAST_HINT.clear();
    }

    // ------------------------------------------------------------------
    // 给测试看的
    // ------------------------------------------------------------------

    /** 这名玩家正在门里等了几刻（没登记为 0） */
    public static int count(UUID player) {
        Entry entry = ENTRIES.get(player);
        return entry == null ? 0 : entry.count;
    }

    /** 这名玩家在不在表里 */
    public static boolean isTracked(UUID player) {
        return ENTRIES.containsKey(player);
    }

    /** 忘掉这名玩家（计时、提示节流；测试收尾用） */
    public static void forget(UUID player) {
        ENTRIES.remove(player);
        LAST_HINT.remove(player);
    }

    // ------------------------------------------------------------------

    private static void preload(ServerPlayer player, long now) {
        if (!ECHoannaPortal.Config.preload() || player.server.getPlayerList().getPlayer(player.getUUID()) != player) {
            return;
        }
        // 矩阵阶不够、到点也传不过去的人不预加载对面（第十三批 13a 的门，判定在 HoannaTeleport.travel）
        if (!ECHoanna.isHoanna(player.level()) && !MatrixRank.mayEnterHoanna(player)) {
            return;
        }
        Long last = LAST_PRELOAD.get(player.getUUID());
        if (last != null && now - last < ECHoannaPortal.Config.cooldownTicks()) {
            return;
        }
        LAST_PRELOAD.put(player.getUUID(), now);
        HoannaTeleport.preload(player);
    }

    private static void hint(ServerPlayer player, long now, String key) {
        Long last = LAST_HINT.get(player.getUUID());
        if (last != null && now - last < HINT_INTERVAL && now >= last) {
            return;
        }
        LAST_HINT.put(player.getUUID(), now);
        HoannaTeleport.hint(player, key);
    }
}
