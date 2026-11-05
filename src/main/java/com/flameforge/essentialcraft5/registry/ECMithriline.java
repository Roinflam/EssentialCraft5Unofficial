package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.CreativeESPEStorageBlock;
import com.flameforge.essentialcraft5.block.DyeableBlock;
import com.flameforge.essentialcraft5.block.MithrilineCrystalPillarBlock;
import com.flameforge.essentialcraft5.block.MithrilineFurnaceBlock;
import com.flameforge.essentialcraft5.block.TripleTallBlock;
import com.flameforge.essentialcraft5.blockentity.CreativeESPEStorageBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MithrilineCrystalPillarBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MithrilineFurnaceBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.MithrilineFurnaceMenu;
import com.flameforge.essentialcraft5.recipe.MithrilineFurnaceRecipe;
import com.flameforge.essentialcraft5.structure.StructurePattern;
import com.flameforge.essentialcraft5.structure.StructurePatterns;
import net.minecraft.advancements.critereon.StatePropertiesPredicate;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.ExplosionCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.client.model.generators.BlockModelBuilder;
import net.minecraftforge.client.model.generators.ConfiguredModel;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.registries.RegistryObject;

/**
 * 米斯莱与 ESPE 模块（第二批，batch2.md §3.6）：米斯莱锭 / 水晶 / 板、米斯莱护板、米斯莱水晶（三格高的 ESPE 源）、
 * 米斯莱熔炉（新配方类型 {@code essentialcraft5:mithriline_furnace}）、创造 ESPE 源。
 * <p>
 * 新旧注册名（总表见 tools/id_map_*.json）：
 * <ul>
 *   <li>genItem:50 / 48 / 49 → mithriline_ingot / mithriline_crystal / mithriline_plate</li>
 *   <li>invertedPlating（BlockDyeable，字段名 invertedBlock）→ mithriline_plating</li>
 *   <li>mithrilineCrystal:0（BlockMithrilineCrystal，TYPE=mithriline，meta%3 是三段）→ mithriline_crystal_pillar
 *       （物品 genItem:48 已经叫 mithriline_crystal，方块加 _pillar；淡青 / 虚空 / 恶魔 / 暗渎四种跟着以后的批次）</li>
 *   <li>mithrilineFurnace → mithriline_furnace；device:8（TileCreativeESPESource）→ creative_espe_storage</li>
 * </ul>
 * 工作台配方在 data/essentialcraft5/recipes/mithriline/，熔炉转化表在 recipes/mithriline/furnace/（tools/gen/mithriline_furnace_recipes.py 生成，
 * 改表后重跑），标签在 tools/tags/ecmithriline.json（米斯莱板进 essentialcraft5:plates/magic、米斯莱护板进富集仓外壳 essentialcraft5:mrucuec_casing），
 * 界面和手册文字在 tools/lang/keys/ecmithriline.json，手册章节 assets/essentialcraft5/guide/mithriline.json。
 * 客户端部分（熔炉界面、水晶柱 BER、手册配方画法）见 client/MithrilineClient。
 */
public final class ECMithriline {

    // ---------------- 材料 ----------------

    /** genItem:50；工作台 4 米斯莱粉 + 魔力锭，或米斯莱熔炉 1 米斯莱粉 60 ESPE */
    public static final RegistryObject<Item> MITHRILINE_INGOT = item("mithriline_ingot", "item/misc/mithrilineingot");
    /** genItem:48（原作矿辞 gemMithrilineCrystal）；工作台 4 米斯莱锭 + 米斯莱粉，或米斯莱熔炉 1 MRU 共鸣水晶 120 ESPE */
    public static final RegistryObject<Item> MITHRILINE_CRYSTAL = item("mithriline_crystal", "item/misc/mithrilinecrystal");
    /** genItem:49；魔力工作台 强化板 + 米斯莱锭×4，400 MRU（原作 RecipesCore L88）；在 essentialcraft5:plates/magic 标签里 */
    public static final RegistryObject<Item> MITHRILINE_PLATE = item("mithriline_plate", "item/misc/mithrilineplate");

    // ---------------- 方块 ----------------

    /**
     * 原作 BlockDyeable(Material.ROCK, MapColor.GREEN).setHardness(4).setResistance(100) → 抗爆 60；要镐。
     * 16 色染色（手持染料右键，同魔力强化石），颜色由框架的 MaterialsClient 自动注册。富集仓外壳、米斯莱熔炉结构用它。
     */
    public static final RegistryObject<DyeableBlock> MITHRILINE_PLATING = ECBlocks.register("mithriline_plating",
            () -> new DyeableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .strength(4F, ECBlocks.legacyResistance(4F, 100F, false))
                    .requiresCorrectToolForDrops(), false),
            BlockSpec.of(dyeableModel("block/invertedplatingblock")));

    /**
     * 原作 BlockMithrilineCrystal(Material.ROCK).setHardness(1).setResistance(1).setLightOpacity(1)，SoundType.GLASS，
     * 米斯莱那种的地图颜色 GREEN → 抗爆 0.6；要镐。三格高（{@link TripleTallBlock}），活塞推不动。
     * <p>
     * 方块模型是空的，只给破坏粒子（原作 blockstate 用 minecraft:block + invertedplatingblock 粒子）；浮着转的水晶由 BER 画，
     * BER 用的 OBJ 模型 block/mithriline_crystal_pillar_floating 也在这里生成。
     * 物品用原作的平面图标 block/icons/mithrilinecrystal（原作没装 CodeChickenLib 时就是它，装了才用 3D 的物品渲染器）。
     * 战利品表只在底座掉自己，拆哪一段都只掉一份。
     * <p>
     * 第三批起水晶柱按档位分方块（{@link MithrilineCrystalPillarBlock.Tier}），别的柱子（淡青 / 虚空 / 恶魔 / 暗渎）由各自的模块照这里注册，
     * 方块实体类复用、类型各自注册；所有柱子都在方块标签 essentialcraft5:espe_pillars 里（米斯莱柱由框架的 tools/tags/framework3c.json 加）。
     */
    public static final RegistryObject<MithrilineCrystalPillarBlock> MITHRILINE_CRYSTAL_PILLAR = ECBlocks.register("mithriline_crystal_pillar",
            () -> new MithrilineCrystalPillarBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .sound(SoundType.GLASS)
                    .strength(1F, ECBlocks.legacyResistance(1F, 1F, false))
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK),
                    // 档位 0（ESPE tier 0、附魔加成 7.5）；类型字段在后面，lambda 里要写类名限定，否则是非法前向引用
                    MithrilineCrystalPillarBlock.Tier.MITHRILINE, () -> ECMithriline.MITHRILINE_CRYSTAL_PILLAR_BE.get()),
            BlockSpec.of((provider, block) -> {
                ModelFile empty = provider.models().getBuilder(provider.name(block))
                        .texture("particle", provider.modLoc("block/invertedplatingblock"));
                provider.getVariantBuilder(block).forAllStates(state -> ConfiguredModel.builder().modelFile(empty).build());
                provider.objModel("mithriline_crystal_pillar_floating", "mithrilinecrystal", "block/model/mithrilinecrystal");
                provider.itemModels().withExistingParent(provider.name(block), provider.mcLoc("item/generated"))
                        .texture("layer0", provider.modLoc("block/icons/mithrilinecrystal"));
            }).loot((loot, block) -> loot.table(block, LootTable.lootTable().withPool(LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1F))
                    .add(LootItem.lootTableItem(block).when(LootItemBlockStatePropertyCondition.hasBlockStateProperties(block)
                            .setProperties(StatePropertiesPredicate.Builder.properties()
                                    .hasProperty(TripleTallBlock.PART, TripleTallBlock.Part.BOTTOM))))
                    .when(ExplosionCondition.survivesExplosion())))));

    public static final RegistryObject<BlockEntityType<MithrilineCrystalPillarBlockEntity>> MITHRILINE_CRYSTAL_PILLAR_BE =
            ECBlockEntities.type("mithriline_crystal_pillar", MithrilineCrystalPillarBlockEntity::new, MITHRILINE_CRYSTAL_PILLAR);

    /** 原作 registerBlockSimple(…, 1, 1, 1)：先抗爆后硬度 → 抗爆 1.0；不透光度 1（不透天空光）；Material.ROCK、MapColor.GREEN，OBJ 模型 */
    public static final RegistryObject<MithrilineFurnaceBlock> MITHRILINE_FURNACE = ECBlocks.register("mithriline_furnace",
            () -> new MithrilineFurnaceBlock(ECBlocks.machine(MapColor.COLOR_GREEN, 1F, ECBlocks.legacyResistance(1F, 1F, true)).noOcclusion()),
            BlockSpec.of(BlockSpec.obj("mithrilinefurnace", "block/model/mithrilinefurnace")));

    public static final RegistryObject<BlockEntityType<MithrilineFurnaceBlockEntity>> MITHRILINE_FURNACE_BE =
            ECBlockEntities.type("mithriline_furnace", MithrilineFurnaceBlockEntity::new, MITHRILINE_FURNACE);

    public static final RegistryObject<MenuType<MithrilineFurnaceMenu>> MITHRILINE_FURNACE_MENU =
            ECMenus.register("mithriline_furnace", MithrilineFurnaceMenu::new);

    /** 原作 device:8：setHardness(1).setResistance(1) → 抗爆 0.6；没有界面 */
    public static final RegistryObject<CreativeESPEStorageBlock> CREATIVE_ESPE_STORAGE = ECBlocks.register("creative_espe_storage",
            () -> new CreativeESPEStorageBlock(ECBlocks.machine(MapColor.STONE, 1F, ECBlocks.legacyResistance(1F, 1F, false))),
            BlockSpec.of(BlockSpec.cubeAll("block/creativeespestorage")));

    public static final RegistryObject<BlockEntityType<CreativeESPEStorageBlockEntity>> CREATIVE_ESPE_STORAGE_BE =
            ECBlockEntities.type("creative_espe_storage", CreativeESPEStorageBlockEntity::new, CREATIVE_ESPE_STORAGE);

    // ---------------- 配方类型 ----------------

    /** 米斯莱熔炉的物品转化（原作 MithrilineFurnaceRecipes，RecipesCore L101-202）；JSON 格式见 MithrilineFurnaceRecipe */
    public static final RegistryObject<RecipeType<MithrilineFurnaceRecipe>> MITHRILINE_FURNACE_TYPE = ECRecipes.type("mithriline_furnace");
    public static final RegistryObject<RecipeSerializer<MithrilineFurnaceRecipe>> MITHRILINE_FURNACE_SERIALIZER =
            ECRecipes.serializer("mithriline_furnace", MithrilineFurnaceRecipe.Serializer::new);

    // ---------------- 结构（必须写在它引用的方块字段之后） ----------------

    /**
     * 13 个 ESPE 源的位置（原作 possiblePowerSources）：不参与判定，只画进手册、给熔炉列位置。
     * 熔炉从这些位置上任何带 ESPE 的方块实体抽（一般是米斯莱水晶；创造 ESPE 源也行）。
     */
    public static final StructurePattern.Rule FURNACE_PILLAR = StructurePattern.Rule
            .block(MITHRILINE_CRYSTAL_PILLAR, "structure.essentialcraft5.mithriline_furnace.pillar").optional();

    /**
     * 米斯莱熔炉的结构（原作 TileMithrilineFurnace.isStructureCorrect）：24 块米斯莱护板，颜色随意。
     * 下层 12 块（熔炉正下方那格原作不要求）、同层外圈 8 块、外圈四角再往上一格 4 块。四面对称，按北向判定。
     * 研究手册的结构页按 id 画这一份（"structure": "essentialcraft5:mithriline_furnace"）。
     */
    public static final StructurePattern FURNACE_STRUCTURE = StructurePatterns.register(EssentialCraft5.id("mithriline_furnace"),
            StructurePattern.builder()
                    .where('P', StructurePattern.Rule.block(MITHRILINE_PLATING, "structure.essentialcraft5.mithriline_furnace.plating"))
                    .where('C', FURNACE_PILLAR)
                    .where('F', StructurePattern.Rule.block(MITHRILINE_FURNACE))
                    .anchor('F')
                    .layer("  P  ", " PPP ", "PP PP", " PPP ", "  P  ")
                    .layer("P P P", " C C ", "P F P", " C C ", "P P P")
                    .layer("P C P", "     ", "C C C", "     ", "P C P")
                    .layer("C   C", "     ", "     ", "     ", "C   C")
                    .build());

    private ECMithriline() {
    }

    /** 由 ECRegistries.register() 调用：类加载即把上面的条目登记进 DeferredRegister，这里再登记配置小节 */
    public static void init() {
        ECConfig.machine(MithrilineCrystalPillarBlockEntity.Config::define);
        ECConfig.machine(MithrilineFurnaceBlockEntity.Config::define);
        ECConfig.machine(CreativeESPEStorageBlockEntity.Config::define);
    }

    // ---------------- 注册工具 ----------------

    private static RegistryObject<Item> item(String name, String texture) {
        return ECItems.register(name, () -> new Item(new Item.Properties()), ItemSpec.generated(texture));
    }

    /** 可染色方块的模型：原作 cube_colored（六面带 tintindex 0）+ 一张贴图（同 ECMaterials 的强化石） */
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
