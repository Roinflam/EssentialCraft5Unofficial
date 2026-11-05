package com.flameforge.essentialcraft5.client.guide;

import com.flameforge.essentialcraft5.client.guide.GuideMarkup.Link;
import com.flameforge.essentialcraft5.client.guide.GuideTextLayout.Line;
import com.flameforge.essentialcraft5.client.guide.GuideTextLayout.Piece;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 一帧的绘制上下文：画布、鼠标、可见范围、透明度，以及本帧「鼠标压着什么」
 *
 * <h3>悬停为什么延迟一帧</h3>
 * <p>一条链接折行后是好几段，鼠标压在第二段上时第一段也要一起变色 —— 可画第一段时还不知道
 * 鼠标压在第二段上。与其先把整页走一遍做命中测试再画，不如用上一帧的结果决定样式、
 * 这一帧边画边记录新的结果。差一帧（十几毫秒）肉眼看不出来，却省掉了一整遍遍历。
 * 点击同理：直接用最近一帧记下的链接 / 物品，画的位置和点的位置不可能对不上。</p>
 *
 * <h3>图（配方、结构、仪式、插图）</h3>
 * <p>{@link Figures} 用自己的绘制上下文 {@link GuideFrame}（旧手册留下来的），挂在这里的 {@link #frame} 上每帧复用。
 * 图画完后 {@link FigureBlocks} 把鼠标下的热区转成这里的 {@link #hoverStack} / {@link #hoverLines}：
 * 悬停出物品提示（加上图自己的说明行，比如「可选」「流动的也算」），点击物品跳到介绍它的条目。</p>
 */
@OnlyIn(Dist.CLIENT)
final class GuideRender {

    GuiGraphics g;
    Font font;
    /** 配方 / 实体预览的缓存（随界面实例） */
    GuideMedia media;
    /** 画「图」用的上下文（每帧复用，见类注释） */
    final GuideFrame frame = new GuideFrame();
    /** 鼠标位置（面板开合只裁剪、不平移，所以就是屏幕坐标） */
    int mouseX;
    int mouseY;
    /** 鼠标是否在可交互区域内（被裁掉的部分、动画进行中都不算） */
    boolean mouseActive;
    /** 左键正按着（悬停中的格子、按钮画成「按下」：暗品红底） */
    boolean mouseDown;
    /** 可见区域的上下边界（面板坐标），用来跳过看不见的行 */
    int clipTop;
    int clipBottom;
    /** 文字与填充的整体透明度（换页淡入用） */
    float alpha = 1f;
    long now;
    float time;
    /** 手册的电弧（悬停火花这类事件由各部件在发现事件时生成，界面统一画） */
    GuideArcs arcs;

    /** 上一帧鼠标下的链接：决定本帧哪条链接画成悬停样式 */
    @Nullable
    Link hoverLink;
    /** 本帧检测到的链接 */
    @Nullable
    Link nextHoverLink;
    /** 本帧鼠标下的物品：在 drawManaged 之外画原版 tooltip；点击时跳到介绍它的条目 */
    ItemStack hoverStack = ItemStack.EMPTY;
    /** 本帧鼠标下的附加说明行（图的热区给的）：接在物品提示后面，没有物品时就是整条提示 */
    List<Component> hoverLines = List.of();

    /** 帧开始：把上一帧的检测结果转正 */
    void beginFrame() {
        hoverLink = nextHoverLink;
        nextHoverLink = null;
        hoverStack = ItemStack.EMPTY;
        hoverLines = List.of();
    }

    int text(int argb) {
        return GuideTheme.textAlpha(argb, alpha);
    }

    int fill(int argb) {
        return GuideTheme.fade(argb, alpha);
    }

    boolean over(int x, int y, int w, int h) {
        return mouseActive && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h
                && mouseY >= clipTop && mouseY < clipBottom;
    }

    boolean visible(int y, int h) {
        return y + h > clipTop && y < clipBottom;
    }

    /**
     * 这一帧画不画物品图标 / 实体模型 / 图
     *
     * <p>它们没有透明度可调：换篇淡入时文字从 25% 慢慢浮现，图标却一上来就是实心的，
     * 像先于正文蹦出来。淡到八成再出现（约 60ms）。</p>
     */
    boolean solidReady() {
        return alpha >= 0.8f;
    }

    /**
     * 准备画图：把本帧的画布、鼠标、裁剪、透明度交给 {@link #frame}
     *
     * @return 准备好的图上下文
     */
    GuideFrame figureFrame() {
        GuideFrame f = frame;
        f.begin(g, font, mouseX, mouseY, now, time);
        f.alpha = alpha;
        f.interactive = mouseActive;
        f.clipTop = clipTop;
        f.clipBottom = clipBottom;
        return f;
    }

    /**
     * 图画完：鼠标下的热区转成本帧的悬停物品 / 说明行
     */
    void collectHotspots(GuideFrame f) {
        List<GuideFrame.Hotspot> spots = f.hotspots();
        for (int i = spots.size() - 1; i >= 0; i--) {
            GuideFrame.Hotspot spot = spots.get(i);
            if (spot.contains(mouseX, mouseY)) {
                hoverStack = spot.stack();
                hoverLines = spot.lines();
                return;
            }
        }
    }

    /**
     * 画一行排好的文字
     *
     * @param line   行
     * @param x      行首 x
     * @param y      行顶 y
     * @param shadow 是否带阴影
     */
    void line(Line line, int x, int y, boolean shadow) {
        for (Piece p : line.pieces) {
            int px = x + p.x;
            Link link = p.link;
            boolean live = link != null && link.isLive();
            if (link != null && over(px, y - 1, Math.max(1, p.w), GuideTheme.LINE_H)) {
                nextHoverLink = link;
                if (link.isItem()) {
                    // 物品链接：悬停出物品提示（和物品格一样）
                    hoverStack = link.stack;
                }
            }
            boolean hovered = live && link == hoverLink;
            g.drawString(font, hovered && p.hoverSeq != null ? p.hoverSeq : p.seq, px, y, text(0xFFFFFFFF), shadow);
            if (live) {
                if (hovered) {
                    g.fill(px, y + 9, px + p.w, y + 10, fill(GuideTheme.FOCUS));
                } else {
                    int under = link.isItem() ? GuideTheme.ITEM_LINK : GuideTheme.LINK;
                    GuideTheme.dotted(g, px, y + 9, p.w, fill(GuideTheme.withAlpha(under, 0x90)));
                }
            }
        }
    }

    /**
     * 画多行（只画可见的）
     *
     * @return 画完之后的 y
     */
    int lines(Line[] lines, int x, int y, int lineH, boolean shadow) {
        for (Line l : lines) {
            if (visible(y, lineH)) {
                line(l, x, y, shadow);
            }
            y += lineH;
        }
        return y;
    }

    /**
     * 多行居中
     */
    int linesCentered(Line[] lines, int x, int w, int y, int lineH, boolean shadow) {
        for (Line l : lines) {
            if (visible(y, lineH)) {
                line(l, x + (w - l.width) / 2, y, shadow);
            }
            y += lineH;
        }
        return y;
    }
}
