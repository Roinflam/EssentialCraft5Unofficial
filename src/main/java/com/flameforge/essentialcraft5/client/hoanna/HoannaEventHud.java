package com.flameforge.essentialcraft5.client.hoanna;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.hoanna.HoannaVisuals;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 海纳尔天灾的提示条（第六批 6a M3，D8 A；batch6a.md §3.4）：聊天里的开始 / 结束提示照原作（M4 发），另在屏幕上放一条小提示——
 * 开头 10 秒在顶部居中、接在 Boss 血条下面，显示「图标 + 名字 + 剩余时间」和一条细进度条；之后缩成「图标 + mm:ss」，挪到快捷栏左端的左上方
 * （{@link HoannaVisuals#hudCollapsedPos}：避开血量 / 护甲 / 饥饿 / 氧气、经验条和副手槽，屏幕太窄时退回顶部居中），一直挂到事件结束。
 * 天灾一挂就是几分钟到一小时（日阵、毒雾 10 分钟，黑暗 1~3 个游戏日），收起后还占着画面正上方会一直碍眼；挪到快捷栏旁边的小块不挡视线，瞄一眼也能看到还剩多久。
 * <ul>
 *   <li>配色取研究手册（{@code CodexTheme}）：底是读的面 #1F1C25、描边是护板紫带 #261A38 往亮提一档、字是暖米白 #D9D2C7 / 次要字 #ABA3B6；
 *       每种事件一个强调色（{@link HoannaVisuals#hudAccent}：日阵琥珀、毒雾酸绿、黑暗紫、地震赭），只用在左边竖条、图标和进度条上。</li>
 *   <li>出现 / 消失、展开 → 收起都是 10 tick 淡入淡出（展开态在顶上淡出、收起态在快捷栏旁淡入）；收起以后除了秒数没有任何动画。
 *       只在海纳尔、F1 隐藏（按 F1 时整个 HUD 本来就不画）。</li>
 *   <li>展开态的位置：{@code CustomizeGuiOverlayEvent.BossEventProgress} 记下这一帧每条 Boss 血条占到哪一行，接在最下面那条下面；没有 Boss 血条时贴顶。
 *       <b>被取消的事件也收</b>（6a 审查 M3-5）：自定义血条模组（例：Enhanced Boss Bars）取消事件、自己画血条并改增量，原版照样按增量往下排，
 *       所以不管取没取消都按「y + 增量 − 字高」（= 下一条的名字顶；原版就是 y + 10）记底边。
 *       HUD 层 {@code essentialcraft5:hoanna_event_hud} 排在 Boss 血条层之上（{@link HoannaClient}），同一帧里血条先画。</li>
 * </ul>
 * 展示厅：{@link #setShowroomTime}（F6a 的展示厅覆盖 {@code HoannaClientEvents.setShowroomOverride} 没有剩余时间，这里补一个）。
 * <p>
 * 第十三批 13d：地脉风暴的展开态在名字下面多一行说明（{@link #HINT_KEY_LEY_STORM}，次要字色），面板高 {@value #EXPANDED_HINT_H}；
 * 它是新天灾，聊天里那句开始提示读完就被刷走了。收起态不变。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class HoannaEventHud {

    private static final int PLATE = 0x1F1C25;
    private static final int PLATE_ALPHA = 0xE0;
    private static final int OUTLINE = 0x3A2C52;
    private static final int TRACK = 0x261A38;
    private static final int TEXT = 0xD9D2C7;
    private static final int TEXT_DIM = 0xABA3B6;
    private static final int ICON = 9;
    private static final int EXPANDED_H = 22;
    private static final int COLLAPSED_H = HoannaVisuals.HUD_COLLAPSED_HEIGHT;
    private static final int MIN_EXPANDED_W = 120;
    /** 带说明行的展开态高度 */
    private static final int EXPANDED_HINT_H = 32;
    /** 地脉风暴展开态的说明行 */
    public static final String HINT_KEY_LEY_STORM = "hud.essentialcraft5.hoanna_event.ley_storm.hint";
    private static Component stormHint;

    /** 9 × 9 的像素图标（'#' 填强调色），按事件 id 排；按行预先拆成横向连续段，画的时候一段一个 fill */
    private static final String[][] GLYPHS = {
            {},
            // 日阵：一个太阳带 8 道光芒
            {"....#....", ".#.....#.", "...###...", "..#####..", "#.#####.#", "..#####..", "...###...", ".#.....#.", "....#...."},
            // 毒雾：一团雾、下面几缕
            {".........", "...###...", "..#####..", ".#######.", "#########", ".........", ".##..##..", "...##..##", "........."},
            // 黑暗：弯月
            {"...###...", "..##.....", ".###.....", ".###.....", ".###.....", ".###.....", ".###.....", "..##.....", "...###..."},
            // 地震：地面上一道裂口
            {".....#...", "....#....", "...##....", "....##...", ".....#...", "....#....", "...#.....", ".........", "#########"},
            // 地脉风暴：一颗往下落的晶体拖着两道尾迹（13d 先放一个，画法归 13d-H2）
            {"#...#....", ".#...#...", "..#...#..", "...#.##..", "....###..", "....####.", ".....###.", "......#..", "........."}
    };
    private static final int[][] GLYPH_RUNS = new int[GLYPHS.length][];
    /** 收起态的左上角（每帧复用） */
    private static final int[] COLLAPSED_POS = new int[2];

    private static int bossBottom = -1;
    private static HoannaEvent shown = HoannaEvent.NONE;
    private static int shownTicks;
    private static float appearPrev;
    private static float appearNow;
    private static final Component[] NAMES = new Component[HoannaEvent.values().length];
    private static int cachedSeconds = -1;
    private static String cachedTime = "";

    private static boolean showroom;
    private static int showroomRemaining;
    private static int showroomTotal;
    private static boolean showroomExpanded;

    static {
        for (int id = 0; id < GLYPHS.length; id++) {
            GLYPH_RUNS[id] = runs(GLYPHS[id]);
        }
    }

    private HoannaEventHud() {
    }

    /** 每行的连续段：{x0, x1, y}（x1 不含） */
    private static int[] runs(String[] rows) {
        int[] out = new int[ICON * ICON * 3];
        int n = 0;
        for (int y = 0; y < rows.length; y++) {
            String row = rows[y];
            int x = 0;
            while (x < row.length()) {
                if (row.charAt(x) != '#') {
                    x++;
                    continue;
                }
                int start = x;
                while (x < row.length() && row.charAt(x) == '#') {
                    x++;
                }
                out[n++] = start;
                out[n++] = x;
                out[n++] = y;
            }
        }
        int[] trimmed = new int[n];
        System.arraycopy(out, 0, trimmed, 0, n);
        return trimmed;
    }

    /**
     * 展示厅：提示条按这个剩余 / 总时长显示，并强制展开或收起（事件本身用 {@code HoannaClientEvents.setShowroomOverride} 设）。
     * 调 {@link #clearShowroomTime} 撤掉。
     */
    public static void setShowroomTime(int remaining, int total, boolean expanded) {
        showroom = true;
        showroomRemaining = remaining;
        showroomTotal = total;
        showroomExpanded = expanded;
    }

    public static void clearShowroomTime() {
        showroom = false;
    }

    // ==================================================================
    // 状态
    // ==================================================================

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onHoannaHudBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        // 这一条占到下一条名字的顶（原版：血条 5 像素高、名字在 y − 9、增量 10 + 字高 → y + 10）；被取消的自定义血条按它自己设的增量算
        bossBottom = Math.max(bossBottom, HoannaVisuals.hudBossSlotBottom(event.getY(), event.getIncrement(), Minecraft.getInstance().font.lineHeight));
    }

    @SubscribeEvent
    public static void onHoannaHudClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || Minecraft.getInstance().isPaused()) {
            return;
        }
        HoannaEvent current = HoannaClientEvents.inHoanna() ? HoannaClientEvents.current() : HoannaEvent.NONE;
        appearPrev = appearNow;
        if (!current.isNone()) {
            if (current != shown) {
                shown = current;
                shownTicks = 0;
                appearPrev = appearNow = 0F;
            } else {
                shownTicks++;
            }
            appearNow = Math.min(1F, appearNow + 1F / HoannaVisuals.HUD_FADE_TICKS);
        } else {
            appearNow = Math.max(0F, appearNow - 1F / HoannaVisuals.HUD_FADE_TICKS);
            if (appearNow <= 0F) {
                shown = HoannaEvent.NONE;
            }
        }
    }

    @SubscribeEvent
    public static void onHoannaHudLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        shown = HoannaEvent.NONE;
        appearPrev = appearNow = 0F;
        clearShowroomTime();
    }

    // ==================================================================
    // 画
    // ==================================================================

    /** HUD 层（{@link HoannaClient} 登记在 Boss 血条层之上） */
    public static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        // bossBottom 已经是「下一条名字的顶」（原版血条下留 5 像素），不再加边距
        int top = HoannaVisuals.hudTop(bossBottom);
        bossBottom = -1;
        Minecraft minecraft = gui.getMinecraft();
        HoannaEvent event = shown;
        float appear = Mth.lerp(partialTick, appearPrev, appearNow);
        if (minecraft.options.hideGui || event.isNone() || appear <= 0.02F || !HoannaClientEvents.inHoanna()) {
            return;
        }
        gui.setupOverlayRenderState(true, false);
        Font font = minecraft.font;
        int remaining = showroom ? showroomRemaining : HoannaClientEvents.remainingTicks();
        int total = showroom ? showroomTotal : HoannaClientEvents.totalTicks();
        String time = time(remaining);
        float expanded;
        if (showroom) {
            expanded = showroomExpanded ? 1F : 0F;
        } else {
            expanded = 1F - HoannaVisuals.smoothstep(HoannaVisuals.HUD_EXPANDED_TICKS,
                    HoannaVisuals.HUD_EXPANDED_TICKS + HoannaVisuals.HUD_FADE_TICKS, shownTicks + partialTick);
        }
        int accent = HoannaVisuals.hudAccent(event);
        if (expanded > 0.01F) {
            drawExpanded(graphics, font, width, top, event, time, remaining, total, accent, appear * expanded);
        }
        if (expanded < 0.99F) {
            drawCollapsed(graphics, font, width, height, top, event, time, accent, appear * (1F - expanded));
        }
    }

    private static void drawExpanded(GuiGraphics graphics, Font font, int screenWidth, int top, HoannaEvent event, String time,
                                     int remaining, int total, int accent, float alpha) {
        Component name = name(event);
        Component hint = hint(event);
        int nameWidth = font.width(name);
        int timeWidth = total > 0 ? font.width(time) : 0;
        int w = Math.max(MIN_EXPANDED_W, 6 + ICON + 5 + nameWidth + 12 + timeWidth + 6);
        if (hint != null) {
            w = Math.min(Math.max(w, 6 + ICON + 5 + font.width(hint) + 6), Math.max(MIN_EXPANDED_W, screenWidth - 8));
        }
        int h = hint != null ? EXPANDED_HINT_H : EXPANDED_H;
        int x = (screenWidth - w) / 2;
        panel(graphics, x, top, w, h, accent, alpha);
        icon(graphics, event, x + 6, top + 3, accent, alpha);
        text(graphics, font, name, x + 6 + ICON + 5, top + 4, TEXT, alpha);
        if (total > 0) {
            text(graphics, font, time, x + w - 6 - timeWidth, top + 4, TEXT_DIM, alpha);
        }
        if (hint != null) {
            text(graphics, font, hint, x + 6 + ICON + 5, top + 15, TEXT_DIM, alpha);
        }
        // 细进度条：剩余的比例（慢慢缩短）
        int barX0 = x + 6;
        int barX1 = x + w - 6;
        int barY = top + h - 5;
        graphics.fill(barX0, barY, barX1, barY + 2, argb(TRACK, alpha));
        float left = total > 0 ? Mth.clamp(remaining / (float) total, 0F, 1F) : 1F;
        int fillX = barX0 + Math.round((barX1 - barX0) * left);
        if (fillX > barX0) {
            graphics.fill(barX0, barY, fillX, barY + 2, argb(accent, alpha * 0.85F));
        }
    }

    /** 收起态：快捷栏左端的左上方（{@link HoannaVisuals#hudCollapsedPos}；屏幕太窄放不下时退回顶部居中） */
    private static void drawCollapsed(GuiGraphics graphics, Font font, int screenWidth, int screenHeight, int top, HoannaEvent event, String time,
                                      int accent, float alpha) {
        boolean hasTime = !time.isEmpty();
        int timeWidth = hasTime ? font.width(time) : 0;
        int w = 6 + ICON + (hasTime ? 4 + timeWidth : 0) + 5;
        HoannaVisuals.hudCollapsedPos(screenWidth, screenHeight, w, COLLAPSED_H, top, COLLAPSED_POS);
        int x = COLLAPSED_POS[0];
        int y = COLLAPSED_POS[1];
        panel(graphics, x, y, w, COLLAPSED_H, accent, alpha);
        icon(graphics, event, x + 6, y + 2, accent, alpha);
        if (hasTime) {
            text(graphics, font, time, x + 6 + ICON + 4, y + 3, TEXT_DIM, alpha);
        }
    }

    /** 底 + 1 像素描边 + 左边 2 像素强调色竖条 */
    private static void panel(GuiGraphics graphics, int x, int y, int w, int h, int accent, float alpha) {
        graphics.fill(x, y, x + w, y + h, argb(PLATE, alpha * PLATE_ALPHA / 255F));
        int outline = argb(OUTLINE, alpha);
        graphics.fill(x, y, x + w, y + 1, outline);
        graphics.fill(x, y + h - 1, x + w, y + h, outline);
        graphics.fill(x, y + 1, x + 1, y + h - 1, outline);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, outline);
        graphics.fill(x + 1, y + 1, x + 3, y + h - 1, argb(accent, alpha));
    }

    private static void icon(GuiGraphics graphics, HoannaEvent event, int x, int y, int accent, float alpha) {
        int[] runs = GLYPH_RUNS[event.id()];
        int color = argb(accent, alpha);
        for (int i = 0; i + 2 < runs.length; i += 3) {
            graphics.fill(x + runs[i], y + runs[i + 2], x + runs[i + 1], y + runs[i + 2] + 1, color);
        }
    }

    private static void text(GuiGraphics graphics, Font font, Component text, int x, int y, int rgb, float alpha) {
        int a = Math.round(alpha * 255F);
        if (a >= 8) {
            // Font 把 alpha 高 6 位全 0 的颜色当不透明：太淡时干脆不画
            graphics.drawString(font, text, x, y, a << 24 | rgb, false);
        }
    }

    private static void text(GuiGraphics graphics, Font font, String text, int x, int y, int rgb, float alpha) {
        int a = Math.round(alpha * 255F);
        if (a >= 8) {
            graphics.drawString(font, text, x, y, a << 24 | rgb, false);
        }
    }

    private static int argb(int rgb, float alpha) {
        return Math.round(Mth.clamp(alpha, 0F, 1F) * 255F) << 24 | rgb;
    }

    /** 展开态名字下面的说明行；只有地脉风暴有 */
    private static Component hint(HoannaEvent event) {
        if (event != HoannaEvent.LEY_STORM) {
            return null;
        }
        if (stormHint == null) {
            stormHint = Component.translatable(HINT_KEY_LEY_STORM);
        }
        return stormHint;
    }

    private static Component name(HoannaEvent event) {
        Component name = NAMES[event.id()];
        if (name == null) {
            name = Component.translatable(event.nameKey());
            NAMES[event.id()] = name;
        }
        return name;
    }

    /** mm:ss（秒数变了才重建字符串）；没有剩余时间（展示厅覆盖、刚进来还没收到包）时返回空串 */
    private static String time(int remaining) {
        if (remaining <= 0) {
            return "";
        }
        int seconds = (remaining + 19) / 20;
        if (seconds != cachedSeconds) {
            cachedSeconds = seconds;
            cachedTime = HoannaVisuals.formatRemaining(remaining);
        }
        return cachedTime;
    }
}
