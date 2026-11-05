package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.blockentity.MagicalCultivatorBlockEntity;
import com.flameforge.essentialcraft5.registry.ECCultivator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * 魔力栽培器界面（batch12.md §4.2.2）。左边 MRU 条，中间三行字（状态、绑定宝石、「株数 · 每轮时长」），
 * 下面一排种子格 / 水格 / 催长格和横箭头进度条；右边上方绑定宝石格，下面 3×3 输出格（照地脉凝聚器的位置）。
 * <p>
 * 额外数值：本轮进度、本轮时长、状态、株数、每轮时长（一轮中是锁定值，空闲时按现在的种子算）。
 */
public class MagicalCultivatorMenu extends MachineMenu {

    public static final int SEED_X = 28;
    public static final int SLOTS_Y = 60;
    public static final int ARROW_X = 86;

    public static final int EXTRA_PROGRESS = 0;
    public static final int EXTRA_ROUND_TICKS = 2;
    public static final int EXTRA_STATUS = 4;
    public static final int EXTRA_PLANTS = 5;
    public static final int EXTRA_DISPLAY_TICKS = 6;
    public static final int EXTRA_COUNT = 8;

    public MagicalCultivatorMenu(int id, Inventory playerInventory, MagicalCultivatorBlockEntity be) {
        this(id, playerInventory, be.getBlockPos(), be.getInventory(), MachineData.server(be, extras(be)), be);
    }

    public MagicalCultivatorMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos(),
                clientHandler(MagicalCultivatorBlockEntity.SLOTS, MagicalCultivatorBlockEntity::slotRule),
                MachineData.client(EXTRA_COUNT), null);
    }

    private MagicalCultivatorMenu(int id, Inventory playerInventory, BlockPos pos, IItemHandler handler, MachineData data,
                                  @Nullable MagicalCultivatorBlockEntity be) {
        super(ECCultivator.MAGICAL_CULTIVATOR_MENU.get(), id, playerInventory, pos, data, ECCultivator.MAGICAL_CULTIVATOR.get(), be);
        addMachineSlot(FilteredSlot.boundGem(handler, MagicalCultivatorBlockEntity.SLOT_GEM, 116, 5));
        // 菜单里的槽位顺序和物品栏下标不同：水格、催长格排在种子格前面，Shift 点击水桶、骨粉时先进各自的格子
        // （基类按槽位顺序找第一个肯收的空格，种子格什么都收）
        addMachineSlot(new FilteredSlot(handler, MagicalCultivatorBlockEntity.SLOT_WATER, SEED_X + 18, SLOTS_Y,
                stack -> stack.is(Items.WATER_BUCKET)));
        addMachineSlot(new FilteredSlot(handler, MagicalCultivatorBlockEntity.SLOT_BONEMEAL, SEED_X + 36, SLOTS_Y,
                stack -> stack.is(Items.BONE_MEAL)));
        addMachineSlot(new SlotItemHandler(handler, MagicalCultivatorBlockEntity.SLOT_SEED, SEED_X, SLOTS_Y));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addMachineSlot(new OutputSlot(handler, MagicalCultivatorBlockEntity.FIRST_OUTPUT + row * 3 + col, 116 + col * 18, 27 + row * 18));
            }
        }
        addPlayerInventory(playerInventory);
    }

    /** 服务端的额外数值，顺序和上面的 EXTRA_* 一致 */
    private static IntSupplier[] extras(MagicalCultivatorBlockEntity be) {
        List<IntSupplier> list = new ArrayList<>(EXTRA_COUNT);
        Collections.addAll(list, MachineData.wide(be::getProgress));
        Collections.addAll(list, MachineData.wide(be::getRoundTicks));
        list.add(() -> be.getStatus().ordinal());
        list.add(be::getDisplayPlants);
        Collections.addAll(list, MachineData.wide(be::getDisplayTicks));
        return list.toArray(IntSupplier[]::new);
    }

    // ---- 客户端读数 ----

    public int progress() {
        return data.extraWide(EXTRA_PROGRESS);
    }

    public int roundTicks() {
        return data.extraWide(EXTRA_ROUND_TICKS);
    }

    public MagicalCultivatorBlockEntity.Status status() {
        return MagicalCultivatorBlockEntity.Status.byId(data.extra(EXTRA_STATUS));
    }

    public int plants() {
        return data.extra(EXTRA_PLANTS);
    }

    public int displayTicks() {
        return data.extraWide(EXTRA_DISPLAY_TICKS);
    }
}
