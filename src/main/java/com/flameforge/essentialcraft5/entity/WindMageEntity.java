package com.flameforge.essentialcraft5.entity;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.registry.ECEntities;
import com.flameforge.essentialcraft5.registry.ECWindMage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 风法师（原作 EntityWindMage，168 行）：主世界夜里的远程怪，学徒 / 法师 / 大法师三种，射 {@link MRUArrowEntity}。
 * <p>
 * 照原作的：
 * <ul>
 *   <li>类型 0 学徒 / 1 法师 / 2 大法师，同步数据 + NBT 键 {@code Type}（字节）；自然生成、刷怪蛋、刷怪笼生成时三种等概率（原作 onInitialSpawn）。</li>
 *   <li>属性：生命 20、移速 0.25、跟踪 16（原作 EntityMob 默认值 + applyEntityAttributes）；碰撞箱 0.6 × 1.8（原作没调 setSize）。</li>
 *   <li>AI：只有远程（原作 setCombatTask 把近战删了、只加 EntityAIAttackRanged(this, 1, 20, 60, 15)）：15 格内看得见就射，间隔 20~60 tick（越近越快）；
 *       游荡、看玩家、环顾；被打就反击（不叫帮手）、主动找玩家。</li>
 *   <li>射箭（原作 attackEntityWithRangedAttack）：速度 1.6、散布 14 − 难度 × 4、瞄准点在目标身高 1/3 处再抬高水平距离 × 0.2，
 *       基础伤害 (类型 + 1) × 3，按原版箭的公式 ⌈速度 × 基础伤害⌉ 约 5 / 10 / 15 点；原作射箭不出声，这里也不出声。</li>
 *   <li>受伤 / 死亡用村民的声音；骑乘时身体朝向跟坐骑（原作 updateRidden）、乘坐偏移 −0.5（原作 getYOffset）；白天不燃烧（原作是 EntityMob，不是亡灵）。</li>
 *   <li>掉落表按类型三张（原作 LootTableRegistry.ENTITY_WINDMAGE_*）：{@link #LOOT_TABLES}；法师那张就是实体类型的默认表 {@code entities/wind_mage}。</li>
 * </ul>
 * 移植改动：
 * <ul>
 *   <li>自然生成加「附近数量上限」（policy P2 / P3：同屏数量要有上限）：服务端配置 {@code general.wind_mage.max_nearby}（默认 8）只
 *       在 {@code nearby_radius}（默认 48）格内（竖直一半）已经有这么多风法师时拒绝生成；0 = 不自然生成。刷怪表本身照原作权重 2、每群 1~6
 *       （{@code data/essentialcraft5/forge/biome_modifier/wind_mage_spawns.json}），一次最多 4 只（原版 getMaxSpawnClusterSize，原作 1.12 同样是 4）。</li>
 *   <li>刷怪判定排除蘑菇岛、深暗之域（policy §4.2：forge:add_spawns 用 {@code #minecraft:is_overworld} 会把它们也算进去，原作只挑「有怪物刷怪表」的群系，这两个原版都不刷怪物）。</li>
 *   <li>找玩家的目标 AI 用原版骷髅的写法（每 10 tick 随机判一次），原作每 tick 找；玩家感觉不到，省一点服务端开销。</li>
 *   <li>读档时类型夹到 0~3（原作读到别的值时没有掉落表、贴图按学徒画；3 是第十三批加的风暴术士）。</li>
 *   <li>网络：追踪距离 64 格照原作（clientTrackingRange 4），同步间隔用原版怪物的 3 tick（原作每 tick 同步一次）。</li>
 * </ul>
 * <p>
 * 第十三批 13c-M1 加了第 4 种：风暴术士（{@link #STORM_SORCERER}，batch13.md §5.2），只在自然刷出时按雷雨 / 海纳尔掷出来，
 * 规矩都在 {@link StormSorcerer}（属性修饰符、疾风弹、预警落雷、名字），掉落表 entities/wind_mage_storm。读档时类型夹到 0~3，老存档里的 0~2 照旧。
 * <p>
 * 风之号角召来的（第十批 10b-M1）身上记着召唤者（{@link #getSummonedBy}，NBT {@value #SUMMONED_BY_KEY}）：找玩家时只挑召唤者，
 * 每 20 tick 查一次该不该随风散去，进出世界时登记 / 划掉自己那一批；其余规矩（打不到旁人、只有召唤者亲手打死才掉落）见 {@link WindMageSummons}。
 * 自然生成的没有这个字段，行为不变。
 */
public class WindMageEntity extends Monster implements RangedAttackMob {

    public static final int APPRENTICE = 0;
    public static final int MAGE = 1;
    public static final int ARCHMAGE = 2;
    /** 风暴术士（第十三批 13c-M1）：雷雨时刷出的第 4 种，见 {@link StormSorcerer} */
    public static final int STORM_SORCERER = 3;
    public static final int TYPE_COUNT = 4;
    /** 原作的三种（学徒 / 法师 / 大法师），刷怪蛋、刷怪笼、风之号角只出这三种 */
    public static final int CLASSIC_TYPE_COUNT = 3;
    /** NBT 键（原作 "Type"） */
    public static final String TYPE_KEY = "Type";
    /** 风之号角召来的：召唤者 UUID 的 NBT 键（第十批 10b-M1） */
    public static final String SUMMONED_BY_KEY = "SummonedBy";

    /** 原作 EntityAIAttackRanged(this, 1D, 20, 60, 15F) */
    public static final int ATTACK_INTERVAL_MIN = 20;
    public static final int ATTACK_INTERVAL_MAX = 60;
    public static final float ATTACK_RADIUS = 15F;
    /** 原作 new EntityMRUArrow(world, this, 1.6F) 和 shoot(…, 1.6F, …) */
    public static final float ARROW_VELOCITY = 1.6F;

    /** 按类型的掉落表（原作 windmage_apprentice / windmage_normal / windmage_archmage；风暴术士 wind_mage_storm） */
    public static final List<ResourceLocation> LOOT_TABLES = List.of(
            EssentialCraft5.id("entities/wind_mage_apprentice"),
            EssentialCraft5.id("entities/wind_mage"),
            EssentialCraft5.id("entities/wind_mage_archmage"),
            EssentialCraft5.id("entities/wind_mage_storm"));
    /** 风暴术士的名字键 */
    public static final String STORM_NAME_KEY = "entity.essentialcraft5.wind_mage.storm";

    /** 自然生成时排除的群系（policy §4.2） */
    public static final List<ResourceKey<Biome>> EXCLUDED_BIOMES = List.of(Biomes.MUSHROOM_FIELDS, Biomes.DEEP_DARK);

    /** 登记给 SpawnPlacements 的判定：排除群系 → 原版怪物判定（亮度、难度、脚下方块）→ 附近数量上限 */
    public static final SpawnPlacements.SpawnPredicate<WindMageEntity> SPAWN_RULES = spawnRules(WindMageEntity::checkWindMageSpawnRules);

    private static final EntityDataAccessor<Byte> DATA_TYPE = SynchedEntityData.defineId(WindMageEntity.class, EntityDataSerializers.BYTE);

    /** 风之号角召来的：召唤者（只在服务端用，不同步；自然生成的为 null） */
    @Nullable
    private UUID summonedBy;

    /** 风暴术士的预警落雷（只在服务端，不存盘；{@link StormSorcerer}） */
    @Nullable
    Vec3 strikeAt;
    int strikeTimer;
    int lightningCooldown = 40;

    public WindMageEntity(EntityType<? extends WindMageEntity> type, Level level) {
        super(type, level);
    }

    /** 原作 applyEntityAttributes：移速 0.25；生命 20、跟踪 16、攻击 2 是 1.12 EntityMob 的默认值，和 1.20 的怪物默认值一样 */
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MOVEMENT_SPEED, 0.25D);
    }

    // ---------------------------------------------------------------- 生成判定

    /** 在 base 前面套上群系排除（给 GameTest 用 base 恒真来单独测群系排除） */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static SpawnPlacements.SpawnPredicate<WindMageEntity> spawnRules(SpawnPlacements.SpawnPredicate<WindMageEntity> base) {
        return ECEntities.notInBiomes(base, EXCLUDED_BIOMES.toArray(new ResourceKey[0]));
    }

    /** 原版怪物判定（{@link Monster#checkMonsterSpawnRules}，同原作 EntityMob.getCanSpawnHere）+ 附近数量上限 */
    public static boolean checkWindMageSpawnRules(EntityType<WindMageEntity> type, ServerLevelAccessor level, MobSpawnType reason,
                                                  BlockPos pos, RandomSource random) {
        return Monster.checkMonsterSpawnRules(type, level, reason, pos, random)
                && belowNearbyCap(level, pos, Config.maxNearby(), Config.nearbyRadius());
    }

    /**
     * pos 附近（水平 radius 格、竖直 radius / 2 格）的风法师少于 max 只时返回 true；max ≤ 0 一律 false。
     * 只在抽中风法师的那几次刷怪尝试里调（权重 2，约占主世界怪物刷怪的 0.3%），一次 AABB 查询。
     */
    public static boolean belowNearbyCap(ServerLevelAccessor level, BlockPos pos, int max, int radius) {
        if (max <= 0) {
            return false;
        }
        AABB box = new AABB(pos).inflate(radius, Math.max(1, radius / 2), radius);
        return level.getEntitiesOfClass(WindMageEntity.class, box).size() < max;
    }

    // ---------------------------------------------------------------- AI

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(4, new RangedAttackGoal(this, 1.0D, ATTACK_INTERVAL_MIN, ATTACK_INTERVAL_MAX, ATTACK_RADIUS));
        goalSelector.addGoal(5, new RandomStrollGoal(this, 1.0D));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        // 参数同原版骷髅（每 10 tick 随机判一次、要看得见）；召来的只挑召唤者（mayTarget）
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::mayTarget));
    }

    /** 找玩家时的筛选：自然生成的谁都打，召来的只打召唤者 */
    private boolean mayTarget(LivingEntity entity) {
        return summonedBy == null || summonedBy.equals(entity.getUUID());
    }

    /** 原作 attackEntityWithRangedAttack；风暴术士改射疾风弹 */
    @Override
    public void performRangedAttack(LivingEntity target, float distanceFactor) {
        if (getMageType() == STORM_SORCERER) {
            StormSorcerer.shootGust(this, target);
            return;
        }
        MRUArrowEntity arrow = new MRUArrowEntity(ECWindMage.MRU_ARROW.get(), level(), this);
        double dx = target.getX() - getX();
        double dy = target.getY(1.0D / 3.0D) - arrow.getY();
        double dz = target.getZ() - getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + horizontal * 0.2D, dz, ARROW_VELOCITY, 14 - level().getDifficulty().getId() * 4);
        arrow.setBaseDamage(arrowBaseDamage(getMageType()));
        level().addFreshEntity(arrow);
    }

    /** 原作 setDamage((getType() + 1) * 3)（只有原作三种射 MRU 箭） */
    public static double arrowBaseDamage(int type) {
        return (Mth.clamp(type, 0, CLASSIC_TYPE_COUNT - 1) + 1) * 3.0D;
    }

    // ---------------------------------------------------------------- 类型

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_TYPE, (byte) APPRENTICE);
    }

    public int getMageType() {
        return Mth.clamp(entityData.get(DATA_TYPE), 0, TYPE_COUNT - 1);
    }

    /** 设类型（夹到 0~3）；变成风暴术士时挂上它的属性并回满血 */
    public void setMageType(int type) {
        applyMageType(type, true);
    }

    private void applyMageType(int type, boolean refill) {
        int clamped = Mth.clamp(type, 0, TYPE_COUNT - 1);
        entityData.set(DATA_TYPE, (byte) clamped);
        if (!level().isClientSide) {
            StormSorcerer.applyModifiers(this, clamped == STORM_SORCERER, refill);
        }
    }

    public boolean isStormSorcerer() {
        return getMageType() == STORM_SORCERER;
    }

    /** 风暴术士换成自己的名字（不动自定义名） */
    @Override
    protected Component getTypeName() {
        return isStormSorcerer() ? Component.translatable(STORM_NAME_KEY) : super.getTypeName();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte(TYPE_KEY, (byte) getMageType());
        if (summonedBy != null) {
            tag.putUUID(SUMMONED_BY_KEY, summonedBy);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(TYPE_KEY)) {
            // 属性（含风暴术士的修饰符）和生命父类已经读回来了：按 UUID 摘了再挂，不改生命
            applyMageType(tag.getByte(TYPE_KEY), false);
        }
        summonedBy = tag.hasUUID(SUMMONED_BY_KEY) ? tag.getUUID(SUMMONED_BY_KEY) : null;
    }

    // ---------------------------------------------------------------- 风之号角召来的（第十批 10b-M1）

    /** 召唤者；自然生成、刷怪蛋生成的为 null */
    @Nullable
    public UUID getSummonedBy() {
        return summonedBy;
    }

    public void setSummonedBy(@Nullable UUID summoner) {
        this.summonedBy = summoner;
    }

    /** 召来的每 20 tick 查一次该不该随风散去（WindMageSummons.checkMage）；风暴术士的落雷倒计时、身上的电火花 */
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (isStormSorcerer() && random.nextInt(5) == 0) {
                level().addParticle(ParticleTypes.ELECTRIC_SPARK, getRandomX(0.5D), getRandomY(), getRandomZ(0.5D), 0D, 0D, 0D);
            }
            return;
        }
        if (summonedBy != null && !isRemoved() && tickCount % 20 == 0) {
            WindMageSummons.checkMage(this);
        }
        if (isStormSorcerer() && !isRemoved()) {
            StormSorcerer.tickStrike(this);
        }
    }

    /** 风暴术士：找时机标落雷点 */
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (isStormSorcerer()) {
            StormSorcerer.aiTick(this);
        }
    }

    /** 进世界（生成、读档、区块重新加载）：召来的登记回自己那一批 */
    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (summonedBy != null && !level().isClientSide) {
            WindMageSummons.track(this);
        }
    }

    /** 离开世界（死了、散了、区块卸载）：从自己那一批里划掉 */
    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        if (summonedBy != null && !level().isClientSide) {
            WindMageSummons.forget(this);
        }
    }

    /**
     * 原作 onInitialSpawn：先走父类，再在原作三种里随机；自然刷出的再掷一次风暴术士（{@link StormSorcerer#roll}）。
     * Forge 把它标成过时是让调用方改走 ForgeEventFactory.onFinalizeSpawn（那里会回调这个方法），覆写照旧（原版怪物都这么写）。
     */
    @SuppressWarnings("deprecation")
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData data, @Nullable CompoundTag tag) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data, tag);
        int type = level.getRandom().nextInt(CLASSIC_TYPE_COUNT);
        if (StormSorcerer.roll(this, level, level.getRandom())) {
            type = STORM_SORCERER;
        }
        setMageType(type);
        return result;
    }

    @Override
    protected ResourceLocation getDefaultLootTable() {
        return LOOT_TABLES.get(getMageType());
    }

    // ---------------------------------------------------------------- 杂项（照原作）

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.VILLAGER_DEATH;
    }

    /** 原作 updateRidden：骑着别的生物时身体朝向跟坐骑（同原版骷髅） */
    @Override
    public void rideTick() {
        super.rideTick();
        Entity vehicle = getControlledVehicle();
        if (vehicle instanceof PathfinderMob mob) {
            yBodyRot = mob.yBodyRot;
        }
    }

    /** 原作 getYOffset() − 0.5 */
    @Override
    public double getMyRidingOffset() {
        return -0.5D;
    }

    // ---------------------------------------------------------------- 配置

    /**
     * 服务端配置 {@code general.wind_mage}（ECWindMage.init 登记）。只管自然生成的数量上限；刷怪权重和每群数量是数据
     * （forge/biome_modifier/wind_mage_spawns.json），服主要改用数据包覆盖。
     */
    public static final class Config {
        public static final int DEFAULT_MAX_NEARBY = 8;
        public static final int DEFAULT_NEARBY_RADIUS = 48;

        @Nullable
        public static ForgeConfigSpec.IntValue maxNearbyValue;
        @Nullable
        public static ForgeConfigSpec.IntValue nearbyRadiusValue;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.comment("Wind Mage natural spawning.", "风法师自然生成").push("wind_mage");
            maxNearbyValue = builder.comment("Natural spawning stops while this many Wind Mages are already within nearby_radius blocks (0 = no natural spawning).",
                            "附近 nearby_radius 格内已经有这么多只风法师时不再自然生成（0 = 不自然生成）")
                    .defineInRange("max_nearby", DEFAULT_MAX_NEARBY, 0, 64);
            nearbyRadiusValue = builder.comment("Horizontal radius for max_nearby (vertical range is half of it).",
                            "max_nearby 的水平半径（竖直取一半）")
                    .defineInRange("nearby_radius", DEFAULT_NEARBY_RADIUS, 8, 128);
            builder.pop();
        }

        public static int maxNearby() {
            return maxNearbyValue == null ? DEFAULT_MAX_NEARBY : ECConfig.get(maxNearbyValue);
        }

        public static int nearbyRadius() {
            return nearbyRadiusValue == null ? DEFAULT_NEARBY_RADIUS : ECConfig.get(nearbyRadiusValue);
        }
    }
}
