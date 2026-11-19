package com.flameforge.essentialcraft5.network;

import com.flameforge.essentialcraft5.client.BuilderClientStatus;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 混铺时每种料各持有多少（服务端 → 本人，模块包，{@code item/BuilderStatus} 和状态包同一轮算，变了才发）。
 * 准星下的缺料红字按每种料分别判（和开工前的二次确认同一个口径：任一种持有少于这一下要用的就算不够），状态包里只有几种合计，所以另发这一份。
 * 混铺不生效时发一次空表。
 * <pre>
 * varint n                 种数（≤ {@value #MAX_KINDS}，按混铺表的顺序）
 *   ResourceLocation item, varlong count（背包、副手和随身容器合计）
 * </pre>
 */
public record S2CBuilderHoldingsPacket(List<ResourceLocation> items, List<Long> counts) {

    public static final int MAX_KINDS = 4;

    public static void encode(S2CBuilderHoldingsPacket packet, FriendlyByteBuf buf) {
        int n = Math.min(Math.min(packet.items.size(), packet.counts.size()), MAX_KINDS);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeResourceLocation(packet.items.get(i));
            buf.writeVarLong(packet.counts.get(i));
        }
    }

    public static S2CBuilderHoldingsPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_KINDS) {
            throw new DecoderException("创世神之杖混铺持有数的种数不对：" + n);
        }
        List<ResourceLocation> items = new ArrayList<>(n);
        List<Long> counts = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            items.add(buf.readResourceLocation());
            counts.add(buf.readVarLong());
        }
        return new S2CBuilderHoldingsPacket(List.copyOf(items), List.copyOf(counts));
    }

    /** 客户端主线程调 */
    public static void handleClient(S2CBuilderHoldingsPacket packet) {
        BuilderClientStatus.acceptHoldings(packet);
    }
}
