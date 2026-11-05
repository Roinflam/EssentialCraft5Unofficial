package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.HomewardCompassItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.model.generators.ItemModelBuilder;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 归途罗盘模块（第十二批，batch12.md §4.5；policy §4.15）：EC5 新物品，原作没有。中后期回家工具。
 * <ul>
 *   <li>物品 {@link #HOMEWARD_COMPASS}（{@link HomewardCompassItem}）：潜行右键方块记一个归途点，右键按住引导 5 秒传回去；
 *       同维度 5 000 + 10/格（封顶 5 万）MRU，跨维度 10 万，全从身上的储存物品扣；冷却 5 / 10 分钟；不能从别的维度传进海纳尔。
 *       配方 {@code data/essentialcraft5/recipes/homewardcompass/homeward_compass.json}（末影鳞合金 ×4、风之水晶、末影之眼 ×2、暗黑灵魂物质、指南针），
 *       第十三章（风之水晶）能做。</li>
 *   <li>引导表、受伤打断、权限待定重试、战斗锁记录、冷却的登录补回 / 死亡清除在 {@code teleport/HomewardChannels}。</li>
 *   <li>模型：没绑定 {@code item/homeward_compass}、绑定后按物品属性 {@link HomewardCompassItem#BOUND_PREDICATE} 换 {@code item/homeward_compass_bound}
 *       （客户端属性在 {@code client/HomewardCompassClient} 注册）。</li>
 *   <li>手册进度 {@code guide/gear/homeward_compass}（拿到罗盘），条目在手册 gear 章末尾。</li>
 *   <li>服务端配置 {@link Config}（{@code general.homeward_compass}）。</li>
 * </ul>
 * 不加网络包、不加玩家能力字段（冷却记在玩家持久数据 PlayerPersisted 下的一个 long 键里，同全息诱饵投影仪）。
 * 名字在命名表 tools/id_map_items.json（{@code homeward_compass}，{@code old_id} 为 null），提示与消息文字在 tools/lang/keys/echomewardcompass.json。
 * 测试 gametest/HomewardCompassTests。
 */
public final class ECHomewardCompass {

    /** 归途罗盘：叠 1；绑定后换贴图 */
    public static final RegistryObject<HomewardCompassItem> HOMEWARD_COMPASS = ECItems.register("homeward_compass",
            () -> new HomewardCompassItem(new Item.Properties().stacksTo(1)),
            ItemSpec.of((provider, item) -> {
                ItemModelBuilder bound = provider.withExistingParent("homeward_compass_bound", provider.mcLoc("item/generated"))
                        .texture("layer0", provider.tex("item/homeward_compass_bound"));
                provider.generated(item, "item/homeward_compass")
                        .override().predicate(HomewardCompassItem.BOUND_PREDICATE, 1F).model(bound).end();
            }));

    private ECHomewardCompass() {
    }

    /** 由 ECRegistries.register() 调用：登记配置小节 general.homeward_compass */
    public static void init() {
        ECConfig.general(Config::define);
    }

    /**
     * 服务端配置 {@code general.homeward_compass}（batch12.md §4.5.4）。读值走 {@link #values()}（没加载时是默认值）；GameTest 用 {@link #setTestOverrides}
     * 整套换掉，不许用 ConfigValue.set（服务端配置自动存盘、文件监视器后台重读会冲掉刚设的值）。
     */
    public static final class Config {

        /**
         * 一套数值。
         *
         * @param enabled                     假：起手无效（绑定照样能绑）
         * @param channelTicks                引导多少刻
         * @param moveTolerance               引导中离起点超过多少格（水平 + 竖直的直线距离）算走开
         * @param combatLockTicks             被生物（含玩家）打过以后多少刻内不能起手
         * @param cooldownTicks               同维度传送成功后的冷却（刻）
         * @param crossDimensionCooldownTicks 跨维度传送成功后的冷却（刻）
         * @param baseMru                     同维度起价
         * @param mruPerBlock                 同维度每格水平距离加多少
         * @param maxMruSameDimension         同维度封顶
         * @param crossDimensionMru           跨维度一口价
         * @param allowCrossDimension         假：只能回同一个维度里的归途点
         * @param deniedTargetDimensions      不能从别的维度传进去的维度（同维度里传不受限）
         */
        public record Values(boolean enabled, int channelTicks, double moveTolerance, int combatLockTicks, int cooldownTicks,
                             int crossDimensionCooldownTicks, int baseMru, int mruPerBlock, int maxMruSameDimension, int crossDimensionMru,
                             boolean allowCrossDimension, List<String> deniedTargetDimensions) {
        }

        /**
         * 默认值（batch12.md §4.5.2）：引导 5 秒、走开 1.5 格打断、挨打后 10 秒不能起手、冷却 5 / 10 分钟；
         * 同维度 5 000 + 10/格、封顶 5 万（和魔力传送器同维度封顶一样），跨维度 10 万（要鲜红灵魂物质一级的储存，是传送器一满罐的两倍，
         * 因为罗盘不用在两头各搭一台）；海纳尔只能走门进去（门有风之真名这一道前置，还受 portal_enabled 管）。
         */
        public static final Values DEFAULTS = new Values(true, 100, 1.5D, 200, 6000, 12000, 5000, 10, 50000, 100000, true,
                List.of("essentialcraft5:hoanna"));

        private static ForgeConfigSpec.BooleanValue enabled;
        private static ForgeConfigSpec.IntValue channelTicks;
        private static ForgeConfigSpec.DoubleValue moveTolerance;
        private static ForgeConfigSpec.IntValue combatLockTicks;
        private static ForgeConfigSpec.IntValue cooldownTicks;
        private static ForgeConfigSpec.IntValue crossDimensionCooldownTicks;
        private static ForgeConfigSpec.IntValue baseMru;
        private static ForgeConfigSpec.IntValue mruPerBlock;
        private static ForgeConfigSpec.IntValue maxMruSameDimension;
        private static ForgeConfigSpec.IntValue crossDimensionMru;
        private static ForgeConfigSpec.BooleanValue allowCrossDimension;
        private static ForgeConfigSpec.ConfigValue<List<? extends String>> deniedTargetDimensions;
        @Nullable
        private static volatile Values testOverride;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.comment("Homeward Compass.", "归途罗盘").push("homeward_compass");
            enabled = builder.comment("If false, the compass can't start a trip home (binding still works).", "假：罗盘不能起手（绑定照样能绑）")
                    .define("enabled", DEFAULTS.enabled());
            channelTicks = builder.comment("How long you hold right-click before you're sent home, in ticks (20 = 1 second).",
                            "要按住右键引导多少刻才传走（20 = 1 秒）")
                    .defineInRange("channel_ticks", DEFAULTS.channelTicks(), 20, 600);
            moveTolerance = builder.comment("Moving farther than this many blocks from where you started (straight-line distance) breaks the channel.",
                            "引导中离起点超过多少格（直线距离）就打断")
                    .defineInRange("move_tolerance", DEFAULTS.moveTolerance(), 0.5D, 8D);
            combatLockTicks = builder.comment("After you're hit by any mob or player, you can't start a trip for this many ticks.",
                            "被任何生物（含玩家）打过以后多少刻内不能起手")
                    .defineInRange("combat_lock_ticks", DEFAULTS.combatLockTicks(), 0, 6000);
            cooldownTicks = builder.comment("Cooldown after a trip within the same dimension, in ticks. Kept across relogs, cleared on death.",
                            "同维度传送成功后的冷却（刻）；退出重进不清，死亡清掉")
                    .defineInRange("cooldown_ticks", DEFAULTS.cooldownTicks(), 0, 72000);
            crossDimensionCooldownTicks = builder.comment("Cooldown after a trip to another dimension, in ticks.", "跨维度传送成功后的冷却（刻）")
                    .defineInRange("cross_dimension_cooldown_ticks", DEFAULTS.crossDimensionCooldownTicks(), 0, 144000);
            baseMru = builder.comment("Base MRU for a trip within the same dimension. All MRU comes from storages you carry.",
                            "同维度传送的起价（MRU），全从身上的储存物品扣")
                    .defineInRange("base_mru", DEFAULTS.baseMru(), 0, 10_000_000);
            mruPerBlock = builder.comment("Extra MRU per block of horizontal distance (rounded up) within the same dimension.",
                            "同维度每格水平距离（向上取整）另加多少 MRU")
                    .defineInRange("mru_per_block", DEFAULTS.mruPerBlock(), 0, 10_000);
            maxMruSameDimension = builder.comment("Cap on the MRU for a trip within the same dimension.", "同维度传送最多花多少 MRU")
                    .defineInRange("max_mru_same_dimension", DEFAULTS.maxMruSameDimension(), 0, 100_000_000);
            crossDimensionMru = builder.comment("MRU for a trip to another dimension (flat price).", "跨维度传送花多少 MRU（一口价）")
                    .defineInRange("cross_dimension_mru", DEFAULTS.crossDimensionMru(), 0, 1_000_000_000);
            allowCrossDimension = builder.comment("If false, the compass only takes you home within the same dimension.", "假：只能回同一个维度里的归途点")
                    .define("allow_cross_dimension", DEFAULTS.allowCrossDimension());
            deniedTargetDimensions = builder.comment("Dimensions the compass can't take you into from another dimension. Trips within one of them still work.",
                            "不能从别的维度传进去的维度；已经在里面时，在里面传不受限")
                    .defineListAllowEmpty("denied_target_dimensions", DEFAULTS.deniedTargetDimensions(),
                            o -> o instanceof String s && ResourceLocation.tryParse(s) != null);
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
            List<String> denied = new ArrayList<>(ECConfig.getValue(deniedTargetDimensions));
            return new Values(ECConfig.get(enabled), ECConfig.get(channelTicks), ECConfig.get(moveTolerance), ECConfig.get(combatLockTicks),
                    ECConfig.get(cooldownTicks), ECConfig.get(crossDimensionCooldownTicks), ECConfig.get(baseMru), ECConfig.get(mruPerBlock),
                    ECConfig.get(maxMruSameDimension), ECConfig.get(crossDimensionMru), ECConfig.get(allowCrossDimension), List.copyOf(denied));
        }

        /** GameTest 用：整套换成给定的数值，null = 回到配置。同一段同步代码里设、测、置回 */
        public static void setTestOverrides(@Nullable Values values) {
            testOverride = values;
        }
    }
}
