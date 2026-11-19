package com.flameforge.essentialcraft5.item.attunement;

import com.flameforge.essentialcraft5.entity.boss.ECBoss;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.Tags;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * 总闸（tools/plan/gear_growth.md 第 1、2 节）：本模组的增伤、减伤、穿透、吸血、冷却缩减、闪避先各自相加，再在这里按目标类别封顶。
 * <p>
 * 只管本模组自己那一份：别的模组的倍数（暴击类、战斗修正类）照常在外面乘，不认、不碰它们；封顶的意义是本模组这一份不会自己先翻好几倍，
 * 和外面的乘数叠起来也只是「本模组 ×2 封顶 × 别人的」，不会连乘爆掉。数都在服务端配置 {@code general.gear_growth.caps}。
 */
public final class GearCaps {

    /** 目标类别：增伤池、穿透的上限按它取 */
    public enum TargetClass {
        MOB, BOSS, PLAYER
    }

    @Nullable
    private static volatile Predicate<EntityType<?>> testBossOverride;

    private GearCaps() {
    }

    /** 玩家 → PLAYER；{@code #forge:bosses} 或本模组的首领（{@link ECBoss}）→ BOSS；其余 MOB。GameTest 设了首领覆盖时按覆盖认首领 */
    public static TargetClass targetClass(@Nullable Entity target) {
        if (target instanceof Player) {
            return TargetClass.PLAYER;
        }
        if (target instanceof LivingEntity living && isBoss(living)) {
            return TargetClass.BOSS;
        }
        return TargetClass.MOB;
    }

    public static boolean isBoss(LivingEntity entity) {
        Predicate<EntityType<?>> override = testBossOverride;
        if (override != null && override.test(entity.getType())) {
            return true;
        }
        return entity instanceof ECBoss || entity.getType().is(Tags.EntityTypes.BOSSES);
    }

    /** GameTest：把某些实体类型也当成首领（null = 恢复）。同一段同步代码里设、测、置回 */
    public static void setTestBossOverride(@Nullable Predicate<EntityType<?>> override) {
        testBossOverride = override;
    }

    /** 增伤池上限（打普通生物 1.0 = 最多 ×2、首领 0.5、玩家 0.3） */
    public static double poolCap(TargetClass target) {
        return Math.max(0D, switch (target) {
            case MOB -> GearGrowthConfig.poolMob();
            case BOSS -> GearGrowthConfig.poolBoss();
            case PLAYER -> GearGrowthConfig.poolPlayer();
        });
    }

    /** 减伤池上限：攻击者是别的玩家 40%，其余（有攻击者、没攻击者）60% */
    public static double reductionCap(@Nullable Entity attacker, LivingEntity victim) {
        boolean pvp = attacker instanceof Player && attacker != victim;
        return Math.max(0D, Math.min(0.95D, pvp ? GearGrowthConfig.reductionCapPlayer() : GearGrowthConfig.reductionCap()));
    }

    /** 护甲穿透上限：打玩家 15%，其余 30% */
    public static double armorPenCap(@Nullable Entity target) {
        return Math.max(0D, Math.min(1D, target instanceof Player ? GearGrowthConfig.armorPenCapPlayer() : GearGrowthConfig.armorPenCap()));
    }

    /** 冷却缩减上限（40%） */
    public static double cooldownCap() {
        return Math.max(0D, Math.min(0.9D, GearGrowthConfig.cooldownReductionCap()));
    }

    /** 冷却按缩减打折后的刻数：base × (1 − min(cdr, 上限))，四舍五入，不小于 0 */
    public static int reducedCooldown(int base, double cdr) {
        if (base <= 0 || !(cdr > 0D)) {
            return Math.max(0, base);
        }
        return Math.max(0, (int) Math.round(base * (1D - Math.min(cdr, cooldownCap()))));
    }

    /** 闪避上限：攻击者是玩家 15%，其余 35%（G2 的虚空套装用） */
    public static double dodgeCap(@Nullable Entity attacker) {
        return Math.max(0D, Math.min(1D, attacker instanceof Player ? GearGrowthConfig.dodgeCapPlayer() : GearGrowthConfig.dodgeCap()));
    }
}
