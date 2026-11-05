package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.block.UltraHeatGeneratorBlock;
import com.flameforge.essentialcraft5.block.UltraNaturalFurnaceBlock;
import com.flameforge.essentialcraft5.blockentity.UltraHeatGeneratorBlockEntity;
import com.flameforge.essentialcraft5.blockentity.UltraNaturalFurnaceBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.UltraHeatGeneratorMenu;
import com.flameforge.essentialcraft5.menu.UltraNaturalFurnaceMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.client.model.generators.loaders.ObjModelBuilder;
import net.minecraftforge.registries.RegistryObject;

/**
 * 第六批 6b M3「终极产能器」（计划 tools/plan/batch6b.md §4.3）的注册类：终极热力产能器 {@code ultra_heat_generator}、
 * 终极自然熔炉 {@code ultra_natural_furnace}（只烧放进格子里的物品）。同批的虚空岩浆熔炉注册在 {@link ECMagmatic} 里（另外三档也在那里）。
 * <p>
 * 新旧注册名：ultraHeatGen → ultra_heat_generator（Gen 展开，和 heat_generator 配对）；ultraFlowerBurner → ultra_natural_furnace
 * （普通版原注册名就是 naturalFurnace，和它配对）。
 * 两个方块原作都是 registerBlockSimple(…, 1, 1, 0)（先抗爆后硬度 → 硬度 1、抗爆 1.0，不透光度 0 → 透天空光），Material.ROCK + MapColor.BLACK；
 * 外观是普通版的 OBJ 模型换一张贴图（原作 blockstate 里改 #initialshadinggroup），这里用 OBJ loader 的 mtl_override 指向自己的 MTL
 * （models/block/ultraheatgenerator.mtl、ultraflowerburner.mtl）。不转成 JSON 模型：OBJ 里有不少不轴对齐的面，JSON 元素转不准。
 * <p>
 * 工作台配方在 data/essentialcraft5/recipes/ultragenerators/（VRV / CGC / VRV：虚空板、MRU 反应核、虚空核心、中间放普通版），
 * 终极自然熔炉能烧的四类方块标签在 tools/tags/ecultragenerators.json，界面文字在 tools/lang/keys/ecultragenerators.json，
 * 客户端部分（两个界面）见 client/UltraGeneratorsClient。
 */
public final class ECUltraGenerators {

    // ---------------- 终极热力产能器 ----------------

    public static final RegistryObject<UltraHeatGeneratorBlock> ULTRA_HEAT_GENERATOR = ECBlocks.register("ultra_heat_generator",
            () -> new UltraHeatGeneratorBlock(ECBlocks.machine(MapColor.COLOR_BLACK, 1F, ECBlocks.legacyResistance(1F, 1F, true)).noOcclusion()),
            BlockSpec.of(retexturedObj("heatgenerator", "ultraheatgenerator")));

    public static final RegistryObject<BlockEntityType<UltraHeatGeneratorBlockEntity>> ULTRA_HEAT_GENERATOR_BE =
            ECBlockEntities.type("ultra_heat_generator", UltraHeatGeneratorBlockEntity::new, ULTRA_HEAT_GENERATOR);

    public static final RegistryObject<MenuType<UltraHeatGeneratorMenu>> ULTRA_HEAT_GENERATOR_MENU =
            ECMenus.register("ultra_heat_generator", UltraHeatGeneratorMenu::new);

    // ---------------- 终极自然熔炉 ----------------

    /** 原作模型文件名是 flowerburner（沿用），贴图 ultraflowerburner */
    public static final RegistryObject<UltraNaturalFurnaceBlock> ULTRA_NATURAL_FURNACE = ECBlocks.register("ultra_natural_furnace",
            () -> new UltraNaturalFurnaceBlock(ECBlocks.machine(MapColor.COLOR_BLACK, 1F, ECBlocks.legacyResistance(1F, 1F, true)).noOcclusion()),
            BlockSpec.of(retexturedObj("flowerburner", "ultraflowerburner")));

    public static final RegistryObject<BlockEntityType<UltraNaturalFurnaceBlockEntity>> ULTRA_NATURAL_FURNACE_BE =
            ECBlockEntities.type("ultra_natural_furnace", UltraNaturalFurnaceBlockEntity::new, ULTRA_NATURAL_FURNACE);

    public static final RegistryObject<MenuType<UltraNaturalFurnaceMenu>> ULTRA_NATURAL_FURNACE_MENU =
            ECMenus.register("ultra_natural_furnace", UltraNaturalFurnaceMenu::new);

    private ECUltraGenerators() {
    }

    /**
     * 普通版的 OBJ 换一张贴图：models/block/&lt;objName&gt;.obj + models/block/&lt;texture&gt;.mtl（map_Kd 指向 block/model/&lt;texture&gt;），
     * 破坏粒子取同一张贴图。写法同 ECPentacle 的淡青水晶柱；runData 时 overrideMaterialLibrary 会检查 MTL 存在，漏了当场报错。
     * 渲染层同普通版（不指定 = solid）：ultraflowerburner.png 的透明像素和 flowerburner.png 逐像素相同，ultraheatgenerator.png 没有透明像素。
     */
    private static BlockSpec.ModelGen retexturedObj(String objName, String texture) {
        return (provider, block) -> {
            ModelFile model = provider.models().getBuilder(provider.name(block))
                    .parent(provider.models().getExistingFile(provider.mcLoc("block/block")))
                    .customLoader(ObjModelBuilder::begin)
                    .modelLocation(provider.modLoc("models/block/" + objName + ".obj"))
                    .flipV(true)
                    .overrideMaterialLibrary(provider.modLoc("models/block/" + texture + ".mtl"))
                    .end()
                    .texture("particle", provider.modLoc("block/model/" + texture));
            provider.simpleBlockWithItem(block, model);
        };
    }

    /** 由 ECRegistries.register() 调用：类加载即把上面的条目登记进 DeferredRegister，这里再登记配置小节 */
    public static void init() {
        ECConfig.machine(UltraHeatGeneratorBlockEntity.Config::define);
        ECConfig.machine(UltraNaturalFurnaceBlockEntity.Config::define);
    }
}
