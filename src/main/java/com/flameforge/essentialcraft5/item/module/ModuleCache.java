package com.flameforge.essentialcraft5.item.module;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 每名玩家身上模块的快照（batch10.md §4.3 第 3 条），写法照 {@code compat/CuriosCompat}：两端各一份 {@link WeakHashMap}，热事件里只问 {@link #active}。
 *
 * <h2>什么时候重建</h2>
 * 拿快照时发现下面任一条就当场重建：四个盔甲槽里的物品换了对象（穿脱、换装都会换）、超过 {@value #MAX_AGE} 刻、配置重载或测试覆盖值变了。
 * 另外 {@link ModuleEvents} 在装备变化（{@code LivingEquipmentChangeEvent}，NBT 变了也发）、登录、重生、换维度时作废，
 * 终端开关 / 拆模块（就地改 NBT、物品对象不变）时 {@link ModuleTerminal} 当场作废。
 *
 * <h2>一格的状态（不看电和冷却）</h2>
 * 按头 → 胸 → 腿 → 靴、格子号的顺序一格格判，前一条成立就不看后面：认不出的模块或部位不对（NBT 被改过）→ {@link Status#UNKNOWN}；
 * 排在这件的格数以外 → {@link Status#OVERFLOW}；被 {@code general.computer_modules.disabled} 关掉 → {@link Status#DISABLED}；
 * 终端里关着 → {@link Status#OFF}；{@code require_full_set} 开着而没穿齐四件 → {@link Status#INCOMPLETE}；
 * 同种模块前面已经有一块生效 → {@link Status#DUPLICATE}（同种全套只算一块）；都不是 → {@link Status#ACTIVE}。
 *
 * <h2>{@link #active} 还看电</h2>
 * 每秒耗电的模块（{@link ModuleType#mruPerSecond()} &gt; 0）还要 {@link ModuleTicker} 这一秒扣成了才算；客户端不知道扣没扣成，按格子状态算。
 * 冷却不在这里看：冷却中的模块照样「生效」，触发前由模块自己问 {@link ModuleCooldowns}（终端把冷却单独显示）。
 */
public final class ModuleCache {

    /** 快照最长用多少刻 */
    public static final int MAX_AGE = 20;

    /** 一格的状态。终端包按序号传，只往末尾加 */
    public enum Status {
        /** 生效（按秒耗电的还要这一秒扣成） */
        ACTIVE,
        /** 终端里关着 */
        OFF,
        /** 缺电（终端显示用；快照里不出现，见 {@link ModuleTerminal}） */
        NO_POWER,
        /** 冷却中（终端显示用；快照里不出现） */
        COOLDOWN,
        /** 没穿齐四件 */
        INCOMPLETE,
        /** 被关掉了（{@code general.computer_modules.disabled}） */
        DISABLED,
        /** 同种模块已经有一块在生效 */
        DUPLICATE,
        /** 排在这件的格数以外（格数改小了） */
        OVERFLOW,
        /** 认不出的模块（或者装在了它不能装的部位上） */
        UNKNOWN,
        /** 等待扣电：按秒耗电的模块刚打开 / 刚装上，还没记过账（终端显示用；快照里不出现，见 {@link ModuleTerminal}） */
        PENDING;

        public static Status of(byte b) {
            Status[] values = values();
            return b >= 0 && b < values.length ? values[b] : UNKNOWN;
        }
    }

    /**
     * 快照里的一格。
     *
     * @param part   部位
     * @param index  格子号（这件 NBT 列表里的下标）
     * @param id     模块注册名
     * @param type   模块（认不出时 null）
     * @param on     开着
     * @param status 状态（不看电和冷却）
     */
    public record Slot(ArmorItem.Type part, int index, ResourceLocation id, @Nullable ModuleType type, boolean on, Status status) {
    }

    /**
     * 一名玩家的快照。
     *
     * @param builtAt        建于哪一刻（世界的 gameTime）
     * @param generation     建时的配置代数
     * @param pieces         建时四个盔甲槽里的物品（按头、胸、腿、靴；只比对象，不比内容）
     * @param fullSet        四件都是能装模块的盔甲、各在自己的部位上
     * @param requireFullSet 建时的 {@code require_full_set}
     * @param slotCounts     四件各有几个模块槽（按头、胸、腿、靴；这一格没穿能装模块的盔甲时 -1）
     * @param slots          所有格（按头 → 靴、格子号）
     * @param eligible       状态是 {@link Status#ACTIVE} 的模块（同种只有一个）
     */
    public record Snapshot(long builtAt, int generation, ItemStack[] pieces, boolean fullSet, boolean requireFullSet, int[] slotCounts,
                           List<Slot> slots, Set<ModuleType> eligible) {

        /** 这个部位穿着能装模块的盔甲 */
        public boolean worn(ArmorItem.Type part) {
            return slotCounts[part.ordinal()] >= 0;
        }

        /** 这个部位的格数（没穿为 -1） */
        public int slotCount(ArmorItem.Type part) {
            return slotCounts[part.ordinal()];
        }

        /** 一块模块都没装 */
        public boolean isEmpty() {
            return slots.isEmpty();
        }
    }

    /** 服务端线程和客户端线程各用一份，不共享（单人游戏两边同时在跑） */
    private static final Map<Player, Snapshot> SERVER_CACHE = new WeakHashMap<>();
    private static final Map<Player, Snapshot> CLIENT_CACHE = new WeakHashMap<>();

    private ModuleCache() {
    }

    // ------------------------------------------------------------------ 查

    /**
     * 这种模块在这名玩家身上生效（见类注释：格子状态 ACTIVE，按秒耗电的还要这一秒扣成；冷却不看）。热事件里只用它。
     *
     * @param player 玩家（两端都行；客户端不看电）
     * @param type   模块
     */
    public static boolean active(Player player, ModuleType type) {
        if (!get(player).eligible().contains(type)) {
            return false;
        }
        if (player.level().isClientSide || type.mruPerSecond() <= 0) {
            return true;
        }
        return ModuleTicker.powered(player, type);
    }

    /** 格子状态是 ACTIVE（不看电） */
    public static boolean eligible(Player player, ModuleType type) {
        return get(player).eligible().contains(type);
    }

    /** 身上生效的模块有几种（{@link #active} 为真的） */
    public static int activeCount(Player player) {
        int n = 0;
        for (ModuleType type : get(player).eligible()) {
            if (active(player, type)) {
                n++;
            }
        }
        return n;
    }

    /** 快照（过期了当场重建） */
    public static Snapshot get(Player player) {
        Map<Player, Snapshot> cache = player.level().isClientSide ? CLIENT_CACHE : SERVER_CACHE;
        long now = player.level().getGameTime();
        Snapshot snapshot = cache.get(player);
        if (snapshot == null || !valid(snapshot, player, now)) {
            snapshot = build(player, now);
            cache.put(player, snapshot);
        }
        return snapshot;
    }

    /** 让这名玩家这一端的快照作废 */
    public static void invalidate(Player player) {
        (player.level().isClientSide ? CLIENT_CACHE : SERVER_CACHE).remove(player);
    }

    /** 服务端停了：清掉服务端那份 */
    static void clearServer() {
        SERVER_CACHE.clear();
    }

    private static boolean valid(Snapshot snapshot, Player player, long now) {
        if (snapshot.generation() != ModuleConfig.generation() || now < snapshot.builtAt() || now - snapshot.builtAt() > MAX_AGE) {
            return false;
        }
        ArmorItem.Type[] parts = ArmorItem.Type.values();
        for (int i = 0; i < parts.length; i++) {
            if (snapshot.pieces()[i] != player.getItemBySlot(parts[i].getSlot())) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ 建

    /** 现建一份（不进缓存；GameTest 和 {@link #get} 用） */
    public static Snapshot build(Player player, long now) {
        ModuleConfig.Values cfg = ModuleConfig.values();
        int generation = ModuleConfig.generation();
        ArmorItem.Type[] parts = ArmorItem.Type.values();
        ItemStack[] pieces = new ItemStack[parts.length];
        int[] counts = new int[parts.length];
        Arrays.fill(counts, -1);
        boolean fullSet = true;
        boolean any = false;
        for (int i = 0; i < parts.length; i++) {
            ItemStack stack = player.getItemBySlot(parts[i].getSlot());
            pieces[i] = stack;
            if (isModularPiece(stack, parts[i])) {
                counts[i] = ArmorModules.slots(stack);
                any |= ArmorModules.count(stack) > 0;
            } else {
                fullSet = false;
            }
        }
        if (!any) {
            return new Snapshot(now, generation, pieces, fullSet, cfg.requireFullSet(), counts, List.of(), Set.of());
        }
        List<Slot> slots = new ArrayList<>();
        Set<ModuleType> eligible = new LinkedHashSet<>();
        for (int i = 0; i < parts.length; i++) {
            if (counts[i] < 0) {
                continue;
            }
            ArmorItem.Type part = parts[i];
            for (ArmorModules.Installed installed : ArmorModules.installed(pieces[i])) {
                ModuleType type = installed.type();
                Status status;
                if (type == null || !type.fits(part)) {
                    status = Status.UNKNOWN;
                } else if (installed.slot() >= counts[i]) {
                    status = Status.OVERFLOW;
                } else if (cfg.isDisabled(type.id())) {
                    status = Status.DISABLED;
                } else if (!installed.on()) {
                    status = Status.OFF;
                } else if (cfg.requireFullSet() && !fullSet) {
                    status = Status.INCOMPLETE;
                } else if (eligible.contains(type)) {
                    status = Status.DUPLICATE;
                } else {
                    status = Status.ACTIVE;
                    eligible.add(type);
                }
                slots.add(new Slot(part, installed.slot(), installed.id(), type, installed.on(), status));
            }
        }
        return new Snapshot(now, generation, pieces, fullSet, cfg.requireFullSet(), counts, Collections.unmodifiableList(slots),
                Collections.unmodifiableSet(eligible));
    }

    /** 这一格穿的是能装模块的盔甲、部位对得上 */
    public static boolean isModularPiece(ItemStack stack, ArmorItem.Type part) {
        return stack.getItem() instanceof IModularArmor && stack.getItem() instanceof ArmorItem armor && armor.getType() == part;
    }
}
