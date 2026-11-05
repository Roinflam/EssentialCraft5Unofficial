package com.flameforge.essentialcraft5.client.hoanna;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.hoanna.HoannaClientConfig;
import com.flameforge.essentialcraft5.hoanna.HoannaVisuals;
import com.flameforge.essentialcraft5.hoanna.Radiation;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.Arrays;

/**
 * 第十三批 13d 的两种客户端粒子（batch13.md §6.2，HS §2.2.3 / §2.2.4；游戏总线、只在客户端，不发包），数值都在 {@link HoannaVisuals}：
 * <ul>
 *   <li><b>地脉风暴的光点</b>：风暴淡入值 &gt; {@value HoannaVisuals#STORM_MOTE_MIN_FADE} 时，每刻至多 {@value HoannaVisuals#STORM_MOTES_PER_TICK} 颗
 *       （粒子设置「少量」时每 3 刻一次、「最少」不放），镜头 {@value HoannaVisuals#STORM_MOTE_RADIUS} 格内、上方 2~10 格，缓缓往下飘；
 *       按原版尘粒子的最长寿命记账，同时 ≤ {@value HoannaVisuals#MAX_STORM_MOTES} 颗。原版 {@code DustParticleOptions}，不注册新粒子。</li>
 *   <li><b>辐射热点的细尘</b>：客户端配置 {@code [hoanna] hotspot_particles} 开着、在海纳尔、不是创造 / 旁观、所在区块的辐射底数
 *       {@code Radiation.chunkBase} ≥ {@value HoannaVisuals#HOTSPOT_MIN_BASE}（客户端自己算，和服务端同一个纯函数）时，每
 *       {@value HoannaVisuals#HOTSPOT_INTERVAL} 刻在玩家 {@value HoannaVisuals#HOTSPOT_RADIUS} 格内放 1 颗灰绿细尘。</li>
 * </ul>
 * 风暴的天色、光幕在 {@link HoannaSkyRenderer} / {@link HoannaEventVisuals}（雾色）；没有闪光。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class HoannaStormVisuals {

    /** 原版尘粒子最长寿命（8 / 0.2 × 大小，取上界）+ 余量 */
    private static final int MOTE_MAX_LIFE = 48;
    private static final DustParticleOptions MOTE = new DustParticleOptions(color(HoannaVisuals.STORM_MOTE_COLOR), HoannaVisuals.STORM_MOTE_SCALE);
    private static final DustParticleOptions HOTSPOT = new DustParticleOptions(color(HoannaVisuals.HOTSPOT_COLOR), HoannaVisuals.HOTSPOT_SCALE);
    private static final int[] MOTE_SLOTS = new int[HoannaVisuals.MAX_STORM_MOTES];

    /** 客户端刻数（暂停时不走） */
    private static int ticks;

    private HoannaStormVisuals() {
    }

    private static Vector3f color(int rgb) {
        return new Vector3f(HoannaVisuals.red(rgb), HoannaVisuals.green(rgb), HoannaVisuals.blue(rgb));
    }

    @SubscribeEvent
    public static void onHoannaStormClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null || minecraft.isPaused() || !HoannaClientEvents.inHoanna()) {
            return;
        }
        ticks++;
        ParticleStatus particles = minecraft.options.particles().get();
        if (particles == ParticleStatus.MINIMAL) {
            return;
        }
        RandomSource random = level.random;
        float storm = HoannaClientEvents.fade(HoannaEvent.LEY_STORM, 0F);
        if (storm > HoannaVisuals.STORM_MOTE_MIN_FADE && (particles == ParticleStatus.ALL || ticks % 3 == 0)) {
            Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
            for (int i = 0; i < HoannaVisuals.STORM_MOTES_PER_TICK; i++) {
                if (random.nextFloat() < storm) {
                    spawnMote(level, camera, random);
                }
            }
        }
        if (ticks % HoannaVisuals.HOTSPOT_INTERVAL == 0 && HoannaClientConfig.hotspotParticles() && !player.isCreative() && !player.isSpectator()
                && HoannaVisuals.hotspotVisible(Radiation.chunkBase(player.chunkPosition().x, player.chunkPosition().z))) {
            double r = HoannaVisuals.HOTSPOT_RADIUS;
            double x = player.getX() + (random.nextDouble() * 2D - 1D) * r;
            double z = player.getZ() + (random.nextDouble() * 2D - 1D) * r;
            double y = player.getY() + 0.3D + random.nextDouble() * 2D;
            level.addParticle(HOTSPOT, x, y, z, 0D, 0.01D, 0D);
        }
    }

    /** 光点：镜头 16 格内、上方 2~10 格，往下飘（离镜头超过 24 格不放） */
    private static void spawnMote(ClientLevel level, Vec3 camera, RandomSource random) {
        int slot = freeSlot();
        if (slot < 0) {
            return;
        }
        double r = HoannaVisuals.STORM_MOTE_RADIUS;
        double dx = (random.nextDouble() * 2D - 1D) * r;
        double dz = (random.nextDouble() * 2D - 1D) * r;
        double dy = 2D + random.nextDouble() * 8D;
        double max = HoannaVisuals.STORM_MOTE_MAX_DISTANCE;
        if (dx * dx + dy * dy + dz * dz > max * max) {
            return;
        }
        MOTE_SLOTS[slot] = ticks + MOTE_MAX_LIFE;
        level.addParticle(MOTE, camera.x + dx, camera.y + dy, camera.z + dz,
                (random.nextDouble() * 2D - 1D) * 0.01D, -0.04D, (random.nextDouble() * 2D - 1D) * 0.01D);
    }

    private static int freeSlot() {
        for (int i = 0; i < MOTE_SLOTS.length; i++) {
            if (MOTE_SLOTS[i] - ticks <= 0) {
                return i;
            }
        }
        return -1;
    }

    @SubscribeEvent
    public static void onHoannaStormLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        Arrays.fill(MOTE_SLOTS, 0);
    }
}
