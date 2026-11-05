package com.flameforge.essentialcraft5.client.screen.element;

import com.flameforge.essentialcraft5.client.screen.ECTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.Random;

/**
 * 高度示意（地层剖面 + 一条表示方块高度的绿线，左边写 y 值）。原作 GuiHeightState。
 * <p>
 * 注意它画在 (x, y) 的左边和上方：底框从 x-18 起，剖面向上延伸 32 像素，和原作一样。
 * 矿点用固定种子的 java.util.Random 撒，所以位置与原作完全一致。
 * 绿线位置按原作的 y/256×50 算，夹在 [0, 45]（1.20.1 的 y 可以是负数或超过 256）。
 */
public record HeightStateElement(int x, int y, int blockY) implements ScreenElement {

    private static final ResourceLocation GRASS = ResourceLocation.withDefaultNamespace("block/grass_block_top");
    private static final ResourceLocation DIRT = ResourceLocation.withDefaultNamespace("block/dirt");
    private static final ResourceLocation STONE = ResourceLocation.withDefaultNamespace("block/stone");
    private static final ResourceLocation BEDROCK = ResourceLocation.withDefaultNamespace("block/bedrock");
    private static final ResourceLocation GRAVEL = ResourceLocation.withDefaultNamespace("block/gravel");
    private static final ResourceLocation DIAMOND = ResourceLocation.withDefaultNamespace("block/diamond_block");
    private static final ResourceLocation COAL = ResourceLocation.withDefaultNamespace("block/coal_block");
    /** 原作 STAINED_HARDENED_CLAY meta 8（淡灰色） */
    private static final ResourceLocation CLAY = ResourceLocation.withDefaultNamespace("block/light_gray_terracotta");
    private static final ResourceLocation GOLD = ResourceLocation.withDefaultNamespace("block/gold_block");
    private static final ResourceLocation LAVA = ResourceLocation.withDefaultNamespace("block/lava_still");
    private static final ResourceLocation EMERALD = ResourceLocation.withDefaultNamespace("block/emerald_block");

    @Override
    public void render(GuiGraphics graphics, int left, int top) {
        graphics.blit(ECTextures.SLOT_COMMON, left - 18, top - 1, 0, 0, 18, 18);
        graphics.blit(ECTextures.SLOT_COMMON, left, top, 0, 1, 18, 17);
        graphics.blit(ECTextures.SLOT_COMMON, left, top - 16, 0, 1, 18, 16);
        graphics.blit(ECTextures.SLOT_COMMON, left, top - 32, 0, 0, 18, 17);

        graphics.setColor(0F, 0.6F, 0F, 1F);
        draw(graphics, GRASS, left + 1, top, 16, 1);
        graphics.setColor(1F, 1F, 1F, 1F);
        draw(graphics, DIRT, left + 1, top + 1, 16, 2);
        draw(graphics, STONE, left + 1, top + 3, 16, 11);
        draw(graphics, BEDROCK, left + 1, top + 15, 16, 1);

        Random random = new Random(143535645L);
        for (int i = 0; i < 10; ++i) {
            draw(graphics, GRAVEL, left + 1 + random.nextInt(15), top + 3 + random.nextInt(11), 2, 2);
        }
        for (int i = 0; i < 2; ++i) {
            draw(graphics, DIAMOND, left + 1 + random.nextInt(15), top + 9 + random.nextInt(11), 1, 1);
        }
        for (int i = 0; i < 12; ++i) {
            draw(graphics, COAL, left + 1 + random.nextInt(15), top + 3 + random.nextInt(11), 1, 1);
        }
        for (int i = 0; i < 6; ++i) {
            draw(graphics, CLAY, left + 1 + random.nextInt(15), top + 3 + random.nextInt(11), 1, 1);
        }
        for (int i = 0; i < 4; ++i) {
            draw(graphics, GOLD, left + 1 + random.nextInt(15), top + 6 + random.nextInt(8), 1, 1);
        }
        for (int i = 0; i < 8; ++i) {
            draw(graphics, LAVA, left + 1 + random.nextInt(15), top + 12 + random.nextInt(2), 1, 1);
        }
        for (int i = 0; i < 8; ++i) {
            draw(graphics, BEDROCK, left + 1 + random.nextInt(15), top + 14, 1, 1);
        }

        int marker = Mth.clamp(ECTextures.scaled(Math.max(blockY, 0), 256, 50), 0, 45);
        graphics.setColor(0F, 1F, 0F, 1F);
        draw(graphics, EMERALD, left + 1, top + 14 - marker, 16, 1);
        graphics.setColor(1F, 1F, 1F, 1F);

        // 原作按两位数摆在左边那格里（x - 15 - 位数）；1.20.1 的 y 是 -64~319，三位数连阴影正好 18 像素宽，压满那格两边的框。
        // 改成在格子里（内宽 16）居中，左右各留 1 像素，放不下时缩小
        String text = Integer.toString(blockY);
        Font font = Minecraft.getInstance().font;
        int textWidth = font.width(text);
        int room = 14;
        if (textWidth <= room) {
            graphics.drawString(font, text, left - 18 + (18 - textWidth) / 2, top + 5, 0xFFFFFF, true);
        } else {
            float scale = room / (float) textWidth;
            graphics.pose().pushPose();
            graphics.pose().translate(left - 16, top + 8 - 4 * scale, 0F);
            graphics.pose().scale(scale, scale, 1F);
            graphics.drawString(font, text, 0, 0, 0xFFFFFF, true);
            graphics.pose().popPose();
        }
    }

    private static void draw(GuiGraphics graphics, ResourceLocation sprite, int x, int y, int w, int h) {
        TextureAtlasSprite atlasSprite = ECTextures.blockSprite(sprite);
        ECTextures.spriteCropped(graphics, atlasSprite, x, y, w, h);
    }

    @Override
    public int width() {
        return 18;
    }

    @Override
    public int height() {
        return 18;
    }
}
