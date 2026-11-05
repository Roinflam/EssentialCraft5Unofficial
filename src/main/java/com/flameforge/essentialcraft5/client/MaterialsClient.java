package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.DyeableBlock;
import com.flameforge.essentialcraft5.registry.ECBlocks;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 材料模块的客户端注册（模组总线）：可染色方块按颜色染、水之魔晶簇按生物群系水色染。
 * 渲染层写在模型里（强化玻璃是 cutout），不用在这里设。
 * <p>
 * 可染色方块不写死：遍历 {@link ECBlocks#entries()} 里所有 {@link DyeableBlock}（含子类），方块和物品颜色一起注册。
 * 以后新加的护板（魔力 / 淡青 / 米斯莱护板……）只要是 DyeableBlock、模型带 tintindex 0，就自动染色，不用来改这里。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MaterialsClient {

    /** 原版生物群系的默认水色（没有世界时用，比如物品栏里） */
    private static final int DEFAULT_WATER_COLOR = 0x3F76E4;

    private MaterialsClient() {
    }

    /** 所有登记过的可染色方块。颜色事件在注册表冻结之后才发，这时 RegistryObject 都能取 */
    private static List<DyeableBlock> dyeableBlocks() {
        return ECBlocks.entries().stream()
                .map(entry -> entry.block().get())
                .filter(DyeableBlock.class::isInstance)
                .map(DyeableBlock.class::cast)
                .toList();
    }

    @SubscribeEvent
    public static void blockColors(RegisterColorHandlersEvent.Block event) {
        List<DyeableBlock> dyeable = dyeableBlocks();
        if (!dyeable.isEmpty()) {
            event.register((state, level, pos, tintIndex) -> tintIndex == 0 ? DyeableBlock.tintColor(state) : -1,
                    dyeable.toArray(Block[]::new));
        }
        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex != 0) {
                return -1;
            }
            return level != null && pos != null ? BiomeColors.getAverageWaterColor(level, pos) : DEFAULT_WATER_COLOR;
        }, ECMaterials.WATER_DROPS_CLUSTER.get());
    }

    @SubscribeEvent
    public static void itemColors(RegisterColorHandlersEvent.Item event) {
        // 物品只有白色（原作挖下来总是 meta 0）；spec.item(null) 不注册物品的方块 asItem 是空气，跳过
        Item[] dyeableItems = dyeableBlocks().stream()
                .map(Block::asItem)
                .filter(item -> item != Items.AIR)
                .toArray(Item[]::new);
        if (dyeableItems.length > 0) {
            event.register((stack, tintIndex) -> tintIndex == 0 ? DyeColor.WHITE.getFireworkColor() : -1, dyeableItems);
        }
        event.register((stack, tintIndex) -> tintIndex == 0 ? DEFAULT_WATER_COLOR : -1,
                ECMaterials.WATER_DROPS_CLUSTER.get());
    }
}
