package com.flameforge.essentialcraft5.datagen;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.registry.ECBlocks;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.model.generators.BlockModelBuilder;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.client.model.generators.ItemModelBuilder;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.client.model.generators.loaders.ObjModelBuilder;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 方块状态与方块模型：遍历 ECBlocks 里登记的方块，执行各自 BlockSpec 里的模型生成（见 BlockSpec 的常用模型）。
 * 这里的公开方法就是给 BlockSpec 的 lambda 用的工具；需要更复杂的（朝向、多状态）直接在 lambda 里调
 * BlockStateProvider 的原生方法（getVariantBuilder、horizontalBlock……）。
 * <ul>
 *   <li>原作 JSON 模型用 tools/import_assets.py model 导进 src/main/resources，用 {@link #existing}。</li>
 *   <li>原作 OBJ 模型用 tools/import_assets.py obj 导进来，用 {@link #obj}（原作 blockstate 的 "flip-v": true 对应 flipV）。</li>
 *   <li>植物（第六批）：{@link #cross}、{@link #tintedCross}（物品平面，{@link #flatBlockItem}）、{@link #cactus}，都是 cutout 层。</li>
 * </ul>
 */
public class ECBlockStateProvider extends BlockStateProvider {

    public ECBlockStateProvider(PackOutput output, ExistingFileHelper files) {
        super(output, EssentialCraft5.MODID, files);
    }

    @Override
    protected void registerStatesAndModels() {
        for (ECBlocks.Entry entry : ECBlocks.entries()) {
            entry.spec().model().generate(this, entry.block().get());
        }
    }

    public String name(Block block) {
        return ForgeRegistries.BLOCKS.getKey(block).getPath();
    }

    /** 贴图引用：不带命名空间的算本模组（"block/x"），带的原样（"minecraft:block/stone"） */
    public ResourceLocation tex(String texture) {
        return texture.contains(":") ? ResourceLocation.parse(texture) : modLoc(texture);
    }

    public void cubeAll(Block block, String texture) {
        simpleBlockWithItem(block, models().cubeAll(name(block), tex(texture)));
    }

    public void existing(Block block, String model) {
        ModelFile file = models().getExistingFile(modLoc("block/" + model));
        simpleBlockWithItem(block, file);
    }

    /**
     * @param objName  models/block/ 下的 OBJ 文件名（不带扩展名，沿用原作文件名）
     * @param particle 破坏粒子用的贴图（取 OBJ 的主贴图）
     */
    public void obj(Block block, String objName, String particle) {
        simpleBlockWithItem(block, objModel(name(block), objName, particle));
    }

    /** 十字交叉植物：parent minecraft:block/cross、cutout；物品平面（{@link #flatBlockItem}） */
    public void cross(Block block, String texture) {
        simpleBlock(block, models().cross(name(block), tex(texture)).renderType("cutout"));
        flatBlockItem(block, texture);
    }

    /** 带群系染色的十字交叉：parent minecraft:block/tinted_cross、cutout；物品平面 */
    public void tintedCross(Block block, String texture) {
        simpleBlock(block, models().withExistingParent(name(block), mcLoc("block/tinted_cross"))
                .texture("cross", tex(texture))
                .renderType("cutout"));
        flatBlockItem(block, texture);
    }

    /** 仙人掌式：parent minecraft:block/cactus（particle 取 side）、cutout；物品用方块模型 */
    public void cactus(Block block, String side, String top, String bottom) {
        simpleBlockWithItem(block, models().withExistingParent(name(block), mcLoc("block/cactus"))
                .texture("side", tex(side))
                .texture("top", tex(top))
                .texture("bottom", tex(bottom))
                .renderType("cutout"));
    }

    /**
     * 平面方块物品：models/item/&lt;注册名&gt;.json = item/generated + layer0（通常就是方块自己的贴图，同原版花、草、树苗）。
     * 方块物品的模型都在这里（方块状态 provider 自带的 itemModels()）出，不在 ECItemModelProvider 里出——那边只遍历 ECItems，
     * 两边写同一个文件会撞。
     */
    public ItemModelBuilder flatBlockItem(Block block, String texture) {
        return itemModels().withExistingParent(name(block), mcLoc("item/generated")).texture("layer0", tex(texture));
    }

    public BlockModelBuilder objModel(String modelName, String objName, String particle) {
        return models().getBuilder(modelName)
                .parent(models().getExistingFile(mcLoc("block/block")))
                .customLoader(ObjModelBuilder::begin)
                .modelLocation(modLoc("models/block/" + objName + ".obj"))
                .flipV(true)
                .end()
                .texture("particle", tex(particle));
    }
}
