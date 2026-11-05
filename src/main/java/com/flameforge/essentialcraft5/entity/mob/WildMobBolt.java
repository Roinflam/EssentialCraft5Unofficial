package com.flameforge.essentialcraft5.entity.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 13c-M1 两种怪物弹射物（元素聚灵弹 {@link WispBoltEntity}、风暴术士的疾风弹 {@code entity/StormGustEntity}）共用的底子：
 * 直线飞（不受重力）、飞够 {@link #lifetime()} 刻自己消失、不存盘（技能实体，policy §4.18「技能实体每只有上限」：每发都是一次性的）。
 * <ul>
 *   <li>只撞射手自己的技能目标：射手是 {@link ECMonster} 的按它的实例方法 {@link ECMonster#isSkillTarget(LivingEntity)}（首领覆盖成名单判定，
 *       没进名单的路人不挨打），别的 {@link Mob} 按 {@link ECMonster#isSkillTarget(Mob, LivingEntity)}（生存 / 冒险玩家，或者射手当前的目标）；
 *       别的生物、盔甲架、展示框直接穿过去；射手已经不在了就只撞生存 / 冒险玩家；</li>
 *   <li>撞到方块就散掉，不改方块、不放火；飞着穿过绊线、压力板、传送门这些都不触发，也不换世界（同 {@code DividerProjectileEntity}）；</li>
 *   <li>撞人由 {@link #hitLiving} 处理（子类定伤害和效果，伤害一律带射手当攻击者）。撞击走原版 {@code ThrowableProjectile.tick}，
 *       先发 Forge 的 {@code ProjectileImpactEvent}（可取消，混合端插件拦得到）。</li>
 * </ul>
 * 画面：渲染器是空的（{@code NoopRenderer}），客户端每刻在身后撒粒子（{@link #trail()}）。
 */
public abstract class WildMobBolt extends ThrowableProjectile {

    protected WildMobBolt(EntityType<? extends WildMobBolt> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    protected WildMobBolt(EntityType<? extends WildMobBolt> type, Level level, LivingEntity shooter) {
        super(type, shooter, level);
        setNoGravity(true);
    }

    /** 飞多少刻后自己消失 */
    protected abstract int lifetime();

    /** 撞到 target（已经过了技能目标的筛选）。owner 是射手，可能已经不在了（null） */
    protected abstract void hitLiving(LivingEntity target, @Nullable Entity owner);

    /** 客户端每刻撒的拖尾粒子 */
    protected abstract void trail();

    /** 撞到方块散掉时在客户端放的粒子（服务端发一次实体事件） */
    protected void burst() {
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected float getGravity() {
        return 0F;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            trail();
        } else if (tickCount > lifetime()) {
            discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (!super.canHitEntity(target) || !(target instanceof LivingEntity living)) {
            return false;
        }
        Entity owner = getOwner();
        if (owner instanceof ECMonster monster) {
            return monster.isSkillTarget(living);
        }
        if (owner instanceof Mob mob) {
            return ECMonster.isSkillTarget(mob, living);
        }
        // 射手已经没了（死了、区块卸载）：只撞生存 / 冒险玩家，不误伤村民、宠物和别的怪
        return living instanceof Player player && !player.isSpectator() && !player.isCreative();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!level().isClientSide && result.getEntity() instanceof LivingEntity living) {
            hitLiving(living, getOwner());
        }
    }

    /** 撞方块只散掉：不走原版的 onProjectileHit（标靶出信号、钟响、紫颂花 / 饰纹陶罐碎掉这些都不触发） */
    @Override
    protected void onHitBlock(BlockHitResult result) {
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide) {
            level().broadcastEntityEvent(this, (byte) 3);
            discard();
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 3) {
            burst();
        } else {
            super.handleEntityEvent(id);
        }
    }

    // ------------------------------------------------------------------ 穿过的方块一概不碰

    /** 原版每刻对身子占到的格子调 entityInside：绊线、压力板、下界 / 末地传送门都从这里触发，整个不跑 */
    @Override
    protected void checkInsideBlocks() {
    }

    /** 压力板、绊线按实体数信号时把它排除（别的东西踩着的那块照常） */
    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    /** {@code ThrowableProjectile.tick} 撞上下界传送门会直接调它，不走 entityInside */
    @Override
    public void handleInsidePortal(BlockPos pos) {
    }

    /**
     * 不换世界：换过去的那份按 UUID 找不到射手，会当成「射手没了」只撞玩家、伤害也不带攻击者，寿命还从头数
     */
    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    /** 技能实体不存盘：区块卸载时直接没了 */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }
}
