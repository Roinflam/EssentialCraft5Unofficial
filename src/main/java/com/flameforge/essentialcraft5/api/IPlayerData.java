package com.flameforge.essentialcraft5.api;

import com.flameforge.essentialcraft5.registry.ECPentacle;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;

import java.util.List;
import java.util.Set;

/**
 * 挂在玩家身上的 EC 数据，通过 {@link ECCapabilities#PLAYER_DATA} 取。
 * <p>
 * 原作存在静态的 {@code ECUtils.PLAYER_DATA_MAP} 里、另写 {@code <uuid>.ecdat} 文件，单人模式下客户端和内置服务端共用一份；
 * 这里改成玩家 capability，随玩家 NBT 存盘，死亡/换维度时整份复制（原作按 UUID 存，死亡不丢）。
 * 服务端改动后调 {@link #markDirty()}，下一个玩家 tick 同步给本人（setter 和腐化效果的增删都自带 markDirty）。
 * <p>
 * 腐化效果（第三批）：玩家身上的效果按字符串 id 存（{@link CorruptionEffects} 的 id，如 {@code body_0}、{@code matrix_13}），不存原作的序号，
 * 以后增删效果不会错位；读档时认不出的 id 原样保留（{@link CorruptionEffects#byId} 返回 null，用的地方跳过）。
 * 同一种效果只会有一个（原作 canMultiply 全是 false）。
 * <pre>{@code
 * IPlayerData data = PlayerDataEvents.get(player);   // 或 player.getCapability(ECCapabilities.PLAYER_DATA)
 * if (data != null && data.addCorruptionEffect("body_0")) { ... }   // 已经有了返回 false
 * for (String id : data.getCorruptionEffects()) { CorruptionEffects.Effect effect = CorruptionEffects.byId(id); ... }
 * }</pre>
 * 存档格式：NBT 里 {@code corruption_effects}（字符串列表）、{@code data_version}（见 player/PlayerData 的迁移说明）。
 * <p>
 * 第十三批 13a 的键（矩阵阶、图谱、容量、试炼、契约之誉、同调预算、过载冷却）见后面那几段；两份图谱不随普通同步包发，
 * 同步包用 {@link #serializeForSync}，客户端收包用 {@link #deserializeSync}。
 * 第十三批 13c 加了击杀台账（只在服务端，同样不随普通同步包发）和首领首杀记录。
 */
public interface IPlayerData extends INBTSerializable<CompoundTag> {

    /** 矩阵伤害（原作字段名 damage / overhaulDamage），累积到一定量后抽腐化效果。 */
    int getMatrixDamage();

    void setMatrixDamage(int value);

    /** Hoanna 维度的辐射。 */
    int getRadiation();

    void setRadiation(int value);

    /** 风之关系点数：攒满 3500 才能用风之石板开 Hoanna 传送门。 */
    int getWindPoints();

    void setWindPoints(int value);

    /** 是否已签订风之契约。 */
    boolean isWindbound();

    void setWindbound(boolean value);

    /** UBMRU：击杀生物获得，饰品和矩阵换能器消耗。 */
    int getUBMRU();

    void setUBMRU(int value);

    /**
     * 改 UBMRU；{@code sync} 为假是「安静地改」：只改值、不标记同步，给矩阵容量每秒的自然换入用（batch13.md §3.1.5，
     * 容量跨过整百、换满、碰到保留线停下时由 {@code matrix/MatrixPool} 自己标一次）。别的地方一律用单参版本。
     */
    default void setUBMRU(int value, boolean sync) {
        setUBMRU(value);
    }

    /** 矩阵类型，对应原作 EnumMatrixType 的序号：0 空、1 混沌、2 冰冻、3 魔法、4 暗影。 */
    int getMatrixType();

    void setMatrixType(int value);

    // ---------------------------------------------------------------- 腐化效果（第三批）

    /** 身上的腐化效果 id，按得到的先后排；只读视图（要改用下面几个方法） */
    List<String> getCorruptionEffects();

    boolean hasCorruptionEffect(String id);

    /** 加一个效果（放到最后）。已经有了、或 id 为空时不加，返回 false */
    boolean addCorruptionEffect(String id);

    /** 去掉一个效果，身上没有时返回 false */
    boolean removeCorruptionEffect(String id);

    /** 清掉全部效果，返回清掉了几个 */
    int clearCorruptionEffects();

    // ---------------------------------------------------------------- 腐化的闸（第十批）

    /**
     * 打开过的最高档灵魂五芒星（−1 = 还没打开过成形的五芒星，0 ~ 4，上限 ECPentacle.MAX_TIER）。{@code draw_requires_cure_tier} 开着时只抽这一档治得了的效果，
     * 治不了的潜伏（{@code corruption/CorruptionGate}）。只会升不会降，存盘（键 {@code pentacle_tier}，没有这个键的旧数据是 −1）。
     * 默认实现给别的实现类兜底：当成全都解锁（照原作）。
     */
    default int getPentacleTier() {
        return ECPentacle.MAX_TIER;
    }

    default void setPentacleTier(int value) {
    }

    /** 抽中一个效果以后还要跳过几次抽取（每分钟一次，在线时才走；{@code draw_cooldown_minutes}）。存盘（键 {@code corruption_cooldown}） */
    default int getCorruptionCooldown() {
        return 0;
    }

    default void setCorruptionCooldown(int value) {
    }

    // ---------------------------------------------------------------- 风之试炼（第十批 10b）

    /** 完成过几个风之心愿（{@code player/WindWishes}；风之真名要够数）。存盘、同步（键 {@code wishes}） */
    default int getWindWishes() {
        return 0;
    }

    default void setWindWishes(int value) {
    }

    /**
     * 当前的风之心愿（副本）：{@code type}、{@code param}、{@code deadline}、{@code progress}，过期后只剩 {@code cooldown}；没有心愿时是空标签。
     * 字段的含义由 {@code player/WindWishes} 解释，这里只管存（键 {@code wish}）。
     */
    default CompoundTag getWindWish() {
        return new CompoundTag();
    }

    /**
     * 换掉当前心愿。{@code sync} 为假时只改存盘的值、不标记同步（每秒推进的进度不值得每秒发一个包，客户端只要知道给出 / 完成 / 过期）。
     */
    default void setWindWish(CompoundTag wish, boolean sync) {
    }

    /** 风之号角下次能吹的游戏刻（{@code item/WindHornItem}，存盘，键 {@code wind_horn_cooldown}） */
    default long getWindHornCooldown() {
        return 0L;
    }

    default void setWindHornCooldown(long gameTime) {
    }

    // ---------------------------------------------------------------- 矩阵后遗症

    /**
     * 矩阵后遗症还剩多少 tick（id 见 {@code corruption/Affliction}；没有时 0）。腐化效果、机器泄漏留下的状态不做成药水效果，
     * 存在这里（键 {@code afflictions}），牛奶清不掉；怎么走见 {@code corruption/Afflictions}。
     */
    default int getAfflictionTicks(String id) {
        return 0;
    }

    /** 后遗症的等级（0 = I 级）；没有时 0 */
    default int getAfflictionLevel(String id) {
        return 0;
    }

    /** 直接设成 ticks、level（ticks ≤ 0 = 去掉），有变化时标记同步 */
    default void setAffliction(String id, int ticks, int level) {
    }

    /** 身上有的后遗症 id（只读） */
    default Set<String> getAfflictionIds() {
        return Set.of();
    }

    /** 每项减 1 tick、走完的去掉（不标记同步，两边各自减）；有走完的返回 true */
    default boolean tickAfflictions() {
        return false;
    }

    /** 全部去掉，返回去掉了几项 */
    default int clearAfflictions() {
        return 0;
    }

    // ---------------------------------------------------------------- 第十三批 13a：矩阵阶（batch13.md §3.1.5、§3.1.6）

    /** 矩阵阶的上限（九阶） */
    int MAX_MATRIX_RANK = 9;

    /**
     * 矩阵阶 0 ~ {@link #MAX_MATRIX_RANK}，只靠试炼突破（键 {@code matrix_rank}，没有这个键的旧数据是零阶）。随普通同步包发。
     * 别的模块读阶用 {@link MatrixRanks#get}，门、回报的判定用矩阵框架自己的接口（服主关掉矩阵阶时门全开）。
     */
    default int getMatrixRank() {
        return 0;
    }

    /** 设阶（夹到 0 ~ 9），有变化时标记同步 */
    default void setMatrixRank(int rank) {
    }

    /**
     * 矩阵图谱：亲手杀过的敌对生物种类（实体类型 id 字符串，按记下的先后排；只读视图，键 {@code matrix_codex}）。
     * <b>不随普通同步包发</b>（{@link #serializeForSync}），改了要另发 {@code network/S2CMatrixCodexPacket}；
     * 增删都不标记同步。条目上限由矩阵框架按配置管，这里不限。
     */
    default Set<String> getMatrixCodex() {
        return Set.of();
    }

    /** 在海纳尔里杀过的种类，另存一份（键 {@code matrix_codex_hoanna}；同上，不随普通同步包发） */
    default Set<String> getMatrixCodexHoanna() {
        return Set.of();
    }

    /** 记一种进总图谱；已经有了、或 id 为空返回 false。不标记同步 */
    default boolean addMatrixCodex(String entityTypeId) {
        return false;
    }

    /** 记一种进海纳尔那份；已经有了、或 id 为空返回 false。不标记同步 */
    default boolean addMatrixCodexHoanna(String entityTypeId) {
        return false;
    }

    /** 两份图谱一起清空，返回清掉的条目数（两份相加）。不标记同步 */
    default int clearMatrixCodex() {
        return 0;
    }

    /** 矩阵容量里存着的 MRU（键 {@code matrix_pool}）。死亡清零（{@code PlayerDataEvents.clone}），换维度、下线不清 */
    default int getMatrixPool() {
        return 0;
    }

    /**
     * 改容量（夹到 ≥ 0）。{@code sync} 为假是「安静地改」：只改值、不标记同步，用在每秒的自然换入上
     * （batch13.md §3.1.5：照原样标脏会让本来没事的玩家每秒收一整包）；被消耗、死亡清零、指令改的传真。
     */
    default void setMatrixPool(int value, boolean sync) {
    }

    /** 容量自动换入的开关（潜行右键灵魂石切换；键 {@code matrix_pool_absorb}，默认开） */
    default boolean isMatrixPoolAbsorb() {
        return true;
    }

    default void setMatrixPoolAbsorb(boolean value) {
    }

    /** 试炼失败后的冷却，剩余刻数（键 {@code matrix_trial_cooldown}；在线时每刻减 1，退出重进不清） */
    default int getMatrixTrialCooldown() {
        return 0;
    }

    default void setMatrixTrialCooldown(int ticks) {
    }

    /** 试炼储备：存进来的 UBMRU，只能花在试炼上（键 {@code matrix_trial_reserve}；死亡不丢） */
    default int getMatrixTrialReserve() {
        return 0;
    }

    default void setMatrixTrialReserve(int value) {
    }

    // ---------------------------------------------------------------- 第十三批 13a：契约之誉、同调（batch13.md §3.3、§3.4）

    /** 契约之誉：和恶魔做成一笔交易 +1（键 {@code demon_standing}；死亡、换维度保留） */
    default int getDemonStanding() {
        return 0;
    }

    default void setDemonStanding(int value) {
    }

    /** 同调预算的两份 */
    enum BudgetKind {
        ARMOR("armor"),
        WEAPON("weapon");

        private final String key;

        BudgetKind(String key) {
            this.key = key;
        }

        /** 存档里的键名（{@code attunement_budget} 下的子键） */
        public String key() {
            return key;
        }
    }

    /** 同调预算（点，带小数；键 {@code attunement_budget {armor, weapon}}） */
    default float getAttunementBudget(BudgetKind kind) {
        return 0F;
    }

    /**
     * 改预算（夹到 ≥ 0）。{@code sync} 为假是「安静地改」，用在在线自然增长上：只在跨过整 {@code PlayerData.BUDGET_SYNC_STEP} 点时标记同步；
     * 被消耗、指令改的传真。上限由同调框架按配置管，这里不限。
     */
    default void setAttunementBudget(BudgetKind kind, float value, boolean sync) {
    }

    /** 宅男过载屏障的冷却，剩余刻数（键 {@code attunement_overload_cd}；在线时每刻减 1，退出重进不清） */
    default int getAttunementOverloadCooldown() {
        return 0;
    }

    default void setAttunementOverloadCooldown(int ticks) {
    }

    /**
     * 两个冷却（试炼、过载屏障）各减 1 刻，不标记同步；有走完（变成 0）的返回 true。
     * 由 {@code PlayerDataEvents} 在服务端每刻调一次，模块不用自己减。
     */
    default boolean tickCooldowns() {
        return false;
    }

    // ---------------------------------------------------------------- 第十三批 13c：击杀台账、首领首杀（batch13.md §5.4、§5.3.5）

    /**
     * 怪物击杀台账（键 {@code mob_ledger}，按人递减 {@code kill_fatigue} 用；格式由 {@code player/MobKillLedger} 定）。
     * <b>只在服务端</b>：不随普通同步包发（{@link #serializeForSync} 去掉它），客户端这边永远是空的。
     * 返回存着的那个标签本身（不复制），台账直接读写它，改了不用标记同步；没有这个键的旧数据是空标签。死亡、换维度照整份复制保留。
     */
    default CompoundTag getMobLedger() {
        return new CompoundTag();
    }

    /** 整个换掉台账（null 当空标签）。不标记同步 */
    default void setMobLedger(CompoundTag ledger) {
    }

    /**
     * 打倒过的首领种类（实体类型 id，键 {@code boss_first_kills}；13e 天灾之心「每人第一次打倒多给一份」用，batch13.md §5.3.5）。
     * 随普通同步包发；没有这个键的旧数据是空的。死亡、换维度保留。
     */
    default Set<String> getBossFirstKills() {
        return Set.of();
    }

    /** 记一种首领；第一次记上返回 true（已经有了、id 为空返回 false）。记上时标记同步 */
    default boolean addBossFirstKill(String entityTypeId) {
        return false;
    }

    // ---------------------------------------------------------------- 第十三批 13d：渊盐冷却、首领每日领奖（batch13.md §6.3、§9.4）

    /**
     * 渊盐冷却到期时的<b>主世界</b> gameTime（键 {@code abyssal_salt_cd}；0 = 没在冷却）。用 {@code server.overworld().getGameTime()} 比，
     * 别用玩家所在世界的 gameTime（混合端插件建的世界和主世界的 gameTime 不一样）。只在服务端用，不随普通同步包发；死亡、换维度保留。
     */
    default long getAbyssalSaltCooldownEnd() {
        return 0L;
    }

    /** 设渊盐冷却到期的主世界 gameTime（负数当 0）。不标记同步（随玩家存档照常存盘） */
    default void setAbyssalSaltCooldownEnd(long overworldGameTime) {
    }

    /**
     * 首领每日领奖记录（键 {@code boss_daily}；首领实体 id → 最后一次领奖的游戏日序号 {@code BossDaily.dayIndex}，旁边 {@code <键>_t} 记那一刻的主世界 gameTime；
     * 13e 渊潮领主「每人每游戏日一份」、天灾之心「每人每日一次」用，怎么比见 {@code BossDaily}）。
     * 返回存着的那个标签本身（不复制），原地改就会进存档；只在服务端用，不随普通同步包发；死亡、换维度保留。
     */
    default CompoundTag getBossDaily() {
        return new CompoundTag();
    }

    /** 整个换掉每日领奖记录（null 当空标签）。不标记同步 */
    default void setBossDaily(CompoundTag daily) {
    }

    // ---------------------------------------------------------------- 同步（第十三批 13a 拆分）

    /**
     * 普通同步包发的那份：整份数据去掉两份图谱（见 {@link #getMatrixCodex}）和只在服务端的击杀台账（{@link #getMobLedger}）、
     * 渊盐冷却（{@link #getAbyssalSaltCooldownEnd}）、首领每日领奖（{@link #getBossDaily}）
     */
    default CompoundTag serializeForSync() {
        return serializeNBT();
    }

    /** 客户端收普通同步包：包里没有的图谱保留本地那份 */
    default void deserializeSync(CompoundTag tag) {
        deserializeNBT(tag);
    }

    boolean isDirty();

    void markDirty();

    void clearDirty();
}
