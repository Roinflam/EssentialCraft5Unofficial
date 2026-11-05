package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.ForceDomeMenu;
import com.flameforge.essentialcraft5.registry.ECForceDome;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.flameforge.essentialcraft5.util.ForceDomeIndex;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 力场穹顶（第十批 10c-M4，batch10.md §5.13；policy 第十批 10c）：EC5 新方块，原作没有。接上 MRU 以后，水平 {@code radius} 格内（整根柱子上下）
 * 不再自然刷出敌对生物。<b>只减少刷怪、不动方块</b>：拦截在 {@link ForceDomeIndex} 里做（只给刷怪事件 DENY / 取消），这里只管「拦哪些区块、付不付得起」。
 *
 * <h2>只罩主人自己的地方（policy §2.4：少探测、长缓存）</h2>
 * 半径以内碰得到的每块区块（按区块里离穹顶最近的方块中心算），以主人身份对「区块中心、穹顶那一层」那一格探一次（{@link ECProtection#check}，
 * 走每主人探测预算；主人在线时才探，单人游戏里房主永远在线）。结论两种都记进方块实体（存盘，按游戏时间计）：
 * <ul>
 *   <li>能动 → 这块区块「可拦」，记 {@code claim_cache_ticks}（默认 1 小时）；不能动（别人的领地、出生点保护）→「不可拦」，照常刷怪，
 *       记 {@value #DENIED_CACHE_MULTIPLIER} 倍长（默认 6 小时）；没问过、过期了 →「未确认」，先不拦。</li>
 *   <li>主人在线时，能动的结论用到九成就重新问一次（每次评估最多新问 {@value #MAX_NEW_PROBES} 块，近的先问），一直在线的主人不会出现空档。
 *       不能动的结论在有效期内一律不重问：不可拦和未确认对拦不拦是同一个结果，提前问只会让领地插件多发一句「没有权限」。
 *       邻居后来给了权限的，拆了重放就会重新问（掉落的方块不带方块实体数据）。</li>
 *   <li>主人离线时只用没过期的结论，不探测；过期的区块不再拦，等主人上线重新确认。没有主人的（/setblock、别的模组放的）不工作。</li>
 *   <li>存着的 MRU 连一块区块一秒的钱（⌈满负荷 ÷ 覆盖区块数⌉）都不够时不探测（同饰品封禁场「付不起不探测」）。</li>
 * </ul>
 * 所以在按区块划、从天到地整根柱子都算的领地上，拿它废不掉别人的刷怪塔：只有主人自己能动方块的区块才拦。
 * 已知取舍（batch10.md §9.2，维持逐区块）：结论只代表区块正中、穹顶那一层，拦的却是整块区块、整根柱子。领地按立方体划（只圈了某几层）、
 * 或者边界不和区块对齐时，穹顶可能罩进邻居领地的一部分：放在别人领地正下方 / 正上方的野外能罩住他那一列，不含任何区块正中的小领地它看不见。
 * 混合端上每次探测都是一次真的破坏事件（被拒时领地插件会给主人发一句「没有权限」），能动的区块一小时最多问一次，不能动的六小时一次。
 *
 * <h2>状态与耗电</h2>
 * 每 {@value #EVAL_INTERVAL} 刻判一次（{@link Status} 的顺序）：停用 → 没有主人 → 名额满 → 红石暂停 → 连一块区块一秒都付不起（不问领地，
 * 全被拒的算没有可拦的区块，其余算 MRU 不够）→ 问领地 → 没有可拦的区块（全被拒 / 还没确认）→ MRU 不够 → 工作。
 * 工作时<b>先算后扣</b>：每次判完付下一秒的钱，满负荷（覆盖的区块全部可拦）每刻 {@code mru_per_tick}，只有一部分可拦时按比例少扣
 * （⌈20 × 每刻 × 可拦 / 覆盖⌉），不够就这一秒不拦、一分不扣。付到的那一秒写进索引（{@link ForceDomeIndex.Handle#setPaidUntil}，多留 {@value #PAID_GRACE} 刻：
 * 刷怪在方块实体 tick 之前），方块实体不 tick 了（所在区块只加载不 tick）覆盖也跟着过期，不会白拦。
 * MRU 不够、红石暂停、还没确认这几种会来回跳的状态只停付费、登记留着（下一秒又工作、区块没变时不用重建索引快照）；停用、没有主人、名额满、
 * 全被拒、换主人、卸载、拆掉时整份撤掉。
 *
 * <h2>台数</h2>
 * 同一主人全服同时 {@code max_per_owner} 台在工作（默认 4，0 = 不限；只数加载着、最近 {@value #ALIVE_TICKS} 刻 tick 过的），按放下的先后取最早的几台；
 * 占名额 = 还没判过状态，或者状态是工作 / 还没确认 / MRU 不够（开着、只是暂时停）。红石关掉的、范围内一块可拦的都没有的不占。
 * 一台<b>开始</b>占名额时，同主人、比它晚放下的几台下一刻就重判（同地脉凝聚器 {@code claimStarted}），超过上限的最多多工作 1 刻。
 *
 * <h2>槽位与同步</h2>
 * 只有 0 号绑定宝石格（漏斗也只能往里塞绑定宝石）。物品栏、平衡值、MRU 都不发给附近的客户端（界面数值走菜单），更新包里也去掉领地记录和放下时间。
 * 第一次开始工作时给在线的主人发手册进度 {@code guide/computer/force_dome}。
 */
public class ForceDomeBlockEntity extends MRUBlockEntity {

    /** 配置 machines.force_dome（policy 第十批 10c「力场穹顶」） */
    public static final class Config {
        /** 默认管的刷怪类型（batch10.md §5.13） */
        public static final List<String> DEFAULT_SPAWN_TYPES = List.of("NATURAL", "CHUNK_GENERATION", "PATROL", "REINFORCEMENT");
        /**
         * {@code spawn_types} 只认这四种（默认全选）。别的写进去也不认：刷怪笼、刷怪蛋、指令本来就承诺不拦；袭击（EVENT）拦了会卡住袭击——
         * Forge 的 coremod 把 {@code Raid.joinRaid} 里的 finalizeSpawn 换成了 FinalizeSpawn 事件，灾厄村民先进了波次名单、再被取消生成，
         * 永远不进世界也不算死，袭击要等到超时；结构（STRUCTURE）刷的是下界门里出来的僵尸猪灵和生成结构时放进去的生物（林地府邸、海底神殿、
         * 女巫小屋……），不归穹顶管
         */
        public static final Set<MobSpawnType> ALLOWED_SPAWN_TYPES = Collections.unmodifiableSet(EnumSet.of(
                MobSpawnType.NATURAL, MobSpawnType.CHUNK_GENERATION, MobSpawnType.PATROL, MobSpawnType.REINFORCEMENT));

        public static ForgeConfigSpec.BooleanValue enabled;
        public static ForgeConfigSpec.IntValue radius;
        public static ForgeConfigSpec.IntValue mruPerTick;
        public static ForgeConfigSpec.IntValue maxPerOwner;
        public static ForgeConfigSpec.IntValue claimCacheTicks;
        public static ForgeConfigSpec.IntValue capacity;
        public static ForgeConfigSpec.ConfigValue<List<? extends String>> spawnTypes;

        /** 刷怪类型名单的解析缓存：配置项每次重读会换一个列表对象，按对象身份认（可能在世界生成线程上读，两个字段都是 volatile） */
        private static volatile List<? extends String> cachedRaw;
        private static volatile Set<MobSpawnType> cachedTypes = parse(DEFAULT_SPAWN_TYPES);

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("force_dome");
            enabled = builder.comment("Whether force domes work at all.", "力场穹顶是否工作")
                    .define("enabled", true);
            radius = builder.comment("Horizontal radius (blocks) in which natural monster spawns are stopped; the whole column from bottom to top counts.",
                            "水平半径（格）：这个范围内（整根柱子上下）不再自然刷出敌对生物")
                    .defineInRange("radius", 32, 1, 64);
            mruPerTick = builder.comment("MRU per tick at full load (every covered chunk blockable). Paid one second ahead; less when only some chunks are blockable.",
                            "满负荷（覆盖的区块全部可拦）每刻多少 MRU；每秒预先付一次，只有一部分区块可拦时按比例少扣")
                    .defineInRange("mru_per_tick", 10, 0, 10_000);
            maxPerOwner = builder.comment("How many domes of the same owner work at the same time across the server (the earliest placed ones; only loaded ones count; 0 = no limit).",
                            "同一个主人全服同时有几台穹顶能工作（按放下的先后取最早的几台，只数加载着的；0 = 不限）")
                    .defineInRange("max_per_owner", 4, 0, 1024);
            claimCacheTicks = builder.comment("How long (ticks) the owner's claim check for one chunk is remembered; chunks the owner may not build in are remembered "
                                    + DENIED_CACHE_MULTIPLIER + " times as long. Offline owners' domes keep using unexpired results only.",
                            "以主人身份对一块区块的领地探测结果记多久（刻），动不了的区块记 " + DENIED_CACHE_MULTIPLIER + " 倍长；主人离线时只用没过期的结果")
                    .defineInRange("claim_cache_ticks", 72_000, 200, 1_728_000);
            capacity = builder.comment("MRU capacity.", "MRU 容量")
                    .defineInRange("capacity", 100_000, 1, Integer.MAX_VALUE);
            spawnTypes = builder.comment("Spawn types the dome stops (monsters only). Only NATURAL, CHUNK_GENERATION, PATROL and REINFORCEMENT are accepted "
                                    + "(all four by default); anything else is dropped. Spawners, spawn eggs, commands, raids and bosses are never stopped.",
                            "穹顶拦哪些刷怪类型（只拦敌对生物）：只能从 NATURAL、CHUNK_GENERATION、PATROL、REINFORCEMENT 这四种里选（默认全选），"
                                    + "写别的会被丢掉；刷怪笼、刷怪蛋、指令、袭击、首领永远不拦")
                    .defineListAllowEmpty("spawn_types", DEFAULT_SPAWN_TYPES, Config::validEntry);
            builder.pop();
        }

        /** {@code spawn_types} 的一项合不合法：只认 {@link #ALLOWED_SPAWN_TYPES} 里的名字（大小写、首尾空格不计） */
        public static boolean validEntry(Object entry) {
            return entry instanceof String s && ALLOWED_SPAWN_TYPES.contains(spawnType(s));
        }

        /** 管的刷怪类型（不可变）；配置没加载时用默认值。任何线程都能调 */
        public static Set<MobSpawnType> spawnTypes() {
            if (spawnTypes == null) {
                return cachedTypes;
            }
            boolean loaded = ECConfig.spec() != null && ECConfig.spec().isLoaded();
            List<? extends String> raw = loaded ? spawnTypes.get() : spawnTypes.getDefault();
            if (raw != cachedRaw) {
                cachedTypes = parse(raw);
                cachedRaw = raw;
            }
            return cachedTypes;
        }

        @Nullable
        static MobSpawnType spawnType(String name) {
            try {
                return MobSpawnType.valueOf(name.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        /** 把名单解析成刷怪类型（不可变）：不认识的、不在 {@link #ALLOWED_SPAWN_TYPES} 里的丢掉（配置文件被手改、校验没拦住时也不会拦到别的类型） */
        public static Set<MobSpawnType> parse(List<? extends String> raw) {
            Set<MobSpawnType> types = EnumSet.noneOf(MobSpawnType.class);
            for (String entry : raw) {
                MobSpawnType type = spawnType(entry);
                if (type != null && ALLOWED_SPAWN_TYPES.contains(type)) {
                    types.add(type);
                }
            }
            return Collections.unmodifiableSet(types);
        }
    }

    /** 界面状态（同步给界面用 ordinal，只许在末尾追加）。判的顺序见类注释 */
    public enum Status {
        /** 工作中（绿） */
        WORKING(0x00FF00),
        /** 配置 enabled = false（红） */
        DISABLED(0xFF0000),
        /** 专用服上没有主人（红） */
        NO_OWNER(0xFF0000),
        /** 同主人更早放下的已占满名额（橙：在等） */
        OWNER_LIMIT(0xFFAA00),
        /** 红石信号（橙：暂停） */
        POWERED(0xFFAA00),
        /** 覆盖的区块全是主人不能动的（红） */
        NO_CLAIM(0xFF0000),
        /** 没有确认过的可拦区块：还在问、区块没加载、或者主人离线且结论过期了（黄） */
        UNCONFIRMED(0xFFFF00),
        /** 付不起下一秒；连一块区块一秒都付不起时也不问领地（黄） */
        NO_MRU(0xFFFF00);

        private static final Status[] VALUES = values();

        public final int color;

        Status(int color) {
            this.color = color;
        }

        public static Status byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : UNCONFIRMED;
        }
    }

    /** 一块区块的领地结论：主人能不能动、哪一刻问的（游戏时间） */
    private record Claim(boolean allowed, long at) {
    }

    public static final int SLOT_GEM = 0;
    public static final int SLOTS = 1;
    /** 多少刻判一次状态、付一次钱 */
    public static final int EVAL_INTERVAL = 20;
    /** 付到的那一秒在索引里多留几刻（同一刻里刷怪在方块实体 tick 之前） */
    public static final int PAID_GRACE = 2;
    /** 每次评估最多新问几块区块的领地 */
    public static final int MAX_NEW_PROBES = 4;
    /** 主人不能动的区块，结论记 {@code claim_cache_ticks} 的几倍长（默认 6 小时）；有效期内不重问 */
    public static final int DENIED_CACHE_MULTIPLIER = 6;
    /** 名额表里，多少刻内 tick 过的才算「在运转」 */
    public static final int ALIVE_TICKS = 40;

    public static final String TAG_PLACED = "placed_tick";
    public static final String TAG_CLAIMS = "claims";
    public static final String TAG_CLAIMS_OWNER = "claims_owner";

    /** 加载着的穹顶（只在服务端线程读写；弱引用，漏了移除也不会拖住方块实体） */
    private static final Set<ForceDomeBlockEntity> LOADED = Collections.newSetFromMap(new WeakHashMap<>());
    /** 占名额的状态（见类注释「台数」） */
    private static final Set<Status> CLAIMING = EnumSet.of(Status.WORKING, Status.UNCONFIRMED, Status.NO_MRU);
    /** 不工作时只停付费、不撤登记的状态（见 {@link #evaluate}）；其余状态撤掉登记 */
    private static final Set<Status> TRANSIENT = EnumSet.of(Status.NO_MRU, Status.POWERED, Status.UNCONFIRMED);

    /** 放下的游戏时间；没有记录是 −1（第一次服务端 tick 补成当前时间） */
    private long placedTick = -1L;
    /** 区块 → 领地结论（存盘） */
    private final Long2ObjectOpenHashMap<Claim> claims = new Long2ObjectOpenHashMap<>();
    /** 领地结论是替谁问的；换了主人整份作废 */
    @Nullable
    private UUID claimsOwner;

    private final ForceDomeIndex.Handle handle = new ForceDomeIndex.Handle();
    private Status status = Status.UNCONFIRMED;
    private boolean evaluated;
    private long nextEval;
    private long lastTick = Long.MIN_VALUE;
    private boolean listed;
    /** 半径内碰得到的区块（近的在前）和算它们时用的半径；半径变了重算 */
    private long[] covered = new long[0];
    private int coveredRadius = -1;
    private int blockable;
    private int unconfirmed;
    private int denied;
    /** 本次在线以后发过手册进度没有（不存盘：重启后再发一次也无妨，发进度本身不重复） */
    private boolean awarded;
    /** 付过几次钱（不存盘，给 GameTest 和排查用） */
    private int payments;

    // GameTest 钩子：实例字段、不存盘（测试并行跑，不能用静态覆盖，也不改真配置）
    private int radiusForTest = -1;
    private int perOwnerForTest = -1;
    private int mruPerTickForTest = -1;
    private int claimCacheForTest = -1;

    public ForceDomeBlockEntity(BlockPos pos, BlockState state) {
        super(ECForceDome.FORCE_DOME_BE.get(), pos, state, () -> ECConfig.get(Config.capacity), SLOTS);
    }

    // ------------------------------------------------------------------
    // 槽位、同步开关
    // ------------------------------------------------------------------

    /** 槽位规则（两端共用）：只有 0 号绑定宝石格 */
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

    public int effectiveRadius() {
        return radiusForTest > 0 ? radiusForTest : ECConfig.get(Config.radius);
    }

    public int mruPerTick() {
        return mruPerTickForTest >= 0 ? mruPerTickForTest : ECConfig.get(Config.mruPerTick);
    }

    public int claimCacheTicks() {
        return claimCacheForTest > 0 ? claimCacheForTest : ECConfig.get(Config.claimCacheTicks);
    }

    /** 主人不能动的区块的结论记多久：{@code claim_cache_ticks} × {@value #DENIED_CACHE_MULTIPLIER} */
    public long deniedCacheTicks() {
        return (long) claimCacheTicks() * DENIED_CACHE_MULTIPLIER;
    }

    /** 一条结论的有效期：能动的 {@link #claimCacheTicks}，不能动的 {@link #deniedCacheTicks} */
    private long validity(Claim claim) {
        return claim.allowed() ? claimCacheTicks() : deniedCacheTicks();
    }

    private int maxPerOwner() {
        return perOwnerForTest >= 0 ? perOwnerForTest : ECConfig.get(Config.maxPerOwner);
    }

    /** 下一秒要付多少：⌈20 × 每刻 × 可拦 / 覆盖⌉（覆盖的区块全部可拦时就是满负荷） */
    public int costPerSecond() {
        if (covered.length == 0 || blockable <= 0) {
            return 0;
        }
        long full = (long) EVAL_INTERVAL * mruPerTick();
        long cost = (full * blockable + covered.length - 1) / covered.length;
        return (int) Math.min(Integer.MAX_VALUE, cost);
    }

    /** 满负荷每秒多少 MRU */
    public int fullCostPerSecond() {
        return (int) Math.min(Integer.MAX_VALUE, (long) EVAL_INTERVAL * mruPerTick());
    }

    /** 拦一块区块一秒要多少：⌈满负荷 ÷ 覆盖区块数⌉（存着的 MRU 比这还少时不问领地；不耗电时是 0） */
    public int oneChunkCostPerSecond() {
        if (covered.length == 0) {
            return 0;
        }
        long full = fullCostPerSecond();
        return (int) ((full + covered.length - 1) / covered.length);
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
            // 还没判过状态，在名额表里先算占着名额：后放的当场重判（区块重新加载时，更早的这台把名额拿回去）
            claimStarted();
        }
    }

    @Override
    protected void serverTick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        long now = server.getGameTime();
        // 上一刻已经确定占着名额（判过状态、一直在 tick、状态是占名额的那几种）；否则这一刻开始占的话要叫后放的重判
        boolean wasClaiming = evaluated && now - lastTick <= ALIVE_TICKS && CLAIMING.contains(status);
        lastTick = now;
        if (!listed) {
            list();
        }
        if (placedTick < 0) {
            placedTick = now;
            server.blockEntityChanged(worldPosition);
        }
        if (now >= nextEval) {
            evaluate(server, now);
        }
        if (!wasClaiming && claimsSlot()) {
            claimStarted();
        }
    }

    /** 判一次状态；工作时付下一秒的钱、更新索引，否则停付费或撤掉覆盖 */
    private void evaluate(ServerLevel server, long now) {
        nextEval = now + EVAL_INTERVAL;
        // GameTest 直接调 evaluateNow 时还没走过 serverTick：在名额表里登记、记下「在运转」
        lastTick = Math.max(lastTick, now);
        if (!listed) {
            list();
        }
        status = decide(server, now);
        evaluated = true;
        if (status == Status.WORKING) {
            handle.setPaidUntil(now + EVAL_INTERVAL + PAID_GRACE);
            publish(server, now);
            if (!awarded) {
                ServerPlayer owner = ECProtection.onlineOwner(this);
                if (owner != null) {
                    awarded = true;
                    PlayerPentacleBlockEntity.award(owner, ECForceDome.GUIDE_FORCE_DOME, ECForceDome.GUIDE_CRITERION);
                }
            }
        } else if (TRANSIENT.contains(status)) {
            // 会来回跳的几种（MRU 进账比花费慢、接着红石时钟、离线主人的结论刚过期）：只停付费，covers 当刻就不认；
            // 登记留着，下一秒又工作、区块没变时索引不用整份重建
            handle.setPaidUntil(Long.MIN_VALUE);
        } else {
            ForceDomeIndex.withdraw(handle);
        }
    }

    private Status decide(ServerLevel server, long now) {
        refreshCoverage();
        if (!ECConfig.get(Config.enabled)) {
            count(now);
            return Status.DISABLED;
        }
        UUID owner = ECProtection.actingOwner(this);
        if (owner == null) {
            count(now);
            return Status.NO_OWNER;
        }
        if (!owner.equals(claimsOwner)) {
            claims.clear();
            claimsOwner = owner;
            // 换了主人：替上一个主人登记的覆盖整份撤掉（停付费的状态不撤登记，这里得自己撤）
            ForceDomeIndex.withdraw(handle);
            server.blockEntityChanged(worldPosition);
        }
        if (limitReached(owner, now)) {
            count(now);
            return Status.OWNER_LIMIT;
        }
        if (server.hasNeighborSignal(worldPosition)) {
            count(now);
            return Status.POWERED;
        }
        if (mruPerTick() > 0 && mru.getMRU() < oneChunkCostPerSecond()) {
            // 连一块区块一秒的钱都付不起：不问领地（同饰品封禁场「付不起不探测」）。全被拒的照旧算「没有你能罩的区块」，不占名额
            count(now);
            return blockable == 0 && unconfirmed == 0 ? Status.NO_CLAIM : Status.NO_MRU;
        }
        probe(server, now);
        count(now);
        if (blockable == 0) {
            return unconfirmed == 0 ? Status.NO_CLAIM : Status.UNCONFIRMED;
        }
        int cost = costPerSecond();
        if (mru.getMRU() < cost) {
            return Status.NO_MRU;
        }
        if (cost > 0) {
            mru.extractMRU(cost, true);
        }
        payments++;
        return Status.WORKING;
    }

    /**
     * 主人在线时问领地：没问过的、能动的结论用到九成的、不能动的结论过期了的区块，近的先问，每次最多 {@value #MAX_NEW_PROBES} 块。
     * 问不出结论（区块没加载、本刻预算用完）的这次跳过，下次再问；不当成拒绝记下来。
     */
    private void probe(ServerLevel server, long now) {
        ServerPlayer online = ECProtection.onlineOwner(this);
        if (online == null) {
            return;
        }
        int cache = claimCacheTicks();
        long refreshAge = cache - cache / 10;
        long deniedCache = deniedCacheTicks();
        int fresh = 0;
        for (long chunk : covered) {
            if (fresh >= MAX_NEW_PROBES) {
                break;
            }
            Claim claim = claims.get(chunk);
            if (claim != null) {
                long age = now - claim.at();
                // 能动的提前重问，一直在线的主人不出空档；不能动的有效期内一律不问：不可拦和未确认对拦不拦是同一个结果，
                // 提前问只会让领地插件多发一句「没有权限」
                if (age >= 0 && age < (claim.allowed() ? refreshAge : deniedCache)) {
                    continue;
                }
            }
            ECProtection.Verdict verdict = ECProtection.check(online, server, probePos(chunk), this);
            if (verdict == ECProtection.Verdict.UNKNOWN) {
                continue;
            }
            claims.put(chunk, new Claim(verdict == ECProtection.Verdict.ALLOW, now));
            fresh++;
            server.blockEntityChanged(worldPosition);
        }
    }

    /** 按现有的结论数三种区块（不探测；有效期按结论分开取，见 {@link #validity}） */
    private void count(long now) {
        int yes = 0;
        int no = 0;
        int unknown = 0;
        for (long chunk : covered) {
            Claim claim = claims.get(chunk);
            if (claim == null || now - claim.at() < 0 || now - claim.at() >= validity(claim)) {
                unknown++;
            } else if (claim.allowed()) {
                yes++;
            } else {
                no++;
            }
        }
        blockable = yes;
        denied = no;
        unconfirmed = unknown;
    }

    /** 工作时把可拦的区块（各自到期时刻 = 问的时刻 + 缓存时长）登记进索引；和上次一样时索引什么都不做 */
    private void publish(ServerLevel server, long now) {
        int cache = claimCacheTicks();
        Long2LongOpenHashMap chunks = new Long2LongOpenHashMap();
        for (long chunk : covered) {
            Claim claim = claims.get(chunk);
            if (claim != null && claim.allowed() && now - claim.at() >= 0 && now - claim.at() < cache) {
                chunks.put(chunk, claim.at() + cache);
            }
        }
        ForceDomeIndex.publish(handle, server.dimension(), worldPosition.getX() + 0.5D, worldPosition.getZ() + 0.5D, effectiveRadius(), chunks);
    }

    /**
     * 半径内碰得到的区块：区块里离穹顶中心最近的方块中心不超过半径（刷怪点按方块中心算）。近的在前（自己所在的区块第一个），问领地时先问近的。
     * 半径变了才重算；不再覆盖的区块的结论一并删掉。
     */
    private void refreshCoverage() {
        int radius = effectiveRadius();
        if (radius == coveredRadius) {
            return;
        }
        coveredRadius = radius;
        double px = worldPosition.getX() + 0.5D;
        double pz = worldPosition.getZ() + 0.5D;
        double radiusSq = (double) radius * radius;
        List<long[]> found = new ArrayList<>();
        for (int cx = (worldPosition.getX() - radius) >> 4; cx <= (worldPosition.getX() + radius) >> 4; cx++) {
            for (int cz = (worldPosition.getZ() - radius) >> 4; cz <= (worldPosition.getZ() + radius) >> 4; cz++) {
                double dx = px - clamp(px, cx * 16 + 0.5D, cx * 16 + 15.5D);
                double dz = pz - clamp(pz, cz * 16 + 0.5D, cz * 16 + 15.5D);
                double distSq = dx * dx + dz * dz;
                if (distSq <= radiusSq) {
                    found.add(new long[] {ChunkPos.asLong(cx, cz), (long) (distSq * 4D)});
                }
            }
        }
        found.sort((a, b) -> Long.compare(a[1], b[1]));
        covered = new long[found.size()];
        for (int i = 0; i < covered.length; i++) {
            covered[i] = found.get(i)[0];
        }
        Set<Long> keep = new HashSet<>();
        for (long chunk : covered) {
            keep.add(chunk);
        }
        if (claims.keySet().removeIf(chunk -> !keep.contains(chunk)) && level != null) {
            level.blockEntityChanged(worldPosition);
        }
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }

    /** 问这块区块的领地时探测哪一格：区块中心、穹顶那一层 */
    public BlockPos probePos(long chunk) {
        return new BlockPos(ChunkPos.getX(chunk) * 16 + 8, worldPosition.getY(), ChunkPos.getZ(chunk) * 16 + 8);
    }

    /** 同主人、比自己早放下、占着名额的穹顶够不够 max_per_owner；顺手清掉表里已移除的 */
    private boolean limitReached(UUID owner, long now) {
        int max = maxPerOwner();
        if (max <= 0) {
            return false;
        }
        int earlier = 0;
        Iterator<ForceDomeBlockEntity> it = LOADED.iterator();
        while (it.hasNext()) {
            ForceDomeBlockEntity other = it.next();
            if (other.isRemoved() || !(other.level instanceof ServerLevel)) {
                other.listed = false;
                it.remove();
                continue;
            }
            if (other == this || now - other.lastTick > ALIVE_TICKS || !other.claimsSlot()) {
                continue;
            }
            if (placedBefore(other, this) && owner.equals(ECProtection.actingOwner(other))) {
                earlier++;
            }
        }
        return earlier >= max;
    }

    /** 占不占名额：还没判过状态，或者状态是「开着、只是暂时停」的那几种 */
    private boolean claimsSlot() {
        return !evaluated || CLAIMING.contains(status);
    }

    /** 这台刚开始占名额：同主人、比它晚放下的几台下一刻就重判。只在翻转时调，遍历一次全服表 */
    private void claimStarted() {
        UUID owner = ECProtection.actingOwner(this);
        if (owner == null) {
            return;
        }
        for (ForceDomeBlockEntity other : LOADED) {
            if (other != this && !other.isRemoved() && other.level instanceof ServerLevel && placedBefore(this, other)
                    && owner.equals(ECProtection.actingOwner(other))) {
                other.nextEval = 0L;
            }
        }
    }

    /** a 是否比 b 早放下：先比放下的时间（没有记录的排最后），相同比坐标 asLong，小的算早（同地脉凝聚器） */
    static boolean placedBefore(ForceDomeBlockEntity a, ForceDomeBlockEntity b) {
        long ta = a.placedTick < 0 ? Long.MAX_VALUE : a.placedTick;
        long tb = b.placedTick < 0 ? Long.MAX_VALUE : b.placedTick;
        if (ta != tb) {
            return ta < tb;
        }
        return a.worldPosition.asLong() < b.worldPosition.asLong();
    }

    private void list() {
        LOADED.add(this);
        listed = true;
    }

    private void unlist() {
        if (level != null && !level.isClientSide) {
            if (listed) {
                LOADED.remove(this);
                listed = false;
            }
            ForceDomeIndex.withdraw(handle);
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

    /** 关服时清空名额表（{@link ForceDomeIndex} 的关服监听调） */
    public static void clearLoaded() {
        for (ForceDomeBlockEntity dome : LOADED) {
            dome.listed = false;
        }
        LOADED.clear();
    }

    // ------------------------------------------------------------------
    // 访问（界面、方块、测试）
    // ------------------------------------------------------------------

    /** 放下的时间（方块的 setPlacedBy 调，Ctrl+中键复制带着旧时间的也改成「现在」）；下一刻重判 */
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

    public int blockableChunks() {
        return blockable;
    }

    public int unconfirmedChunks() {
        return unconfirmed;
    }

    public int deniedChunks() {
        return denied;
    }

    /** 半径内碰得到的区块数（按上次判状态时的半径） */
    public int coveredChunks() {
        return covered.length;
    }

    /** 半径内碰得到的区块（近的在前；复制一份） */
    public long[] coveredChunkKeys() {
        return covered.clone();
    }

    /** 索引里现在登记着、付过这一秒的钱的区块有几块（停付费时是 0） */
    public int publishedChunks() {
        return ForceDomeIndex.publishedChunks(handle);
    }

    /** 付过几次钱（不存盘） */
    public int payments() {
        return payments;
    }

    /** 在不在加载着的穹顶表里（GameTest 用） */
    public boolean isListed() {
        return LOADED.contains(this);
    }

    /** 这块区块现在记着的结论：null = 没有或过期，否则能不能动 */
    @Nullable
    public Boolean claimFor(long chunk) {
        Claim claim = claims.get(chunk);
        if (claim == null || level == null) {
            return null;
        }
        long age = level.getGameTime() - claim.at();
        return age >= 0 && age < validity(claim) ? claim.allowed() : null;
    }

    /** GameTest：立刻判一次（同 tick 里的正常判定照样会跑） */
    public void evaluateNow() {
        if (level instanceof ServerLevel server) {
            evaluate(server, server.getGameTime());
        }
    }

    /** GameTest：半径（≤ 0 恢复配置） */
    public void setRadiusForTest(int radius) {
        radiusForTest = radius;
        nextEval = 0L;
    }

    /** GameTest：全服同主人台数（负数恢复配置，0 = 不限） */
    public void setPerOwnerForTest(int perOwner) {
        perOwnerForTest = perOwner;
        nextEval = 0L;
    }

    /** GameTest：满负荷每刻多少 MRU（负数恢复配置） */
    public void setMruPerTickForTest(int perTick) {
        mruPerTickForTest = perTick;
        nextEval = 0L;
    }

    /** GameTest：领地结论记多久（≤ 0 恢复配置） */
    public void setClaimCacheForTest(int ticks) {
        claimCacheForTest = ticks;
        nextEval = 0L;
    }

    /** GameTest：所有领地结论都当成早问了 ticks 刻（测「过期以后不拦」，不用真等） */
    public void ageClaimsForTest(long ticks) {
        for (Long2ObjectMap.Entry<Claim> entry : claims.long2ObjectEntrySet()) {
            entry.setValue(new Claim(entry.getValue().allowed(), entry.getValue().at() - ticks));
        }
        nextEval = 0L;
    }

    // ------------------------------------------------------------------
    // 存盘、同步
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong(TAG_PLACED, placedTick);
        if (claimsOwner != null && !claims.isEmpty()) {
            tag.putUUID(TAG_CLAIMS_OWNER, claimsOwner);
            ListTag list = new ListTag();
            for (Long2ObjectMap.Entry<Claim> entry : claims.long2ObjectEntrySet()) {
                CompoundTag one = new CompoundTag();
                one.putLong("c", entry.getLongKey());
                one.putBoolean("a", entry.getValue().allowed());
                one.putLong("t", entry.getValue().at());
                list.add(one);
            }
            tag.put(TAG_CLAIMS, list);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        placedTick = tag.contains(TAG_PLACED, Tag.TAG_LONG) ? tag.getLong(TAG_PLACED) : -1L;
        claims.clear();
        claimsOwner = tag.hasUUID(TAG_CLAIMS_OWNER) ? tag.getUUID(TAG_CLAIMS_OWNER) : null;
        if (claimsOwner != null) {
            ListTag list = tag.getList(TAG_CLAIMS, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag one = list.getCompound(i);
                claims.put(one.getLong("c"), new Claim(one.getBoolean("a"), one.getLong("t")));
            }
        }
        coveredRadius = -1;
        nextEval = 0L;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.remove(TAG_PLACED);
        tag.remove(TAG_CLAIMS);
        tag.remove(TAG_CLAIMS_OWNER);
        return tag;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ForceDomeMenu(id, inventory, this);
    }
}
