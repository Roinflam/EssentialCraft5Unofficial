package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.block.RedstoneTransmitterBlock;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.BoundGemItem;
import com.flameforge.essentialcraft5.menu.RedstoneTransmitterMenu;
import com.flameforge.essentialcraft5.registry.ECRedstoneTools;
import com.flameforge.essentialcraft5.util.ECLevels;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

/**
 * 红石传递器的方块实体（原作 TileRedstoneTransmitter）：1 格，只放绑定宝石。宝石在这里只指路，不拉 MRU（{@link #hasBoundGemSlot} 为假、MRU 上限 0）。
 * <p>
 * 每 {@code update_interval_ticks}（默认 2）刻按游戏时间算一次（{@link #refresh}）：
 * <ol>
 *   <li>没放宝石 / 宝石没绑定 → 0；</li>
 *   <li>宝石绑的是别的维度 → 0（原作研究书说不能跨维度，代码却不核对，读的是本维度同坐标）；绑的是它自己 → 0；</li>
 *   <li>目标周围 ±2 格所在的区块有一个没加载完 → 0，<b>绝不触发加载</b>（原作 1.12 每 tick 读远处，没加载的区块会被同步加载，等于强制加载）。
 *       要 ±2 格：读目标收到的信号会问它的六个邻居，邻居是实心方块时还会再问邻居的邻居（{@code SignalGetter.getSignal → getDirectSignalTo}）；
 *       ±2 的正方形最多跨 2×2 个区块，查四个角就够；</li>
 *   <li>目标不是红石传递器 → 0；目标的六个邻居里有红石传递器 → 0（原作防串联，L23~27）；</li>
 *   <li>否则输出目标位置收到的最强信号（{@code getBestNeighborSignal}，= 原作 getRedstonePowerFromNeighbors），变了才改方块状态（原版会通知六个邻居）。</li>
 * </ol>
 * 区块不 tick 时方块实体不 tick，输出保持上次的值。宝石是谁绑的由绑定宝石自己管（绑定时对目标格 {@code ECProtection.canEdit}，第三批安全回补），
 * 这里只读红石信号、不改别人的东西（policy §4.3 规划补②「链接类」），不需要主人。
 * 物品栏不发给客户端（{@link #syncInventoryToClient}）：渲染用不到，而宝石里记着远处的坐标。
 */
public class RedstoneTransmitterBlockEntity extends MRUBlockEntity {

    /** 服务端配置 machines.redstone_transmitter */
    public static final class Config {
        public static ForgeConfigSpec.IntValue updateInterval;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("redstone_transmitter");
            updateInterval = builder.comment("How often (in ticks) a redstone transmitter re-reads the signal at the transmitter its bound gem points to.",
                            "红石传递器每隔多少刻重读一次绑定宝石指向的那个传递器收到的信号")
                    .defineInRange("update_interval_ticks", 2, 1, 20);
            builder.pop();
        }
    }

    public static final int SLOT_GEM = 0;
    public static final int SLOTS = 1;

    /** 连接状态（界面显示；序号随菜单同步，只能在末尾追加） */
    public enum Link {
        NO_GEM,
        UNBOUND,
        OTHER_DIMENSION,
        SELF,
        NOT_LOADED,
        NOT_TRANSMITTER,
        CHAINED,
        LINKED;

        private static final Link[] VALUES = values();

        public static Link byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : NO_GEM;
        }
    }

    /** 上次算的游戏时间（不存盘：读档后第一刻就算一次）；-1 = 还没算过 */
    private long lastUpdate = -1L;
    private Link link = Link.NO_GEM;

    // 宝石目标的解析缓存：宝石或它的 NBT 换了才重新解析（同 MRUBlockEntity）
    private ItemStack cachedGem = ItemStack.EMPTY;
    @Nullable
    private CompoundTag cachedGemTag;
    @Nullable
    private GlobalPos cachedTarget;

    public RedstoneTransmitterBlockEntity(BlockPos pos, BlockState state) {
        super(ECRedstoneTools.REDSTONE_TRANSMITTER_BE.get(), pos, state, () -> 0, SLOTS);
    }

    /** 槽位规则（两端共用）：只放绑定宝石 */
    public static boolean slotRule(int slot, ItemStack stack) {
        return slot == SLOT_GEM && stack.getItem() instanceof BoundGemItem;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slotRule(slot, stack);
    }

    /** 宝石只指路，不拉 MRU */
    @Override
    protected boolean hasBoundGemSlot() {
        return false;
    }

    @Override
    protected boolean syncInventoryToClient() {
        return false;
    }

    @Override
    protected boolean syncBalanceToClient() {
        return false;
    }

    /** 当前连接状态（服务端） */
    public Link link() {
        return link;
    }

    /** 当前输出（方块状态里的值） */
    public int power() {
        BlockState state = getBlockState();
        return state.hasProperty(RedstoneTransmitterBlock.POWER) ? state.getValue(RedstoneTransmitterBlock.POWER) : 0;
    }

    @Override
    protected void serverTick() {
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        // 按游戏时间：方块实体 tick 被别的模组节流（隔几刻才调一次）时也不会一直错过
        if (lastUpdate >= 0L && now >= lastUpdate && now - lastUpdate < ECConfig.get(Config.updateInterval)) {
            return;
        }
        lastUpdate = now;
        refresh();
    }

    /** 立刻重算一次输出，变了就改方块状态（服务端；GameTest 也直接调） */
    public void refresh() {
        if (level == null || level.isClientSide) {
            return;
        }
        int value = computePower();
        BlockState state = getBlockState();
        if (state.hasProperty(RedstoneTransmitterBlock.POWER) && state.getValue(RedstoneTransmitterBlock.POWER) != value) {
            level.setBlock(worldPosition, state.setValue(RedstoneTransmitterBlock.POWER, value), Block.UPDATE_ALL);
        }
    }

    private int computePower() {
        ItemStack gem = inventory.getStackInSlot(SLOT_GEM);
        if (!(gem.getItem() instanceof BoundGemItem)) {
            link = Link.NO_GEM;
            return 0;
        }
        GlobalPos target = target(gem);
        if (target == null) {
            link = Link.UNBOUND;
            return 0;
        }
        if (!target.dimension().equals(level.dimension())) {
            link = Link.OTHER_DIMENSION;
            return 0;
        }
        BlockPos at = target.pos();
        if (at.equals(worldPosition)) {
            link = Link.SELF;
            return 0;
        }
        if (level.isOutsideBuildHeight(at)) {
            link = Link.NOT_TRANSMITTER;
            return 0;
        }
        if (!areaLoaded(level, at)) {
            link = Link.NOT_LOADED;
            return 0;
        }
        if (!(level.getBlockState(at).getBlock() instanceof RedstoneTransmitterBlock)) {
            link = Link.NOT_TRANSMITTER;
            return 0;
        }
        for (Direction direction : Direction.values()) {
            if (level.getBlockState(at.relative(direction)).getBlock() instanceof RedstoneTransmitterBlock) {
                link = Link.CHAINED;
                return 0;
            }
        }
        link = Link.LINKED;
        return Mth.clamp(level.getBestNeighborSignal(at), 0, 15);
    }

    @Nullable
    private GlobalPos target(ItemStack gem) {
        if (gem != cachedGem || gem.getTag() != cachedGemTag) {
            cachedGem = gem;
            cachedGemTag = gem.getTag();
            cachedTarget = BoundGemItem.getTarget(gem);
        }
        return cachedTarget;
    }

    /**
     * 读 {@code at} 收到的信号会碰到的格子（水平 ±2）所在的区块是否都已完全加载（不触发加载）。
     * ±2 的正方形横跨最多 2×2 个区块，四个角各查一次就覆盖了。
     */
    public static boolean areaLoaded(Level level, BlockPos at) {
        return ECLevels.isFullyLoaded(level, at.offset(-2, 0, -2))
                && ECLevels.isFullyLoaded(level, at.offset(2, 0, -2))
                && ECLevels.isFullyLoaded(level, at.offset(-2, 0, 2))
                && ECLevels.isFullyLoaded(level, at.offset(2, 0, 2));
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new RedstoneTransmitterMenu(id, inventory, this);
    }
}
