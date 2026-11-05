package com.flameforge.essentialcraft5.player;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.item.attunement.AttunementConfig;
import com.flameforge.essentialcraft5.item.attunement.AttunementGains;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 同调预算（batch13.md §3.3.3）：每名玩家盔甲、武器各一份，在线时每秒 +{@code budget_per_hour} / 3600（默认 0.1，一小时 360），
 * 最多存 {@code budget_cap}（默认 720，约两小时）。预算只限速、不限来源，存满就不再涨；来源由 {@code item/attunement/AttunementGains} 的几条防挂机规则管。
 * <p>
 * 每 20 刻加一次（O(1)）；自然增长用安静写法（{@code setAttunementBudget(…, false)}），只在跨过整 10 点时才标记同步，
 * 客户端提示框按同步来的数显示，最多差 10 点。每 40 刻顺手让 {@link AttunementGains#flush} 把攒的点写进装备。
 * 只给真玩家加（假玩家不 tick；GameTest 直接调 {@link #accrue}）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class AttunementBudget {

    /** 多少刻加一次 */
    public static final int ACCRUE_INTERVAL = 20;

    private AttunementBudget() {
    }

    @SubscribeEvent
    public static void onAttunementBudgetTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !event.side.isServer()) {
            return;
        }
        Player player = event.player;
        if (!ECProtection.isRealPlayer(player)) {
            return;
        }
        int age = player.tickCount;
        if (age % ACCRUE_INTERVAL == 0) {
            accrue(player, ACCRUE_INTERVAL);
        }
        if (age % AttunementGains.FLUSH_INTERVAL == 0) {
            AttunementGains.flush(player);
        }
    }

    /** 按在线 ticks 刻加两份预算（夹到上限）；总开关关着时不涨 */
    public static void accrue(Player player, int ticks) {
        if (!AttunementConfig.enabled()) {
            return;
        }
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            return;
        }
        float gain = AttunementConfig.budgetPerHour() * ticks / 72_000F;
        float cap = AttunementConfig.budgetCap();
        for (IPlayerData.BudgetKind kind : IPlayerData.BudgetKind.values()) {
            float current = data.getAttunementBudget(kind);
            if (current >= cap) {
                continue;
            }
            data.setAttunementBudget(kind, Math.min(cap, current + gain), false);
        }
    }

    /** 现在剩多少预算（没有玩家数据时 0） */
    public static float get(Player player, IPlayerData.BudgetKind kind) {
        IPlayerData data = PlayerDataEvents.get(player);
        return data == null ? 0F : data.getAttunementBudget(kind);
    }

    /** 直接设（指令用）：夹到 0 ~ 上限，标记同步 */
    public static void set(Player player, IPlayerData.BudgetKind kind, float value) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data != null) {
            float clamped = Math.max(0F, Math.min(AttunementConfig.budgetCap(), Float.isFinite(value) ? value : 0F));
            data.setAttunementBudget(kind, clamped, true);
        }
    }

    /** 提示框里预算那一行：存满了 / 不再增长（每小时增长配成 0）/ 还在涨（写存满要多久） */
    public enum Line {
        FULL,
        STATIC,
        FILLING
    }

    /** 预算那一行该用哪种写法（只看数，两端都能调）：到了上限是存满；没到上限、每小时又不涨，是「不再增长」，不能写成存满 */
    public static Line line(float current, int cap, int perHour) {
        if (current >= cap) {
            return Line.FULL;
        }
        return perHour <= 0 ? Line.STATIC : Line.FILLING;
    }

    /** 从现在的预算到存满还要在线多少秒（提示框「存满约 N 小时」用）；每小时增长是 0 时返回 -1 */
    public static long secondsToFull(float current) {
        int perHour = AttunementConfig.budgetPerHour();
        if (perHour <= 0) {
            return -1L;
        }
        float missing = Math.max(0F, AttunementConfig.budgetCap() - current);
        return (long) Math.ceil(missing * 3600D / perHour);
    }
}
