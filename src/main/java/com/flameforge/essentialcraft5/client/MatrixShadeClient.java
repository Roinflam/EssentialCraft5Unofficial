package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.boss.BossFxClient;
import com.flameforge.essentialcraft5.client.model.mob.MatrixShadeModel;
import com.flameforge.essentialcraft5.client.model.mob.MindSpikeModel;
import com.flameforge.essentialcraft5.client.render.mob.DisorderPillarRenderer;
import com.flameforge.essentialcraft5.client.render.mob.ECMobRenderer;
import com.flameforge.essentialcraft5.client.render.mob.MobTextures;
import com.flameforge.essentialcraft5.entity.boss.MatrixShadeEntity;
import com.flameforge.essentialcraft5.entity.boss.MatrixShadeMirror;
import com.flameforge.essentialcraft5.network.S2CBossFxPacket;
import com.flameforge.essentialcraft5.registry.ECMatrixShade;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 第十三批 13c-M4「矩阵之影」的客户端注册（模组总线，Forge 自动发现）：模型层（手写 {@code LayerDefinition}）、实体渲染器、悖论画面。
 * <ul>
 *   <li>矩阵之影：{@link MatrixShadeModel}（128 × 128），发光层随相变亮（身 0.55、心 0.75、矩阵 1），散去时 10 秒淡到 0；</li>
 *   <li>镜影：同一个模型、同一张主贴图（{@code matrix_shade.png}），发光层换成 {@code shade_mirror_glow.png}（胸口矩阵格是暗的）；</li>
 *   <li>心智钉：{@link MindSpikeModel}（32 × 32）带发光层；失序之柱：{@link DisorderPillarRenderer}；</li>
 *   <li>悖论画面（{@link S2CBossFxPacket#PARADOX}）登记成边缘晕影（a = 强度、b = 颜色，护眼规矩由 {@link BossFxClient} 管）。</li>
 * </ul>
 * 注册类是 {@code registry/ECMatrixShade}；刷怪蛋的颜色由 Forge 的 ForgeSpawnEggItem 自动注册。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MatrixShadeClient {

    /** 散去时发光层淡掉的刻数（和散去动画、dissipate_ticks 默认值一样长） */
    private static final float FADE_TICKS = 200F;

    private MatrixShadeClient() {
    }

    @SubscribeEvent
    public static void matrixShadeLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(MatrixShadeModel.LAYER, MatrixShadeModel::createLayer);
        event.registerLayerDefinition(MindSpikeModel.LAYER, MindSpikeModel::createLayer);
    }

    @SubscribeEvent
    public static void matrixShadeRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ECMatrixShade.MATRIX_SHADE.get(), ShadeRenderer::new);
        event.registerEntityRenderer(ECMatrixShade.SHADE_MIRROR.get(), MirrorRenderer::new);
        event.registerEntityRenderer(ECMatrixShade.MIND_SPIKE.get(),
                ctx -> new ECMobRenderer<>(ctx, new MindSpikeModel(ctx.bakeLayer(MindSpikeModel.LAYER)), 0.3F, "mind_spike").withGlow());
        event.registerEntityRenderer(ECMatrixShade.DISORDER_PILLAR.get(), DisorderPillarRenderer::new);
    }

    @SubscribeEvent
    public static void matrixShadeClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> BossFxClient.registerEdge(S2CBossFxPacket.PARADOX));
    }

    /** 发光层的强度：按相变亮；散去时 ticksIntoFade 刻里淡到 0 */
    static float glowFor(MatrixShadeEntity.Phase phase, boolean dissipating, int ticksIntoFade, float partialTick) {
        float base = switch (phase) {
            case BODY -> 0.55F;
            case MIND -> 0.75F;
            case MATRIX -> 1F;
        };
        if (!dissipating) {
            return base;
        }
        return base * (1F - Mth.clamp((ticksIntoFade + partialTick) / FADE_TICKS, 0F, 1F));
    }

    /** 矩阵之影 */
    static final class ShadeRenderer extends ECMobRenderer<MatrixShadeEntity, MatrixShadeModel<MatrixShadeEntity>> {

        ShadeRenderer(EntityRendererProvider.Context context) {
            super(context, new MatrixShadeModel<>(context.bakeLayer(MatrixShadeModel.LAYER)), 0.8F, "matrix_shade");
            withGlow();
        }

        @Override
        public float glowAlpha(MatrixShadeEntity entity, float partialTick) {
            int fade = entity.dissipating() ? Math.max(0, entity.tickCount - entity.dissipateStartTick()) : 0;
            return glowFor(entity.phase(), entity.dissipating(), fade, partialTick);
        }
    }

    /** 镜影：主贴图借矩阵之影的，发光层用自己的 */
    static final class MirrorRenderer extends ECMobRenderer<MatrixShadeMirror, MatrixShadeModel<MatrixShadeMirror>> {

        MirrorRenderer(EntityRendererProvider.Context context) {
            super(context, new MatrixShadeModel<>(context.bakeLayer(MatrixShadeModel.LAYER)), 0.8F, "shade_mirror");
            withGlow();
        }

        @Override
        protected ResourceLocation baseTexture(MatrixShadeMirror entity) {
            return MobTextures.base("matrix_shade");
        }

        @Override
        public float glowAlpha(MatrixShadeMirror entity, float partialTick) {
            return 0.75F;
        }
    }
}
