package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.blockentity.EnrichmentControllerBlockEntity;
import com.flameforge.essentialcraft5.blockentity.EnrichmentControllerBlockEntity.CollapseState;
import com.flameforge.essentialcraft5.blockentity.EnrichmentControllerBlockEntity.CondenseState;
import com.flameforge.essentialcraft5.client.screen.element.ScreenElement;
import com.flameforge.essentialcraft5.client.screen.element.TextBarElement;
import com.flameforge.essentialcraft5.menu.EnrichmentStateCheckerMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 富集仓状态检测器界面，元素与坐标照搬原作 GuiMRUInfo：MRU 条 (7,4)、MROV (25,22)、平衡值 (25,40)、存量 (25,58)。
 * 原作没连上控制器时 MROV 那条是空的，这里写「未连接富集仓」（红色）。
 * <p>
 * 第十批：MROV 右边 (109,22) 多一条聚集体坍缩的进度（{@link CollapseBar}），只在能坍缩时出现；悬停写进度和卡在哪，文字和右键控制器的一样。
 * 第十三批 13a：平衡值右边 (109,40) 多一条凝结矩阵的进度（{@link CondenseBar}），默认所有维度都有；两条都只在连着成形的仓时画。
 */
public class EnrichmentStateCheckerScreen extends MachineScreen<EnrichmentStateCheckerMenu> {

    public EnrichmentStateCheckerScreen(EnrichmentStateCheckerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        elements.add(mruStorage(7, 4));
        elements.add(new TextBarElement(25, 22, TextBarElement.NORMAL_WIDTH,
                () -> menu.linked()
                        ? Component.translatable("gui.essentialcraft5.mrov", formatResistance(menu.resistance()))
                        : Component.translatable("gui.essentialcraft5.enrichment.no_chamber"),
                () -> menu.linked() ? 0xFFFFFF : 0xFF0000, false));
        elements.add(balance(25, 40));
        elements.add(mruState(25, 58));
        elements.add(new CollapseBar(109, 22));
        elements.add(new CondenseBar(109, 40));
    }

    /** 抗性一般是整数（强化石 2、部件 100……），有小数时留一位 */
    static String formatResistance(double value) {
        double rounded = Math.round(value * 10D) / 10D;
        return rounded == Math.rint(rounded) ? Long.toString((long) rounded) : String.format(Locale.ROOT, "%.1f", rounded);
    }

    /**
     * 一个分支的进度条（宽 60）：底框里按进度填色，正中写百分比。不能走这一支时整条不画、也没有悬停提示。
     * 坍缩（冰冻蓝、混沌红）和凝结（源质紫）各一条，停着的都是灰；悬停写进度和卡在哪，文字和右键控制器的一样。
     */
    private abstract class BranchBar implements ScreenElement {
        private static final int WIDTH = 60;
        static final int PAUSED_FILL = 0x80707070;
        /** 悬停提示每行最宽多少像素 */
        private static final int TOOLTIP_WIDTH = 200;

        private final int x;
        private final int y;

        BranchBar(int x, int y) {
            this.x = x;
            this.y = y;
        }

        abstract boolean shows();

        abstract int progress();

        abstract int seconds();

        abstract boolean working();

        /** 填色（含透明度） */
        abstract int fill();

        abstract List<Component> lines();

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
            return 18;
        }

        @Override
        public void render(GuiGraphics graphics, int left, int top) {
            if (!shows()) {
                return;
            }
            TextBarElement.drawBar(graphics, left, top, WIDTH);
            int seconds = Math.max(1, seconds());
            int progress = Math.min(seconds, Math.max(0, progress()));
            int inner = WIDTH - 4;
            int filled = (int) ((long) inner * progress / seconds);
            if (filled > 0) {
                graphics.fill(left + 2, top + 2, left + 2 + filled, top + 16, fill());
            }
            Font font = Minecraft.getInstance().font;
            String text = (progress * 100 / seconds) + "%";
            graphics.drawString(font, text, left + (WIDTH - font.width(text)) / 2, top + 5, working() ? 0xFFFFFF : 0xA0A0A0, true);
        }

        @Override
        public List<Component> tooltip() {
            if (!shows()) {
                return List.of();
            }
            // 说明行很长（右键控制器时进聊天栏会自动折行），悬停提示不折行：按宽度拆开，免得界面缩放大时伸出屏幕
            Font font = Minecraft.getInstance().font;
            List<Component> wrapped = new ArrayList<>();
            for (Component line : lines()) {
                for (FormattedText part : font.getSplitter().splitLines(line, TOOLTIP_WIDTH, Style.EMPTY)) {
                    wrapped.add(Component.literal(part.getString()));
                }
            }
            return wrapped;
        }
    }

    /** 聚集体坍缩（第十批）：冰冻蓝、混沌红；暗影核心（13e）暗紫 */
    private final class CollapseBar extends BranchBar {
        private static final int FROZEN_FILL = 0xA03050D0;
        private static final int CHAOS_FILL = 0xA0D03838;
        private static final int SHADE_FILL = 0xA05A2A8C;

        CollapseBar(int x, int y) {
            super(x, y);
        }

        @Override
        boolean shows() {
            return menu.showsCollapse();
        }

        @Override
        int progress() {
            return menu.collapseProgress();
        }

        @Override
        int seconds() {
            return menu.collapseSeconds();
        }

        @Override
        boolean working() {
            return menu.collapseState().working();
        }

        @Override
        int fill() {
            CollapseState state = menu.collapseState();
            return switch (state) {
                case FROZEN -> FROZEN_FILL;
                case CHAOS -> CHAOS_FILL;
                case SHADE -> SHADE_FILL;
                default -> PAUSED_FILL;
            };
        }

        @Override
        List<Component> lines() {
            return EnrichmentControllerBlockEntity.collapseLines(menu.collapseState(), menu.collapseProgress(), menu.collapseSeconds(),
                    menu.collapseEvent(), menu.voidCasing(), menu.presenceMRU(), data().mru(), data().balance(),
                    EnrichmentControllerBlockEntity.Config.collapse(), menu.shadeHint(), menu.umbral(), EnrichmentControllerBlockEntity.Config.shade());
        }
    }

    /** 凝结矩阵（第十三批 13a）：源质紫 */
    private final class CondenseBar extends BranchBar {
        private static final int CONDENSE_FILL = 0xA09040D0;

        CondenseBar(int x, int y) {
            super(x, y);
        }

        @Override
        boolean shows() {
            return menu.showsCondense();
        }

        @Override
        int progress() {
            return menu.condenseProgress();
        }

        @Override
        int seconds() {
            return menu.condenseSeconds();
        }

        @Override
        boolean working() {
            return menu.condenseState().working();
        }

        @Override
        int fill() {
            return menu.condenseState() == CondenseState.CONDENSING ? CONDENSE_FILL : PAUSED_FILL;
        }

        @Override
        List<Component> lines() {
            return EnrichmentControllerBlockEntity.condenseLines(menu.condenseState(), menu.condenseProgress(), menu.condenseSeconds(),
                    menu.condenseEvent(), menu.condenseCasing(), menu.condensePresenceMRU(), data().mru(), data().balance(),
                    EnrichmentControllerBlockEntity.Config.condense());
        }
    }
}
