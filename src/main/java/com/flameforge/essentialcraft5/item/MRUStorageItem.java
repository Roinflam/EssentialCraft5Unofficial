package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IMRUHandlerItem;
import com.flameforge.essentialcraft5.mru.MRUItemStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * MRU 储存物品：灵魂碎片、灵魂球、暗黑灵魂物质、鲜红灵魂物质、永恒物质。对应原作 ItemMRUStorageEC 的 5 个 meta。
 * isStorage 恒为 true：带在身上（饰品栏戒指位、盔甲、背包、副手）就是玩家的 MRU 电池，见 MRUDrain。
 * 原作是 Baubles 戒指，这里靠物品标签 curios:ring 进 Curios 戒指位（datagen 里加）。
 */
public class MRUStorageItem extends Item implements CreativeVariants {

    /** 原作 ItemMRUStorageEC.colors：按存量百分比（每 10% 一档）给整个贴图染色，空的偏暗、满的偏红 */
    private static final int[] FILL_COLORS = {
            0x555555, 0x665555, 0x775555, 0x885555, 0x995555, 0xAA5555,
            0xBB5555, 0xCC5555, 0xDD5555, 0xEE6666, 0xFF6666
    };

    private final int maxMRU;

    public MRUStorageItem(Properties properties, int maxMRU) {
        super(properties);
        this.maxMRU = maxMRU;
    }

    public int getMaxMRU() {
        return maxMRU;
    }

    @Override
    public @Nullable ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new MRUItemStorage(stack, maxMRU, true);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler ->
                tooltip.add(Component.translatable("tooltip.essentialcraft5.mru", handler.getMRU(), handler.getMaxMRU())
                        .withStyle(ChatFormatting.GRAY)));
    }

    /** 物品颜色处理器用（客户端注册，见 ECClient）。 */
    public static int fillColor(ItemStack stack) {
        IMRUHandlerItem handler = stack.getCapability(ECCapabilities.MRU_ITEM).orElse(null);
        if (handler == null || handler.getMaxMRU() <= 0) {
            return 0xFFFFFF;
        }
        int percentage = (int) ((long) handler.getMRU() * 100 / handler.getMaxMRU());
        return FILL_COLORS[Math.min(percentage / 10, FILL_COLORS.length - 1)];
    }

    @Override
    public List<ItemStack> creativeVariants() {
        ItemStack empty = new ItemStack(this);
        ItemStack full = new ItemStack(this);
        full.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(maxMRU));
        return List.of(empty, full);
    }
}
