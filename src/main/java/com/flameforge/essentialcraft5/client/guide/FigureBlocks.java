package com.flameforge.essentialcraft5.client.guide;

import com.flameforge.essentialcraft5.client.guide.GuideBlocks.Ctx;
import com.flameforge.essentialcraft5.client.guide.GuideBlocks.Laid;
import com.flameforge.essentialcraft5.client.guide.GuideContent.Block;
import com.flameforge.essentialcraft5.client.guide.GuideTextLayout.Line;
import com.flameforge.essentialcraft5.structure.StructurePattern;
import com.flameforge.essentialcraft5.structure.StructurePatterns;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * EC5 自己的几种块：配方图、结构图、仪式图、插图（格式见 tools/guide/FORMAT.md §1.3）。结构图默认是立体的（{@link StructureView3D}），
 * 这里排的分层俯视图是它的「平面」视图
 *
 * <p>真正的绘制是旧手册留下来的 {@link Figures}（配方的格子位置照原作界面、结构的层与图例、仪式的「丢出 / 产出」），
 * 这里只做两件事：按阅读栏宽度把一张或几张图排好（排版时建好 {@link Figures.Figure}，渲染路径只画），
 * 和把图登记的热区接到新引擎上（{@link GuideRender#figureFrame} / {@link GuideRender#collectHotspots}）——
 * 悬停出物品提示与说明行、点物品跳到介绍它的条目，和正文里的物品链接、物品块走同一条路。</p>
 *
 * <p>和通用引擎的那些类分开放：EC 的代码（CC BY-SA 4.0）不能进作者别的模组，通用引擎以后的改进也还能原样同步过来。</p>
 */
@OnlyIn(Dist.CLIENT)
final class FigureBlocks {

    /** 并排的两张图之间的空隙 */
    private static final int GAP_X = 12;
    /** 上下两行图之间的空隙 */
    private static final int GAP_Y = 8;
    /** 插图最多多高：再高一屏就只剩一张图 */
    private static final int IMAGE_MAX_H = 160;
    /** 结构图不按高度缩格子（阅读栏竖着滚，高度不受限），只按宽度缩 */
    private static final int NO_HEIGHT_LIMIT = 4096;

    private FigureBlocks() {
    }

    /**
     * 排一个 recipe / structure / ritual / image 块
     *
     * @return 排好的块；该整块隐藏时（找不到配方）为 null
     */
    @Nullable
    static Laid layout(Block b, Ctx c) {
        Line[] caption = b.caption == null || b.caption.isEmpty() ? new Line[0] : c.wrap(b.caption, GuideTheme.MIST, false, c.width);
        if (b.figure instanceof FigureSpec.Recipes spec) {
            return recipes(spec, caption, c);
        }
        if (b.figure instanceof FigureSpec.Structure spec) {
            return structure(spec, caption, c);
        }
        if (b.figure instanceof FigureSpec.Image spec) {
            Figures.Picture picture = new Figures.Picture(spec, c.width, IMAGE_MAX_H);
            return new FlowLaid(List.of(picture), 0, caption, c.width);
        }
        return null;
    }

    // ==================== 配方 ====================

    @Nullable
    private static Laid recipes(FigureSpec.Recipes spec, Line[] caption, Ctx c) {
        Minecraft minecraft = Minecraft.getInstance();
        RegistryAccess access = minecraft.level == null ? RegistryAccess.EMPTY : minecraft.level.registryAccess();
        List<Figures.Figure> figures = new ArrayList<>();
        for (Recipe<?> recipe : c.media.recipes(spec)) {
            Figures.Figure figure = Figures.recipe(recipe, access, c.text.font(), c.width, spec.compact());
            if (figure != null) {
                figures.add(figure);
            }
        }
        if (figures.isEmpty()) {
            // 找不到配方：整块不显示（进服时 GuideChecks 已经在日志里报过；写了 optional 的是有意的）
            return null;
        }
        return new FlowLaid(figures, spec.compact() ? 2 : GAP_Y, caption, c.width);
    }

    // ==================== 结构 / 仪式 ====================

    private static Laid structure(FigureSpec.Structure spec, Line[] caption, Ctx c) {
        List<Figures.Figure> layers = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        int initial = 0;
        StructurePattern code = null;
        List<Integer> shownY = new ArrayList<>();
        if (spec.structure() != null) {
            StructurePattern pattern = StructurePatterns.get(spec.structure());
            if (pattern == null) {
                GuideLog.warnOnce("structure:" + spec.structure(), "结构 " + spec.structure() + " 没有登记（StructurePatterns.register）");
                Line[] msg = c.text.wrap(GuideMarkup.literal(I18n.get("guide.essentialcraft5.structure.missing", spec.structure().toString()),
                        GuideTheme.MIST), c.width);
                return GuideBlocks.lines(msg);
            }
            // 跳过整层都没有要求的层，机器所在的层总画
            List<StructurePattern.Layer> shown = new ArrayList<>();
            for (StructurePattern.Layer layer : pattern.layers()) {
                if (!layer.isEmpty() || layer.y() == 0) {
                    shown.add(layer);
                }
            }
            code = pattern;
            for (int i = 0; i < shown.size(); i++) {
                StructurePattern.Layer layer = shown.get(i);
                layers.add(Figures.Layout.code(layer, i, shown.size(), c.text.font(), c.width, NO_HEIGHT_LIMIT));
                labels.add(signed(layer.y()));
                shownY.add(layer.y());
                if (layer.y() == 0) {
                    initial = i;
                }
            }
        } else {
            int anchorLayer = -1;
            if (spec.anchor() != null) {
                for (int l = 0; l < spec.layers().size() && anchorLayer < 0; l++) {
                    for (String row : spec.layers().get(l).pattern()) {
                        if (row.indexOf(spec.anchor()) >= 0) {
                            anchorLayer = l;
                            break;
                        }
                    }
                }
            }
            for (int i = 0; i < spec.layers().size(); i++) {
                layers.add(Figures.Layout.json(spec, i, c.text.font(), c.width, NO_HEIGHT_LIMIT));
                labels.add(anchorLayer >= 0 ? signed(i - anchorLayer) : String.valueOf(i + 1));
            }
            initial = Math.max(0, anchorLayer);
        }
        // 仪式图（丢东西的地面摆法）只有平面图；别的结构默认立体图，平面图做它的「平面」视图（说明交给立体图画在最下面）
        boolean ritual = spec.thrown() != null || spec.result() != null;
        Line[] flatCaption = ritual ? caption : new Line[0];
        Laid flat = layers.size() > 2 ? new LayeredLaid(layers, labels, initial, flatCaption, c) : new FlowLaid(layers, GAP_Y, flatCaption, c.width);
        if (ritual) {
            return flat;
        }
        if (code != null) {
            int[] ys = shownY.stream().mapToInt(Integer::intValue).toArray();
            return StructureView3D.code(spec.structure(), code, labels, ys, flat, caption, c);
        }
        return StructureView3D.json(spec, labels, flat, caption, c);
    }

    private static String signed(int y) {
        return y > 0 ? "+" + y : String.valueOf(y);
    }

    // ==================== 排好的块 ====================

    /**
     * 一张或几张图：一行放得下就并排（每行居中），放不下换行；下面接说明
     */
    private static final class FlowLaid extends Laid {
        private final Figures.Figure[] figures;
        private final int[] fx;
        private final int[] fy;
        private final Line[] caption;
        private final int capY;

        FlowLaid(List<Figures.Figure> list, int gapY, Line[] caption, int width) {
            this.figures = list.toArray(new Figures.Figure[0]);
            this.fx = new int[figures.length];
            this.fy = new int[figures.length];
            this.caption = caption;
            int y = 0;
            int i = 0;
            while (i < figures.length) {
                // 这一行能放几张
                int rowW = figures[i].width();
                int rowH = figures[i].height();
                int j = i + 1;
                while (j < figures.length && rowW + GAP_X + figures[j].width() <= width) {
                    rowW += GAP_X + figures[j].width();
                    rowH = Math.max(rowH, figures[j].height());
                    j++;
                }
                int x = Math.max(0, (width - rowW) / 2);
                for (int k = i; k < j; k++) {
                    fx[k] = x;
                    fy[k] = y + (rowH - figures[k].height()) / 2;
                    x += figures[k].width() + GAP_X;
                }
                y += rowH + (j < figures.length ? gapY : 0);
                i = j;
            }
            this.capY = y + (caption.length > 0 ? 4 : 0);
            this.height = capY + (caption.length > 0 ? caption.length * GuideTheme.LINE_H - GuideTheme.LINE_TAIL : 0);
        }

        @Override
        void render(GuideRender r, int x, int y, int w) {
            if (r.solidReady() && r.visible(y, capY)) {
                GuideFrame f = r.figureFrame();
                for (int i = 0; i < figures.length; i++) {
                    int top = y + fy[i];
                    if (r.visible(top, figures[i].height())) {
                        // 候选物品只在鼠标压着这张图时轮换（见 GuideFrame.cycling）
                        f.cycling = f.hovered(x + fx[i], top, figures[i].width(), figures[i].height());
                        figures[i].render(f, x + fx[i], top);
                    }
                }
                r.collectHotspots(f);
            }
            r.lines(caption, x, y + capY, GuideTheme.LINE_H, false);
        }
    }

    /**
     * 超过 2 层的结构：顶上一排层切换按钮（点击或在按钮上滚轮切层，默认显示机器所在的层），下面画当前层（图例在图下）。
     * 块高按最高的一层留，切层时下面的正文不跳。
     */
    static final class LayeredLaid extends Laid {
        private static final int BAR_H = 13;
        private static final int BTN_GAP = 3;

        private final Figures.Figure[] layers;
        private final String[] labels;
        private final int[] labelW;
        private final int[] btnW;
        private final String caption;
        private final int captionW;
        private final Line[] note;
        private final int bodyH;
        private int current;
        private int hoverButton = -1;

        LayeredLaid(List<Figures.Figure> list, List<String> labels, int initial, Line[] note, Ctx c) {
            this.layers = list.toArray(new Figures.Figure[0]);
            this.labels = labels.toArray(new String[0]);
            this.labelW = new int[this.labels.length];
            this.btnW = new int[this.labels.length];
            for (int i = 0; i < this.labels.length; i++) {
                labelW[i] = c.text.font().width(this.labels[i]);
                btnW[i] = Math.max(15, labelW[i] + 8);
            }
            this.caption = I18n.get("guide.essentialcraft5.ui.layers");
            this.captionW = c.text.font().width(caption);
            this.note = note;
            int max = 0;
            for (Figures.Figure f : layers) {
                max = Math.max(max, f.height());
            }
            this.bodyH = max;
            this.current = Math.max(0, Math.min(layers.length - 1, initial));
            this.height = BAR_H + 6 + bodyH + (note.length > 0 ? 4 + note.length * GuideTheme.LINE_H - GuideTheme.LINE_TAIL : 0);
        }

        /** 展示厅截图用：直接切到第几层（从下往上，0 起） */
        void select(int layer) {
            current = Math.max(0, Math.min(layers.length - 1, layer));
        }

        int layerCount() {
            return layers.length;
        }

        private int barWidth() {
            int w = captionW + 6;
            for (int bw : btnW) {
                w += bw + BTN_GAP;
            }
            return w - BTN_GAP;
        }

        private int buttonAt(double mx, double my, int x, int y, int w) {
            if (my < y || my >= y + BAR_H) {
                return -1;
            }
            int bx = x + (w - barWidth()) / 2 + captionW + 6;
            for (int i = 0; i < btnW.length; i++) {
                if (mx >= bx && mx < bx + btnW[i]) {
                    return i;
                }
                bx += btnW[i] + BTN_GAP;
            }
            return -1;
        }

        @Override
        boolean mouseClicked(double mx, double my, int button, int x, int y, int w) {
            if (button != 0) {
                return false;
            }
            int i = buttonAt(mx, my, x, y, w);
            if (i < 0 || i == current) {
                return i >= 0;
            }
            current = i;
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.2F));
            return true;
        }

        @Override
        boolean mouseScrolled(double mx, double my, double delta, int x, int y, int w) {
            // 只在按钮那一排上滚轮切层：压在图上滚轮照常滚正文，不会卡住
            if (my < y || my >= y + BAR_H || mx < x || mx >= x + w) {
                return false;
            }
            int next = Math.max(0, Math.min(layers.length - 1, current + (delta > 0 ? 1 : -1)));
            current = next;
            return true;
        }

        @Override
        void render(GuideRender r, int x, int y, int w) {
            GuiGraphics g = r.g;
            // 层切换条：「层」+ 一排按钮（从下往上：高度 / 层号），当前层暗品红底品红框，悬停只描品红框，按住时暗品红底
            int bx = x + (w - barWidth()) / 2;
            hoverButton = r.mouseActive ? buttonAt(r.mouseX, r.mouseY, x, y, w) : -1;
            if (r.visible(y, BAR_H)) {
                g.drawString(r.font, caption, bx, y + 3, r.text(GuideTheme.MIST), false);
                bx += captionW + 6;
                for (int i = 0; i < btnW.length; i++) {
                    boolean on = i == current;
                    boolean over = i == hoverButton && r.over(bx, y, btnW[i], BAR_H);
                    CodexTheme.chamferFill(g, bx, y, btnW[i], BAR_H, 2,
                            r.fill(over && r.mouseDown ? GuideTheme.TILE_BG_PRESS
                                    : on ? GuideTheme.withAlpha(GuideTheme.FOCUS_DEEP, 0x70) : GuideTheme.TILE_BG));
                    CodexTheme.chamferOutline(g, bx, y, btnW[i], BAR_H, 2,
                            r.fill(on ? GuideTheme.FOCUS : over ? GuideTheme.withAlpha(GuideTheme.FOCUS, 0xA0)
                                    : GuideTheme.withAlpha(GuideTheme.EDGE, 0xC0)));
                    g.drawString(r.font, labels[i], bx + (btnW[i] - labelW[i]) / 2, y + 3,
                            r.text(on || over ? GuideTheme.PEARL : GuideTheme.MIST), false);
                    bx += btnW[i] + BTN_GAP;
                }
            }
            int top = y + BAR_H + 6;
            Figures.Figure layer = layers[current];
            if (r.solidReady() && r.visible(top, bodyH)) {
                GuideFrame f = r.figureFrame();
                int lx = x + Math.max(0, (w - layer.width()) / 2);
                f.cycling = f.hovered(lx, top, layer.width(), layer.height());
                layer.render(f, lx, top);
                r.collectHotspots(f);
            }
            r.lines(note, x, top + bodyH + 4, GuideTheme.LINE_H, false);
        }
    }
}
