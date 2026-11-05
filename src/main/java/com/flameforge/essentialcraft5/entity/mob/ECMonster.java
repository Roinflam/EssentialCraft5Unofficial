package com.flameforge.essentialcraft5.entity.mob;

import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECMobFramework;
import com.flameforge.essentialcraft5.util.ECEntityRules;
import com.flameforge.essentialcraft5.util.SpawnOrigin;
import com.flameforge.essentialcraft5.util.SpawnOrigin.Origin;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.Tags;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.ToDoubleFunction;

/**
 * 第十三批新怪物的基类（13c-Fa，batch13.md §5.1；policy §4.18「新怪物总则」）。13c、13d 的普通怪都继承它（飞的继承 {@link ECFlyingMonster}），
 * 首领经首领基类 {@code entity/boss/ECBossMonster} 也继承它（没有精英，技能目标换成名单判定）。
 * 这里只放所有新怪共用的规矩，招式、模型、掉落表归各自的类：
 * <ul>
 *   <li><b>来源</b>：读 {@link SpawnOrigin}（Forge 的 {@code FinalizeSpawn} 已经记好）；公平击杀条件、精英几率都按它判。</li>
 *   <li><b>玩家伤害占比</b>：{@link MobDamageShare} 在 {@code LivingDamageEvent} LOWEST 记（对所有 {@code Enemy} 都记，不只这个类），
 *       这里给 {@link #playerDamageShare()} 等几个便捷读法。</li>
 *   <li><b>技能目标</b>（{@link #isSkillTarget}）：范围技、锁定技只打生存 / 冒险模式的玩家和自己当前的目标；当前目标是村民、动物、盔甲架、同类、
 *       {@link ECEntityRules#isSpared} 的（起名的、拴绳的、驯服的……）照样不打。</li>
 *   <li><b>不碰世界</b>：不捡东西、不开门、不踩坏耕地（P1）。</li>
 *   <li><b>元素弱点</b>：实体类型标签 {@code weak_to_fire / weak_to_freezing / weak_to_lightning / weak_to_melee / resists_projectile}，
 *       {@link MobWeakness} 在 {@code LivingHurtEvent} NORMAL 乘倍率；一种类型里有几种变体（元素游灵的四种元素）时覆盖 {@link #hasTrait}。</li>
 *   <li><b>精英</b>（D23：每种固定一个精英变体）：同步数据字节 {@code variant}（0 普通、1 精英），存盘键同名。精英的生命 ×2.5、攻击 ×1.4、护甲 +4、击退抗性 +0.3
 *       （带固定 UUID 的永久属性修饰符，读档时先摘再挂，不会叠两层）、经验 ×3、体型 ×{@value #ELITE_SCALE}、同调点 ×3（在 {@code ECMobFramework.init} 登记）；
 *       名字走 {@link #getTypeName()}（语言键 {@code <实体名字键>.elite}，各路写在自己的语言文件里），<b>不调 setCustomName</b>：
 *       自定义名会让 {@code isSpared} 判真、永不消失。几率见 {@link #rollElite}；精英不能被灵魂刻刀收魂（{@code SoulScriberItem}）。
 *       首领（{@code #forge:bosses}）没有精英（{@link #hasEliteVariant}）。巢穴（13d）要指定精英时直接 {@link #setElite}。</li>
 * </ul>
 * 子类照常写 {@code registerGoals}、属性（{@code Monster.createMonsterAttributes()}）和刷怪规则（{@link MobSpawnRules}）。
 * 本模组代码自己放出来的怪先 {@code SpawnOrigin.set} 或走 {@code ForgeEventFactory.onFinalizeSpawn}，见 {@link SpawnOrigin} 类注释。
 */
public abstract class ECMonster extends Monster {

    /** 存盘键和同步数据都叫这个：0 普通、1 精英（以后要别的固定变体往后排） */
    public static final String TAG_VARIANT = "variant";
    public static final byte VARIANT_NORMAL = 0;
    public static final byte VARIANT_ELITE = 1;
    /** 精英的体型倍数。不做成配置：碰撞箱两边各算各的，改了配置没同步到的客户端会和服务端对不上 */
    public static final float ELITE_SCALE = 1.15F;

    /** 精英属性修饰符的固定 UUID（读档时按它摘掉再挂，所以不会重复） */
    public static final UUID ELITE_HEALTH_ID = UUID.fromString("7c1f6a52-2d3e-4b8a-9a1e-1e5c0d13c001");
    public static final UUID ELITE_ATTACK_ID = UUID.fromString("7c1f6a52-2d3e-4b8a-9a1e-1e5c0d13c002");
    public static final UUID ELITE_ARMOR_ID = UUID.fromString("7c1f6a52-2d3e-4b8a-9a1e-1e5c0d13c003");
    public static final UUID ELITE_KNOCKBACK_ID = UUID.fromString("7c1f6a52-2d3e-4b8a-9a1e-1e5c0d13c004");
    private static final String ELITE_MODIFIER_NAME = "essentialcraft5 elite";

    private static final EntityDataAccessor<Byte> DATA_VARIANT = SynchedEntityData.defineId(ECMonster.class, EntityDataSerializers.BYTE);

    /** 13d 的「海纳尔天灾时精英 ×2」之类：对一只要掷精英的怪返回倍数，几项相乘 */
    private static final List<ToDoubleFunction<Mob>> ELITE_CHANCE_MULTIPLIERS = new CopyOnWriteArrayList<>();

    protected ECMonster(EntityType<? extends ECMonster> type, Level level) {
        super(type, level);
        setCanPickUpLoot(false);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_VARIANT, VARIANT_NORMAL);
    }

    // ================================================================== 精英

    /** 这只是不是精英（别的生物一律 false） */
    public static boolean isElite(@Nullable Entity entity) {
        return entity instanceof ECMonster monster && monster.isElite();
    }

    public boolean isElite() {
        return entityData.get(DATA_VARIANT) == VARIANT_ELITE;
    }

    public byte getVariant() {
        return entityData.get(DATA_VARIANT);
    }

    /** 这种怪有没有精英变体：默认首领（{@code #forge:bosses}）没有，其余都有。子类可以覆盖（首领基类 {@code ECBossMonster} 恒为 false） */
    public boolean hasEliteVariant() {
        return !getType().is(Tags.EntityTypes.BOSSES);
    }

    /**
     * 设成精英 / 普通（巢穴、指令、测试用；自然生成的由 {@link #finalizeSpawn} 掷）。挂上 / 摘掉属性修饰符；变成精英时满血的照样满血。
     * 没有精英变体的种类设精英不起作用。
     */
    public void setElite(boolean elite) {
        if (elite && !hasEliteVariant()) {
            return;
        }
        applyVariant(elite ? VARIANT_ELITE : VARIANT_NORMAL);
    }

    private void applyVariant(byte variant) {
        boolean elite = variant == VARIANT_ELITE;
        float oldMax = getMaxHealth();
        boolean full = getHealth() >= oldMax;
        entityData.set(DATA_VARIANT, variant);
        boolean added = applyEliteModifiers(elite);
        if (elite && added && full) {
            setHealth(getMaxHealth());
        } else if (getHealth() > getMaxHealth()) {
            setHealth(getMaxHealth());
        }
    }

    /**
     * 精英的四个属性修饰符：先按固定 UUID 摘掉，精英再按当前配置挂上（读档时属性存档里已经带着它们，这样不会叠两层）。
     * 返回这次是不是新挂上了生命修饰符（读档前没有、现在有）。
     */
    private boolean applyEliteModifiers(boolean elite) {
        boolean hadHealth = hasModifier(Attributes.MAX_HEALTH, ELITE_HEALTH_ID);
        modifier(Attributes.MAX_HEALTH, ELITE_HEALTH_ID, elite, ECMobFramework.Config.eliteHealth() - 1D, AttributeModifier.Operation.MULTIPLY_BASE);
        modifier(Attributes.ATTACK_DAMAGE, ELITE_ATTACK_ID, elite, ECMobFramework.Config.eliteAttack() - 1D, AttributeModifier.Operation.MULTIPLY_BASE);
        modifier(Attributes.ARMOR, ELITE_ARMOR_ID, elite, ECMobFramework.Config.eliteArmor(), AttributeModifier.Operation.ADDITION);
        modifier(Attributes.KNOCKBACK_RESISTANCE, ELITE_KNOCKBACK_ID, elite, ECMobFramework.Config.eliteKnockback(), AttributeModifier.Operation.ADDITION);
        return elite && !hadHealth;
    }

    private boolean hasModifier(Attribute attribute, UUID id) {
        AttributeInstance instance = getAttribute(attribute);
        return instance != null && instance.getModifier(id) != null;
    }

    private void modifier(Attribute attribute, UUID id, boolean present, double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(id);
        if (present && amount != 0D) {
            instance.addPermanentModifier(new AttributeModifier(id, ELITE_MODIFIER_NAME, amount, operation));
        }
    }

    /**
     * 登记一个精英几率倍数来源（13d：海纳尔天灾时 ×2）。对一只正要掷精英的怪返回倍数，几项相乘；模组构造期或 FMLCommonSetup 里调。
     */
    public static void addEliteChanceMultiplier(ToDoubleFunction<Mob> multiplier) {
        ELITE_CHANCE_MULTIPLIERS.add(multiplier);
    }

    public static void removeEliteChanceMultiplier(ToDoubleFunction<Mob> multiplier) {
        ELITE_CHANCE_MULTIPLIERS.remove(multiplier);
    }

    /** 这只现在掷精英的几率：海纳尔用 {@code elite_chance_hoanna}，其余维度用 {@code elite_chance}，再乘登记的倍数，夹在 0~1 */
    public double eliteChance() {
        double chance = ECMobFramework.Config.eliteChance(ECHoanna.isHoanna(level()));
        for (ToDoubleFunction<Mob> multiplier : ELITE_CHANCE_MULTIPLIERS) {
            chance *= multiplier.applyAsDouble(this);
        }
        return Math.max(0D, Math.min(1D, chance));
    }

    /**
     * 生成时掷不掷精英：有精英变体、来源是自然 / 事件 / 结构 / 巢穴（刷怪笼、刷怪蛋、召唤、指令、首领本体、没记来源的都不掷，§0.4 第 20 条）、
     * 几率掷中、放大成精英体型以后这里放得下（{@link #eliteFits}）、{@code elite_radius} 以内同种精英不到 {@code elite_max_nearby} 只。
     */
    protected boolean rollElite(ServerLevelAccessor level, MobSpawnType reason) {
        if (!hasEliteVariant() || isElite()) {
            return false;
        }
        Origin origin = SpawnOrigin.of(this);
        if (origin == Origin.UNKNOWN) {
            origin = SpawnOrigin.classify(reason, false);
        }
        if (!rollsElite(origin)) {
            return false;
        }
        double chance = eliteChance();
        if (chance <= 0D || getRandom().nextDouble() >= chance) {
            return false;
        }
        if (!eliteFits(level)) {
            return false;
        }
        return elitesNearby(level) < ECMobFramework.Config.eliteMaxNearby();
    }

    /**
     * 在现在的位置放大到精英体型（×{@value #ELITE_SCALE}）以后放不放得下（方块、挡路的实体、世界边界）。
     * 自然刷怪的碰撞检查是按普通体型做的，精英的体型在 {@code finalizeSpawn} 里才变，那时还没进世界、不会挪去找空位：
     * 两格高的矿道里刷出来的矩阵游魂（1.9 → 2.185）当了精英，头就卡进天花板动不了。放不下的就不当精英。
     */
    public boolean eliteFits(CollisionGetter level) {
        EntityDimensions grown = getDimensions(getPose()).scale(ELITE_SCALE);
        return level.noCollision(this, grown.makeBoundingBox(position()));
    }

    /** 哪些来源会掷精英：自然、事件、结构、巢穴 */
    public static boolean rollsElite(Origin origin) {
        return origin == Origin.NATURAL || origin == Origin.EVENT || origin == Origin.STRUCTURE || origin == Origin.NEST;
    }

    /** {@code elite_radius} 以内同种的精英有几只（不算自己；世界生成时的区域查不到实体，按 0 算） */
    public int elitesNearby(ServerLevelAccessor level) {
        int radius = ECMobFramework.Config.eliteRadius();
        double radiusSq = (double) radius * radius;
        AABB box = getBoundingBox().inflate(radius);
        return level.getEntitiesOfClass(ECMonster.class, box,
                other -> other != this && other.getType() == getType() && other.isElite() && other.isAlive() && other.distanceToSqr(this) <= radiusSq).size();
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData spawnData, @Nullable CompoundTag dataTag) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
        if (rollElite(level, reason)) {
            setElite(true);
        }
        return data;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_VARIANT.equals(key)) {
            refreshDimensions();
        }
    }

    @Override
    public float getScale() {
        float scale = super.getScale();
        return isElite() ? scale * ELITE_SCALE : scale;
    }

    /** 精英换成自己的名字（{@code <实体名字键>.elite}），不动自定义名 */
    @Override
    protected Component getTypeName() {
        if (isElite()) {
            return Component.translatable(getType().getDescriptionId() + ".elite");
        }
        return super.getTypeName();
    }

    @Override
    public int getExperienceReward() {
        int base = super.getExperienceReward();
        return isElite() ? base * ECMobFramework.Config.eliteXp() : base;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte(TAG_VARIANT, getVariant());
    }

    /** 读档：属性（含精英修饰符）和生命已经由父类读回来了，这里只把变体设回去、按固定 UUID 摘了再挂，不会叠两层，也不改生命 */
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(TAG_VARIANT, Tag.TAG_ANY_NUMERIC)) {
            byte variant = tag.getByte(TAG_VARIANT);
            applyVariant(variant == VARIANT_ELITE && hasEliteVariant() ? VARIANT_ELITE : VARIANT_NORMAL);
        }
    }

    // ================================================================== 技能目标、弱点

    /** 这只怪的范围技 / 锁定技能不能打到 target（见类注释；任何 {@link Mob} 都能用这条规矩，见 {@link #isSkillTarget(Mob, LivingEntity)}） */
    public boolean isSkillTarget(@Nullable LivingEntity target) {
        return isSkillTarget(this, target);
    }

    /**
     * 技能目标过滤：生存 / 冒险模式的玩家，或者 self 当前的目标；当前目标是村民（含流浪商人）、动物、盔甲架、和 self 同种、
     * {@link ECEntityRules#isSpared} 的（起名的、拴绳的、驯服的……）也不打。旁观者、创造模式的玩家不打。
     */
    public static boolean isSkillTarget(Mob self, @Nullable LivingEntity target) {
        if (target == null || target == self || !target.isAlive() || target.isRemoved()) {
            return false;
        }
        if (target instanceof Player player) {
            return !player.isSpectator() && !player.isCreative();
        }
        if (target != self.getTarget()) {
            return false;
        }
        return !(target instanceof ArmorStand) && !(target instanceof AbstractVillager) && !(target instanceof Animal)
                && target.getType() != self.getType() && !ECEntityRules.isSpared(target);
    }

    /** box 里所有能打的技能目标 */
    public List<LivingEntity> skillTargets(AABB box) {
        return level().getEntitiesOfClass(LivingEntity.class, box, this::isSkillTarget);
    }

    /** 这只有没有某个特性标签（弱点 / 抗性）。默认看实体类型标签；一种类型里有几种变体的子类按变体覆盖 */
    public boolean hasTrait(TagKey<EntityType<?>> trait) {
        return getType().is(trait);
    }

    /** 这一下伤害按弱点 / 抗性乘的倍数（{@link MobWeakness}） */
    public float weaknessMultiplier(DamageSource source) {
        return MobWeakness.multiplier(this::hasTrait, source);
    }

    // ================================================================== 伤害占比

    /** 玩家（含宠物）打的有效伤害占它受的有效伤害的比例，0~1（{@link MobDamageShare}） */
    public float playerDamageShare() {
        return MobDamageShare.share(this);
    }

    /** 这名玩家（含他的宠物）对它打出的有效伤害 */
    public float playerDamage(UUID player) {
        return MobDamageShare.damageBy(this, player);
    }

    // ================================================================== 不碰世界

    @Override
    public boolean canPickUpLoot() {
        return false;
    }

    @Override
    public boolean wantsToPickUp(ItemStack stack) {
        return false;
    }

    /** 不踩坏耕地 */
    @Override
    public boolean canTrample(BlockState state, BlockPos pos, float fallDistance) {
        return false;
    }

    /** 地面寻路不开门（门开着照样走过去） */
    @Override
    protected PathNavigation createNavigation(Level level) {
        GroundPathNavigation navigation = new GroundPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        return navigation;
    }

    /** 服务端世界（给子类少写一次判断） */
    @Nullable
    protected ServerLevel serverLevel() {
        return level() instanceof ServerLevel server ? server : null;
    }
}
