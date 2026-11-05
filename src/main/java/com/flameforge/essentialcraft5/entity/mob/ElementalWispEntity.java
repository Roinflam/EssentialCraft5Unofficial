package com.flameforge.essentialcraft5.entity.mob;

import com.flameforge.essentialcraft5.entity.anim.MobAnimStates;
import com.flameforge.essentialcraft5.registry.ECEntities;
import com.flameforge.essentialcraft5.registry.ECWildMobs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.EnumSet;
import java.util.Locale;

/**
 * 元素游灵（13c-M1，batch13.md §5.2）：主世界地下（y ≤ 40、看不见天）的小飞怪，生命 12、无护甲。按出生那一格的群系分火、水、土、风四种元素
 * （{@link Element#forBiome}），同一种实体、同一张灰度贴图，渲染时按元素染色。
 * <ul>
 *   <li><b>打法</b>：绕着目标飞（半径 3.5 格），每隔 2~3.5 秒停住蓄力半秒、再直线冲过去；撞上是一下普通近战（吃护甲），打实了带上元素效果
 *       （{@link #applyElementHit}：火点燃、水缓慢、土挖掘疲劳、风把人掀起来），撞完弹开。</li>
 *   <li><b>元素克制</b>（{@link #hasTrait}，倍率见 {@code entity/mob/MobWeakness}）：火怕冰冻、水怕雷击、土怕近身砍、风怕火，风还不怕箭（弹射物 ×0.5）。
 *       火之游灵不怕火、碰水和雨会掉血（同烈焰人）。</li>
 *   <li><b>精英「元素聚灵」</b>：绕飞时每 2 秒（{@code elite_bolt_interval}）按火、水、土、风的顺序轮着射一发元素弹（{@link WispBoltEntity}，
 *       {@code mob_bolt}，带那种元素的效果）；冲撞照旧。</li>
 *   <li><b>生成</b>：{@link #SPAWN_RULES}（排除蘑菇岛、深暗之域 → y ≤ {@code max_y}、看不见天、原版怪物判定 → 32 格内同种不到 4 只）；
 *       刷怪表权重 12、每群 1~2、刷怪花费（电荷 0.7 / 预算 0.15）是数据。掉落表 entities/elemental_wisp：对应元素的魔晶 50%（公平击杀 + 按人递减 overworld_common），
 *       精英另掉两颗随机魔晶（递减组 elite）。</li>
 * </ul>
 * 元素存档键 {@value #TAG_ELEMENT}（小写元素名）；没写元素的（{@code /summon} 带了别的 NBT、跳过了初始化）第一刻按脚下的群系补上。
 */
public class ElementalWispEntity extends ECFlyingMonster {

    public static final String TAG_ELEMENT = "Element";

    /** 动画段：蓄力、冲撞、施法（精英射弹） */
    public static final int ANIM_CHARGE = 0;
    public static final int ANIM_DASH = 1;
    public static final int ANIM_CAST = 2;

    /** 绕飞半径（格） */
    public static final double ORBIT_RADIUS = 3.5D;
    /** 蓄力刻数 */
    public static final int CHARGE_TICKS = 10;
    /** 冲撞最多飞几刻 */
    public static final int DASH_TICKS = 14;
    /** 冲撞速度（格 / 刻） */
    public static final double DASH_SPEED = 0.85D;

    /** 自然生成的判定（见类注释） */
    public static final SpawnPlacements.SpawnPredicate<ElementalWispEntity> SPAWN_RULES = spawnRules(ElementalWispEntity::checkWispSpawnRules);

    private static final EntityDataAccessor<Byte> DATA_ELEMENT = SynchedEntityData.defineId(ElementalWispEntity.class, EntityDataSerializers.BYTE);

    public final MobAnimStates anims = new MobAnimStates(3).channel(ANIM_CHARGE, 1).channel(ANIM_DASH, 1).channel(ANIM_CAST, 2);

    /** 元素定了没有（存档里读到、初始化时按群系定、或者第一刻补上） */
    private boolean elementKnown;
    /** 精英：下一发是第几种元素、还要等几刻 */
    private int boltIndex;
    private int boltCooldown;

    public ElementalWispEntity(EntityType<? extends ElementalWispEntity> type, Level level) {
        super(type, level);
        xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 12D)
                .add(Attributes.ARMOR, 0D)
                .add(Attributes.FLYING_SPEED, 0.55D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 3D)
                .add(Attributes.FOLLOW_RANGE, 24D);
    }

    // ================================================================== 元素

    /** 四种元素：存档名、染色、对应魔晶的注册名 */
    public enum Element {
        FIRE("fire", 0xFF7A33),
        WATER("water", 0x3FA9FF),
        EARTH("earth", 0xB58A4C),
        AIR("air", 0xD8F6FF);

        private final String id;
        private final int color;

        Element(String id, int color) {
            this.id = id;
            this.color = color;
        }

        public String id() {
            return id;
        }

        /** 染色 RGB（身体、发光层、粒子都用它） */
        public int color() {
            return color;
        }

        /** 对应的魔晶（掉落表里的物品，fire_drops 等） */
        public String dropId() {
            return id + "_drops";
        }

        public Vector3f rgb() {
            return new Vector3f(((color >> 16) & 0xFF) / 255F, ((color >> 8) & 0xFF) / 255F, (color & 0xFF) / 255F);
        }

        public static Element byIndex(int index) {
            Element[] all = values();
            return all[Math.floorMod(index, all.length)];
        }

        @Nullable
        public static Element byId(String id) {
            for (Element element : values()) {
                if (element.id.equals(id.toLowerCase(Locale.ROOT))) {
                    return element;
                }
            }
            return null;
        }

        /**
         * 出生群系 → 元素，按这个顺序判：海洋、河流、沙滩、沼泽、红树林沼泽、繁茂洞穴是水（冻洋也算水）；
         * 山地、丘陵（{@code #is_mountain}、{@code #is_hill}）是风；基础温度 ≥ 1（沙漠、恶地、热带草原）是火；
         * 温度 &lt; 0.15（下雪的）是风；其余（平原、森林、针叶林、溶洞……）是土。
         * 山地排在温度前面：裸岩山峰的基础温度正好是 1.0，先判温度就成了火，和「山地是风」对不上。
         */
        public static Element forBiome(Holder<Biome> biome) {
            if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_BEACH)
                    || biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP) || biome.is(Biomes.LUSH_CAVES)) {
                return WATER;
            }
            if (biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_HILL)) {
                return AIR;
            }
            float temperature = biome.value().getBaseTemperature();
            if (temperature >= 1.0F) {
                return FIRE;
            }
            return temperature < 0.15F ? AIR : EARTH;
        }

        /** 这种元素的游灵怕什么、抗什么（{@link #hasTrait}） */
        public boolean hasTrait(TagKey<EntityType<?>> trait) {
            return switch (this) {
                case FIRE -> trait == MobWeakness.WEAK_TO_FREEZING;
                case WATER -> trait == MobWeakness.WEAK_TO_LIGHTNING;
                case EARTH -> trait == MobWeakness.WEAK_TO_MELEE;
                case AIR -> trait == MobWeakness.WEAK_TO_FIRE || trait == MobWeakness.RESISTS_PROJECTILE;
            };
        }
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_ELEMENT, (byte) Element.EARTH.ordinal());
    }

    public Element getElement() {
        return Element.byIndex(entityData.get(DATA_ELEMENT));
    }

    public void setElement(Element element) {
        entityData.set(DATA_ELEMENT, (byte) element.ordinal());
        elementKnown = true;
    }

    /** 按脚下这一格的群系定元素 */
    public void pickElementFromBiome() {
        setElement(Element.forBiome(level().getBiome(blockPosition())));
    }

    @Override
    public boolean hasTrait(TagKey<EntityType<?>> trait) {
        return getElement().hasTrait(trait) || super.hasTrait(trait);
    }

    /** 火之游灵不怕火 */
    @Override
    public boolean fireImmune() {
        return getElement() == Element.FIRE || super.fireImmune();
    }

    /** 火之游灵碰水、淋雨掉血（同烈焰人、末影人） */
    @Override
    public boolean isSensitiveToWater() {
        return getElement() == Element.FIRE;
    }

    // ================================================================== 生成

    /** 在 base 外面套上附近数量上限和群系排除（给 GameTest 用 base 恒真来单独测这两层） */
    public static SpawnPlacements.SpawnPredicate<ElementalWispEntity> spawnRules(SpawnPlacements.SpawnPredicate<ElementalWispEntity> base) {
        return ECEntities.notInBiomes(MobSpawnRules.capped(base, ECWildMobs.Config::wispNearbyRadius, ECWildMobs.Config::wispMaxNearby),
                Biomes.MUSHROOM_FIELDS, Biomes.DEEP_DARK);
    }

    /** 高度、看不见天、原版怪物判定（亮度、难度、脚下方块）；附近数量上限由 {@link #SPAWN_RULES} 外面那层管 */
    public static boolean checkWispSpawnRules(EntityType<ElementalWispEntity> type, ServerLevelAccessor level, MobSpawnType reason,
                                              BlockPos pos, RandomSource random) {
        return placeAllowed(level, pos) && Monster.checkMonsterSpawnRules(type, level, reason, pos, random);
    }

    /** 只看位置的两条：y ≤ {@code max_y}、看不见天（地下、洞穴里、屋顶下） */
    public static boolean placeAllowed(LevelReader level, BlockPos pos) {
        return pos.getY() <= ECWildMobs.Config.wispMaxY() && !level.canSeeSky(pos);
    }

    @Nullable
    @Override
    @SuppressWarnings("deprecation")
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData spawnData, @Nullable CompoundTag dataTag) {
        setElement(Element.forBiome(level.getBiome(blockPosition())));
        return super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(TAG_ELEMENT, getElement().id());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(TAG_ELEMENT, Tag.TAG_STRING)) {
            Element element = Element.byId(tag.getString(TAG_ELEMENT));
            if (element != null) {
                setElement(element);
            }
        }
    }

    // ================================================================== AI

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new WispAttackGoal(this));
        goalSelector.addGoal(6, new WaterAvoidingRandomFlyingGoal(this, 1.0D));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, null));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (random.nextInt(3) == 0) {
                Vector3f rgb = getElement().rgb();
                level().addParticle(new DustParticleOptions(rgb, 0.8F), getRandomX(0.4D), getY() + 0.2D + random.nextDouble() * 0.4D,
                        getRandomZ(0.4D), 0D, -0.02D, 0D);
            }
        } else if (!elementKnown) {
            pickElementFromBiome();
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (!anims.handle(id, tickCount)) {
            super.handleEntityEvent(id);
        }
    }

    /** 冲撞撞上：普通近战（吃护甲），打实了带元素效果 */
    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living) {
            applyElementHit(living, getElement(), living.position().subtract(position()), this);
        }
        return hit;
    }

    /**
     * 元素效果（冲撞、元素弹共用）：火点燃、水缓慢 I、土挖掘疲劳 I，各 {@code effect_seconds} 秒；风沿 push 方向把人推开并往上掀
     * （按击退抗性打折，不看秒数）。source 记为效果的来源（打出它的游灵）。
     */
    public static void applyElementHit(LivingEntity target, Element element, Vec3 push, @Nullable Entity source) {
        int seconds = ECWildMobs.Config.wispEffectSeconds();
        switch (element) {
            case FIRE -> {
                if (seconds > 0) {
                    target.setSecondsOnFire(seconds);
                }
            }
            case WATER -> {
                if (seconds > 0) {
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, seconds * 20, 0), source);
                }
            }
            case EARTH -> {
                if (seconds > 0) {
                    target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, seconds * 20, 0), source);
                }
            }
            case AIR -> {
                double factor = 1D - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
                if (factor > 0D) {
                    Vec3 flat = new Vec3(push.x, 0D, push.z);
                    Vec3 dir = flat.lengthSqr() < 1.0E-6D ? Vec3.ZERO : flat.normalize().scale(0.6D * factor);
                    target.push(dir.x, 0.45D * factor, dir.z);
                    target.hurtMarked = true;
                }
            }
        }
    }

    /** 精英：射一发元素弹，元素按火、水、土、风轮着来 */
    public WispBoltEntity fireBolt(LivingEntity target) {
        Element element = Element.byIndex(boltIndex++);
        WispBoltEntity bolt = new WispBoltEntity(level(), this, element);
        double dx = target.getX() - bolt.getX();
        double dy = target.getY(0.5D) - bolt.getY();
        double dz = target.getZ() - bolt.getZ();
        bolt.shoot(dx, dy, dz, WispBoltEntity.SPEED, 2F);
        level().addFreshEntity(bolt);
        anims.play(this, ANIM_CAST);
        playSound(ECWildMobs.WISP_BOLT_SHOOT.get(), 0.8F, 1F);
        return bolt;
    }

    /** 下一发会是什么元素（测试、展示厅用） */
    public Element nextBoltElement() {
        return Element.byIndex(boltIndex);
    }

    private void tickBolt(LivingEntity target, boolean canSee) {
        if (!isElite()) {
            return;
        }
        if (boltCooldown > 0) {
            boltCooldown--;
            return;
        }
        if (canSee && isSkillTarget(target)) {
            fireBolt(target);
            boltCooldown = ECWildMobs.Config.wispBoltInterval();
        }
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return 2;
    }

    // ================================================================== 声音

    @Override
    protected SoundEvent getAmbientSound() {
        return ECWildMobs.WISP_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ECWildMobs.WISP_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ECWildMobs.WISP_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 0.6F;
    }

    // ================================================================== 绕飞与冲撞

    /** 冲撞的四个阶段 */
    public enum Phase { ORBIT, CHARGE, DASH, RECOVER }

    /**
     * 有目标时：绕飞 → 蓄力（{@value #CHARGE_TICKS} 刻，悬停）→ 冲撞（直线，最多 {@value #DASH_TICKS} 刻）→ 撞上或撞墙后弹开缓 15 刻 → 绕飞。
     * 看不见目标或离得远时按寻路追过去，不蓄力。精英绕飞时顺带射元素弹。
     */
    static final class WispAttackGoal extends Goal {

        private final ElementalWispEntity wisp;
        private Phase phase = Phase.ORBIT;
        private int timer;
        private int cooldown;
        private int repath;
        private float angle;
        private float spin = 1F;
        private Vec3 dashDir = Vec3.ZERO;

        WispAttackGoal(ElementalWispEntity wisp) {
            this.wisp = wisp;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = wisp.getTarget();
            return target != null && target.isAlive() && wisp.canAttack(target);
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = wisp.getTarget();
            return target != null && target.isAlive() && wisp.canAttack(target) && wisp.distanceToSqr(target) < 32D * 32D;
        }

        @Override
        public void start() {
            LivingEntity target = wisp.getTarget();
            phase = Phase.ORBIT;
            cooldown = 30 + wisp.getRandom().nextInt(30);
            spin = wisp.getRandom().nextBoolean() ? 1F : -1F;
            if (target != null) {
                angle = (float) Mth.atan2(wisp.getZ() - target.getZ(), wisp.getX() - target.getX());
            }
        }

        @Override
        public void stop() {
            phase = Phase.ORBIT;
            wisp.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = wisp.getTarget();
            if (target == null) {
                return;
            }
            wisp.getLookControl().setLookAt(target, 30F, 30F);
            switch (phase) {
                case ORBIT -> orbit(target);
                case CHARGE -> {
                    wisp.getNavigation().stop();
                    wisp.setDeltaMovement(wisp.getDeltaMovement().scale(0.5D));
                    if (--timer <= 0) {
                        dashDir = target.getBoundingBox().getCenter().subtract(wisp.getBoundingBox().getCenter()).normalize();
                        phase = Phase.DASH;
                        timer = DASH_TICKS;
                        wisp.anims.play(wisp, ANIM_DASH);
                        wisp.playSound(ECWildMobs.WISP_DASH.get(), 0.8F, 1F);
                    }
                }
                case DASH -> {
                    wisp.setDeltaMovement(dashDir.scale(DASH_SPEED));
                    if (wisp.getBoundingBox().inflate(0.35D).intersects(target.getBoundingBox())) {
                        wisp.doHurtTarget(target);
                        recover(dashDir.scale(-0.45D).add(0D, 0.2D, 0D));
                    } else if (wisp.horizontalCollision || wisp.verticalCollision || --timer <= 0) {
                        recover(Vec3.ZERO);
                    }
                }
                case RECOVER -> {
                    if (--timer <= 0) {
                        phase = Phase.ORBIT;
                        cooldown = 40 + wisp.getRandom().nextInt(30);
                    }
                }
            }
        }

        private void orbit(LivingEntity target) {
            angle += 0.09F * spin;
            boolean canSee = wisp.getSensing().hasLineOfSight(target);
            double distSq = wisp.distanceToSqr(target);
            if (canSee && distSq < 144D) {
                double bob = Mth.sin(wisp.tickCount * 0.1F) * 0.4D;
                wisp.getMoveControl().setWantedPosition(target.getX() + Mth.cos(angle) * ORBIT_RADIUS,
                        target.getY() + target.getBbHeight() * 0.6D + 0.6D + bob, target.getZ() + Mth.sin(angle) * ORBIT_RADIUS, 1.0D);
            } else if (--repath <= 0) {
                repath = 10;
                wisp.getNavigation().moveTo(target, 1.0D);
            }
            wisp.tickBolt(target, canSee);
            if (--cooldown <= 0 && canSee && distSq < 100D) {
                phase = Phase.CHARGE;
                timer = CHARGE_TICKS;
                wisp.getNavigation().stop();
                wisp.anims.play(wisp, ANIM_CHARGE);
                wisp.playSound(ECWildMobs.WISP_CHARGE.get(), 0.7F, 1F);
            }
        }

        private void recover(Vec3 bounce) {
            wisp.setDeltaMovement(bounce);
            phase = Phase.RECOVER;
            timer = 15;
        }
    }

    /** 这一刻在冲撞的哪个阶段（测试用：AI 目标里那份） */
    @Nullable
    public Phase attackPhase() {
        return goalSelector.getAvailableGoals().stream().map(wrapped -> wrapped.getGoal())
                .filter(goal -> goal instanceof WispAttackGoal).map(goal -> ((WispAttackGoal) goal).phase).findFirst().orElse(null);
    }

    /** 测试用：这一格的群系 → 元素 */
    public static Element elementAt(Level level, BlockPos pos) {
        return Element.forBiome(level.getBiome(pos));
    }

    /** 测试用：按群系键取元素 */
    public static Element elementOf(Level level, ResourceKey<Biome> biome) {
        return Element.forBiome(level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME).getHolderOrThrow(biome));
    }
}
