package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.block.hoanna.RuinMilestoneBlock;
import com.flameforge.essentialcraft5.hoanna.HoannaSearchBudget;
import com.flameforge.essentialcraft5.hoanna.Radiation;
import com.flameforge.essentialcraft5.hoanna.survey.SurveySearches;
import com.flameforge.essentialcraft5.item.AbyssalSaltItem;
import com.flameforge.essentialcraft5.item.HoannaSurveyMapItem;
import com.flameforge.essentialcraft5.loot.HoannaLoot;
import com.flameforge.essentialcraft5.player.PlayerData;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHoannaMaterials;
import com.flameforge.essentialcraft5.registry.ECHoannaSurvey;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.worldgen.hoanna.StructureForecast;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * 第十三批 13d-H3「探索与续航」（batch13.md §6.3、D33；HS §2.3）：
 * <ul>
 *   <li>残破勘测图：城塔、墓穴箱子表的第三池（12%，原来的遗物池、残卷池不变由 {@code VoidTests.hoannaChestTables} 核）；配方三张 + 地脉结晶；</li>
 *   <li>勘测图：海纳尔外右键不找、绑定的是结构预测给的最近城镇（固定种子）、潜行切换按宝库 → 沉城 → 前哨 → 城镇轮换（没注册的跳过）、跳过表里的不再选、满 16 挤掉最早的、
 *       24 格内到点发进度并解绑、搜索不加载区块、两名玩家同一刻合计不超全服预算；</li>
 *   <li>里程碑：右键给出最近城镇的方向距离（≤ 16 个采样）、每人每块冷却 40 刻、假玩家不理；</li>
 *   <li>渊盐：清 20% 且夹在 3 000~12 000、不到 3 000 时清到 0、配置填 0、主世界吃也有效、饥饿 I、冷却以玩家数据为准（物品冷却丢了照样吃不下、
 *       登录时补回）、结壳的掉落。</li>
 * </ul>
 * 「海纳尔」用 {@link ECHoanna#setTestHoannaOverride}，预测用的世界用 {@link SurveySearches#setTestWorld} 换成固定种子的离线海纳尔
 * （测试世界是超平坦，没有城镇结构集），都在同一段同步代码里设、测、还原。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class HoannaSurveyTests {

    private static final String EMPTY = "empty";
    private static final long SEED_STEP = 0x9E3779B97F4A7C15L;
    private static final int RADIUS = ECHoannaSurvey.Config.DEFAULTS.searchRadius();

    private HoannaSurveyTests() {
    }

    // ================================================================== 工具

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static IPlayerData data(Player player) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            throw new IllegalStateException("测试玩家应挂着玩家数据");
        }
        return data;
    }

    /** 把测试世界当海纳尔、预测用固定种子的离线海纳尔，跑 body（同步，finally 里全部还原、丢掉搜索） */
    private static void inSurvey(GameTestHelper helper, long seed, BiConsumer<ServerLevel, StructureForecast.World> body) {
        ServerLevel level = helper.getLevel();
        StructureForecast.World world = HoannaForecastTests.world(HoannaTestWorld.resolve(level.getServer(), seed));
        ECHoanna.setTestHoannaOverride(level);
        SurveySearches.setTestWorld(world);
        try {
            body.accept(level, world);
        } finally {
            SurveySearches.clear();
            SurveySearches.setTestWorld(null);
            ECHoanna.clearTestOverride();
        }
    }

    /** 暴力对照：同一个世界、同一种目标、同一个中心，不限预算找完 */
    private static Optional<BlockPos> expected(ServerLevel level, StructureForecast.World world, BlockPos center, int radius, Predicate<ChunkPos> skip) {
        List<StructureForecast.Target> targets = HoannaSurveyMapItem.targets(level, world, HoannaSurveyMapItem.TOWN);
        return new StructureForecast.Search(world, targets, center, radius, skip).finish().result();
    }

    /** 一张记着「找城镇」的新图（13e 起宝库排在最前，新图默认找宝库） */
    private static ItemStack townMap() {
        ItemStack stack = new ItemStack(ECHoannaSurvey.HOANNA_SURVEY_MAP.get());
        stack.getOrCreateTag().putString(HoannaSurveyMapItem.TAG_KIND, HoannaSurveyMapItem.TOWN.id());
        return stack;
    }

    private static boolean sameColumn(BlockPos bound, Optional<BlockPos> expected) {
        return bound != null && expected.isPresent() && bound.getX() == expected.get().getX() && bound.getZ() == expected.get().getZ();
    }

    // ================================================================== 残破勘测图

    /** 城塔、墓穴两张表的第三池：一抽、12%、只出残破勘测图；各开 2 000 箱，每箱至多一张、合计约 240 张 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void surveyTornPool(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, helper.absoluteVec(new Vec3(1, 1, 1)))
                .create(LootContextParamSets.CHEST);
        for (var id : List.of(HoannaLoot.TOWN_TOWER, HoannaLoot.CATACOMBS)) {
            LootTable table = level.getServer().getLootData().getLootTable(id);
            helper.assertTrue(table != LootTable.EMPTY, id + " 应能加载");
            int maps = 0;
            for (int i = 0; i < 2000; i++) {
                int here = 0;
                for (ItemStack stack : table.getRandomItems(params, (i + 1) * SEED_STEP)) {
                    if (stack.is(ECHoannaSurvey.TORN_SURVEY_MAP.get())) {
                        here += stack.getCount();
                    }
                }
                helper.assertTrue(here <= 1, id + " 一箱至多一张残破勘测图，实际 " + here);
                maps += here;
            }
            helper.assertTrue(maps > 180 && maps < 300, id + " 2 000 箱应开出约 240 张（12%），实际 " + maps);
        }
        helper.succeed();
    }

    /** 三张残破勘测图 + 一颗地脉结晶（摆哪都行）→ 一张海纳尔勘测图；少一张不成 */
    @GameTest(template = EMPTY)
    public static void surveyMapRecipe(GameTestHelper helper) {
        Item torn = ECHoannaSurvey.TORN_SURVEY_MAP.get();
        Item crystal = ECHoannaMaterials.LEY_CRYSTAL.get();
        Optional<CraftingRecipe> ok = craft(helper, new Item[]{Items.AIR, torn, Items.AIR, crystal, Items.AIR, torn, Items.AIR, torn, Items.AIR});
        helper.assertTrue(ok.isPresent(), "三张残破勘测图 + 地脉结晶应有配方");
        ItemStack result = ok.get().getResultItem(helper.getLevel().registryAccess());
        helper.assertTrue(result.is(ECHoannaSurvey.HOANNA_SURVEY_MAP.get()) && result.getCount() == 1, "产物应是 1 张海纳尔勘测图，实际 " + result);
        helper.assertTrue(craft(helper, new Item[]{torn, torn, crystal, Items.AIR, Items.AIR, Items.AIR, Items.AIR, Items.AIR, Items.AIR}).isEmpty(),
                "两张不够");
        helper.assertTrue(craft(helper, new Item[]{torn, torn, torn, torn, Items.AIR, Items.AIR, Items.AIR, Items.AIR, Items.AIR}).isEmpty(),
                "没有地脉结晶不成");
        helper.succeed();
    }

    private static Optional<CraftingRecipe> craft(GameTestHelper helper, Item[] grid) {
        ServerLevel level = helper.getLevel();
        CraftingContainer container = new TransientCraftingContainer(new DummyMenu(), 3, 3);
        for (int i = 0; i < 9; i++) {
            container.setItem(i, grid[i] == Items.AIR ? ItemStack.EMPTY : new ItemStack(grid[i]));
        }
        return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, container, level);
    }

    // ================================================================== 勘测图

    /** 不在海纳尔：右键、潜行右键都不找、不改图；假玩家右键直接放过 */
    @GameTest(template = EMPTY)
    public static void surveyOutsideHoanna(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "survey-out");
        ItemStack stack = new ItemStack(ECHoannaSurvey.HOANNA_SURVEY_MAP.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        try {
            helper.assertTrue(HoannaSurveyMapItem.startSurvey(player, level, stack) == null, "海纳尔外不该发起搜索");
            helper.assertTrue(HoannaSurveyMapItem.cycleKind(player, level, stack) < 0, "海纳尔外不该换种类");
            stack.getItem().use(level, player, InteractionHand.MAIN_HAND);
            helper.assertFalse(SurveySearches.busy(player.getUUID()), "右键以后也不该有搜索在跑");
            helper.assertTrue(stack.getTag() == null || stack.getTag().isEmpty(), "图不该被写上东西：" + stack.getTag());
            InteractionResultHolder<ItemStack> fake = stack.getItem().use(level, ProtectionFrameworkTests.fakePlayer(helper), InteractionHand.MAIN_HAND);
            helper.assertTrue(fake.getResult() == InteractionResult.PASS, "假玩家右键应放过，实际 " + fake.getResult());
        } finally {
            SurveySearches.clear();
        }
        helper.succeed();
    }

    /** 固定种子：右键绑定的点 = 结构预测从玩家脚下找到的最近一座城镇；找完令牌清掉、种类记成城镇 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void surveyBindsNearestTown(GameTestHelper helper) {
        inSurvey(helper, 11 * SEED_STEP, (level, world) -> {
            ServerPlayer player = player(helper, "survey-bind");
            ItemStack stack = townMap();
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            SurveySearches.Job job = HoannaSurveyMapItem.startSurvey(player, level, stack);
            helper.assertTrue(job != null && SurveySearches.busy(player.getUUID()), "海纳尔里右键应发起搜索");
            helper.assertTrue(HoannaSurveyMapItem.startSurvey(player, level, stack) == null, "每人同时只许一个搜索");
            SurveySearches.finishAllForTest(player);
            Optional<BlockPos> want = expected(level, world, player.blockPosition(), RADIUS, chunk -> false);
            helper.assertTrue(want.isPresent(), "这个种子 2 048 格内应有城镇");
            BlockPos bound = HoannaSurveyMapItem.target(stack);
            helper.assertTrue(sameColumn(bound, want), "绑定的点应是预测的最近城镇 " + want + "，实际 " + bound);
            helper.assertTrue(!stack.getTag().contains(HoannaSurveyMapItem.TAG_PENDING)
                    && "hoanna_town".equals(stack.getTag().getString(HoannaSurveyMapItem.TAG_KIND)), "找完令牌清掉、种类是城镇：" + stack.getTag());
            helper.assertTrue(HoannaSurveyMapItem.modelState(stack) == HoannaSurveyMapItem.STATE_BOUND, "绑定后模型是第三态");
            // 图被扔掉（背包里找不到令牌）时结果作废，不报错
            ItemStack other = new ItemStack(ECHoannaSurvey.HOANNA_SURVEY_MAP.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, other);
            HoannaSurveyMapItem.startSurvey(player, level, other);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            SurveySearches.finishAllForTest(player);
            helper.assertTrue(HoannaSurveyMapItem.target(other) == null, "不在身上的图不写结果");
        });
        helper.succeed();
    }

    /** 种类顺序宝库 → 沉城 → 前哨 → 城镇，没注册的跳过；13e 起四种都有：新图默认宝库，潜行右键依次往后、到头绕回，换的时候解绑、作废正在找的 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void surveyCycleSkipsUnregistered(GameTestHelper helper) {
        helper.assertTrue(HoannaSurveyMapItem.nextKind(-1, i -> i == 3) == 3 && HoannaSurveyMapItem.nextKind(3, i -> i == 3) == 3,
                "只有城镇时从头、从城镇往后都是城镇");
        helper.assertTrue(HoannaSurveyMapItem.nextKind(0, i -> i == 1 || i == 3) == 1 && HoannaSurveyMapItem.nextKind(1, i -> i == 1 || i == 3) == 3
                && HoannaSurveyMapItem.nextKind(3, i -> i == 1 || i == 3) == 1, "跳过没有的，到头绕回");
        helper.assertTrue(HoannaSurveyMapItem.nextKind(-1, i -> false) == -1, "一个都没有返回 −1");
        helper.assertTrue(HoannaSurveyMapItem.KINDS.stream().map(HoannaSurveyMapItem.Kind::id).toList()
                        .equals(List.of("sealed_vault", "sunken_district", "inquisitorium_outpost", "hoanna_town")), "种类顺序照 batch13 §6.3");
        inSurvey(helper, 12 * SEED_STEP, (level, world) -> {
            var available = HoannaSurveyMapItem.available(level, world);
            for (int i = 0; i < HoannaSurveyMapItem.KINDS.size(); i++) {
                helper.assertTrue(available.test(i), HoannaSurveyMapItem.KINDS.get(i).id() + " 13e 起已注册，应可找");
            }
            ServerPlayer player = player(helper, "survey-cycle");
            ItemStack stack = new ItemStack(ECHoannaSurvey.HOANNA_SURVEY_MAP.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            helper.assertTrue(HoannaSurveyMapItem.currentKind(stack, available) == 0, "新图默认找宝库");
            helper.assertTrue(HoannaSurveyMapItem.cycleKind(player, level, stack) == 0, "新图第一下潜行右键定成宝库");
            HoannaSurveyMapItem.bind(stack, new BlockPos(100, 0, 100));
            HoannaSurveyMapItem.startSurvey(player, level, stack);
            helper.assertTrue(HoannaSurveyMapItem.cycleKind(player, level, stack) == 1, "再潜行右键换成沉城");
            helper.assertTrue(HoannaSurveyMapItem.target(stack) == null && !SurveySearches.busy(player.getUUID()), "换种类时解绑、作废正在找的");
            helper.assertTrue(HoannaSurveyMapItem.cycleKind(player, level, stack) == 2 && HoannaSurveyMapItem.cycleKind(player, level, stack) == 3
                    && HoannaSurveyMapItem.cycleKind(player, level, stack) == 0, "前哨 → 城镇 → 绕回宝库");
        });
        helper.succeed();
    }

    /** 跳过表：17 处只留后 16 处、不重复；模型三态；最近的那座在跳过表里时绑定第二近的 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void surveySkipList(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ECHoannaSurvey.HOANNA_SURVEY_MAP.get());
        helper.assertTrue(HoannaSurveyMapItem.modelState(stack) == 0F, "新图是第一态");
        for (int i = 0; i < 17; i++) {
            HoannaSurveyMapItem.addSkip(stack, new ChunkPos(i * 40, -i));
        }
        HoannaSurveyMapItem.addSkip(stack, new ChunkPos(16 * 40, -16));
        List<ChunkPos> skipped = HoannaSurveyMapItem.skipped(stack);
        helper.assertTrue(skipped.size() == HoannaSurveyMapItem.SKIP_LIMIT, "跳过表最多 16 条，实际 " + skipped.size());
        helper.assertTrue(!skipped.contains(new ChunkPos(0, 0)) && skipped.get(0).equals(new ChunkPos(40, -1))
                && skipped.get(15).equals(new ChunkPos(16 * 40, -16)), "挤掉的是最早的那条、顺序不变、重复的不再记：" + skipped);
        helper.assertTrue(HoannaSurveyMapItem.modelState(stack) == HoannaSurveyMapItem.STATE_VISITED, "走到过、没绑定是第二态");

        inSurvey(helper, 13 * SEED_STEP, (level, world) -> {
            ServerPlayer player = player(helper, "survey-skip");
            BlockPos center = player.blockPosition();
            Optional<BlockPos> nearest = expected(level, world, center, RADIUS, chunk -> false);
            helper.assertTrue(nearest.isPresent(), "这个种子 2 048 格内应有城镇");
            ChunkPos first = new ChunkPos(nearest.get());
            Optional<BlockPos> second = expected(level, world, center, RADIUS, Set.of(first)::contains);
            ItemStack map = townMap();
            HoannaSurveyMapItem.addSkip(map, first);
            player.setItemInHand(InteractionHand.MAIN_HAND, map);
            HoannaSurveyMapItem.startSurvey(player, level, map);
            SurveySearches.finishAllForTest(player);
            BlockPos bound = HoannaSurveyMapItem.target(map);
            if (second.isPresent()) {
                helper.assertTrue(sameColumn(bound, second), "跳过最近的以后应绑第二近的 " + second + "，实际 " + bound);
                helper.assertFalse(new ChunkPos(bound).equals(first), "跳过表里的不该再选");
            } else {
                helper.assertTrue(bound == null, "只有一座且被跳过时应找不到");
            }
        });
        helper.succeed();
    }

    /** 到点：24 格内发进度、记进跳过表、解绑；远了不算；不在海纳尔不算；arrive_radius 调小跟着变 */
    @GameTest(template = EMPTY)
    public static void surveyArrival(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "survey-arrive");
        BlockPos feet = player.blockPosition();
        ItemStack stack = new ItemStack(ECHoannaSurvey.HOANNA_SURVEY_MAP.get());
        BlockPos near = new BlockPos(feet.getX() + 10, 0, feet.getZ());
        HoannaSurveyMapItem.bind(stack, near);
        helper.assertFalse(HoannaSurveyMapItem.checkArrival(player, stack), "不在海纳尔不算到");
        ECHoanna.setTestHoannaOverride(level);
        try {
            HoannaSurveyMapItem.bind(stack, new BlockPos(feet.getX() + 100, 0, feet.getZ()));
            helper.assertFalse(HoannaSurveyMapItem.checkArrival(player, stack), "100 格外不算到");
            ECHoannaSurvey.Config.setTestOverrides(new ECHoannaSurvey.Config.Values(2048, 4, 0.2D, 3000, 12000, 12000));
            HoannaSurveyMapItem.bind(stack, near);
            helper.assertFalse(HoannaSurveyMapItem.checkArrival(player, stack), "arrive_radius 4 时 10 格不算到");
            ECHoannaSurvey.Config.setTestOverrides(null);
            helper.assertTrue(HoannaSurveyMapItem.checkArrival(player, stack), "默认 24 格内算到");
            helper.assertTrue(HoannaSurveyMapItem.target(stack) == null, "到了以后解绑");
            helper.assertTrue(HoannaSurveyMapItem.skipped(stack).equals(List.of(new ChunkPos(near))), "到过的点记进跳过表：" + HoannaSurveyMapItem.skipped(stack));
            helper.assertFalse(HoannaSurveyMapItem.checkArrival(player, stack), "解绑以后不再重复算到");
            var advancement = level.getServer().getAdvancements().getAdvancement(HoannaSurveyMapItem.ADVANCEMENT_ARRIVE);
            if (advancement != null) {
                helper.assertTrue(player.getAdvancements().getOrStartProgress(advancement).isDone(), "到点应发进度 survey_arrive");
            }
            HoannaSurveyMapItem.bind(stack, near);
            helper.assertFalse(HoannaSurveyMapItem.checkArrival(ProtectionFrameworkTests.fakePlayer(helper), stack), "假玩家不算到");
        } finally {
            ECHoannaSurvey.Config.setTestOverrides(null);
            ECHoanna.clearTestOverride();
        }
        helper.succeed();
    }

    /** 在真的海纳尔世界上发起一次勘测（2 048 格），做完前后已加载区块数一样，而且真看了群系 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void surveyLoadsNoChunks(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel hoanna = server.getLevel(ECHoanna.LEVEL);
        if (hoanna == null) {
            helper.fail("GameTest 服上没有海纳尔世界");
            return;
        }
        ServerPlayer player = player(helper, "survey-chunks");
        player.moveTo(77_777.5D, 80D, -55_555.5D);
        ItemStack stack = new ItemStack(ECHoannaSurvey.HOANNA_SURVEY_MAP.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        try {
            int before = hoanna.getChunkSource().getLoadedChunksCount();
            SurveySearches.Job job = HoannaSurveyMapItem.startSurvey(player, hoanna, stack);
            helper.assertTrue(job != null, "真海纳尔里应能发起搜索");
            SurveySearches.finishAllForTest(player);
            int after = hoanna.getChunkSource().getLoadedChunksCount();
            helper.assertTrue(job.done() && job.samples() > 0, "应找完、看过群系，实际 " + job.samples() + " 个点");
            helper.assertTrue(before == after, "勘测不该加载区块：前 " + before + "、后 " + after);
        } finally {
            SurveySearches.clear();
        }
        helper.succeed();
    }

    /** 两名玩家同一刻都在找：全服只给 20 个点时两人合计 ≤ 20；预算用完再推不动；放开以后各自找到自己最近的 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void surveyBudgetShared(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        inSurvey(helper, 14 * SEED_STEP, (level, world) -> {
            ServerPlayer a = player(helper, "survey-a");
            ServerPlayer b = player(helper, "survey-b");
            b.moveTo(a.getX() + 3000D, a.getY(), a.getZ() - 1500D);
            ItemStack mapA = townMap();
            ItemStack mapB = townMap();
            a.setItemInHand(InteractionHand.MAIN_HAND, mapA);
            b.setItemInHand(InteractionHand.MAIN_HAND, mapB);
            SurveySearches.Job jobA = HoannaSurveyMapItem.startSurvey(a, level, mapA);
            SurveySearches.Job jobB = HoannaSurveyMapItem.startSurvey(b, level, mapB);
            helper.assertTrue(jobA != null && jobB != null, "两人各自能发起");
            try {
                HoannaSearchBudget.resetForTest(server, 20, 1_000_000_000L);
                SurveySearches.stepAllForTest(server);
                int used = jobA.samples() + jobB.samples();
                helper.assertTrue(used > 0 && used <= 20, "同一刻两人合计应 ≤ 20 个点，实际 " + used);
                SurveySearches.stepAllForTest(server);
                helper.assertTrue(jobA.samples() + jobB.samples() == used, "这一刻预算用完了，再推不该多看");
            } finally {
                HoannaSearchBudget.resetForTest(server, HoannaSearchBudget.POINTS_PER_TICK, HoannaSearchBudget.NANOS_PER_TICK);
            }
            SurveySearches.finishAllForTest(a, b);
            helper.assertTrue(sameColumn(HoannaSurveyMapItem.target(mapA), expected(level, world, a.blockPosition(), RADIUS, c -> false))
                    && sameColumn(HoannaSurveyMapItem.target(mapB), expected(level, world, b.blockPosition(), RADIUS, c -> false)),
                    "各自绑到自己最近的城镇：" + HoannaSurveyMapItem.target(mapA) + " / " + HoannaSurveyMapItem.target(mapB));
        });
        helper.succeed();
    }

    // ================================================================== 里程碑

    /** 右键：最近城镇（1 024 格、≤ 16 个采样）的方向距离；冷却每人每块 40 刻；假玩家不理；不改方块 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void milestoneNearestTown(GameTestHelper helper) {
        inSurvey(helper, 15 * SEED_STEP, (level, world) -> {
            ServerPlayer player = player(helper, "milestone");
            BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
            SurveySearches.Job job = RuinMilestoneBlock.startSearch(player, level, pos);
            helper.assertTrue(job != null, "应发起找城镇");
            SurveySearches.finishAllForTest(player);
            int[] asked = {0};
            Optional<BlockPos> want = expected(level, world, pos, RuinMilestoneBlock.RADIUS, chunk -> asked[0]++ >= RuinMilestoneBlock.SAMPLE_CAP);
            helper.assertTrue(job.done() && job.samples() <= RuinMilestoneBlock.SAMPLE_CAP, "最多 16 个采样，实际 " + job.samples());
            helper.assertTrue(job.result().equals(want), "里程碑的结果应同预测：" + job.result() + " / " + want);
            var line = RuinMilestoneBlock.line(pos, job.result());
            String key = ((TranslatableContents) line.getContents()).getKey();
            helper.assertTrue(key.equals(want.isPresent() ? RuinMilestoneBlock.MSG_HEADING : RuinMilestoneBlock.MSG_NOT_FOUND), "动作栏那一句：" + key);
            helper.assertTrue(RuinMilestoneBlock.line(pos, Optional.of(pos.offset(300, 0, -300))).getContents() instanceof TranslatableContents heading
                    && heading.getArgs().length == 2 && heading.getArgs()[1].equals(424), "东北 300 / 300 约 424 格");

            UUID id = UUID.randomUUID();
            helper.assertTrue(RuinMilestoneBlock.tryUse(id, level.dimension(), pos, 1000L), "第一次能用");
            helper.assertFalse(RuinMilestoneBlock.tryUse(id, level.dimension(), pos, 1039L), "39 刻内不行");
            helper.assertTrue(RuinMilestoneBlock.tryUse(id, level.dimension(), pos.east(), 1001L), "冷却按块分开");
            helper.assertTrue(RuinMilestoneBlock.tryUse(id, level.dimension(), pos, 1040L), "40 刻以后又能用");

            helper.setBlock(new BlockPos(1, 1, 1), ECHoannaSurvey.RUIN_MILESTONE.get());
            BlockState state = level.getBlockState(pos);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
            ServerPlayer fake = ProtectionFrameworkTests.fakePlayer(helper);
            helper.assertTrue(state.use(level, fake, InteractionHand.MAIN_HAND, hit) == InteractionResult.PASS && !SurveySearches.busy(fake.getUUID()),
                    "假玩家右键不理");
            ServerPlayer real = player(helper, "milestone-use");
            helper.assertTrue(state.use(level, real, InteractionHand.MAIN_HAND, hit) == InteractionResult.CONSUME && SurveySearches.busy(real.getUUID()),
                    "真玩家右键发起找城镇");
            helper.assertTrue(level.getBlockState(pos) == state, "不改方块");
        });
        helper.succeed();
    }

    /** 这名玩家已有搜索在跑时右键里程碑：不另起一个，动作栏说「还在找上一处」（和勘测图同一句），不再一声不吭 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void milestoneSaysBusy(GameTestHelper helper) {
        inSurvey(helper, 15 * SEED_STEP, (level, world) -> {
            BarPlayer player = new BarPlayer(level, "milestone-busy");
            BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
            try {
                helper.assertTrue(RuinMilestoneBlock.startSearch(player, level, pos) != null, "第一次应发起找城镇");
                helper.assertTrue(player.heard(HoannaSurveyMapItem.MSG_SEARCHING) == 0, "第一次不说还在找");
                helper.assertTrue(RuinMilestoneBlock.startSearch(player, level, pos.east(8)) == null && SurveySearches.busy(player.getUUID()),
                        "已有搜索在跑不另起、原来的照跑");
                helper.assertTrue(player.heard(HoannaSurveyMapItem.MSG_SEARCHING) == 1, "动作栏应说还在找");
            } finally {
                SurveySearches.finishAllForTest(player);
            }
        });
        helper.succeed();
    }

    /** 记下动作栏消息的测试玩家（测试玩家没有网络，发出去的消息只能在这里截下来） */
    private static final class BarPlayer extends ServerPlayer {
        private final List<Component> bar = new ArrayList<>();

        BarPlayer(ServerLevel level, String name) {
            super(level.getServer(), level, new GameProfile(UUID.randomUUID(), name));
            new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND), this);
        }

        @Override
        public void displayClientMessage(Component message, boolean actionBar) {
            if (actionBar) {
                bar.add(message);
            }
            super.displayClientMessage(message, actionBar);
        }

        int heard(String translationKey) {
            return (int) bar.stream().filter(line -> line.getContents() instanceof TranslatableContents t && translationKey.equals(t.getKey())).count();
        }
    }

    // ================================================================== 渊盐

    /** 清的量：20% 夹在 3 000~12 000、不超过剂量；配置填 0；主世界吃也有效、饥饿 I 30 秒、冷却按主世界 gameTime 记 */
    @GameTest(template = EMPTY)
    public static void saltPurge(GameTestHelper helper) {
        helper.assertTrue(AbyssalSaltItem.purgeAmount(50_000, 0.2D, 3000, 12000) == 10_000, "5 万清 1 万");
        helper.assertTrue(AbyssalSaltItem.purgeAmount(100_000, 0.2D, 3000, 12000) == 12_000, "封顶 1.2 万");
        helper.assertTrue(AbyssalSaltItem.purgeAmount(10_000, 0.2D, 3000, 12000) == 3000, "保底 3 000");
        helper.assertTrue(AbyssalSaltItem.purgeAmount(2_000, 0.2D, 3000, 12000) == 2000, "不到 3 000 时清到 0");
        helper.assertTrue(AbyssalSaltItem.purgeAmount(0, 0.2D, 3000, 12000) == 0, "剂量 0 清 0");
        helper.assertTrue(AbyssalSaltItem.purgeAmount(50_000, 0D, 0, 0) == 0 && AbyssalSaltItem.purgeAmount(50_000, 0D, 3000, 12000) == 3000,
                "比例 0 时只剩保底");
        helper.assertTrue(AbyssalSaltItem.purgeAmount(50_000, 0.2D, 5000, 1000) == 5000, "上限比下限小按下限");

        ServerPlayer player = player(helper, "salt-eat");
        helper.assertFalse(ECHoanna.isHoanna(player.level()), "这条在主世界（测试世界）里吃");
        Radiation.set(player, 50_000);
        int purged = AbyssalSaltItem.eat(player);
        helper.assertTrue(purged == 10_000 && Radiation.get(player) == 40_000, "主世界吃也清 20%，实际清 " + purged + "、剩 " + Radiation.get(player));
        MobEffectInstance hunger = player.getEffect(MobEffects.HUNGER);
        helper.assertTrue(hunger != null && hunger.getAmplifier() == 0 && hunger.getDuration() > 580, "饥饿 I 30 秒：" + hunger);
        long now = player.server.overworld().getGameTime();
        helper.assertTrue(data(player).getAbyssalSaltCooldownEnd() == now + 12_000, "冷却到期 = 主世界 gameTime + 12 000，实际 "
                + data(player).getAbyssalSaltCooldownEnd() + " / " + now);
        helper.assertTrue(player.getCooldowns().isOnCooldown(ECHoannaSurvey.ABYSSAL_SALT.get()), "物品冷却也上了");

        data(player).setAbyssalSaltCooldownEnd(0L);
        Radiation.set(player, 2_000);
        AbyssalSaltItem.eat(player);
        helper.assertTrue(Radiation.get(player) == 0, "剂量不到 3 000 时清到 0，实际 " + Radiation.get(player));

        try {
            ECHoannaSurvey.Config.setTestOverrides(new ECHoannaSurvey.Config.Values(2048, 24, 0D, 0, 0, 0));
            ServerPlayer zero = player(helper, "salt-zero");
            Radiation.set(zero, 30_000);
            AbyssalSaltItem.eat(zero);
            helper.assertTrue(Radiation.get(zero) == 30_000 && data(zero).getAbyssalSaltCooldownEnd() == 0L
                    && !zero.getCooldowns().isOnCooldown(ECHoannaSurvey.ABYSSAL_SALT.get()), "全填 0：不清、没冷却");
        } finally {
            ECHoannaSurvey.Config.setTestOverrides(null);
        }
        helper.succeed();
    }

    /** 冷却以玩家数据为准：吃完一颗再吃不下；物品冷却丢了（重新登录）照样吃不下、能补回；存读档后还在；到期以后又能吃；假玩家放过 */
    @GameTest(template = EMPTY)
    public static void saltCooldownPersists(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "salt-cd");
        Item salt = ECHoannaSurvey.ABYSSAL_SALT.get();
        ItemStack stack = new ItemStack(salt, 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Radiation.set(player, 60_000);
        InteractionResultHolder<ItemStack> first = salt.use(level, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(first.getResult().consumesAction() && player.isUsingItem(), "没冷却时能开始吃（饱了也能吃）");
        player.stopUsingItem();
        stack.finishUsingItem(level, player);
        helper.assertTrue(stack.getCount() == 1 && Radiation.get(player) == 48_000, "吃完扣一颗、清 1.2 万，实际 " + stack.getCount() + " / " + Radiation.get(player));

        helper.assertTrue(salt.use(level, player, InteractionHand.MAIN_HAND).getResult() == InteractionResult.FAIL, "冷却中吃不下");
        player.getCooldowns().removeCooldown(salt);
        helper.assertTrue(salt.use(level, player, InteractionHand.MAIN_HAND).getResult() == InteractionResult.FAIL, "物品冷却丢了照样吃不下（以玩家数据为准）");
        player.getCooldowns().removeCooldown(salt);
        helper.assertTrue(AbyssalSaltItem.restoreCooldown(player) > 11_000 && player.getCooldowns().isOnCooldown(salt), "登录时按剩余时间补回物品冷却");
        stack.finishUsingItem(level, player);
        helper.assertTrue(stack.getCount() == 1 && Radiation.get(player) == 48_000, "冷却中吃完也不生效");

        IPlayerData data = data(player);
        if (data instanceof PlayerData saved) {
            PlayerData back = new PlayerData();
            back.deserializeNBT(saved.serializeNBT());
            helper.assertTrue(back.getAbyssalSaltCooldownEnd() == data.getAbyssalSaltCooldownEnd(), "冷却存读档后还在");
        }
        data.setAbyssalSaltCooldownEnd(player.server.overworld().getGameTime() - 1);
        player.getCooldowns().removeCooldown(salt);
        helper.assertTrue(salt.use(level, player, InteractionHand.MAIN_HAND).getResult().consumesAction(), "到期以后又能吃");
        player.stopUsingItem();
        data.setAbyssalSaltCooldownEnd(player.server.overworld().getGameTime() + 10_000_000L);
        helper.assertTrue(AbyssalSaltItem.remainingCooldown(player) == 12_000, "剩余冷却不超过配置的冷却");

        ItemStack fakeStack = new ItemStack(salt, 3);
        var fake = ProtectionFrameworkTests.fakePlayer(helper);
        fake.setItemInHand(InteractionHand.MAIN_HAND, fakeStack);
        helper.assertTrue(salt.use(level, fake, InteractionHand.MAIN_HAND).getResult() == InteractionResult.PASS, "假玩家放过");
        fakeStack.finishUsingItem(level, fake);
        helper.assertTrue(fakeStack.getCount() == 3, "假玩家吃了不扣");
        fake.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.succeed();
    }

    /** 渊盐结壳：铲子掉渊盐 2~4、时运多掉、精准采集掉本体；里程碑掉一块强化石 */
    @GameTest(template = EMPTY)
    public static void saltCrustDrops(GameTestHelper helper) {
        BlockState crust = ECHoannaSurvey.ABYSSAL_SALT_CRUST.get().defaultBlockState();
        ItemStack shovel = new ItemStack(Items.IRON_SHOVEL);
        ItemStack fortune = new ItemStack(Items.IRON_SHOVEL);
        fortune.enchant(Enchantments.BLOCK_FORTUNE, 3);
        ItemStack silk = new ItemStack(Items.IRON_SHOVEL);
        silk.enchant(Enchantments.SILK_TOUCH, 1);
        int plain = 0;
        int lucky = 0;
        for (int i = 0; i < 200; i++) {
            List<ItemStack> drops = drops(helper, crust, shovel, i);
            helper.assertTrue(drops.size() == 1 && drops.get(0).is(ECHoannaSurvey.ABYSSAL_SALT.get())
                    && drops.get(0).getCount() >= 2 && drops.get(0).getCount() <= 4, "不带附魔掉 2~4 颗渊盐：" + drops);
            plain += drops.get(0).getCount();
            for (ItemStack stack : drops(helper, crust, fortune, i)) {
                lucky += stack.getCount();
            }
            List<ItemStack> silky = drops(helper, crust, silk, i);
            helper.assertTrue(silky.size() == 1 && silky.get(0).is(ECHoannaSurvey.ABYSSAL_SALT_CRUST.get().asItem()) && silky.get(0).getCount() == 1,
                    "精准采集掉本体：" + silky);
        }
        helper.assertTrue(lucky > plain * 3 / 2, "时运 III 应明显多掉：" + lucky + " / " + plain);
        List<ItemStack> milestone = drops(helper, ECHoannaSurvey.RUIN_MILESTONE.get().defaultBlockState(), new ItemStack(Items.IRON_PICKAXE), 0);
        helper.assertTrue(milestone.size() == 1 && milestone.get(0).is(ECMaterials.FORTIFIED_STONE.get().asItem()) && milestone.get(0).getCount() == 1,
                "里程碑掉一块强化石：" + milestone);
        helper.succeed();
    }

    private static List<ItemStack> drops(GameTestHelper helper, BlockState state, ItemStack tool, int i) {
        ServerLevel level = helper.getLevel();
        LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, helper.absoluteVec(new Vec3(1, 1, 1)))
                .withParameter(LootContextParams.TOOL, tool).withParameter(LootContextParams.BLOCK_STATE, state).create(LootContextParamSets.BLOCK);
        LootTable table = level.getServer().getLootData().getLootTable(state.getBlock().getLootTable());
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack stack : table.getRandomItems(params, (i + 1) * SEED_STEP)) {
            if (!stack.isEmpty()) {
                out.add(stack);
            }
        }
        return out;
    }

    /** TransientCraftingContainer 要一个菜单通知格子变化，测试里用个空的 */
    private static final class DummyMenu extends AbstractContainerMenu {
        DummyMenu() {
            super(null, 0);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }
}
