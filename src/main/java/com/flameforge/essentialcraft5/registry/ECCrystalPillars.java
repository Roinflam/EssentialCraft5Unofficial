package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.block.MithrilineCrystalPillarBlock;
import com.flameforge.essentialcraft5.block.TripleTallBlock;
import com.flameforge.essentialcraft5.blockentity.MithrilineCrystalPillarBlockEntity;
import com.flameforge.essentialcraft5.datagen.ECBlockLoot;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.critereon.StatePropertiesPredicate;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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
import net.minecraftforge.client.model.generators.ConfiguredModel;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.client.model.generators.loaders.ObjModelBuilder;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 虚空 / 恶魔 / 暗渎三种 ESPE 水晶柱（第七批，batch7 §3.1 第 5 条）：原作 BlockMithrilineCrystal 的 meta 6 / 9 / 12（TYPE void / demonic / shade），
 * 和米斯莱柱、淡青柱同一个方块类 {@link MithrilineCrystalPillarBlock}，档位 2 / 3 / 4、附魔加成 30 / 60 / 120（只在底座），
 * 产能、上限五种共用米斯莱柱那一份配置（原作全类共用）。
 * <p>
 * 框架 7-F 先注册方块、方块物品、方块实体类型：恶魔五芒星（M1）的结构要 2 档以上的柱子、交易池要虚空柱，灵魂五芒星 2、3 档与恶魔柱配方（M3）
 * 也要它们，先注册好三个模块就不用互相等。配方在 data/essentialcraft5/recipes/crystal_pillars/（虚空柱 IGI / ICI / IPI：虚空板 ×6、虚空核心、
 * 淡青柱、MRU 反应核；恶魔柱 IGI / ICI / IGI：阿克罗尼特锭 ×6、恶魔核心 ×2、虚空柱）。暗渎柱原作没有配方，第七批起只放创造栏；
 * 第十三批 13e 给了生存配方（batch13.md §2.2 第二十二章、D18）：魔力工作台，中心恶魔柱、四角暗影核心 ×1 + 影晶 ×3，2 万 MRU
 * （{@code recipes/crystal_pillars/shade_crystal_pillar.json}），档位 4、产 ESPE ×4、附魔加成 120 一起进生存；方块物品改成普通的
 * {@link ShadePillarItem}（还是史诗，提示一行用途，不再写「没有合成配方」）。灵魂五芒星 4 档的 4 根柱子就是它。
 * <p>
 * 照 {@code ECPentacle} 的淡青柱写：硬度 1、抗爆 1（→ 0.6），要镐，三格高、活塞推不动，只有底座掉落；地图颜色照原作 CrystalType
 * （BLACK / RED / GRAY → {@code COLOR_BLACK / COLOR_RED / COLOR_GRAY}）；破坏粒子照原作 blockstate（虚空石、恶魔护板、暗影腐化第 7 阶）；
 * 浮动水晶用米斯莱柱的 OBJ，各换一份 MTL（{@code models/block/{void,demonic,shade}crystal.mtl}，贴图 {@code block/model/*crystal}），
 * 由 {@code client/CrystalPillarsClient} 登记额外模型和渲染器。物品图标是原作的 {@code block/icons/*crystal}。
 * 三种都进方块标签 {@code essentialcraft5:espe_pillars}（tools/tags/eccrystalpillars.json）。灵魂五芒星 2、3 档的结构按注册名写在
 * {@code ECPentacle}（{@code laterState}），柱子一注册就成立。
 * <p>
 * 新旧注册名：mithrilineCrystal:6 / 9 / 12 → void_crystal_pillar / demonic_crystal_pillar / shade_crystal_pillar（中文「虚空 / 恶魔 / 暗渎水晶柱」）。
 */
public final class ECCrystalPillars {

    /** 虚空水晶柱：档位 2（灵魂五芒星 2 档、恶魔五芒星荧石上的 4 个柱位） */
    public static final RegistryObject<MithrilineCrystalPillarBlock> VOID_CRYSTAL_PILLAR = ECBlocks.register("void_crystal_pillar",
            () -> new MithrilineCrystalPillarBlock(properties(MapColor.COLOR_BLACK),
                    // 类型字段在后面：lambda 里要写类名限定，否则是非法前向引用
                    MithrilineCrystalPillarBlock.Tier.VOID, () -> ECCrystalPillars.VOID_CRYSTAL_PILLAR_BE.get()),
            spec("void_crystal_pillar", "block/voidstone", "voidcrystal"));

    public static final RegistryObject<BlockEntityType<MithrilineCrystalPillarBlockEntity>> VOID_CRYSTAL_PILLAR_BE =
            ECBlockEntities.type("void_crystal_pillar", MithrilineCrystalPillarBlockEntity::new, VOID_CRYSTAL_PILLAR);

    /** 恶魔水晶柱：档位 3（灵魂五芒星 3 档，每点 ESPE 按 2 倍算） */
    public static final RegistryObject<MithrilineCrystalPillarBlock> DEMONIC_CRYSTAL_PILLAR = ECBlocks.register("demonic_crystal_pillar",
            () -> new MithrilineCrystalPillarBlock(properties(MapColor.COLOR_RED),
                    MithrilineCrystalPillarBlock.Tier.DEMONIC, () -> ECCrystalPillars.DEMONIC_CRYSTAL_PILLAR_BE.get()),
            spec("demonic_crystal_pillar", "block/demonicplatingblock", "demoniccrystal"));

    public static final RegistryObject<BlockEntityType<MithrilineCrystalPillarBlockEntity>> DEMONIC_CRYSTAL_PILLAR_BE =
            ECBlockEntities.type("demonic_crystal_pillar", MithrilineCrystalPillarBlockEntity::new, DEMONIC_CRYSTAL_PILLAR);

    /** 暗渎水晶柱：档位 4、附魔加成 120；13e 起有生存配方（灵魂五芒星 4 档） */
    public static final RegistryObject<MithrilineCrystalPillarBlock> SHADE_CRYSTAL_PILLAR = ECBlocks.register("shade_crystal_pillar",
            () -> new MithrilineCrystalPillarBlock(properties(MapColor.COLOR_GRAY),
                    MithrilineCrystalPillarBlock.Tier.SHADE, () -> ECCrystalPillars.SHADE_CRYSTAL_PILLAR_BE.get()),
            spec("shade_crystal_pillar", "block/corruption/shade/7", "shadecrystal")
                    .item(ShadePillarItem::new));

    public static final RegistryObject<BlockEntityType<MithrilineCrystalPillarBlockEntity>> SHADE_CRYSTAL_PILLAR_BE =
            ECBlockEntities.type("shade_crystal_pillar", MithrilineCrystalPillarBlockEntity::new, SHADE_CRYSTAL_PILLAR);

    private ECCrystalPillars() {
    }

    /** 由 ECRegistries.register() 调用：类加载即把上面的条目登记进 DeferredRegister（配置共用米斯莱柱那一份，不用再登记） */
    public static void init() {
    }

    /** 某种柱子的浮动水晶额外模型 id（{@code models/block/<注册名>_floating.json}，datagen 随方块生成） */
    public static String floatingModelName(String registryName) {
        return registryName + "_floating";
    }

    // ---------------- 工具 ----------------

    /** 原作 registerBlockSimple(…, 1, 1, 1)：Material.ROCK、玻璃声，先抗爆后硬度 → 抗爆 0.6；要镐；不透光；三段会被推散，必须推不动 */
    private static BlockBehaviour.Properties properties(MapColor color) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .sound(SoundType.GLASS)
                .strength(1F, ECBlocks.legacyResistance(1F, 1F, false))
                .requiresCorrectToolForDrops()
                .noOcclusion()
                .pushReaction(PushReaction.BLOCK);
    }

    /**
     * 模型与掉落照淡青柱：方块模型是空的（只有破坏粒子），浮动水晶是米斯莱柱的 OBJ + 这种柱子的 MTL；物品图标平面；只在 part=bottom 掉自己
     *
     * @param particle 破坏粒子贴图（原作 blockstates/mithrilinecrystal.json 各 TYPE 的 particle）
     * @param crystal  MTL 与图标的名字（{@code models/block/<crystal>.mtl}、{@code block/model/<crystal>}、{@code block/icons/<crystal>}）
     */
    private static BlockSpec spec(String name, String particle, String crystal) {
        return BlockSpec.of((provider, block) -> {
            ModelFile empty = provider.models().getBuilder(provider.name(block))
                    .texture("particle", provider.modLoc(particle));
            provider.getVariantBuilder(block).forAllStates(state -> ConfiguredModel.builder().modelFile(empty).build());
            provider.models().getBuilder(floatingModelName(name))
                    .parent(provider.models().getExistingFile(provider.mcLoc("block/block")))
                    .customLoader(ObjModelBuilder::begin)
                    .modelLocation(provider.modLoc("models/block/mithrilinecrystal.obj"))
                    .flipV(true)
                    .overrideMaterialLibrary(provider.modLoc("models/block/" + crystal + ".mtl"))
                    .end()
                    .texture("particle", provider.modLoc("block/model/" + crystal));
            provider.itemModels().withExistingParent(provider.name(block), provider.mcLoc("item/generated"))
                    .texture("layer0", provider.modLoc("block/icons/" + crystal));
        }).loot(ECCrystalPillars::bottomOnly);
    }

    private static void bottomOnly(ECBlockLoot loot, Block block) {
        loot.table(block, LootTable.lootTable().withPool(LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1F))
                .add(LootItem.lootTableItem(block).when(LootItemBlockStatePropertyCondition.hasBlockStateProperties(block)
                        .setProperties(StatePropertiesPredicate.Builder.properties()
                                .hasProperty(TripleTallBlock.PART, TripleTallBlock.Part.BOTTOM))))
                .when(ExplosionCondition.survivesExplosion())));
    }

    /** 暗渎柱的方块物品：史诗稀有度、提示框一行灰字写用途（13e 以前是只放创造栏的 CreativeOnly 物品，键名照旧） */
    public static final class ShadePillarItem extends BlockItem {

        public ShadePillarItem(Block block) {
            super(block, new Item.Properties().rarity(Rarity.EPIC));
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.shade_crystal_pillar").withStyle(ChatFormatting.GRAY));
        }
    }

    /** 三种柱子（方块），按档位从低到高；测试和客户端类用 */
    public static List<RegistryObject<MithrilineCrystalPillarBlock>> all() {
        return List.of(VOID_CRYSTAL_PILLAR, DEMONIC_CRYSTAL_PILLAR, SHADE_CRYSTAL_PILLAR);
    }
}
