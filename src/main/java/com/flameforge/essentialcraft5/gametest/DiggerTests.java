package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.blockentity.CrystalFormerBlockEntity;
import com.flameforge.essentialcraft5.item.MagicalDiggerEvents;
import com.flameforge.essentialcraft5.item.MagicalDiggerItem;
import com.flameforge.essentialcraft5.registry.ECDigger;
import com.flameforge.essentialcraft5.registry.ECItems;
import com.flameforge.essentialcraft5.ritual.GroundRituals;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 魔力挖掘器与淡青宝石（ECDigger，第四批 4b，batch4 §3.10）的 GameTest：
 * <ul>
 *   <li>范围挖：3×3×3 里只挖同种方块、每格 9 MRU（含中心）、每格都掉落；推迟到本 tick 末尾（挖中心那一下邻格不动）；
 *       邻格的破坏事件不嵌套在中心格的 destroyBlock 里、中心格照常掉落；5×5×5 里只少 27 格（不递归）；
 *       没有任何领地时照常工作（纯 Forge 功能完整）。</li>
 *   <li>限制：被拒的邻格不动、不扣它的 MRU、只发一次破坏事件；中心格被拒什么都不做；潜行只挖中心也扣 9；
 *       自己存的 MRU 不到 9 时速度 1、不连挖、不扣；先扣身上的储存物品；钱不够就停；主手换了（哪怕换成另一把挖掘器）就作废；
 *       每名玩家同一 tick 只有一个待办；假玩家拿着它不连挖。</li>
 *   <li>挖掘时间照手册：没电时挖石头两秒多、黑曜石一分多钟（效率魔咒不起作用，但仍比空手快 3 倍多），满 MRU 时黑曜石两秒多。</li>
 *   <li>不掉耐久、附不上耐久 / 经验修补，能附效率 / 时运 / 精准采集；什么方块都算用对了工具。</li>
 *   <li>淡青宝石：不在 forge 的钻石 / 绿宝石标签里，在 EC 内部的 {@code essentialcraft5:diamonds / emeralds} 里；
 *       两条辐射仓配方已加载、原料仍是 forge 标签（淡青宝石不能自循环）；挖掘器配方已加载；
 *       水晶成型器的钻石格、钻石 / 绿宝石地面仪式不收它（手册「不收它的地方」）。</li>
 * </ul>
 * 真玩家用 {@link ProtectionFrameworkTests#realPlayer}（不在玩家列表里；范围挖按待办里的玩家对象判断在线，不查列表），模拟领地用
 * {@link ProtectionFrameworkTests.Claims}。挖用 {@code player.gameMode.destroyBlock}（服务端真实的玩家挖方块流程）。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class DiggerTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    private static final int FULL = MagicalDiggerItem.MAX_MRU;
    private static final int COST = MagicalDiggerItem.COST;
    /** 「empty」里 3×3×3 立方体的中心（立方体占 (1..3, 1..3, 1..3)） */
    private static final BlockPos CENTER = new BlockPos(2, 2, 2);
    /** 假玩家测试用的固定身份（FakePlayerFactory 按身份缓存） */
    private static final GameProfile FAKE = new GameProfile(UUID.fromString("5cd89d0b-e9ba-0000-89f4-d166e7000004"), "[EC5]DiggerTest");

    private DiggerTests() {
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private static ItemStack digger() {
        return MagicalDiggerItem.full(ECDigger.MAGICAL_DIGGER.get());
    }

    private static ItemStack digger(int mru) {
        ItemStack stack = new ItemStack(ECDigger.MAGICAL_DIGGER.get());
        stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(mru));
        return stack;
    }

    private static int mru(ItemStack stack) {
        return MagicalDiggerItem.storedMRU(stack);
    }

    private static ServerPlayer holding(GameTestHelper helper, ItemStack stack) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return player;
    }

    /** 以 center 为中心、半边长 r 的立方体全填成 block */
    private static void fill(GameTestHelper helper, BlockPos center, int r, Block block) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
            helper.setBlock(pos.immutable(), block);
        }
    }

    /** 立方体里还剩几格 block */
    private static int count(GameTestHelper helper, BlockPos center, int r, Block block) {
        int n = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
            if (helper.getBlockState(pos.immutable()).is(block)) {
                n++;
            }
        }
        return n;
    }

    private static boolean mine(GameTestHelper helper, ServerPlayer player, BlockPos relative) {
        return player.gameMode.destroyBlock(helper.absolutePos(relative));
    }

    private static AABB around(GameTestHelper helper, BlockPos relative, double radius) {
        return new AABB(helper.absolutePos(relative)).inflate(radius);
    }

    /** 这一片地上某种掉落物的总数 */
    private static int dropped(GameTestHelper helper, BlockPos relative, double radius, Item item) {
        int n = 0;
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, around(helper, relative, radius), Entity::isAlive)) {
            if (entity.getItem().is(item)) {
                n += entity.getItem().getCount();
            }
        }
        return n;
    }

    private static void clearDrops(GameTestHelper helper, BlockPos relative, double radius) {
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, around(helper, relative, radius)).forEach(Entity::discard);
    }

    // ------------------------------------------------------------------
    // 范围挖
    // ------------------------------------------------------------------

    /**
     * 没有任何领地（纯 Forge、单人的情形）：3×3×3 里的石头全挖掉，混在里面的泥土不动；挖中心那一下只动中心、扣 9，
     * 邻格到本 tick 末尾才挖；每块石头扣 9 MRU（含中心）、各掉一个圆石
     */
    @GameTest(template = EMPTY)
    public static void diggerAreaMinesSameBlockWithoutClaims(GameTestHelper helper) {
        fill(helper, CENTER, 1, Blocks.STONE);
        List<BlockPos> dirt = List.of(new BlockPos(1, 1, 1), new BlockPos(3, 2, 1), new BlockPos(2, 3, 3));
        dirt.forEach(pos -> helper.setBlock(pos, Blocks.DIRT));
        int stones = 27 - dirt.size();
        ItemStack tool = digger();
        ServerPlayer player = holding(helper, tool);
        helper.assertTrue(mine(helper, player, CENTER), "中心那格应挖得掉");
        helper.assertBlockPresent(Blocks.AIR, CENTER);
        helper.assertTrue(count(helper, CENTER, 1, Blocks.STONE) == stones - 1, "挖中心那一下不应动邻格（推迟到本 tick 末尾）");
        helper.assertTrue(mru(tool) == FULL - COST, "中心格应扣 9 MRU，剩 " + mru(tool));
        helper.runAfterDelay(1, () -> {
            try {
                helper.assertTrue(count(helper, CENTER, 1, Blocks.STONE) == 0, "3×3×3 里的石头应全挖掉，还剩 " + count(helper, CENTER, 1, Blocks.STONE));
                for (BlockPos pos : dirt) {
                    helper.assertBlockPresent(Blocks.DIRT, pos);
                }
                helper.assertTrue(mru(tool) == FULL - COST * stones, "应扣 9 × " + stones + " MRU，剩 " + mru(tool));
                int cobble = dropped(helper, CENTER, 2.5D, Items.COBBLESTONE);
                helper.assertTrue(cobble == stones, "每块石头（含中心）应各掉一个圆石，实际 " + cobble);
            } finally {
                clearDrops(helper, CENTER, 3D);
            }
            helper.succeed();
        });
    }

    /** 模拟领地拒绝一个邻格：那格还在、只发一次破坏事件、不扣它的 MRU，其余照挖 */
    @GameTest(template = EMPTY)
    public static void diggerDeniedNeighborStaysAndIsFree(GameTestHelper helper) {
        fill(helper, CENTER, 1, Blocks.STONE);
        BlockPos denied = new BlockPos(3, 2, 2);
        ItemStack tool = digger();
        ServerPlayer player = holding(helper, tool);
        ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(denied);
        try {
            helper.assertTrue(mine(helper, player, CENTER), "中心那格没被拒，应挖得掉");
        } catch (RuntimeException e) {
            claims.close();
            throw e;
        }
        helper.runAfterDelay(1, () -> {
            try {
                helper.assertBlockPresent(Blocks.STONE, denied);
                helper.assertTrue(count(helper, CENTER, 1, Blocks.STONE) == 1, "只该剩被拒的那一格，实际 " + count(helper, CENTER, 1, Blocks.STONE));
                helper.assertTrue(mru(tool) == FULL - COST * 26, "被拒的那格不应扣钱：应扣 9 × 26，剩 " + mru(tool));
                helper.assertTrue(claims.breaks(denied) == 1, "被拒的格子应只收到一次破坏事件，实际 " + claims.breaks(denied));
            } finally {
                claims.close();
                clearDrops(helper, CENTER, 3D);
            }
            helper.succeed();
        });
    }

    /** 中心那格被拒：destroyBlock 返回 false，什么都不挖、不扣，也不连带 */
    @GameTest(template = EMPTY)
    public static void diggerDeniedCenterDoesNothing(GameTestHelper helper) {
        fill(helper, CENTER, 1, Blocks.STONE);
        ItemStack tool = digger();
        ServerPlayer player = holding(helper, tool);
        ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(CENTER);
        try {
            helper.assertFalse(mine(helper, player, CENTER), "中心那格被拒，destroyBlock 应返回 false");
            helper.assertBlockPresent(Blocks.STONE, CENTER);
            helper.assertTrue(mru(tool) == FULL, "被拒不应扣 MRU，剩 " + mru(tool));
        } catch (RuntimeException e) {
            claims.close();
            throw e;
        }
        helper.runAfterDelay(1, () -> {
            try {
                helper.assertTrue(count(helper, CENTER, 1, Blocks.STONE) == 27, "中心被拒时不应连带挖邻格");
                helper.assertTrue(mru(tool) == FULL, "中心被拒时不应扣 MRU，剩 " + mru(tool));
                helper.assertTrue(claims.breaks(CENTER) == 1, "中心格应只收到一次破坏事件，实际 " + claims.breaks(CENTER));
            } finally {
                claims.close();
            }
            helper.succeed();
        });
    }

    /** 潜行：只挖中心那一格，照样扣 9 MRU（原作潜行白用，修掉） */
    @GameTest(template = EMPTY)
    public static void diggerSneakMinesCenterOnly(GameTestHelper helper) {
        fill(helper, CENTER, 1, Blocks.STONE);
        ItemStack tool = digger();
        ServerPlayer player = holding(helper, tool);
        player.setShiftKeyDown(true);
        helper.assertTrue(mine(helper, player, CENTER), "潜行也应挖得掉中心那格");
        helper.runAfterDelay(1, () -> {
            try {
                helper.assertBlockPresent(Blocks.AIR, CENTER);
                helper.assertTrue(count(helper, CENTER, 1, Blocks.STONE) == 26, "潜行时不应连带挖邻格");
                helper.assertTrue(mru(tool) == FULL - COST, "潜行挖中心格也应扣 9 MRU，剩 " + mru(tool));
            } finally {
                clearDrops(helper, CENTER, 3D);
            }
            helper.succeed();
        });
    }

    /**
     * 挖掘器自己存的 MRU 不到 9：速度 1、不连挖、不扣（身上的储存物品是满的也一样，照原作看挖掘器自己的 MRU）；
     * 挖掉的那格照样掉落（什么方块都算用对了工具）
     */
    @GameTest(template = EMPTY)
    public static void diggerLowMRUIsSlowAndSingle(GameTestHelper helper) {
        fill(helper, CENTER, 1, Blocks.STONE);
        ItemStack tool = digger(COST - 1);
        ServerPlayer player = holding(helper, tool);
        ItemStack sphere = new ItemStack(ECItems.SOUL_SPHERE.get());
        sphere.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(handler.getMaxMRU()));
        int sphereFull = mru(sphere);
        player.getInventory().setItem(9, sphere);
        BlockState stone = Blocks.STONE.defaultBlockState();
        helper.assertTrue(tool.getDestroySpeed(stone) == MagicalDiggerItem.SLOW_SPEED, "MRU 不到 9 时速度应为 1，实际 " + tool.getDestroySpeed(stone));
        helper.assertTrue(digger().getDestroySpeed(Blocks.DIRT.defaultBlockState()) == MagicalDiggerItem.FAST_SPEED, "满 MRU 时挖什么都应是 32");
        helper.assertTrue(mine(helper, player, CENTER), "MRU 不够也应挖得掉（只是慢）");
        helper.runAfterDelay(1, () -> {
            try {
                helper.assertTrue(count(helper, CENTER, 1, Blocks.STONE) == 26, "MRU 不到 9 时不应连带挖");
                helper.assertTrue(mru(tool) == COST - 1, "慢速挖不应扣挖掘器的 MRU，剩 " + mru(tool));
                helper.assertTrue(mru(sphere) == sphereFull, "慢速挖不应扣储存物品的 MRU，剩 " + mru(sphere));
                helper.assertTrue(dropped(helper, CENTER, 2.5D, Items.COBBLESTONE) == 1, "慢速挖掉的石头也应掉圆石");
            } finally {
                clearDrops(helper, CENTER, 3D);
            }
            helper.succeed();
        });
    }

    /**
     * 手册「怎么用」页的挖掘时间：挖掘器自己存的 MRU 不到 9 时速度 1、效率魔咒不起作用，挖石头两秒多、黑曜石一分多钟；
     * 满 MRU 时黑曜石两秒多。什么方块都算用对了工具（挖掘进度除数 30），所以没电时挖黑曜石仍比空手（除数 100，约 250 秒）快 3 倍多，
     * 不是「和空手一样慢」（4b 审查 DIG-1）
     */
    @GameTest(template = EMPTY)
    public static void diggerSpeedMatchesGuide(GameTestHelper helper) {
        ItemStack slow = digger(COST - 1);
        slow.enchant(Enchantments.BLOCK_EFFICIENCY, 5);
        ServerPlayer player = holding(helper, slow);
        // 不在地上时挖掘速度再 ÷5；手册按站在地上挖算
        player.setOnGround(true);
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState();

        int slowStone = ticksToMine(helper, player, stone);
        int slowObsidian = ticksToMine(helper, player, obsidian);
        helper.assertTrue(slowStone > 40 && slowStone < 60, "没电（带效率 V）挖石头应两秒多（约 45 刻），实际 " + slowStone + " 刻");
        helper.assertTrue(slowObsidian >= 1200 && slowObsidian < 2400, "没电（带效率 V）挖黑曜石应一分多钟（约 1500 刻），实际 " + slowObsidian + " 刻");

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        int bareObsidian = ticksToMine(helper, player, obsidian);
        helper.assertTrue(bareObsidian > 3 * slowObsidian, "空手挖黑曜石（约 5000 刻）应比没电的挖掘器慢 3 倍多，空手 " + bareObsidian + " 刻、挖掘器 " + slowObsidian + " 刻");

        player.setItemInHand(InteractionHand.MAIN_HAND, digger());
        int fastObsidian = ticksToMine(helper, player, obsidian);
        helper.assertTrue(fastObsidian > 40 && fastObsidian < 60, "满 MRU 挖黑曜石应两秒多（约 47 刻），实际 " + fastObsidian + " 刻");
        helper.succeed();
    }

    /** 一直按着挖这种方块要几刻（照客户端 MultiPlayerGameMode：每刻累加 getDestroyProgress，到 1 挖掉） */
    private static int ticksToMine(GameTestHelper helper, ServerPlayer player, BlockState state) {
        float perTick = state.getDestroyProgress(player, helper.getLevel(), helper.absolutePos(CENTER));
        if (perTick <= 0F) {
            return Integer.MAX_VALUE;
        }
        float progress = 0F;
        int ticks = 0;
        while (progress < 1F && ticks < 100_000) {
            progress += perTick;
            ticks++;
        }
        return ticks;
    }

    /** 扣费先扣身上的储存物品（灵魂球），挖掘器自己的不动 */
    @GameTest(template = EMPTY)
    public static void diggerChargesStorageFirst(GameTestHelper helper) {
        fill(helper, CENTER, 1, Blocks.STONE);
        ItemStack tool = digger();
        ServerPlayer player = holding(helper, tool);
        ItemStack sphere = new ItemStack(ECItems.SOUL_SPHERE.get());
        sphere.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(handler.getMaxMRU()));
        int sphereFull = mru(sphere);
        player.getInventory().setItem(9, sphere);
        helper.assertTrue(mine(helper, player, CENTER), "中心那格应挖得掉");
        helper.runAfterDelay(1, () -> {
            try {
                helper.assertTrue(count(helper, CENTER, 1, Blocks.STONE) == 0, "石头应全挖掉");
                helper.assertTrue(mru(sphere) == sphereFull - COST * 27, "应从灵魂球扣 9 × 27，剩 " + mru(sphere));
                helper.assertTrue(mru(tool) == FULL, "储存物品够时不应扣挖掘器自己的，剩 " + mru(tool));
            } finally {
                clearDrops(helper, CENTER, 3D);
            }
            helper.succeed();
        });
    }

    /** 钱不够就停：挖掘器只存 9 × 6 MRU、身上没有储存物品 → 中心 + 5 格，之后停下 */
    @GameTest(template = EMPTY)
    public static void diggerStopsWhenOutOfMRU(GameTestHelper helper) {
        fill(helper, CENTER, 1, Blocks.STONE);
        ItemStack tool = digger(COST * 6);
        ServerPlayer player = holding(helper, tool);
        helper.assertTrue(mine(helper, player, CENTER), "中心那格应挖得掉");
        helper.runAfterDelay(1, () -> {
            try {
                helper.assertTrue(count(helper, CENTER, 1, Blocks.STONE) == 21, "应只挖掉中心 + 5 格，还剩 " + count(helper, CENTER, 1, Blocks.STONE));
                helper.assertTrue(mru(tool) == 0, "MRU 应正好用完，剩 " + mru(tool));
            } finally {
                clearDrops(helper, CENTER, 3D);
            }
            helper.succeed();
        });
    }

    /** 5×5×5 的石头里挖中心：只少 3×3×3 = 27 格，连带挖掉的格子不再各自连带（不递归） */
    @GameTest(template = EMPTY_LARGE)
    public static void diggerDoesNotRecurse(GameTestHelper helper) {
        BlockPos center = new BlockPos(5, 3, 5);
        fill(helper, center, 2, Blocks.STONE);
        ItemStack tool = digger();
        ServerPlayer player = holding(helper, tool);
        helper.assertTrue(mine(helper, player, center), "中心那格应挖得掉");
        helper.runAfterDelay(2, () -> {
            try {
                helper.assertTrue(count(helper, center, 1, Blocks.STONE) == 0, "3×3×3 应全挖掉");
                helper.assertTrue(count(helper, center, 2, Blocks.STONE) == 125 - 27, "5×5×5 应只少 27 格，还剩 " + count(helper, center, 2, Blocks.STONE));
                helper.assertTrue(mru(tool) == FULL - COST * 27, "应扣 9 × 27，剩 " + mru(tool));
            } finally {
                clearDrops(helper, center, 4D);
            }
            helper.succeed();
        });
    }

    /**
     * 邻格的破坏事件都不在中心格的 destroyBlock 里发（混合端的 Bukkit 破坏事件是全局单槽，嵌套会让中心格不掉落），
     * 而是在范围挖里（isAreaMining）逐格发；中心格照常掉落
     */
    @GameTest(template = EMPTY)
    public static void diggerNeighborsNotNested(GameTestHelper helper) {
        fill(helper, CENTER, 1, Blocks.STONE);
        ItemStack tool = digger();
        ServerPlayer player = holding(helper, tool);
        BlockPos absCenter = helper.absolutePos(CENTER);
        boolean[] insideOuter = {false};
        int[] neighborEvents = {0};
        List<String> wrong = new ArrayList<>();
        Consumer<BlockEvent.BreakEvent> listener = event -> {
            BlockPos pos = event.getPos();
            if (event.getLevel() != helper.getLevel() || Math.abs(pos.getX() - absCenter.getX()) > 1
                    || Math.abs(pos.getY() - absCenter.getY()) > 1 || Math.abs(pos.getZ() - absCenter.getZ()) > 1) {
                return;
            }
            if (pos.equals(absCenter)) {
                if (!insideOuter[0]) {
                    wrong.add("中心格的破坏事件应在外层 destroyBlock 里");
                }
                return;
            }
            neighborEvents[0]++;
            if (insideOuter[0]) {
                wrong.add("邻格 " + pos + " 的破坏事件嵌套在中心格的 destroyBlock 里");
            }
            if (!MagicalDiggerEvents.isAreaMining()) {
                wrong.add("邻格 " + pos + " 的破坏事件应在范围挖里发");
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, BlockEvent.BreakEvent.class, listener);
        try {
            insideOuter[0] = true;
            boolean mined;
            try {
                mined = mine(helper, player, CENTER);
            } finally {
                insideOuter[0] = false;
            }
            helper.assertTrue(mined, "中心那格应挖得掉");
            helper.assertTrue(neighborEvents[0] == 0, "挖中心那一下不应发邻格的破坏事件，实际 " + neighborEvents[0]);
            helper.assertFalse(MagicalDiggerEvents.isAreaMining(), "范围挖的标记不应留在外面");
        } catch (RuntimeException e) {
            MinecraftForge.EVENT_BUS.unregister(listener);
            throw e;
        }
        helper.runAfterDelay(1, () -> {
            try {
                helper.assertTrue(wrong.isEmpty(), String.join("；", wrong));
                helper.assertTrue(neighborEvents[0] == 26, "26 个邻格应各发一次破坏事件，实际 " + neighborEvents[0]);
                helper.assertTrue(dropped(helper, CENTER, 2.5D, Items.COBBLESTONE) == 27, "中心格和 26 个邻格都应掉落，实际 "
                        + dropped(helper, CENTER, 2.5D, Items.COBBLESTONE));
                helper.assertFalse(MagicalDiggerEvents.isAreaMining(), "范围挖结束后标记应复位");
            } finally {
                MinecraftForge.EVENT_BUS.unregister(listener);
                clearDrops(helper, CENTER, 3D);
            }
            helper.succeed();
        });
    }

    /** 挖完中心以后主手换了东西（哪怕换成另一把挖掘器）：这次范围挖作废，只扣了中心格 */
    @GameTest(template = EMPTY)
    public static void diggerSwitchingItemCancelsArea(GameTestHelper helper) {
        fill(helper, CENTER, 1, Blocks.STONE);
        ItemStack tool = digger();
        ServerPlayer player = holding(helper, tool);
        helper.assertTrue(mine(helper, player, CENTER), "中心那格应挖得掉");
        ItemStack other = digger();
        player.setItemInHand(InteractionHand.MAIN_HAND, other);
        helper.runAfterDelay(1, () -> {
            try {
                helper.assertTrue(count(helper, CENTER, 1, Blocks.STONE) == 26, "主手换了东西，不应连带挖");
                helper.assertTrue(mru(tool) == FULL - COST, "原来那把只应扣中心格，剩 " + mru(tool));
                helper.assertTrue(mru(other) == FULL, "换上的那把不应被扣，剩 " + mru(other));
            } finally {
                clearDrops(helper, CENTER, 3D);
            }
            helper.succeed();
        });
    }

    /** 同一名玩家同一 tick 挖了两块：第二块的范围挖作废（每人同时只有一个待办），第二块中心照扣 9 */
    @GameTest(template = EMPTY_LARGE)
    public static void diggerOnePendingPerPlayer(GameTestHelper helper) {
        BlockPos first = new BlockPos(3, 3, 3);
        BlockPos second = new BlockPos(7, 3, 7);
        fill(helper, first, 1, Blocks.STONE);
        fill(helper, second, 1, Blocks.STONE);
        ItemStack tool = digger();
        ServerPlayer player = holding(helper, tool);
        helper.assertTrue(mine(helper, player, first), "第一块应挖得掉");
        helper.assertTrue(mine(helper, player, second), "第二块应挖得掉");
        helper.runAfterDelay(1, () -> {
            try {
                helper.assertTrue(count(helper, first, 1, Blocks.STONE) == 0, "第一块周围应全挖掉");
                helper.assertTrue(count(helper, second, 1, Blocks.STONE) == 26, "第二块的范围挖应作废");
                helper.assertTrue(mru(tool) == FULL - COST * 28, "应扣 9 × (27 + 1)，剩 " + mru(tool));
            } finally {
                clearDrops(helper, first, 3D);
                clearDrops(helper, second, 3D);
            }
            helper.succeed();
        });
    }

    /** 假玩家（别的模组的自动化）拿着它：当普通镐用（中心格扣 9），不连带挖 */
    @GameTest(template = EMPTY)
    public static void diggerFakePlayerNoArea(GameTestHelper helper) {
        fill(helper, CENTER, 1, Blocks.STONE);
        FakePlayer fake = FakePlayerFactory.get(helper.getLevel(), FAKE);
        ItemStack tool = digger();
        fake.setItemInHand(InteractionHand.MAIN_HAND, tool);
        try {
            helper.assertTrue(fake.gameMode.destroyBlock(helper.absolutePos(CENTER)), "假玩家也应挖得掉中心那格");
        } finally {
            fake.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
        helper.runAfterDelay(1, () -> {
            try {
                helper.assertTrue(count(helper, CENTER, 1, Blocks.STONE) == 26, "假玩家不应连带挖");
                helper.assertTrue(mru(tool) == FULL - COST, "假玩家挖一格也扣 9，剩 " + mru(tool));
            } finally {
                clearDrops(helper, CENTER, 3D);
            }
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------
    // 物品本身
    // ------------------------------------------------------------------

    /** 没有耐久：附不上耐久、经验修补；能附效率、时运、精准采集；附魔能力 36；什么方块都算用对了工具 */
    @GameTest(template = EMPTY)
    public static void diggerEnchantmentsAndDurability(GameTestHelper helper) {
        ItemStack tool = digger();
        helper.assertFalse(tool.isDamageableItem(), "挖掘器不应有耐久");
        helper.assertTrue(tool.isEnchantable(), "挖掘器应能附魔");
        helper.assertTrue(tool.getEnchantmentValue() == 36, "附魔能力应为 36，实际 " + tool.getEnchantmentValue());
        helper.assertFalse(Enchantments.UNBREAKING.canEnchant(tool), "不应能附耐久");
        helper.assertFalse(Enchantments.MENDING.canEnchant(tool), "不应能附经验修补");
        helper.assertFalse(Enchantments.UNBREAKING.canApplyAtEnchantingTable(tool), "附魔台不应给耐久");
        helper.assertTrue(Enchantments.BLOCK_EFFICIENCY.canApplyAtEnchantingTable(tool), "附魔台应能给效率");
        helper.assertTrue(Enchantments.BLOCK_FORTUNE.canEnchant(tool), "应能附时运");
        helper.assertTrue(Enchantments.SILK_TOUCH.canEnchant(tool), "应能附精准采集");
        for (Block block : List.of(Blocks.OBSIDIAN, Blocks.ANCIENT_DEBRIS, Blocks.STONE, Blocks.COBWEB)) {
            helper.assertTrue(tool.isCorrectToolForDrops(block.defaultBlockState()), block + " 应算用对了工具");
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 淡青宝石
    // ------------------------------------------------------------------

    /**
     * 淡青宝石不在 forge 的钻石 / 绿宝石标签里（不能在别的模组配方里当钻石），在 EC 内部的 diamonds / emeralds 里（真钻石、真绿宝石也在）；
     * 两条辐射仓配方已加载，原料仍是 forge 标签（淡青宝石换不出淡青宝石）；挖掘器的工作台配方已加载
     */
    @GameTest(template = EMPTY)
    public static void diggerPaleGemTagsAndRecipes(GameTestHelper helper) {
        ItemStack gem = new ItemStack(ECDigger.PALE_GEM.get());
        helper.assertFalse(gem.is(Tags.Items.GEMS_DIAMOND), "淡青宝石不应进 forge:gems/diamond");
        helper.assertFalse(gem.is(Tags.Items.GEMS_EMERALD), "淡青宝石不应进 forge:gems/emerald");
        helper.assertFalse(gem.is(Tags.Items.GEMS), "淡青宝石不应进任何 forge:gems");
        helper.assertTrue(gem.is(ECDigger.DIAMONDS), "淡青宝石应在 essentialcraft5:diamonds 里");
        helper.assertTrue(gem.is(ECDigger.EMERALDS), "淡青宝石应在 essentialcraft5:emeralds 里");
        helper.assertTrue(new ItemStack(Items.DIAMOND).is(ECDigger.DIAMONDS), "真钻石应在 essentialcraft5:diamonds 里");
        helper.assertTrue(new ItemStack(Items.EMERALD).is(ECDigger.EMERALDS), "真绿宝石应在 essentialcraft5:emeralds 里");
        helper.assertFalse(new ItemStack(Items.EMERALD).is(ECDigger.DIAMONDS), "绿宝石不应算钻石");

        RecipeManager recipes = helper.getLevel().getRecipeManager();
        for (String name : List.of("pale_gem_from_diamond", "pale_gem_from_emerald")) {
            Optional<? extends Recipe<?>> recipe = recipes.byKey(EssentialCraft5.id("radiating_chamber/" + name));
            helper.assertTrue(recipe.isPresent(), "辐射仓配方 " + name + " 应已加载（淡青宝石注册后 item_exists 成立）");
            ItemStack result = recipe.get().getResultItem(helper.getLevel().registryAccess());
            helper.assertTrue(result.is(ECDigger.PALE_GEM.get()) && result.getCount() == 4, name + " 应产出 4 颗淡青宝石，实际 " + result);
            NonNullList<Ingredient> ingredients = recipe.get().getIngredients();
            for (Ingredient ingredient : ingredients) {
                helper.assertFalse(ingredient.test(gem), name + " 的原料不应收淡青宝石（不许自循环）");
            }
        }
        Optional<? extends Recipe<?>> digger = recipes.byKey(EssentialCraft5.id("digger/magical_digger"));
        helper.assertTrue(digger.isPresent(), "挖掘器配方应已加载");
        helper.assertTrue(digger.get().getResultItem(helper.getLevel().registryAccess()).is(ECDigger.MAGICAL_DIGGER.get()), "挖掘器配方应产出挖掘器");
        helper.succeed();
    }

    /**
     * 手册淡青宝石「不收它的地方」页（4b 审查 DIG-2 / INT-3）：水晶成型器的钻石格、往四周是灵魂沙的绿宝石块上丢钻石 / 绿宝石的仪式
     * 写死原版物品（同原作），不收淡青宝石，也不吃掉它；同一座祭坛丢真绿宝石照常触发（对照：祭坛摆对了）
     */
    @GameTest(template = EMPTY)
    public static void paleGemNotTakenByFormerOrRituals(GameTestHelper helper) {
        ItemStack gem = new ItemStack(ECDigger.PALE_GEM.get());
        helper.assertFalse(CrystalFormerBlockEntity.slotRule(CrystalFormerBlockEntity.SLOT_DIAMOND, gem), "水晶成型器的钻石格不应收淡青宝石");
        helper.assertTrue(CrystalFormerBlockEntity.slotRule(CrystalFormerBlockEntity.SLOT_DIAMOND, new ItemStack(Items.DIAMOND)), "水晶成型器的钻石格应收钻石");

        BlockPos altar = new BlockPos(2, 1, 2);
        helper.setBlock(altar, Blocks.EMERALD_BLOCK);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            helper.setBlock(altar.relative(direction), Blocks.SOUL_SAND);
        }
        ServerLevel level = helper.getLevel();
        try {
            // 掉落物停在绿宝石块正上方那格（所在格 (2, 2, 2)），和 BootstrapTests 的仪式测试同一摆法
            ItemEntity pale = helper.spawnItem(ECDigger.PALE_GEM.get(), 2.5F, 2F, 2.5F);
            helper.assertFalse(GroundRituals.check(level, pale, () -> 0D), "淡青宝石丢在绿宝石块上不应触发钻石 / 绿宝石仪式");
            helper.assertTrue(pale.isAlive() && pale.getItem().getCount() == 1, "不触发时淡青宝石不应被吃掉");
            ItemEntity emerald = helper.spawnItem(Items.EMERALD, 2.5F, 2F, 2.5F);
            helper.assertTrue(GroundRituals.check(level, emerald, () -> 0D), "同一座祭坛丢真绿宝石应触发（祭坛摆对了）");
        } finally {
            clearDrops(helper, altar, 3D);
        }
        helper.succeed();
    }
}
