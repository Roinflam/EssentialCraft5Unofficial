package com.flameforge.essentialcraft5.client.model;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.gun.GunMaterials;
import com.flameforge.essentialcraft5.gun.GunParts;
import com.flameforge.essentialcraft5.gun.GunType;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.math.Transformation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * 枪的物品模型（ECGuns，原作 {@code ModelGunHandler} + {@code ModelGun}）：loader {@code essentialcraft5:gun}，JSON 里 {@code gun_type} 选枪种，
 * {@code layer0} 是底图 {@code item/guns/<枪>/<枪>_generic}（datagen 由 {@link GunModelBuilder} 写）。
 * <ul>
 *   <li>没组装的枪只画底图（原作每秒随机换一套部件）。</li>
 *   <li>组装好的枪：底图上依次叠枪托、握把、机栓、瞄具、透镜五层（原作顺序），贴图 {@code item/guns/<枪>/<base|handle|device|scope|lense>/<材料 id>}；
 *       每层都像原版生成的物品模型一样有厚度（{@code UnbakedGeometryHelper.createUnbakedItemElements}），变换取父模型 {@code forge:item/default-tool}（和 item/handheld 一样）。</li>
 *   <li>覆盖表 {@link GunOverrides} 按部件组合<b>懒烘焙</b>、LRU 缓存 {@value #CACHE_SIZE} 份（原作每帧每把枪新建一个模型对象）；
 *       资源重载时整个模型重烘、缓存随之作废。材料表里没有的 id（数据被改过）那一层不画。</li>
 * </ul>
 * 狙击开镜时不再换空模型（原作第三人称也看不见枪）：只在第一人称取消主手的渲染（{@code client/GunsClientEvents}）。
 */
public final class GunModel implements IUnbakedGeometry<GunModel> {

    /** loader 注册名（{@code essentialcraft5:gun}） */
    public static final String LOADER_NAME = "gun";
    /** JSON 里选枪种的键 */
    public static final String TYPE_KEY = "gun_type";
    /** 每种枪缓存多少套部件组合 */
    public static final int CACHE_SIZE = 256;

    private final GunType type;

    private GunModel(GunType type) {
        this.type = type;
    }

    /** 底图（相对 textures/，不带扩展名） */
    public static String genericTexture(GunType type) {
        return "item/guns/" + type.id() + "/" + type.id() + "_generic";
    }

    /** 部件贴图：part 是 base / handle / device / scope / lense */
    public static ResourceLocation partTexture(GunType type, String part, String id) {
        return EssentialCraft5.id("item/guns/" + type.id() + "/" + part + "/" + id);
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter,
                           ModelState modelState, ItemOverrides overrides, ResourceLocation modelLocation) {
        Material generic = context.getMaterial("layer0");
        Transformation rootTransform = context.getRootTransform();
        if (!rootTransform.isIdentity()) {
            modelState = UnbakedGeometryHelper.composeRootTransformIntoModelState(modelState, rootTransform);
        }
        // 物品模型：不按方块打光、GUI 里画成平面（同 Forge 的动态桶模型）
        IGeometryBakingContext itemContext = StandaloneGeometryBakingContext.builder(context).withGui3d(false).withUseBlockLight(false)
                .build(modelLocation);
        LayerBaker layerBaker = new LayerBaker(itemContext, context.getTransforms(), modelState, modelLocation);
        GunOverrides handler = new GunOverrides(type, overrides, layerBaker, generic);
        return layerBaker.bake(List.of(generic), spriteGetter, handler);
    }

    /**
     * 第 i 层（i ≥ 1）往外推 i × 这么多像素：正反两面各往外、每条侧边沿自己的朝向往外。
     * 不推的话，几层的侧边落在同一个平面上、顶点却不一样（各层的轮廓不同），深度插值有误差，第一人称拿在手上时侧边一条黑一条白地闪（z-fighting，
     * 集成时展示厅 04_scene_gun_hand 看到的）。正面整张的四边形各层顶点相同，本来不打架，推开以后靠深度分先后，结果和原来的「后画的盖住先画的」一样。
     * 0.02 像素 = 1/800 格，五层合计 0.1 像素，看不出来；20 格外仍大于深度缓冲的精度。
     */
    private static final float LAYER_OFFSET = 0.02F;

    /** 把第 layer 层的元素往外推（见 {@link #LAYER_OFFSET}）；元素是 createUnbakedItemElements 刚建的，可以直接改 */
    static void offsetLayer(List<BlockElement> elements, int layer) {
        float d = layer * LAYER_OFFSET;
        for (BlockElement element : elements) {
            // 正反面（NORTH / SOUTH）和侧边条都占 z 7.5~8.5：一起加厚，侧边和正面之间不留缝
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
            TextureAtlasSprite particle = spriteGetter.apply(layers.get(0));
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

    /** 一套部件组合（只含材料表里认得的 id；加特林没有瞄具） */
    private record Key(String base, String handle, String device, @Nullable String scope, @Nullable String lens) {

        static Key of(GunType type, GunParts parts) {
            String scope = parts.scope() != null && GunMaterials.scope(type, parts.scope()).isPresent() ? parts.scope() : null;
            String lens = parts.lens() != null && GunMaterials.lens(parts.lens()).isPresent() ? parts.lens() : null;
            return new Key(known(parts.base()), known(parts.handle()), known(parts.device()), scope, lens);
        }

        @Nullable
        private static String known(String body) {
            return GunMaterials.body(body).isPresent() ? body : null;
        }

        /** 底图之后的各层（原作顺序：枪托、握把、机栓、瞄具、透镜） */
        List<Material> layers(GunType type, Material generic) {
            List<Material> layers = new ArrayList<>(6);
            layers.add(generic);
            add(layers, type, "base", base);
            add(layers, type, "handle", handle);
            add(layers, type, "device", device);
            add(layers, type, "scope", scope);
            add(layers, type, "lense", lens);
            return layers;
        }

        private static void add(List<Material> layers, GunType type, String part, @Nullable String id) {
            if (id != null) {
                layers.add(new Material(InventoryMenu.BLOCK_ATLAS, partTexture(type, part, id)));
            }
        }
    }

    /**
     * 按 NBT 里的部件换模型（原作 ModelGunOverrideHandler）。第一次见到的组合当场烘焙（精灵从已拼好的图集里取），
     * LRU 缓存 {@value #CACHE_SIZE} 份；只在渲染线程调用，另加了锁以防别的模组在别的线程画物品。
     */
    private static final class GunOverrides extends ItemOverrides {

        private final GunType type;
        private final ItemOverrides nested;
        private final LayerBaker baker;
        private final Material generic;
        private final Map<Key, BakedModel> cache = new LinkedHashMap<>(32, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Key, BakedModel> eldest) {
                return size() > CACHE_SIZE;
            }
        };

        GunOverrides(GunType type, ItemOverrides nested, LayerBaker baker, Material generic) {
            this.type = type;
            this.nested = nested;
            this.baker = baker;
            this.generic = generic;
        }

        @Override
        public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
            BakedModel overridden = nested.resolve(model, stack, level, entity, seed);
            if (overridden != model) {
                return overridden;
            }
            Optional<GunParts> parts = GunParts.read(stack);
            if (parts.isEmpty()) {
                return model;
            }
            Key key = Key.of(type, parts.get());
            synchronized (cache) {
                BakedModel baked = cache.get(key);
                if (baked == null) {
                    baked = baker.bake(key.layers(type, generic), Material::sprite, ItemOverrides.EMPTY);
                    cache.put(key, baked);
                }
                return baked;
            }
        }
    }

    /** JSON 读取器（ModelEvent.RegisterGeometryLoaders 里注册成 {@code essentialcraft5:gun}） */
    public static final class Loader implements IGeometryLoader<GunModel> {

        public static final Loader INSTANCE = new Loader();

        private Loader() {
        }

        @Override
        public GunModel read(JsonObject json, JsonDeserializationContext context) throws JsonParseException {
            String id = GsonHelper.getAsString(json, TYPE_KEY);
            GunType type = GunType.byId(id);
            if (type == null) {
                throw new JsonParseException("Unknown " + TYPE_KEY + " '" + id + "' in essentialcraft5:gun model");
            }
            return new GunModel(type);
        }
    }
}
