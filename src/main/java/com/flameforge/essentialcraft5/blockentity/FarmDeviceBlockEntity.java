package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.IntSupplier;

/**
 * 农牧红石设备（ECFarmDevices，batch4 §3.1）的共同部分：魔力物品洗牌器、魔力繁殖器、魔力剪毛器。
 * <p>
 * 原作是一个方块 {@code essentialcraft:device} 的几个 meta（{@code BlockRedstoneDeviceNotSided}），没有方块实体，逻辑写在 {@code neighborChanged} 里：
 * 有信号期间<b>任何</b>邻居更新都扫一遍 25³（L173~221），每做一次 new 一个 FakePlayer，不看领地、不看是谁的动物。
 * 移植后每台带一个方块实体（只为记主人和上升沿闸门：0 MRU、0 槽、没有宝石槽、没有界面、不 tick），
 * 方块是 {@code PulseMachineBlock(ticking = false)}：红石从无到有那一下、过了最小间隔，在方块的计划刻里调 {@link #onRedstonePulse}，平时零开销。
 *
 * <h2>一次脉冲</h2>
 * <ol>
 *   <li>主人（policy §2.3）：没有主人不干（单人里算房主的，{@code ECProtection.ownerState} 已处理）；主人不在线默认不干，
 *       服主关了 {@code require_owner_online} 时照干（这时 {@code checkAsOwner} 直接放行）。不干的脉冲直接丢弃，不排队。</li>
 *   <li>{@link #collect}：以机器为中心 25×25×25（原作 {@code AxisAlignedBB(p).grow(12)}）查一次实体，先过不用探测的判断（类型、归属、掉落物拾取人），
 *       按离机器的距离排序，存进不存盘的候选表（最多 4 × 每次上限，免得一地掉落物攒出几千个候选）。<b>截断之前先把这次注定做不成的去掉</b>
 *       （繁殖器：不是谁的食物、谁都够不着的掉落物，够不着食物的动物；剪毛器：没毛可剪的），否则离机器近的一堆无关实体会把真正要处理的挤出表外（4a 审查 FARM-1）。</li>
 *   <li>{@link #process}：逐个候选再核一遍（这几刻里可能被人动过），最后才用主人的身份探测要动的格子（{@code ECProtection.checkAsOwner}，
 *       policy §2.4「只对真正要动的格子探测」）：放行就做、拒绝就丢掉这个候选、UNKNOWN（本刻探测预算用完）留到下一刻。
 *       「每次最多 N」数的是<b>真正做成</b>的次数（{@link #doneThisPulse}）。</li>
 *   <li>还有 UNKNOWN 又没做满时返回 true，框架下一刻调 {@link #continuePulse} 接着做，最长到最小间隔结束（{@code PulseGate#mayContinue}）；
 *       到点没做完的就算了，下一次脉冲重新查。续做只处理候选表，不再查实体（P2：大范围实体查询每次脉冲只有一次，脉冲间隔下限 10 刻）。</li>
 * </ol>
 * 候选表、做成次数、测试用的半径覆盖都不存盘：区块卸载就当这次脉冲做完了（同 {@code PulseGate} 的续做）。
 */
public abstract class FarmDeviceBlockEntity extends MRUBlockEntity implements RedstonePulseReceiver {

    /** 扫描半径：以机器为中心 25×25×25（原作 {@code new AxisAlignedBB(p).grow(12, 12, 12)}，写死的常量，不做配置） */
    public static final int RANGE = 12;
    /** 候选表最多是每次上限的几倍（见类注释第 2 条） */
    public static final int CANDIDATE_FACTOR = 4;

    private final PulseGate gate;
    /** 本次脉冲已经做成了几次（新脉冲开始时清零；做完后保留到下一次脉冲，给 GameTest 断言用） */
    private int done;
    /** 只给 GameTest 用：扫描半径覆盖，负数 = 用 {@link #RANGE}（测试区之间只隔 5 格，25³ 会碰到隔壁测试的实体） */
    private int rangeOverride = -1;

    /**
     * @param interval 两次脉冲的最小间隔（刻），一般读服务端配置，每次现取
     */
    protected FarmDeviceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, IntSupplier interval) {
        super(type, pos, state, () -> 0, 0);
        this.gate = new PulseGate(interval);
    }

    // ------------------------------------------------------------------ 子类实现

    /** 每次脉冲最多做成几次（服务端配置） */
    protected abstract int maxPerPulse();

    /** 查一次实体，把过了不用探测的判断的候选按距离排好、存进自己的候选表（不存盘） */
    protected abstract void collect(ServerLevel level);

    /**
     * 处理候选表：做成一次调 {@link #markDone()}，做满（{@link #full()}）就停；放行或拒绝的候选从表里拿掉，UNKNOWN 的留着。
     *
     * @return 表里还有 UNKNOWN（下一刻再问）的候选
     */
    protected abstract boolean process(ServerLevel level);

    /** 清空候选表 */
    protected abstract void clearCandidates();

    // ------------------------------------------------------------------ 脉冲

    @Override
    public PulseGate pulseGate() {
        return gate;
    }

    @Override
    public final boolean onRedstonePulse(ServerLevel level, BlockState state) {
        clearCandidates();
        done = 0;
        if (!mayWork()) {
            return false;
        }
        collect(level);
        return work(level);
    }

    @Override
    public final boolean continuePulse(ServerLevel level, BlockState state) {
        if (!mayWork()) {
            clearCandidates();
            return false;
        }
        return work(level);
    }

    private boolean work(ServerLevel level) {
        boolean unresolved = process(level);
        if (!unresolved || full()) {
            clearCandidates();
            return false;
        }
        return true;
    }

    /**
     * 主人规则（policy §2.3、§2.3a）：主人在线（单人里没有主人的算房主的）才干；主人不在线时只有服主关了 {@code require_owner_online} 才干；
     * 没有主人（/setblock、别的模组放的）的在多人服不干。
     */
    protected boolean mayWork() {
        return switch (ECProtection.ownerState(this)) {
            case ONLINE -> true;
            case OFFLINE -> !ECProtection.Config.requireOwnerOnline();
            case NONE -> false;
        };
    }

    /** 本次脉冲已经做成的次数（脉冲做完以后保留到下一次脉冲开始） */
    public int doneThisPulse() {
        return done;
    }

    protected void markDone() {
        done++;
    }

    protected boolean full() {
        return done >= maxPerPulse();
    }

    /** 候选表的长度上限 */
    protected int candidateLimit() {
        return Math.max(1, maxPerPulse()) * CANDIDATE_FACTOR;
    }

    // ------------------------------------------------------------------ 工具

    /** 以主人的身份问这一格能不能动（预算按这台机器算，结果缓存；见 {@code ECProtection.checkAsOwner}） */
    protected ECProtection.Verdict check(BlockPos cell) {
        return ECProtection.checkAsOwner(this, cell);
    }

    /**
     * 实际替谁干活（归属判断用）：记下的主人；单人里没有记下主人时是房主（{@code ECProtection.actingOwner}）。
     * 驯服的动物「主人是不是机器主人」、掉落物「拾取人是不是机器主人」都按它比。
     */
    @Nullable
    protected UUID machineOwner() {
        return ECProtection.actingOwner(this);
    }

    /** 扫描半径（格） */
    public int range() {
        return rangeOverride >= 0 ? rangeOverride : RANGE;
    }

    /**
     * <b>只给 GameTest 用</b>：把这台机器的扫描半径改小（不存盘，挖掉重放就没了）。GameTest 的测试区之间只隔 5 格，
     * 25³ 的范围会碰到隔壁测试的动物和掉落物；按实例覆盖、不动全局状态，别的测试不受影响。负数 = 恢复 {@link #RANGE}。
     */
    public void setRangeForTest(int radius) {
        rangeOverride = radius;
    }

    /** 以机器为中心、半径 {@link #range()} + extra 的范围（原作 AxisAlignedBB(p).grow(12)） */
    protected AABB scanArea(double extra) {
        return new AABB(worldPosition).inflate(range() + extra);
    }

    /** 按离机器中心的距离从近到远排序，截到 {@link #candidateLimit()} 个 */
    protected <T extends Entity> List<T> nearestFirst(List<T> entities) {
        Vec3 center = Vec3.atCenterOf(worldPosition);
        entities.sort(Comparator.comparingDouble(e -> e.distanceToSqr(center)));
        int limit = candidateLimit();
        if (entities.size() > limit) {
            entities.subList(limit, entities.size()).clear();
        }
        return entities;
    }

    // ------------------------------------------------------------------ 其它

    @Override
    protected boolean hasBoundGemSlot() {
        return false;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        gate.save(tag, "pulse");
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        gate.load(tag, "pulse");
    }
}
