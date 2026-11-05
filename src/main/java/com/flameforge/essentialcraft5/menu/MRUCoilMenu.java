package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.MRUCoilBlockEntity;
import com.flameforge.essentialcraft5.registry.ECTesla;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntSupplier;
import java.util.function.Predicate;

/**
 * MRU 特斯拉线圈界面。原作 ContainerMRUCoil：绑定宝石 (108, 23)、玩家白名单 (140, 41)。
 * <p>
 * <b>0、1 号格只有主人和创造模式的人能拿放</b>（{@link EditorSlot}）：白名单上的人能打开界面但只能看——否则名单成员能把白名单换成只写自己名字的，
 * 让别的成员挨打，或者拿走宝石关掉线圈。服务端每次按方块实体现判（{@link MRUCoilBlockEntity#canEdit}）；客户端按同步来的标志位预测（打开后第一次同步之前当成不能编辑）。
 * Shift 点击、数字键交换、双击收集、丢出都经过 {@code mayPickup / mayPlace}，另外 {@link #quickMoveStack} 也挡一道。
 * {@link #stillValid} 另查访问权：从白名单上被删掉的人界面随即关掉。
 * <p>
 * 按钮 {@link #BUTTON_TOGGLE_PLAYERS}：切换这台打不打玩家（⑤），只有能编辑的人按得动（服务端校验，porting_guide §9）。
 * <p>
 * 额外数值（{@link MachineData}，按序号）：0 状态（{@link MRUCoilBlockEntity.Status} 序号）、1 射程、2 标志位（见 {@code FLAG_*}）、
 * 3~5 结构第一格不对的位置（相对线圈）、6~7 每击费用（拆成两格）。
 */
public class MRUCoilMenu extends MachineMenu {

    public static final int BUTTON_TOGGLE_PLAYERS = 0;

    public static final int EXTRA_STATUS = 0;
    public static final int EXTRA_RADIUS = 1;
    public static final int EXTRA_FLAGS = 2;
    public static final int EXTRA_MISMATCH_X = 3;
    public static final int EXTRA_MISMATCH_Y = 4;
    public static final int EXTRA_MISMATCH_Z = 5;
    public static final int EXTRA_COST = 6;
    private static final int EXTRA_COUNT = 8;

    /** 这台打玩家的开关开着 */
    public static final int FLAG_HURT_PLAYERS = 1;
    /** 有记下的主人（没有主人的线圈不打玩家） */
    public static final int FLAG_HAS_OWNER = 1 << 1;
    /** 服务端配置允许线圈打玩家 */
    public static final int FLAG_PLAYERS_ALLOWED = 1 << 2;
    /** 看界面的这个人能拿放格子、切换开关 */
    public static final int FLAG_EDITABLE = 1 << 3;
    /** 3~5 号额外数值有效（结构有一格不对） */
    public static final int FLAG_MISMATCH = 1 << 4;
    /** 只打闯入者（配置 {@code trespassers_only}）：界面提示换一句 */
    public static final int FLAG_TRESPASSERS_ONLY = 1 << 5;

    /** 服务端才有 */
    @Nullable
    private final MRUCoilBlockEntity coil;
    private final Player player;

    public MRUCoilMenu(int id, Inventory playerInventory, MRUCoilBlockEntity be, Player player) {
        this(id, playerInventory, be.getBlockPos(), be.getInventory(), serverData(be, player), be, player);
    }

    public MRUCoilMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos(), MachineMenu.clientHandler(MRUCoilBlockEntity.SLOTS, MRUCoilBlockEntity::slotRule),
                MachineData.client(EXTRA_COUNT), null, playerInventory.player);
    }

    private MRUCoilMenu(int id, Inventory playerInventory, BlockPos pos, IItemHandler handler, MachineData data,
                        @Nullable MRUCoilBlockEntity be, Player player) {
        super(ECTesla.MRU_COIL_MENU.get(), id, playerInventory, pos, data, ECTesla.MRU_COIL.get(), be);
        this.coil = be;
        this.player = player;
        EditorSlot gem = new EditorSlot(this, handler, MRUCoilBlockEntity.SLOT_GEM, 108, 23,
                stack -> MRUCoilBlockEntity.slotRule(MRUCoilBlockEntity.SLOT_GEM, stack));
        gem.setBackground(InventoryMenu.BLOCK_ATLAS, EssentialCraft5.id("item/elemental/drop_air"));
        addMachineSlot(gem);
        addMachineSlot(new EditorSlot(this, handler, MRUCoilBlockEntity.SLOT_LIST, 140, 41,
                stack -> MRUCoilBlockEntity.slotRule(MRUCoilBlockEntity.SLOT_LIST, stack)));
        addPlayerInventory(playerInventory);
    }

    private static MachineData serverData(MRUCoilBlockEntity be, Player player) {
        IntSupplier[] cost = MachineData.wide(be::mruPerHit);
        return MachineData.server(be,
                () -> be.status().ordinal(),
                be::radius,
                () -> flags(be, player),
                () -> offset(be, 0),
                () -> offset(be, 1),
                () -> offset(be, 2),
                cost[0],
                cost[1]);
    }

    private static int flags(MRUCoilBlockEntity be, Player player) {
        int flags = 0;
        if (be.hurtPlayersEnabled()) {
            flags |= FLAG_HURT_PLAYERS;
        }
        if (ECProtection.owner(be) != null) {
            flags |= FLAG_HAS_OWNER;
        }
        if (MRUCoilBlockEntity.playersAllowedByConfig()) {
            flags |= FLAG_PLAYERS_ALLOWED;
        }
        if (be.canEdit(player)) {
            flags |= FLAG_EDITABLE;
        }
        if (be.mismatchOffset() != null) {
            flags |= FLAG_MISMATCH;
        }
        if (be.trespassersOnly()) {
            flags |= FLAG_TRESPASSERS_ONLY;
        }
        return flags;
    }

    private static int offset(MRUCoilBlockEntity be, int axis) {
        BlockPos off = be.mismatchOffset();
        if (off == null) {
            return 0;
        }
        int value = axis == 0 ? off.getX() : axis == 1 ? off.getY() : off.getZ();
        return Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
    }

    // ------------------------------------------------------------------
    // 读（界面用）
    // ------------------------------------------------------------------

    @Nullable
    public MRUCoilBlockEntity.Status status() {
        int id = data.extra(EXTRA_STATUS);
        MRUCoilBlockEntity.Status[] values = MRUCoilBlockEntity.Status.values();
        return id >= 0 && id < values.length ? values[id] : null;
    }

    public int radius() {
        return data.extra(EXTRA_RADIUS);
    }

    public boolean hasFlag(int flag) {
        return (data.extra(EXTRA_FLAGS) & flag) != 0;
    }

    /** 结构第一格不对的位置（世界坐标）；没有时为 null */
    @Nullable
    public BlockPos mismatchPos() {
        if (!hasFlag(FLAG_MISMATCH)) {
            return null;
        }
        return pos.offset(data.extra(EXTRA_MISMATCH_X), data.extra(EXTRA_MISMATCH_Y), data.extra(EXTRA_MISMATCH_Z));
    }

    public int mruPerHit() {
        return data.extraWide(EXTRA_COST);
    }

    /** 看界面的这个人能不能拿放 0、1 号格：服务端按方块实体现判，客户端按同步来的标志位 */
    public boolean canEditSlots() {
        return coil != null ? coil.canEdit(player) : hasFlag(FLAG_EDITABLE);
    }

    // ------------------------------------------------------------------
    // 交互
    // ------------------------------------------------------------------

    @Override
    public boolean stillValid(Player player) {
        return super.stillValid(player) && (coil == null || coil.canView(player));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!canEditSlots()) {
            // 不能编辑的人：机器格里的拿不出来；背包里的也不往机器格里塞（mayPlace 本来就挡，这里整个跳过，省得白算）
            return ItemStack.EMPTY;
        }
        return super.quickMoveStack(player, index);
    }

    /** 按钮只在服务端生效（客户端不预测）；只有能编辑的人（主人、创造模式）按得动 */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (coil == null || player.level().isClientSide) {
            return false;
        }
        if (id != BUTTON_TOGGLE_PLAYERS || !coil.canEdit(player)) {
            return false;
        }
        coil.setHurtPlayers(!coil.hurtPlayersEnabled());
        return true;
    }

    /** 只有能编辑的人能拿放的格子 */
    public static final class EditorSlot extends FilteredSlot {
        private final MRUCoilMenu menu;

        EditorSlot(MRUCoilMenu menu, IItemHandler handler, int index, int x, int y, Predicate<ItemStack> filter) {
            super(handler, index, x, y, filter);
            this.menu = menu;
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            return menu.canEditSlots() && super.mayPlace(stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return menu.canEditSlots() && super.mayPickup(player);
        }
    }
}
