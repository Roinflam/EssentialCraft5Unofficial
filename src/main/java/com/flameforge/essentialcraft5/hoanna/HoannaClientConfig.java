package com.flameforge.essentialcraft5.hoanna;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.config.ECConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import javax.annotation.Nullable;

/**
 * 海纳尔画面的纯客户端设置（第六批 6a M3，batch6a.md §5.3）：CLIENT 配置 {@code config/essentialcraft5-client.toml} 的 {@code [hoanna]}，
 * 每台电脑自己定，服务器不管、不同步。由 {@code ECHoannaSky.init()} 调 {@code ECConfig.client(HoannaClientConfig::define)} 登记。
 * <ul>
 *   <li>{@code sky_extras}（默认 true）：夜里的淡紫星空、MRU 光幕、远空电弧，以及两个残日和伴月。关掉只剩一日一月（日阵的 6 个残日照画：那是事件本身）。</li>
 *   <li>{@code camera_shake}（默认 1.0，0~1）：地震时镜头晃动的幅度倍数，再乘原版「屏幕扭曲效果」；0 = 不晃。</li>
 *   <li>{@code fume_haze_max}（默认 32，0~64）：同时画几只毒雾的雾团（离镜头近的先画）；0 = 不画雾团（雾色和近处拉近的雾照旧）。</li>
 *   <li>{@code hotspot_particles}（默认 true，第十三批 13d）：站在辐射热点（区块底数 ≥ 4）里时，身边偶尔飘一颗灰绿细尘。</li>
 * </ul>
 * common 类（只有 ForgeConfigSpec，不 import 客户端类）：专用服上 CLIENT 配置永远不加载，这里的值就一直是默认值。
 * 渲染代码每帧读，所以在 {@code ModConfigEvent.Loading / Reloading}（模组总线，可能在文件监视线程上发）里缓存进 volatile 字段，读的时候不装箱。
 * 展示厅拍「关掉装饰」的截图用 {@link #setShowroomSkyExtras}，不动真配置。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class HoannaClientConfig {

    public static final boolean DEFAULT_SKY_EXTRAS = true;
    public static final double DEFAULT_CAMERA_SHAKE = 1.0D;
    public static final int DEFAULT_FUME_HAZE_MAX = 32;
    public static final boolean DEFAULT_HOTSPOT_PARTICLES = true;

    private static ForgeConfigSpec.BooleanValue skyExtras;
    private static ForgeConfigSpec.DoubleValue cameraShake;
    private static ForgeConfigSpec.IntValue fumeHazeMax;
    private static ForgeConfigSpec.BooleanValue hotspotParticles;

    private static volatile boolean cachedSkyExtras = DEFAULT_SKY_EXTRAS;
    private static volatile float cachedCameraShake = (float) DEFAULT_CAMERA_SHAKE;
    private static volatile int cachedFumeHazeMax = DEFAULT_FUME_HAZE_MAX;
    private static volatile boolean cachedHotspotParticles = DEFAULT_HOTSPOT_PARTICLES;
    @Nullable
    private static volatile Boolean showroomSkyExtras;

    private HoannaClientConfig() {
    }

    /** CLIENT 配置的 {@code [hoanna]} 小节（{@code ECConfig.client} 调） */
    public static void define(ForgeConfigSpec.Builder builder) {
        builder.comment("Hoanna sky and screen effects (client only).", "海纳尔的天空与画面（只影响这台电脑）").push("hoanna");
        skyExtras = builder.comment(
                        "Draw the extras in Hoanna's sky: the faint violet stars, the MRU aurora, distant silent arcs, the two remnant suns and the companion moon. "
                                + "Off = one sun and one moon (the extra suns of a Sun Array event are still drawn).",
                        "画海纳尔天上的装饰：淡紫星空、MRU 光幕、远空无声的电弧、两个残日和伴月。关掉只剩一日一月（日阵事件多出来的残日照画）")
                .define("sky_extras", DEFAULT_SKY_EXTRAS);
        cameraShake = builder.comment(
                        "How much the view sways during a Hoanna earthquake (also multiplied by the vanilla Screen Effects setting). 0 = no sway.",
                        "海纳尔地震时画面晃动的幅度（还要乘原版「屏幕扭曲效果」）；0 = 不晃")
                .defineInRange("camera_shake", DEFAULT_CAMERA_SHAKE, 0D, 1D);
        fumeHazeMax = builder.comment(
                        "At most this many Poison Fumes get their hazy clouds drawn (nearest first). 0 = no clouds (the fog tint stays).",
                        "同时最多画几只毒雾的雾团（近的先画）；0 = 不画雾团（雾色照旧）")
                .defineInRange("fume_haze_max", DEFAULT_FUME_HAZE_MAX, 0, 64);
        hotspotParticles = builder.comment(
                        "While you stand in a radiation hotspot in Hoanna (a chunk with base radiation 4 or more), a grey-green speck drifts nearby now and then.",
                        "站在海纳尔的辐射热点（区块底数 4 以上）里时，身边偶尔飘一颗灰绿细尘")
                .define("hotspot_particles", DEFAULT_HOTSPOT_PARTICLES);
        builder.pop();
    }

    /** 画不画天空装饰（星星、光幕、电弧、残日、伴月） */
    public static boolean skyExtras() {
        Boolean forced = showroomSkyExtras;
        return forced != null ? forced : cachedSkyExtras;
    }

    /** 地震晃动幅度倍数 0~1 */
    public static float cameraShake() {
        return cachedCameraShake;
    }

    /** 同时画几只毒雾的雾团 */
    public static int fumeHazeMax() {
        return cachedFumeHazeMax;
    }

    /** 辐射热点的细尘 */
    public static boolean hotspotParticles() {
        return cachedHotspotParticles;
    }

    /** 展示厅：强制开 / 关天空装饰（null = 按配置） */
    public static void setShowroomSkyExtras(@Nullable Boolean value) {
        showroomSkyExtras = value;
    }

    private static void refresh() {
        if (skyExtras == null) {
            return;
        }
        cachedSkyExtras = ECConfig.getClient(skyExtras);
        cachedCameraShake = (float) (double) ECConfig.getClient(cameraShake);
        cachedFumeHazeMax = ECConfig.getClient(fumeHazeMax);
        cachedHotspotParticles = ECConfig.getClient(hotspotParticles);
    }

    @SubscribeEvent
    public static void onHoannaSkyConfigLoading(ModConfigEvent.Loading event) {
        if (ECConfig.isClientConfig(event.getConfig())) {
            refresh();
        }
    }

    @SubscribeEvent
    public static void onHoannaSkyConfigReloading(ModConfigEvent.Reloading event) {
        if (ECConfig.isClientConfig(event.getConfig())) {
            refresh();
        }
    }
}
