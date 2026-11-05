package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.registry.ECEssentialTools;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 源质镐 / 源质斧的连带挖（batch12.md §4.4.2、§4.4.5）：中心那格挖掉时（{@code mineBlock}，破坏事件已放行、方块还没移除）只登记一个待办，
 * 到服务端 tick 的 {@code ServerTickEvent} END 再扣中心那格的钱、逐格调玩家本人的 {@code gameMode.destroyBlock}。
 * <p>
 * <b>为什么推迟</b>：Mohist 系混合端的 Bukkit 破坏事件是全局单槽，在另一次破坏过程里（{@code mineBlock}、{@code BreakEvent} 监听器）再发破坏事件，
 * 里层会把外层那一下的槽取走，中心那格被删却不掉落（policy §2.1 ②，同魔力挖掘器）。推迟以后每格都是一次完整、独立的玩家破坏：
 * Forge 的破坏事件照发、混合端照常转给领地插件按格拦，掉落、时运、精准采集、经验都照原版算；被拒的格子返回 false、什么都不发生、不扣钱。
 * <p>
 * <b>为什么挂在 {@code mineBlock} 而不是 {@code BreakEvent} 监听器</b>：{@code mineBlock} 只在真挖、而且破坏事件已经被所有监听器放行之后才调用，
 * 权限探测（{@code ECProtection} 发的探测事件）和别的模组的代破坏不会走到这里，也就不会把探测当成真挖；创造模式拆方块不调它，创造模式自然不连带。
 * 中心那格到 tick 末尾还要再看一眼确实没了（混合端的删方块可以被插件取消），没了才扣钱。
 * <p>
 * <b>源质镐</b>：面取玩家最后一次左键（服务端 {@code LeftClickBlock} 的 START）记下的格子和面，格子对不上就用视线射线打中的面；
 * 3×3 是垂直于这个面的平面上中心周围的 8 格。登记时按当时的方块挑出合格的邻格（规则见 {@link #areaMineable}），tick 末尾逐格再核一遍：
 * 还是登记时那种方块、规则仍满足、{@code level.mayInteract}（出生点保护、世界边界）→ 付得起 → 挖 → 挖成了才扣。付不起就停。
 * <p>
 * <b>源质斧</b>：砍的是 {@code #minecraft:logs} 时登记时就找好树（{@link #planFelling}），tick 末尾中心那格确认没了就建一个砍树任务；
 * 之后每刻 END 每个任务最多砍 {@code logs_per_tick} 根、全部任务合计最多 {@code logs_per_tick_total} 根，任务之间轮流；
 * 每根之前核对玩家还在线、还在这个维度、主手还是同一把斧（不满足整单作废），这一格还是那种原木（不是就跳过），付得起才砍，砍成了才扣。
 * 砍树期间设线程标记，这期间同一个世界里新加入的掉落物挪到中心那一格（{@link #essentialToolItemJoin}）。
 * <p>
 * 每名玩家每刻只有第一次挖掘会连带（同一刻里再挖的只扣中心那格），一个人同时只有一棵树在砍；潜行、假玩家、创造模式都不连带。
 * 只在服务端线程上用（表都不加锁）。处理器名带 {@code essentialTool} 前缀（tools/check_event_handlers.py）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class EssentialToolEvents {

    /** 连带挖 / 砍树的 destroyBlock 正在进行：两件工具自己的 mineBlock 看到它什么都不做（不在连带里再连带） */
    private static final ThreadLocal<Boolean> TOOL_MINING = ThreadLocal.withInitial(() -> Boolean.FALSE);
    /** 砍树的 destroyBlock 正在进行时，掉落物要挪去的地方 */
    private static final ThreadLocal<DropTarget> FELLING_DROP = new ThreadLocal<>();

    /** 玩家 UUID → 最后一次拿着源质镐左键的格子和面 */
    private static final Map<UUID, Hit> LAST_HIT = new HashMap<>();
    /** 本刻登记的待办（按登记顺序处理） */
    private static final List<Pending> PENDING = new ArrayList<>();
    /** 本刻已经登记过一次连带的玩家（同一刻里再挖的只扣中心那格） */
    private static final Set<UUID> CHAINED_THIS_TICK = new HashSet<>();
    /** 玩家 UUID → 正在进行的砍树任务（每人最多一个，按开始顺序） */
    private static final Map<UUID, FellingJob> JOBS = new LinkedHashMap<>();
    /** 砍树任务轮流的起点 */
    private static int rotation;

    private record Hit(BlockPos pos, Direction face) {
    }

    private record Cell(BlockPos pos, Block block) {
    }

    private record DropTarget(Level level, BlockPos center) {
    }

    /**
     * 一次待办。
     *
     * @param cost           每格的价钱（登记时取）
     * @param chain          要不要连带（不潜行、真玩家、不是创造模式、本刻第一次）
     * @param cells          源质镐：登记时合格的邻格
     * @param centerHardness 源质镐：中心那格的硬度（邻格硬度上限按它算）
     * @param logs           源质斧：要砍的原木（不含中心；空 = 只砍中心）
     */
    private record Pending(ServerPlayer player, ResourceKey<Level> dimension, BlockPos center, Block centerBlock, ItemStack tool,
                           int cost, boolean chain, boolean axe, List<Cell> cells, float centerHardness, List<BlockPos> logs) {
    }

    private record FellingJob(ServerPlayer player, ResourceKey<Level> dimension, BlockPos center, Block block, ItemStack tool,
                              int cost, Deque<BlockPos> logs) {
    }

    private EssentialToolEvents() {
    }

    /** 当前是否在源质镐 / 源质斧的连带挖里（这些格子仍是玩家本人挖的，照常算掉落、经验和统计） */
    public static boolean isToolMining() {
        return TOOL_MINING.get();
    }

    // ------------------------------------------------------------------
    // 登记
    // ------------------------------------------------------------------

    /**
     * 两件工具的 {@code mineBlock} 转调这里（只在服务端真挖时、破坏事件放行之后、方块移除之前）。
     * 只对这把工具的适用方块生效；付不起一格时什么都不登记（中心那格照原版规则挖掉、不扣、不连带）。
     */
    static void onToolMined(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity entity) {
        if (level.isClientSide || TOOL_MINING.get() || !(entity instanceof ServerPlayer player) || !(stack.getItem() instanceof EssentialTool tool)
                || !state.is(tool.effectiveBlocks())) {
            return;
        }
        int cost = tool.mruCost();
        if (!MRUDrain.canAfford(player, stack, cost)) {
            return;
        }
        // 假玩家（别的模组的自动化拿着它挖）只当普通工具：它的破坏事件混合端不转给领地插件，不许替它连带
        boolean chain = !player.isShiftKeyDown() && !player.isCreative() && ECProtection.isRealPlayer(player)
                && !CHAINED_THIS_TICK.contains(player.getUUID());
        boolean axe = tool instanceof EssentialAxeItem;
        List<Cell> cells = List.of();
        List<BlockPos> logs = List.of();
        float hardness = state.getDestroySpeed(level, pos);
        if (chain) {
            CHAINED_THIS_TICK.add(player.getUUID());
            ECEssentialTools.Config.Values cfg = ECEssentialTools.Config.values();
            if (axe) {
                if (state.is(BlockTags.LOGS) && !JOBS.containsKey(player.getUUID())) {
                    logs = planFelling(level, pos, state.getBlock(), cfg);
                }
            } else {
                cells = planArea(level, pos, faceFor(player, pos), stack, hardness + cfg.hardnessMargin());
            }
        }
        PENDING.add(new Pending(player, level.dimension(), pos.immutable(), state.getBlock(), stack, cost, chain, axe, cells, hardness, logs));
    }

    /** 源质镐的面：最后一次左键的格子就是这一格时用记下的面，否则用视线射线打中的面，什么都没打中时取视线最靠近的那个方向的反面 */
    static Direction faceFor(ServerPlayer player, BlockPos center) {
        Hit hit = LAST_HIT.get(player.getUUID());
        if (hit != null && hit.pos().equals(center)) {
            return hit.face();
        }
        HitResult result = player.pick(player.getBlockReach(), 1F, false);
        if (result instanceof BlockHitResult block && result.getType() == HitResult.Type.BLOCK) {
            return block.getDirection();
        }
        return Direction.orderedByNearest(player)[0].getOpposite();
    }

    /** 垂直于 face 的平面上，center 周围的 8 格 */
    static List<BlockPos> planeAround(BlockPos center, Direction face) {
        List<BlockPos> cells = new ArrayList<>(8);
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                if (a == 0 && b == 0) {
                    continue;
                }
                cells.add(switch (face.getAxis()) {
                    case X -> center.offset(0, a, b);
                    case Y -> center.offset(a, 0, b);
                    case Z -> center.offset(a, b, 0);
                });
            }
        }
        return cells;
    }

    private static List<Cell> planArea(Level level, BlockPos center, Direction face, ItemStack tool, double maxHardness) {
        List<Cell> cells = new ArrayList<>(8);
        for (BlockPos cell : planeAround(center, face)) {
            if (!level.isLoaded(cell)) {
                continue;
            }
            BlockState state = level.getBlockState(cell);
            if (areaMineable(level, cell, state, tool, maxHardness)) {
                cells.add(new Cell(cell, state.getBlock()));
            }
        }
        return cells;
    }

    /**
     * 源质镐的邻格规则（batch12.md §4.4.2）：不是空气；镐的适用方块；用这把镐挖得出掉落（{@code isCorrectToolForDrops}）；
     * 硬度 ≥ 0（基岩、屏障这类挖不动的不碰）且不超过 maxHardness；没有方块实体（箱子、熔炉、刷怪笼、模组机器一律不碰）；
     * 不在 {@code essentialcraft5:essential_tools/no_area} 里。
     */
    static boolean areaMineable(Level level, BlockPos pos, BlockState state, ItemStack tool, double maxHardness) {
        if (state.isAir() || !state.is(BlockTags.MINEABLE_WITH_PICKAXE) || state.is(ECEssentialTools.NO_AREA) || state.hasBlockEntity()
                || !tool.isCorrectToolForDrops(state)) {
            return false;
        }
        float hardness = state.getDestroySpeed(level, pos);
        return hardness >= 0F && hardness <= maxHardness;
    }

    /**
     * 源质斧找树（batch12.md §4.4.2）：从 center 起广度优先，26 邻接，只认和中心同一种方块；水平 |dx|、|dz| ≤ {@code felling_radius}，
     * dy 在 0 到 {@code felling_height} 之间（只往上和同层找，不碰树桩下面）；最多看 {@code max_scan} 格、最多收 {@code max_logs} 根（含中心，近的先收）；
     * 只读已加载的区块。收到的原木挨着的天然树叶不够 {@code leaves_required} 片时返回空（只砍中心）。
     * {@code max_logs} 是 1 时不找（上限含中心，中心一根就满了；下面的循环是收进一根以后才比上限，会多收一根）。
     * 公开给 GameTest 直接拿不同的数值调（测试不改配置）。
     *
     * @return 要砍的原木，不含中心
     */
    public static List<BlockPos> planFelling(Level level, BlockPos center, Block block, ECEssentialTools.Config.Values cfg) {
        if (cfg.maxLogs() <= 1) {
            return List.of();
        }
        List<BlockPos> logs = new ArrayList<>();
        logs.add(center.immutable());
        Set<BlockPos> visited = new HashSet<>();
        visited.add(center.immutable());
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(center.immutable());
        int scanned = 0;
        search:
        while (!queue.isEmpty()) {
            BlockPos at = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        BlockPos next = at.offset(dx, dy, dz);
                        int rise = next.getY() - center.getY();
                        if (Math.abs(next.getX() - center.getX()) > cfg.fellingRadius() || Math.abs(next.getZ() - center.getZ()) > cfg.fellingRadius()
                                || rise < 0 || rise > cfg.fellingHeight() || !visited.add(next)) {
                            continue;
                        }
                        if (++scanned > cfg.maxScan()) {
                            break search;
                        }
                        if (level.isLoaded(next) && level.getBlockState(next).is(block)) {
                            logs.add(next);
                            if (logs.size() >= cfg.maxLogs()) {
                                break search;
                            }
                            queue.add(next);
                        }
                    }
                }
            }
        }
        if (logs.size() <= 1 || !isNaturalTree(level, logs, cfg.leavesRequired())) {
            return List.of();
        }
        return List.copyOf(logs.subList(1, logs.size()));
    }

    /**
     * 天然树判定：logs 的六个邻格里，不是玩家放的树叶（{@code #minecraft:leaves} 且 {@code persistent} 为假；没有这个属性的模组树叶也算）
     * 加上下界疣块、诡异疣块（巨型菌没有树叶），不重复地数，够 required 片就是天然树。required ≤ 0 恒为真。
     */
    static boolean isNaturalTree(Level level, List<BlockPos> logs, int required) {
        if (required <= 0) {
            return true;
        }
        Set<BlockPos> counted = new HashSet<>();
        for (BlockPos log : logs) {
            for (Direction direction : Direction.values()) {
                BlockPos next = log.relative(direction);
                if (counted.contains(next) || !level.isLoaded(next)) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                boolean natural = state.is(BlockTags.LEAVES)
                        ? !state.hasProperty(LeavesBlock.PERSISTENT) || !state.getValue(LeavesBlock.PERSISTENT)
                        : state.is(Blocks.NETHER_WART_BLOCK) || state.is(Blocks.WARPED_WART_BLOCK);
                if (natural && counted.add(next) && counted.size() >= required) {
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 事件
    // ------------------------------------------------------------------

    /** 记下拿着源质镐左键的格子和面（只认服务端的 START：开始挖一格的那一下） */
    @SubscribeEvent
    public static void essentialToolLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide() || event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START
                || event.getFace() == null || !(event.getEntity().getMainHandItem().getItem() instanceof EssentialPickaxeItem)) {
            return;
        }
        LAST_HIT.put(event.getEntity().getUUID(), new Hit(event.getPos().immutable(), event.getFace()));
    }

    /** 本刻末尾：先处理本刻登记的待办（可能建出砍树任务），再推进砍树任务 */
    @SubscribeEvent
    public static void essentialToolServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        CHAINED_THIS_TICK.clear();
        if (!PENDING.isEmpty()) {
            List<Pending> batch = new ArrayList<>(PENDING);
            PENDING.clear();
            for (Pending pending : batch) {
                run(event.getServer(), pending);
            }
        }
        if (!JOBS.isEmpty()) {
            runJobs(event.getServer());
        }
    }

    /** 砍树期间新加入的掉落物挪到中心那一格（已经砍掉，是空的）的正中，速度清零：树冠上的原木不会卡在树叶上、也不会四处飞 */
    @SubscribeEvent
    public static void essentialToolItemJoin(EntityJoinLevelEvent event) {
        DropTarget target = FELLING_DROP.get();
        if (target == null || event.loadedFromDisk() || event.getLevel() != target.level() || !(event.getEntity() instanceof ItemEntity item)) {
            return;
        }
        BlockPos center = target.center();
        item.setPos(center.getX() + 0.5D, center.getY() + 0.5D, center.getZ() + 0.5D);
        item.setDeltaMovement(Vec3.ZERO);
    }

    @SubscribeEvent
    public static void essentialToolLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        LAST_HIT.remove(id);
        JOBS.remove(id);
        PENDING.removeIf(pending -> pending.player().getUUID().equals(id));
    }

    @SubscribeEvent
    public static void essentialToolServerStopped(ServerStoppedEvent event) {
        LAST_HIT.clear();
        PENDING.clear();
        CHAINED_THIS_TICK.clear();
        JOBS.clear();
    }

    // ------------------------------------------------------------------
    // 执行
    // ------------------------------------------------------------------

    /** 处理一个待办：中心那格确实没了才扣它的钱，扣成了、要连带、玩家还拿着同一把工具才连带 */
    private static void run(MinecraftServer server, Pending pending) {
        ServerPlayer player = pending.player();
        ServerLevel level = server.getLevel(pending.dimension());
        if (player.hasDisconnected() || level == null || !level.isLoaded(pending.center())
                || level.getBlockState(pending.center()).is(pending.centerBlock())) {
            return;
        }
        if (!MRUDrain.playerUseMRU(player, pending.tool(), pending.cost()) || !pending.chain()
                || !stillHolding(player, pending.tool(), pending.dimension())) {
            return;
        }
        if (pending.axe()) {
            if (!pending.logs().isEmpty() && !JOBS.containsKey(player.getUUID())) {
                JOBS.put(player.getUUID(), new FellingJob(player, pending.dimension(), pending.center(), pending.centerBlock(), pending.tool(),
                        pending.cost(), new ArrayDeque<>(pending.logs())));
            }
            return;
        }
        double maxHardness = pending.centerHardness() + ECEssentialTools.Config.values().hardnessMargin();
        for (Cell cell : pending.cells()) {
            // 挖的过程中玩家死了、主手被别的模组换掉了：停
            if (!stillHolding(player, pending.tool(), pending.dimension())) {
                return;
            }
            BlockPos pos = cell.pos();
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!state.is(cell.block()) || !areaMineable(level, pos, state, pending.tool(), maxHardness) || !level.mayInteract(player, pos)) {
                continue;
            }
            if (!MRUDrain.canAfford(player, pending.tool(), pending.cost())) {
                return;
            }
            if (breakAs(player, level, pos, state, null)) {
                MRUDrain.playerUseMRU(player, pending.tool(), pending.cost());
            }
        }
    }

    /** 推进全部砍树任务：全服每刻合计 {@code logs_per_tick_total} 根，每个任务至多 {@code logs_per_tick} 根，起点每刻轮换 */
    private static void runJobs(MinecraftServer server) {
        ECEssentialTools.Config.Values cfg = ECEssentialTools.Config.values();
        List<FellingJob> order = new ArrayList<>(JOBS.values());
        int start = Math.floorMod(rotation++, order.size());
        int budget = cfg.logsPerTickTotal();
        for (int i = 0; i < order.size() && budget > 0; i++) {
            budget -= runJob(server, order.get((start + i) % order.size()), Math.min(cfg.logsPerTick(), budget));
        }
    }

    /** @return 这一刻在这个任务上调了几次 destroyBlock */
    private static int runJob(MinecraftServer server, FellingJob job, int limit) {
        ServerPlayer player = job.player();
        ServerLevel level = server.getLevel(job.dimension());
        int attempts = 0;
        while (attempts < limit) {
            if (level == null || !stillHolding(player, job.tool(), job.dimension())) {
                JOBS.remove(player.getUUID(), job);
                return attempts;
            }
            BlockPos pos = job.logs().poll();
            if (pos == null) {
                break;
            }
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!state.is(job.block()) || !level.mayInteract(player, pos)) {
                continue;
            }
            if (!MRUDrain.canAfford(player, job.tool(), job.cost())) {
                JOBS.remove(player.getUUID(), job);
                return attempts;
            }
            attempts++;
            if (breakAs(player, level, pos, state, new DropTarget(level, job.center()))) {
                MRUDrain.playerUseMRU(player, job.tool(), job.cost());
            }
        }
        if (job.logs().isEmpty()) {
            JOBS.remove(player.getUUID(), job);
        }
        return attempts;
    }

    /**
     * 以玩家本人的身份挖一格（真破坏事件、掉落、附魔、经验），返回那一格是不是真的变了：destroyBlock 在方块没删掉时也可能返回 true
     * （删方块被混合端的插件取消）。挖成的格子另给挖的人自己补一次破碎粒子和声音：原版只发给附近的其他人（挖的人的客户端本来会自己放，
     * 但连带的格子不是他的客户端挖的）。
     */
    private static boolean breakAs(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState before, @Nullable DropTarget drops) {
        boolean destroyed;
        TOOL_MINING.set(Boolean.TRUE);
        if (drops != null) {
            FELLING_DROP.set(drops);
        }
        try {
            destroyed = player.gameMode.destroyBlock(pos);
        } finally {
            TOOL_MINING.set(Boolean.FALSE);
            FELLING_DROP.remove();
        }
        if (!destroyed || level.getBlockState(pos).is(before.getBlock())) {
            return false;
        }
        player.connection.send(new ClientboundLevelEventPacket(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(before), false));
        return true;
    }

    private static boolean stillHolding(ServerPlayer player, ItemStack tool, ResourceKey<Level> dimension) {
        return !player.hasDisconnected() && !player.isRemoved() && player.isAlive() && !player.isSpectator()
                && player.level().dimension() == dimension && player.getMainHandItem() == tool;
    }

    // ------------------------------------------------------------------
    // GameTest
    // ------------------------------------------------------------------

    /** 这名玩家有没有正在进行的砍树任务（GameTest 用） */
    public static boolean isFelling(UUID player) {
        return JOBS.containsKey(player);
    }

    /** 清掉这名玩家留下的待办和砍树任务（GameTest 收尾用：测试失败时别把任务留给下一条测试） */
    public static void forgetForTest(UUID player) {
        LAST_HIT.remove(player);
        JOBS.remove(player);
        PENDING.removeIf(pending -> pending.player().getUUID().equals(player));
        CHAINED_THIS_TICK.remove(player);
    }
}
