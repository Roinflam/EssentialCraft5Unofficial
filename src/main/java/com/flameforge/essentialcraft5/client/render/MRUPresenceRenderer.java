package com.flameforge.essentialcraft5.client.render;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.MRUVision;
import com.flameforge.essentialcraft5.entity.MRUPresenceEntity;
import com.flameforge.essentialcraft5.mru.MRUBalance;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;

import java.util.function.Predicate;

/**
 * MRU 聚集体的渲染。对应原作 RenderMRUPresence（和原版末影龙死亡时的光芒是同一段代码）。
 * <p>
 * 从中心向随机方向射出一簇三棱锥形的光线：颜色按平衡值（纯净青、冰冻蓝、混沌红，同 {@link MRUBalance#rgb}），
 * 半透明叠加混合、不做深度测试（原作 GL_ALWAYS，隔着方块也看得见），整体缩放 0.0000075 × MRU，每 tick 按平衡值缓慢自转。
 * 随机数种子固定为 432，和原作一样每帧画出同样的一簇。
 * <p>
 * 移植改动：
 * <ul>
 *   <li>原作画 MRU/25 条光线，满 20000 MRU 时 800 条；这里最多画 {@link #MAX_RAYS} 条。</li>
 *   <li>原作只有「能看见 MRU」的玩家（手持 / 戴着魔力单片镜等，ECUtils.canPlayerSeeMRU）才画，而且隔墙可见。
 *       batch2 D12 定为：<b>所有人都看得见</b>（{@link #alwaysVisible} 恒为 true，按普通深度测试画，不能隔墙看）；
 *       能看见 MRU 的玩家（{@link MRUVision#canSee}，物品标签 essentialcraft5:mru_vision）照原作不做深度测试，隔着方块也看得见。</li>
 *   <li>隔墙靠两点：光线 RenderType 用 NO_DEPTH_TEST；实体 noCulling = true，所以原版视锥剔除和渲染优化模组的实体遮挡剔除
 *       （它注入在 EntityRenderer.shouldRender 里 Frustum.isVisible 之后，noCulling 的实体走不到那里）都不会把墙后的聚集体跳过。</li>
 *   <li>原作 GL_TRIANGLE_FAN（中心 + 三个底角）拆成 3 个三角形；1.20.1 原版龙光芒用的 lightning 是四边形模式，套不上。</li>
 * </ul>
 */
public class MRUPresenceRenderer extends EntityRenderer<MRUPresenceEntity> {

    private static final ResourceLocation TEXTURE = EssentialCraft5.id("textures/special/whitebox.png");
    private static final float HALF_SQRT_3 = (float) (Math.sqrt(3D) / 2D);
    /** 光线条数上限（原作 MRU/25，不设上限） */
    public static final int MAX_RAYS = 160;
    /** 原作 0.5 的透明度 */
    private static final int ALPHA = 128;

    /**
     * 谁能「看见 MRU」（原作 ECUtils.canPlayerSeeMRU：手持或头戴 MRU 可见物品），默认 {@link MRUVision#canSee}。
     * 这些玩家看到的光线不做深度测试，隔着方块也看得见，同原作。要加新的可见物品往 essentialcraft5:mru_vision 标签里加，不用改这里。
     */
    public static Predicate<Player> canSeeMRU = MRUVision::canSee;
    /**
     * 看不见 MRU 的玩家是否也画（原作不画）。D12 定为所有人都看得见，保持 true；
     * 这时光线按普通深度测试画，不能隔墙看见——否则人人都能透过地面找到埋着的上古遗迹，隔墙是单片镜的用处。
     */
    public static boolean alwaysVisible = true;

    public MRUPresenceRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0F;
    }

    @Override
    public void render(MRUPresenceEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        Player player = Minecraft.getInstance().player;
        int mru = entity.getMRU();
        if (player != null && mru > 0) {
            if (canSeeMRU.test(player)) {
                renderRays(entity, mru, partialTick, poseStack, buffers.getBuffer(RayRenderType.RAYS_SEE_THROUGH));
            } else if (alwaysVisible) {
                renderRays(entity, mru, partialTick, poseStack, buffers.getBuffer(RayRenderType.RAYS));
            }
        }
        super.render(entity, yaw, partialTick, poseStack, buffers, packedLight);
    }

    private static void renderRays(MRUPresenceEntity entity, int mru, float partialTick, PoseStack poseStack, VertexConsumer consumer) {
        float[] rgb = MRUBalance.rgb(entity.getBalance());
        int r = (int) (rgb[0] * 255F);
        int g = (int) (rgb[1] * 255F);
        int b = (int) (rgb[2] * 255F);
        float index = Mth.lerp(partialTick, entity.renderIndexO, entity.renderIndex);
        int rays = Math.min(mru / 25, MAX_RAYS);
        RandomSource random = RandomSource.create(432L);

        poseStack.pushPose();
        float scale = 0.0000075F * mru;
        poseStack.scale(scale, scale, scale);
        for (int i = 0; i < rays; ++i) {
            // 旋转是累积的（每条在上一条的基础上再转），与原作一致
            poseStack.mulPose(Axis.XP.rotationDegrees(random.nextFloat() * 360F));
            poseStack.mulPose(Axis.YP.rotationDegrees(random.nextFloat() * 360F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(random.nextFloat() * 360F + index * 90F));
            float length = random.nextFloat() * 20F + 5F;
            float width = random.nextFloat() * 2F + 1F;
            Matrix4f matrix = poseStack.last().pose();
            float x1 = -HALF_SQRT_3 * width, z1 = -width / 2F;
            float x2 = HALF_SQRT_3 * width, z2 = -width / 2F;
            float x3 = 0F, z3 = width;
            triangle(consumer, matrix, x1, z1, x2, z2, length, r, g, b);
            triangle(consumer, matrix, x2, z2, x3, z3, length, r, g, b);
            triangle(consumer, matrix, x3, z3, x1, z1, length, r, g, b);
        }
        poseStack.popPose();
    }

    /** 中心点 + 远端两个角 */
    private static void triangle(VertexConsumer consumer, Matrix4f matrix, float xa, float za, float xb, float zb, float length,
                                 int r, int g, int b) {
        consumer.vertex(matrix, 0F, 0F, 0F).color(r, g, b, ALPHA).endVertex();
        consumer.vertex(matrix, xa, length, za).color(r, g, b, ALPHA).endVertex();
        consumer.vertex(matrix, xb, length, zb).color(r, g, b, ALPHA).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(MRUPresenceEntity entity) {
        return TEXTURE;
    }

    /** 继承 RenderType 只是为了用 RenderStateShard 里 protected 的状态常量 */
    private static final class RayRenderType extends RenderType {

        /** 原作：SRC_ALPHA/ONE 叠加混合、GL_ALWAYS 深度测试、不写深度，顶点只有位置和颜色 */
        static final RenderType RAYS_SEE_THROUGH = create(EssentialCraft5.MODID + ":mru_presence_rays_see_through",
                DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES, 256, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                        .setTransparencyState(LIGHTNING_TRANSPARENCY)
                        .setDepthTestState(NO_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));

        /** 同上，但按普通深度测试（LEQUAL，默认值），被方块挡住的部分不画 */
        static final RenderType RAYS = create(EssentialCraft5.MODID + ":mru_presence_rays",
                DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES, 256, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                        .setTransparencyState(LIGHTNING_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));

        private RayRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                              boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
        }
    }
}
