package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IMRUHandler;
import com.flameforge.essentialcraft5.blockentity.EnrichmentAcceptorBlockEntity;
import com.flameforge.essentialcraft5.blockentity.EnrichmentControllerBlockEntity;
import com.flameforge.essentialcraft5.blockentity.EnrichmentEjectorBlockEntity;
import com.flameforge.essentialcraft5.blockentity.EnrichmentHoldingChamberBlockEntity;
import com.flameforge.essentialcraft5.blockentity.EnrichmentRedstoneControllerBlockEntity;
import com.flameforge.essentialcraft5.blockentity.EnrichmentStateCheckerBlockEntity;
import com.flameforge.essentialcraft5.blockentity.IntersectorBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MRUBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.enrichment.CuboidShellScanner;
import com.flameforge.essentialcraft5.enrichment.EnrichmentStorage;
import com.flameforge.essentialcraft5.entity.MRUResistances;
import com.flameforge.essentialcraft5.item.BoundGemItem;
import com.flameforge.essentialcraft5.mru.MRULink;
import com.flameforge.essentialcraft5.mru.MRUStorage;
import com.flameforge.essentialcraft5.registry.ECBlocks;
import com.flameforge.essentialcraft5.registry.ECEnrichment;
import com.flameforge.essentialcraft5.registry.ECItems;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.structure.StructureResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 富集仓模块（ECEnrichment）的 GameTest：容量、拆存储方块不截断、输出器报真实平衡值（平衡值设定器能透过输出器改仓的平衡值）、
 * 缺块 / 超尺寸 / 两个控制器不成形、控制器在侧墙和角上、垫地板 / 贴墙（收边后仓底和贴墙那面的部件照样算）、
 * 共用墙的存储方块只算一次、控制器拆掉后部件失效、接收器往仓里拉（对外读数为 0，绑它的机器显示没有 MRU）、红石控制器阈值、
 * 状态检测器读数、汇流器第二个宝石、超上限存量的存读。
 * <p>
 * 大结构用模板 empty_large（11×8×11 空气，相对 (0,0,0) 是结构方块，从 y=1 起放东西）。
 * 标准外壳：强化石砌的 5×3×5，相对坐标 (3,1,3)~(7,3,7)，控制器在北墙正中 (5,2,3)。
 * 容量按当前配置算（默认 60000 + 每个存储方块 100000）。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class EnrichmentTests {

    private static final String EMPTY = "empty";
    private static final String LARGE = "empty_large";

    private static final BlockPos MIN = new BlockPos(3, 1, 3);
    private static final BlockPos MAX = new BlockPos(7, 3, 7);
    /** 北墙正中 */
    private static final BlockPos CONTROLLER = new BlockPos(5, 2, 3);
    /** 西墙、东墙正中 */
    private static final BlockPos CHAMBER_WEST = new BlockPos(3, 2, 5);
    private static final BlockPos CHAMBER_EAST = new BlockPos(7, 2, 5);
    /** 南墙正中 */
    private static final BlockPos EJECTOR = new BlockPos(5, 2, 7);
    /** 顶面正中 */
    private static final BlockPos ACCEPTOR = new BlockPos(5, 3, 5);
    /** 底面正中 */
    private static final BlockPos STATE_CHECKER = new BlockPos(5, 1, 5);

    private EnrichmentTests() {
    }

    // ================================================================ 搭建

    /** 用 block 砌 min~max 长方体的六个面（相对坐标） */
    private static void shell(GameTestHelper helper, BlockPos min, BlockPos max, Block block) {
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    if (x == min.getX() || x == max.getX() || y == min.getY() || y == max.getY() || z == min.getZ() || z == max.getZ()) {
                        helper.setBlock(new BlockPos(x, y, z), block);
                    }
                }
            }
        }
    }

    private static Block stone() {
        return ECMaterials.FORTIFIED_STONE.get();
    }

    /** 标准 5×3×5 外壳 + 北墙控制器 */
    private static EnrichmentControllerBlockEntity standard(GameTestHelper helper) {
        shell(helper, MIN, MAX, stone());
        return controllerAt(helper, CONTROLLER);
    }

    private static EnrichmentControllerBlockEntity controllerAt(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ECEnrichment.EC_CONTROLLER.get());
        return (EnrichmentControllerBlockEntity) helper.getBlockEntity(pos);
    }

    private static int capacity(int chambers) {
        long total = (long) ECConfig.get(EnrichmentControllerBlockEntity.Config.maxMRU)
                + (long) ECConfig.get(EnrichmentControllerBlockEntity.Config.mruPerStorage) * chambers;
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private static IMRUHandler mruOf(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos).getCapability(ECCapabilities.MRU)
                .orElseThrow(() -> new IllegalStateException(pos + " 没有 MRU capability"));
    }

    private static ItemStack gemTo(GameTestHelper helper, BlockPos relative) {
        ItemStack gem = new ItemStack(ECItems.BOUND_GEM.get());
        BoundGemItem.bind(gem, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(relative)), true);
        return gem;
    }

    // ================================================================ 容量

    /** 2 个存储方块时上限 = 60000 + 2 × 100000（按配置算） */
    @GameTest(template = LARGE)
    public static void capacityWithTwoChambers(GameTestHelper helper) {
        EnrichmentControllerBlockEntity controller = standard(helper);
        helper.setBlock(CHAMBER_WEST, ECEnrichment.EC_HOLDING_CHAMBER.get());
        helper.setBlock(CHAMBER_EAST, ECEnrichment.EC_HOLDING_CHAMBER.get());
        helper.assertTrue(controller.storage().getMaxMRU() == capacity(0), "没成形时应是基础容量");
        StructureResult result = controller.checkNow();
        helper.assertTrue(result == StructureResult.MATCH, "5×3×5 外壳应成形，实际 " + result + " / " + controller.failure());
        helper.assertTrue(controller.chambers() == 2, "应数到 2 个存储方块，实际 " + controller.chambers());
        helper.assertTrue(controller.storage().getMaxMRU() == capacity(2),
                "上限应为 " + capacity(2) + "（默认 260000），实际 " + controller.storage().getMaxMRU());
        helper.assertTrue(controller.box() != null && controller.box().getXSpan() == 5 && controller.box().getYSpan() == 3
                && controller.box().getZSpan() == 5, "外壳应是 5×3×5");
        helper.assertTrue(controller.hasPart(helper.absolutePos(CHAMBER_WEST)), "存储方块应被认领");
        EnrichmentHoldingChamberBlockEntity chamber = (EnrichmentHoldingChamberBlockEntity) helper.getBlockEntity(CHAMBER_WEST);
        helper.assertTrue(chamber.enrichmentLink().controller() == controller, "存储方块应连着这个控制器");
        helper.succeed();
    }

    /** 拆掉一个存储方块：上限立即变小，但存量不截断；超出部分只出不进 */
    @GameTest(template = LARGE)
    public static void removingChamberKeepsStoredMRU(GameTestHelper helper) {
        EnrichmentControllerBlockEntity controller = standard(helper);
        helper.setBlock(CHAMBER_WEST, ECEnrichment.EC_HOLDING_CHAMBER.get());
        helper.setBlock(CHAMBER_EAST, ECEnrichment.EC_HOLDING_CHAMBER.get());
        helper.setBlock(EJECTOR, ECEnrichment.EC_EJECTOR.get());
        helper.assertTrue(controller.checkNow() == StructureResult.MATCH, "应成形");
        EnrichmentStorage storage = controller.storage();
        int full = capacity(2);
        storage.setMRU(full);
        helper.assertTrue(storage.getMRU() == full, "应能存满 " + full);

        helper.setBlock(CHAMBER_EAST, stone());
        helper.assertTrue(controller.chambers() == 1, "拆掉后应立即只剩 1 个存储方块，实际 " + controller.chambers());
        helper.assertTrue(storage.getMaxMRU() == capacity(1), "上限应立即降到 " + capacity(1));
        helper.assertTrue(storage.getMRU() == full, "存量不应被截断，实际 " + storage.getMRU());
        helper.assertTrue(controller.checkNow() == StructureResult.MATCH, "换成强化石后外壳仍完整");
        helper.assertTrue(storage.getMRU() == full && storage.isOverCapacity(), "重扫后存量仍保留（超出上限）");
        helper.assertTrue(storage.addMRU(1000, true) == 1000 && storage.getMRU() == full, "超出上限时加不进去");

        IMRUHandler ejector = mruOf(helper, EJECTOR);
        int drained = full - capacity(1);
        helper.assertTrue(ejector.extractMRU(drained, true) == drained, "输出器应能取出超出的部分");
        helper.assertTrue(storage.getMRU() == capacity(1), "取到正好等于上限");
        helper.assertTrue(storage.addMRU(1000, true) == 1000, "满了仍加不进去");
        helper.assertTrue(ejector.extractMRU(1000, true) == 1000, "再取 1000");
        helper.assertTrue(storage.addMRU(5000, true) == 4000 && storage.getMRU() == capacity(1), "低于上限后恢复正常，只收到上限");
        helper.succeed();
    }

    /** 输出器报控制器的真实平衡值，从它拉出来的 MRU 带着这个平衡值（原作恒为 1.0） */
    @GameTest(template = LARGE)
    public static void ejectorReportsRealBalance(GameTestHelper helper) {
        EnrichmentControllerBlockEntity controller = standard(helper);
        helper.setBlock(EJECTOR, ECEnrichment.EC_EJECTOR.get());
        helper.assertTrue(controller.checkNow() == StructureResult.MATCH, "应成形");
        controller.storage().setMRU(50000);
        controller.storage().setBalance(1.6F);
        IMRUHandler ejector = mruOf(helper, EJECTOR);
        helper.assertTrue(Math.abs(ejector.getBalance() - 1.6F) < 1.0E-4F, "输出器平衡值应为 1.6，实际 " + ejector.getBalance());
        helper.assertTrue(ejector.getMRU() == 50000 && ejector.getMaxMRU() == controller.storage().getMaxMRU(), "输出器读数应是整座仓的");
        helper.assertTrue(ejector.addMRU(100, true) == 100, "输出器只出不进");
        MRUStorage consumer = new MRUStorage(5000);
        helper.assertTrue(consumer.pullFrom(ejector) == 5000, "消费端应拉到 5000");
        helper.assertTrue(Math.abs(consumer.getBalance() - 1.6F) < 1.0E-4F, "拉出来的 MRU 平衡值应为 1.6，实际 " + consumer.getBalance());
        helper.assertTrue(controller.storage().getMRU() == 45000, "仓里应剩 45000");
        // 平衡值设定器（管理员工具）调的是方块 capability 的 setBalance：透传给控制器；setMRU 不透传
        ejector.setBalance(0.4F);
        helper.assertTrue(Math.abs(controller.storage().getBalance() - 0.4F) < 1.0E-4F,
                "透过输出器设的平衡值应落到仓上，实际 " + controller.storage().getBalance());
        ejector.setMRU(0);
        helper.assertTrue(controller.storage().getMRU() == 45000, "setMRU 不透传，仓里仍是 45000");
        helper.succeed();
    }

    // ================================================================ 形状

    /** 外壳缺一块时不成形，并指出缺在哪 */
    @GameTest(template = LARGE)
    public static void gapBreaksShell(GameTestHelper helper) {
        EnrichmentControllerBlockEntity controller = standard(helper);
        helper.assertTrue(controller.checkNow() == StructureResult.MATCH, "完整时应成形");
        BlockPos gap = new BlockPos(6, 1, 4);
        helper.setBlock(gap, Blocks.AIR);
        StructureResult result = controller.checkNow();
        helper.assertTrue(result == StructureResult.MISMATCH && !controller.isFormed(), "缺一块时不应成形，实际 " + result);
        helper.assertTrue(controller.failure() == CuboidShellScanner.Failure.GAP, "原因应是缺块，实际 " + controller.failure());
        helper.assertTrue(helper.absolutePos(gap).equals(controller.failurePos()), "应指出缺块的位置，实际 " + controller.failurePos());
        helper.assertTrue(controller.storage().getMaxMRU() == capacity(0), "不成形时上限回到基础容量");
        helper.succeed();
    }

    /** 边长超过上限不成形（用扫描器直接给上限 4）；上限 5 时成形 */
    @GameTest(template = LARGE)
    public static void oversizedShellNotFormed(GameTestHelper helper) {
        standard(helper);
        BlockPos origin = helper.absolutePos(CONTROLLER);
        CuboidShellScanner.Result tooBig = CuboidShellScanner.scan(helper.getLevel(), origin,
                state -> state.is(ECEnrichment.CASING), 3, 4, CuboidShellScanner.NO_VISITOR);
        helper.assertTrue(tooBig.status() == StructureResult.MISMATCH && tooBig.failure() == CuboidShellScanner.Failure.SIZE,
                "边长 5 超过上限 4 时应判尺寸不对，实际 " + tooBig);
        CuboidShellScanner.Result fits = CuboidShellScanner.scan(helper.getLevel(), origin,
                state -> state.is(ECEnrichment.CASING), 3, 5, CuboidShellScanner.NO_VISITOR);
        helper.assertTrue(fits.matches(), "上限 5 时应成形，实际 " + fits);
        CuboidShellScanner.Result tooSmallLimit = CuboidShellScanner.scan(helper.getLevel(), origin,
                state -> state.is(ECEnrichment.CASING), 6, 32, CuboidShellScanner.NO_VISITOR);
        helper.assertTrue(!tooSmallLimit.matches(), "下限 6 时 5 格的外壳不应成形");
        helper.succeed();
    }

    /** 一座仓只能有一个控制器，两个都不成形 */
    @GameTest(template = LARGE)
    public static void twoControllersRejected(GameTestHelper helper) {
        EnrichmentControllerBlockEntity first = standard(helper);
        EnrichmentControllerBlockEntity second = controllerAt(helper, EJECTOR);
        helper.assertTrue(first.checkNow() == StructureResult.MISMATCH && first.failure() == CuboidShellScanner.Failure.REJECTED,
                "第一个控制器应因为有第二个控制器而不成形，实际 " + first.status() + " / " + first.failure());
        helper.assertTrue(helper.absolutePos(EJECTOR).equals(first.failurePos()), "应指出另一个控制器的位置");
        helper.assertTrue(second.checkNow() == StructureResult.MISMATCH, "第二个控制器也不成形");
        helper.succeed();
    }

    /** 控制器在角上、外壳最小 3×3×3 */
    @GameTest(template = LARGE)
    public static void cornerControllerMinimalShell(GameTestHelper helper) {
        BlockPos min = new BlockPos(2, 1, 2);
        shell(helper, min, new BlockPos(4, 3, 4), stone());
        EnrichmentControllerBlockEntity controller = controllerAt(helper, min);
        StructureResult result = controller.checkNow();
        helper.assertTrue(result == StructureResult.MATCH, "角上的控制器、3×3×3 外壳应成形，实际 " + result + " / " + controller.failure());
        helper.assertTrue(controller.box() != null && controller.box().getXSpan() == 3 && controller.box().getYSpan() == 3,
                "外壳应是 3×3×3");
        helper.succeed();
    }

    /** 仓 (3,2,3)~(7,4,7) 搭在更大的强化石地板 (2,1,2)~(8,1,8) 上 */
    private static void floorAndChamber(GameTestHelper helper) {
        for (int x = 2; x <= 8; x++) {
            for (int z = 2; z <= 8; z++) {
                helper.setBlock(new BlockPos(x, 1, z), stone());
            }
        }
        shell(helper, new BlockPos(3, 2, 3), new BlockPos(7, 4, 7), stone());
    }

    private static void assertChamberBox(GameTestHelper helper, EnrichmentControllerBlockEntity controller, BlockPos min, BlockPos max) {
        BoundingBox expected = BoundingBox.fromCorners(helper.absolutePos(min), helper.absolutePos(max));
        helper.assertTrue(expected.equals(controller.box()), "外壳应是 " + expected + "（不含垫着的地板 / 墙），实际 " + controller.box());
    }

    /**
     * 仓垫在更大的强化石地板上、控制器在侧墙正中：过控制器的竖线会走进地板，收边后仓底仍是真正的仓底，
     * 嵌在仓底正中的存储方块照样算（以前地板那一层被当成仓底，仓底中间成了内部）。
     */
    @GameTest(template = LARGE)
    public static void sideWallControllerOnFloor(GameTestHelper helper) {
        floorAndChamber(helper);
        BlockPos pos = new BlockPos(3, 3, 5);
        EnrichmentControllerBlockEntity controller = controllerAt(helper, pos);
        helper.setBlock(new BlockPos(7, 3, 5), ECEnrichment.EC_HOLDING_CHAMBER.get());
        helper.setBlock(new BlockPos(5, 2, 5), ECEnrichment.EC_HOLDING_CHAMBER.get());
        StructureResult result = controller.checkNow();
        helper.assertTrue(result == StructureResult.MATCH, "侧墙上的控制器应成形，实际 " + result + " / " + controller.failure());
        assertChamberBox(helper, controller, new BlockPos(3, 2, 3), new BlockPos(7, 4, 7));
        helper.assertTrue(controller.chambers() == 2, "应数到对面墙上和仓底正中的存储方块，实际 " + controller.chambers());
        helper.succeed();
    }

    /**
     * 手册示例的摆法（控制器在北墙正中）搭在强化石地板上，东墙外面再贴一堵强化石墙：
     * 地板、贴着的墙都收掉，仓底正中的存储方块、东墙上的输出器都归这座仓。
     */
    @GameTest(template = LARGE)
    public static void paddedFloorAndWallTrimmed(GameTestHelper helper) {
        floorAndChamber(helper);
        for (int y = 2; y <= 4; y++) {
            for (int z = 3; z <= 7; z++) {
                helper.setBlock(new BlockPos(8, y, z), stone());
            }
        }
        EnrichmentControllerBlockEntity controller = controllerAt(helper, new BlockPos(5, 3, 3));
        BlockPos chamber = new BlockPos(5, 2, 5);
        BlockPos ejector = new BlockPos(7, 3, 5);
        helper.setBlock(chamber, ECEnrichment.EC_HOLDING_CHAMBER.get());
        helper.setBlock(ejector, ECEnrichment.EC_EJECTOR.get());
        StructureResult result = controller.checkNow();
        helper.assertTrue(result == StructureResult.MATCH, "应成形，实际 " + result + " / " + controller.failure());
        assertChamberBox(helper, controller, new BlockPos(3, 2, 3), new BlockPos(7, 4, 7));
        helper.assertTrue(controller.chambers() == 1, "仓底正中的存储方块应算上，实际 " + controller.chambers());
        helper.assertTrue(controller.hasPart(helper.absolutePos(chamber)) && controller.hasPart(helper.absolutePos(ejector)),
                "仓底的存储方块、贴墙那面的输出器都应被认领");
        EnrichmentEjectorBlockEntity be = (EnrichmentEjectorBlockEntity) helper.getBlockEntity(ejector);
        helper.assertTrue(be.enrichmentLink().controller() == controller, "输出器应连着这个控制器");
        helper.succeed();
    }

    /** 两座仓共用一面墙：墙上的存储方块只算给先认领的那座；那座的控制器拆掉后另一座接手 */
    @GameTest(template = LARGE)
    public static void sharedWallChamberCountedOnce(GameTestHelper helper) {
        shell(helper, new BlockPos(1, 1, 1), new BlockPos(3, 3, 3), stone());
        shell(helper, new BlockPos(3, 1, 1), new BlockPos(5, 4, 3), stone());
        BlockPos shared = new BlockPos(3, 2, 2);
        helper.setBlock(shared, ECEnrichment.EC_HOLDING_CHAMBER.get());
        BlockPos posA = new BlockPos(2, 3, 2);
        BlockPos posB = new BlockPos(4, 4, 2);
        EnrichmentControllerBlockEntity a = controllerAt(helper, posA);
        EnrichmentControllerBlockEntity b = controllerAt(helper, posB);
        helper.assertTrue(a.checkNow() == StructureResult.MATCH, "A 应成形，实际 " + a.status() + " / " + a.failure());
        helper.assertTrue(a.chambers() == 1, "A 应认领共用墙上的存储方块");
        helper.assertTrue(b.checkNow() == StructureResult.MATCH, "B 应成形，实际 " + b.status() + " / " + b.failure());
        helper.assertTrue(b.chambers() == 0, "已被 A 认领的存储方块不应再算给 B，实际 " + b.chambers());
        helper.setBlock(posA, Blocks.AIR);
        helper.assertTrue(b.checkNow() == StructureResult.MATCH && b.chambers() == 1, "A 的控制器拆掉后 B 应接手存储方块");
        helper.succeed();
    }

    // ================================================================ 部件

    /** 控制器拆掉后，输出器读数归零、取不出（原作部件一直引用着死掉的控制器） */
    @GameTest(template = LARGE)
    public static void ejectorUnlinkedAfterControllerBroken(GameTestHelper helper) {
        EnrichmentControllerBlockEntity controller = standard(helper);
        helper.setBlock(EJECTOR, ECEnrichment.EC_EJECTOR.get());
        helper.assertTrue(controller.checkNow() == StructureResult.MATCH, "应成形");
        controller.storage().setMRU(10000);
        IMRUHandler ejector = mruOf(helper, EJECTOR);
        helper.assertTrue(ejector.getMRU() == 10000, "拆之前输出器应读到 10000");
        helper.setBlock(CONTROLLER, stone());
        helper.assertTrue(ejector.getMRU() == 0 && ejector.getMaxMRU() == 0, "控制器拆掉后读数应为 0");
        helper.assertTrue(ejector.extractMRU(1000, true) == 0, "控制器拆掉后取不出 MRU");
        EnrichmentEjectorBlockEntity be = (EnrichmentEjectorBlockEntity) helper.getBlockEntity(EJECTOR);
        helper.assertTrue(be.enrichmentLink().controllerPos() == null, "控制器拆掉时应解开部件");
        helper.succeed();
    }

    /** 接收器从绑定宝石指向的源头把 MRU 拉进整座仓，平衡值跟着源头；对外只进不出 */
    @GameTest(template = LARGE)
    public static void acceptorPullsIntoChamber(GameTestHelper helper) {
        EnrichmentControllerBlockEntity controller = standard(helper);
        helper.setBlock(ACCEPTOR, ECEnrichment.EC_ACCEPTOR.get());
        BlockPos source = new BlockPos(9, 1, 5);
        helper.setBlock(source, ECBlocks.CREATIVE_MRU_STORAGE.get());
        ((MRUBlockEntity) helper.getBlockEntity(source)).getMRUStorage().setBalance(0.5F);
        EnrichmentAcceptorBlockEntity acceptor = (EnrichmentAcceptorBlockEntity) helper.getBlockEntity(ACCEPTOR);
        acceptor.getInventory().setStackInSlot(EnrichmentAcceptorBlockEntity.SLOT_GEM, gemTo(helper, source));
        helper.assertTrue(controller.checkNow() == StructureResult.MATCH, "应成形");
        IMRUHandler view = mruOf(helper, ACCEPTOR);
        helper.succeedWhen(() -> {
            helper.assertTrue(controller.storage().getMRU() == capacity(0), "仓应被拉满，实际 " + controller.storage().getMRU());
            helper.assertTrue(Math.abs(controller.storage().getBalance() - 0.5F) < 1.0E-4F,
                    "平衡值应跟着源头（0.5），实际 " + controller.storage().getBalance());
            helper.assertTrue(acceptor.getLinkStatus() == MRULink.Status.WORKING, "宝石状态应是工作中，实际 " + acceptor.getLinkStatus());
            helper.assertTrue(view.getMRU() == 0 && view.getMaxMRU() == controller.storage().getMaxMRU(),
                    "接收器对外存量读数应为 0、上限是整座仓的，实际 " + view.getMRU() + " / " + view.getMaxMRU());
            helper.assertTrue(view.extractMRU(100, false) == 0, "接收器只进不出");
            // 机器的宝石误绑到接收器上：黄色「目标没有 MRU」，而不是绿色「工作中」却拉不到
            MRUStorage consumer = new MRUStorage(1000);
            MRULink.Status misbound = MRULink.pull(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), consumer,
                    gemTo(helper, ACCEPTOR), MRULink.DEFAULT_RANGE, false);
            helper.assertTrue(misbound == MRULink.Status.NO_MRU && consumer.getMRU() == 0,
                    "绑到接收器的机器应显示没有 MRU、拉不到，实际 " + misbound + " / " + consumer.getMRU());
        });
    }

    /** 红石控制器：设定 50% 时，存量 40% 不出信号、60% 出 15 级 */
    @GameTest(template = LARGE, timeoutTicks = 120)
    public static void redstoneControllerThreshold(GameTestHelper helper) {
        EnrichmentControllerBlockEntity controller = standard(helper);
        helper.setBlock(CHAMBER_EAST, ECEnrichment.EC_REDSTONE_CONTROLLER.get());
        helper.assertTrue(controller.checkNow() == StructureResult.MATCH, "应成形");
        EnrichmentRedstoneControllerBlockEntity rc = (EnrichmentRedstoneControllerBlockEntity) helper.getBlockEntity(CHAMBER_EAST);
        for (int i = 0; i < 5; i++) {
            rc.cycleSetting();
        }
        helper.assertTrue(rc.getSetting() == 5, "潜行右键 5 次应切到 50%");
        int max = controller.storage().getMaxMRU();
        controller.storage().setMRU(max * 4 / 10);
        helper.runAtTickTime(25, () -> {
            helper.assertTrue(!rc.isPowered(), "40% 时不应出信号");
            controller.storage().setMRU(max * 6 / 10);
        });
        helper.runAtTickTime(50, () -> {
            helper.assertTrue(rc.isPowered(), "60% 时应出信号");
            int signal = helper.getLevel().getSignal(helper.absolutePos(CHAMBER_EAST), Direction.EAST);
            helper.assertTrue(signal == 15, "信号强度应为 15，实际 " + signal);
            helper.succeed();
        });
    }

    /** 状态检测器读的是整座仓：存量、平衡值、外壳 MROV；不对外暴露 MRU */
    @GameTest(template = LARGE)
    public static void stateCheckerReadsChamber(GameTestHelper helper) {
        EnrichmentControllerBlockEntity controller = standard(helper);
        helper.setBlock(STATE_CHECKER, ECEnrichment.EC_STATE_CHECKER.get());
        helper.assertTrue(controller.checkNow() == StructureResult.MATCH, "应成形");
        controller.storage().setMRU(12345);
        EnrichmentStateCheckerBlockEntity checker = (EnrichmentStateCheckerBlockEntity) helper.getBlockEntity(STATE_CHECKER);
        helper.assertTrue(checker.isLinked(), "状态检测器应连着仓");
        helper.assertTrue(checker.getMRUStorage().getMRU() == 12345, "界面读到的应是仓的存量");
        // 5×3×5 外壳 66 格：64 块强化石 + 控制器 + 状态检测器
        float expected = 64 * MRUResistances.get(stone().defaultBlockState())
                + MRUResistances.get(ECEnrichment.EC_CONTROLLER.get().defaultBlockState())
                + MRUResistances.get(ECEnrichment.EC_STATE_CHECKER.get().defaultBlockState());
        helper.assertTrue(Math.abs(checker.resistance() - expected) < 1.0E-3, "MROV 应为 " + expected + "，实际 " + checker.resistance());
        helper.assertTrue(!checker.getCapability(ECCapabilities.MRU).isPresent(), "状态检测器不对外暴露 MRU（同原作）");
        helper.succeed();
    }

    /** 汇流器：只在第二个槽放宝石也能拉满，第二个宝石的状态单独同步 */
    @GameTest(template = EMPTY)
    public static void intersectorSecondGem(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        BlockPos source = new BlockPos(3, 1, 3);
        helper.setBlock(pos, ECEnrichment.INTERSECTOR.get());
        helper.setBlock(source, ECBlocks.CREATIVE_MRU_STORAGE.get());
        IntersectorBlockEntity be = (IntersectorBlockEntity) helper.getBlockEntity(pos);
        helper.assertTrue(!be.isItemValid(IntersectorBlockEntity.SLOT_GEM_2, new ItemStack(Blocks.STONE)), "宝石槽只收绑定宝石");
        be.getInventory().setStackInSlot(IntersectorBlockEntity.SLOT_GEM_2, gemTo(helper, source));
        helper.succeedWhen(() -> {
            helper.assertTrue(be.getMRUStorage().getMRU() == be.getMRUStorage().getMaxMRU(), "应被拉满，实际 " + be.getMRUStorage().getMRU());
            helper.assertTrue(be.getSecondLinkStatus() == MRULink.Status.WORKING, "第二个宝石应在工作，实际 " + be.getSecondLinkStatus());
            helper.assertTrue(be.getLinkStatus() == MRULink.Status.NO_GEM, "第一个槽空着");
        });
    }

    // ================================================================ 存储

    /** 超出上限的存量：存读不丢、只出不进、setMRU 不能抬过上限但可以往下调 */
    @GameTest(template = EMPTY)
    public static void storageKeepsOverCapacity(GameTestHelper helper) {
        int[] max = {100};
        EnrichmentStorage storage = new EnrichmentStorage(() -> max[0]);
        CompoundTag saved = new CompoundTag();
        saved.putInt("mru", 500);
        saved.putFloat("balance", 1.2F);
        storage.deserializeNBT(saved);
        helper.assertTrue(storage.getMRU() == 500 && storage.isOverCapacity(), "读档不按上限截断");
        helper.assertTrue(storage.addMRU(10, true) == 10, "超出上限时加不进去");
        helper.assertTrue(storage.extractMRU(450, true) == 450 && storage.getMRU() == 50, "照常取出");
        helper.assertTrue(storage.addMRU(60, true) == 10 && storage.getMRU() == 100, "只加到上限");
        storage.setMRU(1000);
        helper.assertTrue(storage.getMRU() == 100, "setMRU 不能抬过上限");
        max[0] = 50;
        helper.assertTrue(storage.getMRU() == 100, "上限变小不截断");
        storage.setMRU(80);
        helper.assertTrue(storage.getMRU() == 80, "超出时仍能往下调");
        EnrichmentStorage copy = new EnrichmentStorage(() -> 10);
        copy.deserializeNBT(storage.serializeNBT());
        helper.assertTrue(copy.getMRU() == 80 && Math.abs(copy.getBalance() - 1.2F) < 1.0E-4F, "存读后数值不变");
        helper.succeed();
    }
}
