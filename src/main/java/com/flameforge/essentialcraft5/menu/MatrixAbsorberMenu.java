package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.blockentity.MatrixAbsorberBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.registry.ECBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntSupplier;

/**
 * 矩阵换能器界面。槽位坐标照搬原作 ContainerMatrixAbsorber：灵魂石 (26, 5)。
 * 额外数值：每次转换消耗的 UBMRU、产出的 MRU（都来自服务端配置，各拆两格），界面上显示「10 UBMRU → 1 MRU」。
 */
public class MatrixAbsorberMenu extends MachineMenu {

    public static final int EXTRA_USAGE = 0;
    public static final int EXTRA_GENERATED = 2;

    public MatrixAbsorberMenu(int id, Inventory playerInventory, MatrixAbsorberBlockEntity be) {
        this(id, playerInventory, be.getBlockPos(), be.getInventory(), serverData(be), be);
    }

    public MatrixAbsorberMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos(),
                clientHandler(1, MatrixAbsorberBlockEntity::slotRule), MachineData.client(4), null);
    }

    private MatrixAbsorberMenu(int id, Inventory playerInventory, BlockPos pos, IItemHandler handler, MachineData data,
                               @Nullable MatrixAbsorberBlockEntity be) {
        super(ECBootstrap.MATRIX_ABSORBER_MENU.get(), id, playerInventory, pos, data, ECBootstrap.MATRIX_ABSORBER.get(), be);
        // 槽位规则在 handler 上（服务端是方块实体的，客户端是 clientHandler 套的同一份），SlotItemHandler.mayPlace 会问它
        addMachineSlot(new SlotItemHandler(handler, MatrixAbsorberBlockEntity.SLOT_SOUL_STONE, 26, 5));
        addPlayerInventory(playerInventory);
    }

    private static MachineData serverData(MatrixAbsorberBlockEntity be) {
        IntSupplier[] usage = MachineData.wide(() -> ECConfig.get(MatrixAbsorberBlockEntity.Config.ubmruUsage));
        IntSupplier[] generated = MachineData.wide(() -> ECConfig.get(MatrixAbsorberBlockEntity.Config.mruGenerated));
        return MachineData.server(be, usage[0], usage[1], generated[0], generated[1]);
    }

    /** 每次转换消耗的 UBMRU */
    public int usage() {
        return data.extraWide(EXTRA_USAGE);
    }

    /** 每次转换产出的 MRU */
    public int generated() {
        return data.extraWide(EXTRA_GENERATED);
    }
}
