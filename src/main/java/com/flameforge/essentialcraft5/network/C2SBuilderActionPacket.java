package com.flameforge.essentialcraft5.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 创世神之杖的操作（客户端 → 服务端，模块包，{@code ECBuilder.init} 登记）：魔杖键 + 滚轮调尺寸、单按魔杖键切形状、魔杖键 + 右键撤销、
 * 停下、客户端报自己的确认阈值、拆一块模块、远域模块按远处点中的格子开工、设混铺表、设回收的丢弃清单，
 * 以及面板（client/screen/BuilderPanelScreen）的设形状、换模式、开关模块、从身上挑、要清单。
 * 服务端在 {@code item/BuilderActions} 里校验（手里是不是魔杖、限频、夹值）。
 * <pre>
 * byte action   见 {@link #SET_SIZE} 等
 * byte hand     0 主手 1 副手
 * varint a, b   SET_SIZE：宽、长；CLIENT_PREFS：a = 确认阈值；REMOVE_MODULE：a = 模块序号（BuilderModules.Kind 的 ordinal）；
 *               SET_MIX / SET_DISCARD：a = 后面跟几项（0 = 清空）；其余不用
 * 只有 AIM_START 后面再跟：
 * long pos      点中的格子（BlockPos.asLong）
 * byte face     点中的面（Direction 的 3D 序号 0~5）
 * 只有 SET_MIX 后面再跟 a 项：
 * ResourceLocation block, varint weight
 * 只有 SET_DISCARD 后面再跟 a 项：
 * ResourceLocation item
 * 只有 PICK_BLOCK 后面再跟 1 项（a 必须是 1）：
 * ResourceLocation block
 * 面板的几个操作：SET_SHAPE a = 形状序号（BuilderShape 的 ordinal）；SET_MODE a = 模式序号（BuilderMode 的 ordinal）；
 * TOGGLE_MODULE a = 模块序号、b = 1 开 0 关；PANEL_SYNC 不带参数（要一份面板用的清单，回 S2CBuilderPanelPacket）
 * </pre>
 * 以后加面板操作只追加 action 的取值；别的 action 的格式不变。坐标、面都不信：服务端（{@code item/BuilderAim}）按建筑高度、世界边界、
 * 离眼睛多远先筛一遍，再用自己的视线复核。表里的注册名、权重、项数也都不信，服务端逐项校验；项数超过 {@value #MAX_LIST} 的包直接解不开。
 */
public record C2SBuilderActionPacket(int action, int hand, int a, int b, long pos, int face, List<ResourceLocation> ids, List<Integer> weights) {

    public static final int SET_SIZE = 0;
    public static final int CYCLE_SHAPE = 1;
    public static final int UNDO = 2;
    public static final int STOP = 3;
    public static final int CLIENT_PREFS = 4;
    /** 拆下手里魔杖上的一块模块（item/BuilderModules#removeFromHand：先删 NBT 再给物品，施工中不拆） */
    public static final int REMOVE_MODULE = 5;
    /** 远域模块：按客户端远距离瞄准点中的格子和面开工 / 确认（item/BuilderAim#start，服务端复核） */
    public static final int AIM_START = 6;
    /** 设混铺表（item/BuilderActions#setMix）：方块注册名 + 权重 */
    public static final int SET_MIX = 7;
    /** 设回收的丢弃清单（item/BuilderActions#setDiscard）：物品注册名 */
    public static final int SET_DISCARD = 8;
    /** 面板：直接设形状（一阶只能设两点、铺面、直线） */
    public static final int SET_SHAPE = 9;
    /** 面板：把手里的魔杖换成别的模式（item/BuilderActions#setMode，整份 NBT 照抄；铺设魔杖没有别的模式） */
    public static final int SET_MODE = 10;
    /** 面板：开 / 关一块模块（item/BuilderModules#toggleFromHand，停用的不生效但占槽） */
    public static final int TOGGLE_MODULE = 11;
    /** 面板「从身上挑」：把身上有的一种方块设为选定方块（item/BuilderActions#pickBlock） */
    public static final int PICK_BLOCK = 12;
    /** 面板开着时要一份清单（身上的材料、撤销上一次能不能用、最近拆下的；item/BuilderPanel，有频率限制） */
    public static final int PANEL_SYNC = 13;

    /** 表类操作一个包最多带几项（服务端另按配置夹得更小） */
    public static final int MAX_LIST = 64;

    /** 不带表的操作 */
    public C2SBuilderActionPacket(int action, int hand, int a, int b, long pos, int face) {
        this(action, hand, a, b, pos, face, List.of(), List.of());
    }

    /** 不带坐标的操作 */
    public C2SBuilderActionPacket(int action, int hand, int a, int b) {
        this(action, hand, a, b, 0L, 0);
    }

    /** 远域开工：hand 0 主手 1 副手 */
    public static C2SBuilderActionPacket aim(int hand, BlockPos pos, Direction face) {
        return new C2SBuilderActionPacket(AIM_START, hand, 0, 0, pos.asLong(), face.get3DDataValue());
    }

    /** 设混铺表：blocks 和 weights 一一对应；都空 = 清空 */
    public static C2SBuilderActionPacket setMix(int hand, List<ResourceLocation> blocks, List<Integer> weights) {
        if (blocks.size() != weights.size()) {
            throw new IllegalArgumentException("方块和权重数目不一致");
        }
        return new C2SBuilderActionPacket(SET_MIX, hand, blocks.size(), 0, 0L, 0, List.copyOf(blocks), List.copyOf(weights));
    }

    /** 从身上挑：设选定方块 */
    public static C2SBuilderActionPacket pickBlock(int hand, ResourceLocation block) {
        return new C2SBuilderActionPacket(PICK_BLOCK, hand, 1, 0, 0L, 0, List.of(block), List.of());
    }

    /** 设丢弃清单；空 = 清空 */
    public static C2SBuilderActionPacket setDiscard(int hand, List<ResourceLocation> items) {
        return new C2SBuilderActionPacket(SET_DISCARD, hand, items.size(), 0, 0L, 0, List.copyOf(items), List.of());
    }

    public static void encode(C2SBuilderActionPacket packet, FriendlyByteBuf buf) {
        buf.writeByte(packet.action);
        buf.writeByte(packet.hand);
        buf.writeVarInt(packet.a);
        buf.writeVarInt(packet.b);
        if (packet.action == AIM_START) {
            buf.writeLong(packet.pos);
            buf.writeByte(packet.face);
        } else if (packet.action == SET_MIX) {
            for (int i = 0; i < packet.a; i++) {
                buf.writeResourceLocation(packet.ids.get(i));
                buf.writeVarInt(packet.weights.get(i));
            }
        } else if (packet.action == SET_DISCARD || packet.action == PICK_BLOCK) {
            for (int i = 0; i < packet.a; i++) {
                buf.writeResourceLocation(packet.ids.get(i));
            }
        }
    }

    public static C2SBuilderActionPacket decode(FriendlyByteBuf buf) {
        int action = buf.readUnsignedByte();
        int hand = buf.readUnsignedByte();
        int a = buf.readVarInt();
        int b = buf.readVarInt();
        if (action == AIM_START) {
            return new C2SBuilderActionPacket(action, hand, a, b, buf.readLong(), buf.readUnsignedByte());
        }
        if (action == SET_MIX || action == SET_DISCARD || action == PICK_BLOCK) {
            if (a < 0 || a > MAX_LIST || action == PICK_BLOCK && a != 1) {
                throw new DecoderException("创世神之杖操作包的表项数不对：" + a);
            }
            List<ResourceLocation> ids = new ArrayList<>(a);
            List<Integer> weights = new ArrayList<>(action == SET_MIX ? a : 0);
            for (int i = 0; i < a; i++) {
                ids.add(buf.readResourceLocation());
                if (action == SET_MIX) {
                    weights.add(buf.readVarInt());
                }
            }
            return new C2SBuilderActionPacket(action, hand, a, b, 0L, 0, ids, weights);
        }
        return new C2SBuilderActionPacket(action, hand, a, b);
    }
}
