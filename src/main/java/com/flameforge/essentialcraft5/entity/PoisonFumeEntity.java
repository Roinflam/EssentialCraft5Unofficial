package com.flameforge.essentialcraft5.entity;

import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.entity.boss.BossRules;
import com.flameforge.essentialcraft5.entity.boss.ECBoss;
import com.flameforge.essentialcraft5.hoanna.HoannaHazards;
import com.flameforge.essentialcraft5.hoanna.HoannaHazardsConfig;
import com.flameforge.essentialcraft5.hoanna.Radiation;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvents;
import com.flameforge.essentialcraft5.registry.ECBaubles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fluids.FluidType;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * 毒雾（原作 {@code EntityPoisonFume}，132 行；第六批 6a M4，batch6a.md §5.4、policy §4.4）：毒雾天灾期间在海纳尔自然生成、飘来飘去的一团毒气，
 * 靠近的人中毒、辐射猛涨。本身看不见（客户端注册空渲染器，雾团由 M3 的 {@code FumeHazeRenderer} 按注册名跟踪实体来画）。
 * <p>
 * 继承 {@link Mob}，<b>不是 Monster / PathfinderMob</b>：后两者的「走路偏好」（{@code walkTargetValue}）会让它在亮处刷不出来，原作的
 * {@code isValidLightLevel} 恒 true。没有 AI 目标；不受重力；不怕火（注册时 {@code fireImmune}）。
 * <ul>
 *   <li><b>飘</b>（服务端）：每 {@value #TURN_INTERVAL} tick 换一次方向，每轴速度 ≤ {@value #MAX_SPEED}（原作 mX / 10），活 {@value #LIFETIME} tick 后消失
 *       （年龄存盘，读档不重新计时）。<b>自然生成的</b>每 {@value #CHECK_INTERVAL} tick 查一次：不在海纳尔或毒雾事件结束了就消失；
 *       刷怪蛋 / 指令生成的活满 {@value #LIFETIME} tick、在哪都能用（展示厅和创造模式用）。</li>
 *   <li><b>毒</b>（服务端，每 {@value #AFFECT_INTERVAL} tick 遍历一次本世界的玩家，按实体 id 错开）：范围照原作——以毒雾脚下为中心、
 *       ±1 格的盒子再扩 6 / 3 / 6（水平 ±7、竖直 ±4），和玩家碰撞箱相交就算；跳过创造、旁观、戴 μ 指环的；其余 +{@value #DOSE} 辐射
 *       （<b>不乘易伤</b>，照原作：只有 μ 和创造免疫，μ「多吃辐射换免疫」才成立；= 原作每 tick +10）、中毒 II 在没有、比 II 低、
 *       或剩余不到 {@value #POISON_REFRESH_BELOW} tick 时给 {@value #POISON_TICKS} tick（原作每只对每人每 tick 一个效果包）；
 *       有海纳尔环境抗性时（第十三批，虚空套装全套）改成按抗性缩短、隔开着给，人在雾里中毒的时间按比例变少（{@code HoannaHazards.fumePoison}）。</li>
 *   <li><b>摸不着</b>：只有 {@code /kill}、掉出世界这类「无视无敌」的伤害能杀它（原作 /kill 都杀不掉）；不挡准星和箭（{@code isPickable} false）、
 *       推不动也不推别人、不触发幽匿感测体（{@code MovementEmission.NONE}）、不踩压力板和绊线、不受摔伤、不上船和矿车、不过传送门、
 *       别的生物不把它当敌人；和平难度消失；不掉东西、不给经验。</li>
 * </ul>
 */
public class PoisonFumeEntity extends Mob {

    public static final int LIFETIME = 1000;
    public static final int TURN_INTERVAL = 100;
    public static final int CHECK_INTERVAL = 20;
    public static final int AFFECT_INTERVAL = 10;
    public static final double MAX_SPEED = 0.1D;
    /** 原作 AABB：中心 ±1 再 grow(6, 3, 6) */
    public static final double REACH_XZ = 7D;
    public static final double REACH_Y = 4D;
    public static final int DOSE = 100;
    public static final int POISON_TICKS = 200;
    public static final int POISON_AMPLIFIER = 1;
    public static final int POISON_REFRESH_BELOW = 100;

    private int age;
    private boolean natural;
    private double driftX;
    private double driftY;
    private double driftZ;
    /** 首领放的毒雾（13e 天灾之心）：只毒它名单里的参战者、活 {@link #bossLifetime} 刻、不存盘；首领没了就散 */
    @Nullable
    private ECBoss boss;
    private int bossLifetime = LIFETIME;

    public PoisonFumeEntity(EntityType<? extends PoisonFumeEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    /** 原作 EntityMob 默认 20 点生命（反正只有 /kill 能伤它） */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20D);
    }

    // ------------------------------------------------------------------ 生成

    @Override
    @SuppressWarnings("deprecation")
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData data, @Nullable CompoundTag tag) {
        natural = reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION;
        return super.finalizeSpawn(level, difficulty, reason, data, tag);
    }

    /** 是不是自然生成的（毒雾事件结束就消失） */
    public boolean isNatural() {
        return natural;
    }

    public int age() {
        return age;
    }

    /** 首领放毒雾时调（加进世界之前）：绑到这只首领、活 lifetime 刻 */
    public void bindBoss(ECBoss owner, int lifetime) {
        boss = owner;
        bossLifetime = Math.max(1, lifetime);
    }

    @Nullable
    public ECBoss boss() {
        return boss;
    }

    // ------------------------------------------------------------------ 每 tick

    @Override
    public void aiStep() {
        if (level() instanceof ServerLevel level) {
            serverFumeTick(level);
            if (isRemoved()) {
                return;
            }
        }
        super.aiStep();
    }

    private void serverFumeTick(ServerLevel level) {
        age++;
        if (age > (boss != null ? bossLifetime : LIFETIME)) {
            discard();
            return;
        }
        if (boss != null && (boss.bossEntity().isRemoved() || !boss.bossEntity().isAlive())) {
            discard();
            return;
        }
        if (natural && (age + getId()) % CHECK_INTERVAL == 0 && !HoannaEvents.isActive(level, HoannaEvent.FUMES)) {
            discard();
            return;
        }
        if ((age - 1) % TURN_INTERVAL == 0) {
            driftX = random.nextDouble() * 2D - 1D;
            driftY = random.nextDouble() * 2D - 1D;
            driftZ = random.nextDouble() * 2D - 1D;
        }
        setDeltaMovement(driftX * MAX_SPEED, driftY * MAX_SPEED, driftZ * MAX_SPEED);
        if ((age + getId()) % AFFECT_INTERVAL == 0) {
            affectPlayers(boss != null ? boss.bossRoster().online() : level.players());
        }
    }

    /** 毒的范围（世界坐标）：脚下 ±1 的盒子再扩 6 / 3 / 6 */
    public AABB reach() {
        return new AABB(getX() - 1D, getY() - 1D, getZ() - 1D, getX() + 1D, getY() + 1D, getZ() + 1D)
                .inflate(REACH_XZ - 1D, REACH_Y - 1D, REACH_XZ - 1D);
    }

    /**
     * 对范围里的玩家结算一次（GameTest 直接调：测试玩家不在世界的玩家列表里）。
     *
     * @return 这次毒到了几个人
     */
    public int affectPlayers(Collection<? extends Player> players) {
        AABB reach = reach();
        boolean radiation = HoannaHazardsConfig.radiationEnabled();
        int hit = 0;
        for (Player player : players) {
            if (player instanceof FakePlayer || !player.isAlive() || player.isCreative() || player.isSpectator()
                    || !player.getBoundingBox().intersects(reach)
                    // 首领的毒雾是招式：只看是不是参战者，海纳尔调谐挡不住（环境抗性照常减）
                    || (boss == null ? CuriosCompat.has(player, ECBaubles.HOANNA_ATTUNED) : !BossRules.isSkillTarget(boss, player))) {
                continue;
            }
            if (radiation) {
                Radiation.add(player, DOSE);
            }
            // 中毒按海纳尔环境抗性减（虚空套装全套），没有抗性时和原来一样
            HoannaHazards.fumePoison(player, this);
            hit++;
        }
        return hit;
    }

    // ------------------------------------------------------------------ 存档

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("ec5_age", age);
        tag.putBoolean("ec5_natural", natural);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        age = tag.getInt("ec5_age");
        natural = tag.getBoolean("ec5_natural");
    }

    // ------------------------------------------------------------------ 摸不着

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || super.isInvulnerableTo(source);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurt(source, amount);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return false;
    }

    @Override
    protected MovementEmission getMovementEmission() {
        return MovementEmission.NONE;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean canDrownInFluidType(FluidType type) {
        return false;
    }

    @Override
    public boolean isAffectedByPotions() {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public boolean startRiding(Entity vehicle, boolean force) {
        return false;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    /** 首领放的不存盘（读档后首领的技能实体都不回来） */
    @Override
    public boolean shouldBeSaved() {
        return boss == null && super.shouldBeSaved();
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return true;
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    @Override
    public boolean shouldDropExperience() {
        return false;
    }
}
