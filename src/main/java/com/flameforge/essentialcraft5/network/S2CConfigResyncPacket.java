package com.flameforge.essentialcraft5.network;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.nio.file.Files;
import java.util.function.Supplier;

/**
 * 服务端 → 客户端：远程服务器热重载了 EC5 的 SERVER 配置，把新的 toml 原文重发给在线玩家。
 * <p>
 * <b>为什么要有</b>：Forge 只在进服握手时把 SERVER 配置发给客户端一次（ConfigSync，NetworkInitialization 里注册的握手包），
 * 服主改了 toml、服务端热重载之后，在线玩家客户端里的配置还是旧的 —— 研究手册里的数值、物品提示里的数、
 * 靠配置决定的渲染都要重进一次服才变。这里补上：服务端收到本模组 SERVER 配置的 {@code ModConfigEvent.Reloading}，
 * 读同一个文件（和 ConfigSync 读的是同一份 {@code getFullPath()}）发给所有人；客户端调 Forge 自己公开的
 * {@code ModConfig.acceptSyncedConfig} 载入，和握手时走同一条路，载入后照常发 Reloading 事件（手册的配置版本号、
 * CuriosCompat 的配置代数都靠它 +1）。
 * <ul>
 *   <li>只有服务端实例存在时才发（{@code ServerLifecycleHooks.getCurrentServer()}）：纯客户端收到自己 accept 引发的 Reloading
 *       时这里拿到 null，不会往回转发；</li>
 *   <li>集成服 / 局域网房主收到自己的包跳过（{@code isLocalServer}，和 Forge 的 receiveSyncedConfig 一样）：它和集成服共用同一个规格对象；</li>
 *   <li>客户端只接受本模组 SERVER 配置的文件名，不能借这个包改别的模组的配置；</li>
 *   <li>整段 try/catch：发包时抛异常会把人踢下线。</li>
 * </ul>
 * 热重载很少发生，每次发一份 toml（几十 KB）没问题。
 */
public record S2CConfigResyncPacket(String fileName, byte[] data) {

    /** toml 原文的上限（EC5 的 SERVER 配置约 30 KB，留足余量；超过就不发） */
    static final int MAX_BYTES = 1 << 20;
    private static final int MAX_NAME = 256;

    public static void encode(S2CConfigResyncPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.fileName, MAX_NAME);
        buf.writeByteArray(packet.data);
    }

    public static S2CConfigResyncPacket decode(FriendlyByteBuf buf) {
        return new S2CConfigResyncPacket(buf.readUtf(MAX_NAME), buf.readByteArray(MAX_BYTES));
    }

    public static void handle(S2CConfigResyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleConfigResync(packet.fileName, packet.data));
    }

    /**
     * 服务端：配置热重载后重发（模组总线）。类名带前缀：network 包里以后再有配置监听，撞名会有一个静默失效
     */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ConfigResyncEvents {

        private ConfigResyncEvents() {
        }

        @SubscribeEvent
        public static void onConfigResyncReloading(ModConfigEvent.Reloading event) {
            ModConfig config = event.getConfig();
            if (!EssentialCraft5.MODID.equals(config.getModId()) || config.getType() != ModConfig.Type.SERVER) {
                return;
            }
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                return;
            }
            // 事件可能在文件监视线程上发：回到服务端主线程再读文件、发包
            server.execute(() -> resend(server, config));
        }

        private static void resend(MinecraftServer server, ModConfig config) {
            try {
                if (server.getPlayerList().getPlayerCount() == 0) {
                    return;
                }
                byte[] bytes = Files.readAllBytes(config.getFullPath());
                if (bytes.length > MAX_BYTES) {
                    EssentialCraft5.LOGGER.warn("[配置] {} 有 {} 字节，超过重发上限，在线玩家要重进服务器才能看到新数值",
                            config.getFileName(), bytes.length);
                    return;
                }
                S2CConfigResyncPacket packet = new S2CConfigResyncPacket(config.getFileName(), bytes);
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    ECNetwork.sendTo(player, packet);
                }
            } catch (Throwable t) {
                EssentialCraft5.LOGGER.warn("[配置] 热重载后重发配置失败（在线玩家要重进服务器才能看到新数值）：{}", t.toString());
            }
        }
    }
}
