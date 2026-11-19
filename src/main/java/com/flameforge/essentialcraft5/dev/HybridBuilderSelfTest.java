package com.flameforge.essentialcraft5.dev;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.gametest.BuilderTestContainers;
import com.flameforge.essentialcraft5.item.BuilderJobs;
import com.flameforge.essentialcraft5.item.BuilderMaterials;
import com.flameforge.essentialcraft5.item.BuilderMode;
import com.flameforge.essentialcraft5.item.BuilderSelection;
import com.flameforge.essentialcraft5.item.BuilderShape;
import com.flameforge.essentialcraft5.item.BuilderStatus;
import com.flameforge.essentialcraft5.item.BuilderTier;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.registry.ECBuilder;
import com.flameforge.essentialcraft5.registry.ECCreative;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * 创世神之杖在 Mohist 系混合端上、对着<b>真的领地插件</b>（Residence）的自检（tools/plan/builder_upgrade.md §10 的上服前实测）。
 * 开关 {@code -Dessentialcraft5.hybridBuilderSelftest=true}（由混合端自检脚本（不随源码公开）的 {@code --builder} 打开；
 * 和 {@link HybridSelfTest} 互斥，它那套用测试插件按坐标模拟的领地，这套不设 {@code ec5selftest.origin}，模拟的区一块都不生效）。
 * <p>
 * 和 {@link HybridSelfTest} 不同，这里的施工是<b>按真实服务器刻</b>推进的（魔杖自己的刻处理器照常跑，探测预算、暂停、全服格数和耗时预算都是真的），
 * 每组是几段「等到条件成立」的步骤，每刻在 END 的最后看一次。测试插件 EC5Probe 1.8.0 起负责两件事：
 * 按 {@value #PROP_CLAIM_REQUEST} 用 Residence 的接口圈地 / 删地 / 设单项权限；{@value #PROP_COUNT} 打开时数放置 / 破坏事件（{@value #PROP_COUNTS}）。
 * 玩家是脱离网络的真 {@link ServerPlayer}（同 {@link HybridSelfTest}），连接换成记下提示类包的 {@link RecordingConnection}，
 * 分得出是本模组的提示（翻译键 {@code message.essentialcraft5.*}）还是领地插件发的。
 *
 * <h2>场地（原点 O = 出生点 + (100, 0, 100)，y = 200）</h2>
 * Residence 领地：{@value #OWN_CLAIM}（主人的，O + x 0..15 / y 0..15 / z 0..15）、{@value #OTHER_CLAIM}（另一名玩家的，x 32..47）、
 * {@value #PLACEONLY_CLAIM}（另一名玩家的，但给主人单设 destroy=true、place=false：拆得掉、放不上，x 80..95）、
 * {@value #TAKEN_CLAIM}（B06 中途才圈给另一名玩家，x 64..71 / y 0..7 / z 0..7）。其余是野外。
 *
 * <h2>用例</h2>
 * B01 整片在别人领地里：全被拒、不扣料不扣 MRU，被拒到上限停；B02 自己领地里两点和铺面都放成；B03 跨领地边界只放野外那半；
 * B04 材料在背包非快捷栏 / 只在副手 / 在随身容器 / 副手拿着剑时，自己领地照放、别人领地照拒、材料不少（记插件看到的手和物品）；
 * B05 清除 / 全部替换在别人领地里一格不动，对照自己领地照清；B06 撤销：自己领地能撤、退料，铺完被别人圈走后撤不了、不退；
 * B07 只拒放置的领地（拆得掉放不上）：先探后放的探测都放行、每格真放都被拒，数插件收到几条、发给玩家几条提示；
 * B08 出生点保护、世界边界外、建筑高度外不放；B09 一次几千格（一人 4096 格、四人各 2048 格）时每刻耗时（只记录）；
 * B10 先探后放对空气格的探测事件（HIGHEST 看到的方块、MONITOR 收不收得到）；B11 撤销时日志类插件记下的破坏（玩家、方块、坐标）；
 * B12 每格探测 / 真放的耗时（野外、自己领地、别人领地，只记录）；B13 三阶 96×96 一人 / 四人压测（只记录）；
 * B14 数料、状态包、实体遮挡、MRU 扣费、32×4×32 开工确认的单项耗时（只记录）。
 * 日志：{@code [EC5自检] Bnn 开始}、{@code [EC5自检] Bnn … 期望 … 实际 … 通过 / 失败 / 记录}，最后 {@code [EC5自检] 完成 通过 n/m}。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class HybridBuilderSelfTest {

    public static final String PROPERTY = "essentialcraft5.hybridBuilderSelftest";
    private static final boolean ENABLED = Boolean.getBoolean(PROPERTY);
    /**
     * 设成地皮世界名（如 plotw）时改跑 PlotSquared 那组 P01~P05（不跑 B01~B09）：测试插件把 (1;1) 给主人、(2;1) 给另一人，
     * 在这两块地皮和中间的路上施工。服务器要装 PlotSquared 7 并已有这个地皮世界（Multiverse 建的）。
     */
    static final String PROP_PLOTS = "essentialcraft5.hybridBuilderPlots";

    /** 和 EC5Probe 1.9.0 约定的系统属性（改了要两边一起改） */
    static final String PROP_COUNT = "ec5probe.count";
    static final String PROP_COUNTS = "ec5probe.counts";
    static final String PROP_BREAK_CELLS = "ec5probe.breakCells";
    static final String PROP_BREAK_LOG = "ec5probe.breakLog";
    static final String PROP_CLAIM_REQUEST = "ec5probe.claimRequest";
    static final String PROP_CLAIM_REPLY = "ec5probe.claimReply";
    static final String PROP_PROBE = "ec5probe.enabled";

    static final String OWN_CLAIM = "ec5b_own";
    static final String OTHER_CLAIM = "ec5b_other";
    static final String PLACEONLY_CLAIM = "ec5b_placeonly";
    static final String TAKEN_CLAIM = "ec5b_taken";

    private static final String TAG = "[EC5自检]";
    private static final int START_DELAY = 100;
    /** 魔杖自带的 MRU（{@link MRUStaffItem#MAX_MRU}） */
    private static final int FULL = MRUStaffItem.MAX_MRU;

    @Nullable
    private static Run run;
    @Nullable
    private static PlotBoot plotBoot;
    private static int countdown = -1;
    private static long tickStartNanos;
    private static long endStartNanos;

    private HybridBuilderSelfTest() {
    }

    // ==================================================================
    // 生命周期
    // ==================================================================

    @SubscribeEvent
    public static void onBuilderSelfTestStarted(ServerStartedEvent event) {
        if (!ENABLED) {
            return;
        }
        countdown = START_DELAY;
        EssentialCraft5.LOGGER.info("{} 创世神之杖混合端自检已启用（-D{}=true），{} tick 后开始；测试插件 EC5Probe {}", TAG, PROPERTY, START_DELAY,
                System.getProperty(PROP_PROBE, "没有加载"));
    }

    /** START 的最前面记一刻的起点；END 的最前面记 END 处理器的起点（魔杖的刻处理器是 NORMAL，在这两次之间） */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBuilderSelfTestTickFirst(TickEvent.ServerTickEvent event) {
        if (!ENABLED) {
            return;
        }
        if (event.phase == TickEvent.Phase.START) {
            tickStartNanos = System.nanoTime();
        } else {
            endStartNanos = System.nanoTime();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBuilderSelfTestTickLast(TickEvent.ServerTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END) {
            return;
        }
        long now = System.nanoTime();
        if (countdown > 0 && --countdown == 0) {
            String plotWorld = System.getProperty(PROP_PLOTS, "");
            if (plotWorld.isEmpty()) {
                run = new Run(event.getServer());
            } else {
                plotBoot = new PlotBoot(event.getServer(), plotWorld);
            }
        }
        if (plotBoot != null) {
            try {
                if (plotBoot.tick()) {
                    plotBoot = null;
                }
            } catch (RuntimeException | LinkageError e) {
                EssentialCraft5.LOGGER.error("{} 地皮准备抛出异常", TAG, e);
                EssentialCraft5.LOGGER.info("{} 完成 通过 0/1（地皮准备失败）", TAG);
                plotBoot = null;
            }
            return;
        }
        Run current = run;
        if (current == null) {
            return;
        }
        current.sample(now - tickStartNanos, now - endStartNanos);
        boolean done;
        try {
            done = current.tick();
        } catch (RuntimeException | LinkageError e) {
            EssentialCraft5.LOGGER.error("{} 自检本身抛出异常，收尾后放弃", TAG, e);
            current.teardown();
            EssentialCraft5.LOGGER.info("{} 完成 通过 0/1（自检抛出异常）", TAG);
            done = true;
        }
        if (done) {
            run = null;
        }
    }

    @SubscribeEvent
    public static void onBuilderSelfTestStopping(ServerStoppingEvent event) {
        Run current = run;
        run = null;
        countdown = -1;
        if (current != null) {
            EssentialCraft5.LOGGER.warn("{} 关服时还没跑完，收尾后放弃", TAG);
            current.teardown();
        }
    }

    // ==================================================================
    // 一次自检
    // ==================================================================

    private record Outcome(@Nullable Boolean pass, String expected, String actual, List<String> problems) {
    }

    /** 一组：几段依次做的步骤（每段每刻调一次，返回 true 进下一段），做完了判 */
    private record Case(String id, String title, int timeoutTicks, List<BooleanSupplier> steps, Supplier<Outcome> judge) {
    }

    /** 一刻的耗时（毫秒）：整刻、END 处理器那一段（魔杖在这里面） */
    private record TickSample(double total, double end) {
    }

    /**
     * 地皮那组开跑前：找到地皮世界，请测试插件把两块地皮分给主人和另一人，拿到两块的绝对范围后建 {@link Run}。
     * 世界按维度名的路径认（Mohist 给 Bukkit 建的世界起的维度名，路径就是世界名）。
     */
    private static final class PlotBoot {
        private static final String SEQ = "plots";
        private final MinecraftServer server;
        private final String world;
        @Nullable
        private ServerLevel level;
        private int ticks;
        private boolean sent;

        PlotBoot(MinecraftServer server, String world) {
            this.server = server;
            this.world = world;
        }

        /** @return 准备完了（成功建了 Run，或放弃） */
        boolean tick() {
            if (level == null) {
                List<String> names = new ArrayList<>();
                for (ServerLevel l : server.getAllLevels()) {
                    names.add(l.dimension().location().toString());
                    if (l.dimension().location().getPath().equals(world)) {
                        level = l;
                    }
                }
                if (level == null) {
                    EssentialCraft5.LOGGER.error("{} 找不到地皮世界 {}；现有维度 {}", TAG, world, names);
                    EssentialCraft5.LOGGER.info("{} 完成 通过 0/1（找不到地皮世界）", TAG);
                    return true;
                }
            }
            if (!sent) {
                sent = true;
                System.setProperty(PROP_CLAIM_REQUEST, SEQ + "\nplot-setup " + world + " " + HybridSelfTest.OWNER_ID + " " + HybridSelfTest.OTHER_ID);
                return false;
            }
            String reply = System.getProperty(PROP_CLAIM_REPLY, "");
            if (!reply.startsWith(SEQ + " ")) {
                if (++ticks > 200) {
                    EssentialCraft5.LOGGER.error("{} 地皮准备超时（测试插件没有回音）", TAG);
                    EssentialCraft5.LOGGER.info("{} 完成 通过 0/1（地皮准备失败）", TAG);
                    return true;
                }
                return false;
            }
            EssentialCraft5.LOGGER.info("{} 地皮：{}", TAG, reply);
            BoundingBox own = parseBox(reply, "own=");
            BoundingBox other = parseBox(reply, "other=");
            if (reply.startsWith(SEQ + " error") || own == null || other == null) {
                EssentialCraft5.LOGGER.info("{} 完成 通过 0/1（地皮准备失败）", TAG);
                return true;
            }
            run = new Run(server, level, own, other);
            return true;
        }

        /** 从回复里取 key 后面的 x1,y1,z1..x2,y2,z2 */
        @Nullable
        private static BoundingBox parseBox(String reply, String key) {
            int at = reply.indexOf(key);
            if (at < 0) {
                return null;
            }
            String text = reply.substring(at + key.length()).split(" ")[0];
            String[] ends = text.split("\\.\\.");
            if (ends.length != 2) {
                return null;
            }
            String[] a = ends[0].split(",");
            String[] b = ends[1].split(",");
            try {
                return new BoundingBox(Math.min(Integer.parseInt(a[0]), Integer.parseInt(b[0])), Math.min(Integer.parseInt(a[1]), Integer.parseInt(b[1])),
                        Math.min(Integer.parseInt(a[2]), Integer.parseInt(b[2])), Math.max(Integer.parseInt(a[0]), Integer.parseInt(b[0])),
                        Math.max(Integer.parseInt(a[1]), Integer.parseInt(b[1])), Math.max(Integer.parseInt(a[2]), Integer.parseInt(b[2])));
            } catch (RuntimeException e) {
                return null;
            }
        }
    }

    private static final class Run {
        private final MinecraftServer server;
        private final ServerLevel level;
        private final BlockPos origin;
        private final BlockPos spawn;
        private final List<Case> cases = new ArrayList<>();
        private final List<Long> forcedByUs = new ArrayList<>();
        private final List<BoundingBox> touched = new ArrayList<>();
        private final List<String> failed = new ArrayList<>();
        private final List<ServerPlayer> extras = new ArrayList<>();
        private final ServerPlayer owner;
        private final ServerPlayer other;
        private final RecordingConnection ownerConnection = new RecordingConnection();
        @Nullable
        private List<TickSample> sampling;
        private int claimSeq;
        private String lastClaimReply = "";
        private final List<String> claimErrors = new ArrayList<>();
        private int index = -1;
        private int step;
        private int caseTicks;
        private int passed;
        private int judged;
        private boolean setupDone;
        private boolean tornDown;
        private final List<BooleanSupplier> setupSteps = new ArrayList<>();
        private int setupStep;
        private int setupTicks;
        @Nullable
        private double[] savedBorder;
        /** 地皮那组：收尾时不发 Residence 删地请求 */
        private boolean plotMode;

        Run(MinecraftServer server) {
            this.server = server;
            this.level = server.overworld();
            this.spawn = level.getSharedSpawnPos();
            this.origin = new BlockPos(spawn.getX() + 100, 200, spawn.getZ() + 100);
            this.owner = realPlayer(HybridSelfTest.OWNER_ID, HybridSelfTest.OWNER_NAME, ownerConnection);
            this.other = realPlayer(HybridSelfTest.OTHER_ID, HybridSelfTest.OTHER_NAME, new Connection(PacketFlow.SERVERBOUND));
            setupSteps.add(() -> {
                prepareWorld();
                return true;
            });
            setupSteps.add(claim(
                    "create " + OWN_CLAIM + " " + owner.getUUID() + " " + owner.getGameProfile().getName() + " " + corners(rel(0, 0, 0, 15, 15, 15)),
                    "create " + OTHER_CLAIM + " " + other.getUUID() + " " + other.getGameProfile().getName() + " " + corners(rel(32, 0, 0, 47, 15, 15)),
                    "create " + PLACEONLY_CLAIM + " " + other.getUUID() + " " + other.getGameProfile().getName() + " " + corners(rel(80, 0, 0, 95, 7, 15)),
                    "pflag " + PLACEONLY_CLAIM + " " + owner.getUUID() + " destroy true",
                    "pflag " + PLACEONLY_CLAIM + " " + owner.getUUID() + " place false",
                    "remove " + TAKEN_CLAIM,
                    "at " + xyz(origin.offset(5, 5, 5)),
                    "at " + xyz(origin.offset(40, 5, 5)),
                    "at " + xyz(origin.offset(85, 5, 5)),
                    "at " + xyz(origin.offset(25, 5, 5))));
            setupSteps.add(() -> {
                EssentialCraft5.LOGGER.info("{} 圈地：{}", TAG, lastClaimReply);
                return true;
            });
            addCases();
        }

        /**
         * 地皮那组：原点 = 主人那块地皮的西北角、地面往上空一格（地皮地面上方一格是围墙半砖的高度，再高一格才是全空的）。
         * 两块地皮沿 x 相邻，中间是路（含两侧围墙）。
         */
        Run(MinecraftServer server, ServerLevel level, BoundingBox ownPlot, BoundingBox otherPlot) {
            this.server = server;
            this.level = level;
            this.spawn = level.getSharedSpawnPos();
            int cx = (ownPlot.minX() + ownPlot.maxX()) / 2;
            int cz = (ownPlot.minZ() + ownPlot.maxZ()) / 2;
            level.getChunk(cx >> 4, cz >> 4);
            int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, cx, cz);
            this.origin = new BlockPos(ownPlot.minX(), surface + 1, ownPlot.minZ());
            this.owner = realPlayer(HybridSelfTest.OWNER_ID, HybridSelfTest.OWNER_NAME, ownerConnection);
            this.other = realPlayer(HybridSelfTest.OTHER_ID, HybridSelfTest.OTHER_NAME, new Connection(PacketFlow.SERVERBOUND));
            this.plotMode = true;
            setupSteps.add(() -> {
                prepareWorld();
                return true;
            });
            addPlotCases(ownPlot, otherPlot);
        }

        void sample(long totalNanos, long endNanos) {
            if (sampling != null) {
                sampling.add(new TickSample(totalNanos / 1.0E6D, endNanos / 1.0E6D));
            }
        }

        /** @return 跑完了 */
        boolean tick() {
            if (!setupDone) {
                if (setupTicks++ > 200) {
                    EssentialCraft5.LOGGER.error("{} 准备超时（圈地请求没有回音：服务器没装 Residence，或测试插件不是 1.8.0）；最后回复 {}", TAG, lastClaimReply);
                    teardown();
                    EssentialCraft5.LOGGER.info("{} 完成 通过 0/1（准备失败）", TAG);
                    return true;
                }
                if (setupSteps.get(setupStep).getAsBoolean() && ++setupStep >= setupSteps.size()) {
                    setupDone = true;
                    if (!claimErrors.isEmpty()) {
                        EssentialCraft5.LOGGER.error("{} 圈地出错：{}", TAG, claimErrors);
                        teardown();
                        EssentialCraft5.LOGGER.info("{} 完成 通过 0/1（圈地失败）", TAG);
                        return true;
                    }
                }
                return false;
            }
            if (index < 0) {
                index = 0;
                beginCase();
                return false;
            }
            if (index >= cases.size()) {
                finish();
                return true;
            }
            Case c = cases.get(index);
            boolean timedOut = ++caseTicks > c.timeoutTicks();
            boolean advanced = false;
            if (!timedOut) {
                try {
                    if (c.steps().get(step).getAsBoolean()) {
                        step++;
                        advanced = true;
                    }
                } catch (RuntimeException | LinkageError e) {
                    EssentialCraft5.LOGGER.error("{} {} 抛出异常", TAG, c.id(), e);
                    endCase(c, new Outcome(false, "不抛异常", e.toString(), List.of()));
                    return false;
                }
            }
            if (timedOut || advanced && step >= c.steps().size()) {
                Outcome outcome;
                try {
                    outcome = c.judge().get();
                } catch (RuntimeException | LinkageError e) {
                    EssentialCraft5.LOGGER.error("{} {} 判定时抛出异常", TAG, c.id(), e);
                    outcome = new Outcome(false, "不抛异常", e.toString(), List.of());
                }
                if (timedOut) {
                    List<String> problems = new ArrayList<>(outcome.problems());
                    problems.add(c.timeoutTicks() + " 刻没做完（停在第 " + (step + 1) + " 段）");
                    outcome = new Outcome(outcome.pass() == null ? null : false, outcome.expected(), outcome.actual(), problems);
                }
                endCase(c, outcome);
            }
            return false;
        }

        private void beginCase() {
            if (index >= cases.size()) {
                return;
            }
            Case c = cases.get(index);
            EssentialCraft5.LOGGER.info("{} {} 开始 {}", TAG, c.id(), c.title());
            step = 0;
            caseTicks = 0;
            for (ServerPlayer p : List.of(owner, other)) {
                BuilderJobs.stop(p);
                p.getInventory().clearContent();
            }
            ownerConnection.clear();
            System.setProperty(PROP_COUNTS, "");
        }

        private void endCase(Case c, Outcome outcome) {
            for (ServerPlayer p : allPlayers()) {
                if (BuilderJobs.isRunning(p.getUUID())) {
                    BuilderJobs.stop(p);
                }
            }
            sampling = null;
            String verdict;
            if (outcome.pass() == null) {
                verdict = "记录";
            } else {
                judged++;
                if (outcome.pass() && outcome.problems().isEmpty()) {
                    passed++;
                    verdict = "通过";
                } else {
                    failed.add(c.id());
                    verdict = "失败";
                }
            }
            EssentialCraft5.LOGGER.info("{} {} {} 期望 {} 实际 {} {}", TAG, c.id(), c.title(), outcome.expected(), outcome.actual(), verdict);
            for (String problem : outcome.problems()) {
                EssentialCraft5.LOGGER.info("{} {}   问题：{}", TAG, c.id(), problem);
            }
            index++;
            beginCase();
        }

        private List<ServerPlayer> allPlayers() {
            List<ServerPlayer> all = new ArrayList<>(List.of(owner, other));
            all.addAll(extras);
            return all;
        }

        // ------------------------------------------------------------------ 准备与收尾

        private void prepareWorld() {
            System.setProperty(PROP_COUNT, "on");
            System.setProperty(PROP_COUNTS, "");
            BuilderTestContainers.enabled = true;
            int minCx = (origin.getX() - 16) >> 4;
            int maxCx = (origin.getX() + 112) >> 4;
            int minCz = (origin.getZ() - 16) >> 4;
            int maxCz = (origin.getZ() + 116) >> 4;
            for (int cx = minCx; cx <= maxCx; cx++) {
                for (int cz = minCz; cz <= maxCz; cz++) {
                    if (!level.getForcedChunks().contains(ChunkPos.asLong(cx, cz))) {
                        level.setChunkForced(cx, cz, true);
                        forcedByUs.add(ChunkPos.asLong(cx, cz));
                    }
                    level.getChunk(cx, cz);
                }
            }
            // 上次中途被打断留下的方块
            clear(rel(-4, 0, -4, 112, 34, 116));
            clear(rel(86, 116, -2, 94, 124, 2));
            boolean ops = !server.getPlayerList().getOps().isEmpty();
            EssentialCraft5.LOGGER.info("{} 开始：世界 {}，原点 {}（出生点 {}），区块强制加载 {} 个；出生点保护半径 {}，OP 名单{}；插件 {}；测试玩家在玩家列表里：{}",
                    TAG, level.dimension().location(), origin.toShortString(), spawn.toShortString(), forcedByUs.size(), server.getSpawnProtectionRadius(),
                    ops ? "不空" : "是空的（出生点保护不生效，B08 第一项会失败）", System.getProperty(PROP_PROBE, "不在"),
                    server.getPlayerList().getPlayer(owner.getUUID()) == owner && server.getPlayerList().getPlayer(other.getUUID()) == other ? "是" : "否");
        }

        private void finish() {
            teardown();
            if (!failed.isEmpty()) {
                EssentialCraft5.LOGGER.info("{} 失败的用例：{}", TAG, String.join(" ", failed));
            }
            EssentialCraft5.LOGGER.info("{} 完成 通过 {}/{}", TAG, passed, judged);
        }

        void teardown() {
            if (tornDown) {
                return;
            }
            tornDown = true;
            for (ServerPlayer p : allPlayers()) {
                BuilderJobs.stop(p);
                listPlayer(p, false);
            }
            restoreBorder();
            BuilderTestContainers.enabled = false;
            System.clearProperty(PROP_COUNT);
            if (!plotMode) {
                System.setProperty(PROP_CLAIM_REQUEST, (++claimSeq) + "\nremove " + OWN_CLAIM + "\nremove " + OTHER_CLAIM
                        + "\nremove " + PLACEONLY_CLAIM + "\nremove " + TAKEN_CLAIM);
            }
            try {
                for (BoundingBox box : touched) {
                    clear(box);
                }
            } catch (RuntimeException e) {
                EssentialCraft5.LOGGER.error("{} 清理出错", TAG, e);
            }
            for (long chunk : forcedByUs) {
                level.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false);
            }
            forcedByUs.clear();
        }

        // ------------------------------------------------------------------ 用例

        private void addCases() {
            cases.add(b01());
            cases.add(b02());
            cases.add(b03());
            cases.add(b04());
            cases.add(b05());
            cases.add(b06());
            cases.add(b07());
            cases.add(b08());
            cases.add(b09());
            cases.add(b10());
            cases.add(b11());
            cases.add(b12());
            cases.add(b13());
            cases.add(b14());
        }

        /**
         * PlotSquared 那组（原点 = 主人地皮西北角、地面上空一格，见地皮构造器）：
         * P01 自己地皮里铺 4×1×4 全放成；P02 别人地皮里一格不放、不扣料不扣 MRU；P03 路上一格不放；
         * P04 一条 8×1×2 从自己地皮跨到路上：只放自己那半 8 格；P05 别人地皮里的 4×1×4 泥土，清除魔杖一格不拆。
         */
        private void addPlotCases(BoundingBox ownPlot, BoundingBox otherPlot) {
            int y = origin.getY();
            int roadMin = ownPlot.maxX() + 1;
            int roadMax = otherPlot.minX() - 1;
            int roadMid = (roadMin + roadMax) / 2;
            int z0 = ownPlot.minZ();
            BoundingBox own = new BoundingBox(ownPlot.minX() + 4, y, z0 + 4, ownPlot.minX() + 7, y, z0 + 7);
            BoundingBox theirs = new BoundingBox(otherPlot.minX() + 4, y, otherPlot.minZ() + 4, otherPlot.minX() + 7, y, otherPlot.minZ() + 7);
            BoundingBox road = new BoundingBox(roadMid - 1, y, z0 + 4, roadMid + 2, y, z0 + 7);
            BoundingBox straddle = new BoundingBox(ownPlot.maxX() - 3, y, z0 + 10, ownPlot.maxX() + 4, y, z0 + 11);
            BoundingBox straddleOwn = new BoundingBox(ownPlot.maxX() - 3, y, z0 + 10, ownPlot.maxX(), y, z0 + 11);
            BoundingBox dirt = new BoundingBox(otherPlot.minX() + 10, y, otherPlot.minZ() + 10, otherPlot.minX() + 13, y, otherPlot.minZ() + 13);
            EssentialCraft5.LOGGER.info("{} 地皮场地：自己 {}，别人 {}，路 x {}..{}；P01 {} P02 {} P03 {} P04 {} P05 {}", TAG, ownPlot, otherPlot, roadMin, roadMax,
                    own, theirs, road, straddle, dirt);
            cases.add(plotFill("P01", "自己地皮里铺 4×1×4：全部放成", own, 16, 16));
            cases.add(plotFill("P02", "别人地皮里铺 4×1×4：一格不放、不扣料不扣 MRU", theirs, 16, 0));
            if (roadMax - roadMin >= 3) {
                cases.add(plotFill("P03", "路上铺 4×1×4：一格不放、不扣料不扣 MRU", road, 16, 0));
            } else {
                EssentialCraft5.LOGGER.warn("{} 两块地皮之间的路只有 {} 格宽，P03 不跑", TAG, roadMax - roadMin + 1);
            }
            touched.add(straddle);
            Job s = job(owner, points(BuilderMode.FILL, straddle, Blocks.STONE), new BlockPos(straddle.minX(), y + 4, straddle.minZ() - 6),
                    () -> giveStacks(owner, Items.STONE, 9, 0, 16));
            cases.add(new Case("P04", "从自己地皮跨到路上：只铺自己那半", 400, List.of(s.begin(), s.await()), () -> {
                int inOwn = countBlocks(straddleOwn, Blocks.STONE);
                int all = countBlocks(straddle, Blocks.STONE);
                int stone = countMaterial(owner, Items.STONE);
                String actual = s.describe() + "；自己地皮放下 " + inOwn + "、路上放下 " + (all - inOwn) + "，石头剩 " + stone + "，扣 MRU " + s.mruSpent()
                        + "；" + Counts.read() + "；" + ownerConnection;
                boolean pass = inOwn == 8 && all == 8 && stone == 8 && s.mruSpent() == 8 * BuilderMode.PLACE_COST;
                return new Outcome(pass, "自己地皮 8、路上 0，石头剩 8，扣 200", actual, List.of());
            }));
            touched.add(dirt);
            Job c = job(owner, points(BuilderMode.CLEAR, dirt, null), new BlockPos(dirt.minX(), y + 4, dirt.minZ() - 6), () -> {
            });
            cases.add(new Case("P05", "别人地皮里清除：一格不拆", 400, List.of(() -> {
                fill(dirt, Blocks.DIRT);
                return true;
            }, c.begin(), c.await()), () -> {
                int left = countBlocks(dirt, Blocks.DIRT);
                int got = countMaterial(owner, Items.DIRT);
                int drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(dirt.minX() - 4, y - 4, dirt.minZ() - 4, dirt.maxX() + 4, y + 4, dirt.maxZ() + 4)).size();
                String actual = c.describe() + "；泥土剩 " + left + "，背包泥土 " + got + "，扣 MRU " + c.mruSpent() + "，掉落物 " + drops + "；" + Counts.read() + "；" + ownerConnection;
                boolean pass = left == 16 && got == 0 && c.mruSpent() == 0 && drops == 0 && c.result != null && c.result.done() == 0;
                return new Outcome(pass, "泥土剩 16、背包 0、扣 0、掉落物 0", actual, List.of());
            }));
        }

        /** 地皮那组的一单铺面：box 里铺石头，背包给 stone 块；期望放下 expect 格（0 时还要求石头不少、MRU 不扣） */
        private Case plotFill(String id, String title, BoundingBox box, int stone, int expect) {
            touched.add(box);
            Job j = job(owner, points(BuilderMode.FILL, box, Blocks.STONE), new BlockPos(box.minX(), box.minY() + 4, box.minZ() - 6),
                    () -> giveStacks(owner, Items.STONE, 9, 0, stone));
            return new Case(id, title, 400, List.of(j.begin(), j.await()), () -> {
                int placed = countBlocks(box, Blocks.STONE);
                int left = countMaterial(owner, Items.STONE);
                Counts counts = Counts.read();
                String actual = j.describe() + "；放下 " + placed + "，石头剩 " + left + "，扣 MRU " + j.mruSpent() + "；" + counts + "；" + ownerConnection;
                boolean pass = placed == expect && left == stone - expect && j.mruSpent() == expect * BuilderMode.PLACE_COST
                        && j.result != null && j.result.done() == expect;
                List<String> problems = new ArrayList<>();
                if (expect == 0 && counts.placeAllowed() > 0) {
                    problems.add("插件放行了 " + counts.placeAllowed() + " 条放置事件");
                }
                return new Outcome(pass, "放下 " + expect + "，石头剩 " + (stone - expect) + "，扣 " + expect * BuilderMode.PLACE_COST, actual, problems);
            });
        }

        /** 一单施工：谁、什么魔杖、站哪、身上带什么；开工结果和做完的结果记在这里 */
        private final class Job {
            final ServerPlayer player;
            final ItemStack wand;
            final BlockPos stand;
            final Runnable loadout;
            @Nullable
            BuilderJobs.StartResult started;
            @Nullable
            BuilderJobs.Result result;
            int mruBefore;

            Job(ServerPlayer player, ItemStack wand, BlockPos stand, Runnable loadout) {
                this.player = player;
                this.wand = wand;
                this.stand = stand;
                this.loadout = loadout;
            }

            /** 换装、站好、开工（一段：本刻做完） */
            BooleanSupplier begin() {
                return () -> {
                    player.getInventory().clearContent();
                    player.setItemInHand(InteractionHand.MAIN_HAND, wand);
                    loadout.run();
                    player.moveTo(stand.getX() + 0.5D, stand.getY(), stand.getZ() + 0.5D, 0F, 0F);
                    player.setOnGround(false);
                    mruBefore = MRUStaffItem.storedMRU(wand);
                    started = BuilderJobs.start(player, InteractionHand.MAIN_HAND);
                    return true;
                };
            }

            /** 等这单做完（一段） */
            BooleanSupplier await() {
                return () -> {
                    if (BuilderJobs.isRunning(player.getUUID())) {
                        return false;
                    }
                    result = BuilderJobs.lastResult(player.getUUID());
                    return true;
                };
            }

            int mruSpent() {
                return mruBefore - MRUStaffItem.storedMRU(wand);
            }

            String describe() {
                return "开工 " + started + "，" + (result == null ? "没有结果" : result.reason() + " 放成 " + result.done() + " 被拒 " + result.denied()
                        + "（其中探测拒 " + result.probedDenied() + "）跳过 " + result.skipped());
            }
        }

        private Job job(ServerPlayer player, ItemStack wand, BlockPos stand, Runnable loadout) {
            return new Job(player, wand, stand, loadout);
        }

        /**
         * B01（情形 1）：整片 16×2×16 = 512 格都在别人的领地里铺石头，背包里有 512 块。
         * 期望：一格不放、石头不少、魔杖 MRU 不扣，被拒到上限 256 停（DENIED）。另记插件收到的放置 / 破坏（含探测）事件条数、玩家收到几条提示。
         */
        private Case b01() {
            BoundingBox box = rel(32, 0, 0, 47, 1, 15);
            touched.add(box);
            Job j = job(owner, points(BuilderMode.FILL, box, Blocks.STONE), origin.offset(40, 4, -6), () -> giveStacks(owner, Items.STONE, 9, 8));
            return new Case("B01", "整片在别人领地里铺面：全被拒、不扣料不扣 MRU，被拒到上限停", 400, List.of(j.begin(), j.await()), () -> {
                List<String> problems = new ArrayList<>();
                int placed = countBlocks(box, Blocks.STONE);
                int stone = countMaterial(owner, Items.STONE);
                Counts counts = Counts.read();
                String actual = j.describe() + "；放下 " + placed + " 格，石头剩 " + stone + "，扣 MRU " + j.mruSpent() + "；" + counts + "；" + ownerConnection;
                boolean pass = j.started == BuilderJobs.StartResult.STARTED && j.result != null && j.result.reason() == BuilderJobs.StopReason.DENIED
                        && j.result.done() == 0 && placed == 0 && stone == 512 && j.mruSpent() == 0;
                if (counts.placeAllowed() > 0) {
                    problems.add("插件放行了 " + counts.placeAllowed() + " 条放置事件");
                }
                return new Outcome(pass, "DENIED 放成 0；放下 0 格，石头 512，扣 MRU 0", actual, problems);
            });
        }

        /** B02（情形 2）：自己领地里两点形状 8×1×8 = 64 格、铺面 5×5（从脚下）都放成，扣料扣 MRU 正好 */
        private Case b02() {
            BoundingBox box = rel(0, 0, 0, 7, 0, 7);
            BoundingBox plane = rel(8, 4, 10, 12, 4, 14);
            touched.add(box);
            touched.add(plane);
            Job a = job(owner, points(BuilderMode.FILL, box, Blocks.STONE), origin.offset(4, 4, -6), () -> giveStacks(owner, Items.STONE, 9, 2));
            ItemStack planeWand = wand(BuilderMode.FILL, FULL);
            BuilderSelection.setShape(planeWand, BuilderShape.PLANE);
            BuilderSelection.setSize(planeWand, BuilderShape.PLANE, 5, 5);
            BuilderSelection.setStoredBlock(planeWand, ForgeRegistries.BLOCKS.getKey(Blocks.STONE));
            // 悬空站在 (10.5, 5.x, 10.5)：脚下那层是 y = 4，面朝南（+z），5×5 落在 x 8..12、z 10..14（都在自己的领地里）
            Job b = job(owner, planeWand, origin.offset(10, 5, 10), () -> giveStacks(owner, Items.STONE, 9, 1));
            int[] stoneAfterA = new int[1];
            return new Case("B02", "自己领地里两点 64 格、铺面 5×5：全部放成", 400, List.of(a.begin(), a.await(), () -> {
                stoneAfterA[0] = countMaterial(owner, Items.STONE);
                return true;
            }, b.begin(), b.await()), () -> {
                int placedA = countBlocks(box, Blocks.STONE);
                int placedB = countBlocks(plane, Blocks.STONE);
                int stoneB = countMaterial(owner, Items.STONE);
                Counts counts = Counts.read();
                String actual = "两点：" + a.describe() + "，放下 " + placedA + "，石头剩 " + stoneAfterA[0] + "，扣 MRU " + a.mruSpent()
                        + "；铺面：" + b.describe() + "，放下 " + placedB + "，石头剩 " + stoneB + "，扣 MRU " + b.mruSpent() + "；" + counts + "；" + ownerConnection;
                boolean pass = a.result != null && a.result.reason() == BuilderJobs.StopReason.FINISHED && placedA == 64 && stoneAfterA[0] == 64
                        && a.mruSpent() == 64 * BuilderMode.PLACE_COST
                        && b.result != null && b.result.reason() == BuilderJobs.StopReason.FINISHED && placedB == 25 && stoneB == 39
                        && b.mruSpent() == 25 * BuilderMode.PLACE_COST;
                return new Outcome(pass, "两点 FINISHED 放下 64 石头剩 64 扣 1600；铺面 FINISHED 放下 25 石头剩 39 扣 625", actual, List.of());
            });
        }

        /** B03（情形 3）：x 24..39 一条 16×1×4 = 64 格，前一半是野外、后一半在别人领地里：只放野外的 32 格，扣 32 块、800 MRU */
        private Case b03() {
            BoundingBox box = rel(24, 2, 0, 39, 2, 3);
            BoundingBox wild = rel(24, 2, 0, 31, 2, 3);
            BoundingBox claimed = rel(32, 2, 0, 39, 2, 3);
            touched.add(box);
            Job j = job(owner, points(BuilderMode.FILL, box, Blocks.STONE), origin.offset(28, 6, -6), () -> giveStacks(owner, Items.STONE, 9, 1));
            return new Case("B03", "一片跨领地边界：只铺自己能动的那半", 400, List.of(j.begin(), j.await()), () -> {
                int inWild = countBlocks(wild, Blocks.STONE);
                int inClaim = countBlocks(claimed, Blocks.STONE);
                int stone = countMaterial(owner, Items.STONE);
                Counts counts = Counts.read();
                String actual = j.describe() + "；野外放下 " + inWild + "、别人领地放下 " + inClaim + "，石头剩 " + stone + "，扣 MRU " + j.mruSpent()
                        + "；" + counts + "；" + ownerConnection;
                boolean pass = j.result != null && j.result.reason() == BuilderJobs.StopReason.FINISHED && inWild == 32 && inClaim == 0
                        && stone == 32 && j.mruSpent() == 32 * BuilderMode.PLACE_COST;
                return new Outcome(pass, "FINISHED；野外 32、别人领地 0，石头剩 32，扣 800", actual, List.of());
            });
        }

        /**
         * B04（情形 4）：材料不在手里时插件拦不拦、会不会因为「手里的东西不是放下的方块」误拦。主手一直是魔杖，每单 2×1×2 = 4 格：
         * a 石头在背包第 21 格（不在快捷栏）、副手空；b 只在副手；c 在随身容器里（测试容器：收纳袋挂物品处理能力，同 GameTest）；d 背包里、副手拿钻石剑。
         * 自己领地里 a~d 都放成、材料用完；别人领地里 a~c 一格不放、材料一个不少（随身容器里的也原样在）。记插件看到的手和物品。
         */
        private Case b04() {
            List<BooleanSupplier> steps = new ArrayList<>();
            List<Job> own = new ArrayList<>();
            List<Job> denied = new ArrayList<>();
            List<BoundingBox> ownBoxes = new ArrayList<>();
            List<BoundingBox> deniedBoxes = new ArrayList<>();
            List<int[]> leftOwn = new ArrayList<>();
            List<int[]> leftDenied = new ArrayList<>();
            List<Map<String, Integer>> placeKeys = new ArrayList<>();
            String[] names = {"a 背包非快捷栏", "b 只在副手", "c 随身容器", "d 背包 + 副手拿剑"};
            for (int k = 0; k < 4; k++) {
                BoundingBox box = rel(k * 3, 2, 0, k * 3 + 1, 2, 1);
                ownBoxes.add(box);
                touched.add(box);
                int kind = k;
                Job j = job(owner, points(BuilderMode.FILL, box, Blocks.STONE), origin.offset(k * 3, 6, -6), () -> loadout(owner, kind, 4));
                own.add(j);
                int[] left = new int[1];
                leftOwn.add(left);
                steps.add(() -> {
                    System.setProperty(PROP_COUNTS, "");
                    return true;
                });
                steps.add(j.begin());
                steps.add(j.await());
                steps.add(() -> {
                    left[0] = countMaterial(owner, Items.STONE);
                    placeKeys.add(Counts.read().placeKeys());
                    return true;
                });
            }
            for (int k = 0; k < 3; k++) {
                BoundingBox box = rel(32 + k * 3, 4, 0, 33 + k * 3, 4, 1);
                deniedBoxes.add(box);
                touched.add(box);
                int kind = k;
                Job j = job(owner, points(BuilderMode.FILL, box, Blocks.STONE), origin.offset(32 + k * 3, 8, -6), () -> loadout(owner, kind, 4));
                denied.add(j);
                int[] left = new int[1];
                leftDenied.add(left);
                steps.add(() -> {
                    System.setProperty(PROP_COUNTS, "");
                    return true;
                });
                steps.add(j.begin());
                steps.add(j.await());
                steps.add(() -> {
                    left[0] = countMaterial(owner, Items.STONE);
                    placeKeys.add(Counts.read().placeKeys());
                    return true;
                });
            }
            return new Case("B04", "材料在背包 / 副手 / 随身容器、副手拿剑：插件照领地判，事件里报的手拿着放下的方块", 600, steps, () -> {
                List<String> problems = new ArrayList<>();
                StringBuilder actual = new StringBuilder("自己领地：");
                boolean pass = true;
                for (int k = 0; k < own.size(); k++) {
                    int placed = countBlocks(ownBoxes.get(k), Blocks.STONE);
                    actual.append(names[k]).append(" 放下 ").append(placed).append(" 剩 ").append(leftOwn.get(k)[0])
                            .append(" 插件看到 ").append(k < placeKeys.size() ? placeKeys.get(k) : "-").append("；");
                    if (placed != 4 || leftOwn.get(k)[0] != 0) {
                        pass = false;
                        problems.add(names[k] + "（自己领地）：" + own.get(k).describe());
                    }
                    if (k < placeKeys.size()) {
                        for (String key : placeKeys.get(k).keySet()) {
                            if (!key.endsWith("|STONE|same")) {
                                pass = false;
                                problems.add(names[k] + "（自己领地）：放置事件报的手里物品不是放下的石头：" + key);
                            }
                        }
                    }
                }
                actual.append("别人领地：");
                for (int k = 0; k < denied.size(); k++) {
                    int placed = countBlocks(deniedBoxes.get(k), Blocks.STONE);
                    int keyIndex = own.size() + k;
                    actual.append(names[k]).append(" 放下 ").append(placed).append(" 剩 ").append(leftDenied.get(k)[0])
                            .append(" 插件看到 ").append(keyIndex < placeKeys.size() ? placeKeys.get(keyIndex) : "-").append("；");
                    if (placed != 0 || leftDenied.get(k)[0] != 4) {
                        pass = false;
                        problems.add(names[k] + "（别人领地）：" + denied.get(k).describe());
                    }
                }
                return new Outcome(pass, "自己领地 a~d 各放下 4、剩 0、事件里报的手拿着 1 个石头；别人领地 a~c 各放下 0、剩 4", actual.toString(), problems);
            });
        }

        /** 材料放法：0 背包第 21 格；1 副手；2 随身容器（背包第 21 格的测试收纳袋里）；3 背包第 21 格 + 副手钻石剑 */
        private void loadout(ServerPlayer player, int kind, int count) {
            switch (kind) {
                case 1 -> player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STONE, count));
                case 2 -> {
                    ItemStack bag = new ItemStack(Items.BUNDLE);
                    BuilderTestContainers.handler(bag).insertItem(0, new ItemStack(Items.STONE, count), false);
                    player.getInventory().items.set(20, bag);
                }
                case 3 -> {
                    player.getInventory().items.set(20, new ItemStack(Items.STONE, count));
                    player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIAMOND_SWORD));
                }
                default -> player.getInventory().items.set(20, new ItemStack(Items.STONE, count));
            }
        }

        /**
         * B05（情形 5）：别人领地里 4×1×4 的泥土，清除魔杖、全部替换魔杖（换成石头，背包 16 块）各来一单：一格不动、背包不多不少、没有掉落物、MRU 不扣。
         * 对照：自己领地里同样一片泥土，清除魔杖全清掉、16 块泥土进背包。
         */
        private Case b05() {
            BoundingBox theirs = rel(32, 6, 0, 35, 6, 3);
            BoundingBox mine = rel(0, 6, 0, 3, 6, 3);
            touched.add(theirs);
            touched.add(mine);
            Job clear = job(owner, points(BuilderMode.CLEAR, theirs, null), origin.offset(33, 10, -6), () -> {
            });
            Job replace = job(owner, points(BuilderMode.REPLACE_ALL, theirs, Blocks.STONE), origin.offset(33, 10, -6), () -> giveStacks(owner, Items.STONE, 9, 0, 16));
            Job control = job(owner, points(BuilderMode.CLEAR, mine, null), origin.offset(1, 10, -6), () -> {
            });
            int[] after = new int[4];
            return new Case("B05", "清除 / 全部替换在别人领地里：一格都不拆", 600, List.of(() -> {
                fill(theirs, Blocks.DIRT);
                fill(mine, Blocks.DIRT);
                return true;
            }, clear.begin(), clear.await(), () -> {
                after[0] = countBlocks(theirs, Blocks.DIRT);
                after[1] = countMaterial(owner, Items.DIRT);
                return true;
            }, replace.begin(), replace.await(), () -> {
                after[2] = countBlocks(theirs, Blocks.DIRT);
                after[3] = countMaterial(owner, Items.STONE) * 1000 + countMaterial(owner, Items.DIRT);
                return true;
            }, control.begin(), control.await()), () -> {
                int drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(origin.offset(-8, -4, -12), origin.offset(56, 20, 24))).size();
                int controlLeft = countBlocks(mine, Blocks.DIRT);
                int controlDirt = countMaterial(owner, Items.DIRT);
                Counts counts = Counts.read();
                String actual = "清除：" + clear.describe() + "，泥土剩 " + after[0] + "，背包泥土 " + after[1] + "，扣 MRU " + clear.mruSpent()
                        + "；替换：" + replace.describe() + "，泥土剩 " + after[2] + "，背包石头 " + after[3] / 1000 + " 泥土 " + after[3] % 1000
                        + "，扣 MRU " + replace.mruSpent() + "；对照（自己领地清除）：" + control.describe() + "，泥土剩 " + controlLeft + "，背包泥土 " + controlDirt
                        + "；掉落物 " + drops + "；" + counts + "；" + ownerConnection;
                boolean pass = after[0] == 16 && after[1] == 0 && clear.mruSpent() == 0 && clear.result != null && clear.result.done() == 0
                        && after[2] == 16 && after[3] == 16000 && replace.mruSpent() == 0 && replace.result != null && replace.result.done() == 0
                        && controlLeft == 0 && controlDirt == 16 && drops == 0;
                return new Outcome(pass, "清除 / 替换：泥土剩 16、背包不变、扣 0、放成 0；对照清掉 16、背包泥土 16；掉落物 0", actual, List.of());
            });
        }

        /**
         * B06（情形 6）：a 自己领地里铺 4×1×4，撤销 → 全撤、16 块石头退回；
         * b 野外（x 64..67）铺 4×1×4，接着这片被另一名玩家圈走（{@value #TAKEN_CLAIM}），再撤销 → 一格不撤、一块不退。
         */
        private Case b06() {
            BoundingBox mine = rel(12, 8, 12, 15, 8, 15);
            BoundingBox taken = rel(64, 0, 0, 67, 0, 3);
            touched.add(mine);
            touched.add(taken);
            ItemStack wandA = points(BuilderMode.FILL, mine, Blocks.STONE);
            ItemStack wandB = points(BuilderMode.FILL, taken, Blocks.STONE);
            Job buildA = job(owner, wandA, origin.offset(13, 12, 8), () -> giveStacks(owner, Items.STONE, 9, 0, 16));
            Job buildB = job(owner, wandB, origin.offset(65, 4, -6), () -> giveStacks(owner, Items.STONE, 9, 0, 16));
            BuilderJobs.StartResult[] undo = new BuilderJobs.StartResult[2];
            BuilderJobs.Result[] undoResult = new BuilderJobs.Result[2];
            int[] after = new int[4];
            return new Case("B06", "撤销：自己领地能撤；铺完被别人圈走后撤不了、不退料", 600, List.of(
                    buildA.begin(), buildA.await(), () -> {
                        undo[0] = BuilderJobs.undo(owner);
                        return true;
                    }, () -> {
                        if (BuilderJobs.isRunning(owner.getUUID())) {
                            return false;
                        }
                        undoResult[0] = BuilderJobs.lastResult(owner.getUUID());
                        after[0] = countBlocks(mine, Blocks.STONE);
                        after[1] = countMaterial(owner, Items.STONE);
                        return true;
                    },
                    buildB.begin(), buildB.await(),
                    claim("create " + TAKEN_CLAIM + " " + other.getUUID() + " " + other.getGameProfile().getName() + " " + corners(rel(64, 0, 0, 71, 7, 7))),
                    () -> {
                        undo[1] = BuilderJobs.undo(owner);
                        return true;
                    }, () -> {
                        if (BuilderJobs.isRunning(owner.getUUID())) {
                            return false;
                        }
                        undoResult[1] = BuilderJobs.lastResult(owner.getUUID());
                        after[2] = countBlocks(taken, Blocks.STONE);
                        after[3] = countMaterial(owner, Items.STONE);
                        return true;
                    }), () -> {
                String actual = "a 铺：" + buildA.describe() + "；撤销 " + undo[0] + " " + describe(undoResult[0]) + "，石头剩在地上 " + after[0] + "，背包石头 " + after[1]
                        + "；b 铺：" + buildB.describe() + "；圈地 " + lastClaimReply + "；撤销 " + undo[1] + " " + describe(undoResult[1])
                        + "，石头剩在地上 " + after[2] + "，背包石头 " + after[3] + "；" + Counts.read();
                boolean pass = buildA.result != null && buildA.result.done() == 16 && undo[0] == BuilderJobs.StartResult.STARTED && after[0] == 0 && after[1] == 16
                        && buildB.result != null && buildB.result.done() == 16 && after[2] == 16 && after[3] == 0
                        && undoResult[1] != null && undoResult[1].done() == 0;
                return new Outcome(pass, "a 撤掉 16、退回 16；b 撤销放成 0、地上还是 16、背包 0", actual, List.of());
            });
        }

        /**
         * B07（情形 7）：只拒放置的领地（Residence 给主人单设 destroy=true、place=false：拆得掉、放不上）里铺 8×1×4 = 32 格。
         * 开工前的探测（发的是破坏事件）都放行，每格真放才被拒；第一次被拒以后改成先探后放，探测照样放行、照样真放、照样被拒。
         * 期望：一格不放、石头 32 块不少、MRU 不扣。另记：插件收到的放置事件（应 32 条、全取消）、破坏事件（探测）条数、
         * 玩家收到的提示（本模组的、插件的各几条）——这条看的是「先探后放会不会让插件刷屏」，提示条数只记录。
         */
        private Case b07() {
            BoundingBox box = rel(80, 0, 0, 87, 0, 3);
            touched.add(box);
            Job j = job(owner, points(BuilderMode.FILL, box, Blocks.STONE), origin.offset(84, 4, -6), () -> giveStacks(owner, Items.STONE, 9, 0, 32));
            return new Case("B07", "只拒放置的领地：探测放行、真放被拒，数插件事件和发给玩家的提示", 400, List.of(j.begin(), j.await()), () -> {
                int placed = countBlocks(box, Blocks.STONE);
                int stone = countMaterial(owner, Items.STONE);
                Counts counts = Counts.read();
                String actual = j.describe() + "；放下 " + placed + "，石头剩 " + stone + "，扣 MRU " + j.mruSpent() + "；" + counts + "；" + ownerConnection;
                List<String> problems = new ArrayList<>();
                if (counts.placeAllowed() > 0) {
                    problems.add("插件放行了 " + counts.placeAllowed() + " 条放置事件");
                }
                boolean pass = placed == 0 && stone == 32 && j.mruSpent() == 0 && j.result != null && j.result.done() == 0;
                return new Outcome(pass, "放下 0，石头 32，扣 0", actual, problems);
            });
        }

        /**
         * B08（情形 8）：出生点保护范围里 3×1×3（玩家不是 OP、OP 名单不空）、世界边界外（临时把边界挪到 x = O + 104，8 格在里面、8 格在外面）、
         * 建筑高度外（y 316..323，320 起在外面）：边界内 / 高度内的照放，其余一格不放，插件也收不到那些格子的事件。
         */
        private Case b08() {
            BoundingBox spawnBox = new BoundingBox(spawn.getX() + 2, spawn.getY() + 1, spawn.getZ() + 2, spawn.getX() + 4, spawn.getY() + 1, spawn.getZ() + 4);
            BoundingBox border = rel(100, 0, 0, 107, 0, 1);
            BoundingBox borderIn = rel(100, 0, 0, 103, 0, 1);
            int top = level.getMaxBuildHeight();
            BoundingBox height = new BoundingBox(origin.getX() + 90, top - 4, origin.getZ(), origin.getX() + 90, top + 3, origin.getZ());
            touched.add(spawnBox);
            touched.add(border);
            touched.add(height);
            Job s = job(owner, points(BuilderMode.FILL, spawnBox, Blocks.STONE), spawn.above(3), () -> giveStacks(owner, Items.STONE, 9, 0, 9));
            Job b = job(owner, points(BuilderMode.FILL, border, Blocks.STONE), origin.offset(98, 4, -4), () -> giveStacks(owner, Items.STONE, 9, 0, 16));
            Job h = job(owner, points(BuilderMode.FILL, height, Blocks.STONE), new BlockPos(origin.getX() + 88, top - 4, origin.getZ()),
                    () -> giveStacks(owner, Items.STONE, 9, 0, 8));
            Map<String, Integer>[] keys = newMaps(3);
            return new Case("B08", "出生点保护、世界边界外、建筑高度外：不放", 600, List.of(() -> {
                System.setProperty(PROP_COUNTS, "");
                return true;
            }, s.begin(), s.await(), () -> {
                keys[0] = Counts.read().all;
                System.setProperty(PROP_COUNTS, "");
                moveBorder(origin.getX() + 104);
                return true;
            }, b.begin(), b.await(), () -> {
                restoreBorder();
                keys[1] = Counts.read().all;
                System.setProperty(PROP_COUNTS, "");
                return true;
            }, h.begin(), h.await(), () -> {
                keys[2] = Counts.read().all;
                return true;
            }), () -> {
                int sp = countBlocks(spawnBox, Blocks.STONE);
                int bi = countBlocks(borderIn, Blocks.STONE);
                int ball = countBlocks(border, Blocks.STONE);
                int hi = countBlocks(height, Blocks.STONE);
                String actual = "出生点：" + s.describe() + "，放下 " + sp + "，插件 " + keys[0] + "；边界：" + b.describe() + "，里面放下 " + bi + " 外面放下 " + (ball - bi)
                        + "，插件 " + keys[1] + "；高度：" + h.describe() + "，放下 " + hi + "（建筑高度上限 " + top + "），插件 " + keys[2] + "；" + ownerConnection;
                boolean pass = sp == 0 && bi == 8 && ball == 8 && hi == 4;
                return new Outcome(pass, "出生点放下 0；边界里 8 外 0；高度内 4", actual, List.of());
            });
        }

        /**
         * B09（情形 9）：一次几千格的每刻耗时（只记录）。先空闲 60 刻量底数；A 一人 32×4×32 = 4096 格（30 叠石头 + 4 个测试收纳袋，带无尽 MRU 晶体）；
         * B 四人同时各 32×2×32 = 2048 格。记每段整刻耗时、END 处理器那一段（魔杖在里面）的平均 / 95% / 最大，对照 {@code max_millis_per_tick}。
         */
        private Case b09() {
            List<TickSample> idle = new ArrayList<>();
            List<TickSample> phaseA = new ArrayList<>();
            List<TickSample> phaseB = new ArrayList<>();
            BoundingBox boxA = rel(0, 20, 40, 31, 23, 71);
            touched.add(boxA);
            Job a = job(owner, points(BuilderMode.FILL, boxA, Blocks.STONE), origin.offset(16, 28, 36), () -> {
                giveStacks(owner, Items.STONE, 2, 30);
                giveCrystal(owner);
                for (int k = 0; k < 4; k++) {
                    ItemStack bag = new ItemStack(Items.BUNDLE);
                    IItemHandler handler = BuilderTestContainers.handler(bag);
                    for (int s = 0; s < 9; s++) {
                        handler.insertItem(s, new ItemStack(Items.STONE, 64), false);
                    }
                    owner.getInventory().items.set(32 + k, bag);
                }
            });
            List<Job> four = new ArrayList<>();
            // 四块 32×2×32：x / z / y（相对原点），互不重叠，也不碰 A 那块（y 20..23、z 40..71、x 0..31）
            int[][] corners = {{0, 80, 20}, {40, 80, 20}, {40, 40, 20}, {0, 80, 30}};
            for (int k = 0; k < 4; k++) {
                ServerPlayer p = realPlayer(UUID.nameUUIDFromBytes(("ec5-selftest:builder" + k).getBytes(StandardCharsets.UTF_8)), "EC5Builder" + k,
                        new Connection(PacketFlow.SERVERBOUND));
                extras.add(p);
                int x = corners[k][0];
                int z = corners[k][1];
                int y = corners[k][2];
                BoundingBox box = rel(x, y, z, x + 31, y + 1, z + 31);
                touched.add(box);
                four.add(job(p, points(BuilderMode.FILL, box, Blocks.STONE), origin.offset(x + 16, y + 6, z - 4), () -> {
                    giveStacks(p, Items.STONE, 2, 32);
                    giveCrystal(p);
                }));
            }
            int[] idleTicks = new int[1];
            List<BooleanSupplier> steps = new ArrayList<>();
            steps.add(() -> {
                sampling = idle;
                return ++idleTicks[0] >= 60;
            });
            steps.add(() -> {
                sampling = phaseA;
                return a.begin().getAsBoolean();
            });
            steps.add(a.await());
            steps.add(() -> {
                sampling = phaseB;
                for (Job j : four) {
                    j.begin().getAsBoolean();
                }
                return true;
            });
            steps.add(() -> {
                for (Job j : four) {
                    if (!j.await().getAsBoolean()) {
                        return false;
                    }
                }
                sampling = null;
                return true;
            });
            return new Case("B09", "一次几千格：每刻耗时（只记录）", 2400, steps, () -> {
                int placedA = countBlocks(boxA, Blocks.STONE);
                StringBuilder fourText = new StringBuilder();
                int placedB = 0;
                for (Job j : four) {
                    if (j.result != null) {
                        placedB += j.result.done();
                    }
                    fourText.append(j.player.getGameProfile().getName()).append(' ').append(j.describe()).append("；");
                }
                String actual = "预算 " + ECBuilder.Config.maxMillisPerTick() + " ms / 全服 " + ECBuilder.Config.maxCellsPerTickTotal() + " 格 / 每人 "
                        + ECBuilder.Config.cellsPerTick() + " 格；空闲 " + stats(idle) + "；A 一人 4096：" + a.describe() + "，放下 " + placedA + "，" + stats(phaseA)
                        + "；B 四人各 2048：" + fourText + "合计放成 " + placedB + "，" + stats(phaseB);
                return new Outcome(null, "放成 4096 + 8192；END 段每刻不超过预算太多", actual, List.of());
            });
        }

        private String stats(List<TickSample> samples) {
            if (samples.isEmpty()) {
                return "没有采样";
            }
            double[] total = samples.stream().mapToDouble(TickSample::total).sorted().toArray();
            double[] end = samples.stream().mapToDouble(TickSample::end).sorted().toArray();
            return samples.size() + " 刻，整刻 平均 " + fmt(Arrays.stream(total).average().orElse(0)) + " / p95 " + fmt(total[(int) Math.floor((total.length - 1) * 0.95D)])
                    + " / 最大 " + fmt(total[total.length - 1]) + " ms，END 段 平均 " + fmt(Arrays.stream(end).average().orElse(0))
                    + " / p95 " + fmt(end[(int) Math.floor((end.length - 1) * 0.95D)]) + " / 最大 " + fmt(end[end.length - 1]) + " ms";
        }

        /**
         * B10：先探后放对空气格的探测事件。一条 16×1×4 前半野外、后半在别人领地（同 B03 的布置，换一层）：第一次被拒以后改成先探后放，
         * 之后每格先以玩家身份发一次权限探测（对空气格就是「破坏空气」的事件）再真放。记插件在 HIGHEST / MONITOR 两处收到的破坏事件、
         * 各自看到的方块种类：认得出探测的核心只发给 HIGHEST，MONITOR 收不到（日志类插件不会记下「破坏空气」）。
         * 判：只放野外 32 格；MONITOR 一条破坏都没收到。
         */
        private Case b10() {
            BoundingBox box = rel(24, 10, 4, 39, 10, 7);
            BoundingBox wild = rel(24, 10, 4, 31, 10, 7);
            touched.add(box);
            Job j = job(owner, points(BuilderMode.FILL, box, Blocks.STONE), origin.offset(32, 14, -6), () -> giveStacks(owner, Items.STONE, 9, 1));
            return new Case("B10", "先探后放对空气格的探测：插件看到什么、日志类插件记不记", 400, List.of(j.begin(), j.await()), () -> {
                Counts counts = Counts.read();
                int placed = countBlocks(box, Blocks.STONE);
                int placedWild = countBlocks(wild, Blocks.STONE);
                int highestAir = counts.sum("breakHighest|false|AIR") + counts.sum("breakHighest|true|AIR");
                int monitor = counts.sum("breakMonitor|");
                String actual = j.describe() + "；放下 " + placed + "（野外 " + placedWild + "），扣 MRU " + j.mruSpent()
                        + "；HIGHEST 收到对空气格的破坏事件 " + highestAir + " 条（放行 " + counts.sum("breakHighest|false|AIR") + "、取消 " + counts.sum("breakHighest|true|AIR")
                        + "），MONITOR 收到破坏 " + monitor + " 条；" + counts;
                List<String> problems = new ArrayList<>();
                if (monitor > 0) {
                    problems.add("MONITOR 收到了 " + monitor + " 条破坏事件（日志类插件会把探测记成破坏）");
                }
                boolean pass = placed == 32 && placedWild == 32;
                return new Outcome(pass, "只放野外 32 格；MONITOR 0 条破坏", actual, problems);
            });
        }

        /**
         * B11：撤销的破坏事件日志。自己领地里铺 4×1×4（默认提前量，飞行特效开着），撤销；撤销期间逐条记 MONITOR 收到的没取消的破坏
         * （日志类插件记下的就是这些）。判：正好 16 条，玩家是主人、方块是石头、16 个坐标各不相同且都在这片里；背包退回 16 块。
         */
        private Case b11() {
            BoundingBox box = rel(4, 10, 4, 7, 10, 7);
            touched.add(box);
            Job build = job(owner, points(BuilderMode.FILL, box, Blocks.STONE), origin.offset(5, 14, -2), () -> giveStacks(owner, Items.STONE, 9, 0, 16));
            BuilderJobs.StartResult[] undo = new BuilderJobs.StartResult[1];
            BuilderJobs.Result[] undoResult = new BuilderJobs.Result[1];
            String[] log = {""};
            int[] after = new int[2];
            return new Case("B11", "撤销的破坏事件：日志类插件记下的玩家、方块、坐标", 600, List.of(build.begin(), build.await(), () -> {
                System.setProperty(PROP_COUNTS, "");
                System.setProperty(PROP_BREAK_LOG, "");
                System.setProperty(PROP_BREAK_CELLS, "on");
                undo[0] = BuilderJobs.undo(owner);
                return true;
            }, () -> {
                if (BuilderJobs.isRunning(owner.getUUID())) {
                    return false;
                }
                undoResult[0] = BuilderJobs.lastResult(owner.getUUID());
                System.clearProperty(PROP_BREAK_CELLS);
                log[0] = System.getProperty(PROP_BREAK_LOG, "");
                after[0] = countBlocks(box, Blocks.STONE);
                after[1] = countMaterial(owner, Items.STONE);
                return true;
            }), () -> {
                List<String> problems = new ArrayList<>();
                java.util.Set<String> cells = new java.util.HashSet<>();
                int entries = 0;
                for (String entry : log[0].isEmpty() ? new String[0] : log[0].split(";")) {
                    entries++;
                    String[] part = entry.split(" ");
                    if (part.length != 3) {
                        problems.add("记录格式不对：" + entry);
                        continue;
                    }
                    String[] xyz = part[2].split(",");
                    BlockPos pos = new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
                    if (!part[0].equals(owner.getGameProfile().getName()) || !part[1].equals("STONE") || !box.isInside(pos)) {
                        problems.add("这条不对：" + entry);
                    }
                    cells.add(part[2]);
                }
                String actual = "铺：" + build.describe() + "；撤销 " + undo[0] + " " + describe(undoResult[0]) + "，地上剩 " + after[0] + "，背包石头 " + after[1]
                        + "；MONITOR 记下 " + entries + " 条、不同坐标 " + cells.size() + "，前三条 "
                        + String.join(" / ", Arrays.asList(log[0].split(";")).subList(0, Math.min(3, log[0].isEmpty() ? 0 : log[0].split(";").length)))
                        + "；" + Counts.read();
                boolean pass = build.result != null && build.result.done() == 16 && after[0] == 0 && after[1] == 16 && entries == 16 && cells.size() == 16;
                return new Outcome(pass, "MONITOR 16 条：主人、STONE、16 个不同坐标都在这片里；地上 0、背包 16", actual, problems);
            });
        }

        /**
         * B12：每格放置在混合端的实际耗时（只记录）。野外、自己领地、别人领地各 48 格：
         * ① 权限探测 {@code ECProtection.check}（每格清缓存、每格换一个调用方，不吃探测预算）；
         * ② 真放 {@code ECProtection.placeGuarded}（报副手，同魔杖），放完不计时地清掉。每段一刻，按格记纳秒，报平均 / p95 / 最大微秒。
         */
        private Case b12() {
            BoundingBox[] zones = {rel(16, 12, 4, 31, 12, 6), rel(0, 12, 8, 15, 12, 10), rel(32, 12, 8, 47, 12, 10)};
            String[] names = {"野外", "自己领地", "别人领地"};
            for (BoundingBox z : zones) {
                touched.add(z);
            }
            StringBuilder out = new StringBuilder();
            List<BooleanSupplier> steps = new ArrayList<>();
            steps.add(() -> {
                owner.getInventory().clearContent();
                owner.setItemInHand(InteractionHand.MAIN_HAND, points(BuilderMode.FILL, zones[0], Blocks.STONE));
                owner.moveTo(origin.getX() + 24.5D, origin.getY() + 16, origin.getZ() + 7.5D, 0F, 0F);
                return true;
            });
            for (int k = 0; k < zones.length; k++) {
                BoundingBox zone = zones[k];
                String name = names[k];
                steps.add(() -> {
                    long[] nanos = new long[48];
                    int allowed = 0;
                    int i = 0;
                    for (BlockPos pos : BlockPos.betweenClosed(zone.minX(), zone.minY(), zone.minZ(), zone.maxX(), zone.maxY(), zone.maxZ())) {
                        if (i >= nanos.length) {
                            break;
                        }
                        ECProtection.clearCache();
                        long t0 = System.nanoTime();
                        ECProtection.Verdict verdict = ECProtection.check(owner, level, pos.immutable(), new Object());
                        nanos[i++] = System.nanoTime() - t0;
                        if (verdict == ECProtection.Verdict.ALLOW) {
                            allowed++;
                        }
                    }
                    out.append(name).append(" 探测 ").append(micros(nanos, i)).append("（放行 ").append(allowed).append("/").append(i).append("）；");
                    return true;
                });
                steps.add(() -> {
                    long[] nanos = new long[48];
                    int placed = 0;
                    int i = 0;
                    List<BlockPos> done = new ArrayList<>();
                    for (BlockPos pos : BlockPos.betweenClosed(zone.minX(), zone.minY(), zone.minZ(), zone.maxX(), zone.maxY(), zone.maxZ())) {
                        if (i >= nanos.length) {
                            break;
                        }
                        BlockPos at = pos.immutable();
                        long t0 = System.nanoTime();
                        boolean ok = ECProtection.placeGuarded(owner, level, at, Blocks.STONE.defaultBlockState(), net.minecraft.core.Direction.UP,
                                InteractionHand.OFF_HAND, Block.UPDATE_ALL);
                        nanos[i++] = System.nanoTime() - t0;
                        if (ok) {
                            placed++;
                            done.add(at);
                        }
                    }
                    for (BlockPos at : done) {
                        level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                    out.append(name).append(" 真放 ").append(micros(nanos, i)).append("（放成 ").append(placed).append("/").append(i).append("）；");
                    return true;
                });
            }
            return new Case("B12", "每格放置的实际耗时：探测、真放（只记录）", 200, steps, () -> new Outcome(null, "只记录", out.toString(), List.of()));
        }

        /** 三阶魔杖（两点形状），MRU 满 */
        private ItemStack ascended(BuilderMode mode, BoundingBox box, @Nullable Block block) {
            ItemStack stack = points(mode, box, block);
            stack.getOrCreateTag().putInt(BuilderTier.TAG_TIER, BuilderTier.ASCENDED.level());
            return stack;
        }

        /** 9216 格的料：背包 20 叠石头 + 14 个测试收纳袋（每个 9 格 × 64），共 9344 块；带无尽 MRU 晶体 */
        private static void stressLoadout(ServerPlayer player) {
            giveCrystal(player);
            giveStacks(player, Items.STONE, 2, 20);
            for (int k = 0; k < 14; k++) {
                ItemStack bag = new ItemStack(Items.BUNDLE);
                IItemHandler handler = BuilderTestContainers.handler(bag);
                for (int s = 0; s < 9; s++) {
                    handler.insertItem(s, new ItemStack(Items.STONE, 64), false);
                }
                player.getInventory().items.set(22 + k, bag);
            }
        }

        /** 开工；要确认的过 8 刻再点一次（同玩家第二次右键） */
        private BooleanSupplier confirmIfNeeded(Job j) {
            int[] wait = {0};
            return () -> {
                if (j.started != BuilderJobs.StartResult.NEEDS_CONFIRM) {
                    return true;
                }
                if (++wait[0] < 8) {
                    return false;
                }
                j.started = BuilderJobs.start(j.player, InteractionHand.MAIN_HAND);
                return true;
            };
        }

        /**
         * B13：压测（只记录）。三阶魔杖铺 96×96 = 9216 格（每人每刻 128 格；全服每刻 {@code max_cells_per_tick_total}），材料一半以上在 14 个随身容器里：
         * S1 一人，耗时预算照配置；S2 四人同时，预算照配置；S3 四人同时，预算放到 4 ms。每段记整刻 / END 段耗时、做了几刻。
         * 四层在 y 40 / 44 / 48 / 52（相对原点），都在野外，每段开始前清空。
         */
        private Case b13() {
            int[] layers = {40, 44, 48, 52};
            List<BoundingBox> boxes = new ArrayList<>();
            for (int y : layers) {
                BoundingBox box = rel(0, y, 20, 95, y, 115);
                boxes.add(box);
                touched.add(box);
            }
            List<ServerPlayer> crew = new ArrayList<>();
            crew.add(owner);
            for (int k = 1; k < 4; k++) {
                ServerPlayer p = realPlayer(UUID.nameUUIDFromBytes(("ec5-selftest:stress" + k).getBytes(StandardCharsets.UTF_8)), "EC5Stress" + k,
                        new Connection(PacketFlow.SERVERBOUND));
                extras.add(p);
                crew.add(p);
            }
            List<TickSample> idle = new ArrayList<>();
            List<List<TickSample>> phases = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
            String[] phaseNames = {"S1 一人", "S2 四人", "S3 四人、预算 4 ms"};
            int[][] crewSize = {{1}, {4}, {4}};
            Double[] budgets = {null, null, 4.0D};
            List<List<Job>> jobs = new ArrayList<>();
            int[] ticks = new int[3];
            List<BooleanSupplier> steps = new ArrayList<>();
            int[] idleTicks = new int[1];
            steps.add(() -> {
                sampling = idle;
                return ++idleTicks[0] >= 60;
            });
            for (int ph = 0; ph < 3; ph++) {
                int phase = ph;
                List<Job> list = new ArrayList<>();
                for (int k = 0; k < crewSize[ph][0]; k++) {
                    ServerPlayer p = crew.get(k);
                    BoundingBox box = boxes.get(k);
                    list.add(job(p, ascended(BuilderMode.FILL, box, Blocks.STONE), origin.offset(48, layers[k] + 2, 68), () -> stressLoadout(p)));
                }
                jobs.add(list);
                steps.add(() -> {
                    for (BoundingBox box : boxes) {
                        clear(box);
                    }
                    ECBuilder.Config.Test.maxMillisPerTick = budgets[phase];
                    return true;
                });
                steps.add(() -> {
                    for (Job j : list) {
                        j.begin().getAsBoolean();
                    }
                    return true;
                });
                List<BooleanSupplier> confirms = new ArrayList<>();
                for (Job j : list) {
                    confirms.add(confirmIfNeeded(j));
                }
                steps.add(() -> {
                    boolean all = true;
                    for (BooleanSupplier c : confirms) {
                        all &= c.getAsBoolean();
                    }
                    if (all) {
                        sampling = phases.get(phase);
                    }
                    return all;
                });
                steps.add(() -> {
                    ticks[phase]++;
                    for (Job j : list) {
                        if (BuilderJobs.isRunning(j.player.getUUID())) {
                            return false;
                        }
                    }
                    for (Job j : list) {
                        j.result = BuilderJobs.lastResult(j.player.getUUID());
                    }
                    sampling = null;
                    ECBuilder.Config.Test.maxMillisPerTick = null;
                    return true;
                });
            }
            return new Case("B13", "压测：三阶 96×96，一人 / 四人（只记录）", 6000, steps, () -> {
                ECBuilder.Config.Test.maxMillisPerTick = null;
                StringBuilder actual = new StringBuilder("预算 " + ECBuilder.Config.maxMillisPerTick() + " ms / 全服 " + ECBuilder.Config.maxCellsPerTickTotal()
                        + " 格；空闲 " + stats(idle) + "；");
                for (int ph = 0; ph < 3; ph++) {
                    int done = 0;
                    StringBuilder who = new StringBuilder();
                    for (Job j : jobs.get(ph)) {
                        if (j.result != null) {
                            done += j.result.done();
                        }
                        who.append(j.player.getGameProfile().getName()).append(' ').append(j.describe()).append("，");
                    }
                    actual.append(phaseNames[ph]).append("：").append(who).append("合计放成 ").append(done).append("，用了 ").append(ticks[ph])
                            .append(" 刻（每刻平均 ").append(ticks[ph] == 0 ? 0 : done / ticks[ph]).append(" 格），").append(stats(phases.get(ph))).append("；");
                }
                return new Outcome(null, "只记录：MSPT、每刻格数", actual.toString(), List.of());
            });
        }

        /**
         * B14：几处单项开销（只记录）。主人拿三阶魔杖，背包 20 叠石头 + 14 个随身容器：
         * ① {@code BuilderMaterials.count}（状态包和暂停复核数料用的）；② {@code BuilderStatus.compute}（状态包整包）；
         * ③ 实体遮挡 {@code isUnobstructed}：96×96 一层空地逐格查，空地上没有实体 / 散放 64 个盔甲架各一遍，对照「一次取整片的实体列表」；
         * ④ MRU 扣费：{@code canAfford} + {@code playerUseMRU} 每格一次（带无尽晶体 / 只用魔杖自带的）；
         * ⑤ 开工前确认：32×4×32 选区点第一下（算确认信息）和第二下（真开工）的耗时，各三遍。
         */
        private Case b14() {
            BoundingBox layer = rel(0, 60, 20, 95, 60, 115);
            BoundingBox confirmBox = rel(0, 64, 20, 31, 67, 51);
            touched.add(layer);
            touched.add(confirmBox);
            StringBuilder out = new StringBuilder();
            List<BooleanSupplier> steps = new ArrayList<>();
            steps.add(() -> {
                owner.getInventory().clearContent();
                ItemStack wand = ascended(BuilderMode.FILL, layer, Blocks.STONE);
                owner.setItemInHand(InteractionHand.MAIN_HAND, wand);
                stressLoadout(owner);
                owner.moveTo(origin.getX() + 48.5D, origin.getY() + 62, origin.getZ() + 68.5D, 0F, 0F);
                long t0 = System.nanoTime();
                long[] last = null;
                for (int i = 0; i < 200; i++) {
                    last = BuilderMaterials.count(owner, Items.STONE, wand);
                }
                out.append("① 数料 每次 ").append(fmt((System.nanoTime() - t0) / 200 / 1000D)).append(" µs（数到 ").append(Arrays.toString(last)).append("）；");
                long now = level.getGameTime();
                t0 = System.nanoTime();
                for (int i = 0; i < 200; i++) {
                    BuilderStatus.compute(owner, now);
                }
                out.append("② 状态包 每次 ").append(fmt((System.nanoTime() - t0) / 200 / 1000D)).append(" µs；");
                return true;
            });
            steps.add(() -> {
                out.append("③ 遮挡 ").append(obstruction(layer, "无实体")).append("；");
                for (int k = 0; k < 64; k++) {
                    net.minecraft.world.entity.decoration.ArmorStand stand = new net.minecraft.world.entity.decoration.ArmorStand(level,
                            layer.minX() + 6 + (k % 8) * 12 + 0.5D, layer.minY(), layer.minZ() + 6 + (k / 8) * 12 + 0.5D);
                    stand.setNoGravity(true);
                    stand.addTag("ec5_stress");
                    level.addFreshEntity(stand);
                }
                return true;
            });
            steps.add(() -> {
                out.append(obstruction(layer, "64 个盔甲架")).append("；");
                for (net.minecraft.world.entity.Entity e : level.getEntities((net.minecraft.world.entity.Entity) null,
                        new AABB(layer.minX(), layer.minY() - 2, layer.minZ(), layer.maxX() + 1, layer.maxY() + 3, layer.maxZ() + 1),
                        e -> e.getTags().contains("ec5_stress"))) {
                    e.discard();
                }
                ItemStack wand = owner.getMainHandItem();
                long t0 = System.nanoTime();
                for (int i = 0; i < 9216; i++) {
                    if (com.flameforge.essentialcraft5.mru.MRUDrain.canAfford(owner, wand, 20)) {
                        com.flameforge.essentialcraft5.mru.MRUDrain.playerUseMRU(owner, wand, 20);
                    }
                }
                double crystal = (System.nanoTime() - t0) / 9216D / 1000D;
                owner.getInventory().items.set(1, ItemStack.EMPTY);
                wand.getCapability(ECCapabilities.MRU_ITEM).ifPresent(h -> h.setMRU(MRUStaffItem.storedMRU(wand) + 9216 * 20));
                t0 = System.nanoTime();
                for (int i = 0; i < 9216; i++) {
                    if (com.flameforge.essentialcraft5.mru.MRUDrain.canAfford(owner, wand, 20)) {
                        com.flameforge.essentialcraft5.mru.MRUDrain.playerUseMRU(owner, wand, 20);
                    }
                }
                double own = (System.nanoTime() - t0) / 9216D / 1000D;
                out.append("④ MRU 每格 带晶体 ").append(fmt(crystal)).append(" µs / 只用魔杖 ").append(fmt(own)).append(" µs（9216 格合计 ")
                        .append(fmt(crystal * 9.216D)).append(" / ").append(fmt(own * 9.216D)).append(" ms）；");
                owner.getInventory().clearContent();
                owner.setItemInHand(InteractionHand.MAIN_HAND, ascended(BuilderMode.FILL, confirmBox, Blocks.STONE));
                owner.moveTo(origin.getX() + 16.5D, origin.getY() + 70, origin.getZ() + 36.5D, 0F, 0F);
                out.append("⑤ 32×4×32 确认：");
                return true;
            });
            for (int round = 0; round < 3; round++) {
                int[] wait = {0};
                long[] first = {0};
                BuilderJobs.StartResult[] r1 = new BuilderJobs.StartResult[1];
                steps.add(() -> {
                    stressLoadout(owner);
                    long t0 = System.nanoTime();
                    r1[0] = BuilderJobs.start(owner, InteractionHand.MAIN_HAND);
                    first[0] = System.nanoTime() - t0;
                    return true;
                });
                steps.add(() -> {
                    if (++wait[0] < 8) {
                        return false;
                    }
                    long t0 = System.nanoTime();
                    BuilderJobs.StartResult r2 = BuilderJobs.start(owner, InteractionHand.MAIN_HAND);
                    long second = System.nanoTime() - t0;
                    BuilderJobs.stop(owner);
                    out.append("第一下 ").append(r1[0]).append(' ').append(fmt(first[0] / 1.0E6D)).append(" ms、第二下 ").append(r2).append(' ')
                            .append(fmt(second / 1.0E6D)).append(" ms；");
                    return true;
                });
                steps.add(() -> {
                    clear(confirmBox);
                    return true;
                });
            }
            return new Case("B14", "单项开销：数料、状态包、实体遮挡、MRU 扣费、开工确认（只记录）", 400, steps,
                    () -> new Outcome(null, "只记录", out.toString(), List.of()));
        }

        /** 一层逐格 isUnobstructed（放石头的状态）的总耗时，对照一次取整层的实体列表 */
        private String obstruction(BoundingBox layer, String label) {
            net.minecraft.world.level.block.state.BlockState stone = Blocks.STONE.defaultBlockState();
            net.minecraft.world.phys.shapes.CollisionContext context = net.minecraft.world.phys.shapes.CollisionContext.empty();
            int blocked = 0;
            long t0 = System.nanoTime();
            for (BlockPos pos : BlockPos.betweenClosed(layer.minX(), layer.minY(), layer.minZ(), layer.maxX(), layer.maxY(), layer.maxZ())) {
                if (!level.isUnobstructed(stone, pos, context)) {
                    blocked++;
                }
            }
            long perCell = System.nanoTime() - t0;
            t0 = System.nanoTime();
            int found = 0;
            for (int i = 0; i < 72; i++) {
                // 每刻 128 格大约跨两三条格子行，这里按每刻取一次 16×1×16 的范围算 72 刻
                int x = layer.minX() + (i % 6) * 16;
                int z = layer.minZ() + (i / 6) * 8;
                found += level.getEntities((net.minecraft.world.entity.Entity) null, new AABB(x, layer.minY(), z, x + 16, layer.minY() + 1, z + 8)).size();
            }
            long perTick = System.nanoTime() - t0;
            return label + "：逐格 9216 次合计 " + fmt(perCell / 1.0E6D) + " ms（每格 " + fmt(perCell / 9216D / 1000D) + " µs，挡住 " + blocked
                    + " 格），每刻取一次实体列表 72 次合计 " + fmt(perTick / 1.0E6D) + " ms（取到 " + found + "）";
        }

        /** 纳秒数组前 n 个的平均 / p95 / 最大（微秒） */
        private static String micros(long[] nanos, int n) {
            if (n == 0) {
                return "没有采样";
            }
            long[] sorted = Arrays.copyOf(nanos, n);
            Arrays.sort(sorted);
            double avg = Arrays.stream(sorted).average().orElse(0) / 1000D;
            return "平均 " + fmt(avg) + " / p95 " + fmt(sorted[(int) Math.floor((n - 1) * 0.95D)] / 1000D) + " / 最大 " + fmt(sorted[n - 1] / 1000D) + " µs";
        }

        // ------------------------------------------------------------------ 圈地

        /** 一段：发一次圈地请求，等测试插件回同一个序号 */
        private BooleanSupplier claim(String... ops) {
            String[] seq = new String[1];
            return () -> {
                if (seq[0] == null) {
                    seq[0] = String.valueOf(++claimSeq);
                    System.setProperty(PROP_CLAIM_REQUEST, seq[0] + "\n" + String.join("\n", ops));
                    return false;
                }
                String reply = System.getProperty(PROP_CLAIM_REPLY, "");
                if (!reply.startsWith(seq[0] + " ")) {
                    return false;
                }
                lastClaimReply = reply;
                if (reply.startsWith(seq[0] + " error")) {
                    claimErrors.add(reply);
                }
                return true;
            };
        }

        // ------------------------------------------------------------------ 世界边界

        private void moveBorder(int maxX) {
            WorldBorder border = level.getWorldBorder();
            if (savedBorder == null) {
                savedBorder = new double[]{border.getCenterX(), border.getCenterZ(), border.getSize()};
            }
            border.setCenter(maxX - 1000D, origin.getZ());
            border.setSize(2000D);
        }

        private void restoreBorder() {
            if (savedBorder != null) {
                WorldBorder border = level.getWorldBorder();
                border.setCenter(savedBorder[0], savedBorder[1]);
                border.setSize(savedBorder[2]);
                savedBorder = null;
            }
        }

        // ------------------------------------------------------------------ 工具

        private BoundingBox rel(int x1, int y1, int z1, int x2, int y2, int z2) {
            return new BoundingBox(origin.getX() + Math.min(x1, x2), origin.getY() + Math.min(y1, y2), origin.getZ() + Math.min(z1, z2),
                    origin.getX() + Math.max(x1, x2), origin.getY() + Math.max(y1, y2), origin.getZ() + Math.max(z1, z2));
        }

        private static String corners(BoundingBox box) {
            return box.minX() + " " + box.minY() + " " + box.minZ() + " " + box.maxX() + " " + box.maxY() + " " + box.maxZ();
        }

        private static String xyz(BlockPos pos) {
            return pos.getX() + " " + pos.getY() + " " + pos.getZ();
        }

        private static ItemStack wand(BuilderMode mode, int mru) {
            ItemStack stack = new ItemStack(ECBuilder.item(mode));
            stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(mru));
            return stack;
        }

        /** 两点形状、两个点（box 的两个角）和选定方块都设好、MRU 满的魔杖 */
        private ItemStack points(BuilderMode mode, BoundingBox box, @Nullable Block block) {
            ItemStack stack = wand(mode, FULL);
            BuilderSelection.setFirst(stack, new BlockPos(box.minX(), box.minY(), box.minZ()), level.dimension());
            BuilderSelection.setSecond(stack, new BlockPos(box.maxX(), box.maxY(), box.maxZ()));
            if (block != null) {
                BuilderSelection.setStoredBlock(stack, ForgeRegistries.BLOCKS.getKey(block));
            }
            return stack;
        }

        /** 从背包第 from 格起放 stacks 叠满 64 的 item */
        private static void giveStacks(ServerPlayer player, Item item, int from, int stacks) {
            for (int k = 0; k < stacks; k++) {
                player.getInventory().items.set(from + k, new ItemStack(item, 64));
            }
        }

        /** 从背包第 from 格起放 stacks 叠满的，再放一叠 rest 个 */
        private static void giveStacks(ServerPlayer player, Item item, int from, int stacks, int rest) {
            giveStacks(player, item, from, stacks);
            if (rest > 0) {
                player.getInventory().items.set(from + stacks, new ItemStack(item, rest));
            }
        }

        private static void giveCrystal(ServerPlayer player) {
            player.getInventory().items.set(1, new ItemStack(ECCreative.INFINITE_MRU_CRYSTAL.get()));
        }

        /** 背包 36 格 + 副手里没 NBT 的 item，加上背包里测试收纳袋里装的 */
        private static int countMaterial(ServerPlayer player, Item item) {
            int n = 0;
            List<ItemStack> all = new ArrayList<>(player.getInventory().items);
            all.add(player.getOffhandItem());
            for (ItemStack stack : all) {
                if (BuilderMaterials.isPlainMaterial(stack, item)) {
                    n += stack.getCount();
                } else if (stack.is(Items.BUNDLE)) {
                    IItemHandler handler = BuilderTestContainers.handler(stack);
                    for (int s = 0; s < handler.getSlots(); s++) {
                        if (BuilderMaterials.isPlainMaterial(handler.getStackInSlot(s), item)) {
                            n += handler.getStackInSlot(s).getCount();
                        }
                    }
                }
            }
            return n;
        }

        private int countBlocks(BoundingBox box, Block block) {
            int n = 0;
            for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
                if (level.getBlockState(pos).is(block)) {
                    n++;
                }
            }
            return n;
        }

        private void fill(BoundingBox box, Block block) {
            for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
                level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }

        private void clear(BoundingBox box) {
            for (BlockPos pos : BlockPos.betweenClosed(box.minX(), Math.max(level.getMinBuildHeight(), box.minY()), box.minZ(),
                    box.maxX(), Math.min(level.getMaxBuildHeight() - 1, box.maxY()), box.maxZ())) {
                if (!level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }

        /** 脱离网络的真 ServerPlayer（同 {@link HybridSelfTest} 的 realPlayer），连接由调用方给 */
        private ServerPlayer realPlayer(UUID id, String name, Connection connection) {
            ServerPlayer player = new ServerPlayer(server, level, new GameProfile(id, name));
            new ServerGamePacketListenerImpl(server, connection, player);
            player.moveTo(origin.getX() + 0.5D, origin.getY() + 40, origin.getZ() - 10.5D, 0F, 0F);
            listPlayer(player, true);
            return player;
        }

        /**
         * 把测试玩家挂进 / 摘出玩家列表（players 和按 UUID 的表，不走登录流程、不进世界）。
         * 混合端核心从 00697ac62 起，放置 / 破坏 / 权限探测在发给插件之前先按 UUID 查玩家列表，查不到当成已下线、直接取消，
         * 不挂进去的话每一格都被核心拒掉，插件一条事件也收不到，测不到领地插件本身。字段按泛型类型找（运行时是 SRG 名）。
         */
        @SuppressWarnings("unchecked")
        private void listPlayer(ServerPlayer player, boolean add) {
            PlayerList list = server.getPlayerList();
            for (java.lang.reflect.Field field : PlayerList.class.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                String type = field.getGenericType().getTypeName();
                boolean isList = type.equals("java.util.List<" + ServerPlayer.class.getName() + ">");
                boolean isByUuid = type.equals("java.util.Map<java.util.UUID, " + ServerPlayer.class.getName() + ">");
                // 混合端另有按小写名字的表（Bukkit 按名字找在线玩家用）
                boolean isByName = type.equals("java.util.Map<java.lang.String, " + ServerPlayer.class.getName() + ">");
                if (isByName) {
                    try {
                        field.setAccessible(true);
                        Map<String, ServerPlayer> byName = (Map<String, ServerPlayer>) field.get(list);
                        String key = player.getScoreboardName().toLowerCase(java.util.Locale.ROOT);
                        if (add) {
                            byName.put(key, player);
                        } else {
                            byName.remove(key, player);
                        }
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        EssentialCraft5.LOGGER.error("{} 测试玩家{}玩家列表失败：{}", TAG, add ? "挂进" : "摘出", field.getName(), e);
                    }
                    continue;
                }
                if (!isList && !isByUuid) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    if (isList) {
                        List<ServerPlayer> players = (List<ServerPlayer>) field.get(list);
                        players.remove(player);
                        if (add) {
                            players.add(player);
                        }
                    } else {
                        Map<UUID, ServerPlayer> byUuid = (Map<UUID, ServerPlayer>) field.get(list);
                        if (add) {
                            byUuid.put(player.getUUID(), player);
                        } else {
                            byUuid.remove(player.getUUID(), player);
                        }
                    }
                } catch (UnsupportedOperationException e) {
                    // 混合端另有一份只读视图（playersView），跟着 players 变，不用改
                } catch (ReflectiveOperationException | RuntimeException e) {
                    EssentialCraft5.LOGGER.error("{} 测试玩家{}玩家列表失败：{}", TAG, add ? "挂进" : "摘出", field.getName(), e);
                }
            }
        }

        private static String describe(@Nullable BuilderJobs.Result result) {
            return result == null ? "没有结果" : result.reason() + " 撤成 " + result.done() + " 被拒 " + result.denied() + " 跳过 " + result.skipped();
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Integer>[] newMaps(int n) {
            Map<String, Integer>[] maps = new Map[n];
            for (int i = 0; i < n; i++) {
                maps[i] = Map.of();
            }
            return maps;
        }
    }

    // ==================================================================
    // 插件的计数、玩家收到的提示
    // ==================================================================

    /** 测试插件数的事件（{@value #PROP_COUNTS}：{@code place|取消没有|手|物品|same或diff=n;breakHighest|取消没有|方块=n;breakMonitor|取消没有|方块=n}） */
    private record Counts(Map<String, Integer> all) {
        static Counts read() {
            Map<String, Integer> map = new LinkedHashMap<>();
            for (String part : System.getProperty(PROP_COUNTS, "").split(";")) {
                int eq = part.lastIndexOf('=');
                if (eq > 0) {
                    try {
                        map.put(part.substring(0, eq), Integer.parseInt(part.substring(eq + 1)));
                    } catch (NumberFormatException ignored) {
                        // 坏的一段跳过
                    }
                }
            }
            return new Counts(map);
        }

        int sum(String prefix) {
            int n = 0;
            for (Map.Entry<String, Integer> entry : all.entrySet()) {
                if (entry.getKey().startsWith(prefix)) {
                    n += entry.getValue();
                }
            }
            return n;
        }

        int placeAllowed() {
            return sum("place|false");
        }

        Map<String, Integer> placeKeys() {
            Map<String, Integer> map = new LinkedHashMap<>();
            all.forEach((k, v) -> {
                if (k.startsWith("place|")) {
                    map.put(k, v);
                }
            });
            return map;
        }

        @Override
        public String toString() {
            return "插件收到 放置 " + sum("place|") + " 条（取消 " + sum("place|true") + "）、破坏 HIGHEST " + sum("breakHighest|") + " 条（取消 "
                    + sum("breakHighest|true") + "）/ MONITOR " + sum("breakMonitor|") + " 条（取消 " + sum("breakMonitor|true") + "）" + (all.isEmpty() ? "" : " " + all);
        }
    }

    /**
     * 记下提示类包的假连接：本模组的（文字里带 {@code message.essentialcraft5} 翻译键）和别的（领地插件发的）分开数，别的留前三条原文。
     * 分类同 {@link HybridSelfTest#noticeKind}。
     */
    private static final class RecordingConnection extends Connection {
        private int ours;
        private final Map<String, Integer> theirs = new LinkedHashMap<>();
        private final List<String> samples = new ArrayList<>();

        RecordingConnection() {
            super(PacketFlow.SERVERBOUND);
        }

        @Override
        public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
            String kind = HybridSelfTest.noticeKind(packet);
            if (kind != null) {
                String text = packet instanceof ClientboundSystemChatPacket chat && chat.content() != null ? chat.content().toString()
                        : packet instanceof ClientboundSetActionBarTextPacket bar && bar.getText() != null ? bar.getText().toString() : "";
                if (text.contains("message.essentialcraft5")) {
                    ours++;
                } else {
                    theirs.merge(kind, 1, Integer::sum);
                    if (samples.size() < 3) {
                        String plain = packet instanceof ClientboundSystemChatPacket chat && chat.content() != null ? chat.content().getString()
                                : packet instanceof ClientboundSetActionBarTextPacket bar && bar.getText() != null ? bar.getText().getString() : kind;
                        samples.add(plain.length() > 80 ? plain.substring(0, 80) : plain);
                    }
                }
            }
            super.send(packet, listener);
        }

        void clear() {
            ours = 0;
            theirs.clear();
            samples.clear();
        }

        @Override
        public String toString() {
            return "玩家收到 本模组提示 " + ours + " 条、别的提示 " + theirs + (samples.isEmpty() ? "" : " 例 " + samples);
        }
    }

    private static String fmt(double value) {
        return String.format("%.2f", value);
    }
}
