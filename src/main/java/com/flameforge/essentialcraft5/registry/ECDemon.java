package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.DemonicPentacleBlock;
import com.flameforge.essentialcraft5.block.MithrilineCrystalPillarBlock;
import com.flameforge.essentialcraft5.blockentity.DemonicPentacleBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.demon.DemonStanding;
import com.flameforge.essentialcraft5.entity.DemonEntity;
import com.flameforge.essentialcraft5.menu.DemonTradeMenu;
import com.flameforge.essentialcraft5.structure.StructurePattern;
import com.flameforge.essentialcraft5.structure.StructurePatterns;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 恶魔模块（第七批 M1，计划 tools/plan/batch7.md §4.1）：恶魔五芒星、恶魔与刷怪蛋、恶魔交易（数据包交易池 + 交易界面）、五个恶魔音效。
 * <p>
 * 新旧名（总表 tools/id_map_*.json；实体不在命名表里）：
 * <ul>
 *   <li>demonicPentacle（BlockDemonicPentacle / TileDemonicPentacle）→ demonic_pentacle（{@link DemonicPentacleBlock} / {@link DemonicPentacleBlockEntity}）</li>
 *   <li>实体 essentialcraft:entitydemon（EntityDemon）→ {@code essentialcraft5:demon}（{@link DemonEntity}）；entityEgg:7 → demon_spawn_egg（只放创造栏，点刷怪笼不改类型）</li>
 *   <li>原作 GuiDemon / ContainerDemon（guiID[1]）→ 菜单 demon_trade（{@link DemonTradeMenu}，打开时写恶魔的实体 id，不加网络包）</li>
 *   <li>音效 sound.mob.demon.* → {@code sound.demon.summon / say / depart / doom / trade}（原作 11 个 ogg，定义在 tools/sounds/ecdemon.json）</li>
 * </ul>
 * 数据：交易池 {@code data/essentialcraft5/demon_trades/default.json}（{@code demon/DemonTrades}）、配方 {@code recipes/demon/demonic_pentacle.json}
 * （原作矿辞笔误 {@code core_void} 改成虚空核心）、实体标签 {@code essentialcraft5:demon_immune}（清怪不碰，默认空）、进度 {@code guide/demons/*}、
 * {@code main/demon_trade}。文字 tools/lang/keys/ecdemon.json；客户端 client/DemonClient；测试 gametest/DemonTests。
 * 配置：服务端 {@code general.demon}（{@link Config}）。阿克罗尼特锭、被俘灵魂、三种水晶柱由框架先注册（{@link ECDemonic}、{@link ECCrystalPillars}）。
 *
 * <h2>五芒星结构（原作 TileDemonicPentacle.tier0Checker，cp = pos.down()；北向偏移，五芒星自己是原点）</h2>
 * 只有一档。下面一层 13 块虚空石、20 块淡青护板（颜色随意）；本层 4 块荧石、4 块米斯莱护板；16 根水晶柱的底座：
 * 米斯莱护板上 4 根任意档、淡青护板上 8 根淡青以上、荧石上 4 根<b>虚空以上</b>。原作认任何带 ESPE 能力、档位够的方块实体，这里照灵魂五芒星只认水晶柱底座。
 * 召唤时按 {@link #PILLAR_OFFSETS}（原作 coords）的顺序逐根抽 ESPE。
 */
public final class ECDemon {

    // ---------------- 恶魔五芒星 ----------------

    /**
     * 原作 registerBlockSimple(new BlockDemonicPentacle(), "demonicPentacle", 0, 0, 0)：Material.ROCK、MapColor.AIR，硬度 0、抗爆 0、不透光度 0，
     * 1/16 高、没有碰撞，但和原作一样挡液体（见下面 forceSolidOn）。和灵魂五芒星一样改成空手也掉自己（原作 Material.ROCK 硬度 0，一碰就碎、什么都不掉）。
     * 方块模型是空的（只有破坏粒子），转动的符文由 BER 画；物品图标是原作贴图缩到 32×32 的 {@code block/icons/demonicpentacle}
     * （原作拿 512×512 的贴图直接当物品层，1.20 的物品模型会按像素生成几万个面；缩图脚本 tools/gen/demonic_pentacle_icon.py）。
     */
    public static final RegistryObject<DemonicPentacleBlock> DEMONIC_PENTACLE = ECBlocks.register("demonic_pentacle",
            () -> new DemonicPentacleBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NONE)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .sound(SoundType.STONE)
                    .strength(0F, ECBlocks.legacyResistance(0F, 0F, true))
                    // forceSolidOn：原作 Material.ROCK 挡液体；1.20 没碰撞箱的方块默认会被水冲掉、被岩浆烧掉（岩浆烧掉时什么都不掉，
                    // 配方里那块虚空核心就没了），往这一格倒桶（发射器，或点旁边方块的侧面）也会直接把它打掉（canBeReplaced(液体) 对不是 isSolid 的方块放行）。
                    // 同风之符文（ECWind.WIND_RUNE）、原版蜘蛛网、告示牌的写法：只改 isSolid / blocksMotion，不加碰撞箱，照样能穿过去
                    .forceSolidOn()
                    .noOcclusion()
                    .noCollission()),
            BlockSpec.of((provider, block) -> {
                ModelFile empty = provider.models().getBuilder(provider.name(block))
                        .texture("particle", provider.modLoc("block/demonicpentacle"));
                provider.simpleBlock(block, empty);
                provider.itemModels().withExistingParent(provider.name(block), provider.mcLoc("item/generated"))
                        .texture("layer0", provider.modLoc("block/icons/demonicpentacle"));
            }));

    public static final RegistryObject<BlockEntityType<DemonicPentacleBlockEntity>> DEMONIC_PENTACLE_BE =
            ECBlockEntities.type("demonic_pentacle", DemonicPentacleBlockEntity::new, DEMONIC_PENTACLE);

    // ---------------- 恶魔 ----------------

    /**
     * 原作 registerEntity(EntityDemon.class, 32, 1, true)：追踪 32 格照原作；它站着不动，同步间隔用 3。
     * 体型 0.6 × 1.8（原作 EntityLiving 默认），不怕火；不实现 Enemy（特斯拉线圈、反应堆、本模组别的「只打敌对生物」的东西都不打它）。
     * MISC 类别：不占怪物刷怪上限，和平难度也不消失。
     */
    public static final RegistryObject<EntityType<DemonEntity>> DEMON = ECEntities.register("demon",
            EntityType.Builder.<DemonEntity>of(DemonEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F).fireImmune().clientTrackingRange(2).updateInterval(3));

    /** 蛋壳取恶魔皮肤的暗红，斑点取火焰的橙色（原作刷怪蛋是纯白的） */
    public static final int SPAWN_EGG_BACKGROUND = 0x8B060B;
    public static final int SPAWN_EGG_HIGHLIGHT = 0xF76501;
    public static final RegistryObject<ECEntities.SpawnEgg> DEMON_SPAWN_EGG =
            ECEntities.spawnEgg("demon_spawn_egg", DEMON, SPAWN_EGG_BACKGROUND, SPAWN_EGG_HIGHLIGHT);

    public static final RegistryObject<MenuType<DemonTradeMenu>> DEMON_TRADE_MENU = ECMenus.register("demon_trade", DemonTradeMenu::new);

    // ---------------- 音效（原作 SoundRegistry L21~25，播放类别 HOSTILE） ----------------

    /** 召唤成功（只在真召唤时放；原作 getAmbientSound 也拿它当环境音，这里不用，见 DemonEntity 类注释「声音」） */
    public static final RegistryObject<SoundEvent> SOUND_SUMMON = ECSounds.register("sound.demon.summon");
    /** 环境音（说话） */
    public static final RegistryObject<SoundEvent> SOUND_SAY = ECSounds.register("sound.demon.say");
    /** 离开（被打发走、结构坏了、死亡） */
    public static final RegistryObject<SoundEvent> SOUND_DEPART = ECSounds.register("sound.demon.depart");
    /** 成交 */
    public static final RegistryObject<SoundEvent> SOUND_DOOM = ECSounds.register("sound.demon.doom");
    /** 打开交易界面 */
    public static final RegistryObject<SoundEvent> SOUND_TRADE = ECSounds.register("sound.demon.trade");

    // ---------------- 结构（必须写在它引用的方块字段之后） ----------------

    public static final ResourceLocation STRUCTURE_ID = EssentialCraft5.id("demonic_pentacle");

    /** 16 根柱子底座（原作 coords 的顺序，召唤时照这个顺序抽 ESPE）：前 4 根任意档、中间 8 根淡青以上、最后 4 根虚空以上 */
    public static final List<BlockPos> PILLAR_OFFSETS = List.of(
            new BlockPos(2, 1, 2), new BlockPos(2, 1, -2), new BlockPos(-2, 1, 2), new BlockPos(-2, 1, -2),
            new BlockPos(3, 0, 2), new BlockPos(3, 0, -2), new BlockPos(-3, 0, 2), new BlockPos(-3, 0, -2),
            new BlockPos(2, 0, 3), new BlockPos(2, 0, -3), new BlockPos(-2, 0, 3), new BlockPos(-2, 0, -3),
            new BlockPos(3, 1, 0), new BlockPos(-3, 1, 0), new BlockPos(0, 1, 3), new BlockPos(0, 1, -3));
    /** 各段柱子的最低档位（下标 = PILLAR_OFFSETS 的分段：0~3、4~11、12~15） */
    private static final int[] PILLAR_MIN_TIER = {0, 1, 2};

    /** 下面一层 13 块虚空石 */
    public static final List<BlockPos> VOID_STONE_OFFSETS = List.of(
            new BlockPos(0, -1, 0), new BlockPos(2, -1, 0), new BlockPos(-2, -1, 0), new BlockPos(0, -1, -2), new BlockPos(0, -1, 2),
            new BlockPos(3, -1, 1), new BlockPos(3, -1, -1), new BlockPos(-3, -1, 1), new BlockPos(-3, -1, -1),
            new BlockPos(1, -1, 3), new BlockPos(-1, -1, 3), new BlockPos(1, -1, -3), new BlockPos(-1, -1, -3));
    /** 本层 4 块荧石（上面是虚空以上的柱子） */
    public static final List<BlockPos> GLOWSTONE_OFFSETS = List.of(
            new BlockPos(3, 0, 0), new BlockPos(-3, 0, 0), new BlockPos(0, 0, 3), new BlockPos(0, 0, -3));
    /** 本层 4 块米斯莱护板（原作 invertedBlock；上面是任意档的柱子） */
    public static final List<BlockPos> MITHRILINE_PLATING_OFFSETS = List.of(
            new BlockPos(2, 0, 2), new BlockPos(-2, 0, 2), new BlockPos(2, 0, -2), new BlockPos(-2, 0, -2));
    /** 下面一层 20 块淡青护板 */
    public static final List<BlockPos> PALE_PLATING_OFFSETS = List.of(
            new BlockPos(1, -1, 0), new BlockPos(-1, -1, 0), new BlockPos(0, -1, 1), new BlockPos(0, -1, -1),
            new BlockPos(2, -1, 1), new BlockPos(2, -1, -1), new BlockPos(-2, -1, 1), new BlockPos(-2, -1, -1),
            new BlockPos(1, -1, 2), new BlockPos(1, -1, -2), new BlockPos(-1, -1, 2), new BlockPos(-1, -1, -2),
            new BlockPos(3, -1, 2), new BlockPos(3, -1, -2), new BlockPos(-3, -1, 2), new BlockPos(-3, -1, -2),
            new BlockPos(2, -1, 3), new BlockPos(2, -1, -3), new BlockPos(-2, -1, 3), new BlockPos(-2, -1, -3));

    private static final String LEGEND = "structure.essentialcraft5.demonic_pentacle.";

    public static final StructurePattern.Rule PENTACLE_RULE = StructurePattern.Rule.block(DEMONIC_PENTACLE);
    public static final StructurePattern.Rule VOID_STONE_RULE = StructurePattern.Rule.block(ECVoid.VOID_STONE, LEGEND + "void_stone");
    public static final StructurePattern.Rule PALE_PLATING_RULE = StructurePattern.Rule.block(ECAlloys.PALE_PLATING, LEGEND + "pale_plating");
    public static final StructurePattern.Rule MITHRILINE_PLATING_RULE =
            StructurePattern.Rule.block(ECMithriline.MITHRILINE_PLATING, LEGEND + "mithriline_plating");
    public static final StructurePattern.Rule GLOWSTONE_RULE = StructurePattern.Rule.block(() -> Blocks.GLOWSTONE);
    /** 三段柱位：任意档、淡青以上、虚空以上（手册里分别画米斯莱柱、淡青柱、虚空柱） */
    public static final List<StructurePattern.Rule> PILLAR_RULES = List.of(
            MithrilineCrystalPillarBlock.pillarRule(PILLAR_MIN_TIER[0],
                    () -> ECMithriline.MITHRILINE_CRYSTAL_PILLAR.get().defaultBlockState(), LEGEND + "pillar"),
            MithrilineCrystalPillarBlock.pillarRule(PILLAR_MIN_TIER[1],
                    () -> ECPentacle.PALE_CRYSTAL_PILLAR.get().defaultBlockState(), LEGEND + "pale_pillar"),
            MithrilineCrystalPillarBlock.pillarRule(PILLAR_MIN_TIER[2],
                    () -> ECCrystalPillars.VOID_CRYSTAL_PILLAR.get().defaultBlockState(), LEGEND + "void_pillar"));

    /** 完整结构（手册的结构页按 id 画它；id 同 {@link #STRUCTURE_ID}，这里写字面值方便手册校验脚本找到登记处） */
    public static final StructurePattern STRUCTURE = StructurePatterns.register(EssentialCraft5.id("demonic_pentacle"), pattern());

    private ECDemon() {
    }

    /** 由 ECRegistries.register() 调用 */
    public static void init() {
        ECEntities.attributes(DEMON, DemonEntity::createAttributes);
        ECConfig.general(Config::define);
        // 第十三批 13a：契约之誉的迁移动作、指令
        DemonStanding.init();
    }

    /** 第 i 根柱子（PILLAR_OFFSETS 的下标）属于哪一段柱位规则 */
    public static StructurePattern.Rule pillarRule(int index) {
        return PILLAR_RULES.get(index < 4 ? 0 : index < 12 ? 1 : 2);
    }

    private static StructurePattern pattern() {
        StructurePattern.Builder builder = StructurePattern.builder().at(0, 0, 0, PENTACLE_RULE);
        put(builder, VOID_STONE_OFFSETS, VOID_STONE_RULE);
        put(builder, PALE_PLATING_OFFSETS, PALE_PLATING_RULE);
        put(builder, GLOWSTONE_OFFSETS, GLOWSTONE_RULE);
        put(builder, MITHRILINE_PLATING_OFFSETS, MITHRILINE_PLATING_RULE);
        for (int i = 0; i < PILLAR_OFFSETS.size(); i++) {
            BlockPos offset = PILLAR_OFFSETS.get(i);
            builder.at(offset.getX(), offset.getY(), offset.getZ(), pillarRule(i));
        }
        return builder.build();
    }

    private static void put(StructurePattern.Builder builder, List<BlockPos> offsets, StructurePattern.Rule rule) {
        for (BlockPos offset : offsets) {
            builder.at(offset.getX(), offset.getY(), offset.getZ(), rule);
        }
    }

    /**
     * 服务端配置 {@code general.demon}：召唤花费、报酬、清怪参数，默认值照原作（第八批重新平衡时调这里或交易池）。
     * GameTest 用 {@link #setTestOverrides}（不碰真配置），设、用、置回都在同一段同步代码里。
     */
    public static final class Config {

        public static ForgeConfigSpec.IntValue summonEspe;
        public static ForgeConfigSpec.IntValue rewardMin;
        public static ForgeConfigSpec.IntValue rewardMax;
        public static ForgeConfigSpec.IntValue clearRadius;
        public static ForgeConfigSpec.IntValue clearInterval;
        public static ForgeConfigSpec.DoubleValue explosionPower;

        @Nullable
        private static Integer testSummonEspe;
        @Nullable
        private static Integer testRewardMin;
        @Nullable
        private static Integer testRewardMax;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.comment("Demonic pentacle and demons.", "恶魔五芒星与恶魔").push("demon");
            summonEspe = builder.comment("ESPE drawn from the pentacle's 16 crystal pillars to summon a demon (raw ESPE, no pentacle discount).",
                            "召唤一只恶魔从五芒星的 16 根水晶柱里抽多少 ESPE（按柱子里的原始 ESPE 算，不打折）")
                    .defineInRange("summon_espe", 666, 0, 160_000);
            rewardMin = builder.comment("Fewest Ackronite Ingots a demon pays for a finished trade.", "成交一次最少给几个阿克罗尼特锭")
                    .defineInRange("reward_min", 3, 1, 64);
            rewardMax = builder.comment("Most Ackronite Ingots a demon pays for a finished trade.", "成交一次最多给几个阿克罗尼特锭")
                    .defineInRange("reward_max", 8, 1, 64);
            clearRadius = builder.comment("Reach (blocks) of a demon's monster clearing around itself; 0 = demons do not clear monsters.",
                            "恶魔清怪的范围（以它为中心往外几格）；0 = 不清怪")
                    .defineInRange("clear_radius", 12, 0, 16);
            clearInterval = builder.comment("Ticks between two clearing strikes of the same demon (one monster per strike).",
                            "同一只恶魔两次清怪之间隔多少 tick（每次打一只）")
                    .defineInRange("clear_interval", 20, 10, 1200);
            explosionPower = builder.comment("Power of the explosion at the struck monster's feet (2 = original; 0 = no explosion). It never breaks blocks and only hurts monsters a demon would strike.",
                            "清怪时在怪脚下爆炸的威力（原作 2；0 = 不爆炸）。不破坏方块，只伤恶魔本来就会打的那类怪")
                    .defineInRange("explosion_power", 2D, 0D, 8D);
            // 第十三批 13a：契约之誉 general.demon.standing（batch13.md §3.4）
            DemonStanding.Config.define(builder);
            builder.pop();
        }

        public static int summonEspe() {
            return testSummonEspe != null ? testSummonEspe : ECConfig.get(summonEspe);
        }

        /** 报酬下限（上下限填反时取两者中小的） */
        public static int rewardMin() {
            return Math.min(rawRewardMin(), rawRewardMax());
        }

        /** 报酬上限（上下限填反时取两者中大的） */
        public static int rewardMax() {
            return Math.max(rawRewardMin(), rawRewardMax());
        }

        private static int rawRewardMin() {
            return testRewardMin != null ? testRewardMin : ECConfig.get(rewardMin);
        }

        private static int rawRewardMax() {
            return testRewardMax != null ? testRewardMax : ECConfig.get(rewardMax);
        }

        public static int clearRadius() {
            return ECConfig.get(clearRadius);
        }

        public static int clearInterval() {
            return ECConfig.get(clearInterval);
        }

        public static float explosionPower() {
            return (float) ECConfig.get(explosionPower);
        }

        /**
         * GameTest 用：召唤花费、报酬上下限的覆盖值（null = 用配置）。清怪参数按恶魔逐只覆盖（{@link DemonEntity#setTestClearOverrides}），
         * 因为恶魔跨多刻活着，全局覆盖会影响同一批里别的测试。
         */
        public static void setTestOverrides(@Nullable Integer summon, @Nullable Integer minReward, @Nullable Integer maxReward) {
            testSummonEspe = summon;
            testRewardMin = minReward;
            testRewardMax = maxReward;
        }
    }
}
