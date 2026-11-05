package com.flameforge.essentialcraft5.entity.mob;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.entity.anim.MobAnimStates;
import com.flameforge.essentialcraft5.registry.ECNetherEndMobs;
import com.flameforge.essentialcraft5.registry.ECNetherEndMobs.Config.ImpValues;
import com.flameforge.essentialcraft5.util.SpawnOrigin;
import com.flameforge.essentialcraft5.util.SpawnOrigin.Origin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;

/**
 * 炼狱小鬼 {@code nether_imp}（第十三批 13c-M3，batch13.md §5.2）：下界荒地、灵魂沙峡谷里会飞的小怪。
 * <ul>
 *   <li><b>不规则飞</b>：有目标时每 15~35 刻在目标周围 4~9 格、高出 1.5~4.5 格的地方另挑一点飞过去（只挑直线飞得到、放得下自己的点），
 *       一直绕着人换位置；没目标时在附近慢慢飘，不飘到离地 6 格以上。</li>
 *   <li><b>余火弹</b>：看得见、{@code shoot_range} 格以内时每 {@code shoot_interval} 刻（±10）吐一颗 {@link NetherImpEmberBoltEntity}，
 *       伤害取自己的攻击属性（{@code bolt_damage}）。没有预警的普通弹射物，照常吃护甲，落地不放火。</li>
 *   <li><b>残血逃</b>：生命剩到 {@code flee_health} 以下时往背离目标的方向、往上逃 {@code flee_ticks} 刻，逃的时候不出手；
 *       逃完 {@code flee_cooldown} 刻以内不会再逃，回来接着打。</li>
 *   <li><b>精英「小鬼头目」</b>：一次连发 {@code elite_burst} 颗；看见目标时停下来叫帮手（1 秒），在身边放出普通小鬼，
 *       它叫来的、还活着的不超过 {@code elite_summon_count} 只，冷却 {@code elite_summon_cooldown} 刻。叫来的来源记「召唤」
 *       （不公平：不掉关键材料、不出精英）、不给经验，身上记着头目的 UUID（{@value #TAG_CHIEFTAIN}）。</li>
 * </ul>
 * 生命、护甲、余火弹伤害在 {@link #finalizeSpawn} 里按配置设成基础值（精英修饰符在那之后由基类挂）。火焰免疫（实体类型上标的），
 * 灵魂能收（{@code soul_capturable}），精英不能收（基类规矩）。动画三段：普通出手、头目连发、叫帮手（{@link #anims}）。
 */
public class NetherImpEntity extends ECFlyingMonster {

    public static final int ANIM_CAST = 0;
    public static final int ANIM_BURST = 1;
    public static final int ANIM_SUMMON = 2;

    /** 叫来的小鬼身上记头目 UUID 的持久数据键 */
    public static final String TAG_CHIEFTAIN = EssentialCraft5.MODID + ":imp_chieftain";
    /** 连发两颗之间隔几刻 */
    public static final int BURST_GAP = 6;
    /** 出手动画抬手到甩出去那一下的刻数（第一颗在这之后发） */
    public static final int WIND_UP = 4;
    /** 叫帮手要停下来念多少刻 */
    public static final int SUMMON_CAST_TICKS = 20;
    /** 数「自己叫来的」小鬼时看多大范围 */
    public static final int MINION_RADIUS = 32;
    /** 没目标时最多飘到离地几格 */
    public static final int WANDER_MAX_ALTITUDE = 6;

    private static final String TAG_FLEE_COOLDOWN = "flee_cooldown";
    private static final String TAG_SUMMON_COOLDOWN = "summon_cooldown";

    public final MobAnimStates anims = new MobAnimStates(3).channel(ANIM_CAST, 1).channel(ANIM_BURST, 1).channel(ANIM_SUMMON, 1);

    private int shootCooldown = 20;
    private int burstLeft;
    private int burstDelay;
    private int fleeCooldown;
    private int summonCooldown;
    private int summonCast;
    private boolean fleeing;

    public NetherImpEntity(EntityType<? extends NetherImpEntity> type, Level level) {
        super(type, level);
        this.xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        ImpValues defaults = ECNetherEndMobs.Config.IMP_DEFAULTS;
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, defaults.maxHealth())
                .add(Attributes.ARMOR, defaults.armor())
                .add(Attributes.ATTACK_DAMAGE, defaults.boltDamage())
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FLYING_SPEED, 0.6D)
                .add(Attributes.FOLLOW_RANGE, 24D);
    }

    /** 自然生成的判定：同烈焰人，不看亮度（附近数量上限在注册时另包一层） */
    public static boolean checkImpSpawnRules(EntityType<NetherImpEntity> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos,
                                             RandomSource random) {
        return Monster.checkAnyLightMonsterSpawnRules(type, level, reason, pos, random);
    }

    // ================================================================== 生成

    /** 生命、护甲、余火弹伤害按配置设成基础值、回满（生成时调；精英修饰符在这之后挂） */
    public void applyConfiguredAttributes() {
        ImpValues cfg = ECNetherEndMobs.Config.imp();
        setBase(Attributes.MAX_HEALTH, cfg.maxHealth());
        setBase(Attributes.ARMOR, cfg.armor());
        setBase(Attributes.ATTACK_DAMAGE, cfg.boltDamage());
        setHealth(getMaxHealth());
    }

    private void setBase(Attribute attribute, double value) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData spawnData, @Nullable CompoundTag dataTag) {
        applyConfiguredAttributes();
        return super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
    }

    // ================================================================== AI

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FleeGoal());
        goalSelector.addGoal(2, new AttackGoal());
        goalSelector.addGoal(7, new WanderGoal());
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, NetherImpEntity.class).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (fleeCooldown > 0) {
            fleeCooldown--;
        }
        if (summonCooldown > 0) {
            summonCooldown--;
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide && random.nextInt(isElite() ? 2 : 5) == 0) {
            level().addParticle(ParticleTypes.SMALL_FLAME, getRandomX(0.4D), getY(0.55D), getRandomZ(0.4D), 0D, 0.01D, 0D);
        }
    }

    /** 这时该不该逃：开着逃、不在冷却、有目标、生命到线以下 */
    public boolean wantsToFlee() {
        double threshold = ECNetherEndMobs.Config.imp().fleeHealth();
        return threshold > 0D && fleeCooldown <= 0 && getTarget() != null && getHealth() <= getMaxHealth() * threshold;
    }

    /** 正在逃（逃的时候不出手） */
    public boolean isFleeing() {
        return fleeing;
    }

    /** 逃往哪：背离 from 的方向左右 45° 以内、8~14 格外、往上 2~5 格，直线飞得过去的点；找不到返回 null */
    @Nullable
    public Vec3 fleeDestination(LivingEntity from) {
        Vec3 away = position().subtract(from.position()).multiply(1D, 0D, 1D);
        if (away.lengthSqr() < 1.0E-4D) {
            away = Vec3.directionFromRotation(0F, getYRot());
        }
        away = away.normalize();
        for (int i = 0; i < 8; i++) {
            Vec3 dir = away.yRot((float) ((random.nextDouble() - 0.5D) * Math.PI / 2D));
            Vec3 dest = position().add(dir.scale(8D + random.nextDouble() * 6D)).add(0D, 2D + random.nextDouble() * 3D, 0D);
            if (canFlyTo(dest)) {
                return dest;
            }
        }
        return null;
    }

    /** 打的时候换到哪：目标周围 4~9 格（从现在的方位偏开最多 90°）、比目标脚下高 1.5~4.5 格，直线飞得过去的点 */
    @Nullable
    public Vec3 strafePoint(LivingEntity target) {
        double current = Math.atan2(getZ() - target.getZ(), getX() - target.getX());
        for (int i = 0; i < 6; i++) {
            double angle = current + (random.nextDouble() - 0.5D) * Math.PI;
            double radius = 4D + random.nextDouble() * 5D;
            Vec3 dest = new Vec3(target.getX() + Math.cos(angle) * radius, target.getY() + 1.5D + random.nextDouble() * 3D,
                    target.getZ() + Math.sin(angle) * radius);
            if (canFlyTo(dest)) {
                return dest;
            }
        }
        return null;
    }

    /** 直线飞得过去：终点区块已加载、放得下自己、两点之间没有方块挡着 */
    public boolean canFlyTo(Vec3 dest) {
        if (!level().isLoaded(BlockPos.containing(dest))) {
            return false;
        }
        AABB box = getBoundingBox().move(dest.subtract(position()));
        if (!level().noCollision(this, box) || level().containsAnyLiquid(box)) {
            return false;
        }
        double mid = getBbHeight() / 2D;
        return level().clip(new ClipContext(position().add(0D, mid, 0D), dest.add(0D, mid, 0D), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    // ================================================================== 出手

    /** 朝 target 吐一颗余火弹（服务端，散布比烈焰人小）；返回发出去的那颗，世界不收时返回 null */
    @Nullable
    public NetherImpEmberBoltEntity shootAt(LivingEntity target) {
        double dx = target.getX() - getX();
        double dy = target.getY(0.5D) - (getEyeY() - 0.15D);
        double dz = target.getZ() - getZ();
        double spread = Math.sqrt(Math.sqrt(dx * dx + dy * dy + dz * dz)) * 0.15D;
        NetherImpEmberBoltEntity bolt = new NetherImpEmberBoltEntity(level(), this, random.triangle(dx, spread), dy,
                random.triangle(dz, spread), (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
        playSound(ECNetherEndMobs.SOUND_IMP_SHOOT.get(), 0.8F, 1F + (random.nextFloat() - 0.5F) * 0.2F);
        return level().addFreshEntity(bolt) ? bolt : null;
    }

    /** 叫来的小鬼的头目 UUID；不是叫来的返回 null */
    @Nullable
    public UUID chieftain() {
        CompoundTag data = getPersistentData();
        return data.hasUUID(TAG_CHIEFTAIN) ? data.getUUID(TAG_CHIEFTAIN) : null;
    }

    /** {@value #MINION_RADIUS} 格以内它叫来的、还活着的小鬼有几只 */
    public int minionCount() {
        UUID self = getUUID();
        return level().getEntitiesOfClass(NetherImpEntity.class, getBoundingBox().inflate(MINION_RADIUS),
                imp -> imp != this && imp.isAlive() && self.equals(imp.chieftain())).size();
    }

    /**
     * 头目叫帮手（服务端）：在身边放出普通小鬼，补到 {@code elite_summon_count} 只为止。来源先记成「召唤」再走 Forge 的生成事件，
     * 被别的模组 / 插件取消的不算、不留任何东西。不是精英、或配置是 0 时什么都不做。返回这次放成几只。
     */
    public int summonMinions(@Nullable LivingEntity target) {
        if (!(level() instanceof ServerLevel server) || !isElite()) {
            return 0;
        }
        int want = ECNetherEndMobs.Config.imp().eliteSummonCount() - minionCount();
        int spawned = 0;
        for (int i = 0; i < want; i++) {
            NetherImpEntity imp = ECNetherEndMobs.NETHER_IMP.get().create(server);
            if (imp == null) {
                break;
            }
            Vec3 at = minionSpot(imp);
            if (at == null) {
                continue;
            }
            imp.moveTo(at.x, at.y, at.z, getYRot(), 0F);
            imp.getPersistentData().putUUID(TAG_CHIEFTAIN, getUUID());
            SpawnOrigin.set(imp, Origin.SUMMONED);
            ForgeEventFactory.onFinalizeSpawn(imp, server, server.getCurrentDifficultyAt(imp.blockPosition()), MobSpawnType.MOB_SUMMONED, null, null);
            if (imp.isSpawnCancelled()) {
                continue;
            }
            if (target != null) {
                imp.setTarget(target);
            }
            if (server.addFreshEntity(imp)) {
                spawned++;
                server.sendParticles(ParticleTypes.FLAME, imp.getX(), imp.getY(0.5D), imp.getZ(), 8, 0.2D, 0.3D, 0.2D, 0.02D);
            }
        }
        if (spawned > 0) {
            playSound(ECNetherEndMobs.SOUND_IMP_SUMMON.get(), 1F, 1F);
        }
        return spawned;
    }

    /** 叫来的那只放在哪：身边 1~2 格、稍高一点，放得下它的点 */
    @Nullable
    private Vec3 minionSpot(NetherImpEntity imp) {
        for (int i = 0; i < 8; i++) {
            double angle = random.nextDouble() * Math.PI * 2D;
            double radius = 1D + random.nextDouble();
            Vec3 at = new Vec3(getX() + Math.cos(angle) * radius, getY() + random.nextDouble(), getZ() + Math.sin(angle) * radius);
            AABB box = imp.getType().getDimensions().makeBoundingBox(at);
            if (level().noCollision(imp, box) && !level().containsAnyLiquid(box)) {
                return at;
            }
        }
        return null;
    }

    // ================================================================== 杂项

    /** 叫来的小鬼不给经验（头目一直叫，困住它就成了经验塔） */
    @Override
    public int getExperienceReward() {
        return chieftain() != null ? 0 : super.getExperienceReward();
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (!anims.handle(id, tickCount)) {
            super.handleEntityEvent(id);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ECNetherEndMobs.SOUND_IMP_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ECNetherEndMobs.SOUND_IMP_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ECNetherEndMobs.SOUND_IMP_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 0.8F;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt(TAG_FLEE_COOLDOWN, fleeCooldown);
        tag.putInt(TAG_SUMMON_COOLDOWN, summonCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        fleeCooldown = Math.max(0, tag.getInt(TAG_FLEE_COOLDOWN));
        summonCooldown = Math.max(0, tag.getInt(TAG_SUMMON_COOLDOWN));
    }

    // ================================================================== 目标

    /** 残血逃：见类注释 */
    private final class FleeGoal extends Goal {
        private int ticks;
        private int repick;

        FleeGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return wantsToFlee();
        }

        @Override
        public boolean canContinueToUse() {
            return ticks > 0 && isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            ticks = ECNetherEndMobs.Config.imp().fleeTicks();
            repick = 0;
            fleeing = true;
            burstLeft = 0;
            summonCast = 0;
            playSound(ECNetherEndMobs.SOUND_IMP_FLEE.get(), 1F, 1F);
        }

        @Override
        public void tick() {
            ticks--;
            LivingEntity from = getTarget();
            if (from != null && (--repick <= 0 || !getMoveControl().hasWanted())) {
                repick = 20;
                Vec3 dest = fleeDestination(from);
                if (dest != null) {
                    getMoveControl().setWantedPosition(dest.x, dest.y, dest.z, 1.6D);
                }
            }
        }

        @Override
        public void stop() {
            ticks = 0;
            fleeing = false;
            fleeCooldown = ECNetherEndMobs.Config.imp().fleeCooldown();
        }
    }

    /** 绕着目标换位置、吐余火弹；头目另外连发、叫帮手 */
    private final class AttackGoal extends Goal {
        private int repositionTicks;

        AttackGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            return target != null && target.isAlive() && canAttack(target);
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            repositionTicks = 0;
        }

        @Override
        public void stop() {
            burstLeft = 0;
            summonCast = 0;
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target == null) {
                return;
            }
            getLookControl().setLookAt(target, 30F, 30F);
            ImpValues cfg = ECNetherEndMobs.Config.imp();
            if (summonCast > 0) {
                getMoveControl().setWantedPosition(getX(), getY(), getZ(), 0.3D);
                if (--summonCast == 0) {
                    summonMinions(target);
                }
                return;
            }
            if (--repositionTicks <= 0) {
                Vec3 dest = strafePoint(target);
                if (dest != null) {
                    getMoveControl().setWantedPosition(dest.x, dest.y, dest.z, 1D);
                }
                repositionTicks = 15 + random.nextInt(21);
            }
            if (burstLeft > 0) {
                if (--burstDelay <= 0) {
                    shootAt(target);
                    burstLeft--;
                    burstDelay = BURST_GAP;
                }
                return;
            }
            boolean sees = getSensing().hasLineOfSight(target);
            if (isElite() && sees && summonCooldown <= 0 && cfg.eliteSummonCount() > 0) {
                if (minionCount() < cfg.eliteSummonCount()) {
                    summonCast = SUMMON_CAST_TICKS;
                    summonCooldown = cfg.eliteSummonCooldown();
                    anims.play(NetherImpEntity.this, ANIM_SUMMON);
                    return;
                }
                summonCooldown = 40;
            }
            double range = cfg.shootRange();
            if (--shootCooldown <= 0 && sees && distanceToSqr(target) <= range * range) {
                burstLeft = isElite() ? cfg.eliteBurst() : 1;
                burstDelay = WIND_UP;
                anims.play(NetherImpEntity.this, burstLeft > 1 ? ANIM_BURST : ANIM_CAST);
                shootCooldown = cfg.shootInterval() + random.nextInt(21) - 10;
            }
        }
    }

    /** 没目标时在附近慢慢飘（同恶魂的随机漂浮，飘的点要离地不远） */
    private final class WanderGoal extends Goal {

        WanderGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return getTarget() == null && !getMoveControl().hasWanted() && random.nextInt(reducedTickDelay(30)) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            for (int i = 0; i < 6; i++) {
                Vec3 dest = position().add((random.nextDouble() - 0.5D) * 12D, (random.nextDouble() - 0.5D) * 4D, (random.nextDouble() - 0.5D) * 12D);
                if (canFlyTo(dest) && nearGround(dest)) {
                    getMoveControl().setWantedPosition(dest.x, dest.y, dest.z, 0.6D);
                    return;
                }
            }
        }

        private boolean nearGround(Vec3 dest) {
            BlockPos.MutableBlockPos pos = BlockPos.containing(dest).mutable();
            for (int i = 0; i <= WANDER_MAX_ALTITUDE; i++) {
                if (!level().getBlockState(pos).getCollisionShape(level(), pos).isEmpty()) {
                    return true;
                }
                pos.move(0, -1, 0);
            }
            return false;
        }
    }
}
