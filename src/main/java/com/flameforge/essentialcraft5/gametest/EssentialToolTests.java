package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.item.CreativeVariants;
import com.flameforge.essentialcraft5.item.EssentialTool;
import com.flameforge.essentialcraft5.item.EssentialToolEvents;
import com.flameforge.essentialcraft5.registry.ECEssentialTools;
import com.flameforge.essentialcraft5.registry.ECGear;
import com.flameforge.essentialcraft5.registry.ECItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 源质镐 / 源质斧（第十二批，batch12.md §4.4.8）的 GameTest：
 * <ul>
 *   <li>源质镐：左键记下的面决定 3×3 的方向（竖墙、水平面），记下的格子对不上时用视线射线的面；拆中心那一下邻格不动、本 tick 末尾才挖，
 *       中心那格的钱也在 tick 末尾确认方块真没了再扣；每格一份钱；带方块实体的、太硬的、挖不动的、不是镐挖的方块留着；潜行只挖一格；
 *       自己没电时速度 1、付不起就不连带；创造模式只拆中心那格、不扣钱；模拟领地拒绝的格子留着、只收到一次破坏事件、不扣钱。</li>
 *   <li>源质斧：天然树（周围有不是玩家放的树叶）整棵砍倒，掉落物都挪到你砍的那一格；同样的原木配玩家放的树叶只砍一根；
 *       一棵最多 {@code max_logs} 根（含中心，上限 1 时只砍中心）；每根一份钱、先从身上的储存物品扣；砍到一半换手（哪怕换成另一把源质斧）剩下的不砍。</li>
 *   <li>不掉耐久、附不上耐久 / 经验修补，能附效率 / 时运 / 精准采集；挖得动要下界合金镐的方块；配方已加载；创造栏空 / 满两份。</li>
 * </ul>
 * 真玩家用 {@link ProtectionFrameworkTests#realPlayer}（不在玩家列表里；待办和砍树任务按记下的玩家对象判断在线），模拟领地用
 * {@link ProtectionFrameworkTests.Claims}。挖用 {@code player.gameMode.destroyBlock}（服务端真实的玩家挖方块流程），
 * 左键用 Forge 的 {@code PlayerInteractEvent.LeftClickBlock}（START，和服务端收到「开始挖」时发的同一个事件）。
 * 数值一律从 {@code ECEssentialTools.Config.values()} 取（测试不改配置）。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class EssentialToolTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    /** 「empty」里 3×3 墙 / 面的中心 */
    private static final BlockPos CENTER = new BlockPos(2, 2, 2);
    /** 「empty_large」里你亲手砍的那根原木（树根） */
    private static final BlockPos ROOT = new BlockPos(5, 1, 5);

    private EssentialToolTests() {
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private static ECEssentialTools.Config.Values cfg() {
        return ECEssentialTools.Config.values();
    }

    private static ItemStack pickaxe() {
        return EssentialTool.full(ECEssentialTools.ESSENTIAL_PICKAXE.get());
    }

    private static ItemStack axe() {
        return EssentialTool.full(ECEssentialTools.ESSENTIAL_AXE.get());
    }

    private static int mru(ItemStack stack) {
        return stack.getCapability(ECCapabilities.MRU_ITEM).map(handler -> handler.getMRU()).orElse(-1);
    }

    private static ServerPlayer holding(GameTestHelper helper, ItemStack stack) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return player;
    }

    /** 服务端收到「开始挖」时发的左键事件 */
    private static void leftClick(GameTestHelper helper, ServerPlayer player, BlockPos relative, Direction face) {
        MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.LeftClickBlock(player, helper.absolutePos(relative), face,
                PlayerInteractEvent.LeftClickBlock.Action.START));
    }

    private static boolean mine(GameTestHelper helper, ServerPlayer player, BlockPos relative) {
        return player.gameMode.destroyBlock(helper.absolutePos(relative));
    }

    /** 垂直于 axis 的平面上、以 center 为中心的 3×3（含中心） */
    private static List<BlockPos> plane(BlockPos center, Direction.Axis axis) {
        List<BlockPos> cells = new ArrayList<>(9);
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                cells.add(switch (axis) {
                    case X -> center.offset(0, a, b);
                    case Y -> center.offset(a, 0, b);
                    case Z -> center.offset(a, b, 0);
                });
            }
        }
        return cells;
    }

    private static void fill(GameTestHelper helper, List<BlockPos> cells, Block block) {
        cells.forEach(pos -> helper.setBlock(pos, block));
    }

    private static int count(GameTestHelper helper, List<BlockPos> cells, Block block) {
        int n = 0;
        for (BlockPos pos : cells) {
            if (helper.getBlockState(pos).is(block)) {
                n++;
            }
        }
        return n;
    }

    /**
     * 整个测试区域外扩两格（不碰到相邻测试：GameTest 横向每两个结构之间空 5 格）
     *
     * @param size 结构的边长（「empty」5、「empty_large」11）
     */
    private static AABB area(GameTestHelper helper, int size) {
        return new AABB(helper.absoluteVec(new Vec3(-2D, -2D, -2D)), helper.absoluteVec(new Vec3(size + 2D, size + 2D, size + 2D)));
    }

    /** 整个测试区域里某种掉落物的总数 */
    private static int droppedAnywhere(GameTestHelper helper, int size, Item item) {
        return dropped(helper, area(helper, size), item);
    }

    /** 某一格附近（水平、上下各 0.75 格）某种掉落物的总数 */
    private static int droppedAt(GameTestHelper helper, BlockPos relative, Item item) {
        return dropped(helper, new AABB(helper.absolutePos(relative)).inflate(0.75D), item);
    }

    private static int dropped(GameTestHelper helper, AABB box, Item item) {
        int n = 0;
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box, Entity::isAlive)) {
            if (entity.getItem().is(item)) {
                n += entity.getItem().getCount();
            }
        }
        return n;
    }

    /**
     * 收尾：这名玩家留下的待办 / 砍树任务 / 左键记录清掉，掉落物清掉。砍树的测试（size 11）再把结构里的方块全换成空气（不通知邻格）：
     * 原木砍掉以后剩下的天然树叶会慢慢腐烂，留在世界里会在后面别的测试跑的时候掉叶子、改方块
     * （海纳尔城镇的放置测试会把结构包围盒外的方块改动当成失败）。
     */
    private static void cleanup(GameTestHelper helper, ServerPlayer player, int size) {
        EssentialToolEvents.forgetForTest(player.getUUID());
        clearArea(helper, size);
    }

    /** {@link #cleanup} 里不涉及玩家的那一半：结构里的方块（size 11 时）和掉落物 */
    private static void clearArea(GameTestHelper helper, int size) {
        if (size > 5) {
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.ZERO, new BlockPos(size - 1, 7, size - 1))) {
                helper.getLevel().setBlock(helper.absolutePos(pos), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, area(helper, size)).forEach(Entity::discard);
    }

    // ------------------------------------------------------------------
    // 树
    // ------------------------------------------------------------------

    private static BlockState leaves(boolean persistent) {
        return Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, persistent).setValue(LeavesBlock.DISTANCE, 1);
    }

    /** 「empty_large」最底层铺一层石头当地面（掉落物落在上面，不掉出测试区域） */
    private static void floor(GameTestHelper helper) {
        for (int x = 0; x <= 10; x++) {
            for (int z = 0; z <= 10; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    /**
     * 树根在 {@link #ROOT}：竖着 trunk 根，顶上往 +x 伸 branch 根树枝；树干顶和每根树枝上面各一片橡树树叶，树枝末端两侧再各一片。
     * persistentLeaves = true 时树叶是玩家放的（{@code persistent}），用来模拟「原木盖的房子」。
     *
     * @return 全部原木的位置（第一个是树根）
     */
    private static List<BlockPos> branchedTree(GameTestHelper helper, int trunk, int branch, boolean persistentLeaves) {
        floor(helper);
        List<BlockPos> logs = new ArrayList<>();
        for (int y = 0; y < trunk; y++) {
            logs.add(ROOT.above(y));
        }
        BlockPos top = ROOT.above(trunk - 1);
        for (int x = 1; x <= branch; x++) {
            logs.add(top.east(x));
        }
        fill(helper, logs, Blocks.OAK_LOG);
        BlockState leaf = leaves(persistentLeaves);
        helper.setBlock(top.above(), leaf);
        for (int x = 1; x <= branch; x++) {
            helper.setBlock(top.east(x).above(), leaf);
        }
        BlockPos tip = top.east(branch);
        helper.setBlock(tip.north(), leaf);
        helper.setBlock(tip.south(), leaf);
        return logs;
    }

    /**
     * x、z 在树根 ± 2、y 从 1 到 height 的实心原木块（5 × 5 × height 根），顶上一层天然树叶，最底层四条边的正中外侧再各一片。
     * 实心块而不是一根高柱子：找树默认只往上看 {@code felling_height}（48）格，柱子最多收 49 根，到不了 {@code max_logs}。
     * 底层那 4 片不能省：天然树叶只数收进来的原木旁边的，收满 {@code max_logs} 时顶层大半没收进来，只靠顶上的树叶凑不够数。
     *
     * @return 全部原木的位置
     */
    private static List<BlockPos> logBlock(GameTestHelper helper, int height) {
        floor(helper);
        List<BlockPos> logs = new ArrayList<>();
        for (int y = 1; y <= height; y++) {
            for (int x = ROOT.getX() - 2; x <= ROOT.getX() + 2; x++) {
                for (int z = ROOT.getZ() - 2; z <= ROOT.getZ() + 2; z++) {
                    logs.add(new BlockPos(x, y, z));
                }
            }
        }
        fill(helper, logs, Blocks.OAK_LOG);
        BlockState leaf = leaves(false);
        for (int x = ROOT.getX() - 2; x <= ROOT.getX() + 2; x++) {
            for (int z = ROOT.getZ() - 2; z <= ROOT.getZ() + 2; z++) {
                helper.setBlock(new BlockPos(x, height + 1, z), leaf);
            }
        }
        helper.setBlock(ROOT.west(3), leaf);
        helper.setBlock(ROOT.east(3), leaf);
        helper.setBlock(ROOT.north(3), leaf);
        helper.setBlock(ROOT.south(3), leaf);
        return logs;
    }

    // ------------------------------------------------------------------
    // 源质镐
    // ------------------------------------------------------------------

    /**
     * 竖着一面 3×3 石墙（墙后再贴一层石头），对墙的正面（北面）左键后拆中心：拆的那一下只动中心、还没扣钱；
     * 本 tick 末尾墙上 9 格全拆、扣 9 份钱，墙后那层一格不少，每格各掉一个圆石
     */
    @GameTest(template = EMPTY)
    public static void pickaxeFaceArea(GameTestHelper helper) {
        List<BlockPos> wall = plane(CENTER, Direction.Axis.Z);
        List<BlockPos> behind = plane(CENTER.south(), Direction.Axis.Z);
        fill(helper, wall, Blocks.STONE);
        fill(helper, behind, Blocks.STONE);
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        ItemStack tool = pickaxe();
        int full = mru(tool);
        int cost = cfg().mruPerBlock();
        ServerPlayer player = holding(helper, tool);
        leftClick(helper, player, CENTER, Direction.NORTH);
        helper.assertTrue(mine(helper, player, CENTER), "中心那格应挖得掉");
        helper.assertBlockPresent(Blocks.AIR, CENTER);
        helper.assertTrue(count(helper, wall, Blocks.STONE) == 8, "拆中心那一下不应动邻格（推迟到本 tick 末尾）");
        helper.assertTrue(mru(tool) == full, "中心那格的钱应在 tick 末尾确认拆掉后再扣，现在剩 " + mru(tool));
        helper.runAfterDelay(1, () -> {
            try {
                helper.assertTrue(count(helper, wall, Blocks.STONE) == 0, "墙上 9 格应全拆，还剩 " + count(helper, wall, Blocks.STONE));
                helper.assertTrue(count(helper, behind, Blocks.STONE) == 9, "墙后那层不应动，剩 " + count(helper, behind, Blocks.STONE));
                helper.assertTrue(mru(tool) == full - cost * 9, "应扣 9 × " + cost + "，剩 " + mru(tool));
                int cobble = droppedAnywhere(helper, 5, Items.COBBLESTONE);
                helper.assertTrue(cobble == 9, "每格（含中心）应各掉一个圆石，实际 " + cobble);
            } finally {
                cleanup(helper, player, 5);
            }
            helper.succeed();
        });
    }

    /** 对顶面左键：拆的是水平的 3×3，上下两层不动 */
    @GameTest(template = EMPTY)
    public static void pickaxeTopFace(GameTestHelper helper) {
        List<BlockPos> layer = plane(CENTER, Direction.Axis.Y);
        List<BlockPos> below = plane(CENTER.below(), Direction.Axis.Y);
        List<BlockPos> above = plane(CENTER.above(), Direction.Axis.Y);
        fill(helper, layer, Blocks.STONE);
        fill(helper, below, Blocks.STONE);
        fill(helper, above, Blocks.STONE);
        ItemStack tool = pickaxe();
        int full = mru(tool);
        ServerPlayer player = holding(helper, tool);
        leftClick(helper, player, CENTER, Direction.UP);
        helper.assertTrue(mine(helper, player, CENTER), "中心那格应挖得掉");
        helper.runAfterDelay(1, () -> {
            try {
                helper.assertTrue(count(helper, layer, Blocks.STONE) == 0, "中心所在的水平 3×3 应全拆，还剩 " + count(helper, layer, Blocks.STONE));
                helper.assertTrue(count(helper, below, Blocks.STONE) == 9 && count(helper, above, Blocks.STONE) == 9, "上下两层不应动");
                helper.assertTrue(mru(tool) == full - cfg().mruPerBlock() * 9, "应扣 9 格的钱，剩 " + mru(tool));
            } finally {
                cleanup(helper, player, 5);
            }
            helper.succeed();
        });
    }

    /**
     * 左键记下的是另一格（墙后那格的顶面）：和挖的这格对不上，改用视线射线打中的面。玩家站在墙前平视墙的中心，
     * 射线打在北面，拆的是竖墙；照旧记录的「顶面」拆就会连墙后那排一起拆掉
     */
    @GameTest(template = EMPTY)
    public static void pickaxeFaceFromSight(GameTestHelper helper) {
        List<BlockPos> wall = plane(CENTER, Direction.Axis.Z);
        List<BlockPos> behind = plane(CENTER.south(), Direction.Axis.Z);
        fill(helper, wall, Blocks.STONE);
        fill(helper, behind, Blocks.STONE);
        ItemStack tool = pickaxe();
        ServerPlayer player = holding(helper, tool);
        Vec3 eyeLine = helper.absoluteVec(new Vec3(2.5D, 1D, 0.5D));
        player.moveTo(eyeLine.x, eyeLine.y, eyeLine.z, 0F, 0F);
        player.setYHeadRot(0F);
        leftClick(helper, player, CENTER.south(), Direction.UP);
        helper.assertTrue(mine(helper, player, CENTER), "中心那格应挖得掉");
        helper.runAfterDelay(1, () -> {
            try {
                helper.assertTrue(count(helper, wall, Blocks.STONE) == 0, "按视线打中的北面应拆竖墙，还剩 " + count(helper, wall, Blocks.STONE));
                helper.assertTrue(count(helper, behind, Blocks.STONE) == 9, "墙后那层不应动（没用对不上的左键记录），剩 " + count(helper, behind, Blocks.STONE));
            } finally {
                cleanup(helper, player, 5);
            }
            helper.succeed();
        });
    }

    /**
     * 邻格放熔炉（有方块实体）、黑曜石（比中心硬太多）、基岩（挖不动）、泥土（不是镐挖的）→ 都留着；
     * 铁矿石和其余石头照拆；只为拆掉的格子付钱
     */
    @GameTest(template = EMPTY)
    public static void pickaxeSkips(GameTestHelper helper) {
        List<BlockPos> wall = plane(CENTER, Direction.Axis.Z);
        fill(helper, wall, Blocks.STONE);
        BlockPos furnace = new BlockPos(1, 1, 2);
        BlockPos obsidian = new BlockPos(3, 1, 2);
        BlockPos bedrock = new BlockPos(1, 3, 2);
        BlockPos dirt = new BlockPos(3, 3, 2);
        BlockPos ore = new BlockPos(2, 1, 2);
        helper.setBlock(furnace, Blocks.FURNACE);
        helper.setBlock(obsidian, Blocks.OBSIDIAN);
        helper.setBlock(bedrock, Blocks.BEDROCK);
        helper.setBlock(dirt, Blocks.DIRT);
        helper.setBlock(ore, Blocks.IRON_ORE);
        ItemStack tool = pickaxe();
        int full = mru(tool);
        ServerPlayer player = holding(helper, tool);
        leftClick(helper, player, CENTER, Direction.NORTH);
        helper.assertTrue(mine(helper, player, CENTER), "中心那格应挖得掉");
        helper.runAfterDelay(1, () -> {
            try {
                helper.assertBlockPresent(Blocks.FURNACE, furnace);
                helper.assertBlockPresent(Blocks.OBSIDIAN, obsidian);
                helper.assertBlockPresent(Blocks.BEDROCK, bedrock);
                helper.assertBlockPresent(Blocks.DIRT, dirt);
                helper.assertBlockPresent(Blocks.AIR, ore);
                helper.assertTrue(count(helper, wall, Blocks.STONE) == 0, "其余石头应全拆，还剩 " + count(helper, wall, Blocks.STONE));
                // 中心 + 铁矿石 + 3 块石头
                helper.assertTrue(mru(tool) == full - cfg().mruPerBlock() * 5, "只应为拆掉的 5 格付钱，剩 " + mru(tool));
            } finally {
                cleanup(helper, player, 5);
            }
            helper.succeed();
        });
    }

    /** 潜行：只挖中心那一格，照样付那一格的钱 */
    @GameTest(template = EMPTY)
    public static void pickaxeSneakSingle(GameTestHelper helper) {
        List<BlockPos> wall = plane(CENTER, Direction.Axis.Z);
        fill(helper, wall, Blocks.STONE);
        ItemStack tool = pickaxe();
        int full = mru(tool);
        ServerPlayer player = holding(helper, tool);
        player.setShiftKeyDown(true);
        leftClick(helper, player, CENTER, Direction.NORTH);
        helper.assertTrue(mine(helper, player, CENTER), "潜行也应挖得掉中心那格");
        helper.runAfterDelay(1, () -> {
            try {
                helper.assertBlockPresent(Blocks.AIR, CENTER);
                helper.assertTrue(count(helper, wall, Blocks.STONE) == 8, "潜行时不应连带");
                helper.assertTrue(mru(tool) == full - cfg().mruPerBlock(), "潜行挖中心那格也应付一格的钱，剩 " + mru(tool));
            } finally {
                cleanup(helper, player, 5);
            }
            helper.succeed();
        });
    }

    /** 创造模式：拆方块不走 mineBlock，只拆中心那一格、不掉落，不扣 MRU，也不留待办（手册「创造模式下两件都只挖一格」） */
    @GameTest(template = EMPTY)
    public static void pickaxeCreativeSingle(GameTestHelper helper) {
        List<BlockPos> wall = plane(CENTER, Direction.Axis.Z);
        fill(helper, wall, Blocks.STONE);
        ItemStack tool = pickaxe();
        int full = mru(tool);
        ServerPlayer player = holding(helper, tool);
        player.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
        leftClick(helper, player, CENTER, Direction.NORTH);
        helper.assertTrue(mine(helper, player, CENTER), "创造模式也应拆得掉中心那格");
        helper.runAfterDelay(1, () -> {
            try {
                helper.assertBlockPresent(Blocks.AIR, CENTER);
                helper.assertTrue(count(helper, wall, Blocks.STONE) == 8, "创造模式不应连带，剩 " + count(helper, wall, Blocks.STONE));
                helper.assertTrue(mru(tool) == full, "创造模式不应扣 MRU，剩 " + mru(tool));
                helper.assertTrue(droppedAnywhere(helper, 5, Items.COBBLESTONE) == 0, "创造模式拆的不应掉落");
            } finally {
                cleanup(helper, player, 5);
            }
            helper.succeed();
        });
    }

    /**
     * 自己存的 MRU 不到一格的价钱：挖石头的速度是 1（满电时 12、挖泥土这类不是镐挖的方块 1）；
     * 身上也没有储存物品时付不起，中心那格照原版规则挖掉、不扣、不连带
     */
    @GameTest(template = EMPTY)
    public static void pickaxeNoMruSlow(GameTestHelper helper) {
        BlockState stone = Blocks.STONE.defaultBlockState();
        int cost = cfg().mruPerBlock();
        ItemStack empty = EssentialTool.withMRU(ECEssentialTools.ESSENTIAL_PICKAXE.get(), 0);
        helper.assertTrue(empty.getDestroySpeed(stone) == 1F, "自己没电时挖石头速度应为 1，实际 " + empty.getDestroySpeed(stone));
        if (cost > 1) {
            ItemStack almost = EssentialTool.withMRU(ECEssentialTools.ESSENTIAL_PICKAXE.get(), cost - 1);
            helper.assertTrue(almost.getDestroySpeed(stone) == 1F, "差一点不够一格时速度也应为 1");
        }
        float fast = ECEssentialTools.EssentialTier.ESSENTIAL.getSpeed();
        helper.assertTrue(pickaxe().getDestroySpeed(stone) == fast, "满电挖石头速度应为 " + fast + "，实际 " + pickaxe().getDestroySpeed(stone));
        helper.assertTrue(pickaxe().getDestroySpeed(Blocks.DIRT.defaultBlockState()) == 1F, "镐挖泥土速度应为 1");

        List<BlockPos> wall = plane(CENTER, Direction.Axis.Z);
        fill(helper, wall, Blocks.STONE);
        ServerPlayer player = holding(helper, empty);
        leftClick(helper, player, CENTER, Direction.NORTH);
        helper.assertTrue(mine(helper, player, CENTER), "没电也应挖得掉（只是慢）");
        helper.runAfterDelay(1, () -> {
            try {
                helper.assertBlockPresent(Blocks.AIR, CENTER);
                helper.assertTrue(count(helper, wall, Blocks.STONE) == 8, "付不起时不应连带");
                helper.assertTrue(mru(empty) == 0, "付不起时什么都不扣，剩 " + mru(empty));
            } finally {
                cleanup(helper, player, 5);
            }
            helper.succeed();
        });
    }

    /** 模拟领地拒绝墙上的一格：那格还在、只收到一次破坏事件（没被反复试）、不扣它的钱，其余照拆 */
    @GameTest(template = EMPTY)
    public static void pickaxeDeniedCellStaysFree(GameTestHelper helper) {
        List<BlockPos> wall = plane(CENTER, Direction.Axis.Z);
        fill(helper, wall, Blocks.STONE);
        BlockPos denied = new BlockPos(3, 2, 2);
        ItemStack tool = pickaxe();
        int full = mru(tool);
        ServerPlayer player = holding(helper, tool);
        ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(denied);
        try {
            leftClick(helper, player, CENTER, Direction.NORTH);
            helper.assertTrue(mine(helper, player, CENTER), "中心那格没被拒，应挖得掉");
        } catch (RuntimeException e) {
            claims.close();
            throw e;
        }
        helper.runAfterDelay(1, () -> {
            try {
                helper.assertBlockPresent(Blocks.STONE, denied);
                helper.assertTrue(count(helper, wall, Blocks.STONE) == 1, "只该剩被拒的那一格，实际 " + count(helper, wall, Blocks.STONE));
                helper.assertTrue(mru(tool) == full - cfg().mruPerBlock() * 8, "被拒的那格不应扣钱：应扣 8 格，剩 " + mru(tool));
                helper.assertTrue(claims.breaks(denied) == 1, "被拒的格子应只收到一次破坏事件，实际 " + claims.breaks(denied));
            } finally {
                claims.close();
                cleanup(helper, player, 5);
            }
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------
    // 源质斧
    // ------------------------------------------------------------------

    /**
     * 天然树（5 根树干 + 3 根树枝，树叶不是玩家放的）：砍树根以后整棵倒下，扣 8 根的钱；
     * 8 个原木掉落物都在树根那一格（树枝上的不落在树枝底下）
     */
    @GameTest(template = EMPTY_LARGE)
    public static void axeFellsNaturalTree(GameTestHelper helper) {
        List<BlockPos> logs = branchedTree(helper, 5, 3, false);
        ItemStack tool = axe();
        int full = mru(tool);
        ServerPlayer player = holding(helper, tool);
        helper.assertTrue(mine(helper, player, ROOT), "树根应砍得掉");
        helper.succeedWhen(() -> {
            helper.assertTrue(count(helper, logs, Blocks.OAK_LOG) == 0, "整棵树应砍光，还剩 " + count(helper, logs, Blocks.OAK_LOG));
            helper.assertFalse(EssentialToolEvents.isFelling(player.getUUID()), "砍完以后不应还有砍树任务");
            helper.assertTrue(mru(tool) == full - cfg().mruPerLog() * logs.size(), "应扣 " + logs.size() + " 根的钱，剩 " + mru(tool));
            int atRoot = droppedAt(helper, ROOT, Items.OAK_LOG);
            int anywhere = droppedAnywhere(helper, 11, Items.OAK_LOG);
            helper.assertTrue(anywhere == logs.size(), "应掉 " + logs.size() + " 个原木，实际 " + anywhere);
            helper.assertTrue(atRoot == anywhere, "原木掉落物应都在树根那一格，那里只有 " + atRoot + " / " + anywhere);
            cleanup(helper, player, 11);
        });
    }

    /** 同样的原木，树叶是玩家放的（persistent）：当成原木盖的东西，只砍你砍的那一根、只付那一根的钱 */
    @GameTest(template = EMPTY_LARGE)
    public static void axeRefusesHouse(GameTestHelper helper) {
        List<BlockPos> logs = branchedTree(helper, 5, 3, true);
        ItemStack tool = axe();
        int full = mru(tool);
        ServerPlayer player = holding(helper, tool);
        helper.assertTrue(mine(helper, player, ROOT), "那一根应砍得掉");
        helper.runAfterDelay(3, () -> {
            try {
                helper.assertBlockPresent(Blocks.AIR, ROOT);
                helper.assertTrue(count(helper, logs, Blocks.OAK_LOG) == logs.size() - 1, "只该砍掉一根，剩 " + count(helper, logs, Blocks.OAK_LOG));
                helper.assertFalse(EssentialToolEvents.isFelling(player.getUUID()), "不应建砍树任务");
                helper.assertTrue(mru(tool) == full - cfg().mruPerLog(), "只应付一根的钱，剩 " + mru(tool));
            } finally {
                cleanup(helper, player, 11);
            }
            helper.succeed();
        });
    }

    /** 5 × 5 × 6 = 150 根的实心原木块、顶上天然树叶：最多砍 {@code max_logs}（默认 128）根，付这么多根的钱，剩下的留着 */
    @GameTest(template = EMPTY_LARGE)
    public static void axeCap(GameTestHelper helper) {
        List<BlockPos> logs = logBlock(helper, 6);
        int expected = Math.min(logs.size(), cfg().maxLogs());
        ItemStack tool = axe();
        int full = mru(tool);
        ServerPlayer player = holding(helper, tool);
        helper.assertTrue(mine(helper, player, ROOT), "树根应砍得掉");
        helper.succeedWhen(() -> {
            helper.assertFalse(EssentialToolEvents.isFelling(player.getUUID()), "还在砍");
            int left = count(helper, logs, Blocks.OAK_LOG);
            helper.assertTrue(left == logs.size() - expected, "应砍掉 " + expected + " 根、剩 " + (logs.size() - expected) + "，实际剩 " + left);
            helper.assertTrue(mru(tool) == full - cfg().mruPerLog() * expected, "应扣 " + expected + " 根的钱，剩 " + mru(tool));
            cleanup(helper, player, 11);
        });
    }

    /**
     * 找树的上限含中心（直接拿不同的数值调 {@code planFelling}，返回的不含中心；测试不改配置）。8 根的天然树：
     * 不查树叶时上限 1 什么都不收、上限 3 收 2 根；照默认数值收 7 根；
     * 默认要查树叶、上限 3 时一根都不收：收进来的 3 根都在树干下半截，旁边没有树叶（收满上限时只看收进来的原木旁边，偏保守）
     */
    @GameTest(template = EMPTY_LARGE)
    public static void axeMaxLogsCountsCenter(GameTestHelper helper) {
        List<BlockPos> logs = branchedTree(helper, 5, 3, false);
        BlockPos root = helper.absolutePos(ROOT);
        ECEssentialTools.Config.Values base = cfg();
        try {
            int one = EssentialToolEvents.planFelling(helper.getLevel(), root, Blocks.OAK_LOG, base.withFelling(1, 0)).size();
            int three = EssentialToolEvents.planFelling(helper.getLevel(), root, Blocks.OAK_LOG, base.withFelling(3, 0)).size();
            int whole = EssentialToolEvents.planFelling(helper.getLevel(), root, Blocks.OAK_LOG, base).size();
            int cappedNatural = EssentialToolEvents.planFelling(helper.getLevel(), root, Blocks.OAK_LOG,
                    base.withFelling(3, Math.max(1, base.leavesRequired()))).size();
            helper.assertTrue(one == 0, "上限 1 只砍中心，实际除中心外收了 " + one + " 根");
            helper.assertTrue(three == 2, "上限 3 除中心外应收 2 根，实际 " + three);
            helper.assertTrue(base.maxLogs() < logs.size() || whole == logs.size() - 1, "照默认数值除中心外应收 " + (logs.size() - 1) + " 根，实际 " + whole);
            helper.assertTrue(cappedNatural == 0, "收满上限、收进来的原木旁边没有树叶时不应算天然树，实际收了 " + cappedNatural + " 根");
        } finally {
            clearArea(helper, 11);
        }
        helper.succeed();
    }

    /** 10 根的树：每根一份钱，身上的灵魂球够付时全从灵魂球扣，斧子自己的一点不动 */
    @GameTest(template = EMPTY_LARGE)
    public static void axePaysPerLog(GameTestHelper helper) {
        List<BlockPos> logs = branchedTree(helper, 6, 4, false);
        ItemStack tool = axe();
        int full = mru(tool);
        ServerPlayer player = holding(helper, tool);
        ItemStack sphere = new ItemStack(ECItems.SOUL_SPHERE.get());
        sphere.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(handler.getMaxMRU()));
        int sphereFull = mru(sphere);
        player.getInventory().setItem(9, sphere);
        helper.assertTrue(logs.size() == 10, "树应有 10 根");
        helper.assertTrue(sphereFull >= cfg().mruPerLog() * 10, "灵魂球应够付 10 根");
        helper.assertTrue(mine(helper, player, ROOT), "树根应砍得掉");
        helper.succeedWhen(() -> {
            helper.assertTrue(count(helper, logs, Blocks.OAK_LOG) == 0, "整棵树应砍光，还剩 " + count(helper, logs, Blocks.OAK_LOG));
            helper.assertFalse(EssentialToolEvents.isFelling(player.getUUID()), "还在砍");
            helper.assertTrue(mru(sphere) == sphereFull - cfg().mruPerLog() * 10, "应从灵魂球扣 10 根的钱，剩 " + mru(sphere));
            helper.assertTrue(mru(tool) == full, "储存物品够付时不应扣斧子自己的，剩 " + mru(tool));
            cleanup(helper, player, 11);
        });
    }

    /**
     * 5 × 5 × 2 = 50 根的原木块（一刻砍不完）：砍到一半把主手换成另一把满电的源质斧，这一刻末尾任务作废，剩下的不再砍，
     * 也不再扣钱
     */
    @GameTest(template = EMPTY_LARGE)
    public static void switchItemAborts(GameTestHelper helper) {
        List<BlockPos> logs = logBlock(helper, 2);
        ItemStack tool = axe();
        ServerPlayer player = holding(helper, tool);
        helper.assertTrue(mine(helper, player, ROOT), "树根应砍得掉");
        // 两步都在这里登记（GameTestInfo 边遍历边执行登记表，在回调里再登记会改到正在遍历的表）
        int[] atSwitch = new int[2];
        helper.runAfterDelay(1, () -> {
            atSwitch[0] = count(helper, logs, Blocks.OAK_LOG);
            atSwitch[1] = mru(tool);
            try {
                helper.assertTrue(EssentialToolEvents.isFelling(player.getUUID()), "第一刻末尾应建好砍树任务、还没砍完");
                helper.assertTrue(atSwitch[0] > 0 && atSwitch[0] < logs.size() - 1, "第一刻应已经砍了一部分、没砍完，剩 " + atSwitch[0]);
            } catch (RuntimeException e) {
                cleanup(helper, player, 11);
                throw e;
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, axe());
        });
        helper.runAfterDelay(5, () -> {
            try {
                helper.assertFalse(EssentialToolEvents.isFelling(player.getUUID()), "换手以后任务应作废");
                helper.assertTrue(count(helper, logs, Blocks.OAK_LOG) == atSwitch[0], "换手以后不应再砍：换手时剩 " + atSwitch[0] + "，现在剩 "
                        + count(helper, logs, Blocks.OAK_LOG));
                helper.assertTrue(mru(tool) == atSwitch[1], "换手以后不应再扣原来那把的钱");
            } finally {
                cleanup(helper, player, 11);
            }
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------
    // 物品本身
    // ------------------------------------------------------------------

    /**
     * 两件都没有耐久、能附魔（附魔能力 15），附不上耐久 / 经验修补，能附效率 / 时运 / 精准采集；创造栏是空 / 满两份；
     * 材料排在下界合金之后（黑曜石、远古残骸照常掉落）；紫水晶母岩默认不连带；两条配方已加载、分别吃元素镐 / 元素斧
     */
    @GameTest(template = EMPTY)
    public static void essentialToolsBasics(GameTestHelper helper) {
        for (ItemStack tool : List.of(pickaxe(), axe())) {
            String name = tool.getItem().toString();
            helper.assertFalse(tool.isDamageableItem(), name + " 不应有耐久");
            helper.assertTrue(tool.isEnchantable(), name + " 应能附魔");
            helper.assertTrue(tool.getEnchantmentValue() == 15, name + " 附魔能力应为 15，实际 " + tool.getEnchantmentValue());
            helper.assertFalse(Enchantments.UNBREAKING.canEnchant(tool), name + " 不应能附耐久");
            helper.assertFalse(Enchantments.MENDING.canEnchant(tool), name + " 不应能附经验修补");
            helper.assertTrue(Enchantments.BLOCK_EFFICIENCY.canApplyAtEnchantingTable(tool), name + " 附魔台应能给效率");
            helper.assertTrue(Enchantments.BLOCK_FORTUNE.canEnchant(tool), name + " 应能附时运");
            helper.assertTrue(Enchantments.SILK_TOUCH.canEnchant(tool), name + " 应能附精准采集");
            helper.assertTrue(mru(tool) == cfg().maxMru(), name + " 满电应存 " + cfg().maxMru() + "，实际 " + mru(tool));
            List<ItemStack> variants = ((CreativeVariants) tool.getItem()).creativeVariants();
            helper.assertTrue(variants.size() == 2 && mru(variants.get(0)) == 0 && mru(variants.get(1)) == cfg().maxMru(),
                    name + " 创造栏应是空 / 满两份");
        }
        ItemStack pick = pickaxe();
        for (Block block : List.of(Blocks.OBSIDIAN, Blocks.ANCIENT_DEBRIS, Blocks.DIAMOND_ORE, Blocks.STONE)) {
            helper.assertTrue(pick.isCorrectToolForDrops(block.defaultBlockState()), "源质镐挖 " + block + " 应照常掉落");
        }
        helper.assertTrue(axe().isCorrectToolForDrops(Blocks.OAK_LOG.defaultBlockState()), "源质斧应是砍原木的工具");
        helper.assertTrue(Blocks.BUDDING_AMETHYST.defaultBlockState().is(ECEssentialTools.NO_AREA),
                "紫水晶母岩默认在 essential_tools/no_area 里（挖下来什么都不掉，3×3 不连带它）");

        checkRecipe(helper, "essentialtools/essential_pickaxe", ECEssentialTools.ESSENTIAL_PICKAXE.get(), ECGear.ELEMENTAL_PICKAXE.get());
        checkRecipe(helper, "essentialtools/essential_axe", ECEssentialTools.ESSENTIAL_AXE.get(), ECGear.ELEMENTAL_AXE.get());
        helper.succeed();
    }

    private static void checkRecipe(GameTestHelper helper, String path, Item result, Item base) {
        Optional<? extends Recipe<?>> recipe = helper.getLevel().getRecipeManager().byKey(EssentialCraft5.id(path));
        helper.assertTrue(recipe.isPresent(), "配方 " + path + " 应已加载");
        helper.assertTrue(recipe.get().getResultItem(helper.getLevel().registryAccess()).is(result), path + " 应产出 " + result);
        boolean usesBase = false;
        for (Ingredient ingredient : recipe.get().getIngredients()) {
            usesBase |= ingredient.test(new ItemStack(base));
        }
        helper.assertTrue(usesBase, path + " 应吃一把 " + base);
    }
}
