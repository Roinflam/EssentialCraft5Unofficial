package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.blockentity.MatrixCrucibleBlockEntity;
import com.flameforge.essentialcraft5.matrix.MatrixStatus;
import com.flameforge.essentialcraft5.matrix.MatrixTrialChecks;
import com.flameforge.essentialcraft5.matrix.MatrixTrials;
import com.flameforge.essentialcraft5.network.ECNetwork;
import com.flameforge.essentialcraft5.network.S2CMatrixStatusPacket;
import com.flameforge.essentialcraft5.registry.ECMatrix;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * 矩阵淬炼台界面（第十三批 13a，batch13.md §3.1.2）。界面 256×246，布局见 {@code client/screen/MatrixCrucibleScreen}：
 * 绑定宝石格 ({@value #GEM_X}, {@value #SLOT_Y})，四个祭品格从 ({@value #OFFERING_X}, {@value #SLOT_Y}) 起每格 18，背包从 ({@value #INV_X}, {@value #INV_Y}) 起。
 * <ul>
 *   <li>按钮 0 = 开始淬炼（{@link MatrixTrials#tryStart}）；开不了时动作栏说第一条原因。</li>
 *   <li>试炼进行中祭品格锁住：拿不走、放不进、Shift 点击和数字键交换也不行（原版先问 {@link Slot#mayPickup}）。
 *       服务端直接看台子的锁，客户端看额外数值 0（{@link #EXTRA_LOCKED}）。</li>
 *   <li>界面要的「下一阶要什么、哪几样满足了」走矩阵状态包（{@link MatrixStatus}，台子那部分由 {@link MatrixTrialChecks#statusTag} 填）：
 *       打开时、点了按钮、试炼开始结束、存了储备之后各发一次，只发给开着界面的本人；另外每 {@value #STATUS_INTERVAL} 刻重算一次，
 *       和上次发的一样就不发（台子 MRU、柱子 ESPE 的变化最多晚两秒，开着界面挂机的人不再每秒收一整包）。</li>
 * </ul>
 */
public class MatrixCrucibleMenu extends MachineMenu {

    public static final int BUTTON_START = 0;

    public static final int EXTRA_LOCKED = 0;
    private static final int EXTRA_COUNT = 1;

    public static final int GEM_X = 97;
    public static final int OFFERING_X = 121;
    public static final int SLOT_Y = 104;
    public static final int INV_X = 48;
    public static final int INV_Y = 164;
    public static final int HOTBAR_Y = 222;
    /** 状态包每隔几刻重算一次（内容没变就不发） */
    public static final int STATUS_INTERVAL = 40;

    @Nullable
    private final MatrixCrucibleBlockEntity crucible;
    private final Player player;
    private int statusTimer;
    private boolean statusRequested = true;
    /** 上次发出去的状态（去掉矩阵容量；周期重算时内容一样就不发） */
    @Nullable
    private CompoundTag lastStatus;
    /** 这个界面发过几次状态包（服务端） */
    private int statusSent;

    public MatrixCrucibleMenu(int id, Inventory playerInventory, MatrixCrucibleBlockEntity be) {
        this(id, playerInventory, be.getBlockPos(), be.getInventory(), MachineData.server(be, () -> be.isLocked() ? 1 : 0), be);
    }

    public MatrixCrucibleMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos(),
                clientHandler(MatrixCrucibleBlockEntity.SLOTS, MatrixCrucibleBlockEntity::slotRule),
                MachineData.client(EXTRA_COUNT), null);
    }

    private MatrixCrucibleMenu(int id, Inventory playerInventory, BlockPos pos, IItemHandler handler, MachineData data,
                               @Nullable MatrixCrucibleBlockEntity be) {
        super(ECMatrix.MATRIX_CRUCIBLE_MENU.get(), id, playerInventory, pos, data, ECMatrix.MATRIX_CRUCIBLE.get(), be);
        this.crucible = be;
        this.player = playerInventory.player;
        addMachineSlot(FilteredSlot.boundGem(handler, MatrixCrucibleBlockEntity.SLOT_GEM, GEM_X, SLOT_Y));
        BooleanSupplier locked = be != null ? be::isLocked : () -> data.extra(EXTRA_LOCKED) != 0;
        for (int i = 0; i < MatrixCrucibleBlockEntity.OFFERING_SLOTS; i++) {
            addMachineSlot(new OfferingSlot(handler, MatrixCrucibleBlockEntity.FIRST_OFFERING + i, OFFERING_X + i * 18, SLOT_Y, locked));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, INV_X + col * 18, HOTBAR_Y));
        }
    }

    /** 祭品格现在锁着（两端） */
    public boolean isLocked() {
        return crucible != null ? crucible.isLocked() : data.extra(EXTRA_LOCKED) != 0;
    }

    /** 让下一次 {@link #broadcastChanges} 马上重发状态包（服务端） */
    public void requestStatus() {
        statusRequested = true;
    }

    /** 这个界面到现在发过几次状态包（服务端；GameTest 用它核对「没变不发」） */
    public int statusSent() {
        return statusSent;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (crucible == null || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (statusRequested || ++statusTimer >= STATUS_INTERVAL) {
            boolean forced = statusRequested;
            statusRequested = false;
            statusTimer = 0;
            CompoundTag tag = MatrixStatus.build(serverPlayer, MatrixTrialChecks.statusTag(serverPlayer, crucible));
            // 矩阵容量每秒都在自然换入，界面不显示它，比较时不算，不然换入中的人照样每次都发
            CompoundTag compared = tag.copy();
            compared.remove("pool");
            if (forced || !compared.equals(lastStatus)) {
                lastStatus = compared;
                statusSent++;
                if (serverPlayer.connection != null) {
                    ECNetwork.sendTo(serverPlayer, new S2CMatrixStatusPacket(tag));
                }
            }
        }
    }

    /**
     * 按钮 0：开始淬炼。条件不够、回响放不出来时动作栏说原因，什么都不扣。客户端菜单、别的按钮号一律 false。
     */
    @Override
    public boolean clickMenuButton(Player clicker, int id) {
        if (crucible == null || id != BUTTON_START || !(clicker instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        MatrixTrials.Start start = MatrixTrials.tryStart(serverPlayer, crucible);
        requestStatus();
        if (start.started()) {
            return true;
        }
        Component reason = switch (start.result()) {
            case BLOCKED -> Component.translatable("gui.essentialcraft5.matrix_crucible.blocker."
                    + (start.blocker() == null ? MatrixTrialChecks.Blocker.NO_DATA.key() : start.blocker().key()));
            case NO_ROOM -> Component.translatable("message.essentialcraft5.matrix_trial.no_room");
            case SPAWN_DENIED -> Component.translatable("message.essentialcraft5.matrix_trial.spawn_denied");
            case ROOM_UNKNOWN -> Component.translatable("message.essentialcraft5.matrix_trial.room_unknown");
            default -> Component.translatable("message.essentialcraft5.matrix_trial.payment");
        };
        serverPlayer.displayClientMessage(Component.translatable("message.essentialcraft5.matrix_trial.cannot_start", reason)
                .withStyle(ChatFormatting.RED), true);
        return true;
    }

    /** 祭品格：放什么都行（台子的槽位规则），试炼进行中锁住 */
    private static final class OfferingSlot extends SlotItemHandler {

        private final BooleanSupplier locked;

        OfferingSlot(IItemHandler handler, int index, int x, int y, BooleanSupplier locked) {
            super(handler, index, x, y);
            this.locked = locked;
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            return !locked.getAsBoolean() && super.mayPlace(stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return !locked.getAsBoolean() && super.mayPickup(player);
        }
    }
}
