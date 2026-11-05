package com.flameforge.essentialcraft5.item.module.combat;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.gun.RayHitGate;
import com.flameforge.essentialcraft5.item.bauble.BaubleEffects;
import com.flameforge.essentialcraft5.item.ember.InlayEvents;
import com.flameforge.essentialcraft5.item.module.ModuleCache;
import com.flameforge.essentialcraft5.registry.ECCombatModules;
import com.flameforge.essentialcraft5.registry.ECGuns;
import com.flameforge.essentialcraft5.util.ECDamageKinds;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 累加器模块（第十批 10c-M3，batch10.md §5.12.1；policy §4.13）：打进对方受击无敌、被<b>整下吞掉</b>的伤害不白打，记成一笔账，
 * 下一下真打实的时候并进那一下，最多让那一下翻一倍（{@code max_ratio}）；{@code forget_ticks} 刻没再记账就作废。
 * 加特林每刻一发，对同一个目标每 10 刻只有一发算数（原版受击无敌），开着累加器以后其余几发攒下来、补进下一发。
 *
 * <h2>认哪几种攻击</h2>
 * 攻击者（{@code getEntity()}）是开着这块模块的真玩家，而且是：本模组的枪的射线（伤害类型 {@code essentialcraft5:mru_ray}），
 * 或者近战——直接来源是玩家本人、不是荆棘 / 弹射物 / 爆炸 / 魔法（同余烬嵌刻认武器的口径，{@code InlayEvents.weaponHit}）。
 * 饰品打出的（ν 的连锁、σ 的反弹）、尖刺圆盾的范围伤害不算。别的模组的弹射物不记也不结算。
 *
 * <h2>记账（{@code LivingAttackEvent} 最低优先级、不收已取消的）</h2>
 * 照原版 {@code LivingEntity.hurt} 的判断：目标 {@code invulnerableTime > 10}、伤害源不带 {@code bypasses_cooldown}、这一下不比目标的
 * {@code lastHurt} 重 → 这一下整下吞掉，记账；举盾挡得住的不记。比 {@code lastHurt} 重的只打出差额，那一下不记也不结算。
 * {@code lastHurt} 是 protected 字段，按 SRG 名 {@value #LAST_HURT_SRG} 反射读一次缓存。
 * 先把事件对象记下来，<b>本刻末</b>看它最终没被取消才入账（排在后面、同为最低优先级的监听器取消的也看得到）。
 * 被吞掉的那一下不会走到 {@code LivingHurtEvent}，混合端的插件也看不到它；入账只动攻击者自己的账，不碰目标。
 * 反过来，同一下到了 {@code LivingHurtEvent} 就说明它没被吞掉（插件或模组改了受击无敌的时长——Mohist 系混合端的门槛是受击无敌时长的一半、
 * 插件能改；或者排在后面的监听器清了无敌帧），在那里把这条待入账撤掉（{@link #onAccumulatorHurt}，收已取消的）：这一下打实了，不记账也不结算，
 * 账留给下一下，不会同一下又打又记。
 *
 * <h2>结算（并进那一下的输入值，照常过对方的伤害上限）</h2>
 * 目标不在受击无敌里（或这一下带 {@code bypasses_cooldown}）、账上有钱的一下：攻击事件时记下这一下的输入值（基础），
 * 到同一下的 {@code LivingHurtEvent}（最高优先级：相当于并进输入值，后面的二分减半、η 翻倍、护甲、MRU 盔甲照常按并进以后的数算）
 * 加上 {@code min(账, 基础 × max_ratio)}。这一下照常走完对方 {@code hurt} 里的上限判断，例如全息投影在 {@code LivingDamageEvent}
 * 最低优先级的单次封顶。近战用这个写法（定为 B），不改 {@code CriticalHitEvent}：不把这一下变成暴击，横扫、暴击音效和别的模组的暴击判定都照旧。
 * 举盾挡得住的（{@code isDamageSourceBlocked}）攻击事件时就不登记结算，账留着：纯 Forge 在 {@code LivingHurtEvent} 之前就把它扣成 0，
 * Mohist 系混合端却在 {@code LivingHurtEvent} 之后才格挡，登记了的话补上的伤害会并进去、被盾牌整下挡掉还照发 {@code LivingDamageEvent(0)} 把账清了，
 * 盾牌也多掉这笔补伤的耐久——两种环境结果不同。{@code LivingHurtEvent} 里伤害已经是 0、事件已经被取消的同样不结算。
 * <p>
 * 账在这一下打实以后清掉：同一下的 {@code LivingDamageEvent} 到了（没被取消），或本刻末看到这一下的 {@code LivingHurtEvent} 最终被取消、
 * 伤害被改成 0（被盔甲、别的模组整下挡掉了——这一下已经打到了对方身上）。Mohist 系混合端的插件在 {@code LivingHurtEvent} 之后拦下的那一下
 * 两样都不满足，账留着，什么都没发生。
 *
 * <h2>和 ζ 的区别</h2>
 * ζ 每下花 UBMRU 硬清目标的受击无敌；累加器不清，只把浪费掉的记账。戴着 ζ 时目标总不在受击无敌里，没有可记的，累加器自然闲着。
 *
 * <h2>上限</h2>
 * 每名攻击者最多 {@code max_entries} 笔账（满了丢最久没记账的那笔），每笔最多 {@value #MAX_BALANCE}；每刻待定的记账、结算各最多 {@value #MAX_PENDING} 条；
 * 每 {@value #PRUNE_INTERVAL} 刻清一次过期的账，下线时清掉自己的账。只在服务端主线程读写。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class AccumulatorEvents {

    /** {@code LivingEntity.lastHurt} 的 SRG 名（build/createSrgToMcp/output.srg：f_20898_ → lastHurt） */
    public static final String LAST_HURT_SRG = "f_20898_";
    /** 一笔账最多这么多（结算本来就封顶在这一下 × max_ratio，这里只防浮点越加越大） */
    public static final float MAX_BALANCE = 1_000_000F;
    /** 一刻最多记这么多条待定的记账、待结算的攻击 */
    public static final int MAX_PENDING = 256;
    /** 每隔这么多刻清一次过期的账 */
    public static final int PRUNE_INTERVAL = 200;

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 一笔账：累计被吞掉的伤害、最后一次入账的服务端刻 */
    private static final class Entry {
        float amount;
        int lastTick;
    }

    /** 攻击事件时记下的「这一下整下吞掉了」，本刻末看事件最终没被取消、同一下也没走到 LivingHurtEvent 才入账 */
    private record PendingAccrual(LivingAttackEvent event, UUID attacker, UUID target, float amount, int tick) {
    }

    /** 要结算的一下：攻击事件时记下（满额那种、账上有钱），LivingHurtEvent 并进去，LivingDamageEvent / 本刻末清账 */
    private static final class Strike {
        final DamageSource source;
        final LivingEntity target;
        final UUID attacker;
        final float base;
        @Nullable
        LivingHurtEvent hurt;
        boolean settled;

        Strike(DamageSource source, LivingEntity target, UUID attacker, float base) {
            this.source = source;
            this.target = target;
            this.attacker = attacker;
            this.base = base;
        }
    }

    /** 攻击者 → (目标 → 账)，按最后一次入账的先后排（入账时挪到末尾，满了丢最前面的） */
    private static final Map<UUID, LinkedHashMap<UUID, Entry>> ACCOUNTS = new HashMap<>();
    private static final List<PendingAccrual> ACCRUALS = new ArrayList<>();
    private static final List<Strike> STRIKES = new ArrayList<>();
    @Nullable
    private static Field lastHurtField;
    private static boolean lastHurtFailed;
    private static int lastPrune;

    private AccumulatorEvents() {
    }

    // ------------------------------------------------------------------ 配置

    /**
     * {@code general.computer_modules.accumulator}：{@code max_ratio}（结算最多补这一下的几倍，默认 1.0 = 最多翻一倍）、
     * {@code forget_ticks}（多少刻没再入账就作废，默认 40）、{@code max_entries}（每名攻击者最多记几个目标，默认 32）。
     */
    public static final class Config {

        /**
         * @param maxRatio    结算最多补这一下的几倍
         * @param forgetTicks 多少刻没再入账就作废
         * @param maxEntries  每名攻击者最多记几个目标
         */
        public record Values(double maxRatio, int forgetTicks, int maxEntries) {
        }

        public static final Values DEFAULTS = new Values(1.0D, 40, 32);

        @Nullable
        private static ForgeConfigSpec.DoubleValue maxRatio;
        @Nullable
        private static ForgeConfigSpec.IntValue forgetTicks;
        @Nullable
        private static ForgeConfigSpec.IntValue maxEntries;
        @Nullable
        private static volatile Values testOverride;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.comment("Accumulator Module: damage swallowed by the target's hurt cooldown is stored and added to your next hit that lands.",
                    "累加器模块：打进对方受击无敌、被整下吞掉的伤害记账，下一下打实时一起结算").push("accumulator");
            maxRatio = builder.comment("At most this many times the landing hit's own damage is added (1.0 = the hit can at most double).",
                            "结算时最多补上这一下伤害的几倍（1.0 = 最多翻一倍）")
                    .defineInRange("max_ratio", DEFAULTS.maxRatio(), 0D, 10D);
            forgetTicks = builder.comment("Stored damage is lost after this many ticks without another swallowed hit on the same target.",
                            "同一个目标这么多刻（tick）没再记账，这笔账作废")
                    .defineInRange("forget_ticks", DEFAULTS.forgetTicks(), 1, 1200);
            maxEntries = builder.comment("How many targets one player can hold stored damage on at once (the oldest is dropped).",
                            "每名玩家最多同时记几个目标的账（满了丢最久没记账的）")
                    .defineInRange("max_entries", DEFAULTS.maxEntries(), 1, 256);
            builder.pop();
        }

        /** 现在的数值：测试覆盖值 → 配置（没加载时默认值） */
        public static Values values() {
            Values override = testOverride;
            if (override != null) {
                return override;
            }
            if (maxRatio == null) {
                return DEFAULTS;
            }
            return new Values(ECConfig.get(maxRatio), ECConfig.get(forgetTicks), ECConfig.get(maxEntries));
        }

        /** GameTest 用：整套换成给定的数值，null = 回到配置。同一段同步代码里设、测、置回 */
        public static void setTestOverrides(@Nullable Values values) {
            testOverride = values;
        }
    }

    // ------------------------------------------------------------------ 查

    /** attacker 对 target 现在的账（没有或过期为 0） */
    public static float balance(Player attacker, Entity target) {
        return attacker.getServer() == null ? 0F : balance(attacker.getUUID(), target.getUUID(), attacker.getServer().getTickCount());
    }

    private static float balance(UUID attacker, UUID target, int now) {
        LinkedHashMap<UUID, Entry> account = ACCOUNTS.get(attacker);
        Entry entry = account == null ? null : account.get(target);
        if (entry == null) {
            return 0F;
        }
        if (!fresh(entry.lastTick, now, Config.values().forgetTicks())) {
            remove(attacker, target);
            return 0F;
        }
        return entry.amount;
    }

    /** 记在 t、现在 now：0 ≤ now − t ≤ window 才算（重启后刻数从 0 算，比现在还晚的当过期） */
    static boolean fresh(int t, int now, int window) {
        int age = now - t;
        return age >= 0 && age <= window;
    }

    /**
     * 这一下算不算累加器认的攻击（见类注释「认哪几种攻击」；不看模块开没开）。
     *
     * @param attacker 伤害的真正来源
     */
    public static boolean settleable(DamageSource source, ServerPlayer attacker) {
        if (BaubleEffects.isBaubleDamage() || InlayEvents.isNotWeaponHit()) {
            return false;
        }
        if (source.is(ECGuns.MRU_RAY)) {
            return source.getEntity() == attacker;
        }
        return source.getDirectEntity() == attacker && source.getEntity() == attacker
                && !source.is(DamageTypes.THORNS) && !source.is(DamageTypeTags.IS_PROJECTILE) && !source.is(DamageTypeTags.IS_EXPLOSION)
                && !ECDamageKinds.isMagic(source);
    }

    /** 目标的 {@code lastHurt}（反射读不到时 null：不记账，只少一个功能） */
    @Nullable
    static Float lastHurt(LivingEntity entity) {
        Field field = lastHurtField();
        if (field == null) {
            return null;
        }
        try {
            return field.getFloat(entity);
        } catch (IllegalAccessException | IllegalArgumentException e) {
            return null;
        }
    }

    @Nullable
    private static Field lastHurtField() {
        if (lastHurtField == null && !lastHurtFailed) {
            try {
                lastHurtField = ObfuscationReflectionHelper.findField(LivingEntity.class, LAST_HURT_SRG);
            } catch (RuntimeException e) {
                lastHurtFailed = true;
                LOGGER.error("[EC5] 累加器模块找不到 LivingEntity.lastHurt（{}），被受击无敌吞掉的伤害不再记账", LAST_HURT_SRG, e);
            }
        }
        return lastHurtField;
    }

    // ------------------------------------------------------------------ 事件

    /** 记账 / 登记要结算的一下（见类注释） */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAccumulatorAttack(LivingAttackEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) {
            return;
        }
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof ServerPlayer attacker) || !ECProtection.isRealPlayer(attacker) || attacker == target) {
            return;
        }
        float amount = event.getAmount();
        if (!(amount > 0F) || !settleable(source, attacker) || !ModuleCache.active(attacker, ECCombatModules.ACCUMULATOR)) {
            return;
        }
        int now = attacker.server.getTickCount();
        if (target.invulnerableTime > 10 && !source.is(DamageTypeTags.BYPASSES_COOLDOWN)) {
            // 原版 LivingEntity.hurt：受击无敌里这一下不比上一下重就整下吞掉；比上一下重的只打出差额，不记也不结算
            Float last = lastHurt(target);
            if (last != null && amount <= last && !target.isDamageSourceBlocked(source) && ACCRUALS.size() < MAX_PENDING) {
                ACCRUALS.add(new PendingAccrual(event, attacker.getUUID(), target.getUUID(), amount, now));
            }
            return;
        }
        // 举盾挡得住的不登记：混合端在 LivingHurtEvent 之后才格挡，登记了会把账白白清掉（见类注释「结算」）
        if (STRIKES.size() < MAX_PENDING && !target.isDamageSourceBlocked(source) && balance(attacker.getUUID(), target.getUUID(), now) > 0F) {
            STRIKES.add(new Strike(source, target, attacker.getUUID(), amount));
        }
    }

    /**
     * 结算：把账并进这一下（最高优先级，见类注释）。收已取消的：先撤掉同一下按「整下吞掉」记下的待入账——走到这里就说明它没被吞掉；
     * 已取消的事件只做这一步。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onAccumulatorHurt(LivingHurtEvent event) {
        if (!ACCRUALS.isEmpty()) {
            dropAccrual(event.getSource(), event.getEntity());
        }
        if (event.isCanceled() || STRIKES.isEmpty()) {
            return;
        }
        Strike strike = find(event.getSource(), event.getEntity());
        if (strike == null || strike.hurt != null || !(event.getAmount() > 0F) || strike.target.getServer() == null) {
            return;
        }
        float balance = balance(strike.attacker, strike.target.getUUID(), strike.target.getServer().getTickCount());
        float bonus = (float) Math.min(balance, strike.base * Config.values().maxRatio());
        if (!(bonus > 0F)) {
            return;
        }
        event.setAmount(event.getAmount() + bonus);
        strike.hurt = event;
    }

    /** 这一下打实了：清账 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAccumulatorDamage(LivingDamageEvent event) {
        if (STRIKES.isEmpty()) {
            return;
        }
        for (Strike strike : STRIKES) {
            if (strike.hurt != null && !strike.settled && strike.source == event.getSource() && strike.target == event.getEntity()) {
                settle(strike);
            }
        }
    }

    @SubscribeEvent
    public static void onAccumulatorServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        flush();
        int now = event.getServer().getTickCount();
        if (!ACCOUNTS.isEmpty() && (now - lastPrune >= PRUNE_INTERVAL || now < lastPrune)) {
            prune(now);
        }
    }

    @SubscribeEvent
    public static void onAccumulatorLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ACCOUNTS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onAccumulatorServerStopped(ServerStoppedEvent event) {
        ACCOUNTS.clear();
        ACCRUALS.clear();
        STRIKES.clear();
        lastPrune = 0;
    }

    /**
     * 本刻末的处理（自动调；GameTest 在 hurt 之后直接调）：先清掉这一刻打实了的账（伤害事件里没认到、但 LivingHurtEvent 最终被取消或改成 0 的也算打到了），
     * 再把这一刻被吞掉、攻击事件最终没被取消的几下入账（走到了 LivingHurtEvent 的已经在那里撤掉）。
     */
    public static void flush() {
        if (!STRIKES.isEmpty()) {
            for (Strike strike : STRIKES) {
                if (strike.hurt != null && !strike.settled && RayHitGate.hurtZeroed(strike.hurt)) {
                    settle(strike);
                }
            }
            STRIKES.clear();
        }
        if (!ACCRUALS.isEmpty()) {
            List<PendingAccrual> batch = new ArrayList<>(ACCRUALS);
            ACCRUALS.clear();
            for (PendingAccrual pending : batch) {
                if (!pending.event().isCanceled()) {
                    accrue(pending.attacker(), pending.target(), pending.amount(), pending.tick());
                }
            }
        }
    }

    /** GameTest 用：丢掉这名玩家的全部账 */
    public static void forgetForTest(Player attacker) {
        ACCOUNTS.remove(attacker.getUUID());
    }

    // ------------------------------------------------------------------ 内部

    /** 撤掉这一下（同一个伤害源对象、同一个目标）攻击事件时记下的待入账 */
    private static void dropAccrual(DamageSource source, LivingEntity target) {
        UUID id = target.getUUID();
        ACCRUALS.removeIf(pending -> pending.event().getSource() == source && pending.target().equals(id));
    }

    @Nullable
    private static Strike find(DamageSource source, LivingEntity target) {
        for (Strike strike : STRIKES) {
            if (strike.source == source && strike.target == target) {
                return strike;
            }
        }
        return null;
    }

    private static void settle(Strike strike) {
        strike.settled = true;
        remove(strike.attacker, strike.target.getUUID());
    }

    private static void accrue(UUID attacker, UUID target, float amount, int tick) {
        Config.Values cfg = Config.values();
        LinkedHashMap<UUID, Entry> account = ACCOUNTS.computeIfAbsent(attacker, id -> new LinkedHashMap<>());
        Entry entry = account.remove(target);
        if (entry == null || !fresh(entry.lastTick, tick, cfg.forgetTicks())) {
            entry = new Entry();
        }
        entry.amount = Math.min(MAX_BALANCE, entry.amount + amount);
        entry.lastTick = tick;
        account.put(target, entry);
        Iterator<UUID> oldest = account.keySet().iterator();
        while (account.size() > cfg.maxEntries() && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
    }

    private static void remove(UUID attacker, UUID target) {
        LinkedHashMap<UUID, Entry> account = ACCOUNTS.get(attacker);
        if (account != null && account.remove(target) != null && account.isEmpty()) {
            ACCOUNTS.remove(attacker);
        }
    }

    private static void prune(int now) {
        lastPrune = now;
        int window = Config.values().forgetTicks();
        for (Iterator<LinkedHashMap<UUID, Entry>> it = ACCOUNTS.values().iterator(); it.hasNext(); ) {
            LinkedHashMap<UUID, Entry> account = it.next();
            account.values().removeIf(entry -> !fresh(entry.lastTick, now, window));
            if (account.isEmpty()) {
                it.remove();
            }
        }
    }
}
