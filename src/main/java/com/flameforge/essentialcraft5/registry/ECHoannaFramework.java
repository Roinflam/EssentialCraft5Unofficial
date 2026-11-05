package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.block.hoanna.NestCoreBlock;
import com.flameforge.essentialcraft5.blockentity.NestCoreBlockEntity;
import com.flameforge.essentialcraft5.command.ECCommands;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.datagen.ECBlockLoot;
import com.flameforge.essentialcraft5.hoanna.nest.HoannaNestCommands;
import com.flameforge.essentialcraft5.worldgen.hoanna.placement.CellSpread;
import com.flameforge.essentialcraft5.worldgen.hoanna.placement.RadiationHotspot;
import com.flameforge.essentialcraft5.worldgen.hoanna.placement.SpreadExcludingPlacement;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.client.model.generators.ConfiguredModel;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

/**
 * 第十三批 13d-HF「海纳尔框架」的注册类（batch13.md §6.1、§6.3、§6.4、§9.4）：
 * <ul>
 *   <li>放置修饰类型 {@link #CELL_SPREAD}（{@code cell_spread}）、{@link #RADIATION_HOTSPOT}（{@code radiation_hotspot}），
 *       实现在 {@code worldgen/hoanna/placement/}；</li>
 *   <li>结构放置类型 {@link #SPREAD_EXCLUDING}（{@code spread_excluding}，多集排除，13e 用）；</li>
 *   <li>巢穴核心 {@link #NEST_CORE}（方块 {@code block/hoanna/NestCoreBlock}、方块实体 {@link #NEST_CORE_BE}），类型数据
 *       {@code hoanna/nest/HoannaNestTypes}（自己挂 {@code AddReloadListenerEvent}），指令 {@code /ec5 hoanna_nest}。</li>
 * </ul>
 * 结构预测 {@code worldgen/hoanna/StructureForecast} 和全服找地方的预算 {@code hoanna/HoannaSearchBudget} 是纯工具类，不用登记。
 * 魔力潮汐本身在 {@code hoanna/HoannaTide}（纯函数），这里只登记它的开关。战利品条件 {@code hoanna_event} 等五个在
 * {@code loot/condition/HoannaLootConditions}（自己用 RegisterEvent 登记）。
 * 文字进 tools/lang/keys/echoannaframework.json；客户端注册在 {@code client/HoannaFrameworkClient}（现在没有要登记的：巢穴核心是普通方块模型）。
 * 服务端配置 {@code general.hoanna_tide}、{@code general.hoanna_nests}（{@link Config}）。
 */
public final class ECHoannaFramework {

    public static final RegistryObject<PlacementModifierType<CellSpread>> CELL_SPREAD =
            ECRegistries.PLACEMENT_MODIFIER_TYPES.register("cell_spread", () -> () -> CellSpread.CODEC);
    public static final RegistryObject<PlacementModifierType<RadiationHotspot>> RADIATION_HOTSPOT =
            ECRegistries.PLACEMENT_MODIFIER_TYPES.register("radiation_hotspot", () -> () -> RadiationHotspot.CODEC);
    public static final RegistryObject<StructurePlacementType<SpreadExcludingPlacement>> SPREAD_EXCLUDING =
            ECRegistries.STRUCTURE_PLACEMENT_TYPES.register("spread_excluding", () -> () -> SpreadExcludingPlacement.CODEC);

    /** 巢穴核心：硬度 30、抗爆 1 200、挖掉什么都不掉；亮度随状态（等着 4、战斗中 7、休眠 0） */
    public static final RegistryObject<NestCoreBlock> NEST_CORE = ECBlocks.register("nest_core",
            () -> new NestCoreBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).instrument(NoteBlockInstrument.BASEDRUM)
                    .strength(30F, 1_200F).requiresCorrectToolForDrops().lightLevel(NestCoreBlock::lightLevel).sound(SoundType.DEEPSLATE)
                    .pushReaction(PushReaction.BLOCK)),
            BlockSpec.of((provider, block) -> {
                ModelFile armed = provider.models().cubeAll("nest_core_armed", provider.modLoc("block/hoanna/nest_core_armed"));
                ModelFile active = provider.models().cubeAll("nest_core_active", provider.modLoc("block/hoanna/nest_core_active"));
                ModelFile dormant = provider.models().cubeAll("nest_core_dormant", provider.modLoc("block/hoanna/nest_core_dormant"));
                provider.getVariantBuilder(block).forAllStates(state -> ConfiguredModel.builder()
                        .modelFile(switch (state.getValue(NestCoreBlock.PHASE)) {
                            case ARMED -> armed;
                            case ACTIVE -> active;
                            case DORMANT -> dormant;
                        }).build());
                provider.simpleBlockItem(block, armed);
            }).loot(ECBlockLoot::none));

    public static final RegistryObject<BlockEntityType<NestCoreBlockEntity>> NEST_CORE_BE =
            ECBlockEntities.type("nest_core", NestCoreBlockEntity::new, NEST_CORE);

    private ECHoannaFramework() {
    }

    /** 由 ECRegistries.register() 调（模组构造阶段）：类加载即登记静态字段里的条目；配置、指令在这里登记 */
    public static void init() {
        ECConfig.general(Config::define);
        ECCommands.addSubcommand(HoannaNestCommands::build);
    }

    /** 服务端配置 {@code general.hoanna_tide}（{@code enabled}）、{@code general.hoanna_nests}（回充、重置、每人附近上限） */
    public static final class Config {

        public static final boolean DEFAULT_TIDE_ENABLED = true;
        public static final int DEFAULT_RECHARGE_TICKS = 72_000;
        public static final int DEFAULT_RESET_TICKS = 6_000;
        public static final int DEFAULT_MAX_ACTIVE_PER_PLAYER = 2;

        private static ForgeConfigSpec.BooleanValue tideEnabled;
        private static ForgeConfigSpec.IntValue rechargeTicks;
        private static ForgeConfigSpec.IntValue resetTicks;
        private static ForgeConfigSpec.IntValue maxActivePerPlayer;
        @Nullable
        private static volatile Boolean testTideEnabled;
        @Nullable
        private static volatile Integer testRecharge;
        @Nullable
        private static volatile Integer testReset;
        @Nullable
        private static volatile Integer testMaxActive;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("hoanna_tide");
            tideEnabled = builder.comment("Mana tide in Hoanna: the tide level follows the moon phase (full moon 1.0, new moon 0) and drives tide bud growth,",
                            "ley storms, the MRU veil in the sky, local monster weights and collapse speed. false = the tide stays at 0.5: no spring tides",
                            "and no ley storms, tide buds grow every night at 3/8 of the chance instead (same average yield), and the veil, monster weights",
                            "and collapse speed stay at x1. Hoanna shares the overworld's day: skipping the night by sleeping in the overworld skips it there too.",
                            "海纳尔的魔力潮汐：潮位跟着月相走（满月 1.0、新月 0），影响潮晶芽生长、地脉风暴、天上的 MRU 光幕、本地怪权重、坍缩速度；",
                            "关掉时潮位恒为 0.5：没有大潮和地脉风暴；潮晶芽改为每个夜里都长、几率 ×3/8（平均产量不变）；光幕、本地怪权重、坍缩速度都按 ×1。",
                            "海纳尔和主世界共用昼夜：主世界有人睡觉跳过夜晚时，海纳尔的夜也一起跳过")
                    .define("enabled", DEFAULT_TIDE_ENABLED);
            builder.pop();
            builder.push("hoanna_nests");
            rechargeTicks = builder.comment("Ticks a nest stays dormant after its wave is cleared before it can be triggered again (72000 = 3 in-game days).",
                            "A nest type's own recharge_ticks overrides this.",
                            "巢穴清完以后休眠多久才能再触发（刻，72000 = 3 个游戏日）。巢穴类型 JSON 自己写了 recharge_ticks 的以那个为准")
                    .defineInRange("recharge_ticks", DEFAULT_RECHARGE_TICKS, 1, Integer.MAX_VALUE);
            resetTicks = builder.comment("Ticks a nest's wave may go with no survival / adventure player within its leash radius before the remaining",
                            "monsters are removed and the nest re-arms (only counted while the nest's chunk is loaded). A nest type's reset_ticks overrides this.",
                            "放出的这一波在拴绳半径内一直没有生存 / 冒险玩家多久就收回剩下的怪、重新等人（刻，只在核心所在区块加载时累计）。",
                            "巢穴类型 JSON 自己写了 reset_ticks 的以那个为准")
                    .defineInRange("reset_ticks", DEFAULT_RESET_TICKS, 1, Integer.MAX_VALUE);
            maxActivePerPlayer = builder.comment("A nest will not release its wave while any player in range already has this many fighting nests within 48 blocks.",
                            "附近有玩家身边 48 格内已经有这么多个正在战斗的巢穴时，新的巢穴先不放怪")
                    .defineInRange("max_active_per_player", DEFAULT_MAX_ACTIVE_PER_PLAYER, 1, 64);
            builder.pop();
        }

        /** {@code general.hoanna_tide.enabled}（配置还没加载时取默认值） */
        public static boolean tideEnabled() {
            Boolean test = testTideEnabled;
            if (test != null) {
                return test;
            }
            return tideEnabled == null ? DEFAULT_TIDE_ENABLED : ECConfig.get(tideEnabled);
        }

        /** 只给 GameTest：覆盖 {@link #tideEnabled()}（null 还原；不碰配置文件），用完在 finally 里还原 */
        public static void setTestTideEnabled(@Nullable Boolean value) {
            testTideEnabled = value;
        }

        /** {@code general.hoanna_nests.recharge_ticks} */
        public static int rechargeTicks() {
            Integer test = testRecharge;
            if (test != null) {
                return test;
            }
            return rechargeTicks == null ? DEFAULT_RECHARGE_TICKS : ECConfig.get(rechargeTicks);
        }

        /** {@code general.hoanna_nests.reset_ticks} */
        public static int resetTicks() {
            Integer test = testReset;
            if (test != null) {
                return test;
            }
            return resetTicks == null ? DEFAULT_RESET_TICKS : ECConfig.get(resetTicks);
        }

        /** {@code general.hoanna_nests.max_active_per_player} */
        public static int maxActivePerPlayer() {
            Integer test = testMaxActive;
            if (test != null) {
                return test;
            }
            return maxActivePerPlayer == null ? DEFAULT_MAX_ACTIVE_PER_PLAYER : ECConfig.get(maxActivePerPlayer);
        }

        /** 只给 GameTest：覆盖巢穴的三项配置（各自 null = 照配置；不碰配置文件），用完在 finally 里传三个 null 还原 */
        public static void setTestNestOverrides(@Nullable Integer recharge, @Nullable Integer reset, @Nullable Integer maxActive) {
            testRecharge = recharge;
            testReset = reset;
            testMaxActive = maxActive;
        }
    }
}
