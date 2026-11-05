package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.MetadataManagerBlock;
import com.flameforge.essentialcraft5.block.MimicBlock;
import com.flameforge.essentialcraft5.block.RedstoneTransmitterBlock;
import com.flameforge.essentialcraft5.block.camo.Camo;
import com.flameforge.essentialcraft5.blockentity.MimicBlockEntity;
import com.flameforge.essentialcraft5.blockentity.RedstoneTransmitterBlockEntity;
import com.flameforge.essentialcraft5.blockentity.RedstoneTransmitterBlockEntity.Link;
import com.flameforge.essentialcraft5.item.BoundGemItem;
import com.flameforge.essentialcraft5.registry.ECItems;
import com.flameforge.essentialcraft5.registry.ECRedstoneTools;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 拟态方块与红石小件（ECRedstoneTools，batch4 §3.5）的 GameTest：
 * <ul>
 *   <li>拟态方块：拿石头右键变石头、物品不扣；两手空潜行右键还原（清掉后更新包不是空的）；存读档、更新包、模型数据；永远没碰撞、选框跟伪装走（半砖）、恒挡光 15、
 *       不闷人、不刷怪、液体流不进来；不收箱子 / 非方块 / 拟态方块自己；冒险模式改不了（被拒不动）；楼梯按玩家朝向和点中的面算；
 *       六个面都算结实（火把、按钮、拉杆、红石线、门贴得住，潜行放按钮放得上）、寻路当墙（原作 ROCK / SOLID）。</li>
 *   <li>红石传递器：真玩家潜行拿宝石绑定 → 放进另一个 → 拉杆式（红石块）充能 ≤4 刻后另一个输出 15、旁边的灯亮，拿掉回 0（没有领地时照常工作）；
 *       绑定目标在别人领地里绑不上、宝石不少（被拒的格子不动、不扣东西）；别的维度 / 绑自己 / 目标不是传递器 / 目标挨着别的传递器 / 目标没加载（之后仍没加载）/
 *       宝石没绑 / 没宝石 都输出 0 并显示对应状态；六个面都输出。</li>
 *   <li>方块状态检测器：小麦用标志 2 长到 7 期 ≤4 刻内输出 7（不用手动更新）、侧面的灯亮、前方那面不输出；蛋糕吃了 3 口 = 3、满的堆肥桶 = 8、石头 = 0、
 *       音符盒 20 封顶 15；优先表（海龟蛋取 eggs）；前方那面不连红石线；放下时朝向 = 点中的面。</li>
 * </ul>
 * 拟态方块、检测器的测试没有模拟领地：它们只动自己这一格、不探测（右键方块本身由领地插件管交互事件）。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class RedstoneToolsTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";

    private RedstoneToolsTests() {
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /** 点在某格顶面正中的命中结果 */
    private static BlockHitResult hitTop(GameTestHelper helper, BlockPos relative) {
        BlockPos abs = helper.absolutePos(relative);
        return new BlockHitResult(Vec3.atCenterOf(abs).add(0D, 0.5D, 0D), Direction.UP, abs, false);
    }

    /** 模拟玩家右键拟态方块（直接调方块的 use，就是服务端原版右键方块那一步） */
    private static InteractionResult useMimic(GameTestHelper helper, BlockPos relative, ServerPlayer player, InteractionHand hand) {
        BlockPos abs = helper.absolutePos(relative);
        return helper.getLevel().getBlockState(abs).use(helper.getLevel(), player, hand, hitTop(helper, relative));
    }

    private static MimicBlockEntity placeMimic(GameTestHelper helper, BlockPos relative) {
        helper.setBlock(relative, ECRedstoneTools.MIMIC.get());
        return (MimicBlockEntity) helper.getBlockEntity(relative);
    }

    private static RedstoneTransmitterBlockEntity placeTransmitter(GameTestHelper helper, BlockPos relative) {
        helper.setBlock(relative, ECRedstoneTools.REDSTONE_TRANSMITTER.get());
        return (RedstoneTransmitterBlockEntity) helper.getBlockEntity(relative);
    }

    private static ItemStack gemBoundTo(GlobalPos target) {
        ItemStack gem = new ItemStack(ECItems.BOUND_GEM.get());
        BoundGemItem.bind(gem, target, true);
        return gem;
    }

    private static int power(GameTestHelper helper, BlockPos relative) {
        return helper.getBlockState(relative).getValue(RedstoneTransmitterBlock.POWER);
    }

    private static int managerPower(GameTestHelper helper, BlockPos relative) {
        return helper.getBlockState(relative).getValue(MetadataManagerBlock.POWER);
    }

    /** 玩家身上（背包 + 副手）第一颗已绑定的宝石 */
    private static ItemStack boundGemOn(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof BoundGemItem && BoundGemItem.isBound(stack)) {
                return stack;
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.getItem() instanceof BoundGemItem && BoundGemItem.isBound(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    // ------------------------------------------------------------------
    // 拟态方块
    // ------------------------------------------------------------------

    /** 拿石头右键：变成石头、吃掉这次右键（石头不会顺手放出去）、物品不扣；更新包、模型数据、存读档都带着伪装 */
    @GameTest(template = EMPTY)
    public static void mimicTakesAppearanceWithoutConsuming(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        MimicBlockEntity mimic = placeMimic(helper, pos);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 64));

        InteractionResult result = useMimic(helper, pos, player, InteractionHand.MAIN_HAND);
        BlockState stone = Blocks.STONE.defaultBlockState();
        helper.assertTrue(result.consumesAction(), "拿石头右键应吃掉这次右键，实际 " + result);
        helper.assertTrue(mimic.camo() == stone, "应伪装成石头，实际 " + mimic.camo());
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        helper.assertTrue(held.is(Items.STONE) && held.getCount() == 64, "石头不应被消耗，实际 " + held);
        helper.assertTrue(mimic.getModelData().get(Camo.CAMO_STATE) == stone, "模型数据里应是石头");

        CompoundTag update = mimic.getUpdateTag();
        helper.assertTrue(Camo.read(update, MimicBlockEntity.TAG_CAMO) == stone, "更新包应带着伪装");

        BlockPos abs = helper.absolutePos(pos);
        MimicBlockEntity copy = new MimicBlockEntity(abs, helper.getBlockState(pos));
        copy.load(mimic.saveWithoutMetadata());
        helper.assertTrue(copy.camo() == stone, "存读档后伪装应还在，实际 " + copy.camo());

        // 再拿另一种方块右键：换成它
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_PLANKS));
        useMimic(helper, pos, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(mimic.camo() == Blocks.OAK_PLANKS.defaultBlockState(), "应换成橡木木板，实际 " + mimic.camo());
        helper.succeed();
    }

    /** 两只手都空着潜行右键：还原；手里有东西潜行右键不动（1.20 原版此时根本不调方块的 use）；清掉后更新包不是空的（否则客户端不会刷新） */
    @GameTest(template = EMPTY)
    public static void mimicSneakEmptyHandsClears(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        MimicBlockEntity mimic = placeMimic(helper, pos);
        mimic.setCamo(Blocks.STONE.defaultBlockState());
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        player.setShiftKeyDown(true);

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT));
        InteractionResult withItem = useMimic(helper, pos, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(!withItem.consumesAction() && mimic.camo() != null, "手里有东西潜行右键不应还原");

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        InteractionResult cleared = useMimic(helper, pos, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(cleared.consumesAction(), "空手潜行右键应吃掉这次右键，实际 " + cleared);
        helper.assertTrue(mimic.camo() == null, "空手潜行右键应还原，实际 " + mimic.camo());
        CompoundTag update = mimic.getUpdateTag();
        helper.assertFalse(update.isEmpty(), "清掉伪装后的更新标签不能是空的（空标签的包客户端不会读）");
        helper.assertTrue(Camo.read(update, MimicBlockEntity.TAG_CAMO) == null, "清掉后的更新包不应再带伪装");

        InteractionResult again = useMimic(helper, pos, player, InteractionHand.MAIN_HAND);
        helper.assertFalse(again.consumesAction(), "没有伪装时空手潜行右键应 PASS");
        helper.succeed();
    }

    /** 永远没碰撞；选框跟伪装走（下半砖 = 半格高），没伪装整格；恒挡光 15；不闷人、不挡视线、不刷怪、不当红石导体；液体流不进来 */
    @GameTest(template = EMPTY)
    public static void mimicShapesLightAndSpawning(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        MimicBlockEntity mimic = placeMimic(helper, pos);
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(pos);
        BlockState state = level.getBlockState(abs);

        helper.assertTrue(state.getCollisionShape(level, abs).isEmpty(), "没伪装时也不应有碰撞");
        helper.assertTrue(state.getShape(level, abs).bounds().maxY == 1D, "没伪装时选框应是整格");

        mimic.setCamo(Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        VoxelShape slab = state.getShape(level, abs, CollisionContext.empty());
        helper.assertTrue(slab.bounds().maxY == 0.5D, "伪装成下半砖时选框应是半格高，实际 " + slab.bounds());
        helper.assertTrue(state.getCollisionShape(level, abs).isEmpty(), "伪装以后也不应有碰撞");

        mimic.setCamo(Blocks.GLASS.defaultBlockState());
        helper.assertTrue(state.getLightBlock(level, abs) == 15, "伪装成玻璃也应恒挡光 15，实际 " + state.getLightBlock(level, abs));
        helper.assertFalse(state.propagatesSkylightDown(level, abs), "不应让天空光往下穿");
        helper.assertFalse(state.isSuffocating(level, abs), "不应闷人");
        helper.assertFalse(state.isViewBlocking(level, abs), "不应挡视线");
        helper.assertFalse(state.isRedstoneConductor(level, abs), "不应当红石导体");
        helper.assertFalse(state.isValidSpawn(level, abs, EntityType.ZOMBIE), "上面不应刷怪");
        helper.assertTrue(state.is(BlockTags.PREVENT_MOB_SPAWNING_INSIDE), "里面不应刷怪（minecraft:prevent_mob_spawning_inside，要先跑 runData）");
        helper.assertTrue(state.isSolid(), "液体应流不进来（forceSolidOn）");
        helper.succeed();
    }

    /** 不收箱子（有方块实体）、钻石（不是方块）、水桶、拟态方块自己；冒险模式的玩家拿石头右键不改（被拒的格子不动） */
    @GameTest(template = EMPTY)
    public static void mimicRejectsInvalidAndAdventure(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        MimicBlockEntity mimic = placeMimic(helper, pos);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);

        for (ItemStack held : new ItemStack[] {new ItemStack(Items.CHEST), new ItemStack(Items.DIAMOND),
                new ItemStack(Items.WATER_BUCKET), new ItemStack(ECRedstoneTools.MIMIC.get())}) {
            player.setItemInHand(InteractionHand.MAIN_HAND, held.copy());
            InteractionResult result = useMimic(helper, pos, player, InteractionHand.MAIN_HAND);
            helper.assertFalse(result.consumesAction(), held + " 不应被接受，实际 " + result);
            helper.assertTrue(mimic.camo() == null, held + " 不应成为伪装，实际 " + mimic.camo());
        }
        helper.assertFalse(MimicBlock.acceptsCamo(ECRedstoneTools.MIMIC.get().defaultBlockState()), "拟态方块不能模仿拟态方块");
        helper.assertFalse(Camo.isValid(ECRedstoneTools.MIMIC.get().defaultBlockState()), "拟态方块应在 mimic_blacklist 里（要先跑 runData）");

        player.getAbilities().mayBuild = false;
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 3));
        InteractionResult adventure = useMimic(helper, pos, player, InteractionHand.MAIN_HAND);
        helper.assertFalse(adventure.consumesAction(), "冒险模式不应能改伪装");
        helper.assertTrue(mimic.camo() == null, "冒险模式右键后伪装应不变");
        helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 3, "物品不应被动");
        helper.succeed();
    }

    /** 被模仿的状态按「放在拟态方块这一格」算：楼梯朝玩家面朝的方向、点顶面是下半 */
    @GameTest(template = EMPTY)
    public static void mimicUsesPlacementState(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        MimicBlockEntity mimic = placeMimic(helper, pos);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        player.setYRot(-90F);   // 面朝东
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_STAIRS));

        useMimic(helper, pos, player, InteractionHand.MAIN_HAND);
        BlockState camo = mimic.camo();
        helper.assertTrue(camo != null && camo.is(Blocks.OAK_STAIRS), "应伪装成橡木楼梯，实际 " + camo);
        helper.assertTrue(camo.getValue(StairBlock.FACING) == player.getDirection(),
                "楼梯应朝玩家面朝的方向 " + player.getDirection() + "，实际 " + camo.getValue(StairBlock.FACING));
        helper.assertTrue(camo.getValue(StairBlock.HALF) == Half.BOTTOM, "点顶面应是下半楼梯");
        helper.succeed();
    }

    /**
     * 六个面都算结实（原作 SOLID）：侧面贴得住墙上火把、石头按钮、拉杆，顶上立得住火把、红石线、木门；没伪装、伪装成下半砖都一样。
     * 真玩家潜行拿石头按钮点它的北面 → 按钮贴在上面、伪装不变。
     * 寻路当墙（原作 ROCK）：三种寻路都不可通行，这一格 BLOCKED、上面那格 WALKABLE（怪会走上拟态地板再掉下去）；碰撞仍为空。
     */
    @GameTest(template = EMPTY)
    public static void mimicFacesSturdyAndPathsAsWall(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        MimicBlockEntity mimic = placeMimic(helper, pos);
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(pos);

        BlockState slab = Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        for (BlockState camo : new BlockState[] {null, slab}) {
            mimic.setCamo(camo);
            String as = camo == null ? "没伪装时" : "伪装成下半砖时";
            BlockState state = level.getBlockState(abs);
            for (Direction direction : Direction.values()) {
                helper.assertTrue(state.isFaceSturdy(level, abs, direction), as + direction + " 面应算结实");
            }
            BlockState wallTorch = Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.EAST);
            helper.assertTrue(wallTorch.canSurvive(level, abs.east()), as + "墙上火把应贴得住东面");
            BlockState button = Blocks.STONE_BUTTON.defaultBlockState()
                    .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.WALL).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
            helper.assertTrue(button.canSurvive(level, abs.north()), as + "石头按钮应贴得住北面");
            BlockState lever = Blocks.LEVER.defaultBlockState()
                    .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.WALL).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
            helper.assertTrue(lever.canSurvive(level, abs.south()), as + "拉杆应贴得住南面");
            helper.assertTrue(Blocks.TORCH.defaultBlockState().canSurvive(level, abs.above()), as + "火把应立得住顶上");
            helper.assertTrue(Blocks.REDSTONE_WIRE.defaultBlockState().canSurvive(level, abs.above()), as + "红石线应铺得上顶面");
            helper.assertTrue(Blocks.OAK_DOOR.defaultBlockState().canSurvive(level, abs.above()), as + "木门应立得住顶上");
            helper.assertTrue(state.getCollisionShape(level, abs).isEmpty(), as + "仍不应有碰撞");
        }

        BlockState state = level.getBlockState(abs);
        for (PathComputationType type : PathComputationType.values()) {
            helper.assertFalse(state.isPathfindable(level, abs, type), "寻路应把它当墙（" + type + "）");
        }
        BlockPathTypes inside = WalkNodeEvaluator.getBlockPathTypeStatic(level, abs.mutable());
        helper.assertTrue(inside == BlockPathTypes.BLOCKED, "拟态方块这一格对陆地寻路应是 BLOCKED，实际 " + inside);
        BlockPathTypes above = WalkNodeEvaluator.getBlockPathTypeStatic(level, abs.above().mutable());
        helper.assertTrue(above == BlockPathTypes.WALKABLE, "拟态方块上面那格应是能走的地面 WALKABLE，实际 " + above);

        BlockState stone = Blocks.STONE.defaultBlockState();
        mimic.setCamo(stone);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        player.setShiftKeyDown(true);
        ItemStack buttons = new ItemStack(Items.STONE_BUTTON, 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, buttons);
        BlockHitResult northFace = new BlockHitResult(Vec3.atCenterOf(abs).add(0D, 0D, -0.5D), Direction.NORTH, abs, false);
        InteractionResult placed = buttons.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, northFace));
        helper.assertTrue(placed.consumesAction(), "潜行拿按钮点拟态墙的北面应放得上，实际 " + placed);
        BlockState onWall = helper.getBlockState(pos.north());
        helper.assertTrue(onWall.is(Blocks.STONE_BUTTON) && onWall.getValue(BlockStateProperties.ATTACH_FACE) == AttachFace.WALL
                        && onWall.getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.NORTH,
                "按钮应贴在拟态方块的北面，实际 " + onWall);
        helper.assertTrue(mimic.camo() == stone, "放按钮不应改伪装，实际 " + mimic.camo());
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 红石传递器
    // ------------------------------------------------------------------

    /**
     * 没有领地时照常工作（完整流程）：真玩家潜行拿宝石右键 A 绑定 → 宝石放进 B → A 旁边放红石块，≤4 刻后 B 输出 15、B 旁边的红石灯亮、状态「已连接」；
     * 拿掉红石块 ≤4 刻后 B 回 0。A 自己没宝石，输出 0。B 六个面都输出。
     */
    @GameTest(template = EMPTY_LARGE, timeoutTicks = 60)
    public static void transmitterRelaysSignal(GameTestHelper helper) {
        BlockPos a = new BlockPos(2, 2, 5);
        BlockPos b = new BlockPos(8, 2, 5);
        BlockPos lamp = new BlockPos(8, 2, 6);
        BlockPos source = new BlockPos(1, 2, 5);
        placeTransmitter(helper, a);
        RedstoneTransmitterBlockEntity receiver = placeTransmitter(helper, b);
        helper.setBlock(lamp, Blocks.REDSTONE_LAMP);

        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        player.setShiftKeyDown(true);
        ItemStack gem = new ItemStack(ECItems.BOUND_GEM.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, gem);
        InteractionResult bindResult = gem.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hitTop(helper, a)));
        helper.assertTrue(bindResult.consumesAction(), "没有领地时应能绑定，实际 " + bindResult);
        ItemStack bound = boundGemOn(player);
        helper.assertFalse(bound.isEmpty(), "玩家身上应有一颗绑好的宝石");
        GlobalPos expected = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(a));
        helper.assertTrue(expected.equals(BoundGemItem.getTarget(bound)), "宝石应绑到 A，实际 " + BoundGemItem.getTarget(bound));
        receiver.getInventory().setStackInSlot(RedstoneTransmitterBlockEntity.SLOT_GEM, bound.copy());
        helper.setBlock(source, Blocks.REDSTONE_BLOCK);

        helper.startSequence()
                .thenExecuteAfter(4, () -> {
                    helper.assertTrue(power(helper, b) == 15, "A 收到 15 时 B 应输出 15，实际 " + power(helper, b));
                    helper.assertTrue(receiver.link() == Link.LINKED, "B 应显示已连接，实际 " + receiver.link());
                    helper.assertTrue(helper.getBlockState(lamp).getValue(RedstoneLampBlock.LIT), "B 旁边的红石灯应亮");
                    helper.assertTrue(power(helper, a) == 0, "A 没放宝石，自己应输出 0，实际 " + power(helper, a));
                    BlockPos absB = helper.absolutePos(b);
                    BlockState stateB = helper.getLevel().getBlockState(absB);
                    for (Direction direction : Direction.values()) {
                        helper.assertTrue(stateB.getSignal(helper.getLevel(), absB, direction) == 15, "B 应六个面都输出，" + direction + " 面不是");
                    }
                    helper.setBlock(source, Blocks.AIR);
                })
                .thenExecuteAfter(4, () ->
                        helper.assertTrue(power(helper, b) == 0, "A 没信号了 B 应回 0，实际 " + power(helper, b)))
                .thenSucceed();
    }

    /** 被拒的格子不动、不扣东西：目标传递器在模拟领地里（绑的人不能动那一格）时绑不上，宝石原样留在手里、没有探测以外的事件 */
    @GameTest(template = EMPTY)
    public static void transmitterBindingDeniedInClaim(GameTestHelper helper) {
        BlockPos a = new BlockPos(2, 1, 2);
        placeTransmitter(helper, a);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        player.setShiftKeyDown(true);
        ItemStack gem = new ItemStack(ECItems.BOUND_GEM.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, gem);

        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(a)) {
            InteractionResult result = gem.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hitTop(helper, a)));
            helper.assertFalse(result.consumesAction(), "别人领地里的传递器不应绑上，实际 " + result);
            ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
            helper.assertTrue(held.getItem() instanceof BoundGemItem && held.getCount() == 1 && !BoundGemItem.isBound(held),
                    "宝石应原样留在手里（没绑定、一颗不少），实际 " + held);
            helper.assertTrue(boundGemOn(player).isEmpty(), "身上不应多出绑好的宝石");
            helper.assertTrue(claims.probes(a) >= 1, "应对目标格问过一次权限");
            helper.assertTrue(claims.places(a) == 0, "不应有放置事件");
        }
        helper.assertTrue(helper.getBlockState(a).is(ECRedstoneTools.REDSTONE_TRANSMITTER.get()), "传递器本身不应被动");
        helper.succeed();
    }

    /**
     * 输出 0 的各种情况（每个接收端都有对应的状态）：宝石绑别的维度、绑自己、目标不是传递器、目标旁边挨着传递器（防串联）、
     * 目标所在区块没加载（之后也没被加载）、宝石没绑定、没放宝石；对照组正常输出 15。
     */
    @GameTest(template = EMPTY_LARGE, timeoutTicks = 40)
    public static void transmitterZeroCases(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // 对照：A0 头顶红石块，B0 → A0
        BlockPos a0 = new BlockPos(1, 2, 1);
        placeTransmitter(helper, a0);
        helper.setBlock(new BlockPos(1, 3, 1), Blocks.REDSTONE_BLOCK);
        GlobalPos toA0 = GlobalPos.of(level.dimension(), helper.absolutePos(a0));
        BlockPos b0 = new BlockPos(3, 2, 1);
        placeTransmitter(helper, b0).getInventory().setStackInSlot(0, gemBoundTo(toA0));
        // 别的维度（同坐标）
        BlockPos b1 = new BlockPos(5, 2, 1);
        placeTransmitter(helper, b1).getInventory().setStackInSlot(0, gemBoundTo(GlobalPos.of(Level.NETHER, helper.absolutePos(a0))));
        // 绑自己
        BlockPos b2 = new BlockPos(7, 2, 1);
        placeTransmitter(helper, b2).getInventory().setStackInSlot(0, gemBoundTo(GlobalPos.of(level.dimension(), helper.absolutePos(b2))));
        // 目标是一块被充能的石头
        BlockPos stone = new BlockPos(9, 2, 3);
        helper.setBlock(stone, Blocks.STONE);
        helper.setBlock(new BlockPos(9, 3, 3), Blocks.REDSTONE_BLOCK);
        BlockPos b3 = new BlockPos(9, 2, 1);
        placeTransmitter(helper, b3).getInventory().setStackInSlot(0, gemBoundTo(GlobalPos.of(level.dimension(), helper.absolutePos(stone))));
        // 目标 A4 旁边挨着另一个传递器 C4
        BlockPos a4 = new BlockPos(1, 2, 5);
        placeTransmitter(helper, a4);
        placeTransmitter(helper, new BlockPos(1, 2, 6));
        helper.setBlock(new BlockPos(1, 3, 5), Blocks.REDSTONE_BLOCK);
        BlockPos b4 = new BlockPos(3, 2, 5);
        placeTransmitter(helper, b4).getInventory().setStackInSlot(0, gemBoundTo(GlobalPos.of(level.dimension(), helper.absolutePos(a4))));
        // 目标在很远的没加载的区块
        BlockPos far = helper.absolutePos(a0).offset(160_000, 0, 0);
        int farX = SectionPos.blockToSectionCoord(far.getX());
        int farZ = SectionPos.blockToSectionCoord(far.getZ());
        helper.assertTrue(level.getChunkSource().getChunkNow(farX, farZ) == null, "远处的区块一开始就不该是加载的");
        BlockPos b5 = new BlockPos(5, 2, 5);
        placeTransmitter(helper, b5).getInventory().setStackInSlot(0, gemBoundTo(GlobalPos.of(level.dimension(), far)));
        // 宝石没绑定
        BlockPos b6 = new BlockPos(7, 2, 5);
        placeTransmitter(helper, b6).getInventory().setStackInSlot(0, new ItemStack(ECItems.BOUND_GEM.get()));
        // 没放宝石
        BlockPos b7 = new BlockPos(9, 2, 5);
        placeTransmitter(helper, b7);

        helper.runAfterDelay(5, () -> {
            check(helper, b0, 15, Link.LINKED);
            check(helper, b1, 0, Link.OTHER_DIMENSION);
            check(helper, b2, 0, Link.SELF);
            check(helper, b3, 0, Link.NOT_TRANSMITTER);
            check(helper, b4, 0, Link.CHAINED);
            check(helper, b5, 0, Link.NOT_LOADED);
            check(helper, b6, 0, Link.UNBOUND);
            check(helper, b7, 0, Link.NO_GEM);
            helper.assertTrue(level.getChunkSource().getChunkNow(farX, farZ) == null, "读远处的传递器不应加载对方区块");
            helper.succeed();
        });
    }

    private static void check(GameTestHelper helper, BlockPos relative, int expectedPower, Link expectedLink) {
        RedstoneTransmitterBlockEntity be = (RedstoneTransmitterBlockEntity) helper.getBlockEntity(relative);
        helper.assertTrue(power(helper, relative) == expectedPower,
                relative + " 应输出 " + expectedPower + "，实际 " + power(helper, relative));
        helper.assertTrue(be.link() == expectedLink, relative + " 状态应是 " + expectedLink + "，实际 " + be.link());
    }

    /** 放下时朝向 = 点中那一面的反面（指向贴着的方块）；只收绑定宝石 */
    @GameTest(template = EMPTY)
    public static void transmitterPlacementAndSlot(GameTestHelper helper) {
        BlockPos support = new BlockPos(2, 1, 2);
        helper.setBlock(support, Blocks.STONE);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        BlockPos abs = helper.absolutePos(support);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).relative(Direction.NORTH, 0.5D), Direction.NORTH, abs, false);
        BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND,
                new ItemStack(ECRedstoneTools.REDSTONE_TRANSMITTER.get()), hit);
        BlockState placed = ECRedstoneTools.REDSTONE_TRANSMITTER.get().getStateForPlacement(context);
        helper.assertTrue(placed != null && placed.getValue(RedstoneTransmitterBlock.FACING) == Direction.SOUTH,
                "点石头北面放下时应朝南（指向石头），实际 " + placed);
        helper.assertTrue(placed.getValue(RedstoneTransmitterBlock.POWER) == 0, "刚放下应输出 0");

        helper.assertTrue(RedstoneTransmitterBlockEntity.slotRule(0, new ItemStack(ECItems.BOUND_GEM.get())), "应收绑定宝石");
        helper.assertFalse(RedstoneTransmitterBlockEntity.slotRule(0, new ItemStack(Items.DIAMOND)), "不应收别的东西");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 方块状态检测器
    // ------------------------------------------------------------------

    private static BlockState manager(Direction facing) {
        return ECRedstoneTools.METADATA_MANAGER.get().defaultBlockState().setValue(MetadataManagerBlock.FACING, facing);
    }

    /** 小麦用标志 2 长到 7 期（和随机刻生长一样）：≤4 刻内输出 7，不用手动给更新；侧面的红石灯亮；前方那面不输出 */
    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void metadataFollowsCropGrowth(GameTestHelper helper) {
        BlockPos m = new BlockPos(1, 2, 2);
        BlockPos crop = new BlockPos(2, 2, 2);
        BlockPos lamp = new BlockPos(1, 2, 1);
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.FARMLAND);
        helper.setBlock(crop, Blocks.WHEAT.defaultBlockState());
        helper.setBlock(lamp, Blocks.REDSTONE_LAMP);
        helper.setBlock(m, manager(Direction.EAST));

        helper.startSequence()
                .thenExecuteAfter(4, () -> {
                    int age = helper.getBlockState(crop).getValue(CropBlock.AGE);
                    helper.assertTrue(managerPower(helper, m) == age, "应输出小麦现在的生长期 " + age + "，实际 " + managerPower(helper, m));
                    helper.getLevel().setBlock(helper.absolutePos(crop), ((CropBlock) Blocks.WHEAT).getStateForAge(7), Block.UPDATE_CLIENTS);
                })
                .thenExecuteAfter(4, () -> {
                    helper.assertTrue(managerPower(helper, m) == 7, "小麦熟了（7 期）应输出 7，实际 " + managerPower(helper, m));
                    helper.assertTrue(helper.getBlockState(lamp).getValue(RedstoneLampBlock.LIT), "侧面的红石灯应亮");
                    BlockPos abs = helper.absolutePos(m);
                    BlockState state = helper.getLevel().getBlockState(abs);
                    helper.assertTrue(state.getSignal(helper.getLevel(), abs, Direction.WEST) == 0, "前方那格来问时应是 0");
                    for (Direction direction : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.UP, Direction.DOWN, Direction.EAST}) {
                        helper.assertTrue(state.getSignal(helper.getLevel(), abs, direction) == 7, direction + " 方向应输出 7");
                    }
                })
                .thenSucceed();
    }

    /** 各种方块的数值：蛋糕吃了 3 口 = 3、满的堆肥桶 = 8、石头 = 0、音符盒 20 封顶 15；放下 / 换上以后自己算一次 */
    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void metadataReadsBlockValues(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.CAKE.defaultBlockState().setValue(CakeBlock.BITES, 3));
        BlockPos mCake = new BlockPos(2, 2, 1);
        helper.setBlock(mCake, manager(Direction.WEST));

        helper.setBlock(new BlockPos(1, 2, 3), Blocks.COMPOSTER.defaultBlockState().setValue(ComposterBlock.LEVEL, 8));
        BlockPos mComposter = new BlockPos(2, 2, 3);
        helper.setBlock(mComposter, manager(Direction.WEST));

        helper.setBlock(new BlockPos(4, 3, 1), Blocks.STONE);
        BlockPos mStone = new BlockPos(4, 2, 1);
        helper.setBlock(mStone, manager(Direction.UP));

        helper.setBlock(new BlockPos(4, 2, 4), Blocks.NOTE_BLOCK.defaultBlockState().setValue(NoteBlock.NOTE, 20));
        BlockPos mNote = new BlockPos(4, 2, 3);
        helper.setBlock(mNote, manager(Direction.SOUTH));

        helper.runAfterDelay(4, () -> {
            helper.assertTrue(managerPower(helper, mCake) == 3, "蛋糕吃了 3 口应输出 3，实际 " + managerPower(helper, mCake));
            helper.assertTrue(managerPower(helper, mComposter) == 8, "满的堆肥桶应输出 8，实际 " + managerPower(helper, mComposter));
            helper.assertTrue(managerPower(helper, mStone) == 0, "石头没有数值应输出 0，实际 " + managerPower(helper, mStone));
            helper.assertTrue(managerPower(helper, mNote) == 15, "音符盒 20 应封顶 15，实际 " + managerPower(helper, mNote));
            helper.succeed();
        });
    }

    /** 纯逻辑：优先表（海龟蛋取 eggs 不取 hatch）、红石线取 power、水源 level 0；前方那面不连红石线；不当红石导体；放下时朝向 = 点中的面 */
    @GameTest(template = EMPTY)
    public static void metadataRulesAndPlacement(GameTestHelper helper) {
        helper.assertTrue(MetadataManagerBlock.valueOf(Blocks.TURTLE_EGG.defaultBlockState()
                .setValue(TurtleEggBlock.EGGS, 3).setValue(TurtleEggBlock.HATCH, 1)) == 3, "海龟蛋应取蛋的个数");
        helper.assertTrue(MetadataManagerBlock.valueOf(Blocks.REDSTONE_WIRE.defaultBlockState().setValue(RedStoneWireBlock.POWER, 9)) == 9,
                "红石线应取信号强度");
        helper.assertTrue(MetadataManagerBlock.valueOf(Blocks.WATER.defaultBlockState()) == 0, "水源 level 0");
        helper.assertTrue(MetadataManagerBlock.valueOf(Blocks.AIR.defaultBlockState()) == 0, "空气没有数值");

        BlockPos m = new BlockPos(2, 1, 2);
        helper.setBlock(m, manager(Direction.EAST));
        BlockPos abs = helper.absolutePos(m);
        BlockState state = helper.getLevel().getBlockState(abs);
        helper.assertFalse(state.canRedstoneConnectTo(helper.getLevel(), abs, Direction.WEST), "前方的红石线（指向本方块是西）不应连上");
        helper.assertTrue(state.canRedstoneConnectTo(helper.getLevel(), abs, Direction.EAST), "背面的红石线应连上");
        helper.assertTrue(state.canRedstoneConnectTo(helper.getLevel(), abs, Direction.NORTH), "侧面的红石线应连上");
        helper.assertFalse(state.canRedstoneConnectTo(helper.getLevel(), abs, null), "null 方向不连");
        helper.assertFalse(state.isRedstoneConductor(helper.getLevel(), abs), "不应当红石导体");

        BlockPos support = new BlockPos(2, 1, 4);
        helper.setBlock(support, Blocks.STONE);
        BlockPos supportAbs = helper.absolutePos(support);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(supportAbs).relative(Direction.NORTH, 0.5D), Direction.NORTH, supportAbs, false);
        BlockState placed = ECRedstoneTools.METADATA_MANAGER.get().getStateForPlacement(new BlockPlaceContext(player, InteractionHand.MAIN_HAND,
                new ItemStack(ECRedstoneTools.METADATA_MANAGER.get()), hit));
        helper.assertTrue(placed != null && placed.getValue(MetadataManagerBlock.FACING) == Direction.NORTH,
                "点石头北面放下时前方应朝北（背对石头），实际 " + placed);
        helper.succeed();
    }
}
