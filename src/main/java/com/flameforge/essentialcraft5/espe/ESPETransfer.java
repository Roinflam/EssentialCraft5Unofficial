package com.flameforge.essentialcraft5.espe;

import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IESPEHandler;
import com.flameforge.essentialcraft5.util.ECLevels;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * 从周围的 ESPE 源往自己抽 ESPE。对应原作 TileMithrilineFurnace.update 里遍历 possiblePowerSources 的那段：
 * 按给定的相对位置逐个找带 {@link ECCapabilities#ESPE} 的方块实体，每个抽到自己满为止，满了就停。
 *
 * <h2>和原作的区别</h2>
 * <ul>
 *   <li>只在服务端抽（客户端调用直接返回 0）。原作两端都抽，客户端那份再被整包同步盖掉。</li>
 *   <li>方块实体走 {@link ECLevels#loadedBlockEntity}，源头所在区块没完全加载就跳过，绝不触发区块加载。</li>
 *   <li>查 capability 时带面：源头朝向抽取方的那一面（{@link #sideFacing}），源头的按面规则（{@link ESPEAccess}）因此生效。
 *       原作一律传 null，所以谁都能从米斯莱熔炉里抽 ESPE。</li>
 * </ul>
 *
 * <h2>用法（米斯莱熔炉）</h2>
 * <pre>{@code
 * private static final Vec3i[] SOURCES = {
 *         new Vec3i(0, 1, 0),
 *         new Vec3i(1, 0, 1), new Vec3i(-1, 0, 1), new Vec3i(1, 0, -1), new Vec3i(-1, 0, -1),
 *         new Vec3i(2, 1, 0), new Vec3i(-2, 1, 0), new Vec3i(0, 1, 2), new Vec3i(0, 1, -2),
 *         new Vec3i(2, 2, 2), new Vec3i(-2, 2, 2), new Vec3i(2, 2, -2), new Vec3i(-2, 2, -2)};
 *
 * protected void serverTick() {
 *     if (structureOk) {
 *         ESPETransfer.pullFromAround(level, worldPosition, getESPEStorage(), SOURCES);
 *     }
 * }
 * }</pre>
 * 源头被抽走 ESPE 后要自己标记存盘：继承 MRUBlockEntity 的机器已经处理好了（和 MRU 一样立即标记区块）；
 * 自己写的方块实体（比如水晶柱）要在 {@code ESPEStorage.onChanged} 里调 {@code level.blockEntityChanged(worldPosition)}，
 * 否则源头区块已加载但不 tick 时，这次扣减不会存盘，重进世界 ESPE 又回来了。
 */
public final class ESPETransfer {

    private ESPETransfer() {
    }

    /**
     * 依次从 {@code self + offset} 处的 ESPE 源往 {@code into} 抽，抽满就停。
     *
     * @param into 抽到哪里（一般是机器自己的 {@link ESPEStorage}，不受按面规则限制）
     * @return 这次总共抽到的量
     */
    public static double pullFromAround(Level level, BlockPos self, IESPEHandler into, Vec3i... offsets) {
        return pullFromAround(level, self, into, Arrays.asList(offsets));
    }

    /** 同上，偏移量用集合给（比如按朝向旋转过的列表） */
    public static double pullFromAround(Level level, BlockPos self, IESPEHandler into, Iterable<? extends Vec3i> offsets) {
        if (level.isClientSide) {
            return 0D;
        }
        double total = 0D;
        for (Vec3i offset : offsets) {
            if (!hasSpace(into)) {
                break;
            }
            total += pull(level, self, into, self.offset(offset));
        }
        return total;
    }

    /**
     * 从 {@code source} 处的一个 ESPE 源往 {@code into} 抽，最多抽满自己。
     * 源头区块没加载、没有方块实体、这一面不给 ESPE、就是自己，都返回 0。
     *
     * @return 实际抽到的量
     */
    public static double pull(Level level, BlockPos self, IESPEHandler into, BlockPos source) {
        if (level.isClientSide || source.equals(self)) {
            return 0D;
        }
        double space = into.getMaxESPE() - into.getESPE();
        if (!(space > 0)) {
            return 0D;
        }
        IESPEHandler handler = loadedHandler(level, source, sideFacing(source, self));
        if (handler == null || handler == into) {
            return 0D;
        }
        double extracted = handler.extractESPE(space, true);
        if (!(extracted > 0)) {
            return 0D;
        }
        double leftover = into.addESPE(extracted, true);
        if (leftover > 0) {
            // 按 space 取的，正常不会放不下；into 不是 ESPEStorage（别的实现）时防一手，多的还回去，不凭空消失
            handler.addESPE(leftover, true);
            return extracted - leftover;
        }
        return extracted;
    }

    /**
     * 取 pos 处方块实体从 side 面暴露的 ESPE（区块没完全加载、没有方块实体或这一面不给时返回 null），绝不触发区块加载。
     * side 为 null 表示不指定面（原作的调用方式），机器的按面规则同样会处理 null。
     */
    @Nullable
    public static IESPEHandler loadedHandler(Level level, BlockPos pos, @Nullable Direction side) {
        BlockEntity blockEntity = ECLevels.loadedBlockEntity(level, pos);
        return blockEntity == null ? null : blockEntity.getCapability(ECCapabilities.ESPE, side).orElse(null);
    }

    /**
     * 源头朝向抽取方的那一面：正上方的源头给出的是它的 DOWN 面。斜着的取分量最大的轴
     * （原作的 13 个位置里同层四个斜角 (±1, 0, ±1) 两轴一样大，结果是固定的某一面，只要源头各面规则一致就无所谓）。
     */
    public static Direction sideFacing(BlockPos source, BlockPos target) {
        return Direction.getNearest(target.getX() - source.getX(), target.getY() - source.getY(), target.getZ() - source.getZ());
    }

    private static boolean hasSpace(IESPEHandler handler) {
        return handler.getMaxESPE() - handler.getESPE() > 0;
    }
}
