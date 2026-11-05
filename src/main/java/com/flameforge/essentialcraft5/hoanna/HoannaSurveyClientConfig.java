package com.flameforge.essentialcraft5.hoanna;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.config.ECConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * 海纳尔勘测图的纯客户端设置（第十三批 13d-H3，batch13.md §6.3；HS §2.9）：CLIENT 配置 {@code [hoanna]} 小节里的 {@code survey_beam}
 * （默认 true）：夜里或黑暗天灾中，拿着绑定好的勘测图时，在目标那一列画一道只有自己看得见的淡紫光柱。
 * 和 {@link HoannaClientConfig} 同一个小节（各自 push / pop，键不重名）。由 {@code ECHoannaSurvey.init()} 登记。
 * common 类（只有 ForgeConfigSpec），渲染每帧读的值在配置加载 / 重载时缓存进 volatile 字段。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class HoannaSurveyClientConfig {

    public static final boolean DEFAULT_SURVEY_BEAM = true;

    private static ForgeConfigSpec.BooleanValue surveyBeam;
    private static volatile boolean cachedSurveyBeam = DEFAULT_SURVEY_BEAM;

    private HoannaSurveyClientConfig() {
    }

    public static void define(ForgeConfigSpec.Builder builder) {
        builder.push("hoanna");
        surveyBeam = builder.comment(
                        "At night or during Darkness, draw a faint violet beam over the spot your Hoanna Survey Map points to "
                                + "(only you see it, only while you hold the map, only 32 to 512 blocks away).",
                        "夜里或黑暗天灾中，拿着绑定好的海纳尔勘测图时，在标的那一列画一道淡紫光柱（只有自己看得见，目标在 32~512 格时才画）")
                .define("survey_beam", DEFAULT_SURVEY_BEAM);
        builder.pop();
    }

    /** 画不画勘测图的光柱 */
    public static boolean surveyBeam() {
        return cachedSurveyBeam;
    }

    private static void refresh() {
        if (surveyBeam != null) {
            cachedSurveyBeam = ECConfig.getClient(surveyBeam);
        }
    }

    @SubscribeEvent
    public static void onHoannaSurveyConfigLoading(ModConfigEvent.Loading event) {
        if (ECConfig.isClientConfig(event.getConfig())) {
            refresh();
        }
    }

    @SubscribeEvent
    public static void onHoannaSurveyConfigReloading(ModConfigEvent.Reloading event) {
        if (ECConfig.isClientConfig(event.getConfig())) {
            refresh();
        }
    }
}
