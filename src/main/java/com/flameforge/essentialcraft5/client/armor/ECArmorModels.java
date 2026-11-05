package com.flameforge.essentialcraft5.client.armor;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.render.MHSArmorModel;
import com.flameforge.essentialcraft5.item.armor.ArmorLook;
import com.flameforge.essentialcraft5.item.armor.DyeableECArmorItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 盔甲自定义模型的客户端登记表（第三批框架 G6，配合 {@link ArmorLook#model()} 和 {@link ECArmorClientExtensions}）。
 * <p>
 * 内置 {@link ArmorLook#MODEL_ARMOR_EC}：原作 ModelArmorEC，直接复用二批 {@link MHSArmorModel} 的三个模型层
 * （膨胀量按部位 1.0 / 0.75 / 0.5，模型层定义由 client/GearClient 注册），图标摆放同 MHSArmorRenderProperties。
 * 风法师套装这类「原作 ItemArmorEC 系列」只要 {@code ArmorLook.modelArmorEC(贴图…)}，客户端什么都不用写。
 * <p>
 * 新的模型形状：模块在自己的客户端类里
 * <pre>
 * &#64;SubscribeEvent
 * public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
 *     event.registerLayerDefinition(FOO_LAYER, FooArmorModel::create);           // 同 MHSArmorModel.create 的写法
 *     ECArmorModels.register(FOO_MODEL_ID, slot -&gt; FOO_LAYER, ECArmorModels.defaultIcons());
 * }
 * </pre>
 * 模型在 {@code EntityRenderersEvent.AddLayers}（每次重载资源）统一烘焙；穿戴用一套实例、图标用另一套（图标不会被穿戴渲染改过的姿势带歪）。
 * 另外本类自动给所有 {@link DyeableECArmorItem} 注册物品颜色（第 0 层按染的颜色）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ECArmorModels {

    /** 物品图标的摆放：部位中心的模型 y 坐标（像素，向下为正）和缩放 */
    public record IconLayout(float centerY, float scale) {
    }

    private record Spec(Function<EquipmentSlot, ModelLayerLocation> layers, Map<EquipmentSlot, IconLayout> icons) {
    }

    private record Baked(Map<EquipmentSlot, HumanoidModel<LivingEntity>> worn, Map<EquipmentSlot, HumanoidModel<LivingEntity>> icons) {
    }

    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private static final Map<ResourceLocation, Spec> SPECS = new ConcurrentHashMap<>();
    /** 只在渲染线程读写（AddLayers 也在渲染线程发） */
    private static final Map<ResourceLocation, Baked> BAKED = new HashMap<>();

    static {
        register(ArmorLook.MODEL_ARMOR_EC, slot -> switch (slot) {
            case LEGS -> MHSArmorModel.LEGS;
            case CHEST -> MHSArmorModel.CHEST;
            default -> MHSArmorModel.HEAD_FEET;
        }, defaultIcons());
    }

    private ECArmorModels() {
    }

    /**
     * ModelArmorEC 形状的图标摆放（同 MHSArmorRenderProperties）：头盔（含顶板、面罩）约 10 像素见方，胸甲连肩甲约 19 像素宽，
     * 护腿只画了腿的上半、靴子只画了下 4 像素。原版形状的盔甲用 2D 图标，用不到这个。
     */
    public static Map<EquipmentSlot, IconLayout> defaultIcons() {
        Map<EquipmentSlot, IconLayout> icons = new EnumMap<>(EquipmentSlot.class);
        icons.put(EquipmentSlot.HEAD, new IconLayout(-4F, 1.25F));
        icons.put(EquipmentSlot.CHEST, new IconLayout(6F, 0.8F));
        icons.put(EquipmentSlot.LEGS, new IconLayout(16.5F, 1.1F));
        icons.put(EquipmentSlot.FEET, new IconLayout(22F, 1.35F));
        return icons;
    }

    /** 登记一种盔甲模型：各部位用哪个模型层（模型层定义要另外在 RegisterLayerDefinitions 里注册），以及图标怎么摆 */
    public static void register(ResourceLocation id, Function<EquipmentSlot, ModelLayerLocation> layers, Map<EquipmentSlot, IconLayout> icons) {
        SPECS.put(id, new Spec(layers, Map.copyOf(icons)));
    }

    /** 穿在身上用的模型；没登记 / 还没烘焙返回 null（调用方退回原版模型） */
    @Nullable
    public static HumanoidModel<LivingEntity> worn(ResourceLocation id, EquipmentSlot slot) {
        Baked baked = BAKED.get(id);
        return baked == null ? null : baked.worn().get(slot);
    }

    @Nullable
    static HumanoidModel<LivingEntity> icon(ResourceLocation id, EquipmentSlot slot) {
        Baked baked = BAKED.get(id);
        return baked == null ? null : baked.icons().get(slot);
    }

    @Nullable
    static IconLayout iconLayout(ResourceLocation id, EquipmentSlot slot) {
        Spec spec = SPECS.get(id);
        return spec == null ? null : spec.icons().get(slot);
    }

    /** 每次重载资源都会发（实体模型集重新读过之后），在这里重新烘焙所有登记的盔甲模型 */
    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        bake(event.getEntityModels());
    }

    private static void bake(EntityModelSet models) {
        BAKED.clear();
        SPECS.forEach((id, spec) -> {
            Map<EquipmentSlot, HumanoidModel<LivingEntity>> worn = new EnumMap<>(EquipmentSlot.class);
            Map<EquipmentSlot, HumanoidModel<LivingEntity>> icons = new EnumMap<>(EquipmentSlot.class);
            for (EquipmentSlot slot : ARMOR_SLOTS) {
                ModelLayerLocation layer = spec.layers().apply(slot);
                if (layer != null) {
                    worn.put(slot, new HumanoidModel<>(models.bakeLayer(layer)));
                    icons.put(slot, new HumanoidModel<>(models.bakeLayer(layer)));
                }
            }
            BAKED.put(id, new Baked(worn, icons));
        });
    }

    /** 可染色盔甲的物品颜色：第 0 层按染的颜色（没染是白色），其余层原色 */
    @SubscribeEvent
    public static void itemColors(RegisterColorHandlersEvent.Item event) {
        Item[] dyeable = ForgeRegistries.ITEMS.getValues().stream()
                .filter(item -> item instanceof DyeableECArmorItem)
                .toArray(Item[]::new);
        if (dyeable.length > 0) {
            event.register((stack, tintIndex) -> tintIndex == 0 ? DyeableECArmorItem.colorOf(stack) : -1, dyeable);
        }
    }
}
