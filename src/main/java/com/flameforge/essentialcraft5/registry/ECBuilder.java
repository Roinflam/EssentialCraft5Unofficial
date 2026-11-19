package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.client.BuilderClientConfig;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.BuilderActions;
import com.flameforge.essentialcraft5.item.BuilderJobs;
import com.flameforge.essentialcraft5.item.BuilderMix;
import com.flameforge.essentialcraft5.item.BuilderMode;
import com.flameforge.essentialcraft5.item.BuilderModuleItem;
import com.flameforge.essentialcraft5.item.BuilderModules;
import com.flameforge.essentialcraft5.item.BuilderTier;
import com.flameforge.essentialcraft5.item.MagicalBuilderItem;
import com.flameforge.essentialcraft5.item.BuilderSalvage;
import com.flameforge.essentialcraft5.item.PavingWandItem;
import com.flameforge.essentialcraft5.network.C2SBuilderActionPacket;
import com.flameforge.essentialcraft5.network.ECNetwork;
import com.flameforge.essentialcraft5.network.S2CBuilderFlightPacket;
import com.flameforge.essentialcraft5.network.S2CBuilderHoldingsPacket;
import com.flameforge.essentialcraft5.network.S2CBuilderPanelPacket;
import com.flameforge.essentialcraft5.network.S2CBuilderStatusPacket;
import com.flameforge.essentialcraft5.recipe.BuilderModuleRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.model.generators.ItemModelBuilder;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 第六批 6b M1「创世神之杖」（计划 tools/plan/batch6b.md §4.1）的注册类：5 个模式物品
 * {@code magical_builder_{fill,clear,clear_matching,clear_others,replace_all}}（原作 magicalBuilder meta 0~4；
 * meta 2 / 3 原作叫「替换」、实际只清除，注册名改成 clear_matching / clear_others）。
 * 换模式的配方用 {@code essentialcraft5:crafting_shapeless_keep_nbt}（{@link ECRecipes#CRAFTING_SHAPELESS_KEEP_NBT}），MRU、两个点、记下的方块都留着。
 * <p>
 * 物品 {@code item/MagicalBuilderItem}（选点、选定方块、开工 / 停下），分帧施工 {@code item/BuilderJobs}，
 * 选区与 NBT {@code item/BuilderSelection}，模式与每格的规则 {@code item/BuilderMode}，客户端选区框和物品角标 {@code client/BuilderClient}，
 * 魔杖键与滚轮 {@code client/BuilderInput}，准星下提示 {@code client/BuilderHud}，铺面 / 直线的虚影 {@code client/BuilderGhost}。
 * 文字 tools/lang/keys/ecbuilder.json，标签 tools/tags/ecbuilder.json（{@code essentialcraft5:builder_blacklist}，默认空），
 * 配方 data/essentialcraft5/recipes/builder/（主配方 + 5 条换模式），手册进度 advancements/guide/void_machines/magical_builder.json。
 * 配置在 SERVER 配置 {@code general.builder}（{@link Config}）。
 */
public final class ECBuilder {

    /** 方块标签：创世神之杖不选它、不拆它、不换它（整合包里别的模组的特殊方块往这里加） */
    public static final TagKey<Block> BUILDER_BLACKLIST = ECTags.modBlock("builder_blacklist");
    /** 物品标签：魔杖不从这些随身容器里取料（默认空） */
    public static final TagKey<Item> BUILDER_CONTAINER_BLACKLIST = ECTags.modItem("builder_container_blacklist");

    public static final RegistryObject<MagicalBuilderItem> MAGICAL_BUILDER_FILL = wand(BuilderMode.FILL);
    public static final RegistryObject<MagicalBuilderItem> MAGICAL_BUILDER_CLEAR = wand(BuilderMode.CLEAR);
    public static final RegistryObject<MagicalBuilderItem> MAGICAL_BUILDER_CLEAR_MATCHING = wand(BuilderMode.CLEAR_MATCHING);
    public static final RegistryObject<MagicalBuilderItem> MAGICAL_BUILDER_CLEAR_OTHERS = wand(BuilderMode.CLEAR_OTHERS);
    public static final RegistryObject<MagicalBuilderItem> MAGICAL_BUILDER_REPLACE_ALL = wand(BuilderMode.REPLACE_ALL);

    /** 按 {@link BuilderMode} 的顺序（二 / 三阶的 5 个模式物品；不含铺设魔杖） */
    public static final List<RegistryObject<MagicalBuilderItem>> WANDS = List.of(MAGICAL_BUILDER_FILL, MAGICAL_BUILDER_CLEAR,
            MAGICAL_BUILDER_CLEAR_MATCHING, MAGICAL_BUILDER_CLEAR_OTHERS, MAGICAL_BUILDER_REPLACE_ALL);

    /**
     * 一阶「铺设魔杖」（builder_upgrade.md §2.1）：只有填充模式，形状两点 / 铺面 / 直线，默认铺面；数值见 {@link BuilderTier}。
     * 工作台配方 recipes/builder/paving_wand.json；放进创世神之杖的配方图样（顶替一个白名单过滤器）升成二阶，NBT 整份带过去
     * （recipes/builder/magical_builder_from_paver.json，保留 NBT 的有形配方）。贴图 item/spells/paving_wand 现在是占位图。
     */
    public static final RegistryObject<PavingWandItem> PAVING_WAND = ECItems.register("paving_wand",
            () -> new PavingWandItem(new Item.Properties().stacksTo(1)), ItemSpec.handheld("item/spells/paving_wand"));

    /**
     * 6 个魔杖模块 {@code builder_module_<短名>}（builder_upgrade.md §2.2，NBT、装拆在 {@link BuilderModules}）：工作台合成
     * （recipes/builder/builder_module_*.json），在工作台里和魔杖合在一起装上（特殊配方 {@link #BUILDER_MODULE_INSTALL}），
     * 拆走 C2S 包。贴图 item/module/builder_module_* 现在是占位图（tools/gen/builder_module_placeholders.py 生成）。
     */
    public static final Map<BuilderModules.Kind, RegistryObject<BuilderModuleItem>> MODULES = registerModules();

    /** 把模块装到魔杖上的特殊配方（数据 recipes/builder/builder_module_install.json，JSON 只有 type 和可选的 category） */
    public static final RegistryObject<RecipeSerializer<BuilderModuleRecipe>> BUILDER_MODULE_INSTALL =
            ECRecipes.serializer("builder_module_install", () -> new SimpleCraftingRecipeSerializer<>(BuilderModuleRecipe::new));

    /** 物品属性：魔杖档位（1 ~ 3）。5 个模式物品共用，值 ≥ 3 时换三阶外观（模型 item/magical_builder_ascended） */
    public static final ResourceLocation TIER_PREDICATE = ResourceLocation.fromNamespaceAndPath("essentialcraft5", "builder_tier");

    private ECBuilder() {
    }

    public static void init() {
        ECConfig.general(Config::define);
        // 虚影、准星下提示、确认阈值（CLIENT 配置 [builder]，client/BuilderClientConfig 是 common 类，专用服上不加载）
        ECConfig.client(BuilderClientConfig::define);
        // 第 1 批升级加的两个模块包（协议 14 还没上服，和它一起上，不升号）
        ECNetwork.registerC2S(C2SBuilderActionPacket.class, C2SBuilderActionPacket::encode, C2SBuilderActionPacket::decode, BuilderActions::handle);
        ECNetwork.registerS2C(S2CBuilderStatusPacket.class, S2CBuilderStatusPacket::encode, S2CBuilderStatusPacket::decode, S2CBuilderStatusPacket::handleClient);
        // 第 2 批面板加的两个（同样随协议 14 一起上）：面板的清单、混铺每种的持有数
        ECNetwork.registerS2C(S2CBuilderPanelPacket.class, S2CBuilderPanelPacket::encode, S2CBuilderPanelPacket::decode, S2CBuilderPanelPacket::handleClient);
        ECNetwork.registerS2C(S2CBuilderHoldingsPacket.class, S2CBuilderHoldingsPacket::encode, S2CBuilderHoldingsPacket::decode,
                S2CBuilderHoldingsPacket::handleClient);
        // 施工飞行特效包（同样随协议 14 一起上）。登记在这里会让后面登记的各包编号顺延一位，协议 14 还没发布过，不另升协议号
        ECNetwork.registerS2C(S2CBuilderFlightPacket.class, S2CBuilderFlightPacket::encode, S2CBuilderFlightPacket::decode,
                S2CBuilderFlightPacket::handleClient);
    }

    /** 这个模式的魔杖物品 */
    public static MagicalBuilderItem item(BuilderMode mode) {
        return WANDS.get(mode.ordinal()).get();
    }

    /** 这种模块的物品 */
    public static BuilderModuleItem module(BuilderModules.Kind kind) {
        return MODULES.get(kind).get();
    }

    private static Map<BuilderModules.Kind, RegistryObject<BuilderModuleItem>> registerModules() {
        Map<BuilderModules.Kind, RegistryObject<BuilderModuleItem>> map = new EnumMap<>(BuilderModules.Kind.class);
        for (BuilderModules.Kind kind : BuilderModules.Kind.values()) {
            String name = "builder_module_" + kind.id();
            map.put(kind, ECItems.register(name, () -> new BuilderModuleItem(new Item.Properties(), kind), ItemSpec.generated("item/module/" + name)));
        }
        return Collections.unmodifiableMap(map);
    }

    private static RegistryObject<MagicalBuilderItem> wand(BuilderMode mode) {
        // 5 种外观相同（同原作，都是 magicalbuilder 一个模型）；三阶按物品属性 builder_tier 换成共用的升格外观（贴图现在是占位图）
        return ECItems.register("magical_builder_" + mode.id(),
                () -> new MagicalBuilderItem(new Item.Properties().stacksTo(1), mode), ItemSpec.of((provider, item) -> {
                    ItemModelBuilder ascended = provider.withExistingParent("magical_builder_ascended", provider.mcLoc("item/handheld"))
                            .texture("layer0", provider.tex("item/spells/staff_void_ascended"));
                    provider.handheld(item, "item/spells/staff_void")
                            .override().predicate(TIER_PREDICATE, BuilderTier.ASCENDED.level()).model(ascended).end();
                }));
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
        public static final int DEFAULT_MAX_SIDE = 48;
        public static final double DEFAULT_MAX_MILLIS_PER_TICK = 2D;
        public static final int DEFAULT_MAX_JOBS_TOTAL = 16;
        public static final int DEFAULT_MAX_QUEUED_PER_PLAYER = 1;
        public static final int DEFAULT_MATERIAL_PAUSE_SECONDS = 30;
        public static final int DEFAULT_UNDO_WINDOW_SECONDS = 600;
        /** 加了扩域也不能超过的格数（{@code hard_max_volume}） */
        public static final int DEFAULT_HARD_MAX_VOLUME = 32768;
        /** 飞行特效的提前量（刻）：放置类施工先发特效包，过这么多刻才真放；0 = 不发特效包、当刻施工 */
        public static final int DEFAULT_VFX_LEAD_TICKS = 10;
        /** 提前量最多 2 秒（包里是一个无符号字节；计划队列按它的长度开） */
        public static final int MAX_VFX_LEAD_TICKS = 40;
        public static final int DEFAULT_VFX_SEND_RADIUS = 64;
        /**
         * 整轮 GameTest 用的提前量（JVM 属性，{@code runGameTestServer -PecBuilderLead=<刻>} 设）：没有单独覆盖的用例都按它跑，
         * 用来确认提前量为 0 时施工和以前完全一样。单独覆盖（{@link Test#vfxLeadTicks}）优先于它
         */
        public static final String LEAD_PROPERTY = "essentialcraft5.builder.vfxLeadTicks";
        /** 铺面 / 直线的边长不论怎么配都不超过它（格子排序键给每个方向留了 9 位） */
        public static final int HARD_MAX_SIDE = 256;
        /** 一阶铺设魔杖：格数、边长、两点最远、施工距离、每刻格数（没有花费倍率） */
        public static final TierValues PAVER_DEFAULTS = new TierValues(256, 16, 16, 32, 8, 1D);
        /** 三阶升格：同上，再加每格 MRU 的倍率 */
        public static final TierValues ASCENDED_DEFAULTS = new TierValues(16384, 96, 96, 96, 128, 0.8D);

        /** 一档的配置值（施工距离、每刻格数还没套 GameTest 覆盖值） */
        public record TierValues(int maxVolume, int maxSide, int maxPointDistance, int maxReach, int cellsPerTick, double costMultiplier) {
        }

        /** 模块的倍率：扩域乘格数、乘边长，疾工乘每刻格数，节能乘每格 MRU，远域加施工距离（builder_upgrade.md §2.2） */
        public record ModuleValues(double rangeVolume, double rangeSide, double hasteCells, double efficiencyCost, int reachBonus) {
        }

        public static final ModuleValues MODULE_DEFAULTS = new ModuleValues(2D, 1.4D, 2D, 0.6D, 32);

        @Nullable
        private static ForgeConfigSpec.DoubleValue rangeVolume;
        @Nullable
        private static ForgeConfigSpec.DoubleValue rangeSide;
        @Nullable
        private static ForgeConfigSpec.DoubleValue hasteCells;
        @Nullable
        private static ForgeConfigSpec.DoubleValue efficiencyCost;
        @Nullable
        private static ForgeConfigSpec.IntValue reachBonus;
        /** 远距离瞄准的上限默认值（格），见 {@link #aimDistance}：三阶装远域的施工距离就是 128，默认等于不另外限制 */
        public static final int DEFAULT_AIM_DISTANCE = 128;
        @Nullable
        private static ForgeConfigSpec.IntValue aimDistance;
        /** 丢弃清单上限的默认值 */
        public static final int DEFAULT_SALVAGE_DISCARD_MAX = 16;
        @Nullable
        private static ForgeConfigSpec.IntValue mixMaxKinds;
        @Nullable
        private static ForgeConfigSpec.IntValue salvageDiscardMax;

        /** 一档的一组配置项 */
        private record TierSpec(ForgeConfigSpec.IntValue maxVolume, ForgeConfigSpec.IntValue maxSide, ForgeConfigSpec.IntValue maxPointDistance,
                                ForgeConfigSpec.IntValue maxReach, ForgeConfigSpec.IntValue cellsPerTick, @Nullable ForgeConfigSpec.DoubleValue costMultiplier) {
        }

        @Nullable
        private static TierSpec paver;
        @Nullable
        private static TierSpec ascended;
        private static ForgeConfigSpec.IntValue hardMaxVolume;
        /** 沿面测量每刻读几格的默认值（同施工每任务每刻最多扫的格数） */
        public static final int DEFAULT_FACE_SCAN_PER_TICK = BuilderJobs.SCAN_PER_TICK;
        @Nullable
        private static ForgeConfigSpec.IntValue faceScanPerTick;

        private static ForgeConfigSpec.IntValue maxSide;
        private static ForgeConfigSpec.DoubleValue maxMillisPerTick;
        private static ForgeConfigSpec.IntValue maxJobsTotal;
        private static ForgeConfigSpec.IntValue maxQueuedPerPlayer;
        private static ForgeConfigSpec.IntValue deniedLimit;
        private static ForgeConfigSpec.IntValue deniedStreakLimit;
        private static ForgeConfigSpec.BooleanValue probeAfterDenied;
        @Nullable
        private static ForgeConfigSpec.BooleanValue showMaterialInHand;
        private static ForgeConfigSpec.ConfigValue<List<? extends String>> disabledDimensions;
        private static ForgeConfigSpec.BooleanValue allowContainerItems;
        private static ForgeConfigSpec.IntValue materialPauseSeconds;
        private static ForgeConfigSpec.IntValue undoWindowSeconds;
        private static ForgeConfigSpec.DoubleValue maxHardness;
        @Nullable
        private static ForgeConfigSpec.IntValue vfxLeadTicks;
        @Nullable
        private static ForgeConfigSpec.IntValue vfxSendRadius;

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
            builder.comment("Magical Building Wand.", "创世神之杖").push("builder");
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
            maxSide = builder.comment("Longest side of a plane or line laid in one go, in blocks.",
                            "铺面、直线一次最长几格（边长）")
                    .defineInRange("max_side", DEFAULT_MAX_SIDE, 1, 256);
            maxMillisPerTick = builder.comment("Milliseconds all wands on the server may spend per tick together; the rest waits for the next tick.",
                            "全服所有魔杖合计每刻最多花几毫秒，超了这刻就收手、剩下的下刻接着做")
                    .defineInRange("max_millis_per_tick", DEFAULT_MAX_MILLIS_PER_TICK, 0.05D, 50D);
            maxJobsTotal = builder.comment("Jobs that may run at the same time on the server; more wait in line.",
                            "全服同时在做的任务数，多出来的排队")
                    .defineInRange("max_jobs_total", DEFAULT_MAX_JOBS_TOTAL, 1, 256);
            maxQueuedPerPlayer = builder.comment("Jobs one player may have waiting behind the running one (0 = must wait until it ends).",
                            "每人在做的任务后面最多再排几个（0 = 做完才能开下一个）")
                    .defineInRange("max_queued_per_player", DEFAULT_MAX_QUEUED_PER_PLAYER, 0, 8);
            deniedLimit = builder.comment("A job stops after this many blocks were refused (claims and protection).",
                            "一个任务累计被拒（领地、保护）这么多格就停下")
                    .defineInRange("denied_limit", BuilderJobs.DENIED_LIMIT, 1, 65536);
            // 两个上限各管一种情况，哪个先到就按哪个停（都报「没有权限」）：denied_limit 按累计算、含先探后放里探测被拒的格子，
            // 管选区大片落在别人领地里；这一项只数连着真放 / 真拆被拒的格子（探测被拒的不算、也不清零），管「探测放行、真做却被拒」的领地
            // （例如只禁止放置的领地：探测问的是能不能动这一格，放行了，真放时每格被拒，领地插件每格提示一句）。
            // 只在先探后放下数：probe_after_denied 关掉、或探测等超时放弃了的任务不数连续，只受 denied_limit 管
            deniedStreakLimit = builder.comment("While probing before each block (probe_after_denied), a job stops after this many blocks in a row were refused when actually placed or broken; any success resets the count. Jobs that are not probing are only limited by denied_limit. 0 = no limit.",
                            "先探后放时，一个任务连续这么多格真放 / 真拆被拒就停下，中间成功放下或拆掉一格就重新数；没在先探后放的任务只受 denied_limit 管；0 = 不限")
                    .defineInRange("denied_streak_limit", BuilderJobs.DENIED_STREAK_LIMIT, 0, 65536);
            probeAfterDenied = builder.comment("After the first refusal, ask the protection check before each block instead of placing and being refused.",
                            "第一次被拒以后，每格先探测能不能动、放行才真放（不再每格真发事件被拒）")
                    .define("probe_after_denied", true);
            showMaterialInHand = builder.comment("While a block is placed, the hand reported to server plugins holds one of that block, even when the material came from the inventory or a carried container.",
                            "Plugins that judge a placement by the item in hand then see the same as when the player places it by hand. Off = the hand keeps whatever it holds.",
                            "放每一格时，报给服务端插件的那只手临时拿着 1 个这种方块（材料来自背包或随身容器时也一样），",
                            "按手里物品判断放置的插件看到的和玩家亲手放置相同；关掉后那只手保持原样")
                    .define("show_material_in_hand", true);
            disabledDimensions = builder.comment("Dimensions where wands may not start work or undo (ids such as minecraft:the_end).",
                            "这些维度里魔杖不能开工、不能撤销（维度 id，例 minecraft:the_end）")
                    .defineListAllowEmpty("disabled_dimensions", List.of(), o -> o instanceof String s && ResourceLocation.tryParse(s) != null);
            allowContainerItems = builder.comment("Take building material from carried containers that expose an item handler (bags, backpacks), one level deep.",
                            "The Salvage module puts removed blocks into those containers regardless of this; keep a container out of both with the tag essentialcraft5:builder_container_blacklist.",
                            "从身上带着的、给了物品处理能力的随身容器（背包类）里取料，只看一层。",
                            "回收模块往这些容器里放拆下的东西不看这一项；某种容器两样都不想让碰，就把它放进物品标签 essentialcraft5:builder_container_blacklist")
                    .define("allow_container_items", true);
            materialPauseSeconds = builder.comment("When material or MRU runs out, wait this many seconds for a refill before ending (0 = end at once).",
                            "材料或 MRU 用完时暂停等补料的秒数，超时才结束（0 = 用完就结束）")
                    .defineInRange("material_pause_seconds", DEFAULT_MATERIAL_PAUSE_SECONDS, 0, 600);
            undoWindowSeconds = builder.comment("How long the last placement can be undone, in seconds (0 = undo off).",
                            "最近一次放置在多少秒内能撤销（0 = 关掉撤销）")
                    .defineInRange("undo_window_seconds", DEFAULT_UNDO_WINDOW_SECONDS, 0, 86400);
            maxHardness = builder.comment("Clearing and replacing leave blocks harder than this alone (-1 = no limit, as in the original).",
                            "清除、替换不碰硬度高于它的方块（-1 = 不限，同原作）")
                    .defineInRange("max_hardness", -1D, -1D, 1.0E6D);
            hardMaxVolume = builder.comment("No wand may change more blocks than this in one go, whatever its tier and modules.",
                            "不论哪一档、装了什么模块，一次最多改这么多格")
                    .defineInRange("hard_max_volume", DEFAULT_HARD_MAX_VOLUME, 1, 65536);
            faceScanPerTick = builder.comment("Blocks one Surface measurement may look at per tick before work starts; it also shares max_millis_per_tick with the work itself.",
                            "沿面开工前测量时，每个测量每刻最多读几格；同时和施工共用 max_millis_per_tick")
                    .defineInRange("face_scan_per_tick", DEFAULT_FACE_SCAN_PER_TICK, 16, 4096);   // 轮到的第一个测量不看耗时预算，上限压住一步的开销
            vfxLeadTicks = builder.comment("Ticks between showing a block in flight and actually placing it (0 = no flight effect, place at once).",
                            "Materials and MRU are only taken when the block is placed.",
                            "方块飞向目标的特效比真正放下提前的刻数（0 = 不显示飞行特效，立即放置）。",
                            "材料和 MRU 在方块真正放下时才扣除。")
                    .defineInRange("vfx_lead_ticks", DEFAULT_VFX_LEAD_TICKS, 0, MAX_VFX_LEAD_TICKS);
            vfxSendRadius = builder.comment("Players within this many blocks of the work area receive the flight effect; the caster always does.",
                            "距施工范围不超过该格数的玩家可看到飞行特效，施法者本人始终可见")
                    .defineInRange("vfx_send_radius", DEFAULT_VFX_SEND_RADIUS, 0, 256);
            aimDistance = builder.comment("Plane, Wall and Surface can be aimed at blocks as far away as the wand's work range (max_reach of its tier, plus the Reach module); this caps that distance, in blocks.",
                            "When work starts the server traces the player's line of sight itself and only accepts the same block or one right next to it. Aiming never loads chunks. Line always uses normal reach.",
                            "铺面、竖墙、沿面可以瞄准施工距离以内的方块（所在档位的 max_reach，装远域时再加 reach_bonus），这里是瞄准距离的上限（格）。",
                            "开工时服务端按玩家视线重新计算点中的格子，只认同一格或紧挨着的一格；瞄准不会加载区块。直线只认原版交互距离内的方块")
                    .defineInRange("max_aim_distance", DEFAULT_AIM_DISTANCE, 5, 256);
            builder.comment("Tier 1: Paving Wand (fill only; two points, plane and line). Tier 2 uses the keys above.",
                    "一阶：铺设魔杖（只有填充；两点、铺面、直线）。二阶用上面原有的那几个键").push("paver");
            paver = defineTier(builder, PAVER_DEFAULTS, false);
            builder.pop();
            builder.comment("Tier 3: Ascended Magical Building Wand.", "三阶：创世神之杖·升格").push("ascended");
            ascended = defineTier(builder, ASCENDED_DEFAULTS, true);
            builder.pop();
            defineModules(builder);
            builder.pop();
        }

        /** {@code general.builder.modules.*}：各模块的倍率，混铺最多几种、丢弃清单最多几项 */
        private static void defineModules(ForgeConfigSpec.Builder builder) {
            builder.comment("Wand modules, fitted at a crafting table. Multiplied values are rounded down.",
                    "魔杖模块（在工作台里和魔杖合在一起装上）。乘出来的数一律向下取整").push("modules");
            builder.comment("Range module.", "扩域模块").push("range");
            rangeVolume = builder.comment("Largest selection is multiplied by this (hard_max_volume still applies).",
                            "一次最多几格乘这个倍率（仍受 hard_max_volume 限制）")
                    .defineInRange("volume_multiplier", MODULE_DEFAULTS.rangeVolume(), 1D, 16D);
            rangeSide = builder.comment("Longest side of a plane or line is multiplied by this.", "铺面、直线的边长乘这个倍率")
                    .defineInRange("side_multiplier", MODULE_DEFAULTS.rangeSide(), 1D, 4D);
            builder.pop();
            builder.comment("Haste module.", "疾工模块").push("haste");
            hasteCells = builder.comment("Blocks per tick for this wand are multiplied by this (the server-wide limits still apply).",
                            "这根魔杖每刻最多几格乘这个倍率（全服每刻格数和耗时的上限照旧）")
                    .defineInRange("cells_multiplier", MODULE_DEFAULTS.hasteCells(), 1D, 16D);
            builder.pop();
            builder.comment("Efficiency module.", "节能模块").push("efficiency");
            efficiencyCost = builder.comment("MRU per block is multiplied by this (0 = building costs no MRU).", "每格 MRU 乘这个倍率（0 = 不耗 MRU）")
                    .defineInRange("cost_multiplier", MODULE_DEFAULTS.efficiencyCost(), 0D, 1D);
            builder.pop();
            builder.comment("Reach module.", "远域模块").push("reach");
            reachBonus = builder.comment("Added to how far the player may stand from the work, in blocks. The whole selection must still be in loaded chunks.",
                            "施工距离加这么多格。整个范围仍要在已加载的区块里，所以超出服务器视距的部分用不上")
                    .defineInRange("reach_bonus", MODULE_DEFAULTS.reachBonus(), 0, 256);
            builder.pop();
            builder.comment("Mixing module.", "混铺模块").push("mix");
            mixMaxKinds = builder.comment("How many kinds of blocks one mixing table may hold; extra entries on a wand are ignored.",
                            "一张混铺表最多几种方块；魔杖上多出来的不生效")
                    .defineInRange("max_kinds", BuilderMix.HARD_MAX_KINDS, 1, BuilderMix.HARD_MAX_KINDS);
            builder.pop();
            builder.comment("Salvage module.", "回收模块").push("salvage");
            salvageDiscardMax = builder.comment("How many items the discard list may hold; extra entries on a wand are ignored.",
                            "丢弃清单最多几项；魔杖上多出来的不生效")
                    .defineInRange("discard_max", DEFAULT_SALVAGE_DISCARD_MAX, 0, BuilderSalvage.HARD_MAX_DISCARD);
            builder.pop();
            builder.pop();
        }

        /** 一张混铺表最多几种（{@code general.builder.modules.mix.max_kinds}，GameTest 覆盖了就用覆盖值） */
        public static int mixMaxKinds() {
            if (Test.mixMaxKinds != null) {
                return Test.mixMaxKinds;
            }
            return mixMaxKinds == null ? BuilderMix.HARD_MAX_KINDS : ECConfig.get(mixMaxKinds);
        }

        /** 丢弃清单最多几项（{@code general.builder.modules.salvage.discard_max}，GameTest 覆盖了就用覆盖值） */
        public static int salvageDiscardMax() {
            if (Test.salvageDiscardMax != null) {
                return Test.salvageDiscardMax;
            }
            return salvageDiscardMax == null ? DEFAULT_SALVAGE_DISCARD_MAX : ECConfig.get(salvageDiscardMax);
        }

        /** 各模块的配置值（GameTest 覆盖了就用覆盖值）；配置没加载时返回默认值 */
        public static ModuleValues moduleValues() {
            if (Test.moduleValues != null) {
                return Test.moduleValues;
            }
            if (rangeVolume == null) {
                return MODULE_DEFAULTS;
            }
            return new ModuleValues(ECConfig.get(rangeVolume), ECConfig.get(rangeSide), ECConfig.get(hasteCells),
                    ECConfig.get(efficiencyCost), ECConfig.get(reachBonus));
        }

        /**
         * 远距离瞄准的上限（{@code general.builder.max_aim_distance}，GameTest 覆盖了就用覆盖值）；配置没加载时返回默认值。
         * 实际瞄准距离是魔杖的施工距离，不超过这个数（{@code BuilderAim#distance(ItemStack)}）。
         * SERVER 配置会同步给客户端，客户端算虚影和服务端复核用的是同一个数。
         */
        public static int aimDistance() {
            if (Test.aimDistance != null) {
                return Test.aimDistance;
            }
            return aimDistance == null ? DEFAULT_AIM_DISTANCE : ECConfig.get(aimDistance);
        }

        private static TierSpec defineTier(ForgeConfigSpec.Builder builder, TierValues defaults, boolean withCost) {
            ForgeConfigSpec.IntValue volume = builder.comment("Largest selection in blocks (hard_max_volume still applies).", "一次最多几格（仍受 hard_max_volume 限制）")
                    .defineInRange("max_volume", defaults.maxVolume(), 1, 65536);
            ForgeConfigSpec.IntValue side = builder.comment("Longest side of a plane or line, in blocks.", "铺面、直线一次最长几格（边长）")
                    .defineInRange("max_side", defaults.maxSide(), 1, HARD_MAX_SIDE);
            ForgeConfigSpec.IntValue distance = builder.comment("Largest straight-line distance between the two points, in blocks.", "两个点的直线距离上限（格）")
                    .defineInRange("max_point_distance", defaults.maxPointDistance(), 1, 256);
            ForgeConfigSpec.IntValue reach = builder.comment("How close the player must stay to the work, in blocks; walking farther away stops it.",
                            "玩家离施工范围多远以内才能开工（格），施工途中走远了就停")
                    .defineInRange("max_reach", defaults.maxReach(), 1, 256);
            ForgeConfigSpec.IntValue perTick = builder.comment("Blocks one wand may place, remove or swap per tick.", "一根魔杖每刻最多放、拆、换几格")
                    .defineInRange("cells_per_tick", defaults.cellsPerTick(), 1, 4096);
            ForgeConfigSpec.DoubleValue cost = withCost
                    ? builder.comment("MRU per block is multiplied by this and rounded down.", "每格 MRU 乘这个倍率、向下取整")
                    .defineInRange("cost_multiplier", defaults.costMultiplier(), 0D, 1D)
                    : null;
            return new TierSpec(volume, side, distance, reach, perTick, cost);
        }

        /** 这一档的配置值（二阶就是原有的那几个键）；配置没加载时返回默认值 */
        public static TierValues tierValues(BuilderTier tier) {
            return switch (tier) {
                case PAVER -> read(paver, PAVER_DEFAULTS);
                case ASCENDED -> {
                    TierValues values = read(ascended, ASCENDED_DEFAULTS);
                    yield Test.ascendedCostMultiplier == null ? values : new TierValues(values.maxVolume(), values.maxSide(),
                            values.maxPointDistance(), values.maxReach(), values.cellsPerTick(), Test.ascendedCostMultiplier);
                }
                default -> new TierValues(maxVolume(), maxSide(), maxPointDistance(),
                        maxReach == null ? DEFAULT_MAX_REACH : ECConfig.get(maxReach),
                        cellsPerTick == null ? DEFAULT_CELLS_PER_TICK : ECConfig.get(cellsPerTick), 1D);
            };
        }

        private static TierValues read(@Nullable TierSpec spec, TierValues defaults) {
            if (spec == null) {
                return defaults;
            }
            return new TierValues(ECConfig.get(spec.maxVolume()), ECConfig.get(spec.maxSide()), ECConfig.get(spec.maxPointDistance()),
                    ECConfig.get(spec.maxReach()), ECConfig.get(spec.cellsPerTick()),
                    spec.costMultiplier() == null ? defaults.costMultiplier() : ECConfig.get(spec.costMultiplier()));
        }

        /** 施工距离：GameTest 覆盖了就用覆盖值（三档一样），否则用这一档的 */
        public static int reachOverride(int tierValue) {
            return testMaxReach != null ? testMaxReach : tierValue;
        }

        /** 每刻格数：同上 */
        public static int cellsPerTickOverride(int tierValue) {
            return testCellsPerTick != null ? testCellsPerTick : tierValue;
        }

        /** 加了模块也不能超过的格数 */
        public static int hardMaxVolume() {
            if (Test.hardMaxVolume != null) {
                return Test.hardMaxVolume;
            }
            return hardMaxVolume == null ? DEFAULT_HARD_MAX_VOLUME : ECConfig.get(hardMaxVolume);
        }

        public static int maxSide() {
            if (Test.maxSide != null) {
                return Test.maxSide;
            }
            return maxSide == null ? DEFAULT_MAX_SIDE : ECConfig.get(maxSide);
        }

        /** 沿面测量每个测量每刻最多读几格（{@code general.builder.face_scan_per_tick}，GameTest 覆盖了就用覆盖值） */
        public static int faceScanPerTick() {
            if (Test.faceScanPerTick != null) {
                return Test.faceScanPerTick;
            }
            return faceScanPerTick == null ? DEFAULT_FACE_SCAN_PER_TICK : ECConfig.get(faceScanPerTick);
        }

        /**
         * 飞行特效的提前量（{@code general.builder.vfx_lead_ticks}）。GameTest 单独覆盖了用覆盖值，其次是整轮测试的 JVM 属性 {@link #LEAD_PROPERTY}
         */
        public static int vfxLeadTicks() {
            if (Test.vfxLeadTicks != null) {
                return Test.vfxLeadTicks;
            }
            Integer suite = Integer.getInteger(LEAD_PROPERTY);
            if (suite != null) {
                return Math.max(0, Math.min(MAX_VFX_LEAD_TICKS, suite));
            }
            return vfxLeadTicks == null ? DEFAULT_VFX_LEAD_TICKS : ECConfig.get(vfxLeadTicks);
        }

        /** 飞行特效发给多远内的玩家（{@code general.builder.vfx_send_radius}） */
        public static int vfxSendRadius() {
            if (Test.vfxSendRadius != null) {
                return Test.vfxSendRadius;
            }
            return vfxSendRadius == null ? DEFAULT_VFX_SEND_RADIUS : ECConfig.get(vfxSendRadius);
        }

        public static double maxMillisPerTick() {
            if (Test.maxMillisPerTick != null) {
                return Test.maxMillisPerTick;
            }
            return maxMillisPerTick == null ? DEFAULT_MAX_MILLIS_PER_TICK : ECConfig.get(maxMillisPerTick);
        }

        public static int maxJobsTotal() {
            if (Test.maxJobsTotal != null) {
                return Test.maxJobsTotal;
            }
            return maxJobsTotal == null ? DEFAULT_MAX_JOBS_TOTAL : ECConfig.get(maxJobsTotal);
        }

        public static int maxQueuedPerPlayer() {
            return maxQueuedPerPlayer == null ? DEFAULT_MAX_QUEUED_PER_PLAYER : ECConfig.get(maxQueuedPerPlayer);
        }

        public static int deniedLimit() {
            return deniedLimit == null ? BuilderJobs.DENIED_LIMIT : ECConfig.get(deniedLimit);
        }

        /** 连续真被拒几格就停；0 = 不限（见 {@code denied_streak_limit} 处的注释：和 {@link #deniedLimit} 各管一种情况，先到先停） */
        public static int deniedStreakLimit() {
            if (Test.deniedStreakLimit != null) {
                return Test.deniedStreakLimit;
            }
            return deniedStreakLimit == null ? BuilderJobs.DENIED_STREAK_LIMIT : ECConfig.get(deniedStreakLimit);
        }

        public static boolean showMaterialInHand() {
            if (Test.showMaterialInHand != null) {
                return Test.showMaterialInHand;
            }
            return showMaterialInHand == null || ECConfig.get(showMaterialInHand);
        }

        public static boolean probeAfterDenied() {
            if (Test.probeAfterDenied != null) {
                return Test.probeAfterDenied;
            }
            return probeAfterDenied == null || ECConfig.get(probeAfterDenied);
        }

        public static boolean dimensionDisabled(ResourceLocation dimension) {
            List<? extends String> list = Test.disabledDimensions != null ? Test.disabledDimensions
                    : disabledDimensions == null ? List.of() : ECConfig.getValue(disabledDimensions);
            return list.contains(dimension.toString());
        }

        public static boolean allowContainerItems() {
            if (Test.allowContainerItems != null) {
                return Test.allowContainerItems;
            }
            return allowContainerItems == null || ECConfig.get(allowContainerItems);
        }

        public static int materialPauseSeconds() {
            if (Test.materialPauseSeconds != null) {
                return Test.materialPauseSeconds;
            }
            return materialPauseSeconds == null ? DEFAULT_MATERIAL_PAUSE_SECONDS : ECConfig.get(materialPauseSeconds);
        }

        public static int undoWindowSeconds() {
            if (Test.undoWindowSeconds != null) {
                return Test.undoWindowSeconds;
            }
            return undoWindowSeconds == null ? DEFAULT_UNDO_WINDOW_SECONDS : ECConfig.get(undoWindowSeconds);
        }

        /** 清除、替换能碰的最大硬度；负数 = 不限 */
        public static double maxHardness() {
            if (Test.maxHardness != null) {
                return Test.maxHardness;
            }
            return maxHardness == null ? -1D : ECConfig.get(maxHardness);
        }

        /**
         * 只给 GameTest 用的覆盖值（null = 用配置），规矩同 {@link #setTestOverrides}：用到的测试放在各自的 batch 里，
         * {@code @BeforeBatch} 设、{@code @AfterBatch} 调 {@link #reset}。
         */
        public static final class Test {
            @Nullable
            public static Integer maxSide;
            @Nullable
            public static Double maxMillisPerTick;
            @Nullable
            public static Integer maxJobsTotal;
            @Nullable
            public static Boolean probeAfterDenied;
            @Nullable
            public static Boolean showMaterialInHand;
            @Nullable
            public static List<String> disabledDimensions;
            @Nullable
            public static Boolean allowContainerItems;
            @Nullable
            public static Integer materialPauseSeconds;
            @Nullable
            public static Integer undoWindowSeconds;
            @Nullable
            public static Double maxHardness;
            @Nullable
            public static Integer deniedStreakLimit;
            @Nullable
            public static Integer hardMaxVolume;
            @Nullable
            public static Double ascendedCostMultiplier;
            @Nullable
            public static ModuleValues moduleValues;
            @Nullable
            public static Integer aimDistance;
            @Nullable
            public static Integer mixMaxKinds;
            @Nullable
            public static Integer salvageDiscardMax;
            @Nullable
            public static Integer faceScanPerTick;
            @Nullable
            public static Integer vfxLeadTicks;
            @Nullable
            public static Integer vfxSendRadius;

            private Test() {
            }

            public static void reset() {
                maxSide = null;
                maxMillisPerTick = null;
                maxJobsTotal = null;
                probeAfterDenied = null;
                showMaterialInHand = null;
                disabledDimensions = null;
                allowContainerItems = null;
                materialPauseSeconds = null;
                undoWindowSeconds = null;
                maxHardness = null;
                deniedStreakLimit = null;
                hardMaxVolume = null;
                ascendedCostMultiplier = null;
                moduleValues = null;
                aimDistance = null;
                mixMaxKinds = null;
                salvageDiscardMax = null;
                faceScanPerTick = null;
                vfxLeadTicks = null;
                vfxSendRadius = null;
            }
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
