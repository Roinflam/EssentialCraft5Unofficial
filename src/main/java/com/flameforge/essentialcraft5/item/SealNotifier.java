package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.compat.curio.CurioSealConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 饰品封禁场的提示，不刷屏（tools/plan/batch10.md §5.1.6）。
 * <h2>穿戴者（服务端发，{@code displayClientMessage}）</h2>
 * <ul>
 *   <li>开 / 关、用不了、没穿齐：按开关时动作栏一条（开关本身 5 刻内只认一次，见 {@link ComputerSealField}）。</li>
 *   <li>有人进 / 出、待机 ↔ 运转：记进待发表，距上一条至少 {@value #MERGE_TICKS} 刻才发，发时合成一条动作栏
 *       （「饰品封禁场：A、B 进入；C 离开；运转中，压制 2 人」）；同一人离开后 {@value #REENTER_QUIET_TICKS} 刻内又回来不提，他这次的离开也不提；
 *       进来又在同一个待发窗口里出去的两条互相抵掉。名字超过 3 个写「… 等 N 人」。</li>
 *   <li>电量不足：进入这个状态时聊天栏 + 动作栏各一次；转回待机、或者连续运转满 {@value #REENTER_QUIET_TICKS} 刻以后才会再提。</li>
 * </ul>
 * <h2>被压制的人（客户端自己出，见 client/CurioSealClient）</h2>
 * 收到压制包后由 {@link VictimDebounce} 决定：每次登录第一次被压制聊天栏一行说明（谁的场、离开多远恢复）；之后每次开始被压制动作栏一条 + 一声；
 * 解除后 {@value #REENTER_QUIET_TICKS} 刻内又被压制不出字、不响，只靠格子图标。服务端另有最少保持时间和离开阈值，两层一起压住来回跳。
 * 只被自己的场压着（穿戴者 {@code affects_self}，发给本人的名单去掉自己以后为空）时什么都不出，屏幕顶上亮着的紫圆已经说明了；
 * 之后别人的场也罩上来，按一次新的压制提示。
 * <p>
 * GameTest 用 {@link #captureForTest} 按玩家收集发出去的条目（只记，照样发），收尾 {@link #stopCapture}。只在服务端线程用（收集表除外）。
 */
public final class SealNotifier {

    /** 两条进出提示之间至少隔多少刻 */
    public static final int MERGE_TICKS = 40;
    /** 离开后多少刻内又回来不提（被压制的一方：解除后多少刻内又被压制不提） */
    public static final int REENTER_QUIET_TICKS = 200;
    /** 名字最多列几个 */
    public static final int MAX_NAMES = 3;

    private static final String PREFIX = "message.essentialcraft5.computer_seal.";

    /**
     * 发出去的一条提示（测试收集器用）。
     *
     * @param key       翻译键
     * @param actionBar true = 动作栏，false = 聊天栏
     * @param text      整条文字
     */
    public record Sent(String key, boolean actionBar, Component text) {
    }

    /** 一名穿戴者的提示状态 */
    private static final class WearerState {
        /** 上次评估时压制着的别的玩家（UUID → 名字） */
        final Map<UUID, String> current = new LinkedHashMap<>();
        final Map<UUID, String> pendingEnter = new LinkedHashMap<>();
        final Map<UUID, String> pendingLeave = new LinkedHashMap<>();
        /** 离开的刻数（只留 {@value #REENTER_QUIET_TICKS} 刻） */
        final Map<UUID, Long> leftAt = new HashMap<>();
        /** 进来时没提的人：他这次离开也不提 */
        final Set<UUID> silent = new HashSet<>();
        /** 待发的待机 / 运转变化（null = 没变） */
        Boolean pendingRunning;
        boolean running;
        long lastSent = Long.MIN_VALUE / 2;
        boolean noPowerTold;
        /** 「电量不足」提示过以后，从哪一刻起一直在运转（{@link #NOT_POWERED} = 没在数） */
        long poweredSince = NOT_POWERED;
    }

    private static final long NOT_POWERED = Long.MIN_VALUE;

    private static final Map<UUID, WearerState> WEARERS = new HashMap<>();
    private static final Map<UUID, List<Sent>> CAPTURE = new ConcurrentHashMap<>();

    private SealNotifier() {
    }

    // ------------------------------------------------------------------ 穿戴者

    /**
     * 每次评估之后调：sealedOthers 是这一秒压制着的别的玩家（UUID → 名字，不含自己），running = 场在运转。
     * 进出与状态变化记进待发表，到了间隔就合成一条发出去。
     */
    public static void update(ServerPlayer wearer, Map<UUID, String> sealedOthers, boolean running, long now) {
        WearerState state = WEARERS.computeIfAbsent(wearer.getUUID(), id -> new WearerState());
        for (Map.Entry<UUID, String> entry : sealedOthers.entrySet()) {
            UUID id = entry.getKey();
            if (state.current.containsKey(id)) {
                continue;
            }
            if (state.pendingLeave.remove(id) != null) {
                // 出去又在同一个待发窗口里回来：两条抵掉
                continue;
            }
            Long left = state.leftAt.get(id);
            if (left != null && now - left >= 0 && now - left <= REENTER_QUIET_TICKS) {
                state.silent.add(id);
            } else {
                state.pendingEnter.put(id, entry.getValue());
            }
        }
        for (Map.Entry<UUID, String> entry : state.current.entrySet()) {
            UUID id = entry.getKey();
            if (sealedOthers.containsKey(id)) {
                continue;
            }
            state.leftAt.put(id, now);
            if (state.silent.remove(id)) {
                continue;
            }
            if (state.pendingEnter.remove(id) == null) {
                state.pendingLeave.put(id, entry.getValue());
            }
        }
        state.current.clear();
        state.current.putAll(sealedOthers);
        if (running != state.running) {
            state.running = running;
            state.pendingRunning = running;
        }
        state.leftAt.values().removeIf(at -> now - at > REENTER_QUIET_TICKS || now < at);
        flush(wearer, state, now);
    }

    private static void flush(ServerPlayer wearer, WearerState state, long now) {
        if (state.pendingEnter.isEmpty() && state.pendingLeave.isEmpty() && state.pendingRunning == null) {
            return;
        }
        if (now - state.lastSent < MERGE_TICKS && now >= state.lastSent) {
            return;
        }
        List<Component> parts = new ArrayList<>();
        if (!state.pendingEnter.isEmpty()) {
            parts.add(Component.translatable(PREFIX + "enter", names(state.pendingEnter.values())));
        }
        if (!state.pendingLeave.isEmpty()) {
            parts.add(Component.translatable(PREFIX + "leave", names(state.pendingLeave.values())));
        }
        if (state.pendingRunning != null) {
            parts.add(state.pendingRunning
                    ? Component.translatable(PREFIX + "running", state.current.size())
                    : Component.translatable(PREFIX + "standby"));
        }
        state.pendingEnter.clear();
        state.pendingLeave.clear();
        state.pendingRunning = null;
        state.lastSent = now;
        Component body = parts.get(0);
        for (int i = 1; i < parts.size(); i++) {
            body = Component.translatable(PREFIX + "join", body, parts.get(i));
        }
        send(wearer, "status", true, Component.translatable(PREFIX + "status", body).withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    /** 名字表：至多 {@value #MAX_NAMES} 个，多出来的写「… 等 N 人」 */
    public static Component names(Collection<String> all) {
        List<String> list = new ArrayList<>(all);
        int shown = Math.min(MAX_NAMES, list.size());
        Component head = switch (shown) {
            case 0 -> Component.empty();
            case 1 -> Component.literal(list.get(0));
            case 2 -> Component.translatable(PREFIX + "list2", list.get(0), list.get(1));
            default -> Component.translatable(PREFIX + "list3", list.get(0), list.get(1), list.get(2));
        };
        return list.size() > shown ? Component.translatable(PREFIX + "more", head, list.size()) : head;
    }

    /** 电量不足：进入这个状态时聊天栏 + 动作栏各一次（再提要等 {@link #powered} 说可以） */
    public static void noPower(ServerPlayer wearer, int perSecond) {
        WearerState state = WEARERS.computeIfAbsent(wearer.getUUID(), id -> new WearerState());
        state.poweredSince = NOT_POWERED;
        if (state.noPowerTold) {
            return;
        }
        state.noPowerTold = true;
        Component text = Component.translatable(PREFIX + "no_power", perSecond).withStyle(ChatFormatting.GOLD);
        send(wearer, "no_power", false, text);
        send(wearer, "no_power", true, text);
    }

    /**
     * 这一次评估没缺电：待机（running = false）时下次电量不足照常再提；运转时要连续供电满 {@value #REENTER_QUIET_TICKS} 刻才再提——
     * 电量卡在边上、一秒够一秒不够地来回切时，「电量不足」不会每隔几秒刷一次。关掉、死亡、换维度、下线另由 {@link #reset} 清掉。
     */
    public static void powered(ServerPlayer wearer, boolean running, long now) {
        WearerState state = WEARERS.get(wearer.getUUID());
        if (state == null || !state.noPowerTold) {
            return;
        }
        if (!running) {
            state.noPowerTold = false;
            state.poweredSince = NOT_POWERED;
        } else if (state.poweredSince == NOT_POWERED || now < state.poweredSince) {
            state.poweredSince = now;
        } else if (now - state.poweredSince >= REENTER_QUIET_TICKS) {
            state.noPowerTold = false;
            state.poweredSince = NOT_POWERED;
        }
    }

    /**
     * 开 / 关（动作栏）。打开时说清楚它压制谁：要「最近交过手」时写明「{@code combat_window_ticks} 秒内和你交过手的人」，
     * 不然只写半径（整合包里没读过手册的玩家只看得到这一行）
     */
    public static void toggled(ServerPlayer wearer, boolean on, CurioSealConfig.Values cfg) {
        Component text;
        if (!on) {
            text = Component.translatable(PREFIX + "off").withStyle(ChatFormatting.GRAY);
        } else if (cfg.requireRecentCombat()) {
            text = Component.translatable(PREFIX + "on_combat", cfg.radius(), seconds(cfg.combatWindowTicks())).withStyle(ChatFormatting.LIGHT_PURPLE);
        } else {
            text = Component.translatable(PREFIX + "on", cfg.radius()).withStyle(ChatFormatting.LIGHT_PURPLE);
        }
        send(wearer, on ? "on" : "off", true, text);
    }

    /** 刻 → 秒（整数秒不带小数，例：200 → "10"、30 → "1.5"） */
    public static String seconds(int ticks) {
        return ticks % 20 == 0 ? Integer.toString(ticks / 20) : java.math.BigDecimal.valueOf(ticks / 20D).stripTrailingZeros().toPlainString();
    }

    /** 按开关时功能用不了（reason 是终端里的原因文字） */
    public static void unavailable(ServerPlayer wearer, Component reason) {
        send(wearer, "unavailable", true, Component.translatable(PREFIX + "unavailable", reason).withStyle(ChatFormatting.GRAY));
    }

    /** 按开关时没穿齐四件 */
    public static void incomplete(ServerPlayer wearer) {
        send(wearer, "incomplete", true, Component.translatable(PREFIX + "incomplete").withStyle(ChatFormatting.GRAY));
    }

    /** 在场不起作用的维度里打开，或开着场进了这样的维度（动作栏，状态翻转时一次） */
    public static void disabledWorld(ServerPlayer wearer) {
        send(wearer, "disabled_world", true, Component.translatable(PREFIX + "disabled_world").withStyle(ChatFormatting.GRAY));
    }

    /** 场关掉、穿戴者死亡、换维度、下线：进出记录作废（下次打开从头算，第一批人照常提示） */
    public static void reset(UUID wearer) {
        WEARERS.remove(wearer);
    }

    /** 关服时清空 */
    public static void clear() {
        WEARERS.clear();
    }

    private static void send(ServerPlayer player, String key, boolean actionBar, Component text) {
        List<Sent> capture = CAPTURE.get(player.getUUID());
        if (capture != null) {
            capture.add(new Sent(PREFIX + key, actionBar, text));
        }
        if (!(player instanceof FakePlayer)) {
            player.displayClientMessage(text, actionBar);
        }
    }

    // ------------------------------------------------------------------ 测试

    /** GameTest 用：从现在起记下发给这名玩家的提示（照样发出去） */
    public static List<Sent> captureForTest(UUID player) {
        List<Sent> list = Collections.synchronizedList(new ArrayList<>());
        CAPTURE.put(player, list);
        return list;
    }

    /** GameTest 用：不再记 */
    public static void stopCapture(UUID player) {
        CAPTURE.remove(player);
    }

    // ------------------------------------------------------------------ 被压制的一方

    /**
     * 被压制的一方的提示节奏（客户端用，逻辑写在这里好测）：每次登录第一次被压制出聊天栏说明（{@link Kind#FIRST}），
     * 之后每次开始被压制出动作栏 + 一声（{@link Kind#SEALED}）；解除后 {@value #REENTER_QUIET_TICKS} 刻内又被压制什么都不出（{@link Kind#QUIET}）。
     * 解除时出「恢复生效」，但开始那次没出声的（{@link Kind#QUIET}）解除也不出。时钟由调用方给（客户端自己数的刻）。
     * <p>
     * 只被自己的场压着（{@link Kind#SELF}）不算一次「被压制」：不出字，不占「这次登录第一次」，解除时不出「恢复生效」、也不记解除时刻
     * （否则别人随后真来封你会被当成「刚解除又被压制」，一个字都不出）。自封期间别人的场也罩上来时调 {@link #onOthersJoined}，按一次新的压制算。
     */
    public static final class VictimDebounce {

        /** 开始被压制时出什么 */
        public enum Kind {
            /** 这次登录第一次：聊天栏一行说明 */
            FIRST,
            /** 动作栏一条 + 一声 */
            SEALED,
            /** 不出字、不响 */
            QUIET,
            /** 只被自己的场压着：不出字、不响 */
            SELF
        }

        private boolean toldThisLogin;
        private boolean lastQuiet;
        private boolean selfOnly;
        private long lastReleased = Long.MIN_VALUE / 2;

        /** 开始被压制（有别人的场） */
        public Kind onSealed(long now) {
            return onSealed(now, false);
        }

        /** 开始被压制；selfOnly = 只有自己的场（发给本人的名单去掉自己以后为空） */
        public Kind onSealed(long now, boolean selfOnly) {
            this.selfOnly = selfOnly;
            if (selfOnly) {
                return Kind.SELF;
            }
            if (!toldThisLogin) {
                toldThisLogin = true;
                lastQuiet = false;
                return Kind.FIRST;
            }
            lastQuiet = now - lastReleased >= 0 && now - lastReleased <= REENTER_QUIET_TICKS;
            return lastQuiet ? Kind.QUIET : Kind.SEALED;
        }

        /**
         * 只被自己的场压着时，别人的场也罩上来了（发给本人的名单由空变成有人）：按一次新的压制算；不在自封状态时返回 null（什么都不做）
         */
        @Nullable
        public Kind onOthersJoined(long now) {
            return selfOnly ? onSealed(now, false) : null;
        }

        /** 现在是不是只被自己的场压着 */
        public boolean selfOnly() {
            return selfOnly;
        }

        /** 解除：要不要出「恢复生效」（自封的那次解除不出，也不记解除时刻） */
        public boolean onReleased(long now) {
            if (selfOnly) {
                selfOnly = false;
                return false;
            }
            lastReleased = now;
            return !lastQuiet;
        }

        /** 登录 / 退出：重新算「这次登录第一次」 */
        public void reset() {
            toldThisLogin = false;
            lastQuiet = false;
            selfOnly = false;
            lastReleased = Long.MIN_VALUE / 2;
        }
    }
}
