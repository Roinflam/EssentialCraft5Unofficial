package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.api.IElementalCrystal.Element;
import com.flameforge.essentialcraft5.blockentity.ElementalDecomposerBlockEntity;
import com.flameforge.essentialcraft5.registry.ECDecomposer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * 元素分解器界面（batch12.md §4.8.2）。左边 MRU 条，上面状态字、状态字右边绑定宝石格，下面 3×3 输入格；输入格右边四行元素点数（x/32）；
 * 最右两列输出格，每行一种元素（火、水、地、风），左列普通、右列罕见。
 * <p>
 * 额外数值：状态、四种元素的点数、多少点出一颗。
 */
public class ElementalDecomposerMenu extends MachineMenu {

    public static final int INPUT_X = 28;
    public static final int INPUT_Y = 24;
    public static final int OUTPUT_X = 134;
    public static final int OUTPUT_Y = 5;
    /** 绑定宝石格：状态字（27~109）和第一行输出格（134 起）之间 */
    public static final int GEM_X = 112;
    public static final int GEM_Y = 5;

    public static final int EXTRA_STATUS = 0;
    /** 火、水、地、风各一格，按 Element.ordinal() 排 */
    public static final int EXTRA_POINTS = 1;
    public static final int EXTRA_PER_ESSENCE = 5;
    public static final int EXTRA_COUNT = 6;

    public ElementalDecomposerMenu(int id, Inventory playerInventory, ElementalDecomposerBlockEntity be) {
        this(id, playerInventory, be.getBlockPos(), be.getInventory(), MachineData.server(be, extras(be)), be);
    }

    public ElementalDecomposerMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos(),
                clientHandler(ElementalDecomposerBlockEntity.SLOTS, ElementalDecomposerBlockEntity::slotRule),
                MachineData.client(EXTRA_COUNT), null);
    }

    private ElementalDecomposerMenu(int id, Inventory playerInventory, BlockPos pos, IItemHandler handler, MachineData data,
                                    @Nullable ElementalDecomposerBlockEntity be) {
        super(ECDecomposer.ELEMENTAL_DECOMPOSER_MENU.get(), id, playerInventory, pos, data, ECDecomposer.ELEMENTAL_DECOMPOSER.get(), be);
        addMachineSlot(FilteredSlot.boundGem(handler, ElementalDecomposerBlockEntity.SLOT_GEM, GEM_X, GEM_Y));
        for (int i = 0; i < ElementalDecomposerBlockEntity.INPUTS; i++) {
            addMachineSlot(new FilteredSlot(handler, ElementalDecomposerBlockEntity.FIRST_INPUT + i, INPUT_X + (i % 3) * 18, INPUT_Y + (i / 3) * 18,
                    stack -> ElementalDecomposerBlockEntity.elementOf(stack) != null));
        }
        for (Element element : Element.values()) {
            int y = OUTPUT_Y + element.ordinal() * 18;
            addMachineSlot(new OutputSlot(handler, ElementalDecomposerBlockEntity.outputSlot(element, false), OUTPUT_X, y));
            addMachineSlot(new OutputSlot(handler, ElementalDecomposerBlockEntity.outputSlot(element, true), OUTPUT_X + 18, y));
        }
        addPlayerInventory(playerInventory);
    }

    /** 服务端的额外数值，顺序和上面的 EXTRA_* 一致 */
    private static IntSupplier[] extras(ElementalDecomposerBlockEntity be) {
        List<IntSupplier> list = new ArrayList<>(EXTRA_COUNT);
        list.add(() -> be.getStatus().ordinal());
        for (Element element : Element.values()) {
            list.add(() -> be.getPoints(element));
        }
        list.add(ElementalDecomposerBlockEntity::pointsPerEssence);
        return list.toArray(IntSupplier[]::new);
    }

    // ---- 客户端读数 ----

    public ElementalDecomposerBlockEntity.Status status() {
        return ElementalDecomposerBlockEntity.Status.byId(data.extra(EXTRA_STATUS));
    }

    public int points(Element element) {
        return data.extra(EXTRA_POINTS + element.ordinal());
    }

    public int perEssence() {
        return data.extra(EXTRA_PER_ESSENCE);
    }
}
