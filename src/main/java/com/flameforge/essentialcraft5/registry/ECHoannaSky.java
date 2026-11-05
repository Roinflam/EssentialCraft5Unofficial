package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.hoanna.HoannaClientConfig;

/**
 * 第六批 6a M3「天空与画面」（计划 tools/plan/batch6a.md §3、§5.3）的注册类：纯客户端的天空、雾、光照图、事件画面、毒雾雾团、辐射晕影、事件提示条。
 * 这里只登记 CLIENT 配置 {@code [hoanna]}（{@link HoannaClientConfig}）；其余都是客户端类，自带 {@code @Mod.EventBusSubscriber}：
 * <ul>
 *   <li>{@code client/hoanna/HoannaClient}（模组总线）：注册维度效果 {@code essentialcraft5:hoanna}（{@code HoannaEffects}）、两个 HUD 层
 *       {@code hoanna_radiation}（霜冻覆盖层之上）、{@code hoanna_event_hud}（Boss 血条之上）、资源重载时丢掉天空的 VBO。</li>
 *   <li>{@code HoannaEffects} + {@code HoannaSkyRenderer}：天穹、地平线带（不靠雾距离、盖满下半球）、日出扇、日月（一主两残日、主月伴月、日阵另 6 个残日）、星星、MRU 光幕、远空电弧；
 *       雾色随亮度、黑暗事件的光照图；不画云。</li>
 *   <li>{@code HoannaEventVisuals}（游戏总线）：雾色染色、毒雾近处拉近地形雾、地震镜头晃动、日阵余烬、地震落尘、黑暗环境音、强震声、电弧排期。</li>
 *   <li>{@code FumeHazeRenderer}（毒雾雾团，按注册名 {@code essentialcraft5:poison_fume} 认实体）、{@code RadiationOverlay}（辐射晕影，读已同步的玩家数据）、
 *       {@code HoannaEventHud}（顶部事件提示条）。</li>
 * </ul>
 * 数值和纯函数全在 common 的 {@code hoanna/HoannaVisuals}（GameTest {@code HoannaVisualTests} 守着）；事件状态读 F6a 的 {@code client/hoanna/HoannaClientEvents}。
 * 只用 Forge 标准事件（{@code IForgeDimensionSpecialEffects}、{@code ViewportEvent}、{@code RenderLevelStageEvent}、{@code RegisterGuiOverlaysEvent}），
 * 不写 mixin、不检测任何模组：和远景、光影、别家雾模组叠加时各做各的（batch6a.md §3.5）。
 */
public final class ECHoannaSky {

    private ECHoannaSky() {
    }

    public static void init() {
        ECConfig.client(HoannaClientConfig::define);
    }
}
