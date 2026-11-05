package com.flameforge.essentialcraft5.entity;

import com.flameforge.essentialcraft5.gun.RayHitGate;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECHoloWeapons;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * 轨道打击（原作 {@code EntityOrbitalStrike}，167 行；第八批框架 8-Fa，batch8 §3.1 第 3 条、policy §4.6）。
 * 全息投影的乘法攻击和轨道打击遥控器共用：调用方用 {@link #spawn} 放下，地上立起一道品红光柱，过 {@code delayTicks} 刻后落下一击。
 * <ul>
 *   <li><b>结算</b>：一次 {@code getEntitiesOfClass} 查中心 ±{@value #REACH} 格立方内的活体（穿墙，同原作），跳过攻击者本人、旁观者，再按调用方给的
 *       {@code affects} 筛；对每个目标造成 {@code damage}（玩家 × {@code playerMultiplier}，乘出来是 0 的直接跳过），伤害类型
 *       {@code essentialcraft5:orbital_strike}，攻击者 = 召下这一击的生物（见 {@code ECDamageTypes}）。</li>
 *   <li><b>着火在伤害落下之后</b>：{@link RayHitGate.Watch#landed()} 为真、而且目标的生命 + 伤害吸收真的少了，才着火 {@value #FIRE_SECONDS} 秒
 *       （原作先点火后伤害，伤害被拦下也照样着火）。结算期间这个目标不被击退（{@link HoloStrikeEvents}，原作匿名伤害源本来没有击退）。
 *       攻击事件被拦、或混合端插件拦下时不留副作用；纯 Forge 的领地模组要是在 {@code LivingHurtEvent} 才拦，原版 {@code hurt} 照样红闪、
 *       放受击声、让目标记仇，只是不着火、不击退、不叫增援（僵尸的叫增援也由 {@link HoloStrikeEvents} 按「落没落下」拦）。</li>
 *   <li><b>攻击者没了就熄灭</b>：放下时带了攻击者、到点时它已移除（下线、死后重生）、死了、或不在同一个世界，就不结算、不爆炸，直接消失——不留匿名伤害。</li>
 *   <li><b>爆炸只出声光</b>：结算完以后 {@code ECProtection.safeExplode(…, e -> false)}（不破坏方块、不放火、不伤也不推任何实体），
 *       另补 {@value #IMPACT_PARTICLES} 个大爆炸粒子（原作客户端每刻 20 个）。不让爆炸去算伤害：原版爆炸给不出平的数值，
 *       而且在 {@code hurt} 之后无条件击退，纯 Forge 的领地模组拦下伤害后目标照样被炸飞。</li>
 *   <li><b>不改任何方块</b>：原作在 5×5×5 内删水、冰、雪，把能烧的方块就地按熔炉配方烧掉、产物掉地上（远程熔炉 + 采矿器，不看领地），全部删掉。</li>
 *   <li>充能声 {@code sound.orbital_strike} 在第 {@value #CHARGE_SOUND_TICK} 刻放一次，音量 {@value #CHARGE_SOUND_VOLUME}（32 格内听得到；
 *       原作 1 = 16 格，射程 32 的使用者站在最远处自己都听不到）。攻击者是玩家时归「玩家」音量滑块，否则归「敌对生物」。</li>
 *   <li>不存盘（注册时 {@code noSave}，{@link #shouldBeSaved()} 也是假）、不能用 {@code /summon} 召出（{@code noSummon}；没有攻击者和目标规则的打击没有意义）。</li>
 *   <li><b>跟随</b>（第十批 10b 轨道锁定，batch10.md §5.7.4；{@link #spawn(ServerLevel, Vec3, float, float, int, LivingEntity, Predicate, Entity)} 多传一个 {@code follow}）：
 *       每刻把打击点水平挪到被跟的实体脚下（高度不变），落下前 {@value #FOLLOW_FREEZE_TICKS} 刻定住不再跟；被跟的实体没了（移除、死了）
 *       或者不在同一个世界了，就停在最后跟到的位置。只是换了落点，结算规矩一样（伤害带攻击者、只打放行的、按 {@code affects} 筛）。</li>
 * </ul>
 * <h2>同步</h2>
 * 总延迟写进一个 int 的同步字段（原作每刻同步一次字符串），客户端按 {@code tickCount} 自己倒数（{@link #remainingTicks}）；
 * 中途才进入追踪范围的玩家靠生成包里的 {@code data} 字段对上已经过去的刻数（{@link #getAddEntityPacket} / {@link #recreateFromPacket}）。
 * 渲染见 {@code client/render/OrbitalStrikeRenderer}：原版信标光柱，品红，高到建筑上限，随剩余时间收细。
 */
public class OrbitalStrikeEntity extends Entity {

    /** 结算范围：中心 ±2.5 格的立方（原作 ±0.5 的盒子外扩 2） */
    public static final double REACH = 2.5D;
    /** 第几刻放充能声（原作 ticksExisted == 3） */
    public static final int CHARGE_SOUND_TICK = 3;
    /** 充能声音量（32 格） */
    public static final float CHARGE_SOUND_VOLUME = 2F;
    /** 伤害落下后着火几秒（原作 setFire(2)） */
    public static final int FIRE_SECONDS = 2;
    /** 落下时补的大爆炸粒子数 */
    public static final int IMPACT_PARTICLES = 20;
    /** 只出声光的爆炸的威力（原版爆炸半径 1 的声音和粒子） */
    public static final float IMPACT_POWER = 1F;
    /** 跟随：落下前这么多刻定住（给被锁定的目标一个躲开的窗口，光柱也在这时停下） */
    public static final int FOLLOW_FREEZE_TICKS = 10;

    private static final EntityDataAccessor<Integer> DATA_DELAY =
            SynchedEntityData.defineId(OrbitalStrikeEntity.class, EntityDataSerializers.INT);
    /** 展示厅冻结时显示的剩余刻数；-1 = 照常倒数 */
    private static final EntityDataAccessor<Integer> DATA_FROZEN =
            SynchedEntityData.defineId(OrbitalStrikeEntity.class, EntityDataSerializers.INT);

    private float damage;
    private float playerMultiplier = 1F;
    @Nullable
    private LivingEntity attacker;
    /** 放下时带了攻击者：到点时攻击者不在了就熄灭 */
    private boolean expectsAttacker;
    private Predicate<? super LivingEntity> affects = living -> false;
    /** 由 {@link #spawn} 放下的才会结算（别的途径造出来的下一刻就消失） */
    private boolean armed;
    private boolean fired;
    /** 跟着谁走（服务端；没锁定、目标没了以后为 null） */
    @Nullable
    private Entity follow;

    public OrbitalStrikeEntity(EntityType<? extends OrbitalStrikeEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /**
     * 在 pos 放下一记轨道打击（服务端）。
     *
     * @param damage           每个目标挨的伤害（≥ 0）
     * @param playerMultiplier 目标是玩家时伤害再乘这个（≥ 0；乘出来是 0 的玩家直接跳过）
     * @param delayTicks       多少刻后落下（至少 1）
     * @param attacker         伤害记在谁头上；给了的话到点时它不在了就熄灭
     * @param affects          打哪些生物（攻击者本人、旁观者另外一律跳过）
     * @return 放下的实体；生成被别的模组取消时返回 null
     */
    @Nullable
    public static OrbitalStrikeEntity spawn(ServerLevel level, Vec3 pos, float damage, float playerMultiplier, int delayTicks,
                                            @Nullable LivingEntity attacker, Predicate<? super LivingEntity> affects) {
        return spawn(level, pos, damage, playerMultiplier, delayTicks, attacker, affects, null);
    }

    /**
     * 同 {@link #spawn(ServerLevel, Vec3, float, float, int, LivingEntity, Predicate)}，另给一个要跟着走的实体（见类注释「跟随」）。
     *
     * @param follow 打击点跟着它水平移动，落下前 {@value #FOLLOW_FREEZE_TICKS} 刻定住；null = 不跟（同老的重载）
     */
    @Nullable
    public static OrbitalStrikeEntity spawn(ServerLevel level, Vec3 pos, float damage, float playerMultiplier, int delayTicks,
                                            @Nullable LivingEntity attacker, Predicate<? super LivingEntity> affects, @Nullable Entity follow) {
        Objects.requireNonNull(affects, "affects");
        OrbitalStrikeEntity strike = new OrbitalStrikeEntity(ECHoloWeapons.ORBITAL_STRIKE.get(), level);
        strike.moveTo(pos.x, pos.y, pos.z, 0F, 0F);
        strike.damage = Float.isFinite(damage) ? Math.max(0F, damage) : 0F;
        strike.playerMultiplier = Float.isFinite(playerMultiplier) ? Math.max(0F, playerMultiplier) : 0F;
        strike.entityData.set(DATA_DELAY, Math.max(1, delayTicks));
        strike.attacker = attacker;
        strike.expectsAttacker = attacker != null;
        strike.affects = affects;
        strike.follow = follow;
        strike.armed = true;
        return level.addFreshEntity(strike) ? strike : null;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_DELAY, 60);
        entityData.define(DATA_FROZEN, -1);
    }

    // ------------------------------------------------------------------ 每刻

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || isRemoved()) {
            return;
        }
        if (!armed) {
            discard();
            return;
        }
        if (entityData.get(DATA_FROZEN) >= 0) {
            return;
        }
        int delay = delayTicks();
        followStep(delay);
        if (tickCount == Math.min(CHARGE_SOUND_TICK, delay)) {
            level.playSound(null, getX(), getY(), getZ(), ECHoloWeapons.SOUND_ORBITAL_STRIKE.get(), soundSource(),
                    CHARGE_SOUND_VOLUME, 1F);
        }
        if (tickCount >= delay) {
            detonate();
        }
    }

    /**
     * 跟随的一步（每刻在倒数之前调）：落下前 {@value #FOLLOW_FREEZE_TICKS} 刻以内不再跟；被跟的实体没了、不在同一个世界了就放开（停在原地）。
     */
    private void followStep(int delay) {
        Entity target = follow;
        if (target == null) {
            return;
        }
        if (target.isRemoved() || !target.isAlive() || target.level() != level()) {
            follow = null;
            return;
        }
        if (tickCount >= delay - FOLLOW_FREEZE_TICKS) {
            follow = null;
            return;
        }
        if (target.getX() != getX() || target.getZ() != getZ()) {
            setPos(target.getX(), getY(), target.getZ());
        }
    }

    /** 立刻落下（同到点；GameTest、展示厅用）。只结算一次，之后实体消失 */
    public void detonate() {
        detonate(List.of());
    }

    /**
     * 同 {@link #detonate()}，另把 extra 里的活体也当候选：GameTest 里的真玩家不在世界的实体列表里，查不出来。
     * 候选照样要在立方内、在同一个世界、过同样的筛选。
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
            resolve(level, extra);
        } finally {
            discard();
        }
    }

    private void resolve(ServerLevel level, Collection<? extends LivingEntity> extra) {
        Vec3 center = position();
        AABB cube = new AABB(center, center).inflate(REACH);
        for (LivingEntity target : targets(level, cube, extra)) {
            float amount = target instanceof Player ? damage * playerMultiplier : damage;
            if (!(amount > 0F) || !target.isAlive()) {
                continue;
            }
            float before = target.getHealth() + target.getAbsorptionAmount();
            // 每个目标一个新的伤害源实例：RayHitGate 按（伤害源实例, 目标）认这一下
            RayHitGate.Watch watch = HoloStrikeEvents.strike(target, ECDamageTypes.orbitalStrike(level, attacker), amount);
            if (watch.landed() && target.getHealth() + target.getAbsorptionAmount() < before) {
                target.setSecondsOnFire(FIRE_SECONDS);
            }
        }
        ECProtection.safeExplode(level, this, ECDamageTypes.orbitalStrike(level, attacker), center, IMPACT_POWER, living -> false);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, IMPACT_PARTICLES, 0.5D, 0.5D, 0.5D, 0D);
    }

    private List<LivingEntity> targets(ServerLevel level, AABB cube, Collection<? extends LivingEntity> extra) {
        List<LivingEntity> found = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, cube, this::eligible));
        for (LivingEntity candidate : extra) {
            if (candidate.level() == level && !found.contains(candidate) && candidate.getBoundingBox().intersects(cube) && eligible(candidate)) {
                found.add(candidate);
            }
        }
        return found;
    }

    private boolean eligible(LivingEntity living) {
        return living.isAlive() && living != attacker && !living.isSpectator() && affects.test(living);
    }

    private boolean attackerPresent(ServerLevel level) {
        LivingEntity source = attacker;
        return source != null && !source.isRemoved() && source.isAlive() && source.level() == level;
    }

    private SoundSource soundSource() {
        return attacker instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE;
    }

    // ------------------------------------------------------------------ 读数（两端）

    /** 总延迟（刻） */
    public int delayTicks() {
        return entityData.get(DATA_DELAY);
    }

    /** 还剩多少刻落下（客户端按 tickCount 倒数；展示厅冻结时是冻结的值） */
    public float remainingTicks(float partialTick) {
        int frozen = entityData.get(DATA_FROZEN);
        if (frozen >= 0) {
            return frozen;
        }
        return Math.max(0F, delayTicks() - (tickCount + partialTick));
    }

    /** 攻击者（服务端；客户端恒为 null） */
    @Nullable
    public LivingEntity attacker() {
        return attacker;
    }

    public float damage() {
        return damage;
    }

    public float playerMultiplier() {
        return playerMultiplier;
    }

    /** 还在跟着走的实体（服务端；没锁定、已经定住、目标没了时为 null） */
    @Nullable
    public Entity follow() {
        return follow;
    }

    /**
     * 展示厅用：停在「还剩 remainingTicks 刻」不再倒数、不落下，客户端照这个值画光柱；传负数恢复倒数（服务端调）。
     */
    public void setShowroomFreeze(int remainingTicks) {
        entityData.set(DATA_FROZEN, remainingTicks < 0 ? -1 : remainingTicks);
    }

    // ------------------------------------------------------------------ 同步

    /** 生成包的 data 字段带上已经过去的刻数，中途进入追踪范围的客户端照样倒数得准 */
    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this, tickCount);
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        tickCount = Math.max(0, packet.getData());
    }

    /** 光柱高到建筑上限，视锥剔除按整根光柱算 */
    @Override
    public AABB getBoundingBoxForCulling() {
        double top = Math.max(getY() + 1D, level().getMaxBuildHeight());
        return new AABB(getX() - 0.5D, getY(), getZ() - 0.5D, getX() + 0.5D, top, getZ() + 0.5D);
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
