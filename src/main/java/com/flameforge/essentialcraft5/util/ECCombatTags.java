package com.flameforge.essentialcraft5.util;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.gun.RayHitGate;
import com.flameforge.essentialcraft5.item.bauble.BaubleEffects;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 玩家之间的交手记录：「A 对 B 有一下伤害被放行、真的打中了」记下当时的服务端刻数。
 * 饰品封禁场的门（只压制最近交过手的人，item/ComputerSealField）用 {@link #landedEither}，相位锚模块（item/module/combat/PhaseAnchorEvents）用 {@link #inMutualCombat}。
 * 语义是「这一下过了 PvP / 队伍 / 领地的门」，之后别的模组再取消原伤害、盔甲整下挡掉都不改变这一点。
 * <p>
 * <b>为什么看 {@code LivingDamageEvent}</b>：插件、模组拦攻击的地方都在它前面——纯 Forge 的 PvP、队伍在攻击事件之前（{@code ServerPlayer.canHarmPlayer}），
 * 领地 / PvP 模组在 {@code LivingAttackEvent} 里取消；Mohist 系混合端的插件取消 Bukkit 的 {@code EntityDamageByEntityEvent}，它夹在
 * {@code LivingHurtEvent} 和 {@code LivingDamageEvent} 之间，被取消就不发后者。所以走到这里、最终没被取消的，就是服务器放行过的一下。
 * 不靠「假装打一下」去问：混合端上连 0 伤害都会发真的 Bukkit 伤害事件，战斗标记插件会当真。
 * <p>
 * <b>记什么</b>：{@link EventPriority#LOWEST}、不收已取消的；受害者和 {@code source.getEntity()} 都是真玩家（不是 FakePlayer）、不是同一人。
 * 伤害数值不限：混合端上被 MRU 盔甲整下挡掉的那一下是 {@code LivingDamageEvent(0)}，插件放行过，算。弹射物按射手；宠物、怪、没有攻击者的伤害不算。
 * <br>不算「动过手」的两种（{@link #countsAsAttack}，两种环境一样）：
 * <ul>
 *   <li>荆棘、宅男全套反伤、σ 反弹这类挨打以后自动打回去的伤害（{@code ECDamageKinds.isReflected}），以及别的饰品自己打出的伤害（ν 的连锁只打怪，
 *       ι 跟着主击走、主击另有记录）。反伤一定跟在一下已经记下的原攻击后面，{@link #landedEither} 不受影响；{@link #inMutualCombat} 不会被被动反伤凑满。</li>
 *   <li>{@code hurt} 收到的原值就是 0 的（雪球、鸡蛋、末影珍珠砸中玩家）：纯 Forge 的 {@code Player.hurt} 对 0 直接返回，连 {@code LivingHurtEvent} 都不发；
 *       混合端不滤 0、插件放行后照发 {@code LivingDamageEvent(0)}。所以在 {@code LivingAttackEvent}（{@link #onCombatTagAttack}，最高优先级；
 *       它的数值别的监听器改不了，两种环境都是 {@code hurt} 收到的原值）记下原值不大于 0 的伤害源对象，本刻内到了 {@code LivingDamageEvent} 也不记。
 *       盾牌整下格挡、MRU 盔甲挡满、{@code LivingHurtEvent} 被取消的几下原值都大于 0，照记。</li>
 * </ul>
 * <p>
 * <b>到了 {@code LivingHurtEvent} 以后被挡掉、被取消的一下，两种环境都算</b>（第十批 10c 统一，10b 审查 F-2 定的方向）：
 * 混合端在 {@code LivingHurtEvent} 被取消时按 0 照样往下发 Bukkit 伤害事件，插件放行就发 {@code LivingDamageEvent(0)}（盾牌整下格挡、MRU 盔甲挡满、
 * 别的模组在 {@code LivingHurtEvent} 里取消原伤害都是），上面那条就记下了。纯 Forge 上这几种都走不到 {@code LivingDamageEvent}——
 * 盾牌整下格挡后 {@code actuallyHurt(源, 0)} 在 {@code onLivingHurt} 之后返回，{@code LivingHurtEvent} 被取消时同样返回。所以纯 Forge 在
 * {@link #onCombatTagHurt}（{@code LivingHurtEvent} LOWEST、<b>也收已取消的</b>）补记：走到这里时已经被取消、伤害 ≤ 0 的当场记；还没有的记下事件对象，
 * 本刻末再看一次（{@link #flushPending}），排在本监听器后面的监听器取消的、改成 0 的同样补记，伤害照常落下的由 {@code LivingDamageEvent} 那条记。
 * 能走到 {@code LivingHurtEvent}，说明 PvP、队伍（{@code canHarmPlayer}，在攻击事件之前）和 Forge 端的领地 / PvP 模组（在 {@code LivingAttackEvent}
 * 取消，常见的都是）已经放行；在 {@code LivingHurtEvent} 里才取消的模组在混合端上也拦不住这条记录，两边一样。
 * item/armor/MRUShield 挡满时在 LOW 取消事件，也由这里的补记收到。只追打一名举盾 / 穿满电 MRU 盔甲、一下都不还手的人，单人、纯 Forge、混合端都算交过手。
 * <p>
 * <b>刻数</b>：{@code server.getTickCount()}（不受 /time 影响）。过期判断 {@code 0 ≤ now − t ≤ window}：重启以后刻数从 0 算，比现在还晚的当过期。
 * <b>上限</b>：每名攻击者最多记 {@value #MAX_PER_ATTACKER} 个受害者（满了丢最旧的）；总条数超过 {@value #PRUNE_THRESHOLD} 或每 {@value #PRUNE_INTERVAL} 刻
 * 清一次超过 {@value #MAX_AGE} 刻的；下线时作为攻击者、作为受害者的都删。只在服务端线程读写。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class ECCombatTags {

    /** 每名攻击者最多记几个受害者 */
    public static final int MAX_PER_ATTACKER = 32;
    /** 记录超过这么多条时清一次过期的 */
    public static final int PRUNE_THRESHOLD = 256;
    /** 至少每隔这么多刻清一次过期的 */
    public static final int PRUNE_INTERVAL = 600;
    /** 超过这么多刻的记录一律当过期（配置的窗口最大 1200） */
    public static final int MAX_AGE = 1200;

    /** 纯 Forge 一刻最多记这么多条等本刻末再看的 LivingHurtEvent */
    public static final int MAX_PENDING_HURT = 256;
    /** 一刻最多记这么多个原值是 0 的伤害源对象 */
    public static final int MAX_ZERO_INPUT = 256;

    /** 纯 Forge：到了 LivingHurtEvent、当时还没被取消的一下，本刻末再看它最后有没有被取消、改成 0 */
    private record PendingHurt(LivingHurtEvent event, UUID attacker, UUID victim, int tick) {
    }

    /** 攻击者 → (受害者 → 最近一次被放行的伤害的服务端刻) */
    private static final Map<UUID, Object2IntOpenHashMap<UUID>> LANDED = new HashMap<>();
    private static final List<PendingHurt> PENDING_HURT = new ArrayList<>();
    /** 这一刻 {@code hurt} 收到的原值就不大于 0 的玩家打玩家的伤害源（按对象比），本刻末清空 */
    private static final Set<DamageSource> ZERO_INPUT = Collections.newSetFromMap(new IdentityHashMap<>());
    private static int size;
    private static int lastPrune;

    private ECCombatTags() {
    }

    /** 记一次：attacker 对 victim 有一下伤害被放行（假玩家、同一人不记） */
    public static void record(ServerPlayer attacker, ServerPlayer victim, int tick) {
        if (attacker instanceof FakePlayer || victim instanceof FakePlayer || attacker == victim) {
            return;
        }
        record(attacker.getUUID(), victim.getUUID(), tick);
    }

    /** 同上，按 UUID 记（调用方自己保证两边都是真玩家；GameTest 测上限时不用建几十个玩家） */
    public static void record(UUID attacker, UUID victim, int tick) {
        if (attacker.equals(victim)) {
            return;
        }
        Object2IntOpenHashMap<UUID> victims = LANDED.computeIfAbsent(attacker, id -> {
            Object2IntOpenHashMap<UUID> map = new Object2IntOpenHashMap<>();
            map.defaultReturnValue(-1);
            return map;
        });
        if (!victims.containsKey(victim)) {
            if (victims.size() >= MAX_PER_ATTACKER) {
                dropOldest(victims);
            }
            size++;
        }
        victims.put(victim, tick);
        if (size > PRUNE_THRESHOLD || tick - lastPrune >= PRUNE_INTERVAL || tick < lastPrune) {
            prune(tick);
        }
    }

    /** attacker 最近一次打中 victim 的刻数；没有返回 -1 */
    public static int lastLanded(UUID attacker, UUID victim) {
        Object2IntOpenHashMap<UUID> victims = LANDED.get(attacker);
        return victims == null ? -1 : victims.getInt(victim);
    }

    /** 两人之间（任一方向）最近 window 刻内有过一下被放行的伤害（封禁场的门） */
    public static boolean landedEither(Player a, Player b, int now, int window) {
        return fresh(lastLanded(a.getUUID(), b.getUUID()), now, window) || fresh(lastLanded(b.getUUID(), a.getUUID()), now, window);
    }

    /** 双方都在 window 刻内打中过对方 */
    public static boolean inMutualCombat(Player a, Player b, int now, int window) {
        return fresh(lastLanded(a.getUUID(), b.getUUID()), now, window) && fresh(lastLanded(b.getUUID(), a.getUUID()), now, window);
    }

    /** 记录在 t、现在 now、窗口 window 刻：0 ≤ now − t ≤ window 才算（没有记录、重启后比现在还晚的都不算） */
    static boolean fresh(int t, int now, int window) {
        if (t < 0) {
            return false;
        }
        int age = now - t;
        return age >= 0 && age <= window;
    }

    /** 删掉这名玩家作为攻击者、作为受害者的全部记录 */
    public static void forget(UUID player) {
        Object2IntOpenHashMap<UUID> own = LANDED.remove(player);
        if (own != null) {
            size -= own.size();
        }
        for (Iterator<Object2IntOpenHashMap<UUID>> it = LANDED.values().iterator(); it.hasNext(); ) {
            Object2IntOpenHashMap<UUID> victims = it.next();
            if (victims.removeInt(player) >= 0) {
                size--;
            }
            if (victims.isEmpty()) {
                it.remove();
            }
        }
    }

    /**
     * 这一下算不算伤害记在谁头上的那名玩家「动过手」（见类注释）：反伤（{@code ECDamageKinds.isReflected}）和饰品自己打出的伤害不算。
     * 要在伤害事件发生的当时问：饰品伤害、σ 反弹的作用域只在那一下的 {@code hurt} 里有效。
     */
    public static boolean countsAsAttack(DamageSource source) {
        return !ECDamageKinds.isReflected(source) && !BaubleEffects.isBaubleDamage();
    }

    /** GameTest 用：清空 */
    public static void clearForTest() {
        LANDED.clear();
        PENDING_HURT.clear();
        ZERO_INPUT.clear();
        size = 0;
        lastPrune = 0;
    }

    private static void dropOldest(Object2IntOpenHashMap<UUID> victims) {
        UUID oldest = null;
        int oldestTick = Integer.MAX_VALUE;
        for (Object2IntMap.Entry<UUID> entry : victims.object2IntEntrySet()) {
            if (entry.getIntValue() < oldestTick) {
                oldestTick = entry.getIntValue();
                oldest = entry.getKey();
            }
        }
        if (oldest != null) {
            victims.removeInt(oldest);
            size--;
        }
    }

    private static void prune(int now) {
        lastPrune = now;
        for (Iterator<Object2IntOpenHashMap<UUID>> it = LANDED.values().iterator(); it.hasNext(); ) {
            Object2IntOpenHashMap<UUID> victims = it.next();
            int before = victims.size();
            victims.object2IntEntrySet().removeIf(entry -> !fresh(entry.getIntValue(), now, MAX_AGE));
            size -= before - victims.size();
            if (victims.isEmpty()) {
                it.remove();
            }
        }
    }

    // ------------------------------------------------------------------ 事件

    /**
     * 记下 {@code hurt} 收到的原值就不大于 0 的玩家打玩家的伤害源（见类注释：最高优先级；攻击事件的数值别的监听器改不了），
     * 本刻内同一个伤害源对象到了 {@link #onCombatTagDamage} 也不记。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCombatTagAttack(LivingAttackEvent event) {
        if (!(event.getAmount() > 0F) && event.getEntity() instanceof ServerPlayer && event.getSource().getEntity() instanceof ServerPlayer
                && ZERO_INPUT.size() < MAX_ZERO_INPUT) {
            ZERO_INPUT.add(event.getSource());
        }
    }

    /** 伤害被放行、真的打中了（见类注释：LOWEST、不收已取消的、数值不限；反伤、原值是 0 的不算） */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCombatTagDamage(LivingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer victim && !(victim instanceof FakePlayer)
                && event.getSource().getEntity() instanceof ServerPlayer attacker && !(attacker instanceof FakePlayer)
                && attacker != victim && countsAsAttack(event.getSource()) && !ZERO_INPUT.contains(event.getSource())) {
            record(attacker, victim, victim.server.getTickCount());
        }
    }

    /**
     * 纯 Forge：放行了、到了 {@code LivingHurtEvent}，但被挡掉、被取消、改成 0 的一下到不了 {@code LivingDamageEvent}，在这里记（见类注释）：
     * 已经是这样的当场记，还没有的记下事件对象、本刻末再看（{@link #flushPending}）。
     * 混合端不走这里：那边 {@code LivingHurtEvent} 之后还要过插件，放行了才发 {@code LivingDamageEvent(0)}，由 {@link #onCombatTagDamage} 记。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onCombatTagHurt(LivingHurtEvent event) {
        if (RayHitGate.isBukkitHybrid()
                || !(event.getEntity() instanceof ServerPlayer victim) || victim instanceof FakePlayer
                || !(event.getSource().getEntity() instanceof ServerPlayer attacker) || attacker instanceof FakePlayer
                || attacker == victim || !countsAsAttack(event.getSource())) {
            // 反伤要在这里当场判：σ 的反弹在本刻末打出，等到 flushPending 时作用域早就没了
            return;
        }
        int tick = victim.server.getTickCount();
        if (RayHitGate.hurtZeroed(event)) {
            record(attacker, victim, tick);
        } else if (PENDING_HURT.size() < MAX_PENDING_HURT) {
            PENDING_HURT.add(new PendingHurt(event, attacker.getUUID(), victim.getUUID(), tick));
        }
    }

    @SubscribeEvent
    public static void onCombatTagServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ZERO_INPUT.clear();
        if (!PENDING_HURT.isEmpty()) {
            flushPending();
        }
    }

    /**
     * 本刻末（自动调；GameTest 在 hurt 之后直接调）：{@link #onCombatTagHurt} 记下的几下里，最后被取消或伤害被改成 ≤ 0 的补记交手（按当时的刻数）；
     * 伤害照常落下的已经由 {@link #onCombatTagDamage} 记过。
     *
     * @return 补记了几条
     */
    public static int flushPending() {
        if (PENDING_HURT.isEmpty()) {
            return 0;
        }
        List<PendingHurt> batch = new ArrayList<>(PENDING_HURT);
        PENDING_HURT.clear();
        int recorded = 0;
        for (PendingHurt pending : batch) {
            if (RayHitGate.hurtZeroed(pending.event())) {
                record(pending.attacker(), pending.victim(), pending.tick());
                recorded++;
            }
        }
        return recorded;
    }

    @SubscribeEvent
    public static void onCombatTagLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            forget(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onCombatTagServerStopped(ServerStoppedEvent event) {
        clearForTest();
    }
}
