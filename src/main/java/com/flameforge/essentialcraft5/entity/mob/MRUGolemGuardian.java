package com.flameforge.essentialcraft5.entity.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * 聚集体身上「有没有聚能魔像守着」的记录（batch13.md §5.3.1）：一个状态加一个原位，存在聚集体的 NBT 里
 * （{@value #TAG_STATE}、{@value #TAG_ORIGIN}），由 {@code MRUPresenceEntity} 持有、存读档、合并。
 * <ul>
 *   <li>{@link State#NONE}：没有守护。<b>没有 {@value #TAG_STATE} 键的一律是它</b>：老存档里的上古遗迹、指令 / 刷怪蛋建的、收容瓶放出来的、
 *       富集仓里养的都是这样（{@code alwaysStay} 默认就是 true，不能拿它判遗迹）。</li>
 *   <li>{@link State#PENDING}：新生成的上古遗迹写的（{@code ElderMRUCUFeature}），魔像还没出来；带原位。</li>
 *   <li>{@link State#SPAWNED}：魔像真的进了世界（{@code MRUGolemTrigger}）。之后再挪聚集体也不改它，只是护盾够不着。</li>
 *   <li>{@link State#ABANDONED}：还没出来就被挪离原位超过 {@code abandon_distance} 格，这只魔像不会再出来。</li>
 * </ul>
 * 两团合并时「已触发 / 已放弃」优先（{@link #merge}），pending 不会被带进别人的那团。
 */
public final class MRUGolemGuardian {

    /** 聚集体 NBT：状态（小写名字） */
    public static final String TAG_STATE = "guardian";
    /** 聚集体 NBT：原位（遗迹生成时聚集体所在的格子） */
    public static final String TAG_ORIGIN = "guardian_origin";

    public enum State {
        NONE,
        PENDING,
        SPAWNED,
        ABANDONED;

        /** 已经有了结果（出来过或者放弃了），合并时压过别的 */
        public boolean resolved() {
            return this == SPAWNED || this == ABANDONED;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** 认不出的名字当没有守护 */
        public static State byId(String id) {
            for (State state : values()) {
                if (state.id().equals(id)) {
                    return state;
                }
            }
            return NONE;
        }
    }

    public static final MRUGolemGuardian NONE = new MRUGolemGuardian(State.NONE, null);

    private final State state;
    @Nullable
    private final BlockPos origin;

    private MRUGolemGuardian(State state, @Nullable BlockPos origin) {
        this.state = state;
        this.origin = state == State.NONE ? null : origin;
    }

    /** 新遗迹：等着触发，原位是聚集体生成时那一格 */
    public static MRUGolemGuardian pending(BlockPos origin) {
        return new MRUGolemGuardian(State.PENDING, origin.immutable());
    }

    public MRUGolemGuardian with(State next) {
        if (next == state) {
            return this;
        }
        return next == State.NONE ? NONE : new MRUGolemGuardian(next, origin);
    }

    public State state() {
        return state;
    }

    /** 原位；没有守护时是 null */
    @Nullable
    public BlockPos origin() {
        return origin;
    }

    public boolean pending() {
        return state == State.PENDING;
    }

    /**
     * 这团（{@code mine}）吞掉另一团（{@code theirs}）以后的记录：自己已有结果 → 留自己的；对方已有结果 → 取对方的；
     * 自己还在等 → 留自己的；只有对方在等 → 算放弃（对方的原位跟着被吞掉的那团没了，不把 pending 带进来）；两团都没有 → 没有。
     */
    public static MRUGolemGuardian merge(MRUGolemGuardian mine, MRUGolemGuardian theirs) {
        if (mine.state.resolved()) {
            return mine;
        }
        if (theirs.state.resolved()) {
            return theirs;
        }
        if (mine.state == State.PENDING) {
            return mine;
        }
        if (theirs.state == State.PENDING) {
            return theirs.with(State.ABANDONED);
        }
        return NONE;
    }

    /** 读聚集体 NBT：没有状态键的当没有守护；pending 却没有原位的（手改坏的）也当没有 */
    public static MRUGolemGuardian load(CompoundTag tag) {
        if (!tag.contains(TAG_STATE, Tag.TAG_STRING)) {
            return NONE;
        }
        State state = State.byId(tag.getString(TAG_STATE));
        BlockPos origin = tag.contains(TAG_ORIGIN, Tag.TAG_COMPOUND) ? NbtUtils.readBlockPos(tag.getCompound(TAG_ORIGIN)) : null;
        if (state == State.NONE || (state == State.PENDING && origin == null)) {
            return NONE;
        }
        return new MRUGolemGuardian(state, origin);
    }

    /** 写聚集体 NBT：没有守护时两个键都不写，NBT 和老存档一样 */
    public void save(CompoundTag tag) {
        if (state == State.NONE) {
            tag.remove(TAG_STATE);
            tag.remove(TAG_ORIGIN);
            return;
        }
        tag.putString(TAG_STATE, state.id());
        if (origin != null) {
            tag.put(TAG_ORIGIN, NbtUtils.writeBlockPos(origin));
        } else {
            tag.remove(TAG_ORIGIN);
        }
    }

    @Override
    public String toString() {
        return origin == null ? state.id() : state.id() + "@" + origin.toShortString();
    }
}
