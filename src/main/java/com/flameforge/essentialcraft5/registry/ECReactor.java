package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.ReactorBlock;
import com.flameforge.essentialcraft5.block.ReactorSupportBlock;
import com.flameforge.essentialcraft5.blockentity.ReactorBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.structure.StructurePattern;
import com.flameforge.essentialcraft5.structure.StructurePatterns;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.client.model.generators.MultiPartBlockStateBuilder;
import net.minecraftforge.registries.RegistryObject;

/**
 * MRU 特斯拉反应堆模块（第四批 4b，batch4.md §3.12）：反应堆核心、反应堆支架。
 * <p>
 * 新旧注册名：reactor → {@code reactor}（原作 BlockMRUReactor + TileMRUReactor）；reactorSupport → {@code reactor_support}（原作 BlockReactorSupport，没有方块实体）。
 * 原作 {@code BlocksCore} L123~125：核心 {@code registerBlockSimple(…, 1, 10, 0)}（先抗爆后硬度 → 抗爆 6、不透光）；
 * 支架 {@code setHardness(1).setResistance(10)}（先硬度 → 同样抗爆 6）、不透光；两个都是 Material.ROCK、MapColor.PURPLE，要镐。
 * <p>
 * 玩家视角：5×3 魔力护板地基上放核心，两侧各立一根两格高的支架，其余空出来（核心正上方也要空）。成形后不要燃料，每刻产 50 MRU，
 * 平衡值在 0~2 之间来回摆；3 格内的<b>敌对生物</b>每 10 刻挨 5 点 MRU 电弧（原作电所有生物），被电死的不掉东西（捡走的玩家装备照掉）。
 * 规则、数值和与原作的不同见 {@link ReactorBlockEntity} 的类注释；服务端配置 machines.reactor。
 * <p>
 * 结构 {@link #REACTOR_STRUCTURE}（登记为 {@code essentialcraft5:reactor}，手册结构页按 id 画）；成形时给 16 格内玩家触发进度条件
 * {@code essentialcraft5:structure_formed}（手册「特斯拉」章的完成条件）。
 * 工作台配方在 data/essentialcraft5/recipes/reactor/（MRU 磁体是 ECTesla 的，按注册名引用、带 item_exists 条件），
 * 文字在 tools/lang/keys/ecreactor.json，手册是「特斯拉」章（归 ECTesla）的部件 tools/guide/parts/ECReactor.json。
 * 客户端部分（浮球 + 电弧渲染器）见 client/ReactorClient。
 */
public final class ECReactor {

    /** 结构 id（手册结构页、进度条件 structure_formed 用）；登记处写的是同一个字面量（手册离线检查按字面量找登记），GameTest 核对两处一致 */
    public static final ResourceLocation REACTOR_STRUCTURE_ID = EssentialCraft5.id("reactor");

    // ---------------- 反应堆核心 ----------------

    /**
     * 方块模型是原作的 OBJ 底座 mrureactor_btm（贴图 block/model/flowerburner，原作 blockstate 带 flip-v），两个 FORMED 状态同一个模型；
     * 浮球和电弧由方块实体渲染器画，BER 用的 OBJ 浮球 block/reactor_sphere 也在这里生成（ReactorClient 里登记成额外模型）。
     */
    public static final RegistryObject<ReactorBlock> REACTOR = ECBlocks.register("reactor",
            () -> new ReactorBlock(ECBlocks.machine(MapColor.COLOR_PURPLE, 1F, ECBlocks.legacyResistance(1F, 10F, true)).noOcclusion()),
            BlockSpec.of((provider, block) -> {
                provider.obj(block, "mrureactor_btm", "block/model/flowerburner");
                provider.objModel("reactor_sphere", "sphere", "block/model/sphere");
            }));

    public static final RegistryObject<BlockEntityType<ReactorBlockEntity>> REACTOR_BE =
            ECBlockEntities.type("reactor", ReactorBlockEntity::new, REACTOR);

    // ---------------- 反应堆支架 ----------------

    /**
     * 原作 blockstate reactorsupport.json（multipart）：LAYER 底 / 中 / 顶三种外观（顶 = 整块魔力护板贴图），
     * 朝某一面相邻的是已成形的核心时加一组电栏（北 0°、东 90°、南 180°、西 270°，uvlock）。物品模型用「中」。
     */
    public static final RegistryObject<ReactorSupportBlock> REACTOR_SUPPORT = ECBlocks.register("reactor_support",
            () -> new ReactorSupportBlock(ECBlocks.machine(MapColor.COLOR_PURPLE, 1F, ECBlocks.legacyResistance(1F, 10F, false)).noOcclusion()),
            BlockSpec.of((provider, block) -> {
                ModelFile bottom = provider.models().getExistingFile(provider.modLoc("block/reactor_support_bottom"));
                ModelFile middle = provider.models().getExistingFile(provider.modLoc("block/reactor_support_middle"));
                ModelFile top = provider.models().cubeAll("reactor_support_top", provider.modLoc("block/magicplatingblock"));
                ModelFile bars = provider.models().getExistingFile(provider.modLoc("block/reactor_support_bars"));
                MultiPartBlockStateBuilder builder = provider.getMultipartBuilder(block);
                builder.part().modelFile(bottom).addModel().condition(ReactorSupportBlock.LAYER, ReactorSupportBlock.Layer.BOTTOM).end();
                builder.part().modelFile(middle).addModel().condition(ReactorSupportBlock.LAYER, ReactorSupportBlock.Layer.MIDDLE).end();
                builder.part().modelFile(top).addModel().condition(ReactorSupportBlock.LAYER, ReactorSupportBlock.Layer.TOP).end();
                builder.part().modelFile(bars).addModel().condition(BlockStateProperties.NORTH, Boolean.TRUE).end();
                builder.part().modelFile(bars).rotationY(90).uvLock(true).addModel().condition(BlockStateProperties.EAST, Boolean.TRUE).end();
                builder.part().modelFile(bars).rotationY(180).uvLock(true).addModel().condition(BlockStateProperties.SOUTH, Boolean.TRUE).end();
                builder.part().modelFile(bars).rotationY(270).uvLock(true).addModel().condition(BlockStateProperties.WEST, Boolean.TRUE).end();
                provider.itemModels().withExistingParent(provider.name(block), provider.modLoc("block/reactor_support_middle"));
            }));

    // ---------------- 结构 ----------------

    /** 地基：魔力护板（任意颜色；原作只认魔力护板，淡青 / 米斯莱护板不行） */
    public static final StructurePattern.Rule PLATING = StructurePattern.Rule.block(() -> ECAlloys.MAGIC_PLATING.get(),
            "structure.essentialcraft5.reactor.plating");

    /**
     * 原作 {@code TileMRUReactor.initStructure} L43~134，按「沿 X 轴摆」（机器朝北）写；判定时另试朝东（= 沿 Z 轴摆，原作第二段）。
     * 共 45 格：地基 15 块魔力护板、核心、4 根支架、25 格空气（核心正上方也要空）。空气用 {@link StructurePattern.Rule#air()}
     * （洞穴空气、虚空空气也算——原作写的是 {@code != Blocks.AIR}，1.12 地下没有洞穴空气，照抄会在洞里永远不成形）。
     * 字段写在 REACTOR、REACTOR_SUPPORT 之后（规则里取 RegistryObject，见 porting_guide §7 补充）。
     */
    public static final StructurePattern REACTOR_STRUCTURE = StructurePatterns.register(EssentialCraft5.id("reactor"),
            StructurePattern.builder()
                    .where('P', PLATING)
                    .where('S', StructurePattern.Rule.block(REACTOR_SUPPORT))
                    .where('R', StructurePattern.Rule.block(REACTOR))
                    .where('A', StructurePattern.Rule.air())
                    .anchor('R')
                    .layer("PPPPP", "PPPPP", "PPPPP")   // y = -1：地基
                    .layer("AAAAA", "ASRSA", "AAAAA")   // y = 0：核心两侧各一根支架
                    .layer("AAAAA", "ASASA", "AAAAA")   // y = +1：支架第二节，核心正上方空着
                    .build());

    /** 沿 X 轴摆（支架在核心东西两侧）时判定用的朝向 */
    public static final Direction X_AXIS = Direction.NORTH;
    /** 沿 Z 轴摆（支架在核心南北两侧）时判定用的朝向（北向图案顺时针转 90°） */
    public static final Direction Z_AXIS = Direction.EAST;

    private ECReactor() {
    }

    /** 由 ECRegistries.register() 调用：类加载即把上面的条目登记进 DeferredRegister，另登记配置 machines.reactor */
    public static void init() {
        ECConfig.machine(ReactorBlockEntity.Config::define);
    }
}
