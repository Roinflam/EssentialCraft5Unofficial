package com.flameforge.essentialcraft5.item.armor;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import org.jetbrains.annotations.Nullable;

/**
 * 盔甲穿在身上的样子（第三批框架 G6）：两张贴图（护腿一张、其余一张，同原版 layer_2 / layer_1 和原作 getArmorTexture 的分法），
 * 加上可选的自定义模型 id。
 * <ul>
 *   <li>{@link #vanilla}：原版人形盔甲的形状（贴图 64×32，UV 照原版），例如风之护甲 {@code special/armor/wind_layer_1 / _2}。</li>
 *   <li>{@link #modelArmorEC}：原作 ModelArmorEC 的形状（贴图 128×64，多了肩甲、胸前交叉带、头盔顶板和面罩），
 *       二批魔力防护套装、原作 ItemArmorEC 系列（风法师套装 {@code special/armor/armorec3wind_0 / _1}）都是它。</li>
 *   <li>{@link #custom}：别的模型：客户端在 {@code client/armor/ECArmorModels#register} 按同一个 id 登记模型层。</li>
 * </ul>
 * 这是通用类（服务端也会加载），所以只存 id，不存客户端的 ModelLayerLocation；模型怎么画在 client/armor/ECArmorModels。
 */
public record ArmorLook(ResourceLocation outer, ResourceLocation inner, @Nullable ResourceLocation model) {

    /** 原作 ModelArmorEC 的模型 id（客户端用二批 MHSArmorModel 的三个模型层画） */
    public static final ResourceLocation MODEL_ARMOR_EC = EssentialCraft5.id("model_armor_ec");

    /** 原版形状。路径相对 {@code assets/essentialcraft5/textures/}，不带 .png */
    public static ArmorLook vanilla(String outerPath, String innerPath) {
        return new ArmorLook(texture(outerPath), texture(innerPath), null);
    }

    /** 原作 ModelArmorEC 形状（贴图 128×64）。路径同上 */
    public static ArmorLook modelArmorEC(String outerPath, String innerPath) {
        return new ArmorLook(texture(outerPath), texture(innerPath), MODEL_ARMOR_EC);
    }

    /** 自定义模型（客户端要用同一个 id 登记模型层）。路径同上 */
    public static ArmorLook custom(ResourceLocation model, String outerPath, String innerPath) {
        return new ArmorLook(texture(outerPath), texture(innerPath), model);
    }

    public static ResourceLocation texture(String path) {
        return EssentialCraft5.id("textures/" + path + ".png");
    }

    /** 这个部位用哪张贴图：护腿用 inner，其余用 outer */
    public ResourceLocation texture(EquipmentSlot slot) {
        return slot == EquipmentSlot.LEGS ? inner : outer;
    }
}
