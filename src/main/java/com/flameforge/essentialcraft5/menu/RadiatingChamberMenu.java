package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.blockentity.GlitchyCoreDecoder;
import com.flameforge.essentialcraft5.blockentity.RadiatingChamberBlockEntity;
import com.flameforge.essentialcraft5.recipe.RadiatingChamberRecipe;
import com.flameforge.essentialcraft5.registry.ECRadiatingChamber;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.ToIntFunction;

/**
 * 辐射仓界面。槽位坐标照搬原作 ContainerRadiatingChamber：宝石 (155, 5)，上原料 (108, 5)，下原料 (108, 41)，输出 (126, 23)。
 * 原作输出槽是普通槽（能放东西进去把机器堵死），这里改成只能取。
 * <p>
 * 额外数值（原作界面没有进度显示，这些是给新加的进度条用的）：进度、所需 tick、状态、配方的平衡值区间、每 tick 耗能。
 * 平衡值区间按 float 原始位传，和平衡值本身一样原样还原。
 * 解码故障核心时（第十批 10c）同一组数换成解码的：进度 / 所需刻数、核心目标平衡值 ± 容差、每刻平均耗能（{@code RadiatingChamberBlockEntity.decodeView}）。
 */
public class RadiatingChamberMenu extends MachineMenu {

    public static final int EXTRA_PROGRESS = 0;
    public static final int EXTRA_REQUIRED = 2;
    public static final int EXTRA_STATUS = 4;
    public static final int EXTRA_BALANCE_MIN = 5;
    public static final int EXTRA_BALANCE_MAX = 7;
    public static final int EXTRA_COST = 9;
    public static final int EXTRA_COUNT = 11;

    public RadiatingChamberMenu(int id, Inventory playerInventory, RadiatingChamberBlockEntity be) {
        this(id, playerInventory, be.getBlockPos(), be.getInventory(), MachineData.server(be, extras(be)), be);
    }

    public RadiatingChamberMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos(),
                clientHandler(RadiatingChamberBlockEntity.SLOTS, RadiatingChamberBlockEntity::slotRule),
                MachineData.client(EXTRA_COUNT), null);
    }

    private RadiatingChamberMenu(int id, Inventory playerInventory, BlockPos pos, IItemHandler handler, MachineData data,
                                 @Nullable RadiatingChamberBlockEntity be) {
        super(ECRadiatingChamber.RADIATING_CHAMBER_MENU.get(), id, playerInventory, pos, data,
                ECRadiatingChamber.RADIATING_CHAMBER.get(), be);
        addMachineSlot(FilteredSlot.boundGem(handler, RadiatingChamberBlockEntity.SLOT_GEM, 155, 5));
        addMachineSlot(new SlotItemHandler(handler, RadiatingChamberBlockEntity.SLOT_TOP, 108, 5));
        addMachineSlot(new SlotItemHandler(handler, RadiatingChamberBlockEntity.SLOT_BOTTOM, 108, 41));
        addMachineSlot(new OutputSlot(handler, RadiatingChamberBlockEntity.SLOT_OUTPUT, 126, 23));
        addPlayerInventory(playerInventory);
    }

    /** 服务端的额外数值，顺序和上面的 EXTRA_* 一致（大数拆两格） */
    private static IntSupplier[] extras(RadiatingChamberBlockEntity be) {
        List<IntSupplier> list = new ArrayList<>(EXTRA_COUNT);
        // 进度只配它所属的配方显示：红石停机期间换了原料，存着的是旧配方的进度，不能配着新配方的总量显示
        Collections.addAll(list, MachineData.wide(() -> {
            GlitchyCoreDecoder.View decoding = be.decodeView();
            if (decoding != null) {
                return decoding.progress();
            }
            RadiatingChamberRecipe shown = be.getDisplayRecipe();
            return shown != null && shown.getId().equals(be.getProgressRecipe()) ? be.getProgress() : 0;
        }));
        Collections.addAll(list, MachineData.wide(value(be, RadiatingChamberRecipe::getMRU, GlitchyCoreDecoder.View::required)));
        list.add(() -> be.getStatus().ordinal());
        Collections.addAll(list, MachineData.wide(value(be, r -> Float.floatToRawIntBits(r.getBalanceMin()),
                v -> Float.floatToRawIntBits(v.balanceMin()))));
        Collections.addAll(list, MachineData.wide(value(be, r -> Float.floatToRawIntBits(r.getBalanceMax()),
                v -> Float.floatToRawIntBits(v.balanceMax()))));
        Collections.addAll(list, MachineData.wide(value(be, RadiatingChamberBlockEntity::costPerTick, GlitchyCoreDecoder.View::costPerTick)));
        return list.toArray(IntSupplier[]::new);
    }

    /** 正在解码时取解码的数，否则取界面显示的配方上的数（没有配方时为 0） */
    private static IntSupplier value(RadiatingChamberBlockEntity be, ToIntFunction<RadiatingChamberRecipe> recipeGetter,
                                     ToIntFunction<GlitchyCoreDecoder.View> decodeGetter) {
        IntSupplier recipe = recipeValue(be, recipeGetter);
        return () -> {
            GlitchyCoreDecoder.View decoding = be.decodeView();
            return decoding != null ? decodeGetter.applyAsInt(decoding) : recipe.getAsInt();
        };
    }

    /** 界面显示的配方上的某个值，没有配方时为 0 */
    private static IntSupplier recipeValue(RadiatingChamberBlockEntity be, ToIntFunction<RadiatingChamberRecipe> getter) {
        return () -> {
            RadiatingChamberRecipe recipe = be.getDisplayRecipe();
            return recipe == null ? 0 : getter.applyAsInt(recipe);
        };
    }

    // ---- 客户端读数 ----

    public int progress() {
        return data.extraWide(EXTRA_PROGRESS);
    }

    /** 当前配方所需 tick 数；0 表示没有配方（此时区间、耗能都无意义） */
    public int required() {
        return data.extraWide(EXTRA_REQUIRED);
    }

    public RadiatingChamberBlockEntity.Status status() {
        return RadiatingChamberBlockEntity.Status.byId(data.extra(EXTRA_STATUS));
    }

    public float balanceMin() {
        return Float.intBitsToFloat(data.extraWide(EXTRA_BALANCE_MIN));
    }

    public float balanceMax() {
        return Float.intBitsToFloat(data.extraWide(EXTRA_BALANCE_MAX));
    }

    public int costPerTick() {
        return data.extraWide(EXTRA_COST);
    }
}
