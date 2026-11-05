package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.EnrichmentControllerBlockEntity;
import com.flameforge.essentialcraft5.blockentity.EnrichmentControllerBlockEntity.CollapseState;
import com.flameforge.essentialcraft5.blockentity.EnrichmentControllerBlockEntity.CollapseValues;
import com.flameforge.essentialcraft5.blockentity.EnrichmentControllerBlockEntity.CondenseState;
import com.flameforge.essentialcraft5.blockentity.EnrichmentControllerBlockEntity.CondenseValues;
import com.flameforge.essentialcraft5.blockentity.EnrichmentStateCheckerBlockEntity;
import com.flameforge.essentialcraft5.entity.MRUPresenceEntity;
import com.flameforge.essentialcraft5.hoanna.HoannaTide;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvents;
import com.flameforge.essentialcraft5.menu.EnrichmentStateCheckerMenu;
import com.flameforge.essentialcraft5.registry.ECAlloys;
import com.flameforge.essentialcraft5.registry.ECEnrichment;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.registry.ECPentacle;
import com.flameforge.essentialcraft5.registry.ECTags;
import com.flameforge.essentialcraft5.registry.ECVoid;
import com.flameforge.essentialcraft5.structure.StructureResult;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 第十三批 13a「凝结矩阵」的 GameTest（batch13.md §3.5、§12.2）：任何维度都能凝；平衡值窗口 [0.8, 1.2] 两边；外壳里护板 7 块不走、8 块走；
 * 没有稳定器、聚集体存量不够、仓里 MRU 不够都不走；产物永不消失、满一组停在 OUTPUT_FULL；海纳尔里同一座仓按平衡值分到坍缩或凝结；
 * 不吃天灾（倍数 1），倍数改大时才加速；进度存盘；状态检测器和右键控制器写出卡在哪；全程不动方块；
 * 产物被拦时什么都不扣；外壳区块实体没读完时不判丢；两团时认准多的那团、开始后不换、被吞并后清零。
 * <p>
 * 富集仓站位同 {@link MidgameStepTests}（{@code empty_large}）：强化石砌的 5×3×5 外壳 (3,1,3)~(7,3,7)，控制器在北墙正中，存储方块在西墙正中，
 * 稳定器在东墙正中，状态检测器在底面正中；护板铺在底面正中以外的 8 格（前 4 块魔力护板、后 4 块淡青护板），虚空石铺在顶面同样的 8 格（只有分支测试用）。
 * 测试数值只把要攒的秒数从 900 改成几秒，其余照默认；在同一段同步代码里设、测、置回，最后清掉仓里生成的聚集体和产物（产物永不消失）。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class CondenseTests {

    private static final String LARGE = "empty_large";

    private static final BlockPos MIN = new BlockPos(3, 1, 3);
    private static final BlockPos MAX = new BlockPos(7, 3, 7);
    private static final BlockPos EC_CONTROLLER = new BlockPos(5, 2, 3);
    private static final BlockPos HOLDING = new BlockPos(3, 2, 5);
    private static final BlockPos BALANCER = new BlockPos(7, 2, 5);
    private static final BlockPos STATE_CHECKER = new BlockPos(5, 1, 5);
    private static final BlockPos CENTER = new BlockPos(5, 2, 5);
    private static final List<BlockPos> PLATING_SPOTS = List.of(
            new BlockPos(4, 1, 4), new BlockPos(5, 1, 4), new BlockPos(6, 1, 4), new BlockPos(4, 1, 5),
            new BlockPos(6, 1, 5), new BlockPos(4, 1, 6), new BlockPos(5, 1, 6), new BlockPos(6, 1, 6));
    private static final List<BlockPos> VOID_SPOTS = List.of(
            new BlockPos(4, 3, 4), new BlockPos(5, 3, 4), new BlockPos(6, 3, 4), new BlockPos(4, 3, 5),
            new BlockPos(6, 3, 5), new BlockPos(4, 3, 6), new BlockPos(5, 3, 6), new BlockPos(6, 3, 6));

    /** 默认数值，只把要攒的进度从 900 秒改成 3 秒 */
    private static final CondenseValues QUICK = new CondenseValues(true, 50_000, 3, 8, 15_000, 0.8D, 1.2D, 1, List.of());
    /** 同上，攒 20 秒（测中途状态时不会凝完） */
    private static final CondenseValues SLOW = new CondenseValues(true, 50_000, 20, 8, 15_000, 0.8D, 1.2D, 1, List.of());
    /** 坍缩的默认数值，只把 1800 秒改成 3 秒（分支测试用） */
    private static final CollapseValues COLLAPSE_QUICK = new CollapseValues(true, 100_000, 3, 3, 8, List.of(ECHoanna.ID.toString()),
            MRUPresenceEntity.MAX_MRU, 0.5D, 1.5D);
    private static final int PRESENCE = 15_000;

    private CondenseTests() {
    }

    // ================================================================ 共用

    private static void shell(GameTestHelper helper, Block block) {
        for (int x = MIN.getX(); x <= MAX.getX(); x++) {
            for (int y = MIN.getY(); y <= MAX.getY(); y++) {
                for (int z = MIN.getZ(); z <= MAX.getZ(); z++) {
                    if (x == MIN.getX() || x == MAX.getX() || y == MIN.getY() || y == MAX.getY() || z == MIN.getZ() || z == MAX.getZ()) {
                        helper.setBlock(new BlockPos(x, y, z), block);
                    }
                }
            }
        }
    }

    private static Block plating(int index) {
        return index < 4 ? ECAlloys.MAGIC_PLATING.get() : ECAlloys.PALE_PLATING.get();
    }

    /** 搭一座仓：plating 块护板、voidStones 块虚空石、要不要稳定器；成形、存 6 万 MRU、平衡值 1.0（纯净） */
    private static EnrichmentControllerBlockEntity chamber(GameTestHelper helper, int plating, int voidStones, boolean stabilizer) {
        Block stone = ECMaterials.FORTIFIED_STONE.get();
        shell(helper, stone);
        helper.setBlock(EC_CONTROLLER, ECEnrichment.EC_CONTROLLER.get());
        helper.setBlock(HOLDING, ECEnrichment.EC_HOLDING_CHAMBER.get());
        helper.setBlock(BALANCER, stabilizer ? ECPentacle.EC_BALANCER.get() : stone);
        helper.setBlock(STATE_CHECKER, ECEnrichment.EC_STATE_CHECKER.get());
        for (int i = 0; i < plating; i++) {
            helper.setBlock(PLATING_SPOTS.get(i), plating(i));
        }
        for (int i = 0; i < voidStones; i++) {
            helper.setBlock(VOID_SPOTS.get(i), ECVoid.VOID_STONE.get());
        }
        EnrichmentControllerBlockEntity controller = (EnrichmentControllerBlockEntity) helper.getBlockEntity(EC_CONTROLLER);
        helper.assertTrue(controller.checkNow() == StructureResult.MATCH, "仓应成形（护板也是富集仓外壳）");
        helper.assertTrue(controller.condenseCasing() == plating, "外壳里的护板应数出 " + plating + " 块，实际 " + controller.condenseCasing());
        controller.storage().setMRU(60_000);
        controller.storage().setBalance(1.0F);
        return controller;
    }

    private static EnrichmentControllerBlockEntity chamber(GameTestHelper helper, int plating, boolean stabilizer) {
        return chamber(helper, plating, 0, stabilizer);
    }

    private static MRUPresenceEntity presence(GameTestHelper helper, int mru) {
        Vec3 at = helper.absoluteVec(Vec3.atCenterOf(CENTER));
        MRUPresenceEntity presence = MRUPresenceEntity.create(helper.getLevel(), at.x, at.y, at.z, mru, 1F, false, true);
        helper.getLevel().addFreshEntity(presence);
        return presence;
    }

    private static AABB chamberBox(GameTestHelper helper) {
        return new AABB(helper.absolutePos(MIN), helper.absolutePos(MAX).offset(1, 1, 1));
    }

    private static Item matrix() {
        return ECEnrichment.CONDENSED_MATRIX.get();
    }

    private static List<ItemEntity> products(GameTestHelper helper, Item item) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, chamberBox(helper), e -> e.isAlive() && e.getItem().is(item));
    }

    /** 换上测试数值跑 body，最后置回并清掉仓里的聚集体和物品 */
    private static void condenseCase(GameTestHelper helper, CondenseValues values, Runnable body) {
        ServerLevel level = helper.getLevel();
        EnrichmentControllerBlockEntity.Config.setCondenseTestOverride(values);
        try {
            body.run();
        } finally {
            EnrichmentControllerBlockEntity.Config.setCondenseTestOverride(null);
            AABB box = chamberBox(helper).inflate(1D);
            level.getEntitiesOfClass(ItemEntity.class, box, e -> true).forEach(ItemEntity::discard);
            level.getEntitiesOfClass(MRUPresenceEntity.class, box, e -> true).forEach(MRUPresenceEntity::discard);
        }
    }

    private static void step(GameTestHelper helper, EnrichmentControllerBlockEntity controller, CondenseState expected, int progress, String when) {
        CondenseState state = controller.condenseSecond(List.of());
        helper.assertTrue(state == expected, when + "：状态应为 " + expected + "，实际 " + state);
        helper.assertTrue(controller.condenseProgress() == progress, when + "：进度应为 " + progress + "，实际 " + controller.condenseProgress());
    }

    private static void assertLine(GameTestHelper helper, List<Component> lines, String key, Object[] args) {
        for (Component line : lines) {
            if (line.getContents() instanceof TranslatableContents contents && contents.getKey().equals(key)) {
                if (args != null) {
                    Object[] actual = contents.getArgs();
                    helper.assertTrue(actual.length == args.length, key + " 的参数个数应为 " + args.length);
                    for (int i = 0; i < args.length; i++) {
                        helper.assertTrue(String.valueOf(args[i]).equals(String.valueOf(actual[i])),
                                key + " 第 " + (i + 1) + " 个参数应为 " + args[i] + "，实际 " + actual[i]);
                    }
                }
                return;
            }
        }
        List<String> keys = new ArrayList<>();
        for (Component line : lines) {
            keys.add(line.getContents() instanceof TranslatableContents contents ? contents.getKey() : line.getString());
        }
        throw new GameTestAssertException("应有一行 " + key + "，实际 " + keys);
    }

    // ================================================================ 凝结

    /** 主世界（不是海纳尔）照样凝：攒满 3 秒，聚集体消失、扣 5 万 MRU、仓内正中出 1 个永不消失的凝结矩阵；不出坍缩的产物 */
    @GameTest(template = LARGE)
    public static void condenseProducesMatrixAnywhere(GameTestHelper helper) {
        EnrichmentControllerBlockEntity controller = chamber(helper, 8, true);
        helper.assertFalse(ECHoanna.isHoanna(helper.getLevel()), "前提：测试世界不是海纳尔");
        condenseCase(helper, QUICK, () -> {
            MRUPresenceEntity cu = presence(helper, PRESENCE);
            step(helper, controller, CondenseState.CONDENSING, 1, "第 1 秒");
            step(helper, controller, CondenseState.CONDENSING, 2, "第 2 秒");
            helper.assertTrue(cu.isStabilized(), "有稳定器：仓里的聚集体应被稳定");
            helper.assertTrue(products(helper, matrix()).isEmpty(), "没攒满前不出产物");
            controller.condenseSecond(List.of());
            helper.assertTrue(controller.condenseProgress() == 0 && controller.condenseTarget() == null, "凝完进度归零、不再认准那团");
            helper.assertFalse(cu.isAlive(), "凝结的那团聚集体应消失");
            helper.assertTrue(controller.storage().getMRU() == 10_000, "仓里应扣掉 5 万 MRU，剩 10000，实际 " + controller.storage().getMRU());
            List<ItemEntity> made = products(helper, matrix());
            helper.assertTrue(made.size() == 1 && made.get(0).getItem().getCount() == 1, "仓里应出现 1 个凝结矩阵，实际 " + made.size());
            Vec3 center = helper.absoluteVec(Vec3.atCenterOf(CENTER));
            helper.assertTrue(made.get(0).position().distanceTo(center) < 0.6D, "凝结矩阵应在仓内正中");
            helper.assertTrue(made.get(0).getAge() < 0, "产物应永不消失（年龄为负）");
            helper.assertTrue(products(helper, ECVoid.VOID_CORE.get()).isEmpty() && products(helper, ECVoid.VOID_MRU_REACTOR.get()).isEmpty(),
                    "不出坍缩的产物");
            step(helper, controller, CondenseState.NO_PRESENCE, 0, "凝完以后");
        });
        helper.succeed();
    }

    /** 平衡值窗口两头含：0.79 不走、0.8 走、1.2 走、1.21 不走；卡住时进度停住不清零 */
    @GameTest(template = LARGE)
    public static void condenseBalanceWindowEdges(GameTestHelper helper) {
        EnrichmentControllerBlockEntity controller = chamber(helper, 8, true);
        helper.assertFalse(EnrichmentControllerBlockEntity.inCondenseBalance(0.79F, SLOW), "0.79 在窗口外");
        helper.assertTrue(EnrichmentControllerBlockEntity.inCondenseBalance(0.8F, SLOW), "0.8 在窗口里");
        helper.assertTrue(EnrichmentControllerBlockEntity.inCondenseBalance(1.2F, SLOW), "1.2 在窗口里（float 的 1.2 比 1.2 略大也算）");
        helper.assertFalse(EnrichmentControllerBlockEntity.inCondenseBalance(1.21F, SLOW), "1.21 在窗口外");
        condenseCase(helper, SLOW, () -> {
            presence(helper, PRESENCE);
            controller.storage().setBalance(0.79F);
            step(helper, controller, CondenseState.BALANCE, 0, "平衡值 0.79");
            controller.storage().setBalance(0.8F);
            step(helper, controller, CondenseState.CONDENSING, 1, "平衡值 0.8");
            controller.storage().setBalance(1.2F);
            step(helper, controller, CondenseState.CONDENSING, 2, "平衡值 1.2");
            controller.storage().setBalance(1.21F);
            step(helper, controller, CondenseState.BALANCE, 2, "平衡值 1.21：停住不清零");
            assertLine(helper, controller.describe(StructureResult.MATCH), "message.essentialcraft5.ec_controller.condense.balance", null);
        });
        helper.succeed();
    }

    /** 外壳里护板合计 7 块不走、8 块（魔力、淡青各 4）走；护板在 condense_casing 标签里 */
    @GameTest(template = LARGE)
    public static void condenseNeedsEightPlating(GameTestHelper helper) {
        helper.assertTrue(ECAlloys.MAGIC_PLATING.get().defaultBlockState().is(ECTags.CONDENSE_CASING)
                        && ECAlloys.PALE_PLATING.get().defaultBlockState().is(ECTags.CONDENSE_CASING),
                "魔力护板、淡青护板应在 essentialcraft5:condense_casing 标签里");
        helper.assertFalse(ECMaterials.FORTIFIED_STONE.get().defaultBlockState().is(ECTags.CONDENSE_CASING), "强化石不算");
        EnrichmentControllerBlockEntity controller = chamber(helper, 7, true);
        condenseCase(helper, SLOW, () -> {
            presence(helper, PRESENCE);
            step(helper, controller, CondenseState.CASING, 0, "护板 7 块");
            assertLine(helper, controller.describe(StructureResult.MATCH), "message.essentialcraft5.ec_controller.condense.casing", new Object[] {7, 8});
            helper.setBlock(PLATING_SPOTS.get(7), plating(7));
            helper.assertTrue(controller.checkNow() == StructureResult.MATCH && controller.condenseCasing() == 8, "补一块淡青护板后应 8 块");
            step(helper, controller, CondenseState.CONDENSING, 1, "护板 8 块");
        });
        helper.succeed();
    }

    /** 缺任一条件都不走：关掉、没有稳定器、聚集体 14999、仓里 49999、维度名单不含这里；都齐了才走 */
    @GameTest(template = LARGE)
    public static void condenseNeedsEveryCondition(GameTestHelper helper) {
        EnrichmentControllerBlockEntity controller = chamber(helper, 8, true);
        condenseCase(helper, SLOW, () -> {
            MRUPresenceEntity cu = presence(helper, PRESENCE);

            EnrichmentControllerBlockEntity.Config.setCondenseTestOverride(
                    new CondenseValues(false, 50_000, 20, 8, 15_000, 0.8D, 1.2D, 1, List.of()));
            step(helper, controller, CondenseState.DISABLED, 0, "关掉凝结");
            EnrichmentControllerBlockEntity.Config.setCondenseTestOverride(
                    new CondenseValues(true, 50_000, 20, 8, 15_000, 0.8D, 1.2D, 1, List.of(ECHoanna.ID.toString())));
            step(helper, controller, CondenseState.WRONG_DIMENSION, 0, "维度名单只有海纳尔");
            EnrichmentControllerBlockEntity.Config.setCondenseTestOverride(SLOW);

            helper.setBlock(BALANCER, ECMaterials.FORTIFIED_STONE.get());
            helper.assertTrue(controller.checkNow() == StructureResult.MATCH && controller.stabilizers() == 0, "拆掉稳定器后应没有稳定器");
            step(helper, controller, CondenseState.NO_STABILIZER, 0, "没有稳定器");
            helper.setBlock(BALANCER, ECPentacle.EC_BALANCER.get());
            helper.assertTrue(controller.checkNow() == StructureResult.MATCH && controller.stabilizers() == 1, "补回稳定器");

            cu.getStorage().setMRU(PRESENCE - 1);
            step(helper, controller, CondenseState.PRESENCE_LOW, 0, "聚集体 14999");
            assertLine(helper, controller.describe(StructureResult.MATCH), "message.essentialcraft5.ec_controller.condense.presence_low",
                    new Object[] {PRESENCE - 1, PRESENCE});
            cu.getStorage().setMRU(PRESENCE);

            controller.storage().setMRU(49_999);
            step(helper, controller, CondenseState.LOW_MRU, 0, "仓里 49999 MRU");
            controller.storage().setMRU(50_000);

            step(helper, controller, CondenseState.CONDENSING, 1, "条件都齐了");
            helper.assertTrue(cu.isAlive() && products(helper, matrix()).isEmpty(), "还没攒满：聚集体还在、没有产物");
        });
        helper.succeed();
    }

    /** 中途外壳缺一块：停住不清零；补好接着走；那团聚集体没了才清零 */
    @GameTest(template = LARGE)
    public static void condensePausesThenResets(GameTestHelper helper) {
        EnrichmentControllerBlockEntity controller = chamber(helper, 8, true);
        condenseCase(helper, SLOW, () -> {
            MRUPresenceEntity cu = presence(helper, PRESENCE);
            step(helper, controller, CondenseState.CONDENSING, 1, "第 1 秒");
            step(helper, controller, CondenseState.CONDENSING, 2, "第 2 秒");
            BlockPos gap = new BlockPos(5, 2, 7);
            BlockState saved = helper.getLevel().getBlockState(helper.absolutePos(gap));
            helper.setBlock(gap, Blocks.AIR);
            helper.assertTrue(controller.checkNow() != StructureResult.MATCH, "缺一块外壳应不成形");
            step(helper, controller, CondenseState.NOT_FORMED, 2, "外壳缺一块");
            assertLine(helper, controller.describe(controller.checkNow()), "message.essentialcraft5.ec_controller.condense.kept", new Object[] {2, SLOW.seconds()});
            helper.setBlock(gap, saved);
            helper.assertTrue(controller.checkNow() == StructureResult.MATCH, "补上后应成形");
            step(helper, controller, CondenseState.CONDENSING, 3, "外壳补好");
            cu.discard();
            step(helper, controller, CondenseState.NO_PRESENCE, 0, "聚集体没了");
        });
        helper.succeed();
    }

    /** 那一堆满一组时停在 OUTPUT_FULL：不扣 MRU、聚集体还在；拿走一个后下一秒凝结、凑回 64 */
    @GameTest(template = LARGE)
    public static void condenseStopsWhenOutputFull(GameTestHelper helper) {
        EnrichmentControllerBlockEntity controller = chamber(helper, 8, true);
        condenseCase(helper, QUICK, () -> {
            Vec3 at = helper.absoluteVec(Vec3.atCenterOf(CENTER));
            ItemEntity pile = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, new ItemStack(matrix(), 64), 0D, 0D, 0D);
            pile.setUnlimitedLifetime();
            helper.getLevel().addFreshEntity(pile);
            MRUPresenceEntity cu = presence(helper, PRESENCE);
            step(helper, controller, CondenseState.CONDENSING, 1, "第 1 秒");
            step(helper, controller, CondenseState.CONDENSING, 2, "第 2 秒");
            step(helper, controller, CondenseState.OUTPUT_FULL, QUICK.seconds(), "攒满、那一堆满一组");
            helper.assertTrue(cu.isAlive() && controller.storage().getMRU() == 60_000, "满一组时不凝：聚集体还在、不扣 MRU");
            assertLine(helper, controller.describe(StructureResult.MATCH), "message.essentialcraft5.ec_controller.condense.output_full", null);
            step(helper, controller, CondenseState.OUTPUT_FULL, QUICK.seconds(), "再推一秒");

            pile.setItem(pile.getItem().copyWithCount(63));
            controller.condenseSecond(List.of());
            helper.assertFalse(cu.isAlive(), "拿走一个以后下一秒凝结");
            List<ItemEntity> after = products(helper, matrix());
            helper.assertTrue(after.size() == 1 && after.get(0) == pile && pile.getItem().getCount() == 64,
                    "产物加进那一堆、凑回 64，实际 " + after.size() + " 堆 / " + pile.getItem().getCount());
            helper.assertTrue(controller.storage().getMRU() == 10_000, "扣掉 5 万");
        });
        helper.succeed();
    }

    /**
     * 海纳尔里同一座仓（护板、虚空石都够）按平衡值分支：1.0 凝出凝结矩阵（坍缩那支停在平衡值）；0.4 坍缩出虚空核心（凝结那支停在平衡值）。
     * 两支走 tick 用的同一个入口（一秒先坍缩后凝结），产物各堆各的。
     */
    @GameTest(template = LARGE)
    public static void condenseAndCollapseSplitByBalance(GameTestHelper helper) {
        EnrichmentControllerBlockEntity controller = chamber(helper, 8, 8, true);
        controller.storage().setMRU(160_000);
        ServerLevel level = helper.getLevel();
        ECHoanna.setTestHoannaOverride(level);
        EnrichmentControllerBlockEntity.Config.setCollapseTestOverride(COLLAPSE_QUICK);
        // 固定在新月（潮位 0，坍缩 ×1），坍缩那支按每秒 +1 数
        HoannaTide.setTestOverride(0F);
        try {
            condenseCase(helper, QUICK, () -> {
                MRUPresenceEntity first = presence(helper, MRUPresenceEntity.MAX_MRU);
                for (int i = 0; i < 3; i++) {
                    controller.chamberSecond(List.of());
                }
                helper.assertFalse(first.isAlive(), "平衡值 1.0：凝结吃掉那团");
                helper.assertTrue(controller.collapseState() == CollapseState.BALANCE && controller.collapseProgress() == 0,
                        "坍缩那支停在平衡值，实际 " + controller.collapseState() + " / " + controller.collapseProgress());
                helper.assertTrue(products(helper, matrix()).size() == 1 && products(helper, ECVoid.VOID_CORE.get()).isEmpty(), "只出凝结矩阵");
                helper.assertTrue(controller.storage().getMRU() == 110_000, "扣 5 万，实际 " + controller.storage().getMRU());

                controller.storage().setBalance(0.4F);
                MRUPresenceEntity second = presence(helper, MRUPresenceEntity.MAX_MRU);
                for (int i = 0; i < 2; i++) {
                    controller.chamberSecond(List.of());
                }
                helper.assertTrue(controller.condenseState() == CondenseState.BALANCE && controller.condenseProgress() == 0,
                        "凝结那支停在平衡值，实际 " + controller.condenseState() + " / " + controller.condenseProgress());
                helper.assertTrue(controller.collapseState() == CollapseState.FROZEN && controller.collapseProgress() == 2, "坍缩那支在走");
                controller.chamberSecond(List.of());
                helper.assertFalse(second.isAlive(), "平衡值 0.4：第 3 秒坍缩吃掉那团");
                helper.assertTrue(controller.condenseState() == CondenseState.NO_PRESENCE && controller.condenseProgress() == 0,
                        "同一秒坍缩在前，凝结那支按聚集体没了算，实际 " + controller.condenseState());
                helper.assertTrue(products(helper, ECVoid.VOID_CORE.get()).size() == 1, "出虚空核心");
                List<ItemEntity> matrices = products(helper, matrix());
                helper.assertTrue(matrices.size() == 1 && matrices.get(0).getItem().getCount() == 1, "凝结矩阵那一堆不变");
            });
        } finally {
            HoannaTide.setTestOverride(null);
            EnrichmentControllerBlockEntity.Config.setCollapseTestOverride(null);
            ECHoanna.clearTestOverride();
        }
        helper.succeed();
    }

    /** 不吃天灾：默认倍数 1，海纳尔天灾里每秒照样 +1；倍数改成 3 时才 +3，说明行写出倍数 */
    @GameTest(template = LARGE)
    public static void condenseIgnoresHoannaEvents(GameTestHelper helper) {
        EnrichmentControllerBlockEntity controller = chamber(helper, 8, true);
        ServerLevel level = helper.getLevel();
        ECHoanna.setTestHoannaOverride(level);
        try {
            condenseCase(helper, SLOW, () -> {
                presence(helper, PRESENCE);
                step(helper, controller, CondenseState.CONDENSING, 1, "平时");
                try {
                    helper.assertTrue(HoannaEvents.start(level, HoannaEvent.DARKNESS, 200), "应开始一场黑暗");
                    step(helper, controller, CondenseState.CONDENSING, 2, "天灾中、倍数 1");
                    helper.assertTrue(controller.condenseEvent(), "应记下这一秒在天灾里");
                    assertLine(helper, controller.describe(StructureResult.MATCH), "message.essentialcraft5.ec_controller.condense.progress",
                            new Object[] {2, SLOW.seconds()});
                    EnrichmentControllerBlockEntity.Config.setCondenseTestOverride(
                            new CondenseValues(true, 50_000, 20, 8, 15_000, 0.8D, 1.2D, 3, List.of()));
                    step(helper, controller, CondenseState.CONDENSING, 5, "天灾中、倍数 3");
                    assertLine(helper, controller.describe(StructureResult.MATCH), "message.essentialcraft5.ec_controller.condense.progress_event",
                            new Object[] {5, 20, 3});
                } finally {
                    HoannaEvents.stop(level);
                }
                step(helper, controller, CondenseState.CONDENSING, 6, "天灾结束后");
                helper.assertFalse(controller.condenseEvent(), "天灾结束后不再按倍数");
            });
        } finally {
            ECHoanna.clearTestOverride();
        }
        helper.succeed();
    }

    /** 状态检测器的界面数据和右键控制器的说明：护板不够（带数）、聚集体不满（带数）、正在凝结和进度；坍缩那一条在主世界不显示 */
    @GameTest(template = LARGE)
    public static void condenseStatusReadout(GameTestHelper helper) {
        EnrichmentControllerBlockEntity controller = chamber(helper, 7, true);
        ServerPlayer viewer = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-condense-view");
        EnrichmentStateCheckerBlockEntity checker = (EnrichmentStateCheckerBlockEntity) helper.getBlockEntity(STATE_CHECKER);
        EnrichmentStateCheckerMenu menu = new EnrichmentStateCheckerMenu(0, viewer.getInventory(), checker);
        condenseCase(helper, QUICK, () -> {
            MRUPresenceEntity cu = presence(helper, PRESENCE - 1);
            // tick 用的入口：坍缩那支在主世界判成维度不对，凝结那支卡在护板
            controller.chamberSecond(List.of());
            helper.assertTrue(controller.collapseState() == CollapseState.WRONG_DIMENSION, "坍缩那支在主世界不走，实际 " + controller.collapseState());
            helper.assertTrue(checker.isLinked() && menu.showsCondense(), "状态检测器应连着仓、显示凝结那一条");
            helper.assertTrue(menu.condenseState() == CondenseState.CASING && menu.condenseCasing() == 7,
                    "界面数据：护板不够、数出 7 块，实际 " + menu.condenseState() + " / " + menu.condenseCasing());
            helper.assertFalse(menu.showsCollapse(), "主世界里不显示坍缩那一条");

            helper.setBlock(PLATING_SPOTS.get(7), plating(7));
            helper.assertTrue(controller.checkNow() == StructureResult.MATCH, "补上护板");
            controller.condenseSecond(List.of());
            helper.assertTrue(menu.condenseState() == CondenseState.PRESENCE_LOW && menu.condensePresenceMRU() == PRESENCE - 1,
                    "界面数据：聚集体不满、存量 14999，实际 " + menu.condenseState() + " / " + menu.condensePresenceMRU());

            cu.getStorage().setMRU(PRESENCE);
            controller.condenseSecond(List.of());
            helper.assertTrue(menu.condenseState() == CondenseState.CONDENSING && menu.condenseProgress() == 1
                            && menu.condenseSeconds() == QUICK.seconds() && !menu.condenseEvent(),
                    "界面数据：正在凝结、进度 1 / 3");
            List<Component> lines = controller.describe(StructureResult.MATCH);
            assertLine(helper, lines, "message.essentialcraft5.ec_controller.condense.condensing", null);
            assertLine(helper, lines, "message.essentialcraft5.ec_controller.condense.progress", new Object[] {1, QUICK.seconds()});
            List<Component> tooltip = EnrichmentControllerBlockEntity.condenseLines(menu.condenseState(), menu.condenseProgress(),
                    menu.condenseSeconds(), menu.condenseEvent(), menu.condenseCasing(), menu.condensePresenceMRU(),
                    controller.storage().getMRU(), controller.storage().getBalance(), QUICK);
            assertLine(helper, tooltip, "message.essentialcraft5.ec_controller.condense.condensing", null);
        });
        menu.removed(viewer);
        helper.succeed();
    }

    /** 进度、认准的聚集体、护板数存盘；读档后接着走到凝结 */
    @GameTest(template = LARGE)
    public static void condenseSurvivesReload(GameTestHelper helper) {
        EnrichmentControllerBlockEntity controller = chamber(helper, 8, true);
        condenseCase(helper, QUICK, () -> {
            MRUPresenceEntity cu = presence(helper, PRESENCE);
            step(helper, controller, CondenseState.CONDENSING, 1, "第 1 秒");
            step(helper, controller, CondenseState.CONDENSING, 2, "第 2 秒");
            CompoundTag tag = controller.saveWithFullMetadata();
            BlockPos abs = helper.absolutePos(EC_CONTROLLER);
            BlockEntity loaded = BlockEntity.loadStatic(abs, helper.getLevel().getBlockState(abs), tag);
            helper.assertTrue(loaded instanceof EnrichmentControllerBlockEntity fresh
                            && fresh.condenseProgress() == 2 && cu.getUUID().equals(fresh.condenseTarget()) && fresh.condenseCasing() == 8,
                    "读出来的应是进度 2、认准的还是那团聚集体、护板 8 块");
            controller.load(tag);
            helper.assertTrue(controller.condenseProgress() == 2, "原地读档后进度 2");
            controller.condenseSecond(List.of());
            helper.assertFalse(cu.isAlive(), "读档后接着走：第 3 秒凝结");
            helper.assertTrue(products(helper, matrix()).size() == 1, "应出 1 个凝结矩阵");
        });
        helper.succeed();
    }

    /** 世界不动：仓的四周全圈成别人的领地，凝结照常完成，没有破坏 / 放置事件，一格方块都没变 */
    @GameTest(template = LARGE)
    public static void condenseLeavesWorldUntouched(GameTestHelper helper) {
        EnrichmentControllerBlockEntity controller = chamber(helper, 8, true);
        BoundingBox box = BoundingBox.fromCorners(helper.absolutePos(MIN).offset(-1, -1, -1), helper.absolutePos(MAX).offset(1, 1, 1));
        Map<BlockPos, BlockState> before = new HashMap<>();
        Consumer<Map<BlockPos, BlockState>> snapshot = map -> BlockPos.betweenClosedStream(box)
                .forEach(pos -> map.put(pos.immutable(), helper.getLevel().getBlockState(pos)));
        snapshot.accept(before);
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper)) {
            claims.denyBox(box);
            condenseCase(helper, QUICK, () -> {
                presence(helper, PRESENCE);
                for (int i = 0; i < QUICK.seconds(); i++) {
                    controller.condenseSecond(List.of());
                }
                helper.assertTrue(products(helper, matrix()).size() == 1, "领地拒绝所有格子也照常凝结（不动方块、不问权限）");
            });
            int breaks = 0;
            int places = 0;
            for (BlockPos pos : before.keySet()) {
                breaks += claims.breaksAt(pos);
                places += claims.placesAt(pos);
            }
            helper.assertTrue(claims.placeEvents() == 0 && breaks == 0 && places == 0, "不应收到破坏 / 放置事件");
        }
        Map<BlockPos, BlockState> after = new HashMap<>();
        snapshot.accept(after);
        helper.assertTrue(before.equals(after), "凝结前后方块应一格不变");
        helper.succeed();
    }

    // ================================================================ 产物被拦、实体没读完、认准哪团

    private static MRUPresenceEntity presenceAt(GameTestHelper helper, BlockPos relative, int mru) {
        Vec3 at = helper.absoluteVec(Vec3.atCenterOf(relative));
        MRUPresenceEntity presence = MRUPresenceEntity.create(helper.getLevel(), at.x, at.y, at.z, mru, 1F, false, true);
        helper.getLevel().addFreshEntity(presence);
        return presence;
    }

    /**
     * 产物生成被拦（别的模组取消加入事件，或者在加入事件里直接移除、addFreshEntity 仍返回 true）：两种都什么都不扣，
     * 聚集体还在、MRU 不少、进度停在满格、还认准那团，状态 BLOCKED，右键控制器写出来；不拦以后下一秒照常凝出。
     * 监听器只认落在这座仓里的凝结矩阵，摘掉之前别的测试不受影响。
     */
    @GameTest(template = LARGE)
    public static void condenseSpawnBlockedKeepsEverything(GameTestHelper helper) {
        EnrichmentControllerBlockEntity controller = chamber(helper, 8, true);
        AABB box = chamberBox(helper);
        Consumer<EntityJoinLevelEvent> cancel = event -> {
            if (event.getEntity() instanceof ItemEntity item && item.getItem().is(matrix()) && box.contains(item.position())) {
                event.setCanceled(true);
            }
        };
        Consumer<EntityJoinLevelEvent> discard = event -> {
            if (event.getEntity() instanceof ItemEntity item && item.getItem().is(matrix()) && box.contains(item.position())) {
                item.discard();
            }
        };
        condenseCase(helper, QUICK, () -> {
            MRUPresenceEntity cu = presence(helper, PRESENCE);
            step(helper, controller, CondenseState.CONDENSING, 1, "第 1 秒");
            step(helper, controller, CondenseState.CONDENSING, 2, "第 2 秒");
            for (Consumer<EntityJoinLevelEvent> deny : List.of(cancel, discard)) {
                String what = deny == cancel ? "加入事件被取消" : "加入时就被移除";
                MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EntityJoinLevelEvent.class, deny);
                try {
                    step(helper, controller, CondenseState.BLOCKED, QUICK.seconds(), what);
                } finally {
                    MinecraftForge.EVENT_BUS.unregister(deny);
                }
                helper.assertTrue(cu.isAlive() && controller.storage().getMRU() == 60_000,
                        what + "：聚集体还在、不扣 MRU，实际 " + controller.storage().getMRU());
                helper.assertTrue(cu.getUUID().equals(controller.condenseTarget()), what + "：还认准那团");
                helper.assertTrue(products(helper, matrix()).isEmpty(), what + "：仓里没有凝结矩阵");
                assertLine(helper, controller.describe(StructureResult.MATCH), "message.essentialcraft5.ec_controller.condense.blocked", null);
            }
            controller.condenseSecond(List.of());
            helper.assertFalse(cu.isAlive(), "不拦以后下一秒凝结");
            List<ItemEntity> made = products(helper, matrix());
            helper.assertTrue(made.size() == 1 && made.get(0).getItem().getCount() == 1 && made.get(0).getAge() < 0,
                    "出 1 个永不消失的凝结矩阵，实际 " + made.size());
            helper.assertTrue(controller.storage().getMRU() == 10_000 && controller.condenseProgress() == 0, "扣掉 5 万、进度归零");
        });
        helper.succeed();
    }

    /**
     * 外壳区块的实体没读完（LOADING）：这时把聚集体挪出仓外（查也查不到）也不判丢，进度、认准的那团都不动，右键写出来；
     * 读完、聚集体回到仓里以后接着走。
     */
    @GameTest(template = LARGE)
    public static void condenseWaitsWhileShellEntitiesLoad(GameTestHelper helper) {
        EnrichmentControllerBlockEntity controller = chamber(helper, 8, true);
        condenseCase(helper, SLOW, () -> {
            MRUPresenceEntity cu = presence(helper, PRESENCE);
            step(helper, controller, CondenseState.CONDENSING, 1, "第 1 秒");
            step(helper, controller, CondenseState.CONDENSING, 2, "第 2 秒");
            Vec3 inside = cu.position();
            Vec3 outside = helper.absoluteVec(new Vec3(5.5D, 2.5D, 11.5D));
            EnrichmentControllerBlockEntity.setShellEntitiesLoadedTestOverride(false);
            try {
                cu.setPos(outside.x, outside.y, outside.z);
                step(helper, controller, CondenseState.LOADING, 2, "实体没读完");
                step(helper, controller, CondenseState.LOADING, 2, "实体没读完，再推一秒");
                helper.assertTrue(cu.getUUID().equals(controller.condenseTarget()), "没读完时不判丢：还认准那团");
                List<Component> lines = controller.describe(StructureResult.MATCH);
                assertLine(helper, lines, "message.essentialcraft5.ec_controller.condense.loading", null);
                assertLine(helper, lines, "message.essentialcraft5.ec_controller.condense.progress", new Object[] {2, SLOW.seconds()});
            } finally {
                EnrichmentControllerBlockEntity.setShellEntitiesLoadedTestOverride(null);
                cu.setPos(inside.x, inside.y, inside.z);
            }
            step(helper, controller, CondenseState.CONDENSING, 3, "读完以后接着走");
        });
        helper.succeed();
    }

    /**
     * 仓里两团时认准存量多的那团；开始以后另一团变得更多也不换；认准的那团被另一团吞并后进度清零，从头算、改认吞了它的那团。
     * 两团分开放在仓里两个角上（不在同一格，它们自己不会互相吞）。
     */
    @GameTest(template = LARGE)
    public static void condenseTargetsLargestAndSticks(GameTestHelper helper) {
        EnrichmentControllerBlockEntity controller = chamber(helper, 8, true);
        condenseCase(helper, SLOW, () -> {
            MRUPresenceEntity small = presenceAt(helper, new BlockPos(4, 2, 4), 16_000);
            MRUPresenceEntity big = presenceAt(helper, new BlockPos(6, 2, 6), 18_000);
            step(helper, controller, CondenseState.CONDENSING, 1, "第 1 秒");
            helper.assertTrue(big.getUUID().equals(controller.condenseTarget()), "两团时认准存量多的那团（1.8 万）");
            small.getStorage().setMRU(19_000);
            step(helper, controller, CondenseState.CONDENSING, 2, "另一团涨到 1.9 万");
            helper.assertTrue(big.getUUID().equals(controller.condenseTarget()), "开始以后不换目标");
            small.absorb(big);
            helper.assertFalse(big.isAlive(), "前提：认准的那团被吞掉");
            step(helper, controller, CondenseState.CONDENSING, 1, "认准的那团被吞并：清零后从头算");
            helper.assertTrue(small.getUUID().equals(controller.condenseTarget()), "改认吞了它的那团");
        });
        helper.succeed();
    }

    /** 凝结矩阵：注册、稀有度、两行说明；手册进度 guide/pentacle/condensed 在 */
    @GameTest(template = "empty")
    public static void condensedMatrixItem(GameTestHelper helper) {
        ItemStack stack = new ItemStack(matrix());
        helper.assertTrue(stack.getRarity() == Rarity.UNCOMMON, "凝结矩阵应是罕见（黄色名字）");
        List<Component> tooltip = new ArrayList<>();
        matrix().appendHoverText(stack, helper.getLevel(), tooltip, TooltipFlag.NORMAL);
        assertLine(helper, tooltip, "tooltip.essentialcraft5.condensed_matrix.source", null);
        assertLine(helper, tooltip, "tooltip.essentialcraft5.condensed_matrix.use", null);
        Advancement advancement = helper.getLevel().getServer().getAdvancements().getAdvancement(EssentialCraft5.id("guide/pentacle/condensed"));
        helper.assertTrue(advancement != null, "应有手册进度 guide/pentacle/condensed");
        helper.succeed();
    }
}
