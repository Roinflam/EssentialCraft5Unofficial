package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.block.ColdStoneBlock;
import com.flameforge.essentialcraft5.block.DropsClusterBlock;
import com.flameforge.essentialcraft5.block.DyeableBlock;
import com.flameforge.essentialcraft5.item.GlitchyCoreItem;
import com.flameforge.essentialcraft5.item.MagicalWaterItem;
import com.flameforge.essentialcraft5.item.MatrixProjectionItem;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.client.model.generators.BlockModelBuilder;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

/**
 * 材料模块：元素魔晶、通用合成材料、矩阵投影、魔力炉渣，以及魔晶块、魔晶簇、魔力强化石/玻璃、霜冻石。
 * <p>
 * 新旧注册名（总表见 tools/id_map_*.json）：
 * <ul>
 *   <li>gem_elemental:0~4（ItemDrop）→ fire_drops / water_drops / earth_drops / air_drops / elemental_gem</li>
 *   <li>genItem:N（ItemGenericEC）→ 各字段注释里写了 meta；只放第一批用得到的，其余跟着各自的批次；
 *       genItem:45 / 76 → lapis_core / glitchy_core（只放创造栏，第六批 6b）</li>
 *   <li>mruMatrixProjection:0~4 → empty / chaos / frozen / magic / shade_matrix_projection</li>
 *   <li>magicalSlag → magical_slag</li>
 *   <li>compressed:0~5（BlockCompressedDrops）→ fire / water / earth / air / elemental_drops_block、mithriline_dust_block</li>
 *   <li>Drops:0~3（BlockDrops，仪式产物）→ fire / water / earth / air_drops_cluster</li>
 *   <li>fortifiedStone / fortifiedGlass（BlockDyeable）/ coldStone → fortified_stone / fortified_glass / cold_stone</li>
 * </ul>
 * 标签在 tools/tags/materials.json，工作台配方在 data/essentialcraft5/recipes/materials/。
 * 机器做的材料（魔力锭、魔力水、强化板、MRU 碎片链、魔力板、强化石/玻璃、投影）的配方归辐射仓、魔力工作台模块。
 */
public final class ECMaterials {

    // ---------------- 元素魔晶（ItemDrop） ----------------

    public static final RegistryObject<Item> FIRE_DROPS = item("fire_drops", "item/elemental/drop_fire");
    public static final RegistryObject<Item> WATER_DROPS = item("water_drops", "item/elemental/drop_water");
    public static final RegistryObject<Item> EARTH_DROPS = item("earth_drops", "item/elemental/drop_earth");
    public static final RegistryObject<Item> AIR_DROPS = item("air_drops", "item/elemental/drop_air");
    public static final RegistryObject<Item> ELEMENTAL_GEM = item("elemental_gem", "item/elemental/drop_elemental");

    // ---------------- 通用材料（ItemGenericEC，genItem:N） ----------------

    /** genItem:1；魔力工作台升级（×1.2） */
    public static final RegistryObject<Item> ELEMENTAL_CORE = item("elemental_core", "item/misc/elementalcore");
    /** genItem:5；原作矿辞 ingotThaumium，不照搬 */
    public static final RegistryObject<Item> MAGICAL_INGOT = item("magical_ingot", "item/misc/magicalingot");
    /** genItem:6；可以喝 */
    public static final RegistryObject<MagicalWaterItem> MAGICAL_WATER = ECItems.register("magical_water",
            () -> new MagicalWaterItem(new Item.Properties()), ItemSpec.generated("item/misc/magicalwater"));
    /** genItem:7 */
    public static final RegistryObject<Item> MAGIC_FORTIFIED_PLATING = item("magic_fortified_plating", "item/misc/magicfortifiedplating");
    /** genItem:8（原拼写 magicPurifyedEnderScaleAlloy）；魔力工作台升级（×2.5），第二批末影产能器的材料 */
    public static final RegistryObject<Item> MAGIC_PURIFIED_ENDER_SCALE_ALLOY = item("magic_purified_ender_scale_alloy",
            "item/misc/magicpurifyedenderscalealloy");
    /** genItem:12~16：MRU 碎片 → 水晶 → 宝石 → 晶块 → 大晶块（魔力工作台逐级做） */
    public static final RegistryObject<Item> MRU_SHARD = item("mru_shard", "item/misc/mrushard");
    public static final RegistryObject<Item> MRU_CRYSTAL = item("mru_crystal", "item/misc/mrucrystal");
    public static final RegistryObject<Item> MRU_GEM = item("mru_gem", "item/misc/mrugem");
    public static final RegistryObject<Item> MRU_CHUNK = item("mru_chunk", "item/misc/mruchunk");
    public static final RegistryObject<Item> MRU_LARGE_CHUNK = item("mru_large_chunk", "item/misc/mrulargechunk");
    /** genItem:21 */
    public static final RegistryObject<Item> DIAMOND_PLATE = item("diamond_plate", "item/misc/diamondplate");
    /** genItem:22 */
    public static final RegistryObject<Item> EMERALD_PLATE = item("emerald_plate", "item/misc/emeraldplate");
    /** genItem:23（原拼写 eyeOfAbsorbtion）；魔力工作台升级（×3） */
    public static final RegistryObject<Item> EYE_OF_ABSORPTION = item("eye_of_absorption", "item/misc/eyeofabsorbtion");
    /** genItem:24 */
    public static final RegistryObject<Item> FORTIFIED_FRAME = item("fortified_frame", "item/misc/fortifiedframe");
    /** genItem:25 */
    public static final RegistryObject<Item> HEATING_ROD = item("heating_rod", "item/misc/heatingrod");
    /** genItem:26（显示名 Iron Frame） */
    public static final RegistryObject<Item> IRON_SUPPORT = item("iron_support", "item/misc/ironsupport");
    /** genItem:27 */
    public static final RegistryObject<Item> MAGICAL_SCREEN = item("magical_screen", "item/misc/magicalscreen");
    /** genItem:28 */
    public static final RegistryObject<Item> MATRIX_LINK = item("matrix_link", "item/misc/matrixlink");
    /** genItem:29 */
    public static final RegistryObject<Item> MRU_CATCHER = item("mru_catcher", "item/misc/mrucatcher");
    /** genItem:30 */
    public static final RegistryObject<Item> MRU_CONVERSION_MATRIX = item("mru_conversion_matrix", "item/misc/mruconversionmatrix");
    /** genItem:31 */
    public static final RegistryObject<Item> OBSIDIAN_PLATE = item("obsidian_plate", "item/misc/obsidianplate");
    /** genItem:33 */
    public static final RegistryObject<Item> WORLD_INTERACTOR = item("world_interactor", "item/misc/worldinteractor");
    /** genItem:34；原作矿辞 plateThaumium，不照搬；在 essentialcraft5:plates/magic 标签里 */
    public static final RegistryObject<Item> MAGIC_PLATE = item("magic_plate", "item/misc/magicplate");
    /** genItem:51；米斯莱矿的掉落 */
    public static final RegistryObject<Item> MITHRILINE_DUST = item("mithriline_dust", "item/misc/mithrilinedust");

    // ---------------- 原作生存里拿不到的（policy §4.7；第六批 6b 加） ----------------
    // 青金石核心只放创造栏：不给配方、不进战利品和交易，也没有任何配方用它；故障核心第十批起由全息投影掉落（见下）

    /** genItem:45；原作没有配方也没有用途，只登记了矿辞 coreLapis（不照搬） */
    public static final RegistryObject<Item> LAPIS_CORE = item("lapis_core", "item/misc/lapiscore");
    /**
     * genItem:76；原作只当研究书「全息投影」条目的图标，没有来源也没有用途；贴图是动画（glitchycore.png.mcmeta）。
     * 第十批 10c 起不再只放创造栏：全息投影必掉、带目标平衡值，辐射仓解码出审判庭坐标（{@link GlitchyCoreItem}，registry/ECInquisitorium）
     */
    public static final RegistryObject<Item> GLITCHY_CORE = ECItems.register("glitchy_core", () -> new GlitchyCoreItem(new Item.Properties()),
            ItemSpec.generated("item/misc/glitchycore"));

    // ---------------- MRU 矩阵投影（mruMatrixProjection:0~4，原作一组 1 个） ----------------

    public static final RegistryObject<MatrixProjectionItem> EMPTY_MATRIX_PROJECTION = projection("empty_matrix_projection", 0, "mrumatrix_empty");
    public static final RegistryObject<MatrixProjectionItem> CHAOS_MATRIX_PROJECTION = projection("chaos_matrix_projection", 1, "mrumatrix_chaos");
    public static final RegistryObject<MatrixProjectionItem> FROZEN_MATRIX_PROJECTION = projection("frozen_matrix_projection", 2, "mrumatrix_frozen");
    public static final RegistryObject<MatrixProjectionItem> MAGIC_MATRIX_PROJECTION = projection("magic_matrix_projection", 3, "mrumatrix_magic");
    /** 原作配方里没有产出途径（只在研究书里出现），留着给后期的暗影枪械透镜 */
    public static final RegistryObject<MatrixProjectionItem> SHADE_MATRIX_PROJECTION = projection("shade_matrix_projection", 4, "mrumatrix_shade");

    /** magicalSlag：热力产能器每烧一个燃料出 1 个 */
    public static final RegistryObject<Item> MAGICAL_SLAG = item("magical_slag", "item/misc/magicalslag");

    // ---------------- 魔晶块（BlockCompressedDrops） ----------------
    // 原作：Material.ROCK（要镐）、SoundType.METAL、setHardness(0.4).setResistance(1)（先硬度后抗爆）、地图颜色随类型

    public static final RegistryObject<Block> FIRE_DROPS_BLOCK = compressed("fire_drops_block", MapColor.COLOR_ORANGE, "drops_flame");
    public static final RegistryObject<Block> WATER_DROPS_BLOCK = compressed("water_drops_block", MapColor.WATER, "drops_water");
    public static final RegistryObject<Block> EARTH_DROPS_BLOCK = compressed("earth_drops_block", MapColor.DIRT, "drops_earth");
    public static final RegistryObject<Block> AIR_DROPS_BLOCK = compressed("air_drops_block", MapColor.WOOL, "drops_air");
    public static final RegistryObject<Block> ELEMENTAL_DROPS_BLOCK = compressed("elemental_drops_block", MapColor.COLOR_PURPLE, "drops_elemental");
    public static final RegistryObject<Block> MITHRILINE_DUST_BLOCK = compressed("mithriline_dust_block", MapColor.COLOR_GREEN, "mithriline");

    // ---------------- 魔晶簇（BlockDrops，仪式产物） ----------------
    // 模型借原版贴图（岩浆 / 水 / 灰化土顶 / 石英块顶）。1.13 起水的贴图是灰的、靠生物群系水色染，所以水之魔晶簇用带 tintindex 的模型

    public static final RegistryObject<DropsClusterBlock> FIRE_DROPS_CLUSTER = cluster("fire_drops_cluster", FIRE_DROPS, "minecraft:block/lava_still", false);
    public static final RegistryObject<DropsClusterBlock> WATER_DROPS_CLUSTER = cluster("water_drops_cluster", WATER_DROPS, "minecraft:block/water_still", true);
    public static final RegistryObject<DropsClusterBlock> EARTH_DROPS_CLUSTER = cluster("earth_drops_cluster", EARTH_DROPS, "minecraft:block/podzol_top", false);
    public static final RegistryObject<DropsClusterBlock> AIR_DROPS_CLUSTER = cluster("air_drops_cluster", AIR_DROPS, "minecraft:block/quartz_block_top", false);

    // ---------------- 魔力强化石 / 玻璃（BlockDyeable）、霜冻石 ----------------

    /** 原作 BlockDyeable(Material.ROCK).setHardness(3).setResistance(15) → 抗爆 9；要镐 */
    public static final RegistryObject<DyeableBlock> FORTIFIED_STONE = ECBlocks.register("fortified_stone",
            () -> new DyeableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .strength(3F, ECBlocks.legacyResistance(3F, 15F, false))
                    .requiresCorrectToolForDrops(), false),
            BlockSpec.of(dyeableModel("block/fortifiedstone", false)));

    /**
     * 原作 BlockDyeable(Material.GLASS).setHardness(3).setResistance(15).setLightOpacity(0) → 抗爆 9。
     * Material.GLASS 不要求工具（空手也掉自己，原作没有「玻璃挖了没掉落」那一套），也没有哪种工具挖得更快，所以不进 mineable 标签。
     */
    public static final RegistryObject<DyeableBlock> FORTIFIED_GLASS = ECBlocks.register("fortified_glass",
            () -> new DyeableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NONE)
                    .instrument(NoteBlockInstrument.HAT)
                    .strength(3F, ECBlocks.legacyResistance(3F, 15F, false))
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .isValidSpawn((state, level, pos, type) -> false)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false), true),
            BlockSpec.of(dyeableModel("block/transparentglass", true)).blockTags());

    /**
     * 原作 BlockColdStone(Material.ICE).setHardness(1).setResistance(1).setLightOpacity(0) → 抗爆 0.6。
     * Material.ICE 不要求工具、镐也不加速，所以不进 mineable 标签；不透光度 0 → noOcclusion + 天空光穿透（见 ColdStoneBlock）。
     */
    public static final RegistryObject<ColdStoneBlock> COLD_STONE = ECBlocks.register("cold_stone",
            () -> new ColdStoneBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.ICE)
                    .strength(1F, ECBlocks.legacyResistance(1F, 1F, false))
                    .noOcclusion()
                    // 原作 Material.ICE 半透明：不导红石、怪物不能在上面生成
                    .isValidSpawn((state, level, pos, type) -> false)
                    .isRedstoneConductor((state, level, pos) -> false)),
            BlockSpec.of(BlockSpec.cubeAll("block/coldstone")).blockTags());

    private ECMaterials() {
    }

    /** 静态字段在类加载时已登记进 DeferredRegister；本模块没有机器配置要登记 */
    public static void init() {
    }

    // ---------------- 注册工具 ----------------

    private static RegistryObject<Item> item(String name, String texture) {
        return ECItems.register(name, () -> new Item(new Item.Properties()), ItemSpec.generated(texture));
    }

    private static RegistryObject<MatrixProjectionItem> projection(String name, int matrixType, String texture) {
        return ECItems.register(name, () -> new MatrixProjectionItem(new Item.Properties().stacksTo(1), matrixType),
                ItemSpec.generated("item/matrix/" + texture));
    }

    private static RegistryObject<Block> compressed(String name, MapColor color, String texture) {
        return ECBlocks.register(name, () -> new Block(BlockBehaviour.Properties.of()
                        .mapColor(color)
                        .instrument(NoteBlockInstrument.BASEDRUM)
                        .sound(SoundType.METAL)
                        .strength(0.4F, ECBlocks.legacyResistance(0.4F, 1F, false))
                        .requiresCorrectToolForDrops()),
                BlockSpec.of(BlockSpec.cubeAll("block/compressed/" + texture)));
    }

    /**
     * 魔晶簇：瞬间破坏、没有碰撞、掉 1~6 颗对应魔晶（原作 quantityDropped = 1 + rand(6)，不吃时运，精准采集也一样）。
     * 原作 Material.CLOTH 不要求工具，所以不进 mineable 标签。
     */
    private static RegistryObject<DropsClusterBlock> cluster(String name, RegistryObject<Item> drop, String texture, boolean tinted) {
        // forceSolidOn：原作 Material.CLOTH 挡液体；1.20 没碰撞箱的方块默认会被水冲掉、被岩浆烧掉（岩浆烧掉时一颗魔晶都不掉）。
        // 同原版蜘蛛网、压力板的写法
        return ECBlocks.register(name, () -> new DropsClusterBlock(BlockBehaviour.Properties.of()
                        .mapColor(MapColor.NONE)
                        .forceSolidOn()
                        .instabreak()
                        .noCollission()
                        .noOcclusion()),
                BlockSpec.of((provider, block) -> provider.simpleBlockWithItem(block,
                                provider.models().withExistingParent(provider.name(block),
                                                provider.modLoc(tinted ? "block/drops_cluster_tinted" : "block/drops_cluster"))
                                        .texture("base", provider.tex(texture))))
                        .loot((loot, block) -> loot.counted(block, drop.get(), 1, 6))
                        .blockTags());
    }

    /** 可染色方块的模型：原作 cube_colored（六面带 tintindex 0）+ 一张贴图；玻璃用 cutout 渲染层 */
    private static BlockSpec.ModelGen dyeableModel(String texture, boolean cutout) {
        return dyeableModel(texture, cutout ? "cutout" : null);
    }

    /**
     * 同上，渲染层由调用方给（半透明的贴图要 {@code "translucent"}：照 cutout 画，alpha 不是 0 的像素全变成不透明）。
     *
     * @param renderType 渲染层名（{@code "cutout"}、{@code "translucent"}…），null = 默认实心层
     */
    public static BlockSpec.ModelGen dyeableModel(String texture, @Nullable String renderType) {
        return (provider, block) -> {
            ResourceLocation tex = provider.tex(texture);
            BlockModelBuilder model = provider.models().withExistingParent(provider.name(block), provider.modLoc("block/cube_colored"))
                    .texture("particle", tex);
            for (Direction direction : Direction.values()) {
                model.texture(direction.getSerializedName(), tex);
            }
            if (renderType != null) {
                model.renderType(renderType);
            }
            provider.simpleBlockWithItem(block, model);
        };
    }

    /**
     * 两层的可染色方块（第六批 6a 虚空石 / 虚空玻璃）：底层是不染色的原贴图，上面叠一层灰度纹路、面带 tintindex 0、{@code shade: false}，
     * 染料色只落在纹路上。底图很暗的方块整张乘色，16 种颜色乘出来都接近黑色、分不清；纹路核心是满亮度，乘出来就是原版烟花色本身。
     * <p>
     * 模板（src/main/resources/assets/essentialcraft5/models/block/）：
     * <ul>
     *   <li>{@code dyeable_veined}：镂空层（cutout_mipped），两层整格、共面，同花纹方块的 {@code fancy_overlayed}——两组面顶点完全相同，
     *       深度逐像素相等，后画的纹路在 LEQUAL 下总能盖上去，远处也不闪。</li>
     *   <li>{@code dyeable_veined_glass}：半透明层。半透明面在区块里按「面中心到镜头的距离」从远到近排序，两层共面时距离相等，
     *       谁先画要看排序对相等的处理，玻璃画在纹路后面就把纹路压暗。所以纹路六个面各做成一片、只沿自己的法线往外推 0.05 像素
     *       （同 {@code Camo.OVERLAY_PUSH}，面内仍是 0~16，相邻两格的纹路不重叠），从面前任何位置看纹路都更近、一定排在玻璃之后。</li>
     * </ul>
     * 纹路贴图由 tools/gen/void_veins.py 生成（四个出口在对边同一位置，相邻两格的纹路接得上）。方块颜色处理器照旧（MaterialsClient：tintindex 0 → 染料色），
     * 物品是白色纹路。
     *
     * @param base  底图（相对 textures/），也是破坏粒子
     * @param veins 纹路贴图（灰度，只有纹路处不透明）
     * @param glass true = 半透明层的玻璃模板
     */
    public static BlockSpec.ModelGen veinedDyeableModel(String base, String veins, boolean glass) {
        return (provider, block) -> provider.simpleBlockWithItem(block, provider.models()
                .withExistingParent(provider.name(block), provider.modLoc(glass ? "block/dyeable_veined_glass" : "block/dyeable_veined"))
                .texture("base", provider.tex(base))
                .texture("veins", provider.tex(veins))
                .texture("particle", provider.tex(base))
                .renderType(glass ? "translucent" : "cutout_mipped"));
    }
}
