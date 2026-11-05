package com.flameforge.essentialcraft5.client.render;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.WindRuneBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 风之符文地上的图案（原作 RenderWindRune）：祭坛完整时，在符文周围 3×3 的地面上（离地 0.05 格）画一张 windrune 贴图，亮度 60 tick 一个来回地呼吸
 * （原作 color(c, c, c)，c 在 0.2~1 之间）；祭坛不完整时什么都不画。
 * <p>
 * 原作用 OBJ rune.obj 里的一个面（菱形，半对角线 2.12，转 45° 后是 3×3 的正方形），这里直接画这个四边形，贴图坐标照 OBJ（DummyCore 读 vt 时把 v 翻成 1 − v）。
 * 原作关了物品光照、沿用方块处的光照；这里用满亮度（它本来就是发光的魔法图案，夜里也看得见），双面、半透明（贴图四角是透明的）。
 */
public class WindRuneRenderer implements BlockEntityRenderer<WindRuneBlockEntity> {

    public static final ResourceLocation TEXTURE = EssentialCraft5.id("textures/block/model/windrune.png");
    /** 原作 translate(y − 0.2) + 顶点 y 0.25 */
    private static final float HEIGHT = 0.05F;
    /** 原作 c 的下限 */
    private static final float MIN_BRIGHTNESS = 0.2F;

    public WindRuneRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(WindRuneBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null || !be.isFormed()) {
            return;
        }
        float movement = (level.getGameTime() % 60L) + partialTick;
        if (movement > 30F) {
            movement = 60F - movement;
        }
        float c = Math.max(MIN_BRIGHTNESS, movement / 30F);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        Matrix4f matrix = pose.last().pose();
        Matrix3f normal = pose.last().normal();
        // 方块坐标系里中心 (0.5, 0.5)，四角 −1 ~ 2；贴图坐标照原作 OBJ 转 45° 以后的对应关系
        vertex(consumer, matrix, normal, 2F, 2F, 0F, 1F, c);
        vertex(consumer, matrix, normal, 2F, -1F, 1F, 1F, c);
        vertex(consumer, matrix, normal, -1F, -1F, 1F, 0F, c);
        vertex(consumer, matrix, normal, -1F, 2F, 0F, 0F, c);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal, float x, float z, float u, float v, float c) {
        consumer.vertex(matrix, x, HEIGHT, z).color(c, c, c, 1F).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(normal, 0F, 1F, 0F).endVertex();
    }

    /** 图案伸出本格一圈，按 getRenderBoundingBox（3×3）剔除 */
    @Override
    public boolean shouldRenderOffScreen(WindRuneBlockEntity be) {
        return true;
    }
}
