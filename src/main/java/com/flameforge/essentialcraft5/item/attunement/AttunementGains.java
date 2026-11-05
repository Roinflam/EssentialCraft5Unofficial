package com.flameforge.essentialcraft5.item.attunement;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.api.MatrixRanks;
import com.flameforge.essentialcraft5.corruption.CorruptionTicker;
import com.flameforge.essentialcraft5.entity.boss.BossTier;
import com.flameforge.essentialcraft5.entity.boss.ECBoss;
import com.flameforge.essentialcraft5.item.bauble.BaubleEffects;
import com.flameforge.essentialcraft5.item.ember.InlayEvents;
import com.flameforge.essentialcraft5.player.MobKillLedger;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECTags;
import com.flameforge.essentialcraft5.util.ECDamageKinds;
import com.flameforge.essentialcraft5.util.ECLandedHit;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.flameforge.essentialcraft5.util.SpawnOrigin;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.ToIntFunction;

/**
 * 同调度从哪来（batch13.md §3.3.2，policy §4.16「同调」）：只在服务端、只认真玩家，只在伤害真的打实以后记（{@code LivingDamageEvent} LOWEST、没被取消，打实照 {@link ECLandedHit#landed}：最终伤害 &gt; 0 或者打掉了伤害吸收）。
 * <ul>
 *   <li><b>盔甲</b>：穿着挨了一下，身上每件符合的同调盔甲各得 {@code points_hit}（攻击者是首领 {@code points_boss_hit}）。攻击者要是活物、{@link Enemy} 或首领，
 *       不是刷怪笼 / 刷怪蛋来的（{@link SpawnOrigin#isArtificial}）、不在 {@code growth_excluded}，而且<b>这名攻击者 200 刻内被你打实过</b>（站着挨打不还手不攒）；
 *       同一攻击者 10 刻内只算一次。被本模组护盾 / 闪避 / 偏转整下吃掉的那一下没有 {@code LivingDamageEvent}，由那几处调 {@link #armorHitAbsorbed}。</li>
 *   <li><b>武器</b>：主手这件近战打实一下 {@code points_hit}；目标是 {@link Enemy} 或首领、来源公平、{@code isNoAi()} 为假，而且此刻在追你、或 200 刻内打中过你、或是首领。
 *       击杀另给 {@code points_kill}（首领 {@code points_boss_kill}）。</li>
 *   <li>对面是玩家时一律给 {@code points_pvp}（默认 0，两个号对刷拿不到东西）。</li>
 * </ul>
 * <b>不是本人亲手打的不算</b>（{@link #notOwnHit}）：反伤（原版荆棘附魔、宅男全套的反伤、σ 的反弹，{@code ECDamageKinds.isReflected}）不算「被你打实过」，
 * 也不给武器点；ν 的连锁、ι 的追击这类饰品自己打出的伤害（{@code BaubleEffects.isBaubleDamage}）和尖刺圆盾的范围伤害（{@code InlayEvents.isNotWeaponHit}）
 * 不给武器点。这几种伤害的攻击者、直接实体都是玩家本人，只看伤害源分不出来，靠它们打出时的作用域标记认。不然穿着全套宅男站着不动，
 * 怪打你一下就被反伤打回去，同时满足「被你打实过」和「主手打实」，挂机也能攒满两份预算。
 * <p>
 * 防挂机（D9 A）：同一只生物一生给每名玩家的盔甲点 ≤ 5、武器打实点 ≤ 3（击杀另算；首领不限，首领战本来就是正经内容，靠「互相打实」和预算限着，
 * 哪些首领算、要什么条件见 {@link #uncappedBoss}：本模组的小 Boss 档照普通怪的上限，别的模组和原版的首领要 200 刻内打中过你，武器点才不限），
 * 这份累计写在生物的持久数据里（{@value #KEY_GIVEN}），随实体存盘，区块卸载再加载、换维度都接着算；
 * 同一种实体每小时前 30 只给全额、之后减半，按只数、不按次数（一只怪挨打、被砍、被杀只算一只，这一只给的点数按第一次计数时定下的折扣算），
 * 计数记在玩家数据的击杀台账里（{@code player/MobKillLedger} 的同种计数：12 个 5 分钟的小桶按在线时间滚动），死亡重生、换维度、下线再上、关服重开都接着算；
 * {@link #addMultiplier} 登记的倍数（13c 起精英、13d 起巢穴怪 ×3）乘在点数和每只的上限上。
 * 这些都是在预算（{@code player/AttunementBudget}）之前的折扣，最后由预算封顶：同一事件给几件都只扣一次预算，已攒满本阶（等升阶）的件不拿点、不占预算。
 * <p>
 * 写盘节流（P2）：点数先记在内存里（每人按装备槽位），每 40 刻、下线、死亡、换维度时写回那个槽位上的物品，顺手把阶戳抬到穿戴者当时的矩阵阶（只升不降）。
 * 这件在写回之前离开了装备槽就不追着写（{@code LivingEquipmentChangeEvent} 的 from 是原版留的副本，写进去回不到真物品），最多丢 40 刻的点。
 * 写盘会改物品 NBT，原版会把它当一次装备变化（虚空、宅男的脱装判定按物品种类比，不受影响）。
 * <p>
 * 往来时间戳、待写点数是弱引用表（生物、玩家没了表项自动消失）；同种计数在玩家数据里。都只在服务端线程读写。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class AttunementGains {

    /** 「互相打实」的窗口：盔甲点要攻击者在这么多刻内被你打实过，武器点认「这么多刻内打中过你」 */
    public static final int RECIPROCAL_TICKS = 200;
    /** 同一攻击者多少刻内只给一次盔甲点 */
    public static final int SAME_ATTACKER_TICKS = 10;
    /** 同一只生物一生给每名玩家的盔甲点上限（首领不限，见 {@link #uncappedBoss}；乘 {@link #addMultiplier} 的倍数） */
    public static final int ARMOR_CAP_PER_CREATURE = 5;
    /** 同一只生物一生给每名玩家的武器打实点上限（击杀另算；首领不限，见 {@link #uncappedBoss}；乘倍数） */
    public static final int WEAPON_HIT_CAP_PER_CREATURE = 3;
    /** 同一种实体每小时前多少只给全额 */
    public static final int FULL_PER_TYPE_PER_HOUR = 30;
    /** 之后的折扣 */
    public static final float TYPE_FATIGUE = 0.5F;
    public static final int HOUR_TICKS = 72_000;
    /** 同种计数的小桶多长（在线刻）；{@link #TYPE_BUCKETS} 个小桶合起来是最近一小时。计数本身在 {@link MobKillLedger} */
    public static final int TYPE_BUCKET_TICKS = MobKillLedger.BUCKET_TICKS;
    public static final int TYPE_BUCKETS = MobKillLedger.BUCKETS;
    /** 生物持久数据里「每名玩家从它身上已拿了多少盔甲点、武器打实点」的键：{玩家 UUID: {armor, weapon}} */
    public static final String KEY_GIVEN = EssentialCraft5.MODID + ":attunement_given";
    private static final String KEY_ARMOR = "armor";
    private static final String KEY_WEAPON = "weapon";
    /** 写盘间隔（刻） */
    public static final int FLUSH_INTERVAL = 40;

    /** 宅男四件都到同调 2 阶时发的手册步骤（batch13.md §2.2） */
    public static final ResourceLocation GUIDE_TERMINAL_ATTUNED = EssentialCraft5.id("guide/terminal/attuned");
    public static final int GUIDE_TERMINAL_STAGE = 2;

    private static final EquipmentSlot[] GEAR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND};

    private static final List<ToIntFunction<LivingEntity>> MULTIPLIERS = new CopyOnWriteArrayList<>();

    /** 生物 → 每名玩家和它之间的往来 */
    private static final Map<LivingEntity, Map<UUID, Exchange>> MOBS = new WeakHashMap<>();
    /** 玩家 → 待写点数 */
    private static final Map<Player, State> PLAYERS = new WeakHashMap<>();

    private static long testNow = -1L;

    private AttunementGains() {
    }

    /** 一名玩家和一只生物之间的往来 */
    private static final class Exchange {
        /** 玩家最后一次打实它 */
        long hitByPlayer = Long.MIN_VALUE / 2;
        /** 它最后一次打实玩家 */
        long hitPlayer = Long.MIN_VALUE / 2;
        long lastArmor = Long.MIN_VALUE / 2;
        long lastWeapon = Long.MIN_VALUE / 2;
        /** 一生累计（不是首领时同步写进生物的持久数据，见 {@link #KEY_GIVEN}） */
        float armorGiven;
        float weaponGiven;
        /** 这只已经算进同种计数了；它给的点数一律按那时定下的 {@link #factor} 折 */
        boolean counted;
        float factor = 1F;
    }

    /** 一个装备槽上还没写进物品的点数（只认记下时的那个物品对象） */
    private static final class Pending {
        final ItemStack stack;
        float points;

        Pending(ItemStack stack) {
            this.stack = stack;
        }
    }

    private static final class State {
        final EnumMap<EquipmentSlot, Pending> pending = new EnumMap<>(EquipmentSlot.class);
    }

    // ------------------------------------------------------------------
    // 扩展点与测试钩子
    // ------------------------------------------------------------------

    /**
     * 登记一个点数倍数来源（13c 精英、13d 巢穴召出的怪各 ×3）：对一只生物返回 ≥ 1 的倍数，几个来源取最大，乘在点数和这只的上限上。
     * mod 构造期或 FMLCommonSetup 里调。
     */
    public static void addMultiplier(ToIntFunction<LivingEntity> multiplier) {
        MULTIPLIERS.add(multiplier);
    }

    public static void removeMultiplier(ToIntFunction<LivingEntity> multiplier) {
        MULTIPLIERS.remove(multiplier);
    }

    /** GameTest：把「现在」钉在某一刻（≥ 0），-1 恢复用服务器刻数 */
    public static void setTestTime(long now) {
        testNow = now;
    }

    // ------------------------------------------------------------------
    // 事件
    // ------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAttunementDamageLanded(LivingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || !ECLandedHit.landed(event)) {
            return;
        }
        DamageSource source = event.getSource();
        Entity attacker = source.getEntity();
        if (attacker == victim || !(attacker instanceof LivingEntity living)) {
            return;
        }
        if (ECProtection.isRealPlayer(attacker)) {
            ServerPlayer player = (ServerPlayer) attacker;
            // 反伤是挨打以后自动打回去的，不算你打实过它（饰品连锁、尖刺圆盾是你自己出的手，照记交手，只是不给武器点）
            if (!ECDamageKinds.isReflected(source)) {
                recordHitByPlayer(victim, player);
            }
            weaponHit(player, victim, source);
        }
        if (ECProtection.isRealPlayer(victim)) {
            ServerPlayer player = (ServerPlayer) victim;
            exchange(living, player).hitPlayer = now(player);
            armorHit(player, living);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAttunementDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) {
            return;
        }
        if (victim instanceof Player player) {
            // 死了先把攒的点写回去（掉落、keepInventory 都在这之后）
            flush(player);
        }
        Entity killer = event.getSource().getEntity();
        if (killer != victim && ECProtection.isRealPlayer(killer)) {
            weaponKill((ServerPlayer) killer, victim, event.getSource());
        }
    }

    @SubscribeEvent
    public static void onAttunementLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        flush(player);
        PLAYERS.remove(player);
    }

    @SubscribeEvent
    public static void onAttunementServerStopped(ServerStoppedEvent event) {
        MOBS.clear();
        PLAYERS.clear();
    }

    /** 换维度之前写（从末地回来会换一个新的玩家对象，内存里记的点跟不过去） */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAttunementTravel(EntityTravelToDimensionEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof Player player && !player.level().isClientSide) {
            flush(player);
        }
    }

    // ------------------------------------------------------------------
    // 记点
    // ------------------------------------------------------------------

    /** 玩家打实了这只生物（武器点的「此刻在追你」之外、盔甲点的「互相打实」都靠它） */
    public static void recordHitByPlayer(LivingEntity mob, Player player) {
        exchange(mob, player).hitByPlayer = now(player);
    }

    /** 这只生物打实了玩家（武器点「200 刻内打中过你」） */
    public static void recordHitPlayer(LivingEntity mob, Player player) {
        exchange(mob, player).hitPlayer = now(player);
    }

    /**
     * 本模组的护盾、闪避、偏转把一下整个吃掉时调（这一下没有 {@code LivingDamageEvent}）：照「挨了一下」给盔甲点。
     * 只在服务端调；攻击者不符合条件时什么都不做。
     */
    public static void armorHitAbsorbed(Player victim, DamageSource source) {
        if (victim.level().isClientSide || !ECProtection.isRealPlayer(victim) || !(source.getEntity() instanceof LivingEntity attacker)
                || attacker == victim) {
            return;
        }
        ServerPlayer player = (ServerPlayer) victim;
        exchange(attacker, player).hitPlayer = now(player);
        armorHit(player, attacker);
    }

    /**
     * 盔甲挨了攻击者一下（伤害已经打实）：按条件给身上符合的同调盔甲记点。返回这次给每件记的点数（0 = 没给）。
     */
    public static float armorHit(ServerPlayer player, LivingEntity attacker) {
        int base;
        boolean capped = true;
        int multiplier = 1;
        if (attacker instanceof Player other) {
            if (!ECProtection.isRealPlayer(other)) {
                return 0F;
            }
            base = AttunementConfig.pointsPvp();
        } else {
            if (!attacker.isAlive()) {
                return 0F;
            }
            boolean boss = isBoss(attacker);
            if (!boss && !(attacker instanceof Enemy)) {
                return 0F;
            }
            if (!fair(attacker)) {
                return 0F;
            }
            base = boss ? AttunementConfig.pointsBossHit() : AttunementConfig.pointsHit();
            capped = !boss || !uncappedBoss(attacker, player, false);
            multiplier = multiplier(attacker);
        }
        if (base <= 0) {
            return 0F;
        }
        long now = now(player);
        Exchange exchange = exchange(attacker, player);
        if (now - exchange.hitByPlayer > RECIPROCAL_TICKS || now - exchange.lastArmor < SAME_ATTACKER_TICKS) {
            return 0F;
        }
        List<EquipmentSlot> slots = new ArrayList<>(4);
        for (EquipmentSlot slot : Attunement.ARMOR_SLOTS) {
            if (gaining(player, slot, AttunementFamily.Kind.ARMOR)) {
                slots.add(slot);
            }
        }
        if (slots.isEmpty()) {
            return 0F;
        }
        exchange.lastArmor = now;
        float factor = typeFactor(player, exchange, attacker.getType(), now);
        float points = (float) base * multiplier * factor;
        if (capped) {
            points = Math.min(points, ARMOR_CAP_PER_CREATURE * multiplier - exchange.armorGiven);
        }
        float granted = grant(player, slots, AttunementFamily.Kind.ARMOR, points);
        if (granted > 0F) {
            exchange.armorGiven += granted;
            if (capped) {
                saveGiven(attacker, player.getUUID(), exchange);
            }
            countOnce(player, exchange, attacker.getType(), now, factor);
        }
        return granted;
    }

    /**
     * 这一下不是玩家本人亲手打的（见类注释）：反伤、饰品自己打出的伤害、尖刺圆盾的范围伤害。
     * 只在伤害事件发生的当时问（作用域标记是同步的；{@code LivingDeathEvent} 在 hurt 里面发，那时标记还在）。
     */
    public static boolean notOwnHit(DamageSource source) {
        return ECDamageKinds.isReflected(source) || BaubleEffects.isBaubleDamage() || InlayEvents.isNotWeaponHit();
    }

    /**
     * 主手这件打实了 target（{@code source} 的直接来源是玩家本人、不是 {@link #notOwnHit} 那几种才算）。返回记的点数（0 = 没给）。
     */
    public static float weaponHit(ServerPlayer player, LivingEntity target, DamageSource source) {
        if (source.getDirectEntity() != player || notOwnHit(source) || !gaining(player, EquipmentSlot.MAINHAND, AttunementFamily.Kind.WEAPON)) {
            return 0F;
        }
        Engagement engagement = engagement(player, target);
        if (engagement == null) {
            return 0F;
        }
        int base = engagement.pvp ? AttunementConfig.pointsPvp() : AttunementConfig.pointsHit();
        if (base <= 0) {
            return 0F;
        }
        long now = now(player);
        Exchange exchange = exchange(target, player);
        // 横扫、补伤之类同一刻的几下只算一下
        if (exchange.lastWeapon == now) {
            return 0F;
        }
        exchange.lastWeapon = now;
        float factor = typeFactor(player, exchange, target.getType(), now);
        float points = (float) base * engagement.multiplier * factor;
        boolean capped = !engagement.boss || !uncappedBoss(target, player, true);
        if (capped) {
            points = Math.min(points, WEAPON_HIT_CAP_PER_CREATURE * engagement.multiplier - exchange.weaponGiven);
        }
        float granted = grant(player, List.of(EquipmentSlot.MAINHAND), AttunementFamily.Kind.WEAPON, points);
        if (granted > 0F) {
            exchange.weaponGiven += granted;
            if (capped) {
                saveGiven(target, player.getUUID(), exchange);
            }
            countOnce(player, exchange, target.getType(), now, factor);
        }
        return granted;
    }

    /** 主手这件击杀了 victim（不算 {@link #notOwnHit} 那几种）。返回记的点数（0 = 没给）。不受每只生物的上限管 */
    public static float weaponKill(ServerPlayer player, LivingEntity victim, DamageSource source) {
        if (source.getDirectEntity() != player || notOwnHit(source) || !gaining(player, EquipmentSlot.MAINHAND, AttunementFamily.Kind.WEAPON)) {
            return 0F;
        }
        Engagement engagement = engagement(player, victim);
        if (engagement == null) {
            return 0F;
        }
        int base = engagement.pvp ? AttunementConfig.pointsPvp()
                : engagement.boss ? AttunementConfig.pointsBossKill() : AttunementConfig.pointsKill();
        if (base <= 0) {
            return 0F;
        }
        long now = now(player);
        Exchange exchange = exchange(victim, player);
        float factor = typeFactor(player, exchange, victim.getType(), now);
        float points = (float) base * engagement.multiplier * factor;
        float granted = grant(player, List.of(EquipmentSlot.MAINHAND), AttunementFamily.Kind.WEAPON, points);
        if (granted > 0F) {
            countOnce(player, exchange, victim.getType(), now, factor);
        }
        return granted;
    }

    private record Engagement(boolean pvp, boolean boss, int multiplier) {
    }

    /** 武器点对这个目标算不算数：玩家（PvP）、或 Enemy / 首领 + 来源公平 + 没被 setNoAi + 正在和你打 */
    @Nullable
    private static Engagement engagement(ServerPlayer player, LivingEntity target) {
        if (target instanceof Player other) {
            return other != player && ECProtection.isRealPlayer(other) ? new Engagement(true, false, 1) : null;
        }
        boolean boss = isBoss(target);
        if (!boss && !(target instanceof Enemy)) {
            return null;
        }
        if (!fair(target) || target instanceof Mob mob && mob.isNoAi()) {
            return null;
        }
        if (!boss) {
            boolean chasing = target instanceof Mob mob && mob.getTarget() == player;
            boolean hitYou = now(player) - exchange(target, player).hitPlayer <= RECIPROCAL_TICKS;
            if (!chasing && !hitYou) {
                return null;
            }
        }
        return new Engagement(false, boss, multiplier(target));
    }

    public static boolean isBoss(LivingEntity entity) {
        return entity.getType().is(Tags.EntityTypes.BOSSES);
    }

    /**
     * 这只首领（{@link #isBoss} 为真）给 player 的点不受「每只生物」上限（D9 A 的「首领不限」）。上限本来是防「困住一只怪贴身刷」的，
     * 首领不限的前提是首领战有成本、会反击，所以只给这样的首领：
     * <ul>
     *   <li>本模组的小 Boss 档（{@link BossTier#MINI}，聚能魔像这类守着结构的）一律照普通怪的上限：它不散去、吃治疗药水，
     *       护盾抽干以后能一边砍一边喂药把它吊着刷（batch13.md §5.3.1；击杀照给首领的 {@code points_boss_kill}）；</li>
     *   <li>本模组的 Boss 档（召唤来、有成本、不吃状态效果的）不限；</li>
     *   <li>别的首领（原版凋灵、末影龙和别的模组的）认「互相打实」：盔甲点本来就是它打中了你才给，不限；武器点要它 {@value #RECIPROCAL_TICKS} 刻内
     *       打中过你才不限。凋灵笼这类把首领困住、它够不着人的打法只拿普通怪的那 3 点，正常对打时照旧不限。</li>
     * </ul>
     *
     * @param weapon true = 问武器打实点，false = 问盔甲点
     */
    public static boolean uncappedBoss(LivingEntity mob, Player player, boolean weapon) {
        if (mob instanceof ECBoss boss) {
            return boss.bossTier() != BossTier.MINI;
        }
        return !weapon || now(player) - exchange(mob, player).hitPlayer <= RECIPROCAL_TICKS;
    }

    /** 来源公平：不是刷怪笼 / 刷怪蛋来的、不在 {@code growth_excluded} */
    public static boolean fair(LivingEntity entity) {
        return !SpawnOrigin.isArtificial(entity) && !entity.getType().is(ECTags.GROWTH_EXCLUDED);
    }

    /** 这只生物的点数倍数（登记的来源取最大，至少 1） */
    public static int multiplier(LivingEntity entity) {
        int best = 1;
        for (ToIntFunction<LivingEntity> source : MULTIPLIERS) {
            best = Math.max(best, source.applyAsInt(entity));
        }
        return best;
    }

    /** 这个槽上的东西现在还能攒点：同调家族、种类对得上、家族开着、不是最高阶、本阶（算上没写的）还没满 */
    private static boolean gaining(Player player, EquipmentSlot slot, AttunementFamily.Kind kind) {
        ItemStack stack = player.getItemBySlot(slot);
        return room(player, slot, stack, kind) > 0F;
    }

    /** 这件还能放多少点（本阶门槛 − 已写的 − 待写的）；不符合条件是 0 */
    private static float room(Player player, EquipmentSlot slot, ItemStack stack, AttunementFamily.Kind kind) {
        AttunementFamily family = AttunementFamily.of(stack);
        if (family == null || family.kind() != kind || AttunementFamily.slotOf(stack, kind) != slot || !family.active()) {
            return 0F;
        }
        int need = Attunement.need(family, Attunement.stage(stack));
        if (need <= 0) {
            return 0F;
        }
        State state = PLAYERS.get(player);
        Pending pending = state == null ? null : state.pending.get(slot);
        float waiting = pending != null && pending.stack == stack ? pending.points : 0F;
        return need - Attunement.points(stack) - waiting;
    }

    /**
     * 这只生物给的点打几折：已经算进同种计数的，按那时定下的折扣；还没算的，看这一种最近一小时（在线时间）已经给过几只（满 30 只减半）。
     */
    private static float typeFactor(Player player, Exchange exchange, EntityType<?> type, long now) {
        if (exchange.counted) {
            return exchange.factor;
        }
        return typeCount(player, type) >= FULL_PER_TYPE_PER_HOUR ? TYPE_FATIGUE : 1F;
    }

    /** 这只生物第一次真给了点：算进同种计数（一只只算一次），记下它的折扣 */
    private static void countOnce(Player player, Exchange exchange, EntityType<?> type, long now, float factor) {
        if (exchange.counted) {
            return;
        }
        exchange.counted = true;
        exchange.factor = factor;
        IPlayerData data = PlayerDataEvents.get(player);
        if (data != null) {
            MobKillLedger.addType(data, type);
        }
    }

    /** 这名玩家最近一小时在线时间里（台账的 12 个小桶）从这一种实体身上拿过点的只数 */
    public static int typeCount(Player player, EntityType<?> type) {
        IPlayerData data = PlayerDataEvents.get(player);
        return data == null ? 0 : MobKillLedger.typeCount(data, type);
    }

    /**
     * 从预算里扣一次、给每个槽记上：给的点数 = min(要给的, 剩下的预算, 最空的那件还能放多少)；每件按自己还能放多少夹。返回实际扣的预算。
     */
    private static float grant(Player player, List<EquipmentSlot> slots, AttunementFamily.Kind kind, float points) {
        if (!(points > 0F)) {
            return 0F;
        }
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            return 0F;
        }
        IPlayerData.BudgetKind budgetKind = kind.budget();
        float budget = data.getAttunementBudget(budgetKind);
        float[] rooms = new float[slots.size()];
        float mostRoom = 0F;
        for (int i = 0; i < rooms.length; i++) {
            rooms[i] = room(player, slots.get(i), player.getItemBySlot(slots.get(i)), kind);
            mostRoom = Math.max(mostRoom, rooms[i]);
        }
        // 快满的件只差零点几时，不为它多扣预算
        float granted = Math.min(Math.min(points, budget), mostRoom);
        if (!(granted > 0F)) {
            return 0F;
        }
        data.setAttunementBudget(budgetKind, budget - granted, true);
        State state = state(player);
        for (int i = 0; i < rooms.length; i++) {
            EquipmentSlot slot = slots.get(i);
            ItemStack stack = player.getItemBySlot(slot);
            float room = rooms[i];
            if (room <= 0F) {
                continue;
            }
            Pending pending = state.pending.get(slot);
            if (pending == null || pending.stack != stack) {
                pending = new Pending(stack);
                state.pending.put(slot, pending);
            }
            pending.points += Math.min(granted, room);
        }
        return granted;
    }

    // ------------------------------------------------------------------
    // 写盘
    // ------------------------------------------------------------------

    /** 还没写进物品的点数（测试、提示用）；这个槽没有记录、或记录的不是现在这件时为 0 */
    public static float pending(Player player, EquipmentSlot slot) {
        State state = PLAYERS.get(player);
        Pending pending = state == null ? null : state.pending.get(slot);
        return pending != null && pending.stack == player.getItemBySlot(slot) ? pending.points : 0F;
    }

    /**
     * 把待写的整数点写进还在原槽位上的那件物品（小数部分留着下次），离开了槽位的丢掉；顺手把身上同调装备的阶戳抬到现在的矩阵阶；
     * 宅男四件都到 2 阶时补手册步骤。由 {@code player/AttunementBudget} 每 40 刻调，下线、死亡、换维度时也调。
     */
    public static void flush(Player player) {
        if (player.level().isClientSide) {
            return;
        }
        State state = PLAYERS.get(player);
        int rank = MatrixRanks.get(player);
        for (EquipmentSlot slot : GEAR_SLOTS) {
            ItemStack current = player.getItemBySlot(slot);
            if (state != null) {
                Pending pending = state.pending.get(slot);
                if (pending != null) {
                    if (pending.stack != current || current.isEmpty()) {
                        state.pending.remove(slot);
                    } else {
                        int whole = (int) pending.points;
                        if (whole > 0) {
                            Attunement.addPoints(current, whole);
                            pending.points -= whole;
                        }
                    }
                }
            }
            AttunementFamily family = AttunementFamily.of(current);
            if (family != null && AttunementFamily.slotOf(current, family.kind()) == slot
                    && (slot == EquipmentSlot.MAINHAND) == (family.kind() == AttunementFamily.Kind.WEAPON)) {
                Attunement.stampRank(current, rank);
            }
        }
        if (player instanceof ServerPlayer serverPlayer && ECProtection.isRealPlayer(serverPlayer)) {
            checkTerminalStep(serverPlayer);
        }
    }

    /** 宅男四件都穿着、最低一件到了 2 阶：补手册第二十一章的步骤 {@code guide/terminal/attuned}（进度不存在时什么都不做） */
    private static void checkTerminalStep(ServerPlayer player) {
        AttunementFamily computer = AttunementFamily.byId(EssentialCraft5.id("computer_armor"));
        if (computer != null && Attunement.fullSetStage(player, computer) >= GUIDE_TERMINAL_STAGE) {
            CorruptionTicker.award(player, GUIDE_TERMINAL_ATTUNED);
        }
    }

    /** 丢掉这个槽上没写的点（指令直接改了这件时用） */
    public static void discardPending(Player player, EquipmentSlot slot) {
        State state = PLAYERS.get(player);
        if (state != null) {
            state.pending.remove(slot);
        }
    }

    /** 测试：忘掉这名玩家对象上的待写点数（同种计数在玩家数据里，不跟着走，见 {@link #forgetTypeCounts}） */
    public static void forget(Player player) {
        PLAYERS.remove(player);
    }

    /** 测试：清掉这名玩家的同种计数 */
    public static void forgetTypeCounts(Player player) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data != null) {
            MobKillLedger.clearTypes(data);
        }
    }

    /** 测试：忘掉这只生物在内存里的往来记录（持久数据里的一生累计还在，下次照样读回来） */
    public static void forget(LivingEntity mob) {
        MOBS.remove(mob);
    }

    // ------------------------------------------------------------------

    private static Exchange exchange(LivingEntity mob, Player player) {
        return MOBS.computeIfAbsent(mob, m -> new HashMap<>(2)).computeIfAbsent(player.getUUID(), id -> loadExchange(mob, id));
    }

    /** 新建一份往来记录：一生累计从生物的持久数据读回（区块重新加载、换维度后的新实体对象也接得上） */
    private static Exchange loadExchange(LivingEntity mob, UUID player) {
        Exchange exchange = new Exchange();
        if (mob instanceof Player) {
            return exchange;
        }
        CompoundTag data = mob.getPersistentData();
        if (data.contains(KEY_GIVEN, Tag.TAG_COMPOUND)) {
            CompoundTag all = data.getCompound(KEY_GIVEN);
            String key = player.toString();
            if (all.contains(key, Tag.TAG_COMPOUND)) {
                CompoundTag given = all.getCompound(key);
                exchange.armorGiven = given.getFloat(KEY_ARMOR);
                exchange.weaponGiven = given.getFloat(KEY_WEAPON);
            }
        }
        return exchange;
    }

    /** 把这名玩家从这只生物身上拿的一生累计写进它的持久数据（随实体存盘；条目数受打过它的玩家数限制）。玩家不写 */
    private static void saveGiven(LivingEntity mob, UUID player, Exchange exchange) {
        if (mob instanceof Player) {
            return;
        }
        CompoundTag data = mob.getPersistentData();
        CompoundTag all = data.getCompound(KEY_GIVEN);
        CompoundTag given = new CompoundTag();
        given.putFloat(KEY_ARMOR, exchange.armorGiven);
        given.putFloat(KEY_WEAPON, exchange.weaponGiven);
        all.put(player.toString(), given);
        data.put(KEY_GIVEN, all);
    }

    private static State state(Player player) {
        return PLAYERS.computeIfAbsent(player, p -> new State());
    }

    private static long now(Entity entity) {
        if (testNow >= 0L) {
            return testNow;
        }
        MinecraftServer server = entity.getServer();
        return server != null ? server.getTickCount() : entity.level().getGameTime();
    }
}
