package com.flameforge.essentialcraft5.client.render.mob;

import com.flameforge.essentialcraft5.entity.mob.ECMonster;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

/**
 * 新怪物、首领共用的渲染器基类（第十三批 13c-Fb，batch13.md §10「渲染」）。贴图按 {@link MobTextures} 的约定由 id 定，
 * 三种常用的叠层按需打开，连着写：
 * <pre>{@code
 * event.registerEntityRenderer(ECXxx.WISP.get(), ctx -> new ECMobRenderer<>(ctx, new WispModel(ctx.bakeLayer(WispModel.LAYER)), 0.4F, "wisp")
 *         .withGlow().withElite());
 * }</pre>
 * <ul>
 *   <li>{@link #withGlow()}：发光层（{@code <id>_glow.png}，全亮、不受光照，强度 {@link #glowAlpha}）；</li>
 *   <li>{@link #withElite()}：精英层（{@code <id>_elite.png}，{@link #isElite} 为真时叠在身上）；</li>
 *   <li>{@link #withTint()}：整只染色（{@link #tintColor}），身体改由染色层画，受伤变红、发光轮廓照常；</li>
 *   <li>{@link #withScale(float)}：模型整体放大缩小（影子跟着变）。</li>
 * </ul>
 * 体型另乘实体的 {@code getScale()}（精英变体放大 1.15 倍是 {@link ECMonster#getScale()} 给的，碰撞箱和画面一起变；1.20.1 原版渲染器不读它）。
 * 要按实体状态换贴图、换精英判断、换颜色的写子类覆盖对应的方法。只在客户端加载。
 */
public class ECMobRenderer<T extends Mob, M extends EntityModel<T>> extends MobRenderer<T, M> {

    private final String mobId;
    private final float baseShadow;
    private float baseScale = 1F;
    private boolean tinted;

    public ECMobRenderer(EntityRendererProvider.Context context, M model, float shadowRadius, String mobId) {
        super(context, model, shadowRadius);
        this.mobId = mobId;
        this.baseShadow = shadowRadius;
    }

    /** 贴图目录名（{@code textures/entity/<id>/}） */
    public String mobId() {
        return mobId;
    }

    // ------------------------------------------------------------------
    // 连着写的开关
    // ------------------------------------------------------------------

    /** 加发光层 */
    public ECMobRenderer<T, M> withGlow() {
        addLayer(new MobGlowLayer<>(this));
        return this;
    }

    /** 加精英层 */
    public ECMobRenderer<T, M> withElite() {
        addLayer(new MobEliteLayer<>(this));
        return this;
    }

    /** 整只染色：身体改由染色层画（染色层排在所有叠层前面，发光、精英叠在染过色的身体上） */
    public ECMobRenderer<T, M> withTint() {
        if (!tinted) {
            tinted = true;
            layers.add(0, new MobTintLayer<>(this));
        }
        return this;
    }

    /** 模型整体缩放（影子跟着变） */
    public ECMobRenderer<T, M> withScale(float scale) {
        this.baseScale = scale > 0F && Float.isFinite(scale) ? scale : 1F;
        return this;
    }

    // ------------------------------------------------------------------
    // 子类可以覆盖的
    // ------------------------------------------------------------------

    /** 身体贴图（默认主贴图；换色换皮的覆盖它，比如返回 {@link MobTextures#variant}） */
    protected ResourceLocation baseTexture(T entity) {
        return MobTextures.base(mobId);
    }

    /** 发光层贴图 */
    public ResourceLocation glowTexture(T entity) {
        return MobTextures.glow(mobId);
    }

    /** 精英层贴图 */
    public ResourceLocation eliteTexture(T entity) {
        return MobTextures.elite(mobId);
    }

    /** 画不画精英层：读 {@link ECMonster} 同步下来的精英标记（别的生物、首领一律不画） */
    public boolean isElite(T entity) {
        return ECMonster.isElite(entity);
    }

    /** 染色（RGB，默认白 = 不变色）；只在 {@link #withTint()} 后生效 */
    public int tintColor(T entity) {
        return 0xFFFFFF;
    }

    /** 发光层强度 0~1（默认 1；要呼吸、随血量变暗的覆盖它） */
    public float glowAlpha(T entity, float partialTick) {
        return 1F;
    }

    /** 发光层颜色（RGB，默认白 = 照贴图原色；灰度发光层要跟着染色的覆盖它），再乘 {@link #glowAlpha} */
    public int glowColor(T entity) {
        return 0xFFFFFF;
    }

    // ------------------------------------------------------------------
    // 原版接线
    // ------------------------------------------------------------------

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return baseTexture(entity);
    }

    /** 染色时身体由染色层画：看得见、不是半透明（隐身但旁观者能看见）的那一遍不画 */
    @Override
    protected @Nullable RenderType getRenderType(T entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        if (tinted && bodyVisible && !translucent) {
            return null;
        }
        return super.getRenderType(entity, bodyVisible, translucent, glowing);
    }

    @Override
    protected void scale(T entity, PoseStack pose, float partialTick) {
        float scale = baseScale * entity.getScale();
        pose.scale(scale, scale, scale);
    }

    @Override
    public void render(T entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        shadowRadius = baseShadow * baseScale * entity.getScale();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }
}
