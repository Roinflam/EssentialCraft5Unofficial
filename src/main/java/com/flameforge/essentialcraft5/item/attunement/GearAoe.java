package com.flameforge.essentialcraft5.item.attunement;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.util.ECEntityRules;
import com.flameforge.essentialcraft5.util.ECHurtWindow;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 装备觉醒的范围伤害共用写法（tools/plan/gear_growth.md 第 1 节）：元素爆发、过载射线，G2 的盔甲觉醒也用它。
 * <ul>
 *   <li>伤害类型 {@code essentialcraft5:gear_burst}（{@link #GEAR_BURST}，{@code data/essentialcraft5/damage_type/gear_burst.json}）：
 *       {@code scaling = never}，<b>不进</b> {@code bypasses_armor}（护甲照算）；伤害源带攻击者、不带直接实体和位置，PvP、队伍、领地、混合端插件都照攻击者管。</li>
 *   <li>不连锁：它进 {@link AttunementGains#notOwnHit}，不给同调点、不触发矩阵特性 / 余烬嵌刻 / 裁断补伤，也不吃增伤池（{@link GearPool#skipsOffense}），
 *       不会再引出一下范围技。</li>
 *   <li>找目标（{@link #targets}）：敌对生物（{@link Enemy}）或首领；普通生物跳过 {@code ECEntityRules.isSpared}（起了名字、驯服、拴着绳……），
 *       首领只跳过驯服的和拴着绳的；跳过盟友、旁观者、出手的人自己；{@code aoe_hits_players} 默认关，关着时不打玩家；按距离取前 N 个。</li>
 *   <li>数额乘 {@code aoe_scale}；普通目标打之前记下无敌窗口、打完放回（目标刚挨了主手那一下也照样吃得到），首领不清无敌帧。</li>
 * </ul>
 */
public final class GearAoe {

    public static final ResourceKey<DamageType> GEAR_BURST = ResourceKey.create(Registries.DAMAGE_TYPE, EssentialCraft5.id("gear_burst"));

    private GearAoe() {
    }

    /** 范围技的伤害源：带攻击者，不带直接实体和位置 */
    public static DamageSource source(Player attacker) {
        return new DamageSource(ECDamageTypes.holder(attacker.level(), GEAR_BURST), null, attacker);
    }

    /** center 周围 radius 格内的目标（见类注释），按距离从近到远取前 max 个 */
    public static List<LivingEntity> targets(Player player, Vec3 center, double radius, int max) {
        double r2 = radius * radius;
        boolean players = GearGrowthConfig.aoeHitsPlayers();
        AABB box = new AABB(center, center).inflate(radius);
        List<LivingEntity> found = player.level().getEntitiesOfClass(LivingEntity.class, box, entity -> entity != player && entity.isAlive()
                && !entity.isSpectator() && !entity.isAlliedTo(player) && entity.distanceToSqr(center) <= r2 && eligible(entity, players));
        found.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(center)));
        return found.size() > max ? new ArrayList<>(found.subList(0, Math.max(0, max))) : found;
    }

    private static boolean eligible(LivingEntity entity, boolean players) {
        if (entity instanceof Player) {
            return players;
        }
        if (GearCaps.isBoss(entity)) {
            return !ECEntityRules.isTamed(entity) && !(entity instanceof Mob mob && mob.isLeashed());
        }
        return entity instanceof Enemy && !ECEntityRules.isSpared(entity);
    }

    /** 打一下 gear_burst（数额乘 {@code aoe_scale}）；打成了返回 true */
    public static boolean hit(Player player, LivingEntity target, float damage) {
        return hit(player, target, damage, 0F);
    }

    /**
     * 同 {@link #hit(Player, LivingEntity, float)}，这一下最终掉血不超过 cap（≤ 0 不封顶）：封顶登记给这一下新建的伤害源
     * （{@link GearCombatEvents#capNext}，{@code LivingDamageEvent} LOWEST 里夹），中间不管谁再乘多少都不超；首领同样封。
     */
    public static boolean hit(Player player, LivingEntity target, float damage, float cap) {
        float amount = (float) (damage * Math.max(0D, GearGrowthConfig.aoeScale()));
        if (!(amount > 0F) || !Float.isFinite(amount) || !target.isAlive()) {
            return false;
        }
        DamageSource source = source(player);
        if (cap > 0F) {
            GearCombatEvents.capNext(source, cap);
        }
        if (GearCaps.isBoss(target)) {
            return target.hurt(source, amount);
        }
        Vec3 motion = target.getDeltaMovement();
        boolean marked = target.hurtMarked;
        ECHurtWindow window = ECHurtWindow.of(target);
        target.invulnerableTime = 0;
        boolean hurt = target.hurt(source, amount);
        target.setDeltaMovement(motion);
        target.hurtMarked = marked;
        window.restore(target);
        return hurt;
    }
}
