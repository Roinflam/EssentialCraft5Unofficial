package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.hoanna.AbyssalSaltCrustBlock;
import com.flameforge.essentialcraft5.block.hoanna.RuinMilestoneBlock;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.hoanna.HoannaSurveyClientConfig;
import com.flameforge.essentialcraft5.hoanna.survey.SurveySearches;
import com.flameforge.essentialcraft5.item.AbyssalSaltItem;
import com.flameforge.essentialcraft5.item.HoannaSurveyMapItem;
import com.flameforge.essentialcraft5.item.TornSurveyMapItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.client.model.generators.ItemModelBuilder;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

/**
 * 第十三批 13d-H3「探索与续航」的注册类（batch13.md §6.3、D33；HS §2.3）：
 * <ul>
 *   <li>{@link #TORN_SURVEY_MAP} 残破勘测图：城镇废墟城塔、古代墓穴箱子另加的一池（12%，{@code tools/gen/hoanna_loot.py}）；
 *       三张 + 一颗地脉结晶无序合成 {@link #HOANNA_SURVEY_MAP} 海纳尔勘测图（{@code recipes/hoanna_survey/hoanna_survey_map.json}）。</li>
 *   <li>{@link #RUIN_MILESTONE} 里程碑：右键指最近一座城镇废墟（{@link RuinMilestoneBlock}），挖掉掉强化石。</li>
 *   <li>{@link #ABYSSAL_SALT_CRUST} 渊盐结壳（海纳尔之海海底，硬度 0.8、铲子）：掉 {@link #ABYSSAL_SALT} 渊盐 2~4（时运加），精准采集掉本体；
 *       渊盐吃下清辐射（{@link AbyssalSaltItem}）。</li>
 * </ul>
 * 找最近一处的搜索都走 {@code hoanna/survey/SurveySearches}（结构预测 + 全服预算，不加载区块），每刻末由 {@link HoannaSurveyEvents} 推进。
 * 服务端配置 {@code general.hoanna_survey}、{@code general.abyssal_salt}（{@link Config}）；客户端配置 {@code [hoanna] survey_beam}
 * （{@link HoannaSurveyClientConfig}）。文字 tools/lang/keys/echoannasurvey.json（音效直接用原版的，tools/sounds/echoannasurvey.json 是空表），
 * 客户端 {@code client/HoannaSurveyClient}，测试 {@code gametest/HoannaSurveyTests}。
 */
public final class ECHoannaSurvey {

    /** 渊盐结壳：硬度 0.8、铲子；掉渊盐 2~4（时运按原版矿石公式），精准采集掉本体 */
    public static final RegistryObject<AbyssalSaltCrustBlock> ABYSSAL_SALT_CRUST = ECBlocks.register("abyssal_salt_crust",
            () -> new AbyssalSaltCrustBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).strength(0.8F).sound(SoundType.CALCITE)),
            BlockSpec.of(BlockSpec.cubeAll("block/hoanna/abyssal_salt_crust")).blockTags(BlockTags.MINEABLE_WITH_SHOVEL)
                    .loot((loot, block) -> loot.ore(block, ECHoannaSurvey.ABYSSAL_SALT.get(), 2F, 4F)));

    /** 里程碑：硬度照强化石（3 / 15）、亮度 10；侧面和底是强化石，顶面是元素灯；挖掉掉一块强化石 */
    public static final RegistryObject<RuinMilestoneBlock> RUIN_MILESTONE = ECBlocks.register("ruin_milestone",
            () -> new RuinMilestoneBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM)
                    .strength(3F, ECBlocks.legacyResistance(3F, 15F, false)).requiresCorrectToolForDrops().lightLevel(state -> 10)),
            BlockSpec.of((provider, block) -> provider.simpleBlockWithItem(block, provider.models().cubeBottomTop(provider.name(block),
                            provider.tex("block/fortifiedstone"), provider.tex("block/fortifiedstone"), provider.tex("block/lamp/elemental"))))
                    .loot((loot, block) -> loot.counted(block, ECMaterials.FORTIFIED_STONE.get(), 1F, 1F)));

    /** 渊盐：一组 64，吃下清辐射 */
    public static final RegistryObject<AbyssalSaltItem> ABYSSAL_SALT = ECItems.register("abyssal_salt",
            () -> new AbyssalSaltItem(new Item.Properties()), ItemSpec.generated("item/abyssal_salt"));

    /** 残破勘测图：一组 64 */
    public static final RegistryObject<TornSurveyMapItem> TORN_SURVEY_MAP = ECItems.register("torn_survey_map",
            () -> new TornSurveyMapItem(new Item.Properties()), ItemSpec.generated("item/torn_survey_map"));

    /** 海纳尔勘测图：叠 1（每张记着自己的绑定和跳过表）；三态模型：没绑定 / 走到过 / 已绑定 */
    public static final RegistryObject<HoannaSurveyMapItem> HOANNA_SURVEY_MAP = ECItems.register("hoanna_survey_map",
            () -> new HoannaSurveyMapItem(new Item.Properties().stacksTo(1)),
            ItemSpec.of((provider, item) -> {
                ItemModelBuilder visited = provider.generated("hoanna_survey_map_visited", "item/hoanna_survey_map_visited");
                ItemModelBuilder bound = provider.generated("hoanna_survey_map_bound", "item/hoanna_survey_map_bound");
                // 后写的优先：已绑定（1）压过走到过（0.5）
                provider.generated(item, "item/hoanna_survey_map")
                        .override().predicate(HoannaSurveyMapItem.STATE_PREDICATE, HoannaSurveyMapItem.STATE_VISITED).model(visited).end()
                        .override().predicate(HoannaSurveyMapItem.STATE_PREDICATE, HoannaSurveyMapItem.STATE_BOUND).model(bound).end();
            }));

    private ECHoannaSurvey() {
    }

    /** 由 ECRegistries.register() 调（模组构造阶段）：类加载即登记静态字段里的条目；配置在这里登记 */
    public static void init() {
        ECConfig.general(Config::define);
        ECConfig.client(HoannaSurveyClientConfig::define);
    }

    /**
     * 服务端配置 {@code general.hoanna_survey}（搜索半径、到点半径）、{@code general.abyssal_salt}（清除比例、上下限、冷却）。
     * 读值走 {@link #values()}（没加载时是默认值）；GameTest 用 {@link #setTestOverrides} 整套换掉，不用 ConfigValue.set。
     */
    public static final class Config {

        /**
         * 一套数值。
         *
         * @param searchRadius  勘测图找多远（水平半径，格）
         * @param arriveRadius  走到离标的点多近算到了（水平，格）
         * @param purgeRatio    渊盐清掉当前剂量的几成
         * @param purgeMin      至少清多少
         * @param purgeMax      至多清多少
         * @param cooldownTicks 吃完多久才能再吃（刻）
         */
        public record Values(int searchRadius, int arriveRadius, double purgeRatio, int purgeMin, int purgeMax, int cooldownTicks) {
        }

        /** 默认值（batch13.md §6.3、D33 A；HS §2.9） */
        public static final Values DEFAULTS = new Values(2048, 24, 0.2D, 3000, 12000, 12000);

        private static ForgeConfigSpec.IntValue searchRadius;
        private static ForgeConfigSpec.IntValue arriveRadius;
        private static ForgeConfigSpec.DoubleValue purgeRatio;
        private static ForgeConfigSpec.IntValue purgeMin;
        private static ForgeConfigSpec.IntValue purgeMax;
        private static ForgeConfigSpec.IntValue cooldownTicks;
        @Nullable
        private static volatile Values testOverride;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.comment("Hoanna Survey Map.", "海纳尔勘测图").push("hoanna_survey");
            searchRadius = builder.comment("How far the map looks for the nearest site, in blocks (horizontal). It never loads chunks; "
                                    + "every search shares one server-wide budget per tick.",
                            "勘测图往外找多远（水平半径，格）。找的时候不加载区块，全服每刻共用一份预算")
                    .defineInRange("search_radius", DEFAULTS.searchRadius(), 128, 8192);
            arriveRadius = builder.comment("Within this many blocks (horizontal) of the marked spot, you've arrived: the map unmarks it and skips it next time.",
                            "离标的点水平距离在多少格以内算到了：图上去掉这一处，下次找下一处")
                    .defineInRange("arrive_radius", DEFAULTS.arriveRadius(), 4, 256);
            builder.pop();
            builder.comment("Abyssal Salt.", "渊盐").push("abyssal_salt");
            purgeRatio = builder.comment("Share of your current Hoanna radiation dose removed by one salt.", "吃一颗清掉当前海纳尔辐射剂量的几成")
                    .defineInRange("purge_ratio", DEFAULTS.purgeRatio(), 0D, 1D);
            purgeMin = builder.comment("Removes at least this much (never below 0).", "至少清掉多少（不会清到 0 以下）")
                    .defineInRange("purge_min", DEFAULTS.purgeMin(), 0, 1_000_000);
            purgeMax = builder.comment("Removes at most this much.", "至多清掉多少")
                    .defineInRange("purge_max", DEFAULTS.purgeMax(), 0, 1_000_000);
            cooldownTicks = builder.comment("Ticks before you can eat another (20 = 1 second). Kept across relogs and death.",
                            "吃完多少刻以后才能再吃（20 = 1 秒）；退出重进、死亡都不清")
                    .defineInRange("cooldown_ticks", DEFAULTS.cooldownTicks(), 0, 144_000);
            builder.pop();
        }

        /** 现在的数值：测试覆盖值 → 配置（没加载时默认值） */
        public static Values values() {
            Values override = testOverride;
            if (override != null) {
                return override;
            }
            if (searchRadius == null) {
                return DEFAULTS;
            }
            return new Values(ECConfig.get(searchRadius), ECConfig.get(arriveRadius), ECConfig.get(purgeRatio), ECConfig.get(purgeMin),
                    ECConfig.get(purgeMax), ECConfig.get(cooldownTicks));
        }

        /** GameTest 用：整套换成给定的数值，null = 回到配置。同一段同步代码里设、测、置回 */
        public static void setTestOverrides(@Nullable Values values) {
            testOverride = values;
        }
    }

    /** Forge 总线：推进搜索、渊盐冷却在登录 / 重生后补回物品冷却、服务器停了清静态表 */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
    public static final class HoannaSurveyEvents {

        private HoannaSurveyEvents() {
        }

        @SubscribeEvent
        public static void onHoannaSurveyServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                SurveySearches.tick(event.getServer());
            }
        }

        @SubscribeEvent
        public static void onHoannaSurveyLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                AbyssalSaltItem.restoreCooldown(player);
            }
        }

        @SubscribeEvent
        public static void onHoannaSurveyRespawn(PlayerEvent.PlayerRespawnEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                AbyssalSaltItem.restoreCooldown(player);
            }
        }

        @SubscribeEvent
        public static void onHoannaSurveyLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            SurveySearches.cancel(event.getEntity().getUUID());
        }

        @SubscribeEvent
        public static void onHoannaSurveyServerStopped(ServerStoppedEvent event) {
            SurveySearches.clear();
            RuinMilestoneBlock.clearCooldowns();
        }
    }
}
