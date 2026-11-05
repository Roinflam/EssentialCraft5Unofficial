package com.flameforge.essentialcraft5.entity.mob.hoanna;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.entity.anim.MobAnimStates;
import com.flameforge.essentialcraft5.entity.mob.ECMonster;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesB;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesB.Config.EelValues;
import com.flameforge.essentialcraft5.util.SpawnOrigin;
import com.flameforge.essentialcraft5.util.SpawnOrigin.Origin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.TryFindWaterGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 渊鳗 {@code abyssal_eel}（第十三批 13d-N2，batch13.md §5.2、§6.5；怪物稿 §2.4.6）：海纳尔之海里灰蓝带发光斑纹的长鳗。
 * 只有头部一个碰撞箱，身体两节是画面（模型按游动摆尾）。
 * <ul>
 *   <li><b>水中移动</b>：照原版海豚——{@link SmoothSwimmingMoveControl} + {@link WaterBoundPathNavigation}，在水里按自己的速度游、不受浮力推；
 *       能在水下呼吸。离了水在地上扑腾着往水里跳（同原版鱼），每秒掉 {@code dry_damage}（{@code dry_out} 伤害），自己找水回去。</li>
 *   <li><b>只在水里打</b>：只盯在水里的玩家（或打了它的），自己在水里才咬；咬一口 {@code bite_damage}（普通近战，吃护甲）。</li>
 *   <li><b>下拽</b>：咬中技能目标后 {@code drag_ticks} 刻里把目标往下拉（每刻多往下 0.1，最快 {@code drag_speed} 格 / 刻）。
 *       只拽在水里的；目标脚下快到实心方块就不再往下拉（不把人拽进方块）；目标离它 {@value #DRAG_BREAK_RANGE} 格以外、
 *       或这一刻反而往上浮了（往上游、被气泡柱托起）就挣开了。</li>
 *   <li><b>精英「渊鳗群首」</b>：第一次挨打叫来 {@code elite_summon_count} 条渊鳗（来源「召唤」、不公平、不给经验，身上记着群首的 UUID），
 *       咬中另带缓慢 I {@code elite_slow_ticks} 刻。叫过没有存盘，读档不再叫。</li>
 *   <li><b>生成</b>（{@link #checkEelSpawnRules}，登记成 {@code IN_WATER} + 海底高度图，类别仍是 MONSTER：毒雾钩子 {@code FumeSpawns} 只取消水生 / 被动类别）：
 *       水里、这格所在的水柱至少 {@code min_depth} 格深、y 在 {@code min_y}~{@code max_y}；不看亮度（深海本来就暗）。64 格内上限由注册时外面那层管。</li>
 * </ul>
 * 基类仍是 {@link ECMonster}（来源、精英、伤害占比、技能目标照旧），只换了移动 / 寻路 / 呼吸。生命、护甲、咬的伤害在 {@link #finalizeSpawn} 里按配置设成基础值。
 */
public class AbyssalEelEntity extends ECMonster {

    public static final int ANIM_BITE = 0;
    public static final int ANIM_DRAG = 1;
    public static final int ANIM_CALL = 2;

    /** 叫来的渊鳗身上记群首 UUID 的持久数据键 */
    public static final String TAG_MATRIARCH = EssentialCraft5.MODID + ":eel_matriarch";
    private static final String TAG_CALLED = "pack_called";

    /** 下拽时目标离它多远就挣开（格） */
    public static final double DRAG_BREAK_RANGE = 4D;
    /** 下拽每刻往下加的速度（格 / 刻，直到 {@code drag_speed}） */
    public static final double DRAG_ACCEL = 0.1D;
    /** 目标这一刻比上一刻高出这么多就算往上挣开了（格） */
    public static final double DRAG_RISE_BREAK = 0.05D;
    /** 叫来的那几条放在身边多远以内（格） */
    public static final double CALL_RADIUS = 2.5D;

    public final MobAnimStates anims = new MobAnimStates(3).channel(ANIM_BITE, 1).channel(ANIM_DRAG, 2).channel(ANIM_CALL, 1);

    @Nullable
    private LivingEntity dragTarget;
    private int dragLeft;
    /** 上一刻看到的目标位置：玩家的位置由客户端报上来，服务端那份速度不跟着变，往下拽要按位置差算它实际在怎么动 */
    private Vec3 dragLastPos = Vec3.ZERO;
    private boolean packCalled;
    private int dryTicks;

    public AbyssalEelEntity(EntityType<? extends AbyssalEelEntity> type, Level level) {
        super(type, level);
        this.xpReward = 8;
        this.moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.02F, 0.1F, true);
        this.lookControl = new SmoothSwimmingLookControl(this, 10);
    }

    /** 属性默认值（生命 / 护甲照 batch13.md §5.2，其余照怪物稿数值表；生成时再按配置设） */
    public static AttributeSupplier.Builder createAttributes() {
        EelValues d = ECHoannaNativesB.Config.EEL_DEFAULTS;
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, d.maxHealth())
                .add(Attributes.ARMOR, d.armor())
                .add(Attributes.ATTACK_DAMAGE, d.biteDamage())
                .add(Attributes.MOVEMENT_SPEED, 1.4D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    // ================================================================== 生成

    /**
     * 自然生成的判定：非和平、这一格是水；刷怪笼来问只看这两条；自然生成另要 y 在范围内、水柱够深（{@link #placeAllowed}）。
     * 附近数量上限在注册时另包一层。
     */
    public static boolean checkEelSpawnRules(EntityType<AbyssalEelEntity> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos,
                                             RandomSource random) {
        if (level.getDifficulty() == Difficulty.PEACEFUL || !level.getFluidState(pos).is(FluidTags.WATER)) {
            return false;
        }
        return reason == MobSpawnType.SPAWNER || placeAllowed(level, pos, ECHoannaNativesB.Config.eel());
    }

    /**
     * 实体那一关（Forge {@code checkSpawnPosition} = {@code checkSpawnRules && checkSpawnObstruction}）：在水里不看亮度。
     * {@code Monster} 的走向价值在亮处为负，服主把 {@code min_depth} / {@code max_y} 调浅时白天的浅水会被拦下；水深默认值下本来也过得去。
     */
    @Override
    public boolean checkSpawnRules(LevelAccessor level, MobSpawnType reason) {
        if (level.getFluidState(blockPosition()).is(FluidTags.WATER)) {
            return true;
        }
        return super.checkSpawnRules(level, reason);
    }

    /**
     * 只看有没有别的实体挡着（同原版守卫者、溺尸）。{@code Mob} 默认还要求碰撞箱里没有液体——水里的怪不覆盖这个，
     * 自然刷怪每次都在这一步被拒，海纳尔之海一条渊鳗也刷不出来。
     */
    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        return level.isUnobstructed(this);
    }

    /** 只看位置的两条：y 在 {@code min_y}~{@code max_y}，这格所在的水柱至少 {@code min_depth} 格深 */
    public static boolean placeAllowed(BlockGetter level, BlockPos pos, EelValues cfg) {
        return inHeightRange(pos.getY(), cfg) && waterDepth(level, pos, cfg.minDepth()) >= cfg.minDepth();
    }

    public static boolean inHeightRange(int y, EelValues cfg) {
        return y >= cfg.minY() && y <= cfg.maxY();
    }

    /**
     * pos 所在那一竖条水有多深（从 pos 往下、往上数连着的水，pos 本身不是水为 0）；数到 cap 就停（只看同一列、最多 cap 格，不扫大范围）。
     */
    public static int waterDepth(BlockGetter level, BlockPos pos, int cap) {
        if (!level.getFluidState(pos).is(FluidTags.WATER)) {
            return 0;
        }
        int depth = 1;
        BlockPos.MutableBlockPos cursor = pos.mutable();
        for (int i = 1; depth < cap && i < cap; i++) {
            cursor.setWithOffset(pos, 0, -i, 0);
            if (!level.getFluidState(cursor).is(FluidTags.WATER)) {
                break;
            }
            depth++;
        }
        for (int i = 1; depth < cap && i < cap; i++) {
            cursor.setWithOffset(pos, 0, i, 0);
            if (!level.getFluidState(cursor).is(FluidTags.WATER)) {
                break;
            }
            depth++;
        }
        return depth;
    }

    /** 生命、护甲、咬的伤害按配置设成基础值、回满（生成时调；精英修饰符在这之后挂） */
    public void applyConfiguredAttributes() {
        EelValues cfg = ECHoannaNativesB.Config.eel();
        setBase(Attributes.MAX_HEALTH, cfg.maxHealth());
        setBase(Attributes.ARMOR, cfg.armor());
        setBase(Attributes.ATTACK_DAMAGE, cfg.biteDamage());
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
        setAirSupply(getMaxAirSupply());
        setXRot(0F);
        return super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
    }

    // ================================================================== 水中移动

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WaterBoundPathNavigation(this, level);
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public MobType getMobType() {
        return MobType.WATER;
    }

    @Override
    public int getMaxHeadXRot() {
        return 1;
    }

    @Override
    public int getMaxHeadYRot() {
        return 1;
    }

    /** 水里照海豚：按自己的速度往输入方向推、阻尼 0.9；没事干时缓缓下沉。出了水照普通生物 */
    @Override
    public void travel(Vec3 input) {
        if (isEffectiveAi() && isInWater()) {
            moveRelative(getSpeed(), input);
            move(MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(0.9D));
            if (getTarget() == null) {
                setDeltaMovement(getDeltaMovement().add(0D, -0.005D, 0D));
            }
        } else {
            super.travel(input);
        }
    }

    /** 离了水在地上扑腾（同原版鱼） */
    @Override
    public void aiStep() {
        if (!isInWater() && onGround() && verticalCollision) {
            setDeltaMovement(getDeltaMovement().add((random.nextFloat() * 2F - 1F) * 0.05F, 0.4F, (random.nextFloat() * 2F - 1F) * 0.05F));
            setOnGround(false);
            hasImpulse = true;
            playSound(ECHoannaNativesB.SOUND_EEL_FLOP.get(), getSoundVolume(), getVoicePitch());
        }
        super.aiStep();
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new TryFindWaterGoal(this));
        goalSelector.addGoal(2, new BiteGoal());
        goalSelector.addGoal(4, new RandomSwimmingGoal(this, 1.0D, 10));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, LivingEntity::isInWater));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        tickDrag();
        if (!isInWaterOrBubble()) {
            if (++dryTicks >= 20) {
                dryTicks = 0;
                double damage = ECHoannaNativesB.Config.eel().dryDamage();
                if (damage > 0D) {
                    hurt(damageSources().dryOut(), (float) damage);
                }
            }
        } else {
            dryTicks = 0;
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (!anims.handle(id, tickCount)) {
            super.handleEntityEvent(id);
        }
    }

    // ================================================================== 咬、下拽

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living) {
            anims.play(this, ANIM_BITE);
            playSound(ECHoannaNativesB.SOUND_EEL_BITE.get(), 1F, 1F);
            EelValues cfg = ECHoannaNativesB.Config.eel();
            if (isElite() && cfg.eliteSlowTicks() > 0) {
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, cfg.eliteSlowTicks(), 0), this);
            }
            startDrag(living);
        }
        return hit;
    }

    /** 开始往下拽 target（只拽技能目标；{@code drag_ticks} 填 0 不拽）。返回开始了没有 */
    public boolean startDrag(LivingEntity target) {
        int ticks = ECHoannaNativesB.Config.eel().dragTicks();
        if (ticks <= 0 || !isSkillTarget(target) || level().isClientSide) {
            return false;
        }
        dragTarget = target;
        dragLeft = ticks;
        dragLastPos = target.position();
        anims.play(this, ANIM_DRAG);
        return true;
    }

    /** 正在拽谁（没有返回 null） */
    @Nullable
    public LivingEntity dragTarget() {
        return dragTarget;
    }

    /** 下拽推一刻（服务端每刻；测试直接调）：到时、目标没了 / 不再是技能目标 / 离太远 / 往上浮了就停；否则往下拉一下 */
    public void tickDrag() {
        LivingEntity target = dragTarget;
        if (target == null) {
            return;
        }
        if (--dragLeft < 0 || !target.isAlive() || target.isRemoved() || target.level() != level() || !isSkillTarget(target)
                || distanceToSqr(target) > DRAG_BREAK_RANGE * DRAG_BREAK_RANGE || target.getY() > dragLastPos.y + DRAG_RISE_BREAK) {
            stopDrag();
            return;
        }
        Vec3 motion = target instanceof Player ? target.position().subtract(dragLastPos) : target.getDeltaMovement();
        pullDown(target, ECHoannaNativesB.Config.eel().dragSpeed(), motion);
        dragLastPos = target.position();
    }

    public void stopDrag() {
        dragTarget = null;
        dragLeft = 0;
    }

    /**
     * 把 target 往下拉一下：只拉在水里的；往下挪 speed 以后会碰到方块（脚下快到底了）就不拉。竖直速度每次多往下 {@value #DRAG_ACCEL}，
     * 最快 speed；玩家的速度要发给客户端（{@code hurtMarked}）。返回这一下拉了没有。
     */
    public static boolean pullDown(LivingEntity target, double speed) {
        return pullDown(target, speed, target.getDeltaMovement());
    }

    /**
     * 同上，motion 是目标现在实际的速度。玩家的移动在客户端算，服务端的 {@code getDeltaMovement} 停在上一次发过去的值：
     * 拿它比的话拉到最快那一刻就以为「已经够快」不再发包，客户端那边游几下就挣上去了，所以玩家传按位置差算的速度（{@link #tickDrag}）。
     */
    public static boolean pullDown(LivingEntity target, double speed, Vec3 motion) {
        if (!(speed > 0D) || !target.isInWater()) {
            return false;
        }
        AABB below = target.getBoundingBox().move(0D, -speed, 0D);
        if (!target.level().noCollision(target, below)) {
            return false;
        }
        double vy = Math.max(Math.min(motion.y, 0D) - DRAG_ACCEL, -speed);
        if (motion.y <= vy) {
            return false;
        }
        target.setDeltaMovement(motion.x, vy, motion.z);
        target.hurtMarked = true;
        return true;
    }

    // ================================================================== 群首叫帮手

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && isElite() && !packCalled && !level().isClientSide && isAlive()) {
            packCalled = true;
            LivingEntity attacker = source.getEntity() instanceof LivingEntity living ? living : null;
            callPack(attacker);
        }
        return hurt;
    }

    /** 叫过帮手没有（存盘） */
    public boolean packCalled() {
        return packCalled;
    }

    /** 叫来的渊鳗的群首 UUID；不是叫来的返回 null */
    @Nullable
    public UUID matriarch() {
        CompoundTag data = getPersistentData();
        return data.hasUUID(TAG_MATRIARCH) ? data.getUUID(TAG_MATRIARCH) : null;
    }

    /**
     * 群首叫帮手（服务端）：在身边水里放 {@code elite_summon_count} 条普通渊鳗。来源先记成「召唤」再走 Forge 的生成事件，被取消的不算。返回放成的那几条。
     */
    public List<AbyssalEelEntity> callPack(@Nullable LivingEntity target) {
        List<AbyssalEelEntity> out = new ArrayList<>();
        if (!(level() instanceof ServerLevel server) || !isElite()) {
            return out;
        }
        int count = ECHoannaNativesB.Config.eel().eliteSummonCount();
        for (int i = 0; i < count; i++) {
            AbyssalEelEntity eel = ECHoannaNativesB.ABYSSAL_EEL.get().create(server);
            if (eel == null) {
                break;
            }
            Vec3 at = callSpot(eel);
            eel.moveTo(at.x, at.y, at.z, random.nextFloat() * 360F, 0F);
            eel.getPersistentData().putUUID(TAG_MATRIARCH, getUUID());
            SpawnOrigin.set(eel, Origin.SUMMONED);
            ForgeEventFactory.onFinalizeSpawn(eel, server, server.getCurrentDifficultyAt(eel.blockPosition()), MobSpawnType.MOB_SUMMONED, null, null);
            if (eel.isSpawnCancelled()) {
                continue;
            }
            if (target != null && target.isAlive()) {
                eel.setTarget(target);
            }
            if (server.addFreshEntity(eel)) {
                out.add(eel);
                server.sendParticles(ParticleTypes.BUBBLE, eel.getX(), eel.getY(0.5D), eel.getZ(), 10, 0.3D, 0.2D, 0.3D, 0.05D);
            }
        }
        if (!out.isEmpty()) {
            anims.play(this, ANIM_CALL);
            playSound(ECHoannaNativesB.SOUND_EEL_CALL.get(), 1F, 1F);
        }
        return out;
    }

    /** 叫来的那条放在哪：身边 {@value #CALL_RADIUS} 格内的水里、放得下它的点；都不行就放在自己身上 */
    private Vec3 callSpot(AbyssalEelEntity eel) {
        for (int i = 0; i < 8; i++) {
            Vec3 at = position().add((random.nextDouble() - 0.5D) * 2D * CALL_RADIUS, (random.nextDouble() - 0.5D) * 2D,
                    (random.nextDouble() - 0.5D) * 2D * CALL_RADIUS);
            AABB box = eel.getType().getDimensions().makeBoundingBox(at);
            if (level().noCollision(eel, box) && level().getFluidState(BlockPos.containing(at)).is(FluidTags.WATER)) {
                return at;
            }
        }
        return position();
    }

    /** 叫来的不给经验（群首一直不死、困住它会变成经验塔） */
    @Override
    public int getExperienceReward() {
        return matriarch() != null ? 0 : super.getExperienceReward();
    }

    // ================================================================== 存档、声音

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean(TAG_CALLED, packCalled);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        packCalled = tag.getBoolean(TAG_CALLED);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ECHoannaNativesB.SOUND_EEL_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ECHoannaNativesB.SOUND_EEL_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ECHoannaNativesB.SOUND_EEL_DEATH.get();
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return 3;
    }

    /** 只在水里咬（自己出了水就不追着打） */
    private final class BiteGoal extends MeleeAttackGoal {

        BiteGoal() {
            super(AbyssalEelEntity.this, 1.2D, true);
        }

        @Override
        public boolean canUse() {
            return isInWater() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return isInWater() && super.canContinueToUse();
        }
    }
}
