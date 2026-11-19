package com.flameforge.essentialcraft5.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * MRU 箭（原作 EntityMRUArrow，48 行）：风法师射的箭。
 * <p>
 * 照原作的：原版箭的飞行、重力、伤害公式（⌈速度 × 基础伤害⌉，风法师按类型设基础伤害）和伤害类型 {@code minecraft:arrow}
 * （带 {@code minecraft:is_projectile}，causing entity = 射箭的风法师，policy §2.2「伤害带攻击者」）；不能捡；存在超过 60 tick 就消失；
 * 飞行时客户端拖两颗粒子（原作 cSpellFX 是黑边白芯的小团，这里用烟 + 末地烛的白点近似）。
 * <p>
 * 移植改动（policy P1：怪物的弹射物不碰场景）：
 * <ul>
 *   <li>碰到方块就散掉（出声、放一小团烟），不插在方块上，也<b>不调方块的 onProjectileHit</b>：标靶不出信号、钟不响、紫颂花 / 滴水石锥 / 饰纹陶罐不被打碎。
 *       原作是 1.12 的普通箭，会插在方块上、能压按钮。</li>
 *   <li>{@link #checkInsideBlocks()} 什么都不做：飞过按钮、压力板、绊线、下界传送门都不触发。</li>
 *   <li>只打活体生物，不打盔甲架（{@link #canHitTarget}）：穿过展示框、画、盔甲架、矿车、船、末地水晶，不会把装饰打掉。</li>
 *   <li>不存盘（{@link #shouldBeSaved()}）：只活 3 秒的弹射物没必要跟着区块卸载写进存档。</li>
 *   <li>同步间隔用原版箭的 20 tick（原作每 tick 同步，客户端本来就自己模拟弹道）。</li>
 * </ul>
 */
public class MRUArrowEntity extends AbstractArrow {

    /** 原作 onUpdate：ticksExisted > 60 就 setDead */
    public static final int LIFETIME = 60;
    /** 碰到方块散掉时发给客户端的实体事件（同雪球的 3） */
    private static final byte EVENT_FIZZLE = 3;

    public MRUArrowEntity(EntityType<? extends MRUArrowEntity> type, Level level) {
        super(type, level);
        this.pickup = Pickup.DISALLOWED;
    }

    /** 从射手眼睛下方 0.1 格射出（同原版箭） */
    public MRUArrowEntity(EntityType<? extends MRUArrowEntity> type, Level level, LivingEntity shooter) {
        super(type, shooter, level);
        this.pickup = Pickup.DISALLOWED;
    }

    @Override
    public void tick() {
        super.tick();
        if (isRemoved()) {
            return;
        }
        if (!level().isClientSide && tickCount > LIFETIME) {
            discard();
        }
    }

    /** 只打活体生物（不含盔甲架）；另外照原版：不打旁观者、刚射出时不打射手自己 */
    @Override
    protected boolean canHitEntity(Entity entity) {
        return canHitTarget(entity) && super.canHitEntity(entity);
    }

    public static boolean canHitTarget(Entity entity) {
        return entity instanceof LivingEntity && !(entity instanceof ArmorStand);
    }

    /**
     * 碰到方块：服务端出声、放一团烟、消失；不调父类（父类会调方块的 onProjectileHit 并把箭插在方块上）。
     * 客户端只把箭停在撞到的位置，等服务端删掉。
     */
    @Override
    protected void onHitBlock(BlockHitResult hit) {
        setDeltaMovement(hit.getLocation().subtract(getX(), getY(), getZ()));
        if (level().isClientSide) {
            return;
        }
        playSound(getHitGroundSoundEvent(), 1.0F, 1.2F / (random.nextFloat() * 0.2F + 0.9F));
        level().broadcastEntityEvent(this, EVENT_FIZZLE);
        discard();
    }

    @Override
    public void handleEntityEvent(byte id) {
        // 尾迹和碰墙散开都由客户端的 client/vfx/weapon/ProjectileTrailFx 画
        if (id == EVENT_FIZZLE) {
            return;
        }
        super.handleEntityEvent(id);
    }

    /** 飞过的方块一律不触发（按钮、压力板、绊线、传送门、火、蜘蛛网……） */
    @Override
    protected void checkInsideBlocks() {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    /** 不能捡（原作 getArrowStack 返回空） */
    @Override
    protected ItemStack getPickupItem() {
        return ItemStack.EMPTY;
    }
}
