package com.flameforge.essentialcraft5.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 放在方块实体里的结构判定缓存：不每 tick 重扫，区块没加载时不乱改机器状态。
 * <p>
 * 原作米斯莱熔炉每 tick 查 24 块结构（客户端也查），富集仓 1.12 扫描时沿途加载区块；这里：
 * <ul>
 *   <li>两次重算至少隔 {@link #MIN_INTERVAL}（20）tick；</li>
 *   <li>标脏（{@link #markDirty}：放下、邻居变化、读档）后，下一次 check 只要过了最小间隔就重算；
 *       没标脏时每 {@code recheckInterval} tick 复查一次（默认 20，结构大的机器可以调大，靠标脏及时响应）；</li>
 *   <li>只读已完全加载的区块。有格子在没加载的区块里、已加载的部分又都对得上时，本次结果是 UNKNOWN，
 *       {@link #formed()} 仍按<b>上次确定的结果</b>回答，并保持标脏，过了最小间隔再试。</li>
 * </ul>
 * 注意 neighborChanged 只报紧挨着机器的 6 格，结构里离得远的方块被拆时机器收不到通知，靠定期复查兜底
 * （米斯莱熔炉的 24 块护板没有一块紧挨熔炉，所以它必须保留默认的 20 tick 复查）。
 *
 * <h2>用法</h2>
 * <pre>{@code
 * public class MithrilineFurnaceBlockEntity extends MRUBlockEntity {
 *     private final StructureCache structure = new StructureCache(ECMithriline.FURNACE_STRUCTURE);
 *
 *     void serverTick() {
 *         boolean was = structure.formed();
 *         structure.check(level, worldPosition, Direction.NORTH);   // 四面对称，朝向随便给；有朝向的机器传 FACING
 *         if (!was && structure.formed()) { ... 第一次成形：触发进度之类 }
 *         if (!structure.formed()) { return; }
 *         for (BlockPos p : ECMithriline.FURNACE_STRUCTURE.positions(ECMithriline.PILLAR, worldPosition, Direction.NORTH)) {
 *             if (ECLevels.loadedBlockEntity(level, p) instanceof MithrilineCrystalBlockEntity pillar) { ... }
 *         }
 *     }
 *
 *     public StructureCache structure() { return structure; }   // 方块的 onPlace / neighborChanged 里调 be.structure().markDirty()
 *
 *     @Override protected void saveAdditional(CompoundTag tag) { super.saveAdditional(tag); structure.save(tag); }
 *     @Override public void load(CompoundTag tag) { super.load(tag); structure.load(tag); }
 * }
 * }</pre>
 * 界面提示：{@code structure.mismatch()} 不为 null 时 {@code mismatch().message()} 就是「x, y, z 处应为米斯莱护板」；
 * 要显示在客户端界面上，把 {@code mismatch().pos()} 的三个坐标放进 MachineData 同步，客户端自己拼文字。
 * <p>
 * 只保存「上次确定的结果」（存盘键默认 {@code structure}），读档后第一次判定若是 UNKNOWN（邻区块还没加载）就沿用它，
 * 富集仓这类「结构决定容量」的机器不会因为重进世界时邻区块晚加载一会儿就把存量截断。
 */
public final class StructureCache {

    /** 两次重算的最小间隔（tick） */
    public static final int MIN_INTERVAL = 20;
    /** 默认的存盘键 */
    public static final String NBT_KEY = "structure";

    private final StructurePattern pattern;
    private final int recheckInterval;
    private boolean dirty = true;
    /** 上次重算的游戏时间；Long.MIN_VALUE 表示从没算过 */
    private long computedAt = Long.MIN_VALUE;
    private StructureResult status = StructureResult.UNKNOWN;
    private StructureResult lastKnown = StructureResult.UNKNOWN;
    @Nullable
    private StructurePattern.Mismatch mismatch;

    /** 默认每 20 tick 复查一次 */
    public StructureCache(StructurePattern pattern) {
        this(pattern, MIN_INTERVAL);
    }

    /** @param recheckInterval 没标脏时的复查间隔（tick），小于 20 按 20 算；Integer.MAX_VALUE 表示只靠标脏 */
    public StructureCache(StructurePattern pattern, int recheckInterval) {
        this.pattern = Objects.requireNonNull(pattern);
        this.recheckInterval = Math.max(MIN_INTERVAL, recheckInterval);
    }

    public StructurePattern pattern() {
        return pattern;
    }

    /** 放下、邻居变化、读档后调用：下一次 check 过了最小间隔就重算 */
    public void markDirty() {
        dirty = true;
    }

    /**
     * 按节流规则判定：到时间了就重算，否则返回上次的结果。
     *
     * @param facing 机器的水平朝向（结构按北向写，见 StructurePattern）；四面对称的结构传什么都行
     * @return 本次（或缓存的）结果，可能是 UNKNOWN；机器是否工作请看 {@link #formed()}
     */
    public StructureResult check(Level level, BlockPos origin, Direction facing) {
        long now = level.getGameTime();
        if (computedAt != Long.MIN_VALUE) {
            long elapsed = now - computedAt;
            // gameTime 正常不会倒退（/time set 改的是 dayTime）；保险起见倒退也当作到期
            boolean due = elapsed < 0 || elapsed >= (dirty ? MIN_INTERVAL : recheckInterval);
            if (!due) {
                return status;
            }
        }
        return compute(level, origin, facing, now);
    }

    /** 不管节流，立即重算（右键机器、玩家手动刷新时用；别每 tick 调） */
    public StructureResult checkNow(Level level, BlockPos origin, Direction facing) {
        return compute(level, origin, facing, level.getGameTime());
    }

    private StructureResult compute(Level level, BlockPos origin, Direction facing, long now) {
        computedAt = now;
        StructurePattern.Match match = pattern.match(level, origin, facing);
        status = match.result();
        if (status == StructureResult.UNKNOWN) {
            // 保留上次确定的结果和不符位置，保持标脏，过了最小间隔再试
            dirty = true;
        } else {
            dirty = false;
            lastKnown = status;
            mismatch = match.mismatch();
        }
        return status;
    }

    /** 最近一次判定的结果（可能是 UNKNOWN；从没判定过也是 UNKNOWN） */
    public StructureResult status() {
        return status;
    }

    /** 最近一次<b>确定</b>的结果（MATCH / MISMATCH）；从没确定过（且存档里也没有）时是 UNKNOWN */
    public StructureResult lastKnown() {
        return lastKnown;
    }

    /** 结构是否成形：上次确定的结果是 MATCH。区块暂时没加载（UNKNOWN）不改变它 */
    public boolean formed() {
        return lastKnown == StructureResult.MATCH;
    }

    /** 上次确定为 MISMATCH 时第一个不符的格子；成形、没判定过、刚从存档读出来时为 null */
    @Nullable
    public StructurePattern.Mismatch mismatch() {
        return mismatch;
    }

    // ================================================================ 存读

    public void save(CompoundTag tag) {
        save(tag, NBT_KEY);
    }

    /** 只存上次确定的结果（一个字节）；没确定过就不写 */
    public void save(CompoundTag tag, String key) {
        if (lastKnown != StructureResult.UNKNOWN) {
            tag.putByte(key, (byte) lastKnown.ordinal());
        }
    }

    public void load(CompoundTag tag) {
        load(tag, NBT_KEY);
    }

    /** 读回上次确定的结果并标脏（读档后第一次 check 立即重算） */
    public void load(CompoundTag tag, String key) {
        if (tag.contains(key, Tag.TAG_BYTE)) {
            int ordinal = tag.getByte(key);
            StructureResult[] values = StructureResult.values();
            lastKnown = ordinal >= 0 && ordinal < values.length ? values[ordinal] : StructureResult.UNKNOWN;
        }
        mismatch = null;
        status = StructureResult.UNKNOWN;
        computedAt = Long.MIN_VALUE;
        dirty = true;
    }
}
