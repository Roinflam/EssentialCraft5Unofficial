package com.flameforge.essentialcraft5.client.render;

import com.flameforge.essentialcraft5.blockentity.SolarPrismBlockEntity;
import com.flameforge.essentialcraft5.client.vfx.machine.MachineVfx;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 太阳棱镜的光柱。对应原作 RenderSolarBeam（光束实体的渲染器，信标光束贴图、黄色）：
 * 从落点（被照的那个方块的底面）一直画到世界顶部，穿过棱镜本身。现在画成日阵金的等离子光束（{@code client/vfx/machine/BeamVfx#prism}），
 * 这里只登记，在特效阶段画。
 * <p>
 * 光束不做实体：服务端出光束时给追踪棱镜所在区块的玩家发方块事件包，棱镜的方块实体在客户端记下落点深度和结束时间
 * （{@link SolarPrismBlockEntity#triggerEvent}），这里只在那 20 刻里登记。
 * 渲染范围见 {@link SolarPrismBlockEntity#getRenderBoundingBox()}（有光柱时往下到落点、往上到世界顶部）；
 * 可见距离照 BeaconRenderer：水平 256 格，不看高度。默认的三维 64 格会让棱镜架得比吸收器高 64 格以上时，站在吸收器旁的人看不到光柱。
 */
public class SolarPrismRenderer implements BlockEntityRenderer<SolarPrismBlockEntity> {

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
        if (level.getMaxBuildHeight() <= be.getBlockPos().getY() - be.clientBeamDepth()) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        MachineVfx.submit(MachineVfx.PRISM, be, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
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
