package com.flameforge.essentialcraft5.espe;

import com.flameforge.essentialcraft5.api.IESPEHandler;

/**
 * 按 {@link ESPEAccess} 限制过的 ESPE 视图，给 capability 对外用。读数（存量、上限、档位）总是照实返回。
 * <p>
 * {@link #setESPE} 是直接改存量的接口，对外时按「加就是进、减就是出」判断：只进不出的一面只许调高，只出不进的只许调低，
 * 否则别人可以用 setESPE(0) 绕过「只进不出」把熔炉清空。
 */
public final class SidedESPEHandler implements IESPEHandler {

    private final IESPEHandler inner;
    private final ESPEAccess access;

    public SidedESPEHandler(IESPEHandler inner, ESPEAccess access) {
        this.inner = inner;
        this.access = access;
    }

    public ESPEAccess access() {
        return access;
    }

    @Override
    public double getMaxESPE() {
        return inner.getMaxESPE();
    }

    @Override
    public double getESPE() {
        return inner.getESPE();
    }

    @Override
    public void setESPE(double amount) {
        if (Double.isNaN(amount)) {
            return;
        }
        double current = inner.getESPE();
        if (access.canInsert && access.canExtract
                || access.canInsert && amount > current
                || access.canExtract && amount < current) {
            inner.setESPE(amount);
        }
    }

    @Override
    public double addESPE(double amount, boolean doAdd) {
        if (access.canInsert) {
            return inner.addESPE(amount, doAdd);
        }
        // 一点都不收：剩余量就是全部（和 ESPEStorage 对非正数、NaN 的返回保持一致）
        return Double.isNaN(amount) ? 0D : amount;
    }

    @Override
    public double extractESPE(double amount, boolean doExtract) {
        return access.canExtract ? inner.extractESPE(amount, doExtract) : 0D;
    }

    @Override
    public int getTier() {
        return inner.getTier();
    }
}
