package com.flameforge.essentialcraft5.mru;

import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IMRUHandlerItem;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 物品上的 MRU 存储，数据直接写在物品 NBT 顶层（mru / balance / storage），和原作 MRUItemStorage 同一套键名。
 * 由物品的 {@code initCapabilities} 创建；每次读写都直接读物品 NBT，不像原作那样缓存一份再比对。
 * <p>
 * storage 分两种：构造时给定（储存物品恒为 true），或者可切换（写在 NBT 里）。
 */
public class MRUItemStorage implements IMRUHandlerItem, ICapabilityProvider {

    private static final String TAG_MRU = "mru";
    private static final String TAG_BALANCE = "balance";
    private static final String TAG_STORAGE = "storage";

    private final ItemStack stack;
    private final int maxMRU;
    /** null 表示可切换、存在 NBT 里 */
    @Nullable
    private final Boolean fixedStorage;
    private final LazyOptional<IMRUHandlerItem> holder = LazyOptional.of(() -> this);

    public MRUItemStorage(ItemStack stack, int maxMRU) {
        this(stack, maxMRU, null);
    }

    public MRUItemStorage(ItemStack stack, int maxMRU, @Nullable Boolean fixedStorage) {
        this.stack = stack;
        this.maxMRU = maxMRU;
        this.fixedStorage = fixedStorage;
    }

    @Override
    public int getMaxMRU() {
        return maxMRU;
    }

    @Override
    public int getMRU() {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : Mth.clamp(tag.getInt(TAG_MRU), 0, maxMRU);
    }

    @Override
    public void setMRU(int amount) {
        stack.getOrCreateTag().putInt(TAG_MRU, Mth.clamp(amount, 0, maxMRU));
    }

    @Override
    public int addMRU(int amount, boolean doAdd) {
        if (amount <= 0) {
            return amount;
        }
        long total = (long) getMRU() + amount;
        if (total >= maxMRU) {
            if (doAdd) {
                setMRU(maxMRU);
            }
            return (int) (total - maxMRU);
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
            if (doExtract && current > 0) {
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
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TAG_BALANCE) ? MRUBalance.clamp(tag.getFloat(TAG_BALANCE)) : MRUBalance.PURE;
    }

    @Override
    public void setBalance(float balance) {
        stack.getOrCreateTag().putFloat(TAG_BALANCE, MRUBalance.clamp(balance));
    }

    @Override
    public boolean isStorage() {
        if (fixedStorage != null) {
            return fixedStorage;
        }
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(TAG_STORAGE);
    }

    @Override
    public void setStorage(boolean storage) {
        if (fixedStorage == null) {
            stack.getOrCreateTag().putBoolean(TAG_STORAGE, storage);
        }
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return ECCapabilities.MRU_ITEM.orEmpty(cap, holder);
    }
}
