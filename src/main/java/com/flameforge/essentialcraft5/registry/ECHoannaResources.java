package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.block.hoanna.LeyVentBlock;
import com.flameforge.essentialcraft5.block.hoanna.RadiantClusterBlock;
import com.flameforge.essentialcraft5.block.hoanna.TideBudBlock;
import com.flameforge.essentialcraft5.block.hoanna.TitaniteOreBlock;
import com.flameforge.essentialcraft5.block.hoanna.UmbralClusterBlock;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.datagen.ECBlockLoot;
import com.flameforge.essentialcraft5.datagen.ECBlockStateProvider;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import com.flameforge.essentialcraft5.loot.FortuneScaledCount;
import com.flameforge.essentialcraft5.loot.condition.HoannaLootConditions;
import net.minecraft.advancements.critereon.EnchantmentPredicate;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.advancements.critereon.StatePropertiesPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.CopyBlockState;
import net.minecraft.world.level.storage.loot.predicates.AllOfCondition;
import net.minecraft.world.level.storage.loot.predicates.ExplosionCondition;
import net.minecraft.world.level.storage.loot.predicates.InvertedLootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.predicates.MatchTool;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.client.model.generators.ConfiguredModel;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 第十三批 13d-H1a「特产方块」的注册类（batch13.md §6.1、§6.2「天灾采集窗口」、§9.4；HS §2.1.2）。五种方块是预备先注册的空壳：
 * 注册名、方块类（{@code block/hoanna/}）、状态属性（喷口 {@code charge}、潮晶芽 {@code stage}、晶簇 {@code facing} / {@code waterlogged}、
 * 影晶簇 {@code ripe}）在预备定死，13d-H1b 的地物 JSON 按它们写，<b>之后不改</b>。
 * <ul>
 *   <li>{@link #LEY_VENT} 地脉喷口（混沌腐化之地 + 其余陆地稀疏）：随机刻充能、空手右键收地脉结晶、挖掉什么都不掉。</li>
 *   <li>{@link #TITANITE_ORE} 榍石矿（海纳尔沙漠）：榍石 + 闪耀榍石（日阵中露天 40%，否则 8%）；模型是原版砂岩 + 透明叠加层。</li>
 *   <li>{@link #RADIANT_CLUSTER} 辐晶簇（恐惧之地辐射热点）：辐晶 1~2，在海纳尔里挖的人剂量 +1 500 × max(易伤, 0.5)。</li>
 *   <li>{@link #TIDE_BUD} 潮晶芽（魔力腐化之地）：大潮夜长、第 3 阶右键收潮晶。</li>
 *   <li>{@link #UMBRAL_CLUSTER} 影晶簇（暗影腐化之地）：成熟时右键收影晶、退回残簇，残簇夜里 / 黑暗中长回。</li>
 * </ul>
 * 世界生成时的状态（13d-H1b 照这个放）：喷口 {@code charge = 2}；潮晶芽 {@code stage} 随机 0~3；两种晶簇 {@code facing} 朝贴附面的外侧；
 * 影晶簇 {@code ripe = true}。收到的结晶是 {@link ECHoannaMaterials} 的物品，榍石 / 闪耀榍石是 {@link ECVoid} 的。
 * 音效定义在 tools/sounds/echoannaresources.json（全指原版声音改音高），字幕在 tools/lang/keys/echoannaresources.json；
 * 客户端粒子在 {@code client/HoannaResourcesClient}。服务端配置 {@code general.hoanna_world}（{@link Config}）。
 */
public final class ECHoannaResources {

    /** 地脉喷口：硬度 3、抗爆 6、镐；亮度 = charge × 2；挖掉什么都不掉（精准采集也不掉，搬不走） */
    public static final RegistryObject<LeyVentBlock> LEY_VENT = ECBlocks.register("ley_vent",
            () -> new LeyVentBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).instrument(NoteBlockInstrument.BASEDRUM)
                    .strength(3F, 6F).requiresCorrectToolForDrops().randomTicks().lightLevel(LeyVentBlock::lightLevel).sound(SoundType.DEEPSLATE)),
            BlockSpec.of(ECHoannaResources::ventModel).loot(ECBlockLoot::none));

    /** 榍石矿：硬度 3、抗爆 3、要铁镐 */
    public static final RegistryObject<TitaniteOreBlock> TITANITE_ORE = ECBlocks.register("titanite_ore",
            () -> new TitaniteOreBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SAND).instrument(NoteBlockInstrument.BASEDRUM)
                    .strength(3F, 3F).requiresCorrectToolForDrops().sound(SoundType.STONE)),
            BlockSpec.of(ECHoannaResources::titaniteModel).loot(ECHoannaResources::titaniteLoot).addBlockTags(BlockTags.NEEDS_IRON_TOOL));

    /** 辐晶簇：硬度 1.5、亮度 5、镐；朝向 / 含水照紫水晶簇；活塞推不动（只有真玩家亲手挖才掉东西，见 {@link RadiantClusterBlock}） */
    public static final RegistryObject<RadiantClusterBlock> RADIANT_CLUSTER = ECBlocks.register("radiant_cluster",
            () -> new RadiantClusterBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GREEN).forceSolidOn().noOcclusion()
                    .strength(1.5F).lightLevel(state -> 5).sound(SoundType.AMETHYST_CLUSTER).pushReaction(PushReaction.BLOCK)),
            BlockSpec.of(ECHoannaResources::radiantModel).loot(ECHoannaResources::radiantLoot));

    /** 潮晶芽：硬度 0.5，stage 0~3 */
    public static final RegistryObject<TideBudBlock> TIDE_BUD = ECBlocks.register("tide_bud",
            () -> new TideBudBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).forceSolidOn().noOcclusion().randomTicks()
                    .strength(0.5F).sound(SoundType.SMALL_AMETHYST_BUD).pushReaction(PushReaction.DESTROY)),
            BlockSpec.of(ECHoannaResources::tideBudModel).loot(ECHoannaResources::tideBudLoot));

    /**
     * 影晶簇：硬度 2、亮度 2、镐；朝向 / 含水照紫水晶簇，ripe 成熟 / 残簇。亮度不分成熟 / 残簇（来回切换不引起光照重算）：
     * 黑暗天灾里走近了能看见一点微光，哪丛熟了再靠客户端的暗紫尘认；只给 2，两格外就是 0，不改周围刷怪。
     */
    public static final RegistryObject<UmbralClusterBlock> UMBRAL_CLUSTER = ECBlocks.register("umbral_cluster",
            () -> new UmbralClusterBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).forceSolidOn().noOcclusion().randomTicks()
                    .strength(2F).lightLevel(state -> 2).sound(SoundType.AMETHYST_CLUSTER).pushReaction(PushReaction.DESTROY)),
            BlockSpec.of(ECHoannaResources::umbralModel).loot(ECHoannaResources::umbralLoot));

    /** 空手右键喷口收结晶 */
    public static final RegistryObject<SoundEvent> VENT_RELEASE = ECSounds.register("sound.ley_vent.release");
    /** 收潮晶芽 */
    public static final RegistryObject<SoundEvent> TIDE_BUD_HARVEST = ECSounds.register("sound.tide_bud.harvest");
    /** 收影晶簇 */
    public static final RegistryObject<SoundEvent> UMBRAL_HARVEST = ECSounds.register("sound.umbral_cluster.harvest");
    /** 残簇长回成熟（很轻，只在旁边听得见） */
    public static final RegistryObject<SoundEvent> UMBRAL_REGROW = ECSounds.register("sound.umbral_cluster.regrow");

    private ECHoannaResources() {
    }

    /** 由 ECRegistries.register() 调（模组构造阶段）：类加载即登记静态字段里的条目；配置在这里登记 */
    public static void init() {
        ECConfig.general(Config::define);
    }

    // ================================================================== 群系判定（潮晶芽、影晶簇只在自己的群系里长）

    @Nullable
    private static volatile ResourceKey<Biome> testBiome;

    /** 这一格是不是 biome 群系。GameTest 的超平坦世界里没有海纳尔群系，测试用 {@link #setTestBiome} 指定所有格子都算哪个群系 */
    public static boolean inBiome(ServerLevel level, BlockPos pos, ResourceKey<Biome> biome) {
        ResourceKey<Biome> test = testBiome;
        if (test != null) {
            return test == biome;
        }
        return level.getBiome(pos).is(biome);
    }

    /** 测试用：之后所有格子都按 biome 算（null 还原）。覆盖是全局的，只在同一段同步代码里设、测、还原 */
    public static void setTestBiome(@Nullable ResourceKey<Biome> biome) {
        testBiome = biome;
    }

    // ================================================================== 模型（datagen）

    /** 喷口：侧面一张、顶面按有没有充能两张（charge ≥ 1 顶面换亮的那张，远远看得出哪个能收），底面同侧面 */
    private static void ventModel(ECBlockStateProvider provider, Block block) {
        String name = provider.name(block);
        ModelFile idle = provider.models().cubeBottomTop(name, provider.tex("block/hoanna/ley_vent_side"),
                provider.tex("block/hoanna/ley_vent_side"), provider.tex("block/hoanna/ley_vent_top"));
        ModelFile active = provider.models().cubeBottomTop(name + "_active", provider.tex("block/hoanna/ley_vent_side"),
                provider.tex("block/hoanna/ley_vent_side"), provider.tex("block/hoanna/ley_vent_top_active"));
        provider.getVariantBuilder(block).forAllStates(state -> ConfiguredModel.builder()
                .modelFile(state.getValue(LeyVentBlock.CHARGE) > 0 ? active : idle).build());
        provider.simpleBlockItem(block, active);
    }

    /** 榍石矿：原版砂岩三面贴图（模型里引用，不打包原版贴图）+ 本模组的透明叠加层，cutout_mipped */
    private static void titaniteModel(ECBlockStateProvider provider, Block block) {
        provider.simpleBlockWithItem(block, provider.models()
                .withExistingParent(provider.name(block), provider.modLoc("block/hoanna/cube_bottom_top_overlay"))
                .texture("side", provider.mcLoc("block/sandstone"))
                .texture("top", provider.mcLoc("block/sandstone_top"))
                .texture("bottom", provider.mcLoc("block/sandstone_bottom"))
                .texture("overlay", provider.tex("block/hoanna/titanite_ore_overlay"))
                .renderType("cutout_mipped"));
    }

    /** 辐晶簇：十字交叉，六向转法同原版紫水晶簇（{@code directionalBlock} 的 x / y 转角和原版方块状态一致） */
    private static void radiantModel(ECBlockStateProvider provider, Block block) {
        ModelFile model = provider.models().cross(provider.name(block), provider.tex("block/hoanna/radiant_cluster")).renderType("cutout");
        provider.directionalBlock(block, model);
        provider.flatBlockItem(block, "block/hoanna/radiant_cluster");
    }

    /** 影晶簇：成熟 / 残簇两个模型，六向同辐晶簇；物品图是成熟的 */
    private static void umbralModel(ECBlockStateProvider provider, Block block) {
        String name = provider.name(block);
        ModelFile ripe = provider.models().cross(name, provider.tex("block/hoanna/umbral_cluster")).renderType("cutout");
        ModelFile depleted = provider.models().cross(name + "_depleted", provider.tex("block/hoanna/umbral_cluster_depleted")).renderType("cutout");
        provider.directionalBlock(block, state -> state.getValue(UmbralClusterBlock.RIPE) ? ripe : depleted, 180);
        provider.flatBlockItem(block, "block/hoanna/umbral_cluster");
    }

    /** 潮晶芽：四阶四个十字模型（第 3 阶用 tide_bud，前三阶 tide_bud_stage0~2）；物品图是长满的 */
    private static void tideBudModel(ECBlockStateProvider provider, Block block) {
        String name = provider.name(block);
        ModelFile[] stages = new ModelFile[TideBudBlock.MAX_STAGE + 1];
        for (int stage = 0; stage <= TideBudBlock.MAX_STAGE; stage++) {
            boolean full = stage == TideBudBlock.MAX_STAGE;
            String texture = full ? "block/hoanna/tide_bud" : "block/hoanna/tide_bud_stage" + stage;
            stages[stage] = provider.models().cross(full ? name : name + "_stage" + stage, provider.tex(texture)).renderType("cutout");
        }
        provider.getVariantBuilder(block).forAllStates(state -> ConfiguredModel.builder()
                .modelFile(stages[state.getValue(TideBudBlock.STAGE)]).build());
        provider.flatBlockItem(block, "block/hoanna/tide_bud");
    }

    // ================================================================== 掉落（datagen）

    /** 工具带精准采集（同原版 BlockLootSubProvider.HAS_SILK_TOUCH，那个字段是 protected） */
    private static LootItemCondition.Builder silkTouch() {
        return MatchTool.toolMatches(ItemPredicate.Builder.item()
                .hasEnchantment(new EnchantmentPredicate(Enchantments.SILK_TOUCH, MinMaxBounds.Ints.atLeast(1))));
    }

    /**
     * 榍石矿：精准 → 本体；否则榍石 1（时运按 {@code fortune_scaled_count} 加：1 + [0, 时运 + 1)）+ 闪耀榍石，日阵中、头顶露天 0.40，否则 0.08。
     * 闪耀那条拆成条件互斥的两个池，不用 alternatives：alternatives 里第一项的 random_chance 没过会落到第二项，几率会叠成 0.4 + 0.6 × 0.08。
     */
    private static void titaniteLoot(ECBlockLoot loot, Block block) {
        LootItemCondition.Builder window = AllOfCondition.allOf(HoannaLootConditions.hoannaEvent(HoannaEvent.SUN_ARRAY),
                HoannaLootConditions.skyVisible());
        LootTable.Builder table = loot.silkTouchOr(block, LootItem.lootTableItem(ECVoid.TITANITE.get()).apply(FortuneScaledCount.count(1, 1)))
                .withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1F))
                        .when(InvertedLootItemCondition.invert(silkTouch()))
                        .when(window)
                        .when(ExplosionCondition.survivesExplosion())
                        .add(LootItem.lootTableItem(ECVoid.TWINKLING_TITANITE.get())
                                .when(LootItemRandomChanceCondition.randomChance(TitaniteOreBlock.TWINKLING_CHANCE_WINDOW))))
                .withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1F))
                        .when(InvertedLootItemCondition.invert(silkTouch()))
                        .when(InvertedLootItemCondition.invert(window))
                        .when(ExplosionCondition.survivesExplosion())
                        .add(LootItem.lootTableItem(ECVoid.TWINKLING_TITANITE.get())
                                .when(LootItemRandomChanceCondition.randomChance(TitaniteOreBlock.TWINKLING_CHANCE))));
        loot.table(block, table);
    }

    /** 辐晶簇：精准 → 本体；否则辐晶 1~2（时运按 {@code fortune_scaled_count} 加：1 + [0, 2 × (时运 + 1))），受爆炸衰减 */
    private static void radiantLoot(ECBlockLoot loot, Block block) {
        loot.table(block, loot.silkTouchOr(block,
                LootItem.lootTableItem(ECHoannaMaterials.RADIANT_CRYSTAL.get()).apply(FortuneScaledCount.count(1, 2))));
    }

    /** 潮晶芽：精准 → 芽本体（物品放下是 stage 0）；否则只有第 3 阶掉潮晶 1，其余阶段什么都不掉 */
    private static void tideBudLoot(ECBlockLoot loot, Block block) {
        loot.table(block, loot.silkTouchOr(block, LootItem.lootTableItem(ECHoannaMaterials.TIDE_CRYSTAL.get())
                .when(LootItemBlockStatePropertyCondition.hasBlockStateProperties(block)
                        .setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(TideBudBlock.STAGE, TideBudBlock.MAX_STAGE)))));
    }

    /**
     * 影晶簇：表里只有「精准 → 本体（照原状态带 ripe，成熟的移栽过去还是成熟的）」。不精准时成熟簇掉的影晶不写在表里，
     * 由 {@code UmbralClusterBlock#getDrops} 照右键收取同一条规则算（白天的几率读配置 {@code umbral.day_chance}，表里写不了配置值）；残簇什么都不掉。
     */
    private static void umbralLoot(ECBlockLoot loot, Block block) {
        loot.table(block, LootTable.lootTable().withPool(LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1F))
                .when(silkTouch())
                .when(ExplosionCondition.survivesExplosion())
                .add(LootItem.lootTableItem(block).apply(CopyBlockState.copyState(block).copy(UmbralClusterBlock.RIPE)))));
    }

    // ================================================================== 配置

    /**
     * 服务端配置 {@code general.hoanna_world}（HS §2.9）：
     * <ul>
     *   <li>{@code vent.charge_chance_day} 0.08 / {@code charge_chance_night} 0.16 / {@code charge_chance_calamity} 1.0：喷口每次随机刻充一格的几率
     *       （白天 / 夜里 13 000~23 000 刻 / 地震或地脉风暴中）。默认刻速 3 时一格方块平均 4 096 / 3 ≈ 1 365 刻挨一次随机刻，
     *       所以白天约 14 分钟充一格、夜里约 7 分钟、天灾中约 68 秒（3.4 分钟充满）。</li>
     *   <li>{@code vent.max_charge} 3（1~3）、{@code vent.crystals_per_charge} 1（0~16）：最多充几格、每格换几个地脉结晶。</li>
     *   <li>{@code radiant_mining_dose} 1 500：在海纳尔里挖掉一块辐晶簇，挖的人加的剂量（乘 max(易伤, 0.5)）；0 = 不加。</li>
     *   <li>{@code tide_bud.growth_chance} 0.5：条件都满足时每次随机刻长一阶的几率（一个大潮夜约 7 次随机刻，期望长 3.6 阶，基本能长满一轮）。</li>
     *   <li>{@code umbral.day_chance} 0.5：白天收成熟影晶簇时得 1 个影晶的几率（夜里或黑暗天灾中固定 1~2 个）。</li>
     *   <li>{@code umbral.regrow_chance} 0.14：残簇每次随机刻长回成熟的几率（只在暗影腐化之地、夜里或黑暗天灾中）。一夜 10 000 刻约 7.3 次随机刻，
     *       0.14 时平均 7 次长回，即收完以后大约过一夜（约 1 游戏日）就能再收。</li>
     * </ul>
     * 读值一律走这里的静态方法（配置没加载时用默认值）。GameTest 用 {@link #setTestOverride} 临时覆盖（不许用 ConfigValue.set），
     * 覆盖是全局的，只在同一段同步代码里设、测、{@link #clearTestOverrides()}。
     */
    public static final class Config {

        public static final double DEFAULT_CHARGE_CHANCE_DAY = 0.08D;
        public static final double DEFAULT_CHARGE_CHANCE_NIGHT = 0.16D;
        public static final double DEFAULT_CHARGE_CHANCE_CALAMITY = 1.0D;
        public static final int DEFAULT_MAX_CHARGE = LeyVentBlock.MAX_CHARGE;
        public static final int DEFAULT_CRYSTALS_PER_CHARGE = 1;
        public static final int MAX_CRYSTALS_PER_CHARGE = 16;
        public static final int DEFAULT_RADIANT_MINING_DOSE = 1_500;
        public static final double DEFAULT_TIDE_GROWTH_CHANCE = 0.5D;
        public static final double DEFAULT_UMBRAL_DAY_CHANCE = 0.5D;
        public static final double DEFAULT_UMBRAL_REGROW_CHANCE = 0.14D;

        /** 测试覆盖用的键（配置里的完整路径是 {@code general.hoanna_world.} 加上它） */
        public static final String CHARGE_CHANCE_DAY = "vent.charge_chance_day";
        public static final String CHARGE_CHANCE_NIGHT = "vent.charge_chance_night";
        public static final String CHARGE_CHANCE_CALAMITY = "vent.charge_chance_calamity";
        public static final String MAX_CHARGE = "vent.max_charge";
        public static final String CRYSTALS_PER_CHARGE = "vent.crystals_per_charge";
        public static final String RADIANT_MINING_DOSE = "radiant_mining_dose";
        public static final String TIDE_GROWTH_CHANCE = "tide_bud.growth_chance";
        public static final String UMBRAL_DAY_CHANCE = "umbral.day_chance";
        public static final String UMBRAL_REGROW_CHANCE = "umbral.regrow_chance";

        private static final Map<String, Number> TEST = new ConcurrentHashMap<>();

        @Nullable
        private static ForgeConfigSpec.DoubleValue chargeChanceDay;
        @Nullable
        private static ForgeConfigSpec.DoubleValue chargeChanceNight;
        @Nullable
        private static ForgeConfigSpec.DoubleValue chargeChanceCalamity;
        @Nullable
        private static ForgeConfigSpec.IntValue maxCharge;
        @Nullable
        private static ForgeConfigSpec.IntValue crystalsPerCharge;
        @Nullable
        private static ForgeConfigSpec.IntValue radiantMiningDose;
        @Nullable
        private static ForgeConfigSpec.DoubleValue tideGrowthChance;
        @Nullable
        private static ForgeConfigSpec.DoubleValue umbralDayChance;
        @Nullable
        private static ForgeConfigSpec.DoubleValue umbralRegrowChance;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.comment("Hoanna specialty blocks: ley vents, radiant clusters, tide buds and umbral clusters.",
                    "海纳尔的特产方块：地脉喷口、辐晶簇、潮晶芽、影晶簇").push("hoanna_world");

            builder.comment("Ley vents charge up on random ticks (only in Hoanna); an empty-handed right-click collects ley crystals.",
                    "地脉喷口靠随机刻充能（只在海纳尔），空手右键收地脉结晶").push("vent");
            chargeChanceDay = builder.comment("Chance per random tick to gain one charge during the day.",
                            "白天每次随机刻充一格的几率")
                    .defineInRange("charge_chance_day", DEFAULT_CHARGE_CHANCE_DAY, 0D, 1D);
            chargeChanceNight = builder.comment("Chance per random tick to gain one charge at night (time of day 13000 to 23000).",
                            "夜里（一天的 13000~23000 刻）每次随机刻充一格的几率")
                    .defineInRange("charge_chance_night", DEFAULT_CHARGE_CHANCE_NIGHT, 0D, 1D);
            chargeChanceCalamity = builder.comment("Chance per random tick to gain one charge during an earthquake or a ley storm.",
                            "地震或地脉风暴中每次随机刻充一格的几率")
                    .defineInRange("charge_chance_calamity", DEFAULT_CHARGE_CHANCE_CALAMITY, 0D, 1D);
            maxCharge = builder.comment("A vent stops charging at this many charges.", "喷口充到几格就不再充")
                    .defineInRange("max_charge", DEFAULT_MAX_CHARGE, 1, LeyVentBlock.MAX_CHARGE);
            crystalsPerCharge = builder.comment("Ley crystals given per charge when collected. 0 = vents give nothing.",
                            "收取时每格换几个地脉结晶；0 = 喷口什么都不给")
                    .defineInRange("crystals_per_charge", DEFAULT_CRYSTALS_PER_CHARGE, 0, MAX_CRYSTALS_PER_CHARGE);
            builder.pop();

            radiantMiningDose = builder.comment("Radiation dose added to a player who mines a radiant cluster in Hoanna, "
                                    + "multiplied by their radiation vulnerability but never by less than 0.5. 0 = no dose.",
                            "在海纳尔里挖掉一块辐晶簇时挖的人加的剂量，乘他的辐射易伤（易伤低于 0.5 按 0.5 算）；0 = 不加")
                    .defineInRange("radiant_mining_dose", DEFAULT_RADIANT_MINING_DOSE, 0, 1_000_000);

            builder.comment("Tide buds grow only in the Magical Corrupted Land, at night, under open sky, during a spring tide.",
                    "潮晶芽只在魔力腐化之地、大潮的夜里、头顶露天时长").push("tide_bud");
            tideGrowthChance = builder.comment("Chance per random tick to grow one stage when every condition is met. 0 = never grows.",
                            "条件都满足时每次随机刻长一阶的几率；0 = 不长")
                    .defineInRange("growth_chance", DEFAULT_TIDE_GROWTH_CHANCE, 0D, 1D);
            builder.pop();

            builder.comment("Ripe umbral clusters can be harvested with a right-click and grow back in the Shadow Corrupted Land.",
                    "成熟的影晶簇右键收，收完的残簇在暗影腐化之地长回").push("umbral");
            umbralDayChance = builder.comment("Chance to get one umbral crystal when harvesting during the day "
                                    + "(at night or during Darkness you always get 1 to 2).",
                            "白天收取时得 1 个影晶的几率（夜里或黑暗天灾中固定得 1~2 个）")
                    .defineInRange("day_chance", DEFAULT_UMBRAL_DAY_CHANCE, 0D, 1D);
            umbralRegrowChance = builder.comment("Chance per random tick for a harvested cluster to ripen again "
                                    + "(only in the Shadow Corrupted Land, at night or during Darkness). "
                                    + "The default takes about one night. 0 = never grows back.",
                            "残簇每次随机刻长回成熟的几率（只在暗影腐化之地、夜里或黑暗天灾中）；默认大约一夜长回；0 = 不再长回")
                    .defineInRange("regrow_chance", DEFAULT_UMBRAL_REGROW_CHANCE, 0D, 1D);
            builder.pop();

            builder.pop();
        }

        public static double chargeChanceDay() {
            return d(CHARGE_CHANCE_DAY, chargeChanceDay, DEFAULT_CHARGE_CHANCE_DAY);
        }

        public static double chargeChanceNight() {
            return d(CHARGE_CHANCE_NIGHT, chargeChanceNight, DEFAULT_CHARGE_CHANCE_NIGHT);
        }

        public static double chargeChanceCalamity() {
            return d(CHARGE_CHANCE_CALAMITY, chargeChanceCalamity, DEFAULT_CHARGE_CHANCE_CALAMITY);
        }

        /** 1~3 */
        public static int maxCharge() {
            return Math.max(1, Math.min(LeyVentBlock.MAX_CHARGE, i(MAX_CHARGE, maxCharge, DEFAULT_MAX_CHARGE)));
        }

        public static int crystalsPerCharge() {
            return Math.max(0, Math.min(MAX_CRYSTALS_PER_CHARGE, i(CRYSTALS_PER_CHARGE, crystalsPerCharge, DEFAULT_CRYSTALS_PER_CHARGE)));
        }

        public static int radiantMiningDose() {
            return Math.max(0, i(RADIANT_MINING_DOSE, radiantMiningDose, DEFAULT_RADIANT_MINING_DOSE));
        }

        public static double tideGrowthChance() {
            return d(TIDE_GROWTH_CHANCE, tideGrowthChance, DEFAULT_TIDE_GROWTH_CHANCE);
        }

        public static double umbralDayChance() {
            return d(UMBRAL_DAY_CHANCE, umbralDayChance, DEFAULT_UMBRAL_DAY_CHANCE);
        }

        public static double umbralRegrowChance() {
            return d(UMBRAL_REGROW_CHANCE, umbralRegrowChance, DEFAULT_UMBRAL_REGROW_CHANCE);
        }

        /** 测试用：覆盖一个键（上面的常量；null 还原这一个） */
        public static void setTestOverride(String key, @Nullable Number value) {
            if (value == null) {
                TEST.remove(key);
            } else {
                TEST.put(key, value);
            }
        }

        public static void clearTestOverrides() {
            TEST.clear();
        }

        private static double d(String key, @Nullable ForgeConfigSpec.DoubleValue value, double fallback) {
            Number test = TEST.get(key);
            if (test != null) {
                return test.doubleValue();
            }
            return value == null ? fallback : ECConfig.get(value);
        }

        private static int i(String key, @Nullable ForgeConfigSpec.IntValue value, int fallback) {
            Number test = TEST.get(key);
            if (test != null) {
                return test.intValue();
            }
            return value == null ? fallback : ECConfig.get(value);
        }
    }
}
