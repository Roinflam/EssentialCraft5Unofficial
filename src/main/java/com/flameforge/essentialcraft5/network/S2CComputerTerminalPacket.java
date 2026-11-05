package com.flameforge.essentialcraft5.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 宅男终端的数据（服务端 → 客户端，ECComputer 登记，接在终端意图包后面；协议 8）。两种发法：
 * <ul>
 *   <li><b>带名单</b>（{@code detail = true}）：只在收到 {@link C2SComputerTerminalPacket#REFRESH} 时回；终端开着时每 20 刻一次，关着时一个包都没有。</li>
 *   <li><b>只带状态</b>（{@code detail = false}，名单为空）：封禁场的状态变了（开 / 关、待机 ↔ 运转、电量不足、没穿齐……）时发给穿戴者本人，
 *       屏幕顶上的紫色小圆和终端的开关按它画。只在变化时发，不随评估每秒发。</li>
 * </ul>
 * 格式：场状态（1 字节，{@link FieldState} 的序号）、不可用原因（1 字节，{@code CurioSeal.Availability} 的序号）、每秒耗电（VarInt）、
 * 身上能付的 MRU（VarLong，{@link Long#MAX_VALUE} = 不限）、这一秒压制的别的玩家数（VarInt）、是否带名单（1 字节），
 * 带名单时再写条数（VarInt，≤ {@value #MAX_ENTRIES}）和每条：名字（{@code writeUtf(…, 16)}）、状态（1 字节，{@link TargetState} 的序号）、
 * 距离（1 字节，0~255 格）、另一名压制他的穿戴者（{@code writeUtf(…, 16)}，没有写空串）。
 * <p>
 * 第十批 10c（协议 7 → 8）：带名单的包在名单后面再写「模块」页的数据 {@link ModulePanel}——标志（1 字节：穿齐四件、要不要穿齐）、
 * 四件的模块槽（4 字节，按头、胸、腿、靴，没穿能装模块的盔甲写 -1）、这一秒的模块电费（VarInt）、格数（VarInt，≤ {@value #MAX_MODULES}）和每格：
 * 部位（1 字节，0 头 1 胸 2 腿 3 靴）、格子号（1 字节）、模块注册名（{@code writeResourceLocation}）、状态（1 字节，{@code ModuleCache.Status} 的序号）、
 * 开着（1 字节）、冷却剩余刻（VarInt）。只带状态的包不写这一段。
 *
 * @param fieldState   场状态
 * @param availability 不可用时的原因（{@code CurioSeal.Availability} 的序号，能用时是 0）
 * @param mruPerSecond 这一秒的耗电（待机、关着时 0；电量不足时是要付却付不起的数）
 * @param mruAvailable 身上能付的 MRU（储存物品 + 胸甲自存）
 * @param sealedOthers 这一秒压制了几名别的玩家
 * @param detail       是否带名单
 * @param entries      范围内的玩家（≤ {@value #MAX_ENTRIES} 条）
 * @param modules      「模块」页（只在带名单的包里有内容，别的时候是 {@link ModulePanel#EMPTY}）
 */
public record S2CComputerTerminalPacket(byte fieldState, byte availability, int mruPerSecond, long mruAvailable, int sealedOthers,
                                        boolean detail, List<Entry> entries, ModulePanel modules) {

    /** 名单最多几条 */
    public static final int MAX_ENTRIES = 32;
    /** 模块最多几格（四件 × 每件最多 4 格） */
    public static final int MAX_MODULES = 16;
    private static final int NAME_MAX = 16;

    /** 封禁场的状态 */
    public enum FieldState {
        /** 关着 */
        OFF,
        /** 开着、范围内没有能压制的人：不耗电、不压自己 */
        STANDBY,
        /** 至少压制一人 */
        RUNNING,
        /** 有人可压制，但这一秒的电付不起：谁都不压 */
        NO_POWER,
        /** 没穿齐四件 */
        INCOMPLETE,
        /** 这个功能用不了（看不可用原因） */
        UNAVAILABLE,
        /** 开着，但穿戴者所在的维度在 {@code disabled_dimensions} 里：谁都不压、不耗电 */
        DISABLED_WORLD;

        public static FieldState of(byte b) {
            FieldState[] values = values();
            return b >= 0 && b < values.length ? values[b] : OFF;
        }
    }

    /** 名单里一个人的状态 */
    public enum TargetState {
        /** 压制中 */
        SEALED,
        /** 压制中，也被别的穿戴者压制（{@link Entry#other()} 是其中一人） */
        SEALED_SHARED,
        /** 不能互相伤害（PvP 关着、同队不许友伤） */
        CANT_HARM,
        /** 最近没和你交过手 */
        NO_FIGHT,
        /** 站在你没有权限的地方（领地、出生点） */
        NO_PERMISSION,
        /** 创造或旁观（旁观者不进终端名单，名单里只会是创造模式） */
        EXEMPT_MODE,
        /** 判定中（领地探测这一秒没排上，新来的先不压） */
        PENDING,
        /** 能压制，但电量不足、这一秒没压 */
        UNPOWERED;

        public static TargetState of(byte b) {
            TargetState[] values = values();
            return b >= 0 && b < values.length ? values[b] : PENDING;
        }
    }

    /**
     * 名单里的一行。
     *
     * @param name     玩家名
     * @param state    状态（{@link TargetState} 的序号）
     * @param distance 离穿戴者几格（0~255）
     * @param other    另一名压制他的穿戴者（没有时为空串）
     */
    public record Entry(String name, byte state, int distance, String other) {
        public Entry {
            name = clip(name);
            other = clip(other);
            distance = Math.max(0, Math.min(255, distance));
        }
    }

    /**
     * 「模块」页的一格。
     *
     * @param part     部位（0 头 1 胸 2 腿 3 靴）
     * @param slot     格子号
     * @param id       模块注册名
     * @param status   状态（{@code ModuleCache.Status} 的序号：生效、关着、缺电、冷却中、没穿齐、被关掉、同种重复、超出格数、认不出）
     * @param on       开着
     * @param cooldown 冷却还剩几刻（没在冷却为 0）
     */
    public record ModuleRow(byte part, byte slot, ResourceLocation id, byte status, boolean on, int cooldown) {
        public ModuleRow {
            cooldown = Math.max(0, cooldown);
        }
    }

    /**
     * 「模块」页。
     *
     * @param fullSet        穿齐了四件
     * @param requireFullSet 模块要不要穿齐四件才生效
     * @param slots          四件的模块槽（按头、胸、腿、靴；没穿能装模块的盔甲是 -1）
     * @param mruPerSecond   这一秒扣成的模块电费
     * @param rows           每一格（≤ {@value #MAX_MODULES}）
     */
    public record ModulePanel(boolean fullSet, boolean requireFullSet, byte[] slots, int mruPerSecond, List<ModuleRow> rows) {

        public static final ModulePanel EMPTY = new ModulePanel(false, true, new byte[]{-1, -1, -1, -1}, 0, List.of());

        public ModulePanel {
            slots = slots.length == 4 ? slots.clone() : Arrays.copyOf(slots, 4);
            mruPerSecond = Math.max(0, mruPerSecond);
            rows = List.copyOf(rows.size() > MAX_MODULES ? rows.subList(0, MAX_MODULES) : rows);
        }

        /** 这一件的模块槽（没穿为 -1） */
        public int slots(int part) {
            return part >= 0 && part < slots.length ? slots[part] : -1;
        }
    }

    public S2CComputerTerminalPacket {
        entries = List.copyOf(entries.size() > MAX_ENTRIES ? entries.subList(0, MAX_ENTRIES) : entries);
        mruPerSecond = Math.max(0, mruPerSecond);
        mruAvailable = Math.max(0L, mruAvailable);
        sealedOthers = Math.max(0, sealedOthers);
        modules = modules == null ? ModulePanel.EMPTY : modules;
    }

    /** 不带模块页（只带状态的包、展示厅） */
    public S2CComputerTerminalPacket(byte fieldState, byte availability, int mruPerSecond, long mruAvailable, int sealedOthers,
                                     boolean detail, List<Entry> entries) {
        this(fieldState, availability, mruPerSecond, mruAvailable, sealedOthers, detail, entries, ModulePanel.EMPTY);
    }

    /** 只带状态（状态变化时推给本人） */
    public static S2CComputerTerminalPacket state(FieldState state, byte availability, int mruPerSecond, long mruAvailable, int sealedOthers) {
        return new S2CComputerTerminalPacket((byte) state.ordinal(), availability, mruPerSecond, mruAvailable, sealedOthers, false, List.of());
    }

    public FieldState state() {
        return FieldState.of(fieldState);
    }

    private static String clip(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > NAME_MAX ? text.substring(0, NAME_MAX) : text;
    }

    public static void encode(S2CComputerTerminalPacket packet, FriendlyByteBuf buf) {
        buf.writeByte(packet.fieldState);
        buf.writeByte(packet.availability);
        buf.writeVarInt(packet.mruPerSecond);
        buf.writeVarLong(packet.mruAvailable);
        buf.writeVarInt(packet.sealedOthers);
        buf.writeBoolean(packet.detail);
        if (packet.detail) {
            buf.writeVarInt(packet.entries.size());
            for (Entry entry : packet.entries) {
                buf.writeUtf(entry.name(), NAME_MAX);
                buf.writeByte(entry.state());
                buf.writeByte(entry.distance());
                buf.writeUtf(entry.other(), NAME_MAX);
            }
            ModulePanel modules = packet.modules;
            buf.writeByte((modules.fullSet() ? 1 : 0) | (modules.requireFullSet() ? 2 : 0));
            for (int part = 0; part < 4; part++) {
                buf.writeByte(modules.slots(part));
            }
            buf.writeVarInt(modules.mruPerSecond());
            buf.writeVarInt(modules.rows().size());
            for (ModuleRow row : modules.rows()) {
                buf.writeByte(row.part());
                buf.writeByte(row.slot());
                buf.writeResourceLocation(row.id());
                buf.writeByte(row.status());
                buf.writeBoolean(row.on());
                buf.writeVarInt(row.cooldown());
            }
        }
    }

    public static S2CComputerTerminalPacket decode(FriendlyByteBuf buf) {
        byte state = buf.readByte();
        byte availability = buf.readByte();
        int perSecond = buf.readVarInt();
        long available = buf.readVarLong();
        int sealed = buf.readVarInt();
        boolean detail = buf.readBoolean();
        List<Entry> entries = new ArrayList<>();
        ModulePanel modules = ModulePanel.EMPTY;
        if (detail) {
            int count = buf.readVarInt();
            if (count < 0 || count > MAX_ENTRIES) {
                throw new IllegalArgumentException("Too many terminal entries: " + count);
            }
            for (int i = 0; i < count; i++) {
                String name = buf.readUtf(NAME_MAX);
                byte entryState = buf.readByte();
                int distance = buf.readUnsignedByte();
                String other = buf.readUtf(NAME_MAX);
                entries.add(new Entry(name, entryState, distance, other));
            }
            int flags = buf.readUnsignedByte();
            byte[] slots = new byte[4];
            for (int part = 0; part < 4; part++) {
                slots[part] = buf.readByte();
            }
            int moduleMru = buf.readVarInt();
            int rowCount = buf.readVarInt();
            if (rowCount < 0 || rowCount > MAX_MODULES) {
                throw new IllegalArgumentException("Too many terminal modules: " + rowCount);
            }
            List<ModuleRow> rows = new ArrayList<>(rowCount);
            for (int i = 0; i < rowCount; i++) {
                rows.add(new ModuleRow(buf.readByte(), buf.readByte(), buf.readResourceLocation(), buf.readByte(), buf.readBoolean(), buf.readVarInt()));
            }
            modules = new ModulePanel((flags & 1) != 0, (flags & 2) != 0, slots, moduleMru, rows);
        }
        return new S2CComputerTerminalPacket(state, availability, perSecond, available, sealed, detail, entries, modules);
    }
}
