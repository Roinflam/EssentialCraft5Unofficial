package com.flameforge.essentialcraft5.entity.mob.hoanna;

import com.flameforge.essentialcraft5.entity.ArmorDestroyerEntity;
import com.flameforge.essentialcraft5.entity.mob.WildMobBolt;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesC;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 审判庭巡卫射的护甲破坏者（实体 {@code patroller_destroyer}，第十三批 13d-N3，怪物稿 §2.4.7「复用护甲破坏者」）。
 * 全息投影那一发（{@link ArmorDestroyerEntity}）只认放出它的全息投影和它的参战名单，巡卫用不了那个实体，这里另做一个外观、效果一样的：
 * <ul>
 *   <li>打中玩家时调 {@link ArmorDestroyerEntity#wear}（同一份效果：主副手各扣 5 点耐久；实现了 MRU 护盾的盔甲按 0 阶原价改扣 5 × 单价的 MRU，
 *       扣不起的那件扣耐久，不吃魔力相性折扣）。不造成伤害。打中别的技能目标（巡卫当前追着的生物）什么都不做，只消失。</li>
 *   <li>飞行、只撞射手的技能目标、不碰方块机关、不过传送门、不存盘，都照 {@link WildMobBolt}；飞 {@value #LIFETIME} 刻消失，射手没了也消失。</li>
 *   <li>画面同全息投影那一发：一块魔力熔渣（{@link ItemSupplier}，客户端用原版的投掷物渲染器），身后撒一点末地烛粒子。</li>
 * </ul>
 */
public class PatrollerDestroyerEntity extends WildMobBolt implements ItemSupplier {

    /** 飞多久消失（同全息投影那一发） */
    public static final int LIFETIME = ArmorDestroyerEntity.LIFETIME;

    public PatrollerDestroyerEntity(EntityType<? extends PatrollerDestroyerEntity> type, Level level) {
        super(type, level);
    }

    /** 从 shooter 眼前出发、还没给速度的一发（{@link #shoot} 用；GameTest 不放进世界直接调 {@link #hits} / {@link #applyHit}） */
    public PatrollerDestroyerEntity(Level level, LivingEntity shooter) {
        super(ECHoannaNativesC.PATROLLER_DESTROYER.get(), level, shooter);
    }

    /** 从 shooter 眼睛的位置朝 target 的眼睛射一发（服务端）；生成被别的模组取消时返回 null */
    @Nullable
    public static PatrollerDestroyerEntity shoot(ServerLevel level, LivingEntity shooter, LivingEntity target, float speed, float inaccuracy) {
        PatrollerDestroyerEntity bolt = new PatrollerDestroyerEntity(level, shooter);
        Vec3 aim = target.getEyePosition().subtract(bolt.position());
        bolt.shoot(aim.x, aim.y, aim.z, speed, inaccuracy);
        return level.addFreshEntity(bolt) && !bolt.isRemoved() ? bolt : null;
    }

    @Override
    protected int lifetime() {
        return LIFETIME;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && !isRemoved()) {
            Entity owner = getOwner();
            if (!(owner instanceof LivingEntity living) || living.isRemoved() || living.isDeadOrDying()) {
                discard();
            }
        }
    }

    @Override
    protected void hitLiving(LivingEntity target, @Nullable Entity owner) {
        applyHit(target);
    }

    /** 打中 target 的效果：是玩家就照全息投影的护甲破坏者磨装备 / 扣电，别的什么都不做；放一声碎裂 */
    public void applyHit(LivingEntity target) {
        if (target instanceof Player player) {
            ArmorDestroyerEntity.wear(player);
        }
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_BREAK, SoundSource.HOSTILE, 0.3F, 2F);
    }

    /** 这一发能不能撞上 target（射手的技能目标，见 {@link WildMobBolt}） */
    public boolean hits(Entity target) {
        return canHitEntity(target);
    }

    @Override
    protected void trail() {
        if (random.nextInt(2) == 0) {
            level().addParticle(ParticleTypes.END_ROD, getX(), getY() + getBbHeight() * 0.5D, getZ(), 0D, 0D, 0D);
        }
    }

    @Override
    public ItemStack getItem() {
        return new ItemStack(ECMaterials.MAGICAL_SLAG.get());
    }
}
