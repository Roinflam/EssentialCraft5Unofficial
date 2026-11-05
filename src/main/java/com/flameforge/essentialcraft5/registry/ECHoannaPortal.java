package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.CorruptionEffects;
import com.flameforge.essentialcraft5.block.HoannaPortalBlock;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.datagen.ECBlockLoot;
import com.flameforge.essentialcraft5.hoanna.portal.HoannaPortalTracker;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.client.model.generators.ConfiguredModel;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * 第六批 6a M2「海纳尔之门」（计划 tools/plan/batch6a.md §5.2）：传送门方块与兴趣点、找门建门、传送、风之石板换门（{@code item/WindTabletItem}）、
 * 进门画面（{@code client/hoanna/HoannaPortalOverlay}）；配置 {@code general.hoanna}。
 * <p>
 * 新旧注册名（命名表 tools/id_map_blocks.json）：原作 {@code portal}（BlockHoannaPortal）→ {@code hoanna_portal}，<b>没有方块物品</b>（{@code .item(null)}，
 * 原作玩家也拿不到）；兴趣点 {@code essentialcraft5:hoanna_portal}（只登记 {@code stable=true} 的两个朝向，见 HoannaPortalBlock 的类注释）。
 * 代码分布：{@code block/HoannaPortalBlock}、{@code hoanna/portal/}（{@code HoannaPortalShape} 门框判定、{@code HoannaPortalTracker} 等待计时、
 * {@code HoannaPortalPlacer} 找门建门、{@code HoannaTeleport} / {@code HoannaTeleporter} 传送、{@code PortalOverlayCurve} 进门暗角曲线）。
 * 文字在 tools/lang/keys/echoannaportal.json；标签 tools/tags/echoannaportal.json（门进 {@code #minecraft:portals}）；
 * 进度 {@code guide/hoanna/{portal,enter}}、{@code main/enter_hoanna}；手册片段 tools/guide/fragments/hoanna/M2.*.json。
 * <p>
 * 兴趣点用 {@link RegisterEvent} 自己注册（共用的 ECRegistries 没有 POI_TYPES 的 DeferredRegister，同 ECHoannaStructures 的写法）；
 * Forge 的兴趣点注册表有回调，注册时自动把这些方块状态映射到兴趣点（GameData L129 / L504~），放下 / 拆掉门格时 {@code ServerLevel.onBlockStateChange} 自动增删。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ECHoannaPortal {

    /**
     * 原作 BlockHoannaPortal：硬度 −1、没有碰撞箱、亮度 11、玻璃声，照原版下界门；抗爆 0（同下界门，爆炸能把门炸掉，不掉东西）；
     * 不开随机刻（原作 updateTick 空实现：不刷僵尸猪灵）；{@code forceSolidOn} 挡住流水（见方块类注释）。
     * 模型照原版 {@code nether_portal_ns / _ew}（父模型），portal、particle 换原作的纯绿 32 帧 {@code block/portal}，半透明层。
     */
    public static final RegistryObject<HoannaPortalBlock> HOANNA_PORTAL = ECBlocks.register("hoanna_portal",
            () -> new HoannaPortalBlock(BlockBehaviour.Properties.of()
                    .noCollission()
                    .strength(-1F)
                    .sound(SoundType.GLASS)
                    .lightLevel(state -> 11)
                    .pushReaction(PushReaction.BLOCK)
                    .forceSolidOn()),
            BlockSpec.of((provider, block) -> {
                ModelFile ns = provider.models().withExistingParent("hoanna_portal_ns", provider.mcLoc("block/nether_portal_ns"))
                        .texture("portal", provider.modLoc("block/portal"))
                        .texture("particle", provider.modLoc("block/portal"))
                        .renderType("translucent");
                ModelFile ew = provider.models().withExistingParent("hoanna_portal_ew", provider.mcLoc("block/nether_portal_ew"))
                        .texture("portal", provider.modLoc("block/portal"))
                        .texture("particle", provider.modLoc("block/portal"))
                        .renderType("translucent");
                provider.getVariantBuilder(block).forAllStates(state -> ConfiguredModel.builder()
                        .modelFile(state.getValue(HoannaPortalBlock.AXIS) == Direction.Axis.Z ? ew : ns)
                        .build());
            }).loot(ECBlockLoot::none).blockTags().item(null));

    /** 兴趣点：找门用（HoannaPortalPlacer.find） */
    public static final ResourceKey<PoiType> PORTAL_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, EssentialCraft5.id("hoanna_portal"));
    public static final RegistryObject<PoiType> PORTAL_POI_TYPE = RegistryObject.create(PORTAL_POI.location(), Registries.POINT_OF_INTEREST_TYPE,
            EssentialCraft5.MODID);

    private ECHoannaPortal() {
    }

    /** 由 ECRegistries.register() 调（F6a 已接线）：登记配置 general.hoanna、服务端的门计时监听器 */
    public static void init() {
        ECConfig.general(Config::define);
        // 门关着时虚空材料拿不到，灵魂五芒星 2、3 档做不出来：矩阵类腐化效果跟着不抽、不发作（第七批 7-F，batch7 §3.1 第 3 条）
        CorruptionEffects.registerMatrixGate(Config::portalEnabled);
        // 写明事件类型（同 ECHoanna.init）：不靠从方法引用反推泛型
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TickEvent.PlayerTickEvent.class, HoannaPortalTracker::onPlayerTick);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayerEvent.PlayerLoggedOutEvent.class, HoannaPortalTracker::onLoggedOut);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayerEvent.PlayerChangedDimensionEvent.class,
                HoannaPortalTracker::onChangedDimension);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ServerStoppedEvent.class, HoannaPortalTracker::onServerStopped);
    }

    /** 兴趣点只认 stable=true 的门格（捕获期间放的 stable=false 不登记，被拒还原时不会报「never registered」） */
    public static Set<BlockState> poiStates() {
        return HOANNA_PORTAL.get().getStateDefinition().getPossibleStates().stream()
                .filter(state -> state.getValue(HoannaPortalBlock.STABLE))
                .collect(ImmutableSet.toImmutableSet());
    }

    @SubscribeEvent
    public static void registerHoannaPortalPoi(RegisterEvent event) {
        // 方块注册表的 RegisterEvent 先于兴趣点（按原版注册表顺序），这里 HOANNA_PORTAL 已经有值
        event.register(Registries.POINT_OF_INTEREST_TYPE, helper -> helper.register(PORTAL_POI.location(), new PoiType(poiStates(), 0, 1)));
    }

    /**
     * SERVER 配置 {@code general.hoanna}（batch6a §5.2）。服主关掉 {@code portal_enabled} 时，主世界这边的海纳尔之门不再传送、石板不再换门
     * （手册整章藏掉），已经换好的门留在原地；<b>海纳尔那边不受影响</b>：已经在海纳尔的人照样能从门回来、门坏了照样能拿石板补空框，
     * 免得关开关那一刻人在海纳尔的玩家被困住（判断统一走 {@link #portalEnabledIn}）。GameTest 不许用 ConfigValue.set：开关用 {@link #setEnabledForTest}。
     */
    public static final class Config {
        public static ForgeConfigSpec.BooleanValue portalEnabled;
        public static ForgeConfigSpec.IntValue portalWaitTicks;
        public static ForgeConfigSpec.IntValue portalCooldownTicks;
        public static ForgeConfigSpec.IntValue portalSearchRadius;
        public static ForgeConfigSpec.IntValue portalBuildAttempts;
        public static ForgeConfigSpec.BooleanValue portalPreload;
        @Nullable
        private static volatile Boolean enabledForTest;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.comment("The Gate to Hoanna (portal) and the Wind Tablet turning Nether portals into it.",
                    "海纳尔之门：传送门，以及风之石板把下界传送门换成海纳尔之门").push("hoanna");
            portalEnabled = builder.comment("Whether Gates to Hoanna work in the Overworld (off: nobody can travel from the Overworld and the Wind Tablet "
                                    + "cannot open gates; existing gates stay). Players already in Hoanna can still come back through gates when this is off, "
                                    + "and can still mend an empty frame there with the Wind Tablet.",
                            "海纳尔之门在主世界这边是否可用（关掉后从主世界过不去、风之石板不再换门；已经换好的门留在原地）。"
                                    + "关掉后，已经在海纳尔的人仍能从门回来，在海纳尔也仍能用风之石板补空门框")
                    .define("portal_enabled", true);
            portalWaitTicks = builder.comment("Ticks a player has to stand in a gate before travelling (creative: always 1).",
                            "站在门里多少刻才会过去（创造模式固定 1 刻），20 刻 = 1 秒")
                    .defineInRange("portal_wait_ticks", 80, 1, 400);
            portalCooldownTicks = builder.comment("Ticks after travelling before a gate works again; it does not count down while standing in a gate.",
                            "过门后多少刻内门不再响应（站在门里期间不减，得先走出来）")
                    .defineInRange("portal_cooldown_ticks", 300, 20, 6000);
            portalSearchRadius = builder.comment("How far (square radius, blocks) to look for an existing gate on the other side.",
                            "在另一边找现成的门找多远（正方形半径，格）")
                    .defineInRange("portal_search_radius", 128, 16, 128);
            portalBuildAttempts = builder.comment("Spots tried when building an arrival gate in Hoanna (the last one is a forced platform); "
                                    + "if claims deny all of them the trip is cancelled.",
                            "在海纳尔建落脚门时最多试几个位置（最后一个是强制平台）；都被领地拒绝时取消这次传送")
                    .defineInRange("portal_build_attempts", 16, 1, 32);
            portalPreload = builder.comment("Load the arrival area in the background while a player waits in a gate (like vanilla's portal ticket, 300 ticks).",
                            "玩家站进门开始等待时就在后台加载目的地的区块（同原版过门后的传送门票，300 刻自动过期）")
                    .define("portal_preload", true);
            builder.pop();
        }

        public static boolean portalEnabled() {
            Boolean override = enabledForTest;
            if (override != null) {
                return override;
            }
            return portalEnabled == null || ECConfig.get(portalEnabled);
        }

        /**
         * 在这个世界里海纳尔之门能不能用：开关开着，或者就在海纳尔（开关只挡从主世界进去、在主世界换门）。
         * 门计时、过门、客户端进门暗角、石板补空框都走这里；两端都能调（客户端传 ClientLevel，按维度判断）。
         */
        public static boolean portalEnabledIn(@Nullable Level level) {
            return portalEnabled() || ECHoanna.isHoanna(level);
        }

        public static int waitTicks() {
            return portalWaitTicks == null ? 80 : ECConfig.get(portalWaitTicks);
        }

        public static int cooldownTicks() {
            return portalCooldownTicks == null ? 300 : ECConfig.get(portalCooldownTicks);
        }

        public static int searchRadius() {
            return portalSearchRadius == null ? 128 : ECConfig.get(portalSearchRadius);
        }

        public static int buildAttempts() {
            return portalBuildAttempts == null ? 16 : ECConfig.get(portalBuildAttempts);
        }

        public static boolean preload() {
            return portalPreload == null || ECConfig.get(portalPreload);
        }

        /** 只给 GameTest：临时开 / 关海纳尔之门（null 恢复配置值）；只许在同一段同步代码里设、测、置回 null */
        public static void setEnabledForTest(@Nullable Boolean value) {
            enabledForTest = value;
        }
    }
}
