package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;

/** 客户端处理 S2C 包。只由 DistExecutor 在客户端调用。 */
public final class ClientPacketHandler {

    private ClientPacketHandler() {
    }

    public static void handlePlayerData(CompoundTag tag) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        IPlayerData data = PlayerDataEvents.get(player);
        if (data != null) {
            // 普通同步包不带图谱（第十三批 13a），收包时保留本地那份
            data.deserializeSync(tag);
            data.clearDirty();
        }
    }

    /**
     * 远程服务器热重载 EC5 的 SERVER 配置后重发的 toml（见 network/S2CConfigResyncPacket）：
     * 照 Forge 进服握手的做法载入（{@code ModConfig.acceptSyncedConfig}），载入后 Forge 发 Reloading 事件，
     * 研究手册、物品提示等读配置的地方随之更新。集成服 / 局域网房主跳过（和集成服共用同一个规格对象，本来就是新的）。
     */
    public static void handleConfigResync(String fileName, byte[] data) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isLocalServer()) {
            return;
        }
        ModConfig config = ConfigTracker.INSTANCE.fileMap().get(fileName);
        // 只认本模组的 SERVER 配置：不能借这个包改别的模组的配置
        if (config == null || !EssentialCraft5.MODID.equals(config.getModId()) || config.getType() != ModConfig.Type.SERVER) {
            return;
        }
        try {
            config.acceptSyncedConfig(data);
        } catch (RuntimeException e) {
            EssentialCraft5.LOGGER.warn("[配置] 载入服务器重发的配置失败：{}", e.toString());
        }
    }
}
