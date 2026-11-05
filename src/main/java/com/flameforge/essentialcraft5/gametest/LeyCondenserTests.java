package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IESPEHandler;
import com.flameforge.essentialcraft5.blockentity.LeyCondenserBlockEntity;
import com.flameforge.essentialcraft5.blockentity.LeyCondenserBlockEntity.Status;
import com.flameforge.essentialcraft5.blockentity.LeyCondenserBlockEntity.Target;
import com.flameforge.essentialcraft5.blockentity.LeyProjectorBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MithrilineCrystalPillarBlockEntity;
import com.flameforge.essentialcraft5.espe.ESPEStorage;
import com.flameforge.essentialcraft5.espe.ESPETransfer;
import com.flameforge.essentialcraft5.menu.LeyCondenserMenu;
import com.flameforge.essentialcraft5.recipe.OreSmeltingRecipe;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECLeyCondenser;
import com.flameforge.essentialcraft5.registry.ECLeyProjector;
import com.flameforge.essentialcraft5.registry.ECMithriline;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 地脉凝聚器（ECLeyCondenser，第九批 batch9.md §9.1）的 GameTest：
 * <ul>
 *   <li>产量与成本：每个正好 T 刻、正好 M MRU（边做边扣，任何时刻已扣 = 出货数 × M + ⌊M·进度 / T⌋）；钱不够停在原处、补上接着走；
 *       每一步的整数分摊走满正好 M；配置把用时改短时直接出货、不补钱。</li>
 *   <li>下界之星：没有 ESPE 不开工、不动 MRU；只从隔一格一圈的 8 个位置抽（第 9 个位置的柱子不动，8 个位置两两不同、都在那一圈上）；
 *       凝普通矿不抽柱子；红石暂停、输出满、被名额挡住时也不抽；对外只进不出。</li>
 *   <li>停与换：输出格放不下一个就停（不扣、不攒待放），腾出地方接着做；换目标进度清零、不退；红石停在原处；不收的目标点不了、
 *       按钮号越界被拒、点已选中的是取消；用时 0 = 不收；每步超过容量 = 容量不够。</li>
 *   <li>地脉：没有地脉时只能凝下界之星；GameTest 世界（主世界）、下界、海纳尔有地脉，末地没有（和投影仪同一个判断）。</li>
 *   <li>台数：同区块同主人后放的排队（指出挡路那台）；全服同主人只有最早的 4 台工作，拆掉一台或取消它的目标，第 5 台 120 刻内接上；
 *       做到一半被更早的拿回名额：5 刻内停在原处、不扣，名额回来从原进度接着做；倒着给目标、更早那台的区块重新加载，
 *       同时在工作的都不超过上限（最多 1 刻）；两个主人互不影响；专用服上没有主人的不工作。</li>
 *   <li>不能复制：做到一半拆掉只掉出输出格里原有的东西；先清空再移除什么都不掉；存读档进度、目标、放下时间一致，不多出货；
 *       产物白名单里没有燃料、没有本模组的物品；产物进本模组的矿石加工都不算矿石方块、一个最多出一个岩浆合金（远古残骸不收）。</li>
 *   <li>世界不动：四周铺石头、铁矿石、带东西的箱子，模拟领地拒绝所有格子，工作 200 刻：没有任何破坏 / 放置事件，每格不变，没有掉落物。</li>
 *   <li>配方与标签：配方吃投影仪和 #math_cores，标签正好四种数学核心；电脑引擎配方仍要四种指定的核心。</li>
 * </ul>
 * 每个测试的主人都是新的随机 UUID（相邻的测试结构可能在同一区块、名额表是全服共用的，同主人才会互相挡）。
 * 价格、地脉、台数都用方块实体的实例钩子，不改真配置。机器的 MRU 在放下时加满，不放绑定宝石。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class LeyCondenserTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    private static final BlockPos CENTER = new BlockPos(2, 1, 2);
    private static final BlockPos LARGE_CENTER = new BlockPos(5, 1, 5);

    private LeyCondenserTests() {
    }

    // ---------------- 工具 ----------------

    /** 放一台凝聚器：主人（null = 没有主人）、放下时间，MRU 加满 */
    private static LeyCondenserBlockEntity place(GameTestHelper helper, BlockPos rel, @Nullable UUID owner, long placedTick) {
        helper.setBlock(rel, ECLeyCondenser.LEY_CONDENSER.get());
        LeyCondenserBlockEntity be = (LeyCondenserBlockEntity) helper.getBlockEntity(rel);
        if (owner != null) {
            be.setOwner(owner, "condenser-test");
        }
        be.markPlaced(placedTick);
        be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
        return be;
    }

    /** rel 旁边一格、且和 rel 在同一区块（结构可能旋转，按绝对坐标判断） */
    private static BlockPos sameChunkNeighbour(GameTestHelper helper, BlockPos rel) {
        BlockPos abs = helper.absolutePos(rel);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos candidate = rel.relative(direction);
            BlockPos other = helper.absolutePos(candidate);
            if ((other.getX() >> 4) == (abs.getX() >> 4) && (other.getZ() >> 4) == (abs.getZ() >> 4)) {
                return candidate;
            }
        }
        throw new IllegalStateException("四个方向都跨区块，不可能");
    }

    private static int mru(LeyCondenserBlockEntity be) {
        return be.getMRUStorage().getMRU();
    }

    private static double espe(LeyCondenserBlockEntity be) {
        ESPEStorage storage = be.getESPEStorage();
        return storage == null ? -1D : storage.getESPE();
    }

    /** 已扣的 MRU 应等于：出货数 × M + 正在做的那个已付的部分 */
    private static long expectedSpent(int produced, long total, int ticks, int progress) {
        return produced * total + total * progress / ticks;
    }

    private static int countOutput(LeyCondenserBlockEntity be, Item item) {
        int count = 0;
        for (int slot = LeyCondenserBlockEntity.FIRST_OUTPUT; slot < LeyCondenserBlockEntity.SLOTS; slot++) {
            ItemStack stack = be.getInventory().getStackInSlot(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int itemsAround(GameTestHelper helper, BlockPos rel, Item item) {
        AABB box = new AABB(helper.absolutePos(rel)).inflate(3D);
        int count = 0;
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
            if (entity.getItem().is(item)) {
                count += entity.getItem().getCount();
            }
        }
        return count;
    }

    /** 放一根米斯莱水晶柱（三格高自动补齐），ESPE 加满，返回底座的 ESPE */
    private static IESPEHandler fullPillar(GameTestHelper helper, BlockPos rel) {
        helper.setBlock(rel, ECMithriline.MITHRILINE_CRYSTAL_PILLAR.get());
        helper.assertTrue(helper.getBlockEntity(rel) instanceof MithrilineCrystalPillarBlockEntity, "水晶柱底座应有方块实体：" + rel);
        IESPEHandler handler = helper.getBlockEntity(rel).getCapability(ECCapabilities.ESPE, null)
                .orElseThrow(() -> new IllegalStateException("水晶柱应暴露 ESPE"));
        handler.addESPE(handler.getMaxESPE(), true);
        return handler;
    }

    private static LeyCondenserMenu menu(GameTestHelper helper, LeyCondenserBlockEntity be, ServerPlayer player) {
        return new LeyCondenserMenu(0, player.getInventory(), be);
    }

    // ---------------- 产量与成本 ----------------

    /** 粗铁：每个 40 刻、4 000 MRU；凝 2 个正好 2T 刻左右、已扣 = 2 × 4 000 + 正在做的那个已付的部分；没有任何领地时照常工作 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void condenserRateAndCost(GameTestHelper helper) {
        LeyCondenserBlockEntity be = place(helper, CENTER, UUID.randomUUID(), 0L);
        int ticks = be.ticksFor(Target.RAW_IRON);
        int cost = be.mruCost(Target.RAW_IRON);
        helper.assertTrue(ticks == 40 && cost == 4000, "粗铁默认 40 刻、4000 MRU，实际 " + ticks + " / " + cost);
        helper.assertTrue(be.espeCost(Target.RAW_IRON) == 0, "粗铁不要 ESPE");
        be.setTarget(Target.RAW_IRON);
        int start = mru(be);
        long t0 = helper.getTick();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(be.producedCount() >= 2, "还没凝出 2 个"))
                .thenExecute(() -> {
                    long elapsed = helper.getTick() - t0;
                    helper.assertTrue(be.producedCount() == 2, "应正好 2 个，实际 " + be.producedCount());
                    helper.assertTrue(elapsed >= 2L * ticks && elapsed <= 2L * ticks + 3, "2 个应用 2T 刻左右，实际 " + elapsed);
                    long spent = start - mru(be);
                    long want = expectedSpent(2, cost, ticks, be.progress());
                    helper.assertTrue(spent == want, "已扣应为 " + want + "，实际 " + spent + "（进度 " + be.progress() + "）");
                    helper.assertTrue(countOutput(be, Items.RAW_IRON) == 2, "输出格应正好 2 个粗铁");
                    LeyCondenserMenu menu = menu(helper, be, ProtectionFrameworkTests.realPlayer(helper));
                    helper.assertTrue(menu.maxProgress() == ticks && menu.target() == Target.RAW_IRON, "界面应同步目标和用时");
                })
                .thenSucceed();
    }

    /** MRU 只给半个的量：走到一半停住（缺 MRU）、不出货；补上后接着走完，合计正好扣 4 000 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void condenserPayAsYouGo(GameTestHelper helper) {
        LeyCondenserBlockEntity be = place(helper, CENTER, UUID.randomUUID(), 0L);
        be.getMRUStorage().setMRU(2000);
        be.setTarget(Target.RAW_IRON);
        helper.startSequence()
                .thenExecuteAfter(40, () -> {
                    helper.assertTrue(be.progress() == 20, "钱只够一半，进度应停在 20，实际 " + be.progress());
                    helper.assertTrue(be.status() == Status.NO_MRU, "应是缺 MRU，实际 " + be.status());
                    helper.assertTrue(mru(be) == 0 && be.producedCount() == 0, "应已付完 2000、没出货");
                    be.getMRUStorage().setMRU(2000);
                })
                .thenWaitUntil(() -> helper.assertTrue(be.producedCount() == 1, "补上后应凝出 1 个"))
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(mru(be) == 0, "合计应正好扣 4000，剩 " + mru(be));
                    helper.assertTrue(countOutput(be, Items.RAW_IRON) == 1 && be.producedCount() == 1, "只该有 1 个");
                    helper.assertTrue(be.progress() == 0 && be.status() == Status.NO_MRU, "没钱了不该开始下一个，实际进度 " + be.progress());
                })
                .thenSucceed();
    }

    /** 每一步的整数分摊：任何 M、T 走满都正好是 M，每步不超过 ⌈M / T⌉ */
    @GameTest(template = EMPTY)
    public static void condenserStepCostExact(GameTestHelper helper) {
        long[] totals = {0, 1, 7, 1000, 4000, 32000, 100000, 250000, 500000, 10000, 100_000_000};
        int[] ticks = {1, 3, 10, 20, 40, 160, 600, 1200, 2400, 24000};
        for (long total : totals) {
            for (int t : ticks) {
                long sum = 0;
                long ceil = (total + t - 1) / t;
                for (int p = 0; p < t; p++) {
                    long step = LeyCondenserBlockEntity.stepCost(total, t, p);
                    helper.assertTrue(step >= 0 && step <= ceil, "M=" + total + " T=" + t + " 第 " + p + " 步 " + step + " 超出 0~" + ceil);
                    sum += step;
                }
                helper.assertTrue(sum == total, "M=" + total + " T=" + t + " 走满应正好 " + total + "，实际 " + sum);
            }
        }
        helper.succeed();
    }

    /** 做到一半把用时改短：进度已经够就直接出货，没付到的那部分不补；之后按新价格做 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void condenserShorterTimeDelivers(GameTestHelper helper) {
        LeyCondenserBlockEntity be = place(helper, CENTER, UUID.randomUUID(), 0L);
        be.setTarget(Target.RAW_IRON);
        int[] mruAt = {0};
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(be.progress() >= 30, "进度还没到 30"))
                .thenExecute(() -> {
                    mruAt[0] = mru(be);
                    be.setCostForTest(Target.RAW_IRON, 4000, 0, 20);
                })
                .thenWaitUntil(() -> helper.assertTrue(be.producedCount() == 1, "用时改短后应直接出货"))
                .thenExecute(() -> {
                    long spent = mruAt[0] - mru(be);
                    long want = expectedSpent(0, 4000, 20, be.progress());
                    helper.assertTrue(spent == want, "改短后只该按新价格付下一个的进度（" + want + "），实际 " + spent);
                })
                .thenSucceed();
    }

    // ---------------- 下界之星与 ESPE ----------------

    /**
     * 下界之星：周围没有 ESPE → 缺 ESPE、MRU 不动；第 9 个位置 (3, 0, 0) 的满柱子不被抽；(2, 0, 0) 放满柱子 → 开工、凝出一颗，
     * 那根柱子被抽了；价格钩子 2400 MRU + 240 ESPE + 24 刻，扣得正好
     */
    @GameTest(template = EMPTY_LARGE, timeoutTicks = 300)
    public static void condenserStarNeedsEspe(GameTestHelper helper) {
        LeyCondenserBlockEntity be = place(helper, LARGE_CENTER, UUID.randomUUID(), 0L);
        helper.assertTrue(!Target.NETHER_STAR.needsLey, "下界之星不要地脉");
        helper.assertTrue(be.mruCost(Target.NETHER_STAR) == 500_000 && be.espeCost(Target.NETHER_STAR) == 10_000
                && be.ticksFor(Target.NETHER_STAR) == 2400, "下界之星默认 50 万 MRU + 1 万 ESPE + 2400 刻");
        be.setCostForTest(Target.NETHER_STAR, 2400, 240, 24);
        be.setTarget(Target.NETHER_STAR);
        int full = mru(be);
        IESPEHandler[] far = {null};
        IESPEHandler[] near = {null};
        helper.startSequence()
                .thenExecuteAfter(15, () -> {
                    helper.assertTrue(be.status() == Status.NO_ESPE, "没有 ESPE 应停住，实际 " + be.status());
                    helper.assertTrue(mru(be) == full && be.progress() == 0, "没有 ESPE 不该扣 MRU");
                    far[0] = fullPillar(helper, LARGE_CENTER.offset(3, 0, 0));
                })
                .thenExecuteAfter(30, () -> {
                    helper.assertTrue(be.status() == Status.NO_ESPE && espe(be) == 0D, "第 9 个位置的柱子不该被抽，凝聚器 ESPE " + espe(be));
                    helper.assertTrue(far[0].getESPE() == far[0].getMaxESPE(), "远处的柱子应还是满的");
                    near[0] = fullPillar(helper, LARGE_CENTER.offset(2, 0, 0));
                })
                .thenWaitUntil(() -> helper.assertTrue(be.producedCount() == 1, "放上柱子后应凝出一颗星"))
                .thenExecute(() -> {
                    helper.assertTrue(countOutput(be, Items.NETHER_STAR) == 1, "输出格应有一颗下界之星");
                    helper.assertTrue(near[0].getESPE() < near[0].getMaxESPE() - 100, "隔一格那根柱子应被抽过，剩 " + near[0].getESPE());
                    helper.assertTrue(far[0].getESPE() == far[0].getMaxESPE(), "远处的柱子始终不该被抽");
                    long spent = full - mru(be);
                    long want = expectedSpent(1, 2400, 24, be.progress());
                    helper.assertTrue(spent == want, "MRU 应扣 " + want + "，实际 " + spent);
                })
                .thenSucceed();
    }

    /** 凝普通矿：旁边存满的柱子不被抽，凝聚器 ESPE 一直是 0 */
    @GameTest(template = EMPTY_LARGE, timeoutTicks = 200)
    public static void condenserNoEspePullForOres(GameTestHelper helper) {
        LeyCondenserBlockEntity be = place(helper, LARGE_CENTER, UUID.randomUUID(), 0L);
        IESPEHandler pillar = fullPillar(helper, LARGE_CENTER.offset(0, 0, 2));
        be.setTarget(Target.RAW_IRON);
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(be.producedCount() >= 1, "粗铁应照常凝");
            helper.assertTrue(espe(be) == 0D, "凝粗铁不该抽 ESPE，实际 " + espe(be));
            helper.assertTrue(pillar.getESPE() == pillar.getMaxESPE(), "柱子不该被抽");
            helper.succeed();
        });
    }

    /** ESPE 对外只进不出：按面取 capability 抽不出来，往里加可以；别的机器用 ESPETransfer 也抽不走 */
    @GameTest(template = EMPTY)
    public static void condenserEspeInsertOnly(GameTestHelper helper) {
        LeyCondenserBlockEntity be = place(helper, CENTER, UUID.randomUUID(), 0L);
        ESPEStorage storage = be.getESPEStorage();
        helper.assertTrue(storage != null, "凝聚器应有 ESPE 存储");
        storage.setESPE(500D);
        for (Direction side : Direction.values()) {
            IESPEHandler handler = be.getCapability(ECCapabilities.ESPE, side).orElse(null);
            helper.assertTrue(handler != null, side + " 面应暴露 ESPE（只进）");
            helper.assertTrue(handler.extractESPE(100D, true) == 0D, side + " 面不该抽得出 ESPE");
        }
        IESPEHandler up = be.getCapability(ECCapabilities.ESPE, Direction.UP).orElseThrow(IllegalStateException::new);
        helper.assertTrue(up.addESPE(10D, true) == 0D && Math.abs(storage.getESPE() - 510D) < 1e-9, "应能往里加 ESPE，实际 " + storage.getESPE());
        ESPEStorage bucket = new ESPEStorage(() -> 1_000_000D, 0);
        double pulled = ESPETransfer.pull(helper.getLevel(), helper.absolutePos(CENTER.above(2)), bucket, helper.absolutePos(CENTER));
        helper.assertTrue(pulled == 0D, "别的机器不该抽走凝聚器的 ESPE，实际抽到 " + pulled);
        helper.succeed();
    }

    /**
     * 凝下界之星、旁边一根满柱子：红石暂停时不抽；拆掉红石、输出格塞满时也不抽；腾空输出格后才开始抽（正向对照）
     */
    @GameTest(template = EMPTY_LARGE, timeoutTicks = 300)
    public static void condenserNoEspePullWhilePausedOrFull(GameTestHelper helper) {
        BlockPos torch = LARGE_CENTER.above();
        helper.setBlock(torch, Blocks.REDSTONE_BLOCK);
        LeyCondenserBlockEntity be = place(helper, LARGE_CENTER, UUID.randomUUID(), 0L);
        Item[] fillers = {Items.STONE, Items.DIRT, Items.SAND, Items.GRAVEL, Items.OAK_PLANKS, Items.GLASS, Items.BRICKS, Items.CLAY, Items.WHITE_WOOL};
        for (int i = 0; i < LeyCondenserBlockEntity.OUTPUT_SLOTS; i++) {
            be.getInventory().setStackInSlot(LeyCondenserBlockEntity.FIRST_OUTPUT + i, new ItemStack(fillers[i], 64));
        }
        IESPEHandler pillar = fullPillar(helper, LARGE_CENTER.offset(0, 0, 2));
        be.setCostForTest(Target.NETHER_STAR, 2400, 240, 24);
        be.setTarget(Target.NETHER_STAR);
        int full = mru(be);
        helper.startSequence()
                .thenExecuteAfter(30, () -> {
                    helper.assertTrue(be.status() == Status.POWERED, "应是红石暂停，实际 " + be.status());
                    helper.assertTrue(espe(be) == 0D && pillar.getESPE() == pillar.getMaxESPE(), "红石暂停时不该抽 ESPE，凝聚器 " + espe(be));
                    helper.setBlock(torch, Blocks.AIR);
                })
                .thenExecuteAfter(30, () -> {
                    helper.assertTrue(be.status() == Status.OUTPUT_FULL, "应是输出格已满，实际 " + be.status());
                    helper.assertTrue(espe(be) == 0D && pillar.getESPE() == pillar.getMaxESPE(), "输出满时不该抽 ESPE，凝聚器 " + espe(be));
                    helper.assertTrue(mru(be) == full && be.progress() == 0, "暂停、输出满时都不该扣 MRU");
                    for (int i = 0; i < LeyCondenserBlockEntity.OUTPUT_SLOTS; i++) {
                        be.getInventory().setStackInSlot(LeyCondenserBlockEntity.FIRST_OUTPUT + i, ItemStack.EMPTY);
                    }
                })
                .thenWaitUntil(() -> helper.assertTrue(pillar.getESPE() < pillar.getMaxESPE(), "腾空输出格后应开始抽柱子"))
                .thenSucceed();
    }

    /** 同主人两台、全服 1 台：后放的那台目标是下界之星、旁边一根满柱子，被名额挡着时不抽 ESPE（两台的 ±2 范围不重叠） */
    @GameTest(template = EMPTY_LARGE, timeoutTicks = 200)
    public static void condenserNoEspePullWhenOwnerLimited(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        LeyCondenserBlockEntity first = place(helper, new BlockPos(1, 1, 2), owner, 100L);
        BlockPos secondPos = new BlockPos(8, 1, 8);
        LeyCondenserBlockEntity second = place(helper, secondPos, owner, 200L);
        first.setLimitsForTest(64, 1);
        second.setLimitsForTest(64, 1);
        first.setTarget(Target.RAW_COPPER);
        IESPEHandler pillar = fullPillar(helper, secondPos.offset(0, 0, -2));
        second.setCostForTest(Target.NETHER_STAR, 2400, 240, 24);
        second.setTarget(Target.NETHER_STAR);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(first.status() == Status.WORKING, "先放的应在工作，实际 " + first.status());
            helper.assertTrue(second.status() == Status.OWNER_LIMIT, "后放的应被名额挡住，实际 " + second.status());
            helper.assertTrue(espe(second) == 0D && pillar.getESPE() == pillar.getMaxESPE(), "被名额挡住时不该抽 ESPE，凝聚器 " + espe(second));
            helper.succeed();
        });
    }

    /** ESPE 源的 8 个位置：两两不同，都在同一高度、隔一格的那一圈上（四正四斜），不含自己那格 */
    @GameTest(template = EMPTY)
    public static void condenserEspeSourcesRing(GameTestHelper helper) {
        List<Vec3i> sources = LeyCondenserBlockEntity.SOURCES;
        helper.assertTrue(sources.size() == 8 && new HashSet<>(sources).size() == 8, "应是 8 个两两不同的位置，实际 " + sources);
        for (Vec3i offset : sources) {
            int ax = Math.abs(offset.getX());
            int az = Math.abs(offset.getZ());
            helper.assertTrue(offset.getY() == 0, "应在同一高度：" + offset);
            helper.assertTrue((ax == 0 || ax == 2) && (az == 0 || az == 2) && ax + az > 0, "应在隔一格的那一圈上：" + offset);
        }
        helper.succeed();
    }

    // ---------------- 停与换 ----------------

    /**
     * 9 格塞满别的东西：输出格已满、不扣 MRU；腾出一格接着做。一格粗铜 63 个、其余塞满：做 1 个并进去成 64，之后停在「输出格已满」
     */
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void condenserOutputFullStops(GameTestHelper helper) {
        LeyCondenserBlockEntity be = place(helper, CENTER, UUID.randomUUID(), 0L);
        Item[] fillers = {Items.STONE, Items.DIRT, Items.SAND, Items.GRAVEL, Items.OAK_PLANKS, Items.GLASS, Items.BRICKS, Items.CLAY, Items.WHITE_WOOL};
        for (int i = 0; i < LeyCondenserBlockEntity.OUTPUT_SLOTS; i++) {
            be.getInventory().setStackInSlot(LeyCondenserBlockEntity.FIRST_OUTPUT + i, new ItemStack(fillers[i], 64));
        }
        be.setTarget(Target.RAW_COPPER);
        int full = mru(be);
        int slot = LeyCondenserBlockEntity.FIRST_OUTPUT + 4;
        helper.startSequence()
                .thenExecuteAfter(30, () -> {
                    helper.assertTrue(be.status() == Status.OUTPUT_FULL, "应是输出格已满，实际 " + be.status());
                    helper.assertTrue(mru(be) == full && be.progress() == 0 && be.producedCount() == 0, "输出满了不该扣 MRU、不该开工");
                    be.getInventory().setStackInSlot(slot, new ItemStack(Items.RAW_COPPER, 63));
                })
                .thenWaitUntil(() -> helper.assertTrue(be.producedCount() == 1, "腾出一个位置后应凝出 1 个"))
                .thenExecuteAfter(30, () -> {
                    ItemStack stack = be.getInventory().getStackInSlot(slot);
                    helper.assertTrue(stack.is(Items.RAW_COPPER) && stack.getCount() == 64, "应并进那一格成 64，实际 " + stack);
                    helper.assertTrue(be.producedCount() == 1 && be.status() == Status.OUTPUT_FULL, "满了应停住，实际 " + be.status());
                    helper.assertTrue(mru(be) == full - 1000, "只该扣一个的钱，实际扣 " + (full - mru(be)));
                    be.getInventory().setStackInSlot(slot, ItemStack.EMPTY);
                })
                .thenWaitUntil(() -> helper.assertTrue(be.producedCount() >= 2 && be.getInventory().getStackInSlot(slot).is(Items.RAW_COPPER),
                        "取空后应接着做"))
                .thenSucceed();
    }

    /** 做到一半换目标：进度清零、已扣的不退；按回原目标也从 0 开始 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void condenserChangeTargetResets(GameTestHelper helper) {
        LeyCondenserBlockEntity be = place(helper, CENTER, UUID.randomUUID(), 0L);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        LeyCondenserMenu menu = menu(helper, be, player);
        helper.assertTrue(menu.clickMenuButton(player, Target.RAW_IRON.ordinal()) && be.target() == Target.RAW_IRON, "按钮应选中粗铁");
        int full = mru(be);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(be.progress() >= 15, "进度还没到 15"))
                .thenExecute(() -> {
                    int paid = full - mru(be);
                    helper.assertTrue(paid == expectedSpent(0, 4000, 40, be.progress()), "已付应等于进度对应的部分");
                    helper.assertTrue(menu.clickMenuButton(player, Target.RAW_COPPER.ordinal()), "换成粗铜应成功");
                    helper.assertTrue(be.target() == Target.RAW_COPPER && be.progress() == 0, "换目标进度应清零");
                    helper.assertTrue(full - mru(be) == paid, "已扣的不退");
                    helper.assertTrue(menu.clickMenuButton(player, Target.RAW_IRON.ordinal()) && be.progress() == 0, "按回粗铁也从 0 开始");
                })
                .thenSucceed();
    }

    /** 按钮：越界被拒；不收的（用时钩子设 0）被拒、目标不变；点已选中的 = 取消；客户端菜单一律拒 */
    @GameTest(template = EMPTY)
    public static void condenserButtonValidation(GameTestHelper helper) {
        LeyCondenserBlockEntity be = place(helper, CENTER, UUID.randomUUID(), 0L);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        LeyCondenserMenu menu = menu(helper, be, player);
        helper.assertTrue(!menu.clickMenuButton(player, -1) && !menu.clickMenuButton(player, Target.VALUES.length)
                && !menu.clickMenuButton(player, 99) && be.target() == null, "越界的按钮号应被拒");
        be.setCostForTest(Target.DIAMOND, 100000, 0, 0);
        helper.assertTrue(!be.targetEnabled(Target.DIAMOND), "用时 0 = 不收");
        helper.assertTrue(!menu.clickMenuButton(player, Target.DIAMOND.ordinal()) && be.target() == null, "不收的目标不让选");
        helper.assertTrue(menu.clickMenuButton(player, Target.QUARTZ.ordinal()) && be.target() == Target.QUARTZ, "石英应能选");
        helper.assertTrue(menu.clickMenuButton(player, Target.QUARTZ.ordinal()) && be.target() == null, "再点一次应取消");
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(be.getBlockPos());
        LeyCondenserMenu clientMenu = new LeyCondenserMenu(1, player.getInventory(), buf);
        helper.assertTrue(!clientMenu.clickMenuButton(player, Target.QUARTZ.ordinal()) && be.target() == null, "客户端菜单的按钮应被拒");
        helper.succeed();
    }

    /** 用时 0：不收这一种、不扣；每步超过容量（1 000 万 MRU、10 刻）：容量不够、不扣 */
    @GameTest(template = EMPTY)
    public static void condenserTargetOffAndTooExpensive(GameTestHelper helper) {
        LeyCondenserBlockEntity off = place(helper, CENTER, UUID.randomUUID(), 0L);
        off.setTarget(Target.REDSTONE);
        off.setCostForTest(Target.REDSTONE, 1000, 0, 0);
        BlockPos second = new BlockPos(0, 1, 0);
        LeyCondenserBlockEntity costly = place(helper, second, UUID.randomUUID(), 0L);
        costly.setTarget(Target.LAPIS_LAZULI);
        costly.setCostForTest(Target.LAPIS_LAZULI, 10_000_000, 0, 10);
        int fullOff = mru(off);
        int fullCostly = mru(costly);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(off.status() == Status.TARGET_OFF && mru(off) == fullOff, "用时 0 应不收、不扣，实际 " + off.status());
            helper.assertTrue(costly.status() == Status.TOO_EXPENSIVE && mru(costly) == fullCostly, "超容量应停住、不扣，实际 " + costly.status());
            helper.succeed();
        });
    }

    /** 投影到一半接上红石：停在原处、不扣；断开后接着做完 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void condenserRedstonePauses(GameTestHelper helper) {
        LeyCondenserBlockEntity be = place(helper, CENTER, UUID.randomUUID(), 0L);
        be.setTarget(Target.RAW_IRON);
        BlockPos torch = CENTER.above();
        int[] progressAt = {0};
        int[] mruAt = {0};
        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(be.status() == Status.WORKING && be.progress() > 0, "应在凝，实际 " + be.status());
                    helper.setBlock(torch, Blocks.REDSTONE_BLOCK);
                })
                .thenExecuteAfter(2, () -> {
                    progressAt[0] = be.progress();
                    mruAt[0] = mru(be);
                })
                .thenExecuteAfter(30, () -> {
                    helper.assertTrue(be.status() == Status.POWERED, "应是红石暂停，实际 " + be.status());
                    helper.assertTrue(be.progress() == progressAt[0] && be.progress() > 0, "进度应停在原处");
                    helper.assertTrue(mru(be) == mruAt[0] && be.producedCount() == 0, "暂停时不该扣、不该出货");
                    helper.setBlock(torch, Blocks.AIR);
                })
                .thenWaitUntil(() -> helper.assertTrue(be.producedCount() >= 1, "断开红石后应接着做完"))
                .thenSucceed();
    }

    // ---------------- 地脉 ----------------

    /** 没有地脉（钩子）：粗铁停在「这里没有地脉」、不扣，界面同步；下界之星照常 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void condenserNoLeyOnlyStar(GameTestHelper helper) {
        LeyCondenserBlockEntity be = place(helper, CENTER, UUID.randomUUID(), 0L);
        be.setLeyForTest(false);
        be.setTarget(Target.RAW_IRON);
        int full = mru(be);
        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    helper.assertTrue(be.status() == Status.NO_LEY && mru(be) == full, "没有地脉应停住、不扣，实际 " + be.status());
                    LeyCondenserMenu menu = menu(helper, be, ProtectionFrameworkTests.realPlayer(helper));
                    helper.assertTrue(!menu.hasLey(), "界面应知道这里没有地脉");
                    be.setCostForTest(Target.NETHER_STAR, 2400, 240, 24);
                    be.getESPEStorage().setESPE(1000D);
                    be.setTarget(Target.NETHER_STAR);
                })
                .thenWaitUntil(() -> helper.assertTrue(be.producedCount() == 1, "没有地脉也应能凝下界之星"))
                .thenExecute(() -> helper.assertTrue(countOutput(be, Items.NETHER_STAR) == 1, "输出格应有一颗下界之星"))
                .thenSucceed();
    }

    /** 有没有地脉和投影仪同一个判断：GameTest 世界（主世界）、下界、海纳尔有，末地没有 */
    @GameTest(template = EMPTY)
    public static void condenserHasLeyMatchesProjector(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(LeyProjectorBlockEntity.hasLey(level), "GameTest 世界应有地脉");
        ServerLevel nether = level.getServer().getLevel(Level.NETHER);
        if (nether != null) {
            helper.assertTrue(LeyProjectorBlockEntity.hasLey(nether), "下界应有地脉");
        }
        ServerLevel end = level.getServer().getLevel(Level.END);
        if (end != null) {
            helper.assertTrue(!LeyProjectorBlockEntity.hasLey(end), "末地不该有地脉");
        }
        ServerLevel hoanna = level.getServer().getLevel(ECHoanna.LEVEL);
        if (hoanna != null) {
            helper.assertTrue(LeyProjectorBlockEntity.hasLey(hoanna), "海纳尔应有地脉");
        }
        LeyCondenserBlockEntity be = place(helper, CENTER, UUID.randomUUID(), 0L);
        helper.assertTrue(be.hasLeyForMenu(), "凝聚器在 GameTest 世界应判有地脉");
        helper.succeed();
    }

    // ---------------- 台数 ----------------

    /** 同主人同区块两台：后放的排队（DUPLICATE）、不扣，挡路坐标 = 第一台 */
    @GameTest(template = EMPTY)
    public static void condenserSameChunkSecondIdle(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        BlockPos second = sameChunkNeighbour(helper, CENTER);
        LeyCondenserBlockEntity a = place(helper, CENTER, owner, 100L);
        LeyCondenserBlockEntity b = place(helper, second, owner, 200L);
        a.setTarget(Target.RAW_IRON);
        b.setTarget(Target.RAW_IRON);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(a.status() == Status.WORKING, "先放的应在工作，实际 " + a.status());
            helper.assertTrue(b.status() == Status.DUPLICATE && mru(b) == b.getMRUStorage().getMaxMRU(), "后放的应排队、不扣，实际 " + b.status());
            helper.assertTrue(helper.absolutePos(CENTER).equals(b.blocker()), "挡路坐标应是第一台，实际 " + b.blocker());
            LeyCondenserMenu menu = menu(helper, b, ProtectionFrameworkTests.realPlayer(helper));
            helper.assertTrue(helper.absolutePos(CENTER).equals(menu.blocker()), "界面还原的挡路坐标应是第一台");
            helper.succeed();
        });
    }

    /** 放 5 台、同主人（每区块上限钩子 64、全服 4）：最早的 4 台工作、第 5 台「同时工作的太多」 */
    private static List<LeyCondenserBlockEntity> fiveOfOneOwner(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        List<LeyCondenserBlockEntity> list = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            LeyCondenserBlockEntity be = place(helper, new BlockPos(1 + i * 2, 1, 5), owner, 100L * (i + 1));
            be.setLimitsForTest(64, 4);
            be.setTarget(Target.RAW_COPPER);
            list.add(be);
        }
        return list;
    }

    /** 全服同主人 4 台：第 5 台排队、不扣；拆掉最早那台，第 5 台 120 刻内接上 */
    @GameTest(template = EMPTY_LARGE, timeoutTicks = 400)
    public static void condenserOwnerLimit(GameTestHelper helper) {
        List<LeyCondenserBlockEntity> list = fiveOfOneOwner(helper);
        LeyCondenserBlockEntity fifth = list.get(4);
        long[] removedAt = {-1L};
        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    for (int i = 0; i < 4; i++) {
                        helper.assertTrue(list.get(i).status() == Status.WORKING, "第 " + (i + 1) + " 台应在工作，实际 " + list.get(i).status());
                    }
                    helper.assertTrue(fifth.status() == Status.OWNER_LIMIT, "第 5 台应排队，实际 " + fifth.status());
                    helper.assertTrue(mru(fifth) == fifth.getMRUStorage().getMaxMRU() && fifth.producedCount() == 0, "排队的不该扣、不该出货");
                    helper.assertTrue(list.get(0).isListed(), "加载着的应在名额表里");
                    helper.setBlock(new BlockPos(1, 1, 5), Blocks.AIR);
                    helper.assertTrue(!list.get(0).isListed(), "拆掉的应从名额表里移除");
                    removedAt[0] = helper.getTick();
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(helper.getTick() - removedAt[0] <= 120, "拆掉一台 120 刻内第 5 台应接上");
                    helper.assertTrue(fifth.status() == Status.WORKING, "第 5 台应接上，实际 " + fifth.status());
                })
                .thenSucceed();
    }

    /** 全服名额只算「开着的」：最早那台取消目标（闲着）后不占名额，第 5 台 120 刻内接上 */
    @GameTest(template = EMPTY_LARGE, timeoutTicks = 400)
    public static void condenserIdleFreesSlot(GameTestHelper helper) {
        List<LeyCondenserBlockEntity> list = fiveOfOneOwner(helper);
        LeyCondenserBlockEntity fifth = list.get(4);
        long[] clearedAt = {-1L};
        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    helper.assertTrue(fifth.status() == Status.OWNER_LIMIT, "第 5 台应排队，实际 " + fifth.status());
                    list.get(0).setTarget(null);
                    clearedAt[0] = helper.getTick();
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(helper.getTick() - clearedAt[0] <= 120, "取消目标 120 刻内第 5 台应接上");
                    helper.assertTrue(fifth.status() == Status.WORKING, "第 5 台应接上，实际 " + fifth.status());
                })
                .thenExecute(() -> helper.assertTrue(list.get(0).status() == Status.NO_TARGET, "第一台应是没选目标"))
                .thenSucceed();
    }

    /**
     * 做到一半被名额拦下：全服 4 台，最早那台先不选目标，第 5 台（用时钩子 200 刻）开工后再给最早那台选上，
     * 第 5 台 5 刻内停在「同时工作的太多」、进度留在半路，20 刻里进度和 MRU 都不动、不出货；
     * 取消最早那台的目标后从原进度接着走完，合计正好付一个的钱（没有从 0 重来）
     */
    @GameTest(template = EMPTY_LARGE, timeoutTicks = 600)
    public static void condenserOwnerLimitMidProgress(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        List<LeyCondenserBlockEntity> list = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            LeyCondenserBlockEntity be = place(helper, new BlockPos(1 + i * 2, 1, 5), owner, 100L * (i + 1));
            be.setLimitsForTest(64, 4);
            list.add(be);
        }
        LeyCondenserBlockEntity first = list.get(0);
        LeyCondenserBlockEntity fifth = list.get(4);
        for (int i = 1; i < 4; i++) {
            list.get(i).setTarget(Target.RAW_IRON);
        }
        fifth.setCostForTest(Target.RAW_IRON, 4000, 0, 200);
        fifth.setTarget(Target.RAW_IRON);
        int full = mru(fifth);
        long[] claimedAt = {-1L};
        int[] heldProgress = {0};
        int[] heldMru = {0};
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(fifth.progress() > 0, "第 5 台还没开工"))
                .thenExecute(() -> {
                    first.setTarget(Target.RAW_COPPER);
                    claimedAt[0] = helper.getTick();
                })
                .thenWaitUntil(() -> helper.assertTrue(fifth.status() == Status.OWNER_LIMIT, "第 5 台还没被名额拦下"))
                .thenExecute(() -> {
                    long elapsed = helper.getTick() - claimedAt[0];
                    helper.assertTrue(elapsed <= 5, "最早那台选上目标后，第 5 台应在 5 刻内停下，实际 " + elapsed + " 刻");
                    helper.assertTrue(first.status() == Status.WORKING, "最早那台应在工作，实际 " + first.status());
                    heldProgress[0] = fifth.progress();
                    heldMru[0] = mru(fifth);
                    helper.assertTrue(heldProgress[0] > 0 && heldProgress[0] < 200, "应停在半路，实际进度 " + heldProgress[0]);
                    helper.assertTrue(full - heldMru[0] == expectedSpent(0, 4000, 200, heldProgress[0]), "停下时已付应等于进度对应的部分");
                })
                .thenExecuteAfter(20, () -> {
                    helper.assertTrue(fifth.status() == Status.OWNER_LIMIT, "应一直等着，实际 " + fifth.status());
                    helper.assertTrue(fifth.progress() == heldProgress[0] && mru(fifth) == heldMru[0], "等名额时进度和 MRU 都不该动");
                    helper.assertTrue(fifth.producedCount() == 0, "等名额时不该出货");
                    first.setTarget(null);
                })
                .thenWaitUntil(() -> helper.assertTrue(fifth.status() == Status.WORKING, "第 5 台还没接上"))
                .thenExecute(() -> helper.assertTrue(fifth.progress() > heldProgress[0] && fifth.producedCount() == 0,
                        "应从原进度 " + heldProgress[0] + " 接着走，实际 " + fifth.progress()))
                .thenWaitUntil(() -> helper.assertTrue(fifth.producedCount() == 1, "第 5 台还没凝出来"))
                .thenExecute(() -> {
                    long spent = full - mru(fifth);
                    long want = expectedSpent(1, 4000, 200, fifth.progress());
                    helper.assertTrue(spent == want, "合计应是一个的钱加下一个已付的部分（" + want + "），实际 " + spent);
                })
                .thenSucceed();
    }

    /**
     * 倒着给目标：全服 2 台，5 台先都不选目标，再从最晚放下的那台起每 4 刻往前给一台选上。每选上一台更早的，
     * 后放的就重数名额：任何时候同时在凝的都不超过 2 台（超了也只能是 1 刻），最后是最早的 2 台在凝
     */
    @GameTest(template = EMPTY_LARGE, timeoutTicks = 300)
    public static void condenserOwnerLimitReverseSelect(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        List<LeyCondenserBlockEntity> list = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            LeyCondenserBlockEntity be = place(helper, new BlockPos(1 + i * 2, 1, 5), owner, 100L * (i + 1));
            be.setLimitsForTest(64, 2);
            list.add(be);
        }
        int[] step = {0};
        int[] overrun = {0};
        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    for (LeyCondenserBlockEntity be : list) {
                        helper.assertTrue(be.status() == Status.NO_TARGET, "开头都应是没选目标，实际 " + be.status());
                    }
                })
                .thenExecuteFor(60, () -> {
                    int k = step[0]++;
                    if (k % 4 == 0 && k / 4 < list.size()) {
                        list.get(list.size() - 1 - k / 4).setTarget(Target.RAW_COPPER);
                    }
                    int working = 0;
                    for (LeyCondenserBlockEntity be : list) {
                        if (be.status() == Status.WORKING) {
                            working++;
                        }
                    }
                    overrun[0] = working > 2 ? overrun[0] + 1 : 0;
                    helper.assertTrue(overrun[0] <= 1, "同时在凝的超过 2 台已经 " + overrun[0] + " 刻（第 " + k + " 刻，" + working + " 台）");
                })
                .thenExecute(() -> {
                    for (int i = 0; i < list.size(); i++) {
                        Status want = i < 2 ? Status.WORKING : Status.OWNER_LIMIT;
                        helper.assertTrue(list.get(i).status() == want, "第 " + (i + 1) + " 台应是 " + want + "，实际 " + list.get(i).status());
                    }
                })
                .thenSucceed();
    }

    /**
     * 更早那台所在的区块重新加载：它把名额拿回去，在凝的后一台 3 刻内停下（不等自己的名额缓存过期）。
     * 卸载用移除方块模拟（同样移出名额表）；重新加载照区块加载的顺序，新方块实体先读存档、再放进世界（onLoad）
     */
    @GameTest(template = EMPTY_LARGE, timeoutTicks = 400)
    public static void condenserReloadReclaimsSlot(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        BlockPos posA = new BlockPos(1, 1, 5);
        BlockPos posB = new BlockPos(9, 1, 5);
        LeyCondenserBlockEntity a = place(helper, posA, owner, 100L);
        LeyCondenserBlockEntity b = place(helper, posB, owner, 200L);
        a.setLimitsForTest(64, 1);
        b.setLimitsForTest(64, 1);
        a.setTarget(Target.RAW_COPPER);
        b.setTarget(Target.RAW_COPPER);
        CompoundTag[] saved = {null};
        LeyCondenserBlockEntity[] reloaded = {null};
        long[] reloadedAt = {-1L};
        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(a.status() == Status.WORKING && b.status() == Status.OWNER_LIMIT,
                            "开头应是先放的在凝、后放的排队，实际 " + a.status() + " / " + b.status());
                    saved[0] = a.saveWithoutMetadata();
                    a.clearContent();
                    helper.setBlock(posA, Blocks.AIR);
                    helper.assertTrue(!a.isListed(), "移除的应出名额表");
                })
                .thenWaitUntil(() -> helper.assertTrue(b.status() == Status.WORKING, "先放的不在了，后放的还没接上"))
                .thenExecute(() -> {
                    helper.setBlock(posA, ECLeyCondenser.LEY_CONDENSER.get());
                    LeyCondenserBlockEntity fresh = new LeyCondenserBlockEntity(helper.absolutePos(posA), helper.getBlockState(posA));
                    fresh.load(saved[0]);
                    helper.getLevel().setBlockEntity(fresh);
                    helper.assertTrue(fresh.isListed() && fresh.placedTick() == 100L && fresh.target() == Target.RAW_COPPER,
                            "读回来的应登记进名额表、放下时间和目标不变");
                    reloaded[0] = fresh;
                    reloadedAt[0] = helper.getTick();
                })
                .thenWaitUntil(() -> helper.assertTrue(b.status() == Status.OWNER_LIMIT, "后放的还没让出名额"))
                .thenExecute(() -> {
                    long elapsed = helper.getTick() - reloadedAt[0];
                    helper.assertTrue(elapsed <= 3, "更早那台重新加载后，后放的应在 3 刻内停下，实际 " + elapsed + " 刻");
                })
                .thenWaitUntil(() -> helper.assertTrue(reloaded[0].status() == Status.WORKING, "重新加载的那台还没开工"))
                .thenSucceed();
    }

    /** 同区块两个主人：互不影响，都工作 */
    @GameTest(template = EMPTY)
    public static void condenserTwoOwnersBothWork(GameTestHelper helper) {
        BlockPos second = sameChunkNeighbour(helper, CENTER);
        LeyCondenserBlockEntity a = place(helper, CENTER, UUID.randomUUID(), 100L);
        LeyCondenserBlockEntity b = place(helper, second, UUID.randomUUID(), 200L);
        a.setTarget(Target.RAW_GOLD);
        b.setTarget(Target.RAW_GOLD);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(a.status() == Status.WORKING && b.status() == Status.WORKING, "两个主人应都在工作，实际 " + a.status() + " / " + b.status());
            helper.succeed();
        });
    }

    /** 专用服（GameTest 服）上没有主人的：NO_OWNER，不扣 */
    @GameTest(template = EMPTY)
    public static void condenserNoOwnerIdles(GameTestHelper helper) {
        LeyCondenserBlockEntity be = place(helper, CENTER, null, 100L);
        be.setTarget(Target.RAW_IRON);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(be.status() == Status.NO_OWNER, "没有主人应不工作，实际 " + be.status());
            helper.assertTrue(mru(be) == be.getMRUStorage().getMaxMRU() && be.producedCount() == 0, "不该扣、不该出货");
            helper.succeed();
        });
    }

    // ---------------- 漏斗与白名单 ----------------

    /** 漏斗、管道：只有 0 号格能塞（绑定宝石）、输出格塞不进；只有输出格能抽，宝石格抽不走 */
    @GameTest(template = EMPTY)
    public static void condenserAutomationRules(GameTestHelper helper) {
        LeyCondenserBlockEntity be = place(helper, CENTER, UUID.randomUUID(), 0L);
        IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP)
                .orElseThrow(() -> new IllegalStateException("凝聚器应暴露物品能力"));
        for (int slot = LeyCondenserBlockEntity.FIRST_OUTPUT; slot < LeyCondenserBlockEntity.SLOTS; slot++) {
            helper.assertTrue(handler.insertItem(slot, new ItemStack(Items.DIAMOND), true).getCount() == 1, "漏斗不能往输出格 " + slot + " 塞");
        }
        helper.assertTrue(handler.insertItem(LeyCondenserBlockEntity.SLOT_GEM, new ItemStack(Items.DIAMOND), true).getCount() == 1, "0 号格只收绑定宝石");
        Item gem = ForgeRegistries.ITEMS.getValue(EssentialCraft5.id("bound_gem"));
        helper.assertTrue(gem != null && gem != Items.AIR, "绑定宝石应已注册");
        helper.assertTrue(handler.insertItem(LeyCondenserBlockEntity.SLOT_GEM, new ItemStack(gem), true).isEmpty(), "0 号格应收绑定宝石");
        be.getInventory().setStackInSlot(LeyCondenserBlockEntity.SLOT_GEM, new ItemStack(gem));
        helper.assertTrue(handler.extractItem(LeyCondenserBlockEntity.SLOT_GEM, 1, false).isEmpty(), "宝石格不能被抽走");
        int out = LeyCondenserBlockEntity.FIRST_OUTPUT + 6;
        be.getInventory().setStackInSlot(out, new ItemStack(Items.RAW_GOLD, 3));
        ItemStack pulled = handler.extractItem(out, 3, false);
        helper.assertTrue(pulled.is(Items.RAW_GOLD) && pulled.getCount() == 3, "输出格应能抽出来，实际 " + pulled);
        helper.succeed();
    }

    /** 产物白名单：10 种，都是原版物品、都没有燃烧时间（没有煤），含远古残骸和下界之星 */
    @GameTest(template = EMPTY)
    public static void condenserWhitelistNoFuel(GameTestHelper helper) {
        helper.assertTrue(Target.VALUES.length == 10, "目标应是 10 种，实际 " + Target.VALUES.length);
        Set<Item> products = new HashSet<>();
        for (Target target : Target.VALUES) {
            ItemStack stack = new ItemStack(target.product);
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(target.product);
            helper.assertTrue(key != null && "minecraft".equals(key.getNamespace()), target + " 应是原版物品，实际 " + key);
            helper.assertTrue(ForgeHooks.getBurnTime(stack, null) <= 0, target + " 不该有燃烧时间");
            helper.assertTrue(!LeyCondenserBlockEntity.isFuel(target), target + " 不该被当成燃料");
            helper.assertTrue(key.getPath().equals(target.key), target + " 的配置小节名应是产物路径");
            helper.assertTrue(products.add(target.product), "产物不该重复：" + target);
        }
        helper.assertTrue(products.contains(Items.ANCIENT_DEBRIS) && products.contains(Items.NETHER_STAR), "应能凝远古残骸和下界之星");
        helper.assertTrue(!products.contains(Items.COAL) && !products.contains(Items.CHARCOAL) && !products.contains(Items.COAL_BLOCK), "不该有煤");
        helper.succeed();
    }

    /** 产物进本模组的矿石加工：都不算矿石方块，一个最多出一个岩浆合金（和挖来的粗矿一样）；远古残骸不收 */
    @GameTest(template = EMPTY)
    public static void condenserNoOreDoubling(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        for (Target target : Target.VALUES) {
            ItemStack stack = new ItemStack(target.product);
            OreSmeltingRecipe recipe = OreSmeltingRecipe.find(recipes, stack);
            if (target == Target.ANCIENT_DEBRIS) {
                helper.assertTrue(recipe == null, "矿石加工不该收远古残骸，实际 " + (recipe == null ? null : recipe.getId()));
                continue;
            }
            if (recipe == null) {
                continue;
            }
            helper.assertTrue(!recipe.isOre(stack), target + " 不该被当成矿石方块（" + recipe.getId() + "）");
            OreSmeltingRecipe.Yield smeltery = recipe.yieldFor(stack, OreSmeltingRecipe.SMELTERY_MULTIPLIER);
            OreSmeltingRecipe.Yield furnace = recipe.yieldFor(stack, OreSmeltingRecipe.FURNACE_MULTIPLIER);
            helper.assertTrue(smeltery != null && smeltery.alloys() <= smeltery.consumed(), target + " 在冶炼炉里一个最多出一个合金");
            helper.assertTrue(furnace != null && furnace.alloys() <= furnace.consumed(), target + " 在岩浆熔炉里一个最多出一个合金");
        }
        helper.succeed();
    }

    // ---------------- 不能复制 ----------------

    /** 做到一半移除：只掉出输出格里原有的 5 个粗铁，没有多出的产物；另一台先清空再移除什么都不掉（/clone、/setblock 覆盖前原版会清空） */
    @GameTest(template = EMPTY_LARGE, timeoutTicks = 200)
    public static void condenserBreakNoDupe(GameTestHelper helper) {
        BlockPos posA = new BlockPos(2, 1, 2);
        BlockPos posB = new BlockPos(8, 1, 8);
        LeyCondenserBlockEntity a = place(helper, posA, UUID.randomUUID(), 0L);
        LeyCondenserBlockEntity b = place(helper, posB, UUID.randomUUID(), 0L);
        a.getInventory().setStackInSlot(LeyCondenserBlockEntity.FIRST_OUTPUT, new ItemStack(Items.RAW_IRON, 5));
        b.getInventory().setStackInSlot(LeyCondenserBlockEntity.FIRST_OUTPUT, new ItemStack(Items.DIAMOND, 5));
        a.setTarget(Target.RAW_IRON);
        b.setTarget(Target.DIAMOND);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(a.progress() >= 20 && b.progress() >= 20, "两台都应做到一半"))
                .thenExecute(() -> {
                    helper.setBlock(posA, Blocks.AIR);
                    b.clearContent();
                    helper.setBlock(posB, Blocks.AIR);
                })
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(itemsAround(helper, posA, Items.RAW_IRON) == 5, "应正好掉出原有的 5 个粗铁，实际 " + itemsAround(helper, posA, Items.RAW_IRON));
                    helper.assertTrue(itemsAround(helper, posB, Items.DIAMOND) == 0, "清空后移除不该掉任何东西");
                })
                .thenSucceed();
    }

    /** 存成 NBT 再读进新方块实体：进度、目标、放下时间一致，不多出货；更新包不带进度和放下时间 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void condenserSaveLoadRoundTrip(GameTestHelper helper) {
        LeyCondenserBlockEntity be = place(helper, CENTER, UUID.randomUUID(), 1234L);
        be.setTarget(Target.EMERALD);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(be.progress() >= 10, "进度还没到 10"))
                .thenExecute(() -> {
                    CompoundTag saved = be.saveWithoutMetadata();
                    BlockState state = be.getBlockState();
                    LeyCondenserBlockEntity copy = new LeyCondenserBlockEntity(be.getBlockPos(), state);
                    copy.load(saved);
                    helper.assertTrue(copy.progress() == be.progress() && copy.target() == Target.EMERALD && copy.placedTick() == 1234L,
                            "读回来应一致：进度 " + copy.progress() + "、目标 " + copy.target() + "、放下时间 " + copy.placedTick());
                    helper.assertTrue(copy.producedCount() == 0 && copy.getMRUStorage().getMRU() == be.getMRUStorage().getMRU(), "读档不该多出货、MRU 一致");
                    CompoundTag update = be.getUpdateTag();
                    helper.assertTrue(!update.contains(LeyCondenserBlockEntity.TAG_PROGRESS) && !update.contains(LeyCondenserBlockEntity.TAG_PLACED),
                            "更新包不该带进度和放下时间");
                    helper.assertTrue("minecraft:emerald".equals(update.getString(LeyCondenserBlockEntity.TAG_TARGET)), "更新包应带目标（画影像）");
                    CompoundTag noTarget = saved.copy();
                    noTarget.putString(LeyCondenserBlockEntity.TAG_TARGET, "minecraft:coal");
                    LeyCondenserBlockEntity bad = new LeyCondenserBlockEntity(be.getBlockPos(), state);
                    bad.load(noTarget);
                    helper.assertTrue(bad.target() == null && bad.progress() == 0, "存档里不认识的目标（煤）读成没选、进度清零");
                })
                .thenSucceed();
    }

    // ---------------- 世界不动 ----------------

    /**
     * 四周和下面铺石头、铁矿石、放一个装着东西的箱子；模拟领地拒绝所有格子。工作 200 刻：照常凝，
     * 没有任何破坏 / 放置事件，每格方块状态、箱子内容不变，没有掉落物
     */
    @GameTest(template = EMPTY_LARGE, timeoutTicks = 300)
    public static void condenserWorldUntouched(GameTestHelper helper) {
        BlockPos machine = new BlockPos(5, 2, 5);
        List<BlockPos> cells = new ArrayList<>();
        for (int x = 1; x <= 9; x++) {
            for (int y = 1; y <= 4; y++) {
                for (int z = 1; z <= 9; z++) {
                    BlockPos rel = new BlockPos(x, y, z);
                    if (rel.equals(machine)) {
                        continue;
                    }
                    cells.add(rel);
                    if (y == 1 || (y == 2 && Math.abs(x - 5) <= 1 && Math.abs(z - 5) <= 1)) {
                        helper.setBlock(rel, (x + z) % 3 == 0 ? Blocks.IRON_ORE : Blocks.STONE);
                    }
                }
            }
        }
        BlockPos chest = new BlockPos(5, 2, 7);
        helper.setBlock(chest, Blocks.CHEST);
        ((Container) helper.getBlockEntity(chest)).setItem(0, new ItemStack(Items.DIAMOND, 7));
        LeyCondenserBlockEntity be = place(helper, machine, UUID.randomUUID(), 0L);
        be.setTarget(Target.RAW_IRON);
        Map<BlockPos, BlockState> before = new HashMap<>();
        for (BlockPos rel : cells) {
            before.put(rel, helper.getBlockState(rel));
        }
        AABB area = new AABB(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(10, 7, 10))).inflate(1D);
        int itemsBefore = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).size();
        ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper);
        for (BlockPos rel : cells) {
            claims.deny(rel);
        }
        claims.deny(machine);
        helper.runAfterDelay(200, () -> {
            try {
                helper.assertTrue(be.producedCount() >= 4, "领地怎么设都该照常凝，实际 " + be.producedCount() + " 个");
                for (BlockPos rel : cells) {
                    helper.assertTrue(helper.getBlockState(rel) == before.get(rel), "方块被改了：" + rel);
                    helper.assertTrue(claims.breaks(rel) == 0 && claims.places(rel) == 0, "不该对 " + rel + " 发破坏 / 放置事件");
                }
                helper.assertTrue(claims.breaks(machine) == 0 && claims.places(machine) == 0, "不该对自己那格发事件");
                ItemStack inChest = ((Container) helper.getBlockEntity(chest)).getItem(0);
                helper.assertTrue(inChest.is(Items.DIAMOND) && inChest.getCount() == 7, "箱子里的东西不该动，实际 " + inChest);
                int itemsAfter = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).size();
                helper.assertTrue(itemsAfter == itemsBefore, "不该生成掉落物，前 " + itemsBefore + " 后 " + itemsAfter);
            } finally {
                claims.close();
            }
            helper.succeed();
        });
    }

    // ---------------- 配方与标签 ----------------

    /** 配方 DND/VLE/DCD：产物是凝聚器，吃投影仪和任意一种数学核心；#math_cores 正好四种；电脑引擎仍要四种指定的核心 */
    @GameTest(template = EMPTY)
    public static void condenserRecipeAndTag(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Optional<? extends Recipe<?>> recipe = level.getRecipeManager().byKey(EssentialCraft5.id("leycondenser/ley_condenser"));
        helper.assertTrue(recipe.isPresent(), "凝聚器的配方应已加载");
        ItemStack result = recipe.get().getResultItem(level.registryAccess());
        helper.assertTrue(result.is(ECLeyCondenser.LEY_CONDENSER.get().asItem()), "配方产物应是凝聚器，实际 " + result);
        String[] cores = {"addition_core", "subtraction_core", "multiplication_core", "division_core"};
        Set<Item> tagged = new HashSet<>();
        ForgeRegistries.ITEMS.tags().getTag(ECLeyCondenser.MATH_CORES).forEach(tagged::add);
        helper.assertTrue(tagged.size() == 4, "#math_cores 应正好 4 种，实际 " + tagged.size());
        boolean usesProjector = false;
        boolean usesCores = false;
        for (Ingredient ingredient : recipe.get().getIngredients()) {
            usesProjector |= ingredient.test(new ItemStack(ECLeyProjector.LEY_PROJECTOR.get()));
            boolean allCores = true;
            for (String core : cores) {
                Item item = ForgeRegistries.ITEMS.getValue(EssentialCraft5.id(core));
                helper.assertTrue(item != null && tagged.contains(item), core + " 应在 #math_cores 里");
                allCores &= ingredient.test(new ItemStack(item));
            }
            usesCores |= allCores;
        }
        helper.assertTrue(usesProjector && usesCores, "配方应吃投影仪、并且有一格任意一种数学核心都行");
        Optional<? extends Recipe<?>> engine = level.getRecipeManager().byKey(EssentialCraft5.id("computer/computer_engine"));
        helper.assertTrue(engine.isPresent(), "电脑引擎的配方应在");
        for (String core : cores) {
            Item item = ForgeRegistries.ITEMS.getValue(EssentialCraft5.id(core));
            int matching = 0;
            for (Ingredient ingredient : engine.get().getIngredients()) {
                if (ingredient.test(new ItemStack(item))) {
                    matching++;
                    for (String other : cores) {
                        if (!other.equals(core)) {
                            helper.assertTrue(!ingredient.test(new ItemStack(ForgeRegistries.ITEMS.getValue(EssentialCraft5.id(other)))),
                                    "电脑引擎的核心格应只认一种（" + core + " 那格也收 " + other + "）");
                        }
                    }
                }
            }
            helper.assertTrue(matching == 1, "电脑引擎应正好有一格要 " + core + "，实际 " + matching);
        }
        helper.succeed();
    }
}
