package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.mru.MRUItemStorage;
import com.flameforge.essentialcraft5.registry.ECEssentialTools;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 源质镐（batch12.md §4.4）：按挖的那一面开 3×3。共用规则（MRU、速度、付费）见 {@link EssentialTool}，连带挖见 {@link EssentialToolEvents}。
 * <ul>
 *   <li>面朝哪边：服务端记下玩家最后一次左键的格子和面，挖掉的正是那一格就用那个面，否则用视线射线打中的面（D10 按挖的那一面）；
 *       挖地板、天花板时就是水平的 3×3。</li>
 *   <li>邻格要是镐的适用方块、用这把镐挖得出掉落、硬度在 0 到「中心硬度 + {@code hardness_margin}」之间、没有方块实体、
 *       不在 {@code essentialcraft5:essential_tools/no_area} 里；中心那格本身也要是镐的适用方块才连带。每次最多 8 格。</li>
 *   <li>潜行只挖一格；创造模式不连带（创造模式拆方块不走 {@code mineBlock}）。</li>
 * </ul>
 */
public class EssentialPickaxeItem extends PickaxeItem implements EssentialTool, CreativeVariants {

    public EssentialPickaxeItem(Tier tier, int attackModifier, float attackSpeed, Properties properties) {
        super(tier, attackModifier, attackSpeed, properties);
    }

    @Override
    public int mruCost() {
        return ECEssentialTools.Config.values().mruPerBlock();
    }

    @Override
    public TagKey<Block> effectiveBlocks() {
        return BlockTags.MINEABLE_WITH_PICKAXE;
    }

    @Override
    public @Nullable ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new MRUItemStorage(stack, EssentialTool.capacity());
    }

    /** 快速状态下适用方块 12（材料速度），其余 1 */
    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return EssentialTool.isCharged(stack) ? super.getDestroySpeed(stack, state) : 1F;
    }

    /**
     * 服务端玩家挖掉一格（破坏事件已放行、方块还没移除）时由原版调用：登记一个待办，本 tick 末尾再扣中心那格的钱、连带挖邻格，
     * 理由见 {@link EssentialToolEvents} 的类注释。连带挖自己调的 destroyBlock 也会走到这里，那时什么都不做。不调 super：super 扣耐久。
     */
    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity entity) {
        EssentialToolEvents.onToolMined(stack, level, state, pos, entity);
        return true;
    }

    /** 挖的过程中 MRU 变了不重置挖掘进度；换了物品、快慢状态变了、魔咒变了才重置（同魔力挖掘器） */
    @Override
    public boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack) {
        return !newStack.is(oldStack.getItem()) || EssentialTool.isCharged(oldStack) != EssentialTool.isCharged(newStack)
                || !oldStack.getAllEnchantments().equals(newStack.getAllEnchantments());
    }

    /** 没有耐久：挖方块、打怪都不掉，不显示耐久条，也附不上耐久、经验修补 */
    @Override
    public boolean canBeDepleted() {
        return false;
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    /** 没有耐久的物品原版默认不能附魔：这里放开，效率、时运、精准采集照常能附（时运、精准采集对连带挖的每一格都有效） */
    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        EssentialTool.appendTooltip(stack, tooltip, "tooltip.essentialcraft5.essential_pickaxe");
    }

    /** MRU 变化不触发重新举起的动画（每挖一格 NBT 都会变） */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !oldStack.is(newStack.getItem());
    }

    @Override
    public List<ItemStack> creativeVariants() {
        return List.of(new ItemStack(this), EssentialTool.full(this));
    }
}
