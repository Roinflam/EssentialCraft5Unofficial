package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.SunRayAbsorberBlockEntity;
import com.flameforge.essentialcraft5.registry.ECSolar;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * 日光吸收器：被太阳棱镜的光束照到时产出混沌 MRU。原作 BlockSunRayAbsorber（OBJ 模型 sunraycollector，setLightOpacity(0)）。
 * 右键打开界面、潜行时不开，同原作。
 */
public class SunRayAbsorberBlock extends MachineBlock<SunRayAbsorberBlockEntity> {

    public SunRayAbsorberBlock(Properties properties) {
        super(properties, true);
    }

    @Override
    protected BlockEntityType<SunRayAbsorberBlockEntity> blockEntityType() {
        return ECSolar.SUN_RAY_ABSORBER_BE.get();
    }
}
