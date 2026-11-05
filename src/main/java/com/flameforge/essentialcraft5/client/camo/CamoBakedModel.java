package com.flameforge.essentialcraft5.client.camo;

import com.flameforge.essentialcraft5.block.camo.Camo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IQuadTransformer;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 伪装模型（第四批框架 F4-11 的客户端一半）：包在登记过的方块（{@link Camo#registerCamoBlock}）的每个状态的模型外面。
 * 模型数据里有 {@link Camo#CAMO_STATE} 就画被模仿方块的模型，没有就原样画本来的模型。
 * <ul>
 *   <li>{@link #getRenderTypes}：转发被模仿模型的渲染类型（玻璃半透明、树叶镂空……），区块重建按这个分层调 {@link #getQuads}；</li>
 *   <li>{@link #getQuads}：转发被模仿模型在这一层的面（它的模型数据传空：能当伪装的方块没有方块实体，本来就没有模型数据）；
 *       渲染类型为 null（破坏裂纹等）时给全部面；</li>
 *   <li>{@link #getParticleIcon(ModelData)}：破坏 / 奔跑粒子用被模仿方块的贴图；</li>
 *   <li>颜色（草、树叶）由 {@link CamoClient} 注册的 BlockColor 转发原版 {@code BlockColors}。</li>
 * </ul>
 * <b>花纹叠层</b>（第五批框架 F5-1，花纹拟态）：{@link #overlay} 不为空时，伪装状态下在 {@link #OVERLAY_LAYER}（{@code cutout_mipped}）层
 * 追加花纹模型的面，渲染类型取两者的并集。花纹模型是普通 JSON 模型、不认渲染类型参数（给什么层都返回全部面），所以按层过滤要在这里做：
 * 只在镂空层和 null 层给花纹的面；被模仿模型的面也只在它自己的渲染类型里给，免得实心方块在镂空层再画一遍。
 * 没伪装时不叠（方块本来的模型里已经带花纹）。{@code overlay} 为 null 时（拟态方块）行为和第四批完全一样。
 * 伪装不是整格（{@link Camo#overlayPushedOut}）时，花纹各面沿法线往外推 {@link Camo#OVERLAY_PUSH}，免得和伪装的侧面共面闪烁（审查 DECOR-2）。
 * <p>
 * 面剔除、环境光遮蔽仍按外层方块自己的状态算（原版 {@code ModelBlockRenderer} 用的是正在画的那个方块的状态），
 * 所以模块的方块要照原作决定是否不透光（拟态方块恒挡光 15）。被模仿的模型本身也是伪装模型时（黑名单漏了）按没伪装画，免得无限套娃。
 * 这个类在区块渲染线程上被调用：只读、不改任何共享状态。
 */
public class CamoBakedModel extends BakedModelWrapper<BakedModel> {

    /** 花纹叠在哪一层（原作花纹拟态的叠层就是镂空贴图） */
    public static final RenderType OVERLAY_LAYER = RenderType.cutoutMipped();

    private static final ChunkRenderTypeSet OVERLAY_LAYER_SET = ChunkRenderTypeSet.of(OVERLAY_LAYER);

    /** 伪装时叠在上面的花纹；没有是 null */
    @Nullable
    private final BakedModel overlay;

    public CamoBakedModel(BakedModel original) {
        this(original, null);
    }

    public CamoBakedModel(BakedModel original, @Nullable BakedModel overlay) {
        super(original);
        this.overlay = overlay;
    }

    /** 叠在伪装上的花纹模型（没有返回 null；给测试和调试看） */
    @Nullable
    public BakedModel overlay() {
        return overlay;
    }

    /** 模型数据里的伪装；没有、或者画不出来的返回 null */
    @Nullable
    private static BlockState camoOf(@Nullable ModelData data) {
        if (data == null) {
            return null;
        }
        BlockState camo = data.get(Camo.CAMO_STATE);
        if (camo == null || camo.isAir() || camo.getRenderShape() != RenderShape.MODEL) {
            return null;
        }
        return camo;
    }

    /** 被模仿方块的模型；它自己也是伪装模型时返回 null */
    @Nullable
    private static BakedModel modelOf(BlockState camo) {
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(camo);
        return model instanceof CamoBakedModel ? null : model;
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand,
                                             @NotNull ModelData extraData, @Nullable RenderType renderType) {
        BlockState camo = camoOf(extraData);
        BakedModel model = camo == null ? null : modelOf(camo);
        if (model == null) {
            return super.getQuads(state, side, rand, extraData, renderType);
        }
        if (overlay == null) {
            return model.getQuads(camo, side, rand, ModelData.EMPTY, renderType);
        }
        // 叠花纹：被模仿模型的面只在它自己的层里给（它可能不认 renderType 参数）。
        // 查它的渲染类型另用一个随机源，不动传进来的 rand（加权模型按 rand 选变体，getQuads 还要用同一个序列）
        List<BakedQuad> base = renderType == null
                || model.getRenderTypes(camo, RandomSource.create(camo.hashCode()), ModelData.EMPTY).contains(renderType)
                ? model.getQuads(camo, side, rand, ModelData.EMPTY, renderType)
                : List.of();
        if (renderType != null && renderType != OVERLAY_LAYER) {
            return base;
        }
        List<BakedQuad> pattern = overlay.getQuads(state, side, rand, ModelData.EMPTY, renderType);
        if (pattern.isEmpty()) {
            return base;
        }
        if (Camo.overlayPushedOut(camo)) {
            pattern = pushedOut(pattern);   // 伪装不是整格：共面但顶点不同会闪，往外推一点（Camo.OVERLAY_PUSH）
        }
        if (base.isEmpty()) {
            return pattern;
        }
        List<BakedQuad> quads = new ArrayList<>(base.size() + pattern.size());
        quads.addAll(base);
        quads.addAll(pattern);    // 后画：整格伪装时和被模仿方块的面顶点相同、共面，按深度测试 LEQUAL 画在上面（同原作 ModelFancyMimic）
        return quads;
    }

    /**
     * 每个面沿自己的朝向往外平移 {@link Camo#OVERLAY_PUSH}，面内不放大（理由见那里）。复制一份，不动烘焙好的原面。
     * 只在伪装不是整格时走到，每次重建区块现算（一格最多六个面），不缓存：花纹模型不一定和状态、随机数无关。
     */
    private static List<BakedQuad> pushedOut(List<BakedQuad> quads) {
        List<BakedQuad> out = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            Direction dir = quad.getDirection();
            float dx = dir.getStepX() * Camo.OVERLAY_PUSH;
            float dy = dir.getStepY() * Camo.OVERLAY_PUSH;
            float dz = dir.getStepZ() * Camo.OVERLAY_PUSH;
            int[] vertices = quad.getVertices().clone();
            for (int i = 0; i + IQuadTransformer.STRIDE <= vertices.length; i += IQuadTransformer.STRIDE) {
                int at = i + IQuadTransformer.POSITION;
                vertices[at] = Float.floatToRawIntBits(Float.intBitsToFloat(vertices[at]) + dx);
                vertices[at + 1] = Float.floatToRawIntBits(Float.intBitsToFloat(vertices[at + 1]) + dy);
                vertices[at + 2] = Float.floatToRawIntBits(Float.intBitsToFloat(vertices[at + 2]) + dz);
            }
            out.add(new BakedQuad(vertices, quad.getTintIndex(), dir, quad.getSprite(), quad.isShade(), quad.hasAmbientOcclusion()));
        }
        return out;
    }

    @Override
    public @NotNull ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand, @NotNull ModelData data) {
        BlockState camo = camoOf(data);
        BakedModel model = camo == null ? null : modelOf(camo);
        if (model == null) {
            return super.getRenderTypes(state, rand, data);
        }
        ChunkRenderTypeSet types = model.getRenderTypes(camo, rand, ModelData.EMPTY);
        return overlay == null ? types : ChunkRenderTypeSet.union(types, OVERLAY_LAYER_SET);
    }

    @Override
    public @NotNull TextureAtlasSprite getParticleIcon(@NotNull ModelData data) {
        BlockState camo = camoOf(data);
        BakedModel model = camo == null ? null : modelOf(camo);
        if (model == null) {
            return super.getParticleIcon(data);
        }
        return model.getParticleIcon(ModelData.EMPTY);
    }
}
