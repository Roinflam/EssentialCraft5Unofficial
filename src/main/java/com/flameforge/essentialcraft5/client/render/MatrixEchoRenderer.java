package com.flameforge.essentialcraft5.client.render;

import com.flameforge.essentialcraft5.entity.MatrixEchoEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 矩阵回响的渲染（第十三批 13a，batch13.md §3.1.2）：复用玩家克隆体那一套（{@link PlayerCloneRenderer}），玩家模型 + 试炼者自己的皮肤，
 * 半透明（{@link HologramRenderer#ghost}，不写深度、贴图颜色叠上去），再按试炼者的矩阵属性染色：中性绿、混沌红、冰冻蓝、魔力紫、暗影灰紫。
 * 没有贴图；皮肤取不到（试炼者不在玩家列表里）用他 UUID 对应的默认皮肤，细手臂的皮肤换细手臂的模型。回响没有装备，不画盔甲层和手持层。
 */
public class MatrixEchoRenderer extends MobRenderer<MatrixEchoEntity, PlayerModel<MatrixEchoEntity>> {

    /** 比克隆体（{@value HologramRenderer#ALPHA}）实一点：要打的东西得看得清 */
    public static final float ALPHA = 0.45F;
    /** 各矩阵属性的颜色（0 中性、1 混沌、2 冰冻、3 魔力、4 暗影），RGB 0~1 */
    private static final float[][] TINTS = {
            {0.55F, 1.00F, 0.60F},
            {1.00F, 0.42F, 0.38F},
            {0.50F, 0.78F, 1.00F},
            {0.82F, 0.52F, 1.00F},
            {0.60F, 0.48F, 0.70F}
    };

    private final TintedPlayerModel wide;
    private final TintedPlayerModel slim;

    public MatrixEchoRenderer(EntityRendererProvider.Context context) {
        super(context, new TintedPlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.4F);
        this.wide = (TintedPlayerModel) model;
        this.slim = new TintedPlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    /** 这种矩阵属性染成什么颜色（越界的按中性） */
    public static float[] tint(int matrixType) {
        return TINTS[matrixType >= 0 && matrixType < TINTS.length ? matrixType : 0];
    }

    @Override
    public void render(MatrixEchoEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        TintedPlayerModel chosen = isSlim(entity) ? slim : wide;
        chosen.tint = tint(entity.matrixType());
        model = chosen;
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(MatrixEchoEntity entity) {
        UUID id = entity.trialist();
        PlayerInfo info = info(id);
        if (info != null) {
            return info.getSkinLocation();
        }
        return id != null ? DefaultPlayerSkin.getDefaultSkin(id) : DefaultPlayerSkin.getDefaultSkin();
    }

    @Override
    protected @Nullable RenderType getRenderType(MatrixEchoEntity entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        return bodyVisible || translucent ? HologramRenderer.ghost(getTextureLocation(entity)) : super.getRenderType(entity, bodyVisible, translucent, glowing);
    }

    private static boolean isSlim(MatrixEchoEntity entity) {
        UUID id = entity.trialist();
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

    /** 玩家模型，渲染时颜色乘矩阵属性的颜色、透明度乘 {@value #ALPHA} */
    static final class TintedPlayerModel extends PlayerModel<MatrixEchoEntity> {

        private float[] tint = TINTS[0];

        TintedPlayerModel(ModelPart root, boolean slim) {
            super(root, slim);
        }

        @Override
        public void renderToBuffer(PoseStack pose, VertexConsumer consumer, int light, int overlay, float red, float green, float blue,
                                   float alpha) {
            super.renderToBuffer(pose, consumer, light, overlay, red * tint[0], green * tint[1], blue * tint[2], alpha * ALPHA);
        }
    }
}
