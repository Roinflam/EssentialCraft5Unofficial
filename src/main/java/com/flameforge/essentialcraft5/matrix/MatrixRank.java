package com.flameforge.essentialcraft5.matrix;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.api.MatrixRanks;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 矩阵阶（第十三批 13a，batch13.md §2.5、§3.1，限制见 policy §4.16）的判定入口：门、回报、阶的读写、条件核对。
 * 只读数用 {@link MatrixRanks#get}（两端都能用）；判门、判回报都走这里。
 *
 * <h2>门（{@link Gate}）</h2>
 * 只挡进、不挡出，永远不会把人困在海纳尔。{@code general.matrix_rank.enabled = false} 时所有门开；某道门的配置填 0 = 不设这道门；
 * 创造、旁观模式不挡。门只看本人的阶。
 * <ul>
 *   <li>海纳尔之门（默认五阶）：从海纳尔以外进海纳尔的所有入口都调 {@link #mayEnterHoanna}——海纳尔之门（站满等待时间时判，见
 *       {@code hoanna/portal/HoannaTeleport.travel}）、魔力传送器（{@code checkTarget} 返回 {@code RANK_TOO_LOW}）、归途罗盘（服主放开以海纳尔为目标时）。
 *       以后再加进海纳尔的入口也调它。别的模组和指令的跨维度传送（/tp、/tpa、传送石碑……）由 {@link MatrixRankGateEvents} 在
 *       {@code EntityTravelToDimensionEvent} 上兜底（{@code gates.block_all_entries}，默认开）：本模组的入口先判过同一个谓词，重复判不冲突。</li>
 *   <li>全息投影台（默认六阶）：{@code block/HolopadBlock.summon}。</li>
 *   <li>天灾信标（默认八阶，13e）：召唤判定调 {@link #mayPass}{@code (player, Gate.CALAMITY_BEACON)}，挡下时动作栏用 {@link #gateMessage}。</li>
 * </ul>
 *
 * <h2>回报（按阶，写在代码里，不进数据）</h2>
 * 一阶矩阵视觉（{@code api/MRUVision}）；二阶起矩阵容量（{@link MatrixPool}）；三 / 六 / 九阶相性 I / II / III（{@code matrix/MatrixResonance}）；
 * 四阶矩阵虹吸；五阶海纳尔辐射抗性 +{@value #RADIATION_RESISTANCE}（{@code api/MRUResistance}）；七阶矩阵稳固；每阶耐受 +10%（{@link #scaleNewDamage}）。
 * 服主关掉矩阵阶时已有的阶照给回报（只是门全开、升不了新阶）。
 *
 * <h2>和平难度</h2>
 * 和平难度不刷敌对生物，图谱条件按已满足算（{@link #codexSatisfied}）；改回别的难度后照实际图谱算，已升的阶不收回。
 */
public final class MatrixRank {

    /** 最高阶 */
    public static final int MAX = MatrixRanks.MAX;
    /** 七阶起有矩阵稳固 */
    public static final int STABILITY_RANK = 7;
    /** 一阶起拿着自己的灵魂石看得见 MRU */
    public static final int VISION_RANK = 1;
    /** 五阶的海纳尔辐射抗性（饰品项，和集齐古卷那一项同处） */
    public static final float RADIATION_RESISTANCE = 0.05F;
    public static final int RADIATION_RANK = 5;
    /** 手册迁移补阶下限的动作名前缀：essentialcraft5:matrix_rank_floor_&lt;n&gt;（{@link MatrixSystems#init} 登记 1~9） */
    public static final String FLOOR_ACTION = "matrix_rank_floor_";
    /** 每阶的手册步骤进度：essentialcraft5:guide/matrix/rank_&lt;n&gt;（由手册那边建；没建时 {@link #grantRankSteps} 跳过） */
    public static final String RANK_STEP = "guide/matrix/rank_";

    /** GameTest 用的覆盖值（null = 按配置） */
    @Nullable
    private static Boolean testEnabled;
    @Nullable
    private static Integer testHoannaGate;
    @Nullable
    private static Integer testHolopadGate;
    @Nullable
    private static Integer testCalamityGate;
    @Nullable
    private static Boolean testPeaceful;
    @Nullable
    private static Boolean testBlockAllEntries;

    private MatrixRank() {
    }

    // ------------------------------------------------------------------ 门

    /** 三道门 */
    public enum Gate {
        HOANNA_PORTAL("hoanna"),
        HOLOPAD("holopad"),
        CALAMITY_BEACON("calamity_beacon");

        private final String key;

        Gate(String key) {
            this.key = key;
        }

        /** 过这道门要几阶（0 = 不设门） */
        public int required() {
            return switch (this) {
                case HOANNA_PORTAL -> testHoannaGate != null ? testHoannaGate : MatrixConfig.gateHoannaPortal();
                case HOLOPAD -> testHolopadGate != null ? testHolopadGate : MatrixConfig.gateHolopad();
                case CALAMITY_BEACON -> testCalamityGate != null ? testCalamityGate : MatrixConfig.gateCalamityBeacon();
            };
        }

        /** 被挡下时那句话的翻译键 message.essentialcraft5.matrix_rank.gate.&lt;key&gt; */
        public String messageKey() {
            return "message." + EssentialCraft5.MODID + ".matrix_rank.gate." + key;
        }
    }

    /** 矩阵阶开着没有（{@code general.matrix_rank.enabled}；测试覆盖值优先） */
    public static boolean enabled() {
        return testEnabled != null ? testEnabled : MatrixConfig.enabled();
    }

    /**
     * 这名玩家能不能过这道门：矩阵阶关着、门填 0、创造 / 旁观模式都放行；否则看本人的阶。
     * 假玩家照阶算（它们是 0 阶，过不去）。O(1)。
     */
    public static boolean mayPass(@Nullable Player player, Gate gate) {
        if (player == null || !enabled()) {
            return true;
        }
        int required = gate.required();
        if (required <= 0 || player.isCreative() || player.isSpectator()) {
            return true;
        }
        return MatrixRanks.get(player) >= required;
    }

    /** 从海纳尔以外进海纳尔：所有入口统一调它（出海纳尔、在海纳尔修门、换门不用调） */
    public static boolean mayEnterHoanna(@Nullable Player player) {
        return mayPass(player, Gate.HOANNA_PORTAL);
    }

    /** 别的模组、指令进海纳尔的传送也挡不挡（{@code gates.block_all_entries}；测试覆盖值优先） */
    public static boolean blockAllEntries() {
        return testBlockAllEntries != null ? testBlockAllEntries : MatrixConfig.gateBlockAllEntries();
    }

    /**
     * 兜底的那道门：这次跨维度传送该不该挡下。只挡真玩家从海纳尔以外去海纳尔、{@link #blockAllEntries} 开着、{@link #mayEnterHoanna} 不放行的；
     * 出海纳尔、在海纳尔里换位置、非玩家都不管。O(1)。
     */
    public static boolean blocksTravel(Entity entity, ResourceKey<Level> destination) {
        if (!(entity instanceof ServerPlayer player) || !ECProtection.isRealPlayer(player) || !ECHoanna.isHoanna(destination)
                || ECHoanna.isHoanna(player.level()) || !blockAllEntries()) {
            return false;
        }
        return !mayEnterHoanna(player);
    }

    /** 在全息投影台召唤全息投影 */
    public static boolean mayUseHolopad(@Nullable Player player) {
        return mayPass(player, Gate.HOLOPAD);
    }

    /** 在天灾信标召唤（13e 的信标调） */
    public static boolean mayUseCalamityBeacon(@Nullable Player player) {
        return mayPass(player, Gate.CALAMITY_BEACON);
    }

    /** 被门挡下时的那句话：要几阶、现在几阶、去手册哪里看（红字，给动作栏用） */
    public static MutableComponent gateMessage(Player player, Gate gate) {
        return Component.translatable(gate.messageKey(), rankName(gate.required()), rankName(MatrixRanks.get(player)))
                .withStyle(ChatFormatting.RED);
    }

    // ------------------------------------------------------------------ 阶

    /** 本人的阶（{@link MatrixRanks#get}） */
    public static int get(@Nullable Player player) {
        return MatrixRanks.get(player);
    }

    /** 至少 n 阶（回报用：服主关掉矩阵阶时已有的阶照给回报，所以不看开关） */
    public static boolean atLeast(@Nullable Player player, int rank) {
        return MatrixRanks.get(player) >= rank;
    }

    /** 「五阶」「Rank 5」：matrix_rank.essentialcraft5.rank.&lt;n&gt; */
    public static MutableComponent rankName(int rank) {
        int clamped = Math.max(0, Math.min(MAX, rank));
        return Component.translatable("matrix_rank." + EssentialCraft5.MODID + ".rank." + clamped);
    }

    /** 各阶的名字（觉察、汲取……）：matrix_rank.essentialcraft5.title.&lt;n&gt;（1~9） */
    public static MutableComponent title(int rank) {
        int clamped = Math.max(1, Math.min(MAX, rank));
        return Component.translatable("matrix_rank." + EssentialCraft5.MODID + ".title." + clamped);
    }

    /**
     * 设阶（指令、试炼成功用）：夹到 0~9，标记同步。不发进度、不发标题，那些由调用方做。
     *
     * @return 设之前的阶（没有玩家数据返回 −1）
     */
    public static int set(Player player, int rank) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            return -1;
        }
        int before = data.getMatrixRank();
        data.setMatrixRank(rank);
        return before;
    }

    /**
     * 补阶下限（手册迁移 v3 的动作 {@code essentialcraft5:matrix_rank_floor_<n>}，batch13.md §2.6）：低于 n 阶的升到 n 阶，已经更高的不动。
     * 只动阶，不碰图谱（图谱不补）。
     */
    public static void raiseTo(Player player, int rank) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data != null && data.getMatrixRank() < rank) {
            data.setMatrixRank(rank);
        }
    }

    /**
     * 把手册里一~n 阶的步骤进度补上（指令设阶时用，免得手册首页还指着已经过了的阶）。进度文件不存在（手册那边还没建）就跳过。
     *
     * @return 补上了几个
     */
    public static int grantRankSteps(ServerPlayer player, int upTo) {
        int granted = 0;
        for (int rank = 1; rank <= Math.min(MAX, upTo); rank++) {
            ResourceLocation id = EssentialCraft5.id(RANK_STEP + rank);
            Advancement advancement = player.server.getAdvancements().getAdvancement(id);
            if (advancement == null) {
                continue;
            }
            AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
            if (progress.isDone()) {
                continue;
            }
            for (String criterion : progress.getRemainingCriteria()) {
                player.getAdvancements().award(advancement, criterion);
            }
            granted++;
        }
        return granted;
    }

    // ------------------------------------------------------------------ 耐受与稳固

    /**
     * 本批新加的矩阵损伤来源（试炼成功、虹吸、13c 起新怪的命中、矩阵之影）按阶打折后的量：
     * ÷ (1 + {@code tolerance_per_rank} × 阶)，七阶起再 × {@code stability.matrix_damage_multiplier}，四舍五入。
     * 零阶原样返回（一点不变）。以前就有的来源不走这里（D34 A）。由 {@code CorruptionEffects.addMatrixDamage(data, amount, Source.BATCH13)} 调。
     */
    public static long scaleNewDamage(int rank, long amount) {
        if (amount <= 0L || rank <= 0) {
            return amount;
        }
        double scaled = amount / (1D + MatrixConfig.tolerancePerRank() * Math.min(rank, MAX));
        if (rank >= STABILITY_RANK) {
            scaled *= MatrixConfig.stabilityMatrixDamageMultiplier();
        }
        return Math.max(0L, Math.round(scaled));
    }

    // ------------------------------------------------------------------ 条件核对

    /**
     * 一条条件的核对结果（状态包、淬炼台界面用）。
     *
     * @param kind  {@code codex}（图谱某组，key 是组名）、{@code include}（必须杀过的种类，key 是实体 id）、{@code alignment}（属性不是中性）
     * @param key   组名或实体 id
     * @param have  现在有几（include / alignment 是 0 或 1）
     * @param need  要几
     * @param ok    满足没有（和平难度下图谱类的按满足算）
     */
    public record Condition(String kind, String key, int have, int need, boolean ok) {
    }

    /** 和平难度（图谱条件按满足算）；测试覆盖值优先 */
    public static boolean peaceful(@Nullable Level level) {
        if (testPeaceful != null) {
            return testPeaceful;
        }
        return level != null && level.getDifficulty() == Difficulty.PEACEFUL;
    }

    /**
     * 这一阶的图谱条件和属性条件逐条核对（站点、祭品、能量由淬炼台核对）。
     *
     * @param peaceful 和平难度：图谱类条件按满足算（have 照实际写）
     */
    public static List<Condition> conditions(IPlayerData data, MatrixRankData.Entry entry, boolean peaceful) {
        List<Condition> out = new ArrayList<>();
        MatrixCodex.Counts counts = MatrixCodex.counts(data);
        for (MatrixCodex.Group group : MatrixCodex.Group.values()) {
            int need = entry.codex().required(group);
            if (need > 0) {
                int have = counts.get(group);
                out.add(new Condition("codex", group.key(), have, need, peaceful || have >= need));
            }
        }
        for (ResourceLocation id : entry.codex().include()) {
            boolean has = data.getMatrixCodex().contains(id.toString());
            out.add(new Condition("include", id.toString(), has ? 1 : 0, 1, peaceful || has));
        }
        if (entry.requiresAlignment()) {
            boolean aligned = data.getMatrixType() != 0;
            out.add(new Condition("alignment", "not_neutral", aligned ? 1 : 0, 1, aligned));
        }
        return out;
    }

    /** 图谱和属性条件都满足（站点、祭品、能量另判） */
    public static boolean codexSatisfied(IPlayerData data, MatrixRankData.Entry entry, boolean peaceful) {
        for (Condition condition : conditions(data, entry, peaceful)) {
            if (!condition.ok()) {
                return false;
            }
        }
        return true;
    }

    /** 同 {@link #codexSatisfied(IPlayerData, MatrixRankData.Entry, boolean)}，难度按玩家所在的世界算 */
    public static boolean codexSatisfied(Player player, MatrixRankData.Entry entry) {
        IPlayerData data = PlayerDataEvents.get(player);
        return data != null && codexSatisfied(data, entry, peaceful(player.level()));
    }

    /** 能不能当作真玩家处理（矩阵阶的东西都只认真玩家） */
    public static boolean isRealPlayer(@Nullable Player player) {
        return player instanceof ServerPlayer && !(player instanceof FakePlayer);
    }

    // ------------------------------------------------------------------ 测试

    /**
     * GameTest 用：临时覆盖开关和三道门（null 恢复按配置）。只许在同一段同步代码里设、测，测完调 {@link #clearTestOverrides}
     * （测试不能用 ConfigValue.set 改真配置，见项目说明「坑」）。
     */
    public static void setTestOverrides(@Nullable Boolean enabled, @Nullable Integer hoannaGate, @Nullable Integer holopadGate, @Nullable Integer calamityGate) {
        testEnabled = enabled;
        testHoannaGate = hoannaGate;
        testHolopadGate = holopadGate;
        testCalamityGate = calamityGate;
    }

    /** GameTest 用：临时把难度当成和平（或不是和平；null 恢复按世界算） */
    public static void setTestPeaceful(@Nullable Boolean peaceful) {
        testPeaceful = peaceful;
    }

    /** GameTest 用：临时覆盖 {@code gates.block_all_entries}（null 恢复按配置；{@link #clearTestOverrides} 也会清） */
    public static void setTestBlockAllEntries(@Nullable Boolean block) {
        testBlockAllEntries = block;
    }

    public static void clearTestOverrides() {
        setTestOverrides(null, null, null, null);
        testPeaceful = null;
        testBlockAllEntries = null;
    }

    /** 动作名（带命名空间）：essentialcraft5:matrix_rank_floor_&lt;n&gt; */
    public static String floorAction(int rank) {
        return EssentialCraft5.id(FLOOR_ACTION + rank).toString().toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------ 事件

    /**
     * Forge 总线（类名带模块前缀，见 tools/check_event_handlers.py）：海纳尔之门的兜底（batch13.md §2.5「从海纳尔以外进海纳尔的所有入口」）。
     * 纯 Forge 下 {@code ServerPlayer.teleportTo} 换维度、{@code changeDimension} 都发 {@code EntityTravelToDimensionEvent}，
     * 所以 /tp、/tpa、传送石碑这类别的模组的传送都会经过这里。只挡进、不挡出，不会把人困在海纳尔。
     */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
    public static final class MatrixRankGateEvents {

        private MatrixRankGateEvents() {
        }

        @SubscribeEvent
        public static void matrixRankTravelGate(EntityTravelToDimensionEvent event) {
            Entity entity = event.getEntity();
            if (entity.level().isClientSide || !blocksTravel(entity, event.getDimension())) {
                return;
            }
            event.setCanceled(true);
            if (entity instanceof ServerPlayer player) {
                player.displayClientMessage(gateMessage(player, Gate.HOANNA_PORTAL), true);
            }
        }
    }
}
