package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.block.MagicalChestBlock;
import com.flameforge.essentialcraft5.menu.MagicalChestMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestLidController;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 魔力箱 / 虚空箱的方块实体（原作 TileMagicalChest）：格数取自方块的 {@code MagicalChestBlock.Layout}（54 / 117）+ 开合动画。
 * <p>
 * 和原作不同的地方（都是性能 / 信息泄露，玩家感觉不到）：
 * <ul>
 *   <li>物品栏<b>只走菜单</b>同步。原作内容一变就每 60 tick 把整箱 NBT 发给 32 格内所有人（L161~170，还靠整份写两遍 NBT 比较来判断变没变）；
 *       这里的更新包只有一个「开着没有」（区块发给新进来的玩家时带上，让他看到正开着的盖子），{@link #getUpdatePacket} 不覆盖（原版默认不发）。</li>
 *   <li>开合用原版 {@link ContainerOpenersCounter}：只在人数<b>变了</b>时发一次方块事件。原作 L180 {@code ++ticksSinceSync % 20 * 4 == 0}
 *       运算优先级写错（本意 % 80），结果每 20 tick 不管开没开都给附近所有人发一次；原版的计数器有人开着时每 5 刻复核一次、人数不变也会重发，这里也去掉了。</li>
 *   <li>原作客户端加载方块实体时发 ID 7 包要服务端回整份 NBT，服务端不校验坐标和维度，改客户端能远程看任何已加载的魔力箱——不移植。</li>
 *   <li>服务端不 tick；盖子动画（每 tick 0.1、10 tick 开满，线性，照原作 L185~207）只在客户端算。</li>
 * </ul>
 * 物品栏的所有面都能进能出（原作 L279 所有面共用一个 wrapper）。挖掉时照原作撒出内容：直接交出槽里那一堆（掉落时被拆空），
 * 不掉副本——还开着界面的人从旧方块实体里拿不到东西（{@code MRUBlockEntity.dropContents} 同样的写法）。
 * <p>
 * 实现 {@link Clearable}（和原版容器一样，原作的 IInventory.clear 也是这个用处）：/setblock、/fill、/clone、结构方块放置在替换方块之前
 * 先调 {@link #clearContent} 清空，所以被这些命令覆盖时不撒东西；/clone … move 先存下内容再清空原位，内容只搬过去一份，不会原地再撒一份。
 */
public class MagicalChestBlockEntity extends BlockEntity implements MenuProvider, LidBlockEntity, Clearable {

    /** 方块事件：开合人数（和原版箱子同号） */
    public static final int EVENT_OPEN_COUNT = 1;
    private static final String INVENTORY_KEY = "inventory";
    private static final String OPEN_KEY = "open";

    private final ItemStackHandler inventory;
    private LazyOptional<IItemHandler> inventoryCap;
    private final ChestLidController lid = new ChestLidController();

    private final ContainerOpenersCounter openers = new ContainerOpenersCounter() {
        @Override
        protected void onOpen(Level level, BlockPos pos, BlockState state) {
            playSound(level, pos, SoundEvents.CHEST_OPEN);
        }

        @Override
        protected void onClose(Level level, BlockPos pos, BlockState state) {
            playSound(level, pos, SoundEvents.CHEST_CLOSE);
        }

        @Override
        protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int oldCount, int newCount) {
            // 原版每次复核都发（人数没变也发），这里只在变了时发
            if (oldCount != newCount) {
                level.blockEvent(pos, state.getBlock(), EVENT_OPEN_COUNT, newCount);
            }
        }

        @Override
        protected boolean isOwnContainer(Player player) {
            return player.containerMenu instanceof MagicalChestMenu menu && menu.chest() == MagicalChestBlockEntity.this;
        }
    };

    public MagicalChestBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        int slots = state.getBlock() instanceof MagicalChestBlock chest ? chest.layout().slots() : MagicalChestBlock.Layout.MAGICAL.slots();
        this.inventory = new ItemStackHandler(slots) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }
        };
        this.inventoryCap = LazyOptional.of(() -> inventory);
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public MagicalChestBlock.Layout layout() {
        return getBlockState().getBlock() instanceof MagicalChestBlock chest ? chest.layout() : MagicalChestBlock.Layout.MAGICAL;
    }

    // ------------------------------------------------------------------
    // 开合
    // ------------------------------------------------------------------

    /** 菜单在服务端建好时调（旁观者不算） */
    public void startOpen(Player player) {
        if (!remove && level != null && !player.isSpectator()) {
            openers.incrementOpeners(player, level, worldPosition, getBlockState());
        }
    }

    /** 菜单关掉时调 */
    public void stopOpen(Player player) {
        if (!remove && level != null && !player.isSpectator()) {
            openers.decrementOpeners(player, level, worldPosition, getBlockState());
        }
    }

    /** 方块的计划刻里调：数一遍附近开着这个箱子的人 */
    public void recheckOpen() {
        if (!remove && level != null) {
            openers.recheckOpeners(level, worldPosition, getBlockState());
        }
    }

    public int getOpenerCount() {
        return openers.getOpenerCount();
    }

    private static void playSound(Level level, BlockPos pos, SoundEvent sound) {
        // 原作 L188 / L206：音量 0.5、音高 0.9~1.0，和原版箱子一样
        level.playSound(null, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, sound, SoundSource.BLOCKS,
                0.5F, level.random.nextFloat() * 0.1F + 0.9F);
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id == EVENT_OPEN_COUNT) {
            lid.shouldBeOpen(param > 0);
            return true;
        }
        return super.triggerEvent(id, param);
    }

    /** 客户端 ticker */
    public static void lidAnimateTick(Level level, BlockPos pos, BlockState state, MagicalChestBlockEntity chest) {
        chest.lid.tickLid();
    }

    @Override
    public float getOpenNess(float partialTick) {
        return lid.getOpenness(partialTick);
    }

    // ------------------------------------------------------------------
    // 菜单
    // ------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        if (!(getBlockState().getBlock() instanceof MagicalChestBlock block)) {
            return null;
        }
        return new MagicalChestMenu(block.menuType(), containerId, playerInventory, block.layout(), inventory, this);
    }

    // ------------------------------------------------------------------
    // 掉落、存读档、同步
    // ------------------------------------------------------------------

    /** 方块被破坏时撒出内容（原作 breakBlock → InventoryHelper.dropInventoryItems）。交出槽里那一堆本身，不掉副本 */
    public void dropContents() {
        if (level == null) {
            return;
        }
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
            }
        }
    }

    /**
     * /setblock、/fill、/clone、结构放置替换方块之前调（{@link Clearable#tryClear}）：清空物品栏，
     * 接下来的 onRemove → {@link #dropContents} 就没东西可撒。只标记存盘、不发包（方块马上要被换掉；物品栏本来就只走菜单同步）。
     */
    @Override
    public void clearContent() {
        for (int i = 0; i < inventory.getSlots(); i++) {
            if (!inventory.getStackInSlot(i).isEmpty()) {
                inventory.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(INVENTORY_KEY, inventory.serializeNBT());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains(INVENTORY_KEY, Tag.TAG_COMPOUND)) {
            // 以代码的格数为准：ItemStackHandler.deserializeNBT 会按存档里的 Size 重建列表，存档被改过时菜单的格子会对不上
            CompoundTag saved = tag.getCompound(INVENTORY_KEY).copy();
            saved.putInt("Size", inventory.getSlots());
            inventory.deserializeNBT(saved);
        }
    }

    /** 区块发给客户端时只带「开着没有」，不带物品栏 */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        if (openers.getOpenerCount() > 0) {
            tag.putBoolean(OPEN_KEY, true);
        }
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        lid.shouldBeOpen(tag.getBoolean(OPEN_KEY));
    }

    /** 盖子打开时会伸出方块上沿（约 1.45 格高），渲染范围照原版箱子放宽 */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition.offset(-1, 0, -1), worldPosition.offset(2, 2, 2));
    }

    // ------------------------------------------------------------------
    // capability
    // ------------------------------------------------------------------

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER && !remove) {
            return inventoryCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        inventoryCap.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        inventoryCap = LazyOptional.of(() -> inventory);
    }
}
