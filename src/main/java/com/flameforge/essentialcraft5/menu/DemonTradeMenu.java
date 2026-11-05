package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.demon.DemonWant;
import com.flameforge.essentialcraft5.entity.DemonEntity;
import com.flameforge.essentialcraft5.registry.ECDemon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 和恶魔交易的界面（原作 ContainerDemon + 恶魔自己的 1 格 IInventory）。
 * <p>
 * 原作的格子是恶魔身上的，所有人共用（别人能拿走你放进去的东西）、整组吃掉（多给的白送）、成交后不清空、报酬掉在地上谁都能捡。这里：
 * <ul>
 *   <li>每个打开界面的人一个临时格（菜单自己的 {@link SimpleContainer}），只收它要的东西（{@link DemonWant#matches}），上限 = 要的数量；</li>
 *   <li>放够的那一刻（服务端）先扣掉这些东西，再让恶魔成交（{@link DemonEntity#completeTrade}：报酬直接进这个人的背包）；</li>
 *   <li>关界面退回格子里剩下的（{@link #removed} → {@code clearContainer}）；恶魔离开（被打发走、结构坏了、别人先成交）后
 *       {@link #stillValid} 变假，服务端下一刻关掉界面，东西照样退回；</li>
 *   <li>{@link #stillValid}：恶魔还在、离它 ≤ 8 格（原作）、这个人仍有资格（创造模式玩家开着界面切回生存就失效）。</li>
 * </ul>
 * 打开界面时只写恶魔的实体 id（{@code NetworkHooks.openScreen}），客户端从恶魔的同步数据读需求，不加网络包。
 * <p>
 * 第十三批 13a（契约之誉「熟客」）：恶魔可能开两样要价，投放格收任一样要的东西，上限按它对得上的那几样里数量最少的那样；
 * 格里的东西够了就按对得上、又够数的几样里最便宜的那样成交（报酬和交哪样无关，按最便宜的对玩家总是最好）。
 * 例：要价是「村民灵魂 ×6」或「任意灵魂 ×2」时，一次放进 10 个村民灵魂只扣 2 个。界面左边画两个幽灵格（{@link #WANT_X_FIRST}、{@link #WANT_X}）。
 * 混合端上界面点击先过 Bukkit 的 InventoryClickEvent（policy §2.1 ⑧），插件取消了就什么都不发生。
 */
public class DemonTradeMenu extends AbstractContainerMenu {

    /** 投放格（原作唯一那格的位置） */
    public static final int SLOT_X = 80;
    public static final int SLOT_Y = 30;
    /** 界面左边画需求的幽灵格（只画，不是槽位）；只有一样要价时画在这里 */
    public static final int WANT_X = 52;
    public static final int WANT_Y = 30;
    /** 两样要价时第一样的幽灵格（第二样画在 {@link #WANT_X}，中间写「或」） */
    public static final int WANT_X_FIRST = 20;
    public static final int PLAYER_INVENTORY_X = 8;
    public static final int PLAYER_INVENTORY_Y = 84;
    private static final int OFFER_SLOT = 0;

    @Nullable
    private final DemonEntity demon;
    private final Player player;
    private final SimpleContainer offer;
    /** 成交过程中又触发了格子变化（扣东西本身也是一次变化），别再进一次 */
    private boolean settling;

    /** 客户端：按打开时写的实体 id 找恶魔（找不到时界面只画背包） */
    public DemonTradeMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, find(inventory.player.level(), buf.readVarInt()));
    }

    public DemonTradeMenu(int id, Inventory inventory, @Nullable DemonEntity demon) {
        super(ECDemon.DEMON_TRADE_MENU.get(), id);
        this.demon = demon;
        this.player = inventory.player;
        this.offer = new SimpleContainer(1) {
            @Override
            public void setChanged() {
                super.setChanged();
                DemonTradeMenu.this.slotsChanged(this);
            }
        };
        addSlot(new OfferSlot(offer, OFFER_SLOT, SLOT_X, SLOT_Y));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, PLAYER_INVENTORY_X + col * 18, PLAYER_INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, PLAYER_INVENTORY_X + col * 18, PLAYER_INVENTORY_Y + 58));
        }
    }

    @Nullable
    private static DemonEntity find(Level level, int entityId) {
        Entity entity = level.getEntity(entityId);
        return entity instanceof DemonEntity demon ? demon : null;
    }

    @Nullable
    public DemonEntity demon() {
        return demon;
    }

    /** 第一样需求（客户端从同步数据反推），没有恶魔或恶魔什么都不要时为 null */
    @Nullable
    public DemonWant want() {
        return demon == null ? null : demon.want();
    }

    /** 全部需求（一样或两样）；没有恶魔时为空 */
    public List<DemonWant> wants() {
        return demon == null ? List.of() : demon.wants();
    }

    /** 投放格里现在的东西（测试用） */
    public ItemStack offered() {
        return offer.getItem(OFFER_SLOT);
    }

    /** 格子一变就看够不够：只在服务端成交 */
    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (container == offer && player instanceof ServerPlayer serverPlayer) {
            settle(serverPlayer);
        }
    }

    private void settle(ServerPlayer serverPlayer) {
        if (settling || demon == null || !demon.isAvailable() || !demon.canTrade(serverPlayer)) {
            return;
        }
        ItemStack stack = offer.getItem(OFFER_SLOT);
        DemonWant met = null;
        for (DemonWant want : demon.wants()) {
            if (want.matches(stack) && stack.getCount() >= want.count() && (met == null || want.count() < met.count())) {
                met = want;
            }
        }
        if (met == null) {
            return;
        }
        settling = true;
        try {
            // 先扣后给：扣掉要的数量，再让恶魔付报酬、离开
            offer.removeItem(OFFER_SLOT, met.count());
            demon.completeTrade(serverPlayer);
        } finally {
            settling = false;
        }
    }

    @Override
    public boolean stillValid(Player player) {
        if (demon == null) {
            // 客户端找不到恶魔时只画背包；服务端的菜单一定带着恶魔
            return player.level().isClientSide;
        }
        return demon.isAvailable() && demon.canTrade(player) && player.distanceToSqr(demon) <= DemonEntity.TRADE_RANGE * DemonEntity.TRADE_RANGE;
    }

    /** 关界面：投放格里剩下的退回背包（人死了、下线了就掉在原地） */
    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer) {
            clearContainer(player, offer);
        }
    }

    /** Shift 点击：投放格 → 背包；背包 → 投放格（只收它要的、不超过要的数量） */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index == OFFER_SLOT) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!slots.get(OFFER_SLOT).mayPlace(stack) || !moveItemStackTo(stack, OFFER_SLOT, OFFER_SLOT + 1, false)) {
            return ItemStack.EMPTY;
        }
        // 正好放够数时，搬的过程中投放格一变就当场成交（slotsChanged → settle），那一刻被点的这一格已经是 0 个；
        // 报酬按「先叠同种、再找第一个空格」给，可能正好放进这一格。格子里已经换成报酬时不能再清空，不然报酬就没了
        if (stack.isEmpty() && slot.getItem() == stack) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    /** 投放格：只收恶魔要的东西（任一样），上限 = 要的数量；恶魔已经在离开时什么都不收 */
    private final class OfferSlot extends Slot {

        OfferSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (demon == null || !demon.isAvailable()) {
                return false;
            }
            for (DemonWant want : wants()) {
                if (want.matches(stack)) {
                    return true;
                }
            }
            return false;
        }

        /** 不看放的是什么时：几样要价里最大的数量 */
        @Override
        public int getMaxStackSize() {
            int max = 1;
            for (DemonWant want : wants()) {
                max = Math.max(max, want.count());
            }
            return max;
        }

        /** 放的是这一堆时：对得上的那几样要价里最少的数量（够这一样就成交，多收也是退回去；对不上的不收，按 1 算） */
        @Override
        public int getMaxStackSize(ItemStack stack) {
            int min = Integer.MAX_VALUE;
            for (DemonWant want : wants()) {
                if (want.matches(stack)) {
                    min = Math.min(min, want.count());
                }
            }
            return Math.min(min == Integer.MAX_VALUE ? 1 : min, stack.getMaxStackSize());
        }
    }
}
