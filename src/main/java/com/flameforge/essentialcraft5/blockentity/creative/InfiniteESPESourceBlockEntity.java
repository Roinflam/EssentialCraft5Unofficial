package com.flameforge.essentialcraft5.blockentity.creative;

import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IESPEHandler;
import com.flameforge.essentialcraft5.blockentity.CreativeESPEStorageBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MRUBlockEntity;
import com.flameforge.essentialcraft5.espe.ESPETransfer;
import com.flameforge.essentialcraft5.registry.ECCreative;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 无尽 ESPE 源（只放创造栏，registry/ECCreative）：ESPE 永远是满的，怎么抽都不减。
 * <ul>
 *   <li><b>被抽</b>：每一面都暴露同一个无底的 ESPE（{@link InfiniteESPEHandler}）：取多少给多少、什么都不扣，往里加一律退回；
 *       档位同创造 ESPE 源（{@link CreativeESPEStorageBlockEntity#ESPE_TIER}），比任何机器都高。放在米斯莱熔炉抽 ESPE 的位置上照样能用。</li>
 *   <li><b>主动供给</b>：每 {@value #INTERVAL} 刻一轮，给周围（三个轴各不超过 {@value #RADIUS} 格）带 ESPE 的方块实体补满：
 *       米斯莱熔炉（只进不出）、米斯莱水晶柱（玩家五芒星、风之符文、熔炉都从柱子里抽）等。
 *       走现有的 {@link ESPETransfer#pull}：目标从朝着本方块的那一面、按它自己的按面规则收（只出不进的纯源头收不进，不算）。
 *       一轮最多补 {@value #MAX_FILLED} 个、最多看 {@value #MAX_EXAMINED} 个范围内的方块实体、只看已完全加载的区块（{@link CreativeScan}）。</li>
 * </ul>
 * 没有 MRU、没有物品栏、没有界面、不动世界方块、不产生腐化。空手右键报告上一轮补了几个。
 */
public class InfiniteESPESourceBlockEntity extends MRUBlockEntity {

    public static final int RADIUS = 8;
    public static final int INTERVAL = 20;
    public static final int MAX_FILLED = 32;
    public static final int MAX_EXAMINED = 512;
    /** 对外报的存量和上限：比任何机器的上限都大，又是有限数（别人算「上限 − 存量」不会得到 NaN） */
    public static final double INFINITE_ESPE = 1_000_000_000D;

    private final InfiniteESPEHandler infinite = new InfiniteESPEHandler();
    private LazyOptional<IESPEHandler> infiniteCap = LazyOptional.of(() -> infinite);
    private final CreativeScan.Cursor cursor = new CreativeScan.Cursor();
    private int cooldown = INTERVAL;
    private int lastFilled;

    public InfiniteESPESourceBlockEntity(BlockPos pos, BlockState state) {
        super(ECCreative.INFINITE_ESPE_SOURCE_BE.get(), pos, state, () -> 0, 0);
    }

    @Override
    protected boolean hasBoundGemSlot() {
        return false;
    }

    @Override
    protected void serverTick() {
        if (--cooldown <= 0) {
            cooldown = INTERVAL;
            lastFilled = refillAround();
        }
    }

    @Override
    protected void clientTick() {
        if (level != null && innerRotation % 16 == 8) {
            level.addParticle(ParticleTypes.PORTAL,
                    worldPosition.getX() + 0.5D, worldPosition.getY() + 1.1D, worldPosition.getZ() + 0.5D,
                    (level.random.nextDouble() - 0.5D) * 0.6D, 0.1D, (level.random.nextDouble() - 0.5D) * 0.6D);
        }
    }

    /**
     * 立刻补一轮（正常由 serverTick 每 {@value #INTERVAL} 刻调一次；GameTest 直接调）。
     *
     * @return 这一轮真补进了 ESPE 的方块实体个数
     */
    public int refillAround() {
        if (level == null || level.isClientSide) {
            return 0;
        }
        var targets = CreativeScan.collect(level, worldPosition, RADIUS, MAX_EXAMINED, this::targetOf);
        return cursor.serve(targets, MAX_FILLED, target ->
                ESPETransfer.pull(level, target.pos(), target.handler(), worldPosition) > 0D);
    }

    /** 目标朝着本方块那一面的 ESPE；别的无尽 ESPE 源、没有 ESPE 的返回 null */
    @Nullable
    private Target targetOf(BlockEntity blockEntity) {
        if (blockEntity instanceof InfiniteESPESourceBlockEntity) {
            return null;
        }
        BlockPos pos = blockEntity.getBlockPos();
        IESPEHandler handler = blockEntity.getCapability(ECCapabilities.ESPE, ESPETransfer.sideFacing(pos, worldPosition)).orElse(null);
        return handler == null ? null : new Target(pos, handler);
    }

    private record Target(BlockPos pos, IESPEHandler handler) {
    }

    public int getLastFilled() {
        return lastFilled;
    }

    public IESPEHandler infiniteHandler() {
        return infinite;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ECCapabilities.ESPE && !isRemoved()) {
            return infiniteCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        infiniteCap.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        infiniteCap = LazyOptional.of(() -> infinite);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return null;
    }

    /** 无底的 ESPE：存量 = 上限 = {@link #INFINITE_ESPE}，取多少给多少（不扣），加进来的全部退回；NaN、负数、无穷一律当 0 */
    public static final class InfiniteESPEHandler implements IESPEHandler {

        @Override
        public double getMaxESPE() {
            return INFINITE_ESPE;
        }

        @Override
        public double getESPE() {
            return INFINITE_ESPE;
        }

        @Override
        public void setESPE(double amount) {
            // 永远是满的
        }

        @Override
        public double addESPE(double amount, boolean doAdd) {
            return amount > 0D && Double.isFinite(amount) ? amount : 0D;
        }

        @Override
        public double extractESPE(double amount, boolean doExtract) {
            return amount > 0D && Double.isFinite(amount) ? Math.min(amount, INFINITE_ESPE) : 0D;
        }

        @Override
        public int getTier() {
            return CreativeESPEStorageBlockEntity.ESPE_TIER;
        }
    }
}
