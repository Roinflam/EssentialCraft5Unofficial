package com.flameforge.essentialcraft5.client.render;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.PlayerPentacleBlockEntity;
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
 * 灵魂五芒星地上的符文（原作 RenderPlayerPentacle）：OBJ rune.obj 的 pPlane1 就是一个水平的正方形，四个角在 (0, 0.25, ±2.12)、(±2.12, 0.25, 0)
 * （边长 3、斜放 45°），贴 textures/models/pentacle.png；原作平移 (0.5, −0.2, 0.5) 后画，所以在方块底面上方 0.05 格。
 * 这里直接画这个四边形，不再烘焙 OBJ。颜色按档位（{@link PlayerPentacleBlockEntity#tierColor}：不成形灰、0 档绿、1 档蓝、2 档紫、3 档红），
 * 成形后每 tick 转 1°（原作 worldTime % 360），不受光照影响（原作关了光照）。
 * <p>
 * 贴图带透明（线条之外全透明），用信标光柱的渲染方式：不分明暗面、按透明度混合、不写深度；正反两面都画（从下面透过玻璃也看得见）。
 * 符文比方块大，渲染范围见方块实体的 getRenderBoundingBox（5×1×5）。
 */
public class PlayerPentacleRenderer implements BlockEntityRenderer<PlayerPentacleBlockEntity> {

    /** 原作 textures/models/pentacle.png（512×512） */
    public static final ResourceLocation TEXTURE = EssentialCraft5.id("textures/block/model/pentacle.png");
    private static final RenderType RENDER_TYPE = RenderType.beaconBeam(TEXTURE, true);
    /** 四个角到中心的距离：原作 OBJ 的 2.12132 = 1.5√2 */
    private static final float HALF_DIAGONAL = 2.12132F;
    /** 原作 0.25 − 0.2 */
    private static final float HEIGHT = 0.05F;

    public PlayerPentacleRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(PlayerPentacleBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        int tier = be.getTier();
        float[] color = PlayerPentacleBlockEntity.tierColor(tier);
        pose.pushPose();
        pose.translate(0.5F, HEIGHT, 0.5F);
        if (tier >= 0) {
            // 先在 long 上取模再加 partialTick，游戏时间大了也不会一顿一顿
            pose.mulPose(Axis.YP.rotationDegrees(level.getGameTime() % 360 + partialTick));
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
