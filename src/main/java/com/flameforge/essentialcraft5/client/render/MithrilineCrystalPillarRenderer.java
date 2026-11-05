package com.flameforge.essentialcraft5.client.render;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.MithrilineCrystalPillarBlockEntity;
import com.flameforge.essentialcraft5.client.guide.StaticBlockPreview;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

/**
 * 米斯莱水晶柱里浮着转的水晶。对应原作 RenderMithrilineCrystal：OBJ 模型 mithrilinecrystal（上下两个尖锥，高 1、半径 0.125，
 * 贴图 textures/models/mithrilinecrystal），放大 2 倍，绕竖轴每 45 tick 转一圈（原作 time % 45 × 8°），
 * 每 60 tick 在底座上方 0~1 格之间上下浮一次（三角波），不受光照影响（原作关了光照）、透明度 0.9。
 * <p>
 * OBJ 用 Forge 的 OBJ 加载器烘焙成额外模型 {@link #MODEL}（模型文件由 datagen 随方块一起生成，在 MithrilineClient 里登记），
 * 这里逐个面按透明度 0.9、不分明暗地画（见 {@link #RENDER_TYPE}）。模型的 MTL 已经改成白色漫反射、贴图指向 block/model/mithrilinecrystal
 * （原作 MTL 写的是黑色漫反射和护板贴图，原作 TESR 自己绑定贴图所以没受影响；重跑 import_assets.py 会把它改回去）。
 * 方块实体只在底座，所以不用像原作那样判断 meta。
 * <p>
 * 研究手册的立体结构图里画固定的一帧（{@link StaticBlockPreview}）：水晶停在半高、转一个小角度，不跟着世界时间动。
 */
public class MithrilineCrystalPillarRenderer implements BlockEntityRenderer<MithrilineCrystalPillarBlockEntity>, StaticBlockPreview {

    /** 手册立体图里那一帧：转过的角度、浮起的高度（0~30，对应底座上方 0~1 格） */
    private static final float PREVIEW_SPIN = 20F;
    private static final float PREVIEW_MOVEMENT = 15F;

    /** 额外模型：models/block/mithriline_crystal_pillar_floating.json（OBJ） */
    public static final ResourceLocation MODEL = EssentialCraft5.id("block/mithriline_crystal_pillar_floating");
    private static final float ALPHA = 0.9F;
    /**
     * 原作关了光照（不分明暗面、不吃亮度）、按透明度混合。原版实体的 RenderType 都会按法线分明暗面，
     * 信标光柱的不会：方块图集 + 透明、不写深度（水晶是凸的，背面剔除后不会有前后遮挡的问题）
     */
    private static final RenderType RENDER_TYPE = RenderType.beaconBeam(InventoryMenu.BLOCK_ATLAS, true);

    private final RandomSource random = RandomSource.create();
    /** 画哪个额外模型（默认米斯莱的 {@link #MODEL}） */
    private final ResourceLocation model;

    public MithrilineCrystalPillarRenderer(BlockEntityRendererProvider.Context context) {
        this(context, MODEL);
    }

    /**
     * 别的档位的水晶柱用：同样的浮动、旋转、透明度，换一个额外模型（第三批 ECPentacle 的淡青柱：
     * 同一个 OBJ，MTL 换成淡青贴图）。模型要在模块自己的客户端类里用 ModelEvent.RegisterAdditional 登记。
     *
     * @param model 额外模型的位置（如 essentialcraft5:block/pale_crystal_pillar_floating）
     */
    public MithrilineCrystalPillarRenderer(BlockEntityRendererProvider.Context context, ResourceLocation model) {
        this.model = model;
    }

    @Override
    public void render(MithrilineCrystalPillarBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        // 先在 long 上取模再加 partialTick：游戏时间大了以后 float 装不下小数，动画会一顿一顿的
        long time = level.getGameTime();
        float spin = (time % 45 + partialTick) * 8F;
        float movement = time % 60 + partialTick;
        if (movement > 30F) {
            movement = 60F - movement;
        }
        drawCrystal(pose, buffers, spin, movement);
    }

    @Override
    public void renderPreview(BlockState state, PoseStack pose, MultiBufferSource buffers) {
        drawCrystal(pose, buffers, PREVIEW_SPIN, PREVIEW_MOVEMENT);
    }

    private void drawCrystal(PoseStack pose, MultiBufferSource buffers, float spin, float movement) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(this.model);
        pose.pushPose();
        pose.translate(0.5F, movement / 30F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        pose.scale(2F, 2F, 2F);
        VertexConsumer consumer = buffers.getBuffer(RENDER_TYPE);
        PoseStack.Pose last = pose.last();
        for (Direction side : Direction.values()) {
            draw(model, side, last, consumer);
        }
        draw(model, null, last, consumer);
        pose.popPose();
    }

    private void draw(BakedModel model, @Nullable Direction side, PoseStack.Pose pose, VertexConsumer consumer) {
        random.setSeed(42L);
        for (BakedQuad quad : model.getQuads(null, side, random, ModelData.EMPTY, null)) {
            // 不读模型自带的顶点色；光照、覆盖层这两项信标光柱的着色器不用，照常填
            consumer.putBulkData(pose, quad, 1F, 1F, 1F, ALPHA, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, false);
        }
    }
}
