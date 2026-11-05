package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Matrix4f;

/** 界面贴图与绘制小工具（替代原作 DummyCore 的 DrawUtils / TextureUtils）。 */
public final class ECTextures {

    /** 原作 GuiCommon 的背景就是原版发射器界面，槽位框也从它上面取 */
    public static final ResourceLocation GENERIC_BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/container/dispenser.png");
    public static final ResourceLocation SLOT_COMMON = EssentialCraft5.id("textures/gui/slot_common.png");
    public static final ResourceLocation MRU_STORAGE = EssentialCraft5.id("textures/gui/mrustorage.png");
    public static final ResourceLocation MOON_PHASES = EssentialCraft5.id("textures/gui/gui_moon_phases.png");
    /** 原作 ClientProxy.mruIcon（方块图集里的 essentialcraft:blocks/mru） */
    public static final ResourceLocation MRU_SPRITE = EssentialCraft5.id("block/mru");

    private ECTextures() {
    }

    public static TextureAtlasSprite blockSprite(ResourceLocation id) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(id);
    }

    /**
     * 画图集精灵的左上角 w×h 像素（1:1，不缩放；w、h 不超过 16）。
     * 对应原作 DrawUtils.drawScaledTexturedRect：名字叫 scaled，其实是按像素裁切。
     * GuiGraphics 的裁切版 innerBlit 不是 public，只好自己画。颜色用 graphics.setColor 设。
     */
    public static void spriteCropped(GuiGraphics graphics, TextureAtlasSprite sprite, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        float u0 = sprite.getU0();
        float v0 = sprite.getV0();
        float u1 = sprite.getU(Math.min(w, 16));
        float v1 = sprite.getV(Math.min(h, 16));
        RenderSystem.setShaderTexture(0, sprite.atlasLocation());
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        // MRU 贴图带半透明（alpha 187~255），要开混合
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.vertex(matrix, x, y, 0).uv(u0, v0).endVertex();
        buffer.vertex(matrix, x, y + h, 0).uv(u0, v1).endVertex();
        buffer.vertex(matrix, x + w, y + h, 0).uv(u1, v1).endVertex();
        buffer.vertex(matrix, x + w, y, 0).uv(u1, v0).endVertex();
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.disableBlend();
    }

    /** 用精灵平铺一块区域（原作 DrawUtils.drawTexture：按 16×16 平铺，边上裁切） */
    public static void spriteTiled(GuiGraphics graphics, TextureAtlasSprite sprite, int x, int y, int w, int h) {
        for (int i = 0; i < w; i += 16) {
            for (int j = 0; j < h; j += 16) {
                spriteCropped(graphics, sprite, x + i, y + j, Math.min(w - i, 16), Math.min(h - j, 16));
            }
        }
    }

    /** 原作 MathUtils.pixelatedTextureSize：current/max 换算成 size 像素，向下取整 */
    public static int scaled(int current, int max, int size) {
        if (max <= 0) {
            return 0;
        }
        return (int) ((long) Math.min(current, max) * size / max);
    }
}
