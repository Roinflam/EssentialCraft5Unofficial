package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.item.HomewardCompassItem;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.registry.ECEffects;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHomewardCompass;
import com.flameforge.essentialcraft5.registry.ECItems;
import com.flameforge.essentialcraft5.teleport.HomewardChannels;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * 归途罗盘（第十二批，batch12.md §4.5.8）的 GameTest：
 * <ul>
 *   <li>潜行对方块用：NBT 记下那一格和本维度；不潜行不记；</li>
 *   <li>价格、维度规则（纯函数，配置拆成参数，不改真配置）；</li>
 *   <li>真传送：带一个满的鲜红灵魂物质，归途点在 30 格外，引导完人到了、扣 5 300、冷却 6 000；</li>
 *   <li>引导中走开 3 格、挨 1 点伤害、被挂上锚定都打断，不扣钱、不进冷却；刚被僵尸打过起不了手，下线重进也不清；</li>
 *   <li>归途点上方被堵死：不传、不扣、不冷却；</li>
 *   <li>冷却记在持久数据里，登录、从末地回来补回物品冷却，死亡重生清掉。</li>
 * </ul>
 * 测试玩家是 {@code ProtectionFrameworkTests.realPlayer}（不在世界里、不走真 tick），所以引导用物品自己的方法一步步推：
 * {@code use} 起手 → {@code onUseTick} → {@code finishUsingItem} 后照原版 {@code completeUsingItem} 停止使用。
 * 要挨打的测试先让玩家空跑 61 刻，过掉刚进世界的 60 刻无敌。每条结束都清掉引导登记表里自己的那名玩家。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class HomewardCompassTests {

    private static final String EMPTY = "empty";
    private static final ECHomewardCompass.Config.Values D = ECHomewardCompass.Config.DEFAULTS;
    private static final BlockPos HOME_REL = new BlockPos(2, 1, 2);

    private HomewardCompassTests() {
    }

    /** 潜行对方块用：记下那一格和本维度；不潜行放行、不记；再绑一次换成新的那格 */
    @GameTest(template = EMPTY)
    public static void homewardBindStoresHome(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        floor(helper);
        ServerPlayer player = player(helper, "ec5-hc-bind", new Vec3(0.5D, 2D, 0.5D));
        HomewardCompassItem item = ECHomewardCompass.HOMEWARD_COMPASS.get();
        ItemStack stack = new ItemStack(item);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos clicked = helper.absolutePos(HOME_REL);
        helper.assertTrue(item.onItemUseFirst(stack, click(player, clicked)) == InteractionResult.PASS, "不潜行应放行（照常开箱子、开门）");
        helper.assertFalse(HomewardCompassItem.isBound(stack), "不潜行不记");
        player.setShiftKeyDown(true);
        helper.assertTrue(item.onItemUseFirst(stack, click(player, clicked)) == InteractionResult.CONSUME, "潜行应绑定");
        HomewardCompassItem.Home home = HomewardCompassItem.home(stack);
        helper.assertTrue(home != null && home.pos().equals(clicked) && home.dimension().equals(level.dimension()),
                "应记下点的那一格和本维度，实际 " + home);
        BlockPos other = helper.absolutePos(new BlockPos(3, 1, 3));
        item.onItemUseFirst(stack, click(player, other));
        home = HomewardCompassItem.home(stack);
        helper.assertTrue(home != null && home.pos().equals(other), "随时可以重新绑定");
        helper.succeed();
    }

    /** 同维度 0 格 5 000、1 000 格 15 000、10 000 格封顶 50 000；按水平直线距离、向上取整，高度不算；跨维度 100 000 */
    @GameTest(template = EMPTY)
    public static void homewardCostFormula(GameTestHelper helper) {
        BlockPos o = BlockPos.ZERO;
        helper.assertTrue(HomewardCompassItem.cost(o, o, true, D) == 5000, "0 格 5 000");
        helper.assertTrue(HomewardCompassItem.cost(o, new BlockPos(1000, 0, 0), true, D) == 15000, "1 000 格 15 000");
        helper.assertTrue(HomewardCompassItem.cost(o, new BlockPos(10000, 0, 0), true, D) == 50000, "10 000 格封顶 50 000");
        helper.assertTrue(HomewardCompassItem.cost(o, new BlockPos(600, 0, -800), true, D) == 15000, "斜着 1 000 格同样 15 000");
        helper.assertTrue(HomewardCompassItem.cost(o, new BlockPos(1, 0, 1), true, D) == 5020, "√2 格向上取整成 2 格");
        helper.assertTrue(HomewardCompassItem.cost(o, new BlockPos(0, 200, 0), true, D) == 5000, "高度差不算钱");
        helper.assertTrue(HomewardCompassItem.cost(o, new BlockPos(30, 0, 0), false, D) == 100000, "跨维度一口价 100 000");
        helper.succeed();
    }

    /** 主世界 → 海纳尔不行；海纳尔 → 主世界、海纳尔 → 海纳尔、主世界 → 下界都行；关掉跨维度时只剩同维度 */
    @GameTest(template = EMPTY)
    public static void homewardTargetRule(GameTestHelper helper) {
        helper.assertFalse(HomewardCompassItem.targetAllowed(Level.OVERWORLD, ECHoanna.LEVEL, D), "主世界 → 海纳尔不行");
        helper.assertTrue(HomewardCompassItem.targetAllowed(ECHoanna.LEVEL, Level.OVERWORLD, D), "海纳尔 → 主世界行");
        helper.assertTrue(HomewardCompassItem.targetAllowed(ECHoanna.LEVEL, ECHoanna.LEVEL, D), "海纳尔 → 海纳尔行");
        helper.assertTrue(HomewardCompassItem.targetAllowed(Level.OVERWORLD, Level.NETHER, D), "主世界 → 下界行");
        ECHomewardCompass.Config.Values sameOnly = new ECHomewardCompass.Config.Values(D.enabled(), D.channelTicks(), D.moveTolerance(),
                D.combatLockTicks(), D.cooldownTicks(), D.crossDimensionCooldownTicks(), D.baseMru(), D.mruPerBlock(), D.maxMruSameDimension(),
                D.crossDimensionMru(), false, D.deniedTargetDimensions());
        helper.assertFalse(HomewardCompassItem.targetAllowed(Level.OVERWORLD, Level.NETHER, sameOnly), "关掉跨维度：主世界 → 下界不行");
        helper.assertTrue(HomewardCompassItem.targetAllowed(Level.NETHER, Level.NETHER, sameOnly), "关掉跨维度：同维度照样行");
        ECHomewardCompass.Config.Values shortName = new ECHomewardCompass.Config.Values(D.enabled(), D.channelTicks(), D.moveTolerance(),
                D.combatLockTicks(), D.cooldownTicks(), D.crossDimensionCooldownTicks(), D.baseMru(), D.mruPerBlock(), D.maxMruSameDimension(),
                D.crossDimensionMru(), true, List.of("the_nether"));
        helper.assertFalse(HomewardCompassItem.targetAllowed(Level.OVERWORLD, Level.NETHER, shortName), "拒绝表里写 the_nether 等于 minecraft:the_nether");
        helper.succeed();
    }

    /** 满的鲜红灵魂物质、归途点在 30 格外：起手不扣，引导完人到了归途点顶上、扣 5 300、冷却 6 000（持久数据和物品冷却都有） */
    @GameTest(template = EMPTY)
    public static void homewardTeleportsAndCharges(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        floor(helper);
        BlockPos home = helper.absolutePos(HOME_REL);
        ServerPlayer player = absolutePlayer(helper, "ec5-hc-trip", new Vec3(home.getX() + 30.5D, home.getY() + 1D, home.getZ() + 0.5D));
        ItemStack storage = storage(player);
        ItemStack compass = bound(player, level, home);
        try {
            helper.assertTrue(compass.use(level, player, InteractionHand.MAIN_HAND).getResult() == InteractionResult.CONSUME, "应起手");
            helper.assertTrue(player.isUsingItem() && HomewardChannels.busy(player), "应进入引导");
            helper.assertTrue(MRUStaffItem.storedMRU(storage) == 120000, "起手不扣钱");
            helper.assertTrue(compass.getUseDuration() == D.channelTicks() && D.channelTicks() == 100, "引导 100 刻");
            compass.onUseTick(level, player, 50);
            helper.assertTrue(player.isUsingItem(), "原地不动不打断");
            finish(level, player, compass);
            Vec3 landing = new Vec3(home.getX() + 0.5D, home.getY() + 1D, home.getZ() + 0.5D);
            helper.assertTrue(player.position().distanceToSqr(landing) < 1.0E-4D, "应站到归途点顶上，实际 " + player.position());
            helper.assertTrue(MRUStaffItem.storedMRU(storage) == 120000 - 5300, "应扣 5 300，剩 " + MRUStaffItem.storedMRU(storage));
            helper.assertTrue(HomewardCompassItem.remainingTicks(player) == D.cooldownTicks() && D.cooldownTicks() == 6000,
                    "持久数据里冷却 6 000，实际 " + HomewardCompassItem.remainingTicks(player));
            helper.assertTrue(player.getCooldowns().isOnCooldown(compass.getItem()), "物品冷却也该有");
            helper.assertFalse(HomewardChannels.busy(player), "传完不再引导");
        } finally {
            HomewardChannels.forgetForTest(player);
        }
        helper.succeed();
    }

    /** 引导中走开 1 格不打断；走开 3 格打断：停止使用、不扣钱、不进冷却 */
    @GameTest(template = EMPTY)
    public static void homewardMoveCancels(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        floor(helper);
        ServerPlayer player = player(helper, "ec5-hc-move", new Vec3(0.5D, 2D, 0.5D));
        ItemStack storage = storage(player);
        ItemStack compass = bound(player, level, helper.absolutePos(HOME_REL));
        try {
            helper.assertTrue(compass.use(level, player, InteractionHand.MAIN_HAND).getResult() == InteractionResult.CONSUME, "应起手");
            Vec3 start = player.position();
            player.moveTo(start.x + 1D, start.y, start.z);
            compass.onUseTick(level, player, 90);
            helper.assertTrue(player.isUsingItem() && HomewardChannels.busy(player), "1 格以内不打断（默认 1.5 格）");
            player.moveTo(start.x + 3D, start.y, start.z);
            compass.onUseTick(level, player, 80);
            helper.assertFalse(player.isUsingItem() || HomewardChannels.busy(player), "走开 3 格应打断");
            helper.assertTrue(MRUStaffItem.storedMRU(storage) == 120000, "打断不扣钱");
            helper.assertFalse(HomewardCompassItem.coolingDown(player), "打断不进冷却");
        } finally {
            HomewardChannels.forgetForTest(player);
        }
        helper.succeed();
    }

    /** 引导中挨 1 点伤害：停止使用、不扣钱、不进冷却 */
    @GameTest(template = EMPTY)
    public static void homewardDamageCancels(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        floor(helper);
        ServerPlayer player = hittable(helper, "ec5-hc-hurt");
        ItemStack storage = storage(player);
        ItemStack compass = bound(player, level, helper.absolutePos(HOME_REL));
        try {
            helper.assertTrue(compass.use(level, player, InteractionHand.MAIN_HAND).getResult() == InteractionResult.CONSUME, "应起手");
            helper.assertTrue(player.hurt(player.damageSources().generic(), 1F), "伤害应落下");
            helper.assertFalse(player.isUsingItem() || HomewardChannels.busy(player), "受伤应打断");
            helper.assertTrue(MRUStaffItem.storedMRU(storage) == 120000, "打断不扣钱");
            helper.assertFalse(HomewardCompassItem.coolingDown(player), "打断不进冷却");
        } finally {
            HomewardChannels.forgetForTest(player);
        }
        helper.succeed();
    }

    /** 刚被僵尸打过：起不了手（战斗锁 200 刻），不进入使用状态、不扣钱 */
    @GameTest(template = EMPTY)
    public static void homewardCombatLockRefuses(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        floor(helper);
        ServerPlayer player = hittable(helper, "ec5-hc-lock");
        ItemStack storage = storage(player);
        ItemStack compass = bound(player, level, helper.absolutePos(HOME_REL));
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(4.5D, 2D, 4.5D));
        try {
            helper.assertTrue(player.hurt(player.damageSources().mobAttack(zombie), 2F), "僵尸这一下应落下");
            int left = HomewardChannels.combatLockLeft(player, D.combatLockTicks());
            helper.assertTrue(left > 0 && left <= 200 && D.combatLockTicks() == 200, "战斗锁应在 200 刻以内，实际剩 " + left);
            helper.assertTrue(compass.use(level, player, InteractionHand.MAIN_HAND).getResult() == InteractionResult.FAIL, "刚挨过打应起不了手");
            helper.assertFalse(player.isUsingItem() || HomewardChannels.busy(player), "不该进入引导");
            helper.assertTrue(MRUStaffItem.storedMRU(storage) == 120000, "不扣钱");
            helper.assertTrue(HomewardChannels.combatLockLeft(player, 0) == 0, "战斗锁 0 刻时不锁");
            HomewardChannels.onHomewardLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
            helper.assertTrue(HomewardChannels.combatLockLeft(player, D.combatLockTicks()) > 0, "下线重进不清战斗锁");
        } finally {
            HomewardChannels.forgetForTest(player);
            zombie.discard();
        }
        helper.succeed();
    }

    /** 起手以后才被挂上锚定：到点不传、不扣钱、不进冷却；引导中挂上的当场打断 */
    @GameTest(template = EMPTY)
    public static void homewardAnchoredMidChannel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        floor(helper);
        ServerPlayer player = player(helper, "ec5-hc-anchor", new Vec3(0.5D, 2D, 0.5D));
        ItemStack storage = storage(player);
        ItemStack compass = bound(player, level, helper.absolutePos(HOME_REL));
        try {
            Vec3 start = player.position();
            helper.assertTrue(compass.use(level, player, InteractionHand.MAIN_HAND).getResult() == InteractionResult.CONSUME, "应起手");
            player.addEffect(new MobEffectInstance(ECEffects.ANCHORED.get(), 200));
            HomewardChannels.Outcome outcome = HomewardChannels.finish(player);
            player.stopUsingItem();
            helper.assertTrue(outcome == HomewardChannels.Outcome.FAILED, "到点时身上有锚定应失败，实际 " + outcome);
            helper.assertTrue(player.position().distanceToSqr(start) < 1.0E-6D, "人不该动");
            helper.assertTrue(MRUStaffItem.storedMRU(storage) == 120000, "不扣钱");
            helper.assertFalse(HomewardCompassItem.coolingDown(player), "不进冷却");

            player.removeEffect(ECEffects.ANCHORED.get());
            helper.assertTrue(compass.use(level, player, InteractionHand.MAIN_HAND).getResult() == InteractionResult.CONSUME, "锚定没了又能起手");
            player.addEffect(new MobEffectInstance(ECEffects.ANCHORED.get(), 200));
            compass.onUseTick(level, player, 90);
            helper.assertFalse(player.isUsingItem() || HomewardChannels.busy(player), "引导中挂上锚定应当场打断");
            helper.assertTrue(MRUStaffItem.storedMRU(storage) == 120000, "打断不扣钱");
        } finally {
            HomewardChannels.forgetForTest(player);
        }
        helper.succeed();
    }

    /** 归途点上方被方块堵死：到点判 BLOCKED，不传、不扣钱、不进冷却 */
    @GameTest(template = EMPTY)
    public static void homewardBlockedNoCharge(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        floor(helper);
        helper.setBlock(HOME_REL.above(), Blocks.STONE);
        ServerPlayer player = player(helper, "ec5-hc-block", new Vec3(0.5D, 2D, 0.5D));
        ItemStack storage = storage(player);
        ItemStack compass = bound(player, level, helper.absolutePos(HOME_REL));
        try {
            Vec3 start = player.position();
            helper.assertTrue(compass.use(level, player, InteractionHand.MAIN_HAND).getResult() == InteractionResult.CONSUME, "起手不看落点");
            HomewardChannels.Outcome outcome = HomewardChannels.finish(player);
            player.stopUsingItem();
            helper.assertTrue(outcome == HomewardChannels.Outcome.FAILED, "被堵死应失败，实际 " + outcome);
            helper.assertTrue(player.position().distanceToSqr(start) < 1.0E-6D, "人不该动");
            helper.assertTrue(MRUStaffItem.storedMRU(storage) == 120000, "不扣钱");
            helper.assertFalse(HomewardCompassItem.coolingDown(player), "不进冷却");
            helper.assertFalse(HomewardChannels.busy(player) || HomewardChannels.waitingForTest(player), "不留在引导表、重试表里");
        } finally {
            HomewardChannels.forgetForTest(player);
        }
        helper.succeed();
    }

    /** 冷却写进持久数据：物品冷却没了照样算冷却中；登录、从末地回来补回物品冷却；死亡重生清掉；到期后登录删掉记录 */
    @GameTest(template = EMPTY)
    public static void homewardCooldownPersists(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        floor(helper);
        ServerPlayer player = player(helper, "ec5-hc-cd", new Vec3(0.5D, 2D, 0.5D));
        storage(player);
        ItemStack compass = bound(player, level, helper.absolutePos(HOME_REL));
        HomewardCompassItem item = ECHomewardCompass.HOMEWARD_COMPASS.get();
        try {
            HomewardCompassItem.startCooldown(player, D.cooldownTicks());
            helper.assertTrue(player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).contains(HomewardCompassItem.READY_KEY),
                    "持久数据里应有到期刻");
            helper.assertTrue(HomewardCompassItem.remainingTicks(player) == D.cooldownTicks(), "剩 6 000 刻");
            helper.assertTrue(player.getCooldowns().isOnCooldown(item), "物品冷却也该有");

            player.getCooldowns().removeCooldown(item);   // 物品冷却不存盘：重进以后就没了
            helper.assertTrue(HomewardCompassItem.coolingDown(player), "物品冷却没了、持久数据还没到期，仍算冷却中");
            helper.assertTrue(compass.use(level, player, InteractionHand.MAIN_HAND).getResult() == InteractionResult.FAIL, "冷却中起不了手");
            helper.assertFalse(player.isUsingItem(), "冷却中不进入引导");

            HomewardChannels.onHomewardLogin(new PlayerEvent.PlayerLoggedInEvent(player));
            helper.assertTrue(player.getCooldowns().isOnCooldown(item), "登录时补回物品冷却");
            player.getCooldowns().removeCooldown(item);
            HomewardChannels.onHomewardRespawn(new PlayerEvent.PlayerRespawnEvent(player, true));
            helper.assertTrue(player.getCooldowns().isOnCooldown(item), "从末地回来也补回物品冷却");

            player.getCooldowns().removeCooldown(item);
            HomewardChannels.onHomewardRespawn(new PlayerEvent.PlayerRespawnEvent(player, false));
            helper.assertFalse(HomewardCompassItem.coolingDown(player), "死亡重生清掉冷却");

            HomewardCompassItem.setReadyAt(player, level.getGameTime() - 1L);
            HomewardChannels.onHomewardLogin(new PlayerEvent.PlayerLoggedInEvent(player));
            helper.assertFalse(player.getCooldowns().isOnCooldown(item)
                            || player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).contains(HomewardCompassItem.READY_KEY),
                    "到期以后登录不补冷却、删掉过期的记录");
            helper.assertTrue(compass.use(level, player, InteractionHand.MAIN_HAND).getResult() == InteractionResult.CONSUME, "到期以后又能起手");
        } finally {
            HomewardChannels.forgetForTest(player);
        }
        helper.succeed();
    }

    // ================================================================== 工具

    /** y = 1 铺一层石头（5 × 5），归途点在 (2, 1, 2) */
    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
    }

    private static ServerPlayer player(GameTestHelper helper, String name, Vec3 relativeFeet) {
        return absolutePlayer(helper, name, helper.absoluteVec(relativeFeet));
    }

    private static ServerPlayer absolutePlayer(GameTestHelper helper, String name, Vec3 feet) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        player.setGameMode(GameType.SURVIVAL);
        player.moveTo(feet.x, feet.y, feet.z, 0F, 0F);
        return player;
    }

    /** 过掉刚进世界的 60 刻无敌（ServerPlayer.tick 里倒数），再站到 (0.5, 2, 0.5) */
    private static ServerPlayer hittable(GameTestHelper helper, String name) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        player.setGameMode(GameType.SURVIVAL);
        for (int i = 0; i <= 60; i++) {
            player.tick();
        }
        Vec3 at = helper.absoluteVec(new Vec3(0.5D, 2D, 0.5D));
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    /** 背包第 2 格放一个满的鲜红灵魂物质（12 万 MRU），返回它 */
    private static ItemStack storage(ServerPlayer player) {
        ItemStack storage = MRUStaffItem.full(ECItems.RED_SOUL_MATTER.get());
        player.getInventory().setItem(1, storage);
        return storage;
    }

    /** 主手拿一个绑在 home 的罗盘 */
    private static ItemStack bound(ServerPlayer player, ServerLevel level, BlockPos home) {
        ItemStack compass = new ItemStack(ECHomewardCompass.HOMEWARD_COMPASS.get());
        HomewardCompassItem.setHome(compass, level.dimension(), home);
        player.setItemInHand(InteractionHand.MAIN_HAND, compass);
        return compass;
    }

    /** 点 pos 的顶面 */
    private static UseOnContext click(ServerPlayer player, BlockPos pos) {
        return new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos).add(0D, 0.5D, 0D), Direction.UP, pos, false));
    }

    /** 引导满了：照原版 completeUsingItem，先 finishUsingItem 再停止使用 */
    private static void finish(ServerLevel level, ServerPlayer player, ItemStack compass) {
        compass.finishUsingItem(level, player);
        player.stopUsingItem();
    }
}
