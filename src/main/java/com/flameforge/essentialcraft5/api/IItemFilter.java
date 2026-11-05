package com.flameforge.essentialcraft5.api;

/**
 * 物品过滤器（batch4 F4-10）：由过滤器物品的 Item 类实现（ECLogistics 的白 / 黑名单过滤器，以后海纳尔批的高级过滤器）。
 * 别的模块<b>不 import 过滤器物品类</b>，只认这个接口和物品标签 {@code essentialcraft5:filters}（{@link ItemFilters#FILTERS}），
 * 判定一律走 {@link ItemFilters}（数据格式、黑白名单、套娃、体积上限都在那里）。
 * <p>
 * 实现时两个方法对同一个物品必须恒定（不看 NBT）：过滤器物品按 Item 区分黑白名单和普通 / 高级（原作 meta 0~3）。
 */
public interface IItemFilter {

    /** 黑名单：样本里任一匹配就拒，否则收（空黑名单什么都收）。false = 白名单：任一匹配才收（空白名单什么都不收） */
    boolean isBlacklist();

    /** 高级过滤器：才读 {@link ItemFilters.Flag} 的开关（忽略耐久、忽略 NBT、按标签，语义海纳尔批定） */
    boolean isAdvanced();
}
