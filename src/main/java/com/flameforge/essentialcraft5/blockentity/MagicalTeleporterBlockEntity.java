package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.advancement.StructureFormedTrigger;
import com.flameforge.essentialcraft5.ambience.AmbienceParticles;
import com.flameforge.essentialcraft5.block.RedstonePulse;
import com.flameforge.essentialcraft5.client.render.SpellFx;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.BoundGemItem;
import com.flameforge.essentialcraft5.matrix.MatrixRank;
import com.flameforge.essentialcraft5.menu.MagicalTeleporterMenu;
import com.flameforge.essentialcraft5.mru.ECCorruption;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHoannaPortal;
import com.flameforge.essentialcraft5.registry.ECVoidMachines;
import com.flameforge.essentialcraft5.structure.StructureCache;
import com.flameforge.essentialcraft5.teleport.ECTeleport;
import com.flameforge.essentialcraft5.util.ECLevels;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntSupplier;

/**
 * 魔力传送器（第六批 6b M2，batch6b.md §4.2）。对应原作 TileMagicalTeleporter。
 * <p>
 * 玩家视角：搭好结构（{@link ECVoidMachines#TELEPORTER_STRUCTURE}），0 号格放一颗连着 MRU 源的绑定宝石供能，1 号格放一颗绑在目的地方块上的绑定宝石；
 * 站到机器上面约 12.5 秒（{@code ticks_required} 刻），被传到那个方块的顶上。跨维度要存满 MRU。
 *
 * <h2>每刻（只在服务端）</h2>
 * <ol>
 *   <li>谁算站上去：脚所在格 = 机器正上方那格的真玩家（不是假玩家、不是旁观、活着）；每刻只查一次 1×2×1 的实体。记住 UUID，<b>换人进度清零</b>。</li>
 *   <li>要先离开再站上来：传送成功后落在另一台传送器上、或这次失败了，这名玩家得走下来一次才重新算（挂机站着不能当区块加载器，两台互为落点不来回送）。
 *       「落在另一台上」按落地后脚下那格认（另一台顶上铺了地毯、台阶也算）；落点是另一台上方的活板门、梯子这类人会穿过去掉下来的东西时，
 *       靠到达记录兜底：被送走后 {@value #ARRIVAL_GRACE} 刻内踩上<b>别的</b>传送器，也要先走下来一次（{@link #RECENT_ARRIVALS}）。</li>
 *   <li>骑着东西或身上有乘客：不蓄能，提示「下来才能传送」。</li>
 *   <li>有红石：进度冻结（原作有红石时整段不跑，进度留着）。</li>
 *   <li>1 号格宝石有目标、结构成形、跨维度时允许跨维度且目标维度存在、海纳尔开关（目的地是海纳尔、出发点不在海纳尔、开关关着就拒绝，同 6a）——任一不满足进度清零。</li>
 *   <li>开始蓄能前（进度为 0 时）：站上去的人要能动<b>传送器自己那格</b>（{@link ECProtection#check} 三态，UNKNOWN 下一刻再试）——
 *       野外人人能用，放在领地里的只有有权限的人能用，免得谁都能站上去花机器主人的 MRU（policy §1.1 第 3 条）。</li>
 *   <li>MRU 不够费用：进度冻结（照原作，不清零）。</li>
 *   <li>进度 +1；目的地区块没完全加载就加一张 {@code PORTAL} 区域票（半径 1、300 刻自动过期、不存盘）；这次蓄能加过票以后，
 *       每 {@value #TICKET_REFRESH} 刻续一次（同一张票再加一次会刷新它的起算时刻），蓄能或等待一停就不再续，最多 300 刻后自动过期；
 *       目的地一直被别人加载着、这次又没加过票时不加。半径 1 时票等级 32，目的地那一区块完整加载，落点检查用到的格子都在里面；
 *       落点在世界边界外不加（免得为传不过去的地方加载甚至生成区块）。</li>
 *   <li>到点：先立即复查一次结构（每趟多扫 41 格；平时的缓存每 20 刻才复查，到点前不到 1 秒拆掉一块也能传），不成形就清零、不要求先走下来；
 *       再 {@link ECTeleport#check}（落点安全 + 落脚格、脚下格的三态权限）。区块还没加载 / 权限暂时给不出结论就最多再等 {@value #MAX_WAIT} 刻，
 *       超时算失败；不合格就不传、不扣；合格就 {@link ECTeleport#teleport}，传成以后清摔落距离、扣 MRU、进度清零、按配置漏腐化。</li>
 * </ol>
 * 费用（{@link #cost}）：跨维度 = 容量（要存满）；同维度 = {@code (int)(mru_usage × (|dx|+|dy|+|dz|) / 3)}，封顶容量，距离按传送器到宝石坐标（原作 L151~166）。
 *
 * <h2>和原作不同</h2>
 * <ul>
 *   <li>原作宝石有 NBT 没有 pos（铁砧改过名）时 {@code tpCoords[0]} 越界，方块实体 tick 崩；这里 {@link BoundGemItem#getTarget} 缺键返回 null，状态「1 号格没有绑好的宝石」。</li>
 *   <li>原作落点不查权限、不查安全（卡进方块窒息、掉进虚空或岩浆）、谁站上去都花主人的 MRU；这里见上面的规则（改玩法）。</li>
 *   <li>原作跨维度在主线程同步初始化维度、加载生成区块（按 1.12 行为推断）；这里临时票 + 等待，超时失败，绝不同步加载。</li>
 *   <li>原作进度不认人（A 站 200 刻离开、B 再站 50 刻就被传走）、两台互为落点时站着不动 12.5 秒会被送回去、骑乘 / 旁观 / 假玩家都能传（改玩法）。</li>
 *   <li>原作关掉跨维度时照样把人传到本维度的同坐标；这里直接拒绝。服主关了海纳尔之门也能传过去 → 照开关拒绝（改玩法）。</li>
 *   <li>原作两端都跑传送逻辑、每刻 40 格结构判定 × 2 端、满进度每刻约 300 个粒子、同一刻放 40 次烟花声、每刻新起一个矿车声；
 *       这里只在服务端、结构走 {@link StructureCache}、粒子每刻 ≤ {@value #MAX_PARTICLES} 个且离镜头 32 格以外不放、出发点到达点各一声、矿车声每 10 刻一次。</li>
 *   <li>原作漏斗能往 1 号格塞任何东西；这里两格都只收绑定宝石，漏斗只能抽 1 号格（原作的输出格）。</li>
 * </ul>
 * 混合端：同维度和跨维度两条路径上混合端发的 {@code PlayerTeleportEvent} 原因都是 UNKNOWN，按原因拦截的插件看不到（见 {@link ECTeleport} 类注释，batch6b.md §5.3）。
 * <p>
 * 客户端：只在开始 / 停止蓄能、结构成形与否翻转时收一次方块更新（更新包里只有蓄能开始的时刻和成形标记，<b>不带物品栏</b>——1 号格宝石的坐标不发给附近的人），
 * 进度自己按时间算，粒子见 {@link #clientTick}。
 */
public class MagicalTeleporterBlockEntity extends MRUBlockEntity {

    /** 服务端配置 machines.magical_teleporter（原作 tileentities.magicalteleporter，默认值照原作） */
    public static final class Config {
        public static ForgeConfigSpec.IntValue maxMRU;
        public static ForgeConfigSpec.IntValue mruUsage;
        public static ForgeConfigSpec.IntValue ticksRequired;
        public static ForgeConfigSpec.BooleanValue allowDimensional;
        public static ForgeConfigSpec.BooleanValue generateCorruption;
        public static ForgeConfigSpec.IntValue maxCorruptionGen;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("magical_teleporter");
            maxMRU = builder.comment("MRU capacity; a teleport to another dimension costs a full tank.", "MRU 容量；跨维度传送一次要存满")
                    .defineInRange("max_mru", ECConfig.DEVICE_MAX_MRU * 10, 1, Integer.MAX_VALUE);
            mruUsage = builder.comment("Same-dimension cost: this × (|dx| + |dy| + |dz|) / 3, capped at max_mru.",
                            "同维度一次的费用：这个数 × 三轴距离之和 / 3，封顶 max_mru")
                    .defineInRange("mru_usage", 500, 0, Integer.MAX_VALUE);
            ticksRequired = builder.comment("Ticks a player has to stand on the teleporter.", "要在传送器上站多少刻")
                    .defineInRange("ticks_required", 250, 1, 72_000);
            allowDimensional = builder.comment("Allow teleporting to another dimension.", "允许跨维度传送")
                    .define("allow_dimensional", true);
            generateCorruption = builder.comment("Leak MRU corruption around the teleporter on each teleport.", "每传一次在传送器周围漏一点 MRU 腐化")
                    .define("generate_corruption", false);
            maxCorruptionGen = builder.comment("Upper bound of the corruption leaked per teleport (the original comment said per tick, but it ran once per teleport).",
                            "每传一次最多漏多少腐化（原作注释写「每刻」，实际每传一次算一次）")
                    .defineInRange("max_corruption_gen", 5, 0, 10_000);
            builder.pop();
        }
    }

    /** 界面状态（按序号同步，只往后加） */
    public enum Status {
        /** 一切就绪，没人站着 */
        READY(0xFFFFFF),
        /** 1 号格没有宝石、宝石没绑定 */
        NO_DESTINATION(0xFF0000),
        INCOMPLETE(0xFF0000),
        /** 有红石信号：进度冻结 */
        POWERED(0xFFFF00),
        /** 目的地在别的维度，但不许跨维度 */
        DIMENSION_DISABLED(0xFF0000),
        /** 宝石记的维度不存在（数据包删掉了） */
        NO_DIMENSION(0xFF0000),
        /** 海纳尔之门关着，从外面传不进海纳尔 */
        HOANNA_CLOSED(0xFF0000),
        /** MRU 不够这一次的费用：进度冻结 */
        NO_MRU(0xFFFF00),
        RIDING(0xFFFF00),
        /** 要先走下来再站上去 */
        STEP_OFF(0xFFFF00),
        /** 站上去的人不能动传送器这一格（放在别人领地里） */
        NO_ACCESS(0xFF0000),
        CHARGING(0x00FF00),
        /** 到点了，在等目的地加载或权限结论 */
        WAITING(0xFFFF00),
        BLOCKED(0xFF0000),
        NO_FLOOR(0xFF0000),
        HAZARD(0xFF0000),
        DENIED(0xFF0000),
        OUT_OF_WORLD(0xFF0000),
        /** 等了 {@value #MAX_WAIT} 刻目的地还没加载 / 权限还没结论 */
        TIMED_OUT(0xFF0000),
        /** 传送被别的模组、插件取消了（或玩家此刻不能换维度） */
        CANCELLED(0xFF0000),
        /** 站着的人矩阵阶不够，从外面传不进海纳尔（第十三批 13a 的门，{@code MatrixRank.mayEnterHoanna}） */
        RANK_TOO_LOW(0xFF0000);

        private static final Status[] VALUES = values();

        public final int color;

        Status(int color) {
            this.color = color;
        }

        public MutableComponent label() {
            return Component.translatable("gui.essentialcraft5.magical_teleporter." + name().toLowerCase(Locale.ROOT));
        }

        public static Status byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : READY;
        }

        /** 落点检查不合格时对应的状态（OK / UNLOADED / PENDING 不是失败，返回 null） */
        @Nullable
        public static Status of(ECTeleport.Result result) {
            return switch (result) {
                case BLOCKED -> BLOCKED;
                case NO_FLOOR -> NO_FLOOR;
                case HAZARD -> HAZARD;
                case DENIED -> DENIED;
                case OUT_OF_WORLD -> OUT_OF_WORLD;
                default -> null;
            };
        }
    }

    public static final int SLOT_SOURCE = 0;
    public static final int SLOT_DESTINATION = 1;
    public static final int SLOTS = 2;

    /** 目的地临时票的半径（6a 海纳尔之门要找门建门用 3，这里落点固定在一格，1 就够） */
    public static final int TICKET_RADIUS = 1;
    /** 两次补票至少隔几刻（票自己 300 刻过期） */
    public static final int TICKET_REFRESH = 200;
    /** 到点后最多再等几刻 */
    public static final int MAX_WAIT = 100;
    /** 被送走后这么多刻内踩上别的传送器，也要先走下来一次（见 {@link #RECENT_ARRIVALS}） */
    public static final int ARRIVAL_GRACE = 100;
    /** 到达记录超过这么多条时顺手清掉过期的 */
    private static final int ARRIVAL_TABLE_LIMIT = 256;
    /** 界面落点预览多久算一次 */
    public static final int PREVIEW_INTERVAL = 20;
    /** 蓄能声每几刻一次 */
    public static final int SOUND_INTERVAL = 10;
    /** 蓄能粒子每刻最多几个 */
    public static final int MAX_PARTICLES = 12;
    /** 离镜头超过这么远不放粒子 */
    public static final double PARTICLE_RANGE = 32D;

    private static final String TAG_FORMED = "formed";
    private static final String TAG_CHARGE_START = "charge_start";
    private static final DustParticleOptions MAGENTA_DUST = new DustParticleOptions(new Vector3f(1F, 0F, 1F), 1F);

    private final StructureCache structure = new StructureCache(ECVoidMachines.TELEPORTER_STRUCTURE);

    // ---- 服务端状态（都不存盘：区块卸载 / 重进世界时站着的人本来就没了） ----
    private int progress;
    @Nullable
    private UUID occupant;
    /** 要先走下来一次的玩家（只留还站在上面的） */
    private final Set<UUID> mustLeave = new HashSet<>();
    /** 这名玩家上一次失败的原因（他走下来之前界面一直显示它） */
    @Nullable
    private Status lastFailure;
    /** 已经给站着的人提示过的状态（同一状态只提示一次） */
    @Nullable
    private Status hinted;
    private long lastTicket = Long.MIN_VALUE;
    /** 这次蓄能加过临时票（加过就一直续到蓄能停下；resetProgress 清掉） */
    private boolean ticketThisCharge;
    private int waited;
    private Status status = Status.NO_DESTINATION;
    /** 界面落点预览：0 = 不知道，否则 Result 序号 + 1 */
    private int preview;
    private long nextPreview;
    private int cost;

    // 1 号格宝石目标的解析缓存（宝石或它的 NBT 换了才重新解析）
    private ItemStack cachedGem = ItemStack.EMPTY;
    @Nullable
    private CompoundTag cachedGemTag;
    @Nullable
    private GlobalPos cachedTarget;

    // ---- 发给客户端的（服务端也存一份，变了才发） ----
    private boolean formed;
    /** 这次蓄能从哪一刻开始算（游戏时间）；没在蓄能为 -1。客户端按它算进度画粒子 */
    private long chargeStart = -1L;

    // ---- 只给 GameTest 的这一台机器自己的覆盖（null = 用配置），同批别的测试不受影响 ----
    @Nullable
    private Integer testMaxMRU;
    @Nullable
    private Integer testMruUsage;
    @Nullable
    private Integer testTicks;
    @Nullable
    private Boolean testAllowDimensional;
    private boolean testNoPreload;

    public MagicalTeleporterBlockEntity(BlockPos pos, BlockState state) {
        this(pos, state, new Capacity());
    }

    private MagicalTeleporterBlockEntity(BlockPos pos, BlockState state, Capacity capacity) {
        super(ECVoidMachines.MAGICAL_TELEPORTER_BE.get(), pos, state, capacity, SLOTS);
        capacity.owner = this;
    }

    /** MRU 容量（MRUStorage 每次现取）：调父类构造器时还不能引用 this，先建好再把自己挂上去，测试覆盖才能按台生效 */
    private static final class Capacity implements IntSupplier {
        @Nullable
        private MagicalTeleporterBlockEntity owner;

        @Override
        public int getAsInt() {
            return owner != null ? owner.maxMRU() : ECConfig.get(Config.maxMRU);
        }
    }

    // ------------------------------------------------------------------
    // 配置与覆盖
    // ------------------------------------------------------------------

    public int maxMRU() {
        return testMaxMRU != null ? testMaxMRU : ECConfig.get(Config.maxMRU);
    }

    public int mruUsage() {
        return testMruUsage != null ? testMruUsage : ECConfig.get(Config.mruUsage);
    }

    public int ticksRequired() {
        return testTicks != null ? testTicks : ECConfig.get(Config.ticksRequired);
    }

    public boolean allowDimensional() {
        return testAllowDimensional != null ? testAllowDimensional : ECConfig.get(Config.allowDimensional);
    }

    /**
     * 只给 GameTest：这一台的容量、同维度单价、蓄能刻数、允不允许跨维度（null = 用配置），以及关掉目的地预载（测「区块没加载等到超时」）。
     * 只改这一台，不碰配置文件（服务端配置自动存盘，文件监视器会冲掉测试里设的值）。
     */
    public void setTestOverrides(@Nullable Integer maxMRU, @Nullable Integer mruUsage, @Nullable Integer ticks,
                                 @Nullable Boolean allowDimensional, boolean noPreload) {
        testMaxMRU = maxMRU;
        testMruUsage = mruUsage;
        testTicks = ticks;
        testAllowDimensional = allowDimensional;
        testNoPreload = noPreload;
    }

    // ------------------------------------------------------------------
    // 槽位
    // ------------------------------------------------------------------

    /** 槽位规则（两端共用）：两格都只放绑定宝石（原作 0 号是 MRU 源、1 号是落点） */
    public static boolean slotRule(int slot, ItemStack stack) {
        return stack.getItem() instanceof BoundGemItem;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slotRule(slot, stack);
    }

    /** 漏斗只能抽 1 号格（原作 getOutputSlots = {1}） */
    @Override
    protected boolean isOutputSlot(int slot) {
        return slot == SLOT_DESTINATION;
    }

    /** 渲染用不到物品栏，而且 1 号格宝石记着目的地坐标，不发给附近的人 */
    @Override
    protected boolean syncInventoryToClient() {
        return false;
    }

    @Override
    protected boolean syncBalanceToClient() {
        return false;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new MagicalTeleporterMenu(id, inventory, this);
    }

    // ------------------------------------------------------------------
    // 费用
    // ------------------------------------------------------------------

    /**
     * 一次传送的费用（原作 {@code getTPCost} L151~166）：跨维度 = max；同维度 = {@code (int)(usage × (|dx|+|dy|+|dz|) / 3)}，封顶 max。纯函数。
     */
    public static int cost(BlockPos from, BlockPos to, boolean crossDimension, int usage, int max) {
        if (crossDimension) {
            return max;
        }
        long sum = Math.abs((long) from.getX() - to.getX()) + Math.abs((long) from.getY() - to.getY()) + Math.abs((long) from.getZ() - to.getZ());
        double value = usage * (sum / 3D);
        return value >= max ? max : (int) value;
    }

    /** 按 1 号格宝石算这一台现在的费用；宝石没绑定时 0 */
    public int cost() {
        GlobalPos target = target();
        if (target == null || level == null) {
            return 0;
        }
        return cost(worldPosition, target.pos(), target.dimension() != level.dimension(), mruUsage(), maxMRU());
    }

    // ------------------------------------------------------------------
    // tick
    // ------------------------------------------------------------------

    @Override
    protected void serverTick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        long now = server.getGameTime();
        updateStructure(server);
        GlobalPos target = target();
        ServerLevel dest = target == null ? null : server.getServer().getLevel(target.dimension());
        cost = target == null ? 0 : cost(worldPosition, target.pos(), target.dimension() != server.dimension(), mruUsage(), maxMRU());
        if (now >= nextPreview) {
            nextPreview = now + PREVIEW_INTERVAL;
            preview = target == null || dest == null ? 0 : ECTeleport.check(null, dest, target.pos(), null).result().ordinal() + 1;
        }

        List<ServerPlayer> present = playersOnTop(server);
        mustLeave.removeIf(id -> present.stream().noneMatch(p -> p.getUUID().equals(id)));
        ServerPlayer player = pickOccupant(present);
        if (player == null) {
            occupant = null;
            lastFailure = null;
            hinted = null;
            resetProgress();
            Status problem = readiness(null, server, target, dest);
            status = problem != null ? problem
                    : RedstonePulse.isPowered(server, worldPosition) ? Status.POWERED
                    : mru.getMRU() < cost ? Status.NO_MRU : Status.READY;
            return;
        }
        if (!player.getUUID().equals(occupant)) {
            occupant = player.getUUID();
            lastFailure = null;
            hinted = null;
            resetProgress();
        }
        if (justSentHere(server, occupant, now)) {
            mustLeave.add(occupant);
        }
        if (mustLeave.contains(occupant)) {
            resetProgress();
            status = lastFailure != null ? lastFailure : Status.STEP_OFF;
            return;
        }
        if (player.isPassenger() || player.isVehicle()) {
            resetProgress();
            setStatus(player, Status.RIDING);
            return;
        }
        if (RedstonePulse.isPowered(server, worldPosition)) {
            pauseCharge();
            setStatus(player, Status.POWERED);
            return;
        }
        Status problem = readiness(player, server, target, dest);
        if (problem != null) {
            resetProgress();
            setStatus(player, problem);
            return;
        }
        if (progress == 0) {
            ECProtection.Verdict access = ECProtection.check(player, server, worldPosition, this);
            if (access == ECProtection.Verdict.DENY) {
                setStatus(player, Status.NO_ACCESS);
                return;
            }
            if (access == ECProtection.Verdict.UNKNOWN) {
                setStatus(player, Status.WAITING);
                return;
            }
        }
        if (mru.getMRU() < cost) {
            pauseCharge();
            setStatus(player, Status.NO_MRU);
            return;
        }
        // readiness 已保证 target、dest 都不是 null
        BlockPos floor = target.pos();
        int ticks = ticksRequired();
        if (progress < ticks) {
            progress++;
            if (chargeStart < 0L) {
                chargeStart = now - progress;
                markClientDirty();
            }
            status = Status.CHARGING;
            hinted = null;
            preload(dest, floor, now);
            if (progress == 1 || progress % SOUND_INTERVAL == 0) {
                server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.MINECART_RIDING, SoundSource.BLOCKS,
                        0.1F, 0.8F + (float) progress / ticks);
            }
            if (progress < ticks) {
                return;
            }
        }
        arrive(server, player, dest, floor, now);
    }

    /** 到点：复查结构 → 落点检查 → 传送 → 扣费；区块没加载 / 权限没结论时等，超时失败 */
    private void arrive(ServerLevel server, ServerPlayer player, ServerLevel dest, BlockPos floor, long now) {
        if (waited == 0) {
            // 第一次到点时不管节流立即复查（checkNow 给不出结论时 formed() 沿用上次的结论）；不成形和平时一样清零，不要求先走下来
            structure.checkNow(server, worldPosition, Direction.NORTH);
            if (!structure.formed()) {
                resetProgress();
                setStatus(player, Status.INCOMPLETE);
                return;
            }
        }
        preload(dest, floor, now);
        ECTeleport.Check check = ECTeleport.check(player, dest, floor, this);
        if (check.result() == ECTeleport.Result.UNLOADED || check.result() == ECTeleport.Result.PENDING) {
            if (++waited > MAX_WAIT) {
                fail(player, Status.TIMED_OUT);
            } else {
                status = Status.WAITING;
            }
            return;
        }
        Status failure = Status.of(check.result());
        if (failure != null || check.landing() == null) {
            fail(player, failure != null ? failure : Status.CANCELLED);
            return;
        }
        Vec3 landing = check.landing();
        Vec3 departure = player.position();
        UUID id = player.getUUID();
        if (!ECTeleport.teleport(player, dest, landing, player.getYRot(), player.getXRot())) {
            fail(player, Status.CANCELLED);
            return;
        }
        player.resetFallDistance();
        mru.extractMRU(cost, true);
        if (ECConfig.get(Config.generateCorruption)) {
            ECCorruption.randomIncreaseAt(server, worldPosition, server.random, ECConfig.get(Config.maxCorruptionGen));
        }
        RandomSource random = server.random;
        server.playSound(null, departure.x, departure.y, departure.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR, SoundSource.BLOCKS,
                1F, 0.5F + random.nextFloat() * 0.5F);
        dest.playSound(null, landing.x, landing.y, landing.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.BLOCKS,
                1F, 0.5F + random.nextFloat() * 0.5F);
        // 人还留在这台上（落点就是它自己、或者别的模组把人留下了）也要先走下来，免得站着不动一直传
        mustLeave.add(id);
        // 按落地后脚下那格找另一台：落点方块就是它、或者它顶上铺了地毯 / 台阶（结构在它正上方那格不管放什么）
        BlockPos stand = BlockPos.containing(landing).below();
        if (ECLevels.loadedBlockEntity(dest, stand) instanceof MagicalTeleporterBlockEntity other && other != this) {
            other.requireStepOff(id);
        }
        rememberArrival(id, GlobalPos.of(server.dimension(), worldPosition), now);
        lastFailure = null;
        resetProgress();
        status = Status.READY;
    }

    /** 这次没传成：不扣 MRU，要先走下来，界面和动作栏写原因 */
    private void fail(ServerPlayer player, Status failure) {
        mustLeave.add(player.getUUID());
        lastFailure = failure;
        resetProgress();
        setStatus(player, failure);
    }

    /**
     * 这台现在差什么（和站着的人有关的那几条传 player，没人时传 null）；都满足返回 null。
     * 顺序：宝石 → 结构 → 跨维度开关 → 目标维度存在 → 玩家能不能换维度 → 海纳尔开关 → 矩阵阶（进海纳尔要几阶）。
     */
    @Nullable
    private Status readiness(@Nullable ServerPlayer player, ServerLevel server, @Nullable GlobalPos target, @Nullable ServerLevel dest) {
        if (target == null) {
            return Status.NO_DESTINATION;
        }
        if (!structure.formed()) {
            return Status.INCOMPLETE;
        }
        boolean cross = target.dimension() != server.dimension();
        if (cross && !allowDimensional()) {
            return Status.DIMENSION_DISABLED;
        }
        if (dest == null) {
            return Status.NO_DIMENSION;
        }
        if (cross && player != null && !player.canChangeDimensions()) {
            return Status.CANCELLED;
        }
        // 开关只挡从外面进海纳尔，从海纳尔往外传照常（同 6a 海纳尔之门）
        if (ECHoanna.isHoanna(dest) && !ECHoanna.isHoanna(server) && !ECHoannaPortal.Config.portalEnabled()) {
            return Status.HOANNA_CLOSED;
        }
        // 矩阵阶的门同样只挡从外面进海纳尔，只看站着的那个人（没人站时不判）
        if (player != null && ECHoanna.isHoanna(dest) && !ECHoanna.isHoanna(server) && !MatrixRank.mayEnterHoanna(player)) {
            return Status.RANK_TOO_LOW;
        }
        return null;
    }

    /**
     * 按现在的宝石、结构和开关判断这一台差什么（不动进度、不发提示），都满足返回 {@link Status#READY}。
     * 给测试在同一段同步代码里检查全局开关（{@code ECHoannaPortal.Config.setEnabledForTest}）用。
     */
    public Status checkTarget(@Nullable ServerPlayer player) {
        if (!(level instanceof ServerLevel server)) {
            return Status.READY;
        }
        GlobalPos target = target();
        Status problem = readiness(player, server, target, target == null ? null : server.getServer().getLevel(target.dimension()));
        return problem != null ? problem : Status.READY;
    }

    private void updateStructure(ServerLevel server) {
        boolean was = structure.formed();
        structure.check(server, worldPosition, Direction.NORTH);
        boolean now = structure.formed();
        if (!was && now) {
            StructureFormedTrigger.triggerNearby(server, worldPosition, ECVoidMachines.TELEPORTER_STRUCTURE_ID, 0);
        }
        if (formed != now) {
            formed = now;
            markClientDirty();
        }
    }

    /** 站在上面的真玩家：脚所在格就是机器正上方那格，不是旁观、活着（每刻一次 1×2×1 的实体查询） */
    private List<ServerPlayer> playersOnTop(ServerLevel server) {
        BlockPos above = worldPosition.above();
        List<ServerPlayer> candidates;
        ServerPlayer seated = TEST_OCCUPANTS.isEmpty() ? null : TEST_OCCUPANTS.get(GlobalPos.of(server.dimension(), worldPosition));
        if (seated != null) {
            candidates = List.of(seated);
        } else {
            candidates = server.getEntitiesOfClass(ServerPlayer.class, new AABB(above).expandTowards(0D, 1D, 0D));
        }
        List<ServerPlayer> result = new ArrayList<>(candidates.size());
        for (ServerPlayer player : candidates) {
            if (ECProtection.isRealPlayer(player) && player.level() == server && player.isAlive() && !player.isSpectator()
                    && player.blockPosition().equals(above)) {
                result.add(player);
            }
        }
        return result;
    }

    /** 还站着的上一位优先，否则取第一个 */
    @Nullable
    private ServerPlayer pickOccupant(List<ServerPlayer> present) {
        if (present.isEmpty()) {
            return null;
        }
        for (ServerPlayer player : present) {
            if (player.getUUID().equals(occupant)) {
                return player;
            }
        }
        return present.get(0);
    }

    /**
     * 给目的地加 / 续临时票（只在蓄能和到点等待时调）。目的地没加载就加；这次蓄能加过票以后每 {@value #TICKET_REFRESH} 刻续一次，
     * 不管现在加没加载（票 300 刻过期，不续的话蓄能久了会卸载、下一刻再加载，来回存盘读盘）；没加过票、目的地又一直被别人加载着时不加。
     */
    private void preload(ServerLevel dest, BlockPos floor, long now) {
        if (testNoPreload || !dest.isInWorldBounds(floor) || !dest.getWorldBorder().isWithinBounds(floor)) {
            return;
        }
        if (ECLevels.isFullyLoaded(dest, floor) && !ticketThisCharge) {
            return;
        }
        if (lastTicket != Long.MIN_VALUE && now - lastTicket >= 0 && now - lastTicket < TICKET_REFRESH) {
            return;
        }
        // 同一张票（类型、等级、坐标都一样）再加一次只刷新起算时刻，就是续期
        dest.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(floor), TICKET_RADIUS, floor);
        lastTicket = now;
        ticketThisCharge = true;
    }

    private void resetProgress() {
        progress = 0;
        waited = 0;
        ticketThisCharge = false;
        pauseCharge();
    }

    /** 停止客户端的蓄能动画（进度本身不动：冻结时用） */
    private void pauseCharge() {
        if (chargeStart >= 0L) {
            chargeStart = -1L;
            markClientDirty();
        }
    }

    /**
     * 写状态；站着的人第一次碰到这种「停住了」的状态时在动作栏提示一次。
     * 不提示的：蓄能中、就绪、要先走下来（刚被另一台送过来的人一落地就看到这句太吵，界面上看得到）、等待（一两刻的事）。
     */
    private void setStatus(ServerPlayer player, Status next) {
        status = next;
        if (next != Status.CHARGING && next != Status.READY && next != Status.STEP_OFF && next != Status.WAITING && hinted != next) {
            hinted = next;
            player.displayClientMessage(next == Status.RANK_TOO_LOW ? MatrixRank.gateMessage(player, MatrixRank.Gate.HOANNA_PORTAL)
                    : next.label().withStyle(style -> style.withColor(next.color)), true);
        }
    }

    /** 1 号格宝石的目标（缓存解析结果） */
    @Nullable
    private GlobalPos target() {
        ItemStack gem = inventory.getStackInSlot(SLOT_DESTINATION);
        if (!(gem.getItem() instanceof BoundGemItem)) {
            cachedGem = ItemStack.EMPTY;
            cachedGemTag = null;
            cachedTarget = null;
            return null;
        }
        if (gem != cachedGem || gem.getTag() != cachedGemTag) {
            cachedGem = gem;
            cachedGemTag = gem.getTag();
            cachedTarget = BoundGemItem.getTarget(gem);
        }
        return cachedTarget;
    }

    /** 别的传送器把这名玩家送到了这台上面：他得先走下来一次 */
    public void requireStepOff(UUID player) {
        mustLeave.add(player);
    }

    // ------------------------------------------------------------------
    // 到达记录
    // ------------------------------------------------------------------

    /** 一次成功的传送：什么时候（游戏时间，各维度共用主世界的时间）、从哪台传送器送走的 */
    private record Arrival(long time, GlobalPos from) {
    }

    /**
     * 刚被传送器送走的玩家（只在内存里，只在服务端主线程读写）。落点是另一台传送器上方的活板门、梯子（面板贴边，人站在列正中碰不到）、
     * 或者水里时，人落地那一刻已经不在落点上，按脚下那格找不到那一台；那一台每刻又会把不在顶上的人从「要先走下来」里清掉，没法提前标记。
     * 所以记下来：{@value #ARRIVAL_GRACE} 刻内踩上<b>别的</b>传送器就算一次「要先走下来」，用掉这条记录。
     * 送他走的那一台不算（它自己已经要求过一次，人离开它就算走下来了，走回来照常用）。
     * 用蜘蛛网柱这类慢降装置故意拖过时限仍然绕得过，但每趟都要扣 MRU、要等满蓄能，不再加码。
     */
    private static final Map<UUID, Arrival> RECENT_ARRIVALS = new HashMap<>();

    private static void rememberArrival(UUID player, GlobalPos from, long now) {
        if (RECENT_ARRIVALS.size() >= ARRIVAL_TABLE_LIMIT) {
            RECENT_ARRIVALS.values().removeIf(arrival -> now - arrival.time() > ARRIVAL_GRACE || now < arrival.time());
        }
        RECENT_ARRIVALS.put(player, new Arrival(now, from));
    }

    /** 这名玩家刚被别的传送器送来（{@value #ARRIVAL_GRACE} 刻内）：是就用掉这条记录、返回 true */
    private boolean justSentHere(ServerLevel server, UUID player, long now) {
        if (RECENT_ARRIVALS.isEmpty()) {
            return false;
        }
        Arrival arrival = RECENT_ARRIVALS.get(player);
        if (arrival == null) {
            return false;
        }
        long age = now - arrival.time();
        if (age < 0 || age > ARRIVAL_GRACE) {
            RECENT_ARRIVALS.remove(player);
            return false;
        }
        if (arrival.from().equals(GlobalPos.of(server.dimension(), worldPosition))) {
            return false;
        }
        RECENT_ARRIVALS.remove(player);
        return true;
    }

    /**
     * 忘掉这名玩家的到达记录。给展示厅用（它把人传过去以后直接放到回程那台上，不经过「走下来」），
     * 也给要连着测两台传送器的测试用。
     */
    public static void forgetArrival(UUID player) {
        RECENT_ARRIVALS.remove(player);
    }

    // ------------------------------------------------------------------
    // 客户端
    // ------------------------------------------------------------------

    /**
     * 成形时每 4 刻冒一团法术粒子（原作 cSpellFX，每刻一团）；蓄能时在站人的那一列和柱子上冒品红尘埃，个数随进度涨、每刻最多 {@value #MAX_PARTICLES} 个
     * （原作 REDSTONE 粒子参数 (0, 0, 1)：1.12 把红色 0 改成 1，实际是品红）。离镜头超过 {@value #PARTICLE_RANGE} 格什么都不放。
     */
    @Override
    protected void clientTick() {
        if (level == null) {
            return;
        }
        double cx = worldPosition.getX() + 0.5D;
        double cy = worldPosition.getY();
        double cz = worldPosition.getZ() + 0.5D;
        int showroom = SHOWROOM_CHARGE.isEmpty() ? -1 : SHOWROOM_CHARGE.getOrDefault(worldPosition, -1);
        boolean charging = showroom >= 0 || chargeStart >= 0L;
        if ((!formed && !charging) || !SpellFx.nearCamera(cx, cy + 1D, cz, PARTICLE_RANGE)) {
            return;
        }
        RandomSource random = level.random;
        if (formed && innerRotation % 4 == 0) {
            SpellFx.spawn(level, cx + (random.nextFloat() - random.nextFloat()) * 3F, cy + 1D,
                    cz + (random.nextFloat() - random.nextFloat()) * 3F, 0D, 2D, 0D);
        }
        if (!charging) {
            return;
        }
        int ticks = Math.max(1, ticksRequired());
        long shown = showroom >= 0 ? showroom : level.getGameTime() - chargeStart;
        int count = chargeParticles((int) Math.max(0L, Math.min(ticks, shown)), ticks);
        for (int i = 0; i < count; i++) {
            if ((i & 1) == 0) {
                // 站人的那一列
                AmbienceParticles.add(level, MAGENTA_DUST, cx + (random.nextFloat() - 0.5F) * 0.5F, cy + 1D + random.nextFloat() * 2D,
                        cz + (random.nextFloat() - 0.5F) * 0.5F, 0D, 0D, 0D);
            } else {
                // 四面柱子（±2）之间
                double side = random.nextBoolean() ? 2D : -2D;
                double along = (random.nextFloat() - 0.5F) * 2D;
                boolean xAxis = random.nextBoolean();
                AmbienceParticles.add(level, MAGENTA_DUST, cx + (xAxis ? side : along), cy + 2D + random.nextFloat(),
                        cz + (xAxis ? along : side), 0D, 0D, 0D);
            }
        }
    }

    /** 蓄能到 progress / ticks 时每刻放几个粒子：至少 1 个、满进度 {@value #MAX_PARTICLES} 个。纯函数 */
    public static int chargeParticles(int progress, int ticks) {
        if (ticks <= 0) {
            return MAX_PARTICLES;
        }
        int clamped = Math.max(0, Math.min(ticks, progress));
        return Math.max(1, (int) Math.ceil((double) MAX_PARTICLES * clamped / ticks));
    }

    // ------------------------------------------------------------------
    // 访问
    // ------------------------------------------------------------------

    public StructureCache structure() {
        return structure;
    }

    /** 服务端：结构是否成形（最近一次确定的结论）；客户端：更新包里的成形标记 */
    public boolean isFormed() {
        return formed;
    }

    public int progress() {
        return progress;
    }

    public Status status() {
        return status;
    }

    /** 界面落点预览：null = 不知道（宝石没绑、维度不存在） */
    @Nullable
    public ECTeleport.Result preview() {
        return preview == 0 ? null : ECTeleport.Result.byId(preview - 1);
    }

    /** 界面同步用：预览的原始值（0 = 不知道，否则 Result 序号 + 1） */
    public int previewId() {
        return preview;
    }

    /** 这一刻算出来的费用（界面同步用） */
    public int currentCost() {
        return cost;
    }

    /** 1 号格宝石指向别的维度（界面同步用：费用 = 存满） */
    public boolean targetsOtherDimension() {
        GlobalPos target = target();
        return target != null && level != null && target.dimension() != level.dimension();
    }

    /** 这名玩家是不是要先走下来 */
    public boolean mustStepOff(UUID player) {
        return mustLeave.contains(player);
    }

    /** 客户端：蓄能开始的游戏时间，没在蓄能为 -1 */
    public long chargeStart() {
        return chargeStart;
    }

    /** 柱顶往中间打的电弧伸到两格外、三格高，渲染范围包住整个结构 */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(2.5D, 0D, 2.5D).expandTowards(0D, 3D, 0D);
    }

    /** 客户端：蓄能进度 0~1（展示厅摆的蓄能也算），没在蓄能为 -1 */
    public float clientChargeProgress() {
        if (level == null) {
            return -1F;
        }
        int showroom = SHOWROOM_CHARGE.isEmpty() ? -1 : SHOWROOM_CHARGE.getOrDefault(worldPosition, -1);
        if (showroom < 0 && chargeStart < 0L) {
            return -1F;
        }
        long shown = showroom >= 0 ? showroom : level.getGameTime() - chargeStart;
        return Math.max(0F, Math.min(1F, shown / (float) Math.max(1, ticksRequired())));
    }

    @Nullable
    public BlockPos mismatchOffset() {
        return structure.mismatch() == null ? null : structure.mismatch().offset();
    }

    // ------------------------------------------------------------------
    // 存盘与同步
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        structure.save(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        structure.load(tag);
        // 下面两个只在更新包里（客户端）；存档里没有
        if (tag.contains(TAG_FORMED, Tag.TAG_BYTE)) {
            formed = tag.getBoolean(TAG_FORMED);
        }
        if (tag.contains(TAG_CHARGE_START, Tag.TAG_LONG)) {
            chargeStart = tag.getLong(TAG_CHARGE_START);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putBoolean(TAG_FORMED, formed);
        tag.putLong(TAG_CHARGE_START, chargeStart);
        return tag;
    }

    // ------------------------------------------------------------------
    // 给测试和展示厅的
    // ------------------------------------------------------------------

    /** 测试座位：按（维度, 传送器坐标）登记「站在上面的玩家」。GameTest 的测试玩家不在世界实体表里，实体查询找不到它 */
    private static final Map<GlobalPos, ServerPlayer> TEST_OCCUPANTS = new HashMap<>();

    /**
     * 只给 GameTest：这台传送器的实体查询换成「只看这名玩家」（脚所在格、旁观、真玩家这些规则照查，所以玩家要先挪到机器顶上）。
     * player 为 null 时摘掉。用完一定要摘（失败也只影响这一个坐标）。
     */
    public static void setTestOccupant(ServerLevel level, BlockPos pos, @Nullable ServerPlayer player) {
        GlobalPos key = GlobalPos.of(level.dimension(), pos.immutable());
        if (player == null) {
            TEST_OCCUPANTS.remove(key);
        } else {
            TEST_OCCUPANTS.put(key, player);
        }
    }

    /** 展示厅：客户端假装这台在蓄能，进度固定为 progress（不真把镜头传走）；按坐标，只在客户端用 */
    private static final Map<BlockPos, Integer> SHOWROOM_CHARGE = new HashMap<>();

    public static void setShowroomCharging(BlockPos pos, int progress) {
        SHOWROOM_CHARGE.put(pos.immutable(), Math.max(0, progress));
    }

    public static void clearShowroomCharging() {
        SHOWROOM_CHARGE.clear();
    }
}
