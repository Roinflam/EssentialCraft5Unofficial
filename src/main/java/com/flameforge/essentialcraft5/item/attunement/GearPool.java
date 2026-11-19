package com.flameforge.essentialcraft5.item.attunement;

import com.flameforge.essentialcraft5.item.ember.InlayEvents;
import com.flameforge.essentialcraft5.item.VerdictBladeItem;
import com.flameforge.essentialcraft5.registry.ECGuns;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 增伤池 / 减伤池（tools/plan/gear_growth.md 第 2 节）：本模组所有「这一下多打几成 / 少挨几成」的来源都登记在这里，
 * 由 {@link GearCombatEvents} 在 {@code LivingHurtEvent} NORMAL 一处拉取、相加、过总闸（{@link GearCaps}）、乘一次。
 * <p>
 * 拉取式：各模块在自己的 init 里 {@link #registerOffense} / {@link #registerDefense} 一个函数，被问到时返回这一下的比例（0 = 不加）。
 * 原来写在各自 {@code LivingHurtEvent} 监听器里的副作用（记特性增伤、记回击用掉……）留在登记的函数里照做，所以<b>每次伤害只问一次</b>，
 * 测试想看分项用 {@link #offense} / {@link #defense} 的返回值。
 * <p>
 * 增伤来源返回的数，目标是玩家时已经乘过 {@code pvp_effect_scale}（各来源自己乘，同以前）。
 */
public final class GearPool {

    /**
     * 一下伤害的上下文。
     *
     * @param attacker    伤害记在谁头上（可以是 null）
     * @param target      挨打的
     * @param amount      本下进 NORMAL 时的伤害
     * @param weapon      这一下是不是真玩家亲手用主手同调武器打实的（{@link #ownWeapon}）：是就是那件武器，否则 null
     * @param targetClass 目标类别（增伤池上限按它取）
     */
    public record Context(DamageSource source, @Nullable LivingEntity attacker, LivingEntity target, float amount,
                          @Nullable ItemStack weapon, GearCaps.TargetClass targetClass) {

        public boolean ownWeaponHit() {
            return weapon != null;
        }

        /** 目标是玩家时的效果倍率（{@code general.attunement.pvp_effect_scale}），否则 1 */
        public double pvpScale() {
            return target instanceof Player ? Math.max(0D, AttunementConfig.pvpEffectScale()) : 1D;
        }
    }

    /** 增伤来源：返回这一下多打的比例（0 = 不加） */
    @FunctionalInterface
    public interface Offense {
        double bonus(Context context);
    }

    /** 减伤来源：返回这一下少挨的比例（0 = 不减）；context.target 是挨打的那个 */
    @FunctionalInterface
    public interface Defense {
        double reduction(Context context);
    }

    /** 各来源的分项（按登记顺序）和合计（没封顶） */
    public record Breakdown(Map<String, Double> parts, double total) {

        public double part(String id) {
            Double value = parts.get(id);
            return value == null ? 0D : value;
        }
    }

    private static final Map<String, Offense> OFFENSE = Collections.synchronizedMap(new LinkedHashMap<>());
    private static final Map<String, Defense> DEFENSE = Collections.synchronizedMap(new LinkedHashMap<>());

    private GearPool() {
    }

    /** 登记一个增伤来源（同一个 id 再登记会覆盖） */
    public static void registerOffense(String id, Offense offense) {
        OFFENSE.put(id, offense);
    }

    /** 登记一个减伤来源（同一个 id 再登记会覆盖） */
    public static void registerDefense(String id, Defense defense) {
        DEFENSE.put(id, defense);
    }

    /** 撤掉一个增伤来源（GameTest 临时登记的用完撤掉） */
    public static void removeOffense(String id) {
        OFFENSE.remove(id);
    }

    /** 撤掉一个减伤来源 */
    public static void removeDefense(String id) {
        DEFENSE.remove(id);
    }

    /** 问一遍所有增伤来源（会做各来源的副作用）；负数、非有限数当 0 */
    public static Breakdown offense(Context context) {
        Map<String, Double> parts = new LinkedHashMap<>();
        double total = 0D;
        Map<String, Offense> copy;
        synchronized (OFFENSE) {
            copy = new LinkedHashMap<>(OFFENSE);
        }
        for (Map.Entry<String, Offense> entry : copy.entrySet()) {
            double value = entry.getValue().bonus(context);
            if (value > 0D && Double.isFinite(value)) {
                parts.put(entry.getKey(), value);
                total += value;
            }
        }
        return new Breakdown(parts, total);
    }

    /** 问一遍所有减伤来源 */
    public static Breakdown defense(Context context) {
        Map<String, Double> parts = new LinkedHashMap<>();
        double total = 0D;
        Map<String, Defense> copy;
        synchronized (DEFENSE) {
            copy = new LinkedHashMap<>(DEFENSE);
        }
        for (Map.Entry<String, Defense> entry : copy.entrySet()) {
            double value = entry.getValue().reduction(context);
            if (value > 0D && Double.isFinite(value)) {
                parts.put(entry.getKey(), value);
                total += value;
            }
        }
        return new Breakdown(parts, total);
    }

    /** 造上下文（weapon、目标类别按 {@link #ownWeapon}、{@link GearCaps#targetClass} 算） */
    public static Context context(DamageSource source, LivingEntity target, float amount) {
        LivingEntity attacker = source.getEntity() instanceof LivingEntity living ? living : null;
        return new Context(source, attacker, target, amount, ownWeapon(source, target), GearCaps.targetClass(target));
    }

    /** 这两种伤害的数在别处已经定好了，一律不吃增伤池、不吸血、不引爆罪印：裁断 / 终审的补伤、装备觉醒的范围伤害 */
    public static boolean skipsOffense(DamageSource source) {
        return source.is(VerdictBladeItem.VERDICT) || source.is(GearAoe.GEAR_BURST);
    }

    /**
     * 这一下是不是真玩家亲手用主手那件同调武器打的：是就返回那件武器。
     * <ul>
     *   <li>剑这条线：近战直接来源是他本人（{@code InlayEvents.weaponHit}，不是荆棘 / 魔法 / 弹射物）；</li>
     *   <li>枪这条线：伤害类型是 {@code mru_ray}、记在他头上；</li>
     *   <li>反伤、饰品打出的、尖刺圆盾、范围技（{@link AttunementGains#notOwnHit}）和裁断都不算。</li>
     * </ul>
     */
    @Nullable
    public static ItemStack ownWeapon(DamageSource source, LivingEntity target) {
        if (skipsOffense(source) || !(source.getEntity() instanceof ServerPlayer player) || player == target || !ECProtection.isRealPlayer(player)
                || AttunementGains.notOwnHit(source)) {
            return null;
        }
        ItemStack weapon = player.getMainHandItem();
        AttunementFamily family = AttunementFamily.of(weapon);
        if (family == null || family.kind() != AttunementFamily.Kind.WEAPON) {
            return null;
        }
        if (family.line() == AttunementFamily.Line.GUN) {
            return source.is(ECGuns.MRU_RAY) ? weapon : null;
        }
        return source.getDirectEntity() == player && InlayEvents.weaponHit(source, player, weapon) ? weapon : null;
    }

    /**
     * 这一下是不是真玩家本人的直接近战（伤害按他的攻击属性算的那种）：是就返回他，不管主手现在拿的是什么。
     * 条件和 {@link #ownWeapon} 的剑那条线一样，只是不看主手。总闸用它按玩家身上实际挂着的攻击修饰符折算
     * （同一刻先切快捷栏再出手时，攻击属性还是上一把的，主手已经换了）。
     */
    @Nullable
    public static ServerPlayer ownMelee(DamageSource source, LivingEntity target) {
        if (skipsOffense(source) || !(source.getEntity() instanceof ServerPlayer player) || player == target || !ECProtection.isRealPlayer(player)
                || AttunementGains.notOwnHit(source) || source.is(ECGuns.MRU_RAY) || source.getDirectEntity() != player) {
            return null;
        }
        return InlayEvents.weaponHit(source, player, ItemStack.EMPTY) ? player : null;
    }
}
