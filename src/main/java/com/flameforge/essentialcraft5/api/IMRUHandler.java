package com.flameforge.essentialcraft5.api;

/**
 * MRU（魔法辐射单位）存储。方块实体通过 {@link ECCapabilities#MRU} 暴露，物品另见 {@link IMRUHandlerItem}。
 * <p>
 * 方法签名照搬原作 {@code essentialcraft.api.IMRUHandler}，移植机器代码时可以原样对照：
 * {@code addMRU} 返回放不下的剩余量，{@code extractMRU} 返回实际取出量，布尔参数为 false 时只模拟。
 * 原作的 shade 字段只在 MRUCUEC 各部件间转发、没有实际逻辑，已删除。
 */
public interface IMRUHandler {

    int getMaxMRU();

    int getMRU();

    /** 只用于内部直接设置，结果会被夹在 [0, 上限]。 */
    void setMRU(int amount);

    /** @return 放不下的剩余量（amount ≤ 0 时原样返回 amount，与原作一致） */
    int addMRU(int amount, boolean doAdd);

    /** @return 实际取出的量 */
    int extractMRU(int amount, boolean doExtract);

    /** 平衡值，范围 [0, 2]：1 为纯净，小于 1 偏冰冻，大于 1 偏混沌。 */
    float getBalance();

    void setBalance(float balance);
}
