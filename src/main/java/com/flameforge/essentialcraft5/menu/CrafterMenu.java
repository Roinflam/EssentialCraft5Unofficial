package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.blockentity.CrafterBlockEntity;
import com.flameforge.essentialcraft5.item.CraftingFrameItem;
import com.flameforge.essentialcraft5.recipe.CrafterGrid;
import com.flameforge.essentialcraft5.recipe.CraftingPattern;
import com.flameforge.essentialcraft5.registry.ECCrafter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 魔力合成器界面。槽位坐标照搬原作 ContainerCrafter：合成格按行排 (30 + x·18, 17 + y·18)、产物 (124, 35)、框架 (94, 17)，背包 (8, 84)。
 * 额外数值：框架状态（{@link CraftingPattern.State} 的序号：0 没装 / 空框架、1 有效、2 失效）。
 * 客户端的物品栏用 {@link CrafterBlockEntity#clientHandler}：合成格收什么取决于框架格里那件框架，客户端按同一份规则判断，放东西时不会闪回。
 */
public class CrafterMenu extends MachineMenu {

    public static final int EXTRA_FRAME_STATE = 0;
    private static final int EXTRA_COUNT = 1;

    public static final int GRID_X = 30;
    public static final int GRID_Y = 17;
    public static final int OUTPUT_X = 124;
    public static final int OUTPUT_Y = 35;
    public static final int FRAME_X = 94;
    public static final int FRAME_Y = 17;

    public CrafterMenu(int id, Inventory playerInventory, CrafterBlockEntity be) {
        this(id, playerInventory, be.getBlockPos(), be.getInventory(),
                MachineData.server(be, () -> be.frameState().ordinal()), be);
    }

    public CrafterMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos(), CrafterBlockEntity.clientHandler(playerInventory.player.level()),
                MachineData.client(EXTRA_COUNT), null);
    }

    private CrafterMenu(int id, Inventory playerInventory, BlockPos pos, IItemHandler handler, MachineData data,
                        @Nullable CrafterBlockEntity be) {
        super(ECCrafter.CRAFTER_MENU.get(), id, playerInventory, pos, data, ECCrafter.CRAFTER.get(), be);
        for (int i = 0; i < CrafterBlockEntity.GRID_SLOTS; i++) {
            addMachineSlot(new SlotItemHandler(handler, i, GRID_X + i % CrafterGrid.WIDTH * 18, GRID_Y + i / CrafterGrid.WIDTH * 18));
        }
        addMachineSlot(new OutputSlot(handler, CrafterBlockEntity.SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y));
        addMachineSlot(new FilteredSlot(handler, CrafterBlockEntity.SLOT_FRAME, FRAME_X, FRAME_Y,
                stack -> stack.getItem() instanceof CraftingFrameItem));
        addPlayerInventory(playerInventory);
    }

    /** 框架状态（服务端同步来的） */
    public CraftingPattern.State frameState() {
        return CraftingPattern.State.byOrdinal(data.extra(EXTRA_FRAME_STATE));
    }

    /** 框架格里的那件（界面画样本的半透明图用；客户端是同步来的） */
    public ItemStack frame() {
        return slots.get(CrafterBlockEntity.SLOT_FRAME).getItem();
    }
}
