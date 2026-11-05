package com.flameforge.essentialcraft5.player;

import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IPlayerData;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 挂到玩家上的 capability 提供者，随玩家 NBT 一起存盘。 */
public class PlayerDataProvider implements ICapabilitySerializable<CompoundTag> {

    private final PlayerData data = new PlayerData();
    private LazyOptional<IPlayerData> holder = LazyOptional.of(() -> data);

    /**
     * 作废过的 holder 在这里重建（第六批 6a 集成时修，2026-09-30）：玩家换维度（下界 / 末地 / 海纳尔之门、跨维度 /tp）时原版先
     * {@code remove(CHANGED_DIMENSION)}（Forge 在里面 invalidateCaps，跑 attach 时登记的监听 {@link #invalidate}），再 {@code revive()}
     * ——revive 只把分发器重新标成有效（{@code CapabilityProvider.reviveCaps}），不通知提供者。以前 holder 作废了就再也拿不到：
     * 换过一次维度后 {@code PlayerDataEvents.get} 一直是 null（腐化、风之好感、海纳尔辐射全部停摆，直到重新登录），
     * 死亡重生时 {@code PlayerEvent.Clone} 里对旧玩家 reviveCaps 也拿不到数据（整份玩家数据没复制过去）。
     * 真正移除的玩家（没 revive）分发器是无效的，{@code getCapability} 根本走不到这里，所以重建不会让「已作废」的玩家复活数据。
     */
    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ECCapabilities.PLAYER_DATA && !holder.isPresent()) {
            holder = LazyOptional.of(() -> data);
        }
        return ECCapabilities.PLAYER_DATA.orEmpty(cap, holder);
    }

    public void invalidate() {
        holder.invalidate();
    }

    @Override
    public CompoundTag serializeNBT() {
        return data.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        data.deserializeNBT(tag);
    }
}
