package com.flameforge.essentialcraft5.client.guide;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.datafixers.util.Either;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * 查阅：任何界面里物品提示框出来时（物品栏、箱子、机器、配方查看界面之类），指着的物品在手册里有条目、
 * 而且身上带着研究手册，提示框末尾多一行「按住 Ctrl 在研究手册中查看」；按住约 0.7 秒（行首的小圆圈转满）就翻到那一条，
 * 关掉手册回到原来的界面。Mac 上是 Cmd（{@link Screen#hasControlDown()} 在 Mac 上查的就是 Cmd）。
 * <p>
 * 不在提示框旁边另画东西：进度圈做成提示框里的一行（{@link Hint}），
 * 位置、换行、遮挡都交给原版提示框排版。
 * <ul>
 *   <li>挂在 Forge 的 {@link RenderTooltipEvent.GatherComponents} 上：只有真正画出来的提示框才触发（每帧一次），
 *       不会像 ItemTooltipEvent 那样混进 JEI 搜索索引、创造模式搜索之类的离屏提示里。</li>
 *   <li>计时按墙钟：连续按住、指着同一种物品才累计，中间有一帧没画这个提示（鼠标移开）或松开 Ctrl 就从头来。</li>
 *   <li>打开一次后要先松开 Ctrl 才能再触发：关掉手册回到物品栏时手往往还按着 Ctrl，不能一回来又被打开。
 *       「松开了」每个客户端 tick 查一次（{@link #onGuideLookupClientTick}），不靠提示框：在手册里、在空白处松开的也算，
 *       不然下一次查阅要按两次。</li>
 *   <li>不在绘制途中换界面：到点后用 {@link Minecraft#tell} 排到下一帧开头再打开，届时界面已经换了就作罢。</li>
 * </ul>
 * 手册自己的界面不走这里（它的提示框是自己画的，点物品就能跳转）。
 * <p>
 * 「这个物品在哪一篇」查的是全局共用的 {@link GuideView#current()}（手册关着也能查）：只收可见的条目和块，
 * 物品只出现在被条件藏掉的条目 / 块里时不显示这一行；打开后直接翻到那一块并让它亮一下。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class GuideLookup {

    /** 按住多久打开（毫秒） */
    public static final long HOLD_MS = 700L;
    /** 超过这么久没画同一个物品的提示，就当鼠标移开了，重新计时 */
    private static final long GAP_MS = 150L;

    @Nullable
    private static Item holdItem;
    private static long holdStart;
    private static long lastSeen;
    private static boolean armed = true;

    private GuideLookup() {
    }

    /**
     * 提示框里的那一行：行首一个进度圈，后面是说明。
     *
     * @param progress 0~1，按住的进度
     */
    public record Hint(float progress) implements TooltipComponent {
    }

    @SubscribeEvent
    public static void onGatherTooltip(RenderTooltipEvent.GatherComponents event) {
        ItemStack stack = event.getItemStack();
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft.screen;
        if (stack.isEmpty() || minecraft.player == null || screen == null || screen instanceof GuideScreen) {
            return;
        }
        if (!GuideClient.hasBook(minecraft.player)) {
            return;
        }
        GuideView view = GuideView.current();
        GuideView.Target target = view.find(stack.getItem());
        GuideView.EntryView entry = target == null ? null : view.entries.get(target.entryKey());
        if (entry == null || view.lockedBy(entry) != null) {
            return;
        }
        try {
            event.getTooltipElements().add(Either.right(new Hint(progress(minecraft, screen, stack))));
        } catch (UnsupportedOperationException e) {
            // 别的模组用 renderComponentTooltipFromElements 传了个不可变列表进来：这个提示框就不加这一行
        }
    }

    /** 更新按住的计时，到点就排一次打开；返回当前进度 */
    private static float progress(Minecraft minecraft, Screen screen, ItemStack stack) {
        long now = Util.getMillis();
        if (!Screen.hasControlDown()) {
            armed = true;
            holdStart = 0L;
            return 0F;
        }
        if (!armed) {
            return 0F;
        }
        if (holdStart == 0L || holdItem != stack.getItem() || now - lastSeen > GAP_MS) {
            holdStart = now;
            holdItem = stack.getItem();
        }
        lastSeen = now;
        float progress = (now - holdStart) / (float) HOLD_MS;
        if (progress < 1F) {
            return progress;
        }
        armed = false;
        holdStart = 0L;
        ItemStack copy = stack.copy();
        minecraft.tell(() -> {
            if (minecraft.screen == screen) {
                GuideClient.lookup(copy, screen);
            }
        });
        return 1F;
    }

    /**
     * 松开 Ctrl 就重新允许触发。每 tick 查一次，不放在提示框里查：打开手册后提示框不画了（手册里、空白处、手册里没有的物品上都不画），
     * 只在那里查的话，在这些地方松开的 Ctrl 都看不见，下一次查阅得先在提示框上按一下、松开、再按住
     */
    @SubscribeEvent
    public static void onGuideLookupClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !armed && !Screen.hasControlDown()) {
            armed = true;
            holdStart = 0L;
        }
    }

    /** {@link Hint} 的绘制，在 GuideClient 里登记（RegisterClientTooltipComponentFactoriesEvent） */
    public static final class HintRenderer implements ClientTooltipComponent {

        /** 进度圈占的宽度（含右边空隙） */
        private static final int RING_W = 12;
        private static final float RADIUS = 3.5F;
        private static final int SEGMENTS = 20;
        /** 没按住时的字色：薄雾紫灰（原版物品提示的底本来就是深紫，这个颜色压得住） */
        private static final int IDLE_TEXT = GuideTheme.NAV_TEXT_DIM;

        private final float progress;
        private final FormattedCharSequence text;

        public HintRenderer(Hint hint) {
            this.progress = hint.progress();
            String key = Minecraft.ON_OSX ? "Cmd" : "Ctrl";
            this.text = Component.translatable("guide.essentialcraft5.lookup.hold", key).getVisualOrderText();
        }

        @Override
        public int getHeight() {
            return 10;
        }

        @Override
        public int getWidth(Font font) {
            return RING_W + font.width(text);
        }

        @Override
        public void renderText(Font font, int x, int y, Matrix4f matrix, MultiBufferSource.BufferSource buffer) {
            int color = progress > 0F ? GuideTheme.LINK : IDLE_TEXT;
            font.drawInBatch(text, x + RING_W, y, color, true, matrix, buffer, Font.DisplayMode.NORMAL, 0, 0xF000F0);
        }

        @Override
        public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
            float cx = x + 4.5F;
            float cy = y + 3.5F;
            BufferBuilder buffer = Shapes.begin(graphics);
            Matrix4f matrix = graphics.pose().last().pose();
            // 底圈常亮，按住时从正上方顺时针描一圈品红
            ring(buffer, matrix, cx, cy, 1F, GuideTheme.RING_TRACK);
            if (progress > 0F) {
                ring(buffer, matrix, cx, cy, Math.min(1F, progress), GuideTheme.FOCUS);
            }
            Shapes.end();
        }

        private static void ring(BufferBuilder buffer, Matrix4f matrix, float cx, float cy, float fraction, int argb) {
            int segments = Math.max(1, Math.round(SEGMENTS * fraction));
            double step = Math.PI * 2 * fraction / segments;
            double start = -Math.PI / 2;
            for (int i = 0; i < segments; i++) {
                double a = start + step * i;
                double b = a + step;
                Shapes.line(buffer, matrix, cx + (float) Math.cos(a) * RADIUS, cy + (float) Math.sin(a) * RADIUS,
                        cx + (float) Math.cos(b) * RADIUS, cy + (float) Math.sin(b) * RADIUS, 1.4F, argb);
            }
        }
    }
}
