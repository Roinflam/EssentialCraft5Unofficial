package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.block.MagicalDisplayBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 魔力显示屏的方块实体（原作 TileMagicalDisplay）：屏上的一件物品 + 显示方式（0 大图标、1 小图标 + 名字、2 更小的图标 + 名字 + 说明）。
 * <p>
 * 和原作不同的地方：
 * <ul>
 *   <li>自动化（漏斗、管道，所有面）<b>一格最多 1 个</b>（原作能塞进 64 个，玩家却只能放 1 个；一次弹出 64 个）。</li>
 *   <li>不 tick。原作每 tick 走同步计时、每 20 tick 整份写两遍 NBT 比较，变了就发给 32 格内所有人；这里只在变化时发更新包，
 *       同一块屏最快 {@value #SYNC_INTERVAL} tick 一次（玩家右键不受限，自动化换得太快时用计划刻补发最后一次，见 {@link #flushPendingSync}）。</li>
 *   <li>更新包只带物品和显示方式；物品 NBT 超过 {@value #MAX_SYNC_BYTES} 字节时只发 id、数量和不太长的自定义名字（防止写满的书这类东西撑大区块数据包），
 *       存档里仍是完整的物品。</li>
 *   <li>每次改动当场 setChanged（原作不 markDirty，1.20 照抄就是复制）。</li>
 * </ul>
 * 实现 {@link Clearable}（原作 TileMagicalDisplay 是 IInventory，有 clear）：/setblock、/fill、/clone、结构方块放置在替换方块之前先清空屏上的东西，
 * 被这些命令覆盖时不掉出来，/clone … move 也不会原地多掉一份。
 */
public class MagicalDisplayBlockEntity extends BlockEntity implements Clearable {

    /** 显示方式个数（原作 type 0~2） */
    public static final int MODES = 3;
    /** 同一块屏两次更新包之间至少隔多少 tick（policy §1.2 发包） */
    public static final int SYNC_INTERVAL = 10;
    /** 发给客户端的物品 NBT 上限；超过就精简 */
    public static final int MAX_SYNC_BYTES = 16 * 1024;
    /** 精简时自定义名字（JSON）最长保留多少字符 */
    private static final int MAX_SYNC_NAME_CHARS = 1024;
    private static final long NEVER = Long.MIN_VALUE / 4;

    private static final String ITEM_KEY = "item";
    private static final String MODE_KEY = "mode";

    private ItemStack displayed = ItemStack.EMPTY;
    private int mode;
    /** 限频用，不存盘 */
    private long lastSync = NEVER;
    private boolean syncPending;

    private final IItemHandlerModifiable handler = new DisplayHandler();
    private LazyOptional<IItemHandler> handlerCap = LazyOptional.of(() -> handler);

    public MagicalDisplayBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public ItemStack getDisplayed() {
        return displayed;
    }

    public int getMode() {
        return mode;
    }

    public Direction facing() {
        BlockState state = getBlockState();
        return state.hasProperty(MagicalDisplayBlock.FACING) ? state.getValue(MagicalDisplayBlock.FACING) : Direction.NORTH;
    }

    // ------------------------------------------------------------------
    // 玩家右键（MagicalDisplayBlock.use，只在服务端）：当场同步
    // ------------------------------------------------------------------

    /** 放上一件（调用方保证屏是空的、数量是 1） */
    public void setDisplayed(ItemStack stack) {
        displayed = stack.isEmpty() ? ItemStack.EMPTY : stack;
        changed(true);
    }

    /** 空手右键：切换显示方式 */
    public void cycleMode() {
        mode = (mode + 1) % MODES;
        changed(true);
    }

    /**
     * 把屏上的东西从正面弹出来（原作在方块中心生成、拾取延迟 30 tick；这里在屏前面生成，朝外轻推，延迟用原版默认的 10 tick）。
     * 不看 doTileDrops：这是取回屏上的东西，不是方块掉落。返回有没有东西弹出。
     */
    public boolean ejectDisplayed() {
        if (displayed.isEmpty() || !(level instanceof ServerLevel server)) {
            return false;
        }
        ItemStack out = displayed;
        displayed = ItemStack.EMPTY;
        changed(true);
        Direction facing = facing();
        double halfHeight = 0.125D;
        double x = worldPosition.getX() + 0.5D + facing.getStepX() * 0.25D;
        double y = worldPosition.getY() + 0.5D + facing.getStepY() * 0.25D - halfHeight;
        double z = worldPosition.getZ() + 0.5D + facing.getStepZ() * 0.25D;
        double up = facing.getAxis().isHorizontal() ? 0.1D : 0D;
        ItemEntity entity = new ItemEntity(server, x, y, z, out,
                facing.getStepX() * 0.1D, facing.getStepY() * 0.1D + up, facing.getStepZ() * 0.1D);
        entity.setDefaultPickUpDelay();
        server.addFreshEntity(entity);
        return true;
    }

    /** 方块被破坏时（onRemove）取走屏上的东西交给调用方掉落；不发更新包（方块已经没了） */
    public ItemStack takeDisplayed() {
        ItemStack out = displayed;
        displayed = ItemStack.EMPTY;
        setChanged();
        return out;
    }

    /**
     * /setblock、/fill、/clone、结构放置替换方块之前调（{@link Clearable#tryClear}）：清空屏上的东西，接下来的 onRemove 就没东西可掉。
     * 只标记存盘、不发更新包（方块马上要被换掉；结构放置可能在生成线程上，也不该碰发包）。
     */
    @Override
    public void clearContent() {
        displayed = ItemStack.EMPTY;
        setChanged();
    }

    // ------------------------------------------------------------------
    // 同步
    // ------------------------------------------------------------------

    /**
     * 标记存盘并通知客户端。immediate：玩家右键，当场发；否则（自动化）同一块屏 {@value #SYNC_INTERVAL} tick 内最多发一次，
     * 期间的改动由计划刻在间隔结束时补发一次（最后的样子一定会发到）。
     */
    private void changed(boolean immediate) {
        setChanged();
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        long now = server.getGameTime();
        long since = now - lastSync;
        if (immediate || since >= SYNC_INTERVAL) {
            sendUpdate(server, now);
        } else if (!syncPending) {
            syncPending = true;
            server.scheduleTick(worldPosition, getBlockState().getBlock(), (int) Math.max(1L, SYNC_INTERVAL - since));
        }
    }

    private void sendUpdate(ServerLevel server, long now) {
        lastSync = now;
        syncPending = false;
        BlockState state = getBlockState();
        server.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
    }

    /** 方块的计划刻里调：补发限频期间攒下的那次更新 */
    public void flushPendingSync() {
        if (syncPending && level instanceof ServerLevel server) {
            sendUpdate(server, server.getGameTime());
        }
    }

    /** 发给客户端的物品：NBT 太大时只留 id、数量和不太长的自定义名字 */
    public static CompoundTag syncedItemTag(ItemStack stack) {
        CompoundTag full = stack.save(new CompoundTag());
        if (!stack.hasTag() || full.sizeInBytes() <= MAX_SYNC_BYTES) {
            return full;
        }
        ItemStack slim = new ItemStack(stack.getItem(), stack.getCount());
        if (stack.hasCustomHoverName()) {
            Component name = stack.getHoverName();
            if (Component.Serializer.toJson(name).length() <= MAX_SYNC_NAME_CHARS) {
                slim.setHoverName(name);
            }
        }
        return slim.save(new CompoundTag());
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        if (!displayed.isEmpty()) {
            tag.put(ITEM_KEY, syncedItemTag(displayed));
        }
        tag.putByte(MODE_KEY, (byte) mode);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!displayed.isEmpty()) {
            tag.put(ITEM_KEY, displayed.save(new CompoundTag()));
        }
        tag.putByte(MODE_KEY, (byte) mode);
    }

    /** 存档和更新包同一套键：没有 item 键就是空屏 */
    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        displayed = tag.contains(ITEM_KEY, Tag.TAG_COMPOUND) ? ItemStack.of(tag.getCompound(ITEM_KEY)) : ItemStack.EMPTY;
        mode = Mth.clamp(tag.getInt(MODE_KEY), 0, MODES - 1);
    }

    /** 默认按碰撞箱（只有薄板）算，斜着看时方块图标会被提早裁掉；给整格 */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition);
    }

    // ------------------------------------------------------------------
    // capability：所有面、1 格、上限 1 个
    // ------------------------------------------------------------------

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER && !remove) {
            return handlerCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        handlerCap.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        handlerCap = LazyOptional.of(() -> handler);
    }

    private final class DisplayHandler implements IItemHandlerModifiable {

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return slot == 0 ? displayed : ItemStack.EMPTY;
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (slot != 0 || stack.isEmpty() || !displayed.isEmpty()) {
                return stack;
            }
            if (!simulate) {
                displayed = stack.copyWithCount(1);
                changed(false);
            }
            return stack.getCount() == 1 ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - 1);
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 0 || amount <= 0 || displayed.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int count = Math.min(amount, displayed.getCount());
            ItemStack out = displayed.copyWithCount(count);
            if (!simulate) {
                displayed = count >= displayed.getCount() ? ItemStack.EMPTY : displayed.copyWithCount(displayed.getCount() - count);
                changed(false);
            }
            return out;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return slot == 0;
        }

        /** 按接口约定照原样放（调用方自己看 getSlotLimit）；不静默删掉多出来的 */
        @Override
        public void setStackInSlot(int slot, @NotNull ItemStack stack) {
            if (slot == 0) {
                displayed = stack;
                changed(false);
            }
        }
    }
}
