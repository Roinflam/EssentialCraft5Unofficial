package com.flameforge.essentialcraft5.entity.mob;

import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECNetherEndMobs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * 炼狱小鬼吐的余火弹 {@code imp_ember_bolt}（第十三批 13c-M3，batch13.md §5.2）。照原版小火球（{@code SmallFireball}）的飞法：
 * 每刻按加速度推、没有重力，画面借火焰弹物品的贴图（{@code ThrownItemRenderer} 缩小、全亮）。和小火球的区别：
 * <ul>
 *   <li><b>落地不放火</b>：打到方块只冒一点烟就没了，不放火、不碰方块（P1）；飞着穿过绊线、压力板、传送门都不触发，也不换世界；</li>
 *   <li>伤害类型 {@code mob_bolt}（{@code ECDamageTypes.mobBolt(弹, 小鬼)}）：算弹射物、照常吃护甲、盾牌挡得住，护盾按物理挡；
 *       打中活物时伤害取发射时小鬼的攻击属性（精英 ×1.4），打实了（生命或黄心真少了）才让目标烧 {@code burn_seconds} 秒；</li>
 *   <li>只碰活物：盔甲架、展示框、画、船、矿车、末影水晶这些直接穿过去（打碎它们就是在动玩家的东西）；同种小鬼互相穿过、不误伤；</li>
 *   <li>不能被打回去（同小火球，{@link #isPickable} 为假）；飞 {@value #LIFETIME} 刻没打中就自己熄掉，不在世界里堆着。</li>
 * </ul>
 */
public class NetherImpEmberBoltEntity extends Fireball {

    /** 最多飞几刻（约 3 秒，速度上来以后够飞 30 多格） */
    public static final int LIFETIME = 60;
    private static final String TAG_DAMAGE = "damage";

    private float damage = (float) ECNetherEndMobs.Config.IMP_DEFAULTS.boltDamage();

    public NetherImpEmberBoltEntity(EntityType<? extends NetherImpEmberBoltEntity> type, Level level) {
        super(type, level);
    }

    /** 从 shooter 眼前朝 (dx, dy, dz) 方向发出（方向不用归一，父类按长度换成每刻 0.1 的加速度） */
    public NetherImpEmberBoltEntity(Level level, LivingEntity shooter, double dx, double dy, double dz, float damage) {
        super(ECNetherEndMobs.IMP_EMBER_BOLT.get(), shooter, dx, dy, dz, level);
        this.damage = damage;
        setPos(shooter.getX(), shooter.getEyeY() - 0.15D, shooter.getZ());
        setItem(new ItemStack(Items.FIRE_CHARGE));
    }

    public float damage() {
        return damage;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && isAlive() && tickCount > LIFETIME) {
            fizzle();
        }
    }

    /** 画面上不挂原版的燃烧火焰（物品贴图本身就是一团火） */
    @Override
    protected boolean shouldBurn() {
        return false;
    }

    @Override
    protected ParticleOptions getTrailParticle() {
        return ParticleTypes.SMALL_FLAME;
    }

    /** 只碰活物（不含盔甲架）、不碰别的小鬼，见类注释 */
    @Override
    protected boolean canHitEntity(Entity target) {
        if (!(target instanceof LivingEntity) || target instanceof ArmorStand || target instanceof NetherImpEntity) {
            return false;
        }
        return super.canHitEntity(target);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (level().isClientSide || !(result.getEntity() instanceof LivingEntity target)) {
            return;
        }
        Entity owner = getOwner();
        DamageSource source = ECDamageTypes.mobBolt(this, owner);
        float before = target.getHealth() + target.getAbsorptionAmount();
        boolean hurt = target.hurt(source, damage);
        int burn = ECNetherEndMobs.Config.imp().burnSeconds();
        if (hurt && burn > 0 && target.isAlive() && target.getHealth() + target.getAbsorptionAmount() < before) {
            target.setSecondsOnFire(burn);
        }
        if (hurt && owner instanceof LivingEntity living) {
            doEnchantDamageEffects(living, target);
        }
    }

    /**
     * 打到方块：只熄掉。不调父类（{@code BlockState.onProjectileHit}）：标靶会改输出信号、钟会响、紫颂花和滴水石锥会碎，
     * 这些都算动了方块；余火弹落地什么都不碰（{@link #onHit} 里熄掉）。
     */
    @Override
    protected void onHitBlock(BlockHitResult result) {
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide) {
            fizzle();
        }
    }

    /** 冒一点烟、响一声，然后消失 */
    private void fizzle() {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SMOKE, getX(), getY(), getZ(), 4, 0.08D, 0.08D, 0.08D, 0.01D);
            server.playSound(null, getX(), getY(), getZ(), ECNetherEndMobs.SOUND_EMBER_FIZZLE.get(), SoundSource.HOSTILE, 0.4F,
                    1F + (random.nextFloat() - 0.5F) * 0.2F);
        }
        discard();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    // ------------------------------------------------------------------ 穿过的方块一概不碰（同 WildMobBolt）

    /** 原版每刻对身子占到的格子调 entityInside：绊线、压力板、下界 / 末地传送门都从这里触发，整个不跑 */
    @Override
    protected void checkInsideBlocks() {
    }

    /** 压力板、绊线按实体数信号时把它排除 */
    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    public void handleInsidePortal(BlockPos pos) {
    }

    /** 不换世界：换过去的那份按 UUID 找不到小鬼，伤害不带攻击者，寿命还从头数 */
    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat(TAG_DAMAGE, damage);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(TAG_DAMAGE)) {
            damage = Math.max(0F, tag.getFloat(TAG_DAMAGE));
        }
    }
}
