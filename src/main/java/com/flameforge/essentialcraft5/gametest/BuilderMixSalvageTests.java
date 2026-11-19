package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.item.BuilderActions;
import com.flameforge.essentialcraft5.item.BuilderArea;
import com.flameforge.essentialcraft5.item.BuilderJobs;
import com.flameforge.essentialcraft5.item.BuilderMaterials;
import com.flameforge.essentialcraft5.item.BuilderMix;
import com.flameforge.essentialcraft5.item.BuilderMode;
import com.flameforge.essentialcraft5.item.BuilderModules;
import com.flameforge.essentialcraft5.item.BuilderSalvage;
import com.flameforge.essentialcraft5.item.BuilderSelection;
import com.flameforge.essentialcraft5.item.BuilderShape;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.network.C2SBuilderActionPacket;
import com.flameforge.essentialcraft5.registry.ECBuilder;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 创世神之杖混铺、回收模块（tools/plan/builder_upgrade.md §2.2、§7，{@link BuilderMix}、{@link BuilderSalvage}）的 GameTest：
 * 混铺的预览（{@link BuilderMix#blockAt}）、确认（{@link BuilderJobs#plan}）、实际放下的三处一致，权重比例，某种用完换别的、全用完暂停，
 * 撤销按实际种类退料，没装模块 / 清除模式不生效，设混铺表的包乱填被拒；回收塞随身容器、容器满进背包、背包满掉脚下且总数守恒，
 * 丢弃清单，没装回收照旧，设丢弃清单的包乱填被拒。
 * 测试容器（{@link BuilderTestContainers}）在 {@code @BeforeBatch} 打开，所以容器物品都在测试方法里 new。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class BuilderMixSalvageTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    static final String BATCH = "ec5_builder_mix";
    /** 材料用完暂停 2 秒 */
    static final String PAUSE_BATCH = "ec5_builder_mix_pause";

    private static final int FULL = MRUStaffItem.MAX_MRU;

    private BuilderMixSalvageTests() {
    }

    private static void before() {
        BuilderJobs.clearAll();
        ECBuilder.Config.Test.reset();
        ECBuilder.Config.setTestOverrides(null, null, null);
        ECBuilder.Config.Test.materialPauseSeconds = 0;
        ECBuilder.Config.Test.maxJobsTotal = 256;
        BuilderTestContainers.enabled = true;
    }

    private static void after() {
        BuilderTestContainers.enabled = false;
        ECBuilder.Config.Test.reset();
        BuilderJobs.clearAll();
    }

    @BeforeBatch(batch = BATCH)
    public static void builderMixBefore(ServerLevel level) {
        before();
    }

    @AfterBatch(batch = BATCH)
    public static void builderMixAfter(ServerLevel level) {
        after();
    }

    @BeforeBatch(batch = PAUSE_BATCH)
    public static void builderMixPauseBefore(ServerLevel level) {
        before();
        ECBuilder.Config.Test.materialPauseSeconds = 2;
    }

    @AfterBatch(batch = PAUSE_BATCH)
    public static void builderMixPauseAfter(ServerLevel level) {
        after();
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /** 铺面形状、装了 kinds 这几块模块的魔杖 */
    private static ItemStack wand(BuilderMode mode, int width, int length, BuilderModules.Kind... kinds) {
        ItemStack stack = new ItemStack(ECBuilder.item(mode));
        stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(FULL));
        BuilderSelection.setShape(stack, BuilderShape.PLANE);
        BuilderSelection.setSize(stack, BuilderShape.PLANE, width, length);
        if (kinds.length > 0) {
            ListTag list = new ListTag();
            for (BuilderModules.Kind kind : kinds) {
                list.add(StringTag.valueOf(kind.registryName().toString()));
            }
            stack.getOrCreateTag().put(BuilderModules.TAG, list);
        }
        return stack;
    }

    private static BuilderMix.Entry entry(Block block, int weight) {
        return new BuilderMix.Entry(block, weight);
    }

    /** 主手拿着 wand、飞在相对 (x + 0.5, y, z + 0.5) 的真玩家，面朝南：铺面在脚下那一层（相对 y − 1） */
    private static ServerPlayer flyer(GameTestHelper helper, ItemStack wand, int x, int y, int z) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-builder-mix");
        Vec3 at = helper.absoluteVec(new Vec3(x + 0.5D, y, z + 0.5D));
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        player.setOnGround(false);
        player.setItemInHand(InteractionHand.MAIN_HAND, wand);
        return player;
    }

    private static BuilderArea area(ServerPlayer player, ItemStack wand) {
        return BuilderJobs.areaOf(player, wand, BuilderShape.PLANE, null);
    }

    /** 背包里这种没 NBT 的物品有几个（含副手） */
    private static int count(ServerPlayer player, Item item) {
        int n = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (BuilderMaterials.isPlainMaterial(stack, item)) {
                n += stack.getCount();
            }
        }
        ItemStack offhand = player.getInventory().offhand.get(0);
        return BuilderMaterials.isPlainMaterial(offhand, item) ? n + offhand.getCount() : n;
    }

    private static int countIn(IItemHandler handler, Item item) {
        int n = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.is(item)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    /** 范围里每格是哪种方块，按种数 */
    private static int countArea(ServerLevel level, BuilderArea area, Block block) {
        int n = 0;
        for (int i = 0; i < area.size(); i++) {
            if (level.getBlockState(area.cellAt(i, true)).is(block)) {
                n++;
            }
        }
        return n;
    }

    /** 范围里每格按预览函数算，各种几格 */
    private static int[] expected(BuilderMix.Table table, BuilderArea area) {
        int[] out = new int[table.size()];
        long seed = BuilderMix.seedFor(table, area);
        for (int i = 0; i < area.size(); i++) {
            out[BuilderMix.kindAt(table, seed, area.box(), area.cellAt(i, true))]++;
        }
        return out;
    }

    private static void startOk(GameTestHelper helper, ServerPlayer player) {
        BuilderJobs.StartResult result = BuilderJobs.start(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(result == BuilderJobs.StartResult.STARTED, "应能开工，实际 " + result);
    }

    private static BuilderJobs.Result result(GameTestHelper helper, ServerPlayer player) {
        BuilderJobs.Result result = BuilderJobs.lastResult(player.getUUID());
        helper.assertTrue(result != null, "应有施工结果");
        return Objects.requireNonNull(result);
    }

    private static ItemStack bag() {
        return new ItemStack(Items.BUNDLE);
    }

    private static int dropped(GameTestHelper helper, ServerPlayer player, Item item) {
        AABB around = player.getBoundingBox().inflate(4D);
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, around, e -> e.getItem().is(item)).stream()
                .mapToInt(e -> e.getItem().getCount()).sum();
    }

    private static void clearDrops(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(4D)).forEach(ItemEntity::discard);
    }

    private static ResourceLocation id(Block block) {
        return Objects.requireNonNull(ForgeRegistries.BLOCKS.getKey(block));
    }

    private static ResourceLocation id(Item item) {
        return Objects.requireNonNull(ForgeRegistries.ITEMS.getKey(item));
    }

    // ------------------------------------------------------------------
    // 混铺
    // ------------------------------------------------------------------

    /**
     * 6 × 6 铺面、三种按 1 : 2 : 1：预览函数逐格算的、开工前确认扫出的各种格数、施工完世界里每格的方块三处一致；用掉的料各种等于放下的格数
     */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 200)
    public static void builderMixPreviewMatchesActual(GameTestHelper helper) {
        ItemStack stack = wand(BuilderMode.FILL, 6, 6, BuilderModules.Kind.MIX);
        BuilderMix.write(stack, List.of(entry(Blocks.COBBLESTONE, 1), entry(Blocks.OAK_PLANKS, 2), entry(Blocks.DIRT, 1)), 12345);
        ServerPlayer player = flyer(helper, stack, 5, 4, 2);
        player.getInventory().items.set(1, new ItemStack(Items.COBBLESTONE, 64));
        player.getInventory().items.set(2, new ItemStack(Items.OAK_PLANKS, 64));
        player.getInventory().items.set(3, new ItemStack(Items.DIRT, 64));
        BuilderMix.Table table = BuilderMix.forWand(stack, BuilderMode.FILL);
        helper.assertTrue(table != null && table.size() == 3, "混铺表应生效");
        BuilderArea area = area(player, stack);
        int[] want = expected(Objects.requireNonNull(table), area);
        helper.assertTrue(want[0] > 0 && want[1] > 0 && want[2] > 0, "36 格里三种都该出现：" + java.util.Arrays.toString(want));
        BuilderJobs.Plan plan = BuilderJobs.plan(player, stack, BuilderMode.FILL, area,
                BuilderJobs.chooseBlock(player, InteractionHand.MAIN_HAND, stack, BuilderMode.FILL), table);
        helper.assertTrue(plan.place() == 36 && java.util.Arrays.equals(plan.need(), want),
                "确认扫出的各种格数应同预览：" + java.util.Arrays.toString(plan.need()) + " / " + java.util.Arrays.toString(want));
        helper.assertTrue(BuilderJobs.chooseBlock(player, InteractionHand.MAIN_HAND, stack, BuilderMode.FILL) == Blocks.COBBLESTONE,
                "混铺时 chooseBlock 给表里第一种");
        long seed = BuilderMix.seedFor(table, area);
        startOk(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    helper.assertTrue(result(helper, player).done() == 36, "放下 36 格，实际 " + result(helper, player).done());
                    ServerLevel level = helper.getLevel();
                    for (int i = 0; i < area.size(); i++) {
                        BlockPos pos = area.cellAt(i, true);
                        Block expect = BuilderMix.blockAt(table, seed, area.box(), pos);
                        helper.assertTrue(level.getBlockState(pos).is(expect), "第 " + i + " 格应是 " + expect + "，实际 " + level.getBlockState(pos));
                    }
                    helper.assertTrue(count(player, Items.COBBLESTONE) == 64 - want[0] && count(player, Items.OAK_PLANKS) == 64 - want[1]
                            && count(player, Items.DIRT) == 64 - want[2], "各种用掉的料等于放下的格数");
                })
                .thenSucceed();
    }

    /**
     * 选哪种的纯函数：权重 1 : 2 : 3 : 4 在 5 万格里各占 10% / 20% / 30% / 40%（容差 2 个百分点）；挨着的小种子（0～63）在同一格上也打得散；
     * 外框里的下标和施工先后无关；同样的输入结果一样
     */
    @GameTest(template = EMPTY)
    public static void builderMixWeights(GameTestHelper helper) {
        BuilderMix.Table table = new BuilderMix.Table(List.of(entry(Blocks.COBBLESTONE, 1), entry(Blocks.OAK_PLANKS, 2),
                entry(Blocks.DIRT, 3), entry(Blocks.STONE_BRICKS, 4)), 7);
        int samples = 50000;
        for (long seed : new long[] {0L, 1L, 2L, -5L, 0x123456789L}) {
            int[] hits = new int[4];
            for (int i = 0; i < samples; i++) {
                hits[BuilderMix.pick(table, seed, i)]++;
            }
            for (int k = 0; k < 4; k++) {
                double share = hits[k] / (double) samples;
                double want = (k + 1) / 10D;
                helper.assertTrue(Math.abs(share - want) < 0.02D, "种子 " + seed + " 第 " + k + " 种占 " + share + "，应约 " + want);
            }
        }
        Set<Integer> firstCell = new HashSet<>();
        int[] spread = new int[4];
        for (long seed = 0; seed < 64; seed++) {
            int k = BuilderMix.pick(table, seed, 0);
            firstCell.add(k);
            spread[k]++;
        }
        helper.assertTrue(firstCell.size() == 4 && spread[3] < 48, "挨着的小种子在同一格上也要打散：" + java.util.Arrays.toString(spread));
        BoundingBox box = new BoundingBox(10, 5, 20, 13, 6, 24);
        helper.assertTrue(BuilderMix.cellIndex(box, new BlockPos(10, 5, 20)) == 0 && BuilderMix.cellIndex(box, new BlockPos(11, 5, 20)) == 1
                && BuilderMix.cellIndex(box, new BlockPos(10, 5, 21)) == 4 && BuilderMix.cellIndex(box, new BlockPos(10, 6, 20)) == 20,
                "外框下标：x 最快、再 z、再 y");
        helper.assertTrue(BuilderMix.pick(table, 99L, 17) == BuilderMix.pick(table, 99L, 17), "同样的输入结果一样");
        BuilderMix.Table single = new BuilderMix.Table(List.of(entry(Blocks.DIRT, 5)), 3);
        helper.assertTrue(BuilderMix.pick(single, 42L, 3) == 0, "只有一种时恒为 0");
        helper.succeed();
    }

    /**
     * 两种 1 : 1，圆石只有 3 块、木板够：开工前确认要求再点一次（有一种不够）；施工中圆石用完由木板补上，全部铺完；
     * 预览里该放木板的格子都是木板，圆石只用掉 3 块
     */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 200)
    public static void builderMixRunsOutSwitches(GameTestHelper helper) {
        ItemStack stack = wand(BuilderMode.FILL, 4, 4, BuilderModules.Kind.MIX);
        ServerPlayer player = flyer(helper, stack, 5, 4, 3);
        BuilderArea area = area(player, stack);
        // 挑一个让圆石格数多于 3 的种子（这样一定会用完）
        int seed = 0;
        BuilderMix.Table table = null;
        for (int s = 0; s < 1000; s++) {
            BuilderMix.Table t = new BuilderMix.Table(List.of(entry(Blocks.COBBLESTONE, 1), entry(Blocks.OAK_PLANKS, 1)), s);
            if (expected(t, area)[0] >= 6) {
                seed = s;
                table = t;
                break;
            }
        }
        helper.assertTrue(table != null, "应找得到圆石多于 6 格的种子");
        BuilderMix.write(stack, Objects.requireNonNull(table).entries(), seed);
        int[] want = expected(table, area);
        player.getInventory().items.set(1, new ItemStack(Items.COBBLESTONE, 3));
        player.getInventory().items.set(2, new ItemStack(Items.OAK_PLANKS, 30));
        BuilderJobs.StartResult first = BuilderJobs.request(player, InteractionHand.MAIN_HAND, null, true);
        helper.assertTrue(first == BuilderJobs.StartResult.NEEDS_CONFIRM, "有一种不够要确认，实际 " + first);
        BuilderJobs.StartResult second = BuilderJobs.request(player, InteractionHand.MAIN_HAND, null, true);
        helper.assertTrue(second == BuilderJobs.StartResult.STARTED, "再点一次开工，实际 " + second);
        BuilderMix.Table used = Objects.requireNonNull(BuilderMix.forWand(stack, BuilderMode.FILL));
        long jobSeed = BuilderMix.seedFor(used, area);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    BuilderJobs.Result result = result(helper, player);
                    helper.assertTrue(result.reason() == BuilderJobs.StopReason.FINISHED && result.done() == 16,
                            "圆石用完换木板、全部铺完，实际 " + result.reason() + " / " + result.done());
                    ServerLevel level = helper.getLevel();
                    helper.assertTrue(countArea(level, area, Blocks.COBBLESTONE) == 3 && countArea(level, area, Blocks.OAK_PLANKS) == 13,
                            "圆石 3 格、木板 13 格");
                    for (int i = 0; i < area.size(); i++) {
                        BlockPos pos = area.cellAt(i, true);
                        if (BuilderMix.blockAt(used, jobSeed, area.box(), pos) == Blocks.OAK_PLANKS) {
                            helper.assertTrue(level.getBlockState(pos).is(Blocks.OAK_PLANKS), "预览是木板的格子应是木板");
                        }
                    }
                    helper.assertTrue(count(player, Items.COBBLESTONE) == 0 && count(player, Items.OAK_PLANKS) == 30 - 13,
                            "圆石用光、木板用 13 块（预览里木板 " + want[1] + " 格）");
                })
                .thenSucceed();
    }

    /** 两种料都只有 2 块：铺 4 格后暂停（不是结束）；只补木板就接着铺完，剩下的格全是木板 */
    @GameTest(template = EMPTY_LARGE, batch = PAUSE_BATCH, timeoutTicks = 300)
    public static void builderMixPausesWhenAllOut(GameTestHelper helper) {
        ItemStack stack = wand(BuilderMode.FILL, 4, 4, BuilderModules.Kind.MIX);
        BuilderMix.write(stack, List.of(entry(Blocks.COBBLESTONE, 1), entry(Blocks.OAK_PLANKS, 1)), 4);
        ServerPlayer player = flyer(helper, stack, 5, 4, 3);
        BuilderArea area = area(player, stack);
        player.getInventory().items.set(1, new ItemStack(Items.COBBLESTONE, 2));
        player.getInventory().items.set(2, new ItemStack(Items.OAK_PLANKS, 2));
        startOk(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    BuilderJobs.Snapshot snapshot = BuilderJobs.snapshot(player.getUUID(), helper.getLevel().getServer().getTickCount());
                    helper.assertTrue(snapshot != null && snapshot.paused() && !snapshot.pausedForMru(), "应暂停等补料");
                })
                .thenExecute(() -> {
                    helper.assertTrue(BuilderJobs.progress(player.getUUID()) == 4, "暂停前铺了 4 格，实际 " + BuilderJobs.progress(player.getUUID()));
                    player.getInventory().items.set(3, new ItemStack(Items.OAK_PLANKS, 12));
                })
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    BuilderJobs.Result result = result(helper, player);
                    helper.assertTrue(result.reason() == BuilderJobs.StopReason.FINISHED && result.done() == 16,
                            "补了一种就接着铺完，实际 " + result.reason() + " / " + result.done());
                    ServerLevel level = helper.getLevel();
                    helper.assertTrue(countArea(level, area, Blocks.COBBLESTONE) == 2 && countArea(level, area, Blocks.OAK_PLANKS) == 14,
                            "圆石 2 格、木板 14 格");
                })
                .thenSucceed();
    }

    /** 混铺三种铺完再撤销：每种退回的数等于那种实际放下的格数，背包回到开工前 */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 200)
    public static void builderMixUndoRefundsPerKind(GameTestHelper helper) {
        ItemStack stack = wand(BuilderMode.FILL, 4, 4, BuilderModules.Kind.MIX);
        BuilderMix.write(stack, List.of(entry(Blocks.COBBLESTONE, 2), entry(Blocks.OAK_PLANKS, 1), entry(Blocks.DIRT, 1)), 77);
        ServerPlayer player = flyer(helper, stack, 5, 4, 3);
        BuilderArea area = area(player, stack);
        player.getInventory().items.set(1, new ItemStack(Items.COBBLESTONE, 20));
        player.getInventory().items.set(2, new ItemStack(Items.OAK_PLANKS, 20));
        player.getInventory().items.set(3, new ItemStack(Items.DIRT, 20));
        startOk(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    ServerLevel level = helper.getLevel();
                    int cobble = countArea(level, area, Blocks.COBBLESTONE);
                    int planks = countArea(level, area, Blocks.OAK_PLANKS);
                    int dirt = countArea(level, area, Blocks.DIRT);
                    helper.assertTrue(cobble + planks + dirt == 16 && count(player, Items.COBBLESTONE) == 20 - cobble
                            && count(player, Items.OAK_PLANKS) == 20 - planks && count(player, Items.DIRT) == 20 - dirt, "放下 16 格、各扣各的");
                    BuilderJobs.StartResult undo = BuilderJobs.undo(player);
                    helper.assertTrue(undo == BuilderJobs.StartResult.STARTED, "撤销应开工，实际 " + undo);
                })
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在撤销"))
                .thenExecute(() -> {
                    ServerLevel level = helper.getLevel();
                    helper.assertTrue(countArea(level, area, Blocks.AIR) == 16, "16 格都撤掉");
                    helper.assertTrue(count(player, Items.COBBLESTONE) == 20 && count(player, Items.OAK_PLANKS) == 20 && count(player, Items.DIRT) == 20,
                            "每种按实际放的退回：圆石 " + count(player, Items.COBBLESTONE) + "、木板 " + count(player, Items.OAK_PLANKS)
                                    + "、泥土 " + count(player, Items.DIRT));
                })
                .thenSucceed();
    }

    /** 魔杖上有 builder_mix 但没装混铺：照旧只用选定的那一种；装了混铺但是清除模式：表不生效 */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 200)
    public static void builderMixNeedsModule(GameTestHelper helper) {
        ItemStack stack = wand(BuilderMode.FILL, 3, 3);
        BuilderSelection.setStoredBlock(stack, id(Blocks.COBBLESTONE));
        BuilderMix.write(stack, List.of(entry(Blocks.OAK_PLANKS, 1), entry(Blocks.DIRT, 1)), 9);
        ServerPlayer player = flyer(helper, stack, 5, 4, 3);
        BuilderArea area = area(player, stack);
        player.getInventory().items.set(1, new ItemStack(Items.COBBLESTONE, 20));
        player.getInventory().items.set(2, new ItemStack(Items.OAK_PLANKS, 20));
        player.getInventory().items.set(3, new ItemStack(Items.DIRT, 20));
        helper.assertTrue(BuilderMix.forWand(stack, BuilderMode.FILL) == null, "没装混铺不生效");
        helper.assertTrue(BuilderJobs.chooseBlock(player, InteractionHand.MAIN_HAND, stack, BuilderMode.FILL) == Blocks.COBBLESTONE, "用选定的");
        ItemStack clearing = wand(BuilderMode.CLEAR, 3, 3, BuilderModules.Kind.MIX);
        BuilderMix.write(clearing, List.of(entry(Blocks.OAK_PLANKS, 1)), 9);
        helper.assertTrue(BuilderMix.forWand(clearing, BuilderMode.CLEAR) == null && BuilderMix.read(clearing) != null, "清除模式不混铺");
        startOk(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    helper.assertTrue(countArea(helper.getLevel(), area, Blocks.COBBLESTONE) == 9, "9 格都是圆石");
                    helper.assertTrue(count(player, Items.OAK_PLANKS) == 20 && count(player, Items.DIRT) == 20, "表里的料一块没动");
                })
                .thenSucceed();
    }

    /**
     * 设混铺表：好的收下（换新种子）、空表清空；没装模块、施工中、注册名不认得 / 空气 / 带方块实体 / 分两半、权重 0 或 10、重复、超过种数、
     * 方块和权重数目不一致都整张不收、原来的不动；包编解码来回一致，项数超上限的包解不开
     */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 200)
    public static void builderMixSettingRejects(GameTestHelper helper) {
        ItemStack stack = wand(BuilderMode.FILL, 3, 3, BuilderModules.Kind.MIX);
        ServerPlayer player = flyer(helper, stack, 5, 4, 3);
        InteractionHand hand = InteractionHand.MAIN_HAND;
        ResourceLocation cobble = id(Blocks.COBBLESTONE);
        ResourceLocation planks = id(Blocks.OAK_PLANKS);
        helper.assertTrue(BuilderActions.setMix(player, hand, List.of(cobble, planks), List.of(3, 1)) == BuilderActions.SettingResult.OK, "好的表收下");
        BuilderMix.Table set = BuilderMix.read(stack);
        helper.assertTrue(set != null && set.size() == 2 && set.entries().get(0).weight() == 3, "写进 NBT");
        CompoundTag before = Objects.requireNonNull(stack.getTag()).getCompound(BuilderMix.TAG).copy();

        List<List<ResourceLocation>> badIds = List.of(
                List.of(ResourceLocation.fromNamespaceAndPath("essentialcraft5", "no_such_block")),
                List.of(id(Blocks.AIR)),
                List.of(id(Blocks.CHEST)),
                List.of(id(Blocks.OAK_DOOR)),
                List.of(id(Blocks.WALL_TORCH)),
                List.of(cobble, cobble),
                List.of(cobble, planks, id(Blocks.DIRT), id(Blocks.STONE_BRICKS), id(Blocks.SANDSTONE)));
        List<List<Integer>> badWeights = List.of(List.of(1), List.of(1), List.of(1), List.of(1), List.of(1), List.of(1, 1), List.of(1, 1, 1, 1, 1));
        for (int i = 0; i < badIds.size(); i++) {
            BuilderActions.SettingResult r = BuilderActions.setMix(player, hand, badIds.get(i), badWeights.get(i));
            helper.assertTrue(r == BuilderActions.SettingResult.INVALID, "第 " + i + " 种乱填应拒，实际 " + r);
        }
        helper.assertTrue(BuilderActions.setMix(player, hand, List.of(cobble), List.of(0)) == BuilderActions.SettingResult.INVALID, "权重 0 拒");
        helper.assertTrue(BuilderActions.setMix(player, hand, List.of(cobble), List.of(10)) == BuilderActions.SettingResult.INVALID, "权重 10 拒");
        helper.assertTrue(BuilderActions.setMix(player, hand, List.of(cobble, planks), List.of(1)) == BuilderActions.SettingResult.INVALID, "数目不一致拒");
        Integer savedMax = ECBuilder.Config.Test.mixMaxKinds;
        try {
            ECBuilder.Config.Test.mixMaxKinds = 1;
            helper.assertTrue(BuilderActions.setMix(player, hand, List.of(cobble, planks), List.of(1, 1)) == BuilderActions.SettingResult.INVALID,
                    "超过配置的种数拒");
            helper.assertTrue(Objects.requireNonNull(BuilderMix.read(stack)).size() == 1, "配置调小后多出来的不生效");
        } finally {
            ECBuilder.Config.Test.mixMaxKinds = savedMax;
        }
        helper.assertTrue(stack.getTag().getCompound(BuilderMix.TAG).equals(before), "被拒的都没改 NBT");

        ItemStack plain = wand(BuilderMode.FILL, 3, 3);
        player.setItemInHand(InteractionHand.OFF_HAND, plain);
        helper.assertTrue(BuilderActions.setMix(player, InteractionHand.OFF_HAND, List.of(cobble), List.of(1)) == BuilderActions.SettingResult.NO_MODULE
                && BuilderMix.read(plain) == null, "没装混铺拒");
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);

        // 施工中拒
        BuilderSelection.setStoredBlock(stack, cobble);
        player.getInventory().items.set(1, new ItemStack(Items.COBBLESTONE, 64));
        player.getInventory().items.set(2, new ItemStack(Items.OAK_PLANKS, 64));
        startOk(helper, player);
        helper.assertTrue(BuilderActions.setMix(player, hand, List.of(planks), List.of(1)) == BuilderActions.SettingResult.BUSY, "施工中拒");
        helper.assertTrue(stack.getTag().getCompound(BuilderMix.TAG).equals(before), "施工中没改 NBT");

        // 包：编解码来回一致；经 handle 走一遍；项数超上限解不开
        C2SBuilderActionPacket packet = roundTrip(C2SBuilderActionPacket.setMix(0, List.of(cobble, planks), List.of(2, 5)));
        helper.assertTrue(packet.action() == C2SBuilderActionPacket.SET_MIX && packet.ids().equals(List.of(cobble, planks))
                && packet.weights().equals(List.of(2, 5)), "编解码来回一致");
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeByte(C2SBuilderActionPacket.SET_MIX);
        buf.writeByte(0);
        buf.writeVarInt(C2SBuilderActionPacket.MAX_LIST + 1);
        buf.writeVarInt(0);
        boolean threw = false;
        try {
            C2SBuilderActionPacket.decode(buf);
        } catch (RuntimeException e) {
            threw = true;
        }
        helper.assertTrue(threw, "项数超上限的包解不开");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    helper.assertTrue(BuilderActions.handle(packet, player), "做完以后经包设表");
                    BuilderMix.Table now = Objects.requireNonNull(BuilderMix.read(stack));
                    helper.assertTrue(now.size() == 2 && now.entries().get(1).weight() == 5, "包设的表写进去了");
                    helper.assertTrue(BuilderActions.handle(roundTrip(C2SBuilderActionPacket.setMix(0, List.of(), List.of())), player)
                            && !stack.getOrCreateTag().contains(BuilderMix.TAG), "空表清空");
                })
                .thenSucceed();
    }

    private static C2SBuilderActionPacket roundTrip(C2SBuilderActionPacket packet) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        C2SBuilderActionPacket.encode(packet, buf);
        C2SBuilderActionPacket back = C2SBuilderActionPacket.decode(buf);
        if (buf.readableBytes() != 0) {
            throw new IllegalStateException("包没读完，剩 " + buf.readableBytes() + " 字节");
        }
        return back;
    }

    // ------------------------------------------------------------------
    // 回收
    // ------------------------------------------------------------------

    /** 清除模式铺面下摆好 blocks（按格子顺序轮着摆），返回范围 */
    private static BuilderArea lay(GameTestHelper helper, ServerPlayer player, ItemStack stack, Block... blocks) {
        BuilderArea area = area(player, stack);
        for (int i = 0; i < area.size(); i++) {
            helper.getLevel().setBlockAndUpdate(area.cellAt(i, false), blocks[i % blocks.length].defaultBlockState());
        }
        return area;
    }

    /** 拆 9 格木板：全进背包里的测试容器，背包里没多出木板 */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 200)
    public static void builderSalvageIntoContainer(GameTestHelper helper) {
        ItemStack stack = wand(BuilderMode.CLEAR, 3, 3, BuilderModules.Kind.SALVAGE);
        ServerPlayer player = flyer(helper, stack, 5, 4, 3);
        ItemStack bag = bag();
        player.getInventory().items.set(1, bag);
        lay(helper, player, stack, Blocks.OAK_PLANKS);
        startOk(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    helper.assertTrue(result(helper, player).done() == 9, "拆了 9 格");
                    helper.assertTrue(countIn(BuilderTestContainers.handler(bag), Items.OAK_PLANKS) == 9, "9 块进了容器");
                    helper.assertTrue(count(player, Items.OAK_PLANKS) == 0 && dropped(helper, player, Items.OAK_PLANKS) == 0, "背包里、地上都没有");
                })
                .thenSucceed();
    }

    /** 容器只剩 4 格空、背包只剩 2 格空：4 块进容器、2 块进背包、3 块掉脚下，加起来正好 9 */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 200)
    public static void builderSalvageOverflow(GameTestHelper helper) {
        ItemStack stack = wand(BuilderMode.CLEAR, 3, 3, BuilderModules.Kind.SALVAGE);
        ServerPlayer player = flyer(helper, stack, 5, 4, 3);
        clearDrops(helper, player);
        ItemStack bag = bag();
        IItemHandler handler = BuilderTestContainers.handler(bag);
        for (int i = 0; i < 8; i++) {
            handler.insertItem(i, new ItemStack(Items.DIRT, 64), false);
        }
        handler.insertItem(8, new ItemStack(Items.OAK_PLANKS, 60), false);
        player.getInventory().items.set(1, bag);
        player.getInventory().items.set(2, new ItemStack(Items.OAK_PLANKS, 62));
        for (int i = 3; i < player.getInventory().items.size(); i++) {
            player.getInventory().items.set(i, new ItemStack(Items.DIRT, 64));
        }
        lay(helper, player, stack, Blocks.OAK_PLANKS);
        startOk(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    int inBag = countIn(handler, Items.OAK_PLANKS) - 60;
                    int inInventory = count(player, Items.OAK_PLANKS) - 62;
                    int onGround = dropped(helper, player, Items.OAK_PLANKS);
                    helper.assertTrue(inBag == 4 && inInventory == 2 && onGround == 3,
                            "容器 4、背包 2、地上 3，实际 " + inBag + " / " + inInventory + " / " + onGround);
                    clearDrops(helper, player);
                })
                .thenSucceed();
    }

    /** 丢弃清单里有木板：拆 5 格木板 + 4 格泥土，木板一块不给，泥土 4 块进容器 */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 200)
    public static void builderSalvageDiscardList(GameTestHelper helper) {
        ItemStack stack = wand(BuilderMode.CLEAR, 3, 3, BuilderModules.Kind.SALVAGE);
        ServerPlayer player = flyer(helper, stack, 5, 4, 3);
        clearDrops(helper, player);
        ItemStack bag = bag();
        player.getInventory().items.set(1, bag);
        helper.assertTrue(BuilderActions.setDiscard(player, InteractionHand.MAIN_HAND, List.of(id(Items.OAK_PLANKS)))
                == BuilderActions.SettingResult.OK, "设丢弃清单");
        helper.assertTrue(BuilderSalvage.discardList(stack).equals(List.of(Items.OAK_PLANKS)), "写进 NBT");
        lay(helper, player, stack, Blocks.OAK_PLANKS, Blocks.DIRT);
        startOk(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    helper.assertTrue(result(helper, player).done() == 9, "9 格都拆了");
                    IItemHandler handler = BuilderTestContainers.handler(bag);
                    helper.assertTrue(countIn(handler, Items.DIRT) == 4 && countIn(handler, Items.OAK_PLANKS) == 0, "泥土 4 进容器、木板没有");
                    helper.assertTrue(count(player, Items.OAK_PLANKS) == 0 && dropped(helper, player, Items.OAK_PLANKS) == 0, "木板直接不要");
                })
                .thenSucceed();
    }

    /**
     * 全部替换 + 混铺 + 回收，料全在随身容器里：表里有的（木板）不换，泥土格按预览换成木板或圆石、料从容器里扣；
     * 换下来的泥土进同一个容器；总数守恒
     */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 200)
    public static void builderReplaceMixSalvage(GameTestHelper helper) {
        ItemStack stack = wand(BuilderMode.REPLACE_ALL, 3, 3, BuilderModules.Kind.MIX, BuilderModules.Kind.SALVAGE);
        BuilderMix.write(stack, List.of(entry(Blocks.OAK_PLANKS, 1), entry(Blocks.COBBLESTONE, 1)), 31);
        ServerPlayer player = flyer(helper, stack, 5, 4, 3);
        clearDrops(helper, player);
        ItemStack bag = bag();
        IItemHandler handler = BuilderTestContainers.handler(bag);
        handler.insertItem(0, new ItemStack(Items.OAK_PLANKS, 10), false);
        handler.insertItem(1, new ItemStack(Items.COBBLESTONE, 10), false);
        player.getInventory().items.set(1, bag);
        BuilderArea area = lay(helper, player, stack, Blocks.OAK_PLANKS, Blocks.DIRT);
        BuilderMix.Table table = Objects.requireNonNull(BuilderMix.forWand(stack, BuilderMode.REPLACE_ALL));
        long seed = BuilderMix.seedFor(table, area);
        int wantPlanks = 0;
        int wantCobble = 0;
        for (int i = 0; i < area.size(); i++) {
            if (i % 2 == 1) {
                if (BuilderMix.blockAt(table, seed, area.box(), area.cellAt(i, true)) == Blocks.OAK_PLANKS) {
                    wantPlanks++;
                } else {
                    wantCobble++;
                }
            }
        }
        int planksToPlace = wantPlanks;
        int cobbleToPlace = wantCobble;
        startOk(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    ServerLevel level = helper.getLevel();
                    helper.assertTrue(result(helper, player).done() == 4, "只换 4 格泥土，实际 " + result(helper, player).done());
                    for (int i = 0; i < area.size(); i++) {
                        BlockPos pos = area.cellAt(i, true);
                        Block want = i % 2 == 0 ? Blocks.OAK_PLANKS : BuilderMix.blockAt(table, seed, area.box(), pos);
                        helper.assertTrue(level.getBlockState(pos).is(want), "第 " + i + " 格应是 " + want + "，实际 " + level.getBlockState(pos));
                    }
                    helper.assertTrue(countIn(handler, Items.OAK_PLANKS) == 10 - planksToPlace && countIn(handler, Items.COBBLESTONE) == 10 - cobbleToPlace,
                            "料从容器里各扣各的");
                    helper.assertTrue(countIn(handler, Items.DIRT) == 4 && count(player, Items.DIRT) == 0 && dropped(helper, player, Items.DIRT) == 0,
                            "换下来的 4 块泥土进了容器");
                })
                .thenSucceed();
    }

    /** 开着别的界面时回收不碰随身容器（有的容器关界面会把旧内容整份写回），照旧进背包；关了以后的下一单照常进容器 */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 200)
    public static void builderSalvageSkipsWhileMenuOpen(GameTestHelper helper) {
        ItemStack stack = wand(BuilderMode.CLEAR, 3, 3, BuilderModules.Kind.SALVAGE);
        ServerPlayer player = flyer(helper, stack, 5, 4, 3);
        ItemStack bag = bag();
        player.getInventory().items.set(1, bag);
        lay(helper, player, stack, Blocks.OAK_PLANKS);
        player.containerMenu = net.minecraft.world.inventory.ChestMenu.threeRows(1, player.getInventory());
        startOk(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    helper.assertTrue(count(player, Items.OAK_PLANKS) == 9 && countIn(BuilderTestContainers.handler(bag), Items.OAK_PLANKS) == 0,
                            "界面开着：9 块进背包、容器不碰");
                    player.containerMenu = player.inventoryMenu;
                    lay(helper, player, stack, Blocks.DIRT);
                    startOk(helper, player);
                })
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> helper.assertTrue(countIn(BuilderTestContainers.handler(bag), Items.DIRT) == 9, "关了界面照常进容器"))
                .thenSucceed();
    }

    /** 没装回收（魔杖上留着丢弃清单）：照旧进背包，容器不收、清单不生效 */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 200)
    public static void builderNoSalvageUnchanged(GameTestHelper helper) {
        ItemStack stack = wand(BuilderMode.CLEAR, 3, 3);
        BuilderSalvage.writeDiscard(stack, List.of(Items.OAK_PLANKS));
        ServerPlayer player = flyer(helper, stack, 5, 4, 3);
        ItemStack bag = bag();
        player.getInventory().items.set(1, bag);
        lay(helper, player, stack, Blocks.OAK_PLANKS);
        startOk(helper, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    helper.assertTrue(count(player, Items.OAK_PLANKS) == 9, "9 块进背包，实际 " + count(player, Items.OAK_PLANKS));
                    helper.assertTrue(countIn(BuilderTestContainers.handler(bag), Items.OAK_PLANKS) == 0, "容器不收");
                })
                .thenSucceed();
    }

    /**
     * 设丢弃清单：没装回收（含铺设魔杖手改装上的）、施工中拒；不认得的、空气、重复、超过上限整张不收；空表清空；包编解码来回一致
     */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 200)
    public static void builderSalvageSettingRejects(GameTestHelper helper) {
        ItemStack stack = wand(BuilderMode.CLEAR, 3, 3, BuilderModules.Kind.SALVAGE);
        ServerPlayer player = flyer(helper, stack, 5, 4, 3);
        InteractionHand hand = InteractionHand.MAIN_HAND;
        ResourceLocation planks = id(Items.OAK_PLANKS);
        helper.assertTrue(BuilderActions.setDiscard(player, hand, List.of(planks, id(Items.DIRT))) == BuilderActions.SettingResult.OK, "好的收下");
        List<Item> before = BuilderSalvage.discardList(stack);
        helper.assertTrue(before.equals(List.of(Items.OAK_PLANKS, Items.DIRT)), "写进 NBT：" + before);
        helper.assertTrue(BuilderActions.setDiscard(player, hand, List.of(ResourceLocation.fromNamespaceAndPath("essentialcraft5", "no_such_item")))
                == BuilderActions.SettingResult.INVALID, "不认得的拒");
        helper.assertTrue(BuilderActions.setDiscard(player, hand, List.of(id(Items.AIR))) == BuilderActions.SettingResult.INVALID, "空气拒");
        helper.assertTrue(BuilderActions.setDiscard(player, hand, List.of(planks, planks)) == BuilderActions.SettingResult.INVALID, "重复拒");
        List<ResourceLocation> many = new ArrayList<>();
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            if (item != Items.AIR && many.size() <= ECBuilder.Config.salvageDiscardMax()) {
                many.add(id(item));
            }
        }
        helper.assertTrue(BuilderActions.setDiscard(player, hand, many) == BuilderActions.SettingResult.INVALID, "超过上限拒");
        helper.assertTrue(BuilderSalvage.discardList(stack).equals(before), "被拒的没改 NBT");

        ItemStack paver = new ItemStack(ECBuilder.PAVING_WAND.get());
        ListTag modules = new ListTag();
        modules.add(StringTag.valueOf(BuilderModules.Kind.SALVAGE.registryName().toString()));
        paver.getOrCreateTag().put(BuilderModules.TAG, modules);
        player.setItemInHand(InteractionHand.OFF_HAND, paver);
        helper.assertTrue(BuilderActions.setDiscard(player, InteractionHand.OFF_HAND, List.of(planks)) == BuilderActions.SettingResult.NO_MODULE,
                "铺设魔杖上回收不生效，拒");
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);

        C2SBuilderActionPacket packet = roundTrip(C2SBuilderActionPacket.setDiscard(0, List.of(planks)));
        helper.assertTrue(packet.action() == C2SBuilderActionPacket.SET_DISCARD && packet.ids().equals(List.of(planks)), "编解码来回一致");

        // 施工中拒（一格木板，开工的那一刻还在任务表里）
        lay(helper, player, stack, Blocks.OAK_PLANKS);
        startOk(helper, player);
        helper.assertTrue(BuilderActions.setDiscard(player, hand, List.of()) == BuilderActions.SettingResult.BUSY, "施工中拒");
        helper.assertTrue(BuilderSalvage.discardList(stack).equals(before), "施工中没改");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在施工"))
                .thenExecute(() -> {
                    helper.assertTrue(BuilderActions.handle(packet, player) && BuilderSalvage.discardList(stack).equals(List.of(Items.OAK_PLANKS)),
                            "经包设清单");
                    helper.assertTrue(BuilderActions.setDiscard(player, hand, List.of()) == BuilderActions.SettingResult.OK
                            && !stack.getOrCreateTag().contains(BuilderSalvage.DISCARD_TAG), "空表清空");
                    clearDrops(helper, player);
                })
                .thenSucceed();
    }
}
