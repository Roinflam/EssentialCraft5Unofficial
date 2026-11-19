package com.flameforge.essentialcraft5.util;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.event.ProtectionProbeEvent;
import com.flameforge.essentialcraft5.blockentity.MRUBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.google.common.collect.Lists;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * 权限工具：模组替玩家（或替机器的主人）改世界之前，先让领地插件说一句「行不行」。规格见 tools/plan/policy.md §2，写法速查见 porting_guide.md §8。
 * 原作（1.12）一处权限检查都没有：法杖、机器、仪式直接 setBlock，爆炸直接炸；这里把第三批起所有「动世界」的地方统一收到这个类。
 * 单人和纯 Forge 下没有谁会取消这些事件，这里的一切判断都退化成放行（policy §2.3a），混合端上才真的有人拦。
 *
 * <h2>为什么这么写（policy §2.1、§2.3a）</h2>
 * 本模组是通用模组：权限一律走 Forge 标准的 {@code BreakEvent} / {@code EntityPlaceEvent}。Forge 端的领地模组直接监听它们；
 * 单人和没装领地的服没人取消，一律放行。在 Mohist 系混合端上，领地是 Bukkit 插件，看不到 Forge 事件本身，只看服务端转过去的：
 * <ul>
 *   <li>只有实体是 {@link ServerPlayer} <b>且不是 {@link FakePlayer}</b> 的 {@code BreakEvent} / {@code EntityPlaceEvent} / {@code EntityMultiPlaceEvent} 才转。
 *       所以一律用<b>真玩家</b>（使用者本人，或者机器主人的真身）发事件，假玩家发的插件根本看不见。</li>
 *   <li>放置事件还要带朝向才转，这个朝向只有 {@code ForgeEventFactory.onBlockPlace / onMultiBlockPlace} 会写进去：
 *       自己 {@code new EntityPlaceEvent} 再 post 的不转。</li>
 *   <li>「这格他能不能动」用 {@code BreakEvent} 探测：日志插件把它记成「破坏」，回滚时只是把原方块放回原处（无害）；
 *       用放置事件探测会被记成「放置」，回滚时会把真实方块删掉。探测发的是它的子类 {@link ProtectionProbeEvent}（公开 API），
 *       只认 {@code BreakEvent} 的监听器照样收到、取消照样算数；混合端可以按类名（以 {@code ProbeEvent} 结尾）认出探测，
 *       只问领地插件、不当成真挖（不通知 MONITOR 的统计插件、吞掉拒绝提示、不占全局破坏事件槽）。代破坏发的仍是普通 {@code BreakEvent}。</li>
 *   <li><b>Mohist 的 {@code Level.destroyBlock} 在有插件监听时先发可取消的 {@code ModsEntityDestroyBlockEvent}</b>（混合端的 {@code Level.java}：
 *       被取消就返回 false、方块不动）。所以破坏一律「先删、删成功再算掉落」（{@link #breakGuarded} 等），先掉落后删 = 删被取消时白送一份。
 *       例外是批量的 {@link #harvestGuarded(Player, Level, BlockPos, ItemStack, boolean)}（创世神之杖一刻拆几十格）：它不调 destroyBlock，
 *       直接把格子 setBlock 成剩下的流体，所以<b>不发</b> ModsEntityDestroyBlockEvent（Mohist 自己的钩子，不是 Forge 标准事件，领地插件认的是破坏事件）；
 *       Mohist 的 setBlock 同样先发可取消的 {@code SetBlockEvent}（混合端的 {@code Level.java}），被取消时返回 false，照样不掉落。</li>
 *   <li><b>Bukkit 的破坏事件是全局单槽</b>（{@code BlockEventDispatcher} 的 {@code blockBreakEvent}，每次转发都覆盖，
 *       只有 {@code ServerPlayerGameMode.destroyBlock}（混合端里）在玩家亲手挖完时读它决定掉不掉落）。所以<b>不许在另一次破坏过程里</b>
 *       （{@code mineBlock}、{@code onBlockStartBreak}、{@code BreakEvent} 监听器、{@code playerDestroy}）再发破坏事件、调 {@code destroyBlock}、
 *       本类的 {@link #canEdit} / {@link #check} / {@link #breakGuarded} / {@link #harvestGuarded}：会把外层那名玩家
 *       这一下的「掉不掉落」换成里层事件的结果（外层那格不掉落）。要连带做事的推迟到那次破坏返回之后（当 tick 末尾）。</li>
 * </ul>
 *
 * <h2>用法速查（细节看各方法的注释）</h2>
 * <pre>{@code
 * // 1. 不改方块，只问「这格他能不能动」（挪实体、远程绑定、对格子里的生物做事）：
 * if (!ECProtection.canEdit(player, level, target)) { ...提示 message.essentialcraft5.no_permission，不扣东西... }
 * // 机器 / 一次要问很多格的：第四个参数传调用方（一般是 this），每个调用方每 tick 最多新探测 max_probes_per_tick 格，
 * // 同一名被探测的玩家（主人）每 tick 合计最多 max_probes_per_player_tick 格
 * ECProtection.Verdict v = ECProtection.check(player, level, cell, this);   // ALLOW / DENY / UNKNOWN（预算用完、区块没加载：下次再问）
 *
 * // 2. 逐格放方块（被拒的格子跳过，其余照做）；在 useOn 的捕获里调也行，内部会暂停外层捕获
 * if (ECProtection.placeGuarded(player, level, cell, crop.getStateForAge(max), Direction.UP)) { 扣 MRU }
 *
 * // 3. 破坏方块、要掉落（不要写在 useOn 里：捕获只还原方块、不收回掉落 = 复制）。先删、删成功才掉落
 * if (ECProtection.breakGuarded(player, level, pos, ItemStack.EMPTY, true)) { 扣 MRU }
 * List<ItemStack> drops = ECProtection.harvestGuarded(player, level, pos, tool);   // 不生成掉落物，把该掉的东西交给你；null = 没挖
 * ECProtection.harvestGuarded(player, level, pos, tool, true);   // 一刻拆几十格的批量写法：不出破坏粒子和声音、不写探测缓存，效果自己挑几格补发
 *
 * // 4. 爆炸：不破坏方块、不放火，展示框 / 画 / 盔甲架 / 掉落物 / 经验球等不受影响，伤害带攻击者
 * ECProtection.safeExplode(level, null, player, player.position(), 3F);
 *
 * // 5. 机器（主人规则，policy §2.3）：主人在 MachineBlock.setPlacedBy 里记好了
 * ECProtection.OwnerState state = ECProtection.ownerState(this);            // 界面显示「没有主人 / 主人不在线」
 * ECProtection.Verdict v = ECProtection.checkAsOwner(this, cell);           // 没主人 DENY；在线用主人真身探测；离线 UNKNOWN（停工）；单人里都算房主的
 *
 * // 6. 一段「不知道会改哪些方块」的代码：整段捕获，以主人真身发一次放置事件，被拒全部还原
 * ECProtection.CaptureResult r = ECProtection.captureGuarded(owner, level, face, List.of(p, p.relative(dir)), () -> { ...; return 做了事; });
 * if (r.rejected()) { if (r.refundSafe()) 退回物品; else 不退、记日志、冷却 }
 * }</pre>
 *
 * <h2>副作用（policy §2.4）</h2>
 * 探测事件别的监听器也看得到：日志插件可能多记一条「破坏」、反作弊可能取消离得远的真身事件（只会让机器停工，是安全的方向）、
 * 给破坏发奖励的插件可能加分。所以<b>只对真正要动的格子探测</b>（例：魔力漏斗只探测真有东西要搬的格子，别对一片空气挨个探测）。
 * 本模组自己的 BreakEvent 监听器要区分「真破坏」和探测时看 {@link #isProbing()}（别的模组判 {@code instanceof ProtectionProbeEvent}），
 * 区分「玩家亲手挖」和法杖等代挖看 {@link #isGuardedBreak()}。
 * <p>
 * 按主手工具做事的监听器（例如挖一格顺手毁掉卡牌、连锁挖、范围挖掘这类，都在 BreakEvent 里读主手）
 * 会把探测和代破坏当成玩家拿着手上的东西真挖了一格：刷掉落、从这一格起远程连锁挖。所以发事件的那一下把主手<b>暂时拿空</b>（{@link #postBreakEvent}），
 * 发完同一 tick 内放回原来那个对象，不发背包包；领地判定不受影响（Forge 领地模组看玩家和格子；混合端转成 Bukkit 事件也只带玩家和格子）。只读副手或别的状态的监听器管不到。
 *
 * <h2>只给 GameTest 和开发自检用的开关（F4-1）</h2>
 * 没有任何指令暴露它们，正常游戏里恒为空：
 * <ul>
 *   <li>{@link #setOnlineOverride}：把一名不在玩家列表里的玩家（{@code ProtectionFrameworkTests.realPlayer}）当成在线，
 *       {@link #onlinePlayer} 先查它（{@link #onlineOwner}、{@link #ownerState}、{@link #checkAsOwner} 都认）。
 *       按 UUID 分，互不影响；每个测试用自己的 UUID，{@code succeed} 之前 {@link #clearOnlineOverride} 清掉自己那条。开服关服整个清空。</li>
 *   <li>{@link Config#setRequireOwnerOnlineForTest}、{@link Config#setMaxProbesPerPlayerTickForTest}：全局的。GameTest 同一批的测试在同一个 tick 循环里交替跑，
 *       置了没清会让别的模块「主人离线不动」的测试偶发失败——<b>只许在同一段同步代码里设置、断言、清除</b>（try / finally 置回 null）；
 *       要跨 tick 的测试单独放 {@code @GameTest(batch = "owner_offline")}。</li>
 * </ul>
 * <p>
 * 只在服务端线程上用（缓存、预算、爆炸标记、覆盖表都是不加锁的静态表）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class ECProtection {

    /** 探测缓存超过这么多条就整个清掉（每条几十字节；正常用法远到不了） */
    private static final int CACHE_LIMIT = 65536;
    /** 多少 tick 扫一遍缓存、删掉过期的 */
    private static final int SWEEP_INTERVAL = 200;
    /**
     * safeExplode 的半径上限。爆炸按 (4r+2)³ 的范围查实体、再逐个做遮挡射线，
     * 本模组的爆炸（腐化 #13 是 3、恶魔是 2）都远小于它，传大了夹到这里（P2）。
     */
    public static final float MAX_EXPLOSION_RADIUS = 8F;

    /**
     * 混合端给 ForgeEventFactory 加了带手的 4 参 onBlockPlace / onMultiBlockPlace，转成 Bukkit 事件时按它报放置用的手和物品；
     * 3 参版不设手，事件里是默认值 MAIN_HAND（混合端 {@code BlockEvent.EntityPlaceEvent}），于是报成主手和主手里的物品（同 MagicalLanternItem）。
     * 纯 Forge 没有这两个重载，找不到时为 null，用 3 参版。
     */
    @Nullable
    private static final MethodHandle PLACE_WITH_HAND = findWithHand("onBlockPlace", BlockSnapshot.class);
    @Nullable
    private static final MethodHandle MULTI_PLACE_WITH_HAND = findWithHand("onMultiBlockPlace", List.class);

    /** 探测结果：键是（玩家、维度、格子），值 = 到期的服务器 tick &lt;&lt; 1 | 是否允许 */
    private static final Map<ProbeKey, Long> CACHE = new HashMap<>();
    /** 本 tick 每个调用方已经新探测了几格（按对象身份区分调用方，换 tick 清空，所以不会攥着已经没用的方块实体） */
    private static final Map<Object, int[]> BUDGET = new IdentityHashMap<>();
    /** 本 tick 每名被探测的玩家（按 UUID）一共新探测了几格：一名主人放 100 台机器也只能每 tick 发这么多探测事件（F4-1、SAF-3） */
    private static final Map<UUID, int[]> PLAYER_BUDGET = new HashMap<>();
    /** safeExplode 进行中的爆炸，用它们的伤害源（每次新建的实例）按身份认出来 */
    private static final Set<DamageSource> SAFE_EXPLOSIONS = Collections.newSetFromMap(new IdentityHashMap<>());
    /** 带过滤的 safeExplode：这次爆炸只伤哪些生物（键同上，按伤害源实例的身份） */
    private static final Map<DamageSource, Predicate<? super LivingEntity>> SAFE_EXPLOSION_FILTERS = new IdentityHashMap<>();
    /** 测试用：当成在线的玩家（见类注释「只给 GameTest 和开发自检用的开关」） */
    private static final Map<UUID, ServerPlayer> ONLINE_OVERRIDES = new HashMap<>();
    /** 当前线程上开着的实体收集上下文（{@link #collectSpawns}），里层在队首 */
    private static final ThreadLocal<ArrayDeque<SpawnCollector>> COLLECTORS = ThreadLocal.withInitial(ArrayDeque::new);
    /** 所有线程上开着的实体收集上下文个数：为 0 时实体加入事件直接返回，不碰 ThreadLocal（只在服务端线程上改，别的线程读到旧值也只是多查一次） */
    private static int openCollectors;
    private static int budgetTick = Integer.MIN_VALUE;
    private static int lastSweep;
    private static int probing;
    private static int guardedBreak;

    private ECProtection() {
    }

    // ==================================================================
    // 配置（general.protection 小节，由 ECConfig.register 登记）
    // ==================================================================

    /**
     * 服务端配置 {@code general.protection.*}。读值用下面的静态方法（配置没加载时返回默认值）。
     * 测试覆盖值（{@code set…ForTest}）优先于真配置，只给 GameTest 用：GameTest 里别用 {@code ConfigValue.set}
     * （服务端配置自动存盘，文件监视器后台重读会冲掉刚设的值）。用法约束见类注释。
     */
    public static final class Config {
        private static ForgeConfigSpec.BooleanValue requireOwnerOnline;
        private static ForgeConfigSpec.IntValue probeCacheTicks;
        private static ForgeConfigSpec.IntValue maxProbesPerTick;
        private static ForgeConfigSpec.IntValue maxProbesPerPlayerTick;
        @Nullable
        private static Boolean requireOwnerOnlineOverride;
        @Nullable
        private static Integer maxProbesPerPlayerTickOverride;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.comment("Permission checks: machines and items only change blocks that the player (or the machine's owner) could change themselves.",
                    "权限检查：物品和机器只改动使用者（或机器主人）自己能动的格子").push("protection");
            requireOwnerOnline = builder.comment(
                            "Machines that change the world only work while their owner is online (they check claims as the owner).",
                            "Turn off only on servers without claim plugins: offline owners' machines then skip the check.",
                            "会改动世界的机器只在主人在线时工作（用主人的身份查领地）。只有没装领地插件的服才关：关掉后主人离线时机器不做检查")
                    .define("require_owner_online", true);
            probeCacheTicks = builder.comment("How long (ticks) a permission probe result is remembered per player, dimension and block.",
                            "一次权限探测的结果按（玩家、维度、格子）记多久（刻）")
                    .defineInRange("probe_cache_ticks", 200, 1, 72000);
            maxProbesPerTick = builder.comment("At most this many new permission probes per caller (machine, player) per tick; the rest wait for the next tick.",
                            "每个调用方（机器、玩家）每刻最多新探测几格，其余的下一刻再探")
                    .defineInRange("max_probes_per_tick", 16, 1, 1024);
            maxProbesPerPlayerTick = builder.comment(
                            "At most this many new permission probes per probed player per tick, summed over all their machines and items (cached results don't count).",
                            "Each probe is a real block-break event that claim and logging plugins handle, so one owner with many machines can't flood them.",
                            "同一名被探测的玩家（机器主人、拿物品的玩家）每刻合计最多新探测几格（缓存命中不算）。",
                            "每次探测都是一次真的破坏事件，领地和日志插件都要处理，一个主人放很多台机器也不会刷爆")
                    .defineInRange("max_probes_per_player_tick", 64, 1, 1024);
            builder.pop();
        }

        public static boolean requireOwnerOnline() {
            Boolean override = requireOwnerOnlineOverride;
            if (override != null) {
                return override;
            }
            return requireOwnerOnline == null || ECConfig.get(requireOwnerOnline);
        }

        public static int probeCacheTicks() {
            return probeCacheTicks == null ? 200 : ECConfig.get(probeCacheTicks);
        }

        public static int maxProbesPerTick() {
            return maxProbesPerTick == null ? 16 : ECConfig.get(maxProbesPerTick);
        }

        public static int maxProbesPerPlayerTick() {
            Integer override = maxProbesPerPlayerTickOverride;
            if (override != null) {
                return override;
            }
            return maxProbesPerPlayerTick == null ? 64 : ECConfig.get(maxProbesPerPlayerTick);
        }

        /**
         * GameTest 用：{@code require_owner_online} 的测试覆盖值，null = 读真配置。
         * <b>只许在同一段同步代码里设置、断言、置回 null</b>（try / finally），见类注释。
         */
        public static void setRequireOwnerOnlineForTest(@Nullable Boolean value) {
            requireOwnerOnlineOverride = value;
        }

        /** GameTest 用：{@code max_probes_per_player_tick} 的测试覆盖值（夹到 ≥1），null = 读真配置。约束同上 */
        public static void setMaxProbesPerPlayerTickForTest(@Nullable Integer value) {
            maxProbesPerPlayerTickOverride = value == null ? null : Math.max(1, value);
        }

        private static void clearTestOverrides() {
            requireOwnerOnlineOverride = null;
            maxProbesPerPlayerTickOverride = null;
        }
    }

    // ==================================================================
    // 结果类型
    // ==================================================================

    /** 探测的结论 */
    public enum Verdict {
        /** 能动 */
        ALLOW,
        /** 不能动（被领地插件拒绝、出生点保护、世界边界外、不是真玩家、没有主人） */
        DENY,
        /**
         * 现在给不出结论：本 tick 的探测预算用完了、区块没加载、主人不在线。
         * 当成「这次先不做」，<b>不要</b>当成拒绝记下来，下次再问。
         */
        UNKNOWN;

        public boolean allowed() {
            return this == ALLOW;
        }
    }

    /** 机器主人的状态（界面据此显示「没有主人」「主人不在线」，键见 tools/lang/keys/safety.json） */
    public enum OwnerState {
        /** 没有主人：/setblock、结构生成、别的模组或假玩家放的。这样的机器不动世界（单人里算房主的，见 {@link #onlineOwner}） */
        NONE,
        /** 有主人，但不在线（没开局域网的单人里房主在就不会出现：记下的主人不在线时算房主的，见 {@link #onlineOwner}） */
        OFFLINE,
        /** 主人在线 */
        ONLINE
    }

    private record ProbeKey(UUID player, ResourceKey<Level> dimension, long pos) {
    }

    // ==================================================================
    // 身份
    // ==================================================================

    /**
     * 真玩家：服务端的 {@link ServerPlayer} 且不是 {@link FakePlayer}。只有它的事件会被混合端转给领地插件；
     * 假玩家（机械手、别的模组的自动化）一律当成「没有权限」。
     */
    public static boolean isRealPlayer(@Nullable Entity entity) {
        return entity instanceof ServerPlayer && !(entity instanceof FakePlayer);
    }

    /**
     * 在线的真玩家：先查测试覆盖表（{@link #setOnlineOverride}），再查玩家列表；不在线、是假玩家、参数为 null 返回 null。
     * 机器主人经这里认「在线」。
     */
    @Nullable
    public static ServerPlayer onlinePlayer(@Nullable MinecraftServer server, @Nullable UUID id) {
        if (server == null || id == null) {
            return null;
        }
        ServerPlayer override = ONLINE_OVERRIDES.get(id);
        if (override != null) {
            return override;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        return isRealPlayer(player) ? player : null;
    }

    /**
     * 只给 GameTest 和开发自检用：把这名（不在玩家列表里的）真玩家当成在线，直到 {@link #clearOnlineOverride} 或开服 / 关服。
     * 假玩家不收（它本来就不算在线）。
     */
    public static void setOnlineOverride(ServerPlayer player) {
        if (isRealPlayer(player)) {
            ONLINE_OVERRIDES.put(player.getUUID(), player);
        }
    }

    public static void clearOnlineOverride(UUID id) {
        ONLINE_OVERRIDES.remove(id);
    }

    public static void clearOnlineOverrides() {
        ONLINE_OVERRIDES.clear();
    }

    // ==================================================================
    // 探测：canEdit / check
    // ==================================================================

    /**
     * 这名玩家能不能动这一格（不改方块，只问）。用这格<b>当前</b>的方块状态以他的身份发一次 {@code BreakEvent}（子类 {@link ProtectionProbeEvent}），没被取消算能动；
     * 另查出生点保护和世界边界（{@code level.mayInteract}）。结果按（玩家、维度、格子）缓存 {@code probe_cache_ticks}。
     * 预算按玩家算：同一名玩家每 tick 最多新探测 {@code max_probes_per_tick} 格，超出的返回 false（当成「这次不行」）。
     * <p>
     * 不是真玩家（null、客户端、FakePlayer）一律 false。<b>不查</b>冒险模式：玩家拿物品点方块时原版和 Forge 已经查过了
     * （要自己查就 {@code player.mayUseItemAt(pos, face, stack)}）；机器用主人身份探测时主人当前的游戏模式不相干。
     */
    public static boolean canEdit(@Nullable Player player, Level level, BlockPos pos) {
        return check(player, level, pos, player).allowed();
    }

    /** 同 {@link #canEdit(Player, Level, BlockPos)}，预算按 {@code caller} 算（机器传自己，一次要问很多格的物品传一个固定对象） */
    public static boolean canEdit(@Nullable Player player, Level level, BlockPos pos, Object caller) {
        return check(player, level, pos, caller).allowed();
    }

    /**
     * 探测并给出三态结论（见 {@link Verdict}）。缓存命中不花预算；没命中时花调用方本 tick 的一格预算、再花被探测玩家本 tick 的一格总预算
     * （{@code max_probes_per_player_tick}，同一名主人所有机器合计），任一用完返回 {@link Verdict#UNKNOWN}（由机器的续探测兜底）。
     * 区块没加载也返回 UNKNOWN（探测不会触发区块加载）。
     *
     * @param caller 按它的对象身份分预算（一般传 {@code this}）；null 时按玩家算
     */
    public static Verdict check(@Nullable Player player, Level level, BlockPos pos, @Nullable Object caller) {
        if (!(level instanceof ServerLevel serverLevel) || !isRealPlayer(player)) {
            return Verdict.DENY;
        }
        ServerPlayer serverPlayer = (ServerPlayer) player;
        if (!ECLevels.isFullyLoaded(level, pos)) {
            return Verdict.UNKNOWN;
        }
        if (!level.mayInteract(serverPlayer, pos)) {
            return Verdict.DENY;
        }
        int now = serverLevel.getServer().getTickCount();
        maintain(now);
        ProbeKey key = new ProbeKey(serverPlayer.getUUID(), level.dimension(), pos.asLong());
        Long cached = CACHE.get(key);
        if (cached != null && (int) (cached >> 1) - now > 0) {
            return (cached & 1L) != 0 ? Verdict.ALLOW : Verdict.DENY;
        }
        int[] used = BUDGET.computeIfAbsent(caller != null ? caller : serverPlayer, c -> new int[1]);
        if (used[0] >= Config.maxProbesPerTick()) {
            return Verdict.UNKNOWN;
        }
        int[] playerUsed = PLAYER_BUDGET.computeIfAbsent(serverPlayer.getUUID(), id -> new int[1]);
        if (playerUsed[0] >= Config.maxProbesPerPlayerTick()) {
            return Verdict.UNKNOWN;
        }
        used[0]++;
        playerUsed[0]++;
        boolean allowed = probe(serverPlayer, level, pos);
        remember(key, allowed, now);
        return allowed ? Verdict.ALLOW : Verdict.DENY;
    }

    /**
     * 当前是否正在发本类的探测事件。本模组自己的 {@code BreakEvent} 监听器用它区分「真破坏」和探测
     * （例：以后要在方块被挖时做事的，探测时别做）。探测事件本身是 {@link ProtectionProbeEvent}，拿到事件对象的也可以直接判 instanceof。
     */
    public static boolean isProbing() {
        return probing > 0;
    }

    /**
     * 当前是否在本类的代破坏里：{@link #breakGuarded} / {@link #harvestGuarded}（发破坏事件、删方块、算掉落这一整段）。
     * 法杖等替玩家挖的不是玩家亲手挖的：按「挖方块」给奖励的监听器（π/ρ 饰品、ο 的掉落 +1）看到它就跳过。
     */
    public static boolean isGuardedBreak() {
        return guardedBreak > 0;
    }

    /** 忘掉这名玩家的全部探测结果（他的领地权限刚变过、测试里换了模拟领地时用） */
    public static void forget(UUID player) {
        CACHE.keySet().removeIf(key -> key.player().equals(player));
    }

    /** 清空全部探测缓存 */
    public static void clearCache() {
        CACHE.clear();
    }

    private static boolean probe(ServerPlayer player, Level level, BlockPos pos) {
        BlockPos target = probeTarget(level, pos);
        probing++;
        try {
            return !postBreakEvent(player, level, target, level.getBlockState(target), true);
        } finally {
            probing--;
        }
    }

    /**
     * 实际发探测的那一格：要问的格子是牌子时改问它下面（下面也是牌子、或在世界高度外就问上面）那一格。
     * 有的领地插件把牌子当出售 / 出租牌登记，收到这格的破坏事件且判定放行时，就把牌子从登记表里删掉、存盘，而探测并不会真的挖掉它。
     * 领地是立体的，上下相邻一格几乎总在同一块领地里，结论不变；结果照旧按原来那格缓存。上下都是牌子时只好问原来那格。
     */
    static BlockPos probeTarget(Level level, BlockPos pos) {
        if (!level.getBlockState(pos).is(BlockTags.ALL_SIGNS)) {
            return pos;
        }
        for (BlockPos candidate : new BlockPos[]{pos.below(), pos.above()}) {
            if (!level.isOutsideBuildHeight(candidate) && !level.getBlockState(candidate).is(BlockTags.ALL_SIGNS)) {
                return candidate;
            }
        }
        return pos;
    }

    /**
     * 以 player 的身份发一次 {@code BreakEvent}，发的时候主手是空的（见类注释「副作用」）：按主手工具做事的别的模组不会把探测、代破坏当成真挖。
     * 探测发的是子类 {@link ProtectionProbeEvent}（只问能不能动，方块不会被挖），代破坏发普通的 {@code BreakEvent}（放行后真的删方块）。
     * 事件在拿空之后才 new（构造器按主手算经验）；finally 里放回原来那个对象（调用方可能还拿着它的引用），
     * 期间有监听器往空出来的格子塞了东西的，塞进背包或掉在脚下，别覆盖掉。
     * 监听器在事件里把玩家弄死了（没开 keepInventory）：死亡掉落撒的时候主手是空的，拿开的东西放回死人的背包，重生时就没了，所以改成掉在原地；
     * 塞进来的东西同样掉在原地（{@link ECItemEntities#giveOrDrop}）。
     *
     * @param probe true = 探测（{@link #probe}），false = 代破坏（{@link #destroy}）
     * @return 事件是否被取消
     */
    private static boolean postBreakEvent(ServerPlayer player, Level level, BlockPos pos, BlockState state, boolean probe) {
        Inventory inventory = player.getInventory();
        int slot = inventory.selected;
        boolean hide = Inventory.isHotbarSlot(slot);
        ItemStack held = hide ? inventory.items.get(slot) : ItemStack.EMPTY;
        boolean aliveBefore = player.isAlive();
        if (hide) {
            inventory.items.set(slot, ItemStack.EMPTY);
        }
        try {
            BlockEvent.BreakEvent event = probe
                    ? new ProtectionProbeEvent(level, pos, state, player)
                    : new BlockEvent.BreakEvent(level, pos, state, player);
            return MinecraftForge.EVENT_BUS.post(event);
        } finally {
            if (hide) {
                ItemStack intruder = inventory.items.get(slot);
                if (aliveBefore && !player.isAlive() && !level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
                    inventory.items.set(slot, ItemStack.EMPTY);
                    ECItemEntities.giveOrDrop(player, held);
                } else {
                    inventory.items.set(slot, held);
                }
                ECItemEntities.giveOrDrop(player, intruder);
            }
        }
    }

    private static void remember(ProbeKey key, boolean allowed, int now) {
        int expiry = now + Config.probeCacheTicks();
        CACHE.put(key, ((long) expiry << 1) | (allowed ? 1L : 0L));
    }

    /** 真的发过一次破坏事件（breakGuarded / harvestGuarded）时顺手更新缓存：结论和探测是一回事 */
    private static void remember(ServerPlayer player, ServerLevel level, BlockPos pos, boolean allowed) {
        int now = level.getServer().getTickCount();
        maintain(now);
        remember(new ProbeKey(player.getUUID(), level.dimension(), pos.asLong()), allowed, now);
    }

    /** 换 tick 清预算；隔一段时间删掉过期的缓存，太多了整个清掉 */
    private static void maintain(int now) {
        if (now != budgetTick) {
            BUDGET.clear();
            PLAYER_BUDGET.clear();
            budgetTick = now;
        }
        if (now - lastSweep >= SWEEP_INTERVAL || CACHE.size() > CACHE_LIMIT) {
            lastSweep = now;
            CACHE.values().removeIf(value -> (int) (value >> 1) - now <= 0);
            if (CACHE.size() > CACHE_LIMIT) {
                EssentialCraft5.LOGGER.warn("权限探测缓存超过 {} 条，整个清空", CACHE_LIMIT);
                CACHE.clear();
            }
        }
    }

    // ==================================================================
    // 改方块：placeGuarded / breakGuarded / harvestGuarded
    // ==================================================================

    /** {@link #placeGuarded(Player, Level, BlockPos, BlockState, Direction, InteractionHand, int)}，不报手、按 UPDATE_ALL 通知 */
    public static boolean placeGuarded(@Nullable Player player, Level level, BlockPos pos, BlockState newState, Direction face) {
        return placeGuarded(player, level, pos, newState, face, null, Block.UPDATE_ALL);
    }

    /**
     * 以这名玩家的身份把 pos 改成 newState，领地插件拒绝就原样还原（逐格用：被拒的格子跳过，其余照做）。
     * 做法照 Forge 的放置捕获（{@code ForgeHooks.onPlaceItemIntoWorld}）：开快照捕获改方块（这期间不通知客户端和邻居、不调 onPlace），
     * 发一次带朝向的 {@code ForgeEventFactory.onBlockPlace}（改动连带到别的格子时是 {@code onMultiBlockPlace}），
     * 被拒按快照倒序还原（不掉落），放行才补 onPlace 和通知。
     * <p>
     * 在 {@code useOn} 的捕获里调用也行：外层捕获的开关和已捕获的列表先存起来、做完原样放回，这一格不会被外层再打包一次，
     * 外层事件被拒时也不会被撤回（它已经单独问过了）。
     * <p>
     * 返回 false（什么都没改）的情况：不是真玩家、客户端、区块没加载、出生点保护 / 世界边界、状态没变化、被拒；
     * 还有<b>这格原来是带方块实体的另一种方块</b>——换掉它时原方块的 onRemove 会先把容器里的东西掉出来，被拒还原时又按快照放回 = 复制，
     * 所以不许（要换掉容器类方块先用 {@link #breakGuarded}）。同一种方块只改状态（作物长大、机器换朝向）不受这条限制。
     *
     * @param face  朝向（混合端没有朝向就不转给插件，所以不能为 null）：点中的面；机器、范围效果传 {@link Direction#UP}
     * @param hand  用哪只手放的（混合端按它报手里的物品）；传 null 走 3 参版，混合端报的是主手和主手里的物品（机器、范围效果这样传就行）。
     *              手里拿着施法的物品、又不想让插件以为放下的是它（创世神之杖），传另一只手
     * @param flags 放行后通知用的 setBlock 标志（原版 BlockItem 用 UPDATE_ALL_IMMEDIATE，一般 UPDATE_ALL；只改外观不想触发邻居更新时 UPDATE_CLIENTS）
     * @return 是否已经改成 newState
     */
    public static boolean placeGuarded(@Nullable Player player, Level level, BlockPos pos, BlockState newState, Direction face,
                                       @Nullable InteractionHand hand, int flags) {
        if (level.isClientSide || !isRealPlayer(player) || !ECLevels.isFullyLoaded(level, pos) || !level.mayInteract(player, pos)) {
            return false;
        }
        BlockState old = level.getBlockState(pos);
        if (old.hasBlockEntity() && !old.is(newState.getBlock())) {
            return false;
        }
        // 整个流程都要暂停外层捕获（useOn 里调用时外层 ForgeHooks 开着捕获）：不光是 setBlock 那一下，
        // 被拒后的还原、放行后补的 onPlace / 通知里也会 setBlock，要是被外层收进去，ForgeHooks 事后再发一次放置事件，
        // 这格本来就被拒，于是按外层快照「还原」成被拒的改动——被拒的格子又变回去了（第三批 ECStaffs 报的 bug）
        boolean outerCapture = level.captureBlockSnapshots;
        level.captureBlockSnapshots = false;
        try {
            List<BlockSnapshot> snapshots = captureSetBlock(level, pos, newState, flags);
            if (snapshots.isEmpty()) {
                return false;
            }
            if (postPlaceEvent(player, snapshots, face, hand)) {
                restore(level, snapshots);
                return false;
            }
            commit(level, snapshots);
            return true;
        } finally {
            level.captureBlockSnapshots = outerCapture;
        }
    }

    /**
     * 以这名玩家的身份破坏 pos（物品自己算掉落的挖）：先发一次 {@code BreakEvent}（主手拿空），放行才删方块
     * （有破坏粒子和音效，含水的格子留下水），<b>删成功</b>以后 {@code drops} 为 true 时按删之前记下的方块状态和方块实体、按 tool 算掉落
     * （不给经验：原作的机器和法杖都不给）。先删后掉：Mohist 的 {@code destroyBlock} 可能被插件取消（类注释），先掉落就白送一份。
     * 结果顺手更新 {@link #canEdit} 的缓存；整段期间 {@link #isGuardedBreak()} 为 true。执行期间暂停外层捕获：删方块不会被 useOn 的捕获撤回，否则掉落留下、方块回来 = 复制。
     * 破坏事件期间有监听器把这格换掉了（不是发事件时那个状态）也不挖。
     * <p>
     * 玩家拿着工具挖（要算耐久、附魔、经验）的别用这个，用 {@code serverPlayer.gameMode.destroyBlock(pos)}（policy §2.2）。
     * 不要在另一次破坏过程里调用（类注释：Bukkit 破坏事件全局单槽）。
     *
     * @return 是否已经破坏；空气、不可破坏的方块（硬度 &lt; 0）、不是真玩家、区块没加载、出生点保护 / 世界边界、被拒、删除被取消时 false
     */
    public static boolean breakGuarded(@Nullable Player player, Level level, BlockPos pos, ItemStack tool, boolean drops) {
        if (!(level instanceof ServerLevel serverLevel) || !isRealPlayer(player)) {
            return false;
        }
        return destroy((ServerPlayer) player, serverLevel, pos, tool, drops ? DropMode.SPAWN : DropMode.NONE, false) != null;
    }

    /**
     * 同 {@link #breakGuarded}，但<b>不生成掉落物</b>：删成功以后返回这格该掉的东西（{@code Block.getDrops(记下的状态, level, pos, 记下的方块实体, null, tool)}，
     * 时运、精准采集等按 tool 的附魔算），由调用方自己处理（放进物品栏等）。游戏规则 {@code doTileDrops} 关着时返回空表；
     * 不调 {@code spawnAfterBreak}（不出蠹虫、不给经验）。
     *
     * @return 删掉了就是掉落列表（可以改），没删返回 null（同 breakGuarded 返回 false 的情况）
     */
    @Nullable
    public static List<ItemStack> harvestGuarded(@Nullable Player player, Level level, BlockPos pos, ItemStack tool) {
        return harvestGuarded(player, level, pos, tool, false);
    }

    /**
     * 同 {@link #harvestGuarded(Player, Level, BlockPos, ItemStack)}；{@code batch} 为 true 时是给一刻要拆几十格的调用方（创世神之杖）用的批量写法，
     * 权限这一段完全一样（出生点保护 / 世界边界、以玩家身份发破坏事件且主手拿空、事件期间格子被改过就不挖、先删后掉、掉落规则），只差三处：
     * <ul>
     *   <li><b>没有破坏粒子和声音</b>：不调 {@code destroyBlock}（它每格先发 levelEvent 2001，64 格内的客户端每格生成 64 个粒子、放一次声音，
     *       一刻几十格就掉帧），改成同样的 setBlock（格子换成剩下的流体）+ {@code BLOCK_DESTROY} 游戏事件；要效果的由调用方挑几格自己补发。</li>
     *   <li>不发 Mohist 的 ModsEntityDestroyBlockEvent（类注释）；Mohist 的 setBlock 被取消时照样返回 null，不掉落。</li>
     *   <li><b>不写探测缓存</b>：批量调用方不会回头再问这些格子，写进去只会把缓存撑满（超过上限时整表清空，机器的结论也跟着没了）。</li>
     * </ul>
     */
    @Nullable
    public static List<ItemStack> harvestGuarded(@Nullable Player player, Level level, BlockPos pos, ItemStack tool, boolean batch) {
        if (!(level instanceof ServerLevel serverLevel) || !isRealPlayer(player)) {
            return null;
        }
        return destroy((ServerPlayer) player, serverLevel, pos, tool, DropMode.COLLECT, batch);
    }

    /** 挖完以后掉落怎么处理 */
    private enum DropMode {
        /** 不掉落（岩浆法杖熔掉） */
        NONE,
        /** 生成掉落物（{@code Block.dropResources}，不给经验） */
        SPAWN,
        /** 不生成，交给调用方（{@code Block.getDrops}） */
        COLLECT
    }

    /**
     * 代破坏的公共部分：前置检查 → 以 player 发破坏事件（主手拿空）→
     * 记下方块状态和方块实体 → {@code destroyBlock}（批量时 {@link #removeQuietly}；Mohist 都可能取消）→ 删成功才按记下的状态算掉落。
     *
     * @param batch 批量调用（见 {@link #harvestGuarded(Player, Level, BlockPos, ItemStack, boolean)}）：不出破坏效果、不写探测缓存
     * @return 删掉了返回掉落列表（DropMode.COLLECT 以外是空表），没删返回 null
     */
    @Nullable
    private static List<ItemStack> destroy(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack tool, DropMode mode, boolean batch) {
        if (!ECLevels.isFullyLoaded(level, pos) || !level.mayInteract(player, pos)) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(level, pos) < 0) {
            return null;
        }
        guardedBreak++;
        try {
            boolean denied = postBreakEvent(player, level, pos, state, false);
            if (!batch) {
                remember(player, level, pos, !denied);
            }
            if (denied) {
                return null;
            }
            if (level.getBlockState(pos) != state) {
                // 破坏事件的监听器改了这一格：要挖的已经不是问过的那个方块，不挖
                return null;
            }
            BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
            boolean capturing = level.captureBlockSnapshots;
            level.captureBlockSnapshots = false;
            try {
                // 实体传玩家：混合端按它发 ModsEntityDestroyBlockEvent（销毁拦截插件认的是这名玩家）。
                // 批量时不出破坏粒子和声音，也就不走 destroyBlock、不发这个 Mohist 钩子（见 harvestGuarded 的 5 参版）
                boolean removed = batch ? removeQuietly(level, pos, state, player) : level.destroyBlock(pos, false, player);
                if (!removed) {
                    return null;
                }
                List<ItemStack> result = new ArrayList<>();
                if (mode == DropMode.SPAWN) {
                    // 删之后再用删之前的状态和方块实体算（同原版 ServerPlayerGameMode.destroyBlock 先删后 playerDestroy）；容器 onRemove 已经把内容撒出来了
                    Block.dropResources(state, level, pos, blockEntity, player, tool, false);
                } else if (mode == DropMode.COLLECT && level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) {
                    result.addAll(Block.getDrops(state, level, pos, blockEntity, null, tool));
                }
                return result;
            } finally {
                level.captureBlockSnapshots = capturing;
            }
        } finally {
            guardedBreak--;
        }
    }

    /**
     * 同 {@code Level.destroyBlock(pos, false, entity)}，只是不发 levelEvent 2001（破碎粒子和声音），效果由调用方按需补发：
     * 格子换成它剩下的流体（含水的台阶留下水），成功了发 {@code BLOCK_DESTROY} 游戏事件（幽匿感测体照样听得到）。
     *
     * @return 是否删掉了（Mohist 的 setBlock 被插件取消时 false）
     */
    private static boolean removeQuietly(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        if (!level.setBlock(pos, level.getFluidState(pos).createLegacyBlock(), Block.UPDATE_ALL)) {
            return false;
        }
        level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(player, state));
        return true;
    }

    /**
     * 暂停外层的方块快照捕获执行 action（MagicalLanternItem.placePermanent 的写法）。
     * 在 useOn 里自己发事件、自己决定成败的改动用它包起来，免得 ForgeHooks 事后再发一次事件、被拒时连你已经扣过费的改动一起撤回。
     * placeGuarded / breakGuarded / captureGuarded 内部已经处理，不用再包。
     */
    public static void withoutCapture(Level level, Runnable action) {
        boolean capturing = level.captureBlockSnapshots;
        level.captureBlockSnapshots = false;
        try {
            action.run();
        } finally {
            level.captureBlockSnapshots = capturing;
        }
    }

    /** 自己开一次捕获执行 setBlock，返回捕获到的快照；外层捕获的开关和列表原样放回 */
    private static List<BlockSnapshot> captureSetBlock(Level level, BlockPos pos, BlockState state, int flags) {
        boolean outerCapture = level.captureBlockSnapshots;
        ArrayList<BlockSnapshot> shared = level.capturedBlockSnapshots;
        List<BlockSnapshot> outer = shared.isEmpty() ? List.of() : new ArrayList<>(shared);
        shared.clear();
        level.captureBlockSnapshots = true;
        try {
            level.setBlock(pos, state, flags);
            return new ArrayList<>(shared);
        } finally {
            level.captureBlockSnapshots = outerCapture;
            shared.clear();
            shared.addAll(outer);
        }
    }

    /** 被拒：倒序按快照还原，还原期间不掉落、不被任何捕获收进去（同 ForgeHooks） */
    private static void restore(Level level, List<BlockSnapshot> snapshots) {
        if (snapshots.isEmpty()) {
            return;
        }
        boolean capturing = level.captureBlockSnapshots;
        boolean restoring = level.restoringBlockSnapshots;
        level.captureBlockSnapshots = false;
        level.restoringBlockSnapshots = true;
        try {
            for (BlockSnapshot snapshot : Lists.reverse(snapshots)) {
                snapshot.restore(true, false);
            }
        } finally {
            level.restoringBlockSnapshots = restoring;
            level.captureBlockSnapshots = capturing;
        }
    }

    /** 放行：补上捕获期间跳过的 onPlace 和通知（同 ForgeHooks.onPlaceItemIntoWorld 放行后的处理） */
    private static void commit(Level level, List<BlockSnapshot> snapshots) {
        for (BlockSnapshot snapshot : snapshots) {
            BlockPos at = snapshot.getPos();
            BlockState replaced = snapshot.getReplacedBlock();
            BlockState placed = level.getBlockState(at);
            placed.onPlace(level, at, replaced, false);
            level.markAndNotifyBlock(at, level.getChunkAt(at), replaced, placed, snapshot.getFlag(), 512);
        }
    }

    /** 发放置事件，返回是否被取消。有混合端的带手重载且给了手就用它 */
    private static boolean postPlaceEvent(Player player, List<BlockSnapshot> snapshots, Direction face, @Nullable InteractionHand hand) {
        boolean multi = snapshots.size() > 1;
        MethodHandle withHand = multi ? MULTI_PLACE_WITH_HAND : PLACE_WITH_HAND;
        if (hand != null && withHand != null) {
            try {
                return (boolean) withHand.invoke((Entity) player, multi ? snapshots : snapshots.get(0), face, hand);
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable e) {
                throw new IllegalStateException(e);
            }
        }
        return multi ? ForgeEventFactory.onMultiBlockPlace(player, snapshots, face)
                : ForgeEventFactory.onBlockPlace(player, snapshots.get(0), face);
    }

    @Nullable
    private static MethodHandle findWithHand(String name, Class<?> snapshotType) {
        try {
            return MethodHandles.publicLookup().findStatic(ForgeEventFactory.class, name,
                            MethodType.methodType(boolean.class, Entity.class, snapshotType, Direction.class, InteractionHand.class))
                    .asType(MethodType.methodType(boolean.class, Entity.class, Object.class, Direction.class, InteractionHand.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            return null;
        }
    }

    /**
     * 没有玩家可问时（{@link #captureGuarded} 的主人离线、服主关了 {@code require_owner_online}）的出生点保护和世界边界：世界边界照查；
     * 出生点保护照 {@code DedicatedServer.isUnderSpawnProtection} 的条件、把主人当成非 OP 算（不知道离线的人是不是 OP，宁严勿松）。
     * 单人（整合服）没有出生点保护。
     */
    private static boolean mayInteractOffline(ServerLevel level, BlockPos pos) {
        if (!level.getWorldBorder().isWithinBounds(pos)) {
            return false;
        }
        MinecraftServer server = level.getServer();
        int radius = server.getSpawnProtectionRadius();
        if (!server.isDedicatedServer() || level.dimension() != Level.OVERWORLD || radius <= 0 || server.getPlayerList().getOps().isEmpty()) {
            return true;
        }
        BlockPos spawn = level.getSharedSpawnPos();
        return Math.max(Math.abs(pos.getX() - spawn.getX()), Math.abs(pos.getZ() - spawn.getZ())) > radius;
    }

    // ==================================================================
    // 整段捕获：captureGuarded（F4-3）
    // ==================================================================

    /** {@link #captureGuarded(ServerPlayer, Level, Direction, Collection, BooleanSupplier, Predicate)}，不限制生成的实体 */
    public static CaptureResult captureGuarded(@Nullable ServerPlayer owner, Level level, Direction face, Collection<BlockPos> beGuard,
                                               BooleanSupplier action) {
        return captureGuarded(owner, level, face, beGuard, action, null);
    }

    /**
     * 执行一段「不知道会改哪些方块」的代码（例如替主人用物品：方块右键、{@code onItemUseFirst}、对实体、对空气），把它改的方块整段捕获下来，
     * 以 owner 的真身发一次放置事件（1 格 {@code onBlockPlace}、多格 {@code onMultiBlockPlace}，朝向 = face，不报手），被拒就全部还原（F4-3）：
     * <ol>
     *   <li>暂停外层捕获（在别的 useOn 捕获里调用也行：外层的开关和已捕获的列表做完原样放回）；存下 {@code beGuard} 各格的方块状态和方块实体数据
     *       （{@link BlockEntityBackup}，例如用物品时点中的格子和它后面一格）；</li>
     *   <li>自己开快照捕获、开实体收集（{@link #collectSpawns}），跑 action；</li>
     *   <li>捕获被打断（action 里又调了 {@code ItemStack.useOn}：{@code ForgeHooks.onPlaceItemIntoWorld} 调完把捕获开关置 false、把共享的快照列表克隆后清空
     *       （混合端的 {@code ForgeHooks}），之后的 setBlock 全都没被捕获；或者有代码换掉了快照列表对象）→ {@link CaptureResult.Outcome#INTERRUPTED}：
     *       记警告日志，已捕获的照「被拒」还原，{@code refundSafe = false}；</li>
     *   <li>什么都没改 → {@code NO_CHANGE}；改了：每格查出生点保护 / 世界边界，再发放置事件，放行补 onPlace 和邻居通知 → {@code ALLOWED}；</li>
     *   <li>被拒 → {@code DENIED}：按快照倒序还原 → {@code beGuard} 的格子<b>换回使用前的方块实体</b>（{@link BlockEntityBackup#restore()}）→
     *       期间（含还原时）生成的实体全部 {@code discard()}。原因（SAF-1，{@code BlockSnapshot} L63~65 / L118~145）：快照是 {@code setBlock}
     *       那一刻拍的方块实体数据，雕纹书架、唱片机、讲台这类先改方块实体再 setBlock 的，按快照还原会把刚放进去的书 / 唱片装回去，再退回物品 = 复制；
     *       而且唱片机的 {@code load} 在数据里没有唱片时<b>不清空</b>，所以不能 load 回旧数据，要整个换一个按旧数据新建的方块实体。
     *       捕获到的格子里有 {@code beGuard} 以外、快照带方块实体数据的，或 {@code beGuard} 的格子还原后状态对不上 → {@code refundSafe = false}
     *       （没有使用前的数据可还原，宁可少不可多）。</li>
     * </ol>
     * action 抛异常时：已捕获的照「被拒」还原（不许留下没发过事件的改动）、生成的实体删掉，再把异常原样抛出。
     * <p>
     * owner 为 null（主人离线、服主关了 {@code require_owner_online}）：不发事件，改动直接放行；开着在线要求时不跑 action、返回 DENIED。
     * 只在服务端用；客户端不跑 action、返回 DENIED。
     *
     * @param beGuard        被拒时要换回使用前方块实体的格子；没加载的格子忽略
     * @param action         要执行的代码，返回值原样放进 {@link CaptureResult#acted()}（一般是「消耗了动作」），不影响判定
     * @param forbiddenSpawn 期间生成的实体有一个满足它就整次当被拒（不许弹射物时传 {@code e -> e instanceof Projectile}）；null = 不限制
     */
    public static CaptureResult captureGuarded(@Nullable ServerPlayer owner, Level level, Direction face, Collection<BlockPos> beGuard,
                                               BooleanSupplier action, @Nullable Predicate<? super Entity> forbiddenSpawn) {
        if (!(level instanceof ServerLevel serverLevel) || (owner == null ? Config.requireOwnerOnline() : !isRealPlayer(owner))) {
            return new CaptureResult(CaptureResult.Outcome.DENIED, true, false, List.of());
        }
        BlockEntityBackup backup = BlockEntityBackup.of(level, beGuard);
        boolean outerCapture = level.captureBlockSnapshots;
        ArrayList<BlockSnapshot> shared = level.capturedBlockSnapshots;
        List<BlockSnapshot> outer = shared.isEmpty() ? List.of() : new ArrayList<>(shared);
        shared.clear();
        try (SpawnCollector spawns = collectSpawns(level)) {
            List<BlockSnapshot> captured;
            boolean interrupted;
            boolean acted = false;
            Throwable failure = null;
            level.captureBlockSnapshots = true;
            try {
                acted = action.getAsBoolean();
            } catch (RuntimeException | Error e) {
                failure = e;
            } finally {
                // 捕获还开着、列表还是原来那个对象，才说明 action 里的改动全被收进来了
                ArrayList<BlockSnapshot> current = level.capturedBlockSnapshots;
                interrupted = !level.captureBlockSnapshots || current != shared;
                captured = new ArrayList<>(shared);
                if (current != shared) {
                    captured.addAll(current);
                    level.capturedBlockSnapshots = shared;
                }
                // 判定、发事件、还原期间都关着捕获（外层的开关在最外面的 finally 放回），外层列表原样放回
                level.captureBlockSnapshots = false;
                shared.clear();
                shared.addAll(outer);
            }
            if (failure != null) {
                rollback(level, captured, backup, spawns);
                if (failure instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw (Error) failure;
            }
            if (interrupted) {
                EssentialCraft5.LOGGER.warn("[权限] {} 的整段捕获被打断（期间又走了一次物品放置捕获或快照列表被换掉），已尽量还原 {} 处改动，物品不退回；维度 {}，格子 {}",
                        owner == null ? "离线主人" : owner.getGameProfile().getName(), captured.size(), level.dimension().location(), beGuard);
                rollback(level, captured, backup, spawns);
                return new CaptureResult(CaptureResult.Outcome.INTERRUPTED, false, acted, List.of());
            }
            boolean denied = forbiddenSpawn != null && spawns.alive().stream().anyMatch(forbiddenSpawn);
            if (captured.isEmpty() && !denied) {
                return new CaptureResult(CaptureResult.Outcome.NO_CHANGE, true, acted, spawns.alive());
            }
            if (!denied) {
                for (BlockSnapshot snapshot : captured) {
                    BlockPos at = snapshot.getPos();
                    if (!(owner != null ? level.mayInteract(owner, at) : mayInteractOffline(serverLevel, at))) {
                        denied = true;
                        break;
                    }
                }
            }
            if (!denied && owner != null) {
                denied = postPlaceEvent(owner, captured, face, null);
            }
            if (denied) {
                boolean clean = rollback(level, captured, backup, spawns);
                for (BlockSnapshot snapshot : captured) {
                    if (snapshot.getTag() != null && !backup.covers(snapshot.getPos())) {
                        clean = false;
                        break;
                    }
                }
                return new CaptureResult(CaptureResult.Outcome.DENIED, clean, acted, List.of());
            }
            commit(level, captured);
            return new CaptureResult(CaptureResult.Outcome.ALLOWED, true, acted, spawns.alive());
        } finally {
            level.captureBlockSnapshots = outerCapture;
        }
    }

    /** 被拒 / 被打断 / 出异常：倒序按快照还原 → 换回 beGuard 的方块实体 → 删掉期间（含还原时）生成的实体。返回方块实体是否还原干净 */
    private static boolean rollback(Level level, List<BlockSnapshot> captured, BlockEntityBackup backup, SpawnCollector spawns) {
        restore(level, captured);
        boolean clean = backup.restore();
        spawns.discardAll();
        return clean;
    }

    /**
     * {@link #captureGuarded} 的结果。{@link #outcome()} 四种：{@code NO_CHANGE}（没改方块）、{@code ALLOWED}（改了、放行）、
     * {@code DENIED}（被拒或出生点保护 / 生成了不许的实体，已全部还原）、{@code INTERRUPTED}（捕获被打断，已尽量还原）。
     * 调用方只在 {@link #rejected()} 且 {@link #refundSafe()} 时按使用前的副本退回物品。
     */
    public static final class CaptureResult {
        public enum Outcome {
            /** action 没改任何方块（也没生成不许的实体） */
            NO_CHANGE,
            /** 改了方块，以主人身份发的放置事件放行了，改动保留 */
            ALLOWED,
            /** 被拒：方块、beGuard 的方块实体已还原，期间生成的实体已删 */
            DENIED,
            /** 捕获被打断：已捕获的照被拒还原，没被捕获的改动还在；一律不退物品 */
            INTERRUPTED
        }

        private final Outcome outcome;
        private final boolean refundSafe;
        private final boolean acted;
        private final List<Entity> spawned;

        CaptureResult(Outcome outcome, boolean refundSafe, boolean acted, List<Entity> spawned) {
            this.outcome = outcome;
            this.refundSafe = refundSafe;
            this.acted = acted;
            this.spawned = spawned;
        }

        public Outcome outcome() {
            return outcome;
        }

        public boolean is(Outcome value) {
            return outcome == value;
        }

        /** DENIED 或 INTERRUPTED：这次使用不算数 */
        public boolean rejected() {
            return outcome == Outcome.DENIED || outcome == Outcome.INTERRUPTED;
        }

        /**
         * 被拒以后能不能按使用前的副本把物品退回去：世界（含 beGuard 的方块实体）已经确定还原成使用前的样子才为 true。
         * INTERRUPTED 恒为 false；没被拒（NO_CHANGE / ALLOWED）时为 true（不涉及退回）。
         */
        public boolean refundSafe() {
            return refundSafe;
        }

        /** action 的返回值（抛异常以外的情况） */
        public boolean acted() {
            return acted;
        }

        /** NO_CHANGE / ALLOWED 时期间生成、现在还在的实体（弹射物、掉落物……）；被拒时已全部删掉，为空表 */
        public List<Entity> spawned() {
            return spawned;
        }

        @Override
        public String toString() {
            return outcome + (refundSafe ? "" : "(不可退回)");
        }
    }

    // ==================================================================
    // 实体收集（captureGuarded 用）
    // ==================================================================

    /**
     * 开一个实体收集上下文：直到 {@link SpawnCollector#close()}，当前线程上新加入 level 的实体（{@code EntityJoinLevelEvent}，读档加载的不算）都记下来。
     * 用来把「一次被拒的使用」期间生成的掉落物、弹射物删掉。可以嵌套（里外层都记）。用 try-with-resources：
     * <pre>{@code
     * try (ECProtection.SpawnCollector spawns = ECProtection.collectSpawns(level)) {
     *     InteractionResult r = stack.useOn(ctx);              // Forge 自己的放置捕获
     *     if (被拒) { spawns.discardAll(); }
     * }
     * }</pre>
     * 只在服务端线程上用。经验球合并进已有的球（{@code ExperienceOrb.award} 的合并）不经过加入事件，收不到。
     */
    public static SpawnCollector collectSpawns(Level level) {
        SpawnCollector collector = new SpawnCollector(level);
        COLLECTORS.get().push(collector);
        openCollectors++;
        return collector;
    }

    /** 见 {@link #collectSpawns} */
    public static final class SpawnCollector implements AutoCloseable {
        private final Level level;
        private final List<Entity> spawned = new ArrayList<>();
        private boolean closed;

        private SpawnCollector(Level level) {
            this.level = level;
        }

        /** 期间加入这个世界的实体（按加入顺序，含之后已经被删的） */
        public List<Entity> spawned() {
            return Collections.unmodifiableList(spawned);
        }

        /** 期间加入、现在还在世界里的实体 */
        public List<Entity> alive() {
            List<Entity> alive = new ArrayList<>(spawned.size());
            for (Entity entity : spawned) {
                if (!entity.isRemoved()) {
                    alive.add(entity);
                }
            }
            return alive;
        }

        /** 把期间加入、现在还在的实体全部 {@code discard()}（不掉落、不触发死亡），返回删了几个 */
        public int discardAll() {
            int count = 0;
            for (Entity entity : spawned) {
                if (!entity.isRemoved()) {
                    entity.discard();
                    count++;
                }
            }
            return count;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            COLLECTORS.get().remove(this);
            openCollectors--;
        }
    }

    /** 最后处理（被别的监听器取消的没加入，不记）：记进当前线程上开着的、同一个世界的收集上下文 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCaptureEntityJoin(EntityJoinLevelEvent event) {
        if (openCollectors <= 0 || event.loadedFromDisk() || event.getLevel().isClientSide()) {
            return;
        }
        for (SpawnCollector collector : COLLECTORS.get()) {
            if (collector.level == event.getLevel()) {
                collector.spawned.add(event.getEntity());
            }
        }
    }

    // ==================================================================
    // 方块实体备份：被拒时换回使用前的数据（captureGuarded 用）
    // ==================================================================

    /**
     * 存下几格的方块状态和方块实体数据（{@code saveWithFullMetadata}），之后 {@link #restore()} 把方块实体换回存的样子。
     * 给「探测放行、真改被拒」的还原用（policy §2.1 ⑨）：放置快照在 setBlock 那一刻才拍方块实体数据，先改方块实体再 setBlock 的方块
     * （雕纹书架、唱片机、讲台）按快照还原后还留着刚放进去的东西。
     * <p>
     * <b>不用 {@code load} 覆盖</b>：唱片机的 {@code load} 在数据里没有唱片时不清空（原版 {@code JukeboxBlockEntity} L39~48），
     * 按旧数据 load 回去唱片还在。这里按旧数据新建一个方块实体（{@code BlockEntity.loadStatic}）整个换上（{@code level.setBlockEntity}，同区块读档：
     * 旧的 setRemoved、能力失效，新的 onLoad、重挂 ticker）；数据没变的格子不换。
     * <pre>{@code
     * ECProtection.BlockEntityBackup backup = ECProtection.BlockEntityBackup.of(level, List.of(p, p.relative(dir)));   // 使用之前
     * ...被拒、世界按快照还原之后...
     * boolean clean = backup.restore();   // false：有格子的方块状态和使用前对不上（有没被捕获的改动），别退回物品
     * }</pre>
     */
    public static final class BlockEntityBackup {
        private final Level level;
        private final List<Entry> entries;

        private record Entry(BlockPos pos, BlockState state, @Nullable CompoundTag data) {
        }

        private BlockEntityBackup(Level level, List<Entry> entries) {
            this.level = level;
            this.entries = entries;
        }

        /** 存下这些格子（没完全加载的格子跳过，不触发区块加载；重复的只存一次） */
        public static BlockEntityBackup of(Level level, Collection<BlockPos> cells) {
            List<Entry> entries = new ArrayList<>(cells.size());
            Set<BlockPos> seen = new HashSet<>();
            for (BlockPos cell : cells) {
                BlockPos pos = cell.immutable();
                if (!seen.add(pos) || !ECLevels.isFullyLoaded(level, pos)) {
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
                entries.add(new Entry(pos, state, blockEntity == null ? null : blockEntity.saveWithFullMetadata()));
            }
            return new BlockEntityBackup(level, entries);
        }

        /** 这一格有没有存下来 */
        public boolean covers(BlockPos pos) {
            for (Entry entry : entries) {
                if (entry.pos().equals(pos)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * 把存下的格子的方块实体换回存的数据（方块状态和存的时候一样才换）。
         *
         * @return 全部干净：每格的方块状态都和存的时候一样、方块实体都换回去了；false = 有格子对不上（有没被还原的改动），调用方别退回物品
         */
        public boolean restore() {
            boolean clean = true;
            for (Entry entry : entries) {
                BlockPos pos = entry.pos();
                if (!ECLevels.isFullyLoaded(level, pos) || level.getBlockState(pos) != entry.state()) {
                    clean = false;
                    continue;
                }
                CompoundTag data = entry.data();
                if (data == null) {
                    continue;
                }
                BlockEntity current = level.getBlockEntity(pos);
                if (current != null && data.equals(current.saveWithFullMetadata())) {
                    continue;
                }
                BlockEntity fresh = BlockEntity.loadStatic(pos, entry.state(), data);
                if (fresh == null) {
                    clean = false;
                    continue;
                }
                level.setBlockEntity(fresh);
                fresh.setChanged();
                level.sendBlockUpdated(pos, entry.state(), entry.state(), Block.UPDATE_ALL);
            }
            return clean;
        }
    }

    // ==================================================================
    // 爆炸：safeExplode
    // ==================================================================

    /**
     * 不破坏场景的爆炸（爆炸可以有，但不破坏方块）：{@code Explosion.BlockInteraction.KEEP}、不放火，
     * 只伤活体生物——展示框、画、盔甲架、掉落物、经验球（policy 点名的），以及矿车、船、末地水晶、拴绳结、弹射物这些非生物实体
     * 都从受影响的实体里拿掉（同样是场景或别人的东西）。伤害照原版爆炸公式，击退照常，客户端照常看到爆炸。
     * 只对经这里发起的爆炸生效（按伤害源实例认），别的爆炸不受影响。
     *
     * @param source   爆炸本体（原版 Explosion 的 source）：这次爆炸伤不到它；混合端把它当成伤害来源报给插件（PvP、领地规则看它）。
     *                 为 null 时用 attacker 顶上——两个都是 null 的爆炸在插件眼里只是「方块爆炸」，PvP 和领地规则就管不到了。
     *                 <b>注意</b>：原版爆炸不伤 source，所以顶上以后 attacker 本人不会被这次爆炸伤到；
     *                 「自己炸自己」（腐化 #13）要自己按原版公式补上本人的伤害（见 ECCorruption 的 CorruptionBehaviors.selfBlast）
     * @param attacker 伤害记在谁头上（DamageSource 的 causing entity：击杀归属、仇恨）。玩家引起的爆炸传玩家本人
     * @param radius   原版爆炸威力（苦力怕 3、TNT 4），夹到 [0, {@link #MAX_EXPLOSION_RADIUS}]
     * @return 这次爆炸（可以读 {@code getHitPlayers()}）；客户端调用时什么都不做、返回 null
     */
    @Nullable
    public static Explosion safeExplode(Level level, @Nullable Entity source, @Nullable Entity attacker, Vec3 pos, float radius) {
        if (level.isClientSide) {
            return null;
        }
        Entity exploder = source != null ? source : attacker;
        return explodeMarked(level, exploder, level.damageSources().explosion(exploder, attacker), pos, radius);
    }

    /** 同上，爆炸中心取方块中心 */
    @Nullable
    public static Explosion safeExplode(Level level, @Nullable Entity source, @Nullable Entity attacker, BlockPos pos, float radius) {
        return safeExplode(level, source, attacker, Vec3.atCenterOf(pos), radius);
    }

    /**
     * 同上，伤害源自己给（例：恶魔的 {@code essentialcraft5:demon}，被它炸死不掉落）。会复制一份新的伤害源用来认这次爆炸，传进来的实例不会被记下。
     * 伤害源最好带攻击者（{@code getEntity()}），理由同上。
     */
    @Nullable
    public static Explosion safeExplode(Level level, @Nullable Entity source, DamageSource damage, Vec3 pos, float radius) {
        if (level.isClientSide) {
            return null;
        }
        return safeExplode(level, source, damage, pos, radius, null);
    }

    /**
     * 同上，再加一个过滤：只有 {@code affects} 放行的生物受伤、被击退，别的生物在这次爆炸里就像不在场（例：恶魔清怪的爆炸只伤清怪目标那一类，
     * 玩家、宠物、村民不受伤也不被推开，batch7 §4.1）。场景实体照旧先剔掉，过滤只看剩下的活体生物；{@code affects} 为 null 时同上一个重载。
     * 剔除在 {@code ExplosionEvent.Detonate} 里做（最先处理），原版随后只对列表里剩下的实体算伤害和击退，所以被剔除的连击退也没有。
     */
    @Nullable
    public static Explosion safeExplode(Level level, @Nullable Entity source, DamageSource damage, Vec3 pos, float radius,
                                        @Nullable Predicate<? super LivingEntity> affects) {
        if (level.isClientSide) {
            return null;
        }
        Entity exploder = source != null ? source : damage.getEntity();
        DamageSource copy = new DamageSource(damage.typeHolder(), damage.getDirectEntity(), damage.getEntity(), damage.sourcePositionRaw());
        return explodeMarked(level, exploder, copy, pos, radius, affects);
    }

    /** 这次爆炸是不是 safeExplode 发起的（别的 Detonate 监听器想区分时用） */
    public static boolean isSafeExplosion(Explosion explosion) {
        return SAFE_EXPLOSIONS.contains(explosion.getDamageSource());
    }

    private static Explosion explodeMarked(Level level, @Nullable Entity exploder, DamageSource damage, Vec3 pos, float radius) {
        return explodeMarked(level, exploder, damage, pos, radius, null);
    }

    private static Explosion explodeMarked(Level level, @Nullable Entity exploder, DamageSource damage, Vec3 pos, float radius,
                                           @Nullable Predicate<? super LivingEntity> affects) {
        float clamped = Mth.clamp(radius, 0F, MAX_EXPLOSION_RADIUS);
        SAFE_EXPLOSIONS.add(damage);
        if (affects != null) {
            SAFE_EXPLOSION_FILTERS.put(damage, affects);
        }
        try {
            // NONE → BlockInteraction.KEEP；fire = false。ServerLevel.explode 在 KEEP 时还会清空受影响方块再发包
            return level.explode(exploder, damage, null, pos.x, pos.y, pos.z, clamped, false, Level.ExplosionInteraction.NONE);
        } finally {
            SAFE_EXPLOSIONS.remove(damage);
            SAFE_EXPLOSION_FILTERS.remove(damage);
        }
    }

    /** 最先处理：后面的监听器（含本模组富集仓的外壳复查）看到的已经是剔除后的列表 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDetonate(ExplosionEvent.Detonate event) {
        if (!isSafeExplosion(event.getExplosion())) {
            return;
        }
        event.getAffectedBlocks().clear();
        event.getAffectedEntities().removeIf(ECProtection::isScenery);
        Predicate<? super LivingEntity> affects = SAFE_EXPLOSION_FILTERS.get(event.getExplosion().getDamageSource());
        if (affects != null) {
            // isScenery 之后剩下的都是活体生物；这里再按调用方的判定剔一遍
            event.getAffectedEntities().removeIf(e -> e instanceof LivingEntity living && !affects.test(living));
        }
    }

    /** 爆炸不该碰的实体：除了活体生物都算，盔甲架虽然是 LivingEntity 也算 */
    private static boolean isScenery(Entity entity) {
        return !(entity instanceof LivingEntity) || entity instanceof ArmorStand;
    }

    // ==================================================================
    // 机器主人（policy §2.3、§2.3a）
    // ==================================================================

    /**
     * 记下机器的主人（{@code MachineBlock.setPlacedBy} 调）：放置者是真玩家就记他的 UUID 和名字，否则清空
     * （物品带着 BlockEntityTag 被假玩家放下时不继承原来的主人）。
     */
    public static void recordOwner(MRUBlockEntity machine, @Nullable Entity placer) {
        if (isRealPlayer(placer)) {
            ServerPlayer player = (ServerPlayer) placer;
            machine.setOwner(player.getUUID(), player.getGameProfile().getName());
        } else {
            machine.setOwner(null, "");
        }
    }

    /** 记下的主人的 UUID；没有主人返回 null（单人里「算房主的」不改这里，要「实际替谁干活」用 {@link #actingOwner}） */
    @Nullable
    public static UUID owner(MRUBlockEntity machine) {
        return machine.getOwner();
    }

    /**
     * 主人的名字：记下的主人放下机器时的名字（在线时会刷新成当前名字）；没有记下主人、或者在没开局域网的单人里记下的主人不在线时，
     * 是房主的名字（和 {@link #onlineOwner} 一致），多人服里没有主人是空串
     */
    public static String ownerName(MRUBlockEntity machine) {
        UUID id = machine.getOwner();
        if (id == null) {
            ServerPlayer host = singleplayerHost(machine);
            return host == null ? "" : host.getGameProfile().getName();
        }
        MinecraftServer server = serverOf(machine);
        if (isSoloWorld(server) && onlinePlayer(server, id) == null) {
            ServerPlayer host = singleplayerHost(server);
            if (host != null) {
                return host.getGameProfile().getName();
            }
        }
        return machine.getOwnerName();
    }

    /**
     * 实际替谁干活的 UUID：记下的主人；没有记下主人时，单人（整合服）里是房主（policy §2.3a），否则 null。
     * 没开局域网的单人里记下的主人不在线（例如把服务器存档拿到单人里玩、离线账号改了名字，UUID 对不上），也算房主的：
     * 那里除了房主不可能有别人上线，按记下的人算机器就永远停着（4a 审查 LOG-1）。专用服、开了局域网的单人不变。
     * 判断「宠物的主人是不是机器主人」这类归属用它。
     */
    @Nullable
    public static UUID actingOwner(MRUBlockEntity machine) {
        UUID id = machine.getOwner();
        MinecraftServer server = serverOf(machine);
        if (id != null) {
            if (isSoloWorld(server) && onlinePlayer(server, id) == null) {
                return server.getSingleplayerProfile().getId();
            }
            return id;
        }
        GameProfile host = server == null || server.isDedicatedServer() ? null : server.getSingleplayerProfile();
        return host == null ? null : host.getId();
    }

    /**
     * 在线的主人（真玩家，含测试覆盖 {@link #setOnlineOverride}）；主人不在线、机器不在服务端世界里返回 null。顺手把改过的名字记下来。
     * <b>没有记下主人的机器</b>：多人服返回 null（不动世界）；单人（整合服，含开了局域网的）里算房主的，房主在线就返回房主
     * （policy §2.3a：单机玩家用别的模组摆的机器也要能干活），不会把房主写成记下的主人。
     * <b>没开局域网的单人里记下的主人不在线</b>：同样返回房主（见 {@link #actingOwner}），也不把房主的名字写到记下的主人上。
     */
    @Nullable
    public static ServerPlayer onlineOwner(MRUBlockEntity machine) {
        UUID id = machine.getOwner();
        MinecraftServer server = serverOf(machine);
        if (server == null) {
            return null;
        }
        if (id == null) {
            return singleplayerHost(server);
        }
        ServerPlayer player = onlinePlayer(server, id);
        if (player == null) {
            // 必须在下面刷新名字之前返回：房主不是记下的那个人，别把房主的名字写到旧 UUID 上
            return isSoloWorld(server) ? singleplayerHost(server) : null;
        }
        String name = player.getGameProfile().getName();
        if (!name.equals(machine.getOwnerName())) {
            machine.setOwner(id, name);
        }
        return player;
    }

    /**
     * 按 UUID 记「替谁干活」的东西（不是机器：恶魔记的召唤者等）实际替谁干活、而且这人在线：记下的人在线就是他；
     * 没记下人时，单人（整合服）里是在线的房主；没开局域网的单人里记下的人不在线也算房主的（和 {@link #onlineOwner} 同一套规则，只是不刷新名字）。
     * 多人服里记下的人不在线、或者没记下人，返回 null。
     */
    @Nullable
    public static ServerPlayer onlineActing(@Nullable MinecraftServer server, @Nullable UUID owner) {
        if (server == null) {
            return null;
        }
        if (owner == null) {
            return singleplayerHost(server);
        }
        ServerPlayer player = onlinePlayer(server, owner);
        if (player == null && isSoloWorld(server)) {
            return singleplayerHost(server);
        }
        return player;
    }

    public static OwnerState ownerState(MRUBlockEntity machine) {
        if (machine.getOwner() == null) {
            return onlineOwner(machine) != null ? OwnerState.ONLINE : OwnerState.NONE;
        }
        return onlineOwner(machine) != null ? OwnerState.ONLINE : OwnerState.OFFLINE;
    }

    /**
     * 机器替主人动 pos 之前问这一句：没有主人 → DENY（单人里算房主的）；主人在线 → 用主人真身 {@link #check}（预算按机器算）；
     * 主人不在线 → 默认 UNKNOWN（停工，等主人上线；别记成拒绝），服主关了 {@code require_owner_online} 时 ALLOW（不做检查）。
     * 没开局域网的单人里记下的主人不在线时算房主的（{@link #onlineOwner}），用房主探测，不会停工。
     */
    public static Verdict checkAsOwner(MRUBlockEntity machine, BlockPos pos) {
        Level level = machine.getLevel();
        if (level == null) {
            return Verdict.DENY;
        }
        ServerPlayer owner = onlineOwner(machine);
        if (owner != null) {
            return check(owner, level, pos, machine);
        }
        if (machine.getOwner() == null) {
            return Verdict.DENY;
        }
        return Config.requireOwnerOnline() ? Verdict.UNKNOWN : Verdict.ALLOW;
    }

    @Nullable
    private static ServerPlayer singleplayerHost(MRUBlockEntity machine) {
        return singleplayerHost(serverOf(machine));
    }

    @Nullable
    private static MinecraftServer serverOf(MRUBlockEntity machine) {
        Level level = machine.getLevel();
        return level == null ? null : level.getServer();
    }

    /**
     * 没开局域网的单人（整合服）：除了房主不可能有别人上线，记下的主人不在线时算房主的（{@link #actingOwner}、{@link #onlineOwner}）。
     * {@code isSingleplayer()} 就是「有房主档案」，专用服（含混合端）和 GameTest 服都是 false；开了局域网的别人可能上线，照记下的主人算。
     */
    private static boolean isSoloWorld(@Nullable MinecraftServer server) {
        return server != null && server.isSingleplayer() && !server.isPublished();
    }

    /** 单人（整合服）的房主，在线才返回；专用服（含混合端、GameTest 服）恒为 null */
    @Nullable
    private static ServerPlayer singleplayerHost(@Nullable MinecraftServer server) {
        if (server == null || server.isDedicatedServer()) {
            return null;
        }
        GameProfile host = server.getSingleplayerProfile();
        return host == null ? null : onlinePlayer(server, host.getId());
    }

    // ==================================================================
    // 生命周期
    // ==================================================================

    /** 下线就不用再记他的探测结果了 */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!event.getEntity().level().isClientSide) {
            forget(event.getEntity().getUUID());
        }
    }

    /** 单人游戏换存档时服务器 tick 从 0 重新数，旧缓存的到期时间会错，开服、关服都清一遍 */
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        reset();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        reset();
    }

    private static void reset() {
        CACHE.clear();
        BUDGET.clear();
        PLAYER_BUDGET.clear();
        SAFE_EXPLOSIONS.clear();
        SAFE_EXPLOSION_FILTERS.clear();
        ONLINE_OVERRIDES.clear();
        Config.clearTestOverrides();
        // 正常都在 try-with-resources 里关掉了；这里只清服务端线程上万一漏掉的（开服 / 关服事件都在服务端线程上）
        openCollectors = Math.max(0, openCollectors - COLLECTORS.get().size());
        COLLECTORS.get().clear();
        budgetTick = Integer.MIN_VALUE;
        lastSweep = 0;
        probing = 0;
        guardedBreak = 0;
    }
}
