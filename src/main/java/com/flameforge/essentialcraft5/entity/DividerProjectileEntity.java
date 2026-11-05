package com.flameforge.essentialcraft5.entity;

import com.flameforge.essentialcraft5.item.DividerGunItem;
import com.flameforge.essentialcraft5.registry.ECHoloWeapons;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 二分弹（原作 {@code EntityDividerProjectile}，39 行；二分枪射出，batch8 §4.3、policy §4.6）：原版投掷物的飞行（重力 0.03、阻力 0.99），
 * 画成一块魔力熔渣（原作 {@code ThrowableRenderFactory(magicalSlag)}）。
 * <ul>
 *   <li><b>打中方块</b>：在命中点放下一个二分场（{@code DividerGunItem.spawnField(…, }{@value #FIELD_DELAY}{@code  刻, 射手)}：二分枪的目标规则和首领模式，gated = true）。
 *       原作展开点用弹丸这一刻移动之前的位置，最多往回退 1.5 格，贴墙打时场常常够不着墙后面的东西；这里用命中点。</li>
 *   <li><b>打中射手以外的生物</b>：就在命中点展开（原作只认方块，打中生物直接穿过去）。只认活体，盔甲架、展示框、船、矿车都穿过去；射手本人一直穿过。</li>
 *   <li><b>射手不在了就熄灭</b>：落地时按 UUID 找射手（同原版投掷物），找不到（下线没回来）、死了还没重生、或者不在同一个世界，
 *       就什么都不放、直接消失；死后在同一个世界重生、或者寿命内重新登录，落地照常张开，攻击者记成新的玩家实体。二分场到点时还会再查一次。</li>
 *   <li>{@value #LIFETIME} 刻没落地就消失（原作没有寿命上限，会跟着区块存盘）；不存盘（注册时 {@code noSave}，{@link #shouldBeSaved()} 也是假）、不能 /summon。</li>
 *   <li>不碰场景（policy P1）：撞到方块<b>不调方块的 {@code onProjectileHit}</b>（标靶不出红石信号、钟不响、紫颂花 / 滴水石锥 / 饰纹陶罐不被打碎）；
 *       飞过压力板、绊线、按钮都不触发（{@link #checkInsideBlocks()}、{@link #isIgnoringBlockTriggers()}）；不穿下界传送门换世界。</li>
 * </ul>
 */
public class DividerProjectileEntity extends ThrowableItemProjectile {

    /** 多少刻没落地就消失 */
    public static final int LIFETIME = 200;
    /** 落地后二分场多少刻张开（原作 new EntityDivider(…, 0, 2, …) 的 2 秒） */
    public static final int FIELD_DELAY = 40;

    public DividerProjectileEntity(EntityType<? extends DividerProjectileEntity> type, Level level) {
        super(type, level);
    }

    /** 从射手眼睛下方 0.1 格射出（同原版雪球），射手记成主人 */
    public DividerProjectileEntity(Level level, LivingEntity shooter) {
        super(ECHoloWeapons.DIVIDER_PROJECTILE.get(), shooter, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ECMaterials.MAGICAL_SLAG.get();
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && !isRemoved() && tickCount >= LIFETIME) {
            discard();
        }
    }

    /** 只打活体（不含盔甲架），不打射手本人；另外照原版：能被投掷物打中的才算 */
    @Override
    protected boolean canHitEntity(Entity entity) {
        return entity instanceof LivingEntity && !(entity instanceof ArmorStand) && entity != getOwner() && !entity.isSpectator()
                && super.canHitEntity(entity);
    }

    /**
     * 原版投掷物打中实体时给的位置是被打者的脚下（{@code new EntityHitResult(entity)}），不是接触点；
     * 这里照原版选目标时用的那个框（碰撞箱外扩 0.3）和这一刻的飞行线段算出接触点。onHit 在这一刻移动之前调，线段就是 位置 → 位置 + 速度
     */
    @Override
    protected void onHitEntity(EntityHitResult hit) {
        Vec3 from = position();
        Vec3 at = hit.getEntity().getBoundingBox().inflate(0.3D).clip(from, from.add(getDeltaMovement())).orElse(from);
        deploy(at);
    }

    /** 不调父类：父类会调方块的 onProjectileHit（见类注释） */
    @Override
    protected void onHitBlock(BlockHitResult hit) {
        deploy(hit.getLocation());
    }

    /** 服务端：射手还在就在 at 放下二分场，然后消失（客户端什么都不做，等服务端删掉） */
    private void deploy(Vec3 at) {
        if (!(level() instanceof ServerLevel level) || isRemoved()) {
            return;
        }
        LivingEntity shooter = shooter();
        if (shooter != null && shooter.isAlive() && shooter.level() == level) {
            DividerGunItem.spawnField(level, at, FIELD_DELAY, shooter);
        }
        discard();
    }

    /** 射手（服务端按 UUID 找，重生 / 重新登录后是新实体；不是活体、找不到时为 null） */
    @Nullable
    public LivingEntity shooter() {
        return getOwner() instanceof LivingEntity living && !living.isRemoved() ? living : null;
    }

    // ------------------------------------------------------------------ 不碰场景、不存盘

    @Override
    protected void checkInsideBlocks() {
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    public void handleInsidePortal(BlockPos pos) {
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
