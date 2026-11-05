package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.ambience.MachineAmbiences;
import com.flameforge.essentialcraft5.ambience.MachineAmbience;
import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.espe.ESPEAccess;
import com.flameforge.essentialcraft5.espe.ESPEStorage;
import com.flameforge.essentialcraft5.espe.ESPETransfer;
import com.flameforge.essentialcraft5.menu.LeyCondenserMenu;
import com.flameforge.essentialcraft5.menu.MachineData;
import com.flameforge.essentialcraft5.mru.ECCorruption;
import com.flameforge.essentialcraft5.registry.ECLeyCondenser;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

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
 * 地脉凝聚器（EC5 新机器，第九批 batch9.md；policy §4.9）：后期按需凝聚指定的原版矿物（含远古残骸），以及下界之星。
 * <p>
 * <b>不读也不写世界方块、不碰实体、不发 Forge 事件、不加载区块</b>：玩家在界面上点一种目标，机器每刻扣一点 MRU（下界之星另扣 ESPE）、
 * 进度走一格，走满就往自己的 9 个输出格放 1 个。读到的世界数据只有自己所在区块的方块实体表（每人每区块）、
 * 周围 8 个固定位置的 ESPE 源（只在目标要 ESPE 时），以及一张「加载着的凝聚器」表（每人全服）。主人离线照常工作。
 *
 * <h2>目标与价格</h2>
 * 目标表写死在 {@link Target}（原版 9 种矿物 + 下界之星）：不出煤和任何燃料（终极热力产能器能把煤烧回远多于凝聚价格的 MRU），不出本模组的材料。
 * 每种 3 个配置 {@code machines.ley_condenser.<目标>.mru / espe / ticks}：每个用多少 MRU、多少 ESPE、多少刻，{@code ticks = 0} 表示不收这一种。
 * 运行时再查一次燃烧时间：别的模组把某种产物改成燃料时，这一种按「不收」处理、记一次日志。
 *
 * <h2>边做边扣</h2>
 * 从进度 p 走到 p+1 要付 MRU {@code ⌊M·(p+1)/T⌋ − ⌊M·p/T⌋}、ESPE {@code ⌊E·(p+1)/T⌋ − ⌊E·p/T⌋}（整数，走满正好 M、E），哪样不够就停在 p；
 * 够了先扣再走。走满才出货：拆掉、换目标进度都清零，已经扣掉的不退（不清零的话，可以先按粗铜的单价付进度、最后一刻换成下界之星）。
 * 开工前确认输出格放得下 1 个，没有待放清单；配置把用时改短时，进度已经够了就直接出货，没付到的那部分不再补。
 *
 * <h2>台数</h2>
 * <ul>
 *   <li>每人每区块 {@code max_per_owner_per_chunk} 台（默认 1）：照地脉投影仪，遍历自己所在区块的方块实体表，数同主人、比自己早放下的凝聚器。</li>
 *   <li>每人全服 {@code max_per_owner} 台（默认 4，0 = 不限）：静态的 {@link #LOADED} 表（弱引用，{@code onLoad} / 第一次服务端 tick 登记，
 *       {@code setRemoved} / {@code onChunkUnloaded} 移除，服务器停止时清空），数同主人、比自己早放下、「占着名额」的有几台。
 *       占名额 = 最近 {@value #ALIVE_TICKS} 刻里 tick 过（区块卸载、加载着却不 tick 的不算），并且还没判过状态，或者状态是
 *       工作 / 红石暂停 / 输出满 / 缺 MRU / 缺 ESPE（机器开着、只是暂时停）。没选目标、目标不收、没有地脉、被同区块的挡住、被名额挡住的不占。</li>
 *   <li>两条都只在判状态时查，结果缓存 {@value #STATIC_RECHECK} 刻；做到一半也每 {@value #STATIC_RECHECK} 刻核一次，名额被更早的拿回去就停在原处
 *       （进度保留）。光靠缓存不够：更早放下的几台重新占名额时（选上目标、所在区块重新加载），后放的要等各自的缓存过期才停，
 *       这段时间同主人同时在工作的可以超过上限，最坏是他加载着的全部。所以一台<b>开始</b>占名额时（区块加载登记、第一次判完状态、
 *       从不占名额的状态翻成占名额、停 tick 超过 {@value #ALIVE_TICKS} 刻后又转起来），{@link #claimStarted} 把同主人、比它晚放下的几台的
 *       名额缓存作废，它们下一次 tick 就重数：超过上限的那台最多多做 1 刻。只在翻转时遍历一次全服表，平时没有开销。
 *       拆了重放排到最后（放下时间是新的）。</li>
 * </ul>
 * 专用服上没有主人的不工作（/setblock、结构、别的模组或假玩家放的；同投影仪 D4 A ①）；单人、局域网里没记主人的算房主（{@link ECProtection#actingOwner}）。
 *
 * <h2>ESPE</h2>
 * 只在目标要 ESPE（下界之星）、机器在工作或等 MRU / ESPE 时，每 {@value #ESPE_PULL_INTERVAL} 刻从 {@link #SOURCES}（同一高度、隔一格一圈的 8 格）抽一次，
 * 走 {@link ESPETransfer#pullFromAround}（只读已加载的方块实体，不加载区块）；对外的 ESPE 只进不出（{@link ESPEAccess#INSERT}，同米斯莱熔炉）。
 *
 * <h2>槽位与同步</h2>
 * 0 绑定宝石；1~9 输出（只取不放，漏斗任意一面都能抽）。漏斗、管道只能往 0 号格塞绑定宝石。
 * 物品栏、平衡值不发给附近的客户端；更新包只带目标和「在不在凝」（画头顶的影像和光束），换目标或翻转时 {@link #markClientDirty()}。
 * <p>
 * 每工作 20 刻泄漏 0~{@code max_corruption_gen} MRU（同投影仪每格一次）。
 */
public class LeyCondenserBlockEntity extends MRUBlockEntity {

    /** 能凝的东西。顺序只许在末尾追加：界面按钮号就是 ordinal */
    public enum Target {
        RAW_COPPER("raw_copper", Items.RAW_COPPER, true, 1_000, 0, 10),
        RAW_IRON("raw_iron", Items.RAW_IRON, true, 4_000, 0, 40),
        RAW_GOLD("raw_gold", Items.RAW_GOLD, true, 32_000, 0, 160),
        REDSTONE("redstone", Items.REDSTONE, true, 1_000, 0, 10),
        LAPIS_LAZULI("lapis_lazuli", Items.LAPIS_LAZULI, true, 1_000, 0, 10),
        QUARTZ("quartz", Items.QUARTZ, true, 2_000, 0, 20),
        DIAMOND("diamond", Items.DIAMOND, true, 100_000, 0, 600),
        EMERALD("emerald", Items.EMERALD, true, 200_000, 0, 1_200),
        ANCIENT_DEBRIS("ancient_debris", Items.ANCIENT_DEBRIS, true, 250_000, 0, 1_200),
        NETHER_STAR("nether_star", Items.NETHER_STAR, false, 500_000, 10_000, 2_400);

        public static final Target[] VALUES = values();

        /** 配置小节名（产物注册名的路径） */
        public final String key;
        public final Item product;
        /** 是否要所在维度有地脉（投影仪的矿石表） */
        public final boolean needsLey;
        public final int defaultMRU;
        public final int defaultESPE;
        public final int defaultTicks;

        Target(String key, Item product, boolean needsLey, int defaultMRU, int defaultESPE, int defaultTicks) {
            this.key = key;
            this.product = product;
            this.needsLey = needsLey;
            this.defaultMRU = defaultMRU;
            this.defaultESPE = defaultESPE;
            this.defaultTicks = defaultTicks;
        }

        @Nullable
        public static Target byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : null;
        }

        @Nullable
        public static Target byProduct(@Nullable ResourceLocation id) {
            if (id == null) {
                return null;
            }
            for (Target target : VALUES) {
                if (id.equals(ForgeRegistries.ITEMS.getKey(target.product))) {
                    return target;
                }
            }
            return null;
        }
    }

    /** 配置 machines.ley_condenser（policy §4.9） */
    public static final class Config {
        public static ForgeConfigSpec.BooleanValue enabled;
        public static ForgeConfigSpec.IntValue maxMRU;
        public static ForgeConfigSpec.DoubleValue maxESPE;
        public static ForgeConfigSpec.IntValue maxPerOwnerPerChunk;
        public static ForgeConfigSpec.IntValue maxPerOwner;
        public static ForgeConfigSpec.BooleanValue generatesCorruption;
        public static ForgeConfigSpec.IntValue maxCorruptionGen;
        static final Map<Target, ForgeConfigSpec.IntValue> MRU = new EnumMap<>(Target.class);
        static final Map<Target, ForgeConfigSpec.IntValue> ESPE = new EnumMap<>(Target.class);
        static final Map<Target, ForgeConfigSpec.IntValue> TICKS = new EnumMap<>(Target.class);

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("ley_condenser");
            enabled = builder.comment("Whether ley condensers work at all.", "地脉凝聚器是否工作")
                    .define("enabled", true);
            maxMRU = builder.comment("MRU capacity. A target whose MRU per tick is above this cannot be condensed.",
                            "MRU 容量；某种目标每刻要的 MRU 超过它就凝不了")
                    .defineInRange("max_mru", 100_000, 1, Integer.MAX_VALUE);
            maxESPE = builder.comment("ESPE capacity. A target whose ESPE per tick is above this cannot be condensed.",
                            "ESPE 容量；某种目标每刻要的 ESPE 超过它就凝不了")
                    .defineInRange("max_espe", 20_000D, 1D, MachineData.MAX_SYNCED_ESPE);
            maxPerOwnerPerChunk = builder.comment("How many condensers of the same owner work in one chunk (the earliest placed ones; the rest wait).",
                            "同一区块里同一个主人有几台凝聚器能工作（按放下的先后取最早的几台，其余的等着）")
                    .defineInRange("max_per_owner_per_chunk", 1, 1, 64);
            maxPerOwner = builder.comment("How many condensers of the same owner work at the same time across the server (only loaded ones count; 0 = no limit).",
                            "同一个主人全服同时有几台凝聚器能工作（只数加载着的；0 = 不限）")
                    .defineInRange("max_per_owner", 4, 0, 1024);
            generatesCorruption = builder.comment("Whether the condenser leaks MRU into nearby MRUCUs while working.",
                            "工作时是否向周围泄漏 MRU")
                    .define("generate_corruption", true);
            maxCorruptionGen = builder.comment("Max MRU leaked per 20 working ticks (a random amount from 0 to this).",
                            "每工作 20 tick 最多泄漏多少 MRU（在 0 到这个值之间随机）")
                    .defineInRange("max_corruption_gen", 2, 0, 10000);
            for (Target target : Target.VALUES) {
                builder.push(target.key);
                MRU.put(target, builder.comment("MRU used per item, paid a little every tick while condensing.",
                                "每凝一个用多少 MRU（凝的过程中每 tick 扣一点）")
                        .defineInRange("mru", target.defaultMRU, 0, 100_000_000));
                ESPE.put(target, builder.comment("ESPE used per item, paid a little every tick while condensing.",
                                "每凝一个用多少 ESPE（凝的过程中每 tick 扣一点）")
                        .defineInRange("espe", target.defaultESPE, 0, 10_000_000));
                TICKS.put(target, builder.comment("Ticks per item (20 ticks = 1 second). 0 = the condenser does not make this at all.",
                                "每凝一个要多少 tick（20 tick = 1 秒）；0 = 不收这一种")
                        .defineInRange("ticks", target.defaultTicks, 0, 24_000));
                builder.pop();
            }
            builder.pop();
        }

        public static int mru(Target target) {
            ForgeConfigSpec.IntValue value = MRU.get(target);
            return value == null ? target.defaultMRU : ECConfig.get(value);
        }

        public static int espe(Target target) {
            ForgeConfigSpec.IntValue value = ESPE.get(target);
            return value == null ? target.defaultESPE : ECConfig.get(value);
        }

        public static int ticks(Target target) {
            ForgeConfigSpec.IntValue value = TICKS.get(target);
            return value == null ? target.defaultTicks : ECConfig.get(value);
        }
    }

    /**
     * 界面状态（同步给界面用 ordinal，只许在末尾追加）。{@code progress == 0} 时按这个顺序判（{@link #evaluate}），都不是就 WORKING。
     */
    public enum Status {
        /** 正在凝（绿） */
        WORKING(0x00FF00),
        /** 配置 enabled = false（红） */
        DISABLED(0xFF0000),
        /** 专用服上没有主人（红） */
        NO_OWNER(0xFF0000),
        /** 没选目标（黄） */
        NO_TARGET(0xFFFF00),
        /** 这一种用时配成 0，或者产物有燃烧时间（红） */
        TARGET_OFF(0xFF0000),
        /** 目标要地脉，这个维度没有（红） */
        NO_LEY(0xFF0000),
        /** 本区块同主人更早放下的已占满名额（橙：在等） */
        DUPLICATE(0xFFAA00),
        /** 全服同主人更早放下的已占满名额（橙：在等） */
        OWNER_LIMIT(0xFFAA00),
        /** 红石信号（橙：暂停） */
        POWERED(0xFFAA00),
        /** 输出格放不下一个目标产物（黄） */
        OUTPUT_FULL(0xFFFF00),
        /** 每刻要的 MRU / ESPE 超过容量（红：只有配错时会出现） */
        TOO_EXPENSIVE(0xFF0000),
        /** 这一刻的 MRU 不够（黄） */
        NO_MRU(0xFFFF00),
        /** 这一刻的 ESPE 不够（黄） */
        NO_ESPE(0xFFFF00);

        private static final Status[] VALUES = values();

        public final int color;

        Status(int color) {
            this.color = color;
        }

        public static Status byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : NO_TARGET;
        }
    }

    public static final int SLOT_GEM = 0;
    public static final int FIRST_OUTPUT = 1;
    public static final int OUTPUT_SLOTS = 9;
    public static final int SLOTS = FIRST_OUTPUT + OUTPUT_SLOTS;

    /** 停用、没有主人、没选 / 不收的目标、没有地脉、名额：多少 tick 再判一次（名额和地脉的结果也缓存这么久） */
    public static final int STATIC_RECHECK = 100;
    /** 红石、输出满、缺 MRU / ESPE：多少 tick 再判一次 */
    public static final int IDLE_RECHECK = 10;
    /** 抽 ESPE 的间隔 */
    public static final int ESPE_PULL_INTERVAL = 10;
    /** 每工作多少 tick 泄漏一次 */
    public static final int LEAK_INTERVAL = 20;
    /** 全服名额表里，多少 tick 内 tick 过的才算「在运转」 */
    public static final int ALIVE_TICKS = 40;
    /** 光束从机器往下画多少格（渲染盒按它算，同投影仪） */
    public static final int BEAM_LENGTH = 16;

    /** ESPE 源的位置：同一高度、隔一格一圈（四正四斜） */
    public static final List<Vec3i> SOURCES = List.of(
            new Vec3i(2, 0, 0), new Vec3i(-2, 0, 0), new Vec3i(0, 0, 2), new Vec3i(0, 0, -2),
            new Vec3i(2, 0, 2), new Vec3i(-2, 0, 2), new Vec3i(2, 0, -2), new Vec3i(-2, 0, -2));

    /** 占名额的状态（见类注释「台数」） */
    private static final Set<Status> CLAIMING = EnumSet.of(Status.WORKING, Status.POWERED, Status.OUTPUT_FULL, Status.NO_MRU, Status.NO_ESPE);

    public static final String TAG_PLACED = "placed_tick";
    public static final String TAG_PROGRESS = "progress";
    public static final String TAG_TARGET = "target";
    /** 只在更新包里（给客户端画影像和光束），存档里没有 */
    private static final String TAG_WORKING = "working";

    /** 加载着的凝聚器（只在服务端线程读写；弱引用，漏了移除也不会拖住方块实体） */
    private static final Set<LeyCondenserBlockEntity> LOADED = Collections.newSetFromMap(new WeakHashMap<>());
    /** 哪些目标因为有燃烧时间被当成「不收」记过日志（全服只记一次） */
    private static final Set<Target> WARNED_FUEL = EnumSet.noneOf(Target.class);

    /** 放下的游戏时间；没有记录是 −1（第一次服务端 tick 补成当前时间） */
    private long placedTick = -1L;
    private int progress;
    @Nullable
    private Target target;

    private Status status = Status.NO_TARGET;
    /** 判过状态没有（还没判过的在全服名额表里先算占着名额，免得同一刻加载的一批里后放的抢先） */
    private boolean evaluated;
    /** progress == 0 时，游戏时间到这里之前不重新判状态（节流，不存盘） */
    private long nextEval;
    /** 最近一次服务端 tick 的游戏时间（全服名额表判断「在运转」用） */
    private long lastTick = Long.MIN_VALUE;
    private boolean listed;
    /** 名额的缓存：到这个时间重新数；结果是 null（没挡住）/ DUPLICATE / OWNER_LIMIT */
    private long limitsCheckAt;
    @Nullable
    private Status limitResult;
    /** 挡路的那台（DUPLICATE 时） */
    @Nullable
    private BlockPos blocker;
    private long leyCheckAt;
    private boolean leyCached;
    private long nextEspePull;
    /** 燃烧时间的缓存（查一次会发一个 Forge 的燃料事件，不每 tick 查）：哪种目标、查到什么、到什么时候重查 */
    @Nullable
    private Target fuelTarget;
    private boolean fuelResult;
    private long fuelCheckAt;
    private int leakCounter;
    /** 服务端：上次告诉客户端的；客户端：收到的（画影像和光束） */
    private boolean working;
    /** 凝出过几个（不存盘，给 GameTest 和排查用） */
    private int produced;

    // GameTest 钩子：实例字段、不存盘（测试并行跑，不能用静态覆盖，也不改真配置）
    @Nullable
    private Map<Target, int[]> costForTest;
    @Nullable
    private Boolean leyForTest;
    private int perChunkForTest = -1;
    private int perOwnerForTest = -1;

    public LeyCondenserBlockEntity(BlockPos pos, BlockState state) {
        super(ECLeyCondenser.LEY_CONDENSER_BE.get(), pos, state, () -> ECConfig.get(Config.maxMRU), SLOTS,
                () -> ECConfig.get(Config.maxESPE), 0);
    }

    // ------------------------------------------------------------------
    // 槽位、ESPE、同步开关
    // ------------------------------------------------------------------

    /** 槽位规则（两端共用）：0 号绑定宝石，1~9 输出（谁都不能往里放，机器自己用 setStackInSlot 写） */
    public static boolean slotRule(int slot, ItemStack stack) {
        return slot == SLOT_GEM && defaultSlotRule(slot, stack);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slotRule(slot, stack);
    }

    @Override
    protected boolean canAutomationInsert(int slot, ItemStack stack) {
        return slot == SLOT_GEM && super.canAutomationInsert(slot, stack);
    }

    @Override
    protected boolean isOutputSlot(int slot) {
        return slot >= FIRST_OUTPUT && slot < SLOTS;
    }

    /** 只进不出：别的机器抽不走它的 ESPE（同米斯莱熔炉） */
    @Override
    protected ESPEAccess espeAccess(@Nullable Direction side) {
        return ESPEAccess.INSERT;
    }

    @Override
    protected boolean syncInventoryToClient() {
        return false;
    }

    @Override
    protected boolean syncBalanceToClient() {
        return false;
    }

    /** 这台机器一定开了 ESPE（7 参构造器） */
    private ESPEStorage espeStorage() {
        ESPEStorage storage = getESPEStorage();
        if (storage == null) {
            throw new IllegalStateException("地脉凝聚器没有 ESPE 存储");
        }
        return storage;
    }

    // ------------------------------------------------------------------
    // 价格（配置或测试钩子）
    // ------------------------------------------------------------------

    public int mruCost(Target target) {
        int[] test = costForTest == null ? null : costForTest.get(target);
        return test != null ? test[0] : Config.mru(target);
    }

    public int espeCost(Target target) {
        int[] test = costForTest == null ? null : costForTest.get(target);
        return test != null ? test[1] : Config.espe(target);
    }

    public int ticksFor(Target target) {
        int[] test = costForTest == null ? null : costForTest.get(target);
        return test != null ? test[2] : Config.ticks(target);
    }

    /** 这一种收不收：用时不是 0、产物没有燃烧时间 */
    public boolean targetEnabled(Target target) {
        return ticksFor(target) > 0 && !isFuel(target);
    }

    /** 同 {@link #targetEnabled}，燃烧时间的结果缓存 {@value #STATIC_RECHECK} 刻（服务端每 tick 判的地方用） */
    private boolean targetEnabledCached(Target target, long now) {
        if (ticksFor(target) <= 0) {
            return false;
        }
        if (target != fuelTarget || now >= fuelCheckAt) {
            fuelTarget = target;
            fuelResult = isFuel(target);
            fuelCheckAt = now + STATIC_RECHECK;
        }
        return !fuelResult;
    }

    /** 产物有没有燃烧时间（别的模组可能把它改成燃料）；有的话当成「不收」，全服记一次日志 */
    public static boolean isFuel(Target target) {
        if (ForgeHooks.getBurnTime(new ItemStack(target.product), null) <= 0) {
            return false;
        }
        synchronized (WARNED_FUEL) {
            if (WARNED_FUEL.add(target)) {
                EssentialCraft5.LOGGER.warn("[地脉凝聚器] {} 现在有燃烧时间（别的模组改的），凝聚器不收这一种，免得被烧回 MRU", target.product);
            }
        }
        return true;
    }

    /** 从进度 p 走到 p+1 这一步要付的量：⌊total·(p+1)/T⌋ − ⌊total·p/T⌋，走满正好 total */
    public static long stepCost(long total, int ticks, int p) {
        if (total <= 0 || ticks <= 0) {
            return 0L;
        }
        return total * (p + 1) / ticks - total * p / ticks;
    }

    /** 一步最多要付多少（⌈total / T⌉），用来判断容量够不够 */
    private static long maxStep(long total, int ticks) {
        return total <= 0 || ticks <= 0 ? 0L : (total + ticks - 1) / ticks;
    }

    // ------------------------------------------------------------------
    // 工作
    // ------------------------------------------------------------------

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            lastTick = server.getGameTime();
            list();
            // 还没判过状态，在名额表里先算占着名额：后放的当场重数（区块重新加载时，更早的这台把名额拿回去）
            claimStarted();
        }
    }

    @Override
    protected void serverTick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        long now = server.getGameTime();
        // 上一刻已经确定占着名额（判过状态、一直在 tick、状态是占名额的那几种）；否则这一刻开始占的话要叫后放的重数
        boolean wasClaiming = evaluated && now - lastTick <= ALIVE_TICKS && CLAIMING.contains(status);
        lastTick = now;
        tickBody(server, now);
        if (!wasClaiming && claimsSlot()) {
            claimStarted();
        }
    }

    private void tickBody(ServerLevel server, long now) {
        if (!listed) {
            list();
        }
        if (placedTick < 0) {
            placedTick = now;
            server.blockEntityChanged(worldPosition);
        }
        Target current = target;
        if (progress == 0) {
            if (now < nextEval) {
                updateWorking();
                return;
            }
            status = evaluate(server, current, now);
            evaluated = true;
            if (status != Status.WORKING) {
                nextEval = now + switch (status) {
                    case DISABLED, NO_OWNER, NO_TARGET, TARGET_OFF, NO_LEY, DUPLICATE, OWNER_LIMIT -> STATIC_RECHECK;
                    default -> IDLE_RECHECK;
                };
                updateWorking();
                return;
            }
        } else {
            Status hold = holdWhileRunning(server, current, now);
            // 读档时做到一半的机器从这里开始判状态；不记的话，被名额挡着时还一直算「没判过、占着名额」
            evaluated = true;
            if (hold != null) {
                status = hold;
                updateWorking();
                return;
            }
        }
        // current 一定不为 null（evaluate / holdWhileRunning 都查过）
        int ticks = ticksFor(current);
        if (progress >= ticks) {
            // 配置把用时改短了：进度已经够，直接出货
            deliver(server, current);
            updateWorking();
            return;
        }
        if (espeCost(current) > 0) {
            pullESPE(server, now);
        }
        long mruStep = stepCost(mruCost(current), ticks, progress);
        long espeStep = stepCost(espeCost(current), ticks, progress);
        if (mru.getMRU() < mruStep) {
            status = Status.NO_MRU;
            updateWorking();
            return;
        }
        ESPEStorage storage = espeStorage();
        if (espeStep > 0 && storage.getESPE() < espeStep - 1e-6) {
            status = Status.NO_ESPE;
            updateWorking();
            return;
        }
        if (mruStep > 0) {
            mru.extractMRU((int) mruStep, true);
        }
        if (espeStep > 0) {
            storage.extractESPE(espeStep, true);
        }
        progress++;
        status = Status.WORKING;
        if (++leakCounter >= LEAK_INTERVAL) {
            leakCounter = 0;
            if (ECConfig.get(Config.generatesCorruption)) {
                ECCorruption.randomIncreaseAt(server, worldPosition, server.random, ECConfig.get(Config.maxCorruptionGen));
            }
        }
        if (progress >= ticks) {
            deliver(server, current);
        }
        // 进度每 tick 都变：只标记区块要存盘，不像 setChanged 那样通知比较器
        server.blockEntityChanged(worldPosition);
        updateWorking();
    }

    /** 走满了：往输出格放 1 个，放成功进度归 0；放不下（正常不会：开工前确认过）停在满进度 */
    private void deliver(ServerLevel server, Target current) {
        ItemStack product = new ItemStack(current.product);
        if (insertOutput(product, true)) {
            insertOutput(product, false);
            progress = 0;
            produced++;
            status = Status.WORKING;
            server.blockEntityChanged(worldPosition);
        } else {
            status = Status.OUTPUT_FULL;
        }
    }

    /** 开始一个之前判状态（顺序即 {@link Status} 的顺序）；DUPLICATE 时顺手记下挡路的那台 */
    private Status evaluate(ServerLevel server, @Nullable Target current, long now) {
        if (!ECConfig.get(Config.enabled)) {
            return Status.DISABLED;
        }
        UUID owner = ECProtection.actingOwner(this);
        if (owner == null) {
            return Status.NO_OWNER;
        }
        if (current == null) {
            return Status.NO_TARGET;
        }
        if (!targetEnabledCached(current, now)) {
            return Status.TARGET_OFF;
        }
        if (current.needsLey && !hasLey(server, now)) {
            return Status.NO_LEY;
        }
        Status limit = limits(server, owner, now);
        if (limit != null) {
            return limit;
        }
        if (server.hasNeighborSignal(worldPosition)) {
            return Status.POWERED;
        }
        if (!insertOutput(new ItemStack(current.product), true)) {
            return Status.OUTPUT_FULL;
        }
        int ticks = ticksFor(current);
        ESPEStorage storage = espeStorage();
        if (maxStep(mruCost(current), ticks) > mru.getMaxMRU() || maxStep(espeCost(current), ticks) > storage.getMaxESPE()) {
            return Status.TOO_EXPENSIVE;
        }
        if (mru.getMRU() < stepCost(mruCost(current), ticks, 0)) {
            return Status.NO_MRU;
        }
        long espeStep = stepCost(espeCost(current), ticks, 0);
        if (espeStep > 0) {
            pullESPE(server, now);
            if (storage.getESPE() < espeStep - 1e-6) {
                return Status.NO_ESPE;
            }
        }
        return Status.WORKING;
    }

    /**
     * 做到一半时每 tick 查的：停用、目标没了 / 不收了、没有地脉、配置改得超过容量、名额（缓存）、红石；都没事返回 null。
     * 返回非 null 时进度停在原处、这一 tick 不扣。主人在开工时查过，做到一半不会变成「没有主人」，名额重数时才再取一次。
     */
    @Nullable
    private Status holdWhileRunning(ServerLevel server, @Nullable Target current, long now) {
        if (!ECConfig.get(Config.enabled)) {
            return Status.DISABLED;
        }
        if (current == null) {
            return Status.NO_TARGET;
        }
        if (!targetEnabledCached(current, now)) {
            return Status.TARGET_OFF;
        }
        if (current.needsLey && !hasLey(server, now)) {
            return Status.NO_LEY;
        }
        int ticks = ticksFor(current);
        if (maxStep(mruCost(current), ticks) > mru.getMaxMRU() || maxStep(espeCost(current), ticks) > espeStorage().getMaxESPE()) {
            return Status.TOO_EXPENSIVE;
        }
        if (now >= limitsCheckAt) {
            UUID owner = ECProtection.actingOwner(this);
            if (owner == null) {
                return Status.NO_OWNER;
            }
            limits(server, owner, now);
        }
        if (limitResult != null) {
            return limitResult;
        }
        if (server.hasNeighborSignal(worldPosition)) {
            return Status.POWERED;
        }
        return null;
    }

    /** 这个维度有没有地脉（投影仪的矿石表），缓存 {@value #STATIC_RECHECK} 刻 */
    private boolean hasLey(ServerLevel server, long now) {
        if (leyForTest != null) {
            return leyForTest;
        }
        if (now >= leyCheckAt) {
            leyCached = LeyProjectorBlockEntity.hasLey(server);
            leyCheckAt = now + STATIC_RECHECK;
        }
        return leyCached;
    }

    /** 名额（每人每区块、每人全服），缓存 {@value #STATIC_RECHECK} 刻：返回 null / DUPLICATE / OWNER_LIMIT */
    @Nullable
    private Status limits(ServerLevel server, UUID owner, long now) {
        if (now >= limitsCheckAt) {
            limitsCheckAt = now + STATIC_RECHECK;
            blocker = findBlocker(server, owner);
            if (blocker != null) {
                limitResult = Status.DUPLICATE;
            } else {
                int perOwner = perOwnerForTest >= 0 ? perOwnerForTest : ECConfig.get(Config.maxPerOwner);
                limitResult = perOwner > 0 && earlierClaimers(owner, now) >= perOwner ? Status.OWNER_LIMIT : null;
            }
        }
        return limitResult;
    }

    /** 本区块里同主人、比自己早放下的凝聚器够不够每区块的台数：够了返回最早那台的坐标，否则 null（同投影仪 findBlocker） */
    @Nullable
    private BlockPos findBlocker(ServerLevel server, UUID owner) {
        int limit = perChunkForTest > 0 ? perChunkForTest : ECConfig.get(Config.maxPerOwnerPerChunk);
        int older = 0;
        LeyCondenserBlockEntity oldest = null;
        for (BlockEntity other : server.getChunkAt(worldPosition).getBlockEntities().values()) {
            if (other == this || !(other instanceof LeyCondenserBlockEntity condenser) || condenser.isRemoved()) {
                continue;
            }
            if (!placedBefore(condenser, this) || !owner.equals(ECProtection.actingOwner(condenser))) {
                continue;
            }
            older++;
            if (oldest == null || placedBefore(condenser, oldest)) {
                oldest = condenser;
            }
        }
        return older >= limit && oldest != null ? oldest.getBlockPos() : null;
    }

    /** 全服同主人、比自己早放下、占着名额的凝聚器有几台；顺手清掉表里已移除、不在运转的 */
    private int earlierClaimers(UUID owner, long now) {
        int count = 0;
        Iterator<LeyCondenserBlockEntity> it = LOADED.iterator();
        while (it.hasNext()) {
            LeyCondenserBlockEntity other = it.next();
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

    /** 占不占全服名额：还没判过状态，或者状态是「开着、只是暂时停」的那几种 */
    private boolean claimsSlot() {
        return !evaluated || CLAIMING.contains(status);
    }

    /**
     * 这台刚开始占名额：同主人、比它晚放下的几台把名额缓存作废，下一次 tick 就重数（做到一半的走 {@link #holdWhileRunning}，
     * 两件之间的走 {@link #evaluate}）。只在翻转时调，遍历一次全服表。刚放下、还没记放下时间的按最晚算，没有比它晚的。
     */
    private void claimStarted() {
        UUID owner = ECProtection.actingOwner(this);
        if (owner == null) {
            return;
        }
        for (LeyCondenserBlockEntity other : LOADED) {
            if (other != this && !other.isRemoved() && other.level instanceof ServerLevel && placedBefore(this, other)
                    && owner.equals(ECProtection.actingOwner(other))) {
                other.limitsCheckAt = 0L;
            }
        }
    }

    /** a 是否比 b 早放下：先比放下的时间（没有记录的排最后），相同比坐标 asLong，小的算早（同投影仪） */
    static boolean placedBefore(LeyCondenserBlockEntity a, LeyCondenserBlockEntity b) {
        long ta = a.placedTick < 0 ? Long.MAX_VALUE : a.placedTick;
        long tb = b.placedTick < 0 ? Long.MAX_VALUE : b.placedTick;
        if (ta != tb) {
            return ta < tb;
        }
        return a.worldPosition.asLong() < b.worldPosition.asLong();
    }

    /** 每 {@value #ESPE_PULL_INTERVAL} 刻从 8 个固定位置抽一次 ESPE（满了不抽） */
    private void pullESPE(ServerLevel server, long now) {
        ESPEStorage storage = espeStorage();
        if (now < nextEspePull || storage.getESPE() >= storage.getMaxESPE()) {
            return;
        }
        nextEspePull = now + ESPE_PULL_INTERVAL;
        ESPETransfer.pullFromAround(server, worldPosition, storage, SOURCES);
    }

    /**
     * 往 1~9 号格放（先并进同物品的格子、再放空格）。simulate 时只看放不放得下。
     * 用 setStackInSlot 写回（输出格的槽位规则不收任何东西，insertItem 放不进去；同投影仪）。
     */
    private boolean insertOutput(ItemStack stack, boolean simulate) {
        int empty = -1;
        for (int slot = FIRST_OUTPUT; slot < SLOTS; slot++) {
            ItemStack out = inventory.getStackInSlot(slot);
            if (out.isEmpty()) {
                if (empty < 0) {
                    empty = slot;
                }
                continue;
            }
            int limit = Math.min(inventory.getSlotLimit(slot), out.getMaxStackSize());
            if (ItemStack.isSameItemSameTags(out, stack) && out.getCount() + stack.getCount() <= limit) {
                if (!simulate) {
                    inventory.setStackInSlot(slot, out.copyWithCount(out.getCount() + stack.getCount()));
                }
                return true;
            }
        }
        if (empty < 0) {
            return false;
        }
        if (!simulate) {
            inventory.setStackInSlot(empty, stack.copy());
        }
        return true;
    }

    private void updateWorking() {
        // 缺 MRU / ESPE 时一步一步往前挪也算在凝（画面不一闪一闪）
        boolean now = status == Status.WORKING || progress > 0 && (status == Status.NO_MRU || status == Status.NO_ESPE);
        if (now != working) {
            working = now;
            markClientDirty();
        }
    }

    private void list() {
        LOADED.add(this);
        listed = true;
    }

    private void unlist() {
        if (level != null && !level.isClientSide && listed) {
            LOADED.remove(this);
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
    // 客户端：凝下界之星时柱顶飘向机器的粒子
    // ------------------------------------------------------------------

    @Override
    protected void clientTick() {
        if (level == null || !working || target != Target.NETHER_STAR || innerRotation % 8 != 0) {
            return;
        }
        double cx = worldPosition.getX() + 0.5;
        double cy = worldPosition.getY() + 0.5;
        double cz = worldPosition.getZ() + 0.5;
        if (level.getNearestPlayer(cx, cy, cz, 16D, false) == null) {
            return;
        }
        Vec3i offset = SOURCES.get(level.random.nextInt(SOURCES.size()));
        BlockPos pillar = worldPosition.offset(offset);
        BlockEntity source = level.getBlockEntity(pillar);
        if (!(source instanceof MithrilineCrystalPillarBlockEntity)) {
            return;
        }
        // 传送门粒子从「起点 + 速度」飘回起点（y 另外从 +1 落到 0）：起点放机器中心，「速度」取柱顶相对机器的位置，起飞点正好在柱顶
        level.addParticle(ParticleTypes.PORTAL, cx, cy, cz, offset.getX(), 1.6D, offset.getZ());
    }

    // ------------------------------------------------------------------
    // 访问（界面、方块、测试）
    // ------------------------------------------------------------------

    /** 放下的时间（方块的 setPlacedBy 调，Ctrl+中键复制带着旧时间的也改成「现在」）；下一 tick 重新判状态、重新数名额 */
    public void markPlaced(long gameTime) {
        placedTick = gameTime;
        nextEval = 0L;
        limitsCheckAt = 0L;
        setChanged();
    }

    /** 选目标（null = 取消）。换了就清空进度，已经扣掉的不退 */
    public void setTarget(@Nullable Target next) {
        if (next == target) {
            return;
        }
        target = next;
        progress = 0;
        leakCounter = 0;
        nextEval = 0L;
        markClientDirty();
        setChanged();
    }

    @Nullable
    public Target target() {
        return target;
    }

    public long placedTick() {
        return placedTick;
    }

    public Status status() {
        return status;
    }

    public int progress() {
        return progress;
    }

    /** 当前目标每个要多少 tick（没选目标为 0） */
    public int maxProgress() {
        return target == null ? 0 : ticksFor(target);
    }

    /** 界面用：这个维度有没有地脉（服务端缓存的结果；测试钩子优先） */
    public boolean hasLeyForMenu() {
        if (leyForTest != null) {
            return leyForTest;
        }
        return level instanceof ServerLevel server && hasLey(server, server.getGameTime());
    }

    /** DUPLICATE 时挡路的那台；否则 null */
    @Nullable
    public BlockPos blocker() {
        return status == Status.DUPLICATE ? blocker : null;
    }

    /** 凝出过几个（不存盘） */
    public int producedCount() {
        return produced;
    }

    /** 客户端：在不在凝（画影像转动和光束） */
    public boolean isWorking() {
        return working;
    }

    /** 加载着的凝聚器表里有没有自己（GameTest 用） */
    public boolean isListed() {
        return LOADED.contains(this);
    }

    /** GameTest：这一种的价格换成 (mru, espe, ticks)；mru 为负恢复配置 */
    public void setCostForTest(Target which, int mruCost, int espeCostValue, int ticks) {
        if (costForTest == null) {
            costForTest = new EnumMap<>(Target.class);
        }
        if (mruCost < 0) {
            costForTest.remove(which);
        } else {
            costForTest.put(which, new int[] {mruCost, espeCostValue, ticks});
        }
        nextEval = 0L;
    }

    /** GameTest：地脉有没有（null 恢复按维度判） */
    public void setLeyForTest(@Nullable Boolean ley) {
        leyForTest = ley;
        nextEval = 0L;
    }

    /** GameTest：每区块、全服的台数（负数恢复配置） */
    public void setLimitsForTest(int perChunk, int perOwner) {
        perChunkForTest = perChunk;
        perOwnerForTest = perOwner;
        nextEval = 0L;
        limitsCheckAt = 0L;
    }

    // ------------------------------------------------------------------
    // 存盘、同步
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong(TAG_PLACED, placedTick);
        tag.putInt(TAG_PROGRESS, progress);
        if (target != null) {
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(target.product);
            if (key != null) {
                tag.putString(TAG_TARGET, key.toString());
            }
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        placedTick = tag.contains(TAG_PLACED, Tag.TAG_LONG) ? tag.getLong(TAG_PLACED) : -1L;
        progress = Math.max(0, tag.getInt(TAG_PROGRESS));
        target = tag.contains(TAG_TARGET, Tag.TAG_STRING) ? Target.byProduct(ResourceLocation.tryParse(tag.getString(TAG_TARGET))) : null;
        if (target == null) {
            progress = 0;
        }
        // 只有发给客户端的更新包里有这个键（存档里没有）
        if (tag.contains(TAG_WORKING)) {
            working = tag.getBoolean(TAG_WORKING);
        }
        nextEval = 0L;
        limitsCheckAt = 0L;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.remove(TAG_PLACED);
        tag.remove(TAG_PROGRESS);
        tag.putBoolean(TAG_WORKING, working);
        return tag;
    }

    /** 头顶的影像（+2 格）、往下的光束（{@value #BEAM_LENGTH} 格） */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition.getX(), worldPosition.getY() - BEAM_LENGTH, worldPosition.getZ(),
                worldPosition.getX() + 1, worldPosition.getY() + 2, worldPosition.getZ() + 1);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new LeyCondenserMenu(id, inventory, this);
    }

    /** 服务器停止时清空名额表（单人换存档不带过去） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
    public static final class LeyCondenserEvents {

        private LeyCondenserEvents() {
        }

        @SubscribeEvent
        public static void leyCondenserServerStopped(ServerStoppedEvent event) {
            for (LeyCondenserBlockEntity condenser : LOADED) {
                condenser.listed = false;
            }
            LOADED.clear();
        }
    }

    // ------------------------------------------------------------------ 声音和粒子

    @Override
    protected boolean ambientWorking() {
        return status == Status.WORKING;
    }

    @Override
    protected MachineAmbience.Profile ambience() {
        return MachineAmbiences.LEY;
    }
}
