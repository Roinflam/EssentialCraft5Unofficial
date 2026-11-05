package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.blockentity.LeyCondenserBlockEntity;
import com.flameforge.essentialcraft5.blockentity.LeyCondenserBlockEntity.Target;
import com.flameforge.essentialcraft5.registry.ECLeyCondenser;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 地脉凝聚器界面（EC5 新机器，第九批）。槽位：绑定宝石 (116, 5)、9 个输出格 3×3（x 116 / 134 / 152，y 27 / 45 / 63，只取不放）。
 * 选目标走 {@link #clickMenuButton}：按钮号 = {@link Target} 的序号（0~9），再点一次已选中的 = 取消；不收的目标点不了
 * （porting_guide §9：不带坐标，原版已查 containerId、stillValid、旁观者，这里只认服务端菜单、校验按钮号）。
 * <p>
 * 额外数值（{@link MachineData#extra}，都在 short 范围：用时上限 24 000 刻）：状态、进度、每个的刻数、目标序号（−1 = 没选）、这里有没有地脉、
 * 挡路那台的区块内 x、y、区块内 z（没有挡路的 x 为 −1）；之后是 ESPE 那 4 格（{@link MachineData#serverWithESPE}）。
 */
public class LeyCondenserMenu extends MachineMenu {

    public static final int EXTRA_STATUS = 0;
    public static final int EXTRA_PROGRESS = 1;
    public static final int EXTRA_MAX_PROGRESS = 2;
    public static final int EXTRA_TARGET = 3;
    public static final int EXTRA_HAS_LEY = 4;
    public static final int EXTRA_BLOCKER_X = 5;
    public static final int EXTRA_BLOCKER_Y = 6;
    public static final int EXTRA_BLOCKER_Z = 7;
    private static final int EXTRA_COUNT = 8;

    @Nullable
    private final LeyCondenserBlockEntity condenser;

    public LeyCondenserMenu(int id, Inventory playerInventory, LeyCondenserBlockEntity be) {
        this(id, playerInventory, be.getBlockPos(), be.getInventory(), MachineData.serverWithESPE(be,
                () -> be.status().ordinal(),
                be::progress,
                be::maxProgress,
                () -> be.target() == null ? -1 : be.target().ordinal(),
                () -> be.hasLeyForMenu() ? 1 : 0,
                () -> be.blocker() == null ? -1 : be.blocker().getX() & 15,
                () -> be.blocker() == null ? 0 : be.blocker().getY(),
                () -> be.blocker() == null ? 0 : be.blocker().getZ() & 15), be);
    }

    public LeyCondenserMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos(),
                clientHandler(LeyCondenserBlockEntity.SLOTS, LeyCondenserBlockEntity::slotRule),
                MachineData.clientWithESPE(EXTRA_COUNT), null);
    }

    private LeyCondenserMenu(int id, Inventory playerInventory, BlockPos pos, IItemHandler handler, MachineData data,
                             @Nullable LeyCondenserBlockEntity be) {
        super(ECLeyCondenser.LEY_CONDENSER_MENU.get(), id, playerInventory, pos, data, ECLeyCondenser.LEY_CONDENSER.get(), be);
        this.condenser = be;
        addMachineSlot(FilteredSlot.boundGem(handler, LeyCondenserBlockEntity.SLOT_GEM, 116, 5));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addMachineSlot(new OutputSlot(handler, LeyCondenserBlockEntity.FIRST_OUTPUT + row * 3 + col, 116 + col * 18, 27 + row * 18));
            }
        }
        addPlayerInventory(playerInventory);
    }

    /**
     * 按钮 0~9：选这种目标；点已选中的那种 = 取消。不收的目标（用时配成 0、产物是燃料）不让选。
     * 客户端、客户端菜单、范围外的按钮号一律 false（porting_guide §9）。
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (condenser == null || !(player instanceof ServerPlayer)) {
            return false;
        }
        Target target = Target.byId(id);
        if (target == null) {
            return false;
        }
        if (target == condenser.target()) {
            condenser.setTarget(null);
            return true;
        }
        if (!condenser.targetEnabled(target)) {
            return false;
        }
        condenser.setTarget(target);
        return true;
    }

    public LeyCondenserBlockEntity.Status status() {
        return LeyCondenserBlockEntity.Status.byId(data.extra(EXTRA_STATUS));
    }

    public int progress() {
        return data.extra(EXTRA_PROGRESS);
    }

    public int maxProgress() {
        return data.extra(EXTRA_MAX_PROGRESS);
    }

    @Nullable
    public Target target() {
        return Target.byId(data.extra(EXTRA_TARGET));
    }

    public boolean hasLey() {
        return data.extra(EXTRA_HAS_LEY) != 0;
    }

    /** 挡路那台的坐标（按本机所在区块还原）；没有返回 null */
    @Nullable
    public BlockPos blocker() {
        int localX = data.extra(EXTRA_BLOCKER_X);
        if (localX < 0) {
            return null;
        }
        return new BlockPos((pos.getX() & ~15) | (localX & 15), data.extra(EXTRA_BLOCKER_Y), (pos.getZ() & ~15) | (data.extra(EXTRA_BLOCKER_Z) & 15));
    }
}
