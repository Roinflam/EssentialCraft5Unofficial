package com.flameforge.essentialcraft5.menu;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;

import java.util.function.Predicate;

/**
 * 幽灵格（batch4 F4-9）：只记一份「样本」（数量 1），<b>不是真物品</b>——不能取、不能放（{@link #mayPickup} / {@link #mayPlace} 恒假），
 * 所以原版的点击、Shift、拖拽、双击收集、丢弃都动不了它；改样本只走 {@link GhostSlots#handleClick}（菜单的 {@code clicked} 里先交给它）。
 * <p>
 * 为什么要它（policy §4.3、batch4 §3.6）：原作过滤器和合成框架的格子是普通 Slot，放进去的是真物品，
 * 过滤器能刷物品（格子里的东西既在 NBT 里又能取出来）。改成幽灵格以后「放样本不消耗」，没有东西可复制。
 * <p>
 * 样本由 {@link GhostSlots#sampleOf} 生成：NBT 大于 {@link GhostSlots#MAX_SAMPLE_BYTES} 的只留物品 id + 内容哈希（见那里的说明）。
 * 背后的 {@link Container} 一般是菜单自己建的 {@code SimpleContainer}，内容靠原版菜单同步发给客户端（{@code Slot.set} 不查 mayPlace）。
 */
public class GhostSlot extends Slot {

    private final Predicate<ItemStack> accepts;

    public GhostSlot(Container container, int index, int x, int y) {
        this(container, index, x, y, stack -> true);
    }

    /**
     * @param accepts 这一格肯收什么样本（例：过滤器的格子拒收「套了两层的过滤器」，见 {@code ItemFilters.canBeSample}）；
     *                不收的点击什么都不变
     */
    public GhostSlot(Container container, int index, int x, int y, Predicate<ItemStack> accepts) {
        super(container, index, x, y);
        this.accepts = accepts;
    }

    /** 这一格肯不肯收这个样本（空物品 = 清空，总是可以） */
    public boolean acceptsSample(ItemStack stack) {
        return stack.isEmpty() || accepts.test(stack);
    }

    /** 把样本设成 stack 的一份副本（数量 1，大 NBT 转哈希）；不肯收时什么都不做、返回 false。stack 本身不动 */
    public boolean setSample(ItemStack stack) {
        if (!acceptsSample(stack)) {
            return false;
        }
        set(GhostSlots.sampleOf(stack));
        return true;
    }

    /** 清空样本 */
    public void clearSample() {
        set(ItemStack.EMPTY);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public boolean mayPickup(Player player) {
        return false;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return 1;
    }
}
