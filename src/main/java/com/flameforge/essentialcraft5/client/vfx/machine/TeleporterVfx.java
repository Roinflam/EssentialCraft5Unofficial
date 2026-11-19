package com.flameforge.essentialcraft5.client.vfx.machine;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.MagicalTeleporterBlockEntity;
import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import com.flameforge.essentialcraft5.registry.ECVoidMachines;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 魔力传送器：成形后脚下一层辉光；蓄能时中间那一列升起光柱，八根柱子顶上轮流往中间打电弧，进度越高越亮、越密，满了中间爆一下。
 * 传送器原来没有方块实体渲染器，这里补一个只登记、不画模型的。
 */
public final class TeleporterVfx implements BlockEntityRenderer<MagicalTeleporterBlockEntity> {

    private static final VfxPlasma.Tint TINT = VfxPlasma.MRU;
    /** 结构里两格高的柱子（相对机器，顶面在 y + 3） */
    private static final int[][] PILLARS = {{1, 2}, {-1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, 1}, {-2, -1}};

    public TeleporterVfx(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(MagicalTeleporterBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.isFormed() || be.clientChargeProgress() >= 0F) {
            BlockPos pos = be.getBlockPos();
            MachineVfx.submit(MachineVfx.TELEPORTER, be, pos.getX() + 0.5D, pos.getY() + 1D, pos.getZ() + 0.5D);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(MagicalTeleporterBlockEntity be) {
        return true;
    }

    static void draw(VfxDraw draw, Object ref, float dist) {
        if (!(ref instanceof MagicalTeleporterBlockEntity be)) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 1D;
        double z = pos.getZ() + 0.5D;
        float progress = be.clientChargeProgress();
        float breathe = 0.8F + 0.2F * Mth.sin((draw.time() + (pos.asLong() & 127)) * 0.12F);
        draw.flat(new Vec3(x, y, z), 1.6F, 0F, VfxMode.DOT, 0, Vfx.argb(TINT.glow(), (progress >= 0F ? 0.3F + 0.3F * progress : 0.14F) * breathe));
        if (progress < 0F) {
            return;
        }
        float a = 0.25F + 0.75F * progress;
        VfxPlasma.beam(draw, new Vec3(x, y, z), new Vec3(x, y + 2.6D, z), 0.25F + 0.25F * progress, 0.55F * a, true, (int) (pos.asLong() & 1023), TINT);
        long key = pos.asLong();
        long epoch = ArcCache.epoch(key, 3);
        // 进度越高同时亮的柱子越多：1~4 根，按轮次换
        int lit = 1 + Math.round(progress * 3F);
        for (int k = 0; k < lit; k++) {
            int i = (int) ((epoch * 3L + k * 2L) & 7);
            double px = x + PILLARS[i][0];
            double pz = z + PILLARS[i][1];
            double[] path = ArcCache.path(ArcCache.key(key, i), epoch, px, y + 2D, pz, x, y + 1.2D + progress, z, 0.2F);
            CoilVfx.arc(draw, path, ArcCache.POINTS, 0.03F, a * (k == 0 ? 1F : 0.75F), dist, i * 97);
        }
        if (progress > 0.97F) {
            VfxPlasma.burst(draw, draw.towardCamera(new Vec3(x, y + 1.2D, z), 0.5D), 1.4F, (progress - 0.97F) * 300F, 9F, (int) (key & 1023), TINT);
        }
    }

    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {

        private Registration() {
        }

        @SubscribeEvent
        public static void onTeleporterVfxRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(ECVoidMachines.MAGICAL_TELEPORTER_BE.get(), TeleporterVfx::new);
        }
    }
}
