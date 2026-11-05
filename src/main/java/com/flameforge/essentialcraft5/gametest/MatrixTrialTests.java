package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.blockentity.MatrixCrucibleBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MithrilineCrystalPillarBlockEntity;
import com.flameforge.essentialcraft5.blockentity.PlayerPentacleBlockEntity;
import com.flameforge.essentialcraft5.entity.MatrixEchoEntity;
import com.flameforge.essentialcraft5.item.SoulStoneItem;
import com.flameforge.essentialcraft5.matrix.MatrixRank;
import com.flameforge.essentialcraft5.matrix.MatrixRankData;
import com.flameforge.essentialcraft5.matrix.MatrixTrialChecks;
import com.flameforge.essentialcraft5.matrix.MatrixTrialConfig;
import com.flameforge.essentialcraft5.matrix.MatrixTrials;
import com.flameforge.essentialcraft5.menu.MatrixCrucibleMenu;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECBootstrap;
import com.flameforge.essentialcraft5.registry.ECMatrix;
import com.flameforge.essentialcraft5.registry.ECMithriline;
import com.flameforge.essentialcraft5.registry.ECPentacle;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 矩阵淬炼台与试炼（第十三批 13a，batch13.md §3.1.2、§12.2 的「淬炼」那几条、自检 C60 / C75）。
 * <p>
 * 九阶数据换成测试表（{@link #entries()}），整批共用一套覆盖值，所以放在自己的批次里（{@value #BATCH}，引导的另一批 {@value #CHANNEL_BATCH}；
 * 中途要改全局覆盖值的各自单独成批：{@value #SWITCH_BATCH} 中途改和平、{@value #LEAVE_BATCH} 离开半径填 2）：
 * 一阶要 2 泥土 + 1 木棍、100 UBMRU、50 MRU，回响 2 只（同时 1 只）、生命 4、伤害 0、限时 60 秒；二阶限时 1 秒（测超时）；
 * 三阶要 0 档五芒星和 100 ESPE；四阶要 1 档五芒星；五阶要 20 万 MRU（比台子容量大）；六阶 100 UBMRU、50 MRU、限时 12 秒（比中止要等的
 * {@value MatrixTrials#STALL_TICKS} 刻长，测堵住生成点不退）。回响在台子 2~3 格内生成、不带 AI（测试玩家不在世界实体表里），离开半径 6；
 * 引导那批的时限只多给 1 秒（要站的时间 × 2 + 1 秒）。
 * 测试玩家不在世界玩家表里、不 tick，试炼照常由服务端刻推进。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class MatrixTrialTests {

    static final String BATCH = "ec5_matrix_trial";
    static final String CHANNEL_BATCH = "ec5_matrix_trial_channel";
    static final String SWITCH_BATCH = "ec5_matrix_trial_switch";
    static final String LEAVE_BATCH = "ec5_matrix_trial_leave";
    private static final String LARGE = "empty_large";
    private static final BlockPos CRUCIBLE = new BlockPos(5, 1, 5);
    /** 站点测试：台子放在角上，五芒星在中间 */
    private static final BlockPos SITE_CRUCIBLE = new BlockPos(1, 1, 1);
    private static final BlockPos PENTACLE = new BlockPos(5, 2, 5);
    private static final int UBMRU_NEED = 100;
    private static final int MRU_NEED = 50;
    private static final int ESPE_NEED = 100;
    private static final int LEAVE = 6;
    /** 五阶要的 MRU：比台子默认容量（10 万）大 */
    private static final int BIG_MRU = 200_000;
    /** 六阶的时限：比中止要等的刻数长一点 */
    private static final int TERRAIN_SECONDS = 12;

    private MatrixTrialTests() {
    }

    // ================================================================ 批次

    @BeforeBatch(batch = BATCH)
    public static void matrixTrialBefore(ServerLevel level) {
        overrides(false);
    }

    @AfterBatch(batch = BATCH)
    public static void matrixTrialAfter(ServerLevel level) {
        clearOverrides();
    }

    @BeforeBatch(batch = CHANNEL_BATCH)
    public static void matrixTrialChannelBefore(ServerLevel level) {
        overrides(true);
        MatrixTrials.setTestChannelSlack(1);
    }

    @AfterBatch(batch = CHANNEL_BATCH)
    public static void matrixTrialChannelAfter(ServerLevel level) {
        clearOverrides();
    }

    @BeforeBatch(batch = SWITCH_BATCH)
    public static void matrixTrialSwitchBefore(ServerLevel level) {
        overrides(false);
    }

    @AfterBatch(batch = SWITCH_BATCH)
    public static void matrixTrialSwitchAfter(ServerLevel level) {
        clearOverrides();
    }

    @BeforeBatch(batch = LEAVE_BATCH)
    public static void matrixTrialLeaveBefore(ServerLevel level) {
        overrides(false);
        MatrixTrials.setTestOverrides(new int[]{2, 3}, 2, null, false, true);
    }

    @AfterBatch(batch = LEAVE_BATCH)
    public static void matrixTrialLeaveAfter(ServerLevel level) {
        clearOverrides();
    }

    private static void overrides(boolean channel) {
        MatrixTrials.clearAllForTest();
        MatrixRankData.setTestOverride(entries());
        MatrixRank.setTestOverrides(true, null, null, null);
        MatrixRank.setTestPeaceful(channel);
        MatrixTrials.setTestOverrides(new int[]{2, 3}, LEAVE, channel ? 20 : null, channel, true);
    }

    private static void clearOverrides() {
        MatrixTrials.clearAllForTest();
        MatrixTrials.clearTestOverrides();
        MatrixRankData.setTestOverride(null);
        MatrixRank.clearTestOverrides();
    }

    private static Map<Integer, MatrixRankData.Entry> entries() {
        MatrixRankData.Codex none = MatrixRankData.Codex.NONE;
        List<MatrixRankData.Offering> offerings = List.of(new MatrixRankData.Offering(Ingredient.of(Items.DIRT), 2),
                new MatrixRankData.Offering(Ingredient.of(Items.STICK), 1));
        return Map.of(
                1, new MatrixRankData.Entry(1, none, false, -1, offerings, UBMRU_NEED, MRU_NEED, 0, new MatrixRankData.Echoes(2, 1, 4, 0, 60)),
                2, new MatrixRankData.Entry(2, none, false, -1, List.of(), 0, 0, 0, new MatrixRankData.Echoes(1, 1, 4, 0, 1)),
                3, new MatrixRankData.Entry(3, none, false, 0, List.of(), 0, 0, ESPE_NEED, new MatrixRankData.Echoes(1, 1, 4, 0, 60)),
                4, new MatrixRankData.Entry(4, none, false, 1, List.of(), 0, 0, 0, new MatrixRankData.Echoes(1, 1, 4, 0, 60)),
                5, new MatrixRankData.Entry(5, none, false, -1, List.of(), 0, BIG_MRU, 0, new MatrixRankData.Echoes(1, 1, 4, 0, 60)),
                6, new MatrixRankData.Entry(6, none, false, -1, List.of(), UBMRU_NEED, MRU_NEED, 0,
                        new MatrixRankData.Echoes(2, 1, 4, 0, TERRAIN_SECONDS)));
    }

    // ================================================================ 工具

    private record Rig(ServerPlayer player, MatrixCrucibleBlockEntity crucible, IPlayerData data) {
    }

    private static Rig rig(GameTestHelper helper, int rank) {
        return rig(helper, rank, CRUCIBLE, new Vec3(5.5D, 1D, 7.5D));
    }

    /** 放一台淬炼台、一名拿着自己灵魂石的测试玩家（站在台子 2 格外），阶 = rank，身上 1000 UBMRU，台子 1000 MRU；零阶时放好一阶的祭品 */
    private static Rig rig(GameTestHelper helper, int rank, BlockPos at, Vec3 stand) {
        helper.setBlock(at, ECMatrix.MATRIX_CRUCIBLE.get());
        MatrixCrucibleBlockEntity crucible = (MatrixCrucibleBlockEntity) helper.getBlockEntity(at);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-trial");
        Vec3 abs = helper.absoluteVec(stand);
        player.moveTo(abs.x, abs.y, abs.z, 180F, 0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, stone(player.getUUID()));
        IPlayerData data = data(player);
        data.setMatrixRank(rank);
        data.setUBMRU(1000);
        data.setMatrixTrialReserve(0);
        data.setMatrixTrialCooldown(0);
        data.setMatrixDamage(0);
        crucible.getMRUStorage().setMRU(1000);
        if (rank == 0) {
            crucible.getInventory().setStackInSlot(1, new ItemStack(Items.DIRT, 2));
            crucible.getInventory().setStackInSlot(2, new ItemStack(Items.STICK, 1));
        }
        return new Rig(player, crucible, data);
    }

    private static ItemStack stone(UUID owner) {
        ItemStack stone = new ItemStack(ECBootstrap.SOUL_STONE.get());
        SoulStoneItem.bind(stone, owner, "ec5-trial");
        return stone;
    }

    private static IPlayerData data(ServerPlayer player) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            throw new IllegalStateException("测试玩家应挂着玩家数据");
        }
        return data;
    }

    private static MatrixTrials.Trial start(GameTestHelper helper, Rig rig) {
        MatrixTrials.Start start = MatrixTrials.tryStart(rig.player(), rig.crucible());
        helper.assertTrue(start.started(), "试炼应开始，实际 " + start.result() + " / " + start.blocker());
        MatrixTrials.Trial trial = MatrixTrials.trialOf(rig.player().getUUID());
        if (trial == null) {
            throw new IllegalStateException("开始了却查不到试炼");
        }
        return trial;
    }

    /** 以试炼者的身份把场上的回响都打倒 */
    private static void killEchoes(ServerPlayer player) {
        MatrixTrials.Trial trial = MatrixTrials.trialOf(player.getUUID());
        if (trial != null) {
            for (MatrixEchoEntity echo : trial.echoes()) {
                echo.hurt(player.damageSources().playerAttack(player), 1000F);
            }
        }
    }

    private static int cooldownTicks() {
        return MatrixTrialConfig.failCooldownSeconds() * 20;
    }

    /** 整个测试结构（绝对坐标），外扩 3 格 */
    private static BoundingBox area(GameTestHelper helper) {
        BlockPos min = helper.absolutePos(new BlockPos(-3, 0, -3));
        BlockPos max = helper.absolutePos(new BlockPos(13, 7, 13));
        return BoundingBox.fromCorners(min, max);
    }

    /** 搭 0 档灵魂五芒星（照 PentacleTests），柱子灌满 1 万 ESPE，主人记成 owner */
    private static PlayerPentacleBlockEntity pentacle(GameTestHelper helper, UUID owner) {
        for (BlockPos offset : ECPentacle.TIER_PATTERNS.get(0).offsets(ECPentacle.PLATING_RULE)) {
            helper.setBlock(PENTACLE.offset(offset), ECMithriline.MITHRILINE_PLATING.get());
        }
        for (BlockPos offset : ECPentacle.PILLAR_OFFSETS.get(0)) {
            helper.setBlock(PENTACLE.offset(offset), ECMithriline.MITHRILINE_CRYSTAL_PILLAR.get());
        }
        helper.setBlock(PENTACLE, ECPentacle.PLAYER_PENTACLE.get());
        PlayerPentacleBlockEntity be = (PlayerPentacleBlockEntity) helper.getBlockEntity(PENTACLE);
        be.setOwner(owner, "ec5-owner");
        for (BlockPos offset : ECPentacle.PILLAR_OFFSETS.get(0)) {
            ((MithrilineCrystalPillarBlockEntity) helper.getBlockEntity(PENTACLE.offset(offset))).getESPEStorage().setESPE(10000D);
        }
        helper.assertTrue(be.recheckTier() == 0, "0 档五芒星应成形，实际 " + be.getTier());
        return be;
    }

    // ================================================================ 台子

    /** 祭品要亲手放：漏斗、管道一格都插不进，也取不出 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixCrucibleRejectsAutomation(GameTestHelper helper) {
        Rig rig = rig(helper, 0);
        IItemHandler handler = rig.crucible().getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElse(null);
        if (handler != null) {
            ItemStack rest = handler.insertItem(3, new ItemStack(Items.DIRT, 4), false);
            helper.assertTrue(rest.getCount() == 4 && rig.crucible().getInventory().getStackInSlot(3).isEmpty(), "自动化不应插进祭品格");
            helper.assertTrue(handler.extractItem(1, 2, false).isEmpty() && rig.crucible().getInventory().getStackInSlot(1).getCount() == 2,
                    "自动化不应取走祭品");
        }
        helper.assertTrue(MatrixCrucibleBlockEntity.slotRule(1, new ItemStack(Items.DIAMOND)), "手放什么都行");
        helper.succeed();
    }

    /** 合成表：元素晶石 ×4、黑曜石 ×2、灵魂沙 ×2、MRU 链接器 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixCrucibleRecipe(GameTestHelper helper) {
        Optional<? extends Recipe<?>> recipe = helper.getLevel().getRecipeManager().byKey(EssentialCraft5.id("matrix/matrix_crucible"));
        helper.assertTrue(recipe.isPresent(), "淬炼台应有合成表");
        ItemStack result = recipe.get().getResultItem(helper.getLevel().registryAccess());
        helper.assertTrue(result.is(ECMatrix.MATRIX_CRUCIBLE.get().asItem()), "合成结果应是淬炼台");
        helper.assertTrue(recipe.get().getIngredients().stream().anyMatch(i -> i.test(new ItemStack(Items.SOUL_SAND))), "用到灵魂沙");
        helper.succeed();
    }

    /** 条件全满足时界面和服务端一致地说「能开始」 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixTrialReadyStatus(GameTestHelper helper) {
        Rig rig = rig(helper, 0);
        MatrixTrialChecks.Report report = MatrixTrialChecks.evaluate(rig.player(), rig.crucible());
        helper.assertTrue(report.ready(), "条件应全满足，实际挡住 " + report.blockers());
        CompoundTag tag = MatrixTrialChecks.statusTag(rig.player(), rig.crucible());
        helper.assertTrue(tag.getBoolean("ready") && tag.getList("blockers", 8).isEmpty() && tag.getBoolean("offerings_ok")
                && tag.getInt("ubmru_need") == UBMRU_NEED && tag.getInt("mru_need") == MRU_NEED
                && "not_needed".equals(tag.getCompound("site").getString("state")), "状态包应全打勾：" + tag);
        helper.succeed();
    }

    /** 缺一样都开不了，什么都不扣、不锁 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixTrialBlockedPaysNothing(GameTestHelper helper) {
        Rig rig = rig(helper, 0);
        ServerPlayer player = rig.player();
        Vec3 home = player.position();
        expectBlocked(helper, rig, MatrixTrialChecks.Blocker.SOUL_STONE, () -> player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY),
                () -> player.setItemInHand(InteractionHand.MAIN_HAND, stone(player.getUUID())));
        expectBlocked(helper, rig, MatrixTrialChecks.Blocker.SOUL_STONE, () -> player.setItemInHand(InteractionHand.MAIN_HAND, stone(UUID.randomUUID())),
                () -> player.setItemInHand(InteractionHand.MAIN_HAND, stone(player.getUUID())));
        expectBlocked(helper, rig, MatrixTrialChecks.Blocker.TOO_FAR, () -> player.moveTo(home.x, home.y, home.z + 3D),
                () -> player.moveTo(home.x, home.y, home.z));
        expectBlocked(helper, rig, MatrixTrialChecks.Blocker.HEALTH, () -> player.setHealth(player.getMaxHealth() * 0.4F),
                () -> player.setHealth(player.getMaxHealth()));
        expectBlocked(helper, rig, MatrixTrialChecks.Blocker.OFFERINGS, () -> rig.crucible().getInventory().setStackInSlot(2, ItemStack.EMPTY),
                () -> rig.crucible().getInventory().setStackInSlot(2, new ItemStack(Items.STICK)));
        expectBlocked(helper, rig, MatrixTrialChecks.Blocker.UBMRU, () -> rig.data().setUBMRU(UBMRU_NEED - 1),
                () -> rig.data().setUBMRU(1000));
        expectBlocked(helper, rig, MatrixTrialChecks.Blocker.MRU, () -> rig.crucible().getMRUStorage().setMRU(MRU_NEED - 1),
                () -> rig.crucible().getMRUStorage().setMRU(1000));
        expectBlocked(helper, rig, MatrixTrialChecks.Blocker.COOLDOWN, () -> rig.data().setMatrixTrialCooldown(100),
                () -> rig.data().setMatrixTrialCooldown(0));
        try {
            expectBlocked(helper, rig, MatrixTrialChecks.Blocker.DISABLED, () -> MatrixRank.setTestOverrides(false, null, null, null), () -> {
            });
        } finally {
            MatrixRank.setTestOverrides(true, null, null, null);
        }
        helper.succeed();
    }

    private static void expectBlocked(GameTestHelper helper, Rig rig, MatrixTrialChecks.Blocker expected, Runnable breakIt, Runnable restore) {
        breakIt.run();
        int ubmru = rig.data().getUBMRU();
        int mru = rig.crucible().getMRUStorage().getMRU();
        MatrixTrials.Start start = MatrixTrials.tryStart(rig.player(), rig.crucible());
        helper.assertTrue(start.result() == MatrixTrials.StartResult.BLOCKED && start.blocker() == expected,
                "应被 " + expected + " 挡住，实际 " + start.result() + " / " + start.blocker());
        helper.assertTrue(rig.data().getUBMRU() == ubmru && rig.data().getMatrixTrialReserve() == 0 && rig.crucible().getMRUStorage().getMRU() == mru
                && !rig.crucible().isLocked() && MatrixTrials.trialOf(rig.player().getUUID()) == null, expected + "：不该扣东西、不该锁");
        restore.run();
    }

    /** 开始：先扣试炼储备再扣身上，MRU 从台子扣；祭品锁格；首领血条只给试炼者；别人用不了这台、自己开不了第二场 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixTrialStartPaysAndLocks(GameTestHelper helper) {
        Rig rig = rig(helper, 0);
        rig.data().setMatrixTrialReserve(60);
        MatrixCrucibleMenu menu = new MatrixCrucibleMenu(0, rig.player().getInventory(), rig.crucible());
        MatrixTrials.Trial trial = start(helper, rig);
        try {
            helper.assertTrue(rig.data().getMatrixTrialReserve() == 0 && rig.data().getUBMRU() == 1000 - (UBMRU_NEED - 60),
                    "应先扣储备 60、再扣身上 40，实际储备 " + rig.data().getMatrixTrialReserve() + "、身上 " + rig.data().getUBMRU());
            helper.assertTrue(rig.crucible().getMRUStorage().getMRU() == 1000 - MRU_NEED, "台子应扣 " + MRU_NEED + " MRU");
            helper.assertTrue(rig.player().getUUID().equals(rig.crucible().lockedBy()), "台子应锁给试炼者");
            helper.assertTrue(rig.crucible().getInventory().getStackInSlot(1).getCount() == 2, "祭品开始时不消耗");
            helper.assertTrue(!menu.slots.get(1).mayPickup(rig.player()) && !menu.slots.get(3).mayPlace(new ItemStack(Items.DIRT)),
                    "锁住的祭品格拿不走、放不进");
            // 真走一遍点击：Shift 点、数字键交换、双击收集同类、丢出，都动不了锁住的格子
            ServerPlayer trialist = rig.player();
            menu.clicked(1, 0, ClickType.QUICK_MOVE, trialist);
            menu.clicked(1, 0, ClickType.SWAP, trialist);
            menu.clicked(2, 0, ClickType.THROW, trialist);
            menu.setCarried(new ItemStack(Items.DIRT, 1));
            menu.clicked(5, 0, ClickType.PICKUP_ALL, trialist);
            int carried = menu.getCarried().getCount();
            menu.setCarried(ItemStack.EMPTY);
            helper.assertTrue(rig.crucible().getInventory().getStackInSlot(1).getCount() == 2 && rig.crucible().getInventory().getStackInSlot(2).getCount() == 1
                    && carried == 1, "点击操作不该从锁住的格子里拿走东西：泥土 " + rig.crucible().getInventory().getStackInSlot(1).getCount()
                    + "、木棍 " + rig.crucible().getInventory().getStackInSlot(2).getCount() + "、收集到 " + carried);
            helper.assertTrue(!trialist.getInventory().contains(new ItemStack(Items.DIRT)) && !trialist.getInventory().contains(new ItemStack(Items.STICK))
                    && trialist.getMainHandItem().getItem() instanceof SoulStoneItem, "背包里不该多出祭品，主手还是灵魂石");
            helper.assertTrue(trial.echoes().size() == 1 && trial.echoes().get(0).isAlive(), "开始时放出第一只回响");
            helper.assertTrue(trial.barViewers().size() == 1 && trial.barViewers().contains(rig.player()), "首领血条只给试炼者");
            MatrixTrials.Start again = MatrixTrials.tryStart(rig.player(), rig.crucible());
            helper.assertTrue(again.blocker() == MatrixTrialChecks.Blocker.RUNNING, "进行中再点应挡住，实际 " + again.blocker());
            ServerPlayer other = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-other");
            other.moveTo(rig.player().getX(), rig.player().getY(), rig.player().getZ());
            other.setItemInHand(InteractionHand.MAIN_HAND, stone(other.getUUID()));
            data(other).setUBMRU(1000);
            helper.assertTrue(MatrixTrialChecks.evaluate(other, rig.crucible()).blockers().contains(MatrixTrialChecks.Blocker.BUSY),
                    "别人在用的台子应挡住");
        } finally {
            MatrixTrials.clearForTest(rig.player().getUUID());
        }
        helper.assertTrue(!rig.crucible().isLocked() && menu.slots.get(1).mayPickup(rig.player()), "结束后解锁");
        helper.succeed();
    }

    // ================================================================ 成功与失败

    /** 打完回响：升一阶、按位置消耗祭品、矩阵损伤按新阶打折加上、解锁、不冷却 */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 200)
    public static void matrixTrialSuccess(GameTestHelper helper) {
        Rig rig = rig(helper, 0);
        start(helper, rig);
        long expected = MatrixRank.scaleNewDamage(1, (long) MatrixTrialConfig.successMatrixDamagePerRank());
        helper.succeedWhen(() -> {
            killEchoes(rig.player());
            helper.assertTrue(rig.data().getMatrixRank() == 1, "还没升阶");
            helper.assertTrue(MatrixTrials.trialOf(rig.player().getUUID()) == null && !rig.crucible().isLocked(), "成功后结束并解锁");
            helper.assertTrue(rig.crucible().getInventory().getStackInSlot(1).isEmpty() && rig.crucible().getInventory().getStackInSlot(2).isEmpty(),
                    "祭品应消耗");
            helper.assertTrue(rig.data().getMatrixDamage() == expected, "矩阵损伤应加 " + expected + "，实际 " + rig.data().getMatrixDamage());
            helper.assertTrue(rig.data().getMatrixTrialCooldown() == 0, "成功不冷却");
        });
    }

    /** 走出离开半径：失败、冷却、能量不退、祭品留着 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixTrialFailLeft(GameTestHelper helper) {
        Rig rig = rig(helper, 0);
        MatrixTrials.Trial trial = start(helper, rig);
        MatrixEchoEntity echo = trial.echoes().get(0);
        Vec3 far = helper.absoluteVec(new Vec3(5.5D, 1D, 5.5D + LEAVE + 2));
        rig.player().moveTo(far.x, far.y, far.z);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(MatrixTrials.trialOf(rig.player().getUUID()) == null, "走远应失败");
            assertFailed(helper, rig, "走远");
            helper.assertTrue(echo.isRemoved(), "失败后回响收回");
            helper.succeed();
        });
    }

    /** 超时：失败、冷却 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixTrialFailTimeout(GameTestHelper helper) {
        Rig rig = rig(helper, 1);
        start(helper, rig);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(MatrixTrials.trialOf(rig.player().getUUID()) == null, "1 秒的试炼 30 刻后应超时");
            helper.assertTrue(rig.data().getMatrixTrialCooldown() == cooldownTicks() && rig.data().getMatrixRank() == 1, "超时应冷却、不升阶");
            helper.succeed();
        });
    }

    /** 死亡、下线、换维度都算失败（走事件入口） */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixTrialFailDeathLogoutDimension(GameTestHelper helper) {
        Rig rig = rig(helper, 0);
        ServerPlayer player = rig.player();
        rig.data().setUBMRU(10000);
        start(helper, rig);
        MatrixTrials.MatrixTrialEvents.matrixTrialPlayerDeath(new LivingDeathEvent(player, player.damageSources().generic()));
        assertFailed(helper, rig, "死亡");
        rig.data().setMatrixTrialCooldown(0);
        start(helper, rig);
        MatrixTrials.MatrixTrialEvents.matrixTrialLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
        assertFailed(helper, rig, "下线");
        rig.data().setMatrixTrialCooldown(0);
        start(helper, rig);
        MatrixTrials.MatrixTrialEvents.matrixTrialChangedDimension(new PlayerEvent.PlayerChangedDimensionEvent(player, Level.OVERWORLD, Level.NETHER));
        assertFailed(helper, rig, "换维度");
        helper.assertTrue(rig.data().getUBMRU() == 10000 - 3 * UBMRU_NEED && rig.crucible().getMRUStorage().getMRU() == 1000 - 3 * MRU_NEED,
                "三次都扣了能量、都不退");
        helper.succeed();
    }

    /** 台子被挖掉：失败 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixTrialFailCrucibleGone(GameTestHelper helper) {
        Rig rig = rig(helper, 0);
        start(helper, rig);
        helper.setBlock(CRUCIBLE, Blocks.AIR);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(MatrixTrials.trialOf(rig.player().getUUID()) == null, "台子没了应失败");
            helper.assertTrue(rig.data().getMatrixTrialCooldown() == cooldownTicks() && rig.data().getMatrixRank() == 0, "失败冷却、不升阶");
            int dirt = 0;
            int stick = 0;
            for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(CRUCIBLE)).inflate(3D))) {
                if (item.getItem().is(Items.DIRT)) {
                    dirt += item.getItem().getCount();
                } else if (item.getItem().is(Items.STICK)) {
                    stick += item.getItem().getCount();
                }
            }
            helper.assertTrue(dirt == 2 && stick == 1, "祭品应正好掉一份：泥土 " + dirt + "、木棍 " + stick);
            helper.assertTrue(MatrixTrials.failMessageKey(MatrixTrials.Failure.CRUCIBLE, 180).endsWith("failed_crucible"),
                    "台子没了的失败提示不该说祭品还在台子里");
            helper.succeed();
        });
    }

    private static void assertFailed(GameTestHelper helper, Rig rig, String what) {
        helper.assertTrue(MatrixTrials.trialOf(rig.player().getUUID()) == null, what + "：试炼应结束");
        helper.assertTrue(rig.data().getMatrixTrialCooldown() == cooldownTicks(), what + "：应冷却 " + cooldownTicks() + " 刻，实际 "
                + rig.data().getMatrixTrialCooldown());
        helper.assertTrue(rig.data().getMatrixRank() == 0, what + "：不升阶");
        helper.assertTrue(rig.crucible().getInventory().getStackInSlot(1).getCount() == 2 && rig.crucible().getInventory().getStackInSlot(2).getCount() == 1,
                what + "：祭品留在格子里");
        helper.assertTrue(!rig.crucible().isLocked(), what + "：解锁");
    }

    // ================================================================ 回响

    /** 只有试炼者打得动：别人、摔、火、魔法都无效；/kill 照常但不算打倒；只盯试炼者 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixEchoOnlyTrialistHurts(GameTestHelper helper) {
        Rig rig = rig(helper, 0);
        ServerPlayer player = rig.player();
        MatrixTrials.Trial trial = start(helper, rig);
        try {
            MatrixEchoEntity echo = trial.echoes().get(0);
            ServerPlayer other = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-other");
            float full = echo.getHealth();
            helper.assertTrue(!echo.hurt(other.damageSources().playerAttack(other), 2F) && !echo.hurt(echo.damageSources().fall(), 2F)
                    && !echo.hurt(echo.damageSources().inFire(), 2F) && !echo.hurt(echo.damageSources().magic(), 2F)
                    && echo.getHealth() == full, "别人和环境的伤害都应无效");
            helper.assertTrue(echo.hurt(player.damageSources().playerAttack(player), 1F) && echo.getHealth() < full, "试炼者打得动");
            echo.setTarget(other);
            helper.assertTrue(echo.getTarget() == player, "目标只能是试炼者，实际 " + echo.getTarget());
            helper.assertTrue(!echo.doHurtTarget(other), "不打别人");
            echo.hurt(echo.damageSources().genericKill(), 1000F);
            helper.assertTrue(echo.isRemoved() && trial.killed() == 0, "/kill 能清掉，但不算打倒");
        } finally {
            MatrixTrials.clearForTest(player.getUUID());
        }
        helper.succeed();
    }

    /** 打倒回响什么都不给：不掉东西、不给经验和 UBMRU、不进图谱 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixEchoDeathGivesNothing(GameTestHelper helper) {
        Rig rig = rig(helper, 0);
        ServerPlayer player = rig.player();
        MatrixTrials.Trial trial = start(helper, rig);
        try {
            int ubmru = rig.data().getUBMRU();
            int codex = rig.data().getMatrixCodex().size();
            MatrixEchoEntity echo = trial.echoes().get(0);
            echo.hurt(player.damageSources().playerAttack(player), 1000F);
            helper.assertTrue(echo.isRemoved() && trial.killed() == 1, "试炼者打倒的算一只");
            // 只数本测试结构里的（同批别的测试挖台子会掉祭品）
            AABB box = new AABB(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(11, 8, 11)));
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, box).isEmpty()
                    && helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, box).isEmpty(), "不掉东西、不给经验");
            helper.assertTrue(rig.data().getUBMRU() == ubmru && rig.data().getMatrixCodex().size() == codex
                    && !rig.data().getMatrixCodex().contains(EssentialCraft5.id("matrix_echo").toString()), "不给 UBMRU、不进图谱");
        } finally {
            MatrixTrials.clearForTest(player.getUUID());
        }
        helper.succeed();
    }

    /** 不属于任何试炼的回响自己消失；不存盘、不能 /summon */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixEchoWithoutTrialVanishes(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-trial");
        MatrixEchoEntity echo = MatrixEchoEntity.create(helper.getLevel(), player, 2, 10, 1, helper.absolutePos(CRUCIBLE),
                helper.absoluteVec(new Vec3(2.5D, 1D, 2.5D)));
        helper.assertTrue(echo != null && helper.getLevel().addFreshEntity(echo), "回响应能放进世界");
        helper.assertTrue(!ECMatrix.MATRIX_ECHO.get().canSerialize() && !ECMatrix.MATRIX_ECHO.get().canSummon(), "不存盘、不能 /summon");
        helper.assertTrue(echo.matrixType() == 2 && player.getUUID().equals(echo.trialist()), "外观数据");
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(echo.isRemoved(), "没有试炼的回响应自己消失");
            helper.succeed();
        });
    }

    /**
     * 别人的药水、治疗对回响无效（直接给、药水云、喷溅药水、瞬间治疗、forceAddEffect）；没有来源的增益不收、有害的照收；
     * 试炼者自己给的照收；别人的钓竿钩不住它（钩子穿过去），试炼者自己的照常
     */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixEchoIgnoresOthersEffects(GameTestHelper helper) {
        Rig rig = rig(helper, 0);
        ServerPlayer player = rig.player();
        ServerLevel level = helper.getLevel();
        MatrixTrials.Trial trial = start(helper, rig);
        try {
            MatrixEchoEntity echo = trial.echoes().get(0);
            ServerPlayer other = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-other");
            helper.assertTrue(!echo.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200), other) && !echo.hasEffect(MobEffects.DAMAGE_BOOST),
                    "旁人直接给的力量不收");
            AreaEffectCloud cloud = new AreaEffectCloud(level, echo.getX(), echo.getY(), echo.getZ());
            cloud.setOwner(other);
            helper.assertTrue(!echo.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200), cloud) && !echo.hasEffect(MobEffects.REGENERATION),
                    "旁人的滞留药水不收");
            ThrownPotion splash = new ThrownPotion(level, other);
            helper.assertTrue(!echo.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200), splash) && !echo.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                    "旁人扔的喷溅药水（帮试炼者的也算）不收");
            echo.forceAddEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200), other);
            helper.assertTrue(!echo.hasEffect(MobEffects.DAMAGE_RESISTANCE), "forceAddEffect 也挡住旁人");
            helper.assertTrue(!echo.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200)) && !echo.hasEffect(MobEffects.REGENERATION),
                    "没有来源的增益不收");
            helper.assertTrue(echo.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200)) && echo.hasEffect(MobEffects.WEAKNESS),
                    "没有来源的有害效果照收（别的模组的武器常不带来源）");
            helper.assertTrue(echo.hurt(player.damageSources().playerAttack(player), 1F), "试炼者打得动");
            float hurt = echo.getHealth();
            MobEffects.HEAL.applyInstantenousEffect(other, other, echo, 1, 1D);
            echo.heal(4F);
            helper.assertTrue(echo.getHealth() == hurt, "瞬间治疗、直接回血都不吃：" + hurt + " → " + echo.getHealth());
            ThrownPotion own = new ThrownPotion(level, player);
            helper.assertTrue(echo.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200), own) && echo.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                    "试炼者自己扔的药水照收");
            helper.assertTrue(echo.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 200), player), "试炼者直接给的照收");
            FishingHook hook = new FishingHook(other, level, 0, 0);
            helper.assertTrue(ForgeEventFactory.onProjectileImpact(hook, new EntityHitResult(echo)), "别人的钓竿钩不住回响");
            FishingHook ownHook = new FishingHook(player, level, 0, 0);
            helper.assertTrue(!ForgeEventFactory.onProjectileImpact(ownHook, new EntityHitResult(echo)), "试炼者自己的钓竿照常");
        } finally {
            MatrixTrials.clearForTest(player.getUUID());
        }
        helper.succeed();
    }

    /** 试炼者自己打出的火焰类伤害（反弹的火球）伤得到；火、岩浆、着火伤不到，身上也不着火 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixEchoTrialistFireHurts(GameTestHelper helper) {
        Rig rig = rig(helper, 0);
        ServerPlayer player = rig.player();
        MatrixTrials.Trial trial = start(helper, rig);
        try {
            MatrixEchoEntity echo = trial.echoes().get(0);
            float full = echo.getHealth();
            helper.assertTrue(!echo.hurt(echo.damageSources().lava(), 2F) && !echo.hurt(echo.damageSources().onFire(), 2F)
                    && !echo.hurt(echo.damageSources().inFire(), 2F) && echo.getHealth() == full, "没有攻击者的火、岩浆伤不到");
            echo.setSecondsOnFire(5);
            helper.assertTrue(echo.getRemainingFireTicks() <= 0 && !echo.isOnFire(), "身上不着火");
            helper.assertTrue(!echo.fireImmune(), "不该注册成 fireImmune");
            LargeFireball fireball = new LargeFireball(helper.getLevel(), player, 1D, 0D, 0D, 1);
            helper.assertTrue(echo.hurt(echo.damageSources().fireball(fireball, player), 1F) && echo.getHealth() < full,
                    "试炼者反弹回去的火球（带 is_fire）应伤得到");
        } finally {
            MatrixTrials.clearForTest(player.getUUID());
        }
        helper.succeed();
    }

    /** 别的模组直接 setHealth 到 0：当刻报告、腾出名额；试炼者打过的算打倒，没人打过的不算 */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 200)
    public static void matrixEchoSetHealthKillCounts(GameTestHelper helper) {
        Rig rig = rig(helper, 0);
        ServerPlayer player = rig.player();
        MatrixTrials.Trial trial = start(helper, rig);
        MatrixEchoEntity first = trial.echoes().get(0);
        first.setHealth(0F);
        MatrixEchoEntity[] second = new MatrixEchoEntity[1];
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(first.isRemoved() && !trial.echoes().contains(first), "生命归零应当刻移除、腾出名额");
            helper.assertTrue(trial.killed() == 0, "没人打过的不算打倒");
        });
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(trial.echoes().size() == 1, "应补了一只，实际 " + trial.echoes().size());
            second[0] = trial.echoes().get(0);
            second[0].hurt(player.damageSources().playerAttack(player), 1F);
            second[0].setHealth(0F);
        });
        helper.runAfterDelay(43, () -> {
            helper.assertTrue(second[0].isRemoved() && trial.killed() == 1, "试炼者打过、再被 setHealth 打死的应算一只，实际 " + trial.killed());
            MatrixTrials.clearForTest(player.getUUID());
            helper.succeed();
        });
    }

    /** 回响被关在井里、走不到试炼者身边：连续几次就挪到试炼者附近；够得着的不算卡住；不算打倒、不另补 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixEchoStuckMovesNearTrialist(GameTestHelper helper) {
        Rig rig = rig(helper, 0);
        ServerPlayer player = rig.player();
        MatrixTrials.Trial trial = start(helper, rig);
        try {
            MatrixEchoEntity echo = trial.echoes().get(0);
            echo.moveTo(player.getX() + 0.8D, player.getY(), player.getZ());
            echo.setOnGround(true);
            helper.assertTrue(!MatrixTrials.checkStuck(trial, echo) && echo.stuckStrikes() == 0, "够得着试炼者的不算卡住");
            BlockPos shaft = new BlockPos(9, 1, 5);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = 0; dy < 3; dy++) {
                        if (dx != 0 || dz != 0) {
                            helper.setBlock(shaft.offset(dx, dy, dz), Blocks.STONE);
                        }
                    }
                }
            }
            Vec3 cell = helper.absoluteVec(Vec3.atBottomCenterOf(shaft));
            echo.moveTo(cell.x, cell.y, cell.z);
            echo.setOnGround(true);
            echo.setStuckStrikes(0);
            boolean moved = false;
            int calls = 0;
            while (!moved && calls < MatrixTrials.STUCK_STRIKES) {
                moved = MatrixTrials.checkStuck(trial, echo);
                calls++;
            }
            helper.assertTrue(moved && calls == MatrixTrials.STUCK_STRIKES, "连续 " + MatrixTrials.STUCK_STRIKES + " 次走不到应挪过去，实际 " + calls + " 次、"
                    + (moved ? "挪了" : "没挪"));
            double dx = echo.getX() - player.getX();
            double dz = echo.getZ() - player.getZ();
            helper.assertTrue(Math.sqrt(dx * dx + dz * dz) <= MatrixTrials.RELOCATE_MAX + 1D && !echo.blockPosition().equals(helper.absolutePos(shaft)),
                    "应挪到试炼者附近，实际 " + echo.blockPosition());
            helper.assertTrue(trial.killed() == 0 && trial.echoes().size() == 1 && trial.echoes().contains(echo), "挪过去不算打倒、不另补");
        } finally {
            MatrixTrials.clearForTest(player.getUUID());
        }
        helper.succeed();
    }

    // ================================================================ 生成点与领地（C60）

    /** 周围全被领地拒：开不了、什么都不扣 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixTrialNoRoomPaysNothing(GameTestHelper helper) {
        Rig rig = rig(helper, 0);
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).denyBox(area(helper))) {
            MatrixTrials.Start start = MatrixTrials.tryStart(rig.player(), rig.crucible());
            helper.assertTrue(start.result() == MatrixTrials.StartResult.NO_ROOM, "回响放不出来应开不了，实际 " + start.result());
            helper.assertTrue(rig.data().getUBMRU() == 1000 && rig.crucible().getMRUStorage().getMRU() == 1000 && !rig.crucible().isLocked()
                    && MatrixTrials.trialOf(rig.player().getUUID()) == null, "什么都不扣、不锁");
        }
        helper.succeed();
    }

    /** 一半被拒：回响只放在允许的那一半 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixTrialSpawnsOnlyWhereAllowed(GameTestHelper helper) {
        Rig rig = rig(helper, 0);
        BlockPos center = helper.absolutePos(CRUCIBLE);
        BoundingBox west = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(-3, 0, -3)), new BlockPos(center.getX() - 1, center.getY() + 6,
                center.getZ() + 8));
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).denyBox(west)) {
            MatrixTrials.Trial trial = start(helper, rig);
            try {
                int found = 0;
                for (MatrixEchoEntity echo : trial.echoes()) {
                    helper.assertTrue(echo.blockPosition().getX() >= center.getX(), "回响不该放在被拒的格子：" + echo.blockPosition());
                    found++;
                }
                for (int i = 0; i < 8; i++) {
                    MatrixTrials.Placement placement = MatrixTrials.findSpawn(trial, helper.getLevel().random);
                    if (placement.pos() != null) {
                        helper.assertTrue(placement.pos().x >= center.getX(), "生成点不该在被拒的格子：" + placement.pos());
                        found++;
                    }
                }
                helper.assertTrue(found > 0, "允许的那一半应找得到生成点");
            } finally {
                MatrixTrials.clearForTest(rig.player().getUUID());
            }
        }
        helper.succeed();
    }

    /** 中途领地变了、一只都放不出来：中止、能量全退（储备回储备、身上回身上、MRU 回台子），不冷却、祭品留着 */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 400)
    public static void matrixTrialStallAbortRefunds(GameTestHelper helper) {
        Rig rig = rig(helper, 0);
        rig.data().setMatrixTrialReserve(60);
        MatrixTrials.Trial trial = start(helper, rig);
        ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).denyBox(area(helper));
        ECProtection.forget(rig.player().getUUID());
        trial.echoes().forEach(MatrixEchoEntity::discard);
        helper.succeedWhen(() -> {
            helper.assertTrue(MatrixTrials.trialOf(rig.player().getUUID()) == null, "还没中止");
            claims.close();
            helper.assertTrue(rig.data().getMatrixTrialReserve() == 60 && rig.data().getUBMRU() == 1000 && rig.crucible().getMRUStorage().getMRU() == 1000,
                    "能量应全退：储备 " + rig.data().getMatrixTrialReserve() + "、身上 " + rig.data().getUBMRU() + "、MRU " + rig.crucible().getMRUStorage().getMRU());
            helper.assertTrue(rig.data().getMatrixTrialCooldown() == 0 && !rig.crucible().isLocked()
                    && rig.crucible().getInventory().getStackInSlot(1).getCount() == 2, "中止不冷却、解锁、祭品留着");
        });
    }


    /**
     * 开始以后用方块把放过回响的格子（连同整圈）堵死：这是地形，试炼者自己能控制，过了中止要等的刻数也不中止、不退能量，
     * 到点按超时失败并冷却（以前一律中止退还，能拿来躲失败代价）
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 400)
    public static void matrixTrialTerrainBlockedNoRefund(GameTestHelper helper) {
        Rig rig = rig(helper, 5);
        UUID id = rig.player().getUUID();
        MatrixTrials.Trial trial = start(helper, rig);
        helper.assertTrue(!trial.usedCells().isEmpty(), "放过回响的格子应记下");
        int ubmru = rig.data().getUBMRU();
        int mru = rig.crucible().getMRUStorage().getMRU();
        helper.assertTrue(ubmru == 1000 - UBMRU_NEED && mru == 1000 - MRU_NEED, "开始时应扣能量");
        trial.echoes().forEach(MatrixEchoEntity::discard);
        for (int x = 2; x <= 8; x++) {
            for (int y = 1; y <= 5; y++) {
                for (int z = 2; z <= 8; z++) {
                    BlockPos at = new BlockPos(x, y, z);
                    if (!at.equals(CRUCIBLE)) {
                        helper.setBlock(at, Blocks.STONE);
                    }
                }
            }
        }
        helper.runAfterDelay(MatrixTrials.STALL_TICKS + 20, () -> {
            helper.assertTrue(MatrixTrials.trialOf(id) == trial, "被方块堵住不该中止");
            helper.assertTrue(rig.data().getUBMRU() == ubmru && rig.crucible().getMRUStorage().getMRU() == mru, "被方块堵住不该退能量");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(MatrixTrials.trialOf(id) == null, "还没到时限");
            helper.assertTrue(rig.data().getMatrixTrialCooldown() == cooldownTicks(), "到点应按超时失败、冷却");
            helper.assertTrue(rig.data().getUBMRU() == ubmru && rig.crucible().getMRUStorage().getMRU() == mru && rig.data().getMatrixRank() == 5,
                    "超时失败不退能量、不升阶");
        });
    }

    /**
     * 回响被别的模组、插件拒了（实体加不进世界）：开始时说被拦下、什么都不扣；中途一直被拒、场上又没有，中止并全退、不冷却
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 400)
    public static void matrixTrialSpawnDeniedAbortRefunds(GameTestHelper helper) {
        Rig rig = rig(helper, 0);
        UUID id = rig.player().getUUID();
        Consumer<EntityJoinLevelEvent> deny = event -> {
            if (event.getEntity() instanceof MatrixEchoEntity echo && id.equals(echo.trialist())) {
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EntityJoinLevelEvent.class, deny);
        MatrixTrials.Start denied;
        try {
            denied = MatrixTrials.tryStart(rig.player(), rig.crucible());
        } finally {
            MinecraftForge.EVENT_BUS.unregister(deny);
        }
        helper.assertTrue(denied.result() == MatrixTrials.StartResult.SPAWN_DENIED, "开始时被拒应说被拦下，实际 " + denied.result());
        helper.assertTrue(rig.data().getUBMRU() == 1000 && rig.crucible().getMRUStorage().getMRU() == 1000 && !rig.crucible().isLocked()
                && MatrixTrials.trialOf(id) == null, "开始时被拒什么都不扣");
        MatrixTrials.Trial trial = start(helper, rig);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EntityJoinLevelEvent.class, deny);
        trial.echoes().forEach(MatrixEchoEntity::discard);
        helper.succeedWhen(() -> {
            helper.assertTrue(MatrixTrials.trialOf(id) == null, "还没中止");
            MinecraftForge.EVENT_BUS.unregister(deny);
            helper.assertTrue(rig.data().getUBMRU() == 1000 && rig.crucible().getMRUStorage().getMRU() == 1000 && trial.refundedInFull(),
                    "中止应全退：身上 " + rig.data().getUBMRU() + "、MRU " + rig.crucible().getMRUStorage().getMRU());
            helper.assertTrue(rig.data().getMatrixTrialCooldown() == 0 && !rig.crucible().isLocked(), "中止不冷却、解锁");
        });
    }

    /** 中止时台子已经被灌满：退不回去的部分算没退全（提示换一句），UBMRU 照样全退 */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 400)
    public static void matrixTrialAbortPartialRefund(GameTestHelper helper) {
        Rig rig = rig(helper, 0);
        MatrixTrials.Trial trial = start(helper, rig);
        int max = rig.crucible().getMRUStorage().getMaxMRU();
        rig.crucible().getMRUStorage().setMRU(max);
        ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).denyBox(area(helper));
        ECProtection.forget(rig.player().getUUID());
        trial.echoes().forEach(MatrixEchoEntity::discard);
        helper.succeedWhen(() -> {
            helper.assertTrue(MatrixTrials.trialOf(rig.player().getUUID()) == null, "还没中止");
            claims.close();
            helper.assertTrue(!trial.refundedInFull(), "台子满了，MRU 退不回去，应算没退全");
            helper.assertTrue(rig.data().getUBMRU() == 1000 && rig.crucible().getMRUStorage().getMRU() == max && rig.data().getMatrixTrialCooldown() == 0,
                    "UBMRU 照样全退、台子不超上限、不冷却");
        });
    }

    // ================================================================ 站点（C75）

    /** 四阶起要 8 格内的五芒星：没有、档位不够、不归你都不算，ESPE 不扣；归你的才算 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixTrialSiteOwnPentacleOnly(GameTestHelper helper) {
        Rig rig = rig(helper, 2, SITE_CRUCIBLE, new Vec3(1.5D, 1D, 3.5D));
        helper.assertTrue(MatrixTrialChecks.evaluate(rig.player(), rig.crucible()).site().state() == MatrixTrialChecks.SiteState.NONE,
                "没有五芒星时站点是 NONE");
        UUID neighbour = UUID.randomUUID();
        PlayerPentacleBlockEntity pentacle = pentacle(helper, neighbour);
        int espe = pentacle.effectiveESPE();
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(PENTACLE)) {
            ECProtection.forget(rig.player().getUUID());
            MatrixTrialChecks.Report report = MatrixTrialChecks.evaluate(rig.player(), rig.crucible());
            helper.assertTrue(report.site().state() == MatrixTrialChecks.SiteState.NOT_YOURS
                    && report.blockers().contains(MatrixTrialChecks.Blocker.SITE_NOT_YOURS), "邻居的五芒星不算站点，实际 " + report.site());
            MatrixTrials.Start start = MatrixTrials.tryStart(rig.player(), rig.crucible());
            helper.assertTrue(!start.started() && pentacle.effectiveESPE() == espe, "不扣邻居柱子里的 ESPE");
            pentacle.setOwner(rig.player().getUUID(), "ec5-trial");
            report = MatrixTrialChecks.evaluate(rig.player(), rig.crucible());
            helper.assertTrue(report.site().state() == MatrixTrialChecks.SiteState.OK && report.site().pentacle() == pentacle,
                    "自己的五芒星算站点，实际 " + report.site());
        }
        rig.data().setMatrixRank(3);
        MatrixTrialChecks.Report tier = MatrixTrialChecks.evaluate(rig.player(), rig.crucible());
        helper.assertTrue(tier.site().state() == MatrixTrialChecks.SiteState.TIER && tier.site().foundTier() == 0 && tier.site().needTier() == 1,
                "要 1 档时 0 档不够，实际 " + tier.site());
        helper.succeed();
    }

    /** 站点就绪：开始时从自己五芒星的柱子扣有效 ESPE */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixTrialSitePaysEspe(GameTestHelper helper) {
        Rig rig = rig(helper, 2, SITE_CRUCIBLE, new Vec3(1.5D, 1D, 3.5D));
        PlayerPentacleBlockEntity pentacle = pentacle(helper, rig.player().getUUID());
        int before = pentacle.effectiveESPE();
        start(helper, rig);
        try {
            helper.assertTrue(pentacle.effectiveESPE() == before - ESPE_NEED, "应扣 " + ESPE_NEED + " 有效 ESPE：" + before + " → " + pentacle.effectiveESPE());
        } finally {
            MatrixTrials.clearForTest(rig.player().getUUID());
        }
        helper.succeed();
    }


    /** 五芒星刚拆掉、缓存的档位还没更新时点开始：扣 ESPE 之前当场重判，档位不够就开不了，柱子一点不扣 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixTrialPayRechecksPentacleTier(GameTestHelper helper) {
        Rig rig = rig(helper, 2, SITE_CRUCIBLE, new Vec3(1.5D, 1D, 3.5D));
        PlayerPentacleBlockEntity pentacle = pentacle(helper, rig.player().getUUID());
        double before = pillarEspe(helper);
        BlockPos plating = ECPentacle.TIER_PATTERNS.get(0).offsets(ECPentacle.PLATING_RULE).iterator().next();
        helper.setBlock(PENTACLE.offset(plating), Blocks.AIR);
        helper.assertTrue(pentacle.getTier() == 0, "缓存的档位这时还是 0");
        MatrixTrials.Start start = MatrixTrials.tryStart(rig.player(), rig.crucible());
        helper.assertTrue(start.result() == MatrixTrials.StartResult.PAYMENT, "当场重判档位不够应开不了，实际 " + start.result() + " / " + start.blocker());
        helper.assertTrue(Math.abs(pillarEspe(helper) - before) < 1.0E-6D && !rig.crucible().isLocked() && MatrixTrials.trialOf(rig.player().getUUID()) == null,
                "柱子一点不扣、不锁");
        helper.succeed();
    }

    /** 三阶站点用例：中途领地变了、中止，ESPE 原样放回五芒星的柱子 */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 400)
    public static void matrixTrialAbortRefundsEspe(GameTestHelper helper) {
        Rig rig = rig(helper, 2, SITE_CRUCIBLE, new Vec3(1.5D, 1D, 3.5D));
        PlayerPentacleBlockEntity pentacle = pentacle(helper, rig.player().getUUID());
        // 柱子是满的、露天还会自己产 ESPE：等中止的这段时间就补满了，退回去的反而放不下。柱顶盖上石头，不让它产
        for (BlockPos offset : ECPentacle.PILLAR_OFFSETS.get(0)) {
            helper.setBlock(PENTACLE.offset(offset).above(3), Blocks.STONE);
        }
        double before = pillarEspe(helper);
        int effective = pentacle.effectiveESPE();
        MatrixTrials.Trial trial = start(helper, rig);
        helper.assertTrue(pentacle.effectiveESPE() == effective - ESPE_NEED, "开始时应扣 " + ESPE_NEED + " 有效 ESPE");
        ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).denyBox(area(helper));
        ECProtection.forget(rig.player().getUUID());
        trial.echoes().forEach(MatrixEchoEntity::discard);
        helper.succeedWhen(() -> {
            helper.assertTrue(MatrixTrials.trialOf(rig.player().getUUID()) == null, "还没中止");
            claims.close();
            helper.assertTrue(Math.abs(pillarEspe(helper) - before) < 1.0E-6D && pentacle.effectiveESPE() == effective && trial.refundedInFull(),
                    "ESPE 应原样放回：柱子 " + before + " → " + pillarEspe(helper) + "，有效 " + effective + " → " + pentacle.effectiveESPE()
                            + "，全退回 " + trial.refundedInFull());
            helper.assertTrue(rig.data().getMatrixTrialCooldown() == 0, "中止不冷却");
        });
    }

    /** 测试五芒星（0 档）所有柱子的原始 ESPE 之和 */
    private static double pillarEspe(GameTestHelper helper) {
        double sum = 0D;
        for (BlockPos offset : ECPentacle.PILLAR_OFFSETS.get(0)) {
            if (helper.getBlockEntity(PENTACLE.offset(offset)) instanceof MithrilineCrystalPillarBlockEntity pillar) {
                sum += pillar.getESPEStorage().getESPE();
            }
        }
        return sum;
    }

    /**
     * 状态包：打开时发一次；之后每 {@value MatrixCrucibleMenu#STATUS_INTERVAL} 刻重算，内容没变不发（矩阵容量的自然换入不算变化）；
     * 身上 UBMRU 变了，到下一轮重算时发；点了按钮之类 requestStatus 的马上重发
     */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixCrucibleStatusDeduped(GameTestHelper helper) {
        Rig rig = rig(helper, 0);
        MatrixCrucibleMenu menu = new MatrixCrucibleMenu(0, rig.player().getInventory(), rig.crucible());
        menu.broadcastChanges();
        helper.assertTrue(menu.statusSent() == 1, "打开时应发一次，实际 " + menu.statusSent());
        for (int i = 0; i < MatrixCrucibleMenu.STATUS_INTERVAL * 3; i++) {
            menu.broadcastChanges();
        }
        helper.assertTrue(menu.statusSent() == 1, "内容没变不该再发，实际 " + menu.statusSent());
        rig.data().setMatrixPool(rig.data().getMatrixPool() + 7, false);
        for (int i = 0; i < MatrixCrucibleMenu.STATUS_INTERVAL; i++) {
            menu.broadcastChanges();
        }
        helper.assertTrue(menu.statusSent() == 1, "只有矩阵容量变了不该发，实际 " + menu.statusSent());
        rig.data().setUBMRU(rig.data().getUBMRU() - 1);
        for (int i = 0; i < MatrixCrucibleMenu.STATUS_INTERVAL - 1; i++) {
            menu.broadcastChanges();
        }
        helper.assertTrue(menu.statusSent() == 1, "还没到重算的时候，实际 " + menu.statusSent());
        menu.broadcastChanges();
        helper.assertTrue(menu.statusSent() == 2, "UBMRU 变了，这一轮应发，实际 " + menu.statusSent());
        menu.requestStatus();
        menu.broadcastChanges();
        helper.assertTrue(menu.statusSent() == 3, "requestStatus 后应马上重发，实际 " + menu.statusSent());
        helper.succeed();
    }

    // ================================================================ 试炼储备

    /**
     * 存储备：上限是下一阶要的 UBMRU，满了再存不进；九阶说「最高阶」，下一阶不要 UBMRU、下一阶没数据各有各的说法（以前一律说最高阶）；
     * 储备超过当前上限的部分，存的时候先退回身上
     */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixReserveDepositCaps(GameTestHelper helper) {
        Rig rig = rig(helper, 0);
        MatrixTrials.Deposit first = MatrixTrials.deposit(rig.player());
        helper.assertTrue(first.amount() == UBMRU_NEED && first.reason() == null, "应存进 " + UBMRU_NEED + "，实际 " + first);
        helper.assertTrue(rig.data().getMatrixTrialReserve() == UBMRU_NEED && rig.data().getUBMRU() == 1000 - UBMRU_NEED, "储备满、身上少了");
        expectDeposit(helper, rig, "full", "满了再存不进");
        helper.assertTrue(rig.data().getUBMRU() == 1000 - UBMRU_NEED, "满了身上不再少");
        rig.data().setMatrixRank(MatrixRank.MAX);
        rig.data().setMatrixTrialReserve(0);
        expectDeposit(helper, rig, "not_needed", "九阶不用存");
        // 一阶的下一阶（二阶）不要 UBMRU：说「下一阶不要」，不说「最高阶」；储备里的 500 超过上限 0，退回身上
        rig.data().setMatrixRank(1);
        rig.data().setMatrixTrialReserve(500);
        rig.data().setUBMRU(100);
        expectDeposit(helper, rig, "no_ubmru", "下一阶不要 UBMRU");
        helper.assertTrue(rig.data().getMatrixTrialReserve() == 0 && rig.data().getUBMRU() == 600,
                "超出上限的储备应退回身上：储备 " + rig.data().getMatrixTrialReserve() + "、身上 " + rig.data().getUBMRU());
        // 七阶的下一阶在测试表里没有
        rig.data().setMatrixRank(7);
        expectDeposit(helper, rig, "no_data", "下一阶没数据");
        helper.succeed();
    }

    private static void expectDeposit(GameTestHelper helper, Rig rig, String reason, String what) {
        int reserve = rig.data().getMatrixTrialReserve();
        MatrixTrials.Deposit deposit = MatrixTrials.deposit(rig.player());
        helper.assertTrue(deposit.amount() == 0 && reason.equals(deposit.reason()), what + "：应因 " + reason + " 存不进，实际 " + deposit);
        helper.assertTrue(rig.data().getMatrixTrialReserve() <= reserve, what + "：储备不该变多");
    }

    /** 试炼进行中存不进（以前会按旧阶的上限存进去、升阶后卡死）；成功升阶后储备超过新上限的部分退回身上 */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 200)
    public static void matrixReserveRunningAndExcess(GameTestHelper helper) {
        Rig rig = rig(helper, 0);
        start(helper, rig);
        int body = rig.data().getUBMRU();
        MatrixTrials.Deposit during = MatrixTrials.deposit(rig.player());
        helper.assertTrue(during.amount() == 0 && "running".equals(during.reason()) && rig.data().getMatrixTrialReserve() == 0
                && rig.data().getUBMRU() == body, "试炼中存不进，实际 " + during);
        // 试炼中硬塞进去的储备（老版本会按旧阶的上限存进去）：升到一阶后上限是 0，应退回身上
        rig.data().setMatrixTrialReserve(500);
        helper.succeedWhen(() -> {
            killEchoes(rig.player());
            helper.assertTrue(rig.data().getMatrixRank() == 1, "还没升阶");
            helper.assertTrue(rig.data().getMatrixTrialReserve() == 0 && rig.data().getUBMRU() == body + 500,
                    "升阶后多出的储备应退回身上：储备 " + rig.data().getMatrixTrialReserve() + "、身上 " + rig.data().getUBMRU());
        });
    }

    /** 拿着自己的灵魂石潜行右键淬炼台才存，并接住这次交互（灵魂石自己的潜行右键不生效）；别人的石头、不潜行、点别的方块都不管 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixReserveSneakUse(GameTestHelper helper) {
        Rig rig = rig(helper, 0);
        ServerPlayer player = rig.player();
        BlockPos pos = helper.absolutePos(CRUCIBLE);
        player.setShiftKeyDown(false);
        helper.assertTrue(!use(player, pos).isCanceled() && rig.data().getMatrixTrialReserve() == 0, "不潜行不存");
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, stone(UUID.randomUUID()));
        helper.assertTrue(!use(player, pos).isCanceled() && rig.data().getMatrixTrialReserve() == 0, "别人的灵魂石不存");
        player.setItemInHand(InteractionHand.MAIN_HAND, stone(player.getUUID()));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        helper.assertTrue(!use(player, helper.absolutePos(new BlockPos(1, 1, 1))).isCanceled(), "点别的方块不管");
        PlayerInteractEvent.RightClickBlock event = use(player, pos);
        helper.assertTrue(event.isCanceled() && rig.data().getMatrixTrialReserve() == UBMRU_NEED, "潜行右键淬炼台应存进储备并接住交互");
        helper.succeed();
    }

    private static PlayerInteractEvent.RightClickBlock use(ServerPlayer player, BlockPos pos) {
        PlayerInteractEvent.RightClickBlock event = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        MatrixTrials.MatrixTrialEvents.matrixTrialReserveUse(event);
        return event;
    }


    // ================================================================ 失败判定走 tick（不调监听方法）

    /** 玩家对象被移除（末地出口回主世界时旧对象带着 CHANGED_DIMENSION 被移除、不发换维度事件）：tick 开头就判换维度失败 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixTrialTickCatchesRemoved(GameTestHelper helper) {
        Rig rig = rig(helper, 0);
        start(helper, rig);
        rig.player().setRemoved(Entity.RemovalReason.CHANGED_DIMENSION);
        helper.runAfterDelay(2, () -> {
            assertFailed(helper, rig, "玩家对象被移除");
            helper.succeed();
        });
    }

    /** 重生成了新的玩家对象（同一个 UUID）：试炼还挂在旧对象上，按换维度失败 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixTrialRespawnFails(GameTestHelper helper) {
        Rig rig = rig(helper, 0);
        start(helper, rig);
        ServerPlayer reborn = ProtectionFrameworkTests.realPlayer(helper, rig.player().getUUID(), "ec5-trial");
        MatrixTrials.MatrixTrialEvents.matrixTrialRespawn(new PlayerEvent.PlayerRespawnEvent(rig.player(), false));
        helper.assertTrue(MatrixTrials.trialOf(rig.player().getUUID()) != null, "同一个对象的重生事件不该判失败");
        MatrixTrials.MatrixTrialEvents.matrixTrialRespawn(new PlayerEvent.PlayerRespawnEvent(reborn, true));
        assertFailed(helper, rig, "重生成新对象");
        helper.succeed();
    }

    /** 生命归零（不经过死亡事件）：tick 里判死亡失败 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixTrialTickCatchesDeath(GameTestHelper helper) {
        Rig rig = rig(helper, 0);
        start(helper, rig);
        rig.player().setHealth(0F);
        helper.runAfterDelay(2, () -> {
            assertFailed(helper, rig, "生命归零");
            helper.succeed();
        });
    }

    // ================================================================ 配置与数据的边界

    /** 数据包写 alive 9：按每人同时 4 只读（policy §4.16），这一阶照常读到；写 3 就是 3 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixRankDataClampsAlive(GameTestHelper helper) {
        List<String> warnings = new ArrayList<>();
        MatrixRankData.Entry many = MatrixRankData.parse(3, JsonParser.parseString(
                "{\"echoes\": {\"count\": 5, \"alive\": 9, \"health\": 10, \"damage\": 1, \"time_seconds\": 60}}"), warnings::add);
        helper.assertTrue(many != null && many.echoes().alive() == MatrixRankData.MAX_ALIVE && warnings.isEmpty(),
                "alive 9 应读成 " + MatrixRankData.MAX_ALIVE + "、不算坏条目，实际 " + (many == null ? "读不到" : many.echoes().alive()) + " " + warnings);
        MatrixRankData.Entry few = MatrixRankData.parse(3, JsonParser.parseString(
                "{\"echoes\": {\"count\": 5, \"alive\": 3, \"health\": 10, \"damage\": 1, \"time_seconds\": 60}}"), warnings::add);
        helper.assertTrue(few != null && few.echoes().alive() == 3, "alive 3 照读");
        helper.succeed();
    }

    /** 这一阶要的 MRU 比台子容量还大：单独挡住（以前只说 MRU 不够，怎么充都凑不够），状态包带台子容量 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixTrialMruCapacityBlocker(GameTestHelper helper) {
        Rig rig = rig(helper, 4);
        MatrixTrialChecks.Report report = MatrixTrialChecks.evaluate(rig.player(), rig.crucible());
        helper.assertTrue(report.blockers().contains(MatrixTrialChecks.Blocker.MRU_CAPACITY) && !report.blockers().contains(MatrixTrialChecks.Blocker.MRU),
                "应挡在容量上，实际 " + report.blockers());
        MatrixTrials.Start start = MatrixTrials.tryStart(rig.player(), rig.crucible());
        helper.assertTrue(start.blocker() == MatrixTrialChecks.Blocker.MRU_CAPACITY, "开始应被容量挡住，实际 " + start.blocker());
        CompoundTag tag = MatrixTrialChecks.statusTag(rig.player(), rig.crucible());
        helper.assertTrue(tag.getInt("mru_cap") == rig.crucible().getMRUStorage().getMaxMRU() && tag.getInt("mru_need") == BIG_MRU, "状态包应带台子容量：" + tag);
        helper.succeed();
    }

    /** 失败提示：台子没了不说祭品还在，冷却为 0 不说几秒后再试 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void matrixTrialFailMessageKeys(GameTestHelper helper) {
        String base = "message.essentialcraft5.matrix_trial.";
        helper.assertTrue(MatrixTrials.failMessageKey(MatrixTrials.Failure.TIMEOUT, 180).equals(base + "failed"), "普通失败");
        helper.assertTrue(MatrixTrials.failMessageKey(MatrixTrials.Failure.LEFT, 0).equals(base + "failed_no_cooldown"), "冷却 0");
        helper.assertTrue(MatrixTrials.failMessageKey(MatrixTrials.Failure.CRUCIBLE, 180).equals(base + "failed_crucible"), "台子没了");
        helper.assertTrue(MatrixTrials.failMessageKey(MatrixTrials.Failure.CRUCIBLE, 0).equals(base + "failed_crucible_no_cooldown"), "台子没了、冷却 0");
        helper.succeed();
    }

    /** 离开半径填 2（比开始距离还小）：按 5 格算，站在 3 格处开始不会第一刻就判走远；回收距离不比最远生成距离近 */
    @GameTest(template = LARGE, batch = LEAVE_BATCH)
    public static void matrixTrialLeaveRadiusFloor(GameTestHelper helper) {
        helper.assertTrue(MatrixTrials.leaveRadius() == MatrixTrials.MIN_LEAVE_RADIUS, "离开半径应按下限算，实际 " + MatrixTrials.leaveRadius());
        helper.assertTrue(MatrixTrials.leashRadius() >= 3 + MatrixTrials.LEASH_PAST_SPAWN, "回收距离应比最远生成距离远，实际 " + MatrixTrials.leashRadius());
        Rig rig = rig(helper, 0, CRUCIBLE, new Vec3(5.5D, 1D, 8.5D));
        MatrixTrials.Trial trial = start(helper, rig);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(MatrixTrials.trialOf(rig.player().getUUID()) == trial && rig.data().getMatrixTrialCooldown() == 0,
                    "站在 3 格处开始不该判走远");
            MatrixTrials.clearForTest(rig.player().getUUID());
            helper.succeed();
        });
    }

    /** 回响试炼进行中改成和平难度：回响全部收回，当场改成引导，时限换成引导的 */
    @GameTest(template = LARGE, batch = SWITCH_BATCH)
    public static void matrixTrialPeacefulSwitch(GameTestHelper helper) {
        Rig rig = rig(helper, 0);
        MatrixTrials.Trial trial = start(helper, rig);
        helper.assertTrue(!trial.channel() && trial.echoes().size() == 1, "开始时应是回响试炼");
        MatrixEchoEntity echo = trial.echoes().get(0);
        MatrixTrials.setTestOverrides(new int[]{2, 3}, LEAVE, 20, null, true);
        MatrixRank.setTestPeaceful(true);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(trial.channel() && trial.echoes().isEmpty() && echo.isRemoved(), "改和平后应收回回响、改成引导");
            helper.assertTrue(trial.timeLimit() == 20 * 2 + MatrixTrials.CHANNEL_SLACK_SECONDS * 20, "时限应换成引导的，实际 " + trial.timeLimit());
            helper.assertTrue(MatrixTrials.trialOf(rig.player().getUUID()) == trial, "改引导不算失败");
            MatrixTrials.clearForTest(rig.player().getUUID());
            helper.succeed();
        });
    }

    // ================================================================ 和平引导

    /** 和平难度：不放回响，站着不动够时间就成功 */
    @GameTest(template = LARGE, batch = CHANNEL_BATCH, timeoutTicks = 200)
    public static void matrixChannelSucceeds(GameTestHelper helper) {
        Rig rig = rig(helper, 0);
        MatrixTrials.Trial trial = start(helper, rig);
        helper.assertTrue(trial.channel() && trial.echoes().isEmpty(), "和平难度应走引导、不放回响");
        helper.succeedWhen(() -> {
            helper.assertTrue(rig.data().getMatrixRank() == 1, "还没引导完");
            helper.assertTrue(rig.crucible().getInventory().getStackInSlot(1).isEmpty() && !rig.crucible().isLocked(), "祭品消耗、解锁");
        });
    }

    /** 引导中动了、受伤了都从头算，不算失败 */
    @GameTest(template = LARGE, batch = CHANNEL_BATCH, timeoutTicks = 200)
    public static void matrixChannelResetsOnMoveAndHurt(GameTestHelper helper) {
        Rig rig = rig(helper, 0);
        ServerPlayer player = rig.player();
        MatrixTrials.Trial trial = start(helper, rig);
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(trial.channelProgress() >= 3, "站着应在攒进度，实际 " + trial.channelProgress());
            player.moveTo(player.getX() + 1D, player.getY(), player.getZ());
        });
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(trial.channelProgress() <= 2, "动了应从头算，实际 " + trial.channelProgress());
            MatrixTrials.onTrialistHurt(player);
        });
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(trial.channelProgress() <= 2 && MatrixTrials.trialOf(player.getUUID()) == trial, "受伤从头算、不算失败");
            helper.assertTrue(rig.data().getMatrixTrialCooldown() == 0 && rig.data().getMatrixRank() == 0, "没有冷却、没升阶");
            MatrixTrials.clearForTest(player.getUUID());
            helper.succeed();
        });
    }

    /** 引导也有时限（要站的时间 × 2 + 多给的秒数）：一直在动、站不够，到点按超时失败并冷却 */
    @GameTest(template = LARGE, batch = CHANNEL_BATCH, timeoutTicks = 200)
    public static void matrixChannelTimeLimitFails(GameTestHelper helper) {
        Rig rig = rig(helper, 0);
        ServerPlayer player = rig.player();
        MatrixTrials.Trial trial = start(helper, rig);
        helper.assertTrue(trial.channel() && trial.timeLimit() == 20 * 2 + 20, "引导时限应是 20 × 2 + 1 秒，实际 " + trial.timeLimit());
        Vec3 home = player.position();
        boolean[] flip = {false};
        helper.onEachTick(() -> {
            flip[0] = !flip[0];
            player.moveTo(home.x + (flip[0] ? 0.5D : 0D), home.y, home.z);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(MatrixTrials.trialOf(player.getUUID()) == null, "还没到时限");
            helper.assertTrue(rig.data().getMatrixTrialCooldown() == cooldownTicks() && rig.data().getMatrixRank() == 0, "引导超时应失败、冷却、不升阶");
        });
    }
}
