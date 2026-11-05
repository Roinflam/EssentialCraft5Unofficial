package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.ECBalancerBlock;
import com.flameforge.essentialcraft5.block.MithrilineCrystalPillarBlock;
import com.flameforge.essentialcraft5.block.PlayerPentacleBlock;
import com.flameforge.essentialcraft5.block.TripleTallBlock;
import com.flameforge.essentialcraft5.blockentity.MithrilineCrystalPillarBlockEntity;
import com.flameforge.essentialcraft5.blockentity.PlayerPentacleBlockEntity;
import com.flameforge.essentialcraft5.menu.PlayerPentacleMenu;
import com.flameforge.essentialcraft5.structure.StructurePattern;
import com.flameforge.essentialcraft5.structure.StructurePatterns;
import net.minecraft.advancements.critereon.StatePropertiesPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
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
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 灵魂五芒星与收容模块（第三批，batch3.md §3.7）：灵魂五芒星（花 ESPE 治腐化效果）、淡青块、黯淡水晶 / 黯淡水晶粉、
 * 淡青水晶柱（1 档 ESPE 水晶柱）、富集仓稳定器（让仓里的 MRU 聚集体不伤人）。
 * <p>
 * 新旧注册名（总表见 tools/id_map_*.json）：
 * <ul>
 *   <li>playerPentacle（BlockPlayerPentacle / TilePlayerPentacle）→ player_pentacle</li>
 *   <li>blockPale（BlockEC）→ pale_block；genItem:47 / 46 → fading_crystal / fading_dust</li>
 *   <li>mithrilineCrystal:3（TYPE=pale）→ pale_crystal_pillar；ecBalancer（BlockMRUCUECBalancer）→ ec_balancer</li>
 * </ul>
 * 工作台配方在 data/essentialcraft5/recipes/pentacle/（淡青块的配方由 ECAlloys 预写在 alloys/pale_block.json，
 * 黯淡水晶 / 粉的米斯莱熔炉配方由 ECMithriline 预写在 mithriline/furnace/，注册了物品就生效）；
 * 标签在 tools/tags/ecpentacle.json（淡青柱进 essentialcraft5:espe_pillars、稳定器进富集仓外壳 essentialcraft5:mrucuec_casing）；
 * 界面、提示、手册文字在 tools/lang/keys/ecpentacle.json；手册章节 assets/essentialcraft5/guide/pentacle.json，
 * 步骤进度在 data/essentialcraft5/advancements/guide/pentacle/。客户端部分（五芒星界面与 BER、淡青柱的浮动水晶）见 client/PentacleClient。
 *
 * <h2>五芒星的结构（原作 TilePlayerPentacle 的 tier0Checker ~ tier3Checker，按北向偏移写，五芒星自己是原点）</h2>
 * 五档逐级累加：{@link #TIER_PATTERNS} 第 t 份是「0 ~ t 档的要求全部加起来」，档位 = 从 0 档起连续满足的最高一档（原作 checkStructureAndTier）。
 * <ul>
 *   <li>0 档：脚下一层 13 块米斯莱护板（颜色随意），同层 8 根任意档的水晶柱（底座）。</li>
 *   <li>1 档：再加同层四个斜角 4 块淡青块，淡青块上面各 1 根 1 档以上的水晶柱（淡青柱）。</li>
 *   <li>2 档：再加外圈四角两层虚空石 8 块，上面各 1 根 2 档以上的水晶柱（虚空柱）。</li>
 *   <li>3 档：再加脚下一层四个正方向隔一格 4 块恶魔护板，高 3 格处 4 根 3 档以上的水晶柱（恶魔柱）。</li>
 *   <li>4 档（第十三批 13e，原作没有，batch13.md §2.2 第二十二章）：再加外圈四个斜角 (±3, −1, ±3) 4 块虚空石（和护板同一层），
 *       上面 (±3, 0, ±3) 各 1 根 4 档的水晶柱（暗渎柱）。</li>
 * </ul>
 * <b>4 档的柱位一经发布就不能再改</b>（玩家已经照着搭了，改了整座降档）：放在 3 档外框（x、z 都在 ±2 以内）外一格的四个斜角，
 * 不出外框 2 格、四根对称，整座的占地从 5 × 5 变成 7 × 7、高度不变（最高仍是 3 档恶魔柱的顶，y = 5），手册的立体图画得下；
 * 柱子落在地面那一层（和 0 档柱同高），垫在下面的虚空石和米斯莱护板同层，不和前四档的任何一格重叠。
 * 2、3、4 档的方块是后来的批次注册的（虚空石第六批 6a ECVoid，虚空柱、恶魔柱、暗渎柱、恶魔护板第七批 ECCrystalPillars / ECDemonic）：
 * 这里按注册名匹配（{@link #laterBlock}），本类不引用那些模块；都注册了，五档就都能成形。
 * 3 档时打开界面发进度「灵魂净化」（main/player_pentacle），手册的 2、3、4 档步骤由 {@link PlayerPentacleBlockEntity#onMenuOpened} 打勾。
 * 4 档有效 ESPE 每点按 2 倍算（同 3 档），治疗能力同 3 档（{@code corruption/CorruptionGate#cureTier} 只算到 3 档）。
 * 水晶柱格子用框架的 {@link MithrilineCrystalPillarBlock#pillarRule}（这一格是 essentialcraft5:espe_pillars 里的柱子<b>底座</b>、档位够）。
 * 每档从哪些柱子抽 ESPE 见 {@link #PILLAR_OFFSETS}（原作 coords 数组，前 8 / 12 / 16 / 20 个；4 档再加 4 根，共 24 个）。
 */
public final class ECPentacle {

    // ---------------- 灵魂五芒星 ----------------

    /**
     * 原作 registerBlockSimple(new BlockPlayerPentacle(), "playerPentacle", 0, 0, 0)：Material.ROCK、MapColor.AIR，硬度 0、抗爆 0、不透光度 0，
     * 1/16 高的薄片，和原作一样挡液体（见下面 forceSolidOn）。原作 Material.ROCK 要镐才掉落，硬度 0 空手一碰就碎而且什么都不掉（一个五芒星要 5 块灵魂石），这里改成空手也掉。
     * 方块模型是空的（只有破坏粒子），转动的符文由 BER 画；物品图标用原作贴图缩到 32×32 的 block/icons/playerpentacle
     * （原作直接拿 512×512 的贴图当物品层，1.20 的物品模型会按像素生成几万个面）。
     */
    public static final RegistryObject<PlayerPentacleBlock> PLAYER_PENTACLE = ECBlocks.register("player_pentacle",
            () -> new PlayerPentacleBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NONE)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .sound(SoundType.STONE)
                    .strength(0F, ECBlocks.legacyResistance(0F, 0F, true))
                    // forceSolidOn：原作 Material.ROCK 挡液体。1/16 高的碰撞箱太薄，1.20 按形状算出来不是 isSolid（平均边长 0.69 < 0.73），
                    // 默认会被水冲掉、被岩浆烧掉（岩浆烧掉时什么都不掉，5 块灵魂石就没了），往这一格倒桶（发射器，或点旁边方块的侧面）也会直接把它打掉
                    // （canBeReplaced(液体) 对不是 isSolid 的方块放行）。同风之符文（ECWind.WIND_RUNE）的写法：只改 isSolid / blocksMotion，碰撞箱不变
                    .forceSolidOn()
                    .noOcclusion()),
            BlockSpec.of((provider, block) -> {
                ModelFile empty = provider.models().getBuilder(provider.name(block))
                        .texture("particle", provider.modLoc("block/playerpentacle"));
                provider.simpleBlock(block, empty);
                provider.itemModels().withExistingParent(provider.name(block), provider.mcLoc("item/generated"))
                        .texture("layer0", provider.modLoc("block/icons/playerpentacle"));
            }));

    public static final RegistryObject<BlockEntityType<PlayerPentacleBlockEntity>> PLAYER_PENTACLE_BE =
            ECBlockEntities.type("player_pentacle", PlayerPentacleBlockEntity::new, PLAYER_PENTACLE);

    public static final RegistryObject<MenuType<PlayerPentacleMenu>> PLAYER_PENTACLE_MENU =
            ECMenus.register("player_pentacle", PlayerPentacleMenu::new);

    // ---------------- 材料 ----------------

    /**
     * 原作 registerBlockSimple(new BlockEC(Material.ROCK, MapColor.LAPIS), "blockPale", 2, 100, 0).setSoundType(METAL)：
     * 先抗爆后硬度 → 抗爆 60；要镐。★ 原作不透光度 0（透光的整格方块），这里是正常的不透光方块（batch3 §3.7 修 3）。
     * 五芒星 1 档的结构件；第四批的淡青宝石也用它。配方 ISI/SES/ISI 已由 ECAlloys 预写（alloys/pale_block.json）。
     */
    public static final RegistryObject<Block> PALE_BLOCK = ECBlocks.register("pale_block",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.LAPIS)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .sound(SoundType.METAL)
                    .strength(2F, ECBlocks.legacyResistance(2F, 100F, true))
                    .requiresCorrectToolForDrops()),
            BlockSpec.of(BlockSpec.cubeAll("block/paleblock")));

    /** genItem:47（矿辞 gemFading）：米斯莱熔炉 1 米斯莱水晶 → 1 黯淡水晶，240 ESPE（ECMithriline 预写） */
    public static final RegistryObject<Item> FADING_CRYSTAL = ECItems.register("fading_crystal",
            () -> new Item(new Item.Properties()), ItemSpec.generated("item/misc/fadingcrystal"));

    /** genItem:46（矿辞 dustFading）：米斯莱熔炉 1 黯淡水晶 → 8 黯淡水晶粉，480 ESPE（ECMithriline 预写） */
    public static final RegistryObject<Item> FADING_DUST = ECItems.register("fading_dust",
            () -> new Item(new Item.Properties()), ItemSpec.generated("item/misc/fadingdust"));

    // ---------------- 淡青水晶柱 ----------------

    /**
     * 原作 mithrilineCrystal meta 3（CrystalType.PALE，MapColor.LAPIS）：和米斯莱柱同一个方块类（{@link MithrilineCrystalPillarBlock}），
     * 档位 1（ESPE tier 1、附魔加成 15），产能 / 上限和米斯莱柱共用一份配置（原作这几个值全类共用）。
     * 硬度 1、抗爆 1（→ 0.6），要镐，三格高、活塞推不动；只有底座掉落。浮着转的水晶由 BER 画（米斯莱柱的渲染器换淡青贴图，见 PentacleClient）。
     * 配方 IGI/ICI/IGI：黯淡水晶粉 ×6、黯淡水晶 ×2、米斯莱水晶柱。
     */
    public static final RegistryObject<MithrilineCrystalPillarBlock> PALE_CRYSTAL_PILLAR = ECBlocks.register("pale_crystal_pillar",
            () -> new MithrilineCrystalPillarBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.LAPIS)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .sound(SoundType.GLASS)
                    .strength(1F, ECBlocks.legacyResistance(1F, 1F, false))
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK),
                    // 类型字段在后面：lambda 里要写类名限定，否则是非法前向引用
                    MithrilineCrystalPillarBlock.Tier.PALE, () -> ECPentacle.PALE_CRYSTAL_PILLAR_BE.get()),
            BlockSpec.of((provider, block) -> {
                // 原作 blockstate：minecraft:block + 淡青护板的破坏粒子；浮动水晶用米斯莱柱的 OBJ，换一份 MTL（贴图 block/model/palecrystal）
                ModelFile empty = provider.models().getBuilder(provider.name(block))
                        .texture("particle", provider.modLoc("block/paleplatingblock"));
                provider.getVariantBuilder(block).forAllStates(state -> ConfiguredModel.builder().modelFile(empty).build());
                provider.models().getBuilder("pale_crystal_pillar_floating")
                        .parent(provider.models().getExistingFile(provider.mcLoc("block/block")))
                        .customLoader(ObjModelBuilder::begin)
                        .modelLocation(provider.modLoc("models/block/mithrilinecrystal.obj"))
                        .flipV(true)
                        .overrideMaterialLibrary(provider.modLoc("models/block/palecrystal.mtl"))
                        .end()
                        .texture("particle", provider.modLoc("block/model/palecrystal"));
                provider.itemModels().withExistingParent(provider.name(block), provider.mcLoc("item/generated"))
                        .texture("layer0", provider.modLoc("block/icons/palecrystal"));
            }).loot((loot, block) -> loot.table(block, LootTable.lootTable().withPool(LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1F))
                    .add(LootItem.lootTableItem(block).when(LootItemBlockStatePropertyCondition.hasBlockStateProperties(block)
                            .setProperties(StatePropertiesPredicate.Builder.properties()
                                    .hasProperty(TripleTallBlock.PART, TripleTallBlock.Part.BOTTOM))))
                    .when(ExplosionCondition.survivesExplosion())))));

    public static final RegistryObject<BlockEntityType<MithrilineCrystalPillarBlockEntity>> PALE_CRYSTAL_PILLAR_BE =
            ECBlockEntities.type("pale_crystal_pillar", MithrilineCrystalPillarBlockEntity::new, PALE_CRYSTAL_PILLAR);

    // ---------------- 富集仓稳定器 ----------------

    /**
     * 原作 BlockMRUCUECBalancer：Material.ROCK、MapColor.PURPLE，setHardness(3).setResistance(15)（先硬度后抗爆 → 抗爆 9），要镐，cube_all。
     * 富集仓外壳方块（在 essentialcraft5:mrucuec_casing 标签里），不 tick、没有方块实体：控制器扫外壳时数到它，就每 20 tick
     * 给仓里的聚集体续 40 tick 稳定期（框架 G10）。MRU 抗性 100（entity/MRUResistances 已预填）。
     * 原作 setLightOpacity(0)：和别的富集仓部件一样不照搬（整格不透光，只是仓里暗一些，见 EnrichmentPartBlock 注释）。
     */
    public static final RegistryObject<ECBalancerBlock> EC_BALANCER = ECBlocks.register("ec_balancer",
            () -> new ECBalancerBlock(ECBlocks.machine(MapColor.COLOR_PURPLE, 3F, ECBlocks.legacyResistance(3F, 15F, false))),
            BlockSpec.of(BlockSpec.cubeAll("block/mrucuecmrubalancer")));

    // ---------------- 结构（必须写在它引用的方块字段之后） ----------------

    /**
     * 每档新增的水晶柱位置（北向偏移，原作 TilePlayerPentacle.coords 按 8 / 4 / 4 / 4 分段，第五份是 13e 的 4 档）；抽 ESPE 按这个顺序（原作逐根抽）。
     * 4 档那四个位置发布后不能改（见类注释）。
     */
    public static final List<List<BlockPos>> PILLAR_OFFSETS = List.of(
            List.of(new BlockPos(2, 0, 1), new BlockPos(2, 0, -1), new BlockPos(-2, 0, 1), new BlockPos(-2, 0, -1),
                    new BlockPos(1, 0, 2), new BlockPos(-1, 0, 2), new BlockPos(1, 0, -2), new BlockPos(-1, 0, -2)),
            List.of(new BlockPos(1, 1, 1), new BlockPos(-1, 1, 1), new BlockPos(1, 1, -1), new BlockPos(-1, 1, -1)),
            List.of(new BlockPos(2, 2, 2), new BlockPos(-2, 2, 2), new BlockPos(2, 2, -2), new BlockPos(-2, 2, -2)),
            List.of(new BlockPos(2, 3, 0), new BlockPos(-2, 3, 0), new BlockPos(0, 3, 2), new BlockPos(0, 3, -2)),
            List.of(new BlockPos(3, 0, 3), new BlockPos(-3, 0, 3), new BlockPos(3, 0, -3), new BlockPos(-3, 0, -3)));

    /** 0 档的 13 块米斯莱护板（原作 tier0Checker，cp = pos.down()） */
    private static final List<BlockPos> PLATING_OFFSETS = List.of(
            new BlockPos(0, -1, 0), new BlockPos(1, -1, 0), new BlockPos(-1, -1, 0), new BlockPos(0, -1, 1), new BlockPos(0, -1, -1),
            new BlockPos(2, -1, -1), new BlockPos(2, -1, 1), new BlockPos(-2, -1, -1), new BlockPos(-2, -1, 1),
            new BlockPos(-1, -1, 2), new BlockPos(1, -1, 2), new BlockPos(-1, -1, -2), new BlockPos(1, -1, -2));
    /** 1 档的 4 块淡青块（原作 tier1Checker） */
    private static final List<BlockPos> PALE_OFFSETS = List.of(
            new BlockPos(1, 0, 1), new BlockPos(-1, 0, 1), new BlockPos(1, 0, -1), new BlockPos(-1, 0, -1));
    /** 2 档的 8 块虚空石（原作 tier2Checker，两层） */
    private static final List<BlockPos> VOID_STONE_OFFSETS = List.of(
            new BlockPos(2, 0, 2), new BlockPos(-2, 0, 2), new BlockPos(2, 0, -2), new BlockPos(-2, 0, -2),
            new BlockPos(2, 1, 2), new BlockPos(-2, 1, 2), new BlockPos(2, 1, -2), new BlockPos(-2, 1, -2));
    /** 3 档的 4 块恶魔护板（原作 tier3Checker） */
    private static final List<BlockPos> DEMONIC_OFFSETS = List.of(
            new BlockPos(2, -1, 0), new BlockPos(-2, -1, 0), new BlockPos(0, -1, 2), new BlockPos(0, -1, -2));
    /** 4 档的 4 块虚空石（13e）：垫在暗渎柱下面，和护板同一层 */
    private static final List<BlockPos> SHADE_BASE_OFFSETS = List.of(
            new BlockPos(3, -1, 3), new BlockPos(-3, -1, 3), new BlockPos(3, -1, -3), new BlockPos(-3, -1, -3));

    private static final String LEGEND = "structure.essentialcraft5.player_pentacle.";

    public static final StructurePattern.Rule PENTACLE_RULE = StructurePattern.Rule.block(PLAYER_PENTACLE);
    public static final StructurePattern.Rule PLATING_RULE = StructurePattern.Rule.block(ECMithriline.MITHRILINE_PLATING, LEGEND + "plating");
    public static final StructurePattern.Rule PALE_RULE = StructurePattern.Rule.block(PALE_BLOCK);
    public static final StructurePattern.Rule VOID_STONE_RULE = laterBlock("void_stone", LEGEND + "void_stone");
    public static final StructurePattern.Rule DEMONIC_RULE = laterBlock("demonic_plating", LEGEND + "demonic_plating");
    /** 各档新增的水晶柱格子：档位 ≥ 这一档的柱子底座 */
    public static final List<StructurePattern.Rule> PILLAR_RULES = List.of(
            MithrilineCrystalPillarBlock.pillarRule(0, () -> ECMithriline.MITHRILINE_CRYSTAL_PILLAR.get().defaultBlockState(), LEGEND + "pillar"),
            MithrilineCrystalPillarBlock.pillarRule(1, () -> PALE_CRYSTAL_PILLAR.get().defaultBlockState(), LEGEND + "pale_pillar"),
            MithrilineCrystalPillarBlock.pillarRule(2, () -> laterState("void_crystal_pillar"), LEGEND + "void_pillar"),
            MithrilineCrystalPillarBlock.pillarRule(3, () -> laterState("demonic_crystal_pillar"), LEGEND + "demonic_pillar"),
            MithrilineCrystalPillarBlock.pillarRule(4, () -> laterState("shade_crystal_pillar"), LEGEND + "shade_pillar"));

    /** 0 ~ 4 档的完整结构（逐级累加），手册的结构页按 id 画（4 档的 id 是 player_pentacle_tier4） */
    public static final List<StructurePattern> TIER_PATTERNS = List.of(
            StructurePatterns.register(EssentialCraft5.id("player_pentacle"), pattern(0)),
            StructurePatterns.register(EssentialCraft5.id("player_pentacle_tier1"), pattern(1)),
            StructurePatterns.register(EssentialCraft5.id("player_pentacle_tier2"), pattern(2)),
            StructurePatterns.register(EssentialCraft5.id("player_pentacle_tier3"), pattern(3)),
            StructurePatterns.register(EssentialCraft5.id("player_pentacle_tier4"), pattern(4)));

    /** 最高档：原作 3 档（恶魔），第十三批 13e 加 4 档（暗渎） */
    public static final int MAX_TIER = 4;

    private ECPentacle() {
    }

    /** 由 ECRegistries.register() 调用：类加载即把上面的条目登记进 DeferredRegister（本模块没有配置小节） */
    public static void init() {
    }

    /** 某一档要从哪些柱子抽 ESPE（0 ~ tier 档的柱子位置，北向偏移；tier &lt; 0 返回空表） */
    public static List<BlockPos> pillarOffsets(int tier) {
        List<BlockPos> out = new ArrayList<>();
        for (int t = 0; t <= Math.min(tier, MAX_TIER); t++) {
            out.addAll(PILLAR_OFFSETS.get(t));
        }
        return Collections.unmodifiableList(out);
    }

    // ---------------- 工具 ----------------

    private static StructurePattern pattern(int tier) {
        StructurePattern.Builder builder = StructurePattern.builder().at(0, 0, 0, PENTACLE_RULE);
        put(builder, PLATING_OFFSETS, PLATING_RULE);
        if (tier >= 1) {
            put(builder, PALE_OFFSETS, PALE_RULE);
        }
        if (tier >= 2) {
            put(builder, VOID_STONE_OFFSETS, VOID_STONE_RULE);
        }
        if (tier >= 3) {
            put(builder, DEMONIC_OFFSETS, DEMONIC_RULE);
        }
        if (tier >= 4) {
            put(builder, SHADE_BASE_OFFSETS, VOID_STONE_RULE);
        }
        for (int t = 0; t <= tier; t++) {
            put(builder, PILLAR_OFFSETS.get(t), PILLAR_RULES.get(t));
        }
        return builder.build();
    }

    private static void put(StructurePattern.Builder builder, List<BlockPos> offsets, StructurePattern.Rule rule) {
        for (BlockPos offset : offsets) {
            builder.at(offset.getX(), offset.getY(), offset.getZ(), rule);
        }
    }

    /**
     * 后来的批次注册的方块（虚空石、恶魔护板）：按注册名匹配，注册之前这一格永远判不过（整合包删了那些方块也一样）。
     * 用注册名而不是 RegistryObject：本模块不引用后来的模块。
     */
    private static StructurePattern.Rule laterBlock(String path, String legendKey) {
        ResourceLocation id = EssentialCraft5.id(path);
        return StructurePattern.Rule.predicate((level, pos, state) -> !state.isAir() && id.equals(ForgeRegistries.BLOCKS.getKey(state.getBlock())),
                () -> laterState(path), legendKey);
    }

    /** 以后才注册的方块的默认状态（手册画图用）；还没注册时画屏障 */
    private static BlockState laterState(String path) {
        Block block = ForgeRegistries.BLOCKS.getValue(EssentialCraft5.id(path));
        return block == null || block == Blocks.AIR ? Blocks.BARRIER.defaultBlockState() : block.defaultBlockState();
    }
}
