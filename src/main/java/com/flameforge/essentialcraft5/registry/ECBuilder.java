package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.BuilderMode;
import com.flameforge.essentialcraft5.item.MagicalBuilderItem;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 第六批 6b M1「建筑魔杖」（计划 tools/plan/batch6b.md §4.1）的注册类：5 个模式物品
 * {@code magical_builder_{fill,clear,clear_matching,clear_others,replace_all}}（原作 magicalBuilder meta 0~4；
 * meta 2 / 3 原作叫「替换」、实际只清除，注册名改成 clear_matching / clear_others）。
 * 换模式的配方用 {@code essentialcraft5:crafting_shapeless_keep_nbt}（{@link ECRecipes#CRAFTING_SHAPELESS_KEEP_NBT}），MRU、两个点、记下的方块都留着。
 * <p>
 * 物品 {@code item/MagicalBuilderItem}（选点、选定方块、开工 / 停下），分帧施工 {@code item/BuilderJobs}，
 * 选区与 NBT {@code item/BuilderSelection}，模式与每格的规则 {@code item/BuilderMode}，客户端选区框和物品角标 {@code client/BuilderClient}。
 * 文字 tools/lang/keys/ecbuilder.json，标签 tools/tags/ecbuilder.json（{@code essentialcraft5:builder_blacklist}，默认空），
 * 配方 data/essentialcraft5/recipes/builder/（主配方 + 5 条换模式），手册进度 advancements/guide/void_machines/magical_builder.json。
 * 配置在 SERVER 配置 {@code general.builder}（{@link Config}）。
 */
public final class ECBuilder {

    /** 方块标签：建筑魔杖不选它、不拆它、不换它（整合包里别的模组的特殊方块往这里加） */
    public static final TagKey<Block> BUILDER_BLACKLIST = ECTags.modBlock("builder_blacklist");

    public static final RegistryObject<MagicalBuilderItem> MAGICAL_BUILDER_FILL = wand(BuilderMode.FILL);
    public static final RegistryObject<MagicalBuilderItem> MAGICAL_BUILDER_CLEAR = wand(BuilderMode.CLEAR);
    public static final RegistryObject<MagicalBuilderItem> MAGICAL_BUILDER_CLEAR_MATCHING = wand(BuilderMode.CLEAR_MATCHING);
    public static final RegistryObject<MagicalBuilderItem> MAGICAL_BUILDER_CLEAR_OTHERS = wand(BuilderMode.CLEAR_OTHERS);
    public static final RegistryObject<MagicalBuilderItem> MAGICAL_BUILDER_REPLACE_ALL = wand(BuilderMode.REPLACE_ALL);

    /** 按 {@link BuilderMode} 的顺序 */
    public static final List<RegistryObject<MagicalBuilderItem>> WANDS = List.of(MAGICAL_BUILDER_FILL, MAGICAL_BUILDER_CLEAR,
            MAGICAL_BUILDER_CLEAR_MATCHING, MAGICAL_BUILDER_CLEAR_OTHERS, MAGICAL_BUILDER_REPLACE_ALL);

    private ECBuilder() {
    }

    public static void init() {
        ECConfig.general(Config::define);
    }

    /** 这个模式的魔杖物品 */
    public static MagicalBuilderItem item(BuilderMode mode) {
        return WANDS.get(mode.ordinal()).get();
    }

    private static RegistryObject<MagicalBuilderItem> wand(BuilderMode mode) {
        // 5 种外观相同（同原作，都是 magicalbuilder 一个模型）
        return ECItems.register("magical_builder_" + mode.id(),
                () -> new MagicalBuilderItem(new Item.Properties().stacksTo(1), mode), ItemSpec.handheld("item/spells/staff_void"));
    }

    /**
     * SERVER 配置 {@code general.builder}：一次能改多大、多远能用、每刻做几格。原作的花费（25 / 250 / 300）写死在 {@link BuilderMode}。
     * 读值用下面的静态方法，配置没加载时返回默认值；GameTest 用 {@link #setTestOverrides}（施工跨好几刻，在 batch 的 @BeforeBatch 设、@AfterBatch 置回 null）。
     */
    public static final class Config {
        public static final int DEFAULT_MAX_VOLUME = 4096;
        public static final int DEFAULT_MAX_POINT_DISTANCE = 48;
        public static final int DEFAULT_MAX_REACH = 64;
        public static final int DEFAULT_CELLS_PER_TICK = 64;
        public static final int DEFAULT_MAX_CELLS_PER_TICK_TOTAL = 256;

        private static ForgeConfigSpec.IntValue maxVolume;
        private static ForgeConfigSpec.IntValue maxPointDistance;
        private static ForgeConfigSpec.IntValue maxReach;
        private static ForgeConfigSpec.IntValue cellsPerTick;
        private static ForgeConfigSpec.IntValue maxCellsPerTickTotal;

        @Nullable
        private static Integer testMaxReach;
        @Nullable
        private static Integer testCellsPerTick;
        @Nullable
        private static Integer testMaxCellsPerTickTotal;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.comment("Magical Building Wand.", "建筑魔杖").push("builder");
            maxVolume = builder.comment("Largest selection in blocks (the original had no limit, up to about 22000 at once).",
                            "选区最多几格（原作不限，最大约 2.2 万格、一刻做完）")
                    .defineInRange("max_volume", DEFAULT_MAX_VOLUME, 1, 32768);
            maxPointDistance = builder.comment("Largest straight-line distance between the two points, in blocks (the original's value).",
                            "两个点的直线距离上限（格，原作的值）")
                    .defineInRange("max_point_distance", DEFAULT_MAX_POINT_DISTANCE, 1, 256);
            maxReach = builder.comment("How close the player must stay to the selection, in blocks; walking farther away stops the work.",
                            "玩家离选区多远以内才能开工（格），施工途中走远了就停")
                    .defineInRange("max_reach", DEFAULT_MAX_REACH, 1, 256);
            cellsPerTick = builder.comment("Blocks one wand may place, remove or swap per tick (blocks that need nothing done are not counted).",
                            "一根魔杖每刻最多放、拆、换几格（不用动的格子不算）")
                    .defineInRange("cells_per_tick", DEFAULT_CELLS_PER_TICK, 1, 4096);
            maxCellsPerTickTotal = builder.comment("Blocks all wands on the server may change per tick together; shared evenly when several are working.",
                            "全服所有魔杖合计每刻最多改几格，几个人同时用时平分")
                    .defineInRange("max_cells_per_tick_total", DEFAULT_MAX_CELLS_PER_TICK_TOTAL, 1, 16384);
            builder.pop();
        }

        public static int maxVolume() {
            return maxVolume == null ? DEFAULT_MAX_VOLUME : ECConfig.get(maxVolume);
        }

        public static int maxPointDistance() {
            return maxPointDistance == null ? DEFAULT_MAX_POINT_DISTANCE : ECConfig.get(maxPointDistance);
        }

        public static int maxReach() {
            if (testMaxReach != null) {
                return testMaxReach;
            }
            return maxReach == null ? DEFAULT_MAX_REACH : ECConfig.get(maxReach);
        }

        public static int cellsPerTick() {
            if (testCellsPerTick != null) {
                return testCellsPerTick;
            }
            return cellsPerTick == null ? DEFAULT_CELLS_PER_TICK : ECConfig.get(cellsPerTick);
        }

        public static int maxCellsPerTickTotal() {
            if (testMaxCellsPerTickTotal != null) {
                return testMaxCellsPerTickTotal;
            }
            return maxCellsPerTickTotal == null ? DEFAULT_MAX_CELLS_PER_TICK_TOTAL : ECConfig.get(maxCellsPerTickTotal);
        }

        /**
         * 只给 GameTest 用：覆盖离选区的距离上限和两个每刻上限（null = 用配置）。用到它的测试放在各自单独的 batch 里
         * （同一 batch 的测试同时跑，覆盖值是全服共用的），由 {@code @BeforeBatch} 设、{@code @AfterBatch} 置回 null（见 BuilderTests）。
         */
        public static void setTestOverrides(@Nullable Integer maxReach, @Nullable Integer cellsPerTick, @Nullable Integer maxCellsPerTickTotal) {
            testMaxReach = maxReach;
            testCellsPerTick = cellsPerTick;
            testMaxCellsPerTickTotal = maxCellsPerTickTotal;
        }
    }
}
