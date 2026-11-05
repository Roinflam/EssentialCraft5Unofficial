package com.flameforge.essentialcraft5.client.ley;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.ley.LeyScan;
import com.flameforge.essentialcraft5.network.S2CLeyScanPacket;
import com.flameforge.essentialcraft5.registry.ECLeyDetector;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

/**
 * 地脉探测仪的客户端部分（第十二批，batch12.md §4.3.2）：收到 {@link S2CLeyScanPacket} 以后扫描、描边、到时淡出。
 * <ul>
 *   <li><b>扫描</b>：以收到包时玩家所在格为中心、边长 2r+1 的立方体，每个客户端刻（{@code ClientTickEvent} END、游戏没暂停）查 {@value #BLOCKS_PER_TICK} 格，
 *       半径 16 的 35 937 格 9 刻扫完（{@link LeyScan.Cursor}）。只看客户端本来就有的区块数据：装了反矿透插件的服务器上看到的就是插件发来的假矿，
 *       不会多知道服务器没发的东西。方块在 {@code ley_detector/targets} 里、不在 {@code …/ignored} 里才算；「只看贵重」时还要在 {@code …/valuable} 里。
 *       按距离留最近的 {@code max_highlights} 格。</li>
 *   <li><b>描边</b>：扫完才换上新结果（再扫一次替换上一次）。每格一个关深度测试的线框盒（隔墙可见），贵重金 #FFD34D、本模组的元素蕴魔矿紫 #B57CFF、
 *       其余青 #6FE3E3；画 {@code display_ticks} 刻，最后 {@value #FADE_TICKS} 刻透明度线性降到 0。那一格已经被挖掉、换成别的方块时不再画。
 *       画在 {@code AFTER_PARTICLES}（同建筑魔杖的选区框，那一套线框渲染已经在整合包里看过）。</li>
 *   <li>换维度、换世界、断线时清空。</li>
 * </ul>
 * 只在客户端主线程读写。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class LeyScanClient {

    /** 每个客户端刻查几格 */
    public static final int BLOCKS_PER_TICK = 4096;
    /** 最后这么多刻淡出 */
    public static final int FADE_TICKS = 60;
    /** 贵重矿 */
    public static final int COLOR_VALUABLE = 0xFFD34D;
    /** 本模组的元素蕴魔矿 */
    public static final int COLOR_EC = 0xB57CFF;
    /** 其余矿 */
    public static final int COLOR_OTHER = 0x6FE3E3;
    private static final double LINE_WIDTH = 2D;

    /** 一格描边：方块变了就不画 */
    private record Highlight(BlockPos pos, Block block, AABB box, float red, float green, float blue) {
    }

    @Nullable
    private static LeyScan.Cursor scanning;
    @Nullable
    private static ClientLevel scanningLevel;
    private static int pendingDisplayTicks;

    private static List<Highlight> shown = List.of();
    @Nullable
    private static ClientLevel shownLevel;
    private static long shownAt;
    private static int shownTicks;

    /** 没暂停时每个客户端刻 +1 */
    private static long ticks;

    private LeyScanClient() {
    }

    /** 收到扫描包（客户端主线程）：从玩家现在站的格子开始一次新扫描，上一次的轮廓留到新的扫完 */
    public static void handle(S2CLeyScanPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            return;
        }
        boolean valuableOnly = packet.valuableOnly();
        scanning = new LeyScan.Cursor(player.blockPosition(), packet.radius(), packet.maxHighlights(), state -> matches(state, valuableOnly));
        scanningLevel = level;
        pendingDisplayTicks = packet.displayTicks();
    }

    private static boolean matches(BlockState state, boolean valuableOnly) {
        return state.is(ECLeyDetector.TARGETS) && !state.is(ECLeyDetector.IGNORED) && (!valuableOnly || state.is(ECLeyDetector.VALUABLE));
    }

    private static int colorOf(BlockState state) {
        if (state.is(ECLeyDetector.VALUABLE)) {
            return COLOR_VALUABLE;
        }
        return state.is(ECLeyDetector.EC) ? COLOR_EC : COLOR_OTHER;
    }

    @SubscribeEvent
    public static void onLeyClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            clear();
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }
        ticks++;
        if (scanning != null) {
            if (scanningLevel != level) {
                scanning = null;
                scanningLevel = null;
            } else {
                scanning.step(level, BLOCKS_PER_TICK);
                if (scanning.done()) {
                    publish(scanning, level);
                    scanning = null;
                    scanningLevel = null;
                }
            }
        }
        if (!shown.isEmpty() && (shownLevel != level || ticks - shownAt >= shownTicks)) {
            shown = List.of();
            shownLevel = null;
        }
    }

    private static void publish(LeyScan.Cursor cursor, ClientLevel level) {
        List<Highlight> highlights = new ArrayList<>();
        for (LeyScan.Hit hit : cursor.results()) {
            int color = colorOf(hit.state());
            highlights.add(new Highlight(hit.pos(), hit.state().getBlock(), new AABB(hit.pos()).inflate(0.002D),
                    (color >> 16 & 0xFF) / 255F, (color >> 8 & 0xFF) / 255F, (color & 0xFF) / 255F));
        }
        shown = highlights;
        shownLevel = level;
        shownAt = ticks;
        shownTicks = pendingDisplayTicks;
    }

    /** 透明度：age 是显示了多少刻（带小数），最后 FADE_TICKS 刻从 1 线性降到 0 */
    static float alphaAt(float age, int displayTicks) {
        float left = displayTicks - age;
        if (left <= 0F) {
            return 0F;
        }
        int fade = Math.min(FADE_TICKS, displayTicks);
        return left >= fade ? 1F : left / fade;
    }

    @SubscribeEvent
    public static void onLeyRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || shown.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || level != shownLevel) {
            return;
        }
        float alpha = alphaAt(ticks - shownAt + event.getPartialTick(), shownTicks);
        if (alpha <= 0F) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(OutlineRenderType.LINES_THROUGH_WALLS);
        for (Highlight highlight : shown) {
            if (level.getBlockState(highlight.pos()).getBlock() != highlight.block()) {
                continue;
            }
            LevelRenderer.renderLineBox(poseStack, lines, highlight.box(), highlight.red(), highlight.green(), highlight.blue(), alpha);
        }
        buffers.endBatch(OutlineRenderType.LINES_THROUGH_WALLS);
        poseStack.popPose();
    }

    @SubscribeEvent
    public static void onLeyLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    private static void clear() {
        scanning = null;
        scanningLevel = null;
        shown = List.of();
        shownLevel = null;
    }

    /** 继承 RenderType 只是为了用 RenderStateShard 里 protected 的状态常量 */
    private static final class OutlineRenderType extends RenderType {

        /** 同原版 lines，但关深度测试（隔墙可见）、不写深度、半透明（淡出要用）、固定线宽 */
        static final RenderType LINES_THROUGH_WALLS = create(EssentialCraft5.MODID + ":ley_outline",
                DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 4096, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_LINES_SHADER)
                        .setLineState(new LineStateShard(OptionalDouble.of(LINE_WIDTH)))
                        .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(NO_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));

        private OutlineRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                                  boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
        }
    }
}
