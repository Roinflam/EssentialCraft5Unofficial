package com.flameforge.essentialcraft5.network;

import com.flameforge.essentialcraft5.client.BuilderClientStatus;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 创世神之杖面板要的清单（服务端 → 本人，模块包，{@code ECBuilder.init} 登记）：面板开着时客户端隔一会儿发一次 {@code PANEL_SYNC}，
 * 服务端（{@code item/BuilderPanel}）限频后回这一份。随身容器里的东西客户端看不到，撤销栈只在服务端，所以这些数由服务端给。
 * <pre>
 * byte    undoState        见 {@link #UNDO_READY} 等
 * byte    undoShape        撤销栈顶那条开工时的形状（BuilderShape 的 ordinal），不知道是 255
 * varint  undoW, undoL, undoH, undoCells, undoAgeSeconds   那条记录的外框（x、z、y 三向格数）、格数、过了多少秒
 * bool    clearedSince     那条记录之后又做过（或正在做）一单清除类：清除的结果不能撤，也不越过它撤更早那次放置
 * varint  n                身上的材料种数（≤ {@value #MAX_MATERIALS}）
 *   ResourceLocation item, varlong body, varlong box
 * bool    truncated        材料不止这么多种，只发了最多的那些
 * bool    containersSeen   这次看了随身容器（开着别的界面、或服务器关了容器取料时不看）
 * varint  m                回收最近拆下过的物品种数（≤ {@value #MAX_RECENT}）
 *   ResourceLocation item
 * </pre>
 */
public record S2CBuilderPanelPacket(int undoState, int undoShape, int undoWidth, int undoLength, int undoHeight, int undoCells, int undoAgeSeconds,
                                    boolean clearedSince, List<Entry> materials, boolean truncated, boolean containersSeen,
                                    List<ResourceLocation> recent) {

    /** 一包最多带几种材料 */
    public static final int MAX_MATERIALS = 64;
    /** 一包最多带几种最近拆下的 */
    public static final int MAX_RECENT = 16;

    /** 撤销上一次能用 */
    public static final int UNDO_READY = 0;
    /** 没有能撤的（从没放过、或撤完了） */
    public static final int UNDO_NONE = 1;
    /** 没有能撤的，而且上一单是清除类（清除的结果不能撤） */
    public static final int UNDO_CLEARED = 2;
    /** 过了时限，或者记录在别的维度 */
    public static final int UNDO_EXPIRED = 3;
    /** 离那片太远 */
    public static final int UNDO_FAR = 4;
    /** 正在撤销（或撤销排着队） */
    public static final int UNDO_BUSY = 5;
    /** 服务器关掉了撤销 */
    public static final int UNDO_OFF = 6;

    /** 一种材料：物品注册名、背包和副手里有多少、随身容器里有多少 */
    public record Entry(ResourceLocation item, long body, long box) {
        public long total() {
            return body + box;
        }
    }

    public static void encode(S2CBuilderPanelPacket packet, FriendlyByteBuf buf) {
        buf.writeByte(packet.undoState);
        buf.writeByte(packet.undoShape < 0 ? 255 : packet.undoShape);
        buf.writeVarInt(packet.undoWidth);
        buf.writeVarInt(packet.undoLength);
        buf.writeVarInt(packet.undoHeight);
        buf.writeVarInt(packet.undoCells);
        buf.writeVarInt(packet.undoAgeSeconds);
        buf.writeBoolean(packet.clearedSince);
        int n = Math.min(packet.materials.size(), MAX_MATERIALS);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Entry entry = packet.materials.get(i);
            buf.writeResourceLocation(entry.item);
            buf.writeVarLong(entry.body);
            buf.writeVarLong(entry.box);
        }
        buf.writeBoolean(packet.truncated);
        buf.writeBoolean(packet.containersSeen);
        int m = Math.min(packet.recent.size(), MAX_RECENT);
        buf.writeVarInt(m);
        for (int i = 0; i < m; i++) {
            buf.writeResourceLocation(packet.recent.get(i));
        }
    }

    public static S2CBuilderPanelPacket decode(FriendlyByteBuf buf) {
        int state = buf.readUnsignedByte();
        int shape = buf.readUnsignedByte();
        int w = buf.readVarInt();
        int l = buf.readVarInt();
        int h = buf.readVarInt();
        int cells = buf.readVarInt();
        int age = buf.readVarInt();
        boolean cleared = buf.readBoolean();
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_MATERIALS) {
            throw new DecoderException("创世神之杖面板清单的材料种数不对：" + n);
        }
        List<Entry> materials = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            materials.add(new Entry(buf.readResourceLocation(), buf.readVarLong(), buf.readVarLong()));
        }
        boolean truncated = buf.readBoolean();
        boolean seen = buf.readBoolean();
        int m = buf.readVarInt();
        if (m < 0 || m > MAX_RECENT) {
            throw new DecoderException("创世神之杖面板清单的最近拆下种数不对：" + m);
        }
        List<ResourceLocation> recent = new ArrayList<>(m);
        for (int i = 0; i < m; i++) {
            recent.add(buf.readResourceLocation());
        }
        return new S2CBuilderPanelPacket(state, shape == 255 ? -1 : shape, w, l, h, cells, age, cleared, List.copyOf(materials), truncated, seen,
                List.copyOf(recent));
    }

    /** 客户端主线程调（{@code ECNetwork.registerS2C} 已经套了只在客户端跑） */
    public static void handleClient(S2CBuilderPanelPacket packet) {
        BuilderClientStatus.acceptPanel(packet);
    }
}
