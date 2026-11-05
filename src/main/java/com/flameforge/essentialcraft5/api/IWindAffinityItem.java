package com.flameforge.essentialcraft5.api;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 穿戴时改变风之好感系数的物品（原作 IWindModifyHandler.getModifier）。物品类实现它，穿在盔甲四格或戴在任意饰品槽里时，
 * {@link WindAffinity#coefficient} 把返回值加进系数。
 * <p>
 * 原作的数：风法师套装每件 +0.23（原作写死在 WindRelations 里按物品判断，这里改成套装自己实现），风法师护符 +0.3，捕风挂坠 −0.1
 * （单戴捕风挂坠时系数 0.1 − 0.1 = 0，好感不涨，照原作；提示行要写明）。
 * <pre>{@code
 * public class WindMageArmorItem extends ArmorItem implements IWindAffinityItem {
 *     @Override
 *     public float getWindAffinity(ItemStack stack, Player player) {
 *         return 0.23F;
 *     }
 * }
 * }</pre>
 * 只在服务端调（好感只在服务端改）；按 float 返回、按 float 累加，和原作的取整结果一致（见 {@link WindAffinity#add}）。
 */
public interface IWindAffinityItem {

    /** 这件物品给风之好感系数加多少（可以为负） */
    float getWindAffinity(ItemStack stack, Player player);
}
