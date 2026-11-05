package com.flameforge.essentialcraft5.item.ember;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.item.EmberInlayItem;
import com.flameforge.essentialcraft5.item.ForgedElementalSwordItem;
import com.flameforge.essentialcraft5.item.bauble.BaubleEffects;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.registry.ECEmberForge;
import com.flameforge.essentialcraft5.util.ECDamageKinds;
import com.flameforge.essentialcraft5.util.ECLandedHit;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.Util;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.AnvilRepairEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * 余烬嵌刻的事件（第十批 10b，batch10.md §5.7.2）：铁砧上嵌、打实以后触发。嵌片本身见 {@link EmberInlayItem}。
 *
 * <h2>嵌（{@code AnvilUpdateEvent}）</h2>
 * 左格是一件能嵌的武器（{@link EmberInlayItem#isInlayable}，只放一件），右格是记着一对余烬的嵌片 → 出一件拷贝、根级 NBT 换成这一对，
 * 花 {@code anvil_levels} 级经验、1 片嵌片。一把武器只有一片：已经嵌着别的就换掉（旧的不退），嵌着同一对的不给出（白花经验）。
 * 改名照原版铁砧的规则一起生效（同 {@code gun/GunAnvilEvents}）。第一次取走嵌好的武器发手册进度 {@code guide/void_machines/ember_inlay}（{@code AnvilRepairEvent}）。
 *
 * <h2>触发（{@code LivingDamageEvent}，伤害打实以后）</h2>
 * 攻击者是真玩家（不认假玩家），而且这一下是：
 * <ul>
 *   <li><b>近战</b>：直接来源就是这名玩家（{@code Player.attack} 的这一下、横扫），不算荆棘、魔法、弹射物——主手那件带嵌刻；</li>
 *   <li><b>弓 / 弩</b>：直接来源是这名玩家射出的箭，玩家主手是带嵌刻的弓或弩（射出去以后换了手里的东西就看换上的那件）。</li>
 * </ul>
 * 饰品打出的伤害（ν 的连锁、σ 的反弹，{@code BaubleEffects.isBaubleDamage}）和尖刺圆盾的范围伤害（{@link #notWeaponHit}）同样是记在玩家头上、
 * 直接来源是玩家本人的 {@code playerAttack}，但不是武器打的，不算（不然戴 ν 进刷怪塔，每杀一只就让连锁打到的十几只各触发一次、各扣一次）。
 * <p>
 * 先记下来（事件对象、攻击者、目标、那一对），<b>本刻末</b>（{@code ServerTickEvent} END）再结算：这时伤害事件已经走完，
 * 被排在后面的监听器取消、或者最终伤害是 0 的都不算——别的模组在 {@code LivingHurtEvent} 里取消原伤害时，Mohist 系混合端照样按 0 发
 * {@code LivingDamageEvent}，举盾整下挡掉也是 0；混合端插件拦下的那一下根本不发 {@code LivingDamageEvent}，什么都不发生。
 * <b>伤害吸收</b>：{@code LivingDamageEvent} 里的数是扣过伤害吸收（金苹果的黄心）以后的，黄心整下吃掉的一下是 0，可它确实打实了。
 * 「打实」统一由 {@link ECLandedHit} 判（伤害 &gt; 0 或者打掉了伤害吸收，第十三批 13c 起和审判庭、虚空、相性、同调共用一份记录），
 * 在 {@code LivingDamageEvent} 时问一次、记进待结算的那条，刻末结算时再看事件最终有没有被取消。
 * <p>
 * 结算：同一刻、同一名玩家对同一个生物的几段伤害只结算一次（别的模组的武器在攻击时先附带打一下、主击照常落下，那是一次攻击），
 * 取第一段打实的；前面一段没打实（被取消、是 0、扣不起）时后面一段顶上。横扫、溅射打的是不同的生物，各算一下。
 * 扣 {@code mru_per_hit} MRU（{@code MRUDrain.playerUseMRU}：身上的储存物品，凑不够再看武器自己存的；扣不起就什么都不触发），
 * 再逐个调 {@link ForgedElementalSwordItem#applyEmber}（和余烬元素剑同一段：引雷每人 2 秒一次、反噬照旧）。<b>只给效果，不加伤害。</b>
 * 放到刻末结算还有一个好处：引雷要对目标再打一下，不能套在它这一下伤害事件里面打。
 * <p>
 * 每刻最多记 {@value #MAX_PENDING} 条（正常一刻只有几条），多出来的这一刻不触发。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class InlayEvents {

    /** 一刻最多记这么多条待结算的命中 */
    public static final int MAX_PENDING = 256;

    /** 待结算的一下；absorbed = 这一下吃掉了目标的伤害吸收（伤害是 0 也算打实） */
    private record Pending(LivingDamageEvent event, ServerPlayer attacker, LivingEntity target, EmberInlayItem.Pair pair, ItemStack weapon,
                           boolean absorbed) {
    }

    /** 结算时去重的键：攻击者、目标（按对象比） */
    private record HitKey(UUID attacker, LivingEntity target) {
    }

    private static final List<Pending> PENDING = new ArrayList<>();
    private static final ThreadLocal<int[]> NOT_WEAPON_HIT = ThreadLocal.withInitial(() -> new int[1]);

    private InlayEvents() {
    }

    // ------------------------------------------------------------------ 铁砧

    @SubscribeEvent
    public static void onInlayAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack result = inlayResult(event.getLeft(), event.getRight(), event.getName());
        if (result.isEmpty()) {
            return;
        }
        event.setOutput(result);
        event.setCost(EmberInlayItem.Config.anvilLevels());
        event.setMaterialCost(1);
    }

    /**
     * 铁砧的产物（纯函数，GameTest 直接测）：左边能嵌、右边是记着一对的嵌片、功能开着、左边没嵌着同一对 → 左边的拷贝换上这一对；否则空。
     *
     * @param name 铁砧里输入的名字（null / 空白 = 没改名：左边有自定义名字就去掉，同原版）
     */
    public static ItemStack inlayResult(ItemStack left, ItemStack right, @Nullable String name) {
        if (!EmberInlayItem.Config.enabled() || !EmberInlayItem.isInlayable(left)) {
            return ItemStack.EMPTY;
        }
        Optional<EmberInlayItem.Pair> pair = EmberInlayItem.embers(right);
        if (pair.isEmpty() || pair.equals(EmberInlayItem.inlayOf(left))) {
            return ItemStack.EMPTY;
        }
        ItemStack result = left.copy();
        result.setCount(1);
        EmberInlayItem.setInlay(result, pair.get());
        if (name != null && !Util.isBlank(name)) {
            if (!name.equals(left.getHoverName().getString())) {
                result.setHoverName(Component.literal(name));
            }
        } else if (left.hasCustomHoverName()) {
            result.resetHoverName();
        }
        return result;
    }

    /** 取走嵌好的武器：第一次发手册进度（右格是嵌片、产物带嵌刻才算） */
    @SubscribeEvent
    public static void onInlayAnvilRepair(AnvilRepairEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && EmberInlayItem.embers(event.getRight()).isPresent()
                && EmberInlayItem.inlayOf(event.getOutput()).isPresent()) {
            awardGuideStep(player);
        }
    }

    /** 手册步骤「余烬嵌刻」：进度不存在、已完成时什么都不做（条件名不写死） */
    public static void awardGuideStep(ServerPlayer player) {
        Advancement advancement = player.server.getAdvancements().getAdvancement(ECEmberForge.ADVANCEMENT_EMBER_INLAY);
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

    // ------------------------------------------------------------------ 触发

    /**
     * 以「不是武器打的」身份执行 action：这期间打出的玩家攻击伤害不触发主手的嵌刻（尖刺圆盾举满时的范围伤害）。
     * 写法同 {@code BaubleEffects.asBaubleDamage}；不直接借用那个标记，是因为 ζ / η / ι 照旧要认尖刺圆盾的伤害。
     */
    public static boolean notWeaponHit(BooleanSupplier action) {
        int[] depth = NOT_WEAPON_HIT.get();
        depth[0]++;
        try {
            return action.getAsBoolean();
        } finally {
            depth[0]--;
        }
    }

    /** 现在打出的伤害在 {@link #notWeaponHit} 里 */
    public static boolean isNotWeaponHit() {
        return NOT_WEAPON_HIT.get()[0] > 0;
    }

    /** 饰品、尖刺圆盾这类打出的伤害：不是武器打的，一概不看 */
    private static boolean notFromWeapon() {
        return BaubleEffects.isBaubleDamage() || isNotWeaponHit();
    }

    @SubscribeEvent
    public static void onInlayDamage(LivingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide() || !EmberInlayItem.Config.enabled() || notFromWeapon()) {
            return;
        }
        ServerPlayer attacker = attacker(event.getSource());
        if (attacker == null || attacker == target) {
            return;
        }
        Optional<EmberInlayItem.Pair> pair = inlayHit(event.getSource(), attacker);
        if (pair.isEmpty()) {
            return;
        }
        if (!ECLandedHit.landed(event)) {
            return;
        }
        boolean absorbed = ECLandedHit.absorbed(event) > 0F;
        if (PENDING.size() < MAX_PENDING) {
            PENDING.add(new Pending(event, attacker, target, pair.get(), attacker.getMainHandItem(), absorbed));
        }
    }

    /** 主手那件嵌着带效果的一对、而且这一下是用它打的（{@link #weaponHit}）时返回那一对 */
    private static Optional<EmberInlayItem.Pair> inlayHit(DamageSource source, ServerPlayer attacker) {
        ItemStack weapon = attacker.getMainHandItem();
        Optional<EmberInlayItem.Pair> pair = EmberInlayItem.inlayOf(weapon);
        if (pair.isEmpty() || pair.get().effects().isEmpty() || !weaponHit(source, attacker, weapon)) {
            return Optional.empty();
        }
        return pair;
    }

    /** 伤害的真正来源是真玩家时返回它 */
    @Nullable
    private static ServerPlayer attacker(DamageSource source) {
        Entity entity = source.getEntity();
        return entity instanceof ServerPlayer player && ECProtection.isRealPlayer(player) ? player : null;
    }

    /**
     * 这一下是不是用这件武器打的（见类注释）：近战 = 直接来源是玩家本人、不是荆棘 / 魔法 / 弹射物；
     * 弓弩 = 直接来源是这名玩家的箭、主手是弓或弩。
     */
    public static boolean weaponHit(DamageSource source, ServerPlayer attacker, ItemStack weapon) {
        Entity direct = source.getDirectEntity();
        if (direct == attacker) {
            return !source.is(DamageTypes.THORNS) && !source.is(DamageTypeTags.IS_PROJECTILE) && !ECDamageKinds.isMagic(source);
        }
        return direct instanceof AbstractArrow arrow && arrow.getOwner() == attacker && weapon.getItem() instanceof ProjectileWeaponItem;
    }

    @SubscribeEvent
    public static void onInlayServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!PENDING.isEmpty()) {
            flush();
        }
    }

    /**
     * 结算这一刻记下的命中（见类注释），返回真触发了几次。本刻末自动调；GameTest 在 hurt 之后直接调。
     * 同一名玩家对同一个生物只结算第一条成了的（{@link #resolve} 为真才占住这一对，前面没成的让后面的顶上）。
     */
    public static int flush() {
        if (PENDING.isEmpty()) {
            return 0;
        }
        List<Pending> batch = new ArrayList<>(PENDING);
        PENDING.clear();
        Set<HitKey> done = new HashSet<>();
        int fired = 0;
        for (Pending pending : batch) {
            HitKey key = new HitKey(pending.attacker().getUUID(), pending.target());
            if (done.contains(key)) {
                continue;
            }
            if (resolve(pending)) {
                done.add(key);
                fired++;
            }
        }
        return fired;
    }

    private static boolean resolve(Pending pending) {
        LivingDamageEvent event = pending.event();
        ServerPlayer attacker = pending.attacker();
        if (event.isCanceled() || !(event.getAmount() > 0F || pending.absorbed()) || attacker.isRemoved() || !EmberInlayItem.Config.enabled()) {
            return false;
        }
        Set<EmberEffect> effects = pending.pair().effects();
        if (effects.isEmpty() || !MRUDrain.playerUseMRU(attacker, pending.weapon(), EmberInlayItem.Config.mruPerHit())) {
            return false;
        }
        for (EmberEffect effect : effects) {
            ForgedElementalSwordItem.applyEmber(effect, pending.target(), attacker);
        }
        return true;
    }

    /** 还没结算的条数（测试看） */
    public static int pendingCount() {
        return PENDING.size();
    }

    @SubscribeEvent
    public static void onInlayServerStopping(ServerStoppingEvent event) {
        PENDING.clear();
    }
}
