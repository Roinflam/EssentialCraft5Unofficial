package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.item.OrbitalRemoteItem;
import com.flameforge.essentialcraft5.registry.ECHoloWeapons;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 「轨道打击遥控器与二分枪」的客户端部分（registry/ECHoloWeapons）：
 * <ul>
 *   <li>二分弹画成一块魔力熔渣（原版投掷物渲染器，原作 {@code ThrowableRenderFactory(magicalSlag)}）。</li>
 *   <li>遥控器的预览光柱（原作 {@code RenderHandlerEC} L256~290）：主手<b>或副手</b>拿着遥控器时，每帧按玩家这一刻的位置和朝向找落点
 *       （不插值，和右键时服务端用的 {@link OrbitalRemoteItem#aimFor} 是同一个函数），在落点画一道品红信标光柱
 *       （内径 0.2、外径 0.499，从打中那一格的顶面画到建筑上限；partialTick 只给光柱的动画用）。
 *       移动中光柱会比准星略超前一点，那就是真实落点。
 *       原作挂在雾事件上（一帧要发好几次，光柱叠着画好几遍）、只看主手、写死画到 y = 255。
 *       准星对着能锁定的生物时（第十批 10b，{@link OrbitalRemoteItem#lockTargetFor}），光柱改画在那只生物脚下、随它移动（按 partialTick 插值）。</li>
 * </ul>
 * 轨道打击、二分场两个技能实体的渲染器在 {@link HoloStrikeClient}。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HoloWeaponsClient {

    /** 光柱颜色（原作 new float[]{1, 0, 1}） */
    private static final float[] MAGENTA = {1F, 0F, 1F};
    /** 光柱内径、外径（原作 0.2D、0.499D） */
    private static final float BEAM_RADIUS = 0.2F;
    private static final float GLOW_RADIUS = 0.499F;

    private HoloWeaponsClient() {
    }

    @SubscribeEvent
    public static void holoWeaponsRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ECHoloWeapons.DIVIDER_PROJECTILE.get(), ThrownItemRenderer::new);
    }

    /** 预览光柱（Forge 总线） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
    public static final class HoloWeaponsPreview {

        private HoloWeaponsPreview() {
        }

        @SubscribeEvent
        public static void onHoloWeaponsRenderLevel(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            LocalPlayer player = minecraft.player;
            ClientLevel level = minecraft.level;
            if (player == null || level == null || player.isSpectator() || !holdingRemote(player)) {
                return;
            }
            float partialTick = event.getPartialTick();
            // 先看能不能锁定生物（同右键时的判断），锁定了就画在它脚下；没有再找方块
            LivingEntity lock = OrbitalRemoteItem.lockTargetFor(player);
            double cornerX;
            double bottom;
            double cornerZ;
            if (lock != null) {
                Vec3 feet = lock.getPosition(partialTick);
                cornerX = feet.x - 0.5D;
                bottom = feet.y;
                cornerZ = feet.z - 0.5D;
            } else {
                BlockHitResult hit = OrbitalRemoteItem.aimFor(player);
                if (hit == null) {
                    return;
                }
                BlockPos pos = hit.getBlockPos();
                cornerX = pos.getX();
                bottom = pos.getY() + 1;
                cornerZ = pos.getZ();
            }
            int height = (int) Math.floor(level.getMaxBuildHeight() - bottom);
            if (height <= 0) {
                return;
            }
            Vec3 camera = event.getCamera().getPosition();
            PoseStack poseStack = event.getPoseStack();
            poseStack.pushPose();
            // renderBeaconBeam 自己往 +0.5 挪（信标在方块角上），这里只挪到那一格的角上（锁定生物时是以它脚下为中心的一格）
            poseStack.translate(cornerX - camera.x, bottom - camera.y, cornerZ - camera.z);
            MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
            BeaconRenderer.renderBeaconBeam(poseStack, buffers, BeaconRenderer.BEAM_LOCATION, partialTick, 1F, level.getGameTime(),
                    0, height, MAGENTA, BEAM_RADIUS, GLOW_RADIUS);
            buffers.endBatch(RenderType.beaconBeam(BeaconRenderer.BEAM_LOCATION, false));
            buffers.endBatch(RenderType.beaconBeam(BeaconRenderer.BEAM_LOCATION, true));
            poseStack.popPose();
        }

        private static boolean holdingRemote(LocalPlayer player) {
            return player.getMainHandItem().getItem() instanceof OrbitalRemoteItem
                    || player.getOffhandItem().getItem() instanceof OrbitalRemoteItem;
        }
    }
}
