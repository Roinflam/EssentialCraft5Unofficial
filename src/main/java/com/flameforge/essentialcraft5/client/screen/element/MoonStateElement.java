package com.flameforge.essentialcraft5.client.screen.element;

import com.flameforge.essentialcraft5.client.screen.ECTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/** 当前月相图标（18×18）。原作 GuiMoonState。 */
public record MoonStateElement(int x, int y) implements ScreenElement {

    @Override
    public void render(GuiGraphics graphics, int left, int top) {
        graphics.blit(ECTextures.SLOT_COMMON, left, top, 0, 0, 18, 18);
        graphics.blit(ECTextures.MOON_PHASES, left + 1, top + 1, 16 * moonPhase(), 0, 16, 16);
    }

    private static int moonPhase() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0 : minecraft.level.getMoonPhase();
    }

    @Override
    public int width() {
        return 18;
    }

    @Override
    public int height() {
        return 18;
    }

    @Override
    public List<Component> tooltip() {
        return List.of(Component.translatable("gui.essentialcraft5.moon_phase." + moonPhase()));
    }
}
