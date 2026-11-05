package com.flameforge.essentialcraft5.entity.mob.hoanna;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.phys.EntityHitResult;

/**
 * 13d-N3 两只怪的 Forge 总线事件（两端都注册）：墓穴守灵的「弹射物 30% 直接穿过」。
 * 排在 LOW、只改还是 DEFAULT 的结果（别的模组已经定了别的结果、或者取消了的不碰）。箭把 {@code SKIP_ENTITY} 当成「把它加进忽略名单、穿过去」；
 * 别的弹射物只是跳过这一刻，下一刻还贴着就再发一次事件——穿不穿过按 UUID 定死（{@link CatacombRevenantEntity#passesThrough}），所以下一刻照样穿过。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class HoannaNativesCEvents {

    private HoannaNativesCEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void hoannaNativesCProjectileImpact(ProjectileImpactEvent event) {
        if (event.isCanceled() || event.getImpactResult() != ProjectileImpactEvent.ImpactResult.DEFAULT
                || !(event.getRayTraceResult() instanceof EntityHitResult hit) || !(hit.getEntity() instanceof CatacombRevenantEntity revenant)) {
            return;
        }
        if (revenant.passesThrough(event.getProjectile())) {
            event.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
            revenant.showPhase(hit.getLocation());
        }
    }
}
