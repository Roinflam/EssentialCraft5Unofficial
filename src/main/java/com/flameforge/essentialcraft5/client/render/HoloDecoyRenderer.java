package com.flameforge.essentialcraft5.client.render;

import com.flameforge.essentialcraft5.entity.HoloDecoyEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 全息诱饵的渲染：玩家模型 + 主人的皮肤，和全息投影、玩家克隆体一样半透明（{@link HologramRenderer#ghost}，透明度 {@value HologramRenderer#ALPHA}）。
 * 皮肤按主人的 UUID 从玩家列表取，取不到（主人刚下线、离线服）用这个 UUID 对应的默认皮肤；细手臂的皮肤换细手臂的模型。
 * 诱饵身上没有装备（服务端装备格一直是空的），不画盔甲层和手持物品层；不显示名字。
 */
public class HoloDecoyRenderer extends LivingEntityRenderer<HoloDecoyEntity, PlayerModel<HoloDecoyEntity>> {

    private final PlayerModel<HoloDecoyEntity> wide;
    private final PlayerModel<HoloDecoyEntity> slim;

    public HoloDecoyRenderer(EntityRendererProvider.Context context) {
        super(context, new GhostModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.wide = model;
        this.slim = new GhostModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @Override
    public void render(HoloDecoyEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        model = isSlim(entity) ? slim : wide;
        model.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(HoloDecoyEntity entity) {
        UUID id = entity.ownerId();
        PlayerInfo info = info(id);
        if (info != null) {
            return info.getSkinLocation();
        }
        return id != null ? DefaultPlayerSkin.getDefaultSkin(id) : DefaultPlayerSkin.getDefaultSkin();
    }

    @Override
    protected @Nullable RenderType getRenderType(HoloDecoyEntity entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        return bodyVisible || translucent ? HologramRenderer.ghost(getTextureLocation(entity)) : super.getRenderType(entity, bodyVisible, translucent, glowing);
    }

    /** 不画名字（LivingEntityRenderer 对不是 Mob 的活体默认 64 格内一直显示名字） */
    @Override
    protected boolean shouldShowName(HoloDecoyEntity entity) {
        return false;
    }

    private static boolean isSlim(HoloDecoyEntity entity) {
        UUID id = entity.ownerId();
        PlayerInfo info = info(id);
        if (info != null) {
            return "slim".equals(info.getModelName());
        }
        return id != null && "slim".equals(DefaultPlayerSkin.getSkinModelName(id));
    }

    @Nullable
    private static PlayerInfo info(@Nullable UUID id) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return id == null || connection == null ? null : connection.getPlayerInfo(id);
    }

    /** 玩家模型，渲染时透明度乘 {@value HologramRenderer#ALPHA} */
    static final class GhostModel extends PlayerModel<HoloDecoyEntity> {

        GhostModel(ModelPart root, boolean slim) {
            super(root, slim);
        }

        @Override
        public void renderToBuffer(PoseStack pose, VertexConsumer consumer, int light, int overlay, float red, float green, float blue,
                                   float alpha) {
            super.renderToBuffer(pose, consumer, light, overlay, red, green, blue, alpha * HologramRenderer.ALPHA);
        }
    }
}
