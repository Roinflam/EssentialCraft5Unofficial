package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.LeyProjectorMenu;
import com.flameforge.essentialcraft5.mru.ECCorruption;
import com.flameforge.essentialcraft5.registry.ECLeyProjector;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootDataManager;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.Tags;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * 地脉投影仪（EC5 新机器，第五批 batch5.md §3.4、policy §4.8）：原作魔力采石场（TileMagicalQuarry）的继承者。
 * <p>
 * <b>不读也不写世界方块、不碰实体、不发 Break / Place 事件、不加载区块</b>：每 {@code ticks_per_block} tick 按所在维度的「投影表」
 * （战利品表，{@code essentialcraft5:ley_projector/ores/<维度 ns>/<维度 path>}）掷一次，产物进机器自己的 9 个输出格。
 * 唯一读到的世界数据是自己所在区块的方块实体表（「每人每区块」判定）。主人离线照常工作（不动世界，同岩浆冶炼炉、反应堆），只在区块已加载时 tick。
 * <p>
 * 继承原作的：容量 5 000（原作 L48）、红石有信号就停（L91）、每格腐化泄漏 0~2（L300~302）；速度、每格 MRU = 原作不装升级的平均值（20 tick、60 MRU）。
 * 原作的挖掘、吸掉落物、坐标包、7 种升级都不移植（2026-09-28 起机器不挖不放方块）。
 *
 * <h2>一格怎么投影</h2>
 * <ol>
 *   <li>{@code progress == 0} 时判状态（顺序见 {@link Status}）；工作就<b>一次扣完这一格的 MRU</b>（{@code min(mru_per_block, max_mru)}），进度 +1。
 *       之后每 tick 进度 +1（有红石信号就停在原处、进度保留），到 {@code ticks_per_block} 投影、进度归 0。
 *       所以跑了 n tick 投影了 ⌊n / T⌋ 格、扣了 ⌈n / T⌉ 格的 MRU（最后一格已经开头扣了）。</li>
 *   <li>找表：先按维度 id，找不到再按维度类型（{@code dimensionTypeId()}，混合端的额外世界、用主世界类型的挖矿维度也有地脉；D4 A ②），都没有 = 「这里没有地脉」。
 *       用 {@code getElementOptional} 而不是 {@code getLootTable}：后者找不到时返回空表（{@code LootDataResolver} L25~27），分不出「没表」和「空表」。</li>
 *   <li>参数：ORIGIN = 机器中心，TOOL = 一把虚拟铁镐（透镜格有东西时带时运 I），参数集 FISHING（表的 type 也是 fishing，只要求 ORIGIN、TOOL）。</li>
 *   <li>掷表用 {@link LootTable#getRandomItemsRaw}，<b>不走 {@code ForgeHooks.modifyLoot}</b>：别的模组挂在矿石 / 所有方块上的全局战利品修改器
 *       （额外掉落、自动冶炼、时运加成）不该在一格都没挖的情况下触发。残余风险：给<b>所有</b>表加池的 {@code LootTableLoadEvent} 监听器
 *       会影响投影表（表是数据，整合包自己负责）；下面的运行时过滤兜住燃料和矿石方块。</li>
 *   <li>运行时过滤：丢掉有燃烧时间的（防热力产能器把投影的煤烧回 MRU）和 {@code #forge:ores} 里的（防矿石方块进岩浆冶炼炉翻倍，数据包往表里加了也不行），
 *       每台只记一次日志。什么都没剩、「保留岩石」开着 → 另掷岩石表（{@code ley_projector/rock/…}），同样过滤。</li>
 *   <li>矿（矿石表的产物）按最大堆叠拆开，先并进同种的输出格、再放空格；放不下的记进「待放」（存盘，最多 {@value #MAX_PENDING} 堆，超出丢掉并记一次日志），
 *       待放不空时不开始下一格（「输出格已满」），每 tick 先把待放往输出格里塞。</li>
 *   <li>岩石（岩石表的产物，审查 LEY-1）<b>永远不进待放、不会让机器停</b>：每种岩石最多占一格、只并进自己那格，
 *       岩石合计最多占 {@value #MAX_ROCK_SLOTS} 格（其余至少 7 格留给矿），放不下的直接丢掉。哪些物品算岩石记在 {@code rock_kinds}
 *       （掷岩石表掷出过的物品种类，存盘，最多 {@value #MAX_ROCK_KINDS} 种），读档后照样认得出输出格里的岩石。
 *       以前「保留岩石」开着时岩石能占满 9 格、再堆满待放，机器就一直停在「输出格已满」，矿也进不来。</li>
 * </ol>
 *
 * <h2>每人每区块（policy §4.8）</h2>
 * 同一区块里、同一主人（{@link ECProtection#actingOwner}）的投影仪只有最早放下的 {@code max_per_owner_per_chunk} 台工作。
 * 不建全局表：每次开始一格前遍历自己所在区块的方块实体表，数「同主人、比自己早」的有几台（先比放下的游戏时间，
 * 没有记录的（/setblock、结构放的，第一次 tick 前）排最后；相同比坐标 {@code asLong}，小的算早——/clone 出来的副本按坐标决出）。
 * 拆掉、区块卸载、/clone、换存档都不用释放。被挡住的每 {@value #DUPLICATE_RECHECK} tick 查一次。区块进入 tick 前待建方块实体已全部建好，不会漏看。
 * <p>
 * <b>专用服上没有主人的不工作</b>（D4 A ①）：/setblock、结构、别的模组或假玩家放的，界面显示「没有主人」——否则等于每区块多一个「无主」名额，
 * 用假玩家放置就能绕开「每人一台」。单人和开了局域网的整合服里没记主人的算房主（{@code actingOwner}，policy §2.3a）。
 *
 * <h2>槽位</h2>
 * 0 绑定宝石；1 透镜（{@link ECLeyProjector#LENSES}，界面上限 1 个，不消耗）；2~10 输出。漏斗、管道只能往 0 号格插（绑定宝石），只能从 2~10 号格取。
 *
 * <h2>同步</h2>
 * 物品栏、平衡值都不发给附近的客户端（界面走菜单同步）；更新包里只带「在不在投影」（画光束），翻转时 {@link #markClientDirty()}（框架节流 10 tick）。
 * 待放的产物、进度、放下的时间、岩石种类不进更新包。
 */
public class LeyProjectorBlockEntity extends MRUBlockEntity {

    /** 配置 machines.ley_projector（policy §4.8） */
    public static final class Config {
        public static ForgeConfigSpec.BooleanValue enabled;
        public static ForgeConfigSpec.IntValue maxMRU;
        public static ForgeConfigSpec.IntValue ticksPerBlock;
        public static ForgeConfigSpec.IntValue mruPerBlock;
        public static ForgeConfigSpec.IntValue maxPerOwnerPerChunk;
        public static ForgeConfigSpec.BooleanValue generatesCorruption;
        public static ForgeConfigSpec.IntValue maxCorruptionGen;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("ley_projector");
            enabled = builder.comment("Whether ley projectors work at all.", "地脉投影仪是否工作")
                    .define("enabled", true);
            maxMRU = builder.comment("MRU capacity.", "MRU 容量")
                    .defineInRange("max_mru", ECConfig.DEVICE_MAX_MRU, 1, Integer.MAX_VALUE);
            ticksPerBlock = builder.comment("Ticks needed to project one block of rock layer (20 ticks = 1 second).",
                            "投影一格岩层要多少 tick（20 tick = 1 秒）")
                    .defineInRange("ticks_per_block", 20, 1, 1200);
            mruPerBlock = builder.comment("MRU used per projected block, taken all at once when the block starts. Values above max_mru use max_mru.",
                            "每投影一格用多少 MRU（开始投影这一格时一次扣完）；比 max_mru 大时按 max_mru 扣")
                    .defineInRange("mru_per_block", 60, 0, 1_000_000);
            maxPerOwnerPerChunk = builder.comment("How many projectors of the same owner work in one chunk (the earliest placed ones; the rest wait).",
                            "同一区块里同一个主人有几台投影仪能工作（按放下的先后取最早的几台，其余的等着）")
                    .defineInRange("max_per_owner_per_chunk", 1, 1, 64);
            generatesCorruption = builder.comment("Whether the projector leaks MRU into nearby MRUCUs while working.",
                            "工作时是否向周围泄漏 MRU")
                    .define("generate_corruption", true);
            maxCorruptionGen = builder.comment("Max MRU leaked per projected block (a random amount from 0 to this). The original config comment said per tick, which was wrong.",
                            "每投影一格最多泄漏多少 MRU（在 0 到这个值之间随机；原作配置注释写「每 tick」是错的）")
                    .defineInRange("max_corruption_gen", 2, 0, 10000);
            builder.pop();
        }
    }

    /**
     * 界面状态（同步给界面用 ordinal，只许在末尾追加）。开始一格前按 DISABLED → NO_OWNER → NO_LEY → DUPLICATE → POWERED → OUTPUT_FULL → NO_MRU 的顺序判断，
     * 都不是就 WORKING（{@link #evaluate}）。
     */
    public enum Status {
        /** 正在投影（绿） */
        WORKING(0x00FF00),
        /** 配置 enabled = false（红） */
        DISABLED(0xFF0000),
        /** 专用服上没有主人（红） */
        NO_OWNER(0xFF0000),
        /** 这个维度没有投影表（红） */
        NO_LEY(0xFF0000),
        /** 同区块同主人更早的已占满名额（橙：在等） */
        DUPLICATE(0xFFAA00),
        /** 红石信号（橙：暂停） */
        POWERED(0xFFAA00),
        /** 待放的产物没放完（黄：缺地方） */
        OUTPUT_FULL(0xFFFF00),
        /** MRU 不够一格（黄） */
        NO_MRU(0xFFFF00);

        private static final Status[] VALUES = values();

        public final int color;

        Status(int color) {
            this.color = color;
        }

        public static Status byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : NO_MRU;
        }
    }

    public static final int SLOT_GEM = 0;
    public static final int SLOT_LENS = 1;
    public static final int FIRST_OUTPUT = 2;
    public static final int OUTPUT_SLOTS = 9;
    public static final int SLOTS = FIRST_OUTPUT + OUTPUT_SLOTS;

    /** 待放的产物最多几堆（正常一格最多一两堆；这是给数据包写出一次掷很多的表时兜底） */
    public static final int MAX_PENDING = 27;
    /** 岩石合计最多占几个输出格（LEY-1；每种岩石另外最多一格） */
    public static final int MAX_ROCK_SLOTS = 2;
    /** 记住的岩石种类上限（岩石表正常 2 种；数据包写很多种时兜底，超出的新种类照样按岩石放，只是不记） */
    public static final int MAX_ROCK_KINDS = 32;
    /** 被同主人更早的挡住时，多少 tick 再查一次 */
    public static final int DUPLICATE_RECHECK = 100;
    /** 没有主人、没有地脉、停用：多少 tick 再查一次（数据包重载、配置热改后最多等这么久） */
    public static final int STATIC_RECHECK = 100;
    /** 红石、MRU 不足、输出满：多少 tick 再查一次 */
    public static final int IDLE_RECHECK = 10;
    /** 光束从机器往下画多少格（渲染盒按它算） */
    public static final int BEAM_LENGTH = 16;

    /** 投影表目录（战利品表 id 的路径前缀），后面接维度的 ns/path */
    public static final String ORES_DIR = "ley_projector/ores/";
    /** 岩石表目录（「保留岩石」开着时用） */
    public static final String ROCK_DIR = "ley_projector/rock/";

    public static final String TAG_PLACED = "placed_tick";
    public static final String TAG_PROGRESS = "progress";
    public static final String TAG_KEEP_ROCK = "keep_rock";
    public static final String TAG_PENDING = "pending";
    /** 掷岩石表掷出过的物品种类（注册名列表），认输出格里哪些是岩石 */
    public static final String TAG_ROCK_KINDS = "rock_kinds";
    /** 只在更新包里（给客户端画光束），存档里没有 */
    private static final String TAG_WORKING = "working";

    /** 虚拟工具：两个堆叠只读（投影表里只有 apply_bonus / fortune_scaled_count 读 TOOL 的附魔），用到才建 */
    @Nullable
    private static ItemStack plainTool;
    @Nullable
    private static ItemStack fortuneTool;

    /** 放下的游戏时间；没有记录是 −1（第一次服务端 tick 补成当前时间） */
    private long placedTick = -1L;
    private int progress;
    private boolean keepRock;
    private final List<ItemStack> pending = new ArrayList<>();
    /** 算岩石的物品种类（{@link #TAG_ROCK_KINDS}） */
    private final Set<Item> rockKinds = new LinkedHashSet<>();

    private Status status = Status.NO_MRU;
    /** progress == 0 时，游戏时间到这里之前不重新判状态（节流，不存盘） */
    private long nextEval;
    /** 挡路的那台（DUPLICATE 时）；不存盘 */
    @Nullable
    private BlockPos blocker;
    /** 服务端：上次告诉客户端的；客户端：收到的（画光束） */
    private boolean working;
    private boolean warnedFiltered;
    private boolean warnedPending;
    /** 投影过几格（不存盘，给 GameTest 和排查用） */
    private int projected;

    // GameTest 钩子：实例字段、不存盘（测试并行跑，不能用静态覆盖）
    @Nullable
    private Function<LootParams, List<ItemStack>> rollerForTest;
    @Nullable
    private Function<LootParams, List<ItemStack>> rockRollerForTest;
    @Nullable
    private ResourceLocation tableForTest;

    public LeyProjectorBlockEntity(BlockPos pos, BlockState state) {
        super(ECLeyProjector.LEY_PROJECTOR_BE.get(), pos, state, () -> ECConfig.get(Config.maxMRU), SLOTS);
    }

    // ------------------------------------------------------------------
    // 槽位
    // ------------------------------------------------------------------

    /** 槽位规则（两端共用）：0 号绑定宝石，1 号透镜，2~10 输出（谁都不能往里放，机器自己用 setStackInSlot 写） */
    public static boolean slotRule(int slot, ItemStack stack) {
        if (slot == SLOT_GEM) {
            return defaultSlotRule(slot, stack);
        }
        return slot == SLOT_LENS && stack.is(ECLeyProjector.LENSES);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slotRule(slot, stack);
    }

    /** 漏斗、管道只能往 0 号格送绑定宝石；透镜要玩家亲手放 */
    @Override
    protected boolean canAutomationInsert(int slot, ItemStack stack) {
        return slot == SLOT_GEM && super.canAutomationInsert(slot, stack);
    }

    /** 只能从输出格取（透镜格、宝石格抽不走） */
    @Override
    protected boolean isOutputSlot(int slot) {
        return slot >= FIRST_OUTPUT && slot < SLOTS;
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
    // 工作
    // ------------------------------------------------------------------

    @Override
    protected void serverTick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        long now = server.getGameTime();
        if (placedTick < 0) {
            placedTick = now;
            server.blockEntityChanged(worldPosition);
        }
        if (!pending.isEmpty()) {
            flushPending(server);
        }
        if (progress == 0) {
            if (now < nextEval) {
                updateWorking();
                return;
            }
            status = evaluate(server);
            if (status != Status.WORKING) {
                nextEval = now + switch (status) {
                    case DUPLICATE -> DUPLICATE_RECHECK;
                    case DISABLED, NO_OWNER, NO_LEY -> STATIC_RECHECK;
                    default -> IDLE_RECHECK;
                };
                updateWorking();
                return;
            }
            int cost = cost();
            if (cost > 0) {
                mru.extractMRU(cost, true);
            }
        } else if (!ECConfig.get(Config.enabled)) {
            status = Status.DISABLED;
            updateWorking();
            return;
        } else if (server.hasNeighborSignal(worldPosition)) {
            // 进度停在原处
            status = Status.POWERED;
            updateWorking();
            return;
        } else {
            status = Status.WORKING;
        }
        progress++;
        if (progress >= ticksPerBlock()) {
            progress = 0;
            project(server);
        }
        // 进度每 tick 都变：只标记区块要存盘，不像 setChanged 那样通知比较器
        server.blockEntityChanged(worldPosition);
        updateWorking();
    }

    /** 开始一格之前判状态（顺序即 {@link Status} 的顺序）；DUPLICATE 时顺手记下挡路的那台 */
    private Status evaluate(ServerLevel server) {
        blocker = null;
        if (!ECConfig.get(Config.enabled)) {
            return Status.DISABLED;
        }
        UUID owner = ECProtection.actingOwner(this);
        if (owner == null) {
            return Status.NO_OWNER;
        }
        if (findTable(server, ORES_DIR) == null) {
            return Status.NO_LEY;
        }
        blocker = findBlocker(server, owner);
        if (blocker != null) {
            return Status.DUPLICATE;
        }
        if (server.hasNeighborSignal(worldPosition)) {
            return Status.POWERED;
        }
        if (!pending.isEmpty()) {
            return Status.OUTPUT_FULL;
        }
        if (mru.getMRU() < cost()) {
            return Status.NO_MRU;
        }
        return Status.WORKING;
    }

    /**
     * 本区块里同主人、比自己早放下的投影仪够不够 {@code max_per_owner_per_chunk} 台：够了返回最早那台的坐标（界面上指给玩家看），否则 null。
     * 只读自己所在区块的方块实体表（区块正在 tick，一定已加载），不扫描方块、不建全局表。
     */
    @Nullable
    private BlockPos findBlocker(ServerLevel server, UUID owner) {
        int limit = ECConfig.get(Config.maxPerOwnerPerChunk);
        int older = 0;
        LeyProjectorBlockEntity oldest = null;
        for (BlockEntity other : server.getChunkAt(worldPosition).getBlockEntities().values()) {
            if (other == this || !(other instanceof LeyProjectorBlockEntity projector) || projector.isRemoved()) {
                continue;
            }
            if (!placedBefore(projector, this) || !owner.equals(ECProtection.actingOwner(projector))) {
                continue;
            }
            older++;
            if (oldest == null || placedBefore(projector, oldest)) {
                oldest = projector;
            }
        }
        return older >= limit && oldest != null ? oldest.getBlockPos() : null;
    }

    /** a 是否比 b 早放下：先比放下的时间（没有记录的排最后），相同比坐标 asLong，小的算早 */
    static boolean placedBefore(LeyProjectorBlockEntity a, LeyProjectorBlockEntity b) {
        long ta = a.placedTick < 0 ? Long.MAX_VALUE : a.placedTick;
        long tb = b.placedTick < 0 ? Long.MAX_VALUE : b.placedTick;
        if (ta != tb) {
            return ta < tb;
        }
        return a.worldPosition.asLong() < b.worldPosition.asLong();
    }

    /** 投影一格：掷表 → 过滤 → 有矿就放进输出格（放不下的进待放）；什么都没剩且开着「保留岩石」就掷岩石表、按岩石的规矩放 → 腐化泄漏 */
    private void project(ServerLevel server) {
        boolean lens = !inventory.getStackInSlot(SLOT_LENS).isEmpty();
        LootParams params = params(server, Vec3.atCenterOf(worldPosition), lens);
        List<ItemStack> rolled;
        ResourceLocation source;
        if (rollerForTest != null) {
            rolled = rollerForTest.apply(params);
            source = EssentialCraft5.id("test_roller");
        } else {
            Found ores = findTable(server, ORES_DIR);
            if (ores == null) {
                // 这一格开始以后数据包被重载、表没了：这一格作废（MRU 已扣），下一格开头会判成「没有地脉」
                status = Status.NO_LEY;
                return;
            }
            rolled = roll(ores.table(), params);
            source = ores.id();
        }
        List<ItemStack> outputs = filter(rolled, source);
        if (!outputs.isEmpty()) {
            store(outputs);
        } else if (keepRock) {
            storeRocks(rollRocks(server, params));
        }
        projected++;
        if (ECConfig.get(Config.generatesCorruption)) {
            ECCorruption.randomIncreaseAt(server, worldPosition, server.random, ECConfig.get(Config.maxCorruptionGen));
        }
    }

    private record Found(ResourceLocation id, LootTable table) {
    }

    /**
     * 找本维度的表：先按维度 id（{@code <dir><ns>/<path>}），找不到再按维度类型；都没有返回 null。
     * 测试钩子 {@link #setTableForTest} 只替换矿石表（找不到就是没有，不再按维度类型找）。
     */
    @Nullable
    private Found findTable(ServerLevel server, String dir) {
        if (tableForTest != null && ORES_DIR.equals(dir)) {
            LootDataManager data = server.getServer().getLootData();
            return data.getElementOptional(LootDataType.TABLE, tableForTest).map(table -> new Found(tableForTest, table)).orElse(null);
        }
        return findDimensionTable(server, dir);
    }

    /**
     * 这个维度有没有地脉（有没有矿石表，找法同 {@link #findTable}，不看测试钩子）。地脉凝聚器（第九批）用它判断能不能凝地脉里的矿物。
     */
    public static boolean hasLey(ServerLevel server) {
        return findDimensionTable(server, ORES_DIR) != null;
    }

    /** 按维度 id、再按维度类型找 {@code dir} 下的表；都没有返回 null */
    @Nullable
    private static Found findDimensionTable(ServerLevel server, String dir) {
        LootDataManager data = server.getServer().getLootData();
        ResourceLocation byDimension = tableId(dir, server.dimension().location());
        Optional<LootTable> table = data.getElementOptional(LootDataType.TABLE, byDimension);
        if (table.isPresent()) {
            return new Found(byDimension, table.get());
        }
        ResourceLocation byType = tableId(dir, server.dimensionTypeId().location());
        return data.getElementOptional(LootDataType.TABLE, byType).map(found -> new Found(byType, found)).orElse(null);
    }

    /** 表 id：{@code essentialcraft5:<dir><维度 ns>/<维度 path>}，例 {@code essentialcraft5:ley_projector/ores/minecraft/overworld} */
    public static ResourceLocation tableId(String dir, ResourceLocation dimension) {
        return EssentialCraft5.id(dir + dimension.getNamespace() + "/" + dimension.getPath());
    }

    /** 掷表参数：ORIGIN = 机器中心，TOOL = 虚拟铁镐（透镜 = 时运 I），参数集 FISHING */
    public static LootParams params(ServerLevel level, Vec3 origin, boolean lens) {
        return new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, origin)
                .withParameter(LootContextParams.TOOL, tool(lens))
                .create(LootContextParamSets.FISHING);
    }

    private static ItemStack tool(boolean fortune) {
        if (plainTool == null || fortuneTool == null) {
            plainTool = new ItemStack(Items.IRON_PICKAXE);
            ItemStack enchanted = new ItemStack(Items.IRON_PICKAXE);
            enchanted.enchant(Enchantments.BLOCK_FORTUNE, 1);
            fortuneTool = enchanted;
        }
        return fortune ? fortuneTool : plainTool;
    }

    /**
     * 掷一次表（不过滤）。走 Raw：不触发全局战利品修改器（见类注释）；按最大堆叠拆堆、去掉没启用的实验性物品（原版 createStackSplitter）。
     */
    @SuppressWarnings("deprecation")
    public static List<ItemStack> roll(LootTable table, LootParams params) {
        List<ItemStack> out = new ArrayList<>();
        table.getRandomItemsRaw(params, LootTable.createStackSplitter(params.getLevel(), out::add));
        return out;
    }

    /** 投影仪会不会留下这个产物：不是空的、没有燃烧时间、不在 #forge:ores 里 */
    public static boolean allowedOutput(ItemStack stack) {
        return !stack.isEmpty() && ForgeHooks.getBurnTime(stack, null) <= 0 && !stack.is(Tags.Items.ORES);
    }

    private List<ItemStack> filter(List<ItemStack> rolled, ResourceLocation source) {
        List<ItemStack> kept = new ArrayList<>(rolled.size());
        for (ItemStack stack : rolled) {
            if (allowedOutput(stack)) {
                kept.add(stack);
            } else if (!stack.isEmpty() && !warnedFiltered) {
                warnedFiltered = true;
                EssentialCraft5.LOGGER.warn("[地脉投影仪] {} 的投影表 {} 掷出了 {}（燃料或矿石方块），已丢弃；这台以后同类的不再记录",
                        worldPosition, source, stack);
            }
        }
        return kept;
    }

    /** 掷岩石表（过滤同矿石表）；这个维度没有岩石表时什么都不出 */
    private List<ItemStack> rollRocks(ServerLevel server, LootParams params) {
        if (rockRollerForTest != null) {
            return filter(rockRollerForTest.apply(params), EssentialCraft5.id("test_rock_roller"));
        }
        Found rock = findTable(server, ROCK_DIR);
        return rock == null ? List.of() : filter(roll(rock.table(), params), rock.id());
    }

    /**
     * 放岩石（LEY-1）：不进待放。输出格里已有这种岩石（第一格同种的就是「自己那格」）就只往那格并，并满了剩下的丢掉；
     * 还没有的话，岩石已占的格数不到 {@value #MAX_ROCK_SLOTS} 才拿一个空格；否则整个丢掉。
     */
    private void storeRocks(List<ItemStack> rocks) {
        for (ItemStack rock : rocks) {
            Item kind = rock.getItem();
            if (!rockKinds.contains(kind) && rockKinds.size() < MAX_ROCK_KINDS) {
                rockKinds.add(kind);
            }
            int own = -1;
            int rockSlots = 0;
            int empty = -1;
            for (int slot = FIRST_OUTPUT; slot < SLOTS; slot++) {
                ItemStack out = inventory.getStackInSlot(slot);
                if (out.isEmpty()) {
                    if (empty < 0) {
                        empty = slot;
                    }
                } else if (out.is(kind)) {
                    rockSlots++;
                    if (own < 0) {
                        own = slot;
                    }
                } else if (rockKinds.contains(out.getItem())) {
                    rockSlots++;
                }
            }
            if (own >= 0) {
                ItemStack out = inventory.getStackInSlot(own);
                int limit = Math.min(inventory.getSlotLimit(own), out.getMaxStackSize());
                int move = Math.min(limit - out.getCount(), rock.getCount());
                if (move > 0 && ItemStack.isSameItemSameTags(out, rock)) {
                    inventory.setStackInSlot(own, out.copyWithCount(out.getCount() + move));
                }
            } else if (empty >= 0 && rockSlots < MAX_ROCK_SLOTS) {
                int limit = Math.min(inventory.getSlotLimit(empty), rock.getMaxStackSize());
                inventory.setStackInSlot(empty, rock.copyWithCount(Math.min(limit, rock.getCount())));
            }
            // 其余的丢掉（岩石不进待放）
        }
    }

    /** 矿按最大堆叠拆开放进输出格，放不下的进待放 */
    private void store(List<ItemStack> outputs) {
        for (ItemStack stack : outputs) {
            int count = stack.getCount();
            int max = Math.max(1, stack.getMaxStackSize());
            while (count > 0) {
                int part = Math.min(count, max);
                count -= part;
                ItemStack rest = insertOutput(stack.copyWithCount(part));
                if (!rest.isEmpty()) {
                    addPending(rest);
                }
            }
        }
    }

    /**
     * 往 2~10 号格放（先并进同物品同 NBT 的格子、再放空格），返回放不下的部分。
     * 用 setStackInSlot 写回（输出格的槽位规则不收任何东西，insertItem 放不进去；照 {@code OreProcessing.put} 的写法）。
     */
    private ItemStack insertOutput(ItemStack stack) {
        ItemStack rest = stack.copy();
        for (int pass = 0; pass < 2 && !rest.isEmpty(); pass++) {
            for (int slot = FIRST_OUTPUT; slot < SLOTS && !rest.isEmpty(); slot++) {
                ItemStack out = inventory.getStackInSlot(slot);
                int limit = Math.min(inventory.getSlotLimit(slot), rest.getMaxStackSize());
                if (pass == 0) {
                    if (!out.isEmpty() && ItemStack.isSameItemSameTags(out, rest) && out.getCount() < limit) {
                        int move = Math.min(limit - out.getCount(), rest.getCount());
                        inventory.setStackInSlot(slot, out.copyWithCount(out.getCount() + move));
                        rest.shrink(move);
                    }
                } else if (out.isEmpty()) {
                    int move = Math.min(limit, rest.getCount());
                    inventory.setStackInSlot(slot, rest.copyWithCount(move));
                    rest.shrink(move);
                }
            }
        }
        return rest;
    }

    private void addPending(ItemStack stack) {
        for (ItemStack waiting : pending) {
            if (ItemStack.isSameItemSameTags(waiting, stack) && waiting.getCount() < waiting.getMaxStackSize()) {
                int move = Math.min(waiting.getMaxStackSize() - waiting.getCount(), stack.getCount());
                waiting.grow(move);
                stack.shrink(move);
                if (stack.isEmpty()) {
                    return;
                }
            }
        }
        if (pending.size() < MAX_PENDING) {
            pending.add(stack.copy());
        } else if (!warnedPending) {
            warnedPending = true;
            EssentialCraft5.LOGGER.warn("[地脉投影仪] {} 的待放产物超过 {} 堆，丢掉了 {}；这台以后不再记录", worldPosition, MAX_PENDING, stack);
        }
    }

    /** 每 tick 先把待放的往输出格里塞（取走输出后自动接着放） */
    private void flushPending(ServerLevel server) {
        boolean changed = false;
        Iterator<ItemStack> it = pending.iterator();
        while (it.hasNext()) {
            ItemStack stack = it.next();
            ItemStack rest = insertOutput(stack);
            if (rest.getCount() != stack.getCount()) {
                changed = true;
                if (rest.isEmpty()) {
                    it.remove();
                } else {
                    stack.setCount(rest.getCount());
                }
            }
        }
        if (changed) {
            server.blockEntityChanged(worldPosition);
        }
    }

    private void updateWorking() {
        boolean now = status == Status.WORKING;
        if (now != working) {
            working = now;
            markClientDirty();
        }
    }

    public int ticksPerBlock() {
        return ECConfig.get(Config.ticksPerBlock);
    }

    /** 一格实际扣的 MRU：min(mru_per_block, max_mru)（配置写得比容量还大时机器照样能工作） */
    public int cost() {
        return Math.min(ECConfig.get(Config.mruPerBlock), mru.getMaxMRU());
    }

    // ------------------------------------------------------------------
    // 访问（界面、方块、测试）
    // ------------------------------------------------------------------

    /** 放下的时间（方块的 setPlacedBy 调，Ctrl+中键复制带着旧时间的也改成「现在」）；下一 tick 重新判状态 */
    public void markPlaced(long gameTime) {
        placedTick = gameTime;
        nextEval = 0L;
        setChanged();
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

    public boolean keepRock() {
        return keepRock;
    }

    /** 界面按钮「保留岩石」（菜单的 clickMenuButton 调） */
    public void toggleKeepRock() {
        keepRock = !keepRock;
        setChanged();
    }

    /** DUPLICATE 时挡路的那台；否则 null */
    @Nullable
    public BlockPos blocker() {
        return status == Status.DUPLICATE ? blocker : null;
    }

    /** 待放的产物（只读视图） */
    public List<ItemStack> pendingView() {
        return Collections.unmodifiableList(pending);
    }

    /** 投影过几格（不存盘） */
    public int projectedCount() {
        return projected;
    }

    /** 客户端：在不在投影（画光束） */
    public boolean isWorking() {
        return working;
    }

    /** GameTest：代替掷矿石表（参数照常建好传进去）；null 恢复 */
    public void setRollerForTest(@Nullable Function<LootParams, List<ItemStack>> roller) {
        rollerForTest = roller;
        nextEval = 0L;
    }

    /** GameTest：代替掷岩石表（「保留岩石」开着、矿石表什么都没出时才调）；null 恢复 */
    public void setRockRollerForTest(@Nullable Function<LootParams, List<ItemStack>> roller) {
        rockRollerForTest = roller;
    }

    /** 记住的岩石种类（只读视图） */
    public Set<Item> rockKindsView() {
        return Collections.unmodifiableSet(rockKinds);
    }

    /**
     * 开发展示厅（02_gui_ley_projector_scene_working）：输出格摆成「岩石两格、矿五格、空两格」——深板岩圆石、圆石各一满组
     * （满了，之后投影出的岩石都丢掉，画面不会再变），粗铁、粗铜、红石、青金石、钻石各一些，最后两格空着；记下这两种岩石。
     */
    public void fillShowroomDemo() {
        ItemStack[] demo = {
                new ItemStack(Items.COBBLED_DEEPSLATE, 64), new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.RAW_IRON, 23),
                new ItemStack(Items.RAW_COPPER, 41), new ItemStack(Items.REDSTONE, 18), new ItemStack(Items.LAPIS_LAZULI, 12),
                new ItemStack(Items.DIAMOND, 3), ItemStack.EMPTY, ItemStack.EMPTY};
        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            inventory.setStackInSlot(FIRST_OUTPUT + i, demo[i]);
        }
        rockKinds.add(Items.COBBLED_DEEPSLATE);
        rockKinds.add(Items.COBBLESTONE);
        pending.clear();
        setChanged();
    }

    /** GameTest：矿石表换成这个 id（不存在 = 没有地脉）；null 恢复 */
    public void setTableForTest(@Nullable ResourceLocation id) {
        tableForTest = id;
        nextEval = 0L;
    }

    // ------------------------------------------------------------------
    // 掉落、清空、存盘、同步
    // ------------------------------------------------------------------

    /** 挖掉时连待放的一起掉出来（super 掉物品栏） */
    @Override
    public void dropContents() {
        super.dropContents();
        if (level != null) {
            for (ItemStack stack : pending) {
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
            }
        }
        pending.clear();
    }

    /** /clone move、/setblock 覆盖前原版会先清空：待放的也要清，否则被复制一份（同第四批 STO-2） */
    @Override
    public void clearContent() {
        super.clearContent();
        pending.clear();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong(TAG_PLACED, placedTick);
        tag.putInt(TAG_PROGRESS, progress);
        tag.putBoolean(TAG_KEEP_ROCK, keepRock);
        if (!pending.isEmpty()) {
            ListTag list = new ListTag();
            for (ItemStack stack : pending) {
                list.add(stack.save(new CompoundTag()));
            }
            tag.put(TAG_PENDING, list);
        }
        if (!rockKinds.isEmpty()) {
            ListTag kinds = new ListTag();
            for (Item kind : rockKinds) {
                ResourceLocation key = ForgeRegistries.ITEMS.getKey(kind);
                if (key != null) {
                    kinds.add(StringTag.valueOf(key.toString()));
                }
            }
            tag.put(TAG_ROCK_KINDS, kinds);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        placedTick = tag.contains(TAG_PLACED, Tag.TAG_LONG) ? tag.getLong(TAG_PLACED) : -1L;
        progress = Math.max(0, tag.getInt(TAG_PROGRESS));
        keepRock = tag.getBoolean(TAG_KEEP_ROCK);
        pending.clear();
        ListTag list = tag.getList(TAG_PENDING, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && pending.size() < MAX_PENDING; i++) {
            ItemStack stack = ItemStack.of(list.getCompound(i));
            if (!stack.isEmpty()) {
                pending.add(stack);
            }
        }
        rockKinds.clear();
        ListTag kinds = tag.getList(TAG_ROCK_KINDS, Tag.TAG_STRING);
        for (int i = 0; i < kinds.size() && rockKinds.size() < MAX_ROCK_KINDS; i++) {
            ResourceLocation key = ResourceLocation.tryParse(kinds.getString(i));
            if (key != null && ForgeRegistries.ITEMS.containsKey(key)) {
                rockKinds.add(ForgeRegistries.ITEMS.getValue(key));
            }
        }
        // 只有发给客户端的更新包里有这个键（存档里没有）
        if (tag.contains(TAG_WORKING)) {
            working = tag.getBoolean(TAG_WORKING);
        }
        nextEval = 0L;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.remove(TAG_PENDING);
        tag.remove(TAG_PLACED);
        tag.remove(TAG_PROGRESS);
        tag.remove(TAG_KEEP_ROCK);
        tag.remove(TAG_ROCK_KINDS);
        tag.putBoolean(TAG_WORKING, working);
        return tag;
    }

    /** 光束从机器往下 {@value #BEAM_LENGTH} 格（修原作 isGlobalRenderer：原作到哪都画） */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition.getX(), worldPosition.getY() - BEAM_LENGTH, worldPosition.getZ(),
                worldPosition.getX() + 1, worldPosition.getY() + 1, worldPosition.getZ() + 1);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new LeyProjectorMenu(id, inventory, this);
    }
}
