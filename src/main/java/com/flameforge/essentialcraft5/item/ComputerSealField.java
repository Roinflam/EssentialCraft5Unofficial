package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IMRUHandlerItem;
import com.flameforge.essentialcraft5.api.event.CurioSealEvent;
import com.flameforge.essentialcraft5.compat.curio.CurioSeal;
import com.flameforge.essentialcraft5.compat.curio.CurioSealConfig;
import com.flameforge.essentialcraft5.item.module.ModuleTerminal;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.network.C2SComputerTerminalPacket;
import com.flameforge.essentialcraft5.network.ECNetwork;
import com.flameforge.essentialcraft5.network.S2CComputerTerminalPacket;
import com.flameforge.essentialcraft5.network.S2CComputerTerminalPacket.FieldState;
import com.flameforge.essentialcraft5.network.S2CComputerTerminalPacket.TargetState;
import com.flameforge.essentialcraft5.util.ECCombatTags;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.flameforge.essentialcraft5.util.OwnerCellMemory;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 宅男套装的饰品封禁场（第十批 10a-M1，tools/plan/batch10.md §5.1）：穿齐四件、在宅男终端里打开，半径内能压制的玩家饰品栏里的东西全部失效。
 * 压制本身由 {@link CurioSeal}（饰品压制核心）做，这里只决定「每一秒压制谁、付多少电」。
 *
 * <h2>评估（每名开着场的穿戴者每 {@code evaluate_interval} 刻一次）</h2>
 * {@link TickEvent.PlayerTickEvent} 末尾、按 {@code (tickCount + 实体 id) % 间隔} 错开；GameTest 直接调 {@link #evaluate}。
 * <ol>
 *   <li>开关关着 → 关；功能不可用 → 不可用；没穿齐四件 → 没穿齐；所在维度在 {@code disabled_dimensions} 里 → 这里不起作用；旁观、死了 → 待机。
 *       这几种都撤掉自己登记的全部压制、不扣电。</li>
 *   <li>候选人：同一世界、活着的真玩家（不含假玩家和自己），球形 {@code radius} 格内；已在场内的人超过 {@code radius + 2} 才算离开。</li>
 *   <li>对每个候选人<b>依次</b>过门，前一步不过就不做后一步：创造 / 旁观不压 → {@code 穿戴者.canHarmPlayer(目标)}（PvP 开关、队伍友伤，
 *       混合端的按世界 PvP 也在这里）→ {@code require_recent_combat} 开着时（默认关）两人最近 {@code combat_window_ticks} 刻内交过手
 *       （{@link ECCombatTags#landedEither}，任一方向有过一下被放行的伤害）→ {@code respect_claims} 开着时以穿戴者身份探测目标脚下那格
 *       （{@link ECProtection#check}，含出生点保护）。探测 UNKNOWN（预算用完、区块没加载）时保持这人上一次的结论，新来的先不压。
 *       探测按 policy §2.4「少探测、长缓存」压着：本场按格记结论，能动的记 {@code probe_cache_ticks}、不能动的记 5 分钟；
 *       刚被拒过的人 {@code probe_cache_ticks} 内换了格子也沿用「没有权限」、不再探测；每次评估新探测最多 {@code max_probes_per_tick} 格（默认 4）；
 *       第一次真要探测时先看付不付得起一人份（{@code (mru_base + mru_per_target) × 间隔 / 20}），付不起这次一格都不探测，这些人记「电量不足」。
 *       探测以穿戴者身份发破坏事件，被领地插件拒绝时「没有权限」的提示发给穿戴者。</li>
 *   <li>有人过门才运转：总价 = ({@code mru_base} + {@code mru_per_target} × 人数) × 间隔 / 20，<b>先算后扣</b>——
 *       {@link MRUDrain#canAfford} 够才扣、才 {@link CurioSeal#hold}；不够就这一秒谁都不压、一分不扣，转「电量不足」并提示一次
 *       （没人过门、但有人因为付不起一人份没探测时同样转「电量不足」；范围里没人、全走缓存时照旧待机）。
 *       运转时穿戴者自己也压（{@code affects_self}，创造模式不压、不扣电）；这时他饰品栏里的储存物品从第一次付钱起就不算（压住以后本来就不算）。</li>
 *   <li>这一秒不再压制的人马上撤掉自己这份来源（仍受 {@code min_hold_ticks}）；压制的到期刻 = 现在 + 间隔 + {@value #HOLD_SLACK}，漏一次评估不断。</li>
 * </ol>
 * 「交过手」这一步默认关：打开以后，插件靠取消伤害事件做的 PvP 保护（区域关 PvP、个人 PvP 开关、新手保护期）和 Forge 端在伤害事件里拦的
 * PvP / 领地模组也能照顾到——这些在不伤人的前提下问不到，假装打一下也不行（混合端上 0 伤害照样发真的伤害事件），服务器刚放行过两人之间的
 * 一下伤害是唯一可靠的信号；领地探测也只对正在交手的人做，远处的破坏探测少得多。关着时只认 {@code canHarmPlayer} 和领地两道门，
 * 不想让它在某些世界（例如主城）里起作用就把维度写进 {@code disabled_dimensions}。
 *
 * <h2>状态与提示</h2>
 * 开关存在玩家持久数据 PlayerPersisted 下的 {@value #FIELD_TAG}（同夜视：跟人不跟盔甲，重登、死亡保留）。
 * 场的状态（关 / 待机 / 运转 / 电量不足 / 没穿齐 / 不可用）变了就给本人推一个只带状态的 {@link S2CComputerTerminalPacket}（屏幕顶上的紫色小圆、终端开关按它画）；
 * 终端开着时客户端每 20 刻要一份带名单的；名单只列穿戴者看得见的人（{@link #visibleTo}）。提示的合并与去抖在 {@link SealNotifier}。
 * 压制开始那一刻人在空中（不在地上、没在滑翔、不在水里、没爬梯子、没骑乘）给一次 {@code slow_fall_ticks} 的缓降（{@link #onSealFieldSealed}），
 * 免得飞行饰品一失效就摔死；靠饰品栏里的鞘翅滑翔的人（{@link #glidingOnCurio}）压制一开始滑翔就停，同样给。同一次压制只给一次（事件只在状态翻转时发）。
 *
 * <h2>下线、死亡、换维度</h2>
 * 穿戴者死亡、换维度时撤掉他登记的全部压制（{@link CurioSeal#dropEverywhere}），下线由压制核心自己撤；场的运行状态（场内名单、探测缓存）一并丢掉。
 * 被压制的一方换维度、下线、重生由压制核心处理。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class ComputerSealField {

    /** 开关在玩家持久数据里的键（PlayerPersisted 下） */
    public static final String FIELD_TAG = "essentialcraft5:computer_seal_field";
    /** 手册「饰品封禁场」一步：第一次在终端里打开时发 */
    public static final ResourceLocation GUIDE_SEAL_FIELD = EssentialCraft5.id("guide/computer/seal_field");
    /** 开关包同一人最短间隔（刻） */
    public static final int TOGGLE_MIN_INTERVAL = 5;
    /** 刷新包同一人最短间隔（刻） */
    public static final int REFRESH_MIN_INTERVAL = 10;
    /** 压制到期刻在下一次评估之后再留这么多刻 */
    public static final int HOLD_SLACK = 10;
    /** 本地探测缓存超过这么多格时清一次过期的 */
    private static final int PROBE_CACHE_PRUNE = 256;

    /**
     * 一次评估的结果（GameTest 断言用）。
     *
     * @param state      评估后的场状态
     * @param sealed     这一秒压制的别的玩家
     * @param selfSealed 穿戴者自己是否也压了
     * @param cost       这一次扣（或要扣却付不起）的 MRU
     * @param statuses   范围内每个人的状态
     */
    public record Result(FieldState state, List<ServerPlayer> sealed, boolean selfSealed, int cost, Map<ServerPlayer, TargetState> statuses) {

        public TargetState statusOf(Player player) {
            return statuses.get(player);
        }
    }

    /** 一名穿戴者的场（运行时状态，不存盘；只在服务端线程读写） */
    private static final class Field {
        FieldState state = FieldState.OFF;
        @Nullable
        ResourceKey<Level> dimension;
        /** 已在场内的人（离开要超过半径 + 2） */
        final Set<UUID> inside = new HashSet<>();
        /** 领地探测上一次给出结论的结果（UNKNOWN 时沿用） */
        final Map<UUID, Boolean> claimVerdict = new HashMap<>();
        /**
         * 本场自己的探测缓存：格子 → (到期刻 &lt;&lt; 1 | 能不能动)。能动的记 {@code probe_cache_ticks}，不能动的记
         * {@link OwnerCellMemory#DENY_TICKS}（5 分钟，同线圈 / 恶魔记主人不能动的格子），站在邻居领地里挂机的人 5 分钟才探测一次
         */
        final Long2LongOpenHashMap probeCache = new Long2LongOpenHashMap();
        /**
         * 按人沿用「不能动」：新探测得到 DENY 时记 现在 + {@code probe_cache_ticks}，到期前这个人换了格子（缓存没命中）也不再探测，直接算没有权限。
         * 只沿用 DENY：结论只会少罩、不会误罩；能动的不沿用，人走进领地要马上解除
         */
        final Map<UUID, Long> denyUntil = new HashMap<>();
        /** 自己正在压制的人（UUID → 玩家对象），不再压制时撤自己这份 */
        final Map<UUID, ServerPlayer> holding = new HashMap<>();
        int mruPerSecond;
        int sealedOthers;
        List<S2CComputerTerminalPacket.Entry> entries = List.of();
        /** 上次推给本人的状态（变了才推） */
        int pushedState = -1;
        int pushedSealed = -1;

        Field() {
            probeCache.defaultReturnValue(Long.MIN_VALUE);
        }
    }

    private static final Map<UUID, Field> FIELDS = new HashMap<>();
    private static final Map<Player, Long> LAST_TOGGLE = new WeakHashMap<>();
    private static final Map<Player, Long> LAST_REFRESH = new WeakHashMap<>();
    private static final Map<UUID, List<S2CComputerTerminalPacket>> TERMINAL_CAPTURE = new ConcurrentHashMap<>();
    /** 谁的客户端正显示着谁：观察者 UUID → 他正在追踪的玩家 UUID（Forge 的 StartTracking / StopTracking 维护，终端名单只列这些人） */
    private static final Map<UUID, Set<UUID>> TRACKING = new HashMap<>();
    /** 每个服务端刻开头取一次配置（玩家刻里每人每刻都要看间隔） */
    private static CurioSealConfig.Values tickValues = CurioSealConfig.DEFAULTS;

    private ComputerSealField() {
    }

    // ------------------------------------------------------------------ 开关

    /** 场开着（玩家持久数据的 PlayerPersisted 下，死亡复制玩家时 Forge 会带过去） */
    public static boolean isOn(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getBoolean(FIELD_TAG);
    }

    /** 直接设开关（GameTest 用；玩家走 {@link #toggle}） */
    public static void setOn(Player player, boolean on) {
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        if (on) {
            persisted.putBoolean(FIELD_TAG, true);
        } else {
            persisted.remove(FIELD_TAG);
        }
        root.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    // ------------------------------------------------------------------ 评估

    /**
     * 评估一次（见类注释）。candidates 是要看的玩家（游戏里是穿戴者所在世界的全部玩家，GameTest 传自己建的）；now 是压制表的时钟
     * {@link CurioSeal#now}（也是交手记录的时钟）。会扣电、登记压制、推状态、发提示，压制状态在这一刻末尾的结算里才翻转。
     */
    public static Result evaluate(ServerPlayer wearer, Collection<? extends Player> candidates, long now) {
        CurioSealConfig.Values cfg = CurioSealConfig.values();
        Field field = FIELDS.computeIfAbsent(wearer.getUUID(), id -> new Field());
        ResourceKey<Level> dimension = wearer.level().dimension();
        if (field.dimension != dimension) {
            field.dimension = dimension;
            field.inside.clear();
            field.claimVerdict.clear();
            field.probeCache.clear();
            field.denyUntil.clear();
        }
        FieldState blocked = blockedState(wearer);
        if (blocked != null || wearer.isSpectator() || !wearer.isAlive()) {
            FieldState state = blocked != null ? blocked : FieldState.STANDBY;
            stop(wearer, field, state);
            return new Result(state, List.of(), false, 0, Map.of());
        }

        double radius = cfg.radius();
        double enterSq = radius * radius;
        double leaveSq = (radius + 2D) * (radius + 2D);
        int tick = (int) now;
        int probesLeft = cfg.maxProbesPerTick();
        int interval = cfg.evaluateInterval();
        ItemStack chest = wearer.getItemBySlot(EquipmentSlot.CHEST);
        // 运转就会把自己的饰品栏也封住：那里的储存从第一次就不算，免得「这一秒用饰品栏的电付了、下一秒被封了付不起」来回启停
        boolean skipOwnCurios = selfPays(wearer, cfg);
        // 付不付得起一人份：第一次真要探测时才算（范围里没人、全走缓存时不算，不会把「待机」变成「电量不足」）；付不起就不探测
        Boolean affordOne = null;
        boolean unpowered = false;
        Map<ServerPlayer, TargetState> statuses = new LinkedHashMap<>();
        Map<ServerPlayer, Double> distances = new HashMap<>();
        List<ServerPlayer> pass = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (Player candidate : candidates) {
            if (!(candidate instanceof ServerPlayer target) || target instanceof FakePlayer || target == wearer
                    || target.getUUID().equals(wearer.getUUID()) || !target.isAlive() || target.level() != wearer.level()) {
                continue;
            }
            double distSq = target.distanceToSqr(wearer);
            if (distSq > (field.inside.contains(target.getUUID()) ? leaveSq : enterSq)) {
                continue;
            }
            seen.add(target.getUUID());
            distances.put(target, Math.sqrt(distSq));
            TargetState status;
            if (target.isCreative() || target.isSpectator()) {
                status = TargetState.EXEMPT_MODE;
            } else if (!wearer.canHarmPlayer(target)) {
                status = TargetState.CANT_HARM;
            } else if (cfg.requireRecentCombat() && !ECCombatTags.landedEither(wearer, target, tick, cfg.combatWindowTicks())) {
                status = TargetState.NO_FIGHT;
            } else if (cfg.respectClaims()) {
                Boolean allowed = null;
                boolean cantPay = false;
                UUID id = target.getUUID();
                BlockPos pos = target.blockPosition();
                long key = pos.asLong();
                long cached = field.probeCache.get(key);
                Long deniedUntil = field.denyUntil.get(id);
                if (cached != Long.MIN_VALUE && (cached >> 1) > now) {
                    allowed = (cached & 1L) != 0L;
                } else if (deniedUntil != null && deniedUntil > now) {
                    // 刚被拒过的人换了格子：沿用「没有权限」，不再探测（最多晚 probe_cache_ticks 才罩住走出领地的人）
                    allowed = false;
                } else if (probesLeft > 0) {
                    if (affordOne == null) {
                        long one = ((long) cfg.mruBase() + cfg.mruPerTarget()) * interval / 20L;
                        affordOne = MRUDrain.canAfford(wearer, chest, (int) Math.min(Integer.MAX_VALUE, one), skipOwnCurios);
                    }
                    if (affordOne) {
                        probesLeft--;
                        ECProtection.Verdict verdict = ECProtection.check(wearer, wearer.level(), pos, field);
                        if (verdict != ECProtection.Verdict.UNKNOWN) {
                            allowed = verdict.allowed();
                            int ttl = allowed ? ECProtection.Config.probeCacheTicks() : OwnerCellMemory.DENY_TICKS;
                            field.probeCache.put(key, ((now + ttl) << 1) | (allowed ? 1L : 0L));
                            if (allowed) {
                                field.denyUntil.remove(id);
                            } else {
                                field.denyUntil.put(id, now + ECProtection.Config.probeCacheTicks());
                            }
                        }
                    } else {
                        cantPay = true;
                    }
                }
                if (cantPay) {
                    status = TargetState.UNPOWERED;
                    unpowered = true;
                } else {
                    if (allowed == null) {
                        allowed = field.claimVerdict.get(id);
                    } else {
                        field.claimVerdict.put(id, allowed);
                    }
                    status = allowed == null ? TargetState.PENDING : allowed ? TargetState.SEALED : TargetState.NO_PERMISSION;
                }
            } else {
                status = TargetState.SEALED;
            }
            statuses.put(target, status);
            if (status == TargetState.SEALED) {
                pass.add(target);
            }
        }
        field.inside.clear();
        field.inside.addAll(seen);
        field.claimVerdict.keySet().retainAll(seen);
        field.denyUntil.keySet().retainAll(seen);
        if (field.probeCache.size() > PROBE_CACHE_PRUNE) {
            field.probeCache.long2LongEntrySet().removeIf(entry -> (entry.getLongValue() >> 1) <= now);
        }

        // 没人过门、但有人因为付不起一人份而没探测：按一人份的价算「电量不足」
        long perSecondLong = pass.isEmpty() ? (unpowered ? (long) cfg.mruBase() + cfg.mruPerTarget() : 0L)
                : (long) cfg.mruBase() + (long) cfg.mruPerTarget() * pass.size();
        int perSecond = (int) Math.min(Integer.MAX_VALUE, perSecondLong);
        int cost = (int) Math.min(Integer.MAX_VALUE, perSecondLong * interval / 20L);
        FieldState state;
        boolean selfSealed = false;
        // 电量不足时进出提示不动：名单上的人并没有离开，供上电以后接着从原来的名单算
        boolean notify = true;
        Set<UUID> keep = new HashSet<>();
        if (pass.isEmpty() && unpowered) {
            state = FieldState.NO_POWER;
            SealNotifier.noPower(wearer, perSecond);
            notify = false;
        } else if (pass.isEmpty()) {
            state = FieldState.STANDBY;
            SealNotifier.powered(wearer, false, now);
        } else {
            if (!MRUDrain.canAfford(wearer, chest, cost, skipOwnCurios)) {
                state = FieldState.NO_POWER;
                for (ServerPlayer target : pass) {
                    statuses.put(target, TargetState.UNPOWERED);
                }
                SealNotifier.noPower(wearer, perSecond);
                notify = false;
            } else {
                MRUDrain.playerUseMRU(wearer, chest, cost, skipOwnCurios);
                state = FieldState.RUNNING;
                SealNotifier.powered(wearer, true, now);
                long until = now + interval + HOLD_SLACK;
                for (ServerPlayer target : pass) {
                    CurioSeal.hold(target, wearer, until);
                    keep.add(target.getUUID());
                    field.holding.put(target.getUUID(), target);
                }
                if (cfg.affectsSelf() && !wearer.isCreative()) {
                    CurioSeal.hold(wearer, wearer, until);
                    keep.add(wearer.getUUID());
                    field.holding.put(wearer.getUUID(), wearer);
                    selfSealed = true;
                }
            }
        }
        release(wearer, field, keep);

        // 名单：同时被别的穿戴者压制的标出一个名字（目标自己的场不算「别人」）；终端只列穿戴者看得见的人（见 visibleTo）
        List<S2CComputerTerminalPacket.Entry> entries = new ArrayList<>();
        Map<UUID, String> sealedOthers = new LinkedHashMap<>();
        for (Map.Entry<ServerPlayer, TargetState> entry : statuses.entrySet()) {
            ServerPlayer target = entry.getKey();
            TargetState status = entry.getValue();
            String other = "";
            if (status == TargetState.SEALED) {
                sealedOthers.put(target.getUUID(), target.getGameProfile().getName());
                for (Map.Entry<UUID, String> source : CurioSeal.sourcesOf(target).entrySet()) {
                    if (!source.getKey().equals(wearer.getUUID()) && !source.getKey().equals(target.getUUID())) {
                        other = source.getValue();
                        status = TargetState.SEALED_SHARED;
                        entry.setValue(status);
                        break;
                    }
                }
            }
            if (visibleTo(wearer, target)) {
                entries.add(new S2CComputerTerminalPacket.Entry(target.getGameProfile().getName(), (byte) status.ordinal(),
                        (int) Math.round(distances.getOrDefault(target, 0D)), other));
            }
        }
        entries.sort(Comparator.comparingInt(S2CComputerTerminalPacket.Entry::distance));
        field.entries = List.copyOf(entries);
        field.state = state;
        field.mruPerSecond = state == FieldState.STANDBY ? 0 : perSecond;
        field.sealedOthers = state == FieldState.RUNNING ? pass.size() : 0;
        if (notify) {
            SealNotifier.update(wearer, state == FieldState.RUNNING ? sealedOthers : Map.of(), state == FieldState.RUNNING, now);
        }
        pushState(wearer, field);
        return new Result(state, state == FieldState.RUNNING ? List.copyOf(pass) : List.of(), selfSealed,
                state == FieldState.STANDBY ? 0 : cost, Collections.unmodifiableMap(statuses));
    }

    /** 运转时会不会把穿戴者自己的饰品也封住（affects_self 开着、不是创造模式）：这时他饰品栏里的储存物品不付电 */
    private static boolean selfPays(Player wearer, CurioSealConfig.Values cfg) {
        return cfg.affectsSelf() && !wearer.isCreative();
    }

    /**
     * 终端名单里列不列 target：穿戴者看得见的人才列——不是旁观者、没隐身（同队且能看见隐身队友的照列）、
     * 而且穿戴者的客户端正显示着他（服务端在追踪：{@link #TRACKING}）。旁观者原版本来就不发给别人，混合端插件用 hidePlayer 藏起来的人
     * 走 removePairing、照发 Forge 的 StopTracking，所以不碰插件接口也顾得到。看不见的人照常过门、照常被压制、照常算钱，只是名单里没有、
     * 也不算进「范围内的玩家（N）」：终端不能拿来当透视。
     */
    private static boolean visibleTo(ServerPlayer wearer, ServerPlayer target) {
        if (target.isSpectator() || target.isInvisibleTo(wearer)) {
            return false;
        }
        Set<UUID> seen = TRACKING.get(wearer.getUUID());
        return seen != null && seen.contains(target.getUUID());
    }

    /** 开着却不能工作的原因：关着 / 不可用 / 没穿齐；能工作时 null */
    @Nullable
    private static FieldState blockedState(ServerPlayer wearer) {
        if (!isOn(wearer)) {
            return FieldState.OFF;
        }
        if (CurioSeal.availability() != CurioSeal.Availability.AVAILABLE) {
            return FieldState.UNAVAILABLE;
        }
        if (!ComputerArmorEvents.hasFullSet(wearer)) {
            return FieldState.INCOMPLETE;
        }
        if (CurioSealConfig.values().disabledIn(wearer.level().dimension())) {
            return FieldState.DISABLED_WORLD;
        }
        return null;
    }

    /** 不再压制的人撤掉自己这份来源（仍受最少保持时间） */
    private static void release(ServerPlayer wearer, Field field, Set<UUID> keep) {
        field.holding.entrySet().removeIf(entry -> {
            if (keep.contains(entry.getKey())) {
                return false;
            }
            CurioSeal.drop(entry.getValue(), wearer.getUUID());
            return true;
        });
    }

    /** 场停下（关掉、不可用、没穿齐、旁观、死了）：撤掉全部压制，名单清空，进出提示从头算 */
    private static void stop(ServerPlayer wearer, Field field, FieldState state) {
        release(wearer, field, Set.of());
        CurioSeal.dropEverywhere(wearer.getUUID());
        field.inside.clear();
        field.claimVerdict.clear();
        field.denyUntil.clear();
        field.entries = List.of();
        field.mruPerSecond = 0;
        field.sealedOthers = 0;
        if (field.state == FieldState.RUNNING || field.state == FieldState.STANDBY || field.state == FieldState.NO_POWER) {
            SealNotifier.reset(wearer.getUUID());
            if (state == FieldState.DISABLED_WORLD) {
                SealNotifier.disabledWorld(wearer);
            }
        }
        field.state = state;
        pushState(wearer, field);
    }

    // ------------------------------------------------------------------ 终端

    /** C2SComputerTerminalPacket 的服务端处理（ECComputer 登记）：封禁场的两种动作在这里，模块的两种（10c）转给 ModuleTerminal，别的丢弃 */
    public static void handleTerminalPacket(ServerPlayer player, C2SComputerTerminalPacket packet) {
        if (packet.action() == C2SComputerTerminalPacket.TOGGLE_SEAL) {
            toggle(player);
        } else if (packet.action() == C2SComputerTerminalPacket.REFRESH) {
            refresh(player);
        } else if (packet.action() == C2SComputerTerminalPacket.MODULE_TOGGLE || packet.action() == C2SComputerTerminalPacket.MODULE_REMOVE) {
            ModuleTerminal.handle(player, packet);
        }
    }

    /**
     * 开 / 关封禁场：不是旁观；打开还要穿齐四件、功能可用（关掉不看这两条：脱了盔甲以后也关得掉）；
     * 同一人 {@value #TOGGLE_MIN_INTERVAL} 刻内只认一次（只有切换成功才记时间）。
     * 打开时发手册进度；关掉时马上撤掉全部压制。下一次评估最晚 {@code evaluate_interval} 刻后到。
     *
     * @return 是否切换了
     */
    public static boolean toggle(ServerPlayer player) {
        if (player.isSpectator()) {
            return false;
        }
        long time = player.level().getGameTime();
        Long last = LAST_TOGGLE.get(player);
        if (last != null && time - last >= 0 && time - last < TOGGLE_MIN_INTERVAL) {
            return false;
        }
        boolean on = !isOn(player);
        if (on && !ComputerArmorEvents.hasFullSet(player)) {
            SealNotifier.incomplete(player);
            return false;
        }
        CurioSeal.Availability availability = CurioSeal.availability();
        if (on && availability != CurioSeal.Availability.AVAILABLE) {
            SealNotifier.unavailable(player, reasonText(availability));
            return false;
        }
        LAST_TOGGLE.put(player, time);
        setOn(player, on);
        CurioSealConfig.Values cfg = CurioSealConfig.values();
        // 在不起作用的维度里照样能打开（开关跟人走），只是这里不运转；离开这个维度就按开着算
        boolean disabledHere = on && cfg.disabledIn(player.level().dimension());
        if (disabledHere) {
            SealNotifier.disabledWorld(player);
        } else {
            SealNotifier.toggled(player, on, cfg);
        }
        Field field = FIELDS.computeIfAbsent(player.getUUID(), id -> new Field());
        if (on) {
            award(player);
            field.state = disabledHere ? FieldState.DISABLED_WORLD : FieldState.STANDBY;
            pushState(player, field);
        } else {
            stop(player, field, FieldState.OFF);
        }
        return true;
    }

    /**
     * 回一份带名单的终端数据：要戴着头盔；同一人 {@value #REFRESH_MIN_INTERVAL} 刻内只回一次。
     *
     * @return 是否回了
     */
    public static boolean refresh(ServerPlayer player) {
        if (!ComputerArmorEvents.wears(player, ArmorItem.Type.HELMET)) {
            return false;
        }
        long time = player.level().getGameTime();
        Long last = LAST_REFRESH.get(player);
        if (last != null && time - last >= 0 && time - last < REFRESH_MIN_INTERVAL) {
            return false;
        }
        LAST_REFRESH.put(player, time);
        send(player, packet(player, true));
        return true;
    }

    /** 终端数据：状态按现在的开关 / 可用 / 穿齐再核一遍，名单取上一次评估的；带名单的包另带「模块」页（ModuleTerminal.panel） */
    public static S2CComputerTerminalPacket packet(ServerPlayer player, boolean detail) {
        Field field = FIELDS.get(player.getUUID());
        FieldState blocked = blockedState(player);
        FieldState state = blocked != null ? blocked : field != null && field.state != FieldState.OFF ? field.state : FieldState.STANDBY;
        boolean working = blocked == null && field != null;
        byte availability = (byte) CurioSeal.availability().ordinal();
        return new S2CComputerTerminalPacket((byte) state.ordinal(), availability, working ? field.mruPerSecond : 0, availableMru(player),
                working ? field.sealedOthers : 0, detail, detail && working ? field.entries : List.of(),
                detail ? ModuleTerminal.panel(player) : S2CComputerTerminalPacket.ModulePanel.EMPTY);
    }

    /** 马上回一份带名单的终端数据（不受刷新间隔限制；模块页做成一次操作以后调，界面不用等下一次刷新） */
    public static void sendTerminal(ServerPlayer player) {
        send(player, packet(player, true));
    }

    /**
     * 身上能付的 MRU：储存物品（被压制的饰品栏里的不算；运转会封住自己的饰品时自己饰品栏里的也不算，同 {@link #evaluate} 付钱的口径）
     * + 胸甲自存；创造、带着无尽 MRU 晶体时不限
     */
    public static long availableMru(ServerPlayer player) {
        long storages = MRUDrain.availableFromStorages(player, selfPays(player, CurioSealConfig.values()));
        if (storages == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        IMRUHandlerItem own = player.getItemBySlot(EquipmentSlot.CHEST).getCapability(ECCapabilities.MRU_ITEM).orElse(null);
        return storages + (own != null ? Math.max(0, own.getMRU()) : 0);
    }

    /** 不可用的原因（动作栏、终端用） */
    public static Component reasonText(CurioSeal.Availability availability) {
        return Component.translatable("gui.essentialcraft5.computer_terminal.reason." + availability.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static void pushState(ServerPlayer player, Field field) {
        int sealed = field.state == FieldState.RUNNING ? field.sealedOthers : 0;
        if (field.pushedState == field.state.ordinal() && field.pushedSealed == sealed) {
            return;
        }
        field.pushedState = field.state.ordinal();
        field.pushedSealed = sealed;
        send(player, packet(player, false));
    }

    private static void send(ServerPlayer player, S2CComputerTerminalPacket packet) {
        List<S2CComputerTerminalPacket> capture = TERMINAL_CAPTURE.get(player.getUUID());
        if (capture != null) {
            capture.add(packet);
        }
        if (!(player instanceof FakePlayer)) {
            ECNetwork.sendTo(player, packet);
        }
    }

    /** 手册步骤「饰品封禁场」：进度不存在、已完成时什么都不做（照 HoannaEvents.awardGuideStep，条件名不写死） */
    private static void award(ServerPlayer player) {
        Advancement advancement = player.server.getAdvancements().getAdvancement(GUIDE_SEAL_FIELD);
        if (advancement == null) {
            return;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
        if (progress.isDone()) {
            return;
        }
        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(advancement, criterion);
        }
    }

    /** 人在空中：不在地上、没在滑翔、不在水里、没爬梯子、没骑乘 */
    public static boolean isAirborne(Player player) {
        return !player.onGround() && !player.isFallFlying() && !player.isInWater() && !player.onClimbable() && !player.isPassenger();
    }

    /** 被封时在滑翔的人看多少刻（饰品撤飞行属性、滑翔停下一般在下一刻，留点余量） */
    public static final int GLIDE_WATCH_TICKS = 5;
    /** 被封时在滑翔、胸甲位又不是鞘翅的人 → 看到哪一刻（游戏时间）为止 */
    private static final Map<UUID, Long> GLIDE_WATCH = new HashMap<>();

    /**
     * 正在滑翔、胸甲位那件自己飞不了：可能是靠饰品栏里的鞘翅滑翔（有的饰品鞘翅每刻按饰品栏挂飞行属性），
     * 压制一开始下一刻就撤了飞行属性、滑翔停住，可这一刻 {@code isFallFlying} 还是真的，{@link #isAirborne} 不算它。
     * 也可能是靠别处来的滑翔属性（别的盔甲、状态效果、技能），压制撤不掉，照样滑翔。所以这里只是「先记下」，
     * 滑翔真的停了、人还在空中才给缓降（{@link #followUpGlide}）；滑翔时有缓降，重力只剩八分之一，被封反而飞得更远
     */
    public static boolean glidingOnCurio(Player player) {
        return player.isFallFlying() && !player.getItemBySlot(EquipmentSlot.CHEST).canElytraFly(player);
    }

    /**
     * 压制开始时在滑翔、可能靠饰品滑翔的人（{@link #glidingOnCurio}）：玩家 tick 末尾看滑翔停没停。停了、还在空中就给缓降；
     * 过了 {@value #GLIDE_WATCH_TICKS} 刻还在滑翔（不是靠饰品）、压制已经解除、或者落地了就不再看
     */
    public static void followUpGlide(ServerPlayer player, long now) {
        Long until = GLIDE_WATCH.get(player.getUUID());
        if (until == null) {
            return;
        }
        if (now > until || player.isSpectator() || !CurioSeal.isSealed(player)) {
            GLIDE_WATCH.remove(player.getUUID());
            return;
        }
        if (player.isFallFlying()) {
            return;
        }
        GLIDE_WATCH.remove(player.getUUID());
        int ticks = CurioSealConfig.values().slowFallTicks();
        if (ticks > 0 && isAirborne(player)) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, ticks, 0, false, true, true));
        }
    }

    // ------------------------------------------------------------------ 测试

    /** GameTest 用：从现在起记下发给这名玩家的终端数据包（照样发出去） */
    public static List<S2CComputerTerminalPacket> captureTerminalForTest(UUID player) {
        List<S2CComputerTerminalPacket> list = Collections.synchronizedList(new ArrayList<>());
        TERMINAL_CAPTURE.put(player, list);
        return list;
    }

    /**
     * GameTest 用：当成 observer 的客户端正显示着这些玩家（测试玩家不进世界、没有追踪关系，名单断言要先登记）。
     * 「真的追踪才进名单」由走事件总线的 {@code sealFieldListsOnlyVisible} 守着
     */
    public static void trackForTest(ServerPlayer observer, ServerPlayer... targets) {
        Set<UUID> seen = TRACKING.computeIfAbsent(observer.getUUID(), id -> new HashSet<>());
        for (ServerPlayer target : targets) {
            seen.add(target.getUUID());
        }
    }

    /** GameTest 用：收尾（不再记终端包，场的运行状态丢掉，撤掉他登记的压制，追踪表里作为观察者、作为目标的都删） */
    public static void forgetForTest(ServerPlayer player) {
        TERMINAL_CAPTURE.remove(player.getUUID());
        FIELDS.remove(player.getUUID());
        LAST_TOGGLE.remove(player);
        LAST_REFRESH.remove(player);
        SealNotifier.reset(player.getUUID());
        CurioSeal.dropEverywhere(player.getUUID());
        forgetTracking(player.getUUID());
        GLIDE_WATCH.remove(player.getUUID());
    }

    /** GameTest 用：这一刻玩家刻里用的评估间隔（服务端刻开头取的配置，测试里改的覆盖值要到下一刻才生效） */
    public static int evaluateIntervalThisTick() {
        return tickValues.evaluateInterval();
    }

    private static void forgetTracking(UUID player) {
        TRACKING.remove(player);
        TRACKING.values().removeIf(seen -> seen.remove(player) && seen.isEmpty());
    }

    // ------------------------------------------------------------------ 事件

    @SubscribeEvent
    public static void onSealFieldServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            tickValues = CurioSealConfig.values();
        }
    }

    /** 每名开着场的穿戴者每 evaluate_interval 刻评估一次（按实体 id 错开） */
    @SubscribeEvent
    public static void onSealFieldPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer wearer) || wearer instanceof FakePlayer) {
            return;
        }
        if (Math.floorMod(wearer.tickCount + wearer.getId(), tickValues.evaluateInterval()) != 0) {
            return;
        }
        if (!isOn(wearer)) {
            Field field = FIELDS.get(wearer.getUUID());
            if (field != null && field.state != FieldState.OFF) {
                stop(wearer, field, FieldState.OFF);
            }
            return;
        }
        evaluate(wearer, wearer.serverLevel().players(), CurioSeal.now(wearer.server));
    }

    /**
     * 压制开始那一刻人在空中：给一次缓降（同一次压制只发一次事件，所以只给一次）。
     * 在滑翔、胸甲位又不是鞘翅的先记下，等滑翔真停了再给（{@link #followUpGlide}）
     */
    @SubscribeEvent
    public static void onSealFieldSealed(CurioSealEvent event) {
        if (!event.isSealed() || !(event.getEntity() instanceof ServerPlayer target) || target.level().isClientSide) {
            return;
        }
        int ticks = CurioSealConfig.values().slowFallTicks();
        if (ticks <= 0 || target.isSpectator()) {
            return;
        }
        if (isAirborne(target)) {
            target.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, ticks, 0, false, true, true));
        } else if (glidingOnCurio(target)) {
            GLIDE_WATCH.put(target.getUUID(), target.serverLevel().getGameTime() + GLIDE_WATCH_TICKS);
        }
    }

    /** 被封时在滑翔的人，滑翔停没停（见 {@link #followUpGlide}） */
    @SubscribeEvent
    public static void onSealFieldGlideWatch(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !GLIDE_WATCH.isEmpty() && event.player instanceof ServerPlayer player) {
            followUpGlide(player, player.serverLevel().getGameTime());
        }
    }

    /** 登录时把开关状态推给本人（紫色小圆要画） */
    @SubscribeEvent
    public static void onSealFieldLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isOn(player)) {
            Field field = FIELDS.computeIfAbsent(player.getUUID(), id -> new Field());
            field.pushedState = -1;
            pushState(player, field);
        }
    }

    @SubscribeEvent
    public static void onSealFieldLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 他登记的压制由压制核心在下线时撤掉
            FIELDS.remove(player.getUUID());
            SealNotifier.reset(player.getUUID());
            forgetTracking(player.getUUID());
            GLIDE_WATCH.remove(player.getUUID());
        }
    }

    /**
     * 追踪表（终端名单只列穿戴者看得见的人，见 {@link #visibleTo}）：所有真玩家都记，场随时可能打开。
     * 重生、换维度时原版先 removePairing 再 addPairing，两个事件都会到，按 UUID 记自然接上
     */
    @SubscribeEvent
    public static void onSealFieldStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer observer && !(observer instanceof FakePlayer)
                && event.getTarget() instanceof ServerPlayer target && target != observer) {
            TRACKING.computeIfAbsent(observer.getUUID(), id -> new HashSet<>()).add(target.getUUID());
        }
    }

    @SubscribeEvent
    public static void onSealFieldStopTracking(PlayerEvent.StopTracking event) {
        if (event.getEntity() instanceof ServerPlayer observer && event.getTarget() instanceof ServerPlayer target) {
            Set<UUID> seen = TRACKING.get(observer.getUUID());
            if (seen != null && seen.remove(target.getUUID()) && seen.isEmpty()) {
                TRACKING.remove(observer.getUUID());
            }
        }
    }

    /** 穿戴者死了：撤掉他登记的全部压制（开关保留） */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSealFieldDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            dropAll(player);
        }
    }

    /** 穿戴者换了维度：场伸不过维度，撤掉全部压制，场内名单和探测缓存重来 */
    @SubscribeEvent
    public static void onSealFieldChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            dropAll(player);
        }
    }

    private static void dropAll(ServerPlayer player) {
        CurioSeal.dropEverywhere(player.getUUID());
        Field field = FIELDS.get(player.getUUID());
        if (field != null) {
            field.holding.clear();
            field.inside.clear();
            field.claimVerdict.clear();
            field.probeCache.clear();
            field.denyUntil.clear();
            field.entries = List.of();
            field.sealedOthers = 0;
        }
        SealNotifier.reset(player.getUUID());
    }

    @SubscribeEvent
    public static void onSealFieldServerStopped(ServerStoppedEvent event) {
        FIELDS.clear();
        LAST_TOGGLE.clear();
        LAST_REFRESH.clear();
        TRACKING.clear();
        GLIDE_WATCH.clear();
        SealNotifier.clear();
        tickValues = CurioSealConfig.DEFAULTS;
    }
}
