package com.flameforge.essentialcraft5.entity.mob;

import com.flameforge.essentialcraft5.entity.MRUPresenceEntity;
import com.flameforge.essentialcraft5.entity.MRUPresenceIndex;
import com.flameforge.essentialcraft5.entity.anim.MobAnimStates;
import com.flameforge.essentialcraft5.registry.ECWildMobs;
import com.flameforge.essentialcraft5.util.SpawnOrigin;
import com.flameforge.essentialcraft5.util.SpawnOrigin.Origin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * 矩阵游魂（13c-M1，batch13.md §5.2、§6.5 末行）：被大团 MRU 聚集体招来的人形怪，生命 26、护甲 2，下半身是飘着的袍子。
 * <ul>
 *   <li><b>在哪</b>：所有维度，暗处，24 格内有一团 ≥ 5 000 MRU、<b>没被稳定器压着</b>的聚集体时，往那一格的怪物刷怪表里追加权重 30
 *       （{@code MatrixWraithEvents}，用 {@link MRUPresenceIndex} 查，只认已加载的、不加载区块）；一团聚集体 32 格内最多 3 只。
 *       富集仓里养着的、正在凝结 / 坍缩的种子都被稳定器压着，不招游魂、不被抽。这样刷出来的来源记「事件」（{@code EVENT}，公平）。</li>
 *   <li><b>抽聚集体</b>：真的进了世界（{@code EntityJoinLevelEvent} 最低优先级、没被取消）才从最近那团够格的聚集体抽 300 MRU；
 *       生成被取消、刷怪蛋 / 刷怪笼 / 指令出来的都不抽。只抽 MRU，不生成聚集体、不扩散腐化。</li>
 *   <li><b>打法</b>：近战抓人（4 点，吃护甲）；抓实一下给被抓的那个玩家加 150 矩阵损伤（{@code matrix_damage_per_hit}，走带来源标记的
 *       {@code CorruptionEffects.addMatrixDamage}，吃矩阵阶的耐受；只加给生存 / 冒险、没开无敌的真玩家）。弹射物只吃一半（{@link #hasTrait}）。</li>
 *   <li><b>精英「矩阵怨灵」</b>：每 8 秒（{@code elite_blink_cooldown}）缩成一团半秒，再闪到目标背后 2 格（{@link #tryBlinkBehind}）：
 *       落点要能站、脚下有方块、不在液体里，走 Forge 的 {@code EntityTeleportEvent.EnderEntity}（可取消，混合端插件拦得到），被拦或落点不行就留在原地。</li>
 *   <li><b>掉落</b>（entities/matrix_wraith）：MRU 碎片 1~2（公平击杀 + 按人递减 overworld_common）；精英另掉 MRU 碎片 2~4（递减组 elite）。</li>
 * </ul>
 */
public class MatrixWraithEntity extends ECMonster {

    public static final int ANIM_ATTACK = 0;
    public static final int ANIM_BLINK_OUT = 1;
    public static final int ANIM_BLINK_IN = 2;

    /** 精英闪之前缩成一团的刻数（预警） */
    public static final int BLINK_WINDUP = 10;
    /** 闪到目标背后多远（格） */
    public static final double BLINK_DISTANCE = 2D;
    /** 「一团聚集体附近最多几只」按这个半径数 */
    public static final int PER_PRESENCE_RADIUS = 32;

    private static final Vector3f MATRIX_PURPLE = new Vector3f(0.72F, 0.36F, 1F);

    public final MobAnimStates anims = new MobAnimStates(3).channel(ANIM_BLINK_OUT, 1).channel(ANIM_BLINK_IN, 1);

    /** 自然 / 事件刷出、还没进世界：进世界时抽聚集体（不存盘） */
    private boolean pendingDrain;
    private int blinkCooldown = 60;
    private int blinkWindup;

    public MatrixWraithEntity(EntityType<? extends MatrixWraithEntity> type, Level level) {
        super(type, level);
        xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 26D)
                .add(Attributes.ARMOR, 2D)
                .add(Attributes.MOVEMENT_SPEED, 0.26D)
                .add(Attributes.ATTACK_DAMAGE, 4D)
                .add(Attributes.FOLLOW_RANGE, 32D);
    }

    /** 弹射物只吃一半（{@code resists_projectile}）；数据包往类型标签里加的别的特性照常算 */
    @Override
    public boolean hasTrait(TagKey<EntityType<?>> trait) {
        return trait == MobWeakness.RESISTS_PROJECTILE || super.hasTrait(trait);
    }

    // ================================================================== 生成

    /** 原版怪物判定（暗处、非和平、脚下方块）+ 附近有够格的聚集体、那团附近还没满 */
    public static boolean checkWraithSpawnRules(EntityType<MatrixWraithEntity> type, ServerLevelAccessor level, MobSpawnType reason,
                                                BlockPos pos, RandomSource random) {
        return Monster.checkMonsterSpawnRules(type, level, reason, pos, random) && roomNearAnchor(level.getLevel(), pos);
    }

    /** pos 附近（{@code presence_radius}）够格的聚集体：≥ {@code min_presence_mru}、没被稳定器压着；没有返回 null */
    @Nullable
    public static MRUPresenceEntity anchorFor(Level level, BlockPos pos) {
        return MRUPresenceIndex.nearest(level, Vec3.atCenterOf(pos), ECWildMobs.Config.wraithPresenceRadius(),
                ECWildMobs.Config.wraithMinPresenceMru(), true);
    }

    /** pos 附近有够格的聚集体，并且那团 {@value #PER_PRESENCE_RADIUS} 格内的游魂不到 {@code max_per_presence} 只 */
    public static boolean roomNearAnchor(ServerLevel level, BlockPos pos) {
        int max = ECWildMobs.Config.wraithMaxPerPresence();
        if (max <= 0) {
            return false;
        }
        MRUPresenceEntity anchor = anchorFor(level, pos);
        return anchor != null && countNear(level, anchor) < max;
    }

    /** 这团聚集体 {@value #PER_PRESENCE_RADIUS} 格内活着的游魂 */
    public static int countNear(ServerLevel level, Entity anchor) {
        double radiusSq = (double) PER_PRESENCE_RADIUS * PER_PRESENCE_RADIUS;
        return level.getEntities(ECWildMobs.MATRIX_WRAITH.get(), anchor.getBoundingBox().inflate(PER_PRESENCE_RADIUS),
                wraith -> wraith.isAlive() && wraith.distanceToSqr(anchor) <= radiusSq).size();
    }

    /** 自然刷出（只可能是聚集体招来的）：来源改记「事件」、进世界时抽聚集体；然后照基类掷精英 */
    @Nullable
    @Override
    @SuppressWarnings("deprecation")
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData spawnData, @Nullable CompoundTag dataTag) {
        if (reason == MobSpawnType.NATURAL && SpawnOrigin.of(this) == Origin.NATURAL) {
            SpawnOrigin.set(this, Origin.EVENT);
            pendingDrain = true;
        }
        return super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
    }

    /** 进世界时调一次：要抽的话从最近那团够格的聚集体抽 {@code drain_mru}，返回抽到多少（不抽返回 0） */
    public int drainAnchorOnJoin(ServerLevel level) {
        if (!pendingDrain) {
            return 0;
        }
        pendingDrain = false;
        int want = ECWildMobs.Config.wraithDrainMru();
        MRUPresenceEntity anchor = anchorFor(level, blockPosition());
        if (want <= 0 || anchor == null) {
            return 0;
        }
        int drawn = anchor.getStorage().extractMRU(Math.min(want, anchor.getMRU()), true);
        if (drawn > 0) {
            level.sendParticles(new DustParticleOptions(MATRIX_PURPLE, 1.2F), anchor.getX(), anchor.getY(), anchor.getZ(), 12, 0.4D, 0.4D, 0.4D, 0.02D);
            level.playSound(null, getX(), getY(), getZ(), ECWildMobs.WRAITH_DRAIN.get(), SoundSource.HOSTILE, 0.8F, 1F);
        }
        return drawn;
    }

    /** 测试用：当成聚集体招来的（进世界时抽） */
    public void markPendingDrain() {
        pendingDrain = true;
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return 1;
    }

    // ================================================================== AI

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, false));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, null));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        anims.play(this, ANIM_ATTACK);
        return super.doHurtTarget(target);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (!anims.handle(id, tickCount)) {
            super.handleEntityEvent(id);
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!isElite()) {
            return;
        }
        LivingEntity target = getTarget();
        if (blinkWindup > 0) {
            if (--blinkWindup == 0) {
                if (target == null || !tryBlinkBehind(target)) {
                    anims.play(this, ANIM_BLINK_IN);
                }
                blinkCooldown = ECWildMobs.Config.wraithBlinkCooldown();
            }
            return;
        }
        if (blinkCooldown > 0) {
            blinkCooldown--;
            return;
        }
        if (target != null && isSkillTarget(target) && getSensing().hasLineOfSight(target)) {
            double distSq = distanceToSqr(target);
            if (distSq > 9D && distSq < 256D) {
                blinkWindup = BLINK_WINDUP;
                anims.play(this, ANIM_BLINK_OUT);
                if (level() instanceof ServerLevel server) {
                    server.sendParticles(new DustParticleOptions(MATRIX_PURPLE, 1F), getX(), getY(1D), getZ(), 10, 0.3D, 0.6D, 0.3D, 0.01D);
                }
            }
        }
    }

    /**
     * 闪到 target 背后 {@value #BLINK_DISTANCE} 格（按它面朝的方向；脚下高一格、低一格也试）。落点要：脚下那格有碰撞、站得下、没有液体、
     * 不是会伤人的方块；再发 {@code EntityTeleportEvent.EnderEntity}，被取消就不闪。闪成功返回 true。
     */
    public boolean tryBlinkBehind(LivingEntity target) {
        if (!(level() instanceof ServerLevel server)) {
            return false;
        }
        Vec3 facing = target.getViewVector(1F).multiply(1D, 0D, 1D);
        if (facing.lengthSqr() < 1.0E-4D) {
            facing = target.position().subtract(position()).multiply(1D, 0D, 1D);
        }
        if (facing.lengthSqr() < 1.0E-4D) {
            return false;
        }
        Vec3 behind = target.position().subtract(facing.normalize().scale(BLINK_DISTANCE));
        for (int dy : new int[]{0, 1, -1}) {
            Vec3 dest = new Vec3(behind.x, Math.floor(behind.y) + dy, behind.z);
            if (!standable(server, dest)) {
                continue;
            }
            EntityTeleportEvent.EnderEntity event = ForgeEventFactory.onEnderTeleport(this, dest.x, dest.y, dest.z);
            if (event.isCanceled()) {
                return false;
            }
            Vec3 from = position();
            teleportTo(event.getTargetX(), event.getTargetY(), event.getTargetZ());
            getNavigation().stop();
            lookAt(target, 180F, 180F);
            anims.play(this, ANIM_BLINK_IN);
            server.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1D, from.z, 16, 0.3D, 0.6D, 0.3D, 0.05D);
            server.sendParticles(new DustParticleOptions(MATRIX_PURPLE, 1.2F), getX(), getY(1D), getZ(), 16, 0.3D, 0.6D, 0.3D, 0.02D);
            server.playSound(null, from.x, from.y, from.z, ECWildMobs.WRAITH_BLINK.get(), SoundSource.HOSTILE, 0.9F, 1F);
            server.playSound(null, getX(), getY(), getZ(), ECWildMobs.WRAITH_BLINK.get(), SoundSource.HOSTILE, 0.9F, 1F);
            return true;
        }
        return false;
    }

    private boolean standable(ServerLevel level, Vec3 feet) {
        BlockPos pos = BlockPos.containing(feet);
        if (!level.hasChunkAt(pos) || level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()) {
            return false;
        }
        AABB box = getDimensions(getPose()).makeBoundingBox(feet);
        if (!level.noCollision(this, box) || level.containsAnyLiquid(box)) {
            return false;
        }
        BlockPathTypes ground = WalkNodeEvaluator.getBlockPathTypeStatic(level, pos.mutable());
        return ground != BlockPathTypes.DAMAGE_FIRE && ground != BlockPathTypes.DAMAGE_OTHER && ground != BlockPathTypes.LAVA
                && ground != BlockPathTypes.DANGER_FIRE && ground != BlockPathTypes.DANGER_OTHER && ground != BlockPathTypes.DAMAGE_CAUTIOUS;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && random.nextInt(4) == 0) {
            level().addParticle(new DustParticleOptions(MATRIX_PURPLE, 0.7F), getRandomX(0.5D), getY() + random.nextDouble() * 0.6D,
                    getRandomZ(0.5D), 0D, 0.01D, 0D);
        }
    }

    // ================================================================== 声音

    @Override
    protected SoundEvent getAmbientSound() {
        return ECWildMobs.WRAITH_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ECWildMobs.WRAITH_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ECWildMobs.WRAITH_DEATH.get();
    }
}
