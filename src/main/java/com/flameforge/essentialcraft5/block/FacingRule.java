package com.flameforge.essentialcraft5.block;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.DirectionalPlaceContext;

/**
 * 放下时朝向怎么定（batch4 F4-8）。{@link FacingMachineBlock} / {@link FacingPulseMachineBlock} 用它；
 * 没有方块实体的方块（方块状态检测器）在自己的 {@code getStateForPlacement} 里直接 {@code FacingRule.CLICKED_FACE.apply(context)}。
 * 结果是 6 向（{@code BlockStateProperties.FACING}）。
 */
public enum FacingRule {
    /** 点中的那一面（从被点的方块指向新方块）：魔力漏斗、方块状态检测器（原作 {@code getStateForPlacement} 的 facing 参数） */
    CLICKED_FACE,
    /** 点中那一面的反面（背贴在被点的方块上）：红石传递器（原作 {@code BlockRedstoneTransmitter} L100~103） */
    CLICKED_FACE_OPPOSITE,
    /**
     * 朝着放置者（同 1.20 活塞 / 发射器：{@code getNearestLookingDirection().getOpposite()}，含上下）。目前没有使用者
     * （原本给执行器，原作 {@code BlockRightClicker} L100~106；执行器已去掉）。
     * 没有放置者（别的模组拿 null 玩家的上下文放）时退回点中的面；发射器放的（{@link DirectionalPlaceContext}）照原版取它给的方向。
     */
    FACING_PLACER;

    /** 按这条规则算朝向 */
    public Direction apply(BlockPlaceContext context) {
        return switch (this) {
            case CLICKED_FACE -> context.getClickedFace();
            case CLICKED_FACE_OPPOSITE -> context.getClickedFace().getOpposite();
            case FACING_PLACER -> context.getPlayer() != null || context instanceof DirectionalPlaceContext
                    ? context.getNearestLookingDirection().getOpposite()
                    : context.getClickedFace();
        };
    }
}
