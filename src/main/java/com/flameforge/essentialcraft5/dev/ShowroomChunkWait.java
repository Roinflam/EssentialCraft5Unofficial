package com.flameforge.essentialcraft5.dev;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * 展示厅场景的 {@code ready}（{@link DevShowroom.SceneShot#ready()}）：镜头到位、一片区域里的区块客户端都收到了、网格编译完，再拍。
 * <p>
 * 为什么不能只按刻数等（6a 审查 M6-1）：集成时悬空矿井那两张（04_scene_modern_shafts / _side）整屏是天空——传送到 640 格外时服务端
 * 「Can't keep up」落后 2.5 秒，区块还没发过来 / 没编译就到点拍了。这里按客户端的真实状态判断：
 * <ol>
 *   <li>在预定的维度里，玩家眼睛离预定的镜头位置 ≤ {@value #EYE_TOLERANCE} 格（传送已经到了客户端）；</li>
 *   <li>区域（方块坐标，外扩一圈区块）里、离镜头所在区块不超过「视距 − {@value #VIEW_MARGIN}」的区块客户端都有了
 *       （视距边上的区块服务端不一定发，发了也缺邻居编译不了，不等）；</li>
 *   <li>{@code LevelRenderer.hasRenderedAllChunks()}：编译队列空、没有待上传的网格；</li>
 *   <li>以上连续 {@value #STABLE_TICKS} 刻成立，视锥里渲染出来的区块数（{@code countRenderedChunks}）也没变、且不为 0
 *       （区块到了以后要等下一帧才排进编译，渲染图缺邻居时 500 ms 后才重建）。</li>
 * </ol>
 * 镜头位置要等服务端 prepare 算出来的（海纳尔那几张按真实地面取景），由服务端线程调 {@link #target}；取景失败调 {@link #skip}，不白等 30 秒。
 * 每张场景用自己的实例（计数器在里面）。{@link #test} 只在客户端主线程调。
 */
final class ShowroomChunkWait implements Predicate<Minecraft> {

    /** 条件连续成立这么多刻才算好 */
    static final int STABLE_TICKS = 20;
    /** 玩家眼睛离预定镜头位置多远以内算到位（格） */
    static final double EYE_TOLERANCE = 3;
    /** 离镜头所在区块超过「视距 − 这么多」的区块不等 */
    static final int VIEW_MARGIN = 2;

    private final ResourceKey<Level> dimension;
    @Nullable
    private volatile BoundingBox region;
    @Nullable
    private volatile Vec3 eye;
    private volatile boolean skip;
    // 下面三个只在客户端主线程读写
    private int stable;
    private int lastRendered = -1;
    private String state = "镜头还没到位";

    /** 镜头位置以后由服务端 {@link #target} 给 */
    ShowroomChunkWait(ResourceKey<Level> dimension) {
        this.dimension = dimension;
    }

    /** 镜头位置事先就知道（主世界的固定场景） */
    ShowroomChunkWait(ResourceKey<Level> dimension, Vec3 eye, BoundingBox region) {
        this(dimension);
        target(eye, region);
    }

    /** 服务端线程（站好位以后）：镜头眼睛在哪、要等哪一片（方块坐标）。先写区域再写眼睛：客户端读到眼睛就一定读得到区域 */
    void target(Vec3 eye, BoundingBox region) {
        this.region = region;
        this.eye = eye;
    }

    /** 服务端线程：这张没摆成（取景失败），不用等 */
    void skip() {
        this.skip = true;
    }

    /** 包住镜头和看向点、四周再外扩 margin 格的区域 */
    static BoundingBox around(Vec3 eye, Vec3 target, int margin) {
        return new BoundingBox(Mth.floor(Math.min(eye.x, target.x)) - margin, Mth.floor(Math.min(eye.y, target.y)) - margin,
                Mth.floor(Math.min(eye.z, target.z)) - margin, Mth.floor(Math.max(eye.x, target.x)) + margin,
                Mth.floor(Math.max(eye.y, target.y)) + margin, Mth.floor(Math.max(eye.z, target.z)) + margin);
    }

    @Override
    public boolean test(Minecraft mc) {
        if (skip) {
            return true;
        }
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        Vec3 wanted = eye;
        BoundingBox box = region;
        if (level == null || player == null || wanted == null || box == null) {
            return waiting("镜头还没到位");
        }
        if (!level.dimension().equals(dimension)) {
            return waiting("还不在 " + dimension.location());
        }
        if (player.getEyePosition().distanceToSqr(wanted) > EYE_TOLERANCE * EYE_TOLERANCE) {
            return waiting("镜头还没到位（在 " + player.getEyePosition() + "，要到 " + wanted + "）");
        }
        int reach = Math.max(1, mc.options.getEffectiveRenderDistance() - VIEW_MARGIN);
        int centerX = SectionPos.blockToSectionCoord(player.getBlockX());
        int centerZ = SectionPos.blockToSectionCoord(player.getBlockZ());
        int missing = 0;
        for (int cx = SectionPos.blockToSectionCoord(box.minX()) - 1; cx <= SectionPos.blockToSectionCoord(box.maxX()) + 1; cx++) {
            for (int cz = SectionPos.blockToSectionCoord(box.minZ()) - 1; cz <= SectionPos.blockToSectionCoord(box.maxZ()) + 1; cz++) {
                int dx = cx - centerX;
                int dz = cz - centerZ;
                if (dx * dx + dz * dz <= reach * reach && !level.getChunkSource().hasChunk(cx, cz)) {
                    missing++;
                }
            }
        }
        if (missing > 0) {
            return waiting("还差 " + missing + " 个区块没收到");
        }
        if (!mc.levelRenderer.hasRenderedAllChunks()) {
            return waiting("网格还在编译");
        }
        int rendered = mc.levelRenderer.countRenderedChunks();
        if (rendered == 0 || rendered != lastRendered) {
            lastRendered = rendered;
            stable = 0;
            state = "渲染出 " + rendered + " 个区块段，等它不再变";
            return false;
        }
        stable++;
        state = "稳定 " + stable + " 刻（" + rendered + " 个区块段）";
        return stable >= STABLE_TICKS;
    }

    private boolean waiting(String why) {
        stable = 0;
        lastRendered = -1;
        state = why;
        return false;
    }

    /** 超时的警告里打出来：卡在哪一步 */
    @Override
    public String toString() {
        return state;
    }
}
