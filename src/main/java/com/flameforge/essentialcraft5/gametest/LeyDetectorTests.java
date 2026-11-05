package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IMRUHandlerItem;
import com.flameforge.essentialcraft5.item.LeyDetectorItem;
import com.flameforge.essentialcraft5.ley.LeyScan;
import com.flameforge.essentialcraft5.registry.ECLeyDetector;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 地脉探测仪（第十二批，batch12.md §4.3.8）的 GameTest：
 * <ul>
 *   <li>右键扣 1 500、上 200 刻冷却、发一个扫描包；冷却中再按不扣；</li>
 *   <li>MRU 不够不扣、不发包；</li>
 *   <li>潜行右键切模式，不扣 MRU、不上冷却；</li>
 *   <li>扫描函数按距离留最近的几格、从近到远；</li>
 *   <li>方块标签：钻石矿算贵重，铁矿算矿、不算贵重，元素蕴魔矿是紫色那一类。</li>
 * </ul>
 * 测试玩家是 {@code ProtectionFrameworkTests.realPlayer}（不在世界里、连接是假的，发给它的包只进队列）。扫描测试只看测试结构里面的方块
 * （外面换成空气），相邻测试摆的矿不会混进来。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class LeyDetectorTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    private static final ECLeyDetector.Config.Values D = ECLeyDetector.Config.DEFAULTS;

    private LeyDetectorTests() {
    }

    /** 自带 5 000 的探测仪右键：剩 3 500、冷却 200 刻；冷却中再按不扣 */
    @GameTest(template = EMPTY)
    public static void leyScanCostsAndCooldown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "ec5-ley-scan");
        LeyDetectorItem item = ECLeyDetector.LEY_DETECTOR.get();
        ItemStack stack = detector(5000);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        int sent = LeyDetectorItem.testScansSent();
        helper.assertTrue(stack.use(level, player, InteractionHand.MAIN_HAND).getResult() == InteractionResult.CONSUME, "扫描应成功");
        helper.assertTrue(mru(stack) == 5000 - D.mruPerScan() && D.mruPerScan() == 1500, "应扣 1 500，剩 " + mru(stack));
        helper.assertTrue(LeyDetectorItem.testScansSent() == sent + 1, "应给本人发一个扫描包");
        helper.assertTrue(player.getCooldowns().isOnCooldown(item), "扫完应进冷却");
        for (int i = 0; i < D.cooldownTicks() - 1; i++) {
            player.getCooldowns().tick();
        }
        helper.assertTrue(player.getCooldowns().isOnCooldown(item) && D.cooldownTicks() == 200, "199 刻后还在冷却（默认 200）");
        helper.assertTrue(stack.use(level, player, InteractionHand.MAIN_HAND).getResult() == InteractionResult.FAIL, "冷却中应按不动");
        helper.assertTrue(mru(stack) == 3500 && LeyDetectorItem.testScansSent() == sent + 1, "冷却中不扣、不发包，剩 " + mru(stack));
        player.getCooldowns().tick();
        helper.assertFalse(player.getCooldowns().isOnCooldown(item), "200 刻后冷却结束");
        helper.succeed();
    }

    /** 只有 1 000：不扣、不发包、不进冷却 */
    @GameTest(template = EMPTY)
    public static void leyNotEnoughNoScan(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "ec5-ley-poor");
        ItemStack stack = detector(1000);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        int sent = LeyDetectorItem.testScansSent();
        helper.assertTrue(stack.use(level, player, InteractionHand.MAIN_HAND).getResult() == InteractionResult.FAIL, "MRU 不够应按不动");
        helper.assertTrue(mru(stack) == 1000, "不该扣，剩 " + mru(stack));
        helper.assertTrue(LeyDetectorItem.testScansSent() == sent, "不该发包");
        helper.assertFalse(player.getCooldowns().isOnCooldown(stack.getItem()), "不该进冷却");
        helper.succeed();
    }

    /** 潜行右键：全部 → 只看贵重 → 聚集体（13c）→ 全部；不扣 MRU、不进冷却、不发包；回到「全部」时 NBT 里两个键都不留 */
    @GameTest(template = EMPTY)
    public static void leySneakToggles(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "ec5-ley-mode");
        ItemStack stack = detector(5000);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.setShiftKeyDown(true);
        int sent = LeyDetectorItem.testScansSent();
        helper.assertFalse(LeyDetectorItem.valuableOnly(stack), "新做的是「全部矿石」");
        helper.assertTrue(stack.use(level, player, InteractionHand.MAIN_HAND).getResult().consumesAction(), "潜行右键应切换");
        helper.assertTrue(LeyDetectorItem.valuableOnly(stack), "应切到「只看贵重」");
        stack.use(level, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(LeyDetectorItem.presenceMode(stack) && !LeyDetectorItem.valuableOnly(stack), "再按切到「聚集体」");
        stack.use(level, player, InteractionHand.MAIN_HAND);
        helper.assertFalse(LeyDetectorItem.valuableOnly(stack) || LeyDetectorItem.presenceMode(stack), "第三下切回「全部矿石」");
        helper.assertTrue(stack.getTag() == null || !stack.getTag().contains(LeyDetectorItem.TAG_VALUABLE_ONLY)
                && !stack.getTag().contains(LeyDetectorItem.TAG_PRESENCE), "回到「全部」时不留键");
        helper.assertTrue(mru(stack) == 5000 && LeyDetectorItem.testScansSent() == sent, "切模式不扣、不发包");
        helper.assertFalse(player.getCooldowns().isOnCooldown(stack.getItem()), "切模式不进冷却");
        helper.succeed();
    }

    /** 三块矿离中心 2 / 5 / 9 格：最多留 2 格时只回 2、5 那两块，从近到远 */
    @GameTest(template = EMPTY_LARGE)
    public static void leyCollectNearestFirst(GameTestHelper helper) {
        BlockPos centerRel = new BlockPos(1, 2, 1);
        BlockPos nearRel = centerRel.offset(0, 0, 2);
        BlockPos midRel = centerRel.offset(5, 0, 0);
        BlockPos farRel = centerRel.offset(0, 0, 9);
        helper.setBlock(nearRel, Blocks.IRON_ORE);
        helper.setBlock(midRel, Blocks.DIAMOND_ORE);
        helper.setBlock(farRel, Blocks.GOLD_ORE);
        BlockGetter inside = insideStructure(helper, new BlockPos(10, 7, 10));
        BlockPos center = helper.absolutePos(centerRel);
        List<BlockPos> found = LeyScan.collect(inside, center, 10, state -> state.is(ECLeyDetector.TARGETS), 2);
        helper.assertTrue(found.size() == 2, "最多留 2 格，实际 " + found.size());
        helper.assertTrue(found.get(0).equals(helper.absolutePos(nearRel)), "第一格应是 2 格外那块，实际 " + found.get(0));
        helper.assertTrue(found.get(1).equals(helper.absolutePos(midRel)), "第二格应是 5 格外那块，实际 " + found.get(1));
        List<BlockPos> all = LeyScan.collect(inside, center, 10, state -> state.is(ECLeyDetector.TARGETS), 256);
        helper.assertTrue(all.size() == 3 && all.get(2).equals(helper.absolutePos(farRel)), "不限个数时三块都在、9 格外那块在最后：" + all);
        List<BlockPos> valuable = LeyScan.collect(inside, center, 10,
                state -> state.is(ECLeyDetector.TARGETS) && state.is(ECLeyDetector.VALUABLE), 256);
        helper.assertTrue(valuable.size() == 2 && !valuable.contains(helper.absolutePos(nearRel)), "只看贵重时没有铁矿：" + valuable);
        List<BlockPos> small = LeyScan.collect(inside, center, 4, state -> state.is(ECLeyDetector.TARGETS), 256);
        helper.assertTrue(small.size() == 1, "半径 4 只够到 2 格外那块，实际 " + small);
        helper.succeed();
    }

    /** 钻石矿在 valuable 里；铁矿在 targets、不在 valuable；元素蕴魔矿在 targets 和 ec 里；石头哪个都不在 */
    @GameTest(template = EMPTY)
    public static void leyTagsResolve(GameTestHelper helper) {
        BlockState diamond = Blocks.DIAMOND_ORE.defaultBlockState();
        BlockState iron = Blocks.IRON_ORE.defaultBlockState();
        helper.assertTrue(diamond.is(ECLeyDetector.TARGETS) && diamond.is(ECLeyDetector.VALUABLE), "钻石矿应算矿、算贵重");
        helper.assertTrue(Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState().is(ECLeyDetector.VALUABLE), "深层钻石矿也算贵重");
        helper.assertTrue(Blocks.ANCIENT_DEBRIS.defaultBlockState().is(ECLeyDetector.VALUABLE), "远古残骸算贵重");
        helper.assertTrue(iron.is(ECLeyDetector.TARGETS) && !iron.is(ECLeyDetector.VALUABLE), "铁矿应算矿、不算贵重");
        helper.assertFalse(Blocks.STONE.defaultBlockState().is(ECLeyDetector.TARGETS), "石头不算矿");
        helper.assertFalse(iron.is(ECLeyDetector.IGNORED), "默认的忽略表是空的");
        var fireOre = ForgeRegistries.BLOCKS.getValue(EssentialCraft5.id("fire_infused_ore"));
        if (fireOre != null && fireOre != Blocks.AIR) {
            BlockState state = fireOre.defaultBlockState();
            helper.assertTrue(state.is(ECLeyDetector.TARGETS) && state.is(ECLeyDetector.EC) && !state.is(ECLeyDetector.VALUABLE),
                    "火之蕴魔矿应算矿、是本模组的紫色那一类、不算贵重");
        }
        var mithriline = ForgeRegistries.BLOCKS.getValue(EssentialCraft5.id("mithriline_ore"));
        if (mithriline != null && mithriline != Blocks.AIR) {
            helper.assertTrue(mithriline.defaultBlockState().is(ECLeyDetector.VALUABLE), "米斯莱矿算贵重");
        }
        helper.succeed();
    }

    // ================================================================== 工具

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        player.setGameMode(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(2.5D, 2D, 2.5D));
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        return player;
    }

    private static ItemStack detector(int mru) {
        ItemStack stack = new ItemStack(ECLeyDetector.LEY_DETECTOR.get());
        handler(stack).setMRU(mru);
        return stack;
    }

    private static IMRUHandlerItem handler(ItemStack stack) {
        return stack.getCapability(ECCapabilities.MRU_ITEM).orElseThrow(() -> new IllegalStateException("探测仪应带 MRU 能力"));
    }

    private static int mru(ItemStack stack) {
        return handler(stack).getMRU();
    }

    /**
     * 只露出测试结构里面的方块（相对坐标 0 ~ maxRel），外面一律当空气：相邻测试摆的矿不会混进扫描结果。
     * 结构没转向，两个对角换算成绝对坐标后取每个轴的最小、最大值。
     */
    private static BlockGetter insideStructure(GameTestHelper helper, BlockPos maxRel) {
        ServerLevel level = helper.getLevel();
        BlockPos a = helper.absolutePos(BlockPos.ZERO);
        BlockPos b = helper.absolutePos(maxRel);
        int minX = Math.min(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxX = Math.max(a.getX(), b.getX());
        int maxY = Math.max(a.getY(), b.getY());
        int maxZ = Math.max(a.getZ(), b.getZ());
        return new BlockGetter() {
            private boolean inside(BlockPos pos) {
                return pos.getX() >= minX && pos.getX() <= maxX && pos.getY() >= minY && pos.getY() <= maxY && pos.getZ() >= minZ && pos.getZ() <= maxZ;
            }

            @Override
            public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
                return null;
            }

            @Override
            public BlockState getBlockState(BlockPos pos) {
                return inside(pos) ? level.getBlockState(pos) : Blocks.AIR.defaultBlockState();
            }

            @Override
            public FluidState getFluidState(BlockPos pos) {
                return getBlockState(pos).getFluidState();
            }

            @Override
            public int getHeight() {
                return level.getHeight();
            }

            @Override
            public int getMinBuildHeight() {
                return level.getMinBuildHeight();
            }
        };
    }
}
