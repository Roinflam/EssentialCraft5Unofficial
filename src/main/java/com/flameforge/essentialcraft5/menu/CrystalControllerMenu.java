package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.api.IElementalCrystal;
import com.flameforge.essentialcraft5.blockentity.CrystalControllerBlockEntity;
import com.flameforge.essentialcraft5.registry.ECEssence;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * 水晶控制器界面。槽位坐标照搬原作 ContainerCrystalController：宝石 (108, 5)，精华 (140, 41)。
 * <p>
 * 额外数值：状态、旁边有没有水晶、四元素和大小（取整，原作也是取整显示）、1 号槽精华每 tick 的几率（百分数，×100 定点）、
 * 吃一个精华的耗能（服务端配置）。
 */
public class CrystalControllerMenu extends MachineMenu {

    public static final int EXTRA_STATUS = 0;
    public static final int EXTRA_HAS_CRYSTAL = 1;
    /** 火、水、地、风各一格，按 Element.ordinal() 排 */
    public static final int EXTRA_ELEMENTS = 2;
    public static final int EXTRA_SIZE = 6;
    public static final int EXTRA_CHANCE = 7;
    public static final int EXTRA_COST = 9;
    public static final int EXTRA_COUNT = 11;

    public CrystalControllerMenu(int id, Inventory playerInventory, CrystalControllerBlockEntity be) {
        this(id, playerInventory, be.getBlockPos(), be.getInventory(), MachineData.server(be, extras(be)), be);
    }

    public CrystalControllerMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos(),
                clientHandler(CrystalControllerBlockEntity.SLOTS, CrystalControllerBlockEntity::slotRule),
                MachineData.client(EXTRA_COUNT), null);
    }

    private CrystalControllerMenu(int id, Inventory playerInventory, BlockPos pos, IItemHandler handler, MachineData data,
                                  @Nullable CrystalControllerBlockEntity be) {
        super(ECEssence.CRYSTAL_CONTROLLER_MENU.get(), id, playerInventory, pos, data, ECEssence.CRYSTAL_CONTROLLER.get(), be);
        addMachineSlot(FilteredSlot.boundGem(handler, CrystalControllerBlockEntity.SLOT_GEM, 108, 5));
        addMachineSlot(new FilteredSlot(handler, CrystalControllerBlockEntity.SLOT_ESSENCE, 140, 41,
                stack -> CrystalControllerBlockEntity.slotRule(CrystalControllerBlockEntity.SLOT_ESSENCE, stack)));
        addPlayerInventory(playerInventory);
    }

    /** 服务端的额外数值，顺序和上面的 EXTRA_* 一致 */
    private static IntSupplier[] extras(CrystalControllerBlockEntity be) {
        List<IntSupplier> list = new ArrayList<>(EXTRA_COUNT);
        list.add(() -> be.getStatus().ordinal());
        list.add(() -> be.hasCrystal() ? 1 : 0);
        for (IElementalCrystal.Element element : IElementalCrystal.Element.values()) {
            list.add(() -> be.displayElement(element));
        }
        list.add(be::displaySize);
        Collections.addAll(list, MachineData.wideFixed(be::currentChancePercent));
        Collections.addAll(list, MachineData.wide(CrystalControllerBlockEntity::feedCost));
        return list.toArray(IntSupplier[]::new);
    }

    // ---- 客户端读数 ----

    public CrystalControllerBlockEntity.Status status() {
        return CrystalControllerBlockEntity.Status.byId(data.extra(EXTRA_STATUS));
    }

    public boolean hasCrystal() {
        return data.extra(EXTRA_HAS_CRYSTAL) != 0;
    }

    public int element(IElementalCrystal.Element element) {
        return data.extra(EXTRA_ELEMENTS + element.ordinal());
    }

    public int size() {
        return data.extra(EXTRA_SIZE);
    }

    /** 精华每 tick 被吃掉的几率（百分数，精度 0.01）；没放精华时 0 */
    public double chancePercent() {
        return data.extraFixed(EXTRA_CHANCE);
    }

    /** 吃一个精华的耗能 */
    public int feedCost() {
        return data.extraWide(EXTRA_COST);
    }

    /** 精华槽里的东西（客户端由原版菜单同步） */
    public ItemStack essence() {
        return slots.get(CrystalControllerBlockEntity.SLOT_ESSENCE).getItem();
    }
}
