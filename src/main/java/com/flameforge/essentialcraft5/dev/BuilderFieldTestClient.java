package com.flameforge.essentialcraft5.dev;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.BuilderClientStatus;
import com.flameforge.essentialcraft5.client.BuilderHud;
import com.flameforge.essentialcraft5.client.BuilderInput;
import com.flameforge.essentialcraft5.client.builder.BuilderFlights;
import com.flameforge.essentialcraft5.client.screen.BuilderPanelScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.Locale;

/**
 * {@link BuilderFieldTest} 的客户端一半（只在开发环境起作用）。测试工具往 {@link #request} 写一行，下一个客户端刻读走，结果在 {@link #result} 和日志「[魔杖实测]」。
 * <pre>
 * scroll &lt;+1|-1&gt; [次数]   滚轮：从 Forge 的滚轮事件那一步往下走，和真滚轮一样先给各监听器（魔杖键按着时魔杖会取消它），
 *                         没被取消就照原版切快捷栏；回报每次有没有被取消、现在选的是第几格
 * active &lt;on|off&gt;         把窗口当成在前台 / 后台（测试窗口压在底层时 isWindowActive 是 false，按住魔杖键的单按会被当成切走了窗口）
 * hud                    准星下提示的排版（面板和每行的界面坐标），同时打进日志
 * status                 最近一次收到的施工状态包
 * key                    魔杖键现在算不算按着
 * panel                  开着的面板「蓝图台」的版面（每个框的界面坐标），同时打进日志；录演示时按它算要点的位置
 * flatworld &lt;存档名&gt;      在标题画面进这个超平坦存档（没有就新建：创造、开作弊、永昼、晴天、不刷怪），录演示用的空场地
 * flights                飞行特效统计：在场的、画着方块的、峰值、上限、累计起飞 / 只闪 / 落位 / 碎掉，读完清零
 * flightstyle &lt;auto|arc|mote&gt;   飞行画法：按画质和光影包自动选 / 一律弧线 + 拖尾 / 一律光点凝成
 * shaderbody &lt;on|off&gt;     光影包下画不画飞行方块本体（off = 只画光效的退路）
 * </pre>
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class BuilderFieldTestClient {

    public static volatile String request = "";
    public static volatile String result = "";

    private BuilderFieldTestClient() {
    }

    @SubscribeEvent
    public static void onBuilderFieldClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || FMLEnvironment.production) {
            return;
        }
        String line = request;
        if (line == null || line.isBlank()) {
            return;
        }
        request = "";
        Minecraft mc = Minecraft.getInstance();
        StringBuilder out = new StringBuilder();
        for (String command : line.split(";")) {
            String c = command.trim();
            if (c.isEmpty()) {
                continue;
            }
            String r;
            try {
                r = run(mc, c);
            } catch (RuntimeException e) {
                r = "error " + e;
                EssentialCraft5.LOGGER.warn("[魔杖实测] 客户端指令出错：{}", c, e);
            }
            EssentialCraft5.LOGGER.info("[魔杖实测] 客户端 {} => {}", c, r);
            out.append(out.length() == 0 ? "" : " | ").append(r);
        }
        result = out.toString();
    }

    private static String run(Minecraft mc, String command) {
        String[] a = command.split(" +");
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "scroll" -> {
                if (mc.player == null) {
                    return "error 没有玩家";
                }
                double delta = Double.parseDouble(a[1]);
                int times = a.length > 2 ? Integer.parseInt(a[2]) : 1;
                StringBuilder b = new StringBuilder("scroll");
                for (int i = 0; i < times; i++) {
                    // 同 MouseHandler.onScroll 在游戏里（没开界面）的后半段：先问 Forge 事件，没人取消才切快捷栏
                    boolean canceled = ForgeHooksClient.onMouseScroll(mc.mouseHandler, delta);
                    if (!canceled && !mc.player.isSpectator()) {
                        mc.player.getInventory().swapPaint(delta);
                    }
                    b.append(canceled ? " 取消" : " 放行");
                }
                return b.append(" 快捷栏第 ").append(mc.player.getInventory().selected).append(" 格").toString();
            }
            case "active" -> {
                mc.setWindowActive(a[1].equals("on"));
                return "active " + mc.isWindowActive();
            }
            case "hud" -> {
                return "hud " + BuilderHud.showroomLayout();
            }
            case "status" -> {
                return "status " + BuilderClientStatus.latest();
            }
            case "key" -> {
                return "key " + BuilderInput.keyHeld();
            }
            case "panel" -> {
                if (mc.screen instanceof BuilderPanelScreen screen && screen.currentLayout() != null) {
                    String layout = screen.currentLayout().describe();
                    EssentialCraft5.LOGGER.info("[魔杖实测] 面板版面 缩放 {} {}", mc.getWindow().getGuiScale(), layout);
                    return "panel " + mc.getWindow().getGuiScaledWidth() + "x" + mc.getWindow().getGuiScaledHeight() + " " + layout;
                }
                return "panel 没开着";
            }
            case "flatworld" -> {
                if (mc.level != null) {
                    return "error 先退出当前存档";
                }
                String name = a[1];
                if (mc.getLevelSource().levelExists(name)) {
                    mc.createWorldOpenFlows().loadLevel(mc.screen, name);
                    return "flatworld 进入 " + name;
                }
                net.minecraft.world.level.GameRules rules = new net.minecraft.world.level.GameRules();
                rules.getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, null);
                rules.getRule(net.minecraft.world.level.GameRules.RULE_WEATHER_CYCLE).set(false, null);
                rules.getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false, null);
                net.minecraft.world.level.LevelSettings settings = new net.minecraft.world.level.LevelSettings(name,
                        net.minecraft.world.level.GameType.CREATIVE, false, net.minecraft.world.Difficulty.PEACEFUL, true,
                        rules, net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(name, settings,
                        new net.minecraft.world.level.levelgen.WorldOptions(0L, false, false),
                        access -> access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                                .getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT)
                                .value().createWorldDimensions());
                return "flatworld 新建 " + name;
            }
            case "flights" -> {
                return BuilderFlights.stats();
            }
            case "flightstyle" -> {
                BuilderFlights.styleOverride = switch (a[1]) {
                    case "arc" -> 1;
                    case "mote" -> 2;
                    default -> 0;
                };
                return "flightstyle " + BuilderFlights.styleOverride;
            }
            case "shaderbody" -> {
                BuilderFlights.bodyUnderShaders = a[1].equals("on");
                return "shaderbody " + BuilderFlights.bodyUnderShaders;
            }
            default -> {
                return "error 不认识的指令 " + a[0];
            }
        }
    }
}
