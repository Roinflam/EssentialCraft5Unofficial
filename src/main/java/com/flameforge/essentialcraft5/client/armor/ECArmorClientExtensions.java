package com.flameforge.essentialcraft5.client.armor;

import com.flameforge.essentialcraft5.item.armor.ArmorLook;
import com.flameforge.essentialcraft5.item.armor.DyeableECArmorItem;
import com.flameforge.essentialcraft5.item.armor.ECArmorItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;

/**
 * {@link ECArmorItem} 的客户端渲染属性（所有实例共用一个，渲染时按物品栈现查 {@link ArmorLook}）：
 * <ul>
 *   <li>穿在身上：{@link ArmorLook#model()} 不为空时换成 {@link ECArmorModels} 里登记的模型（Forge 负责把原模型的姿势和部件可见性抄过来），
 *       为空或还没烘焙就用原版模型。贴图由物品的 getArmorTexture 给。</li>
 *   <li>物品图标（只在物品模型是 builtin/entity 时才会用到，见 {@link ECArmorItem#icon3d}）：把对应部位画在物品空间正中，正面朝北，
 *       可染色的按颜色上色。写法同二批 MHSArmorRenderProperties。</li>
 * </ul>
 */
public final class ECArmorClientExtensions implements IClientItemExtensions {

    public static final ECArmorClientExtensions INSTANCE = new ECArmorClientExtensions();

    @Nullable
    private BlockEntityWithoutLevelRenderer iconRenderer;

    private ECArmorClientExtensions() {
    }

    @Override
    public HumanoidModel<?> getHumanoidArmorModel(LivingEntity livingEntity, ItemStack itemStack, EquipmentSlot equipmentSlot,
                                                  HumanoidModel<?> original) {
        if (itemStack.getItem() instanceof ECArmorItem armor) {
            ResourceLocation model = armor.look().model();
            if (model != null) {
                HumanoidModel<LivingEntity> replacement = ECArmorModels.worn(model, equipmentSlot);
                if (replacement != null) {
                    return replacement;
                }
            }
        }
        return original;
    }

    @Override
    public BlockEntityWithoutLevelRenderer getCustomRenderer() {
        if (iconRenderer == null) {
            iconRenderer = new IconRenderer(Minecraft.getInstance());
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

    private static final class IconRenderer extends BlockEntityWithoutLevelRenderer {

        IconRenderer(Minecraft minecraft) {
            super(minecraft.getBlockEntityRenderDispatcher(), minecraft.getEntityModels());
        }

        /** 原版的 BEWLR 在这里烘焙盾牌、三叉戟等模型，这里用不到；本类的模型由 ECArmorModels 在 AddLayers 里烘焙 */
        @Override
        public void onResourceManagerReload(ResourceManager resourceManager) {
        }

        @Override
        public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers,
                                 int light, int overlay) {
            if (!(stack.getItem() instanceof ECArmorItem armor) || armor.look().model() == null) {
                return;
            }
            ResourceLocation modelId = armor.look().model();
            EquipmentSlot slot = armor.getEquipmentSlot();
            HumanoidModel<LivingEntity> model = ECArmorModels.icon(modelId, slot);
            ECArmorModels.IconLayout layout = ECArmorModels.iconLayout(modelId, slot);
            if (model == null || layout == null) {
                return;
            }
            showSlot(model, slot);
            // EntityModel.young 默认是 true：没有实体给它赋值时按幼年画（头缩到 0.75 倍再往下挪、身体缩一半再挪 1.5 格），
            // 图标就成了缩在格子左下角的一小点。同 MHSArmorRenderProperties，图标实例每次画之前都摆回成年、站立
            model.young = false;
            model.crouching = false;
            model.riding = false;
            float red = 1F;
            float green = 1F;
            float blue = 1F;
            if (armor instanceof DyeableECArmorItem) {
                int color = DyeableECArmorItem.colorOf(stack);
                red = (color >> 16 & 255) / 255F;
                green = (color >> 8 & 255) / 255F;
                blue = (color & 255) / 255F;
            }
            pose.pushPose();
            pose.translate(0.5F, 0.5F, 0.5F);
            // 同 LivingEntityRenderer：模型空间 y 向下，翻成 y 向上；x 跟着翻，正面（模型 -z）保持朝北
            pose.scale(-layout.scale(), -layout.scale(), layout.scale());
            pose.translate(0F, -layout.centerY() / 16F, 0F);
            VertexConsumer consumer = ItemRenderer.getArmorFoilBuffer(buffers,
                    RenderType.armorCutoutNoCull(armor.look().texture(slot)), false, stack.hasFoil());
            model.renderToBuffer(pose, consumer, light, overlay, red, green, blue, 1F);
            pose.popPose();
        }
    }
}
