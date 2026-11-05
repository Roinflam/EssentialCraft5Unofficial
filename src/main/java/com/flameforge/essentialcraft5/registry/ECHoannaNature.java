package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.DreadCactusBlock;
import com.flameforge.essentialcraft5.block.HoannaFlowerBlock;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TallGrassBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.IntRange;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.AlternativesEntry;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.minecraft.world.level.storage.loot.functions.ApplyExplosionDecay;
import net.minecraft.world.level.storage.loot.functions.LimitCount;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.predicates.MatchTool;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.minecraftforge.common.Tags;
import net.minecraftforge.registries.RegistryObject;

/**
 * 第六批 6a M1「群系与海纳尔植物」（计划 tools/plan/batch6a.md §5.1）：海纳尔的 6 个方块和魔力果实。
 * 群系、地物、biome_modifier 全是数据，由 {@code tools/gen/hoanna_biomes.py} 生成（改表重跑，别手改生成物）；地表规则在 {@code worldgen/hoanna/HoannaSurfaceRules}。
 * <p>
 * 新旧名（tools/id_map_*.json）：dreadDirt → dread_dirt、cacti → dread_cactus、flowerGreen → green_flower、tallGrass → hoanna_tall_grass、
 * fruit → magical_fruit_block、root → root；物品 fruit_Item → magical_fruit。方块全进创造栏「装饰」页（F5-3），果实进物品页。
 * <p>
 * 和原作不同的地方（原作 BlocksCore L93~99 {@code registerBlockSimple(…, 1, 1, 0)}：硬度 1、抗爆 1、不透光度 0）：
 * <ul>
 *   <li>恐惧泥土、魔力果实块、根系按普通方块不透光（原作第 5 个参数顺手填的 0，1.12 里光直接穿过，恐惧之地下面的洞穴漏天光、不刷怪；先例是 ECWorldGen 的矿石）；</li>
 *   <li>恐惧泥土进 {@code #minecraft:dirt}：原作恐惧之地配了枯灌木（deadBushPerChunk 2）却一株长不出来（恐惧泥土不是泥土）；
 *       副作用：树苗、花草能种在上面，末影人会搬它（受 mobGriefing 和 Forge 的 {@code EntityMobGriefingEvent} 管，同原版泥土）；</li>
 *   <li>绿花、草丛瞬间挖（原作硬度 1，徒手 1.5 秒），草丛不能一格格叠成柱（原作 canSustainBush 认草丛自己），用原版 {@link TallGrassBlock}：
 *       骨粉长成原版高草丛、剪刀剪下本体、徒手 1/8 掉 1 个小麦种子（原作 {@code ForgeHooks.getGrassSeed}，不吃时运）；</li>
 *   <li>恐惧仙人掌中毒节流、创造不中毒、两三格能叠（见 {@link DreadCactusBlock}）；</li>
 *   <li>根系不进 {@code #minecraft:logs}（原作注册成矿辞 log）：进了会让附近树叶不腐烂、能当燃料烧、能合成木板以外的东西，它只是墓穴的装饰。</li>
 * </ul>
 * 贴图照原作（{@code tools/asset_map.tsv}）：{@code block/firstworld/{dreaddirt, cactus_side/top/bottom, flower_green, tallgrass, magicalfruit, root}}、{@code item/fruit}。
 * 草丛的方块和物品按草地颜色染色（{@code client/HoannaNatureClient}，原作 BlockHoannaTallGrass L62~64）。
 */
public final class ECHoannaNature {

    // ------------------------------------------------------------------
    // 数值（掉落表是 datagen 按这些生成的；GameTest HoannaNatureTests 按它们核）
    // ------------------------------------------------------------------

    /** 草丛徒手掉小麦种子的概率（原作 nextInt(8) == 0） */
    public static final float TALL_GRASS_SEED_CHANCE = 0.125F;
    public static final int FRUIT_MIN = 3;
    public static final int FRUIT_MAX = 7;
    public static final int FRUIT_CAP = 9;
    public static final int FRUIT_NUTRITION = 8;
    public static final float FRUIT_SATURATION = 10F;

    // ------------------------------------------------------------------
    // 方块（原作 Material.GROUND / CACTUS / WOOD，声音 GROUND = 1.20 的 GRAVEL、PLANT = GRASS）
    // ------------------------------------------------------------------

    /** 恐惧之地的表层（地表规则 {@code HoannaSurfaceRules}）。原作 BlockEC(Material.GROUND)、SoundType.GROUND */
    public static final RegistryObject<Block> DREAD_DIRT = ECBlocks.register("dread_dirt",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.DIRT)
                    .strength(1F, ECBlocks.legacyResistance(1F, 1F, true)).sound(SoundType.GRAVEL)),
            BlockSpec.of(BlockSpec.cubeAll("block/firstworld/dreaddirt"))
                    .blockTags(BlockTags.MINEABLE_WITH_SHOVEL, BlockTags.DIRT).decoration());

    /** 海纳尔沙漠的仙人掌（原作 BlockDreadCactus）；贴图原作自己的 firstworld/cactus_* */
    public static final RegistryObject<DreadCactusBlock> DREAD_CACTUS = ECBlocks.register("dread_cactus",
            () -> new DreadCactusBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).randomTicks()
                    .strength(1F, ECBlocks.legacyResistance(1F, 1F, true)).sound(SoundType.GRASS)
                    .noOcclusion().pushReaction(PushReaction.DESTROY)),
            BlockSpec.of(BlockSpec.cactus("block/firstworld/cactus_side", "block/firstworld/cactus_top", "block/firstworld/cactus_bottom"))
                    .blockTags().decoration());

    /** 海纳尔绿花（原作 BlockHoannaFlower）；属性照原版蒲公英 */
    public static final RegistryObject<HoannaFlowerBlock> GREEN_FLOWER = ECBlocks.register("green_flower",
            () -> new HoannaFlowerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().instabreak()
                    .sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.DESTROY)),
            BlockSpec.of(BlockSpec.cross("block/firstworld/flower_green")).blockTags().decoration());

    /**
     * 海纳尔草丛（原作 BlockHoannaTallGrass）：原版 {@link TallGrassBlock}，属性照原版矮草。
     * 掉落：剪刀（{@code #forge:shears}，模组剪刀也认）→ 本体；否则 12.5% 掉 1 个小麦种子，不吃时运（原作每次 1/8、1 个）。
     */
    public static final RegistryObject<TallGrassBlock> HOANNA_TALL_GRASS = ECBlocks.register("hoanna_tall_grass",
            () -> new TallGrassBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).replaceable().noCollission().instabreak()
                    .sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XYZ).ignitedByLava().pushReaction(PushReaction.DESTROY)),
            BlockSpec.of(BlockSpec.tintedCross("block/firstworld/tallgrass"))
                    .blockTags(BlockTags.REPLACEABLE, BlockTags.REPLACEABLE_BY_TREES)
                    .loot((loot, block) -> loot.table(block, LootTable.lootTable().withPool(LootPool.lootPool()
                            .setRolls(ConstantValue.exactly(1F))
                            .add(AlternativesEntry.alternatives(
                                    LootItem.lootTableItem(block)
                                            .when(MatchTool.toolMatches(ItemPredicate.Builder.item().of(Tags.Items.SHEARS))),
                                    LootItem.lootTableItem(Items.WHEAT_SEEDS)
                                            .when(LootItemRandomChanceCondition.randomChance(TALL_GRASS_SEED_CHANCE))
                                            .apply(ApplyExplosionDecay.explosionDecay()))))))
                    .decoration());

    /** 魔力果实（原作 ItemFruit = ItemFood(8, 10F)）：海纳尔唯一的天然食物，一个补 8 点饥饿并把饱和度补满 */
    public static final RegistryObject<Item> MAGICAL_FRUIT = ECItems.register("magical_fruit",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(FRUIT_NUTRITION).saturationMod(FRUIT_SATURATION).build())),
            ItemSpec.generated("item/fruit"));

    /**
     * 魔力果实块（原作 BlockMagicalFruit，类似西瓜）：挖掉 3~7 个果实，时运每级 +0~1、最多 9（原作 quantityDroppedWithBonus = 3~7 + nextInt(1 + 时运)，封顶 9，
     * 就是原版 uniform_bonus_count ×1 + limit_count 9）；精准采集整块。原作 Material.CACTUS（推动时碎）。
     */
    public static final RegistryObject<Block> MAGICAL_FRUIT_BLOCK = ECBlocks.register("magical_fruit_block",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT)
                    .strength(1F, ECBlocks.legacyResistance(1F, 1F, true)).sound(SoundType.GRASS).pushReaction(PushReaction.DESTROY)),
            BlockSpec.of(BlockSpec.cubeAll("block/firstworld/magicalfruit"))
                    .blockTags(BlockTags.MINEABLE_WITH_AXE)
                    .loot((loot, block) -> loot.table(block, loot.silkTouchOr(block, LootItem.lootTableItem(MAGICAL_FRUIT.get())
                            .apply(SetItemCountFunction.setCount(UniformGenerator.between(FRUIT_MIN, FRUIT_MAX)))
                            .apply(ApplyBonusCount.addUniformBonusCount(Enchantments.BLOCK_FORTUNE, 1))
                            .apply(LimitCount.limitCount(IntRange.upperBound(FRUIT_CAP))))))
                    .decoration());

    /** 根系（原作 BlockEC(Material.WOOD)）：古代墓穴的装饰（结构拼件经 {@code HoannaStructureBlocks.root()} 按注册名取），生存里只能从墓穴挖 */
    public static final RegistryObject<Block> ROOT = ECBlocks.register("root",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS)
                    .strength(1F, ECBlocks.legacyResistance(1F, 1F, true)).sound(SoundType.WOOD).ignitedByLava()),
            BlockSpec.of(BlockSpec.cubeAll("block/firstworld/root")).blockTags(BlockTags.MINEABLE_WITH_AXE).decoration());

    // ------------------------------------------------------------------
    // 地物（JSON 由 tools/gen/hoanna_biomes.py 生成；这里只放键，给测试和以后的代码按键取）
    // ------------------------------------------------------------------

    public static final ResourceKey<ConfiguredFeature<?, ?>> HOANNA_TREES_CONFIGURED = configured("hoanna_trees");
    public static final ResourceKey<ConfiguredFeature<?, ?>> GREEN_FLOWER_PATCH_CONFIGURED = configured("green_flower_patch");
    public static final ResourceKey<ConfiguredFeature<?, ?>> HOANNA_GRASS_PATCH_CONFIGURED = configured("hoanna_grass_patch");
    public static final ResourceKey<ConfiguredFeature<?, ?>> DREAD_CACTUS_PATCH_CONFIGURED = configured("dread_cactus_patch");
    public static final ResourceKey<ConfiguredFeature<?, ?>> MAGICAL_FRUIT_PATCH_CONFIGURED = configured("magical_fruit_patch");

    public static final ResourceKey<PlacedFeature> HOANNA_TREES = placed("hoanna_trees");
    public static final ResourceKey<PlacedFeature> GREEN_FLOWER_PATCH = placed("green_flower_patch");
    public static final ResourceKey<PlacedFeature> HOANNA_GRASS_PATCH = placed("hoanna_grass_patch");
    public static final ResourceKey<PlacedFeature> DREAD_CACTUS_PATCH = placed("dread_cactus_patch");
    public static final ResourceKey<PlacedFeature> MAGICAL_FRUIT_PATCH = placed("magical_fruit_patch");
    /** 海纳尔版上古遗迹（D12 A：rarity_filter 3，普查后由 2 改 3，约每 32 区块一座），feature 仍是 {@code essentialcraft5:elder_mrucu}；主世界那份 {@code elder_mrucu} 带稀有度 16，不进海纳尔 */
    public static final ResourceKey<PlacedFeature> ELDER_MRUCU_HOANNA = placed("elder_mrucu_hoanna");

    private ECHoannaNature() {
    }

    private static ResourceKey<ConfiguredFeature<?, ?>> configured(String name) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE, EssentialCraft5.id(name));
    }

    private static ResourceKey<PlacedFeature> placed(String name) {
        return ResourceKey.create(Registries.PLACED_FEATURE, EssentialCraft5.id(name));
    }

    /** 由 ECRegistries.register() 调；方块、物品是静态字段，类加载时已登记，这里没有配置、指令、包要登记 */
    public static void init() {
    }
}
