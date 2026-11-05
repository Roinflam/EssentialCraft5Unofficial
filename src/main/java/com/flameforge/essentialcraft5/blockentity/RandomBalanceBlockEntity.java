package com.flameforge.essentialcraft5.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.IntSupplier;

/**
 * 平衡值可以随机的产能器（热力产能器、自然熔炉；第二批的末影产能器也用）。
 * <p>
 * 原作这几台的配置 Balance 默认 -1，表示放下时在 [0, 2) 里随机取一个平衡值。
 * 这是冰冻（≤0.5）和混沌（≥1.5）MRU 的<b>唯一</b>来源：冰冻投影要冰冻 MRU，冷凝器又要冰冻投影，
 * 所以这个随机不能「修」掉（见 tools/plan/batch1.md §1.2）。
 * <p>
 * 原作的 bug：随机写在 firstTick 里，而 firstTick 不存盘，每次区块加载都会重新随机，玩家挑好的平衡值过一会就变了。
 * 这里改成第一次 tick 随机一次、连同「已随机」标记一起存盘；拆掉重放是新的方块实体，会重新随机（要保留，玩家靠它刷平衡值）。
 * <p>
 * 配置改成固定值（≥0）时每 tick 按配置设，改配置即时生效（同月亮井）。
 */
public abstract class RandomBalanceBlockEntity extends MRUBlockEntity {

    /** 配置里的这个值表示「放下时随机」 */
    public static final double RANDOM = -1D;

    private static final String TAG_ROLLED = "balance_rolled";

    private boolean balanceRolled;

    protected RandomBalanceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, IntSupplier maxMRU, int slots) {
        super(type, pos, state, maxMRU, slots);
    }

    /**
     * 在 serverTick 开头调用。
     *
     * @param configured 配置的平衡值；小于 0 表示随机
     */
    protected void applyBalance(double configured) {
        if (configured >= 0D) {
            mru.setBalance((float) configured);
            return;
        }
        if (!balanceRolled && level != null) {
            balanceRolled = true;
            mru.setBalance(level.random.nextFloat() * 2F);
            // 随机结果碰巧等于原值时 setBalance 不会标记存盘，这里补一下，免得「已随机」标记丢掉
            level.blockEntityChanged(worldPosition);
        }
    }

    /** 平衡值是否已经随机过（GameTest 用） */
    public boolean isBalanceRolled() {
        return balanceRolled;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean(TAG_ROLLED, balanceRolled);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        balanceRolled = tag.getBoolean(TAG_ROLLED);
    }
}
