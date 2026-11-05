package com.flameforge.essentialcraft5.item.module;

import com.flameforge.essentialcraft5.item.ComputerSealField;
import com.flameforge.essentialcraft5.network.C2SComputerTerminalPacket;
import com.flameforge.essentialcraft5.network.S2CComputerTerminalPacket.ModulePanel;
import com.flameforge.essentialcraft5.network.S2CComputerTerminalPacket.ModuleRow;
import com.flameforge.essentialcraft5.util.ECItemEntities;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 宅男终端「模块」页的服务端（batch10.md §4.3 第 6、7 条）：处理 {@link C2SComputerTerminalPacket#MODULE_TOGGLE} / {@link C2SComputerTerminalPacket#MODULE_REMOVE}，
 * 给终端数据包拼「模块」那一段（{@link #panel}）。终端包的总入口仍是 {@code ComputerSealField.handleTerminalPacket}。
 *
 * <h2>核对（不信包里的任何东西）</h2>
 * 不是旁观者；{@code part} 是 0~3，那个盔甲槽穿着能装模块的盔甲、部位对得上；{@code slot} 那一格确实有模块；开关还要模块能开关；
 * 两种动作合起来同一人 {@value #ACTION_MIN_INTERVAL} 刻内只认一次（只有做成了才记时间）。做成以后当场作废这名玩家的模块快照，
 * 并马上回一份带名单的终端数据（不等下一次刷新），界面立刻看到结果。
 * <p>
 * 包里还带着界面上那一行的模块注册名：这一格现在装的不是它（拆掉一块后面的往前挪，新名单到之前又点了一下；或者打开终端时还在用上次的名单），
 * 就是 {@link Result#STALE}——什么都不改、不记操作间隔，回一份新名单（同一人 {@value #ACTION_MIN_INTERVAL} 刻内最多回一次）。不然东西虽然会还回来，
 * 拆掉的却不是玩家点的那块。
 *
 * <h2>拆</h2>
 * 先从盔甲 NBT 里删掉这一格，同一刻 {@link ECItemEntities#giveOrDrop} 一块新的模块物品（背包满了掉在脚下；人死了 / 下线了直接生成掉落物）：
 * 模块只在 NBT 里存在一份，删了才给，不会复制。认不出的模块（物品已经不存在）只删、不给。
 */
public final class ModuleTerminal {

    /** 同一人两次模块操作的最短间隔（刻） */
    public static final int ACTION_MIN_INTERVAL = 5;

    /** 操作的结果（GameTest 断言用） */
    public enum Result {
        /** 做成了 */
        OK,
        /** 旁观者 */
        SPECTATOR,
        /** 5 刻内做过一次 */
        TOO_FAST,
        /** 部位不对（不是 0~3） */
        BAD_PART,
        /** 那个部位没穿能装模块的盔甲 */
        NOT_WORN,
        /** 那一格没有模块 */
        EMPTY_SLOT,
        /** 这块模块不能开关 */
        NOT_TOGGLEABLE,
        /** 这一格现在装的不是界面上那块（名单过期了） */
        STALE
    }

    private static final Map<Player, Long> LAST_ACTION = new WeakHashMap<>();
    /** 名单过期时回新名单的时间（限频，免得一直发过期的包换终端数据） */
    private static final Map<Player, Long> LAST_STALE_REPLY = new WeakHashMap<>();

    private ModuleTerminal() {
    }

    /** 部位序号 → 部位（0 头 1 胸 2 腿 3 靴）；越界为 null */
    @Nullable
    public static ArmorItem.Type part(int index) {
        ArmorItem.Type[] types = ArmorItem.Type.values();
        return index >= 0 && index < types.length ? types[index] : null;
    }

    /** 终端包里模块的两个动作（ComputerSealField.handleTerminalPacket 转过来）；别的动作返回 null、什么都不做 */
    @Nullable
    public static Result handle(ServerPlayer player, C2SComputerTerminalPacket packet) {
        if (packet.action() == C2SComputerTerminalPacket.MODULE_TOGGLE) {
            return toggle(player, packet.part(), packet.slot(), packet.module());
        }
        if (packet.action() == C2SComputerTerminalPacket.MODULE_REMOVE) {
            return remove(player, packet.part(), packet.slot(), packet.module());
        }
        return null;
    }

    /** 开 / 关一格，不核对模块注册名（服务端自己调、GameTest 用；玩家的包走 {@link #handle}） */
    public static Result toggle(ServerPlayer player, int partIndex, int slot) {
        return toggle(player, partIndex, slot, null);
    }

    /**
     * 开 / 关一格（见类注释）。
     *
     * @param expected 界面上这一格的模块注册名；null 不核对
     */
    public static Result toggle(ServerPlayer player, int partIndex, int slot, @Nullable String expected) {
        Target target = target(player, partIndex, slot, expected);
        if (target.result != null) {
            return target.result;
        }
        ArmorModules.Installed installed = installedAt(target.armor, slot);
        ModuleType type = installed == null ? null : installed.type();
        if (type == null || !type.toggleable()) {
            return Result.NOT_TOGGLEABLE;
        }
        if (!ArmorModules.setOn(target.armor, slot, !installed.on())) {
            return Result.NOT_TOGGLEABLE;
        }
        done(player);
        return Result.OK;
    }

    /** 拆下一格，不核对模块注册名（服务端自己调、GameTest 用；玩家的包走 {@link #handle}） */
    public static Result remove(ServerPlayer player, int partIndex, int slot) {
        return remove(player, partIndex, slot, null);
    }

    /**
     * 拆下一格（见类注释）。
     *
     * @param expected 界面上这一格的模块注册名；null 不核对
     */
    public static Result remove(ServerPlayer player, int partIndex, int slot, @Nullable String expected) {
        Target target = target(player, partIndex, slot, expected);
        if (target.result != null) {
            return target.result;
        }
        ResourceLocation id = ArmorModules.remove(target.armor, slot);
        if (id != null) {
            ModuleType type = ArmorModules.get(id);
            Item item = type != null ? type.item() : Items.AIR;
            if (item != Items.AIR) {
                ECItemEntities.giveOrDrop(player, new ItemStack(item));
            }
        }
        done(player);
        return Result.OK;
    }

    private record Target(@Nullable Result result, ItemStack armor) {
    }

    private static Target target(ServerPlayer player, int partIndex, int slot, @Nullable String expected) {
        if (player.isSpectator()) {
            return new Target(Result.SPECTATOR, ItemStack.EMPTY);
        }
        long time = player.level().getGameTime();
        Long last = LAST_ACTION.get(player);
        if (last != null && time - last >= 0 && time - last < ACTION_MIN_INTERVAL) {
            return new Target(Result.TOO_FAST, ItemStack.EMPTY);
        }
        ArmorItem.Type part = part(partIndex);
        if (part == null) {
            return new Target(Result.BAD_PART, ItemStack.EMPTY);
        }
        ItemStack armor = player.getItemBySlot(part.getSlot());
        if (!ModuleCache.isModularPiece(armor, part)) {
            return new Target(Result.NOT_WORN, ItemStack.EMPTY);
        }
        if (!ArmorModules.hasSlot(armor, slot)) {
            return new Target(Result.EMPTY_SLOT, ItemStack.EMPTY);
        }
        if (expected != null) {
            ArmorModules.Installed installed = installedAt(armor, slot);
            if (installed == null || !installed.id().toString().equals(expected)) {
                staleReply(player, time);
                return new Target(Result.STALE, ItemStack.EMPTY);
            }
        }
        return new Target(null, armor);
    }

    /** 名单过期：回一份新名单，同一人 {@value #ACTION_MIN_INTERVAL} 刻内最多一次 */
    private static void staleReply(ServerPlayer player, long time) {
        Long last = LAST_STALE_REPLY.get(player);
        if (last != null && time - last >= 0 && time - last < ACTION_MIN_INTERVAL) {
            return;
        }
        LAST_STALE_REPLY.put(player, time);
        ComputerSealField.sendTerminal(player);
    }

    @Nullable
    private static ArmorModules.Installed installedAt(ItemStack armor, int slot) {
        for (ArmorModules.Installed installed : ArmorModules.installed(armor)) {
            if (installed.slot() == slot) {
                return installed;
            }
        }
        return null;
    }

    private static void done(ServerPlayer player) {
        LAST_ACTION.put(player, player.level().getGameTime());
        ModuleCache.invalidate(player);
        ComputerSealField.sendTerminal(player);
    }

    // ------------------------------------------------------------------ 终端数据

    /**
     * 「模块」页那一段：四件的格数、每一格的状态。状态在快照的基础上再看电和冷却——
     * 生效的按秒耗电模块这一秒付不起写「缺电」，刚打开 / 刚装上、还没记过账的写「等待扣电」（{@link ModuleCache.Status#PENDING}，
     * 下一次记账最多 1 秒后到，这之前它还没起作用）；生效的模块在冷却中写「冷却中」（带剩余刻）；
     * 生效的、有一次性花费的模块身上的电不够一次写「缺电」（模块照样算生效，触发时自己会再查一遍）。
     */
    public static ModulePanel panel(ServerPlayer player) {
        ModuleCache.Snapshot snapshot = ModuleCache.get(player);
        byte[] slots = new byte[4];
        for (ArmorItem.Type part : ArmorItem.Type.values()) {
            slots[part.ordinal()] = (byte) snapshot.slotCount(part);
        }
        List<ModuleRow> rows = new ArrayList<>();
        long available = -1L;
        for (ModuleCache.Slot slot : snapshot.slots()) {
            ModuleCache.Status status = slot.status();
            ModuleType type = slot.type();
            int cooldown = 0;
            if (status == ModuleCache.Status.ACTIVE && type != null) {
                cooldown = ModuleCooldowns.remaining(player, type);
                if (type.mruPerSecond() > 0 && !ModuleTicker.powered(player, type)) {
                    status = ModuleTicker.unpaid(player, type) ? ModuleCache.Status.NO_POWER : ModuleCache.Status.PENDING;
                } else if (cooldown > 0) {
                    status = ModuleCache.Status.COOLDOWN;
                } else if (type.mruPerUse() > 0) {
                    if (available < 0L) {
                        available = ModuleTicker.availableMru(player);
                    }
                    if (available < type.mruPerUse()) {
                        status = ModuleCache.Status.NO_POWER;
                    }
                }
            } else if (type != null) {
                cooldown = ModuleCooldowns.remaining(player, type);
            }
            rows.add(new ModuleRow((byte) slot.part().ordinal(), (byte) slot.index(), slot.id(), (byte) status.ordinal(), slot.on(), cooldown));
        }
        return new ModulePanel(snapshot.fullSet(), snapshot.requireFullSet(), slots, ModuleTicker.paidPerSecond(player), rows);
    }

    /** GameTest 用：清掉这名玩家的操作间隔记录 */
    public static void forgetForTest(Player player) {
        LAST_ACTION.remove(player);
        LAST_STALE_REPLY.remove(player);
    }

    static void clear() {
        LAST_ACTION.clear();
        LAST_STALE_REPLY.clear();
    }
}
