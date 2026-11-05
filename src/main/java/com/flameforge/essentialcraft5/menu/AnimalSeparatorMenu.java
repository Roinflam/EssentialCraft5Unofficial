package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.block.AnimalSeparatorBlock;
import com.flameforge.essentialcraft5.blockentity.AnimalSeparatorBlockEntity;
import com.flameforge.essentialcraft5.registry.ECSeparators;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 动物分离器界面（幼年 / 成年共用）。原作借用射线塔那套（ContainerRayTower）：绑定宝石 (108, 23)。
 * 额外数值只有一格：主人状态（{@code ECProtection.OwnerState} 的序号 + 1，0 表示还没同步到；主人不在线但机器照常工作时报成在线），
 * 写法照第三批自然熔炉曾用过的主人状态（自然熔炉那份已随第四批改造去掉）。
 */
public class AnimalSeparatorMenu extends MachineMenu {

    public static final int EXTRA_OWNER = 0;
    private static final int EXTRA_COUNT = 1;

    public AnimalSeparatorMenu(int id, Inventory playerInventory, AnimalSeparatorBlockEntity be) {
        this(id, playerInventory, be.getBlockPos(), be.getInventory(),
                MachineData.server(be, () -> be.displayedOwnerState().ordinal() + 1), be.getBlockState().getBlock(), be);
    }

    public AnimalSeparatorMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos(), MachineMenu.clientHandler(1, AnimalSeparatorBlockEntity::slotRule),
                MachineData.client(EXTRA_COUNT), null, null);
    }

    private AnimalSeparatorMenu(int id, Inventory playerInventory, BlockPos pos, IItemHandler handler, MachineData data,
                                @Nullable Block block, @Nullable AnimalSeparatorBlockEntity be) {
        super(ECSeparators.ANIMAL_SEPARATOR_MENU.get(), id, playerInventory, pos, data, blockFor(playerInventory, pos, block), be);
        addMachineSlot(FilteredSlot.boundGem(handler, AnimalSeparatorBlockEntity.SLOT_GEM, 108, 23));
        addPlayerInventory(playerInventory);
    }

    /**
     * 客户端菜单的「方块」（MachineMenu 客户端按方块判断 stillValid，只认一个）：两种分离器共用这个菜单，取那一格现在的方块；
     * 客户端还没有那一格（区块没到）时退回幼年分离器。
     */
    private static Block blockFor(Inventory playerInventory, BlockPos pos, @Nullable Block block) {
        if (block != null) {
            return block;
        }
        Block here = playerInventory.player.level().getBlockState(pos).getBlock();
        return here instanceof AnimalSeparatorBlock ? here : ECSeparators.CHILD_SEPARATOR.get();
    }

    /** 主人状态；还没同步到（或数值不认识）时为 null */
    @Nullable
    public ECProtection.OwnerState ownerState() {
        int id = data.extra(EXTRA_OWNER) - 1;
        ECProtection.OwnerState[] states = ECProtection.OwnerState.values();
        return id >= 0 && id < states.length ? states[id] : null;
    }
}
