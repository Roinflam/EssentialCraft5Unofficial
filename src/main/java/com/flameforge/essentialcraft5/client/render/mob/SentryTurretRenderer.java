package com.flameforge.essentialcraft5.client.render.mob;

import com.flameforge.essentialcraft5.client.model.mob.SentryTurretModel;
import com.flameforge.essentialcraft5.entity.boss.SentryTurretEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** 哨戒炮台的渲染器：和废城守望者共用贴图目录 {@code town_sentry}（主贴图 + 发光层），放大 1.3 倍 */
public class SentryTurretRenderer extends ECMobRenderer<SentryTurretEntity, SentryTurretModel> {

    public static final float SCALE = 1.3F;

    public SentryTurretRenderer(EntityRendererProvider.Context context) {
        super(context, new SentryTurretModel(context.bakeLayer(SentryTurretModel.LAYER)), 0.4F, "town_sentry");
        withScale(SCALE);
        withGlow();
    }
}
