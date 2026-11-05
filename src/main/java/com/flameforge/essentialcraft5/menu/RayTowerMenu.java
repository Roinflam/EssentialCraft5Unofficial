package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.blockentity.RayTowerBlockEntity;
import com.flameforge.essentialcraft5.registry.ECGenerators;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/** 射线塔界面。槽位坐标照搬原作 ContainerRayTower：绑定宝石 (108, 23)。没有额外数值。 */
public class RayTowerMenu extends MachineMenu {

    public RayTowerMenu(int id, Inventory playerInventory, RayTowerBlockEntity be) {
        this(id, playerInventory, be.getBlockPos(), be.getInventory(), MachineData.server(be), be);
    }

    public RayTowerMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos(), clientHandler(1, RayTowerBlockEntity::slotRule), MachineData.client(0), null);
    }

    private RayTowerMenu(int id, Inventory playerInventory, BlockPos pos, IItemHandler handler, MachineData data,
                         @Nullable RayTowerBlockEntity be) {
        super(ECGenerators.RAY_TOWER_MENU.get(), id, playerInventory, pos, data, ECGenerators.RAY_TOWER.get(), be);
        addMachineSlot(FilteredSlot.boundGem(handler, RayTowerBlockEntity.SLOT_GEM, 108, 23));
        addPlayerInventory(playerInventory);
    }
}
