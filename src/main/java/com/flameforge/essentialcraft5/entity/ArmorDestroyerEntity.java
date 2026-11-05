package com.flameforge.essentialcraft5.entity;

import com.flameforge.essentialcraft5.item.armor.IMRUShieldArmor;
import com.flameforge.essentialcraft5.item.armor.MRUShield;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.registry.ECHologram;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 护甲破坏者（原作 {@code EntityArmorDestroyer}，72 行）：全息投影减法攻击射出的投掷物，贴图借魔力熔渣（batch8.md §4.1）。
 * <ul>
 *   <li>只打放出它的那只全息投影名单里的参战玩家（{@link #isTargetFor}），别的实体直接穿过去；</li>
 *   <li>打中：主副手各扣 {@value #WEAR} 点耐久；四件盔甲里实现了 {@link IMRUShieldArmor} 的（魔力防护、虚空、宅男套装）改扣
 *       {@value #WEAR} × 每点单价的 MRU（先扣身上的储存物品；原作对 ISpecialArmor 调 damageArmor，虚空每件 4 000、宅男每件 1 250），
 *       扣不起的那件照常扣 {@value #WEAR} 点耐久，其余盔甲也扣耐久。不造成伤害。魔力防护穿着会掉耐久，扣不起时要是什么都不做，
 *       没充电反倒比充了电划算；虚空、宅男套装穿在玩家身上本来不掉耐久（{@code damageItem} 返回 0），扣不起时照旧什么都不动。
 *       护盾盔甲的磨损按原价扣（{@code MRUDrain.playerUseMRUExact}），不吃魔力相性的折扣，
 *       同护盾本身的扣费（batch13.md §3.2：单价里已经算了同调阶的降价，再打折就叠过头了）；</li>
 *   <li>撞到方块只消失，不调方块的 {@code onProjectileHit}（标靶出红石、钟会响、饰纹陶罐会碎、紫水晶会响）；不触发绊线、压力板；不过传送门；</li>
 *   <li>寿命 {@value #LIFETIME} 刻；放出它的全息投影没了就消失；不存盘、不能 /summon。</li>
 * </ul>
 */
public class ArmorDestroyerEntity extends ThrowableItemProjectile {

    /** 每次扣几点耐久（原作 5） */
    public static final int WEAR = 5;
    /** 飞多久消失 */
    public static final int LIFETIME = 100;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** 展示厅用：不按寿命、不跟着 Boss 消失 */
    private boolean showroomKeep;

    public ArmorDestroyerEntity(EntityType<? extends ArmorDestroyerEntity> type, Level level) {
        super(type, level);
    }

    private ArmorDestroyerEntity(Level level, HologramEntity owner) {
        super(ECHologram.ARMOR_DESTROYER.get(), owner, level);
    }

    /**
     * 从 owner 眼睛的位置朝 target 的眼睛射一发（服务端）。生成被别的模组取消时返回 null。
     */
    @Nullable
    public static ArmorDestroyerEntity shoot(ServerLevel level, HologramEntity owner, Player target, float speed, float inaccuracy) {
        ArmorDestroyerEntity destroyer = new ArmorDestroyerEntity(level, owner);
        Vec3 aim = target.getEyePosition().subtract(destroyer.position());
        destroyer.shoot(aim.x, aim.y, aim.z, speed, inaccuracy);
        return level.addFreshEntity(destroyer) && !destroyer.isRemoved() ? destroyer : null;
    }

    @Override
    protected Item getDefaultItem() {
        return ECMaterials.MAGICAL_SLAG.get();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || isRemoved()) {
            return;
        }
        Entity owner = getOwner();
        if (!showroomKeep && (tickCount >= LIFETIME || !(owner instanceof HologramEntity hologram) || hologram.isRemoved()
                || hologram.isDeadOrDying())) {
            discard();
        }
    }

    /**
     * 展示厅用：keep 为真时不按寿命、不跟着 Boss 消失（服务端调）。要停在半空拍「飞行中」，另外把速度清零、关重力
     * （{@code setDeltaMovement(Vec3.ZERO)}、{@code setNoGravity(true)}）。
     */
    public void setShowroomKeep(boolean keep) {
        this.showroomKeep = keep;
    }

    /** 这发能不能打中 entity：只认放出它的全息投影名单里的参战玩家（服务端；客户端没有名单，一律穿过） */
    public boolean isTargetFor(Entity entity) {
        return entity instanceof Player player && getOwner() instanceof HologramEntity hologram && hologram.isSkillTarget(player);
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return isTargetFor(entity) && super.canHitEntity(entity);
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide && !isRemoved()) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_BREAK, SoundSource.HOSTILE, 0.3F, 2F);
            discard();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!level().isClientSide && result.getEntity() instanceof Player player && isTargetFor(player)) {
            wear(player);
        }
    }

    /** 撞到方块：什么都不做（不调父类，父类会调方块的 onProjectileHit），由 {@link #onHit} 消失 */
    @Override
    protected void onHitBlock(BlockHitResult result) {
    }

    /**
     * 打中一名玩家的效果（见类注释；GameTest 直接调）：主副手扣耐久；MRU 盔甲扣电、扣不起扣耐久；别的盔甲扣耐久。不造成伤害
     */
    public static void wear(Player player) {
        damage(player, EquipmentSlot.MAINHAND);
        damage(player, EquipmentSlot.OFFHAND);
        for (EquipmentSlot slot : ARMOR) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ArmorItem.Type type = typeOf(slot);
            ItemStack shielded = type == null ? null : MRUShield.wornPiece(player, type);
            if (shielded != null && shielded.getItem() instanceof IMRUShieldArmor armor) {
                long cost = (long) WEAR * Math.max(0, armor.mruPerDamage(shielded));
                if (!MRUDrain.playerUseMRUExact(player, shielded, (int) Math.min(Integer.MAX_VALUE, cost))) {
                    damage(player, slot);
                }
            } else {
                damage(player, slot);
            }
        }
    }

    private static void damage(Player player, EquipmentSlot slot) {
        ItemStack stack = player.getItemBySlot(slot);
        if (!stack.isEmpty() && stack.isDamageableItem()) {
            stack.hurtAndBreak(WEAR, player, p -> p.broadcastBreakEvent(slot));
        }
    }

    @Nullable
    private static ArmorItem.Type typeOf(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> ArmorItem.Type.HELMET;
            case CHEST -> ArmorItem.Type.CHESTPLATE;
            case LEGS -> ArmorItem.Type.LEGGINGS;
            case FEET -> ArmorItem.Type.BOOTS;
            default -> null;
        };
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }
}
