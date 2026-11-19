package com.flameforge.essentialcraft5.item.attunement;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.matrix.MatrixConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端配置 {@code general.attunement}（第十三批 13a，batch13.md §3.3.5）：同调的开关、各阶门槛、矩阵阶要求、预算、点数、每阶通用加成、
 * 对玩家的效果倍率。由 {@code registry/ECAttunement.init()} 登记。
 * <p>
 * {@code pvp_effect_scale} 是全批「对玩家减半」共用的那一个（相性、矩阵特性、定罪、回击都读它，batch13.md §3.2）。
 * <p>
 * {@code disabled_families} 的条目可以不带命名空间（{@code "computer_armor"} 当成 {@code essentialcraft5:computer_armor}，和升阶配方 JSON 的 {@code gear} 同一种写法，
 * {@link #familyId}）；认不出的家族名在配置读进来时打一次警告，不再悄悄不起作用。
 */
public final class AttunementConfig {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 从 n 阶升 n + 1 阶要攒满的同调度（第 i 项 = 第 i 阶 → 第 i + 1 阶） */
    public static final List<Integer> DEFAULT_THRESHOLDS = List.of(300, 800, 1600, 3000, 5000);
    /** 升到第 n 阶要的矩阵阶（第 i 项 = 升到第 i + 1 阶；默认 2n − 1） */
    public static final List<Integer> DEFAULT_RANK_GATE = List.of(1, 3, 5, 7, 9);

    public static ForgeConfigSpec.BooleanValue enabled;
    public static ForgeConfigSpec.ConfigValue<List<? extends Number>> thresholds;
    public static ForgeConfigSpec.ConfigValue<List<? extends Number>> rankGate;
    public static ForgeConfigSpec.IntValue budgetPerHour;
    public static ForgeConfigSpec.IntValue budgetCap;
    public static ForgeConfigSpec.IntValue pointsHit;
    public static ForgeConfigSpec.IntValue pointsBossHit;
    public static ForgeConfigSpec.IntValue pointsKill;
    public static ForgeConfigSpec.IntValue pointsBossKill;
    public static ForgeConfigSpec.IntValue pointsPvp;
    public static ForgeConfigSpec.DoubleValue healthPerStage;
    public static ForgeConfigSpec.DoubleValue damagePerStage;
    public static ForgeConfigSpec.DoubleValue pvpEffectScale;
    public static ForgeConfigSpec.ConfigValue<List<? extends String>> disabledFamilies;

    private AttunementConfig() {
    }

    public static void define(ForgeConfigSpec.Builder builder) {
        builder.comment("Attunement: armor and weapons grow stages through real fights.", "同调：盔甲和武器在实战里长阶").push("attunement");
        enabled = builder.comment("Attunement on. When off, no gear gains attunement points and no stage-up recipe gives a result; stages already reached keep their effects.",
                        "同调总开关。关掉时装备不再攒同调度、升阶配方不出产物；已有的阶效果照常")
                .define("enabled", true);
        thresholds = builder.comment("Attunement points needed to go from stage n to stage n + 1 (one entry per stage).",
                        "从 n 阶升 n + 1 阶要攒满的同调度（每阶一项）")
                .defineList("thresholds", DEFAULT_THRESHOLDS, o -> o instanceof Number n && n.intValue() > 0);
        rankGate = builder.comment("Matrix rank needed for each stage (entry n = stage n + 1; 0 = no requirement). Ignored while matrix ranks are off.",
                        "升到每一阶要的矩阵阶（第 n 项 = 升到 n + 1 阶；0 = 不要求）。矩阵阶关掉时不看")
                .defineList("rank_gate", DEFAULT_RANK_GATE, o -> o instanceof Number n && n.intValue() >= 0 && n.intValue() <= 9);
        budgetPerHour = builder.comment("Attunement budget gained per hour online, for armor and weapons separately. The budget only limits how fast points come in, not where they come from.",
                        "每在线一小时攒多少同调预算（盔甲、武器各一份）。预算只限速，不限来源")
                .defineInRange("budget_per_hour", 360, 0, 100_000);
        budgetCap = builder.comment("Most budget that can be banked (720 = about two hours).", "预算最多存多少（720 约等于两小时）")
                .defineInRange("budget_cap", 720, 0, 1_000_000);
        pointsHit = builder.comment("Points for armor taking a hit, or a weapon landing a hit.", "盔甲挨一下、武器打实一下给几点")
                .defineInRange("points_hit", 1, 0, 10_000);
        pointsBossHit = builder.comment("Points for armor taking a hit from a boss.", "盔甲挨首领一下给几点")
                .defineInRange("points_boss_hit", 3, 0, 10_000);
        pointsKill = builder.comment("Extra points for a weapon landing the kill.", "武器击杀另给几点")
                .defineInRange("points_kill", 2, 0, 10_000);
        pointsBossKill = builder.comment("Extra points for a weapon killing a boss.", "武器击杀首领另给几点")
                .defineInRange("points_boss_kill", 60, 0, 100_000);
        pointsPvp = builder.comment("Points when the other side is a player (0 = fights between players give nothing).",
                        "对面是玩家时给几点（0 = 玩家之间打不攒）")
                .defineInRange("points_pvp", 0, 0, 10_000);
        healthPerStage = builder.comment("No longer read: replaced by general.gear_growth.armor.health_per_stage.",
                        "已由 general.gear_growth.armor.health_per_stage 接管，不再读")
                .defineInRange("health_per_stage", 1D, 0D, 20D);
        damagePerStage = builder.comment("No longer read: replaced by general.gear_growth.weapon.damage_per_stage (now a damage bonus, not an attribute).",
                        "已由 general.gear_growth.weapon.damage_per_stage 接管（改成增伤池，不再是属性倍率），不再读")
                .defineInRange("damage_per_stage", 0.03D, 0D, 1D);
        pvpEffectScale = builder.comment("Scale on durations, drains and bonus percentages of matrix affinities, matrix traits and attunement effects when the target is a player.",
                        "相性、矩阵特性、同调效果打在玩家身上时，时长、吸取和增伤百分比乘的倍数")
                .defineInRange("pvp_effect_scale", 0.5D, 0D, 1D);
        disabledFamilies = builder.comment("Attunement families turned off (for example \"essentialcraft5:computer_armor\", or just \"computer_armor\" for this mod's families): they gain no points and their stage-up recipes give nothing; stages already reached keep their effects.",
                        "关掉的同调家族（例 \"essentialcraft5:computer_armor\"，本模组的家族也可以只写 \"computer_armor\"）：不攒点、升阶配方不出产物；已有的阶效果照常")
                .defineListAllowEmpty("disabled_families", List.of(), o -> o instanceof String s && ResourceLocation.tryParse(s) != null);
        builder.pop();
    }

    /** 总开关（测试覆盖优先） */
    public static boolean enabled() {
        Boolean on = testEnabled;
        return on != null ? on : ECConfig.get(enabled);
    }

    /** 各阶门槛（TOML 里的整数可能读成 Long，这里统一转成 int） */
    public static int[] thresholds() {
        return toInts(thresholds == null ? DEFAULT_THRESHOLDS : ECConfig.getValue(thresholds));
    }

    /** 各阶要的矩阵阶 */
    public static int[] rankGate() {
        return toInts(rankGate == null ? DEFAULT_RANK_GATE : ECConfig.getValue(rankGate));
    }

    private static int[] toInts(List<? extends Number> values) {
        int[] out = new int[values.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = values.get(i).intValue();
        }
        return out;
    }

    public static int budgetPerHour() {
        return ECConfig.get(budgetPerHour);
    }

    public static int budgetCap() {
        return ECConfig.get(budgetCap);
    }

    public static int pointsHit() {
        return ECConfig.get(pointsHit);
    }

    public static int pointsBossHit() {
        return ECConfig.get(pointsBossHit);
    }

    public static int pointsKill() {
        return ECConfig.get(pointsKill);
    }

    public static int pointsBossKill() {
        return ECConfig.get(pointsBossKill);
    }

    public static int pointsPvp() {
        return ECConfig.get(pointsPvp);
    }

    /** 盔甲每阶每件的最大生命：读 {@code general.gear_growth.armor.health_per_stage}（老键 {@code attunement.health_per_stage} 不再读） */
    public static double healthPerStage() {
        return GearGrowthConfig.armorHealthPerStage();
    }

    public static double pvpEffectScale() {
        Double scale = testPvpEffectScale;
        return scale != null ? scale : ECConfig.get(pvpEffectScale);
    }

    public static List<? extends String> disabledFamilies() {
        return ECConfig.getValue(disabledFamilies);
    }

    // ------------------------------------------------------------------
    // 派生的读法
    // ------------------------------------------------------------------

    /**
     * 从 stage 阶升下一阶要攒满的同调度。配置的列表比阶数短时，后面的阶沿用最后一项；列表是空的按默认表。
     */
    public static int threshold(int stage) {
        int[] values = thresholds();
        if (values.length == 0) {
            values = toInts(DEFAULT_THRESHOLDS);
        }
        return Math.max(1, values[Math.max(0, Math.min(stage, values.length - 1))]);
    }

    /** 0 阶累计到 stage 阶一共要多少同调度（各阶门槛之和） */
    public static int cumulative(int stage) {
        long sum = 0;
        for (int i = 0; i < stage; i++) {
            sum += threshold(i);
        }
        return (int) Math.min(Integer.MAX_VALUE, sum);
    }

    /**
     * 升到 toStage 阶要的矩阵阶（读物品上的阶戳比）。矩阵阶整套关掉（{@code general.matrix_rank.enabled = false}）时不设门，返回 0；
     * 列表比阶数短时沿用最后一项。
     */
    public static int requiredRank(int toStage) {
        if (toStage <= 0 || !MatrixConfig.enabled()) {
            return 0;
        }
        int[] values = rankGate();
        if (values.length == 0) {
            return 0;
        }
        return Math.max(0, values[Math.min(toStage - 1, values.length - 1)]);
    }

    /** 同调总开关开着、这个家族没被关掉 */
    public static boolean familyActive(ResourceLocation family) {
        if (!enabled()) {
            return false;
        }
        Set<ResourceLocation> off = testDisabled;
        if (off != null) {
            return !off.contains(family);
        }
        return !disabledIds().contains(family);
    }

    /**
     * 家族名：不带命名空间时按本模组（{@code "computer_armor"} → {@code essentialcraft5:computer_armor}）；写得不对返回 null。
     * 配置的 {@code disabled_families} 和升阶配方 JSON 的 {@code gear} 都用它
     */
    @Nullable
    public static ResourceLocation familyId(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        return ResourceLocation.tryParse(trimmed.indexOf(':') >= 0 ? trimmed : EssentialCraft5.MODID + ":" + trimmed);
    }

    /** 把 disabled_families 的条目解析成家族 id（补命名空间）；认不出的家族各打一次警告 */
    public static Set<ResourceLocation> parseDisabled(List<? extends String> entries) {
        Set<ResourceLocation> ids = new HashSet<>();
        for (String entry : entries) {
            ResourceLocation id = familyId(entry);
            if (id == null) {
                continue;
            }
            ids.add(id);
            if (AttunementFamily.byId(id) == null && WARNED.add(id)) {
                LOGGER.warn("general.attunement.disabled_families: no attunement family named {} (entry \"{}\"), it has no effect", id, entry);
            }
        }
        return Set.copyOf(ids);
    }

    private static final Set<ResourceLocation> WARNED = ConcurrentHashMap.newKeySet();
    @Nullable
    private static volatile List<? extends String> disabledSource;
    private static volatile Set<ResourceLocation> disabledParsed = Set.of();

    /** 配置里关掉的家族（列表对象换了，也就是配置重载过，才重新解析） */
    private static Set<ResourceLocation> disabledIds() {
        List<? extends String> entries = disabledFamilies();
        if (entries != disabledSource) {
            disabledParsed = parseDisabled(entries);
            disabledSource = entries;
        }
        return disabledParsed;
    }

    // ------------------------------------------------------------------
    // GameTest 用的覆盖值（服务端配置会自动存盘、文件监视器会把 set 进去的值冲掉，测试不改真配置）
    // ------------------------------------------------------------------

    @Nullable
    private static volatile Boolean testEnabled;
    @Nullable
    private static volatile Set<ResourceLocation> testDisabled;
    @Nullable
    private static volatile Double testPvpEffectScale;

    /**
     * 测试覆盖：总开关、关掉的家族（null = 照配置）。用完调 {@link #clearTestOverrides}。
     */
    public static void setTestOverrides(@Nullable Boolean enabled, @Nullable Collection<ResourceLocation> disabled) {
        testEnabled = enabled;
        testDisabled = disabled == null ? null : Set.copyOf(disabled);
    }

    /** 测试覆盖：关掉的家族按配置条目的写法给（走和配置一样的解析，短名补命名空间）。用完调 {@link #clearTestOverrides} */
    public static void setTestDisabledEntries(List<String> entries) {
        testDisabled = parseDisabled(entries);
    }

    /** 测试覆盖：{@code pvp_effect_scale}（null = 照配置）。用完调 {@link #clearTestOverrides} */
    public static void setTestPvpEffectScale(@Nullable Double scale) {
        testPvpEffectScale = scale;
    }

    public static void clearTestOverrides() {
        testEnabled = null;
        testDisabled = null;
        testPvpEffectScale = null;
    }
}
