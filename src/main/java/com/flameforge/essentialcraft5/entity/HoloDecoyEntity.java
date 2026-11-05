package com.flameforge.essentialcraft5.entity;

import com.flameforge.essentialcraft5.registry.ECHoloDecoy;
import com.flameforge.essentialcraft5.registry.ECHologram;
import com.flameforge.essentialcraft5.util.ECEntityRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 全息诱饵（第十批 10b，batch10.md §5.8.2；原作没有）：全息诱饵投影仪投出的使用者分身，活 {@code general.holo_decoy.lifetime_ticks} 刻（默认 160）。
 * <ul>
 *   <li><b>骗谁</b>：诱饵周围 {@code radius}（默认 16）格内的敌对生物（{@code Mob} 而且是 {@link Enemy}），正盯着主人或和主人同队的玩家的，改去打诱饵。
 *       跳过 {@code ECEntityRules.isSpared}（起了名字的、拴着绳的……）和 {@code #essentialcraft5:decoy_immune}（默认监守者）。
 *       投出来那一刻扫一遍，之后每 {@value #SCAN_INTERVAL} 刻扫一遍（33³ 的实体查询 10 刻一次，policy §1.2「更大的至少 10 刻一次」）；
 *       中间想换回主人的（被主人打了一下、目标技能重新挑人）在 {@code LivingChangeTargetEvent} 里改成诱饵（{@code HoloDecoyEvents}，照 ψ 指环的写法）。</li>
 *   <li><b>只改敌对生物的目标</b>：不碰玩家、不碰任何人的宠物、村民；别的玩家被怪盯着照旧（除非他和主人同队）。</li>
 *   <li><b>Boss</b>（{@code #forge:bosses}）：每只首领每 {@code max(cooldown_ticks, boss_ticks)} 刻（默认 600）最多被诱饵骗 {@code boss_ticks}（默认 60）刻，
 *       几个人的诱饵共用这一份（{@link #BOSS_WINDOW}：按首领记窗口开始的刻数）——各算各的话，同队几个人错开投放就能接力一直骗着。
 *       窗口到点，每个正被它盯着的诱饵都把目标还给原来盯着的人（还在、同一个世界里的话）；放回时发的换目标事件里，队友的诱饵也接不走。
 *       {@code boss_ticks} 为 0 时 Boss 不上当。</li>
 *   <li><b>换目标的写法</b>：靠目标技能（goal）找人的怪调 {@code setTarget}（会发 {@code LivingChangeTargetEvent}，别的模组可以拦）；
 *       靠记忆（brain）找人的怪（猪灵、疣猪兽、监守者这类，目标存在记忆 {@code ATTACK_TARGET} 里）照原版行为树的口径先发一次
 *       {@code LivingChangeTargetEvent}（BEHAVIOR_TARGET），没被拦才改记忆。</li>
 *   <li><b>消失</b>：到时间、主人下线 / 换维度 / 死了（主人的引用被移除、不活了、不在同一个世界）、主人再投一个新的（每人同时一个），
 *       都当场移除；被打掉（20 点生命）也就没了。不存盘、不能 /summon（注册时 {@code noSave().noSummon()}，这里另外让 {@link #shouldBeSaved} 恒为假）。</li>
 *   <li><b>本身</b>：不掉东西、不给经验、不被凋灵打死时种凋灵玫瑰、推不动、不吃药水效果、不怕火、不受摔落伤害；
 *       主人和同队玩家打不动它（横扫、范围武器不会误伤自己的分身）。服务端装备格一直是空的，客户端只画主人的皮肤（{@code client/render/HoloDecoyRenderer}）。</li>
 * </ul>
 * 每名主人的诱饵登记在 {@link #ACTIVE} 里（只在服务端线程读写），{@code LivingChangeTargetEvent} 没有诱饵在场时第一行就返回。
 */
public class HoloDecoyEntity extends LivingEntity {

    /** 诱饵的生命（20，同玩家） */
    public static final double MAX_HEALTH = 20D;
    /** 每隔几刻重新扫一遍周围的怪 */
    public static final int SCAN_INTERVAL = 10;
    /** 落点往下找地面最多找几格（准星对着半空、墙面时往下落） */
    public static final int FLOOR_SEARCH = 4;

    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER =
            SynchedEntityData.defineId(HoloDecoyEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    /** 每名主人现在的诱饵（服务端；值是弱引用，诱饵移除时自己摘掉） */
    private static final Map<UUID, WeakReference<HoloDecoyEntity>> ACTIVE = new HashMap<>();
    /**
     * 每只首领被骗的窗口：值是窗口开始时的 {@code gameTime}（各维度共用），所有诱饵共用这一份（只在服务端线程读写；
     * 首领没了键自己回收，关服时清空）。窗口开始后 {@code boss_ticks} 刻内骗得了，{@code max(cooldown_ticks, boss_ticks)} 刻以后才开下一个
     */
    private static final Map<Mob, Long> BOSS_WINDOW = new WeakHashMap<>();

    /** 主人（服务端；诱饵不存盘，所以不用存 UUID 再找人） */
    @Nullable
    private Player owner;
    /** 活多少刻（生成时按配置定下来，之后改配置不影响已经在场的） */
    private int lifetime = ECHoloDecoy.Config.DEFAULTS.lifetimeTicks();
    private int radius = ECHoloDecoy.Config.DEFAULTS.radius();
    private int bossTicks = ECHoloDecoy.Config.DEFAULTS.bossTicks();
    /** 同一只首领两个被骗窗口的间隔：max(冷却, boss_ticks)，生成时定下来 */
    private int bossPeriod = Math.max(ECHoloDecoy.Config.DEFAULTS.cooldownTicks(), ECHoloDecoy.Config.DEFAULTS.bossTicks());
    /** 这个诱饵骗过的 Boss：按 UUID 记原来盯着的人（到点还给他）；计时看共用的 {@link #BOSS_WINDOW} */
    private final Map<UUID, BossLure> bosses = new HashMap<>();
    /** 已经登记进 {@link #ACTIVE}、开始骗怪了（付完钱才开始） */
    private boolean active;

    /** 一只 Boss 被这个诱饵骗的记录 */
    private static final class BossLure {
        final WeakReference<Mob> boss;
        @Nullable
        final WeakReference<LivingEntity> original;
        boolean released;

        BossLure(Mob boss, @Nullable LivingEntity original) {
            this.boss = new WeakReference<>(boss);
            this.original = original == null ? null : new WeakReference<>(original);
        }
    }

    public HoloDecoyEntity(EntityType<? extends HoloDecoyEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        // 击退抗性 1：挨打时原地不动（「推不动」）
        return LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_OWNER, Optional.empty());
    }

    // ------------------------------------------------------------------ 投出

    /**
     * 找一个能站的落点（两端都能算：客户端据此决定要不要挥手）：从眼睛沿视线 range 格找第一个方块（{@code COLLIDER}，不认液体），
     * 打中顶面就站在那；打中侧面先退回 0.4 格；什么都没打中就从 range 格那一点往下找。往下最多找 {@value #FLOOR_SEARCH} 格地面，
     * 落点上诱饵的碰撞箱（0.6 × 1.8）里不能有方块，还要在世界边界以内。找不到返回 null。
     */
    @Nullable
    public static Vec3 findSpot(Level level, Player player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 point;
        if (hit.getType() == HitResult.Type.BLOCK) {
            point = hit.getDirection() == Direction.UP ? hit.getLocation() : hit.getLocation().subtract(look.scale(0.4D));
        } else {
            point = end;
        }
        BlockPos start = BlockPos.containing(point.x, point.y + 0.01D, point.z);
        for (int dy = 0; dy <= FLOOR_SEARCH; dy++) {
            BlockPos feet = start.below(dy);
            if (!level.isLoaded(feet)) {
                return null;
            }
            VoxelShape here = level.getBlockState(feet).getCollisionShape(level, feet);
            double top;
            if (!here.isEmpty()) {
                // 这一格本身有东西（台阶、雪层、地毯）：站在它顶上；比准星那一点高出半格以上说明是墙，不往里塞
                top = feet.getY() + here.max(Direction.Axis.Y);
                if (top > point.y + 0.6D) {
                    return null;
                }
            } else {
                BlockPos below = feet.below();
                BlockState belowState = level.getBlockState(below);
                VoxelShape floor = belowState.getCollisionShape(level, below);
                if (floor.isEmpty()) {
                    continue;
                }
                top = below.getY() + floor.max(Direction.Axis.Y);
            }
            Vec3 spot = new Vec3(point.x, top, point.z);
            AABB box = ECHoloDecoy.HOLO_DECOY.get().getDimensions().makeBoundingBox(spot);
            if (!level.getWorldBorder().isWithinBounds(box) || !level.noCollision(box)) {
                return null;
            }
            return spot;
        }
        return null;
    }

    /**
     * 在 at 生成 owner 的诱饵（服务端），朝向照主人。<b>还没开始骗怪</b>：调用方付完钱再 {@link #activate()}；
     * 生成被别的模组取消、或者在加入事件里被直接移除时返回 null。
     */
    @Nullable
    public static HoloDecoyEntity spawn(ServerLevel level, Player owner, Vec3 at, ECHoloDecoy.Config.Values config) {
        HoloDecoyEntity decoy = ECHoloDecoy.HOLO_DECOY.get().create(level);
        if (decoy == null) {
            return null;
        }
        decoy.moveTo(at.x, at.y, at.z, owner.getYRot(), 0F);
        decoy.setYHeadRot(owner.getYRot());
        decoy.yBodyRot = owner.getYRot();
        decoy.owner = owner;
        decoy.entityData.set(DATA_OWNER, Optional.of(owner.getUUID()));
        decoy.lifetime = config.lifetimeTicks();
        decoy.radius = config.radius();
        decoy.bossTicks = config.bossTicks();
        decoy.bossPeriod = Math.max(config.cooldownTicks(), config.bossTicks());
        return level.addFreshEntity(decoy) && !decoy.isRemoved() ? decoy : null;
    }

    /**
     * 付完钱以后调：登记成主人现在的诱饵（主人原来的那个当场移除），放一点光和声音，马上扫一遍周围的怪。
     */
    public void activate() {
        if (level().isClientSide || owner == null || active) {
            return;
        }
        active = true;
        WeakReference<HoloDecoyEntity> previousRef = ACTIVE.put(owner.getUUID(), new WeakReference<>(this));
        HoloDecoyEntity previous = previousRef == null ? null : previousRef.get();
        if (previous != null && previous != this && !previous.isRemoved()) {
            previous.vanish();
        }
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1D, getZ(), 12, 0.3D, 0.6D, 0.3D, 0.02D);
        }
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6F, 1.6F);
        lure();
    }

    // ------------------------------------------------------------------ 每刻

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (!active || ownerGone() || tickCount >= lifetime) {
            vanish();
            return;
        }
        releaseExpiredBosses();
        if (tickCount % SCAN_INTERVAL == 0) {
            lure();
        }
    }

    /** 主人不在了：引用被移除（下线、死后重生换了新对象）、不活了、不在同一个世界 */
    private boolean ownerGone() {
        Player p = owner;
        return p == null || p.isRemoved() || !p.isAlive() || p.level() != level();
    }

    /** 消失：一点光，然后移除 */
    public void vanish() {
        if (isRemoved()) {
            return;
        }
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1D, getZ(), 6, 0.25D, 0.5D, 0.25D, 0.01D);
        }
        discard();
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (owner != null) {
            UUID id = owner.getUUID();
            WeakReference<HoloDecoyEntity> ref = ACTIVE.get(id);
            if (ref != null && (ref.get() == this || ref.get() == null)) {
                ACTIVE.remove(id);
            }
        }
    }

    // ------------------------------------------------------------------ 骗怪

    /** 扫一遍周围：正盯着主人或同队玩家的敌对生物改打诱饵 */
    public void lure() {
        if (!active || !isAlive() || level().isClientSide) {
            return;
        }
        double r = radius;
        AABB box = getBoundingBox().inflate(r);
        List<Mob> mobs = level().getEntitiesOfClass(Mob.class, box, mob -> mob instanceof Enemy && mob.isAlive() && distanceToSqr(mob) <= r * r);
        for (Mob mob : mobs) {
            LivingEntity target = currentTarget(mob);
            if (target == this || !(target instanceof Player player) || !protects(player) || !mayLure(mob, player)) {
                continue;
            }
            retarget(mob, this);
        }
    }

    /** 这名玩家在诱饵的保护里：是主人，或者和主人同队 */
    public boolean protects(Player player) {
        Player p = owner;
        if (p == null) {
            return false;
        }
        return player == p || player.getUUID().equals(p.getUUID()) || p.isAlliedTo(player);
    }

    /**
     * 能不能骗这只怪（不看它现在盯着谁）：不在 {@code decoy_immune}、不是 {@code isSpared}。Boss 看共用的窗口（{@link #BOSS_WINDOW}）：
     * 没有窗口、或者上一个窗口开始已经过了 {@code max(cooldown_ticks, boss_ticks)} 刻，就开一个新窗口；窗口开始后 {@code boss_ticks} 刻内为真，
     * 之后到下一个窗口之前谁的诱饵问都为假。为真时在本诱饵这里记下原来盯着的人（到点还给他）。
     */
    public boolean mayLure(Mob mob, @Nullable LivingEntity original) {
        if (mob.getType().is(ECHoloDecoy.DECOY_IMMUNE) || ECEntityRules.isSpared(mob)) {
            return false;
        }
        if (!isBoss(mob)) {
            return true;
        }
        if (bossTicks <= 0) {
            return false;
        }
        long now = level().getGameTime();
        Long start = BOSS_WINDOW.get(mob);
        if (start == null || now - start >= bossPeriod || now < start) {
            BOSS_WINDOW.put(mob, now);
        } else if (now - start >= bossTicks) {
            return false;
        }
        BossLure lure = bosses.get(mob.getUUID());
        if (lure == null || lure.released) {
            bosses.put(mob.getUUID(), new BossLure(mob, original));
        }
        return true;
    }

    /** 共用的窗口到点了：还盯着本诱饵的 Boss 把目标还给原来盯着的人（不在了就清空，让它自己重新找） */
    private void releaseExpiredBosses() {
        if (bosses.isEmpty()) {
            return;
        }
        long now = level().getGameTime();
        // 先标记、收集，循环外再换目标：换目标会发 LivingChangeTargetEvent，监听里会回头查窗口和这张表
        List<BossLure> expired = null;
        for (Iterator<BossLure> it = bosses.values().iterator(); it.hasNext(); ) {
            BossLure lure = it.next();
            if (lure.released) {
                continue;
            }
            Mob boss = lure.boss.get();
            if (boss == null || boss.isRemoved()) {
                it.remove();
                continue;
            }
            Long start = BOSS_WINDOW.get(boss);
            if (start != null && now >= start && now - start < bossTicks) {
                continue;
            }
            lure.released = true;
            if (expired == null) {
                expired = new ArrayList<>();
            }
            expired.add(lure);
        }
        if (expired == null) {
            return;
        }
        for (BossLure lure : expired) {
            Mob boss = lure.boss.get();
            if (boss != null && !boss.isRemoved() && currentTarget(boss) == this) {
                LivingEntity original = lure.original == null ? null : lure.original.get();
                boolean valid = original != null && original.isAlive() && !original.isRemoved() && original.level() == boss.level();
                retarget(boss, valid ? original : null);
            }
        }
    }

    public static boolean isBoss(Entity entity) {
        return entity.getType().is(Tags.EntityTypes.BOSSES);
    }

    /** 这只怪现在盯着谁：靠记忆找人的读 {@code ATTACK_TARGET}，其余读 {@code getTarget()} */
    @Nullable
    public static LivingEntity currentTarget(Mob mob) {
        Brain<?> brain = mob.getBrain();
        if (brain.checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)) {
            Optional<LivingEntity> memory = brain.getMemory(MemoryModuleType.ATTACK_TARGET);
            if (memory.isPresent()) {
                return memory.get();
            }
        }
        return mob.getTarget();
    }

    /**
     * 把怪的目标改成 target（可以是 null）。靠记忆找人的：先发一次 BEHAVIOR_TARGET 的 {@code LivingChangeTargetEvent}（同原版 StartAttacking），
     * 没被拦才写记忆；其余走 {@code setTarget}（它自己发 MOB_TARGET 的事件）。
     */
    public static void retarget(Mob mob, @Nullable LivingEntity target) {
        Brain<?> brain = mob.getBrain();
        if (brain.checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)) {
            if (target == null) {
                brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
                return;
            }
            LivingChangeTargetEvent event = ForgeHooks.onLivingChangeTarget(mob, target, LivingChangeTargetEvent.LivingTargetType.BEHAVIOR_TARGET);
            if (!event.isCanceled() && event.getNewTarget() != null) {
                brain.setMemory(MemoryModuleType.ATTACK_TARGET, event.getNewTarget());
            }
            return;
        }
        mob.setTarget(target);
    }

    // ------------------------------------------------------------------ 给事件监听用

    /** 有没有诱饵在场（{@code LivingChangeTargetEvent} 每刻都发很多次，没有诱饵时第一行就返回） */
    public static boolean noneActive() {
        return ACTIVE.isEmpty();
    }

    /**
     * mob 想去打 target（玩家）时，找一个应当把它骗过来的诱饵：同一个世界、怪在诱饵的半径内、target 在诱饵的保护里、这只怪骗得了。没有返回 null。
     */
    @Nullable
    public static HoloDecoyEntity lureFor(Mob mob, Player target) {
        if (ACTIVE.isEmpty() || !(mob instanceof Enemy)) {
            return null;
        }
        List<UUID> stale = null;
        HoloDecoyEntity found = null;
        for (Map.Entry<UUID, WeakReference<HoloDecoyEntity>> entry : ACTIVE.entrySet()) {
            HoloDecoyEntity decoy = entry.getValue().get();
            if (decoy == null || decoy.isRemoved()) {
                if (stale == null) {
                    stale = new ArrayList<>();
                }
                stale.add(entry.getKey());
                continue;
            }
            if (found != null || !decoy.active || !decoy.isAlive() || decoy.level() != mob.level()) {
                continue;
            }
            double r = decoy.radius;
            if (decoy.distanceToSqr(mob) <= r * r && decoy.protects(target) && decoy.mayLure(mob, target)) {
                found = decoy;
            }
        }
        if (stale != null) {
            stale.forEach(ACTIVE::remove);
        }
        return found;
    }

    /** 这名玩家现在的诱饵（服务端；没有时 null） */
    @Nullable
    public static HoloDecoyEntity of(Player player) {
        WeakReference<HoloDecoyEntity> ref = ACTIVE.get(player.getUUID());
        HoloDecoyEntity decoy = ref == null ? null : ref.get();
        return decoy == null || decoy.isRemoved() ? null : decoy;
    }

    /** 主人下线 / 换维度 / 死亡时当场移除他的诱饵（{@code HoloDecoyEvents}；tick 里也会查，这里是为了不等到下一刻） */
    public static void dismiss(Player player) {
        HoloDecoyEntity decoy = of(player);
        if (decoy != null) {
            decoy.vanish();
        }
    }

    /** 关服时清空登记表和首领窗口（单人游戏换存档时不带进下一个世界） */
    public static void clearAll() {
        ACTIVE.clear();
        BOSS_WINDOW.clear();
    }

    // ------------------------------------------------------------------ 查询

    /** 主人的 UUID（两端；客户端按它取皮肤） */
    @Nullable
    public UUID ownerId() {
        return entityData.get(DATA_OWNER).orElse(null);
    }

    /** 主人（服务端） */
    @Nullable
    public Player owner() {
        return owner;
    }

    public int lifetime() {
        return lifetime;
    }

    public boolean isActive() {
        return active;
    }

    // ------------------------------------------------------------------ 本身的规矩

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    /** 主人和同队玩家打不动它（横扫、范围武器不会误伤自己的分身） */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (super.isInvulnerableTo(source)) {
            return true;
        }
        return source.getEntity() instanceof Player player && !level().isClientSide && protects(player);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return false;
    }

    @Override
    public boolean isAffectedByPotions() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    protected void dropAllDeathLoot(DamageSource source) {
    }

    @Override
    protected void createWitherRose(@Nullable LivingEntity killer) {
    }

    @Override
    public boolean shouldDropExperience() {
        return false;
    }

    @Override
    protected @Nullable SoundEvent getHurtSound(DamageSource source) {
        return ECHologram.SOUND_DAMAGE_MELEE.get();
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return ECHologram.SOUND_SHUTDOWN.get();
    }

    @Override
    protected float getSoundVolume() {
        return 0.5F;
    }

    private static final NonNullList<ItemStack> NO_ARMOR = NonNullList.withSize(4, ItemStack.EMPTY);

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return NO_ARMOR;
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    /** 装备格一直是空的：别的模组、发射器给它穿戴什么都穿不上 */
    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }
}
