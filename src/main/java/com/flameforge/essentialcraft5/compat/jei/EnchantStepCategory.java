package com.flameforge.essentialcraft5.compat.jei;

import com.flameforge.essentialcraft5.client.guide.Figures;
import com.flameforge.essentialcraft5.enchant.EnchantUpgrades;
import com.flameforge.essentialcraft5.registry.ECEnchantUpgrade;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 附魔增幅台 / 升华台：这两台不走配方，每升一级的价格按等级算（{@link EnchantUpgrades#step}）。JEI 里一级一页：
 * 书（n−1 级）+ 材料一 + 材料二 → 书（n 级），下面写 MRU、ESPE、用时、矩阵损伤，译文和机器界面上「下一步」提示框是同一套。
 * <p>
 * 书格轮流显示这一级收的每种附魔（配置压低上限、不收的附魔不出现），左右两本书联动、显示的是同一种附魔。
 * 价格在翻到这一页时才算，改了配置里的倍率，重新打开 JEI 就是新价格（不用重进世界）。
 */
final class EnchantStepCategory implements IRecipeCategory<EnchantStepCategory.Upgrade> {

    /** 一页：哪台机器、推到第几级 */
    record Upgrade(EnchantUpgrades.Tier tier, int level) {
    }

    private static final String KEY = "gui.essentialcraft5.enchant_upgrade.";
    /** 英文的 MRU 一行（「MRU: 11,250,000 (up to 2,084 per tick)」）和矩阵损伤一行都长，宽度取 JEI 常见的上限 */
    private static final int W = 176;
    private static final int TITLE_Y = 0;
    private static final int SLOT_Y = 13;
    private static final int BOOK_X = 9;
    private static final int A_X = BOOK_X + 22;
    private static final int B_X = A_X + 18;
    private static final int ARROW_X = B_X + 24;
    private static final int OUT_X = ARROW_X + JeiDraw.ARROW_W + 8;
    private static final int NOTE_Y = SLOT_Y + 22;
    /** 升华台最多四行（MRU、ESPE、用时、矩阵损伤），英文时 MRU 和矩阵损伤可能各折成两行 */
    private static final int NOTE_LINES = 6;
    private static final int H = NOTE_Y + NOTE_LINES * JeiDraw.LINE_H;

    private final RecipeType<Upgrade> type;
    private final EnchantUpgrades.Tier tier;
    private final Block station;
    private final JeiDraw draw;
    private final IDrawable background;
    private final IDrawable icon;

    EnchantStepCategory(IGuiHelper gui, RecipeType<Upgrade> type, EnchantUpgrades.Tier tier) {
        this.type = type;
        this.tier = tier;
        station = tier == EnchantUpgrades.Tier.AMPLIFIER ? ECEnchantUpgrade.ENCHANT_AMPLIFIER.get() : ECEnchantUpgrade.ENCHANT_ASCENDER.get();
        draw = new JeiDraw(gui);
        background = gui.createBlankDrawable(W, H);
        icon = gui.createDrawableItemStack(new ItemStack(station));
    }

    /** 这台机器每一级一页；一种附魔都推不到这一级的（配置全压在下面）不出这一页 */
    static List<Upgrade> steps(EnchantUpgrades.Tier tier) {
        List<Upgrade> out = new ArrayList<>();
        for (int n = tier.minLevel + 1; n <= tier.cap; n++) {
            if (!enchantments(tier, n).isEmpty()) {
                out.add(new Upgrade(tier, n));
            }
        }
        return out;
    }

    /** 这台机器能推到 n 级的附魔，按注册名排（JEI 里轮流显示的顺序固定） */
    private static List<Enchantment> enchantments(EnchantUpgrades.Tier tier, int n) {
        List<Map.Entry<ResourceLocation, Enchantment>> found = new ArrayList<>();
        for (Map.Entry<net.minecraft.resources.ResourceKey<Enchantment>, Enchantment> entry : ForgeRegistries.ENCHANTMENTS.getEntries()) {
            if (EnchantUpgrades.cap(entry.getValue(), tier) >= n) {
                found.add(Map.entry(entry.getKey().location(), entry.getValue()));
            }
        }
        found.sort(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)));
        List<Enchantment> out = new ArrayList<>(found.size());
        for (Map.Entry<ResourceLocation, Enchantment> entry : found) {
            out.add(entry.getValue());
        }
        return out;
    }

    @Override
    public RecipeType<Upgrade> getRecipeType() {
        return type;
    }

    @Override
    public Component getTitle() {
        return station.getName();
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Upgrade upgrade, IFocusGroup focuses) {
        int n = upgrade.level();
        List<ItemStack> before = new ArrayList<>();
        List<ItemStack> after = new ArrayList<>();
        for (Enchantment enchantment : enchantments(tier, n)) {
            before.add(EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantment, n - 1)));
            after.add(EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantment, n)));
        }
        EnchantUpgrades.Step step = EnchantUpgrades.step(tier, n);
        IRecipeSlotBuilder in = draw.slot(builder, RecipeIngredientRole.INPUT, BOOK_X, SLOT_Y).addItemStacks(before);
        draw.slot(builder, RecipeIngredientRole.INPUT, A_X, SLOT_Y).addItemStacks(stacks(step.a()));
        draw.slot(builder, RecipeIngredientRole.INPUT, B_X, SLOT_Y).addItemStacks(stacks(step.b()));
        IRecipeSlotBuilder out = draw.slot(builder, RecipeIngredientRole.OUTPUT, OUT_X, SLOT_Y).addItemStacks(after);
        // 左右两本书一起轮换：左边是锋利 XI，右边就是锋利 XII
        builder.createFocusLink(in, out);
    }

    @Override
    public void draw(Upgrade upgrade, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        int n = upgrade.level();
        Font font = JeiDraw.font();
        Component title = Component.translatable(KEY + "next",
                Component.translatable("enchantment.level." + (n - 1)), Component.translatable("enchantment.level." + n));
        JeiDraw.centered(g, title, W, TITLE_Y, JeiDraw.TEXT);
        // 书和材料之间空一点，看得出左边那格放书
        g.fill(BOOK_X + 18, SLOT_Y + 3, BOOK_X + 19, SLOT_Y + 13, 0xFF8B8B8B);
        draw.arrow(g, ARROW_X, SLOT_Y - 1);
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (Component note : notes(EnchantUpgrades.step(tier, n))) {
            lines.addAll(font.split(note, W - 4));
        }
        for (int i = 0; i < lines.size() && i < NOTE_LINES; i++) {
            FormattedCharSequence line = lines.get(i);
            g.drawString(font, line, (W - font.width(line)) / 2, NOTE_Y + i * JeiDraw.LINE_H, JeiDraw.TEXT, false);
        }
    }

    /** 同机器界面「下一步」提示框里的价格几行（材料已经画成格子，这里不重复） */
    private static List<Component> notes(EnchantUpgrades.Step step) {
        List<Component> notes = new ArrayList<>();
        notes.add(Component.translatable(KEY + "step.mru", Figures.whole(step.mru()), Figures.whole(step.mruPerTick())));
        if (step.espe() > 0) {
            notes.add(Component.translatable(KEY + "step.espe", Figures.whole(step.espe())));
        }
        notes.add(Component.translatable(KEY + "step.time", Figures.number(step.ticks() / 20F)));
        if (step.matrix() > 0) {
            notes.add(Component.translatable(KEY + "step.matrix", Figures.whole(step.matrix())).withStyle(ChatFormatting.DARK_PURPLE));
        }
        return notes;
    }

    /** 一格材料：可选的几种物品，每种都显示成要的数量 */
    private static List<ItemStack> stacks(EnchantUpgrades.Need need) {
        List<ItemStack> out = new ArrayList<>();
        for (Item item : need.any()) {
            ItemStack stack = new ItemStack(item, Math.max(1, need.count()));
            if (!stack.isEmpty()) {
                out.add(stack);
            }
        }
        return out;
    }
}
