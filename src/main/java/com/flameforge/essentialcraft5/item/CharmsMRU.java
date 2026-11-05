package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IMRUHandlerItem;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.mru.MRUItemStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ICapabilityProvider;

import java.util.List;

/**
 * 护符与随身物品模块（ECCharms）几件 MRU 物品共用的小工具：物品自带的 MRU 存储、提示行、创造栏空 / 满两份、
 * 以及「不扣费、只看够不够」的判断（客户端预判用，原作没有）。
 * <p>
 * 这些物品都是原作 ItemMRUGeneric 系：自带 {@value #MAX_MRU} MRU，在充能仓里充；用的时候照 {@code MRUDrain.playerUseMRU}
 * 先从身上的灵魂储存物品里扣，不够再扣物品自己的，创造模式不扣。它们本身<b>不是</b>储存物品（不会被别的装备当电池抽）。
 */
public final class CharmsMRU {

    /** 原作 ItemMRUGeneric.maxMRU 的默认值 */
    public static final int MAX_MRU = 5000;

    private CharmsMRU() {
    }

    /** 物品自带的 MRU 存储：固定不是储存物品 */
    public static ICapabilityProvider storage(ItemStack stack) {
        return new MRUItemStorage(stack, MAX_MRU, false);
    }

    /** 物品自己存的 MRU（没有存储时 0） */
    public static int storedMRU(ItemStack stack) {
        IMRUHandlerItem handler = stack.getCapability(ECCapabilities.MRU_ITEM).orElse(null);
        return handler == null ? 0 : handler.getMRU();
    }

    /** 直接设物品自己的 MRU（GameTest、创造栏用） */
    public static void setStoredMRU(ItemStack stack, int amount) {
        stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(amount));
    }

    /**
     * 只判断「现在付得起 amount MRU」，什么都不扣：结论同 {@link MRUDrain#canAfford}（创造模式总是付得起；
     * 身上储存物品的总量够，或者物品自己的够），只多一条 amount ≤ 0 恒为 true。
     * 先看物品自己存的（翅膀每刻都问，自己够就不用翻一遍背包和饰品栏），不够再转调 MRUDrain，储存物品怎么算只有那一份。
     * <p>
     * 客户端预判用（翅膀要不要在本地往上飞、随身物品要不要开始吃），客户端看到的物品 NBT 是服务端同步过来的。
     * 真正扣费一律在服务端走 MRUDrain。
     */
    public static boolean canAfford(Player player, ItemStack own, int amount) {
        return amount <= 0 || storedMRU(own) >= amount || MRUDrain.canAfford(player, own, amount);
    }

    /** 提示框第一行：x/5000 MRU（和其它 MRU 物品同一个键） */
    public static void appendMRU(ItemStack stack, List<Component> tooltip) {
        stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler ->
                tooltip.add(Component.translatable("tooltip.essentialcraft5.mru", handler.getMRU(), handler.getMaxMRU())
                        .withStyle(ChatFormatting.GRAY)));
    }

    /** 创造栏放空、满两份（原作 getSubItems） */
    public static List<ItemStack> emptyAndFull(ItemStack empty) {
        ItemStack full = empty.copy();
        setStoredMRU(full, MAX_MRU);
        return List.of(empty, full);
    }

    /** MRU 变化不触发重新举起的动画（原作 ItemMRUGeneric 只在换物品时触发） */
    public static boolean reequip(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !oldStack.is(newStack.getItem());
    }
}
