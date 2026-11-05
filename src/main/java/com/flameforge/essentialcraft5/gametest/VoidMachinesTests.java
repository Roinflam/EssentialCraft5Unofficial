package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.DimensionalTransceiverBlock;
import com.flameforge.essentialcraft5.block.DyeableBlock;
import com.flameforge.essentialcraft5.blockentity.DimensionalTransceiverBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MRUBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MagicalFurnaceBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MagicalTeleporterBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MagicalTeleporterBlockEntity.Status;
import com.flameforge.essentialcraft5.item.BoundGemItem;
import com.flameforge.essentialcraft5.item.creative.CreativeOnly;
import com.flameforge.essentialcraft5.matrix.MatrixRank;
import com.flameforge.essentialcraft5.mru.MRULink;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECBlocks;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHoannaNature;
import com.flameforge.essentialcraft5.registry.ECHoannaPortal;
import com.flameforge.essentialcraft5.registry.ECItems;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.registry.ECVoidMachines;
import com.flameforge.essentialcraft5.structure.StructurePattern;
import com.flameforge.essentialcraft5.teleport.ECTeleport;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

/**
 * 第六批 6b M2「传送与熔炼」的 GameTest（batch6b.md §4.2 的测试点）：魔力传送器、魔力熔炉、MRU 跨维度收发器。
 * <ul>
 *   <li>站上去的人：测试玩家 {@link ProtectionFrameworkTests#realPlayer} 不在世界实体表里，实体查询找不到它，
 *       所以先挪到机器顶上，再用 {@link MagicalTeleporterBlockEntity#setTestOccupant} 登记成「这台上面的人」（规则照查）。</li>
 *   <li>同维度真传送（{@code teleportTo} 走假连接的 {@code connection.teleport}，位置会真的变）；跨维度用真海纳尔世界
 *       （GameTest 服里有），换维度那一步由 {@link ECTeleport#setTestDimensionChange} 接住（测试玩家不能真换维度），
 *       只有「被 EntityTravelToDimensionEvent 取消」那条真调 {@code changeDimension}（事件在任何改动之前就取消返回）。
 *       跨维度的放单独 batch {@value #DIM_BATCH}，海纳尔那边的方块放在 y = {@value #HOANNA_Y}（地形上面的空中），用完拆掉。</li>
 *   <li>魔力熔炉的配方缓存各台共用：换配方表的那条放单独 batch {@value #RECIPE_BATCH}，免得半路清掉缓存、打乱同批「每刻查几次」的计数。</li>
 *   <li>领地用 {@link ProtectionFrameworkTests.Claims}；配置一律用方块实体自己的测试覆盖值，海纳尔开关用 {@code ECHoannaPortal.Config.setEnabledForTest}（同一段同步代码里设、测、置回）。</li>
 * </ul>
 * 大结构用模板 empty_large（11×8×11）：传送器在 (5, 1, 5)、落点方块在 (9, 1, 9)（三轴距离和 8 → 同维度费用 (int)(500 × 8 / 3) = 1333）；
 * 魔力熔炉在 (5, 2, 5)（下一层虚空石在 y = 1，四角热力产能器在 y = 3，产物落在 y = 4）。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class VoidMachinesTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    private static final String DIM_BATCH = "void_machines_dim";
    private static final String RECIPE_BATCH = "void_machines_recipe";
    private static final int HOANNA_Y = 300;

    private static final BlockPos TELEPORTER = new BlockPos(5, 1, 5);
    private static final BlockPos FLOOR = new BlockPos(9, 1, 9);
    private static final int SAME_DIM_COST = 1333;
    private static final BlockPos FURNACE = new BlockPos(5, 2, 5);

    private VoidMachinesTests() {
    }

    // ================================================================ 工具

    /** 照结构表摆出全部必需格子（虚空石、护板随便染几种颜色，证明颜色不影响），返回机器那一格的方块实体 */
    private static BlockEntity build(GameTestHelper helper, StructurePattern pattern, BlockPos anchor) {
        int index = 0;
        for (Map.Entry<BlockPos, StructurePattern.Rule> entry : pattern.rules().entrySet()) {
            if (entry.getValue().isOptional()) {
                continue;
            }
            BlockState state = entry.getValue().displayState();
            if (state.hasProperty(DyeableBlock.COLOR)) {
                state = state.setValue(DyeableBlock.COLOR, DyeColor.values()[index++ % DyeColor.values().length]);
            }
            helper.setBlock(anchor.offset(entry.getKey()), state);
        }
        return helper.getBlockEntity(anchor);
    }

    /** 摆好魔力传送器的结构并立即判定一次 */
    private static MagicalTeleporterBlockEntity buildTeleporter(GameTestHelper helper) {
        MagicalTeleporterBlockEntity be = (MagicalTeleporterBlockEntity) build(helper, ECVoidMachines.TELEPORTER_STRUCTURE, TELEPORTER);
        be.structure().checkNow(helper.getLevel(), be.getBlockPos(), Direction.NORTH);
        return be;
    }

    private static MagicalFurnaceBlockEntity buildFurnace(GameTestHelper helper) {
        MagicalFurnaceBlockEntity be = (MagicalFurnaceBlockEntity) build(helper, ECVoidMachines.FURNACE_STRUCTURE, FURNACE);
        be.structure().checkNow(helper.getLevel(), be.getBlockPos(), Direction.NORTH);
        return be;
    }

    private static ItemStack gem(GlobalPos target) {
        ItemStack stack = new ItemStack(ECItems.BOUND_GEM.get());
        BoundGemItem.bind(stack, target, false);
        return stack;
    }

    private static GlobalPos here(GameTestHelper helper, BlockPos relative) {
        return GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(relative));
    }

    /** 传送器：落点宝石指向 target，MRU 设成 mru */
    private static void aim(MagicalTeleporterBlockEntity be, GlobalPos target, int mru) {
        be.getInventory().setStackInSlot(MagicalTeleporterBlockEntity.SLOT_DESTINATION, gem(target));
        be.getMRUStorage().setMRU(mru);
    }

    /** 把玩家挪到传送器正上方并登记成站在上面的人 */
    private static void standOn(GameTestHelper helper, MagicalTeleporterBlockEntity be, ServerPlayer player) {
        BlockPos pos = be.getBlockPos();
        player.moveTo(pos.getX() + 0.5D, pos.getY() + 1D, pos.getZ() + 0.5D, 0F, 0F);
        MagicalTeleporterBlockEntity.setTestOccupant(helper.getLevel(), pos, player);
    }

    /** 新建一名真玩家站上去；矩阵设成九阶，跨维度的测试不被海纳尔的阶门挡住（阶门由 {@link #teleporterRankGate} 单独测） */
    private static ServerPlayer standOn(GameTestHelper helper, MagicalTeleporterBlockEntity be) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        PlayerDataEvents.get(player).setMatrixRank(MatrixRank.MAX);
        standOn(helper, be, player);
        return player;
    }

    private static void stepOff(GameTestHelper helper, MagicalTeleporterBlockEntity be) {
        MagicalTeleporterBlockEntity.setTestOccupant(helper.getLevel(), be.getBlockPos(), null);
    }

    /** 站在传送器上的人还在原地（脚在机器正上方那格） */
    private static boolean stillOnTop(ServerPlayer player, MagicalTeleporterBlockEntity be) {
        return player.blockPosition().equals(be.getBlockPos().above());
    }

    /** {@link #paceRealTime} 上一次等完的时刻（System.nanoTime） */
    private static long lastPacedTick;

    /**
     * 测试期间让服务端每刻至少隔 50 ms（正常节奏）。GameTest 服不等下一刻，空转时一秒能跑一百多刻，
     * 「几百刻内目的地区块生成完」这类按正常节奏写的前提要看区块线程跟不跟得上：这一批排在刚开服时
     * （批次顺序按批次名的哈希，加了别的批次就会挪），区块线程还冷，250 刻只有两三秒，生成赶不上。
     * 同一刻里几条测试都调也只等一次。
     */
    private static void paceRealTime(GameTestHelper helper) {
        helper.onEachTick(() -> {
            long wait = lastPacedTick + 50_000_000L - System.nanoTime();
            if (wait > 0L) {
                LockSupport.parkNanos(wait);
            }
            lastPacedTick = System.nanoTime();
        });
    }

    private static ServerLevel realHoanna(GameTestHelper helper) {
        ServerLevel hoanna = helper.getLevel().getServer().getLevel(ECHoanna.LEVEL);
        helper.assertTrue(hoanna != null, "GameTest 服里应有真海纳尔世界（见 HoannaFrameworkTests.hoannaLevelInGameTest）");
        return hoanna;
    }

    /** 海纳尔里一个落点方块的位置：测试结构正上方的空中（每个测试实例的位置不同，互不干扰） */
    private static BlockPos hoannaFloor(GameTestHelper helper) {
        BlockPos abs = helper.absolutePos(TELEPORTER);
        return new BlockPos(abs.getX(), HOANNA_Y, abs.getZ());
    }

    /** 在海纳尔摆一个落点方块（上面两格清空），并加一张临时票让它在测试期间一直加载着 */
    private static void prepareHoannaFloor(ServerLevel hoanna, BlockPos floor) {
        hoanna.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(floor), 1, floor);
        hoanna.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
        hoanna.setBlock(floor.above(), Blocks.AIR.defaultBlockState(), 3);
        hoanna.setBlock(floor.above(2), Blocks.AIR.defaultBlockState(), 3);
    }

    private static ItemEntity drop(GameTestHelper helper, Vec3 relative, ItemStack stack) {
        Vec3 at = helper.absoluteVec(relative);
        ItemEntity entity = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, stack, 0D, 0D, 0D);
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }

    /** 扔在熔炉顶上 */
    private static ItemEntity dropOnFurnace(GameTestHelper helper, ItemStack stack) {
        return drop(helper, new Vec3(FURNACE.getX() + 0.5D, FURNACE.getY() + 1.1D, FURNACE.getZ() + 0.5D), stack);
    }

    /** 某个落点（相对熔炉的偏移）那一格里某种物品的总数 */
    private static int countAt(GameTestHelper helper, BlockPos furnaceOffset, Item item) {
        AABB box = new AABB(helper.absolutePos(FURNACE.offset(furnaceOffset))).inflate(0.5D);
        int count = 0;
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box, ItemEntity::isAlive)) {
            if (entity.getItem().is(item)) {
                count += entity.getItem().getCount();
            }
        }
        return count;
    }

    /** 某个落点那一格里掉落物的总数（不分种类） */
    private static int countAll(GameTestHelper helper, BlockPos furnaceOffset) {
        AABB box = new AABB(helper.absolutePos(FURNACE.offset(furnaceOffset))).inflate(0.5D);
        int count = 0;
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box, ItemEntity::isAlive)) {
            count += entity.getItem().getCount();
        }
        return count;
    }

    /** 挂一个只在测试期间有效的事件监听器，返回摘掉它的动作 */
    private static <E extends net.minecraftforge.eventbus.api.Event> Runnable listen(Class<E> type, Consumer<E> listener) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, type, listener);
        return () -> MinecraftForge.EVENT_BUS.unregister(listener);
    }

    // ================================================================ 魔力传送器：结构与费用

    /**
     * 结构：颜色随意都成形；少一块护板立即判为不成形、报那一格；运行中拆掉一根离机器远的柱子（收不到邻居通知），≤ 20 刻复查判为不成形。
     */
    @GameTest(template = EMPTY_LARGE)
    public static void teleporterStructure(GameTestHelper helper) {
        MagicalTeleporterBlockEntity be = buildTeleporter(helper);
        ServerLevel level = helper.getLevel();
        helper.assertTrue(be.structure().formed(), "摆全了应成形，不符：" + be.mismatchOffset());
        helper.assertTrue(ECVoidMachines.TELEPORTER_STRUCTURE.rules().size() == 41, "结构应是 36 虚空石 + 4 护板 + 机器本身，实际 "
                + ECVoidMachines.TELEPORTER_STRUCTURE.rules().size() + " 格");
        BlockPos plating = new BlockPos(0, 0, -2);
        helper.setBlock(TELEPORTER.offset(plating), Blocks.STONE);
        be.structure().checkNow(level, be.getBlockPos(), Direction.NORTH);
        helper.assertTrue(!be.structure().formed() && plating.equals(be.mismatchOffset()), "少一块护板应不成形、报 (0, 0, -2)，实际 " + be.mismatchOffset());
        helper.setBlock(TELEPORTER.offset(plating), ECVoidMachines.MAGIC_PLATING.displayState());
        be.structure().checkNow(level, be.getBlockPos(), Direction.NORTH);
        helper.assertTrue(be.structure().formed(), "放回护板应重新成形");

        BlockPos pillar = new BlockPos(2, 2, 1);
        helper.runAtTickTime(25, () -> {
            helper.assertTrue(be.isFormed(), "跑起来以后应成形（同步给客户端的标记）");
            helper.setBlock(TELEPORTER.offset(pillar), Blocks.AIR);
        });
        helper.runAtTickTime(47, () -> {
            helper.assertTrue(!be.isFormed() && pillar.equals(be.mismatchOffset()),
                    "拆掉柱子 ≤ 20 刻应判为不成形、报 (2, 2, 1)，实际成形=" + be.isFormed() + "，不符 " + be.mismatchOffset());
            helper.succeed();
        });
    }

    /**
     * 到点时立即复查结构：平时的结构缓存每 20 刻才复查一次（放下时在第 0 刻判过，下一次在第 20 刻左右，再下一次第 40 刻左右），
     * 第 24 刻拆掉一根柱子、第 30 刻左右到点，不复查就照样传了。复查不成形：清零、不传、不扣、不要求先走下来（和平时结构不全一样）。
     */
    @GameTest(template = EMPTY_LARGE, timeoutTicks = 100)
    public static void teleporterRechecksStructureOnArrival(GameTestHelper helper) {
        MagicalTeleporterBlockEntity be = buildTeleporter(helper);
        helper.setBlock(FLOOR, Blocks.STONE);
        be.setTestOverrides(null, null, 30, null, false);
        aim(be, here(helper, FLOOR), 5000);
        ServerPlayer player = standOn(helper, be);
        BlockPos pillar = TELEPORTER.offset(2, 2, 1);
        helper.runAtTickTime(24, () -> {
            helper.assertTrue(be.status() == Status.CHARGING && be.isFormed(), "拆之前应在蓄能、成形：状态 " + be.status());
            helper.setBlock(pillar, Blocks.AIR);
        });
        helper.runAtTickTime(36, () -> {
            helper.assertTrue(be.status() == Status.INCOMPLETE && be.progress() == 0, "到点复查应发现结构不全：状态 " + be.status() + "，进度 " + be.progress());
            helper.assertTrue(stillOnTop(player, be) && be.getMRUStorage().getMRU() == 5000, "不传不扣：位置 " + player.position() + "，MRU " + be.getMRUStorage().getMRU());
            helper.assertTrue(!be.mustStepOff(player.getUUID()), "结构不全不要求先走下来");
            stepOff(helper, be);
            helper.succeed();
        });
    }

    /** 费用：(1,0,0) → 166、(30,0,0) → 5000、(10,10,10) → 5000、三轴和 ≥ 300 封顶 50000、跨维度 = 容量；改覆盖值跟着变 */
    @GameTest(template = EMPTY_LARGE)
    public static void teleporterCost(GameTestHelper helper) {
        BlockPos o = BlockPos.ZERO;
        helper.assertTrue(MagicalTeleporterBlockEntity.cost(o, new BlockPos(1, 0, 0), false, 500, 50_000) == 166, "(1,0,0) 应为 166");
        helper.assertTrue(MagicalTeleporterBlockEntity.cost(o, new BlockPos(30, 0, 0), false, 500, 50_000) == 5000, "(30,0,0) 应为 5000");
        helper.assertTrue(MagicalTeleporterBlockEntity.cost(o, new BlockPos(10, -10, 10), false, 500, 50_000) == 5000, "(10,-10,10) 应为 5000");
        helper.assertTrue(MagicalTeleporterBlockEntity.cost(o, new BlockPos(299, 0, 0), false, 500, 50_000) == 49_833, "(299,0,0) 应为 49833");
        helper.assertTrue(MagicalTeleporterBlockEntity.cost(o, new BlockPos(300, 0, 0), false, 500, 50_000) == 50_000, "三轴和 300 封顶");
        helper.assertTrue(MagicalTeleporterBlockEntity.cost(o, new BlockPos(1_000_000, 0, 0), false, 500, 50_000) == 50_000, "远处封顶、不溢出");
        helper.assertTrue(MagicalTeleporterBlockEntity.cost(o, new BlockPos(1, 0, 0), true, 500, 50_000) == 50_000, "跨维度 = 容量");

        MagicalTeleporterBlockEntity be = buildTeleporter(helper);
        aim(be, here(helper, FLOOR), 0);
        helper.assertTrue(be.cost() == SAME_DIM_COST, "落点三轴和 8：应为 1333，实际 " + be.cost());
        be.setTestOverrides(1000, 300, null, null, false);
        helper.assertTrue(be.cost() == 800 && be.getMRUStorage().getMaxMRU() == 1000, "单价 300 → 800、容量覆盖 1000：实际 " + be.cost() + " / " + be.getMRUStorage().getMaxMRU());
        be.setTestOverrides(500, 300, null, null, false);
        helper.assertTrue(be.cost() == 500, "封顶跟着容量覆盖走：实际 " + be.cost());
        be.getInventory().setStackInSlot(MagicalTeleporterBlockEntity.SLOT_DESTINATION, gem(GlobalPos.of(ECHoanna.LEVEL, helper.absolutePos(FLOOR))));
        helper.assertTrue(be.cost() == 500, "跨维度 = 容量（覆盖后 500）：实际 " + be.cost());
        helper.succeed();
    }

    // ================================================================ 魔力传送器：同维度

    /** 站满蓄能刻数被传到落点方块的顶面（台阶 → +0.5），MRU 正好扣掉费用、进度清零；没到点之前不传 */
    @GameTest(template = EMPTY_LARGE)
    public static void teleporterTeleports(GameTestHelper helper) {
        MagicalTeleporterBlockEntity be = buildTeleporter(helper);
        helper.setBlock(FLOOR, Blocks.SMOOTH_STONE_SLAB);
        be.setTestOverrides(null, null, 10, null, false);
        aim(be, here(helper, FLOOR), 2000);
        ServerPlayer player = standOn(helper, be);
        Vec3 landing = helper.absoluteVec(new Vec3(FLOOR.getX() + 0.5D, FLOOR.getY() + 0.5D, FLOOR.getZ() + 0.5D));
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(stillOnTop(player, be) && be.progress() > 0 && be.progress() < 10 && be.status() == Status.CHARGING,
                    "没到点不传：进度 " + be.progress() + "，状态 " + be.status());
            helper.assertTrue(be.chargeStart() >= 0L, "蓄能时有开始时刻（给客户端画粒子）");
            helper.assertTrue(be.preview() == ECTeleport.Result.OK && be.currentCost() == SAME_DIM_COST, "界面预览：落点安全、费用 1333，实际 " + be.preview() + " / " + be.currentCost());
        });
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(player.position().distanceToSqr(landing) < 1.0E-6D, "应落在台阶顶面 " + landing + "，实际 " + player.position());
            helper.assertTrue(be.getMRUStorage().getMRU() == 2000 - SAME_DIM_COST, "应正好扣 1333，剩 " + be.getMRUStorage().getMRU());
            helper.assertTrue(be.progress() == 0 && be.chargeStart() < 0L, "传完进度清零、停止蓄能动画");
            helper.assertTrue(player.fallDistance == 0F, "传完清摔落距离");
            stepOff(helper, be);
            helper.succeed();
        });
    }

    /** MRU 不够费用：进度不走（状态 NO_MRU）；补上接着走；半路再没了进度冻结、不清零 */
    @GameTest(template = EMPTY_LARGE)
    public static void teleporterFreezesWithoutMru(GameTestHelper helper) {
        MagicalTeleporterBlockEntity be = buildTeleporter(helper);
        helper.setBlock(FLOOR, Blocks.STONE);
        be.setTestOverrides(null, null, 40, null, false);
        aim(be, here(helper, FLOOR), SAME_DIM_COST - 1);
        ServerPlayer player = standOn(helper, be);
        AtomicInteger frozen = new AtomicInteger(-1);
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(be.progress() == 0 && be.status() == Status.NO_MRU, "差 1 MRU 不应蓄能：进度 " + be.progress() + "，状态 " + be.status());
            be.getMRUStorage().setMRU(SAME_DIM_COST);
        });
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(be.progress() >= 5 && be.progress() < 40, "补上后应接着走，进度 " + be.progress());
            frozen.set(be.progress());
            be.getMRUStorage().setMRU(0);
        });
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(be.progress() == frozen.get() && be.status() == Status.NO_MRU,
                    "半路没了 MRU 应冻结在 " + frozen.get() + "，实际 " + be.progress() + "，状态 " + be.status());
            helper.assertTrue(stillOnTop(player, be), "没传");
            stepOff(helper, be);
            helper.succeed();
        });
    }

    /** 中途换人进度清零（原作 A 站 200 刻离开、B 再站 50 刻就被传走） */
    @GameTest(template = EMPTY_LARGE)
    public static void teleporterOccupantChangeResets(GameTestHelper helper) {
        MagicalTeleporterBlockEntity be = buildTeleporter(helper);
        helper.setBlock(FLOOR, Blocks.STONE);
        be.setTestOverrides(null, null, 40, null, false);
        aim(be, here(helper, FLOOR), 5000);
        ServerPlayer first = standOn(helper, be);
        ServerPlayer second = ProtectionFrameworkTests.realPlayer(helper);
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(be.progress() >= 6, "第一个人应在蓄能，进度 " + be.progress());
            first.moveTo(first.getX() + 3D, first.getY(), first.getZ());
            standOn(helper, be, second);
        });
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(be.progress() >= 1 && be.progress() <= 2, "换人应从 0 重新算，进度 " + be.progress());
            stepOff(helper, be);
            helper.succeed();
        });
    }

    /** 有红石：进度冻结（状态 POWERED）；撤掉接着走 */
    @GameTest(template = EMPTY_LARGE)
    public static void teleporterRedstoneFreezes(GameTestHelper helper) {
        MagicalTeleporterBlockEntity be = buildTeleporter(helper);
        helper.setBlock(FLOOR, Blocks.STONE);
        be.setTestOverrides(null, null, 60, null, false);
        aim(be, here(helper, FLOOR), 5000);
        standOn(helper, be);
        BlockPos power = TELEPORTER.below();
        AtomicInteger frozen = new AtomicInteger(-1);
        helper.runAtTickTime(5, () -> helper.setBlock(power, Blocks.REDSTONE_BLOCK));
        helper.runAtTickTime(7, () -> frozen.set(be.progress()));
        helper.runAtTickTime(15, () -> {
            helper.assertTrue(frozen.get() > 0 && be.progress() == frozen.get() && be.status() == Status.POWERED,
                    "有红石应冻结在 " + frozen.get() + "，实际 " + be.progress() + "，状态 " + be.status());
            helper.setBlock(power, Blocks.AIR);
        });
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(be.progress() > frozen.get() && be.status() == Status.CHARGING, "撤掉红石应接着走，进度 " + be.progress());
            stepOff(helper, be);
            helper.succeed();
        });
    }

    /**
     * 落点检查（纯规则）：石头 → 顶面 +1、台阶 +0.5、栅栏 +1.5；脚下空气 NO_FLOOR；脚 / 头那格有方块 BLOCKED；
     * 脚那格有岩浆、火 HAZARD；落点是点着的营火 HAZARD、熄灭的营火 OK；建筑高度最高那层能站、再往上 OUT_OF_WORLD；没加载 UNLOADED（不加载区块）；
     * 给了玩家时落脚格、脚下格被领地拒绝 DENIED；不给玩家（预览）不查权限。
     */
    @GameTest(template = EMPTY)
    public static void teleportLandingRules(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        record Case(BlockPos floor, BlockState state, double top) {
        }
        List<Case> ok = List.of(
                new Case(new BlockPos(0, 1, 0), Blocks.STONE.defaultBlockState(), 1D),
                new Case(new BlockPos(2, 1, 0), Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 0.5D),
                new Case(new BlockPos(4, 1, 0), Blocks.OAK_FENCE.defaultBlockState(), 1.5D),
                new Case(new BlockPos(0, 1, 2), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false), 0.4375D));
        for (Case c : ok) {
            helper.setBlock(c.floor(), c.state());
            BlockPos abs = helper.absolutePos(c.floor());
            ECTeleport.Check check = ECTeleport.check(null, level, abs, null);
            Vec3 expected = new Vec3(abs.getX() + 0.5D, abs.getY() + c.top(), abs.getZ() + 0.5D);
            helper.assertTrue(check.ok() && check.landing() != null && check.landing().distanceToSqr(expected) < 1.0E-9D,
                    c.state() + " 应能站、落点 " + expected + "，实际 " + check.result() + " " + check.landing());
        }

        helper.assertTrue(ECTeleport.check(null, level, helper.absolutePos(new BlockPos(2, 1, 2)), null).result() == ECTeleport.Result.NO_FLOOR,
                "脚下空气应 NO_FLOOR");
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.WATER);
        helper.assertTrue(ECTeleport.check(null, level, helper.absolutePos(new BlockPos(2, 1, 2)), null).result() == ECTeleport.Result.NO_FLOOR,
                "脚下是水应 NO_FLOOR");
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.AIR);

        BlockPos feetBlocked = new BlockPos(4, 1, 2);
        helper.setBlock(feetBlocked, Blocks.STONE);
        helper.setBlock(feetBlocked.above(), Blocks.STONE);
        helper.assertTrue(ECTeleport.check(null, level, helper.absolutePos(feetBlocked), null).result() == ECTeleport.Result.BLOCKED, "脚那格有方块应 BLOCKED");
        BlockPos headBlocked = new BlockPos(0, 1, 4);
        helper.setBlock(headBlocked, Blocks.STONE);
        helper.setBlock(headBlocked.above(2), Blocks.STONE);
        helper.assertTrue(ECTeleport.check(null, level, helper.absolutePos(headBlocked), null).result() == ECTeleport.Result.BLOCKED, "头那格有方块应 BLOCKED");

        BlockPos lava = new BlockPos(2, 1, 4);
        helper.setBlock(lava, Blocks.STONE);
        level.setBlock(helper.absolutePos(lava.above()), Blocks.LAVA.defaultBlockState(), 18);
        helper.assertTrue(ECTeleport.check(null, level, helper.absolutePos(lava), null).result() == ECTeleport.Result.HAZARD, "脚那格有岩浆应 HAZARD");
        level.setBlock(helper.absolutePos(lava.above()), Blocks.AIR.defaultBlockState(), 18);
        BlockPos fire = new BlockPos(4, 1, 4);
        helper.setBlock(fire, Blocks.NETHERRACK);
        level.setBlock(helper.absolutePos(fire.above()), Blocks.FIRE.defaultBlockState(), 18);
        helper.assertTrue(ECTeleport.check(null, level, helper.absolutePos(fire), null).result() == ECTeleport.Result.HAZARD, "脚那格有火应 HAZARD");
        level.setBlock(helper.absolutePos(fire.above()), Blocks.AIR.defaultBlockState(), 18);
        BlockPos litCampfire = new BlockPos(0, 1, 2);
        helper.setBlock(litCampfire, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
        helper.assertTrue(ECTeleport.check(null, level, helper.absolutePos(litCampfire), null).result() == ECTeleport.Result.HAZARD, "点着的营火当落点应 HAZARD");

        // 建筑高度：最高那层方块上能站（头伸出去不拦），再往上一格就是世界外
        BlockPos top = new BlockPos(helper.absolutePos(BlockPos.ZERO).getX(), level.getMaxBuildHeight() - 1, helper.absolutePos(BlockPos.ZERO).getZ());
        level.setBlock(top, Blocks.STONE.defaultBlockState(), 18);
        try {
            helper.assertTrue(ECTeleport.check(null, level, top, null).ok(), "最高那层方块上应能站，实际 " + ECTeleport.check(null, level, top, null).result());
            // 给了真玩家也一样：落脚格在建筑高度以外，只查脚下那格（以前落脚格永远「没加载」→ PENDING → 等满 100 刻超时）
            ServerPlayer climber = ProtectionFrameworkTests.realPlayer(helper);
            ECTeleport.Result withPlayer = ECTeleport.check(climber, level, top, "top").result();
            helper.assertTrue(withPlayer == ECTeleport.Result.OK, "真玩家站最高那层方块应 OK，实际 " + withPlayer);
            helper.assertTrue(ECTeleport.check(null, level, top.above(), null).result() == ECTeleport.Result.OUT_OF_WORLD, "建筑高度以外应 OUT_OF_WORLD");
        } finally {
            level.setBlock(top, Blocks.AIR.defaultBlockState(), 18);
        }

        BlockPos far = helper.absolutePos(BlockPos.ZERO).offset(2_000_000, 0, 0);
        helper.assertTrue(ECTeleport.check(null, level, far, null).result() == ECTeleport.Result.UNLOADED, "没加载的区块应 UNLOADED");
        helper.assertTrue(level.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null, "检查不应加载区块");

        // 权限：石头（落脚格 (0,2,0)）拒落脚格；栅栏（落点 y + 1.5，落脚格 (4,2,0)）只拒脚下那格
        BlockPos stone = ok.get(0).floor();
        BlockPos fence = ok.get(2).floor();
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        ServerPlayer other = ProtectionFrameworkTests.realPlayer(helper);
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(stone.above()).deny(fence)) {
            helper.assertTrue(ECTeleport.check(null, level, helper.absolutePos(stone), null).ok(), "不给玩家（预览）不查权限");
            helper.assertTrue(ECTeleport.check(player, level, helper.absolutePos(stone), "a").result() == ECTeleport.Result.DENIED, "落脚格被拒应 DENIED");
            helper.assertTrue(ECTeleport.check(other, level, helper.absolutePos(fence), "b").result() == ECTeleport.Result.DENIED,
                    "只有脚下那格被拒也应 DENIED（有的保护模组不管空气格）");
            helper.assertTrue(claims.breaks(stone.above()) == 1 && claims.breaks(fence.above()) == 1 && claims.breaks(fence) == 1,
                    "落脚格、脚下格各探测一次：" + claims.breaks(stone.above()) + " / " + claims.breaks(fence.above()) + " / " + claims.breaks(fence));
            helper.assertTrue(ECTeleport.check(player, level, helper.absolutePos(ok.get(1).floor()), "c").ok(), "没被拒的格子照常 OK");
        }
        helper.succeed();
    }

    /**
     * 会伤人的方块：落脚面是岩浆块、仙人掌、恐惧仙人掌（模组方块，靠寻路钩子认）→ HAZARD；脚那格有细雪、甜浆果丛、凋灵玫瑰 → HAZARD；
     * 只是旁边一格有仙人掌、凋灵玫瑰 → 照常 OK（不误杀）。宝石点在矮草、一层雪上 → 落在下面那块地上；点在火上 → 落在下面、火在脚那格 → HAZARD。
     * 每种摆完立刻检查（后摆的方块可能让前面立不住的植物掉下来）。
     */
    @GameTest(template = EMPTY_LARGE)
    public static void teleportLandingHazards(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState grassBlock = Blocks.GRASS_BLOCK.defaultBlockState();
        // ground：合格时应落在哪一格的顶上（都是整块，顶面 +1）；不合格的填 null
        record Case(String name, BlockPos target, ECTeleport.Result expected, BlockPos ground) {
        }
        List<Case> cases = new ArrayList<>();
        Consumer<Case> verify = c -> {
            ECTeleport.Check check = ECTeleport.check(null, level, helper.absolutePos(c.target()), null);
            helper.assertTrue(check.result() == c.expected(), c.name() + " 应为 " + c.expected() + "，实际 " + check.result());
            if (c.ground() != null) {
                BlockPos ground = c.ground();
                Vec3 expected = helper.absoluteVec(new Vec3(ground.getX() + 0.5D, ground.getY() + 1D, ground.getZ() + 0.5D));
                helper.assertTrue(check.landing() != null && check.landing().distanceToSqr(expected) < 1.0E-9D,
                        c.name() + " 应落在 " + expected + "，实际 " + check.landing());
            }
            cases.add(c);
        };

        helper.setBlock(new BlockPos(1, 1, 1), Blocks.MAGMA_BLOCK);
        verify.accept(new Case("落脚面是岩浆块", new BlockPos(1, 1, 1), ECTeleport.Result.HAZARD, null));
        helper.setBlock(new BlockPos(4, 0, 1), Blocks.SAND);
        helper.setBlock(new BlockPos(4, 1, 1), Blocks.CACTUS);
        verify.accept(new Case("落脚面是仙人掌", new BlockPos(4, 1, 1), ECTeleport.Result.HAZARD, null));
        helper.setBlock(new BlockPos(7, 0, 1), Blocks.SAND);
        helper.setBlock(new BlockPos(7, 1, 1), ECHoannaNature.DREAD_CACTUS.get());
        verify.accept(new Case("落脚面是恐惧仙人掌", new BlockPos(7, 1, 1), ECTeleport.Result.HAZARD, null));

        helper.setBlock(new BlockPos(1, 1, 4), stone);
        helper.setBlock(new BlockPos(1, 2, 4), Blocks.POWDER_SNOW);
        verify.accept(new Case("脚那格是细雪", new BlockPos(1, 1, 4), ECTeleport.Result.HAZARD, null));
        helper.setBlock(new BlockPos(4, 1, 4), grassBlock);
        helper.setBlock(new BlockPos(4, 2, 4), Blocks.SWEET_BERRY_BUSH);
        verify.accept(new Case("脚那格是甜浆果丛", new BlockPos(4, 1, 4), ECTeleport.Result.HAZARD, null));
        helper.setBlock(new BlockPos(7, 1, 4), grassBlock);
        helper.setBlock(new BlockPos(7, 2, 4), Blocks.WITHER_ROSE);
        verify.accept(new Case("脚那格是凋灵玫瑰", new BlockPos(7, 1, 4), ECTeleport.Result.HAZARD, null));

        helper.setBlock(new BlockPos(1, 1, 7), Blocks.NETHERRACK);
        level.setBlock(helper.absolutePos(new BlockPos(1, 2, 7)), Blocks.FIRE.defaultBlockState(), 18);
        verify.accept(new Case("宝石点在火上（落到下面，火在脚那格）", new BlockPos(1, 2, 7), ECTeleport.Result.HAZARD, null));
        level.setBlock(helper.absolutePos(new BlockPos(1, 2, 7)), Blocks.AIR.defaultBlockState(), 18);
        helper.setBlock(new BlockPos(4, 1, 7), grassBlock);
        helper.setBlock(new BlockPos(4, 2, 7), Blocks.GRASS);
        verify.accept(new Case("宝石点在矮草上", new BlockPos(4, 2, 7), ECTeleport.Result.OK, new BlockPos(4, 1, 7)));
        helper.setBlock(new BlockPos(7, 1, 7), stone);
        helper.setBlock(new BlockPos(7, 2, 7), Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1));
        verify.accept(new Case("宝石点在一层雪上", new BlockPos(7, 2, 7), ECTeleport.Result.OK, new BlockPos(7, 1, 7)));

        // 旁边一格有仙人掌、凋灵玫瑰：落点本身没事，不该误判
        helper.setBlock(new BlockPos(4, 1, 9), stone);
        helper.setBlock(new BlockPos(5, 1, 9), Blocks.SAND);
        helper.setBlock(new BlockPos(5, 2, 9), Blocks.CACTUS);
        helper.setBlock(new BlockPos(3, 1, 9), grassBlock);
        helper.setBlock(new BlockPos(3, 2, 9), Blocks.WITHER_ROSE);
        verify.accept(new Case("旁边一格有仙人掌和凋灵玫瑰", new BlockPos(4, 1, 9), ECTeleport.Result.OK, new BlockPos(4, 1, 9)));
        helper.assertTrue(cases.size() == 10, "十种都查过：" + cases.size());
        helper.succeed();
    }

    /**
     * 三态里的「给不出结论」不当拒绝：先替玩家把本刻的探测预算用掉（每名玩家每刻 1 格），
     * 落点检查得 PENDING（不是 DENIED）；站在传送器上的人检查传送器自己那格得 WAITING，进度不动、不要求先走下来；下一刻预算恢复，照常蓄能。
     */
    @GameTest(template = EMPTY_LARGE)
    public static void teleporterUnknownVerdictsWait(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MagicalTeleporterBlockEntity be = buildTeleporter(helper);
        helper.setBlock(FLOOR, Blocks.STONE);
        be.setTestOverrides(null, null, 40, null, false);
        aim(be, here(helper, FLOOR), 5000);
        ServerPlayer lander = ProtectionFrameworkTests.realPlayer(helper);
        ServerPlayer rider = standOn(helper, be);
        BlockPos spend = helper.absolutePos(new BlockPos(1, 1, 1));
        try {
            ECProtection.Config.setMaxProbesPerPlayerTickForTest(1);
            ECProtection.check(lander, level, spend, "spend");
            ECTeleport.Result landing = ECTeleport.check(lander, level, helper.absolutePos(FLOOR), "landing").result();
            helper.assertTrue(landing == ECTeleport.Result.PENDING, "预算用完：落点应 PENDING，实际 " + landing);
            ECProtection.check(rider, level, spend, "spend");
            tick(helper, be);
            helper.assertTrue(be.status() == Status.WAITING && be.progress() == 0, "预算用完：传送器那格应 WAITING、进度 0，实际 " + be.status() + " / " + be.progress());
            helper.assertTrue(!be.mustStepOff(rider.getUUID()), "给不出结论不要求先走下来");
        } finally {
            ECProtection.Config.setMaxProbesPerPlayerTickForTest(null);
        }
        helper.runAtTickTime(8, () -> {
            helper.assertTrue(be.status() == Status.CHARGING && be.progress() > 0, "下一刻预算恢复应照常蓄能：状态 " + be.status() + "，进度 " + be.progress());
            stepOff(helper, be);
            helper.succeed();
        });
    }

    /** 落点被挡：不传、不扣，状态 BLOCKED；挡的东西拿走以后还要先走下来再站上去，之后照常传 */
    @GameTest(template = EMPTY_LARGE, timeoutTicks = 60)
    public static void teleporterBlockedNeedsStepOff(GameTestHelper helper) {
        MagicalTeleporterBlockEntity be = buildTeleporter(helper);
        helper.setBlock(FLOOR, Blocks.STONE);
        helper.setBlock(FLOOR.above(), Blocks.STONE);
        be.setTestOverrides(null, null, 5, null, false);
        aim(be, here(helper, FLOOR), 2000);
        ServerPlayer player = standOn(helper, be);
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(be.status() == Status.BLOCKED && be.getMRUStorage().getMRU() == 2000 && stillOnTop(player, be),
                    "被挡应不传不扣：状态 " + be.status() + "，MRU " + be.getMRUStorage().getMRU());
            helper.assertTrue(be.mustStepOff(player.getUUID()), "失败后要先走下来");
            helper.setBlock(FLOOR.above(), Blocks.AIR);
        });
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(be.progress() == 0 && stillOnTop(player, be) && be.status() == Status.BLOCKED,
                    "没走下来就不重新蓄能（界面还显示上次的原因）：进度 " + be.progress() + "，状态 " + be.status());
            stepOff(helper, be);
        });
        helper.runAtTickTime(22, () -> {
            helper.assertTrue(!be.mustStepOff(player.getUUID()), "走下来以后标记清掉");
            standOn(helper, be, player);
        });
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(!stillOnTop(player, be) && be.getMRUStorage().getMRU() == 2000 - SAME_DIM_COST,
                    "重新站上去应照常传：位置 " + player.position() + "，MRU " + be.getMRUStorage().getMRU());
            stepOff(helper, be);
            helper.succeed();
        });
    }

    /** 落点被模拟领地拒绝：到点不传不扣（DENIED）、要先走下来 */
    @GameTest(template = EMPTY_LARGE)
    public static void teleporterDeniedLanding(GameTestHelper helper) {
        MagicalTeleporterBlockEntity be = buildTeleporter(helper);
        helper.setBlock(FLOOR, Blocks.STONE);
        be.setTestOverrides(null, null, 5, null, false);
        aim(be, here(helper, FLOOR), 2000);
        ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(FLOOR.above());
        ServerPlayer player = standOn(helper, be);
        helper.runAtTickTime(12, () -> {
            claims.close();
            helper.assertTrue(be.status() == Status.DENIED && be.getMRUStorage().getMRU() == 2000 && stillOnTop(player, be),
                    "落点没权限应不传不扣：状态 " + be.status() + "，MRU " + be.getMRUStorage().getMRU());
            helper.assertTrue(be.mustStepOff(player.getUUID()), "失败后要先走下来");
            helper.assertTrue(claims.breaks(FLOOR.above()) >= 1, "落脚格应以玩家身份探测过");
            stepOff(helper, be);
            helper.succeed();
        });
    }

    /** 传送器自己那格被模拟领地拒绝（放在别人领地里）：站上去的人不能蓄能（NO_ACCESS），不扣 MRU */
    @GameTest(template = EMPTY_LARGE)
    public static void teleporterNeedsAccessToItself(GameTestHelper helper) {
        MagicalTeleporterBlockEntity be = buildTeleporter(helper);
        helper.setBlock(FLOOR, Blocks.STONE);
        be.setTestOverrides(null, null, 5, null, false);
        aim(be, here(helper, FLOOR), 2000);
        ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(TELEPORTER);
        ServerPlayer player = standOn(helper, be);
        helper.runAtTickTime(12, () -> {
            claims.close();
            helper.assertTrue(be.status() == Status.NO_ACCESS && be.progress() == 0, "不能动传送器那格应不蓄能：状态 " + be.status() + "，进度 " + be.progress());
            helper.assertTrue(be.getMRUStorage().getMRU() == 2000 && stillOnTop(player, be), "不扣不传");
            stepOff(helper, be);
            helper.succeed();
        });
    }

    /** 假玩家、旁观者不算站上去（READY、进度 0）；骑着东西的不蓄能（RIDING） */
    @GameTest(template = EMPTY_LARGE)
    public static void teleporterOnlyRealPlayersOnFoot(GameTestHelper helper) {
        MagicalTeleporterBlockEntity be = buildTeleporter(helper);
        helper.setBlock(FLOOR, Blocks.STONE);
        be.setTestOverrides(null, null, 3, null, false);
        aim(be, here(helper, FLOOR), 5000);
        standOn(helper, be, ProtectionFrameworkTests.fakePlayer(helper));
        ServerPlayer spectator = ProtectionFrameworkTests.realPlayer(helper);
        ServerPlayer rider = ProtectionFrameworkTests.realPlayer(helper);
        helper.runAtTickTime(6, () -> {
            helper.assertTrue(be.progress() == 0 && be.status() == Status.READY, "假玩家不算：进度 " + be.progress() + "，状态 " + be.status());
            spectator.setGameMode(GameType.SPECTATOR);
            standOn(helper, be, spectator);
        });
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(be.progress() == 0 && be.status() == Status.READY, "旁观者不算：进度 " + be.progress() + "，状态 " + be.status());
            standOn(helper, be, rider);
            Boat boat = new Boat(EntityType.BOAT, helper.getLevel());
            boat.moveTo(rider.getX(), rider.getY(), rider.getZ());
            helper.assertTrue(rider.startRiding(boat, true), "测试前提：骑上船");
            rider.moveTo(be.getBlockPos().getX() + 0.5D, be.getBlockPos().getY() + 1D, be.getBlockPos().getZ() + 0.5D);
        });
        helper.runAtTickTime(18, () -> {
            helper.assertTrue(be.progress() == 0 && be.status() == Status.RIDING, "骑着东西不蓄能：进度 " + be.progress() + "，状态 " + be.status());
            helper.assertTrue(be.getMRUStorage().getMRU() == 5000, "都不扣 MRU");
            rider.stopRiding();
            stepOff(helper, be);
            helper.succeed();
        });
    }

    /** 没宝石、宝石没绑、只有 display NBT 的宝石（铁砧改过名）：不崩、状态 NO_DESTINATION；绑好 → READY；拆一块 → INCOMPLETE */
    @GameTest(template = EMPTY_LARGE)
    public static void teleporterGemStates(GameTestHelper helper) {
        MagicalTeleporterBlockEntity be = buildTeleporter(helper);
        helper.setBlock(FLOOR, Blocks.STONE);
        ServerLevel level = helper.getLevel();
        ItemStack renamed = new ItemStack(ECItems.BOUND_GEM.get());
        renamed.setHoverName(Component.literal("改过名"));
        List<ItemStack> unusable = List.of(ItemStack.EMPTY, new ItemStack(ECItems.BOUND_GEM.get()), renamed);
        for (ItemStack stack : unusable) {
            be.getInventory().setStackInSlot(MagicalTeleporterBlockEntity.SLOT_DESTINATION, stack.copy());
            MRUBlockEntity.tickServer(level, be.getBlockPos(), be.getBlockState(), be);
            helper.assertTrue(be.status() == Status.NO_DESTINATION && be.checkTarget(null) == Status.NO_DESTINATION && be.cost() == 0,
                    stack + " 应为 NO_DESTINATION，实际 " + be.status());
        }
        aim(be, here(helper, FLOOR), 5000);
        MRUBlockEntity.tickServer(level, be.getBlockPos(), be.getBlockState(), be);
        helper.assertTrue(be.status() == Status.READY && be.checkTarget(null) == Status.READY, "绑好了、没人站：READY，实际 " + be.status());
        helper.setBlock(TELEPORTER.offset(-2, 0, 0), Blocks.AIR);
        be.structure().checkNow(level, be.getBlockPos(), Direction.NORTH);
        helper.assertTrue(be.checkTarget(null) == Status.INCOMPLETE, "拆一块护板：INCOMPLETE，实际 " + be.checkTarget(null));
        helper.succeed();
    }

    /** 同维度：别的模组 / 插件取消了 EntityTeleportEvent → 不传、不扣（CANCELLED）、要先走下来 */
    @GameTest(template = EMPTY_LARGE)
    public static void teleporterSameDimensionCancelled(GameTestHelper helper) {
        MagicalTeleporterBlockEntity be = buildTeleporter(helper);
        helper.setBlock(FLOOR, Blocks.STONE);
        be.setTestOverrides(null, null, 5, null, false);
        aim(be, here(helper, FLOOR), 2000);
        ServerPlayer player = standOn(helper, be);
        UUID id = player.getUUID();
        AtomicInteger seen = new AtomicInteger();
        Runnable unlisten = listen(EntityTeleportEvent.class, event -> {
            if (event.getEntity().getUUID().equals(id)) {
                seen.incrementAndGet();
                event.setCanceled(true);
            }
        });
        helper.runAtTickTime(12, () -> {
            unlisten.run();
            helper.assertTrue(seen.get() == 1, "应发一次 EntityTeleportEvent，实际 " + seen.get());
            helper.assertTrue(be.status() == Status.CANCELLED && be.getMRUStorage().getMRU() == 2000 && stillOnTop(player, be),
                    "被取消应不传不扣：状态 " + be.status() + "，MRU " + be.getMRUStorage().getMRU());
            helper.assertTrue(be.mustStepOff(id), "被取消后要先走下来");
            stepOff(helper, be);
            helper.succeed();
        });
    }

    /** 落到另一台传送器上：那一台记下「这名玩家要先走下来」，站着不动不会被送回去 */
    @GameTest(template = EMPTY_LARGE)
    public static void teleporterLandingOnTeleporterNeedsStepOff(GameTestHelper helper) {
        MagicalTeleporterBlockEntity be = buildTeleporter(helper);
        helper.setBlock(FLOOR, ECVoidMachines.MAGICAL_TELEPORTER.get());
        MagicalTeleporterBlockEntity other = (MagicalTeleporterBlockEntity) helper.getBlockEntity(FLOOR);
        other.getInventory().setStackInSlot(MagicalTeleporterBlockEntity.SLOT_DESTINATION, gem(here(helper, TELEPORTER)));
        other.getMRUStorage().setMRU(5000);
        be.setTestOverrides(null, null, 5, null, false);
        aim(be, here(helper, FLOOR), 2000);
        ServerPlayer player = standOn(helper, be);
        // 落地以后另一台的实体查询要能看到他（测试玩家不在实体表里）；落地之前他不在那台正上方，登记了也不算站上去
        MagicalTeleporterBlockEntity.setTestOccupant(helper.getLevel(), other.getBlockPos(), player);
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(stillOnTop(player, other), "应落在另一台传送器顶上：" + player.position());
            helper.assertTrue(other.mustStepOff(player.getUUID()), "落点那台应要求先走下来");
            stepOff(helper, be);
        });
        helper.runAtTickTime(18, () -> {
            helper.assertTrue(other.progress() == 0 && other.status() == Status.STEP_OFF, "站着不动不蓄能：进度 " + other.progress() + "，状态 " + other.status());
            MagicalTeleporterBlockEntity.setTestOccupant(helper.getLevel(), other.getBlockPos(), null);
            helper.succeed();
        });
    }

    /**
     * 另一台顶上铺了地毯（结构在它正上方那格不管放什么），宝石绑在地毯上：到点时按落地后脚下那格认出那一台，当场要求先走下来。
     * 在同一段同步代码里推进一刻（蓄能 1 刻、到点就传），另一台这时还没 tick，标记只能是到点时记上的。
     * 送他走的那一台不受到达记录影响：走开一刻再站回去，照常传。
     */
    @GameTest(template = EMPTY_LARGE)
    public static void teleporterLandingOnCarpetedTeleporter(GameTestHelper helper) {
        MagicalTeleporterBlockEntity be = buildTeleporter(helper);
        helper.setBlock(FLOOR, ECVoidMachines.MAGICAL_TELEPORTER.get());
        helper.setBlock(FLOOR.above(), Blocks.WHITE_CARPET);
        MagicalTeleporterBlockEntity other = (MagicalTeleporterBlockEntity) helper.getBlockEntity(FLOOR);
        be.setTestOverrides(null, null, 1, null, false);
        aim(be, here(helper, FLOOR.above()), 5000);
        int cost = be.cost();
        ServerPlayer player = standOn(helper, be);
        UUID id = player.getUUID();
        try {
            tick(helper, be);
            Vec3 landing = helper.absoluteVec(new Vec3(FLOOR.getX() + 0.5D, FLOOR.getY() + 1D + 1D / 16D, FLOOR.getZ() + 0.5D));
            helper.assertTrue(player.position().distanceToSqr(landing) < 1.0E-6D, "应落在另一台顶上的地毯上 " + landing + "，实际 " + player.position());
            helper.assertTrue(other.mustStepOff(id), "脚下是另一台传送器（隔着地毯）：那台应要求先走下来");
            helper.assertTrue(be.getMRUStorage().getMRU() == 5000 - cost, "扣了一趟的费用：剩 " + be.getMRUStorage().getMRU());

            stepOff(helper, be);
            tick(helper, be);
            standOn(helper, be, player);
            tick(helper, be);
            helper.assertTrue(be.getMRUStorage().getMRU() == 5000 - 2 * cost && player.position().distanceToSqr(landing) < 1.0E-6D,
                    "走回送他走的那一台应照常传：剩 " + be.getMRUStorage().getMRU() + "，位置 " + player.position() + "，状态 " + be.status());
        } finally {
            stepOff(helper, be);
            MagicalTeleporterBlockEntity.forgetArrival(id);
        }
        helper.succeed();
    }

    /**
     * 另一台上方放一扇打开的活板门，宝石绑在活板门上：落点在活板门顶上（面板贴边，人站在列正中碰不到，下一刻就掉下去），
     * 按脚下那格认不出那一台；靠到达记录兜底：刚被送走的人踩上那一台，也要先走下来一次。
     */
    @GameTest(template = EMPTY_LARGE)
    public static void teleporterDropOntoTeleporterNeedsStepOff(GameTestHelper helper) {
        MagicalTeleporterBlockEntity be = buildTeleporter(helper);
        helper.setBlock(FLOOR, ECVoidMachines.MAGICAL_TELEPORTER.get());
        helper.setBlock(FLOOR.above(), Blocks.IRON_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.OPEN, true));
        MagicalTeleporterBlockEntity other = (MagicalTeleporterBlockEntity) helper.getBlockEntity(FLOOR);
        other.getInventory().setStackInSlot(MagicalTeleporterBlockEntity.SLOT_DESTINATION, gem(here(helper, TELEPORTER)));
        other.getMRUStorage().setMRU(5000);
        be.setTestOverrides(null, null, 1, null, false);
        aim(be, here(helper, FLOOR.above()), 5000);
        ServerPlayer player = standOn(helper, be);
        UUID id = player.getUUID();
        try {
            tick(helper, be);
            Vec3 onTrapdoor = helper.absoluteVec(new Vec3(FLOOR.getX() + 0.5D, FLOOR.getY() + 2D, FLOOR.getZ() + 0.5D));
            helper.assertTrue(player.position().distanceToSqr(onTrapdoor) < 1.0E-6D, "应落在活板门顶上 " + onTrapdoor + "，实际 " + player.position());
            helper.assertTrue(!other.mustStepOff(id), "测试前提：落点下面是活板门，到点时认不出那一台");
            // 测试玩家不在世界里、不会自己掉下去：直接挪到那一台顶上（等于掉下来了）
            standOn(helper, other, player);
            tick(helper, other);
            helper.assertTrue(other.mustStepOff(id) && other.status() == Status.STEP_OFF && other.progress() == 0,
                    "刚被送来就踩上那一台：应先走下来，状态 " + other.status() + "，进度 " + other.progress());
        } finally {
            stepOff(helper, be);
            stepOff(helper, other);
            MagicalTeleporterBlockEntity.forgetArrival(id);
        }
        helper.succeed();
    }

    /** 漏斗：两格都只收绑定宝石，只能抽 1 号格（原作的输出格） */
    @GameTest(template = EMPTY)
    public static void teleporterAutomationRules(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ECVoidMachines.MAGICAL_TELEPORTER.get());
        MagicalTeleporterBlockEntity be = (MagicalTeleporterBlockEntity) helper.getBlockEntity(pos);
        IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);
        for (int slot = 0; slot < 2; slot++) {
            helper.assertTrue(handler.insertItem(slot, new ItemStack(Items.DIAMOND), false).getCount() == 1, slot + " 号格不应收钻石");
            helper.assertTrue(handler.insertItem(slot, new ItemStack(ECItems.BOUND_GEM.get()), false).isEmpty(), slot + " 号格应收绑定宝石");
        }
        helper.assertTrue(handler.extractItem(MagicalTeleporterBlockEntity.SLOT_SOURCE, 1, false).isEmpty(), "0 号格不能被抽");
        helper.assertTrue(!handler.extractItem(MagicalTeleporterBlockEntity.SLOT_DESTINATION, 1, false).isEmpty(), "1 号格能被抽");
        helper.assertTrue(!be.getUpdateTag().contains("inventory"), "更新包不带物品栏（1 号格宝石记着目的地坐标）");
        helper.succeed();
    }

    // ================================================================ 魔力传送器：跨维度（真海纳尔世界）

    /** 跨维度：费用 = 容量（要存满）；钩子收到真海纳尔世界和算好的落点；扣光 MRU；人没走（钩子不挪人）就要先走下来 */
    @GameTest(template = EMPTY_LARGE, batch = DIM_BATCH)
    public static void teleporterCrossDimension(GameTestHelper helper) {
        ServerLevel hoanna = realHoanna(helper);
        BlockPos floor = hoannaFloor(helper);
        prepareHoannaFloor(hoanna, floor);
        MagicalTeleporterBlockEntity be = buildTeleporter(helper);
        be.setTestOverrides(1000, null, 5, null, false);
        aim(be, GlobalPos.of(ECHoanna.LEVEL, floor), 999);
        ServerPlayer player = standOn(helper, be);
        List<ServerLevel> dests = new ArrayList<>();
        List<PortalInfo> infos = new ArrayList<>();
        ECTeleport.setTestDimensionChange(player.getUUID(), (moved, dest, info) -> {
            dests.add(dest);
            infos.add(info);
            return true;
        });
        helper.runAtTickTime(8, () -> {
            helper.assertTrue(be.cost() == 1000 && be.status() == Status.NO_MRU && dests.isEmpty(), "跨维度要存满：差 1 不蓄能，状态 " + be.status());
            be.getMRUStorage().setMRU(1000);
        });
        helper.runAtTickTime(20, () -> {
            ECTeleport.setTestDimensionChange(player.getUUID(), null);
            stepOff(helper, be);
            hoanna.setBlock(floor, Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(dests.size() == 1 && dests.get(0) == hoanna, "应换到真海纳尔世界一次，实际 " + dests.size() + " 次");
            Vec3 expected = new Vec3(floor.getX() + 0.5D, floor.getY() + 1D, floor.getZ() + 0.5D);
            helper.assertTrue(infos.get(0).pos.distanceToSqr(expected) < 1.0E-9D, "落点应是 " + expected + "，实际 " + infos.get(0).pos);
            helper.assertTrue(be.getMRUStorage().getMRU() == 0, "扣掉整个容量，剩 " + be.getMRUStorage().getMRU());
            helper.assertTrue(be.status() == Status.STEP_OFF && be.mustStepOff(player.getUUID()), "人还在上面：要先走下来，状态 " + be.status());
            helper.succeed();
        });
    }

    /** 不许跨维度（覆盖值）：拒绝（DIMENSION_DISABLED）、不扣、钩子不被调 */
    @GameTest(template = EMPTY_LARGE, batch = DIM_BATCH)
    public static void teleporterDimensionDisabled(GameTestHelper helper) {
        ServerLevel hoanna = realHoanna(helper);
        BlockPos floor = hoannaFloor(helper);
        prepareHoannaFloor(hoanna, floor);
        MagicalTeleporterBlockEntity be = buildTeleporter(helper);
        be.setTestOverrides(1000, null, 3, false, false);
        aim(be, GlobalPos.of(ECHoanna.LEVEL, floor), 1000);
        ServerPlayer player = standOn(helper, be);
        AtomicInteger calls = new AtomicInteger();
        ECTeleport.setTestDimensionChange(player.getUUID(), (moved, dest, info) -> {
            calls.incrementAndGet();
            return true;
        });
        helper.runAtTickTime(10, () -> {
            ECTeleport.setTestDimensionChange(player.getUUID(), null);
            stepOff(helper, be);
            hoanna.setBlock(floor, Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(be.status() == Status.DIMENSION_DISABLED && be.progress() == 0, "应拒绝：状态 " + be.status());
            helper.assertTrue(calls.get() == 0 && be.getMRUStorage().getMRU() == 1000, "不换维度、不扣");
            helper.succeed();
        });
    }

    /**
     * 海纳尔开关：门关着时从外面传不进海纳尔（HOANNA_CLOSED）；出发点算海纳尔时照常（同 6a 海纳尔之门：开关只挡从外面进去）。
     * 开关和「把主世界当海纳尔」都是全局覆盖，只在这一段同步代码里设、测、置回。
     */
    @GameTest(template = EMPTY_LARGE, batch = DIM_BATCH)
    public static void teleporterRespectsHoannaSwitch(GameTestHelper helper) {
        MagicalTeleporterBlockEntity be = buildTeleporter(helper);
        aim(be, GlobalPos.of(ECHoanna.LEVEL, hoannaFloor(helper)), 0);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        // 矩阵阶门另由 teleporterRankGate 测，这里设成九阶不受它影响
        PlayerDataEvents.get(player).setMatrixRank(MatrixRank.MAX);
        try {
            ECHoannaPortal.Config.setEnabledForTest(true);
            helper.assertTrue(be.checkTarget(player) == Status.READY, "门开着：READY，实际 " + be.checkTarget(player));
            ECHoannaPortal.Config.setEnabledForTest(false);
            helper.assertTrue(be.checkTarget(player) == Status.HOANNA_CLOSED, "门关着：HOANNA_CLOSED，实际 " + be.checkTarget(player));
            ECHoanna.setTestHoannaOverride(helper.getLevel());
            try {
                helper.assertTrue(be.checkTarget(player) == Status.READY, "出发点算海纳尔时照常，实际 " + be.checkTarget(player));
            } finally {
                ECHoanna.clearTestOverride();
            }
        } finally {
            ECHoannaPortal.Config.setEnabledForTest(null);
        }
        helper.succeed();
    }

    /**
     * 矩阵阶门（batch13.md §3.1.2）：从外面传进海纳尔要够阶，不够时 RANK_TOO_LOW；出发点已经在海纳尔、创造模式照常 READY。
     * 门由测试覆盖成 5 阶，不看服务器配置。
     */
    @GameTest(template = EMPTY)
    public static void teleporterRankGate(GameTestHelper helper) {
        MagicalTeleporterBlockEntity be = buildTeleporter(helper);
        aim(be, GlobalPos.of(ECHoanna.LEVEL, hoannaFloor(helper)), 0);
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        PlayerDataEvents.get(player).setMatrixRank(4);
        try {
            ECHoannaPortal.Config.setEnabledForTest(true);
            MatrixRank.setTestOverrides(null, 5, null, null);
            helper.assertTrue(be.checkTarget(player) == Status.RANK_TOO_LOW, "四阶：RANK_TOO_LOW，实际 " + be.checkTarget(player));
            ECHoanna.setTestHoannaOverride(helper.getLevel());
            try {
                helper.assertTrue(be.checkTarget(player) == Status.READY, "出发点已经在海纳尔时不判，实际 " + be.checkTarget(player));
            } finally {
                ECHoanna.clearTestOverride();
            }
            player.setGameMode(GameType.CREATIVE);
            helper.assertTrue(be.checkTarget(player) == Status.READY, "创造模式不挡，实际 " + be.checkTarget(player));
            player.setGameMode(GameType.SURVIVAL);
            PlayerDataEvents.get(player).setMatrixRank(5);
            helper.assertTrue(be.checkTarget(player) == Status.READY, "五阶：READY，实际 " + be.checkTarget(player));
        } finally {
            MatrixRank.clearTestOverrides();
            ECHoannaPortal.Config.setEnabledForTest(null);
        }
        helper.succeed();
    }

    /**
     * 有天花板的维度：下界 y = 127 的基岩顶（脚在 128，逻辑高度 128 以上）→ OUT_OF_WORLD（同原版紫颂果）；下面 y = 100 的石头照常 OK。
     * 用真下界，测试里同步加载那一个区块，改过的方块用完放回去。
     */
    @GameTest(template = EMPTY, batch = DIM_BATCH)
    public static void teleportNetherRoof(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "GameTest 服里应有下界");
        helper.assertTrue(nether.dimensionType().hasCeiling() && nether.getLogicalHeight() == 128, "测试前提：下界有天花板、逻辑高度 128");
        BlockPos abs = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos roof = new BlockPos(abs.getX(), nether.getMinBuildHeight() + nether.getLogicalHeight() - 1, abs.getZ());
        BlockPos low = new BlockPos(abs.getX(), 100, abs.getZ());
        nether.getChunk(roof.getX() >> 4, roof.getZ() >> 4);
        Map<BlockPos, BlockState> saved = new LinkedHashMap<>();
        for (BlockPos pos : List.of(roof, roof.above(), roof.above(2), low, low.above(), low.above(2))) {
            saved.put(pos, nether.getBlockState(pos));
        }
        try {
            nether.setBlock(roof, Blocks.BEDROCK.defaultBlockState(), 18);
            nether.setBlock(roof.above(), Blocks.AIR.defaultBlockState(), 18);
            nether.setBlock(roof.above(2), Blocks.AIR.defaultBlockState(), 18);
            nether.setBlock(low, Blocks.STONE.defaultBlockState(), 18);
            nether.setBlock(low.above(), Blocks.AIR.defaultBlockState(), 18);
            nether.setBlock(low.above(2), Blocks.AIR.defaultBlockState(), 18);
            ECTeleport.Result onRoof = ECTeleport.check(null, nether, roof, null).result();
            helper.assertTrue(onRoof == ECTeleport.Result.OUT_OF_WORLD, "下界基岩顶应 OUT_OF_WORLD，实际 " + onRoof);
            ECTeleport.Result below = ECTeleport.check(null, nether, low, null).result();
            helper.assertTrue(below == ECTeleport.Result.OK, "逻辑高度以内照常 OK，实际 " + below);
        } finally {
            for (Map.Entry<BlockPos, BlockState> entry : saved.entrySet()) {
                nether.setBlock(entry.getKey(), entry.getValue(), 18);
            }
        }
        helper.succeed();
    }

    /** 跨维度：EntityTravelToDimensionEvent 被取消 → changeDimension 返回 null → 不扣（CANCELLED）、要先走下来（真调 changeDimension，事件在任何改动之前取消） */
    @GameTest(template = EMPTY_LARGE, batch = DIM_BATCH)
    public static void teleporterCrossDimensionCancelled(GameTestHelper helper) {
        ServerLevel hoanna = realHoanna(helper);
        BlockPos floor = hoannaFloor(helper);
        prepareHoannaFloor(hoanna, floor);
        MagicalTeleporterBlockEntity be = buildTeleporter(helper);
        be.setTestOverrides(1000, null, 3, null, false);
        aim(be, GlobalPos.of(ECHoanna.LEVEL, floor), 1000);
        ServerPlayer player = standOn(helper, be);
        UUID id = player.getUUID();
        AtomicInteger seen = new AtomicInteger();
        Runnable unlisten = listen(EntityTravelToDimensionEvent.class, event -> {
            if (event.getEntity().getUUID().equals(id)) {
                seen.incrementAndGet();
                event.setCanceled(true);
            }
        });
        helper.runAtTickTime(10, () -> {
            unlisten.run();
            stepOff(helper, be);
            hoanna.setBlock(floor, Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(seen.get() == 1, "应发一次 EntityTravelToDimensionEvent，实际 " + seen.get());
            helper.assertTrue(player.level() == helper.getLevel(), "人还在原来的世界");
            helper.assertTrue(be.status() == Status.CANCELLED && be.getMRUStorage().getMRU() == 1000 && be.mustStepOff(id),
                    "被取消应不扣、要先走下来：状态 " + be.status() + "，MRU " + be.getMRUStorage().getMRU());
            helper.succeed();
        });
    }

    /** 到点目的地区块没加载（测试覆盖关掉预载）：等 {@value MagicalTeleporterBlockEntity#MAX_WAIT} 刻超时失败，不扣、不换维度、不触发同步加载 */
    @GameTest(template = EMPTY_LARGE, batch = DIM_BATCH, timeoutTicks = 200)
    public static void teleporterUnloadedTimesOut(GameTestHelper helper) {
        ServerLevel hoanna = realHoanna(helper);
        BlockPos far = hoannaFloor(helper).offset(2_000_000, 0, 0);
        MagicalTeleporterBlockEntity be = buildTeleporter(helper);
        be.setTestOverrides(1000, null, 1, null, true);
        aim(be, GlobalPos.of(ECHoanna.LEVEL, far), 1000);
        ServerPlayer player = standOn(helper, be);
        AtomicInteger calls = new AtomicInteger();
        ECTeleport.setTestDimensionChange(player.getUUID(), (moved, dest, info) -> {
            calls.incrementAndGet();
            return true;
        });
        helper.runAtTickTime(50, () -> helper.assertTrue(be.status() == Status.WAITING, "到点后等目的地：状态 " + be.status()));
        helper.succeedWhen(() -> {
            helper.assertTrue(be.status() == Status.TIMED_OUT, "应等到超时，状态 " + be.status());
            ECTeleport.setTestDimensionChange(player.getUUID(), null);
            stepOff(helper, be);
            helper.assertTrue(calls.get() == 0 && be.getMRUStorage().getMRU() == 1000, "超时不换维度、不扣");
            helper.assertTrue(hoanna.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null, "不应同步加载目的地区块");
            helper.assertTrue(helper.getTick() > MagicalTeleporterBlockEntity.MAX_WAIT, "至少等了 " + MagicalTeleporterBlockEntity.MAX_WAIT + " 刻");
        });
    }

    /**
     * 蓄能时目的地区块没加载：临时票把它加载出来，到点有确定的结论（这里落点是空中 → NO_FLOOR，不扣），不会超时；
     * 失败以后不再蓄能、不再续票，票过期（300 刻）后目的地区块卸载，不会一直挂着。
     */
    @GameTest(template = EMPTY_LARGE, batch = DIM_BATCH, timeoutTicks = 700)
    public static void teleporterPreloadsDestination(GameTestHelper helper) {
        ServerLevel hoanna = realHoanna(helper);
        paceRealTime(helper);
        BlockPos target = hoannaFloor(helper).offset(0, 0, 3000);
        helper.assertTrue(hoanna.getChunkSource().getChunkNow(target.getX() >> 4, target.getZ() >> 4) == null, "测试前提：目的地区块没加载");
        MagicalTeleporterBlockEntity be = buildTeleporter(helper);
        be.setTestOverrides(1000, null, 200, null, false);
        aim(be, GlobalPos.of(ECHoanna.LEVEL, target), 1000);
        ServerPlayer player = standOn(helper, be);
        AtomicInteger calls = new AtomicInteger();
        ECTeleport.setTestDimensionChange(player.getUUID(), (moved, dest, info) -> {
            calls.incrementAndGet();
            return true;
        });
        AtomicLong concluded = new AtomicLong(-1L);
        helper.onEachTick(() -> {
            if (concluded.get() < 0L && be.status() == Status.NO_FLOOR) {
                concluded.set(helper.getTick());
                ECTeleport.setTestDimensionChange(player.getUUID(), null);
                stepOff(helper, be);
                helper.assertTrue(hoanna.getChunkSource().getChunkNow(target.getX() >> 4, target.getZ() >> 4) != null, "临时票应把目的地区块加载出来");
                helper.assertTrue(calls.get() == 0 && be.getMRUStorage().getMRU() == 1000, "落点不合格：不换维度、不扣");
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(concluded.get() >= 0L, "到点应有结论 NO_FLOOR，状态 " + be.status());
            helper.assertTrue(helper.getTick() >= concluded.get() + 350, "结论之后再等 350 刻");
            helper.assertTrue(hoanna.getChunkSource().getChunkNow(target.getX() >> 4, target.getZ() >> 4) == null,
                    "不再蓄能以后票应过期、目的地区块卸载（票不该一直挂着）");
        });
    }

    /**
     * 蓄能比临时票的寿命（300 刻）还久时中途续票：蓄能刻数覆盖成 450，第 250~430 刻每一刻目的地区块都还在
     * （以前只在没加载时才加票，第 300 刻左右票过期、区块卸载，下一刻再加票再加载，来回存盘读盘）。
     */
    @GameTest(template = EMPTY_LARGE, batch = DIM_BATCH, timeoutTicks = 600)
    public static void teleporterRenewsTicket(GameTestHelper helper) {
        ServerLevel hoanna = realHoanna(helper);
        paceRealTime(helper);
        BlockPos target = hoannaFloor(helper).offset(0, 0, -5000);
        int cx = target.getX() >> 4;
        int cz = target.getZ() >> 4;
        helper.assertTrue(hoanna.getChunkSource().getChunkNow(cx, cz) == null, "测试前提：目的地区块没加载");
        MagicalTeleporterBlockEntity be = buildTeleporter(helper);
        be.setTestOverrides(1000, null, 450, null, false);
        aim(be, GlobalPos.of(ECHoanna.LEVEL, target), 1000);
        ServerPlayer player = standOn(helper, be);
        AtomicInteger calls = new AtomicInteger();
        ECTeleport.setTestDimensionChange(player.getUUID(), (moved, dest, info) -> {
            calls.incrementAndGet();
            return true;
        });
        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick >= 250 && tick <= 430) {
                helper.assertTrue(be.status() == Status.CHARGING, "第 " + tick + " 刻应还在蓄能，状态 " + be.status());
                helper.assertTrue(hoanna.getChunkSource().getChunkNow(cx, cz) != null, "第 " + tick + " 刻目的地区块卸载了：蓄能期间票应一直续着");
            }
        });
        helper.runAtTickTime(440, () -> {
            ECTeleport.setTestDimensionChange(player.getUUID(), null);
            stepOff(helper, be);
            helper.assertTrue(calls.get() == 0, "还没到点，不该换维度");
            helper.succeed();
        });
    }

    // ================================================================ 魔力熔炉

    /** 结构：摆全成形；少一台热力产能器（换成石头）立即判为不成形、报那一格 */
    @GameTest(template = EMPTY_LARGE)
    public static void furnaceStructure(GameTestHelper helper) {
        MagicalFurnaceBlockEntity be = buildFurnace(helper);
        helper.assertTrue(be.structure().formed(), "摆全了应成形，不符：" + be.mismatchOffset());
        helper.assertTrue(ECVoidMachines.FURNACE_STRUCTURE.rules().size() == 34, "结构应是 29 虚空石 + 4 热力产能器 + 机器本身，实际 "
                + ECVoidMachines.FURNACE_STRUCTURE.rules().size());
        BlockPos generator = new BlockPos(-2, 1, 2);
        helper.setBlock(FURNACE.offset(generator), Blocks.STONE);
        be.structure().checkNow(helper.getLevel(), be.getBlockPos(), Direction.NORTH);
        helper.assertTrue(!be.structure().formed() && generator.equals(be.mismatchOffset()), "少一台产能器应不成形、报 (-2, 1, 2)，实际 " + be.mismatchOffset());
        helper.succeed();
    }

    /** 3 个粗铁（几率覆盖为 0）：60 刻后 3 个铁锭在 (+2,+2,+2)、3 个炉渣在 (+2,+2,−2)，MRU 少 25 × 20 × 3 = 1500，输入用完、不给经验 */
    @GameTest(template = EMPTY_LARGE)
    public static void furnaceSmelts(GameTestHelper helper) {
        MagicalFurnaceBlockEntity be = buildFurnace(helper);
        be.setTestChances(0D, 0D);
        be.getMRUStorage().setMRU(10_000);
        ItemEntity input = dropOnFurnace(helper, new ItemStack(Items.RAW_IRON, 3));
        helper.runAtTickTime(30, () -> helper.assertTrue(countAt(helper, MagicalFurnaceBlockEntity.PRODUCT, Items.IRON_INGOT) == 1
                && input.getItem().getCount() == 2, "30 刻时应熔完 1 个：铁锭 " + countAt(helper, MagicalFurnaceBlockEntity.PRODUCT, Items.IRON_INGOT)
                + "，剩 " + input.getItem().getCount()));
        helper.runAtTickTime(70, () -> {
            helper.assertTrue(countAt(helper, MagicalFurnaceBlockEntity.PRODUCT, Items.IRON_INGOT) == 3, "应有 3 个铁锭在 (+2,+2,+2)");
            helper.assertTrue(countAt(helper, MagicalFurnaceBlockEntity.SLAG, ECMaterials.MAGICAL_SLAG.get()) == 3, "应有 3 个炉渣在 (+2,+2,−2)");
            helper.assertTrue(countAll(helper, MagicalFurnaceBlockEntity.BONUS_PRODUCT) == 0 && countAll(helper, MagicalFurnaceBlockEntity.BONUS_SLAG) == 0,
                    "几率为 0：另外两个角什么都没有");
            helper.assertTrue(!input.isAlive(), "输入用完应移除");
            helper.assertTrue(be.getMRUStorage().getMRU() == 10_000 - 1500, "MRU 应少 1500，剩 " + be.getMRUStorage().getMRU());
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class,
                    new AABB(helper.absolutePos(FURNACE)).inflate(4D)).isEmpty(), "不给经验");
            helper.assertTrue(be.status() == MagicalFurnaceBlockEntity.Status.IDLE && be.progress() == 0, "熔完空闲，状态 " + be.status());
            helper.succeed();
        });
    }

    /** 几率覆盖为 1：每件 2 份产物、2 个炉渣，落点照原作四个角 */
    @GameTest(template = EMPTY_LARGE)
    public static void furnaceBonusCorners(GameTestHelper helper) {
        MagicalFurnaceBlockEntity be = buildFurnace(helper);
        be.setTestChances(1D, 1D);
        be.getMRUStorage().setMRU(10_000);
        dropOnFurnace(helper, new ItemStack(Items.RAW_GOLD));
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(countAt(helper, MagicalFurnaceBlockEntity.PRODUCT, Items.GOLD_INGOT) == 1, "(+2,+2,+2) 一个金锭");
            helper.assertTrue(countAt(helper, MagicalFurnaceBlockEntity.BONUS_PRODUCT, Items.GOLD_INGOT) == 1, "(−2,+2,−2) 多出的一个金锭");
            helper.assertTrue(countAt(helper, MagicalFurnaceBlockEntity.SLAG, ECMaterials.MAGICAL_SLAG.get()) == 1, "(+2,+2,−2) 一个炉渣");
            helper.assertTrue(countAt(helper, MagicalFurnaceBlockEntity.BONUS_SLAG, ECMaterials.MAGICAL_SLAG.get()) == 1, "(−2,+2,+2) 多出的一个炉渣");
            helper.succeed();
        });
    }

    /** 没有熔炼配方的、指定了别人拾取的、永远捡不起来的（展示品）都不碰（机器没有主人）；不扣 MRU */
    @GameTest(template = EMPTY_LARGE)
    public static void furnaceSkipsForeignItems(GameTestHelper helper) {
        MagicalFurnaceBlockEntity be = buildFurnace(helper);
        be.setTestChances(0D, 0D);
        be.getMRUStorage().setMRU(10_000);
        ItemEntity stick = dropOnFurnace(helper, new ItemStack(Items.STICK));
        ItemEntity foreign = dropOnFurnace(helper, new ItemStack(Items.RAW_IRON));
        foreign.setTarget(UUID.randomUUID());
        ItemEntity display = dropOnFurnace(helper, new ItemStack(Items.RAW_COPPER));
        display.setNeverPickUp();
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(stick.isAlive() && foreign.isAlive() && display.isAlive()
                    && foreign.getItem().getCount() == 1 && display.getItem().getCount() == 1, "三件都不该被碰");
            helper.assertTrue(be.status() == MagicalFurnaceBlockEntity.Status.IDLE && be.getMRUStorage().getMRU() == 10_000,
                    "空闲、不扣 MRU：状态 " + be.status() + "，MRU " + be.getMRUStorage().getMRU());
            helper.assertTrue(countAll(helper, MagicalFurnaceBlockEntity.SLAG) == 0, "没有炉渣");
            helper.succeed();
        });
    }

    /** MRU 半路不够：进度冻结（NO_MRU）、补上接着走；换一种东西进度清零 */
    @GameTest(template = EMPTY_LARGE)
    public static void furnaceFreezesAndResets(GameTestHelper helper) {
        MagicalFurnaceBlockEntity be = buildFurnace(helper);
        be.setTestChances(0D, 0D);
        be.getMRUStorage().setMRU(250);
        ItemEntity iron = dropOnFurnace(helper, new ItemStack(Items.RAW_IRON));
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(be.progress() == 10 && be.status() == MagicalFurnaceBlockEntity.Status.NO_MRU,
                    "250 MRU 只够 10 刻，应冻结在 10：进度 " + be.progress() + "，状态 " + be.status());
            be.getMRUStorage().setMRU(1000);
        });
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(countAt(helper, MagicalFurnaceBlockEntity.PRODUCT, Items.IRON_INGOT) == 1 && !iron.isAlive(), "补上后接着熔完");
            helper.assertTrue(be.getMRUStorage().getMRU() == 750, "后一半只花 250，剩 " + be.getMRUStorage().getMRU());
            dropOnFurnace(helper, new ItemStack(Items.RAW_COPPER));
        });
        helper.runAtTickTime(48, () -> {
            helper.assertTrue(be.progress() > 3, "铜开始熔，进度 " + be.progress());
            for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(be.getBlockPos().above()).expandTowards(0D, 1D, 0D))) {
                if (entity.getItem().is(Items.RAW_COPPER)) {
                    entity.discard();
                }
            }
            dropOnFurnace(helper, new ItemStack(Items.RAW_GOLD));
        });
        helper.runAtTickTime(50, () -> {
            helper.assertTrue(be.progress() <= 2, "换成金：进度应从 0 重新算，实际 " + be.progress());
            helper.succeed();
        });
    }

    /** 出口 1 格内堆了 8 个掉落物：暂停（OUTPUT_FULL）、进度停在满格、不再生成实体、输入不减 */
    @GameTest(template = EMPTY_LARGE)
    public static void furnaceOutputFullPauses(GameTestHelper helper) {
        MagicalFurnaceBlockEntity be = buildFurnace(helper);
        be.setTestChances(0D, 0D);
        be.getMRUStorage().setMRU(10_000);
        Item[] fillers = {Items.DIRT, Items.COBBLESTONE, Items.SAND, Items.GRAVEL, Items.OAK_LOG, Items.APPLE, Items.FEATHER, Items.STRING};
        Vec3 corner = new Vec3(FURNACE.getX() + 2.5D, FURNACE.getY() + 2.15D, FURNACE.getZ() + 2.5D);
        for (Item filler : fillers) {
            drop(helper, corner, new ItemStack(filler));
        }
        ItemEntity input = dropOnFurnace(helper, new ItemStack(Items.RAW_IRON));
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(be.status() == MagicalFurnaceBlockEntity.Status.OUTPUT_FULL && be.progress() == be.ticksRequired(),
                    "应暂停在满格：状态 " + be.status() + "，进度 " + be.progress());
            helper.assertTrue(input.isAlive() && input.getItem().getCount() == 1, "输入不减");
            helper.assertTrue(countAt(helper, MagicalFurnaceBlockEntity.PRODUCT, Items.IRON_INGOT) == 0
                    && countAll(helper, MagicalFurnaceBlockEntity.SLAG) == 0, "不再生成产物和炉渣");
            helper.assertTrue(be.getMRUStorage().getMRU() == 10_000 - 500, "只花了熔一件的 500，剩 " + be.getMRUStorage().getMRU());
            helper.succeed();
        });
    }

    /** 有红石：停机（POWERED），东西不熔、MRU 不扣 */
    @GameTest(template = EMPTY_LARGE)
    public static void furnaceRedstoneStops(GameTestHelper helper) {
        MagicalFurnaceBlockEntity be = buildFurnace(helper);
        be.getMRUStorage().setMRU(10_000);
        helper.setBlock(FURNACE.east(), Blocks.REDSTONE_BLOCK);
        ItemEntity input = dropOnFurnace(helper, new ItemStack(Items.RAW_IRON));
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(be.status() == MagicalFurnaceBlockEntity.Status.POWERED && be.progress() == 0, "有红石应停机：状态 " + be.status());
            helper.assertTrue(input.isAlive() && be.getMRUStorage().getMRU() == 10_000, "不熔不扣");
            helper.succeed();
        });
    }

    /** 产物照配方的 assemble：配方产物数量为 2 → 出 2（用测试配方代替数据包） */
    @GameTest(template = EMPTY_LARGE)
    public static void furnaceUsesRecipeCount(GameTestHelper helper) {
        MagicalFurnaceBlockEntity be = buildFurnace(helper);
        be.setTestChances(0D, 0D);
        be.getMRUStorage().setMRU(10_000);
        be.setTestRecipe(new SmeltingRecipe(EssentialCraft5.id("test/magical_furnace_count"), "", CookingBookCategory.MISC,
                Ingredient.of(Items.STICK), new ItemStack(Items.DIAMOND, 2), 0F, 200));
        dropOnFurnace(helper, new ItemStack(Items.STICK));
        helper.runAtTickTime(30, () -> {
            be.setTestRecipe(null);
            helper.assertTrue(countAt(helper, MagicalFurnaceBlockEntity.PRODUCT, Items.DIAMOND) == 2, "应出 2 个，实际 "
                    + countAt(helper, MagicalFurnaceBlockEntity.PRODUCT, Items.DIAMOND));
            helper.succeed();
        });
    }

    /** 几率覆盖为 1 时，湿海绵和 #forge:dusts 里的东西不多出产物（炉渣照常两个）；标签默认成员对 */
    @GameTest(template = EMPTY_LARGE)
    public static void furnaceNoBonusTag(GameTestHelper helper) {
        helper.assertTrue(new ItemStack(Items.WET_SPONGE).is(ECVoidMachines.NO_BONUS) && new ItemStack(Items.REDSTONE).is(ECVoidMachines.NO_BONUS),
                "标签默认应有湿海绵和 #forge:dusts（红石粉）");
        helper.assertTrue(!new ItemStack(Items.RAW_IRON).is(ECVoidMachines.NO_BONUS), "粗铁不在标签里");
        MagicalFurnaceBlockEntity be = buildFurnace(helper);
        be.setTestChances(1D, 1D);
        be.getMRUStorage().setMRU(10_000);
        be.setTestRecipe(new SmeltingRecipe(EssentialCraft5.id("test/magical_furnace_dust"), "", CookingBookCategory.MISC,
                Ingredient.of(Items.REDSTONE), new ItemStack(Items.GLOWSTONE_DUST), 0F, 200));
        dropOnFurnace(helper, new ItemStack(Items.WET_SPONGE));
        dropOnFurnace(helper, new ItemStack(Items.REDSTONE));
        helper.runAtTickTime(55, () -> {
            be.setTestRecipe(null);
            helper.assertTrue(countAt(helper, MagicalFurnaceBlockEntity.PRODUCT, Items.SPONGE) == 1
                    && countAt(helper, MagicalFurnaceBlockEntity.PRODUCT, Items.GLOWSTONE_DUST) == 1, "两件各出一份");
            helper.assertTrue(countAll(helper, MagicalFurnaceBlockEntity.BONUS_PRODUCT) == 0, "排除标签里的不多出产物");
            helper.assertTrue(countAt(helper, MagicalFurnaceBlockEntity.SLAG, ECMaterials.MAGICAL_SLAG.get()) == 2
                    && countAt(helper, MagicalFurnaceBlockEntity.BONUS_SLAG, ECMaterials.MAGICAL_SLAG.get()) == 2, "炉渣几率不受影响：每件两个");
            helper.succeed();
        });
    }

    /** 输入范围照原作（从机器本格起两格高）：悬在顶面上方 1.5 格的东西（y + 2.5，不受重力）不碰 */
    @GameTest(template = EMPTY_LARGE)
    public static void furnaceInputRange(GameTestHelper helper) {
        MagicalFurnaceBlockEntity be = buildFurnace(helper);
        be.setTestChances(0D, 0D);
        be.getMRUStorage().setMRU(10_000);
        ItemEntity hovering = drop(helper, new Vec3(FURNACE.getX() + 0.5D, FURNACE.getY() + 2.5D, FURNACE.getZ() + 0.5D), new ItemStack(Items.RAW_IRON));
        hovering.setNoGravity(true);
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(hovering.isAlive() && hovering.getItem().getCount() == 1, "悬在两格以上的不该被熔");
            helper.assertTrue(be.status() == MagicalFurnaceBlockEntity.Status.IDLE && be.getMRUStorage().getMRU() == 10_000,
                    "空闲、不扣：状态 " + be.status() + "，MRU " + be.getMRUStorage().getMRU());
            hovering.discard();
            helper.succeed();
        });
    }

    /**
     * 配方查询有上限：顶上扔 70 种熔不了的普通物品 + 20 把弓（新建就带 Damage:0，算带 NBT），每刻真查配方 ≤ 4 次；
     * 都查过一遍以后不再查（普通物品进共用缓存，弓按掉落物记「查过、没有」）；之后扔一个粗铁，照样轮得到、熔得出来。
     */
    @GameTest(template = EMPTY_LARGE, timeoutTicks = 120)
    public static void furnaceRecipeLookupBudget(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MagicalFurnaceBlockEntity be = buildFurnace(helper);
        be.setTestChances(0D, 0D);
        be.getMRUStorage().setMRU(10_000);
        List<Item> plain = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (plain.size() >= 70) {
                break;
            }
            ItemStack stack = new ItemStack(item);
            if (stack.isEmpty() || stack.hasTag()
                    || level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SimpleContainer(stack), level).isPresent()) {
                continue;
            }
            plain.add(item);
        }
        helper.assertTrue(plain.size() == 70, "测试前提：找得到 70 种没有熔炼配方的普通物品，只有 " + plain.size());
        helper.assertTrue(new ItemStack(Items.BOW).hasTag(), "测试前提：弓新建就带 NBT");
        for (Item item : plain) {
            dropOnFurnace(helper, new ItemStack(item));
        }
        for (int i = 0; i < 20; i++) {
            dropOnFurnace(helper, new ItemStack(Items.BOW));
        }
        long[] last = {be.recipeLookupsForTest()};
        long[] quiet = {-1L};
        helper.onEachTick(() -> {
            long now = be.recipeLookupsForTest();
            long delta = now - last[0];
            last[0] = now;
            long tick = helper.getTick();
            if (tick >= 1 && tick < 50) {
                helper.assertTrue(delta <= MagicalFurnaceBlockEntity.MAX_LOOKUPS_PER_TICK,
                        "第 " + tick + " 刻真查了 " + delta + " 次配方，应 ≤ " + MagicalFurnaceBlockEntity.MAX_LOOKUPS_PER_TICK);
            }
        });
        helper.runAtTickTime(40, () -> quiet[0] = be.recipeLookupsForTest());
        helper.runAtTickTime(50, () -> {
            helper.assertTrue(be.recipeLookupsForTest() == quiet[0], "都查过一遍以后不该再查：第 40~50 刻又查了 " + (be.recipeLookupsForTest() - quiet[0]) + " 次");
            helper.assertTrue(be.status() == MagicalFurnaceBlockEntity.Status.IDLE, "熔不了的东西不熔：状态 " + be.status());
            dropOnFurnace(helper, new ItemStack(Items.RAW_IRON));
        });
        helper.runAtTickTime(90, () -> {
            helper.assertTrue(countAt(helper, MagicalFurnaceBlockEntity.PRODUCT, Items.IRON_INGOT) == 1, "粗铁应轮得到、熔得出来：铁锭 "
                    + countAt(helper, MagicalFurnaceBlockEntity.PRODUCT, Items.IRON_INGOT));
            helper.succeed();
        });
    }

    /** 共用配方缓存跟着配方表走：同一张表第二次不查；换一张表（数据包重载会换新的）重新查；换回来也重新查 */
    @GameTest(template = EMPTY, batch = RECIPE_BATCH)
    public static void furnaceRecipeCacheInvalidates(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ECVoidMachines.MAGICAL_FURNACE.get());
        MagicalFurnaceBlockEntity be = (MagicalFurnaceBlockEntity) helper.getBlockEntity(pos);
        RecipeManager real = level.getRecipeManager();
        ItemStack iron = new ItemStack(Items.RAW_IRON);
        helper.assertTrue(be.recipeForTest(level, real, iron) != null, "粗铁有熔炼配方");
        long n = be.recipeLookupsForTest();
        helper.assertTrue(be.recipeForTest(level, real, iron) != null && be.recipeLookupsForTest() == n, "第二次走缓存，不再查表");
        RecipeManager reloaded = new RecipeManager(ICondition.IContext.EMPTY);
        helper.assertTrue(be.recipeForTest(level, reloaded, iron) == null && be.recipeLookupsForTest() == n + 1,
                "换了配方表（空表）应作废缓存、重新查：查了 " + (be.recipeLookupsForTest() - n) + " 次");
        helper.assertTrue(be.recipeForTest(level, real, iron) != null && be.recipeLookupsForTest() == n + 2,
                "换回来也要重新查：查了 " + (be.recipeLookupsForTest() - n) + " 次");
        helper.succeed();
    }

    /**
     * 产物并进快到寿命的旧堆时，那一堆跟着续命：第 15 刻在东南角放一个年龄 5985 的铁锭（原版 6000 刻消失，也就是第 30 刻左右），
     * 熔炉第 20 刻左右把铁锭并进去；第 45 刻那一堆还在、2 个（以前连同并进去的一起在第 30 刻左右消失）。
     */
    @GameTest(template = EMPTY_LARGE)
    public static void furnaceMergeExtendsLifespan(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MagicalFurnaceBlockEntity be = buildFurnace(helper);
        be.setTestChances(0D, 0D);
        be.getMRUStorage().setMRU(10_000);
        dropOnFurnace(helper, new ItemStack(Items.RAW_IRON));
        AtomicReference<ItemEntity> pile = new AtomicReference<>();
        helper.runAtTickTime(15, () -> {
            Vec3 at = helper.absoluteVec(new Vec3(FURNACE.getX() + 2.5D, FURNACE.getY() + 2.15D, FURNACE.getZ() + 2.5D));
            ItemEntity old = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.IRON_INGOT), 0D, 0D, 0D);
            CompoundTag tag = old.saveWithoutId(new CompoundTag());
            tag.putShort("Age", (short) 5985);
            old.load(tag);
            helper.assertTrue(old.getAge() == 5985, "测试前提：年龄写进去了");
            level.addFreshEntity(old);
            pile.set(old);
        });
        helper.runAtTickTime(45, () -> {
            ItemEntity old = pile.get();
            helper.assertTrue(old.isAlive() && old.getItem().getCount() == 2, "旧堆应还在、并进了 1 个：存活 " + old.isAlive() + "，数量 " + old.getItem().getCount());
            helper.assertTrue(countAt(helper, MagicalFurnaceBlockEntity.PRODUCT, Items.IRON_INGOT) == 2, "东南角 2 个铁锭");
            helper.succeed();
        });
    }

    /**
     * 先看角再掷骰：西北角（多出的产物）堆满 8 个杂物。几率为 0 时那个角不会落东西，照常熔；
     * 几率很小但不为 0 时多出那份可能落过去，就暂停（以前只看掷中的角，掷不中就照常出，多出的那份悄悄没了）。
     */
    @GameTest(template = EMPTY_LARGE, timeoutTicks = 120)
    public static void furnaceBonusCornerFullPauses(GameTestHelper helper) {
        MagicalFurnaceBlockEntity be = buildFurnace(helper);
        be.setTestChances(0D, 0D);
        be.getMRUStorage().setMRU(10_000);
        Item[] fillers = {Items.DIRT, Items.COBBLESTONE, Items.SAND, Items.GRAVEL, Items.OAK_LOG, Items.APPLE, Items.FEATHER, Items.STRING};
        Vec3 corner = new Vec3(FURNACE.getX() - 1.5D, FURNACE.getY() + 2.15D, FURNACE.getZ() - 1.5D);
        for (Item filler : fillers) {
            drop(helper, corner, new ItemStack(filler));
        }
        ItemEntity iron = dropOnFurnace(helper, new ItemStack(Items.RAW_IRON));
        AtomicReference<ItemEntity> gold = new AtomicReference<>();
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(!iron.isAlive() && countAt(helper, MagicalFurnaceBlockEntity.PRODUCT, Items.IRON_INGOT) == 1,
                    "几率为 0：西北角满了也照常熔，状态 " + be.status());
            be.setTestChances(1.0E-9D, 0D);
            gold.set(dropOnFurnace(helper, new ItemStack(Items.RAW_GOLD)));
        });
        helper.runAtTickTime(70, () -> {
            helper.assertTrue(be.status() == MagicalFurnaceBlockEntity.Status.OUTPUT_FULL && be.progress() == be.ticksRequired(),
                    "多出那份可能落到的角满了应暂停：状态 " + be.status() + "，进度 " + be.progress());
            helper.assertTrue(gold.get().isAlive() && gold.get().getItem().getCount() == 1, "输入不减");
            helper.assertTrue(countAt(helper, MagicalFurnaceBlockEntity.PRODUCT, Items.GOLD_INGOT) == 0, "不出产物");
            helper.succeed();
        });
    }

    /** 指定给机器主人拾取的照常熔（只跳过指定给别人的） */
    @GameTest(template = EMPTY_LARGE)
    public static void furnaceSmeltsItemsMeantForOwner(GameTestHelper helper) {
        MagicalFurnaceBlockEntity be = buildFurnace(helper);
        be.setTestChances(0D, 0D);
        be.getMRUStorage().setMRU(10_000);
        UUID owner = UUID.randomUUID();
        be.setOwner(owner, "furnace_owner");
        ItemEntity mine = dropOnFurnace(helper, new ItemStack(Items.RAW_IRON));
        mine.setTarget(owner);
        ItemEntity foreign = dropOnFurnace(helper, new ItemStack(Items.RAW_COPPER));
        foreign.setTarget(UUID.randomUUID());
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(!mine.isAlive() && countAt(helper, MagicalFurnaceBlockEntity.PRODUCT, Items.IRON_INGOT) == 1, "指定给主人的应被熔");
            helper.assertTrue(foreign.isAlive() && countAt(helper, MagicalFurnaceBlockEntity.PRODUCT, Items.COPPER_INGOT) == 0, "指定给别人的不碰");
            helper.succeed();
        });
    }

    // ================================================================ MRU 跨维度收发器

    private static DimensionalTransceiverBlockEntity placeTransceiver(GameTestHelper helper, BlockPos pos, GlobalPos source) {
        helper.setBlock(pos, ECVoidMachines.DIMENSIONAL_TRANSCEIVER.get());
        DimensionalTransceiverBlockEntity be = (DimensionalTransceiverBlockEntity) helper.getBlockEntity(pos);
        be.getInventory().setStackInSlot(0, gem(source));
        return be;
    }

    private static void tick(GameTestHelper helper, MRUBlockEntity be) {
        MRUBlockEntity.tickServer(helper.getLevel(), be.getBlockPos(), be.getBlockState(), be);
    }

    /** 同维度不限距离：源头在 21 格高处，收发器拉得到（WORKING），普通机器（魔力熔炉，16 格）OUT_OF_RANGE；更新包不带物品栏 */
    @GameTest(template = EMPTY_LARGE)
    public static void transceiverUnlimitedRange(GameTestHelper helper) {
        BlockPos source = new BlockPos(2, 22, 2);
        helper.setBlock(source, ECBlocks.CREATIVE_MRU_STORAGE.get());
        MRUBlockEntity src = (MRUBlockEntity) helper.getBlockEntity(source);
        src.getMRUStorage().setMRU(src.getMRUStorage().getMaxMRU());
        DimensionalTransceiverBlockEntity be = placeTransceiver(helper, new BlockPos(2, 1, 2), here(helper, source));
        BlockPos furnacePos = new BlockPos(8, 1, 8);
        helper.setBlock(furnacePos, ECVoidMachines.MAGICAL_FURNACE.get());
        MagicalFurnaceBlockEntity furnace = (MagicalFurnaceBlockEntity) helper.getBlockEntity(furnacePos);
        furnace.getInventory().setStackInSlot(0, gem(here(helper, source)));
        tick(helper, be);
        tick(helper, furnace);
        try {
            helper.assertTrue(be.getLinkStatus() == MRULink.Status.WORKING && be.getMRUStorage().getMRU() == be.getMRUStorage().getMaxMRU(),
                    "收发器 21 格外应拉满：" + be.getLinkStatus() + " " + be.getMRUStorage().getMRU());
            helper.assertTrue(be.getMRUStorage().getMaxMRU() == 5000, "容量 5000");
            helper.assertTrue(furnace.getLinkStatus() == MRULink.Status.OUT_OF_RANGE && furnace.getMRUStorage().getMRU() == 0,
                    "普通机器 16 格外拉不到：" + furnace.getLinkStatus());
            helper.assertTrue(!be.getUpdateTag().contains("inventory"), "更新包不带物品栏（宝石记着源头坐标）");
        } finally {
            helper.setBlock(source, Blocks.AIR);
        }
        helper.succeed();
    }

    /** 跨维度：源头在真海纳尔世界、加了票的区块 → WORKING、MRU 进来；关掉跨维度（覆盖值）→ WRONG_DIMENSION */
    @GameTest(template = EMPTY, batch = DIM_BATCH)
    public static void transceiverCrossDimension(GameTestHelper helper) {
        ServerLevel hoanna = realHoanna(helper);
        BlockPos abs = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos source = new BlockPos(abs.getX(), HOANNA_Y - 10, abs.getZ());
        hoanna.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(source), 1, source);
        hoanna.setBlock(source, ECBlocks.CREATIVE_MRU_STORAGE.get().defaultBlockState(), 3);
        try {
            MRUBlockEntity src = (MRUBlockEntity) hoanna.getBlockEntity(source);
            helper.assertTrue(src != null, "测试前提：海纳尔那边的创造 MRU 源放下了");
            src.getMRUStorage().setMRU(src.getMRUStorage().getMaxMRU());
            DimensionalTransceiverBlockEntity be = placeTransceiver(helper, new BlockPos(2, 1, 2), GlobalPos.of(ECHoanna.LEVEL, source));
            be.setTestAllowDimensional(true);
            tick(helper, be);
            helper.assertTrue(be.getLinkStatus() == MRULink.Status.WORKING && be.getMRUStorage().getMRU() == 5000,
                    "跨维度应拉满：" + be.getLinkStatus() + " " + be.getMRUStorage().getMRU());
            be.getMRUStorage().setMRU(0);
            be.setTestAllowDimensional(false);
            tick(helper, be);
            helper.assertTrue(be.getLinkStatus() == MRULink.Status.WRONG_DIMENSION && be.getMRUStorage().getMRU() == 0,
                    "关掉跨维度应 WRONG_DIMENSION：" + be.getLinkStatus());
        } finally {
            hoanna.setBlock(source, Blocks.AIR.defaultBlockState(), 3);
        }
        helper.succeed();
    }

    /** 跨维度源头区块没加载：UNLOADED，拉完区块仍没加载（getChunkNow 为 null） */
    @GameTest(template = EMPTY, batch = DIM_BATCH)
    public static void transceiverUnloadedSource(GameTestHelper helper) {
        ServerLevel hoanna = realHoanna(helper);
        BlockPos far = helper.absolutePos(new BlockPos(2, 1, 2)).offset(-2_000_000, 0, 0);
        DimensionalTransceiverBlockEntity be = placeTransceiver(helper, new BlockPos(2, 1, 2), GlobalPos.of(ECHoanna.LEVEL, far));
        be.setTestAllowDimensional(true);
        tick(helper, be);
        helper.assertTrue(be.getLinkStatus() == MRULink.Status.UNLOADED && be.getMRUStorage().getMRU() == 0, "应 UNLOADED：" + be.getLinkStatus());
        helper.assertTrue(hoanna.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null, "不应加载源头区块");
        helper.succeed();
    }

    /** 收发器只放创造栏：没有配方产出它、哪个战利品表都不出它（它自己的方块掉落表除外）；物品提示写用途，最后一行「没有合成配方」 */
    @GameTest(template = EMPTY)
    public static void transceiverCreativeOnly(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Item item = ECVoidMachines.DIMENSIONAL_TRANSCEIVER.get().asItem();
        for (Recipe<?> recipe : server.getRecipeManager().getRecipes()) {
            helper.assertTrue(!recipe.getResultItem(server.registryAccess()).is(item), "配方 " + recipe.getId() + " 不应产出收发器");
        }
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        ResourceLocation own = EssentialCraft5.id("loot_tables/blocks/dimensional_transceiver.json");
        Map<ResourceLocation, Resource> tables = server.getResourceManager().listResources("loot_tables", file -> file.getPath().endsWith(".json"));
        helper.assertTrue(tables.containsKey(own), "测试前提：收发器有自己的方块掉落表（先跑 runData）");
        for (Map.Entry<ResourceLocation, Resource> entry : tables.entrySet()) {
            if (entry.getKey().equals(own)) {
                continue;
            }
            try (Reader reader = entry.getValue().openAsReader()) {
                StringBuilder text = new StringBuilder();
                char[] buffer = new char[4096];
                for (int n; (n = reader.read(buffer)) > 0; ) {
                    text.append(buffer, 0, n);
                }
                helper.assertTrue(!text.toString().contains(id), "战利品表 " + entry.getKey() + " 不应出收发器");
            } catch (IOException e) {
                helper.fail("读不了 " + entry.getKey() + "：" + e);
            }
        }
        List<Component> tooltip = new ArrayList<>();
        item.appendHoverText(new ItemStack(item), helper.getLevel(), tooltip, TooltipFlag.NORMAL);
        helper.assertTrue(tooltip.stream().anyMatch(line -> line.getContents() instanceof TranslatableContents contents
                && contents.getKey().equals(DimensionalTransceiverBlock.TOOLTIP_KEY)), "提示里应有用途一行，实际 " + tooltip);
        Component lastLine = tooltip.isEmpty() ? null : tooltip.get(tooltip.size() - 1);
        helper.assertTrue(lastLine != null && lastLine.getContents() instanceof TranslatableContents contents
                && contents.getKey().equals(CreativeOnly.TOOLTIP_KEY), "提示最后一行应是「没有合成配方」，实际 " + tooltip);
        helper.succeed();
    }

    // ================================================================ 配方

    /** 两台结构机器的配方：SRI / PCP / VEV，传送器的 P 是 #plates/magic、熔炉的 P 是魔力强化板；都要 1 个世界交互器、虚空核心、永恒物质 */
    @GameTest(template = EMPTY)
    public static void voidMachineRecipes(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Item fortified = BuiltInRegistries.ITEM.get(EssentialCraft5.id("magic_fortified_plating"));
        Item magicPlate = BuiltInRegistries.ITEM.getTag(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                EssentialCraft5.id("plates/magic"))).flatMap(set -> set.stream().findFirst()).map(Holder::value).orElse(Items.AIR);
        helper.assertTrue(magicPlate != Items.AIR, "测试前提：#essentialcraft5:plates/magic 有成员");
        for (String name : List.of("magical_teleporter", "magical_furnace")) {
            Recipe<?> recipe = server.getRecipeManager().byKey(EssentialCraft5.id("void_machines/" + name)).orElse(null);
            helper.assertTrue(recipe instanceof ShapedRecipe, name + " 应有有序配方");
            ShapedRecipe shaped = (ShapedRecipe) recipe;
            helper.assertTrue(shaped.getWidth() == 3 && shaped.getHeight() == 3, name + " 应是 3×3");
            helper.assertTrue(BuiltInRegistries.ITEM.getKey(shaped.getResultItem(server.registryAccess()).getItem()).equals(EssentialCraft5.id(name)), name + " 产物");
            List<Ingredient> in = shaped.getIngredients();
            helper.assertTrue(in.get(2).test(new ItemStack(BuiltInRegistries.ITEM.get(EssentialCraft5.id("world_interactor")))), name + " 右上是世界交互器");
            helper.assertTrue(in.get(4).test(new ItemStack(BuiltInRegistries.ITEM.get(EssentialCraft5.id("void_core")))), name + " 正中是虚空核心");
            helper.assertTrue(in.get(7).test(new ItemStack(BuiltInRegistries.ITEM.get(EssentialCraft5.id("matter_of_eternity")))), name + " 下中是永恒物质");
            boolean teleporter = name.equals("magical_teleporter");
            helper.assertTrue(in.get(3).test(new ItemStack(teleporter ? magicPlate : fortified))
                    && !in.get(3).test(new ItemStack(teleporter ? fortified : magicPlate)), name + " 两侧的板不对");
            for (Ingredient ingredient : in) {
                helper.assertTrue(!ingredient.isEmpty(), name + " 不应有空原料");
            }
        }
        helper.succeed();
    }
}
