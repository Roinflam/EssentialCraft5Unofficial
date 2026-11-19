package com.flameforge.essentialcraft5.dev;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ItemFilters;
import com.flameforge.essentialcraft5.block.DyeableBlock;
import com.flameforge.essentialcraft5.block.creative.InfiniteThermalSourceBlock;
import com.flameforge.essentialcraft5.blockentity.HeatGeneratorBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MRUBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MagicalChestBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MagicalFurnaceBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MagicalTeleporterBlockEntity;
import com.flameforge.essentialcraft5.blockentity.NaturalFurnaceBlockEntity;
import com.flameforge.essentialcraft5.blockentity.UltraHeatGeneratorBlockEntity;
import com.flameforge.essentialcraft5.blockentity.UltraNaturalFurnaceBlockEntity;
import com.flameforge.essentialcraft5.item.BoundGemItem;
import com.flameforge.essentialcraft5.item.BuilderJobs;
import com.flameforge.essentialcraft5.item.BuilderMode;
import com.flameforge.essentialcraft5.item.BuilderSelection;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.registry.ECBlocks;
import com.flameforge.essentialcraft5.registry.ECBuilder;
import com.flameforge.essentialcraft5.registry.ECCreative;
import com.flameforge.essentialcraft5.registry.ECGenerators;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECItems;
import com.flameforge.essentialcraft5.registry.ECLogistics;
import com.flameforge.essentialcraft5.registry.ECMagicianTable;
import com.flameforge.essentialcraft5.registry.ECMagmatic;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.registry.ECMithriline;
import com.flameforge.essentialcraft5.registry.ECRadiatingChamber;
import com.flameforge.essentialcraft5.registry.ECStorage;
import com.flameforge.essentialcraft5.registry.ECUltraGenerators;
import com.flameforge.essentialcraft5.registry.ECVoidMachines;
import com.flameforge.essentialcraft5.structure.StructurePattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.IntFunction;

/**
 * 展示厅的第六批 6b 部分（虚空机器，{@link DevShowroom} 调，只在 -PecShowroom / -PecShowroom6b 时跑）。
 * <p>
 * <b>场景</b>（04_scene_*，和前几批一样沿 X 排在方块排北边，镜头从南边朝北拍）：
 * 创世神之杖的选区框（主手拿着才画）、全部替换做完的样子、清一块 16³ 石头（4096 格正好是体积上限）开工前 / 施工中 / 做完，
 * 同时记客户端帧时间，再摆回去用「直接 setBlock、没有粒子」按同样速度清一遍作对照（batch6b §8，结果写进日志和 run/screenshots/ec5_builder_frametime.txt）；
 * 成形的魔力传送器（客户端假装在蓄能，看粒子）、成形的魔力熔炉（顶上扔一组原木，等它熔完看四个角）；
 * 普通 / 终极热力产能器各四面隔一格放岩浆（岩浆用玻璃围住）、普通 / 终极自然熔炉在烧东西（看两台 OBJ 换贴图对不对）；
 * 无尽道具：无尽 MRU 源和周围几台吃 MRU 的机器、四块热源围着的热力产能器、冷源旁的冷凝器、无尽 ESPE 源旁的米斯莱熔炉；
 * 最后真坐一次传送器往返：主世界 → 海纳尔（y 200 的平台）→ 主世界，每段拍蓄能和到达各一张，日志里记每段到没到、扣了多少。
 * <p>
 * <b>界面</b>（02_gui_*）：虚空箱、手持高级白名单 / 黑名单过滤器、快捷栏里五根记了不同方块的创世神之杖（物品格右下角的方块角标）、
 * 无尽道具和两件原作死物品的图标、场景里成形的传送器 / 熔炉、预热过的终极热力产能器、在烧原木的终极自然熔炉、被无尽 MRU 源补满的充能仓。
 * 只拍 6b（-PecShowroom6b）时另摆一排 6b 方块（全量时它们在方块排里），每台有界面的机器也拍一张。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class DevShowroomVoid {

    private DevShowroomVoid() {
    }

    /** 全量展示厅里本类占几个场景位（后面的海纳尔部分从 firstIndex + 这个数开始） */
    static final int SCENE_SLOTS = 8;

    /** 往返传送：两边都覆盖成这么多刻蓄能（配置默认 250 刻，展示厅不等那么久） */
    private static final int TRIP_CHARGE_TICKS = 60;
    /** 海纳尔那一头的平台高度 */
    private static final int TRIP_HOANNA_Y = 200;

    private static final DyeColor[] COLORS = DyeColor.values();

    // ================================================================ 入口

    /**
     * 服务端线程（进世界时）：从 {@code sceneBase(firstIndex)} 起摆静态场景，往返传送的海纳尔平台也在这里摆好（区块强制加载）。
     * {@code ownRow} = 只拍 6b：再摆一排 6b 方块并给每台有界面的机器加一张界面截图。
     */
    static void build(ServerLevel level, ServerPlayer player, IntFunction<BlockPos> sceneBase, int firstIndex, boolean ownRow) {
        buildSelectionScene(level, player, sceneBase.apply(firstIndex));
        buildReplaceScene(level, player, sceneBase.apply(firstIndex + 1));
        buildClearCubeScene(level, player, sceneBase.apply(firstIndex + 2));
        buildTeleporterScene(level, sceneBase.apply(firstIndex + 3));
        buildFurnaceScene(level, sceneBase.apply(firstIndex + 4));
        buildUltraGeneratorsScene(level, sceneBase.apply(firstIndex + 5));
        buildCreativeScene(level, sceneBase.apply(firstIndex + 6));
        buildTrip(level, sceneBase.apply(firstIndex + 7));
        buildScreens(level, sceneBase.apply(firstIndex + 1).offset(0, 0, 12));
        if (ownRow) {
            buildOwnRow(level, player, sceneBase.apply(firstIndex + 8));
        }
    }

    // ================================================================ 小工具

    private static void scene(String name, @Nullable java.util.function.Consumer<ServerPlayer> prepare, @Nullable Vec3 eye, @Nullable Vec3 target,
                              int delay, @Nullable java.util.function.Consumer<Minecraft> client, @Nullable java.util.function.Predicate<Minecraft> ready) {
        DevShowroom.scenes.add(new DevShowroom.SceneShot(name, prepare, eye, target, delay, client, ready));
    }

    private static Vec3 at(BlockPos base, double dx, double dy, double dz) {
        return DevShowroom.at(base, dx, dy, dz);
    }

    /** 照结构表摆出全部必需格子（虚空石、护板染几种颜色），返回机器那一格 */
    private static BlockPos buildStructure(ServerLevel level, StructurePattern pattern, BlockPos anchor) {
        int index = 0;
        for (var entry : pattern.rules().entrySet()) {
            if (entry.getValue().isOptional() || entry.getValue().isAir()) {
                continue;
            }
            BlockState state = entry.getValue().displayState();
            if (state.hasProperty(DyeableBlock.COLOR)) {
                state = state.setValue(DyeableBlock.COLOR, COLORS[(index++ * 5) % COLORS.length]);
            }
            level.setBlockAndUpdate(StructurePattern.toWorld(anchor, entry.getKey(), Direction.NORTH), state);
        }
        return anchor;
    }

    private static ItemStack wand(BuilderMode mode, @Nullable BlockPos first, @Nullable BlockPos second, ServerLevel level, @Nullable Block block) {
        ItemStack stack = MRUStaffItem.full(ECBuilder.item(mode));
        if (first != null && second != null) {
            BuilderSelection.setFirst(stack, first, level.dimension());
            BuilderSelection.setSecond(stack, second);
        }
        if (block != null) {
            BuilderSelection.setStoredBlock(stack, ForgeRegistries.BLOCKS.getKey(block));
        }
        return stack;
    }

    private static ItemStack boundGem(GlobalPos target) {
        ItemStack gem = new ItemStack(ECItems.BOUND_GEM.get());
        BoundGemItem.bind(gem, target, false);
        return gem;
    }

    /** 放进当前选中的快捷栏格（两端都是 0 号），之后的动作照「主手拿着」算 */
    private static void hold(ServerPlayer player, ItemStack stack) {
        player.getInventory().setItem(player.getInventory().selected, stack);
    }

    // ================================================================ 创世神之杖

    /**
     * 选区框：6 × 4 × 6 的选区，里面摆几格石头和一根橡木原木（看框是不是隔着方块也看得见），选定橡木木板；
     * 拍的那一刻主手拿着这根填充魔杖（框只在主手、同维度时画）。
     */
    private static void buildSelectionScene(ServerLevel level, ServerPlayer player, BlockPos base) {
        BlockPos a = base.offset(0, 0, -5);
        BlockPos b = base.offset(5, 3, 0);
        level.setBlockAndUpdate(base.offset(1, 0, -1), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(base.offset(2, 0, -1), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(base.offset(3, 0, -3), Blocks.OAK_LOG.defaultBlockState());
        level.setBlockAndUpdate(base.offset(3, 1, -3), Blocks.OAK_LOG.defaultBlockState());
        level.setBlockAndUpdate(base.offset(4, 0, -4), Blocks.STONE_BRICKS.defaultBlockState());
        ItemStack stack = wand(BuilderMode.FILL, a, b, level, Blocks.OAK_PLANKS);
        scene("builder_selection", p -> hold(p, stack.copy()), at(base, 9.5, 6.5, 7.5), at(base, 2.5, 1, -2.5), DevShowroom.FIRST_SCENE_WAIT, null, null);
    }

    /** 全部替换：5 × 3 × 5 里空气、石头、泥土各一些，选定橡木木板，创造模式开工（不用材料、不给掉落），做完再拍 */
    private static void buildReplaceScene(ServerLevel level, ServerPlayer player, BlockPos base) {
        BlockPos a = base.offset(0, 0, -4);
        BlockPos b = base.offset(4, 2, 0);
        for (BlockPos pos : BlockPos.betweenClosed(a, b)) {
            int h = Math.floorMod(pos.getX() * 7 + pos.getZ() * 13 + pos.getY() * 5, 5);
            BlockState state = h == 0 ? Blocks.STONE.defaultBlockState() : h == 1 ? Blocks.DIRT.defaultBlockState()
                    : h == 2 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
            level.setBlockAndUpdate(pos, state);
        }
        // 旁边留一块没动的对照
        BlockPos c = base.offset(7, 0, -4);
        for (BlockPos pos : BlockPos.betweenClosed(c, c.offset(4, 2, 4))) {
            int h = Math.floorMod(pos.getX() * 7 + pos.getZ() * 13 + pos.getY() * 5, 5);
            level.setBlockAndUpdate(pos, h == 0 ? Blocks.STONE.defaultBlockState() : h == 1 ? Blocks.DIRT.defaultBlockState()
                    : h == 2 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        ItemStack stack = wand(BuilderMode.REPLACE_ALL, a, b, level, Blocks.OAK_PLANKS);
        java.util.function.Consumer<ServerPlayer> start = p -> {
            hold(p, stack.copy());
            p.teleportTo(base.getX() + 5.5, base.getY() + 4, base.getZ() + 6.5);
            BuilderJobs.StartResult result = BuilderJobs.start(p, InteractionHand.MAIN_HAND);
            EssentialCraft5.LOGGER.info("[showroom] 全部替换开工：{}", result);
        };
        scene("builder_replace_all", start, at(base, 5.5, 5.5, 8.5), at(base, 5.5, 0.5, -2), 50, null, null);
    }

    // ---------------------------------------------------------------- 清 16³ 与帧时间

    private static final int CUBE = 16;
    /** 对照组每刻删几格（和创世神之杖每任务每刻的默认上限一样） */
    private static final int PLAIN_PER_TICK = 64;
    /** 帧记录：开着时每帧记一条 {时刻, 帧间隔} */
    private static volatile boolean recording;
    private static final List<long[]> FRAMES = new ArrayList<>();
    private static long lastFrame;
    /** A = 创世神之杖清（每格 destroyBlock，有破碎粒子和声音）；B = 同样的顺序和速度直接 setBlock 成空气（没有粒子），作对照 */
    private static volatile long startA;
    private static volatile long endA;
    private static volatile long startB;
    private static volatile long endB;
    private static volatile int startTick;
    @Nullable
    private static volatile UUID jobPlayer;
    private static final ArrayDeque<BlockPos> PLAIN_QUEUE = new ArrayDeque<>();
    @Nullable
    private static volatile ServerLevel plainLevel;

    @SubscribeEvent
    public static void onVoidShowroomRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !recording) {
            return;
        }
        long now = System.nanoTime();
        if (lastFrame != 0L) {
            synchronized (FRAMES) {
                FRAMES.add(new long[] {now, now - lastFrame});
            }
        }
        lastFrame = now;
    }

    @SubscribeEvent
    public static void onVoidShowroomServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        UUID player = jobPlayer;
        if (player != null && endA == 0L && !BuilderJobs.isRunning(player)) {
            endA = System.nanoTime();
            EssentialCraft5.LOGGER.info("[showroom] 清 16³（创世神之杖）做完：{}，服务端 {} 刻", BuilderJobs.lastResult(player),
                    event.getServer().getTickCount() - startTick);
            jobPlayer = null;
        }
        ServerLevel level = plainLevel;
        if (level != null) {
            for (int i = 0; i < PLAIN_PER_TICK && !PLAIN_QUEUE.isEmpty(); i++) {
                level.setBlock(PLAIN_QUEUE.poll(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            if (PLAIN_QUEUE.isEmpty()) {
                endB = System.nanoTime();
                plainLevel = null;
                EssentialCraft5.LOGGER.info("[showroom] 清 16³（对照：直接 setBlock，无粒子）做完，服务端 {} 刻", event.getServer().getTickCount() - startTick);
            }
        }
    }

    /**
     * 清一块 16³ 的石头（4096 格 = 体积上限），客户端记帧时间（batch6b §8：决定要不要给 ECProtection 加不出粒子的删除方式）。
     * A：创造模式拿清除魔杖开工（不扣 MRU、不给掉落；每格走 harvestGuarded 的批量写法，不出破碎效果，每任务每刻只补发至多 4 格的粒子和声音。
 * 6b 审查前这里每格 destroyBlock、每格都有粒子和声音，整段平均 21~45 ms，对照组 B 约 8.5 ms）；
     * B：把石头摆回去、等区块重建完，再按同样的顺序（从上往下）、同样的速度（每刻 {@value #PLAIN_PER_TICK} 格）直接 setBlock 成空气，没有粒子。
     * 两组在同一次游戏里、同一个机位，开工前 60 帧、开工后 60 帧和整段施工各算一次，写进日志和 run/screenshots/ec5_builder_frametime.txt。
     */
    private static void buildClearCubeScene(ServerLevel level, ServerPlayer player, BlockPos base) {
        BlockPos a = base.offset(0, 0, -(CUBE - 1));
        BlockPos b = base.offset(CUBE - 1, CUBE - 1, 0);
        fillCube(level, a, b);
        ItemStack stack = wand(BuilderMode.CLEAR, a, b, level, null);
        Vec3 eye = at(base, 7.5, 14, 22);
        Vec3 target = at(base, 7.5, 6, -7);
        scene("builder_cube_before", p -> hold(p, stack.copy()), eye, target, 60, mc -> {
            synchronized (FRAMES) {
                FRAMES.clear();
            }
            lastFrame = 0L;
            startA = 0L;
            endA = 0L;
            startB = 0L;
            endB = 0L;
            recording = true;
        }, null);
        scene("builder_cube_clearing", p -> {
            jobPlayer = p.getUUID();
            startTick = p.getServer().getTickCount();
            startA = System.nanoTime();
            BuilderJobs.StartResult result = BuilderJobs.start(p, InteractionHand.MAIN_HAND);
            EssentialCraft5.LOGGER.info("[showroom] 清 16³ 开工：{}", result);
        }, eye, target, 25, null, null);
        scene("builder_cube_cleared", null, eye, target, 40, null, mc -> endA != 0L);
        scene("builder_cube_cleared_side", null, at(base, 22, 10, 6), at(base, 7.5, 2, -7), 10, null, null);
        // 对照组：摆回去（手上的魔杖收掉，选区框不画），等区块重建完再开始
        scene("builder_cube_rebuilt", p -> {
            hold(p, ItemStack.EMPTY);
            fillCube(p.serverLevel(), a, b);
        }, eye, target, 100, null, null);
        scene("builder_cube_clearing_plain", p -> {
            PLAIN_QUEUE.clear();
            for (int y = b.getY(); y >= a.getY(); y--) {
                for (int x = a.getX(); x <= b.getX(); x++) {
                    for (int z = a.getZ(); z <= b.getZ(); z++) {
                        PLAIN_QUEUE.add(new BlockPos(x, y, z));
                    }
                }
            }
            startTick = p.getServer().getTickCount();
            startB = System.nanoTime();
            plainLevel = p.serverLevel();
        }, eye, target, 25, null, null);
        scene("builder_cube_cleared_plain", null, eye, target, 40, null, mc -> endB != 0L);
        scene("builder_cube_report", null, eye, target, 5, mc -> {
            recording = false;
            reportFrames(mc);
        }, null);
    }

    private static void fillCube(ServerLevel level, BlockPos a, BlockPos b) {
        for (BlockPos pos : BlockPos.betweenClosed(a, b)) {
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static void reportFrames(Minecraft mc) {
        List<long[]> frames;
        synchronized (FRAMES) {
            frames = new ArrayList<>(FRAMES);
        }
        StringBuilder text = new StringBuilder();
        text.append("builder clear 16^3 frame times (client, ms)").append(System.lineSeparator());
        text.append(String.format(Locale.ROOT, "fps limit %d, vsync %s, window %dx%d, gui scale %d%n", mc.options.framerateLimit().get(),
                mc.options.enableVsync().get(), mc.getWindow().getWidth(), mc.getWindow().getHeight(), mc.options.guiScale().get()));
        text.append(window("A wand (batch removal, <=4 effects/tick)", frames, 0L, startA, endA));
        text.append(window("B plain setBlock (no particles)", frames, endA, startB, endB));
        EssentialCraft5.LOGGER.info("[showroom] 创世神之杖清 16³ 帧时间：{}", text.toString().replace(System.lineSeparator(), " | "));
        try {
            File dir = new File(mc.gameDirectory, "screenshots");
            dir.mkdirs();
            Files.writeString(new File(dir, "ec5_builder_frametime.txt").toPath(), text.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            EssentialCraft5.LOGGER.warn("[showroom] 写不了帧时间文件", e);
        }
    }

    /** 一组：开工前 60 帧（只取 after 之后的）、开工后 60 帧、整段施工 */
    private static String window(String name, List<long[]> frames, long after, long start, long end) {
        List<Long> before = new ArrayList<>();
        List<Long> first = new ArrayList<>();
        List<Long> whole = new ArrayList<>();
        for (long[] f : frames) {
            if (f[0] > after && f[0] < start) {
                before.add(f[1]);
            } else if (start != 0L && f[0] >= start && (end == 0L || f[0] <= end)) {
                whole.add(f[1]);
                if (first.size() < 60) {
                    first.add(f[1]);
                }
            }
        }
        List<Long> last60 = before.subList(Math.max(0, before.size() - 60), before.size());
        return String.format(Locale.ROOT, "%s: job %s ms wall%n  before (last 60 frames): %s%n  after start (first 60 frames): %s%n  whole job (%d frames): %s%n",
                name, start == 0L || end == 0L ? "?" : String.valueOf((end - start) / 1_000_000L), stats(last60), stats(first), whole.size(), stats(whole));
    }

    private static String stats(List<Long> nanos) {
        if (nanos.isEmpty()) {
            return "n/a";
        }
        long[] sorted = nanos.stream().mapToLong(Long::longValue).sorted().toArray();
        double sum = Arrays.stream(sorted).sum();
        return String.format(Locale.ROOT, "n=%d mean %.2f p50 %.2f p95 %.2f p99 %.2f max %.2f", sorted.length,
                sum / sorted.length / 1e6, sorted[sorted.length / 2] / 1e6, sorted[(int) Math.min(sorted.length - 1, Math.floor(sorted.length * 0.95))] / 1e6,
                sorted[(int) Math.min(sorted.length - 1, Math.floor(sorted.length * 0.99))] / 1e6, sorted[sorted.length - 1] / 1e6);
    }

    // ================================================================ 传送器与熔炉

    @Nullable
    private static BlockPos sceneTeleporter;
    @Nullable
    private static BlockPos sceneFurnace;

    /** 成形的魔力传送器：落点宝石绑在南边一块石头上、MRU 加满；客户端假装蓄能到 200 刻（{@link MagicalTeleporterBlockEntity#setShowroomCharging}），不真把镜头传走 */
    private static void buildTeleporterScene(ServerLevel level, BlockPos base) {
        BlockPos machine = base.offset(3, 0, -3);
        buildStructure(level, ECVoidMachines.TELEPORTER_STRUCTURE, machine);
        BlockPos floor = machine.offset(0, 0, 5);
        level.setBlockAndUpdate(floor, Blocks.POLISHED_ANDESITE.defaultBlockState());
        if (level.getBlockEntity(machine) instanceof MagicalTeleporterBlockEntity be) {
            be.getInventory().setStackInSlot(MagicalTeleporterBlockEntity.SLOT_DESTINATION, boundGem(GlobalPos.of(level.dimension(), floor)));
            be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
            be.structure().checkNow(level, machine, Direction.NORTH);
            sceneTeleporter = machine;
        } else {
            EssentialCraft5.LOGGER.warn("[showroom] {} 没有魔力传送器方块实体", machine);
        }
        BlockPos pos = machine.immutable();
        scene("magical_teleporter", p -> hold(p, ItemStack.EMPTY), at(base, 8.5, 6.5, 5.5), at(base, 3.5, 1, -3), 30,
                mc -> MagicalTeleporterBlockEntity.setShowroomCharging(pos, 200), null);
        scene("magical_teleporter_close", null, at(base, 3.5, 3.2, 2.2), at(base, 3.5, 1.2, -3), 15, null, null);
    }

    /** 成形的魔力熔炉：MRU 加满，顶上扔一组原木和几个粗铁，等熔完看东南角（产物）、东北角（炉渣），几率开着时另外两角也可能有 */
    private static void buildFurnaceScene(ServerLevel level, BlockPos base) {
        BlockPos machine = base.offset(3, 1, -3);
        buildStructure(level, ECVoidMachines.FURNACE_STRUCTURE, machine);
        if (level.getBlockEntity(machine) instanceof MagicalFurnaceBlockEntity be) {
            be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
            be.structure().checkNow(level, machine, Direction.NORTH);
            sceneFurnace = machine;
        } else {
            EssentialCraft5.LOGGER.warn("[showroom] {} 没有魔力熔炉方块实体", machine);
        }
        BlockPos pos = machine.immutable();
        java.util.function.Consumer<ServerPlayer> feed = p -> {
            ServerLevel l = p.serverLevel();
            if (l.getBlockEntity(pos) instanceof MagicalFurnaceBlockEntity be) {
                be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
            }
            for (ItemStack stack : new ItemStack[] {new ItemStack(Items.OAK_LOG, 4), new ItemStack(Items.RAW_IRON, 3)}) {
                ItemEntity drop = new ItemEntity(l, pos.getX() + 0.5D, pos.getY() + 1.2D, pos.getZ() + 0.5D, stack, 0D, 0D, 0D);
                l.addFreshEntity(drop);
            }
        };
        scene("magical_furnace_feed", feed, at(base, 7.5, 5.5, 2.5), at(base, 3.5, 1.8, -3), 30, mc -> MagicalTeleporterBlockEntity.clearShowroomCharging(), null);
        scene("magical_furnace_done", null, at(base, 7.5, 5.5, 2.5), at(base, 3.5, 1.8, -3), 220, null, null);
    }

    // ================================================================ 终极产能器

    @Nullable
    private static BlockPos sceneUltraHeat;
    @Nullable
    private static BlockPos sceneUltraNatural;

    /**
     * 普通 / 终极热力产能器各四面隔一格放岩浆（岩浆四周用玻璃围住，不流出来、热源照算），终极那台先设成 12000 度、放煤；
     * 旁边普通自然熔炉烧虞美人、终极自然熔炉烧橡木原木。看 OBJ 换 MTL 以后的 UV、终极自然熔炉的透明像素、燃烧粒子。
     */
    private static void buildUltraGeneratorsScene(ServerLevel level, BlockPos base) {
        BlockPos heat = base.offset(1, 0, -3);
        BlockPos ultraHeat = base.offset(8, 0, -3);
        for (BlockPos gen : new BlockPos[] {heat, ultraHeat}) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos lava = gen.relative(direction, 2);
                for (Direction around : Direction.Plane.HORIZONTAL) {
                    level.setBlockAndUpdate(lava.relative(around), Blocks.GLASS.defaultBlockState());
                }
                level.setBlockAndUpdate(lava, Blocks.LAVA.defaultBlockState());
            }
        }
        level.setBlockAndUpdate(heat, ECGenerators.HEAT_GENERATOR.get().defaultBlockState());
        level.setBlockAndUpdate(ultraHeat, ECUltraGenerators.ULTRA_HEAT_GENERATOR.get().defaultBlockState());
        if (level.getBlockEntity(heat) instanceof HeatGeneratorBlockEntity be) {
            be.getInventory().setStackInSlot(HeatGeneratorBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL, 16));
        }
        if (level.getBlockEntity(ultraHeat) instanceof UltraHeatGeneratorBlockEntity be) {
            be.setHeatForTest(12000D);
            be.getInventory().setStackInSlot(UltraHeatGeneratorBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL, 16));
            sceneUltraHeat = ultraHeat;
        }
        BlockPos natural = base.offset(13, 0, -1);
        BlockPos ultraNatural = base.offset(15, 0, -1);
        level.setBlockAndUpdate(natural, ECGenerators.NATURAL_FURNACE.get().defaultBlockState());
        level.setBlockAndUpdate(ultraNatural, ECUltraGenerators.ULTRA_NATURAL_FURNACE.get().defaultBlockState());
        if (level.getBlockEntity(natural) instanceof NaturalFurnaceBlockEntity be) {
            be.getInventory().setStackInSlot(NaturalFurnaceBlockEntity.SLOT_PLANT, new ItemStack(Items.POPPY, 16));
        }
        if (level.getBlockEntity(ultraNatural) instanceof UltraNaturalFurnaceBlockEntity be) {
            be.getInventory().setStackInSlot(UltraNaturalFurnaceBlockEntity.SLOT_PLANT, new ItemStack(Items.OAK_LOG, 16));
            sceneUltraNatural = ultraNatural;
        }
        scene("ultra_generators", null, at(base, 8, 6, 6.5), at(base, 8, 0.5, -2.5), 40, null, null);
        scene("ultra_generators_close", null, at(base, 12.5, 2.2, 2.6), at(base, 14, 0.6, -1), 20, null, null);
        scene("ultra_heat_generator_side", null, at(base, 11.5, 2.5, 0.8), at(base, 8.5, 0.5, -3), 15, null, null);
    }

    // ================================================================ 无尽道具

    @Nullable
    private static BlockPos sceneChargingChamber;

    /**
     * 无尽 MRU 源居中，周围 3~4 格摆 4 台靠绑定宝石吃 MRU 的机器（放下时 MRU 清零，源每 20 刻补一轮）；
     * 东边一台热力产能器四面隔一格各一块无尽冷热源（热源，放煤），再往东一块切成冷源的冷热源挨着冷凝器、无尽 ESPE 源挨着米斯莱熔炉。
     */
    private static void buildCreativeScene(ServerLevel level, BlockPos base) {
        BlockPos source = base.offset(4, 0, -4);
        level.setBlockAndUpdate(source, ECCreative.INFINITE_MRU_SOURCE.get().defaultBlockState());
        List<RegistryObject<? extends Block>> consumers = List.of(ECBlocks.CHARGING_CHAMBER, ECMagicianTable.MAGICIAN_TABLE,
                ECRadiatingChamber.RADIATING_CHAMBER, ECMagmatic.FORTIFIED_MAGMATIC_FURNACE);
        BlockPos[] spots = {source.offset(-3, 0, 0), source.offset(3, 0, 0), source.offset(0, 0, -3), source.offset(0, 0, 3)};
        for (int i = 0; i < consumers.size(); i++) {
            level.setBlockAndUpdate(spots[i], consumers.get(i).get().defaultBlockState());
            if (level.getBlockEntity(spots[i]) instanceof MRUBlockEntity be) {
                be.getMRUStorage().setMRU(0);
            }
        }
        sceneChargingChamber = spots[0].immutable();
        BlockPos heat = base.offset(12, 0, -4);
        level.setBlockAndUpdate(heat, ECGenerators.HEAT_GENERATOR.get().defaultBlockState());
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            level.setBlockAndUpdate(heat.relative(direction, 2), ECCreative.INFINITE_THERMAL_SOURCE.get().defaultBlockState());
        }
        if (level.getBlockEntity(heat) instanceof HeatGeneratorBlockEntity be) {
            be.getInventory().setStackInSlot(HeatGeneratorBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL, 16));
        }
        BlockPos cold = base.offset(17, 0, -2);
        level.setBlockAndUpdate(cold, ECCreative.INFINITE_THERMAL_SOURCE.get().defaultBlockState().setValue(InfiniteThermalSourceBlock.COLD, true));
        level.setBlockAndUpdate(cold.east(), ECGenerators.COLD_DISTILLATOR.get().defaultBlockState());
        BlockPos espe = base.offset(17, 0, -6);
        level.setBlockAndUpdate(espe, ECCreative.INFINITE_ESPE_SOURCE.get().defaultBlockState());
        BlockState mithriline = ECMithriline.MITHRILINE_FURNACE.get().defaultBlockState();
        level.setBlockAndUpdate(espe.east(), mithriline);
        scene("creative_sources", null, at(base, 10, 7, 7.5), at(base, 10, 0.5, -4), 60, null, null);
        scene("creative_sources_close", null, at(base, 4.5, 2.4, -0.6), at(base, 4.5, 0.6, -4), 15, null, null);
        scene("creative_thermal_espe", null, at(base, 15, 4.5, 2.5), at(base, 15, 0.5, -4), 15, null, null);
    }

    // ================================================================ 往返传送

    @Nullable
    private static BlockPos tripOverworld;
    @Nullable
    private static BlockPos tripHoanna;

    /**
     * 主世界一座传送器（落点 = 海纳尔 y 200 平台上的一块石头），海纳尔同 x、z 的平台上一座回程传送器（落点 = 主世界传送器南边 4 格的一块石头）。
     * 两台都覆盖成 {@value #TRIP_CHARGE_TICKS} 刻蓄能、MRU 加满（跨维度费用 = 容量）。玩家真站上去（不飞），到点由传送器自己传。
     */
    private static void buildTrip(ServerLevel level, BlockPos base) {
        MinecraftServer server = level.getServer();
        ServerLevel hoanna = ECHoanna.level(server);
        if (hoanna == null) {
            EssentialCraft5.LOGGER.warn("[showroom] 没有海纳尔世界，跳过传送器往返");
            return;
        }
        BlockPos home = base.offset(3, 0, -3);
        buildStructure(level, ECVoidMachines.TELEPORTER_STRUCTURE, home);
        BlockPos homeFloor = home.offset(0, 0, 4);
        level.setBlockAndUpdate(homeFloor, Blocks.POLISHED_ANDESITE.defaultBlockState());
        BlockPos away = new BlockPos(home.getX(), TRIP_HOANNA_Y, home.getZ());
        ChunkPos chunk = new ChunkPos(away);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                hoanna.setChunkForced(chunk.x + dx, chunk.z + dz, true);
                hoanna.getChunk(chunk.x + dx, chunk.z + dz);
            }
        }
        for (BlockPos pos : BlockPos.betweenClosed(away.offset(-4, -1, -4), away.offset(4, -1, 6))) {
            hoanna.setBlockAndUpdate(pos, ECMaterials.FORTIFIED_STONE.get().defaultBlockState());
        }
        for (BlockPos pos : BlockPos.betweenClosed(away.offset(-4, 0, -4), away.offset(4, 4, 6))) {
            hoanna.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        buildStructure(hoanna, ECVoidMachines.TELEPORTER_STRUCTURE, away);
        BlockPos awayFloor = away.offset(0, 0, 4);
        hoanna.setBlockAndUpdate(awayFloor, Blocks.POLISHED_ANDESITE.defaultBlockState());
        aimTeleporter(level, home, GlobalPos.of(ECHoanna.LEVEL, awayFloor));
        aimTeleporter(hoanna, away, GlobalPos.of(level.dimension(), homeFloor));
        tripOverworld = home.immutable();
        tripHoanna = away.immutable();

        BlockPos h = home.immutable();
        BlockPos w = away.immutable();
        scene("teleporter_trip_charging", p -> standOn(p, level, h, 180F), null, null, 40, null, null);
        scene("teleporter_trip_arrived_hoanna", null, null, null, 120,
                null, mc -> mc.level != null && mc.level.dimension() == ECHoanna.LEVEL && mc.player != null && mc.player.onGround());
        scene("teleporter_trip_return_charging", p -> {
            logTrip(p, "去程", level, h);
            // 刚被送过来 5 秒内踩上别的传送器要先走下来一次；这里直接把人放到回程那台上，先清掉到达记录
            MagicalTeleporterBlockEntity.forgetArrival(p.getUUID());
            standOn(p, hoanna, w, 180F);
        }, null, null, 40, null, null);
        scene("teleporter_trip_back_home", null, null, null, 120,
                null, mc -> mc.level != null && mc.level.dimension() == level.dimension() && mc.player != null && mc.player.onGround());
        scene("teleporter_trip_done", p -> logTrip(p, "回程", hoanna, w), at(base, 9, 5.5, 7), at(base, 3.5, 0.8, -1), 20, null, null);
    }

    private static void aimTeleporter(ServerLevel level, BlockPos machine, GlobalPos target) {
        if (level.getBlockEntity(machine) instanceof MagicalTeleporterBlockEntity be) {
            be.getInventory().setStackInSlot(MagicalTeleporterBlockEntity.SLOT_DESTINATION, boundGem(target));
            be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
            be.setTestOverrides(null, null, TRIP_CHARGE_TICKS, null, false);
            be.structure().checkNow(level, machine, Direction.NORTH);
        } else {
            EssentialCraft5.LOGGER.warn("[showroom] {} {} 没有魔力传送器方块实体", level.dimension().location(), machine);
        }
    }

    /** 不飞、站到传送器正上方，面朝 yaw；MRU 补满 */
    private static void standOn(ServerPlayer player, ServerLevel level, BlockPos machine, float yaw) {
        if (level.getBlockEntity(machine) instanceof MagicalTeleporterBlockEntity be) {
            be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
        }
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        player.teleportTo(level, machine.getX() + 0.5D, machine.getY() + 1D, machine.getZ() + 0.5D, yaw, 25F);
    }

    private static void logTrip(ServerPlayer player, String leg, ServerLevel from, BlockPos machine) {
        String status = "?";
        int mru = -1;
        if (from.getBlockEntity(machine) instanceof MagicalTeleporterBlockEntity be) {
            status = be.status().name();
            mru = be.getMRUStorage().getMRU();
        }
        EssentialCraft5.LOGGER.info("[showroom] 传送器往返 {}：玩家在 {} {}，出发那台状态 {}、剩 MRU {}", leg, player.level().dimension().location(),
                player.blockPosition().toShortString(), status, mru);
    }

    // ================================================================ 界面

    private static void buildScreens(ServerLevel level, BlockPos chestPos) {
        level.setBlockAndUpdate(chestPos, ECStorage.VOID_CHEST.get().defaultBlockState());
        if (level.getBlockEntity(chestPos) instanceof MagicalChestBlockEntity chest) {
            chest.getInventory().setStackInSlot(0, new ItemStack(Items.DIAMOND, 64));
            chest.getInventory().setStackInSlot(12, new ItemStack(Items.IRON_INGOT, 32));
            chest.getInventory().setStackInSlot(58, new ItemStack(Items.REDSTONE, 48));
            chest.getInventory().setStackInSlot(116, new ItemStack(Items.GOLDEN_APPLE, 3));
        } else {
            EssentialCraft5.LOGGER.warn("[showroom] {} 没有虚空箱方块实体", chestPos);
        }
        BlockPos voidPos = chestPos.immutable();
        DevShowroom.screens.add(new DevShowroom.ScreenShot("void_chest",
                p -> p.teleportTo(voidPos.getX() + 0.5, voidPos.getY(), voidPos.getZ() - 1.5),
                p -> {
                    if (p.serverLevel().getBlockEntity(voidPos) instanceof MagicalChestBlockEntity chest) {
                        NetworkHooks.openScreen(p, chest, voidPos);
                    }
                }));

        ItemStack white = new ItemStack(ECLogistics.ADVANCED_WHITELIST_FILTER.get());
        ItemFilters.setSamples(white, List.of(new ItemStack(Items.IRON_ORE), new ItemStack(Items.OAK_LOG), new ItemStack(Items.IRON_INGOT)));
        ItemFilters.setFlag(white, ItemFilters.Flag.IGNORE_DAMAGE, true);
        ItemFilters.setFlag(white, ItemFilters.Flag.MATCH_TAGS, true);
        DevShowroom.screens.add(held("advanced_whitelist_filter", white));
        ItemStack black = new ItemStack(ECLogistics.ADVANCED_BLACKLIST_FILTER.get());
        ItemFilters.setSamples(black, List.of(new ItemStack(Items.COBBLESTONE)));
        DevShowroom.screens.add(held("advanced_blacklist_filter", black));

        // 快捷栏 0~4：五种模式的魔杖各记一种方块（物品格右下角画半尺寸的方块）
        Block[] stored = {Blocks.STONE, Blocks.OAK_PLANKS, Blocks.GLASS, Blocks.SAND, Blocks.BRICKS};
        List<ItemStack> wands = new ArrayList<>();
        for (int i = 0; i < BuilderMode.values().length && i < stored.length; i++) {
            wands.add(wand(BuilderMode.values()[i], null, null, level, stored[i]));
        }
        DevShowroom.screens.add(new DevShowroom.ScreenShot("builder_overlay", p -> {
            for (int i = 0; i < 9; i++) {
                p.getInventory().setItem(i, i < wands.size() ? wands.get(i).copy() : ItemStack.EMPTY);
            }
            p.getInventory().selected = 8;
        }, p -> Minecraft.getInstance().execute(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.setScreen(new InventoryScreen(mc.player));
            }
        })));
        // 无尽道具与两件原作死物品的图标（动画贴图停在某一帧；晶体的耐久条是彩虹色）
        Item[] creative = {ECCreative.INFINITE_MRU_CRYSTAL.get(), ECCreative.CREATIVE_WARD.get(), ECCreative.INFINITE_MRU_SOURCE.get().asItem(),
                ECCreative.INFINITE_ESPE_SOURCE.get().asItem(), ECCreative.INFINITE_THERMAL_SOURCE.get().asItem(), ECVoidMachines.DIMENSIONAL_TRANSCEIVER.get().asItem(),
                ECMaterials.LAPIS_CORE.get(), ECMaterials.GLITCHY_CORE.get(), ECStorage.VOID_CHEST.get().asItem()};
        DevShowroom.screens.add(new DevShowroom.ScreenShot("creative_items_hotbar", p -> {
            for (int i = 0; i < 9; i++) {
                p.getInventory().setItem(i, i < creative.length ? new ItemStack(creative[i]) : ItemStack.EMPTY);
            }
            p.getInventory().selected = 8;
        }, p -> Minecraft.getInstance().execute(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.setScreen(new InventoryScreen(mc.player));
            }
        })));

        machineScreen("magical_teleporter_scene", () -> sceneTeleporter, p -> {
            if (sceneTeleporter != null && p.serverLevel().getBlockEntity(sceneTeleporter) instanceof MagicalTeleporterBlockEntity be) {
                be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
            }
        });
        machineScreen("magical_furnace_scene", () -> sceneFurnace, null);
        machineScreen("ultra_heat_generator_scene", () -> sceneUltraHeat, null);
        machineScreen("ultra_natural_furnace_scene", () -> sceneUltraNatural, null);
        machineScreen("creative_refilled_charging_chamber", () -> sceneChargingChamber, null);
    }

    private static DevShowroom.ScreenShot held(String name, ItemStack stack) {
        return new DevShowroom.ScreenShot(name,
                p -> p.getInventory().setItem(p.getInventory().selected, stack.copy()),
                p -> p.getMainHandItem().use(p.level(), p, InteractionHand.MAIN_HAND));
    }

    /** 场景里某台机器的界面（站到它北边 1.5 格；pos 在场景摆好后才有） */
    private static void machineScreen(String name, java.util.function.Supplier<BlockPos> pos,
                                      @Nullable java.util.function.Consumer<ServerPlayer> before) {
        DevShowroom.screens.add(new DevShowroom.ScreenShot(name, p -> {
            BlockPos at = pos.get();
            if (at != null) {
                p.teleportTo(p.server.overworld(), at.getX() + 0.5, at.getY(), at.getZ() - 1.5, p.getYRot(), p.getXRot());
            }
            if (before != null) {
                before.accept(p);
            }
        }, p -> {
            BlockPos at = pos.get();
            if (at != null && p.serverLevel().getBlockEntity(at) instanceof MRUBlockEntity be) {
                NetworkHooks.openScreen(p, be, at);
            } else {
                EssentialCraft5.LOGGER.warn("[showroom] {}：{} 没有机器", name, at);
            }
        }));
    }

    // ================================================================ 只拍 6b 时的一排方块

    /** 6b 的方块排成一排（拍一张），有界面的各拍一张（MRU 充到一半）。两个无尽源不放这里：它们会把旁边的机器补满，在 04_scene_creative_sources 里拍 */
    private static void buildOwnRow(ServerLevel level, ServerPlayer player, BlockPos base) {
        List<RegistryObject<? extends Block>> blocks = List.of(ECVoidMachines.MAGICAL_TELEPORTER, ECVoidMachines.MAGICAL_FURNACE,
                ECVoidMachines.DIMENSIONAL_TRANSCEIVER, ECUltraGenerators.ULTRA_HEAT_GENERATOR, ECUltraGenerators.ULTRA_NATURAL_FURNACE,
                ECMagmatic.VOID_MAGMATIC_FURNACE, ECStorage.VOID_CHEST, ECCreative.INFINITE_THERMAL_SOURCE);
        for (int i = 0; i < blocks.size(); i++) {
            BlockPos pos = base.offset(i * 2, 0, -2);
            Block block = blocks.get(i).get();
            BlockState state = block.defaultBlockState();
            level.setBlockAndUpdate(pos, state);
            block.setPlacedBy(level, pos, state, player, new ItemStack(block));
            if (level.getBlockEntity(pos) instanceof MRUBlockEntity be && be.createMenu(0, player.getInventory(), player) != null) {
                be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU() / 2);
                BlockPos machine = pos.immutable();
                String name = blocks.get(i).getId().getPath();
                DevShowroom.screens.add(new DevShowroom.ScreenShot(name,
                        p -> p.teleportTo(machine.getX() + 0.5, machine.getY(), machine.getZ() - 1.5),
                        p -> {
                            if (p.serverLevel().getBlockEntity(machine) instanceof MRUBlockEntity machineBe) {
                                NetworkHooks.openScreen(p, machineBe, machine);
                            }
                        }));
            }
        }
        scene("void_row", null, at(base, 9.5, 3.5, 5.5), at(base, 9.5, 0.5, -2), 30, null, null);
        scene("void_row_close", null, at(base, 7.5, 1.8, 0.8), at(base, 7.5, 0.6, -2), 15, null, null);
    }
}
