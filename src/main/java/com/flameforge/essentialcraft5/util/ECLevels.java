package com.flameforge.essentialcraft5.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

/** 跨区块、跨维度访问世界的小工具。 */
public final class ECLevels {

    private ECLevels() {
    }

    /**
     * 取已经完全加载好的区块里的方块实体，区块没加载完就返回 null，绝不触发加载。
     * <p>
     * 不能用 {@code level.isLoaded(pos)} + {@code level.getBlockEntity(pos)}：isLoaded 只看票据等级，
     * 区块正在从磁盘加载时它已经是 true，接着 getBlockEntity 会在主线程上等区块加载完（卡服）。
     * 以后远程读写方块实体（绑定宝石、维度收发器、MRUCU……）都走这里。
     */
    @Nullable
    public static BlockEntity loadedBlockEntity(Level level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos)) {
            return null;
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow(
                SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
        return chunk == null ? null : chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.IMMEDIATE);
    }

    /** 区块是否已经完全加载（不触发加载） */
    public static boolean isFullyLoaded(Level level, BlockPos pos) {
        return !level.isOutsideBuildHeight(pos) && level.getChunkSource().getChunkNow(
                SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ())) != null;
    }
}
