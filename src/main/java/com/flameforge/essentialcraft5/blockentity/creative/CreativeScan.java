package com.flameforge.essentialcraft5.blockentity.creative;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 无尽 MRU 源、无尽 ESPE 源的周期扫描（只放创造栏的道具，registry/ECCreative）。
 * <p>
 * 不逐格查方块（半径 8 的立方体有 4 913 格），而是把范围压到的区块（最多 3 × 3 个）的方块实体表各翻一遍：
 * 区块用 {@code getChunkNow} 取，没完全加载的直接跳过、绝不触发加载（和 {@code util/ECLevels} 同一个判法）；
 * 范围外的只比一下坐标，范围内的一轮最多看 {@code maxExamined} 个，再多就不看了（机器摆得比这还密的地方本来就不该放一个源顶全部）。
 * 补给时用 {@link Cursor} 从上一轮停下的地方接着轮，范围里的机器比每轮上限多时也轮得到每一台。
 */
public final class CreativeScan {

    private CreativeScan() {
    }

    /**
     * 收集 center 周围（三个轴各不超过 radius 格）、mapper 返回非 null 的方块实体，不含 center 自己。
     *
     * @param maxExamined 这一轮最多看多少个范围内的方块实体
     */
    public static <T> List<T> collect(Level level, BlockPos center, int radius, int maxExamined, Function<BlockEntity, T> mapper) {
        List<T> out = new ArrayList<>();
        int minCx = SectionPos.blockToSectionCoord(center.getX() - radius);
        int maxCx = SectionPos.blockToSectionCoord(center.getX() + radius);
        int minCz = SectionPos.blockToSectionCoord(center.getZ() - radius);
        int maxCz = SectionPos.blockToSectionCoord(center.getZ() + radius);
        int examined = 0;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    BlockPos pos = blockEntity.getBlockPos();
                    if (blockEntity.isRemoved() || pos.equals(center)
                            || Math.abs(pos.getX() - center.getX()) > radius
                            || Math.abs(pos.getY() - center.getY()) > radius
                            || Math.abs(pos.getZ() - center.getZ()) > radius) {
                        continue;
                    }
                    if (++examined > maxExamined) {
                        return out;
                    }
                    T mapped = mapper.apply(blockEntity);
                    if (mapped != null) {
                        out.add(mapped);
                    }
                }
            }
        }
        return out;
    }

    /** 轮流补给：每轮从上次停下的下一个开始，最多让 maxServed 个目标「真补到了」（serve 返回 true） */
    public static final class Cursor {
        private int next;

        /** @return 这一轮补到了几个 */
        public <T> int serve(List<T> targets, int maxServed, Predicate<T> serve) {
            int size = targets.size();
            if (size == 0) {
                return 0;
            }
            int begin = Math.floorMod(next, size);
            int served = 0;
            int visited = 0;
            while (visited < size && served < maxServed) {
                if (serve.test(targets.get((begin + visited) % size))) {
                    served++;
                }
                visited++;
            }
            next = begin + visited;
            return served;
        }
    }
}
