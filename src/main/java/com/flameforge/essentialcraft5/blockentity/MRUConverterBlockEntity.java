package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.ambience.MachineAmbiences;
import com.flameforge.essentialcraft5.ambience.MachineAmbience;
import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.MRUConverterBlock;
import com.flameforge.essentialcraft5.compat.botania.ManaBridge;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.MRUConverterMenu;
import com.flameforge.essentialcraft5.mru.ECCorruption;
import com.flameforge.essentialcraft5.registry.ECConverters;
import com.flameforge.essentialcraft5.registry.ECCreative;
import com.flameforge.essentialcraft5.util.ECLevels;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * MRU 转换器（EC5 新机器，原作没有；规划 tools/plan/mru_converter.md，policy §4.14）：把 MRU 换成 Forge 能量（FE）或植物魔法的魔力，
 * 推给贴着它六个面、收得下的方块。<b>只出不进</b>：不收 FE、不收魔力，EC5 里也没有任何东西能把它们换回 MRU。
 * <p>
 * <b>不读也不写世界方块、不碰实体、不发 Forge 事件、不加载区块</b>：读到的只有六个邻居的方块实体能力（邻居区块没加载完当没有，
 * {@link ECLevels#loadedBlockEntity}）和一张「加载着的转换器」表（每人全服名额）。
 *
 * <h2>换算</h2>
 * 每 1 000 MRU 换 {@code fe_per_1000_mru} FE（默认 500，即 2 MRU = 1 FE）或 {@code mana_per_1000_mru} 魔力（默认 25，即 40 MRU = 1 魔力），
 * 四档比率相同、只差每刻最多吃多少 MRU（{@link ConverterTier}）。按千分之一单位算：不满 1 的零头存在 {@link #carry}（存盘），不丢也不凭空多，
 * 扣掉的 MRU × 比率 = 输出 × 1 000 + 零头的变化。输出先进自己的缓存（上限 = 1 秒的最大输出），缓存满了就不再吃 MRU，所以旁边没人收时不白耗 MRU。
 * 无尽档不扣 MRU，每刻把缓存补满，按 {@code infinite.max_mru_per_tick}（默认 10 240）折算。
 *
 * <h2>输出</h2>
 * 每刻最多输出 = 每刻最多吃的 MRU × 比率 / 1 000（有零头向上取整），推给邻居和被电缆抽走的合计不超过它（{@link #quotaLeft}，每刻清零）。
 * 推的顺序从 {@code 游戏刻 % 6} 那一面起轮流六个面（{@link #distribute}），不饿死任何一面。能量：对外两种模式都挂 {@link ForgeCapabilities#ENERGY}
 * （电缆照常连上），只出不进；魔力模式下它是空的、抽不出。魔力：只经 {@link ManaBridge} 推，规则见那边。
 * 连续 {@value #IDLE_BACKOFF} 刻一点都没送出去就退避成每 {@value #IDLE_RETRY} 刻试一次，邻居一变立即恢复。
 *
 * <h2>邻居能力缓存</h2>
 * 每面按当前模式缓存一份能力（能量 {@code LazyOptional<IEnergyStorage>}、魔力是桥给的不透明句柄）：方块的 neighborChanged / onNeighborChange
 * 按来源坐标作废那一面；邻居能力自己作废时经监听置脏（监听只抓弱引用，不把本机挂在邻居身上）；查不到能力的面另每 {@value #STATIC_RECHECK} 刻重查一次
 * （有的模组多方块成形后才挂能力）。同一个能力对象只挂一次监听。
 * <p>
 * 光靠这两条不够：植物魔法从来不作废它的魔力能力（{@code attachBeCaps} 没登记作废监听），有的模组每次都新建能力对象、也从不作废。
 * 邻居区块被卸载又重新加载时（本机所在区块那段时间只是加载着、不 tick）没有邻居更新，缓存里还是已经移除的旧方块实体。
 * 所以每面另弱引用一份查到能力时的邻居方块实体，用缓存前看它是不是没了、是不是 {@code isRemoved()}（区块卸载会对它 setRemoved），是就重查。
 * 魔力推完存量不动的面（{@link ManaBridge#BLIND}：魔力分配器、魔力销毁器）记下来不再推，这一面的缓存重查时一并清掉。
 *
 * <h2>状态与名额</h2>
 * 每刻按顺序判（{@link #gate}）：停用 → 没有主人（专用服上没记主人的不工作；单人里没记主人的算房主，{@link ECProtection#actingOwner}）→
 * 当前模式的输出被关（比率或每刻上限为 0）/ 魔力模式但植物魔法没加载 → 名额 → 红石（任何信号都暂停）。都过了才换算、推送，
 * 推出去了是「工作中」，否则「缺 MRU」或「旁边没有收得下的方块」。主人和名额的结果缓存 {@value #STATIC_RECHECK} 刻，红石靠邻居变化重读、
 * 另每 {@value #POWER_RECHECK} 刻兜底一次。
 * <p>
 * 每人全服同时 {@code max_per_owner} 台（默认 8，0 = 不限；四档和无尽档一起数），写法照地脉凝聚器，数同主人、比自己早放下、
 * 最近 {@value #ALIVE_TICKS} 刻 tick 过、状态是「工作 / 没人收 / 缺 MRU / 红石暂停」的有几台（没选到可用模式、被名额挡住的、还没 tick 过的不占）。
 * 静态的 {@link #CLAIMERS} 表（弱引用）只放正占着名额的，数名额只扫这张表、数够上限就停：囤再多排队的转换器也不用逐台扫，开销跟着占名额的台数走。
 * 一台开始占名额时（第一次 tick 判出来、或者从不占变成占）把同主人、比它晚放下、正占着名额的几台的缓存作废（{@link #claimStarted}），
 * 超过上限的最多多做 1 刻；已经在排队的不用作废，前面多一台只会让它接着排。拆了重放排到最后。
 * 只加载、不 tick 的区块（玩家视距边上那一圈）里的转换器不占名额：{@code onLoad} 不登记，第一次真 tick 才开始算。
 *
 * <h2>方块状态与同步</h2>
 * {@code mode} 跟着方块实体的模式走；{@code active} = 最近 {@value #ACTIVE_WINDOW} 刻推出去过，同一台两次切换至少隔 {@value #ACTIVE_WINDOW} 刻。
 * 两个都只发客户端更新、不发邻居更新（{@code UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE}）。界面数值走菜单同步；物品栏、平衡值不发给附近的客户端。
 * <p>
 * 生存档工作时每 {@value #LEAK_INTERVAL} 刻泄漏 0~{@code max_corruption_gen} MRU（同地脉凝聚器）；无尽档不漏。区块不加载就什么都不做，不补算离线时间。
 * 存盘：模式、缓存、零头、放下时间（MRU、主人在基类）；邻居缓存、滑动和、名额结果不存。读档时缓存截到当前上限（配置改小时）。
 */
public class MRUConverterBlockEntity extends MRUBlockEntity {

    /** 输出哪一种。顺序只许在末尾追加：界面按钮号就是 ordinal，方块状态的值是 {@link #getSerializedName()} */
    public enum Mode implements StringRepresentable {
        /** Forge 能量（FE，也叫 RF） */
        ENERGY("energy"),
        /** 植物魔法的魔力 */
        MANA("mana");

        private static final Mode[] VALUES = values();

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public @NotNull String getSerializedName() {
            return name;
        }

        @Nullable
        public static Mode byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : null;
        }

        public static Mode byName(String name) {
            for (Mode mode : VALUES) {
                if (mode.name.equals(name)) {
                    return mode;
                }
            }
            return ENERGY;
        }
    }

    /** 五档。四档生存的数值都是配置 {@code machines.mru_converter.<key>.*}，无尽档只有每刻折算 */
    public enum ConverterTier {
        /** 低档 mru_converter：第 3 章做得出、第 4 章有了产能器才用得上 */
        BASIC("basic", 5_000, 160),
        /** 中档 pale_mru_converter：第 6 章（淡青） */
        PALE("pale", 20_000, 640),
        /** 高档 mithriline_mru_converter：第 11 章（米斯莱） */
        MITHRILINE("mithriline", 80_000, 2_560),
        /** 终极档 demonic_mru_converter：第 17 章（恶魔；海纳尔之门关着时做不出） */
        DEMONIC("demonic", 250_000, 10_240),
        /** 无尽档 infinite_mru_converter：无尽道具，只放创造栏；不耗 MRU、没有宝石槽 */
        INFINITE("infinite", 0, 10_240);

        private static final ConverterTier[] VALUES = values();

        /** 配置小节名 */
        public final String key;
        public final int defaultMaxMRU;
        public final int defaultPerTick;

        ConverterTier(String key, int defaultMaxMRU, int defaultPerTick) {
            this.key = key;
            this.defaultMaxMRU = defaultMaxMRU;
            this.defaultPerTick = defaultPerTick;
        }

        public static ConverterTier byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : BASIC;
        }

        public boolean infinite() {
            return this == INFINITE;
        }

        /** 槽位数：生存档 1（绑定宝石），无尽档 0 */
        public int slots() {
            return infinite() ? 0 : 1;
        }

        /** MRU 容量（无尽档 0） */
        public int maxMRU() {
            return infinite() ? 0 : Config.maxMRU(this);
        }

        /** 每刻最多吃多少 MRU（无尽档：按多少折算）；生存档大于容量时按容量算 */
        public int maxPerTick() {
            int perTick = Config.perTick(this);
            return infinite() ? perTick : Math.min(perTick, maxMRU());
        }

        /** 满负荷每刻出多少（千分之一单位）：每刻上限 × 比率 */
        public long outputMilli(Mode mode) {
            return (long) maxPerTick() * Config.ratio(mode);
        }

        /** 方块实体类型：四档生存共用 ECConverters 的，无尽档用 ECCreative 的 */
        public BlockEntityType<MRUConverterBlockEntity> blockEntityType() {
            return infinite() ? ECCreative.INFINITE_MRU_CONVERTER_BE.get() : ECConverters.MRU_CONVERTER_BE.get();
        }

        /** 这一档的方块 */
        public Block block() {
            return switch (this) {
                case BASIC -> ECConverters.MRU_CONVERTER.get();
                case PALE -> ECConverters.PALE_MRU_CONVERTER.get();
                case MITHRILINE -> ECConverters.MITHRILINE_MRU_CONVERTER.get();
                case DEMONIC -> ECConverters.DEMONIC_MRU_CONVERTER.get();
                case INFINITE -> ECCreative.INFINITE_MRU_CONVERTER.get();
            };
        }
    }

    /** 配置 machines.mru_converter（policy §4.14） */
    public static final class Config {
        public static ForgeConfigSpec.BooleanValue enabled;
        public static ForgeConfigSpec.IntValue fePer1000;
        public static ForgeConfigSpec.IntValue manaPer1000;
        public static ForgeConfigSpec.IntValue maxPerOwner;
        public static ForgeConfigSpec.BooleanValue generatesCorruption;
        public static ForgeConfigSpec.IntValue maxCorruptionGen;
        static final Map<ConverterTier, ForgeConfigSpec.IntValue> MAX_MRU = new EnumMap<>(ConverterTier.class);
        static final Map<ConverterTier, ForgeConfigSpec.IntValue> PER_TICK = new EnumMap<>(ConverterTier.class);

        public static final int DEFAULT_FE_PER_1000 = 500;
        public static final int DEFAULT_MANA_PER_1000 = 25;
        public static final int DEFAULT_MAX_PER_OWNER = 8;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("mru_converter");
            enabled = builder.comment("Whether MRU converters (all tiers, including the infinite one) work at all.",
                            "MRU 转换器（各档，含无尽档）是否工作")
                    .define("enabled", true);
            fePer1000 = builder.comment("FE (Forge Energy) made from every 1000 MRU; 0 = no energy output. For reference, Botania's Mana Fluxfield turns 1 mana into 10 FE.",
                            "每 1000 MRU 换多少 FE（Forge 能量）；0 = 不出能量。参考：植物魔法的魔力通量场 1 魔力 = 10 FE")
                    .defineInRange("fe_per_1000_mru", DEFAULT_FE_PER_1000, 0, 1_000_000);
            manaPer1000 = builder.comment("Botania mana made from every 1000 MRU; 0 = no mana output (mana mode also needs Botania installed).",
                            "每 1000 MRU 换多少植物魔法的魔力；0 = 不出魔力（魔力模式还要装了植物魔法）")
                    .defineInRange("mana_per_1000_mru", DEFAULT_MANA_PER_1000, 0, 1_000_000);
            maxPerOwner = builder.comment("How many converters of the same owner work at the same time across the server, all tiers together including the infinite one (only loaded ones count; the earliest placed ones work; 0 = no limit).",
                            "同一个主人全服同时有几台转换器能工作，各档和无尽档一起数（只数加载着的，按放下的先后取最早的几台；0 = 不限）")
                    .defineInRange("max_per_owner", DEFAULT_MAX_PER_OWNER, 0, 1024);
            generatesCorruption = builder.comment("Whether survival-tier converters leak MRU into nearby MRUCUs while working (the infinite one never does).",
                            "生存档转换器工作时是否向周围泄漏 MRU（无尽档永远不漏）")
                    .define("generate_corruption", true);
            maxCorruptionGen = builder.comment("Max MRU leaked per 20 working ticks (a random amount from 0 to this).",
                            "每工作 20 tick 最多泄漏多少 MRU（在 0 到这个值之间随机）")
                    .defineInRange("max_corruption_gen", 2, 0, 10000);
            for (ConverterTier tier : ConverterTier.VALUES) {
                builder.push(tier.key);
                if (!tier.infinite()) {
                    MAX_MRU.put(tier, builder.comment("MRU capacity.", "MRU 容量")
                            .defineInRange("max_mru", tier.defaultMaxMRU, 1, Integer.MAX_VALUE));
                    PER_TICK.put(tier, builder.comment("Max MRU converted per tick (above max_mru counts as max_mru); 0 = this tier outputs nothing.",
                                    "每 tick 最多换多少 MRU（大于 max_mru 时按 max_mru 算）；0 = 这一档不输出")
                            .defineInRange("max_mru_per_tick", tier.defaultPerTick, 0, 1_000_000));
                } else {
                    PER_TICK.put(tier, builder.comment("The infinite converter uses no MRU; it outputs as much as converting this much MRU per tick would. 0 = it outputs nothing.",
                                    "无尽转换器不耗 MRU，按每 tick 换这么多 MRU 的量输出；0 = 不输出")
                            .defineInRange("max_mru_per_tick", tier.defaultPerTick, 0, 1_000_000));
                }
                builder.pop();
            }
            builder.pop();
        }

        public static int maxMRU(ConverterTier tier) {
            ForgeConfigSpec.IntValue value = MAX_MRU.get(tier);
            return value == null ? tier.defaultMaxMRU : ECConfig.get(value);
        }

        public static int perTick(ConverterTier tier) {
            ForgeConfigSpec.IntValue value = PER_TICK.get(tier);
            return value == null ? tier.defaultPerTick : ECConfig.get(value);
        }

        /** 每 1 000 MRU 换多少（这一种的比率） */
        public static int ratio(Mode mode) {
            ForgeConfigSpec.IntValue value = mode == Mode.ENERGY ? fePer1000 : manaPer1000;
            if (value == null) {
                return mode == Mode.ENERGY ? DEFAULT_FE_PER_1000 : DEFAULT_MANA_PER_1000;
            }
            return ECConfig.get(value);
        }

        public static boolean enabled() {
            return enabled == null || ECConfig.get(enabled);
        }

        public static int maxPerOwner() {
            return maxPerOwner == null ? DEFAULT_MAX_PER_OWNER : ECConfig.get(maxPerOwner);
        }
    }

    /**
     * 界面状态（同步给界面用 ordinal，只许在末尾追加）。判的顺序见 {@link #gate}；都过了以后按这一刻推没推出去定前三种。
     */
    public enum Status {
        /** 推出去了（绿） */
        WORKING(0x00FF00),
        /** 旁边没有收得下的方块（黄：攒满 1 秒的量就停，不耗 MRU） */
        NO_RECEIVER(0xFFFF00),
        /** 缓存空了、也没有 MRU 可换（黄） */
        NO_MRU(0xFFFF00),
        /** 红石信号（橙：暂停） */
        POWERED(0xFFAA00),
        /** 配置 enabled = false（红） */
        DISABLED(0xFF0000),
        /** 当前模式的比率或这一档的每刻上限是 0（红） */
        MODE_OFF(0xFF0000),
        /** 魔力模式，但植物魔法没有加载（红） */
        NO_BOTANIA(0xFF0000),
        /** 全服同主人更早放下的已占满名额（橙：在等） */
        OWNER_LIMIT(0xFFAA00),
        /** 专用服上没有主人（红） */
        NO_OWNER(0xFF0000);

        private static final Status[] VALUES = values();

        public final int color;

        Status(int color) {
            this.color = color;
        }

        public static Status byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : NO_RECEIVER;
        }
    }

    public static final int SLOT_GEM = 0;

    /** 主人、名额、查不到能力的面：多少 tick 再判一次 */
    public static final int STATIC_RECHECK = 100;
    /** 红石的兜底重读间隔（平时靠邻居变化） */
    public static final int POWER_RECHECK = 20;
    /** 全服名额表里，多少 tick 内 tick 过的才算「在运转」 */
    public static final int ALIVE_TICKS = 40;
    /** 连续多少 tick 一点都没送出去就退避 */
    public static final int IDLE_BACKOFF = 20;
    /** 退避时多少 tick 试一次 */
    public static final int IDLE_RETRY = 20;
    /** 输出缓存 = 多少 tick 的最大输出（1 秒） */
    public static final int BUFFER_TICKS = 20;
    /** 界面「近 1 秒平均」的窗口，也是 active 状态的窗口和最短切换间隔 */
    public static final int ACTIVE_WINDOW = 20;
    /** 每工作多少 tick 泄漏一次 */
    public static final int LEAK_INTERVAL = 20;

    /** 占名额的状态 */
    private static final Set<Status> CLAIMING = EnumSet.of(Status.WORKING, Status.NO_RECEIVER, Status.NO_MRU, Status.POWERED);
    /** 能往外送的状态（判状态的几关都过了） */
    private static final Set<Status> OUTPUTTING = EnumSet.of(Status.WORKING, Status.NO_RECEIVER, Status.NO_MRU);
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final long NEVER = Long.MIN_VALUE / 4;

    public static final String TAG_MODE = "mode";
    public static final String TAG_BUFFER = "buffer";
    public static final String TAG_CARRY = "carry";
    public static final String TAG_PLACED = "placed_tick";

    /** 正占着名额的转换器（只在服务端线程读写；弱引用，漏了移除也不会拖住方块实体）。每台 tick 末尾按自己占不占名额进出 */
    private static final Set<MRUConverterBlockEntity> CLAIMERS = Collections.newSetFromMap(new WeakHashMap<>());

    private final ConverterTier tier;
    private Mode mode;
    /** 缓存里换好、还没送出去的 FE / 魔力（整数单位） */
    private int buffer;
    /** 不满 1 单位的零头（千分之一单位，0~999） */
    private int carry;
    /** 放下的游戏时间；没有记录是 −1（第一次服务端 tick 补成当前时间） */
    private long placedTick = -1L;

    private Status status = Status.NO_RECEIVER;
    /** 判过状态没有（还没判过的不占名额） */
    private boolean evaluated;
    private long lastTick = NEVER;
    /** 在 {@link #CLAIMERS} 里 */
    private boolean listed;
    /** 主人和名额的缓存：到这个时间重新判 */
    private long staticCheckAt;
    private boolean ownerMissing;
    @Nullable
    private Status limitResult;
    private boolean poweredCache;
    private boolean poweredDirty = true;
    private long poweredCheckAt;

    // 邻居能力缓存（下标 = Direction.ordinal）
    @SuppressWarnings("unchecked")
    private final LazyOptional<IEnergyStorage>[] energyCaps = new LazyOptional[6];
    private final Object[] manaHandles = new Object[6];
    /** 这一面的缓存是新鲜的（按当前模式查过、之后没作废） */
    private final boolean[] fresh = new boolean[6];
    /** 查不到能力的面到这个时间重查 */
    private final long[] emptyRecheckAt = new long[6];
    /** 查到能力时那一面的邻居方块实体（弱引用）：没了、被移除了就当缓存过期（见类注释「邻居能力缓存」） */
    @SuppressWarnings("unchecked")
    private final WeakReference<BlockEntity>[] neighbourRefs = new WeakReference[6];
    /** 魔力推完存量不动的面（{@link ManaBridge#BLIND}）：不再往这一面推，缓存重查时清掉 */
    private final boolean[] manaBlind = new boolean[6];
    private final IEnergyStorage[] energyTargets = new IEnergyStorage[6];
    private final List<IEnergyStorage> energyTargetList = Arrays.asList(energyTargets);

    private int idleTicks;
    private long nextPushAttempt;
    /** 本刻已经送出去的量（推的 + 被抽的）和它属于哪一刻 */
    private long quotaTick = NEVER;
    private int quotaUsed;
    /** 上一次 tick 以后被电缆抽走的（下一次 tick 算进「送出去了」） */
    private int extractedPending;
    /** 正在往邻居推：这时候别人反过来从这里抽一律不给（防重入算两次） */
    private boolean pushing;
    private final int[] window = new int[ACTIVE_WINDOW];
    private int windowSum;
    private long windowTick = NEVER;
    private long lastOutputTick = NEVER;
    private long lastActiveToggle = NEVER;
    private int leakCounter;

    @Nullable
    private LazyOptional<IEnergyStorage> energyCap;

    // 不存盘的统计（GameTest、排查用）
    private long totalOutput;
    private long totalMruSpent;
    private int leakCount;
    private int neighbourEvents;

    // GameTest 钩子：实例字段、不存盘（测试并行跑，不能用静态覆盖，也不改真配置）
    private int feRatioForTest = -1;
    private int manaRatioForTest = -1;
    private int perTickForTest = -1;
    private int perOwnerForTest = -1;
    @Nullable
    private ManaBridge bridgeForTest;

    public MRUConverterBlockEntity(BlockPos pos, BlockState state) {
        this(tierOf(state), pos, state);
    }

    private MRUConverterBlockEntity(ConverterTier tier, BlockPos pos, BlockState state) {
        super(tier.blockEntityType(), pos, state, tier::maxMRU, tier.slots());
        this.tier = tier;
        this.mode = state.hasProperty(MRUConverterBlock.MODE) ? state.getValue(MRUConverterBlock.MODE) : Mode.ENERGY;
    }

    /** 方块状态属于哪一档（不是转换器方块时按低档，正常不会出现） */
    public static ConverterTier tierOf(BlockState state) {
        return state.getBlock() instanceof MRUConverterBlock block ? block.tier() : ConverterTier.BASIC;
    }

    // ------------------------------------------------------------------
    // 槽位、同步开关
    // ------------------------------------------------------------------

    /** 槽位规则（两端共用）：0 号只放绑定宝石 */
    public static boolean slotRule(int slot, ItemStack stack) {
        return slot == SLOT_GEM && defaultSlotRule(slot, stack);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slotRule(slot, stack);
    }

    @Override
    protected boolean syncInventoryToClient() {
        return false;
    }

    @Override
    protected boolean syncBalanceToClient() {
        return false;
    }

    // ------------------------------------------------------------------
    // 数值（配置或测试钩子）
    // ------------------------------------------------------------------

    /** 植物魔法的桥（测试钩子优先） */
    public ManaBridge bridge() {
        return bridgeForTest != null ? bridgeForTest : ManaBridge.get();
    }

    /** 这一种每 1 000 MRU 换多少 */
    public int ratio(Mode which) {
        int test = which == Mode.ENERGY ? feRatioForTest : manaRatioForTest;
        return test >= 0 ? test : Config.ratio(which);
    }

    /** 每刻最多吃多少 MRU（无尽档：按多少折算） */
    public int perTick() {
        if (perTickForTest >= 0) {
            return tier.infinite() ? perTickForTest : Math.min(perTickForTest, mru.getMaxMRU());
        }
        return tier.maxPerTick();
    }

    /** 这一种满负荷每刻出多少（千分之一单位） */
    public long outputMilli(Mode which) {
        return (long) perTick() * ratio(which);
    }

    /** 这一种能不能出：比率和每刻上限都不是 0，魔力还要植物魔法 */
    public boolean modeUsable(Mode which) {
        return outputMilli(which) > 0 && (which != Mode.MANA || bridge().available());
    }

    /** 当前模式每刻最多送出去多少（有零头向上取整） */
    public int outputQuota() {
        return clampInt((outputMilli(mode) + 999L) / 1000L);
    }

    /** 当前模式的输出缓存上限（1 秒的最大输出，有零头向上取整） */
    public int bufferCap() {
        return clampInt((outputMilli(mode) * BUFFER_TICKS + 999L) / 1000L);
    }

    private static int clampInt(long value) {
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE / 2, value));
    }

    /** 千分之一单位 → 字符串（去掉多余的 0：20000 → "20"，1480 → "1.48"） */
    public static String formatMilli(long milli) {
        return BigDecimal.valueOf(milli, 3).stripTrailingZeros().toPlainString();
    }

    // ------------------------------------------------------------------
    // 工作
    // ------------------------------------------------------------------

    @Override
    protected void serverTick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        long now = server.getGameTime();
        // 中间停过 tick 超过 ALIVE_TICKS（区块退到只加载、不 tick）的，别人早就不数它了，接着占就是重新开始占
        boolean wasClaiming = listed && claimsSlot() && now - lastTick <= ALIVE_TICKS;
        lastTick = now;
        tickBody(server, now);
        if (claimsSlot()) {
            if (!listed) {
                list();
            }
            if (!wasClaiming) {
                claimStarted();
            }
        } else if (listed) {
            unlist();
        }
    }

    private void tickBody(ServerLevel server, long now) {
        if (placedTick < 0) {
            placedTick = now;
            server.blockEntityChanged(worldPosition);
        }
        syncModeState(server);
        int cap = bufferCap();
        if (buffer > cap) {
            buffer = cap;
            server.blockEntityChanged(worldPosition);
        }
        Status gate = gate(server, now);
        evaluated = true;
        int delivered = 0;
        if (gate != null) {
            status = gate;
            // 被挡住时别人抽走的不算（extractEnergy 那边也不给），清掉免得放开以后算错
            extractedPending = 0;
        } else {
            convert(server, cap);
            delivered = extractedPending;
            extractedPending = 0;
            if (now >= nextPushAttempt && buffer > 0) {
                int pushed = push(server, now);
                delivered += pushed;
                if (pushed > 0) {
                    idleTicks = 0;
                } else if (++idleTicks >= IDLE_BACKOFF) {
                    nextPushAttempt = now + IDLE_RETRY;
                }
            }
            if (delivered > 0) {
                idleTicks = 0;
                nextPushAttempt = 0L;
                status = Status.WORKING;
            } else if (buffer <= 0 && !tier.infinite() && mru.getMRU() <= 0) {
                status = Status.NO_MRU;
            } else {
                status = Status.NO_RECEIVER;
            }
            if (status == Status.WORKING && !tier.infinite() && ++leakCounter >= LEAK_INTERVAL) {
                leakCounter = 0;
                if (ECConfig.get(Config.generatesCorruption)) {
                    leakCount++;
                    ECCorruption.randomIncreaseAt(server, worldPosition, server.random, ECConfig.get(Config.maxCorruptionGen));
                }
            }
        }
        recordOutput(now, delivered);
        updateActiveState(server, now);
    }

    /**
     * 判状态（顺序即类注释「状态与名额」）：返回挡住的原因，都过了返回 null。主人和名额缓存 {@value #STATIC_RECHECK} 刻。
     */
    @Nullable
    private Status gate(ServerLevel server, long now) {
        if (!Config.enabled()) {
            return Status.DISABLED;
        }
        if (now >= staticCheckAt) {
            staticCheckAt = now + STATIC_RECHECK;
            UUID owner = ECProtection.actingOwner(this);
            ownerMissing = owner == null;
            int perOwner = perOwnerForTest >= 0 ? perOwnerForTest : Config.maxPerOwner();
            limitResult = owner != null && perOwner > 0 && earlierClaimers(owner, now, perOwner) >= perOwner ? Status.OWNER_LIMIT : null;
        }
        if (ownerMissing) {
            return Status.NO_OWNER;
        }
        if (outputMilli(mode) <= 0) {
            return Status.MODE_OFF;
        }
        if (mode == Mode.MANA && !bridge().available()) {
            return Status.NO_BOTANIA;
        }
        if (limitResult != null) {
            return limitResult;
        }
        if (poweredDirty || now >= poweredCheckAt) {
            poweredDirty = false;
            poweredCheckAt = now + POWER_RECHECK;
            poweredCache = server.hasNeighborSignal(worldPosition);
        }
        return poweredCache ? Status.POWERED : null;
    }

    /** 换算：只吃缓存装得下的那部分 MRU，零头进 carry。无尽档不扣，直接补满 */
    private void convert(ServerLevel server, int cap) {
        if (tier.infinite()) {
            if (buffer != cap) {
                buffer = cap;
                server.blockEntityChanged(worldPosition);
            }
            return;
        }
        int room = cap - buffer;
        long ratio = ratio(mode);
        if (room <= 0 || ratio <= 0) {
            return;
        }
        // carry + m × ratio < (room + 1) × 1000 ⇒ 换出来的整数单位 ≤ room
        long fit = ((long) (room + 1) * 1000L - 1L - carry) / ratio;
        int want = (int) Math.min(Math.min(perTick(), mru.getMRU()), fit);
        if (want <= 0) {
            return;
        }
        int spent = mru.extractMRU(want, true);
        if (spent <= 0) {
            return;
        }
        long total = carry + spent * ratio;
        buffer += (int) (total / 1000L);
        carry = (int) (total % 1000L);
        totalMruSpent += spent;
        server.blockEntityChanged(worldPosition);
    }

    /** 本刻还能送出去多少 */
    private int quotaLeft(long now) {
        if (quotaTick != now) {
            quotaTick = now;
            quotaUsed = 0;
        }
        return Math.max(0, outputQuota() - quotaUsed);
    }

    /** 往六个面推，从 {@code 游戏刻 % 6} 那一面起轮流，合计不超过本刻额度和缓存。返回推出去的量 */
    private int push(ServerLevel server, long now) {
        int amount = Math.min(quotaLeft(now), buffer);
        if (amount <= 0) {
            return 0;
        }
        int start = (int) Math.floorMod(now, 6L);
        int delivered;
        pushing = true;
        try {
            if (mode == Mode.ENERGY) {
                for (int face = 0; face < 6; face++) {
                    energyTargets[face] = energyAt(server, now, face);
                }
                delivered = distribute(amount, energyTargetList, start);
                Arrays.fill(energyTargets, null);
            } else {
                delivered = pushMana(server, now, amount, start);
            }
        } finally {
            pushing = false;
        }
        delivered = Math.max(0, Math.min(delivered, Math.min(amount, buffer)));
        if (delivered > 0) {
            buffer -= delivered;
            quotaUsed += delivered;
            totalOutput += delivered;
            server.blockEntityChanged(worldPosition);
        }
        return delivered;
    }

    /**
     * 能量的分法（GameTest 直接测它）：从第 {@code start} 个起轮流问每个目标（null 和不收的跳过），每个给剩下的全部、按它实收的扣，
     * 合计不超过 {@code amount}。返回合计送出去的量。目标的 receiveEnergy 返回负数、或超过给它的量，按 0 / 给它的量算。
     */
    public static int distribute(int amount, List<? extends IEnergyStorage> targets, int start) {
        int size = targets.size();
        if (amount <= 0 || size == 0) {
            return 0;
        }
        int left = amount;
        int from = Math.floorMod(start, size);
        for (int i = 0; i < size && left > 0; i++) {
            IEnergyStorage target = targets.get((from + i) % size);
            if (target == null || !target.canReceive()) {
                continue;
            }
            int got = target.receiveEnergy(left, false);
            left -= Math.max(0, Math.min(got, left));
        }
        return amount - left;
    }

    private int pushMana(ServerLevel server, long now, int amount, int start) {
        ManaBridge bridge = bridge();
        int left = amount;
        for (int i = 0; i < 6 && left > 0; i++) {
            int face = (start + i) % 6;
            Object handle = manaAt(server, now, face, bridge);
            if (handle == null || manaBlind[face]) {
                continue;
            }
            int result = bridge.accept(handle, left);
            if (ManaBridge.blind(result)) {
                manaBlind[face] = true;
            }
            left -= Math.max(0, Math.min(ManaBridge.taken(result), left));
        }
        return amount - left;
    }

    /** 这一面的能量接收方（缓存，见类注释）；没有返回 null */
    @Nullable
    private IEnergyStorage energyAt(ServerLevel server, long now, int face) {
        LazyOptional<IEnergyStorage> cached = energyCaps[face];
        boolean stale = !fresh[face] || (cached != null ? !cached.isPresent() || neighbourGone(face) : now >= emptyRecheckAt[face]);
        if (stale) {
            Direction dir = DIRECTIONS[face];
            BlockEntity neighbour = ECLevels.loadedBlockEntity(server, worldPosition.relative(dir));
            LazyOptional<IEnergyStorage> found = null;
            if (neighbour != null && neighbour != this && !neighbour.isRemoved()) {
                LazyOptional<IEnergyStorage> cap = neighbour.getCapability(ForgeCapabilities.ENERGY, dir.getOpposite());
                if (cap.isPresent()) {
                    found = cap;
                }
            }
            if (found != null && found != cached) {
                watchEnergy(found, this, face);
            }
            energyCaps[face] = found;
            neighbourRefs[face] = found != null ? new WeakReference<>(neighbour) : null;
            fresh[face] = true;
            emptyRecheckAt[face] = now + STATIC_RECHECK;
            cached = found;
        }
        return cached == null ? null : cached.resolve().orElse(null);
    }

    /** 这一面的魔力接收方句柄（同上）；没有返回 null */
    @Nullable
    private Object manaAt(ServerLevel server, long now, int face, ManaBridge bridge) {
        Object cached = manaHandles[face];
        boolean stale = !fresh[face] || (cached != null ? !bridge.isPresent(cached) || neighbourGone(face) : now >= emptyRecheckAt[face]);
        if (stale) {
            Direction dir = DIRECTIONS[face];
            BlockEntity neighbour = ECLevels.loadedBlockEntity(server, worldPosition.relative(dir));
            Object found = neighbour != null && neighbour != this && !neighbour.isRemoved() ? bridge.find(neighbour, dir.getOpposite()) : null;
            if (found != null && found != cached) {
                watchMana(bridge, found, this, face);
            }
            manaHandles[face] = found;
            neighbourRefs[face] = found != null ? new WeakReference<>(neighbour) : null;
            manaBlind[face] = false;
            fresh[face] = true;
            emptyRecheckAt[face] = now + STATIC_RECHECK;
            cached = found;
        }
        return cached;
    }

    /** 查到能力时的那个邻居方块实体已经没了、或被移除了（区块卸载、拆掉）：缓存过期 */
    private boolean neighbourGone(int face) {
        WeakReference<BlockEntity> ref = neighbourRefs[face];
        BlockEntity neighbour = ref == null ? null : ref.get();
        return neighbour == null || neighbour.isRemoved();
    }

    /** 邻居的能量能力作废时把那一面置脏。监听只抓弱引用，免得作废前邻居一直拖着这台机器 */
    private static void watchEnergy(LazyOptional<IEnergyStorage> cap, MRUConverterBlockEntity self, int face) {
        WeakReference<MRUConverterBlockEntity> ref = new WeakReference<>(self);
        cap.addListener(gone -> {
            MRUConverterBlockEntity be = ref.get();
            if (be != null && be.energyCaps[face] == gone) {
                be.fresh[face] = false;
                be.wake();
            }
        });
    }

    private static void watchMana(ManaBridge bridge, Object handle, MRUConverterBlockEntity self, int face) {
        WeakReference<MRUConverterBlockEntity> ref = new WeakReference<>(self);
        bridge.onInvalidate(handle, () -> {
            MRUConverterBlockEntity be = ref.get();
            if (be != null && be.manaHandles[face] == handle) {
                be.fresh[face] = false;
                be.wake();
            }
        });
    }

    /** 退避中的立即恢复 */
    private void wake() {
        idleTicks = 0;
        nextPushAttempt = 0L;
    }

    /** 方块的邻居变化（放下 / 拆掉方块、红石、邻居方块实体内容）：作废那一面的缓存、重读红石、退出退避 */
    public void onNeighborChanged(BlockPos from) {
        neighbourEvents++;
        poweredDirty = true;
        for (Direction dir : DIRECTIONS) {
            if (worldPosition.relative(dir).equals(from)) {
                fresh[dir.ordinal()] = false;
                wake();
                return;
            }
        }
    }

    private void invalidateNeighbours() {
        Arrays.fill(fresh, false);
        wake();
    }

    /** 近 {@value #ACTIVE_WINDOW} 刻的滑动和；中间停过 tick（区块卸载）超过窗口就清零 */
    private void recordOutput(long now, int delivered) {
        if (now - windowTick >= ACTIVE_WINDOW || now < windowTick) {
            Arrays.fill(window, 0);
            windowSum = 0;
        } else {
            for (long t = windowTick + 1; t < now; t++) {
                int skipped = (int) Math.floorMod(t, (long) ACTIVE_WINDOW);
                windowSum -= window[skipped];
                window[skipped] = 0;
            }
        }
        windowTick = now;
        int index = (int) Math.floorMod(now, (long) ACTIVE_WINDOW);
        windowSum -= window[index];
        window[index] = delivered;
        windowSum += delivered;
        if (delivered > 0) {
            lastOutputTick = now;
        }
    }

    /** active 方块状态：最近一个窗口里推出去过；两次切换至少隔一个窗口；只发客户端更新 */
    private void updateActiveState(ServerLevel server, long now) {
        BlockState state = getBlockState();
        if (!state.hasProperty(MRUConverterBlock.ACTIVE)) {
            return;
        }
        boolean want = now - lastOutputTick < ACTIVE_WINDOW;
        if (state.getValue(MRUConverterBlock.ACTIVE) != want && now - lastActiveToggle >= ACTIVE_WINDOW) {
            lastActiveToggle = now;
            server.setBlock(worldPosition, state.setValue(MRUConverterBlock.ACTIVE, want), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    /** mode 方块状态跟着方块实体的模式走（读档、指令改了方块状态时改回来） */
    private void syncModeState(ServerLevel server) {
        BlockState state = getBlockState();
        if (state.hasProperty(MRUConverterBlock.MODE) && state.getValue(MRUConverterBlock.MODE) != mode) {
            server.setBlock(worldPosition, state.setValue(MRUConverterBlock.MODE, mode), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    // ------------------------------------------------------------------
    // 名额（照地脉凝聚器）
    // ------------------------------------------------------------------

    /** 全服同主人、比自己早放下、占着名额的转换器有几台（数到 limit 就停）；顺手清掉表里已移除的 */
    private int earlierClaimers(UUID owner, long now, int limit) {
        int count = 0;
        Iterator<MRUConverterBlockEntity> it = CLAIMERS.iterator();
        while (it.hasNext() && count < limit) {
            MRUConverterBlockEntity other = it.next();
            if (other.isRemoved() || !(other.level instanceof ServerLevel)) {
                other.listed = false;
                it.remove();
                continue;
            }
            if (other == this || now - other.lastTick > ALIVE_TICKS || !other.claimsSlot()) {
                continue;
            }
            if (placedBefore(other, this) && owner.equals(ECProtection.actingOwner(other))) {
                count++;
            }
        }
        return count;
    }

    /** 占不占全服名额：判过状态、状态是「开着、只是暂时没送出去 / 暂停」的那几种。还没 tick 过的不占 */
    private boolean claimsSlot() {
        return evaluated && CLAIMING.contains(status);
    }

    /**
     * 这台刚开始占名额：同主人、比它晚放下、正占着名额的几台把名额缓存作废，下一次 tick 就重数。
     * 排队中的（不在表里）不用管：前面多一台只会让它接着排
     */
    private void claimStarted() {
        UUID owner = ECProtection.actingOwner(this);
        if (owner == null) {
            return;
        }
        for (MRUConverterBlockEntity other : CLAIMERS) {
            if (other != this && !other.isRemoved() && other.level instanceof ServerLevel && placedBefore(this, other)
                    && owner.equals(ECProtection.actingOwner(other))) {
                other.staticCheckAt = 0L;
            }
        }
    }

    /** a 是否比 b 早放下：先比放下的时间（没有记录的排最后），相同比坐标 asLong，小的算早（同地脉凝聚器） */
    static boolean placedBefore(MRUConverterBlockEntity a, MRUConverterBlockEntity b) {
        long ta = a.placedTick < 0 ? Long.MAX_VALUE : a.placedTick;
        long tb = b.placedTick < 0 ? Long.MAX_VALUE : b.placedTick;
        if (ta != tb) {
            return ta < tb;
        }
        return a.worldPosition.asLong() < b.worldPosition.asLong();
    }

    private void list() {
        CLAIMERS.add(this);
        listed = true;
    }

    private void unlist() {
        if (level != null && !level.isClientSide && listed) {
            CLAIMERS.remove(this);
            listed = false;
        }
    }

    @Override
    public void setRemoved() {
        unlist();
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        unlist();
        super.onChunkUnloaded();
    }

    // ------------------------------------------------------------------
    // 对外能力：能量只出不进
    // ------------------------------------------------------------------

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) {
            if (isRemoved()) {
                return LazyOptional.empty();
            }
            if (energyCap == null || !energyCap.isPresent()) {
                energyCap = LazyOptional.of(OutputEnergy::new);
            }
            return energyCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        if (energyCap != null) {
            energyCap.invalidate();
            energyCap = null;
        }
    }

    /**
     * 对外的能量视图：不收（{@code canReceive} false、{@code receiveEnergy} 恒 0）；能量模式下从缓存抽，计入本刻额度，
     * 判状态的几关没过（红石暂停、名额、停用……）时抽不出；魔力模式下是空的、{@code canExtract} false。
     */
    private final class OutputEnergy implements IEnergyStorage {

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (mode != Mode.ENERGY || pushing || maxExtract <= 0 || !OUTPUTTING.contains(status)
                    || !(level instanceof ServerLevel server) || isRemoved()) {
                return 0;
            }
            long now = server.getGameTime();
            int give = Math.min(Math.min(maxExtract, buffer), quotaLeft(now));
            if (give <= 0) {
                return 0;
            }
            if (!simulate) {
                buffer -= give;
                quotaUsed += give;
                extractedPending += give;
                totalOutput += give;
                server.blockEntityChanged(worldPosition);
            }
            return give;
        }

        @Override
        public int getEnergyStored() {
            return mode == Mode.ENERGY ? buffer : 0;
        }

        @Override
        public int getMaxEnergyStored() {
            return mode == Mode.ENERGY ? bufferCap() : 0;
        }

        @Override
        public boolean canExtract() {
            return mode == Mode.ENERGY;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // 访问（界面、方块、测试）
    // ------------------------------------------------------------------

    /**
     * 切模式（界面按钮）：清掉旧模式缓存里没送出去的（最多 1 秒的量）和零头，作废邻居缓存，方块状态立即改（只发客户端更新）。
     * 目标模式出不了（比率 0、魔力而植物魔法没加载）或者和现在相同时返回 false、什么都不改。
     */
    public boolean setMode(Mode next) {
        if (next == mode || !modeUsable(next)) {
            return false;
        }
        mode = next;
        buffer = 0;
        carry = 0;
        // 邻居缓存只置脏、不清空：同一个能力对象重新查到时不再挂第二个监听
        invalidateNeighbours();
        if (level instanceof ServerLevel server) {
            syncModeState(server);
        }
        setChanged();
        return true;
    }

    /** 放下的时间（方块的 setPlacedBy 调）；下一 tick 重新数名额 */
    public void markPlaced(long gameTime) {
        placedTick = gameTime;
        staticCheckAt = 0L;
        setChanged();
    }

    public ConverterTier tier() {
        return tier;
    }

    public Mode mode() {
        return mode;
    }

    public Status status() {
        return status;
    }

    public int buffer() {
        return buffer;
    }

    /** 零头（千分之一单位） */
    public int carry() {
        return carry;
    }

    public long placedTick() {
        return placedTick;
    }

    /** 近 {@value #ACTIVE_WINDOW} 刻送出去的合计（界面显示「近 1 秒平均每刻」= 它 / {@value #ACTIVE_WINDOW}） */
    public int recentOutput() {
        return windowSum;
    }

    /** 界面用的标志位：1 = 植物魔法可用，2 = 能量模式能出，4 = 魔力模式能出（比率和每刻上限都不是 0） */
    public int menuFlags() {
        int flags = 0;
        if (bridge().available()) {
            flags |= 1;
        }
        if (outputMilli(Mode.ENERGY) > 0) {
            flags |= 2;
        }
        if (outputMilli(Mode.MANA) > 0) {
            flags |= 4;
        }
        return flags;
    }

    /** 没人收、已退避成每 {@value #IDLE_RETRY} 刻试一次（GameTest 用） */
    public boolean isBackingOff() {
        return idleTicks >= IDLE_BACKOFF;
    }

    /** 名额表（正占着名额的）里有没有自己（GameTest 用） */
    public boolean isListed() {
        return CLAIMERS.contains(this);
    }

    /** 送出去的合计（推的 + 被抽的；不存盘） */
    public long totalOutput() {
        return totalOutput;
    }

    /** 换算吃掉的 MRU 合计（不存盘） */
    public long totalMruSpent() {
        return totalMruSpent;
    }

    /** 泄漏过几次（不存盘） */
    public int leakCount() {
        return leakCount;
    }

    /** 收到过几次邻居变化通知（不存盘；GameTest 查「切 active 不发邻居更新」用） */
    public int neighbourEvents() {
        return neighbourEvents;
    }

    /** GameTest：两种比率（负数恢复配置） */
    public void setRatiosForTest(int fePer1000, int manaPer1000) {
        feRatioForTest = fePer1000;
        manaRatioForTest = manaPer1000;
    }

    /** GameTest：每刻上限（负数恢复配置） */
    public void setPerTickForTest(int perTick) {
        perTickForTest = perTick;
    }

    /** GameTest：每人全服台数（负数恢复配置） */
    public void setOwnerLimitForTest(int perOwner) {
        perOwnerForTest = perOwner;
        staticCheckAt = 0L;
    }

    /** GameTest：换一个植物魔法桥（null 恢复按模组表选的） */
    public void setManaBridgeForTest(@Nullable ManaBridge bridge) {
        bridgeForTest = bridge;
        invalidateNeighbours();
    }

    // ------------------------------------------------------------------
    // 存盘、同步
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString(TAG_MODE, mode.getSerializedName());
        tag.putInt(TAG_BUFFER, buffer);
        tag.putInt(TAG_CARRY, carry);
        tag.putLong(TAG_PLACED, placedTick);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains(TAG_MODE, Tag.TAG_STRING)) {
            mode = Mode.byName(tag.getString(TAG_MODE));
        }
        buffer = Math.max(0, Math.min(tag.getInt(TAG_BUFFER), bufferCap()));
        carry = Math.max(0, Math.min(tag.getInt(TAG_CARRY), 999));
        placedTick = tag.contains(TAG_PLACED, Tag.TAG_LONG) ? tag.getLong(TAG_PLACED) : -1L;
        staticCheckAt = 0L;
        Arrays.fill(fresh, false);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        // 客户端只画方块状态（mode / active），这些用不到
        tag.remove(TAG_BUFFER);
        tag.remove(TAG_CARRY);
        tag.remove(TAG_PLACED);
        return tag;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new MRUConverterMenu(id, inventory, this);
    }

    /** 服务器停止时清空名额表（单人换存档不带过去） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
    public static final class MRUConverterEvents {

        private MRUConverterEvents() {
        }

        @SubscribeEvent
        public static void mruConverterServerStopped(ServerStoppedEvent event) {
            for (MRUConverterBlockEntity converter : CLAIMERS) {
                converter.listed = false;
            }
            CLAIMERS.clear();
        }
    }

    // ------------------------------------------------------------------ 声音和粒子

    @Override
    protected boolean ambientWorking() {
        return status == Status.WORKING;
    }

    @Override
    protected MachineAmbience.Profile ambience() {
        return MachineAmbiences.CONVERTER;
    }
}
