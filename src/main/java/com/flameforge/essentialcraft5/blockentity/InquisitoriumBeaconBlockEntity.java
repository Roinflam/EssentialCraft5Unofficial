package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.item.InquisitoriumCoordinatesItem;
import com.flameforge.essentialcraft5.registry.ECInquisitorium;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 审判庭信标的方块实体（第十批 10c，batch10.md §5.10.4）：只记着做信标时用的那份审判庭坐标（{@code Dim / X / Z}，键同坐标物品）。
 * 放下时从物品根标签读进来（{@code block/InquisitoriumBeaconBlock#setPlacedBy}），挖下来经掉落表的 copy_nbt 带回物品；不 tick、不同步给客户端。
 */
public class InquisitoriumBeaconBlockEntity extends BlockEntity {

    @Nullable
    private InquisitoriumCoordinatesItem.Target target;

    public InquisitoriumBeaconBlockEntity(BlockPos pos, BlockState state) {
        super(ECInquisitorium.INQUISITORIUM_BEACON_BE.get(), pos, state);
    }

    /** 记着的坐标（创造栏拿的空白信标为 null） */
    @Nullable
    public InquisitoriumCoordinatesItem.Target target() {
        return target;
    }

    public void setTarget(@Nullable InquisitoriumCoordinatesItem.Target target) {
        this.target = target;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (target != null) {
            target.write(tag);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        target = InquisitoriumCoordinatesItem.Target.read(tag);
    }
}
