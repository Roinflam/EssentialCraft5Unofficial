package com.flameforge.essentialcraft5.client.render.mob;

import com.flameforge.essentialcraft5.client.model.mob.TownSentryModel;
import com.flameforge.essentialcraft5.entity.boss.TownSentryEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;

/**
 * 废城守望者的渲染器：聚能魔像的模型放大 1.25 倍 + 发光层（胸口的核心、肩上炮塔座的警示灯）。发光层平时缓缓起伏，
 * 镇压蓄力时最亮（地上的红圈之外，身上也看得出它要砸了）。
 */
public class TownSentryRenderer extends ECMobRenderer<TownSentryEntity, TownSentryModel> {

    public static final float SCALE = 1.25F;

    public TownSentryRenderer(EntityRendererProvider.Context context) {
        super(context, new TownSentryModel(context.bakeLayer(TownSentryModel.LAYER)), 0.9F, "town_sentry");
        withScale(SCALE);
        withGlow();
    }

    @Override
    public float glowAlpha(TownSentryEntity entity, float partialTick) {
        if (entity.isCharging()) {
            return 1F;
        }
        return 0.55F + 0.15F * Mth.sin((entity.tickCount + partialTick) * 0.08F);
    }
}
