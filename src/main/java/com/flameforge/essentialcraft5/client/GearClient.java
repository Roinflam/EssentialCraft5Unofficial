package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.render.MHSArmorModel;
import com.flameforge.essentialcraft5.client.render.MHSArmorRenderProperties;
import com.flameforge.essentialcraft5.item.MRUCUContainerItem;
import com.flameforge.essentialcraft5.item.MRUMoverItem;
import com.flameforge.essentialcraft5.item.MRUPresenceSpawnEggItem;
import com.flameforge.essentialcraft5.mru.MRUBalance;
import com.flameforge.essentialcraft5.registry.ECGear;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 装备模块的客户端注册（模组总线）：魔力防护套装的盔甲模型层、二阶移动魔杖的「使用中」贴图切换、聚集体刷怪蛋的两层颜色、
 * 聚集体收容瓶的「装着」模型切换和按平衡值染色。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GearClient {

    private GearClient() {
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(MHSArmorModel.LEGS, () -> MHSArmorModel.create(MHSArmorModel.LEGS_INFLATION));
        event.registerLayerDefinition(MHSArmorModel.CHEST, () -> MHSArmorModel.create(MHSArmorModel.CHEST_INFLATION));
        event.registerLayerDefinition(MHSArmorModel.HEAD_FEET, () -> MHSArmorModel.create(MHSArmorModel.HEAD_FEET_INFLATION));
    }

    /** 每次重载资源都会发（实体模型集重新读过之后），在这里重新烘焙盔甲模型 */
    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        MHSArmorRenderProperties.bake(event.getEntityModels());
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        // 同一阶魔杖（MRUCUClient）：直接看持有者是不是正在使用这根魔杖，不写 NBT
        event.enqueueWork(() -> {
            ItemProperties.register(ECGear.MRU_MOVER_T2.get(), MRUMoverItem.ACTIVE_PREDICATE,
                    (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1F : 0F);
            ItemProperties.register(ECGear.MRUCU_CONTAINER.get(), MRUCUContainerItem.FILLED_PREDICATE,
                    (stack, level, entity, seed) -> MRUCUContainerItem.isFilled(stack) ? 1F : 0F);
        });
    }

    @SubscribeEvent
    public static void itemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> switch (tintIndex) {
            case 0 -> MRUPresenceSpawnEggItem.BACKGROUND_COLOR;
            case 1 -> MRUPresenceSpawnEggItem.HIGHLIGHT_COLOR;
            default -> -1;
        }, ECGear.MRU_PRESENCE_SPAWN_EGG.get());
        // 收容瓶装着时的第二层：按瓶里聚集体的平衡值染色，同世界里聚集体光线的颜色
        event.register((stack, tintIndex) -> {
            MRUCUContainerItem.Contents contents = tintIndex == 1 ? MRUCUContainerItem.contents(stack) : null;
            if (contents == null) {
                return -1;
            }
            float[] rgb = MRUBalance.rgb(contents.balance());
            return 0xFF000000 | (int) (rgb[0] * 255F) << 16 | (int) (rgb[1] * 255F) << 8 | (int) (rgb[2] * 255F);
        }, ECGear.MRUCU_CONTAINER.get());
    }
}
