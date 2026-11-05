package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.api.IElementalCrystal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 机器旁边的元素水晶（水晶控制器、水晶提取器共用；原作两台机器各自的 getCrystal）。
 * <p>
 * 原作每 tick 查东西南北四个方块实体（查到的后一个覆盖前一个，所以四面都有时取北边）。这里缓存找到的方向：
 * 平时每 tick 只查缓存那一格（一次区块查找）；那一格的水晶没了就马上重找；邻居变化（{@link #markDirty}，方块的 neighborChanged 调）
 * 或者每 {@value #RECHECK_INTERVAL} tick 也重找一次，照原作的优先顺序（北、南、西、东，见 {@link IElementalCrystal#adjacent}）。
 * 查找走 {@link IElementalCrystal#at}（{@code ECLevels.loadedBlockEntity}，不触发区块加载）。
 * <p>
 * 只缓存方向，不缓存水晶对象（接口约定：别跨 tick 持有）。两端都能用：客户端拿它找粒子要飞向哪边。
 */
public final class AdjacentCrystal {

    /** 没标脏时多少 tick 重找一次 */
    public static final int RECHECK_INTERVAL = 20;

    @Nullable
    private Direction side;
    private int recheck;

    /** 邻居变了：下次 {@link #find} 立即重找 */
    public void markDirty() {
        recheck = 0;
    }

    /** 旁边的水晶，没有返回 null。每 tick 调一次 */
    @Nullable
    public IElementalCrystal find(Level level, BlockPos pos) {
        if (recheck > 0) {
            recheck--;
            if (side == null) {
                return null;
            }
            IElementalCrystal cached = IElementalCrystal.at(level, pos.relative(side));
            if (cached != null) {
                return cached;
            }
            // 缓存那一格的水晶没了（被挖、区块卸载）：马上重找
        }
        recheck = RECHECK_INTERVAL;
        side = null;
        for (Direction direction : IElementalCrystal.ADJACENT_ORDER) {
            IElementalCrystal crystal = IElementalCrystal.at(level, pos.relative(direction));
            if (crystal != null) {
                side = direction;
                return crystal;
            }
        }
        return null;
    }

    /** 最近一次找到的水晶在哪一边（没找到为 null） */
    @Nullable
    public Direction side() {
        return side;
    }
}
