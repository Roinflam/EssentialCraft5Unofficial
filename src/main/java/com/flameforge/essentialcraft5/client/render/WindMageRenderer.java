package com.flameforge.essentialcraft5.client.render;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.entity.WindMageEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 风法师的渲染（原作 RenderWindMage）：1.12 的 ModelBiped（64×32 贴图，左手左腿镜像右边），缩放 0.9375，影子 0.5，
 * 贴图按类型换：学徒绿袍 windmage_apprentice、法师蓝袍 windmage、大法师红袍 windmage_archmage；
 * 第十三批的风暴术士用 windmage_storm（从大法师那张改成暗蓝紫加电光），个子大一点（1.0）。
 * <p>
 * 1.20 的 {@link HumanoidModel#createMesh} 就是 1.12 ModelBiped 的 UV 排法（左臂左腿 mirror），贴图尺寸登记成 64×32 即可。
 * 原作工厂用的构造器没加盔甲层，这里也不加；{@link HumanoidMobRenderer} 自带的头颅 / 鞘翅 / 手持物品层只在 /summon 给了装备时才看得见。
 */
public class WindMageRenderer extends HumanoidMobRenderer<WindMageEntity, HumanoidModel<WindMageEntity>> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(EssentialCraft5.id("wind_mage"), "main");
    private static final List<ResourceLocation> TEXTURES = List.of(
            EssentialCraft5.id("textures/entity/windmage_apprentice.png"),
            EssentialCraft5.id("textures/entity/windmage.png"),
            EssentialCraft5.id("textures/entity/windmage_archmage.png"),
            EssentialCraft5.id("textures/entity/windmage_storm.png"));
    /** 原作 preRenderCallback */
    private static final float SCALE = 0.9375F;
    private static final float STORM_SCALE = 1.0F;

    public WindMageRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(LAYER)), 0.5F);
    }

    public static LayerDefinition createLayer() {
        return LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F), 64, 32);
    }

    @Override
    public ResourceLocation getTextureLocation(WindMageEntity entity) {
        return TEXTURES.get(entity.getMageType());
    }

    @Override
    protected void scale(WindMageEntity entity, PoseStack pose, float partialTick) {
        float scale = entity.isStormSorcerer() ? STORM_SCALE : SCALE;
        pose.scale(scale, scale, scale);
    }
}
