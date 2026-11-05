package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.client.ley.LeyScanClient;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.LeyDetectorItem;
import com.flameforge.essentialcraft5.network.ECNetwork;
import com.flameforge.essentialcraft5.network.S2CLeyScanPacket;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

/**
 * 地脉探测仪模块（第十二批，batch12.md §4.3；policy §4.15）：EC5 新物品，原作没有。中期找矿工具，和地脉投影仪、地脉凝聚器连成「地脉」一条线。
 * <ul>
 *   <li>物品 {@link #LEY_DETECTOR}（{@link LeyDetectorItem}）：自带 MRU（{@code general.ley_detector.max_mru}），右键花 MRU 扫一次周围的矿，
 *       轮廓只画在使用者自己的屏幕上；潜行右键切「全部矿石 / 只看贵重」。配方 {@code data/essentialcraft5/recipes/leydetector/ley_detector.json}
 *       （米斯莱锭 ×4、魔力屏幕、地之焦点 ×2、魔力单片镜、指南针），第十一章拿到米斯莱以后能做。</li>
 *   <li>网络包 {@link S2CLeyScanPacket}：服务端只扣费、上冷却、给本人发这一个包，扫描和描边全在客户端（{@code client/ley/LeyScanClient}），服务器不扫一格方块。</li>
 *   <li>方块标签（{@code tools/tags/ecleydetector.json}）：{@link #TARGETS} 算矿（默认 {@code #forge:ores}）、{@link #VALUABLE} 贵重（金色）、
 *       {@link #EC} 本模组的元素蕴魔矿（紫色）、{@link #IGNORED} 不画（默认空，给整合包作者）。</li>
 *   <li>手册进度 {@code guide/gear/ley_detector}（拿到探测仪），条目在手册 gear 章末尾。</li>
 *   <li>服务端配置 {@link Config}（{@code general.ley_detector}）。</li>
 * </ul>
 * 名字在命名表 tools/id_map_items.json（{@code ley_detector}，{@code old_id} 为 null），提示与消息文字在 tools/lang/keys/ecleydetector.json。
 * 测试 gametest/LeyDetectorTests。
 */
public final class ECLeyDetector {

    /** 地脉探测仪：叠 1，平面贴图 */
    public static final RegistryObject<LeyDetectorItem> LEY_DETECTOR = ECItems.register("ley_detector",
            () -> new LeyDetectorItem(new Item.Properties().stacksTo(1)), ItemSpec.generated("item/ley_detector"));

    /** 扫描时算「矿」的方块 */
    public static final TagKey<Block> TARGETS = ECTags.modBlock("ley_detector/targets");
    /** 贵重矿：金色描边，「只看贵重」模式只画这些 */
    public static final TagKey<Block> VALUABLE = ECTags.modBlock("ley_detector/valuable");
    /** 本模组的元素蕴魔矿：紫色描边 */
    public static final TagKey<Block> EC = ECTags.modBlock("ley_detector/ec");
    /** 即使在 {@link #TARGETS} 里也不画 */
    public static final TagKey<Block> IGNORED = ECTags.modBlock("ley_detector/ignored");

    private ECLeyDetector() {
    }

    /** 由 ECRegistries.register() 调用：登记配置小节 general.ley_detector 和扫描包（只服务端发、只客户端收） */
    public static void init() {
        ECConfig.general(Config::define);
        ECNetwork.registerS2C(S2CLeyScanPacket.class, S2CLeyScanPacket::encode, S2CLeyScanPacket::decode,
                packet -> LeyScanClient.handle(packet));
    }

    /**
     * 服务端配置 {@code general.ley_detector}（batch12.md §4.3.4）。读值走 {@link #values()}（没加载时是默认值）；GameTest 用 {@link #setTestOverrides}
     * 整套换掉，不许用 ConfigValue.set（服务端配置自动存盘、文件监视器后台重读会冲掉刚设的值）。
     */
    public static final class Config {

        /**
         * 一套数值。
         *
         * @param enabled        假：右键没有反应（潜行切模式照样能切）
         * @param maxMru         探测仪自己最多存多少 MRU
         * @param mruPerScan     每扫一次花多少 MRU
         * @param cooldownTicks  扫完一次的冷却（刻）
         * @param radius         扫描半径（格，以玩家所在格为中心的立方体，边长 2r+1），随包发给客户端
         * @param displayTicks   轮廓画多久（刻），最后 {@link LeyScanClient#FADE_TICKS} 刻淡出
         * @param maxHighlights  最多画几格（留离玩家最近的）
         */
        public record Values(boolean enabled, int maxMru, int mruPerScan, int cooldownTicks, int radius, int displayTicks, int maxHighlights) {
        }

        /**
         * 默认值：存 1 万、每次 1 500、冷却 10 秒、半径 16、画 15 秒、最多 256 格。
         * 半径 16 盖住一段支线矿道，再大就成了全图透视，客户端开销也按立方涨；1 500 MRU 便宜，限频率靠冷却；15 秒够看清走过去（batch12.md §4.3.2）
         */
        public static final Values DEFAULTS = new Values(true, 10000, 1500, 200, 16, 300, 256);

        private static ForgeConfigSpec.BooleanValue enabled;
        private static ForgeConfigSpec.IntValue maxMru;
        private static ForgeConfigSpec.IntValue mruPerScan;
        private static ForgeConfigSpec.IntValue cooldownTicks;
        private static ForgeConfigSpec.IntValue radius;
        private static ForgeConfigSpec.IntValue displayTicks;
        private static ForgeConfigSpec.IntValue maxHighlights;
        @Nullable
        private static volatile Values testOverride;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.comment("Ley Detector.", "地脉探测仪").push("ley_detector");
            enabled = builder.comment("If false, right-clicking the Ley Detector does nothing.", "假：右键地脉探测仪没有反应")
                    .define("enabled", DEFAULTS.enabled());
            maxMru = builder.comment("MRU the detector itself can hold (charge it in a Charging Chamber).",
                            "探测仪自己最多存多少 MRU（放进充能仓充）")
                    .defineInRange("max_mru", DEFAULTS.maxMru(), 1, 100_000_000);
            mruPerScan = builder.comment("MRU spent per scan (taken from MRU storages you carry first, then from the detector itself).",
                            "每扫一次花多少 MRU（先扣身上的储存物品，凑不够再扣探测仪自己存的）")
                    .defineInRange("mru_per_scan", DEFAULTS.mruPerScan(), 0, 1_000_000);
            cooldownTicks = builder.comment("Cooldown after each scan, in ticks (20 = 1 second).", "每扫一次的冷却（刻，20 = 1 秒）")
                    .defineInRange("cooldown_ticks", DEFAULTS.cooldownTicks(), 0, 72000);
            radius = builder.comment("Scan radius in blocks: a cube of side 2r+1 centered on the player. The scan runs on the user's own client, "
                                    + "over block data that client already has; the server does not scan anything.",
                            "扫描半径（格）：以玩家所在格为中心、边长 2r+1 的立方体。扫描在使用者自己的客户端上跑，只看客户端本来就有的方块数据，服务端不扫")
                    .defineInRange("radius", DEFAULTS.radius(), 4, 32);
            displayTicks = builder.comment("How long the outlines stay on screen, in ticks; they fade out over the last 60 ticks.",
                            "轮廓在屏幕上留多久（刻），最后 60 刻淡出")
                    .defineInRange("display_ticks", DEFAULTS.displayTicks(), 20, 6000);
            maxHighlights = builder.comment("At most this many ore blocks are outlined per scan (the nearest ones).", "每次最多描几格（留最近的）")
                    .defineInRange("max_highlights", DEFAULTS.maxHighlights(), 16, 1024);
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
            return new Values(ECConfig.get(enabled), ECConfig.get(maxMru), ECConfig.get(mruPerScan), ECConfig.get(cooldownTicks),
                    ECConfig.get(radius), ECConfig.get(displayTicks), ECConfig.get(maxHighlights));
        }

        /** GameTest 用：整套换成给定的数值，null = 回到配置。同一段同步代码里设、测、置回 */
        public static void setTestOverrides(@Nullable Values values) {
            testOverride = values;
        }
    }
}
