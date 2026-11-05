package com.flameforge.essentialcraft5.block.creative;

import com.flameforge.essentialcraft5.api.IColdBlock;
import com.flameforge.essentialcraft5.api.IHotBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 无尽冷热源（只放创造栏，registry/ECCreative）：空手右键在热源 / 冷源之间切换，用来满功率跑热力产能器、冷凝器。
 * <ul>
 *   <li><b>热源</b>（默认）：给热力产能器（和终极热力产能器，热源规则同一份）的热力系数 {@value #HEAT_MODIFIER}，
 *       也就是「不衰减」——系数是四面连乘，岩浆 0.95 四面才 0.81，四面都放它正好是基础产量。对冷凝器冷值 0。</li>
 *   <li><b>冷源</b>：给冷凝器的冷值 {@value #COLD_VALUE}，等于把冷凝器周围 7 × 7 × 7 除自己以外的 342 格全部摆满霜冻石（每块 0.5）的总和，
 *       一块就顶满配的冷凝器。对热力产能器系数 0（冷源不给热，和空气一样）。</li>
 * </ul>
 * 没有方块实体、不 tick、不动别的方块；切换只改自己的方块状态（右键先过 Forge 的交互事件，领地模组照常能拦）。
 */
public class InfiniteThermalSourceBlock extends Block implements IHotBlock, IColdBlock {

    public static final BooleanProperty COLD = BooleanProperty.create("cold");

    /** 热源的热力系数：1 = 不衰减（热力产能器原版方块里最高的岩浆是 0.95） */
    public static final float HEAT_MODIFIER = 1F;
    /** 冷源的冷值：(7³ − 1) × 霜冻石 0.5 */
    public static final float COLD_VALUE = 171F;

    public static final String SWITCH_KEY = "message.essentialcraft5.creative.infinite_thermal_source";

    public InfiniteThermalSourceBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(COLD, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(COLD);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState();
    }

    public static boolean isCold(BlockState state) {
        return state.hasProperty(COLD) && state.getValue(COLD);
    }

    @Override
    public float getHeatModifier(BlockGetter level, BlockPos pos, BlockState state) {
        return isCold(state) ? 0F : HEAT_MODIFIER;
    }

    @Override
    public float getColdModifier(BlockGetter level, BlockPos pos, BlockState state) {
        return isCold(state) ? COLD_VALUE : 0F;
    }

    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        // 手里拿着东西时不拦：照常靠着它放方块、用物品
        if (!player.getItemInHand(hand).isEmpty() || hand != InteractionHand.MAIN_HAND || !player.mayBuild()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            BlockState next = toggle(level, pos, state);
            boolean cold = isCold(next);
            level.playSound(null, pos, cold ? SoundEvents.POWDER_SNOW_PLACE : SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.6F, 1.2F);
            player.displayClientMessage(Component.translatable(SWITCH_KEY + (cold ? ".cold" : ".hot"))
                    .withStyle(cold ? ChatFormatting.AQUA : ChatFormatting.GOLD), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** 冷热互换，返回换完的状态（右键和 GameTest 共用） */
    public static BlockState toggle(Level level, BlockPos pos, BlockState state) {
        BlockState next = state.cycle(COLD);
        level.setBlock(pos, next, Block.UPDATE_ALL);
        return next;
    }
}
