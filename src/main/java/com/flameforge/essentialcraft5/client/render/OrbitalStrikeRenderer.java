package com.flameforge.essentialcraft5.client.render;

import com.flameforge.essentialcraft5.entity.OrbitalStrikeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * 轨道打击的光柱（原作 {@code RenderOrbitalStrike}）：原版信标光柱（{@link BeaconRenderer#renderBeaconBeam}），品红，
 * 从打击点一直画到建筑上限（原作写死到 y = 255），内径 / 外径随剩余时间收细——原作 {@code delay / 15}、{@code delay / 6}（delay 单位是秒），
 * 换成刻是剩余刻数 / 300、/ 120：遥控器的 3 秒开头是 0.2 / 0.5，落下那一刻收成一条线。
 * 视锥剔除按整根光柱算（{@link OrbitalStrikeEntity#getBoundingBoxForCulling()}）。
 */
public class OrbitalStrikeRenderer extends EntityRenderer<OrbitalStrikeEntity> {

    private static final float[] MAGENTA = {1F, 0F, 1F};
    /** 剩余刻数 → 内径（原作 delay 秒 / 15） */
    private static final float BEAM_PER_TICK = 1F / 300F;
    /** 剩余刻数 → 外径（原作 delay 秒 / 6） */
    private static final float GLOW_PER_TICK = 1F / 120F;
    /** 半径不能是 0：信标光柱的贴图 V 坐标按 0.5 / 半径 算 */
    private static final float MIN_RADIUS = 0.01F;

    public OrbitalStrikeRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0F;
    }

    @Override
    public void render(OrbitalStrikeEntity strike, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight) {
        Level level = strike.level();
        int height = level.getMaxBuildHeight() - Mth.floor(strike.getY());
        if (height > 0) {
            float remaining = strike.remainingTicks(partialTick);
            float beam = Math.max(MIN_RADIUS, remaining * BEAM_PER_TICK);
            float glow = Math.max(MIN_RADIUS, remaining * GLOW_PER_TICK);
            pose.pushPose();
            // renderBeaconBeam 自己会往 +0.5 挪（信标在方块角上），实体原点在打击点正中，先挪回去
            pose.translate(-0.5D, 0D, -0.5D);
            BeaconRenderer.renderBeaconBeam(pose, buffers, BeaconRenderer.BEAM_LOCATION, partialTick, 1F, level.getGameTime(),
                    0, height, MAGENTA, beam, glow);
            pose.popPose();
        }
        super.render(strike, yaw, partialTick, pose, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(OrbitalStrikeEntity strike) {
        return BeaconRenderer.BEAM_LOCATION;
    }
}
