package com.flameforge.essentialcraft5.hoanna;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.gun.RayHitGate;
import com.flameforge.essentialcraft5.registry.ECBaubles;
import com.flameforge.essentialcraft5.util.ECEntityRules;
import com.flameforge.essentialcraft5.util.ECShieldBlocks;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Saddleable;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ξ 腰带（{@code essentialcraft5:irradiates_on_hit}，原作 {@code ECEventHandler.damageEvent} L405~506；第六批 6a M4，batch6a.md §5.4；
 * 第十批饰品平衡改了数值和打怪的做法）：
 * <ul>
 *   <li>戴着的人<b>打玩家</b>：对方 +伤害 × {@value #PER_DAMAGE_TARGET} 辐射，每一下最多 {@code bauble_limits.xi_dose_per_hit}（默认 5000，半级）
 *       （{@link #targetDose}），而且<b>只把对方的剂量推到 {@code bauble_limits.xi_dose_ceiling}（默认 15000）为止</b>（{@link #ceilingAdd}）：
 *       默认最多压到辐射 II（生命上限 −2），离开海纳尔约 17 分钟（在线）回到不压上限的程度。
 *       每点 5000 是原作的数，原作不封顶：一下 4 点就是 2 万剂量；第六批封到每下一级，但不封总量，挨上十来下就是十万剂量、
 *       生命上限只剩九点，剂量离开海纳尔每秒才散 5，要在线五六个小时才散完；挨上一百下就是上限 1 点、五十多个小时，死亡也不清——等于毁号。</li>
 *   <li>戴着的人<b>打别的生物</b>：对方最大生命降低<b>这一下实际扣掉的生命</b>——固定 UUID {@link #XI_MODIFIER} 的<b>一个</b> MAX_HEALTH 加法修饰符，
 *       每次取旧值再减、最低让最大生命留 1（原作每一下 new 一个随机 UUID 的修饰符，永久累积、实体 NBT 越来越大、能打到 0，batch3 §3.8 点名要修）。
 *       效果就是「ξ 打掉的血回不来」：凋灵的回血、末影龙的末地水晶、会回血的首领都补不回来。
 *       第十批改了三处：按扣掉的生命减（以前按护甲结算之前的伤害减，生命被压在新上限以下，等于每一下无视护甲、无视首领的减伤上限）——
 *       扣掉多少要等这一下结算完才知道（首领常在 {@code LivingDamageEvent} 里再夹一次，例如全息投影的单次封顶，和这里同是最低优先级、先后不定），
 *       所以伤害事件里只记下「打之前的生命」，本 tick 末按真正少掉的生命压上限（{@link #flushMaxHealth}，不超过这一下的伤害）；
 *       修饰符<b>不存盘</b>（{@code addTransientModifier}，区块卸载、重进世界后恢复，原作和以前都是永久的）；
 *       <b>宠物和坐骑不减</b>（驯服的狼猫、马、羊驼，装了鞍的骆驼 / 猪 / 炽足兽，正被玩家骑着的……，以前碰一下别人的马就永久少一截血），见 {@link #canLowerMaxHealth}。</li>
 *   <li>戴着的人<b>自己挨打</b>：+伤害 × {@value #PER_DAMAGE_SELF} 辐射（这是戴 ξ 的代价），同样只推到 {@code xi_dose_ceiling} 为止
 *       （以前不封顶：戴着 ξ 正常打怪，一小时挨两百点伤害就是二十万剂量、生命上限 1 点）。</li>
 * </ul>
 * 剂量上限只管 ξ 自己加的：已经在海纳尔攒到更高的人，ξ 不再加，也不减。
 * <p>
 * <b>攻击本身照常造成伤害</b>：这里只读伤害、不改伤害值，ξ 的效果叠在正常伤害之上。原作的 ξ 分支（{@code ECEventHandler} L450~457）同样不改
 * {@code event.getAmount()}，这一点和原作一样。
 * 打玩家的剂量照原作口径取 {@code LivingHurtEvent} 里的伤害（护甲结算之前；本监听器是最低优先级，别的模组的加减已经算进去），
 * 但<b>要等到 {@code LivingDamageEvent} 才生效</b>：Mohist 系混合端把 Bukkit 的 {@code EntityDamageByEntityEvent} 发在两者之间，
 * 被领地 / PvP 插件拦下的攻击走不到 {@code LivingDamageEvent}，就不给辐射（参照 {@code gun/RayHitGate}）；纯 Forge 里攻击事件被取消、
 * 无敌时间、伤害被改成 0 时也走不到。两个事件之间按「同一个目标、同一个伤害源对象、同一 tick」认同一下；没走到 Damage 的记录下一次清掉。
 * 记录里带着这一下的 {@code LivingHurtEvent}，认领时它最后被取消了、或者伤害被改成 0（没取消）也不算（{@code RayHitGate.hurtZeroed}）：
 * 本监听器是最低优先级，别的模组排在它后面取消或改成 0 时，纯 Forge 不再发 {@code LivingDamageEvent}，混合端却按 0 照样往下走、
 * Bukkit 事件放行后照发 {@code LivingDamageEvent(0)}（同 {@code BaubleCombatEvents} 里 ζ / ι / σ 的写法）。
 * 被盾牌整下挡掉的也不算（{@link ECShieldBlocks#fullyShielded}）：纯 Forge 在 {@code LivingHurtEvent} 之前就扣掉盾牌挡的部分，挡满时根本走不到这里；
 * 混合端在 {@code LivingHurtEvent} 之后才算举盾，挡满以后照样发 {@code LivingDamageEvent(0)}，不排除的话举盾挡下 ξ 佩戴者的一击照样吃剂量。
 * <p>
 * 只在服务端；攻击者 = 伤害源的「真正来源」（{@code getEntity()}，弹射物算射手）、打的是自己不算；假玩家不加辐射（{@link Radiation} 忽略）。
 * {@code general.radiation.enabled} 关掉时不加辐射，但打怪降最大生命照旧（那不是辐射剂量）。
 * <p>
 * <b>秒杀不按面值算</b>（6a 审查 M4-2）：
 * <ul>
 *   <li>{@code bypasses_invulnerability} 类伤害（{@code /kill}、掉出世界）<b>完全不算</b>：以前 {@code /kill} 走 {@code hurt(genericKill, Float.MAX_VALUE)}，
 *       戴 ξ 的人剂量直接加满 100 万、死亡不清零，复活后生命上限 1 点、离开海纳尔约 55 小时在线才散完。</li>
 *   <li>其余伤害按「这一下最多能打掉的生命」算：{@code min(伤害, 目标生命 + 伤害吸收)}（在 LivingHurtEvent 那一刻取），打人、挨打两处用它
 *       （打怪按真正少掉的生命，本来就不会超过剩下的生命）。整合包里的秒杀 / 超大伤害（暴击、枪、Boss）不会一下给出几十万剂量（原作 {@code (int)(amount × 1000)} 碰上超大伤害是 int 上限，原剂量 &gt; 0 时相加溢出）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class RadiationBaubleEvents {

    /** 打玩家：每点伤害给对方的剂量 */
    public static final int PER_DAMAGE_TARGET = 5000;
    /** 第六批定的每下封顶（一级）；现在每下封顶读 {@code bauble_limits.xi_dose_per_hit}，见 {@link #targetDose} */
    public static final int MAX_TARGET_DOSE_PER_HIT = Radiation.PER_LEVEL;
    /** 自己挨打：每点伤害给自己的剂量 */
    public static final int PER_DAMAGE_SELF = 1000;
    /** 打怪降最大生命用的修饰符（固定 UUID，同一只怪只有这一个） */
    public static final UUID XI_MODIFIER = UUID.nameUUIDFromBytes("essentialcraft5:xi_radiation".getBytes(StandardCharsets.UTF_8));
    public static final String XI_MODIFIER_NAME = "essentialcraft5:xi_radiation";
    /** 等待 LivingDamageEvent 的记录超过这么多条就清一次（正常只有 0~2 条） */
    private static final int MAX_PENDING = 32;

    /**
     * 一次正在结算的伤害：目标、伤害源（按对象比）、LivingHurtEvent 最后的伤害（按 {@link #countedDamage} 截过）、那一刻的 gameTime、要做的两件事、
     * 这一下的 LivingHurtEvent（结算时看它最后有没有被取消）
     */
    private record Pending(LivingEntity target, DamageSource source, float amount, long tick, boolean attackerWears, boolean targetWears,
                           LivingHurtEvent hurtEvent) {
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    /** 打怪要压的上限：目标、这一下扣血之前的生命、这一下的伤害（LivingDamageEvent 里看到的数）；本 tick 末结算 */
    private record Lowering(LivingEntity target, float healthBefore, float dealt) {
    }

    private static final List<Lowering> LOWERINGS = new ArrayList<>();
    /** 一个 tick 最多记这么多（超出的这一下不压上限） */
    private static final int MAX_LOWERINGS = 256;

    private RadiationBaubleEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onXiHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide || event.getAmount() <= 0F) {
            return;
        }
        DamageSource source = event.getSource();
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;   // /kill、掉出世界：不给辐射（M4-2）
        }
        Entity attacker = source.getEntity();
        boolean attackerWears = attacker instanceof Player player && attacker != target && wears(player);
        boolean targetWears = target instanceof Player player && wears(player);
        if (!attackerWears && !targetWears) {
            return;
        }
        long now = target.level().getGameTime();
        if (!PENDING.isEmpty()) {
            PENDING.removeIf(p -> p.tick() != now);
            if (PENDING.size() >= MAX_PENDING) {
                PENDING.remove(0);
            }
        }
        PENDING.add(new Pending(target, source, countedDamage(event.getAmount(), target.getHealth(), target.getAbsorptionAmount()),
                now, attackerWears, targetWears, event));
    }

    /** 这一下算多少伤害：{@code min(伤害, 生命 + 伤害吸收)}，不为负（纯函数，GameTest 测） */
    public static float countedDamage(float amount, float health, float absorption) {
        return Math.max(0F, Math.min(amount, Math.max(0F, health) + Math.max(0F, absorption)));
    }

    /** GameTest 用：LivingHurtEvent 已记账、还在等 LivingDamageEvent 的这一下（同一个目标、同一个伤害源对象）有没有 */
    public static boolean hasPending(LivingEntity target, DamageSource source) {
        for (Pending pending : PENDING) {
            if (pending.target() == target && pending.source() == source) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onXiDamage(LivingDamageEvent event) {
        if (PENDING.isEmpty()) {
            return;
        }
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();
        long now = target.level().getGameTime();
        Pending found = null;
        for (int i = PENDING.size() - 1; i >= 0; i--) {
            Pending pending = PENDING.get(i);
            if (pending.target() == target && pending.source() == source) {
                PENDING.remove(i);
                if (pending.tick() == now) {
                    found = pending;
                }
                break;
            }
        }
        if (found == null || RayHitGate.hurtZeroed(found.hurtEvent()) || ECShieldBlocks.fullyShielded(target, source, event.getAmount())) {
            return;   // LivingHurtEvent 最后被取消或伤害被改成 0 的、被盾牌整下挡掉的：记录已经删了，不加辐射、不压上限（见类注释）
        }
        apply(target, found.amount(), found.attackerWears(), found.targetWears(), event.getAmount());
    }

    /**
     * 打玩家这一下给对方的剂量（还没按上限截）：{@code min(伤害 × }{@value #PER_DAMAGE_TARGET}{@code , xi_dose_per_hit)}，
     * counted 是按 {@link #countedDamage} 截过的伤害（纯函数，GameTest 测）
     */
    public static long targetDose(float counted) {
        return Math.max(0L, Math.min(ECBaubles.Limits.xiDosePerHit(), (long) (counted * (double) PER_DAMAGE_TARGET)));
    }

    /**
     * ξ 这次实际能加的剂量：只把剂量推到 ceiling 为止，{@code max(0, min(add, ceiling − current))}；已经超过上限的不加也不减（纯函数，GameTest 测）
     */
    public static long ceilingAdd(int current, long add, int ceiling) {
        if (add <= 0L || current >= ceiling) {
            return 0L;
        }
        return Math.min(add, (long) ceiling - Math.max(0, current));
    }

    /** 给 player 加 ξ 的剂量，按 {@code xi_dose_ceiling} 截；返回实际加了多少 */
    static long addCapped(Player player, long add) {
        long capped = ceilingAdd(Radiation.get(player), add, ECBaubles.Limits.xiDoseCeiling());
        if (capped > 0L) {
            Radiation.add(player, capped);
        }
        return capped;
    }

    /**
     * 结算一次落地的伤害（hurtAmount = LivingHurtEvent 的伤害按 {@link #countedDamage} 截过，dealt = 这次 LivingDamageEvent 的伤害，
     * 也就是要从生命里扣的数，0 表示全被吸收）。只加辐射、降最大生命，不动这一下的伤害（见类注释）。
     */
    static void apply(LivingEntity target, float hurtAmount, boolean attackerWears, boolean targetWears, float dealt) {
        boolean radiation = HoannaHazardsConfig.radiationEnabled();
        if (attackerWears) {
            if (target instanceof Player player) {
                if (radiation) {
                    addCapped(player, targetDose(hurtAmount));
                }
            } else {
                queueLowering(target, dealt);
            }
        }
        if (targetWears && target instanceof Player player && radiation) {
            addCapped(player, (long) (hurtAmount * (double) PER_DAMAGE_SELF));
        }
    }

    /** 记下这一下：本 tick 末按真正少掉的生命压上限（见类注释）；宠物、坐骑、伤害 ≤ 0 的不记 */
    static void queueLowering(LivingEntity target, float dealt) {
        if (dealt > 0F && canLowerMaxHealth(target) && LOWERINGS.size() < MAX_LOWERINGS) {
            LOWERINGS.add(new Lowering(target, target.getHealth(), dealt));
        }
    }

    /** GameTest 用：这只生物本 tick 有没有记下要压上限的一下（还没到 tick 末结算） */
    public static boolean hasLowering(LivingEntity target) {
        for (Lowering lowering : LOWERINGS) {
            if (lowering.target() == target) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onXiServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !LOWERINGS.isEmpty()) {
            flushMaxHealth();
        }
    }

    /**
     * 结算本 tick 记下的打怪：少掉的生命 = {@code min(这一下的伤害, 打之前的生命 − 现在的生命)}，大于 0 就压上限；死了、没了的跳过。
     * 上限和生命少同样多，所以生命不会超过新上限。
     *
     * @return 压了几次（GameTest 直接调）
     */
    public static int flushMaxHealth() {
        List<Lowering> batch = new ArrayList<>(LOWERINGS);
        LOWERINGS.clear();
        int lowered = 0;
        for (Lowering lowering : batch) {
            LivingEntity target = lowering.target();
            if (!target.isAlive() || target.isRemoved()) {
                continue;
            }
            float lost = Math.min(lowering.dealt(), lowering.healthBefore() - target.getHealth());
            if (lost > 0F) {
                lowerMaxHealth(target, lost);
                lowered++;
            }
        }
        return lowered;
    }

    /**
     * ξ 压不压这只生物的最大生命：宠物和坐骑不压——驯服 / 记了主人的（{@link ECEntityRules#isTamed}：狼猫、马、羊驼，驯服了却没记主人的也算）、
     * 装了鞍的（骆驼 {@code isTamed()} 恒为真却从不记主人，猪、炽足兽根本不是可驯服的生物）、正被玩家骑着的（含别的模组的坐骑）。
     * 不用 {@link ECEntityRules#isSpared}：它把起了名字的也排除，模组首领常用自定义名字，那样 ξ 就压不动首领了。
     */
    public static boolean canLowerMaxHealth(LivingEntity target) {
        return !(target instanceof Player) && !ECEntityRules.isTamed(target) && !(target instanceof Saddleable saddleable && saddleable.isSaddled())
                && !target.hasPassenger(passenger -> passenger instanceof Player);
    }

    /**
     * 打怪：固定 UUID 的修饰符再减 lost（这一下从生命里扣掉的数），最低让最大生命留 1；修饰符不存盘（区块卸载、重进世界后恢复）。
     * 由 {@link #flushMaxHealth} 在扣血之后调：上限和生命少同样多，生命不会超过新上限。宠物、坐骑、lost ≤ 0 时什么都不做。
     *
     * @return 修饰符现在的值（没变时是旧值，不压时 0）
     */
    public static double lowerMaxHealth(LivingEntity target, float lost) {
        AttributeInstance attribute = target.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null || !canLowerMaxHealth(target)) {
            return 0D;
        }
        AttributeModifier old = attribute.getModifier(XI_MODIFIER);
        double oldAmount = old == null ? 0D : old.getAmount();
        if (!(lost > 0F)) {
            return oldAmount;
        }
        if (old != null) {
            attribute.removeModifier(XI_MODIFIER);
        }
        double without = attribute.getValue();
        double amount = xiModifierAmount(oldAmount, lost, without);
        if (amount < 0D) {
            attribute.addTransientModifier(new AttributeModifier(XI_MODIFIER, XI_MODIFIER_NAME, amount, AttributeModifier.Operation.ADDITION));
        }
        return amount;
    }

    /** 新的修饰符值：{@code max(旧值 − 伤害, 1 − 不算它时的最大生命)}，且不为正（纯函数，GameTest 测下限） */
    public static double xiModifierAmount(double oldAmount, float damage, double maxHealthWithout) {
        double floor = Math.min(0D, 1D - maxHealthWithout);
        return Math.min(0D, Math.max(floor, oldAmount - damage));
    }

    private static boolean wears(Player player) {
        return !(player instanceof FakePlayer) && CuriosCompat.has(player, ECBaubles.IRRADIATES_ON_HIT);
    }

    @SubscribeEvent
    public static void onXiServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
        LOWERINGS.clear();
    }
}
