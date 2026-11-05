package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.datagen.ECBlockLoot;
import com.flameforge.essentialcraft5.datagen.ECBlockStateProvider;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * 方块在注册时声明的规格：datagen 靠它自动生成方块状态/模型、战利品表、标签，模块不用改 datagen 代码。
 * <pre>
 * ECBlocks.register("heat_generator", () -&gt; new HeatGeneratorBlock(...),
 *         BlockSpec.of(BlockSpec.obj("heatgenerator", "block/model/heatgenerator")));
 * </pre>
 * 默认：掉自己、进 mineable/pickaxe、带同名 BlockItem。模型必须给（没有默认，漏了 datagen 会报错）。
 * 这里的 lambda 只在 datagen 时执行，游戏运行时不会碰到 datagen 的类。
 */
public final class BlockSpec {

    @FunctionalInterface
    public interface ModelGen {
        void generate(ECBlockStateProvider provider, Block block);
    }

    @FunctionalInterface
    public interface LootGen {
        void generate(ECBlockLoot loot, Block block);
    }

    final ModelGen model;
    LootGen loot = ECBlockLoot::self;
    final List<TagKey<Block>> blockTags = new ArrayList<>(List.of(BlockTags.MINEABLE_WITH_PICKAXE));
    final List<TagKey<Item>> itemTags = new ArrayList<>();
    Function<Block, Item> itemFactory = block -> new BlockItem(block, new Item.Properties());
    boolean creativeTab = true;
    boolean decoration = false;

    private BlockSpec(ModelGen model) {
        this.model = model;
    }

    public static BlockSpec of(ModelGen model) {
        return new BlockSpec(model);
    }

    public BlockSpec loot(LootGen loot) {
        this.loot = loot;
        return this;
    }

    /** 替换默认的挖掘工具标签（默认 mineable/pickaxe），比如镐挖改铲挖、要求铁镐 */
    @SafeVarargs
    public final BlockSpec blockTags(TagKey<Block>... tags) {
        blockTags.clear();
        Collections.addAll(blockTags, tags);
        return this;
    }

    @SafeVarargs
    public final BlockSpec addBlockTags(TagKey<Block>... tags) {
        Collections.addAll(blockTags, tags);
        return this;
    }

    @SafeVarargs
    public final BlockSpec itemTags(TagKey<Item>... tags) {
        Collections.addAll(itemTags, tags);
        return this;
    }

    /** 自定义方块物品（带 tooltip、特殊渲染之类）；传 null 表示没有物品（技术性方块） */
    public BlockSpec item(Function<Block, Item> factory) {
        this.itemFactory = factory;
        return this;
    }

    /** 不放进创造模式物品栏（技术性方块、未完成的内容） */
    public BlockSpec hidden() {
        this.creativeTab = false;
        return this;
    }

    public boolean inCreativeTab() {
        return creativeTab && itemFactory != null;
    }

    /**
     * 放进创造栏第三页「源质魔法：装饰」（花纹方块、花纹拟态这类纯装饰，第五批 F5-3），不进「方块」页；
     * 开发展示厅的方块排也跳过它们（装饰用场景拍）。和 {@link #hidden()} 同时用时仍然不进任何一页。
     */
    public BlockSpec decoration() {
        this.decoration = true;
        return this;
    }

    public boolean isDecoration() {
        return decoration;
    }

    public ModelGen model() {
        return model;
    }

    public LootGen lootGen() {
        return loot;
    }

    public List<TagKey<Block>> blockTagList() {
        return blockTags;
    }

    public List<TagKey<Item>> itemTagList() {
        return itemTags;
    }

    // ---- 常用模型 ----

    /** 六面同贴图（贴图路径相对 textures/，如 "block/creativemrustorage"） */
    public static ModelGen cubeAll(String texture) {
        return (provider, block) -> provider.cubeAll(block, texture);
    }

    /** 从原作导入到 src/main/resources 的 JSON 模型（models/block/&lt;model&gt;.json） */
    public static ModelGen existing(String model) {
        return (provider, block) -> provider.existing(block, model);
    }

    /** 原作 OBJ 模型：models/block/&lt;objName&gt;.obj，particle 为破坏粒子贴图 */
    public static ModelGen obj(String objName, String particle) {
        return (provider, block) -> provider.obj(block, objName, particle);
    }

    // ---- 植物类模型（第六批 F6b，海纳尔植物用）：都画在 cutout 层 ----

    /**
     * 十字交叉的植物（花、树苗那种），parent {@code minecraft:block/cross}；方块物品是平面的（layer0 = 同一张贴图，同原版花）。
     * 贴图路径相对 textures/，如 {@code "block/firstworld/flower_green"}。
     */
    public static ModelGen cross(String texture) {
        return (provider, block) -> provider.cross(block, texture);
    }

    /**
     * 带群系染色的十字交叉（草丛那种），parent {@code minecraft:block/tinted_cross}（面带 tintindex 0）；方块物品平面（item/generated 的 layer0
     * 自带 tintindex 0）。染色本身要模块在客户端注册 BlockColor / ItemColor（方块用 {@code BiomeColors.getAverageGrassColor}）。
     */
    public static ModelGen tintedCross(String texture) {
        return (provider, block) -> provider.tintedCross(block, texture);
    }

    /**
     * 仙人掌式模型，parent {@code minecraft:block/cactus}（侧面向内缩 1 像素、顶底整面）；三张贴图路径相对 textures/。
     * 方块物品直接用方块模型（同原版仙人掌）。
     */
    public static ModelGen cactus(String side, String top, String bottom) {
        return (provider, block) -> provider.cactus(block, side, top, bottom);
    }
}
