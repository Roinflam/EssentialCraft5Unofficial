package com.flameforge.essentialcraft5.item.bauble;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.gun.RayHitGate;
import com.flameforge.essentialcraft5.item.attunement.GearPool;
import com.flameforge.essentialcraft5.registry.ECBaubles;
import com.flameforge.essentialcraft5.util.ECHurtWindow;
import com.flameforge.essentialcraft5.util.ECShieldBlocks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 战斗类饰品（原作 ECEventHandler.damageEvent / attackTargetEvent / enderTeleport）：
 * <ul>
 *   <li>ζ 护身符：你打中目标时扣 300 UBMRU、清掉目标的无敌帧（点多快打多快）。</li>
 *   <li>η 护身符：你造成的和你受到的伤害都 ×2。</li>
 *   <li>θ 腰带：受到致命伤时扣「伤害 × 5000」UBMRU 抵掉这一下，之后冷却 {@code bauble_limits.theta_cooldown_ticks}（默认 30 秒）。</li>
 *   <li>ι 指环：你打出的伤害 10% 拆出来，改成本 tick 末的一下无视护甲的魔法伤害，打完把目标的无敌窗口放回原样（不清无敌帧，见 {@link #flushIota}）。</li>
 *   <li>σ 指环：受到有来源的伤害时 10% 原样反给来源（本 tick 末，原来那一下真打到自己才反）、自己免伤。</li>
 *   <li>ψ 指环：怪物盯上你时改去打附近别的敌对生物。</li>
 *   <li>υ 指环：离末影人最近（16 格内）的是你时，它不能瞬移。</li>
 * </ul>
 * 伤害源一律带佩戴者（原作 causePlayerDamage），PvP 开关、队伍、领地插件照常管得到。ν、ι、σ 自己打出的伤害不再进这里（{@link BaubleEffects#asBaubleDamage}；
 * σ 的反弹另外套一层 {@link BaubleEffects#asReflectDamage}，交手记录和削减模块靠它认出这是反伤）。
 *
 * <h2>和原作不同的地方</h2>
 * <ul>
 *   <li>ζ：原作还调了 resetCooldown，那在 1.12 是「开始冷却」（把计时清零），近战时本来就刚清过、没用，射箭时反而让下一刀变弱，去掉。</li>
 *   <li>θ：原作在护甲之前按原始伤害判断、按原始伤害扣费（护甲本来挡得住的也会触发）；这里改在 LivingDamageEvent（护甲、吸收之后）按真正要扣的血算。
 *       虚空和 /kill（bypasses_invulnerability）不再抵挡，免得在虚空里一直被吊着扣光 UBMRU。</li>
 *   <li>ι：原作判断「目标无敌帧 ≤ 0」，而 1.12 在发 LivingHurtEvent 之前已经把无敌帧设成 20，这个效果从来没触发过；这里照说明文字做出来。
 *       魔法伤害放到本 tick 末结算（在伤害事件里对同一个目标再 hurt 一次会打乱原版的无敌帧和死亡流程），这一下不再击退，打完把目标的无敌窗口原样放回（{@link #flushIota}）。</li>
 *   <li>ψ（policy §4.2）：原作只给怪设了「复仇目标」、随便挑周围一只活物（村民、宠物、别人的马都算），1.20 改用 LivingChangeTargetEvent 直接换目标；
 *       新目标只能是敌对生物，同一只怪 {@code bauble_limits.psi_cooldown_ticks}（默认 40）tick 内只查一次周围、沿用上次换的目标。
 *       首领（{@code #forge:bosses}）默认不换（{@code bauble_limits.psi_affects_bosses}）：会召小怪的首领（全息投影的克隆体、别的模组的首领）
 *       会掉头去打自己的小怪，整场战斗就不用打了。</li>
 * </ul>
 *
 * <h2>攻击被拦下时不留后果（第十批）</h2>
 * Mohist 系混合端把 Bukkit 的 {@code EntityDamageByEntityEvent} 发在 {@code LivingHurtEvent} 和 {@code LivingDamageEvent} 之间
 * （混合端的 {@code LivingEntity.damageEntity0}），PvP / 领地插件取消攻击时 {@code LivingDamageEvent} 不发。所以：
 * <ul>
 *   <li>ζ 的扣费和清无敌帧挪到 {@code LivingDamageEvent}（攻击确实打实了才算）。另外，混合端的 {@code LivingEntity.hurt} 在 {@code actuallyHurt}
 *       <b>之后</b>才把无敌帧设成 20（原版 / Forge 在之前），伤害事件里清零会被立刻盖掉，所以同时记下目标、本 tick 末再清一次（{@link #flushZeta}）。</li>
 *   <li>ι 在 {@code LivingHurtEvent} 拆出 10%（要在护甲之前），但排队的魔法伤害要等这一下的 {@code LivingDamageEvent} 确认过才打（{@link #flushIota}）；
 *       没确认的（被拦下、被取消、无敌）丢掉。</li>
 *   <li>原来那一下的 {@code LivingHurtEvent} 最后被取消了的，ζ、ι 都不算（同下面 σ 的写法：记下事件对象，结算时看 {@code isCanceled()}）。
 *       纯 Forge 取消以后不再发 {@code LivingDamageEvent}，自然不算；混合端上别的模组排在 ζ / ι 后面取消 {@code LivingHurtEvent} 时，
 *       伤害按 0 照样往下走、Bukkit 事件放行后照发 {@code LivingDamageEvent(0)}，只看「伤害事件来过」就会照扣照清、照补魔法伤害。
 *       ζ 因此也在 {@code LivingHurtEvent} 里先记下这一下（{@link #claimZeta}）：没有记录（在 ζ 之前就被取消）或记录的事件被取消了，都不扣费、不清无敌帧。
 *       伤害最后被改成 0（没取消）的同样不算（{@code RayHitGate.hurtZeroed}）：纯 Forge 在 {@code actuallyHurt} 的 {@code <= 0} 处返回、不发
 *       {@code LivingDamageEvent}，混合端照发 {@code LivingDamageEvent(0)}。本类自己就有这种情况：σ 在同一个处理器里把受到的伤害改成 0，
 *       不排除的话，混合端上戴 ζ + ι 的人打 σ 生效的人，ζ 照扣照清、ι 照补魔法伤害（σ 的免伤被打穿）。</li>
 *   <li>η 的翻倍只改这一下的数值，被拦下就一起没了。</li>
 *   <li>σ（第十批权限修补 A5）：自己免伤照旧当场生效，反弹改到本 tick 末、而且只反「原来那一下真的落到佩戴者身上」的（{@link #flushSigma}）。
 *       以前在 {@code LivingHurtEvent} 里当场反，早于插件对原伤害的判断：按受害者位置判 PvP 的领地拦下了原伤害，攻击者照样挨反弹。
 *       反弹本身是一次记在佩戴者头上的伤害，照样过 PvP / 领地的门。「真的落下」认两种信号，哪种先到都算：
 *       纯 Forge 上 σ 把这一下改成 0 以后到不了 {@code LivingDamageEvent}（{@code Player.actuallyHurt} 里直接返回），但 {@code hurt} 照样把它记成
 *       佩戴者的「最近一次伤害」；Mohist 系混合端正相反——改成 0 的伤害在 Bukkit 事件放行后照样发 {@code LivingDamageEvent(0)}，
 *       可 {@code damageEntity0} 对「原始伤害为 0」返回 false，{@code hurt} 提前返回、不记最近一次伤害。插件拦下时两种信号都没有。
 *       所以没用 {@code MRUShield.defer}（只等 {@code LivingDamageEvent}，纯 Forge 上永远不反），也不能只看最近一次伤害（混合端上永远不反）。
 *       另外，原来那一下的 {@code LivingHurtEvent} 最后被取消了的也不反（别的模组排在 σ 后面取消原伤害时，两种信号照样会出现：
 *       纯 Forge 的 {@code hurt} 记最近一次伤害只看传进来的原始伤害，混合端取消以后按 0 照样往下走），所以记下事件对象、结算时看它（同 {@code gun/RayHitGate}）。</li>
 * </ul>
 * <h2>举盾挡下的不算打实（第十批）</h2>
 * 纯 Forge 在 {@code LivingHurtEvent} 之前就扣掉盾牌挡的部分，挡满时上面几件都不会触发；混合端在 {@code LivingHurtEvent} 之后才算举盾，
 * 挡满以后照样发 {@code LivingDamageEvent(0)}。所以 {@link #onBaubleDamageLanded} 先问 {@link ECShieldBlocks#fullyShielded}：整下被盾挡掉的，
 * ι 不确认、σ 不算落下、ζ 不扣费也不清无敌帧，两种环境一样。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class BaubleCombatEvents {

    /** ι：拆出来的比例 */
    public static final float IOTA_FRACTION = 0.1F;
    /** σ：反弹几率 */
    public static final float SIGMA_CHANCE = 0.1F;
    /** υ：多远以内的最近玩家 */
    public static final double UPSILON_RANGE = 16D;
    /** ι 一个 tick 最多排几下魔法伤害（超出的这一下不拆，照原样打） */
    public static final int MAX_PENDING = 256;

    /**
     * ι 排队的魔法伤害：source 按对象比、tick 是拆的那一刻；hurtEvent 是拆的那一下的 LivingHurtEvent（结算时看它最后有没有被取消）；
     * confirmed = 这一下的 LivingDamageEvent 来过
     */
    private static final class PendingMagic {
        final LivingEntity target;
        final Player attacker;
        final float amount;
        final DamageSource source;
        final long tick;
        final LivingHurtEvent hurtEvent;
        boolean confirmed;

        PendingMagic(LivingEntity target, Player attacker, float amount, DamageSource source, long tick, LivingHurtEvent hurtEvent) {
            this.target = target;
            this.attacker = attacker;
            this.amount = amount;
            this.source = source;
            this.tick = tick;
            this.hurtEvent = hurtEvent;
        }
    }

    /** ζ 等着 LivingDamageEvent 认领的一下：目标、伤害源（按对象比）、那一刻、这一下的 LivingHurtEvent（认领时看它最后有没有被取消） */
    private record PendingZeta(LivingEntity target, DamageSource source, long tick, LivingHurtEvent hurtEvent) {
    }

    private record Redirect(long time, @Nullable WeakReference<LivingEntity> target) {
    }

    /**
     * σ 等着结算的反弹：佩戴者、攻击者、原来那一下的伤害源（按对象比）、反多少、原来那一下的 LivingHurtEvent（结算时看它最后有没有被取消）；
     * landed = 这一下的 LivingDamageEvent 来过、而且不是被盾牌整下挡掉的（混合端的信号）
     */
    private static final class PendingSigma {
        final Player wearer;
        final Entity attacker;
        final DamageSource source;
        final float amount;
        final LivingHurtEvent hurtEvent;
        boolean landed;

        PendingSigma(Player wearer, Entity attacker, DamageSource source, float amount, LivingHurtEvent hurtEvent) {
            this.wearer = wearer;
            this.attacker = attacker;
            this.source = source;
            this.amount = amount;
            this.hurtEvent = hurtEvent;
        }
    }

    private static final List<PendingMagic> PENDING = new ArrayList<>();
    private static final Map<LivingEntity, Redirect> REDIRECTS = new WeakHashMap<>();
    /** ζ 本 tick 末要再清一次无敌帧的目标（混合端，见类注释） */
    private static final List<LivingEntity> ZETA_CLEAR = new ArrayList<>();
    /** ζ 在 LivingHurtEvent 里记下、等 LivingDamageEvent 认领的（见 {@link #claimZeta}）；只留当前这一刻的 */
    private static final List<PendingZeta> ZETA_HITS = new ArrayList<>();
    /** σ 本 tick 末要核对、反弹的（见 {@link #flushSigma}） */
    private static final List<PendingSigma> SIGMA = new ArrayList<>();
    /** GameTest 用：σ 的掷骰（null = 真随机） */
    @Nullable
    private static volatile Float testSigmaRoll;

    private BaubleCombatEvents() {
    }

    // ---------------------------------------------------------------- ζ η ι σ

    @SubscribeEvent
    public static void onBaubleLivingHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide || BaubleEffects.isBaubleDamage()) {
            return;
        }
        DamageSource source = event.getSource();
        // 打出去（原作先判断攻击者）：η 翻倍 → ι 拆 10%；ζ 只在这里记下这一下，扣费和清无敌帧在 LivingDamageEvent（见类注释）
        if (source.getEntity() instanceof Player attacker && attacker != target && SpecialBaubleItem.isWearer(attacker)) {
            if (BaubleEffects.has(attacker, ECBaubles.ETA_AMULET.get())) {
                event.setAmount(event.getAmount() * 2F);
            }
            if (BaubleEffects.has(attacker, ECBaubles.IOTA_RING.get())) {
                splitIota(event, attacker, target);
            }
            if (BaubleEffects.has(attacker, ECBaubles.ZETA_AMULET.get())) {
                noteZeta(event, target);
            }
        }
        // 受到（原作再判断受害者）：η 翻倍 → σ 反弹
        if (target instanceof Player player && SpecialBaubleItem.isWearer(player)) {
            if (BaubleEffects.has(player, ECBaubles.ETA_AMULET.get())) {
                event.setAmount(event.getAmount() * 2F);
            }
            if (BaubleEffects.has(player, ECBaubles.SIGMA_RING.get())) {
                reflectSigma(event, player);
            }
        }
    }

    private static void splitIota(LivingHurtEvent event, Player attacker, LivingEntity target) {
        // 裁断 / 终审 / 范围技是数定好、有封顶的补伤：拆出去的那份换了伤害源，封顶夹不到它，所以不拆
        if (GearPool.skipsOffense(event.getSource())) {
            return;
        }
        float amount = event.getAmount();
        float part = amount * IOTA_FRACTION;
        if (part <= 0F || PENDING.size() >= MAX_PENDING) {
            return;
        }
        event.setAmount(amount - part);
        PENDING.add(new PendingMagic(target, attacker, part, event.getSource(), target.level().getGameTime(), event));
    }

    /** ζ：记下这一下（目标、伤害源对象、这一刻、事件对象），等 {@link #onBaubleDamageLanded} 认领；不是这一刻的旧记录顺手清掉 */
    private static void noteZeta(LivingHurtEvent event, LivingEntity target) {
        long now = target.level().getGameTime();
        if (!ZETA_HITS.isEmpty()) {
            ZETA_HITS.removeIf(pending -> pending.tick() != now);
        }
        if (ZETA_HITS.size() < MAX_PENDING) {
            ZETA_HITS.add(new PendingZeta(target, event.getSource(), now, event));
        }
    }

    /**
     * ζ：认领这一下在 LivingHurtEvent 里的记录（同一个目标、同一个伤害源对象、同一刻；认领了就删掉）。
     * 没有记录（LivingHurtEvent 在 ζ 之前就被取消、没走到这里）、记录的事件最后被取消了、或者伤害最后被改成 0（例如对方的 σ 免伤），
     * 都不算打实（{@link RayHitGate#hurtZeroed}；纯 Forge 这几种都到不了 LivingDamageEvent，混合端照发 {@code LivingDamageEvent(0)}）。
     */
    private static boolean claimZeta(LivingEntity target, DamageSource source) {
        long now = target.level().getGameTime();
        for (int i = ZETA_HITS.size() - 1; i >= 0; i--) {
            PendingZeta pending = ZETA_HITS.get(i);
            if (pending.target() == target && pending.source() == source && pending.tick() == now) {
                ZETA_HITS.remove(i);
                return !RayHitGate.hurtZeroed(pending.hurtEvent());
            }
        }
        return false;
    }

    /**
     * 攻击打实了（{@code LivingDamageEvent}，混合端在 Bukkit 伤害事件之后）：确认 ι 排的那一下、σ 记成落下；ζ 认领到这一下没被取消的记录（{@link #claimZeta}）才扣费、清无敌帧。
     * 最低优先级、不收已取消的：别的模组在这里取消的也不算打实。被盾牌整下挡掉的（混合端挡满照样发 {@code LivingDamageEvent(0)}）也不算，见类注释。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBaubleDamageLanded(LivingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide || BaubleEffects.isBaubleDamage()) {
            return;
        }
        DamageSource source = event.getSource();
        if (ECShieldBlocks.fullyShielded(target, source, event.getAmount())) {
            return;
        }
        if (!PENDING.isEmpty()) {
            long now = target.level().getGameTime();
            for (PendingMagic pending : PENDING) {
                if (pending.target == target && pending.source == source && pending.tick == now) {
                    pending.confirmed = true;
                }
            }
        }
        if (!SIGMA.isEmpty()) {
            for (PendingSigma pending : SIGMA) {
                if (pending.wearer == target && pending.source == source) {
                    pending.landed = true;
                }
            }
        }
        if (source.getEntity() instanceof Player attacker && attacker != target && SpecialBaubleItem.isWearer(attacker)
                && BaubleEffects.has(attacker, ECBaubles.ZETA_AMULET.get()) && claimZeta(target, source)
                && BaubleEffects.spendUBMRU(attacker, BaubleEffects.ZETA_COST)) {
            // 原版 / Forge：无敌帧在 actuallyHurt 之前已经设成 20，这里清零就生效；混合端在之后才设，本 tick 末再清一次
            target.invulnerableTime = 0;
            if (ZETA_CLEAR.size() < MAX_PENDING) {
                ZETA_CLEAR.add(target);
            }
        }
    }

    /**
     * σ：这一下自己免伤（当场把伤害改成 0，护甲、吸收、MRU 盔甲都不再算），反弹记下来，等这一下真的落到自己身上再在本 tick 末打回去（{@link #flushSigma}）。
     * 伤害是 0 的不反（混合端上 0 伤害也会走完整条伤害流程）。
     */
    private static void reflectSigma(LivingHurtEvent event, Player player) {
        Entity attacker = event.getSource().getEntity();
        float amount = event.getAmount();
        if (attacker == null || attacker == player || !attacker.isAlive() || !(amount > 0F)) {
            return;
        }
        Float forced = testSigmaRoll;
        if ((forced != null ? forced : player.getRandom().nextFloat()) >= SIGMA_CHANCE) {
            return;
        }
        event.setAmount(0F);
        if (SIGMA.size() < MAX_PENDING) {
            SIGMA.add(new PendingSigma(player, attacker, event.getSource(), amount, event));
        }
    }

    /**
     * 结算 σ 的反弹（第十批权限修补 A5）：只反「原来那一下真的落到佩戴者身上」的。两种信号任一个就算（见类注释）：
     * 这一下的 {@code LivingDamageEvent} 来过（混合端：Bukkit 事件放行后照样发，伤害是 0 也发；被盾牌整下挡掉的不算），或者 {@code hurt} 把它记成了佩戴者的
     * 「最近一次伤害」（纯 Forge：{@code getLastDamageSource}，按对象比）。插件拦下原伤害时两样都没有；别的模组在 LivingAttackEvent 里取消的到不了这里，
     * 所以按受害者位置判 PvP 的领地拦下了原伤害，攻击者也不会再挨反弹。两种信号都认，不按环境分支。
     * 原来那一下的 {@code LivingHurtEvent} 最后被取消了的（不管哪个模组、哪个优先级取消）先排除：两种信号这时都可能照样出现。
     * 反弹本身是一次记在佩戴者头上的 {@code playerAttack}，照样过 PvP / 队伍 / 领地的门；纯 Forge 上同一 tick 里佩戴者又挨了别的伤害（最近一次伤害换了）时这一下不反（只会少反、不会多反）。
     *
     * @return 反中了几下（GameTest 直接调）
     */
    public static int flushSigma() {
        if (SIGMA.isEmpty()) {
            return 0;
        }
        List<PendingSigma> batch = new ArrayList<>(SIGMA);
        SIGMA.clear();
        int dealt = 0;
        for (PendingSigma pending : batch) {
            if (pending.hurtEvent.isCanceled()) {
                continue;
            }
            Player wearer = pending.wearer;
            Entity attacker = pending.attacker;
            boolean landed = pending.landed || wearer.getLastDamageSource() == pending.source;
            if (wearer.isRemoved() || !attacker.isAlive() || attacker.isRemoved() || !landed) {
                continue;
            }
            boolean hurt = BaubleEffects.asReflectDamage(() -> attacker.hurt(wearer.damageSources().playerAttack(wearer), pending.amount));
            if (hurt) {
                dealt++;
            }
        }
        return dealt;
    }

    /** GameTest 用：σ 的掷骰固定成 roll（小于 {@link #SIGMA_CHANCE} 一定反弹；null 恢复真随机），只在同一段同步代码里设、测、还原 */
    public static void setSigmaRollForTest(@Nullable Float roll) {
        testSigmaRoll = roll;
    }

    /** ζ 的补清、ι 的魔法伤害、σ 的反弹：每个服务端 tick 末结算 */
    @SubscribeEvent
    public static void onBaubleServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!ZETA_CLEAR.isEmpty()) {
            flushZeta();
        }
        if (!PENDING.isEmpty()) {
            flushIota();
        }
        if (!SIGMA.isEmpty()) {
            flushSigma();
        }
        if (!ZETA_HITS.isEmpty()) {
            ZETA_HITS.clear();   // 没等到 LivingDamageEvent 的（被拦下、被取消、无敌）
        }
    }

    @SubscribeEvent
    public static void onBaubleServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
        REDIRECTS.clear();
        ZETA_CLEAR.clear();
        ZETA_HITS.clear();
        SIGMA.clear();
    }

    /**
     * ζ 的补清：本 tick 被戴 ζ 的人打实过的目标，无敌帧再清一次零（混合端在伤害事件之后才设无敌帧，见类注释）。
     *
     * @return 清了几个（GameTest 直接调）
     */
    public static int flushZeta() {
        int cleared = 0;
        for (LivingEntity target : ZETA_CLEAR) {
            if (target.isAlive() && !target.isRemoved()) {
                target.invulnerableTime = 0;
                cleared++;
            }
        }
        ZETA_CLEAR.clear();
        return cleared;
    }

    /**
     * 结算排队的 ι 魔法伤害（无视护甲，记在佩戴者头上）：只打 {@code LivingDamageEvent} 确认过、拆的那一下的 {@code LivingHurtEvent} 最后既没被取消、
     * 伤害也没被改成 0 的（攻击确实打实了，{@link RayHitGate#hurtZeroed}；对方的 σ 免掉的那一下不补，免伤不会被打穿）；
     * 记下目标的无敌窗口 → 清掉无敌帧 → 打 → 恢复打之前的速度（不再击退）→ 无敌窗口原样放回（{@code util/ECHurtWindow}）。
     * 以前打完把无敌帧清 0：戴 ι 的人用任何武器连点（每下 0.2 倍伤害），下一刻都能整下打实，约是满蓄力输出的 2.5 倍；
     * 放回以后目标等于只挨了原来那一下，没蓄满的一下照原版被无敌窗口挡掉。ζ 清过的（同一刻先结算 {@link #flushZeta}）记下的就是 0，照样是 0。
     *
     * @return 打中了几下（GameTest 直接调）
     */
    public static int flushIota() {
        List<PendingMagic> batch = new ArrayList<>(PENDING);
        PENDING.clear();
        int dealt = 0;
        for (PendingMagic pending : batch) {
            LivingEntity target = pending.target;
            if (!pending.confirmed || RayHitGate.hurtZeroed(pending.hurtEvent) || !target.isAlive() || target.isRemoved()) {
                continue;
            }
            Vec3 motion = target.getDeltaMovement();
            ECHurtWindow window = ECHurtWindow.of(target);
            target.invulnerableTime = 0;
            boolean hurt = BaubleEffects.asBaubleDamage(() ->
                    target.hurt(target.damageSources().indirectMagic(pending.attacker, pending.attacker), pending.amount));
            target.setDeltaMovement(motion);
            window.restore(target);
            if (hurt) {
                dealt++;
            }
        }
        return dealt;
    }

    // ---------------------------------------------------------------- θ

    /** θ 冷却的到期刻（{@code gameTime}）记在玩家持久数据里的键：原版物品冷却不存盘，退出重进就清零 */
    public static final String THETA_READY_KEY = "essentialcraft5:theta_ready";

    /**
     * θ：致命的一下扣 UBMRU 抵掉。第十批饰品平衡加了冷却（{@code bauble_limits.theta_cooldown_ticks}，默认 600 tick）：原作没有冷却，
     * UBMRU 攒得多的人（刷怪塔里攒上几百万）等于不死，PvP 里打不死、首领战里站着不动。冷却用原版的物品冷却
     * （{@code ItemCooldowns}，会同步给本人，饰品栏里那条腰带上显示冷却遮罩），冷却中这件不生效、也不扣 UBMRU。
     * 物品冷却不进玩家存档，抵掉一次以后退出重进就能绕过冷却，所以另把到期刻记进玩家持久数据（{@link #THETA_READY_KEY}，
     * {@code gameTime} 各维度共用、/time 改不了），两样有一样还在冷却就算冷却中；重进时把剩下的刻数补回物品冷却（{@link #onBaubleThetaLogin}），
     * 饰品栏照样画遮罩。死亡重生不保留（这时 θ 本来就没起作用）。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBaubleLivingDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide
                || !BaubleEffects.has(player, ECBaubles.THETA_BELT.get())) {
            return;
        }
        float amount = event.getAmount();
        if (amount <= 0F || player.getHealth() - amount > 0F || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        Item theta = ECBaubles.THETA_BELT.get();
        if (thetaCoolingDown(player)) {
            return;
        }
        IPlayerData data = BaubleEffects.data(player);
        double cost = (double) amount * BaubleEffects.THETA_COST_PER_DAMAGE;
        if (data != null && data.getUBMRU() >= cost) {
            data.setUBMRU((int) Math.max(0D, data.getUBMRU() - cost));
            event.setAmount(0F);
            int cooldown = ECBaubles.Limits.thetaCooldown();
            if (cooldown > 0) {
                player.getCooldowns().addCooldown(theta, cooldown);
                player.getPersistentData().putLong(THETA_READY_KEY, player.level().getGameTime() + cooldown);
            }
        }
    }

    /** θ 还在冷却：物品冷却还在，或者持久数据里的到期刻还没到（重进以后物品冷却清了也算） */
    public static boolean thetaCoolingDown(Player player) {
        return player.getCooldowns().isOnCooldown(ECBaubles.THETA_BELT.get()) || thetaRemainingTicks(player) > 0L;
    }

    /** θ 持久数据里的冷却还剩几刻（没有记录、已经到期时 0） */
    public static long thetaRemainingTicks(Player player) {
        CompoundTag tag = player.getPersistentData();
        if (!tag.contains(THETA_READY_KEY)) {
            return 0L;
        }
        return Math.max(0L, tag.getLong(THETA_READY_KEY) - player.level().getGameTime());
    }

    /** 重进时 θ 还在冷却：把剩下的刻数补回物品冷却（饰品栏的冷却遮罩靠它画）；已经到期的记录顺手删掉 */
    @SubscribeEvent
    public static void onBaubleThetaLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || !player.getPersistentData().contains(THETA_READY_KEY)) {
            return;
        }
        long remaining = thetaRemainingTicks(player);
        if (remaining > 0L) {
            player.getCooldowns().addCooldown(ECBaubles.THETA_BELT.get(), (int) Math.min(Integer.MAX_VALUE, remaining));
        } else {
            player.getPersistentData().remove(THETA_READY_KEY);
        }
    }

    // ---------------------------------------------------------------- ψ

    @SubscribeEvent
    public static void onBaubleChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity mob = event.getEntity();
        if (!(event.getNewTarget() instanceof Player player) || !(mob instanceof Mob) || mob.level().isClientSide
                || !BaubleEffects.has(player, ECBaubles.PSI_RING.get())) {
            return;
        }
        if (BaubleEffects.isBoss(mob) && !ECBaubles.Limits.psiAffectsBosses()) {
            return;
        }
        LivingEntity redirected = redirectPsi(mob, player);
        if (redirected != null) {
            event.setNewTarget(redirected);
        }
    }

    /**
     * ψ：给盯上 player 的 mob 找一个新目标。同一只怪冷却内不再查周围，只沿用上次换的目标（还有效的话）。
     *
     * @return 新目标；没有合适的返回 null（照旧打玩家）
     */
    @Nullable
    public static LivingEntity redirectPsi(LivingEntity mob, Player player) {
        long now = mob.level().getGameTime();
        Redirect last = REDIRECTS.get(mob);
        if (last != null && now >= last.time() && now - last.time() < ECBaubles.Limits.psiCooldown()) {
            LivingEntity previous = last.target() == null ? null : last.target().get();
            return previous != null && validPsiTarget(mob, player, previous) ? previous : null;
        }
        // 原作：怪的位置 ±0.5 再 grow(6, 3, 6)，去掉自己和原目标，随机挑一只
        AABB box = new AABB(mob.getX() - 0.5D, mob.getY() - 0.5D, mob.getZ() - 0.5D, mob.getX() + 0.5D, mob.getY() + 0.5D, mob.getZ() + 0.5D)
                .inflate(6D, 3D, 6D);
        List<LivingEntity> candidates = mob.level().getEntitiesOfClass(LivingEntity.class, box, other -> validPsiTarget(mob, player, other));
        LivingEntity chosen = candidates.isEmpty() ? null : candidates.get(mob.getRandom().nextInt(candidates.size()));
        REDIRECTS.put(mob, new Redirect(now, chosen == null ? null : new WeakReference<>(chosen)));
        return chosen;
    }

    /** 新目标：活着的敌对生物，不是怪自己、不是玩家，怪能攻击它 */
    private static boolean validPsiTarget(LivingEntity mob, Player player, LivingEntity other) {
        return other != mob && other != player && !(other instanceof Player) && other instanceof Enemy && other.isAlive()
                && other.level() == mob.level() && mob.canAttack(other);
    }

    // ---------------------------------------------------------------- υ

    @SubscribeEvent
    public static void onBaubleEnderTeleport(EntityTeleportEvent.EnderEntity event) {
        if (event.getEntityLiving() instanceof EnderMan enderMan && !enderMan.level().isClientSide && blocksEnderTeleport(enderMan)) {
            event.setCanceled(true);
        }
    }

    /** υ：离这只末影人最近、16 格以内的玩家戴着 υ 指环 */
    public static boolean blocksEnderTeleport(EnderMan enderMan) {
        Player nearest = enderMan.level().getNearestPlayer(enderMan, UPSILON_RANGE);
        return nearest != null && BaubleEffects.has(nearest, ECBaubles.UPSILON_RING.get());
    }
}
