package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.block.EnrichmentControllerBlock;
import com.flameforge.essentialcraft5.block.EnrichmentMachineBlock;
import com.flameforge.essentialcraft5.block.EnrichmentPartBlock;
import com.flameforge.essentialcraft5.block.EnrichmentRedstoneControllerBlock;
import com.flameforge.essentialcraft5.block.IntersectorBlock;
import com.flameforge.essentialcraft5.blockentity.EnrichmentAcceptorBlockEntity;
import com.flameforge.essentialcraft5.blockentity.EnrichmentControllerBlockEntity;
import com.flameforge.essentialcraft5.blockentity.EnrichmentEjectorBlockEntity;
import com.flameforge.essentialcraft5.blockentity.EnrichmentHoldingChamberBlockEntity;
import com.flameforge.essentialcraft5.blockentity.EnrichmentRedstoneControllerBlockEntity;
import com.flameforge.essentialcraft5.blockentity.EnrichmentStateCheckerBlockEntity;
import com.flameforge.essentialcraft5.blockentity.IntersectorBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.CondensedMatrixItem;
import com.flameforge.essentialcraft5.menu.EnrichmentAcceptorMenu;
import com.flameforge.essentialcraft5.menu.EnrichmentStateCheckerMenu;
import com.flameforge.essentialcraft5.menu.IntersectorMenu;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/**
 * 富集仓模块（第二批 §3.2）：MRUCU 富集仓的控制器与 5 种外壳部件、MRU 汇流器。
 * <p>
 * 新旧注册名：ecController → ec_controller；ecAcceptor → ec_acceptor；ecEjector → ec_ejector；ecHoldingChamber → ec_holding_chamber；
 * ecStateChecker → ec_state_checker；ecRedstoneController → ec_redstone_controller；intersector → intersector。
 * 稳定器 ecBalancer → ec_balancer 由第三批 ECPentacle 注册（实现 enrichment/EnrichmentStabilizer），控制器每 20 tick 给仓内聚集体续 40 tick 稳定期。
 * <p>
 * 6 个富集仓方块原作都是 Material.ROCK + MapColor.PURPLE、setHardness(3).setResistance(15).setLightOpacity(0)（先硬度后抗爆 → 抗爆 9），
 * cube_all 贴图；汇流器 registerBlockSimple(…, 1, 1, 15)（先抗爆后硬度 → 抗爆 1），JSON 模型，CUTOUT_MIPPED。
 * <p>
 * 外壳方块标签 {@link #CASING}（{@code essentialcraft5:mrucuec_casing}）由本模块建（tools/tags/ecenrichment.json：强化石、强化玻璃和 6 个部件），
 * 别的模块往自己的标签文件里加护板。工作台配方在 data/essentialcraft5/recipes/enrichment/，文字在 tools/lang/keys/ecenrichment.json，
 * 进度 main/mrucuec、main/mrucuec_big 用模块自带的触发器（enrichment/EnrichmentTrigger），手册章节 assets/essentialcraft5/guide/enrichment.json。
 * 客户端部分见 client/EnrichmentClient。
 * <p>
 * 第十三批 13a（batch13.md §3.5）：富集仓的第二个分支「凝结」，产物 {@link #CONDENSED_MATRIX}（凝结矩阵，四阶起淬炼台的祭品）；
 * 判定和产物规矩在 {@link EnrichmentControllerBlockEntity} 类注释，配置 {@code machines.enrichment_chamber.condense.*}，
 * 外壳里要数的护板是方块标签 {@code essentialcraft5:condense_casing}（tools/tags/condense.json）。
 */
public final class ECEnrichment {

    /** 能做富集仓外壳的方块（原作 StructureRegistry 的 MRUCUEC 列表） */
    public static final TagKey<Block> CASING = ECTags.modBlock("mrucuec_casing");

    // ---------------- 控制器 ----------------

    public static final RegistryObject<EnrichmentControllerBlock> EC_CONTROLLER = ECBlocks.register("ec_controller",
            () -> new EnrichmentControllerBlock(part()),
            BlockSpec.of(BlockSpec.cubeAll("block/mrucueccontroller")));

    public static final RegistryObject<BlockEntityType<EnrichmentControllerBlockEntity>> EC_CONTROLLER_BE =
            ECBlockEntities.type("ec_controller", EnrichmentControllerBlockEntity::new, EC_CONTROLLER);

    // ---------------- 存储方块 ----------------

    public static final RegistryObject<EnrichmentPartBlock> EC_HOLDING_CHAMBER = ECBlocks.register("ec_holding_chamber",
            () -> new EnrichmentPartBlock(part(), () -> ECEnrichment.EC_HOLDING_CHAMBER_BE.get()),
            BlockSpec.of(BlockSpec.cubeAll("block/mrucuecmruholdingchamber")));

    public static final RegistryObject<BlockEntityType<EnrichmentHoldingChamberBlockEntity>> EC_HOLDING_CHAMBER_BE =
            ECBlockEntities.type("ec_holding_chamber", EnrichmentHoldingChamberBlockEntity::new, EC_HOLDING_CHAMBER);

    // ---------------- 接收器 ----------------

    public static final RegistryObject<EnrichmentMachineBlock<EnrichmentAcceptorBlockEntity>> EC_ACCEPTOR = ECBlocks.register("ec_acceptor",
            () -> new EnrichmentMachineBlock<>(part(), () -> ECEnrichment.EC_ACCEPTOR_BE.get(), true),
            BlockSpec.of(BlockSpec.cubeAll("block/mrucuecmruacceptor")));

    public static final RegistryObject<BlockEntityType<EnrichmentAcceptorBlockEntity>> EC_ACCEPTOR_BE =
            ECBlockEntities.type("ec_acceptor", EnrichmentAcceptorBlockEntity::new, EC_ACCEPTOR);

    public static final RegistryObject<MenuType<EnrichmentAcceptorMenu>> EC_ACCEPTOR_MENU =
            ECMenus.register("ec_acceptor", EnrichmentAcceptorMenu::new);

    // ---------------- 输出器 ----------------

    public static final RegistryObject<EnrichmentPartBlock> EC_EJECTOR = ECBlocks.register("ec_ejector",
            () -> new EnrichmentPartBlock(part(), () -> ECEnrichment.EC_EJECTOR_BE.get()),
            BlockSpec.of(BlockSpec.cubeAll("block/mrucuecmruejector")));

    public static final RegistryObject<BlockEntityType<EnrichmentEjectorBlockEntity>> EC_EJECTOR_BE =
            ECBlockEntities.type("ec_ejector", EnrichmentEjectorBlockEntity::new, EC_EJECTOR);

    // ---------------- 状态检测器 ----------------

    public static final RegistryObject<EnrichmentMachineBlock<EnrichmentStateCheckerBlockEntity>> EC_STATE_CHECKER = ECBlocks.register("ec_state_checker",
            () -> new EnrichmentMachineBlock<>(part(), () -> ECEnrichment.EC_STATE_CHECKER_BE.get(), false),
            BlockSpec.of(BlockSpec.cubeAll("block/mrucuecmrustatechecker")));

    public static final RegistryObject<BlockEntityType<EnrichmentStateCheckerBlockEntity>> EC_STATE_CHECKER_BE =
            ECBlockEntities.type("ec_state_checker", EnrichmentStateCheckerBlockEntity::new, EC_STATE_CHECKER);

    public static final RegistryObject<MenuType<EnrichmentStateCheckerMenu>> EC_STATE_CHECKER_MENU =
            ECMenus.register("ec_state_checker", EnrichmentStateCheckerMenu::new);

    // ---------------- 红石控制器 ----------------

    public static final RegistryObject<EnrichmentRedstoneControllerBlock> EC_REDSTONE_CONTROLLER = ECBlocks.register("ec_redstone_controller",
            () -> new EnrichmentRedstoneControllerBlock(part()),
            BlockSpec.of(BlockSpec.cubeAll("block/mrucuecredstonecontroller")));

    public static final RegistryObject<BlockEntityType<EnrichmentRedstoneControllerBlockEntity>> EC_REDSTONE_CONTROLLER_BE =
            ECBlockEntities.type("ec_redstone_controller", EnrichmentRedstoneControllerBlockEntity::new, EC_REDSTONE_CONTROLLER);

    // ---------------- MRU 汇流器 ----------------

    public static final RegistryObject<IntersectorBlock> INTERSECTOR = ECBlocks.register("intersector",
            () -> new IntersectorBlock(ECBlocks.machine(MapColor.STONE, 1F, ECBlocks.legacyResistance(1F, 1F, true)).noOcclusion()),
            BlockSpec.of(BlockSpec.existing("intersector")));

    public static final RegistryObject<BlockEntityType<IntersectorBlockEntity>> INTERSECTOR_BE =
            ECBlockEntities.type("intersector", IntersectorBlockEntity::new, INTERSECTOR);

    public static final RegistryObject<MenuType<IntersectorMenu>> INTERSECTOR_MENU =
            ECMenus.register("intersector", IntersectorMenu::new);

    // ---------------- 凝结矩阵（第十三批 13a） ----------------

    /** 富集仓平衡值在中间时凝出来；不能合成，只从仓里来 */
    public static final RegistryObject<CondensedMatrixItem> CONDENSED_MATRIX = ECItems.register("condensed_matrix",
            () -> new CondensedMatrixItem(new Item.Properties().rarity(Rarity.UNCOMMON)), ItemSpec.generated("item/condensed_matrix"));

    private ECEnrichment() {
    }

    /** 富集仓部件的方块属性：原作 setHardness(3).setResistance(15)（先硬度后抗爆 → 抗爆 9），Material.ROCK 要镐 */
    private static BlockBehaviour.Properties part() {
        return ECBlocks.machine(MapColor.COLOR_PURPLE, 3F, ECBlocks.legacyResistance(3F, 15F, false));
    }

    /** 由 ECRegistries.register() 调用：类加载即把上面的条目登记进 DeferredRegister，这里再登记配置小节 */
    public static void init() {
        ECConfig.machine(EnrichmentControllerBlockEntity.Config::define);
        ECConfig.machine(IntersectorBlockEntity.Config::define);
    }
}
