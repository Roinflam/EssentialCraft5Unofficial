package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.blockentity.ForceDomeBlockEntity;
import com.flameforge.essentialcraft5.registry.ECForceDome;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntSupplier;

/**
 * 力场穹顶界面（第十批 10c-M4）。槽位只有绑定宝石 (152, 5)；没有按钮（开关用红石）。
 * <p>
 * 额外数值（{@link MachineData#extra}）：状态、可拦 / 未确认 / 不可拦的区块数（半径最多 64，覆盖的区块不超过 81 块）、半径、主人状态
 * （{@link ECProtection.OwnerState} 序号），之后两格是当前每秒多少 MRU（{@link MachineData#wide}），再两格是满负荷每秒多少。
 * 名额上限、领地结论记多久这些提示里的数直接读配置（服务端配置进服时发给客户端）。
 */
public class ForceDomeMenu extends MachineMenu {

    public static final int EXTRA_STATUS = 0;
    public static final int EXTRA_BLOCKABLE = 1;
    public static final int EXTRA_UNCONFIRMED = 2;
    public static final int EXTRA_DENIED = 3;
    public static final int EXTRA_RADIUS = 4;
    public static final int EXTRA_OWNER_STATE = 5;
    /** 两格（低、高 16 位） */
    public static final int EXTRA_COST = 6;
    /** 两格（低、高 16 位） */
    public static final int EXTRA_FULL_COST = 8;
    private static final int EXTRA_COUNT = 10;

    public ForceDomeMenu(int id, Inventory playerInventory, ForceDomeBlockEntity be) {
        this(id, playerInventory, be.getBlockPos(), be.getInventory(), MachineData.server(be, extras(be)), be);
    }

    public ForceDomeMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos(), clientHandler(ForceDomeBlockEntity.SLOTS, ForceDomeBlockEntity::slotRule),
                MachineData.client(EXTRA_COUNT), null);
    }

    private ForceDomeMenu(int id, Inventory playerInventory, BlockPos pos, IItemHandler handler, MachineData data,
                          @Nullable ForceDomeBlockEntity be) {
        super(ECForceDome.FORCE_DOME_MENU.get(), id, playerInventory, pos, data, ECForceDome.FORCE_DOME.get(), be);
        addMachineSlot(FilteredSlot.boundGem(handler, ForceDomeBlockEntity.SLOT_GEM, 152, 5));
        addPlayerInventory(playerInventory);
    }

    private static IntSupplier[] extras(ForceDomeBlockEntity be) {
        IntSupplier[] cost = MachineData.wide(be::costPerSecond);
        IntSupplier[] full = MachineData.wide(be::fullCostPerSecond);
        return new IntSupplier[] {
                () -> be.status().ordinal(),
                be::blockableChunks,
                be::unconfirmedChunks,
                be::deniedChunks,
                be::effectiveRadius,
                () -> ECProtection.ownerState(be).ordinal(),
                cost[0], cost[1],
                full[0], full[1]
        };
    }

    public ForceDomeBlockEntity.Status status() {
        return ForceDomeBlockEntity.Status.byId(data.extra(EXTRA_STATUS));
    }

    public int blockable() {
        return data.extra(EXTRA_BLOCKABLE);
    }

    public int unconfirmed() {
        return data.extra(EXTRA_UNCONFIRMED);
    }

    public int denied() {
        return data.extra(EXTRA_DENIED);
    }

    public int radius() {
        return data.extra(EXTRA_RADIUS);
    }

    public ECProtection.OwnerState ownerState() {
        int id = data.extra(EXTRA_OWNER_STATE);
        ECProtection.OwnerState[] values = ECProtection.OwnerState.values();
        return id >= 0 && id < values.length ? values[id] : ECProtection.OwnerState.NONE;
    }

    /** 当前每秒多少 MRU（按可拦的区块比例） */
    public int costPerSecond() {
        return data.extraWide(EXTRA_COST);
    }

    /** 满负荷每秒多少 MRU */
    public int fullCostPerSecond() {
        return data.extraWide(EXTRA_FULL_COST);
    }
}
