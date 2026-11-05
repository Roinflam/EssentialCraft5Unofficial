package com.flameforge.essentialcraft5.client.render;

import com.flameforge.essentialcraft5.api.MagicianTableUpgrades;
import com.flameforge.essentialcraft5.blockentity.MagicianTableBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 魔力工作台里的升级方块。原作 RenderMagicianTable：装了升级时，在工作台模型里面画一个缩到 0.99、抬高 0.005 的立方体，
 * 贴图是该升级的展开图（原作 models/block/cube.obj），透过工作台模型的镂空处能看到。原作这个 TESR 只画这一样东西，没有悬浮物品。
 * <p>
 * cube.obj 只有 8 个顶点、6 个面，这里直接把它的顶点和 UV 抄进代码，不走 OBJ 加载器。
 * 原作 DummyCore 的 OBJ 加载器读 UV 时做了 v → 1 - v（和方块模型的 flip-v 一样），这里同样翻转。
 */
public class MagicianTableRenderer implements BlockEntityRenderer<MagicianTableBlockEntity> {

    /** cube.obj 的顶点：x、z 在 [-0.5, 0.5]，y 在 [0, 1] */
    private static final float[][] POSITIONS = {
            {-0.5F, 0F, 0.5F}, {0.5F, 0F, 0.5F}, {-0.5F, 1F, 0.5F}, {0.5F, 1F, 0.5F},
            {-0.5F, 1F, -0.5F}, {0.5F, 1F, -0.5F}, {-0.5F, 0F, -0.5F}, {0.5F, 0F, -0.5F}
    };
    /** cube.obj 的 UV（原值，渲染时翻转 v） */
    private static final float[][] UVS = {
            {0.24997F, 0F}, {0.49997F, 0F}, {0.24997F, 0.25F}, {0.49997F, 0.25F}, {0.24997F, 0.5F},
            {0.49997F, 0.5F}, {0.24997F, 0.75F}, {0.49997F, 0.75F}, {0.24997F, 1F}, {0.49997F, 1F},
            {0.74997F, 0F}, {0.74997F, 0.25F}, {-0.00003F, 0F}, {-0.00003F, 0.25F}
    };
    /** 每个面：4 个 {顶点下标, UV 下标}（OBJ 的顺序，从外面看是逆时针） */
    private static final int[][][] FACES = {
            {{0, 0}, {1, 1}, {3, 3}, {2, 2}},
            {{2, 2}, {3, 3}, {5, 5}, {4, 4}},
            {{4, 4}, {5, 5}, {7, 7}, {6, 6}},
            {{6, 6}, {7, 7}, {1, 9}, {0, 8}},
            {{1, 1}, {7, 10}, {5, 11}, {3, 3}},
            {{6, 12}, {0, 0}, {2, 2}, {4, 13}}
    };
    /** 各面法线：南、上、北、下、东、西 */
    private static final float[][] NORMALS = {
            {0F, 0F, 1F}, {0F, 1F, 0F}, {0F, 0F, -1F}, {0F, -1F, 0F}, {1F, 0F, 0F}, {-1F, 0F, 0F}
    };

    public MagicianTableRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(MagicianTableBlockEntity table, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        MagicianTableUpgrades.Upgrade upgrade = MagicianTableUpgrades.get(table.getUpgrade());
        if (upgrade == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.005D, 0.5D);
        poseStack.scale(0.99F, 0.99F, 0.99F);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entitySolid(upgrade.texture()));
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        for (int face = 0; face < FACES.length; face++) {
            float[] n = NORMALS[face];
            for (int[] corner : FACES[face]) {
                float[] p = POSITIONS[corner[0]];
                float[] uv = UVS[corner[1]];
                consumer.vertex(pose, p[0], p[1], p[2])
                        .color(255, 255, 255, 255)
                        .uv(uv[0], 1F - uv[1])
                        .overlayCoords(packedOverlay)
                        .uv2(packedLight)
                        .normal(normal, n[0], n[1], n[2])
                        .endVertex();
            }
        }
        poseStack.popPose();
    }
}
