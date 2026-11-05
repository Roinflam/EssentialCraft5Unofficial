package com.flameforge.essentialcraft5.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * 宅男终端（客户端 → 服务端，ECComputer 登记，接在压制状态包后面）：意图——动作、部位、格子三个字节，模块的两个动作后面再跟这一格模块的注册名。
 * 能不能做由服务端自己查（item/ComputerSealField#handleTerminalPacket，模块的两个动作转给 item/module/ModuleTerminal），不信客户端的任何状态；未知动作直接丢弃。
 * <ul>
 *   <li>{@link #TOGGLE_SEAL}：开 / 关饰品封禁场（要穿齐四件、不是旁观、功能可用；同一人 5 刻内只认一次）。</li>
 *   <li>{@link #REFRESH}：要一份终端数据（要戴着头盔；同一人 10 刻内只回一次）。终端开着时每 20 刻发一次，关着时一个包都不发。</li>
 *   <li>{@link #MODULE_TOGGLE}（第十批 10c）：开 / 关 {@code part} 那件盔甲上第 {@code slot} 格的模块（要穿着那件、格子里有模块、模块能开关、不是旁观；
 *       和 {@link #MODULE_REMOVE} 合起来同一人 5 刻内只认一次）。</li>
 *   <li>{@link #MODULE_REMOVE}（第十批 10c）：拆下那一格的模块（先删 NBT、同一刻把模块物品给玩家，背包满了掉在脚下）。</li>
 * </ul>
 * 封禁场的两个动作 {@code part} / {@code slot} 不用、发 0，也不带注册名。
 * 夜视按钮照旧发 {@link C2SComputerArmorPacket#NIGHT_VISION}。
 * <p>
 * 模块的两个动作带上界面上那一行的模块注册名（{@code writeUtf}，最长 {@value #MAX_MODULE_ID} 字符）：格子号是按装上的先后排的，拆掉一块后面的往前挪，
 * 新名单到之前连点两下、或者打开终端时还在用上次留下的名单，格子号会指到别的模块上。服务端核对这一格现在装的确实是它，对不上就什么都不做、回一份新名单。
 * 10c 加上这一段时协议号已经是 "8"（10c 才升、还没发布过），没有再升。
 *
 * @param action 动作
 * @param part   部位（0 头 1 胸 2 腿 3 靴；只有模块的两个动作用）
 * @param slot   格子号（只有模块的两个动作用）
 * @param module 界面上这一格的模块注册名（只有模块的两个动作写进包里；别的动作是空字符串）
 */
public record C2SComputerTerminalPacket(byte action, byte part, byte slot, String module) {

    /** 开 / 关饰品封禁场 */
    public static final byte TOGGLE_SEAL = 0;
    /** 要一份终端数据 */
    public static final byte REFRESH = 1;
    /** 开 / 关一格模块（第十批 10c） */
    public static final byte MODULE_TOGGLE = 2;
    /** 拆下一格模块（第十批 10c） */
    public static final byte MODULE_REMOVE = 3;
    /**
     * 模块注册名最长几个字符：和服务端发名单时的 {@code writeResourceLocation} 同一个上限，名单里有什么（包括指令改出来的长注册名）都能原样发回来，
     * 不会在客户端编码时出错
     */
    public static final int MAX_MODULE_ID = 32767;

    public C2SComputerTerminalPacket {
        module = module == null ? "" : module;
    }

    public C2SComputerTerminalPacket(byte action) {
        this(action, (byte) 0, (byte) 0, "");
    }

    /** 不带注册名（模块的两个动作这样发会被服务端当成名单过期，见类注释） */
    public C2SComputerTerminalPacket(byte action, byte part, byte slot) {
        this(action, part, slot, "");
    }

    /** 模块的两个动作：部位、格子号、界面上这一格的模块注册名 */
    public C2SComputerTerminalPacket(byte action, int part, int slot, String module) {
        this(action, (byte) part, (byte) slot, module);
    }

    /** 这个动作在包里带不带注册名 */
    public static boolean carriesModule(byte action) {
        return action == MODULE_TOGGLE || action == MODULE_REMOVE;
    }

    public static void encode(C2SComputerTerminalPacket packet, FriendlyByteBuf buf) {
        buf.writeByte(packet.action);
        buf.writeByte(packet.part);
        buf.writeByte(packet.slot);
        if (carriesModule(packet.action)) {
            buf.writeUtf(packet.module, MAX_MODULE_ID);
        }
    }

    public static C2SComputerTerminalPacket decode(FriendlyByteBuf buf) {
        byte action = buf.readByte();
        byte part = buf.readByte();
        byte slot = buf.readByte();
        String module = carriesModule(action) ? buf.readUtf(MAX_MODULE_ID) : "";
        return new C2SComputerTerminalPacket(action, part, slot, module);
    }
}
