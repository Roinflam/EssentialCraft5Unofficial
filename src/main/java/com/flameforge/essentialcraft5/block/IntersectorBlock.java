package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.IntersectorBlockEntity;
import com.flameforge.essentialcraft5.registry.ECEnrichment;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * MRU 汇流器方块。对应原作 BlockMRUIntersector：右键开界面（潜行不开），拆掉时掉出两颗宝石；
 * JSON 模型（玻璃罩 + 强化石底座 + 十字支架），CUTOUT_MIPPED，不是整格（noOcclusion）。
 * 原作 registerBlockSimple(…, 1, 1, 15) 不透光度 15，不透天空光。
 */
public class IntersectorBlock extends MachineBlock<IntersectorBlockEntity> {

    public IntersectorBlock(Properties properties) {
        super(properties, false);
    }

    @Override
    protected BlockEntityType<IntersectorBlockEntity> blockEntityType() {
        return ECEnrichment.INTERSECTOR_BE.get();
    }
}
