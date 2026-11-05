package com.flameforge.essentialcraft5.client.hoanna;

import com.flameforge.essentialcraft5.hoanna.HoannaVisuals;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * 海纳尔的维度效果 {@code essentialcraft5:hoanna}（第六批 6a M3，batch6a.md §3.3 末段；由 {@link HoannaClient} 在
 * {@code RegisterDimensionSpecialEffectsEvent} 里注册，维度类型 JSON 的 {@code effects} 指向它）。
 * <ul>
 *   <li>云高 NaN（D5 A：不画云，原版和常见的渲染优化模组都直接跳过；不覆写 {@code renderClouds}，不自己画云）；有地面、天空类型 NORMAL、不强制亮光照图、没有恒定环境光。</li>
 *   <li>{@link #renderSky}：交给 {@link HoannaSkyRenderer}，返回 true（原版天空不画；开光影时整片天在光影的 CUSTOM_SKY 阶段）。</li>
 *   <li>{@link #getBrightnessDependentFogColor}：c × (b·0.88 + 0.12, b·0.88 + 0.12, b·0.84 + 0.16)（{@link HoannaVisuals#fogBrightnessFactor}），夜里的雾比主世界略亮、略偏蓝紫。</li>
 *   <li>{@link #getSunriseColor}：日出扇换成灰紫 → 玫瑰 → 暖沙、不透明度 × 0.55（{@link HoannaVisuals#sunrise}；原版雾在日出方向的混色也读它）。</li>
 *   <li>{@link #adjustLightmapColors}：黑暗事件（D6 A）按 {@link HoannaVisuals#lightmapFactor} 压暗光照图，在夜视和伽马之前，火把、夜视药水、亮度滑块照常有效。</li>
 *   <li>不覆写 {@code renderSnowAndRain} / {@code tickRain}：海纳尔的昼夜、天气跟主世界（照原作）。</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public final class HoannaEffects extends DimensionSpecialEffects {

    private final float[] sunrise = new float[4];
    /** 这一次光照图更新里的黑暗淡入值（pixelX = pixelY = 0 那一格算一次，其余 255 格复用） */
    private float lightmapDarkness;

    public HoannaEffects() {
        super(HoannaVisuals.CLOUD_HEIGHT, true, SkyType.NORMAL, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        return color.multiply(HoannaVisuals.fogBrightnessFactor(0, brightness), HoannaVisuals.fogBrightnessFactor(1, brightness),
                HoannaVisuals.fogBrightnessFactor(2, brightness));
    }

    @Override
    public boolean isFoggyAt(int x, int z) {
        return false;
    }

    @Nullable
    @Override
    public float[] getSunriseColor(float timeOfDay, float partialTick) {
        return HoannaVisuals.sunrise(timeOfDay, sunrise);
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, Camera camera, Matrix4f projectionMatrix,
                             boolean isFoggy, Runnable setupFog) {
        HoannaSkyRenderer.render(this, level, partialTick, poseStack, camera, projectionMatrix, isFoggy, setupFog);
        return true;
    }

    @Override
    public void adjustLightmapColors(ClientLevel level, float partialTicks, float skyDarken, float blockLightRedFlicker, float skyLight,
                                     int pixelX, int pixelY, Vector3f colors) {
        if (pixelX == 0 && pixelY == 0) {
            // LightTexture 的循环是 sky（外层）× block（内层），(0, 0) 是每次更新的第一格
            lightmapDarkness = HoannaClientEvents.fade(HoannaEvent.DARKNESS, partialTicks);
        }
        float darkness = lightmapDarkness;
        if (darkness > 0F) {
            colors.mul(HoannaVisuals.lightmapFactor(darkness, pixelX));
        }
    }
}
