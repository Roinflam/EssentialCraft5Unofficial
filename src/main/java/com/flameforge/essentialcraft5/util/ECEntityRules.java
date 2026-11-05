package com.flameforge.essentialcraft5.util;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 生物的归属规则（policy §4.3 ③ 的「归属关」，batch4 F4-12）：机器、光束、寒气、线圈对生物做事之前，先问这只生物是不是「有主的」。
 * 放在这里而不是 {@code ECProtection}：这是生物归属规则，不是权限探测（batch4 裁定 7）；领地那一关另走 {@code ECProtection.checkAsOwner}。
 *
 * <h2>两种问法</h2>
 * <ul>
 *   <li>{@link #isSpared}：<b>谁都不许碰</b>的生物，不看机器是谁的。原来是太阳棱镜的 {@code sparedFromBeam}（第三批 P-7 / RB-3），挪过来以后
 *       第四批加了装了鞍的骆驼（4a 审查 FW-7）；太阳棱镜、冷凝器用它，以后线圈、反应堆打怪也用它。</li>
 *   <li>{@link #belongsToOther}：<b>这台机器</b>能不能处理它。和上面一样，只是驯服的 / 有主的生物<b>主人就是机器主人时不算别人的</b>
 *       （2026-09-28 D1 取 B：驯服的动物只处理主人就是机器主人的，别人的不碰）。繁殖器、剪毛器、分离器用它。</li>
 * </ul>
 * 两者的差别只在「驯服 / 有主」这一项：起了名字的、拴着绳的、村民与流浪商人、玩家造的铁傀儡、雪傀儡、认了主人的悦灵、装了鞍的骆驼，
 * 即使是机器主人自己的也不动（名字和拴绳是玩家明确表示「这只别动」；悦灵没有「驯服」这回事，认主的记忆也可能指向别人，一律不动；
 * 骆驼不用驯服就能骑、从不记主人，只能靠鞍看出「有人在用」，也就看不出是谁的）。
 * <p>
 * 盔甲架、展示框之类没有「主人」的东西不在这里管，走领地（能动那一格就能动它）。玩家不是谁的财产，但也不该被机器当成牲口处理，
 * 所以 {@link #belongsToOther} 对玩家一律返回 true（机器要对玩家做什么，调用方自己另写规则，例如冷凝器只影响主人本人）。
 */
public final class ECEntityRules {

    /**
     * D1 的开关（2026-09-28 取 B）：true = 机器主人自己驯服的生物，机器照常处理；false（D1 选 A）= 驯服的一律不碰，
     * {@link #belongsToOther} 退化成 {@link #isSpared}（玩家除外）。只改这一处。
     */
    public static final boolean TAMED_BY_MACHINE_OWNER_IS_OWN = true;

    private ECEntityRules() {
    }

    /**
     * 谁都不许碰的「有主的」生物（原 {@code SolarPrismBlockEntity.sparedFromBeam}）：
     * 起了名字的（{@code hasCustomName}，含命名牌）；村民（{@link AbstractVillager}，流浪商人一并算）；雪傀儡（只能玩家造）；
     * 玩家造的铁傀儡（{@link IronGolem#isPlayerCreated()}；村庄自己刷的照打）；认了主人的悦灵（记着 {@code LIKED_PLAYER}）；
     * 装了鞍的骆驼（{@link Camel#isSaddled()}，见 {@link #alwaysSpared}）；
     * 驯服的（{@link OwnableEntity} 记着主人，或 {@link TamableAnimal#isTame()}、{@link AbstractHorse#isTamed()}，骆驼除外）；拴着绳的（{@link Mob#isLeashed()}）。
     * <p>
     * 玩家、盔甲架、创造模式、旁观者不在名单里，调用处各自处理。
     */
    public static boolean isSpared(LivingEntity entity) {
        return alwaysSpared(entity) || isTamed(entity);
    }

    /**
     * 这只生物是不是「别人的」、这台机器不该处理：同 {@link #isSpared}，但驯服 / 有主的生物主人等于 {@code machineOwner} 时不算
     * （{@link #TAMED_BY_MACHINE_OWNER_IS_OWN}）。驯服了却没记主人的（命令生成的 {@code Tame:1b} 马之类）算别人的；
     * 没有主人的机器（{@code machineOwner == null}）把所有驯服的都当别人的。玩家一律返回 true（见类注释）。
     *
     * @param machineOwner 机器实际替谁干活，传 {@code ECProtection.actingOwner(machine)}（单人里可能是房主，见它的注释）；
     *                     别传 {@code ECProtection.owner}：单人里没记主人时它是 null，会把房主自己驯服的也当成别人的。没有主人为 null
     */
    public static boolean belongsToOther(LivingEntity entity, @Nullable UUID machineOwner) {
        if (entity instanceof Player) {
            return true;
        }
        if (alwaysSpared(entity)) {
            return true;
        }
        if (!isTamed(entity)) {
            return false;
        }
        if (!TAMED_BY_MACHINE_OWNER_IS_OWN || machineOwner == null) {
            return true;
        }
        return !machineOwner.equals(tamedOwner(entity));
    }

    /** 驯服 / 有主的生物的主人 UUID（{@link OwnableEntity#getOwnerUUID()}）；不是这类生物或没记主人返回 null */
    @Nullable
    public static UUID tamedOwner(LivingEntity entity) {
        return entity instanceof OwnableEntity ownable ? ownable.getOwnerUUID() : null;
    }

    /** 驯服 / 有主：记着主人的 {@link OwnableEntity}，或者驯服标记为真（驯服了却没记主人的也算） */
    public static boolean isTamed(LivingEntity entity) {
        if (entity instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null) {
            return true;
        }
        if (entity instanceof TamableAnimal tamable && tamable.isTame()) {
            return true;
        }
        // 骆驼的 isTamed() 恒为 true（原版不用驯服就能骑、拿仙人掌繁殖），野生的没记主人：只按上面的主人 UUID 算，否则机器永远不处理骆驼
        return entity instanceof AbstractHorse horse && !(horse instanceof Camel) && horse.isTamed();
    }

    /**
     * 不管是谁的机器都不碰的那几类（isSpared 去掉「驯服」一项）。
     * <p>
     * 装了鞍的骆驼（4a 审查 FW-7）：骆驼的 {@code isTamed()} 恒为真、从不记主人，{@link #isTamed} 为了让繁殖器、
     * 分离器能处理野骆驼把它排除了，连带光束、寒气开始打玩家正在用的骆驼（改动前所有骆驼都豁免）。原版骆驼只能靠鞍看出「有人在用」，
     * 所以装了鞍的放在这里：光束、寒气和所有机器都不碰，机器主人自己的鞍骆驼也不喂、不传（和起了名字的一样）；没装鞍的骆驼照野生动物算。
     */
    private static boolean alwaysSpared(LivingEntity entity) {
        if (entity.hasCustomName()) {
            return true;
        }
        if (entity instanceof AbstractVillager || entity instanceof SnowGolem) {
            return true;
        }
        if (entity instanceof IronGolem golem && golem.isPlayerCreated()) {
            return true;
        }
        if (entity instanceof Allay allay && allay.getBrain().hasMemoryValue(MemoryModuleType.LIKED_PLAYER)) {
            return true;
        }
        if (entity instanceof Camel camel && camel.isSaddled()) {
            return true;
        }
        return entity instanceof Mob mob && mob.isLeashed();
    }
}
