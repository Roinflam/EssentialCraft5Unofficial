package com.flameforge.essentialcraft5.client.screen.element;

import com.flameforge.essentialcraft5.client.screen.ECTextures;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;

import java.util.List;
import java.util.function.IntSupplier;

/**
 * 竖直的流体罐（18×54）。原作 GuiFluidTank（岩浆冶炼炉的岩浆罐）：框取 mrustorage.png 上面 54 行、再补最底下一行做下边，
 * 里面用流体的贴图从下往上填，最高 52 像素。
 * <p>
 * 原作直接读客户端方块实体的罐子（界面打开时取一次引用，之后就不一定跟着变）；这里的量和容量都从菜单同步，流体种类由调用方给
 * （冶炼炉的罐子只收岩浆）。原作画的是流动贴图，这里用静止贴图（流动贴图是 32×32 的斜纹，截一小块看起来是乱的）。
 *
 * @param fluid 罐里装的流体（决定贴图、颜色和提示里的名字）
 */
public record FluidTankElement(int x, int y, Fluid fluid, IntSupplier amount, IntSupplier capacity) implements ScreenElement {

    public static final int WIDTH = 18;
    public static final int HEIGHT = 54;
    /** 流体能填的高度 */
    private static final int INNER = 52;

    @Override
    public void render(GuiGraphics graphics, int left, int top) {
        graphics.blit(ECTextures.MRU_STORAGE, left, top, 0, 0, WIDTH, HEIGHT);
        graphics.blit(ECTextures.MRU_STORAGE, left, top + HEIGHT - 1, 0, 71, WIDTH, 1);
        int filled = ECTextures.scaled(amount.getAsInt(), capacity.getAsInt(), INNER);
        if (filled <= 0) {
            return;
        }
        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(fluid);
        int tint = extensions.getTintColor();
        graphics.setColor((tint >> 16 & 0xFF) / 255F, (tint >> 8 & 0xFF) / 255F, (tint & 0xFF) / 255F, 1F);
        ECTextures.spriteTiled(graphics, ECTextures.blockSprite(extensions.getStillTexture()), left + 1, top + 1 + INNER - filled, 16, filled);
        graphics.setColor(1F, 1F, 1F, 1F);
    }

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public int height() {
        return HEIGHT;
    }

    /** 「岩浆：5000 / 8000 mB」 */
    @Override
    public List<Component> tooltip() {
        return List.of(Component.translatable("gui.essentialcraft5.fluid_tank", fluid.getFluidType().getDescription(),
                amount.getAsInt(), capacity.getAsInt()));
    }
}
