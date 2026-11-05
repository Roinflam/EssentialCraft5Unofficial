package com.flameforge.essentialcraft5.client.render;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.MagicalMirrorBlockEntity;
import com.flameforge.essentialcraft5.client.guide.StaticBlockPreview;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

/**
 * 魔力镜的方块实体渲染器，对应原作 RenderMagicalMirror（98 行），变换照抄：
 * <ul>
 *   <li>镜框（OBJ 分组 pCube2，贴图 armtextures）和镜面（pPlane1，贴图 mirror）从方块中心往下 0.5 格起画，
 *       每 120 刻上下浮 0.25 格一个来回（三角波，原作 L29~37）；</li>
 *   <li>链接了目标就转向它：偏航 = −atan2(dz, dx) − 90°，俯仰 = atan2(dy − 浮动, 水平距离)（原作 L38~48）；</li>
 *   <li>传送中镜面按 20 刻一个周期在白色和品红之间闪（绿色分量 1 → 0 → 1，原作 L54~64）；</li>
 *   <li>送的那件东西：前三分之一的时间从镜子下方 0.3 格升到镜子里 0.7 格，之后直线飞到目标方块中心，每刻转 1°（原作 L72~80：
 *       20 刻上升、40 刻飞行；原作飞行起点是 0.5 格，和上升终点 0.7 差 0.2 格会跳一下，这里接上了）。时长按服务端记下的 {@code duration} 换算。</li>
 * </ul>
 * 原作镜面正反各画一次（开着背面剔除），这里镜面用不剔除背面的渲染类型画一次。原作关了标准物品光照，这里用实体的光照（明暗面略有不同）。
 * 两份模型是 ECMirror 里 datagen 生成的额外模型（MirrorClient 登记），逐面画、不读模型自带的顶点色（原作 MTL 的漫反射是黑色）。
 * 渲染范围只包住镜子到目标（方块实体的 getRenderBoundingBox），不再全局渲染（原作 isGlobalRenderer，M-7）。
 * 研究手册的立体结构图画不浮动、没链接、不在传送的一帧（{@link #renderPreview}）。
 */
public class MagicalMirrorRenderer implements BlockEntityRenderer<MagicalMirrorBlockEntity>, StaticBlockPreview {

    /** 额外模型：models/block/magical_mirror_frame.json（mirror.obj 只留 pCube2） */
    public static final ResourceLocation FRAME = EssentialCraft5.id("block/magical_mirror_frame");
    /** 额外模型：models/block/magical_mirror_glass.json（mirror.obj 只留 pPlane1） */
    public static final ResourceLocation GLASS = EssentialCraft5.id("block/magical_mirror_glass");

    private static final RenderType FRAME_TYPE = RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS);
    private static final RenderType GLASS_TYPE = RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS);

    private final RandomSource random = RandomSource.create();

    public MagicalMirrorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(MagicalMirrorBlockEntity mirror, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        Level level = mirror.getLevel();
        if (level == null) {
            return;
        }
        // 先在 long 上取模再加 partialTick：游戏时间大了以后 float 装不下小数，动画会一顿一顿的
        long time = level.getGameTime();
        float cycle = time % 120 + partialTick;
        float bob = cycle <= 60F ? cycle / 240F : 0.5F - cycle / 240F;
        BlockPos pos = mirror.getBlockPos();
        BlockPos target = mirror.getTarget();

        pose.pushPose();
        pose.translate(0.5F, -0.5F + bob, 0.5F);
        if (target != null) {
            double dx = target.getX() - pos.getX();
            double dy = target.getY() - pos.getY() - bob;
            double dz = target.getZ() - pos.getZ();
            float yaw = (float) (-Math.toDegrees(Math.atan2(dz, dx)) - 90D);
            float pitch = (float) Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            pose.mulPose(Axis.YP.rotationDegrees(yaw));
            pose.mulPose(Axis.XP.rotationDegrees(pitch));
        }
        var models = Minecraft.getInstance().getModelManager();
        draw(models.getModel(FRAME), pose.last(), buffers.getBuffer(FRAME_TYPE), 1F, light);
        float green = 1F;
        if (mirror.isTransferring()) {
            float pulse = time % 20 + partialTick;
            green = pulse <= 10F ? 1F - pulse / 10F : (pulse - 10F) / 10F;
        }
        draw(models.getModel(GLASS), pose.last(), buffers.getBuffer(GLASS_TYPE), green, light);
        pose.popPose();

        ItemStack sample = mirror.getSample();
        if (mirror.isTransferring() && !sample.isEmpty() && target != null) {
            renderFlyingItem(mirror, sample, pos, target, time, partialTick, pose, buffers, light, level);
        }
    }

    private void renderFlyingItem(MagicalMirrorBlockEntity mirror, ItemStack sample, BlockPos pos, BlockPos target, long time,
                                  float partialTick, PoseStack pose, MultiBufferSource buffers, int light, Level level) {
        float duration = Math.max(3, mirror.getDuration());
        float elapsed = Mth.clamp((float) (time - mirror.getStartTick()) + partialTick, 0F, duration);
        float rise = duration / 3F;
        double x;
        double y;
        double z;
        if (elapsed < rise) {
            x = 0.5D;
            y = -0.3D + elapsed / rise;
            z = 0.5D;
        } else {
            double f = (elapsed - rise) / (duration - rise);
            x = 0.5D + (target.getX() - pos.getX()) * f;
            y = 0.7D + (target.getY() - pos.getY() + 0.5D - 0.7D) * f;
            z = 0.5D + (target.getZ() - pos.getZ()) * f;
        }
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(Axis.YP.rotationDegrees(time % 360 + partialTick));
        Minecraft.getInstance().getItemRenderer().renderStatic(sample, ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY,
                pose, buffers, level, (int) pos.asLong());
        pose.popPose();
    }

    /** 浮动取一个来回的正中（离方块中心往下 0.375 格） */
    private static final float PREVIEW_BOB = 0.125F;

    @Override
    public void renderPreview(BlockState state, PoseStack pose, MultiBufferSource buffers) {
        var models = Minecraft.getInstance().getModelManager();
        pose.pushPose();
        pose.translate(0.5F, -0.5F + PREVIEW_BOB, 0.5F);
        draw(models.getModel(FRAME), pose.last(), buffers.getBuffer(FRAME_TYPE), 1F, LightTexture.FULL_BRIGHT);
        draw(models.getModel(GLASS), pose.last(), buffers.getBuffer(GLASS_TYPE), 1F, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    private void draw(BakedModel model, PoseStack.Pose pose, VertexConsumer consumer, float green, int light) {
        for (Direction side : Direction.values()) {
            drawQuads(model, side, pose, consumer, green, light);
        }
        drawQuads(model, null, pose, consumer, green, light);
    }

    private void drawQuads(BakedModel model, @Nullable Direction side, PoseStack.Pose pose, VertexConsumer consumer, float green, int light) {
        random.setSeed(42L);
        for (BakedQuad quad : model.getQuads(null, side, random, ModelData.EMPTY, null)) {
            consumer.putBulkData(pose, quad, 1F, green, 1F, 1F, light, OverlayTexture.NO_OVERLAY, false);
        }
    }

    /** 渲染范围由方块实体的 getRenderBoundingBox 给（镜子到目标），这里不画屏幕外的 */
    @Override
    public boolean shouldRenderOffScreen(MagicalMirrorBlockEntity mirror) {
        return false;
    }
}
