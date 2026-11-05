package com.flameforge.essentialcraft5.client.screen.element;

import com.flameforge.essentialcraft5.client.screen.ECTextures;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntSupplier;

/** 竖直的 MRU 存量条（18×72）。原作 GuiMRUStorage：框取 mrustorage.png，里面用会动的 MRU 贴图从下往上填。 */
public record MRUStorageElement(int x, int y, IntSupplier mru, IntSupplier maxMRU) implements ScreenElement {

    @Override
    public void render(GuiGraphics graphics, int left, int top) {
        graphics.blit(ECTextures.MRU_STORAGE, left, top, 0, 0, 18, 72);
        int filled = ECTextures.scaled(mru.getAsInt(), maxMRU.getAsInt(), 72);
        ECTextures.spriteTiled(graphics, ECTextures.blockSprite(ECTextures.MRU_SPRITE), left + 1, top + 73 - filled, 16, filled - 2);
    }

    @Override
    public int width() {
        return 18;
    }

    @Override
    public int height() {
        return 72;
    }

    @Override
    public List<Component> tooltip() {
        return List.of(Component.translatable("tooltip.essentialcraft5.mru", mru.getAsInt(), maxMRU.getAsInt()));
    }
}
