package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.blockentity.MatrixTunerBlockEntity;
import com.flameforge.essentialcraft5.registry.ECMatrixTuner;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntSupplier;

/**
 * 矩阵调律器界面。只有一个绑定宝石格 (152, 44)。按钮 0 切换私人 / 公开（{@link #clickMenuButton}，porting_guide §9：
 * 不带任何参数，服务端自己判这人是不是主人或 OP）。
 * <p>
 * 额外数值：状态序号、是否公开、正在调的人还剩多少损伤（占两格，{@link MachineData#wide}，没人在调为 −1）；
 * 之后是 ESPE 那 4 格（{@link MachineData#serverWithESPE}）。
 */
public class MatrixTunerMenu extends MachineMenu {

    public static final int BUTTON_TOGGLE = 0;

    public static final int EXTRA_STATUS = 0;
    public static final int EXTRA_PUBLIC = 1;
    public static final int EXTRA_REMAINING = 2;
    private static final int EXTRA_COUNT = 4;

    @Nullable
    private final MatrixTunerBlockEntity tuner;

    public MatrixTunerMenu(int id, Inventory playerInventory, MatrixTunerBlockEntity be) {
        this(id, playerInventory, be.getBlockPos(), be.getInventory(), MachineData.serverWithESPE(be, extras(be)), be);
    }

    public MatrixTunerMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos(),
                clientHandler(MatrixTunerBlockEntity.SLOTS, MatrixTunerBlockEntity::slotRule),
                MachineData.clientWithESPE(EXTRA_COUNT), null);
    }

    private static IntSupplier[] extras(MatrixTunerBlockEntity be) {
        IntSupplier[] extras = new IntSupplier[EXTRA_COUNT];
        extras[EXTRA_STATUS] = () -> be.status().ordinal();
        extras[EXTRA_PUBLIC] = () -> be.isPublic() ? 1 : 0;
        System.arraycopy(MachineData.wide(be::remaining), 0, extras, EXTRA_REMAINING, 2);
        return extras;
    }

    private MatrixTunerMenu(int id, Inventory playerInventory, BlockPos pos, IItemHandler handler, MachineData data,
                            @Nullable MatrixTunerBlockEntity be) {
        super(ECMatrixTuner.MATRIX_TUNER_MENU.get(), id, playerInventory, pos, data, ECMatrixTuner.MATRIX_TUNER.get(), be);
        this.tuner = be;
        addMachineSlot(FilteredSlot.boundGem(handler, MatrixTunerBlockEntity.SLOT_GEM, 152, 44));
        addPlayerInventory(playerInventory);
    }

    /**
     * 按钮 0：私人 ↔ 公开。只有主人或 OP（权限 2）按得动，别人按了动作栏提示一句、模式不变。
     * 客户端、客户端菜单、别的按钮号一律 false。
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (tuner == null || !(player instanceof ServerPlayer serverPlayer) || id != BUTTON_TOGGLE) {
            return false;
        }
        if (!tuner.canToggle(serverPlayer)) {
            serverPlayer.displayClientMessage(Component.translatable("message.essentialcraft5.matrix_tuner.not_owner")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        tuner.setPublic(!tuner.isPublic());
        return true;
    }

    public MatrixTunerBlockEntity.Status status() {
        return MatrixTunerBlockEntity.Status.byId(data.extra(EXTRA_STATUS));
    }

    public boolean isPublic() {
        return data.extra(EXTRA_PUBLIC) != 0;
    }

    /** 正在调的人还剩多少损伤；没人在调为 −1 */
    public int remaining() {
        return data.extraWide(EXTRA_REMAINING);
    }
}
