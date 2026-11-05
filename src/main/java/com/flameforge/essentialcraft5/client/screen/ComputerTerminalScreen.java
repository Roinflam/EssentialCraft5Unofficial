package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IMRUHandlerItem;
import com.flameforge.essentialcraft5.client.ComputerClient;
import com.flameforge.essentialcraft5.client.CurioSealClient;
import com.flameforge.essentialcraft5.client.guide.CodexTheme;
import com.flameforge.essentialcraft5.client.guide.GuideTheme;
import com.flameforge.essentialcraft5.compat.curio.CurioSealConfig;
import com.flameforge.essentialcraft5.item.ComputerArmorEvents;
import com.flameforge.essentialcraft5.item.ComputerAttunement;
import com.flameforge.essentialcraft5.item.SealNotifier;
import com.flameforge.essentialcraft5.item.attunement.Attunement;
import com.flameforge.essentialcraft5.item.module.ArmorModules;
import com.flameforge.essentialcraft5.item.module.ModuleCache;
import com.flameforge.essentialcraft5.item.module.ModuleTerminal;
import com.flameforge.essentialcraft5.item.module.ModuleType;
import com.flameforge.essentialcraft5.network.C2SComputerArmorPacket;
import com.flameforge.essentialcraft5.network.C2SComputerTerminalPacket;
import com.flameforge.essentialcraft5.network.ECNetwork;
import com.flameforge.essentialcraft5.network.S2CComputerTerminalPacket;
import com.flameforge.essentialcraft5.network.S2CComputerTerminalPacket.Entry;
import com.flameforge.essentialcraft5.network.S2CComputerTerminalPacket.FieldState;
import com.flameforge.essentialcraft5.network.S2CComputerTerminalPacket.ModulePanel;
import com.flameforge.essentialcraft5.network.S2CComputerTerminalPacket.ModuleRow;
import com.flameforge.essentialcraft5.network.S2CComputerTerminalPacket.TargetState;
import com.flameforge.essentialcraft5.registry.ECComputer;
import com.flameforge.essentialcraft5.util.NumberText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 宅男终端（tools/plan/batch10.md §5.1.8）：宅男套装的客户端面板，默认 Alt+C 打开，戴着头盔就能打开。
 * <p>
 * 只是客户端界面 + 意图包，不做服务端菜单（{@code ContainerData} 只同步 16 位、名单还得另发包，菜单还会顶掉玩家开着的别的界面）：
 * 开着时每 {@value #REFRESH_TICKS} 刻发一次 {@link C2SComputerTerminalPacket#REFRESH}，服务端回一份带名单的 {@link S2CComputerTerminalPacket}；
 * 关着时一个包都不发。按钮只发意图（封禁场开关发 {@link C2SComputerTerminalPacket#TOGGLE_SEAL}，夜视照旧发 {@link C2SComputerArmorPacket#NIGHT_VISION}），
 * 能不能做、做了以后的状态都以服务端回的为准。不暂停游戏；再按一次终端键或 Esc 关；摘了头盔自动关。
 * <p>
 * 代码画（不加贴图），配色取 {@link CodexTheme} / {@link GuideTheme}：读的面 {@code PLATE}、顶栏紫带 {@code BAND}，品红只给开关的「开」态。
 * 版面从上到下：标题与四件电量；饰品封禁场（开关、状态、耗电、还能撑多久）；夜视开关；喷射推进与反伤；范围内的玩家（可滚动）；底部按键提示。
 * <p>
 * 第十批 10c：面板上沿外面两个页签「封禁场」「模块」（上次看哪页，下次打开还是哪页）。「模块」页（batch10.md §4.3 第 7 条）按头、胸、腿、靴列出每件的模块槽，
 * 每格写模块名、状态（生效 / 关着 / 缺电 / 冷却中 / 没穿齐 / 停用 / 同种重复 / 超出格数 / 认不出）、开关和「拆下」小方钮（画一个叉）；数据是同一份带名单的终端包里的模块段
 * （{@link ModulePanel}），按钮发 {@link C2SComputerTerminalPacket#MODULE_TOGGLE} / {@link C2SComputerTerminalPacket#MODULE_REMOVE}，服务端做成以后马上回一份新的。
 * 封禁场那一页的内容和判断没动。
 * <p>
 * 第十三批 13a（batch13.md §4.4）：格数随这件的同调阶多出来（满 5 阶 3 / 3 / 2 / 2 = 10 格），格数照旧以服务端回的为准；部位标题后面写同调给了几格
 * （{@code part.slots_attuned}，按自己身上那件的阶在客户端算，不加包）。格子多了照样在列表里滚动。
 */
@OnlyIn(Dist.CLIENT)
public final class ComputerTerminalScreen extends Screen {

    /** 按键名（ComputerClient 登记，默认 Alt+C） */
    public static final String KEY = "key.essentialcraft5.computer_terminal";
    /** 开着时多少刻要一次数据（服务端同一人 10 刻内只回一次） */
    public static final int REFRESH_TICKS = 20;

    private static final String GUI = "gui.essentialcraft5.computer_terminal.";
    private static final String GROUP_SEP_KEY = "guide.essentialcraft5.ui.fmt.group_sep";

    private static final int W = 236;
    private static final int H = 222;
    private static final int PAD = 10;
    private static final int HEADER_H = 18;
    private static final int ROW_H = 10;
    private static final int LIST_TOP = 138;
    private static final int LIST_BOTTOM = H - 16;
    private static final int SWITCH_W = 26;
    private static final int SWITCH_H = 11;
    private static final int SEAL_SWITCH_Y = 24;
    private static final int NIGHT_SWITCH_Y = 80;
    private static final int BAR_W = 20;
    private static final int BAR_H = 4;
    private static final int BAR_GAP = 4;

    private static final int COL_DIST = 86;
    private static final int COL_STATE = 122;

    /** 页签：0 封禁场、1 模块 */
    private static final int PAGE_SEAL = 0;
    private static final int PAGE_MODULES = 1;
    private static final int TAB_H = 14;
    private static final int TAB_GAP = 2;
    /** 模块页 */
    private static final int MOD_SUMMARY_Y = 24;
    private static final int MOD_HINT_Y = 36;
    private static final int MOD_DIVIDER_Y = 48;
    private static final int MOD_LIST_TOP = 53;
    private static final int MOD_ROW_H = 13;
    private static final int MOD_COL_STATE = 96;
    private static final String MOD = GUI + "module.";

    /** 状态文字的颜色 */
    private static final int C_RUNNING = GuideTheme.LINK;
    private static final int C_WARN = GuideTheme.TAG_W;
    private static final int C_BAD = GuideTheme.DANGER;
    private static final int C_DIM = GuideTheme.TEXT_DIM;
    private static final int C_TEXT = GuideTheme.TEXT;
    private static final int C_PENDING = GuideTheme.TAG_T;

    /** 上次看的哪一页（关掉再打开还是那页） */
    private static int lastPage = PAGE_SEAL;

    private int left;
    private int top;
    private int ticks;
    private int scroll;
    private int page = PAGE_SEAL;
    private int moduleScroll;
    /** 模块页这一帧画出来的按钮（点击时按它找） */
    private final List<ModuleHit> moduleHits = new ArrayList<>();

    /** 模块页上一个能点的地方：动作、部位、格子号、这一格画的是哪块模块（随包发给服务端核对，名单过期时那一下不算） */
    private record ModuleHit(int x0, int y0, int x1, int y1, byte action, int part, int slot, String module) {
        boolean contains(double mx, double my) {
            return mx >= x0 && mx < x1 && my >= y0 && my < y1;
        }
    }

    /** 模块页的一行：部位标题，或者某件的一格（row 为 null = 空格子） */
    private record ModuleLine(int part, int slot, int slots, boolean header, @Nullable ModuleRow row) {
    }

    public ComputerTerminalScreen() {
        super(Component.translatable(GUI + "title"));
    }

    /** 下次打开时显示哪一页（展示厅拍「模块」页用；玩家点页签时也记在这里） */
    public static void setPageForNextOpen(boolean modules) {
        lastPage = modules ? PAGE_MODULES : PAGE_SEAL;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        // 页签画在面板上沿外面，窗口很矮时也要留出它的高度
        top = Math.max(TAB_H + 2, (height - H) / 2);
        if (ticks == 0) {
            page = lastPage;
            requestRefresh();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isSpectator() || !ComputerArmorEvents.wears(player, ArmorItem.Type.HELMET)) {
            onClose();
            return;
        }
        ticks++;
        if (ticks % REFRESH_TICKS == 0) {
            requestRefresh();
        }
    }

    private static void requestRefresh() {
        ECNetwork.sendToServer(new C2SComputerTerminalPacket(C2SComputerTerminalPacket.REFRESH));
    }

    // ------------------------------------------------------------------ 数据

    @Nullable
    private static S2CComputerTerminalPacket data() {
        return CurioSealClient.terminalData();
    }

    /** 开关是不是开着：状态不是「关」就是开着（没穿齐、用不了时也是开着、只是不工作） */
    private static boolean sealOn(@Nullable S2CComputerTerminalPacket data) {
        return data != null && data.state() != FieldState.OFF;
    }

    /** 现在点开关能不能打开：穿齐四件、功能可用（关掉总是可以） */
    private static boolean canTurnOn(Player player, @Nullable S2CComputerTerminalPacket data) {
        return ComputerArmorEvents.hasFullSet(player) && (data == null || data.availability() == 0);
    }

    private static boolean nightVisionOn(Player player) {
        MobEffectInstance effect = player.getEffect(MobEffects.NIGHT_VISION);
        return effect != null && !effect.showIcon();
    }

    /** 名单行：先是「你自己」（affects_self 时、场开着时），再是服务端给的名单 */
    private static List<Component[]> rows(@Nullable S2CComputerTerminalPacket data) {
        List<Component[]> rows = new ArrayList<>();
        if (data == null) {
            return rows;
        }
        FieldState state = data.state();
        boolean working = state == FieldState.STANDBY || state == FieldState.RUNNING || state == FieldState.NO_POWER;
        if (working && CurioSealConfig.values().affectsSelf()) {
            rows.add(new Component[]{Component.translatable(GUI + "self"), Component.empty(), Component.empty()});
        }
        S2CComputerTerminalPacket detail = CurioSealClient.terminalDetail();
        if (working && detail != null) {
            for (Entry entry : detail.entries()) {
                rows.add(new Component[]{Component.literal(entry.name()), Component.translatable(GUI + "distance", entry.distance()), targetText(entry)});
            }
        }
        return rows;
    }

    private static Component targetText(Entry entry) {
        TargetState state = TargetState.of(entry.state());
        Component text = state == TargetState.SEALED_SHARED
                ? Component.translatable(GUI + "target.sealed_shared", entry.other())
                : Component.translatable(GUI + "target." + state.name().toLowerCase(Locale.ROOT));
        int color = switch (state) {
            case SEALED, SEALED_SHARED -> C_RUNNING;
            case PENDING -> C_PENDING;
            case UNPOWERED -> C_WARN;
            default -> C_DIM;
        };
        return text.copy().withStyle(style -> style.withColor(color));
    }

    private static Component stateText(@Nullable S2CComputerTerminalPacket data) {
        if (data == null) {
            return Component.translatable(GUI + "loading").withStyle(style -> style.withColor(C_DIM));
        }
        FieldState state = data.state();
        return switch (state) {
            case OFF -> Component.translatable(GUI + "state.off").withStyle(style -> style.withColor(C_DIM));
            case STANDBY -> Component.translatable(GUI + "state.standby").withStyle(style -> style.withColor(C_DIM));
            case RUNNING -> Component.translatable(GUI + "state.running", data.sealedOthers()).withStyle(style -> style.withColor(C_RUNNING));
            case NO_POWER -> Component.translatable(GUI + "state.no_power").withStyle(style -> style.withColor(C_WARN));
            case INCOMPLETE -> Component.translatable(GUI + "state.incomplete").withStyle(style -> style.withColor(C_BAD));
            case UNAVAILABLE -> Component.translatable(GUI + "state.unavailable", reasonText(data.availability()))
                    .withStyle(style -> style.withColor(C_BAD));
            case DISABLED_WORLD -> Component.translatable(GUI + "state.disabled_world").withStyle(style -> style.withColor(C_DIM));
        };
    }

    private static Component reasonText(byte availability) {
        String[] reasons = {"available", "disabled", "curios_version", "mixin_missing"};
        String name = availability >= 0 && availability < reasons.length ? reasons[availability] : "mixin_missing";
        return Component.translatable(GUI + "reason." + name);
    }

    private static Component costText(@Nullable S2CComputerTerminalPacket data) {
        FieldState state = data == null ? FieldState.OFF : data.state();
        if ((state == FieldState.RUNNING || state == FieldState.NO_POWER) && data.mruPerSecond() > 0) {
            return Component.translatable(GUI + "cost", number(data.mruPerSecond()));
        }
        CurioSealConfig.Values cfg = CurioSealConfig.values();
        return Component.translatable(GUI + "cost.standby", number(cfg.mruBase()), number(cfg.mruPerTarget()));
    }

    private static Component enduranceText(@Nullable S2CComputerTerminalPacket data) {
        if (data == null) {
            return Component.empty();
        }
        if (data.mruAvailable() == Long.MAX_VALUE) {
            return Component.translatable(GUI + "left.infinite");
        }
        if (data.state() == FieldState.RUNNING && data.mruPerSecond() > 0) {
            long minutes = data.mruAvailable() / data.mruPerSecond() / 60L;
            return minutes < 1 ? Component.translatable(GUI + "left.under_minute")
                    : Component.translatable(GUI + "left", number(minutes));
        }
        return Component.translatable(GUI + "available", number(data.mruAvailable()));
    }

    /** 千位分组跟着界面语言键走（英文逗号、中文不分组），同研究手册 */
    private static String number(long value) {
        Language language = Language.getInstance();
        String sep = language.has(GROUP_SEP_KEY) ? language.getOrDefault(GROUP_SEP_KEY) : ",";
        return NumberText.group(Long.toString(value), sep);
    }

    // ------------------------------------------------------------------ 画

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        S2CComputerTerminalPacket data = data();
        int x = left;
        int y = top;
        // 外框、读的面、顶栏紫带
        graphics.fill(x - 1, y - 1, x + W + 1, y + H + 1, CodexTheme.EDGE);
        graphics.fill(x, y, x + W, y + H, CodexTheme.PLATE);
        graphics.fill(x, y, x + W, y + HEADER_H, GuideTheme.BAND);
        graphics.drawString(font, title, x + PAD, y + 5, GuideTheme.GLOW_BRIGHT, false);
        Component hover = drawCharges(graphics, player, mouseX, mouseY);
        drawTabs(graphics);
        List<Component> moduleHover = null;
        moduleHits.clear();
        if (page == PAGE_MODULES) {
            moduleHover = renderModules(graphics, mouseX, mouseY);
        } else {
            hover = renderSeal(graphics, player, data, hover, mouseX, mouseY);
        }

        // 底部提示
        Component hint = Component.translatable(GUI + "hint", ComputerClient.KEY_TERMINAL.getTranslatedKeyMessage());
        graphics.drawString(font, hint, x + (W - font.width(hint)) / 2, y + H - 11, GuideTheme.FAINT, false);

        super.render(graphics, mouseX, mouseY, partialTick);
        if (moduleHover != null && !moduleHover.isEmpty()) {
            graphics.renderComponentTooltip(font, moduleHover, mouseX, mouseY);
        } else if (hover != null) {
            graphics.renderTooltip(font, hover, mouseX, mouseY);
        }
    }

    /** 封禁场那一页（10a 的版面，没改） */
    @Nullable
    private Component renderSeal(GuiGraphics graphics, Player player, @Nullable S2CComputerTerminalPacket data, @Nullable Component hover,
                                 int mouseX, int mouseY) {
        int x = left;
        int y = top;
        // 饰品封禁场
        boolean on = sealOn(data);
        boolean enabled = on || canTurnOn(player, data);
        graphics.drawString(font, Component.translatable(GUI + "seal"), x + PAD, y + SEAL_SWITCH_Y + 2, enabled ? C_TEXT : C_DIM, false);
        drawSwitch(graphics, x + W - PAD - SWITCH_W, y + SEAL_SWITCH_Y, on, enabled);
        drawClipped(graphics, stateText(data), x + PAD, y + 40, W - 2 * PAD, C_TEXT);
        drawClipped(graphics, costText(data), x + PAD, y + 52, W - 2 * PAD, C_DIM);
        drawClipped(graphics, enduranceText(data), x + PAD, y + 64, W - 2 * PAD, C_DIM);

        // 夜视
        boolean night = nightVisionOn(player);
        graphics.drawString(font, Component.translatable(GUI + "night_vision"), x + PAD, y + NIGHT_SWITCH_Y + 2, C_TEXT, false);
        drawSwitch(graphics, x + W - PAD - SWITCH_W, y + NIGHT_SWITCH_Y, night, true);

        // 喷射推进、反伤（穿齐四件才有）
        ECComputer.Config.Values armor = ECComputer.Config.values();
        if (ComputerArmorEvents.hasFullSet(player)) {
            int cooling = ComputerClient.boostCooldownTicks(player);
            Component boost = cooling > 0
                    ? Component.translatable(GUI + "boost.cooling", ComputerClient.KEY_BOOST.getTranslatedKeyMessage(),
                    SealNotifier.seconds(armor.dashCooldown()), SealNotifier.seconds(cooling))
                    : Component.translatable(GUI + "boost", ComputerClient.KEY_BOOST.getTranslatedKeyMessage(), SealNotifier.seconds(armor.dashCooldown()));
            drawClipped(graphics, boost, x + PAD, y + 96, W - 2 * PAD, C_DIM);
            if (armor.thornsRatio() > 0D) {
                drawClipped(graphics, Component.translatable(GUI + "thorns", Math.round(armor.thornsRatio() * 100D) + "%"),
                        x + PAD, y + 108, W - 2 * PAD, C_DIM);
            }
        } else {
            drawClipped(graphics, Component.translatable(GUI + "boost.incomplete"), x + PAD, y + 96, W - 2 * PAD, C_DIM);
        }

        // 范围内的玩家
        graphics.fill(x + PAD, y + 121, x + W - PAD, y + 122, GuideTheme.BORDER);
        List<Component[]> rows = rows(data);
        S2CComputerTerminalPacket detail = CurioSealClient.terminalDetail();
        int others = detail == null || !sealWorking(data) ? 0 : detail.entries().size();
        graphics.drawString(font, Component.translatable(GUI + "players", others), x + PAD, y + 126, GuideTheme.TABLE_HEAD, false);
        int visible = (LIST_BOTTOM - LIST_TOP) / ROW_H;
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows.size() - visible));
        if (!sealWorking(data)) {
            drawClipped(graphics, Component.translatable(GUI + "players.closed"), x + PAD, y + LIST_TOP, W - 2 * PAD, C_DIM);
        } else {
            if (others == 0) {
                // 只有「你自己」一行（或者一行都没有）：后面接一句「范围内没有别的玩家」
                drawClipped(graphics, Component.translatable(GUI + "players.empty"), x + PAD, y + LIST_TOP + rows.size() * ROW_H, W - 2 * PAD, C_DIM);
            }
            for (int i = 0; i < visible && scroll + i < rows.size(); i++) {
                Component tip = drawRow(graphics, rows.get(scroll + i), x, y + LIST_TOP + i * ROW_H, mouseX, mouseY);
                if (tip != null) {
                    hover = tip;
                }
            }
            if (rows.size() > visible) {
                int trackTop = y + LIST_TOP;
                int trackH = visible * ROW_H;
                int thumbH = Math.max(8, trackH * visible / rows.size());
                int thumbY = trackTop + (trackH - thumbH) * scroll / Math.max(1, rows.size() - visible);
                graphics.fill(x + W - 5, trackTop, x + W - 3, trackTop + trackH, CodexTheme.PLATE_DEEP);
                graphics.fill(x + W - 5, thumbY, x + W - 3, thumbY + thumbH, CodexTheme.FROST_EDGE);
            }
        }
        return hover;
    }

    // ------------------------------------------------------------------ 页签

    private Component tabLabel(int index) {
        return Component.translatable(GUI + (index == PAGE_SEAL ? "tab.seal" : "tab.modules"));
    }

    private int tabWidth(int index) {
        return Math.max(40, font.width(tabLabel(index)) + 12);
    }

    /** 面板上沿外面的两个页签：当前页接着顶栏紫带画，另一页压暗 */
    private void drawTabs(GuiGraphics graphics) {
        int tx = left + PAD;
        for (int i = PAGE_SEAL; i <= PAGE_MODULES; i++) {
            int tw = tabWidth(i);
            boolean current = i == page;
            int ty = top - TAB_H;
            graphics.fill(tx - 1, ty - 1, tx + tw + 1, top, CodexTheme.EDGE);
            if (current) {
                // 盖住面板外框的上沿那一行，和顶栏紫带连成一片
                graphics.fill(tx, ty, tx + tw, top, GuideTheme.BAND);
            } else {
                graphics.fill(tx, ty + 1, tx + tw, top - 1, CodexTheme.PLATE_DEEP);
            }
            Component label = tabLabel(i);
            graphics.drawString(font, label, tx + (tw - font.width(label)) / 2, ty + 4, current ? GuideTheme.GLOW_BRIGHT : C_DIM, false);
            tx += tw + TAB_GAP;
        }
    }

    /** 鼠标压着哪个页签（没有为 -1） */
    private int tabAt(double mouseX, double mouseY) {
        if (mouseY < top - TAB_H - 1 || mouseY >= top) {
            return -1;
        }
        int tx = left + PAD;
        for (int i = PAGE_SEAL; i <= PAGE_MODULES; i++) {
            int tw = tabWidth(i);
            if (mouseX >= tx - 1 && mouseX < tx + tw + 1) {
                return i;
            }
            tx += tw + TAB_GAP;
        }
        return -1;
    }

    // ------------------------------------------------------------------ 模块页

    @Nullable
    private static ModulePanel modulePanel() {
        S2CComputerTerminalPacket detail = CurioSealClient.terminalDetail();
        return detail == null ? null : detail.modules();
    }

    /** 模块页的行：每件一行标题，下面是它的每一格（格数以外还装着的也列出来，状态写「超出格数」） */
    private static List<ModuleLine> moduleLines(ModulePanel panel) {
        List<ModuleLine> lines = new ArrayList<>();
        for (int part = 0; part < 4; part++) {
            int slots = panel.slots(part);
            lines.add(new ModuleLine(part, -1, slots, true, null));
            if (slots < 0) {
                continue;
            }
            int count = slots;
            for (ModuleRow row : panel.rows()) {
                if (row.part() == part) {
                    count = Math.max(count, row.slot() + 1);
                }
            }
            for (int slot = 0; slot < count; slot++) {
                ModuleRow found = null;
                for (ModuleRow row : panel.rows()) {
                    if (row.part() == part && row.slot() == slot) {
                        found = row;
                        break;
                    }
                }
                lines.add(new ModuleLine(part, slot, slots, false, found));
            }
        }
        return lines;
    }

    /** 自己身上这个部位那件宅男盔甲的同调阶多给了几格（没穿是 0） */
    private int attunedSlots(int part) {
        ArmorItem.Type type = ModuleTerminal.part(part);
        Player player = minecraft == null ? null : minecraft.player;
        if (type == null || player == null) {
            return 0;
        }
        ItemStack stack = player.getItemBySlot(type.getSlot());
        return ComputerArmorEvents.isPiece(stack, type) ? ComputerAttunement.slotBonus(type, Attunement.stage(stack)) : 0;
    }

    private static Component partName(int part) {
        String[] names = {"helmet", "chestplate", "leggings", "boots"};
        return Component.translatable(GUI + "part." + names[Mth.clamp(part, 0, 3)]);
    }

    private static Component moduleName(ResourceLocation id) {
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item == null || item == Items.AIR ? Component.literal(id.toString()) : item.getDescription();
    }

    private static int statusColor(ModuleCache.Status status) {
        return switch (status) {
            case ACTIVE -> C_RUNNING;
            case NO_POWER, PENDING -> C_WARN;
            case COOLDOWN -> C_PENDING;
            case INCOMPLETE, OVERFLOW, UNKNOWN -> C_BAD;
            default -> C_DIM;
        };
    }

    private static Component statusText(ModuleRow row) {
        ModuleCache.Status status = ModuleCache.Status.of(row.status());
        String key = MOD + "status." + status.name().toLowerCase(Locale.ROOT);
        Component text = status == ModuleCache.Status.COOLDOWN
                ? Component.translatable(key, SealNotifier.seconds(row.cooldown()))
                : Component.translatable(key);
        int color = statusColor(status);
        return text.copy().withStyle(style -> style.withColor(color));
    }

    /** 摘要：模块生不生效、这一秒的电费 */
    private static Component summaryText(ModulePanel panel) {
        if (panel.rows().isEmpty()) {
            return Component.translatable(MOD + "summary.empty").withStyle(style -> style.withColor(C_DIM));
        }
        if (panel.requireFullSet() && !panel.fullSet()) {
            return Component.translatable(MOD + "summary.incomplete").withStyle(style -> style.withColor(C_BAD));
        }
        Component base = panel.requireFullSet() ? Component.translatable(MOD + "summary.working")
                : Component.translatable(MOD + "summary.per_piece");
        if (panel.mruPerSecond() > 0) {
            base = Component.translatable(MOD + "summary.cost", base, number(panel.mruPerSecond()));
        }
        return base.copy().withStyle(style -> style.withColor(C_TEXT));
    }

    /** 画模块页；返回鼠标压着的地方要显示的提示（没有为 null） */
    @Nullable
    private List<Component> renderModules(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = left;
        int y = top;
        ModulePanel panel = modulePanel();
        if (panel == null) {
            drawClipped(graphics, Component.translatable(GUI + "loading"), x + PAD, y + MOD_SUMMARY_Y, W - 2 * PAD, C_DIM);
            return null;
        }
        drawClipped(graphics, summaryText(panel), x + PAD, y + MOD_SUMMARY_Y, W - 2 * PAD, C_TEXT);
        drawClipped(graphics, Component.translatable(MOD + "hint"), x + PAD, y + MOD_HINT_Y, W - 2 * PAD, C_DIM);
        graphics.fill(x + PAD, y + MOD_DIVIDER_Y, x + W - PAD, y + MOD_DIVIDER_Y + 1, GuideTheme.BORDER);

        List<ModuleLine> lines = moduleLines(panel);
        int visible = (LIST_BOTTOM - MOD_LIST_TOP) / MOD_ROW_H;
        moduleScroll = Mth.clamp(moduleScroll, 0, Math.max(0, lines.size() - visible));
        List<Component> hover = null;
        for (int i = 0; i < visible && moduleScroll + i < lines.size(); i++) {
            List<Component> tip = drawModuleLine(graphics, lines.get(moduleScroll + i), y + MOD_LIST_TOP + i * MOD_ROW_H, mouseX, mouseY);
            if (tip != null) {
                hover = tip;
            }
        }
        if (lines.size() > visible) {
            int trackTop = y + MOD_LIST_TOP;
            int trackH = visible * MOD_ROW_H;
            int thumbH = Math.max(8, trackH * visible / lines.size());
            int thumbY = trackTop + (trackH - thumbH) * moduleScroll / Math.max(1, lines.size() - visible);
            graphics.fill(x + W - 5, trackTop, x + W - 3, trackTop + trackH, CodexTheme.PLATE_DEEP);
            graphics.fill(x + W - 5, thumbY, x + W - 3, thumbY + thumbH, CodexTheme.FROST_EDGE);
        }
        return hover;
    }

    /** 一行：部位标题；或者一格（模块名、状态、开关、「拆下」）。返回鼠标压着时的提示 */
    @Nullable
    private List<Component> drawModuleLine(GuiGraphics graphics, ModuleLine line, int rowY, int mouseX, int mouseY) {
        int x = left;
        if (line.header()) {
            int bonus = line.slots() > 0 ? attunedSlots(line.part()) : 0;
            Component text = line.slots() < 0 ? Component.translatable(MOD + "part.missing", partName(line.part()))
                    : line.slots() == 0 ? Component.translatable(MOD + "part.none", partName(line.part()))
                    : bonus > 0 ? Component.translatable(MOD + "part.slots_attuned", partName(line.part()), line.slots(), bonus)
                    : Component.translatable(MOD + "part.slots", partName(line.part()), line.slots());
            drawClipped(graphics, text, x + PAD, rowY + 2, W - 2 * PAD, GuideTheme.TABLE_HEAD);
            return null;
        }
        int textY = rowY + 2;
        ModuleRow row = line.row();
        if (row == null) {
            drawClipped(graphics, Component.translatable(MOD + "empty"), x + PAD + 6, textY, MOD_COL_STATE - PAD - 10, C_DIM);
            return null;
        }
        // 最右边：「拆下」小方钮（画一个叉，悬停写「拆下」），左边挨着开关；方钮做小，状态那一列英文也放得下
        int removeW = SWITCH_H;
        int removeX = x + W - PAD - removeW;
        boolean overRemove = mouseX >= removeX - 1 && mouseX < removeX + removeW + 1 && mouseY >= rowY - 1 && mouseY < rowY + SWITCH_H + 1;
        graphics.fill(removeX, rowY, removeX + removeW, rowY + SWITCH_H, overRemove ? CodexTheme.FOCUS : GuideTheme.BORDER);
        graphics.fill(removeX + 1, rowY + 1, removeX + removeW - 1, rowY + SWITCH_H - 1, overRemove ? CodexTheme.FOCUS_DEEP : CodexTheme.PLATE_DEEP);
        int cross = overRemove ? CodexTheme.PEARL : C_TEXT;
        for (int i = 0; i < 5; i++) {
            graphics.fill(removeX + 3 + i, rowY + 3 + i, removeX + 4 + i, rowY + 4 + i, cross);
            graphics.fill(removeX + 7 - i, rowY + 3 + i, removeX + 8 - i, rowY + 4 + i, cross);
        }
        moduleHits.add(new ModuleHit(removeX - 1, rowY - 1, removeX + removeW + 1, rowY + SWITCH_H + 1, C2SComputerTerminalPacket.MODULE_REMOVE,
                line.part(), line.slot(), row.id().toString()));

        ModuleType type = ArmorModules.get(row.id());
        int switchX = removeX - 4 - SWITCH_W;
        if (type != null && type.toggleable()) {
            drawSwitch(graphics, switchX, rowY, row.on(), true);
            moduleHits.add(new ModuleHit(switchX - 2, rowY - 1, switchX + SWITCH_W + 2, rowY + SWITCH_H + 1, C2SComputerTerminalPacket.MODULE_TOGGLE,
                    line.part(), line.slot(), row.id().toString()));
        }

        // 状态靠开关右对齐（各行右边线落在同一列），名字一栏用掉剩下的宽度：平时状态只有「生效中 / Working」这么短，
        // 英文模块全名（最长的 Phase Anchor Module 107 像素）也放得下；状态长（等待扣电、冷却倒数）时名字才截断。
        // 状态最多往左伸到 MOD_COL_STATE，名字至少留原来那么宽
        Component name = moduleName(row.id());
        Component status = statusText(row);
        int nameX = x + PAD + 6;
        int statusRight = switchX - 4;
        int statusW = Math.min(font.width(status), statusRight - (x + MOD_COL_STATE));
        int statusX = statusRight - statusW;
        int nameW = statusX - 6 - nameX;
        drawClipped(graphics, name, nameX, textY, nameW, C_TEXT);
        drawClipped(graphics, status, statusX, textY, statusW, C_DIM);

        if (mouseY >= rowY && mouseY < rowY + MOD_ROW_H) {
            if (overRemove) {
                return List.of(Component.translatable(MOD + "remove"),
                        Component.translatable(type == null ? MOD + "remove.unknown.tip" : MOD + "remove.tip").withStyle(style -> style.withColor(C_DIM)));
            }
            if (mouseX >= x + PAD && mouseX < nameX + nameW) {
                List<Component> tip = new ArrayList<>();
                tip.add(name);
                if (type != null) {
                    tip.add(Component.translatable(type.descriptionKey()).withStyle(style -> style.withColor(C_DIM)));
                }
                return tip;
            }
            if (mouseX >= statusX && mouseX < statusRight) {
                ModuleCache.Status value = ModuleCache.Status.of(row.status());
                String tipKey = MOD + "status." + value.name().toLowerCase(Locale.ROOT) + ".tip";
                return List.of(status, value == ModuleCache.Status.COOLDOWN
                        ? Component.translatable(tipKey, SealNotifier.seconds(row.cooldown())).withStyle(style -> style.withColor(C_DIM))
                        : Component.translatable(tipKey).withStyle(style -> style.withColor(C_DIM)));
            }
        }
        return null;
    }

    private static boolean sealWorking(@Nullable S2CComputerTerminalPacket data) {
        if (data == null) {
            return false;
        }
        FieldState state = data.state();
        return state == FieldState.STANDBY || state == FieldState.RUNNING || state == FieldState.NO_POWER;
    }

    /** 一行名单：名字、距离、状态；状态被截断时返回整句给悬停提示 */
    @Nullable
    private Component drawRow(GuiGraphics graphics, Component[] row, int x, int y, int mouseX, int mouseY) {
        if (row[1].getString().isEmpty()) {
            drawClipped(graphics, row[0], x + PAD, y, W - 2 * PAD, C_DIM);
            return null;
        }
        drawClipped(graphics, row[0], x + PAD, y, COL_DIST - PAD - 4, C_TEXT);
        drawClipped(graphics, row[1], x + COL_DIST, y, COL_STATE - COL_DIST - 4, C_DIM);
        int stateW = W - COL_STATE - 8;
        boolean clipped = drawClipped(graphics, row[2], x + COL_STATE, y, stateW, C_DIM);
        boolean over = mouseY >= y && mouseY < y + ROW_H && mouseX >= x + COL_STATE && mouseX < x + COL_STATE + stateW;
        return clipped && over ? row[2] : null;
    }

    /** 画一行字，放不下就截断加省略号；返回是否截断了 */
    private boolean drawClipped(GuiGraphics graphics, Component text, int x, int y, int maxWidth, int color) {
        if (font.width(text) <= maxWidth) {
            graphics.drawString(font, text, x, y, color, false);
            return false;
        }
        String ellipsis = "…";
        String cut = font.plainSubstrByWidth(text.getString(), Math.max(0, maxWidth - font.width(ellipsis)));
        Integer textColor = text.getStyle().getColor() == null ? null : text.getStyle().getColor().getValue();
        graphics.drawString(font, cut + ellipsis, x, y, textColor != null ? textColor | 0xFF000000 : color, false);
        return true;
    }

    /** 开关：开 = 品红底、圆钮在右；关 = 深底描边、圆钮在左；不能点时整体压暗 */
    private static void drawSwitch(GuiGraphics graphics, int x, int y, boolean on, boolean enabled) {
        int edge = enabled ? GuideTheme.BORDER : CodexTheme.PLATE_HI;
        graphics.fill(x, y, x + SWITCH_W, y + SWITCH_H, edge);
        int body = on ? (enabled ? CodexTheme.FOCUS : CodexTheme.FOCUS_DEEP) : CodexTheme.PLATE_DEEP;
        graphics.fill(x + 1, y + 1, x + SWITCH_W - 1, y + SWITCH_H - 1, body);
        int knobX = on ? x + SWITCH_W - 1 - 9 : x + 1;
        graphics.fill(knobX, y + 1, knobX + 9, y + SWITCH_H - 1, enabled ? CodexTheme.PEARL : CodexTheme.FAINT);
    }

    /** 顶栏右边四件的电量小条；鼠标压着时返回那件的电量文字 */
    @Nullable
    private Component drawCharges(GuiGraphics graphics, Player player, int mouseX, int mouseY) {
        int max = Math.max(1, ECComputer.Config.values().maxMru());
        ArmorItem.Type[] types = ArmorItem.Type.values();
        int total = types.length * BAR_W + (types.length - 1) * BAR_GAP;
        int bx = left + W - PAD - total;
        int by = top + (HEADER_H - BAR_H) / 2;
        Component hover = null;
        for (ArmorItem.Type type : types) {
            ItemStack stack = player.getItemBySlot(type.getSlot());
            boolean worn = ComputerArmorEvents.isPiece(stack, type);
            int mru = worn ? stack.getCapability(ECCapabilities.MRU_ITEM).map(IMRUHandlerItem::getMRU).orElse(0) : 0;
            graphics.fill(bx - 1, by - 1, bx + BAR_W + 1, by + BAR_H + 1, worn ? GuideTheme.BORDER : CodexTheme.PLATE_HI);
            graphics.fill(bx, by, bx + BAR_W, by + BAR_H, CodexTheme.PLATE_DEEP);
            int filled = worn ? Mth.clamp((int) Math.round((double) mru * BAR_W / max), mru > 0 ? 1 : 0, BAR_W) : 0;
            if (filled > 0) {
                graphics.fill(bx, by, bx + filled, by + BAR_H, CodexTheme.MRU_VIOLET);
            }
            if (mouseX >= bx - 1 && mouseX <= bx + BAR_W && mouseY >= by - 3 && mouseY <= by + BAR_H + 2) {
                hover = worn ? Component.translatable(GUI + "charge", stack.getHoverName(), number(mru), number(max))
                        : Component.translatable(GUI + "charge.missing", Component.translatable(GUI + "part." + type.getName()));
            }
            bx += BAR_W + BAR_GAP;
        }
        return hover;
    }

    // ------------------------------------------------------------------ 输入

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (button == 0 && player != null) {
            int tab = tabAt(mouseX, mouseY);
            if (tab >= 0) {
                if (tab != page) {
                    page = tab;
                    lastPage = tab;
                    click(minecraft);
                }
                return true;
            }
            if (page == PAGE_MODULES) {
                for (ModuleHit hit : moduleHits) {
                    if (hit.contains(mouseX, mouseY)) {
                        ECNetwork.sendToServer(new C2SComputerTerminalPacket(hit.action(), hit.part(), hit.slot(), hit.module()));
                        click(minecraft);
                        return true;
                    }
                }
                return super.mouseClicked(mouseX, mouseY, button);
            }
            int sx = left + W - PAD - SWITCH_W;
            S2CComputerTerminalPacket data = data();
            if (inside(mouseX, mouseY, sx, top + SEAL_SWITCH_Y) && (sealOn(data) || canTurnOn(player, data))) {
                ECNetwork.sendToServer(new C2SComputerTerminalPacket(C2SComputerTerminalPacket.TOGGLE_SEAL));
                click(minecraft);
                return true;
            }
            if (inside(mouseX, mouseY, sx, top + NIGHT_SWITCH_Y)) {
                ECNetwork.sendToServer(new C2SComputerArmorPacket(C2SComputerArmorPacket.NIGHT_VISION));
                click(minecraft);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x - 2 && mouseX < x + SWITCH_W + 2 && mouseY >= y - 2 && mouseY < y + SWITCH_H + 2;
    }

    private static void click(Minecraft minecraft) {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1F));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (page == PAGE_MODULES) {
            if (mouseX >= left && mouseX < left + W && mouseY >= top + MOD_LIST_TOP && mouseY < top + LIST_BOTTOM) {
                moduleScroll -= (int) Math.signum(delta);
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        if (mouseX >= left && mouseX < left + W && mouseY >= top + LIST_TOP && mouseY < top + LIST_BOTTOM) {
            scroll -= (int) Math.signum(delta);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ComputerClient.KEY_TERMINAL.matches(keyCode, scanCode) && ComputerClient.KEY_TERMINAL.getKeyModifier().isActive(KeyConflictContext.GUI)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
