package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.MagicalChestBlock;
import com.flameforge.essentialcraft5.block.MagicalDisplayBlock;
import com.flameforge.essentialcraft5.blockentity.MagicalChestBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MagicalDisplayBlockEntity;
import com.flameforge.essentialcraft5.menu.MagicalChestMenu;
import com.flameforge.essentialcraft5.recipe.CrafterGrid;
import com.flameforge.essentialcraft5.registry.ECStorage;
import com.flameforge.essentialcraft5.registry.ECVoid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * 魔力箱、虚空箱与显示屏（ECStorage）的自动化测试（./gradlew runGameTestServer，先 runData：掉落表是生成的）。
 * <p>
 * 这两个方块只动自己的格子和物品栏、不需要主人（policy §4.3 规划补 ②），没有 ECProtection 限制的功能，
 * 所以没有「被拒的格子」测试；下面全部是在没有任何领地的情况下跑的（= 纯 Forge / 单人照常工作）。
 * 别人能不能开你的箱子、点你的屏，是领地插件对右键交互的判断，不在本模组代码里。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class StorageTests {

    private static final String EMPTY = "empty";
    /** 箱子在测试区里的位置 */
    private static final BlockPos CHEST = new BlockPos(2, 1, 2);

    private StorageTests() {
    }

    // ------------------------------------------------------------------
    // 魔力箱
    // ------------------------------------------------------------------

    private static MagicalChestBlockEntity placeChest(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ECStorage.MAGICAL_CHEST.get());
        return (MagicalChestBlockEntity) helper.getBlockEntity(pos);
    }

    /** 第 i 格放 i + 1 个圆石（1~54，合计 1485），最后一格换成带名字的钻石 */
    private static void fillChest(MagicalChestBlockEntity chest, boolean namedLast) {
        int slots = chest.getInventory().getSlots();
        for (int i = 0; i < slots; i++) {
            chest.getInventory().setStackInSlot(i, new ItemStack(Items.COBBLESTONE, i + 1));
        }
        if (namedLast) {
            ItemStack named = new ItemStack(Items.DIAMOND, 7);
            named.setHoverName(Component.literal("ec5-test"));
            chest.getInventory().setStackInSlot(slots - 1, named);
        }
    }

    /** 54 格存读档都在（含 NBT），格子不错位 */
    @GameTest(template = EMPTY)
    public static void storageChestSavesAllSlots(GameTestHelper helper) {
        MagicalChestBlockEntity chest = placeChest(helper, CHEST);
        helper.assertTrue(chest.getInventory().getSlots() == 54, "魔力箱应有 54 格");
        fillChest(chest, true);
        CompoundTag saved = chest.saveWithoutMetadata();
        MagicalChestBlockEntity copy = new MagicalChestBlockEntity(ECStorage.MAGICAL_CHEST_BE.get(),
                helper.absolutePos(CHEST), chest.getBlockState());
        copy.load(saved);
        for (int i = 0; i < 54; i++) {
            helper.assertTrue(ItemStack.matches(chest.getInventory().getStackInSlot(i), copy.getInventory().getStackInSlot(i)),
                    "第 " + i + " 格读档后不一致");
        }
        helper.assertTrue("ec5-test".equals(copy.getInventory().getStackInSlot(53).getHoverName().getString()), "最后一格的名字丢了");
        helper.succeed();
    }

    /** 挖掉：内容全部撒出来（圆石合计 1485 个 + 7 个钻石），掉 1 个不带 BlockEntityTag 的魔力箱 */
    @GameTest(template = EMPTY)
    public static void storageChestBreakSpillsContents(GameTestHelper helper) {
        MagicalChestBlockEntity chest = placeChest(helper, CHEST);
        fillChest(chest, true);
        int cobble = 53 * 54 / 2;
        helper.getLevel().destroyBlock(helper.absolutePos(CHEST), true);
        helper.assertItemEntityCountIs(Items.COBBLESTONE, CHEST, 3D, cobble);
        helper.assertItemEntityCountIs(Items.DIAMOND, CHEST, 3D, 7);
        helper.assertItemEntityCountIs(ECStorage.MAGICAL_CHEST.get().asItem(), CHEST, 3D, 1);
        for (ItemEntity item : helper.getEntities(EntityType.ITEM, CHEST, 3D)) {
            if (item.getItem().is(ECStorage.MAGICAL_CHEST.get().asItem())) {
                helper.assertTrue(item.getItem().getTagElement("BlockEntityTag") == null, "掉落的魔力箱不应带内容");
            }
        }
        helper.succeed();
    }

    /** 开着界面时方块被挖：界面失效、槽里取不到东西，地上的数量不多不少（不复制） */
    @GameTest(template = EMPTY)
    public static void storageChestMenuInvalidAfterBreak(GameTestHelper helper) {
        MagicalChestBlockEntity chest = placeChest(helper, CHEST);
        chest.getInventory().setStackInSlot(0, new ItemStack(Items.DIAMOND, 64));
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        MagicalChestMenu menu = (MagicalChestMenu) chest.createMenu(1, player.getInventory(), player);
        helper.assertTrue(menu != null && menu.stillValid(player), "放好的箱子界面应有效");
        helper.assertTrue(menu.getSlot(0).getItem().getCount() == 64, "菜单第 0 格应看到 64 个钻石");

        helper.getLevel().destroyBlock(helper.absolutePos(CHEST), false);
        helper.assertFalse(menu.stillValid(player), "方块没了界面应失效");
        helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), "方块没了菜单槽里不应还有东西");
        helper.assertTrue(menu.quickMoveStack(player, 0).isEmpty(), "Shift 点击不应再取出东西");
        helper.assertFalse(player.getInventory().contains(new ItemStack(Items.DIAMOND)), "玩家不应拿到钻石");
        helper.assertItemEntityCountIs(Items.DIAMOND, CHEST, 3D, 64);
        menu.removed(player);
        helper.succeed();
    }

    /** 更新包不带物品栏；有人开着时只带「开着」 */
    @GameTest(template = EMPTY)
    public static void storageChestUpdateTagHasNoItems(GameTestHelper helper) {
        MagicalChestBlockEntity chest = placeChest(helper, CHEST);
        fillChest(chest, true);
        CompoundTag closed = chest.getUpdateTag();
        helper.assertTrue(closed.isEmpty(), "没人开着时更新包应为空，实际 " + closed);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        MagicalChestMenu menu = (MagicalChestMenu) chest.createMenu(1, player.getInventory(), player);
        CompoundTag open = chest.getUpdateTag();
        helper.assertTrue(open.getBoolean("open") && open.size() == 1, "开着时更新包只应有 open，实际 " + open);
        menu.removed(player);
        helper.succeed();
    }

    /** 开合人数：建菜单 +1，关界面 -1 */
    @GameTest(template = EMPTY)
    public static void storageChestOpenerCount(GameTestHelper helper) {
        MagicalChestBlockEntity chest = placeChest(helper, CHEST);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        MagicalChestMenu menu = (MagicalChestMenu) chest.createMenu(1, player.getInventory(), player);
        helper.assertTrue(chest.getOpenerCount() == 1, "打开后人数应为 1，实际 " + chest.getOpenerCount());
        menu.removed(player);
        helper.assertTrue(chest.getOpenerCount() == 0, "关掉后人数应为 0，实际 " + chest.getOpenerCount());
        helper.succeed();
    }

    /**
     * Shift 点击并进箱子里已有的堆叠（原版菜单原地改数量、只调 slot.setChanged）也要让箱子标记存盘：
     * 否则重启后回到旧数量（Forge 的 SlotItemHandler.setChanged 什么都不做）。
     */
    @GameTest(template = EMPTY)
    public static void storageChestShiftMergeMarksDirty(GameTestHelper helper) {
        MagicalChestBlockEntity chest = placeChest(helper, CHEST);
        chest.getInventory().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 10));
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        player.getInventory().setItem(9, new ItemStack(Items.COBBLESTONE, 10));
        MagicalChestMenu menu = (MagicalChestMenu) chest.createMenu(1, player.getInventory(), player);
        LevelChunk chunk = helper.getLevel().getChunkAt(helper.absolutePos(CHEST));
        chunk.setUnsaved(false);
        // 菜单第 54 格 = 玩家物品栏第 9 格（主背包第一格）
        menu.quickMoveStack(player, 54);
        helper.assertTrue(chest.getInventory().getStackInSlot(0).getCount() == 20, "应并进第 0 格成 20 个");
        helper.assertTrue(chunk.isUnsaved(), "并进已有堆叠后箱子所在区块应标记为要存盘");
        CompoundTag saved = chest.saveWithoutMetadata();
        MagicalChestBlockEntity copy = new MagicalChestBlockEntity(ECStorage.MAGICAL_CHEST_BE.get(),
                helper.absolutePos(CHEST), chest.getBlockState());
        copy.load(saved);
        helper.assertTrue(copy.getInventory().getStackInSlot(0).getCount() == 20, "存读档后应是 20 个");
        menu.removed(player);
        helper.succeed();
    }

    /** 比较器：空 0；一格满 1；全满 15（和原版箱子同一套算法） */
    @GameTest(template = EMPTY)
    public static void storageChestComparator(GameTestHelper helper) {
        MagicalChestBlockEntity chest = placeChest(helper, CHEST);
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(CHEST);
        BlockState state = level.getBlockState(abs);
        helper.assertTrue(state.hasAnalogOutputSignal(), "魔力箱应有比较器输出");
        helper.assertTrue(state.getAnalogOutputSignal(level, abs) == 0, "空箱应输出 0");
        chest.getInventory().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));
        helper.assertTrue(state.getAnalogOutputSignal(level, abs) == 1, "一格满应输出 1");
        for (int i = 0; i < 54; i++) {
            chest.getInventory().setStackInSlot(i, new ItemStack(Items.COBBLESTONE, 64));
        }
        helper.assertTrue(state.getAnalogOutputSignal(level, abs) == 15, "全满应输出 15");
        helper.succeed();
    }

    /** 漏斗从上面塞、从下面抽都行（物品能力所有面） */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void storageChestHoppersInAndOut(GameTestHelper helper) {
        BlockPos chestPos = new BlockPos(2, 2, 2);
        BlockPos above = chestPos.above();
        BlockPos below = chestPos.below();
        helper.setBlock(below, Blocks.HOPPER);
        MagicalChestBlockEntity chest = placeChest(helper, chestPos);
        helper.setBlock(above, Blocks.HOPPER);
        chest.getInventory().setStackInSlot(0, new ItemStack(Items.DIAMOND, 3));
        ((HopperBlockEntity) helper.getBlockEntity(above)).setItem(0, new ItemStack(Items.COBBLESTONE, 2));
        helper.succeedWhen(() -> {
            HopperBlockEntity top = (HopperBlockEntity) helper.getBlockEntity(above);
            HopperBlockEntity bottom = (HopperBlockEntity) helper.getBlockEntity(below);
            helper.assertTrue(top.isEmpty(), "上面的漏斗应把圆石送进箱子");
            helper.assertTrue(bottom.countItem(Items.DIAMOND) > 0, "下面的漏斗应从箱子里抽到钻石");
        });
    }

    // ------------------------------------------------------------------
    // 魔力显示屏
    // ------------------------------------------------------------------

    private static MagicalDisplayBlockEntity placeDisplay(GameTestHelper helper, BlockPos pos, Direction facing) {
        helper.setBlock(pos, ECStorage.MAGICAL_DISPLAY.get().defaultBlockState().setValue(MagicalDisplayBlock.FACING, facing));
        return (MagicalDisplayBlockEntity) helper.getBlockEntity(pos);
    }

    private static void rightClick(GameTestHelper helper, BlockPos pos, ServerPlayer player) {
        BlockPos abs = helper.absolutePos(pos);
        BlockState state = helper.getLevel().getBlockState(abs);
        state.use(helper.getLevel(), player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
    }

    /** 生存拿 3 个钻石右键：屏上 1 个、手上剩 2 个；创造模式不扣 */
    @GameTest(template = EMPTY)
    public static void storageDisplayPutTakesOne(GameTestHelper helper) {
        BlockPos survivalPos = new BlockPos(1, 1, 2);
        BlockPos creativePos = new BlockPos(3, 1, 2);
        MagicalDisplayBlockEntity survival = placeDisplay(helper, survivalPos, Direction.UP);
        MagicalDisplayBlockEntity creative = placeDisplay(helper, creativePos, Direction.UP);

        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND, 3));
        rightClick(helper, survivalPos, player);
        helper.assertTrue(survival.getDisplayed().is(Items.DIAMOND) && survival.getDisplayed().getCount() == 1, "屏上应有 1 个钻石");
        helper.assertTrue(player.getMainHandItem().getCount() == 2, "生存手上应剩 2 个，实际 " + player.getMainHandItem().getCount());

        player.getAbilities().instabuild = true;
        rightClick(helper, creativePos, player);
        helper.assertTrue(creative.getDisplayed().getCount() == 1, "创造模式也应放上 1 个");
        helper.assertTrue(player.getMainHandItem().getCount() == 2, "创造模式不应扣手上的");

        // 屏上已有东西时拿着物品右键：弹出原来的、不放新的
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.EMERALD, 1));
        rightClick(helper, survivalPos, player);
        helper.assertTrue(survival.getDisplayed().isEmpty(), "屏上有东西时拿物品右键应把它弹出来");
        helper.assertTrue(player.getMainHandItem().is(Items.EMERALD), "不应放上新的");
        helper.assertItemEntityCountIs(Items.DIAMOND, survivalPos, 1.5D, 1);
        helper.succeed();
    }

    /** 空手右键三次：显示方式 0 → 1 → 2 → 0 */
    @GameTest(template = EMPTY)
    public static void storageDisplayCyclesMode(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        MagicalDisplayBlockEntity display = placeDisplay(helper, pos, Direction.NORTH);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        int[] expected = {1, 2, 0};
        for (int mode : expected) {
            rightClick(helper, pos, player);
            helper.assertTrue(display.getMode() == mode, "显示方式应为 " + mode + "，实际 " + display.getMode());
        }
        helper.succeed();
    }

    /** 潜行空手：弹出、屏空；存读档后仍是空的（原作不 markDirty，1.20 照抄就是复制） */
    @GameTest(template = EMPTY)
    public static void storageDisplaySneakEjects(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        MagicalDisplayBlockEntity display = placeDisplay(helper, pos, Direction.SOUTH);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND, 1));
        rightClick(helper, pos, player);
        helper.assertTrue(player.getMainHandItem().isEmpty(), "手上的钻石应放到屏上");
        LevelChunk chunk = helper.getLevel().getChunkAt(helper.absolutePos(pos));
        chunk.setUnsaved(false);
        player.setShiftKeyDown(true);
        rightClick(helper, pos, player);
        helper.assertTrue(display.getDisplayed().isEmpty(), "潜行空手右键应取出");
        helper.assertTrue(chunk.isUnsaved(), "取出后区块应标记为要存盘");
        helper.assertItemEntityCountIs(Items.DIAMOND, pos, 1.5D, 1);
        MagicalDisplayBlockEntity copy = new MagicalDisplayBlockEntity(ECStorage.MAGICAL_DISPLAY_BE.get(),
                helper.absolutePos(pos), display.getBlockState());
        copy.load(display.saveWithoutMetadata());
        helper.assertTrue(copy.getDisplayed().isEmpty(), "存读档后屏上应是空的");
        // 从正面（朝南）弹出：掉落物在屏所在格的南半边，不在墙里
        for (ItemEntity item : helper.getEntities(EntityType.ITEM, pos, 1.5D)) {
            double z = item.getZ() - helper.absolutePos(pos).getZ();
            helper.assertTrue(z > 0.5D, "应从正面弹出，实际 z 偏移 " + z);
        }
        helper.succeed();
    }

    /** 挖掉：掉 1 个显示屏 + 屏上的物品 */
    @GameTest(template = EMPTY)
    public static void storageDisplayBreakDropsBoth(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        MagicalDisplayBlockEntity display = placeDisplay(helper, pos, Direction.UP);
        display.setDisplayed(new ItemStack(Items.DIAMOND));
        helper.getLevel().destroyBlock(helper.absolutePos(pos), true);
        helper.assertItemEntityCountIs(ECStorage.MAGICAL_DISPLAY.get().asItem(), pos, 2D, 1);
        helper.assertItemEntityCountIs(Items.DIAMOND, pos, 2D, 1);
        helper.succeed();
    }

    /** 漏斗从上面塞：只进 1 个，漏斗里剩 4 个 */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void storageDisplayHopperInsertsOne(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        MagicalDisplayBlockEntity display = placeDisplay(helper, pos, Direction.UP);
        BlockPos above = pos.above();
        helper.setBlock(above, Blocks.HOPPER);
        HopperBlockEntity hopper = (HopperBlockEntity) helper.getBlockEntity(above);
        hopper.setItem(0, new ItemStack(Items.DIAMOND, 5));
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(display.getDisplayed().getCount() == 1, "屏上应只有 1 个，实际 " + display.getDisplayed().getCount());
            helper.assertTrue(hopper.countItem(Items.DIAMOND) == 4, "漏斗里应剩 4 个，实际 " + hopper.countItem(Items.DIAMOND));
            helper.succeed();
        });
    }

    /** 更新包带物品和显示方式；物品 NBT 超过 16 KB 时只发 id、数量和名字，存档里仍是完整的 */
    @GameTest(template = EMPTY)
    public static void storageDisplayUpdateTag(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        MagicalDisplayBlockEntity display = placeDisplay(helper, pos, Direction.NORTH);
        ItemStack small = new ItemStack(Items.PAPER);
        small.setHoverName(Component.literal("small"));
        display.setDisplayed(small);
        display.cycleMode();
        CompoundTag tag = display.getUpdateTag();
        helper.assertTrue(tag.getByte("mode") == 1, "更新包应带显示方式");
        helper.assertTrue(ItemStack.matches(ItemStack.of(tag.getCompound("item")), small), "小物品应原样发出");

        ItemStack big = new ItemStack(Items.PAPER);
        big.getOrCreateTag().putString("big", "x".repeat(MagicalDisplayBlockEntity.MAX_SYNC_BYTES + 1000));
        big.setHoverName(Component.literal("label"));
        display.takeDisplayed();
        display.setDisplayed(big);
        CompoundTag bigTag = display.getUpdateTag().getCompound("item");
        ItemStack synced = ItemStack.of(bigTag);
        helper.assertTrue(synced.is(Items.PAPER), "精简后仍应是纸");
        helper.assertTrue(synced.getTag() == null || !synced.getTag().contains("big"), "超大的 NBT 不应发给客户端");
        helper.assertTrue("label".equals(synced.getHoverName().getString()), "精简后应保留名字");
        helper.assertTrue(display.saveWithoutMetadata().getCompound("item").getCompound("tag").contains("big"), "存档里应是完整的物品");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 命令替换方块（两个方块实体都实现 Clearable，和原版容器一样）
    // ------------------------------------------------------------------

    /** 以服务器身份（权限 4）执行一条命令，不输出；返回命令结果（失败为 0） */
    private static int runCommand(GameTestHelper helper, String command) {
        ServerLevel level = helper.getLevel();
        return level.getServer().getCommands().performPrefixedCommand(
                level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput(), command);
    }

    private static String coords(GameTestHelper helper, BlockPos rel) {
        BlockPos abs = helper.absolutePos(rel);
        return abs.getX() + " " + abs.getY() + " " + abs.getZ();
    }

    /**
     * /clone … move 搬走装满的魔力箱和放着东西的显示屏：内容只跟过去一份，原地不撒（没实现 Clearable 时原地撒一份、目标处再有一份 = 翻倍）；
     * 再用 /setblock 覆盖：东西直接清掉，不撒一地（原版箱子也是这样）。
     */
    @GameTest(template = EMPTY)
    public static void storageCommandsClearInsteadOfSpill(GameTestHelper helper) {
        BlockPos chestFrom = new BlockPos(1, 1, 1);
        BlockPos chestTo = new BlockPos(3, 1, 1);
        BlockPos displayFrom = new BlockPos(1, 1, 3);
        BlockPos displayTo = new BlockPos(3, 1, 3);
        BlockPos center = new BlockPos(2, 1, 2);
        MagicalChestBlockEntity chest = placeChest(helper, chestFrom);
        fillChest(chest, true);
        List<ItemStack> expected = new ArrayList<>();
        for (int i = 0; i < chest.getInventory().getSlots(); i++) {
            expected.add(chest.getInventory().getStackInSlot(i).copy());
        }
        MagicalDisplayBlockEntity display = placeDisplay(helper, displayFrom, Direction.UP);
        display.setDisplayed(new ItemStack(Items.EMERALD));

        helper.assertTrue(runCommand(helper, "clone " + coords(helper, chestFrom) + " " + coords(helper, chestFrom) + " "
                + coords(helper, chestTo) + " replace move") == 1, "/clone 搬魔力箱应成功");
        helper.assertTrue(runCommand(helper, "clone " + coords(helper, displayFrom) + " " + coords(helper, displayFrom) + " "
                + coords(helper, displayTo) + " replace move") == 1, "/clone 搬显示屏应成功");
        helper.assertBlockPresent(Blocks.AIR, chestFrom);
        helper.assertBlockPresent(Blocks.AIR, displayFrom);
        int spilled = helper.getEntities(EntityType.ITEM, center, 2D).size();
        helper.assertTrue(spilled == 0, "搬走时原地不应撒出东西，实际地上 " + spilled + " 堆");
        MagicalChestBlockEntity movedChest = (MagicalChestBlockEntity) helper.getBlockEntity(chestTo);
        for (int i = 0; i < expected.size(); i++) {
            helper.assertTrue(ItemStack.matches(expected.get(i), movedChest.getInventory().getStackInSlot(i)),
                    "搬过去的魔力箱第 " + i + " 格不一致");
        }
        MagicalDisplayBlockEntity movedDisplay = (MagicalDisplayBlockEntity) helper.getBlockEntity(displayTo);
        helper.assertTrue(movedDisplay.getDisplayed().is(Items.EMERALD), "搬过去的显示屏上应有绿宝石");

        helper.assertTrue(runCommand(helper, "setblock " + coords(helper, chestTo) + " minecraft:stone") == 1, "/setblock 覆盖魔力箱应成功");
        helper.assertTrue(runCommand(helper, "setblock " + coords(helper, displayTo) + " minecraft:stone") == 1, "/setblock 覆盖显示屏应成功");
        helper.assertBlockPresent(Blocks.STONE, chestTo);
        helper.assertBlockPresent(Blocks.STONE, displayTo);
        spilled = helper.getEntities(EntityType.ITEM, center, 2D).size();
        helper.assertTrue(spilled == 0, "/setblock 覆盖时不应撒出东西，实际地上 " + spilled + " 堆");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 虚空箱（第六批 6b：同一个方块类，Layout.VOID，117 格）
    // ------------------------------------------------------------------

    private static MagicalChestBlockEntity placeVoidChest(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ECStorage.VOID_CHEST.get());
        return (MagicalChestBlockEntity) helper.getBlockEntity(pos);
    }

    /** 方块属性照原作（硬度 1、抗爆 1.0、地图色黑、要镐）；117 格、界面不写字、箱子贴图 void.png；魔力箱仍是 54 格、写字 */
    @GameTest(template = EMPTY)
    public static void storageVoidChestBasics(GameTestHelper helper) {
        MagicalChestBlockEntity chest = placeVoidChest(helper, CHEST);
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(CHEST);
        BlockState state = level.getBlockState(abs);
        helper.assertTrue(Math.abs(state.getDestroySpeed(level, abs) - 1F) < 1e-4F, "虚空箱硬度应为 1");
        helper.assertTrue(Math.abs(state.getExplosionResistance(level, abs, null) - 1F) < 1e-4F,
                "虚空箱抗爆应为 1.0，实际 " + state.getExplosionResistance(level, abs, null));
        helper.assertTrue(state.getMapColor(level, abs) == MapColor.COLOR_BLACK, "虚空箱地图色应为黑");
        helper.assertTrue(state.requiresCorrectToolForDrops() && state.is(BlockTags.MINEABLE_WITH_PICKAXE), "虚空箱要镐");
        helper.assertTrue(chest.getType() == ECStorage.VOID_CHEST_BE.get(), "虚空箱用自己的方块实体类型");
        helper.assertTrue(chest.getInventory().getSlots() == 117 && chest.layout() == MagicalChestBlock.Layout.VOID,
                "虚空箱应有 117 格，实际 " + chest.getInventory().getSlots());
        helper.assertFalse(MagicalChestBlock.Layout.VOID.labels(), "虚空箱界面不写字（贴图没留空）");
        helper.assertTrue(MagicalChestBlock.Layout.VOID.modelTexture().getPath().equals("textures/block/chests/void.png"), "虚空箱用 void.png 画箱子");
        MagicalChestBlockEntity magical = placeChest(helper, new BlockPos(1, 1, 1));
        helper.assertTrue(magical.getInventory().getSlots() == 54 && MagicalChestBlock.Layout.MAGICAL.labels(), "魔力箱仍是 54 格、写字");
        helper.succeed();
    }

    /**
     * 格子坐标照贴图：第 0 格 (12, 8)、第 12 格 (228, 8)、第 116 格 (228, 152)、玩家物品栏第一格 (48, 174)、快捷栏 y 232；
     * Shift 点击：箱子前 116 格满时从背包点进去落在第 116 格，再从第 116 格点回背包
     */
    @GameTest(template = EMPTY)
    public static void storageVoidChestMenuLayout(GameTestHelper helper) {
        MagicalChestBlockEntity chest = placeVoidChest(helper, CHEST);
        for (int i = 0; i < 116; i++) {
            chest.getInventory().setStackInSlot(i, new ItemStack(Items.STONE, 64));
        }
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        player.getInventory().setItem(9, new ItemStack(Items.DIAMOND, 5));
        MagicalChestMenu menu = (MagicalChestMenu) chest.createMenu(1, player.getInventory(), player);
        helper.assertTrue(menu != null && menu.getType() == ECStorage.VOID_CHEST_MENU.get() && menu.slots.size() == 117 + 36,
                "虚空箱菜单应是自己的菜单类型、117 + 36 格");
        assertSlotAt(helper, menu, 0, 12, 8);
        assertSlotAt(helper, menu, 12, 228, 8);
        assertSlotAt(helper, menu, 13, 12, 26);
        assertSlotAt(helper, menu, 116, 228, 152);
        assertSlotAt(helper, menu, 117, 48, 174);
        assertSlotAt(helper, menu, 117 + 27, 48, 232);
        assertSlotAt(helper, menu, 117 + 35, 192, 232);

        // 菜单第 117 格 = 玩家物品栏第 9 格（主背包第一格）
        menu.quickMoveStack(player, 117);
        helper.assertTrue(chest.getInventory().getStackInSlot(116).is(Items.DIAMOND) && chest.getInventory().getStackInSlot(116).getCount() == 5,
                "Shift 点击应把钻石放进第 116 格");
        helper.assertTrue(player.getInventory().getItem(9).isEmpty(), "背包里的钻石应被移走");
        menu.quickMoveStack(player, 116);
        helper.assertTrue(chest.getInventory().getStackInSlot(116).isEmpty() && player.getInventory().countItem(Items.DIAMOND) == 5,
                "从第 116 格 Shift 点击应回到背包");
        menu.removed(player);
        helper.succeed();
    }

    private static void assertSlotAt(GameTestHelper helper, MagicalChestMenu menu, int index, int x, int y) {
        int ax = menu.getSlot(index).x;
        int ay = menu.getSlot(index).y;
        helper.assertTrue(ax == x && ay == y, "菜单第 " + index + " 格应在 (" + x + ", " + y + ")，实际 (" + ax + ", " + ay + ")");
    }

    /** 比较器：只有第 116 格满 = 1，117 格全满 = 15；漏斗：前 116 格满时从上面塞进来的东西落在第 116 格，物品能力能取出来 */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void storageVoidChestHopperAndComparator(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos comparatorPos = new BlockPos(0, 1, 0);
        MagicalChestBlockEntity measured = placeVoidChest(helper, comparatorPos);
        BlockPos measuredAbs = helper.absolutePos(comparatorPos);
        BlockState measuredState = level.getBlockState(measuredAbs);
        measured.getInventory().setStackInSlot(116, new ItemStack(Items.STONE, 64));
        helper.assertTrue(measuredState.getAnalogOutputSignal(level, measuredAbs) == 1, "只有一格满应输出 1");
        for (int i = 0; i < 117; i++) {
            measured.getInventory().setStackInSlot(i, new ItemStack(Items.STONE, 64));
        }
        helper.assertTrue(measuredState.getAnalogOutputSignal(level, measuredAbs) == 15, "117 格全满应输出 15");

        BlockPos chestPos = new BlockPos(2, 1, 2);
        BlockPos above = chestPos.above();
        MagicalChestBlockEntity chest = placeVoidChest(helper, chestPos);
        for (int i = 0; i < 116; i++) {
            chest.getInventory().setStackInSlot(i, new ItemStack(Items.STONE, 64));
        }
        helper.setBlock(above, Blocks.HOPPER);
        ((HopperBlockEntity) helper.getBlockEntity(above)).setItem(0, new ItemStack(Items.DIAMOND, 2));
        helper.succeedWhen(() -> {
            ItemStack last = chest.getInventory().getStackInSlot(116);
            helper.assertTrue(last.is(Items.DIAMOND) && last.getCount() == 2, "漏斗应把钻石塞进第 116 格");
            IItemHandler handler = chest.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN).orElseThrow(IllegalStateException::new);
            helper.assertTrue(handler.getSlots() == 117, "物品能力应有 117 格");
            helper.assertTrue(handler.extractItem(116, 2, true).getCount() == 2, "物品能力应能从第 116 格取出");
        });
    }

    /** 挖掉：117 格全部撒出（总数不变），掉 1 个不带内容的虚空箱；/setblock 覆盖另一个装满的虚空箱时不撒 */
    @GameTest(template = EMPTY)
    public static void storageVoidChestBreakSpills(GameTestHelper helper) {
        MagicalChestBlockEntity chest = placeVoidChest(helper, CHEST);
        int expected = 0;
        for (int i = 0; i < 117; i++) {
            int count = i % 64 + 1;
            chest.getInventory().setStackInSlot(i, new ItemStack(Items.COBBLESTONE, count));
            expected += count;
        }
        helper.getLevel().destroyBlock(helper.absolutePos(CHEST), true);
        helper.assertItemEntityCountIs(Items.COBBLESTONE, CHEST, 3D, expected);
        helper.assertItemEntityCountIs(ECStorage.VOID_CHEST.get().asItem(), CHEST, 3D, 1);
        for (ItemEntity item : helper.getEntities(EntityType.ITEM, CHEST, 3D)) {
            if (item.getItem().is(ECStorage.VOID_CHEST.get().asItem())) {
                helper.assertTrue(item.getItem().getTagElement("BlockEntityTag") == null, "掉落的虚空箱不应带内容");
            }
            item.discard();
        }

        BlockPos other = new BlockPos(1, 1, 3);
        MagicalChestBlockEntity full = placeVoidChest(helper, other);
        for (int i = 0; i < 117; i++) {
            full.getInventory().setStackInSlot(i, new ItemStack(Items.DIAMOND, 64));
        }
        helper.assertTrue(runCommand(helper, "setblock " + coords(helper, other) + " minecraft:stone") == 1, "/setblock 覆盖虚空箱应成功");
        helper.assertBlockPresent(Blocks.STONE, other);
        helper.assertItemEntityCountIs(Items.DIAMOND, other, 3D, 0);
        helper.succeed();
    }

    /** 存档里写着 Size: 54（被改过、或者以后格数变了）：读进来仍是 117 格，前 54 格的东西都在，后面是空的 */
    @GameTest(template = EMPTY)
    public static void storageVoidChestLoadsOtherSize(GameTestHelper helper) {
        MagicalChestBlockEntity chest = placeVoidChest(helper, CHEST);
        for (int i = 0; i < 54; i++) {
            chest.getInventory().setStackInSlot(i, new ItemStack(Items.COBBLESTONE, i + 1));
        }
        CompoundTag saved = chest.saveWithoutMetadata();
        saved.getCompound("inventory").putInt("Size", 54);
        MagicalChestBlockEntity copy = new MagicalChestBlockEntity(ECStorage.VOID_CHEST_BE.get(), helper.absolutePos(CHEST), chest.getBlockState());
        copy.load(saved);
        helper.assertTrue(copy.getInventory().getSlots() == 117, "读档后应仍是 117 格，实际 " + copy.getInventory().getSlots());
        for (int i = 0; i < 54; i++) {
            helper.assertTrue(copy.getInventory().getStackInSlot(i).getCount() == i + 1, "第 " + i + " 格读档后不对");
        }
        for (int i = 54; i < 117; i++) {
            helper.assertTrue(copy.getInventory().getStackInSlot(i).isEmpty(), "第 " + i + " 格应是空的");
        }
        helper.succeed();
    }

    /** 配方：8 块虚空石围一圈得 1 个虚空箱；中间放了东西不匹配 */
    @GameTest(template = EMPTY)
    public static void storageVoidChestRecipe(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Recipe<?> found = level.getRecipeManager().byKey(EssentialCraft5.id("storage/void_chest")).orElse(null);
        if (!(found instanceof CraftingRecipe recipe)) {
            helper.fail("找不到工作台配方 essentialcraft5:storage/void_chest");
            return;
        }
        ItemStack[] cells = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            cells[i] = i == 4 ? ItemStack.EMPTY : new ItemStack(ECVoid.VOID_STONE.get());
        }
        CrafterGrid ring = new CrafterGrid(cells);
        helper.assertTrue(recipe.matches(ring, level), "8 块虚空石围一圈应匹配");
        ItemStack out = recipe.assemble(ring, level.registryAccess());
        helper.assertTrue(out.is(ECStorage.VOID_CHEST.get().asItem()) && out.getCount() == 1, "应得 1 个虚空箱，实际 " + out);
        cells[4] = new ItemStack(Items.DIAMOND);
        helper.assertFalse(recipe.matches(new CrafterGrid(cells), level), "中间放了东西不应匹配");
        helper.succeed();
    }
}
