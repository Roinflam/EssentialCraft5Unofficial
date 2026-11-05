package com.flameforge.essentialcraft5.client.camo;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.camo.Camo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 伪装的客户端接线（第四批框架 F4-11）：模块在公共包里 {@link Camo#registerCamoBlock} 登记过的方块，
 * <ul>
 *   <li>模型烘焙完（{@link ModelEvent.ModifyBakingResult}）把它们<b>所有状态</b>的模型包一层 {@link CamoBakedModel}（按注册名找
 *       {@code ModelResourceLocation}，不用自定义 JSON 加载器，方块本来的 blockstate / 模型 JSON 照常由 datagen 生成）；</li>
 *   <li>方块颜色（{@link RegisterColorHandlersEvent.Block}）注册一个转发器：伪装成草方块、树叶时按所在位置的群系上色，没伪装时不上色（-1）。</li>
 *   <li>花纹叠层（第五批框架 F5-1）：登记时带了花纹模型的，先在 {@link ModelEvent.RegisterAdditional} 里把花纹模型登记成额外模型
 *       （原版照常加载、烘焙，贴图进方块图集），包模型时用 {@code event.getModels().get(花纹模型 id)} 取烘焙好的花纹交给 {@link CamoBakedModel}。
 *       花纹模型文件缺失（烘焙成紫黑缺失模型）时记警告、按没有花纹包。</li>
 * </ul>
 * 两个事件都在模组构造之后才发（颜色在 {@code Minecraft} 构造器里、模型在资源加载里），模块在注册类 {@code init()} 里登记就赶得上。
 * 资源重载（F3+T）会重新烘焙，每次都重新包。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CamoClient {

    private CamoClient() {
    }

    @SubscribeEvent
    public static void registerOverlayModels(ModelEvent.RegisterAdditional event) {
        for (ResourceLocation model : Camo.overlayModels()) {
            event.register(model);
        }
    }

    @SubscribeEvent
    public static void wrapCamoModels(ModelEvent.ModifyBakingResult event) {
        Map<ResourceLocation, BakedModel> models = event.getModels();
        ModelBakery bakery = event.getModelBakery();
        UnbakedModel missing = bakery.getModel(ModelBakery.MISSING_MODEL_LOCATION);   // 模型文件缺失时 getModel 给的就是它（同一个对象）
        int wrapped = 0;
        for (Block block : resolve()) {
            BakedModel overlay = null;
            ResourceLocation overlayId = Camo.overlayOf(block);
            if (overlayId != null) {
                overlay = models.get(overlayId);
                if (overlay == null || bakery.getModel(overlayId) == missing) {
                    EssentialCraft5.LOGGER.warn("伪装模型：{} 的花纹模型 {} 没有加载到（文件缺失？），伪装时不叠花纹",
                            ForgeRegistries.BLOCKS.getKey(block), overlayId);
                    overlay = null;
                }
            }
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                ModelResourceLocation location = BlockModelShaper.stateToModelLocation(state);
                BakedModel original = models.get(location);
                if (original != null && !(original instanceof CamoBakedModel)) {
                    models.put(location, new CamoBakedModel(original, overlay));
                    wrapped++;
                }
            }
        }
        if (wrapped > 0) {
            EssentialCraft5.LOGGER.debug("伪装模型：包了 {} 个方块状态的模型", wrapped);
        }
    }

    @SubscribeEvent
    public static void registerCamoColors(RegisterColorHandlersEvent.Block event) {
        List<Block> blocks = resolve();
        if (!blocks.isEmpty()) {
            Set<Block> camoBlocks = Set.copyOf(blocks);
            event.register((state, level, pos, tintIndex) -> camoColor(camoBlocks, level, pos, tintIndex), blocks.toArray(new Block[0]));
        }
    }

    /** 转发被模仿方块的颜色；拿不到伪装、或被模仿的也是伪装方块时不上色 */
    private static int camoColor(Set<Block> camoBlocks, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos, int tintIndex) {
        if (level == null || pos == null) {
            return -1;
        }
        BlockState camo = Camo.camoAt(level, pos);
        if (camo == null || camoBlocks.contains(camo.getBlock())) {
            return -1;
        }
        return Minecraft.getInstance().getBlockColors().getColor(camo, level, pos, tintIndex);
    }

    /** 登记过的方块（还没注册上的跳过并记日志：通常是那个模块没接线） */
    private static List<Block> resolve() {
        List<Block> blocks = new ArrayList<>();
        for (Supplier<? extends Block> supplier : Camo.camoBlocks()) {
            try {
                Block block = supplier.get();
                if (block != null && !blocks.contains(block)) {
                    blocks.add(block);
                }
            } catch (RuntimeException e) {
                EssentialCraft5.LOGGER.warn("伪装模型：登记的方块取不到，跳过（{}）", e.toString());
            }
        }
        return blocks;
    }
}
