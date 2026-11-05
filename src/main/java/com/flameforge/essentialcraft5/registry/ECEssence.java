package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.api.IElementalCrystal;
import com.flameforge.essentialcraft5.block.CrystalControllerBlock;
import com.flameforge.essentialcraft5.block.CrystalExtractorBlock;
import com.flameforge.essentialcraft5.block.MagicLightBlock;
import com.flameforge.essentialcraft5.blockentity.CrystalControllerBlockEntity;
import com.flameforge.essentialcraft5.blockentity.CrystalExtractorBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.datagen.ECBlockLoot;
import com.flameforge.essentialcraft5.item.EssenceItem;
import com.flameforge.essentialcraft5.item.MagicalLanternItem;
import com.flameforge.essentialcraft5.menu.CrystalControllerMenu;
import com.flameforge.essentialcraft5.menu.CrystalExtractorMenu;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/**
 * 精华与焦点模块（第二批，batch2 §3.4）：水晶控制器、水晶提取器、魔力光源（方块）；精华 ×16、元素燃料 ×4、焦点 ×4、魔力灯笼（物品）。
 * <p>
 * 新旧注册名：crystalController → crystal_controller；crystalExtractor → crystal_extractor；torch（BlockMagicLight）→ magic_light；
 * essence:0~15 → {common, uncommon, rare, exceptional}_{fire, water, earth, air}_essence（meta = 稀有度 × 4 + 元素）；
 * elementalFuel:0~3 → {fire, water, earth, air}_elemental_fuel；fFocus / wFocus / eFocus / aFocus → {fire, water, earth, air}_focus；
 * magicalLantern → magical_lantern。
 * <p>
 * 元素水晶（ECCrystal 模块）只通过 {@link IElementalCrystal} 访问，不 import 它的类。
 * 物品标签 {@code essentialcraft5:essences}、{@code focuses} 由本模块建（tools/tags/ecessence.json），代码常量是 {@link #ESSENCES}、{@link #FOCUSES}。
 * 工作台配方在 data/essentialcraft5/recipes/essence/（tools/gen/essence_recipes.py 生成），文字在 tools/lang/keys/ecessence.json，
 * 研究手册章节 assets/essentialcraft5/guide/essence.json。客户端部分（界面、光源粒子）见 client/EssenceClient。
 */
public final class ECEssence {

    /** 16 种精华（手册隐藏进度、以后的配方用） */
    public static final TagKey<Item> ESSENCES = ECTags.modItem("essences");
    /** 4 种焦点 */
    public static final TagKey<Item> FOCUSES = ECTags.modItem("focuses");

    // ---------------- 水晶控制器 ----------------

    /**
     * 原作 crystalController：registerBlockSimple(…, 1, 1, 0)（先抗爆后硬度，不透光度 0），Material.ROCK，OBJ 模型
     * → 硬度 1、抗爆 1.0，透天空光
     */
    public static final RegistryObject<CrystalControllerBlock> CRYSTAL_CONTROLLER = ECBlocks.register("crystal_controller",
            () -> new CrystalControllerBlock(ECBlocks.machine(MapColor.STONE, 1F, ECBlocks.legacyResistance(1F, 1F, true)).noOcclusion()),
            BlockSpec.of(BlockSpec.obj("crystalcontroller", "block/model/crystalcontroller")));

    public static final RegistryObject<BlockEntityType<CrystalControllerBlockEntity>> CRYSTAL_CONTROLLER_BE =
            ECBlockEntities.type("crystal_controller", CrystalControllerBlockEntity::new, CRYSTAL_CONTROLLER);

    public static final RegistryObject<MenuType<CrystalControllerMenu>> CRYSTAL_CONTROLLER_MENU =
            ECMenus.register("crystal_controller", CrystalControllerMenu::new);

    // ---------------- 水晶提取器 ----------------

    /** 原作 crystalExtractor：同水晶控制器 */
    public static final RegistryObject<CrystalExtractorBlock> CRYSTAL_EXTRACTOR = ECBlocks.register("crystal_extractor",
            () -> new CrystalExtractorBlock(ECBlocks.machine(MapColor.STONE, 1F, ECBlocks.legacyResistance(1F, 1F, true)).noOcclusion()),
            BlockSpec.of(BlockSpec.obj("crystalextractor", "block/model/crystalextractor")));

    public static final RegistryObject<BlockEntityType<CrystalExtractorBlockEntity>> CRYSTAL_EXTRACTOR_BE =
            ECBlockEntities.type("crystal_extractor", CrystalExtractorBlockEntity::new, CRYSTAL_EXTRACTOR);

    public static final RegistryObject<MenuType<CrystalExtractorMenu>> CRYSTAL_EXTRACTOR_MENU =
            ECMenus.register("crystal_extractor", CrystalExtractorMenu::new);

    // ---------------- 魔力光源 ----------------

    /**
     * 原作 torch（BlockMagicLight）：registerBlockSimple(…, 0, 0, 1)，Material.CIRCUITS，亮度 15，不掉落。
     * 没有物品（原作的方块物品拿不到）；模型是只有破坏粒子的空模型（原作同样），渲染形状 INVISIBLE；不需要工具。
     */
    public static final RegistryObject<MagicLightBlock> MAGIC_LIGHT = ECBlocks.register("magic_light",
            () -> new MagicLightBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NONE)
                    .noCollission()
                    .noOcclusion()
                    .instabreak()
                    .lightLevel(state -> 15)
                    .pushReaction(PushReaction.DESTROY)),
            BlockSpec.of((provider, block) -> provider.simpleBlock(block,
                            provider.models().getBuilder("magic_light").texture("particle", provider.modLoc("block/firstworld/root"))))
                    .loot(ECBlockLoot::none)
                    .blockTags()
                    .item(null));

    // ---------------- 精华（原作 essence:0~15，meta = 稀有度 × 4 + 元素） ----------------

    public static final RegistryObject<EssenceItem> COMMON_FIRE_ESSENCE = registerEssence(0, IElementalCrystal.Element.FIRE);
    public static final RegistryObject<EssenceItem> COMMON_WATER_ESSENCE = registerEssence(0, IElementalCrystal.Element.WATER);
    public static final RegistryObject<EssenceItem> COMMON_EARTH_ESSENCE = registerEssence(0, IElementalCrystal.Element.EARTH);
    public static final RegistryObject<EssenceItem> COMMON_AIR_ESSENCE = registerEssence(0, IElementalCrystal.Element.AIR);
    public static final RegistryObject<EssenceItem> UNCOMMON_FIRE_ESSENCE = registerEssence(1, IElementalCrystal.Element.FIRE);
    public static final RegistryObject<EssenceItem> UNCOMMON_WATER_ESSENCE = registerEssence(1, IElementalCrystal.Element.WATER);
    public static final RegistryObject<EssenceItem> UNCOMMON_EARTH_ESSENCE = registerEssence(1, IElementalCrystal.Element.EARTH);
    public static final RegistryObject<EssenceItem> UNCOMMON_AIR_ESSENCE = registerEssence(1, IElementalCrystal.Element.AIR);
    public static final RegistryObject<EssenceItem> RARE_FIRE_ESSENCE = registerEssence(2, IElementalCrystal.Element.FIRE);
    public static final RegistryObject<EssenceItem> RARE_WATER_ESSENCE = registerEssence(2, IElementalCrystal.Element.WATER);
    public static final RegistryObject<EssenceItem> RARE_EARTH_ESSENCE = registerEssence(2, IElementalCrystal.Element.EARTH);
    public static final RegistryObject<EssenceItem> RARE_AIR_ESSENCE = registerEssence(2, IElementalCrystal.Element.AIR);
    public static final RegistryObject<EssenceItem> EXCEPTIONAL_FIRE_ESSENCE = registerEssence(3, IElementalCrystal.Element.FIRE);
    public static final RegistryObject<EssenceItem> EXCEPTIONAL_WATER_ESSENCE = registerEssence(3, IElementalCrystal.Element.WATER);
    public static final RegistryObject<EssenceItem> EXCEPTIONAL_EARTH_ESSENCE = registerEssence(3, IElementalCrystal.Element.EARTH);
    public static final RegistryObject<EssenceItem> EXCEPTIONAL_AIR_ESSENCE = registerEssence(3, IElementalCrystal.Element.AIR);

    /** 按原作 meta 排：下标 = 稀有度 × 4 + 元素 */
    private static final List<RegistryObject<EssenceItem>> ESSENCE_TABLE = List.of(
            COMMON_FIRE_ESSENCE, COMMON_WATER_ESSENCE, COMMON_EARTH_ESSENCE, COMMON_AIR_ESSENCE,
            UNCOMMON_FIRE_ESSENCE, UNCOMMON_WATER_ESSENCE, UNCOMMON_EARTH_ESSENCE, UNCOMMON_AIR_ESSENCE,
            RARE_FIRE_ESSENCE, RARE_WATER_ESSENCE, RARE_EARTH_ESSENCE, RARE_AIR_ESSENCE,
            EXCEPTIONAL_FIRE_ESSENCE, EXCEPTIONAL_WATER_ESSENCE, EXCEPTIONAL_EARTH_ESSENCE, EXCEPTIONAL_AIR_ESSENCE);

    // ---------------- 元素燃料（原作 elementalFuel:0~3，最大堆叠 16，Rarity.RARE，贴图 efuel_fiery / watery / earthen / windy） ----------------

    public static final RegistryObject<Item> FIRE_ELEMENTAL_FUEL = fuel("fire", "fiery");
    public static final RegistryObject<Item> WATER_ELEMENTAL_FUEL = fuel("water", "watery");
    public static final RegistryObject<Item> EARTH_ELEMENTAL_FUEL = fuel("earth", "earthen");
    public static final RegistryObject<Item> AIR_ELEMENTAL_FUEL = fuel("air", "windy");

    // ---------------- 焦点（原作 ItemElementalFocus：耐久 3000、不能堆叠；二批只有魔力灯笼用，护符、法杖在第三批） ----------------

    public static final RegistryObject<Item> FIRE_FOCUS = focus("fire");
    public static final RegistryObject<Item> WATER_FOCUS = focus("water");
    public static final RegistryObject<Item> EARTH_FOCUS = focus("earth");
    public static final RegistryObject<Item> AIR_FOCUS = focus("air");

    // ---------------- 魔力灯笼 ----------------

    /** 原作 magicalLantern：registerItemSimple(…, 0, true, 1)（手持姿势、不能堆叠），贴图 tools/elementalpurifier */
    public static final RegistryObject<MagicalLanternItem> MAGICAL_LANTERN = ECItems.register("magical_lantern",
            () -> new MagicalLanternItem(new Item.Properties().stacksTo(1)),
            ItemSpec.handheld("item/tools/elementalpurifier"));

    private ECEssence() {
    }

    /** 由 ECRegistries.register() 调用：类加载即把上面的条目登记进 DeferredRegister，这里再登记配置小节 */
    public static void init() {
        ECConfig.machine(CrystalControllerBlockEntity.Config::define);
        ECConfig.machine(CrystalExtractorBlockEntity.Config::define);
    }

    /** 某个稀有度（0 普通 …… 3 卓越）、某个元素的精华 */
    public static EssenceItem essence(int rarity, IElementalCrystal.Element element) {
        return ESSENCE_TABLE.get(rarity * 4 + element.ordinal()).get();
    }

    /** 16 种精华，按原作 meta 排 */
    public static List<RegistryObject<EssenceItem>> essences() {
        return ESSENCE_TABLE;
    }

    private static RegistryObject<EssenceItem> registerEssence(int rarity, IElementalCrystal.Element element) {
        String name = element.getSerializedName();
        return ECItems.register(EssenceItem.RARITY_NAMES.get(rarity) + "_" + name + "_essence",
                () -> new EssenceItem(new Item.Properties(), element, rarity),
                ItemSpec.generated("item/elemental/essence" + name));
    }

    private static RegistryObject<Item> fuel(String element, String texture) {
        return ECItems.register(element + "_elemental_fuel",
                () -> new Item(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)),
                ItemSpec.generated("item/elemental/efuel_" + texture));
    }

    private static RegistryObject<Item> focus(String element) {
        return ECItems.register(element + "_focus",
                () -> new Item(new Item.Properties().durability(3000)),
                ItemSpec.generated("item/elemental/efocus" + element));
    }
}
