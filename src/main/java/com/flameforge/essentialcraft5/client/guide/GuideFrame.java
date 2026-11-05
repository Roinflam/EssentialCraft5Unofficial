package com.flameforge.essentialcraft5.client.guide;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 画「图」（{@link Figures}：配方、结构、仪式、插图）时的绘制上下文。图元画的时候顺手登记鼠标下的「热区」，
 * 画完后由 {@link FigureBlocks} 转给 {@link GuideRender}：物品提示、附加说明行、点物品跳到介绍它的条目，都在那边统一处理。
 * <p>
 * 一个界面只有一个实例（挂在 {@link GuideRender} 上），每帧 {@link #begin} 重置一次，渲染路径上不新建对象；
 * 只有鼠标真的压在某个格子上时才登记热区（一帧最多几条）。
 * 面板全程不平移（开场只裁剪），{@link #dx} / {@link #dy} 恒为 0，留着给以后需要平移的图元用。
 */
public final class GuideFrame {

    /**
     * 可悬停的区域。
     *
     * @param stack 物品（有的话提示框显示物品提示，点击跳到介绍它的条目），没有物品时为 EMPTY
     * @param lines 物品提示后面追加的说明；没有物品时就是提示的全部内容
     * @param entry 指向的条目键（分类/条目），点击翻过去；不是条目链接时为 null
     */
    public record Hotspot(int x, int y, int width, int height, ItemStack stack, List<Component> lines, @Nullable String entry) {

        public boolean contains(double mouseX, double mouseY) {
            return GuideTheme.hit(mouseX, mouseY, x, y, width, height);
        }
    }

    public GuiGraphics g;
    public Font font;
    public int mouseX;
    public int mouseY;
    public long now;
    public float time;
    /** 当前内容的整体透明度（换篇淡入用），图元的 fill / 文字都乘上它 */
    public float alpha = 1F;
    public int dx;
    public int dy;

    /**
     * 展示厅（开发时截图）用：要找的物品。非空时，画到第一格这种物品（整格可见）就把格子中心的屏幕坐标记进
     * {@link #showroomProbeX} / {@link #showroomProbeY}，展示厅再把假鼠标放过去拍物品提示。平时是 null，每格多一次判空
     */
    @Nullable
    static volatile Item showroomProbe;
    static volatile int showroomProbeX = -1;
    static volatile int showroomProbeY = -1;
    /** 悬停是否可用（开场动画中、翻页擦除的旧页上不响应） */
    public boolean interactive;
    /**
     * 这张图的候选物品（标签原料、可换的方块）要不要轮换：鼠标压在这张图上时才每秒换一个，平时停在第一个
     * （配方图在阅读栏正中间，读旁边的字时余光里每秒跳一下也是常驻动画）。{@link FigureBlocks} 画每张图之前按鼠标设
     */
    public boolean cycling;
    /** 可见区域的上下边界：被阅读栏裁掉的格子不算悬停 */
    public int clipTop = Integer.MIN_VALUE;
    public int clipBottom = Integer.MAX_VALUE;
    private final List<Hotspot> hotspots = new ArrayList<>();
    /** 空格子（没有候选物品）：共用一个空数组，渲染路径上不新建 */
    public static final ItemStack[] NONE = new ItemStack[0];
    /** 方块 → 画图标用的物品栈（只读，渲染时共用；每帧给每个格子 new 一个太浪费） */
    private static final Map<Block, ItemStack> BLOCK_STACKS = new IdentityHashMap<>();

    /** 旧写法：一帧一个实例（结构图、配方图以外的地方仍可这样用） */
    public GuideFrame(GuiGraphics g, Font font, int mouseX, int mouseY, long now, float time) {
        begin(g, font, mouseX, mouseY, now, time);
    }

    GuideFrame() {
    }

    /** 新的一帧：换画布与鼠标，清空热区 */
    public void begin(GuiGraphics g, Font font, int mouseX, int mouseY, long now, float time) {
        this.g = g;
        this.font = font;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.now = now;
        this.time = time;
        this.alpha = 1F;
        this.dx = 0;
        this.dy = 0;
        this.clipTop = Integer.MIN_VALUE;
        this.clipBottom = Integer.MAX_VALUE;
        this.cycling = false;
        this.hotspots.clear();
    }

    public List<Hotspot> hotspots() {
        return hotspots;
    }

    /** 当前坐标系下 (x, y, w, h) 是否被鼠标悬停 */
    public boolean hovered(int x, int y, int width, int height) {
        return interactive && mouseY >= clipTop && mouseY < clipBottom
                && GuideTheme.hit(mouseX - dx, mouseY - dy, x, y, width, height);
    }

    public void hotspot(int x, int y, int width, int height, ItemStack stack, List<Component> lines) {
        hotspots.add(new Hotspot(x + dx, y + dy, width, height, stack, lines, null));
    }

    /** 指向条目的热区 */
    public void entryHotspot(int x, int y, int width, int height, String entry) {
        hotspots.add(new Hotspot(x + dx, y + dy, width, height, ItemStack.EMPTY, List.of(), entry));
    }

    /** 多个候选物品（标签原料、轮换的方块）：鼠标压在这张图上时每秒换一个（全界面同步），平时停在第一个 */
    public ItemStack cycle(ItemStack[] options) {
        if (options.length == 0) {
            return ItemStack.EMPTY;
        }
        return options[cycleIndex(options.length)];
    }

    /** 轮换到第几个（见 {@link #cycling}） */
    public int cycleIndex(int length) {
        return cycling ? (int) ((now / 1000L) % length) : 0;
    }

    /**
     * 物品格：底框 + 物品 + 数量；鼠标压着时登记热区。
     *
     * @param options 候选物品（空数组画空格子）
     */
    public void slot(ItemStack[] options, int x, int y) {
        slot(cycle(options), options.length, x, y);
    }

    public void slot(ItemStack stack, int x, int y) {
        slot(stack, stack.isEmpty() ? 0 : 1, x, y);
    }

    private void slot(ItemStack stack, int options, int x, int y) {
        boolean hover = !stack.isEmpty() && hovered(x, y, 18, 18);
        GuideTheme.slot(g, x, y, hover ? 1F : 0F, alpha);
        if (stack.isEmpty()) {
            return;
        }
        g.renderItem(stack, x + 1, y + 1);
        g.renderItemDecorations(font, stack, x + 1, y + 1);
        if (options > 1) {
            // 有几种可选：右上角描边上一个静止的小三角（悬停出提示「N 种任选」，压在图上时才轮换）
            GuideTheme.slotAlternatives(g, x, y, alpha);
        }
        Item probe = showroomProbe;
        if (probe != null && showroomProbeX < 0 && stack.getItem() == probe && y + dy >= clipTop && y + dy + 18 <= clipBottom) {
            showroomProbeY = y + dy + 9;
            showroomProbeX = x + dx + 9;
        }
        if (hover) {
            List<Component> lines = options > 1
                    ? List.of(Component.translatable("guide.essentialcraft5.recipe.alternatives", options))
                    : List.of();
            hotspot(x, y, 18, 18, stack, lines);
        }
    }

    /**
     * 画一个方块的图标（16×16）：有物品形态的画物品，没有的（岩浆、水、火）画它的粒子贴图并染上方块颜色。
     */
    public void blockIcon(Block block, int x, int y) {
        if (block.asItem() != Items.AIR) {
            g.renderItem(BLOCK_STACKS.computeIfAbsent(block, ItemStack::new), x, y);
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        BlockState state = block.defaultBlockState();
        TextureAtlasSprite sprite = minecraft.getBlockRenderer().getBlockModel(state).getParticleIcon(ModelData.EMPTY);
        BlockPos pos = minecraft.player != null ? minecraft.player.blockPosition() : BlockPos.ZERO;
        int tint = minecraft.level != null ? minecraft.getBlockColors().getColor(state, minecraft.level, pos, 0) : -1;
        float r = tint == -1 ? 1F : ((tint >> 16) & 0xFF) / 255F;
        float gr = tint == -1 ? 1F : ((tint >> 8) & 0xFF) / 255F;
        float b = tint == -1 ? 1F : (tint & 0xFF) / 255F;
        g.setColor(r, gr, b, 1F);
        g.blit(x, y, 0, 16, 16, sprite);
        g.setColor(1F, 1F, 1F, 1F);
    }

    /** 缩小画的物品（图例、标签前的小图标），scale 0.5 即 8×8 */
    public void smallItem(ItemStack stack, int x, int y, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0F);
        g.pose().scale(scale, scale, 1F);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    public void smallBlock(Block block, int x, int y, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0F);
        g.pose().scale(scale, scale, 1F);
        blockIcon(block, 0, 0);
        g.pose().popPose();
    }

    public int text(int argb) {
        return GuideTheme.textAlpha(argb, alpha);
    }

    public int fill(int argb) {
        return GuideTheme.fade(argb, alpha);
    }
}
