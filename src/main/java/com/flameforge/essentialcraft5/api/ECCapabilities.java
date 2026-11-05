package com.flameforge.essentialcraft5.api;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;

/**
 * 本模组的所有 capability。方块实体的 MRU 和物品的 MRU 分开两个，与原作一致：
 * 查方块实体时不会误拿到物品语义（isStorage 等）。
 */
public final class ECCapabilities {

    public static final Capability<IMRUHandler> MRU = CapabilityManager.get(new CapabilityToken<>() {});
    public static final Capability<IMRUHandlerItem> MRU_ITEM = CapabilityManager.get(new CapabilityToken<>() {});
    public static final Capability<IESPEHandler> ESPE = CapabilityManager.get(new CapabilityToken<>() {});
    public static final Capability<IPlayerData> PLAYER_DATA = CapabilityManager.get(new CapabilityToken<>() {});

    private ECCapabilities() {
    }

    public static void register(RegisterCapabilitiesEvent event) {
        event.register(IMRUHandler.class);
        event.register(IMRUHandlerItem.class);
        event.register(IESPEHandler.class);
        event.register(IPlayerData.class);
    }
}
