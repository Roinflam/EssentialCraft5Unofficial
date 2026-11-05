package com.flameforge.essentialcraft5.api;

import net.minecraft.world.item.ItemStack;

/**
 * 带 MRU 抗性的物品（盔甲、饰品）。对应原作 {@code essentialcraft.api.IMRUResistHandler}（返回三个数的 List）
 * 和 {@code ApiCore.setItemResistances(item, cResist, rResist, cAffect)} 登记表——这里只留接口：由 Item 类实现。
 * <p>
 * 只在两处生效（{@link MRUResistance#vulnerability}）：穿在四个盔甲槽里，或装在 Curios 饰品槽里。拿在手上、放在背包里不算。
 * 盔甲四件的抗性先相加再除以 4，饰品不除，所以一件盔甲 0.25 相当于饰品 0.0625。
 * <p>
 * 物品提示里的抗性行（「MRU 腐化抗性 +25%」）由框架自动加（{@link MRUResistance} 里的 ItemTooltipEvent 监听），物品自己不要再写。
 * <p>
 * 例（ECGear 的魔力防护套装，原作 ItemsCore L117-129：每件 0.25 / 0.125 / 0.10）：
 * <pre>{@code
 * public class MHSArmorItem extends ArmorItem implements IMRUResistanceItem {
 *     @Override
 *     public float getResistance(ItemStack stack, MRUResistance.Type type) {
 *         return switch (type) {
 *             case MRU_CORRUPTION -> 0.25F;
 *             case CORRUPTION_RADIATION -> 0.125F;
 *             case HOANNA_RADIATION -> 0.10F;
 *         };
 *     }
 * }
 * }</pre>
 */
public interface IMRUResistanceItem {

    /**
     * @return 这件物品对 type 的抗性（0 = 没有）。盔甲每件 1、四件穿齐才完全免疫；饰品一件 1 就完全免疫。
     *         可以为负（加重）；非有限值（NaN、无穷）按 0 算。
     */
    float getResistance(ItemStack stack, MRUResistance.Type type);
}
