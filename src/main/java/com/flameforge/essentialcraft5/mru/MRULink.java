package com.flameforge.essentialcraft5.mru;

import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IMRUHandler;
import com.flameforge.essentialcraft5.item.BoundGemItem;
import com.flameforge.essentialcraft5.util.ECLevels;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * 绑定宝石的 MRU 拉取：消费端机器 0 号槽放一颗记着源坐标的绑定宝石，每 tick 从源头拉满自己。
 * 对应原作 MRUTileStorage / MRUTileRangelessStorage / MRUTileCrossDimStorage 的 mruIn。
 * <p>
 * 与原作的区别：
 * <ul>
 *   <li>只在服务端跑。原作两端都拉，客户端的结果再被每秒一次的整包同步盖掉。</li>
 *   <li>目标区块没完全加载就不拉（{@link Status#UNLOADED}）。1.20.1 的 getBlockEntity 会在主线程上同步加载区块，
 *       而 isLoaded 只看票据等级、区块加载中也返回 true，所以走 {@link ECLevels#loadedBlockEntity}。</li>
 *   <li>核对维度：原作普通版和无限距离版只比坐标不比维度，别的维度绑的宝石会连到本维度同坐标。</li>
 * </ul>
 * 界面上的状态（原作 GuiBoundGemState 在客户端现算，远处/跨维度的方块客户端根本看不到）改成这里算好、随菜单同步。
 */
public final class MRULink {

    /** 原作 MRUTileStorage 的默认距离：三个轴各不超过 16 格 */
    public static final int DEFAULT_RANGE = 16;
    /** 无限距离（原作 MRUTileRangelessStorage） */
    public static final int UNLIMITED = Integer.MAX_VALUE;

    public enum Status {
        NO_GEM(0xFF0000),
        NOT_BOUND(0xFF0000),
        WRONG_DIMENSION(0xFF0000),
        BOUND_TO_SELF(0xFF0000),
        OUT_OF_RANGE(0xFF0000),
        UNLOADED(0xFFFF00),
        NO_TILE(0xFF0000),
        NOT_MAGICAL(0xFF0000),
        NO_MRU(0xFFFF00),
        WORKING(0x00FF00);

        private static final Status[] VALUES = values();

        /** 原作 GuiBoundGemState 的字色：红色是出错，黄色是暂时没东西可拉，绿色正常 */
        public final int color;

        Status(int color) {
            this.color = color;
        }

        public Component label() {
            return Component.translatable("gui.essentialcraft5.link." + name().toLowerCase(Locale.ROOT));
        }

        public static Status byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : NO_GEM;
        }
    }

    private MRULink() {
    }

    /**
     * 从宝石指向的方块实体往 {@code into} 拉 MRU。只能在服务端调。
     *
     * @param range          三个轴各自允许的最大距离，{@link #UNLIMITED} 为不限（只对同一维度有意义）
     * @param crossDimension 是否允许连到别的维度（原作只有维度收发器）
     */
    public static Status pull(Level level, BlockPos self, MRUStorage into, ItemStack gem, int range, boolean crossDimension) {
        if (!(gem.getItem() instanceof BoundGemItem)) {
            return Status.NO_GEM;
        }
        return pull(level, self, into, BoundGemItem.getTarget(gem), range, crossDimension);
    }

    /** 同上，目标已经从宝石里解析好（方块实体会缓存解析结果，省得每 tick 重新解析 NBT） */
    public static Status pull(Level level, BlockPos self, MRUStorage into, @Nullable GlobalPos target, int range, boolean crossDimension) {
        if (target == null) {
            return Status.NOT_BOUND;
        }

        Level targetLevel;
        boolean sameLevel = target.dimension() == level.dimension();
        if (sameLevel) {
            targetLevel = level;
        } else if (crossDimension) {
            MinecraftServer server = level.getServer();
            targetLevel = server == null ? null : server.getLevel(target.dimension());
            if (targetLevel == null) {
                return Status.NO_TILE;
            }
        } else {
            return Status.WRONG_DIMENSION;
        }

        BlockPos targetPos = target.pos();
        if (sameLevel) {
            if (targetPos.equals(self)) {
                return Status.BOUND_TO_SELF;
            }
            if (!inRange(self, targetPos, range)) {
                return Status.OUT_OF_RANGE;
            }
        }
        // 只取已经完全加载好的区块，绝不在主线程上等区块加载
        if (!ECLevels.isFullyLoaded(targetLevel, targetPos)) {
            return Status.UNLOADED;
        }
        BlockEntity source = ECLevels.loadedBlockEntity(targetLevel, targetPos);
        if (source == null) {
            return Status.NO_TILE;
        }
        IMRUHandler handler = source.getCapability(ECCapabilities.MRU).orElse(null);
        if (handler == null) {
            return Status.NOT_MAGICAL;
        }
        boolean sourceHasMRU = handler.getMRU() > 0;
        into.pullFrom(handler);
        return sourceHasMRU ? Status.WORKING : Status.NO_MRU;
    }

    /** 客户端画连线粒子用：同一维度且在范围内时返回源坐标，否则 null。 */
    @Nullable
    public static BlockPos visibleSource(Level level, BlockPos self, ItemStack gem, int range) {
        GlobalPos target = BoundGemItem.getTarget(gem);
        if (target == null || target.dimension() != level.dimension() || target.pos().equals(self)) {
            return null;
        }
        return inRange(self, target.pos(), range) ? target.pos() : null;
    }

    private static boolean inRange(BlockPos a, BlockPos b, int range) {
        if (range == UNLIMITED) {
            return true;
        }
        return Math.abs(a.getX() - b.getX()) <= range
                && Math.abs(a.getY() - b.getY()) <= range
                && Math.abs(a.getZ() - b.getZ()) <= range;
    }
}
