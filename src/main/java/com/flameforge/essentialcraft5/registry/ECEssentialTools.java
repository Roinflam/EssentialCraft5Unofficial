package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.EssentialAxeItem;
import com.flameforge.essentialcraft5.item.EssentialPickaxeItem;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.TierSortingRegistry;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 源质镐 / 源质斧模块（第十二批，batch12.md §4.4；policy §4.15）：EC5 新工具，原作没有。第十三章（风之水晶）以后的主力工具，
 * 镐按挖的那一面开 3×3，斧把整棵天然树砍倒，每一格都是玩家本人的一次真正的拆方块（{@code gameMode.destroyBlock}），推迟到服务端 tick 末尾做，
 * 拆成了才扣 MRU（{@code item/EssentialToolEvents}）。
 * <p>
 * 和魔力挖掘器分工（batch12.md §4.4.1）：挖掘器是「同种方块 3×3×3」（挖矿脉），源质镐是「一面 3×3、什么石头都挖」（开路），所以配方不吃挖掘器。
 * <ul>
 *   <li>{@code essential_pickaxe}（{@link EssentialPickaxeItem}）：配方 MWM / RER / _D_（米斯莱板 ×2、风之水晶、MRU 共鸣水晶 ×2、元素镐、暗黑灵魂物质）。</li>
 *   <li>{@code essential_axe}（{@link EssentialAxeItem}）：同上，元素镐换成元素斧。</li>
 * </ul>
 * 材料 {@link EssentialTier#ESSENTIAL}：挖掘等级、伤害、攻速同下界合金（等级 4、攻击加成 4，挖掘速度 12），附魔能力 15，没有耐久；
 * 排序登记在下界合金之后、元素材料之前。镐 {@code PickaxeItem(1, -2.8)} → 攻击 6；斧 {@code AxeItem(5, -3.0)} → 攻击 10，和下界合金镐 / 斧一样。
 * <p>
 * 文字在 {@code tools/lang/keys/ecessentialtools.json}，方块标签 {@code essentialcraft5:essential_tools/no_area}（默认只有紫水晶母岩）在
 * {@code tools/tags/ecessentialtools.json}，配方 {@code data/essentialcraft5/recipes/essentialtools/}，
 * 手册在「装备与防护」章 {@code gear} 的 {@code essential_tools} 条目，GameTest {@code gametest/EssentialToolTests}。
 */
public final class ECEssentialTools {

    /**
     * 源质镐不连带挖的方块：整合包作者想让某些方块只能一格一格挖时往里加。默认放着紫水晶母岩：它挖下来什么都不掉，
     * 3×3 挖隧道时顺手连带会把晶洞的母岩悄悄拆光
     */
    public static final TagKey<Block> NO_AREA = ECTags.modBlock("essential_tools/no_area");

    public static final RegistryObject<EssentialPickaxeItem> ESSENTIAL_PICKAXE = ECItems.register("essential_pickaxe",
            () -> new EssentialPickaxeItem(EssentialTier.ESSENTIAL, 1, -2.8F, new Item.Properties().stacksTo(1)),
            ItemSpec.handheld("item/essential_pickaxe").tags(ItemTags.PICKAXES, ItemTags.CLUSTER_MAX_HARVESTABLES));

    public static final RegistryObject<EssentialAxeItem> ESSENTIAL_AXE = ECItems.register("essential_axe",
            () -> new EssentialAxeItem(EssentialTier.ESSENTIAL, 5F, -3.0F, new Item.Properties().stacksTo(1)),
            ItemSpec.handheld("item/essential_axe").tags(ItemTags.AXES));

    private ECEssentialTools() {
    }

    /** 由 ECRegistries.register() 调用：类加载即把上面的条目登记进 DeferredRegister，这里再登记材料排序和配置小节 general.essential_tools */
    public static void init() {
        EssentialTier.init();
        ECConfig.general(Config::define);
    }

    /**
     * 两件工具的材料（batch12.md §4.4.2）：挖掘等级同下界合金（4）、攻击加成 4、挖掘速度 12（快速状态下适用方块的速度，见 {@code EssentialTool}）、
     * 附魔能力 15、耐久 0（{@code TieredItem} 按它设最大耐久，0 = 不会损坏）、没有修复材料。
     * 用 {@link TierSortingRegistry} 排在下界合金之后、元素材料之前：要下界合金工具才掉落的方块（含别的模组用 Forge 标签声明的）照常掉落。
     */
    public enum EssentialTier implements Tier {
        ESSENTIAL;

        static {
            TierSortingRegistry.registerTier(ESSENTIAL, EssentialCraft5.id("essential"), List.of(Tiers.NETHERITE),
                    List.of(EssentialCraft5.id("elemental")));
        }

        /** 触发类加载（登记排序） */
        public static void init() {
        }

        @Override
        public int getUses() {
            return 0;
        }

        @Override
        public float getSpeed() {
            return 12F;
        }

        @Override
        public float getAttackDamageBonus() {
            return 4F;
        }

        /** 只给没接 TierSortingRegistry 的旧代码看；挖掘判定走排序 */
        @Override
        @Deprecated
        public int getLevel() {
            return 4;
        }

        @Override
        public int getEnchantmentValue() {
            return 15;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.EMPTY;
        }

        /** 没有「要这一档才挖得动」的方块 */
        @Override
        @Nullable
        public TagKey<Block> getTag() {
            return null;
        }
    }

    /**
     * 服务端配置 {@code general.essential_tools}（batch12.md §4.4.4）。SERVER 配置随进服同步给客户端：挖掘速度看「工具自己存的 MRU 够不够一格」，
     * 两端用同一个每格价钱算，才不会一边快一边慢。MRU 容量在物品建出来时取一次（同宅男套装），改小以后存量按新容量截断。
     * GameTest 用 {@link #setTestOverrides}，不碰真配置。
     */
    public static final class Config {

        /**
         * 一套数值。
         *
         * @param maxMru           每把自带多少 MRU
         * @param mruPerBlock      源质镐每拆成一格（含中心）扣多少
         * @param mruPerLog        源质斧每砍成一根（含中心）扣多少
         * @param hardnessMargin   镐的邻格硬度最多比中心那格高多少
         * @param maxLogs          一棵树最多砍几根（含中心）
         * @param maxScan          找树最多看几格
         * @param fellingRadius    找树的水平范围（|dx|、|dz| 都不超过）
         * @param fellingHeight    找树往上的范围（dy 在 0 到它之间，不往下找）
         * @param leavesRequired   原木周围至少几片天然树叶才算天然树（0 = 不查）
         * @param logsPerTick      每个砍树任务每刻最多砍几根
         * @param logsPerTickTotal 全服所有砍树任务每刻合计最多砍几根
         */
        public record Values(int maxMru, int mruPerBlock, int mruPerLog, double hardnessMargin, int maxLogs, int maxScan,
                             int fellingRadius, int fellingHeight, int leavesRequired, int logsPerTick, int logsPerTickTotal) {

            /** 拿这一套换掉找树的两项（GameTest 直接拿它调 {@code EssentialToolEvents.planFelling}，不改配置） */
            public Values withFelling(int maxLogsValue, int leavesRequiredValue) {
                return new Values(maxMru, mruPerBlock, mruPerLog, hardnessMargin, maxLogsValue, maxScan, fellingRadius, fellingHeight,
                        leavesRequiredValue, logsPerTick, logsPerTickTotal);
            }
        }

        /** 默认值（batch12.md §4.4.4）：5 万 MRU、镐每格 10、斧每根 20、硬度宽限 3、一棵 128 根（D11）、找树 1 024 格 / 水平 10 / 往上 48、天然树叶 4 片、每任务每刻 16 根、全服每刻 64 根 */
        public static final Values DEFAULTS = new Values(50_000, 10, 20, 3.0D, 128, 1_024, 10, 48, 4, 16, 64);

        private static ForgeConfigSpec.IntValue maxMru;
        private static ForgeConfigSpec.IntValue mruPerBlock;
        private static ForgeConfigSpec.IntValue mruPerLog;
        private static ForgeConfigSpec.DoubleValue hardnessMargin;
        private static ForgeConfigSpec.IntValue maxLogs;
        private static ForgeConfigSpec.IntValue maxScan;
        private static ForgeConfigSpec.IntValue fellingRadius;
        private static ForgeConfigSpec.IntValue fellingHeight;
        private static ForgeConfigSpec.IntValue leavesRequired;
        private static ForgeConfigSpec.IntValue logsPerTick;
        private static ForgeConfigSpec.IntValue logsPerTickTotal;
        @Nullable
        private static volatile Values testOverride;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.comment("Essential Pickaxe and Essential Axe: the pickaxe mines a 3x3 on the face you hit, the axe fells a whole natural tree. Each block is a real break by the player, done at the end of the server tick and paid only when it breaks.",
                    "源质镐 / 源质斧：镐按挖的那一面开 3×3，斧砍倒整棵天然树。每一格都是玩家本人的一次真正的拆方块，在服务端 tick 末尾做，拆成了才扣 MRU").push("essential_tools");
            maxMru = builder.comment("MRU stored by each tool. Takes effect on tools created afterwards; existing ones are clamped to the new capacity.",
                            "每把工具自带多少 MRU（新建出来的工具按新值算，已有的存量按新容量截断）")
                    .defineInRange("max_mru", DEFAULTS.maxMru(), 1, 100_000_000);
            mruPerBlock = builder.comment("MRU the pickaxe spends per block it breaks, the one you mine included. The tool mines fast only while it holds at least this much itself.",
                            "源质镐每拆成一格（含你亲手挖的那格）扣多少 MRU；工具自己存的不到这个数时挖得很慢")
                    .defineInRange("mru_per_block", DEFAULTS.mruPerBlock(), 0, 10_000);
            mruPerLog = builder.comment("MRU the axe spends per log it cuts, the one you chop included. The tool chops fast only while it holds at least this much itself.",
                            "源质斧每砍成一根（含你亲手砍的那根）扣多少 MRU；工具自己存的不到这个数时砍得很慢")
                    .defineInRange("mru_per_log", DEFAULTS.mruPerLog(), 0, 10_000);
            hardnessMargin = builder.comment("Pickaxe: a surrounding block is mined only if its hardness is at most the center block's hardness plus this (stone 1.5, obsidian 50).",
                            "源质镐：邻格的硬度最多比中心那格高多少才一起挖（石头 1.5、黑曜石 50）")
                    .defineInRange("hardness_margin", DEFAULTS.hardnessMargin(), 0D, 100D);
            maxLogs = builder.comment("Axe: most logs cut from one tree, the one you chop included (the largest vanilla trees have about 100).",
                            "源质斧：一棵树最多砍几根（含你亲手砍的那根；原版最大的树约 100 根）")
                    .defineInRange("max_logs", DEFAULTS.maxLogs(), 1, 1_024);
            maxScan = builder.comment("Axe: most blocks looked at when finding the tree (once per chop).",
                            "源质斧：找树时最多看几格（每砍一次找一次）")
                    .defineInRange("max_scan", DEFAULTS.maxScan(), 64, 8_192);
            fellingRadius = builder.comment("Axe: horizontal reach of the tree search, in blocks from the chopped log.",
                            "源质斧：找树的水平范围（离你砍的那根几格以内）")
                    .defineInRange("felling_radius", DEFAULTS.fellingRadius(), 1, 16);
            fellingHeight = builder.comment("Axe: upward reach of the tree search, in blocks above the chopped log (it never searches below it).",
                            "源质斧：找树往上的范围（你砍的那根以上几格；不往下找）")
                    .defineInRange("felling_height", DEFAULTS.fellingHeight(), 1, 128);
            leavesRequired = builder.comment("Axe: natural leaves (or nether wart blocks) that must touch the logs for the whole tree to fall; otherwise only the chopped log breaks. 0 = no check.",
                            "源质斧：原木周围至少有几片天然树叶（或下界疣块 / 诡异疣块）才整棵砍倒，否则只砍你砍的那根；0 = 不查")
                    .defineInRange("leaves_required", DEFAULTS.leavesRequired(), 0, 64);
            logsPerTick = builder.comment("Axe: most logs one felling cuts per tick.", "源质斧：每棵树每刻最多砍几根")
                    .defineInRange("logs_per_tick", DEFAULTS.logsPerTick(), 1, 128);
            logsPerTickTotal = builder.comment("Axe: most logs all fellings together cut per tick.", "源质斧：全部正在砍的树每刻合计最多砍几根")
                    .defineInRange("logs_per_tick_total", DEFAULTS.logsPerTickTotal(), 1, 1_024);
            builder.pop();
        }

        /** 现在的数值：测试覆盖值 → 配置（没加载时默认值） */
        public static Values values() {
            Values override = testOverride;
            if (override != null) {
                return override;
            }
            if (maxMru == null) {
                return DEFAULTS;
            }
            return new Values(ECConfig.get(maxMru), ECConfig.get(mruPerBlock), ECConfig.get(mruPerLog), ECConfig.get(hardnessMargin),
                    ECConfig.get(maxLogs), ECConfig.get(maxScan), ECConfig.get(fellingRadius), ECConfig.get(fellingHeight),
                    ECConfig.get(leavesRequired), ECConfig.get(logsPerTick), ECConfig.get(logsPerTickTotal));
        }

        /** GameTest 用：整套换成给定的数值，null = 回到配置。跨刻的测试在最后一步置回 */
        public static void setTestOverrides(@Nullable Values values) {
            testOverride = values;
        }
    }
}
