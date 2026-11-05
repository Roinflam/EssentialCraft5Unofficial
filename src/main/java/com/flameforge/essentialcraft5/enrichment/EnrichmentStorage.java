package com.flameforge.essentialcraft5.enrichment;

import com.flameforge.essentialcraft5.mru.MRUBalance;
import com.flameforge.essentialcraft5.mru.MRUStorage;
import net.minecraft.nbt.CompoundTag;

import java.util.function.IntSupplier;

/**
 * 富集仓控制器的 MRU 存储。继承 {@link MRUStorage} 只是为了能直接交给 {@code MRULink.pull}（接收器从绑定宝石拉 MRU）、
 * {@code MachineData}（状态检测器的界面）；存量和平衡值都用自己的字段，父类的字段不用。
 * <p>
 * 和普通存储的区别：<b>存量可以高于上限</b>。上限随外壳上的存储方块数变（原作 60000 + 每个存储方块 100000），
 * 拆掉存储方块、或者结构暂时不完整时上限会变小。原作这时下次加 MRU 就把存量截断到新上限（白白删掉）；
 * 这里超出的部分原样保留，<b>只出不进</b>：存量不低于上限时加不进去，取出照常，取到上限以下再恢复正常。
 * 读档也不按上限截断（读档时结构还没扫，上限是基础值）。
 */
public class EnrichmentStorage extends MRUStorage {

    private static final String TAG_MRU = "mru";
    private static final String TAG_BALANCE = "balance";

    private int stored;
    private float balance = MRUBalance.PURE;
    private Runnable changed = () -> {};

    public EnrichmentStorage(IntSupplier capacity) {
        super(capacity);
    }

    @Override
    public EnrichmentStorage onChanged(Runnable onChanged) {
        this.changed = onChanged;
        return this;
    }

    /** 实际存量，可能高于上限（见类注释） */
    @Override
    public int getMRU() {
        return stored;
    }

    /** 直接设置：不能把存量抬到上限以上，但存量本来就超出时往下调不会被截到上限 */
    @Override
    public void setMRU(int amount) {
        int value = Math.max(0, Math.min(amount, Math.max(getMaxMRU(), stored)));
        if (value != stored) {
            stored = value;
            changed.run();
        }
    }

    @Override
    public int addMRU(int amount, boolean doAdd) {
        if (amount <= 0) {
            return amount;
        }
        int max = getMaxMRU();
        if (stored >= max) {
            // 满了，或者超出上限（只出不进）
            return amount;
        }
        long total = (long) stored + amount;
        if (total >= max) {
            if (doAdd) {
                set(max);
            }
            return (int) (total - max);
        }
        if (doAdd) {
            set((int) total);
        }
        return 0;
    }

    @Override
    public int extractMRU(int amount, boolean doExtract) {
        if (amount <= 0) {
            return 0;
        }
        int taken = Math.min(amount, stored);
        if (doExtract && taken > 0) {
            set(stored - taken);
        }
        return taken;
    }

    @Override
    public float getBalance() {
        return balance;
    }

    @Override
    public void setBalance(float balance) {
        float value = MRUBalance.clamp(balance);
        if (value != this.balance) {
            this.balance = value;
            changed.run();
        }
    }

    /** 是否超出上限（只出不进） */
    public boolean isOverCapacity() {
        return stored > getMaxMRU();
    }

    private void set(int value) {
        if (value != stored) {
            stored = value;
            changed.run();
        }
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_MRU, stored);
        tag.putFloat(TAG_BALANCE, balance);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        stored = Math.max(0, tag.getInt(TAG_MRU));
        balance = tag.contains(TAG_BALANCE) ? MRUBalance.clamp(tag.getFloat(TAG_BALANCE)) : MRUBalance.PURE;
    }
}
