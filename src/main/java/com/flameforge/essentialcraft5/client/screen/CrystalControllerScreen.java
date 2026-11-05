package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.api.IElementalCrystal;
import com.flameforge.essentialcraft5.blockentity.CrystalControllerBlockEntity;
import com.flameforge.essentialcraft5.blockentity.CrystalControllerBlockEntity.Status;
import com.flameforge.essentialcraft5.client.screen.element.ScreenElement;
import com.flameforge.essentialcraft5.item.EssenceItem;
import com.flameforge.essentialcraft5.menu.CrystalControllerMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 水晶控制器界面，元素与坐标照搬原作 GuiCrystalController：MRU 存量条 (7, 4)、绑定宝石状态 (25, 4)、
 * MRU 数值 (25, 58)、水晶状态面板 (25, 22)（原作 GuiCrystalState）。
 * <p>
 * 面板和原作一样两列三行：火 / 水 / 地，风 / 大小 / 几率。原作面板约 100 像素宽、右列从 +50 起，中文和两位数一长就会叠字，
 * 这里面板加宽到精华槽左边（112 像素），右列按左列最宽的那行往后排，但不超出面板右边（再往右就画到精华槽上了）。
 * 英文文字按这个宽度写得紧：冒号后不空格、几率写 Rate，最宽时左列「Water:100%」54、右列「Size:100%」46 像素，正好放得下。
 * 原作「几率」写的是 2 × (稀有度 + 1)%，实际是每 tick 0.1% × (稀有度 + 1)，这里显示真实值；悬停看状态和说明。
 * 旁边没有水晶时面板中间写状态（原作 MRU 为 0 时整个面板都不画）。
 */
public class CrystalControllerScreen extends MachineScreen<CrystalControllerMenu> {

    private static final String KEY = "gui.essentialcraft5.crystal_controller.";

    public CrystalControllerScreen(CrystalControllerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        elements.add(mruStorage(7, 4));
        elements.add(boundGemState(25, 4));
        elements.add(mruState(25, 58));
        elements.add(new CrystalPanel(25, 22));
    }

    /** 百分数：最多两位小数、去掉末尾的 0（0.10 → 0.1） */
    static String percent(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /** 原作 GuiCrystalState：slot_common.png 拼出来的两行高面板（36 像素）加文字 */
    private final class CrystalPanel implements ScreenElement {

        private static final int WIDTH = 112;
        private static final int HEIGHT = 36;

        private final int x;
        private final int y;

        CrystalPanel(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public int x() {
            return x;
        }

        @Override
        public int y() {
            return y;
        }

        @Override
        public int width() {
            return WIDTH;
        }

        @Override
        public int height() {
            return HEIGHT;
        }

        @Override
        public void render(GuiGraphics graphics, int left, int top) {
            drawPanel(graphics, left, top);
            Font font = Minecraft.getInstance().font;
            if (!menu.hasCrystal()) {
                // 英文「No Elemental Crystal next to it」比面板宽 40 多像素，会压到右边的格子：按面板宽折行，整段竖直居中
                List<FormattedCharSequence> lines = font.split(Status.NO_CRYSTAL.label(), WIDTH - 6);
                int lineY = top + (HEIGHT - lines.size() * 10 + 2) / 2;
                for (FormattedCharSequence line : lines) {
                    graphics.drawString(font, line, left + (WIDTH - font.width(line)) / 2, lineY, 0xFFFFFF, true);
                    lineY += 10;
                }
                return;
            }
            List<Component> leftColumn = List.of(
                    elementLine(IElementalCrystal.Element.FIRE),
                    elementLine(IElementalCrystal.Element.WATER),
                    elementLine(IElementalCrystal.Element.EARTH));
            List<Component> rightColumn = new ArrayList<>(3);
            rightColumn.add(elementLine(IElementalCrystal.Element.AIR));
            rightColumn.add(Component.translatable("gui.essentialcraft5.crystal.size", menu.size()));
            if (menu.essence().getItem() instanceof EssenceItem) {
                rightColumn.add(Component.translatable("gui.essentialcraft5.crystal.chance", percent(menu.chancePercent())));
            }
            int widest = 0;
            for (int i = 0; i < leftColumn.size(); i++) {
                graphics.drawString(font, leftColumn.get(i), left + 2, top + 4 + i * 10, 0xFFFFFF, true);
                widest = Math.max(widest, font.width(leftColumn.get(i)));
            }
            int rightWidest = 0;
            for (Component line : rightColumn) {
                rightWidest = Math.max(rightWidest, font.width(line));
            }
            // 右列尽量离左列 8 像素、不早于 +50；放不下时往左挪到右边留 2 像素，但至少和左列隔 4 像素（两列叠字比压到槽更难读）
            int rightX = left + Math.max(2 + widest + 4, Math.min(Math.max(50, 2 + widest + 8), WIDTH - 2 - rightWidest));
            for (int i = 0; i < rightColumn.size(); i++) {
                graphics.drawString(font, rightColumn.get(i), rightX, top + 4 + i * 10, 0xFFFFFF, true);
            }
        }

        private Component elementLine(IElementalCrystal.Element element) {
            return Component.translatable("gui.essentialcraft5.crystal." + element.getSerializedName(), menu.element(element));
        }

        /** 上半 17 行（含上边框）、中间 2 行、下半 17 行（含下边框）；横向照 TextBarElement 的拼法 */
        private void drawPanel(GuiGraphics graphics, int left, int top) {
            column(graphics, left, top, 0, 17);
            for (int dx = 17; dx < WIDTH - 17; dx += 16) {
                column(graphics, left + dx, top, 1, Math.min(16, WIDTH - 17 - dx));
            }
            column(graphics, left + WIDTH - 17, top, 1, 17);
        }

        private void column(GuiGraphics graphics, int screenX, int top, int u, int w) {
            graphics.blit(ECTextures.SLOT_COMMON, screenX, top, u, 0, w, 17);
            graphics.blit(ECTextures.SLOT_COMMON, screenX, top + 17, u, 1, w, 2);
            graphics.blit(ECTextures.SLOT_COMMON, screenX, top + 19, u, 1, w, 17);
        }

        @Override
        public List<Component> tooltip() {
            List<Component> lines = new ArrayList<>();
            Status status = menu.status();
            lines.add(status.label().withStyle(style -> style.withColor(status.color)));
            if (menu.essence().getItem() instanceof EssenceItem essence) {
                lines.add(Component.translatable(KEY + "chance_detail", percent(menu.chancePercent()), menu.feedCost())
                        .withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable(KEY + "gain_detail", CrystalControllerBlockEntity.maxGain(essence))
                        .withStyle(ChatFormatting.GRAY));
            }
            lines.add(Component.translatable(KEY + "idle_detail").withStyle(ChatFormatting.DARK_GRAY));
            return lines;
        }
    }
}
