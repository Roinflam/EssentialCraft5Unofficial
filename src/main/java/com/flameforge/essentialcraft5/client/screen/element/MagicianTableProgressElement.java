package com.flameforge.essentialcraft5.client.screen.element;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.MagicianTableUpgrades;
import com.flameforge.essentialcraft5.client.screen.ECTextures;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * 魔力工作台的五格底框加四条进度箭头（54×54）。原作 GuiProgressBar_MagicianTable：
 * 底框取 gui_magiciantable.png 左上 54×54，四条箭头从四角那几格朝中心那格长，各 18 像素。
 * 原作没有悬停提示，这里补上进度和速度系数（界面上看不到升级的效果）。
 */
public record MagicianTableProgressElement(int x, int y, IntSupplier progress, IntSupplier required,
                                           IntSupplier speedPercent) implements ScreenElement {

    public static final ResourceLocation TEXTURE = EssentialCraft5.id("textures/gui/gui_magiciantable.png");

    @Override
    public void render(GuiGraphics graphics, int left, int top) {
        graphics.blit(TEXTURE, left, top, 0, 0, 54, 54);
        int filled = ECTextures.scaled(progress.getAsInt(), required.getAsInt(), 18);
        if (filled <= 0) {
            return;
        }
        // 左、上、右、下四条，坐标与原作一致
        graphics.blit(TEXTURE, left, top + 18, 54, 18, filled, 18);
        graphics.blit(TEXTURE, left + 18, top, 72, 0, 18, filled);
        graphics.blit(TEXTURE, left + 54 - filled, top + 18, 108 - filled, 18, filled, 18);
        graphics.blit(TEXTURE, left + 18, top + 54 - filled, 72, 54 - filled, 18, filled);
    }

    @Override
    public int width() {
        return 54;
    }

    @Override
    public int height() {
        return 54;
    }

    @Override
    public List<Component> tooltip() {
        List<Component> lines = new ArrayList<>();
        int max = required.getAsInt();
        lines.add(max > 0
                ? Component.translatable("gui.essentialcraft5.magician_table.progress", progress.getAsInt(), max)
                : Component.translatable("gui.essentialcraft5.magician_table.idle"));
        int speed = speedPercent.getAsInt();
        if (speed != 100) {
            lines.add(Component.translatable("gui.essentialcraft5.magician_table.speed",
                    MagicianTableUpgrades.format(speed / 100D)).withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }
}
