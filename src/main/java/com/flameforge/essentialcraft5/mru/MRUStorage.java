package com.flameforge.essentialcraft5.mru;

import com.flameforge.essentialcraft5.api.IMRUHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraftforge.common.util.INBTSerializable;

import java.util.function.IntSupplier;

/**
 * 方块实体（以及以后 MRUCU 实体）用的 MRU 存储。对应原作 {@code MRUStorage}。
 * <p>
 * 上限用 {@link IntSupplier} 取：机器的上限来自服务端配置，配置在世界加载后才可用、而且可以在游戏中改，
 * 所以每次现取（配置值有缓存，很便宜）。上限被调低时存量按新上限夹住。
 * 值有变化时回调 {@code onChanged}，方块实体借此 setChanged 并安排同步。
 */
public class MRUStorage implements IMRUHandler, INBTSerializable<CompoundTag> {

    private static final String TAG_MRU = "mru";
    private static final String TAG_BALANCE = "balance";

    private final IntSupplier maxSupplier;
    private int mru;
    private float balance = MRUBalance.PURE;
    private Runnable onChanged = () -> {};

    public MRUStorage(IntSupplier maxSupplier) {
        this.maxSupplier = maxSupplier;
    }

    public MRUStorage(int max) {
        this(() -> max);
    }

    public MRUStorage onChanged(Runnable onChanged) {
        this.onChanged = onChanged;
        return this;
    }

    @Override
    public int getMaxMRU() {
        return Math.max(0, maxSupplier.getAsInt());
    }

    @Override
    public int getMRU() {
        return Math.min(mru, getMaxMRU());
    }

    @Override
    public void setMRU(int amount) {
        int value = Mth.clamp(amount, 0, getMaxMRU());
        if (value != mru) {
            mru = value;
            onChanged.run();
        }
    }

    @Override
    public int addMRU(int amount, boolean doAdd) {
        if (amount <= 0) {
            return amount;
        }
        int max = getMaxMRU();
        // 用 long 防溢出：原作 mru + amount 在大数下会绕回负数
        long total = (long) getMRU() + amount;
        if (total >= max) {
            if (doAdd) {
                setMRU(max);
            }
            return (int) (total - max);
        }
        if (doAdd) {
            setMRU((int) total);
        }
        return 0;
    }

    @Override
    public int extractMRU(int amount, boolean doExtract) {
        if (amount <= 0) {
            return 0;
        }
        int current = getMRU();
        if (current <= amount) {
            if (doExtract) {
                setMRU(0);
            }
            return current;
        }
        if (doExtract) {
            setMRU(current - amount);
        }
        return amount;
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
            onChanged.run();
        }
    }

    /**
     * 从 source 拉取，最多拉满自身。平衡值按数量加权平均（原作 MRUTileStorage.mruIn 的算法），
     * 并补上原作跨维度版漏掉的除零判断（它会把平衡值算成 NaN）。
     *
     * @return 实际拉到的量
     */
    public int pullFrom(IMRUHandler source) {
        int current = getMRU();
        int request = getMaxMRU() - current;
        if (request <= 0) {
            return 0;
        }
        int extracted = source.extractMRU(request, true);
        if (extracted <= 0) {
            // 一点没拉到就别重算平衡值：(b×c)/c 的浮点误差会让平衡值来回抖，每次抖动都触发一次方块实体同步
            return 0;
        }
        if (extracted + current > 0) {
            setBalance((source.getBalance() * extracted + getBalance() * current) / (extracted + current));
        }
        addMRU(extracted, true);
        return extracted;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_MRU, mru);
        tag.putFloat(TAG_BALANCE, balance);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        mru = Math.max(0, tag.getInt(TAG_MRU));
        balance = tag.contains(TAG_BALANCE) ? MRUBalance.clamp(tag.getFloat(TAG_BALANCE)) : MRUBalance.PURE;
    }
}
