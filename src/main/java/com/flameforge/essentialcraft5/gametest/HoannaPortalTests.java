package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.block.HoannaPortalBlock;
import com.flameforge.essentialcraft5.hoanna.portal.HoannaPortalPlacer;
import com.flameforge.essentialcraft5.hoanna.portal.HoannaPortalTracker;
import com.flameforge.essentialcraft5.hoanna.portal.HoannaTeleport;
import com.flameforge.essentialcraft5.hoanna.portal.HoannaTeleporter;
import com.flameforge.essentialcraft5.hoanna.portal.PortalOverlayCurve;
import com.flameforge.essentialcraft5.item.WindTabletItem;
import com.flameforge.essentialcraft5.matrix.MatrixRank;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.player.WindWishes;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHoannaPortal;
import com.flameforge.essentialcraft5.registry.ECWind;
import net.minecraft.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 第六批 6a M2「海纳尔之门」的 GameTest（batch6a.md §5.2 的测试点；风之石板「好感不够不换不扣」在 WindTests.windTabletNotAttunedNoCost）：
 * 方块与兴趣点；风之石板换整扇门（4 × 5 与 23 × 23）、被领地拒绝整扇不动不扣、下界里不行、海纳尔里补空框（含被拒）、门关着不换不传；
 * 拆一块框整扇塌、没有掉落；只有真玩家计时（掉落物、僵尸站 200 刻原地不动）；骑乘不计时（D9 A）；站在门里冷却不减、不低于 20；
 * 找门（100 格外找得到、130 格外找不到）；回主世界只找不建（落到出生点 / 重生点，重生维度不对不认）；海纳尔建门逐个候选点问领地、全拒就放弃；
 * 传送器永不返回 null；进门暗角曲线。6a 审查补（M2-1 / M2-2）：兜底落点先加载区块（没加载的一列不落进基岩）；计时到点在 END 里走到换维度那一步；
 * 真海纳尔世界里的往返（建门 B → 回程找回 A → 拆 A 后兜底）。
 * 落脚门选点（M2-3）：上有地表、下有洞的列选地表；四周全是水时建在水面平台上、不进海底下的洞，附近有露出水面的岛就先上岛。
 * 关掉开关（M2-4）：主世界这边不计时不传，海纳尔那边照常计时、能传回主世界、能补空框。
 * <p>
 * 写法：真玩家一律 {@link ProtectionFrameworkTests#realPlayer}（不在世界里、不在玩家列表里：门的计时直接调 {@code entityInside}，
 * 服务端不会给它做预加载，也不会真的换维度）；领地 {@link ProtectionFrameworkTests.Claims}（建门跨世界时用 {@code open(helper, level)} + 绝对坐标）。
 * 找门、建门、回程和大门放在离测试结构 {@value #FAR_FIND} ~ {@value #FAR_BIG} 格外的超平坦空地（各测试之间差 600 格，远大于找门半径 128，互不干扰），
 * 用完把门格拆掉（兴趣点跟着删）。「在海纳尔」的判断用 {@code ECHoanna.setTestHoannaOverride}，只在单刻的测试里开、finally 里清。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class HoannaPortalTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    /** 远处测试点离测试结构多远（沿 x） */
    private static final int FAR_FIND = 2600;
    private static final int FAR_BUILD = 3200;
    private static final int FAR_RETURN = 3800;
    private static final int FAR_BIG = 4400;
    /** 真海纳尔世界里的往返、计时（6a 审查 M2-2）：主世界和海纳尔都用这组坐标（两边同坐标），门摆在 y = {@value #GATE_Y} */
    private static final int FAR_ROUND_TRIP = 5000;
    private static final int FAR_TIMER = 5600;
    /** 落脚门选点（M2-3）：石台里掏洞、海底下有洞；关掉开关后回程（M2-4） */
    private static final int FAR_SURFACE = 6200;
    private static final int FAR_SEA = 6800;
    private static final int FAR_DISABLED = 7400;
    /** 从没加载过的一列（6a 审查 M2-1）：沿 x、z 各挪这么远 */
    private static final int FAR_UNLOADED = 20000;
    private static final int GATE_Y = 100;

    private HoannaPortalTests() {
    }

    // ================================================================ 工具

    /** 真玩家，风之好感和风之心愿（第十批 10b）刚好够让石板回应 */
    private static ServerPlayer attunedPlayer(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            throw new IllegalStateException("测试玩家应挂着玩家数据");
        }
        data.setWindPoints(Math.max(ECWind.Config.tabletPoints(), 1));
        data.setWindWishes(WindWishes.required());
        return player;
    }

    /** 满 MRU 的石板，放进主手 */
    private static ItemStack tablet(ServerPlayer player) {
        ItemStack tablet = new ItemStack(ECWind.WIND_TABLET.get());
        tablet.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(WindTabletItem.MAX_MRU));
        player.setItemInHand(InteractionHand.MAIN_HAND, tablet);
        return tablet;
    }

    private static int mru(ItemStack tablet) {
        return tablet.getCapability(ECCapabilities.MRU_ITEM).map(handler -> handler.getMRU()).orElse(-1);
    }

    private static int cost() {
        return ECWind.Config.tabletPortalMRU();
    }

    private static Direction along(Direction.Axis axis) {
        return axis == Direction.Axis.Z ? Direction.SOUTH : Direction.EAST;
    }

    /** 以 origin 为左下角、沿门宽方向 w 格、向上 h 格的内框 */
    private static List<BlockPos> interior(BlockPos origin, Direction.Axis axis, int w, int h) {
        List<BlockPos> cells = new ArrayList<>();
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                cells.add(origin.relative(along(axis), i).above(j));
            }
        }
        return cells;
    }

    /** 这个内框外面一圈的框 */
    private static List<BlockPos> frame(BlockPos origin, Direction.Axis axis, int w, int h) {
        List<BlockPos> cells = new ArrayList<>();
        for (int i = -1; i <= w; i++) {
            for (int j = -1; j <= h; j++) {
                if (i == -1 || i == w || j == -1 || j == h) {
                    cells.add(origin.relative(along(axis), i).above(j));
                }
            }
        }
        return cells;
    }

    /** 摆黑曜石框、把内框清成空气 */
    private static void buildFrame(ServerLevel level, BlockPos origin, Direction.Axis axis, int w, int h) {
        for (BlockPos cell : interior(origin, axis, w, h)) {
            level.setBlock(cell, Blocks.AIR.defaultBlockState(), 18);
        }
        for (BlockPos cell : frame(origin, axis, w, h)) {
            level.setBlock(cell, Blocks.OBSIDIAN.defaultBlockState(), 3);
        }
    }

    /** 照原版点燃（PortalShape.createPortalBlocks） */
    private static void lightNether(GameTestHelper helper, Level level, BlockPos origin, Direction.Axis axis) {
        boolean lit = PortalShape.findEmptyPortalShape(level, origin, axis).map(shape -> {
            shape.createPortalBlocks();
            return true;
        }).orElse(false);
        helper.assertTrue(lit, "点燃下界门 " + origin);
    }

    private static InteractionResult click(ServerPlayer player, Level level, BlockPos pos, Direction face, ItemStack stack) {
        return stack.useOn(new UseOnContext(level, player, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false)));
    }

    private static int count(Level level, Collection<BlockPos> cells, Predicate<BlockState> test) {
        int n = 0;
        for (BlockPos cell : cells) {
            if (test.test(level.getBlockState(cell))) {
                n++;
            }
        }
        return n;
    }

    private static Predicate<BlockState> stableHoanna(Direction.Axis axis) {
        return state -> state.getBlock() instanceof HoannaPortalBlock && state.getValue(HoannaPortalBlock.AXIS) == axis
                && state.getValue(HoannaPortalBlock.STABLE);
    }

    private static boolean hasPoi(ServerLevel level, BlockPos pos) {
        return level.getPoiManager().getType(pos).map(holder -> holder.is(ECHoannaPortal.PORTAL_POI)).orElse(false);
    }

    /** 拆成空气（flag 18：不连锁更新；门格的兴趣点跟着删） */
    private static void clear(Level level, Collection<BlockPos> cells) {
        for (BlockPos cell : cells) {
            level.setBlock(cell, Blocks.AIR.defaultBlockState(), 18);
        }
    }

    /** 远处的测试点：测试结构 (0, 1, 0) 沿 x 挪 dx 格 */
    private static BlockPos far(GameTestHelper helper, int dx) {
        return helper.absolutePos(new BlockPos(0, 1, 0)).offset(dx, 0, 0);
    }

    /** 以 base 为左下角的 2 × 3 X 朝向门的空间（外扩 0.5 格：落点贴着门边也算在门里） */
    private static AABB gateBox(BlockPos base) {
        return new AABB(base).minmax(new AABB(base.relative(Direction.EAST).above(2))).inflate(0.5D);
    }

    /** 拆掉以 base 为左下角的 X 朝向门（门格 + 框） */
    private static void clearGate(Level level, BlockPos base) {
        clear(level, HoannaPortalPlacer.portalCells(base, Direction.Axis.X));
        clear(level, HoannaPortalPlacer.frameCells(base, Direction.Axis.X));
    }

    /** 真海纳尔世界（GameTest 服里有，见 HoannaFrameworkTests.hoannaLevelInGameTest）；没有就让测试失败 */
    private static ServerLevel realHoanna(GameTestHelper helper) {
        ServerLevel hoanna = helper.getLevel().getServer().getLevel(ECHoanna.LEVEL);
        helper.assertTrue(hoanna != null, "GameTest 服里应有真海纳尔世界（见 HoannaFrameworkTests.hoannaLevelInGameTest）");
        return hoanna;
    }

    /** 数一块区域里的黑曜石和门格（看有没有「多出来的门」） */
    private static int gateBlocks(ServerLevel level, BlockPos center, int radius, int minY, int maxY) {
        int n = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockState state = level.getBlockState(cursor.set(x, y, z));
                    if (state.is(Blocks.OBSIDIAN) || state.is(Blocks.NETHER_PORTAL) || state.getBlock() instanceof HoannaPortalBlock) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    /** 把 from ~ to 的长方体填成 state（flag 18：不连锁更新；填的水同一刻里清掉就不会流） */
    private static void fill(ServerLevel level, BlockPos from, BlockPos to, BlockState state) {
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            level.setBlock(pos, state, 18);
        }
    }

    /** 一块长方体里的黑曜石和门格数（看建门动没动这一块） */
    private static int gateBlocksIn(ServerLevel level, BlockPos from, BlockPos to) {
        int n = 0;
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.OBSIDIAN) || state.getBlock() instanceof HoannaPortalBlock) {
                n++;
            }
        }
        return n;
    }

    // ================================================================ 方块

    /** 没有物品、挖不动、没有碰撞箱、亮度 11、不随机刻、流水冲不掉；兴趣点只认 stable=true（两个朝向都认），stable=false 不登记 */
    @GameTest(template = EMPTY)
    public static void hoannaPortalBlockBasics(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HoannaPortalBlock block = ECHoannaPortal.HOANNA_PORTAL.get();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        helper.assertTrue(!ForgeRegistries.ITEMS.containsKey(EssentialCraft5.id("hoanna_portal")) && block.asItem() == Items.AIR,
                "海纳尔传送门不该有方块物品");
        BlockState stableX = block.portalState(Direction.Axis.X, true);
        BlockState stableZ = block.portalState(Direction.Axis.Z, true);
        BlockState unstable = block.portalState(Direction.Axis.X, false);
        helper.assertTrue(block.defaultBlockState().getValue(HoannaPortalBlock.STABLE), "默认状态是 stable（/setblock 放的能被找到）");
        helper.assertTrue(stableX.getLightEmission() == 11, "亮度 11，实际 " + stableX.getLightEmission());
        helper.assertTrue(stableX.getDestroySpeed(level, pos) < 0F, "硬度 −1");
        helper.assertTrue(stableX.getCollisionShape(level, pos).isEmpty(), "没有碰撞箱");
        helper.assertTrue(!stableX.isRandomlyTicking(), "不开随机刻（不刷僵尸猪灵）");
        helper.assertTrue(stableX.isSolid(), "forceSolidOn：流水、倒桶冲不掉");
        helper.assertTrue(block.getCloneItemStack(level, pos, stableX).isEmpty(), "中键拿不到东西");
        helper.assertTrue(stableX.is(BlockTags.PORTALS) && unstable.is(BlockTags.PORTALS),
                "在 #minecraft:portals 里（在门里下坐骑时原地下来，同原版下界门；标签是生成的，先跑 runData）");
        helper.assertTrue(PoiTypes.forState(stableX).map(holder -> holder.is(ECHoannaPortal.PORTAL_POI)).orElse(false)
                && PoiTypes.forState(stableZ).map(holder -> holder.is(ECHoannaPortal.PORTAL_POI)).orElse(false), "stable 的两个朝向都登记了兴趣点");
        helper.assertTrue(PoiTypes.forState(unstable).isEmpty(), "stable=false 不登记兴趣点");
        helper.succeed();
    }

    /** 拆掉一块框：整扇门变空气（不掉东西、不是物品） */
    @GameTest(template = EMPTY_LARGE)
    public static void hoannaPortalCollapsesWithoutFrame(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(4, 2, 5));
        HoannaPortalPlacer.place(level, base, Direction.Axis.X, false, true);
        List<BlockPos> cells = HoannaPortalPlacer.portalCells(base, Direction.Axis.X);
        helper.assertTrue(count(level, cells, stableHoanna(Direction.Axis.X)) == 6, "摆好 2 × 3 门");
        level.setBlock(base.west(), Blocks.AIR.defaultBlockState(), 3);
        helper.assertTrue(count(level, cells, BlockState::isAir) == 6, "框缺一块，整扇门塌成空气");
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(base).inflate(4));
        helper.assertTrue(drops.isEmpty(), "没有掉落物：" + drops);
        clear(level, HoannaPortalPlacer.frameCells(base, Direction.Axis.X));
        helper.succeed();
    }

    // ================================================================ 风之石板

    /**
     * 主世界点燃的 4 × 5 门、23 × 23 门（内框 21 × 21 = 441 格）：整扇换成同一朝向的海纳尔之门、框还是黑曜石、没有空气、各扣一次 MRU；
     * 下一刻兴趣点查得到（stable=true 才登记）
     */
    @GameTest(template = EMPTY_LARGE, timeoutTicks = 60)
    public static void windTabletConvertsWholePortal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = attunedPlayer(helper);

        BlockPos small = helper.absolutePos(new BlockPos(4, 2, 5));
        buildFrame(level, small, Direction.Axis.X, 2, 3);
        lightNether(helper, level, small, Direction.Axis.X);
        ItemStack first = tablet(player);
        InteractionResult smallResult = click(player, level, small.above(), Direction.NORTH, first);
        List<BlockPos> smallCells = interior(small, Direction.Axis.X, 2, 3);
        helper.assertTrue(smallResult.consumesAction(), "换门成功应吃掉这次右键：" + smallResult);
        helper.assertTrue(count(level, smallCells, stableHoanna(Direction.Axis.X)) == 6, "6 格都是 X 朝向的海纳尔之门");
        helper.assertTrue(count(level, frame(small, Direction.Axis.X, 2, 3), state -> state.is(Blocks.OBSIDIAN)) == 14, "框不动");
        helper.assertTrue(mru(first) == WindTabletItem.MAX_MRU - cost(), "扣 " + cost() + " MRU，实际剩 " + mru(first));

        BlockPos big = far(helper, FAR_BIG).above(2);
        buildFrame(level, big, Direction.Axis.Z, 21, 21);
        lightNether(helper, level, big, Direction.Axis.Z);
        ItemStack second = tablet(player);
        InteractionResult bigResult = click(player, level, big.relative(Direction.SOUTH, 10).above(10), Direction.EAST, second);
        List<BlockPos> bigCells = interior(big, Direction.Axis.Z, 21, 21);
        helper.assertTrue(bigResult.consumesAction(), "大门换门成功：" + bigResult);
        helper.assertTrue(count(level, bigCells, stableHoanna(Direction.Axis.Z)) == 441,
                "441 格都是 Z 朝向的海纳尔之门，实际 " + count(level, bigCells, stableHoanna(Direction.Axis.Z)));
        helper.assertTrue(count(level, bigCells, BlockState::isAir) == 0, "没有塌出空气");
        helper.assertTrue(count(level, frame(big, Direction.Axis.Z, 21, 21), state -> state.is(Blocks.OBSIDIAN)) == 88, "大框不动");
        helper.assertTrue(mru(second) == WindTabletItem.MAX_MRU - cost(), "大门也只扣一次 " + cost());

        helper.runAtTickTime(2, () -> {
            helper.assertTrue(hasPoi(level, small) && hasPoi(level, smallCells.get(5)), "小门的兴趣点登记了");
            helper.assertTrue(hasPoi(level, big) && hasPoi(level, bigCells.get(bigCells.size() - 1)), "大门的兴趣点登记了");
            Optional<BlockUtil.FoundRectangle> found = HoannaPortalPlacer.find(level, small.above(4), 16);
            helper.assertTrue(found.isPresent() && found.get().minCorner.equals(small), "按兴趣点找得到小门：" + found.map(r -> r.minCorner));
            clear(level, bigCells);
            clear(level, frame(big, Direction.Axis.Z, 21, 21));
            clear(level, smallCells);
            helper.succeed();
        });
    }

    /** 内框里一格在别人领地里：整扇门还是下界门、没有空气、MRU 不变、返回 FAIL，放置事件只发了一次（整片一次多格放置） */
    @GameTest(template = EMPTY_LARGE)
    public static void windTabletDeniedNoChange(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = attunedPlayer(helper);
        BlockPos origin = helper.absolutePos(new BlockPos(4, 2, 5));
        buildFrame(level, origin, Direction.Axis.X, 2, 3);
        lightNether(helper, level, origin, Direction.Axis.X);
        ItemStack tablet = tablet(player);
        List<BlockPos> cells = interior(origin, Direction.Axis.X, 2, 3);
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(new BlockPos(5, 3, 5))) {
            InteractionResult result = click(player, level, origin, Direction.NORTH, tablet);
            helper.assertTrue(result == InteractionResult.FAIL, "被拒返回 FAIL：" + result);
            helper.assertTrue(count(level, cells, state -> state.is(Blocks.NETHER_PORTAL)) == 6, "整扇门还是下界门");
            helper.assertTrue(count(level, cells, BlockState::isAir) == 0, "还原时没有塌出空气");
            helper.assertTrue(mru(tablet) == WindTabletItem.MAX_MRU, "不扣 MRU，实际 " + mru(tablet));
            helper.assertTrue(claims.placeEvents() == 1 && claims.places(new BlockPos(5, 3, 5)) == 1,
                    "整片一次多格放置事件，实际 " + claims.placeEvents() + " 次");
        }
        clear(level, cells);
        helper.succeed();
    }

    /** 下界里点下界门：提示这里不行、返回 FAIL，门不变、不扣 MRU */
    @GameTest(template = EMPTY)
    public static void windTabletWrongDimension(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        if (nether == null) {
            helper.succeed();
            return;
        }
        ServerPlayer player = attunedPlayer(helper);
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO);
        BlockPos origin = new BlockPos(anchor.getX(), 100, anchor.getZ());
        buildFrame(nether, origin, Direction.Axis.X, 2, 3);
        lightNether(helper, nether, origin, Direction.Axis.X);
        ItemStack tablet = tablet(player);
        InteractionResult result = click(player, nether, origin, Direction.NORTH, tablet);
        List<BlockPos> cells = interior(origin, Direction.Axis.X, 2, 3);
        helper.assertTrue(result == InteractionResult.FAIL, "下界里不换：" + result);
        helper.assertTrue(count(nether, cells, state -> state.is(Blocks.NETHER_PORTAL)) == 6, "下界门不变");
        helper.assertTrue(mru(tablet) == WindTabletItem.MAX_MRU, "不扣 MRU");
        clear(nether, cells);
        clear(nether, frame(origin, Direction.Axis.X, 2, 3));
        helper.succeed();
    }

    /**
     * D10 A：海纳尔里点搭好的空框（框底的上表面）→ 填成海纳尔之门、扣 MRU；内框一格在别人领地里的那个框不动、不扣；
     * 不在海纳尔时点空框只提示先点燃、不动
     */
    @GameTest(template = EMPTY_LARGE)
    public static void windTabletFillsEmptyFrameInHoanna(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = attunedPlayer(helper);
        ItemStack tablet = tablet(player);
        BlockPos open = helper.absolutePos(new BlockPos(2, 2, 3));
        BlockPos claimed = helper.absolutePos(new BlockPos(6, 2, 7));
        buildFrame(level, open, Direction.Axis.X, 2, 3);
        buildFrame(level, claimed, Direction.Axis.X, 2, 3);
        List<BlockPos> openCells = interior(open, Direction.Axis.X, 2, 3);
        List<BlockPos> claimedCells = interior(claimed, Direction.Axis.X, 2, 3);
        ECHoanna.setTestHoannaOverride(level);
        try {
            try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(new BlockPos(7, 3, 7))) {
                InteractionResult denied = click(player, level, claimed.below(), Direction.UP, tablet);
                helper.assertTrue(denied == InteractionResult.FAIL, "被拒返回 FAIL：" + denied);
                helper.assertTrue(count(level, claimedCells, BlockState::isAir) == 6, "被拒的框里还是空气");
                helper.assertTrue(mru(tablet) == WindTabletItem.MAX_MRU, "被拒不扣 MRU");
                helper.assertTrue(claims.placeEvents() == 1, "一次多格放置事件");
            }
            InteractionResult filled = click(player, level, open.below(), Direction.UP, tablet);
            helper.assertTrue(filled.consumesAction(), "补门成功：" + filled);
            helper.assertTrue(count(level, openCells, stableHoanna(Direction.Axis.X)) == 6, "空框填成 X 朝向的海纳尔之门");
            helper.assertTrue(mru(tablet) == WindTabletItem.MAX_MRU - cost(), "扣 " + cost() + " MRU");
        } finally {
            ECHoanna.clearTestOverride();
        }
        InteractionResult overworld = click(player, level, claimed.below(), Direction.UP, tablet);
        helper.assertTrue(overworld == InteractionResult.FAIL, "主世界点空框：提示先点燃、返回 FAIL：" + overworld);
        helper.assertTrue(count(level, claimedCells, BlockState::isAir) == 6, "主世界的空框不会被填");
        helper.assertTrue(mru(tablet) == WindTabletItem.MAX_MRU - cost(), "没再扣");
        clear(level, openCells);
        helper.succeed();
    }

    /** 门关着（portal_enabled = false）时的主世界这边：石板不换、不扣；站进门不计时（海纳尔那边见 {@link #hoannaPortalDisabledStillReturns}） */
    @GameTest(template = EMPTY_LARGE)
    public static void hoannaPortalDisabledByConfig(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = attunedPlayer(helper);
        BlockPos origin = helper.absolutePos(new BlockPos(4, 2, 5));
        buildFrame(level, origin, Direction.Axis.X, 2, 3);
        lightNether(helper, level, origin, Direction.Axis.X);
        ItemStack tablet = tablet(player);
        List<BlockPos> cells = interior(origin, Direction.Axis.X, 2, 3);
        BlockState portal = ECHoannaPortal.HOANNA_PORTAL.get().portalState(Direction.Axis.X, true);
        ECHoannaPortal.Config.setEnabledForTest(false);
        try {
            InteractionResult result = click(player, level, origin, Direction.NORTH, tablet);
            helper.assertTrue(result == InteractionResult.FAIL, "门关着不换：" + result);
            helper.assertTrue(count(level, cells, state -> state.is(Blocks.NETHER_PORTAL)) == 6 && mru(tablet) == WindTabletItem.MAX_MRU,
                    "不改不扣");
            portal.entityInside(level, helper.absolutePos(new BlockPos(1, 1, 1)), player);
            helper.assertTrue(!HoannaPortalTracker.isTracked(player.getUUID()), "门关着不计时");
        } finally {
            ECHoannaPortal.Config.setEnabledForTest(null);
            HoannaPortalTracker.forget(player.getUUID());
        }
        clear(level, cells);
        helper.succeed();
    }

    /**
     * 门关着（portal_enabled = false）时海纳尔那边照常（M2-4）：关开关那一刻人在海纳尔的玩家不能被困住。
     * <ul>
     *   <li>主世界这边：站进门不计时，到点调 travel 也不传；</li>
     *   <li>真海纳尔世界里的玩家：站进门照常计时，travel 传回主世界（附近没门，按「只找不建」落到出生点）；</li>
     *   <li>海纳尔里拿石板点空框照样补成门（用 setTestHoannaOverride 把测试结构所在的主世界当海纳尔）。</li>
     * </ul>
     * 客户端的进门暗角和这里走同一个判断 {@code ECHoannaPortal.Config.portalEnabledIn}，一并断言。开关用测试覆盖值（不用 ConfigValue.set）。
     */
    @GameTest(template = EMPTY_LARGE)
    public static void hoannaPortalDisabledStillReturns(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel();
        ServerLevel hoanna = realHoanna(helper);
        BlockState portal = ECHoannaPortal.HOANNA_PORTAL.get().portalState(Direction.Axis.X, true);
        ServerPlayer home = ProtectionFrameworkTests.realPlayer(helper);
        ServerPlayer away = ProtectionFrameworkTests.realPlayer(helper);
        BlockPos anchor = far(helper, FAR_DISABLED);
        BlockPos gate = new BlockPos(anchor.getX(), GATE_Y, anchor.getZ());
        away.setServerLevel(hoanna);
        away.moveTo(gate.getX() + 1.0D, gate.getY(), gate.getZ() + 0.5D, 0F, 0F);
        BlockPos homeGate = helper.absolutePos(new BlockPos(1, 1, 1));
        List<ServerLevel> dests = new ArrayList<>();
        List<HoannaTeleport.Plan> plans = new ArrayList<>();
        HoannaTeleport.setTestDimensionChange(home.getUUID(), (moved, dest, plan) -> {
            dests.add(dest);
            return true;
        });
        HoannaTeleport.setTestDimensionChange(away.getUUID(), (moved, dest, plan) -> {
            dests.add(dest);
            plans.add(plan);
            return true;
        });

        ServerPlayer builder = attunedPlayer(helper);
        ItemStack tablet = tablet(builder);
        BlockPos emptyFrame = helper.absolutePos(new BlockPos(2, 2, 3));
        buildFrame(overworld, emptyFrame, Direction.Axis.X, 2, 3);
        List<BlockPos> frameCells = interior(emptyFrame, Direction.Axis.X, 2, 3);
        ECHoannaPortal.Config.setEnabledForTest(false);
        try {
            helper.assertTrue(!ECHoannaPortal.Config.portalEnabledIn(overworld) && ECHoannaPortal.Config.portalEnabledIn(hoanna),
                    "关掉后只挡主世界这头（客户端暗角也按这个判断）");

            portal.entityInside(overworld, homeGate, home);
            helper.assertTrue(!HoannaPortalTracker.isTracked(home.getUUID()), "关掉后主世界这边不计时");
            helper.assertTrue(!HoannaTeleport.travel(home, homeGate) && dests.isEmpty(), "关掉后主世界这边不传");

            portal.entityInside(hoanna, gate, away);
            helper.assertTrue(HoannaPortalTracker.count(away.getUUID()) == 1, "关掉后海纳尔那边照常计时，实际 " + HoannaPortalTracker.count(away.getUUID()));
            helper.assertTrue(HoannaTeleport.travel(away, gate), "关掉后海纳尔那边能回");
            helper.assertTrue(dests.size() == 1 && dests.get(0) == overworld, "回的是主世界：" + dests.stream().map(level -> level.dimension().location()).toList());
            helper.assertTrue(plans.get(0).fellBack() && plans.get(0).info().pos.y > overworld.getMinBuildHeight(),
                    "附近没门：只找不建、落到出生点 " + plans.get(0).info().pos);

            ECHoanna.setTestHoannaOverride(overworld);
            try {
                InteractionResult filled = click(builder, overworld, emptyFrame.below(), Direction.UP, tablet);
                helper.assertTrue(filled.consumesAction(), "关掉后海纳尔里照样能补空框：" + filled);
                helper.assertTrue(count(overworld, frameCells, stableHoanna(Direction.Axis.X)) == 6, "空框填成了海纳尔之门");
                helper.assertTrue(mru(tablet) == WindTabletItem.MAX_MRU - cost(), "照常扣 " + cost() + " MRU");
            } finally {
                ECHoanna.clearTestOverride();
            }
        } finally {
            ECHoannaPortal.Config.setEnabledForTest(null);
            HoannaTeleport.setTestDimensionChange(home.getUUID(), null);
            HoannaTeleport.setTestDimensionChange(away.getUUID(), null);
            HoannaPortalTracker.forget(home.getUUID());
            HoannaPortalTracker.forget(away.getUUID());
        }
        clear(overworld, frameCells);
        clear(overworld, frame(emptyFrame, Direction.Axis.X, 2, 3));
        helper.succeed();
    }

    // ================================================================ 过门

    /** 只有真玩家计时：掉落物、僵尸在门里 200 刻仍在原地、原世界；真玩家在门里每刻 +1（直接调 entityInside，不真换维度） */
    @GameTest(template = EMPTY_LARGE, timeoutTicks = 260)
    public static void hoannaPortalOnlyPlayers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(4, 2, 5));
        HoannaPortalPlacer.place(level, base, Direction.Axis.X, false, true);
        List<BlockPos> cells = HoannaPortalPlacer.portalCells(base, Direction.Axis.X);
        List<BlockPos> glass = new ArrayList<>();
        for (BlockPos cell : cells) {
            glass.add(cell.north());
            glass.add(cell.south());
        }
        for (BlockPos cell : glass) {
            level.setBlock(cell, Blocks.GLASS.defaultBlockState(), 3);
        }
        Zombie zombie = EntityType.ZOMBIE.create(level);
        helper.assertTrue(zombie != null, "建僵尸");
        zombie.moveTo(base.getX() + 1.0D, base.getY(), base.getZ() + 0.5D, 0F, 0F);
        zombie.setInvulnerable(true);
        zombie.setCanPickUpLoot(false);
        zombie.setPersistenceRequired();
        level.addFreshEntity(zombie);
        ItemEntity item = new ItemEntity(level, base.getX() + 0.5D, base.getY() + 0.1D, base.getZ() + 0.5D, new ItemStack(Items.DIAMOND));
        item.setDeltaMovement(Vec3.ZERO);
        item.setNeverPickUp();
        item.setUnlimitedLifetime();
        level.addFreshEntity(item);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        BlockState portal = level.getBlockState(base);
        for (int t = 10; t < 15; t++) {
            helper.runAtTickTime(t, () -> portal.entityInside(level, base, player));
        }
        AABB box = new AABB(base).minmax(new AABB(base.relative(Direction.EAST).above(2))).inflate(0.5D);
        helper.runAtTickTime(200, () -> {
            helper.assertTrue(!zombie.isRemoved() && zombie.level() == level && box.contains(zombie.position()),
                    "僵尸还在门里、没被传走：" + zombie.position() + " removed=" + zombie.isRemoved());
            helper.assertTrue(!item.isRemoved() && item.level() == level && box.contains(item.position()),
                    "掉落物还在门里、没被传走：" + item.position() + " removed=" + item.isRemoved());
            helper.assertTrue(!HoannaPortalTracker.isTracked(zombie.getUUID()) && !HoannaPortalTracker.isTracked(item.getUUID()), "生物、掉落物不计时");
            helper.assertTrue(HoannaPortalTracker.count(player.getUUID()) == 5, "真玩家 5 刻计 5，实际 " + HoannaPortalTracker.count(player.getUUID()));
            HoannaPortalTracker.forget(player.getUUID());
            zombie.discard();
            item.discard();
            clear(level, glass);
            clear(level, cells);
            helper.succeed();
        });
    }

    /** D9 A：骑着船站进门不计时；下来以后照常计时 */
    @GameTest(template = EMPTY)
    public static void hoannaRidingBlocked(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        BlockState portal = ECHoannaPortal.HOANNA_PORTAL.get().portalState(Direction.Axis.X, true);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        Boat boat = EntityType.BOAT.create(level);
        helper.assertTrue(boat != null, "建船");
        boat.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0F, 0F);
        level.addFreshEntity(boat);
        try {
            helper.assertTrue(player.startRiding(boat, true) && player.isPassenger(), "骑上船");
            portal.entityInside(level, pos, player);
            helper.assertTrue(!HoannaPortalTracker.isTracked(player.getUUID()), "骑着东西不计时");
            player.stopRiding();
            portal.entityInside(level, pos, player);
            helper.assertTrue(HoannaPortalTracker.count(player.getUUID()) == 1, "下来以后照常计时");
        } finally {
            HoannaPortalTracker.forget(player.getUUID());
            boat.discard();
        }
        helper.succeed();
    }

    /** 站在门里冷却不减（每刻按回进门时的值）、不低于 20，冷却中不计时；冷却完了才计时 */
    @GameTest(template = EMPTY, timeoutTicks = 20)
    public static void hoannaCooldownHeldWhileInside(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        UUID id = player.getUUID();
        BlockState portal = ECHoannaPortal.HOANNA_PORTAL.get().portalState(Direction.Axis.X, true);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        int[] seen = new int[4];
        player.setPortalCooldown(100);
        helper.runAtTickTime(1, () -> {
            portal.entityInside(level, pos, player);
            seen[0] = player.getPortalCooldown();
        });
        for (int t = 2; t <= 4; t++) {
            int index = t - 1;
            helper.runAtTickTime(t, () -> {
                // 模拟原版每刻减 1（真玩家的 baseTick 在 entityInside 之前减）
                player.setPortalCooldown(player.getPortalCooldown() - 1);
                portal.entityInside(level, pos, player);
                seen[index] = player.getPortalCooldown();
            });
        }
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(seen[0] == 100 && seen[1] == 100 && seen[2] == 100 && seen[3] == 100,
                    "站在门里冷却按住 100：" + seen[0] + "," + seen[1] + "," + seen[2] + "," + seen[3]);
            helper.assertTrue(HoannaPortalTracker.count(id) == 0, "冷却中不计时");
            HoannaPortalTracker.forget(id);
            player.setPortalCooldown(5);
            portal.entityInside(level, pos, player);
            helper.assertTrue(player.getPortalCooldown() == HoannaPortalTracker.MIN_HELD_COOLDOWN,
                    "快冷却完时站进门至少按住 20，实际 " + player.getPortalCooldown());
        });
        helper.runAtTickTime(6, () -> {
            HoannaPortalTracker.forget(id);
            player.setPortalCooldown(0);
            portal.entityInside(level, pos, player);
            helper.assertTrue(HoannaPortalTracker.count(id) == 1, "冷却完了开始计时");
            HoannaPortalTracker.forget(id);
            helper.succeed();
        });
    }

    /** 找门：100 格外的门找得到（按兴趣点，返回整扇门的矩形）；只有 130 格外的门时找不到（正方形半径 128） */
    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void hoannaPortalFindExisting(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos target = far(helper, FAR_FIND);
        BlockPos near = target.offset(100, 0, 0);
        BlockPos farther = target.offset(130, 0, 0);
        HoannaPortalPlacer.place(level, near, Direction.Axis.X, false, true);
        helper.runAtTickTime(2, () -> {
            Optional<BlockUtil.FoundRectangle> found = HoannaPortalPlacer.find(level, target, 128);
            helper.assertTrue(found.isPresent() && found.get().minCorner.equals(near) && found.get().axis1Size == 2 && found.get().axis2Size == 3,
                    "100 格外的门找得到：" + found.map(r -> r.minCorner + " " + r.axis1Size + "×" + r.axis2Size));
            clear(level, HoannaPortalPlacer.portalCells(near, Direction.Axis.X));
            clear(level, HoannaPortalPlacer.frameCells(near, Direction.Axis.X));
            HoannaPortalPlacer.place(level, farther, Direction.Axis.X, false, true);
        });
        helper.runAtTickTime(4, () -> {
            helper.assertTrue(HoannaPortalPlacer.find(level, target, 128).isEmpty(), "130 格外的门找不到");
            helper.assertTrue(HoannaPortalPlacer.find(level, target.offset(20, 0, 0), 128).isPresent(), "离它 110 格时又找得到（兴趣点确实登记了）");
            clear(level, HoannaPortalPlacer.portalCells(farther, Direction.Axis.X));
            clear(level, HoannaPortalPlacer.frameCells(farther, Direction.Axis.X));
            helper.succeed();
        });
    }

    /**
     * 回主世界、附近没有门：只找不建——落点是世界出生点；重生点记在别的维度（海纳尔）时不认；重生点是主世界的床时落在床边；
     * 目标周围 33 × 33 没有多出任何黑曜石或门格，也没发放置事件
     */
    @GameTest(template = EMPTY_LARGE)
    public static void hoannaReturnNeverBuilds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        BlockPos target = far(helper, FAR_RETURN);
        player.moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, 0F, 0F);
        BlockPos foot = helper.absolutePos(new BlockPos(5, 1, 5));
        BlockPos head = foot.north();
        level.setBlock(foot, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT), 18);
        level.setBlock(head, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD), 18);
        int minY = level.getMinBuildHeight();
        int before = gateBlocks(level, target, 16, minY, minY + 200);
        BlockPos spawn = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, level.getSharedSpawnPos());
        Vec3 spawnVec = new Vec3(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D);
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper, level)) {
            HoannaTeleport.Plan none = HoannaTeleport.plan(player, level, target, level, false);
            helper.assertTrue(none != null && none.fellBack() && none.portal() == null && none.info().pos.equals(spawnVec),
                    "没有重生点：落到世界出生点 " + spawnVec + "，实际 " + (none == null ? null : none.info().pos));

            player.setRespawnPosition(ECHoanna.LEVEL, head, 0F, false, false);
            HoannaTeleport.Plan otherDimension = HoannaTeleport.plan(player, level, target, level, false);
            helper.assertTrue(otherDimension != null && otherDimension.info().pos.equals(spawnVec),
                    "重生点在海纳尔：不拿到主世界用，落到出生点，实际 " + (otherDimension == null ? null : otherDimension.info().pos));

            player.setRespawnPosition(Level.OVERWORLD, head, 0F, false, false);
            HoannaTeleport.Plan bed = HoannaTeleport.plan(player, level, target, level, false);
            helper.assertTrue(bed != null && bed.fellBack() && bed.info().pos.distanceTo(Vec3.atCenterOf(head)) < 3D,
                    "重生点是主世界的床：落在床边，实际 " + (bed == null ? null : bed.info().pos));
            helper.assertTrue(claims.placeEvents() == 0, "回程不发放置事件");
        }
        int after = gateBlocks(level, target, 16, minY, minY + 200);
        helper.assertTrue(before == after, "回程不建门：目标周围黑曜石 / 门格 " + before + " → " + after);
        level.setBlock(head, Blocks.AIR.defaultBlockState(), 18);
        level.setBlock(foot, Blocks.AIR.defaultBlockState(), 18);
        helper.succeed();
    }

    /**
     * 海纳尔建门（在超平坦主世界上直接调建门，世界由参数给）：候选点 15 个普通点 + 1 个强制平台；
     * 一大块都是别人的领地 → 16 个候选点各问一次（各一次多格放置事件）、全被拒、返回空、世界不变；
     * 只拒最近那个候选点的门格 → 建在第一个不碰那格的候选点上
     */
    @GameTest(template = EMPTY, timeoutTicks = 20)
    public static void hoannaArrivalBuildRespectsClaims(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        BlockPos anchor = far(helper, FAR_BUILD);
        level.getChunkAt(anchor);
        BlockPos target = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, anchor);
        List<HoannaPortalPlacer.Candidate> candidates = HoannaPortalPlacer.candidates(level, target, Direction.Axis.X, 16);
        helper.assertTrue(candidates.size() == 16 && candidates.get(15).platform()
                        && candidates.stream().limit(15).noneMatch(HoannaPortalPlacer.Candidate::platform),
                "15 个普通点 + 最后一个强制平台，实际 " + candidates.size());
        boolean goodFirst = true;
        for (int i = 1; i < 15; i++) {
            if (candidates.get(i).good() && !candidates.get(i - 1).good()) {
                goodFirst = false;
            }
        }
        helper.assertTrue(goodFirst, "「好点」排在「次好点」前面");
        Set<BlockPos> touched = new HashSet<>();
        for (HoannaPortalPlacer.Candidate candidate : candidates) {
            touched.addAll(HoannaPortalPlacer.footprint(candidate, Direction.Axis.X));
        }
        int before = count(level, touched, state -> state.is(Blocks.OBSIDIAN) || state.getBlock() instanceof HoannaPortalBlock);

        BoundingBox everything = new BoundingBox(target.getX() - 24, level.getMinBuildHeight(), target.getZ() - 24,
                target.getX() + 24, level.getMaxBuildHeight() - 1, target.getZ() + 24);
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper, level).denyBox(everything)) {
            Optional<BlockUtil.FoundRectangle> none = HoannaPortalPlacer.build(player, level, target, Direction.Axis.X, 16);
            helper.assertTrue(none.isEmpty(), "全被拒：不建门");
            helper.assertTrue(claims.placeEvents() == 16, "16 个候选点各一次放置事件，实际 " + claims.placeEvents());
        }
        int afterDenied = count(level, touched, state -> state.is(Blocks.OBSIDIAN) || state.getBlock() instanceof HoannaPortalBlock);
        helper.assertTrue(afterDenied == before, "全被拒时世界不变：" + before + " → " + afterDenied);

        BlockPos denied = candidates.get(0).pos();
        HoannaPortalPlacer.Candidate expected = candidates.stream()
                .filter(candidate -> !HoannaPortalPlacer.footprint(candidate, Direction.Axis.X).contains(denied))
                .findFirst().orElseThrow();
        int expectedIndex = candidates.indexOf(expected);
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper, level).denyAt(denied)) {
            Optional<BlockUtil.FoundRectangle> built = HoannaPortalPlacer.build(player, level, target, Direction.Axis.X, 16);
            helper.assertTrue(built.isPresent() && built.get().minCorner.equals(expected.pos()),
                    "建在第 " + (expectedIndex + 1) + " 个候选点 " + expected.pos() + "，实际 " + built.map(r -> r.minCorner));
            helper.assertTrue(claims.placeEvents() == expectedIndex + 1, "前面被拒的各问一次，实际 " + claims.placeEvents());
            List<BlockPos> cells = HoannaPortalPlacer.portalCells(expected.pos(), Direction.Axis.X);
            helper.assertTrue(count(level, cells, stableHoanna(Direction.Axis.X)) == 6, "建好的是 stable 的 X 朝向门");
            helper.assertTrue(!(level.getBlockState(denied).getBlock() instanceof HoannaPortalBlock), "被拒的那格没有门");
            clear(level, cells);
            clear(level, HoannaPortalPlacer.frameCells(expected.pos(), Direction.Axis.X));
        }
        helper.succeed();
    }

    /**
     * 落脚门先挑露天的地表、再挑洞里（M2-3）：目标四周是一座 13 × 13 的石台（台面露天），台里掏了个 7 × 7 × 5 的洞，目标 y 就在洞底
     * （目标 y 取的是玩家在主世界的 y，和海纳尔的地形没有关系）。以前只按含 y 的距离排，洞底那个点距离 0，门建进洞里；
     * 现在第一个候选点是目标这一列的台面（露天那档按水平距离排），所有露天的点都排在洞里的点前面、洞里的点排在平台前面，
     * 建门建在台面上，洞里一格不动。
     */
    @GameTest(template = EMPTY, timeoutTicks = 20)
    public static void hoannaArrivalPrefersSurface(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        BlockPos anchor = far(helper, FAR_SURFACE);
        level.getChunkAt(anchor);
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, anchor.getX(), anchor.getZ());
        BlockPos center = new BlockPos(anchor.getX(), ground, anchor.getZ());
        BlockPos boxMin = center.offset(-6, 0, -6);
        BlockPos boxMax = center.offset(6, 23, 6);
        BlockPos caveMin = center.offset(-3, 10, -3);
        BlockPos caveMax = center.offset(3, 14, 3);
        fill(level, boxMin, boxMax, Blocks.STONE.defaultBlockState());
        fill(level, caveMin, caveMax, Blocks.AIR.defaultBlockState());
        BlockPos target = center.above(10);
        BlockPos top = center.above(24);
        try {
            List<HoannaPortalPlacer.Candidate> candidates = HoannaPortalPlacer.candidates(level, target, Direction.Axis.X, 16);
            HoannaPortalPlacer.Candidate first = candidates.get(0);
            helper.assertTrue(first.site() == HoannaPortalPlacer.Site.OPEN && first.pos().equals(top),
                    "第一个候选点是目标这一列的台面 " + top + "（不是洞底 " + target + "），实际 " + first.pos() + " " + first.site());

            // 全部候选点：露天 → 洞里 → 平台，档不交错；洞里确实放得下门（不然这个测试说明不了什么）
            List<HoannaPortalPlacer.Candidate> all = HoannaPortalPlacer.candidates(level, target, Direction.Axis.X, 4096);
            int lastOpen = -1;
            int firstCovered = -1;
            int covered = 0;
            for (int i = 0; i < all.size(); i++) {
                HoannaPortalPlacer.Site site = all.get(i).site();
                if (site == HoannaPortalPlacer.Site.OPEN) {
                    lastOpen = i;
                } else if (site == HoannaPortalPlacer.Site.COVERED) {
                    covered++;
                    if (firstCovered < 0) {
                        firstCovered = i;
                    }
                }
            }
            helper.assertTrue(covered > 0 && all.stream().anyMatch(candidate -> candidate.pos().equals(target)),
                    "洞底放得下门（洞里的候选点 " + covered + " 个，含目标点）");
            helper.assertTrue(lastOpen >= 0 && firstCovered > lastOpen, "露天的点都排在洞里的点前面：最后一个露天 #" + lastOpen + "，第一个洞里 #" + firstCovered);
            helper.assertTrue(all.get(all.size() - 1).site() == HoannaPortalPlacer.Site.PLATFORM, "平台排最后：" + all.get(all.size() - 1).site());

            Optional<BlockUtil.FoundRectangle> built = HoannaPortalPlacer.build(player, level, target, Direction.Axis.X, 16);
            helper.assertTrue(built.isPresent() && built.get().minCorner.equals(top), "建在台面上 " + top + "，实际 " + built.map(r -> r.minCorner));
            helper.assertTrue(count(level, HoannaPortalPlacer.portalCells(top, Direction.Axis.X), stableHoanna(Direction.Axis.X)) == 6, "台面上是 stable 的门");
            helper.assertTrue(gateBlocksIn(level, caveMin, caveMax) == 0, "洞里一格不动");
        } finally {
            clearGate(level, top);
            fill(level, boxMin, boxMax, Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }

    /**
     * 海上没有干地（M2-3，6a 往返实测的情形：目标在海纳尔之海上、半径 16 内没有干地，门建进海底下约 100 格的洞）：
     * 目标四周 ±17 格是 3 格深的水，海底一层石头，下面有个 9 × 9 × 5 的洞。水面上放不了框，露天一个点都没有，洞里放得下。
     * <ol>
     *   <li>第一个候选点是目标这一列的水面平台（地板铺在水面这一层，门立在地板上），后面才是洞里的点；
     *       建门：门脚下、前后是黑曜石地板（在水面这一层），门在水面上，洞里一格不动；</li>
     *   <li>拆掉门、恢复海面，在 9 格外堆一座露出水面的小岛：露天有点了，第一个候选点在岛上，水面平台退到最后一个。</li>
     * </ol>
     */
    @GameTest(template = EMPTY, timeoutTicks = 20)
    public static void hoannaArrivalOnWaterPlatform(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        BlockPos anchor = far(helper, FAR_SEA);
        level.getChunkAt(anchor);
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, anchor.getX(), anchor.getZ());
        BlockPos center = new BlockPos(anchor.getX(), ground, anchor.getZ());
        int r = 17;
        BlockPos rockMin = center.offset(-r, 0, -r);
        BlockPos seaFloorMax = center.offset(r, 5, r);
        BlockPos waterMin = center.offset(-r, 6, -r);
        BlockPos waterMax = center.offset(r, 8, r);
        BlockPos caveMin = center.offset(-4, 0, -4);
        BlockPos caveMax = center.offset(4, 4, 4);
        BlockPos islandMin = center.offset(8, 6, -2);
        BlockPos islandMax = center.offset(13, 9, 2);
        fill(level, rockMin, seaFloorMax, Blocks.STONE.defaultBlockState());
        fill(level, caveMin, caveMax, Blocks.AIR.defaultBlockState());
        fill(level, waterMin, waterMax, Blocks.WATER.defaultBlockState());
        // 目标 y 高出水面一截（玩家在主世界的 y），不影响水面平台的高度
        BlockPos target = center.above(30);
        BlockPos onWater = center.above(9);
        try {
            List<HoannaPortalPlacer.Candidate> candidates = HoannaPortalPlacer.candidates(level, target, Direction.Axis.X, 16);
            HoannaPortalPlacer.Candidate first = candidates.get(0);
            helper.assertTrue(first.site() == HoannaPortalPlacer.Site.WATER_PLATFORM && first.pos().equals(onWater),
                    "第一个候选点是水面平台 " + onWater + "，实际 " + first.pos() + " " + first.site());
            helper.assertTrue(candidates.size() == 16 && candidates.stream().skip(1).allMatch(c -> c.site() == HoannaPortalPlacer.Site.COVERED
                            && c.pos().getY() == ground),
                    "其后 15 个都是海底下洞里的点（露天一个都没有），实际 " + candidates.size() + " 个");

            Optional<BlockUtil.FoundRectangle> built = HoannaPortalPlacer.build(player, level, target, Direction.Axis.X, 16);
            helper.assertTrue(built.isPresent() && built.get().minCorner.equals(onWater),
                    "建在水面平台上 " + onWater + "，实际 " + built.map(rect -> rect.minCorner));
            helper.assertTrue(count(level, HoannaPortalPlacer.portalCells(onWater, Direction.Axis.X), stableHoanna(Direction.Axis.X)) == 6,
                    "水面上是 stable 的门");
            List<BlockPos> floor = new ArrayList<>();
            for (BlockPos cell : HoannaPortalPlacer.platformCells(onWater, Direction.Axis.X)) {
                if (cell.getY() == onWater.getY() - 1) {
                    floor.add(cell);
                }
            }
            helper.assertTrue(floor.size() == 6 && count(level, floor, state -> state.is(Blocks.OBSIDIAN)) == 6,
                    "门脚下和前后铺了黑曜石地板（水面这一层）");
            helper.assertTrue(floor.stream().allMatch(cell -> cell.getY() == ground + 8), "地板就在水面那一层 y = " + (ground + 8));
            BlockPos front = onWater.north();
            helper.assertTrue(level.getBlockState(front.below()).isSolid() && level.getBlockState(front).isAir() && level.getBlockState(front.above()).isAir(),
                    "门前能站：脚下实心、脚和头两格是空气");
            helper.assertTrue(gateBlocksIn(level, caveMin, caveMax) == 0, "海底下的洞里一格不动");

            // 2. 拆门、恢复海面，堆一座露出水面的小岛：先上岛，水面平台退到最后
            clearGate(level, onWater);
            clear(level, HoannaPortalPlacer.platformCells(onWater, Direction.Axis.X));
            fill(level, waterMin, waterMax, Blocks.WATER.defaultBlockState());
            fill(level, islandMin, islandMax, Blocks.STONE.defaultBlockState());
            BlockPos island = center.offset(9, 10, 0);
            List<HoannaPortalPlacer.Candidate> withIsland = HoannaPortalPlacer.candidates(level, target, Direction.Axis.X, 16);
            HoannaPortalPlacer.Candidate islandFirst = withIsland.get(0);
            HoannaPortalPlacer.Candidate last = withIsland.get(withIsland.size() - 1);
            helper.assertTrue(islandFirst.site() == HoannaPortalPlacer.Site.OPEN && islandFirst.pos().equals(island),
                    "有岛时第一个候选点在岛上 " + island + "，实际 " + islandFirst.pos() + " " + islandFirst.site());
            helper.assertTrue(last.site() == HoannaPortalPlacer.Site.WATER_PLATFORM && last.pos().equals(onWater)
                            && withIsland.stream().filter(c -> c.site() == HoannaPortalPlacer.Site.WATER_PLATFORM).count() == 1,
                    "水面平台只有一个、排最后：" + last.pos() + " " + last.site());
        } finally {
            clearGate(level, onWater);
            fill(level, rockMin, center.offset(r, 12, r), Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }

    /** 传送器只装算好的落点：永不返回 null、不是原版、放音效、不生成末地平台；null 落点构造时就拒绝；门关着时 travel 什么都不做 */
    @GameTest(template = EMPTY)
    public static void hoannaTeleporterNeverNull(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        PortalInfo info = new PortalInfo(new Vec3(1D, 2D, 3D), Vec3.ZERO, 10F, 20F);
        HoannaTeleporter teleporter = new HoannaTeleporter(info);
        helper.assertTrue(teleporter.getPortalInfo(player, level, dest -> null) == info, "返回算好的落点，不问原版");
        helper.assertTrue(!teleporter.isVanilla() && teleporter.playTeleportSound(player, level, level), "不是原版传送器、放传送音效");
        boolean[] spawnPortal = {true};
        Entity placed = teleporter.placeEntity(player, level, level, 0F, spawn -> {
            spawnPortal[0] = spawn;
            return player;
        });
        helper.assertTrue(placed == player && !spawnPortal[0], "走原版挪人逻辑、不生成末地平台");
        boolean threw = false;
        try {
            new HoannaTeleporter(null);
        } catch (NullPointerException e) {
            threw = true;
        }
        helper.assertTrue(threw, "落点为 null 时构造就报错");
        ECHoannaPortal.Config.setEnabledForTest(false);
        try {
            helper.assertTrue(!HoannaTeleport.travel(player, helper.absolutePos(BlockPos.ZERO)) && player.level() == level, "门关着不传送");
        } finally {
            ECHoannaPortal.Config.setEnabledForTest(null);
        }
        helper.succeed();
    }

    /**
     * 【6a 审查 M2-1】回主世界兜底到世界出生点时，先加载区块再读高度图：出生点区块不常驻时（混合端插件关 keepSpawnInMemory、Multiverse、
     * 去掉出生点区块的模组），原版 {@code Level.getHeight} 对没加载的区块返回最低高度，人会落进 y = −64 的基岩里。
     * GameTest 服的出生点区块是常驻的，所以直接拿一列从没加载过的远处坐标调 {@link HoannaTeleport#surfaceArrival}：
     * 落点高于最低高度、和加载后的高度图一致、站在这一列正中、脚下实心、脚和头两格没有碰撞箱。
     */
    @GameTest(template = EMPTY)
    public static void hoannaSurfaceArrivalLoadsChunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos xz = far(helper, FAR_UNLOADED).offset(0, 0, FAR_UNLOADED);
        int cx = SectionPos.blockToSectionCoord(xz.getX());
        int cz = SectionPos.blockToSectionCoord(xz.getZ());
        helper.assertTrue(!level.hasChunk(cx, cz), "前提：这一列所在的区块 (" + cx + ", " + cz + ") 还没加载");
        helper.assertTrue(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, xz.getX(), xz.getZ()) == level.getMinBuildHeight(),
                "前提（M2-1 的成因）：没加载时原版高度图返回最低高度");
        Vec3 arrival = HoannaTeleport.surfaceArrival(level, xz);
        BlockPos feet = BlockPos.containing(arrival);
        helper.assertTrue(arrival.y > level.getMinBuildHeight(), "落点高于最低高度 " + level.getMinBuildHeight() + "，实际 " + arrival);
        helper.assertTrue(arrival.x == xz.getX() + 0.5D && arrival.z == xz.getZ() + 0.5D, "站在这一列正中：" + arrival);
        helper.assertTrue(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, xz.getX(), xz.getZ()) == feet.getY(),
                "和加载后的高度图一致：" + level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, xz.getX(), xz.getZ()) + " / " + feet.getY());
        helper.assertTrue(level.getBlockState(feet.below()).isSolid(), "脚下实心：" + level.getBlockState(feet.below()));
        helper.assertTrue(level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty(), "脚、头两格没有碰撞箱");
        helper.succeed();
    }

    /**
     * 【6a 审查 M2-2】计时到点真的走到换维度那一步（以前没有任何测试跑过这条路）：真玩家站在主世界的门 A 里，每刻先调门的 entityInside、
     * 再发 PlayerTickEvent END（同真玩家一刻里的顺序：aiStep 里 move 碰到门在前，刻末 END 在后）；先连站几刻、空一刻（计数 −4）、再站到计满。
     * 每刻的计数都要和原版式的模型一致（在门里 +1、封顶等待时间；不在门里 −4、≤ 0 摘掉）；计满那一刻的 END 里调 travel →
     * 在真海纳尔世界找到事先摆好的门 B（找到了就不建）→ 设冷却 → 换维度（测试玩家不在世界里、不能真换，这一步由
     * {@link HoannaTeleport#setTestDimensionChange} 接住）；只传一次；之后站在门里冷却按住、不再计时。
     */
    @GameTest(template = EMPTY, timeoutTicks = 460)
    public static void hoannaPortalTimerTravels(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel();
        ServerLevel hoanna = realHoanna(helper);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        // 矩阵设成九阶，不被海纳尔的阶门挡住（阶门另见 MatrixFrameworkTests.matrixGateHoannaPortalTravel）
        PlayerDataEvents.get(player).setMatrixRank(MatrixRank.MAX);
        UUID id = player.getUUID();
        BlockPos anchor = far(helper, FAR_TIMER);
        BlockPos base = new BlockPos(anchor.getX(), GATE_Y, anchor.getZ());
        HoannaPortalPlacer.place(overworld, base, Direction.Axis.X, false, true);
        HoannaPortalPlacer.place(hoanna, base, Direction.Axis.X, false, true);
        BlockState portal = overworld.getBlockState(base);
        player.moveTo(base.getX() + 1.0D, base.getY(), base.getZ() + 0.5D, 0F, 0F);
        player.setPortalCooldown(0);
        int wait = HoannaPortalTracker.waitTicks(player);
        int cooldown = ECHoannaPortal.Config.cooldownTicks();

        // 日程：第 1 ~ first 刻站在门里（不到点），第 gap 刻不在门里，之后一直站着；先按模型算出每刻 END 之后的计数（−1 = 不在表里）和到点那一刻
        int first = Math.min(10, wait - 1);
        int gap = first + 1;
        int[] expected = new int[wait + first + 8];
        int modelCount = 0;
        boolean modelTracked = false;
        int trigger = -1;
        for (int t = 1; trigger < 0; t++) {
            boolean in = t != gap;
            if (in) {
                modelCount = Math.min(modelCount + 1, wait);
                modelTracked = true;
            }
            if (modelTracked) {
                if (!in) {
                    modelCount -= HoannaPortalTracker.DECAY_PER_TICK;
                    if (modelCount <= 0) {
                        modelTracked = false;
                        modelCount = 0;
                    }
                } else if (modelCount >= wait) {
                    trigger = t;
                    modelTracked = false;
                    modelCount = 0;
                }
            }
            expected[t] = modelTracked ? modelCount : -1;
        }
        int triggerTick = trigger;

        List<HoannaTeleport.Plan> plans = new ArrayList<>();
        List<ServerLevel> dests = new ArrayList<>();
        HoannaTeleport.setTestDimensionChange(id, (moved, dest, plan) -> {
            plans.add(plan);
            dests.add(dest);
            return true;
        });
        for (int t = 1; t <= triggerTick + 1; t++) {
            int tick = t;
            helper.runAtTickTime(tick, () -> {
                if (tick != gap) {
                    portal.entityInside(overworld, base, player);
                }
                HoannaPortalTracker.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
                if (tick < triggerTick) {
                    int actual = HoannaPortalTracker.isTracked(id) ? HoannaPortalTracker.count(id) : -1;
                    helper.assertTrue(plans.isEmpty(), "第 " + tick + " 刻还没到点（到点在第 " + triggerTick + " 刻）就传了");
                    helper.assertTrue(actual == expected[tick], "第 " + tick + " 刻计数应为 " + expected[tick] + "，实际 " + actual
                            + "（等待 " + wait + " 刻，第 " + gap + " 刻不在门里）");
                } else if (tick == triggerTick) {
                    helper.assertTrue(plans.size() == 1, "计满的那一刻 END 里过门一次，实际 " + plans.size() + " 次");
                    HoannaTeleport.Plan plan = plans.get(0);
                    helper.assertTrue(dests.get(0) == hoanna, "从主世界去的是真海纳尔世界：" + dests.get(0).dimension().location());
                    helper.assertTrue(plan.portal() != null && plan.portal().minCorner.equals(base) && !plan.fellBack(),
                            "找到事先摆好的门 B（不建新门）：" + (plan.portal() == null ? null : plan.portal().minCorner));
                    helper.assertTrue(gateBox(base).contains(plan.info().pos), "落点在 B 里：" + plan.info().pos);
                    helper.assertTrue(player.getPortalCooldown() == cooldown, "换维度之前设好冷却 " + cooldown + "，实际 " + player.getPortalCooldown());
                    helper.assertTrue(!HoannaPortalTracker.isTracked(id), "传走以后从表里摘掉");
                } else {
                    helper.assertTrue(plans.size() == 1, "冷却中站在门里不再传，实际共 " + plans.size() + " 次");
                    helper.assertTrue(HoannaPortalTracker.count(id) == 0 && player.getPortalCooldown() == cooldown,
                            "冷却中不计时、冷却按住：计数 " + HoannaPortalTracker.count(id) + "，冷却 " + player.getPortalCooldown());
                    HoannaPortalTracker.forget(id);
                    HoannaTeleport.setTestDimensionChange(id, null);
                    clearGate(overworld, base);
                    clearGate(hoanna, base);
                    helper.succeed();
                }
            });
        }
    }

    /**
     * 【6a 审查 M2-2】真海纳尔世界里的一趟往返（以前找门 / 建门只在超平坦主世界上、把同一个世界当两边调过）：
     * <ol>
     *   <li>主世界 y = {@value #GATE_Y} 摆一扇 stable 的门 A，玩家站进去；</li>
     *   <li>plan（去海纳尔、找不到就建）：真海纳尔里建出 stable 的 X 朝向门 B（目标 ±16 格内），落点在 B 里；下一刻 B 的兴趣点查得到；</li>
     *   <li>玩家站进 B，plan（回主世界、只找不建）：找回的就是 A 的矩形、落点在 A 里，不发放置事件，主世界那一片没多出方块；</li>
     *   <li>拆掉 A 再回：落到出生点（fellBack），落点高于最低高度，主世界那一片一块门 / 黑曜石都没有；</li>
     *   <li>收尾拆掉 B。</li>
     * </ol>
     * 换维度本身要真玩家，GameTest 做不了：计时走到换维度那一步见 {@link #hoannaPortalTimerTravels}，混合端上的真换维度见混合端自检 C21 / C22。
     * 出生点区块没加载时的兜底落点见 {@link #hoannaSurfaceArrivalLoadsChunk}（这里出生点区块常驻，测不出 M2-1）。
     */
    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void hoannaRoundTripRealWorld(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel();
        ServerLevel hoanna = realHoanna(helper);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        BlockPos anchor = far(helper, FAR_ROUND_TRIP);
        BlockPos a = new BlockPos(anchor.getX(), GATE_Y, anchor.getZ());
        HoannaPortalPlacer.place(overworld, a, Direction.Axis.X, false, true);
        player.moveTo(a.getX() + 1.0D, a.getY(), a.getZ() + 0.5D, 0F, 0F);
        HoannaTeleport.Plan out = HoannaTeleport.plan(player, overworld, a, hoanna, true);
        helper.assertTrue(out != null && !out.fellBack() && out.portal() != null,
                "去海纳尔：找不到门就建一扇，实际 " + (out == null ? "null（全部候选点被拒）" : out.portal()));
        BlockPos b = out.portal().minCorner;
        helper.assertTrue(count(hoanna, HoannaPortalPlacer.portalCells(b, Direction.Axis.X), stableHoanna(Direction.Axis.X)) == 6,
                "真海纳尔里的门 B " + b + " 是 stable 的 X 朝向门");
        helper.assertTrue(Math.abs(b.getX() - a.getX()) <= HoannaPortalPlacer.CREATE_RADIUS + 1
                && Math.abs(b.getZ() - a.getZ()) <= HoannaPortalPlacer.CREATE_RADIUS + 1, "B 在目标 ±16 格内：A " + a + "，B " + b);
        helper.assertTrue(gateBox(b).contains(out.info().pos), "落点在 B 里：" + out.info().pos + "，B " + b);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(hasPoi(hoanna, b), "B 的兴趣点登记了（stable 才登记，下一刻生效）");
            player.moveTo(b.getX() + 1.0D, b.getY(), b.getZ() + 0.5D, 0F, 0F);
            int minY = Math.min(a.getY(), b.getY()) - 8;
            int maxY = Math.max(a.getY(), b.getY()) + 8;
            int before = gateBlocks(overworld, b, 20, minY, maxY);
            try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper, overworld)) {
                HoannaTeleport.Plan back = HoannaTeleport.plan(player, hoanna, b, overworld, false);
                helper.assertTrue(back != null && !back.fellBack() && back.portal() != null && back.portal().minCorner.equals(a)
                                && back.portal().axis1Size == 2 && back.portal().axis2Size == 3,
                        "回程找回的是 A 的矩形 " + a + "，实际 " + (back == null || back.portal() == null ? null : back.portal().minCorner));
                helper.assertTrue(gateBox(a).contains(back.info().pos), "回程落点在 A 里：" + back.info().pos);
                helper.assertTrue(claims.placeEvents() == 0, "回程不发放置事件");
            }
            helper.assertTrue(gateBlocks(overworld, b, 20, minY, maxY) == before, "回程主世界没多出方块");

            clearGate(overworld, a);
            HoannaTeleport.Plan fallback = HoannaTeleport.plan(player, hoanna, b, overworld, false);
            helper.assertTrue(fallback != null && fallback.fellBack() && fallback.portal() == null,
                    "拆掉 A 以后回程落到出生点，实际 " + (fallback == null ? null : fallback.portal()));
            helper.assertTrue(fallback.info().pos.y > overworld.getMinBuildHeight(), "兜底落点高于最低高度：" + fallback.info().pos);
            helper.assertTrue(gateBlocks(overworld, b, 20, minY, maxY) == 0, "拆掉 A 以后回程也不建门：主世界那一片一块门 / 黑曜石都没有");
            clearGate(hoanna, b);
            helper.succeed();
        });
    }

    /** 进门暗角：等到一半 = 0.20 × 屏幕扭曲效果、等满 0.40 封顶、离开 0.5 秒（10 刻）单调淡到 0、创造模式一刻至多 0.02、缩放 0 不画 */
    @GameTest(template = EMPTY)
    public static void hoannaPortalOverlayPureMath(GameTestHelper helper) {
        float strength = 0F;
        for (int t = 1; t <= 40; t++) {
            strength = PortalOverlayCurve.step(strength, PortalOverlayCurve.progress(t, 80), 80);
        }
        helper.assertTrue(Math.abs(PortalOverlayCurve.alpha(strength, 1D) - 0.20F) < 1.0E-4F, "等 40 / 80 刻：0.20，实际 " + PortalOverlayCurve.alpha(strength, 1D));
        helper.assertTrue(Math.abs(PortalOverlayCurve.alpha(strength, 0.5D) - 0.10F) < 1.0E-4F, "屏幕扭曲效果 50%：0.10");
        helper.assertTrue(PortalOverlayCurve.alpha(strength, 0D) == 0F, "屏幕扭曲效果 0：不画");
        for (int t = 41; t <= 200; t++) {
            strength = PortalOverlayCurve.step(strength, PortalOverlayCurve.progress(t, 80), 80);
            helper.assertTrue(PortalOverlayCurve.alpha(strength, 1D) <= PortalOverlayCurve.MAX_ALPHA + 1.0E-6F, "封顶 0.40");
        }
        helper.assertTrue(Math.abs(PortalOverlayCurve.alpha(strength, 1D) - 0.40F) < 1.0E-4F, "等满 0.40");
        float previous = strength;
        for (int t = 1; t <= PortalOverlayCurve.FADE_OUT_TICKS; t++) {
            strength = PortalOverlayCurve.step(strength, 0F, 80);
            helper.assertTrue(strength <= previous, "淡出单调不增");
            previous = strength;
        }
        helper.assertTrue(strength == 0F, "离开 10 刻（0.5 秒）淡到 0，实际 " + strength);
        float creative = PortalOverlayCurve.step(0F, PortalOverlayCurve.progress(1, 1), 1);
        helper.assertTrue(PortalOverlayCurve.alpha(creative, 1D) <= 0.0201F, "创造模式（等 1 刻）一刻至多 0.02，不一下子亮起来");
        helper.succeed();
    }
}
