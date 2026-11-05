package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.CorruptionEffects;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.blockentity.EnchantUpgraderBlockEntity;
import com.flameforge.essentialcraft5.blockentity.EnchantUpgraderBlockEntity.Status;
import com.flameforge.essentialcraft5.blockentity.MithrilineCrystalPillarBlockEntity;
import com.flameforge.essentialcraft5.enchant.EnchantUpgrades;
import com.flameforge.essentialcraft5.enchant.EnchantUpgrades.Entry;
import com.flameforge.essentialcraft5.enchant.EnchantUpgrades.Step;
import com.flameforge.essentialcraft5.enchant.EnchantUpgrades.Tier;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECEnchantUpgrade;
import com.flameforge.essentialcraft5.registry.ECMithriline;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

/**
 * 附魔增幅台、升华台（ECEnchantUpgrade）和水晶柱每秒结算的 GameTest：
 * <ul>
 *   <li>价格表：两台机器的每一步 MRU / ESPE / 用时 / 材料 / 矩阵损伤在边界等级上的数值；书的规则（单一附魔、上限、配置排除、升华台只收 25 级以上）。</li>
 *   <li>增幅台：一步走完正好扣 MRU 和材料、书升一级送进输出格；材料不够不开工、不动任何东西；缺 MRU 暂停进度保留；
 *       拿走书不退材料；红石停机；多附魔 / 被排除的书不收；25 级的书直接送出；ESPE 只从八个位置抽；存读档接着做；自动化只送对应的格子。</li>
 *   <li>升华台：没有在线的主人、五芒星档位不够、矩阵放不下都不开工；走完一步矩阵损伤记到主人身上；最后一刻主人不在就停在最后一刻；底下的 25 级才收。</li>
 *   <li>铁砧：超原版上限的书按完整等级附到装备上，经验按等级封顶；没超的、左边是书、不能附的、互斥的都不碰或不出结果。</li>
 *   <li>水晶柱每 20 刻结算一次，平均速率不变。</li>
 * </ul>
 * 价格走真实配置的默认值（倍率都是 1），机器的 MRU 在测试里每刻补满或一次加满，不放绑定宝石。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class EnchantUpgradeTests {

    private static final String EMPTY = "empty";
    private static final BlockPos CENTER = new BlockPos(2, 1, 2);
    private static final double EPS = 1e-6;

    private EnchantUpgradeTests() {
    }

    // ---------------- 工具 ----------------

    private static Item item(String path) {
        Item item = ForgeRegistries.ITEMS.getValue(EssentialCraft5.id(path));
        if (item == null || item == Items.AIR) {
            throw new IllegalStateException("没有这个物品：" + path);
        }
        return item;
    }

    private static ItemStack book(Enchantment enchantment, int level) {
        return EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantment, level));
    }

    private static EnchantUpgraderBlockEntity place(GameTestHelper helper, BlockPos rel, Tier tier) {
        helper.setBlock(rel, ECEnchantUpgrade.block(tier).get());
        EnchantUpgraderBlockEntity be = (EnchantUpgraderBlockEntity) helper.getBlockEntity(rel);
        be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
        return be;
    }

    private static void giveMaterials(EnchantUpgraderBlockEntity be, Step step, int times) {
        be.getInventory().setStackInSlot(EnchantUpgraderBlockEntity.SLOT_A,
                new ItemStack(step.a().any().get(0), step.a().count() * times));
        be.getInventory().setStackInSlot(EnchantUpgraderBlockEntity.SLOT_B,
                new ItemStack(step.b().any().get(0), step.b().count() * times));
    }

    private static int level(ItemStack book) {
        Entry entry = EnchantUpgrades.read(book);
        return entry == null ? -1 : entry.level();
    }

    private static ItemStack slot(EnchantUpgraderBlockEntity be, int slot) {
        return be.getInventory().getStackInSlot(slot);
    }

    // ---------------- 价格表与规则 ----------------

    /** 边界等级上的价格：增幅台 2 / 11 / 25 级，升华台 26 / 31 / 41 / 50 级 */
    @GameTest(template = EMPTY)
    public static void enchantUpgradePriceTable(GameTestHelper helper) {
        Step a2 = EnchantUpgrades.step(Tier.AMPLIFIER, 2);
        helper.assertTrue(a2.mru() == 8000 && a2.espe() == 0 && a2.ticks() == 420 && a2.matrix() == 0,
                "增幅台 2 级应是 8000 MRU / 0 ESPE / 420 刻，实际 " + a2);
        helper.assertTrue(a2.a().count() == 8 && a2.a().any().equals(java.util.List.of(Items.LAPIS_LAZULI))
                && a2.b().count() == 2 && a2.b().any().contains(item("common_fire_essence")) && a2.b().any().size() == 4,
                "2 级材料应是 8 青金石 + 2 普通精华（四元素任选）");
        Step a11 = EnchantUpgrades.step(Tier.AMPLIFIER, 11);
        helper.assertTrue(a11.mru() == 242_000 && a11.espe() == 150 && a11.ticks() == 960 && a11.a().any().contains(Items.DIAMOND)
                        && a11.a().count() == 2,
                "增幅台 11 级应是 242000 MRU / 150 ESPE / 960 刻 / 钻石 ×2，实际 " + a11);
        Step a25 = EnchantUpgrades.step(Tier.AMPLIFIER, 25);
        helper.assertTrue(a25.mru() == 1_250_000 && a25.espe() == 850 && a25.ticks() == 1800
                        && a25.a().count() == 5 && a25.a().any().contains(item("mithriline_ingot"))
                        && a25.b().count() == 6 && a25.b().any().contains(item("exceptional_air_essence")),
                "增幅台 25 级应是 1250000 / 850 / 1800，米斯莱锭 ×5 + 卓越精华 ×6，实际 " + a25);
        Step s26 = EnchantUpgrades.step(Tier.ASCENDER, 26);
        helper.assertTrue(s26.mru() == 3_042_000 && s26.espe() == 1800 && s26.ticks() == 1080 && s26.matrix() == 8000
                        && s26.a().any().contains(item("demonic_core")) && s26.a().count() == 2 && s26.b().count() == 12,
                "升华台 26 级应是 3042000 / 1800 / 1080 / 矩阵 8000，恶魔核心 ×2 + 卓越精华 ×12，实际 " + s26);
        Step s31 = EnchantUpgrades.step(Tier.ASCENDER, 31);
        helper.assertTrue(s31.matrix() == 8000 + 2000 * 5 && s31.a().any().contains(item("matter_of_eternity"))
                        && s31.a().count() == 1 && s31.b().any().contains(item("demonic_plate")) && s31.b().count() == 8,
                "升华台 31 级矩阵应是 18000，材料永恒物质 ×1 + 恶魔板 ×8");
        Step s50 = EnchantUpgrades.step(Tier.ASCENDER, 50);
        helper.assertTrue(s50.mru() == 11_250_000 && s50.espe() == 9000 && s50.ticks() == 5400 && s50.matrix() == 8000 + 2000 * 24
                        && s50.a().any().contains(Items.NETHER_STAR) && s50.a().count() == 4
                        && s50.b().any().contains(item("matter_of_eternity")) && s50.b().count() == 1,
                "升华台 50 级应是 11250000 / 9000 / 5400 / 矩阵 56000，下界之星 ×4 + 永恒物质 ×1，实际 " + s50);
        long totalTicks = 0;
        long totalMatrix = 0;
        for (int n = 26; n <= 50; n++) {
            totalTicks += EnchantUpgrades.step(Tier.ASCENDER, n).ticks();
            totalMatrix += EnchantUpgrades.step(Tier.ASCENDER, n).matrix();
        }
        helper.assertTrue(totalTicks == 81_000 && totalMatrix == 800_000,
                "升华台 25 步总计应是 81000 刻、80 万矩阵损伤（手册写了），实际 " + totalTicks + " / " + totalMatrix);
        helper.assertTrue(a25.mruPerTick() == 695 && a25.mruPerTick() <= 50_000 && s50.mruPerTick() == 2084,
                "每刻 MRU 向上取整：25 级 695，50 级 2084");
        long ampTicks = 0;
        for (int n = 2; n <= 25; n++) {
            ampTicks += EnchantUpgrades.step(Tier.AMPLIFIER, n).ticks();
        }
        helper.assertTrue(ampTicks == 26_640, "增幅台 1 → 25 级总计应是 26640 刻（约 22 分钟，手册写了），实际 " + ampTicks);
        helper.succeed();
    }

    /**
     * 每一步的两格材料都放得进一格：数量不超过物品一组的上限，空格子一次收得下。
     * 永恒物质一组只有 1 个，以前升华台 31 级起要 2 个、41 级起要 4 个，书卡在 30 级。
     */
    @GameTest(template = EMPTY)
    public static void enchantUpgradeMaterialsFitOneSlot(GameTestHelper helper) {
        for (Tier tier : Tier.VALUES) {
            EnchantUpgraderBlockEntity be = place(helper, CENTER.offset(tier.ordinal() * 2, 0, 0), tier);
            for (int n = tier.minLevel + 1; n <= tier.cap; n++) {
                Step step = EnchantUpgrades.step(tier, n);
                int[] slots = {EnchantUpgraderBlockEntity.SLOT_A, EnchantUpgraderBlockEntity.SLOT_B};
                EnchantUpgrades.Need[] needs = {step.a(), step.b()};
                for (int i = 0; i < 2; i++) {
                    for (Item item : needs[i].any()) {
                        ItemStack want = new ItemStack(item, needs[i].count());
                        helper.assertTrue(needs[i].count() <= want.getMaxStackSize(),
                                tier + " " + n + " 级材料" + (i + 1) + "要 " + needs[i].count() + " 个 " + item + "，一组只有 " + want.getMaxStackSize());
                        ItemStack left = be.getInventory().insertItem(slots[i], want, true);
                        helper.assertTrue(left.isEmpty(),
                                tier + " " + n + " 级材料" + (i + 1) + "的 " + needs[i].count() + " 个 " + item + " 空格子应一次收下，剩 " + left);
                    }
                }
            }
        }
        helper.succeed();
    }

    /** 书的规则：单一附魔才收；只有一级、诅咒、配置排除的不收；时运、抢夺压到 10；升华台只收 25 级以上 */
    @GameTest(template = EMPTY)
    public static void enchantUpgradeBookRules(GameTestHelper helper) {
        ItemStack sharp = book(Enchantments.SHARPNESS, 5);
        Entry entry = EnchantUpgrades.read(sharp);
        helper.assertTrue(entry != null && entry.enchantment() == Enchantments.SHARPNESS && entry.level() == 5, "单附魔书应读出锋利 5");
        ItemStack two = book(Enchantments.SHARPNESS, 5);
        EnchantedBookItem.addEnchantment(two, new EnchantmentInstance(Enchantments.UNBREAKING, 3));
        helper.assertTrue(EnchantUpgrades.read(two) == null, "两条附魔的书不收");
        helper.assertTrue(EnchantUpgrades.read(new ItemStack(Items.BOOK)) == null && EnchantUpgrades.read(new ItemStack(Items.DIAMOND_SWORD)) == null,
                "普通书、装备都不是附魔书");
        helper.assertTrue(EnchantUpgrades.cap(Enchantments.SHARPNESS, Tier.AMPLIFIER) == 25
                        && EnchantUpgrades.cap(Enchantments.SHARPNESS, Tier.ASCENDER) == 50,
                "锋利：增幅台 25、升华台 50");
        helper.assertTrue(EnchantUpgrades.cap(Enchantments.MENDING, Tier.AMPLIFIER) == 0
                        && EnchantUpgrades.cap(Enchantments.SILK_TOUCH, Tier.ASCENDER) == 0
                        && EnchantUpgrades.cap(Enchantments.VANISHING_CURSE, Tier.AMPLIFIER) == 0,
                "只有一级的（经验修补、精准采集）和诅咒不收");
        helper.assertTrue(EnchantUpgrades.cap(Enchantments.FROST_WALKER, Tier.AMPLIFIER) == 0
                        && EnchantUpgrades.cap(Enchantments.SWIFT_SNEAK, Tier.ASCENDER) == 0
                        && EnchantUpgrades.cap(Enchantments.RIPTIDE, Tier.AMPLIFIER) == 0,
                "冰霜行者、迅捷潜行、激流默认不收");
        helper.assertTrue(EnchantUpgrades.cap(Enchantments.BLOCK_FORTUNE, Tier.AMPLIFIER) == 10
                        && EnchantUpgrades.cap(Enchantments.MOB_LOOTING, Tier.ASCENDER) == 10,
                "时运、抢夺压到 10 级");
        Entry l24 = new Entry(Enchantments.SHARPNESS, 24);
        Entry l25 = new Entry(Enchantments.SHARPNESS, 25);
        Entry l49 = new Entry(Enchantments.SHARPNESS, 49);
        Entry l50 = new Entry(Enchantments.SHARPNESS, 50);
        helper.assertTrue(EnchantUpgrades.canUpgrade(l24, Tier.AMPLIFIER) && !EnchantUpgrades.canUpgrade(l25, Tier.AMPLIFIER),
                "增幅台 24 级还能升、25 级不能");
        helper.assertTrue(!EnchantUpgrades.canUpgrade(l24, Tier.ASCENDER) && EnchantUpgrades.canUpgrade(l25, Tier.ASCENDER)
                        && EnchantUpgrades.canUpgrade(l49, Tier.ASCENDER) && !EnchantUpgrades.canUpgrade(l50, Tier.ASCENDER),
                "升华台只收 25 ~ 49 级");
        ItemStack up = EnchantUpgrades.withLevel(sharp, 6);
        helper.assertTrue(level(up) == 6 && EnchantUpgrades.read(up).enchantment() == Enchantments.SHARPNESS && up.getCount() == 1,
                "withLevel 应只改等级");
        helper.succeed();
    }

    // ---------------- 增幅台 ----------------

    /** 一步走完：用 1 级的书、目标 2 级，正好扣 8000 MRU 和材料，用时 420 刻，书进输出格 */
    @GameTest(template = EMPTY, timeoutTicks = 560)
    public static void enchantAmplifierRunsOneStep(GameTestHelper helper) {
        EnchantUpgraderBlockEntity be = place(helper, CENTER, Tier.AMPLIFIER);
        Step step = EnchantUpgrades.step(Tier.AMPLIFIER, 2);
        be.getInventory().setStackInSlot(EnchantUpgraderBlockEntity.SLOT_BOOK, book(Enchantments.UNBREAKING, 1));
        giveMaterials(be, step, 2);
        be.setTargetLevel(2);
        int start = be.getMRUStorage().getMRU();
        long t0 = helper.getTick();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!slot(be, EnchantUpgraderBlockEntity.SLOT_OUTPUT).isEmpty(), "书还没送进输出格"))
                .thenExecute(() -> {
                    long elapsed = helper.getTick() - t0;
                    helper.assertTrue(elapsed >= step.ticks() && elapsed <= step.ticks() + 8, "一步应约 420 刻，实际 " + elapsed);
                    helper.assertTrue(level(slot(be, EnchantUpgraderBlockEntity.SLOT_OUTPUT)) == 2, "输出的书应是 2 级");
                    helper.assertTrue(slot(be, EnchantUpgraderBlockEntity.SLOT_BOOK).isEmpty(), "输入格应空了");
                    helper.assertTrue(start - be.getMRUStorage().getMRU() == 8000, "应正好扣 8000 MRU，实际 " + (start - be.getMRUStorage().getMRU()));
                    helper.assertTrue(slot(be, EnchantUpgraderBlockEntity.SLOT_A).getCount() == 8
                                    && slot(be, EnchantUpgraderBlockEntity.SLOT_B).getCount() == 2,
                            "两格材料各只扣一步的量（还剩一步的）");
                    helper.assertTrue(be.stepLevel() == 0 && be.progress() == 0, "状态清零");
                })
                .thenSucceed();
    }

    /** 材料不够不开工、什么都不动；放错材料不算；补够才开始，开始时一次扣掉 */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void enchantAmplifierNeedsMaterials(GameTestHelper helper) {
        EnchantUpgraderBlockEntity be = place(helper, CENTER, Tier.AMPLIFIER);
        be.getInventory().setStackInSlot(EnchantUpgraderBlockEntity.SLOT_BOOK, book(Enchantments.UNBREAKING, 1));
        be.getInventory().setStackInSlot(EnchantUpgraderBlockEntity.SLOT_A, new ItemStack(Items.LAPIS_LAZULI, 7));
        be.getInventory().setStackInSlot(EnchantUpgraderBlockEntity.SLOT_B, new ItemStack(Items.DIRT, 5));
        int mru = be.getMRUStorage().getMRU();
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(be.status() == Status.NO_MATERIAL, "材料不够应是 NO_MATERIAL，实际 " + be.status());
                    helper.assertTrue(be.getMRUStorage().getMRU() == mru && be.stepLevel() == 0 && be.progress() == 0, "不开工、不扣 MRU");
                    helper.assertTrue(slot(be, EnchantUpgraderBlockEntity.SLOT_A).getCount() == 7 && slot(be, EnchantUpgraderBlockEntity.SLOT_B).getCount() == 5,
                            "材料原样不动");
                    be.getInventory().setStackInSlot(EnchantUpgraderBlockEntity.SLOT_A, new ItemStack(Items.LAPIS_LAZULI, 8));
                    be.getInventory().setStackInSlot(EnchantUpgraderBlockEntity.SLOT_B, new ItemStack(item("common_water_essence"), 2));
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(be.status() == Status.WORKING && be.stepLevel() == 2, "材料够了应开工，实际 " + be.status() + " / " + be.stepLevel());
                    helper.assertTrue(slot(be, EnchantUpgraderBlockEntity.SLOT_A).isEmpty() && slot(be, EnchantUpgraderBlockEntity.SLOT_B).isEmpty(),
                            "开工时一次扣掉这一步的材料");
                })
                .thenSucceed();
    }

    /** 缺 MRU 暂停、进度保留，补上接着走；拿走书清零、材料不退；红石停机 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void enchantAmplifierPausesAndResets(GameTestHelper helper) {
        EnchantUpgraderBlockEntity be = place(helper, CENTER, Tier.AMPLIFIER);
        Step step = EnchantUpgrades.step(Tier.AMPLIFIER, 2);
        be.getInventory().setStackInSlot(EnchantUpgraderBlockEntity.SLOT_BOOK, book(Enchantments.UNBREAKING, 1));
        giveMaterials(be, step, 1);
        int[] frozen = new int[1];
        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.assertTrue(be.status() == Status.WORKING && be.progress() >= 15, "应已走了一阵，实际 " + be.progress());
                    be.getMRUStorage().setMRU(0);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    frozen[0] = be.progress();
                    helper.assertTrue(be.status() == Status.NO_MRU, "没 MRU 应是 NO_MRU，实际 " + be.status());
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    helper.assertTrue(be.progress() == frozen[0] && be.stepLevel() == 2, "暂停时进度不动、这一步保留");
                    be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(be.progress() > frozen[0], "补上 MRU 应接着走");
                    // 拿走书：这一步清零，材料不退
                    ItemStack taken = be.getInventory().extractItem(EnchantUpgraderBlockEntity.SLOT_BOOK, 1, false);
                    helper.assertFalse(taken.isEmpty(), "应能拿走书");
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(be.stepLevel() == 0 && be.progress() == 0, "拿走书应清零");
                    helper.assertTrue(slot(be, EnchantUpgraderBlockEntity.SLOT_A).isEmpty(), "付掉的材料不退");
                    be.getInventory().setStackInSlot(EnchantUpgraderBlockEntity.SLOT_BOOK, book(Enchantments.UNBREAKING, 1));
                    giveMaterials(be, step, 1);
                    helper.setBlock(CENTER.east(), Blocks.REDSTONE_BLOCK);
                    be.invalidatePower();
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(be.status() == Status.POWERED && be.stepLevel() == 0, "红石信号应停机、不开工，实际 " + be.status());
                })
                .thenSucceed();
    }

    /** 不收的书和不用升的书：多附魔 BAD_BOOK、被排除 NOT_ACCEPTED、25 级直接送输出格、输出格满时等着 */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void enchantAmplifierRefusals(GameTestHelper helper) {
        EnchantUpgraderBlockEntity be = place(helper, CENTER, Tier.AMPLIFIER);
        ItemStack two = book(Enchantments.SHARPNESS, 2);
        EnchantedBookItem.addEnchantment(two, new EnchantmentInstance(Enchantments.UNBREAKING, 3));
        be.getInventory().setStackInSlot(EnchantUpgraderBlockEntity.SLOT_BOOK, two);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(be.status() == Status.BAD_BOOK, "多附魔的书应是 BAD_BOOK，实际 " + be.status());
                    be.getInventory().setStackInSlot(EnchantUpgraderBlockEntity.SLOT_BOOK, book(Enchantments.FROST_WALKER, 1));
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(be.status() == Status.NOT_ACCEPTED, "冰霜行者应是 NOT_ACCEPTED，实际 " + be.status());
                    be.getInventory().setStackInSlot(EnchantUpgraderBlockEntity.SLOT_BOOK, book(Enchantments.SHARPNESS, 25));
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(level(slot(be, EnchantUpgraderBlockEntity.SLOT_OUTPUT)) == 25 && slot(be, EnchantUpgraderBlockEntity.SLOT_BOOK).isEmpty(),
                            "25 级的书增幅台不用再升，直接送进输出格");
                    be.getInventory().setStackInSlot(EnchantUpgraderBlockEntity.SLOT_BOOK, book(Enchantments.SHARPNESS, 25));
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(be.status() == Status.OUTPUT_FULL && !slot(be, EnchantUpgraderBlockEntity.SLOT_BOOK).isEmpty(),
                            "输出格满了应等着，实际 " + be.status());
                })
                .thenSucceed();
    }

    /** 9 级起要 ESPE（这里用 11 级）：只从八个位置抽，第九个位置不动 */
    @GameTest(template = EMPTY, timeoutTicks = 120)
    public static void enchantAmplifierPullsEspeFromRing(GameTestHelper helper) {
        EnchantUpgraderBlockEntity be = place(helper, CENTER, Tier.AMPLIFIER);
        Step step = EnchantUpgrades.step(Tier.AMPLIFIER, 11);
        be.getInventory().setStackInSlot(EnchantUpgraderBlockEntity.SLOT_BOOK, book(Enchantments.SHARPNESS, 10));
        giveMaterials(be, step, 1);
        BlockPos onRing = CENTER.offset(2, 0, 0);
        BlockPos offRing = CENTER.offset(3, 0, 0);
        helper.setBlock(onRing, ECMithriline.MITHRILINE_CRYSTAL_PILLAR.get());
        helper.setBlock(offRing, ECMithriline.MITHRILINE_CRYSTAL_PILLAR.get());
        double[] off = new double[1];
        helper.startSequence()
                .thenExecute(() -> {
                    be.getESPEStorage().setESPE(0D);
                    ((MithrilineCrystalPillarBlockEntity) helper.getBlockEntity(onRing)).getESPEStorage().setESPE(500D);
                    ((MithrilineCrystalPillarBlockEntity) helper.getBlockEntity(offRing)).getESPEStorage().setESPE(500D);
                    off[0] = ((MithrilineCrystalPillarBlockEntity) helper.getBlockEntity(offRing)).getESPEStorage().getESPE();
                })
                .thenIdle(30)
                .thenExecute(() -> {
                    double pulled = be.getESPEStorage().getESPE();
                    helper.assertTrue(pulled > 0D || be.progress() > 0, "应从圈上的水晶柱抽到 ESPE，实际 " + pulled + " / 进度 " + be.progress());
                    helper.assertTrue(((MithrilineCrystalPillarBlockEntity) helper.getBlockEntity(offRing)).getESPEStorage().getESPE() >= off[0] - 1D,
                            "圈外的水晶柱不动（水晶柱自己每秒还会攒一点）");
                    helper.assertTrue(((MithrilineCrystalPillarBlockEntity) helper.getBlockEntity(onRing)).getESPEStorage().getESPE() < 500D,
                            "圈上的水晶柱被抽走一部分");
                })
                .thenSucceed();
    }

    /** 存读档：这一步、进度、目标等级、被卡住的书都原样接着做 */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void enchantUpgradeSaveAndLoad(GameTestHelper helper) {
        EnchantUpgraderBlockEntity be = place(helper, CENTER, Tier.AMPLIFIER);
        Step step = EnchantUpgrades.step(Tier.AMPLIFIER, 3);
        be.getInventory().setStackInSlot(EnchantUpgraderBlockEntity.SLOT_BOOK, book(Enchantments.UNBREAKING, 2));
        giveMaterials(be, step, 1);
        be.setTargetLevel(7);
        helper.startSequence()
                .thenIdle(12)
                .thenExecute(() -> {
                    helper.assertTrue(be.stepLevel() == 3 && be.progress() > 5, "应在做 3 级这一步");
                    CompoundTag saved = be.saveWithFullMetadata();
                    EnchantUpgraderBlockEntity copy = EnchantUpgraderBlockEntity.create(helper.absolutePos(CENTER), helper.getBlockState(CENTER));
                    copy.load(saved);
                    helper.assertTrue(copy.stepLevel() == 3 && copy.progress() == be.progress() && copy.targetLevel() == 7,
                            "读回应是 3 级这一步、进度 " + be.progress() + "、目标 7，实际 " + copy.stepLevel() + " / " + copy.progress() + " / " + copy.targetLevel());
                    helper.assertTrue(level(copy.getInventory().getStackInSlot(EnchantUpgraderBlockEntity.SLOT_BOOK)) == 2, "书也读回来");
                    be.setTargetLevel(1);
                    helper.assertTrue(be.targetLevel() == 2, "目标夹在最低 + 1 以上");
                    be.setTargetLevel(99);
                    helper.assertTrue(be.targetLevel() == 25, "目标夹在上限以下，到顶就是上限");
                })
                .thenSucceed();
    }

    /** 自动化：书格只收单附魔且没被排除的，材料格只收这台用得到的，输出格塞不进去、能抽 */
    @GameTest(template = EMPTY)
    public static void enchantUpgradeAutomationRules(GameTestHelper helper) {
        EnchantUpgraderBlockEntity be = place(helper, CENTER, Tier.AMPLIFIER);
        IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER, net.minecraft.core.Direction.UP)
                .orElseThrow(() -> new IllegalStateException("应有物品能力"));
        ItemStack two = book(Enchantments.SHARPNESS, 2);
        EnchantedBookItem.addEnchantment(two, new EnchantmentInstance(Enchantments.UNBREAKING, 3));
        helper.assertTrue(!handler.insertItem(EnchantUpgraderBlockEntity.SLOT_BOOK, two, true).isEmpty(), "多附魔的书漏斗送不进去");
        helper.assertTrue(!handler.insertItem(EnchantUpgraderBlockEntity.SLOT_BOOK, book(Enchantments.FROST_WALKER, 1), true).isEmpty(), "被排除的书送不进去");
        helper.assertTrue(handler.insertItem(EnchantUpgraderBlockEntity.SLOT_BOOK, book(Enchantments.SHARPNESS, 1), true).isEmpty(), "单附魔书能送进书格");
        helper.assertTrue(handler.insertItem(EnchantUpgraderBlockEntity.SLOT_A, new ItemStack(Items.LAPIS_LAZULI, 8), true).isEmpty(), "青金石能送进材料一");
        helper.assertTrue(!handler.insertItem(EnchantUpgraderBlockEntity.SLOT_A, new ItemStack(Items.DIRT, 8), true).isEmpty(), "泥土送不进材料一");
        helper.assertTrue(!handler.insertItem(EnchantUpgraderBlockEntity.SLOT_A, new ItemStack(item("common_fire_essence"), 8), true).isEmpty(), "精华送不进材料一（那是材料二的）");
        helper.assertTrue(handler.insertItem(EnchantUpgraderBlockEntity.SLOT_B, new ItemStack(item("rare_earth_essence"), 8), true).isEmpty(), "精华能送进材料二");
        helper.assertTrue(!handler.insertItem(EnchantUpgraderBlockEntity.SLOT_OUTPUT, book(Enchantments.SHARPNESS, 3), true).isEmpty(), "输出格不能往里塞");
        be.getInventory().setStackInSlot(EnchantUpgraderBlockEntity.SLOT_OUTPUT, book(Enchantments.SHARPNESS, 25));
        helper.assertTrue(!handler.extractItem(EnchantUpgraderBlockEntity.SLOT_OUTPUT, 1, true).isEmpty(), "输出格能抽");
        EnchantUpgraderBlockEntity late = place(helper, CENTER.offset(0, 0, 1), Tier.ASCENDER);
        IItemHandler lateHandler = late.getCapability(ForgeCapabilities.ITEM_HANDLER, net.minecraft.core.Direction.UP)
                .orElseThrow(() -> new IllegalStateException("应有物品能力"));
        helper.assertTrue(lateHandler.insertItem(EnchantUpgraderBlockEntity.SLOT_A, new ItemStack(Items.NETHER_STAR, 1), true).isEmpty(),
                "升华台的材料一收下界之星");
        helper.assertTrue(!lateHandler.insertItem(EnchantUpgraderBlockEntity.SLOT_A, new ItemStack(Items.LAPIS_LAZULI, 1), true).isEmpty(),
                "升华台的材料一不收青金石");
        helper.succeed();
    }

    // ---------------- 升华台 ----------------

    private static ServerPlayer ownerOnline(GameTestHelper helper, EnchantUpgraderBlockEntity be, UUID id) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, id, "ec5-ench");
        be.setOwner(id, "ec5-ench");
        ECProtection.setOnlineOverride(player);
        return player;
    }

    /** 门槛：不到 25 级 TOO_LOW；没在线的主人 NO_OWNER；五芒星档位不够 LOW_TIER；矩阵放不下 MATRIX_FULL；都过了才开工 */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void enchantAscenderGates(GameTestHelper helper) {
        EnchantUpgraderBlockEntity be = place(helper, CENTER, Tier.ASCENDER);
        Step step = EnchantUpgrades.step(Tier.ASCENDER, 26);
        UUID id = UUID.randomUUID();
        be.getInventory().setStackInSlot(EnchantUpgraderBlockEntity.SLOT_BOOK, book(Enchantments.SHARPNESS, 24));
        giveMaterials(be, step, 1);
        ServerPlayer[] owner = new ServerPlayer[1];
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(be.status() == Status.TOO_LOW, "24 级的书升华台应是 TOO_LOW，实际 " + be.status());
                    be.getInventory().setStackInSlot(EnchantUpgraderBlockEntity.SLOT_BOOK, book(Enchantments.SHARPNESS, 25));
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(be.status() == Status.NO_OWNER, "没有主人应是 NO_OWNER，实际 " + be.status());
                    helper.assertTrue(be.stepLevel() == 0 && slot(be, EnchantUpgraderBlockEntity.SLOT_A).getCount() == step.a().count(), "不开工、材料不动");
                    owner[0] = ownerOnline(helper, be, id);
                    IPlayerData data = PlayerDataEvents.get(owner[0]);
                    data.setPentacleTier(1);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(be.status() == Status.LOW_TIER, "五芒星只打开过 1 档应是 LOW_TIER，实际 " + be.status());
                    IPlayerData data = PlayerDataEvents.get(owner[0]);
                    data.setPentacleTier(2);
                    data.setMatrixDamage(CorruptionEffects.maxMatrixDamage() - 1000);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(be.status() == Status.MATRIX_FULL, "矩阵放不下应是 MATRIX_FULL，实际 " + be.status());
                    helper.assertTrue(be.stepLevel() == 0 && slot(be, EnchantUpgraderBlockEntity.SLOT_A).getCount() == step.a().count(), "不开工、材料不动");
                    PlayerDataEvents.get(owner[0]).setMatrixDamage(0);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(be.stepLevel() == 26 && slot(be, EnchantUpgraderBlockEntity.SLOT_A).isEmpty(),
                            "门槛都过了应开工并扣材料，实际 " + be.status() + " / " + be.stepLevel());
                    ECProtection.clearOnlineOverride(id);
                })
                .thenSucceed();
    }

    /** 走完一步：矩阵损伤 8000 记到主人身上，书升到 26 级；最后一刻主人不在就停在最后一刻，回来立刻完成 */
    @GameTest(template = EMPTY, timeoutTicks = 1500)
    public static void enchantAscenderCompletesStepAndHoldsAtLastTick(GameTestHelper helper) {
        EnchantUpgraderBlockEntity be = place(helper, CENTER, Tier.ASCENDER);
        Step step = EnchantUpgrades.step(Tier.ASCENDER, 26);
        UUID id = UUID.randomUUID();
        ServerPlayer owner = ownerOnline(helper, be, id);
        IPlayerData data = PlayerDataEvents.get(owner);
        data.setPentacleTier(3);
        data.setMatrixDamage(1000);
        be.getInventory().setStackInSlot(EnchantUpgraderBlockEntity.SLOT_BOOK, book(Enchantments.SHARPNESS, 25));
        giveMaterials(be, step, 1);
        be.setTargetLevel(26);
        be.getESPEStorage().setESPE(be.getESPEStorage().getMaxESPE());
        helper.onEachTick(() -> be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU()));
        helper.startSequence()
                .thenIdle(100)
                .thenExecute(() -> {
                    helper.assertTrue(be.stepLevel() == 26 && be.progress() > 50, "应在做 26 级这一步，进度 " + be.progress());
                    ECProtection.clearOnlineOverride(id);
                })
                .thenIdle(step.ticks() + 20)
                .thenExecute(() -> {
                    helper.assertTrue(be.status() == Status.NO_OWNER && be.progress() == step.ticks() - 1,
                            "主人不在：停在最后一刻前，实际 " + be.status() + " / " + be.progress() + "（总 " + step.ticks() + "）");
                    helper.assertTrue(slot(be, EnchantUpgraderBlockEntity.SLOT_OUTPUT).isEmpty() && PlayerDataEvents.get(owner).getMatrixDamage() == 1000,
                            "还没完成、还没记损伤");
                    ECProtection.setOnlineOverride(owner);
                })
                .thenIdle(4)
                .thenExecute(() -> {
                    helper.assertTrue(level(slot(be, EnchantUpgraderBlockEntity.SLOT_OUTPUT)) == 26, "主人回来后应完成，书 26 级进输出格");
                    helper.assertTrue(PlayerDataEvents.get(owner).getMatrixDamage() == 1000 + 8000,
                            "矩阵损伤应加 8000，实际 " + PlayerDataEvents.get(owner).getMatrixDamage());
                    ECProtection.clearOnlineOverride(id);
                })
                .thenSucceed();
    }

    // ---------------- 铁砧 ----------------

    private static AnvilUpdateEvent anvil(GameTestHelper helper, ItemStack left, ItemStack right, String name) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        AnvilUpdateEvent event = new AnvilUpdateEvent(left, right, name, 0, player);
        MinecraftForge.EVENT_BUS.post(event);
        return event;
    }

    /** 不改原版铁砧：超上限的书放上去，EC5 不给结果、不取消，交给原版和别的附魔模组 */
    @GameTest(template = EMPTY)
    public static void enchantUpgradeLeavesAnvilAlone(GameTestHelper helper) {
        AnvilUpdateEvent event = anvil(helper, new ItemStack(Items.DIAMOND_SWORD), book(Enchantments.SHARPNESS, 25), "");
        helper.assertTrue(!event.isCanceled() && event.getOutput().isEmpty() && event.getCost() == 0,
                "超上限的书不该被 EC5 接管，实际 取消=" + event.isCanceled() + " 输出=" + event.getOutput() + " 经验=" + event.getCost());
        helper.succeed();
    }

    // ---------------- 水晶柱每秒结算 ----------------

    /** 水晶柱每 20 刻结算一次、折合每刻的产量不变：存量永远是「一秒的量」的整数倍，一段时间后和逐刻累加差不多 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void crystalPillarSettlesOncePerSecond(GameTestHelper helper) {
        BlockPos rel = new BlockPos(2, 1, 2);
        helper.setBlock(rel, ECMithriline.MITHRILINE_CRYSTAL_PILLAR.get());
        MithrilineCrystalPillarBlockEntity pillar = (MithrilineCrystalPillarBlockEntity) helper.getBlockEntity(rel);
        helper.assertTrue(MithrilineCrystalPillarBlockEntity.seesSky(helper.getLevel(), helper.absolutePos(rel)), "测试柱子应看得见天空");
        pillar.getESPEStorage().setESPE(0D);
        double second = MithrilineCrystalPillarBlockEntity.generation(helper.getLevel(), pillar.getTier()) * MithrilineCrystalPillarBlockEntity.SKY_CHECK_INTERVAL;
        helper.startSequence()
                .thenIdle(70)
                .thenExecute(() -> {
                    double stored = pillar.getESPEStorage().getESPE();
                    double ratio = stored / second;
                    helper.assertTrue(Math.abs(ratio - Math.rint(ratio)) < EPS && ratio >= 2D && ratio <= 4D,
                            "70 刻内应结算 2 ~ 4 次，存量是一秒量 " + second + " 的整数倍，实际 " + stored);
                })
                .thenSucceed();
    }
}
