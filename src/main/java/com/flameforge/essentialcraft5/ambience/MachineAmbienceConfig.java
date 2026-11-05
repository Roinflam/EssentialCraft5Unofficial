package com.flameforge.essentialcraft5.ambience;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.config.ECConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * 机器工作时的声音和粒子的个人开关：CLIENT 配置 {@code config/essentialcraft5-client.toml} 的 {@code [machine_ambience]}，
 * 每台电脑自己定，服务器不管、不同步。由 {@code ECMachineSounds.init()} 调 {@code ECConfig.client(MachineAmbienceConfig::define)} 登记。
 * <p>
 * common 类（只有 ForgeConfigSpec，不 import 客户端类）：专用服上 CLIENT 配置永远不加载，值一直是默认值。
 * 每刻都要读，所以在 {@code ModConfigEvent.Loading / Reloading} 里缓存进 volatile 字段（同 {@code GuideScaleConfig}）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MachineAmbienceConfig {

    private static ForgeConfigSpec.BooleanValue sounds;
    private static ForgeConfigSpec.DoubleValue volume;
    private static ForgeConfigSpec.BooleanValue particles;

    private static volatile boolean cachedSounds = true;
    private static volatile float cachedVolume = 1.0F;
    private static volatile boolean cachedParticles = true;

    private MachineAmbienceConfig() {
    }

    /** CLIENT 配置的 {@code [machine_ambience]} 小节（{@code ECConfig.client} 调） */
    public static void define(ForgeConfigSpec.Builder builder) {
        builder.comment("Sounds and particles of working machines (client only).",
                "机器工作时的声音和粒子（只影响这台电脑）").push("machine_ambience");
        sounds = builder.comment("Play the humming and ticking sounds of working machines.",
                "是否播放机器工作时的嗡鸣、咔哒等声音").define("sounds", true);
        volume = builder.comment("Volume of those sounds, multiplied with the game's Blocks volume. 0.0-1.0.",
                "这些声音的音量，再乘上游戏设置里「方块」的音量。0.0~1.0").defineInRange("volume", 1.0D, 0.0D, 1.0D);
        particles = builder.comment("Show the particles of working machines (the game's own particle setting still applies).",
                "是否显示机器工作时的粒子（游戏设置里的粒子档位照样生效）").define("particles", true);
        builder.pop();
    }

    public static boolean sounds() {
        return cachedSounds;
    }

    public static float volume() {
        return cachedVolume;
    }

    public static boolean particles() {
        return cachedParticles;
    }

    @SubscribeEvent
    public static void onAmbienceConfigLoading(ModConfigEvent.Loading event) {
        refresh(event.getConfig());
    }

    @SubscribeEvent
    public static void onAmbienceConfigReloading(ModConfigEvent.Reloading event) {
        refresh(event.getConfig());
    }

    private static void refresh(ModConfig config) {
        if (!ECConfig.isClientConfig(config) || sounds == null) {
            return;
        }
        cachedSounds = sounds.get();
        cachedVolume = volume.get().floatValue();
        cachedParticles = particles.get();
    }
}
