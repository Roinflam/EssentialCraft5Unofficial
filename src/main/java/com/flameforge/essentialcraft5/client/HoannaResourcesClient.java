package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.ParticleCooldown;
import com.flameforge.essentialcraft5.client.hoanna.HoannaClientEvents;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * 第十三批 13d-H1a「特产方块」的客户端部分（模组总线，Forge 自动发现）。方块的渲染层写在模型里（簇、芽 {@code cutout}，榍石矿 {@code cutout_mipped}），
 * 这里只放三个方块 {@code animateTick} 调的粒子（只在客户端调，服务端不会加载这个类）。注册类是 {@code registry/ECHoannaResources}。
 * <p>
 * 粒子照 policy §1.2「客户端渲染」：每次抽中至多 {@value #MAX_PER_CALL} 颗，离相机 {@value #MAX_DISTANCE} 格外不冒，
 * 同一格 {@value ParticleCooldown#COOLDOWN_TICKS} 刻内只冒一次（{@link ParticleCooldown}，三种方块各一张表）。
 * 客户端每刻在玩家周围抽 667 × 2 个位置调 animateTick，越远越稀；加上同格冷却，一处喷口每秒至多 2 颗。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HoannaResourcesClient {

    /** 每次 animateTick 至多冒几颗 */
    public static final int MAX_PER_CALL = 2;
    /** 离相机多远就不冒（格） */
    public static final double MAX_DISTANCE = 24D;

    /** 喷口的淡紫尘 */
    private static final DustParticleOptions VENT_DUST = new DustParticleOptions(new Vector3f(0.72F, 0.52F, 0.95F), 0.7F);
    /** 榍石的小闪光（暖白） */
    private static final DustParticleOptions TITANITE_GLINT = new DustParticleOptions(new Vector3f(1F, 0.93F, 0.66F), 0.45F);
    /** 影晶的暗紫尘 */
    private static final DustParticleOptions UMBRAL_DUST = new DustParticleOptions(new Vector3f(0.36F, 0.16F, 0.52F), 0.9F);

    private static final ParticleCooldown VENT_COOLDOWN = new ParticleCooldown();
    private static final ParticleCooldown TITANITE_COOLDOWN = new ParticleCooldown();
    private static final ParticleCooldown UMBRAL_COOLDOWN = new ParticleCooldown();

    private HoannaResourcesClient() {
    }

    @SubscribeEvent
    public static void hoannaResourcesLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
    }

    @SubscribeEvent
    public static void hoannaResourcesRenderers(EntityRenderersEvent.RegisterRenderers event) {
    }

    /** 喷口 charge ≥ 2：顶面往上飘 1~2 颗淡紫尘（调用方判了 charge） */
    public static void ventDust(Level level, BlockPos pos, RandomSource random) {
        if (!near(pos) || !VENT_COOLDOWN.tryFire(pos.asLong(), level.getGameTime())) {
            return;
        }
        int count = 1 + random.nextInt(MAX_PER_CALL);
        for (int i = 0; i < count; i++) {
            level.addParticle(VENT_DUST, pos.getX() + 0.2D + random.nextDouble() * 0.6D, pos.getY() + 1.02D,
                    pos.getZ() + 0.2D + random.nextDouble() * 0.6D, 0D, 0.03D + random.nextDouble() * 0.02D, 0D);
        }
    }

    /** 日阵中头顶露天的榍石矿：随日阵淡入强度冒一颗小闪光（不改方块状态） */
    public static void titaniteGlint(Level level, BlockPos pos, RandomSource random) {
        float fade = HoannaClientEvents.fade(HoannaEvent.SUN_ARRAY, 0F);
        if (fade <= 0F || random.nextFloat() >= fade || !near(pos) || !level.canSeeSky(pos.above())) {
            return;
        }
        if (!TITANITE_COOLDOWN.tryFire(pos.asLong(), level.getGameTime())) {
            return;
        }
        level.addParticle(TITANITE_GLINT, pos.getX() + random.nextDouble(), pos.getY() + 1.02D, pos.getZ() + random.nextDouble(), 0D, 0D, 0D);
    }

    /** 黑暗天灾中的成熟影晶簇：周围冒 1~2 颗暗紫尘、慢慢往上飘（调用方判了 ripe） */
    public static void umbralDust(Level level, BlockPos pos, RandomSource random) {
        float fade = HoannaClientEvents.fade(HoannaEvent.DARKNESS, 0F);
        if (fade <= 0F || random.nextFloat() >= fade || !near(pos) || !UMBRAL_COOLDOWN.tryFire(pos.asLong(), level.getGameTime())) {
            return;
        }
        int count = 1 + random.nextInt(MAX_PER_CALL);
        for (int i = 0; i < count; i++) {
            level.addParticle(UMBRAL_DUST, pos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 1.2D, pos.getY() + 0.3D + random.nextDouble() * 0.6D,
                    pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 1.2D, 0D, 0.015D, 0D);
        }
    }

    /** 离相机不超过 {@link #MAX_DISTANCE} 格 */
    private static boolean near(BlockPos pos) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        return camera.getPosition().distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= MAX_DISTANCE * MAX_DISTANCE;
    }
}
