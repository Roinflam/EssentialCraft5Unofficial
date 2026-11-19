package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.BuilderJobs;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * 创世神之杖的纯客户端设置：CLIENT 配置 {@code config/essentialcraft5-client.toml} 的 {@code [builder]}，每台电脑自己定（{@code registry/ECBuilder.init} 登记）。
 * <ul>
 *   <li>{@code ghost}（true）：铺面、直线形状下画要铺的范围（{@link BuilderGhost}）；</li>
 *   <li>{@code ghost_max}（512）：要画的格子超过这么多时只画外框和顶面网格；</li>
 *   <li>{@code hud}（true）：拿着魔杖时在准星下方显示形状、尺寸、材料和进度（{@link BuilderHud}）；</li>
 *   <li>{@code confirm_threshold}（1024）：一单要动的格子超过它时要再点一次确认。这个值经操作包报给服务端，由服务端判定。</li>
 *   <li>{@code flights}（true）：显示方块从魔杖飞向目标格的特效；</li>
 *   <li>{@code flights_max}（0）：同时飞行的方块最多几块，0 = 按特效画质档取 24 / 64 / 128；</li>
 *   <li>{@code flight_sounds}（true）：飞行特效的起飞声和落位声。</li>
 * </ul>
 * common 类（只有 ForgeConfigSpec，不 import 客户端类）：专用服上 CLIENT 配置不加载。虚影和提示每帧读，在加载 / 重载事件里缓存进 volatile 字段；
 * 重载时记一下「要重报确认阈值」，由 {@link BuilderInput} 在下一刻发出去。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class BuilderClientConfig {

    public static final boolean DEFAULT_GHOST = true;
    public static final int DEFAULT_GHOST_MAX = 512;
    public static final boolean DEFAULT_HUD = true;
    public static final boolean DEFAULT_FLIGHTS = true;
    /** 0 = 按特效画质档定 */
    public static final int DEFAULT_FLIGHTS_MAX = 0;
    public static final boolean DEFAULT_FLIGHT_SOUNDS = true;

    private static ForgeConfigSpec.BooleanValue ghost;
    private static ForgeConfigSpec.IntValue ghostMax;
    private static ForgeConfigSpec.BooleanValue hud;
    private static ForgeConfigSpec.IntValue confirmThreshold;
    private static ForgeConfigSpec.BooleanValue flights;
    private static ForgeConfigSpec.IntValue flightsMax;
    private static ForgeConfigSpec.BooleanValue flightSounds;

    private static volatile boolean cachedGhost = DEFAULT_GHOST;
    private static volatile int cachedGhostMax = DEFAULT_GHOST_MAX;
    private static volatile boolean cachedHud = DEFAULT_HUD;
    private static volatile int cachedConfirmThreshold = BuilderJobs.DEFAULT_CONFIRM_THRESHOLD;
    private static volatile boolean cachedFlights = DEFAULT_FLIGHTS;
    private static volatile int cachedFlightsMax = DEFAULT_FLIGHTS_MAX;
    private static volatile boolean cachedFlightSounds = DEFAULT_FLIGHT_SOUNDS;
    /** 确认阈值变了（或刚加载），要重报给服务端 */
    private static volatile boolean prefsDirty = true;

    private BuilderClientConfig() {
    }

    /** CLIENT 配置的 {@code [builder]} 小节 */
    public static void define(ForgeConfigSpec.Builder builder) {
        builder.comment("Building Wand display (client only).", "创世神之杖的显示（只影响这台电脑）").push("builder");
        ghost = builder.comment("Show a preview of the area a plane or line will cover.",
                        "铺面、直线形状下显示要铺的范围")
                .define("ghost", DEFAULT_GHOST);
        ghostMax = builder.comment("Above this many blocks the preview only draws the outline and a grid on top.",
                        "要画的格子超过这么多时只画外框和顶面网格")
                .defineInRange("ghost_max", DEFAULT_GHOST_MAX, 16, 4096);
        hud = builder.comment("Show shape, size, material and progress below the crosshair while holding a wand.",
                        "拿着魔杖时在准星下方显示形状、尺寸、材料和进度")
                .define("hud", DEFAULT_HUD);
        confirmThreshold = builder.comment("A job that changes more blocks than this needs a second click to start.",
                        "一单要改动的格子超过这么多时，要再点一次才开工")
                .defineInRange("confirm_threshold", BuilderJobs.DEFAULT_CONFIRM_THRESHOLD, 0, 1 << 20);
        flights = builder.comment("Show blocks flying from the wand to their places.",
                        "显示方块从魔杖飞向目标格的特效")
                .define("flights", DEFAULT_FLIGHTS);
        flightsMax = builder.comment("Most blocks in flight at once (0 = follow the effect quality: 24 / 64 / 128 for low / medium / high).",
                        "同时飞行的方块数上限（0 = 按特效画质：低、中、高分别为 24、64、128）")
                .defineInRange("flights_max", DEFAULT_FLIGHTS_MAX, 0, 1024);
        flightSounds = builder.comment("Play take-off and landing sounds for flying blocks.",
                        "播放飞行方块的起飞声和落位声")
                .define("flight_sounds", DEFAULT_FLIGHT_SOUNDS);
        builder.pop();
    }

    public static boolean ghost() {
        return cachedGhost;
    }

    public static int ghostMax() {
        return cachedGhostMax;
    }

    public static boolean hud() {
        return cachedHud;
    }

    public static int confirmThreshold() {
        return cachedConfirmThreshold;
    }

    public static boolean flights() {
        return cachedFlights;
    }

    /** 同时飞行的上限；0 = 按特效画质档定（由画特效的一方换算） */
    public static int flightsMax() {
        return cachedFlightsMax;
    }

    public static boolean flightSounds() {
        return cachedFlightSounds;
    }

    /** 要不要把确认阈值报给服务端；返回 true 的同时清掉标记 */
    static boolean takePrefsDirty() {
        boolean dirty = prefsDirty;
        prefsDirty = false;
        return dirty;
    }

    /** 进了一个新服务器：重报一次 */
    static void markPrefsDirty() {
        prefsDirty = true;
    }

    private static void refresh() {
        cachedGhost = ghost == null ? DEFAULT_GHOST : ECConfig.getClient(ghost);
        cachedGhostMax = ghostMax == null ? DEFAULT_GHOST_MAX : ECConfig.getClient(ghostMax);
        cachedHud = hud == null ? DEFAULT_HUD : ECConfig.getClient(hud);
        cachedFlights = flights == null ? DEFAULT_FLIGHTS : ECConfig.getClient(flights);
        cachedFlightsMax = flightsMax == null ? DEFAULT_FLIGHTS_MAX : ECConfig.getClient(flightsMax);
        cachedFlightSounds = flightSounds == null ? DEFAULT_FLIGHT_SOUNDS : ECConfig.getClient(flightSounds);
        int threshold = confirmThreshold == null ? BuilderJobs.DEFAULT_CONFIRM_THRESHOLD : ECConfig.getClient(confirmThreshold);
        if (threshold != cachedConfirmThreshold) {
            prefsDirty = true;
        }
        cachedConfirmThreshold = threshold;
    }

    @SubscribeEvent
    public static void onBuilderClientConfigLoading(ModConfigEvent.Loading event) {
        if (ECConfig.isClientConfig(event.getConfig())) {
            refresh();
        }
    }

    @SubscribeEvent
    public static void onBuilderClientConfigReloading(ModConfigEvent.Reloading event) {
        if (ECConfig.isClientConfig(event.getConfig())) {
            refresh();
        }
    }
}
