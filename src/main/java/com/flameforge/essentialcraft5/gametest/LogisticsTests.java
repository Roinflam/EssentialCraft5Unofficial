package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ItemFilters;
import com.flameforge.essentialcraft5.block.FacingMachineBlock;
import com.flameforge.essentialcraft5.blockentity.MagicalHopperBlockEntity;
import com.flameforge.essentialcraft5.menu.FilterMenu;
import com.flameforge.essentialcraft5.menu.HeldItemMenu;
import com.flameforge.essentialcraft5.menu.MagicalHopperMenu;
import com.flameforge.essentialcraft5.registry.ECLogistics;
import com.flameforge.essentialcraft5.util.ECItemEntities;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 物流模块（ECLogistics）的 GameTest：魔力漏斗与物品过滤器（batch4 §3.6 的测试点）。
 * <p>
 * 漏斗的搬运用 {@link MagicalHopperBlockEntity#transferOnce()} 在同一刻里同步触发（不等它自己的 20 刻节奏，掉落物也来不及落地、合并），
 * 主人「在线」用 {@code ECProtection.setOnlineOverride(realPlayer(helper, 自己的 UUID, 名字))}，同一段代码里测完、succeed 之前清掉；
 * 每个测试用自己的随机 UUID，探测缓存互不影响。模拟领地用 {@link ProtectionFrameworkTests.Claims}。
 * 漏斗放在 empty_large 的 (5, 2, 5)，半径 3 的范围（7×7×7）整个在测试区里。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class LogisticsTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    private static final BlockPos HOPPER = new BlockPos(5, 2, 5);
    /** 漏斗朝东时的前方格 */
    private static final BlockPos FRONT = HOPPER.east();

    private LogisticsTests() {
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private static MagicalHopperBlockEntity placeHopper(GameTestHelper helper, BlockPos rel, Direction facing) {
        helper.setBlock(rel, ECLogistics.MAGICAL_HOPPER.get().defaultBlockState().setValue(FacingMachineBlock.FACING, facing));
        return (MagicalHopperBlockEntity) helper.getBlockEntity(rel);
    }

    /** 给漏斗记一个主人并把他当成在线，返回他的 UUID（succeed 之前 clearOnlineOverride） */
    private static UUID ownOnline(GameTestHelper helper, MagicalHopperBlockEntity hopper) {
        UUID id = UUID.randomUUID();
        hopper.setOwner(id, "hopper-owner");
        ECProtection.setOnlineOverride(ProtectionFrameworkTests.realPlayer(helper, id, "hopper-owner"));
        return id;
    }

    /** 在相对坐标 (x, y, z) 放一个不动的掉落物（y 取 .1，所在格就是 ⌊y⌋ 那一层） */
    private static ItemEntity drop(GameTestHelper helper, double x, double y, double z, ItemStack stack) {
        Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
        ItemEntity item = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, stack);
        item.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(item);
        return item;
    }

    /** 掉落物是否在相对格 rel 里（按所在格算） */
    private static boolean inCell(GameTestHelper helper, ItemEntity item, BlockPos rel) {
        return item.blockPosition().equals(helper.absolutePos(rel));
    }

    private static int countItems(GameTestHelper helper, Predicate<ItemStack> which) {
        AABB area = new AABB(helper.absolutePos(HOPPER)).inflate(5D);
        int total = 0;
        for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, area, ItemEntity::isAlive)) {
            if (which.test(item.getItem())) {
                total += item.getItem().getCount();
            }
        }
        return total;
    }

    private static ItemStack filter(boolean blacklist, ItemStack... samples) {
        ItemStack filter = new ItemStack(blacklist ? ECLogistics.BLACKLIST_FILTER.get() : ECLogistics.WHITELIST_FILTER.get());
        for (int i = 0; i < samples.length; i++) {
            if (!samples[i].isEmpty() && !ItemFilters.setSample(filter, i, samples[i])) {
                throw new IllegalStateException("LogisticsTests：样本放不进过滤器");
            }
        }
        return filter;
    }

    // ------------------------------------------------------------------
    // 魔力漏斗
    // ------------------------------------------------------------------

    /** 没有领地（纯 Forge、单人）时照常工作：朝东的漏斗把 3 格内的掉落物瞬移到前方格中心，数量不变、速度清零；前方格里的不再动 */
    @GameTest(template = EMPTY_LARGE)
    public static void hopperMovesItemsWithoutClaims(GameTestHelper helper) {
        MagicalHopperBlockEntity hopper = placeHopper(helper, HOPPER, Direction.EAST);
        UUID owner = ownOnline(helper, hopper);
        ItemEntity cobble = drop(helper, 2.5D, 2.1D, 5.5D, new ItemStack(Items.COBBLESTONE, 13));
        cobble.setDeltaMovement(0.3D, 0.2D, 0D);
        ItemEntity far = drop(helper, 9.5D, 2.1D, 5.5D, new ItemStack(Items.DIRT));   // x=9 在半径 3 之外（漏斗在 5，前方格 6 以东另算）
        try {
            int moved = hopper.transferOnce();
            helper.assertTrue(moved == 1, "应搬动 1 个掉落物，实际 " + moved);
            helper.assertTrue(inCell(helper, cobble, FRONT) && cobble.getItem().getCount() == 13 && cobble.isAlive(), "圆石应在前方格、数量不变");
            helper.assertTrue(cobble.getDeltaMovement().lengthSqr() == 0D, "瞬移后应清掉速度");
            helper.assertTrue(inCell(helper, far, new BlockPos(9, 2, 5)), "范围外的不动");
            helper.assertTrue(hopper.transferOnce() == 0, "已经在前方格里的不应再搬");
        } finally {
            ECProtection.clearOnlineOverride(owner);
        }
        helper.succeed();
    }

    /** 有红石信号就停工（电平开关），信号撤了照常 */
    @GameTest(template = EMPTY_LARGE)
    public static void hopperRedstoneStops(GameTestHelper helper) {
        MagicalHopperBlockEntity hopper = placeHopper(helper, HOPPER, Direction.EAST);
        UUID owner = ownOnline(helper, hopper);
        ItemEntity item = drop(helper, 3.5D, 2.1D, 5.5D, new ItemStack(Items.COBBLESTONE));
        try {
            helper.setBlock(HOPPER.above(), Blocks.REDSTONE_BLOCK);
            helper.assertTrue(hopper.transferOnce() == 0 && inCell(helper, item, new BlockPos(3, 2, 5)), "有红石信号时不应搬");
            helper.assertTrue(hopper.redstoneCode() == 1, "界面应显示红石停工");
            helper.setBlock(HOPPER.above(), Blocks.AIR);
            helper.assertTrue(hopper.transferOnce() == 1 && inCell(helper, item, FRONT), "信号撤掉后应照常搬");
        } finally {
            ECProtection.clearOnlineOverride(owner);
        }
        helper.succeed();
    }

    /** 被拒的格子不动、不扣东西：物品所在格被拒 → 那一堆不动，别的照搬；前方格被拒 → 什么都不搬，也不去探测物品格 */
    @GameTest(template = EMPTY_LARGE)
    public static void hopperDeniedCellsUntouched(GameTestHelper helper) {
        MagicalHopperBlockEntity hopper = placeHopper(helper, HOPPER, Direction.EAST);
        UUID owner = ownOnline(helper, hopper);
        BlockPos deniedCell = new BlockPos(3, 2, 5);
        BlockPos freeCell = new BlockPos(4, 2, 4);
        ItemEntity denied = drop(helper, 3.5D, 2.1D, 5.5D, new ItemStack(Items.DIAMOND, 3));
        ItemEntity free = drop(helper, 4.5D, 2.1D, 4.5D, new ItemStack(Items.COBBLESTONE, 5));
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(deniedCell)) {
            helper.assertTrue(hopper.transferOnce() == 1, "只应搬能动的那一格里的");
            helper.assertTrue(inCell(helper, denied, deniedCell) && denied.getItem().getCount() == 3, "被拒格子里的钻石应原地不动、数量不变");
            helper.assertTrue(inCell(helper, free, FRONT), "能动的格子里的应搬走");
            helper.assertTrue(claims.probes(deniedCell) == 1 && claims.breaksBy(deniedCell, owner) == 1, "被拒的格子应以主人身份探测一次");

            // 前方格也被拒：整轮不搬，物品格不探测
            ItemEntity other = drop(helper, 4.5D, 2.1D, 6.5D, new ItemStack(Items.IRON_INGOT));
            claims.deny(FRONT);
            ECProtection.forget(owner);
            helper.assertTrue(hopper.transferOnce() == 0, "前方格被拒时什么都不应搬");
            helper.assertTrue(inCell(helper, other, new BlockPos(4, 2, 6)), "前方格被拒时铁锭不应动");
            helper.assertTrue(claims.breaks(new BlockPos(4, 2, 6)) == 0, "前方格被拒时不应再去探测物品格");
        } finally {
            ECProtection.clearOnlineOverride(owner);
        }
        helper.succeed();
    }

    /** 主人不在线不搬；没有主人（不是玩家放下的，专用服）不搬 */
    @GameTest(template = EMPTY_LARGE)
    public static void hopperNeedsOnlineOwner(GameTestHelper helper) {
        MagicalHopperBlockEntity hopper = placeHopper(helper, HOPPER, Direction.EAST);
        ItemEntity item = drop(helper, 3.5D, 2.1D, 5.5D, new ItemStack(Items.COBBLESTONE));
        helper.assertTrue(hopper.transferOnce() == 0 && inCell(helper, item, new BlockPos(3, 2, 5)), "没有主人时不应搬");
        helper.assertTrue(hopper.ownerStateCode() == ECProtection.OwnerState.NONE.ordinal() + 1, "界面应显示没有主人");

        hopper.setOwner(UUID.randomUUID(), "offline-owner");
        ECProtection.Config.setRequireOwnerOnlineForTest(true);
        try {
            helper.assertTrue(hopper.transferOnce() == 0 && inCell(helper, item, new BlockPos(3, 2, 5)), "主人不在线时不应搬");
            helper.assertTrue(hopper.ownerStateCode() == ECProtection.OwnerState.OFFLINE.ordinal() + 1, "界面应显示主人不在线");
        } finally {
            ECProtection.Config.setRequireOwnerOnlineForTest(null);
        }
        helper.succeed();
    }

    /** 指定了别人拾取的不碰、指定给主人的照搬；永远捡不起来的（展示品）不碰 */
    @GameTest(template = EMPTY_LARGE)
    public static void hopperRespectsItemOwnership(GameTestHelper helper) {
        MagicalHopperBlockEntity hopper = placeHopper(helper, HOPPER, Direction.EAST);
        UUID owner = ownOnline(helper, hopper);
        ItemEntity others = drop(helper, 3.5D, 2.1D, 5.5D, new ItemStack(Items.DIAMOND));
        others.setTarget(UUID.randomUUID());
        ItemEntity mine = drop(helper, 3.5D, 2.1D, 4.5D, new ItemStack(Items.EMERALD));
        mine.setTarget(owner);
        ItemEntity display = drop(helper, 4.5D, 2.1D, 6.5D, new ItemStack(Items.GOLD_INGOT));
        display.setNeverPickUp();
        try {
            helper.assertTrue(ECItemEntities.target(others) != null && ECItemEntities.neverPickUp(display), "测试前提：拾取人和展示品标记应读得出");
            helper.assertTrue(hopper.transferOnce() == 1, "只应搬指定给主人的那一个");
            helper.assertTrue(inCell(helper, mine, FRONT), "指定给主人的应照搬");
            helper.assertTrue(inCell(helper, others, new BlockPos(3, 2, 5)), "指定给别人的不应碰");
            helper.assertTrue(inCell(helper, display, new BlockPos(4, 2, 6)), "永远捡不起来的不应碰");
        } finally {
            ECProtection.clearOnlineOverride(owner);
        }
        helper.succeed();
    }

    /** 前方是箱子：直接装进去，放得下多少装多少，其余留在地上（总数守恒）；前方是石头（实心、不是容器）：不搬 */
    @GameTest(template = EMPTY_LARGE)
    public static void hopperFrontContainerAndSolid(GameTestHelper helper) {
        MagicalHopperBlockEntity hopper = placeHopper(helper, HOPPER, Direction.EAST);
        UUID owner = ownOnline(helper, hopper);
        helper.setBlock(FRONT, Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(FRONT);
        for (int i = 0; i < chest.getContainerSize() - 1; i++) {
            chest.setItem(i, new ItemStack(Items.DIRT, 64));
        }
        drop(helper, 3.5D, 2.1D, 5.5D, new ItemStack(Items.COBBLESTONE, 64));
        drop(helper, 3.5D, 2.1D, 4.5D, new ItemStack(Items.COBBLESTONE, 64));
        drop(helper, 4.5D, 2.1D, 6.5D, new ItemStack(Items.COBBLESTONE, 64));
        try {
            helper.assertTrue(hopper.transferOnce() == 1, "箱子只剩 1 格，应只装进 1 堆");
            ItemStack last = chest.getItem(chest.getContainerSize() - 1);
            helper.assertTrue(last.is(Items.COBBLESTONE) && last.getCount() == 64, "最后一格应是 64 个圆石");
            int onGround = countItems(helper, stack -> stack.is(Items.COBBLESTONE));
            helper.assertTrue(onGround == 128, "剩下两堆应留在地上（总数守恒），实际地上 " + onGround);
            helper.assertTrue(hopper.transferOnce() == 0, "箱子满了以后不应再搬");

            // 前方换成石头：不是容器又挡着，不搬（原作会把东西塞进石头里）。先清空箱子，免得拆箱子撒一地
            chest.clearContent();
            helper.setBlock(FRONT, Blocks.STONE);
            helper.assertTrue(hopper.transferOnce() == 0 && countItems(helper, stack -> stack.is(Items.COBBLESTONE)) == 128, "前方实心时不应搬");
            // 前方是另一台魔力漏斗：当实心处理，地上的过滤器不会被塞进它的过滤器格
            MagicalHopperBlockEntity next = placeHopper(helper, FRONT, Direction.EAST);
            drop(helper, 3.5D, 3.1D, 5.5D, filter(false, new ItemStack(Items.STONE)));
            helper.assertTrue(hopper.transferOnce() == 0 && next.getInventory().getStackInSlot(0).isEmpty(), "不应把过滤器塞进下一台漏斗");
        } finally {
            ECProtection.clearOnlineOverride(owner);
        }
        helper.succeed();
    }

    /** 过滤器：白名单只搬样本里有的，黑名单搬样本以外的 */
    @GameTest(template = EMPTY_LARGE)
    public static void hopperUsesFilter(GameTestHelper helper) {
        MagicalHopperBlockEntity hopper = placeHopper(helper, HOPPER, Direction.EAST);
        UUID owner = ownOnline(helper, hopper);
        try {
            hopper.getInventory().setStackInSlot(MagicalHopperBlockEntity.SLOT_FILTER, filter(false, new ItemStack(Items.COBBLESTONE)));
            ItemEntity cobble = drop(helper, 3.5D, 2.1D, 5.5D, new ItemStack(Items.COBBLESTONE));
            ItemEntity dirt = drop(helper, 3.5D, 2.1D, 4.5D, new ItemStack(Items.DIRT));
            helper.assertTrue(hopper.transferOnce() == 1 && inCell(helper, cobble, FRONT) && inCell(helper, dirt, new BlockPos(3, 2, 4)),
                    "白名单只放圆石时只应搬圆石");

            hopper.getInventory().setStackInSlot(MagicalHopperBlockEntity.SLOT_FILTER, filter(true, new ItemStack(Items.COBBLESTONE)));
            ItemEntity cobble2 = drop(helper, 4.5D, 2.1D, 6.5D, new ItemStack(Items.COBBLESTONE));
            helper.assertTrue(hopper.transferOnce() == 1 && inCell(helper, dirt, FRONT) && inCell(helper, cobble2, new BlockPos(4, 2, 6)),
                    "黑名单拦圆石时只应搬泥土");
            helper.assertFalse(hopper.getUpdateTag().contains("inventory"), "更新包不应带过滤器（物品栏）");
        } finally {
            ECProtection.clearOnlineOverride(owner);
        }
        helper.succeed();
    }

    /** 一轮最多 64 个：100 个掉落物堆在两格里，第一轮 64 个、第二轮剩下的 36 个 */
    @GameTest(template = EMPTY_LARGE)
    public static void hopperCapsPerRound(GameTestHelper helper) {
        MagicalHopperBlockEntity hopper = placeHopper(helper, HOPPER, Direction.EAST);
        UUID owner = ownOnline(helper, hopper);
        for (int i = 0; i < 100; i++) {
            drop(helper, i % 2 == 0 ? 3.5D : 4.5D, 2.1D, 4.5D, new ItemStack(Items.COBBLESTONE));
        }
        try {
            int first = hopper.transferOnce();
            helper.assertTrue(first == MagicalHopperBlockEntity.Config.maxItems(), "第一轮应正好搬上限个，实际 " + first);
            int second = hopper.transferOnce();
            helper.assertTrue(first + second == 100, "第二轮应搬完剩下的，实际 " + second);
            helper.assertTrue(countItems(helper, stack -> stack.is(Items.COBBLESTONE)) == 100, "总数应不变");
        } finally {
            ECProtection.clearOnlineOverride(owner);
        }
        helper.succeed();
    }

    /** 过滤器格只收过滤器，Shift 点能放进去；挖掉漏斗：漏斗和过滤器各掉一个 */
    @GameTest(template = EMPTY_LARGE)
    public static void hopperMenuAndDrops(GameTestHelper helper) {
        MagicalHopperBlockEntity hopper = placeHopper(helper, HOPPER, Direction.EAST);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        Vec3 near = Vec3.atCenterOf(helper.absolutePos(HOPPER.north()));
        player.moveTo(near.x, near.y, near.z);
        MagicalHopperMenu menu = new MagicalHopperMenu(1, player.getInventory(), hopper);
        helper.assertFalse(menu.slots.get(0).mayPlace(new ItemStack(Items.STONE)), "过滤器格不应收普通物品");
        helper.assertTrue(menu.slots.get(0).mayPlace(new ItemStack(ECLogistics.BLACKLIST_FILTER.get())), "过滤器格应收过滤器");
        player.getInventory().setItem(9, filter(false, new ItemStack(Items.DIRT)));
        menu.quickMoveStack(player, 1);   // 菜单 1 号 = 背包 9 号
        helper.assertTrue(ItemFilters.isFilter(hopper.getInventory().getStackInSlot(0)) && player.getInventory().getItem(9).isEmpty(),
                "Shift 点过滤器应放进过滤器格");
        helper.assertTrue(hopper.getInventory().getStackInSlot(0).getCount() == 1, "过滤器格应只有 1 个");

        BlockPos abs = helper.absolutePos(HOPPER);
        helper.getLevel().destroyBlock(abs, true);
        AABB area = new AABB(abs).inflate(2D);
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area, ItemEntity::isAlive);
        int filters = drops.stream().filter(e -> ItemFilters.isFilter(e.getItem())).mapToInt(e -> e.getItem().getCount()).sum();
        int hoppers = drops.stream().filter(e -> e.getItem().is(ECLogistics.MAGICAL_HOPPER.get().asItem())).mapToInt(e -> e.getItem().getCount()).sum();
        helper.assertTrue(filters == 1, "过滤器应只掉一个，实际 " + filters);
        helper.assertTrue(hoppers == 1, "漏斗应掉自己一个，实际 " + hoppers);
        helper.assertFalse(menu.stillValid(player), "方块没了界面应失效");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 物品过滤器
    // ------------------------------------------------------------------

    /** 两种过滤器实现框架接口、进 essentialcraft5:filters 标签（标签由 datagen 生成，先跑 runData） */
    @GameTest(template = EMPTY)
    public static void filterItemsRegistered(GameTestHelper helper) {
        ItemStack white = new ItemStack(ECLogistics.WHITELIST_FILTER.get());
        ItemStack black = new ItemStack(ECLogistics.BLACKLIST_FILTER.get());
        helper.assertTrue(ItemFilters.isFilter(white) && !ItemFilters.isBlacklist(white) && !ItemFilters.isAdvanced(white), "白名单过滤器");
        helper.assertTrue(ItemFilters.isFilter(black) && ItemFilters.isBlacklist(black), "黑名单过滤器");
        helper.assertTrue(white.is(ItemFilters.FILTERS) && black.is(ItemFilters.FILTERS), "两种过滤器都应在 essentialcraft5:filters 标签里");
        helper.assertTrue(white.getMaxStackSize() == 1 && black.getMaxStackSize() == 1, "过滤器堆叠 1");
        helper.succeed();
    }

    /** 幽灵格：左键设样本不耗光标、立刻写进手上那件；右键清空；界面开着时对手持格的点、数字键、丢弃都无效 */
    @GameTest(template = EMPTY)
    public static void filterMenuGhostSlots(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        Inventory inventory = player.getInventory();
        inventory.selected = 0;
        ItemStack held = new ItemStack(ECLogistics.WHITELIST_FILTER.get());
        inventory.setItem(0, held);
        FilterMenu menu = new FilterMenu(0, inventory, InteractionHand.MAIN_HAND);
        helper.assertTrue(menu.stillValid(player), "刚打开应有效");

        menu.setCarried(new ItemStack(Items.DIAMOND, 5));
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(Items.DIAMOND) && menu.getCarried().getCount() == 5, "设样本不应消耗光标上的东西");
        helper.assertTrue(menu.sample(0).is(Items.DIAMOND) && menu.sample(0).getCount() == 1, "样本应是 1 个钻石");
        helper.assertTrue(ItemFilters.samples(held).get(0).is(Items.DIAMOND), "样本应立刻写进手上的过滤器");
        helper.assertTrue(ItemFilters.accepts(held, new ItemStack(Items.DIAMOND, 64)) && !ItemFilters.accepts(held, new ItemStack(Items.DIRT)),
                "写好的过滤器应只放行钻石");
        menu.clicked(0, 1, ClickType.PICKUP, player);
        helper.assertTrue(menu.sample(0).isEmpty() && ItemFilters.samples(held).get(0).isEmpty(), "右键应清空并写回");
        menu.setCarried(ItemStack.EMPTY);

        // 手持那格：菜单 36 + 0（快捷栏 0 号）是锁定槽
        int heldMenuSlot = 36;
        helper.assertTrue(menu.slots.get(heldMenuSlot) instanceof HeldItemMenu.LockedSlot, "手持那格应锁住");
        menu.clicked(heldMenuSlot, 0, ClickType.PICKUP, player);
        menu.clicked(heldMenuSlot, 0, ClickType.THROW, player);
        menu.clicked(20, 0, ClickType.SWAP, player);   // 数字键 1（快捷栏 0 = 手持那格）换背包 20 号
        menu.clicked(1, 0, ClickType.SWAP, player);
        helper.assertTrue(inventory.getItem(0) == held && menu.getCarried().isEmpty() && menu.stillValid(player), "手持的过滤器应拿不走、换不走、丢不掉");
        helper.assertTrue(menu.sample(1).isEmpty(), "数字键换手持格对样本格也应无效");
        helper.succeed();
    }

    /**
     * 判定：黑名单第 1 格空、第 2 格圆石 → 圆石被拦（原作只看第 1 格）；空白名单全拒、空黑名单全放；
     * 两层套娃生效、第三层放不进（界面里也放不进）；大 NBT 的书存哈希、改一个字不放行；附魔剑和普通剑不互相匹配。
     */
    @GameTest(template = EMPTY)
    public static void filterMatching(GameTestHelper helper) {
        ItemStack cobble = new ItemStack(Items.COBBLESTONE, 64);
        ItemStack dirt = new ItemStack(Items.DIRT);
        ItemStack gap = filter(true, ItemStack.EMPTY, new ItemStack(Items.COBBLESTONE));
        helper.assertTrue(!ItemFilters.accepts(gap, cobble) && ItemFilters.accepts(gap, dirt), "黑名单第 1 格空时仍应按第 2 格拦圆石");
        helper.assertFalse(ItemFilters.accepts(filter(false), dirt), "空白名单什么都不放行");
        helper.assertTrue(ItemFilters.accepts(filter(true), dirt), "空黑名单什么都放行");

        ItemStack inner = filter(false, new ItemStack(Items.COBBLESTONE));
        ItemStack outerWhite = filter(false, inner);
        ItemStack outerBlack = filter(true, inner);
        helper.assertTrue(ItemFilters.accepts(outerWhite, cobble) && !ItemFilters.accepts(outerWhite, dirt), "白名单里套白名单：内层放行的才收");
        helper.assertTrue(!ItemFilters.accepts(outerBlack, cobble) && ItemFilters.accepts(outerBlack, dirt), "黑名单里套白名单：内层放行的被拦");
        ItemStack third = new ItemStack(ECLogistics.WHITELIST_FILTER.get());
        helper.assertFalse(ItemFilters.setSample(third, 0, outerWhite), "套了一层的过滤器不应再被放进别的过滤器（第三层）");
        helper.assertTrue(ItemFilters.samples(third).get(0).isEmpty(), "放不进时过滤器不应变");

        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        player.getInventory().selected = 0;
        player.getInventory().setItem(0, third);
        FilterMenu menu = new FilterMenu(0, player.getInventory(), InteractionHand.MAIN_HAND);
        menu.setCarried(outerWhite);
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.sample(0).isEmpty() && ItemFilters.samples(third).get(0).isEmpty(), "界面里第三层也应放不进");
        menu.setCarried(inner);
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(ItemFilters.isFilter(ItemFilters.samples(third).get(0)), "界面里第二层应放得进");
        menu.setCarried(ItemStack.EMPTY);

        ItemStack book = bigBook('a');
        ItemStack bookFilter = filter(false, book);
        helper.assertTrue(ItemFilters.accepts(bookFilter, bigBook('a')) && !ItemFilters.accepts(bookFilter, bigBook('b')), "大书按内容哈希比：同内容放行、改一字不放行");
        helper.assertTrue(bookFilter.getTag() != null && bookFilter.getTag().sizeInBytes() < 2048, "大书样本只应存 id + 哈希");

        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        ItemStack enchanted = new ItemStack(Items.IRON_SWORD);
        enchanted.enchant(Enchantments.SHARPNESS, 1);
        helper.assertFalse(ItemFilters.accepts(filter(false, sword), enchanted), "普通剑的样本不应放行附魔剑");
        helper.assertFalse(ItemFilters.accepts(filter(false, enchanted), sword), "附魔剑的样本不应放行普通剑");
        helper.succeed();
    }

    /** 一本约 200 KB（按 sizeInBytes）的书：100 页、每页 1000 个 fill 字符 */
    private static ItemStack bigBook(char fill) {
        ItemStack book = new ItemStack(Items.WRITABLE_BOOK);
        ListTag pages = new ListTag();
        for (int i = 0; i < 100; i++) {
            pages.add(StringTag.valueOf(String.valueOf(fill).repeat(1000)));
        }
        book.getOrCreateTag().put("pages", pages);
        return book;
    }
}
