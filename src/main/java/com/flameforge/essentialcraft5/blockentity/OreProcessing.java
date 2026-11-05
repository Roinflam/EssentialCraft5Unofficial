package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.item.MagmaticAlloyItem;
import com.flameforge.essentialcraft5.recipe.OreSmeltingRecipe;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * 岩浆冶炼炉和岩浆熔炉共用的小工具：界面状态、输出槽「先看放不放得下再做」、出魔力炉渣。
 * <p>
 * 原作两台机器都只检查「输出槽 +4 / +1 放得下」就开工，做完再按实际产量（掉落数 × 2 / 掉落数）加上去、截到堆叠上限，
 * 青金石矿一次出 32 个合金，输出槽里原有 40 个时多出来的直接没了。这里按实际产量判断，放不下就停在原处等人取走。
 */
public final class OreProcessing {

    /** 界面上显示的一道工序的状态（服务端算好，随菜单同步） */
    public enum Status {
        /** 输入格空着 */
        IDLE(0xFFFFFF),
        WORKING(0x00FF00),
        /** 输入格里的东西没有对应的矿石加工配方（或岩浆合金的配方被数据包删了） */
        NO_RECIPE(0xFF0000),
        /** 粗矿在这台机器里两个出一份，现在只有一个 */
        NEED_MORE(0xFFFF00),
        NO_LAVA(0xFFFF00),
        NO_MRU(0xFFFF00),
        OUTPUT_FULL(0xFF0000),
        /** 有红石信号，停机 */
        POWERED(0xFF0000);

        private static final Status[] VALUES = values();

        /** 字色：红色要玩家处理，黄色是暂时条件不够，绿色正常（与辐射仓、绑定宝石状态的配色一致） */
        public final int color;

        Status(int color) {
            this.color = color;
        }

        public MutableComponent label() {
            return Component.translatable("gui.essentialcraft5.ore_processing." + name().toLowerCase(Locale.ROOT));
        }

        public static Status byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : IDLE;
        }
    }

    private OreProcessing() {
    }

    /**
     * 每 tick 都要判断「放不放得下」的那一份产物：配方和数量没变就复用同一个堆叠，不每 tick 新建物品和 NBT
     * （机器卡在「输出槽已满」时也是每 tick 都在判断）。返回的堆叠只能读，不能改；放进槽里时 {@link #put} 会复制。
     */
    public static final class Results {
        @Nullable
        private OreSmeltingRecipe alloyRecipe;
        private ItemStack alloys = ItemStack.EMPTY;
        @Nullable
        private OreSmeltingRecipe productRecipe;
        private ItemStack products = ItemStack.EMPTY;

        /** 这条配方熔出来的 count 个岩浆合金 */
        public ItemStack alloys(OreSmeltingRecipe recipe, int count) {
            if (recipe != alloyRecipe || alloys.getCount() != count) {
                alloyRecipe = recipe;
                alloys = MagmaticAlloyItem.create(recipe.getId(), count);
            }
            return alloys;
        }

        /** 一个岩浆合金炼出来的成品（{@link OreSmeltingRecipe#PRODUCTS_PER_ALLOY} 个）；产物标签是空的时返回空物品 */
        public ItemStack products(OreSmeltingRecipe recipe) {
            if (recipe != productRecipe) {
                productRecipe = recipe;
                ItemStack product = recipe.product();
                products = product.isEmpty() ? ItemStack.EMPTY : product.copyWithCount(OreSmeltingRecipe.PRODUCTS_PER_ALLOY);
            }
            return products;
        }
    }

    /** 输出槽放不放得下这一整份（空槽，或同物品同 NBT 且加上后不超过堆叠上限和槽位上限） */
    public static boolean fits(ItemStackHandler inventory, int slot, ItemStack result) {
        if (result.isEmpty()) {
            return false;
        }
        ItemStack out = inventory.getStackInSlot(slot);
        int limit = Math.min(inventory.getSlotLimit(slot), result.getMaxStackSize());
        if (out.isEmpty()) {
            return result.getCount() <= limit;
        }
        return ItemStack.isSameItemSameTags(out, result) && out.getCount() + result.getCount() <= limit;
    }

    /**
     * 把一整份放进输出槽（调用前先用 {@link #fits} 判断）。
     * 用 setStackInSlot 写回（不原地 grow），ItemStackHandler 才知道变了：存盘、客户端同步都靠它；
     * 也不能用 insertItem：输出槽的槽位规则不接受任何东西。
     */
    public static void put(ItemStackHandler inventory, int slot, ItemStack result) {
        ItemStack out = inventory.getStackInSlot(slot);
        inventory.setStackInSlot(slot, out.isEmpty() ? result.copy() : out.copyWithCount(out.getCount() + result.getCount()));
    }

    /** 出一个魔力炉渣；槽满了（或者被别的东西占着）就不出，机器照常工作（同原作） */
    public static void addSlag(ItemStackHandler inventory, int slot) {
        Item slag = ECMaterials.MAGICAL_SLAG.get();
        ItemStack out = inventory.getStackInSlot(slot);
        if (out.isEmpty()) {
            inventory.setStackInSlot(slot, new ItemStack(slag));
        } else if (out.is(slag) && out.getCount() < Math.min(out.getMaxStackSize(), inventory.getSlotLimit(slot))) {
            inventory.setStackInSlot(slot, out.copyWithCount(out.getCount() + 1));
        }
    }
}
