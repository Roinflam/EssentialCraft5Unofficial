package com.flameforge.essentialcraft5.player;

import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.registry.ECPentacle;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link IPlayerData} 的实现。对应原作 PlayerGenericData（字段一一对应，键名改成小写下划线）。
 * <p>
 * 腐化效果：原作存成 effectsSize + effectTag_i（每个带类型、成本、序号的整份副本）；这里只存 id 列表 {@code corruption_effects}，
 * 类别、成本等从 api/CorruptionEffects 的表里查。
 *
 * <h2>存档版本与 D14 迁移</h2>
 * NBT 里的 {@code data_version}：第一、二批存的数据没有这个键（读出来是 0），第三批起写 {@link #DATA_VERSION}。
 * 读到版本 &lt; 1 的数据时把矩阵损伤清零（D14：第一、二批没有腐化效果，聚集体攒下的损伤一直没有后果；原样保留的话，
 * 腐化效果上线后常驻基地的玩家每分钟必中一个效果，直到损伤扣完）。读档就是登录的时候，清掉多少在登录时写进日志（{@link PlayerDataEvents}）。
 * 死亡复制、同步包都带着版本号，所以只会清这一次。
 * <p>
 * 第十批加了 {@code pentacle_tier}（打开过的最高档五芒星）和 {@code corruption_cooldown}（抽中后的冷却），没有这两个键的旧数据按 −1 和 0 读，
 * 不用升版本号：旧存档里的人身上已有的效果在打开够档的五芒星之前潜伏（{@code corruption/CorruptionGate}），不会因此变得更糟。
 * <p>
 * 第十批 10b 加了风之试炼的 {@code wishes}（完成过几个心愿）、{@code wish}（当前心愿，{@code player/WindWishes} 解释）、
 * {@code wind_horn_cooldown}（风之号角的冷却到哪一刻）。同样不升版本号：读到一份有内容、却没有 {@code wishes} 键的旧数据时记一个
 * 「老存档」标记（{@link #takeLegacyWishes}），登录时由 {@code WindWishes.migrateLegacy} 处理（好感已到门槛的老玩家心愿数记满）；
 * 死亡复制、同步包都带着这个键，所以只处理一次。
 * <p>
 * 矩阵后遗症 {@code afflictions}（id → [剩余 tick, 等级]，{@code corruption/Afflictions}）：同样不升版本号，没有这个键就是身上没有。
 * <p>
 * 第十三批 13a（batch13.md §3.1.5、§3.1.6、§3.3.3、§3.4）加了矩阵阶、两份图谱、矩阵容量与开关、试炼冷却与储备、契约之誉、
 * 两份同调预算、过载屏障冷却。同样不升版本号：缺键一律读成默认值（零阶、空图谱、容量 0、容量开关开、没有冷却、声望 0、预算 0），
 * 旧数据读出来就是现状。两份图谱不随普通同步包发（{@link #serializeForSync}），客户端收到普通同步包时保留自己那份
 * （{@link #deserializeSync}）；容量、预算的自然增长用安静写法（{@link #setMatrixPool}、{@link #setAttunementBudget} 的 {@code sync} 为假，
 * 容量换入时扣的 UBMRU 同样走 {@link #setUBMRU(int, boolean)}），不触发整包同步。两个冷却存剩余刻数，在线时由 {@link PlayerDataEvents} 每刻减（不用 gameTime：各维度的 gameTime 不一定一样）。
 * <p>
 * 第十三批 13c（batch13.md §5.4、§5.3.5）加了击杀台账 {@code mob_ledger}（{@code player/MobKillLedger} 读写的一个标签，只在服务端）和
 * 首领首杀 {@code boss_first_kills}（实体类型 id 列表）。同样不升版本号：缺键读成空的，就是「没杀过、没打倒过」，旧数据读出来照旧；
 * 两个键都是空的时候不写进存档。台账不随普通同步包发（{@link #serializeForSync} 去掉），首杀记录随包发。
 * <p>
 * 第十三批 13d（batch13.md §6.3、§9.4）加了渊盐冷却 {@code abyssal_salt_cd}（到期时的<b>主世界</b> gameTime，0 = 没在冷却）和
 * 首领每日领奖 {@code boss_daily}（首领实体 id → 最后一次领奖的游戏日序号，13e 渊潮领主用，见 {@code entity/boss/BossDaily}）。
 * 同样不升版本号：缺键读成 0 / 空标签；是默认值时不写进存档；两个都只在服务端用，不随普通同步包发；死亡复制照整份保留。
 * 冷却按主世界 gameTime 记、不存剩余刻数：渊盐的冷却要跨下线算（离线也在走），而混合端插件建的世界和主世界的 gameTime 可以不一样，
 * 按所在世界记的话换个世界就会算错。
 */
public class PlayerData implements IPlayerData {

    /** 当前存档数据版本（见类注释）。以后再要迁移就加 1，在 {@link #deserializeNBT} 里按旧版本处理 */
    public static final int DATA_VERSION = 1;

    private static final String TAG_DATA_VERSION = "data_version";
    private static final String TAG_CORRUPTION_EFFECTS = "corruption_effects";
    private static final String TAG_PENTACLE_TIER = "pentacle_tier";
    private static final String TAG_CORRUPTION_COOLDOWN = "corruption_cooldown";
    private static final String TAG_WISHES = "wishes";
    private static final String TAG_WISH = "wish";
    private static final String TAG_WIND_HORN_COOLDOWN = "wind_horn_cooldown";
    private static final String TAG_AFFLICTIONS = "afflictions";
    // 第十三批 13a（batch13.md §3.1.5）
    private static final String TAG_MATRIX_RANK = "matrix_rank";
    private static final String TAG_MATRIX_CODEX = "matrix_codex";
    private static final String TAG_MATRIX_CODEX_HOANNA = "matrix_codex_hoanna";
    private static final String TAG_MATRIX_POOL = "matrix_pool";
    private static final String TAG_MATRIX_POOL_ABSORB = "matrix_pool_absorb";
    private static final String TAG_MATRIX_TRIAL_COOLDOWN = "matrix_trial_cooldown";
    private static final String TAG_MATRIX_TRIAL_RESERVE = "matrix_trial_reserve";
    private static final String TAG_DEMON_STANDING = "demon_standing";
    private static final String TAG_ATTUNEMENT_BUDGET = "attunement_budget";
    private static final String TAG_ATTUNEMENT_OVERLOAD_CD = "attunement_overload_cd";
    // 第十三批 13c（batch13.md §5.4、§5.3.5）
    private static final String TAG_MOB_LEDGER = "mob_ledger";
    private static final String TAG_BOSS_FIRST_KILLS = "boss_first_kills";
    // 第十三批 13d（batch13.md §6.3、§9.4）
    private static final String TAG_ABYSSAL_SALT_CD = "abyssal_salt_cd";
    private static final String TAG_BOSS_DAILY = "boss_daily";

    /**
     * 同调预算安静地涨时，每跨过这么多点的整数倍才标一次同步（batch13.md §3.1.5「跨过整 10 点」）：
     * 每秒 +0.1 的话约 100 秒一次。客户端在两次同步之间按在线时长自己外推显示。
     */
    public static final float BUDGET_SYNC_STEP = 10F;

    private int matrixDamage;
    private int radiation;
    private int windPoints;
    private boolean windbound;
    private int ubmru;
    private int matrixType;
    private final List<String> corruptionEffects = new ArrayList<>();
    private final List<String> corruptionEffectsView = Collections.unmodifiableList(corruptionEffects);
    /** 打开过的最高档五芒星，−1 = 没有（第十批，见 {@link IPlayerData#getPentacleTier()}） */
    private int pentacleTier = -1;
    /** 抽中以后还要跳过的抽取次数 */
    private int corruptionCooldown;
    /** 完成过几个风之心愿（第十批 10b） */
    private int windWishes;
    /** 当前的风之心愿；没有时是空标签 */
    private CompoundTag windWish = new CompoundTag();
    /** 风之号角下次能吹的游戏刻 */
    private long windHornCooldown;
    /** 矩阵后遗症：id → {剩余 tick, 等级}，按得到的先后排 */
    private final Map<String, int[]> afflictions = new LinkedHashMap<>();
    /** 矩阵阶 0 ~ {@link IPlayerData#MAX_MATRIX_RANK}（第十三批 13a） */
    private int matrixRank;
    /** 矩阵图谱：亲手杀过的敌对生物种类（实体类型 id），按记下的先后排 */
    private final Set<String> matrixCodex = new LinkedHashSet<>();
    private final Set<String> matrixCodexView = Collections.unmodifiableSet(matrixCodex);
    /** 在海纳尔里杀过的种类，另存一份 */
    private final Set<String> matrixCodexHoanna = new LinkedHashSet<>();
    private final Set<String> matrixCodexHoannaView = Collections.unmodifiableSet(matrixCodexHoanna);
    /** 矩阵容量里存着的 MRU */
    private int matrixPool;
    /** 矩阵容量自动换入开关（默认开） */
    private boolean matrixPoolAbsorb = true;
    /** 试炼失败后的冷却，剩余刻数 */
    private int matrixTrialCooldown;
    /** 试炼储备：存进来的 UBMRU，只能花在试炼上 */
    private int matrixTrialReserve;
    /** 契约之誉 */
    private int demonStanding;
    /** 同调预算：盔甲、武器两份（点，带小数） */
    private float attunementBudgetArmor;
    private float attunementBudgetWeapon;
    /** 宅男过载屏障的冷却，剩余刻数 */
    private int attunementOverloadCooldown;
    /** 击杀台账（第十三批 13c；只在服务端，格式归 player/MobKillLedger） */
    private CompoundTag mobLedger = new CompoundTag();
    /** 打倒过的首领种类，按记下的先后排 */
    private final Set<String> bossFirstKills = new LinkedHashSet<>();
    private final Set<String> bossFirstKillsView = Collections.unmodifiableSet(bossFirstKills);
    /** 渊盐冷却到期时的主世界 gameTime（第十三批 13d；0 = 没在冷却） */
    private long abyssalSaltCooldownEnd;
    /** 首领 id → 最后一次领奖的游戏日序号（第十三批 13d，13e 用） */
    private CompoundTag bossDaily = new CompoundTag();
    /** 上一次读档读到的是没有 wishes 键的旧数据（不存盘，登录时迁移用，取一次就清掉） */
    private boolean legacyWishes;
    private boolean dirty;
    /** 上一次读档时 D14 迁移清掉的矩阵损伤（不存盘，登录时写日志用） */
    private int migratedMatrixDamage;

    @Override
    public int getMatrixDamage() {
        return matrixDamage;
    }

    @Override
    public void setMatrixDamage(int value) {
        if (value != matrixDamage) {
            matrixDamage = value;
            markDirty();
        }
    }

    @Override
    public int getRadiation() {
        return radiation;
    }

    @Override
    public void setRadiation(int value) {
        if (value != radiation) {
            radiation = value;
            markDirty();
        }
    }

    @Override
    public int getWindPoints() {
        return windPoints;
    }

    @Override
    public void setWindPoints(int value) {
        if (value != windPoints) {
            windPoints = value;
            markDirty();
        }
    }

    @Override
    public boolean isWindbound() {
        return windbound;
    }

    @Override
    public void setWindbound(boolean value) {
        if (value != windbound) {
            windbound = value;
            markDirty();
        }
    }

    @Override
    public int getUBMRU() {
        return ubmru;
    }

    @Override
    public void setUBMRU(int value) {
        setUBMRU(value, true);
    }

    @Override
    public void setUBMRU(int value, boolean sync) {
        if (value != ubmru) {
            ubmru = value;
            if (sync) {
                markDirty();
            }
        }
    }

    @Override
    public int getMatrixType() {
        return matrixType;
    }

    @Override
    public void setMatrixType(int value) {
        if (value != matrixType) {
            matrixType = value;
            markDirty();
        }
    }

    // ---------------------------------------------------------------- 腐化效果

    @Override
    public List<String> getCorruptionEffects() {
        return corruptionEffectsView;
    }

    @Override
    public boolean hasCorruptionEffect(String id) {
        return corruptionEffects.contains(id);
    }

    @Override
    public boolean addCorruptionEffect(String id) {
        if (id == null || id.isEmpty() || corruptionEffects.contains(id)) {
            return false;
        }
        corruptionEffects.add(id);
        markDirty();
        return true;
    }

    @Override
    public boolean removeCorruptionEffect(String id) {
        if (corruptionEffects.remove(id)) {
            markDirty();
            return true;
        }
        return false;
    }

    @Override
    public int clearCorruptionEffects() {
        int count = corruptionEffects.size();
        if (count > 0) {
            corruptionEffects.clear();
            markDirty();
        }
        return count;
    }

    @Override
    public int getPentacleTier() {
        return pentacleTier;
    }

    @Override
    public void setPentacleTier(int value) {
        int clamped = Math.max(-1, Math.min(ECPentacle.MAX_TIER, value));
        if (clamped != pentacleTier) {
            pentacleTier = clamped;
            markDirty();
        }
    }

    @Override
    public int getCorruptionCooldown() {
        return corruptionCooldown;
    }

    @Override
    public void setCorruptionCooldown(int value) {
        int clamped = Math.max(0, value);
        if (clamped != corruptionCooldown) {
            corruptionCooldown = clamped;
            markDirty();
        }
    }

    @Override
    public int getWindWishes() {
        return windWishes;
    }

    @Override
    public void setWindWishes(int value) {
        int clamped = Math.max(0, value);
        if (clamped != windWishes) {
            windWishes = clamped;
            markDirty();
        }
    }

    @Override
    public CompoundTag getWindWish() {
        return windWish.copy();
    }

    @Override
    public void setWindWish(CompoundTag wish, boolean sync) {
        CompoundTag next = wish == null ? new CompoundTag() : wish.copy();
        if (!next.equals(windWish)) {
            windWish = next;
            if (sync) {
                markDirty();
            }
        }
    }

    @Override
    public long getWindHornCooldown() {
        return windHornCooldown;
    }

    @Override
    public void setWindHornCooldown(long gameTime) {
        long clamped = Math.max(0L, gameTime);
        if (clamped != windHornCooldown) {
            windHornCooldown = clamped;
            markDirty();
        }
    }

    // ---------------------------------------------------------------- 矩阵后遗症

    @Override
    public int getAfflictionTicks(String id) {
        int[] entry = afflictions.get(id);
        return entry == null ? 0 : entry[0];
    }

    @Override
    public int getAfflictionLevel(String id) {
        int[] entry = afflictions.get(id);
        return entry == null ? 0 : entry[1];
    }

    @Override
    public void setAffliction(String id, int ticks, int level) {
        if (id == null || id.isEmpty()) {
            return;
        }
        if (ticks <= 0) {
            if (afflictions.remove(id) != null) {
                markDirty();
            }
            return;
        }
        int[] entry = afflictions.get(id);
        int clamped = Math.max(0, level);
        if (entry == null) {
            afflictions.put(id, new int[]{ticks, clamped});
            markDirty();
        } else if (entry[0] != ticks || entry[1] != clamped) {
            entry[0] = ticks;
            entry[1] = clamped;
            markDirty();
        }
    }

    @Override
    public Set<String> getAfflictionIds() {
        return Collections.unmodifiableSet(afflictions.keySet());
    }

    @Override
    public boolean tickAfflictions() {
        boolean expired = false;
        for (Iterator<int[]> it = afflictions.values().iterator(); it.hasNext(); ) {
            int[] entry = it.next();
            if (--entry[0] <= 0) {
                it.remove();
                expired = true;
            }
        }
        return expired;
    }

    @Override
    public int clearAfflictions() {
        int count = afflictions.size();
        if (count > 0) {
            afflictions.clear();
            markDirty();
        }
        return count;
    }

    // ---------------------------------------------------------------- 第十三批 13a：矩阵阶、图谱、容量、试炼

    @Override
    public int getMatrixRank() {
        return matrixRank;
    }

    @Override
    public void setMatrixRank(int rank) {
        int clamped = Math.max(0, Math.min(MAX_MATRIX_RANK, rank));
        if (clamped != matrixRank) {
            matrixRank = clamped;
            markDirty();
        }
    }

    @Override
    public Set<String> getMatrixCodex() {
        return matrixCodexView;
    }

    @Override
    public Set<String> getMatrixCodexHoanna() {
        return matrixCodexHoannaView;
    }

    @Override
    public boolean addMatrixCodex(String entityTypeId) {
        return entityTypeId != null && !entityTypeId.isEmpty() && matrixCodex.add(entityTypeId);
    }

    @Override
    public boolean addMatrixCodexHoanna(String entityTypeId) {
        return entityTypeId != null && !entityTypeId.isEmpty() && matrixCodexHoanna.add(entityTypeId);
    }

    @Override
    public int clearMatrixCodex() {
        int count = matrixCodex.size() + matrixCodexHoanna.size();
        matrixCodex.clear();
        matrixCodexHoanna.clear();
        return count;
    }

    @Override
    public int getMatrixPool() {
        return matrixPool;
    }

    @Override
    public void setMatrixPool(int value, boolean sync) {
        int clamped = Math.max(0, value);
        if (clamped != matrixPool) {
            matrixPool = clamped;
            if (sync) {
                markDirty();
            }
        }
    }

    @Override
    public boolean isMatrixPoolAbsorb() {
        return matrixPoolAbsorb;
    }

    @Override
    public void setMatrixPoolAbsorb(boolean value) {
        if (value != matrixPoolAbsorb) {
            matrixPoolAbsorb = value;
            markDirty();
        }
    }

    @Override
    public int getMatrixTrialCooldown() {
        return matrixTrialCooldown;
    }

    @Override
    public void setMatrixTrialCooldown(int ticks) {
        int clamped = Math.max(0, ticks);
        if (clamped != matrixTrialCooldown) {
            matrixTrialCooldown = clamped;
            markDirty();
        }
    }

    @Override
    public int getMatrixTrialReserve() {
        return matrixTrialReserve;
    }

    @Override
    public void setMatrixTrialReserve(int value) {
        int clamped = Math.max(0, value);
        if (clamped != matrixTrialReserve) {
            matrixTrialReserve = clamped;
            markDirty();
        }
    }

    // ---------------------------------------------------------------- 第十三批 13a：契约之誉、同调

    @Override
    public int getDemonStanding() {
        return demonStanding;
    }

    @Override
    public void setDemonStanding(int value) {
        int clamped = Math.max(0, value);
        if (clamped != demonStanding) {
            demonStanding = clamped;
            markDirty();
        }
    }

    @Override
    public float getAttunementBudget(BudgetKind kind) {
        return kind == BudgetKind.WEAPON ? attunementBudgetWeapon : attunementBudgetArmor;
    }

    @Override
    public void setAttunementBudget(BudgetKind kind, float value, boolean sync) {
        float clamped = finiteNonNegative(value);
        float old = getAttunementBudget(kind);
        if (clamped == old) {
            return;
        }
        if (kind == BudgetKind.WEAPON) {
            attunementBudgetWeapon = clamped;
        } else {
            attunementBudgetArmor = clamped;
        }
        // 安静地涨：只在跨过整 BUDGET_SYNC_STEP 点时标一次，其余时候客户端自己外推
        if (sync || Math.floor(clamped / BUDGET_SYNC_STEP) != Math.floor(old / BUDGET_SYNC_STEP)) {
            markDirty();
        }
    }

    @Override
    public int getAttunementOverloadCooldown() {
        return attunementOverloadCooldown;
    }

    @Override
    public void setAttunementOverloadCooldown(int ticks) {
        int clamped = Math.max(0, ticks);
        if (clamped != attunementOverloadCooldown) {
            attunementOverloadCooldown = clamped;
            markDirty();
        }
    }

    @Override
    public boolean tickCooldowns() {
        boolean expired = false;
        if (matrixTrialCooldown > 0 && --matrixTrialCooldown == 0) {
            expired = true;
        }
        if (attunementOverloadCooldown > 0 && --attunementOverloadCooldown == 0) {
            expired = true;
        }
        return expired;
    }

    // ---------------------------------------------------------------- 第十三批 13c：击杀台账、首领首杀

    @Override
    public CompoundTag getMobLedger() {
        return mobLedger;
    }

    @Override
    public void setMobLedger(CompoundTag ledger) {
        mobLedger = ledger == null ? new CompoundTag() : ledger;
    }

    @Override
    public Set<String> getBossFirstKills() {
        return bossFirstKillsView;
    }

    @Override
    public boolean addBossFirstKill(String entityTypeId) {
        if (entityTypeId == null || entityTypeId.isEmpty() || !bossFirstKills.add(entityTypeId)) {
            return false;
        }
        markDirty();
        return true;
    }

    // ---------------------------------------------------------------- 第十三批 13d：渊盐冷却、首领每日领奖

    @Override
    public long getAbyssalSaltCooldownEnd() {
        return abyssalSaltCooldownEnd;
    }

    @Override
    public void setAbyssalSaltCooldownEnd(long overworldGameTime) {
        // 只在服务端用，不随同步包发，所以不标记同步（存盘照常：玩家存档时整份写）
        abyssalSaltCooldownEnd = Math.max(0L, overworldGameTime);
    }

    @Override
    public CompoundTag getBossDaily() {
        return bossDaily;
    }

    @Override
    public void setBossDaily(CompoundTag daily) {
        bossDaily = daily == null ? new CompoundTag() : daily;
    }

    /** 取出「上一次读档读到的是没有 wishes 键的旧数据」这个标记（取一次就清掉），见类注释 */
    public boolean takeLegacyWishes() {
        boolean value = legacyWishes;
        legacyWishes = false;
        return value;
    }

    /** 取出上一次读档时 D14 迁移清掉的矩阵损伤（取一次就归零）；没迁移过是 0 */
    public int takeMigratedMatrixDamage() {
        int value = migratedMatrixDamage;
        migratedMatrixDamage = 0;
        return value;
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void markDirty() {
        dirty = true;
    }

    @Override
    public void clearDirty() {
        dirty = false;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("matrix_damage", matrixDamage);
        tag.putInt("radiation", radiation);
        tag.putInt("wind_points", windPoints);
        tag.putBoolean("windbound", windbound);
        tag.putInt("ubmru", ubmru);
        tag.putInt("matrix_type", matrixType);
        ListTag effects = new ListTag();
        for (String id : corruptionEffects) {
            effects.add(StringTag.valueOf(id));
        }
        tag.put(TAG_CORRUPTION_EFFECTS, effects);
        tag.putInt(TAG_PENTACLE_TIER, pentacleTier);
        tag.putInt(TAG_CORRUPTION_COOLDOWN, corruptionCooldown);
        tag.putInt(TAG_WISHES, windWishes);
        tag.put(TAG_WISH, windWish.copy());
        tag.putLong(TAG_WIND_HORN_COOLDOWN, windHornCooldown);
        if (!afflictions.isEmpty()) {
            CompoundTag list = new CompoundTag();
            afflictions.forEach((id, entry) -> list.putIntArray(id, new int[]{entry[0], entry[1]}));
            tag.put(TAG_AFFLICTIONS, list);
        }
        writeBatch13(tag);
        if (!mobLedger.isEmpty()) {
            tag.put(TAG_MOB_LEDGER, mobLedger.copy());
        }
        if (!bossFirstKills.isEmpty()) {
            tag.put(TAG_BOSS_FIRST_KILLS, stringList(bossFirstKills));
        }
        if (abyssalSaltCooldownEnd > 0L) {
            tag.putLong(TAG_ABYSSAL_SALT_CD, abyssalSaltCooldownEnd);
        }
        if (!bossDaily.isEmpty()) {
            tag.put(TAG_BOSS_DAILY, bossDaily.copy());
        }
        tag.put(TAG_MATRIX_CODEX, stringList(matrixCodex));
        tag.put(TAG_MATRIX_CODEX_HOANNA, stringList(matrixCodexHoanna));
        tag.putInt(TAG_DATA_VERSION, DATA_VERSION);
        return tag;
    }

    /**
     * 普通同步包用的那份：整份数据去掉两份图谱（batch13.md §3.1.5：大整合包里图谱可能几百条，随每次标脏整份发太重）
     * 和击杀台账（13c：只有服务端用得着），以及 13d 的渊盐冷却、首领每日领奖（同样只在服务端用）。图谱走 {@code network/S2CMatrixCodexPacket}；客户端用 {@link #deserializeSync} 收，保留自己那份图谱。
     */
    @Override
    public CompoundTag serializeForSync() {
        CompoundTag tag = serializeNBT();
        tag.remove(TAG_MATRIX_CODEX);
        tag.remove(TAG_MATRIX_CODEX_HOANNA);
        tag.remove(TAG_MOB_LEDGER);
        tag.remove(TAG_ABYSSAL_SALT_CD);
        tag.remove(TAG_BOSS_DAILY);
        return tag;
    }

    /** 客户端收普通同步包：包里没有的图谱保留本地那份（见 {@link #serializeForSync}） */
    @Override
    public void deserializeSync(CompoundTag tag) {
        List<String> keepCodex = tag.contains(TAG_MATRIX_CODEX) ? null : new ArrayList<>(matrixCodex);
        List<String> keepHoanna = tag.contains(TAG_MATRIX_CODEX_HOANNA) ? null : new ArrayList<>(matrixCodexHoanna);
        deserializeNBT(tag);
        if (keepCodex != null) {
            matrixCodex.addAll(keepCodex);
        }
        if (keepHoanna != null) {
            matrixCodexHoanna.addAll(keepHoanna);
        }
    }

    private void writeBatch13(CompoundTag tag) {
        tag.putInt(TAG_MATRIX_RANK, matrixRank);
        tag.putInt(TAG_MATRIX_POOL, matrixPool);
        tag.putBoolean(TAG_MATRIX_POOL_ABSORB, matrixPoolAbsorb);
        tag.putInt(TAG_MATRIX_TRIAL_COOLDOWN, matrixTrialCooldown);
        tag.putInt(TAG_MATRIX_TRIAL_RESERVE, matrixTrialReserve);
        tag.putInt(TAG_DEMON_STANDING, demonStanding);
        CompoundTag budget = new CompoundTag();
        budget.putFloat(BudgetKind.ARMOR.key(), attunementBudgetArmor);
        budget.putFloat(BudgetKind.WEAPON.key(), attunementBudgetWeapon);
        tag.put(TAG_ATTUNEMENT_BUDGET, budget);
        tag.putInt(TAG_ATTUNEMENT_OVERLOAD_CD, attunementOverloadCooldown);
    }

    /** 第十三批 13a 的键：缺键读成默认值（见类注释），手改存档写坏的数值夹回范围 */
    private void readBatch13(CompoundTag tag) {
        matrixRank = Math.max(0, Math.min(MAX_MATRIX_RANK, tag.getInt(TAG_MATRIX_RANK)));
        readStringSet(tag, TAG_MATRIX_CODEX, matrixCodex);
        readStringSet(tag, TAG_MATRIX_CODEX_HOANNA, matrixCodexHoanna);
        matrixPool = Math.max(0, tag.getInt(TAG_MATRIX_POOL));
        matrixPoolAbsorb = !tag.contains(TAG_MATRIX_POOL_ABSORB, Tag.TAG_ANY_NUMERIC) || tag.getBoolean(TAG_MATRIX_POOL_ABSORB);
        matrixTrialCooldown = Math.max(0, tag.getInt(TAG_MATRIX_TRIAL_COOLDOWN));
        matrixTrialReserve = Math.max(0, tag.getInt(TAG_MATRIX_TRIAL_RESERVE));
        demonStanding = Math.max(0, tag.getInt(TAG_DEMON_STANDING));
        CompoundTag budget = tag.getCompound(TAG_ATTUNEMENT_BUDGET);
        attunementBudgetArmor = finiteNonNegative(budget.getFloat(BudgetKind.ARMOR.key()));
        attunementBudgetWeapon = finiteNonNegative(budget.getFloat(BudgetKind.WEAPON.key()));
        attunementOverloadCooldown = Math.max(0, tag.getInt(TAG_ATTUNEMENT_OVERLOAD_CD));
    }

    private static float finiteNonNegative(float value) {
        return Float.isFinite(value) ? Math.max(0F, value) : 0F;
    }

    private static ListTag stringList(Set<String> values) {
        ListTag list = new ListTag();
        for (String value : values) {
            list.add(StringTag.valueOf(value));
        }
        return list;
    }

    private static void readStringSet(CompoundTag tag, String key, Set<String> into) {
        into.clear();
        ListTag list = tag.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            String value = list.getString(i);
            if (!value.isEmpty()) {
                into.add(value);
            }
        }
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        matrixDamage = tag.getInt("matrix_damage");
        radiation = tag.getInt("radiation");
        windPoints = tag.getInt("wind_points");
        windbound = tag.getBoolean("windbound");
        ubmru = tag.getInt("ubmru");
        matrixType = tag.getInt("matrix_type");
        corruptionEffects.clear();
        ListTag effects = tag.getList(TAG_CORRUPTION_EFFECTS, Tag.TAG_STRING);
        for (int i = 0; i < effects.size(); i++) {
            String id = effects.getString(i);
            // 手改存档可能写出重复或空的 id：去掉，保持「同一种只有一个」
            if (!id.isEmpty() && !corruptionEffects.contains(id)) {
                corruptionEffects.add(id);
            }
        }
        // 第十批加的两个键：旧数据没有时五芒星档位是 −1（还没打开过，不是 0 档），冷却是 0
        pentacleTier = tag.contains(TAG_PENTACLE_TIER, Tag.TAG_ANY_NUMERIC) ? Math.max(-1, Math.min(ECPentacle.MAX_TIER, tag.getInt(TAG_PENTACLE_TIER))) : -1;
        corruptionCooldown = Math.max(0, tag.getInt(TAG_CORRUPTION_COOLDOWN));
        // 第十批 10b 的三个键：旧数据没有时心愿数 0、没有心愿、号角不在冷却；有内容的旧数据记一个迁移标记（见类注释）
        windWishes = Math.max(0, tag.getInt(TAG_WISHES));
        windWish = tag.getCompound(TAG_WISH).copy();
        windHornCooldown = Math.max(0L, tag.getLong(TAG_WIND_HORN_COOLDOWN));
        legacyWishes = !tag.isEmpty() && !tag.contains(TAG_WISHES, Tag.TAG_ANY_NUMERIC);
        afflictions.clear();
        CompoundTag list = tag.getCompound(TAG_AFFLICTIONS);
        for (String id : list.getAllKeys()) {
            // 手改存档写坏的（不是两个数、时间不是正数）跳过
            if (list.get(id) instanceof IntArrayTag array && array.size() == 2 && array.getAsIntArray()[0] > 0) {
                int[] value = array.getAsIntArray();
                afflictions.put(id, new int[]{value[0], Math.max(0, value[1])});
            }
        }
        readBatch13(tag);
        // 第十三批 13c 的两个键：没有就是空的（没杀过、没打倒过）
        mobLedger = tag.getCompound(TAG_MOB_LEDGER).copy();
        readStringSet(tag, TAG_BOSS_FIRST_KILLS, bossFirstKills);
        // 第十三批 13d 的两个键：没有就是没在冷却、没领过
        abyssalSaltCooldownEnd = Math.max(0L, tag.getLong(TAG_ABYSSAL_SALT_CD));
        bossDaily = tag.getCompound(TAG_BOSS_DAILY).copy();
        migratedMatrixDamage = 0;
        if (tag.getInt(TAG_DATA_VERSION) < 1) {
            // D14：第一、二批攒下的矩阵损伤清零（见类注释）；版本号在下次存盘时写成 DATA_VERSION
            migratedMatrixDamage = matrixDamage;
            if (matrixDamage != 0) {
                matrixDamage = 0;
                markDirty();
            }
        }
    }
}
