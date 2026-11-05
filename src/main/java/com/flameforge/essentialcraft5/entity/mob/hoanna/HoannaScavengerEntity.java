package com.flameforge.essentialcraft5.entity.mob.hoanna;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.entity.anim.MobAnimStates;
import com.flameforge.essentialcraft5.entity.mob.ECMonster;
import com.flameforge.essentialcraft5.hoanna.Radiation;
import com.flameforge.essentialcraft5.network.S2CGunBeamPacket;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesA;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesA.Config.ScavengerValues;
import com.flameforge.essentialcraft5.util.SpawnOrigin;
import com.flameforge.essentialcraft5.util.SpawnOrigin.Origin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Locale;
import java.util.UUID;

/**
 * 遗民拾荒者 {@code hoanna_scavenger}（第十三批 13d-N1，batch13.md §5.2；怪物稿 §2.4.5）：裹着破布和拼凑金属片、戴防毒面具、背铁皮罐的人形，
 * 城镇和矿井那个文明的残存者。恐惧之地、海纳尔沙漠里出没，<b>白天也在地表刷</b>（{@link #checkScavengerSpawnRules}）。
 * <ul>
 *   <li><b>两种武器</b>（生成时按随机数定一次、存盘 {@value #TAG_WEAPON}，读档不变）：撬棍（近战，{@code crowbar_damage}）或步枪
 *       （{@code rifle_aim_ticks} 刻瞄准、{@code rifle_range} 格以内、不生成实体的射线，{@code rifle_damage}，伤害类型 {@code mob_bolt}，
 *       隔着方块打不到）。攻击属性就是手里那件的伤害（精英 ×1.4）。没走初始化的（指令带了别的 NBT）第一刻按 UUID 补一件。</li>
 *   <li><b>辐射</b>：撬棍 / 步枪打实一下给真玩家加 {@code crowbar_dose} / {@code rifle_dose} 剂量，乘玩家自己的辐射易伤（盔甲、饰品照常减，不设下限；
 *       {@link #irradiate}，在 {@code ECHoannaNativesA.HoannaNativesAEvents} 里打实以后才加）。</li>
 *   <li><b>打针</b>：生命剩到 {@code stim_health} 以下时给自己打一针（再生 II {@code stim_ticks} 刻），冷却 {@code stim_cooldown} 刻、存盘。</li>
 *   <li>戴着面具：毒雾本来就只作用于玩家，它不受影响。</li>
 *   <li><b>精英「遗民队长」</b>：背一面展开式护盾，正面 90° 来的弹射物全挡；第一次被打到 {@code elite_flare_health} 以下时打信号弹（只一次，存盘），
 *       身边叫来 {@code elite_summon_count} 名拾荒者：来源先记「召唤」再走 Forge 的生成事件（{@code MOB_SUMMONED}），被取消的不进世界；
 *       叫来的不公平（关键掉落拿不到）、不出精英、不给经验、灵魂刻刀不收魂（基类规矩），身上记着队长的 UUID（{@value #TAG_CAPTAIN}）。</li>
 * </ul>
 * 生命、护甲、攻击在 {@link #finalizeSpawn} 里按配置设成基础值。动画五段：挥撬棍、举枪瞄准、开枪、打针、打信号弹（{@link #anims}）。
 */
public class HoannaScavengerEntity extends ECMonster {

    public static final int ANIM_SWING = 0;
    public static final int ANIM_AIM = 1;
    public static final int ANIM_SHOOT = 2;
    public static final int ANIM_STIM = 3;
    public static final int ANIM_FLARE = 4;

    /** 存档里记武器的键（小写名） */
    public static final String TAG_WEAPON = "weapon";
    /** 叫来的拾荒者身上记队长 UUID 的持久数据键 */
    public static final String TAG_CAPTAIN = EssentialCraft5.MODID + ":scavenger_captain";
    /** 数「自己叫来的」拾荒者时看多大范围 */
    public static final int MINION_RADIUS = 32;
    /** 步枪光束画面的颜色（枪械光束包的 balance：0 = 中性青色） */
    public static final int RIFLE_COLOR = 0;
    /** 护盾正面的半角余弦（正面 90°） */
    public static final double SHIELD_COS = Math.cos(Math.toRadians(45D));
    /** 打针的再生等级（0 起算，1 = 再生 II） */
    public static final int STIM_AMPLIFIER = 1;

    private static final String TAG_STIM_COOLDOWN = "stim_cooldown";
    private static final String TAG_FLARE_USED = "flare_used";

    private static final EntityDataAccessor<Byte> DATA_WEAPON = SynchedEntityData.defineId(HoannaScavengerEntity.class, EntityDataSerializers.BYTE);

    /** 手里那件 */
    public enum Weapon {
        CROWBAR,
        RIFLE;

        public String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Weapon byName(String name) {
            for (Weapon weapon : values()) {
                if (weapon.serializedName().equals(name)) {
                    return weapon;
                }
            }
            return CROWBAR;
        }

        public static Weapon byId(int id) {
            return id == RIFLE.ordinal() ? RIFLE : CROWBAR;
        }
    }

    public final MobAnimStates anims = new MobAnimStates(5).channel(ANIM_SWING, 1).channel(ANIM_AIM, 1).channel(ANIM_SHOOT, 1)
            .channel(ANIM_STIM, 1).channel(ANIM_FLARE, 1);

    /** 武器定了没有（初始化定的、存档读到的、或者第一刻按 UUID 补的） */
    private boolean weaponKnown;
    private int stimCooldown;
    private boolean flareUsed;

    public HoannaScavengerEntity(EntityType<? extends HoannaScavengerEntity> type, Level level) {
        super(type, level);
        this.xpReward = 10;
    }

    /** 属性默认值（生成时 {@link #applyConfiguredAttributes} 再按配置和武器改基础值） */
    public static AttributeSupplier.Builder createAttributes() {
        ScavengerValues defaults = ECHoannaNativesA.Config.SCAVENGER_DEFAULTS;
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, defaults.maxHealth())
                .add(Attributes.ARMOR, defaults.armor())
                .add(Attributes.ATTACK_DAMAGE, defaults.crowbarDamage())
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_WEAPON, (byte) Weapon.CROWBAR.ordinal());
    }

    // ================================================================== 生成规则

    /**
     * 自然生成的判定（附近数量上限在注册时另包一层）：非和平；<b>白天地表变体</b>——这一格头顶露天、y ≥ {@code surface_min_y} 时不看亮度
     * （原版生物的基本判定：脚下能站）；其余照原版怪物（夜里 / 暗处）。
     */
    public static boolean checkScavengerSpawnRules(EntityType<HoannaScavengerEntity> type, ServerLevelAccessor level, MobSpawnType reason,
                                                   BlockPos pos, RandomSource random) {
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return false;
        }
        if (isSurfaceSpot(level, pos)) {
            return Mob.checkMobSpawnRules(type, level, reason, pos, random);
        }
        return Monster.checkMonsterSpawnRules(type, level, reason, pos, random);
    }

    /** 这一格算不算白天地表变体的位置：头顶露天（看天光）、y ≥ {@code surface_min_y} */
    public static boolean isSurfaceSpot(LevelReader level, BlockPos pos) {
        return surfaceRule(pos.getY(), level.canSeeSky(pos), ECHoannaNativesA.Config.scavenger().surfaceMinY());
    }

    /** 纯函数：露天且 y ≥ minY */
    public static boolean surfaceRule(int y, boolean openSky, int minY) {
        return openSky && y >= minY;
    }

    /**
     * 原版怪物在亮处会被「走向价值」这一关拦下（{@code PathfinderMob.checkSpawnRules}），白天地表变体的位置放行；其余照旧。
     */
    @Override
    public boolean checkSpawnRules(LevelAccessor level, MobSpawnType reason) {
        if (isSurfaceSpot(level, blockPosition())) {
            return true;
        }
        return super.checkSpawnRules(level, reason);
    }

    // ================================================================== 生成与武器

    /** 生命、护甲、攻击（按手里那件）设成基础值、回满（生成时调；精英修饰符在这之后挂） */
    public void applyConfiguredAttributes() {
        ScavengerValues cfg = ECHoannaNativesA.Config.scavenger();
        setBase(Attributes.MAX_HEALTH, cfg.maxHealth());
        setBase(Attributes.ARMOR, cfg.armor());
        setBase(Attributes.ATTACK_DAMAGE, weapon() == Weapon.RIFLE ? cfg.rifleDamage() : cfg.crowbarDamage());
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
        setWeapon(level.getRandom().nextDouble() < ECHoannaNativesA.Config.scavenger().rifleChance() ? Weapon.RIFLE : Weapon.CROWBAR);
        applyConfiguredAttributes();
        return super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
    }

    public Weapon weapon() {
        return Weapon.byId(entityData.get(DATA_WEAPON));
    }

    /** 换武器（生成时、测试、展示厅用）；攻击属性的基础值跟着换成那件的伤害 */
    public void setWeapon(Weapon weapon) {
        entityData.set(DATA_WEAPON, (byte) weapon.ordinal());
        weaponKnown = true;
        ScavengerValues cfg = ECHoannaNativesA.Config.scavenger();
        setBase(Attributes.ATTACK_DAMAGE, weapon == Weapon.RIFLE ? cfg.rifleDamage() : cfg.crowbarDamage());
    }

    /** 没定武器的（跳过了初始化）按 UUID 补一件，同一只每次补的都一样 */
    private void ensureWeapon() {
        if (!weaponKnown) {
            setWeapon((getUUID().getLeastSignificantBits() & 1L) == 0L ? Weapon.CROWBAR : Weapon.RIFLE);
        }
    }

    // ================================================================== AI

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new RifleGoal());
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0D, false) {
            @Override
            public boolean canUse() {
                return weapon() == Weapon.CROWBAR && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return weapon() == Weapon.CROWBAR && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, HoannaScavengerEntity.class).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void tick() {
        if (!level().isClientSide) {
            ensureWeapon();
        }
        super.tick();
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (stimCooldown > 0) {
            stimCooldown--;
        }
        if (wantsStim()) {
            stim();
        }
        if (wantsFlare()) {
            fireFlare(getTarget());
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            anims.play(this, ANIM_SWING);
        }
        return hit;
    }

    // ================================================================== 步枪

    /** 步枪现在打不打得着 target：射程以内、眼睛到它身体中间没有方块挡着 */
    public boolean rifleCanHit(LivingEntity target) {
        double range = ECHoannaNativesA.Config.scavenger().rifleRange();
        Vec3 eye = getEyePosition();
        Vec3 center = target.getBoundingBox().getCenter();
        return eye.distanceToSqr(center) <= range * range && level().clip(new ClipContext(eye, center, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    /**
     * 开一枪（服务端）：打得着就让 target 挨一下 {@code mob_bolt}（攻击属性的值），画一道光束；打不着（太远、被方块挡住、不是技能目标）
     * 光束画到挡住的地方、什么都不打。返回打实了没有（{@code hurt} 的返回值）。
     */
    public boolean shootRifle(LivingEntity target) {
        if (!(level() instanceof ServerLevel server)) {
            return false;
        }
        Vec3 eye = getEyePosition();
        Vec3 center = target.getBoundingBox().getCenter();
        boolean clear = isSkillTarget(target) && rifleCanHit(target);
        Vec3 end = center;
        if (!clear) {
            HitResult block = level().clip(new ClipContext(eye, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            end = block.getLocation();
        }
        S2CGunBeamPacket.sendAround(server, getId(), eye, end, RIFLE_COLOR);
        playSound(ECHoannaNativesA.SOUND_SCAVENGER_SHOOT.get(), 1F, 0.9F + random.nextFloat() * 0.2F);
        anims.play(this, ANIM_SHOOT);
        return clear && target.hurt(ECDamageTypes.mobBolt(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
    }

    // ================================================================== 辐射

    /**
     * 打实以后给 player 加辐射（由 {@code ECHoannaNativesA.HoannaNativesAEvents} 调）：撬棍（直接实体是它的近战）加 {@code crowbar_dose}，
     * 步枪（{@code mob_bolt}、没有直接实体）加 {@code rifle_dose}，都乘 player 的辐射易伤（{@code Radiation.addFloored} 传下限 0：不设下限）；
     * 别的伤害不加。假玩家、创造 / 旁观、关了辐射的不加。返回加完以后的剂量（没加返回现在的剂量）。
     */
    public int irradiate(ServerPlayer player, DamageSource source) {
        ScavengerValues cfg = ECHoannaNativesA.Config.scavenger();
        int dose;
        if (source.getDirectEntity() == this && source.is(DamageTypes.MOB_ATTACK)) {
            dose = cfg.crowbarDose();
        } else if (source.getDirectEntity() == null && source.is(ECDamageTypes.MOB_BOLT)) {
            dose = cfg.rifleDose();
        } else {
            return Radiation.get(player);
        }
        return Radiation.addFloored(player, dose, 0F);
    }

    // ================================================================== 打针

    /** 该不该打针：开着、不在冷却、活着、生命到线以下 */
    public boolean wantsStim() {
        double threshold = ECHoannaNativesA.Config.scavenger().stimHealth();
        return threshold > 0D && stimCooldown <= 0 && isAlive() && getHealth() <= getMaxHealth() * threshold;
    }

    /** 打一针：再生 II、挂冷却 */
    public void stim() {
        ScavengerValues cfg = ECHoannaNativesA.Config.scavenger();
        addEffect(new MobEffectInstance(MobEffects.REGENERATION, cfg.stimTicks(), STIM_AMPLIFIER));
        stimCooldown = cfg.stimCooldown();
        anims.play(this, ANIM_STIM);
        playSound(ECHoannaNativesA.SOUND_SCAVENGER_STIM.get(), 1F, 1F);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY(0.6D), getZ(), 6, 0.3D, 0.4D, 0.3D, 0D);
        }
    }

    /** 打针的冷却还剩几刻 */
    public int stimCooldown() {
        return stimCooldown;
    }

    // ================================================================== 信号弹（精英）

    /** 该不该打信号弹：精英、开着、还没打过、生命到线以下 */
    public boolean wantsFlare() {
        ScavengerValues cfg = ECHoannaNativesA.Config.scavenger();
        return isElite() && !flareUsed && cfg.eliteSummonCount() > 0 && isAlive() && getHealth() <= getMaxHealth() * cfg.eliteFlareHealth();
    }

    public boolean flareUsed() {
        return flareUsed;
    }

    /** 叫来的拾荒者的队长 UUID；不是叫来的返回 null */
    @Nullable
    public UUID captain() {
        CompoundTag data = getPersistentData();
        return data.hasUUID(TAG_CAPTAIN) ? data.getUUID(TAG_CAPTAIN) : null;
    }

    /** {@value #MINION_RADIUS} 格以内它叫来的、还活着的拾荒者有几名 */
    public int minionCount() {
        UUID self = getUUID();
        return level().getEntitiesOfClass(HoannaScavengerEntity.class, getBoundingBox().inflate(MINION_RADIUS),
                s -> s != this && s.isAlive() && self.equals(s.captain())).size();
    }

    /**
     * 打信号弹（服务端，只一次）：在身边放出 {@code elite_summon_count} 名拾荒者。来源先记「召唤」再走 Forge 的生成事件，被别的模组 / 插件取消的不算、
     * 不留东西。不是精英、已经打过、配置是 0 时什么都不做。返回这次放成几名。
     */
    public int fireFlare(@Nullable LivingEntity target) {
        if (!(level() instanceof ServerLevel server) || !isElite() || flareUsed) {
            return 0;
        }
        int want = ECHoannaNativesA.Config.scavenger().eliteSummonCount();
        if (want <= 0) {
            return 0;
        }
        flareUsed = true;
        anims.play(this, ANIM_FLARE);
        playSound(ECHoannaNativesA.SOUND_SCAVENGER_FLARE.get(), 1.5F, 1F);
        server.sendParticles(ParticleTypes.FIREWORK, getX(), getY() + 3D, getZ(), 24, 0.3D, 0.6D, 0.3D, 0.05D);
        server.sendParticles(ParticleTypes.FLAME, getX(), getY() + 3D, getZ(), 8, 0.2D, 0.2D, 0.2D, 0.01D);
        int spawned = 0;
        for (int i = 0; i < want; i++) {
            HoannaScavengerEntity minion = ECHoannaNativesA.HOANNA_SCAVENGER.get().create(server);
            if (minion == null) {
                break;
            }
            Vec3 at = minionSpot(minion);
            if (at == null) {
                continue;
            }
            minion.moveTo(at.x, at.y, at.z, getYRot(), 0F);
            minion.getPersistentData().putUUID(TAG_CAPTAIN, getUUID());
            SpawnOrigin.set(minion, Origin.SUMMONED);
            ForgeEventFactory.onFinalizeSpawn(minion, server, server.getCurrentDifficultyAt(minion.blockPosition()), MobSpawnType.MOB_SUMMONED, null, null);
            if (minion.isSpawnCancelled()) {
                continue;
            }
            if (target != null) {
                minion.setTarget(target);
            }
            if (server.addFreshEntity(minion)) {
                spawned++;
            }
        }
        return spawned;
    }

    /** 叫来的那名放在哪：身边 1.5~3 格、地面上放得下它的点（往下最多找 3 格） */
    @Nullable
    private Vec3 minionSpot(HoannaScavengerEntity minion) {
        for (int i = 0; i < 10; i++) {
            double angle = random.nextDouble() * Math.PI * 2D;
            double radius = 1.5D + random.nextDouble() * 1.5D;
            BlockPos.MutableBlockPos pos = BlockPos.containing(getX() + Math.cos(angle) * radius, getY() + 1D, getZ() + Math.sin(angle) * radius).mutable();
            for (int dy = 0; dy < 4; dy++) {
                BlockPos below = pos.below();
                if (!level().getBlockState(below).getCollisionShape(level(), below).isEmpty()) {
                    Vec3 at = new Vec3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
                    AABB box = minion.getType().getDimensions().makeBoundingBox(at);
                    if (level().noCollision(minion, box) && !level().containsAnyLiquid(box)) {
                        return at;
                    }
                    break;
                }
                pos.move(0, -1, 0);
            }
        }
        return null;
    }

    // ================================================================== 护盾（精英）

    /** 遗民队长的护盾：正面来的弹射物整个挡掉（{@code hurt} 返回 false） */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && isElite() && source.is(DamageTypeTags.IS_PROJECTILE) && FrostbittenEntity.shieldFacing(this, source, SHIELD_COS)) {
            playSound(ECHoannaNativesA.SOUND_SCAVENGER_SHIELD.get(), 1F, 0.9F + random.nextFloat() * 0.2F);
            return false;
        }
        return super.hurt(source, amount);
    }

    // ================================================================== 杂项

    /** 叫来的不给经验（队长打信号弹只一次，但困住队长和叫来的一起磨也不该多拿） */
    @Override
    public int getExperienceReward() {
        return captain() != null ? 0 : super.getExperienceReward();
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (!anims.handle(id, tickCount)) {
            super.handleEntityEvent(id);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ECHoannaNativesA.SOUND_SCAVENGER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ECHoannaNativesA.SOUND_SCAVENGER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ECHoannaNativesA.SOUND_SCAVENGER_DEATH.get();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(TAG_WEAPON, weapon().serializedName());
        tag.putInt(TAG_STIM_COOLDOWN, stimCooldown);
        tag.putBoolean(TAG_FLARE_USED, flareUsed);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(TAG_WEAPON)) {
            // 只换同步数据和标记，不动攻击属性：属性存档里已经带着生成时的值（改了配置也不跟着变）
            entityData.set(DATA_WEAPON, (byte) Weapon.byName(tag.getString(TAG_WEAPON)).ordinal());
            weaponKnown = true;
        }
        stimCooldown = Math.max(0, tag.getInt(TAG_STIM_COOLDOWN));
        flareUsed = tag.getBoolean(TAG_FLARE_USED);
    }

    /** 拿步枪的：走到射程以内、看得见就站住瞄 {@code rifle_aim_ticks} 刻再开一枪，歇 {@code rifle_rest_ticks} 刻接着瞄 */
    private final class RifleGoal extends Goal {
        private int aimTicks;
        private int restTicks;
        private int repath;

        RifleGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            return weapon() == Weapon.RIFLE && target != null && target.isAlive() && canAttack(target);
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
            aimTicks = 0;
            repath = 0;
        }

        @Override
        public void stop() {
            aimTicks = 0;
            getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target == null) {
                return;
            }
            getLookControl().setLookAt(target, 30F, 30F);
            if (restTicks > 0) {
                restTicks--;
            }
            ScavengerValues cfg = ECHoannaNativesA.Config.scavenger();
            boolean canHit = rifleCanHit(target) && getSensing().hasLineOfSight(target);
            if (!canHit) {
                aimTicks = 0;
                if (--repath <= 0) {
                    getNavigation().moveTo(target, 1.0D);
                    repath = 10;
                }
                return;
            }
            getNavigation().stop();
            if (restTicks > 0) {
                return;
            }
            if (aimTicks == 0) {
                anims.play(HoannaScavengerEntity.this, ANIM_AIM);
                playSound(ECHoannaNativesA.SOUND_SCAVENGER_AIM.get(), 0.8F, 1F);
            }
            if (++aimTicks >= cfg.rifleAim()) {
                shootRifle(target);
                aimTicks = 0;
                restTicks = cfg.rifleRest();
            }
        }
    }
}
