package com.flameforge.essentialcraft5.client.render;

import com.flameforge.essentialcraft5.blockentity.SolarPrismBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 太阳棱镜的光柱。对应原作 RenderSolarBeam（光束实体的渲染器）：信标光束贴图、黄色 (1, 1, 0)，
 * 从落点（被照的那个方块的底面）一直画到世界顶部，穿过棱镜本身。
 * <p>
 * 光束不做实体：服务端出光束时给追踪棱镜所在区块的玩家发方块事件包，棱镜的方块实体在客户端记下落点深度和结束时间
 * （{@link SolarPrismBlockEntity#triggerEvent}），这里只在那 20 刻里画。原作内芯半径 0、光晕 0.5；0 半径在 1.20.1 的 renderBeaconBeam 里会除以零，
 * 这里内芯用信标同款 0.2。
 * 渲染范围见 {@link SolarPrismBlockEntity#getRenderBoundingBox()}（有光柱时往下到落点、往上到世界顶部）；
 * 可见距离照 BeaconRenderer：水平 256 格，不看高度。默认的三维 64 格会让棱镜架得比吸收器高 64 格以上时，站在吸收器旁的人看不到光柱。
 */
public class SolarPrismRenderer implements BlockEntityRenderer<SolarPrismBlockEntity> {

    private static final float[] BEAM_COLOR = {1F, 1F, 0F};
    private static final float BEAM_RADIUS = 0.2F;
    private static final float GLOW_RADIUS = 0.5F;

    public SolarPrismRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(SolarPrismBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        long gameTime = level.getGameTime();
        if (!be.isBeamVisible(gameTime)) {
            return;
        }
        int depth = be.clientBeamDepth();
        int bottom = be.getBlockPos().getY() - depth;
        int height = level.getMaxBuildHeight() - bottom;
        if (height <= 0) {
            return;
        }
        pose.pushPose();
        pose.translate(0D, -depth, 0D);
        BeaconRenderer.renderBeaconBeam(pose, buffers, BeaconRenderer.BEAM_LOCATION, partialTick, 1F, gameTime, 0, height,
                BEAM_COLOR, BEAM_RADIUS, GLOW_RADIUS);
        pose.popPose();
    }

    /** 光柱远远超出本格，和信标一样放进全局列表，按 getRenderBoundingBox 做视锥剔除 */
    @Override
    public boolean shouldRenderOffScreen(SolarPrismBlockEntity be) {
        return true;
    }

    /** 同 BeaconRenderer：256 格 */
    @Override
    public int getViewDistance() {
        return 256;
    }

    /** 同 BeaconRenderer：只比水平距离（光柱竖着贯穿整列，镜头在哪个高度都该看得见） */
    @Override
    public boolean shouldRender(SolarPrismBlockEntity be, Vec3 camera) {
        return Vec3.atCenterOf(be.getBlockPos()).multiply(1D, 0D, 1D).closerThan(camera.multiply(1D, 0D, 1D), getViewDistance());
    }
}
