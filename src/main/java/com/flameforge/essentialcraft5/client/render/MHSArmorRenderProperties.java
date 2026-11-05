package com.flameforge.essentialcraft5.client.render;

import com.flameforge.essentialcraft5.item.MHSArmorItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * 魔力防护套装的客户端渲染属性（{@link MHSArmorItem#initializeClient} 交给 Forge）：
 * <ul>
 *   <li>穿在身上：{@link #getHumanoidArmorModel} 换成 {@link MHSArmorModel}（Forge 负责把原模型的姿势和部件可见性抄过来）。</li>
 *   <li>物品图标：原作靠 CodeChickenLib 的 ArmorRenderer 把 3D 盔甲画成图标，这里用 BEWLR（物品模型是 builtin/entity，
 *       显示变换照方块，见 ECGear 的物品规格）。图标用的是另一套模型实例，不会被穿戴渲染改过的姿势带歪。</li>
 * </ul>
 * 模型在 {@code EntityRenderersEvent.AddLayers}（每次重载资源）由 client/GearClient 调 {@link #bake} 烘焙。
 */
public final class MHSArmorRenderProperties implements IClientItemExtensions {

    public static final MHSArmorRenderProperties INSTANCE = new MHSArmorRenderProperties();

    /**
     * 图标的摆放：部位中心的模型 y 坐标（像素，向下为正）和缩放。
     * 头盔（含顶板、面罩）约 10 像素见方，胸甲连肩甲约 19 像素宽，护腿只画了腿的上半、靴子只画了下 4 像素。
     *
     * <p>{@code guiScale} 是物品栏 / 手册里（{@link ItemDisplayContext#GUI}）用的缩放：显示变换是方块那套（斜 45° 看、再缩到 0.625），
     * 按 {@code scale} 画的话胸甲、护腿、靴子在 16px 的格子里只有 8×10、8×9.5、8×7.5 像素，又是近黑的酒红，缩成一小团认不出来。
     * GUI 里放大到最长边约 13~14 像素（和原版盔甲图标差不多满格），头盔约 12×13。
     * 手持、掉在地上、物品展示框仍用 {@code scale}：那些场合按方块大小看正合适，放大了会从展示框里戳出去。</p>
     */
    private record IconLayout(float centerY, float scale, float guiScale) {
    }

    private static final Map<EquipmentSlot, IconLayout> ICON_LAYOUTS = new EnumMap<>(Map.of(
            EquipmentSlot.HEAD, new IconLayout(-4F, 1.25F, 1.35F),
            EquipmentSlot.CHEST, new IconLayout(6F, 0.8F, 1.1F),
            EquipmentSlot.LEGS, new IconLayout(16.5F, 1.1F, 1.6F),
            EquipmentSlot.FEET, new IconLayout(22F, 1.35F, 2.1F)));

    private static final Map<EquipmentSlot, HumanoidModel<LivingEntity>> WORN = new EnumMap<>(EquipmentSlot.class);
    private static final Map<EquipmentSlot, HumanoidModel<LivingEntity>> ICONS = new EnumMap<>(EquipmentSlot.class);

    @Nullable
    private BlockEntityWithoutLevelRenderer iconRenderer;

    private MHSArmorRenderProperties() {
    }

    /** 各部位用哪一层（膨胀量照原作，见 {@link MHSArmorModel}） */
    private static ModelLayerLocation layer(EquipmentSlot slot) {
        return switch (slot) {
            case LEGS -> MHSArmorModel.LEGS;
            case CHEST -> MHSArmorModel.CHEST;
            default -> MHSArmorModel.HEAD_FEET;
        };
    }

    public static void bake(EntityModelSet models) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            WORN.put(slot, new HumanoidModel<>(models.bakeLayer(layer(slot))));
            ICONS.put(slot, new HumanoidModel<>(models.bakeLayer(layer(slot))));
        }
    }

    @Override
    public HumanoidModel<?> getHumanoidArmorModel(LivingEntity livingEntity, ItemStack itemStack, EquipmentSlot equipmentSlot,
                                                  HumanoidModel<?> original) {
        HumanoidModel<LivingEntity> model = WORN.get(equipmentSlot);
        return model != null ? model : original;
    }

    @Override
    public BlockEntityWithoutLevelRenderer getCustomRenderer() {
        if (iconRenderer == null) {
            Minecraft minecraft = Minecraft.getInstance();
            iconRenderer = new IconRenderer(minecraft);
        }
        return iconRenderer;
    }

    /** 同原版 HumanoidArmorLayer.setPartVisibility */
    private static void showSlot(HumanoidModel<?> model, EquipmentSlot slot) {
        model.setAllVisible(false);
        switch (slot) {
            case HEAD -> {
                model.head.visible = true;
                model.hat.visible = true;
            }
            case CHEST -> {
                model.body.visible = true;
                model.rightArm.visible = true;
                model.leftArm.visible = true;
            }
            case LEGS -> {
                model.body.visible = true;
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
            }
            case FEET -> {
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
            }
            default -> {
            }
        }
    }

    /** 物品图标：把对应部位画在 1×1×1 的物品空间正中，正面朝北（和方块物品的正面同一侧） */
    private static final class IconRenderer extends BlockEntityWithoutLevelRenderer {

        IconRenderer(Minecraft minecraft) {
            super(minecraft.getBlockEntityRenderDispatcher(), minecraft.getEntityModels());
        }

        /** 原版的 BEWLR 在这里烘焙盾牌、三叉戟等模型，这里用不到；本类的模型由 {@link #bake} 负责 */
        @Override
        public void onResourceManagerReload(ResourceManager resourceManager) {
        }

        @Override
        public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers,
                                 int light, int overlay) {
            if (!(stack.getItem() instanceof ArmorItem armor)) {
                return;
            }
            EquipmentSlot slot = armor.getEquipmentSlot();
            HumanoidModel<LivingEntity> model = ICONS.get(slot);
            IconLayout layout = ICON_LAYOUTS.get(slot);
            if (model == null || layout == null) {
                return;
            }
            showSlot(model, slot);
            // EntityModel.young 默认是 true：没有实体给它赋值时按幼年画 —— 头缩到 0.75 倍再往下挪 1 格、身体缩一半再挪 1.5 格，
            // 图标就成了缩在格子左下角的一小块（物品栏、手册里都是）。穿戴模型由 Forge 从实体模型抄 young，不受影响
            model.young = false;
            model.crouching = false;
            model.riding = false;
            pose.pushPose();
            pose.translate(0.5F, 0.5F, 0.5F);
            // 同 LivingEntityRenderer：模型空间 y 向下，翻成 y 向上；x 跟着翻，正面（模型 -z）保持朝北
            float scale = context == ItemDisplayContext.GUI ? layout.guiScale() : layout.scale();
            pose.scale(-scale, -scale, scale);
            pose.translate(0F, -layout.centerY() / 16F, 0F);
            VertexConsumer consumer = ItemRenderer.getArmorFoilBuffer(buffers,
                    RenderType.armorCutoutNoCull(MHSArmorItem.texture(slot)), false, stack.hasFoil());
            model.renderToBuffer(pose, consumer, light, overlay, 1F, 1F, 1F, 1F);
            pose.popPose();
        }
    }
}
