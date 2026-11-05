package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.entity.boss.BossDaily;
import com.flameforge.essentialcraft5.registry.ECSunkenDistrict;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 沉城祭坛的方块实体（第十三批 13e-S2，batch13.md §5.3.4）：只记这一座上一次召唤的游戏日序号和那一刻的主世界 gameTime
 * （{@code BossDaily.now(server)}；世界生成时不写，没召过是 −1）。不 tick；右键时才读（{@code SunkenAltarBlock.summon}）。
 * 「今天召过」照 {@code BossDaily}：日序号变大，或者离上次满 24 000 刻 gameTime，就是新的一天——{@code /time set} 往回拨不会多召一次，
 * 锁了日夜循环（{@code doDaylightCycle false}）也不会一辈子只召一次。
 */
public class SunkenAltarBlockEntity extends BlockEntity {

    /** 没召过 */
    public static final long NEVER = -1L;
    private static final String TAG_LAST_DAY = "last_day";
    private static final String TAG_LAST_GAME_TIME = "last_gt";

    private long lastDay = NEVER;
    /** 上一次召唤那一刻的主世界 gameTime（没记过 = {@link Long#MIN_VALUE}，只按日序号比） */
    private long lastGameTime = Long.MIN_VALUE;

    public SunkenAltarBlockEntity(BlockPos pos, BlockState state) {
        super(ECSunkenDistrict.SUNKEN_ALTAR_BE.get(), pos, state);
    }

    /** 上一次召唤的游戏日序号（没召过 {@value #NEVER}） */
    public long lastDay() {
        return lastDay;
    }

    /** 上一次召唤那一刻的主世界 gameTime（没记过是 {@link Long#MIN_VALUE}） */
    public long lastGameTime() {
        return lastGameTime;
    }

    /** now 这一天召过没有 */
    public boolean usedOn(BossDaily.Stamp now) {
        if (lastDay == NEVER) {
            return false;
        }
        if (lastGameTime == Long.MIN_VALUE) {
            return lastDay >= now.day();
        }
        return !now.isNewDaySince(lastDay, lastGameTime);
    }

    /** 记下 now 召过一次（日序号只往大里改） */
    public void markUsed(BossDaily.Stamp now) {
        lastDay = Math.max(lastDay, now.day());
        lastGameTime = now.gameTime();
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (lastDay != NEVER) {
            tag.putLong(TAG_LAST_DAY, lastDay);
        }
        if (lastGameTime != Long.MIN_VALUE) {
            tag.putLong(TAG_LAST_GAME_TIME, lastGameTime);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        lastDay = tag.contains(TAG_LAST_DAY, Tag.TAG_LONG) ? tag.getLong(TAG_LAST_DAY) : NEVER;
        lastGameTime = tag.contains(TAG_LAST_GAME_TIME, Tag.TAG_LONG) ? tag.getLong(TAG_LAST_GAME_TIME) : Long.MIN_VALUE;
    }
}
