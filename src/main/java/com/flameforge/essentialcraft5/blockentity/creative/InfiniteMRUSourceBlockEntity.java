package com.flameforge.essentialcraft5.blockentity.creative;

import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IMRUHandler;
import com.flameforge.essentialcraft5.blockentity.MRUBlockEntity;
import com.flameforge.essentialcraft5.mru.MRUStorage;
import com.flameforge.essentialcraft5.registry.ECCreative;
import com.flameforge.essentialcraft5.registry.ECItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 无尽 MRU 源（只放创造栏，registry/ECCreative）：MRU 永远是满的，怎么拉都不减。
 * <ul>
 *   <li><b>被拉</b>：对外的 MRU（{@link ECCapabilities#MRU}）是一个无底的视图 {@link InfiniteMRUHandler}：
 *       存量和上限都是 {@link Integer#MAX_VALUE}，取多少给多少、什么都不扣，往里塞一律退回。
 *       所以绑定宝石（{@code mru/MRULink}）连到它时，机器每刻都能一口气拉满，不受原作创造 MRU 源 100 万的量限。</li>
 *   <li><b>主动补满</b>：每 {@value #INTERVAL} 刻一轮，给周围（三个轴各不超过 {@value #RADIUS} 格）靠绑定宝石吃 MRU 的机器补满，
 *       算法和绑定宝石拉取是同一个（{@link MRUStorage#pullFrom}，平衡值按量加权）。一轮最多补 {@value #MAX_FILLED} 台、
 *       最多看 {@value #MAX_EXAMINED} 个范围内的方块实体、只看已完全加载的区块（{@link CreativeScan}），多出来的下一轮接着轮。</li>
 *   <li>「靠绑定宝石吃 MRU 的机器」= 0 号槽只收绑定宝石的机器（基类的宝石槽、富集仓接收器）。产能器的 0 号槽放燃料、植物，
 *       不在此列：给产能器灌满 MRU 会让它停下不烧，测产量时反而碍事。补的是 {@code getMRUStorage()}：富集仓接收器连着仓时就是整座仓。</li>
 *   <li>平衡值存在自己的存储里（默认纯净 1.0），平衡值设定器照常能改，补给出去的 MRU 带这个平衡值。</li>
 * </ul>
 * 不漏 MRU、不产生腐化、没有界面、不动世界方块。空手右键报告上一轮补了几台。
 */
public class InfiniteMRUSourceBlockEntity extends MRUBlockEntity {

    /** 补给半径（三个轴各自），和绑定宝石默认距离 16 的一半 */
    public static final int RADIUS = 8;
    /** 两轮补给之间的刻数 */
    public static final int INTERVAL = 20;
    /** 一轮最多补几台 */
    public static final int MAX_FILLED = 32;
    /** 一轮最多看几个范围内的方块实体 */
    public static final int MAX_EXAMINED = 512;

    private final InfiniteMRUHandler infinite = new InfiniteMRUHandler(mru);
    private LazyOptional<IMRUHandler> infiniteCap = LazyOptional.of(() -> infinite);
    private final CreativeScan.Cursor cursor = new CreativeScan.Cursor();
    private int cooldown = INTERVAL;
    /** 上一轮补了几台（只在服务端有意义，右键报告用） */
    private int lastFilled;

    public InfiniteMRUSourceBlockEntity(BlockPos pos, BlockState state) {
        super(ECCreative.INFINITE_MRU_SOURCE_BE.get(), pos, state, () -> Integer.MAX_VALUE, 0);
    }

    @Override
    protected boolean hasBoundGemSlot() {
        return false;
    }

    @Override
    protected void serverTick() {
        // 自己的存储只用来存平衡值、给读 getMRUStorage() 的地方（界面、展示）看；值不变时 setMRU 什么都不做
        mru.setMRU(Integer.MAX_VALUE);
        if (--cooldown <= 0) {
            cooldown = INTERVAL;
            lastFilled = refillAround();
        }
    }

    @Override
    protected void clientTick() {
        if (level != null && innerRotation % 16 == 0) {
            level.addParticle(ParticleTypes.END_ROD,
                    worldPosition.getX() + 0.2D + level.random.nextDouble() * 0.6D, worldPosition.getY() + 1.05D,
                    worldPosition.getZ() + 0.2D + level.random.nextDouble() * 0.6D, 0D, 0.03D, 0D);
        }
    }

    /**
     * 立刻补一轮（正常由 serverTick 每 {@value #INTERVAL} 刻调一次；GameTest 直接调）。
     *
     * @return 这一轮真补进了 MRU 的机器台数
     */
    public int refillAround() {
        if (level == null || level.isClientSide) {
            return 0;
        }
        ItemStack gem = new ItemStack(ECItems.BOUND_GEM.get());
        ItemStack notGem = new ItemStack(Items.STICK);
        var targets = CreativeScan.collect(level, worldPosition, RADIUS, MAX_EXAMINED,
                blockEntity -> isLinkedConsumer(blockEntity, gem, notGem) ? (MRUBlockEntity) blockEntity : null);
        return cursor.serve(targets, MAX_FILLED, machine -> machine.getMRUStorage().pullFrom(infinite) > 0);
    }

    /** 0 号槽只收绑定宝石的 MRU 机器（无尽源自己除外） */
    private static boolean isLinkedConsumer(BlockEntity blockEntity, ItemStack gem, ItemStack notGem) {
        return blockEntity instanceof MRUBlockEntity machine
                && !(machine instanceof InfiniteMRUSourceBlockEntity)
                && !machine.isRemoved()
                && machine.getInventory().getSlots() > 0
                && machine.isItemValid(0, gem)
                && !machine.isItemValid(0, notGem);
    }

    public int getLastFilled() {
        return lastFilled;
    }

    /** 对外的无底视图（GameTest 和右键报告用；别的东西一律走 capability） */
    public IMRUHandler infiniteHandler() {
        return infinite;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ECCapabilities.MRU && !isRemoved()) {
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

    /**
     * 无底的 MRU：存量 = 上限 = {@link Integer#MAX_VALUE}，{@code extractMRU} 要多少给多少（不扣），{@code addMRU} 全部退回（已经满了）。
     * 平衡值读写转给方块实体自己的存储（存盘、平衡值设定器能改）。
     */
    public static final class InfiniteMRUHandler implements IMRUHandler {
        private final MRUStorage balanceHolder;

        InfiniteMRUHandler(MRUStorage balanceHolder) {
            this.balanceHolder = balanceHolder;
        }

        @Override
        public int getMaxMRU() {
            return Integer.MAX_VALUE;
        }

        @Override
        public int getMRU() {
            return Integer.MAX_VALUE;
        }

        @Override
        public void setMRU(int amount) {
            // 永远是满的
        }

        @Override
        public int addMRU(int amount, boolean doAdd) {
            // 满了，放不下的原样退回；amount ≤ 0 时同样原样返回（接口约定）
            return amount;
        }

        @Override
        public int extractMRU(int amount, boolean doExtract) {
            return Math.max(0, amount);
        }

        @Override
        public float getBalance() {
            return balanceHolder.getBalance();
        }

        @Override
        public void setBalance(float balance) {
            balanceHolder.setBalance(balance);
        }
    }
}
