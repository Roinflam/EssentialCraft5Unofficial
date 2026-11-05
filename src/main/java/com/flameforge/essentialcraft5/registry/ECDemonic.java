package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.block.DyeableBlock;
import com.flameforge.essentialcraft5.item.CapturedSoulItem;
import com.flameforge.essentialcraft5.item.SoulScriberItem;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/**
 * 恶魔材料（第七批，batch7 §3.1 第 5 条、§4.3）：阿克罗尼特锭、恶魔板、恶魔核心、被俘灵魂、恶魔护板、灵魂刻刀。
 * <p>
 * 框架 7-F 先注册前四个名字：恶魔交易（M1）的报酬是阿克罗尼特锭、交易池要被俘灵魂，灵魂刻刀与恶魔柱也要它们，
 * 先注册好几个模块就不用互相等，配方也不用加 {@code forge:item_exists} 条件。恶魔护板、灵魂刻刀和全部配方、标签随第七批 M3 加在这里。
 * <p>
 * 新旧注册名（总表见 tools/id_map_*.json）：
 * <ul>
 *   <li>genitem 52 / 54 / 53 → ackronite_ingot / demonic_plate / demonic_core（纯材料，原作 ItemGenericEC 的子类型）</li>
 *   <li>soul（ItemCapturedSoul）→ captured_soul（NBT {@code entity} 记实体，见 {@link CapturedSoulItem}）</li>
 *   <li>demonicPlating（BlockDyeable）→ demonic_plating；soulScriber（ItemSoulScriber）→ soul_scriber（{@link SoulScriberItem}）</li>
 * </ul>
 * 配方（data/essentialcraft5/recipes/demonic/）：恶魔板 = 魔术师台 魔力强化板 + 阿克罗尼特锭 ×4 → 4 个、2 000 MRU（原作 RecipesCore L89）；
 * 恶魔核心 = ISI / SES / ISI（恶魔板、元素晶石、MRU 共鸣水晶）；恶魔护板 = 恶魔板 ×4 → 16 块；灵魂刻刀 = 下界岩 / 石英 / 木棍竖排。
 * 原作 1.12 版这条线做不出来（恶魔五芒星的配方写错了矿辞，batch7 §0 第 1 条），这些数值 1.12 版的玩家没玩到过，照原作的数写。
 * <p>
 * 标签（tools/tags/ecdemonic.json）：恶魔核心进 {@code essentialcraft5:cores/magic}、恶魔板进 {@code plates/magic}（原作同时登记 coreMagic / plateMagic，
 * 所以现有收这两个标签的配方都能拿它们代用，枪的核心格也认恶魔核心）；恶魔护板进富集仓外壳 {@code mrucuec_casing}、线圈底座 {@code mru_coil_base}
 * （原作 StructureRegistry L43、L57）；灵魂刻刀的 {@code minecraft:swords} 写在规格上；收魂的实体标签 {@code soul_capturable} 是静态 JSON。
 * 恶魔护板的 16 个花纹由 {@code ECDecorations} 按 {@code FancyMaterial.DEMONIC_PLATING} 注册（配方 tools/gen/fancy_recipes.py 生成）。
 * <p>
 * 阿克罗尼特只能从恶魔交易来；魔术师台升级（恶魔核心 16，{@code MagicianTableUpgrades}）、MRU 抗性（恶魔护板 36）、
 * demonic 枪身与 16 倍狙击镜（{@code GunMaterials}）、灵魂五芒星 3 档的恶魔护板格（{@code ECPentacle}）都已按注册名预写，注册了就生效。
 * 文字：名字由 datagen 按命名表取；被俘灵魂的带实体显示名和提示在 tools/lang/keys/framework7.json，灵魂刻刀的提示、
 * 灵魂五芒星 3 档的进度在 tools/lang/keys/ecdemonic.json。
 */
public final class ECDemonic {

    /** genitem 52：恶魔交易的报酬（原作 3 + nextInt(6) 个）；原作矿辞 ingotDemonic */
    public static final RegistryObject<Item> ACKRONITE_INGOT = ECItems.register("ackronite_ingot",
            () -> new Item(new Item.Properties()), ItemSpec.generated("item/misc/ackroniteingot"));

    /** genitem 54：魔术师台 魔力强化板 + 阿克罗尼特锭 ×4 → 4 个、2 000 MRU（原作 RecipesCore L89）；在 essentialcraft5:plates/magic 里 */
    public static final RegistryObject<Item> DEMONIC_PLATE = ECItems.register("demonic_plate",
            () -> new Item(new Item.Properties()), ItemSpec.generated("item/misc/demonicplate"));

    /** genitem 53：有序 ISI / SES / ISI（恶魔板、元素晶石、MRU 共鸣水晶）；在 essentialcraft5:cores/magic 里 */
    public static final RegistryObject<Item> DEMONIC_CORE = ECItems.register("demonic_core",
            () -> new Item(new Item.Properties()), ItemSpec.generated("item/misc/demoniccore"));

    /** 原作 soul：贴图 item/creaturesoul（原作 blockstates/item/soul.json 的 layer0） */
    public static final RegistryObject<CapturedSoulItem> CAPTURED_SOUL = ECItems.register("captured_soul",
            () -> new CapturedSoulItem(new Item.Properties()), ItemSpec.generated("item/creaturesoul"));

    /**
     * 原作 BlockDyeable(Material.ROCK, MapColor.RED).setHardness(3).setResistance(100)（先硬度后抗爆）→ 抗爆 60；要镐。
     * 16 色染色（手持染料右键，同魔力护板），颜色由框架的 MaterialsClient 自动注册；挖下来总是白色。MRU 抗性 36（entity/MRUResistances 已预填）。
     */
    public static final RegistryObject<DyeableBlock> DEMONIC_PLATING = ECBlocks.register("demonic_plating",
            () -> new DyeableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .strength(3F, ECBlocks.legacyResistance(3F, 100F, false))
                    .requiresCorrectToolForDrops(), false),
            BlockSpec.of(ECMaterials.dyeableModel("block/demonicplatingblock", null)));

    /** 原作 soulScriber：贴图 item/tools/soulscriber（原作 setFull3D，手持模型） */
    public static final RegistryObject<SoulScriberItem> SOUL_SCRIBER = ECItems.register("soul_scriber",
            () -> new SoulScriberItem(new Item.Properties()), ItemSpec.handheld("item/tools/soulscriber").tags(ItemTags.SWORDS));

    private ECDemonic() {
    }

    /** 由 ECRegistries.register() 调用：类加载即把上面的条目登记进 DeferredRegister（本模块没有配置小节） */
    public static void init() {
    }
}
