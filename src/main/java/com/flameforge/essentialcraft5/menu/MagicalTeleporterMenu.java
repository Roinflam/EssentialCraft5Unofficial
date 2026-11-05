package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.blockentity.MagicalTeleporterBlockEntity;
import com.flameforge.essentialcraft5.registry.ECVoidMachines;
import com.flameforge.essentialcraft5.teleport.ECTeleport;
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

/**
 * 魔力传送器界面。槽位坐标照搬原作 ContainerMagicalTeleporter：0 号格 MRU 源宝石 (108, 23)、1 号格落点宝石 (140, 23)，都只收绑定宝石。
 * <p>
 * 额外数值：状态、落点预览（{@link ECTeleport.Result} 序号 + 1，0 = 不知道）、这一次的费用、蓄能进度与所需刻数（刻数配置上限 72000，超出 short，拆两格）、
 * 结构第一个不符的格子（北向偏移，有没有另占一格）、目的地是不是在别的维度。
 * 落点坐标不同步（只给看界面的人看结论），1 号格宝石本身照常走物品栏同步，开界面的人本来就看得到它的提示。
 */
public class MagicalTeleporterMenu extends MachineMenu {

    public static final int EXTRA_STATUS = 0;
    public static final int EXTRA_PREVIEW = 1;
    public static final int EXTRA_COST = 2;
    public static final int EXTRA_PROGRESS = 4;
    public static final int EXTRA_TICKS = 6;
    public static final int EXTRA_HAS_MISMATCH = 8;
    public static final int EXTRA_MISMATCH_X = 9;
    public static final int EXTRA_MISMATCH_Y = 10;
    public static final int EXTRA_MISMATCH_Z = 11;
    public static final int EXTRA_CROSS_DIMENSION = 12;
    public static final int EXTRA_COUNT = 13;

    public MagicalTeleporterMenu(int id, Inventory playerInventory, MagicalTeleporterBlockEntity be) {
        this(id, playerInventory, be.getBlockPos(), be.getInventory(), MachineData.server(be, extras(be)), be);
    }

    public MagicalTeleporterMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos(),
                clientHandler(MagicalTeleporterBlockEntity.SLOTS, MagicalTeleporterBlockEntity::slotRule),
                MachineData.client(EXTRA_COUNT), null);
    }

    private MagicalTeleporterMenu(int id, Inventory playerInventory, BlockPos pos, IItemHandler handler, MachineData data,
                                  @Nullable MagicalTeleporterBlockEntity be) {
        super(ECVoidMachines.MAGICAL_TELEPORTER_MENU.get(), id, playerInventory, pos, data, ECVoidMachines.MAGICAL_TELEPORTER.get(), be);
        addMachineSlot(new SlotItemHandler(handler, MagicalTeleporterBlockEntity.SLOT_SOURCE, 108, 23));
        addMachineSlot(new SlotItemHandler(handler, MagicalTeleporterBlockEntity.SLOT_DESTINATION, 140, 23));
        addPlayerInventory(playerInventory);
    }

    /** 服务端的额外数值，顺序和上面的 EXTRA_* 一致 */
    private static IntSupplier[] extras(MagicalTeleporterBlockEntity be) {
        List<IntSupplier> list = new ArrayList<>(EXTRA_COUNT);
        list.add(() -> be.status().ordinal());
        list.add(be::previewId);
        Collections.addAll(list, MachineData.wide(be::currentCost));
        Collections.addAll(list, MachineData.wide(be::progress));
        Collections.addAll(list, MachineData.wide(be::ticksRequired));
        list.add(() -> be.mismatchOffset() == null ? 0 : 1);
        list.add(() -> be.mismatchOffset() == null ? 0 : be.mismatchOffset().getX());
        list.add(() -> be.mismatchOffset() == null ? 0 : be.mismatchOffset().getY());
        list.add(() -> be.mismatchOffset() == null ? 0 : be.mismatchOffset().getZ());
        list.add(() -> be.targetsOtherDimension() ? 1 : 0);
        return list.toArray(IntSupplier[]::new);
    }

    // ---- 客户端读数 ----

    public MagicalTeleporterBlockEntity.Status status() {
        return MagicalTeleporterBlockEntity.Status.byId(data.extra(EXTRA_STATUS));
    }

    /** 落点预览（按原版玩家尺寸、不查权限）；null = 不知道（宝石没绑、目标维度不存在） */
    @Nullable
    public ECTeleport.Result preview() {
        int id = data.extra(EXTRA_PREVIEW);
        return id <= 0 ? null : ECTeleport.Result.byId(id - 1);
    }

    /** 这一次的费用；宝石没绑时 0 */
    public int cost() {
        return data.extraWide(EXTRA_COST);
    }

    public int progress() {
        return data.extraWide(EXTRA_PROGRESS);
    }

    public int ticksRequired() {
        return data.extraWide(EXTRA_TICKS);
    }

    /** 结构第一个不符的格子（北向偏移；结构四面对称、按北向判定，所以就是相对机器的世界偏移）；没有时为 null */
    @Nullable
    public BlockPos mismatchOffset() {
        if (data.extra(EXTRA_HAS_MISMATCH) == 0) {
            return null;
        }
        return new BlockPos(data.extra(EXTRA_MISMATCH_X), data.extra(EXTRA_MISMATCH_Y), data.extra(EXTRA_MISMATCH_Z));
    }

    /** 1 号格宝石指向别的维度（费用 = 存满） */
    public boolean crossDimension() {
        return data.extra(EXTRA_CROSS_DIMENSION) != 0;
    }
}
