package com.flameforge.essentialcraft5.client.guide;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.config.ECConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * 研究手册的界面大小：CLIENT 配置 {@code config/essentialcraft5-client.toml} 的 {@code [guide] gui_scale}，每台电脑自己定，服务器不管、不同步。
 * 由 {@code ECGuide.init()} 调 {@code ECConfig.client(GuideScaleConfig::define)} 登记。
 * <ul>
 *   <li>{@code 0}（默认）：自动 —— 游戏选「自动」时取不超过游戏倍数、虚拟屏幕至少 560×320 的最大倍数（最小 2），游戏选了手动倍数就跟游戏
 *       （{@link GuideScaleMath#pick}）；</li>
 *   <li>{@code -1}：总跟游戏的界面缩放一样；</li>
 *   <li>{@code 1~32}：指定倍数，不会比游戏的界面缩放更大（超过时按游戏倍数画、配置不改）。</li>
 * </ul>
 * 手册顶栏的「Aa」按钮、Ctrl + 滚轮、Ctrl − / = / 0 改的也是这一项（{@link #set}），会写回文件。
 * <p>
 * 同一小节里还有 {@code intro_animation}：手册的开场、收场动画开不开（默认开）。顶栏「Aa」左边那颗按钮切换它（{@link #setIntroAnimation}），也写回文件。
 * <p>
 * common 类（只有 ForgeConfigSpec，不 import 客户端类）：专用服上 CLIENT 配置永远不加载，值一直是默认值。
 * 打开手册时读，在 {@code ModConfigEvent.Loading / Reloading} 里缓存进 volatile 字段（同 {@code CurioSealClientConfig}）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GuideScaleConfig {

    public static final int DEFAULT_GUI_SCALE = GuideScaleMath.AUTO;

    private static ForgeConfigSpec.IntValue guiScale;
    private static ForgeConfigSpec.BooleanValue introAnimation;
    private static volatile int cachedGuiScale = DEFAULT_GUI_SCALE;
    private static volatile boolean cachedIntroAnimation = true;
    private static boolean writeFailedLogged;

    private GuideScaleConfig() {
    }

    /** CLIENT 配置的 {@code [guide]} 小节（{@code ECConfig.client} 调） */
    public static void define(ForgeConfigSpec.Builder builder) {
        builder.comment("Research book (client only).", "研究手册（只影响这台电脑）").push("guide");
        guiScale = builder.comment(
                        "Research book size. 0 = auto, -1 = always follow the game's GUI scale, 1-32 = fixed (never larger than the game's GUI scale).",
                        "研究手册的界面大小。0 = 自动；-1 = 总跟游戏的界面缩放一样；1~32 = 指定倍数（不会比游戏的界面缩放更大）。"
                                + "手册顶栏的「Aa」按钮、Ctrl + 滚轮也能调，会写回这里")
                .defineInRange("gui_scale", DEFAULT_GUI_SCALE, GuideScaleMath.FOLLOW_GAME, GuideScaleMath.MAX_FIXED);
        introAnimation = builder.comment(
                        "Play the opening and closing animation of the research book. Off: it opens and closes at once.",
                        "研究手册开不开场、收场动画。关掉以后手册直接打开、直接合上。手册顶栏「Aa」左边的按钮也能切换，会写回这里")
                .define("intro_animation", true);
        builder.pop();
    }

    /** 手册开场、收场动画开着没有 */
    public static boolean introAnimation() {
        return cachedIntroAnimation;
    }

    /** 切换开场动画并写回文件，写不进去时这次的值照样生效（同 {@link #set}） */
    public static void setIntroAnimation(boolean value) {
        cachedIntroAnimation = value;
        ForgeConfigSpec spec = ECConfig.clientSpec();
        if (introAnimation == null || spec == null || !spec.isLoaded()) {
            return;
        }
        try {
            introAnimation.set(value);
        } catch (RuntimeException e) {
            if (!writeFailedLogged) {
                writeFailedLogged = true;
                EssentialCraft5.LOGGER.warn("[指南] 研究手册的设置写不进 essentialcraft5-client.toml（文件可能被别的程序占着），这次照样生效：{}", e.toString());
            }
        }
    }

    /** 配置值（0 自动 / -1 跟游戏 / 1~32 指定） */
    public static int guiScale() {
        return cachedGuiScale;
    }

    /**
     * 改配置并写回文件（手册里调大小时）。缓存马上更新，不等文件监视器回读；配置还没加载（不该发生）时只改缓存。
     * <p>
     * Forge 的配置文件是 autosave（{@code ConfigFileTypeHandler} 建 {@code CommentedFileConfig} 时开的），{@code set} 本身就写一次文件，
     * 不再另调 {@code save}。这是在点击 / 滚轮 / 按键的处理里调的：文件被杀毒软件、同步盘占着写不进去时 NightConfig 抛的异常要接住，
     * 否则顺着界面的输入处理一路抛上去，客户端直接崩溃。写不进去时这次的值照样生效（缓存），只是下次开游戏还是旧值
     */
    public static void set(int value) {
        int v = Math.max(GuideScaleMath.FOLLOW_GAME, Math.min(GuideScaleMath.MAX_FIXED, value));
        cachedGuiScale = v;
        ForgeConfigSpec spec = ECConfig.clientSpec();
        if (guiScale == null || spec == null || !spec.isLoaded()) {
            return;
        }
        try {
            guiScale.set(v);
        } catch (RuntimeException e) {
            if (!writeFailedLogged) {
                writeFailedLogged = true;
                EssentialCraft5.LOGGER.warn("[指南] 研究手册的界面大小写不进 essentialcraft5-client.toml（文件可能被别的程序占着），这次照样生效：{}", e.toString());
            }
        }
    }

    private static void refresh() {
        cachedGuiScale = guiScale == null ? DEFAULT_GUI_SCALE : ECConfig.getClient(guiScale);
        cachedIntroAnimation = introAnimation == null || ECConfig.getClient(introAnimation);
    }

    @SubscribeEvent
    public static void onGuideScaleConfigLoading(ModConfigEvent.Loading event) {
        if (ECConfig.isClientConfig(event.getConfig())) {
            refresh();
        }
    }

    @SubscribeEvent
    public static void onGuideScaleConfigReloading(ModConfigEvent.Reloading event) {
        if (ECConfig.isClientConfig(event.getConfig())) {
            refresh();
        }
    }
}
