package com.flameforge.essentialcraft5.matrix;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.item.bauble.BaubleEffects;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECBaubles;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

/**
 * 矩阵容量（第十三批 13a，batch13.md §3.1.4，限制见 policy §4.16）：二阶起体内存 MRU，上限 {@code pool_per_rank} × (阶 − 1)。
 * <ul>
 *   <li><b>换入</b>（{@link #absorbOnce}，每 20 刻一次）：UBMRU 按矩阵换能器的比例（{@value #UBMRU_PER_MRU} 换 1）换进来，每秒最多
 *       {@code pool_absorb_per_second}（10）MRU，满了停；只换<b>保留线</b>以上的那部分 UBMRU（{@link #reserveLine}：{@code pool_absorb_reserve} 默认 2 万，
 *       戴着 θ 腰带时 ×{@value #THETA_RESERVE_FACTOR}）——θ 的致死保护按伤害 × 5 000 扣 UBMRU，ζ / ο / τ / φ 也按次扣，容量不能把这些储备悄悄吸空。
 *       开关在玩家数据里（{@link IPlayerData#isMatrixPoolAbsorb()}，默认开）；潜行右键灵魂石切换，灵魂石那边调 {@link #toggleAbsorb}。</li>
 *   <li><b>节流同步</b>（batch13.md §3.1.5）：换入时 UBMRU、容量都用安静写法（不标记同步），只在容量跨过整 {@value #SYNC_STEP} MRU、
 *       这次换完满了、或者这次因为碰到保留线没换满时标记一次——不然开着换入的人每秒收一整包玩家数据。客户端看到的 UBMRU / 容量最多差
 *       {@value #SYNC_STEP} MRU（1 000 UBMRU），别的原因发包时顺带更新。容量被花掉、指令改的照常主动标记（{@link #drain}）。</li>
 *   <li><b>扣费顺序</b>：身上储存物品 → 容量 → 物品自己（{@code mru/MRUDrain}；宅男 / 虚空 / 魔力防护的护盾经 {@code MRUDrain.payShield} 也走它，
 *       按原价、新覆盖面不认无尽晶体）。</li>
 *   <li>死亡清零（{@code PlayerDataEvents.clone}），换维度、下线不清。阶降下来（指令）时多出来的部分在下一次换入时夹回上限。</li>
 * </ul>
 * 服主关掉矩阵阶时已有的阶照给回报，容量照常换入。只在服务端、只对真玩家（不是假玩家）。
 */
public final class MatrixPool {

    /** 多少 UBMRU 换 1 MRU（同矩阵换能器） */
    public static final int UBMRU_PER_MRU = 10;
    /** 戴 θ 腰带时保留线乘几 */
    public static final int THETA_RESERVE_FACTOR = 5;
    /** 换入间隔（刻） */
    public static final int ABSORB_INTERVAL = 20;
    /** 换入时容量每跨过这么多 MRU 标记一次同步 */
    public static final int SYNC_STEP = 100;

    private MatrixPool() {
    }

    /** n 阶的容量上限：二阶起 {@code pool_per_rank} × (阶 − 1)，零、一阶是 0 */
    public static int capacity(int rank) {
        if (rank < 2) {
            return 0;
        }
        long cap = (long) MatrixConfig.poolPerRank() * (Math.min(rank, MatrixRank.MAX) - 1);
        return (int) Math.min(Integer.MAX_VALUE, cap);
    }

    public static int capacity(IPlayerData data) {
        return capacity(data.getMatrixRank());
    }

    /** 保留线：换入时留着不动的 UBMRU（戴 θ 腰带时 ×{@value #THETA_RESERVE_FACTOR}） */
    public static long reserveLine(Player player) {
        long reserve = MatrixConfig.poolAbsorbReserve();
        return BaubleEffects.has(player, ECBaubles.THETA_BELT.get()) ? reserve * THETA_RESERVE_FACTOR : reserve;
    }

    /**
     * 切换换入开关（主手拿着灵魂石潜行右键时由灵魂石调），动作栏说一句现在开着还是关着、保留线多少。
     * 保留线直接传整数：这句在服务端拼，专用服的语言是英文，按语言分组会给中文玩家看「20,000」。
     *
     * @return 切换后的状态（没有玩家数据时 false、什么都不做）
     */
    public static boolean toggleAbsorb(Player player) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            return false;
        }
        boolean on = !data.isMatrixPoolAbsorb();
        data.setMatrixPoolAbsorb(on);
        player.displayClientMessage(Component.translatable(on ? "message.essentialcraft5.matrix_pool.absorb_on" : "message.essentialcraft5.matrix_pool.absorb_off",
                reserveLine(player)).withStyle(on ? ChatFormatting.AQUA : ChatFormatting.GRAY), true);
        return on;
    }

    /** 容量里现在有多少 MRU（没有玩家数据是 0） */
    public static int available(Player player) {
        IPlayerData data = PlayerDataEvents.get(player);
        return data == null ? 0 : data.getMatrixPool();
    }

    /**
     * 从容量里扣（被消耗，标记同步）。
     *
     * @return 实际扣掉的量（不够时扣光）
     */
    public static int drain(Player player, int amount) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null || amount <= 0) {
            return 0;
        }
        int pool = data.getMatrixPool();
        int taken = Math.min(pool, amount);
        if (taken > 0) {
            data.setMatrixPool(pool - taken, true);
        }
        return taken;
    }

    /**
     * 换入一次（每秒一次；GameTest 直接调）：先把超出上限的夹回去，再按开关、保留线、每秒上限换。
     *
     * @return 这次换进来的 MRU
     */
    public static int absorbOnce(Player player, IPlayerData data) {
        int cap = capacity(data);
        int pool = data.getMatrixPool();
        if (pool > cap) {
            data.setMatrixPool(cap, true);
            return 0;
        }
        if (cap <= 0 || pool >= cap || !data.isMatrixPoolAbsorb()) {
            return 0;
        }
        long spare = (long) data.getUBMRU() - reserveLine(player);
        if (spare < UBMRU_PER_MRU) {
            return 0;
        }
        long room = Math.min(MatrixConfig.poolAbsorbPerSecond(), cap - pool);
        long moved = Math.min(room, spare / UBMRU_PER_MRU);
        if (moved <= 0L) {
            return 0;
        }
        int after = pool + (int) moved;
        data.setUBMRU((int) (data.getUBMRU() - moved * UBMRU_PER_MRU), false);
        data.setMatrixPool(after, false);
        // 跨过整百、满了、碰到保留线停下来（下一秒起不再换）时才发一包
        if (after / SYNC_STEP != pool / SYNC_STEP || after >= cap || moved < room) {
            data.markDirty();
        }
        return (int) moved;
    }

    /** Forge 总线（类名带模块前缀，见 tools/check_event_handlers.py） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
    public static final class MatrixPoolEvents {

        private MatrixPoolEvents() {
        }

        @SubscribeEvent
        public static void matrixPoolTick(TickEvent.PlayerTickEvent event) {
            Player player = event.player;
            if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER || player instanceof FakePlayer
                    || player.tickCount % ABSORB_INTERVAL != 0 || !player.isAlive()) {
                return;
            }
            IPlayerData data = PlayerDataEvents.get(player);
            if (data != null && (data.getMatrixRank() >= 2 || data.getMatrixPool() > 0)) {
                absorbOnce(player, data);
            }
        }
    }
}
