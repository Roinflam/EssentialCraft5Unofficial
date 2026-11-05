package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IMRUHandlerItem;
import com.flameforge.essentialcraft5.blockentity.ChargingChamberBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MoonWellBlockEntity;
import com.flameforge.essentialcraft5.item.BoundGemItem;
import com.flameforge.essentialcraft5.menu.ChargingChamberMenu;
import com.flameforge.essentialcraft5.menu.MachineData;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.items.ItemStackHandler;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.mru.MRULink;
import com.flameforge.essentialcraft5.registry.ECBlocks;
import com.flameforge.essentialcraft5.registry.ECItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

/**
 * 框架的自动化测试，跑法：./gradlew runGameTestServer（独立服环境，顺带验证专用服不会加载客户端类）。
 * 只在开启 GameTest 时才会被加载，正式环境不影响。
 * 结构模板是 data/essentialcraft5/structures/empty.nbt（5×4×5 空气），方块都在测试里自己放。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class ECGameTests {

    private static final String EMPTY = "empty";

    private ECGameTests() {
    }

    private static ItemStack gemBoundTo(GameTestHelper helper, BlockPos relative) {
        ItemStack gem = new ItemStack(ECItems.BOUND_GEM.get());
        BoundGemItem.bind(gem, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(relative)), true);
        return gem;
    }

    /** 绑定宝石从创造 MRU 源拉 MRU，充能仓再把 MRU 充进灵魂碎片 */
    @GameTest(template = EMPTY)
    public static void chargingChamberPullsAndCharges(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 1);
        BlockPos chamber = new BlockPos(3, 1, 1);
        helper.setBlock(source, ECBlocks.CREATIVE_MRU_STORAGE.get());
        helper.setBlock(chamber, ECBlocks.CHARGING_CHAMBER.get());
        ChargingChamberBlockEntity be = (ChargingChamberBlockEntity) helper.getBlockEntity(chamber);
        be.getInventory().setStackInSlot(ChargingChamberBlockEntity.SLOT_GEM, gemBoundTo(helper, source));
        be.getInventory().setStackInSlot(ChargingChamberBlockEntity.SLOT_ITEM, new ItemStack(ECItems.SOUL_SHARD.get()));

        helper.succeedWhen(() -> {
            helper.assertTrue(be.getLinkStatus() == MRULink.Status.WORKING, "绑定宝石状态应为 WORKING，实际 " + be.getLinkStatus());
            helper.assertTrue(be.getMRUStorage().getMRU() > 0, "充能仓应拉到 MRU");
            IMRUHandlerItem shard = be.getInventory().getStackInSlot(ChargingChamberBlockEntity.SLOT_ITEM)
                    .getCapability(ECCapabilities.MRU_ITEM).orElseThrow(IllegalStateException::new);
            helper.assertTrue(shard.getMRU() == shard.getMaxMRU(), "灵魂碎片应被充满，实际 " + shard.getMRU());
        });
    }

    /** 红石信号让充能仓停止充能（拉 MRU 不受影响，与原作一致） */
    @GameTest(template = EMPTY)
    public static void chargingChamberStopsWithRedstone(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 1);
        BlockPos chamber = new BlockPos(3, 1, 1);
        helper.setBlock(source, ECBlocks.CREATIVE_MRU_STORAGE.get());
        helper.setBlock(chamber, ECBlocks.CHARGING_CHAMBER.get());
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.REDSTONE_BLOCK);
        ChargingChamberBlockEntity be = (ChargingChamberBlockEntity) helper.getBlockEntity(chamber);
        be.getInventory().setStackInSlot(ChargingChamberBlockEntity.SLOT_GEM, gemBoundTo(helper, source));
        be.getInventory().setStackInSlot(ChargingChamberBlockEntity.SLOT_ITEM, new ItemStack(ECItems.SOUL_SHARD.get()));

        helper.runAtTickTime(40, () -> {
            IMRUHandlerItem shard = be.getInventory().getStackInSlot(ChargingChamberBlockEntity.SLOT_ITEM)
                    .getCapability(ECCapabilities.MRU_ITEM).orElseThrow(IllegalStateException::new);
            helper.assertTrue(shard.getMRU() == 0, "有红石信号时不应充能，实际 " + shard.getMRU());
            helper.assertTrue(be.getMRUStorage().getMRU() > 0, "有红石信号时仍应拉 MRU");
            helper.succeed();
        });
    }

    /** 绑定宝石的各种失败状态 */
    @GameTest(template = EMPTY)
    public static void boundGemStatuses(GameTestHelper helper) {
        BlockPos chamber = new BlockPos(2, 1, 2);
        helper.setBlock(chamber, ECBlocks.CHARGING_CHAMBER.get());
        ChargingChamberBlockEntity be = (ChargingChamberBlockEntity) helper.getBlockEntity(chamber);
        BlockPos self = helper.absolutePos(chamber);
        Level level = helper.getLevel();

        ItemStack farGem = new ItemStack(ECItems.BOUND_GEM.get());
        BoundGemItem.bind(farGem, GlobalPos.of(level.dimension(), self.offset(17, 0, 0)), true);
        ItemStack otherDimension = new ItemStack(ECItems.BOUND_GEM.get());
        BoundGemItem.bind(otherDimension, GlobalPos.of(Level.NETHER, self.offset(1, 0, 0)), true);
        ItemStack self_ = new ItemStack(ECItems.BOUND_GEM.get());
        BoundGemItem.bind(self_, GlobalPos.of(level.dimension(), self), true);
        ItemStack emptyTarget = new ItemStack(ECItems.BOUND_GEM.get());
        BoundGemItem.bind(emptyTarget, GlobalPos.of(level.dimension(), self.offset(1, 0, 0)), true);

        check(helper, be, ItemStack.EMPTY, MRULink.Status.NO_GEM);
        check(helper, be, new ItemStack(ECItems.BOUND_GEM.get()), MRULink.Status.NOT_BOUND);
        check(helper, be, farGem, MRULink.Status.OUT_OF_RANGE);
        check(helper, be, otherDimension, MRULink.Status.WRONG_DIMENSION);
        check(helper, be, self_, MRULink.Status.BOUND_TO_SELF);
        check(helper, be, emptyTarget, MRULink.Status.NO_TILE);
        helper.succeed();
    }

    private static void check(GameTestHelper helper, ChargingChamberBlockEntity be, ItemStack gem, MRULink.Status expected) {
        MRULink.Status actual = MRULink.pull(helper.getLevel(), be.getBlockPos(), be.getMRUStorage(), gem,
                MRULink.DEFAULT_RANGE, false);
        helper.assertTrue(actual == expected, "应为 " + expected + "，实际 " + actual);
    }

    /** 漏斗、管道只能往 0 号槽塞绑定宝石，只能从 1 号槽取 */
    @GameTest(template = EMPTY)
    public static void automationRules(GameTestHelper helper) {
        BlockPos chamber = new BlockPos(2, 1, 2);
        helper.setBlock(chamber, ECBlocks.CHARGING_CHAMBER.get());
        ChargingChamberBlockEntity be = (ChargingChamberBlockEntity) helper.getBlockEntity(chamber);
        IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);

        ItemStack rejected = handler.insertItem(0, new ItemStack(Items.DIAMOND), false);
        helper.assertTrue(rejected.getCount() == 1, "0 号槽不应接受绑定宝石以外的物品");
        ItemStack gemLeft = handler.insertItem(0, new ItemStack(ECItems.BOUND_GEM.get()), false);
        helper.assertTrue(gemLeft.isEmpty(), "0 号槽应接受绑定宝石");
        helper.assertTrue(handler.extractItem(0, 1, false).isEmpty(), "0 号槽不应能被取出");

        handler.insertItem(1, new ItemStack(Items.DIAMOND), false);
        helper.assertTrue(handler.extractItem(1, 1, false).is(Items.DIAMOND), "1 号槽应能被取出");
        helper.succeed();
    }

    /** 月亮井：满月午夜、看得到天空时产出 MRU */
    @GameTest(template = EMPTY, batch = "moon_well")
    public static void moonWellGeneratesAtNight(GameTestHelper helper) {
        // 第 0 天 18000 = 满月的午夜
        helper.getLevel().setDayTime(18000);
        BlockPos well = new BlockPos(2, 1, 2);
        helper.setBlock(well, ECBlocks.MOON_WELL.get());
        MoonWellBlockEntity be = (MoonWellBlockEntity) helper.getBlockEntity(well);
        BlockPos absolute = helper.absolutePos(well);
        helper.assertTrue(MoonWellBlockEntity.potentialGeneration(helper.getLevel(), absolute) >= 1,
                "测试位置 y=" + absolute.getY() + " 太高，月亮井不会产出");
        helper.succeedWhen(() -> {
            Level level = helper.getLevel();
            helper.assertTrue(!level.isDay(), "应为夜晚，dayTime=" + level.getDayTime());
            helper.assertTrue(level.getMoonPhase() != 4, "不应是新月");
            // 看不到天空多半是测试世界不是超平坦（见 build.gradle 的 runGameTestServer），不是月亮井的问题
            helper.assertTrue(level.canSeeSky(absolute.above()), "测试位置 y=" + absolute.getY() + " 看不到天空，天空光照="
                    + level.getBrightness(LightLayer.SKY, absolute.above()));
            helper.assertTrue(be.getMRUStorage().getMRU() > 0, "月亮井夜里应产出 MRU");
        });
    }

    /** 月亮井：有红石信号时不产出 */
    @GameTest(template = EMPTY, batch = "moon_well")
    public static void moonWellStopsWithRedstone(GameTestHelper helper) {
        helper.getLevel().setDayTime(18000);
        BlockPos well = new BlockPos(2, 1, 2);
        helper.setBlock(well, ECBlocks.MOON_WELL.get());
        helper.setBlock(new BlockPos(1, 1, 2), Blocks.REDSTONE_BLOCK);
        MoonWellBlockEntity be = (MoonWellBlockEntity) helper.getBlockEntity(well);
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(be.getMRUStorage().getMRU() == 0, "有红石信号时不应产出，实际 " + be.getMRUStorage().getMRU());
            helper.succeed();
        });
    }

    /** 叠起来的月亮井都能发电（原作 setLightOpacity(0)，不挡天空） */
    @GameTest(template = EMPTY, batch = "moon_well")
    public static void stackedMoonWellsAllGenerate(GameTestHelper helper) {
        helper.getLevel().setDayTime(18000);
        BlockPos lower = new BlockPos(2, 1, 2);
        BlockPos upper = new BlockPos(2, 2, 2);
        helper.setBlock(lower, ECBlocks.MOON_WELL.get());
        helper.setBlock(upper, ECBlocks.MOON_WELL.get());
        MoonWellBlockEntity bottom = (MoonWellBlockEntity) helper.getBlockEntity(lower);
        MoonWellBlockEntity top = (MoonWellBlockEntity) helper.getBlockEntity(upper);
        helper.succeedWhen(() -> {
            helper.assertTrue(top.getMRUStorage().getMRU() > 0, "上面的月亮井应产出");
            helper.assertTrue(bottom.getMRUStorage().getMRU() > 0, "下面的月亮井也应产出（上面的月亮井不挡天空）");
        });
    }

    /** 挖掉机器：掉的是槽里的原物（槽被拆空），原地放回同种机器后旧菜单立即失效，不能再从旧方块实体里取东西 */
    @GameTest(template = EMPTY)
    public static void breakingMachineCannotDuplicate(GameTestHelper helper) {
        BlockPos chamber = new BlockPos(2, 1, 2);
        helper.setBlock(chamber, ECBlocks.CHARGING_CHAMBER.get());
        ChargingChamberBlockEntity old = (ChargingChamberBlockEntity) helper.getBlockEntity(chamber);
        old.getInventory().setStackInSlot(ChargingChamberBlockEntity.SLOT_ITEM, new ItemStack(ECItems.MATTER_OF_ETERNITY.get()));

        Player player = helper.makeMockPlayer();
        BlockPos absolute = helper.absolutePos(chamber);
        player.setPos(absolute.getX() + 0.5, absolute.getY() + 1, absolute.getZ() + 2.5);
        ChargingChamberMenu menu = new ChargingChamberMenu(1, player.getInventory(), old);
        helper.assertTrue(menu.stillValid(player), "打开时菜单应有效");
        helper.assertTrue(ECBlocks.CHARGING_CHAMBER.get().getMenuProvider(old.getBlockState(), helper.getLevel(), absolute) == null,
                "方块不应把方块实体当 MenuProvider 交给原版（旁观者右键会崩客户端）");

        helper.getLevel().destroyBlock(absolute, true);
        helper.assertTrue(old.getInventory().getStackInSlot(ChargingChamberBlockEntity.SLOT_ITEM).isEmpty(),
                "掉落后旧方块实体的槽应被拆空");
        helper.setBlock(chamber, ECBlocks.CHARGING_CHAMBER.get());
        helper.assertTrue(!menu.stillValid(player), "原地放回同种机器后旧菜单应失效");
        helper.succeed();
    }

    /** 读档时存档的槽位数和代码不同：范围内的物品保住，多出来的在第一次 tick 时掉在地上 */
    @GameTest(template = EMPTY)
    public static void inventorySizeMismatchKeepsItems(GameTestHelper helper) {
        BlockPos chamber = new BlockPos(2, 1, 2);
        helper.setBlock(chamber, ECBlocks.CHARGING_CHAMBER.get());
        ChargingChamberBlockEntity be = (ChargingChamberBlockEntity) helper.getBlockEntity(chamber);

        // 伪造一份 3 槽的存档：0 号宝石、2 号钻石（充能仓只有 2 槽）
        ItemStackHandler saved = new ItemStackHandler(3);
        saved.setStackInSlot(0, new ItemStack(ECItems.BOUND_GEM.get()));
        saved.setStackInSlot(2, new ItemStack(Items.DIAMOND, 5));
        CompoundTag tag = be.saveWithoutMetadata();
        tag.put("inventory", saved.serializeNBT());
        be.load(tag);

        helper.assertTrue(be.getInventory().getSlots() == 2, "槽位数应以代码为准");
        helper.assertTrue(be.getInventory().getStackInSlot(0).is(ECItems.BOUND_GEM.get()), "范围内的物品应保住");
        helper.succeedWhen(() -> helper.assertItemEntityCountIs(Items.DIAMOND, new BlockPos(2, 2, 2), 1.5, 5));
    }

    /** 界面同步的平衡值和服务端完全一致（贴近 1 的值不能被四舍五入成纯净） */
    @GameTest(template = EMPTY)
    public static void balanceSyncsExactly(GameTestHelper helper) {
        BlockPos chamber = new BlockPos(2, 1, 2);
        helper.setBlock(chamber, ECBlocks.CHARGING_CHAMBER.get());
        ChargingChamberBlockEntity be = (ChargingChamberBlockEntity) helper.getBlockEntity(chamber);
        be.getMRUStorage().setBalance(1.000025F);
        be.getMRUStorage().setMRU(4321);

        MachineData server = MachineData.server(be);
        MachineData client = MachineData.client(0);
        for (int i = 0; i < server.getCount(); i++) {
            // 原版菜单数据包按 short 传
            client.set(i, (short) server.get(i));
        }
        helper.assertTrue(client.balance() == 1.000025F, "平衡值应精确同步，实际 " + client.balance());
        helper.assertTrue(client.mru() == 4321 && client.maxMRU() == be.getMRUStorage().getMaxMRU(), "MRU 与上限应同步");
        helper.succeed();
    }

    /** 玩家消耗 MRU：从身上的储存物品扣，不够就不扣 */
    @GameTest(template = EMPTY)
    public static void playerDrainsFromStorage(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ItemStack shard = new ItemStack(ECItems.SOUL_SHARD.get());
        IMRUHandlerItem handler = shard.getCapability(ECCapabilities.MRU_ITEM).orElseThrow(IllegalStateException::new);
        handler.setMRU(300);
        player.getInventory().setItem(0, shard);

        helper.assertTrue(MRUDrain.tryDrainFromStorages(player, 100), "300 MRU 应够扣 100");
        helper.assertTrue(handler.getMRU() == 200, "扣完应剩 200，实际 " + handler.getMRU());
        helper.assertTrue(!MRUDrain.tryDrainFromStorages(player, 500), "200 MRU 不够扣 500");
        helper.assertTrue(handler.getMRU() == 200, "不够时不应扣，实际 " + handler.getMRU());
        helper.succeed();
    }
}
