package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.registry.ECBuilder;
import com.flameforge.essentialcraft5.util.ECItemEntities;
import com.flameforge.essentialcraft5.util.ECLevels;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 建筑魔杖的施工（{@link MagicalBuilderItem}）：开工时登记一个任务，每刻在 {@code ServerTickEvent} END 做一批格子。
 * 写在刻末尾、不嵌在别的破坏过程里（同 {@link MagicalDiggerEvents}：混合端的 Bukkit 破坏事件全局只有一个槽）。
 * <h2>开工的条件（{@link #start}）</h2>
 * 真玩家（别的模组的假玩家拿着魔杖不施工）、能改方块（冒险模式不行）、这个人没有在做的活、两个点都有、点就在玩家所在的维度、
 * 除「清除」以外都要有选定方块、选区不超过 {@code max_volume} 格、玩家离选区 {@code max_reach} 格以内、选区的区块全部已加载。
 * <h2>每刻</h2>
 * <ul>
 *   <li>先复核：没下线、活着、不是旁观、还能改方块、同一个维度、同一只手里还是<b>同一个</b>魔杖物品对象、离选区 {@code max_reach} 格以内；
 *       任何一条不满足就停（「中断」），已经做好的不退。每做完一格（发过事件的）再查一次前几条：事件的监听器可能让玩家死掉、换掉手里的东西。</li>
 *   <li>被拒（领地拒绝、删除被取消、放置被拒）累计 {@value #DENIED_LIMIT} 格就停（「没有权限」）：每格被拒都是真发了一次事件，
 *       混合端上放置被取消会把整份背包重发给客户端，领地插件每拦一次可能提示一句，整片框在别人领地里时不停就是几千次。</li>
 *   <li>预算：每个任务每刻最多<b>动</b> {@code cells_per_tick} 格，全服所有任务合计 {@code max_cells_per_tick_total} 格，按轮转的顺序平分
 *       （每个任务分到「剩下的 ÷ 还没轮到的任务数」，用不完的留给后面的）。「动」是指真要放、拆、换的格子（要算放置状态、查实体、发事件）；
 *       不用动的格子（填充模式里已有方块的、清除模式里的空气）只读一下方块，不算进预算，但每个任务每刻最多看 {@value #SCAN_PER_TICK} 格。</li>
 *   <li>顺序见 {@link BuilderSelection#cellAt} 和 {@link BuilderMode#bottomUp}；每格怎么处理见 {@link BuilderMode#decide}。
 *       读格子前查 {@code ECLevels.isFullyLoaded}，没加载的跳过（不等它、不触发加载）。</li>
 * </ul>
 * <h2>每格（只按做成的格子收费）</h2>
 * <pre>
 * 放：算放置状态（物品自己的规则、朝向按玩家视线；台阶放单层、海泡菜和蜡烛放 1 个、作物从最小的长起），为 null、立不住、被实体挡着 → 跳过（记进「放不下」）；
 *     背包里没有材料 → 停「材料用完」；MRU 不够 25 → 停；取 1 个材料 → placeGuarded 放行：扣 25；被拒：材料原样放回
 * 拆：MRU 不够 250 → 停；harvestGuarded 没拆 → 跳过、不扣；拆了：扣 250，掉落进本刻的掉落表
 *     门、高花的上半，伸出的活塞头：改拆主体那一格（下半、底座），见 {@link #breakMainPart}
 * 换：新方块在这格放不下（放置状态按这格现在的流体算）、立不住、被挡着 → 跳过（连拆都不拆）；没有材料 → 停；MRU 不够 300 → 停；harvestGuarded 没拆 → 跳过；
 *     拆了以后重算放置状态 → placeGuarded 放行：扣 300、用掉材料；被拒（或放不下）：格子留成空气，掉落照给，只扣 250，材料不动
 * </pre>
 * 放：{@code ECProtection.placeGuarded(玩家, …, Direction.UP, 另一只手, UPDATE_ALL)}——手传没拿魔杖的那只：混合端按这只手报放置用的物品
 * （传 null 时报主手，魔杖拿在主手就成了「放下了一根魔杖」），纯 Forge 不看手；
 * 不调 {@code setPlacedBy}（带方块实体的、分两半的方块本来就选不了）。拆：{@code ECProtection.harvestGuarded(玩家, …, 空手, true)}
 * （发破坏事件时主手拿空；先删、删成了才算掉落；掉落按空手算：钻石矿掉钻石、石头掉圆石、玻璃和冰什么都不掉；不出蠹虫、不给经验）。
 * 用的是批量写法：删的时候没有破坏粒子和声音、不写探测缓存。每格都出原版那份效果时，64 格内的客户端每刻要生成约 4 000 个粒子、放 64 次声音，
 * 实测帧时间从 8.5 ms 掉到 21~45 ms；所以效果由这里补发，每个任务每刻最多 {@value #MAX_EFFECTS_PER_TICK} 格（份额 64 时是第 0、16、32、48 格）。
 * 两个工具里都查过出生点保护和世界边界。材料只用背包（含副手）里<b>没有 NBT</b> 的那种物品：改过名、装了东西的不碰，放回去的和取出来的一模一样。
 * MRU 先 {@code MRUDrain.canAfford} 问够不够、做成了再 {@code playerUseMRU}（先扣身上的储存物品，再扣魔杖自己的）。
 * 掉落每刻合并成整组交给玩家（{@code ECItemEntities.giveOrDrop}，放不下的掉在脚下，玩家这一刻里死了就掉在原地；不用 {@code Inventory#add}：创造模式下塞不下会吞掉）。
 * 创造模式不扣 MRU、不要材料、拆下来的东西不给。
 * <p>
 * 任务表不存盘：玩家下线、停服时直接丢掉（已经做好的留着，掉落每刻都交付了，手上没有积压）。只在服务端线程上用。
 * 类名带模块前缀：Forge 按「包名 + 简单类名 + 方法名 + 事件类型」认监听器（见 tools/check_event_handlers.py）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class BuilderJobs {

    /** 每个任务每刻最多看几格（含不用动的格子）：4 096 格的选区里全是不用动的格子时也要 4 刻才扫完 */
    public static final int SCAN_PER_TICK = 1024;
    /** 拆的时候每隔几格补发一次破坏效果（每刻份额比 {@code EFFECT_STRIDE × MAX_EFFECTS_PER_TICK} 大时间隔跟着放宽） */
    private static final int EFFECT_STRIDE = 16;
    /** 每个任务每刻最多给几格补发破坏效果（粒子和声音） */
    public static final int MAX_EFFECTS_PER_TICK = 4;
    /**
     * 一个任务累计被拒这么多格就停下（4 刻的量）。按累计算、不按连续算：同一层里 x 在外、z 在内地扫，选区 x 大的那一半在别人领地里时，
     * 每层连续被拒的格子就有上百个，按连续算会在第一层就误停、重开也停在同一处；一行被拒一行放行地交替时，连续计数又会不停清零、封不住总量。
     * 被拒不到这么多的小块交叠照常做完。
     */
    public static final int DENIED_LIMIT = 256;
    /** 最近结束的任务结果按玩家留几条（GameTest、排查用） */
    private static final int RESULT_HISTORY = 64;

    /** 玩家 UUID → 正在做的任务（按开工顺序） */
    private static final Map<UUID, Job> JOBS = new LinkedHashMap<>();
    private static final Map<UUID, Result> RESULTS = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Result> eldest) {
            return size() > RESULT_HISTORY;
        }
    };
    /** 轮转的起点：每刻往后挪一个，全服预算不够分时轮流先做 */
    private static int rotation;

    private BuilderJobs() {
    }

    /** {@link #start} 的结果 */
    public enum StartResult {
        STARTED,
        /** 不是真玩家（假玩家、客户端），不提示 */
        NOT_REAL_PLAYER,
        /** 这个人已经有活在做 */
        BUSY,
        /** 冒险模式等不能改方块的情况 */
        CANNOT_BUILD,
        /** 手里不是建筑魔杖 */
        NOT_A_WAND,
        NO_POINTS,
        /** 点不在玩家所在的维度 */
        OTHER_DIMENSION,
        /** 这个模式要选定方块，但没选（或者选的已经不能用了） */
        NO_BLOCK,
        TOO_BIG,
        OUT_OF_REACH,
        /** 选区有区块没加载 */
        NOT_LOADED
    }

    /** 任务为什么结束 */
    public enum StopReason {
        FINISHED,
        /** 玩家自己潜行对空气右键停下 */
        STOPPED,
        OUT_OF_MATERIAL,
        NO_MRU,
        /** 下线以外的复核没过：走远、换手、换维度、死亡、改成冒险模式 */
        INTERRUPTED,
        /** 被拒累计 {@link #DENIED_LIMIT} 格（选区大片落在别人的领地里） */
        DENIED
    }

    /**
     * 结束了的任务：为什么结束、做成了几格、被拒了几格、新方块放不下跳过了几格、补发破坏效果最多的那一刻有几格（后三个给 GameTest 和排查用）
     */
    public record Result(StopReason reason, int done, int denied, int skipped, int peakEffects) {
    }

    private static final class Job {
        final ServerPlayer player;
        final InteractionHand hand;
        final ItemStack wand;
        final BuilderMode mode;
        final ResourceKey<Level> dimension;
        final BoundingBox box;
        final int total;
        @Nullable
        final Block block;
        @Nullable
        final Item material;
        /** 下一格的序号（{@link BuilderSelection#cellAt}） */
        int next;
        /** 做成了几格 */
        int done;
        /** 上次找到材料的格子（先看它，省得每格扫一遍背包） */
        int materialSlot = -1;
        /** 停在「MRU 不够」时那一格要多少 */
        int needed;
        /** 被拒了几格（{@link #DENIED_LIMIT}） */
        int denied;
        /** 新方块在这格放不下（物品不让放、立不住、被挡着）跳过了几格 */
        int skipped;
        /** 本刻补发了几格破坏效果 */
        int effects;
        /** 补发破坏效果最多的那一刻有几格 */
        int peakEffects;
        /** 本刻最后放下的一格（放一次声音） */
        @Nullable
        BlockPos lastPlacedPos;
        @Nullable
        BlockState lastPlacedState;

        Job(ServerPlayer player, InteractionHand hand, ItemStack wand, BuilderMode mode, BoundingBox box, @Nullable Block block) {
            this.player = player;
            this.hand = hand;
            this.wand = wand;
            this.mode = mode;
            this.dimension = player.level().dimension();
            this.box = box;
            this.total = BuilderSelection.cellCount(box);
            this.block = block;
            this.material = block == null ? null : block.asItem();
        }
    }

    /** 一格的处理结果 */
    private enum Outcome {
        /** 不用动，没算进预算 */
        SKIPPED,
        /** 动过（做没做成都算进预算） */
        ATTEMPTED,
        OUT_OF_MATERIAL,
        NO_MRU
    }

    // ------------------------------------------------------------------
    // 开工、停下、查询
    // ------------------------------------------------------------------

    /**
     * 用 hand 里的建筑魔杖开工（服务端；魔杖对空气右键时调，GameTest 直接调）。成功和失败都在动作栏提示一句（假玩家除外）。
     */
    public static StartResult start(ServerPlayer player, InteractionHand hand) {
        if (!ECProtection.isRealPlayer(player)) {
            return StartResult.NOT_REAL_PLAYER;
        }
        if (JOBS.containsKey(player.getUUID())) {
            MagicalBuilderItem.tellError(player, Component.translatable("message.essentialcraft5.builder.busy"));
            return StartResult.BUSY;
        }
        if (!player.mayBuild()) {
            MagicalBuilderItem.tellError(player, Component.translatable("message.essentialcraft5.builder.cannot_build"));
            return StartResult.CANNOT_BUILD;
        }
        ItemStack wand = player.getItemInHand(hand);
        BuilderMode mode = BuilderMode.of(wand);
        if (mode == null) {
            return StartResult.NOT_A_WAND;
        }
        BlockPos first = BuilderSelection.first(wand);
        BlockPos second = BuilderSelection.second(wand);
        if (first == null || second == null || BuilderSelection.dimension(wand) == null) {
            MagicalBuilderItem.tellError(player, Component.translatable("message.essentialcraft5.builder.no_points"));
            return StartResult.NO_POINTS;
        }
        if (!BuilderSelection.inDimension(wand, player.level().dimension())) {
            MagicalBuilderItem.tellError(player, Component.translatable("message.essentialcraft5.builder.other_dimension"));
            return StartResult.OTHER_DIMENSION;
        }
        Block block = MagicalBuilderItem.storedBlock(wand);
        if (mode.needsBlock() && block == null) {
            MagicalBuilderItem.tellError(player, Component.translatable("message.essentialcraft5.builder.no_block"));
            return StartResult.NO_BLOCK;
        }
        long volume = BuilderSelection.volume(first, second);
        int maxVolume = ECBuilder.Config.maxVolume();
        if (volume > maxVolume) {
            MagicalBuilderItem.tellError(player, Component.translatable("message.essentialcraft5.builder.too_big", volume, maxVolume));
            return StartResult.TOO_BIG;
        }
        BoundingBox box = BuilderSelection.box(first, second);
        int reach = ECBuilder.Config.maxReach();
        if (BuilderSelection.distanceSqToBox(player.position(), box) > (double) reach * reach) {
            MagicalBuilderItem.tellError(player, Component.translatable("message.essentialcraft5.builder.out_of_reach", reach));
            return StartResult.OUT_OF_REACH;
        }
        if (!chunksLoaded(player.level(), box)) {
            MagicalBuilderItem.tellError(player, Component.translatable("message.essentialcraft5.builder.not_loaded"));
            return StartResult.NOT_LOADED;
        }
        Job job = new Job(player, hand, wand, mode, box, block);
        JOBS.put(player.getUUID(), job);
        MagicalBuilderItem.tell(player, Component.translatable("message.essentialcraft5.builder.started", job.total));
        return StartResult.STARTED;
    }

    /** 停下这个人正在做的活（潜行对空气右键）；没有活在做返回 false */
    public static boolean stop(ServerPlayer player) {
        Job job = JOBS.get(player.getUUID());
        if (job == null) {
            return false;
        }
        finish(job, StopReason.STOPPED);
        return true;
    }

    public static boolean isRunning(UUID player) {
        return JOBS.containsKey(player);
    }

    /** 正在做的活已经做成了几格；没有活在做返回 -1 */
    public static int progress(UUID player) {
        Job job = JOBS.get(player);
        return job == null ? -1 : job.done;
    }

    /** 这个人最近一次结束的活；没有返回 null */
    @Nullable
    public static Result lastResult(UUID player) {
        return RESULTS.get(player);
    }

    /** 丢掉所有任务和结果、不提示（GameTest 的 batch 结束时用：测试超时没收尾的任务不能接着改下一批测试的场地） */
    public static void clearAll() {
        JOBS.clear();
        RESULTS.clear();
        rotation = 0;
    }

    // ------------------------------------------------------------------
    // 事件
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onBuilderServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || JOBS.isEmpty()) {
            return;
        }
        tickJobs();
    }

    @SubscribeEvent
    public static void onBuilderLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        JOBS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onBuilderServerStopped(ServerStoppedEvent event) {
        clearAll();
    }

    // ------------------------------------------------------------------
    // 调度
    // ------------------------------------------------------------------

    /** 本刻做一轮：从轮转起点开始，每个任务分到「剩下的全服预算 ÷ 还没轮到的任务数」（向上取整，不超过每任务上限） */
    private static void tickJobs() {
        List<Job> jobs = new ArrayList<>(JOBS.values());
        int count = jobs.size();
        int startAt = Math.floorMod(rotation++, count);
        int perJob = ECBuilder.Config.cellsPerTick();
        int left = ECBuilder.Config.maxCellsPerTickTotal();
        for (int k = 0; k < count; k++) {
            Job job = jobs.get((startAt + k) % count);
            if (JOBS.get(job.player.getUUID()) != job) {
                continue;
            }
            int waiting = count - k;
            int share = left <= 0 ? 0 : Math.min(perJob, (left + waiting - 1) / waiting);
            left -= run(job, share);
        }
    }

    /** 做一个任务的这一刻，返回动了几格（算进全服预算） */
    private static int run(Job job, int share) {
        if (!stillValid(job)) {
            finish(job, StopReason.INTERRUPTED);
            return 0;
        }
        ServerPlayer player = job.player;
        ServerLevel level = player.serverLevel();
        List<ItemStack> drops = new ArrayList<>();
        // 补发破坏效果的间隔：份额 64 时每 16 格一次（4 格），份额 16 以下每刻 1 格；份额调得更大时间隔放宽，仍是最多 4 格、分散在这一刻做的格子里
        int stride = Math.max(EFFECT_STRIDE, (share + MAX_EFFECTS_PER_TICK - 1) / MAX_EFFECTS_PER_TICK);
        int attempts = 0;
        int scanned = 0;
        job.effects = 0;
        StopReason stop = null;
        try {
            while (job.next < job.total && attempts < share && scanned < SCAN_PER_TICK) {
                BlockPos pos = BuilderSelection.cellAt(job.box, job.next, job.mode.bottomUp());
                boolean effect = attempts % stride == 0 && job.effects < MAX_EFFECTS_PER_TICK;
                Outcome outcome = process(job, level, pos, drops, effect);
                if (outcome == Outcome.OUT_OF_MATERIAL) {
                    stop = StopReason.OUT_OF_MATERIAL;
                    break;
                }
                if (outcome == Outcome.NO_MRU) {
                    stop = StopReason.NO_MRU;
                    break;
                }
                job.next++;
                scanned++;
                if (outcome == Outcome.ATTEMPTED) {
                    attempts++;
                    if (!stillHolding(job)) {
                        stop = StopReason.INTERRUPTED;
                        break;
                    }
                    if (job.denied >= DENIED_LIMIT) {
                        stop = StopReason.DENIED;
                        break;
                    }
                }
            }
        } finally {
            job.peakEffects = Math.max(job.peakEffects, job.effects);
            deliver(player, drops);
            playPlaceSound(job, level);
        }
        if (stop != null) {
            finish(job, stop);
        } else if (job.next >= job.total) {
            finish(job, StopReason.FINISHED);
        }
        return attempts;
    }

    private static boolean stillHolding(Job job) {
        ServerPlayer player = job.player;
        return !player.hasDisconnected() && !player.isRemoved() && player.isAlive() && !player.isSpectator()
                && player.getItemInHand(job.hand) == job.wand;
    }

    private static boolean stillValid(Job job) {
        ServerPlayer player = job.player;
        int reach = ECBuilder.Config.maxReach();
        return stillHolding(job) && player.mayBuild() && player.level().dimension() == job.dimension
                && BuilderSelection.distanceSqToBox(player.position(), job.box) <= (double) reach * reach;
    }

    private static boolean chunksLoaded(Level level, BoundingBox box) {
        for (int cx = SectionPos.blockToSectionCoord(box.minX()); cx <= SectionPos.blockToSectionCoord(box.maxX()); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(box.minZ()); cz <= SectionPos.blockToSectionCoord(box.maxZ()); cz++) {
                if (level.getChunkSource().getChunkNow(cx, cz) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    /** 任务结束：移出任务表、记下结果、在聊天栏告诉玩家做成了几格 */
    private static void finish(Job job, StopReason reason) {
        UUID id = job.player.getUUID();
        if (JOBS.get(id) == job) {
            JOBS.remove(id);
        }
        RESULTS.put(id, new Result(reason, job.done, job.denied, job.skipped, job.peakEffects));
        Component message = switch (reason) {
            case FINISHED -> job.skipped > 0
                    ? Component.translatable("message.essentialcraft5.builder.filled_skipped", job.done, job.skipped)
                    : Component.translatable("message.essentialcraft5.builder.filled", job.done);
            case STOPPED -> Component.translatable("message.essentialcraft5.builder.stopped", job.done);
            case OUT_OF_MATERIAL -> Component.translatable("message.essentialcraft5.builder.out_of_material",
                    job.material == null ? Component.empty() : job.material.getDescription(), job.done);
            case NO_MRU -> Component.translatable("message.essentialcraft5.builder.no_mru", job.needed, job.done);
            case INTERRUPTED -> Component.translatable("message.essentialcraft5.builder.interrupted", ECBuilder.Config.maxReach(), job.done);
            case DENIED -> Component.translatable("message.essentialcraft5.builder.denied", job.denied, job.done);
        };
        boolean good = reason == StopReason.FINISHED || reason == StopReason.STOPPED;
        job.player.displayClientMessage(message.copy().withStyle(good ? ChatFormatting.DARK_PURPLE : ChatFormatting.RED), false);
    }

    // ------------------------------------------------------------------
    // 每一格
    // ------------------------------------------------------------------

    private static Outcome process(Job job, ServerLevel level, BlockPos pos, List<ItemStack> drops, boolean effect) {
        if (level.isOutsideBuildHeight(pos) || !ECLevels.isFullyLoaded(level, pos)) {
            return Outcome.SKIPPED;
        }
        BlockState state = level.getBlockState(pos);
        return switch (decide(job, level, pos, state)) {
            case SKIP -> Outcome.SKIPPED;
            case PLACE -> place(job, level, pos);
            case BREAK -> breakMainPart(job, level, pos, state, drops, effect);
            case REPLACE -> replace(job, level, pos, state, drops, effect);
        };
    }

    private static BuilderMode.Action decide(Job job, BlockGetter level, BlockPos pos, BlockState state) {
        return job.mode.decide(classify(level, pos, state), job.block != null && state.is(job.block));
    }

    /** 这一格算哪种情况（{@link BuilderMode.CellKind}） */
    public static BuilderMode.CellKind classify(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return BuilderMode.CellKind.AIR;
        }
        if (state.liquid()) {
            return BuilderMode.CellKind.LIQUID;
        }
        if (state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0F || state.is(ECBuilder.BUILDER_BLACKLIST)) {
            return BuilderMode.CellKind.PROTECTED;
        }
        return BuilderMode.CellKind.OTHER;
    }

    private static Outcome place(Job job, ServerLevel level, BlockPos pos) {
        ServerPlayer player = job.player;
        BlockState state = placementState(player, level, pos, job.block, job.material);
        if (state == null || !canStand(player, level, pos, state)) {
            job.skipped++;
            return Outcome.ATTEMPTED;
        }
        boolean creative = player.getAbilities().instabuild;
        int slot = creative ? -1 : findMaterial(job);
        if (!creative && slot < 0) {
            return Outcome.OUT_OF_MATERIAL;
        }
        if (!MRUDrain.canAfford(player, job.wand, BuilderMode.PLACE_COST)) {
            job.needed = BuilderMode.PLACE_COST;
            return Outcome.NO_MRU;
        }
        if (placeOne(job, level, pos, state, slot, creative)) {
            MRUDrain.playerUseMRU(player, job.wand, BuilderMode.PLACE_COST);
            job.done++;
        } else {
            job.denied++;
        }
        return Outcome.ATTEMPTED;
    }

    /**
     * 清除类模式拆一格。这一格是分两半的方块里「附属的那一半」时改拆主体那一格：门、高花、大型蕨等的上半拆下半，伸出的活塞头拆底座。
     * 直接拆附属的那一半，原版会连带用 {@code destroyBlock(主体, true)} 把主体拆掉、掉落物撒在原地（不进背包，创造模式也掉），
     * 附属的那一半自己什么都不掉，却照收 250；原版玩家亲手挖时靠 {@code playerWillDestroy} 防这个
     * （{@code DoublePlantBlock.preventCreativeDropFromBottomPart}、{@code PistonHeadBlock.playerWillDestroy}），这里不走那条路。
     * 改拆主体：破坏事件按真正掉东西的那格发，另一半随后被形状更新删掉（它的掉落表是空的，不生成掉落物）。
     * 主体那格不在选区里也照拆（原版拆一半也会连带整个）；主体那格按这个模式不该拆（例：清除选定以外、选定的正是活塞）时，这一格也不动。
     * 全部替换从下往上做，先碰到的就是主体，不用改。
     */
    private static Outcome breakMainPart(Job job, ServerLevel level, BlockPos pos, BlockState state, List<ItemStack> drops, boolean effect) {
        BlockPos main = mainPartCandidate(pos, state);
        if (main == null) {
            return harvest(job, level, pos, state, drops, effect);
        }
        if (level.isOutsideBuildHeight(main) || !ECLevels.isFullyLoaded(level, main)) {
            return Outcome.SKIPPED;   // 主体那格没加载：这一半也不拆（拆了主体就掉在地上），也不去触发加载
        }
        BlockState mainState = level.getBlockState(main);
        if (!isMainPart(state, mainState)) {
            return harvest(job, level, pos, state, drops, effect);   // 落单的一半，照常拆
        }
        if (decide(job, level, main, mainState) != BuilderMode.Action.BREAK) {
            return Outcome.SKIPPED;
        }
        return harvest(job, level, main, mainState, drops, effect);
    }

    /** 这一格是「附属的那一半」时主体该在的那一格（只看这一格的状态，不读别的格子）；不是返回 null */
    @Nullable
    static BlockPos mainPartCandidate(BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof PistonHeadBlock) {
            return pos.relative(state.getValue(PistonHeadBlock.FACING).getOpposite());
        }
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            return pos.below();
        }
        return null;
    }

    /**
     * main 是不是 part 的主体：活塞头照 {@code PistonHeadBlock.isFittingBase}（头被拆时原版只连带拆这样的底座：普通头配普通活塞、
     * 黏性头配黏性活塞、伸出着、朝向相同）；分两半的方块要是同一种方块的下半。
     */
    static boolean isMainPart(BlockState part, BlockState main) {
        if (part.getBlock() instanceof PistonHeadBlock) {
            Block base = part.getValue(PistonHeadBlock.TYPE) == PistonType.DEFAULT ? Blocks.PISTON : Blocks.STICKY_PISTON;
            return main.is(base) && main.getValue(PistonBaseBlock.EXTENDED) && main.getValue(PistonBaseBlock.FACING) == part.getValue(PistonHeadBlock.FACING);
        }
        return main.is(part.getBlock()) && main.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && main.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER;
    }

    private static Outcome harvest(Job job, ServerLevel level, BlockPos pos, BlockState state, List<ItemStack> drops, boolean effect) {
        ServerPlayer player = job.player;
        if (!MRUDrain.canAfford(player, job.wand, BuilderMode.BREAK_COST)) {
            job.needed = BuilderMode.BREAK_COST;
            return Outcome.NO_MRU;
        }
        List<ItemStack> harvested = ECProtection.harvestGuarded(player, level, pos, ItemStack.EMPTY, true);
        if (harvested == null) {
            job.denied++;
            return Outcome.ATTEMPTED;   // 被拒、删除被取消：不动、不扣
        }
        breakEffect(job, level, pos, state, effect);
        MRUDrain.playerUseMRU(player, job.wand, BuilderMode.BREAK_COST);
        job.done++;
        if (!player.getAbilities().instabuild) {
            drops.addAll(harvested);
        }
        return Outcome.ATTEMPTED;
    }

    private static Outcome replace(Job job, ServerLevel level, BlockPos pos, BlockState state, List<ItemStack> drops, boolean effect) {
        ServerPlayer player = job.player;
        // 新方块在这格放不下、立不住、被实体挡着的，连拆都不拆。放置状态按这一格现在的流体算（PrePlaceContext）：
        // 拆完这一格剩下的正好是这份流体，所以和拆完再算的结论一样——海带、海草在干的格子上放不了，就不拆；含水的方块换成海带照常换
        BlockState planned = job.block == null || job.material == null ? null
                : job.block.getStateForPlacement(new PrePlaceContext(level, player, new ItemStack(job.material), pos));
        if (planned == null || !canStand(player, level, pos, planned)) {
            job.skipped++;
            return Outcome.ATTEMPTED;
        }
        boolean creative = player.getAbilities().instabuild;
        if (!creative && findMaterial(job) < 0) {
            return Outcome.OUT_OF_MATERIAL;   // 原作先吃掉方块再看有没有材料（L461），没材料方块就白丢了
        }
        if (!MRUDrain.canAfford(player, job.wand, BuilderMode.REPLACE_COST)) {
            job.needed = BuilderMode.REPLACE_COST;
            return Outcome.NO_MRU;
        }
        List<ItemStack> harvested = ECProtection.harvestGuarded(player, level, pos, ItemStack.EMPTY, true);
        if (harvested == null) {
            job.denied++;
            return Outcome.ATTEMPTED;
        }
        breakEffect(job, level, pos, state, effect);
        if (!creative) {
            drops.addAll(harvested);
        }
        boolean placed = false;
        // 拆完照旧重算一次兜底（破坏事件的监听器可能动过周围）
        BlockState placing = placementState(player, level, pos, job.block, job.material);
        if (placing != null && canStand(player, level, pos, placing)) {
            // 破坏事件的监听器可能动过背包，重新找一次材料
            int slot = creative ? -1 : findMaterial(job);
            if (creative || slot >= 0) {
                placed = placeOne(job, level, pos, placing, slot, creative);
                if (!placed) {
                    job.denied++;   // 放置被拒同样算（混合端照样重发整份背包）
                }
            }
        }
        // 这名玩家在这格本来就有破坏权：放置被拒时格子留成空气，按「拆」收费
        MRUDrain.playerUseMRU(player, job.wand, placed ? BuilderMode.REPLACE_COST : BuilderMode.BREAK_COST);
        job.done++;
        return Outcome.ATTEMPTED;
    }

    /**
     * 拆成了一格：抽中的格子补发原版 {@code destroyBlock} 那份破坏效果（客户端按包里的方块状态画粒子、放破碎声；火不出，同原版）。
     * 删本身走的是不出效果的批量写法（类注释），所以每任务每刻最多 {@value #MAX_EFFECTS_PER_TICK} 格有效果。
     */
    private static void breakEffect(Job job, ServerLevel level, BlockPos pos, BlockState state, boolean effect) {
        if (effect && !(state.getBlock() instanceof BaseFireBlock)) {
            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
            job.effects++;
        }
    }

    /**
     * 放一格：先从 slot 取 1 个材料（创造模式不取），{@code placeGuarded} 放行就算用掉，被拒原样放回。
     * 先取后放：放置事件里有监听器动了背包也不会出现「放下了却没扣到材料」。放回时那一格已经不是原来那叠（被挪走、玩家在事件里死了背包已经撒出去）
     * 就交给 {@code giveOrDrop}：活着进背包，死了掉在原地。
     */
    private static boolean placeOne(Job job, ServerLevel level, BlockPos pos, BlockState state, int slot, boolean creative) {
        ServerPlayer player = job.player;
        ItemStack source = null;
        ItemStack taken = ItemStack.EMPTY;
        if (!creative) {
            source = slotStack(player.getInventory(), slot);
            taken = source.split(1);
        }
        // 报没拿魔杖的那只手：混合端按这只手报放置用的物品（不给手时按主手报，魔杖就成了「放下的东西」）；纯 Forge 不看手
        InteractionHand other = job.hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        boolean placed = ECProtection.placeGuarded(player, level, pos, state, Direction.UP, other, Block.UPDATE_ALL);
        if (placed) {
            job.lastPlacedPos = pos;
            job.lastPlacedState = state;
        } else if (!creative) {
            Inventory inventory = player.getInventory();
            if (slotStack(inventory, slot) == source && source.getCount() < source.getMaxStackSize()) {
                source.grow(1);
            } else {
                ECItemEntities.giveOrDrop(player, taken);
            }
        }
        return placed;
    }

    /**
     * 这一格放下去的状态：按物品自己的放置规则、以玩家的视线算一格（点中的面当作这一格的顶面），不照抄选定时的状态——
     * 台阶放单层、海泡菜和蜡烛放 1 个、雪层放 1 层、作物从最小的长起、楼梯朝玩家面对的方向。格子放不了（不是可替换的）或者方块不让放返回 null。
     */
    @Nullable
    static BlockState placementState(ServerPlayer player, Level level, BlockPos pos, @Nullable Block block, @Nullable Item material) {
        if (block == null || material == null) {
            return null;
        }
        BlockPlaceContext context = new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, new ItemStack(material),
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        // 这一格本身不可替换时，原版会改成往上一格放（getClickedPos 变成 pos.above()），算出来的状态就不是这一格的了
        if (!context.replacingClickedOnBlock() || !context.canPlace()) {
            return null;
        }
        return block.getStateForPlacement(context);
    }

    /**
     * 全部替换拆之前预算放置状态用的上下文：把 pos 当成「点中的、可以替换的那一格」，不管它现在是什么方块
     * （原版遇到不可替换的格子会改成往上一格放）；流体、邻居、玩家视线都按世界里现在的算，和 {@link #placementState} 用同一个点击位置。
     */
    private static final class PrePlaceContext extends BlockPlaceContext {
        /** 父类构造器里会回调 {@link #getClickedPos}（例：藤蔓的 canBeReplaced），那时还没赋值，退回父类的算法 */
        @Nullable
        private final BlockPos target;

        PrePlaceContext(Level level, Player player, ItemStack stack, BlockPos pos) {
            super(level, player, InteractionHand.MAIN_HAND, stack, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
            this.target = pos;
        }

        @Override
        public BlockPos getClickedPos() {
            return target != null ? target : super.getClickedPos();
        }

        @Override
        public boolean canPlace() {
            return true;
        }

        @Override
        public boolean replacingClickedOnBlock() {
            return true;
        }
    }

    /**
     * 这个状态在这一格立得住、没被实体挡着（同原版 BlockItem.canPlace），另外查施工的玩家自己：
     * 不在世界的实体表里的玩家（GameTest 的测试玩家）原版查不到，站着的格子照样不能填。
     */
    static boolean canStand(ServerPlayer player, Level level, BlockPos pos, BlockState state) {
        if (!state.canSurvive(level, pos)) {
            return false;
        }
        CollisionContext context = CollisionContext.of(player);
        if (!level.isUnobstructed(state, pos, context)) {
            return false;
        }
        VoxelShape shape = state.getCollisionShape(level, pos, context);
        return shape.isEmpty() || !Shapes.joinIsNotEmpty(shape.move(pos.getX(), pos.getY(), pos.getZ()),
                Shapes.create(player.getBoundingBox()), BooleanOp.AND);
    }

    // ------------------------------------------------------------------
    // 材料、掉落、声音
    // ------------------------------------------------------------------

    /** 材料格的编号：0~35 是背包，36 是副手 */
    private static ItemStack slotStack(Inventory inventory, int slot) {
        return slot < inventory.items.size() ? inventory.items.get(slot) : inventory.offhand.get(0);
    }

    /** 背包（含副手）里第一格可用的材料，没有返回 -1 */
    private static int findMaterial(Job job) {
        Inventory inventory = job.player.getInventory();
        if (job.materialSlot >= 0 && isPlainMaterial(slotStack(inventory, job.materialSlot), job.material)) {
            return job.materialSlot;
        }
        for (int slot = 0; slot <= inventory.items.size(); slot++) {
            if (isPlainMaterial(slotStack(inventory, slot), job.material)) {
                job.materialSlot = slot;
                return slot;
            }
        }
        return -1;
    }

    /** 能当材料：就是那种物品、没有 NBT（改过名、装了东西的不碰） */
    static boolean isPlainMaterial(ItemStack stack, @Nullable Item material) {
        if (material == null || stack.isEmpty() || !stack.is(material)) {
            return false;
        }
        CompoundTag tag = stack.getTag();
        return tag == null || tag.isEmpty();
    }

    /** 本刻的掉落按「同物品同 NBT」合并成整组交给玩家（放不下的掉在脚下；事件的监听器让玩家在这一刻里死了的，掉在原地） */
    private static void deliver(ServerPlayer player, List<ItemStack> drops) {
        if (drops.isEmpty()) {
            return;
        }
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack drop : drops) {
            ItemStack rest = drop.copy();
            for (ItemStack stack : merged) {
                if (rest.isEmpty()) {
                    break;
                }
                if (stack.getCount() < stack.getMaxStackSize() && ItemStack.isSameItemSameTags(stack, rest)) {
                    int move = Math.min(rest.getCount(), stack.getMaxStackSize() - stack.getCount());
                    stack.grow(move);
                    rest.shrink(move);
                }
            }
            if (!rest.isEmpty()) {
                merged.add(rest);
            }
        }
        for (ItemStack stack : merged) {
            ECItemEntities.giveOrDrop(player, stack);
        }
    }

    /** 本刻放下过方块时，在最后一格放一次放置声（每格都放会叠成一片） */
    private static void playPlaceSound(Job job, ServerLevel level) {
        BlockState state = job.lastPlacedState;
        BlockPos pos = job.lastPlacedPos;
        job.lastPlacedState = null;
        job.lastPlacedPos = null;
        if (state == null || pos == null) {
            return;
        }
        SoundType sound = state.getSoundType(level, pos, job.player);
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1F) / 2F, sound.getPitch() * 0.8F);
    }
}
