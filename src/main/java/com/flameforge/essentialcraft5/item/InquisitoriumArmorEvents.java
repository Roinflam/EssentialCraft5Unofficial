package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.item.attunement.Attunement;
import com.flameforge.essentialcraft5.item.attunement.AttunementConfig;
import com.flameforge.essentialcraft5.item.attunement.AttunementGains;
import com.flameforge.essentialcraft5.item.module.MatrixFilterEvents;
import com.flameforge.essentialcraft5.registry.ECAttunement;
import com.flameforge.essentialcraft5.registry.ECInquisitorium;
import com.flameforge.essentialcraft5.registry.ECTags;
import com.flameforge.essentialcraft5.util.ECDamageKinds;
import com.flameforge.essentialcraft5.util.ECLandedHit;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 审判庭套装穿在身上时的规则（第十三批 13b，batch13.md §4.3 审判庭一行、§4.4 审判庭、D14、D35 A；配置 {@code general.inquisitorium_armor}，
 * 见 {@link ECInquisitorium.Config#law()}、{@link ECInquisitorium.Config#attuned()}）。只在服务端、只对玩家（假玩家不算）；
 * 除了不掉耐久（{@link InquisitoriumArmorItem#damageItem}，按件）以外都要四件穿齐，阶按四件里最低的那件算（{@link Attunement#fullSetStage}），
 * 混穿两套时两套的全套效果都没有。
 *
 * <h2>审判庭之律（0 阶）</h2>
 * <ul>
 *   <li><b>抗魔</b>：魔法伤害（{@link ECDamageKinds#isMagic}，带不带 {@code bypasses_armor} 都算）× (1 − {@code magic_resistance})。
 *       要有攻击者、攻击者不是自己：腐化 #17 / #18、MRU 腐化这类 {@code magic()} 自伤没有攻击者，照扣（batch13.md §0.4 第 21 条，同宅男 2 阶、暗影相性）。</li>
 *   <li><b>首领减伤</b>：攻击者在 {@code #forge:bosses} 里时 × (1 − {@code boss_resistance})，和抗魔相乘。</li>
 *   <li><b>律令</b>：每 {@value #EDICT_INTERVAL} 刻把身上的有害效果各削掉一截，时长约 × (1 − {@code edict_reduction})（{@link #edictCut}）。
 *       写法照医师腰带（{@code BaubleEffects.shortenHarmfulEffects}）：反射改剩余时长、至少留 1 刻让它自己按原版流程到期、改完给本人发一个效果更新包，
 *       不重新加效果；对象从医师腰带的八种扩到全部 {@link MobEffectCategory#HARMFUL}；无限时长的、{@code essentialcraft5:unfilterable} 里的
 *       （锚定这类机制效果，矩阵过滤同样不拦）不动。和医师腰带叠着戴时两边各削各的。</li>
 *   <li><b>定罪</b>：活物打实你一下（{@link LivingDamageEvent} LOWEST、没被取消，伤害 &gt; 0 或者打掉了你的伤害吸收；反伤不算它动手）
 *       就记它 {@code conviction_ticks} 刻，每人最多 {@code conviction_max_targets} 个（满了挤掉最快到期的），新记上时在它身上 {@code sendParticles} 一次；
 *       你亲手打被你定罪的目标 × (1 + {@code conviction_bonus})，目标是玩家时增伤再乘 {@code general.attunement.pvp_effect_scale}。
 *       反伤、饰品打出的伤害、尖刺圆盾的范围伤害（{@link AttunementGains#notOwnHit}）和裁决之刃的裁断不加，和虚空回击、相性、矩阵特性同一口径。
 *       记在内存里（按玩家对象弱引用、按服务器刻数到期），重登、死亡后从零开始。</li>
 * </ul>
 *
 * <h2>同调阶</h2>
 * <table>
 *   <tr><th>阶</th><th>效果</th></tr>
 *   <tr><td>1</td><td>抗魔换成 45%；「赦罪」：亲手打死被你定罪的目标时清掉自己一个有害效果（有限时长的先清、清剩余最长的，
 *       一个有限时长的都没有时才清无限时长的；每 {@code absolution_cooldown} 刻一次，没有可清的不算一次）</td></tr>
 *   <tr><td>2</td><td>定罪增伤换成 30%，目标是首领再 +10%</td></tr>
 *   <tr><td>3</td><td>「壁垒」：护甲后单下超过最大生命 30% 的部分 ×0.5（{@link LivingDamageEvent} HIGH；只管有攻击者的伤害；按扣伤害吸收之前的整下算）</td></tr>
 *   <tr><td>4</td><td>有攻击者的无视护甲、不是魔法的伤害 ×0.7；壁垒门槛 25%、超出 ×0.4</td></tr>
 * </table>
 *
 * <h2>倍率的位置和顺序</h2>
 * 抗魔、首领减伤、4 阶无视护甲、定罪增伤都在 {@link LivingHurtEvent} NORMAL（护甲之前），和相性的增减伤（{@code MatrixResonanceEvents}）、
 * 矩阵特性、η 护身符同一层，全是按比例相乘，同一层里谁先谁后结果一样；MRU 护盾在 LOW，看到的是乘过的数。
 * 壁垒要「护甲后」的数，放在 {@link LivingDamageEvent} HIGH：在后面按最终伤害记账的监听器（同调点、透镜吸取、定罪本身）之前改好。
 * {@code bypasses_invulnerability}（{@code /kill}、掉出世界）和 {@code essentialcraft5:unshieldable} 里的伤害这几条都不减。
 *
 * <h2>伤害吸收（黄心）</h2>
 * 原版 {@code actuallyHurt} 先过护甲、再扣伤害吸收，扣剩的才发 {@link LivingDamageEvent}，事件里的数已经少了黄心吃掉的那截。
 * 壁垒的门槛是按「护甲后的整下」定的（batch13.md §4.4，手册的例子也是这么算的），所以向 {@link ECLandedHit#absorbed} 要黄心吃掉了多少
 * （它在 {@link LivingHurtEvent} LOWEST 记下那时的伤害吸收，按「同一个受害者、同一个伤害源对象」对上），
 * 把黄心吃掉的加回来算壁垒：减掉的部分先从这一下里扣，扣到 0 还有剩就退回黄心（{@link #applyBulwark}）。这样总共掉的是壁垒(整下)，黄心照样先扣。
 * 定罪认的「打实」是 {@link ECLandedHit#landed}：伤害是 0、但黄心少了的一下也算。同一下第一次问到的数会记住，壁垒在 HIGH 退回黄心不影响定罪在 LOWEST 认它打实。
 *
 * <h2>混合端</h2>
 * 减伤、增伤只是在 {@link LivingHurtEvent} 里改数，没有要扣的东西；定罪等伤害真的落下才记（{@link LivingDamageEvent}），
 * Mohist 系混合端上被领地插件拦下的攻击到不了那里，不记、不冒粒子。赦罪在 {@link LivingDeathEvent} LOWEST、不收已取消的（图腾救下不算）。
 *
 * <h2>换装</h2>
 * 这里不缓存「穿没穿齐」，每次用到时现查四个盔甲槽；同调写盘只改 NBT（原版会发一次换装事件）不影响任何一条，定罪表不跟着换装清。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class InquisitoriumArmorEvents {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 1 阶起抗魔换数、有赦罪 */
    public static final int ABSOLUTION_STAGE = 1;
    /** 2 阶起定罪增伤换数、对首领再加 */
    public static final int CONVICTION_STAGE = 2;
    /** 3 阶起有壁垒 */
    public static final int BULWARK_STAGE = 3;
    /** 4 阶起减无视护甲、壁垒换数 */
    public static final int TOP_STAGE = 4;
    /** 律令每多少刻削一次 */
    public static final int EDICT_INTERVAL = 20;
    /** 定罪的粒子数 */
    public static final int CONVICTION_PARTICLES = 12;

    /** 定罪的粒子：审判庭的品红（同哨兵刷怪蛋的斑点色 #E040FF） */
    private static final DustParticleOptions CONVICTION_PARTICLE = new DustParticleOptions(new Vector3f(0.878F, 0.251F, 1F), 1.2F);

    /** 一条定罪：被定罪的生物（弱引用）、到哪一个服务器刻为止（不含） */
    private static final class Conviction {
        final WeakReference<LivingEntity> target;
        long until;

        Conviction(LivingEntity target, long until) {
            this.target = new WeakReference<>(target);
            this.until = until;
        }
    }

    /** 每名玩家的定罪表（按玩家对象弱引用；只在服务端线程读写） */
    private static final Map<Player, List<Conviction>> CONVICTIONS = new WeakHashMap<>();
    /** 赦罪到哪一个服务器刻为止还在冷却（不含） */
    private static final Map<Player, Long> ABSOLUTION_UNTIL = new WeakHashMap<>();

    @Nullable
    private static Field durationField;
    private static boolean durationFieldFailed;
    @Nullable
    private static volatile Long testNow;

    private InquisitoriumArmorEvents() {
    }

    // ------------------------------------------------------------------
    // 穿没穿齐
    // ------------------------------------------------------------------

    /** 全套的同调阶（四件里最低的）；没穿齐、不是玩家、是假玩家都是 -1 */
    public static int setStage(@Nullable Entity entity) {
        if (!(entity instanceof Player player) || player instanceof FakePlayer) {
            return -1;
        }
        return Attunement.fullSetStage(player, ECAttunement.INQUISITORIUM_ARMOR);
    }

    /** 这一下整个不受这几条影响：{@code bypasses_invulnerability}、{@code essentialcraft5:unshieldable} */
    private static boolean excluded(DamageSource source) {
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(ECTags.UNSHIELDABLE);
    }

    /** 这一下有攻击者、攻击者不是挨打的本人 */
    private static boolean hasOtherAttacker(DamageSource source, LivingEntity victim) {
        Entity attacker = source.getEntity();
        return attacker != null && attacker != victim;
    }

    private static double fraction(double value) {
        return Double.isFinite(value) ? Math.max(0D, Math.min(1D, value)) : 0D;
    }

    // ------------------------------------------------------------------
    // 挨打的一方：抗魔、首领减伤、4 阶无视护甲
    // ------------------------------------------------------------------

    /**
     * 穿全套的玩家挨这一下时乘的倍数（1 = 不减）：抗魔或 4 阶无视护甲（二者互斥：魔法走抗魔）× 首领减伤。
     * 都要有攻击者、攻击者不是自己；{@code bypasses_invulnerability}、{@code unshieldable} 不减。
     */
    public static double incomingMultiplier(LivingEntity victim, DamageSource source) {
        int stage = setStage(victim);
        if (stage < 0 || excluded(source) || !hasOtherAttacker(source, victim)) {
            return 1D;
        }
        ECInquisitorium.Config.Law law = ECInquisitorium.Config.law();
        ECInquisitorium.Config.Attuned attuned = ECInquisitorium.Config.attuned();
        double factor = 1D;
        if (ECDamageKinds.isMagic(source)) {
            factor *= 1D - fraction(stage >= ABSOLUTION_STAGE ? attuned.magicResistance() : law.magicResistance());
        } else if (stage >= TOP_STAGE && source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            factor *= 1D - fraction(attuned.piercingResistance());
        }
        Entity attacker = source.getEntity();
        if (attacker != null && attacker.getType().is(Tags.EntityTypes.BOSSES)) {
            factor *= 1D - fraction(law.bossResistance());
        }
        return factor;
    }

    // ------------------------------------------------------------------
    // 打人的一方：定罪增伤
    // ------------------------------------------------------------------

    /**
     * 穿全套的 attacker 打 target 这一下多出的比例（0 = 不加）：target 被他定罪着、这一下记在他头上、是他亲手打的；
     * 0~1 阶 {@code conviction_bonus}，2 阶起 {@code conviction_bonus_stage_2}（首领再加 {@code conviction_boss_bonus_stage_2}）；
     * target 是玩家时乘 {@code general.attunement.pvp_effect_scale}。
     * <p>
     * 「亲手」同虚空回击、相性、矩阵特性：反伤、饰品打出的伤害、尖刺圆盾的范围伤害（{@link AttunementGains#notOwnHit}）不加，
     * 裁决之刃的裁断（{@link VerdictBladeItem#VERDICT}，按目标最大生命补的那一下）也不加。弹射物照加，那是他自己射的。
     */
    public static double convictionBonus(LivingEntity attacker, LivingEntity target, DamageSource source) {
        int stage = setStage(attacker);
        if (stage < 0 || attacker == target || source.getEntity() != attacker || AttunementGains.notOwnHit(source)
                || source.is(VerdictBladeItem.VERDICT) || !isConvicted((Player) attacker, target)) {
            return 0D;
        }
        double bonus;
        if (stage >= CONVICTION_STAGE) {
            ECInquisitorium.Config.Attuned attuned = ECInquisitorium.Config.attuned();
            bonus = attuned.convictionBonus() + (target.getType().is(Tags.EntityTypes.BOSSES) ? attuned.convictionBossBonus() : 0D);
        } else {
            bonus = ECInquisitorium.Config.law().convictionBonus();
        }
        if (!(bonus > 0D) || !Double.isFinite(bonus)) {
            return 0D;
        }
        return target instanceof Player ? bonus * Math.max(0D, AttunementConfig.pvpEffectScale()) : bonus;
    }

    /** 抗魔、首领减伤、无视护甲、定罪增伤（NORMAL，不收已取消的；护甲之前） */
    @SubscribeEvent
    public static void onInquisitoriumHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }
        float amount = event.getAmount();
        if (!(amount > 0F) || !Float.isFinite(amount)) {
            return;
        }
        DamageSource source = event.getSource();
        double factor = incomingMultiplier(victim, source);
        if (source.getEntity() instanceof Player attacker) {
            factor *= 1D + convictionBonus(attacker, victim, source);
        }
        if (factor != 1D) {
            event.setAmount((float) Math.max(0D, amount * factor));
        }
    }

    // ------------------------------------------------------------------
    // 壁垒（3 阶）
    // ------------------------------------------------------------------

    /**
     * 壁垒之后的伤害：全套 3 阶起，有攻击者（不是自己）、护甲后的 amount 超过最大生命 × 门槛时，超出的部分按阶减掉；
     * 不满足条件原样返回。
     */
    public static float bulwark(LivingEntity victim, DamageSource source, float amount) {
        int stage = setStage(victim);
        if (stage < BULWARK_STAGE || !(amount > 0F) || !Float.isFinite(amount) || excluded(source) || !hasOtherAttacker(source, victim)) {
            return amount;
        }
        ECInquisitorium.Config.Attuned attuned = ECInquisitorium.Config.attuned();
        boolean top = stage >= TOP_STAGE;
        double threshold = fraction(top ? attuned.bulwarkThresholdTop() : attuned.bulwarkThreshold());
        double reduction = fraction(top ? attuned.bulwarkReductionTop() : attuned.bulwarkReduction());
        double limit = victim.getMaxHealth() * threshold;
        if (!(reduction > 0D) || !(amount > limit)) {
            return amount;
        }
        return (float) (limit + (amount - limit) * (1D - reduction));
    }

    /**
     * 按「护甲后、扣黄心之前」的整下算壁垒（纯函数，GameTest 直接测）：amount 是 {@link LivingDamageEvent} 里的数（已经扣过黄心），
     * absorbed 是黄心吃掉的。整下 = amount + absorbed，壁垒减掉的部分先从 amount 里扣，扣到 0 还有剩的退回黄心。
     *
     * @return {@code [新的伤害, 退回黄心的量]}；壁垒不起作用时是 {@code [amount, 0]}
     */
    public static float[] applyBulwark(LivingEntity victim, DamageSource source, float amount, float absorbed) {
        float whole = amount + Math.max(0F, absorbed);
        float cut = whole - bulwark(victim, source, whole);
        if (!(cut > 0F) || !Float.isFinite(cut)) {
            return new float[]{amount, 0F};
        }
        return amount >= cut ? new float[]{amount - cut, 0F} : new float[]{0F, cut - amount};
    }

    /** 壁垒（HIGH，不收已取消的；护甲之后、按扣黄心之前的整下算，见类注释「伤害吸收」） */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInquisitoriumBulwark(LivingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || setStage(victim) < BULWARK_STAGE) {
            return;
        }
        float amount = event.getAmount();
        float[] result = applyBulwark(victim, event.getSource(), amount, ECLandedHit.absorbed(event));
        if (result[1] > 0F) {
            victim.setAbsorptionAmount(victim.getAbsorptionAmount() + result[1]);
        }
        if (result[0] != amount) {
            event.setAmount(result[0]);
        }
    }

    // ------------------------------------------------------------------
    // 定罪表
    // ------------------------------------------------------------------

    /** 服务器刻（测试可钉住；各世界的 gameTime 不一定一样，换维度会错，所以不用它） */
    public static long now(Entity entity) {
        Long override = testNow;
        if (override != null) {
            return override;
        }
        MinecraftServer server = entity.level().getServer();
        return server != null ? server.getTickCount() : entity.level().getGameTime();
    }

    /**
     * 活物打实了穿全套的玩家（LOWEST，不收已取消的；打实照 {@link ECLandedHit#landed}，混合端被插件拦下的到不了这里）。
     * 黄心整下吃掉的一下事件里是 0，可它确实打中了你（原版也把它记成造成了伤害），照样定罪。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onInquisitoriumConvict(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        if (!ECLandedHit.landed(event)) {
            return;
        }
        DamageSource source = event.getSource();
        if (source.getEntity() instanceof LivingEntity attacker && attacker != player && !ECDamageKinds.isReflected(source)) {
            convict(player, attacker);
        }
    }

    /**
     * 把 target 记进 player 的定罪表（穿全套才记）：已经在表里的刷新到期时间；新记上的在它身上冒一次粒子。
     *
     * @return 这次是新记上的
     */
    public static boolean convict(Player player, LivingEntity target) {
        if (setStage(player) < 0 || target == player || !target.isAlive()) {
            return false;
        }
        ECInquisitorium.Config.Law law = ECInquisitorium.Config.law();
        int ticks = law.convictionTicks();
        int max = law.convictionMaxTargets();
        if (ticks <= 0 || max <= 0) {
            return false;
        }
        long now = now(player);
        List<Conviction> list = CONVICTIONS.computeIfAbsent(player, p -> new ArrayList<>(4));
        prune(list, now);
        for (Conviction conviction : list) {
            if (conviction.target.get() == target) {
                conviction.until = now + ticks;
                return false;
            }
        }
        while (list.size() >= max) {
            Conviction soonest = list.get(0);
            for (Conviction conviction : list) {
                if (conviction.until < soonest.until) {
                    soonest = conviction;
                }
            }
            list.remove(soonest);
        }
        list.add(new Conviction(target, now + ticks));
        if (target.level() instanceof ServerLevel level) {
            level.sendParticles(CONVICTION_PARTICLE, target.getX(), target.getY() + target.getBbHeight() * 0.6D, target.getZ(),
                    CONVICTION_PARTICLES, target.getBbWidth() * 0.4D, target.getBbHeight() * 0.3D, target.getBbWidth() * 0.4D, 0D);
        }
        return true;
    }

    /** target 现在被 player 定罪着（没到期、还在世界里） */
    public static boolean isConvicted(Player player, LivingEntity target) {
        List<Conviction> list = CONVICTIONS.get(player);
        if (list == null || list.isEmpty()) {
            return false;
        }
        long now = now(player);
        for (Conviction conviction : list) {
            if (conviction.target.get() == target) {
                return now < conviction.until && !target.isRemoved();
            }
        }
        return false;
    }

    /** player 现在定罪着几个（过期的先清掉） */
    public static int convictedCount(Player player) {
        List<Conviction> list = CONVICTIONS.get(player);
        if (list == null) {
            return 0;
        }
        prune(list, now(player));
        return list.size();
    }

    private static void prune(List<Conviction> list, long now) {
        for (Iterator<Conviction> it = list.iterator(); it.hasNext(); ) {
            Conviction conviction = it.next();
            LivingEntity target = conviction.target.get();
            if (target == null || target.isRemoved() || now >= conviction.until) {
                it.remove();
            }
        }
    }

    private static void forget(Player player, LivingEntity target) {
        List<Conviction> list = CONVICTIONS.get(player);
        if (list != null) {
            list.removeIf(conviction -> conviction.target.get() == target);
        }
    }

    // ------------------------------------------------------------------
    // 赦罪（1 阶）
    // ------------------------------------------------------------------

    /** 亲手打死了谁（LOWEST，不收已取消的：图腾救下不算） */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onInquisitoriumAbsolution(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }
        DamageSource source = event.getSource();
        if (source.getEntity() instanceof Player killer && killer != victim && !AttunementGains.notOwnHit(source)) {
            absolve(killer, victim);
        }
    }

    /**
     * 赦罪：killer 全套 1 阶起、victim 被他定罪着、冷却走完时清掉 killer 一个有害效果；清掉了才进冷却、动作栏说一句。
     *
     * @return 清掉的效果；没清返回 null
     */
    @Nullable
    public static MobEffect absolve(Player killer, LivingEntity victim) {
        if (setStage(killer) < ABSOLUTION_STAGE || !isConvicted(killer, victim)) {
            return null;
        }
        long now = now(killer);
        Long until = ABSOLUTION_UNTIL.get(killer);
        if (until != null && now < until) {
            return null;
        }
        MobEffect removed = cleanseOne(killer);
        forget(killer, victim);
        if (removed == null) {
            return null;
        }
        int cooldown = ECInquisitorium.Config.attuned().absolutionCooldown();
        if (cooldown > 0) {
            ABSOLUTION_UNTIL.put(killer, now + cooldown);
        }
        killer.displayClientMessage(Component.translatable("message.essentialcraft5.inquisitorium_armor.absolution", removed.getDisplayName())
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return removed;
    }

    /**
     * 清掉 entity 一个有害效果：有限时长的先清、清剩余时间最长的，一个有限时长的都没有时才清无限时长的（不止一个时取第一个）；
     * {@code essentialcraft5:unfilterable} 里的不清。移除被别的模组取消时算没清。
     * <p>
     * 和透镜剥增益（{@code LensEffects.dispelOne}，无限时长优先）顺序相反：给自己净化时，无限时长的有害效果多半是别的模组每刻补挂的
     * （饰品、诅咒物品、光环），清了下一刻又回来，冷却却用掉了；先清有限的，挑错了也只是晚一点清。
     */
    @Nullable
    public static MobEffect cleanseOne(LivingEntity entity) {
        MobEffectInstance pick = null;
        for (MobEffectInstance instance : entity.getActiveEffects()) {
            if (instance.getEffect().getCategory() != MobEffectCategory.HARMFUL || MatrixFilterEvents.isUnfilterable(instance.getEffect())) {
                continue;
            }
            if (pick == null || outranks(instance, pick)) {
                pick = instance;
            }
        }
        if (pick == null) {
            return null;
        }
        MobEffect effect = pick.getEffect();
        return entity.removeEffect(effect) ? effect : null;
    }

    private static boolean outranks(MobEffectInstance a, MobEffectInstance b) {
        if (a.isInfiniteDuration() != b.isInfiniteDuration()) {
            return !a.isInfiniteDuration();
        }
        return !a.isInfiniteDuration() && a.getDuration() > b.getDuration();
    }

    // ------------------------------------------------------------------
    // 律令
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onInquisitoriumEdictTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide() && event.player.tickCount % EDICT_INTERVAL == 0) {
            edict(event.player);
        }
    }

    /**
     * 每 {@value #EDICT_INTERVAL} 刻再削掉几刻，让时长缩到 1 − reduction：每刻多走 reduction / (1 − reduction) 刻。
     * 默认 0.4 → 13 刻（实际 ×20/33 ≈ 0.606）；0 或负数是 0（不削）。
     */
    public static int edictCut(double reduction) {
        if (!(reduction > 0D) || !Double.isFinite(reduction)) {
            return 0;
        }
        double r = Math.min(0.9D, reduction);
        return (int) Math.round(EDICT_INTERVAL * r / (1D - r));
    }

    /**
     * 律令削一次（每 {@value #EDICT_INTERVAL} 刻一次；GameTest 里的玩家不 tick，直接调）：穿全套时把有害效果各削掉 {@link #edictCut} 刻。
     *
     * @return 削了几个效果
     */
    public static int edict(LivingEntity entity) {
        if (setStage(entity) < 0) {
            return 0;
        }
        return shortenHarmful(entity, edictCut(ECInquisitorium.Config.law().edictReduction()));
    }

    /**
     * 把 entity 身上的有害效果各缩短 ticks（至少留 1 刻，让它按原版流程到期、移除属性修饰符）；无限时长的、{@code unfilterable} 里的不动。
     * 反射改私有的剩余时长（SRG 名 f_19503_，同医师腰带），改完给本人发一个效果更新包，不重新加效果（重新加会重算属性、发事件）。
     * 别的模组做成有害效果的冷却、代价同样会被缩；不该缩的由整合包作者放进 {@code essentialcraft5:unfilterable}（矩阵过滤同样不拦它）。
     * 只缩表层：被同种更强效果盖住的那一层（{@code hiddenEffect}）不动，等它露出来以后再按每次的间隔缩，和医师腰带一样。
     *
     * @return 缩短了几个效果
     */
    public static int shortenHarmful(LivingEntity entity, int ticks) {
        Field field = durationField();
        if (field == null || ticks <= 0) {
            return 0;
        }
        int changed = 0;
        for (MobEffectInstance instance : entity.getActiveEffects()) {
            if (instance.getEffect().getCategory() != MobEffectCategory.HARMFUL || instance.isInfiniteDuration()
                    || MatrixFilterEvents.isUnfilterable(instance.getEffect())) {
                continue;
            }
            int duration = instance.getDuration();
            int cut = Math.min(ticks, duration - 1);
            if (cut <= 0) {
                continue;
            }
            try {
                field.setInt(instance, duration - cut);
            } catch (IllegalAccessException e) {
                return changed;
            }
            changed++;
            if (entity instanceof ServerPlayer serverPlayer && serverPlayer.connection != null) {
                serverPlayer.connection.send(new ClientboundUpdateMobEffectPacket(serverPlayer.getId(), instance));
            }
        }
        return changed;
    }

    @Nullable
    private static Field durationField() {
        if (durationField == null && !durationFieldFailed) {
            try {
                durationField = ObfuscationReflectionHelper.findField(MobEffectInstance.class, "f_19503_");
            } catch (RuntimeException e) {
                durationFieldFailed = true;
                LOGGER.error("[EC5] 审判庭律令找不到 MobEffectInstance.duration（f_19503_），缩短有害效果失效", e);
            }
        }
        return durationField;
    }

    // ------------------------------------------------------------------
    // 收尾、测试
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onInquisitoriumServerStopped(ServerStoppedEvent event) {
        CONVICTIONS.clear();
        ABSOLUTION_UNTIL.clear();
    }

    /** GameTest 用：把「现在」钉在给定的服务器刻（null = 照常读服务器） */
    public static void setTestNow(@Nullable Long tick) {
        testNow = tick;
    }

    /** GameTest 用：清掉这名玩家的定罪表和赦罪冷却 */
    public static void forgetForTest(Player player) {
        CONVICTIONS.remove(player);
        ABSOLUTION_UNTIL.remove(player);
    }
}
