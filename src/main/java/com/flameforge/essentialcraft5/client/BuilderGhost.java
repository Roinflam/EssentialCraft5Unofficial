package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxEffect;
import com.flameforge.essentialcraft5.client.vfx.VfxEffects;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import com.flameforge.essentialcraft5.client.vfx.VfxPalette;
import com.flameforge.essentialcraft5.item.BuilderArea;
import com.flameforge.essentialcraft5.item.BuilderJobs;
import com.flameforge.essentialcraft5.item.BuilderMix;
import com.flameforge.essentialcraft5.item.BuilderMode;
import com.flameforge.essentialcraft5.item.BuilderSelection;
import com.flameforge.essentialcraft5.item.BuilderShape;
import com.flameforge.essentialcraft5.item.BuilderShapes;
import com.flameforge.essentialcraft5.item.BuilderSurface;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.network.S2CBuilderStatusPacket;
import com.flameforge.essentialcraft5.item.BuilderTier;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.Objects;

/**
 * 创世神之杖的预览：拿着魔杖时算出这一下会动哪些格子（准星下提示 {@link BuilderHud} 的「要放」「要多少 MRU」也读这里），
 * 再把范围画在世界里（tools/plan/builder_upgrade.md §3.3、§6.1）。
 * <ul>
 *   <li>按需重算：形状、尺寸、朝向、点中的格子和面（没点中时是脚下）、手里的魔杖和材料变了，或者每 {@value #REFRESH_TICKS} 刻一次；每帧只画缓存。
 *       范围的几何和服务端共用 {@link BuilderShapes}，每格怎么做用 {@link BuilderMode#decide}、{@link BuilderJobs#classify}、
 *       {@link BuilderJobs#placementState}、{@link BuilderJobs#canStand}，都按本地世界算，只作提示（开工时服务端用自己的数据重算）。</li>
 *   <li>按格子顺序数材料和 MRU：手头的料（状态包里身上加随身容器的数）和可用 MRU 够到哪一格，后面的格子算「不够」。</li>
 *   <li>画法（tools/plan/builder_upgrade.md 开头挑定的「顶面网格」）：不画方块，只在范围朝外的那一面画细线网格（铺面、直线、两点是顶面，
 *       竖墙是朝着玩家的那面，沿面是点中的那一面），外框粗一点亮一点，四角立柱，格子里很淡的底色。够的格子薰衣草紫，材料或 MRU 不够的格子
 *       底色和网格变红，清除类是粉红；这一格放不了（被占着、立不住、有人站着、出了建筑高度或世界边界）只画一个暗红小框。
 *       底色和网格线都是纯色半透明面片（不走特效着色器，那边的光带芯是白的，线会发白），离镜头很近的线淡下去。要画的格子超过客户端配置 {@code ghost_max} 时只画外框和网格。
 *       几何在预览重算时攒成 {@link Mesh}，每帧照着画。</li>
 *   <li>只在施工、撤销、暂停都没有时画（状态包空闲或排队中），按 F1 藏界面时不画；这些结束以后先停 {@value #HOLD_TICKS} 刻、
 *       再用 {@value #FADE_TICKS} 刻淡入，让刚铺好的那片先露出来，不马上被下一片的虚影盖住。两点形状按选区画外框和网格。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class BuilderGhost {

    /** 每这么多刻重算一次（世界变了、材料数变了跟着变） */
    static final int REFRESH_TICKS = 10;
    /** 逐格查「放不放得下」最多查这么多格，再往后只按模式数（配置允许的最大选区 32768 格时每次重算也不卡） */
    static final int DETAIL_LIMIT = BuilderJobs.PREVIEW_DETAIL_LIMIT;

    /** 格子的样子（面板蓝图也按这几种上色）。cells 数组比实际画的格子长，没填的那几项是 OK 且 states 为 null */
    public static final byte OK = 0;
    public static final byte SHORT = 1;
    public static final byte BLOCKED = 2;
    public static final byte BREAK = 3;

    /** 不够的格子的颜色 */
    private static final int SHORT_COLOR = 0xFF5A6E;
    /** 放不了的格子的小框 */
    private static final int BLOCKED_COLOR = 0xA8323F;
    /** 清除类要拆的格子的颜色 */
    private static final int BREAK_COLOR = 0xFF7A9A;

    /** 沿面预览最多算这么多格（服务端按档位的格数测）；档位上限比它大、又真的连到了这么多格时只画前这么多，提示里写明 */
    public static final int FACE_PREVIEW_LIMIT = BuilderJobs.PREVIEW_DETAIL_LIMIT;

    /** {@link Preview#note}：没什么要说的 */
    public static final int NOTE_NONE = 0;
    /** 沿面连到了档位的格数上限，只铺离点中处近的那些 */
    public static final int NOTE_CAPPED = 1;
    /** 沿面预览只算了前 {@link #FACE_PREVIEW_LIMIT} 格（实际开工时服务端测得更多） */
    public static final int NOTE_PREVIEW_CAPPED = 2;
    /** 沿面点中的那一面不能沿着铺（被盖住、是空气或液体） */
    public static final int NOTE_COVERED = 3;

    /**
     * 一次预览的结果。shape 为两点时 cells 为 null（不画），只给提示用的数。
     * width / length：铺面、直线是宽、长，竖墙是宽、高，两点是外框的 x、z（height 是 y），沿面 width 是测出来的格数。
     *
     * @param place    要放（含替换）的格数，含材料 / MRU 不够的，不含放不了的
     * @param breaks   要拆的格数
     * @param blocked  放不了的格数
     * @param needMru  这些格子合计要多少 MRU（打过折）
     * @param tooBig   两点的选区超出上限
     * @param unset    两点还没设好、或者不在这个维度；沿面没对准方块、或点中的那一面不能沿着铺（note 为 {@link #NOTE_COVERED}）
     * @param note     沿面的附注（{@link #NOTE_NONE} 等）
     * @param anchor   范围的锚点（混铺按它和外框取种子，面板蓝图按表上色用）；没有范围时为 null
     * @param facing   算这份预览时玩家的水平朝向（面板蓝图按它把「前方」朝上画）
     * @param needByKind 混铺时表里每种要放几格（按表的顺序；准星下的缺料红字按每种判）；没混铺为 null
     */
    public record Preview(InteractionHand hand, BuilderShape shape, BuilderMode mode, @Nullable Block block, int width, int length, int height,
                          int place, int breaks, int blocked, long needMru, boolean tooBig, boolean unset,
                          @Nullable long[] cells, @Nullable byte[] kinds, @Nullable BlockState[] states, @Nullable BoundingBox box,
                          boolean outlineOnly, boolean anyShort, int note, @Nullable BlockPos anchor, Direction facing,
                          @Nullable int[] needByKind) {
    }

    /** 沿面的测量结果按「点中的格子和面、这两格的方块、上限、状态」缓存：这些都没变就不重算（预览每 10 刻重算一次时直接拿来用） */
    private record FaceKey(ResourceKey<Level> dimension, BlockPos pos, Direction face, BlockState state, BlockState outer, int limit, int radius,
                           int statusState) {
    }

    @Nullable
    private static FaceKey faceKey;
    @Nullable
    private static BuilderSurface.Scan faceScan;

    /** 判断要不要重算 */
    private record Key(InteractionHand hand, BuilderShape shape, BuilderMode mode, @Nullable Block block, int width, int length,
                       Direction facing, @Nullable BlockPos hitPos, @Nullable Direction hitFace, BlockPos feet,
                       @Nullable BlockPos first, @Nullable BlockPos second, long bucket, boolean creative,
                       @Nullable S2CBuilderStatusPacket status, int ghostMax) {
    }

    @Nullable
    private static Preview preview;
    @Nullable
    private static Key lastKey;

    private BuilderGhost() {
    }

    /** 最近一次算好的预览；没拿魔杖返回 null */
    @Nullable
    public static Preview preview() {
        return preview;
    }

    @SubscribeEvent
    public static void onBuilderGhostTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        tickHold();
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        InteractionHand hand = player == null ? null : BuilderInput.wandHand(player);
        // 虚影和准星提示都关了也照算：面板开着时蓝图和左下三行要用这份预览
        if (level == null || hand == null || !BuilderClientConfig.ghost() && !BuilderClientConfig.hud()
                && !(minecraft.screen instanceof com.flameforge.essentialcraft5.client.screen.BuilderPanelScreen)) {
            preview = null;
            lastKey = null;
            faceKey = null;
            faceScan = null;
            clearMesh();
            return;
        }
        ItemStack wand = player.getItemInHand(hand);
        BuilderMode mode = BuilderMode.of(wand);
        BuilderShape shape = BuilderSelection.shape(wand);
        Block block = BuilderJobs.chooseBlock(player, hand, wand, mode);
        int[] size = shape.sized() ? BuilderInput.displaySize(hand, wand, shape) : new int[] {0, 0};
        BuilderShapes.Hit hit = shape.sized() ? hitOf(minecraft, player, wand) : null;
        BlockPos first = shape.sized() ? null : BuilderSelection.first(wand);
        BlockPos second = shape.sized() ? null : BuilderSelection.second(wand);
        if (first != null && !BuilderSelection.inDimension(wand, level.dimension())) {
            first = null;
            second = null;
        }
        Key key = new Key(hand, shape, mode, block, size[0], size[1], player.getDirection(), hit == null ? null : hit.pos(),
                hit == null ? null : hit.face(), BuilderShapes.feetAnchor(player), first, second, level.getGameTime() / REFRESH_TICKS,
                player.getAbilities().instabuild, BuilderClientStatus.latest(), BuilderClientConfig.ghostMax());
        if (key.equals(lastKey)) {
            return;
        }
        lastKey = key;
        preview = compute(player, level, hand, wand, mode, shape, block, size, hit, first, second);
        rebuildMesh(player, level, hit);
    }

    /**
     * 准星点中的方块和面；没点中方块（空气、实体）返回 null，同服务端：那时右键走的是「对空气用」，铺在脚下。
     * 形状用远距离瞄准时，原版准星什么都没点中就改看施工距离以内的远距离射线（{@link BuilderInput#farHit}），右键时按同一条射线发开工包。
     */
    @Nullable
    static BuilderShapes.Hit hitOf(Minecraft minecraft, LocalPlayer player, ItemStack wand) {
        HitResult result = minecraft.hitResult;
        if (result instanceof BlockHitResult blockHit && result.getType() == HitResult.Type.BLOCK) {
            return new BuilderShapes.Hit(blockHit.getBlockPos(), blockHit.getDirection());
        }
        return BuilderInput.farHit(minecraft, player, wand);
    }

    private static Preview compute(LocalPlayer player, ClientLevel level, InteractionHand hand, ItemStack wand, BuilderMode mode, BuilderShape shape,
                                   @Nullable Block block, int[] size, @Nullable BuilderShapes.Hit hit, @Nullable BlockPos first, @Nullable BlockPos second) {
        BuilderArea area;
        int width = size[0];
        int length = size[1];
        int height = 1;
        int note = NOTE_NONE;
        if (shape == BuilderShape.FACE) {
            if (hit == null) {
                return new Preview(hand, shape, mode, block, 0, 0, 0, 0, 0, 0, 0L, false, true, null, null, null, null, false, false, NOTE_NONE,
                        null, player.getDirection(), null);
            }
            BuilderSurface.Scan scan = faceScan(level, hit, BuilderTier.stats(wand));
            if (scan.invalid()) {
                return new Preview(hand, shape, mode, block, 0, 0, 0, 0, 0, 0, 0L, false, true, null, null, null, null, false, false, NOTE_COVERED,
                        null, player.getDirection(), null);
            }
            area = scan.area(mode.inPlace());
            width = area.size();
            length = 0;
            if (scan.capped()) {
                note = scan.limit() < BuilderTier.stats(wand).maxVolume() ? NOTE_PREVIEW_CAPPED : NOTE_CAPPED;
            }
        } else if (shape.sized()) {
            area = BuilderShapes.area(shape, new BuilderShapes.Size(width, length), player.getDirection(), hit, BuilderShapes.feetAnchor(player),
                    mode.inPlace());
        } else {
            if (first == null || second == null) {
                return new Preview(hand, shape, mode, block, 0, 0, 0, 0, 0, 0, 0L, false, true, null, null, null, null, false, false, NOTE_NONE,
                        null, player.getDirection(), null);
            }
            BoundingBox box = BuilderSelection.box(first, second);
            width = box.getXSpan();
            length = box.getZSpan();
            height = box.getYSpan();
            if (BuilderSelection.volume(first, second) > BuilderTier.stats(wand).maxVolume()) {
                return new Preview(hand, shape, mode, block, width, length, height, 0, 0, 0, 0L, true, false, null, null, null, box, false, false,
                        NOTE_NONE, null, player.getDirection(), null);
            }
            area = BuilderArea.box(box);
        }
        S2CBuilderStatusPacket status = BuilderClientStatus.latest();
        BuilderMix.Table mix = BuilderMix.forWand(wand, mode);
        long mixSeed = mix == null ? 0L : BuilderMix.seedFor(mix, area);
        int[] needByKind = mix == null ? null : new int[mix.size()];
        boolean creative = player.getAbilities().instabuild;
        // 没收到状态包时不知道手头有多少，先当够（不把整片标红）
        long material = creative || status == null ? Long.MAX_VALUE : status.have() + status.haveInContainers();
        long mru = creative || status == null || status.mru() < 0L ? Long.MAX_VALUE : status.mru();
        BuilderTier.Stats stats = BuilderTier.stats(wand);
        int placeCost = MRUDrain.discounted(player, stats.placeCost());
        int breakCost = MRUDrain.discounted(player, stats.breakCost());
        int replaceCost = MRUDrain.discounted(player, stats.replaceCost());
        Item item = block == null ? null : block.asItem();
        boolean bottomUp = mode.bottomUp();
        boolean draw = shape.sized();
        int total = area.size();
        long[] cells = draw ? new long[total] : null;
        byte[] kinds = draw ? new byte[total] : null;
        BlockState[] states = draw ? new BlockState[total] : null;
        int drawn = 0;
        int place = 0;
        int breaks = 0;
        int blocked = 0;
        long needMru = 0L;
        long materialUsed = 0L;
        long mruUsed = 0L;
        boolean anyShort = false;
        for (int i = 0; i < total; i++) {
            BlockPos pos = area.cellAt(i, bottomUp);
            boolean detail = i < DETAIL_LIMIT;
            if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
                if (bottomUp) {
                    blocked++;
                    drawn = mark(cells, kinds, states, drawn, pos, BLOCKED, null);
                }
                continue;
            }
            if (!level.hasChunkAt(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            // 混铺时表里任一种都算「选定的那种」（全部替换不去换表里的另一种，同服务端）
            boolean same = mix != null ? mix.contains(state) : block != null && state.is(block);
            BuilderMode.Action action = mode.decide(BuilderJobs.classify(level, pos, state), same);
            switch (action) {
                case PLACE, REPLACE -> {
                    BlockState placed = null;
                    if (block != null && detail) {
                        if (action == BuilderMode.Action.PLACE) {
                            placed = BuilderJobs.placementState(player, level, pos, block, item);
                            if (placed == null || !BuilderJobs.canStand(player, level, pos, placed)) {
                                blocked++;
                                drawn = mark(cells, kinds, states, drawn, pos, BLOCKED, null);
                                continue;
                            }
                        } else {
                            placed = block.defaultBlockState();
                        }
                    }
                    int cost = action == BuilderMode.Action.PLACE ? placeCost : replaceCost;
                    place++;
                    if (needByKind != null) {
                        needByKind[BuilderMix.kindAt(mix, mixSeed, area.box(), pos)]++;
                    }
                    needMru += cost;
                    boolean enough = materialUsed < material && mruUsed + cost <= mru;
                    if (enough) {
                        materialUsed++;
                        mruUsed += cost;
                    } else {
                        anyShort = true;
                    }
                    if (placed != null) {
                        drawn = mark(cells, kinds, states, drawn, pos, enough ? OK : SHORT, placed);
                    }
                }
                case BREAK -> {
                    breaks++;
                    needMru += breakCost;
                    drawn = mark(cells, kinds, states, drawn, pos, BREAK, null);
                }
                default -> {
                }
            }
        }
        BoundingBox box = area.box();
        boolean outlineOnly = draw && drawn > BuilderClientConfig.ghostMax();
        return new Preview(hand, shape, mode, block, width, length, height, place, breaks, blocked, needMru, false, false,
                cells, kinds, states, box, outlineOnly, anyShort, note, area.anchor(), player.getDirection(), needByKind);
    }

    /**
     * 沿面的测量（同服务端的 {@link BuilderSurface}，只读本地已加载的格子），上限取档位格数和 {@link #FACE_PREVIEW_LIMIT} 小的那个。
     * 点中的格子和面、那两格的方块、上限、状态包的状态都没变就用上一次的结果，不重算。
     */
    private static BuilderSurface.Scan faceScan(ClientLevel level, BuilderShapes.Hit hit, BuilderTier.Stats stats) {
        int limit = Math.min(stats.maxVolume(), FACE_PREVIEW_LIMIT);
        int radius = BuilderSurface.radius(stats.maxSide());
        S2CBuilderStatusPacket status = BuilderClientStatus.latest();
        FaceKey key = new FaceKey(level.dimension(), hit.pos(), hit.face(), level.getBlockState(hit.pos()),
                level.getBlockState(hit.pos().relative(hit.face())), limit, radius, status == null ? -1 : status.state());
        BuilderSurface.Scan cached = faceScan;
        if (cached != null && key.equals(faceKey)) {
            return cached;
        }
        BuilderSurface.Scan scan = BuilderSurface.scanAll(BuilderSurface.reader(level), hit.pos(), hit.face(), limit, radius);
        faceKey = key;
        faceScan = scan;
        return scan;
    }

    private static int mark(@Nullable long[] cells, @Nullable byte[] kinds, @Nullable BlockState[] states, int drawn, BlockPos pos, byte kind,
                            @Nullable BlockState state) {
        if (cells == null || drawn >= cells.length) {
            return drawn;
        }
        cells[drawn] = pos.asLong();
        kinds[drawn] = kind;
        states[drawn] = state;
        return drawn + 1;
    }

    // ------------------------------------------------------------------
    // 画
    // ------------------------------------------------------------------

    /** 施工、撤销、暂停结束后停这么多刻再画下一片的虚影，让刚铺好的那片先露出来 */
    static final int HOLD_TICKS = 30;
    /** 停完以后这么多刻淡入 */
    static final int FADE_TICKS = 10;
    /** 所有格子合在一起只画外框和网格时，整片底色的不透明度 */
    private static final float OUTLINE_FILL_ALPHA = 0.06F;
    /** 网格线：外框、内线的半宽 */
    private static final float EDGE_HALF = 0.07F;
    private static final float INNER_HALF = 0.045F;
    /** 网格线、底色离方块表面的距离：往空处放时缩进这一格里面（不和上方的方块打架），作用于已有方块时浮在表面外 */
    private static final double LINE_INSET = -0.01D;
    private static final double FILL_INSET = -0.03D;
    private static final double LINE_OUTSET = 0.025D;
    private static final double FILL_OUTSET = 0.01D;

    /** 本地刻数（不用世界的 gameTime：各维度的不一样，换维度后会算错） */
    private static int clientTicks;
    /** 到这一刻（{@link #clientTicks}）为止不画，之后淡入 */
    private static int holdUntil;
    @Nullable
    private static Mesh mesh;
    @Nullable
    private static GhostFx fx;

    /** 施工、撤销、暂停中（这时不画虚影） */
    private static boolean busy(@Nullable S2CBuilderStatusPacket status) {
        return status != null && status.state() != S2CBuilderStatusPacket.IDLE && status.state() != S2CBuilderStatusPacket.QUEUED
                && status.state() != S2CBuilderStatusPacket.SURVEYING;
    }

    /** 每刻记一次：施工中就把停顿往后推 */
    private static void tickHold() {
        clientTicks++;
        if (busy(BuilderClientStatus.latest())) {
            holdUntil = clientTicks + HOLD_TICKS;
        }
    }

    /** 这一帧虚影的不透明度系数，0 是不画 */
    private static float visibility(Minecraft minecraft, float partialTick) {
        if (mesh == null || preview == null || !BuilderClientConfig.ghost() || minecraft.options.hideGui || minecraft.level == null
                || busy(BuilderClientStatus.latest())) {
            return 0F;
        }
        return Mth.clamp((clientTicks - holdUntil + partialTick) / FADE_TICKS, 0F, 1F);
    }

    private static void rebuildMesh(LocalPlayer player, ClientLevel level, @Nullable BuilderShapes.Hit hit) {
        Preview current = preview;
        mesh = current == null ? null : Mesh.build(current, hit, player, level);
        // 特效表满了时登记不上，GhostFx 不会被走刻：隔一阵没走过刻就重新登记
        if (mesh != null && (fx == null || !fx.alive || clientTicks - fx.lastTick > 40)) {
            fx = new GhostFx();
            VfxEffects.add(fx);
        }
    }

    private static void clearMesh() {
        mesh = null;
    }

    /** 换世界、退出时特效表整个清空，这里跟着丢掉引用，下次重新登记 */
    @SubscribeEvent
    public static void onBuilderGhostLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            fx = null;
            mesh = null;
        }
    }

    /** 底色和网格线：纯色半透明面片（不走特效着色器，那边的光带芯是白的，整片铺开会发白，红格也看不出来）；放不了的格子画暗红小框 */
    @SubscribeEvent
    public static void onBuilderGhostRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Mesh current = mesh;
        float alpha = visibility(minecraft, event.getPartialTick());
        if (current == null || alpha <= 0F) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        if (current.fillArgb.length > 0) {
            VertexConsumer quads = buffers.getBuffer(RenderType.debugQuads());
            Matrix4f matrix = poseStack.last().pose();
            double[] f = current.fills;
            for (int i = 0; i < current.fillArgb.length; i++) {
                int argb = faded(current.fillArgb[i], alpha);
                int a = argb >>> 24;
                int r = argb >> 16 & 0xFF;
                int g = argb >> 8 & 0xFF;
                int b = argb & 0xFF;
                for (int k = 0; k < 4; k++) {
                    int o = i * 12 + k * 3;
                    quads.vertex(matrix, (float) (f[o] - camera.x), (float) (f[o + 1] - camera.y), (float) (f[o + 2] - camera.z))
                            .color(r, g, b, a).endVertex();
                }
            }
            buffers.endBatch(RenderType.debugQuads());
        }
        if (current.lineArgb.length > 0) {
            VertexConsumer quads = buffers.getBuffer(RenderType.debugQuads());
            Matrix4f matrix = poseStack.last().pose();
            double[] l = current.lines;
            for (int i = 0; i < current.lineArgb.length; i++) {
                int o = i * 9;
                float x0 = (float) (l[o] - camera.x);
                float y0 = (float) (l[o + 1] - camera.y);
                float z0 = (float) (l[o + 2] - camera.z);
                float x1 = (float) (l[o + 3] - camera.x);
                float y1 = (float) (l[o + 4] - camera.y);
                float z1 = (float) (l[o + 5] - camera.z);
                float sx = (float) l[o + 6];
                float sy = (float) l[o + 7];
                float sz = (float) l[o + 8];
                int argb = faded(current.lineArgb[i], alpha * nearFade(x0, y0, z0, x1, y1, z1));
                int a = argb >>> 24;
                if (a == 0) {
                    continue;
                }
                int r = argb >> 16 & 0xFF;
                int g = argb >> 8 & 0xFF;
                int b = argb & 0xFF;
                quads.vertex(matrix, x0 - sx, y0 - sy, z0 - sz).color(r, g, b, a).endVertex();
                quads.vertex(matrix, x1 - sx, y1 - sy, z1 - sz).color(r, g, b, a).endVertex();
                quads.vertex(matrix, x1 + sx, y1 + sy, z1 + sz).color(r, g, b, a).endVertex();
                quads.vertex(matrix, x0 + sx, y0 + sy, z0 + sz).color(r, g, b, a).endVertex();
            }
            buffers.endBatch(RenderType.debugQuads());
        }
        if (current.blocked.length > 0) {
            poseStack.pushPose();
            poseStack.translate(-camera.x, -camera.y, -camera.z);
            VertexConsumer lines = buffers.getBuffer(RenderType.lines());
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (long cell : current.blocked) {
                AABB box = new AABB(pos.set(cell)).deflate(0.3D);
                LevelRenderer.renderLineBox(poseStack, lines, box, r(BLOCKED_COLOR), g(BLOCKED_COLOR), b(BLOCKED_COLOR), 0.9F * alpha);
            }
            buffers.endBatch(RenderType.lines());
            poseStack.popPose();
        }
    }

    /** 离镜头这么近（格）以内的网格线淡到 {@link #NEAR_MIN_ALPHA} */
    private static final float NEAR_FADE_START = 1.2F;
    /** 离镜头这么远（格）起按原样画 */
    private static final float NEAR_FADE_END = 3.5F;
    private static final float NEAR_MIN_ALPHA = 0.2F;

    /**
     * 离镜头近的网格线变淡：人站在范围里或紧挨着范围时，脚边的线在画面底部会显得又粗又亮。
     * 按镜头（原点）到线段的最近距离算，参数是相对镜头的坐标。
     */
    private static float nearFade(float x0, float y0, float z0, float x1, float y1, float z1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float dz = z1 - z0;
        float len2 = dx * dx + dy * dy + dz * dz;
        float t = len2 < 1.0E-6F ? 0F : Mth.clamp(-(x0 * dx + y0 * dy + z0 * dz) / len2, 0F, 1F);
        float px = x0 + dx * t;
        float py = y0 + dy * t;
        float pz = z0 + dz * t;
        float d = Mth.sqrt(px * px + py * py + pz * pz);
        float k = Mth.clamp((d - NEAR_FADE_START) / (NEAR_FADE_END - NEAR_FADE_START), 0F, 1F);
        return NEAR_MIN_ALPHA + (1F - NEAR_MIN_ALPHA) * k * k * (3F - 2F * k);
    }

    private static int faded(int argb, float alpha) {
        int a = Math.round((argb >>> 24) * alpha);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    private static float r(int rgb) {
        return (rgb >> 16 & 0xFF) / 255F;
    }

    private static float g(int rgb) {
        return (rgb >> 8 & 0xFF) / 255F;
    }

    private static float b(int rgb) {
        return (rgb & 0xFF) / 255F;
    }

    /**
     * 四角立柱：走特效层的细长条，用普通半透明混合、芯宽取得很小，线显本色。
     * 不用加色混合：白天亮地面上加色会把薰衣草紫冲成白色，红格也看不出来。
     */
    private static final class GhostFx implements VfxEffect {
        boolean alive = true;
        int lastTick = clientTicks;

        @Override
        public boolean tick() {
            lastTick = clientTicks;
            alive = mesh != null && fx == this;
            return alive;
        }

        @Override
        public AABB bounds() {
            Mesh current = mesh;
            return current == null ? new AABB(0, 0, 0, 0, 0, 0) : current.bounds;
        }

        @Override
        public void render(VfxDraw draw) {
            Mesh current = mesh;
            float alpha = visibility(Minecraft.getInstance(), draw.partialTick());
            if (current == null || alpha <= 0F) {
                return;
            }
            double[] p = current.pillars;
            int argb = Vfx.argb(current.pillarRgb, 0.9F * alpha);
            Vec3 camera = draw.camera();
            for (int o = 0; o < p.length; o += 6) {
                pillar(draw, camera, p[o], p[o + 1], p[o + 2], p[o + 3], p[o + 4], p[o + 5], argb);
            }
        }

        /** 立柱：两点之间正对镜头的细长条，底端细、顶端粗一点 */
        private static void pillar(VfxDraw draw, Vec3 camera, double ax, double ay, double az, double bx, double by, double bz, int argb) {
            double dx = bx - ax;
            double dy = by - ay;
            double dz = bz - az;
            double mx = camera.x - (ax + bx) * 0.5D;
            double my = camera.y - (ay + by) * 0.5D;
            double mz = camera.z - (az + bz) * 0.5D;
            double sx = dy * mz - dz * my;
            double sy = dz * mx - dx * mz;
            double sz = dx * my - dy * mx;
            double len = Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (len < 1.0E-9D) {
                return;
            }
            double k0 = 0.03D / len;
            double k1 = 0.045D / len;
            int faint = Vfx.argb(argb & 0xFFFFFF, (argb >>> 24) / 255F * 0.25F);
            draw.quad(ax - sx * k0, ay - sy * k0, az - sz * k0, 0F, -1F, bx - sx * k1, by - sy * k1, bz - sz * k1, 1F, -1F,
                    bx + sx * k1, by + sy * k1, bz + sz * k1, 1F, 1F, ax + sx * k0, ay + sy * k0, az + sz * k0, 0F, 1F,
                    VfxMode.STRIP, 1, faint, true);
            draw.quad(ax - sx * k0, ay - sy * k0, az - sz * k0, 0F, -1F, bx - sx * k1, by - sy * k1, bz - sz * k1, 1F, -1F,
                    bx + sx * k1, by + sy * k1, bz + sz * k1, 1F, 1F, ax + sx * k0, ay + sy * k0, az + sz * k0, 0F, 1F,
                    VfxMode.STRIP, 1, argb, true);
        }
    }

    /**
     * 画网格的那一面：法线 normal，面内两轴 u、v（法线是 y 轴时 u = x、v = z；x 轴时 u = z、v = y；z 轴时 u = x、v = y），
     * 沿法线的坐标记作 p。
     */
    private record Plane(Direction normal) {

        Direction.Axis axis() {
            return normal.getAxis();
        }

        int u(BlockPos pos) {
            return axis() == Direction.Axis.X ? pos.getZ() : pos.getX();
        }

        int v(BlockPos pos) {
            return axis() == Direction.Axis.Y ? pos.getZ() : pos.getY();
        }

        int depth(BlockPos pos) {
            return pos.get(axis());
        }

        Direction uDir() {
            return axis() == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
        }

        Direction vDir() {
            return axis() == Direction.Axis.Y ? Direction.SOUTH : Direction.UP;
        }

        /** 这一格朝法线那一面的 p，再沿法线挪 offset（正数往外） */
        double face(int depth, double offset) {
            boolean positive = normal.getAxisDirection() == Direction.AxisDirection.POSITIVE;
            return (positive ? depth + 1 : depth) + (positive ? offset : -offset);
        }

        void put(DoubleArrayList out, double u, double v, double p) {
            switch (axis()) {
                case X -> {
                    out.add(p);
                    out.add(v);
                    out.add(u);
                }
                case Y -> {
                    out.add(u);
                    out.add(p);
                    out.add(v);
                }
                default -> {
                    out.add(u);
                    out.add(v);
                    out.add(p);
                }
            }
        }
    }

    /**
     * 一份预览画成什么，都是世界坐标，预览重算时重建一次，每帧照着画。
     * <ul>
     *   <li>每格只画朝法线那一面（铺面、直线、两点是顶面，竖墙是朝着玩家的那面，沿面是点中的那一面）；同一列里外面还有格子的不画。</li>
     *   <li>底色每格一块；网格线每格四条边，和旁边的格子共用的边只画一次、细一点，旁边没有格子的边是外框、粗一点亮一点。
     *       边的颜色按两边的格子取：任一边材料不够就红，清除类是粉红，其余薰衣草紫。</li>
     *   <li>四角立柱从范围的背面立到网格那一面（沿面没有，形状不规则）。</li>
     *   <li>格子数超过 {@code ghost_max} 时不逐格画：整片一块底色、每格一条网格线、外框（有不够的格子时外框是红的）。</li>
     * </ul>
     */
    private static final class Mesh {
        final double[] fills;
        final int[] fillArgb;
        final double[] lines;
        final int[] lineArgb;
        final double[] pillars;
        final int pillarRgb;
        final long[] blocked;
        final AABB bounds;

        private Mesh(Builder b, int pillarRgb) {
            this.fills = b.fills.toDoubleArray();
            this.fillArgb = b.fillArgb.toIntArray();
            this.lines = b.lines.toDoubleArray();
            this.lineArgb = b.lineArgb.toIntArray();
            this.pillars = b.pillars.toDoubleArray();
            this.pillarRgb = pillarRgb;
            this.blocked = b.blocked.toLongArray();
            this.bounds = b.bounds();
        }

        /** 格子的类别（底色、线的颜色按它取） */
        private static final byte PLACE = 0;
        private static final byte LACK = 1;
        private static final byte CLEAR = 2;

        @Nullable
        static Mesh build(Preview preview, @Nullable BuilderShapes.Hit hit, LocalPlayer player, ClientLevel level) {
            boolean clear = !preview.mode().bottomUp();
            int base = clear ? BREAK_COLOR : VfxPalette.LAVENDER;
            BoundingBox box = preview.box();
            if (preview.cells() == null) {
                // 两点：不逐格算，按选区画外框和网格
                if (preview.shape() != BuilderShape.POINTS || box == null || preview.tooBig() || preview.unset()) {
                    return null;
                }
                return outline(box, new Plane(Direction.UP), base, preview.anyShort(), preview.mode().inPlace(), true);
            }
            Plane plane = new Plane(normalOf(preview, hit, player));
            if (box == null) {
                return null;
            }
            if (preview.outlineOnly()) {
                return outline(box, plane, base, preview.anyShort(), preview.mode().inPlace(), preview.shape() != BuilderShape.FACE);
            }
            long[] cells = Objects.requireNonNull(preview.cells());
            byte[] kinds = Objects.requireNonNull(preview.kinds());
            BlockState[] states = Objects.requireNonNull(preview.states());
            Builder out = new Builder();
            Long2ByteOpenHashMap classes = new Long2ByteOpenHashMap();
            for (int i = 0; i < cells.length; i++) {
                byte kind = kinds[i];
                if (kind == BLOCKED) {
                    out.blocked.add(cells[i]);
                } else if (kind == BREAK) {
                    classes.put(cells[i], CLEAR);
                } else if (states[i] != null) {
                    classes.put(cells[i], kind == SHORT ? LACK : PLACE);
                }
            }
            LongOpenHashSet faces = new LongOpenHashSet();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (long cell : classes.keySet()) {
                pos.set(cell).move(plane.normal());
                if (!classes.containsKey(pos.asLong())) {
                    faces.add(cell);
                }
            }
            int minU = Integer.MAX_VALUE;
            int minV = Integer.MAX_VALUE;
            int maxU = Integer.MIN_VALUE;
            int maxV = Integer.MIN_VALUE;
            Direction uDir = plane.uDir();
            Direction vDir = plane.vDir();
            BlockPos.MutableBlockPos side = new BlockPos.MutableBlockPos();
            for (long cell : faces) {
                pos.set(cell);
                byte own = classes.get(cell);
                BlockState state = level.getBlockState(pos);
                boolean onSurface = !state.isAir() && state.canOcclude();
                double pFill = plane.face(plane.depth(pos), onSurface ? FILL_OUTSET : FILL_INSET);
                double pLine = plane.face(plane.depth(pos), onSurface ? LINE_OUTSET : LINE_INSET);
                int u = plane.u(pos);
                int v = plane.v(pos);
                minU = Math.min(minU, u);
                minV = Math.min(minV, v);
                maxU = Math.max(maxU, u);
                maxV = Math.max(maxV, v);
                out.fill(plane, u, v, u + 1, v + 1, pFill, Vfx.argb(colourOf(own, base), fillAlpha(own)));
                // 四条边：-u、+u、-v、+v
                for (int s = 0; s < 4; s++) {
                    Direction dir = switch (s) {
                        case 0 -> uDir.getOpposite();
                        case 1 -> uDir;
                        case 2 -> vDir.getOpposite();
                        default -> vDir;
                    };
                    side.setWithOffset(pos, dir);
                    long neighbour = side.asLong();
                    boolean shared = faces.contains(neighbour);
                    if (shared && (s == 0 || s == 2)) {
                        continue;
                    }
                    byte other = shared ? classes.get(neighbour) : own;
                    int rgb = colourOf(own == LACK || other == LACK ? LACK : own == CLEAR || other == CLEAR ? CLEAR : PLACE, base);
                    if (s < 2) {
                        double lu = s == 0 ? u : u + 1;
                        out.line(plane, lu, v, lu, v + 1, pLine, !shared, rgb, true);
                    } else {
                        double lv = s == 2 ? v : v + 1;
                        out.line(plane, u, lv, u + 1, lv, pLine, !shared, rgb, false);
                    }
                }
            }
            if (!faces.isEmpty() && preview.shape() != BuilderShape.FACE) {
                out.pillars(plane, minU, minV, maxU + 1, maxV + 1, box);
            }
            for (long cell : out.blocked) {
                out.include(BlockPos.getX(cell), BlockPos.getY(cell), BlockPos.getZ(cell));
            }
            return out.empty() ? null : new Mesh(out, base);
        }

        /** 只画外框和网格（两点、格子太多）：整片一块底色、每格一条线，外框粗一点（有不够的格子时是红的） */
        private static Mesh outline(BoundingBox box, Plane plane, int base, boolean anyShort, boolean onSurface, boolean pillars) {
            Builder out = new Builder();
            BlockPos min = new BlockPos(box.minX(), box.minY(), box.minZ());
            BlockPos max = new BlockPos(box.maxX(), box.maxY(), box.maxZ());
            int front = plane.normal().getAxisDirection() == Direction.AxisDirection.POSITIVE ? plane.depth(max) : plane.depth(min);
            double pFill = plane.face(front, onSurface ? FILL_OUTSET : FILL_INSET);
            double pLine = plane.face(front, onSurface ? LINE_OUTSET : LINE_INSET);
            int u0 = Math.min(plane.u(min), plane.u(max));
            int u1 = Math.max(plane.u(min), plane.u(max)) + 1;
            int v0 = Math.min(plane.v(min), plane.v(max));
            int v1 = Math.max(plane.v(min), plane.v(max)) + 1;
            out.fill(plane, u0, v0, u1, v1, pFill, Vfx.argb(base, OUTLINE_FILL_ALPHA));
            for (int u = u0 + 1; u < u1; u++) {
                out.line(plane, u, v0, u, v1, pLine, false, base, true);
            }
            for (int v = v0 + 1; v < v1; v++) {
                out.line(plane, u0, v, u1, v, pLine, false, base, false);
            }
            int frame = anyShort ? SHORT_COLOR : base;
            out.line(plane, u0, v0, u0, v1, pLine, true, frame, true);
            out.line(plane, u1, v0, u1, v1, pLine, true, frame, true);
            out.line(plane, u0, v0, u1, v0, pLine, true, frame, false);
            out.line(plane, u0, v1, u1, v1, pLine, true, frame, false);
            if (pillars) {
                out.pillars(plane, u0, v0, u1, v1, box);
            }
            return new Mesh(out, base);
        }

        /** 网格画在哪一面：竖墙朝着玩家那面，沿面是点中的那一面，其余是顶面 */
        private static Direction normalOf(Preview preview, @Nullable BuilderShapes.Hit hit, LocalPlayer player) {
            if (preview.shape() == BuilderShape.FACE) {
                return hit == null ? Direction.UP : hit.face();
            }
            BoundingBox box = preview.box();
            if (preview.shape() != BuilderShape.WALL || box == null) {
                return Direction.UP;
            }
            Direction.Axis axis;
            if (box.getXSpan() == 1 && box.getZSpan() != 1) {
                axis = Direction.Axis.X;
            } else if (box.getZSpan() == 1 && box.getXSpan() != 1) {
                axis = Direction.Axis.Z;
            } else {
                axis = player.getDirection().getAxis().isHorizontal() ? player.getDirection().getAxis() : Direction.Axis.Z;
            }
            double centre = axis == Direction.Axis.X ? (box.minX() + box.maxX() + 1) / 2D : (box.minZ() + box.maxZ() + 1) / 2D;
            double eye = axis == Direction.Axis.X ? player.getEyePosition().x : player.getEyePosition().z;
            return Direction.fromAxisAndDirection(axis, eye >= centre ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
        }

        private static int colourOf(byte kind, int base) {
            return kind == LACK ? SHORT_COLOR : kind == CLEAR ? BREAK_COLOR : base;
        }

        private static float fillAlpha(byte kind) {
            return kind == LACK ? 0.32F : kind == CLEAR ? 0.18F : 0.14F;
        }
    }

    /** 攒 {@link Mesh} 的数据，顺带记外接框（视锥剔除用） */
    private static final class Builder {
        final DoubleArrayList fills = new DoubleArrayList();
        final IntArrayList fillArgb = new IntArrayList();
        final DoubleArrayList lines = new DoubleArrayList();
        final IntArrayList lineArgb = new IntArrayList();
        final DoubleArrayList pillars = new DoubleArrayList();
        final LongArrayList blocked = new LongArrayList();
        private double minX = Double.MAX_VALUE;
        private double minY = Double.MAX_VALUE;
        private double minZ = Double.MAX_VALUE;
        private double maxX = -Double.MAX_VALUE;
        private double maxY = -Double.MAX_VALUE;
        private double maxZ = -Double.MAX_VALUE;

        boolean empty() {
            return fillArgb.isEmpty() && lineArgb.isEmpty() && blocked.isEmpty();
        }

        void include(double x, double y, double z) {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x + 1);
            maxY = Math.max(maxY, y + 1);
            maxZ = Math.max(maxZ, z + 1);
        }

        AABB bounds() {
            if (minX > maxX) {
                return new AABB(0, 0, 0, 0, 0, 0);
            }
            return new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(1D);
        }

        private void includeLast(DoubleArrayList list, int points) {
            int n = list.size();
            for (int i = n - points * 3; i < n; i += 3) {
                include(list.getDouble(i), list.getDouble(i + 1), list.getDouble(i + 2));
            }
        }

        void fill(Plane plane, double u0, double v0, double u1, double v1, double p, int argb) {
            plane.put(fills, u0, v0, p);
            plane.put(fills, u1, v0, p);
            plane.put(fills, u1, v1, p);
            plane.put(fills, u0, v1, p);
            fillArgb.add(argb);
            includeLast(fills, 4);
        }

        /** 一条网格线：两端在面内，半宽沿另一条轴（acrossU：线沿 v 走、半宽沿 u） */
        void line(Plane plane, double u0, double v0, double u1, double v1, double p, boolean edge, int rgb, boolean acrossU) {
            plane.put(lines, u0, v0, p);
            plane.put(lines, u1, v1, p);
            includeLast(lines, 2);
            float half = edge ? EDGE_HALF : INNER_HALF;
            // 半宽向量：面内平移 half，不沿法线（把「从原点到这一点」减掉就是方向）
            DoubleArrayList tmp = new DoubleArrayList(6);
            plane.put(tmp, acrossU ? half : 0D, acrossU ? 0D : half, 0D);
            lines.add(tmp.getDouble(0));
            lines.add(tmp.getDouble(1));
            lines.add(tmp.getDouble(2));
            // 外框更宽、颜色往白里提三成，比内线亮一些，又不至于发白
            lineArgb.add(Vfx.argb(edge ? lighten(rgb, 0.3F) : rgb, edge ? 0.95F : 0.8F));
        }

        private static int lighten(int rgb, float k) {
            int r = rgb >> 16 & 0xFF;
            int g = rgb >> 8 & 0xFF;
            int b = rgb & 0xFF;
            r += Math.round((255 - r) * k);
            g += Math.round((255 - g) * k);
            b += Math.round((255 - b) * k);
            return r << 16 | g << 8 | b;
        }

        /** 四角立柱：面内四个角，从范围背面立到网格那一面 */
        void pillars(Plane plane, double u0, double v0, double u1, double v1, BoundingBox box) {
            boolean positive = plane.normal().getAxisDirection() == Direction.AxisDirection.POSITIVE;
            BlockPos min = new BlockPos(box.minX(), box.minY(), box.minZ());
            BlockPos max = new BlockPos(box.maxX(), box.maxY(), box.maxZ());
            double back = positive ? plane.depth(min) : plane.depth(max) + 1;
            double front = positive ? plane.depth(max) + 1 : plane.depth(min);
            double[][] corners = {{u0, v0}, {u1, v0}, {u0, v1}, {u1, v1}};
            for (double[] c : corners) {
                plane.put(pillars, c[0], c[1], back);
                plane.put(pillars, c[0], c[1], front);
            }
            includeLast(pillars, 8);
        }
    }
}
