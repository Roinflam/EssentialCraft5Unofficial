package com.flameforge.essentialcraft5.entity.boss;

/**
 * 首领分两档（batch13.md §5.3「共同」、policy §4.18「首领通用件」），规矩按档位给，不按具体哪只怪写分支：
 * <ul>
 *   <li>{@link #MINI} 小 Boss（聚能魔像、13e 的废城守望者这类结构守卫）：只用单次封顶；谁打都算伤害（关键掉落另由 {@code fair_kill} 管，
 *       别的模组的炮塔、宠物照样打得动它）；不能骑船矿车。</li>
 *   <li>{@link #BOSS} Boss（矩阵之影、13e 的天灾之心这类召唤出来的）：只吃参战真玩家的伤害（{@code bypasses_invulnerability} 例外）、
 *       不是近战的 ×{@code non_melee_multiplier}、拒 {@code orbital_strike}；单次封顶 + 每秒桶；不吃状态效果（发光除外）；
 *       不受摔落 / 窒息 / 溺水 / 挤压；推不动、不吃击退；不能骑船矿车。</li>
 * </ul>
 * 两档都进 {@code #forge:bosses}、{@code divider_immune}、{@code mru_coil_immune}（标签数据由各路登记），掉落都按参战者每人一份（{@link BossLoot}）。
 */
public enum BossTier {

    MINI(false),
    BOSS(true);

    private final boolean strict;

    BossTier(boolean strict) {
        this.strict = strict;
    }

    /** Boss 档：只认参战真玩家、非近战打折、拒轨道打击 */
    public boolean fightersOnly() {
        return strict;
    }

    /** 每秒伤害桶生效（小 Boss 只用单次封顶） */
    public boolean usesBucket() {
        return strict;
    }

    /** 不吃状态效果（发光除外） */
    public boolean effectImmune() {
        return strict;
    }

    /** 不受摔落、窒息（卡墙）、溺水、挤压 */
    public boolean environmentImmune() {
        return strict;
    }

    /** 推不动、不吃击退、不被水流推 */
    public boolean immovable() {
        return strict;
    }
}
