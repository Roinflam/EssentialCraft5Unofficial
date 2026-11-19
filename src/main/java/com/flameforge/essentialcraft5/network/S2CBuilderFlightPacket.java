package com.flameforge.essentialcraft5.network;

import com.flameforge.essentialcraft5.client.builder.BuilderFlightClient;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 创世神之杖的飞行特效（服务端 → 施工中心附近同维度的玩家，模块包，{@code item/BuilderFlightPlan} 组包、发送）。
 * 只是画面：服务端发包时还没取料、没改世界，真放在 {@code leadTicks} 刻之后照常逐格复核，没放成的格子客户端自己碎掉。
 * <pre>
 * byte   kind          0 = 飞出（放置） 1 = 飞回（撤销） 2 = 收尾
 * varint casterId      施法者实体 id
 * varint jobId
 * byte   leadTicks     提前量（无符号）
 * long   base          BlockPos.asLong（这批格子的最小角）
 * varint paletteSize   ≤ {@value #MAX_PALETTE}，然后 paletteSize 个 varint 方块状态编号（Block.getId）
 * varint count         ≤ {@value #MAX_CELLS}
 * count × { ubyte dx, ubyte dy, ubyte dz, [paletteSize &gt; 1 时] ubyte paletteIndex }
 * </pre>
 * 收尾包的调色板和格子都是空的。超出上限、下标越界、种类不认得的包在解码时丢掉。
 *
 * @param cells 每格打成一个 int：{@code dx | dy << 8 | dz << 16 | paletteIndex << 24}（各取低 8 位）
 */
public record S2CBuilderFlightPacket(int kind, int casterId, int jobId, int leadTicks, long base, int[] palette, int[] cells) {

    public static final int KIND_PLACE = 0;
    public static final int KIND_UNDO = 1;
    public static final int KIND_END = 2;
    public static final int MAX_PALETTE = 8;
    public static final int MAX_CELLS = 256;
    /** 相对坐标每一维最大的偏移（无符号字节） */
    public static final int MAX_OFFSET = 255;

    public static int packCell(int dx, int dy, int dz, int paletteIndex) {
        return (dx & 0xFF) | (dy & 0xFF) << 8 | (dz & 0xFF) << 16 | (paletteIndex & 0xFF) << 24;
    }

    public int count() {
        return cells.length;
    }

    public int dx(int i) {
        return cells[i] & 0xFF;
    }

    public int dy(int i) {
        return cells[i] >>> 8 & 0xFF;
    }

    public int dz(int i) {
        return cells[i] >>> 16 & 0xFF;
    }

    public int paletteIndex(int i) {
        return cells[i] >>> 24 & 0xFF;
    }

    /** 第 i 格的方块状态编号 */
    public int stateId(int i) {
        return palette[paletteIndex(i)];
    }

    /** 第 i 格的世界坐标 */
    public BlockPos pos(int i) {
        BlockPos origin = BlockPos.of(base);
        return origin.offset(dx(i), dy(i), dz(i));
    }

    public static void encode(S2CBuilderFlightPacket packet, FriendlyByteBuf buf) {
        int paletteSize = Math.min(packet.palette.length, MAX_PALETTE);
        int count = Math.min(packet.cells.length, MAX_CELLS);
        buf.writeByte(packet.kind);
        buf.writeVarInt(packet.casterId);
        buf.writeVarInt(packet.jobId);
        buf.writeByte(packet.leadTicks);
        buf.writeLong(packet.base);
        buf.writeVarInt(paletteSize);
        for (int i = 0; i < paletteSize; i++) {
            buf.writeVarInt(packet.palette[i]);
        }
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            int cell = packet.cells[i];
            buf.writeByte(cell & 0xFF);
            buf.writeByte(cell >>> 8 & 0xFF);
            buf.writeByte(cell >>> 16 & 0xFF);
            if (paletteSize > 1) {
                buf.writeByte(cell >>> 24 & 0xFF);
            }
        }
    }

    public static S2CBuilderFlightPacket decode(FriendlyByteBuf buf) {
        int kind = buf.readUnsignedByte();
        if (kind > KIND_END) {
            throw new DecoderException("创世神之杖飞行包的种类不对：" + kind);
        }
        int casterId = buf.readVarInt();
        int jobId = buf.readVarInt();
        int leadTicks = buf.readUnsignedByte();
        long base = buf.readLong();
        int paletteSize = buf.readVarInt();
        if (paletteSize < 0 || paletteSize > MAX_PALETTE) {
            throw new DecoderException("创世神之杖飞行包的调色板大小不对：" + paletteSize);
        }
        int[] palette = new int[paletteSize];
        for (int i = 0; i < paletteSize; i++) {
            palette[i] = buf.readVarInt();
        }
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_CELLS) {
            throw new DecoderException("创世神之杖飞行包的格数不对：" + count);
        }
        if (count > 0 && paletteSize == 0) {
            throw new DecoderException("创世神之杖飞行包有格子却没有调色板");
        }
        int[] cells = new int[count];
        for (int i = 0; i < count; i++) {
            int dx = buf.readUnsignedByte();
            int dy = buf.readUnsignedByte();
            int dz = buf.readUnsignedByte();
            int index = paletteSize > 1 ? buf.readUnsignedByte() : 0;
            if (index >= paletteSize) {
                throw new DecoderException("创世神之杖飞行包的调色板下标越界：" + index);
            }
            cells[i] = packCell(dx, dy, dz, index);
        }
        return new S2CBuilderFlightPacket(kind, casterId, jobId, leadTicks, base, palette, cells);
    }

    /** 客户端主线程调 */
    public static void handleClient(S2CBuilderFlightPacket packet) {
        BuilderFlightClient.handle(packet);
    }
}
