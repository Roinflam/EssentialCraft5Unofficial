package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IMRUHandlerItem;
import com.flameforge.essentialcraft5.registry.ECEssentialTools;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * 源质镐 / 源质斧共用的部分（batch12.md §4.4.2）。两件分别继承 {@code PickaxeItem} / {@code AxeItem}，没法共用父类，覆盖原版方法的地方各写一遍、都转调这里。
 * <ul>
 *   <li>自带 MRU（{@code general.essential_tools.max_mru}，充能仓能充）；创造栏放空 / 满两份。</li>
 *   <li>挖掘速度：工具<b>自己</b>存的 MRU ≥ 每格价钱时，对「适用方块」（镐 {@code #mineable/pickaxe}、斧 {@code #mineable/axe}）是材料速度 12，
 *       否则 1。只看自己存的：客户端只知道物品 NBT，看不到身上别的储存物品的电，两端要算出同一个速度（同魔力挖掘器）。</li>
 *   <li>付费：每拆成一格适用方块（含中心）扣一格的价钱，先扣身上的储存物品、再扣工具自己（{@code MRUDrain.playerUseMRU}）；
 *       拆之前先 {@code MRUDrain.canAfford}，付不起时中心那格照原版规则挖掉、不扣、不连带。不适用的方块（拿镐挖泥土、拿斧挖石头）当普通工具用，不扣也不连带。</li>
 * </ul>
 */
public interface EssentialTool {

    /** 每拆成一格扣多少 MRU（镐 {@code mru_per_block}、斧 {@code mru_per_log}） */
    int mruCost();

    /** 这把工具的适用方块：只有这些方块按快速挖、付费、连带 */
    TagKey<Block> effectiveBlocks();

    /** 工具自己存的 MRU（没有能力时 0） */
    static int storedMRU(ItemStack stack) {
        return stack.getCapability(ECCapabilities.MRU_ITEM).map(IMRUHandlerItem::getMRU).orElse(0);
    }

    /** 快速状态：工具自己存的 MRU ≥ 一格的价钱（价钱为 0 时恒为真） */
    static boolean isCharged(ItemStack stack) {
        return stack.getItem() instanceof EssentialTool tool && storedMRU(stack) >= tool.mruCost();
    }

    /** 现在的 MRU 容量（物品建出来时取一次） */
    static int capacity() {
        return ECEssentialTools.Config.values().maxMru();
    }

    /**
     * 两件共用的提示行：电量、每格价钱、连带范围、潜行只挖一格、没电时的警告。
     *
     * @param prefix 各自的键前缀（{@code tooltip.essentialcraft5.essential_pickaxe} / {@code essential_axe}），下面接 {@code .cost / .area / .sneak}
     */
    static void appendTooltip(ItemStack stack, List<Component> tooltip, String prefix) {
        stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler ->
                tooltip.add(Component.translatable("tooltip.essentialcraft5.mru", handler.getMRU(), handler.getMaxMRU())
                        .withStyle(ChatFormatting.GRAY)));
        int cost = stack.getItem() instanceof EssentialTool tool ? tool.mruCost() : 0;
        tooltip.add(Component.translatable(prefix + ".cost", cost).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(prefix + ".area").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(prefix + ".sneak").withStyle(ChatFormatting.DARK_GRAY));
        if (!isCharged(stack)) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.essential_tools.slow", cost).withStyle(ChatFormatting.RED));
        }
    }

    /** 满 MRU 的一把（创造栏、测试、展示用） */
    static ItemStack full(Item item) {
        ItemStack full = new ItemStack(item);
        full.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(handler.getMaxMRU()));
        return full;
    }

    /** 存着 mru 的一把（测试用） */
    static ItemStack withMRU(Item item, int mru) {
        ItemStack stack = new ItemStack(item);
        stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(mru));
        return stack;
    }
}
