package com.flameforge.essentialcraft5.item.module;

import com.flameforge.essentialcraft5.config.ECConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 服务端配置 {@code general.computer_modules}（模块平台，batch10.md §4.3 第 10 条），随进服同步给客户端（提示行、终端用同一份数）。
 * <ul>
 *   <li>{@code disabled}：模块注册名列表（默认空，写法同 {@code general.baubles.disabled}）。列进去的模块装着也不生效、不耗电，手册里对应的说明篇藏掉
 *       （条件 {@code module:<注册名>}）；新的装不上（装上只会白占一格，{@link ArmorModules#check}），已经装着的照常能拆（不能把格子锁死）。</li>
 *   <li>{@code slots_helmet} / {@code slots_chestplate} / {@code slots_leggings} / {@code slots_boots}：每件宅男盔甲的基础模块槽（0~4，默认 2 / 2 / 1 / 1，D10 A）。
 *       第十三批起再加这一件的同调阶给的（胸甲 1 阶、头盔 / 护腿 3 阶、靴子 5 阶各 +1，{@code item/ComputerAttunement}），合计仍封顶 {@value #MAX_SLOTS}。
 *       改小以后已经装着的照留，排在格数以外的不生效（终端写「超出格数」），能拆。</li>
 *   <li>{@code require_full_set}：要穿齐四件模块才生效（默认 true；关掉时穿着哪件，哪件上的模块就生效）。</li>
 * </ul>
 * 各模块自己的小节（{@code general.computer_modules.rewind} 之类）用 {@link #section} 登记，写在这一节里面。
 * GameTest 用 {@link #setTestOverrides}，不碰真配置。
 */
public final class ModuleConfig {

    /** 每件最多几个模块槽（配置上限，也是终端包、配方的上限） */
    public static final int MAX_SLOTS = 4;

    /**
     * 一套数值。
     *
     * @param disabled       被关掉的模块（注册名）
     * @param slotsHelmet    头盔的模块槽
     * @param slotsChest     胸甲的模块槽
     * @param slotsLegs      护腿的模块槽
     * @param slotsBoots     靴子的模块槽
     * @param requireFullSet 要不要穿齐四件
     */
    public record Values(Set<ResourceLocation> disabled, int slotsHelmet, int slotsChest, int slotsLegs, int slotsBoots, boolean requireFullSet) {

        public Values {
            disabled = Set.copyOf(disabled);
        }

        /** 这个部位的宅男盔甲有几个模块槽 */
        public int slots(ArmorItem.Type type) {
            return switch (type) {
                case HELMET -> slotsHelmet;
                case CHESTPLATE -> slotsChest;
                case LEGGINGS -> slotsLegs;
                case BOOTS -> slotsBoots;
            };
        }

        public boolean isDisabled(ResourceLocation id) {
            return disabled.contains(id);
        }
    }

    /** 默认值：不关任何模块，头 2 / 胸 2 / 腿 1 / 靴 1，要穿齐四件 */
    public static final Values DEFAULTS = new Values(Set.of(), 2, 2, 1, 1, true);

    private static final List<Consumer<ForgeConfigSpec.Builder>> SECTIONS = new ArrayList<>();

    @Nullable
    private static ForgeConfigSpec.ConfigValue<List<? extends String>> disabled;
    @Nullable
    private static ForgeConfigSpec.IntValue slotsHelmet;
    @Nullable
    private static ForgeConfigSpec.IntValue slotsChest;
    @Nullable
    private static ForgeConfigSpec.IntValue slotsLegs;
    @Nullable
    private static ForgeConfigSpec.IntValue slotsBoots;
    @Nullable
    private static ForgeConfigSpec.BooleanValue requireFullSet;
    @Nullable
    private static volatile Values testOverride;
    /** 配置重载、测试覆盖值改了时加一，{@link ModuleCache} 的旧快照随之作废（配置事件可能在别的线程发，只动这个计数） */
    private static volatile int generation;

    @Nullable
    private static List<? extends String> cachedList;
    private static Set<ResourceLocation> cachedSet = Set.of();

    private ModuleConfig() {
    }

    /**
     * 各模块登记自己的配置小节（模块注册类的 {@code init()} 里调，必须在 ECConfig 建规格之前）：
     * consumer 里自己 {@code push("rewind")} / {@code pop()}，写出来就是 {@code general.computer_modules.rewind.*}。
     */
    public static void section(Consumer<ForgeConfigSpec.Builder> consumer) {
        SECTIONS.add(consumer);
    }

    /** ECModules.init 里经 {@code ECConfig.general} 登记 */
    public static void define(ForgeConfigSpec.Builder builder) {
        builder.comment("Modules for the Computer Guy's Suit (terminal chapter).", "宅男套装的模块（终端与模块一章）").push("computer_modules");
        disabled = builder.comment(
                        "Module ids listed here cannot be installed, and have no effect and cost no MRU while already installed; they can still be removed.",
                        "e.g. [\"essentialcraft5:rewind_module\"] on PvP servers.",
                        "列在这里的模块（注册名）装不上；已经装着的不生效、不耗电，照常能拆。PvP 服可以关回溯，例 [\"essentialcraft5:rewind_module\"]")
                .defineListAllowEmpty("disabled", List.of(), o -> o instanceof String s && ResourceLocation.tryParse(s) != null);
        slotsHelmet = builder.comment("Module slots of the helmet.", "头盔的模块槽")
                .defineInRange("slots_helmet", DEFAULTS.slotsHelmet(), 0, MAX_SLOTS);
        slotsChest = builder.comment("Module slots of the chestplate.", "胸甲的模块槽")
                .defineInRange("slots_chestplate", DEFAULTS.slotsChest(), 0, MAX_SLOTS);
        slotsLegs = builder.comment("Module slots of the leggings.", "护腿的模块槽")
                .defineInRange("slots_leggings", DEFAULTS.slotsLegs(), 0, MAX_SLOTS);
        slotsBoots = builder.comment("Module slots of the boots.", "靴子的模块槽")
                .defineInRange("slots_boots", DEFAULTS.slotsBoots(), 0, MAX_SLOTS);
        requireFullSet = builder.comment(
                        "Whether modules only work while all four pieces of the suit are worn (otherwise each worn piece's modules work on their own).",
                        "是否要穿齐四件模块才生效（关掉时穿着哪件，哪件上的模块就生效）")
                .define("require_full_set", DEFAULTS.requireFullSet());
        for (Consumer<ForgeConfigSpec.Builder> section : SECTIONS) {
            section.accept(builder);
        }
        builder.pop();
    }

    /** 现在的数值：测试覆盖值 → 配置（没加载时默认值） */
    public static Values values() {
        Values override = testOverride;
        if (override != null) {
            return override;
        }
        if (slotsHelmet == null) {
            return DEFAULTS;
        }
        return new Values(disabledIds(), ECConfig.get(slotsHelmet), ECConfig.get(slotsChest), ECConfig.get(slotsLegs), ECConfig.get(slotsBoots),
                ECConfig.get(requireFullSet));
    }

    /** 这块模块被关掉了 */
    public static boolean isDisabled(ResourceLocation id) {
        return values().isDisabled(id);
    }

    /** 快照代数（见 {@link #generation}） */
    static int generation() {
        return generation;
    }

    /** 配置加载 / 重载时调（模组总线，见 ModuleEvents.ConfigBus） */
    static void bump() {
        generation++;
    }

    private static synchronized Set<ResourceLocation> disabledIds() {
        if (disabled == null) {
            return Set.of();
        }
        List<? extends String> list;
        try {
            list = disabled.get();
        } catch (IllegalStateException e) {
            list = disabled.getDefault();
        }
        if (list != cachedList) {
            Set<ResourceLocation> set = new HashSet<>();
            for (String s : list) {
                ResourceLocation id = ResourceLocation.tryParse(s);
                if (id != null) {
                    set.add(id);
                }
            }
            cachedList = list;
            cachedSet = Set.copyOf(set);
        }
        return cachedSet;
    }

    /** GameTest 用：整套换成给定的数值，null = 回到配置。同一段同步代码里设、测、置回 */
    public static void setTestOverrides(@Nullable Values values) {
        testOverride = values;
        generation++;
    }
}
