package com.flameforge.essentialcraft5.client.render;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.HologramClient;
import com.flameforge.essentialcraft5.entity.HologramEntity;
import com.flameforge.essentialcraft5.registry.ECComputer;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * 全息投影的渲染（原作 {@code RenderHologram}）：原版人形模型 + {@code entity/boss.png}，半透明；头顶 {@value #CORE_HEIGHT} 格转着一个核心物品。
 * <ul>
 *   <li>贴图是 64 × 32 的老布局（原作 {@code ModelBiped(0, 0, 64, 32)}），模型层按 64 × 32 建（{@link HologramClient#HOLOGRAM_LAYER}，左手左腿镜像右边）。</li>
 *   <li>混合照原作：顶点透明度 {@value #ALPHA}、源颜色不乘透明度（{@code GL_ONE, GL_ONE_MINUS_SRC_ALPHA}），后面的东西只暗两成、贴图颜色直接加上去，
 *       看起来是一层发光的影子；不写深度（{@link #ghost}，玩家克隆体共用）。</li>
 *   <li>头顶的核心：出招时是对应的数学核心（加 / 减 / 乘 / 除），休息时是故障核心（原作 L56~78），每刻转 1 度，满亮度。</li>
 * </ul>
 */
public class HologramRenderer extends MobRenderer<HologramEntity, HumanoidModel<HologramEntity>> {

    public static final ResourceLocation TEXTURE = EssentialCraft5.id("textures/entity/boss.png");
    /** 原作 GlStateManager.color(1, 1, 1, 0.2F) */
    public static final float ALPHA = 0.2F;
    /** 原作 renderItemStack_Full 的 y 偏移 2.4 */
    public static final float CORE_HEIGHT = 2.4F;

    private final ItemRenderer itemRenderer;

    public HologramRenderer(EntityRendererProvider.Context context) {
        super(context, new GhostHumanoidModel(context.bakeLayer(HologramClient.HOLOGRAM_LAYER)), 0.5F);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public ResourceLocation getTextureLocation(HologramEntity entity) {
        return TEXTURE;
    }

    @Override
    protected @Nullable RenderType getRenderType(HologramEntity entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        return bodyVisible || translucent ? ghost(TEXTURE) : super.getRenderType(entity, bodyVisible, translucent, glowing);
    }

    @Override
    public void render(HologramEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        super.render(entity, yaw, partialTick, pose, buffers, light);
        ItemStack core = coreFor(entity.attackId());
        if (core.isEmpty()) {
            return;
        }
        pose.pushPose();
        pose.translate(0F, CORE_HEIGHT, 0F);
        pose.mulPose(Axis.YP.rotationDegrees((entity.tickCount + partialTick) % 360F));
        itemRenderer.renderStatic(core, ItemDisplayContext.GROUND, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose, buffers,
                entity.level(), entity.getId());
        pose.popPose();
    }

    /** 招式 → 头顶的核心：休息 = 故障核心，0~3 = 加法 / 减法 / 乘法 / 除法核心 */
    public static ItemStack coreFor(int attack) {
        Item item = switch (attack) {
            case HologramEntity.ADDITION -> ECComputer.ADDITION_CORE.get();
            case HologramEntity.SUBTRACTION -> ECComputer.SUBTRACTION_CORE.get();
            case HologramEntity.MULTIPLICATION -> ECComputer.MULTIPLICATION_CORE.get();
            case HologramEntity.DIVISION -> ECComputer.DIVISION_CORE.get();
            default -> ECMaterials.GLITCHY_CORE.get();
        };
        return new ItemStack(item);
    }

    /** 全息影子的渲染类型（每张贴图一份）：原版实体半透明着色器、源颜色不乘透明度的混合、不剔背面、不写深度 */
    public static RenderType ghost(ResourceLocation texture) {
        return GhostRenderType.GHOST.apply(texture);
    }

    /** 渲染时把透明度乘上 {@value #ALPHA}（原作是全局颜色，这里交给模型） */
    static final class GhostHumanoidModel extends HumanoidModel<HologramEntity> {

        GhostHumanoidModel(ModelPart root) {
            super(root);
        }

        @Override
        public void renderToBuffer(PoseStack pose, VertexConsumer consumer, int light, int overlay, float red, float green, float blue,
                                   float alpha) {
            super.renderToBuffer(pose, consumer, light, overlay, red, green, blue, alpha * ALPHA);
        }
    }

    private static final class GhostRenderType extends RenderType {

        /** 原作 GL_ONE / GL_ONE_MINUS_SRC_ALPHA */
        private static final TransparencyStateShard GHOST_TRANSPARENCY = new TransparencyStateShard(EssentialCraft5.MODID + ":ghost_transparency",
                () -> {
                    RenderSystem.enableBlend();
                    RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                            GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
                },
                () -> {
                    RenderSystem.disableBlend();
                    RenderSystem.defaultBlendFunc();
                });

        static final Function<ResourceLocation, RenderType> GHOST = Util.memoize(texture -> create(EssentialCraft5.MODID + ":hologram_ghost",
                DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, true,
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                        .setTextureState(new TextureStateShard(texture, false, false))
                        .setTransparencyState(GHOST_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(OVERLAY)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(true)));

        private GhostRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling,
                                boolean sortOnUpload, Runnable setup, Runnable clear) {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
        }
    }
}
