package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.command.DemonStandingCommands;
import com.flameforge.essentialcraft5.demon.DemonStanding;
import com.flameforge.essentialcraft5.demon.DemonTrades;
import com.flameforge.essentialcraft5.demon.DemonWant;
import com.flameforge.essentialcraft5.entity.DemonEntity;
import com.flameforge.essentialcraft5.item.CapturedSoulItem;
import com.flameforge.essentialcraft5.menu.DemonTradeMenu;
import com.flameforge.essentialcraft5.player.GuideMigration;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECDemon;
import com.flameforge.essentialcraft5.registry.ECDemonic;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.advancements.Advancement;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 第十三批 13a「契约之誉」的 GameTest（batch13.md §3.4、§12.2，D19 A）：
 * <ul>
 *   <li>三档门槛 8 / 20 / 40，关掉时各档都不起作用但照样记数；成交 +1、打发走不加；死亡保留。</li>
 *   <li>熟客：召出的恶魔开两样，第二样是贵价（数量 ×1.5 向上取整），只从乘完还放得进一组的条目里抽、和第一样不同种；整池都乘不下才改要
 *       2 个任意被俘灵魂（实际要同种的两个）；两样交哪样都成交，两样都对得上同一堆时按便宜的那样扣。</li>
 *   <li>契主：每笔多给 30 点经验（按成交前的声望），阿克罗尼特照旧。</li>
 *   <li>血契：潜行、主手或副手拿着记着实体的被俘灵魂右键，交一个灵魂重抽一次（创造模式不扣）；第二次不扣不换；不够档、没潜行、空灵魂都照常开交易界面；
 *       不是召唤者的生存玩家被拒；别人替召唤者重抽时开几样跟着召唤者。</li>
 *   <li>要价列表存盘（连同换没换过），旧存档的单条 {@code want} 读成一样要价；老存档按迁移表 v3 补下限（端到端）、只抬不降；指令走指令树能查能改。</li>
 * </ul>
 * 契约之誉开关、交易池、报酬都用测试覆盖值，在同一段同步代码里设、测、置回；恶魔在测试结束前清掉。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class DemonStandingTests {

    private static final String LARGE = "empty_large";
    private static final String EMPTY = "empty";
    private static final Vec3 PLAYER_AT = new Vec3(4.5D, 2D, 5.5D);
    private static final Vec3 DEMON_AT = new Vec3(5.5D, 2D, 5.5D);
    private static final ResourceLocation VILLAGER = ResourceLocation.withDefaultNamespace("villager");

    private DemonStandingTests() {
    }

    // ================================================================ 共用

    private static ServerPlayer player(GameTestHelper helper, String name, int standing) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        Vec3 abs = helper.absoluteVec(PLAYER_AT);
        player.moveTo(abs.x, abs.y, abs.z, 0F, 0F);
        PlayerDataEvents.get(player).setDemonStanding(standing);
        return player;
    }

    private static int standing(Player player) {
        return PlayerDataEvents.get(player).getDemonStanding();
    }

    /** 放进世界的恶魔（清怪关掉），召唤者是 summoner，开 wants 这几样要价 */
    private static DemonEntity demon(GameTestHelper helper, Player summoner, DemonWant... wants) {
        ServerLevel level = helper.getLevel();
        DemonEntity demon = ECDemon.DEMON.get().create(level);
        if (demon == null) {
            throw new IllegalStateException("建不出恶魔");
        }
        Vec3 abs = helper.absoluteVec(DEMON_AT);
        demon.moveTo(abs.x, abs.y, abs.z, 0F, 0F);
        demon.setTestClearOverrides(0, null);
        demon.setSummoner(summoner.getUUID());
        demon.setWants(List.of(wants));
        level.addFreshEntity(demon);
        return demon;
    }

    private static DemonWant iron(int count) {
        return DemonWant.item(Items.IRON_BLOCK, count, false);
    }

    private static DemonTrades.Entry ironEntry(int count) {
        return DemonTrades.Entry.item(Items.IRON_BLOCK, count, false, 1);
    }

    /** 开着契约之誉、换上交易池和报酬跑 body，最后全部置回 */
    private static void standingCase(@Nullable List<DemonTrades.Entry> pool, Runnable body) {
        DemonStanding.setTestEnabledOverride(true);
        DemonTrades.setTestOverride(pool);
        ECDemon.Config.setTestOverrides(null, 5, 5);
        try {
            body.run();
        } finally {
            ECDemon.Config.setTestOverrides(null, null, null);
            DemonTrades.setTestOverride(null);
            DemonStanding.setTestEnabledOverride(null);
        }
    }

    /** 打开交易界面、把 offer 放进投放格，关界面（剩下的退回背包） */
    private static void trade(ServerPlayer player, DemonEntity demon, ItemStack offer) {
        DemonTradeMenu menu = new DemonTradeMenu(1, player.getInventory(), demon);
        menu.setCarried(offer);
        menu.clicked(0, 0, ClickType.PICKUP, player);
        menu.removed(player);
    }

    private static int ingots(Player player) {
        return player.getInventory().countItem(ECDemonic.ACKRONITE_INGOT.get());
    }

    private static int souls(Player player) {
        return player.getInventory().countItem(ECDemonic.CAPTURED_SOUL.get());
    }

    private static void award(GameTestHelper helper, ServerPlayer player, ResourceLocation id) {
        Advancement advancement = helper.getLevel().getServer().getAdvancements().getAdvancement(id);
        helper.assertTrue(advancement != null, "应有进度 " + id);
        List<String> remaining = new ArrayList<>();
        player.getAdvancements().getOrStartProgress(advancement).getRemainingCriteria().forEach(remaining::add);
        remaining.forEach(criterion -> player.getAdvancements().award(advancement, criterion));
    }

    private static String key(MutableComponent component) {
        return component.getContents() instanceof TranslatableContents contents ? contents.getKey() : component.getString();
    }

    // ================================================================ 档位

    /** 三档门槛照默认 8 / 20 / 40；只写数 / 带档名两种说法；关掉时都不够（NONE 永远够） */
    @GameTest(template = EMPTY)
    public static void demonStandingTiers(GameTestHelper helper) {
        standingCase(null, () -> {
            helper.assertTrue(DemonStanding.Tier.REGULAR.threshold() == 8 && DemonStanding.Tier.PATRON.threshold() == 20
                    && DemonStanding.Tier.BLOOD.threshold() == 40, "门槛应是 8 / 20 / 40");
            helper.assertTrue(DemonStanding.tier(7) == DemonStanding.Tier.NONE && DemonStanding.tier(8) == DemonStanding.Tier.REGULAR
                    && DemonStanding.tier(19) == DemonStanding.Tier.REGULAR && DemonStanding.tier(20) == DemonStanding.Tier.PATRON
                    && DemonStanding.tier(39) == DemonStanding.Tier.PATRON && DemonStanding.tier(40) == DemonStanding.Tier.BLOOD, "按门槛分档");
            helper.assertTrue(key(DemonStanding.label(7)).equals("gui.essentialcraft5.demon_standing.value"), "没到档只写数");
            helper.assertTrue(key(DemonStanding.label(23)).equals("gui.essentialcraft5.demon_standing.value_tier"), "到档带档名");
            helper.assertTrue(key(DemonStanding.fullLabel(7)).equals("gui.essentialcraft5.demon_standing.value_full")
                    && key(DemonStanding.fullLabel(23)).equals("gui.essentialcraft5.demon_standing.value_full_tier"), "完整说法另有一套键");
            DemonStanding.setTestEnabledOverride(false);
            helper.assertTrue(DemonStanding.tier(99) == DemonStanding.Tier.NONE && !DemonStanding.atLeast(99, DemonStanding.Tier.REGULAR),
                    "关掉时哪一档都不算");
            helper.assertTrue(DemonStanding.atLeast(0, DemonStanding.Tier.NONE), "NONE 永远够");
        });
        helper.succeed();
    }

    // ================================================================ 熟客：两样要价

    /**
     * 不到熟客（7）、没有召唤者都只开一样；熟客（8）开两样，第二样是贵价（铁块 8 → 12）。
     * 真的交易池（默认 45 条，23 条乘不下）：第二样从不是任意灵魂，物品放得进一组。
     */
    @GameTest(template = EMPTY)
    public static void demonRegularGetsTwoPrices(GameTestHelper helper) {
        ServerPlayer newcomer = player(helper, "ec5-new", 7);
        ServerPlayer regular = player(helper, "ec5-regular", 8);
        standingCase(List.of(ironEntry(8)), () -> {
            helper.assertTrue(DemonTrades.rollFor(null, RandomSource.create(1L)).equals(List.of(iron(8))), "没有召唤者：一样");
            helper.assertTrue(DemonTrades.rollFor(newcomer, RandomSource.create(1L)).equals(List.of(iron(8))), "声望 7：一样");
            List<DemonWant> two = DemonTrades.rollFor(regular, RandomSource.create(1L));
            helper.assertTrue(two.equals(List.of(iron(8), iron(12))), "熟客：铁块 8 或 12（池里只有一种时第二样也是它），实际 " + two);
            DemonStanding.setTestEnabledOverride(false);
            helper.assertTrue(DemonTrades.rollFor(regular, RandomSource.create(1L)).size() == 1, "关掉契约之誉：熟客也只开一样");
        });
        standingCase(null, () -> {
            helper.assertTrue(DemonTrades.entries().size() >= 40, "前提：读到了默认交易池，实际 " + DemonTrades.entries().size() + " 条");
            for (long i = 0; i < 300; i++) {
                List<DemonWant> wants = DemonTrades.rollFor(regular, RandomSource.create(i * 0x9E3779B97F4A7C15L));
                helper.assertTrue(wants.size() == 2, "真的交易池：熟客每次都开两样");
                DemonWant second = wants.get(1);
                helper.assertFalse(second.isAnySoul(), "真的交易池：第二样不该是任意灵魂，实际 " + second);
                helper.assertTrue(second.isSoul() || second.count() <= DemonWant.deliverLimit(second.item()),
                        "第二样一次交得上（已填充地图这类只算 1 件），实际 " + second);
            }
        });
        helper.succeed();
    }

    /** 贵价换算：×1.5 向上取整；放不进一组（TNT 64、手枪 1）改要 2 个任意被俘灵魂；灵魂先掷再乘，夹到 64；倍数 1 等于原价 */
    @GameTest(template = EMPTY)
    public static void demonPremiumConversions(GameTestHelper helper) {
        RandomSource random = RandomSource.create(7L);
        double factor = DemonStanding.Config.premiumMultiplier();
        helper.assertTrue(factor == 1.5D, "默认贵价倍数 1.5，实际 " + factor);
        helper.assertTrue(DemonTrades.premium(ironEntry(8), random, factor).equals(iron(12)), "铁块 8 → 12");
        helper.assertTrue(DemonTrades.premium(ironEntry(5), random, factor).equals(iron(8)), "铁块 5 → 8（7.5 向上取整）");
        DemonWant tnt = DemonTrades.premium(DemonTrades.Entry.item(Items.TNT, 64, false, 1), random, factor);
        helper.assertTrue(tnt.isAnySoul() && tnt.count() == DemonTrades.PREMIUM_SOUL_COUNT, "TNT 64 → 2 个任意被俘灵魂，实际 " + tnt);
        Item pistol = ForgeRegistries.ITEMS.getValue(EssentialCraft5.id("pistol"));
        helper.assertTrue(pistol != null && new ItemStack(pistol).getMaxStackSize() == 1, "前提：手枪一组 1 个");
        DemonWant gun = DemonTrades.premium(DemonTrades.Entry.item(pistol, 1, true, 1), random, factor);
        helper.assertTrue(gun.isAnySoul() && gun.count() == 2, "手枪 → 2 个任意被俘灵魂，实际 " + gun);
        DemonWant souls = DemonTrades.premium(DemonTrades.Entry.soul(VILLAGER, 4, 4, 1), random, factor);
        helper.assertTrue(souls.equals(DemonWant.soul(VILLAGER, 6)), "村民灵魂 4 → 6，实际 " + souls);
        DemonWant many = DemonTrades.premium(DemonTrades.Entry.soul(VILLAGER, 50, 50, 1), random, factor);
        helper.assertTrue(many.count() == DemonTrades.MAX_SOUL_COUNT, "灵魂 50 × 1.5 夹到 64，实际 " + many.count());
        helper.assertTrue(DemonTrades.premium(ironEntry(8), random, 1D).equals(iron(8)), "倍数 1：和原价一样");
        helper.succeed();
    }

    /**
     * 第二样只从乘得下的条目里抽：池 [TNT 64, 铁块 8] 时第二样总是铁块 12（TNT 乘完一组放不下，不进贵价池，第一样是 TNT 时也一样）；
     * 池 [手枪, 村民灵魂 4] 时第二样总是村民灵魂 6（灵魂条目总乘得下）；整池都乘不下（只有手枪）才改要 2 个任意灵魂。
     */
    @GameTest(template = EMPTY)
    public static void demonPremiumSkipsOversized(GameTestHelper helper) {
        ServerPlayer regular = player(helper, "ec5-premium", 8);
        double factor = DemonStanding.Config.premiumMultiplier();
        DemonTrades.Entry tnt = DemonTrades.Entry.item(Items.TNT, 64, false, 1);
        Item pistol = ForgeRegistries.ITEMS.getValue(EssentialCraft5.id("pistol"));
        helper.assertTrue(pistol != null && new ItemStack(pistol).getMaxStackSize() == 1, "前提：手枪一组 1 个");
        DemonTrades.Entry gun = DemonTrades.Entry.item(pistol, 1, true, 1);
        DemonTrades.Entry villagers = DemonTrades.Entry.soul(VILLAGER, 4, 4, 1);
        helper.assertFalse(DemonTrades.premiumFits(tnt, factor) || DemonTrades.premiumFits(gun, factor), "TNT 64、手枪乘完放不进一组");
        helper.assertTrue(DemonTrades.premiumFits(ironEntry(8), factor) && DemonTrades.premiumFits(DemonTrades.Entry.soul(VILLAGER, 50, 50, 1), factor),
                "铁块 8、灵魂条目乘得下");
        standingCase(List.of(tnt, ironEntry(8)), () -> {
            int tntFirst = 0;
            for (long i = 0; i < 40; i++) {
                List<DemonWant> wants = DemonTrades.rollFor(regular, RandomSource.create(i * 0x9E3779B97F4A7C15L));
                helper.assertTrue(wants.size() == 2 && wants.get(1).equals(iron(12)), "第二样总是铁块 12，实际 " + wants);
                if (wants.get(0).item() == Items.TNT) {
                    tntFirst++;
                }
            }
            helper.assertTrue(tntFirst > 0, "前提：40 次里第一样抽到过 TNT");
        });
        standingCase(List.of(gun, villagers), () -> {
            for (long i = 0; i < 20; i++) {
                List<DemonWant> wants = DemonTrades.rollFor(regular, RandomSource.create(i * 0x9E3779B97F4A7C15L));
                helper.assertTrue(wants.size() == 2 && wants.get(1).equals(DemonWant.soul(VILLAGER, 6)),
                        "手枪乘不下：第二样总是村民灵魂 4 × 1.5 = 6，实际 " + wants);
            }
        });
        standingCase(List.of(gun), () -> {
            List<DemonWant> wants = DemonTrades.rollFor(regular, RandomSource.create(5L));
            helper.assertTrue(wants.size() == 2 && wants.get(1).isAnySoul() && wants.get(1).count() == DemonTrades.PREMIUM_SOUL_COUNT,
                    "整池都乘不下：第二样改要 2 个任意灵魂，实际 " + wants);
        });
        helper.succeed();
    }

    /**
     * 已填充地图、玩家头颅、成书（{@code essentialcraft5:demon_unstackable}）每件 NBT 都不一样，叠不进同一个投放格，一次只算 1 件：
     * 不进贵价池（地图 1 × 1.5 原来成了「× 2」，两张不同的地图放不进一格，永远交不上）；池里只有地图时第二样改要任意灵魂；
     * 交易池写了 3 张也只掷出 1 张；存档里留着的「地图 × 2」读回来夹成 1 张。
     */
    @GameTest(template = EMPTY)
    public static void demonPremiumSkipsUnstackable(GameTestHelper helper) {
        ServerPlayer regular = player(helper, "ec5-premium-map", 8);
        double factor = DemonStanding.Config.premiumMultiplier();
        for (Item item : List.of(Items.FILLED_MAP, Items.PLAYER_HEAD, Items.WRITTEN_BOOK)) {
            helper.assertTrue(DemonWant.deliverLimit(item) == 1, item + " 一次只交得上 1 件");
            helper.assertFalse(DemonTrades.premiumFits(DemonTrades.Entry.item(item, 1, false, 1), factor), item + " 不进贵价池");
        }
        helper.assertTrue(DemonWant.deliverLimit(Items.IRON_BLOCK) == 64, "铁块照一组 64 算");
        DemonTrades.Entry map = DemonTrades.Entry.item(Items.FILLED_MAP, 1, false, 1);
        standingCase(List.of(map, ironEntry(8)), () -> {
            for (long i = 0; i < 40; i++) {
                List<DemonWant> wants = DemonTrades.rollFor(regular, RandomSource.create(i * 0x9E3779B97F4A7C15L));
                helper.assertTrue(wants.size() == 2 && wants.get(1).equals(iron(12)), "第二样总是铁块 12、不会是地图 × 2，实际 " + wants);
            }
        });
        standingCase(List.of(map), () -> {
            List<DemonWant> wants = DemonTrades.rollFor(regular, RandomSource.create(5L));
            helper.assertTrue(wants.size() == 2 && wants.get(0).equals(DemonWant.item(Items.FILLED_MAP, 1, false)) && wants.get(1).isAnySoul(),
                    "池里只有地图：第一样地图 × 1，第二样改要任意灵魂，实际 " + wants);
        });
        DemonWant three = DemonTrades.Entry.item(Items.FILLED_MAP, 3, false, 1).roll(RandomSource.create(1L));
        helper.assertTrue(three.count() == 1, "交易池写了 3 张地图也只掷出 1 张，实际 " + three);
        DemonWant loaded = DemonWant.load(new DemonWant(Items.FILLED_MAP, null, 2, false).save());
        helper.assertTrue(loaded != null && loaded.count() == 1, "存档里的地图 × 2 读回来夹成 1 张，实际 " + loaded);
        helper.succeed();
    }

    /** 池里有两种时，第二样总和第一样不同种；第二样总是贵价 */
    @GameTest(template = EMPTY)
    public static void demonSecondPriceDiffers(GameTestHelper helper) {
        ServerPlayer regular = player(helper, "ec5-regular", 8);
        DemonTrades.Entry gold = DemonTrades.Entry.item(Items.GOLD_BLOCK, 4, false, 1);
        standingCase(List.of(ironEntry(8), gold), () -> {
            int differ = 0;
            for (long seed = 0; seed < 20; seed++) {
                List<DemonWant> wants = DemonTrades.rollFor(regular, RandomSource.create(seed));
                helper.assertTrue(wants.size() == 2, "熟客开两样");
                DemonWant second = wants.get(1);
                helper.assertTrue(second.equals(iron(12)) || second.equals(DemonWant.item(Items.GOLD_BLOCK, 6, false)),
                        "第二样是贵价（铁块 12 或金块 6），实际 " + second);
                if (wants.get(0).item() != second.item()) {
                    differ++;
                }
            }
            helper.assertTrue(differ == 20, "20 次里第二样都和第一样不同，实际 " + differ);
        });
        helper.succeed();
    }

    /** 两样交哪样都成交：投放格两样都收、上限按对上的那样；2 个村民灵魂（任意灵魂那样）成交，8 个铁块也成交 */
    @GameTest(template = LARGE)
    public static void demonTradeEitherPrice(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-either", 8);
        standingCase(null, () -> {
            DemonEntity first = demon(helper, player, iron(8), DemonWant.anySoul(2));
            DemonEntity second = demon(helper, player, iron(8), DemonWant.anySoul(2));
            try {
                DemonTradeMenu menu = new DemonTradeMenu(1, player.getInventory(), first);
                ItemStack villager = CapturedSoulItem.of(EntityType.VILLAGER, 5);
                helper.assertTrue(menu.getSlot(0).mayPlace(new ItemStack(Items.IRON_BLOCK)) && menu.getSlot(0).mayPlace(villager),
                        "投放格收铁块，也收任意灵魂");
                helper.assertFalse(menu.getSlot(0).mayPlace(new ItemStack(ECDemonic.CAPTURED_SOUL.get())), "空灵魂不收");
                helper.assertFalse(menu.getSlot(0).mayPlace(new ItemStack(Items.GOLD_BLOCK)), "金块不收");
                helper.assertTrue(menu.getSlot(0).getMaxStackSize(new ItemStack(Items.IRON_BLOCK)) == 8
                        && menu.getSlot(0).getMaxStackSize(villager) == 2, "上限按对上的那样：铁块 8、灵魂 2");
                menu.setCarried(villager);
                menu.clicked(0, 0, ClickType.PICKUP, player);
                helper.assertTrue(first.isRemoved(), "交 2 个村民灵魂应成交");
                helper.assertTrue(menu.getCarried().getCount() == 3, "多的 3 个灵魂还在手上，实际 " + menu.getCarried());
                menu.removed(player);

                trade(player, second, new ItemStack(Items.IRON_BLOCK, 8));
                helper.assertTrue(second.isRemoved(), "交 8 个铁块也成交");
                helper.assertTrue(standing(player) == 10, "两笔 +2，实际 " + standing(player));
            } finally {
                first.discard();
                second.discard();
            }
        });
        helper.succeed();
    }

    /**
     * 两样都对得上同一堆时按便宜的那样扣：要价「村民灵魂 ×6」或「任意灵魂 ×2」，一次放进 10 个村民灵魂只扣 2 个（格子上限也按 2）；
     * Shift 点进同样只扣 2 个（多放进格的关界面退回）；同一种东西贵的排在前面（铁块 12 或 8）时，放进 12 个只扣 8 个。
     */
    @GameTest(template = LARGE)
    public static void demonOfferPaysCheapest(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-cheap", 8);
        standingCase(null, () -> {
            DemonEntity click = demon(helper, player, DemonWant.soul(VILLAGER, 6), DemonWant.anySoul(2));
            DemonEntity shift = demon(helper, player, DemonWant.soul(VILLAGER, 6), DemonWant.anySoul(2));
            DemonEntity irons = demon(helper, player, iron(12), iron(8));
            try {
                DemonTradeMenu menu = new DemonTradeMenu(1, player.getInventory(), click);
                ItemStack ten = CapturedSoulItem.of(EntityType.VILLAGER, 10);
                helper.assertTrue(menu.getSlot(0).getMaxStackSize(ten) == 2,
                        "两样都对得上：上限按少的那样（2），实际 " + menu.getSlot(0).getMaxStackSize(ten));
                menu.setCarried(ten);
                menu.clicked(0, 0, ClickType.PICKUP, player);
                helper.assertTrue(click.isRemoved(), "一次放进就成交");
                helper.assertTrue(menu.getCarried().getCount() == 8, "只扣 2 个，手上剩 8 个，实际 " + menu.getCarried().getCount());
                menu.removed(player);

                player.getInventory().clearContent();
                player.getInventory().setItem(0, CapturedSoulItem.of(EntityType.VILLAGER, 10));
                DemonTradeMenu shiftMenu = new DemonTradeMenu(2, player.getInventory(), shift);
                // 快捷栏第一格在菜单里是 1 + 27 = 28 号槽
                shiftMenu.quickMoveStack(player, 28);
                helper.assertTrue(shift.isRemoved(), "Shift 点进也成交");
                shiftMenu.removed(player);
                helper.assertTrue(souls(player) == 8, "Shift 点进同样只扣 2 个，背包里剩 8 个，实际 " + souls(player));

                player.getInventory().clearContent();
                DemonTradeMenu ironMenu = new DemonTradeMenu(3, player.getInventory(), irons);
                ironMenu.setCarried(new ItemStack(Items.IRON_BLOCK, 12));
                ironMenu.clicked(0, 0, ClickType.PICKUP, player);
                helper.assertTrue(irons.isRemoved(), "铁块成交");
                helper.assertTrue(ironMenu.getCarried().getCount() == 4,
                        "贵的排在前面也按 8 个扣，手上剩 4 个，实际 " + ironMenu.getCarried().getCount());
                ironMenu.removed(player);
            } finally {
                click.discard();
                shift.discard();
                irons.discard();
            }
        });
        helper.succeed();
    }

    /**
     * 「任意灵魂 ×2」只能交两个同种的：格里放 1 个村民灵魂，再拿 1 个末影人灵魂点格子是交换（两种叠不到一起），不成交；
     * 再放一个末影人灵魂凑成两个同种的才成交。
     */
    @GameTest(template = LARGE)
    public static void demonAnySoulNeedsSameKind(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-samekind", 8);
        standingCase(null, () -> {
            DemonEntity demon = demon(helper, player, iron(8), DemonWant.anySoul(2));
            try {
                DemonTradeMenu menu = new DemonTradeMenu(1, player.getInventory(), demon);
                menu.setCarried(CapturedSoulItem.of(EntityType.VILLAGER, 1));
                menu.clicked(0, 0, ClickType.PICKUP, player);
                helper.assertTrue(menu.offered().getCount() == 1 && menu.getCarried().isEmpty(), "第一个村民灵魂进格");
                menu.setCarried(CapturedSoulItem.of(EntityType.ENDERMAN, 1));
                menu.clicked(0, 0, ClickType.PICKUP, player);
                helper.assertFalse(demon.isRemoved(), "村民 + 末影人凑不成两个：不成交");
                helper.assertTrue(menu.offered().getCount() == 1 && CapturedSoulItem.matches(menu.offered(), EntityType.ENDERMAN),
                        "两种灵魂叠不到一起：格里换成末影人灵魂，实际 " + menu.offered());
                helper.assertTrue(menu.getCarried().getCount() == 1 && CapturedSoulItem.matches(menu.getCarried(), EntityType.VILLAGER),
                        "手上换成村民灵魂");
                menu.setCarried(CapturedSoulItem.of(EntityType.ENDERMAN, 1));
                menu.clicked(0, 0, ClickType.PICKUP, player);
                helper.assertTrue(demon.isRemoved() && menu.offered().isEmpty(), "两个末影人灵魂（同种）成交");
                menu.removed(player);
            } finally {
                demon.discard();
            }
        });
        helper.succeed();
    }

    // ================================================================ 记数、契主

    /** 成交 +1；被召唤者打发走不加 */
    @GameTest(template = LARGE)
    public static void demonTradeRaisesStanding(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-count", 0);
        standingCase(null, () -> {
            DemonEntity traded = demon(helper, player, iron(1));
            DemonEntity dismissed = demon(helper, player, iron(1));
            try {
                trade(player, traded, new ItemStack(Items.IRON_BLOCK));
                helper.assertTrue(traded.isRemoved() && standing(player) == 1, "成交后 +1，实际 " + standing(player));
                helper.assertTrue(dismissed.skipAttackInteraction(player) && dismissed.isRemoved(), "召唤者左键打发走");
                helper.assertTrue(standing(player) == 1, "打发走不加，实际 " + standing(player));
            } finally {
                traded.discard();
                dismissed.discard();
            }
        });
        helper.succeed();
    }

    /** 契主按成交前的声望算：19 → 20 那一笔不给经验；20 起每笔多 30 点经验，阿克罗尼特不多给（报酬覆盖成 5 个） */
    @GameTest(template = LARGE)
    public static void demonPatronExtraExperience(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-patron", 19);
        standingCase(null, () -> {
            DemonEntity a = demon(helper, player, iron(1));
            DemonEntity b = demon(helper, player, iron(1));
            try {
                int xp = player.totalExperience;
                trade(player, a, new ItemStack(Items.IRON_BLOCK));
                helper.assertTrue(player.totalExperience == xp, "到 20 的那一笔还不给经验，实际多了 " + (player.totalExperience - xp));
                helper.assertTrue(standing(player) == 20 && ingots(player) == 5, "声望 20、5 个锭");
                trade(player, b, new ItemStack(Items.IRON_BLOCK));
                helper.assertTrue(player.totalExperience == xp + 30, "契主每笔多 30 点经验，实际多了 " + (player.totalExperience - xp));
                helper.assertTrue(ingots(player) == 10, "阿克罗尼特不多给：两笔共 10 个，实际 " + ingots(player));
                helper.assertTrue(standing(player) == 21, "声望 21");
            } finally {
                a.discard();
                b.discard();
            }
        });
        helper.succeed();
    }

    /** 关掉契约之誉：只开一样、契主不给经验，但成交照样 +1 */
    @GameTest(template = LARGE)
    public static void demonStandingDisabledStillCounts(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-off", 25);
        standingCase(List.of(ironEntry(8)), () -> {
            DemonStanding.setTestEnabledOverride(false);
            helper.assertTrue(DemonTrades.rollFor(player, RandomSource.create(3L)).size() == 1, "关掉时只开一样");
            DemonEntity demon = demon(helper, player, iron(1));
            try {
                int xp = player.totalExperience;
                trade(player, demon, new ItemStack(Items.IRON_BLOCK));
                helper.assertTrue(demon.isRemoved() && player.totalExperience == xp, "关掉时成交不给经验");
                helper.assertTrue(standing(player) == 26, "照样记数，实际 " + standing(player));
            } finally {
                demon.discard();
            }
        });
        helper.succeed();
    }

    // ================================================================ 血契

    /** 血契：潜行、手持村民灵魂右键 → 扣 1 个、重抽（按现在的声望，熟客两样）、不开界面；第二次不扣不换；不潜行时照常开界面 */
    @GameTest(template = LARGE)
    public static void demonBloodRerollOnce(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-blood", 40);
        standingCase(List.of(ironEntry(8)), () -> {
            DemonEntity demon = demon(helper, player, DemonWant.item(Items.GOLD_BLOCK, 1, false));
            try {
                player.setItemInHand(InteractionHand.MAIN_HAND, CapturedSoulItem.of(EntityType.VILLAGER, 3));
                player.setShiftKeyDown(true);
                InteractionResult result = demon.interact(player, InteractionHand.MAIN_HAND);
                helper.assertTrue(result == InteractionResult.CONSUME && player.containerMenu == player.inventoryMenu, "重抽不开界面");
                helper.assertTrue(player.getMainHandItem().getCount() == 2, "交出 1 个灵魂，实际剩 " + player.getMainHandItem().getCount());
                helper.assertTrue(demon.hasRerolled() && demon.wants().equals(List.of(iron(8), iron(12))),
                        "换成新要价（熟客两样），实际 " + demon.wants());

                demon.setWants(List.of(DemonWant.item(Items.GOLD_BLOCK, 1, false)));
                demon.interact(player, InteractionHand.MAIN_HAND);
                helper.assertTrue(player.getMainHandItem().getCount() == 2, "第二次不扣灵魂");
                helper.assertTrue(demon.wants().equals(List.of(DemonWant.item(Items.GOLD_BLOCK, 1, false))), "第二次不换要价");
                helper.assertTrue(player.containerMenu == player.inventoryMenu, "第二次也不开界面（松开潜行再右键才开）");

                player.setShiftKeyDown(false);
                demon.interact(player, InteractionHand.MAIN_HAND);
                helper.assertTrue(player.containerMenu instanceof DemonTradeMenu, "不潜行右键照常开交易界面");
                player.closeContainer();
                helper.assertTrue(player.getMainHandItem().getCount() == 2, "开界面不扣灵魂");
            } finally {
                demon.discard();
            }
        });
        helper.succeed();
    }

    /** 血契的门：39、空灵魂、关掉契约之誉时都照常开界面、不扣不换；创造模式玩家重抽不扣灵魂 */
    @GameTest(template = LARGE)
    public static void demonBloodNeedsTierAndSoul(GameTestHelper helper) {
        ServerPlayer short1 = player(helper, "ec5-blood39", 39);
        ServerPlayer blood = player(helper, "ec5-blood40", 40);
        ServerPlayer creative = player(helper, "ec5-bloodc", 40);
        creative.setGameMode(GameType.CREATIVE);
        standingCase(List.of(ironEntry(8)), () -> {
            List<DemonEntity> demons = new ArrayList<>();
            try {
                DemonEntity forShort = demon(helper, short1, iron(3));
                demons.add(forShort);
                short1.setItemInHand(InteractionHand.MAIN_HAND, CapturedSoulItem.of(EntityType.VILLAGER, 3));
                short1.setShiftKeyDown(true);
                forShort.interact(short1, InteractionHand.MAIN_HAND);
                helper.assertTrue(short1.containerMenu instanceof DemonTradeMenu && !forShort.hasRerolled()
                        && short1.getMainHandItem().getCount() == 3, "声望 39：照常开界面、不重抽");
                short1.closeContainer();

                DemonEntity forEmpty = demon(helper, blood, iron(3));
                demons.add(forEmpty);
                blood.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ECDemonic.CAPTURED_SOUL.get(), 2));
                blood.setShiftKeyDown(true);
                forEmpty.interact(blood, InteractionHand.MAIN_HAND);
                helper.assertTrue(blood.containerMenu instanceof DemonTradeMenu && !forEmpty.hasRerolled()
                        && blood.getMainHandItem().getCount() == 2, "空灵魂：照常开界面、不重抽");
                blood.closeContainer();

                DemonStanding.setTestEnabledOverride(false);
                blood.setItemInHand(InteractionHand.MAIN_HAND, CapturedSoulItem.of(EntityType.VILLAGER, 3));
                forEmpty.interact(blood, InteractionHand.MAIN_HAND);
                helper.assertTrue(blood.containerMenu instanceof DemonTradeMenu && !forEmpty.hasRerolled()
                        && blood.getMainHandItem().getCount() == 3, "关掉契约之誉：照常开界面、不重抽");
                blood.closeContainer();
                DemonStanding.setTestEnabledOverride(true);

                DemonEntity forCreative = demon(helper, blood, iron(3));
                demons.add(forCreative);
                creative.setItemInHand(InteractionHand.MAIN_HAND, CapturedSoulItem.of(EntityType.VILLAGER, 3));
                creative.setShiftKeyDown(true);
                forCreative.interact(creative, InteractionHand.MAIN_HAND);
                helper.assertTrue(forCreative.hasRerolled() && creative.getMainHandItem().getCount() == 3,
                        "创造模式玩家（声望 40）重抽不扣灵魂");
            } finally {
                demons.forEach(DemonEntity::discard);
            }
        });
        helper.succeed();
    }

    /** 不是召唤者的生存玩家（声望 40、主手村民灵魂、潜行）右键别人的恶魔：被拒，不重抽、不扣灵魂、不换要价、不开界面 */
    @GameTest(template = LARGE)
    public static void demonBloodRefusesNonSummoner(GameTestHelper helper) {
        ServerPlayer owner = player(helper, "ec5-owner", 0);
        ServerPlayer stranger = player(helper, "ec5-stranger", 40);
        stranger.setGameMode(GameType.SURVIVAL);
        standingCase(List.of(ironEntry(8)), () -> {
            DemonEntity demon = demon(helper, owner, iron(3));
            try {
                stranger.setItemInHand(InteractionHand.MAIN_HAND, CapturedSoulItem.of(EntityType.VILLAGER, 3));
                stranger.setShiftKeyDown(true);
                InteractionResult result = demon.interact(stranger, InteractionHand.MAIN_HAND);
                helper.assertTrue(result == InteractionResult.CONSUME && !demon.hasRerolled() && demon.wants().equals(List.of(iron(3))),
                        "别人的恶魔：不重抽、要价不变，实际 " + demon.wants());
                helper.assertTrue(stranger.getMainHandItem().getCount() == 3, "不扣灵魂");
                helper.assertTrue(stranger.containerMenu == stranger.inventoryMenu, "不开交易界面");
            } finally {
                demon.discard();
            }
        });
        helper.succeed();
    }

    /** 血契认副手：主手拿铁剑、副手拿村民灵魂，潜行右键 → 交出副手的一个灵魂、重抽（熟客两样）、不开界面，主手的剑不动 */
    @GameTest(template = LARGE)
    public static void demonBloodOffhandSoul(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-offhand", 40);
        standingCase(List.of(ironEntry(8)), () -> {
            DemonEntity demon = demon(helper, player, DemonWant.item(Items.GOLD_BLOCK, 1, false));
            try {
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
                player.setItemInHand(InteractionHand.OFF_HAND, CapturedSoulItem.of(EntityType.VILLAGER, 3));
                player.setShiftKeyDown(true);
                InteractionResult result = demon.interact(player, InteractionHand.MAIN_HAND);
                helper.assertTrue(result == InteractionResult.CONSUME && demon.hasRerolled(), "副手拿灵魂也重抽");
                helper.assertTrue(player.getOffhandItem().getCount() == 2, "交出副手的 1 个灵魂，实际剩 " + player.getOffhandItem().getCount());
                helper.assertTrue(player.getMainHandItem().is(Items.IRON_SWORD) && player.getMainHandItem().getCount() == 1, "主手的剑不动");
                helper.assertTrue(player.containerMenu == player.inventoryMenu, "重抽不开界面");
                helper.assertTrue(demon.wants().equals(List.of(iron(8), iron(12))), "换成新要价（熟客两样），实际 " + demon.wants());
            } finally {
                demon.discard();
            }
        });
        helper.succeed();
    }

    /**
     * 别人（创造模式管理员，声望 40）替召唤者重抽时，开几样跟着召唤者，不按重抽的人自己的契约之誉：
     * 召唤者不在线（测试玩家不在玩家列表里）时照现在开几样就开几样，一样的还是一样、两样的还是两样。
     */
    @GameTest(template = LARGE)
    public static void demonBloodRerollFollowsSummoner(GameTestHelper helper) {
        ServerPlayer summoner = player(helper, "ec5-sumoff", 0);
        ServerPlayer admin = player(helper, "ec5-admin", 40);
        admin.setGameMode(GameType.CREATIVE);
        helper.assertTrue(helper.getLevel().getServer().getPlayerList().getPlayer(summoner.getUUID()) == null,
                "前提：召唤者不在玩家列表里（算不在线）");
        standingCase(List.of(ironEntry(8)), () -> {
            DemonEntity one = demon(helper, summoner, iron(3));
            DemonEntity two = demon(helper, summoner, iron(3), iron(5));
            try {
                admin.setItemInHand(InteractionHand.MAIN_HAND, CapturedSoulItem.of(EntityType.VILLAGER, 3));
                admin.setShiftKeyDown(true);
                one.interact(admin, InteractionHand.MAIN_HAND);
                helper.assertTrue(one.hasRerolled() && one.wants().equals(List.of(iron(8))),
                        "召唤者那只只有一样：管理员重抽后还是一样，实际 " + one.wants());
                two.interact(admin, InteractionHand.MAIN_HAND);
                helper.assertTrue(two.hasRerolled() && two.wants().equals(List.of(iron(8), iron(12))),
                        "召唤者那只是两样：重抽后还是两样，实际 " + two.wants());
                helper.assertTrue(admin.getMainHandItem().getCount() == 3, "创造模式不扣灵魂");
            } finally {
                one.discard();
                two.discard();
            }
        });
        helper.succeed();
    }

    // ================================================================ 存盘、兼容

    /** 要价列表和「换过没有」存盘往返；旧存档只有一条 want 时读成一样要价 */
    @GameTest(template = LARGE)
    public static void demonWantsSaveAndLegacy(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ec5-nbt", 40);
        ServerLevel level = helper.getLevel();
        standingCase(List.of(ironEntry(8)), () -> {
            DemonEntity demon = demon(helper, player, iron(8));
            try {
                helper.assertTrue(demon.bloodReroll(player, CapturedSoulItem.of(EntityType.VILLAGER, 1)), "前提：血契换过一次");
                List<DemonWant> wants = List.of(iron(8), DemonWant.anySoul(2));
                demon.setWants(wants);
                CompoundTag tag = demon.saveWithoutId(new CompoundTag());
                DemonEntity copy = ECDemon.DEMON.get().create(level);
                copy.load(tag);
                helper.assertTrue(copy.wants().equals(wants) && copy.hasRerolled(), "两样要价和「换过」都应存住，实际 " + copy.wants());
                helper.assertTrue(copy.want() != null && copy.want().equals(iron(8)), "want() 是第一样");

                CompoundTag legacy = tag.copy();
                legacy.remove("wants");
                legacy.remove("rerolled");
                legacy.put("want", DemonWant.soul(VILLAGER, 3).save());
                DemonEntity old = ECDemon.DEMON.get().create(level);
                old.load(legacy);
                helper.assertTrue(old.wants().equals(List.of(DemonWant.soul(VILLAGER, 3))) && !old.hasRerolled(),
                        "旧存档的单条 want 读成一样要价，实际 " + old.wants());
                CompoundTag resaved = old.saveWithoutId(new CompoundTag());
                helper.assertTrue(resaved.contains("wants") && !resaved.contains("want"), "再存盘写成列表");
            } finally {
                demon.discard();
            }
        });
        helper.succeed();
    }

    /** 任意被俘灵魂：记着实体的都收、空的不收；存盘、同步反推都还是「任意」；数量夹到 64 */
    @GameTest(template = EMPTY)
    public static void demonAnySoulWant(GameTestHelper helper) {
        DemonWant any = DemonWant.anySoul(2);
        helper.assertTrue(any.isAnySoul() && !any.isSoul(), "任意灵魂不指定实体");
        helper.assertTrue(any.matches(CapturedSoulItem.of(EntityType.VILLAGER, 1)) && any.matches(CapturedSoulItem.of(EntityType.ENDERMAN, 1)),
                "村民、末影人的灵魂都收");
        helper.assertFalse(any.matches(new ItemStack(ECDemonic.CAPTURED_SOUL.get())), "空灵魂不收");
        helper.assertFalse(any.matches(new ItemStack(Items.IRON_BLOCK)), "别的东西不收");
        helper.assertFalse(DemonWant.soul(VILLAGER, 1).isAnySoul(), "指定实体的不是任意灵魂");
        DemonWant loaded = DemonWant.load(any.save());
        helper.assertTrue(loaded != null && loaded.isAnySoul() && loaded.count() == 2, "存盘往返还是任意灵魂 × 2");
        DemonWant shown = DemonWant.fromDisplay(any.displayStack(), false);
        helper.assertTrue(shown != null && shown.isAnySoul() && shown.count() == 2, "同步反推还是任意灵魂 × 2");
        helper.assertTrue(DemonWant.anySoul(100).count() == DemonTrades.MAX_SOUL_COUNT, "数量夹到 64");
        helper.succeed();
    }

    /** 死亡保留：重生后的玩家还是血契 */
    @GameTest(template = EMPTY)
    public static void demonStandingKeptOnDeath(GameTestHelper helper) {
        Player original = helper.makeMockPlayer();
        PlayerDataEvents.get(original).setDemonStanding(42);
        Player reborn = helper.makeMockPlayer();
        PlayerDataEvents.clone(new PlayerEvent.Clone(reborn, original, true));
        standingCase(null, () -> helper.assertTrue(DemonStanding.get(reborn) == 42 && DemonStanding.atLeast(reborn, DemonStanding.Tier.BLOOD),
                "死亡后声望 42、还是血契，实际 " + DemonStanding.get(reborn)));
        helper.succeed();
    }

    /** 老存档补下限：两个迁移动作已登记；补到下限、已经更高的不降 */
    @GameTest(template = EMPTY)
    public static void demonStandingLegacyFloors(GameTestHelper helper) {
        for (int floor : DemonStanding.LEGACY_FLOORS) {
            helper.assertTrue(GuideMigration.hasAction(DemonStanding.LEGACY_FLOOR_PREFIX + floor),
                    "应登记迁移动作 " + DemonStanding.LEGACY_FLOOR_PREFIX + floor);
        }
        ServerPlayer player = player(helper, "ec5-floor", 3);
        DemonStanding.raiseTo(player, 8);
        helper.assertTrue(standing(player) == 8, "3 补到 8");
        DemonStanding.raiseTo(player, 40);
        helper.assertTrue(standing(player) == 40, "8 补到 40");
        PlayerDataEvents.get(player).setDemonStanding(55);
        DemonStanding.raiseTo(player, 40);
        DemonStanding.raiseTo(player, 8);
        helper.assertTrue(standing(player) == 55, "已经 55 的不降");
        helper.succeed();
    }

    /**
     * 老存档补下限端到端：用读到的迁移表、从第 2 版迁到第 3 版（GuideMigration.migrate → v3 补发进度 → then 里的动作）。
     * 只有阿克罗尼特进度的补到 8，做出过宅男套装的补到 40，已经 55 的不降；版本写到 3 以后再跑一次什么都不补、声望不变。
     */
    @GameTest(template = EMPTY)
    public static void demonStandingLegacyMigration(GameTestHelper helper) {
        Map<Integer, List<GuideMigration.Step>> tables = GuideMigration.tables();
        helper.assertTrue(tables.containsKey(3), "应读到迁移表 v3");
        ResourceLocation ackronite = EssentialCraft5.id("guide/demons/ackronite");
        ResourceLocation computerArmor = EssentialCraft5.id("main/computer_armor");
        ServerPlayer trader = player(helper, "ec5-mig8", 0);
        ServerPlayer armored = player(helper, "ec5-mig40", 3);
        ServerPlayer veteran = player(helper, "ec5-mig55", 55);
        List<ServerPlayer> all = List.of(trader, armored, veteran);
        try {
            all.forEach(p -> GuideMigration.setVersion(p, 2));
            award(helper, trader, ackronite);
            award(helper, armored, computerArmor);
            award(helper, veteran, ackronite);
            award(helper, veteran, computerArmor);
            all.forEach(p -> GuideMigration.migrate(p, tables, 3));
            helper.assertTrue(standing(trader) == 8, "有阿克罗尼特进度：补到 8，实际 " + standing(trader));
            helper.assertTrue(standing(armored) == 40, "做出过宅男套装：补到 40，实际 " + standing(armored));
            helper.assertTrue(standing(veteran) == 55, "已经 55 的不降，实际 " + standing(veteran));
            helper.assertTrue(GuideMigration.version(trader) == 3 && GuideMigration.version(armored) == 3, "迁完写下版本 3");
            PlayerDataEvents.get(trader).setDemonStanding(2);
            helper.assertTrue(GuideMigration.migrate(trader, tables, 3) == 0 && standing(trader) == 2, "版本已是 3：再跑什么都不补");
        } finally {
            all.forEach(p -> p.getAdvancements().stopListening());
        }
        helper.succeed();
    }

    /** /ec5 player <p> demon_standing get|set：set 改数（不夹上限、不给经验），get 返回那个数；两个人时返回人数；走指令树一样能用 */
    @GameTest(template = EMPTY)
    public static void demonStandingCommand(GameTestHelper helper) throws CommandSyntaxException {
        ServerPlayer a = player(helper, "ec5-cmd-a", 0);
        ServerPlayer b = player(helper, "ec5-cmd-b", 5);
        CommandSourceStack source = helper.getLevel().getServer().createCommandSourceStack().withSuppressedOutput();
        int xp = a.totalExperience;
        helper.assertTrue(DemonStandingCommands.set(source, List.of(a), 12) == 1, "set 一个人返回 1");
        IPlayerData data = PlayerDataEvents.get(a);
        helper.assertTrue(data.getDemonStanding() == 12 && a.totalExperience == xp, "set 12：只改数");
        helper.assertTrue(DemonStandingCommands.get(source, List.of(a)) == 12, "get 一个人返回那个数");
        helper.assertTrue(DemonStandingCommands.get(source, List.of(a, b)) == 2, "get 两个人返回人数");
        // 走 /ec5 的指令树（挂载点 ECCommands.addPlayerSubcommand），以 a 的身份用 @s 选中自己
        ServerLevel level = helper.getLevel();
        CommandSourceStack asA = level.getServer().createCommandSourceStack().withLevel(level).withEntity(a).withSuppressedOutput();
        int set = level.getServer().getCommands().performPrefixedCommand(asA, "ec5 player @s demon_standing set 21");
        helper.assertTrue(set == 1 && data.getDemonStanding() == 21, "指令树：set 21 应生效，返回 " + set + "、声望 " + data.getDemonStanding());
        int got = level.getServer().getCommands().performPrefixedCommand(asA, "ec5 player @s demon_standing get");
        helper.assertTrue(got == 21, "指令树：get 应返回 21，实际 " + got);
        helper.succeed();
    }
}
