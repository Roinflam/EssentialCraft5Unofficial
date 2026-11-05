package com.flameforge.essentialcraft5.menu;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 从手上的物品打开的菜单（batch4 F4-9）：物品过滤器、合成框架。菜单里是幽灵格（{@link GhostSlot}），改了样本<b>立刻</b>写回手上那件物品。
 *
 * <h2>修的原作问题</h2>
 * 原作过滤器 / 合成框架每 tick 把菜单内容写进「当前手持的物品」（{@code ItemFilter.onUpdate}），界面不随物品离手失效、{@code canInteractWith} 恒真：
 * 数字键把另一个过滤器换到手上，内容就写进了新的那个；从副手打开写进的是主手；过滤器格子是真物品能刷。这里：
 * <ul>
 *   <li>手持那格在背包里换成锁定槽（{@link LockedSlot}，不能取不能放），数字键 / F 键换到手持那格（副手打开时是 40）被挡掉；</li>
 *   <li>{@link #stillValid}：手上还是打开时那<b>同一个对象</b>（不只是同种物品）且不空——被换走、丢掉、用完、切了快捷栏都立刻关界面；</li>
 *   <li>样本只在幽灵格里（不吞物品、不能取出）；每次变化后马上 {@link #commit()} 写回（不靠每 tick 写），写之前量体积，超过 {@link GhostSlots#MAX_ITEM_BYTES} 就撤销这次改动。</li>
 * </ul>
 * 两端跑同一个 {@link #clicked}（客户端预测，写的是客户端那份物品），服务端的结果由原版菜单同步回来。
 * 混合端先发 Bukkit {@code InventoryClickEvent} 再调 {@code clicked}（{@code ServerGamePacketListenerImpl}），插件拒了什么都不变。
 *
 * <h2>写法</h2>
 * <pre>{@code
 * public class FilterMenu extends HeldItemMenu {
 *     private final SimpleContainer samples = new SimpleContainer(ItemFilters.SLOTS);
 *     // 客户端构造器（ECMenus.register("filter", FilterMenu::new)）
 *     public FilterMenu(int id, Inventory inventory, FriendlyByteBuf buf) { this(id, inventory, HeldItemMenu.readHand(buf)); }
 *     public FilterMenu(int id, Inventory inventory, InteractionHand hand) {
 *         super(ECLogistics.FILTER_MENU.get(), id, inventory, hand);
 *         List<ItemStack> saved = ItemFilters.samples(held());
 *         for (int i = 0; i < ItemFilters.SLOTS; i++) {
 *             samples.setItem(i, saved.get(i));
 *             addSlot(new GhostSlot(samples, i, 62 + i % 3 * 18, 17 + i / 3 * 18, ItemFilters::canBeSample));
 *         }
 *         addPlayerInventory(inventory, 8, 84);
 *     }
 *     protected boolean saveTo(ItemStack held) { return ItemFilters.setSamples(held, 九格内容); }
 * }
 * // 物品的 use（服务端）：HeldItemMenu.open(serverPlayer, hand, stack.getHoverName(), FilterMenu::new); return sidedSuccess
 * }</pre>
 * 界面按钮（模式开关）照 porting_guide §9 走 {@code clickMenuButton}，改完调 {@link #commit()}。
 */
public abstract class HeldItemMenu extends AbstractContainerMenu {

    /** 从手上物品建菜单（{@link #open} 用；服务端） */
    @FunctionalInterface
    public interface Factory {
        AbstractContainerMenu create(int id, Inventory inventory, InteractionHand hand);
    }

    protected final Inventory playerInventory;
    protected final InteractionHand hand;
    /** 手持那格在背包里的下标：主手 = 打开时选中的快捷栏号，副手 = 40 */
    private final int heldSlot;
    /** 打开时手上那个物品对象（{@link #stillValid} 按对象身份比） */
    private final ItemStack opened;

    protected HeldItemMenu(@Nullable MenuType<?> type, int id, Inventory inventory, InteractionHand hand) {
        super(type, id);
        this.playerInventory = inventory;
        this.hand = hand;
        this.heldSlot = hand == InteractionHand.MAIN_HAND ? inventory.selected : Inventory.SLOT_OFFHAND;
        this.opened = inventory.getItem(heldSlot);
    }

    /**
     * 打开（服务端，物品 {@code use} 里调）：手上空着、或是 FakePlayer（别的模组的机械手之类）就不开。客户端的菜单构造器用 {@link #readHand} 读出是哪只手。
     */
    public static void open(ServerPlayer player, InteractionHand hand, Component title, Factory factory) {
        if (player instanceof FakePlayer || player.getItemInHand(hand).isEmpty()) {
            return;
        }
        NetworkHooks.openScreen(player, new SimpleMenuProvider((id, inventory, p) -> factory.create(id, inventory, hand), title),
                buf -> buf.writeEnum(hand));
    }

    /** 客户端菜单构造器读 {@link #open} 写进去的那只手 */
    public static InteractionHand readHand(FriendlyByteBuf buf) {
        return buf.readEnum(InteractionHand.class);
    }

    public InteractionHand hand() {
        return hand;
    }

    /** 手持那格在玩家背包里的下标（主手 0~8、副手 40） */
    public int heldSlot() {
        return heldSlot;
    }

    /** 手上那件物品（当前这一端背包里的那个对象；服务端上就是打开时那个） */
    protected final ItemStack held() {
        return playerInventory.getItem(heldSlot);
    }

    /**
     * 把样本 / 开关写进 held（服务端和客户端都会调，写的是各自那份）。只写本菜单管的数据，别的 NBT 不动。
     * 由 {@link #commit()} 调：它先写在副本上量体积，通过了才换到手上那件物品上。
     *
     * @return false = 这次写不进（例：{@code ItemFilters.setSamples} 因为超上限、第三层套娃拒绝），菜单撤销这次改动
     */
    protected abstract boolean saveTo(ItemStack held);

    /**
     * 马上把菜单内容写回手上的物品：先在副本上 {@link #saveTo}，它返回 false 或整个 NBT 超过 {@link GhostSlots#MAX_ITEM_BYTES} 就放弃并返回 false（手上的物品不动）。
     * 幽灵格点击后自动调（失败时撤销那次点击）；模式按钮之类改了别的状态的，子类自己调（失败时自己把状态改回去）。
     */
    protected final boolean commit() {
        ItemStack held = held();
        if (held.isEmpty() || !isOpenedItem(held)) {
            return false;
        }
        ItemStack probe = held.copy();
        if (!saveTo(probe)) {
            return false;
        }
        CompoundTag tag = probe.getTag();
        if (tag != null && tag.sizeInBytes() > GhostSlots.MAX_ITEM_BYTES) {
            return false;
        }
        held.setTag(tag);
        return true;
    }

    /** 玩家背包（3 行 + 快捷栏，快捷栏在 y + 58），手持那格换成 {@link LockedSlot}。副手不在这 36 格里，靠 {@link #clicked} 挡数字键 40 */
    protected void addPlayerInventory(Inventory inventory, int x, int y) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addInventorySlot(inventory, col + row * 9 + 9, x + col * 18, y + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            addInventorySlot(inventory, col, x + col * 18, y + 58);
        }
    }

    private void addInventorySlot(Inventory inventory, int index, int x, int y) {
        addSlot(index == heldSlot ? new LockedSlot(inventory, index, x, y) : new Slot(inventory, index, x, y));
    }

    /**
     * 服务端：手持那格还是打开时那个对象、且不空。客户端的物品对象每次同步都会换新，不按身份比（客户端只做预测，结果以服务端为准）
     */
    private boolean isOpenedItem(ItemStack current) {
        if (playerInventory.player.level().isClientSide) {
            return !current.isEmpty();
        }
        return current == opened && !opened.isEmpty();
    }

    /** 手上还是打开时那同一个物品对象、且不空（被换走、丢掉、用完、切快捷栏都会失效；原作恒真） */
    @Override
    public boolean stillValid(Player player) {
        return !opened.isEmpty() && player.getItemInHand(hand) == opened;
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        // 手持那格已经不是打开时那件（下一刻就会因 stillValid 关掉）：这期间的点击一律不理，免得写进换上来的另一件物品（原作的复制点）
        if (!isOpenedItem(held())) {
            return;
        }
        if (slotId >= 0 && slotId < slots.size() && slots.get(slotId) instanceof LockedSlot) {
            return;
        }
        // 数字键 / F 键把手持那格换走（主手：那格的快捷栏号；副手打开：40）
        if (type == ClickType.SWAP && button == heldSlot) {
            return;
        }
        List<ItemStack> before = ghostContents();
        if (GhostSlots.handleClick(this, slotId, button, type, player)) {
            if (!sameContents(before, ghostContents()) && !commit()) {
                restoreGhosts(before);
            }
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    /** Shift 点击：幽灵格和背包之间都不搬（从背包 Shift 复制样本由 {@link GhostSlots#handleClick} 做） */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return !(slot instanceof GhostSlot) && !(slot instanceof LockedSlot) && super.canDragTo(slot);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return !(slot instanceof GhostSlot) && !(slot instanceof LockedSlot) && super.canTakeItemForPickAll(stack, slot);
    }

    private List<ItemStack> ghostContents() {
        List<ItemStack> out = new ArrayList<>();
        for (Slot slot : slots) {
            if (slot instanceof GhostSlot) {
                out.add(slot.getItem().copy());
            }
        }
        return out;
    }

    private static boolean sameContents(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!ItemStack.matches(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    private void restoreGhosts(List<ItemStack> before) {
        int i = 0;
        for (Slot slot : slots) {
            if (slot instanceof GhostSlot) {
                slot.set(before.get(i++));
            }
        }
    }

    /** 手持那格：看得见，不能取、不能放（数字键换走另外在 {@link #clicked} 挡） */
    public static class LockedSlot extends Slot {
        public LockedSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
