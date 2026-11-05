package com.flameforge.essentialcraft5.api;

/**
 * ESPE（Ender Star Pulse Energy，末影之星脉冲能量）存储，通过 {@link ECCapabilities#ESPE} 暴露。
 * 由米斯莱水晶产出，米斯莱熔炉、玩家/恶魔五芒星、风之符文消耗。
 * <p>
 * 原作还有一个物品版 IESPEHandlerItem，全源码没有任何物品挂载，没有移植。
 */
public interface IESPEHandler {

    double getMaxESPE();

    double getESPE();

    void setESPE(double amount);

    /** @return 放不下的剩余量 */
    double addESPE(double amount, boolean doAdd);

    /** @return 实际取出的量 */
    double extractESPE(double amount, boolean doExtract);

    int getTier();
}
