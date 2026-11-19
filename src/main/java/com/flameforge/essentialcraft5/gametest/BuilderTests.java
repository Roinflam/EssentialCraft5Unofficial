package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.item.BuilderJobs;
import com.flameforge.essentialcraft5.item.BuilderMode;
import com.flameforge.essentialcraft5.item.BuilderMode.Action;
import com.flameforge.essentialcraft5.item.BuilderMode.CellKind;
import com.flameforge.essentialcraft5.item.BuilderSelection;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.item.MagicalBuilderItem;
import com.flameforge.essentialcraft5.recipe.CrafterGrid;
import com.flameforge.essentialcraft5.registry.ECBuilder;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 创世神之杖（ECBuilder，第六批 6b M1，batch6b §4.1）的 GameTest：
 * <ul>
 *   <li>选点：两个点和维度写进 NBT、潜行选定方块、能选和不能选的方块、两点距离和选区格数的上限、右键方块走 onItemUseFirst、假玩家什么都不做。</li>
 *   <li>填充 / 清除 / 清除选定方块 / 清除选定以外 / 全部替换：只按做成的格子收费、材料和掉落进出背包、被拒的格子不动不扣、
 *       带方块实体 / 硬度 &lt; 0 / 液体不碰、全部替换「破坏放行、放置被拒」留空气只扣 250、没材料不拆。</li>
 *   <li>放置规则：双层台阶放单层、海泡菜和蜡烛放 1 个、成熟小麦放 age 0、玩家站着的格子不填；清除从上往下（沙子不往下掉）。</li>
 *   <li>调度：全服每刻上限多人平分、一人一个任务、换手 / 走远 / 换维度就停、潜行对空气右键先停后清点、开工前的各项检查。</li>
 *   <li>创造模式不扣 MRU、不要材料、清除不给掉落；换模式的五条配方转一圈数据都在；主配方原料齐全（2 个世界交互器）。</li>
 *   <li>批量拆（{@code harvestGuarded(…, true)}）不写探测缓存、每刻补发破坏效果不超过上限；被拒累计到上限就停；门、高花、伸出的活塞改拆主体那格；
 *       全部替换放不下（海带进干格子、花放石头上、玩家站着）的连拆都不拆；事件的监听器中途换手、打死玩家时，这一刻里就停、东西掉在原地。</li>
 * </ul>
 * 施工的测试放在单独的 batch {@value #BATCH}：全服每刻上限是所有魔杖共用的，和别的测试同批跑时不受影响，batch 结束时清掉没收尾的任务。
 * 要改每刻上限的放在各自的 batch 里（{@value #BUDGET_BATCH}、{@value #SLOW_BATCH}），用 {@link ECBuilder.Config#setTestOverrides}，
 * 在 {@code @BeforeBatch} 设、{@code @AfterBatch} 置回（不用 ConfigValue.set：服务端配置会存盘、被文件监视器冲掉）。
 * 真玩家用 {@link ProtectionFrameworkTests#realPlayer}（不进世界：站着的格子由魔杖自己查），模拟领地用 {@link ProtectionFrameworkTests.Claims}。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class BuilderTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    static final String BATCH = "ec5_builder";
    static final String BUDGET_BATCH = "ec5_builder_budget";
    static final String SLOW_BATCH = "ec5_builder_slow";
    private static final int FULL = MRUStaffItem.MAX_MRU;
    private static final int PLACE = BuilderMode.PLACE_COST;
    private static final int BREAK = BuilderMode.BREAK_COST;
    private static final int REPLACE = BuilderMode.REPLACE_COST;
    /** 「empty」里常用的 3×2×3 选区（玩家站在 (0, 1, 0)，不在里面） */
    private static final BlockPos A = new BlockPos(1, 1, 1);
    private static final BlockPos B = new BlockPos(3, 2, 3);

    private BuilderTests() {
    }

    @BeforeBatch(batch = BUDGET_BATCH)
    public static void builderBudgetBefore(ServerLevel level) {
        BuilderJobs.clearAll();
        ECBuilder.Config.Test.materialPauseSeconds = 0;
        ECBuilder.Config.Test.maxJobsTotal = 256;
        // 这一 batch 测的是格数预算的平分，耗时预算放大到不起作用
        ECBuilder.Config.Test.maxMillisPerTick = 1000D;
        ECBuilder.Config.setTestOverrides(null, 8, 8);
    }

    @AfterBatch(batch = BUDGET_BATCH)
    public static void builderBudgetAfter(ServerLevel level) {
        ECBuilder.Config.setTestOverrides(null, null, null);
        ECBuilder.Config.Test.reset();
        BuilderJobs.clearAll();
    }

    @BeforeBatch(batch = SLOW_BATCH)
    public static void builderSlowBefore(ServerLevel level) {
        BuilderJobs.clearAll();
        ECBuilder.Config.Test.materialPauseSeconds = 0;
        ECBuilder.Config.Test.maxJobsTotal = 256;
        ECBuilder.Config.setTestOverrides(null, 1, 1);
    }

    @AfterBatch(batch = SLOW_BATCH)
    public static void builderSlowAfter(ServerLevel level) {
        ECBuilder.Config.setTestOverrides(null, null, null);
        ECBuilder.Config.Test.reset();
        BuilderJobs.clearAll();
    }

    /** 这些老测试按「材料 / MRU 用完就结束」写的，暂停关掉；同一 batch 同时跑的任务超过默认全服名额，名额放大 */
    @BeforeBatch(batch = BATCH)
    public static void builderBefore(ServerLevel level) {
        BuilderJobs.clearAll();
        ECBuilder.Config.Test.materialPauseSeconds = 0;
        ECBuilder.Config.Test.maxJobsTotal = 256;
    }

    @AfterBatch(batch = BATCH)
    public static void builderAfter(ServerLevel level) {
        ECBuilder.Config.Test.reset();
        BuilderJobs.clearAll();
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private static ItemStack wand(BuilderMode mode, int mru) {
        ItemStack stack = new ItemStack(ECBuilder.item(mode));
        stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(mru));
        return stack;
    }

    /** 两个点（相对坐标）和选定方块都设好的魔杖 */
    private static ItemStack wand(GameTestHelper helper, BuilderMode mode, BlockPos a, BlockPos b, @Nullable Block block, int mru) {
        ItemStack stack = wand(mode, mru);
        BuilderSelection.setFirst(stack, helper.absolutePos(a), helper.getLevel().dimension());
        BuilderSelection.setSecond(stack, helper.absolutePos(b));
        if (block != null) {
            BuilderSelection.setStoredBlock(stack, ForgeRegistries.BLOCKS.getKey(block));
        }
        return stack;
    }

    private static int mru(ItemStack stack) {
        return MRUStaffItem.storedMRU(stack);
    }

    /** 主手拿着 wand 的真玩家，站在 (0, 1, 0)；extra 依次放进背包第 1 格起 */
    private static ServerPlayer holding(GameTestHelper helper, ItemStack wand, ItemStack... extra) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, wand);
        for (int i = 0; i < extra.length; i++) {
            player.getInventory().items.set(1 + i, extra[i]);
        }
        return player;
    }

    /** 背包（含副手）里某种物品的总数 */
    private static int count(ServerPlayer player, Item item) {
        int n = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                n += stack.getCount();
            }
        }
        ItemStack offhand = player.getInventory().offhand.get(0);
        return offhand.is(item) ? n + offhand.getCount() : n;
    }

    private static void fill(GameTestHelper helper, BlockPos a, BlockPos b, Block block) {
        for (BlockPos pos : BlockPos.betweenClosed(a, b)) {
            helper.setBlock(pos.immutable(), block);
        }
    }

    private static int countBox(GameTestHelper helper, BlockPos a, BlockPos b, Block block) {
        int n = 0;
        for (BlockPos pos : BlockPos.betweenClosed(a, b)) {
            if (helper.getBlockState(pos.immutable()).is(block)) {
                n++;
            }
        }
        return n;
    }

    /** 测试区域（相对 (0, 0, 0) 到 (sx, sy, sz)）里的掉落物数 */
    private static int itemEntities(GameTestHelper helper, int sx, int sy, int sz) {
        AABB area = new AABB(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(sx, sy, sz)));
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, area, Entity::isAlive).size();
    }

    private static void start(GameTestHelper helper, ServerPlayer player) {
        BuilderJobs.StartResult result = BuilderJobs.start(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(result == BuilderJobs.StartResult.STARTED, "应能开工，实际 " + result);
    }

    /** 等这名玩家的活结束 */
    private static void awaitDone(GameTestHelper helper, ServerPlayer player, Runnable then) {
        UUID id = player.getUUID();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(id), "还在施工"))
                .thenExecute(then)
                .thenSucceed();
    }

    /** 结束的原因和做成的格数 */
    private static void assertResult(GameTestHelper helper, ServerPlayer player, BuilderJobs.StopReason reason, int done) {
        BuilderJobs.Result result = BuilderJobs.lastResult(player.getUUID());
        helper.assertTrue(result != null, "应有施工结果");
        helper.assertTrue(result.reason() == reason && result.done() == done,
                "结果应是 " + reason + " / " + done + " 格，实际 " + result.reason() + " / " + result.done());
    }

    /** 正在做的活做成了几格；已经结束的取结果；从没开过 0 */
    private static int doneOf(UUID id) {
        int progress = BuilderJobs.progress(id);
        if (progress >= 0) {
            return progress;
        }
        BuilderJobs.Result result = BuilderJobs.lastResult(id);
        return result == null ? 0 : result.done();
    }

    // ------------------------------------------------------------------
    // 纯函数：每格的规则、格子顺序
    // ------------------------------------------------------------------

    /** 类注释里的表：五种模式 × 四种格子 × 同不同种；换模式的顺序；只有清除不用选定方块；会放方块的两种从下往上 */
    @GameTest(template = EMPTY)
    public static void builderDecideTable(GameTestHelper helper) {
        Object[][] table = {
                // 模式, 空气, 液体, 受保护, 同种, 别的
                {BuilderMode.FILL, Action.PLACE, Action.SKIP, Action.SKIP, Action.SKIP, Action.SKIP},
                {BuilderMode.CLEAR, Action.SKIP, Action.SKIP, Action.SKIP, Action.BREAK, Action.BREAK},
                {BuilderMode.CLEAR_MATCHING, Action.SKIP, Action.SKIP, Action.SKIP, Action.BREAK, Action.SKIP},
                {BuilderMode.CLEAR_OTHERS, Action.SKIP, Action.SKIP, Action.SKIP, Action.SKIP, Action.BREAK},
                {BuilderMode.REPLACE_ALL, Action.PLACE, Action.PLACE, Action.SKIP, Action.SKIP, Action.REPLACE},
        };
        for (Object[] row : table) {
            BuilderMode mode = (BuilderMode) row[0];
            check(helper, mode, CellKind.AIR, false, (Action) row[1]);
            check(helper, mode, CellKind.LIQUID, false, (Action) row[2]);
            check(helper, mode, CellKind.PROTECTED, false, (Action) row[3]);
            check(helper, mode, CellKind.PROTECTED, true, (Action) row[3]);
            check(helper, mode, CellKind.OTHER, true, (Action) row[4]);
            check(helper, mode, CellKind.OTHER, false, (Action) row[5]);
        }
        BuilderMode mode = BuilderMode.FILL;
        BuilderMode[] expected = {BuilderMode.CLEAR, BuilderMode.CLEAR_MATCHING, BuilderMode.CLEAR_OTHERS, BuilderMode.REPLACE_ALL, BuilderMode.FILL};
        for (BuilderMode next : expected) {
            helper.assertTrue(mode.next() == next, mode + " 的下一个应是 " + next + "，实际 " + mode.next());
            mode = mode.next();
        }
        for (BuilderMode m : BuilderMode.values()) {
            helper.assertTrue(m.needsBlock() == (m != BuilderMode.CLEAR), m + " 要不要选定方块不对");
            helper.assertTrue(m.bottomUp() == (m == BuilderMode.FILL || m == BuilderMode.REPLACE_ALL), m + " 的施工方向不对");
            helper.assertTrue(BuilderMode.of(new ItemStack(ECBuilder.item(m))) == m, m + " 的物品认不出模式");
        }
        helper.assertTrue(BuilderMode.of(new ItemStack(Items.STICK)) == null, "木棍不是创世神之杖");
        helper.succeed();
    }

    private static void check(GameTestHelper helper, BuilderMode mode, CellKind kind, boolean same, Action expected) {
        Action actual = mode.decide(kind, same);
        helper.assertTrue(actual == expected, mode + " / " + kind + (same ? "（同种）" : "") + " 应是 " + expected + "，实际 " + actual);
    }

    /** 每格恰好一次；从下往上时第一格在最低层、从上往下时在最高层；同一层里 x 优先、再 z；离选区的距离按整格算 */
    @GameTest(template = EMPTY)
    public static void builderCellOrder(GameTestHelper helper) {
        BoundingBox box = BuilderSelection.box(new BlockPos(5, 10, -3), new BlockPos(3, 8, -2));
        int cells = BuilderSelection.cellCount(box);
        helper.assertTrue(cells == 3 * 3 * 2 && BuilderSelection.volume(new BlockPos(5, 10, -3), new BlockPos(3, 8, -2)) == cells, "格数应是 18，实际 " + cells);
        for (boolean bottomUp : new boolean[] {true, false}) {
            Set<BlockPos> seen = new HashSet<>();
            int lastY = bottomUp ? Integer.MIN_VALUE : Integer.MAX_VALUE;
            for (int i = 0; i < cells; i++) {
                BlockPos pos = BuilderSelection.cellAt(box, i, bottomUp);
                helper.assertTrue(box.isInside(pos), pos + " 不在选区里");
                helper.assertTrue(seen.add(pos), pos + " 出现了两次");
                helper.assertTrue(bottomUp ? pos.getY() >= lastY : pos.getY() <= lastY, (bottomUp ? "从下往上" : "从上往下") + "的顺序乱了：" + pos);
                lastY = pos.getY();
            }
            BlockPos first = BuilderSelection.cellAt(box, 0, bottomUp);
            helper.assertTrue(first.equals(new BlockPos(3, bottomUp ? 8 : 10, -3)), "第一格应是 (3, " + (bottomUp ? 8 : 10) + ", -3)，实际 " + first);
            helper.assertTrue(BuilderSelection.cellAt(box, 1, bottomUp).equals(new BlockPos(3, first.getY(), -2)), "同一个 x 里先走 z");
            helper.assertTrue(BuilderSelection.cellAt(box, 2, bottomUp).equals(new BlockPos(4, first.getY(), -3)), "z 走完再换 x");
        }
        BoundingBox unit = BuilderSelection.box(BlockPos.ZERO, BlockPos.ZERO);
        helper.assertTrue(BuilderSelection.distanceSqToBox(new Vec3(0.5D, 0.5D, 0.5D), unit) == 0D, "在格子里距离为 0");
        helper.assertTrue(Math.abs(BuilderSelection.distanceSqToBox(new Vec3(4D, 0.5D, 0.5D), unit) - 9D) < 1E-9, "离 x = 1 的面 3 格，平方 9");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 选点、选定方块
    // ------------------------------------------------------------------

    /** 右键方块走 onItemUseFirst：依次设两个点（带维度），再点提示「已设置」；潜行点石头选定；潜行点箱子不行（不会开箱子）；假玩家 PASS、不改 NBT */
    @GameTest(template = EMPTY)
    public static void builderSelectPointsAndBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos stone = new BlockPos(1, 1, 1);
        BlockPos dirt = new BlockPos(3, 2, 3);
        BlockPos chest = new BlockPos(3, 1, 1);
        helper.setBlock(stone, Blocks.STONE);
        helper.setBlock(dirt, Blocks.DIRT);
        helper.setBlock(chest, Blocks.CHEST);
        ItemStack stack = wand(BuilderMode.FILL, FULL);
        ServerPlayer player = holding(helper, stack);
        MagicalBuilderItem item = (MagicalBuilderItem) stack.getItem();

        helper.assertTrue(click(helper, item, player, stack, stone) == InteractionResult.CONSUME, "第一次点方块应设第一点");
        helper.assertTrue(helper.absolutePos(stone).equals(BuilderSelection.first(stack)) && BuilderSelection.second(stack) == null, "第一点应是石头那格");
        helper.assertTrue(BuilderSelection.inDimension(stack, level.dimension()), "点里应记着维度");
        helper.assertTrue(click(helper, item, player, stack, dirt) == InteractionResult.CONSUME, "第二次点方块应设第二点");
        helper.assertTrue(helper.absolutePos(dirt).equals(BuilderSelection.second(stack)), "第二点应是泥土那格");
        helper.assertTrue(MagicalBuilderItem.selectPoint(player, stack, level, helper.absolutePos(chest)) == MagicalBuilderItem.PointResult.BOTH_SET,
                "两个点都有了再点只提示");
        helper.assertTrue(helper.absolutePos(dirt).equals(BuilderSelection.second(stack)), "已设置时第二点不变");

        player.setShiftKeyDown(true);
        helper.assertTrue(click(helper, item, player, stack, stone) == InteractionResult.CONSUME, "潜行点石头应能选定");
        helper.assertTrue(MagicalBuilderItem.storedBlock(stack) == Blocks.STONE, "选定方块应是石头");
        helper.assertTrue(click(helper, item, player, stack, chest) == InteractionResult.FAIL, "箱子不能选定");
        helper.assertTrue(MagicalBuilderItem.storedBlock(stack) == Blocks.STONE, "选箱子失败后原来的石头还在");

        ItemStack untouched = wand(BuilderMode.FILL, FULL);
        net.minecraftforge.common.util.FakePlayer fake = ProtectionFrameworkTests.fakePlayer(helper);
        fake.setItemInHand(InteractionHand.MAIN_HAND, untouched);
        InteractionResult fakeResult = untouched.onItemUseFirst(new UseOnContext(fake, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(stone)), Direction.UP, helper.absolutePos(stone), false)));
        fake.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.assertTrue(fakeResult == InteractionResult.PASS && untouched.getTag() != null && BuilderSelection.first(untouched) == null,
                "假玩家拿着魔杖什么都不做，实际 " + fakeResult);
        helper.assertTrue(BuilderJobs.start(fake, InteractionHand.MAIN_HAND) == BuilderJobs.StartResult.NOT_REAL_PLAYER, "假玩家开不了工");

        // 换到别的维度点一下：当成新选区的第一点
        ServerLevel nether = level.getServer().getLevel(Level.NETHER);
        if (nether != null) {
            player.setShiftKeyDown(false);
            helper.assertTrue(MagicalBuilderItem.selectPoint(player, stack, nether, BlockPos.ZERO) == MagicalBuilderItem.PointResult.FIRST,
                    "在别的维度点应重设第一点");
            helper.assertTrue(BuilderSelection.inDimension(stack, Level.NETHER) && BuilderSelection.second(stack) == null, "新选区在下界、第二点清掉");
        }
        helper.succeed();
    }

    private static InteractionResult click(GameTestHelper helper, MagicalBuilderItem item, ServerPlayer player, ItemStack stack, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        return item.onItemUseFirst(stack, new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
    }

    /** 能选：石头、楼梯、原木、台阶、沙子、小麦；不能选：空气、水、岩浆、火、盆栽、基岩、屏障、箱子、营火、告示牌、门、床、向日葵、细雪 */
    @GameTest(template = EMPTY)
    public static void builderRecordRules(GameTestHelper helper) {
        BlockState[] allowed = {Blocks.STONE.defaultBlockState(), Blocks.OAK_STAIRS.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState(),
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE), Blocks.SAND.defaultBlockState(),
                Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7)};
        for (BlockState state : allowed) {
            helper.assertTrue(MagicalBuilderItem.recordFailure(state, EmptyBlockGetter.INSTANCE, BlockPos.ZERO) == null, state + " 应能选定");
        }
        Object[][] rejected = {
                {Blocks.AIR.defaultBlockState(), "air"},
                {Blocks.WATER.defaultBlockState(), "liquid"},
                {Blocks.LAVA.defaultBlockState(), "liquid"},
                {Blocks.FIRE.defaultBlockState(), "no_item"},
                {Blocks.POTTED_DANDELION.defaultBlockState(), "no_item"},
                {Blocks.BEDROCK.defaultBlockState(), "unbreakable"},
                {Blocks.BARRIER.defaultBlockState(), "unbreakable"},
                {Blocks.CHEST.defaultBlockState(), "block_entity"},
                {Blocks.CAMPFIRE.defaultBlockState(), "block_entity"},
                {Blocks.OAK_SIGN.defaultBlockState(), "block_entity"},
                {Blocks.OAK_DOOR.defaultBlockState(), "two_part"},
                {Blocks.RED_BED.defaultBlockState(), "block_entity"},
                {Blocks.SUNFLOWER.defaultBlockState(), "two_part"},
                {Blocks.POWDER_SNOW.defaultBlockState(), "bucket"},
        };
        for (Object[] row : rejected) {
            BlockState state = (BlockState) row[0];
            String reason = MagicalBuilderItem.recordFailure(state, EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            helper.assertTrue(row[1].equals(reason), state + " 不能选定的原因应是 " + row[1] + "，实际 " + reason);
        }
        // 世界里点一下：被拒时 NBT 不变
        BlockPos chest = new BlockPos(1, 1, 1);
        BlockPos bedrock = new BlockPos(2, 1, 1);
        BlockPos door = new BlockPos(3, 1, 1);
        helper.setBlock(new BlockPos(3, 0, 1), Blocks.STONE);
        helper.setBlock(chest, Blocks.CHEST);
        helper.setBlock(bedrock, Blocks.BEDROCK);
        helper.setBlock(door, Blocks.OAK_DOOR);
        ItemStack stack = wand(BuilderMode.FILL, FULL);
        ServerPlayer player = holding(helper, stack);
        for (BlockPos pos : new BlockPos[] {chest, bedrock, door}) {
            helper.assertTrue(!MagicalBuilderItem.recordBlock(player, stack, helper.getLevel(), helper.absolutePos(pos)), pos + " 不应能选定");
        }
        helper.assertTrue(BuilderSelection.storedBlock(stack) == null, "都被拒了，魔杖里不应有选定方块");
        helper.succeed();
    }

    /** 两点距离 48 能设、49 不能；16³ 能设、17×16×16 超过 4 096 格不能；被拒时第二点不设 */
    @GameTest(template = EMPTY)
    public static void builderPointLimits(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = holding(helper, wand(BuilderMode.FILL, FULL));
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        Object[][] cases = {
                {new BlockPos(48, 0, 0), true},
                {new BlockPos(49, 0, 0), false},
                {new BlockPos(15, 15, 15), true},
                {new BlockPos(16, 15, 15), false},
                {new BlockPos(15, 16, 15), false},
        };
        for (Object[] row : cases) {
            BlockPos offset = (BlockPos) row[0];
            boolean ok = (Boolean) row[1];
            ItemStack stack = wand(BuilderMode.FILL, FULL);
            helper.assertTrue(MagicalBuilderItem.selectPoint(player, stack, level, origin) == MagicalBuilderItem.PointResult.FIRST, "第一点应能设");
            MagicalBuilderItem.PointResult result = MagicalBuilderItem.selectPoint(player, stack, level, origin.offset(offset));
            helper.assertTrue(result == (ok ? MagicalBuilderItem.PointResult.SECOND : MagicalBuilderItem.PointResult.REJECTED),
                    "偏移 " + offset.toShortString() + " 应" + (ok ? "能" : "不能") + "设第二点，实际 " + result);
            helper.assertTrue((BuilderSelection.second(stack) != null) == ok, "第二点" + (ok ? "应已设" : "不应设"));
        }
        helper.assertTrue(ECBuilder.Config.maxPointDistance() == 48 && ECBuilder.Config.maxVolume() == 4096, "默认上限应是 48 格 / 4 096 格");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 填充
    // ------------------------------------------------------------------

    /** 只填空气格：用掉的木板 = 放下的格数，MRU 少 25 × 格数，已有的石头不动 */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public static void builderFillPlacesIntoAir(GameTestHelper helper) {
        BlockPos s1 = new BlockPos(2, 1, 2);
        BlockPos s2 = new BlockPos(3, 2, 3);
        helper.setBlock(s1, Blocks.STONE);
        helper.setBlock(s2, Blocks.STONE);
        ItemStack stack = wand(helper, BuilderMode.FILL, A, B, Blocks.OAK_PLANKS, FULL);
        ServerPlayer player = holding(helper, stack, new ItemStack(Items.OAK_PLANKS, 64));
        start(helper, player);
        awaitDone(helper, player, () -> {
            assertResult(helper, player, BuilderJobs.StopReason.FINISHED, 16);
            helper.assertTrue(countBox(helper, A, B, Blocks.OAK_PLANKS) == 16, "16 个空气格应都填上木板");
            helper.assertTrue(helper.getBlockState(s1).is(Blocks.STONE) && helper.getBlockState(s2).is(Blocks.STONE), "已有的石头不动");
            helper.assertTrue(count(player, Items.OAK_PLANKS) == 48, "应用掉 16 块木板，剩 " + count(player, Items.OAK_PLANKS));
            helper.assertTrue(mru(stack) == FULL - 16 * PLACE, "MRU 应少 16 × 25，实际 " + mru(stack));
        });
    }

    /** 只有 5 块木板：照顺序（最低一层、x 小的先、同 x 里 z 小的先）放 5 格就停，只扣这 5 格 */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public static void builderFillOutOfMaterial(GameTestHelper helper) {
        ItemStack stack = wand(helper, BuilderMode.FILL, A, B, Blocks.OAK_PLANKS, FULL);
        ServerPlayer player = holding(helper, stack, new ItemStack(Items.OAK_PLANKS, 5));
        start(helper, player);
        awaitDone(helper, player, () -> {
            assertResult(helper, player, BuilderJobs.StopReason.OUT_OF_MATERIAL, 5);
            BlockPos[] first5 = {new BlockPos(1, 1, 1), new BlockPos(1, 1, 2), new BlockPos(1, 1, 3), new BlockPos(2, 1, 1), new BlockPos(2, 1, 2)};
            for (BlockPos pos : first5) {
                helper.assertTrue(helper.getBlockState(pos).is(Blocks.OAK_PLANKS), pos.toShortString() + " 应是最先放的 5 格之一");
            }
            helper.assertTrue(countBox(helper, A, B, Blocks.OAK_PLANKS) == 5, "只应放 5 格");
            helper.assertTrue(count(player, Items.OAK_PLANKS) == 0 && mru(stack) == FULL - 5 * PLACE, "木板用完、MRU 只扣 5 格，实际 " + mru(stack));
        });
    }

    /** 魔杖只有 85 MRU：放 3 格（75）就停，剩 10，只用了 3 块木板 */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public static void builderFillOutOfMru(GameTestHelper helper) {
        ItemStack stack = wand(helper, BuilderMode.FILL, A, B, Blocks.OAK_PLANKS, 3 * PLACE + 10);
        ServerPlayer player = holding(helper, stack, new ItemStack(Items.OAK_PLANKS, 64));
        start(helper, player);
        awaitDone(helper, player, () -> {
            assertResult(helper, player, BuilderJobs.StopReason.NO_MRU, 3);
            helper.assertTrue(countBox(helper, A, B, Blocks.OAK_PLANKS) == 3, "只应放 3 格");
            helper.assertTrue(mru(stack) == 10 && count(player, Items.OAK_PLANKS) == 61, "MRU 应剩 10、木板 61，实际 " + mru(stack) + " / " + count(player, Items.OAK_PLANKS));
        });
    }

    /** 别人领地里的一格：放置事件被拒，还是空气、不扣 MRU、木板原样放回；只问了一次 */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public static void builderFillRespectsClaims(GameTestHelper helper) {
        BlockPos denied = new BlockPos(2, 1, 2);
        ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(denied);
        ItemStack stack = wand(helper, BuilderMode.FILL, A, B, Blocks.OAK_PLANKS, FULL);
        ServerPlayer player = holding(helper, stack, new ItemStack(Items.OAK_PLANKS, 64));
        start(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    claims.close();
                    assertResult(helper, player, BuilderJobs.StopReason.FINISHED, 17);
                    helper.assertTrue(helper.getBlockState(denied).isAir(), "被拒的格子应还是空气");
                    // 开工前探测可能已经探到它（之后先探后放、不再真放），所以真放置事件 0 或 1 次
                    helper.assertTrue(claims.places(denied) <= 1 && claims.places(denied) + claims.probes(denied) >= 1,
                            "被拒的格子应问到、最多真放一次，实际放置 " + claims.places(denied) + "、探测 " + claims.probes(denied));
                    helper.assertTrue(countBox(helper, A, B, Blocks.OAK_PLANKS) == 17, "其余 17 格照填");
                    helper.assertTrue(count(player, Items.OAK_PLANKS) == 64 - 17, "被拒那格的木板应放回，剩 " + count(player, Items.OAK_PLANKS));
                    helper.assertTrue(mru(stack) == FULL - 17 * PLACE, "只扣做成的 17 格，实际 " + mru(stack));
                })
                .thenSucceed();
    }

    /** 玩家站着的两格（(0, 1, 0)、(0, 2, 0)）不填，其余 6 格照填 */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public static void builderFillSkipsPlayer(GameTestHelper helper) {
        BlockPos a = new BlockPos(0, 1, 0);
        BlockPos b = new BlockPos(1, 2, 1);
        ItemStack stack = wand(helper, BuilderMode.FILL, a, b, Blocks.OAK_PLANKS, FULL);
        ServerPlayer player = holding(helper, stack, new ItemStack(Items.OAK_PLANKS, 64));
        start(helper, player);
        awaitDone(helper, player, () -> {
            assertResult(helper, player, BuilderJobs.StopReason.FINISHED, 6);
            helper.assertTrue(helper.getBlockState(new BlockPos(0, 1, 0)).isAir() && helper.getBlockState(new BlockPos(0, 2, 0)).isAir(), "玩家站着的格子不应填");
            helper.assertTrue(countBox(helper, a, b, Blocks.OAK_PLANKS) == 6 && count(player, Items.OAK_PLANKS) == 58, "其余 6 格应填上");
        });
    }

    /**
     * 按物品自己的放置规则放单格：记双层石台阶放出下半块、记 4 个的海泡菜放 1 个、记 4 根的蜡烛放 1 根、记成熟小麦放 age 0（用掉小麦种子），
     * 每格只用 1 个材料
     */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public static void builderPlacementRules(GameTestHelper helper) {
        fill(helper, new BlockPos(1, 0, 2), new BlockPos(3, 0, 4), Blocks.STONE);
        helper.setBlock(new BlockPos(4, 0, 2), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        helper.setBlock(new BlockPos(4, 0, 4), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        helper.setBlock(new BlockPos(1, 1, 4), Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE));
        helper.setBlock(new BlockPos(2, 1, 4), Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, 4));
        helper.setBlock(new BlockPos(3, 1, 4), Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 4));
        helper.setBlock(new BlockPos(4, 1, 4), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        Item[] materials = {Items.STONE_SLAB, Items.SEA_PICKLE, Items.CANDLE, Items.WHEAT_SEEDS};
        ServerPlayer[] players = new ServerPlayer[4];
        for (int i = 0; i < 4; i++) {
            BlockPos cell = new BlockPos(1 + i, 1, 2);
            ItemStack stack = wand(helper, BuilderMode.FILL, cell, cell, null, FULL);
            players[i] = holding(helper, stack, new ItemStack(materials[i], 8));
            helper.assertTrue(MagicalBuilderItem.recordBlock(players[i], stack, helper.getLevel(), helper.absolutePos(new BlockPos(1 + i, 1, 4))),
                    "应能选定第 " + i + " 个样本");
            start(helper, players[i]);
        }
        helper.startSequence()
                .thenWaitUntil(() -> {
                    for (ServerPlayer player : players) {
                        helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工");
                    }
                })
                .thenExecute(() -> {
                    for (int i = 0; i < 4; i++) {
                        assertResult(helper, players[i], BuilderJobs.StopReason.FINISHED, 1);
                        helper.assertTrue(count(players[i], materials[i]) == 7, materials[i] + " 应只用 1 个，剩 " + count(players[i], materials[i]));
                    }
                    BlockState slab = helper.getBlockState(new BlockPos(1, 1, 2));
                    helper.assertTrue(slab.is(Blocks.STONE_SLAB) && slab.getValue(SlabBlock.TYPE) == SlabType.BOTTOM, "应放出下半块台阶，实际 " + slab);
                    BlockState pickle = helper.getBlockState(new BlockPos(2, 1, 2));
                    helper.assertTrue(pickle.is(Blocks.SEA_PICKLE) && pickle.getValue(SeaPickleBlock.PICKLES) == 1, "海泡菜应只放 1 个，实际 " + pickle);
                    BlockState candle = helper.getBlockState(new BlockPos(3, 1, 2));
                    helper.assertTrue(candle.is(Blocks.CANDLE) && candle.getValue(CandleBlock.CANDLES) == 1, "蜡烛应只放 1 根，实际 " + candle);
                    BlockState wheat = helper.getBlockState(new BlockPos(4, 1, 2));
                    helper.assertTrue(wheat.is(Blocks.WHEAT) && wheat.getValue(CropBlock.AGE) == 0, "小麦应从 age 0 长起，实际 " + wheat);
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------
    // 清除
    // ------------------------------------------------------------------

    /** 16 格石头 + 2 格空气：石头全拆、圆石 16 进背包、地上没有掉落物，MRU 只按 16 格收（空气格不收） */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public static void builderClearHarvestsIntoInventory(GameTestHelper helper) {
        fill(helper, A, B, Blocks.STONE);
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR);
        helper.setBlock(new BlockPos(3, 2, 3), Blocks.AIR);
        ItemStack stack = wand(helper, BuilderMode.CLEAR, A, B, null, FULL);
        ServerPlayer player = holding(helper, stack);
        start(helper, player);
        awaitDone(helper, player, () -> {
            assertResult(helper, player, BuilderJobs.StopReason.FINISHED, 16);
            helper.assertTrue(countBox(helper, A, B, Blocks.AIR) == 18, "选区应清空");
            helper.assertTrue(count(player, Items.COBBLESTONE) == 16, "圆石 16 应进背包，实际 " + count(player, Items.COBBLESTONE));
            helper.assertTrue(itemEntities(helper, 5, 4, 5) == 0, "地上不应有掉落物");
            helper.assertTrue(mru(stack) == FULL - 16 * BREAK, "MRU 应少 16 × 250，实际 " + mru(stack));
        });
    }

    /** 别人领地里的一格：只问一次、不动、不扣、没有它的掉落 */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public static void builderClearRespectsClaims(GameTestHelper helper) {
        fill(helper, A, B, Blocks.STONE);
        BlockPos denied = new BlockPos(2, 1, 2);
        ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(denied);
        ItemStack stack = wand(helper, BuilderMode.CLEAR, A, B, null, FULL);
        ServerPlayer player = holding(helper, stack);
        start(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    claims.close();
                    assertResult(helper, player, BuilderJobs.StopReason.FINISHED, 17);
                    helper.assertTrue(helper.getBlockState(denied).is(Blocks.STONE), "被拒的格子不动");
                    // 开工前探测会先探到这一格、之后一律先探后放，真破坏事件最多一次（探测事件另算，见 Claims.probes）
                    helper.assertTrue(claims.breaks(denied) >= 1 && claims.breaks(denied) - claims.probes(denied) <= 1,
                            "被拒的格子应问到、最多真问一次，实际 " + claims.breaks(denied) + "（探测 " + claims.probes(denied) + "）");
                    helper.assertTrue(count(player, Items.COBBLESTONE) == 17 && itemEntities(helper, 5, 4, 5) == 0, "只有 17 格的圆石，地上没有掉落物");
                    helper.assertTrue(mru(stack) == FULL - 17 * BREAK, "只扣 17 格，实际 " + mru(stack));
                    // 每格走的是同一条删除路径：其余每格都正好问过一次
                    for (BlockPos pos : BlockPos.betweenClosed(A, B)) {
                        BlockPos cell = pos.immutable();
                        if (!cell.equals(denied)) {
                            int real = claims.breaks(cell) - claims.probes(cell);
                            helper.assertTrue(real == 1, cell.toShortString() + " 应正好发一次真破坏事件，实际 " + real);
                        }
                    }
                    BuilderJobs.Result result = BuilderJobs.lastResult(player.getUUID());
                    helper.assertTrue(result.denied() == 1, "被拒应记 1 格，实际 " + result.denied());
                })
                .thenSucceed();
    }

    /**
     * 不碰的格子：装着钻石的箱子、基岩、屏障、（被屏障围住的）水源都原样、连破坏事件都不发；其余 10 格石头照拆
     */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public static void builderClearSkipsProtected(GameTestHelper helper) {
        fill(helper, A, B, Blocks.STONE);
        BlockPos chest = new BlockPos(1, 1, 1);
        BlockPos bedrock = new BlockPos(3, 1, 1);
        BlockPos water = new BlockPos(2, 1, 2);
        BlockPos[] barriers = {new BlockPos(1, 1, 2), new BlockPos(3, 1, 2), new BlockPos(2, 1, 1), new BlockPos(2, 1, 3), new BlockPos(2, 2, 2)};
        helper.setBlock(new BlockPos(2, 0, 2), Blocks.STONE);
        for (BlockPos pos : barriers) {
            helper.setBlock(pos, Blocks.BARRIER);
        }
        helper.setBlock(water, Blocks.WATER);
        helper.setBlock(bedrock, Blocks.BEDROCK);
        helper.setBlock(chest, Blocks.CHEST);
        ChestBlockEntity box = (ChestBlockEntity) helper.getBlockEntity(chest);
        box.setItem(0, new ItemStack(Items.DIAMOND, 3));
        ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper);
        ItemStack stack = wand(helper, BuilderMode.CLEAR, A, B, null, FULL);
        ServerPlayer player = holding(helper, stack);
        start(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    claims.close();
                    assertResult(helper, player, BuilderJobs.StopReason.FINISHED, 10);
                    helper.assertTrue(helper.getBlockState(chest).is(Blocks.CHEST), "箱子不应被拆");
                    ChestBlockEntity after = (ChestBlockEntity) helper.getBlockEntity(chest);
                    helper.assertTrue(after.getItem(0).is(Items.DIAMOND) && after.getItem(0).getCount() == 3, "箱子里的钻石应还在");
                    helper.assertTrue(helper.getBlockState(bedrock).is(Blocks.BEDROCK), "基岩不动");
                    for (BlockPos pos : barriers) {
                        helper.assertTrue(helper.getBlockState(pos).is(Blocks.BARRIER), "屏障不动：" + pos.toShortString());
                        helper.assertTrue(claims.breaks(pos) == 0, "屏障不应收到破坏事件");
                    }
                    helper.assertTrue(helper.getBlockState(water).is(Blocks.WATER) && helper.getBlockState(water).getFluidState().isSource(), "水源不变");
                    helper.assertTrue(claims.breaks(water) == 0 && claims.breaks(chest) == 0 && claims.breaks(bedrock) == 0,
                            "水、箱子、基岩连破坏事件都不应发");
                    helper.assertTrue(count(player, Items.COBBLESTONE) == 10 && mru(stack) == FULL - 10 * BREAK, "只拆 10 格石头，实际 MRU " + mru(stack));
                })
                .thenSucceed();
    }

    /** 选定石头、清除选定方块：只拆 5 格石头，4 格泥土不动 */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public static void builderClearMatching(GameTestHelper helper) {
        BlockPos b = new BlockPos(3, 1, 3);
        checker(helper, b);
        ItemStack stack = wand(helper, BuilderMode.CLEAR_MATCHING, A, b, Blocks.STONE, FULL);
        ServerPlayer player = holding(helper, stack);
        start(helper, player);
        awaitDone(helper, player, () -> {
            assertResult(helper, player, BuilderJobs.StopReason.FINISHED, 5);
            helper.assertTrue(countBox(helper, A, b, Blocks.STONE) == 0 && countBox(helper, A, b, Blocks.DIRT) == 4, "石头应拆光、泥土不动");
            helper.assertTrue(count(player, Items.COBBLESTONE) == 5 && count(player, Items.DIRT) == 0 && mru(stack) == FULL - 5 * BREAK,
                    "只拆 5 格石头，实际 MRU " + mru(stack));
        });
    }

    /** 选定石头、清除选定以外：只拆 4 格泥土，5 格石头不动 */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public static void builderClearOthers(GameTestHelper helper) {
        BlockPos b = new BlockPos(3, 1, 3);
        checker(helper, b);
        ItemStack stack = wand(helper, BuilderMode.CLEAR_OTHERS, A, b, Blocks.STONE, FULL);
        ServerPlayer player = holding(helper, stack);
        start(helper, player);
        awaitDone(helper, player, () -> {
            assertResult(helper, player, BuilderJobs.StopReason.FINISHED, 4);
            helper.assertTrue(countBox(helper, A, b, Blocks.STONE) == 5 && countBox(helper, A, b, Blocks.DIRT) == 0, "泥土应拆光、石头不动");
            helper.assertTrue(count(player, Items.DIRT) == 4 && count(player, Items.COBBLESTONE) == 0 && mru(stack) == FULL - 4 * BREAK,
                    "只拆 4 格泥土，实际 MRU " + mru(stack));
        });
    }

    /** (1, 1, 1)~b 这一层摆成棋盘：x + z 是偶数的 5 格石头，其余 4 格泥土 */
    private static void checker(GameTestHelper helper, BlockPos b) {
        for (BlockPos pos : BlockPos.betweenClosed(A, b)) {
            helper.setBlock(pos.immutable(), (pos.getX() + pos.getZ()) % 2 == 0 ? Blocks.STONE : Blocks.DIRT);
        }
    }

    /**
     * 两根 3 格高的沙柱同时清（全服每刻只做 1 格，两人轮流，每根柱子每 2 刻少 1 格——比沙子下落的 2 刻延迟慢）：
     * 从上往下拆，全程没有下落的沙子，最后全拆光、沙子进背包
     */
    @GameTest(template = EMPTY, batch = SLOW_BATCH, timeoutTicks = 200)
    public static void builderClearTopDownSand(GameTestHelper helper) {
        BlockPos[] columns = {new BlockPos(1, 1, 2), new BlockPos(3, 1, 2)};
        ServerPlayer[] players = new ServerPlayer[2];
        for (int i = 0; i < 2; i++) {
            BlockPos bottom = columns[i];
            BlockPos top = bottom.above(2);
            helper.setBlock(bottom.below(), Blocks.STONE);
            fill(helper, bottom, top, Blocks.SAND);
            players[i] = holding(helper, wand(helper, BuilderMode.CLEAR, bottom, top, null, FULL));
        }
        for (ServerPlayer player : players) {
            start(helper, player);
        }
        AABB area = new AABB(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(5, 4, 5)));
        helper.onEachTick(() -> helper.assertTrue(helper.getLevel().getEntitiesOfClass(FallingBlockEntity.class, area).isEmpty(), "沙子不应往下掉"));
        helper.startSequence()
                .thenWaitUntil(() -> {
                    for (ServerPlayer player : players) {
                        helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工");
                    }
                })
                .thenExecute(() -> {
                    for (int i = 0; i < 2; i++) {
                        assertResult(helper, players[i], BuilderJobs.StopReason.FINISHED, 3);
                        helper.assertTrue(countBox(helper, columns[i], columns[i].above(2), Blocks.AIR) == 3, "沙柱应拆光");
                        helper.assertTrue(count(players[i], Items.SAND) == 3, "3 块沙子应进背包");
                    }
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------
    // 全部替换
    // ------------------------------------------------------------------

    /** 空气格放（25）、石头换成木板（300，圆石进背包）、已经是木板的不动不扣 */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public static void builderReplaceAll(GameTestHelper helper) {
        BlockPos b = new BlockPos(3, 1, 3);
        fill(helper, new BlockPos(2, 1, 1), new BlockPos(2, 1, 3), Blocks.STONE);
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.STONE);
        helper.setBlock(new BlockPos(3, 1, 1), Blocks.OAK_PLANKS);
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.OAK_PLANKS);
        ItemStack stack = wand(helper, BuilderMode.REPLACE_ALL, A, b, Blocks.OAK_PLANKS, FULL);
        ServerPlayer player = holding(helper, stack, new ItemStack(Items.OAK_PLANKS, 64));
        start(helper, player);
        awaitDone(helper, player, () -> {
            assertResult(helper, player, BuilderJobs.StopReason.FINISHED, 7);
            helper.assertTrue(countBox(helper, A, b, Blocks.OAK_PLANKS) == 9, "9 格应都是木板");
            helper.assertTrue(count(player, Items.COBBLESTONE) == 4 && count(player, Items.OAK_PLANKS) == 64 - 7, "圆石 4、木板用掉 7");
            helper.assertTrue(mru(stack) == FULL - 3 * PLACE - 4 * REPLACE, "MRU 应少 3 × 25 + 4 × 300，实际 " + mru(stack));
        });
    }

    /** 液体格：填充跳过（不动不扣），全部替换按放置收 25；水被屏障围着，不会流出去 */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public static void builderReplaceAllLiquid(GameTestHelper helper) {
        BlockPos water = new BlockPos(2, 1, 2);
        helper.setBlock(water.below(), Blocks.STONE);
        for (Direction direction : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP}) {
            helper.setBlock(water.relative(direction), Blocks.BARRIER);
        }
        helper.setBlock(water, Blocks.WATER);
        ItemStack fillWand = wand(helper, BuilderMode.FILL, water, water, Blocks.OAK_PLANKS, FULL);
        ServerPlayer filler = holding(helper, fillWand, new ItemStack(Items.OAK_PLANKS, 8));
        ItemStack replaceWand = wand(helper, BuilderMode.REPLACE_ALL, water, water, Blocks.OAK_PLANKS, FULL);
        ServerPlayer replacer = holding(helper, replaceWand, new ItemStack(Items.OAK_PLANKS, 8));
        start(helper, filler);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(filler.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    assertResult(helper, filler, BuilderJobs.StopReason.FINISHED, 0);
                    helper.assertTrue(helper.getBlockState(water).is(Blocks.WATER) && mru(fillWand) == FULL && count(filler, Items.OAK_PLANKS) == 8,
                            "填充模式不碰液体");
                    start(helper, replacer);
                })
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(replacer.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    assertResult(helper, replacer, BuilderJobs.StopReason.FINISHED, 1);
                    helper.assertTrue(helper.getBlockState(water).is(Blocks.OAK_PLANKS), "水应换成木板");
                    helper.assertTrue(mru(replaceWand) == FULL - PLACE && count(replacer, Items.OAK_PLANKS) == 7, "液体格按放置收 25，实际 " + mru(replaceWand));
                })
                .thenSucceed();
    }

    /** 只拒放置、放行破坏的格子：石头拆了、格子留成空气、圆石照给、只扣 250、木板没少；这一格记作「只拆未放」，不算做成 */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public static void builderReplaceDenyPlace(GameTestHelper helper) {
        BlockPos cell = new BlockPos(2, 1, 2);
        helper.setBlock(cell, Blocks.STONE);
        ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).denyPlace(cell);
        ItemStack stack = wand(helper, BuilderMode.REPLACE_ALL, cell, cell, Blocks.OAK_PLANKS, FULL);
        ServerPlayer player = holding(helper, stack, new ItemStack(Items.OAK_PLANKS, 64));
        start(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    claims.close();
                    assertResult(helper, player, BuilderJobs.StopReason.FINISHED, 0);
                    helper.assertTrue(BuilderJobs.lastResult(player.getUUID()).brokenOnly() == 1, "应记 1 格只拆未放");
                    helper.assertTrue(helper.getBlockState(cell).isAir(), "放置被拒的格子应留成空气");
                    helper.assertTrue(claims.breaks(cell) - claims.probes(cell) == 1 && claims.places(cell) == 1, "破坏、放置各真问一次（探测不算）");
                    helper.assertTrue(count(player, Items.COBBLESTONE) == 1 && count(player, Items.OAK_PLANKS) == 64, "圆石照给、木板没少");
                    helper.assertTrue(mru(stack) == FULL - BREAK, "只按拆收 250，实际 " + mru(stack));
                })
                .thenSucceed();
    }

    /** 背包里没有木板：石头一块都不拆（原作先吃掉方块再看材料，方块白丢），不扣 MRU、不发破坏事件 */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public static void builderReplaceNeedsMaterial(GameTestHelper helper) {
        BlockPos b = new BlockPos(3, 1, 1);
        fill(helper, A, b, Blocks.STONE);
        ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper);
        // 改过名的木板不算材料
        ItemStack named = new ItemStack(Items.OAK_PLANKS, 64);
        named.setHoverName(net.minecraft.network.chat.Component.literal("keep"));
        ItemStack stack = wand(helper, BuilderMode.REPLACE_ALL, A, b, Blocks.OAK_PLANKS, FULL);
        ServerPlayer player = holding(helper, stack, named);
        start(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    claims.close();
                    assertResult(helper, player, BuilderJobs.StopReason.OUT_OF_MATERIAL, 0);
                    helper.assertTrue(countBox(helper, A, b, Blocks.STONE) == 3, "石头不应被拆");
                    for (BlockPos pos : BlockPos.betweenClosed(A, b)) {
                        BlockPos cell = pos.immutable();
                        helper.assertTrue(claims.breaks(cell) - claims.probes(cell) == 0, "没材料时不应发真破坏事件（开工前探测的不算）");
                    }
                    helper.assertTrue(mru(stack) == FULL && count(player, Items.OAK_PLANKS) == 64 && count(player, Items.COBBLESTONE) == 0,
                            "什么都不扣、改过名的木板没动");
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------
    // 创造模式
    // ------------------------------------------------------------------

    /** 创造模式：填充不扣 MRU、不用材料；清除不给掉落、地上也没有 */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public static void builderCreativeFree(GameTestHelper helper) {
        BlockPos fillA = new BlockPos(1, 1, 1);
        BlockPos fillB = new BlockPos(2, 1, 2);
        BlockPos clearA = new BlockPos(1, 2, 3);
        BlockPos clearB = new BlockPos(3, 2, 3);
        fill(helper, clearA, clearB, Blocks.STONE);
        ItemStack fillWand = wand(helper, BuilderMode.FILL, fillA, fillB, Blocks.OAK_PLANKS, 100);
        ServerPlayer filler = holding(helper, fillWand, new ItemStack(Items.OAK_PLANKS, 10));
        filler.setGameMode(GameType.CREATIVE);
        ItemStack clearWand = wand(helper, BuilderMode.CLEAR, clearA, clearB, null, 100);
        ServerPlayer clearer = holding(helper, clearWand);
        clearer.setGameMode(GameType.CREATIVE);
        start(helper, filler);
        start(helper, clearer);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(filler.getUUID()) && !BuilderJobs.isRunning(clearer.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    assertResult(helper, filler, BuilderJobs.StopReason.FINISHED, 4);
                    assertResult(helper, clearer, BuilderJobs.StopReason.FINISHED, 3);
                    helper.assertTrue(countBox(helper, fillA, fillB, Blocks.OAK_PLANKS) == 4 && countBox(helper, clearA, clearB, Blocks.AIR) == 3, "都应做完");
                    helper.assertTrue(mru(fillWand) == 100 && mru(clearWand) == 100, "创造模式不扣 MRU（只有 100，按生存算也不够清 3 格）");
                    helper.assertTrue(count(filler, Items.OAK_PLANKS) == 10, "创造模式不用材料");
                    helper.assertTrue(count(clearer, Items.COBBLESTONE) == 0 && itemEntities(helper, 5, 4, 5) == 0, "创造模式清除不给掉落");
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------
    // 调度
    // ------------------------------------------------------------------

    /** 每任务每刻 8、全服每刻 8：两人同时填 32 格，每刻合计不超过 8、两人每刻都有份，至少 8 刻做完 */
    @GameTest(template = EMPTY_LARGE, batch = BUDGET_BATCH, timeoutTicks = 200)
    public static void builderBudgetShared(GameTestHelper helper) {
        ServerPlayer[] players = new ServerPlayer[2];
        for (int i = 0; i < 2; i++) {
            BlockPos a = new BlockPos(1 + 5 * i, 1, 1);
            ItemStack stack = wand(helper, BuilderMode.FILL, a, a.offset(3, 1, 3), Blocks.OAK_PLANKS, FULL);
            players[i] = holding(helper, stack);
            players[i].setGameMode(GameType.CREATIVE);
        }
        for (ServerPlayer player : players) {
            start(helper, player);
        }
        int[] last = {0, 0};
        AtomicInteger ticks = new AtomicInteger();
        helper.onEachTick(() -> {
            int[] now = {doneOf(players[0].getUUID()), doneOf(players[1].getUUID())};
            int d0 = now[0] - last[0];
            int d1 = now[1] - last[1];
            helper.assertTrue(d0 + d1 <= 8, "两人一刻合计做了 " + (d0 + d1) + " 格，超过全服上限 8");
            if (now[0] < 32 && now[1] < 32 && (now[0] > 0 || now[1] > 0)) {
                helper.assertTrue(d0 > 0 && d1 > 0, "两人都在做时每刻都应有份：" + d0 + " / " + d1);
            }
            if (d0 + d1 > 0) {
                ticks.incrementAndGet();
            }
            last[0] = now[0];
            last[1] = now[1];
        });
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(players[0].getUUID()) && !BuilderJobs.isRunning(players[1].getUUID()), "还在施工"))
                .thenExecute(() -> {
                    assertResult(helper, players[0], BuilderJobs.StopReason.FINISHED, 32);
                    assertResult(helper, players[1], BuilderJobs.StopReason.FINISHED, 32);
                    helper.assertTrue(ticks.get() >= 8, "64 格每刻最多 8 格，至少要 8 刻，实际 " + ticks.get());
                })
                .thenSucceed();
    }

    /** 开工前的检查：没点、点在别的维度、没选定方块（清除不用）、选区太大、离得太远、区块没加载、冒险模式、手里不是魔杖 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void builderStartChecks(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 1, 1);
        BlockPos b = new BlockPos(2, 2, 2);
        expect(helper, holding(helper, wand(BuilderMode.FILL, FULL)), BuilderJobs.StartResult.NO_POINTS);

        ItemStack otherDim = wand(helper, BuilderMode.CLEAR, a, b, null, FULL);
        BuilderSelection.setFirst(otherDim, helper.absolutePos(a), Level.NETHER);
        BuilderSelection.setSecond(otherDim, helper.absolutePos(b));
        expect(helper, holding(helper, otherDim), BuilderJobs.StartResult.OTHER_DIMENSION);

        expect(helper, holding(helper, wand(helper, BuilderMode.FILL, a, b, null, FULL)), BuilderJobs.StartResult.NO_BLOCK);
        ItemStack staleBlock = wand(helper, BuilderMode.CLEAR_MATCHING, a, b, null, FULL);
        BuilderSelection.setStoredBlock(staleBlock, ForgeRegistries.BLOCKS.getKey(Blocks.CHEST));
        expect(helper, holding(helper, staleBlock), BuilderJobs.StartResult.NO_BLOCK);

        ItemStack big = wand(BuilderMode.CLEAR, FULL);
        BuilderSelection.setFirst(big, helper.absolutePos(a), helper.getLevel().dimension());
        BuilderSelection.setSecond(big, helper.absolutePos(a).offset(16, 15, 15));
        expect(helper, holding(helper, big), BuilderJobs.StartResult.TOO_BIG);

        ServerPlayer far = holding(helper, wand(helper, BuilderMode.CLEAR, a, b, null, FULL));
        far.moveTo(far.getX() + 100D, far.getY(), far.getZ());
        expect(helper, far, BuilderJobs.StartResult.OUT_OF_REACH);

        ServerPlayer unloaded = ProtectionFrameworkTests.realPlayer(helper);
        unloaded.moveTo(unloaded.getX() + 200000D, unloaded.getY(), unloaded.getZ());
        ItemStack remote = wand(BuilderMode.CLEAR, FULL);
        BuilderSelection.setFirst(remote, unloaded.blockPosition().offset(1, 0, 1), helper.getLevel().dimension());
        BuilderSelection.setSecond(remote, unloaded.blockPosition().offset(2, 1, 2));
        unloaded.setItemInHand(InteractionHand.MAIN_HAND, remote);
        expect(helper, unloaded, BuilderJobs.StartResult.NOT_LOADED);

        ServerPlayer adventure = holding(helper, wand(helper, BuilderMode.CLEAR, a, b, null, FULL));
        adventure.setGameMode(GameType.ADVENTURE);
        expect(helper, adventure, BuilderJobs.StartResult.CANNOT_BUILD);

        expect(helper, holding(helper, new ItemStack(Items.STICK)), BuilderJobs.StartResult.NOT_A_WAND);

        // 清除不用选定方块：选区全是空气，开工后马上做完、一格都没动
        ServerPlayer clear = holding(helper, wand(helper, BuilderMode.CLEAR, a, b, null, FULL));
        expect(helper, clear, BuilderJobs.StartResult.STARTED);
        awaitDone(helper, clear, () -> assertResult(helper, clear, BuilderJobs.StopReason.FINISHED, 0));
    }

    private static void expect(GameTestHelper helper, ServerPlayer player, BuilderJobs.StartResult expected) {
        BuilderJobs.StartResult result = BuilderJobs.start(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(result == expected, "开工应是 " + expected + "，实际 " + result);
    }

    /**
     * 一人一个任务：再开被拒、原任务照跑；潜行对空气右键先停下（两个点留着），再右键一次才清点（选定方块留着）；停下以后不再改方块
     */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 200)
    public static void builderBusyAndSneakStop(GameTestHelper helper) {
        ServerPlayer player = bigCreativeJob(helper);
        ItemStack stack = player.getMainHandItem();
        start(helper, player);
        helper.assertTrue(BuilderJobs.start(player, InteractionHand.MAIN_HAND) == BuilderJobs.StartResult.QUEUED, "有活在做时再开一单应排队");
        helper.assertTrue(BuilderJobs.start(player, InteractionHand.MAIN_HAND) == BuilderJobs.StartResult.BUSY, "排队名额用完再开应被拒");
        helper.assertTrue(BuilderJobs.isRunning(player.getUUID()), "原来的活应照跑");
        int[] stoppedAt = {-1};
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(BuilderJobs.progress(player.getUUID()) > 0, "还没开始放"))
                .thenExecute(() -> {
                    player.setShiftKeyDown(true);
                    stack.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
                    helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "潜行对空气右键应停下（排队的一起清掉）");
                    BuilderJobs.Result result = BuilderJobs.lastResult(player.getUUID());
                    helper.assertTrue(result != null && result.reason() == BuilderJobs.StopReason.STOPPED && result.done() < BIG, "应是自己停下的");
                    helper.assertTrue(BuilderSelection.hasBothPoints(stack), "停下时两个点应留着");
                    stoppedAt[0] = countBox(helper, BIG_A, BIG_B, Blocks.OAK_PLANKS);
                    helper.assertTrue(stoppedAt[0] == result.done(), "做成的格数应和世界里的木板数一致");
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(countBox(helper, BIG_A, BIG_B, Blocks.OAK_PLANKS) == stoppedAt[0], "停下以后不应再放");
                    stack.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
                    helper.assertTrue(BuilderSelection.first(stack) == null && BuilderSelection.second(stack) == null, "再右键一次应清掉两个点");
                    helper.assertTrue(MagicalBuilderItem.storedBlock(stack) == Blocks.OAK_PLANKS, "选定方块应留着");
                })
                .thenSucceed();
    }

    /** 换手（哪怕换成一模一样的另一根）就停，已经放好的不退 */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 200)
    public static void builderStopsOnHandSwap(GameTestHelper helper) {
        ServerPlayer player = bigCreativeJob(helper);
        interrupt(helper, player, () -> player.setItemInHand(InteractionHand.MAIN_HAND, player.getMainHandItem().copy()));
    }

    /** 走出 64 格就停 */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 200)
    public static void builderStopsWhenFar(GameTestHelper helper) {
        ServerPlayer player = bigCreativeJob(helper);
        interrupt(helper, player, () -> player.moveTo(player.getX() + 100D, player.getY(), player.getZ()));
    }

    /** 换了维度就停 */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 200)
    public static void builderStopsOnDimensionChange(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        if (nether == null) {
            helper.fail("GameTest 服里应有下界");
            return;
        }
        ServerPlayer player = bigCreativeJob(helper);
        interrupt(helper, player, () -> player.setServerLevel(nether));
    }

    /** 大选区 (1, 1, 1)~(9, 6, 9)：486 格，每刻最多 64 格，至少 8 刻 */
    private static final BlockPos BIG_A = new BlockPos(1, 1, 1);
    private static final BlockPos BIG_B = new BlockPos(9, 6, 9);
    private static final int BIG = 9 * 6 * 9;

    /** 创造模式填大选区（不用材料、不扣 MRU）的玩家，还没开工 */
    private static ServerPlayer bigCreativeJob(GameTestHelper helper) {
        ServerPlayer player = holding(helper, wand(helper, BuilderMode.FILL, BIG_A, BIG_B, Blocks.OAK_PLANKS, FULL));
        player.setGameMode(GameType.CREATIVE);
        return player;
    }

    /** 开工、等放下第一批以后做 action，应在下一刻因复核没过停下，已经放好的留着、之后不再放 */
    private static void interrupt(GameTestHelper helper, ServerPlayer player, Runnable action) {
        start(helper, player);
        int[] at = {-1};
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(BuilderJobs.progress(player.getUUID()) > 0, "还没开始放"))
                .thenExecute(action)
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    BuilderJobs.Result result = BuilderJobs.lastResult(player.getUUID());
                    helper.assertTrue(result != null && result.reason() == BuilderJobs.StopReason.INTERRUPTED, "应因复核没过停下，实际 " + result);
                    helper.assertTrue(result.done() > 0 && result.done() < BIG, "应只做了一部分，实际 " + result.done());
                    at[0] = countBox(helper, BIG_A, BIG_B, Blocks.OAK_PLANKS);
                    helper.assertTrue(at[0] == result.done(), "已经放好的应留着：世界里 " + at[0] + "，记录 " + result.done());
                })
                .thenIdle(3)
                .thenExecute(() -> helper.assertTrue(countBox(helper, BIG_A, BIG_B, Blocks.OAK_PLANKS) == at[0], "停下以后不应再放"))
                .thenSucceed();
    }

    // ------------------------------------------------------------------
    // 批量拆、被拒上限、放不下的格子
    // ------------------------------------------------------------------

    /**
     * 批量写法 {@code harvestGuarded(…, true)}（创世神之杖用）：放行的格子删掉、交出掉落，被拒的格子不动、返回 null；两种结论都不写探测缓存，
     * 之后对同一格 check 要重新探测一次。对照：4 参版照旧写缓存，之后的 check 命中缓存、不再探测
     */
    @GameTest(template = EMPTY)
    public static void builderBatchHarvestSkipsCache(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos open = new BlockPos(1, 1, 2);
        BlockPos claimed = new BlockPos(3, 1, 2);
        helper.setBlock(open, Blocks.STONE);
        helper.setBlock(claimed, Blocks.STONE);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(claimed)) {
            List<ItemStack> drops = ECProtection.harvestGuarded(player, level, helper.absolutePos(open), ItemStack.EMPTY, true);
            helper.assertTrue(drops != null && drops.size() == 1 && drops.get(0).is(Items.COBBLESTONE), "放行的格子应交出圆石，实际 " + drops);
            helper.assertTrue(helper.getBlockState(open).isAir(), "放行的格子应删掉");
            helper.assertTrue(ECProtection.harvestGuarded(player, level, helper.absolutePos(claimed), ItemStack.EMPTY, true) == null, "被拒的格子应返回 null");
            helper.assertTrue(helper.getBlockState(claimed).is(Blocks.STONE), "被拒的格子不动");
            helper.assertTrue(claims.breaks(open) == 1 && claims.breaks(claimed) == 1, "各发一次破坏事件");

            helper.assertTrue(ECProtection.check(player, level, helper.absolutePos(open), null) == ECProtection.Verdict.ALLOW, "放行的格子 check 应是 ALLOW");
            helper.assertTrue(ECProtection.check(player, level, helper.absolutePos(claimed), null) == ECProtection.Verdict.DENY, "被拒的格子 check 应是 DENY");
            helper.assertTrue(claims.probes(open) == 1 && claims.probes(claimed) == 1,
                    "批量写法不写缓存，之后的 check 应各重新探测一次，实际 " + claims.probes(open) + " / " + claims.probes(claimed));

            ECProtection.forget(player.getUUID());
            helper.setBlock(open, Blocks.STONE);
            helper.assertTrue(ECProtection.harvestGuarded(player, level, helper.absolutePos(open), ItemStack.EMPTY) != null, "对照：4 参版应能拆");
            helper.assertTrue(ECProtection.check(player, level, helper.absolutePos(open), null) == ECProtection.Verdict.ALLOW, "对照 check 应是 ALLOW");
            helper.assertTrue(claims.probes(open) == 1, "对照：4 参版写了缓存，check 不应再探测，实际 " + claims.probes(open));
        } finally {
            ECProtection.forget(player.getUUID());
        }
        helper.succeed();
    }

    /**
     * 整片选区都在别人的领地里（486 格石头全拒）：被拒累计 {@value BuilderJobs#DENIED_LIMIT} 格就停在「没有权限」，
     * 发的破坏事件（含探测）在这个数加开工前抽样数以内，一格没动、不扣 MRU、没有掉落
     */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 200)
    public static void builderStopsAfterDeniedLimit(GameTestHelper helper) {
        fill(helper, BIG_A, BIG_B, Blocks.STONE);
        ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper)
                .denyBox(BoundingBox.fromCorners(helper.absolutePos(BIG_A), helper.absolutePos(BIG_B)));
        ItemStack stack = wand(helper, BuilderMode.CLEAR, BIG_A, BIG_B, null, FULL);
        ServerPlayer player = holding(helper, stack);
        start(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    claims.close();
                    assertResult(helper, player, BuilderJobs.StopReason.DENIED, 0);
                    int breaks = 0;
                    for (BlockPos pos : BlockPos.betweenClosed(BIG_A, BIG_B)) {
                        breaks += claims.breaks(pos.immutable());
                    }
                    // 破坏事件里含开工前探测的那几格（最多 PRE_PROBE_SAMPLES 次），之后一律先探后放，所以总数在上限和上限 + 抽样数之间
                    helper.assertTrue(breaks >= BuilderJobs.DENIED_LIMIT && breaks <= BuilderJobs.DENIED_LIMIT + BuilderJobs.PRE_PROBE_SAMPLES,
                            "应问了 " + BuilderJobs.DENIED_LIMIT + " 格左右就停，实际 " + breaks);
                    helper.assertTrue(BuilderJobs.lastResult(player.getUUID()).denied() == BuilderJobs.DENIED_LIMIT, "被拒计数应到上限");
                    helper.assertTrue(countBox(helper, BIG_A, BIG_B, Blocks.STONE) == BIG, "一格都不应动");
                    helper.assertTrue(mru(stack) == FULL && count(player, Items.COBBLESTONE) == 0, "不扣 MRU、没有掉落，实际 MRU " + mru(stack));
                })
                .thenSucceed();
    }

    /**
     * 选区里只有 40 格在别人的领地里（不到上限）：照常做完，其余 446 格都拆了。
     * 拆的时候不出原版那份破坏效果，补发效果的格子每刻不超过 {@value BuilderJobs#MAX_EFFECTS_PER_TICK} 格（每格都出效果时每刻是几十格）
     */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 200)
    public static void builderDeniedBelowLimitFinishes(GameTestHelper helper) {
        fill(helper, BIG_A, BIG_B, Blocks.STONE);
        BlockPos denyA = new BlockPos(1, 1, 1);
        BlockPos denyB = new BlockPos(5, 2, 4);
        int deniedCells = 5 * 2 * 4;
        ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper)
                .denyBox(BoundingBox.fromCorners(helper.absolutePos(denyA), helper.absolutePos(denyB)));
        ServerPlayer player = holding(helper, wand(helper, BuilderMode.CLEAR, BIG_A, BIG_B, null, FULL));
        player.setGameMode(GameType.CREATIVE);
        start(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    claims.close();
                    assertResult(helper, player, BuilderJobs.StopReason.FINISHED, BIG - deniedCells);
                    BuilderJobs.Result result = BuilderJobs.lastResult(player.getUUID());
                    helper.assertTrue(result.denied() == deniedCells, "被拒应记 " + deniedCells + " 格，实际 " + result.denied());
                    helper.assertTrue(countBox(helper, denyA, denyB, Blocks.STONE) == deniedCells && countBox(helper, BIG_A, BIG_B, Blocks.AIR) == BIG - deniedCells,
                            "领地里的格子不动、其余拆光");
                    helper.assertTrue(result.peakEffects() >= 1 && result.peakEffects() <= BuilderJobs.MAX_EFFECTS_PER_TICK,
                            "每刻补发破坏效果的格子应在 1~" + BuilderJobs.MAX_EFFECTS_PER_TICK + "，实际最多 " + result.peakEffects());
                    helper.assertTrue(itemEntities(helper, 11, 8, 11) == 0, "创造模式不掉落");
                })
                .thenSucceed();
    }

    /**
     * 清除碰到分两半的方块（门、向日葵、朝上伸出的活塞）：从上往下先碰到上半 / 活塞头，改拆下半 / 底座。
     * 生存模式门、向日葵、活塞各进背包 1 个、各只收一次 250；创造模式什么都不给；地上都没有掉落物
     * （直接拆上半时，原版会连带用 destroyBlock(…, true) 拆掉下半，东西撒在地上，创造模式也一样）
     */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public static void builderClearTwoPartBlocks(GameTestHelper helper) {
        ServerPlayer survival = twoPartRow(helper, 1);
        ServerPlayer creative = twoPartRow(helper, 3);
        creative.setGameMode(GameType.CREATIVE);
        ItemStack survivalWand = survival.getMainHandItem();
        start(helper, survival);
        start(helper, creative);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(survival.getUUID()) && !BuilderJobs.isRunning(creative.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    assertResult(helper, survival, BuilderJobs.StopReason.FINISHED, 3);
                    assertResult(helper, creative, BuilderJobs.StopReason.FINISHED, 3);
                    for (int z : new int[] {1, 3}) {
                        helper.assertTrue(countBox(helper, new BlockPos(1, 1, z), new BlockPos(3, 2, z), Blocks.AIR) == 6, "第 " + z + " 排应拆光");
                    }
                    helper.assertTrue(count(survival, Items.OAK_DOOR) == 1 && count(survival, Items.SUNFLOWER) == 1 && count(survival, Items.PISTON) == 1,
                            "门、向日葵、活塞应各进背包 1 个");
                    helper.assertTrue(mru(survivalWand) == FULL - 3 * BREAK, "每件只收一次 250，实际 " + mru(survivalWand));
                    helper.assertTrue(count(creative, Items.OAK_DOOR) + count(creative, Items.SUNFLOWER) + count(creative, Items.PISTON) == 0, "创造模式不给东西");
                    helper.assertTrue(itemEntities(helper, 5, 4, 5) == 0, "地上不应有掉落物");
                })
                .thenSucceed();
    }

    /** 第 z 排摆一扇橡木门 (1, 1~2, z)、一株向日葵 (2, 1~2, z)、一个朝上伸出的活塞 (3, 1~2, z)，返回拿着框住这一排的清除魔杖的玩家 */
    private static ServerPlayer twoPartRow(GameTestHelper helper, int z) {
        helper.setBlock(new BlockPos(1, 0, z), Blocks.STONE);
        helper.setBlock(new BlockPos(1, 1, z), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(new BlockPos(1, 2, z), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        helper.setBlock(new BlockPos(2, 0, z), Blocks.DIRT);
        helper.setBlock(new BlockPos(2, 1, z), Blocks.SUNFLOWER.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(new BlockPos(2, 2, z), Blocks.SUNFLOWER.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
        // 底下垫红石块让它一直伸着（没有信号时底座一放下就会缩回去）
        helper.setBlock(new BlockPos(3, 0, z), Blocks.REDSTONE_BLOCK);
        helper.setBlock(new BlockPos(3, 1, z), Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.UP).setValue(PistonBaseBlock.EXTENDED, true));
        helper.setBlock(new BlockPos(3, 2, z), Blocks.PISTON_HEAD.defaultBlockState().setValue(PistonHeadBlock.FACING, Direction.UP).setValue(PistonHeadBlock.TYPE, PistonType.DEFAULT));
        return holding(helper, wand(helper, BuilderMode.CLEAR, new BlockPos(1, 1, z), new BlockPos(3, 2, z), null, FULL));
    }

    /**
     * 全部替换按这一格现在的流体算放置状态，放不下的连拆都不拆、不收钱、不发破坏事件：
     * 选定虞美人换石头（下面是石头，花立不住）、选定海带换干的石头（海带只能放进满水的格子）都不动；
     * 含水的石台阶换成海带照常换（拆完剩下的正是那格水），收 300、台阶进背包
     */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public static void builderReplaceSkipsUnplaceable(GameTestHelper helper) {
        BlockPos poppyCell = new BlockPos(1, 1, 1);
        BlockPos dryCell = new BlockPos(3, 1, 1);
        BlockPos wetCell = new BlockPos(2, 1, 3);
        for (BlockPos cell : new BlockPos[] {poppyCell, dryCell, wetCell}) {
            helper.setBlock(cell.below(), Blocks.STONE);
        }
        helper.setBlock(poppyCell, Blocks.STONE);
        helper.setBlock(dryCell, Blocks.STONE);
        // 含水台阶的四周和顶上围屏障：拆的那一下留下的水流不出去
        for (Direction direction : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP}) {
            helper.setBlock(wetCell.relative(direction), Blocks.BARRIER);
        }
        helper.setBlock(wetCell, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.WATERLOGGED, true));
        ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper);
        ItemStack poppyWand = wand(helper, BuilderMode.REPLACE_ALL, poppyCell, poppyCell, Blocks.POPPY, FULL);
        ServerPlayer poppy = holding(helper, poppyWand, new ItemStack(Items.POPPY, 8));
        ItemStack dryWand = wand(helper, BuilderMode.REPLACE_ALL, dryCell, dryCell, Blocks.KELP, FULL);
        ServerPlayer dry = holding(helper, dryWand, new ItemStack(Items.KELP, 8));
        ItemStack wetWand = wand(helper, BuilderMode.REPLACE_ALL, wetCell, wetCell, Blocks.KELP, FULL);
        ServerPlayer wet = holding(helper, wetWand, new ItemStack(Items.KELP, 8));
        ServerPlayer[] players = {poppy, dry, wet};
        for (ServerPlayer player : players) {
            start(helper, player);
        }
        helper.startSequence()
                .thenWaitUntil(() -> {
                    for (ServerPlayer player : players) {
                        helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工");
                    }
                })
                .thenExecute(() -> {
                    claims.close();
                    assertResult(helper, poppy, BuilderJobs.StopReason.FINISHED, 0);
                    assertResult(helper, dry, BuilderJobs.StopReason.FINISHED, 0);
                    helper.assertTrue(BuilderJobs.lastResult(poppy.getUUID()).skipped() == 1 && BuilderJobs.lastResult(dry.getUUID()).skipped() == 1,
                            "两格都应记成放不下、跳过");
                    helper.assertTrue(helper.getBlockState(poppyCell).is(Blocks.STONE) && helper.getBlockState(dryCell).is(Blocks.STONE), "放不下的格子连拆都不拆");
                    helper.assertTrue(claims.breaks(poppyCell) - claims.probes(poppyCell) == 0 && claims.breaks(dryCell) - claims.probes(dryCell) == 0,
                            "放不下的格子不发真破坏事件（开工前探测的不算）");
                    helper.assertTrue(mru(poppyWand) == FULL && mru(dryWand) == FULL, "放不下的格子不收钱");
                    helper.assertTrue(count(poppy, Items.POPPY) == 8 && count(dry, Items.KELP) == 8 && count(dry, Items.COBBLESTONE) == 0, "材料不动、没有掉落");

                    assertResult(helper, wet, BuilderJobs.StopReason.FINISHED, 1);
                    helper.assertTrue(helper.getBlockState(wetCell).is(Blocks.KELP), "含水台阶应换成海带，实际 " + helper.getBlockState(wetCell));
                    helper.assertTrue(mru(wetWand) == FULL - REPLACE && count(wet, Items.KELP) == 7 && count(wet, Items.STONE_SLAB) == 1,
                            "收 300、用掉 1 个海带、台阶进背包，实际 MRU " + mru(wetWand));
                })
                .thenSucceed();
    }

    /**
     * 全部替换遇到玩家站着的两格（(0, 1, 0)、(0, 2, 0) 的石头）：连拆都不拆、不发破坏事件；旁边一格石头照换，已经是木板的那格不动，只收 300
     */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public static void builderReplaceSkipsPlayerCells(GameTestHelper helper) {
        BlockPos feet = new BlockPos(0, 1, 0);
        BlockPos head = new BlockPos(0, 2, 0);
        BlockPos free = new BlockPos(1, 1, 0);
        BlockPos planks = new BlockPos(1, 2, 0);
        helper.setBlock(feet, Blocks.STONE);
        helper.setBlock(head, Blocks.STONE);
        helper.setBlock(free, Blocks.STONE);
        helper.setBlock(planks, Blocks.OAK_PLANKS);
        ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper);
        ItemStack stack = wand(helper, BuilderMode.REPLACE_ALL, feet, planks, Blocks.OAK_PLANKS, FULL);
        ServerPlayer player = holding(helper, stack, new ItemStack(Items.OAK_PLANKS, 64));
        start(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    claims.close();
                    assertResult(helper, player, BuilderJobs.StopReason.FINISHED, 1);
                    helper.assertTrue(BuilderJobs.lastResult(player.getUUID()).skipped() == 2, "玩家站着的两格应记成放不下");
                    helper.assertTrue(helper.getBlockState(feet).is(Blocks.STONE) && helper.getBlockState(head).is(Blocks.STONE), "玩家站着的两格不动");
                    helper.assertTrue(claims.breaks(feet) - claims.probes(feet) == 0 && claims.breaks(head) - claims.probes(head) == 0,
                            "玩家站着的两格不发真破坏事件（开工前探测的不算）");
                    helper.assertTrue(helper.getBlockState(free).is(Blocks.OAK_PLANKS) && claims.breaks(free) - claims.probes(free) == 1, "旁边那格照换");
                    helper.assertTrue(mru(stack) == FULL - REPLACE && count(player, Items.COBBLESTONE) == 1 && count(player, Items.OAK_PLANKS) == 63,
                            "只收 300、圆石 1、木板用掉 1，实际 MRU " + mru(stack));
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------
    // 事件的监听器在施工中途换手、打死玩家、拒绝放置
    // ------------------------------------------------------------------

    /**
     * 魔杖拿在副手开工，第 1 格的破坏事件里有监听器把副手换成一模一样的另一根：每做完一格都复查手里的魔杖，所以这一刻里就停下，只做成 1 格
     * （只靠每刻开头的复查的话，这一刻会把 18 格全做完）
     */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public static void builderStopsOnHandSwapWithinTick(GameTestHelper helper) {
        fill(helper, A, B, Blocks.STONE);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        player.setItemInHand(InteractionHand.OFF_HAND, wand(helper, BuilderMode.CLEAR, A, B, null, FULL));
        BuilderJobs.StartResult started = BuilderJobs.start(player, InteractionHand.OFF_HAND);
        helper.assertTrue(started == BuilderJobs.StartResult.STARTED, "副手拿着应能开工，实际 " + started);
        UUID id = player.getUUID();
        boolean[] swapped = {false};
        Consumer<BlockEvent.BreakEvent> swap = event -> {
            if (!swapped[0] && event.getPlayer() != null && id.equals(event.getPlayer().getUUID())) {
                swapped[0] = true;
                player.setItemInHand(InteractionHand.OFF_HAND, player.getOffhandItem().copy());
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, BlockEvent.BreakEvent.class, swap);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(id), "还在施工"))
                .thenExecute(() -> {
                    MinecraftForge.EVENT_BUS.unregister(swap);
                    helper.assertTrue(swapped[0], "监听器应换过副手");
                    assertResult(helper, player, BuilderJobs.StopReason.INTERRUPTED, 1);
                    helper.assertTrue(countBox(helper, A, B, Blocks.STONE) == 17, "只应拆 1 格");
                })
                .thenSucceed();
    }

    /**
     * 第 1 格的破坏事件里有监听器把玩家打死（没开 keepInventory）：这一格照样拆了，圆石掉在玩家脚下，不塞进死人的背包（重生后就没了）；
     * 发事件时拿开的主手魔杖也掉在原地，不放回死人的背包
     */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public static void builderDeathDropsAtFeet(GameTestHelper helper) {
        BlockPos cell = new BlockPos(1, 1, 1);
        helper.setBlock(cell, Blocks.STONE);
        ServerPlayer player = centered(helper, holding(helper, wand(helper, BuilderMode.CLEAR, cell, cell, null, FULL)));
        UUID id = player.getUUID();
        Consumer<BlockEvent.BreakEvent> killer = event -> {
            if (event.getPlayer() != null && id.equals(event.getPlayer().getUUID()) && player.isAlive()) {
                player.kill();
            }
        };
        start(helper, player);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, BlockEvent.BreakEvent.class, killer);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(id), "还在施工"))
                .thenExecute(() -> {
                    MinecraftForge.EVENT_BUS.unregister(killer);
                    helper.assertTrue(!player.isAlive(), "玩家应已经死了");
                    assertResult(helper, player, BuilderJobs.StopReason.INTERRUPTED, 1);
                    helper.assertTrue(helper.getBlockState(cell).isAir(), "这一格照样拆了");
                    helper.assertTrue(droppedNear(helper, player, Items.COBBLESTONE) == 1, "圆石应掉在玩家脚下，实际 " + droppedNear(helper, player, Items.COBBLESTONE));
                    helper.assertTrue(count(player, Items.COBBLESTONE) == 0, "圆石不应塞进死人的背包");
                    helper.assertTrue(droppedNear(helper, player, ECBuilder.item(BuilderMode.CLEAR)) == 1, "魔杖应掉在原地");
                    helper.assertTrue(count(player, ECBuilder.item(BuilderMode.CLEAR)) == 0, "魔杖不应放回死人的背包");
                })
                .thenSucceed();
    }

    /**
     * 放置事件的监听器把玩家打死、又取消了这次放置：取出来的那 1 块木板掉在原地（不塞进死人的背包），
     * 加上死亡掉落撒出来的 4 块，地上正好 5 块；格子还是空气，什么都没做成
     */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public static void builderRefundDropsWhenPlacerDies(GameTestHelper helper) {
        BlockPos cell = new BlockPos(1, 1, 1);
        ServerPlayer player = centered(helper, holding(helper, wand(helper, BuilderMode.FILL, cell, cell, Blocks.OAK_PLANKS, FULL),
                new ItemStack(Items.OAK_PLANKS, 5)));
        UUID id = player.getUUID();
        Consumer<BlockEvent.EntityPlaceEvent> killer = event -> {
            if (event.getEntity() != null && id.equals(event.getEntity().getUUID())) {
                if (player.isAlive()) {
                    player.kill();
                }
                event.setCanceled(true);
            }
        };
        start(helper, player);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, BlockEvent.EntityPlaceEvent.class, killer);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(id), "还在施工"))
                .thenExecute(() -> {
                    MinecraftForge.EVENT_BUS.unregister(killer);
                    helper.assertTrue(!player.isAlive(), "玩家应已经死了");
                    assertResult(helper, player, BuilderJobs.StopReason.INTERRUPTED, 0);
                    helper.assertTrue(helper.getBlockState(cell).isAir(), "被拒的格子应还是空气");
                    helper.assertTrue(droppedNear(helper, player, Items.OAK_PLANKS) == 5, "地上应正好 5 块木板，实际 " + droppedNear(helper, player, Items.OAK_PLANKS));
                    helper.assertTrue(count(player, Items.OAK_PLANKS) == 0, "木板不应塞进死人的背包");
                })
                .thenSucceed();
    }

    /** 把测试玩家挪到测试区中间 (2.5, 1, 2.5)：死亡掉落带随机速度，站在角上会撒到别的测试的场地里 */
    private static ServerPlayer centered(GameTestHelper helper, ServerPlayer player) {
        Vec3 at = helper.absoluteVec(new Vec3(2.5D, 1D, 2.5D));
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        return player;
    }

    /** 玩家周围 2.5 格内某种物品的掉落物合计几个 */
    private static int droppedNear(GameTestHelper helper, ServerPlayer player, Item item) {
        int n = 0;
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(2.5D), Entity::isAlive)) {
            if (entity.getItem().is(item)) {
                n += entity.getItem().getCount();
            }
        }
        return n;
    }

    // ------------------------------------------------------------------
    // 配方
    // ------------------------------------------------------------------

    /** 工作台里单放一根换到下一个模式，转一圈 5 次：MRU、两个点、维度、选定方块都在 */
    @GameTest(template = EMPTY)
    public static void builderCycleKeepsData(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack stack = wand(helper, BuilderMode.FILL, A, B, Blocks.STONE, 1234);
        BlockPos first = BuilderSelection.first(stack);
        BlockPos second = BuilderSelection.second(stack);
        BuilderMode mode = BuilderMode.FILL;
        for (int i = 0; i < 5; i++) {
            CrafterGrid grid = new CrafterGrid(new ItemStack[] {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, stack});
            Optional<CraftingRecipe> recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level);
            helper.assertTrue(recipe.isPresent(), mode + " 单放应有换模式的配方");
            ResourceLocation expectedId = ResourceLocation.fromNamespaceAndPath(EssentialCraft5.MODID, "builder/cycle_" + mode.id());
            helper.assertTrue(recipe.get().getId().equals(expectedId), "应是 " + expectedId + "，实际 " + recipe.get().getId());
            ItemStack out = recipe.get().assemble(grid, level.registryAccess());
            BuilderMode next = mode.next();
            helper.assertTrue(BuilderMode.of(out) == next && out.getCount() == 1, mode + " 应换成 " + next + "，实际 " + out);
            helper.assertTrue(mru(out) == 1234, "MRU 应留着，实际 " + mru(out));
            helper.assertTrue(first.equals(BuilderSelection.first(out)) && second.equals(BuilderSelection.second(out)), "两个点应留着");
            helper.assertTrue(BuilderSelection.inDimension(out, level.dimension()), "维度应留着");
            helper.assertTrue(MagicalBuilderItem.storedBlock(out) == Blocks.STONE, "选定方块应留着");
            stack = out;
            mode = next;
        }
        helper.assertTrue(mode == BuilderMode.FILL, "转一圈应回到填充");
        helper.succeed();
    }

    /** 主配方：九格都有原料（原作执行器那两格换成世界交互器，不许留空标签），产物是填充模式；按原料摆一遍能做出来 */
    @GameTest(template = EMPTY)
    public static void builderMainRecipe(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(EssentialCraft5.MODID, "builder/magical_builder");
        Optional<? extends Recipe<?>> found = level.getRecipeManager().byKey(id);
        helper.assertTrue(found.isPresent() && found.get() instanceof ShapedRecipe, "主配方应已加载且是有序配方");
        ShapedRecipe recipe = (ShapedRecipe) found.get();
        List<Ingredient> ingredients = recipe.getIngredients();
        helper.assertTrue(ingredients.size() == 9, "应是 3×3，实际 " + ingredients.size());
        ItemStack[] cells = new ItemStack[9];
        int interactors = 0;
        for (int i = 0; i < 9; i++) {
            Ingredient ingredient = ingredients.get(i);
            // Forge 给空标签的原料塞一个屏障当占位，也算空
            helper.assertTrue(!ingredient.isEmpty() && ingredient.getItems().length > 0 && !ingredient.getItems()[0].is(Items.BARRIER),
                    "第 " + i + " 格原料不应是空的");
            if (ingredient.test(new ItemStack(ECMaterials.WORLD_INTERACTOR.get()))) {
                interactors++;
            }
            cells[i] = ingredient.getItems()[0].copy();
        }
        helper.assertTrue(interactors == 2, "应有 2 个世界交互器，实际 " + interactors);
        ItemStack result = recipe.getResultItem(level.registryAccess());
        helper.assertTrue(BuilderMode.of(result) == BuilderMode.FILL, "产物应是填充模式，实际 " + result);
        CrafterGrid grid = new CrafterGrid(cells);
        helper.assertTrue(recipe.matches(grid, level), "按原料摆好应能匹配");
        helper.succeed();
    }
}
