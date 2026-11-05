package com.flameforge.essentialcraft5.entity.mob.hoanna;

import com.flameforge.essentialcraft5.gun.LensEffects;
import com.flameforge.essentialcraft5.item.ember.EmberKind;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 余烬弹打实以后带的余烬效果（第十三批 13d-N2，怪物稿 §2.4.4 的表）：八种余烬八种效果，一颗弹只带射它那种余烬的一种。
 * <pre>
 * 阴燃 common     着火 3 秒
 * 燃烧 flame      着火 6 秒 + 2 点火焰伤害（火球类伤害，抗火挡得住）
 * 酸蚀 acidic     中毒 I 5 秒
 * 混沌 chaos      从别的七种里随机一种
 * 腐化 corrupted  虚弱 I 6 秒
 * 水晶 crystal    缓慢 I 4 秒
 * 神圣 divine     驱散目标一个有益效果（同魔力透镜、余烬「神圣 + 魔力」：{@link LensEffects#dispelOne}）
 * 魔力 magical    从目标身上的储存物品抽 {@code magical_drain} MRU（不够就有多少抽多少，没有就不抽；{@link MRUDrain}）
 * </pre>
 * 状态效果的来源记成射手（{@code addEffect(效果, 射手)}）。怪打人照常给，不过玩家对玩家的门。
 */
public final class EmberSpiritHit {

    public static final int COMMON_FIRE_SECONDS = 3;
    public static final int FLAME_FIRE_SECONDS = 6;
    public static final float FLAME_EXTRA_DAMAGE = 2F;
    public static final int ACIDIC_POISON_TICKS = 100;
    public static final int CORRUPTED_WEAKNESS_TICKS = 120;
    public static final int CRYSTAL_SLOWNESS_TICKS = 80;

    private EmberSpiritHit() {
    }

    /**
     * 对 target 施加 kind 那种余烬的效果（服务端，伤害已经打实以后调）。返回实际生效的是哪一种（混沌余烬返回抽中的那种）。
     *
     * @param bolt  打中的余烬弹（燃烧余烬的火焰伤害记它为直接实体；可以为 null）
     * @param owner 射手（伤害的攻击者、状态效果的来源；射手已经不在了时为 null）
     */
    public static EmberKind apply(EmberKind kind, LivingEntity target, @Nullable Entity bolt, @Nullable Entity owner, RandomSource random) {
        EmberKind effective = kind == EmberKind.CHAOS ? chaosPick(random) : kind;
        switch (effective) {
            case COMMON -> target.setSecondsOnFire(COMMON_FIRE_SECONDS);
            case FLAME -> {
                target.setSecondsOnFire(FLAME_FIRE_SECONDS);
                burn(target, bolt, owner);
            }
            case ACIDIC -> target.addEffect(new MobEffectInstance(MobEffects.POISON, ACIDIC_POISON_TICKS, 0), owner);
            case CORRUPTED -> target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, CORRUPTED_WEAKNESS_TICKS, 0), owner);
            case CRYSTAL -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, CRYSTAL_SLOWNESS_TICKS, 0), owner);
            case DIVINE -> LensEffects.dispelOne(target);
            case MAGICAL -> drain(target, ECHoannaNativesB.Config.ember().magicalDrain());
            default -> {
            }
        }
        return effective;
    }

    /** 混沌余烬：从别的七种里等概率抽一种（不会抽到混沌自己） */
    public static EmberKind chaosPick(RandomSource random) {
        EmberKind[] all = EmberKind.values();
        EmberKind pick = all[random.nextInt(all.length - 1)];
        return pick.ordinal() >= EmberKind.CHAOS.ordinal() ? all[pick.ordinal() + 1] : pick;
    }

    /**
     * 魔力余烬：从玩家身上的储存物品抽至多 amount MRU（不够就有多少抽多少）。不是玩家、创造模式、amount ≤ 0 时不抽。返回抽走多少。
     */
    public static int drain(LivingEntity target, int amount) {
        if (amount <= 0 || !(target instanceof Player player) || player.getAbilities().instabuild) {
            return 0;
        }
        long available = MRUDrain.availableFromStorages(player);
        int take = (int) Math.min(amount, available);
        if (take <= 0) {
            return 0;
        }
        return MRUDrain.tryDrainFromStorages(player, take) ? take : 0;
    }

    /** 燃烧余烬的 2 点火焰伤害：火球类（{@code is_fire}、{@code is_projectile}），刚挨过余烬弹，先清掉无敌时间；不再多击退一次 */
    private static void burn(LivingEntity target, @Nullable Entity bolt, @Nullable Entity owner) {
        DamageSource source = new DamageSource(ECDamageTypes.holder(target.level(), DamageTypes.FIREBALL), bolt, owner);
        Vec3 motion = target.getDeltaMovement();
        target.invulnerableTime = 0;
        if (target.hurt(source, FLAME_EXTRA_DAMAGE)) {
            target.setDeltaMovement(motion);
        }
    }
}
