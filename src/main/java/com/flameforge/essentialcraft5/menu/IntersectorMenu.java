package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.blockentity.IntersectorBlockEntity;
import com.flameforge.essentialcraft5.mru.MRULink;
import com.flameforge.essentialcraft5.registry.ECEnrichment;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * MRU 汇流器界面。槽位坐标照搬原作 ContainerMRUIntersector：两个绑定宝石 (108, 23)、(108, 41)。
 * 额外数值 1 格：第二个宝石的状态（第一个走 MachineData 的基础格）。
 */
public class IntersectorMenu extends MachineMenu {

    public IntersectorMenu(int id, Inventory playerInventory, IntersectorBlockEntity be) {
        this(id, playerInventory, be.getBlockPos(), be.getInventory(),
                MachineData.server(be, () -> be.getSecondLinkStatus().ordinal()), be);
    }

    public IntersectorMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos(), clientHandler(2, IntersectorBlockEntity::slotRule), MachineData.client(1), null);
    }

    private IntersectorMenu(int id, Inventory playerInventory, BlockPos pos, IItemHandler handler, MachineData data,
                            @Nullable IntersectorBlockEntity be) {
        super(ECEnrichment.INTERSECTOR_MENU.get(), id, playerInventory, pos, data, ECEnrichment.INTERSECTOR.get(), be);
        addMachineSlot(FilteredSlot.boundGem(handler, IntersectorBlockEntity.SLOT_GEM, 108, 23));
        addMachineSlot(FilteredSlot.boundGem(handler, IntersectorBlockEntity.SLOT_GEM_2, 108, 41));
        addPlayerInventory(playerInventory);
    }

    /** 第二个宝石的状态 */
    public MRULink.Status secondLinkStatus() {
        return MRULink.Status.byId(data.extra(0));
    }
}
