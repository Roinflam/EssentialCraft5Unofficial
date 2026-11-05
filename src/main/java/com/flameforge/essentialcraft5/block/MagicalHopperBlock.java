package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.MagicalHopperBlockEntity;
import com.flameforge.essentialcraft5.registry.ECLogistics;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * 魔力漏斗（原作 {@code BlockMagicalHopper}，100 行）：6 向朝向 = 放下时点中的那一面（点地面放下就朝上吐，原作 {@code getStateForPlacement} L55~57），
 * 不经过放置上下文放下的（{@code /setblock}）默认朝下（原作 L34）。右键开界面、潜行不开；挖掉掉出过滤器（{@code MachineBlock}）。
 * 硬度 1、抗爆 1（先设）、不透光（原作 {@code registerBlockSimple(…, 1, 1, 15)}）。
 * 红石是「有信号就停」的电平开关，在方块实体的 tick 里看（{@link RedstonePulse#isPowered}），不套上升沿（policy §4.3 规划补 ①）。
 */
public class MagicalHopperBlock extends FacingMachineBlock<MagicalHopperBlockEntity> {

    public MagicalHopperBlock(Properties properties) {
        super(properties, false, FacingRule.CLICKED_FACE, true);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN));
    }

    @Override
    protected BlockEntityType<MagicalHopperBlockEntity> blockEntityType() {
        return ECLogistics.MAGICAL_HOPPER_BE.get();
    }
}
