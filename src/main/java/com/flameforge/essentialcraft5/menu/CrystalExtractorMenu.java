package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.api.IElementalCrystal;
import com.flameforge.essentialcraft5.blockentity.CrystalExtractorBlockEntity;
import com.flameforge.essentialcraft5.registry.ECEssence;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * 水晶提取器界面。槽位坐标照搬原作 ContainerCrystalExtractor：宝石 (108, 5)，12 个产物格两行，
 * 从 (26, 23) 起每格 18 像素（第二行 y=41）。原作产物格是普通槽，这里改成只能取。
 * <p>
 * 额外数值（原作界面没有进度，是给新加的进度条用的）：进度、每轮所需 tick、状态、旁边有没有水晶、水晶大小和四元素（取整）。
 */
public class CrystalExtractorMenu extends MachineMenu {

    public static final int EXTRA_PROGRESS = 0;
    public static final int EXTRA_REQUIRED = 2;
    public static final int EXTRA_STATUS = 4;
    public static final int EXTRA_HAS_CRYSTAL = 5;
    public static final int EXTRA_SIZE = 6;
    /** 火、水、地、风各一格，按 Element.ordinal() 排 */
    public static final int EXTRA_ELEMENTS = 7;
    public static final int EXTRA_COUNT = 11;

    public CrystalExtractorMenu(int id, Inventory playerInventory, CrystalExtractorBlockEntity be) {
        this(id, playerInventory, be.getBlockPos(), be.getInventory(), MachineData.server(be, extras(be)), be);
    }

    public CrystalExtractorMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos(),
                clientHandler(CrystalExtractorBlockEntity.SLOTS, CrystalExtractorBlockEntity::slotRule),
                MachineData.client(EXTRA_COUNT), null);
    }

    private CrystalExtractorMenu(int id, Inventory playerInventory, BlockPos pos, IItemHandler handler, MachineData data,
                                 @Nullable CrystalExtractorBlockEntity be) {
        super(ECEssence.CRYSTAL_EXTRACTOR_MENU.get(), id, playerInventory, pos, data, ECEssence.CRYSTAL_EXTRACTOR.get(), be);
        addMachineSlot(FilteredSlot.boundGem(handler, CrystalExtractorBlockEntity.SLOT_GEM, 108, 5));
        for (int i = 0; i < CrystalExtractorBlockEntity.OUTPUTS; i++) {
            addMachineSlot(new OutputSlot(handler, CrystalExtractorBlockEntity.FIRST_OUTPUT + i, 26 + (i % 6) * 18, 23 + (i / 6) * 18));
        }
        addPlayerInventory(playerInventory);
    }

    /** 服务端的额外数值，顺序和上面的 EXTRA_* 一致 */
    private static IntSupplier[] extras(CrystalExtractorBlockEntity be) {
        List<IntSupplier> list = new ArrayList<>(EXTRA_COUNT);
        Collections.addAll(list, MachineData.wide(be::getProgress));
        Collections.addAll(list, MachineData.wide(CrystalExtractorBlockEntity::requiredTicks));
        list.add(() -> be.getStatus().ordinal());
        list.add(() -> be.hasCrystal() ? 1 : 0);
        list.add(be::displaySize);
        for (IElementalCrystal.Element element : IElementalCrystal.Element.values()) {
            list.add(() -> be.displayElement(element));
        }
        return list.toArray(IntSupplier[]::new);
    }

    // ---- 客户端读数 ----

    public int progress() {
        return data.extraWide(EXTRA_PROGRESS);
    }

    public int required() {
        return data.extraWide(EXTRA_REQUIRED);
    }

    public CrystalExtractorBlockEntity.Status status() {
        return CrystalExtractorBlockEntity.Status.byId(data.extra(EXTRA_STATUS));
    }

    public boolean hasCrystal() {
        return data.extra(EXTRA_HAS_CRYSTAL) != 0;
    }

    public int size() {
        return data.extra(EXTRA_SIZE);
    }

    public int element(IElementalCrystal.Element element) {
        return data.extra(EXTRA_ELEMENTS + element.ordinal());
    }
}
