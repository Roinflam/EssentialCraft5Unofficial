package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IMRUHandlerItem;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.mru.MRUItemStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 法杖与护盾的共同基类（ECStaffs），对应原作 ItemMRUGeneric：自带 {@value #MAX_MRU} MRU（不是储存物品，不会被别的物品当电池抽），
 * 创造模式物品栏放空、满两份，提示框写存量，MRU 变化不触发重新举起的动画。
 * <p>
 * 扣费一律走 {@code MRUDrain.playerUseMRU}（先扣身上的储存物品，凑不够再扣物品自己的）。要「先确认够、做成了再扣」的地方
 * （生命法杖逐格催熟、岩浆法杖挖之前）用 {@link #canAfford}：和 MRUDrain 同一套判断，只读不扣，客户端也能用来决定要不要挥手。
 * 三把矩阵武器是 SwordItem，不继承这个类，共用这里的静态方法。
 */
public class MRUStaffItem extends Item implements CreativeVariants {

    /** 原作 ItemMRUGeneric.maxMRU 的默认值 */
    public static final int MAX_MRU = 5000;

    public MRUStaffItem(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new MRUItemStorage(stack, MAX_MRU);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        mruTooltip(stack, tooltip);
        addUsage(stack, tooltip);
    }

    /** 子类在 MRU 那一行下面加用法说明 */
    protected void addUsage(ItemStack stack, List<Component> tooltip) {
    }

    /** MRU 变化不触发重新举起的动画（原作 ItemMRUGeneric 只在换物品时触发） */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !oldStack.is(newStack.getItem());
    }

    @Override
    public List<ItemStack> creativeVariants() {
        return variants(this);
    }

    // ------------------------------------------------------------------
    // 共用的静态方法（三把武器也用）
    // ------------------------------------------------------------------

    /** 「x/5000 MRU」一行 */
    public static void mruTooltip(ItemStack stack, List<Component> tooltip) {
        stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler ->
                tooltip.add(Component.translatable("tooltip.essentialcraft5.mru", handler.getMRU(), handler.getMaxMRU())
                        .withStyle(ChatFormatting.GRAY)));
    }

    /** 一行灰色的用法说明 */
    public static Component usage(String key, Object... args) {
        return Component.translatable(key, args).withStyle(ChatFormatting.DARK_GRAY);
    }

    /** 创造模式物品栏的空、满两份（原作 getSubItems） */
    public static List<ItemStack> variants(Item item) {
        return List.of(new ItemStack(item), full(item));
    }

    /** 满 MRU 的一件 */
    public static ItemStack full(Item item) {
        ItemStack full = new ItemStack(item);
        full.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(handler.getMaxMRU()));
        return full;
    }

    /** 物品自己存的 MRU（没有能力时 0） */
    public static int storedMRU(ItemStack stack) {
        return stack.getCapability(ECCapabilities.MRU_ITEM).map(IMRUHandlerItem::getMRU).orElse(0);
    }

    /**
     * 现在扣得起 amount 吗：转调 {@link MRUDrain#canAfford}，和 {@code MRUDrain.playerUseMRU} 的判断完全一样
     * （创造模式恒为 true；身上储存物品的总量够，或者物品自己存的够），只是不扣。
     * 同一 tick 里「canAfford → 做 → playerUseMRU」之间没有别的东西动 MRU，后面的扣费一定成功。
     */
    public static boolean canAfford(Player player, ItemStack stack, int amount) {
        return MRUDrain.canAfford(player, stack, amount);
    }

    /** 动作栏提示「MRU 不够（要 x）」 */
    public static void tellNoMRU(Player player, int cost) {
        player.displayClientMessage(Component.translatable("message.essentialcraft5.staffs.no_mru", cost).withStyle(ChatFormatting.RED), true);
    }

    /** 动作栏提示「你没有权限改动那里」（框架的通用键） */
    public static void tellNoPermission(Player player) {
        player.displayClientMessage(Component.translatable("message.essentialcraft5.no_permission").withStyle(ChatFormatting.RED), true);
    }
}
