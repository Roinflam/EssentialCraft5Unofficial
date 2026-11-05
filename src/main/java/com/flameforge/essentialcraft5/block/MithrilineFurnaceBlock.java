package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.MithrilineFurnaceBlockEntity;
import com.flameforge.essentialcraft5.registry.ECMithriline;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * 米斯莱熔炉：用 ESPE 把一种物品转化成另一种。对应原作 BlockMithrilineFurnace（Material.ROCK、MapColor.GREEN、OBJ 模型、
 * registerBlockSimple(…, 1, 1, 1)：不透光度 1，所以不透天空光）。
 * <p>
 * 结构（24 块米斯莱护板）和 ESPE 来源（13 个水晶柱位置）见 {@link ECMithriline#FURNACE_STRUCTURE}。
 * 不覆盖 onPlace / neighborChanged 去给结构缓存标脏：新放下的方块实体第一次 tick 就会判定；
 * 结构里又没有一格紧挨着熔炉，邻居更新通知不到，靠方块实体每 20 tick 复查（StructureCache 默认）。
 */
public class MithrilineFurnaceBlock extends MachineBlock<MithrilineFurnaceBlockEntity> {

    public MithrilineFurnaceBlock(Properties properties) {
        super(properties, false);
    }

    @Override
    protected BlockEntityType<MithrilineFurnaceBlockEntity> blockEntityType() {
        return ECMithriline.MITHRILINE_FURNACE_BE.get();
    }
}
