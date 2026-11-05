package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.demon.DemonStanding;
import com.flameforge.essentialcraft5.demon.DemonWant;
import com.flameforge.essentialcraft5.entity.DemonEntity;
import com.flameforge.essentialcraft5.menu.DemonTradeMenu;
import com.flameforge.essentialcraft5.registry.ECDemon;
import com.flameforge.essentialcraft5.registry.ECDemonic;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 和恶魔交易的界面（原作 GuiDemon，贴图 {@code gui/demon.png}，176×166）。
 * <p>
 * 原作把需求直接叠画在唯一那格上，和放进去的东西重叠，看不出放了多少；这里左边画一个半透明的幽灵格（需求和数量，悬停出物品提示），
 * 右边是原作那格（投放格），中间一个箭头；名字一行写「要：名字 × 数量」，会损坏的需求注明「要满耐久」，灵魂写实体名（被俘灵魂自己的显示名就带着）；
 * 下面一行写报酬范围。原作把名字画在 (5, 59)，这里两行文字放在 (8, 50)、(8, 61)：竖直方向免得第二行压到红色面板下沿的花边（y 72~75），
 * 横向和标题对齐、左右各留 8 像素（红色面板左右边框连金色花边占 4 像素，从 5 起画字离花边只隔 2 像素）。
 * 物品栏标题不画（原作也没有，落在花边上看不清）。
 * <p>
 * 第十三批 13a（契约之誉，batch13.md §3.4）：恶魔开两样要价时画两个幽灵格（{@link DemonTradeMenu#WANT_X_FIRST}、{@link DemonTradeMenu#WANT_X}），
 * 中间写「或」，名字一行写成「要：A × n，或 B × m」（放不下先截名字、数量留着，细节看悬停）；任意被俘灵魂写「任意被俘灵魂」。
 * 标题行右边写你自己的「契约之誉 23（契主）」，悬停列出三档各给什么、到了没有；标题按它剩下的宽度截断（英文到了血契那一档有 139 像素，
 * 恶魔被命名牌改了长名字时也不叠字）。到了契主时投放格右边写多给的经验「+50 经验」（接在报酬那行后面放不下，会把数字截掉），悬停写这一档给什么。
 * 契约之誉关着时都不画。
 */
public class DemonTradeScreen extends AbstractContainerScreen<DemonTradeMenu> {

    private static final ResourceLocation TEXTURE = EssentialCraft5.id("textures/gui/demon.png");
    private static final int TITLE_COLOR = 0xF2C46D;
    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int NOTE_COLOR = 0xFFB070;
    private static final int GHOST_OVERLAY = 0x708B8B8B;
    private static final int LINE_1_Y = 50;
    private static final int LINE_2_Y = 61;
    private static final int TEXT_X = 8;
    private static final int TEXT_WIDTH = 160;
    /** 契主多给的经验：原版经验数字的绿色 */
    private static final int XP_COLOR = 0x80FF20;
    /** 标题行右边写契约之誉，离右边框几像素 */
    private static final int STANDING_RIGHT = 8;

    public DemonTradeScreen(DemonTradeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
    }

    /** 每样要价的幽灵格横坐标（相对界面左上角）：一样画在 WANT_X，两样画在 WANT_X_FIRST 和 WANT_X */
    private static int ghostX(int index, int count) {
        return count >= 2 && index == 0 ? DemonTradeMenu.WANT_X_FIRST : DemonTradeMenu.WANT_X;
    }

    private int ownStanding() {
        return minecraft != null && minecraft.player != null ? DemonStanding.get(minecraft.player) : 0;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (!menu.getCarried().isEmpty()) {
            return;
        }
        List<DemonWant> wants = menu.wants();
        int gy = topPos + DemonTradeMenu.WANT_Y;
        for (int i = 0; i < wants.size(); i++) {
            DemonWant want = wants.get(i);
            int gx = leftPos + ghostX(i, wants.size());
            if (mouseX >= gx && mouseX < gx + 16 && mouseY >= gy && mouseY < gy + 16) {
                List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(minecraft, want.displayStack()));
                if (want.isAnySoul()) {
                    lines.add(Component.translatable("gui.essentialcraft5.demon_trade.any_soul_hint").withStyle(ChatFormatting.GOLD));
                }
                if (want.needsFullDurability()) {
                    lines.add(Component.translatable("gui.essentialcraft5.demon_trade.full_durability").withStyle(ChatFormatting.GOLD));
                }
                graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
                return;
            }
        }
        if (DemonStanding.enabled()) {
            int standing = ownStanding();
            Component label = DemonStanding.label(standing);
            int width = font.width(label);
            int lx = leftPos + imageWidth - STANDING_RIGHT - width;
            int ly = topPos + titleLabelY;
            if (mouseX >= lx && mouseX < lx + width && mouseY >= ly - 1 && mouseY < ly + 9) {
                graphics.renderComponentTooltip(font, standingTooltip(standing), mouseX, mouseY);
                return;
            }
            Component bonus = bonusXp(standing);
            if (bonus != null) {
                int bw = font.width(bonus);
                int bx = leftPos + imageWidth - TEXT_X - bw;
                int by = topPos + DemonTradeMenu.WANT_Y + 4;
                if (mouseX >= bx && mouseX < bx + bw && mouseY >= by - 1 && mouseY < by + 9) {
                    graphics.renderComponentTooltip(font, List.of(Component.translatable("gui.essentialcraft5.demon_standing.tier_line",
                            Component.translatable(DemonStanding.Tier.PATRON.key()), DemonStanding.Tier.PATRON.threshold(),
                            DemonStanding.perk(DemonStanding.Tier.PATRON)).withStyle(ChatFormatting.GREEN)), mouseX, mouseY);
                }
            }
        }
    }

    /** 到了契主、配置里多给经验时投放格右边那句「+50 经验」，否则 null */
    private static Component bonusXp(int standing) {
        return DemonStanding.atLeast(standing, DemonStanding.Tier.PATRON) && DemonStanding.Config.patronXp() > 0
                ? Component.translatable("gui.essentialcraft5.demon_trade.bonus_xp", DemonStanding.Config.patronXp())
                : null;
    }

    /** 悬停契约之誉：现在多少、三档各给什么（到了的绿色，没到的灰色）；到了血契时注明这只恶魔换没换过 */
    private List<Component> standingTooltip(int standing) {
        List<Component> lines = new ArrayList<>();
        lines.add(DemonStanding.fullLabel(standing).withStyle(ChatFormatting.GOLD));
        for (DemonStanding.Tier tier : DemonStanding.Tier.values()) {
            if (tier == DemonStanding.Tier.NONE) {
                continue;
            }
            boolean reached = DemonStanding.atLeast(standing, tier);
            lines.add(Component.translatable("gui.essentialcraft5.demon_standing.tier_line", Component.translatable(tier.key()),
                    tier.threshold(), DemonStanding.perk(tier)).withStyle(reached ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        }
        DemonEntity demon = menu.demon();
        if (demon != null && DemonStanding.atLeast(standing, DemonStanding.Tier.BLOOD) && demon.hasRerolled()) {
            lines.add(Component.translatable("message.essentialcraft5.demon_standing.blood.used").withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        List<DemonWant> wants = menu.wants();
        int count = Math.max(1, wants.size());
        int gy = topPos + DemonTradeMenu.WANT_Y;
        // 幽灵格的框借投放格的框（贴图上唯一那格，(79, 29) 起 18×18）
        for (int i = 0; i < count; i++) {
            int gx = leftPos + ghostX(i, count);
            graphics.blit(TEXTURE, gx - 1, gy - 1, DemonTradeMenu.SLOT_X - 1, DemonTradeMenu.SLOT_Y - 1, 18, 18);
        }
        graphics.drawString(font, "»", leftPos + DemonTradeMenu.WANT_X + 19, gy + 4, TITLE_COLOR, false);
        if (count >= 2) {
            Component or = Component.translatable("gui.essentialcraft5.demon_trade.or");
            int gapLeft = DemonTradeMenu.WANT_X_FIRST + 17;
            int gapRight = DemonTradeMenu.WANT_X - 1;
            graphics.drawString(font, or, leftPos + gapLeft + (gapRight - gapLeft - font.width(or)) / 2, gy + 4, TITLE_COLOR, false);
        }
        for (int i = 0; i < wants.size(); i++) {
            int gx = leftPos + ghostX(i, wants.size());
            ItemStack shown = wants.get(i).displayStack();
            graphics.renderFakeItem(shown, gx, gy);
            // 半透明蒙一层表示「这是要的东西，不是格子里的」，数量画在蒙层上面
            graphics.pose().pushPose();
            graphics.pose().translate(0F, 0F, 180F);
            graphics.fill(gx, gy, gx + 16, gy + 16, GHOST_OVERLAY);
            graphics.pose().popPose();
            graphics.renderItemDecorations(font, shown, gx, gy);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        int standing = ownStanding();
        int titleRoom = TEXT_WIDTH;
        if (DemonStanding.enabled()) {
            Component label = DemonStanding.label(standing);
            int labelWidth = font.width(label);
            graphics.drawString(font, label, imageWidth - STANDING_RIGHT - labelWidth, titleLabelY, NOTE_COLOR, false);
            // 标题和契约之誉之间至少空一个字
            titleRoom = imageWidth - STANDING_RIGHT - labelWidth - 6 - titleLabelX;
            Component bonus = bonusXp(standing);
            if (bonus != null) {
                graphics.drawString(font, bonus, imageWidth - TEXT_X - font.width(bonus), DemonTradeMenu.WANT_Y + 4, XP_COLOR, false);
            }
        }
        drawFitted(graphics, title, titleLabelX, titleLabelY, titleRoom, TITLE_COLOR);
        List<DemonWant> wants = menu.wants();
        if (wants.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.essentialcraft5.demon_trade.nothing"), TEXT_X, LINE_1_Y, TEXT_COLOR, false);
            return;
        }
        Function<List<Component>, Component> build;
        List<Component> names;
        if (wants.size() >= 2) {
            DemonWant a = wants.get(0);
            DemonWant b = wants.get(1);
            names = List.of(name(a), name(b));
            build = n -> Component.translatable("gui.essentialcraft5.demon_trade.want_either", n.get(0), a.count(), n.get(1), b.count());
        } else {
            DemonWant want = wants.get(0);
            names = List.of(name(want));
            build = n -> {
                MutableComponent line = Component.translatable("gui.essentialcraft5.demon_trade.want", n.get(0), want.count());
                if (want.needsFullDurability()) {
                    line.append(Component.translatable("gui.essentialcraft5.demon_trade.full_durability_short").withStyle(s -> s.withColor(NOTE_COLOR)));
                }
                return line;
            };
        }
        drawFitted(graphics, fitNames(names, build), TEXT_X, LINE_1_Y, TEXT_WIDTH, TEXT_COLOR);
        Component reward = Component.translatable("gui.essentialcraft5.demon_trade.reward",
                ECDemonic.ACKRONITE_INGOT.get().getDescription(), ECDemon.Config.rewardMin(), ECDemon.Config.rewardMax());
        drawFitted(graphics, reward, TEXT_X, LINE_2_Y, TEXT_WIDTH, NOTE_COLOR);
    }

    /** 要价那一行写的名字：任意被俘灵魂单独一个说法，别的照物品显示名（灵魂带实体名） */
    private static Component name(DemonWant want) {
        return want.isAnySoul() ? Component.translatable("gui.essentialcraft5.demon_trade.any_soul") : want.displayStack().getHoverName();
    }

    /**
     * 要价那一行放不下时先截名字：每次把最宽的那个名字去掉一个字、加省略号，数量和「要满耐久」留着
     * （整行从尾巴截会把「× 3」截掉，英文的「Captured Soul (Villager)」就放不下数量）。名字截到只剩一个字还放不下，交给 {@link #drawFitted}。
     */
    private Component fitNames(List<Component> names, Function<List<Component>, Component> build) {
        Component line = build.apply(names);
        if (font.width(line) <= TEXT_WIDTH) {
            return line;
        }
        List<String> cut = new ArrayList<>();
        for (Component name : names) {
            cut.add(name.getString());
        }
        while (true) {
            int widest = 0;
            for (int i = 1; i < cut.size(); i++) {
                if (font.width(cut.get(i)) > font.width(cut.get(widest))) {
                    widest = i;
                }
            }
            String s = cut.get(widest);
            if (s.length() <= 1) {
                return line;
            }
            cut.set(widest, s.substring(0, s.offsetByCodePoints(s.length(), -1)).stripTrailing());
            List<Component> shown = new ArrayList<>();
            for (int i = 0; i < cut.size(); i++) {
                String text = cut.get(i).length() < names.get(i).getString().length() ? cut.get(i) + "…" : cut.get(i);
                shown.add(Component.literal(text).withStyle(names.get(i).getStyle()));
            }
            line = build.apply(shown);
            if (font.width(line) <= TEXT_WIDTH) {
                return line;
            }
        }
    }

    /** 一行放不下 width 时截断加省略号 */
    private void drawFitted(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        if (font.width(text) <= width) {
            graphics.drawString(font, text, x, y, color, false);
            return;
        }
        String cut = font.plainSubstrByWidth(text.getString(), Math.max(0, width - font.width("…"))) + "…";
        graphics.drawString(font, cut, x, y, color, false);
    }
}
