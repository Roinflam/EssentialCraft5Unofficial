package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.block.MagmaticFurnaceBlock;
import com.flameforge.essentialcraft5.block.MagmaticSmelteryBlock;
import com.flameforge.essentialcraft5.blockentity.MagmaticFurnaceBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MagmaticSmelteryBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.MagmaticAlloyItem;
import com.flameforge.essentialcraft5.menu.MagmaticFurnaceMenu;
import com.flameforge.essentialcraft5.menu.MagmaticSmelteryMenu;
import com.flameforge.essentialcraft5.recipe.OreSmeltingRecipe;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.registries.RegistryObject;

/**
 * 矿石加工模块（第二批）：岩浆冶炼炉、岩浆合金、强化 / 魔力 / 淡青 / 虚空岩浆熔炉，配方类型 essentialcraft5:ore_smelting。
 * <p>
 * 新旧注册名：magmaticSmeltery → magmatic_smeltery；magicalAlloy → magmatic_alloy（原名和显示名不符）；
 * furnaceMagic 的 meta 0 / 4 / 8 / 12 → fortified_ / magic_ / pale_ / void_magmatic_furnace（虚空级第六批 6b 加，要海纳尔遗迹里的虚空板）。
 * <p>
 * 矿表是 data/essentialcraft5/recipes/magmatic/ore_*.json（原作 OreSmeltingRecipe 里写死的表，只留原版和本模组的矿，D8），
 * 机器配方也在那个目录。文字在 tools/lang/keys/ecmagmatic.json，研究手册章节 assets/essentialcraft5/guide/magmatic.json。
 * 客户端部分（界面、岩浆合金染色、手册配方页画法）见 client/MagmaticClient。
 */
public final class ECMagmatic {

    // ---------------- 配方类型 ----------------

    public static final RegistryObject<RecipeType<OreSmeltingRecipe>> ORE_SMELTING = ECRecipes.type("ore_smelting");
    public static final RegistryObject<RecipeSerializer<OreSmeltingRecipe>> ORE_SMELTING_SERIALIZER =
            ECRecipes.serializer("ore_smelting", OreSmeltingRecipe.Serializer::new);

    // ---------------- 岩浆合金 ----------------

    /** 原作 magicalAlloy：registerItemSimple(…, 0, false, 64)；贴图两层，第二层按矿石颜色染色 */
    public static final RegistryObject<MagmaticAlloyItem> MAGMATIC_ALLOY = ECItems.register("magmatic_alloy",
            () -> new MagmaticAlloyItem(new Item.Properties()),
            ItemSpec.of((provider, item) -> provider.generated(item, "item/modular/alloy_layer_0")
                    .texture("layer1", provider.tex("item/modular/alloy_layer_1"))));

    // ---------------- 岩浆冶炼炉 ----------------

    /**
     * 原作 magmaticSmeltery：registerBlockSimple(…, 1, 1, 0)（先抗爆后硬度，不透光度 0 → 透天空光），Material.ROCK + MapColor.PURPLE，
     * OBJ 模型 → 硬度 1、抗爆 1.0
     */
    public static final RegistryObject<MagmaticSmelteryBlock> MAGMATIC_SMELTERY = ECBlocks.register("magmatic_smeltery",
            () -> new MagmaticSmelteryBlock(ECBlocks.machine(MapColor.COLOR_PURPLE, 1F, ECBlocks.legacyResistance(1F, 1F, true))
                    .noOcclusion()),
            BlockSpec.of(BlockSpec.obj("magmaticsmeltery", "block/model/magmaticsmeltery")));

    public static final RegistryObject<BlockEntityType<MagmaticSmelteryBlockEntity>> MAGMATIC_SMELTERY_BE =
            ECBlockEntities.type("magmatic_smeltery", MagmaticSmelteryBlockEntity::new, MAGMATIC_SMELTERY);

    public static final RegistryObject<MenuType<MagmaticSmelteryMenu>> MAGMATIC_SMELTERY_MENU =
            ECMenus.register("magmatic_smeltery", MagmaticSmelteryMenu::new);

    // ---------------- 岩浆熔炉（四档） ----------------

    /**
     * 原作 furnaceMagic：setHardness(1).setResistance(1).setLightOpacity(15)（先硬度后抗爆 → 抗爆 0.6，实心不透光），
     * Material.ROCK，地图颜色按档位（STONE / PURPLE / LAPIS / BLACK）；外观是原版 orientable 模型（原作 blockstates/furnacemagic.json）
     */
    public static final RegistryObject<MagmaticFurnaceBlock> FORTIFIED_MAGMATIC_FURNACE = furnace("fortified_magmatic_furnace",
            MagmaticFurnaceBlock.Tier.FORTIFIED);
    public static final RegistryObject<MagmaticFurnaceBlock> MAGIC_MAGMATIC_FURNACE = furnace("magic_magmatic_furnace",
            MagmaticFurnaceBlock.Tier.MAGIC);
    public static final RegistryObject<MagmaticFurnaceBlock> PALE_MAGMATIC_FURNACE = furnace("pale_magmatic_furnace",
            MagmaticFurnaceBlock.Tier.PALE);
    /**
     * 虚空级（第六批 6b）：配方 recipes/magmatic/void_magmatic_furnace.json 早就写好（8 块虚空板围淡青级，带两条 forge:item_exists），注册以后自动成立。
     * 这个字段必须写在下面的方块实体类型之前：可变参数在类初始化时就取字段的值，写在后面拿到的是 null。
     */
    public static final RegistryObject<MagmaticFurnaceBlock> VOID_MAGMATIC_FURNACE = furnace("void_magmatic_furnace",
            MagmaticFurnaceBlock.Tier.VOID);

    public static final RegistryObject<BlockEntityType<MagmaticFurnaceBlockEntity>> MAGMATIC_FURNACE_BE =
            ECBlockEntities.type("magmatic_furnace", MagmaticFurnaceBlockEntity::new,
                    FORTIFIED_MAGMATIC_FURNACE, MAGIC_MAGMATIC_FURNACE, PALE_MAGMATIC_FURNACE, VOID_MAGMATIC_FURNACE);

    public static final RegistryObject<MenuType<MagmaticFurnaceMenu>> MAGMATIC_FURNACE_MENU =
            ECMenus.register("magmatic_furnace", MagmaticFurnaceMenu::new);

    private ECMagmatic() {
    }

    private static RegistryObject<MagmaticFurnaceBlock> furnace(String name, MagmaticFurnaceBlock.Tier tier) {
        return ECBlocks.register(name,
                () -> new MagmaticFurnaceBlock(ECBlocks.machine(tier.mapColor(), 1F, ECBlocks.legacyResistance(1F, 1F, false)), tier),
                BlockSpec.of((provider, block) -> {
                    // 原版 orientable：炉口朝北，horizontalBlock 按 facing 转（北 0°、东 90°、南 180°、西 270°）
                    ModelFile model = provider.models().orientable(provider.name(block),
                            provider.tex(tier.sideTexture()), provider.tex(tier.frontTexture()), provider.tex(tier.sideTexture()));
                    provider.horizontalBlock(block, model);
                    provider.simpleBlockItem(block, model);
                }));
    }

    /** 由 ECRegistries.register() 调用：类加载即把上面的条目登记进 DeferredRegister，这里再登记配置小节 */
    public static void init() {
        ECConfig.machine(MagmaticSmelteryBlockEntity.Config::define);
        ECConfig.machine(MagmaticFurnaceBlockEntity.Config::define);
    }
}
