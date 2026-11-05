package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.client.CrafterClient;
import com.flameforge.essentialcraft5.menu.CraftingFrameMenu;
import com.flameforge.essentialcraft5.menu.HeldItemMenu;
import com.flameforge.essentialcraft5.recipe.CrafterGrid;
import com.flameforge.essentialcraft5.recipe.CraftingPattern;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 合成框架（原作 ItemCraftingFrame + InventoryCraftingFrame）：右键打开 3×3 样板（{@link CraftingFrameMenu}），装进魔力合成器以后合成器只收样板要的原料。
 * 数据格式见 {@link CraftingPattern}。叠 1 个（原作同）。
 * <p>
 * 修的原作问题：用 y = −2 当记号开界面、{@code onUpdate} 每 tick 把界面内容写进<b>主手</b>物品（副手打开时改的是主手那件）、
 * 往没有 NBT 的框架写随机 uniqueID——这些都由 {@link HeldItemMenu} 的「点一下立刻写回打开时那只手里的那件」取代。
 */
public class CraftingFrameItem extends Item {

    public CraftingFrameItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            HeldItemMenu.open(serverPlayer, hand, stack.getHoverName(), CraftingFrameMenu::new);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ItemStack[] samples = CraftingPattern.samples(stack);
        if (CraftingPattern.allEmpty(samples)) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.crafting_frame.empty").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.essentialcraft5.crafting_frame.use").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.add(resultLine(stack, samples, level));
        tooltip.add(Component.translatable("tooltip.essentialcraft5.crafting_frame.mode",
                Component.translatable(CraftingPattern.mode(stack).translationKey())).withStyle(ChatFormatting.GRAY));
        // 按住 Shift 列出 9 格（按键状态只在客户端有；提示框本来也只在客户端画）
        Boolean shift = DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> CrafterClient::hasShiftDown);
        if (Boolean.TRUE.equals(shift)) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.crafting_frame.recipe").withStyle(ChatFormatting.GRAY));
            for (int i = 0; i < samples.length; i++) {
                Component cell = Component.literal(String.valueOf(i + 1));
                tooltip.add((samples[i].isEmpty()
                        ? Component.translatable("tooltip.essentialcraft5.crafting_frame.empty_slot", cell)
                        : Component.translatable("tooltip.essentialcraft5.crafting_frame.slot", cell, samples[i].getHoverName()))
                        .withStyle(ChatFormatting.DARK_AQUA));
            }
        } else {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.crafting_frame.shift").withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("tooltip.essentialcraft5.crafting_frame.use").withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * 「产物：X」：按记下的配方现查（不信存档里的产物）。只有按 ID 找不到配方时才写红字「配方已不存在」。
     * 特殊配方（药箭、修复物品、染色皮甲、复制成书等原版 {@code CustomRecipe}，以及模组自己写的）的 {@code getResultItem} 恒为空，
     * 产物要按样本现算：用样本拼一张 3×3、{@code matches} 通过就拿 {@code assemble} 的结果；还是算不出来（比如样本是只留了哈希的大 NBT 物品）
     * 写中性的「产物：按原料现算」——框架照样有效，装进合成器能合成（4a 审查 CRAFT-3）。
     */
    private static Component resultLine(ItemStack stack, ItemStack[] samples, @Nullable Level level) {
        ResourceLocation id = CraftingPattern.recipeId(stack);
        if (id == null) {
            return Component.translatable("tooltip.essentialcraft5.crafting_frame.no_result").withStyle(ChatFormatting.GRAY);
        }
        if (level == null) {
            return Component.translatable("tooltip.essentialcraft5.crafting_frame.result", id.toString()).withStyle(ChatFormatting.GRAY);
        }
        CraftingRecipe recipe = CraftingPattern.craftingRecipe(level.getRecipeManager(), id);
        if (recipe == null) {
            return Component.translatable("tooltip.essentialcraft5.crafting_frame.missing").withStyle(ChatFormatting.RED);
        }
        ItemStack result = ItemStack.EMPTY;
        try {
            result = recipe.getResultItem(level.registryAccess());
            if (result.isEmpty()) {
                CrafterGrid grid = new CrafterGrid(samples);
                if (recipe.matches(grid, level)) {
                    result = recipe.assemble(grid, level.registryAccess());
                }
            }
        } catch (RuntimeException e) {
            // 别的模组的配方在样本上抛异常：提示框每帧都画，不记日志，按「算不出来」显示
            result = ItemStack.EMPTY;
        }
        if (result.isEmpty()) {
            return Component.translatable("tooltip.essentialcraft5.crafting_frame.result_special").withStyle(ChatFormatting.GRAY);
        }
        Component name = result.getCount() > 1
                ? Component.translatable("tooltip.essentialcraft5.crafting_frame.result_count", result.getHoverName(), result.getCount())
                : result.getHoverName();
        return Component.translatable("tooltip.essentialcraft5.crafting_frame.result", name).withStyle(ChatFormatting.GRAY);
    }
}
