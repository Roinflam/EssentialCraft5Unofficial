package com.flameforge.essentialcraft5.entity.boss;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.util.ECLandedHit;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 首领的伤害预算（第十三批 13c-Fb，batch13.md §5.3「共同」、policy §4.18）：单次封顶 + 每秒伤害桶，每只首领一份。
 * 小 Boss 只用单次封顶，Boss 两样都用（{@link BossTier#usesBucket}）；数值由首领自己给（{@link ECBoss#bossLimits}，读各自的配置，0 = 不限）。
 *
 * <h2>在哪里夹</h2>
 * {@code LivingDamageEvent} LOWEST（{@link BossBudgetEvents#bossBudgetDamage}）：扣血前最后一个事件，别的模组在 {@code LivingHurtEvent} /
 * {@code LivingDamageEvent} 里乘过的加成（暴击、元素、难度）都已经算进去，混合端的 Bukkit 伤害事件夹在 Hurt 和 Damage 之间，两端都管用。
 * 同在 LOWEST、比本模组登记得晚的监听器还能再改，所以再加一道兜底：同一个首领、同一个伤害源实例在这一下里死了，而按夹过的数它本来死不了，
 * 就在 {@code LivingDeathEvent}（HIGHEST）里取消死亡、把生命设回「打之前 − 夹过的数」（照 {@code corruption/CappedHurt} 的写法）。
 * 没有攻击者、带 {@code bypasses_invulnerability} 的（{@code /kill}、掉出世界，{@link BossRules#unblockable}）不夹、不扣桶；
 * 有攻击者的同类伤害（「真伤」武器）照普通伤害夹。
 *
 * <h2>同一下只算一次（{@link #resolve}）</h2>
 * 每秒桶会被扣，同一个事件不能问两次就扣两次：第一次问时算出放行多少、扣桶，按事件对象记住，同一个事件后面再问都是这个数（本刻末清空）。
 * 别的监听器（矩阵图谱的首领参战记账）要在同一个事件里知道「夹过以后是多少」，调 {@link #resolve} 拿同一个数，谁先跑都一样。
 *
 * <h2>每秒桶</h2>
 * 容量 = 每秒的量（一秒的份），按游戏时间连续回满（每刻回 1/20）；一下最多拿走桶里剩的。多名玩家同一秒打进来的共用一个桶。
 * 读档以后从满桶开始（不存盘：多给的至多一秒的份）。游戏时间倒退（{@code /time} 改的是日内时间，不影响 {@code getGameTime}）按没过时间算。
 * <p>
 * 夹完以后，打实的一下（{@code ECLandedHit.landed}）把有效伤害（扣到生命上的、不超过剩下的生命，加上打掉的伤害吸收）记进名单的伤害账
 * （{@link BossRoster#noteDamage}），打的人进名单。只在服务端主线程用。
 */
public final class BossDamageBudget {

    /** 每张表一刻最多记这么多条 */
    public static final int MAX_ENTRIES = 4096;
    /** 一秒多少刻 */
    private static final float TICKS_PER_SECOND = 20F;

    /**
     * 限额。
     *
     * @param perHit    单次封顶（0 = 不封顶）
     * @param perSecond 每秒桶（0 = 不限）
     */
    public record Limits(float perHit, float perSecond) {

        public static final Limits NONE = new Limits(0F, 0F);

        public Limits {
            perHit = sanitize(perHit);
            perSecond = sanitize(perSecond);
        }

        /** 从配置的 double 建（NaN、负数按 0） */
        public static Limits of(double perHit, double perSecond) {
            return new Limits((float) perHit, (float) perSecond);
        }

        /** 不带每秒桶的那份（小 Boss） */
        public Limits withoutBucket() {
            return perSecond > 0F ? new Limits(perHit, 0F) : this;
        }

        /** 两项都乘 scale（不限的 0 还是 0；scale 不大于 1 时原样返回，见 {@link BossHealthGuard#limitScale}） */
        public Limits scaled(float scale) {
            return scale > 1F && Float.isFinite(scale) ? new Limits(perHit * scale, perSecond * scale) : this;
        }

        private static float sanitize(float value) {
            return Float.isFinite(value) && value > 0F ? value : 0F;
        }
    }

    /** 桶里剩多少；NaN = 还没用过（满的） */
    private float tokens = Float.NaN;
    private long lastTick;

    /**
     * 放行多少（会扣桶）：先夹单次封顶，再夹桶里剩的。amount 不是正数（含 NaN）时返回 0、不扣。
     *
     * @param gameTime 首领所在世界的 {@code getGameTime()}
     */
    public float take(float amount, Limits limits, long gameTime) {
        if (!(amount > 0F)) {
            return 0F;
        }
        float allowed = amount;
        if (limits.perHit() > 0F) {
            allowed = Math.min(allowed, limits.perHit());
        }
        if (limits.perSecond() > 0F) {
            refill(limits.perSecond(), gameTime);
            allowed = Math.max(0F, Math.min(allowed, tokens));
            tokens -= allowed;
        }
        return allowed;
    }

    /** 桶里现在剩多少（只看、不扣；不限时返回正无穷） */
    public float tokens(Limits limits, long gameTime) {
        if (limits.perSecond() <= 0F) {
            return Float.POSITIVE_INFINITY;
        }
        refill(limits.perSecond(), gameTime);
        return tokens;
    }

    /** 回到满桶（读档、GameTest） */
    public void reset() {
        tokens = Float.NaN;
    }

    private void refill(float capacity, long gameTime) {
        if (Float.isNaN(tokens)) {
            tokens = capacity;
            lastTick = gameTime;
            return;
        }
        long elapsed = gameTime - lastTick;
        if (elapsed > 0L) {
            tokens = (float) Math.min(capacity, tokens + elapsed * (double) capacity / TICKS_PER_SECOND);
        }
        lastTick = gameTime;
        // 配置改小了：桶也跟着缩
        tokens = Math.min(tokens, capacity);
    }

    // ------------------------------------------------------------------
    // 同一个事件只算一次
    // ------------------------------------------------------------------

    /** 已经问过的伤害事件 → 放行的数 */
    private static final Map<LivingDamageEvent, Float> RESOLVED = new IdentityHashMap<>();
    /** 本刻夹过的一下：首领 → 那一下（死亡兜底用） */
    private static final Map<LivingEntity, Guard> GUARDS = new IdentityHashMap<>();

    private record Guard(DamageSource source, float healthBefore, float allowed) {
    }

    /**
     * 这一下夹过以后是多少（见类注释「同一下只算一次」）：不是首领、在客户端、{@link BossRules#unblockable} 的原样返回；
     * 小 Boss 不用每秒桶。第一次问时扣桶并记住，同一个事件再问返回同一个数。在 {@code LivingDamageEvent} 的监听器里调。
     */
    public static float resolve(LivingDamageEvent event) {
        Float known = RESOLVED.get(event);
        if (known != null) {
            return known;
        }
        LivingEntity entity = event.getEntity();
        float amount = event.getAmount();
        float allowed = amount;
        if (entity instanceof ECBoss boss && !entity.level().isClientSide && !BossRules.unblockable(entity, event.getSource())) {
            Limits limits = boss.effectiveLimits();
            if (!boss.bossTier().usesBucket()) {
                limits = limits.withoutBucket();
            }
            allowed = Math.min(amount, boss.bossBudget().take(amount, limits, entity.level().getGameTime()));
        }
        if (RESOLVED.size() < MAX_ENTRIES) {
            RESOLVED.put(event, allowed);
        }
        return allowed;
    }

    /** Forge 总线（类名带模块前缀，见 tools/check_event_handlers.py） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
    public static final class BossBudgetEvents {

        private BossBudgetEvents() {
        }

        /**
         * 单次封顶 + 每秒桶，打实了记账（LOWEST、不收已取消的）。最后把这一下夹完的数记给生命结算（{@link BossHealthGuard#allow}），
         * 随后扣血的那次 setHealth 由它抵掉；首领以外受结算保护的实体（灾柱）在这里只夹单次封顶
         */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void bossBudgetDamage(LivingDamageEvent event) {
            LivingEntity entity = event.getEntity();
            if (entity.level().isClientSide || !(entity instanceof BossHealthGuard.Guarded guarded)) {
                return;
            }
            if (!(entity instanceof ECBoss boss) || BossRules.unblockable(entity, event.getSource())) {
                float cap = guarded.guardLimits().perHit();
                if (!(entity instanceof ECBoss) && !BossRules.unblockable(entity, event.getSource()) && cap > 0F && event.getAmount() > cap) {
                    event.setAmount(cap);
                }
                guarded.healthGuard().allow(event.getAmount(), entity.level().getGameTime());
                return;
            }
            float allowed = resolve(event);
            if (allowed < event.getAmount()) {
                event.setAmount(allowed);
            }
            float health = entity.getHealth();
            if (GUARDS.size() < MAX_ENTRIES || GUARDS.containsKey(entity)) {
                GUARDS.put(entity, new Guard(event.getSource(), health, event.getAmount()));
            }
            if (ECLandedHit.landed(event)) {
                float effective = Math.min(event.getAmount(), Math.max(0F, health)) + ECLandedHit.absorbed(event);
                boss.bossRoster().noteDamage(event.getSource(), effective);
            }
            guarded.healthGuard().allow(event.getAmount(), entity.level().getGameTime());
        }

        /**
         * 兜底：同一个首领、同一个伤害源实例在夹过的这一下里死了，按夹过的数本来死不了（同在 LOWEST、登记得比本模组晚的监听器又加了伤害）
         * → 取消死亡、生命设回「打之前 − 夹过的数」
         */
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void bossBudgetDeathGuard(LivingDeathEvent event) {
            if (GUARDS.isEmpty()) {
                return;
            }
            LivingEntity entity = event.getEntity();
            Guard guard = GUARDS.get(entity);
            if (guard == null || guard.source() != event.getSource() || !(entity instanceof ECBoss)) {
                return;
            }
            float expected = guard.healthBefore() - guard.allowed();
            if (expected > 1.0E-3F && Float.isFinite(expected)) {
                event.setCanceled(true);
                entity.setHealth(expected);
            }
        }

        @SubscribeEvent
        public static void bossBudgetServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                clear();
            }
        }

        @SubscribeEvent
        public static void bossBudgetServerStopped(ServerStoppedEvent event) {
            clear();
        }

        private static void clear() {
            if (!RESOLVED.isEmpty()) {
                RESOLVED.clear();
            }
            if (!GUARDS.isEmpty()) {
                GUARDS.clear();
            }
        }
    }
}
