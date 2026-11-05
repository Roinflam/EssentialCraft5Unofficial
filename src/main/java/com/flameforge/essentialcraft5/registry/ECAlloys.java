package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.block.DyeableBlock;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.client.model.generators.BlockModelBuilder;
import net.minecraftforge.registries.RegistryObject;

/**
 * 中期材料模块（第二批）：魔力精华、水晶粉、四种合金、注魔合金、日光玻璃、MRU 共鸣水晶、淡青系列，
 * 以及魔力护板 / 淡青护板、两种栅栏、6 盏水晶灯。没有机器、没有界面，只有物品、方块和配方。
 * <p>
 * 新旧注册名（总表见 tools/id_map_*.json）：
 * <ul>
 *   <li>genItem:N（ItemGenericEC）→ 各字段注释里写了 meta；原拼写 magicPurifyed* 改成 magic_purified_*</li>
 *   <li>magicPlating / platingPale（BlockDyeable）→ magic_plating / pale_plating</li>
 *   <li>fFence / magicFence（BlockFenceEC）→ fortified_stone_fence / magic_fence（原版 FenceBlock）</li>
 *   <li>crystalLamp:0~5（BlockCrystalLamp，按 EnumDropType 分 meta）→ fire / water / earth / air / elemental / mithriline_crystal_lamp</li>
 * </ul>
 * 配方都在 data/essentialcraft5/recipes/alloys/：魔力工作台 10 条（RecipesCore L63-64、L70-72、L78-80、L82、L90）、
 * 工作台 10 条；黄金法球（L65）和淡青块预写，带 forge:item_exists，跟着注册它们的批次生效。
 * 辐射仓的 5 条（魔力精华、共鸣水晶、淡青珍珠 / 锭 / 元素核心）第一批已经预写在 recipes/radiating_chamber/，注册后自动生效。
 * <p>
 * 标签在 tools/tags/ecalloys.json：淡青四样进本模组的「原版或淡青」标签（essentialcraft5:gold_ingots、ender_pearls、plates/magic、cores/magic），
 * <b>不进任何 forge 共享标签</b>（原作 allowPaleItemsInOtherRecipes 是隐性联动，能把 1 颗钻石变成 4 颗「钻石」）；
 * 两种护板进富集仓外壳标签 essentialcraft5:mrucuec_casing（ECEnrichment 建）。
 * 注魔合金（×5）和共鸣水晶（×4）的工作台升级已在 api/MagicianTableUpgrades 按注册名登记，这里不用写代码；
 * 护板的 MRU 抗性（魔力 5、淡青 10）在 entity/MRUResistances 的默认表里，按方块登记，染成什么颜色都一样。
 * 研究手册是「中期材料」一章：assets/essentialcraft5/guide/alloys.json，文字在 tools/lang/keys/ecalloys.json。
 */
public final class ECAlloys {

    // ---------------- 中期材料（ItemGenericEC，genItem:N） ----------------

    /** genItem:3（矿辞 dustMagic）；辐射仓：红石 + 烈焰粉。和水晶提取器抽的「元素精华」不是一种东西 */
    public static final RegistryObject<Item> MAGICAL_ESSENCE = item("magical_essence", "magicalessence");
    /** genItem:20；魔力工作台：魔力精华，3000 */
    public static final RegistryObject<Item> CRYSTAL_DUST = item("crystal_dust", "crystaldust");
    /** genItem:79；魔力工作台：魔力强化板 + 烈焰粉 ×4，500 */
    public static final RegistryObject<Item> MAGIC_PURIFIED_BLAZE_ALLOY = item("magic_purified_blaze_alloy", "magicpurifyedblazealloy");
    /** genItem:9（矿辞 plateGlass）；魔力工作台：魔力强化板 + 玻璃 ×4，1000 */
    public static final RegistryObject<Item> MAGIC_PURIFIED_GLASS_ALLOY = item("magic_purified_glass_alloy", "magicpurifyedglassalloy");
    /** genItem:11；魔力工作台：魔力强化板 + 红石 ×4，1000 */
    public static final RegistryObject<Item> MAGIC_PURIFIED_REDSTONE_ALLOY = item("magic_purified_redstone_alloy", "magicpurifyedredstonealloy");
    /** genItem:10（矿辞 ingotGoldMagical，显示名 Magical Golden Ingot）；魔力工作台：金锭 + 金粒 ×4，250 */
    public static final RegistryObject<Item> MAGIC_PURIFIED_GOLD_ALLOY = item("magic_purified_gold_alloy", "magicpurifyedgoldalloy");
    /** genItem:0；魔力工作台升级 ×5。魔力工作台：魔力强化板 + 烈焰合金 ×2 + 末影鳞合金 ×2（两条对角摆法），10000 */
    public static final RegistryObject<Item> COMBINED_MAGICAL_ALLOYS = item("combined_magical_alloys", "combinedmagicalalloys");
    /** genItem:32；魔力工作台：玻璃合金 + 钻石板 ×2 + 绿宝石板 ×2（两条对角摆法），10000 */
    public static final RegistryObject<Item> SUN_IMBUED_GLASS = item("sun_imbued_glass", "sunimbuedglass");
    /** genItem:44（矿辞 gemResonant）；魔力工作台升级 ×4。辐射仓：钻石 + 绿宝石，100 tick × 每 tick 50 MRU */
    public static final RegistryObject<Item> MRU_RESONATING_CRYSTAL = item("mru_resonating_crystal", "mruresonatingcrystal");

    // ---------------- 淡青系列 ----------------

    /** genItem:38；在 essentialcraft5:ender_pearls 里（EC 自己的配方能拿它当末影珍珠）。辐射仓：青金石 + 萤石粉 */
    public static final RegistryObject<Item> PALE_PEARL = item("pale_pearl", "palepearl");
    /** genItem:39；在 essentialcraft5:gold_ingots 里。辐射仓：青金石块 + 金锭 → 4 个 */
    public static final RegistryObject<Item> PALE_INGOT = item("pale_ingot", "paleingot");
    /** genItem:41（原作显示名 Pale Plating，和方块同名）；在 essentialcraft5:plates/magic 里。魔力工作台：魔力强化板 + 淡青锭 ×4，100 */
    public static final RegistryObject<Item> PALE_PLATE = item("pale_plate", "paleplate");
    /** genItem:42；在 essentialcraft5:cores/magic 里。辐射仓：石头 + 元素魔晶 */
    public static final RegistryObject<Item> PALE_CORE = item("pale_core", "palecore");

    // ---------------- 护板（BlockDyeable） ----------------

    /** 原作 BlockDyeable(Material.ROCK, MapColor.PURPLE).setHardness(3).setResistance(15)（先硬度后抗爆）→ 抗爆 9；要镐；MRU 抗性 5 */
    public static final RegistryObject<DyeableBlock> MAGIC_PLATING = plating("magic_plating", MapColor.COLOR_PURPLE, 15F,
            "block/magicplatingblock");
    /** 原作 BlockDyeable(Material.ROCK, MapColor.LAPIS).setHardness(3).setResistance(100)（先硬度后抗爆）→ 抗爆 60；要镐；MRU 抗性 10 */
    public static final RegistryObject<DyeableBlock> PALE_PLATING = plating("pale_plating", MapColor.LAPIS, 100F,
            "block/paleplatingblock");

    // ---------------- 栅栏（BlockFenceEC → 原版 FenceBlock） ----------------

    /** 原作 fFence：BlockFenceEC(Material.ROCK).setHardness(3).setResistance(15) → 抗爆 9；贴图同魔力强化石 */
    public static final RegistryObject<FenceBlock> FORTIFIED_STONE_FENCE = fence("fortified_stone_fence", MapColor.STONE,
            "block/fortifiedstone");
    /** 原作 magicFence：BlockFenceEC(Material.ROCK, MapColor.PURPLE).setHardness(3).setResistance(15) → 抗爆 9；贴图同魔力护板 */
    public static final RegistryObject<FenceBlock> MAGIC_FENCE = fence("magic_fence", MapColor.COLOR_PURPLE,
            "block/magicplatingblock");

    // ---------------- 水晶灯（BlockCrystalLamp，crystalLamp:0~5） ----------------
    // 原作 Material.ROCK、setLightLevel(1F)（亮度 15）、setHardness(0.4).setResistance(1)（先硬度后抗爆 → 抗爆 0.6）、不透光；要镐

    /** 水晶灯的亮度（原作 setLightLevel(1F) = 15） */
    public static final int LAMP_LIGHT = 15;

    public static final RegistryObject<Block> FIRE_CRYSTAL_LAMP = lamp("fire_crystal_lamp", "fire");
    public static final RegistryObject<Block> WATER_CRYSTAL_LAMP = lamp("water_crystal_lamp", "water");
    public static final RegistryObject<Block> EARTH_CRYSTAL_LAMP = lamp("earth_crystal_lamp", "earth");
    public static final RegistryObject<Block> AIR_CRYSTAL_LAMP = lamp("air_crystal_lamp", "air");
    public static final RegistryObject<Block> ELEMENTAL_CRYSTAL_LAMP = lamp("elemental_crystal_lamp", "elemental");
    public static final RegistryObject<Block> MITHRILINE_CRYSTAL_LAMP = lamp("mithriline_crystal_lamp", "mithriline");

    private ECAlloys() {
    }

    /** 静态字段在类加载时已登记进 DeferredRegister；本模组没有机器配置要登记 */
    public static void init() {
    }

    // ---------------- 注册工具 ----------------

    private static RegistryObject<Item> item(String name, String texture) {
        return ECItems.register(name, () -> new Item(new Item.Properties()), ItemSpec.generated("item/misc/" + texture));
    }

    /**
     * 可染色的护板：和魔力强化石同一个方块类（颜色是方块状态，染料右键改色、潜行右键染 5×5×5，不消耗染料），
     * 挖下来总是白色。方块颜色、物品颜色由 MaterialsClient 按 DyeableBlock 自动注册。
     * 原作模型用 CTM 贴图 X_ct（1.20.1 没有 CTM，_ct 只剩一张普通贴图），这里和魔力强化石一样用不带 _ct 的那张。
     */
    private static RegistryObject<DyeableBlock> plating(String name, MapColor color, float legacyResistance, String texture) {
        return ECBlocks.register(name, () -> new DyeableBlock(BlockBehaviour.Properties.of()
                        .mapColor(color)
                        .instrument(NoteBlockInstrument.BASEDRUM)
                        .strength(3F, ECBlocks.legacyResistance(3F, legacyResistance, false))
                        .requiresCorrectToolForDrops(), false),
                BlockSpec.of(dyeableModel(texture)));
    }

    /**
     * 栅栏：原版 FenceBlock（同下界砖栅栏：石质、要镐），进 minecraft:fences（方块和物品），能和别的非木质栅栏相连；
     * <b>不进</b> forge:fences（那是给「任意栅栏」配方用的共享标签，按计划不加）。
     */
    private static RegistryObject<FenceBlock> fence(String name, MapColor color, String texture) {
        return ECBlocks.register(name, () -> new FenceBlock(BlockBehaviour.Properties.of()
                        .mapColor(color)
                        .instrument(NoteBlockInstrument.BASEDRUM)
                        .strength(3F, ECBlocks.legacyResistance(3F, 15F, false))
                        .requiresCorrectToolForDrops()),
                BlockSpec.of((provider, block) -> {
                            ResourceLocation tex = provider.tex(texture);
                            provider.fenceBlock((FenceBlock) block, tex);
                            provider.itemModels().fenceInventory(provider.name(block), tex);
                        })
                        .addBlockTags(BlockTags.FENCES)
                        .itemTags(ItemTags.FENCES));
    }

    /** 水晶灯：六面同贴图的普通方块，原作一个方块按 meta 分 6 种，这里拆成 6 个注册名 */
    private static RegistryObject<Block> lamp(String name, String texture) {
        return ECBlocks.register(name, () -> new Block(BlockBehaviour.Properties.of()
                        .mapColor(MapColor.STONE)
                        .instrument(NoteBlockInstrument.BASEDRUM)
                        .strength(0.4F, ECBlocks.legacyResistance(0.4F, 1F, false))
                        .lightLevel(state -> LAMP_LIGHT)
                        .requiresCorrectToolForDrops()),
                BlockSpec.of(BlockSpec.cubeAll("block/lamp/" + texture)));
    }

    /** 可染色方块的模型：原作 cube_colored（六面带 tintindex 0）+ 一张贴图（同 ECMaterials 的写法） */
    private static BlockSpec.ModelGen dyeableModel(String texture) {
        return (provider, block) -> {
            ResourceLocation tex = provider.tex(texture);
            BlockModelBuilder model = provider.models().withExistingParent(provider.name(block), provider.modLoc("block/cube_colored"))
                    .texture("particle", tex);
            for (Direction direction : Direction.values()) {
                model.texture(direction.getSerializedName(), tex);
            }
            provider.simpleBlockWithItem(block, model);
        };
    }
}
