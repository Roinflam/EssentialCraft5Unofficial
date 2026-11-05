package com.flameforge.essentialcraft5.entity.mob.hoanna;

import com.flameforge.essentialcraft5.entity.anim.MobAnimStates;
import com.flameforge.essentialcraft5.entity.mob.ECFlyingMonster;
import com.flameforge.essentialcraft5.entity.mob.MobWeakness;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvents;
import com.flameforge.essentialcraft5.item.ember.EmberKind;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesB;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesB.Config.EmberValues;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.Difficulty;
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
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.EnumSet;
import java.util.Locale;

/**
 * 余烬灵 {@code ember_spirit}（第十三批 13d-N2，batch13.md §5.2；怪物稿 §2.4.4）：恐惧之地、海纳尔沙漠里一团悬浮的余烬碎块，围着暗火核心翻滚。
 * <ul>
 *   <li><b>一只一种余烬</b>：生成时随机定是八种余烬（{@link EmberKind}）里的哪一种，存同步数据和存档（键 {@value #TAG_EMBER}，小写种类名，
 *       掉落表按它分）；渲染时灰度贴图按余烬染色（{@link #color}）。没写种类的（指令带了别的 NBT）第一刻补上。</li>
 *   <li><b>打法</b>：烈焰人式悬停，有目标时绕着人在 5~9 格、高出 2~4 格的地方换位置，看得见、{@code shoot_range} 格内每 {@code shoot_interval} 刻（±10）
 *       吐一颗余烬弹（{@link EmberSpiritBoltEntity}，{@code mob_bolt}，没有预警的普通弹射物，照常吃护甲），打实了带那种余烬的效果（{@link EmberSpiritHit}）。
 *       免疫火；怕冰冻（{@code weak_to_freezing}，按 13c 的做法覆写 {@link #hasTrait}）。</li>
 *   <li><b>精英「余烬双生」</b>：多带一种余烬（键 {@value #TAG_EMBER_SECOND}，和第一种不同），一次连吐两颗，两种余烬各一颗。</li>
 *   <li><b>生成</b>（{@link #checkEmberSpawnRules}）：平时同原版怪物（夜里 / 暗处）；海纳尔日阵进行中、头顶露天的地方白天也刷（亮度放开，天灾结束恢复）。
 *       48 格内同种不到 {@code nearby_cap} 只由注册时外面那层管；群系权重归 13d-N3。</li>
 * </ul>
 * 生命、护甲、余烬弹伤害在 {@link #finalizeSpawn} 里按配置设成基础值（精英修饰符在那之后由基类挂）。动画两段：吐弹、连吐（{@link #anims}）。
 */
public class EmberSpiritEntity extends ECFlyingMonster {

    public static final int ANIM_CAST = 0;
    public static final int ANIM_VOLLEY = 1;

    public static final String TAG_EMBER = "Ember";
    public static final String TAG_EMBER_SECOND = "EmberSecond";

    /** 连吐两颗之间隔几刻 */
    public static final int VOLLEY_GAP = 6;
    /** 出手动画抬起到吐出去那一下的刻数（第一颗在这之后发） */
    public static final int WIND_UP = 4;
    /** 没目标时最多飘到离地几格 */
    public static final int WANDER_MAX_ALTITUDE = 5;
    /** 同步数据里「没有第二种」的值 */
    private static final byte NO_SECOND = -1;

    private static final EntityDataAccessor<Byte> DATA_EMBER = SynchedEntityData.defineId(EmberSpiritEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_EMBER_SECOND = SynchedEntityData.defineId(EmberSpiritEntity.class, EntityDataSerializers.BYTE);

    public final MobAnimStates anims = new MobAnimStates(2).channel(ANIM_CAST, 1).channel(ANIM_VOLLEY, 1);

    private boolean emberKnown;
    private int shootCooldown = 20;
    private int volleyLeft;
    private int volleyDelay;

    public EmberSpiritEntity(EntityType<? extends EmberSpiritEntity> type, Level level) {
        super(type, level);
        this.xpReward = 10;
    }

    /** 属性默认值（生命 / 护甲照 batch13.md §5.2，其余照怪物稿数值表；生成时再按配置设） */
    public static AttributeSupplier.Builder createAttributes() {
        EmberValues d = ECHoannaNativesB.Config.EMBER_DEFAULTS;
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, d.maxHealth())
                .add(Attributes.ARMOR, d.armor())
                .add(Attributes.ATTACK_DAMAGE, d.boltDamage())
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FLYING_SPEED, 0.35D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    // ================================================================== 余烬种类

    /** 八种余烬的染色（身体、发光层、弹、粒子都用它） */
    public static int color(EmberKind kind) {
        return switch (kind) {
            case ACIDIC -> 0x9CE04A;
            case CHAOS -> 0xD8324A;
            case COMMON -> 0xE08A3A;
            case CORRUPTED -> 0x8A4CC0;
            case CRYSTAL -> 0x7FDCF2;
            case DIVINE -> 0xFFE58A;
            case MAGICAL -> 0xE070E8;
            case FLAME -> 0xFF5A1E;
        };
    }

    public static Vector3f rgb(EmberKind kind) {
        int c = color(kind);
        return new Vector3f(((c >> 16) & 0xFF) / 255F, ((c >> 8) & 0xFF) / 255F, (c & 0xFF) / 255F);
    }

    /** 存档 / 掉落表里写的名字（小写种类名，{@code flame}、{@code chaos}……） */
    public static String key(EmberKind kind) {
        return kind.name().toLowerCase(Locale.ROOT);
    }

    @Nullable
    public static EmberKind byKey(String key) {
        for (EmberKind kind : EmberKind.values()) {
            if (key(kind).equals(key)) {
                return kind;
            }
        }
        return null;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_EMBER, (byte) EmberKind.COMMON.ordinal());
        entityData.define(DATA_EMBER_SECOND, NO_SECOND);
    }

    public EmberKind getEmber() {
        EmberKind[] all = EmberKind.values();
        return all[Math.floorMod(entityData.get(DATA_EMBER), all.length)];
    }

    /** 精英带的第二种；没有返回 null */
    @Nullable
    public EmberKind getSecondEmber() {
        byte b = entityData.get(DATA_EMBER_SECOND);
        EmberKind[] all = EmberKind.values();
        return b < 0 || b >= all.length ? null : all[b];
    }

    public void setEmber(EmberKind kind) {
        entityData.set(DATA_EMBER, (byte) kind.ordinal());
        emberKnown = true;
        EmberKind second = getSecondEmber();
        if (second == kind) {
            entityData.set(DATA_EMBER_SECOND, NO_SECOND);
            ensureSecond();
        }
    }

    public void setSecondEmber(@Nullable EmberKind kind) {
        entityData.set(DATA_EMBER_SECOND, kind == null || kind == getEmber() ? NO_SECOND : (byte) kind.ordinal());
    }

    /** 随机定一种余烬（精英再定和它不同的第二种） */
    public void pickEmber(RandomSource random) {
        EmberKind[] all = EmberKind.values();
        setEmber(all[random.nextInt(all.length)]);
        ensureSecond();
    }

    /** 精英还没有第二种就补一种（和第一种不同） */
    private void ensureSecond() {
        if (!isElite() || getSecondEmber() != null) {
            return;
        }
        EmberKind[] all = EmberKind.values();
        int pick = random.nextInt(all.length - 1);
        if (pick >= getEmber().ordinal()) {
            pick++;
        }
        setSecondEmber(all[pick]);
    }

    @Override
    public void setElite(boolean elite) {
        super.setElite(elite);
        if (isElite()) {
            ensureSecond();
        }
    }

    /** 怕冰冻（{@code weak_to_freezing}） */
    @Override
    public boolean hasTrait(TagKey<EntityType<?>> trait) {
        return trait == MobWeakness.WEAK_TO_FREEZING || super.hasTrait(trait);
    }

    // ================================================================== 生成

    /**
     * 自然生成的判定：海纳尔日阵进行中、这一格头顶露天时只看难度和脚下（同 {@code checkAnyLightMonsterSpawnRules}，亮度放开）；
     * 别的时候同原版怪物判定（要够暗）。附近数量上限在注册时另包一层。
     */
    public static boolean checkEmberSpawnRules(EntityType<EmberSpiritEntity> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos,
                                               RandomSource random) {
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return false;
        }
        if (sunArrayOpenSky(level, pos)) {
            return Monster.checkAnyLightMonsterSpawnRules(type, level, reason, pos, random);
        }
        return Monster.checkMonsterSpawnRules(type, level, reason, pos, random);
    }

    /**
     * 实体那一关：原版怪物在亮处会被「走向价值」拦下（{@code PathfinderMob.checkSpawnRules}：{@code Monster.getWalkTargetValue} 在亮度
     * 高于约 12 时为负），日阵正午的露天亮度 15，光过了放置判定还是刷不出来。日阵里露天的位置在这里放行，其余照旧（batch13.md §5.2 余烬灵）。
     */
    @Override
    public boolean checkSpawnRules(LevelAccessor level, MobSpawnType reason) {
        if (level instanceof ServerLevelAccessor server && sunArrayOpenSky(server, blockPosition())) {
            return true;
        }
        return super.checkSpawnRules(level, reason);
    }

    /** 这一格是不是「日阵里的露天」：海纳尔正在日阵、头顶看得见天 */
    public static boolean sunArrayOpenSky(ServerLevelAccessor level, BlockPos pos) {
        return HoannaEvents.isActive(level.getLevel(), HoannaEvent.SUN_ARRAY) && level.canSeeSky(pos);
    }

    /** 生命、护甲、余烬弹伤害按配置设成基础值、回满（生成时调；精英修饰符在这之后挂） */
    public void applyConfiguredAttributes() {
        EmberValues cfg = ECHoannaNativesB.Config.ember();
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
        pickEmber(random);
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
        ensureSecond();
        return data;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(TAG_EMBER, key(getEmber()));
        EmberKind second = getSecondEmber();
        if (second != null) {
            tag.putString(TAG_EMBER_SECOND, key(second));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(TAG_EMBER, Tag.TAG_STRING)) {
            EmberKind kind = byKey(tag.getString(TAG_EMBER));
            if (kind != null) {
                entityData.set(DATA_EMBER, (byte) kind.ordinal());
                emberKnown = true;
            }
        }
        if (tag.contains(TAG_EMBER_SECOND, Tag.TAG_STRING)) {
            setSecondEmber(byKey(tag.getString(TAG_EMBER_SECOND)));
        }
    }

    // ================================================================== AI

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new AttackGoal());
        goalSelector.addGoal(7, new WanderGoal());
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (random.nextInt(2) == 0) {
                level().addParticle(new DustParticleOptions(rgb(getEmber()), 0.8F), getRandomX(0.45D), getY() + 0.2D + random.nextDouble() * 0.8D,
                        getRandomZ(0.45D), 0D, 0.02D, 0D);
            }
            if (random.nextInt(6) == 0) {
                level().addParticle(ParticleTypes.SMALL_FLAME, getRandomX(0.3D), getY(0.6D), getRandomZ(0.3D), 0D, 0.01D, 0D);
            }
        } else if (!emberKnown) {
            pickEmber(random);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (!anims.handle(id, tickCount)) {
            super.handleEntityEvent(id);
        }
    }

    /** 朝 target 吐一颗 kind 的余烬弹（服务端）；返回吐出去的那颗，世界不收时返回 null */
    @Nullable
    public EmberSpiritBoltEntity shootAt(LivingEntity target, EmberKind kind) {
        EmberSpiritBoltEntity bolt = new EmberSpiritBoltEntity(level(), this, kind, (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
        double dx = target.getX() - bolt.getX();
        double dy = target.getY(0.5D) - bolt.getY();
        double dz = target.getZ() - bolt.getZ();
        bolt.shoot(dx, dy, dz, EmberSpiritBoltEntity.SPEED, 2F);
        playSound(ECHoannaNativesB.SOUND_EMBER_SHOOT.get(), 0.8F, 1F + (random.nextFloat() - 0.5F) * 0.2F);
        return level().addFreshEntity(bolt) ? bolt : null;
    }

    /** 打的时候换到哪：目标周围 5~9 格、比目标脚下高 2~4 格，直线飞得过去的点 */
    @Nullable
    public Vec3 strafePoint(LivingEntity target) {
        double current = Math.atan2(getZ() - target.getZ(), getX() - target.getX());
        for (int i = 0; i < 6; i++) {
            double angle = current + (random.nextDouble() - 0.5D) * Math.PI * 0.8D;
            double radius = 5D + random.nextDouble() * 4D;
            Vec3 dest = new Vec3(target.getX() + Math.cos(angle) * radius, target.getY() + 2D + random.nextDouble() * 2D,
                    target.getZ() + Math.sin(angle) * radius);
            if (canFlyTo(dest)) {
                return dest;
            }
        }
        return null;
    }

    /** 直线飞得过去：终点区块已加载、放得下自己、不在液体里、两点之间没有方块挡着 */
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

    @Override
    public int getMaxSpawnClusterSize() {
        return 2;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ECHoannaNativesB.SOUND_EMBER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ECHoannaNativesB.SOUND_EMBER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ECHoannaNativesB.SOUND_EMBER_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 0.7F;
    }

    // ================================================================== 目标

    /** 绕着目标换位置、吐余烬弹；精英连吐两颗（两种余烬各一颗） */
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
            volleyLeft = 0;
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target == null) {
                return;
            }
            getLookControl().setLookAt(target, 30F, 30F);
            if (--repositionTicks <= 0) {
                Vec3 dest = strafePoint(target);
                if (dest != null) {
                    getMoveControl().setWantedPosition(dest.x, dest.y, dest.z, 1D);
                }
                repositionTicks = 20 + random.nextInt(25);
            }
            if (volleyLeft > 0) {
                if (--volleyDelay <= 0) {
                    EmberKind second = getSecondEmber();
                    EmberKind kind = volleyLeft == 1 && second != null ? second : getEmber();
                    shootAt(target, kind);
                    volleyLeft--;
                    volleyDelay = VOLLEY_GAP;
                }
                return;
            }
            EmberValues cfg = ECHoannaNativesB.Config.ember();
            double range = cfg.shootRange();
            if (--shootCooldown <= 0 && getSensing().hasLineOfSight(target) && distanceToSqr(target) <= range * range) {
                volleyLeft = getSecondEmber() != null ? 2 : 1;
                volleyDelay = WIND_UP;
                anims.play(EmberSpiritEntity.this, volleyLeft > 1 ? ANIM_VOLLEY : ANIM_CAST);
                shootCooldown = cfg.shootInterval() + random.nextInt(21) - 10;
            }
        }
    }

    /** 没目标时在附近慢慢飘（飘的点离地不远） */
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
                Vec3 dest = position().add((random.nextDouble() - 0.5D) * 10D, (random.nextDouble() - 0.5D) * 4D, (random.nextDouble() - 0.5D) * 10D);
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
