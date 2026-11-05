package com.flameforge.essentialcraft5.client.render;

import com.flameforge.essentialcraft5.entity.PlayerCloneEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 玩家克隆体的渲染（原作 {@code RenderPlayerClone}）：玩家模型 + 被克隆者的皮肤，和全息投影一样半透明（{@link HologramRenderer#ghost}），
 * 盔甲、手里的东西也按透明度 {@value HologramRenderer#ALPHA} 画（外观复制品，见实体类注释）。
 * <ul>
 *   <li>皮肤：按被克隆者的 UUID 从玩家列表取；取不到（下线了、离线服）用这个 UUID 对应的默认皮肤（原作直接用 getPlayerInfo 的返回值，玩家一下线就崩客户端）。
 *       细手臂的皮肤换细手臂的模型。</li>
 *   <li>盔甲层照原版 {@link HumanoidArmorLayer} 的画法（Forge 的贴图 / 模型钩子、皮革染色），只把渲染类型换成半透明的；纹饰和附魔光效不画。</li>
 *   <li>手里的东西：原版物品层，顶点透明度乘 {@value HologramRenderer#ALPHA}（半透明层上的物品跟着变淡，实心方块类的物品照原样）。</li>
 * </ul>
 */
public class PlayerCloneRenderer extends MobRenderer<PlayerCloneEntity, PlayerModel<PlayerCloneEntity>> {

    private final PlayerModel<PlayerCloneEntity> wide;
    private final PlayerModel<PlayerCloneEntity> slim;

    public PlayerCloneRenderer(EntityRendererProvider.Context context) {
        super(context, new GhostPlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.wide = model;
        this.slim = new GhostPlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        addLayer(new GhostArmorLayer(this, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), context));
        addLayer(new GhostItemLayer(this, context));
    }

    @Override
    public void render(PlayerCloneEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        model = isSlim(entity) ? slim : wide;
        model.rightArmPose = entity.getMainHandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        model.leftArmPose = entity.getOffhandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(PlayerCloneEntity entity) {
        UUID id = entity.clonedPlayer();
        PlayerInfo info = info(id);
        if (info != null) {
            return info.getSkinLocation();
        }
        return id != null ? DefaultPlayerSkin.getDefaultSkin(id) : DefaultPlayerSkin.getDefaultSkin();
    }

    @Override
    protected @Nullable RenderType getRenderType(PlayerCloneEntity entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        return bodyVisible || translucent ? HologramRenderer.ghost(getTextureLocation(entity)) : super.getRenderType(entity, bodyVisible, translucent, glowing);
    }

    private static boolean isSlim(PlayerCloneEntity entity) {
        UUID id = entity.clonedPlayer();
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
    static final class GhostPlayerModel extends PlayerModel<PlayerCloneEntity> {

        GhostPlayerModel(ModelPart root, boolean slim) {
            super(root, slim);
        }

        @Override
        public void renderToBuffer(PoseStack pose, VertexConsumer consumer, int light, int overlay, float red, float green, float blue,
                                   float alpha) {
            super.renderToBuffer(pose, consumer, light, overlay, red, green, blue, alpha * HologramRenderer.ALPHA);
        }
    }

    /** 半透明的盔甲层（画法照原版 HumanoidArmorLayer.renderArmorPiece） */
    static final class GhostArmorLayer extends HumanoidArmorLayer<PlayerCloneEntity, PlayerModel<PlayerCloneEntity>, HumanoidModel<PlayerCloneEntity>> {

        private static final EquipmentSlot[] ORDER = {EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.HEAD};
        private final HumanoidModel<PlayerCloneEntity> inner;
        private final HumanoidModel<PlayerCloneEntity> outer;

        GhostArmorLayer(RenderLayerParent<PlayerCloneEntity, PlayerModel<PlayerCloneEntity>> parent, HumanoidModel<PlayerCloneEntity> inner,
                        HumanoidModel<PlayerCloneEntity> outer, EntityRendererProvider.Context context) {
            super(parent, inner, outer, context.getModelManager());
            this.inner = inner;
            this.outer = outer;
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, PlayerCloneEntity entity, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            for (EquipmentSlot slot : ORDER) {
                ItemStack stack = entity.getItemBySlot(slot);
                if (!(stack.getItem() instanceof ArmorItem armor) || armor.getEquipmentSlot() != slot) {
                    continue;
                }
                HumanoidModel<PlayerCloneEntity> base = slot == EquipmentSlot.LEGS ? inner : outer;
                getParentModel().copyPropertiesTo(base);
                setPartVisibility(base, slot);
                Model model = getArmorModelHook(entity, stack, slot, base);
                if (armor instanceof DyeableLeatherItem dyeable) {
                    int color = dyeable.getColor(stack);
                    draw(pose, buffers, light, model, getArmorResource(entity, stack, slot, null),
                            (color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F);
                    draw(pose, buffers, light, model, getArmorResource(entity, stack, slot, "overlay"), 1F, 1F, 1F);
                } else {
                    draw(pose, buffers, light, model, getArmorResource(entity, stack, slot, null), 1F, 1F, 1F);
                }
            }
        }

        private static void draw(PoseStack pose, MultiBufferSource buffers, int light, Model model, ResourceLocation texture,
                                 float red, float green, float blue) {
            model.renderToBuffer(pose, buffers.getBuffer(HologramRenderer.ghost(texture)), light, OverlayTexture.NO_OVERLAY, red, green, blue,
                    HologramRenderer.ALPHA);
        }
    }

    /** 原版手持物品层，顶点透明度乘 {@value HologramRenderer#ALPHA} */
    static final class GhostItemLayer extends ItemInHandLayer<PlayerCloneEntity, PlayerModel<PlayerCloneEntity>> {

        GhostItemLayer(RenderLayerParent<PlayerCloneEntity, PlayerModel<PlayerCloneEntity>> parent, EntityRendererProvider.Context context) {
            super(parent, context.getItemInHandRenderer());
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, PlayerCloneEntity entity, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            MultiBufferSource faded = type -> new FadedConsumer(buffers.getBuffer(type));
            super.render(pose, faded, light, entity, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
        }
    }

    /** 把写进来的顶点透明度乘上 {@value HologramRenderer#ALPHA}，其余原样转交 */
    private record FadedConsumer(VertexConsumer delegate) implements VertexConsumer {

        private static int fade(int alpha) {
            return Math.round(alpha * HologramRenderer.ALPHA);
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            delegate.color(red, green, blue, fade(alpha));
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            delegate.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            delegate.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            delegate.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            delegate.endVertex();
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
            delegate.defaultColor(red, green, blue, fade(alpha));
        }

        @Override
        public void unsetDefaultColor() {
            delegate.unsetDefaultColor();
        }
    }
}
