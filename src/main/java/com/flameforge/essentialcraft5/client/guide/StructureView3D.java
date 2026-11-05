package com.flameforge.essentialcraft5.client.guide;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.TripleTallBlock;
import com.flameforge.essentialcraft5.client.guide.GuideBlocks.Ctx;
import com.flameforge.essentialcraft5.client.guide.GuideBlocks.Laid;
import com.flameforge.essentialcraft5.client.guide.GuideTextLayout.Line;
import com.flameforge.essentialcraft5.structure.StructurePattern;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.RenderTypeHelper;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.tags.ITagManager;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 研究手册的多方块结构图：默认是能拖着转的立体模型，顶上「立体 / 平面」切回原来的分层俯视图（{@link Figures.Layout}，数格子还是它方便）。
 *
 * <ul>
 *   <li><b>立体</b>：斜 45° 俯视（看得见顶面、南面、东面），左键按住拖动旋转（左右转一圈、上下最多翻到正上方）；不自己转，阅读栏里不放常驻动画。
 *       模型右上角的「复位」回到初始角度。滚轮照常滚正文，不拿来缩放。</li>
 *   <li><b>层</b>：超过一层时顶栏有一排层按钮，点哪层就只画到那一层（上面的层拿掉，看得见里面），「全部」画整个结构。</li>
 *   <li><b>悬停</b>：鼠标下最靠前的那一格描品红框，出和平面图一样的提示（方块名、说明、可选、几种任选、高度）；点一下（没拖动）跳到介绍它的条目。
 *       图例里每种方块写上要几个，悬停图例时模型里这种方块全部描框。</li>
 *   <li>方块按它的方块模型画（用物品栏同一套光照）；方块模型是空的、样子靠方块实体渲染器画的（水晶柱），画渲染器给的静止一帧
 *       （{@link StaticBlockPreview}），没实现的照常调渲染器；流体画成贴着流体贴图的方块；「必须是空的」画成淡薰衣草的透明方块，
 *       可选的格子半透明，代码登记的结构若机器那格没写规则，按结构 id 找同名方块画上（找不到画淡品红的透明方块）。</li>
 * </ul>
 * 立体 / 平面的选择全手册共用（换一篇还是上次选的），不存盘。块高按两种视图里高的那个留，切换时下面的正文不跳。
 */
@OnlyIn(Dist.CLIENT)
final class StructureView3D extends Laid {

    // ================================================================ 模型

    /** 一种格子（图例的一项） */
    private static final class Kind {
        /** 候选方块状态（多个时鼠标压在图上每秒换一个，和平面图一样） */
        final BlockState[] states;
        final boolean air;
        final boolean optional;
        /** 机器那格没写规则、也找不到机器方块：画淡品红的透明方块 */
        final boolean marker;
        @Nullable
        final Component label;
        final Component name;
        /** 这种格子一共几格（图例写「×N」） */
        int count;
        /** 图例的名字排好的行和位置（排版时填） */
        List<FormattedCharSequence> lines = List.of();
        int lx;
        int ly;
        int lw;

        Kind(BlockState[] states, boolean air, boolean optional, boolean marker, @Nullable Component label, Component name) {
            this.states = states;
            this.air = air;
            this.optional = optional;
            this.marker = marker;
            this.label = label;
            this.name = name;
        }
    }

    /** 结构里的一格（坐标从 0 起：x 从西往东、y 从下往上、z 从北往南） */
    private record Cell(int x, int y, int z, Kind kind, int height, boolean anchor, @Nullable Component where) {
    }

    private static final int BAR_H = 13;
    private static final int BTN_GAP = 3;
    /** 模型区最高多少：最小的界面（虚拟高 240）里阅读栏约 185 高，模型区加顶栏要能一屏看全 */
    private static final int VIEW_MAX_H = 160;
    private static final int VIEW_MIN_H = 80;
    /** 一格方块最多画多大（小结构别放得太大） */
    private static final float BLOCK_MAX_PX = 20F;
    private static final float DEFAULT_YAW = -45F;
    private static final float DEFAULT_PITCH = 30F;
    /** 按下到松开移动不超过这么多（手册坐标）算点击 */
    private static final double CLICK_SLOP = 3D;
    private static final float DEG_PER_PX = 0.9F;

    /** 立体 / 平面：全手册共用，换一篇还是上次选的 */
    private static boolean flatMode;
    /** 展示厅截图用：上一帧画的模型区中心（手册坐标），假鼠标放这里拍悬停；没画过为 -1 */
    static volatile int showroomCenterX = -1;
    static volatile int showroomCenterY = -1;

    private final List<Cell> cells = new ArrayList<>();
    private final List<Kind> kinds = new ArrayList<>();
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    /** 层按钮（从下往上）：显示的文字、点了以后画到哪一个 y（含） */
    private final String[] labels;
    private final int[] labelW;
    private final int[] btnW;
    private final int[] layerY;
    private final Laid flat;
    private final Line[] note;
    private final Font font;

    // 排版结果
    private final String text3d;
    private final String text2d;
    private final String textAll;
    private final String textLayers;
    private final String textReset;
    private final String textHint;
    private final int w3d;
    private final int w2d;
    private final int wAll;
    private final int wLayers;
    private final int wReset;
    private final boolean layersOnSecondRow;
    private final int toolbarH;
    private final int viewH;
    private final float scale;
    private final int legendH;
    private final int bodyH;

    // 交互状态
    /** -1 = 全部；否则是 {@link #layers} 的下标 */
    private int cut = -1;
    private float yaw = DEFAULT_YAW;
    private float pitch = DEFAULT_PITCH;
    private boolean pressing;
    private boolean dragged;
    private double pressX;
    private double pressY;
    /** 按下的是哪一类：0 模型区，1 工具栏（立体 / 平面、层），2 交给平面图 */
    private int pressTarget;

    // 渲染缓存
    private final Map<BlockState, Boolean> emptyModel = new IdentityHashMap<>();
    private final Map<BlockState, BlockEntity> blockEntities = new IdentityHashMap<>();
    private final Map<Block, ItemStack> stacks = new IdentityHashMap<>();
    private static boolean warnedRenderer;

    private StructureView3D(List<Cell> cells, List<Kind> kinds, List<String> labels, int[] layerY, Laid flat, Line[] note, Ctx c) {
        this.cells.addAll(cells);
        this.kinds.addAll(kinds);
        int sx = 1;
        int sy = 1;
        int sz = 1;
        for (Cell cell : cells) {
            sx = Math.max(sx, cell.x() + 1);
            sy = Math.max(sy, cell.y() + cell.height());
            sz = Math.max(sz, cell.z() + 1);
        }
        this.sizeX = sx;
        this.sizeY = sy;
        this.sizeZ = sz;
        this.labels = labels.size() > 1 ? labels.toArray(new String[0]) : new String[0];
        this.layerY = layerY;
        this.flat = flat;
        this.note = note;
        this.font = c.text.font();
        int width = c.width;

        this.text3d = I18n.get("guide.essentialcraft5.structure.view_3d");
        this.text2d = I18n.get("guide.essentialcraft5.structure.view_2d");
        this.textAll = I18n.get("guide.essentialcraft5.structure.all_layers");
        this.textLayers = I18n.get("guide.essentialcraft5.ui.layers");
        this.textReset = I18n.get("guide.essentialcraft5.structure.reset");
        this.textHint = I18n.get("guide.essentialcraft5.structure.hint");
        this.w3d = font.width(text3d) + 10;
        this.w2d = font.width(text2d) + 10;
        this.wAll = font.width(textAll) + 8;
        this.wLayers = font.width(textLayers);
        this.wReset = font.width(textReset) + 8;
        this.labelW = new int[this.labels.length];
        this.btnW = new int[this.labels.length];
        for (int i = 0; i < this.labels.length; i++) {
            labelW[i] = font.width(this.labels[i]);
            btnW[i] = Math.max(15, labelW[i] + 8);
        }
        this.layersOnSecondRow = this.labels.length > 0 && w3d + w2d + 12 + layerBarWidth() > width;
        this.toolbarH = layersOnSecondRow ? BAR_H * 2 + 4 : BAR_H;

        // 比例：按初始角度（斜 30° 俯视）的投影尺寸放进模型区；转起来超出的部分被模型区裁掉
        double horiz = Math.sqrt((double) sizeX * sizeX + (double) sizeZ * sizeZ);
        double rad = Math.toRadians(DEFAULT_PITCH);
        double projH = sizeY * Math.cos(rad) + horiz * Math.sin(rad);
        float s = (float) Math.min((width - 16) / Math.max(1D, horiz), (VIEW_MAX_H - 24) / Math.max(1D, projH));
        this.scale = Math.max(4F, Math.min(BLOCK_MAX_PX, s));
        this.viewH = Mth.clamp((int) Math.ceil(projH * scale) + 28, VIEW_MIN_H, VIEW_MAX_H);

        this.legendH = layoutLegend(width);
        int body3d = viewH + (legendH > 0 ? 6 + legendH : 0);
        this.bodyH = Math.max(body3d, flat.height);
        this.height = toolbarH + 4 + bodyH + (note.length > 0 ? 4 + note.length * GuideTheme.LINE_H - GuideTheme.LINE_TAIL : 0);
    }

    // ---------------------------------------------------------------- 建模型

    /**
     * 手册 JSON 写的结构（单层或多层）。仪式图（有 throw）不走这里。
     *
     * @param labels 层按钮的文字（和平面图的层切换条一样）
     */
    static StructureView3D json(FigureSpec.Structure spec, List<String> labels, Laid flat, Line[] note, Ctx c) {
        List<FigureSpec.StructureLayer> layers = spec.layers();
        int anchorLayer = -1;
        if (spec.anchor() != null) {
            for (int l = 0; l < layers.size() && anchorLayer < 0; l++) {
                for (String row : layers.get(l).pattern()) {
                    if (row.indexOf(spec.anchor()) >= 0) {
                        anchorLayer = l;
                        break;
                    }
                }
            }
        }
        Map<FigureSpec.StructureKey, Kind> byKey = new LinkedHashMap<>();
        List<Cell> cells = new ArrayList<>();
        int[] layerY = new int[layers.size()];
        for (int l = 0; l < layers.size(); l++) {
            layerY[l] = l;
            FigureSpec.StructureLayer layer = layers.get(l);
            Component where = null;
            if (anchorLayer >= 0) {
                where = Component.translatable("guide.essentialcraft5.structure.height", signed(l - anchorLayer));
            } else if (layers.size() > 1) {
                where = Component.translatable("guide.essentialcraft5.structure.layer", l + 1, layers.size());
            }
            List<String> pattern = layer.pattern();
            for (int r = 0; r < pattern.size(); r++) {
                String row = pattern.get(r);
                for (int col = 0; col < row.length(); col++) {
                    char ch = row.charAt(col);
                    FigureSpec.StructureKey key = ch == ' ' ? null : layer.key().get(ch);
                    if (key == null) {
                        continue;
                    }
                    Kind kind = byKey.computeIfAbsent(key, StructureView3D::kind);
                    boolean anchor = spec.anchor() != null && ch == spec.anchor();
                    cells.add(new Cell(col, l, r, kind, height(kind), anchor, where));
                }
            }
        }
        return new StructureView3D(cells, ordered(cells), labels, layerY, flat, note, c);
    }

    /**
     * 代码里登记的结构。
     *
     * @param shownY 层按钮对应的层（StructurePattern 的 y，相对机器），和平面图的层切换条一一对应
     */
    static StructureView3D code(ResourceLocation id, StructurePattern pattern, List<String> labels, int[] shownY, Laid flat, Line[] note, Ctx c) {
        List<StructurePattern.Layer> layers = pattern.layers();
        int minY = layers.get(0).y();
        Map<StructurePattern.Rule, Kind> byRule = new IdentityHashMap<>();
        List<Cell> cells = new ArrayList<>();
        StructurePattern.Layer first = layers.get(0);
        int originX = first.anchorColumn();
        int originZ = first.anchorRow();
        boolean originRule = false;
        for (StructurePattern.Layer layer : layers) {
            Component where = Component.translatable("guide.essentialcraft5.structure.height", signed(layer.y()));
            for (int r = 0; r < layer.depth(); r++) {
                for (int col = 0; col < layer.width(); col++) {
                    StructurePattern.Rule rule = layer.at(col, r);
                    if (rule == null) {
                        continue;
                    }
                    Kind kind = byRule.computeIfAbsent(rule, StructureView3D::kind);
                    boolean anchor = layer.y() == 0 && col == originX && r == originZ;
                    originRule |= anchor;
                    cells.add(new Cell(col, layer.y() - minY, r, kind, height(kind), anchor, where));
                }
            }
        }
        if (!originRule) {
            // 机器那格没写规则：按结构 id 找同名的机器方块（玩家五芒星的各档去掉 _tierN），找不到画个记号
            Block machine = machineBlock(id);
            Component where = Component.translatable("guide.essentialcraft5.structure.height", signed(0));
            Kind kind = machine != null
                    ? new Kind(new BlockState[]{machine.defaultBlockState()}, false, false, false, null, machine.getName())
                    : new Kind(new BlockState[0], false, false, true, null, Component.translatable("guide.essentialcraft5.structure.anchor"));
            cells.add(new Cell(originX, -minY, originZ, kind, height(kind), true, where));
        }
        int[] layerY = new int[shownY.length];
        for (int i = 0; i < shownY.length; i++) {
            layerY[i] = shownY[i] - minY;
        }
        return new StructureView3D(cells, ordered(cells), labels, layerY, flat, note, c);
    }

    /** 图例顺序：机器本身在前，其余按从下往上、从北往南、从西往东第一次出现；顺带数每种几格 */
    private static List<Kind> ordered(List<Cell> cells) {
        List<Kind> out = new ArrayList<>();
        for (Cell cell : cells) {
            if (cell.anchor() && !out.contains(cell.kind())) {
                out.add(cell.kind());
            }
        }
        List<Cell> sorted = new ArrayList<>(cells);
        sorted.sort((a, b) -> a.y() != b.y() ? Integer.compare(a.y(), b.y()) : a.z() != b.z() ? Integer.compare(a.z(), b.z())
                : Integer.compare(a.x(), b.x()));
        for (Cell cell : sorted) {
            if (!out.contains(cell.kind())) {
                out.add(cell.kind());
            }
            cell.kind().count++;
        }
        return out;
    }

    @Nullable
    private static Block machineBlock(ResourceLocation id) {
        for (String path : new String[]{id.getPath(), id.getPath().replaceAll("_tier\\d+$", "")}) {
            ResourceLocation key = new ResourceLocation(id.getNamespace(), path);
            if (ForgeRegistries.BLOCKS.containsKey(key)) {
                return ForgeRegistries.BLOCKS.getValue(key);
            }
        }
        return null;
    }

    /** 三格高的方块（水晶柱）画出来占三格：取方块、描框都按三格算 */
    private static int height(Kind kind) {
        for (BlockState state : kind.states) {
            if (state.getBlock() instanceof TripleTallBlock) {
                return 3;
            }
        }
        return 1;
    }

    /** JSON 的格子定义 → 一种格子（同平面图的 Figures.Layout#resolve） */
    private static Kind kind(FigureSpec.StructureKey key) {
        Component label = key.label() == null ? null : Component.literal(Language.getInstance().getOrDefault(key.label()));
        if (key.air()) {
            return new Kind(new BlockState[0], true, false, false, label, Component.translatable("guide.essentialcraft5.structure.air"));
        }
        List<BlockState> states = new ArrayList<>();
        for (ResourceLocation id : key.blocks()) {
            if (ForgeRegistries.BLOCKS.containsKey(id)) {
                states.add(ForgeRegistries.BLOCKS.getValue(id).defaultBlockState());
            }
        }
        ITagManager<Block> tags = ForgeRegistries.BLOCKS.tags();
        if (key.tag() != null && tags != null) {
            for (Block block : tags.getTag(tags.createTagKey(key.tag()))) {
                states.add(block.defaultBlockState());
            }
        }
        Component name = states.isEmpty() ? Component.literal(key.tag() != null ? "#" + key.tag() : "?") : states.get(0).getBlock().getName();
        return new Kind(states.toArray(new BlockState[0]), false, false, false, label, name);
    }

    /** 代码里的规则 → 一种格子（同平面图的 Figures.Layout#resolve） */
    private static Kind kind(StructurePattern.Rule rule) {
        boolean optional = rule.isOptional();
        Component name;
        Component label = null;
        BlockState[] states;
        if (rule.isAir()) {
            states = new BlockState[0];
            name = rule.legendKey() != null ? rule.legend() : Component.translatable("guide.essentialcraft5.structure.air");
        } else {
            List<Block> blocks = rule.displayBlocks();
            if (blocks.size() == 1) {
                states = new BlockState[]{rule.displayState()};
            } else {
                states = new BlockState[blocks.size()];
                for (int i = 0; i < states.length; i++) {
                    states[i] = blocks.get(i).defaultBlockState();
                }
            }
            name = rule.legend();
            if (!rule.usesBlockName()) {
                label = name;
            }
        }
        if (optional) {
            name = Component.translatable("guide.essentialcraft5.structure.optional_entry", name);
        }
        return new Kind(states, rule.isAir(), optional, false, label, name);
    }

    private static String signed(int y) {
        return y > 0 ? "+" + y : String.valueOf(y);
    }

    // ---------------------------------------------------------------- 排版

    private int layerBarWidth() {
        if (labels.length == 0) {
            return 0;
        }
        int w = wLayers + 6 + wAll + BTN_GAP;
        for (int bw : btnW) {
            w += bw + BTN_GAP;
        }
        return w - BTN_GAP;
    }

    /** 图例：小图标 + 名字 + 「×N」，一行放不下换行；名字比整行还宽时自己折行 */
    private int layoutLegend(int width) {
        int lx = 0;
        int ly = 0;
        int rowH = Figures.LINE_H;
        int textMax = Math.max(1, width - 10 - 20);
        for (Kind kind : kinds) {
            Component full = countText(kind);
            List<FormattedCharSequence> lines = new ArrayList<>();
            int textW = font.width(full);
            if (textW <= textMax) {
                lines.add(full.getVisualOrderText());
            } else {
                textW = 0;
                for (String line : new GuideTextLayout(font).wrapPlain(full.getString(), textMax, 8)) {
                    lines.add(FormattedCharSequence.forward(line, Style.EMPTY));
                    textW = Math.max(textW, font.width(line));
                }
            }
            int itemW = 10 + textW;
            if (lx > 0 && lx + itemW > width) {
                lx = 0;
                ly += rowH;
                rowH = Figures.LINE_H;
            }
            kind.lines = List.copyOf(lines);
            kind.lx = lx;
            kind.ly = ly;
            kind.lw = itemW;
            rowH = Math.max(rowH, Math.max(1, lines.size()) * Figures.LINE_H);
            lx += itemW + 8;
        }
        return kinds.isEmpty() ? 0 : ly + rowH;
    }

    private static Component countText(Kind kind) {
        if (kind.air || kind.marker) {
            return kind.name;
        }
        return Component.empty().append(kind.name).append(" ")
                .append(Component.translatable("guide.essentialcraft5.structure.count", kind.count).withStyle(s -> s.withColor(GuideTheme.GLOW & 0xFFFFFF)));
    }

    // ---------------------------------------------------------------- 展示厅

    /** 展示厅截图用：只画到第 layer 个层按钮（从下往上，0 起；负数 = 全部）；平面图同时切到那一层 */
    void select(int layer) {
        cut = layer < 0 || labels.length == 0 ? -1 : Math.min(labels.length - 1, layer);
        if (flat instanceof FigureBlocks.LayeredLaid layered && layer >= 0) {
            layered.select(layer);
        }
    }

    /** 展示厅截图用：转到给定角度 */
    void showroomAngles(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = Mth.clamp(pitch, -20F, 90F);
    }

    /** 展示厅截图用：全手册切到平面图 / 立体图 */
    static void showroomFlat(boolean flat) {
        flatMode = flat;
    }

    // ---------------------------------------------------------------- 工具栏的位置

    private int toggleX(int x) {
        return x;
    }

    private int layerBarX(int x, int w) {
        return layersOnSecondRow ? x + (w - layerBarWidth()) / 2 : x + w - layerBarWidth();
    }

    private int layerBarY(int y) {
        return layersOnSecondRow ? y + BAR_H + 4 : y;
    }

    /** 层按钮命中：-2 没命中，-1「全部」，其余是层下标 */
    private int layerButtonAt(double mx, double my, int x, int y, int w) {
        if (labels.length == 0) {
            return -2;
        }
        int by = layerBarY(y);
        if (my < by || my >= by + BAR_H) {
            return -2;
        }
        int bx = layerBarX(x, w) + wLayers + 6;
        if (mx >= bx && mx < bx + wAll) {
            return -1;
        }
        bx += wAll + BTN_GAP;
        for (int i = 0; i < btnW.length; i++) {
            if (mx >= bx && mx < bx + btnW[i]) {
                return i;
            }
            bx += btnW[i] + BTN_GAP;
        }
        return -2;
    }

    /** 立体 / 平面切换命中：0 立体，1 平面，-1 没命中 */
    private int toggleAt(double mx, double my, int x, int y) {
        if (my < y || my >= y + BAR_H) {
            return -1;
        }
        int bx = toggleX(x);
        if (mx >= bx && mx < bx + w3d) {
            return 0;
        }
        if (mx >= bx + w3d && mx < bx + w3d + w2d) {
            return 1;
        }
        return -1;
    }

    private boolean rotated() {
        return yaw != DEFAULT_YAW || pitch != DEFAULT_PITCH;
    }

    private boolean overReset(double mx, double my, int x, int top, int w) {
        return rotated() && my >= top + 3 && my < top + 3 + 11 && mx >= x + w - 3 - wReset && mx < x + w - 3;
    }

    // ---------------------------------------------------------------- 鼠标

    @Override
    boolean mouseClicked(double mx, double my, int button, int x, int y, int w) {
        int top = y + toolbarH + 4;
        if (button != 0) {
            return flatMode && flat.mouseClicked(mx, my, button, x, top, w);
        }
        int toggle = toggleAt(mx, my, x, y);
        if (toggle >= 0) {
            if ((toggle == 1) != flatMode) {
                flatMode = toggle == 1;
                click();
            }
            pressTarget = 1;
            return true;
        }
        if (flatMode) {
            pressTarget = 2;
            return flat.mouseClicked(mx, my, button, x, top, w);
        }
        int layer = layerButtonAt(mx, my, x, y, w);
        if (layer > -2) {
            if (layer != cut) {
                cut = layer;
                click();
            }
            pressTarget = 1;
            return true;
        }
        if (overReset(mx, my, x, top, w)) {
            yaw = DEFAULT_YAW;
            pitch = DEFAULT_PITCH;
            click();
            pressTarget = 1;
            return true;
        }
        if (mx >= x && mx < x + w && my >= top && my < top + viewH) {
            pressing = true;
            dragged = false;
            pressX = mx;
            pressY = my;
            pressTarget = 0;
            return true;
        }
        return false;
    }

    @Override
    boolean mouseDragged(double mx, double my, int button, double dx, double dy, int x, int y, int w) {
        if (pressTarget == 2) {
            return flat.mouseDragged(mx, my, button, dx, dy, x, y + toolbarH + 4, w);
        }
        if (!pressing || button != 0) {
            return pressTarget == 1;
        }
        if (!dragged && Math.abs(mx - pressX) + Math.abs(my - pressY) > CLICK_SLOP) {
            dragged = true;
        }
        if (dragged) {
            yaw = Mth.wrapDegrees(yaw + (float) dx * DEG_PER_PX);
            pitch = Mth.clamp(pitch + (float) dy * DEG_PER_PX, -20F, 90F);
        }
        return true;
    }

    @Override
    boolean mouseReleased(double mx, double my, int button, int x, int y, int w) {
        if (pressTarget == 2) {
            return flat.mouseReleased(mx, my, button, x, y + toolbarH + 4, w);
        }
        boolean click = pressing && !dragged;
        pressing = false;
        dragged = false;
        return click;
    }

    @Override
    boolean mouseScrolled(double mx, double my, double delta, int x, int y, int w) {
        if (flatMode) {
            return flat.mouseScrolled(mx, my, delta, x, y + toolbarH + 4, w);
        }
        // 只在层按钮那一排上滚轮切层：压在模型上滚轮照常滚正文
        int by = layerBarY(y);
        if (labels.length == 0 || my < by || my >= by + BAR_H || mx < x || mx >= x + w) {
            return false;
        }
        // 按钮从左到右是「全部」、最下层……最上层；滚轮往上往高层走，越过最上层就是「全部」
        int position = cut < 0 ? labels.length : cut;
        position = Mth.clamp(position + (delta > 0 ? 1 : -1), 0, labels.length);
        cut = position == labels.length ? -1 : position;
        return true;
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.2F));
    }

    // ---------------------------------------------------------------- 画

    @Override
    void render(GuideRender r, int x, int y, int w) {
        if (r.visible(y, toolbarH)) {
            renderToolbar(r, x, y, w);
        }
        int top = y + toolbarH + 4;
        if (flatMode) {
            flat.render(r, x, top, w);
        } else if (r.visible(top, viewH + (legendH > 0 ? 6 + legendH : 0))) {
            render3d(r, x, top, w);
        }
        r.lines(note, x, top + bodyH + 4, GuideTheme.LINE_H, false);
    }

    private void renderToolbar(GuideRender r, int x, int y, int w) {
        int bx = toggleX(x);
        int hoverToggle = r.mouseActive ? toggleAt(r.mouseX, r.mouseY, x, y) : -1;
        button(r, bx, y, w3d, text3d, !flatMode, hoverToggle == 0);
        button(r, bx + w3d, y, w2d, text2d, flatMode, hoverToggle == 1);
        if (flatMode || labels.length == 0) {
            return;
        }
        int ly = layerBarY(y);
        int lx = layerBarX(x, w);
        int hover = r.mouseActive ? layerButtonAt(r.mouseX, r.mouseY, x, y, w) : -2;
        r.g.drawString(r.font, textLayers, lx, ly + 3, r.text(GuideTheme.MIST), false);
        lx += wLayers + 6;
        button(r, lx, ly, wAll, textAll, cut < 0, hover == -1);
        lx += wAll + BTN_GAP;
        for (int i = 0; i < labels.length; i++) {
            button(r, lx, ly, btnW[i], labels[i], cut == i, hover == i);
            lx += btnW[i] + BTN_GAP;
        }
    }

    /** 按钮：选中的暗品红底品红框，悬停描品红框，按住时暗品红底（和平面图的层切换条一样） */
    private static void button(GuideRender r, int bx, int y, int bw, String label, boolean on, boolean over) {
        GuiGraphics g = r.g;
        CodexTheme.chamferFill(g, bx, y, bw, BAR_H, 2,
                r.fill(over && r.mouseDown ? GuideTheme.TILE_BG_PRESS
                        : on ? GuideTheme.withAlpha(GuideTheme.FOCUS_DEEP, 0x70) : GuideTheme.TILE_BG));
        CodexTheme.chamferOutline(g, bx, y, bw, BAR_H, 2,
                r.fill(on ? GuideTheme.FOCUS : over ? GuideTheme.withAlpha(GuideTheme.FOCUS, 0xA0) : GuideTheme.withAlpha(GuideTheme.EDGE, 0xC0)));
        g.drawString(r.font, label, bx + (bw - r.font.width(label)) / 2, y + 3, r.text(on || over ? GuideTheme.PEARL : GuideTheme.MIST), false);
    }

    private int cutY() {
        return cut < 0 || cut >= layerY.length ? Integer.MAX_VALUE : layerY[cut];
    }

    private boolean shown(Cell cell) {
        return cell.y() <= cutY();
    }

    private void render3d(GuideRender r, int x, int top, int w) {
        GuiGraphics g = r.g;
        boolean overView = r.over(x, top, w, viewH);
        GuideFrame f = r.figureFrame();
        f.cycling = overView || r.over(x, top + viewH + 6, w, legendH);

        // 模型区的底：比阅读栏略深的磨砂底 + 细框
        CodexTheme.chamferFill(g, x, top, w, viewH, 3, r.fill(GuideTheme.withAlpha(GuideTheme.TILE_BG, 0x9C)));
        CodexTheme.chamferOutline(g, x, top, w, viewH, 3, r.fill(GuideTheme.withAlpha(GuideTheme.EDGE, 0x90)));

        // 悬停：图例里的一种（模型里这种全描框），或模型里鼠标下最靠前的一格
        Kind hoverKind = null;
        int legendTop = top + viewH + 6;
        if (legendH > 0 && r.mouseActive) {
            for (Kind kind : kinds) {
                if (r.over(x + kind.lx, legendTop + kind.ly, kind.lw, Math.max(1, kind.lines.size()) * Figures.LINE_H)) {
                    hoverKind = kind;
                }
            }
        }
        float cx = x + w / 2F;
        float cy = top + viewH / 2F;
        showroomCenterX = (int) cx;
        showroomCenterY = (int) cy;
        Matrix3f rot = new Matrix3f().rotateX((float) Math.toRadians(pitch)).rotateY((float) Math.toRadians(yaw));
        Cell hover = overView && !(pressing && dragged) && !overReset(r.mouseX, r.mouseY, x, top, w)
                ? pick(r.mouseX, r.mouseY, cx, cy, rot) : null;

        if (r.solidReady()) {
            g.flush();
            g.enableScissor(x + 1, top + 1, x + w - 1, top + viewH - 1);
            // 模型区里的深度先清掉（前面画的底和正文不挡方块），画完再清一次（后面画的东西不被方块挡）
            clearDepth();
            drawModel(g, f, cx, cy);
            drawOutlines(g, cx, cy, rot, hover, hoverKind);
            clearDepth();
            g.disableScissor();
        }

        if (hover != null) {
            BlockState shown = display(f, hover.kind());
            ItemStack stack = shown == null ? ItemStack.EMPTY : stack(shown.getBlock());
            f.hotspot(r.mouseX, r.mouseY, 1, 1, stack, tooltip(hover, shown, stack.isEmpty()));
        }

        // 提示和复位：画在模型上面，字压暗一点
        g.drawString(r.font, textHint, x + 5, top + viewH - 12, r.text(GuideTheme.withAlpha(GuideTheme.MIST, 0x90)), false);
        if (rotated()) {
            boolean over = overReset(r.mouseX, r.mouseY, x, top, w) && r.mouseActive;
            int rx = x + w - 3 - wReset;
            CodexTheme.chamferFill(g, rx, top + 3, wReset, 11, 2, r.fill(over ? GuideTheme.withAlpha(GuideTheme.FOCUS_DEEP, 0x70) : GuideTheme.TILE_BG));
            CodexTheme.chamferOutline(g, rx, top + 3, wReset, 11, 2,
                    r.fill(over ? GuideTheme.FOCUS : GuideTheme.withAlpha(GuideTheme.EDGE, 0xC0)));
            g.drawString(r.font, textReset, rx + 4, top + 5, r.text(over ? GuideTheme.PEARL : GuideTheme.MIST), false);
        }

        // 图例
        for (Kind kind : kinds) {
            int lx = x + kind.lx;
            int ly = legendTop + kind.ly;
            if (kind.air) {
                dashed(f, lx, ly, 8);
            } else if (kind.marker) {
                g.fill(lx + 1, ly + 1, lx + 7, ly + 7, f.fill(GuideTheme.withAlpha(GuideTheme.FOCUS, 0x90)));
            } else if (kind.states.length > 0) {
                BlockState state = display(f, kind);
                if (state != null) {
                    f.smallBlock(state.getBlock(), lx, ly, 0.5F);
                }
            }
            int lineY = ly;
            for (FormattedCharSequence line : kind.lines) {
                g.drawString(r.font, line, lx + 10, lineY, f.text(kind == hoverKind ? GuideTheme.PEARL : GuideTheme.TEXT_DIM), false);
                lineY += Figures.LINE_H;
            }
            if (kind == hoverKind) {
                BlockState state = display(f, kind);
                ItemStack stack = state == null ? ItemStack.EMPTY : stack(state.getBlock());
                List<Component> extra = new ArrayList<>();
                if (state != null && stack.isEmpty()) {
                    extra.add(state.getBlock().getName());
                }
                if (kind.label != null) {
                    extra.add(kind.label);
                }
                if (kind.states.length > 1) {
                    extra.add(Component.translatable("guide.essentialcraft5.recipe.alternatives", kind.states.length));
                }
                f.hotspot(x + kind.lx, ly, kind.lw, Math.max(1, kind.lines.size()) * Figures.LINE_H, stack, extra);
            }
        }
        r.collectHotspots(f);
    }

    private static void clearDepth() {
        RenderSystem.depthMask(true);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    /** 这一格现在画哪个方块状态（几种任选时跟着轮换）；空气、记号为 null */
    @Nullable
    private static BlockState display(GuideFrame f, Kind kind) {
        if (kind.states.length == 0) {
            return null;
        }
        return kind.states[f.cycleIndex(kind.states.length)];
    }

    private ItemStack stack(Block block) {
        return stacks.computeIfAbsent(block, b -> b.asItem() == Items.AIR ? ItemStack.EMPTY : new ItemStack(b));
    }

    /** 悬停提示：和平面图一样的附注，最后一行写高度 / 第几层 */
    private static List<Component> tooltip(Cell cell, @Nullable BlockState shown, boolean nameless) {
        Kind kind = cell.kind();
        List<Component> lines = new ArrayList<>();
        if (kind.air || kind.marker) {
            lines.add(kind.name);
        } else if (shown != null && nameless) {
            lines.add(shown.getBlock().getName());
        }
        if (kind.label != null) {
            lines.add(kind.label);
        }
        if (kind.optional) {
            lines.add(Component.translatable("guide.essentialcraft5.structure.optional"));
        }
        if (kind.states.length > 1) {
            lines.add(Component.translatable("guide.essentialcraft5.recipe.alternatives", kind.states.length));
        }
        if (cell.anchor() && !kind.marker) {
            lines.add(Component.translatable("guide.essentialcraft5.structure.anchor"));
        }
        if (cell.where() != null) {
            lines.add(cell.where());
        }
        return lines;
    }

    // ---------------------------------------------------------------- 三维

    /** 和 {@link #drawModel} 用同一套变换：结构坐标 → 手册坐标（z 朝向读者为正） */
    private Vector3f project(float px, float py, float pz, float cx, float cy, Matrix3f rot) {
        Vector3f v = new Vector3f(px - sizeX / 2F, py - sizeY / 2F, pz - sizeZ / 2F);
        rot.transform(v);
        return new Vector3f(cx + v.x * scale, cy - v.y * scale, v.z);
    }

    /** 鼠标下最靠前的一格：屏幕上那一点沿视线反投回结构坐标，和每一格的方块做射线求交 */
    @Nullable
    private Cell pick(double mx, double my, float cx, float cy, Matrix3f rot) {
        Matrix3f inv = rot.transpose(new Matrix3f());
        Vector3f origin = new Vector3f((float) ((mx - cx) / scale), (float) (-(my - cy) / scale), 1000F);
        Vector3f dir = new Vector3f(0F, 0F, -1F);
        inv.transform(origin);
        inv.transform(dir);
        origin.add(sizeX / 2F, sizeY / 2F, sizeZ / 2F);
        // 实心格取最靠前的；空气格是透明的，只在视线上没有实心格时才算
        Cell best = null;
        Cell bestAir = null;
        float bestT = Float.MAX_VALUE;
        float bestAirT = Float.MAX_VALUE;
        for (Cell cell : cells) {
            if (!shown(cell)) {
                continue;
            }
            float t = hit(origin, dir, cell.x(), cell.y(), cell.z(), cell.x() + 1, cell.y() + cell.height(), cell.z() + 1);
            if (t < 0F) {
                continue;
            }
            if (cell.kind().air) {
                if (t < bestAirT) {
                    bestAir = cell;
                    bestAirT = t;
                }
            } else if (t < bestT) {
                best = cell;
                bestT = t;
            }
        }
        return best != null ? best : bestAir;
    }

    /** 射线和轴对齐方块的交点参数（进入点），不相交为 -1 */
    private static float hit(Vector3f o, Vector3f d, float x0, float y0, float z0, float x1, float y1, float z1) {
        float tMin = -Float.MAX_VALUE;
        float tMax = Float.MAX_VALUE;
        float[] origin = {o.x, o.y, o.z};
        float[] dir = {d.x, d.y, d.z};
        float[] lo = {x0, y0, z0};
        float[] hi = {x1, y1, z1};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(dir[i]) < 1.0E-6F) {
                if (origin[i] < lo[i] || origin[i] > hi[i]) {
                    return -1F;
                }
                continue;
            }
            float a = (lo[i] - origin[i]) / dir[i];
            float b = (hi[i] - origin[i]) / dir[i];
            tMin = Math.max(tMin, Math.min(a, b));
            tMax = Math.min(tMax, Math.max(a, b));
        }
        return tMax >= tMin && tMax >= 0F ? Math.max(0F, tMin) : -1F;
    }

    private void drawModel(GuiGraphics g, GuideFrame f, float cx, float cy) {
        Minecraft mc = Minecraft.getInstance();
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 150F);
        pose.scale(scale, -scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.translate(-sizeX / 2F, -sizeY / 2F, -sizeZ / 2F);
        Lighting.setupFor3DItems();
        MultiBufferSource.BufferSource buffers = g.bufferSource();
        MultiBufferSource ghost = type -> new AlphaConsumer(buffers.getBuffer(ghostType(type)), 0.45F);
        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        // 先画实心的，再画半透明的可选格子。每一遍先画全部方块模型，再从远到近画方块实体渲染器那部分（见 farToNear）
        List<Cell> ordered = farToNear();
        for (int pass = 0; pass < 2; pass++) {
            MultiBufferSource target = pass == 1 ? ghost : buffers;
            for (int stage = 0; stage < 2; stage++) {
                for (Cell cell : ordered) {
                    if (!shown(cell) || cell.kind().air || cell.kind().marker || cell.kind().optional != (pass == 1)) {
                        continue;
                    }
                    BlockState state = display(f, cell.kind());
                    if (state == null) {
                        continue;
                    }
                    pose.pushPose();
                    pose.translate(cell.x(), cell.y(), cell.z());
                    if (stage == 0) {
                        drawBlock(dispatcher, state, pose, target);
                    } else if (drawBlockEntityPart(dispatcher, state, pose, target)) {
                        // 每格画完就交出去，前后才是从远到近
                        buffers.endBatch();
                    }
                    pose.popPose();
                }
                buffers.endBatch();
            }
        }
        // 「必须是空的」和机器记号：透明的方块，最后画
        VertexConsumer quads = buffers.getBuffer(RenderType.debugQuads());
        Matrix4f m = pose.last().pose();
        for (Cell cell : cells) {
            if (shown(cell) && (cell.kind().air || cell.kind().marker)) {
                int color = cell.kind().marker ? GuideTheme.withAlpha(GuideTheme.FOCUS, 0x55) : GuideTheme.withAlpha(GuideTheme.GLOW, 0x1C);
                tintedCube(quads, m, cell.x() + 0.06F, cell.y() + 0.06F, cell.z() + 0.06F, cell.x() + 0.94F, cell.y() + 0.94F, cell.z() + 0.94F, color);
            }
        }
        buffers.endBatch();
        pose.popPose();
    }

    /**
     * 格子按离镜头从远到近排。方块模型用的方块图集类型在 GuiGraphics 缓冲里是固定缓冲，要到 endBatch 才画，
     * 而水晶柱的信标光柱类型不写深度：混在一起画时方块模型总是最后上屏，把挡在它前面的柱子也盖住（南边的柱子跑到机器后面去）。
     * 所以先画全部方块模型，再按这个顺序一格一格画方块实体渲染器那部分，每格画完就交出去。
     * 变换和 {@link #drawModel} 一致：先转偏航再转俯仰，z 越大离屏幕越近。
     */
    private List<Cell> farToNear() {
        Matrix4f view = new Matrix4f().rotateX(pitch * Mth.DEG_TO_RAD).rotateY(yaw * Mth.DEG_TO_RAD);
        Map<Cell, Float> depth = new IdentityHashMap<>();
        Vector3f center = new Vector3f();
        for (Cell cell : cells) {
            center.set(cell.x() + 0.5F - sizeX / 2F, cell.y() + cell.height() / 2F - sizeY / 2F, cell.z() + 0.5F - sizeZ / 2F);
            depth.put(cell, view.transformPosition(center).z);
        }
        List<Cell> sorted = new ArrayList<>(cells);
        sorted.sort(Comparator.comparingDouble(depth::get));
        return sorted;
    }

    /** 可选格子的半透明：方块图集的实心 / 镂空类型换成半透明，别的照旧（透明度由 {@link AlphaConsumer} 乘上去） */
    private static RenderType ghostType(RenderType type) {
        if (type == Sheets.solidBlockSheet() || type == Sheets.cutoutBlockSheet()) {
            return Sheets.translucentCullBlockSheet();
        }
        return type;
    }

    private void drawBlock(BlockRenderDispatcher dispatcher, BlockState state, PoseStack pose, MultiBufferSource buffers) {
        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty() && state.getRenderShape() != RenderShape.MODEL) {
            fluidCube(fluid, pose, buffers);
            return;
        }
        RenderShape shape = state.getRenderShape();
        if (shape == RenderShape.MODEL) {
            BakedModel model = dispatcher.getBlockModel(state);
            int color = Minecraft.getInstance().getBlockColors().getColor(state, null, null, 0);
            float red = (color >> 16 & 0xFF) / 255F;
            float green = (color >> 8 & 0xFF) / 255F;
            float blue = (color & 0xFF) / 255F;
            // 同 renderSingleBlock，但半透明一律用剔除背面的那种：不剔除的那种在「极佳」画质下画进物品实体的缓冲，界面里看不见
            for (RenderType type : model.getRenderTypes(state, RandomSource.create(42L), ModelData.EMPTY)) {
                dispatcher.getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderTypeHelper.getEntityRenderType(type, true)),
                        state, model, red, green, blue, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, type);
            }
        } else if (shape == RenderShape.ENTITYBLOCK_ANIMATED) {
            dispatcher.renderSingleBlock(state, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        }
    }

    /** 样子（全部或一部分）靠方块实体渲染器画的方块：画那一部分，画了返回 true。在全部方块模型画完之后调，见 {@link #drawModel} */
    private boolean drawBlockEntityPart(BlockRenderDispatcher dispatcher, BlockState state, PoseStack pose, MultiBufferSource buffers) {
        RenderShape shape = state.getRenderShape();
        if (!(state.getBlock() instanceof EntityBlock) || shape == RenderShape.MODEL && !emptyModel(dispatcher, state)) {
            return false;
        }
        drawBlockEntity(state, pose, buffers);
        return true;
    }

    private boolean emptyModel(BlockRenderDispatcher dispatcher, BlockState state) {
        return emptyModel.computeIfAbsent(state, s -> {
            BakedModel model = dispatcher.getBlockModel(s);
            RandomSource random = RandomSource.create(42L);
            if (!model.getQuads(s, null, random, ModelData.EMPTY, null).isEmpty()) {
                return false;
            }
            for (Direction side : Direction.values()) {
                if (!model.getQuads(s, side, random, ModelData.EMPTY, null).isEmpty()) {
                    return false;
                }
            }
            return true;
        });
    }

    /** 样子靠方块实体渲染器画的方块：优先画它给的静止一帧，没有就照常调渲染器（要有世界） */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void drawBlockEntity(BlockState state, PoseStack pose, MultiBufferSource buffers) {
        Minecraft mc = Minecraft.getInstance();
        BlockEntity be = blockEntities.computeIfAbsent(state, s -> ((EntityBlock) s.getBlock()).newBlockEntity(BlockPos.ZERO, s));
        if (be == null) {
            return;
        }
        try {
            BlockEntityRenderer renderer = mc.getBlockEntityRenderDispatcher().getRenderer(be);
            if (renderer instanceof StaticBlockPreview preview) {
                preview.renderPreview(state, pose, buffers);
            } else if (renderer != null && mc.level != null) {
                if (be.getLevel() == null) {
                    be.setLevel(mc.level);
                }
                renderer.render(be, 0F, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            }
        } catch (RuntimeException e) {
            blockEntities.put(state, null);
            if (!warnedRenderer) {
                warnedRenderer = true;
                EssentialCraft5.LOGGER.warn("[指南] 立体结构图画不了 {} 的方块实体，跳过", ForgeRegistries.BLOCKS.getKey(state.getBlock()), e);
            }
        }
    }

    /** 流体：贴着静止流体贴图、染上流体颜色的方块（高 14/16，和世界里的水面差不多） */
    private static void fluidCube(FluidState fluid, PoseStack pose, MultiBufferSource buffers) {
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid);
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ext.getStillTexture());
        int tint = ext.getTintColor();
        VertexConsumer consumer = buffers.getBuffer(Sheets.translucentCullBlockSheet());
        PoseStack.Pose last = pose.last();
        float top = 14F / 16F;
        float[][] faces = {
                // 每个面四个角（逆时针看向面外），后三个数是法线
                {0, top, 0, 0, top, 1, 1, top, 1, 1, top, 0, 0, 1, 0},
                {0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, 0, -1, 0},
                {0, 0, 0, 0, top, 0, 1, top, 0, 1, 0, 0, 0, 0, -1},
                {1, 0, 1, 1, top, 1, 0, top, 1, 0, 0, 1, 0, 0, 1},
                {0, 0, 1, 0, top, 1, 0, top, 0, 0, 0, 0, -1, 0, 0},
                {1, 0, 0, 1, top, 0, 1, top, 1, 1, 0, 1, 1, 0, 0},
        };
        float[][] uv = {{sprite.getU0(), sprite.getV0()}, {sprite.getU0(), sprite.getV1()}, {sprite.getU1(), sprite.getV1()}, {sprite.getU1(), sprite.getV0()}};
        int a = tint >>> 24 == 0 ? 0xFF : tint >>> 24;
        int r = tint >> 16 & 0xFF;
        int g = tint >> 8 & 0xFF;
        int b = tint & 0xFF;
        for (float[] face : faces) {
            // 两种绕向各画一遍：剔除背面以后从外面看总有一遍是正面
            for (int pass = 0; pass < 2; pass++) {
                for (int k = 0; k < 4; k++) {
                    int i = pass == 0 ? k : 3 - k;
                    consumer.vertex(last.pose(), face[i * 3], face[i * 3 + 1], face[i * 3 + 2]).color(r, g, b, a).uv(uv[i][0], uv[i][1])
                            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                            .normal(last.normal(), face[12], face[13], face[14]).endVertex();
                }
            }
        }
    }

    /** 纯色的透明方块（空气、记号）；不剔除背面，所以绕向无所谓 */
    private static void tintedCube(VertexConsumer c, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, int argb) {
        int a = argb >>> 24;
        int r = argb >> 16 & 0xFF;
        int g = argb >> 8 & 0xFF;
        int b = argb & 0xFF;
        float[][] faces = {
                {x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0},
                {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1},
                {x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0},
                {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1},
                {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0},
                {x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1},
        };
        for (float[] face : faces) {
            for (int k = 0; k < 4; k++) {
                c.vertex(m, face[k * 3], face[k * 3 + 1], face[k * 3 + 2]).color(r, g, b, a).endVertex();
            }
        }
    }

    /** 描框：把格子的 8 个角投到屏幕上，12 条棱画成 1 像素的线，压在模型上面（不做深度测试） */
    private void drawOutlines(GuiGraphics g, float cx, float cy, Matrix3f rot, @Nullable Cell hover, @Nullable Kind hoverKind) {
        if (hover == null && hoverKind == null) {
            return;
        }
        VertexConsumer c = g.bufferSource().getBuffer(RenderType.guiOverlay());
        Matrix4f m = g.pose().last().pose();
        if (hoverKind != null) {
            for (Cell cell : cells) {
                if (cell.kind() == hoverKind && shown(cell)) {
                    outline(c, m, cell, cx, cy, rot, GuideTheme.withAlpha(GuideTheme.FOCUS, 0xC0));
                }
            }
        }
        if (hover != null) {
            outline(c, m, hover, cx, cy, rot, GuideTheme.FOCUS);
        }
        g.flush();
    }

    private static final int[][] EDGES = {
            {0, 1}, {1, 3}, {3, 2}, {2, 0}, {4, 5}, {5, 7}, {7, 6}, {6, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};

    private void outline(VertexConsumer c, Matrix4f m, Cell cell, float cx, float cy, Matrix3f rot, int argb) {
        Vector3f[] p = new Vector3f[8];
        for (int i = 0; i < 8; i++) {
            p[i] = project(cell.x() + (i & 1), cell.y() + ((i >> 1) & 1) * cell.height(), cell.z() + ((i >> 2) & 1), cx, cy, rot);
        }
        int a = argb >>> 24;
        int r = argb >> 16 & 0xFF;
        int gr = argb >> 8 & 0xFF;
        int b = argb & 0xFF;
        for (int[] e : EDGES) {
            Vector3f s = p[e[0]];
            Vector3f t = p[e[1]];
            float dx = t.x - s.x;
            float dy = t.y - s.y;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 1.0E-3F) {
                continue;
            }
            float nx = -dy / len * 0.5F;
            float ny = dx / len * 0.5F;
            float[][] q = {{s.x + nx, s.y + ny}, {s.x - nx, s.y - ny}, {t.x - nx, t.y - ny}, {t.x + nx, t.y + ny}};
            // 两种绕向各一遍，免得被剔掉
            for (int pass = 0; pass < 2; pass++) {
                for (int k = 0; k < 4; k++) {
                    int i = pass == 0 ? k : 3 - k;
                    c.vertex(m, q[i][0], q[i][1], 0F).color(r, gr, b, a).endVertex();
                }
            }
        }
    }

    /** 「这一格必须是空的」的图例：虚线方框（同平面图） */
    private static void dashed(GuideFrame f, int x, int y, int size) {
        int color = f.fill(GuideTheme.TEXT_DIM);
        for (int i = 0; i < size; i += 3) {
            int end = Math.min(size, i + 2);
            f.g.fill(x + i, y, x + end, y + 1, color);
            f.g.fill(x + i, y + size - 1, x + end, y + size, color);
            f.g.fill(x, y + i, x + 1, y + end, color);
            f.g.fill(x + size - 1, y + i, x + size, y + end, color);
        }
    }

    /** 把透明度乘上一个系数（可选格子画半透明） */
    private static final class AlphaConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final float alpha;

        AlphaConsumer(VertexConsumer delegate, float alpha) {
            this.delegate = delegate;
            this.alpha = alpha;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            delegate.color(r, g, b, (int) (a * alpha));
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            delegate.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            delegate.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            delegate.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            delegate.endVertex();
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
            delegate.defaultColor(r, g, b, (int) (a * alpha));
        }

        @Override
        public void unsetDefaultColor() {
            delegate.unsetDefaultColor();
        }
    }
}
