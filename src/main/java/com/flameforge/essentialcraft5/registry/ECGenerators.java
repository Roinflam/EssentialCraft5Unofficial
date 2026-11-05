package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.block.ColdDistillatorBlock;
import com.flameforge.essentialcraft5.block.HeatGeneratorBlock;
import com.flameforge.essentialcraft5.block.NaturalFurnaceBlock;
import com.flameforge.essentialcraft5.block.RayTowerBlock;
import com.flameforge.essentialcraft5.blockentity.ColdDistillatorBlockEntity;
import com.flameforge.essentialcraft5.blockentity.HeatGeneratorBlockEntity;
import com.flameforge.essentialcraft5.blockentity.NaturalFurnaceBlockEntity;
import com.flameforge.essentialcraft5.blockentity.RayTowerBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.ColdDistillatorMenu;
import com.flameforge.essentialcraft5.menu.HeatGeneratorMenu;
import com.flameforge.essentialcraft5.menu.NaturalFurnaceMenu;
import com.flameforge.essentialcraft5.menu.RayTowerMenu;
import net.minecraft.advancements.critereon.StatePropertiesPredicate;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.ExplosionCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.client.model.generators.ConfiguredModel;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.registries.RegistryObject;

/**
 * 产能器与射线塔模块（第一批）：热力产能器、自然熔炉、冷凝器、射线塔（方块、方块实体、菜单同名）。
 * <p>
 * 新旧注册名：heatGenerator → heat_generator；naturalFurnace（方块类 BlockFlowerBurner、Tile 类 TileFlowerBurner）→ natural_furnace；
 * coldDistillator → cold_distillator；rayTower → ray_tower。
 * 四个方块原作都是 setHardness(1).setResistance(1).setLightOpacity(0)（先硬度后抗爆 → 抗爆 0.6，透天空光）；
 * 三台产能器 Material.ROCK + MapColor.PURPLE、OBJ 模型，射线塔 Material.IRON。
 * <p>
 * 工作台配方在 data/essentialcraft5/recipes/generators/，自然熔炉能烧的植物标签在 tools/tags/ecgenerators.json，
 * 界面文字在 tools/lang/keys/ecgenerators.json。客户端部分（界面、射线塔水晶 BER）见 client/GeneratorsClient。
 */
public final class ECGenerators {

    // ---------------- 射线塔 ----------------

    /**
     * 两格高：layer=bottom 用原作 JSON 模型（导入为 models/block/ray_tower.json，cutout_mipped），
     * layer=top 是只有破坏粒子的空模型（原作同样）。战利品表只在下半格掉自己。活塞推不动（两半会被推散）。
     */
    public static final RegistryObject<RayTowerBlock> RAY_TOWER = ECBlocks.register("ray_tower",
            () -> new RayTowerBlock(ECBlocks.machine(MapColor.METAL, 1F, ECBlocks.legacyResistance(1F, 1F, false))
                    .noOcclusion().pushReaction(PushReaction.BLOCK)),
            BlockSpec.of((provider, block) -> {
                ModelFile bottom = provider.models().getExistingFile(provider.modLoc("block/ray_tower"));
                ModelFile top = provider.models().getBuilder("ray_tower_top").texture("particle", provider.mcLoc("block/anvil"));
                provider.getVariantBuilder(block).forAllStates(state -> ConfiguredModel.builder()
                        .modelFile(RayTowerBlock.isBottom(state) ? bottom : top).build());
                provider.simpleBlockItem(block, bottom);
            }).loot((loot, block) -> loot.table(block, LootTable.lootTable().withPool(LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1F))
                    .add(LootItem.lootTableItem(block).when(LootItemBlockStatePropertyCondition.hasBlockStateProperties(block)
                            .setProperties(StatePropertiesPredicate.Builder.properties()
                                    .hasProperty(RayTowerBlock.LAYER, RayTowerBlock.Layer.BOTTOM))))
                    .when(ExplosionCondition.survivesExplosion())))));

    public static final RegistryObject<BlockEntityType<RayTowerBlockEntity>> RAY_TOWER_BE =
            ECBlockEntities.type("ray_tower", RayTowerBlockEntity::new, RAY_TOWER);

    public static final RegistryObject<MenuType<RayTowerMenu>> RAY_TOWER_MENU =
            ECMenus.register("ray_tower", RayTowerMenu::new);

    // ---------------- 冷凝器 ----------------

    public static final RegistryObject<ColdDistillatorBlock> COLD_DISTILLATOR = ECBlocks.register("cold_distillator",
            () -> new ColdDistillatorBlock(ECBlocks.machine(MapColor.COLOR_PURPLE, 1F, ECBlocks.legacyResistance(1F, 1F, false)).noOcclusion()),
            BlockSpec.of(BlockSpec.obj("colddistillator", "block/model/colddistillator")));

    public static final RegistryObject<BlockEntityType<ColdDistillatorBlockEntity>> COLD_DISTILLATOR_BE =
            ECBlockEntities.type("cold_distillator", ColdDistillatorBlockEntity::new, COLD_DISTILLATOR);

    public static final RegistryObject<MenuType<ColdDistillatorMenu>> COLD_DISTILLATOR_MENU =
            ECMenus.register("cold_distillator", ColdDistillatorMenu::new);

    // ---------------- 自然熔炉 ----------------

    /** 原作模型文件名是 flowerburner（沿用） */
    public static final RegistryObject<NaturalFurnaceBlock> NATURAL_FURNACE = ECBlocks.register("natural_furnace",
            () -> new NaturalFurnaceBlock(ECBlocks.machine(MapColor.COLOR_PURPLE, 1F, ECBlocks.legacyResistance(1F, 1F, false)).noOcclusion()),
            BlockSpec.of(BlockSpec.obj("flowerburner", "block/model/flowerburner")));

    public static final RegistryObject<BlockEntityType<NaturalFurnaceBlockEntity>> NATURAL_FURNACE_BE =
            ECBlockEntities.type("natural_furnace", NaturalFurnaceBlockEntity::new, NATURAL_FURNACE);

    public static final RegistryObject<MenuType<NaturalFurnaceMenu>> NATURAL_FURNACE_MENU =
            ECMenus.register("natural_furnace", NaturalFurnaceMenu::new);

    // ---------------- 热力产能器 ----------------

    public static final RegistryObject<HeatGeneratorBlock> HEAT_GENERATOR = ECBlocks.register("heat_generator",
            () -> new HeatGeneratorBlock(ECBlocks.machine(MapColor.COLOR_PURPLE, 1F, ECBlocks.legacyResistance(1F, 1F, false)).noOcclusion()),
            BlockSpec.of(BlockSpec.obj("heatgenerator", "block/model/heatgenerator")));

    public static final RegistryObject<BlockEntityType<HeatGeneratorBlockEntity>> HEAT_GENERATOR_BE =
            ECBlockEntities.type("heat_generator", HeatGeneratorBlockEntity::new, HEAT_GENERATOR);

    public static final RegistryObject<MenuType<HeatGeneratorMenu>> HEAT_GENERATOR_MENU =
            ECMenus.register("heat_generator", HeatGeneratorMenu::new);

    private ECGenerators() {
    }

    /** 由 ECRegistries.register() 调用：类加载即把上面的条目登记进 DeferredRegister，这里再登记配置小节 */
    public static void init() {
        ECConfig.machine(RayTowerBlockEntity.Config::define);
        ECConfig.machine(ColdDistillatorBlockEntity.Config::define);
        ECConfig.machine(NaturalFurnaceBlockEntity.Config::define);
        ECConfig.machine(HeatGeneratorBlockEntity.Config::define);
    }
}
