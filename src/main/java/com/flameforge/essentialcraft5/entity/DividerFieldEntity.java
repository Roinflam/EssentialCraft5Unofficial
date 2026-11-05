package com.flameforge.essentialcraft5.entity;

import com.flameforge.essentialcraft5.gun.RayHitGate;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECEffects;
import com.flameforge.essentialcraft5.registry.ECHoloWeapons;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidType;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * 二分场（原作 {@code EntityDivider}，107 行；第八批框架 8-Fa，batch8 §3.1 第 4 条、policy §4.6）。
 * 全息投影的除法攻击和二分枪的弹丸共用：调用方用 {@link #spawn} 放下一个品红球，过 {@code delayTicks} 刻后张开，
 * 球里的生物生命减半、挂上虚弱 V / 缓慢 V / 挖掘疲劳 V 各 {@value #WEAKEN_TICKS} 刻、失明 I {@value #BLIND_TICKS} 刻（照原作，不致死）。
 * <ul>
 *   <li><b>范围</b>：一次 {@code getEntitiesOfClass} 查中心 ±{@value #QUERY} 格（原作 ±0.5 外扩 3），再筛「脚下到中心 ≤ {@value #RADIUS} 格」、
 *       活着、不是旁观 / 创造模式的玩家、没开无敌标记（{@code Invulnerable:1b} 的展示用生物）、不在实体标签 {@code essentialcraft5:divider_immune} 里
 *       （默认只有全息投影；原作写死跳过 {@code EntityHologram}，服主可以往标签里加）、不是放出它的生物本人，最后过调用方给的 {@code affects}。
 *       原作构造器没存攻击者（{@code attacker} 永远是 null），射手自己站在 3 格内也被减半；这里一律跳过攻击者。</li>
 *   <li><b>{@code gated}</b>（玩家射出的场）：对每个目标先打一下 0 伤害的 {@code essentialcraft5:divider}（攻击者 = 射手），
 *       {@link RayHitGate.Watch#probePassed()} 放行才减半、加效果——PvP、领地模组、混合端插件都按射手问得到。
 *       纯 Forge 里 {@code hurt(源, 0)} 会走完 {@code LivingAttackEvent} → {@code LivingHurtEvent(0)}；Mohist 系混合端 0 伤害照样发 Bukkit 事件、
 *       放行后照常发 {@code LivingDamageEvent}。探测期间不击退（{@link HoloStrikeEvents}），{@code divider} 带 {@code no_anger}、不让目标记仇；
 *       探测前后的无敌帧计时原样放回（{@code bypasses_cooldown} 的探测会把它设成 20，不放回的话接下来 10 刻里打它不推不闪）。
 *       纯 Forge 模组在 {@code LivingHurtEvent} 才拦的，原版 {@code hurt} 在那之后照样播受击声、红闪（任何这样被拦的攻击都一样），只是不减半。
 *       玩家挨 0 伤害时 {@code Player.hurt} 直接返回、探测不放行，所以玩家射出的场本来就不打玩家。</li>
 *   <li><b>{@code gated} 为假</b>（全息投影放的）：不探测，直接生效；对象由调用方收窄（参战玩家和敌对生物）。</li>
 *   <li>减半照原作用 {@code setHealth}（setHealth 类机制不改）；效果用 {@code addEffect(效果, 攻击者)}，{@code MobEffectEvent.Applicable} 拦得到。</li>
 *   <li><b>首领模式</b>（第十批 10b，batch10.md §5.7.3，二分枪对首领）：调用方另给一个 {@code bossMode} 规则和时长时，合这个规则、不合 {@code affects}
 *       的生物不减半、不挂那四种效果，改成在探测放行后挂 {@code bossModeTicks} 刻「二分」（{@link ECEffects#BISECTED}：它造成的伤害减半、受到的治疗减半，
 *       由 {@code effect/EffectEvents} 处理）。不砍生命。效果只走 {@code addEffect}：对状态效果免疫的首领照它的设计不吃——
 *       {@code canBeAffected} / Forge 的 {@code MobEffectEvent.Applicable} 拒绝的、像原版凋灵那样把 {@code addEffect} 覆盖成一律返回 false 的都算，
 *       所以原版凋灵挂不上，末影龙本来就过不了探测。不用 {@code forceAddEffect}：它会绕过实体自己的免疫，也不发 {@code MobEffectEvent.Added}。
 *       {@code divider_immune} 里的照旧完全不碰。</li>
 *   <li><b>攻击者没了就熄灭</b>：放下时带了攻击者、到点时它已移除 / 死了 / 不在同一个世界，就什么都不做、直接消失。</li>
 *   <li>每 {@value #SOUND_INTERVAL} 刻一声苦力怕点燃声（音量 1、音高 0.5，原作）；品红粒子只在客户端出。不存盘、不能 {@code /summon}。</li>
 * </ul>
 * 渲染见 {@code client/render/DividerFieldRenderer}（反应堆的球体模型放大 3 倍，品红半透明）。
 */
public class DividerFieldEntity extends Entity {

    /** 生效半径：目标脚下到场中心的距离（原作 RANGE = 3） */
    public static final double RADIUS = 3D;
    /** 先查的立方半边长（原作 ±0.5 外扩 3） */
    public static final double QUERY = 3.5D;
    public static final int SOUND_INTERVAL = 10;
    /** 虚弱、缓慢、挖掘疲劳的时长与等级（原作 200 刻、等级 4 = V） */
    public static final int WEAKEN_TICKS = 200;
    public static final int WEAKEN_AMPLIFIER = 4;
    /** 失明（原作 100 刻、等级 0 = I） */
    public static final int BLIND_TICKS = 100;
    public static final int BLIND_AMPLIFIER = 0;

    private static final DustParticleOptions MAGENTA_DUST = new DustParticleOptions(new Vector3f(1F, 0F, 1F), 1F);

    private int delay = 40;
    @Nullable
    private LivingEntity attacker;
    private boolean expectsAttacker;
    private Predicate<? super LivingEntity> affects = living -> false;
    /** 首领模式的对象（见类注释）；默认谁都不是 */
    private Predicate<? super LivingEntity> bossMode = living -> false;
    private int bossModeTicks;
    private boolean gated;
    private boolean armed;
    private boolean fired;
    private boolean showroomHold;

    public DividerFieldEntity(EntityType<? extends DividerFieldEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /**
     * 在 pos 放下一个二分场（服务端）。
     *
     * @param delayTicks 多少刻后张开（至少 1；Boss 和二分枪都是 40）
     * @param attacker   放出它的生物（探测的攻击者、效果的来源）；给了的话到点时它不在了就熄灭
     * @param affects    作用于哪些生物（攻击者本人、旁观 / 创造模式的玩家、开了无敌标记的、{@code divider_immune} 标签里的另外一律跳过）
     * @param gated      先走 0 伤害探测、放行才生效（玩家射出的场传 true）
     * @return 放下的实体；生成被别的模组取消时返回 null
     */
    @Nullable
    public static DividerFieldEntity spawn(ServerLevel level, Vec3 pos, int delayTicks, @Nullable LivingEntity attacker,
                                           Predicate<? super LivingEntity> affects, boolean gated) {
        return spawn(level, pos, delayTicks, attacker, affects, gated, living -> false, 0);
    }

    /**
     * 同 {@link #spawn(ServerLevel, Vec3, int, LivingEntity, Predicate, boolean)}，另带首领模式（见类注释）：
     * 合 {@code bossMode}、不合 {@code affects} 的生物（同样先过探测，{@code gated} 为真时）挂 {@code bossModeTicks} 刻二分，不减半。
     *
     * @param bossMode      首领模式作用于哪些生物（攻击者本人等同样一律跳过）
     * @param bossModeTicks 二分挂多久（刻）；≤ 0 = 不用首领模式
     */
    @Nullable
    public static DividerFieldEntity spawn(ServerLevel level, Vec3 pos, int delayTicks, @Nullable LivingEntity attacker,
                                           Predicate<? super LivingEntity> affects, boolean gated,
                                           Predicate<? super LivingEntity> bossMode, int bossModeTicks) {
        Objects.requireNonNull(affects, "affects");
        Objects.requireNonNull(bossMode, "bossMode");
        DividerFieldEntity field = new DividerFieldEntity(ECHoloWeapons.DIVIDER_FIELD.get(), level);
        field.moveTo(pos.x, pos.y, pos.z, 0F, 0F);
        field.delay = Math.max(1, delayTicks);
        field.attacker = attacker;
        field.expectsAttacker = attacker != null;
        field.affects = affects;
        field.bossMode = bossMode;
        field.bossModeTicks = Math.max(0, bossModeTicks);
        field.gated = gated;
        field.armed = true;
        return level.addFreshEntity(field) ? field : null;
    }

    @Override
    protected void defineSynchedData() {
    }

    // ------------------------------------------------------------------ 每刻

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            level().addParticle(MAGENTA_DUST, getX(), getY(), getZ(), 0D, 0D, 0D);
            return;
        }
        if (isRemoved()) {
            return;
        }
        if (!armed) {
            discard();
            return;
        }
        if (showroomHold) {
            return;
        }
        if (tickCount % SOUND_INTERVAL == 0) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.CREEPER_PRIMED, soundSource(), 1F, 0.5F);
        }
        if (tickCount >= delay) {
            detonate();
        }
    }

    /** 立刻张开（同到点；GameTest、展示厅用）。只结算一次，之后实体消失 */
    public void detonate() {
        detonate(List.of());
    }

    /**
     * 同 {@link #detonate()}，另把 extra 里的活体也当候选：GameTest 里的真玩家不在世界的实体列表里，查不出来。
     * 候选照样要在同一个世界、在立方内、过同样的筛选。
     */
    public void detonate(Collection<? extends LivingEntity> extra) {
        if (fired || !(level() instanceof ServerLevel level)) {
            return;
        }
        fired = true;
        try {
            if (!armed || (expectsAttacker && !attackerPresent(level))) {
                return;
            }
            for (LivingEntity target : targets(level, extra)) {
                // 先定好是哪一种（减半还是首领模式），探测之后不再重判
                boolean halve = affects.test(target);
                if (gated && !probe(level, target)) {
                    continue;
                }
                if (!target.isAlive()) {
                    continue;
                }
                if (halve) {
                    apply(target);
                } else {
                    applyBossMode(target);
                }
            }
        } finally {
            discard();
        }
    }

    /** 0 伤害探测：放行了才算（见类注释）；无敌帧计时原样放回 */
    private boolean probe(ServerLevel level, LivingEntity target) {
        int invulnerable = target.invulnerableTime;
        RayHitGate.Watch watch = HoloStrikeEvents.strike(target, ECDamageTypes.divider(level, attacker), 0F);
        target.invulnerableTime = invulnerable;
        return watch.probePassed();
    }

    private void apply(LivingEntity target) {
        target.setHealth(target.getHealth() / 2F);
        addEffect(target, MobEffects.WEAKNESS, WEAKEN_TICKS, WEAKEN_AMPLIFIER);
        addEffect(target, MobEffects.MOVEMENT_SLOWDOWN, WEAKEN_TICKS, WEAKEN_AMPLIFIER);
        addEffect(target, MobEffects.DIG_SLOWDOWN, WEAKEN_TICKS, WEAKEN_AMPLIFIER);
        addEffect(target, MobEffects.BLINDNESS, BLIND_TICKS, BLIND_AMPLIFIER);
    }

    /** 首领模式：只挂二分（效果没注册时什么都不做）；只走 addEffect，对状态效果免疫的首领挂不上，见类注释 */
    private void applyBossMode(LivingEntity target) {
        if (bossModeTicks <= 0 || !ECEffects.BISECTED.isPresent()) {
            return;
        }
        target.addEffect(new MobEffectInstance(ECEffects.BISECTED.get(), bossModeTicks, 0), attacker);
    }

    /** 原作 new PotionEffect(效果, 时长, 等级, ambient = true, showParticles = true) */
    private void addEffect(LivingEntity target, MobEffect effect, int ticks, int amplifier) {
        target.addEffect(new MobEffectInstance(effect, ticks, amplifier, true, true), attacker);
    }

    private List<LivingEntity> targets(ServerLevel level, Collection<? extends LivingEntity> extra) {
        AABB box = new AABB(position(), position()).inflate(QUERY);
        List<LivingEntity> found = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, box, this::eligible));
        for (LivingEntity candidate : extra) {
            if (candidate.level() == level && !found.contains(candidate) && candidate.getBoundingBox().intersects(box) && eligible(candidate)) {
                found.add(candidate);
            }
        }
        return found;
    }

    private boolean eligible(LivingEntity living) {
        if (!living.isAlive() || living == attacker || living.isSpectator() || living.isInvulnerable()
                || living.getType().is(ECHoloWeapons.DIVIDER_IMMUNE)) {
            return false;
        }
        if (living instanceof Player player && player.isCreative()) {
            return false;
        }
        return living.distanceTo(this) <= RADIUS && (affects.test(living) || inBossMode(living));
    }

    /** 不减半、改挂二分的那一类（首领模式开着、合规则） */
    private boolean inBossMode(LivingEntity living) {
        return bossModeTicks > 0 && bossMode.test(living);
    }

    private boolean attackerPresent(ServerLevel level) {
        LivingEntity source = attacker;
        return source != null && !source.isRemoved() && source.isAlive() && source.level() == level;
    }

    private SoundSource soundSource() {
        return attacker instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE;
    }

    // ------------------------------------------------------------------ 读数

    /** 多少刻后张开（服务端） */
    public int delayTicks() {
        return delay;
    }

    /** 首领模式挂多久的二分（刻，0 = 没有首领模式；服务端） */
    public int bossModeTicks() {
        return bossModeTicks;
    }

    /** 是不是玩家射出的、要先探测的场（服务端） */
    public boolean gated() {
        return gated;
    }

    /** 攻击者（服务端；客户端恒为 null） */
    @Nullable
    public LivingEntity attacker() {
        return attacker;
    }

    /** 展示厅用：hold 为真时不倒数、不张开，一直挂着给镜头拍（服务端调） */
    public void setShowroomHold(boolean hold) {
        this.showroomHold = hold;
    }

    /** 球体半径 3，视锥剔除按整个球算 */
    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(RADIUS);
    }

    // ------------------------------------------------------------------ 存档（不存）

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    // ------------------------------------------------------------------ 摸不着、推不动

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean ignoreExplosion() {
        return true;
    }

    @Override
    public boolean isPushedByFluid(FluidType type) {
        return false;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }
}
