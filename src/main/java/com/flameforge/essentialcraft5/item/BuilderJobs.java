package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.registry.ECBuilder;
import com.flameforge.essentialcraft5.util.ECItemEntities;
import com.flameforge.essentialcraft5.util.ECLevels;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.flameforge.essentialcraft5.network.S2CBuilderFlightPacket;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
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
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntFunction;

/**
 * 创世神之杖的施工（{@link MagicalBuilderItem}）：开工时登记一个任务，每刻在 {@code ServerTickEvent} END 做一批格子。
 * 写在刻末尾、不嵌在别的破坏过程里（同 {@link MagicalDiggerEvents}：混合端的破坏事件全局只有一个槽）。升级后的设计见 tools/plan/builder_upgrade.md。
 * <h2>开工（{@link #request}）</h2>
 * 真玩家、能改方块（冒险模式不行）、手里是魔杖、维度不在 {@code disabled_dimensions} 里；范围按形状算（{@link BuilderSelection#shape}：
 * 两点照旧要两个点、点在这个维度、不超过 {@code max_volume}；铺面 / 直线按 {@link BuilderShapes}，边长夹到 {@code max_side}）；
 * 要选定方块的模式有方块可用（魔杖在主手时副手拿着的方块优先，见 {@link #chooseBlock}）；离范围 {@code max_reach} 格以内、区块全部已加载。
 * 从物品右键来的（confirm = true）先扫一遍范围：要动的格数超过玩家报上来的确认阈值、或材料 / MRU 不够这一单时，第一次只提示，
 * {@value #CONFIRM_TICKS} 刻内同一单再点一次才开工。开工前在要动的格子里均匀挑最多 {@value #PRE_PROBE_SAMPLES} 格探测领地，
 * 有被拒的提示比例、任务一开始就「先探后放」（见下）。
 * <h2>排队</h2>
 * 每人 1 个在做 + 最多 {@code max_queued_per_player} 个排队；全服同时在做（不算暂停的）最多 {@code max_jobs_total} 个，多的按先来后到排队。
 * 排队的任务轮到时再复核一遍（拿着同一根魔杖、在范围内……），没过就丢掉。潜行对空气右键停下时连排队的一起清掉。
 * <h2>沿面的测量</h2>
 * 沿面的范围要先测（{@link BuilderSurface}）：开工请求只登记一个测量（{@link Survey}），每刻在调度末尾接着扫，扫完再走上面的开工流程。
 * 测量占这个人一个排队名额、每人同一时间只有一个，每刻读的格数有上限（{@code face_scan_per_tick}）、和施工共用耗时预算，不加载区块；
 * 潜行对空气右键、撤销、换手、走远、换维度、死亡、下线都会把它取消。
 * <h2>每刻</h2>
 * <ul>
 *   <li>先复核：没下线、活着、不是旁观、还能改方块、同一个维度、同一只手里还是<b>同一个</b>魔杖物品对象、离范围 {@code max_reach} 格以内；
 *       任何一条不满足就停（「中断」），已经做好的不退。每做完一格（发过事件的）再查一次前几条。</li>
 *   <li>被拒（真放置 / 真拆被拒，加上先探后放里探测被拒的）累计 {@code denied_limit} 格就停（「没有权限」）；
 *       另外开着先探后放时，真放置 / 真拆<b>连续</b>被拒 {@code denied_streak_limit} 格也停（中间做成一格就重新数，探测被拒的不算；
 *       全部替换里拆成了、放置被拒的格也算被拒），免得探测放行、真做却被拒的领地（只禁止放置的那种）每格都让领地插件提示一句。
 *       没开先探后放（{@code probe_after_denied} 关掉、或等探测预算超时放弃了）时不数连续，只受累计上限管。
 *       最后一格才到上限的按做完结算。
 *       第一次真被拒以后（{@code probe_after_denied}）每格先 {@code ECProtection.check} 探测，放行才真做：混合端上每次真放置被拒会把整份背包重发给客户端、
 *       领地插件可能每次提示一句，探测不会；探测有每刻预算，用完（UNKNOWN）这刻就停在这一格，下刻再问。</li>
 *   <li>预算：每个任务每刻最多<b>动</b> {@code cells_per_tick} 格，全服所有任务合计 {@code max_cells_per_tick_total} 格，按轮转的顺序平分；
 *       另外全服合计每刻最多花 {@code max_millis_per_tick} 毫秒（每动完一格、每扫 64 格看一次时间，到了这刻就收手，后面的任务这刻份额为 0；
 *       每刻至少有一格做得成，不会因为预算设得太小永远不动）。不用动的格子只读一下方块，不算格数，但每个任务每刻最多看 {@value #SCAN_PER_TICK} 格。</li>
 *   <li>材料或 MRU 用完：{@code material_pause_seconds} 大于 0 时<b>暂停</b>（不结束），每 {@value #RESUME_CHECK_TICKS} 刻看一次补上没有，
 *       补上就接着做同一格；从第一次暂停起超时才按「材料用完」/「MRU 不够」结束（中间做成过一格就重新计时）。暂停的任务不分预算，照样每刻复核。</li>
 *   <li>顺序见 {@link BuilderArea#cellAt}；每格怎么处理见 {@link BuilderMode#decide}；没加载的格子跳过（不触发加载），单独计数，结束时告诉玩家。</li>
 *   <li>飞行特效（{@code vfx_lead_ticks}，D）：放置类模式（填充、全部替换）的任务分成计划和执行两步（{@link BuilderFlightPlan}）。
 *       刻 t 只读方块、按上面的规则挑出这一刻要做的格子，发飞行包；刻 t + D 起才照常逐格做，权限、材料、MRU、立不住全部重查，
 *       计划这一步不取料、不扣 MRU、不改世界。耗时预算不够时剩下的格子留到下一刻，不另发包；每个任务最多攒 D + 1 次计划。
 *       暂停、停下、中断、做完时丢掉计划了还没执行的格子、发收尾包（暂停的接着做时重新计划）；这类任务服务端不放放置声，由客户端在落位时放。
 *       清除类模式不飞，照旧当刻就做；撤销照旧当刻删，删成的格子发飞回的包。D = 0 时不发任何包，和没有特效时完全一样。</li>
 * </ul>
 * <h2>每格（只按做成的格子收费）</h2>
 * <pre>
 * 放：算放置状态（物品自己的规则、朝向按玩家视线；台阶放单层、海泡菜和蜡烛放 1 个、作物从最小的长起），为 null、立不住、被实体挡着、
 *     会掉落的方块（沙子、砂砾、混凝土粉末）下面是空的 → 跳过（记进「放不下」，不生成下落实体）；
 *     找不到材料（{@link BuilderMaterials}：背包、副手，再随身容器）→ 暂停 / 停「材料用完」；MRU 不够 → 暂停 / 停；
 *     取 1 个材料 → placeGuarded 放行：扣 MRU、记进撤销记录；被拒：材料原样放回原处（容器塞回同一处理器同一格）
 * 拆：MRU 不够 250 → 暂停 / 停；harvestGuarded 没拆 → 跳过、不扣；拆了：扣 250，掉落进本刻的掉落表
 *     门、高花的上半，伸出的活塞头：改拆主体那一格（下半、底座），见 {@link #breakMainPart}
 * 换：新方块在这格放不下、立不住、被挡着 → 跳过（连拆都不拆）；没有材料 → 暂停 / 停；MRU 不够 300 → 暂停 / 停；harvestGuarded 没拆 → 跳过；
 *     拆了以后重算放置状态 → placeGuarded 放行：扣 300、用掉材料、记进撤销记录；被拒（或放不下）：格子留成空气，掉落照给，只扣 250，材料不动；
 *     放置被拒的那种另记「只拆未放」（不算做成、也不算被拒未改动，结束时另起一行告诉玩家）
 * 撤：见 {@link BuilderUndo}；以玩家身份 harvestGuarded，删的掉落丢掉，删成了退 1 个记下的材料；不扣也不退 MRU
 * </pre>
 * 放：{@code ECProtection.placeGuarded(玩家, …, Direction.UP, 另一只手, UPDATE_ALL)}——手传没拿魔杖的那只：混合端按这只手报放置用的物品
 * （传 null 时报主手，魔杖拿在主手就成了「放下了一根魔杖」），纯 Forge 不看手；材料从副手来时正好对得上，
 * 从背包、随身容器来时发事件的那一刻把那只手临时换成 1 个材料（{@link HandSwap}）。
 * 不调 {@code setPlacedBy}（带方块实体的、分两半的方块本来就选不了）。拆：{@code ECProtection.harvestGuarded(玩家, …, 空手, true)}
 * （发破坏事件时主手拿空；先删、删成了才算掉落；掉落按空手算；不出蠹虫、不给经验）。用的是批量写法：删的时候没有破坏粒子和声音、不写探测缓存；
 * 效果由这里补发，每个任务每刻最多 {@value #MAX_EFFECTS_PER_TICK} 格。材料只用<b>没有 NBT</b> 的那种物品，放回去的和取出来的一模一样。
 * MRU 先 {@code MRUDrain.canAfford} 问够不够、做成了再 {@code playerUseMRU}。掉落和撤销退的材料每刻合并成整组 {@code giveOrDrop}。
 * 创造模式不扣 MRU、不要材料、拆下来的东西不给、撤销不退料。
 * <p>
 * 任务表、排队、待确认、撤销记录都不存盘：玩家下线、停服时直接丢掉；死亡、换维度时当前任务中断、排队的丢掉，撤销记录留着。只在服务端线程上用。
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
     * {@code denied_limit} 的默认值（4 刻的量）。按累计算、不按连续算：同一层里 x 在外、z 在内地扫，选区 x 大的那一半在别人领地里时，
     * 每层连续被拒的格子就有上百个，按连续算会在第一层就误停；一行被拒一行放行地交替时，连续计数又会不停清零、封不住总量。
     */
    public static final int DENIED_LIMIT = 256;
    /**
     * {@code denied_streak_limit} 的默认值：连续真被拒几格就停。只在先探后放下数真放 / 真拆被拒（探测放行、真做却被拒，会让领地插件提示的那种），
     * 探测被拒的不算，所以上面说的「选区一半在别人领地里」不会误停（第一次真被拒以后都是探测被拒）；
     * 没在先探后放（{@code probe_after_denied} 关掉、或探测等超时放弃了）时所有被拒都是真被拒，这时不数连续，只受累计上限管。
     */
    public static final int DENIED_STREAK_LIMIT = 8;
    /** 待确认的一单多少刻内再点一次算确认（3 秒） */
    public static final int CONFIRM_TICKS = 60;
    /** 开工前探测领地挑几格 */
    public static final int PRE_PROBE_SAMPLES = 16;
    /** 暂停的任务每隔几刻看一次补上没有 */
    public static final int RESUME_CHECK_TICKS = 10;
    /** 二阶（老魔杖）的撤销栈深；各档的数在 {@link BuilderTier#undoDepth} */
    public static final int UNDO_DEPTH = 1;
    /** 没报过确认阈值的玩家按这个算（客户端配置 confirm_threshold 的默认值） */
    public static final int DEFAULT_CONFIRM_THRESHOLD = 1024;
    /** 最近结束的任务结果按玩家留几条（GameTest、排查用） */
    private static final int RESULT_HISTORY = 64;
    /**
     * 物品右键开工的最短间隔（刻）。原版客户端按住右键时每 4 刻自动重发一次「使用物品」，间隔取 6 刻、被忽略的那一下也刷新时间，
     * 按住不放的连发就永远进不来：二次确认不会被按住自动通过，铺面也不会自动再排一单叠出第二层，必须松开再按。
     */
    public static final int REQUEST_GAP_TICKS = 6;
    /** 先探后放时探测预算一直不够（UNKNOWN）连续等了这么多刻，就不再探、直接走真事件（照样受保护，只是多发几次真事件） */
    public static final int PROBE_WAIT_LIMIT = 40;

    /** 玩家 UUID → 正在做（含暂停）的任务（按开工顺序） */
    private static final Map<UUID, Job> JOBS = new LinkedHashMap<>();
    /** 排队的任务（按先来后到） */
    private static final List<Job> WAITING = new ArrayList<>();
    private static final Map<UUID, Result> RESULTS = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Result> eldest) {
            return size() > RESULT_HISTORY;
        }
    };
    /** 玩家 UUID → 待确认的一单 */
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    /** 玩家 UUID → 上一次物品右键开工的刻（被忽略的也算） */
    private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();
    /** 玩家 UUID → 正在测量的沿面（按开始的先后） */
    private static final Map<UUID, Survey> SURVEYS = new LinkedHashMap<>();
    /** 玩家 UUID → 最近一次测完后开工的结果（GameTest、排查用） */
    private static final Map<UUID, StartResult> LAST_SURVEY = new HashMap<>();
    /** 玩家 UUID → 最近一单清除类（拆成过至少一格）结束的那一刻：面板据此说明「上一次是清除，不能撤」 */
    private static final Map<UUID, Long> LAST_CLEAR = new HashMap<>();
    /** 轮转的起点：每刻往后挪一个，全服预算不够分时轮流先做 */
    private static int rotation;
    private static int surveyRotation;
    /** 本刻的耗时预算用完了 */
    private static boolean timeExhausted;
    private static int nextJobId;

    private BuilderJobs() {
    }

    /** {@link #request} / {@link #undo} 的结果 */
    public enum StartResult {
        STARTED,
        /** 排上队了（前面有自己的活、或全服名额满了） */
        QUEUED,
        /** 大单或材料 / MRU 不够：等再点一次确认 */
        NEEDS_CONFIRM,
        /** 不是真玩家（假玩家、客户端），不提示 */
        NOT_REAL_PLAYER,
        /** 这个人在做的和排队的都满了 */
        BUSY,
        /** 冒险模式等不能改方块的情况 */
        CANNOT_BUILD,
        /** 手里不是创世神之杖 */
        NOT_A_WAND,
        NO_POINTS,
        /** 点不在玩家所在的维度 */
        OTHER_DIMENSION,
        /** 这个维度在 {@code disabled_dimensions} 里 */
        DISABLED_DIMENSION,
        /** 这个模式要选定方块，但没选（或者选的已经不能用了） */
        NO_BLOCK,
        TOO_BIG,
        OUT_OF_REACH,
        /** 范围有区块没加载 */
        NOT_LOADED,
        /** 没有能撤的记录（或撤销关着） */
        NOTHING_TO_UNDO,
        /** 记录过了时限、或在别的维度 */
        UNDO_EXPIRED,
        /** 上一单是清除类：清除的结果不能撤，也不越过它撤更早那次放置（{@link #clearedSince}） */
        UNDO_CLEARED,
        /** 沿面：开始测量了（测完由调度接着开工或要确认，结果见 {@link #lastSurvey}） */
        SURVEYING,
        /** 沿面：没对准方块，或点中的那一面不能沿着铺（空气、液体、被盖住） */
        NO_SURFACE
    }

    /** 任务为什么结束 */
    public enum StopReason {
        FINISHED,
        /** 玩家自己停下（潜行对空气右键、停下按钮、撤销前先停下） */
        STOPPED,
        OUT_OF_MATERIAL,
        NO_MRU,
        /** 下线以外的复核没过：走远、换手、换维度、死亡、改成冒险模式 */
        INTERRUPTED,
        /** 被拒累计 {@code denied_limit} 格（范围大片落在别人的领地里） */
        DENIED
    }

    /** 施工还是撤销 */
    public enum Kind {
        BUILD,
        UNDO
    }

    /**
     * 结束了的任务：为什么结束、做成了几格（撤销：删成了几格）、被拒了几格（含探测被拒）、新方块放不下跳过了几格（撤销：方块变了没动的格数）、
     * 补发破坏效果最多的那一刻有几格、其中探测被拒的格数、区块没加载跳过的格数、是施工还是撤销、结束时提示那一句的语言键、
     * 全部替换里拆成了但放置被拒、留成空气的格数（不算进 done 和 denied）
     */
    public record Result(StopReason reason, int done, int denied, int skipped, int peakEffects, int probedDenied, int unloaded, Kind kind, String messageKey,
                         int brokenOnly) {
    }

    /**
     * 给状态包和 GameTest 看的任务现状。pausedForMru：暂停是因为 MRU 不够（否则是材料用完）——客户端不能拿「手头还有多少料」反推，
     * 随身容器里看得见、却取不出来的料（处理器的过滤规则拒绝取出）照样算在手头的数里。surveying：沿面正在测量（done = 已连上的格数，total = 上限）
     */
    public record Snapshot(Kind kind, boolean paused, boolean queued, int done, int total, int queueAhead, int pauseTicksLeft, boolean pausedForMru,
                           boolean surveying) {
    }

    /**
     * 沿面开工前的测量（{@link BuilderSurface}）：每刻在调度里接着扫，每个测量每刻最多读 {@code face_scan_per_tick} 格，和施工共用全服耗时预算；
     * 扫完按测出来的范围走和别的形状一样的开工流程（确认、探测、排队）。测量占这个人一个排队名额（{@link #queueFull}），同一时间每人只有一个；
     * 期间每刻复核（同一只手里还是同一根魔杖、形状还是沿面、活着、能改方块、同一维度、离点中处在施工距离内），没过就取消。
     */
    private static final class Survey {
        final ServerPlayer player;
        final InteractionHand hand;
        final ItemStack wand;
        final BuilderMode mode;
        final ResourceKey<Level> dimension;
        final BuilderShapes.Hit hit;
        final boolean confirm;
        /** 点下去的那一刻：二次确认的时限从这一刻算，不从测完算 */
        final long requestedAt;
        final BuilderSurface.Scan scan;

        Survey(ServerPlayer player, InteractionHand hand, ItemStack wand, BuilderMode mode, BuilderShapes.Hit hit, boolean confirm, long requestedAt,
               BuilderSurface.Scan scan) {
            this.player = player;
            this.hand = hand;
            this.wand = wand;
            this.mode = mode;
            this.dimension = player.level().dimension();
            this.hit = hit;
            this.confirm = confirm;
            this.requestedAt = requestedAt;
            this.scan = scan;
        }

        UUID owner() {
            return player.getUUID();
        }
    }

    /** 待确认的一单：直接存这一单的各项（按值比），不压成哈希，免得两单撞出同一个数时第二单被当成确认 */
    private record Pending(Fingerprint fingerprint, long expires) {
    }

    /** 「同一单」的判据：范围、锚点、格数、模式、方块、形状、混铺表（含种子）都一样 */
    private record Fingerprint(BoundingBox box, BlockPos anchor, int size, BuilderMode mode, @Nullable Block block, BuilderShape shape,
                               @Nullable BuilderMix.Table mix) {
    }

    private static final class Job {
        final int id = ++nextJobId;
        final Kind kind;
        final ServerPlayer player;
        final InteractionHand hand;
        final ItemStack wand;
        final BuilderMode mode;
        final ResourceKey<Level> dimension;
        /** 施工的范围；撤销任务为 null */
        @Nullable
        final BuilderArea area;
        /** 复核距离用的外框 */
        final BoundingBox box;
        final int total;
        @Nullable
        final Block block;
        @Nullable
        final Item material;
        /**
         * 这一单能放的几种方块和对应的材料：没混铺时就是 {@link #block} 一种（没选定方块时为空），混铺时是表里的几种（按表的顺序）。
         * 每格用哪种见 {@link #kindAt}
         */
        final Block[] blocks;
        final Item[] items;
        /** 混铺表（开工时定下，整单不变）；没混铺为 null */
        @Nullable
        final BuilderMix.Table mix;
        /** 混铺的任务种子（{@link BuilderMix#seedFor}） */
        final long mixSeed;
        /** 回收生效（开工时定下）：拆下的先塞随身容器 */
        final boolean salvage;
        /** 回收的丢弃清单（开工时定下）；没装回收是空集 */
        final Set<Item> discard;
        /** 施工：这单放下的格子（第一次放成时压栈）；撤销：正在撤的那条 */
        @Nullable
        final BuilderUndo.Record record;
        boolean recordPushed;
        final BuilderMaterials.Cursor cursor = new BuilderMaterials.Cursor();
        /** 下一格的序号 */
        int next;
        /** 做成了几格 */
        int done;
        /** 停在「MRU 不够」时那一格要多少 */
        int needed;
        /** 真放置 / 真拆被拒了几格 */
        int denied;
        /** 先探后放里探测被拒的格数 */
        int probedDenied;
        /**
         * 先探后放开着时连着真放 / 真拆被拒了几格（做成一格清零，探测被拒不动它）。没开先探后放（服主关掉、或等探测预算超时放弃了）时不数，
         * 那时被拒的格子多半就是落在别人领地里的那一片，只受累计上限管
         */
        int deniedStreak;
        /** 全部替换里拆成了、放置被拒、格子留成空气的格数（不算「做成」也不算「被拒未改动」，完成提示单独写一句） */
        int brokenOnly;
        /** 因为连续被拒停的（结束提示换一句） */
        boolean stoppedByStreak;
        /** 新方块在这格放不下跳过了几格（撤销：方块变了没动的格数） */
        int skipped;
        /** 区块没加载跳过的格数 */
        int unloaded;
        /** 本刻补发了几格破坏效果 */
        int effects;
        /** 补发破坏效果最多的那一刻有几格 */
        int peakEffects;
        /** 每格先探测再真做 */
        boolean probeMode;
        /** 先探后放里探测预算不够、开始连续等待的那一刻；-1 = 没在等 */
        long probeWaitSince = -1;
        /** 等探测预算等超时、放弃过先探后放：之后再被拒也不再打开（不然每被拒一格就要再等一轮） */
        boolean probeAbandoned;
        /** 已经 {@link #finish} 过（事件监听器在一格的放置 / 破坏里把人弄死、换维度时，同一个任务会被结束两次） */
        boolean finished;
        /** finish 时的原因（下线清掉的没有） */
        @Nullable
        StopReason finishReason;
        boolean paused;
        @Nullable
        StopReason pauseReason;
        /** 暂停到哪一刻就结束；-1 = 没在计时 */
        long pauseDeadline = -1;
        long lastResumeCheck;
        /** 本刻最后放下的一格（放一次声音） */
        @Nullable
        BlockPos lastPlacedPos;
        @Nullable
        BlockState lastPlacedState;
        /** 这根魔杖的档位数值（施工距离、每刻格数、每格 MRU、撤销栈深）；每刻开头按 {@link #wand} 重算一次，配置重载后下一刻就生效 */
        BuilderTier.Stats stats;
        /** 飞行特效的提前量（建任务时定下，整单不变）：放置类施工和撤销按它发特效包，0 = 不发；清除类恒为 0 */
        final int lead;
        /** 放置类施工的计划队列；提前量为 0、清除类、撤销为 null（当刻就做） */
        @Nullable
        final BuilderFlightPlan.Ring ring;
        /** 撤销这一刻删成的格子（发飞回的包）；不发特效时为 null */
        @Nullable
        LongArrayList flewBack;
        @Nullable
        IntArrayList flewBackStates;
        /**
         * 计划时写进飞行包、还没执行的格子：这些格子的放置声由客户端在落位时放；没进包的（计划时跳过后来又要放的、
         * 六面被包住的、预计放不下的）执行时照旧由服务端放一下放置声。没有计划队列时为 null
         */
        @Nullable
        final LongOpenHashSet flown;

        Job(Kind kind, ServerPlayer player, InteractionHand hand, ItemStack wand, BuilderMode mode, @Nullable BuilderArea area, BoundingBox box,
            int total, @Nullable Block block, @Nullable BuilderUndo.Record record, @Nullable BuilderMix.Table mix) {
            this.kind = kind;
            this.player = player;
            this.hand = hand;
            this.wand = wand;
            this.mode = mode;
            this.dimension = player.level().dimension();
            this.area = area;
            this.box = box;
            this.total = total;
            this.block = block;
            this.material = block == null ? null : block.asItem();
            this.record = record;
            this.stats = BuilderTier.stats(wand);
            this.mix = kind == Kind.BUILD && area != null ? mix : null;
            if (this.mix != null) {
                int n = this.mix.size();
                this.blocks = new Block[n];
                this.items = new Item[n];
                for (int i = 0; i < n; i++) {
                    this.blocks[i] = this.mix.block(i);
                    this.items[i] = this.blocks[i].asItem();
                }
                this.mixSeed = BuilderMix.seedFor(this.mix, area);
            } else {
                this.blocks = block == null ? new Block[0] : new Block[] {block};
                this.items = block == null ? new Item[0] : new Item[] {this.material};
                this.mixSeed = 0L;
            }
            this.salvage = kind == Kind.BUILD && BuilderSalvage.active(wand);
            this.discard = this.salvage ? BuilderSalvage.discardSet(wand) : Set.of();
            boolean flies = kind == Kind.UNDO || area != null && mode.bottomUp();
            this.lead = flies ? ECBuilder.Config.vfxLeadTicks() : 0;
            this.ring = kind == Kind.BUILD && this.lead > 0 ? new BuilderFlightPlan.Ring(this.lead) : null;
            this.flown = this.ring == null ? null : new LongOpenHashSet();
        }

        UUID owner() {
            return player.getUUID();
        }

        /** 这一格用第几种（没混铺恒为 0） */
        int kindAt(BlockPos pos) {
            return mix == null ? 0 : BuilderMix.kindAt(mix, mixSeed, box, pos);
        }

        /** 格子里的方块算不算「选定的那种」（混铺时表里任一种都算） */
        boolean matches(BlockState state) {
            return mix != null ? mix.contains(state) : block != null && state.is(block);
        }
    }

    /** 一格的处理结果 */
    private enum Outcome {
        /** 不用动，没算进预算 */
        SKIPPED,
        /** 动过（做没做成都算进预算） */
        ATTEMPTED,
        /** 探测预算用完：这刻停在这一格，下刻再问 */
        WAIT,
        OUT_OF_MATERIAL,
        NO_MRU
    }

    // ------------------------------------------------------------------
    // 开工、停下、撤销、查询
    // ------------------------------------------------------------------

    /** 两点形状和 GameTest 的老入口：不要确认、按脚下算铺面（同 {@code request(player, hand, null, false)}） */
    public static StartResult start(ServerPlayer player, InteractionHand hand) {
        return request(player, hand, null, false);
    }

    /**
     * 物品右键开工前的限频（{@link MagicalBuilderItem} 的 use / onItemUseFirst 在调 {@link #request} 之前问；request 本身不限，
     * 别的入口和 GameTest 同一刻连调照常）。距这个人上一次物品右键开工不到 {@value #REQUEST_GAP_TICKS} 刻就返回 true（这一下忽略、不提示），
     * 不管忽不忽略都把时间记成这一刻。也压住了改过的客户端狂发「使用物品」包：每 6 刻最多让服务端扫一次范围。
     * 潜行右键停下不走这里（按住右键时按下潜行要能马上停）。
     */
    public static boolean requestThrottled(ServerPlayer player) {
        long now = now(player);
        Long last = LAST_REQUEST.put(player.getUUID(), now);
        return last != null && now >= last && now - last < REQUEST_GAP_TICKS;
    }

    /**
     * 用 hand 里的创世神之杖开工（服务端）。hit 是右键点中的格子和面（铺面 / 直线按它定位；没点中传 null，铺脚下），两点形状不看它。
     * confirm 为 true 时大单和材料 / MRU 不够的要再点一次确认（物品右键走这里）。成功和失败都在动作栏提示一句（假玩家除外）。
     */
    public static StartResult request(ServerPlayer player, InteractionHand hand, @Nullable BuilderShapes.Hit hit, boolean confirm) {
        if (!ECProtection.isRealPlayer(player)) {
            return StartResult.NOT_REAL_PLAYER;
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
        if (ECBuilder.Config.dimensionDisabled(player.level().dimension().location())) {
            MagicalBuilderItem.tellError(player, Component.translatable("message.essentialcraft5.builder.disabled_dimension"));
            return StartResult.DISABLED_DIMENSION;
        }
        UUID id = player.getUUID();
        if (SURVEYS.containsKey(id)) {
            MagicalBuilderItem.tellError(player, Component.translatable("message.essentialcraft5.builder.surveying_busy"));
            return StartResult.BUSY;
        }
        if (queueFull(id)) {
            MagicalBuilderItem.tellError(player, Component.translatable("message.essentialcraft5.builder.busy"));
            return StartResult.BUSY;
        }
        BuilderShape shape = BuilderSelection.shape(wand);
        if (shape == BuilderShape.FACE) {
            return requestFace(player, hand, wand, mode, hit, confirm);
        }
        BuilderArea area;
        if (shape == BuilderShape.POINTS) {
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
            long volume = BuilderSelection.volume(first, second);
            int maxVolume = BuilderTier.stats(wand).maxVolume();
            if (volume > maxVolume) {
                MagicalBuilderItem.tellError(player, Component.translatable("message.essentialcraft5.builder.too_big", volume, maxVolume));
                return StartResult.TOO_BIG;
            }
            area = BuilderArea.box(BuilderSelection.box(first, second));
        } else {
            area = areaOf(player, wand, shape, hit);
        }
        return launch(player, hand, wand, mode, area, confirm, now(player), false);
    }

    /**
     * 范围定下以后的开工流程（所有形状共用；沿面在测完时由调度调）：要选定方块的有方块、离范围在施工距离内、区块全加载，
     * 要确认的先确认（confirmTick 是点下去的那一刻，拿它和上一次提示记下的时限比），然后探测、排队。capped：沿面到了格数上限，开工提示换一句。
     */
    private static StartResult launch(ServerPlayer player, InteractionHand hand, ItemStack wand, BuilderMode mode, BuilderArea area, boolean confirm,
                                      long confirmTick, boolean capped) {
        Block block = chooseBlock(player, hand, wand, mode);
        if (mode.needsBlock() && block == null) {
            MagicalBuilderItem.tellError(player, Component.translatable("message.essentialcraft5.builder.no_block"));
            return StartResult.NO_BLOCK;
        }
        BoundingBox box = area.box();
        int reach = BuilderTier.stats(wand).maxReach();
        if (BuilderSelection.distanceSqToBox(player.position(), box) > (double) reach * reach) {
            MagicalBuilderItem.tellError(player, Component.translatable("message.essentialcraft5.builder.out_of_reach", reach));
            return StartResult.OUT_OF_REACH;
        }
        if (!chunksLoaded(player.level(), area)) {
            MagicalBuilderItem.tellError(player, Component.translatable("message.essentialcraft5.builder.not_loaded"));
            return StartResult.NOT_LOADED;
        }
        long now = now(player);
        BuilderMix.Table mix = BuilderMix.forWand(wand, mode);
        if (confirm && needsConfirm(player, wand, mode, area, block, mix, confirmTick, now)) {
            return StartResult.NEEDS_CONFIRM;
        }
        PENDING.remove(player.getUUID());
        BuilderUndo.Record record = mode.bottomUp() ? BuilderUndo.create(player.level().dimension(), now) : null;
        if (record != null) {
            record.shape = BuilderSelection.shape(wand);
        }
        Job job = new Job(Kind.BUILD, player, hand, wand, mode, area, box, area.size(), block, record, mix);
        preProbe(job);
        return enqueue(job, capped
                ? Component.translatable("message.essentialcraft5.builder.started_capped", job.total)
                : Component.translatable("message.essentialcraft5.builder.started", job.total));
    }

    /**
     * 沿面：先当场看点中的那格和它那一面（两格，不占测量名额），能沿着铺才登记一个测量，由调度分几刻扫完再开工（{@link #tickSurveys}）。
     * 要选定方块的模式先查有没有方块、点中处在不在施工距离内、那一格的区块加没加载，免得白测一遍。
     */
    private static StartResult requestFace(ServerPlayer player, InteractionHand hand, ItemStack wand, BuilderMode mode, @Nullable BuilderShapes.Hit hit,
                                           boolean confirm) {
        if (hit == null) {
            MagicalBuilderItem.tellError(player, Component.translatable("message.essentialcraft5.builder.face_no_hit"));
            return StartResult.NO_SURFACE;
        }
        if (mode.needsBlock() && chooseBlock(player, hand, wand, mode) == null) {
            MagicalBuilderItem.tellError(player, Component.translatable("message.essentialcraft5.builder.no_block"));
            return StartResult.NO_BLOCK;
        }
        BuilderTier.Stats stats = BuilderTier.stats(wand);
        int reach = stats.maxReach();
        if (BuilderSelection.distanceSqToBox(player.position(), new BoundingBox(hit.pos())) > (double) reach * reach) {
            MagicalBuilderItem.tellError(player, Component.translatable("message.essentialcraft5.builder.out_of_reach", reach));
            return StartResult.OUT_OF_REACH;
        }
        ServerLevel level = player.serverLevel();
        if (!ECLevels.isFullyLoaded(level, hit.pos())) {
            MagicalBuilderItem.tellError(player, Component.translatable("message.essentialcraft5.builder.not_loaded"));
            return StartResult.NOT_LOADED;
        }
        BuilderSurface.Scan scan = new BuilderSurface.Scan(hit.pos(), hit.face(), stats.maxVolume(), BuilderSurface.radius(stats.maxSide()));
        scan.step(BuilderSurface.reader(level), 0);   // 只看起点和它那一面
        if (scan.invalid()) {
            MagicalBuilderItem.tellError(player, Component.translatable("message.essentialcraft5.builder.face_covered"));
            return StartResult.NO_SURFACE;
        }
        UUID id = player.getUUID();
        SURVEYS.put(id, new Survey(player, hand, wand, mode, hit, confirm, now(player), scan));
        LAST_SURVEY.remove(id);
        MagicalBuilderItem.tell(player, Component.translatable("message.essentialcraft5.builder.surveying"));
        return StartResult.SURVEYING;
    }

    /** 这个人最近一次沿面测完以后开工的结果；还在测、或没测过返回 null（GameTest 用） */
    @Nullable
    public static StartResult lastSurvey(UUID player) {
        return LAST_SURVEY.get(player);
    }

    /** 这个人有沿面在测量 */
    public static boolean isSurveying(UUID player) {
        return SURVEYS.containsKey(player);
    }

    /**
     * 铺面 / 直线 / 竖墙的范围（服务端开工和状态包共用）；沿面的范围要测量，不走这里。
     * 起点按魔杖的模式分：清除、替换从点中的那格本身起，填充从那一面外侧起（{@link BuilderMode#inPlace}）。
     */
    public static BuilderArea areaOf(Player player, ItemStack wand, BuilderShape shape, @Nullable BuilderShapes.Hit hit) {
        int[] raw = BuilderSelection.size(wand, shape);
        BuilderTier.Stats stats = BuilderTier.stats(wand);
        BuilderShapes.Size size = BuilderShapes.clamp(raw[0], raw[1], stats.maxSide(), stats.maxVolume());
        BuilderMode mode = BuilderMode.of(wand);
        return BuilderShapes.area(shape, size, player.getDirection(), hit, BuilderShapes.feetAnchor(player), mode != null && mode.inPlace());
    }

    /**
     * 这一单用哪种方块：混铺生效（{@link BuilderMix#forWand}）时是表里的第一种（每格实际用哪种按表算，副手、选定的都不看）；
     * 否则魔杖在主手、模式会放方块、副手拿着能当材料的方块物品（没 NBT、{@link MagicalBuilderItem#recordFailure} 放行）时用副手这种；
     * 否则用魔杖里选定的。清除选定 / 清除选定以外照旧按选定方块比。
     */
    @Nullable
    public static Block chooseBlock(Player player, InteractionHand hand, ItemStack wand, BuilderMode mode) {
        BuilderMix.Table mix = BuilderMix.forWand(wand, mode);
        if (mix != null) {
            return mix.block(0);
        }
        if (hand == InteractionHand.MAIN_HAND && mode.bottomUp()) {
            ItemStack offhand = player.getOffhandItem();
            if (offhand.getItem() instanceof BlockItem blockItem && BuilderMaterials.isPlainMaterial(offhand, offhand.getItem())) {
                Block block = blockItem.getBlock();
                if (block != Blocks.AIR && MagicalBuilderItem.recordFailure(block.defaultBlockState(), net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO) == null) {
                    return block;
                }
            }
        }
        return MagicalBuilderItem.storedBlock(wand);
    }

    /**
     * 这一单要用的几种材料物品（状态包数「持有」用）：混铺时是表里的几种，否则是 {@link #chooseBlock} 那一种；没有方块返回空数组。
     */
    public static Item[] materialItems(Player player, InteractionHand hand, ItemStack wand, BuilderMode mode) {
        BuilderMix.Table mix = BuilderMix.forWand(wand, mode);
        if (mix != null) {
            Item[] items = new Item[mix.size()];
            for (int i = 0; i < items.length; i++) {
                items[i] = mix.block(i).asItem();
            }
            return items;
        }
        Block block = chooseBlock(player, hand, wand, mode);
        return block == null ? new Item[0] : new Item[] {block.asItem()};
    }

    /**
     * 准星下预览逐格核「放不放得下」的格数上限：前这么多格算放置状态、核站不站得住（实体挡着的算放不下），再往后的一律当放得下。
     * 开工前确认（{@link #needsConfirm}）和客户端预览共用这个数，两处「要放几格」的口径一样。
     */
    public static final int PREVIEW_DETAIL_LIMIT = 4096;

    /**
     * 这一格要「放」时放不放得下：算得出放置状态、放下后站得住、不被实体（含玩家自己）挡着。准星下预览和开工前确认共用，
     * 放不下的格算「放不下」，不算进「要放」的格数，也不算进「材料只够 X / Y 格」的 Y。
     */
    public static boolean placeable(Player player, Level level, BlockPos pos, @Nullable Block block, @Nullable Item material) {
        BlockState placed = placementState(player, level, pos, block, material);
        return placed != null && canStand(player, level, pos, placed);
    }

    /**
     * 扫一遍范围看要不要确认；要的话记一条待确认、提示一句、返回 true。同一单（形状、范围、模式、方块都一样）在提示发出后 {@value #CONFIRM_TICKS} 刻内
     * 再点一次算确认：比对用这一下点下去的那刻 clickTick，新记的时限从提示发出的这刻 now 算。沿面点下去要先测几刻才走到这里，两个时刻不一样；
     * 时限要是也从点下去算，测量一长提示出来时就已经过期，再点永远确认不了，客户端从收到提示起的倒计时也对不上。
     * 格数和准星下的「要放 / 放不下」同一个算法：建筑高度、世界边界外的格不算，要放的格逐格核 {@link #placeable}（前 {@value #PREVIEW_DETAIL_LIMIT} 格）。
     */
    private static boolean needsConfirm(ServerPlayer player, ItemStack wand, BuilderMode mode, BuilderArea area, @Nullable Block block,
                                        @Nullable BuilderMix.Table mix, long clickTick, long now) {
        Plan plan = plan(player, wand, mode, area, block, mix);
        int place = plan.place();
        int breaks = plan.breaks();
        int replace = plan.replace();
        int actions = place + breaks + replace;
        boolean creative = player.getAbilities().instabuild;
        int needMaterial = place + replace;
        Item[] items = plan.items();
        long[] have = new long[items.length];
        long haveAll = 0;
        boolean shortMaterial = false;
        if (!creative && needMaterial > 0) {
            for (int k = 0; k < items.length; k++) {
                if (plan.need()[k] > 0) {
                    long[] counts = BuilderMaterials.count(player, items[k], wand);
                    have[k] = counts[0] + counts[1];
                    haveAll += have[k];
                    // 混铺时哪一种不够都要确认：用完会换别的几种顶上，铺出来的比例就和设定的不一样了
                    shortMaterial |= have[k] < plan.need()[k];
                }
            }
            if (items.length == 0) {
                shortMaterial = true;   // 要放却没有方块（老逻辑：持有 0 < 要用）
            }
        }
        BuilderTier.Stats stats = BuilderTier.stats(wand);
        long needMru = (long) MRUDrain.discounted(player, stats.placeCost()) * place + (long) MRUDrain.discounted(player, stats.breakCost()) * breaks
                + (long) MRUDrain.discounted(player, stats.replaceCost()) * replace;
        long mru = creative ? Long.MAX_VALUE : availableMru(player, wand);
        boolean shortMru = !creative && mru < needMru;
        if (actions <= BuilderActions.confirmThreshold(player.getUUID()) && !shortMaterial && !shortMru) {
            return false;
        }
        UUID id = player.getUUID();
        Fingerprint fingerprint = new Fingerprint(area.box(), area.anchor(), area.size(), mode, block, BuilderSelection.shape(wand), mix);
        Pending pending = PENDING.get(id);
        if (pending != null && pending.fingerprint().equals(fingerprint) && clickTick <= pending.expires()) {
            PENDING.remove(id);
            return false;
        }
        PENDING.put(id, new Pending(fingerprint, now + CONFIRM_TICKS));
        Component message;
        if (shortMaterial && mix != null) {
            message = Component.translatable("message.essentialcraft5.builder.confirm_mix", mixList(items, have, plan.need()));
        } else if (shortMaterial) {
            message = Component.translatable("message.essentialcraft5.builder.confirm_material", haveAll, needMaterial);
        } else if (shortMru) {
            message = Component.translatable("message.essentialcraft5.builder.confirm_mru", mru, needMru);
        } else {
            message = Component.translatable("message.essentialcraft5.builder.confirm_large", actions);
        }
        MagicalBuilderItem.tell(player, message);
        return true;
    }

    /**
     * 开工前扫一遍的结果：要放、要拆、要换的格数，几种材料（{@link Job#items} 同序）各要用几个（放 + 换）。
     * 混铺时每格按 {@link BuilderMix#kindAt} 算那一种（同施工），「放不放得下」也按那一种核。
     */
    public record Plan(int place, int breaks, int replace, Item[] items, int[] need) {
    }

    /**
     * 扫一遍范围（只读）：格数口径同准星下预览——建筑高度、世界边界外、没加载的格不算，要放的格逐格核 {@link #placeable}（前 {@value #PREVIEW_DETAIL_LIMIT} 格）。
     * 开工前确认用它；GameTest 拿它和施工结果对。
     */
    public static Plan plan(ServerPlayer player, ItemStack wand, BuilderMode mode, BuilderArea area, @Nullable Block block, @Nullable BuilderMix.Table mix) {
        ServerLevel level = player.serverLevel();
        int place = 0;
        int breaks = 0;
        int replace = 0;
        boolean bottomUp = mode.bottomUp();
        Block[] blocks;
        if (mix != null) {
            blocks = new Block[mix.size()];
            for (int k = 0; k < blocks.length; k++) {
                blocks[k] = mix.block(k);
            }
        } else {
            blocks = block == null ? new Block[0] : new Block[] {block};
        }
        Item[] items = new Item[blocks.length];
        for (int k = 0; k < blocks.length; k++) {
            items[k] = blocks[k].asItem();
        }
        int[] need = new int[blocks.length];
        long seed = mix == null ? 0L : BuilderMix.seedFor(mix, area);
        BoundingBox box = area.box();
        for (int i = 0; i < area.size(); i++) {
            BlockPos pos = area.cellAt(i, bottomUp);
            if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos) || !ECLevels.isFullyLoaded(level, pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            boolean same = mix != null ? mix.contains(state) : block != null && state.is(block);
            int k = mix == null ? 0 : BuilderMix.kindAt(mix, seed, box, pos);
            switch (mode.decide(classify(level, pos, state), same)) {
                case PLACE -> {
                    if (blocks.length == 0 || i >= PREVIEW_DETAIL_LIMIT || placeable(player, level, pos, blocks[k], items[k])) {
                        place++;
                        if (blocks.length > 0) {
                            need[k]++;
                        }
                    }
                }
                case BREAK -> breaks++;
                case REPLACE -> {
                    replace++;
                    if (blocks.length > 0) {
                        need[k]++;
                    }
                }
                default -> {
                }
            }
        }
        return new Plan(place, breaks, replace, items, need);
    }

    /** 确认那一句里的「圆石 87 / 120、石砖 30 / 40」：每种持有 / 要用，只列这一单用得到的 */
    private static Component mixList(Item[] items, long[] have, int[] need) {
        MutableComponent out = Component.empty();
        boolean first = true;
        for (int k = 0; k < items.length; k++) {
            if (need[k] <= 0) {
                continue;
            }
            if (!first) {
                out.append(Component.translatable("message.essentialcraft5.builder.list_sep"));
            }
            first = false;
            out.append(Component.translatable("message.essentialcraft5.builder.mix_entry", items[k].getDescription(), have[k], need[k]));
        }
        return out;
    }

    /** 魔杖自带的加上身上储存物品、矩阵里的 MRU；有无限的返回 Long.MAX_VALUE */
    public static long availableMru(Player player, ItemStack wand) {
        if (MRUDrain.hasInfiniteStorage(player)) {
            return Long.MAX_VALUE;
        }
        return MRUStaffItem.storedMRU(wand) + MRUDrain.availableFromStorages(player);
    }

    /** 开工前在要动的格子里均匀挑最多 {@value #PRE_PROBE_SAMPLES} 格探测；有被拒的提示比例，并让任务一开始就先探后放 */
    private static void preProbe(Job job) {
        if (job.area == null || job.total == 0) {
            return;
        }
        ServerPlayer player = job.player;
        ServerLevel level = player.serverLevel();
        int asked = 0;
        int denied = 0;
        int step = Math.max(1, job.total / PRE_PROBE_SAMPLES);
        for (int i = 0; i < job.total && asked < PRE_PROBE_SAMPLES; i += step) {
            BlockPos pos = job.area.cellAt(i, job.mode.bottomUp());
            if (level.isOutsideBuildHeight(pos) || !ECLevels.isFullyLoaded(level, pos)) {
                continue;
            }
            if (decide(job, level, pos, level.getBlockState(pos)) == BuilderMode.Action.SKIP) {
                continue;
            }
            ECProtection.Verdict verdict = ECProtection.check(player, level, pos, job);
            if (verdict == ECProtection.Verdict.UNKNOWN) {
                continue;
            }
            asked++;
            if (verdict == ECProtection.Verdict.DENY) {
                denied++;
            }
        }
        if (denied > 0) {
            MagicalBuilderItem.tellError(player, Component.translatable("message.essentialcraft5.builder.pre_probe", denied * 100 / asked));
            if (ECBuilder.Config.probeAfterDenied()) {
                job.probeMode = true;
            }
        }
    }

    /** 这个人在做的和排队的都满了（正在测量的沿面算一个排队的：测完就排进去，反复测量也绕不过每人的名额） */
    private static boolean queueFull(UUID id) {
        int waiting = waitingCount(id) + (SURVEYS.containsKey(id) ? 1 : 0);
        return (JOBS.containsKey(id) || waiting > 0) && waiting >= ECBuilder.Config.maxQueuedPerPlayer();
    }

    private static int waitingCount(UUID id) {
        int n = 0;
        for (Job job : WAITING) {
            if (job.owner().equals(id)) {
                n++;
            }
        }
        return n;
    }

    /** 排队时动作栏那一句：前面还有 ahead 个；为 0 时（只是名额被占着）写「等空出名额」 */
    public static Component queuedMessage(int ahead) {
        return queuedMessage(ahead, false);
    }

    /** 同上；ownRunning：自己手上已有一单在做，前面又没有别人排着时，等的是自己这一单做完，写「当前这一单完成后开工」 */
    public static Component queuedMessage(int ahead, boolean ownRunning) {
        if (ahead > 0) {
            return Component.translatable("message.essentialcraft5.builder.queued", ahead);
        }
        return ownRunning
                ? Component.translatable("message.essentialcraft5.builder.queued_own")
                : Component.translatable("message.essentialcraft5.builder.queued_slot");
    }

    /** 能马上开工就开工，否则排队 */
    private static StartResult enqueue(Job job, Component startedMessage) {
        UUID id = job.owner();
        if (!JOBS.containsKey(id) && activeCount() < ECBuilder.Config.maxJobsTotal()) {
            JOBS.put(id, job);
            MagicalBuilderItem.tell(job.player, startedMessage);
            return StartResult.STARTED;
        }
        WAITING.add(job);
        // 前面没有排着的单子时不写「前面还有 0 个」：自己有单在做就说等这一单做完，否则是名额被占着，说等空出名额
        MagicalBuilderItem.tell(job.player, queuedMessage(WAITING.size() - 1, JOBS.containsKey(id)));
        return StartResult.QUEUED;
    }

    /**
     * 全服占着名额的任务数（暂停的也算：暂停的人补料后直接恢复、不再看名额，不算的话恢复时会超过 {@code max_jobs_total}）。
     * 暂停的任务不分格数和耗时预算，只占名额。
     */
    private static int activeCount() {
        return JOBS.size();
    }

    /** 这个人有排着队的撤销 */
    private static boolean undoWaiting(UUID id) {
        for (Job job : WAITING) {
            if (job.kind == Kind.UNDO && job.owner().equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** 停下这个人正在做的活，连排队的一起清掉（潜行对空气右键）；什么都没有返回 false */
    public static boolean stop(ServerPlayer player) {
        UUID id = player.getUUID();
        boolean any = dropWaiting(id, StopReason.STOPPED);
        if (SURVEYS.remove(id) != null) {
            MagicalBuilderItem.tell(player, Component.translatable("message.essentialcraft5.builder.survey_stopped"));
            any = true;
        }
        Job job = JOBS.get(id);
        if (job != null) {
            finish(job, StopReason.STOPPED);
            any = true;
        }
        return any;
    }

    /** 丢掉这个人排队的任务（撤销任务的记录压回去），返回有没有丢 */
    private static boolean dropWaiting(UUID id, StopReason reason) {
        boolean any = false;
        for (Iterator<Job> it = WAITING.iterator(); it.hasNext(); ) {
            Job job = it.next();
            if (job.owner().equals(id)) {
                it.remove();
                settleUndo(job);
                RESULTS.put(id, result(job, reason));
                any = true;
            }
        }
        return any;
    }

    /**
     * 撤销最近一次放置（服务端；魔杖键 + 右键、面板按钮调）。查过能撤以后，正在做的活和排队的先停下（撤不了就都不动）；记录要在这个维度、没过 {@code undo_window_seconds}、
     * 离那片 {@code max_reach} 格以内。撤销本身是一个任务，走同样的调度、预算、排队、复核。
     */
    public static StartResult undo(ServerPlayer player) {
        if (!ECProtection.isRealPlayer(player)) {
            return StartResult.NOT_REAL_PLAYER;
        }
        if (!player.mayBuild()) {
            MagicalBuilderItem.tellError(player, Component.translatable("message.essentialcraft5.builder.cannot_build"));
            return StartResult.CANNOT_BUILD;
        }
        InteractionHand hand = BuilderMode.of(player.getMainHandItem()) != null ? InteractionHand.MAIN_HAND
                : BuilderMode.of(player.getOffhandItem()) != null ? InteractionHand.OFF_HAND : null;
        if (hand == null) {
            return StartResult.NOT_A_WAND;
        }
        if (ECBuilder.Config.dimensionDisabled(player.level().dimension().location())) {
            MagicalBuilderItem.tellError(player, Component.translatable("message.essentialcraft5.builder.disabled_dimension"));
            return StartResult.DISABLED_DIMENSION;
        }
        int window = ECBuilder.Config.undoWindowSeconds();
        if (window <= 0) {
            MagicalBuilderItem.tellError(player, Component.translatable("message.essentialcraft5.builder.undo_off"));
            return StartResult.NOTHING_TO_UNDO;
        }
        UUID id = player.getUUID();
        Job running = JOBS.get(id);
        if (running != null && running.kind == Kind.UNDO || undoWaiting(id)) {
            MagicalBuilderItem.tellError(player, Component.translatable("message.essentialcraft5.builder.busy"));
            return StartResult.BUSY;
        }
        // 先看有没有能撤的，能撤才停正在做的和排队的：撤不了时手上的活不该没了。
        // 正在做的这单已经放成过一格的话，它的记录就在栈顶，peek 到的就是它（停下以后照样撤它）；排队的施工还没放过，不在栈里
        // 判断的先后和面板（BuilderPanel.compute）一致：没有记录 → 过期 / 别的维度 → 太远 → 上一单是清除，两边给同一个原因
        BuilderUndo.Record record = BuilderUndo.peek(id);
        if (record == null || record.size() == 0) {
            if (clearedSince(id, null)) {
                MagicalBuilderItem.tellError(player, Component.translatable("message.essentialcraft5.builder.undo_cleared"));
                return StartResult.UNDO_CLEARED;
            }
            MagicalBuilderItem.tellError(player, Component.translatable("message.essentialcraft5.builder.undo_none"));
            return StartResult.NOTHING_TO_UNDO;
        }
        long now = now(player);
        if (record.dimension() != player.level().dimension() || now - record.createdTick() > window * 20L) {
            MagicalBuilderItem.tellError(player, Component.translatable("message.essentialcraft5.builder.undo_expired"));
            return StartResult.UNDO_EXPIRED;
        }
        BoundingBox bounds = Objects.requireNonNull(record.bounds());
        int reach = BuilderTier.stats(player.getItemInHand(hand)).maxReach();
        if (BuilderSelection.distanceSqToBox(player.position(), bounds) > (double) reach * reach) {
            MagicalBuilderItem.tellError(player, Component.translatable("message.essentialcraft5.builder.out_of_reach", reach));
            return StartResult.OUT_OF_REACH;
        }
        if (clearedSince(id, record)) {
            // 上一单是清除：清除的结果不能撤，也不越过它去撤更早那次放置（面板按钮同样变灰）
            MagicalBuilderItem.tellError(player, Component.translatable("message.essentialcraft5.builder.undo_cleared"));
            return StartResult.UNDO_CLEARED;
        }
        // 开工后头几刻（飞行特效的提前量内）这一单还一格没放、记录没进栈，栈顶是上一单：这时按撤销是想撤这一单，
        // 只停下它，不越过它去撤上一单
        if (running != null && running.ring != null && running.record != null && !running.recordPushed) {
            finish(running, StopReason.STOPPED);
            dropWaiting(id, StopReason.STOPPED);
            return StartResult.NOTHING_TO_UNDO;
        }
        if (running != null) {
            finish(running, StopReason.STOPPED);
        }
        dropWaiting(id, StopReason.STOPPED);
        SURVEYS.remove(id);
        BuilderUndo.pop(id);
        ItemStack wand = player.getItemInHand(hand);
        Job job = new Job(Kind.UNDO, player, hand, wand, Objects.requireNonNull(BuilderMode.of(wand)), null, bounds, record.size(), null, record, null);
        return enqueue(job, Component.translatable("message.essentialcraft5.builder.undo_started", record.size()));
    }

    /**
     * 这根魔杖的两点那一单在等再点一次确认，还没过时限：等着的必须是两点形状、框就是这根魔杖两个点围成的框
     * （别的魔杖、别的形状在等确认时，点方块不算确认这一单）
     */
    public static boolean awaitingConfirm(ServerPlayer player, ItemStack wand) {
        Pending pending = PENDING.get(player.getUUID());
        if (pending == null || now(player) > pending.expires() || pending.fingerprint().shape() != BuilderShape.POINTS
                || BuilderSelection.shape(wand) != BuilderShape.POINTS) {
            return false;
        }
        BlockPos a = BuilderSelection.first(wand);
        BlockPos b = BuilderSelection.second(wand);
        return a != null && b != null && pending.fingerprint().box().equals(BuilderSelection.box(a, b));
    }

    /** 有活在做、暂停、排队，或沿面在测量 */
    public static boolean isRunning(UUID player) {
        return JOBS.containsKey(player) || waitingCount(player) > 0 || SURVEYS.containsKey(player);
    }

    /** 正在做的活已经做成了几格；没有活在做返回 -1 */
    public static int progress(UUID player) {
        Job job = JOBS.get(player);
        return job == null ? -1 : job.done;
    }

    /** 这个人的任务现状（在做的优先，其次在测量的沿面，再次排在最前的那个）；都没有返回 null */
    @Nullable
    public static Snapshot snapshot(UUID player, long now) {
        Job job = JOBS.get(player);
        if (job != null) {
            int left = job.paused && job.pauseDeadline >= 0 ? (int) Math.max(0, job.pauseDeadline - now) : 0;
            return new Snapshot(job.kind, job.paused, false, job.done, job.total, 0, left, job.paused && job.pauseReason == StopReason.NO_MRU, false);
        }
        Survey survey = SURVEYS.get(player);
        if (survey != null) {
            return new Snapshot(Kind.BUILD, false, false, survey.scan.found(), survey.scan.limit(), 0, 0, false, true);
        }
        int ahead = 0;
        for (Job waiting : WAITING) {
            if (waiting.owner().equals(player)) {
                return new Snapshot(waiting.kind, false, true, 0, waiting.total, ahead, 0, false, false);
            }
            ahead++;
        }
        return null;
    }

    /** 这个人最近一次结束的活；没有返回 null */
    @Nullable
    public static Result lastResult(UUID player) {
        return RESULTS.get(player);
    }

    /** 丢掉所有任务、排队、结果、待确认、撤销记录，不提示（GameTest 的 batch 结束时用、停服时用） */
    public static void clearAll() {
        JOBS.clear();
        WAITING.clear();
        RESULTS.clear();
        PENDING.clear();
        LAST_REQUEST.clear();
        SURVEYS.clear();
        LAST_SURVEY.clear();
        LAST_CLEAR.clear();
        BuilderUndo.clearAll();
        BuilderActions.clearAll();
        BuilderSalvage.clearAll();
        rotation = 0;
        surveyRotation = 0;
        timeExhausted = false;
    }

    private static long now(ServerPlayer player) {
        return player.server.getTickCount();
    }

    // ------------------------------------------------------------------
    // 事件
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onBuilderServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || JOBS.isEmpty() && WAITING.isEmpty() && SURVEYS.isEmpty()) {
            return;
        }
        tickJobs(event.getServer());
    }

    /** 下线：任务、排队、测量、待确认、撤销记录、限频计数全清 */
    @SubscribeEvent
    public static void onBuilderLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        Job job = JOBS.remove(id);
        if (job != null) {
            job.finished = true;   // 下线发生在一格的事件里时，run 循环回来看到它就不再往下做、也不再 finish
            endFlights(job);
        }
        WAITING.removeIf(waiting -> waiting.owner().equals(id));
        SURVEYS.remove(id);
        LAST_SURVEY.remove(id);
        PENDING.remove(id);
        LAST_REQUEST.remove(id);
        LAST_CLEAR.remove(id);
        BuilderUndo.forget(id);
        BuilderActions.forget(id);
        BuilderSalvage.forget(id);
    }

    /** 最近一单清除类结束的那一刻（服务器刻）；没有返回 -1 */
    public static long lastClearTick(UUID player) {
        return LAST_CLEAR.getOrDefault(player, -1L);
    }

    /**
     * 上一单是清除类（拆成过至少一格），撤销两条路（面板按钮、魔杖键 + 右键）都据此不撤：
     * 撤销栈顶那次放置（record，null = 栈里没有）压在栈顶时结束过一单清除（{@link BuilderUndo.Record#clearedAfter}），
     * 栈是空的而最近一单是清除，或者正在做（含暂停，暂停的也留在 JOBS 里）的是一单已经拆过的清除。
     * 最后一种不算的话，清除做到一半时撤销会先把它停下、再越过它撤更早那次放置。排队里的清除还没拆过，不用看。
     */
    public static boolean clearedSince(UUID player, @Nullable BuilderUndo.Record record) {
        Job running = JOBS.get(player);
        if (running != null && running.kind == Kind.BUILD && !running.mode.bottomUp() && running.done > 0) {
            return true;
        }
        return record == null ? lastClearTick(player) >= 0L : record.clearedAfter();
    }

    /** 正在做（含暂停）或排着队的是撤销任务 */
    public static boolean undoBusy(UUID player) {
        Job running = JOBS.get(player);
        return running != null && running.kind == Kind.UNDO || undoWaiting(player);
    }

    /** 换维度：当前任务中断、排队的丢掉，撤销记录留着 */
    @SubscribeEvent
    public static void onBuilderChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            interruptAll(player.getUUID());
        }
    }

    /** 死亡：同换维度 */
    @SubscribeEvent
    public static void onBuilderDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !event.isCanceled()) {
            interruptAll(player.getUUID());
        }
    }

    private static void interruptAll(UUID id) {
        dropWaiting(id, StopReason.INTERRUPTED);
        Survey survey = SURVEYS.remove(id);
        if (survey != null) {
            MagicalBuilderItem.tellError(survey.player, Component.translatable("message.essentialcraft5.builder.survey_cancelled"));
        }
        Job job = JOBS.get(id);
        if (job != null) {
            finish(job, StopReason.INTERRUPTED);
        }
        PENDING.remove(id);
    }

    @SubscribeEvent
    public static void onBuilderServerStopped(ServerStoppedEvent event) {
        clearAll();
    }

    // ------------------------------------------------------------------
    // 调度
    // ------------------------------------------------------------------

    /** 本刻做一轮：先让排队的补进来，暂停的看补料，其余从轮转起点开始分全服预算；剩下的耗时预算给沿面测量 */
    private static void tickJobs(MinecraftServer server) {
        long now = server.getTickCount();
        timeExhausted = false;
        promote();
        List<Job> runnable = new ArrayList<>();
        for (Job job : new ArrayList<>(JOBS.values())) {
            if (JOBS.get(job.owner()) != job) {
                continue;
            }
            if (job.paused) {
                tickPaused(job, now);
            } else {
                runnable.add(job);
            }
        }
        // 耗时预算从这里起算（补排队、暂停的复核不占施工的时间）；测量用施工剩下的
        long deadline = System.nanoTime() + (long) (ECBuilder.Config.maxMillisPerTick() * 1_000_000D);
        int count = runnable.size();
        if (count > 0) {
            int startAt = Math.floorMod(rotation++, count);
            int left = ECBuilder.Config.maxCellsPerTickTotal();
            for (int k = 0; k < count; k++) {
                Job job = runnable.get((startAt + k) % count);
                if (JOBS.get(job.owner()) != job || job.paused) {
                    continue;
                }
                int waiting = count - k;
                job.stats = BuilderTier.stats(job.wand);
                int share = left <= 0 || timeExhausted ? 0 : Math.min(job.stats.cellsPerTick(), (left + waiting - 1) / waiting);
                left -= run(job, share, deadline, now);
            }
        }
        tickSurveys(now, deadline);
    }

    /**
     * 沿面测量：从轮转起点起，每个测量这刻最多读 {@code face_scan_per_tick} 格；和施工共用这刻的耗时预算，用完了就只让轮到的第一个扫
     * （不会被大单饿着），其余的下刻再说。复核没过的取消；扫完的移出测量表、按测出来的范围开工（{@link #launch}），一刻最多开一个。
     */
    private static void tickSurveys(long now, long deadline) {
        if (SURVEYS.isEmpty()) {
            return;
        }
        List<Survey> surveys = new ArrayList<>(SURVEYS.values());
        int count = surveys.size();
        int startAt = Math.floorMod(surveyRotation++, count);
        int budget = ECBuilder.Config.faceScanPerTick();
        boolean anyScanned = false;
        boolean launched = false;
        for (int k = 0; k < count; k++) {
            Survey survey = surveys.get((startAt + k) % count);
            UUID id = survey.owner();
            if (SURVEYS.get(id) != survey) {
                continue;
            }
            if (!surveyValid(survey)) {
                SURVEYS.remove(id);
                if (!survey.player.hasDisconnected()) {
                    tellSurveyInterrupted(survey);
                }
                continue;
            }
            if (!survey.scan.done()) {
                if (anyScanned && System.nanoTime() >= deadline) {
                    continue;
                }
                anyScanned = true;
                if (!survey.scan.step(BuilderSurface.reader(survey.player.serverLevel()), budget)) {
                    continue;
                }
            }
            // 测完的开工要扫一遍范围（确认、探测），一刻只让一个开工，几个同一刻测完的依次往后挪一刻
            if (launched) {
                continue;
            }
            launched = true;
            SURVEYS.remove(id);
            BuilderArea area = survey.scan.area(survey.mode.inPlace());
            StartResult result;
            if (area.size() == 0) {
                MagicalBuilderItem.tellError(survey.player, Component.translatable("message.essentialcraft5.builder.face_covered"));
                result = StartResult.NO_SURFACE;
            } else {
                result = launch(survey.player, survey.hand, survey.wand, survey.mode, area, survey.confirm, survey.requestedAt, survey.scan.capped());
            }
            LAST_SURVEY.put(id, result);
        }
    }

    /** 测量期间每刻复核：没下线、活着、不是旁观、能改方块、同一维度、同一只手里还是同一根魔杖且形状还是沿面、离点中那格在施工距离内 */
    private static boolean surveyValid(Survey survey) {
        ServerPlayer player = survey.player;
        if (player.hasDisconnected() || player.isRemoved() || !player.isAlive() || player.isSpectator() || !player.mayBuild()
                || player.level().dimension() != survey.dimension || player.getItemInHand(survey.hand) != survey.wand
                || BuilderSelection.shape(survey.wand) != BuilderShape.FACE) {
            return false;
        }
        int reach = BuilderTier.stats(survey.wand).maxReach();
        return BuilderSelection.distanceSqToBox(player.position(), new BoundingBox(survey.hit.pos())) <= (double) reach * reach;
    }

    private static void tellSurveyInterrupted(Survey survey) {
        MagicalBuilderItem.tellError(survey.player, Component.translatable("message.essentialcraft5.builder.survey_interrupted",
                BuilderTier.stats(survey.wand).maxReach()));
    }

    /** 排队的任务按先来后到补进空出来的名额；轮到时复核没过的丢掉 */
    private static void promote() {
        for (Iterator<Job> it = WAITING.iterator(); it.hasNext(); ) {
            Job job = it.next();
            UUID id = job.owner();
            if (JOBS.containsKey(id) || activeCount() >= ECBuilder.Config.maxJobsTotal()) {
                continue;
            }
            it.remove();
            if (!stillValid(job)) {
                settleUndo(job);
                RESULTS.put(id, result(job, StopReason.INTERRUPTED));
                continue;
            }
            JOBS.put(id, job);
            MagicalBuilderItem.tell(job.player, job.kind == Kind.UNDO
                    ? Component.translatable("message.essentialcraft5.builder.undo_started", job.total)
                    : Component.translatable("message.essentialcraft5.builder.started", job.total));
        }
    }

    /** 暂停的任务：照样复核；到时限就结束；每 {@value #RESUME_CHECK_TICKS} 刻看一次补上没有 */
    private static void tickPaused(Job job, long now) {
        job.stats = BuilderTier.stats(job.wand);
        if (!stillValid(job)) {
            finish(job, StopReason.INTERRUPTED);
            return;
        }
        if (job.pauseDeadline >= 0 && now >= job.pauseDeadline) {
            finish(job, job.pauseReason == null ? StopReason.OUT_OF_MATERIAL : job.pauseReason);
            return;
        }
        if (now - job.lastResumeCheck < RESUME_CHECK_TICKS) {
            return;
        }
        job.lastResumeCheck = now;
        ServerPlayer player = job.player;
        boolean ready = job.pauseReason == StopReason.NO_MRU
                ? MRUDrain.canAfford(player, job.wand, job.needed)
                : player.getAbilities().instabuild || anyMaterial(job, now);
        if (ready) {
            job.paused = false;
            MagicalBuilderItem.tell(player, Component.translatable("message.essentialcraft5.builder.resumed"));
        }
    }

    /** 做一个任务的这一刻，返回动了几格（算进全服预算） */
    private static int run(Job job, int share, long deadline, long now) {
        if (!stillValid(job)) {
            finish(job, StopReason.INTERRUPTED);
            return 0;
        }
        ServerPlayer player = job.player;
        ServerLevel level = player.serverLevel();
        List<ItemStack> drops = new ArrayList<>();
        int stride = Math.max(EFFECT_STRIDE, (share + MAX_EFFECTS_PER_TICK - 1) / MAX_EFFECTS_PER_TICK);
        int attempts = 0;
        int scanned = 0;
        job.effects = 0;
        StopReason stop = null;
        int deniedLimit = ECBuilder.Config.deniedLimit();
        int streakLimit = ECBuilder.Config.deniedStreakLimit();
        // 放置类施工只做到已经到期的计划为止（还没计划、没飞过的格子不做）；其余照旧做到底
        int limit = job.ring == null ? job.total : job.ring.executableUntil(now, job.next);
        if (job.kind == Kind.UNDO && job.lead > 0) {
            job.flewBack = new LongArrayList();
            job.flewBackStates = new IntArrayList();
        }
        try {
            while (job.next < limit && attempts < share && scanned < SCAN_PER_TICK) {
                boolean effect = attempts % stride == 0 && job.effects < MAX_EFFECTS_PER_TICK;
                Outcome outcome = job.kind == Kind.UNDO
                        ? processUndo(job, level, drops, effect, now)
                        : process(job, level, job.area.cellAt(job.next, job.mode.bottomUp()), drops, effect, now);
                if (job.finished) {
                    // 这一格的事件里任务已经被结束（死亡、换维度、下线）：不再往下做。结束时这一格还没算进去（事件没取消就是放成了），
                    // 结果按做完这一格的数重记一次
                    if (outcome == Outcome.ATTEMPTED) {
                        attempts++;
                    }
                    if (job.finishReason != null) {
                        RESULTS.put(job.owner(), result(job, job.finishReason));
                    }
                    break;
                }
                if (outcome == Outcome.OUT_OF_MATERIAL) {
                    stop = StopReason.OUT_OF_MATERIAL;
                    break;
                }
                if (outcome == Outcome.NO_MRU) {
                    stop = StopReason.NO_MRU;
                    break;
                }
                if (outcome == Outcome.WAIT) {
                    attempts++;
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
                    // 最后一格就算到了上限也按做完结算（报「已做完、N 格没有权限」，不报「已停工」）
                    boolean more = job.next < job.total;
                    if (more && job.denied + job.probedDenied + job.brokenOnly >= deniedLimit) {
                        stop = StopReason.DENIED;
                        break;
                    }
                    if (more && streakLimit > 0 && job.deniedStreak >= streakLimit) {
                        job.stoppedByStreak = true;
                        stop = StopReason.DENIED;
                        break;
                    }
                    if (System.nanoTime() >= deadline) {
                        timeExhausted = true;
                        break;
                    }
                } else if ((scanned & 63) == 0 && System.nanoTime() >= deadline) {
                    timeExhausted = true;
                    break;
                }
            }
        } finally {
            job.peakEffects = Math.max(job.peakEffects, job.effects);
            // 回收：拆下的先塞随身容器（每刻只列一次容器），塞不下的照旧进背包、掉脚下
            deliver(player, job.salvage ? BuilderSalvage.stash(player, job.wand, drops) : drops);
            // 进过飞行包的格子不记 lastPlaced（放置声由客户端在落位时放），这里只剩没进包的
            playPlaceSound(job, level);
            sendFlewBack(job);
        }
        if (job.finished) {
            return attempts;
        }
        if (stop == StopReason.OUT_OF_MATERIAL || stop == StopReason.NO_MRU) {
            if (job.kind == Kind.BUILD && ECBuilder.Config.materialPauseSeconds() > 0 && stillHolding(job)) {
                pause(job, stop, now);
            } else {
                finish(job, stop);
            }
        } else if (stop != null) {
            finish(job, stop);
        } else if (job.next >= job.total) {
            finish(job, StopReason.FINISHED);
        } else if (job.ring != null) {
            job.ring.retire(job.next);
            planFlights(job, level, share, deadline, now);
        }
        return attempts;
    }

    /**
     * 放置类施工的计划（只在提前量大于 0 时）：从上次计划到的那一格往后，只读方块、按 {@link #decide} 挑出要动的格子，最多 share 格
     * （和这一刻分到的份额一样，不用动的格子不算、每次最多看 {@value #SCAN_PER_TICK} 格），记成「第 now + D 刻起可以执行」的一段，
     * 能放下的格子按预计的方块状态发飞行包。不取料、不扣 MRU、不改世界、不探测领地；这些都留给执行时逐格做。
     * 执行落后、已经攒了 D + 1 段时这一刻不计划。
     */
    private static void planFlights(Job job, ServerLevel level, int share, long deadline, long now) {
        BuilderFlightPlan.Ring ring = Objects.requireNonNull(job.ring);
        int index = Math.max(ring.planNext, job.next);
        // 本刻耗时用完时，队列里还有没执行的计划就不再计划；已经没有了照样计划一小段（下面每 64 格看一次耗时，最多再看 64 格），
        // 不然攒的计划做完以后要空等 D 刻，预算吃紧时施工会慢将近一半
        boolean starved = ring.planNext <= job.next;
        if (share <= 0 || ring.full() || index >= job.total || timeExhausted && !starved) {
            return;
        }
        BuilderArea area = Objects.requireNonNull(job.area);
        boolean bottomUp = job.mode.bottomUp();
        LongArrayList positions = new LongArrayList();
        IntArrayList states = new IntArrayList();
        int planned = 0;
        int scanned = 0;
        while (index < job.total && planned < share && scanned < SCAN_PER_TICK) {
            BlockPos pos = area.cellAt(index, bottomUp);
            index++;
            scanned++;
            if (!level.isOutsideBuildHeight(pos) && ECLevels.isFullyLoaded(level, pos)) {
                BlockState state = level.getBlockState(pos);
                BuilderMode.Action action = decide(job, level, pos, state);
                if (action != BuilderMode.Action.SKIP) {
                    planned++;
                    BlockState flying = predictPlaced(job, level, pos, state, action);
                    if (flying != null) {
                        positions.add(pos.asLong());
                        states.add(Block.getId(flying));
                        Objects.requireNonNull(job.flown).add(pos.asLong());
                    }
                }
            }
            if ((scanned & 63) == 0 && System.nanoTime() >= deadline) {
                timeExhausted = true;
                break;
            }
        }
        ring.push(now + ring.lead, index);
        ring.planNext = index;
        if (!positions.isEmpty()) {
            ring.sent = true;
            BuilderFlightPlan.send(level, job.player, job.box,
                    BuilderFlightPlan.pack(S2CBuilderFlightPacket.KIND_PLACE, job.player.getId(), job.id, job.lead, positions, states));
        }
    }

    /**
     * 计划时预计这一格会放下什么（只给特效看）：选中的那种的放置状态；放不下、立不住、是全部替换会跳过的「附属的那一半」时返回 null（不飞）。
     * 混铺用完一种换下一种这类要看材料的情况不在这里算，执行时照 {@link #choose} 定。
     */
    @Nullable
    private static BlockState predictPlaced(Job job, ServerLevel level, BlockPos pos, BlockState state, BuilderMode.Action action) {
        if (job.blocks.length == 0) {
            return null;
        }
        if (enclosed(level, pos)) {
            // 六面都被完整不透光的方块包住：玩家本来看不到这一格，不写进包（否则包里有没有这一格就暴露了里面是什么）
            return null;
        }
        ServerPlayer player = job.player;
        int k = job.kindAt(pos);
        BlockState placed;
        if (action == BuilderMode.Action.REPLACE) {
            if (mainPartCandidate(pos, state) != null) {
                return null;
            }
            placed = job.blocks[k].getStateForPlacement(new PrePlaceContext(level, player, new ItemStack(job.items[k]), pos));
        } else if (action == BuilderMode.Action.PLACE) {
            placed = placementState(player, level, pos, job.blocks[k], job.items[k]);
        } else {
            return null;
        }
        if (placed == null) {
            return null;
        }
        if (canStand(player, level, pos, placed)) {
            return placed;
        }
        // 从下往上做时，下面那格多半是这一单还没放到的格子（沙子、火把这类要靠它撑着）：下面那格在范围内就按放得下算，只看人和实体挡不挡
        return job.box.isInside(pos.below()) && clearOf(player, level, pos, placed) ? placed : null;
    }

    /** 这一格六面都是完整不透光的方块 */
    private static boolean enclosed(ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos side = new BlockPos.MutableBlockPos();
        for (Direction direction : Direction.values()) {
            side.setWithOffset(pos, direction);
            // 没加载的邻格不读（读了会把区块加载进来），按露在外面算
            if (!level.isLoaded(side) || !level.getBlockState(side).isSolidRender(level, side)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 丢掉计划了还没执行的格子、通知客户端收尾（暂停、结束、下线时调）。这些格子没取过料、没扣过 MRU，不用结算；
     * 暂停的任务接着做时从当前这一格重新计划、重新发包。
     */
    private static void endFlights(Job job) {
        BuilderFlightPlan.Ring ring = job.ring;
        if (ring == null) {
            return;
        }
        ring.reset(job.next);
        if (job.flown != null) {
            job.flown.clear();
        }
        if (ring.sent) {
            ring.sent = false;
            BuilderFlightPlan.send(job.player.server.getLevel(job.dimension), job.player, job.box,
                    List.of(BuilderFlightPlan.endPacket(job.player.getId(), job.id, job.lead)));
        }
    }

    /** 撤销这一刻删成的格子发飞回的包（真方块已经删了，特效只是从格子飞回杖尖） */
    private static void sendFlewBack(Job job) {
        LongArrayList positions = job.flewBack;
        IntArrayList states = job.flewBackStates;
        job.flewBack = null;
        job.flewBackStates = null;
        if (positions == null || states == null || positions.isEmpty()) {
            return;
        }
        BuilderFlightPlan.send(job.player.server.getLevel(job.dimension), job.player, job.box,
                BuilderFlightPlan.pack(S2CBuilderFlightPacket.KIND_UNDO, job.player.getId(), job.id, job.lead, positions, states));
    }

    private static void pause(Job job, StopReason reason, long now) {
        endFlights(job);
        job.paused = true;
        job.pauseReason = reason;
        job.lastResumeCheck = now;
        if (job.pauseDeadline < 0) {
            job.pauseDeadline = now + ECBuilder.Config.materialPauseSeconds() * 20L;
        }
        int seconds = (int) Math.max(1, (job.pauseDeadline - now + 19) / 20);
        Component message = reason == StopReason.NO_MRU
                ? Component.translatable("message.essentialcraft5.builder.paused_mru", job.needed, seconds)
                : Component.translatable("message.essentialcraft5.builder.paused_material", materialName(job), seconds);
        MagicalBuilderItem.tellError(job.player, message);
    }

    /** 提示里「XX 用完了」的 XX：混铺写「混铺的方块」（几种都用完才会停），否则是那种材料 */
    private static Component materialName(Job job) {
        if (job.mix != null) {
            return Component.translatable("message.essentialcraft5.builder.mix_material");
        }
        return job.material == null ? Component.empty() : job.material.getDescription();
    }

    /** 这一种（{@link Job#items} 的下标）身上还有没有能取的 */
    private static boolean hasMaterial(Job job, int kind, long now) {
        return BuilderMaterials.find(job.player, job.items[kind], job.cursor, job.wand, now) != null;
    }

    /** 几种里哪一种身上还有（暂停的任务看补上没有） */
    private static boolean anyMaterial(Job job, long now) {
        for (int k = 0; k < job.items.length; k++) {
            if (hasMaterial(job, k, now)) {
                return true;
            }
        }
        return false;
    }

    private static boolean stillHolding(Job job) {
        ServerPlayer player = job.player;
        return !player.hasDisconnected() && !player.isRemoved() && player.isAlive() && !player.isSpectator()
                && player.getItemInHand(job.hand) == job.wand;
    }

    private static boolean stillValid(Job job) {
        ServerPlayer player = job.player;
        int reach = job.stats.maxReach();
        return stillHolding(job) && player.mayBuild() && player.level().dimension() == job.dimension
                && BuilderSelection.distanceSqToBox(player.position(), job.box) <= (double) reach * reach;
    }

    /**
     * 范围里的格子所在区块都已加载。长方体按外框查；沿面这种不规则的按格子所在区块查（测量只连已加载的格子，
     * 一片 L 形的外框角上可能压着没加载的区块，按外框查会把测好的一单白白拒掉）。
     */
    private static boolean chunksLoaded(Level level, BuilderArea area) {
        long[] cells = area.cells(true);
        if (cells == null) {
            return chunksLoaded(level, area.box());
        }
        LongOpenHashSet checked = new LongOpenHashSet();
        for (long cell : cells) {
            int cx = SectionPos.blockToSectionCoord(BlockPos.getX(cell));
            int cz = SectionPos.blockToSectionCoord(BlockPos.getZ(cell));
            if (checked.add(ChunkPos.asLong(cx, cz)) && level.getChunkSource().getChunkNow(cx, cz) == null) {
                return false;
            }
        }
        return true;
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

    private static Result result(Job job, StopReason reason) {
        String key = finishMessage(job, reason).getContents() instanceof TranslatableContents contents ? contents.getKey() : "";
        return new Result(reason, job.done, job.denied + job.probedDenied, job.skipped, job.peakEffects, job.probedDenied, job.unloaded, job.kind, key,
                job.brokenOnly);
    }

    /** 撤销任务没撤完的那部分压回栈顶（人已经下线的不压：下线时撤销栈已经清掉，压回去就是给下线的人重建了一条） */
    private static void settleUndo(Job job) {
        if (job.kind == Kind.UNDO && job.record != null && job.next < job.total && !job.player.hasDisconnected()) {
            BuilderUndo.putBack(job.owner(), job.record, job.total - job.next, job.stats.undoDepth());
        }
    }

    /** 任务结束：移出任务表、记下结果、在聊天栏告诉玩家做成了几格。同一个任务只结束一次，再调直接返回 */
    private static void finish(Job job, StopReason reason) {
        if (job.finished) {
            return;
        }
        job.finished = true;
        job.finishReason = reason;
        endFlights(job);
        UUID id = job.owner();
        if (JOBS.get(id) == job) {
            JOBS.remove(id);
        }
        WAITING.remove(job);
        settleUndo(job);
        RESULTS.put(id, result(job, reason));
        if (job.kind == Kind.BUILD && !job.mode.bottomUp() && job.done > 0) {
            LAST_CLEAR.put(id, now(job.player));
            // 栈顶那次放置之后有了一单清除：两条撤销路都不再撤它（clearedSince）
            BuilderUndo.Record top = BuilderUndo.peek(id);
            if (top != null) {
                top.clearedAfter = true;
            }
        } else if (job.done > 0) {
            // 最近一单不是清除（放置、撤销）：面板不再说「上一次是清除」
            LAST_CLEAR.remove(id);
        }
        Component message = finishMessage(job, reason);
        boolean good = reason == StopReason.FINISHED || reason == StopReason.STOPPED;
        job.player.displayClientMessage(message.copy().withStyle(good ? ChatFormatting.DARK_PURPLE : ChatFormatting.RED), false);
        if (job.brokenOnly > 0) {
            job.player.displayClientMessage(brokenOnlyMessage(job.brokenOnly).withStyle(ChatFormatting.GRAY), false);
        }
        if (job.unloaded > 0) {
            job.player.displayClientMessage(Component.translatable("message.essentialcraft5.builder.unloaded", job.unloaded)
                    .withStyle(ChatFormatting.GRAY), false);
        }
    }

    /** 全部替换里拆成了、放置被拒的格数那一句（结束提示后面另起一行；这些格不在「改动了 N 格」和「N 格没有权限，未改动」里） */
    public static MutableComponent brokenOnlyMessage(int count) {
        return Component.translatable("message.essentialcraft5.builder.broken_only", count);
    }

    /** 结束时聊天栏那一句：整单做完但有格子被拒时写上被拒格数；因连续被拒停下的换一句 */
    private static Component finishMessage(Job job, StopReason reason) {
        int deniedAll = job.denied + job.probedDenied;
        Component message;
        if (job.kind == Kind.UNDO && (reason == StopReason.FINISHED || reason == StopReason.STOPPED)) {
            message = Component.translatable("message.essentialcraft5.builder.undone", job.done, job.skipped);
        } else {
            message = switch (reason) {
                case FINISHED -> deniedAll > 0
                        ? (job.skipped > 0
                        ? Component.translatable("message.essentialcraft5.builder.filled_skipped_denied", job.done, job.skipped, deniedAll)
                        : Component.translatable("message.essentialcraft5.builder.filled_denied", job.done, deniedAll))
                        : job.skipped > 0
                        ? Component.translatable("message.essentialcraft5.builder.filled_skipped", job.done, job.skipped)
                        : Component.translatable("message.essentialcraft5.builder.filled", job.done);
                case STOPPED -> Component.translatable("message.essentialcraft5.builder.stopped", job.done);
                case OUT_OF_MATERIAL -> Component.translatable("message.essentialcraft5.builder.out_of_material", materialName(job), job.done);
                case NO_MRU -> Component.translatable("message.essentialcraft5.builder.no_mru", job.needed, job.done);
                case INTERRUPTED -> Component.translatable("message.essentialcraft5.builder.interrupted", job.stats.maxReach(), job.done);
                case DENIED -> job.stoppedByStreak
                        ? Component.translatable("message.essentialcraft5.builder.denied_streak", job.deniedStreak, job.done)
                        : Component.translatable("message.essentialcraft5.builder.denied", deniedAll, job.done);
            };
        }
        return message;
    }

    // ------------------------------------------------------------------
    // 每一格
    // ------------------------------------------------------------------

    private static Outcome process(Job job, ServerLevel level, BlockPos pos, List<ItemStack> drops, boolean effect, long now) {
        if (level.isOutsideBuildHeight(pos)) {
            return Outcome.SKIPPED;
        }
        if (!ECLevels.isFullyLoaded(level, pos)) {
            job.unloaded++;
            return Outcome.SKIPPED;
        }
        BlockState state = level.getBlockState(pos);
        BuilderMode.Action action = decide(job, level, pos, state);
        if (action == BuilderMode.Action.SKIP) {
            return Outcome.SKIPPED;
        }
        if (job.probeMode) {
            ECProtection.Verdict verdict = ECProtection.check(job.player, level, pos, job);
            if (verdict == ECProtection.Verdict.UNKNOWN && !probeGaveUp(job, now)) {
                return Outcome.WAIT;
            }
            job.probeWaitSince = -1;
            if (verdict == ECProtection.Verdict.DENY) {
                job.probedDenied++;
                return Outcome.ATTEMPTED;
            }
        }
        return switch (action) {
            case SKIP -> Outcome.SKIPPED;
            case PLACE -> place(job, level, pos, now);
            case BREAK -> breakMainPart(job, level, pos, state, drops, effect);
            case REPLACE -> replace(job, level, pos, state, drops, effect, now);
        };
    }

    /**
     * 探测预算不够（UNKNOWN）时要不要放弃探测：同一名玩家名下的机器每刻把个人探测预算用完时，魔杖每刻第一格就拿到 UNKNOWN，
     * 一直等下去任务不前进、不暂停、不结束，还占着全服名额。连续等满 {@value #PROBE_WAIT_LIMIT} 刻就关掉这个任务的先探后放、
     * 这一格起直接走真事件（真事件照样受领地保护，只是被拒的格子会真发事件）。返回 true = 放弃，这一格照常做。
     */
    private static boolean probeGaveUp(Job job, long now) {
        if (job.probeWaitSince < 0) {
            job.probeWaitSince = now;
            return false;
        }
        if (now - job.probeWaitSince < PROBE_WAIT_LIMIT) {
            return false;
        }
        job.probeMode = false;
        job.probeAbandoned = true;
        job.probeWaitSince = -1;
        return true;
    }

    /** 撤一格（倒着做：后放的先撤）。方块变了的不动不退（只读，不算预算）；删成了退 1 个记下的材料，删的掉落丢掉 */
    private static Outcome processUndo(Job job, ServerLevel level, List<ItemStack> drops, boolean effect, long now) {
        BuilderUndo.Record record = Objects.requireNonNull(job.record);
        int index = job.total - 1 - job.next;
        BlockPos pos = record.pos(index);
        if (level.isOutsideBuildHeight(pos)) {
            return Outcome.SKIPPED;
        }
        if (!ECLevels.isFullyLoaded(level, pos)) {
            job.unloaded++;
            return Outcome.SKIPPED;
        }
        BlockState state = level.getBlockState(pos);
        if (!BuilderUndo.unchanged(state, record.states[index])) {
            job.skipped++;
            return Outcome.SKIPPED;
        }
        ServerPlayer player = job.player;
        if (job.probeMode) {
            ECProtection.Verdict verdict = ECProtection.check(player, level, pos, job);
            if (verdict == ECProtection.Verdict.UNKNOWN && !probeGaveUp(job, now)) {
                return Outcome.WAIT;
            }
            job.probeWaitSince = -1;
            if (verdict == ECProtection.Verdict.DENY) {
                job.probedDenied++;
                return Outcome.ATTEMPTED;
            }
        }
        List<ItemStack> harvested = ECProtection.harvestGuarded(player, level, pos, ItemStack.EMPTY, true);
        if (harvested == null) {
            denied(job);
            return Outcome.ATTEMPTED;
        }
        // 发飞回包的撤销不放原版破坏效果：客户端画方块从格子飞回杖尖，碎屑叠在上面看着像拆了两遍；
        // 关了飞行画面的人由客户端收包时自己补放（BuilderFlightClient.undoFallback）
        breakEffect(job, level, pos, state, effect && job.flewBack == null);
        job.done++;
        job.deniedStreak = 0;
        if (job.flewBack != null && job.flewBackStates != null) {
            job.flewBack.add(pos.asLong());
            job.flewBackStates.add(Block.getId(state));
        }
        Item material = record.materials[index];
        if (material != null && !player.getAbilities().instabuild) {
            drops.add(new ItemStack(material));
        }
        return Outcome.ATTEMPTED;
    }

    private static BuilderMode.Action decide(Job job, BlockGetter level, BlockPos pos, BlockState state) {
        return job.mode.decide(classify(level, pos, state), job.matches(state));
    }

    /** 这一格算哪种情况（{@link BuilderMode.CellKind}）；配了 {@code max_hardness} 时更硬的也算受保护 */
    public static BuilderMode.CellKind classify(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return BuilderMode.CellKind.AIR;
        }
        if (state.liquid()) {
            return BuilderMode.CellKind.LIQUID;
        }
        float hardness = state.getDestroySpeed(level, pos);
        if (state.hasBlockEntity() || hardness < 0F || state.is(ECBuilder.BUILDER_BLACKLIST)) {
            return BuilderMode.CellKind.PROTECTED;
        }
        double maxHardness = ECBuilder.Config.maxHardness();
        if (maxHardness >= 0D && hardness > maxHardness) {
            return BuilderMode.CellKind.PROTECTED;
        }
        return BuilderMode.CellKind.OTHER;
    }

    /** 真放置 / 真拆被拒：计数，第一次被拒以后改成先探后放（放弃过的不再打开） */
    private static void denied(Job job) {
        job.denied++;
        refused(job);
    }

    /**
     * 真发的事件被拒（含全部替换里拆成了、放置被拒的那种）：开着先探后放时数连续被拒、并打开先探后放。
     * 连续计数只在先探后放下才有意义——这时探测已经放行、真做却被拒，就是只禁止放置这类领地，每格都会让领地插件提示一句；
     * 服主关掉 {@code probe_after_denied}、或等探测预算超时放弃了的任务不数，被拒的格子照旧跳过，只受累计上限管
     * （不然选区里连着 {@code denied_streak_limit} 格落在别人领地里就会误停）。
     */
    private static void refused(Job job) {
        if (ECBuilder.Config.probeAfterDenied() && !job.probeAbandoned) {
            job.deniedStreak++;
            job.probeMode = true;
        }
    }

    /** 做成了一格：暂停计时、连续被拒清零 */
    private static void succeeded(Job job) {
        job.done++;
        job.pauseDeadline = -1;
        job.deniedStreak = 0;
    }

    /** 一格选定用哪种：{@link Job#items} 的下标和放下去的状态；两个特殊值见 {@link #choose} */
    private record Choice(int kind, @Nullable BlockState state) {
    }

    /** 这格放不下（选中的那种放不下，或身上有的几种都放不下）：跳过 */
    private static final Choice CANNOT_PLACE = new Choice(-1, null);
    /** 几种材料身上都没有了：暂停 / 停「材料用完」 */
    private static final Choice NO_MATERIAL_LEFT = new Choice(-2, null);

    /**
     * 这一格用哪种。先按 {@link Job#kindAt} 选中的那种（和预览、确认一样）：它在这格放不下就跳过；放得下、身上有料就用它。
     * 混铺时选中的那种用完了，按表的顺序往后（绕回开头）找身上还有、在这格放得下的一种顶上；几种都没料才算用完（暂停或结束）。
     * 没混铺时只有一种，结果和以前一样：放不下跳过、没料用完。
     *
     * @param planner 第 k 种在这一格放下去的状态，放不下返回 null
     */
    private static Choice choose(Job job, BlockPos pos, long now, boolean creative, IntFunction<BlockState> planner) {
        int n = job.blocks.length;
        if (n == 0) {
            return CANNOT_PLACE;
        }
        int first = job.kindAt(pos);
        BlockState planned = planner.apply(first);
        if (planned == null) {
            return CANNOT_PLACE;
        }
        if (creative || hasMaterial(job, first, now)) {
            return new Choice(first, planned);
        }
        boolean any = false;
        for (int step = 1; step < n; step++) {
            int k = (first + step) % n;
            if (!hasMaterial(job, k, now)) {
                continue;
            }
            any = true;
            BlockState state = planner.apply(k);
            if (state != null) {
                return new Choice(k, state);
            }
        }
        return any ? CANNOT_PLACE : NO_MATERIAL_LEFT;
    }

    private static Outcome place(Job job, ServerLevel level, BlockPos pos, long now) {
        ServerPlayer player = job.player;
        boolean creative = player.getAbilities().instabuild;
        Choice choice = choose(job, pos, now, creative, k -> {
            BlockState placed = placementState(player, level, pos, job.blocks[k], job.items[k]);
            return placed != null && canStand(player, level, pos, placed) ? placed : null;
        });
        if (choice == NO_MATERIAL_LEFT) {
            return Outcome.OUT_OF_MATERIAL;
        }
        if (choice == CANNOT_PLACE) {
            job.skipped++;
            return Outcome.ATTEMPTED;
        }
        BlockState state = Objects.requireNonNull(choice.state());
        int cost = job.stats.placeCost();
        if (!MRUDrain.canAfford(player, job.wand, cost)) {
            job.needed = cost;
            return Outcome.NO_MRU;
        }
        PlaceResult result = placeWithMaterial(job, level, pos, state, creative, now, job.items[choice.kind()]);
        if (result == PlaceResult.PLACED) {
            MRUDrain.playerUseMRU(player, job.wand, cost);
            succeeded(job);
        } else if (result == PlaceResult.DENIED) {
            denied(job);
        } else {
            return Outcome.OUT_OF_MATERIAL;
        }
        return Outcome.ATTEMPTED;
    }

    /**
     * 清除类模式拆一格。这一格是分两半的方块里「附属的那一半」时改拆主体那一格：门、高花、大型蕨等的上半拆下半，伸出的活塞头拆底座。
     * 直接拆附属的那一半，原版会连带用 {@code destroyBlock(主体, true)} 把主体拆掉、掉落物撒在原地（不进背包，创造模式也掉），
     * 附属的那一半自己什么都不掉，却照收 250；原版玩家亲手挖时靠 {@code playerWillDestroy} 防这个，这里不走那条路。
     * 改拆主体：破坏事件按真正掉东西的那格发，另一半随后被形状更新删掉（它的掉落表是空的，不生成掉落物）。
     * 主体那格不在选区里也照拆（原版拆一半也会连带整个）；主体那格按这个模式不该拆时，这一格也不动。
     * 全部替换不走这里，碰到附属的那一半直接跳过（见 {@link #replace}）。
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
     * main 是不是 part 的主体：活塞头照 {@code PistonHeadBlock.isFittingBase}（普通头配普通活塞、黏性头配黏性活塞、伸出着、朝向相同）；
     * 分两半的方块要是同一种方块的下半。
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
        if (!MRUDrain.canAfford(player, job.wand, job.stats.breakCost())) {
            job.needed = job.stats.breakCost();
            return Outcome.NO_MRU;
        }
        List<ItemStack> harvested = ECProtection.harvestGuarded(player, level, pos, ItemStack.EMPTY, true);
        if (harvested == null) {
            denied(job);
            return Outcome.ATTEMPTED;   // 被拒、删除被取消：不动、不扣
        }
        breakEffect(job, level, pos, state, effect);
        MRUDrain.playerUseMRU(player, job.wand, job.stats.breakCost());
        succeeded(job);
        collect(job, drops, harvested);
        return Outcome.ATTEMPTED;
    }

    /**
     * 拆下的掉落收进本刻的掉落表（创造模式不给）。回收生效时丢弃清单里的直接不要，其余照收；本刻结束时先塞随身容器（{@link BuilderSalvage#stash}）。
     * 掉落只来自这一格真实破坏算出的那份，这里只筛、不加。
     */
    private static void collect(Job job, List<ItemStack> drops, List<ItemStack> harvested) {
        if (job.player.getAbilities().instabuild) {
            return;
        }
        if (job.salvage) {
            BuilderSalvage.noteRecent(job.owner(), harvested);
        }
        if (job.discard.isEmpty()) {
            drops.addAll(harvested);
            return;
        }
        for (ItemStack stack : harvested) {
            if (!job.discard.contains(stack.getItem())) {
                drops.add(stack);
            }
        }
    }

    private static Outcome replace(Job job, ServerLevel level, BlockPos pos, BlockState state, List<ItemStack> drops, boolean effect, long now) {
        ServerPlayer player = job.player;
        // 门、高花的上半，伸出的活塞头：拆这一半时原版会连带把主体拆掉、掉落撒在地上，主体那格也没以玩家身份发破坏事件。
        // 铺面 / 直线只有一层，压在上半那层时主体不在这一单里；活塞头横着伸出时从下往上的顺序也管不到。这一格跳过，不拆不放
        BlockPos main = mainPartCandidate(pos, state);
        if (main != null && !level.isOutsideBuildHeight(main)
                && (!ECLevels.isFullyLoaded(level, main) || isMainPart(state, level.getBlockState(main)))) {
            job.skipped++;
            return Outcome.SKIPPED;
        }
        // 新方块在这格放不下、立不住、被实体挡着的，连拆都不拆。放置状态按这一格现在的流体算（PrePlaceContext）：
        // 拆完这一格剩下的正好是这份流体，所以和拆完再算的结论一样——海带、海草在干的格子上放不了，就不拆；含水的方块换成海带照常换
        // 混铺时用哪种同 place：选中的那种用完了换下一种身上有、放得下的
        boolean creative = player.getAbilities().instabuild;
        Choice choice = choose(job, pos, now, creative, k -> {
            BlockState planned = job.blocks[k].getStateForPlacement(new PrePlaceContext(level, player, new ItemStack(job.items[k]), pos));
            return planned != null && canStand(player, level, pos, planned) ? planned : null;
        });
        if (choice == NO_MATERIAL_LEFT) {
            return Outcome.OUT_OF_MATERIAL;   // 原作先吃掉方块再看有没有材料（L461），没材料方块就白丢了
        }
        if (choice == CANNOT_PLACE) {
            job.skipped++;
            return Outcome.ATTEMPTED;
        }
        Block newBlock = job.blocks[choice.kind()];
        Item newItem = job.items[choice.kind()];
        if (!MRUDrain.canAfford(player, job.wand, job.stats.replaceCost())) {
            job.needed = job.stats.replaceCost();
            return Outcome.NO_MRU;
        }
        List<ItemStack> harvested = ECProtection.harvestGuarded(player, level, pos, ItemStack.EMPTY, true);
        if (harvested == null) {
            denied(job);
            return Outcome.ATTEMPTED;
        }
        breakEffect(job, level, pos, state, effect);
        collect(job, drops, harvested);
        boolean placed = false;
        boolean placeDenied = false;
        // 拆完照旧重算一次兜底（破坏事件的监听器可能动过周围）
        BlockState placing = placementState(player, level, pos, newBlock, newItem);
        if (placing != null && canStand(player, level, pos, placing)) {
            // 破坏事件的监听器可能动过背包，重新找一次材料
            PlaceResult result = placeWithMaterial(job, level, pos, placing, creative, now, newItem);
            placed = result == PlaceResult.PLACED;
            placeDenied = result == PlaceResult.DENIED;
        }
        // 这名玩家在这格本来就有破坏权：放置被拒时格子留成空气，按「拆」收费
        MRUDrain.playerUseMRU(player, job.wand, placed ? job.stats.replaceCost() : job.stats.breakCost());
        if (placeDenied) {
            // 拆成了、放没成（只禁止放置的领地）：既不算做成也不算「被拒未改动」，单独记「只拆未放」；
            // 连续被拒照数、不清零（不然这种领地里每格都清零，停不下来），暂停计时照常清
            job.brokenOnly++;
            job.pauseDeadline = -1;
            refused(job);
        } else {
            succeeded(job);
        }
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

    private enum PlaceResult {
        PLACED,
        DENIED,
        /** 那处材料取不出来（处理器拒绝或给错东西） */
        NO_MATERIAL
    }

    /** 找材料（material 这一种）、放一格；那处取不出来就换下一处再试（最多 4 处），都没有返回 NO_MATERIAL */
    private static PlaceResult placeWithMaterial(Job job, ServerLevel level, BlockPos pos, BlockState state, boolean creative, long now, Item material) {
        if (creative) {
            return placeOne(job, level, pos, state, null, true, material);
        }
        for (int attempt = 0; attempt < 4; attempt++) {
            BuilderMaterials.Location location = BuilderMaterials.find(job.player, material, job.cursor, job.wand, now);
            if (location == null) {
                return PlaceResult.NO_MATERIAL;
            }
            PlaceResult result = placeOne(job, level, pos, state, location, false, material);
            if (result != PlaceResult.NO_MATERIAL) {
                return result;
            }
        }
        return PlaceResult.NO_MATERIAL;
    }

    /**
     * 放一格：先从 location 取 1 个材料（创造模式不取），{@code placeGuarded} 放行就算用掉、记进撤销记录，被拒原样放回原处。
     * 先取后放：放置事件里有监听器动了背包也不会出现「放下了却没扣到材料」。取不出来的那处（处理器拒绝或给错东西）这个任务以后不再看，返回 NO_MATERIAL。
     */
    private static PlaceResult placeOne(Job job, ServerLevel level, BlockPos pos, BlockState state, @Nullable BuilderMaterials.Location location, boolean creative,
                                        Item material) {
        ServerPlayer player = job.player;
        ItemStack taken = ItemStack.EMPTY;
        if (!creative) {
            taken = BuilderMaterials.take(player, Objects.requireNonNull(location), material);
            if (taken.isEmpty()) {
                job.cursor.skip(location);
                return PlaceResult.NO_MATERIAL;
            }
        }
        // 报没拿魔杖的那只手：混合端按这只手报放置用的物品（不给手时按主手报，魔杖就成了「放下的东西」）；纯 Forge 不看手
        InteractionHand other = job.hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        boolean placed;
        HandSwap swap = HandSwap.show(player, other, material);
        try {
            placed = ECProtection.placeGuarded(player, level, pos, state, Direction.UP, other, Block.UPDATE_ALL);
        } finally {
            if (swap != null) {
                swap.restore();
            }
        }
        if (placed) {
            // 取出的那份算用掉：容器交出的若是自己格子里那叠的引用（处理器实现有问题时），这里清零才真扣到
            taken.setCount(0);
            if (job.flown == null || !job.flown.remove(pos.asLong())) {
                job.lastPlacedPos = pos;
                job.lastPlacedState = state;
            }
            if (job.record != null) {
                // 记实际放下的那一种：撤销按它 1 比 1 退（混铺时每格可能不同）
                job.record.add(pos, state, creative ? null : material);
                // 放置事件里被踢下线的：下线时撤销栈已经清掉，这里不再给下线的人建一条
                if (!job.recordPushed && !player.hasDisconnected()) {
                    job.recordPushed = true;
                    BuilderUndo.push(job.owner(), job.record, job.stats.undoDepth());
                }
            }
            return PlaceResult.PLACED;
        }
        if (!creative) {
            BuilderMaterials.giveBack(player, location, taken);
        }
        return PlaceResult.DENIED;
    }

    /**
     * 发放置事件的那一刻，把报给插件的那只手临时换成 1 个这次放下的材料，事件发完原样换回（{@code general.builder.show_material_in_hand}）。
     * 混合端按这只手报「放置用的物品」，材料从背包或随身容器来时那只手里多半是空的或别的东西，按手里物品判断的插件
     * （区块方块数限制、禁用物品）会看错：手里是空的就当不是玩家放的而放过，手里拿着别的就按那个判。换成材料后看到的和玩家亲手放一样。
     * <ul>
     *   <li>那只手里本来就是这种材料（材料从副手来的常见情形）不换。</li>
     *   <li>换的是一份副本，取出来的那 1 个（taken）不经手，被拒时照旧原样放回原处；事件里有人改了手里这份副本也不影响结算。</li>
     *   <li>换回按事先记下的格号写（主手是开工那一刻选中的快捷栏格），事件里切了快捷栏也不会写错格子；同一段同步代码里换、换回，
     *   中间不会有容器同步或装备变化事件。</li>
     *   <li>事件里玩家被踢下线或死掉时，下线存盘、死亡掉落之前先换回（{@link #onBuilderHandSwapLogout}、{@link #onBuilderHandSwapDeath}，
     *   最高优先级）：存进存档、掉在地上的是那只手原来的东西，不是临时那份。</li>
     * </ul>
     */
    private static final class HandSwap {
        /** 正在发事件的那一次（服务端单线程，同一时刻最多一个） */
        @Nullable
        private static HandSwap active;

        private final ServerPlayer player;
        private final boolean offhand;
        private final int slot;
        private final ItemStack saved;

        private HandSwap(ServerPlayer player, boolean offhand, int slot, ItemStack saved) {
            this.player = player;
            this.offhand = offhand;
            this.slot = slot;
            this.saved = saved;
        }

        @Nullable
        static HandSwap show(ServerPlayer player, InteractionHand hand, Item material) {
            if (!ECBuilder.Config.showMaterialInHand() || player.getItemInHand(hand).is(material)) {
                return null;
            }
            Inventory inventory = player.getInventory();
            boolean offhand = hand == InteractionHand.OFF_HAND;
            int slot = offhand ? 0 : inventory.selected;
            if (!offhand && !Inventory.isHotbarSlot(slot)) {
                return null;
            }
            if (active != null) {
                // 放置事件里又触发了一次放置（别的监听器调回来）：外层那次还没换回，这一次不再换
                return null;
            }
            NonNullList<ItemStack> list = offhand ? inventory.offhand : inventory.items;
            HandSwap swap = new HandSwap(player, offhand, slot, list.get(slot));
            list.set(slot, new ItemStack(material));
            active = swap;
            return swap;
        }

        /** 换回（重复调用只换一次） */
        void restore() {
            if (active != this) {
                return;
            }
            active = null;
            Inventory inventory = player.getInventory();
            (offhand ? inventory.offhand : inventory.items).set(slot, saved);
        }

        static void restoreFor(Object entity) {
            HandSwap swap = active;
            if (swap != null && swap.player == entity) {
                swap.restore();
            }
        }
    }

    /** 放置事件里被踢下线：下线存盘之前把临时换进手里的材料换回去（{@link HandSwap}） */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBuilderHandSwapLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        HandSwap.restoreFor(event.getEntity());
    }

    /** 放置事件里死掉：掉落之前换回（{@link HandSwap}） */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBuilderHandSwapDeath(LivingDeathEvent event) {
        HandSwap.restoreFor(event.getEntity());
    }

    /**
     * 这一格放下去的状态：按物品自己的放置规则、以玩家的视线算一格（点中的面当作这一格的顶面），不照抄选定时的状态——
     * 台阶放单层、海泡菜和蜡烛放 1 个、雪层放 1 层、作物从最小的长起、楼梯朝玩家面对的方向。格子放不了（不是可替换的）或者方块不让放返回 null。
     * 两端都能用（客户端虚影也照它算）。
     */
    @Nullable
    public static BlockState placementState(Player player, Level level, BlockPos pos, @Nullable Block block, @Nullable Item material) {
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
     * 会掉落的方块（沙子、砂砾、混凝土粉末）下面那格是空的也算立不住：放上去会变成下落实体，跳过。
     */
    public static boolean canStand(Player player, Level level, BlockPos pos, BlockState state) {
        if (!state.canSurvive(level, pos)) {
            return false;
        }
        if (state.getBlock() instanceof FallingBlock && FallingBlock.isFree(level.getBlockState(pos.below()))) {
            return false;
        }
        return clearOf(player, level, pos, state);
    }

    /** 放下去不会被实体挡住、也不会卡住玩家自己 */
    private static boolean clearOf(Player player, Level level, BlockPos pos, BlockState state) {
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

    /** 能当材料：就是那种物品、没有 NBT（改过名、装了东西的不碰） */
    static boolean isPlainMaterial(ItemStack stack, @Nullable Item material) {
        return BuilderMaterials.isPlainMaterial(stack, material);
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
