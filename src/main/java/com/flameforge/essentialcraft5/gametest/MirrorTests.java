package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.MRUBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MagicalMirrorBlockEntity;
import com.flameforge.essentialcraft5.item.ControlRodItem;
import com.flameforge.essentialcraft5.registry.ECMirror;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * 魔力镜模块（ECMirror）的 GameTest（batch4 §3.8 的测试点）。
 * <p>
 * 摆法（empty 模板）：源箱子 (1, 1, 1)、镜子在它正上方 (1, 2, 1)、目标箱子 (3, 1, 3)（和源箱子不相邻，不会并成大箱子），直线距离 3。
 * 链接走控制棒（{@link ControlRodItem#tryLink} 或真的 {@code ItemStack.useOn}）时用 {@code ProtectionFrameworkTests.realPlayer}，每次新 UUID，探测缓存互不影响；
 * 只测传送行为的直接 {@link MagicalMirrorBlockEntity#link}。传送用 {@link MagicalMirrorBlockEntity#tryStartTransfer} / {@link MagicalMirrorBlockEntity#finishTransfer}
 * 同步触发（不等 60 刻），「照常工作」「存读档」「只在开始 / 结束同步」这几条走真实的 tick。
 * 镜子不需要主人（链接时已查过三格），所以这里不设在线覆盖。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class MirrorTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    private static final BlockPos SOURCE = new BlockPos(1, 1, 1);
    private static final BlockPos MIRROR = new BlockPos(1, 2, 1);
    private static final BlockPos TARGET = new BlockPos(3, 1, 3);

    private MirrorTests() {
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private static MagicalMirrorBlockEntity placeMirror(GameTestHelper helper, BlockPos rel) {
        helper.setBlock(rel, ECMirror.MAGICAL_MIRROR.get());
        return (MagicalMirrorBlockEntity) helper.getBlockEntity(rel);
    }

    private static ChestBlockEntity placeChest(GameTestHelper helper, BlockPos rel) {
        helper.setBlock(rel, Blocks.CHEST);
        return (ChestBlockEntity) helper.getBlockEntity(rel);
    }

    /** 源箱子（第 0 格 count 个 item）+ 镜子 + 目标箱子 */
    private static ChestBlockEntity setUp(GameTestHelper helper, Item item, int count) {
        ChestBlockEntity source = placeChest(helper, SOURCE);
        source.setItem(0, new ItemStack(item, count));
        return source;
    }

    private static GlobalPos mirrorAt(GameTestHelper helper, BlockPos rel) {
        return GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(rel));
    }

    private static ControlRodItem.LinkResult link(GameTestHelper helper, ServerPlayer player, BlockPos mirror, BlockPos target) {
        return ControlRodItem.tryLink(player, helper.getLevel(), mirrorAt(helper, mirror), helper.absolutePos(target));
    }

    /** 拿着控制棒右键 rel 那一格（真的走 ItemStack.useOn，含 Forge 的放置捕获） */
    private static InteractionResult useRod(GameTestHelper helper, ServerPlayer player, ItemStack rod, BlockPos rel) {
        player.setItemInHand(InteractionHand.MAIN_HAND, rod);
        BlockPos abs = helper.absolutePos(rel);
        return rod.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false)));
    }

    private static int count(BlockEntity container, Item item) {
        return ((Container) container).countItem(item);
    }

    private static String key(Component message) {
        return message.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : "";
    }

    // ------------------------------------------------------------------
    // 链接与照常工作
    // ------------------------------------------------------------------

    /**
     * 没有领地时（纯 Forge、单人）照常工作：控制棒点镜子记下坐标和维度，再点目标箱子链接成功、控制棒清空；
     * 源箱子 3 个圆石，80 刻内送过去 1 个（空闲检查 ≤20 刻 + 传送 60 刻），第二个还在路上、还在源箱子里。
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void mirrorSendsWithoutClaims(GameTestHelper helper) {
        ChestBlockEntity source = setUp(helper, Items.COBBLESTONE, 3);
        MagicalMirrorBlockEntity mirror = placeMirror(helper, MIRROR);
        ChestBlockEntity target = placeChest(helper, TARGET);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        ItemStack rod = new ItemStack(ECMirror.CONTROL_ROD.get());

        helper.assertTrue(useRod(helper, player, rod, MIRROR).consumesAction(), "拿控制棒点镜子应记下它");
        GlobalPos selected = ControlRodItem.selectedMirror(rod);
        helper.assertTrue(selected != null && selected.pos().equals(helper.absolutePos(MIRROR))
                && selected.dimension().equals(helper.getLevel().dimension()), "控制棒应记下镜子的坐标和维度");
        helper.assertTrue(useRod(helper, player, rod, TARGET).consumesAction(), "没有领地时应链接成功");
        helper.assertTrue(helper.absolutePos(TARGET).equals(mirror.getTarget()), "镜子应链接到目标箱子");
        helper.assertTrue(ControlRodItem.selectedMirror(rod) == null, "链接成功后控制棒应清空");

        helper.runAfterDelay(80, () -> {
            helper.assertTrue(count(target, Items.COBBLESTONE) == 1, "80 刻后目标应有 1 个圆石，实际 " + count(target, Items.COBBLESTONE));
            helper.assertTrue(count(source, Items.COBBLESTONE) == 2, "源箱子应剩 2 个（第二个还在路上），实际 " + count(source, Items.COBBLESTONE));
            helper.succeed();
        });
    }

    /**
     * 被拒的不动、不扣东西：镜子格、镜子正下方的源箱子格、目标格，任一被模拟领地拒绝都链接不上，
     * 被拒的那格以链接的人的身份探测了一次；控制棒还记着镜子（换个地方再试）；源箱子里的东西不动。
     * 探测预算用完（给不出结论）时提示稍后再试，也不链接。
     */
    @GameTest(template = EMPTY)
    public static void mirrorLinkDeniedCellsUntouched(GameTestHelper helper) {
        ChestBlockEntity source = setUp(helper, Items.COBBLESTONE, 3);
        MagicalMirrorBlockEntity mirror = placeMirror(helper, MIRROR);
        placeChest(helper, TARGET);
        for (BlockPos denied : List.of(MIRROR, SOURCE, TARGET)) {
            ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
            try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(denied)) {
                ItemStack rod = new ItemStack(ECMirror.CONTROL_ROD.get());
                useRod(helper, player, rod, MIRROR);
                helper.assertFalse(useRod(helper, player, rod, TARGET).consumesAction(), denied + " 被拒时不应链接成功");
                helper.assertTrue(mirror.getTarget() == null, denied + " 被拒时镜子不应有链接");
                helper.assertTrue(ControlRodItem.selectedMirror(rod) != null, denied + " 被拒时控制棒应还记着镜子");
                helper.assertTrue(claims.probes(denied) == 1 && claims.breaksBy(denied, player.getUUID()) == 1,
                        denied + " 应以链接的人的身份探测一次");
            }
        }
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        ECProtection.Config.setMaxProbesPerPlayerTickForTest(1);
        try {
            helper.assertTrue(link(helper, player, MIRROR, TARGET) == ControlRodItem.LinkResult.TRY_LATER, "探测预算用完时应提示稍后再试");
            helper.assertTrue(mirror.getTarget() == null, "给不出结论时不应链接");
        } finally {
            ECProtection.Config.setMaxProbesPerPlayerTickForTest(null);
        }
        helper.assertFalse(mirror.tryStartTransfer(), "没有链接时不应开始传送");
        helper.assertTrue(count(source, Items.COBBLESTONE) == 3, "源箱子里的东西不应动");
        helper.succeed();
    }

    /**
     * 链接的其它条件：距离 9 绑不上、距离正好 8 能绑；记下的镜子在别的维度绑不上；假玩家绑不上；
     * 点镜子正下方的源箱子、点不是容器的方块、记下的那格已经没有镜子都绑不上（后者让控制棒忘掉它）；
     * 这些失败都不发探测事件。链接以后目标超出最远距离（配置调小的情形，这里直接 link 过去模拟）不送。
     */
    @GameTest(template = EMPTY_LARGE)
    public static void mirrorLinkRules(GameTestHelper helper) {
        setUp(helper, Items.COBBLESTONE, 3);
        MagicalMirrorBlockEntity mirror = placeMirror(helper, MIRROR);
        BlockPos near = new BlockPos(9, 2, 1);   // 离镜子正好 8 格
        BlockPos far = new BlockPos(10, 2, 1);   // 9 格
        helper.setBlock(near, Blocks.BARREL);
        helper.setBlock(far, Blocks.BARREL);
        BlockPos stone = new BlockPos(3, 1, 3);
        helper.setBlock(stone, Blocks.STONE);
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper)) {
            ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
            helper.assertTrue(link(helper, player, MIRROR, far) == ControlRodItem.LinkResult.TOO_FAR, "9 格外应太远");
            helper.assertTrue(ControlRodItem.tryLink(player, helper.getLevel(), GlobalPos.of(Level.NETHER, helper.absolutePos(MIRROR)),
                    helper.absolutePos(near)) == ControlRodItem.LinkResult.OTHER_DIMENSION, "记下的镜子在别的维度时应拒绝");
            helper.assertTrue(link(helper, player, MIRROR, SOURCE) == ControlRodItem.LinkResult.SOURCE_ITSELF, "不能链接到镜子正下方的源箱子");
            helper.assertTrue(link(helper, player, MIRROR, stone) == ControlRodItem.LinkResult.NOT_CONTAINER, "石头不是容器");
            helper.assertTrue(link(helper, player, new BlockPos(5, 2, 5), near) == ControlRodItem.LinkResult.MIRROR_MISSING,
                    "记下的那格没有镜子时应报镜子不在了");
            helper.assertTrue(ControlRodItem.tryLink(ProtectionFrameworkTests.fakePlayer(helper), helper.getLevel(), mirrorAt(helper, MIRROR),
                    helper.absolutePos(near)) == ControlRodItem.LinkResult.NO_PERMISSION, "假玩家不能链接");
            helper.assertTrue(mirror.getTarget() == null, "上面这些都不应链接");
            helper.assertTrue(claims.breaks(MIRROR) == 0 && claims.breaks(near) == 0, "没到权限那一步的失败不应发探测事件");

            helper.assertTrue(link(helper, player, MIRROR, near) == ControlRodItem.LinkResult.LINKED, "正好 8 格应能链接");
            helper.assertTrue(claims.probes(MIRROR) == 1 && claims.probes(SOURCE) == 1 && claims.probes(near) == 1, "三格各探测一次");
        }

        // 用控制棒点一个已经没有镜子的坐标：控制棒忘掉它
        ItemStack rod = new ItemStack(ECMirror.CONTROL_ROD.get());
        ControlRodItem.selectMirror(rod, helper.getLevel().dimension(), helper.absolutePos(new BlockPos(5, 2, 5)));
        useRod(helper, ProtectionFrameworkTests.realPlayer(helper), rod, near);
        helper.assertTrue(ControlRodItem.selectedMirror(rod) == null, "记下的镜子不在了，控制棒应忘掉它");

        // 链接以后超出最远距离：不送，状态是太远
        mirror.link(helper.absolutePos(far));
        helper.assertFalse(mirror.tryStartTransfer(), "超出最远距离的链接不应送");
        helper.assertTrue(mirror.status() == MagicalMirrorBlockEntity.Status.TOO_FAR, "状态应是太远");
        helper.assertTrue(helper.absolutePos(far).equals(mirror.getTarget()), "超出距离只是不送，不断开");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 不吞物品
    // ------------------------------------------------------------------

    /**
     * 目标放不下时源不变：目标全满；目标只剩一格装着同种但 NBT 不同（起了名字）的圆石、其余全满（原作 M-1 吞物品的场景）；
     * 开始以后、送到之前目标被塞满 → 结束时再模拟一次，不取；开始以后源格里换了东西 → 不取。东西一件不少。
     */
    @GameTest(template = EMPTY)
    public static void mirrorNeverLosesItems(GameTestHelper helper) {
        ChestBlockEntity source = setUp(helper, Items.COBBLESTONE, 3);
        MagicalMirrorBlockEntity mirror = placeMirror(helper, MIRROR);
        ChestBlockEntity target = placeChest(helper, TARGET);
        mirror.link(helper.absolutePos(TARGET));
        for (int i = 0; i < target.getContainerSize(); i++) {
            target.setItem(i, new ItemStack(Items.DIRT, 64));
        }
        helper.assertFalse(mirror.tryStartTransfer(), "目标全满时不应开始");

        ItemStack named = new ItemStack(Items.COBBLESTONE, 1);
        named.setHoverName(Component.literal("ec5-mirror"));
        target.setItem(0, named);
        helper.assertFalse(mirror.tryStartTransfer(), "只剩同种不同 NBT 的格子时不应开始（不叠）");

        target.setItem(0, ItemStack.EMPTY);
        helper.assertTrue(mirror.tryStartTransfer() && mirror.getSample().is(Items.COBBLESTONE), "空出一格后应开始送圆石");
        target.setItem(0, new ItemStack(Items.DIRT, 64));
        helper.assertFalse(mirror.finishTransfer(), "送到前目标被塞满，结束时不应取");
        helper.assertFalse(mirror.isTransferring(), "这一件作废，回到空闲");
        helper.assertTrue(count(source, Items.COBBLESTONE) == 3 && count(target, Items.COBBLESTONE) == 0, "目标被塞满时源箱子一件不少、目标一件不多");

        target.setItem(0, ItemStack.EMPTY);
        helper.assertTrue(mirror.tryStartTransfer(), "又空出一格，应重新开始");
        source.setItem(0, new ItemStack(Items.STONE, 3));
        helper.assertFalse(mirror.finishTransfer(), "源格里换了东西，结束时不应取");

        helper.assertTrue(count(source, Items.STONE) == 3 && count(source, Items.COBBLESTONE) == 0, "源箱子应原样是 3 个石头");
        helper.assertTrue(target.getItem(0).isEmpty() && count(target, Items.STONE) == 0 && count(target, Items.COBBLESTONE) == 0,
                "目标不应收到任何东西（一件不少、一件不多）");
        helper.succeed();
    }

    /** 目标是机器：熔炉顶面进原料格；酿造台顶面只收酿造材料，源里的泥土不动 */
    @GameTest(template = EMPTY)
    public static void mirrorMachineTargetsUseTopFace(GameTestHelper helper) {
        ChestBlockEntity source = setUp(helper, Items.STICK, 2);
        MagicalMirrorBlockEntity mirror = placeMirror(helper, MIRROR);
        helper.setBlock(TARGET, Blocks.FURNACE);
        mirror.link(helper.absolutePos(TARGET));
        helper.assertTrue(mirror.tryStartTransfer() && mirror.finishTransfer(), "木棍应送进熔炉");
        Container furnace = (Container) helper.getBlockEntity(TARGET);
        helper.assertTrue(furnace.getItem(0).is(Items.STICK) && furnace.getItem(0).getCount() == 1, "熔炉顶面应进原料格（0 号格）");
        helper.assertTrue(furnace.getItem(1).isEmpty(), "燃料格不应有东西");
        helper.assertTrue(count(source, Items.STICK) == 1, "源箱子应剩 1 根");

        helper.setBlock(TARGET, Blocks.BREWING_STAND);
        source.setItem(0, new ItemStack(Items.DIRT, 5));
        helper.assertFalse(mirror.tryStartTransfer(), "酿造台顶面不收泥土，不应开始");
        helper.assertTrue(count(source, Items.DIRT) == 5 && count(source, Items.STICK) == 0, "泥土应原样留在源箱子");
        helper.assertTrue(helper.absolutePos(TARGET).equals(mirror.getTarget()), "目标换成别的容器时链接还在");
        helper.succeed();
    }

    /**
     * 找货不卡住：源箱子前 12 格是 12 种酿造台不收的东西、第 12 格是糖，每次最多试 {@code insert_checks}（默认 8）种，
     * 下次从停下的地方接着看，几次之内一定找到糖；同一种放不下的只试一次（10 格泥土 + 糖：第一次就找到糖）。
     */
    @GameTest(template = EMPTY)
    public static void mirrorScanDoesNotStall(GameTestHelper helper) {
        ChestBlockEntity source = placeChest(helper, SOURCE);
        Item[] junk = {Items.DIRT, Items.STONE, Items.COBBLESTONE, Items.SAND, Items.GRAVEL, Items.OAK_LOG,
                Items.GLASS, Items.OAK_PLANKS, Items.CLAY_BALL, Items.BRICK, Items.STICK, Items.FLINT};
        for (int i = 0; i < junk.length; i++) {
            source.setItem(i, new ItemStack(junk[i]));
        }
        source.setItem(junk.length, new ItemStack(Items.SUGAR));
        MagicalMirrorBlockEntity mirror = placeMirror(helper, MIRROR);
        helper.setBlock(TARGET, Blocks.BREWING_STAND);
        mirror.link(helper.absolutePos(TARGET));
        int tries = MagicalMirrorBlockEntity.Config.insertChecks();
        boolean first = mirror.tryStartTransfer();
        helper.assertTrue(first == (tries > junk.length), "每次最多试 " + tries + " 种：第一次" + (tries > junk.length ? "应" : "不应") + "找到糖");
        boolean found = first;
        for (int i = 0; i < 4 && !found; i++) {
            found = mirror.tryStartTransfer();
        }
        helper.assertTrue(found && mirror.getSample().is(Items.SUGAR), "几次之内应找到糖");

        mirror.unlink();
        for (int i = 0; i < 10; i++) {
            source.setItem(i, new ItemStack(Items.DIRT));
        }
        source.setItem(10, new ItemStack(Items.SUGAR));
        source.setItem(11, ItemStack.EMPTY);
        source.setItem(12, ItemStack.EMPTY);
        mirror.link(helper.absolutePos(TARGET));
        helper.assertTrue(mirror.tryStartTransfer() && mirror.getSample().is(Items.SUGAR), "10 格泥土只算一种，第一次就应找到糖");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 链接的维护
    // ------------------------------------------------------------------

    /** 目标被挖掉（区块已加载）：镜子清掉链接，源箱子不动 */
    @GameTest(template = EMPTY)
    public static void mirrorTargetRemovedClearsLink(GameTestHelper helper) {
        ChestBlockEntity source = setUp(helper, Items.COBBLESTONE, 3);
        MagicalMirrorBlockEntity mirror = placeMirror(helper, MIRROR);
        placeChest(helper, TARGET);
        mirror.link(helper.absolutePos(TARGET));
        helper.setBlock(TARGET, Blocks.AIR);
        helper.assertFalse(mirror.tryStartTransfer(), "目标没了不应开始");
        helper.assertTrue(mirror.getTarget() == null, "目标没了应清掉链接");
        helper.assertTrue(mirror.status() == MagicalMirrorBlockEntity.Status.UNLINKED, "状态应回到没有链接");
        helper.assertTrue(count(source, Items.COBBLESTONE) == 3, "源箱子不应动");
        helper.succeed();
    }

    /**
     * 镜子带着存档数据挪了地方（搬运类模组、创造模式带数据复制）：位置和链接时记下的对不上，断开链接，
     * 不会从新位置下面的（别人的）箱子里抽东西。
     */
    @GameTest(template = EMPTY)
    public static void mirrorMovedWithDataDropsLink(GameTestHelper helper) {
        setUp(helper, Items.COBBLESTONE, 3);
        MagicalMirrorBlockEntity mirror = placeMirror(helper, MIRROR);
        placeChest(helper, TARGET);
        mirror.link(helper.absolutePos(TARGET));
        CompoundTag saved = mirror.saveWithoutMetadata();

        ChestBlockEntity other = placeChest(helper, new BlockPos(3, 1, 1));
        other.setItem(0, new ItemStack(Items.DIAMOND, 5));
        MagicalMirrorBlockEntity moved = placeMirror(helper, new BlockPos(3, 2, 1));
        moved.load(saved);
        helper.assertTrue(helper.absolutePos(TARGET).equals(moved.getTarget()), "读进来时还带着旧链接");
        helper.assertFalse(moved.tryStartTransfer(), "挪了位置的镜子不应再送");
        helper.assertTrue(moved.getTarget() == null, "挪了位置的镜子应断开链接");
        helper.assertTrue(count(other, Items.DIAMOND) == 5, "新位置下面箱子里的钻石不应动");
        helper.succeed();
    }

    /** 传送进行到一半存读档（换一个新的方块实体读回同一份数据）：接着送完，总数守恒 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void mirrorSaveLoadMidTransfer(GameTestHelper helper) {
        ChestBlockEntity source = setUp(helper, Items.COBBLESTONE, 3);
        MagicalMirrorBlockEntity mirror = placeMirror(helper, MIRROR);
        ChestBlockEntity target = placeChest(helper, TARGET);
        mirror.link(helper.absolutePos(TARGET));
        helper.assertTrue(mirror.tryStartTransfer(), "应开始送");
        CompoundTag saved = mirror.saveWithoutMetadata();
        helper.setBlock(MIRROR, Blocks.AIR);
        MagicalMirrorBlockEntity reloaded = placeMirror(helper, MIRROR);
        reloaded.load(saved);
        helper.assertTrue(reloaded.isTransferring() && reloaded.getSample().is(Items.COBBLESTONE)
                && helper.absolutePos(TARGET).equals(reloaded.getTarget()), "读档后应还在送同一件、链接还在");
        helper.assertTrue(count(source, Items.COBBLESTONE) == 3, "送到之前东西还在源箱子里");
        helper.runAfterDelay(70, () -> {
            helper.assertTrue(count(target, Items.COBBLESTONE) == 1, "读档后应接着送完这一件，实际目标 " + count(target, Items.COBBLESTONE));
            helper.assertTrue(count(source, Items.COBBLESTONE) == 2, "源箱子应剩 2 个（总数守恒），实际 " + count(source, Items.COBBLESTONE));
            helper.succeed();
        });
    }

    /**
     * 同步只在开始、结束各请求一次，传送中不请求；更新包只带渲染要用的（不带 MRU、主人、游标、链接原点）。
     * 源箱子只有 1 个，送完以后空闲检查找不到东西，也不再请求。
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void mirrorSyncsOnlyAtStartAndEnd(GameTestHelper helper) {
        ChestBlockEntity source = setUp(helper, Items.COBBLESTONE, 1);
        MagicalMirrorBlockEntity mirror = placeMirror(helper, MIRROR);
        ChestBlockEntity target = placeChest(helper, TARGET);
        mirror.link(helper.absolutePos(TARGET));
        int linked = mirror.syncRequests();
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(mirror.isTransferring(), "5 刻内应开始送");
            helper.assertTrue(mirror.syncRequests() == linked + 1, "开始时应请求一次同步");
            CompoundTag tag = mirror.getUpdateTag();
            helper.assertTrue(tag.getBoolean(MagicalMirrorBlockEntity.TRANSFERRING_KEY) && tag.contains(MagicalMirrorBlockEntity.SAMPLE_KEY)
                    && tag.contains(MagicalMirrorBlockEntity.TARGET_KEY), "更新包应带目标、在送、样本");
            helper.assertFalse(tag.contains("mru_storage") || tag.contains(MRUBlockEntity.OWNER_KEY)
                    || tag.contains(MagicalMirrorBlockEntity.CURSOR_KEY) || tag.contains(MagicalMirrorBlockEntity.LINK_ORIGIN_KEY),
                    "更新包不应带 MRU、主人、游标、链接原点");
        });
        helper.runAfterDelay(45, () -> helper.assertTrue(mirror.syncRequests() == linked + 1, "传送中不应再请求同步"));
        helper.runAfterDelay(100, () -> {
            helper.assertFalse(mirror.isTransferring(), "应已送完");
            helper.assertTrue(mirror.syncRequests() == linked + 2, "结束时应再请求一次，之后空闲检查找不到东西不再请求，实际多了 "
                    + (mirror.syncRequests() - linked));
            helper.assertTrue(count(target, Items.COBBLESTONE) == 1 && count(source, Items.COBBLESTONE) == 0, "那 1 个应送到");
            helper.assertFalse(mirror.getUpdateTag().contains(MagicalMirrorBlockEntity.SAMPLE_KEY), "送完以后更新包不再带样本");
            helper.succeed();
        });
    }

    /** 大 NBT 的样本只发 id 和数量；存档里是完整的 */
    @GameTest(template = EMPTY)
    public static void mirrorTrimsBigSampleInUpdate(GameTestHelper helper) {
        ItemStack book = new ItemStack(Items.WRITABLE_BOOK);
        book.getOrCreateTag().putString("big", "x".repeat(4096));
        ChestBlockEntity source = placeChest(helper, SOURCE);
        source.setItem(0, book);
        MagicalMirrorBlockEntity mirror = placeMirror(helper, MIRROR);
        placeChest(helper, TARGET);
        mirror.link(helper.absolutePos(TARGET));
        helper.assertTrue(mirror.tryStartTransfer(), "应开始送书");
        CompoundTag synced = mirror.getUpdateTag().getCompound(MagicalMirrorBlockEntity.SAMPLE_KEY);
        helper.assertTrue(!synced.contains("tag") && "minecraft:writable_book".equals(synced.getString("id")), "更新包里的大样本应只有 id 和数量");
        helper.assertTrue(mirror.saveWithoutMetadata().getCompound(MagicalMirrorBlockEntity.SAMPLE_KEY).getCompound("tag").contains("big"),
                "存档里应是完整的样本");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 控制棒与状态提示
    // ------------------------------------------------------------------

    /** 潜行右键空气忘掉记下的镜子；没记镜子时点别的方块什么都不做；空手右键镜子的状态文字 */
    @GameTest(template = EMPTY)
    public static void mirrorRodAndStatus(GameTestHelper helper) {
        setUp(helper, Items.COBBLESTONE, 1);
        MagicalMirrorBlockEntity mirror = placeMirror(helper, MIRROR);
        placeChest(helper, TARGET);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        ItemStack rod = new ItemStack(ECMirror.CONTROL_ROD.get());
        helper.assertTrue(useRod(helper, player, rod, TARGET) == InteractionResult.PASS, "没记镜子时点箱子应什么都不做");
        useRod(helper, player, rod, MIRROR);
        helper.assertTrue(ControlRodItem.selectedMirror(rod) != null, "应记下镜子");
        player.setShiftKeyDown(true);
        rod.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        player.setShiftKeyDown(false);
        helper.assertTrue(ControlRodItem.selectedMirror(rod) == null && !rod.hasTag(), "潜行右键空气应忘掉镜子（没有别的 NBT 时整个清掉）");

        helper.assertTrue("message.essentialcraft5.magical_mirror.unlinked".equals(key(mirror.statusMessage())), "没链接时的提示");
        mirror.link(helper.absolutePos(TARGET));
        helper.assertTrue("message.essentialcraft5.magical_mirror.linked".equals(key(mirror.statusMessage())), "链接好时的提示");
        helper.setBlock(SOURCE, Blocks.AIR);
        helper.assertTrue("message.essentialcraft5.magical_mirror.no_source".equals(key(mirror.statusMessage())), "下面没有容器时的提示");
        helper.succeed();
    }

    /**
     * 断开链接后的更新包不能是空标签：原版 ClientboundBlockEntityDataPacket 把空标签当成 null，客户端就不调 load，
     * 一直留着旧目标和「在送」（幽灵物品、粒子不停）。用一个新建的方块实体当客户端，照原版的收包路径喂进去。
     */
    @GameTest(template = EMPTY)
    public static void mirrorUnlinkUpdateReachesClient(GameTestHelper helper) {
        setUp(helper, Items.COBBLESTONE, 3);
        MagicalMirrorBlockEntity mirror = placeMirror(helper, MIRROR);
        placeChest(helper, TARGET);
        mirror.link(helper.absolutePos(TARGET));
        helper.assertTrue(mirror.tryStartTransfer(), "应开始送");
        MagicalMirrorBlockEntity client = new MagicalMirrorBlockEntity(ECMirror.MAGICAL_MIRROR_BE.get(), mirror.getBlockPos(), mirror.getBlockState());
        client.onDataPacket(null, ClientboundBlockEntityDataPacket.create(mirror));
        helper.assertTrue(client.isTransferring() && helper.absolutePos(TARGET).equals(client.getTarget()), "客户端应看到在送、目标对");

        helper.setBlock(TARGET, Blocks.AIR);
        helper.assertFalse(mirror.finishTransfer(), "目标没了这一件送不到");
        helper.assertTrue(mirror.getTarget() == null && !mirror.isTransferring(), "服务端应已断开链接、不在送");
        ClientboundBlockEntityDataPacket packet = ClientboundBlockEntityDataPacket.create(mirror);
        helper.assertTrue(packet.getTag() != null, "断开以后的更新包不能是空的（空的会被原版当成 null、客户端不读）");
        client.onDataPacket(null, packet);
        helper.assertTrue(client.getTarget() == null && !client.isTransferring() && client.getSample().isEmpty(), "客户端应清掉旧目标和在送的样本");
        helper.succeed();
    }

    /** 空手右键的状态会说明没在送的原因：下面的容器空了、目标满了；送得动时回到「链接到…」 */
    @GameTest(template = EMPTY)
    public static void mirrorStatusTellsWhyIdle(GameTestHelper helper) {
        ChestBlockEntity source = placeChest(helper, SOURCE);
        MagicalMirrorBlockEntity mirror = placeMirror(helper, MIRROR);
        ChestBlockEntity target = placeChest(helper, TARGET);
        mirror.link(helper.absolutePos(TARGET));
        helper.assertTrue(mirror.status() == MagicalMirrorBlockEntity.Status.LINKED, "刚链接时应是「链接到…」");
        helper.assertFalse(mirror.tryStartTransfer(), "下面的箱子是空的，不应开始");
        helper.assertTrue(mirror.status() == MagicalMirrorBlockEntity.Status.SOURCE_EMPTY
                && "message.essentialcraft5.magical_mirror.source_empty".equals(key(mirror.statusMessage())), "应说明下面的容器空了");

        source.setItem(0, new ItemStack(Items.COBBLESTONE, 2));
        for (int slot = 0; slot < target.getContainerSize(); slot++) {
            target.setItem(slot, new ItemStack(Items.DIRT, 64));
        }
        helper.assertFalse(mirror.tryStartTransfer(), "目标满了，不应开始");
        helper.assertTrue(mirror.status() == MagicalMirrorBlockEntity.Status.TARGET_FULL
                && "message.essentialcraft5.magical_mirror.target_full".equals(key(mirror.statusMessage())), "应说明目标满了");

        target.setItem(0, ItemStack.EMPTY);
        helper.assertTrue(mirror.tryStartTransfer(), "目标腾出一格，应开始送");
        helper.assertTrue(mirror.status() == MagicalMirrorBlockEntity.Status.LINKED, "在送的时候应是「链接到…」");
        helper.setBlock(SOURCE, Blocks.AIR);
        helper.assertTrue(mirror.status() == MagicalMirrorBlockEntity.Status.NO_SOURCE, "下面没有容器时应说明");
        helper.succeed();
    }

    /** 放在熔炉上：只能从顶面取走没烧的原料，烧好的成品留在熔炉里（同原作；手册照这个写） */
    @GameTest(template = EMPTY)
    public static void mirrorFurnaceSourceGivesInputOnly(GameTestHelper helper) {
        helper.setBlock(SOURCE, Blocks.FURNACE);
        Container furnace = (Container) helper.getBlockEntity(SOURCE);
        furnace.setItem(0, new ItemStack(Items.RAW_IRON, 1));
        furnace.setItem(2, new ItemStack(Items.IRON_INGOT, 3));
        MagicalMirrorBlockEntity mirror = placeMirror(helper, MIRROR);
        ChestBlockEntity target = placeChest(helper, TARGET);
        mirror.link(helper.absolutePos(TARGET));
        helper.assertTrue(mirror.tryStartTransfer() && mirror.getSample().is(Items.RAW_IRON), "应开始送原料格里的生铁");
        helper.assertTrue(mirror.finishTransfer(), "生铁应送到");
        helper.assertFalse(mirror.tryStartTransfer(), "原料格空了，成品格不应被取");
        helper.assertTrue(furnace.getItem(2).is(Items.IRON_INGOT) && furnace.getItem(2).getCount() == 3, "成品格的 3 个铁锭应原样留着");
        helper.assertTrue(count(target, Items.RAW_IRON) == 1 && count(target, Items.IRON_INGOT) == 0, "目标应只收到 1 个生铁");
        helper.succeed();
    }
}
