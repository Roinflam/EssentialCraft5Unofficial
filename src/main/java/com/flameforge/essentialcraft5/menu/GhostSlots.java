package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.api.IItemFilter;
import com.google.common.hash.Hashing;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 幽灵格的点击处理与「样本」工具（batch4 F4-9）。
 *
 * <h2>点击（{@link #handleClick}）</h2>
 * 菜单覆盖 {@code clicked}，先调它，返回 true 就 return（{@link HeldItemMenu} 已经这样做了；机器菜单要用幽灵格也照这样写）：
 * <pre>{@code
 * public void clicked(int slotId, int button, ClickType type, Player player) {
 *     if (GhostSlots.handleClick(this, slotId, button, type, player)) {
 *         return;   // 样本变了的话，这里写回（机器菜单：写进方块实体并 setChanged）
 *     }
 *     super.clicked(slotId, button, type, player);
 * }
 * }</pre>
 * 规则：左键 = 样本设成光标物品的一份副本（数量 1，<b>光标上的东西不动</b>），光标空或右键 = 清空；
 * Shift 点幽灵格 = 清空，从玩家背包 Shift 点 = 复制一份进第一个空着、肯收的幽灵格（背包里的不动）；
 * 数字键 / F 对着幽灵格 = 设成那格快捷栏（副手）物品的副本；丢弃键 = 清空（不掉东西）；创造模式中键、双击收集对幽灵格什么都不做；
 * 拖拽不经过这里（{@code QUICK_CRAFT} 返回 false 交给原版），靠 {@link GhostSlot#mayPlace} 为假和菜单的 {@code canDragTo} 跳过幽灵格。
 * 两端跑同一份逻辑（客户端预测），服务端的结果再由原版菜单同步回来。混合端先发 Bukkit {@code InventoryClickEvent} 再调 {@code clicked}，插件拒了就什么都不变。
 *
 * <h2>样本体积上限（policy §1.2 P2）</h2>
 * 幽灵格让「零成本复制 NBT」成为可能：一本 100 KB 的书放 9 次、再套一层过滤器就是 8 MB 的区块炸弹。所以：
 * <ul>
 *   <li>单个样本的 NBT（{@code stack.getTag()}，按 {@link Tag#sizeInBytes()}）大于 {@link #MAX_SAMPLE_BYTES} 的，样本只留物品 id + 内容哈希
 *       （{@link #sampleOf} 当场转换，界面上显示的是不带 NBT 的那个物品；匹配时比哈希，见 {@link #matches}）。
 *       过滤器物品（{@link IItemFilter}）例外：套娃要读它里面的样本，整份留着，靠下面的总量上限和两层上限（{@code ItemFilters.MAX_DEPTH}）压住。</li>
 *   <li>装样本的那件物品（过滤器、合成框架）整个 NBT 不超过 {@link #MAX_ITEM_BYTES}，超了这次改动不生效（{@link HeldItemMenu#commit}、{@code ItemFilters.setSample}）。</li>
 * </ul>
 * 存盘格式（{@link #save} / {@link #load}）：普通样本 {@code {item: {id, Count: 1b, tag}}}；哈希样本 {@code {id: "ns:path", hash: long, damage: int}}。
 * 调用方自己加格子号之类的键（例：过滤器的 {@code Slot}）。
 */
public final class GhostSlots {

    /** 单个样本 NBT 超过这么多字节就只存 id + 哈希 */
    public static final int MAX_SAMPLE_BYTES = 512;
    /** 装样本的物品整个 NBT 的上限（8 KB） */
    public static final int MAX_ITEM_BYTES = 8192;
    /** 哈希样本在菜单里的标记：物品 NBT 里只有这一个键 {@code {hash: long, damage: int}} */
    public static final String HASHED_KEY = "ec5_hashed_sample";

    private static final String ITEM = "item";
    private static final String ID = "id";
    private static final String HASH = "hash";
    private static final String DAMAGE = "damage";
    /** 原版耐久的 NBT 键：哈希不算它，另外单独比（忽略耐久的高级过滤器要用） */
    private static final String DAMAGE_TAG = "Damage";

    private GhostSlots() {
    }

    // ------------------------------------------------------------------
    // 点击
    // ------------------------------------------------------------------

    /**
     * 处理一次点击里和幽灵格有关的部分（规则见类注释）。返回 true 表示已经处理（菜单直接 return），false 交给原版。
     * 只改幽灵格的内容，不碰光标、不碰玩家背包。
     */
    public static boolean handleClick(AbstractContainerMenu menu, int slotId, int button, ClickType type, Player player) {
        if (slotId < 0 || slotId >= menu.slots.size()) {
            return false;
        }
        Slot slot = menu.slots.get(slotId);
        if (slot instanceof GhostSlot ghost) {
            switch (type) {
                case PICKUP -> {
                    ItemStack carried = menu.getCarried();
                    if (button == 0 && !carried.isEmpty()) {
                        ghost.setSample(carried);
                    } else if (button == 0 || button == 1) {
                        ghost.clearSample();
                    }
                }
                case QUICK_MOVE, THROW -> ghost.clearSample();
                case SWAP -> {
                    if (isInventoryButton(button)) {
                        ItemStack source = player.getInventory().getItem(button);
                        if (source.isEmpty()) {
                            ghost.clearSample();
                        } else {
                            ghost.setSample(source);
                        }
                    }
                }
                case QUICK_CRAFT -> {
                    // 拖拽是跨好几个包的状态机，交给原版；幽灵格 mayPlace 为假，原版自己会跳过
                    return false;
                }
                default -> {
                    // CLONE（创造中键）、PICKUP_ALL（双击收集）：对幽灵格什么都不做
                }
            }
            return true;
        }
        // 从玩家背包 Shift 点：复制进第一个空着、肯收的幽灵格；没有这样的格子交给原版（机器菜单可能还有真格子）
        if (type == ClickType.QUICK_MOVE && slot.container instanceof Inventory && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            for (Slot target : menu.slots) {
                if (target instanceof GhostSlot ghost && !ghost.hasItem() && ghost.acceptsSample(stack)) {
                    ghost.setSample(stack);
                    return true;
                }
            }
        }
        return false;
    }

    /** SWAP 的按钮号是背包下标：快捷栏 0~8、副手 40 */
    private static boolean isInventoryButton(int button) {
        return Inventory.isHotbarSlot(button) || button == Inventory.SLOT_OFFHAND;
    }

    // ------------------------------------------------------------------
    // 样本
    // ------------------------------------------------------------------

    /**
     * 把一件物品变成样本：数量 1 的副本；NBT 大于 {@link #MAX_SAMPLE_BYTES} 的变成「哈希样本」（同一物品、NBT 里只有 {@link #HASHED_KEY}）。
     * 已经是哈希样本的原样复制；过滤器物品整份保留（见类注释）。空物品返回空。
     */
    public static ItemStack sampleOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || isHashed(stack) || stack.getItem() instanceof IItemFilter || tag.sizeInBytes() <= MAX_SAMPLE_BYTES) {
            return stack.copyWithCount(1);
        }
        return hashedSample(stack.getItem(), contentHash(tag), stack.getDamageValue());
    }

    /** 是不是哈希样本（{@link #sampleOf} 转换过的大 NBT 样本） */
    public static boolean isHashed(ItemStack sample) {
        CompoundTag tag = sample.getTag();
        return tag != null && tag.contains(HASHED_KEY, Tag.TAG_COMPOUND);
    }

    /**
     * NBT 的内容哈希：去掉 {@code Damage} 后按键排好序的字符串形式（{@code getAsString}，和存档里键的顺序无关）的 SHA-256 前 64 位。
     * 64 位足够：撞上了也只是过滤器把一件别的物品当成样本，不会复制东西。
     */
    public static long contentHash(@Nullable CompoundTag tag) {
        CompoundTag stripped = withoutDamage(tag);
        String text = stripped == null ? "" : stripped.getAsString();
        return Hashing.sha256().hashString(text, StandardCharsets.UTF_8).asLong();
    }

    /** {@link #cachedContentHash} 每个线程最多记这么多份 */
    private static final int HASH_CACHE_SIZE = 64;
    /** 候选物品的 NBT（按内容认，键是私有副本）→ {@link #contentHash}；按访问顺序淘汰最久没用的 */
    private static final ThreadLocal<Map<CompoundTag, Long>> HASH_CACHE = ThreadLocal.withInitial(
            () -> new LinkedHashMap<>(HASH_CACHE_SIZE * 2, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<CompoundTag, Long> eldest) {
                    return size() > HASH_CACHE_SIZE;
                }
            });

    /**
     * 带缓存的 {@link #contentHash}，给 {@link #matches} 比哈希样本用（4a 审查 CRAFT-1）。
     * <p>
     * 不缓存时，漏斗每 tick 对合成器 11 个槽逐个试插、下方漏斗每 tick 问能不能抽，每问一次都要把候选物品整份 NBT 转成字符串再算 SHA-256；
     * 塞了装满长书的潜影盒这类 MB 级的物品，一台机器就能拖垮 tick。这里按 NBT <b>内容</b>记住算过的结果：
     * 键是 {@code tag.copy()}（私有副本，外面原地改了物品的 NBT 也不会弄坏缓存），查找按 {@code CompoundTag.equals / hashCode} 比内容。
     * 漏斗每 tick 重新拷出来的物品和缓存里的键共用同一批不可变的字符串标签（{@code StringTag.copy} 返回自己，String 自带哈希缓存、比较先看是不是同一个对象），
     * 所以命中时的开销通常只和标签个数有关，和字数无关（从存档重新读出来的同内容物品要逐字比一遍，也远比转字符串再算 SHA 便宜）。
     * 每个线程一份（服务端线程、客户端菜单的渲染线程各用各的），最多 {@value #HASH_CACHE_SIZE} 份。
     */
    private static long cachedContentHash(CompoundTag tag) {
        Map<CompoundTag, Long> cache = HASH_CACHE.get();
        Long hit = cache.get(tag);
        if (hit != null) {
            return hit;
        }
        long hash = contentHash(tag);
        cache.put(tag.copy(), hash);
        return hash;
    }

    /**
     * 候选物品是否匹配样本：同一物品且 NBT 相同（{@link ItemStack#isSameItemSameTags}）；哈希样本比内容哈希和耐久
     * （候选物品的哈希按 NBT 内容缓存，见 {@link #cachedContentHash}）。
     */
    public static boolean matches(ItemStack sample, ItemStack candidate) {
        return matches(sample, candidate, false, false);
    }

    /**
     * 同上，带高级过滤器的两个开关。
     *
     * @param ignoreDamage 不比耐久（{@code Damage}）
     * @param ignoreNbt    只比物品，不比 NBT
     */
    public static boolean matches(ItemStack sample, ItemStack candidate, boolean ignoreDamage, boolean ignoreNbt) {
        if (sample.isEmpty() || candidate.isEmpty() || !candidate.is(sample.getItem())) {
            return false;
        }
        if (!ignoreNbt && !ignoreDamage && !isHashed(sample)) {
            return ItemStack.isSameItemSameTags(sample, candidate);
        }
        return nbtMatches(sample, candidate, ignoreDamage, ignoreNbt);
    }

    /**
     * 只比 NBT、不比是哪种物品（高级过滤器「按标签」用：物品同一性换成「在同一个标签里」，NBT 照样要一样）。
     * 哈希样本比内容哈希和耐久；{@code ignoreDamage} 时去掉 {@code Damage} 再比（「只有 Damage:0」和「没有 NBT」当成一样）；
     * 不忽略时耐久值单独比（{@link ItemStack#getDamageValue}，没有 {@code Damage} 键算 0），其余 NBT 整份比，空 compound 和没有 NBT 当成一样。
     * <p>
     * 耐久要单独比：1.20.1 有耐久的物品一建出来就带 {@code Damage:0}，两边是不同物品时（标签里同时有打火石和火焰弹、金锭和金工具），
     * 整份比会把没损耗的新打火石当成「NBT 不同」拒掉；按物品比的路径（{@link #matches}）两边是同一种物品，不受影响。
     *
     * @param ignoreNbt 为真时恒真（只要两边都不空）
     */
    public static boolean nbtMatches(ItemStack sample, ItemStack candidate, boolean ignoreDamage, boolean ignoreNbt) {
        if (sample.isEmpty() || candidate.isEmpty()) {
            return false;
        }
        if (ignoreNbt) {
            return true;
        }
        if (isHashed(sample)) {
            CompoundTag marker = Objects.requireNonNull(sample.getTag()).getCompound(HASHED_KEY);
            if (!ignoreDamage && candidate.getDamageValue() != marker.getInt(DAMAGE)) {
                return false;
            }
            // 哈希样本原本的 NBT 超过 512 字节，不带 NBT 的候选不可能是它
            return candidate.getTag() != null && cachedContentHash(candidate.getTag()) == marker.getLong(HASH);
        }
        if (!ignoreDamage && sample.getDamageValue() != candidate.getDamageValue()) {
            return false;
        }
        return sameExceptDamage(sample.getTag(), candidate.getTag());
    }

    /**
     * 两份 NBT 去掉 {@code Damage} 后是否相同（没有 NBT、空 compound、只有 {@code Damage} 都算空）。
     * 不拷贝：按标签比时漏斗每 tick 都要对候选物品问一遍，有耐久的候选每次都带 {@code Damage}，拷一份再删太浪费。
     */
    private static boolean sameExceptDamage(@Nullable CompoundTag a, @Nullable CompoundTag b) {
        int size = sizeExceptDamage(a);
        if (size != sizeExceptDamage(b)) {
            return false;
        }
        if (size == 0) {
            return true;
        }
        // 两边去掉 Damage 后键数相同，a 的每个键在 b 里都有相等的值 = 两边的键集合也相同
        for (String key : a.getAllKeys()) {
            if (!DAMAGE_TAG.equals(key) && !Objects.equals(a.get(key), b.get(key))) {
                return false;
            }
        }
        return true;
    }

    private static int sizeExceptDamage(@Nullable CompoundTag tag) {
        if (tag == null) {
            return 0;
        }
        return tag.size() - (tag.contains(DAMAGE_TAG) ? 1 : 0);
    }

    /** 样本写成 NBT（格式见类注释）。空样本返回空 compound（调用方一般直接跳过空格子） */
    public static CompoundTag save(ItemStack sample) {
        CompoundTag out = new CompoundTag();
        if (sample.isEmpty()) {
            return out;
        }
        ItemStack normalized = sampleOf(sample);
        if (isHashed(normalized)) {
            CompoundTag marker = Objects.requireNonNull(normalized.getTag()).getCompound(HASHED_KEY);
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(normalized.getItem());
            out.putString(ID, id == null ? "minecraft:air" : id.toString());
            out.putLong(HASH, marker.getLong(HASH));
            out.putInt(DAMAGE, marker.getInt(DAMAGE));
        } else {
            out.put(ITEM, normalized.save(new CompoundTag()));
        }
        return out;
    }

    /** 从 {@link #save} 的格式读回样本（顺手按现在的规则再过一遍 {@link #sampleOf}）；读不出（物品已删除、格式不对）返回空 */
    public static ItemStack load(CompoundTag tag) {
        if (tag.contains(ITEM, Tag.TAG_COMPOUND)) {
            return sampleOf(ItemStack.of(tag.getCompound(ITEM)));
        }
        if (tag.contains(ID, Tag.TAG_STRING) && tag.contains(HASH, Tag.TAG_LONG)) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString(ID));
            Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
            if (item == null || item == Items.AIR) {
                return ItemStack.EMPTY;
            }
            return hashedSample(item, tag.getLong(HASH), tag.getInt(DAMAGE));
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack hashedSample(Item item, long hash, int damage) {
        ItemStack sample = new ItemStack(item);
        CompoundTag marker = new CompoundTag();
        marker.putLong(HASH, hash);
        marker.putInt(DAMAGE, damage);
        CompoundTag tag = new CompoundTag();
        tag.put(HASHED_KEY, marker);
        sample.setTag(tag);
        return sample;
    }

    /** 去掉 Damage 的副本；没有 NBT 或去掉后是空的返回 null（「只有 Damage:0」和「没有 NBT」当成一样） */
    @Nullable
    private static CompoundTag withoutDamage(@Nullable CompoundTag tag) {
        if (tag == null) {
            return null;
        }
        if (!tag.contains(DAMAGE_TAG)) {
            return tag.isEmpty() ? null : tag;
        }
        CompoundTag copy = tag.copy();
        copy.remove(DAMAGE_TAG);
        return copy.isEmpty() ? null : copy;
    }
}
