package com.flameforge.essentialcraft5.client.render;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.DemonicPentacleBlockEntity;
import com.flameforge.essentialcraft5.client.vfx.machine.MachineVfx;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 恶魔五芒星地上的符文（原作 RenderDemonicPentacle）：原作画 rune.obj 的平面（和灵魂五芒星同一块正方形面片，四角 (0, 0.25, ±2.12)、(±2.12, 0.25, 0)），
 * 贴 {@code textures/models/demonicpentacle.png}，平移 (0.5, −0.2, 0.5)，所以在方块底面上方 0.05 格。rune.obj 没有导入，照 {@link PlayerPentacleRenderer} 直接画这块面片。
 * <p>
 * 成形时每 tick 转 1°（原作 worldTime % 360），红色亮度以 60 tick 为周期在 0.02~0.8 之间来回（原作 color(c, 0, 0)）。
 * 原作没成形时什么都不画，放下就找不到；这里画不转的暗灰符文（同灵魂五芒星不成形的 0.2 灰）。不受光照影响；正反两面都画。
 */
public class DemonicPentacleRenderer implements BlockEntityRenderer<DemonicPentacleBlockEntity> {

    /** 原作 textures/models/demonicpentacle.png（512×512，红色符文） */
    public static final ResourceLocation TEXTURE = EssentialCraft5.id("textures/block/model/demonicpentacle.png");
    private static final RenderType RENDER_TYPE = RenderType.beaconBeam(TEXTURE, true);
    /** 原作 OBJ 的 2.12132 = 1.5√2 */
    private static final float HALF_DIAGONAL = 2.12132F;
    /** 原作 0.25 − 0.2 */
    private static final float HEIGHT = 0.05F;
    private static final float UNFORMED = 0.2F;
    private static final float MIN_RED = 0.02F;
    private static final float MAX_RED = 0.8F;

    public DemonicPentacleRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(DemonicPentacleBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        float[] color;
        pose.pushPose();
        pose.translate(0.5F, HEIGHT, 0.5F);
        if (be.isFormed()) {
            color = new float[]{red(level.getGameTime(), partialTick), 0F, 0F};
            // 先在 long 上取模再加 partialTick，游戏时间大了也不会一顿一顿
            pose.mulPose(Axis.YP.rotationDegrees(level.getGameTime() % 360 + partialTick));
        } else {
            color = new float[]{UNFORMED, UNFORMED, UNFORMED};
        }
        VertexConsumer consumer = buffers.getBuffer(RENDER_TYPE);
        Matrix4f matrix = pose.last().pose();
        Matrix3f normal = pose.last().normal();
        float d = HALF_DIAGONAL;
        // 原作 UV：(0, 0, +d) → (0, 0)，(+d, 0, 0) → (1, 0)，(0, 0, −d) → (1, 1)，(−d, 0, 0) → (0, 1)
        float[][] corners = {{0F, d, 0F, 0F}, {d, 0F, 1F, 0F}, {0F, -d, 1F, 1F}, {-d, 0F, 0F, 1F}};
        for (int i = 0; i < 4; i++) {
            vertex(consumer, matrix, normal, corners[i], color, 1F);
        }
        for (int i = 3; i >= 0; i--) {
            vertex(consumer, matrix, normal, corners[i], color, -1F);
        }
        pose.popPose();
        MachineVfx.submit(MachineVfx.PENTACLE, be, be.getBlockPos().getX() + 0.5D, be.getBlockPos().getY(), be.getBlockPos().getZ() + 0.5D);
    }

    /** 原作 movement：0~30 升、30~60 降，除以 30 后夹到 [0.02, 0.8] */
    static float red(long gameTime, float partialTick) {
        float movement = gameTime % 60L + partialTick;
        if (movement > 30F) {
            movement = 60F - movement;
        }
        return Math.max(MIN_RED, Math.min(MAX_RED, movement / 30F));
    }

    /** corner = {x, z, u, v}；信标光柱的顶点格式是 BLOCK（位置、颜色、UV、光照、法线） */
    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal, float[] corner, float[] color, float normalY) {
        consumer.vertex(matrix, corner[0], 0F, corner[1])
                .color(color[0], color[1], color[2], 1F)
                .uv(corner[2], corner[3])
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(normal, 0F, normalY, 0F)
                .endVertex();
    }
}
