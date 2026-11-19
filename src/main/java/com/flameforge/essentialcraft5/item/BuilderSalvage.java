package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.registry.ECBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 回收模块（tools/plan/builder_upgrade.md §2.2）：清除、全部替换拆下来的掉落先塞进身上的背包类随身容器，塞不下的照旧进背包、再塞不下掉在脚下；
 * 丢弃清单里的物品拆了直接不要。只在服务端用，没装回收（或一阶装不上）时一切照旧。
 * <ul>
 *   <li>掉落只来自那一格真实破坏算出的战利品（{@code ECProtection.harvestGuarded} 的返回值，保护规则不变）；这里只是换个去处，不多也不少（丢弃清单里的除外）。</li>
 *   <li>容器和取料同一套：{@link BuilderMaterials#listContainers}（背包 36 格 → 副手 → 盔甲位 → 饰品栏里数量为 1、有 Forge 物品处理能力、
 *       不是创世神之杖、不在 {@code builder_container_blacklist} 里的，只看一层）。不受 {@code allow_container_items} 管（那一项只管取料）；
 *       服主不想让某种容器收东西就把它放进 {@code builder_container_blacklist}。</li>
 *   <li>每个任务每刻只列一次容器：一刻里拆下的先按「同物品同 NBT」合并，再逐个 {@code insertItem}；塞到一半的余量照常往下走。</li>
 *   <li>玩家已经死了、下线了：不碰容器，全部交给 {@code giveOrDrop}（掉在原地）；开着别的界面时也不碰（{@link BuilderMaterials#containersUsable}），照旧进背包。</li>
 *   <li>塞的时候先叠到已有的同种上、再占空格；某只容器的处理器抛异常或返回的余量不像话，这一刻不再往它里面塞，那一份照旧往下走。</li>
 * </ul>
 * NBT {@code builder_salvage_discard: ["minecraft:cobblestone", …]}（物品注册名），读的时候只认得到的、不重复的前
 * {@code general.builder.modules.salvage.discard_max} 个。
 */
public final class BuilderSalvage {

    /** NBT 键：丢弃清单 */
    public static final String DISCARD_TAG = "builder_salvage_discard";
    /** 丢弃清单最多几项（配置 discard_max 的上限，也是包里能带的上限） */
    public static final int HARD_MAX_DISCARD = 64;
    /** 每人记最近拆下过的几种物品（面板的丢弃清单从这里挑） */
    public static final int RECENT_LIMIT = 16;

    /** 玩家 UUID → 回收生效时最近拆下过的物品（新的在前，不重复）。只在内存里，下线清掉 */
    private static final Map<UUID, Deque<Item>> RECENT = new HashMap<>();

    private BuilderSalvage() {
    }

    /** 记下这一格拆下的物品（回收生效的任务调；丢弃清单里的也记，面板上能看到、能移出清单） */
    static void noteRecent(UUID player, List<ItemStack> harvested) {
        if (harvested.isEmpty()) {
            return;
        }
        Deque<Item> recent = RECENT.computeIfAbsent(player, id -> new ArrayDeque<>(RECENT_LIMIT + 1));
        for (ItemStack stack : harvested) {
            if (stack.isEmpty()) {
                continue;
            }
            Item item = stack.getItem();
            if (recent.peekFirst() == item) {
                continue;
            }
            recent.remove(item);
            recent.addFirst(item);
            while (recent.size() > RECENT_LIMIT) {
                recent.removeLast();
            }
        }
    }

    /** 最近拆下过的物品（新的在前）；没有返回空表 */
    public static List<Item> recent(UUID player) {
        Deque<Item> recent = RECENT.get(player);
        return recent == null ? List.of() : List.copyOf(recent);
    }

    static void forget(UUID player) {
        RECENT.remove(player);
    }

    static void clearAll() {
        RECENT.clear();
    }

    /** 回收生效没有（模块生效：二阶起） */
    public static boolean active(ItemStack wand) {
        return BuilderModules.has(wand, BuilderModules.Kind.SALVAGE);
    }

    /** 丢弃清单（筛过、按存的顺序）；没有返回空表。不看模块装没装 */
    public static List<Item> discardList(ItemStack wand) {
        CompoundTag tag = wand.getTag();
        if (tag == null || !tag.contains(DISCARD_TAG, Tag.TAG_LIST)) {
            return List.of();
        }
        ListTag list = tag.getList(DISCARD_TAG, Tag.TAG_STRING);
        int max = ECBuilder.Config.salvageDiscardMax();
        Set<Item> out = new LinkedHashSet<>();
        int scan = Math.min(list.size(), HARD_MAX_DISCARD * 2);   // 手改 NBT 塞了一长串没用的项时也只看这么多
        for (int i = 0; i < scan && out.size() < max; i++) {
            Item item = knownItem(ResourceLocation.tryParse(list.getString(i)));
            if (item != null) {
                out.add(item);
            }
        }
        return List.copyOf(out);
    }

    /** 写丢弃清单（就地改）；空表去掉整个键 */
    public static void writeDiscard(ItemStack wand, List<Item> items) {
        if (items.isEmpty()) {
            CompoundTag tag = wand.getTag();
            if (tag != null) {
                tag.remove(DISCARD_TAG);
            }
            return;
        }
        ListTag list = new ListTag();
        for (Item item : items) {
            list.add(StringTag.valueOf(String.valueOf(ForgeRegistries.ITEMS.getKey(item))));
        }
        wand.getOrCreateTag().put(DISCARD_TAG, list);
    }

    /** 认得的物品（不是空气）；否则 null */
    @Nullable
    public static Item knownItem(@Nullable ResourceLocation id) {
        if (id == null || !ForgeRegistries.ITEMS.containsKey(id)) {
            return null;
        }
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item == null || item == Items.AIR ? null : item;
    }

    /** 开工时定下的这一单的丢弃清单（没装回收是空集） */
    static Set<Item> discardSet(ItemStack wand) {
        if (!active(wand)) {
            return Set.of();
        }
        List<Item> list = discardList(wand);
        return list.isEmpty() ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(list));
    }

    /**
     * 一刻里拆下的东西先塞随身容器：返回塞不下的（交给调用方照旧 {@code giveOrDrop}）。drops 按「同物品同 NBT」合并后再塞。
     * 处理器返回的余量不像话（换了物品、比塞进去的还多）时当这一格没塞进去，不按它的数。
     */
    static List<ItemStack> stash(ServerPlayer player, @Nullable ItemStack wand, List<ItemStack> drops) {
        if (drops.isEmpty() || !player.isAlive() || player.isRemoved() || player.hasDisconnected()
                || !BuilderMaterials.containersUsable(player)) {
            return drops;
        }
        List<BuilderMaterials.Container> containers = BuilderMaterials.listContainers(player, wand);
        if (containers.isEmpty()) {
            return drops;
        }
        List<ItemStack> rest = new ArrayList<>();
        Set<IItemHandler> broken = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ItemStack stack : merge(drops)) {
            ItemStack left = stack;
            for (BuilderMaterials.Container container : containers) {
                IItemHandler handler = container.handler();
                if (broken.contains(handler)) {
                    continue;
                }
                ItemStack after = insert(handler, left);
                if (after == null) {
                    broken.add(handler);   // 这只容器不按规矩来：这一刻不再往里塞，left 照旧往下走
                    continue;
                }
                left = after;
                if (left.isEmpty()) {
                    break;
                }
            }
            if (!left.isEmpty()) {
                rest.add(left);
            }
        }
        return rest;
    }

    /**
     * 往一只容器里塞（先叠到已有的同种上、再占空格，同 {@code ItemHandlerHelper.insertItemStacked}），返回剩下的。
     * 处理器返回的余量不像话（换了物品、比塞进去的还多）或者抛异常时返回 null：调用方当这只容器没收、不再用它
     * （不按它的数，宁可让那一份照旧进背包，也不按一个不守规矩的处理器多算或少算）。
     */
    @Nullable
    private static ItemStack insert(IItemHandler handler, ItemStack stack) {
        ItemStack returned;
        try {
            returned = ItemHandlerHelper.insertItemStacked(handler, stack.copy(), false);
        } catch (RuntimeException e) {
            return null;
        }
        if (returned.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (returned.getCount() > stack.getCount() || !ItemStack.isSameItemSameTags(returned, stack)) {
            return null;
        }
        return returned.copy();
    }

    /** 按「同物品同 NBT」合并成整组（数量不变） */
    static List<ItemStack> merge(List<ItemStack> drops) {
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack drop : drops) {
            ItemStack rest = drop.copy();
            for (ItemStack stack : merged) {
                if (rest.isEmpty()) {
                    break;
                }
                if (stack.getCount() < stack.getMaxStackSize() && ItemStack.isSameItemSameTags(stack, rest)) {
                    int move = Math.min(rest.getCount(), stack.getMaxStackSize() - stack.getCount());
                    stack.grow(move);
                    rest.shrink(move);
                }
            }
            if (!rest.isEmpty()) {
                merged.add(rest);
            }
        }
        return merged;
    }
}
