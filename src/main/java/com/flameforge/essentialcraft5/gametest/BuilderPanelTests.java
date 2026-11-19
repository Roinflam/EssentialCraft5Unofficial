package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.client.screen.BuilderBlueprint;
import com.flameforge.essentialcraft5.client.screen.BuilderPanelLayout;
import com.flameforge.essentialcraft5.item.BuilderActions;
import com.flameforge.essentialcraft5.item.BuilderJobs;
import com.flameforge.essentialcraft5.item.BuilderMaterials;
import com.flameforge.essentialcraft5.item.BuilderMix;
import com.flameforge.essentialcraft5.item.BuilderMode;
import com.flameforge.essentialcraft5.item.BuilderModules;
import com.flameforge.essentialcraft5.item.BuilderPanel;
import com.flameforge.essentialcraft5.item.BuilderSelection;
import com.flameforge.essentialcraft5.item.BuilderShape;
import com.flameforge.essentialcraft5.item.BuilderTier;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.network.C2SBuilderActionPacket;
import com.flameforge.essentialcraft5.network.S2CBuilderPanelPacket;
import com.flameforge.essentialcraft5.registry.ECBuilder;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 创世神之杖面板「蓝图台」的 GameTest：
 * <ul>
 *   <li>版面纯函数 {@link BuilderPanelLayout}：四种窗口 × 界面缩放 2 / 3 / 4（按原版的算法退到放得下的缩放）× 三页 × 三档 × 空闲 / 施工中 ×
 *       中英两套字宽，外加最小界面 320 × 240、缩放 1 的大窗口和「窗口太小」：控件都在屏内、在所属那块里，同一块里互不重叠，
 *       同一行中线一致，台子左右 / 上下留白差不超过 1 像素；</li>
 *   <li>蓝图纯函数 {@link BuilderBlueprint}：缩放放得进框、居中、每格像素在上下限内，格子多时并格；投影朝向、正视图、并格优先级；</li>
 *   <li>服务端：换模式整份 NBT 照抄、施工 / 暂停时拒、铺设魔杖拒；设形状按档位；从身上挑的清单（内容、排序、上限、开着别的界面不看容器）、
 *       频率限制、写进选定方块；模块开关（停用的不生效但占槽）；各操作乱填被拒；混铺缺料按每种判。</li>
 * </ul>
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class BuilderPanelTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    static final String BATCH = "ec5_builder_panel";

    private BuilderPanelTests() {
    }

    @BeforeBatch(batch = BATCH)
    public static void builderPanelBefore(ServerLevel level) {
        BuilderJobs.clearAll();
        ECBuilder.Config.Test.reset();
        ECBuilder.Config.setTestOverrides(null, null, null);
        BuilderTestContainers.enabled = true;
    }

    @AfterBatch(batch = BATCH)
    public static void builderPanelAfter(ServerLevel level) {
        BuilderTestContainers.enabled = false;
        ECBuilder.Config.Test.reset();
        BuilderJobs.clearAll();
    }

    // ------------------------------------------------------------------
    // 版面
    // ------------------------------------------------------------------

    /** 原版按窗口和设定的缩放算界面尺寸：缩放往下退到界面至少 320 × 240，界面宽高向上取整 */
    private static int[] guiSize(int windowW, int windowH, int requested) {
        int scale = 1;
        while (scale < requested && windowW / (scale + 1) >= 320 && windowH / (scale + 1) >= 240) {
            scale++;
        }
        return new int[] {(windowW + scale - 1) / scale, (windowH + scale - 1) / scale};
    }

    /** 一套字宽：中文（短）和英文（长，数也长）。顺序同 Inputs：标题、MRU、MRU 数、页签、模式、撤销、停下、尺寸标签 */
    private static final int[][] TEXT_WIDTHS = {
            {108, 17, 40, 18, 72, 45, 18, 9},
            {190, 17, 76, 37, 125, 46, 21, 6},
    };

    private static BuilderPanelLayout.Inputs inputs(int w, int h, BuilderPanelLayout.Page page, BuilderTier tier, boolean running, int[] text,
                                                    int discard) {
        int shapes = tier == BuilderTier.PAVER ? 3 : 5;
        int modes = tier == BuilderTier.PAVER ? 1 : 5;
        return new BuilderPanelLayout.Inputs(w, h, page, shapes, modes, tier.moduleSlots(), running, text[0], text[1], text[2], text[3], text[4],
                text[5], text[6], text[7], discard);
    }

    /** 查一份版面，问题写进 problems */
    private static void checkLayout(BuilderPanelLayout l, List<String> problems) {
        String where = l.describe().substring(0, l.describe().indexOf('|'));
        BuilderPanelLayout.Rect screen = new BuilderPanelLayout.Rect(0, 0, l.in.width(), l.in.height());
        Map<String, BuilderPanelLayout.Rect> byId = new HashMap<>();
        for (BuilderPanelLayout.Box box : l.boxes()) {
            if (byId.put(box.id(), box.rect()) != null) {
                problems.add(where + "：框名重复 " + box.id());
            }
        }
        Map<String, Integer> rowMid = new HashMap<>();
        for (BuilderPanelLayout.Box box : l.boxes()) {
            BuilderPanelLayout.Rect r = box.rect();
            if (r.w() < 0 || r.h() < 0) {
                problems.add(where + "：" + box.id() + " 宽高为负 " + r);
            }
            if (!r.inside(screen)) {
                problems.add(where + "：" + box.id() + " 出屏 " + r);
            }
            if (!box.parent().equals("screen")) {
                BuilderPanelLayout.Rect parent = byId.get(box.parent());
                if (parent == null) {
                    problems.add(where + "：" + box.id() + " 的上级 " + box.parent() + " 不存在");
                } else if (!r.inside(parent)) {
                    problems.add(where + "：" + box.id() + " " + r + " 超出 " + box.parent() + " " + parent);
                }
            }
            if (box.row() != null) {
                int mid = 2 * r.y() + r.h();
                Integer first = rowMid.putIfAbsent(box.row(), mid);
                if (first != null && first != mid) {
                    problems.add(where + "：" + box.row() + " 一行里 " + box.id() + " 的中线和别的不一致");
                }
            }
        }
        List<BuilderPanelLayout.Box> boxes = l.boxes();
        for (int i = 0; i < boxes.size(); i++) {
            for (int j = i + 1; j < boxes.size(); j++) {
                BuilderPanelLayout.Box a = boxes.get(i);
                BuilderPanelLayout.Box b = boxes.get(j);
                if (a.parent().equals(b.parent()) && a.rect().intersects(b.rect())) {
                    problems.add(where + "：" + a.id() + " " + a.rect() + " 和 " + b.id() + " " + b.rect() + " 重叠");
                }
            }
        }
        // 台子居中：左右、上下留白差不超过 1，多出来的那 1 像素在右 / 下
        BuilderPanelLayout.Rect f = l.frame;
        int left = f.x();
        int right = l.in.width() - f.right();
        int top = f.y();
        int bottom = l.in.height() - f.bottom();
        if (right - left < 0 || right - left > 1 || bottom - top < 0 || bottom - top > 1) {
            problems.add(where + "：台子没居中 " + f);
        }
        if (l.tooSmall) {
            return;
        }
        // 能点的控件都得有面积
        List<BuilderPanelLayout.Rect> clickable = new ArrayList<>(List.of(l.close, l.undo, l.stop));
        clickable.addAll(l.tabs);
        clickable.addAll(l.shapeButtons);
        clickable.addAll(l.modeRows);
        clickable.addAll(l.sizeMinus);
        clickable.addAll(l.sizePlus);
        for (BuilderPanelLayout.ModuleRow row : l.moduleRows) {
            clickable.add(row.toggle());
            clickable.add(row.remove());
        }
        for (BuilderPanelLayout.MixRow row : l.mixRows) {
            clickable.add(row.minus());
            clickable.add(row.plus());
            clickable.add(row.remove());
        }
        for (BuilderPanelLayout.Rect r : clickable) {
            if (r == null || r.w() <= 0 || r.h() <= 0) {
                problems.add(where + "：有控件没有面积 " + r);
            }
        }
        // 顶栏在第一条分隔线上面，底条在第二条下面
        if (l.close.bottom() > l.rule1Y || l.bar.y() < l.rule2Y || l.left.bottom() > l.rule2Y || l.content.bottom() > l.rule2Y) {
            problems.add(where + "：顶栏 / 主体 / 底条压到分隔线");
        }
        // 三档都要放得下：模块页每个槽一行，范围页每个形状、每个模式都在
        if (l.in.page() == BuilderPanelLayout.Page.MODULES && l.moduleRows.size() != l.in.slots()) {
            problems.add(where + "：模块行数不对");
        }
        if (l.in.page() == BuilderPanelLayout.Page.RANGE
                && (l.shapeButtons.size() != l.in.shapes() || l.modeRows.size() != (l.in.modes() > 1 ? l.in.modes() : 0))) {
            problems.add(where + "：形状 / 模式行数不对");
        }
        if (l.in.running() && l.progress == null) {
            problems.add(where + "：施工中底条没留进度");
        }
    }

    private static void expectNoProblems(GameTestHelper helper, List<String> problems, int checked) {
        if (!problems.isEmpty()) {
            StringBuilder out = new StringBuilder("版面有 " + problems.size() + " 处问题（共查 " + checked + " 份）：");
            for (int i = 0; i < Math.min(6, problems.size()); i++) {
                out.append("\n").append(problems.get(i));
            }
            helper.fail(out.toString());
        }
    }

    /** 四种窗口 × 缩放 2 / 3 / 4 × 三页 × 三档 × 空闲 / 施工中 × 中英字宽 × 丢弃清单候选 0 / 12 / 64 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void builderPanelLayoutFitsCommonWindows(GameTestHelper helper) {
        int[][] windows = {{854, 480}, {1280, 720}, {1920, 1080}, {2560, 1440}};
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (int[] window : windows) {
            for (int scale = 2; scale <= 4; scale++) {
                int[] gui = guiSize(window[0], window[1], scale);
                for (BuilderPanelLayout.Page page : BuilderPanelLayout.Page.values()) {
                    for (BuilderTier tier : BuilderTier.values()) {
                        for (boolean running : new boolean[] {false, true}) {
                            for (int[] text : TEXT_WIDTHS) {
                                for (int discard : new int[] {0, 12, 64}) {
                                    BuilderPanelLayout l = BuilderPanelLayout.compute(inputs(gui[0], gui[1], page, tier, running, text, discard));
                                    if (l.tooSmall) {
                                        problems.add(window[0] + "×" + window[1] + " 缩放 " + scale + " 不该算窗口太小");
                                    }
                                    checkLayout(l, problems);
                                    checked++;
                                }
                            }
                        }
                    }
                }
            }
        }
        expectNoProblems(helper, problems, checked);
        helper.succeed();
    }

    /** 边界：原版允许的最小界面 320 × 240 和几种奇数尺寸（窄版）、缩放 1 的大窗口（台子封顶 520 × 300）、窗口太小只画提示 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void builderPanelLayoutEdges(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        int checked = 0;
        int[][] sizes = {{320, 240}, {321, 241}, {427, 240}, {426, 240}, {440, 250}, {480, 270}, {640, 360}, {2560, 1440}, {312, 208}, {313, 209},
                {312, 212}, {313, 213}};
        for (int[] size : sizes) {
            for (BuilderPanelLayout.Page page : BuilderPanelLayout.Page.values()) {
                for (BuilderTier tier : BuilderTier.values()) {
                    for (boolean running : new boolean[] {false, true}) {
                        for (int[] text : TEXT_WIDTHS) {
                            BuilderPanelLayout l = BuilderPanelLayout.compute(inputs(size[0], size[1], page, tier, running, text, 64));
                            checkLayout(l, problems);
                            checked++;
                        }
                    }
                }
            }
        }
        BuilderPanelLayout big = BuilderPanelLayout.compute(inputs(2560, 1440, BuilderPanelLayout.Page.RANGE, BuilderTier.ASCENDED, false,
                TEXT_WIDTHS[0], 0));
        helper.assertTrue(big.frame.w() == BuilderPanelLayout.MAX_W && big.frame.h() == BuilderPanelLayout.MAX_H && !big.narrow,
                "大窗口台子封顶 520 × 300：" + big.frame);
        BuilderPanelLayout min = BuilderPanelLayout.compute(inputs(320, 240, BuilderPanelLayout.Page.BLOCK, BuilderTier.BUILDER, false,
                TEXT_WIDTHS[1], 0));
        helper.assertTrue(!min.tooSmall && min.narrow, "320 × 240 是窄版、不算太小");
        for (int[] tiny : new int[][] {{311, 400}, {400, 207}, {200, 120}, {1, 1}}) {
            BuilderPanelLayout l = BuilderPanelLayout.compute(inputs(tiny[0], tiny[1], BuilderPanelLayout.Page.RANGE, BuilderTier.BUILDER, true,
                    TEXT_WIDTHS[0], 0));
            helper.assertTrue(l.tooSmall && l.tooSmallNote != null && l.tabs.isEmpty() && l.undo == null,
                    tiny[0] + "×" + tiny[1] + " 应只画「窗口太小」");
            checkLayout(l, problems);
            checked++;
        }
        // 长标题被压窄，不推挤右边的 MRU；超长的 MRU 数也不出台子
        int[] longText = TEXT_WIDTHS[1].clone();
        longText[0] = 900;
        longText[2] = 120;
        BuilderPanelLayout l = BuilderPanelLayout.compute(inputs(320, 240, BuilderPanelLayout.Page.MODULES, BuilderTier.ASCENDED, true, longText, 64));
        checkLayout(l, problems);
        checked++;
        expectNoProblems(helper, problems, checked);
        helper.succeed();
    }

    /** 版面是纯函数：同样的输入两次结果一样；等分、居中的取整（多出来的像素给左边 / 留右边） */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void builderPanelLayoutHelpers(GameTestHelper helper) {
        BuilderPanelLayout.Inputs in = inputs(480, 270, BuilderPanelLayout.Page.BLOCK, BuilderTier.BUILDER, true, TEXT_WIDTHS[0], 12);
        helper.assertTrue(BuilderPanelLayout.compute(in).describe().equals(BuilderPanelLayout.compute(in).describe()), "同样输入同样结果");
        List<int[]> parts = BuilderPanelLayout.split(10, 103, 5, 2);
        int sum = 0;
        for (int i = 0; i < parts.size(); i++) {
            sum += parts.get(i)[1];
            if (i > 0) {
                helper.assertTrue(parts.get(i)[0] == parts.get(i - 1)[0] + parts.get(i - 1)[1] + 2, "等分间隔 2");
                helper.assertTrue(parts.get(i)[1] <= parts.get(i - 1)[1], "多出来的像素从左边起给");
            }
        }
        helper.assertTrue(sum == 103 - 8 && parts.get(4)[0] + parts.get(4)[1] == 113, "等分正好铺满：" + sum);
        helper.assertTrue(BuilderPanelLayout.center(0, 11, 4) == 3 && BuilderPanelLayout.center(5, 10, 4) == 8, "居中偏左上");
        // 两个汉字字宽 18、着墨 16：放进 40 宽的页签，左右各留 12
        helper.assertTrue(BuilderPanelLayout.centerText(273, 40, 18) == 273 + 12, "中文按着墨的宽居中");
        helper.assertTrue(BuilderPanelLayout.centerText(0, 5, 10) == 0 && BuilderPanelLayout.centerText(0, 10, 1) == 5, "字比框宽、字宽不到末尾留白时不出错");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 蓝图
    // ------------------------------------------------------------------

    /** 缩放：放进框里、居中（差 1 留右下）、每格在上下限之间；格子多时 k × k 并格，并完仍放得下 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void builderPanelBlueprintFit(GameTestHelper helper) {
        int[][] grids = {{1, 1}, {5, 5}, {12, 3}, {48, 48}, {67, 67}, {134, 134}, {300, 7}, {4096, 1}};
        int[][] areas = {{90, 60}, {186, 160}, {72, 40}};
        for (int[] g : grids) {
            for (int[] a : areas) {
                BuilderBlueprint.Fit fit = BuilderBlueprint.fit(g[0], g[1], 100, 50, a[0], a[1]);
                String name = g[0] + "×" + g[1] + " 放进 " + a[0] + "×" + a[1];
                helper.assertTrue(fit.cols() > 0 && fit.cell() >= BuilderBlueprint.MIN_CELL && fit.cell() <= BuilderBlueprint.MAX_CELL,
                        name + " 每格像素不在上下限内：" + fit);
                helper.assertTrue(fit.width() <= a[0] && fit.height() <= a[1], name + " 出框：" + fit);
                int dx = (a[0] - fit.width()) - 2 * (fit.x() - 100);
                int dy = (a[1] - fit.height()) - 2 * (fit.y() - 50);
                helper.assertTrue(dx >= 0 && dx <= 1 && dy >= 0 && dy <= 1, name + " 没居中：" + fit);
                helper.assertTrue(fit.cols() == (g[0] + fit.k() - 1) / fit.k() && fit.rows() == (g[1] + fit.k() - 1) / fit.k(), name + " 并格数不对");
                if (fit.k() > 1) {
                    BuilderBlueprint.Fit smaller = BuilderBlueprint.fit(g[0], g[1], 100, 50, a[0], a[1]);
                    helper.assertTrue(smaller.k() == fit.k(), name + " 每次算的一样");
                    helper.assertTrue((g[0] + fit.k() - 2) / (fit.k() - 1) * BuilderBlueprint.MIN_CELL > a[0]
                            || (g[1] + fit.k() - 2) / (fit.k() - 1) * BuilderBlueprint.MIN_CELL > a[1], name + " 并得比需要的多：k = " + fit.k());
                }
            }
        }
        helper.assertTrue(BuilderBlueprint.fit(5, 5, 0, 0, 1, 1).cols() == 0, "框连一格都放不下时返回空");
        helper.assertTrue(BuilderBlueprint.fit(2, 2, 0, 0, 100, 100).cell() == BuilderBlueprint.MAX_CELL, "格子少时不放得太大");
        helper.succeed();
    }

    /** 混铺取色：地图色一样的几种按表里的顺序从暗到亮拉开；和别的都不近的颜色不变；一种时不变 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void builderPanelMixColoursDistinct(GameTestHelper helper) {
        int stone = Blocks.STONE_BRICKS.defaultMapColor().col;
        helper.assertTrue(stone == Blocks.MOSSY_STONE_BRICKS.defaultMapColor().col && stone == Blocks.CRACKED_STONE_BRICKS.defaultMapColor().col,
                "前提：三种石砖的地图色一样");
        int red = 0xB03030;
        int[] out = BuilderBlueprint.distinctColours(new int[] {stone, red, stone, stone});
        helper.assertTrue(out[1] == red, "和别的都不近的颜色应不变");
        int l0 = luma(out[0]);
        int l2 = luma(out[2]);
        int l3 = luma(out[3]);
        helper.assertTrue(l0 < l2 && l2 < l3, "同色的几种应按表里的顺序从暗到亮：" + l0 + " " + l2 + " " + l3);
        helper.assertTrue(l2 - l0 >= 20 && l3 - l2 >= 20, "拉开得太少：" + l0 + " " + l2 + " " + l3);
        helper.assertTrue(BuilderBlueprint.distinctColours(new int[] {stone})[0] == stone, "只有一种时不变");
        int[] two = BuilderBlueprint.distinctColours(new int[] {0x707070, 0x646464});
        helper.assertTrue(luma(two[0]) != luma(two[1]) && Math.abs(luma(two[0]) - luma(two[1])) >= 20, "相近的两种也要拉开");
        // 纯白、纯黑几种：整组往中间挪，相邻两种照样拉得开
        for (int[] same : new int[][] {{0xFFFFFF, 0xFFFFFF, 0xFFFFFF}, {0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF}, {0x191919, 0x191919, 0x191919, 0x191919}}) {
            int[] spread = BuilderBlueprint.distinctColours(same);
            for (int i = 1; i < spread.length; i++) {
                helper.assertTrue(luma(spread[i]) - luma(spread[i - 1]) >= 20,
                        "白色 / 黑色系拉开得太少：" + Integer.toHexString(same[0]) + " 第 " + i + " 种 " + luma(spread[i - 1]) + " → " + luma(spread[i]));
            }
        }
        // A 近 B、B 近 C 时三种同组，拉开后不和组外的撞色
        int[] chain = BuilderBlueprint.distinctColours(new int[] {0x707070, 0x8C8C8C, 0x7E7E7E});
        helper.assertTrue(luma(chain[0]) != luma(chain[1]) && luma(chain[1]) != luma(chain[2]) && luma(chain[0]) != luma(chain[2]), "连着相近的三种应各不相同");
        helper.succeed();
    }

    private static int luma(int rgb) {
        return ((rgb >> 16 & 0xFF) * 299 + (rgb >> 8 & 0xFF) * 587 + (rgb & 0xFF) * 114) / 1000;
    }

    /** 投影：俯视时前方朝上、右手朝右；竖着的一片画正视图；同一点按优先级留要拆的；并格同样按优先级 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void builderPanelBlueprintProjection(GameTestHelper helper) {
        // 面朝北：前方是 −z，右手是 +x。放三格：原点、前方一格（应在上一行）、右手一格（应在右边一列）
        long[] cells = {BlockPos.asLong(0, 64, 0), BlockPos.asLong(0, 64, -1), BlockPos.asLong(1, 64, 0)};
        byte[] kinds = {BuilderBlueprint.PLACE, BuilderBlueprint.SHORT, BuilderBlueprint.BREAK};
        int[] colours = {1, 2, 3};
        BuilderBlueprint.Grid north = BuilderBlueprint.project(cells, kinds, colours, 3, Direction.NORTH);
        helper.assertTrue(north.cols() == 2 && north.rows() == 2 && !north.frontView(), "俯视 2 × 2");
        helper.assertTrue(north.kind(0, 1) == BuilderBlueprint.PLACE && north.kind(0, 0) == BuilderBlueprint.SHORT
                && north.kind(1, 1) == BuilderBlueprint.BREAK && north.kind(1, 0) == BuilderBlueprint.EMPTY, "面朝北：前方朝上、右手朝右");
        // 面朝东：前方是 +x，右手是 +z
        long[] east = {BlockPos.asLong(0, 64, 0), BlockPos.asLong(1, 64, 0), BlockPos.asLong(0, 64, 1)};
        BuilderBlueprint.Grid eastGrid = BuilderBlueprint.project(east, kinds, colours, 3, Direction.EAST);
        helper.assertTrue(eastGrid.kind(0, 1) == BuilderBlueprint.PLACE && eastGrid.kind(0, 0) == BuilderBlueprint.SHORT
                && eastGrid.kind(1, 1) == BuilderBlueprint.BREAK, "面朝东：前方朝上、右手朝右");
        // 竖墙：x 方向 3 格、高 2 格、z 只有一格 → 正视图，往上为上
        List<Long> wall = new ArrayList<>();
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 3; x++) {
                wall.add(BlockPos.asLong(x, 70 + y, 5));
            }
        }
        long[] wallCells = wall.stream().mapToLong(Long::longValue).toArray();
        byte[] wallKinds = new byte[6];
        java.util.Arrays.fill(wallKinds, BuilderBlueprint.PLACE);
        wallKinds[3] = BuilderBlueprint.BREAK;   // (0, 71) 上层最左
        BuilderBlueprint.Grid front = BuilderBlueprint.project(wallCells, wallKinds, new int[6], 6, Direction.NORTH);
        helper.assertTrue(front.frontView() && front.cols() == 3 && front.rows() == 2, "竖墙画正视图 3 × 2");
        helper.assertTrue(front.kind(0, 0) == BuilderBlueprint.BREAK, "上层在上面、右手 +x 朝右");
        // 两层叠在同一点：要拆的留下
        long[] stacked = {BlockPos.asLong(0, 64, 0), BlockPos.asLong(0, 65, 0), BlockPos.asLong(1, 64, 0), BlockPos.asLong(1, 65, 0),
                BlockPos.asLong(0, 64, 1), BlockPos.asLong(1, 64, 1)};
        byte[] stackedKinds = {BuilderBlueprint.PLACE, BuilderBlueprint.BREAK, BuilderBlueprint.BLOCKED, BuilderBlueprint.SHORT,
                BuilderBlueprint.PLACE, BuilderBlueprint.PLACE};
        BuilderBlueprint.Grid top = BuilderBlueprint.project(stacked, stackedKinds, new int[6], 6, Direction.NORTH);
        helper.assertTrue(!top.frontView() && top.count(BuilderBlueprint.BREAK) == 1 && top.count(BuilderBlueprint.SHORT) == 1
                && top.count(BuilderBlueprint.BLOCKED) == 0, "叠在同一点按优先级留：要拆 > 缺料 > 要放 > 放不了");
        // 并格：2 × 2 并一格，要拆的那格留下
        BuilderBlueprint.Grid merged = BuilderBlueprint.downsample(top, 2);
        helper.assertTrue(merged.cols() == 1 && merged.rows() == 1 && merged.kind(0, 0) == BuilderBlueprint.BREAK, "并格按优先级");
        BuilderBlueprint.Grid odd = BuilderBlueprint.downsample(BuilderBlueprint.box(5, 3, BuilderBlueprint.PLACE, 7), 2);
        helper.assertTrue(odd.cols() == 3 && odd.rows() == 2 && odd.count(BuilderBlueprint.PLACE) == 6, "除不尽的边也并进去");
        helper.assertTrue(BuilderBlueprint.project(new long[0], new byte[0], new int[0], 0, Direction.NORTH).cols() == 0, "没格子是空图");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 服务端
    // ------------------------------------------------------------------

    private static ItemStack wand(BuilderMode mode) {
        ItemStack stack = new ItemStack(ECBuilder.item(mode));
        stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(MRUStaffItem.MAX_MRU));
        return stack;
    }

    private static int mruOf(ItemStack stack) {
        return stack.getCapability(ECCapabilities.MRU_ITEM).map(handler -> handler.getMRU()).orElse(-1);
    }

    private static ServerPlayer flyer(GameTestHelper helper, ItemStack held, int x, int y, int z) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-builder-panel");
        Vec3 at = helper.absoluteVec(new Vec3(x + 0.5D, y, z + 0.5D));
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        player.setOnGround(false);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        return player;
    }

    private static ResourceLocation id(net.minecraft.world.level.block.Block block) {
        return Objects.requireNonNull(ForgeRegistries.BLOCKS.getKey(block));
    }

    private static ItemStack withModules(ItemStack wand, BuilderModules.Kind... kinds) {
        ListTag list = new ListTag();
        for (BuilderModules.Kind kind : kinds) {
            list.add(StringTag.valueOf(kind.registryName().toString()));
        }
        wand.getOrCreateTag().put(BuilderModules.TAG, list);
        return wand;
    }

    /** 换模式：换成对应模式的物品，整份 NBT（选区、形状、尺寸、模块、档位、自定义键）和 MRU 照抄；同模式、乱填序号、铺设魔杖都拒 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void builderPanelModeSwapCopiesNbt(GameTestHelper helper) {
        ItemStack stack = withModules(wand(BuilderMode.FILL), BuilderModules.Kind.MIX, BuilderModules.Kind.SALVAGE);
        stack.getOrCreateTag().putInt(BuilderTier.TAG_TIER, BuilderTier.ASCENDED.level());
        stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(12345));
        BuilderSelection.setShape(stack, BuilderShape.WALL);
        BuilderSelection.setSize(stack, BuilderShape.WALL, 7, 3);
        BuilderSelection.setStoredBlock(stack, id(Blocks.STONE_BRICKS));
        stack.getOrCreateTag().putString("ec5_panel_marker", "keep me");
        CompoundTag before = Objects.requireNonNull(stack.getTag()).copy();
        ServerPlayer player = flyer(helper, stack, 1, 3, 1);

        helper.assertTrue(BuilderActions.setMode(player, InteractionHand.MAIN_HAND, BuilderMode.FILL.ordinal()) == BuilderActions.SettingResult.INVALID,
                "换成同一个模式拒");
        helper.assertTrue(BuilderActions.setMode(player, InteractionHand.MAIN_HAND, -1) == BuilderActions.SettingResult.INVALID
                && BuilderActions.setMode(player, InteractionHand.MAIN_HAND, BuilderMode.values().length) == BuilderActions.SettingResult.INVALID,
                "模式序号乱填拒");
        helper.assertTrue(player.getMainHandItem() == stack, "被拒时手里的物品对象没换");

        helper.assertTrue(BuilderActions.setMode(player, InteractionHand.MAIN_HAND, BuilderMode.REPLACE_ALL.ordinal()) == BuilderActions.SettingResult.OK,
                "能换成全部替换");
        ItemStack swapped = player.getMainHandItem();
        helper.assertTrue(BuilderMode.of(swapped) == BuilderMode.REPLACE_ALL && swapped.getItem() == ECBuilder.item(BuilderMode.REPLACE_ALL)
                && swapped.getCount() == 1, "手里换成全部替换的魔杖");
        helper.assertTrue(before.equals(swapped.getTag()), "NBT 整份照抄：" + swapped.getTag());
        helper.assertTrue(mruOf(swapped) == 12345, "MRU 照抄：" + mruOf(swapped));
        helper.assertTrue(BuilderTier.of(swapped) == BuilderTier.ASCENDED && BuilderSelection.shape(swapped) == BuilderShape.WALL
                && BuilderModules.has(swapped, BuilderModules.Kind.SALVAGE), "档位、形状、模块都在");
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (BuilderMode.of(player.getInventory().getItem(i)) != null) {
                total++;
            }
        }
        helper.assertTrue(total == 1, "背包里只有一根魔杖（没多出来）：" + total);

        // 包走一遍：副手的魔杖也认
        ItemStack off = wand(BuilderMode.CLEAR);
        ServerPlayer second = flyer(helper, ItemStack.EMPTY, 3, 3, 1);
        second.setItemInHand(InteractionHand.OFF_HAND, off);
        helper.assertTrue(BuilderActions.handle(new C2SBuilderActionPacket(C2SBuilderActionPacket.SET_MODE, 1, BuilderMode.FILL.ordinal(), 0), second)
                && BuilderMode.of(second.getOffhandItem()) == BuilderMode.FILL, "副手的魔杖经包换模式");
        helper.assertTrue(!BuilderActions.handle(new C2SBuilderActionPacket(C2SBuilderActionPacket.SET_MODE, 0, BuilderMode.CLEAR.ordinal(), 0), second),
                "主手不是魔杖的不理");

        // 铺设魔杖没有模式可换
        ItemStack paver = new ItemStack(ECBuilder.PAVING_WAND.get());
        ServerPlayer third = flyer(helper, paver, 5, 3, 1);
        for (BuilderMode mode : BuilderMode.values()) {
            helper.assertTrue(BuilderActions.setMode(third, InteractionHand.MAIN_HAND, mode.ordinal()) == BuilderActions.SettingResult.INVALID,
                    "铺设魔杖拒换成 " + mode);
        }
        helper.assertTrue(third.getMainHandItem() == paver, "铺设魔杖没被换掉");

        // 旁观者、死了不理
        ServerPlayer ghost = flyer(helper, wand(BuilderMode.FILL), 7, 3, 1);
        ghost.setGameMode(GameType.SPECTATOR);
        helper.assertTrue(BuilderActions.setMode(ghost, InteractionHand.MAIN_HAND, BuilderMode.CLEAR.ordinal()) == BuilderActions.SettingResult.UNAVAILABLE,
                "旁观者拒");
        helper.succeed();
    }

    /** 施工中、暂停中改设置的操作一律拒（换模式、设形状、尺寸、模块开关、从身上挑），手里的物品和 NBT 不动；停下以后能改 */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 300)
    public static void builderPanelBusyRefusesSettings(GameTestHelper helper) {
        ItemStack stack = withModules(wand(BuilderMode.FILL), BuilderModules.Kind.HASTE);
        ServerPlayer player = flyer(helper, stack, 2, 4, 6);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 1));
        player.getInventory().items.set(2, new ItemStack(Items.COBBLESTONE, 4));
        BuilderSelection.setFirst(stack, helper.absolutePos(new BlockPos(3, 3, 6)), helper.getLevel().dimension());
        BuilderSelection.setSecond(stack, helper.absolutePos(new BlockPos(4, 3, 6)));
        BuilderSelection.setStoredBlock(stack, id(Blocks.OAK_PLANKS));
        Runnable expectRefused = () -> {
            CompoundTag tag = Objects.requireNonNull(stack.getTag()).copy();
            helper.assertTrue(BuilderActions.setMode(player, InteractionHand.MAIN_HAND, BuilderMode.CLEAR.ordinal()) == BuilderActions.SettingResult.BUSY,
                    "换模式拒");
            helper.assertTrue(BuilderActions.setShape(player, InteractionHand.MAIN_HAND, BuilderShape.PLANE.ordinal()) == BuilderActions.SettingResult.BUSY,
                    "设形状拒");
            helper.assertTrue(BuilderActions.pickBlock(player, InteractionHand.MAIN_HAND, id(Blocks.COBBLESTONE)) == BuilderActions.SettingResult.BUSY,
                    "从身上挑拒");
            helper.assertTrue(BuilderModules.toggleFromHand(player, InteractionHand.MAIN_HAND, BuilderModules.Kind.HASTE.ordinal(), false)
                    == BuilderModules.RemoveResult.BUSY, "模块开关拒");
            helper.assertTrue(!BuilderActions.handle(new C2SBuilderActionPacket(C2SBuilderActionPacket.CYCLE_SHAPE, 0, 0, 0), player),
                    "切形状的包拒");
            helper.assertTrue(player.getMainHandItem() == stack && tag.equals(stack.getTag()), "手里的物品和 NBT 都没动");
        };
        helper.startSequence()
                .thenExecute(() -> {
                    helper.assertTrue(BuilderJobs.start(player, InteractionHand.MAIN_HAND) == BuilderJobs.StartResult.STARTED, "应能开工");
                    helper.assertTrue(BuilderJobs.isRunning(player.getUUID()), "在施工");
                    expectRefused.run();
                })
                .thenWaitUntil(() -> {
                    BuilderJobs.Snapshot snapshot = BuilderJobs.snapshot(player.getUUID(), helper.getLevel().getGameTime());
                    helper.assertTrue(snapshot != null && snapshot.paused(), "还没因缺料暂停");
                })
                .thenExecute(() -> {
                    expectRefused.run();
                    // 停下不受「先停下才能改」限制
                    helper.assertTrue(BuilderActions.handle(new C2SBuilderActionPacket(C2SBuilderActionPacket.STOP, 0, 0, 0), player), "停下的包照收");
                })
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还没停下"))
                .thenExecute(() -> {
                    helper.assertTrue(BuilderActions.setShape(player, InteractionHand.MAIN_HAND, BuilderShape.LINE.ordinal()) == BuilderActions.SettingResult.OK
                            && BuilderSelection.shape(stack) == BuilderShape.LINE, "停下以后能设形状");
                    helper.assertTrue(BuilderActions.pickBlock(player, InteractionHand.MAIN_HAND, id(Blocks.COBBLESTONE)) == BuilderActions.SettingResult.OK
                            && id(Blocks.COBBLESTONE).equals(BuilderSelection.storedBlock(stack)), "停下以后能从身上挑");
                })
                .thenSucceed();
    }

    /** 设形状：按档位（一阶没有竖墙、沿面），序号乱填拒；切形状的包照旧按档位转 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void builderPanelSetShapeByTier(GameTestHelper helper) {
        ItemStack stack = wand(BuilderMode.FILL);
        ServerPlayer player = flyer(helper, stack, 1, 3, 1);
        for (BuilderShape shape : BuilderShape.values()) {
            helper.assertTrue(BuilderActions.setShape(player, InteractionHand.MAIN_HAND, shape.ordinal()) == BuilderActions.SettingResult.OK
                    && BuilderSelection.shape(stack) == shape, "二阶能设 " + shape);
        }
        helper.assertTrue(BuilderActions.setShape(player, InteractionHand.MAIN_HAND, -1) == BuilderActions.SettingResult.INVALID
                && BuilderActions.setShape(player, InteractionHand.MAIN_HAND, 99) == BuilderActions.SettingResult.INVALID, "序号乱填拒");
        ItemStack paver = new ItemStack(ECBuilder.PAVING_WAND.get());
        ServerPlayer second = flyer(helper, paver, 3, 3, 1);
        helper.assertTrue(BuilderActions.setShape(second, InteractionHand.MAIN_HAND, BuilderShape.WALL.ordinal()) == BuilderActions.SettingResult.INVALID
                && BuilderActions.setShape(second, InteractionHand.MAIN_HAND, BuilderShape.FACE.ordinal()) == BuilderActions.SettingResult.INVALID,
                "一阶拒竖墙、沿面");
        helper.assertTrue(BuilderActions.setShape(second, InteractionHand.MAIN_HAND, BuilderShape.LINE.ordinal()) == BuilderActions.SettingResult.OK
                && BuilderSelection.shape(paver) == BuilderShape.LINE, "一阶能设直线");
        helper.succeed();
    }

    /** 从身上挑的清单：背包和随身容器里能当材料的方块，按总数排、同数按注册名；带 NBT 的、不是方块的、取不出的容器不算；上限截断；开着别的界面不看容器 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void builderPanelStocksList(GameTestHelper helper) {
        ItemStack stack = wand(BuilderMode.FILL);
        ServerPlayer player = flyer(helper, stack, 1, 3, 1);
        player.getInventory().items.set(1, new ItemStack(Items.COBBLESTONE, 10));
        player.getInventory().items.set(2, new ItemStack(Items.OAK_PLANKS, 40));
        player.getInventory().items.set(3, new ItemStack(Items.DIAMOND, 64));
        ItemStack tagged = new ItemStack(Items.GLASS, 64);
        tagged.getOrCreateTag().putString("ec5_panel_marker", "x");
        player.getInventory().items.set(4, tagged);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.OAK_PLANKS, 24));
        ItemStack bag = new ItemStack(Items.BUNDLE);
        IItemHandler handler = BuilderTestContainers.handler(bag);
        handler.insertItem(0, new ItemStack(Items.STONE, 64), false);
        handler.insertItem(1, new ItemStack(Items.STONE, 36), false);
        handler.insertItem(2, new ItemStack(Items.COBBLESTONE, 5), false);
        player.getInventory().items.set(5, bag);
        ItemStack locked = new ItemStack(Items.WRITABLE_BOOK);
        BuilderTestContainers.handler(locked).insertItem(0, new ItemStack(Items.SANDSTONE, 64), false);
        player.getInventory().items.set(6, locked);

        BuilderMaterials.Stocks stocks = BuilderMaterials.stocks(player, stack, 64);
        List<String> got = new ArrayList<>();
        for (BuilderMaterials.Stock s : stocks.list()) {
            got.add(ForgeRegistries.ITEMS.getKey(s.item()).getPath() + " " + s.body() + "+" + s.box());
        }
        helper.assertTrue(got.equals(List.of("stone 0+100", "oak_planks 64+0", "cobblestone 10+5")) && !stocks.truncated() && stocks.containersSeen(),
                "清单内容和顺序：" + got);
        BuilderMaterials.Stocks cut = BuilderMaterials.stocks(player, stack, 2);
        helper.assertTrue(cut.list().size() == 2 && cut.truncated(), "上限截断");

        // 包也带同样的内容
        S2CBuilderPanelPacket packet = BuilderPanel.compute(player, stack, helper.getLevel().getServer().getTickCount());
        helper.assertTrue(packet.materials().size() == 3 && packet.materials().get(0).box() == 100L
                && packet.undoState() == S2CBuilderPanelPacket.UNDO_NONE, "清单包：" + packet);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        S2CBuilderPanelPacket.encode(packet, buf);
        S2CBuilderPanelPacket back = S2CBuilderPanelPacket.decode(buf);
        helper.assertTrue(back.equals(packet) && buf.readableBytes() == 0, "清单包编解码来回一致");

        // 开着别的容器界面：随身容器不看，背包里的照给
        player.containerMenu = new net.minecraft.world.inventory.ChestMenu(net.minecraft.world.inventory.MenuType.GENERIC_9x1, 99,
                player.getInventory(), new net.minecraft.world.SimpleContainer(9), 1);
        BuilderMaterials.Stocks closed = BuilderMaterials.stocks(player, stack, 64);
        helper.assertTrue(!closed.containersSeen() && closed.list().size() == 2 && closed.list().get(0).item() == Items.OAK_PLANKS
                && closed.list().get(1).total() == 10L, "开着别的界面不看容器：" + closed.list());
        player.containerMenu = player.inventoryMenu;
        helper.succeed();
    }

    /**
     * 撤销那句：放一片后是「能撤」；接着清除一片后变灰写「上一次是清除」（撤的不会是上一次）；再放一片又能撤（不再说清除）
     */
    @GameTest(template = EMPTY_LARGE, batch = BATCH, timeoutTicks = 300)
    public static void builderPanelUndoStateAfterClear(GameTestHelper helper) {
        ItemStack stack = wand(BuilderMode.FILL);
        ServerPlayer player = flyer(helper, stack, 2, 4, 2);
        player.getInventory().items.set(1, new ItemStack(Items.OAK_PLANKS, 16));
        BlockPos a = helper.absolutePos(new BlockPos(3, 3, 3));
        BlockPos b = helper.absolutePos(new BlockPos(5, 3, 3));
        BuilderSelection.setStoredBlock(stack, id(Blocks.OAK_PLANKS));
        java.util.function.IntSupplier undoState = () -> BuilderPanel.compute(player, player.getMainHandItem(),
                helper.getLevel().getServer().getTickCount()).undoState();
        java.util.function.Consumer<BlockPos> select = pos -> {
            ItemStack held = player.getMainHandItem();
            BuilderSelection.setFirst(held, pos, helper.getLevel().dimension());
            BuilderSelection.setSecond(held, pos);
        };
        helper.startSequence()
                .thenExecute(() -> {
                    helper.assertTrue(undoState.getAsInt() == S2CBuilderPanelPacket.UNDO_NONE, "开始时没有可撤的");
                    select.accept(a);
                    helper.assertTrue(BuilderJobs.start(player, InteractionHand.MAIN_HAND) == BuilderJobs.StartResult.STARTED, "放第一格");
                })
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在放"))
                .thenExecute(() -> {
                    helper.assertTrue(undoState.getAsInt() == S2CBuilderPanelPacket.UNDO_READY, "放完能撤：" + undoState.getAsInt());
                    helper.assertTrue(BuilderActions.setMode(player, InteractionHand.MAIN_HAND, BuilderMode.CLEAR.ordinal()) == BuilderActions.SettingResult.OK,
                            "换成清除");
                    select.accept(a);
                    helper.assertTrue(BuilderJobs.start(player, InteractionHand.MAIN_HAND) == BuilderJobs.StartResult.STARTED, "清掉那一格");
                })
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在清"))
                .thenExecute(() -> {
                    helper.assertTrue(undoState.getAsInt() == S2CBuilderPanelPacket.UNDO_CLEARED, "上一单是清除：撤销变灰，实际 " + undoState.getAsInt());
                    helper.assertTrue(BuilderActions.setMode(player, InteractionHand.MAIN_HAND, BuilderMode.FILL.ordinal()) == BuilderActions.SettingResult.OK,
                            "换回填充");
                    select.accept(b);
                    helper.assertTrue(BuilderJobs.start(player, InteractionHand.MAIN_HAND) == BuilderJobs.StartResult.STARTED, "再放一格");
                })
                .thenWaitUntil(() -> helper.assertTrue(!BuilderJobs.isRunning(player.getUUID()), "还在放"))
                .thenExecute(() -> helper.assertTrue(undoState.getAsInt() == S2CBuilderPanelPacket.UNDO_READY
                        && !BuilderPanel.compute(player, player.getMainHandItem(), helper.getLevel().getServer().getTickCount()).clearedSince(),
                        "又放了一片：能撤、不再说清除"))
                .thenSucceed();
    }

    /** 清单请求的频率限制：同一个人 10 刻里只回一次，不是魔杖不回；不占操作包的名额 */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 60)
    public static void builderPanelSyncRateLimited(GameTestHelper helper) {
        ItemStack stack = wand(BuilderMode.FILL);
        ServerPlayer player = flyer(helper, stack, 1, 3, 1);
        ServerPlayer empty = flyer(helper, ItemStack.EMPTY, 3, 3, 1);
        helper.startSequence()
                .thenExecute(() -> {
                    helper.assertTrue(BuilderPanel.request(player, InteractionHand.MAIN_HAND), "第一次回");
                    helper.assertTrue(!BuilderPanel.request(player, InteractionHand.MAIN_HAND), "同一刻再要不回");
                    helper.assertTrue(!BuilderPanel.request(empty, InteractionHand.MAIN_HAND), "手里不是魔杖不回");
                })
                .thenIdle(BuilderPanel.SYNC_GAP_TICKS - 2)
                .thenExecute(() -> helper.assertTrue(!BuilderPanel.request(player, InteractionHand.MAIN_HAND), "不到 10 刻不回"))
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(BuilderPanel.request(player, InteractionHand.MAIN_HAND), "过了 10 刻再回");
                    // 连发清单请求：只受自己的 10 刻限频，不占操作包的名额，之后真点的操作照收
                    int accepted = 0;
                    for (int i = 0; i < 30; i++) {
                        if (BuilderActions.handle(new C2SBuilderActionPacket(C2SBuilderActionPacket.PANEL_SYNC, 0, 0, 0), player)) {
                            accepted++;
                        }
                        helper.assertTrue(!BuilderActions.handle(new C2SBuilderActionPacket(C2SBuilderActionPacket.PANEL_SYNC, 0, 0, 0), empty),
                                "手里不是魔杖不回");
                    }
                    helper.assertTrue(accepted == 0, "刚回过，10 刻里连发一个都不回：" + accepted);
                    helper.assertTrue(BuilderActions.handle(new C2SBuilderActionPacket(C2SBuilderActionPacket.SET_SHAPE, 0, BuilderShape.PLANE.ordinal(), 0),
                            player) && BuilderSelection.shape(stack) == BuilderShape.PLANE, "连发清单请求后真操作照收");
                })
                .thenSucceed();
    }

    /** 从身上挑：身上有的能挑，写进选定方块；身上没有的、不能当材料的、乱填的注册名都拒；创造模式不要求身上有；包的项数不是 1 解不开 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void builderPanelPickBlock(GameTestHelper helper) {
        ItemStack stack = wand(BuilderMode.FILL);
        BuilderSelection.setStoredBlock(stack, id(Blocks.OAK_PLANKS));
        ServerPlayer player = flyer(helper, stack, 1, 3, 1);
        ItemStack bag = new ItemStack(Items.BUNDLE);
        BuilderTestContainers.handler(bag).insertItem(0, new ItemStack(Items.STONE_BRICKS, 3), false);
        player.getInventory().items.set(4, bag);
        player.getInventory().items.set(5, new ItemStack(Items.SAND, 1));

        helper.assertTrue(BuilderActions.pickBlock(player, InteractionHand.MAIN_HAND, id(Blocks.STONE_BRICKS)) == BuilderActions.SettingResult.OK
                && id(Blocks.STONE_BRICKS).equals(BuilderSelection.storedBlock(stack)), "随身容器里的能挑");
        helper.assertTrue(BuilderActions.pickBlock(player, InteractionHand.MAIN_HAND, id(Blocks.DIAMOND_BLOCK)) == BuilderActions.SettingResult.INVALID,
                "身上没有的拒");
        for (ResourceLocation bad : new ResourceLocation[] {id(Blocks.AIR), id(Blocks.WATER), id(Blocks.PISTON_HEAD),
                ResourceLocation.fromNamespaceAndPath("ec5test", "no_such_block")}) {
            helper.assertTrue(BuilderActions.pickBlock(player, InteractionHand.MAIN_HAND, bad) == BuilderActions.SettingResult.INVALID, "拒 " + bad);
        }
        helper.assertTrue(id(Blocks.STONE_BRICKS).equals(BuilderSelection.storedBlock(stack)), "被拒时选定方块不动");
        helper.assertTrue(BuilderActions.handle(C2SBuilderActionPacket.pickBlock(0, id(Blocks.SAND)), player)
                && id(Blocks.SAND).equals(BuilderSelection.storedBlock(stack)), "包走一遍");
        player.setGameMode(GameType.CREATIVE);
        helper.assertTrue(BuilderActions.pickBlock(player, InteractionHand.MAIN_HAND, id(Blocks.DIAMOND_BLOCK)) == BuilderActions.SettingResult.OK,
                "创造模式不要求身上有");
        player.setGameMode(GameType.SURVIVAL);

        for (int count : new int[] {0, 2, C2SBuilderActionPacket.MAX_LIST + 1}) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeByte(C2SBuilderActionPacket.PICK_BLOCK);
            buf.writeByte(0);
            buf.writeVarInt(count);
            buf.writeVarInt(0);
            for (int i = 0; i < count; i++) {
                buf.writeResourceLocation(id(Blocks.STONE));
            }
            boolean threw = false;
            try {
                C2SBuilderActionPacket.decode(buf);
            } catch (RuntimeException e) {
                threw = true;
            }
            helper.assertTrue(threw, "从身上挑带 " + count + " 项的包解不开");
        }
        helper.succeed();
    }

    /** 模块开关：停用的不生效但照样占槽（后面多装的那块不会顶上来），再开恢复；拆下时停用记录一起去掉；没装的、序号乱填、开关值乱填拒 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void builderPanelModuleToggle(GameTestHelper helper) {
        // 二阶 3 个槽，NBT 里写了 4 块：第 4 块（远域）不占槽、不生效
        ItemStack stack = withModules(wand(BuilderMode.FILL), BuilderModules.Kind.RANGE, BuilderModules.Kind.HASTE, BuilderModules.Kind.EFFICIENCY,
                BuilderModules.Kind.REACH);
        ItemStack plain = wand(BuilderMode.FILL);
        ServerPlayer player = flyer(helper, stack, 1, 3, 1);
        int boosted = BuilderTier.stats(stack).maxVolume();
        helper.assertTrue(boosted > BuilderTier.stats(plain).maxVolume(), "扩域生效");

        helper.assertTrue(BuilderModules.toggleFromHand(player, InteractionHand.MAIN_HAND, BuilderModules.Kind.RANGE.ordinal(), false)
                == BuilderModules.RemoveResult.OK, "能停用扩域");
        helper.assertTrue(!BuilderModules.has(stack, BuilderModules.Kind.RANGE) && BuilderModules.slotted(stack).contains(BuilderModules.Kind.RANGE)
                && BuilderModules.slotted(stack).size() == 3, "停用的不生效但占槽");
        helper.assertTrue(BuilderTier.stats(stack).maxVolume() == BuilderTier.stats(plain).maxVolume(), "停用后格数回到没装的");
        helper.assertTrue(!BuilderModules.has(stack, BuilderModules.Kind.REACH)
                && BuilderTier.stats(stack).maxReach() == BuilderTier.stats(plain).maxReach(), "停用不腾槽：第 4 块远域照样不生效");
        helper.assertTrue(BuilderModules.has(stack, BuilderModules.Kind.HASTE), "别的照旧生效");

        helper.assertTrue(BuilderModules.toggleFromHand(player, InteractionHand.MAIN_HAND, BuilderModules.Kind.REACH.ordinal(), false)
                == BuilderModules.RemoveResult.NOT_INSTALLED, "不占槽的那块不能开关");
        helper.assertTrue(BuilderModules.toggleFromHand(player, InteractionHand.MAIN_HAND, BuilderModules.Kind.MIX.ordinal(), false)
                == BuilderModules.RemoveResult.NOT_INSTALLED, "没装的拒");
        helper.assertTrue(BuilderModules.toggleFromHand(player, InteractionHand.MAIN_HAND, 99, false) == BuilderModules.RemoveResult.BAD_MODULE
                && BuilderModules.toggleFromHand(player, InteractionHand.MAIN_HAND, -1, true) == BuilderModules.RemoveResult.BAD_MODULE, "序号乱填拒");
        helper.assertTrue(!BuilderActions.handle(new C2SBuilderActionPacket(C2SBuilderActionPacket.TOGGLE_MODULE, 0, BuilderModules.Kind.RANGE.ordinal(), 2),
                player) && !BuilderModules.has(stack, BuilderModules.Kind.RANGE), "开关值乱填拒");

        helper.assertTrue(BuilderActions.handle(new C2SBuilderActionPacket(C2SBuilderActionPacket.TOGGLE_MODULE, 0, BuilderModules.Kind.RANGE.ordinal(), 1),
                player) && BuilderTier.stats(stack).maxVolume() == boosted, "再开恢复");
        helper.assertTrue(!Objects.requireNonNull(stack.getTag()).contains(BuilderModules.TAG_OFF), "全开时不留停用键");

        // 停用后拆下：停用记录一起去掉，再装上是开着的
        BuilderModules.setEnabled(stack, BuilderModules.Kind.HASTE, false);
        helper.assertTrue(BuilderModules.removeFromHand(player, InteractionHand.MAIN_HAND, BuilderModules.Kind.HASTE.ordinal()) == BuilderModules.RemoveResult.OK
                && BuilderModules.disabled(stack).isEmpty(), "拆下时停用记录一起去掉");
        helper.assertTrue(BuilderModules.has(stack, BuilderModules.Kind.REACH), "拆掉一块后第 4 块顶上来");

        // 手改 NBT：停用表里塞乱七八糟的只认得认得的
        ListTag junk = new ListTag();
        junk.add(StringTag.valueOf("not a name"));
        junk.add(StringTag.valueOf("ec5test:nothing"));
        junk.add(StringTag.valueOf(BuilderModules.Kind.EFFICIENCY.registryName().toString()));
        stack.getOrCreateTag().put(BuilderModules.TAG_OFF, junk);
        helper.assertTrue(BuilderModules.disabled(stack).size() == 1 && !BuilderModules.has(stack, BuilderModules.Kind.EFFICIENCY), "停用表只认得认得的");
        helper.succeed();
    }

    /** 各操作乱填：手序号乱填当主手；不是魔杖、没在施工也照样能「停下 / 撤销」不出错；尺寸、形状的包在别的形状下乱填不改 NBT */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void builderPanelGarbageActions(GameTestHelper helper) {
        ItemStack stack = wand(BuilderMode.FILL);
        BuilderSelection.setShape(stack, BuilderShape.FACE);
        ServerPlayer player = flyer(helper, stack, 1, 3, 1);
        CompoundTag before = Objects.requireNonNull(stack.getTag()).copy();
        helper.assertTrue(!BuilderActions.handle(new C2SBuilderActionPacket(C2SBuilderActionPacket.SET_SIZE, 0, 9, 9), player), "沿面没有尺寸，拒");
        helper.assertTrue(!BuilderActions.handle(new C2SBuilderActionPacket(C2SBuilderActionPacket.SET_SHAPE, 0, 42, 0), player), "形状序号乱填拒");
        helper.assertTrue(!BuilderActions.handle(new C2SBuilderActionPacket(C2SBuilderActionPacket.SET_MODE, 0, -7, 0), player), "模式序号乱填拒");
        helper.assertTrue(!BuilderActions.handle(new C2SBuilderActionPacket(C2SBuilderActionPacket.TOGGLE_MODULE, 0, 0, 1), player), "没装的模块开关拒");
        helper.assertTrue(!BuilderActions.handle(new C2SBuilderActionPacket(99, 0, 0, 0), player), "认不得的操作拒");
        helper.assertTrue(player.getMainHandItem() == stack && before.equals(stack.getTag()), "NBT 没动");
        helper.succeed();
    }

    /** 混铺缺料按每种判：合计够但有一种不够也算缺；不知道持有数时让调用方退回比合计 */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void builderPanelShortageByKind(GameTestHelper helper) {
        helper.assertTrue(BuilderMix.shortByKind(new int[] {10, 10}, new long[] {100, 9}) == 1, "合计够、一种不够 = 缺");
        helper.assertTrue(BuilderMix.shortByKind(new int[] {10, 10}, new long[] {10, 10}) == 0, "每种都正好够 = 够");
        helper.assertTrue(BuilderMix.shortByKind(new int[] {10, 0}, new long[] {10, 0}) == 0, "这一单不用的那种不看");
        helper.assertTrue(BuilderMix.shortByKind(new int[] {10, 0}, new long[] {10, -1}) == 0, "不用的那种不知道持有数也不碍事");
        helper.assertTrue(BuilderMix.shortByKind(new int[] {10, 5}, new long[] {10, -1}) == -1, "要用的那种不知道持有数 = 退回比合计");
        helper.assertTrue(BuilderMix.shortByKind(new int[] {10}, new long[] {10, 10}) == -1, "长度对不上 = 退回比合计");
        helper.succeed();
    }
}
