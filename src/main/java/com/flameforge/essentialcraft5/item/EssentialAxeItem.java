package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.mru.MRUItemStorage;
import com.flameforge.essentialcraft5.registry.ECEssentialTools;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 源质斧（batch12.md §4.4）：砍倒整棵天然树。共用规则（MRU、速度、付费）见 {@link EssentialTool}，找树和砍树见 {@link EssentialToolEvents}。
 * <ul>
 *   <li>砍的是 {@code #minecraft:logs} 里的方块（含菌柄）时，从这一根往上、往四周找和它<b>同一种</b>的原木（26 邻接，水平 {@code felling_radius}、
 *       往上 {@code felling_height}、最多看 {@code max_scan} 格、最多 {@code max_logs} 根，近的先收）。</li>
 *   <li>这些原木挨着的天然树叶（不是玩家放的）和下界疣块 / 诡异疣块合计不到 {@code leaves_required} 片时，只砍这一根：
 *       玩家用原木盖的房子、柱子周围没有天然树叶，不会被一斧子拆掉。</li>
 *   <li>整棵树分几刻砍完（每棵每刻 {@code logs_per_tick}、全服每刻 {@code logs_per_tick_total}），掉落物都挪到你砍的那一格；树叶留给原版自己腐烂。</li>
 *   <li>潜行只砍一根；创造模式不连带；同一个人一棵树没砍完时只砍这一根。右键去皮照原版。</li>
 * </ul>
 */
public class EssentialAxeItem extends AxeItem implements EssentialTool, CreativeVariants {

    public EssentialAxeItem(Tier tier, float attackModifier, float attackSpeed, Properties properties) {
        super(tier, attackModifier, attackSpeed, properties);
    }

    @Override
    public int mruCost() {
        return ECEssentialTools.Config.values().mruPerLog();
    }

    @Override
    public TagKey<Block> effectiveBlocks() {
        return BlockTags.MINEABLE_WITH_AXE;
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

    /** 同 {@link EssentialPickaxeItem#mineBlock}：只登记待办，本 tick 末尾再扣钱、找树 */
    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity entity) {
        EssentialToolEvents.onToolMined(stack, level, state, pos, entity);
        return true;
    }

    @Override
    public boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack) {
        return !newStack.is(oldStack.getItem()) || EssentialTool.isCharged(oldStack) != EssentialTool.isCharged(newStack)
                || !oldStack.getAllEnchantments().equals(newStack.getAllEnchantments());
    }

    @Override
    public boolean canBeDepleted() {
        return false;
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        EssentialTool.appendTooltip(stack, tooltip, "tooltip.essentialcraft5.essential_axe");
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !oldStack.is(newStack.getItem());
    }

    @Override
    public List<ItemStack> creativeVariants() {
        return List.of(new ItemStack(this), EssentialTool.full(this));
    }
}
