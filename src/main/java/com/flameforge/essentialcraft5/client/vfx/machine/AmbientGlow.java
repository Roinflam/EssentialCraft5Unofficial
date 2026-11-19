package com.flameforge.essentialcraft5.client.vfx.machine;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.ambience.AmbienceParticles;
import com.flameforge.essentialcraft5.ambience.MachineAmbienceConfig;
import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import com.flameforge.essentialcraft5.client.vfx.VfxPalette;
import com.flameforge.essentialcraft5.client.vfx.VfxParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptionsBase;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Vector3f;

/**
 * 机器氛围粒子的客户端实现（{@link AmbienceParticles}）：烟、尘粒子、电火花、末地烛 / 女巫这几种换成发光粒子池里的光点，
 * 别的类型（附魔字符、熔岩、传送门……）照原样走原版。
 * <p>
 * 一个基地几十台炉子每刻都在冒，所以：受 {@code [machine_ambience] particles} 开关管；32 格外（烟 24 格）直接不放；
 * 按质量档、原版粒子档位、远近按概率稀疏；全局每刻最多 {@value #BUDGET} 个（再乘质量系数），超出的这一刻丢掉。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class AmbientGlow {

    private static final int BUDGET = 120;
    private static final double RANGE = 32D;
    private static final double SMOKE_RANGE = 24D;
    private static final int SMOKE = 0x30283A;
    private static int usedThisTick;
    private static int spawnedSince;

    private AmbientGlow() {
    }

    /** 上次取以来放了几个（统计用，取完清零） */
    static int spawned() {
        int n = spawnedSince;
        spawnedSince = 0;
        return n;
    }

    static void add(Level level, ParticleOptions type, double x, double y, double z, double vx, double vy, double vz) {
        if (!MachineAmbienceConfig.particles()) {
            return;
        }
        boolean smoke = type == ParticleTypes.SMOKE;
        boolean spark = type == ParticleTypes.ELECTRIC_SPARK;
        boolean rod = type == ParticleTypes.END_ROD;
        boolean witch = type == ParticleTypes.WITCH;
        boolean dust = type instanceof DustParticleOptionsBase;
        // 火焰照旧走原版：生成点贴着炉口，柔边光点大半被方块挡住，看着比原版暗
        if (!MachineVfx.enabled || !(smoke || spark || rod || witch || dust)) {
            AmbienceParticles.vanilla(level, type, x, y, z, vx, vy, vz);
            return;
        }
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double d = Math.sqrt(cam.distanceToSqr(x, y, z));
        if (d > (smoke ? SMOKE_RANGE : RANGE)) {
            return;
        }
        float scale = Vfx.scale();
        if (usedThisTick >= BUDGET * Math.max(0.35F, scale)) {
            return;
        }
        // 远近系数同 Vfx.lod：24 格内满数，再往外慢慢变稀
        float keep = scale * (d <= Vfx.LOD_NEAR ? 1F : (float) Mth.clampedMap(d, Vfx.LOD_NEAR, Vfx.LOD_FAR, 1D, 0.25D));
        RandomSource random = Vfx.random();
        if (random.nextFloat() >= keep) {
            return;
        }
        usedThisTick++;
        spawnedSince++;
        if (smoke) {
            VfxParticles.spawn(x, y, z, vx * 0.5D, vy * 0.5D + 0.01D, vz * 0.5D, 26 + random.nextInt(12), 0.09F, 0.3F,
                    Vfx.argb(SMOKE, 0.28F), Vfx.argb(SMOKE, 0F), VfxMode.SMOKE, random.nextInt(1024), 0.96F, -0.0006F, 0F,
                    (random.nextFloat() - 0.5F) * 0.05F);
        } else if (spark) {
            double a = random.nextDouble() * Mth.TWO_PI;
            double sp = 0.04D + random.nextDouble() * 0.06D;
            VfxParticles.spawn(x, y, z, Math.cos(a) * sp, (random.nextDouble() - 0.3D) * sp, Math.sin(a) * sp, 4 + random.nextInt(4), 0.04F, 0.01F,
                    Vfx.argb(VfxPalette.WHITE_HOT, 1F), Vfx.argb(VfxPalette.MAGENTA, 0F), VfxMode.SPARK, 0, 0.85F, 0.01F, 2.2F, 0F);
        } else {
            int rgb;
            float size;
            if (rod) {
                rgb = VfxPalette.LAVENDER;
                size = 0.05F;
            } else if (witch) {
                rgb = VfxPalette.VIOLET;
                size = 0.06F;
            } else {
                DustParticleOptionsBase options = (DustParticleOptionsBase) type;
                Vector3f c = options.getColor();
                rgb = (Mth.clamp((int) (c.x() * 255F), 0, 255) << 16) | (Mth.clamp((int) (c.y() * 255F), 0, 255) << 8)
                        | Mth.clamp((int) (c.z() * 255F), 0, 255);
                size = 0.035F + 0.03F * Math.min(1.5F, options.getScale());
            }
            // 尘粒子原来不动、只往上飘一点；光点照这个节奏，再往白里提一点做芯
            VfxParticles.spawn(x, y, z, vx * 0.3D + (random.nextDouble() - 0.5D) * 0.004D, vy * 0.3D + 0.006D, vz * 0.3D + (random.nextDouble() - 0.5D) * 0.004D,
                    16 + random.nextInt(12), size, size * 0.3F, Vfx.argb(Vfx.lerp(rgb, 0xFFFFFF, 0.35F), 0.9F), Vfx.argb(rgb, 0F),
                    VfxMode.DOT, 500, 0.95F, -0.0004F, 0F, 0F);
        }
    }

    @SubscribeEvent
    public static void onAmbientGlowTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            usedThisTick = 0;
        }
    }

    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {

        private Setup() {
        }

        @SubscribeEvent
        public static void onAmbientGlowSetup(FMLClientSetupEvent event) {
            AmbienceParticles.install(AmbientGlow::add);
        }
    }
}
