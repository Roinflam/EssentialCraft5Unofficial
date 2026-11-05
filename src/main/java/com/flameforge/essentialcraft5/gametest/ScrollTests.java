package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.MRUResistance;
import com.flameforge.essentialcraft5.client.guide.GuideConditions;
import com.flameforge.essentialcraft5.client.guide.GuideSteps;
import com.flameforge.essentialcraft5.config.GuideConfigSnapshot;
import com.flameforge.essentialcraft5.item.AncientScrollItem;
import com.flameforge.essentialcraft5.lore.ScrollChestModifier;
import com.flameforge.essentialcraft5.lore.ScrollEvents;
import com.flameforge.essentialcraft5.lore.ScrollVolumeFunction;
import com.flameforge.essentialcraft5.lore.ScrollVolumes;
import com.flameforge.essentialcraft5.registry.ECHoannaPortal;
import com.flameforge.essentialcraft5.registry.ECScrolls;
import com.flameforge.essentialcraft5.worldgen.ElderMRUCUFeature;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * 源质残卷（第十二批，batch12.md §4.1.10；卷数照定稿的「序 + 卷一～卷六」，卷号 0～6，集齐门开 7 卷 / 门关 5 卷）。
 * <p>
 * 用真的服务端玩家（{@link ProtectionFrameworkTests#realPlayer}）走读的流程、发进度；掉落表要先跑 runData（遗迹表引用的原版地牢表、注入表都在数据里）。
 * 配置一律走测试覆盖值（{@link ECScrolls.Config#setTestOverrides}、{@link ECHoannaPortal.Config#setEnabledForTest}），
 * 在同一段同步代码里设、测、置回，不调 ConfigValue.set。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class ScrollTests {

    private static final String EMPTY = "empty";
    private static final String BATCH = "scrolls";
    /** 掷骰的种子打散：LegacyRandomSource 对挨着的小种子，第一次 nextFloat 几乎一样，乘个大奇数再用 */
    private static final long SEED_MIX = 0x9E3779B97F4A7C15L;
    private static final float EPS = 1.0E-4F;

    private ScrollTests() {
    }

    // ------------------------------------------------------------------ 读

    /** 生存模式手持两张卷三读完：卷三的进度完成、手上剩一张；别的卷没读 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void scrollReadAwardsAndConsumes(GameTestHelper helper) {
        ServerPlayer player = player(helper, "scroll-read", GameType.SURVIVAL);
        ItemStack stack = AncientScrollItem.of(3, 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        ECScrolls.Config.setTestOverrides(ECScrolls.Config.DEFAULTS);
        try {
            InteractionResult start = stack.use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult();
            helper.assertTrue(start == InteractionResult.CONSUME, "没读过的卷应能起手，实际 " + start);
            ItemStack left = stack.finishUsingItem(helper.getLevel(), player);
            helper.assertTrue(ScrollVolumes.isRead(player, 3), "读完应发卷三的进度 " + ScrollVolumes.advancement(3));
            helper.assertTrue(left.getCount() == 1, "读完应用掉一张，剩 " + left.getCount());
            for (int v = 0; v < ScrollVolumes.COUNT; v++) {
                helper.assertTrue(v == 3 || !ScrollVolumes.isRead(player, v), "只读了卷三，第 " + v + " 卷不该算读过");
            }
        } finally {
            ECScrolls.Config.setTestOverrides(null);
            player.stopUsingItem();
        }
        helper.succeed();
    }

    /** 读过的卷再右键：起手失败、不消耗；绕过起手直接读完（同一卷在别处读完了）也不消耗、不重发 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void scrollReadAgainDoesNothing(GameTestHelper helper) {
        ServerPlayer player = player(helper, "scroll-again", GameType.SURVIVAL);
        helper.assertTrue(ScrollVolumes.award(player, 3), "第一次记下卷三应成功");
        helper.assertFalse(ScrollVolumes.award(player, 3), "同一卷第二次记应返回 false");
        ItemStack stack = AncientScrollItem.of(3, 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        ECScrolls.Config.setTestOverrides(ECScrolls.Config.DEFAULTS);
        try {
            InteractionResult start = stack.use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult();
            helper.assertTrue(start == InteractionResult.FAIL, "读过的卷不该起手，实际 " + start);
            helper.assertFalse(player.isUsingItem(), "读过的卷不该进入使用状态");
            ItemStack left = stack.finishUsingItem(helper.getLevel(), player);
            helper.assertTrue(left.getCount() == 2, "读过的卷不该消耗，剩 " + left.getCount());
        } finally {
            ECScrolls.Config.setTestOverrides(null);
        }
        helper.succeed();
    }

    /** 没有 Volume、越界的叫「无字」、不能读；Volume = 0（序）是有效的卷号；创造栏只列七卷 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void scrollBlankNotReadable(GameTestHelper helper) {
        ServerPlayer player = player(helper, "scroll-blank", GameType.SURVIVAL);
        ItemStack blank = new ItemStack(ECScrolls.ANCIENT_SCROLL.get());
        ItemStack outOfRange = new ItemStack(ECScrolls.ANCIENT_SCROLL.get());
        outOfRange.getOrCreateTag().putInt(ScrollVolumes.TAG, ScrollVolumes.COUNT);
        ItemStack negative = new ItemStack(ECScrolls.ANCIENT_SCROLL.get());
        negative.getOrCreateTag().putInt(ScrollVolumes.TAG, -1);
        for (ItemStack stack : List.of(blank, outOfRange, negative)) {
            helper.assertTrue(AncientScrollItem.volume(stack) == -1, "应算无字：" + stack.getTag());
            helper.assertTrue(nameKey(stack.getHoverName()).equals(AncientScrollItem.NAME_BLANK), "无字残卷的名字键不对：" + stack.getTag());
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            InteractionResult result = stack.use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult();
            helper.assertTrue(result == InteractionResult.FAIL, "无字残卷不该起手：" + stack.getTag() + " → " + result);
            ItemStack after = stack.finishUsingItem(helper.getLevel(), player);
            helper.assertTrue(after.getCount() == 1, "无字残卷不该被消耗");
        }
        for (int v = 0; v < ScrollVolumes.COUNT; v++) {
            helper.assertFalse(ScrollVolumes.isRead(player, v), "读无字残卷不该记下任何一卷（第 " + v + " 卷）");
        }
        ItemStack preface = AncientScrollItem.of(0, 1);
        helper.assertTrue(AncientScrollItem.volume(preface) == 0, "Volume 0 是序，应有效");
        helper.assertTrue(nameKey(preface.getHoverName()).equals(AncientScrollItem.NAME_VOLUME), "序的名字应是「源质残卷（序）」那个键");
        helper.assertTrue(ScrollVolumes.advancement(0).equals(EssentialCraft5.id("guide/scrolls/preface"))
                && ScrollVolumes.advancement(6).equals(EssentialCraft5.id("guide/scrolls/book_6")), "进度 id 应是 preface、book_1～book_6");
        // 创造栏（JEI 的物品列表也只收创造栏的）列七卷、每卷一张，不列无字的
        List<ItemStack> variants = ECScrolls.ANCIENT_SCROLL.get().creativeVariants();
        helper.assertTrue(variants.size() == ScrollVolumes.COUNT, "创造栏应列 " + ScrollVolumes.COUNT + " 卷，实际 " + variants.size());
        for (int v = 0; v < variants.size(); v++) {
            helper.assertTrue(AncientScrollItem.volume(variants.get(v)) == v, "创造栏第 " + v + " 张应是卷号 " + v + "：" + variants.get(v).getTag());
        }
        helper.succeed();
    }

    /** 创造模式读完记进度、不消耗 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void scrollCreativeDoesNotConsume(GameTestHelper helper) {
        ServerPlayer player = player(helper, "scroll-creative", GameType.CREATIVE);
        ItemStack stack = AncientScrollItem.of(1, 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        ECScrolls.Config.setTestOverrides(ECScrolls.Config.DEFAULTS);
        try {
            ItemStack left = stack.finishUsingItem(helper.getLevel(), player);
            helper.assertTrue(ScrollVolumes.isRead(player, 1), "创造模式读完也应记下卷一");
            helper.assertTrue(left.getCount() == 1, "创造模式不该消耗，剩 " + left.getCount());
        } finally {
            ECScrolls.Config.setTestOverrides(null);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ 集齐

    /**
     * 门开着：读了六卷还不算集齐；七卷读齐 → 集齐的进度完成；没穿戴任何东西时 MRU 腐化、海纳尔辐射的易伤 1 → 0.9，腐化辐射不变；
     * 集齐只发一次
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void scrollSetCompleteAwardsAndResists(GameTestHelper helper) {
        ServerPlayer player = player(helper, "scroll-set", GameType.SURVIVAL);
        ECScrolls.Config.setTestOverrides(ECScrolls.Config.DEFAULTS);
        ECHoannaPortal.Config.setEnabledForTest(true);
        try {
            for (MRUResistance.Type type : MRUResistance.Type.values()) {
                float before = MRUResistance.vulnerability(player, type);
                helper.assertTrue(Math.abs(before - 1F) < EPS, "什么都没穿时 " + type + " 的易伤应是 1，实际 " + before);
            }
            for (int v = 0; v < ScrollVolumes.COUNT - 1; v++) {
                ScrollVolumes.award(player, v);
            }
            helper.assertFalse(ScrollVolumes.tryAwardComplete(player), "门开着时六卷不该算集齐");
            helper.assertTrue(Math.abs(MRUResistance.vulnerability(player, MRUResistance.Type.MRU_CORRUPTION) - 1F) < EPS, "没集齐不该有抗性");
            ScrollVolumes.award(player, ScrollVolumes.COUNT - 1);
            helper.assertTrue(ScrollVolumes.tryAwardComplete(player), "七卷读齐应发集齐的进度");
            helper.assertTrue(ScrollVolumes.complete(player), "集齐的进度应已完成");
            helper.assertFalse(ScrollVolumes.tryAwardComplete(player), "集齐只发一次");
            float set = (float) ECScrolls.Config.DEFAULTS.setResistance();
            for (MRUResistance.Type type : MRUResistance.Type.values()) {
                float expected = type == MRUResistance.Type.CORRUPTION_RADIATION ? 1F : 1F - set;
                float actual = MRUResistance.vulnerability(player, type);
                helper.assertTrue(Math.abs(actual - expected) < EPS, type + " 的易伤应是 " + expected + "，实际 " + actual);
            }
            ECScrolls.Config.setTestOverrides(new ECScrolls.Config.Values(true, 40, true, 0D));
            helper.assertTrue(Math.abs(MRUResistance.vulnerability(player, MRUResistance.Type.MRU_CORRUPTION) - 1F) < EPS,
                    "set_resistance = 0 时集齐也没有抗性");
        } finally {
            ECScrolls.Config.setTestOverrides(null);
            ECHoannaPortal.Config.setEnabledForTest(null);
        }
        helper.succeed();
    }

    /**
     * 要几卷：纯函数 requiredCount(true) = 7、(false) = 5；门关着时读完序和卷一～卷四就算集齐。
     * 登录判定：门关着、读了五卷但还没发集齐（老存档），登录处理器补发
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void scrollRequiredCountByPortal(GameTestHelper helper) {
        helper.assertTrue(ScrollVolumes.requiredCount(true) == 7 && ScrollVolumes.requiredCount(false) == 5,
                "门开 7 卷、门关 5 卷，实际 " + ScrollVolumes.requiredCount(true) + " / " + ScrollVolumes.requiredCount(false));
        helper.assertTrue(ScrollVolumes.COUNT == 7 && ScrollVolumes.OVERWORLD == 5, "常量 COUNT = 7、OVERWORLD = 5");
        helper.assertTrue(ScrollVolumes.isSetComplete(v -> v < 5, false) && !ScrollVolumes.isSetComplete(v -> v < 5, true),
                "读了序和卷一～卷四：门关着算集齐、门开着不算");
        helper.assertFalse(ScrollVolumes.isSetComplete(v -> v != 0, false), "缺序不算集齐");
        ServerPlayer player = player(helper, "scroll-login", GameType.SURVIVAL);
        ECHoannaPortal.Config.setEnabledForTest(false);
        try {
            for (int v = 0; v < ScrollVolumes.OVERWORLD; v++) {
                ScrollVolumes.award(player, v);
            }
            helper.assertFalse(ScrollVolumes.complete(player), "只记了卷，还没判集齐");
            ScrollEvents.onScrollLogin(new PlayerEvent.PlayerLoggedInEvent(player));
            helper.assertTrue(ScrollVolumes.complete(player), "门关着时读完五卷，登录时应补发集齐");
        } finally {
            ECHoannaPortal.Config.setEnabledForTest(null);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ 掉落

    /**
     * 上古遗迹的箱子表（固定种子掷 400 次）：出过残卷、出过原版地牢的东西、出过注入表里本模组的东西；残卷卷号都在 0～4；
     * 遗迹地物用的就是这张表
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void scrollElderTableHasDungeonAndScroll(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(ElderMRUCUFeature.CHEST_LOOT.equals(EssentialCraft5.id("chests/elder_mrucu")), "遗迹箱子应用 chests/elder_mrucu");
        LootTable table = level.getServer().getLootData().getLootTable(ElderMRUCUFeature.CHEST_LOOT);
        helper.assertTrue(table != LootTable.EMPTY, "chests/elder_mrucu 应能加载");
        LootParams params = chestParams(helper, null);
        int scrolls = 0;
        boolean vanilla = false;
        boolean injected = false;
        TreeSet<Integer> volumes = new TreeSet<>();
        for (int i = 0; i < 400; i++) {
            for (ItemStack stack : table.getRandomItems(params, (i + 1) * SEED_MIX)) {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (stack.is(ECScrolls.ANCIENT_SCROLL.get())) {
                    scrolls++;
                    volumes.add(AncientScrollItem.volume(stack));
                } else if (id != null && id.getNamespace().equals("minecraft")) {
                    vanilla = true;
                } else if (id != null && id.getNamespace().equals(EssentialCraft5.MODID)) {
                    injected = true;
                }
            }
        }
        helper.assertTrue(scrolls > 100 && scrolls < 180, "400 箱应开出约 140 张残卷（35%），实际 " + scrolls);
        helper.assertTrue(vanilla, "应开出过原版地牢的东西");
        helper.assertTrue(injected, "应开出过注入表里本模组的东西（原版地牢表被注入，引用它的表跟着拿到）");
        helper.assertTrue(volumes.first() >= 0 && volumes.last() <= 4, "遗迹只出序和卷一～卷四，实际 " + volumes);
        helper.assertTrue(volumes.size() == 5, "没有玩家时五卷都该出过，实际 " + volumes);
        helper.succeed();
    }

    /** 掉落函数偏向没读过的：读过序和卷一～卷三，掷 1 000 次卷四占 ≥ 75%；没有玩家时五卷各约 20%（± 5%） */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void scrollFunctionPrefersUnread(GameTestHelper helper) {
        ScrollVolumeFunction function = ScrollVolumeFunction.create(0, 4, ScrollVolumeFunction.DEFAULT_PREFER_UNREAD);
        ServerPlayer player = player(helper, "scroll-prefer", GameType.SURVIVAL);
        for (int v = 0; v <= 3; v++) {
            ScrollVolumes.award(player, v);
        }
        RandomSource random = RandomSource.create(20261019L * SEED_MIX);
        int fourth = 0;
        for (int i = 0; i < 1000; i++) {
            int v = function.pick(player, random);
            helper.assertTrue(v >= 0 && v <= 4, "卷号越界：" + v);
            if (v == 4) {
                fourth++;
            }
        }
        helper.assertTrue(fourth >= 750, "读过前四卷，卷四应占 ≥ 75%，实际 " + fourth + " / 1000");
        int[] counts = new int[5];
        for (int i = 0; i < 1000; i++) {
            counts[function.pick(null, random)]++;
        }
        for (int v = 0; v < 5; v++) {
            helper.assertTrue(counts[v] >= 150 && counts[v] <= 250, "没有玩家时每卷约 200 次，第 " + v + " 卷 " + counts[v]);
        }
        helper.succeed();
    }

    /** 海纳尔三张表：城塔只出 0～5、墓穴只出卷五、哨兵被玩家打倒时正好一张卷六，不是玩家打倒的不掉 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void scrollHoannaTablesVolumes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TreeSet<Integer> tower = rollChestVolumes(helper, EssentialCraft5.id("chests/town_tower"), 400);
        TreeSet<Integer> catacombs = rollChestVolumes(helper, EssentialCraft5.id("chests/catacombs"), 200);
        helper.assertTrue(!tower.isEmpty() && tower.first() >= 0 && tower.last() <= 5, "城塔应只出序和卷一～卷五，实际 " + tower);
        helper.assertTrue(catacombs.size() == 1 && catacombs.first() == 5, "墓穴应只出卷五，实际 " + catacombs);
        ServerPlayer killer = player(helper, "scroll-kill", GameType.SURVIVAL);
        LootTable sentinel = level.getServer().getLootData().getLootTable(EssentialCraft5.id("entities/inquisitorium_sentinel"));
        helper.assertTrue(sentinel != LootTable.EMPTY, "哨兵的表应能加载");
        for (int i = 0; i < 20; i++) {
            List<ItemStack> scrolls = sentinel.getRandomItems(entityParams(helper, killer), (i + 1) * SEED_MIX).stream()
                    .filter(s -> s.is(ECScrolls.ANCIENT_SCROLL.get())).toList();
            helper.assertTrue(scrolls.size() == 1 && scrolls.get(0).getCount() == 1 && AncientScrollItem.volume(scrolls.get(0)) == 6,
                    "玩家打倒哨兵应正好掉一张卷六，实际 " + scrolls.stream().map(ItemStack::getTag).toList());
        }
        for (int i = 0; i < 5; i++) {
            boolean any = sentinel.getRandomItems(entityParams(helper, null), (i + 1) * SEED_MIX).stream()
                    .anyMatch(s -> s.is(ECScrolls.ANCIENT_SCROLL.get()));
            helper.assertFalse(any, "不是玩家打倒的哨兵不掉残卷");
        }
        helper.succeed();
    }

    /**
     * 原版箱子（全局战利品修改器 ancient_scroll_chests）：读过序和卷一、卷二的玩家掷地牢箱子 300 次，只开出卷三、卷四各一张，
     * 出过以后不再出；新玩家掷 300 次正好五卷各一张；没有开箱人不出；下界照样出，海纳尔不出
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void scrollVanillaChestsOnlyMissing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer reader = player(helper, "scroll-chest-reader", GameType.SURVIVAL);
        for (int v = 0; v <= 2; v++) {
            ScrollVolumes.award(reader, v);
        }
        ECScrolls.Config.setTestOverrides(ECScrolls.Config.DEFAULTS);
        try {
            List<Integer> got = rollVanillaChest(level, BuiltInLootTables.SIMPLE_DUNGEON, reader, 300);
            helper.assertTrue(got.size() == 2 && got.contains(3) && got.contains(4), "读过 0～2 的人应正好开出卷三、卷四各一张，实际 " + got);
            helper.assertTrue(ScrollVolumes.isGiven(reader, 3) && ScrollVolumes.isGiven(reader, 4) && !ScrollVolumes.isGiven(reader, 0),
                    "开出来的卷应记成掉给过，读过但没掉过的不记");
            helper.assertTrue(rollVanillaChest(level, BuiltInLootTables.ABANDONED_MINESHAFT, reader, 300).isEmpty(), "五卷都有了，箱子不该再出");

            ServerPlayer fresh = player(helper, "scroll-chest-fresh", GameType.SURVIVAL);
            List<Integer> all = rollVanillaChest(level, BuiltInLootTables.SIMPLE_DUNGEON, fresh, 300);
            helper.assertTrue(new TreeSet<>(all).equals(new TreeSet<>(List.of(0, 1, 2, 3, 4))) && all.size() == 5,
                    "新玩家掷 300 次应正好开出序和卷一～卷四各一张，实际 " + all);
            helper.assertTrue(rollVanillaChest(level, BuiltInLootTables.SIMPLE_DUNGEON, null, 300).isEmpty(), "没有开箱人不该出");

            ServerPlayer nether = player(helper, "scroll-chest-nether", GameType.SURVIVAL);
            ServerLevel netherLevel = level.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
            helper.assertTrue(netherLevel != null && !rollVanillaChest(netherLevel, BuiltInLootTables.BASTION_TREASURE, nether, 200).isEmpty(),
                    "下界的箱子应出");
            ServerPlayer hoanna = player(helper, "scroll-chest-hoanna", GameType.SURVIVAL);
            ServerLevel hoannaLevel = level.getServer().getLevel(com.flameforge.essentialcraft5.registry.ECHoanna.LEVEL);
            helper.assertTrue(hoannaLevel == null || rollVanillaChest(hoannaLevel, BuiltInLootTables.SIMPLE_DUNGEON, hoanna, 200).isEmpty(),
                    "海纳尔里的箱子不走这条");
        } finally {
            ECScrolls.Config.setTestOverrides(null);
        }
        helper.assertTrue(ScrollChestModifier.isChestTable(BuiltInLootTables.END_CITY_TREASURE)
                        && ScrollChestModifier.isChestTable(ResourceLocation.fromNamespaceAndPath("somemod", "chests/tower"))
                        && !ScrollChestModifier.isChestTable(EssentialCraft5.id("chests/elder_mrucu"))
                        && !ScrollChestModifier.isChestTable(BuiltInLootTables.SPAWN_BONUS_CHEST)
                        && !ScrollChestModifier.isChestTable(ResourceLocation.withDefaultNamespace("entities/zombie")),
                "只管别人的 chests/ 表，本模组的表、出生奖励箱、生物掉落不算");
        helper.succeed();
    }

    /** 遗迹 / 海纳尔的表开出残卷时也记成掉给过开箱人，之后偏向他没有的卷 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void scrollFunctionMarksGiven(GameTestHelper helper) {
        ServerPlayer player = player(helper, "scroll-given", GameType.SURVIVAL);
        ECScrolls.Config.setTestOverrides(ECScrolls.Config.DEFAULTS);
        try {
            LootTable table = helper.getLevel().getServer().getLootData().getLootTable(EssentialCraft5.id("chests/catacombs"));
            boolean dropped = false;
            for (int i = 0; i < 50 && !dropped; i++) {
                dropped = table.getRandomItems(chestParams(helper, player), (i + 1) * SEED_MIX).stream()
                        .anyMatch(s -> s.is(ECScrolls.ANCIENT_SCROLL.get()));
            }
            helper.assertTrue(dropped, "墓穴箱子 50 次里应开出过卷五");
            helper.assertTrue(ScrollVolumes.isGiven(player, 5) && ScrollVolumes.has(player, 5) && !ScrollVolumes.isRead(player, 5),
                    "开出卷五应记成掉给过（还没读）");
        } finally {
            ECScrolls.Config.setTestOverrides(null);
        }
        ScrollVolumeFunction function = ScrollVolumeFunction.create(4, 5, 1F);
        RandomSource random = RandomSource.create(20261020L * SEED_MIX);
        for (int i = 0; i < 50; i++) {
            helper.assertTrue(function.pick(player, random) == 4, "卷五掉给过了，prefer_unread = 1 时应只抽卷四");
        }
        helper.succeed();
    }

    /**
     * 关掉 general.ancient_scrolls.enabled：掉落函数把残卷的数量设成 0，箱子里不再出；手里剩下的照样能读，
     * 但「古卷」章藏掉了，读完、集齐的提示换成不提手册的那几句
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void scrollDisabledDropsNothing(GameTestHelper helper) {
        ECScrolls.Config.setTestOverrides(new ECScrolls.Config.Values(false, 40, true, 0.1D));
        try {
            TreeSet<Integer> catacombs = rollChestVolumes(helper, EssentialCraft5.id("chests/catacombs"), 100);
            helper.assertTrue(catacombs.isEmpty(), "关掉以后墓穴不该再出残卷，实际 " + catacombs);
            ServerPlayer player = player(helper, "scroll-disabled", GameType.SURVIVAL);
            ItemStack stack = AncientScrollItem.of(2, 1);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            stack.finishUsingItem(helper.getLevel(), player);
            helper.assertTrue(ScrollVolumes.isRead(player, 2) && stack.isEmpty(), "关掉以后手里已有的残卷照样能读、照样消耗");
            ServerPlayer opener = player(helper, "scroll-disabled-chest", GameType.SURVIVAL);
            helper.assertTrue(rollVanillaChest(helper.getLevel(), BuiltInLootTables.SIMPLE_DUNGEON, opener, 200).isEmpty(),
                    "关掉以后原版箱子也不该出");
        } finally {
            ECScrolls.Config.setTestOverrides(null);
        }
        helper.assertTrue(AncientScrollItem.finishedMessageKey(true).equals(AncientScrollItem.MSG_FINISHED)
                && AncientScrollItem.finishedMessageKey(false).equals(AncientScrollItem.MSG_FINISHED_PLAIN), "读完提示：章藏掉时不提手册");
        helper.assertTrue(ScrollVolumes.completeMessageKey(true, true).equals(ScrollVolumes.MSG_COMPLETE_ALL)
                && ScrollVolumes.completeMessageKey(false, true).equals(ScrollVolumes.MSG_COMPLETE_OVERWORLD)
                && ScrollVolumes.completeMessageKey(true, false).equals(ScrollVolumes.MSG_COMPLETE_ALL_PLAIN)
                && ScrollVolumes.completeMessageKey(false, false).equals(ScrollVolumes.MSG_COMPLETE_OVERWORLD_PLAIN), "集齐提示按门和章选");
        helper.succeed();
    }

    // ------------------------------------------------------------------ 手册「古卷」章

    /**
     * 手册 scrolls.json 和代码对得上：各卷条目的 complete、advancement 都是这一卷的进度，中英都写了 locked_hint；
     * 集齐条目的 complete 是集齐的进度、不锁；可选步骤门开着 8 步、门关着 6 步（卷五、卷六随门藏掉）
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void scrollGuideChapterMatchesCode(GameTestHelper helper) {
        JsonObject zh = chapter(helper, "zh_cn");
        JsonObject en = chapter(helper, "en_us");
        if (zh == null || en == null) {
            return;
        }
        Map<String, JsonObject> enById = new HashMap<>();
        for (JsonObject e : entries(en)) {
            enById.put(str(e, "id"), e);
        }
        String[] ids = {"preface", "book_1", "book_2", "book_3", "book_4", "book_5", "book_6"};
        Map<String, JsonObject> zhById = new HashMap<>();
        for (JsonObject e : entries(zh)) {
            zhById.put(str(e, "id"), e);
        }
        for (int v = 0; v < ScrollVolumes.COUNT; v++) {
            JsonObject e = zhById.get(ids[v]);
            helper.assertTrue(e != null, "古卷章缺条目 " + ids[v]);
            String adv = ScrollVolumes.advancement(v).toString();
            helper.assertTrue(adv.equals(str(e, "complete")) && adv.equals(str(e, "advancement")),
                    ids[v] + " 的 complete / advancement 应都是 " + adv);
            helper.assertTrue(str(e, "locked_hint") != null && enById.get(ids[v]) != null && str(enById.get(ids[v]), "locked_hint") != null,
                    ids[v] + " 中英都要写 locked_hint");
        }
        JsonObject set = zhById.get("complete_set");
        helper.assertTrue(set != null && ScrollVolumes.COMPLETE.toString().equals(str(set, "complete")) && str(set, "advancement") == null,
                "complete_set 的 complete 应是 " + ScrollVolumes.COMPLETE + "、不锁");
        helper.assertTrue(steps(zh, true) == 8 && steps(zh, false) == 6,
                "可选步骤门开 8 步、门关 6 步，实际 " + steps(zh, true) + " / " + steps(zh, false));
        helper.succeed();
    }

    // ------------------------------------------------------------------ 工具

    /** 真玩家：过掉出生无敌、设游戏模式，站在结构里 */
    private static ServerPlayer player(GameTestHelper helper, String name, GameType mode) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        player.setGameMode(mode);
        return player;
    }

    private static String nameKey(Component name) {
        return name.getContents() instanceof TranslatableContents contents ? contents.getKey() : "";
    }

    private static LootParams chestParams(GameTestHelper helper, @Nullable ServerPlayer opener) {
        return new LootParams.Builder(helper.getLevel())
                .withParameter(LootContextParams.ORIGIN, helper.absoluteVec(new Vec3(1, 1, 1)))
                .withOptionalParameter(LootContextParams.THIS_ENTITY, opener)
                .create(LootContextParamSets.CHEST);
    }

    private static LootParams entityParams(GameTestHelper helper, @Nullable ServerPlayer killer) {
        ServerLevel level = helper.getLevel();
        Husk victim = EntityType.HUSK.create(level);
        if (victim == null) {
            throw new IllegalStateException("建不出尸壳");
        }
        Vec3 at = helper.absoluteVec(new Vec3(2.5D, 1D, 2.5D));
        victim.moveTo(at.x, at.y, at.z);
        return new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, victim)
                .withParameter(LootContextParams.ORIGIN, at)
                .withParameter(LootContextParams.DAMAGE_SOURCE, killer != null ? level.damageSources().playerAttack(killer) : level.damageSources().generic())
                .withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer)
                .withOptionalParameter(LootContextParams.KILLER_ENTITY, killer)
                .create(LootContextParamSets.ENTITY);
    }

    /** 掷一张箱子表 rolls 次（没有开箱人），开出来的残卷卷号 */
    private static TreeSet<Integer> rollChestVolumes(GameTestHelper helper, ResourceLocation id, int rolls) {
        LootTable table = helper.getLevel().getServer().getLootData().getLootTable(id);
        helper.assertTrue(table != LootTable.EMPTY, id + " 应能加载");
        LootParams params = chestParams(helper, null);
        TreeSet<Integer> volumes = new TreeSet<>();
        for (int i = 0; i < rolls; i++) {
            for (ItemStack stack : table.getRandomItems(params, (i + 1) * SEED_MIX)) {
                if (stack.is(ECScrolls.ANCIENT_SCROLL.get())) {
                    volumes.add(AncientScrollItem.volume(stack));
                }
            }
        }
        return volumes;
    }

    /** 在 level 里以 opener 开箱掷原版箱子表 rolls 次（走 Forge 的全局战利品修改器），按顺序列出开出来的残卷卷号 */
    private static List<Integer> rollVanillaChest(ServerLevel level, ResourceLocation id, @Nullable ServerPlayer opener, int rolls) {
        LootTable table = level.getServer().getLootData().getLootTable(id);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(level.getSharedSpawnPos()))
                .withOptionalParameter(LootContextParams.THIS_ENTITY, opener)
                .create(LootContextParamSets.CHEST);
        List<Integer> volumes = new ArrayList<>();
        for (int i = 0; i < rolls; i++) {
            for (ItemStack stack : table.getRandomItems(params, (i + 1) * SEED_MIX)) {
                if (stack.is(ECScrolls.ANCIENT_SCROLL.get())) {
                    volumes.add(AncientScrollItem.volume(stack));
                }
            }
        }
        return volumes;
    }

    @Nullable
    private static JsonObject chapter(GameTestHelper helper, String language) {
        Path file = ModList.get().getModFileById(EssentialCraft5.MODID).getFile()
                .findResource("assets", EssentialCraft5.MODID, "guide", language, "scrolls.json");
        if (!Files.isRegularFile(file)) {
            helper.fail("jar 里没有 guide/" + language + "/scrolls.json");
            return null;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            helper.fail("读不了 guide/" + language + "/scrolls.json：" + e);
            return null;
        }
    }

    private static List<JsonObject> entries(JsonObject category) {
        List<JsonObject> out = new ArrayList<>();
        if (category.get("entries") instanceof JsonArray array) {
            for (JsonElement e : array) {
                if (e.isJsonObject()) {
                    out.add(e.getAsJsonObject());
                }
            }
        }
        return out;
    }

    @Nullable
    private static String str(JsonObject o, String key) {
        JsonElement el = o.get(key);
        return el != null && el.isJsonPrimitive() ? el.getAsString() : null;
    }

    /** 照手册界面数这一章的步骤（条件不成立的条目不算）：门开没开换成 portal，其余用真实配置快照 */
    private static int steps(JsonObject category, boolean portal) {
        Map<String, String> snapshot = new HashMap<>(GuideConfigSnapshot.capture());
        snapshot.put("general.hoanna.portal_enabled", Boolean.toString(portal));
        snapshot.put("general.ancient_scrolls.enabled", "true");
        List<String> warnings = new ArrayList<>();
        GuideConditions cond = new GuideConditions(snapshot, false, (key, message) -> warnings.add(message));
        if (!cond.test(GuideConditions.parse(category.get("if"), () -> warnings.add("分类 if 写坏了")))) {
            return -1;
        }
        GuideSteps.Tally tally = new GuideSteps.Tally();
        for (JsonObject e : entries(category)) {
            if (!cond.test(GuideConditions.parse(e.get("if"), () -> warnings.add(str(e, "id") + " 的 if 写坏了")))) {
                continue;
            }
            boolean step = str(e, "complete") != null;
            boolean optional = GuideSteps.OptionalRule.parse(e.get("optional"), warnings::add).test(cond::test);
            tally.add(step, step && !optional, false);
        }
        return warnings.isEmpty() ? tally.steps() : -2;
    }
}
