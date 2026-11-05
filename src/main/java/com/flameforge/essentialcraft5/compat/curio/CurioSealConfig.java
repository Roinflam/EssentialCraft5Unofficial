package com.flameforge.essentialcraft5.compat.curio;

import com.flameforge.essentialcraft5.config.ECConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 服务端配置 {@code general.computer_seal}（宅男套装的饰品封禁场，tools/plan/batch10.md §5.1.5，13 项），由 {@code ECComputer.init} 登记。
 * 压制核心只读 {@link Values#enabled()} 和 {@link Values#minHoldTicks()}，其余给封禁场（评估、门、扣电、提示，item/ComputerSealField）用。
 * SERVER 配置随进服同步给客户端（终端、手册条件读得到）。GameTest 用 {@link #setTestOverrides}，不碰真配置。
 */
public final class CurioSealConfig {

    /**
     * 一套数值。
     *
     * @param enabled          整个功能开关（手册按它藏整篇）
     * @param radius           半径（格）；已在场内的人超过半径 + 2 才算离开
     * @param mruBase          运转时每秒基础花费
     * @param mruPerTarget     每压制一名别的玩家每秒加价
     * @param affectsSelf      运转时穿戴者自己的饰品是否一起失效
     * @param respectClaims    是否以穿戴者身份探测目标脚下那格（领地、出生点保护）
     * @param slowFallTicks    压制开始时人在空中给的缓降（刻），0 = 不给
     * @param minHoldTicks     被压制后最少保持多久（刻）
     * @param evaluateInterval 每名穿戴者多少刻评估一次
     * @param maxProbesPerTick 每名穿戴者每次评估最多新探测几格（键名沿用 max_probes_per_tick）
     * @param requireRecentCombat 只压制最近和穿戴者交过手的玩家（两人之间有过一下被放行的伤害，谁打谁都算）
     * @param combatWindowTicks  交手记录的有效期（刻）
     * @param disabledDimensions 场不起作用的维度：带冒号的按完整 id 比，不带冒号的按路径比（{@link #disabledIn}）
     */
    public record Values(boolean enabled, int radius, int mruBase, int mruPerTarget, boolean affectsSelf, boolean respectClaims,
                         int slowFallTicks, int minHoldTicks, int evaluateInterval, int maxProbesPerTick,
                         boolean requireRecentCombat, int combatWindowTicks, List<String> disabledDimensions) {

        public Values {
            disabledDimensions = List.copyOf(disabledDimensions);
        }

        /** 拿这一套改几项（GameTest 用） */
        public Builder toBuilder() {
            return new Builder(this);
        }

        /**
         * 这个维度里场不起作用。名单里带冒号的按完整 id 比（{@code minecraft:the_nether}），不带的按路径比，不管命名空间：
         * 混合端上插件建的世界注册成 {@code minecraft:<小写世界名>}，别的服务端也可能用别的命名空间，只写世界名就都对得上。
         */
        public boolean disabledIn(ResourceKey<Level> dimension) {
            if (disabledDimensions.isEmpty()) {
                return false;
            }
            ResourceLocation id = dimension.location();
            String full = id.toString();
            for (String entry : disabledDimensions) {
                String name = entry.trim().toLowerCase(Locale.ROOT);
                if (name.indexOf(':') >= 0 ? name.equals(full) : name.equals(id.getPath())) {
                    return true;
                }
            }
            return false;
        }
    }

    /** {@link Values} 的改写器（GameTest 只改要测的几项） */
    public static final class Builder {
        private boolean enabled;
        private int radius;
        private int mruBase;
        private int mruPerTarget;
        private boolean affectsSelf;
        private boolean respectClaims;
        private int slowFallTicks;
        private int minHoldTicks;
        private int evaluateInterval;
        private int maxProbesPerTick;
        private boolean requireRecentCombat;
        private int combatWindowTicks;
        private List<String> disabledDimensions;

        private Builder(Values v) {
            enabled = v.enabled();
            radius = v.radius();
            mruBase = v.mruBase();
            mruPerTarget = v.mruPerTarget();
            affectsSelf = v.affectsSelf();
            respectClaims = v.respectClaims();
            slowFallTicks = v.slowFallTicks();
            minHoldTicks = v.minHoldTicks();
            evaluateInterval = v.evaluateInterval();
            maxProbesPerTick = v.maxProbesPerTick();
            requireRecentCombat = v.requireRecentCombat();
            combatWindowTicks = v.combatWindowTicks();
            disabledDimensions = v.disabledDimensions();
        }

        public Builder enabled(boolean value) {
            enabled = value;
            return this;
        }

        public Builder radius(int value) {
            radius = value;
            return this;
        }

        public Builder mru(int base, int perTarget) {
            mruBase = base;
            mruPerTarget = perTarget;
            return this;
        }

        public Builder affectsSelf(boolean value) {
            affectsSelf = value;
            return this;
        }

        public Builder respectClaims(boolean value) {
            respectClaims = value;
            return this;
        }

        public Builder slowFallTicks(int value) {
            slowFallTicks = value;
            return this;
        }

        public Builder minHoldTicks(int value) {
            minHoldTicks = value;
            return this;
        }

        public Builder evaluateInterval(int value) {
            evaluateInterval = value;
            return this;
        }

        public Builder maxProbesPerTick(int value) {
            maxProbesPerTick = value;
            return this;
        }

        public Builder requireRecentCombat(boolean value) {
            requireRecentCombat = value;
            return this;
        }

        public Builder combatWindowTicks(int value) {
            combatWindowTicks = value;
            return this;
        }

        public Builder disabledDimensions(String... values) {
            disabledDimensions = List.of(values);
            return this;
        }

        public Values build() {
            return new Values(enabled, radius, mruBase, mruPerTarget, affectsSelf, respectClaims, slowFallTicks, minHoldTicks,
                    evaluateInterval, maxProbesPerTick, requireRecentCombat, combatWindowTicks, disabledDimensions);
        }
    }

    /**
     * 默认值（batch10.md §5.1.5）：32 格、每秒 200 + 每人 200 MRU、自己也失效、看领地、缓降 8 秒、最少保持 3 秒、每秒评估一次、每次评估最多新探测 4 格、
     * 不要求交过手（开着时窗口 10 秒）、名叫 zc 的维度里不起作用
     */
    public static final Values DEFAULTS = new Values(true, 32, 200, 200, true, true, 160, 60, 20, 4, false, 200, List.of("zc"));

    private static ForgeConfigSpec.BooleanValue enabled;
    private static ForgeConfigSpec.IntValue radius;
    private static ForgeConfigSpec.IntValue mruBase;
    private static ForgeConfigSpec.IntValue mruPerTarget;
    private static ForgeConfigSpec.BooleanValue affectsSelf;
    private static ForgeConfigSpec.BooleanValue respectClaims;
    private static ForgeConfigSpec.IntValue slowFallTicks;
    private static ForgeConfigSpec.IntValue minHoldTicks;
    private static ForgeConfigSpec.IntValue evaluateInterval;
    private static ForgeConfigSpec.IntValue maxProbesPerTick;
    private static ForgeConfigSpec.BooleanValue requireRecentCombat;
    private static ForgeConfigSpec.IntValue combatWindowTicks;
    private static ForgeConfigSpec.ConfigValue<List<? extends String>> disabledDimensions;
    @Nullable
    private static volatile Values testOverride;

    private CurioSealConfig() {
    }

    public static void define(ForgeConfigSpec.Builder builder) {
        builder.comment("Curio Seal Field of the Computer Guy's Suit: while running, Curios items of players in range have no effect.",
                "宅男套装的饰品封禁场：运转时范围内玩家饰品栏里的东西全部失效").push("computer_seal");
        enabled = builder.comment("Whether the Curio Seal Field exists at all.", "整个功能开关")
                .define("enabled", DEFAULTS.enabled());
        radius = builder.comment("Radius in blocks (sphere, same dimension); a player already inside leaves at radius + 2.",
                        "半径（格，球形、同一维度）；已在场内的人超过半径 + 2 才算离开")
                .defineInRange("radius", DEFAULTS.radius(), 8, 64);
        mruBase = builder.comment("MRU per second while the field is running.", "运转时每秒的基础花费（MRU）")
                .defineInRange("mru_base", DEFAULTS.mruBase(), 0, 1_000_000);
        mruPerTarget = builder.comment("Extra MRU per second for each other player sealed.", "每压制一名别的玩家，每秒加多少 MRU")
                .defineInRange("mru_per_target", DEFAULTS.mruPerTarget(), 0, 1_000_000);
        affectsSelf = builder.comment("Whether the wearer's own Curios items are sealed too while the field is running.",
                        "运转时穿戴者自己的饰品是否一起失效")
                .define("affects_self", DEFAULTS.affectsSelf());
        respectClaims = builder.comment("Whether a player is only sealed where the wearer could break blocks (claims, spawn protection).",
                        "是否以穿戴者身份探测目标脚下那格（领地、出生点保护），探测不过就不压制")
                .define("respect_claims", DEFAULTS.respectClaims());
        slowFallTicks = builder.comment("Slow falling given to a player who is in the air when sealed, in ticks (0 = none).",
                        "压制开始时人在空中给的缓降（刻），0 = 不给")
                .defineInRange("slow_fall_ticks", DEFAULTS.slowFallTicks(), 0, 1200);
        minHoldTicks = builder.comment("Minimum time a player stays sealed, in ticks.", "被压制后最少保持多久（刻）")
                .defineInRange("min_hold_ticks", DEFAULTS.minHoldTicks(), 20, 1200);
        evaluateInterval = builder.comment("How often each wearer's field is evaluated, in ticks.", "每名穿戴者多少刻评估一次")
                .defineInRange("evaluate_interval", DEFAULTS.evaluateInterval(), 10, 100);
        maxProbesPerTick = builder.comment("Claim probes a wearer may start per evaluation (new positions only; results are cached).",
                        "每名穿戴者每次评估最多新探测几格（只算新格子，结果有缓存）")
                .defineInRange("max_probes_per_tick", DEFAULTS.maxProbesPerTick(), 1, 64);
        requireRecentCombat = builder.comment(
                        "Only seal players who recently fought the wearer: one hit between the two (either way) that the server let through. "
                                + "PvP protection that cancels damage events (both Forge mods and plugins on hybrid servers) cannot be asked without hurting someone, so this is how it is respected.",
                        "只压制最近和穿戴者交过手的玩家：两人之间（谁打谁都算）有过一下被服务器放行的伤害。靠取消伤害事件做的 PvP 保护（Forge 模组、混合端插件都是）不伤人就问不到，靠这一条照顾到")
                .define("require_recent_combat", DEFAULTS.requireRecentCombat());
        combatWindowTicks = builder.comment("How long a hit between the two counts as \"recently fought\", in ticks.",
                        "一下交手记录的有效期（刻）")
                .defineInRange("combat_window_ticks", DEFAULTS.combatWindowTicks(), 20, 1200);
        disabledDimensions = builder.comment(
                        "Dimensions where the field does nothing: no one is sealed and no power is used. "
                                + "An entry with a colon matches that exact dimension id; one without matches any dimension with that path (\"zc\" matches minecraft:zc).",
                        "封禁场不起作用的维度：谁都不压制、也不耗电。带冒号的按完整 id 比，不带冒号的按路径比（\"zc\" 对得上 minecraft:zc）")
                .defineListAllowEmpty("disabled_dimensions", DEFAULTS.disabledDimensions(), o -> o instanceof String str && !str.isBlank());
        builder.pop();
    }

    /** 现在的数值：测试覆盖值 → 配置（没加载时默认值） */
    public static Values values() {
        Values override = testOverride;
        if (override != null) {
            return override;
        }
        if (enabled == null) {
            return DEFAULTS;
        }
        return new Values(ECConfig.get(enabled), ECConfig.get(radius), ECConfig.get(mruBase), ECConfig.get(mruPerTarget),
                ECConfig.get(affectsSelf), ECConfig.get(respectClaims), ECConfig.get(slowFallTicks), ECConfig.get(minHoldTicks),
                ECConfig.get(evaluateInterval), ECConfig.get(maxProbesPerTick), ECConfig.get(requireRecentCombat), ECConfig.get(combatWindowTicks),
                dimensionList());
    }

    /** 维度名单（ECConfig.get 没有列表的重载：没加载时用默认值） */
    private static List<String> dimensionList() {
        boolean loaded = ECConfig.spec() != null && ECConfig.spec().isLoaded();
        return new ArrayList<>(loaded ? disabledDimensions.get() : disabledDimensions.getDefault());
    }

    /** GameTest 用：整套换成给定的数值，null = 回到配置。同一段同步代码里设、测、置回（跨刻的测试在最后一步置回） */
    public static void setTestOverrides(@Nullable Values values) {
        testOverride = values;
    }
}
