package com.flameforge.essentialcraft5.api;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.matrix.MatrixRank;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * 25 种腐化效果的数据表（原作 CERegistry 的成本与类别 + CorruptionEffectECNBTBased 的序号、图标、触发概率），以及抽取规则和 {@code corruption} 配置。
 * <b>只有数据</b>：效果的行为（每秒判定一次、造成什么后果）由 ECCorruption 模块按 {@link Effect#id()} 写；五芒星按 {@link Effect#category()} 判档位、
 * 按 {@link Effect#cost()} 算花费；{@code /ec5 player <targets> corruption} 按 id 增删。玩家身上的效果存在 {@link IPlayerData#getCorruptionEffects()}。
 *
 * <h2>id</h2>
 * 「类别_原作序号」：身体 {@code body_0}~{@code body_4}、心智 {@code mind_5}~{@code mind_10}、矩阵 {@code matrix_11}~{@code matrix_24}（序号同原作 meta，
 * 研究书、原作 lang 的 {@code essentialcraft.effect.name_N} 都按它编号）。名字的翻译键 {@link Effect#nameKey()} 由框架写好（tools/lang/keys/systems_framework.json）；
 * 触发时发的那句话用 {@link Effect#messageKey()}，由 ECCorruption 写进自己的 lang 文件（原作 {@code essentialcraft.effect.desc_N}，#16、#18 原作不发话）。
 *
 * <h2>抽取（原作 PlayerTickHandler L538~574，ECCorruption 每 {@link #DRAW_INTERVAL} tick 调一次）</h2>
 * <pre>{@code
 * if (CorruptionEffects.rollDraw(data.getMatrixDamage(), random)) {                          // 概率 min(1, (损伤 + 1) / 36000)
 *     CorruptionEffects.Effect effect = CorruptionEffects.draw(data.getMatrixDamage(), data.getCorruptionEffects(), random);
 *     if (effect != null) {                                                                   // 候选：门槛 ≤ 损伤、还没有、没被配置关掉；身体类权重 ×3
 *         data.setMatrixDamage(data.getMatrixDamage() - CorruptionEffects.drawThreshold(effect)); // 抽中扣掉门槛（成本 × draw_threshold_multiplier）
 *         data.addCorruptionEffect(effect.id());
 *         player.sendSystemMessage(...);                                                      // 原作 essentialcraft.txt.corruption，红字
 *     }
 * }
 * }</pre>
 * 效果触发：每 {@link #TICK_INTERVAL} tick 对身上每个 {@link #isEnabled 开着的}效果判一次，概率见 {@link Effect#chance()}（原作写死的数，
 * 都是「每秒」的概率，因为原作玩家 tick 在 {@code ticksExisted % 20 != 0} 时直接 return），再乘 {@code effect_chance_multiplier}（{@link #effectChance}）。
 *
 * <h2>抽取门槛与效果强度（2026-10-16 起）</h2>
 * 抽到一个效果要攒够它的「门槛」= 成本 × {@code draw_threshold_multiplier}（默认 3，{@link #drawThreshold}），抽中扣的也是门槛；成本本身不变，
 * 还是五芒星治它的 ESPE 价。损伤上限 {@code max_matrix_damage} 默认跟着抬到 288000（最贵的效果门槛 216000，攒满还能再抽一两个）。
 * 效果发作更勤（{@code effect_chance_multiplier}，默认 ×2，每秒概率封顶 1）、扣血更重（{@code effect_damage_multiplier}，默认 ×1.5，
 * 不含机器泄漏的「MRU 腐化」状态）。第一次打开成形的五芒星以后，所有类别（含矩阵类）都可能被抽到（{@code corruption/CorruptionGate}）。
 *
 * <h2>配置（SERVER 配置 general.corruption，ECConfig.general 登记）</h2>
 * {@code effects_enabled}（默认 true，关掉就不抽、已有的也不触发）、{@code matrix_effects_enabled}（默认 true：矩阵类要五芒星 2 档才能治，
 * 成本 72 000 的三种要 3 档；2、3 档的柱子随第七批上线，矩阵类从那时起默认打开，E2）、{@code disabled_effects}（id 列表，单独关掉某几种）。
 * 读值用 {@link #isEnabled}。矩阵类另外受「门」管（{@link #registerMatrixGate}）：海纳尔之门关着时虚空材料拿不到、2、3 档做不出来，
 * 中了治不了，所以不抽、身上已有的也不发作。
 *
 * <h2>不毁号的几道闸（第十批，配置同在 general.corruption）</h2>
 * 原作的机器泄漏在 32 格内没有聚集体时会因为空指针什么都不做（只有基地本来就挨着上古遗迹那团时才汇进去），基地里不会自己长出聚集体；
 * 移植修好以后，基地里几乎必有大聚集体，第四到第八章就开始攒损伤，而矩阵类要到第十七章才治得了。所以加了这几道闸，默认都开：
 * <ul>
 *   <li>{@code draw_requires_cure_tier}：玩家<b>打开过</b>一座成形的灵魂五芒星以后才会抽到效果（哪一档都行，之后所有类别都可能抽到）；
 *       没打开过的玩家身上有损伤也不抽、已有的效果潜伏不发作；任何一档都治不了的效果（柱子容量配得太小）同样不抽
 *       （判定在 {@code corruption/CorruptionGate}，打开过的最高档存在 {@link IPlayerData#getPentacleTier()}）。</li>
 *   <li>{@code draw_cooldown_minutes}：抽中一个以后，至少隔这么多分钟（在线时间）才会再抽中下一个。</li>
 *   <li>{@code max_matrix_damage}：矩阵损伤最多攒到这么多（默认 288000，五芒星界面损伤条的满格 {@link #damageBarMax}）。所有加损伤的地方走 {@link #addMatrixDamage}。</li>
 *   <li>{@code gentle_effects}：#8 只打乱快捷栏、#9 把主手的东西收进背包而不是扔地上、#10 只是手抽一下（打断正在用的东西）、
 *       #13 炸自己最多掉当前生命的一半且不伤宠物和村民（{@code corruption/CorruptionBehaviors}）。</li>
 *   <li>{@code respect_claims}：MRU 聚集体只给能动它所在那一格的玩家加损伤，机器泄漏只汇进机器主人能动的聚集体、「MRU 腐化」状态只叠给机器的主人
 *       （{@code entity/MRUPresenceEntity}、{@code mru/ECCorruption}）；只有自己的、或者自己能动的五芒星才记档位（{@code PlayerPentacleBlockEntity}）。</li>
 *   <li>{@code mru_corruption_lethal}（默认关）：机器泄漏叠的「MRU 腐化」状态像原版中毒一样打不死人（{@code effect/MRUCorruptionEffect}）。腐化效果和这个状态扣多少血见 {@link HealthCost}（2026-10-13 起按最大生命的比例）。</li>
 * </ul>
 * 以后把别的东西换算成矩阵损伤的（例如把伤害或有害状态转成损伤的装备），一律走 {@link #addMatrixDamage}，并先看 {@link #matrixRoom}：
 * 损伤已经攒满时没有地方再记，转换就不该生效，否则满载以后等于白挡。
 * 第十三批起新加的损伤来源走带来源标记的重载 {@link #addMatrixDamage(IPlayerData, long, Source)}（{@link Source#BATCH13}，吃矩阵阶的耐受和稳固）。
 */
public final class CorruptionEffects {

    /** 效果的三类（原作 EnumCorruptionEffect），决定五芒星几档能治、抽取权重 */
    public enum Category {
        /** 身体：五芒星 0 档能治；抽取时权重 ×3（原作 findSuitableEffects 把身体类加三遍） */
        BODY(0, 3),
        /** 心智：五芒星 1 档起能治（原作 GuiPlayerPentacle L71 / L155） */
        MIND(1, 1),
        /** 矩阵：五芒星 2 档起能治 */
        MATRIX(2, 1);

        private final int minPentacleTier;
        private final int drawWeight;

        Category(int minPentacleTier, int drawWeight) {
            this.minPentacleTier = minPentacleTier;
            this.drawWeight = drawWeight;
        }

        /** 灵魂五芒星至少几档才能治这一类 */
        public int minPentacleTier() {
            return minPentacleTier;
        }

        /** 抽取时的权重 */
        public int drawWeight() {
            return drawWeight;
        }

        /** id 前缀，也是翻译键里的名字：body / mind / matrix */
        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** 类别名的翻译键 corruption_category.essentialcraft5.&lt;key&gt;（身体 / 心智 / 矩阵，框架已写） */
        public String nameKey() {
            return "corruption_category." + EssentialCraft5.MODID + "." + key();
        }
    }

    /**
     * 一种腐化效果。
     *
     * @param id       「类别_序号」，存进玩家数据的就是它
     * @param index    原作序号（meta）0~24
     * @param category 类别
     * @param cost     成本：抽中时从矩阵损伤里扣掉的量，也是五芒星治它要花的有效 ESPE（原作 getStickiness）
     * @param chance   原作每秒触发一次的概率（写死在 onPlayerTick 里的数；#4 是「下落中每秒都触发」，记 1）。条件（在末地、在下雨……）见 {@link #condition}
     * @param icon     五芒星界面的图标 textures/special/corruptioneffects/*.png（原作 allLocs，已导入）
     * @param condition 原作触发条件的简述（给写行为的人对照，不是玩家看的文字）
     */
    public record Effect(String id, int index, Category category, int cost, float chance, ResourceLocation icon, String condition) {

        /** 名字：corruption_effect.essentialcraft5.&lt;id&gt;（框架已写，原作 essentialcraft.effect.name_N） */
        public String nameKey() {
            return "corruption_effect." + EssentialCraft5.MODID + "." + id;
        }

        /** 触发时发给本人的话：corruption_effect.essentialcraft5.&lt;id&gt;.message（ECCorruption 写，原作 essentialcraft.effect.desc_N） */
        public String messageKey() {
            return nameKey() + ".message";
        }

        /** 按配置现在开没开着（{@link CorruptionEffects#isEnabled(Effect)}） */
        public boolean isEnabled() {
            return CorruptionEffects.isEnabled(this);
        }
    }

    /** 抽取概率的分母：原作 {@code rand.nextInt(36000) <= damage} */
    public static final int DRAW_SCALE = 36000;
    /** 多少 tick 抽一次（原作 ticksExisted % 1200） */
    public static final int DRAW_INTERVAL = 1200;
    /** 多少 tick 跑一遍身上的效果（原作玩家 tick 每 20 tick 才往下走） */
    public static final int TICK_INTERVAL = 20;
    /** 原作五芒星界面损伤条的满格值（GuiPlayerPentacle）；现在的满格是 {@link #damageBarMax()} */
    public static final int DAMAGE_BAR_MAX = 72000;

    private static final Map<String, Effect> BY_ID = new LinkedHashMap<>();
    private static final List<Effect> ALL_LIST = new ArrayList<>();

    /** 全部 25 种，按原作序号排（下标就是序号） */
    public static final List<Effect> ALL;

    static {
        // 成本：CERegistry L13~37；概率与条件：CorruptionEffectECNBTBased.onPlayerTick L152~363；图标：allLocs L41~65
        add(Category.BODY, 6000, 0.001F, "body_hands", "0.1%：原作给缓慢 II 20 秒，名字「双手迟钝」→ 改挖掘疲劳 II（batch3 §3.6 修 1）");
        add(Category.BODY, 6000, 0.001F, "body_hands", "0.1%：虚弱 II 20 秒");
        add(Category.BODY, 6000, 0.01F, "body_hands", "1%，且正在挥手：仙人掌伤害，最大生命 × HealthCost.HAND_BURN（原作 3 点）");
        add(Category.BODY, 6000, 0.001F, "body_legs", "0.1%：缓慢 II 20 秒");
        add(Category.BODY, 6000, 1F, "body_legs", "下落中（fallDistance > 0）每秒摔落距离 +1");
        add(Category.MIND, 36000, 0.001F, "mind_white", "0.1%，且没有心智悖论：心智悖论 100 秒");
        add(Category.MIND, 36000, 0.0008F, "mind_black", "0.08%：只发一句话");
        add(Category.MIND, 14400, 0.001F, "mind_purple", "0.1%：只发一句话");
        add(Category.MIND, 18000, 0.00005F, "mind_red", "0.005%：打乱物品栏 → 只打乱主物品栏 36 格（修 2）；gentle_effects 时只打乱快捷栏 9 格");
        add(Category.MIND, 8000, 0.00007F, "mind_blue", "0.007%：原作丢光全部物品 → 只丢主手那一组（修 3）；gentle_effects 时收进背包，不扔");
        add(Category.MIND, 8000, 0.001F, "mind_teal", "0.1%：替玩家右键主手物品 → 走 connection.handleUseItem（修 4）；gentle_effects 时只挥一下手、打断正在用的东西");
        add(Category.MATRIX, 20000, 0.03F, "matrix_damage", "受击中（hurtTime > 0）3%：清掉无敌帧");
        add(Category.MATRIX, 72000, 0.003F, "matrix_end", "在末地 0.3%：溺水伤害，最大生命 × HealthCost.SUFFOCATION（原作 1 点）");
        add(Category.MATRIX, 20000, 0.0006F, "matrix_explosion", "0.06%：以自己为中心 3 级爆炸 → ECProtection.safeExplode（不破坏方块，攻击者是本人）；本人那一下按最大生命 × HealthCost.SELF_BLAST 算（原作贴脸 43 点）；gentle_effects 时本人最多掉当前生命一半、不伤驯服的动物和村民");
        add(Category.MATRIX, 4000, 0.001F, "matrix_lava", "0.1%：着火 8 秒，同时扣最大生命 × HealthCost.BURNING 的着火伤害（原作只着火）");
        add(Category.MATRIX, 8000, 0.0007F, "matrix_lightning", "0.07%：真闪电 → 只做视觉的闪电 + 本人 thunderHit（闪电伤害按最大生命 × HealthCost.LIGHTNING，原作 5 点）");
        add(Category.MATRIX, 2000, 0.01F, "matrix_matrix", "1%：矩阵损伤 +1（不发话）");
        add(Category.MATRIX, 36000, 0.01F, "matrix_moon", "1%，夜里、没下雨、头顶露天：魔法伤害，最大生命 × HealthCost.MOONLIGHT × 月相 5 / 3 / 2 / 1 / 0 ÷ 5（原作 1~5 点；原作条件写错永不触发，E8 修）");
        add(Category.MATRIX, 14400, 0.001F, "matrix_mru", "0.1%，且身上有 MRU 腐化：魔法伤害，最大生命 × HealthCost.MRU_FAILURE（原作 4 点；不发话）");
        add(Category.MATRIX, 72000, 0.003F, "matrix_nether", "在下界 0.3%：溺水伤害，同 #12");
        add(Category.MATRIX, 72000, 0.003F, "matrix_overworld", "在主世界 0.3%：溺水伤害，同 #12");
        add(Category.MATRIX, 36000, 0.1F, "matrix_rain", "10%，白天、下雨、头顶露天：凋零伤害，最大生命 × HealthCost.WITHERING（原作 1 点；原作不发话）");
        add(Category.MATRIX, 36000, 0.003F, "matrix_sun", "0.3%，白天、没下雨、头顶露天：凋零伤害，同 #21");
        add(Category.MATRIX, 8000, 0.03F, "matrix_water", "3%，眼睛在水里：凋零伤害，最大生命 × HealthCost.ACID_WATER（原作 3 点）");
        add(Category.MATRIX, 4000, 0.005F, "matrix_wind", "0.5%：凋零伤害，同 #21");
        ALL = Collections.unmodifiableList(ALL_LIST);
    }

    private CorruptionEffects() {
    }

    private static void add(Category category, int cost, float chance, String icon, String condition) {
        int index = ALL_LIST.size();
        String id = category.key() + "_" + index;
        Effect effect = new Effect(id, index, category, cost, chance,
                EssentialCraft5.id("textures/special/corruptioneffects/" + icon + ".png"), condition);
        ALL_LIST.add(effect);
        BY_ID.put(id, effect);
    }

    // ---------------------------------------------------------------- 查询

    @Nullable
    public static Effect byId(String id) {
        return BY_ID.get(id);
    }

    /** 按原作序号取（0~24），越界返回 null */
    @Nullable
    public static Effect byIndex(int index) {
        return index >= 0 && index < ALL.size() ? ALL.get(index) : null;
    }

    /** 全部 id（按序号），指令补全用 */
    public static Collection<String> ids() {
        return Collections.unmodifiableCollection(BY_ID.keySet());
    }

    // ---------------------------------------------------------------- 抽取

    /** 一次抽取判定成功的概率：min(1, (损伤 + 1) / 36000)；损伤 ≤ 0 时按 0 算（1 / 36000） */
    public static double drawChance(int matrixDamage) {
        return Math.min(1D, (Math.max(0, matrixDamage) + 1D) / DRAW_SCALE);
    }

    /** 掷一次抽取判定（原作 {@code rand.nextInt(36000) <= damage}） */
    public static boolean rollDraw(int matrixDamage, RandomSource random) {
        return random.nextInt(DRAW_SCALE) <= matrixDamage;
    }

    /**
     * 抽取的候选（带权重展开：身体类每种出现 3 次）：门槛（{@link #drawThreshold}）≤ 损伤、owned 里还没有、{@link #isEnabled 开着}的效果。
     * 效果总开关关着时返回空表。
     */
    public static List<Effect> drawCandidates(int matrixDamage, Collection<String> owned) {
        List<Effect> candidates = new ArrayList<>();
        if (!effectsEnabled()) {
            return candidates;
        }
        for (Effect effect : ALL) {
            if (drawThreshold(effect) <= matrixDamage && !owned.contains(effect.id()) && isEnabled(effect)) {
                for (int i = 0; i < effect.category().drawWeight(); i++) {
                    candidates.add(effect);
                }
            }
        }
        return candidates;
    }

    /** 从候选里等概率抽一个（候选已按权重展开）；没有候选返回 null。不改玩家数据，扣损伤、加效果由调用方做 */
    @Nullable
    public static Effect draw(int matrixDamage, Collection<String> owned, RandomSource random) {
        return draw(matrixDamage, owned, random, effect -> true);
    }

    /** 同 {@link #drawCandidates(int, Collection)}，再只留 {@code allowed} 放行的（腐化效果模块传「这名玩家的五芒星档位治得了」） */
    public static List<Effect> drawCandidates(int matrixDamage, Collection<String> owned, Predicate<Effect> allowed) {
        List<Effect> candidates = drawCandidates(matrixDamage, owned);
        candidates.removeIf(effect -> !allowed.test(effect));
        return candidates;
    }

    /** 同 {@link #draw(int, Collection, RandomSource)}，候选再过一道 {@code allowed} */
    @Nullable
    public static Effect draw(int matrixDamage, Collection<String> owned, RandomSource random, Predicate<Effect> allowed) {
        List<Effect> candidates = drawCandidates(matrixDamage, owned, allowed);
        return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
    }

    // ---------------------------------------------------------------- 门槛与强度

    /** 抽到这个效果要攒多少矩阵损伤：成本 × {@code draw_threshold_multiplier} 向上取整；抽中扣的也是它 */
    public static int drawThreshold(Effect effect) {
        long threshold = (long) Math.ceil(effect.cost() * drawThresholdMultiplier());
        return (int) Math.min(Integer.MAX_VALUE, threshold);
    }

    /** 效果每秒发作的概率：原作的概率 × {@code effect_chance_multiplier}，封顶 1 */
    public static float effectChance(Effect effect) {
        return (float) Math.min(1D, effect.chance() * effectChanceMultiplier());
    }

    /** 抽取门槛的倍率（{@code general.corruption.draw_threshold_multiplier}；测试覆盖值优先） */
    public static double drawThresholdMultiplier() {
        if (testThresholdMultiplier != null) {
            return testThresholdMultiplier;
        }
        return Config.drawThresholdMultiplier == null ? Config.DEFAULT_DRAW_THRESHOLD_MULTIPLIER : ECConfig.get(Config.drawThresholdMultiplier);
    }

    /** 效果发作概率的倍率（{@code general.corruption.effect_chance_multiplier}；测试覆盖值优先） */
    public static double effectChanceMultiplier() {
        if (testChanceMultiplier != null) {
            return testChanceMultiplier;
        }
        return Config.effectChanceMultiplier == null ? Config.DEFAULT_EFFECT_CHANCE_MULTIPLIER : ECConfig.get(Config.effectChanceMultiplier);
    }

    /** 效果扣血的倍率（{@code general.corruption.effect_damage_multiplier}；测试覆盖值优先） */
    public static double effectDamageMultiplier() {
        if (testDamageMultiplier != null) {
            return testDamageMultiplier;
        }
        return Config.effectDamageMultiplier == null ? Config.DEFAULT_EFFECT_DAMAGE_MULTIPLIER : ECConfig.get(Config.effectDamageMultiplier);
    }

    /** 五芒星界面损伤条的满格：配置的上限；不封顶（2147483647 之类）时按最贵效果的门槛再留一份余量 */
    public static int damageBarMax() {
        int cap = maxMatrixDamage();
        if (cap > 0 && cap <= DAMAGE_BAR_MAX * 100) {
            return cap;
        }
        long highest = 0L;
        for (Effect effect : ALL) {
            highest = Math.max(highest, drawThreshold(effect));
        }
        return (int) Math.max(DAMAGE_BAR_MAX, Math.min(Integer.MAX_VALUE, highest + highest / 3));
    }

    // ---------------------------------------------------------------- 矩阵损伤的上限

    /** 矩阵损伤的上限（{@code general.corruption.max_matrix_damage}，默认 288000；测试覆盖值优先） */
    public static int maxMatrixDamage() {
        if (testMaxDamageOverride != null) {
            return testMaxDamageOverride;
        }
        return Config.maxMatrixDamage == null ? Config.DEFAULT_MAX_MATRIX_DAMAGE : ECConfig.get(Config.maxMatrixDamage);
    }

    /** 还能再记多少矩阵损伤（上限 − 现有，最少 0）。把伤害、状态转成损伤的东西先看它，满了就不该再转 */
    public static int matrixRoom(IPlayerData data) {
        return (int) Math.max(0L, (long) maxMatrixDamage() - data.getMatrixDamage());
    }

    /**
     * 加矩阵损伤的统一入口：按 long 算、夹在上限以内（已经超过上限的不往下扣，由腐化效果模块每秒夹回去）。
     *
     * @return 实际加上的量（0 = 没加：amount ≤ 0，或者已经满了）
     */
    public static int addMatrixDamage(IPlayerData data, long amount) {
        if (amount <= 0L) {
            return 0;
        }
        int current = data.getMatrixDamage();
        long target = Math.min((long) current + amount, Math.max((long) current, (long) maxMatrixDamage()));
        int added = (int) Math.max(0L, Math.min(Integer.MAX_VALUE, target) - current);
        if (added > 0) {
            data.setMatrixDamage(current + added);
        }
        return added;
    }

    /**
     * 矩阵损伤的来源分两类（第十三批 13a，batch13.md §3.1.4、D34 A）：耐受、矩阵稳固只抵矩阵阶这一套新带来的损伤，
     * 以前就有的来源（机器泄漏、聚集体、升华台、哨兵、枪械、腐化 #16 等）一点不减。
     */
    public enum Source {
        /** 以前就有的来源：照 {@link #addMatrixDamage(IPlayerData, long)} 原样记 */
        LEGACY,
        /**
         * 第十三批新加的来源（试炼成功、虹吸、13c 起新怪物的命中）：先按本人的矩阵阶打折。
         * 矩阵之影不加新损伤，它的退还是把召唤时转走的原数还回去，走 {@link #LEGACY}（按阶打折的话，故意打输就能净化一截）
         */
        BATCH13
    }

    /**
     * 带来源标记的加损伤入口：{@link Source#BATCH13} 先 ÷ (1 + 0.1 × 阶)、七阶起再 × 0.8（{@code matrix/MatrixRank.scaleNewDamage}，
     * 两个数都可配），再照 {@link #addMatrixDamage(IPlayerData, long)} 夹上限；零阶不打折。{@link Source#LEGACY} 等于不带标记的那个。
     * 抽取概率（{@link #DRAW_SCALE}）、门槛、上限、冷却、档位闸都不因为矩阵阶变化。
     *
     * @return 实际加上的量
     */
    public static int addMatrixDamage(IPlayerData data, long amount, Source source) {
        long scaled = source == Source.BATCH13 ? MatrixRank.scaleNewDamage(data.getMatrixRank(), amount) : amount;
        return addMatrixDamage(data, scaled);
    }

    // ---------------------------------------------------------------- 配置

    /** 腐化效果总开关（{@code general.corruption.effects_enabled}；测试覆盖值优先） */
    public static boolean effectsEnabled() {
        if (testEffectsEnabledOverride != null) {
            return testEffectsEnabledOverride;
        }
        return Config.effectsEnabled == null ? Config.DEFAULT_EFFECTS_ENABLED : ECConfig.get(Config.effectsEnabled);
    }

    /**
     * 矩阵类开着没有：开关（{@code general.corruption.matrix_effects_enabled}，默认开，E2；测试覆盖值优先）开着，
     * 并且所有登记的门都放行（没人登记时放行）。
     */
    public static boolean matrixEffectsEnabled() {
        boolean switchedOn = testMatrixOverride != null ? testMatrixOverride
                : Config.matrixEffectsEnabled == null ? Config.DEFAULT_MATRIX_EFFECTS_ENABLED : ECConfig.get(Config.matrixEffectsEnabled);
        if (!switchedOn) {
            return false;
        }
        for (BooleanSupplier gate : MATRIX_GATES) {
            if (!gate.getAsBoolean()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 登记一个矩阵类的门：门返回 false 时矩阵类不抽、身上已有的也不发作（配置开关照旧要开着）。
     * 在注册类的 {@code init()} 里登记，例：{@code registry/ECHoannaPortal} 登记「海纳尔之门开着」——门关着时虚空材料拿不到，
     * 灵魂五芒星 2、3 档做不出来，中了矩阵类就治不了。门每次判定都会调，要便宜（读配置、读测试覆盖值），两端都可能调。
     */
    public static void registerMatrixGate(BooleanSupplier gate) {
        MATRIX_GATES.add(Objects.requireNonNull(gate));
    }

    /** 登记过的矩阵类门（只在模组构造时加，之后只读） */
    private static final List<BooleanSupplier> MATRIX_GATES = new CopyOnWriteArrayList<>();

    /*
     * GameTest 用的内存覆盖值（null = 按配置）。测试不能用 ConfigValue.set 改真配置：Forge 的服务端配置会自动存盘，
     * 文件监视器随后在后台把整份文件重读一遍，读到的若是连续几次 set 中间的内容，就会把后面刚设的值冲掉（2026-09-25 实测的不稳定失败）。
     */
    @Nullable
    private static Boolean testMatrixOverride;
    @Nullable
    private static List<String> testDisabledOverride;
    @Nullable
    private static Boolean testRequireCureTierOverride;
    @Nullable
    private static Integer testCooldownOverride;
    @Nullable
    private static Integer testMaxDamageOverride;
    @Nullable
    private static Boolean testGentleOverride;
    @Nullable
    private static Boolean testRespectClaimsOverride;
    @Nullable
    private static Boolean testMruCorruptionLethalOverride;
    @Nullable
    private static Boolean testEffectsEnabledOverride;
    @Nullable
    private static Double testThresholdMultiplier;
    @Nullable
    private static Double testChanceMultiplier;
    @Nullable
    private static Double testDamageMultiplier;

    /**
     * GameTest 用：临时覆盖抽取门槛、发作概率、扣血三个倍率（传 null 恢复按配置），测完调 {@link #clearTestSafetyOverrides}。
     * 只许在同一段同步代码里设、测、清（别的测试的玩家 tick 也读它）。
     */
    public static void setTestStrength(@Nullable Double threshold, @Nullable Double chance, @Nullable Double damage) {
        testThresholdMultiplier = threshold;
        testChanceMultiplier = chance;
        testDamageMultiplier = damage;
    }

    /** GameTest 用：临时覆盖矩阵类开关与禁用列表（传 null 恢复按配置），测完必须在 finally 里恢复 */
    public static void setTestOverrides(@Nullable Boolean matrixEnabled, @Nullable List<String> disabledIds) {
        testMatrixOverride = matrixEnabled;
        testDisabledOverride = disabledIds == null ? null : List.copyOf(disabledIds);
    }

    /**
     * GameTest 用：临时覆盖第十批的几道闸（传 null 恢复按配置），测完必须在 finally 里调 {@link #clearTestSafetyOverrides}。
     *
     * @param requireCureTier  {@code draw_requires_cure_tier}
     * @param cooldownMinutes  {@code draw_cooldown_minutes}
     * @param maxDamage        {@code max_matrix_damage}
     * @param gentle           {@code gentle_effects}
     * @param respectClaims    {@code respect_claims}
     */
    public static void setTestSafetyOverrides(@Nullable Boolean requireCureTier, @Nullable Integer cooldownMinutes, @Nullable Integer maxDamage,
                                              @Nullable Boolean gentle, @Nullable Boolean respectClaims) {
        testRequireCureTierOverride = requireCureTier;
        testCooldownOverride = cooldownMinutes;
        testMaxDamageOverride = maxDamage;
        testGentleOverride = gentle;
        testRespectClaimsOverride = respectClaims;
    }

    /** 恢复 {@link #setTestSafetyOverrides}、{@link #setTestMruCorruptionLethal}、{@link #setTestEffectsEnabled}、{@link #setTestHealthCost} 改过的全部值 */
    public static void clearTestSafetyOverrides() {
        setTestSafetyOverrides(null, null, null, null, null);
        testMruCorruptionLethalOverride = null;
        testEffectsEnabledOverride = null;
        setTestStrength(null, null, null);
        HealthCost.clearTestOverrides();
    }

    /**
     * GameTest 用：临时覆盖 {@code effects_enabled}（null 恢复按配置），测完调 {@link #clearTestSafetyOverrides}。
     * 只许在同一段同步代码里设、测、清（别的测试的玩家 tick 也读它）。
     */
    public static void setTestEffectsEnabled(@Nullable Boolean enabled) {
        testEffectsEnabledOverride = enabled;
    }

    /** GameTest 用：临时覆盖 {@code mru_corruption_lethal}（null 恢复按配置），测完调 {@link #clearTestSafetyOverrides} */
    public static void setTestMruCorruptionLethal(@Nullable Boolean lethal) {
        testMruCorruptionLethalOverride = lethal;
    }

    /** 「MRU 腐化」状态（机器泄漏叠的那个，不是腐化效果）能不能打死人（{@code general.corruption.mru_corruption_lethal}，默认不能） */
    public static boolean mruCorruptionLethal() {
        if (testMruCorruptionLethalOverride != null) {
            return testMruCorruptionLethalOverride;
        }
        return Config.mruCorruptionLethal == null ? Config.DEFAULT_MRU_CORRUPTION_LETHAL : ECConfig.get(Config.mruCorruptionLethal);
    }

    /** 只抽打开过的五芒星治得了的效果、治不了的潜伏（{@code general.corruption.draw_requires_cure_tier}，默认开） */
    public static boolean drawRequiresCureTier() {
        if (testRequireCureTierOverride != null) {
            return testRequireCureTierOverride;
        }
        return Config.drawRequiresCureTier == null ? Config.DEFAULT_DRAW_REQUIRES_CURE_TIER : ECConfig.get(Config.drawRequiresCureTier);
    }

    /** 抽中一个以后至少隔几分钟（在线时间）才会再抽中（{@code general.corruption.draw_cooldown_minutes}，默认 20；0 = 照原作每分钟都抽） */
    public static int drawCooldownMinutes() {
        if (testCooldownOverride != null) {
            return testCooldownOverride;
        }
        return Config.drawCooldownMinutes == null ? Config.DEFAULT_DRAW_COOLDOWN_MINUTES : ECConfig.get(Config.drawCooldownMinutes);
    }

    /** #8、#9、#10、#13 用温和的后果（{@code general.corruption.gentle_effects}，默认开） */
    public static boolean gentleEffects() {
        if (testGentleOverride != null) {
            return testGentleOverride;
        }
        return Config.gentleEffects == null ? Config.DEFAULT_GENTLE_EFFECTS : ECConfig.get(Config.gentleEffects);
    }

    /** 聚集体、泄漏照领地走（{@code general.corruption.respect_claims}，默认开） */
    public static boolean respectClaims() {
        if (testRespectClaimsOverride != null) {
            return testRespectClaimsOverride;
        }
        return Config.respectClaims == null ? Config.DEFAULT_RESPECT_CLAIMS : ECConfig.get(Config.respectClaims);
    }

    /**
     * 这种效果现在开没开着：总开关开、（矩阵类还要矩阵类开关开）、不在 disabled_effects 里。
     * 抽取时只抽开着的；ECCorruption 跑效果时也要跳过关着的（玩家身上已有的关掉以后就不再触发，但留在列表里，重新打开后恢复）。
     */
    public static boolean isEnabled(Effect effect) {
        if (!effectsEnabled()) {
            return false;
        }
        if (effect.category() == Category.MATRIX && !matrixEffectsEnabled()) {
            return false;
        }
        return !disabledIds().contains(effect.id());
    }

    private static List<? extends String> disabledIds() {
        if (testDisabledOverride != null) {
            return testDisabledOverride;
        }
        ForgeConfigSpec.ConfigValue<List<? extends String>> value = Config.disabledEffects;
        if (value == null) {
            return List.of();
        }
        try {
            return value.get();
        } catch (IllegalStateException | NullPointerException e) {
            // 配置还没加载（进世界前；开发环境里 get() 会抛异常）：按默认（空表）
            return value.getDefault();
        }
    }

    /** SERVER 配置 general.corruption（由 player/PlayerSystems.init 登记） */
    public static final class Config {
        static final boolean DEFAULT_EFFECTS_ENABLED = true;
        static final boolean DEFAULT_MATRIX_EFFECTS_ENABLED = true;
        static final boolean DEFAULT_DRAW_REQUIRES_CURE_TIER = true;
        public static final int DEFAULT_DRAW_COOLDOWN_MINUTES = 20;
        public static final int DEFAULT_MAX_MATRIX_DAMAGE = DAMAGE_BAR_MAX * 4;
        public static final double DEFAULT_DRAW_THRESHOLD_MULTIPLIER = 3D;
        public static final double DEFAULT_EFFECT_CHANCE_MULTIPLIER = 2D;
        public static final double DEFAULT_EFFECT_DAMAGE_MULTIPLIER = 1.5D;
        static final boolean DEFAULT_GENTLE_EFFECTS = true;
        static final boolean DEFAULT_RESPECT_CLAIMS = true;
        static final boolean DEFAULT_MRU_CORRUPTION_LETHAL = false;

        public static ForgeConfigSpec.BooleanValue effectsEnabled;
        public static ForgeConfigSpec.BooleanValue matrixEffectsEnabled;
        public static ForgeConfigSpec.ConfigValue<List<? extends String>> disabledEffects;
        public static ForgeConfigSpec.BooleanValue drawRequiresCureTier;
        public static ForgeConfigSpec.IntValue drawCooldownMinutes;
        public static ForgeConfigSpec.IntValue maxMatrixDamage;
        public static ForgeConfigSpec.DoubleValue drawThresholdMultiplier;
        public static ForgeConfigSpec.DoubleValue effectChanceMultiplier;
        public static ForgeConfigSpec.DoubleValue effectDamageMultiplier;
        public static ForgeConfigSpec.BooleanValue gentleEffects;
        public static ForgeConfigSpec.BooleanValue respectClaims;
        public static ForgeConfigSpec.BooleanValue mruCorruptionLethal;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.comment("Corruption effects that matrix damage turns into.", "矩阵损伤转成的腐化效果").push("corruption");
            effectsEnabled = builder.comment("Whether players can get corruption effects, and whether effects they have do anything.",
                            "玩家会不会得到腐化效果、已有的效果会不会触发")
                    .define("effects_enabled", DEFAULT_EFFECTS_ENABLED);
            matrixEffectsEnabled = builder.comment(
                            "Whether matrix-type effects (matrix_11 to matrix_24) can be drawn and trigger. They need a tier 2 Soul Pentacle to cure",
                            "(the three costing 72000 need tier 3). They are also off while Gates to Hoanna are disabled (general.hoanna.portal_enabled),",
                            "since the Void materials for tier 2 and 3 cannot be obtained then.",
                            "矩阵类效果（matrix_11~matrix_24）会不会被抽到、会不会触发。治它们要 2 档灵魂五芒星（成本 72000 的三种要 3 档）；",
                            "海纳尔之门关着（general.hoanna.portal_enabled）时 2、3 档的虚空材料拿不到，矩阵类也一并不抽、不发作")
                    .define("matrix_effects_enabled", DEFAULT_MATRIX_EFFECTS_ENABLED);
            disabledEffects = builder.comment("Effect ids that are never drawn and never trigger, e.g. [\"mind_8\", \"matrix_13\"].",
                            "单独关掉的效果 id（不会被抽到、已有的不触发），例如 [\"mind_8\", \"matrix_13\"]")
                    .defineListAllowEmpty("disabled_effects", List.of(), o -> o instanceof String s && BY_ID.containsKey(s));
            drawRequiresCureTier = builder.comment(
                            "Effects are only drawn for players who have opened a complete Soul Pentacle (any tier) at least once; from then on every",
                            "category can be drawn, matrix ones included. Players who never opened one draw nothing and effects they already have stay",
                            "dormant (do nothing); so do effects no pentacle tier can cure with the current pillar capacity. Off = draw any effect, as in the original.",
                            "玩家打开过一次成形的灵魂五芒星（哪一档都行）以后才会抽到效果，之后所有类别（含矩阵类）都可能抽到；没打开过的不抽、已有的效果潜伏不发作；",
                            "按现在的柱子容量哪一档都治不了的效果同样不抽。关掉 = 照原作什么都抽")
                    .define("draw_requires_cure_tier", DEFAULT_DRAW_REQUIRES_CURE_TIER);
            drawCooldownMinutes = builder.comment(
                            "After gaining an effect, minutes of play before the next one can be gained (draws are rolled once per minute online). 0 = every minute, as in the original.",
                            "抽中一个效果以后，至少再过多少分钟（在线时间，每分钟抽一次）才可能抽中下一个。0 = 照原作每分钟都可能抽中")
                    .defineInRange("draw_cooldown_minutes", DEFAULT_DRAW_COOLDOWN_MINUTES, 0, 1440);
            maxMatrixDamage = builder.comment(
                            "Matrix damage stops growing at this value (it is also the full bar in the Soul Pentacle screen). It should be at least the",
                            "highest threshold (cost x draw_threshold_multiplier, 216000 by default), or the costliest effects can never be drawn.",
                            "2147483647 = no limit, as in the original.",
                            "矩阵损伤最多攒到多少（也是五芒星界面损伤条的满格）。至少要有最贵效果的门槛（成本 × draw_threshold_multiplier，默认 216000），",
                            "否则最贵的几种永远抽不到。2147483647 = 照原作不封顶")
                    .defineInRange("max_matrix_damage", DEFAULT_MAX_MATRIX_DAMAGE, 0, Integer.MAX_VALUE);
            drawThresholdMultiplier = builder.comment(
                            "Matrix damage needed to draw an effect = its cost x this (the cost is still what the Soul Pentacle charges to cure it);",
                            "drawing one also uses up that much damage. Raise it to make effects rarer and the damage bar matter more.",
                            "抽到一个效果要攒多少矩阵损伤 = 它的成本 × 这个数（成本还是五芒星治它的 ESPE 价）；抽中时扣掉的也是这么多。调大 = 效果更难出、损伤更值得防")
                    .defineInRange("draw_threshold_multiplier", DEFAULT_DRAW_THRESHOLD_MULTIPLIER, 1D, 100D);
            effectChanceMultiplier = builder.comment(
                            "Multiplier on how often effects the player has trigger (each effect's per-second chance, capped at 1).",
                            "身上的效果发作得多勤：每种效果每秒发作概率 × 这个数（封顶 1）")
                    .defineInRange("effect_chance_multiplier", DEFAULT_EFFECT_CHANCE_MULTIPLIER, 0D, 100D);
            effectDamageMultiplier = builder.comment(
                            "Multiplier on the health taken by effects (on top of the *_health_percent values below; the MRU Corruption status",
                            "stacked by leaking machines is not an effect and is not multiplied). One hit never takes more than the whole max health.",
                            "效果扣血的倍率（乘在下面各项 *_health_percent 之上；机器泄漏叠的「MRU 腐化」状态不是腐化效果，不乘）。一下最多扣到整条最大生命")
                    .defineInRange("effect_damage_multiplier", DEFAULT_EFFECT_DAMAGE_MULTIPLIER, 0D, 10D);
            gentleEffects = builder.comment(
                            "Gentler outcomes: #8 shuffles only the hotbar, #9 puts the held stack away into the inventory instead of dropping it,",
                            "#10 only makes the hand twitch (interrupting whatever is being used) instead of using the held item,",
                            "#13 takes at most half of the current health from the player and spares tamed animals and villagers.",
                            "温和的后果：#8 只打乱快捷栏；#9 把主手那一组收进背包，不扔在地上；#10 只是手抽一下（打断正在用的东西），不替你使用手里的东西；",
                            "#13 炸自己最多掉当前生命的一半，不伤驯服的动物和村民")
                    .define("gentle_effects", DEFAULT_GENTLE_EFFECTS);
            respectClaims = builder.comment(
                            "MRUCUs only add matrix damage to players who may edit the block they float in; machine leaks only feed",
                            "MRUCUs the machine's owner may edit (otherwise a new one forms at the machine), and the MRU Corruption status they cause",
                            "only stacks on the machine's owner; a Soul Pentacle only records its tier for its owner or players who may edit it.",
                            "Keeps neighbours from corrupting each other.",
                            "MRU 聚集体只给能动它所在那一格的玩家加矩阵损伤；机器泄漏只汇进机器主人能动的聚集体（否则在机器处另凝一团），",
                            "泄漏叠的「MRU 腐化」状态只叠给机器的主人；灵魂五芒星只给主人和能动它的人记档位。免得邻居互相坑")
                    .define("respect_claims", DEFAULT_RESPECT_CLAIMS);
            mruCorruptionLethal = builder.comment(
                            "Whether the MRU Corruption status (stacked by leaking machines nearby, not a corruption effect) can kill. Off = like Poison,",
                            "it never takes the last point of health, so someone idling next to working machines does not die there.",
                            "「MRU 腐化」状态（附近机器泄漏叠上的那个，不是腐化效果）能不能打死人。关掉 = 像中毒一样最多打到剩 1 点，守着机器挂机的人不会死在那里")
                    .define("mru_corruption_lethal", DEFAULT_MRU_CORRUPTION_LETHAL);
            for (HealthCost cost : HealthCost.values()) {
                cost.value = builder.comment(
                                cost.commentEn + ". A fraction of the victim's current max health (0.075 = 7.5%). Resistance and Protection enchantments"
                                        + " reduce it as usual; armour only reduces the cactus, lightning and explosion ones (drowning, fire, magic and wither"
                                        + " damage ignore armour, as in vanilla).",
                                cost.commentZh + "。按受害者当前最大生命的比例（0.075 = 7.5%）。抗性提升、保护附魔照常减免；"
                                        + "护甲只减仙人掌、闪电、爆炸这几种（溺水、着火、魔法、凋零伤害照原版不吃护甲）")
                        .defineInRange(cost.key, cost.defaultFraction, 0D, 1D);
            }
            builder.pop();
        }
    }

    /**
     * 扣血按最大生命的比例（2026-10-13 起）：腐化效果和「MRU 腐化」状态发作时扣的血不再是原作的固定点数（1 点溺水、3 点凋零、4 点魔法……），
     * 而是受害者<b>当前最大生命</b>（{@code getMaxHealth()}，装备、属性加成都算）的一个比例。整合包里玩家的生命常常是几百上千，固定点数不疼不痒；
     * 换成比例以后，原版 20 点生命时和原作差不多（原作 1 点约合 7.5%），血再多也照样疼。伤害类型照旧，护甲、抗性提升、附魔照原版的规则起作用。
     * 行为写在 {@code corruption/CorruptionBehaviors.damageOf} 和 {@code effect/MRUCorruptionEffect.damage}。
     * <p>
     * 发作时扣多少血：受害者当前最大生命 × 比例。每一项是 general.corruption 里的一个配置（DoubleValue，0~1，0.075 = 7.5%），
     * 键名 {@code <名字>_health_percent}（照 {@code machines.mru_coil.player_health_percent} 的写法：存小数，手册用 {@code |pct} 显示）。
     * <p>
     * 默认值按原作的点数换算：原作 1 点 → 7.5%（3 点 → 22.5%、4 点 → 30%、5 点 → 37.5%），#2 双手灼伤 3 点定为 15%（身体类 0 档就治得了、
     * 挥手时 1% 的几率，按 22.5% 太频繁），「MRU 腐化」状态原作 5 点定为 25%（它从 II 级起每秒都可能挨一下）。
     * #13 自发爆炸本人那一下原作贴脸普通难度 43 点，定为 60%（爆炸伤害照原版随难度变化：简单约一半、困难 1.5 倍；温和版照样最多掉当前生命的一半）。
     * #14 浑身发烫原作只着火 8 秒，现在同时扣一次 7.5% 的着火伤害（抗火挡得住）——着火每秒 1 点在高血量下同样不疼。
     */
    public enum HealthCost {
        /** #2 双手灼伤：仙人掌伤害（原作 3 点） */
        HAND_BURN("hand_burn_health_percent", 0.15D,
                "#2 Burned hands: cactus damage each time it triggers",
                "#2 双手灼伤：每次的仙人掌伤害"),
        /** #12 / #19 / #20 末地 / 下界 / 主世界窒息：溺水伤害（原作 1 点） */
        SUFFOCATION("suffocation_health_percent", 0.075D,
                "#12 / #19 / #20 (can't breathe in the End / Nether / Overworld): drowning damage each time",
                "#12 / #19 / #20 末地 / 下界 / 主世界窒息：每次的溺水伤害"),
        /** #13 自发爆炸：站在爆心的本人那一下（原作普通难度 43 点；随难度变化，温和版最多掉当前生命的一半） */
        SELF_BLAST("self_blast_health_percent", 0.6D,
                "#13 Spontaneous explosions: damage to the player at the centre on Normal difficulty (it scales with difficulty like any explosion;"
                        + " with gentle_effects the player still loses at most half of their current health)",
                "#13 自发爆炸：站在爆心的本人在普通难度下挨的伤害（和别的爆炸一样随难度变化；开着 gentle_effects 时照样最多掉当前生命的一半）"),
        /** #14 浑身发烫：着火 8 秒的同时扣一次着火伤害（抗火挡得住） */
        BURNING("burning_health_percent", 0.075D,
                "#14 You are too hot: fire damage taken once as the player is set on fire for 8 seconds (Fire Resistance blocks it)",
                "#14 浑身发烫：着火 8 秒的同时扣一次的着火伤害（抗火能挡）"),
        /** #15 招雷体质：闪电伤害（原作 5 点），另外着火 8 秒 */
        LIGHTNING("lightning_health_percent", 0.375D,
                "#15 Lightning likes you: lightning damage (the player also burns for 8 seconds)",
                "#15 招雷体质：闪电伤害（另外着火 8 秒）"),
        /** #17 月光刺痛：满月时的魔法伤害（原作 5 点）；凸月、半月、蛾眉月按 3 / 5、2 / 5、1 / 5，新月不疼 */
        MOONLIGHT("moonlight_health_percent", 0.375D,
                "#17 The moon hurts: magic damage under a full moon; gibbous 3/5, quarter 2/5, crescent 1/5 of it, new moon none",
                "#17 月光刺痛：满月时的魔法伤害；凸月 3/5、半月 2/5、蛾眉月 1/5，新月不疼"),
        /** #18 MRU 防护失效：魔法伤害（原作 4 点） */
        MRU_FAILURE("mru_failure_health_percent", 0.3D,
                "#18 No protection against MRU: magic damage each time",
                "#18 MRU 防护失效：每次的魔法伤害"),
        /** #21 / #22 / #24 酸雨、烈日灼身、风如刀割：凋零伤害（原作 1 点） */
        WITHERING("withering_health_percent", 0.075D,
                "#21 / #22 / #24 (acid rain, burning sun, the air hurts): wither damage each time",
                "#21 / #22 / #24 酸雨、烈日灼身、风如刀割：每次的凋零伤害"),
        /** #23 水皆成酸：凋零伤害（原作 3 点） */
        ACID_WATER("acid_water_health_percent", 0.225D,
                "#23 All water is acid: wither damage each time",
                "#23 水皆成酸：每次的凋零伤害"),
        /** 「MRU 腐化」状态（机器泄漏叠的，不是腐化效果）：每次的魔法伤害，再乘 MRU 腐化易伤（原作 5 点 × 易伤）；γ 指环改成回这么多血 */
        MRU_CORRUPTION("mru_corruption_health_percent", 0.25D,
                "MRU Corruption status (stacked by leaking machines, not a corruption effect): magic damage each hit, before MRU Corruption"
                        + " resistance (armour that resists it lowers this); the gamma ring heals this much instead",
                "「MRU 腐化」状态（机器泄漏叠的，不是腐化效果）：每次的魔法伤害，再按 MRU 腐化抗性打折（穿挡 MRU 腐化的护甲会少）；戴 γ 指环时改成回这么多血");

        private final String key;
        private final double defaultFraction;
        private final String commentEn;
        private final String commentZh;
        @Nullable
        private ForgeConfigSpec.DoubleValue value;
        @Nullable
        private volatile Double testOverride;

        HealthCost(String key, double defaultFraction, String commentEn, String commentZh) {
            this.key = key;
            this.defaultFraction = defaultFraction;
            this.commentEn = commentEn;
            this.commentZh = commentZh;
        }

        /** 配置键（general.corruption 下） */
        public String key() {
            return key;
        }

        /** 默认比例 */
        public double defaultFraction() {
            return defaultFraction;
        }

        /** 现在的比例（测试覆盖值优先；配置没加载时按默认），夹在 [0, 1] */
        public double fraction() {
            Double test = testOverride;
            double raw = test != null ? test : value == null ? defaultFraction : ECConfig.get(value);
            return raw > 0D ? Math.min(1D, raw) : 0D;
        }

        /** 对 entity 扣多少：它当前的最大生命 × {@link #fraction()} */
        public float of(net.minecraft.world.entity.LivingEntity entity) {
            return amount(entity.getMaxHealth());
        }

        /** 最大生命为 maxHealth 时扣多少（不会是负数、NaN）：腐化效果的各项再乘 {@link CorruptionEffects#effectDamageMultiplier()}，一下最多扣到整条最大生命 */
        public float amount(float maxHealth) {
            double scale = this == MRU_CORRUPTION ? 1D : effectDamageMultiplier();
            double amount = Math.min((double) maxHealth, (double) maxHealth * fraction() * scale);
            return amount > 0D && Double.isFinite(amount) ? (float) amount : 0F;
        }

        /** 全部测试覆盖值置回 null（{@link CorruptionEffects#clearTestSafetyOverrides} 调） */
        static void clearTestOverrides() {
            for (HealthCost cost : values()) {
                cost.testOverride = null;
            }
        }
    }

    /**
     * GameTest 用：临时覆盖某一项扣血比例（null 恢复按配置），测完调 {@link #clearTestSafetyOverrides}。
     * 只许在同一段同步代码里设、测、清（别的测试的玩家 tick 也读它）。
     */
    public static void setTestHealthCost(HealthCost cost, @Nullable Double fraction) {
        cost.testOverride = fraction;
    }
}
