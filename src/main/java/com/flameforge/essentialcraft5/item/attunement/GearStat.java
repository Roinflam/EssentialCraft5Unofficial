package com.flameforge.essentialcraft5.item.attunement;

import com.flameforge.essentialcraft5.EssentialCraft5;

import java.util.Locale;

/**
 * 同调词条的种类（tools/plan/gear_growth.md 第 1 节）。词条的数都是比例（0.08 = 8%），提示框按百分数写；
 * 每种带一个语言键 {@code tooltip.essentialcraft5.gear_stat.<名字>}（参数是百分数里的数，语言键里自己写 %%）。
 * <p>
 * 前六种 G1 的武器用，后四种给盔甲（G2）。
 */
public enum GearStat {
    /** 增伤（进增伤池） */
    DAMAGE,
    /** 吸血：主手打实的最终伤害的几成回给自己（过每秒桶） */
    LIFESTEAL,
    /** 护甲穿透：结算时无视目标几成护甲和韧性 */
    ARMOR_PEN,
    /** 斩杀：目标生命低于门槛时增伤池再加这么多 */
    EXECUTE,
    /** 冷却缩减 */
    COOLDOWN,
    /** MRU 花费折扣 */
    MRU_DISCOUNT,
    /** 闪避几率 */
    DODGE,
    /** 减伤（进减伤池） */
    REDUCTION,
    /** 移动速度 */
    MOVE_SPEED,
    /** 攻击速度 */
    ATTACK_SPEED;

    /** 提示框那一行的语言键 */
    public String key() {
        return "tooltip." + EssentialCraft5.MODID + ".gear_stat." + name().toLowerCase(Locale.ROOT);
    }
}
