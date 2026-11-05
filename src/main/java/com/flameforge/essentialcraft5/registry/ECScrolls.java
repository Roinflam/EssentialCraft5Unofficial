package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.AncientScrollItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

/**
 * 源质残卷模块（第十二批，batch12.md §4.1；卷数照 §10.1 D1 的定稿：序 + 卷一～卷六共 7 卷）。原作没有这件东西。
 * <ul>
 *   <li>物品 {@link #ANCIENT_SCROLL}（{@link AncientScrollItem}）：一个注册名，NBT {@code Volume}（0 = 序，1～6 = 卷一～卷六）区分卷；
 *       模型两层，{@code layer0} 是纸、{@code layer1} 是蜡封（灰度，按卷着色，见 {@code client/ScrollsClient}）。</li>
 *   <li>读过的状态是代码发的隐藏进度 {@code guide/scrolls/preface}、{@code book_1}～{@code book_6}，集齐是 {@code guide/scrolls/complete}
 *       （{@code lore/ScrollVolumes}）；不加玩家数据字段，不加网络包。</li>
 *   <li>掉落函数 {@code essentialcraft5:scroll_volume}（{@code lore/ScrollVolumeFunction}，自带 RegisterEvent 登记）：上古遗迹的新箱子表
 *       {@code chests/elder_mrucu}、海纳尔城塔 / 墓穴的箱子表和审判庭哨兵的表（后三张由 tools/gen/hoanna_loot.py 生成）。</li>
 *   <li>全局战利品修改器 {@code essentialcraft5:ancient_scroll_chests}（{@code lore/ScrollChestModifier}）：主世界、下界、末地的箱子
 *       另出序和卷一～卷四，只出开箱人没有的卷。</li>
 *   <li>服务端配置 {@link Config}（{@code general.ancient_scrolls}）。</li>
 * </ul>
 * 名字：命名表 tools/id_map_items.json 的 {@code ancient_scroll}（源质残卷 / Fragments of Essence）；各卷的名字、卷名、提示和消息在
 * tools/lang/keys/ecscrolls.json。手册支线「古卷」是 {@code guide/<语言>/scrolls.json}。测试 gametest/ScrollTests。
 */
public final class ECScrolls {

    /** 源质残卷：一组 16（读完消耗，副本可以转送），罕见；创造栏放 7 卷各一份（{@link AncientScrollItem#creativeVariants}） */
    public static final RegistryObject<AncientScrollItem> ANCIENT_SCROLL = ECItems.register("ancient_scroll",
            () -> new AncientScrollItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)),
            ItemSpec.of((provider, item) -> provider.withExistingParent(provider.name(item), provider.mcLoc("item/generated"))
                    .texture("layer0", provider.tex("item/ancient_scroll"))
                    .texture("layer1", provider.tex("item/ancient_scroll_seal"))));

    private ECScrolls() {
    }

    /** 由 ECRegistries.register() 调用：类加载即把物品登记进 DeferredRegister，这里再登记配置小节 general.ancient_scrolls */
    public static void init() {
        ECConfig.general(Config::define);
    }

    /**
     * 服务端配置 {@code general.ancient_scrolls}（batch12.md §4.1.6）。掉落概率写在掉落表 JSON 里（数据包能改），不在这里。
     * 读值走 {@link #values()}（没加载时是默认值）；GameTest 用 {@link #setTestOverrides} 整套换掉，不用 ConfigValue.set
     * （服务端配置自动存盘、文件监视器后台重读会冲掉刚设的值）。
     */
    public static final class Config {

        /**
         * 一套数值。
         *
         * @param enabled       假：掉落函数不出残卷、手册「古卷」整章藏掉；手里已经有的照样能读
         * @param readTicks     按住右键多少刻读完
         * @param consumeOnRead 读完消耗一张（创造模式不消耗）
         * @param setResistance 集齐给的 MRU 腐化抗性和海纳尔辐射抗性（各加这么多，算在饰品那一项里；0 = 没有奖励）
         */
        public record Values(boolean enabled, int readTicks, boolean consumeOnRead, double setResistance) {
        }

        /** 默认值：开、2 秒读完、读完消耗、集齐各 +10%（相当于一件好饰品的一项抗性，batch12.md §4.1.4） */
        public static final Values DEFAULTS = new Values(true, 40, true, 0.1D);

        private static ForgeConfigSpec.BooleanValue enabled;
        private static ForgeConfigSpec.IntValue readTicks;
        private static ForgeConfigSpec.BooleanValue consumeOnRead;
        private static ForgeConfigSpec.DoubleValue setResistance;
        @Nullable
        private static volatile Values testOverride;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.comment("Fragments of Essence (ancient_scroll): seven volumes found in ruins, in chests of the Overworld, the Nether and the End, "
                            + "in Hoanna and on Inquisitorium Sentinels.",
                    "源质残卷：散在上古遗迹、主世界 / 下界 / 末地的箱子、海纳尔和审判庭哨兵身上的七卷").push("ancient_scrolls");
            enabled = builder.comment("Whether scrolls drop at all (off: loot tables drop nothing and the research book hides the Scrolls chapter; "
                                    + "scrolls already found can still be read).",
                            "是否掉落残卷（关掉后掉落表不再出残卷，研究手册的「古卷」整章藏掉；已经拿到的照样能读）")
                    .define("enabled", DEFAULTS.enabled());
            readTicks = builder.comment("How long to hold right-click to read a scroll, in ticks (20 = 1 second).",
                            "按住右键多少刻读完一卷（20 刻 = 1 秒）")
                    .defineInRange("read_ticks", DEFAULTS.readTicks(), 1, 200);
            consumeOnRead = builder.comment("Whether a scroll is used up when read (never in creative mode). "
                                    + "Off: one scroll can be passed around the whole server.",
                            "读完是否消耗这张残卷（创造模式不消耗）。关掉后一张残卷可以传遍全服")
                    .define("consume_on_read", DEFAULTS.consumeOnRead());
            setResistance = builder.comment("MRU corruption resistance and Hoanna radiation resistance given for good once all volumes are read "
                                    + "(7, or 5 while Gates to Hoanna are disabled); added like an accessory's resistance. 0 = no reward.",
                            "读完全部残卷（7 卷，海纳尔之门关着时 5 卷）后永久增加的 MRU 腐化抗性和海纳尔辐射抗性，和饰品的抗性加在一起；0 = 没有奖励")
                    .defineInRange("set_resistance", DEFAULTS.setResistance(), 0D, 0.4D);
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
            return new Values(ECConfig.get(enabled), ECConfig.get(readTicks), ECConfig.get(consumeOnRead), ECConfig.get(setResistance));
        }

        /** GameTest 用：整套换成给定的数值，null = 回到配置。同一段同步代码里设、测、置回 */
        public static void setTestOverrides(@Nullable Values values) {
            testOverride = values;
        }
    }
}
