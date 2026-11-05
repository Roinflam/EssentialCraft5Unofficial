package com.flameforge.essentialcraft5.client.render.mob;

import com.flameforge.essentialcraft5.client.model.mob.MRUGolemModel;
import com.flameforge.essentialcraft5.entity.mob.MRUGolemEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;

/**
 * 聚能魔像的渲染器：主贴图 + 发光层（核心、晶柱、石缝里的光）。发光层的亮度表示护盾：聚集体在供能时亮、慢慢呼吸，
 * 蓄力聚能冲击时最亮，护盾够不着（聚集体挪远、抽干）时暗下去——打的人一眼能看出护盾还在不在。
 */
public class MRUGolemRenderer extends ECMobRenderer<MRUGolemEntity, MRUGolemModel> {

    public MRUGolemRenderer(EntityRendererProvider.Context context) {
        super(context, new MRUGolemModel(context.bakeLayer(MRUGolemModel.LAYER)), 0.9F, "mru_golem");
        withGlow();
    }

    @Override
    public float glowAlpha(MRUGolemEntity entity, float partialTick) {
        if (entity.isCharging()) {
            return 1F;
        }
        if (entity.isShielded()) {
            return 0.8F + 0.2F * Mth.sin((entity.tickCount + partialTick) * 0.12F);
        }
        return 0.35F;
    }
}
