package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ItemFilters;
import com.flameforge.essentialcraft5.registry.ECLogistics;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 物品过滤器的样本界面（原作 {@code ContainerFilter} + {@code InventoryMagicFilter}）：9 个幽灵格，坐标照原作 L33（62 + i%3×18，17 + i/3×18），
 * 玩家背包 (8, 84)、快捷栏 142（原作 setupPlayerInventory）。
 * 幽灵格只记样本（左键设成光标物品的副本、右键清空、从背包 Shift 复制、数字键取快捷栏那格的副本），样本不消耗；
 * 每次改动立刻写回打开时手上的那件过滤器（{@link HeldItemMenu#commit}：超过 8 KB、第三层套娃时这次改动撤销）。
 * 手持那格锁住、数字键换不走，物品离手界面就关（{@link HeldItemMenu}）。
 *
 * <h2>高级过滤器的按钮（{@link #clickMenuButton}，porting_guide §9，不加网络包）</h2>
 * <ul>
 *   <li>{@link #BUTTON_FLAG} + 0~2：切换「忽略耐久 / 忽略 NBT / 按标签」（原作 GuiFilter 的三个按钮；原作的按钮包只认客户端报上来的界面类名，
 *       往主手任意物品写三个布尔值，这里服务端先查手上确实是高级过滤器）。打开「按标签」时还没选过标签的格子自动选默认标签（{@link ItemFilters#setFlag}）。</li>
 *   <li>{@link #BUTTON_NEXT_TAG} / {@link #BUTTON_PREV_TAG} + 格号：这一格换下一个 / 上一个标签（界面里鼠标指着样本滚轮）。
 *       候选是这件样本自己的全部标签加「按物品」（{@link ItemFilters#tagChoices}），由服务端按样本算，客户端只报格号和方向；
 *       格子空着、开关没开、样本没有标签时返回 false。</li>
 * </ul>
 * 普通过滤器、客户端调用、越界的按钮号一律返回 false、什么都不改。
 * <p>
 * 开关和每格选中的标签用 {@link DataSlot} 同步给界面（每格同步的是它在 {@link ItemFilters#tagChoices} 里的序号，0 = 按物品；客户端按同步来的样本自己查出标签名，
 * 编解码是 {@link #encodeTag} / {@link #decodeTag}；手上那件的 NBT 换了或标签重载过才重算）：
 * 从副手打开时，客户端背包里那件过滤器的 NBT 在界面开着期间不会更新（原版只同步当前菜单的格子），不能靠读它（同 {@code CraftingFrameMenu}）。
 */
public class FilterMenu extends HeldItemMenu {

    public static final int GRID_X = 62;
    public static final int GRID_Y = 17;
    /** 开关按钮：+ {@link ItemFilters.Flag#ordinal()}（0 忽略耐久、1 忽略 NBT、2 按标签） */
    public static final int BUTTON_FLAG = 0;
    /** 第 i 格换下一个标签：16 + i */
    public static final int BUTTON_NEXT_TAG = 16;
    /** 第 i 格换上一个标签：32 + i */
    public static final int BUTTON_PREV_TAG = 32;

    private final SimpleContainer samples = new SimpleContainer(ItemFilters.SLOTS);
    private final boolean advanced;

    /** 客户端：同步来的开关（按 Flag 序号的位） */
    private int syncedFlags;
    /** 客户端：同步来的每格标签序号（0 = 按物品，k = tagChoices 的第 k 个） */
    private final int[] syncedTags = new int[ItemFilters.SLOTS];

    /** 服务端：上次算同步值时手上过滤器的 NBT 对象（commit 每次都换新对象，身份不变就不用重算） */
    @Nullable
    private CompoundTag syncSource;
    /** 服务端：上次算同步值时的标签代数（{@link #tagGeneration}） */
    private int syncGeneration;
    private boolean syncValid;
    private int serverFlags;
    private final int[] serverTags = new int[ItemFilters.SLOTS];

    /** 这次 commit 要顺带改的开关 / 标签（按钮处理时设、commit 完清掉） */
    @Nullable
    private ItemFilters.Flag pendingFlag;
    private boolean pendingFlagValue;
    private int pendingTagSlot = -1;
    @Nullable
    private TagKey<Item> pendingTag;

    /** 客户端（{@code ECMenus.register("filter", FilterMenu::new)}） */
    public FilterMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, HeldItemMenu.readHand(buf));
    }

    public FilterMenu(int id, Inventory inventory, InteractionHand hand) {
        super(ECLogistics.FILTER_MENU.get(), id, inventory, hand);
        NonNullList<ItemStack> saved = ItemFilters.samples(held());
        for (int i = 0; i < ItemFilters.SLOTS; i++) {
            samples.setItem(i, saved.get(i));
            addSlot(new GhostSlot(samples, i, GRID_X + i % 3 * 18, GRID_Y + i / 3 * 18, ItemFilters::canBeSample));
        }
        addPlayerInventory(inventory, 8, 84);
        this.advanced = ItemFilters.isAdvanced(held());
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                refreshServerSync();
                return serverFlags;
            }

            @Override
            public void set(int value) {
                syncedFlags = value;
            }
        });
        for (int i = 0; i < ItemFilters.SLOTS; i++) {
            int slot = i;
            addDataSlot(new DataSlot() {
                @Override
                public int get() {
                    refreshServerSync();
                    return serverTags[slot];
                }

                @Override
                public void set(int value) {
                    syncedTags[slot] = value;
                }
            });
        }
    }

    /** 第 i 格的样本（界面、测试用） */
    public ItemStack sample(int i) {
        return samples.getItem(i);
    }

    /** 打开的是不是高级过滤器（才有三个开关） */
    public boolean isAdvanced() {
        return advanced;
    }

    /** 开关现在开没开：服务端读手上过滤器的 NBT，客户端读同步来的值 */
    public boolean flagOn(ItemFilters.Flag flag) {
        if (isClientSide()) {
            return (syncedFlags & 1 << flag.ordinal()) != 0;
        }
        return ItemFilters.flag(held(), flag);
    }

    /**
     * 第 i 格判定时用的标签（{@link ItemFilters#effectiveTag}）；按物品比时 null。不看开关。
     * 客户端由同步来的序号在这件样本的 {@link ItemFilters#tagChoices} 里取。
     */
    @Nullable
    public TagKey<Item> selectedTag(int i) {
        if (i < 0 || i >= ItemFilters.SLOTS) {
            return null;
        }
        if (!isClientSide()) {
            return ItemFilters.effectiveTag(held(), i);
        }
        return decodeTag(samples.getItem(i), syncedTags[i]);
    }

    /**
     * 服务端：一格的标签编成同步值——它在这件样本的 {@link ItemFilters#tagChoices} 里的序号 + 1；null（按物品）或不在候选里是 0。
     * 和 {@link #decodeTag} 是一对，两端各自按样本查候选表，所以同步的只是一个小整数。
     */
    public static int encodeTag(ItemStack sample, @Nullable TagKey<Item> tag) {
        return tagIndex(ItemFilters.tagChoices(sample), tag);
    }

    /** 客户端：把 {@link #encodeTag} 的同步值按这件样本的候选表解回标签；0 或越界返回 null（按物品） */
    @Nullable
    public static TagKey<Item> decodeTag(ItemStack sample, int index) {
        List<TagKey<Item>> choices = ItemFilters.tagChoices(sample);
        return index > 0 && index <= choices.size() ? choices.get(index - 1) : null;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (player.level().isClientSide || !advanced || !ItemFilters.isAdvanced(held())) {
            return false;
        }
        ItemFilters.Flag[] flags = ItemFilters.Flag.values();
        if (id >= BUTTON_FLAG && id < BUTTON_FLAG + flags.length) {
            ItemFilters.Flag flag = flags[id - BUTTON_FLAG];
            pendingFlag = flag;
            pendingFlagValue = !ItemFilters.flag(held(), flag);
            try {
                return commit();
            } finally {
                pendingFlag = null;
            }
        }
        int slot;
        int step;
        if (id >= BUTTON_NEXT_TAG && id < BUTTON_NEXT_TAG + ItemFilters.SLOTS) {
            slot = id - BUTTON_NEXT_TAG;
            step = 1;
        } else if (id >= BUTTON_PREV_TAG && id < BUTTON_PREV_TAG + ItemFilters.SLOTS) {
            slot = id - BUTTON_PREV_TAG;
            step = -1;
        } else {
            return false;
        }
        ItemStack held = held();
        if (!ItemFilters.flag(held, ItemFilters.Flag.MATCH_TAGS)) {
            return false;
        }
        ItemStack sample = ItemFilters.samples(held).get(slot);
        List<TagKey<Item>> choices = ItemFilters.tagChoices(sample);
        if (choices.isEmpty()) {
            return false;
        }
        int current = tagIndex(choices, ItemFilters.effectiveTag(held, slot));
        int next = Math.floorMod(current + step, choices.size() + 1);
        pendingTagSlot = slot;
        pendingTag = next == 0 ? null : choices.get(next - 1);
        try {
            return commit();
        } finally {
            pendingTagSlot = -1;
            pendingTag = null;
        }
    }

    @Override
    protected boolean saveTo(ItemStack held) {
        List<ItemStack> list = new ArrayList<>(ItemFilters.SLOTS);
        for (int i = 0; i < ItemFilters.SLOTS; i++) {
            list.add(samples.getItem(i));
        }
        if (!ItemFilters.setSamples(held, list)) {
            return false;
        }
        if (pendingFlag != null) {
            ItemFilters.setFlag(held, pendingFlag, pendingFlagValue);
        }
        return pendingTagSlot < 0 || ItemFilters.setSampleTag(held, pendingTagSlot, pendingTag);
    }

    private boolean isClientSide() {
        return playerInventory.player.level().isClientSide;
    }

    /**
     * 服务端：手上过滤器的 NBT 换了、或者标签重载过，才重算同步值（DataSlot 每刻都会问一遍）。
     * 同步值还取决于标签成员（样本还在不在所选标签里、候选表的排序），{@code /reload} 后这些会变而 NBT 对象不变：
     * 不跟着重算的话，客户端按新的候选表解旧序号，角标和提示会写成别的标签（实际分拣用存着的标签，不受影响）。
     */
    private void refreshServerSync() {
        if (isClientSide()) {
            return;
        }
        ItemStack held = held();
        CompoundTag tag = held.getTag();
        int generation = tagGeneration;
        if (syncValid && tag == syncSource && generation == syncGeneration) {
            return;
        }
        syncSource = tag;
        syncGeneration = generation;
        syncValid = true;
        int bits = 0;
        for (ItemFilters.Flag flag : ItemFilters.Flag.values()) {
            if (ItemFilters.flag(held, flag)) {
                bits |= 1 << flag.ordinal();
            }
        }
        serverFlags = bits;
        NonNullList<ItemStack> saved = ItemFilters.samples(held);
        for (int i = 0; i < ItemFilters.SLOTS; i++) {
            serverTags[i] = encodeTag(saved.get(i), ItemFilters.effectiveTag(held, i));
        }
    }

    /** 标签在候选表里的序号：0 = 按物品（null 或不在表里），k = 第 k 个 */
    private static int tagIndex(List<TagKey<Item>> choices, @Nullable TagKey<Item> tag) {
        return tag == null ? 0 : choices.indexOf(tag) + 1;
    }

    /** 标签重载一次加 1（{@link #refreshServerSync} 的缓存键之一）。服务端在服务端线程、客户端在渲染线程改，单人里两边共用这一个数，多算一次没关系 */
    private static volatile int tagGeneration;

    /** {@code /reload}、进服收到标签时都会发 {@link TagsUpdatedEvent}：标签代数加 1，开着的过滤器界面下一刻重算同步值 */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
    public static final class FilterTagEvents {

        private FilterTagEvents() {
        }

        @SubscribeEvent
        public static void onFilterTagsUpdated(TagsUpdatedEvent event) {
            tagGeneration++;
        }
    }
}
