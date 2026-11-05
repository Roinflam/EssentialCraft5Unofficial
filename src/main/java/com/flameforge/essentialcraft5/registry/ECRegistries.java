package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.item.WindTier;
import com.flameforge.essentialcraft5.matrix.MatrixSystems;
import com.flameforge.essentialcraft5.player.PlayerSystems;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 所有 DeferredRegister。具体内容按类型放在 EC* 类里（ECBlocks、ECItems……）。
 * <p>
 * 注册名规则（原作靠 metadata 区分子类型，1.13 起取消了，要拆成独立 id）：
 * 全小写下划线，从原作注册名转换（moonWell → moon_well）；原作是 meta 子类型的，用子类型名
 * （storage:0 → soul_shard，device:4 → creative_mru_storage）。新旧名对照写进各 EC* 类的注释。
 * 一旦发布就不能再改名，改名要用 MissingMappingsEvent 接旧名。
 */
public final class ECRegistries {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, EssentialCraft5.MODID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, EssentialCraft5.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, EssentialCraft5.MODID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, EssentialCraft5.MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, EssentialCraft5.MODID);
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(ForgeRegistries.RECIPE_TYPES, EssentialCraft5.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, EssentialCraft5.MODID);
    /** 第三批框架 G1：公共实体类型，登记入口见 {@link ECEntities}（ECMRUCU 的 mru_presence 仍用 RegisterEvent 自己注册） */
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, EssentialCraft5.MODID);
    /** 第三批框架 G2：公共药水效果，登记入口见 {@link ECEffects}（ECMRUCU 的 mru_corruption 仍用 RegisterEvent） */
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, EssentialCraft5.MODID);
    /** 第三批框架 G2：公共音效，登记入口见 {@link ECSounds}（ECMRUCU 的 sound.mrucu_noise 仍用 RegisterEvent） */
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, EssentialCraft5.MODID);
    /** 第十三批 13c：自定义粒子类型，登记入口见 {@link ECParticles} */
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, EssentialCraft5.MODID);
    /**
     * 第十三批 13d：地物（Java 写的 {@code Feature}，13d-H1b 的喷口、冰刺、残骸堆等）。数据里的 configured / placed feature 仍是静态 JSON。
     * 6a 以前唯一的 Java 地物 {@code elder_mrucu} 照旧在 ECMRUCU 里用 RegisterEvent 注册（注册名不变，不迁移）。
     */
    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(ForgeRegistries.FEATURES, EssentialCraft5.MODID);
    /** 第十三批 13d：放置修饰类型（13d-HF 的 {@code cell_spread}、{@code radiation_hotspot}） */
    public static final DeferredRegister<PlacementModifierType<?>> PLACEMENT_MODIFIER_TYPES =
            DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, EssentialCraft5.MODID);
    /** 第十三批 13d：结构放置类型（13d-HF 的多集排除 {@code spread_excluding}，13e 的宝库、前哨用） */
    public static final DeferredRegister<StructurePlacementType<?>> STRUCTURE_PLACEMENT_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PLACEMENT, EssentialCraft5.MODID);

    private ECRegistries() {
    }

    public static void register(IEventBus modBus) {
        // 先让各内容类完成静态初始化（把条目登记进上面的 DeferredRegister），再挂到总线上
        ECBlocks.init();
        ECItems.init();
        ECBlockEntities.init();
        ECMenus.init();
        ECCreativeTabs.init();
        ECRecipes.init();
        // 第三批框架（G1~G7）：公共实体 / 效果 / 音效的登记入口；Curios 配置（general.baubles）要在 ECConfig.register 之前登记；
        // 风之 Tier 要在模组构造阶段登记排序（风之装备模块没接线时 GameTest 也要能测）
        ECEntities.init();
        ECEffects.init();
        ECSounds.init();
        ECMachineSounds.init();
        ECParticles.init();
        CuriosCompat.init();
        WindTier.init();
        // 玩家系统（G9~G11）：登记 general.corruption / general.wind_affinity 配置并挂 /ec5 player … corruption，也要在 ECConfig.register 之前
        PlayerSystems.init();
        // 第十三批 13a（计划 batch13.md §9.1）：矩阵阶框架（general.matrix_rank）、同调框架（general.attunement），同样要在 ECConfig.register 之前
        MatrixSystems.init();
        ECAttunement.init();

        // 各内容模块（顺序 = 创造模式物品栏里的顺序）
        ECMaterials.init();
        ECWorldGen.init();
        ECBootstrap.init();
        // 第十三批 13a：矩阵淬炼台（第二章，创造栏里挨着灵魂石、矩阵换能器）
        ECMatrix.init();
        ECMRUCU.init();
        ECRadiatingChamber.init();
        ECMagicianTable.init();
        ECGenerators.init();
        // 第二批
        ECAlloys.init();
        ECEnrichment.init();
        ECCrystal.init();
        ECEssence.init();
        ECSolar.init();
        ECMithriline.init();
        ECDevices.init();
        // 附魔升级：增幅台（中期）、升华台（后期），创造栏里挨着魔力附魔台
        ECEnchantUpgrade.init();
        ECMagmatic.init();
        ECGear.init();
        // 第三批
        ECCharms.init();
        ECStaffs.init();
        ECWind.init();
        ECWindGear.init();
        ECWindMage.init();
        ECCorruption.init();
        ECPentacle.init();
        ECBaubles.init();
        // 第四批 4a（2026-09-28，计划 tools/plan/batch4.md §0.0：机器不自动挖、放方块）
        ECFarmDevices.init();
        ECSeparators.init();
        ECCrafter.init();
        ECRedstoneTools.init();
        ECLogistics.init();
        ECStorage.init();
        // 第四批 4b
        ECMirror.init();
        ECDigger.init();
        ECTesla.init();
        ECReactor.init();
        // 第五批（计划 tools/plan/batch5.md）：枪械共享核心的骨架（F5-5），四把枪由 ECGuns 模块加
        ECGuns.init();
        ECWeaponMaker.init();
        ECLeyProjector.init();
        // 第九批（计划 tools/plan/batch9.md）：地脉凝聚器，排在投影仪后面（创造栏里挨着投影仪）
        ECLeyCondenser.init();
        // 第十二批（计划 batch12.md §5.1）：源质残卷
        ECScrolls.init();
        // 第十二批（计划 batch12.md §4.2、§4.8）：魔力栽培器、元素分解器
        ECCultivator.init();
        ECDecomposer.init();
        // 第十二批（计划 batch12.md §4.3、§4.5）：地脉探测仪、归途罗盘
        ECLeyDetector.init();
        ECHomewardCompass.init();
        // 第十二批（计划 batch12.md §4.4）：源质镐 / 源质斧
        ECEssentialTools.init();
        // 第十二批（计划 batch12.md §4.6）：矩阵调律器
        ECMatrixTuner.init();
        ECDecorations.init();
        // 第六批 6a「海纳尔」（计划 tools/plan/batch6a.md）：框架 ECHoanna（维度键、天灾同步包、sound.deep_noise）在前，
        // 结构基类与拼件类型 ECHoannaStructures（框架 F6b），其余是七个模块的注册类（F6a 先建空壳，模块只填自己的类）
        ECHoanna.init();
        ECHoannaNature.init();
        ECHoannaPortal.init();
        ECHoannaSky.init();
        ECHoannaHazards.init();
        ECHoannaStructures.init();
        ECVoid.init();
        // 第六批 6b「虚空机器」（计划 tools/plan/batch6b.md）：三个新模块的注册类先建空壳，模块只填自己的类；
        // 虚空箱、高级过滤器加在 ECStorage / ECLogistics，虚空岩浆熔炉加在 ECMagmatic；ECCreative 是只放创造栏的道具
        ECBuilder.init();
        ECVoidMachines.init();
        ECUltraGenerators.init();
        ECCreative.init();
        // 第七批「虚空与恶魔」（计划 tools/plan/batch7.md）：ECDemonic、ECCrystalPillars 是几个模块共用的名字（恶魔材料、被俘灵魂、
        // 灵魂刻刀、恶魔护板；三种水晶柱），后面三个是恶魔五芒星与交易、余烬锻炉与余烬元素剑、虚空套装
        ECDemonic.init();
        ECCrystalPillars.init();
        ECDemon.init();
        ECEmberForge.init();
        ECVoidArmor.init();
        // 第八批「全息与计算机」（计划 tools/plan/batch8.md）：全息投影台与 Boss、电脑一系、轨道打击遥控器与二分枪三个模块；
        // 框架先在里面注册几个模块共用的名字（全息唱片与 Boss 音效、富神经元护板与四种数学核心、两个技能实体与两件武器的空壳）
        ECHologram.init();
        ECComputer.init();
        ECHoloWeapons.init();
        // 第十批 10b（计划 tools/plan/batch10.md §5.8.2）：全息诱饵投影仪与诱饵实体，排在两件全息武器后面（创造栏里挨着它们）
        ECHoloDecoy.init();
        // 第十批 10c-M4（计划 tools/plan/batch10.md §5.13）：力场穹顶（宅男套装一章的可选步骤），排在全息诱饵后面
        ECForceDome.init();
        // 第十批 10c（计划 tools/plan/batch10.md §4.3）：宅男套装的模块平台（空白模块、装模块的配方、general.computer_modules）；
        // 六块模块的注册类接在它后面
        ECModules.init();
        // 第十批 10c-M2（计划 tools/plan/batch10.md §5.11）：三块生存模块（回溯、矩阵过滤、矩阵分流），配置挂在 general.computer_modules 下
        ECSurvivalModules.init();
        // 第十批 10c-M3（计划 tools/plan/batch10.md §5.12）：三块战斗模块（累加器、削减、相位锚），配置挂在 general.computer_modules 下
        ECCombatModules.init();
        // 第十二批（计划 batch12.md §4.7）：四块生活模块（吸附、攀行、稳手、隐匿），配置挂在 general.computer_modules 下
        ECUtilityModules.init();
        // 2026-10-13（规划 tools/plan/mru_converter.md）：MRU 转换器四档，本模组唯一的联动（MRU → Forge 能量 / 植物魔法魔力）；无尽档在 ECCreative
        ECConverters.init();
        // 第十三批 13c（计划 batch13.md §9.3）：新怪物。生成与掉落框架（Fa，配置 general.mobs 这一层）、首领与渲染框架（Fb）在前，
        // 怪物材料（上古晶核、矩阵结晶）其次，再是四路怪物：元素游灵 / 矩阵游魂 / 风暴术士、聚能魔像、炼狱小鬼 / 虚空潜行者、矩阵之影
        ECMobFramework.init();
        ECBossFramework.init();
        ECMobDrops.init();
        ECWildMobs.init();
        ECMRUGolem.init();
        ECNetherEndMobs.init();
        ECMatrixShade.init();
        // 第十三批 13d（计划 batch13.md §6、§9.4）：海纳尔世界。共用材料（四种结晶、霜髓；异变残质 / 之心在 ECMobDrops）在前，
        // 框架（潮汐配置、放置修饰、巢穴核心）其次，再是特产方块、地物与中景观、地脉风暴、勘测图与渊盐、三组本地怪
        ECHoannaMaterials.init();
        ECHoannaFramework.init();
        ECHoannaResources.init();
        ECHoannaFeatures.init();
        ECHoannaStorm.init();
        ECHoannaSurvey.init();
        ECHoannaNativesA.init();
        ECHoannaNativesB.init();
        ECHoannaNativesC.init();
        // 第十三批 13e（计划 batch13.md §9.5）：海纳尔结构与终章。暗影线的材料（暗影核心、恶魔之誓）在前（天灾信标的配方、八 / 九阶要它们），
        // 天灾之心（信标、残核）、废城守望者其次，再是三座结构里的方块（结构类型和拼件在 ECHoannaStructures.init() 里）
        ECShade.init();
        ECCalamityHeart.init();
        ECTownSentry.init();
        ECSealedVault.init();
        ECSunkenDistrict.init();
        ECInquisitoriumOutpost.init();
        ECGuide.init();

        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        MENU_TYPES.register(modBus);
        CREATIVE_TABS.register(modBus);
        RECIPE_TYPES.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
        ENTITY_TYPES.register(modBus);
        MOB_EFFECTS.register(modBus);
        SOUND_EVENTS.register(modBus);
        PARTICLE_TYPES.register(modBus);
        FEATURES.register(modBus);
        PLACEMENT_MODIFIER_TYPES.register(modBus);
        STRUCTURE_PLACEMENT_TYPES.register(modBus);
    }
}
