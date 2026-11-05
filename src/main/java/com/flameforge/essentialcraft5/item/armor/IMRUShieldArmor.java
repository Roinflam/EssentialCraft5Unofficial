package com.flameforge.essentialcraft5.item.armor;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 「有电就替穿戴者挡伤害」的盔甲（第八批框架 8-Fb，batch8.md §3.2 第 6 条；虚空套装、宅男套装）。
 * 实现它的盔甲只要说清「挡哪些伤害、管几份、每点多少 MRU」，挨打时的处理全在 {@link MRUShield}：
 * <ul>
 *   <li>穿在自己部位、这件自己存的 MRU &gt; 0 才挡（MRU 读物品的 {@code MRU_ITEM} 能力，盔甲自己要提供）；</li>
 *   <li>这一下挡不挡、挡几份、价钱乘几由 {@link #coverage} 给，默认就是第十三批以前的规则（{@link #shields} + {@link #shieldShare} + 原价）；</li>
 *   <li>挡下它那一份要花 {@code max(1, ⌊伤害 × 份额 / 20⌋) × 每点单价 × 价格倍数}，先扣身上的储存物品、再扣矩阵容量、最后扣这件自己的；<b>扣不起这件就不挡</b>；</li>
 *   <li>四件挡下的份额合计到 {@value MRUShield#TOTAL_SHARES} 就取消这次 {@code LivingHurtEvent}，不够的照常走原版护甲。</li>
 * </ul>
 * 别的模块认「MRU 盔甲」也看这个接口（全息投影的护甲破坏者对它改扣电：{@code 5 × mruPerDamage}，按 0 阶原价），不用知道具体是哪一套。
 *
 * <h2>覆盖面（第十三批 13a，batch13.md §4.4）</h2>
 * 同调阶让一件盔甲多挡几种伤害、换一个价钱，都写在 {@link #coverage} 里，{@link MRUShield} 不认识具体是哪一套。
 * {@link MRUShield} 先排除带 {@code minecraft:bypasses_invulnerability} 和在 {@code essentialcraft5:unshieldable} 里的伤害，再逐件问。
 * 返回的 {@link Coverage#extended()} 为真表示这是「新覆盖面」（魔法、无视护甲这类原来挡不到的），{@link MRUShield} 另外替它守三条：
 * 要有攻击者，攻击者不是穿戴者自己也不是别的玩家（只给 PvE），扣电时不认无尽 MRU 晶体（只认别的储存物品、矩阵容量和盔甲自带的电）。
 * 护盾整下吃掉一次攻击、或挡下了一部分以后，{@link MRUShield} 在 Forge 总线上发 {@link MRUShieldAbsorbedEvent}（混合端等伤害真的生效、扣过电再发），
 * 各套「挡下以后」的效果听它，不用改 {@link MRUShield}。
 */
public interface IMRUShieldArmor {

    /**
     * 一件盔甲这一下怎么挡。
     *
     * @param share           这件挡多少份（二十分之几，可以是小数；四件合计封顶 {@value MRUShield#TOTAL_SHARES}）
     * @param priceMultiplier 每点单价（{@link #mruPerDamage}）乘几
     * @param extended        新覆盖面（见接口说明）：要有攻击者、不对玩家攻击者、不从无尽晶体扣
     */
    record Coverage(double share, double priceMultiplier, boolean extended) {

        public Coverage {
            share = Double.isFinite(share) ? Math.max(0D, share) : 0D;
            priceMultiplier = Double.isFinite(priceMultiplier) ? Math.max(0D, priceMultiplier) : 1D;
        }

        /** 原来的挡法：这个部位的份额、原价 */
        public static Coverage standard(int share) {
            return new Coverage(share, 1D, false);
        }

        /** 份额、价格各乘一个数（PvP 限份额、同调降价这类），新覆盖面的标记照留 */
        public Coverage scaled(double shareFactor, double priceFactor) {
            return new Coverage(share * shareFactor, priceMultiplier * priceFactor, extended);
        }
    }

    /** 这件按原来的规则挡不挡这一下（{@link #coverage} 的默认实现用；调用方已经排除了 {@code bypasses_armor}） */
    boolean shields(ItemStack stack, DamageSource source);

    /** 这个部位管的份额，按二十分之几（虚空、宅男都是头 3、胸 8、腿 6、靴 3） */
    int shieldShare(ArmorItem.Type type);

    /** 挡下的伤害每点花多少 MRU（0 阶原价；同调阶的降价、新覆盖面的加价放在 {@link Coverage#priceMultiplier()} 里） */
    int mruPerDamage(ItemStack stack);

    /**
     * 这件这一下怎么挡；不挡返回 null。默认 = 第十三批以前的规则：带 {@code bypasses_armor} 的不挡，{@link #shields} 为真时按这个部位的份额、原价挡。
     * 实现可以按物品上的同调阶、伤害种类、攻击者给别的份额和价钱；{@link MRUShield} 已经排除了 {@code bypasses_invulnerability} 和 {@code unshieldable}。
     */
    @Nullable
    default Coverage coverage(ItemStack stack, DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_ARMOR) || !(stack.getItem() instanceof ArmorItem armor) || !shields(stack, source)) {
            return null;
        }
        return Coverage.standard(shieldShare(armor.getType()));
    }
}
