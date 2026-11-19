package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.registry.ECBuilder;
import com.flameforge.essentialcraft5.util.ECItemEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 创世神之杖的材料从哪取（tools/plan/builder_upgrade.md §1.8）。每格按这个顺序找：
 * <ol>
 *   <li>背包 36 格，再副手：就是这种物品、没 NBT（{@link #isPlainMaterial}）。副手排在后面，副手那叠留着，下一单还按它选材料；</li>
 *   <li>随身容器（{@code allow_container_items} 开着时）：背包 36 格 → 副手 → 4 个盔甲位 → 饰品栏里<b>数量为 1</b>、
 *       有 Forge 物品处理能力（{@code ForgeCapabilities.ITEM_HANDLER}）、不是创世神之杖、不在 {@code essentialcraft5:builder_container_blacklist} 里的物品；
 *       只看一层，不往容器里的容器里钻。不认任何具体的模组，只认这个通用能力。</li>
 * </ol>
 * 只在服务端、同一段同步代码里用：{@link #find} 找到位置（只读），{@link #take} 取 1 个，放置被拒就 {@link #giveBack} 原样塞回同一处。
 * 容器取出的东西不是 1 个这种没 NBT 的物品（处理器自己的规则）时原样塞回、当没有。
 * 每种材料上次命中的位置缓存在 {@link Cursor} 里，只在同一刻里先看它：跨刻重新列一遍容器（容器可能被扔掉、挪走）。
 */
public final class BuilderMaterials {

    private BuilderMaterials() {
    }

    /** 能当材料：就是那种物品、没有 NBT（改过名、装了东西的不碰） */
    public static boolean isPlainMaterial(ItemStack stack, @Nullable Item material) {
        if (material == null || stack.isEmpty() || !stack.is(material)) {
            return false;
        }
        CompoundTag tag = stack.getTag();
        return tag == null || tag.isEmpty();
    }

    /** 一处材料：背包格（slot 0~35 背包、36 副手），或某个容器处理器的某一格 */
    public static final class Location {
        /** 背包格；容器时为 -1 */
        final int slot;
        /** 容器物品本身（核对它还在不在用） */
        @Nullable
        final ItemStack container;
        @Nullable
        final IItemHandler handler;
        final int handlerSlot;
        /** 背包格里当时那一叠（放回时核对还是不是它） */
        @Nullable
        final ItemStack source;

        private Location(int slot, @Nullable ItemStack container, @Nullable IItemHandler handler, int handlerSlot, @Nullable ItemStack source) {
            this.slot = slot;
            this.container = container;
            this.handler = handler;
            this.handlerSlot = handlerSlot;
            this.source = source;
        }

        public boolean fromContainer() {
            return handler != null;
        }
    }

    /** 每个任务一个：每种材料上次命中的位置（混铺时几种轮着取，各记各的）和那一刻 */
    public static final class Cursor {
        /** 材料 → 上次命中的位置；只在 {@link #tick} 那一刻里有效 */
        final Map<Item, Location> recent = new IdentityHashMap<>(4);
        /**
         * 这一刻里已经确认身上没有的材料（混铺时某一种用完后，轮到它的格子不再每格把背包和容器重扫一遍）。
         * 同一刻里让它重新出现的只有事件监听器往背包里塞东西，等下一刻再认
         */
        final Set<Item> missing = Collections.newSetFromMap(new IdentityHashMap<>());
        long tick = Long.MIN_VALUE;
        /** 本刻列出的容器（同一刻里复用） */
        @Nullable
        List<Container> containers;
        long containersTick = Long.MIN_VALUE;
        /** 取不出东西（拒绝、给错东西）的处理器，这个任务以后不再看 */
        final Set<IItemHandler> skipped = Collections.newSetFromMap(new IdentityHashMap<>());

        /** 这处取不出来：容器的处理器记下不再看，缓存作废 */
        public void skip(@Nullable Location location) {
            if (location != null && location.handler != null) {
                skipped.add(location.handler);
            }
            recent.clear();
            missing.clear();
        }
    }

    record Container(ItemStack stack, IItemHandler handler) {
    }

    /** 背包格的编号：0~35 是背包，36 是副手 */
    static ItemStack slotStack(Inventory inventory, int slot) {
        return slot < inventory.items.size() ? inventory.items.get(slot) : inventory.offhand.get(0);
    }

    /** 找一处可用的材料（只读，不取）；没有返回 null */
    @Nullable
    public static Location find(ServerPlayer player, @Nullable Item material, Cursor cursor, @Nullable ItemStack wand, long tick) {
        if (material == null) {
            return null;
        }
        Inventory inventory = player.getInventory();
        if (cursor.tick != tick) {
            cursor.recent.clear();
            cursor.missing.clear();
            cursor.tick = tick;
        }
        if (cursor.missing.contains(material)) {
            return null;
        }
        Location last = cursor.recent.get(material);
        if (last != null && stillThere(last, inventory, material)) {
            return last;
        }
        for (int slot = 0; slot <= inventory.items.size(); slot++) {
            ItemStack stack = slotStack(inventory, slot);
            if (isPlainMaterial(stack, material)) {
                return remember(cursor, material, new Location(slot, null, null, -1, stack));
            }
        }
        if (!ECBuilder.Config.allowContainerItems() || !containersUsable(player)) {
            cursor.missing.add(material);
            return null;
        }
        for (Container container : containers(player, cursor, wand, tick)) {
            IItemHandler handler = container.handler();
            if (cursor.skipped.contains(handler)) {
                continue;
            }
            int slots = handler.getSlots();
            for (int i = 0; i < slots; i++) {
                if (isPlainMaterial(handler.getStackInSlot(i), material)) {
                    return remember(cursor, material, new Location(-1, container.stack(), handler, i, null));
                }
            }
        }
        cursor.missing.add(material);
        return null;
    }

    /**
     * 这时候能不能动随身容器：玩家开着别的界面（{@code containerMenu} 不是自己的背包）时不动。有的背包类容器的界面拿着内容的一份副本，
     * 关界面时整份写回物品，界面开着时经物品处理能力取走 / 塞进的东西会被盖掉（取料变成复制、回收变成丢失）。
     * 这时取料只看背包，回收照旧进背包；关了界面下一刻就恢复。
     */
    static boolean containersUsable(Player player) {
        return player.containerMenu == player.inventoryMenu;
    }

    private static Location remember(Cursor cursor, Item material, Location location) {
        cursor.recent.put(material, location);
        return location;
    }

    private static boolean stillThere(Location location, Inventory inventory, Item material) {
        if (location.handler == null) {
            ItemStack stack = slotStack(inventory, location.slot);
            return stack == location.source && isPlainMaterial(stack, material);
        }
        return location.container != null && location.container.getCount() == 1
                && location.handlerSlot < location.handler.getSlots()
                && isPlainMaterial(location.handler.getStackInSlot(location.handlerSlot), material);
    }

    /**
     * 从 location 取 1 个。背包格直接 split；容器走 {@code extractItem(…, 1, false)}，拿到的不是 1 个这种没 NBT 的物品就原样塞回、返回空。
     */
    public static ItemStack take(ServerPlayer player, Location location, Item material) {
        if (location.handler == null) {
            ItemStack stack = slotStack(player.getInventory(), location.slot);
            if (stack != location.source || !isPlainMaterial(stack, material)) {
                return ItemStack.EMPTY;
            }
            return stack.split(1);
        }
        ItemStack taken = location.handler.extractItem(location.handlerSlot, 1, false);
        if (taken.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (taken.getCount() != 1 || !isPlainMaterial(taken, material)) {
            giveBack(player, location, taken);
            return ItemStack.EMPTY;
        }
        return taken;
    }

    /**
     * 放置被拒：原样放回。背包格那一格还是原来那叠、没满就加回去；容器塞回同一处理器同一格；放不回的交给 {@code giveOrDrop}
     * （活着进背包、死了掉在原地）。放置事件里玩家被踢下线的（背包已经存过盘）直接掉在原地，不塞回去。
     */
    public static void giveBack(ServerPlayer player, Location location, ItemStack taken) {
        if (taken.isEmpty()) {
            return;
        }
        if (player.hasDisconnected() || player.isRemoved()) {
            ECItemEntities.giveOrDrop(player, taken);
            return;
        }
        if (location.handler == null) {
            ItemStack stack = slotStack(player.getInventory(), location.slot);
            if (stack == location.source && !stack.isEmpty() && ItemStack.isSameItemSameTags(stack, taken)
                    && stack.getCount() + taken.getCount() <= stack.getMaxStackSize()) {
                stack.grow(taken.getCount());
                return;
            }
            ECItemEntities.giveOrDrop(player, taken);
            return;
        }
        ItemStack rest = location.handler.insertItem(location.handlerSlot, taken, false);
        if (!rest.isEmpty()) {
            ECItemEntities.giveOrDrop(player, rest);
        }
    }

    /**
     * 身上能当材料的数量：[0] 背包和副手，[1] 随身容器里。
     * 容器里的料要取得出来才算：每只容器对第一格匹配的料模拟取 1 个（{@code extractItem(…, 1, true)}），取不出、或给的不是 1 个这种没 NBT 的物品
     * （容器自己的过滤规则不让取）就整只容器不算，准星下的「持有」和开工前「料够不够」都按这个数。
     * 只试第一格：同一只容器里各格的规则一般一样，逐格试开销要翻几十倍；规则按格不同的容器以施工时真取为准（取不出时这只容器这一单不再看，{@link Cursor#skip}）。
     */
    public static long[] count(Player player, @Nullable Item material, @Nullable ItemStack wand) {
        long[] result = new long[2];
        if (material == null) {
            return result;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot <= inventory.items.size(); slot++) {
            ItemStack stack = slotStack(inventory, slot);
            if (isPlainMaterial(stack, material)) {
                result[0] += stack.getCount();
            }
        }
        if (ECBuilder.Config.allowContainerItems()) {
            for (Container container : listContainers(player, wand)) {
                result[1] += countTakeable(container.handler(), material);
            }
        }
        return result;
    }

    /** 一只容器里能当材料、取得出来的数量；第一格匹配的料模拟取不出就整只算 0（见 {@link #count}） */
    static long countTakeable(IItemHandler handler, Item material) {
        long sum = 0;
        boolean probed = false;
        int slots = handler.getSlots();
        for (int i = 0; i < slots; i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!isPlainMaterial(stack, material)) {
                continue;
            }
            if (!probed) {
                probed = true;
                ItemStack trial = handler.extractItem(i, 1, true);
                if (trial.getCount() != 1 || !isPlainMaterial(trial, material)) {
                    return 0;
                }
            }
            sum += stack.getCount();
        }
        return sum;
    }

    /** 面板「从身上挑」的一行：一种能当材料的方块物品，背包和副手里有多少、随身容器里有多少 */
    public record Stock(Item item, long body, long box) {
        public long total() {
            return body + box;
        }
    }

    /** {@link #stocks} 的结果：清单、是不是截掉了一部分、容器这次看没看（开着别的界面时不看） */
    public record Stocks(List<Stock> list, boolean truncated, boolean containersSeen) {
    }

    /**
     * 身上能当选定方块的材料（面板「从身上挑」，服务端按请求算）：背包 36 格和副手、随身容器（只看一层，规矩同 {@link #count}：
     * 第一格模拟取不出就整只容器不算这种）里没 NBT 的方块物品，方块要能选定、放下去就是它自己（{@link BuilderMix#usableBlock}）。
     * 不受「容器取料开关」以外的设置影响；开着别的界面时不看容器（{@link #containersUsable}），清单照样给背包里的。
     * 按总数从多到少、同数按注册名排，最多 limit 种。
     */
    public static Stocks stocks(Player player, @Nullable ItemStack wand, int limit) {
        Map<Item, long[]> counts = new LinkedHashMap<>();
        Map<Item, Boolean> usable = new IdentityHashMap<>();
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot <= inventory.items.size(); slot++) {
            ItemStack stack = slotStack(inventory, slot);
            if (stockable(stack, usable)) {
                counts.computeIfAbsent(stack.getItem(), item -> new long[2])[0] += stack.getCount();
            }
        }
        boolean seen = ECBuilder.Config.allowContainerItems() && containersUsable(player);
        if (seen) {
            for (Container container : listContainers(player, wand)) {
                IItemHandler handler = container.handler();
                Map<Item, Long> inThis = new IdentityHashMap<>();
                Set<Item> refused = Collections.newSetFromMap(new IdentityHashMap<>());
                int slots = handler.getSlots();
                for (int i = 0; i < slots; i++) {
                    ItemStack stack = handler.getStackInSlot(i);
                    if (!stockable(stack, usable)) {
                        continue;
                    }
                    Item item = stack.getItem();
                    if (refused.contains(item)) {
                        continue;
                    }
                    if (!inThis.containsKey(item)) {
                        ItemStack trial = handler.extractItem(i, 1, true);
                        if (trial.getCount() != 1 || !isPlainMaterial(trial, item)) {
                            refused.add(item);
                            continue;
                        }
                    }
                    inThis.merge(item, (long) stack.getCount(), Long::sum);
                }
                inThis.forEach((item, n) -> counts.computeIfAbsent(item, key -> new long[2])[1] += n);
            }
        }
        List<Stock> list = new ArrayList<>(counts.size());
        counts.forEach((item, n) -> list.add(new Stock(item, n[0], n[1])));
        list.sort(Comparator.comparingLong(Stock::total).reversed()
                .thenComparing(stock -> String.valueOf(ForgeRegistries.ITEMS.getKey(stock.item()))));
        boolean truncated = list.size() > limit;
        return new Stocks(truncated ? List.copyOf(list.subList(0, limit)) : List.copyOf(list), truncated, seen);
    }

    /** 能进「从身上挑」清单：没 NBT 的方块物品，它的方块能选定、放下去就是它自己 */
    private static boolean stockable(ItemStack stack, Map<Item, Boolean> cache) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        CompoundTag tag = stack.getTag();
        if (tag != null && !tag.isEmpty()) {
            return false;
        }
        return cache.computeIfAbsent(stack.getItem(), item -> {
            Block block = blockItem.getBlock();
            return BuilderMix.usableBlock(ForgeRegistries.BLOCKS.getKey(block)) == block;
        });
    }

    private static List<Container> containers(ServerPlayer player, Cursor cursor, @Nullable ItemStack wand, long tick) {
        if (cursor.containers == null || cursor.containersTick != tick) {
            cursor.containers = listContainers(player, wand);
            cursor.containersTick = tick;
        }
        return cursor.containers;
    }

    /** 身上的随身容器，按 背包 36 格 → 副手 → 盔甲位 → 饰品栏 */
    static List<Container> listContainers(Player player, @Nullable ItemStack wand) {
        List<Container> list = new ArrayList<>();
        Inventory inventory = player.getInventory();
        for (ItemStack stack : inventory.items) {
            addContainer(list, stack, wand);
        }
        addContainer(list, inventory.offhand.get(0), wand);
        for (ItemStack stack : inventory.armor) {
            addContainer(list, stack, wand);
        }
        CuriosCompat.forEachEquipped(player, stack -> addContainer(list, stack, wand));
        return list;
    }

    private static void addContainer(List<Container> list, ItemStack stack, @Nullable ItemStack wand) {
        if (stack.isEmpty() || stack.getCount() != 1 || stack == wand || stack.getItem() instanceof MagicalBuilderItem
                || stack.is(ECBuilder.BUILDER_CONTAINER_BLACKLIST)) {
            return;
        }
        stack.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
            for (Container existing : list) {
                if (existing.handler() == handler) {
                    return;   // 同一个处理器（有的物品在两处都报同一份内容）只算一次
                }
            }
            list.add(new Container(stack, handler));
        });
    }
}
