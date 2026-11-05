package com.flameforge.essentialcraft5.client.model;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.item.ember.ForgedSwordData;
import com.flameforge.essentialcraft5.item.ember.ForgedSwordData.Element;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.math.Transformation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.ForgeRenderTypes;
import net.minecraftforge.client.RenderTypeGroup;
import net.minecraftforge.client.model.CompositeModel;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.IGeometryLoader;
import net.minecraftforge.client.model.geometry.IUnbakedGeometry;
import net.minecraftforge.client.model.geometry.StandaloneGeometryBakingContext;
import net.minecraftforge.client.model.geometry.UnbakedGeometryHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 余烬元素剑的物品模型（第七批 M2，原作物品方块状态 {@code blockstates/item/elementalsword.json} + {@code MeshDefinitionElementalSword}）：
 * loader {@code essentialcraft5:forged_sword}，JSON 由 {@link ForgedSwordModelBuilder} 写。
 * <ul>
 *   <li>两层：下层（刀身）按主元素取 {@code item/modular/pa_<元素>}，上层（剑柄）按副元素取 {@code item/modular/sa_<元素>}；
 *       复合的那一层用整把剑 {@code item/tools/elementalsword}（原作两个 {@code normal} 都指它）。没锻造的是「复合 / 复合」，只画一层整把剑。</li>
 *   <li>每层都像原版生成的物品模型一样有厚度（{@code UnbakedGeometryHelper.createUnbakedItemElements}），第 i 层往外推 i × {@value #LAYER_OFFSET} 像素：
 *       复合刀身（整把剑）和剑柄有 37 个像素重叠，不推开的话两层侧边落在同一平面上，第一人称一条黑一条白地闪（同枪的 {@code GunModel.offsetLayer}）。</li>
 *   <li>25 种组合在烘焙时一次烘好（5 × 5，资源重载时随模型一起重烘），{@link SwordOverrides} 按 NBT 读主副元素选（{@link ForgedSwordData#modelKey}）。</li>
 * </ul>
 */
public final class ForgedSwordModel implements IUnbakedGeometry<ForgedSwordModel> {

    /** loader 注册名（{@code essentialcraft5:forged_sword}） */
    public static final String LOADER_NAME = "forged_sword";
    /** 整把剑的贴图（相对 textures/，复合那一层、没锻造的剑、粒子都用它） */
    public static final String WHOLE_SWORD = "item/tools/elementalsword";
    /** 第 i 层（i ≥ 1）往外推 i × 这么多像素（0.02 像素 = 1/800 格，看不出来；理由见 GunModel.offsetLayer） */
    private static final float LAYER_OFFSET = 0.02F;

    private ForgedSwordModel() {
    }

    /** 某一层的贴图：主元素 → 刀身 pa_*、副元素 → 剑柄 sa_*；复合 → 整把剑 */
    public static ResourceLocation layerTexture(Element element, boolean blade) {
        if (element == Element.COMBINED) {
            return EssentialCraft5.id(WHOLE_SWORD);
        }
        return EssentialCraft5.id("item/modular/" + (blade ? "pa_" : "sa_") + element.layer());
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter,
                           ModelState modelState, ItemOverrides overrides, ResourceLocation modelLocation) {
        Transformation rootTransform = context.getRootTransform();
        if (!rootTransform.isIdentity()) {
            modelState = UnbakedGeometryHelper.composeRootTransformIntoModelState(modelState, rootTransform);
        }
        // 物品模型：不按方块打光、GUI 里画成平面（同 Forge 的动态桶模型、枪的模型）
        IGeometryBakingContext itemContext = StandaloneGeometryBakingContext.builder(context).withGui3d(false).withUseBlockLight(false)
                .build(modelLocation);
        LayerBaker layerBaker = new LayerBaker(itemContext, context.getTransforms(), modelState, modelLocation);
        Map<String, BakedModel> variants = new HashMap<>();
        for (Element bottom : Element.values()) {
            for (Element top : Element.values()) {
                variants.put(ForgedSwordData.modelKey(bottom, top), layerBaker.bake(layers(bottom, top), spriteGetter, ItemOverrides.EMPTY));
            }
        }
        return layerBaker.bake(layers(Element.COMBINED, Element.COMBINED), spriteGetter, new SwordOverrides(overrides, variants));
    }

    /** 一种组合要叠的贴图：下层刀身、上层剑柄；两层都是整把剑时只画一层 */
    private static List<Material> layers(Element bottom, Element top) {
        List<Material> layers = new ArrayList<>(2);
        layers.add(new Material(InventoryMenu.BLOCK_ATLAS, layerTexture(bottom, true)));
        if (bottom != Element.COMBINED || top != Element.COMBINED) {
            layers.add(new Material(InventoryMenu.BLOCK_ATLAS, layerTexture(top, false)));
        }
        return layers;
    }

    /** 把第 layer 层的元素往外推：正反面加厚，侧边条沿自己的朝向往外（元素是 createUnbakedItemElements 刚建的，可以直接改） */
    static void offsetLayer(List<BlockElement> elements, int layer) {
        float d = layer * LAYER_OFFSET;
        for (BlockElement element : elements) {
            element.from.z -= d;
            element.to.z += d;
            for (Direction direction : element.faces.keySet()) {
                if (direction.getAxis() != Direction.Axis.Z) {
                    element.from.add(direction.getStepX() * d, direction.getStepY() * d, 0F);
                    element.to.add(direction.getStepX() * d, direction.getStepY() * d, 0F);
                }
            }
        }
    }

    /** 把若干层贴图烘成一个物品模型（每层带厚度，按顺序叠） */
    private record LayerBaker(IGeometryBakingContext context, ItemTransforms transforms, ModelState modelState, ResourceLocation modelLocation) {

        private static final RenderTypeGroup RENDER_TYPES = new RenderTypeGroup(RenderType.translucent(), ForgeRenderTypes.ITEM_UNSORTED_TRANSLUCENT.get());

        BakedModel bake(List<Material> layers, Function<Material, TextureAtlasSprite> spriteGetter, ItemOverrides overrides) {
            TextureAtlasSprite particle = spriteGetter.apply(new Material(InventoryMenu.BLOCK_ATLAS, EssentialCraft5.id(WHOLE_SWORD)));
            CompositeModel.Baked.Builder builder = CompositeModel.Baked.builder(context, particle, overrides, transforms);
            for (int i = 0; i < layers.size(); i++) {
                TextureAtlasSprite sprite = spriteGetter.apply(layers.get(i));
                List<BlockElement> elements = UnbakedGeometryHelper.createUnbakedItemElements(i, sprite.contents());
                if (i > 0) {
                    offsetLayer(elements, i);
                }
                List<BakedQuad> quads = UnbakedGeometryHelper.bakeElements(elements, material -> sprite, modelState, modelLocation);
                builder.addQuads(RENDER_TYPES, quads);
            }
            builder.setParticle(particle);
            return builder.build();
        }
    }

    /** 按 NBT 里的主副元素换模型（原作 MeshDefinitionElementalSword）：25 种在烘焙时已烘好，这里只查表 */
    private static final class SwordOverrides extends ItemOverrides {

        private final ItemOverrides nested;
        private final Map<String, BakedModel> variants;

        SwordOverrides(ItemOverrides nested, Map<String, BakedModel> variants) {
            this.nested = nested;
            this.variants = variants;
        }

        @Override
        public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
            BakedModel overridden = nested.resolve(model, stack, level, entity, seed);
            if (overridden != model) {
                return overridden;
            }
            BakedModel variant = variants.get(ForgedSwordData.modelKey(stack));
            return variant != null ? variant : model;
        }
    }

    /** JSON 读取器（ModelEvent.RegisterGeometryLoaders 里注册成 {@code essentialcraft5:forged_sword}，见 client/EmberForgeClient） */
    public static final class Loader implements IGeometryLoader<ForgedSwordModel> {

        public static final Loader INSTANCE = new Loader();

        private Loader() {
        }

        @Override
        public ForgedSwordModel read(JsonObject json, JsonDeserializationContext context) throws JsonParseException {
            return new ForgedSwordModel();
        }
    }
}
