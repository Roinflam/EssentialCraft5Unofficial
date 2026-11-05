package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.MatrixRanks;
import com.flameforge.essentialcraft5.client.guide.Figures;
import com.flameforge.essentialcraft5.item.attunement.Attunement;
import com.flameforge.essentialcraft5.item.attunement.AttunementConfig;
import com.flameforge.essentialcraft5.item.attunement.AttunementFamily;
import com.flameforge.essentialcraft5.item.attunement.AttunementStageLines;
import com.flameforge.essentialcraft5.player.AttunementBudget;
import com.flameforge.essentialcraft5.recipe.AttunementAscensionRecipe;
import com.flameforge.essentialcraft5.recipe.MagicianTableRecipe;
import com.flameforge.essentialcraft5.registry.ECRecipes;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 同调装备的提示框（batch13.md §3.3.5）：
 * <pre>
 * 同调 ◆◆◇◇◇（2 / 5 阶）
 * 同调度 650 / 1 600
 *  1 阶 ……（各家族的阶效果：解锁了白字、没解锁灰字）
 * 预算还能长 312 点，存满要在线约 2 小时
 * 按住 Shift 看下一阶要什么
 * </pre>
 * Shift：升到下一阶要的矩阵阶（和你现在几阶）、阶戳不够时提醒穿上 / 拿着一会儿、魔力工作台四角要放什么（从配方表里找）。
 * <p>
 * 各家族的阶效果写成语言键 {@code tooltip.<命名空间>.attunement.<家族>.stage_<n>}（有这个键才画那一行），由实现阶效果的模块写进自己的语言文件；
 * 数能在配置里改的家族，物品实现 {@link AttunementStageLines}，按配置填好参数再给出来。
 * 这里只管排版。每阶通用加成（最大生命、攻击倍率）是属性修饰符，原版的「穿在身上时」一栏自己会列。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class AttunementTooltips {

    private static final String PREFIX = "tooltip.essentialcraft5.attunement.";

    private AttunementTooltips() {
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onAttunementTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        AttunementFamily family = AttunementFamily.of(stack);
        if (family == null) {
            return;
        }
        List<Component> lines = lines(stack, family, event.getEntity(), Screen.hasShiftDown());
        int at = Math.min(1, event.getToolTip().size());
        event.getToolTip().addAll(at, lines);
    }

    /** 第 n 阶的效果：物品自己按配置填数（{@link AttunementStageLines}），否则照语言键原样写；都没有返回 null */
    @Nullable
    private static Component stageEffect(ItemStack stack, String effectPrefix, int n) {
        if (stack.getItem() instanceof AttunementStageLines own) {
            return own.attunementStageLine(stack, n);
        }
        String key = effectPrefix + n;
        return I18n.exists(key) ? Component.translatable(key) : null;
    }

    /** 提示框里同调这几行（展示厅、测试也能直接调） */
    public static List<Component> lines(ItemStack stack, AttunementFamily family, @Nullable Player player, boolean shift) {
        List<Component> lines = new ArrayList<>();
        int stage = Attunement.stage(stack);
        int max = family.maxStage();
        lines.add(Component.translatable(PREFIX + "stage", diamonds(stage, max), stage, max).withStyle(ChatFormatting.LIGHT_PURPLE));
        if (stage < max) {
            int need = Attunement.need(family, stage);
            int points = Attunement.points(stack);
            lines.add(Component.translatable(points >= need ? PREFIX + "points_full" : PREFIX + "points",
                    Figures.number(points), Figures.number(need)).withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable(PREFIX + "maxed").withStyle(ChatFormatting.GRAY));
        }
        String effectPrefix = "tooltip." + family.id().getNamespace() + ".attunement." + family.id().getPath() + ".stage_";
        for (int n = 1; n <= max; n++) {
            Component effect = stageEffect(stack, effectPrefix, n);
            if (effect != null) {
                lines.add(Component.translatable(PREFIX + "effect", n, effect)
                        .withStyle(n <= stage ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY));
            }
        }
        if (!family.active()) {
            lines.add(Component.translatable(PREFIX + "disabled").withStyle(ChatFormatting.DARK_GRAY));
            return lines;
        }
        if (stage >= max) {
            return lines;
        }
        if (player != null) {
            float budget = AttunementBudget.get(player, family.kind().budget());
            Component line = switch (AttunementBudget.line(budget, AttunementConfig.budgetCap(), AttunementConfig.budgetPerHour())) {
                case FULL -> Component.translatable(PREFIX + "budget_full", Figures.number((int) budget));
                case STATIC -> Component.translatable(PREFIX + "budget_static", Figures.number((int) budget));
                case FILLING -> Component.translatable(PREFIX + "budget", Figures.number((int) budget), duration(AttunementBudget.secondsToFull(budget)));
            };
            lines.add(line.copy().withStyle(ChatFormatting.DARK_AQUA));
        }
        if (!shift) {
            lines.add(Component.translatable(PREFIX + "shift").withStyle(ChatFormatting.DARK_GRAY));
            return lines;
        }
        lines.add(Component.translatable(PREFIX + "next", stage + 1).withStyle(ChatFormatting.GOLD));
        int rank = AttunementConfig.requiredRank(stage + 1);
        if (rank > 0) {
            int own = MatrixRanks.get(player);
            lines.add(Component.translatable(PREFIX + "next_rank", rank, own)
                    .withStyle(own >= rank ? ChatFormatting.GREEN : ChatFormatting.RED));
            if (own >= rank && Attunement.rankStamp(stack) < rank) {
                lines.add(Component.translatable(PREFIX + "stamp_hint").withStyle(ChatFormatting.GRAY));
            }
        }
        AttunementAscensionRecipe recipe = findRecipe(stack, family, stage);
        if (recipe == null) {
            lines.add(Component.translatable(PREFIX + "next_unknown").withStyle(ChatFormatting.GRAY));
            return lines;
        }
        lines.add(Component.translatable(PREFIX + "next_corners").withStyle(ChatFormatting.GRAY));
        for (int i = 1; i < MagicianTableRecipe.INPUTS; i++) {
            Ingredient ingredient = recipe.getIngredients().get(i);
            if (ingredient.isEmpty()) {
                continue;
            }
            ItemStack[] options = ingredient.getItems();
            if (options.length == 0) {
                continue;
            }
            MutableComponent name = options[0].getHoverName().copy();
            if (options.length > 1) {
                name = Component.translatable(PREFIX + "any_of", name);
            }
            lines.add(Component.translatable(PREFIX + "corner", name, recipe.countAt(i)).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable(PREFIX + "next_mru", Figures.number(recipe.getMRU())).withStyle(ChatFormatting.GRAY));
        return lines;
    }

    /** ◆◆◇◇◇ */
    static String diamonds(int stage, int max) {
        StringBuilder builder = new StringBuilder(max);
        for (int i = 0; i < max; i++) {
            builder.append(i < stage ? '◆' : '◇');
        }
        return builder.toString();
    }

    /** 「约 2 小时」「约 40 分钟」 */
    static Component duration(long seconds) {
        if (seconds >= 3600L) {
            return Component.translatable(PREFIX + "hours", Figures.number(Math.round(seconds / 360F) / 10F));
        }
        return Component.translatable(PREFIX + "minutes", Math.max(1L, (seconds + 59L) / 60L));
    }

    /** 客户端配方表里这件这一阶的升阶配方（部位对得上的第一条）；没有返回 null */
    @Nullable
    static AttunementAscensionRecipe findRecipe(ItemStack stack, AttunementFamily family, int stage) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        EquipmentSlot slot = AttunementFamily.slotOf(stack, family.kind());
        for (MagicianTableRecipe recipe : minecraft.level.getRecipeManager().getAllRecipesFor(ECRecipes.MAGICIAN_TABLE.get())) {
            if (recipe instanceof AttunementAscensionRecipe ascension && ascension.family().equals(family.id())
                    && ascension.fromStage() == stage && (ascension.parts().isEmpty() || ascension.parts().contains(slot))) {
                return ascension;
            }
        }
        return null;
    }
}
