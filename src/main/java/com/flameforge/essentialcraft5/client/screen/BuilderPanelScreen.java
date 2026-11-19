package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.client.BuilderClientStatus;
import com.flameforge.essentialcraft5.client.BuilderGhost;
import com.flameforge.essentialcraft5.client.BuilderHud;
import com.flameforge.essentialcraft5.client.BuilderInput;
import com.flameforge.essentialcraft5.client.guide.CodexTheme;
import com.flameforge.essentialcraft5.item.BuilderMix;
import com.flameforge.essentialcraft5.item.BuilderMode;
import com.flameforge.essentialcraft5.item.BuilderModules;
import com.flameforge.essentialcraft5.item.BuilderSalvage;
import com.flameforge.essentialcraft5.item.BuilderSelection;
import com.flameforge.essentialcraft5.item.BuilderShape;
import com.flameforge.essentialcraft5.item.BuilderTier;
import com.flameforge.essentialcraft5.item.PavingWandItem;
import com.flameforge.essentialcraft5.network.C2SBuilderActionPacket;
import com.flameforge.essentialcraft5.network.ECNetwork;
import com.flameforge.essentialcraft5.network.S2CBuilderPanelPacket;
import com.flameforge.essentialcraft5.network.S2CBuilderStatusPacket;
import com.flameforge.essentialcraft5.registry.ECBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 创世神之杖面板「蓝图台」（tools/plan/builder_upgrade.md §2.3，原型 {@code panel_proto/a/v5.html}）：单按魔杖键铺开、再单按或 Esc 收起，不暂停游戏。
 * 版面全部来自 {@link BuilderPanelLayout}（纯函数），这里只照着画和判点击；蓝图的投影和缩放在 {@link BuilderBlueprint}，算好的格子图按
 * 「预览对象、进度、混铺表、区域大小」缓存，这几样不变就不重算。
 * <p>
 * 所有改动都发 {@link C2SBuilderActionPacket}，服务端逐个复核（手里还是魔杖、没在施工……），这里的置灰只是提示。
 * 施工中（做、暂停、排队、撤销、测量）除了「撤销上一次」和「停下」都置灰，顶栏写「先停下才能改」。
 * 面板开着时按住魔杖键滚轮照旧调尺寸、按住魔杖键右键照旧撤销（原版的按键事件这时到不了 {@link BuilderInput}，这里接过来走同一段）。
 */
public class BuilderPanelScreen extends Screen {

    private static final String P = "gui.essentialcraft5.builder_panel.";

    // 原型配色（和研究手册 CodexTheme 同一套，补了几种面板专用的）
    private static final int LINE_DIM = 0xFF4A3A70;
    private static final int LINE_OFF = 0xFF332C40;
    private static final int SEL = 0xFF3A1840;
    private static final int SEL_HI = 0xFF4A2052;
    private static final int OFF = 0xFF6E6680;
    private static final int WARN = 0xFFFF6B6B;
    private static final int WAIT = 0xFFFFD27A;
    private static final int STATUS = 0xFFC9A6FF;
    private static final int INK = 0xFF120C1C;
    private static final int GRID = 0xFF1B1329;
    private static final int RULE = 0xFF3A3050;
    private static final int BTN = 0xFF241F2E;
    private static final int BTN_HI = 0xFF342D42;
    private static final int BTN_OFF = 0xFF1A1720;
    private static final int WELL_D = 0xFF0E0C12;
    private static final int WELL_L = 0xFF2A2633;
    private static final int CUT = 0xFF4A2A34;
    private static final int CUT_IN = 0xFF1A0F16;
    private static final int CUT_DONE = 0xFFA04A4A;
    private static final int OUT = 0xC00B0910;

    /** 单按：按下到松开不超过这么多刻（同 BuilderInput） */
    private static final int TAP_MAX_TICKS = 10;
    /** 面板清单多久要一次（服务端另有 10 刻的限频） */
    private static final int SYNC_TICKS = 20;
    /** 混铺表、丢弃清单改完停这么多刻才发（连点加减号不一下一包） */
    private static final int SEND_DELAY = 8;
    /** 发出去以后本地这份最多再留多久（等魔杖 NBT 同步回来） */
    private static final int LOCAL_HOLD = 40;

    /** 上次开到哪一页（再开时停在那一页） */
    private static BuilderPanelLayout.Page lastPage = BuilderPanelLayout.Page.RANGE;

    private final InteractionHand hand;
    private BuilderPanelLayout.Page page = lastPage;
    @Nullable
    private BuilderPanelLayout layout;
    @Nullable
    private BuilderPanelLayout.Inputs layoutInputs;
    private int ticks;
    private int lastSync = -SYNC_TICKS;
    // 魔杖键：面板开着时按下、松开算一次单按
    private boolean keyArmed;
    private boolean keyUsed;
    private int keyDownAt;
    // 滚动
    private int listScroll;
    private int discardScroll;
    /** 混铺「加一种」：左边列表点一下是加进表，不是改选定方块 */
    private boolean mixAdd;
    // 本地还没发、或刚发出去的混铺表和丢弃清单
    @Nullable
    private List<MixPick> localMix;
    private int mixChangedAt = -1;
    private int mixSentAt = -1;
    @Nullable
    private List<ResourceLocation> localDiscard;
    private int discardChangedAt = -1;
    private int discardSentAt = -1;
    private List<Item> discardCandidates = List.of();
    // 蓝图缓存
    @Nullable
    private BlueprintKey blueprintKey;
    @Nullable
    private BuilderBlueprint.Grid blueprintGrid;
    @Nullable
    private BuilderBlueprint.Fit blueprintFit;
    private boolean blueprintSketch;

    /** 混铺表里的一项（本地编辑用） */
    private record MixPick(ResourceLocation block, int weight) {
    }

    /** 蓝图什么时候要重算 */
    private record BlueprintKey(@Nullable Object preview, int done, int state, @Nullable List<MixPick> mix, int x, int y, int w, int h,
                                int undoShape, int undoW, int undoL, int undoH) {
    }

    public BuilderPanelScreen(InteractionHand hand) {
        super(Component.translatable(P + "title"));
        this.hand = hand;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        layout = null;
        sync();
    }

    // ------------------------------------------------------------------
    // 读状态
    // ------------------------------------------------------------------

    private ItemStack wand() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? ItemStack.EMPTY : player.getItemInHand(hand);
    }

    private static boolean working() {
        return BuilderClientStatus.working();
    }

    private static int state() {
        S2CBuilderStatusPacket status = BuilderClientStatus.latest();
        return status == null ? S2CBuilderStatusPacket.IDLE : status.state();
    }

    private List<BuilderShape> shapes(BuilderTier tier) {
        List<BuilderShape> out = new ArrayList<>();
        for (BuilderShape shape : BuilderShape.values()) {
            if (tier.allows(shape)) {
                out.add(shape);
            }
        }
        return out;
    }

    private List<BuilderMode> modes(ItemStack wand, BuilderTier tier) {
        List<BuilderMode> out = new ArrayList<>();
        if (wand.getItem() instanceof PavingWandItem) {
            out.add(BuilderMode.FILL);
            return out;
        }
        for (BuilderMode mode : BuilderMode.values()) {
            if (tier.allows(mode)) {
                out.add(mode);
            }
        }
        return out;
    }

    /** 尺寸那行两组加减号：每组的标签键和是不是「加潜行」那一维（同按住魔杖键滚轮 / 潜行滚轮） */
    private static String[] sizeLabels(BuilderShape shape) {
        return switch (shape) {
            case LINE -> new String[] {"width", "length"};
            case WALL -> new String[] {"width", "height"};
            default -> new String[] {"side", "length"};
        };
    }

    private static boolean sizeSneak(BuilderShape shape, int group) {
        return switch (shape) {
            case LINE -> group == 0;
            default -> group == 1;
        };
    }

    // ------------------------------------------------------------------
    // 每刻
    // ------------------------------------------------------------------

    @Override
    public void tick() {
        ticks++;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !player.isAlive() || player.isSpectator() || BuilderMode.of(player.getItemInHand(hand)) == null) {
            onClose();
            return;
        }
        if (ticks - lastSync >= SYNC_TICKS) {
            sync();
        }
        if (localMix != null && mixChangedAt >= 0 && ticks - mixChangedAt >= SEND_DELAY) {
            flushMix();
        }
        if (localMix != null && mixSentAt >= 0 && (ticks - mixSentAt > LOCAL_HOLD || localMix.equals(wandMix(player.getItemInHand(hand))))) {
            localMix = null;
            mixSentAt = -1;
        }
        if (localDiscard != null && discardChangedAt >= 0 && ticks - discardChangedAt >= SEND_DELAY) {
            flushDiscard();
        }
        if (localDiscard != null && discardSentAt >= 0
                && (ticks - discardSentAt > LOCAL_HOLD || localDiscard.equals(wandDiscard(player.getItemInHand(hand))))) {
            localDiscard = null;
            discardSentAt = -1;
        }
        discardCandidates = computeDiscardCandidates(player.getItemInHand(hand));
        if (working()) {
            mixAdd = false;
        }
    }

    private void sync() {
        lastSync = ticks;
        BuilderInput.send(C2SBuilderActionPacket.PANEL_SYNC, hand, 0, 0);
    }

    @Override
    public void removed() {
        if (localMix != null && mixChangedAt >= 0) {
            flushMix();
        }
        if (localDiscard != null && discardChangedAt >= 0) {
            flushDiscard();
        }
        super.removed();
    }

    @Override
    public void onClose() {
        lastPage = page;
        super.onClose();
    }

    // ------------------------------------------------------------------
    // 版面
    // ------------------------------------------------------------------

    private BuilderPanelLayout layout() {
        ItemStack wand = wand();
        BuilderTier tier = BuilderTier.of(wand);
        int sizeLabelW = 0;
        for (String key : new String[] {"side", "length", "width", "height"}) {
            sizeLabelW = Math.max(sizeLabelW, font.width(Component.translatable(P + "size." + key)));
        }
        int tabW = 0;
        for (String key : new String[] {"range", "block", "modules"}) {
            tabW = Math.max(tabW, font.width(Component.translatable(P + "tab." + key)));
        }
        int modeW = 0;
        for (BuilderMode mode : BuilderMode.values()) {
            modeW = Math.max(modeW, font.width(Component.translatable("message.essentialcraft5.builder.mode." + mode.id())));
        }
        BuilderPanelLayout.Inputs in = new BuilderPanelLayout.Inputs(width, height, page, shapes(tier).size(), modes(wand, tier).size(),
                tier.moduleSlots(), working(), font.width(titleText(wand, tier)), font.width("MRU"), font.width(mruText()), tabW, modeW,
                font.width(Component.translatable(P + "undo")), font.width(Component.translatable(P + "stop")), sizeLabelW,
                discardCandidates.size());
        if (layout == null || !in.equals(layoutInputs)) {
            layoutInputs = in;
            layout = BuilderPanelLayout.compute(in);
        }
        return layout;
    }

    private Component titleText(ItemStack wand, BuilderTier tier) {
        return Component.empty()
                .append(Component.translatable(P + "title").withStyle(s -> s.withColor(CodexTheme.GLOW & 0xFFFFFF)))
                .append(Component.literal("  "))
                .append(wand.getHoverName().copy().withStyle(s -> s.withColor(CodexTheme.PEARL & 0xFFFFFF)))
                .append(Component.literal("  "))
                .append(Component.translatable(P + "tier." + tier.level()).withStyle(s -> s.withColor(CodexTheme.FAINT & 0xFFFFFF)));
    }

    private String mruText() {
        S2CBuilderStatusPacket status = BuilderClientStatus.latest();
        if (status == null) {
            return "…";
        }
        return status.mru() < 0L ? Component.translatable("hud.essentialcraft5.builder.infinite").getString() : BuilderHud.number(status.mru());
    }

    // ------------------------------------------------------------------
    // 画
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        BuilderPanelLayout l = layout();
        if (l.tooSmall) {
            BuilderPanelLayout.Rect f = l.frame;
            g.fill(f.x(), f.y(), f.right(), f.bottom(), CodexTheme.PLATE);
            frame(g, f, CodexTheme.EDGE);
            drawWrapped(g, Component.translatable(P + "too_small"), l.tooSmallNote, CodexTheme.PEARL, true);
            return;
        }
        ItemStack wand = wand();
        BuilderTier tier = BuilderTier.of(wand);
        boolean locked = working();
        List<Component> tooltip = new ArrayList<>();
        drawFrame(g, l);
        drawTitle(g, l, wand, tier, locked, mouseX, mouseY, tooltip);
        drawLeft(g, l, wand, locked, mouseX, mouseY, tooltip);
        drawTabs(g, l, mouseX, mouseY);
        switch (page) {
            case RANGE -> drawRange(g, l, wand, tier, locked, mouseX, mouseY, tooltip);
            case BLOCK -> drawBlock(g, l, wand, locked, mouseX, mouseY, tooltip);
            case MODULES -> drawModules(g, l, wand, tier, locked, mouseX, mouseY, tooltip);
            default -> {
            }
        }
        drawBar(g, l, locked, mouseX, mouseY, tooltip);
        if (!tooltip.isEmpty()) {
            g.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    private void drawFrame(GuiGraphics g, BuilderPanelLayout l) {
        BuilderPanelLayout.Rect f = l.frame;
        g.fill(f.x() - 1, f.y() - 1, f.right() + 1, f.bottom() + 1, OUT);
        g.fill(f.x(), f.y(), f.right(), f.bottom(), CodexTheme.PLATE);
        frame(g, f, CodexTheme.EDGE);
        g.hLine(f.x() + 1, f.right() - 2, f.y() + 1, CodexTheme.PLATE_HI);
        // 四角的铆钉
        int[][] studs = {{f.x() + 3, f.y() + 3}, {f.right() - 5, f.y() + 3}, {f.x() + 3, f.bottom() - 5}, {f.right() - 5, f.bottom() - 5}};
        for (int[] s : studs) {
            g.fill(s[0], s[1], s[0] + 2, s[1] + 2, LINE_DIM);
        }
        g.hLine(f.x() + 6, f.right() - 7, l.rule1Y, RULE);
        g.hLine(f.x() + 6, f.right() - 7, l.rule2Y, RULE);
    }

    private void drawTitle(GuiGraphics g, BuilderPanelLayout l, ItemStack wand, BuilderTier tier, boolean locked, int mx, int my,
                           List<Component> tooltip) {
        drawFitted(g, titleText(wand, tier), l.title, CodexTheme.PEARL, false);
        if (locked && l.lockNote != null) {
            drawFitted(g, Component.translatable(P + "locked"), l.lockNote, WAIT, true);
        }
        drawFitted(g, Component.literal("MRU"), l.mruLabel, CodexTheme.FAINT, false);
        BuilderPanelLayout.Rect bar = l.mruBar;
        g.fill(bar.x() - 1, bar.y() - 1, bar.right() + 1, bar.bottom() + 1, LINE_DIM);
        g.fill(bar.x(), bar.y(), bar.right(), bar.bottom(), WELL_D);
        S2CBuilderStatusPacket status = BuilderClientStatus.latest();
        if (status != null) {
            double ratio = status.mru() < 0L ? 1D : Math.min(1D, status.mru() / (double) Math.max(1, BuilderTier.stats(wand).mruCapacity()));
            int fill = (int) Math.floor(bar.w() * ratio);
            if (fill > 0) {
                g.fillGradient(bar.x(), bar.y(), bar.x() + fill, bar.bottom(), CodexTheme.MRU_MAGENTA, CodexTheme.MRU_BLUE);
            }
        }
        drawFitted(g, Component.literal(mruText()), l.mruText, CodexTheme.PEARL, false);
        boolean hover = l.close.contains(mx, my);
        mini(g, l.close, hover, true);
        glyph(g, l.close, GLYPH_X, hover ? CodexTheme.PEARL : CodexTheme.MIST);
        if (hover) {
            tooltip.add(Component.translatable(P + "close"));
        }
    }

    // ---- 左栏：蓝图 + 三行 ----

    private void drawLeft(GuiGraphics g, BuilderPanelLayout l, ItemStack wand, boolean locked, int mx, int my, List<Component> tooltip) {
        BuilderPanelLayout.Rect left = l.left;
        well(g, left);
        BuilderPanelLayout.Rect bp = l.blueprint;
        g.fill(bp.x(), bp.y(), bp.right(), bp.bottom(), INK);
        for (int x = bp.x() + 8; x < bp.right(); x += 8) {
            g.vLine(x, bp.y() + BLUEPRINT_TOP - 1, bp.bottom(), GRID);
        }
        for (int y = bp.y() + BLUEPRINT_TOP + 7; y < bp.bottom(); y += 8) {
            g.hLine(bp.x(), bp.right() - 1, y, GRID);
        }
        BuilderGhost.Preview preview = blueprintPreview();
        drawBlueprint(g, l, wand, preview);
        int status = state();
        BuilderPanelLayout.Rect line0 = l.infoLines.get(0);
        BuilderPanelLayout.Rect line1 = l.infoLines.get(1);
        BuilderPanelLayout.Rect line2 = l.infoLines.get(2);
        BuilderShape shape = BuilderSelection.shape(wand);
        Component shapeName = Component.translatable("message.essentialcraft5.builder.shape." + shape.id());
        if (locked) {
            S2CBuilderStatusPacket packet = BuilderClientStatus.latest();
            int total = packet == null ? 0 : packet.total();
            Component right = Component.translatable(P + "total", BuilderHud.number(total));
            splitRow(g, line0, shapeName, STATUS, right, CodexTheme.MIST);
            boolean undoing = status == S2CBuilderStatusPacket.UNDOING;
            // 模式和混铺表都按蓝图那份预览取（施工中换了模式，蓝图照旧画开工时那份）
            BuilderMode mode = preview != null && !preview.unset() ? preview.mode() : BuilderMode.of(wand);
            boolean clearing = !undoing && mode != null && !mode.bottomUp();
            // 图例的色块和蓝图上画的一样：放置按方块颜色（混铺取表里第一种），做完的是本色、没做的压暗；
            // 拆除：拆完的是亮红，待拆的是红框暗芯；撤销按示意外框的颜色
            int rgb = undoing ? CodexTheme.GLOW & 0xFFFFFF : legendColour(wand, mode, preview);
            int todo = blend(rgb, INK, 0.62F);
            String[] keys = undoing ? new String[] {"legend_undo_todo", "legend_undo_done"}
                    : clearing ? new String[] {"legend_cleared", "legend_to_clear"} : new String[] {"legend_done", "legend_todo"};
            int[] swatches = undoing ? new int[] {todo, CUT_DONE} : clearing ? new int[] {CUT_DONE, CUT} : new int[] {0xFF000000 | rgb, todo};
            int[] inners = clearing ? new int[] {0, CUT_IN} : new int[] {0, 0};
            legend(g, line1, swatches, inners, new Component[] {Component.translatable(P + keys[0]), Component.translatable(P + keys[1])});
            drawFitted(g, Component.translatable(P + "block", blockName(wand)), line2, CodexTheme.MIST, false);
            return;
        }
        // 第一行：形状 / 要放、要拆
        Component right;
        if (preview == null) {
            right = Component.empty();
        } else if (preview.place() > 0 || preview.breaks() == 0) {
            S2CBuilderStatusPacket packet = BuilderClientStatus.latest();
            long have = packet == null ? Long.MAX_VALUE : packet.have() + packet.haveInContainers();
            BuilderMix.Table mix = BuilderMix.forWand(wand, BuilderMode.of(wand));
            boolean creative = Minecraft.getInstance().player != null && Minecraft.getInstance().player.getAbilities().instabuild;
            boolean shortage = !creative && preview.place() > 0 && BuilderHud.materialShort(mix, preview, have);
            right = Component.translatable(P + "need_place", BuilderHud.number(preview.place()))
                    .withStyle(s -> s.withColor((shortage ? WARN : CodexTheme.MIST) & 0xFFFFFF));
        } else {
            right = Component.translatable(P + "need_break", BuilderHud.number(preview.breaks()));
        }
        splitRow(g, line0, shapeName, STATUS, right, CodexTheme.MIST);
        // 第二行：尺寸
        if (shape.resizable()) {
            int[] size = BuilderInput.displaySize(hand, wand, shape);
            drawFitted(g, Component.translatable(P + "size", size[0], size[1]), l.sizeText, CodexTheme.PEARL, false);
            String[] labels = sizeLabels(shape);
            for (int k = 0; k < 2; k++) {
                if (l.sizeLabels.get(k).w() > 0) {
                    drawFitted(g, Component.translatable(P + "size." + labels[k]), l.sizeLabels.get(k), CodexTheme.FAINT, false);
                }
                BuilderPanelLayout.Rect minus = l.sizeMinus.get(k);
                BuilderPanelLayout.Rect plus = l.sizePlus.get(k);
                boolean hm = minus.contains(mx, my);
                boolean hp = plus.contains(mx, my);
                mini(g, minus, hm, true);
                glyph(g, minus, GLYPH_MINUS, hm ? CodexTheme.PEARL : CodexTheme.MIST);
                mini(g, plus, hp, true);
                glyph(g, plus, GLYPH_PLUS, hp ? CodexTheme.PEARL : CodexTheme.MIST);
            }
        } else {
            drawFitted(g, Component.translatable(P + "no_size"), line1, CodexTheme.FAINT, false);
        }
        // 第三行：MRU
        S2CBuilderStatusPacket packet = BuilderClientStatus.latest();
        long need = preview == null ? 0L : preview.needMru();
        boolean shortMru = packet != null && packet.mru() >= 0L && packet.mru() < need;
        Component available = Component.translatable(P + "available", mruText());
        Component needText = Component.translatable(P + "need_mru", BuilderHud.number(need));
        if (font.width(needText) + font.width(available) > line2.w() - 4) {
            // 窄版两段放不下：只写这一单要多少，可用多少顶栏已经写着（并排时「需要」那段会被省略到看不见数）
            drawFitted(g, needText, line2, shortMru ? WARN : CodexTheme.MIST, false);
        } else {
            splitRow(g, line2, needText, shortMru ? WARN : CodexTheme.MIST, available, CodexTheme.FAINT);
        }
    }

    /** 蓝图上面那条字的高度（角上写「前方」「示意」） */
    private static final int BLUEPRINT_TOP = 11;

    /** 蓝图按哪份预览画：施工中用开工那一刻记下的，否则用准星下现在这份 */
    @Nullable
    private static BuilderGhost.Preview blueprintPreview() {
        if (working()) {
            return BuilderClientStatus.jobPreview();
        }
        return BuilderGhost.preview();
    }

    private void drawBlueprint(GuiGraphics g, BuilderPanelLayout l, ItemStack wand, @Nullable BuilderGhost.Preview preview) {
        BuilderPanelLayout.Rect bp = l.blueprint;
        int ax = bp.x() + 1;
        int ay = bp.y() + BLUEPRINT_TOP;
        int aw = bp.w() - 2;
        int ah = bp.h() - BLUEPRINT_TOP - 1;
        S2CBuilderStatusPacket status = BuilderClientStatus.latest();
        int state = state();
        // 测量中的 done 是已连上的格数、排队中还没开工：都不按「前 done 格做完了」点亮，整片画成待放
        int done = status == null || !working() ? -1
                : state == S2CBuilderStatusPacket.SURVEYING || state == S2CBuilderStatusPacket.QUEUED ? 0 : status.done();
        S2CBuilderPanelPacket panel = BuilderClientStatus.panel();
        boolean undoing = state == S2CBuilderStatusPacket.UNDOING;
        BlueprintKey key = new BlueprintKey(undoing ? null : preview, done, state, mixPicks(wand), ax, ay, aw, ah,
                panel == null ? -1 : panel.undoShape(), panel == null ? 0 : panel.undoWidth(), panel == null ? 0 : panel.undoLength(),
                panel == null ? 0 : panel.undoHeight());
        if (!key.equals(blueprintKey)) {
            blueprintKey = key;
            buildBlueprint(wand, undoing ? null : preview, done, undoing, status, panel, ax, ay, aw, ah);
        }
        // 角上的字
        Component corner;
        if (blueprintGrid != null && blueprintGrid.frontView()) {
            corner = Component.translatable(P + "bp.front_view");
        } else {
            corner = Component.translatable(P + "bp.ahead");
        }
        BuilderPanelLayout.Rect top = new BuilderPanelLayout.Rect(bp.x() + 3, bp.y() + 1, bp.w() - 6, 9);
        Component note = null;
        if (blueprintSketch) {
            note = Component.translatable(P + "bp.sketch");
        } else if (blueprintFit != null && blueprintFit.k() > 1) {
            note = Component.translatable(P + "bp.scale", blueprintFit.k(), blueprintFit.k());
        }
        if (note != null) {
            int nw = Math.min(font.width(note), top.w() / 2);
            drawFitted(g, note, new BuilderPanelLayout.Rect(top.right() - nw, top.y(), nw, top.h()), CodexTheme.FAINT, false);
            drawFitted(g, corner, new BuilderPanelLayout.Rect(top.x(), top.y(), top.w() - nw - 4, top.h()), CodexTheme.FAINT, false);
        } else {
            drawFitted(g, corner, top, CodexTheme.FAINT, false);
        }
        BuilderBlueprint.Grid grid = blueprintGrid;
        BuilderBlueprint.Fit fit = blueprintFit;
        if (grid == null || fit == null || fit.cols() == 0) {
            Component empty = emptyBlueprintText(preview);
            drawWrapped(g, empty, new BuilderPanelLayout.Rect(ax + 4, ay, aw - 8, ah), CodexTheme.FAINT, true);
            return;
        }
        int size = fit.cell() - fit.gap();
        g.drawManaged(() -> {
            for (int r = 0; r < grid.rows(); r++) {
                for (int c = 0; c < grid.cols(); c++) {
                    byte kind = grid.kind(c, r);
                    if (kind == BuilderBlueprint.EMPTY) {
                        continue;
                    }
                    int x = fit.x() + c * fit.cell();
                    int y = fit.y() + r * fit.cell();
                    int rgb = grid.colour(c, r);
                    switch (kind) {
                        case BuilderBlueprint.PLACE -> g.fill(x, y, x + size, y + size, 0xFF000000 | rgb);
                        case BuilderBlueprint.TODO -> g.fill(x, y, x + size, y + size, blend(rgb, INK, 0.62F));
                        case BuilderBlueprint.SHORT -> {
                            g.fill(x, y, x + size, y + size, blend(rgb, INK, 0.55F));
                            if (size >= 4) {
                                g.hLine(x, x + size - 1, y, WARN);
                            }
                        }
                        case BuilderBlueprint.BLOCKED -> g.fill(x, y, x + size, y + size, LINE_OFF);
                        case BuilderBlueprint.BREAK -> {
                            g.fill(x, y, x + size, y + size, CUT);
                            if (size >= 4) {
                                g.fill(x + 1, y + 1, x + size - 1, y + size - 1, CUT_IN);
                            }
                        }
                        case BuilderBlueprint.BROKEN -> g.fill(x, y, x + size, y + size, CUT_DONE);
                        default -> {
                        }
                    }
                }
            }
        });
    }

    private Component emptyBlueprintText(@Nullable BuilderGhost.Preview preview) {
        if (preview != null && preview.unset()) {
            return preview.note() == BuilderGhost.NOTE_COVERED ? Component.translatable(P + "bp.covered") : Component.translatable(P + "bp.unset");
        }
        return Component.translatable(P + "bp.none");
    }

    /** 重算格子图（只在 {@link BlueprintKey} 变了时调） */
    private void buildBlueprint(ItemStack wand, @Nullable BuilderGhost.Preview preview, int done, boolean undoing,
                                @Nullable S2CBuilderStatusPacket status, @Nullable S2CBuilderPanelPacket panel, int ax, int ay, int aw, int ah) {
        blueprintSketch = false;
        BuilderBlueprint.Grid grid;
        if (undoing) {
            // 撤销没有逐格数据：按撤销栈顶那条的外框画示意，进度从前往后填
            int w = panel == null ? 1 : Math.max(1, panel.undoWidth());
            int len = panel == null ? 1 : Math.max(1, panel.undoLength());
            grid = BuilderBlueprint.box(w, len, BuilderBlueprint.TODO, CodexTheme.GLOW & 0xFFFFFF);
            int total = status == null ? 0 : Math.max(1, status.total());
            int filled = status == null ? 0 : (int) Math.min(grid.kinds().length, (long) grid.kinds().length * status.done() / total);
            for (int i = 0; i < filled; i++) {
                grid.kinds()[i] = BuilderBlueprint.BROKEN;
            }
            blueprintSketch = true;
        } else if (preview == null || preview.unset()) {
            grid = null;
        } else if (preview.cells() == null) {
            // 两点：没有逐格数据，按外框画示意
            if (preview.tooBig() || preview.width() <= 0) {
                grid = null;
            } else {
                byte kind = preview.mode().bottomUp() ? BuilderBlueprint.PLACE : BuilderBlueprint.BREAK;
                grid = BuilderBlueprint.box(preview.width(), preview.length(), kind, colourOf(preview.block()));
                blueprintSketch = true;
            }
        } else {
            grid = projectPreview(wand, preview, done);
        }
        if (grid == null || grid.cols() == 0) {
            blueprintGrid = null;
            blueprintFit = null;
            return;
        }
        BuilderBlueprint.Fit fit = BuilderBlueprint.fit(grid.cols(), grid.rows(), ax, ay, aw, ah);
        blueprintGrid = BuilderBlueprint.downsample(grid, fit.k());
        blueprintFit = fit;
    }

    private BuilderBlueprint.Grid projectPreview(ItemStack wand, BuilderGhost.Preview preview, int done) {
        long[] cells = preview.cells();
        byte[] kinds = preview.kinds();
        BlockState[] states = preview.states();
        int n = cells.length;
        long[] pos = new long[n];
        byte[] outKinds = new byte[n];
        int[] colours = new int[n];
        // 混铺：按表和锚点逐格上色（和施工用同一个函数）；沿面预览截断时外框和实际施工的不一样，逐格上色会错位，改用选定方块的颜色、角上写「示意」
        BuilderMix.Table mix = BuilderMix.forWand(wand, preview.mode());
        // 只认沿面预览被截断（外框和施工时不同）；世界里虚影只画外框（outlineOnly）时格子照样全在、外框没变，照表上色
        boolean capped = preview.note() == BuilderGhost.NOTE_PREVIEW_CAPPED;
        boolean byTable = mix != null && !capped && preview.anchor() != null && preview.box() != null;
        long seed = byTable ? BuilderMix.seedFor(mix, preview.anchor()) : 0L;
        int[] mixColours = byTable ? mixColours(mix.entries().stream().map(BuilderMix.Entry::block).toList()) : null;
        if (mix != null && !byTable) {
            blueprintSketch = true;
        }
        int fallback = colourOf(preview.block());
        int count = 0;
        int actioned = 0;
        int placeCells = 0;
        for (int i = 0; i < n; i++) {
            byte kind = kinds[i];
            if ((kind == BuilderGhost.OK || kind == BuilderGhost.SHORT) && states[i] == null) {
                continue;   // 数组比实际画的格子长，没填的项
            }
            byte out = switch (kind) {
                case BuilderGhost.OK -> BuilderBlueprint.PLACE;
                case BuilderGhost.SHORT -> BuilderBlueprint.SHORT;
                case BuilderGhost.BLOCKED -> BuilderBlueprint.BLOCKED;
                default -> BuilderBlueprint.BREAK;
            };
            if (done >= 0 && out != BuilderBlueprint.BLOCKED) {
                // 施工中：按施工顺序前 done 格算做完了
                boolean finished = actioned < done;
                actioned++;
                out = out == BuilderBlueprint.BREAK ? (finished ? BuilderBlueprint.BROKEN : BuilderBlueprint.BREAK)
                        : finished ? BuilderBlueprint.PLACE : BuilderBlueprint.TODO;
            }
            int colour = fallback;
            if (out != BuilderBlueprint.BREAK && out != BuilderBlueprint.BROKEN && out != BuilderBlueprint.BLOCKED) {
                if (byTable) {
                    colour = mixColours[BuilderMix.kindAt(mix, seed, preview.box(), net.minecraft.core.BlockPos.of(cells[i]))];
                } else if (mix == null && states[i] != null) {
                    colour = colourOf(states[i].getBlock());
                }
            }
            pos[count] = cells[i];
            outKinds[count] = out;
            colours[count] = colour;
            count++;
            if (kind == BuilderGhost.OK || kind == BuilderGhost.SHORT) {
                placeCells++;
            }
        }
        if (placeCells < preview.place() && preview.box() != null) {
            // 预览只逐格算了前一部分「要放」的格（BuilderGhost.DETAIL_LIMIT），投出来是半片：改画外框示意，角上写「示意」
            blueprintSketch = true;
            return BuilderBlueprint.box(preview.box().getXSpan(), preview.box().getZSpan(), done >= 0 ? BuilderBlueprint.TODO : BuilderBlueprint.PLACE,
                    fallback);
        }
        return BuilderBlueprint.project(pos, outKinds, colours, count, preview.facing());
    }

    /** 方块在蓝图上的颜色：地图颜色；没有地图颜色（玻璃这类）用面板的浅紫 */
    private static int colourOf(@Nullable Block block) {
        if (block == null) {
            return CodexTheme.GLOW & 0xFFFFFF;
        }
        int col = block.defaultMapColor().col;
        return col == 0 ? CodexTheme.GLOW & 0xFFFFFF : col;
    }

    /**
     * 施工中图例的方块颜色，和蓝图的取色走同一条路：蓝图按混铺表逐格上色时取表里第一种；
     * 两点外框示意、沿面预览截断这些画示意的情形取预览的方块
     */
    private static int legendColour(ItemStack wand, @Nullable BuilderMode mode, @Nullable BuilderGhost.Preview preview) {
        BuilderMix.Table mix = BuilderMix.forWand(wand, mode);
        boolean byTable = mix != null && (preview == null || preview.cells() != null
                && preview.note() != BuilderGhost.NOTE_PREVIEW_CAPPED && preview.anchor() != null && preview.box() != null);
        if (byTable) {
            return mixColours(mix.entries().stream().map(BuilderMix.Entry::block).toList())[0];
        }
        return colourOf(preview == null ? null : preview.block());
    }

    /** 混铺几种方块的颜色（按表里的顺序）：地图色相近的几种拉开明度（{@link BuilderBlueprint#distinctColours}），蓝图和比例条共用 */
    private static int[] mixColours(List<Block> blocks) {
        int[] base = new int[blocks.size()];
        for (int i = 0; i < base.length; i++) {
            base[i] = colourOf(blocks.get(i));
        }
        return BuilderBlueprint.distinctColours(base);
    }

    private Component blockName(ItemStack wand) {
        BuilderMix.Table mix = BuilderMix.forWand(wand, BuilderMode.of(wand));
        if (mix != null) {
            return Component.translatable(P + "mixed", mix.size());
        }
        ResourceLocation id = BuilderSelection.storedBlock(wand);
        Block block = id == null ? null : ForgeRegistries.BLOCKS.getValue(id);
        return block == null || id == null || !ForgeRegistries.BLOCKS.containsKey(id) ? Component.translatable(P + "no_block") : block.getName();
    }

    // ---- 页签 ----

    private void drawTabs(GuiGraphics g, BuilderPanelLayout l, int mx, int my) {
        String[] keys = {"range", "block", "modules"};
        for (int i = 0; i < 3; i++) {
            BuilderPanelLayout.Rect t = l.tabs.get(i);
            boolean selected = page.ordinal() == i;
            boolean hover = t.contains(mx, my);
            g.fill(t.x(), t.y(), t.right(), t.bottom(), selected ? SEL : hover ? BTN_HI : BTN);
            frame(g, t, selected ? CodexTheme.EDGE : LINE_DIM);
            if (selected) {
                g.hLine(t.x() + 1, t.right() - 2, t.y(), CodexTheme.FOCUS);
            }
            drawFitted(g, Component.translatable(P + "tab." + keys[i]), t, selected ? CodexTheme.PEARL : CodexTheme.MIST, true);
        }
        BuilderPanelLayout.Rect rule = l.tabRule;
        if (rule.w() > 0) {
            g.hLine(rule.x(), rule.right() - 1, rule.y(), LINE_DIM);
        }
    }

    // ---- 范围页 ----

    private void drawRange(GuiGraphics g, BuilderPanelLayout l, ItemStack wand, BuilderTier tier, boolean locked, int mx, int my,
                           List<Component> tooltip) {
        drawFitted(g, Component.translatable(P + "shapes"), l.shapeHead, CodexTheme.FAINT, false);
        List<BuilderShape> shapes = shapes(tier);
        BuilderShape current = BuilderSelection.shape(wand);
        BuilderShape tipShape = current;
        for (int i = 0; i < l.shapeButtons.size() && i < shapes.size(); i++) {
            BuilderPanelLayout.Rect b = l.shapeButtons.get(i);
            BuilderShape shape = shapes.get(i);
            boolean hover = b.contains(mx, my);
            boolean selected = shape == current;
            if (hover) {
                tipShape = shape;
            }
            button(g, b, selected, hover && !locked, locked);
            int colour = locked ? OFF : selected ? CodexTheme.PEARL : CodexTheme.MIST;
            int ix = BuilderPanelLayout.center(b.x(), b.w(), 11);
            shapeIcon(g, shape, ix, b.y() + 4, locked ? OFF : selected ? CodexTheme.FOCUS : CodexTheme.GLOW);
            drawFitted(g, Component.translatable("message.essentialcraft5.builder.shape." + shape.id()),
                    new BuilderPanelLayout.Rect(b.x() + 2, b.y() + 17, b.w() - 4, 9), colour, true);
            if (hover && locked) {
                tooltip.add(Component.translatable(P + "locked"));
            }
        }
        if (l.shapeTip != null) {
            drawWrapped(g, Component.translatable(P + "shape_tip." + tipShape.id()), l.shapeTip, CodexTheme.MIST, false);
        } else if (tipShape != current || l.shapeButtons.stream().anyMatch(b -> b.contains(mx, my))) {
            tooltip.add(Component.translatable(P + "shape_tip." + tipShape.id()));
        }
        drawFitted(g, Component.translatable(P + "modes"), l.modeHead, CodexTheme.FAINT, false);
        if (l.onlyFill != null) {
            well(g, l.onlyFill);
            drawFitted(g, Component.translatable(P + "only_fill"), inset(l.onlyFill, 4), CodexTheme.MIST, false);
            return;
        }
        List<BuilderMode> modes = modes(wand, tier);
        BuilderMode currentMode = BuilderMode.of(wand);
        for (int i = 0; i < l.modeRows.size() && i < modes.size(); i++) {
            BuilderPanelLayout.Rect r = l.modeRows.get(i);
            BuilderMode mode = modes.get(i);
            boolean hover = r.contains(mx, my);
            boolean selected = mode == currentMode;
            if (selected || hover && !locked) {
                g.fill(r.x(), r.y(), r.right(), r.bottom(), selected ? SEL : BTN_HI);
            }
            if (selected) {
                g.vLine(r.x(), r.y(), r.bottom() - 1, CodexTheme.FOCUS);
            }
            modeIcon(g, mode, r.x() + 4, r.y() + 2, locked ? OFF : selected ? CodexTheme.FOCUS : CodexTheme.GLOW);
            int labelX = r.x() + 4 + 9 + 4;
            int labelW = (i < l.modeDescs.size() ? l.modeDescs.get(i).x() - 8 : r.right() - 4) - labelX;
            drawFitted(g, Component.translatable("message.essentialcraft5.builder.mode." + mode.id()),
                    new BuilderPanelLayout.Rect(labelX, r.y(), Math.max(0, labelW), r.h()), locked ? OFF : selected ? CodexTheme.PEARL : CodexTheme.MIST,
                    false);
            if (i < l.modeDescs.size()) {
                drawFitted(g, Component.translatable(P + "mode_desc." + mode.id()), l.modeDescs.get(i), locked ? OFF : CodexTheme.FAINT, false);
            } else if (hover) {
                tooltip.add(Component.translatable(P + "mode_desc." + mode.id()));
            }
            if (hover && locked) {
                tooltip.add(Component.translatable(P + "locked"));
            }
        }
    }

    // ---- 方块页 ----

    /** 左边列表的一行 */
    private record Material(Item item, long body, long box) {
    }

    private List<Material> materials() {
        S2CBuilderPanelPacket panel = BuilderClientStatus.panel();
        if (panel == null) {
            return List.of();
        }
        List<Material> out = new ArrayList<>();
        for (S2CBuilderPanelPacket.Entry entry : panel.materials()) {
            Item item = ForgeRegistries.ITEMS.getValue(entry.item());
            if (item instanceof BlockItem) {
                out.add(new Material(item, entry.body(), entry.box()));
            }
        }
        return out;
    }

    private void drawBlock(GuiGraphics g, BuilderPanelLayout l, ItemStack wand, boolean locked, int mx, int my, List<Component> tooltip) {
        // 左列：从身上挑
        Component selected = Component.translatable(P + "selected", blockName(wand));
        Component head = Component.translatable(mixAdd ? P + "mix_pick" : P + "pick");
        if (mixAdd) {
            // 挑「加一种」时左边那句是操作说明，先保它
            int headW = Math.min(font.width(head), l.listHead.w() / 2);
            drawFitted(g, head, new BuilderPanelLayout.Rect(l.listHead.x(), l.listHead.y(), headW, l.listHead.h()), WAIT, false);
            int selW = l.listHead.w() - headW - 6;
            if (selW > 0) {
                int w = Math.min(font.width(selected), selW);
                drawFitted(g, selected, new BuilderPanelLayout.Rect(l.listHead.right() - w, l.listHead.y(), w, l.listHead.h()), CodexTheme.MIST, false);
            }
        } else {
            // 平时先保右边的「选定」：并排放不下时标题省略；连省略后的标题也挤不下时只写「选定」
            int full = Math.max(0, l.listHead.w() - 4);
            int lw = font.width(head);
            int rw = font.width(selected);
            if (lw + rw > full && rw > full - Math.min(lw, full / 3)) {
                drawFitted(g, selected, l.listHead, CodexTheme.MIST, false);
            } else {
                splitRow(g, l.listHead, head, CodexTheme.FAINT, selected, CodexTheme.MIST);
            }
        }
        well(g, l.listWell);
        BuilderPanelLayout.Rect view = l.listView;
        List<Material> list = materials();
        int rows = list.size();
        int maxScroll = Math.max(0, rows * BuilderPanelLayout.ROW - view.h());
        listScroll = Math.max(0, Math.min(listScroll, maxScroll));
        ResourceLocation stored = BuilderSelection.storedBlock(wand);
        Set<ResourceLocation> inMix = new LinkedHashSet<>();
        List<MixPick> picks = mixPicks(wand);
        if (picks != null) {
            for (MixPick p : picks) {
                inMix.add(p.block());
            }
        }
        if (list.isEmpty()) {
            S2CBuilderPanelPacket panel = BuilderClientStatus.panel();
            drawWrapped(g, Component.translatable(panel == null ? P + "loading" : P + "list_empty"), inset(view, 4), CodexTheme.FAINT, true);
        } else {
            int rowW = view.w() - (maxScroll > 0 ? BuilderPanelLayout.SCROLL_ROOM : 0);
            g.enableScissor(view.x(), view.y(), view.right(), view.bottom());
            for (int i = 0; i < rows; i++) {
                int y = view.y() + i * BuilderPanelLayout.ROW - listScroll;
                if (y + BuilderPanelLayout.ROW <= view.y() || y >= view.bottom()) {
                    continue;
                }
                Material m = list.get(i);
                BuilderPanelLayout.Rect row = new BuilderPanelLayout.Rect(view.x(), y, rowW, BuilderPanelLayout.ROW);
                ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(((BlockItem) m.item()).getBlock());
                boolean isSelected = !mixAdd && blockId != null && blockId.equals(stored);
                boolean disabled = locked || mixAdd && (inMix.contains(blockId) || BuilderMix.usableBlock(blockId) == null);
                boolean hover = row.contains(mx, my) && view.contains(mx, my);
                if (isSelected || hover && !disabled) {
                    g.fill(row.x(), row.y(), row.right(), row.bottom() - 1, isSelected ? SEL : BTN_HI);
                }
                g.renderItem(new ItemStack(m.item()), row.x() + 2, row.y() + 3);
                long total = m.body() + m.box();
                Component count = Component.literal(BuilderHud.number(total));
                int cw = font.width(count);
                int nameW = row.w() - 22 - cw - 6;
                drawFitted(g, m.item().getDescription(), new BuilderPanelLayout.Rect(row.x() + 21, row.y() + 2, Math.max(0, nameW), 9),
                        disabled ? OFF : CodexTheme.PEARL, false);
                g.drawString(font, count, row.right() - 3 - cw, row.y() + 2, (disabled ? OFF : CodexTheme.MIST), false);
                Component detail = m.box() > 0 ? Component.translatable(P + "stock", BuilderHud.number(m.body()), BuilderHud.number(m.box()))
                        : Component.translatable(P + "stock_body", BuilderHud.number(m.body()));
                drawFitted(g, detail, new BuilderPanelLayout.Rect(row.x() + 21, row.y() + 12, row.w() - 24, 9), disabled ? OFF : CodexTheme.FAINT,
                        false);
                if (hover && locked) {
                    tooltip.add(Component.translatable(P + "locked"));
                }
            }
            g.disableScissor();
            if (maxScroll > 0) {
                scrollbar(g, view, listScroll, maxScroll, rows * BuilderPanelLayout.ROW);
            }
            S2CBuilderPanelPacket panel = BuilderClientStatus.panel();
            if (panel != null && view.contains(mx, my) && my > view.bottom() - 10 && (panel.truncated() || !panel.containersSeen())) {
                tooltip.add(Component.translatable(panel.truncated() ? P + "list_truncated" : P + "containers_skipped",
                        S2CBuilderPanelPacket.MAX_MATERIALS));
            }
        }
        g.vLine(l.columnRule.x(), l.columnRule.y(), l.columnRule.bottom() - 1, RULE);
        // 右列：混铺
        BuilderPanelLayout.Rect mixHead = l.mixHead;
        if (!BuilderModules.has(wand, BuilderModules.Kind.MIX)) {
            drawFitted(g, Component.translatable(P + "mix"), mixHead, CodexTheme.FAINT, false);
            // 装了但停用的和没装的分开说（停用的要去「模块」页启用，不用再装）
            String why = BuilderModules.slotted(wand).contains(BuilderModules.Kind.MIX) ? "mix_disabled" : "mix_none";
            drawWrapped(g, Component.translatable(P + why), new BuilderPanelLayout.Rect(mixHead.x(), l.mixBar.y(), mixHead.w(),
                    l.content.bottom() - l.mixBar.y()), CodexTheme.FAINT, false);
            return;
        }
        List<MixPick> mix = picks == null ? List.of() : picks;
        int total = 0;
        for (MixPick p : mix) {
            total += p.weight();
        }
        StringBuilder ratio = new StringBuilder();
        for (MixPick p : mix) {
            if (!ratio.isEmpty()) {
                ratio.append(':');
            }
            ratio.append(p.weight());
        }
        Component mixLabel = Component.translatable(P + "mix");
        int lw = font.width(mixLabel);
        drawFitted(g, mixLabel, new BuilderPanelLayout.Rect(mixHead.x(), mixHead.y(), lw, mixHead.h()), CodexTheme.FAINT, false);
        if (!ratio.isEmpty()) {
            int rw = Math.min(font.width(ratio.toString()), mixHead.w() - lw - 4);
            drawFitted(g, Component.literal(ratio.toString()), new BuilderPanelLayout.Rect(mixHead.right() - rw, mixHead.y(), rw, mixHead.h()),
                    CodexTheme.MIST, false);
        }
        BuilderPanelLayout.Rect bar = l.mixBar;
        g.fill(bar.x(), bar.y(), bar.right(), bar.bottom(), WELL_D);
        if (total > 0) {
            int[] colours = mixColours(mix.stream().map(p -> ForgeRegistries.BLOCKS.getValue(p.block())).toList());
            int x = bar.x();
            int used = 0;
            for (int i = 0; i < mix.size(); i++) {
                used += mix.get(i).weight();
                int end = i == mix.size() - 1 ? bar.right() : bar.x() + bar.w() * used / total;
                g.fill(x, bar.y(), end, bar.bottom(), 0xFF000000 | colours[i]);
                if (i > 0 && end > x + 1) {
                    g.vLine(x, bar.y() - 1, bar.bottom(), WELL_D);   // 两段之间 1 像素缝，颜色再近也分得出段
                }
                x = end;
            }
        }
        int max = Math.min(BuilderMix.HARD_MAX_KINDS, ECBuilder.Config.mixMaxKinds());
        for (int i = 0; i < l.mixRows.size(); i++) {
            BuilderPanelLayout.MixRow row = l.mixRows.get(i);
            if (i < mix.size()) {
                MixPick p = mix.get(i);
                Block block = ForgeRegistries.BLOCKS.getValue(p.block());
                g.renderItem(new ItemStack(block == null ? Items.BARRIER : block.asItem()), row.icon().x(), row.icon().y());
                boolean hm = !locked && row.minus().contains(mx, my);
                boolean hp = !locked && row.plus().contains(mx, my);
                boolean hr = !locked && row.remove().contains(mx, my);
                mini(g, row.minus(), hm, !locked && p.weight() > BuilderMix.MIN_WEIGHT);
                glyph(g, row.minus(), GLYPH_MINUS, locked ? OFF : CodexTheme.MIST);
                drawFitted(g, Component.literal(String.valueOf(p.weight())), row.value(), locked ? OFF : CodexTheme.PEARL, true);
                mini(g, row.plus(), hp, !locked && p.weight() < BuilderMix.MAX_WEIGHT);
                glyph(g, row.plus(), GLYPH_PLUS, locked ? OFF : CodexTheme.MIST);
                int pct = total == 0 ? 0 : Math.round(p.weight() * 100F / total);
                Component pc = Component.literal(pct + "%");
                if (font.width(pc) - 1 > row.percent().w()) {
                    pc = Component.literal(String.valueOf(pct));   // 窄版放不下百分号时只写数（「100%」要 23 像素，窄版只有 17）
                }
                int pw = Math.min(font.width(pc), row.percent().w());
                drawFitted(g, pc, new BuilderPanelLayout.Rect(row.percent().right() - pw, row.percent().y(), pw, row.percent().h()),
                        locked ? OFF : CodexTheme.MIST, false);
                mini(g, row.remove(), hr, !locked);
                glyph(g, row.remove(), GLYPH_X, locked ? OFF : hr ? WARN : CodexTheme.MIST);
                if (row.icon().contains(mx, my) && block != null) {
                    tooltip.add(block.getName());
                }
                if (hr) {
                    tooltip.add(Component.translatable(P + "remove"));
                }
            } else if (i == mix.size() && mix.size() < max) {
                BuilderPanelLayout.Rect add = row.add();
                boolean hover = !locked && add.contains(mx, my);
                button(g, add, mixAdd, hover, locked);
                drawFitted(g, Component.translatable(mixAdd ? P + "mix_add_cancel" : P + "mix_add"), add,
                        locked ? OFF : mixAdd ? WAIT : CodexTheme.MIST, true);
                break;
            } else if (i == 0 && mix.isEmpty()) {
                break;
            }
        }
        if (mix.isEmpty() && !l.mixRows.isEmpty()) {
            BuilderPanelLayout.MixRow first = l.mixRows.get(0);
            if (first.row().bottom() + 2 < l.content.bottom()) {
                BuilderPanelLayout.Rect hint = new BuilderPanelLayout.Rect(first.row().x(), first.add().bottom() + 3, first.row().w(),
                        Math.max(0, l.content.bottom() - first.add().bottom() - 3));
                drawWrapped(g, Component.translatable(P + "mix_empty"), hint, CodexTheme.FAINT, false);
            }
        }
    }

    // ---- 模块页 ----

    private void drawModules(GuiGraphics g, BuilderPanelLayout l, ItemStack wand, BuilderTier tier, boolean locked, int mx, int my,
                             List<Component> tooltip) {
        List<BuilderModules.Kind> slotted = BuilderModules.slotted(wand);
        Set<BuilderModules.Kind> off = BuilderModules.disabled(wand);
        drawFitted(g, Component.translatable(P + "modules", slotted.size(), tier.moduleSlots()), l.moduleHead, CodexTheme.FAINT, false);
        for (int i = 0; i < l.moduleRows.size(); i++) {
            BuilderPanelLayout.ModuleRow row = l.moduleRows.get(i);
            well(g, row.row());
            if (i >= slotted.size()) {
                drawFitted(g, Component.translatable(P + "empty_slot"), row.name(), OFF, false);
                drawFitted(g, Component.translatable(P + "empty_slot_hint"), row.desc(), CodexTheme.FAINT, false);
                continue;
            }
            BuilderModules.Kind kind = slotted.get(i);
            boolean enabled = !off.contains(kind);
            g.renderItem(kind.newItem(), row.icon().x(), row.icon().y());
            if (!enabled) {
                g.fill(row.icon().x(), row.icon().y(), row.icon().right(), row.icon().bottom(), 0xA00E0C12);
            }
            drawFitted(g, Component.translatable("tooltip.essentialcraft5.builder.modules." + kind.id()), row.name(),
                    enabled ? CodexTheme.PEARL : OFF, false);
            drawFitted(g, Component.translatable(P + "module." + kind.id()), row.desc(), enabled ? CodexTheme.FAINT : OFF, false);
            BuilderPanelLayout.Rect t = row.toggle();
            boolean ht = !locked && t.contains(mx, my);
            g.fill(t.x(), t.y(), t.right(), t.bottom(), enabled ? (locked ? LINE_OFF : CodexTheme.FOCUS_DEEP) : WELL_D);
            frame(g, t, ht ? CodexTheme.GLOW : LINE_DIM);
            int knobX = enabled ? t.right() - 1 - 7 : t.x() + 1;
            g.fill(knobX, t.y() + 1, knobX + 7, t.bottom() - 1, locked ? OFF : enabled ? CodexTheme.PEARL : CodexTheme.MIST);
            BuilderPanelLayout.Rect r = row.remove();
            boolean hr = !locked && r.contains(mx, my);
            mini(g, r, hr, !locked);
            glyph(g, r, GLYPH_X, locked ? OFF : hr ? WARN : CodexTheme.MIST);
            if (t.contains(mx, my)) {
                tooltip.add(Component.translatable(locked ? P + "locked" : enabled ? P + "module_on" : P + "module_off"));
            } else if (r.contains(mx, my)) {
                tooltip.add(Component.translatable(locked ? P + "locked" : P + "module_remove"));
            }
        }
        if (l.limits != null) {
            BuilderTier.Stats stats = BuilderTier.stats(wand);
            BuilderPanelLayout.Rect lim = l.limits;
            if (l.limitsFull) {
                drawFitted(g, Component.translatable(P + "limits"), new BuilderPanelLayout.Rect(lim.x(), lim.y(), lim.w(), 9), CodexTheme.FAINT,
                        false);
                Component[] lines = {
                        Component.translatable(P + "limit.volume", BuilderHud.number(stats.maxVolume())),
                        Component.translatable(P + "limit.side", BuilderHud.number(stats.maxSide())),
                        Component.translatable(P + "limit.speed", BuilderHud.number(stats.cellsPerTick())),
                        Component.translatable(P + "limit.reach", BuilderHud.number(stats.maxReach())),
                        Component.translatable(P + "limit.undo", stats.undoDepth())};
                for (int i = 0; i < lines.length; i++) {
                    drawFitted(g, lines[i], new BuilderPanelLayout.Rect(lim.x() + 4, lim.y() + BuilderPanelLayout.HEAD_GAP + i * BuilderPanelLayout.LINE,
                            lim.w() - 4, 9), CodexTheme.MIST, false);
                }
            } else {
                drawFitted(g, Component.translatable(P + "limits_brief1", BuilderHud.number(stats.maxVolume()), BuilderHud.number(stats.maxSide()),
                        BuilderHud.number(stats.cellsPerTick())), new BuilderPanelLayout.Rect(lim.x(), lim.y(), lim.w(), 9), CodexTheme.FAINT, false);
                drawFitted(g, Component.translatable(P + "limits_brief2", BuilderHud.number(stats.maxReach()), stats.undoDepth()),
                        new BuilderPanelLayout.Rect(lim.x(), lim.y() + BuilderPanelLayout.LINE, lim.w(), 9), CodexTheme.FAINT, false);
            }
        }
        g.vLine(l.columnRule.x(), l.columnRule.y(), l.columnRule.bottom() - 1, RULE);
        drawFitted(g, Component.translatable(P + "discard"), l.discardHead, CodexTheme.FAINT, false);
        BuilderPanelLayout.Rect grid = l.discardGrid;
        if (!BuilderModules.has(wand, BuilderModules.Kind.SALVAGE)) {
            drawWrapped(g, Component.translatable(P + "discard_none"), new BuilderPanelLayout.Rect(grid.x(), grid.y(), grid.w(),
                    l.content.bottom() - grid.y()), CodexTheme.FAINT, false);
            return;
        }
        List<Item> candidates = discardCandidates;
        if (candidates.isEmpty()) {
            drawWrapped(g, Component.translatable(P + "discard_empty"), grid, CodexTheme.FAINT, false);
        }
        List<ResourceLocation> discard = currentDiscard(wand);
        int cols = l.discardCols;
        int visibleRows = Math.max(1, (grid.h() + 1) / BuilderPanelLayout.SLOT_PITCH);
        int totalRows = (candidates.size() + cols - 1) / cols;
        discardScroll = Math.max(0, Math.min(discardScroll, Math.max(0, totalRows - visibleRows)));
        for (int i = discardScroll * cols; i < candidates.size(); i++) {
            int r = i / cols - discardScroll;
            if (r >= visibleRows) {
                break;
            }
            int c = i % cols;
            int x = grid.x() + c * BuilderPanelLayout.SLOT_PITCH;
            int y = grid.y() + r * BuilderPanelLayout.SLOT_PITCH;
            BuilderPanelLayout.Rect slot = new BuilderPanelLayout.Rect(x, y, BuilderPanelLayout.SLOT, BuilderPanelLayout.SLOT);
            Item item = candidates.get(i);
            boolean marked = discard.contains(ForgeRegistries.ITEMS.getKey(item));
            boolean hover = slot.contains(mx, my);
            g.fill(slot.x(), slot.y(), slot.right(), slot.bottom(), hover && !locked ? BTN_HI : WELL_D);
            frame(g, slot, marked ? WARN : LINE_OFF);
            g.renderItem(new ItemStack(item), x + 1, y + 1);
            if (locked) {
                g.fill(x + 1, y + 1, x + 17, y + 17, 0x800E0C12);
            }
            if (hover) {
                tooltip.add(item.getDescription());
                tooltip.add(Component.translatable(locked ? P + "locked" : marked ? P + "discard_on" : P + "discard_off")
                        .withStyle(s -> s.withColor(CodexTheme.FAINT & 0xFFFFFF)));
            }
        }
        if (totalRows > visibleRows) {
            BuilderPanelLayout.Rect track = new BuilderPanelLayout.Rect(grid.x(), grid.y(), grid.w(), visibleRows * BuilderPanelLayout.SLOT_PITCH - 1);
            scrollbar(g, track, discardScroll, totalRows - visibleRows, totalRows);
        }
        // 窄版一行放不下时折到下面（下面没有别的东西）
        drawWrapped(g, Component.translatable(P + "discard_note"), new BuilderPanelLayout.Rect(l.discardNote.x(), l.discardNote.y(), l.discardNote.w(),
                Math.max(l.discardNote.h(), l.content.bottom() - l.discardNote.y())), CodexTheme.FAINT, false);
    }

    // ---- 底条 ----

    private void drawBar(GuiGraphics g, BuilderPanelLayout l, boolean locked, int mx, int my, List<Component> tooltip) {
        S2CBuilderPanelPacket panel = BuilderClientStatus.panel();
        boolean undoOk = panel == null || panel.undoState() == S2CBuilderPanelPacket.UNDO_READY;
        boolean hu = undoOk && l.undo.contains(mx, my);
        button(g, l.undo, false, hu, !undoOk);
        drawFitted(g, Component.translatable(P + "undo"), l.undo, undoOk ? CodexTheme.PEARL : OFF, true);
        boolean hs = locked && l.stop.contains(mx, my);
        button(g, l.stop, false, hs, !locked);
        drawFitted(g, Component.translatable(P + "stop"), l.stop, locked ? WARN : OFF, true);
        // 说明：撤销能不能用、撤的是哪次
        List<Component> lines = undoLines(panel);
        drawLines(g, lines, l.undoNote, undoOk ? CodexTheme.MIST : CodexTheme.FAINT);
        if (l.progress != null) {
            drawProgress(g, l.progress);
        }
    }

    private List<Component> undoLines(@Nullable S2CBuilderPanelPacket panel) {
        List<Component> out = new ArrayList<>();
        if (panel == null) {
            out.add(Component.translatable(P + "undo.unknown"));
            return out;
        }
        switch (panel.undoState()) {
            case S2CBuilderPanelPacket.UNDO_READY -> {
                BuilderShape shape = panel.undoShape() >= 0 && panel.undoShape() < BuilderShape.values().length
                        ? BuilderShape.values()[panel.undoShape()] : null;
                Component what = shape == null ? Component.translatable(P + "undo.box", panel.undoWidth(), panel.undoLength(), panel.undoHeight())
                        : Component.translatable(P + "undo.shape", Component.translatable("message.essentialcraft5.builder.shape." + shape.id()),
                        panel.undoWidth(), panel.undoLength(), panel.undoHeight());
                out.add(Component.translatable(P + "undo.ready", what, BuilderHud.number(panel.undoCells())));
                out.add(Component.translatable(P + "undo.age", ageText(panel.undoAgeSeconds())));
            }
            case S2CBuilderPanelPacket.UNDO_CLEARED -> out.add(Component.translatable(P + "undo.cleared"));
            case S2CBuilderPanelPacket.UNDO_EXPIRED -> out.add(Component.translatable(P + "undo.expired"));
            case S2CBuilderPanelPacket.UNDO_FAR -> out.add(Component.translatable(P + "undo.far"));
            case S2CBuilderPanelPacket.UNDO_BUSY -> out.add(Component.translatable(P + "undo.busy"));
            case S2CBuilderPanelPacket.UNDO_OFF -> out.add(Component.translatable(P + "undo.off"));
            default -> out.add(Component.translatable(P + "undo.none"));
        }
        return out;
    }

    private static Component ageText(int seconds) {
        if (seconds < 60) {
            return Component.translatable(P + "age.seconds", seconds);
        }
        return Component.translatable(P + "age.minutes", seconds / 60);
    }

    private void drawProgress(GuiGraphics g, BuilderPanelLayout.Rect p) {
        S2CBuilderStatusPacket status = BuilderClientStatus.latest();
        if (status == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        long now = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        int state = status.state();
        Component head;
        int colour = STATUS;
        switch (state) {
            case S2CBuilderStatusPacket.UNDOING -> head = Component.translatable("hud.essentialcraft5.builder.undoing",
                    BuilderHud.number(status.done()), BuilderHud.number(status.total()));
            case S2CBuilderStatusPacket.PAUSED, S2CBuilderStatusPacket.PAUSED_MRU -> {
                int seconds = (BuilderClientStatus.pauseTicksLeft(now) + 19) / 20;
                head = Component.translatable(state == S2CBuilderStatusPacket.PAUSED ? "hud.essentialcraft5.builder.paused_material"
                        : "hud.essentialcraft5.builder.paused_mru", BuilderHud.number(seconds));
                colour = WAIT;
            }
            case S2CBuilderStatusPacket.QUEUED -> head = status.queueAhead() <= 0 ? Component.translatable("hud.essentialcraft5.builder.queued_slot")
                    : Component.translatable("hud.essentialcraft5.builder.queued", BuilderHud.number(status.queueAhead()));
            case S2CBuilderStatusPacket.SURVEYING -> head = Component.translatable("hud.essentialcraft5.builder.surveying",
                    BuilderHud.number(status.done()));
            default -> head = Component.translatable("hud.essentialcraft5.builder.running", BuilderHud.number(status.done()),
                    BuilderHud.number(status.total()));
        }
        int y = p.y() + (p.h() - (9 + 2 + 5 + 2 + 9)) / 2;
        drawFitted(g, head, new BuilderPanelLayout.Rect(p.x(), y, p.w(), 9), colour, false);
        BuilderPanelLayout.Rect bar = new BuilderPanelLayout.Rect(p.x(), y + 11, p.w(), 5);
        g.fill(bar.x(), bar.y(), bar.right(), bar.bottom(), WELL_D);
        if (status.total() > 0) {
            int fill = (int) Math.min(bar.w(), (long) bar.w() * status.done() / status.total());
            g.fillGradient(bar.x(), bar.y(), bar.x() + fill, bar.bottom(), CodexTheme.MRU_MAGENTA,
                    S2CBuilderStatusPacket.paused(state) ? WAIT : CodexTheme.MRU_VIOLET);
        }
        if (state == S2CBuilderStatusPacket.RUNNING || state == S2CBuilderStatusPacket.UNDOING) {
            long eta = BuilderClientStatus.etaTicks(now);
            if (eta > 0L) {
                drawFitted(g, Component.translatable("hud.essentialcraft5.builder.eta", BuilderHud.number((eta + 19) / 20)),
                        new BuilderPanelLayout.Rect(p.x(), y + 18, p.w(), 9), CodexTheme.FAINT, false);
            }
        }
    }

    // ------------------------------------------------------------------
    // 输入
    // ------------------------------------------------------------------

    private boolean wandKeyDown() {
        InputConstants.Key key = BuilderInput.WAND_KEY.getKey();
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return key.getValue() != InputConstants.UNKNOWN.getValue() && InputConstants.isKeyDown(window, key.getValue());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (BuilderInput.WAND_KEY.getKey().equals(InputConstants.getKey(keyCode, scanCode))) {
            if (!keyArmed) {
                keyArmed = true;
                keyUsed = false;
                keyDownAt = ticks;
            }
            return true;
        }
        if (keyArmed) {
            keyUsed = true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (mixAdd) {
                mixAdd = false;
                return true;
            }
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (BuilderInput.WAND_KEY.getKey().equals(InputConstants.getKey(keyCode, scanCode))) {
            wandKeyReleased();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    private void wandKeyReleased() {
        boolean tap = keyArmed && !keyUsed && ticks - keyDownAt <= TAP_MAX_TICKS;
        keyArmed = false;
        if (tap) {
            onClose();
        }
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (BuilderInput.WAND_KEY.getKey().equals(InputConstants.Type.MOUSE.getOrCreate(button))) {
            wandKeyReleased();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || delta == 0D) {
            return false;
        }
        int dir = delta > 0D ? 1 : -1;
        if (wandKeyDown()) {
            // 按住魔杖键滚轮：同游戏里，调尺寸
            keyUsed = true;
            BuilderInput.resize(player, hand, dir, hasShiftDown());
            return true;
        }
        BuilderPanelLayout l = layout();
        if (l.tooSmall) {
            return false;
        }
        if (page == BuilderPanelLayout.Page.BLOCK && l.listWell.contains(mouseX, mouseY)) {
            listScroll -= dir * BuilderPanelLayout.ROW;
            return true;
        }
        if (page == BuilderPanelLayout.Page.MODULES && l.discardGrid.contains(mouseX, mouseY)) {
            discardScroll -= dir;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        if (BuilderInput.WAND_KEY.getKey().equals(InputConstants.Type.MOUSE.getOrCreate(button))) {
            if (!keyArmed) {
                keyArmed = true;
                keyUsed = false;
                keyDownAt = ticks;
            }
            return true;
        }
        if (keyArmed) {
            keyUsed = true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && wandKeyDown()) {
            // 按住魔杖键右键：同游戏里，撤销上一次
            send(C2SBuilderActionPacket.UNDO, 0, 0);
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        BuilderPanelLayout l = layout();
        if (l.tooSmall) {
            onClose();
            return true;
        }
        if (l.close.contains(mouseX, mouseY)) {
            onClose();
            return true;
        }
        for (int i = 0; i < l.tabs.size(); i++) {
            if (l.tabs.get(i).contains(mouseX, mouseY)) {
                page = BuilderPanelLayout.Page.values()[i];
                lastPage = page;
                mixAdd = false;
                click();
                return true;
            }
        }
        if (l.undo.contains(mouseX, mouseY)) {
            S2CBuilderPanelPacket panel = BuilderClientStatus.panel();
            if (panel == null || panel.undoState() == S2CBuilderPanelPacket.UNDO_READY) {
                send(C2SBuilderActionPacket.UNDO, 0, 0);
                click();
            }
            return true;
        }
        if (l.stop.contains(mouseX, mouseY)) {
            if (working()) {
                send(C2SBuilderActionPacket.STOP, 0, 0);
                click();
            }
            return true;
        }
        ItemStack wand = player.getItemInHand(hand);
        boolean locked = working();
        // 左栏尺寸加减号
        BuilderShape shape = BuilderSelection.shape(wand);
        if (!locked && shape.resizable()) {
            for (int k = 0; k < 2; k++) {
                int dir = l.sizeMinus.get(k).contains(mouseX, mouseY) ? -1 : l.sizePlus.get(k).contains(mouseX, mouseY) ? 1 : 0;
                if (dir != 0) {
                    BuilderInput.resize(player, hand, dir, sizeSneak(shape, k));
                    click();
                    return true;
                }
            }
        }
        if (!l.content.contains(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (locked) {
            return true;   // 施工中除了撤销和停下都不收
        }
        BuilderTier tier = BuilderTier.of(wand);
        switch (page) {
            case RANGE -> {
                List<BuilderShape> shapes = shapes(tier);
                for (int i = 0; i < l.shapeButtons.size() && i < shapes.size(); i++) {
                    if (l.shapeButtons.get(i).contains(mouseX, mouseY)) {
                        if (shapes.get(i) != shape) {
                            send(C2SBuilderActionPacket.SET_SHAPE, shapes.get(i).ordinal(), 0);
                            click();
                        }
                        return true;
                    }
                }
                List<BuilderMode> modes = modes(wand, tier);
                for (int i = 0; i < l.modeRows.size() && i < modes.size(); i++) {
                    if (l.modeRows.get(i).contains(mouseX, mouseY)) {
                        if (modes.get(i) != BuilderMode.of(wand)) {
                            send(C2SBuilderActionPacket.SET_MODE, modes.get(i).ordinal(), 0);
                            click();
                        }
                        return true;
                    }
                }
            }
            case BLOCK -> {
                if (clickBlockPage(l, wand, mouseX, mouseY)) {
                    click();
                }
                return true;
            }
            case MODULES -> {
                if (clickModulesPage(l, wand, mouseX, mouseY)) {
                    click();
                }
                return true;
            }
            default -> {
            }
        }
        return true;
    }

    private boolean clickBlockPage(BuilderPanelLayout l, ItemStack wand, double mx, double my) {
        if (l.listView.contains(mx, my)) {
            int index = (int) ((my - l.listView.y() + listScroll) / BuilderPanelLayout.ROW);
            List<Material> list = materials();
            if (index < 0 || index >= list.size()) {
                return false;
            }
            Block block = ((BlockItem) list.get(index).item()).getBlock();
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
            if (id == null) {
                return false;
            }
            if (mixAdd) {
                List<MixPick> picks = new ArrayList<>(Objects.requireNonNullElse(mixPicks(wand), List.of()));
                if (BuilderMix.usableBlock(id) == null || picks.stream().anyMatch(p -> p.block().equals(id))) {
                    return false;
                }
                picks.add(new MixPick(id, 1));
                setLocalMix(picks);
                mixAdd = false;
                return true;
            }
            ECNetwork.sendToServer(C2SBuilderActionPacket.pickBlock(hand == InteractionHand.OFF_HAND ? 1 : 0, id));
            return true;
        }
        if (!BuilderModules.has(wand, BuilderModules.Kind.MIX)) {
            return false;
        }
        List<MixPick> picks = new ArrayList<>(Objects.requireNonNullElse(mixPicks(wand), List.of()));
        int max = Math.min(BuilderMix.HARD_MAX_KINDS, ECBuilder.Config.mixMaxKinds());
        for (int i = 0; i < l.mixRows.size(); i++) {
            BuilderPanelLayout.MixRow row = l.mixRows.get(i);
            if (i < picks.size()) {
                MixPick p = picks.get(i);
                if (row.minus().contains(mx, my) && p.weight() > BuilderMix.MIN_WEIGHT) {
                    picks.set(i, new MixPick(p.block(), p.weight() - 1));
                    setLocalMix(picks);
                    return true;
                }
                if (row.plus().contains(mx, my) && p.weight() < BuilderMix.MAX_WEIGHT) {
                    picks.set(i, new MixPick(p.block(), p.weight() + 1));
                    setLocalMix(picks);
                    return true;
                }
                if (row.remove().contains(mx, my)) {
                    picks.remove(i);
                    setLocalMix(picks);
                    return true;
                }
            } else if (i == picks.size() && picks.size() < max && row.add().contains(mx, my)) {
                mixAdd = !mixAdd;
                return true;
            }
        }
        return false;
    }

    private boolean clickModulesPage(BuilderPanelLayout l, ItemStack wand, double mx, double my) {
        List<BuilderModules.Kind> slotted = BuilderModules.slotted(wand);
        Set<BuilderModules.Kind> off = BuilderModules.disabled(wand);
        for (int i = 0; i < l.moduleRows.size() && i < slotted.size(); i++) {
            BuilderPanelLayout.ModuleRow row = l.moduleRows.get(i);
            BuilderModules.Kind kind = slotted.get(i);
            if (row.toggle().contains(mx, my)) {
                send(C2SBuilderActionPacket.TOGGLE_MODULE, kind.ordinal(), off.contains(kind) ? 1 : 0);
                return true;
            }
            if (row.remove().contains(mx, my)) {
                send(C2SBuilderActionPacket.REMOVE_MODULE, kind.ordinal(), 0);
                return true;
            }
        }
        if (!BuilderModules.has(wand, BuilderModules.Kind.SALVAGE) || !l.discardGrid.contains(mx, my)) {
            return false;
        }
        int cols = l.discardCols;
        int c = (int) ((mx - l.discardGrid.x()) / BuilderPanelLayout.SLOT_PITCH);
        int r = (int) ((my - l.discardGrid.y()) / BuilderPanelLayout.SLOT_PITCH);
        // 和画的时候同一个可见行数：网格高被截成不是整行时，最下面那条窄条和行间缝都不算点中（不然点到的是看不见的下一行）
        int visibleRows = Math.max(1, (l.discardGrid.h() + 1) / BuilderPanelLayout.SLOT_PITCH);
        if (c < 0 || c >= cols || r < 0 || r >= visibleRows
                || (mx - l.discardGrid.x()) % BuilderPanelLayout.SLOT_PITCH >= BuilderPanelLayout.SLOT
                || (my - l.discardGrid.y()) % BuilderPanelLayout.SLOT_PITCH >= BuilderPanelLayout.SLOT) {
            return false;
        }
        int index = (r + discardScroll) * cols + c;
        if (index < 0 || index >= discardCandidates.size()) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(discardCandidates.get(index));
        if (id == null) {
            return false;
        }
        List<ResourceLocation> list = new ArrayList<>(currentDiscard(wand));
        if (!list.remove(id)) {
            // 上限按服务端配置（登录时同步过来），满了不让加；超过的整张表服务端不收
            if (list.size() >= Math.min(BuilderSalvage.HARD_MAX_DISCARD, ECBuilder.Config.salvageDiscardMax())) {
                return false;
            }
            list.add(id);
        }
        localDiscard = list;
        discardChangedAt = ticks;
        discardSentAt = -1;
        return true;
    }

    private void send(int action, int a, int b) {
        BuilderInput.send(action, hand, a, b);
        // 改完马上要一份新清单（撤销状态、身上的料都可能变了）；服务端限频，最快 10 刻一份
        lastSync = Math.min(lastSync, ticks - SYNC_TICKS + 4);
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    // ------------------------------------------------------------------
    // 混铺表、丢弃清单（本地编辑 + 延迟发送）
    // ------------------------------------------------------------------

    @Nullable
    private List<MixPick> mixPicks(ItemStack wand) {
        return localMix != null ? localMix : wandMix(wand);
    }

    /** 魔杖 NBT 里的表（筛过的） */
    @Nullable
    private static List<MixPick> wandMix(ItemStack wand) {
        BuilderMix.Table table = BuilderMix.read(wand);
        if (table == null) {
            return null;
        }
        List<MixPick> out = new ArrayList<>(table.size());
        for (BuilderMix.Entry entry : table.entries()) {
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(entry.block());
            if (id != null) {
                out.add(new MixPick(id, entry.weight()));
            }
        }
        return out;
    }

    private void setLocalMix(List<MixPick> picks) {
        localMix = List.copyOf(picks);
        mixChangedAt = ticks;
        mixSentAt = -1;
    }

    private void flushMix() {
        List<MixPick> picks = localMix;
        if (picks == null) {
            return;
        }
        List<ResourceLocation> ids = new ArrayList<>(picks.size());
        List<Integer> weights = new ArrayList<>(picks.size());
        for (MixPick p : picks) {
            ids.add(p.block());
            weights.add(p.weight());
        }
        ECNetwork.sendToServer(C2SBuilderActionPacket.setMix(hand == InteractionHand.OFF_HAND ? 1 : 0, ids, weights));
        mixChangedAt = -1;
        mixSentAt = ticks;
    }

    private List<ResourceLocation> currentDiscard(ItemStack wand) {
        return localDiscard != null ? localDiscard : wandDiscard(wand);
    }

    private static List<ResourceLocation> wandDiscard(ItemStack wand) {
        List<ResourceLocation> out = new ArrayList<>();
        for (Item item : BuilderSalvage.discardList(wand)) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            if (id != null) {
                out.add(id);
            }
        }
        return out;
    }

    private void flushDiscard() {
        List<ResourceLocation> list = localDiscard;
        if (list == null) {
            return;
        }
        ECNetwork.sendToServer(C2SBuilderActionPacket.setDiscard(hand == InteractionHand.OFF_HAND ? 1 : 0, list));
        discardChangedAt = -1;
        discardSentAt = ticks;
    }

    /** 丢弃清单能挑的：已在清单里的、回收最近拆下过的、身上的方块（按这个顺序、去重） */
    private List<Item> computeDiscardCandidates(ItemStack wand) {
        Set<Item> out = new LinkedHashSet<>();
        for (ResourceLocation id : currentDiscard(wand)) {
            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item != null && item != Items.AIR) {
                out.add(item);
            }
        }
        S2CBuilderPanelPacket panel = BuilderClientStatus.panel();
        if (panel != null) {
            for (ResourceLocation id : panel.recent()) {
                Item item = ForgeRegistries.ITEMS.getValue(id);
                if (item != null && item != Items.AIR) {
                    out.add(item);
                }
            }
            for (S2CBuilderPanelPacket.Entry entry : panel.materials()) {
                Item item = ForgeRegistries.ITEMS.getValue(entry.item());
                if (item != null && item != Items.AIR) {
                    out.add(item);
                }
            }
        }
        List<Item> list = new ArrayList<>(out);
        return list.size() > BuilderSalvage.HARD_MAX_DISCARD + S2CBuilderPanelPacket.MAX_RECENT
                ? list.subList(0, BuilderSalvage.HARD_MAX_DISCARD + S2CBuilderPanelPacket.MAX_RECENT) : list;
    }

    // ------------------------------------------------------------------
    // 小画法
    // ------------------------------------------------------------------

    private static void frame(GuiGraphics g, BuilderPanelLayout.Rect r, int colour) {
        g.hLine(r.x(), r.right() - 1, r.y(), colour);
        g.hLine(r.x(), r.right() - 1, r.bottom() - 1, colour);
        g.vLine(r.x(), r.y(), r.bottom() - 1, colour);
        g.vLine(r.right() - 1, r.y(), r.bottom() - 1, colour);
    }

    /** 凹下去的井：上左深、下右浅 */
    private static void well(GuiGraphics g, BuilderPanelLayout.Rect r) {
        g.fill(r.x(), r.y(), r.right(), r.bottom(), WELL_D);
        g.hLine(r.x(), r.right() - 1, r.y(), 0xFF000000);
        g.vLine(r.x(), r.y(), r.bottom() - 1, 0xFF000000);
        g.hLine(r.x(), r.right() - 1, r.bottom() - 1, WELL_L);
        g.vLine(r.right() - 1, r.y(), r.bottom() - 1, WELL_L);
    }

    private static void button(GuiGraphics g, BuilderPanelLayout.Rect r, boolean selected, boolean hover, boolean disabled) {
        int bg = disabled ? BTN_OFF : selected ? (hover ? SEL_HI : SEL) : hover ? BTN_HI : BTN;
        g.fill(r.x(), r.y(), r.right(), r.bottom(), bg);
        frame(g, r, disabled ? LINE_OFF : selected ? CodexTheme.EDGE : hover ? CodexTheme.GLOW : LINE_DIM);
        if (selected && !disabled) {
            g.hLine(r.x() + 1, r.right() - 2, r.y(), CodexTheme.FOCUS);
        }
    }

    private static void mini(GuiGraphics g, BuilderPanelLayout.Rect r, boolean hover, boolean enabled) {
        g.fill(r.x(), r.y(), r.right(), r.bottom(), !enabled ? BTN_OFF : hover ? BTN_HI : BTN);
        frame(g, r, !enabled ? LINE_OFF : hover ? CodexTheme.GLOW : LINE_DIM);
    }

    private static final String[] GLYPH_PLUS = {"..#..", "..#..", "#####", "..#..", "..#.."};
    private static final String[] GLYPH_MINUS = {".....", ".....", "#####", ".....", "....."};
    private static final String[] GLYPH_X = {"#...#", ".#.#.", "..#..", ".#.#.", "#...#"};

    /** 5 × 5 的像素小图，在 r 里居中 */
    private static void glyph(GuiGraphics g, BuilderPanelLayout.Rect r, String[] rows, int colour) {
        int x0 = BuilderPanelLayout.center(r.x(), r.w(), 5);
        int y0 = BuilderPanelLayout.center(r.y(), r.h(), 5);
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < rows[y].length(); x++) {
                if (rows[y].charAt(x) == '#') {
                    g.fill(x0 + x, y0 + y, x0 + x + 1, y0 + y + 1, colour);
                }
            }
        }
    }

    /** 形状图标 11 × 11 */
    private static void shapeIcon(GuiGraphics g, BuilderShape shape, int x, int y, int colour) {
        int dim = blend(colour & 0xFFFFFF, INK, 0.55F);
        switch (shape) {
            case POINTS -> {
                g.fill(x, y, x + 3, y + 3, colour);
                g.fill(x + 8, y + 8, x + 11, y + 11, colour);
                for (int i = 4; i < 8; i += 2) {
                    g.fill(x + i, y + i, x + i + 1, y + i + 1, dim);
                }
                g.hLine(x + 3, x + 10, y + 1, dim);
                g.vLine(x + 9, y + 1, y + 7, dim);
            }
            case PLANE -> {
                g.fill(x, y + 3, x + 11, y + 8, dim);
                frame(g, new BuilderPanelLayout.Rect(x, y + 3, 11, 5), colour);
            }
            case LINE -> {
                g.fill(x, y + 4, x + 11, y + 7, colour);
                g.fill(x + 8, y + 2, x + 9, y + 9, dim);
            }
            case WALL -> {
                g.fill(x + 3, y, x + 8, y + 11, dim);
                frame(g, new BuilderPanelLayout.Rect(x + 3, y, 5, 11), colour);
            }
            case FACE -> {
                g.fill(x, y + 6, x + 11, y + 11, dim);
                g.fill(x, y + 4, x + 11, y + 6, colour);
                g.fill(x + 2, y, x + 5, y + 4, colour);
            }
            default -> {
            }
        }
    }

    /** 模式图标 9 × 9 */
    private static void modeIcon(GuiGraphics g, BuilderMode mode, int x, int y, int colour) {
        BuilderPanelLayout.Rect r = new BuilderPanelLayout.Rect(x, y, 9, 9);
        switch (mode) {
            case FILL -> g.fill(x + 1, y + 1, x + 8, y + 8, colour);
            case CLEAR -> {
                frame(g, r, colour);
                glyph(g, r, GLYPH_X, colour);
            }
            case CLEAR_MATCHING -> {
                frame(g, r, colour);
                g.fill(x + 3, y + 3, x + 6, y + 6, colour);
            }
            case CLEAR_OTHERS -> {
                g.fill(x, y, x + 9, y + 9, colour);
                g.fill(x + 3, y + 3, x + 6, y + 6, INK);
            }
            case REPLACE_ALL -> {
                frame(g, r, colour);
                g.fill(x + 1, y + 1, x + 5, y + 8, colour);
            }
            default -> {
            }
        }
    }

    private static void scrollbar(GuiGraphics g, BuilderPanelLayout.Rect view, int scroll, int maxScroll, int content) {
        int x = view.right() - BuilderPanelLayout.SCROLLBAR - 1;
        int h = view.h();
        int thumb = Math.max(8, h * h / Math.max(h, content));
        int y = view.y() + (h - thumb) * scroll / Math.max(1, maxScroll);
        g.fill(x, view.y(), x + BuilderPanelLayout.SCROLLBAR, view.bottom(), LINE_OFF);
        g.fill(x, y, x + BuilderPanelLayout.SCROLLBAR, y + thumb, CodexTheme.EDGE);
    }

    /** 图例：每项一个 7 × 7 色块加说明；inners 不为 0 的项画成 colours 的 1 像素外框 + inners 填心（和蓝图上待拆的格同一画法） */
    private static void legend(GuiGraphics g, BuilderPanelLayout.Rect line, int[] colours, int[] inners, Component[] labels) {
        Minecraft mc = Minecraft.getInstance();
        int x = line.x();
        int y = line.y() + (line.h() - 7) / 2;
        for (int i = 0; i < colours.length; i++) {
            if (x + 7 > line.right()) {
                break;
            }
            g.fill(x, y, x + 7, y + 7, 0xFF000000 | colours[i]);
            if (inners[i] != 0) {
                g.fill(x + 1, y + 1, x + 6, y + 6, 0xFF000000 | inners[i]);
            }
            x += 10;
            int room = line.right() - x;
            String text = ellipsis(mc.font, labels[i].getString(), room);
            g.drawString(mc.font, text, x, line.y() + (line.h() - 8) / 2, CodexTheme.MIST, false);
            x += mc.font.width(text) + 8;
        }
    }

    private static BuilderPanelLayout.Rect inset(BuilderPanelLayout.Rect r, int d) {
        return new BuilderPanelLayout.Rect(r.x() + d, r.y(), Math.max(0, r.w() - 2 * d), r.h());
    }

    /**
     * 左右各写一段：放得下就各写全；放不下时右段（多是数）先拿够自己的宽，左段至少留三分之一；左段本来就短的照它的宽留。
     * 两段都放不下时按各自全宽的比例分，两边一起省略
     */
    private void splitRow(GuiGraphics g, BuilderPanelLayout.Rect r, Component left, int leftColour, Component right, int rightColour) {
        int full = Math.max(0, r.w() - 4);
        int lw = font.width(left);
        int rw = font.width(right);
        if (lw + rw > full) {
            int leftKeep = Math.min(lw, full / 3);
            rw = rw <= full - leftKeep ? rw : lw + rw > 0 ? (int) ((long) full * rw / (lw + rw)) : 0;
            rw = Math.max(0, Math.min(rw, full - leftKeep));
        }
        if (rw > 0) {
            drawFitted(g, right, new BuilderPanelLayout.Rect(r.right() - rw, r.y(), rw, r.h()), rightColour, false);
        }
        drawFitted(g, left, new BuilderPanelLayout.Rect(r.x(), r.y(), Math.max(0, r.w() - rw - 4), r.h()), leftColour, false);
    }

    /** 一行字放进框里：放不下末尾省略；centred 时水平居中；垂直方向都居中 */
    private void drawFitted(GuiGraphics g, Component text, @Nullable BuilderPanelLayout.Rect r, int colour, boolean centred) {
        if (r == null || r.w() <= 0) {
            return;
        }
        FormattedCharSequence line;
        int w = font.width(text);
        if (w - 1 <= r.w()) {   // 字宽末尾 1 像素是字距、不着墨，超出框这 1 像素不算放不下
            line = text.getVisualOrderText();
        } else {
            int dots = font.width("…");
            line = net.minecraft.locale.Language.getInstance().getVisualOrder(
                    net.minecraft.network.chat.FormattedText.composite(font.substrByWidth(text, Math.max(0, r.w() - dots)), Component.literal("…")));
            w = font.width(line);
        }
        int x = centred ? BuilderPanelLayout.centerText(r.x(), r.w(), w) : r.x();
        int y = r.y() + (r.h() - 8) / 2;
        g.drawString(font, line, x, y, colour, false);
    }

    /** 折行写进框里，最多写到框底；最后一行放不下时省略 */
    private void drawWrapped(GuiGraphics g, Component text, @Nullable BuilderPanelLayout.Rect r, int colour, boolean centred) {
        if (r == null || r.w() <= 0) {
            return;
        }
        List<FormattedCharSequence> lines = font.split(text, r.w());
        int max = Math.max(1, (r.h() + 1) / 10);
        int shown = Math.min(lines.size(), max);
        int y = centred ? r.y() + (r.h() - shown * 10 + 2) / 2 : r.y();
        for (int i = 0; i < shown; i++) {
            FormattedCharSequence line = lines.get(i);
            if (i == shown - 1 && lines.size() > shown) {
                // 最后一行后面还有字：接上下一行再按框宽截短、末尾加省略号
                String cut = ellipsis(font, plain(line) + plain(lines.get(i + 1)), r.w());
                int x = centred ? BuilderPanelLayout.centerText(r.x(), r.w(), font.width(cut)) : r.x();
                g.drawString(font, cut, x, y + i * 10, colour, false);
                break;
            }
            int w = font.width(line);
            int x = centred ? BuilderPanelLayout.centerText(r.x(), r.w(), w) : r.x();
            g.drawString(font, line, x, y + i * 10, colour, false);
        }
    }

    private static String plain(FormattedCharSequence line) {
        StringBuilder sb = new StringBuilder();
        line.accept((index, style, codePoint) -> {
            sb.appendCodePoint(codePoint);
            return true;
        });
        return sb.toString();
    }

    /** 几行字（每行放不下就省略），在框里垂直居中 */
    private void drawLines(GuiGraphics g, List<Component> lines, BuilderPanelLayout.Rect r, int colour) {
        if (r.w() <= 0 || lines.isEmpty()) {
            return;
        }
        int shown = Math.min(lines.size(), Math.max(1, (r.h() + 2) / 10));
        int y = r.y() + (r.h() - (shown * 10 - 2)) / 2;
        for (int i = 0; i < shown; i++) {
            drawFitted(g, lines.get(i), new BuilderPanelLayout.Rect(r.x(), y + i * 10, r.w(), 8), colour, false);
        }
    }

    private static String ellipsis(net.minecraft.client.gui.Font font, String text, int max) {
        if (font.width(text) <= max) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, max - font.width("…"))) + "…";
    }

    /** rgb 和 base 按 t 混（t = base 占的份） */
    private static int blend(int rgb, int base, float t) {
        int r = (int) (((rgb >> 16) & 0xFF) * (1 - t) + ((base >> 16) & 0xFF) * t);
        int gr = (int) (((rgb >> 8) & 0xFF) * (1 - t) + ((base >> 8) & 0xFF) * t);
        int b = (int) ((rgb & 0xFF) * (1 - t) + (base & 0xFF) * t);
        return 0xFF000000 | r << 16 | gr << 8 | b;
    }

    // ------------------------------------------------------------------
    // 展示厅用
    // ------------------------------------------------------------------

    /** 切到某一页（展示厅拍三页用） */
    public void showroomPage(BuilderPanelLayout.Page target) {
        page = target;
        lastPage = target;
        layout = null;
    }

    /** 现在这份版面（展示厅记进日志量对齐用）；还没画过为 null */
    @Nullable
    public BuilderPanelLayout currentLayout() {
        return layout;
    }
}
