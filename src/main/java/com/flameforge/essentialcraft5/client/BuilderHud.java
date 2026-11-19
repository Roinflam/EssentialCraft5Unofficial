package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.screen.BuilderPanelScreen;
import com.flameforge.essentialcraft5.item.BuilderMix;
import com.flameforge.essentialcraft5.item.BuilderShape;
import com.flameforge.essentialcraft5.network.S2CBuilderStatusPacket;
import com.flameforge.essentialcraft5.item.BuilderTier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 创世神之杖的准星下提示（tools/plan/builder_upgrade.md §1.7）：拿着魔杖、客户端配置 {@code [builder] hud} 开着时，在准星下方居中画一块小面板。
 * <ul>
 *   <li>平时两组：①形状和尺寸、要放 / 要拆几格（放不了的另报）；②手头的材料（随身容器里的另报）、这一单的 MRU 和可用 MRU，不够的那项红色。
 *       「要放」「要多少 MRU」按本地世界算（{@link BuilderGhost}），「手头」「可用」用服务端状态包里的数。</li>
 *   <li>施工 / 撤销中：进度和剩余时间（按最近 20 刻的进度估）；暂停：缺什么、还等几秒；排队：前面还有几个；沿面测量中：已连上几格；待确认：几秒内再点一次。</li>
 * </ul>
 * 排版全靠算：每行宽度用 {@code font.width} 量，面板按 {@code (屏宽 − 面板宽) / 2} 在屏幕里居中，每行按 {@code (面板宽 − 行宽) / 2} 在面板里居中
 * （取整，奇数差多出来的 1 像素落在右边，全局一致；屏宽是奇数时行若按屏幕算，相对面板会偏到左边）；
 * 面板左右各留 {@value #PAD_X}、上下各留 {@value #PAD_Y}（含字的阴影）。一行放得下就放一行，放不下按两组分两行，一组还放不下就在段与段之间折行，
 * 单段比屏幕还宽就截断加省略号；
 * 有截断、或行数超了时先换短写法重排一次（随身容器那部分不写、「可用」一词省掉、放不下的格数不报），还放不下才截断。面板顶在准星和攻击冷却指示下面（屏幕中线往下 {@value #TOP_OFFSET}），底不越过动作栏那行字
 * （屏幕底往上 {@value #ACTION_BAR_TOP}）；行数超出时最后一行把剩下的并进来截断。数字按语言分组：中文满一万写「万」、满一亿写「亿」，英文千分位。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class BuilderHud {

    /** 面板左右内边距 */
    static final int PAD_X = 4;
    /** 面板上下内边距（字高 8 加 1 行阴影，上下各空 3 行） */
    static final int PAD_Y = 3;
    /** 行距：字 9（含阴影）+ 1 */
    static final int LINE_H = 10;
    /** 面板离屏幕左右边至少这么多 */
    static final int SCREEN_MARGIN = 4;
    /** 准星 15 × 15 居中，攻击冷却指示在中线往下 9～13 行：面板从中线往下 16 行起 */
    static final int TOP_OFFSET = 16;
    /** 动作栏那行字在屏幕底往上 72～63 行（开了文字背景再各宽 2）：面板底不越过底往上 76 行 */
    static final int ACTION_BAR_TOP = 76;

    private static final int BACKGROUND = 0xA0120C1C;
    private static final int TEXT = 0xEDE4FF;
    private static final int MUTED = 0x9C90B8;
    private static final int STATUS = 0xC9A6FF;
    private static final int WARN = 0xFF6B6B;
    private static final int CONFIRM = 0xFFD27A;

    private BuilderHud() {
    }

    /** 模组总线：登记在准星那一层之后 */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class BuilderHudRegistration {
        private BuilderHudRegistration() {
        }

        @SubscribeEvent
        public static void registerBuilderOverlay(RegisterGuiOverlaysEvent event) {
            event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "builder_hud", BuilderHud::render);
        }
    }

    static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.options.hideGui || player.isSpectator() || !BuilderClientConfig.hud()
                || minecraft.screen instanceof BuilderPanelScreen) {
            // 面板开着时整屏盖住了准星，这条提示收起来（面板上自己写着这些数）
            return;
        }
        BuilderGhost.Preview preview = BuilderGhost.preview();
        if (preview == null) {
            return;
        }
        List<List<Seg>> groups = segments(player, preview, minecraft.level.getGameTime());
        Font font = minecraft.font;
        Layout layout = layout(font, groups, width, height);
        if (layout.lines().isEmpty()) {
            return;
        }
        graphics.fill(layout.x(), layout.y(), layout.x() + layout.width(), layout.y() + layout.height(), BACKGROUND);
        for (int i = 0; i < layout.lines().size(); i++) {
            FormattedCharSequence line = layout.lines().get(i);
            int lineX = layout.x() + (layout.width() - font.width(line)) / 2;
            graphics.drawString(font, line, lineX, layout.y() + PAD_Y + i * LINE_H, TEXT, true);
        }
    }

    /** 排好的面板：左上角、宽高、每行；squeezed = 有行被截断，或者行数超了、最后一行是并进来的 */
    record Layout(int x, int y, int width, int height, List<FormattedCharSequence> lines, boolean squeezed) {
    }

    /**
     * 一段字：brief 是放不下时换用的短写法（null = 没有短写法）；optional 的段放不下时整段不要
     * （例：随身容器里有多少只是附带的说明，英文的「可用」一词）
     */
    record Seg(Component full, @Nullable Component brief, boolean optional) {
        static Seg of(Component text) {
            return new Seg(text, null, false);
        }
    }

    /** 先用完整写法排；有截断或并行时改用短写法再排一次（还放不下就照短写法截断） */
    static Layout layout(Font font, List<List<Seg>> groups, int width, int height) {
        Layout full = layoutOf(font, pick(groups, false), width, height);
        return full.squeezed() ? layoutOf(font, pick(groups, true), width, height) : full;
    }

    private static List<List<Component>> pick(List<List<Seg>> groups, boolean brief) {
        List<List<Component>> out = new ArrayList<>();
        for (List<Seg> group : groups) {
            List<Component> row = new ArrayList<>();
            for (Seg seg : group) {
                if (brief && seg.optional()) {
                    continue;
                }
                row.add(brief && seg.brief() != null ? seg.brief() : seg.full());
            }
            out.add(row);
        }
        return out;
    }

    /** 按上面类注释的规则排版（纯计算，展示厅量测也用它） */
    private static Layout layoutOf(Font font, List<List<Component>> groups, int width, int height) {
        int maxInner = Math.max(16, width - 2 * SCREEN_MARGIN - 2 * PAD_X);
        int top = height / 2 + TOP_OFFSET;
        int bottom = height - ACTION_BAR_TOP;
        int maxLines = Math.max(1, (bottom - top - 2 * PAD_Y + (LINE_H - font.lineHeight)) / LINE_H);
        List<List<Component>> rows = new ArrayList<>();
        List<Component> all = new ArrayList<>();
        groups.forEach(all::addAll);
        if (all.isEmpty()) {
            return new Layout(0, 0, 0, 0, List.of(), false);
        }
        if (font.width(join(all)) <= maxInner) {
            rows.add(all);
        } else {
            for (List<Component> group : groups) {
                List<Component> row = new ArrayList<>();
                for (Component segment : group) {
                    row.add(segment);
                    if (row.size() > 1 && font.width(join(row)) > maxInner) {
                        row.remove(row.size() - 1);
                        rows.add(row);
                        row = new ArrayList<>();
                        row.add(segment);
                    }
                }
                if (!row.isEmpty()) {
                    rows.add(row);
                }
            }
        }
        boolean squeezed = rows.size() > maxLines;
        if (squeezed) {
            List<Component> last = new ArrayList<>();
            for (int i = maxLines - 1; i < rows.size(); i++) {
                last.addAll(rows.get(i));
            }
            rows = new ArrayList<>(rows.subList(0, maxLines - 1));
            rows.add(last);
        }
        List<FormattedCharSequence> lines = new ArrayList<>();
        int widest = 0;
        for (List<Component> row : rows) {
            Component text = join(row);
            squeezed |= font.width(text) > maxInner;
            FormattedCharSequence line = fit(font, text, maxInner);
            lines.add(line);
            widest = Math.max(widest, font.width(line));
        }
        int panelWidth = widest + 2 * PAD_X;
        int panelHeight = 2 * PAD_Y + lines.size() * LINE_H - (LINE_H - font.lineHeight);
        return new Layout((width - panelWidth) / 2, top, panelWidth, panelHeight, lines, squeezed);
    }

    /** 展示厅量测用：按当前界面尺寸排一次，返回面板的 x、y、宽、高和每行的 x、宽（界面坐标） */
    public static String showroomLayout() {
        Minecraft minecraft = Minecraft.getInstance();
        BuilderGhost.Preview preview = BuilderGhost.preview();
        if (minecraft.player == null || minecraft.level == null || preview == null) {
            return "none";
        }
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        Layout layout = layout(minecraft.font, segments(minecraft.player, preview, minecraft.level.getGameTime()), width, height);
        StringBuilder out = new StringBuilder();
        out.append("screen=").append(width).append('x').append(height).append(" scale=").append(minecraft.getWindow().getGuiScale())
                .append(" panel=").append(layout.x()).append(',').append(layout.y()).append(',').append(layout.width()).append(',').append(layout.height());
        for (FormattedCharSequence line : layout.lines()) {
            int lineWidth = minecraft.font.width(line);
            out.append(" line=").append(layout.x() + (layout.width() - lineWidth) / 2).append(',').append(lineWidth);
        }
        out.append(" squeezed=").append(layout.squeezed());
        return out.toString();
    }

    /** 一行：段与段之间夹分隔符 */
    private static Component join(List<Component> segments) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < segments.size(); i++) {
            if (i > 0) {
                out.append(Component.translatable("hud.essentialcraft5.builder.sep").withStyle(style -> style.withColor(MUTED)));
            }
            out.append(segments.get(i));
        }
        return out;
    }

    /** 放得下原样；放不下截到 maxWidth 并加省略号 */
    private static FormattedCharSequence fit(Font font, Component text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text.getVisualOrderText();
        }
        Component ellipsis = Component.literal("…").withStyle(style -> style.withColor(MUTED));
        FormattedText cut = font.substrByWidth(text, Math.max(0, maxWidth - font.width(ellipsis)));
        return Language.getInstance().getVisualOrder(FormattedText.composite(cut, ellipsis));
    }

    // ------------------------------------------------------------------
    // 内容
    // ------------------------------------------------------------------

    /** 两组段落：①状态、形状和要动的格数；②材料和 MRU */
    static List<List<Seg>> segments(LocalPlayer player, BuilderGhost.Preview preview, long now) {
        List<Seg> first = new ArrayList<>();
        List<Seg> second = new ArrayList<>();
        S2CBuilderStatusPacket status = BuilderClientStatus.latest();
        int state = status == null ? S2CBuilderStatusPacket.IDLE : status.state();
        boolean creative = player.getAbilities().instabuild;
        boolean working = state == S2CBuilderStatusPacket.RUNNING || state == S2CBuilderStatusPacket.UNDOING
                || S2CBuilderStatusPacket.paused(state);
        if (state == S2CBuilderStatusPacket.RUNNING || state == S2CBuilderStatusPacket.UNDOING) {
            String key = state == S2CBuilderStatusPacket.RUNNING ? "running" : "undoing";
            first.add(seg(key, STATUS, number(status.done()), number(status.total())));
            long eta = BuilderClientStatus.etaTicks(now);
            if (eta >= 0L) {
                first.add(seg("eta", TEXT, number((eta + 19L) / 20L)));
            }
        } else if (S2CBuilderStatusPacket.paused(state)) {
            long seconds = (BuilderClientStatus.pauseTicksLeft(now) + 19L) / 20L;
            first.add(seg(state == S2CBuilderStatusPacket.PAUSED ? "paused_material" : "paused_mru", WARN, number(seconds)));
        } else if (state == S2CBuilderStatusPacket.QUEUED) {
            // 前面没有排着的单子、只是名额被占着：写「等空出名额」，不写「前面还有 0 个」
            first.add(status.queueAhead() > 0 ? seg("queued", STATUS, number(status.queueAhead())) : seg("queued_slot", STATUS));
        } else if (state == S2CBuilderStatusPacket.SURVEYING) {
            first.add(seg("surveying", STATUS, number(status.done())));
        }
        int confirmTicks = BuilderClientStatus.confirmTicksLeft(now);
        if (confirmTicks > 0 && state == S2CBuilderStatusPacket.IDLE) {
            first.add(seg("confirm", CONFIRM, number((confirmTicks + 19L) / 20L)));
        }
        long have = status == null ? -1L : status.have() + status.haveInContainers();
        BuilderMix.Table mix = BuilderMix.forWand(player.getItemInHand(preview.hand()), preview.mode());
        if (!working) {
            first.add(Seg.of(shapeSegment(preview)));
            if (!preview.unset() && !preview.tooBig()) {
                if (preview.mode().bottomUp()) {
                    boolean shortMaterial = !creative && have >= 0L && materialShort(mix, preview, have);
                    first.add(seg("need_place", shortMaterial ? WARN : TEXT, number(preview.place())));
                }
                if (preview.breaks() > 0 || !preview.mode().bottomUp()) {
                    first.add(seg("need_break", TEXT, number(preview.breaks())));
                }
                if (preview.blocked() > 0) {
                    first.add(new Seg(text("blocked", MUTED, number(preview.blocked())), null, true));
                }
            }
        }
        if (creative) {
            second.add(seg("creative", MUTED));
            return List.of(first, second);
        }
        Component blockName = preview.block() == null ? null : preview.block().getName();
        // 混铺生效时材料那一段写「混铺 N 种 持有 X」（几种合计，服务端按同样几种数好发来）；红不红按每种分别判
        if (mix != null) {
            String kinds = number(mix.size());
            if (status == null) {
                second.add(seg("mix_material", TEXT, kinds, Component.translatable("hud.essentialcraft5.builder.unknown")));
            } else {
                boolean shortMaterial = !working && materialShort(mix, preview, have);
                long inContainers = status.haveInContainers();
                int colour = shortMaterial ? WARN : TEXT;
                Component total = text("mix_material", colour, kinds, number(have));
                second.add(inContainers > 0L
                        ? new Seg(text("mix_material_containers", colour, kinds, number(have), number(inContainers)), total, false)
                        : Seg.of(total));
            }
        } else if (preview.mode().bottomUp()) {
            if (blockName == null) {
                second.add(seg("no_block", WARN));
            } else if (status == null) {
                second.add(seg("material", TEXT, blockName, Component.translatable("hud.essentialcraft5.builder.unknown")));
            } else {
                boolean shortMaterial = !working && have < preview.place();
                long inContainers = status.haveInContainers();
                int colour = shortMaterial ? WARN : TEXT;
                Component total = text("material", colour, blockName, number(have));
                second.add(inContainers > 0L
                        ? new Seg(text("material_containers", colour, blockName, number(have), number(inContainers)), total, false)
                        : Seg.of(total));
            }
        } else if (preview.mode().needsBlock()) {
            second.add(blockName == null ? seg("no_block", WARN) : seg("selected", TEXT, blockName));
        }
        Component available = status == null ? Component.translatable("hud.essentialcraft5.builder.unknown")
                : status.mru() < 0L ? Component.translatable("hud.essentialcraft5.builder.infinite") : Component.literal(number(status.mru()));
        if (working || preview.unset() || preview.tooBig()) {
            second.add(seg("mru_available", TEXT, available));
        } else {
            boolean shortMru = status != null && status.mru() >= 0L && status.mru() < preview.needMru();
            second.add(new Seg(text("mru", shortMru ? WARN : TEXT, number(preview.needMru()), available),
                    text("mru_brief", shortMru ? WARN : TEXT, number(preview.needMru()), available), false));
        }
        return List.of(first, second);
    }

    /**
     * 料够不够这一下：没混铺比合计；混铺时和开工前的二次确认同一个口径，按每种分别判——表里任一种持有少于这一下要放的那种格数就算不够
     * （每种持有数是服务端另发的 {@code S2CBuilderHoldingsPacket}；还没收到、或和表对不上时退回比合计）
     */
    public static boolean materialShort(@Nullable BuilderMix.Table mix, BuilderGhost.Preview preview, long totalHave) {
        int[] need = preview.needByKind();
        if (mix == null || need == null || need.length != mix.size()) {
            return totalHave < preview.place();
        }
        long[] held = new long[need.length];
        for (int k = 0; k < need.length; k++) {
            held[k] = BuilderClientStatus.holding(mix.block(k).asItem());
        }
        int verdict = BuilderMix.shortByKind(need, held);
        return verdict < 0 ? totalHave < preview.place() : verdict == 1;
    }

    private static Component shapeSegment(BuilderGhost.Preview preview) {
        Component name = Component.translatable("message.essentialcraft5.builder.shape." + preview.shape().id());
        if (preview.shape() == BuilderShape.FACE) {
            if (preview.unset()) {
                return preview.note() == BuilderGhost.NOTE_COVERED ? text("face_covered", WARN, name) : text("face_unset", STATUS, name);
            }
            String key = switch (preview.note()) {
                case BuilderGhost.NOTE_CAPPED -> "face_capped";
                case BuilderGhost.NOTE_PREVIEW_CAPPED -> "face_preview_capped";
                default -> "face_count";
            };
            return text(key, STATUS, name, number(preview.width()));
        }
        if (preview.shape().resizable()) {
            return text("shape_sized", STATUS, name, number(preview.width()), number(preview.length()));
        }
        if (preview.unset()) {
            return text("shape_unset", STATUS, name);
        }
        if (preview.tooBig()) {
            LocalPlayer holder = Minecraft.getInstance().player;
            int max = holder == null ? 0 : BuilderTier.stats(holder.getItemInHand(preview.hand())).maxVolume();
            return text("too_big", WARN, name, number(max));
        }
        return text("shape_box", STATUS, name, number(preview.width()), number(preview.length()), number(preview.height()));
    }

    private static Seg seg(String key, int colour, Object... args) {
        return Seg.of(text(key, colour, args));
    }

    private static Component text(String key, int colour, Object... args) {
        return Component.translatable("hud.essentialcraft5.builder." + key, args).withStyle(style -> style.withColor(colour));
    }

    /**
     * 数字按语言分组：中文（简繁）不到一万每三位空一格（3 450），满一万写「12 万」「12.3 万」、满一亿写「亿」（小数一位、往下取，不往大里报）；
     * 其余语言千分位（3,450）。
     */
    public static String number(long value) {
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        return language != null && language.startsWith("zh") ? chineseNumber(value) : String.format(Locale.ROOT, "%,d", value);
    }

    static String chineseNumber(long value) {
        if (value < 0L) {
            return "-" + chineseNumber(-value);
        }
        if (value < 10_000L) {
            return String.format(Locale.ROOT, "%,d", value).replace(',', ' ');
        }
        boolean yi = value >= 100_000_000L;
        long unit = yi ? 100_000_000L : 10_000L;
        long tenths = value / (unit / 10L);
        String amount = tenths >= 1000L || tenths % 10L == 0L ? Long.toString(tenths / 10L) : (tenths / 10L) + "." + (tenths % 10L);
        return Component.translatable(yi ? "hud.essentialcraft5.builder.yi" : "hud.essentialcraft5.builder.wan", amount).getString();
    }
}
