package com.flameforge.essentialcraft5.entity.mob.hoanna;

import com.flameforge.essentialcraft5.entity.anim.MobAnimStates;
import com.flameforge.essentialcraft5.entity.mob.ECMonster;
import com.flameforge.essentialcraft5.entity.mob.MobSpawnRules;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesC;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesC.Config.RevenantValues;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
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
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 墓穴守灵 {@code catacomb_revenant}（第十三批 13d-N3，batch13.md §5.2；怪物稿 §2.4.8）：穿锈蚀板甲的骷髅骑士，肋间透出淡青的灵火。
 * <ul>
 *   <li><b>近战</b>：持剑 {@code attack_damage}（默认 9；剑是模型的一部分，不是装备，不会掉、也不加攻击力）。</li>
 *   <li><b>灵体</b>：弹射物有 {@code projectile_pass_chance}（默认 30%）直接从它身上穿过、不受伤。是不是穿过由「这一发 + 这一只」的 UUID 定死
 *       （{@link #passesThrough}），同一发箭不会这一刻穿过、下一刻又打中；两端算出来一样，客户端的箭也不会先弹开再被拉回去。
 *       接在 Forge 的 {@code ProjectileImpactEvent} 上（{@code HoannaNativesCEvents}），结果改成 {@code SKIP_ENTITY}。</li>
 *   <li><b>骨誓</b>：一只死时，{@code oath_radius}（8）格内别的守灵得到力量 I {@code oath_ticks}（10 秒）。</li>
 *   <li><b>精英「墓穴守灵长」</b>：每 {@code blade_interval}（12 秒）刻放 {@code blade_count}（2）把追踪灵刃（{@link RevenantBladeEntity}，弹射物 5 点、
 *       追踪 3 秒、能被打掉）；它放出的、还在飞的不超过 {@code blade_count} 把。</li>
 *   <li><b>生成</b>（{@link #SPAWN_RULES}）：只在古代墓穴的拼件里（结构 {@code old_catacombs} 的 {@code spawn_overrides}，{@code bounding_box: piece}：
 *       守灵 60、骷髅 30、僵尸 20），亮度 ≤ {@code max_light}（7），32 格内同种 ≤ {@code nearby_cap}（8）。任何群系的刷怪表里都没有它。
 *       13e 的宝库守卫（召唤）、沉城卫兵（巢穴 {@code sunken_guard}）也是它。</li>
 * </ul>
 * 亡灵（亡灵杀手、治疗药水照原版算），不在阳光下着火，不捡东西、不开门（基类）。生命、护甲、攻击在 {@link #finalizeSpawn} 里按配置设成基础值。
 * 动画两段：挥剑、守灵长举剑召灵刃（{@link #anims}）。
 */
public class CatacombRevenantEntity extends ECMonster {

    public static final int ANIM_SLASH = 0;
    public static final int ANIM_CALL = 1;
    /** 自然生成时数「附近有几只」的半径 */
    public static final int NEARBY_RADIUS = 32;
    /** 数「自己放出的」灵刃时看多大范围 */
    public static final int BLADE_RADIUS = 48;
    /** 守灵长举剑召灵刃的前摇（刻） */
    public static final int CALL_WIND_UP = 10;
    /** 灵刃挂在身边的位置（相对脚下，左右各一） */
    private static final double BLADE_SIDE = 0.9D;
    private static final double BLADE_HEIGHT = 2.1D;

    private static final String TAG_BLADE_COOLDOWN = "blade_cooldown";

    /** 自然生成的判定：非和平、亮度 ≤ max_light、脚下能站，再加 32 格同种上限 */
    public static final SpawnPlacements.SpawnPredicate<CatacombRevenantEntity> SPAWN_RULES =
            MobSpawnRules.capped(CatacombRevenantEntity::checkRevenantSpawnRules, () -> NEARBY_RADIUS,
                    () -> ECHoannaNativesC.Config.revenant().nearbyCap());

    public final MobAnimStates anims = new MobAnimStates(2).channel(ANIM_SLASH, 1).channel(ANIM_CALL, 1);

    private int bladeCooldown = 80;
    private int callTicks;

    public CatacombRevenantEntity(EntityType<? extends CatacombRevenantEntity> type, Level level) {
        super(type, level);
        this.xpReward = 8;
    }

    /** 属性默认值（生命 / 护甲照 batch13.md §5.2，剑 9、移速 0.25 照怪物稿） */
    public static AttributeSupplier.Builder createAttributes() {
        RevenantValues defaults = ECHoannaNativesC.Config.REVENANT_DEFAULTS;
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, defaults.maxHealth())
                .add(Attributes.ARMOR, defaults.armor())
                .add(Attributes.ATTACK_DAMAGE, defaults.attackDamage())
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 24D);
    }

    /** 亮度不超过 {@code max_light}、非和平、脚下能站（附近上限在 {@link #SPAWN_RULES} 另包一层） */
    public static boolean checkRevenantSpawnRules(EntityType<CatacombRevenantEntity> type, ServerLevelAccessor level, MobSpawnType reason,
                                                  BlockPos pos, RandomSource random) {
        return level.getDifficulty() != Difficulty.PEACEFUL && level.getMaxLocalRawBrightness(pos) <= ECHoannaNativesC.Config.revenant().maxLight()
                && Mob.checkMobSpawnRules(type, level, reason, pos, random);
    }

    // ================================================================== 生成

    /** 生命、护甲、剑的伤害按配置设成基础值、回满（生成时调；精英修饰符在这之后挂） */
    public void applyConfiguredAttributes() {
        RevenantValues cfg = ECHoannaNativesC.Config.revenant();
        setBase(Attributes.MAX_HEALTH, cfg.maxHealth());
        setBase(Attributes.ARMOR, cfg.armor());
        setBase(Attributes.ATTACK_DAMAGE, cfg.attackDamage());
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

    @Override
    public MobType getMobType() {
        return MobType.UNDEAD;
    }

    // ================================================================== 灵体

    /**
     * 这一发弹射物从这一只身上穿不穿过：按（弹射物 UUID, 守灵 UUID）散列成 [0, 1) 的数，小于 chance 就穿过。纯函数，两端一样。
     */
    public static boolean passesThrough(UUID projectile, UUID revenant, double chance) {
        if (chance <= 0D) {
            return false;
        }
        if (chance >= 1D) {
            return true;
        }
        long h = projectile.getMostSignificantBits() * 0x9E3779B97F4A7C15L ^ projectile.getLeastSignificantBits();
        h ^= revenant.getMostSignificantBits() * 0xC2B2AE3D27D4EB4FL ^ revenant.getLeastSignificantBits() * 0x165667B19E3779F9L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53 < chance;
    }

    /** 这一发打到它身上时穿不穿过（按配置的几率） */
    public boolean passesThrough(Projectile projectile) {
        return passesThrough(projectile.getUUID(), getUUID(), ECHoannaNativesC.Config.revenant().passChance());
    }

    /** 穿过时在它身上冒一缕灵火（客户端调） */
    public void showPhase(Vec3 at) {
        if (level().isClientSide) {
            for (int i = 0; i < 4; i++) {
                level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, (random.nextDouble() - 0.5D) * 0.05D, 0.03D,
                        (random.nextDouble() - 0.5D) * 0.05D);
            }
        }
    }

    // ================================================================== 骨誓

    @Override
    public void die(DamageSource source) {
        boolean wasAlive = !isRemoved() && !dead;
        super.die(source);
        // 死亡事件被别的模组 / 插件取消时父类不会把 dead 置上，这时不算死、不发骨誓
        if (wasAlive && dead && level() instanceof ServerLevel server) {
            boneOath(server);
        }
    }

    /** 骨誓：{@code oath_radius} 格内别的、活着的守灵得到力量 I（返回给了几只；{@code oath_ticks} 填 0 时什么都不做） */
    public int boneOath(ServerLevel server) {
        RevenantValues cfg = ECHoannaNativesC.Config.revenant();
        if (cfg.oathTicks() <= 0) {
            return 0;
        }
        double radius = cfg.oathRadius();
        List<CatacombRevenantEntity> allies = server.getEntitiesOfClass(CatacombRevenantEntity.class, getBoundingBox().inflate(radius),
                other -> other != this && other.isAlive() && other.distanceToSqr(this) <= radius * radius);
        for (CatacombRevenantEntity ally : allies) {
            ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, cfg.oathTicks(), 0), this);
        }
        if (!allies.isEmpty()) {
            playSound(ECHoannaNativesC.SOUND_REVENANT_OATH.get(), 1F, 1F);
        }
        return allies.size();
    }

    // ================================================================== 灵刃

    /** {@value #BLADE_RADIUS} 格以内它放出的、还在飞的灵刃有几把 */
    public int bladeCount() {
        UUID self = getUUID();
        return level().getEntitiesOfClass(RevenantBladeEntity.class, getBoundingBox().inflate(BLADE_RADIUS),
                blade -> blade.isAlive() && blade.getOwner() != null && self.equals(blade.getOwner().getUUID())).size();
    }

    /**
     * 守灵长放灵刃（服务端）：在身边两侧放出灵刃，补到 {@code blade_count} 把为止，追 target。不是精英、不是技能目标、生成被取消时放不出来。返回这次放了几把。
     */
    public int summonBlades(LivingEntity target) {
        if (!(level() instanceof ServerLevel server) || !isElite() || !isSkillTarget(target)) {
            return 0;
        }
        RevenantValues cfg = ECHoannaNativesC.Config.revenant();
        int want = cfg.bladeCount() - bladeCount();
        int spawned = 0;
        for (int i = 0; i < want; i++) {
            double side = (i % 2 == 0 ? 1D : -1D) * BLADE_SIDE * (1 + i / 2);
            Vec3 right = Vec3.directionFromRotation(0F, getYRot() + 90F);
            Vec3 at = position().add(right.scale(side)).add(0D, BLADE_HEIGHT, 0D);
            if (RevenantBladeEntity.launch(server, this, target, at, (float) cfg.bladeDamage(), cfg.bladeHoming()) != null) {
                spawned++;
            }
        }
        if (spawned > 0) {
            playSound(ECHoannaNativesC.SOUND_REVENANT_BLADES.get(), 1F, 1F);
        }
        return spawned;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!isElite()) {
            return;
        }
        RevenantValues cfg = ECHoannaNativesC.Config.revenant();
        LivingEntity target = getTarget();
        if (callTicks > 0) {
            if (--callTicks == 0 && target != null) {
                summonBlades(target);
            }
            return;
        }
        if (bladeCooldown > 0) {
            bladeCooldown--;
            return;
        }
        if (cfg.bladeInterval() > 0 && target != null && isSkillTarget(target) && getSensing().hasLineOfSight(target)) {
            bladeCooldown = cfg.bladeInterval();
            if (bladeCount() < cfg.bladeCount()) {
                callTicks = CALL_WIND_UP;
                anims.play(this, ANIM_CALL);
            }
        }
    }

    // ================================================================== 行为目标、杂项

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, false));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, CatacombRevenantEntity.class).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        anims.play(this, ANIM_SLASH);
        return super.doHurtTarget(target);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide && random.nextInt(isElite() ? 4 : 8) == 0) {
            level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, getRandomX(0.25D), getY(0.6D), getRandomZ(0.25D), 0D, 0.01D, 0D);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (!anims.handle(id, tickCount)) {
            super.handleEntityEvent(id);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ECHoannaNativesC.SOUND_REVENANT_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ECHoannaNativesC.SOUND_REVENANT_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ECHoannaNativesC.SOUND_REVENANT_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(ECHoannaNativesC.SOUND_REVENANT_STEP.get(), 0.15F, 1F);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt(TAG_BLADE_COOLDOWN, bladeCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        bladeCooldown = Math.max(0, tag.getInt(TAG_BLADE_COOLDOWN));
    }
}
