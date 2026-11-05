package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.block.MRUConverterBlock;
import com.flameforge.essentialcraft5.block.creative.InfiniteESPESourceBlock;
import com.flameforge.essentialcraft5.block.creative.InfiniteMRUSourceBlock;
import com.flameforge.essentialcraft5.block.creative.InfiniteThermalSourceBlock;
import com.flameforge.essentialcraft5.blockentity.MRUConverterBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MRUConverterBlockEntity.ConverterTier;
import com.flameforge.essentialcraft5.blockentity.creative.InfiniteESPESourceBlockEntity;
import com.flameforge.essentialcraft5.blockentity.creative.InfiniteMRUSourceBlockEntity;
import com.flameforge.essentialcraft5.item.creative.CreativeOnly;
import com.flameforge.essentialcraft5.item.creative.CreativeWardItem;
import com.flameforge.essentialcraft5.item.creative.InfiniteMRUCrystalItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.client.model.generators.ConfiguredModel;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.registries.RegistryObject;

/**
 * 第六批 6b M5 无尽道具（计划里叫「创造专属道具」）：原作没有、EC5 新增的一组「无尽」能源和护符。
 * <p>
 * 规则（policy §4.7「只放创造栏」）：没有配方，不进战利品、交易、地牢，只放在创造栏里，供服主用指令或商店发放给玩家。
 * 到了玩家手里生存模式照常能用：三个方块硬度 1、进 mineable/pickaxe，用镐挖下来掉落自己，可以搬走再放；
 * 抗爆 {@value #BLAST_RESISTANCE}（同黑曜石），苦力怕、TNT 炸不坏；不进 wither_immune / dragon_immune。
 * 物品提示最后一行写「没有合成配方」（{@link CreativeOnly}）。都不动世界方块、不漏 MRU、不产生腐化或辐射；
 * 两个源的周期扫描有半径、间隔和每轮上限，只看已完全加载的区块（blockentity/creative/CreativeScan）。
 * 和原作的「创造 MRU 源」「创造 ESPE 源」（被动、只能被抽）不同，无尽源会主动给周围的机器补满。
 * <ul>
 *   <li>{@code infinite_mru_source} 无尽 MRU 源：怎么拉都不减（绑定宝石不受量限），每秒给半径 8 内靠绑定宝石吃 MRU 的机器补满，每轮至多 32 台</li>
 *   <li>{@code infinite_espe_source} 无尽 ESPE 源：怎么抽都不减，每秒给半径 8 内带 ESPE 的方块实体（米斯莱熔炉、水晶柱）补满，每轮至多 32 个</li>
 *   <li>{@code infinite_thermal_source} 无尽冷热源：空手右键切换；热源对热力产能器系数 1（不衰减），冷源对冷凝器冷值 171（顶满配）</li>
 *   <li>{@code infinite_mru_crystal} 无尽 MRU 晶体：无底的 MRU 储存物品，玩家花 MRU（MRUDrain）全由它付、永远是满的；饰品栏指环位也能放</li>
 *   <li>{@code creative_ward} 无尽护佑护符：带着时 UBMRU 保持满、矩阵损伤和海纳尔辐射保持 0；饰品栏项链位也能放</li>
 *   <li>{@code infinite_mru_converter} 无尽 MRU 转换器（2026-10-13 加）：MRU 转换器的无尽档，不耗 MRU，按终极档的速率输出 FE 或植物魔法的魔力，
 *       推给贴着的方块；不漏 MRU，每人全服同时工作的台数和四档生存转换器一起数（见 {@link ECConverters}）</li>
 * </ul>
 * 共用框架里只加了判断（都写明出处）：{@code api/IMRUHandlerItem.isInfinite}、{@code mru/MRUDrain}（认无尽晶体）、
 * {@code corruption/CorruptionTicker}、{@code hoanna/Radiation}、{@code player/UBMRUEvents}（认护符）。
 * 贴图由 {@code tools/gen/creative_textures.py} 从原作贴图派生（金框 + 彩虹环 + 动画），记在 asset_map.tsv 的 DERIVED 行。
 */
public final class ECCreative {

    /** 三个无尽方块的抗爆，同黑曜石：原版的爆炸（TNT、苦力怕、末影水晶、凋灵生成时那一下）都炸不动 */
    public static final float BLAST_RESISTANCE = 1200F;

    // ---------------- 方块 ----------------

    public static final RegistryObject<InfiniteMRUSourceBlock> INFINITE_MRU_SOURCE = ECBlocks.register("infinite_mru_source",
            () -> new InfiniteMRUSourceBlock(infiniteBlock(MapColor.COLOR_PURPLE)
                    .lightLevel(state -> 10)),
            BlockSpec.of(BlockSpec.cubeAll("block/creative/infinite_mru_source"))
                    .item(block -> CreativeOnly.blockItem(block, "tooltip.essentialcraft5.creative.infinite_mru_source",
                            InfiniteMRUSourceBlockEntity.RADIUS, InfiniteMRUSourceBlockEntity.MAX_FILLED)));

    public static final RegistryObject<BlockEntityType<InfiniteMRUSourceBlockEntity>> INFINITE_MRU_SOURCE_BE =
            ECBlockEntities.type("infinite_mru_source", InfiniteMRUSourceBlockEntity::new, INFINITE_MRU_SOURCE);

    public static final RegistryObject<InfiniteESPESourceBlock> INFINITE_ESPE_SOURCE = ECBlocks.register("infinite_espe_source",
            () -> new InfiniteESPESourceBlock(infiniteBlock(MapColor.COLOR_GREEN)
                    .lightLevel(state -> 10)),
            BlockSpec.of(BlockSpec.cubeAll("block/creative/infinite_espe_source"))
                    .item(block -> CreativeOnly.blockItem(block, "tooltip.essentialcraft5.creative.infinite_espe_source",
                            InfiniteESPESourceBlockEntity.RADIUS, InfiniteESPESourceBlockEntity.MAX_FILLED)));

    public static final RegistryObject<BlockEntityType<InfiniteESPESourceBlockEntity>> INFINITE_ESPE_SOURCE_BE =
            ECBlockEntities.type("infinite_espe_source", InfiniteESPESourceBlockEntity::new, INFINITE_ESPE_SOURCE);

    /** 没有方块实体；方块状态 cold=false 用热源贴图、cold=true 用冷源贴图，物品用热源那张 */
    public static final RegistryObject<InfiniteThermalSourceBlock> INFINITE_THERMAL_SOURCE = ECBlocks.register("infinite_thermal_source",
            () -> new InfiniteThermalSourceBlock(infiniteBlock(MapColor.COLOR_ORANGE)
                    .lightLevel(state -> InfiniteThermalSourceBlock.isCold(state) ? 7 : 13)),
            BlockSpec.of((provider, block) -> {
                ModelFile hot = provider.models().cubeAll("infinite_thermal_source_hot",
                        provider.tex("block/creative/infinite_thermal_source_hot"));
                ModelFile cold = provider.models().cubeAll("infinite_thermal_source_cold",
                        provider.tex("block/creative/infinite_thermal_source_cold"));
                provider.getVariantBuilder(block).forAllStates(state -> ConfiguredModel.builder()
                        .modelFile(InfiniteThermalSourceBlock.isCold(state) ? cold : hot).build());
                provider.simpleBlockItem(block, hot);
            }).item(block -> CreativeOnly.blockItem(block, "tooltip.essentialcraft5.creative.infinite_thermal_source",
                    (int) InfiniteThermalSourceBlock.COLD_VALUE)));

    /**
     * 无尽 MRU 转换器（2026-10-13，mru_converter.md；policy §4.7、§4.14）：MRU 转换器的无尽档，不耗 MRU、没有宝石槽，按
     * {@code machines.mru_converter.infinite.max_mru_per_tick}（默认 10 240）折算输出 FE 或植物魔法的魔力。方块类、方块实体类和四档生存的共用
     * （{@link MRUConverterBlock}，档位 INFINITE），菜单、配置在 {@link ECConverters}；这里只登记方块和它自己的方块实体类型。
     * 物品提示由方块写（用途、每刻输出，最后一行「没有合成配方」），所以物品用普通的史诗稀有度方块物品。不漏 MRU；每人全服台数和四档一起数。
     */
    public static final RegistryObject<MRUConverterBlock> INFINITE_MRU_CONVERTER = ECBlocks.register("infinite_mru_converter",
            () -> new MRUConverterBlock(ConverterTier.INFINITE, infiniteBlock(MapColor.COLOR_YELLOW)),
            BlockSpec.of(MRUConverterBlock.models("infinite"))
                    .item(block -> new BlockItem(block, new Item.Properties().rarity(Rarity.EPIC))));

    public static final RegistryObject<BlockEntityType<MRUConverterBlockEntity>> INFINITE_MRU_CONVERTER_BE =
            ECBlockEntities.type("infinite_mru_converter", MRUConverterBlockEntity::new,
                    INFINITE_MRU_CONVERTER);

    // ---------------- 物品 ----------------

    public static final RegistryObject<InfiniteMRUCrystalItem> INFINITE_MRU_CRYSTAL = ECItems.register("infinite_mru_crystal",
            () -> new InfiniteMRUCrystalItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)),
            ItemSpec.generated("item/creative/infinite_mru_crystal").tags(ECTags.CURIOS_RING));

    public static final RegistryObject<CreativeWardItem> CREATIVE_WARD = ECItems.register("creative_ward",
            () -> new CreativeWardItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)),
            ItemSpec.generated("item/creative/creative_ward").tags(ECTags.CURIOS_NECKLACE));

    private ECCreative() {
    }

    /**
     * 三个无尽方块共用的属性：硬度 1（同原作的创造 MRU 源，什么镐都挖得快），要镐才掉落（BlockSpec 默认进 mineable/pickaxe、掉落自己），
     * 抗爆 {@value #BLAST_RESISTANCE}。
     */
    private static BlockBehaviour.Properties infiniteBlock(MapColor color) {
        return ECBlocks.machine(color, 1F, BLAST_RESISTANCE);
    }

    public static void init() {
    }
}
