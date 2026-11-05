package com.flameforge.essentialcraft5.client.screen.element;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 机器界面上的一个显示元素（MRU 条、平衡值、状态文字……）。对应原作 DummyCore 的 GuiElement。
 * 坐标相对界面左上角，与原作各 Gui 类里 elementList.add(new XxxElement(x, y, …)) 的数字一致。
 */
public interface ScreenElement {

    int x();

    int y();

    /** @param left 元素左上角的屏幕坐标（界面左上角 + x） */
    void render(GuiGraphics graphics, int left, int top);

    /** 元素占的区域，用来判断悬停 */
    int width();

    int height();

    /** 悬停提示，没有就返回空列表 */
    default List<Component> tooltip() {
        return List.of();
    }
}
