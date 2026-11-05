package com.flameforge.essentialcraft5.api;

/**
 * 物品上的 MRU 存储，通过 {@link ECCapabilities#MRU_ITEM} 暴露。
 * <p>
 * {@link #isStorage()} 为 true 的物品（灵魂碎片等储存物品）会被当作玩家的 MRU 电池：
 * 玩家使用耗 MRU 的物品时，先从饰品栏、盔甲、背包、副手里的储存物品扣（见 {@code mru.MRUDrain}）。
 */
public interface IMRUHandlerItem extends IMRUHandler {

    boolean isStorage();

    void setStorage(boolean storage);

    /**
     * 无底的储存物品（扣多少都不减，只有只放创造栏的无尽 MRU 晶体，registry/ECCreative）。
     * {@code mru.MRUDrain} 见到它就由它全付、不动身上别的储存物品。默认 false。
     */
    default boolean isInfinite() {
        return false;
    }
}
