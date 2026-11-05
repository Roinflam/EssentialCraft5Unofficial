package com.flameforge.essentialcraft5.entity;

import com.flameforge.essentialcraft5.registry.ECHologram;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * 玩家克隆体（原作 {@code EntityPlayerClone}，166 行）：全息投影加法攻击时生成在每个参战者身边，长着被克隆者的样子，一碰就碎（生命 2），
 * 只活 {@value #LIFETIME} 刻（batch8.md §4.1）。
 * <ul>
 *   <li>继承 {@link Monster}（原作继承僵尸：会叫增援，增援是真僵尸、有掉落，还能把村民变成僵尸村民、捡东西吞掉）；
 *       近战，不破门、不捡东西、不踩耕地、不掉落、不给经验，和平难度消失，不存盘（注册时 {@code noSave}）。</li>
 *   <li>移速直接写进属性：0.23 × 1.8（原作一直挂着速度 IV，等于这个数，还会被喝牛奶清掉）。</li>
 *   <li>目标：只追生成它的那只全息投影名单里的参战者；刷怪蛋出来的（没有 Boss）追最近的生存玩家。生成它的 Boss 没了（死了、消失了）它也跟着消失。</li>
 *   <li>攻击力 = 3（原作僵尸）+ 被克隆者主手物品的攻击加成 + 锋利类附魔加成，封顶 {@code general.hologram.clone_max_attack_damage}（D2 = A，默认 10；
 *       难度缩放照原版，困难 ×1.5）。拿模组武器的玩家，原作的克隆体能一下打出上百点。</li>
 *   <li><b>装备只做外观</b>：服务端六个装备格一直是空的，被克隆者的主副手和四件盔甲的复制品放在同步数据里，客户端渲染时才拿出来（{@link #getItemBySlot}）。
 *       原作把主手的真物品拷了一份给怪，别的模组的缴械、偷取效果能把它变成真东西；副手和盔甲那几行三元式写反了，从来拷不到，这里六格都拷。</li>
 * </ul>
 * 皮肤：客户端按被克隆者的 UUID 取（{@code client/render/PlayerCloneRenderer}；玩家不在线取不到时用默认皮肤，原作这里会崩客户端）。
 */
public class PlayerCloneEntity extends Monster {

    /** 活多少刻（原作 ticksExisted % 200 == 0 时 setDead） */
    public static final int LIFETIME = 200;
    /** 原作僵尸的基础攻击力 */
    public static final double BASE_ATTACK = 3D;
    /** 原作每刻挂速度 IV：0.23 × (1 + 0.2 × 4) */
    public static final double MOVEMENT_SPEED = 0.23D * 1.8D;
    public static final double MAX_HEALTH = 2D;
    /** 找参战者的距离（克隆体生成在参战者 6 格内，参战者离 Boss 不超过 24 格） */
    public static final double FOLLOW_RANGE = 32D;

    private static final EntityDataAccessor<Optional<UUID>> DATA_CLONED =
            SynchedEntityData.defineId(PlayerCloneEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    @SuppressWarnings("unchecked")
    private static final EntityDataAccessor<ItemStack>[] DATA_LOOK = new EntityDataAccessor[SLOTS.length];

    static {
        for (int i = 0; i < SLOTS.length; i++) {
            DATA_LOOK[i] = SynchedEntityData.defineId(PlayerCloneEntity.class, EntityDataSerializers.ITEM_STACK);
        }
    }

    /** 生成它的全息投影（不存盘：克隆体本身不存盘） */
    @Nullable
    private HologramEntity boss;
    /** 由 Boss 生成的：Boss 没了就跟着消失（刷怪蛋出来的为假） */
    private boolean boundToBoss;
    /** 展示厅用：不按寿命、不跟着 Boss 消失 */
    private boolean showroomKeep;

    public PlayerCloneEntity(EntityType<? extends PlayerCloneEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED)
                .add(Attributes.ATTACK_DAMAGE, BASE_ATTACK)
                .add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE);
    }

    /**
     * 在 at 生成一只照着 cloned 的克隆体（服务端）：外观六格、皮肤 UUID、攻击力都在加进世界之前设好。生成被别的模组取消时返回 null。
     */
    @Nullable
    public static PlayerCloneEntity spawn(ServerLevel level, @Nullable HologramEntity boss, Player cloned, Vec3 at) {
        PlayerCloneEntity clone = ECHologram.PLAYER_CLONE.get().create(level);
        if (clone == null) {
            return null;
        }
        clone.moveTo(at.x, at.y, at.z, cloned.getYRot(), cloned.getXRot());
        clone.setYHeadRot(cloned.getYHeadRot());
        clone.copyLook(cloned);
        clone.boss = boss;
        clone.boundToBoss = boss != null;
        return level.addFreshEntity(clone) && !clone.isRemoved() ? clone : null;
    }

    /** 照着 cloned 设外观（六格复制品、皮肤）和攻击力；服务端装备格不动（一直是空的） */
    public void copyLook(Player cloned) {
        for (int i = 0; i < SLOTS.length; i++) {
            ItemStack stack = cloned.getItemBySlot(SLOTS[i]);
            entityData.set(DATA_LOOK[i], stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        }
        entityData.set(DATA_CLONED, Optional.of(cloned.getUUID()));
        AttributeInstance attack = getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) {
            attack.setBaseValue(attackDamageFor(cloned.getMainHandItem(), ECHologram.Config.values().cloneMaxAttackDamage()));
        }
    }

    /**
     * 克隆体的攻击力：3 + 这件主手物品的攻击加成（主手修饰符里 ADDITION 的那几条）+ 锋利类附魔加成，封顶 cap（≤ 0 时不封顶）。
     * 下界合金剑是 3 + 7 = 10，锋利 V 再 + 3。
     */
    public static double attackDamageFor(ItemStack weapon, double cap) {
        double damage = BASE_ATTACK;
        if (!weapon.isEmpty()) {
            for (AttributeModifier modifier : weapon.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE)) {
                if (modifier.getOperation() == AttributeModifier.Operation.ADDITION) {
                    damage += modifier.getAmount();
                }
            }
            damage += EnchantmentHelper.getDamageBonus(weapon, MobType.UNDEFINED);
        }
        return cap > 0D ? Math.min(damage, cap) : damage;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_CLONED, Optional.empty());
        for (EntityDataAccessor<ItemStack> look : DATA_LOOK) {
            entityData.define(look, ItemStack.EMPTY);
        }
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1D, false));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1D));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isTarget));
    }

    /** 生成它的全息投影（刷怪蛋出来的、Boss 已经卸载的为 null；服务端） */
    @Nullable
    public HologramEntity boss() {
        return boss;
    }

    /** 追谁：有 Boss 的只追它名单里的参战者，没有的（刷怪蛋）追最近的玩家（原版另外跳过创造 / 旁观） */
    public boolean isTarget(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        HologramEntity owner = boss;
        return !boundToBoss || (owner != null && owner.isParticipant(player.getUUID()));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        // 原作 onLivingUpdate 每刻 extinguish：克隆体不着火
        clearFire();
        HologramEntity owner = boss;
        if (!showroomKeep && (tickCount >= LIFETIME || (boundToBoss && (owner == null || owner.isRemoved() || owner.isDeadOrDying())))) {
            discard();
        }
    }

    /** 展示厅用：keep 为真时不按寿命消失、Boss 没了也留着（服务端调；配合 NoAI 摆姿势拍照） */
    public void setShowroomKeep(boolean keep) {
        this.showroomKeep = keep;
    }

    // ------------------------------------------------------------------
    // 外观
    // ------------------------------------------------------------------

    /** 被克隆者的 UUID（两端；没有时为 null） */
    @Nullable
    public UUID clonedPlayer() {
        return entityData.get(DATA_CLONED).orElse(null);
    }

    /** 外观上这一格是什么（两端都读同步数据） */
    public ItemStack lookInSlot(EquipmentSlot slot) {
        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i] == slot) {
                return entityData.get(DATA_LOOK[i]);
            }
        }
        return ItemStack.EMPTY;
    }

    /** 客户端渲染时拿外观复制品；服务端照常（装备格一直是空的，不会被缴械、偷走、掉出来） */
    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        if (level().isClientSide) {
            return lookInSlot(slot);
        }
        return super.getItemBySlot(slot);
    }

    /** 服务端的装备格是空的，这里拒绝往里放东西（别的模组、发射器给它穿盔甲也穿不上） */
    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        if (level().isClientSide || stack.isEmpty()) {
            super.setItemSlot(slot, stack);
        }
    }

    // ------------------------------------------------------------------
    // 不掉、不捡、不踩、不响
    // ------------------------------------------------------------------

    @Override
    protected ResourceLocation getDefaultLootTable() {
        return BuiltInLootTables.EMPTY;
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
    }

    @Override
    public boolean canTrample(BlockState state, BlockPos pos, float fallDistance) {
        return false;
    }

    @Override
    public boolean canPickUpLoot() {
        return false;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected @Nullable SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return null;
    }

    @Override
    public boolean isPersistenceRequired() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSquared) {
        return true;
    }

    /** 被谁打死都不算「有东西可抢」：没有掉落、没有经验 */
    @Override
    public boolean shouldDropExperience() {
        return false;
    }
}
