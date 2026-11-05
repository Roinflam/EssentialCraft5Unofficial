package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.blockentity.LeyProjectorBlockEntity;
import com.flameforge.essentialcraft5.registry.ECLeyProjector;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 地脉投影仪界面（EC5 新机器）。槽位：绑定宝石 (108, 5)、透镜 (126, 5)（上限 1 个）、9 个输出格 3×3（x 108 / 126 / 144，y 27 / 45 / 63，只取不放）。
 * 「保留岩石」开关走 {@link #clickMenuButton}（按钮号 {@link #BUTTON_KEEP_ROCK}，porting_guide §9：不带坐标，原版已查 containerId、stillValid、旁观者）。
 * <p>
 * 额外数值（{@link MachineData#extra}，都在 short 范围）：状态、进度、每格 tick 数、保留岩石、挡路那台的区块内 x、y、区块内 z（没有挡路的 x 为 −1）。
 * 挡路的那台一定在同一区块，只传区块内坐标，客户端按 {@link #getPos()} 所在区块还原。
 */
public class LeyProjectorMenu extends MachineMenu {

    public static final int BUTTON_KEEP_ROCK = 0;

    public static final int EXTRA_STATUS = 0;
    public static final int EXTRA_PROGRESS = 1;
    public static final int EXTRA_MAX_PROGRESS = 2;
    public static final int EXTRA_KEEP_ROCK = 3;
    public static final int EXTRA_BLOCKER_X = 4;
    public static final int EXTRA_BLOCKER_Y = 5;
    public static final int EXTRA_BLOCKER_Z = 6;
    private static final int EXTRA_COUNT = 7;

    @Nullable
    private final LeyProjectorBlockEntity projector;

    public LeyProjectorMenu(int id, Inventory playerInventory, LeyProjectorBlockEntity be) {
        this(id, playerInventory, be.getBlockPos(), be.getInventory(), MachineData.server(be,
                () -> be.status().ordinal(),
                be::progress,
                be::ticksPerBlock,
                () -> be.keepRock() ? 1 : 0,
                () -> be.blocker() == null ? -1 : be.blocker().getX() & 15,
                () -> be.blocker() == null ? 0 : be.blocker().getY(),
                () -> be.blocker() == null ? 0 : be.blocker().getZ() & 15), be);
    }

    public LeyProjectorMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos(),
                clientHandler(LeyProjectorBlockEntity.SLOTS, LeyProjectorBlockEntity::slotRule),
                MachineData.client(EXTRA_COUNT), null);
    }

    private LeyProjectorMenu(int id, Inventory playerInventory, BlockPos pos, IItemHandler handler, MachineData data,
                             @Nullable LeyProjectorBlockEntity be) {
        super(ECLeyProjector.LEY_PROJECTOR_MENU.get(), id, playerInventory, pos, data, ECLeyProjector.LEY_PROJECTOR.get(), be);
        this.projector = be;
        addMachineSlot(FilteredSlot.boundGem(handler, LeyProjectorBlockEntity.SLOT_GEM, 108, 5));
        addMachineSlot(new LensSlot(handler, LeyProjectorBlockEntity.SLOT_LENS, 126, 5));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addMachineSlot(new OutputSlot(handler, LeyProjectorBlockEntity.FIRST_OUTPUT + row * 3 + col, 108 + col * 18, 27 + row * 18));
            }
        }
        addPlayerInventory(playerInventory);
    }

    /** 按钮 0：切换「保留岩石」。客户端、客户端菜单、别的按钮号一律 false（porting_guide §9） */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (projector == null || !(player instanceof ServerPlayer) || id != BUTTON_KEEP_ROCK) {
            return false;
        }
        projector.toggleKeepRock();
        return true;
    }

    public LeyProjectorBlockEntity.Status status() {
        return LeyProjectorBlockEntity.Status.byId(data.extra(EXTRA_STATUS));
    }

    public int progress() {
        return data.extra(EXTRA_PROGRESS);
    }

    public int maxProgress() {
        return data.extra(EXTRA_MAX_PROGRESS);
    }

    public boolean keepRock() {
        return data.extra(EXTRA_KEEP_ROCK) != 0;
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

    /** 透镜格：只收透镜标签里的物品（淡青宝石），一格只放 1 个（多放不会更强，照原作时运升级 1~128 个都是时运 I） */
    public static final class LensSlot extends FilteredSlot {

        public LensSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y, stack -> stack.is(ECLeyProjector.LENSES));
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public int getMaxStackSize(@NotNull ItemStack stack) {
            return 1;
        }
    }
}
