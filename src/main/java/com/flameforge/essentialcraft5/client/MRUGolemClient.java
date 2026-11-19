package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.model.mob.MRUGolemModel;
import com.flameforge.essentialcraft5.client.render.mob.MRUGolemRenderer;
import com.flameforge.essentialcraft5.client.vfx.VfxEffects;
import com.flameforge.essentialcraft5.client.vfx.resolve.ArcFx;
import com.flameforge.essentialcraft5.entity.boss.BossWarnings;
import com.flameforge.essentialcraft5.entity.mob.MRUGolemEntity;
import com.flameforge.essentialcraft5.registry.ECMRUGolem;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 第十三批 13c-M2「聚能魔像」的客户端：模型层、渲染器（模组总线，Forge 自动发现），以及魔像身上的两种电弧
 * （实体在 {@code handleEntityEvent} 里经 {@code DistExecutor} 调过来）。注册类是 {@code registry/ECMRUGolem}；刷怪蛋的颜色由 Forge 的
 * ForgeSpawnEggItem 自动注册。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MRUGolemClient {

    /** 魔像电弧的颜色（MRU 靛紫，和聚能冲击的预警圈同色） */
    private static final int ARC_COLOR = BossWarnings.MRU_BLUE;

    private MRUGolemClient() {
    }

    @SubscribeEvent
    public static void mruGolemLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(MRUGolemModel.LAYER, MRUGolemModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void mruGolemRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ECMRUGolem.MRU_GOLEM.get(), MRUGolemRenderer::new);
    }

    /** 守着的聚集体到魔像胸口的电弧（聚集体不在附近就不画）；idle = 待机时偶尔冒的那条 */
    public static void shieldArc(MRUGolemEntity golem, boolean idle) {
        Entity presence = golem.syncedPresence();
        if (presence == null) {
            return;
        }
        Vec3 chest = golem.position().add(0D, golem.getBbHeight() * 0.62D, 0D);
        // 等离子电弧（特效大改第 2 批，client/vfx/resolve/ArcFx）：抵掉一下是粗的、带分叉，待机偶尔冒的那条细一点
        if (golem.level() instanceof ClientLevel level) {
            VfxEffects.add(ArcFx.local(level, golem, presence.position(), chest, ARC_COLOR, idle ? 0.03F : 0.05F, idle ? 1 : 3));
        }
    }

    /** 电弧鞭：魔像左拳的大致位置到目标身体中间 */
    public static void whipArc(MRUGolemEntity golem) {
        Entity target = golem.syncedWhipTarget();
        if (target == null) {
            return;
        }
        float yaw = golem.yBodyRot * ((float) Math.PI / 180F);
        // 左拳在身体左侧约 0.6 格、胸口高度（模型的左边是 +X，转到世界坐标）
        Vec3 fist = golem.position().add(Math.cos(yaw) * 0.6D, golem.getBbHeight() * 0.55D, Math.sin(yaw) * 0.6D);
        if (golem.level() instanceof ClientLevel level) {
            VfxEffects.add(ArcFx.local(level, golem, fist, target.position().add(0D, target.getBbHeight() * 0.5D, 0D), ARC_COLOR, 0.065F, 4));
        }
    }
}
