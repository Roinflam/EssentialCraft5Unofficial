package com.flameforge.essentialcraft5.espe;

import com.flameforge.essentialcraft5.api.IESPEHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.function.DoubleSupplier;

/**
 * ESPE 存储，对应原作 ESPEStorage（默认上限 10000）。上限同 MRUStorage 一样用 supplier 现取配置。
 * <p>
 * 所有入口都防 NaN：NaN 一旦写进存量，比较全为 false，extract 会变成要多少给多少的无限源，还会存进存档。
 * <p>
 * 机器用法：继承 {@code MRUBlockEntity} 时用带 ESPE 的构造器，基类建好存储、存读档、暴露 capability（见 MRUBlockEntity 类注释）；
 * 从周围的源抽 ESPE 用 {@link ESPETransfer}。自己写的方块实体（不继承 MRUBlockEntity）直接 new 一个，
 * 在 {@link #onChanged} 里标记存盘，capability 按面包成 {@link SidedESPEHandler}。
 */
public class ESPEStorage implements IESPEHandler, INBTSerializable<CompoundTag> {

    private static final String TAG_ESPE = "espe";

    /** 界面、手册、提示里显示 ESPE 数值统一用这个格式：最多两位小数，去掉末尾的 0，不分组（和 MRU 的整数显示一致） */
    private static final ThreadLocal<DecimalFormat> FORMAT = ThreadLocal.withInitial(() -> {
        DecimalFormat format = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));
        // 默认的银行家舍入会把 1.125 显示成 1.12，玩家看着像少算了
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format;
    });

    private final DoubleSupplier maxSupplier;
    private final int tier;
    private double espe;
    private Runnable onChanged = () -> {};

    public ESPEStorage(DoubleSupplier maxSupplier, int tier) {
        this.maxSupplier = maxSupplier;
        this.tier = tier;
    }

    public ESPEStorage onChanged(Runnable onChanged) {
        this.onChanged = onChanged;
        return this;
    }

    @Override
    public double getMaxESPE() {
        return Math.max(0D, maxSupplier.getAsDouble());
    }

    @Override
    public double getESPE() {
        return Math.min(espe, getMaxESPE());
    }

    @Override
    public void setESPE(double amount) {
        if (Double.isNaN(amount)) {
            return;
        }
        double value = Math.max(0D, Math.min(amount, getMaxESPE()));
        if (value != espe) {
            espe = value;
            onChanged.run();
        }
    }

    @Override
    public double addESPE(double amount, boolean doAdd) {
        if (!(amount > 0)) {
            return Double.isNaN(amount) ? 0D : amount;
        }
        double max = getMaxESPE();
        double total = getESPE() + amount;
        if (total >= max) {
            if (doAdd) {
                setESPE(max);
            }
            return total - max;
        }
        if (doAdd) {
            setESPE(total);
        }
        return 0D;
    }

    @Override
    public double extractESPE(double amount, boolean doExtract) {
        if (!(amount > 0)) {
            return 0D;
        }
        double current = getESPE();
        if (current <= amount) {
            if (doExtract) {
                setESPE(0D);
            }
            return current;
        }
        if (doExtract) {
            setESPE(current - amount);
        }
        return amount;
    }

    @Override
    public int getTier() {
        return tier;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble(TAG_ESPE, espe);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        double value = tag.getDouble(TAG_ESPE);
        espe = Double.isNaN(value) ? 0D : Math.max(0D, value);
    }

    /** 显示用的 ESPE 数值：1234.5 → "1234.5"，60 → "60"，0.025 → "0.03"（四舍五入到两位小数） */
    public static String format(double espe) {
        return FORMAT.get().format(Double.isNaN(espe) ? 0D : espe);
    }
}
