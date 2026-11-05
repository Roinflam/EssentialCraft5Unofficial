package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.PulseGate;
import com.flameforge.essentialcraft5.blockentity.RedstonePulseReceiver;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * 红石上升沿（batch4 F4-8，policy §1.2「红石触发：上升沿 + 每台最小间隔」）。照原版发射器：方块状态 {@link #TRIGGERED} 记「上次看到的信号」，
 * 上升沿时排一个 {@value #DELAY} 刻后的计划刻，在计划刻里过方块实体的 {@link PulseGate} 再调 {@link RedstonePulseReceiver#onRedstonePulse}。
 * 没有信号变化时零开销（不需要方块实体 ticker）。{@link PulseMachineBlock}、{@link FacingPulseMachineBlock} 已经接好；
 * 自己写的方块（状态里要有 {@link #TRIGGERED}）在对应的方法里调这里的同名方法即可。
 * <p>
 * 修的原作问题：洗牌器、繁殖器、剪毛器原作在 {@code neighborChanged} 里「有信号就做一次」（{@code BlockRedstoneDeviceNotSided} L173~227），
 * 拉着拉杆时旁边每有一次方块更新就扫一遍 25³。
 *
 * <h2>细节</h2>
 * <ul>
 *   <li>上升沿 = {@code level.hasNeighborSignal(pos)} 从无到有（六面任一方向有信号，含强充能方块；不做原版发射器的「上方一格也算」准连接）。
 *       设 {@code TRIGGERED=true} 用标志 4（同原版发射器：不通知邻居、不发给客户端，模型不看它），在闸门上记一个挂起的上升沿，排计划刻。</li>
 *   <li>放下时（{@link #placementState}）按当时的信号初始化 {@code TRIGGERED}：放在已经通电的线上不会误触发，要先断再通。
 *       不经过放置上下文放下的（{@code /setblock}、别的模组）在 {@link #onPlace} 里排一个计划刻，到时只把 {@code TRIGGERED} 同步成当时的信号、不触发。
 *       （不在 onPlace 里直接 setBlock：那时方块实体还没建，嵌套 setBlock 会让外层把旧状态塞回方块实体。）</li>
 *   <li>计划刻：原版按「位置 + 方块」去重，已经排着的再排会被忽略，所以同一格同时只有一个；冷却中来的上升沿记一次，排在间隔结束那一刻。
 *       计划刻随区块存盘，区块卸载再加载后照常执行（那时主人离线就由模块自己决定丢弃）。</li>
 *   <li>挖起再放：新的方块实体，闸门从零开始，{@code TRIGGERED} 按放下时的信号，主人按新的放置者。</li>
 * </ul>
 */
public final class RedstonePulse {

    /** 上次看到的红石信号（原版发射器同一个属性） */
    public static final BooleanProperty TRIGGERED = BlockStateProperties.TRIGGERED;
    /** 上升沿之后几刻执行（原版发射器 4；新定 1） */
    public static final int DELAY = 1;

    private RedstonePulse() {
    }

    /** 六面有没有红石信号（魔力漏斗、线圈这类「有信号就停」的电平开关也用它） */
    public static boolean isPowered(Level level, BlockPos pos) {
        return level.hasNeighborSignal(pos);
    }

    /** {@code getStateForPlacement} 里用：把 {@link #TRIGGERED} 设成放下那一格当前有没有信号 */
    public static BlockState placementState(BlockState state, BlockPlaceContext context) {
        return state.setValue(TRIGGERED, isPowered(context.getLevel(), context.getClickedPos()));
    }

    /** {@code onPlace} 里用（先调 super）：换成这个方块时信号和 {@link #TRIGGERED} 不一致，就排一刻去同步（不触发） */
    public static void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState) {
        if (level.isClientSide || oldState.is(state.getBlock()) || !state.hasProperty(TRIGGERED)) {
            return;
        }
        if (state.getValue(TRIGGERED) != isPowered(level, pos)) {
            level.scheduleTick(pos, state.getBlock(), DELAY);
        }
    }

    /** {@code neighborChanged} 里用：上升沿记挂起、排计划刻；下降沿只清 {@link #TRIGGERED} */
    public static void neighborChanged(BlockState state, Level level, BlockPos pos) {
        if (level.isClientSide || !state.hasProperty(TRIGGERED)) {
            return;
        }
        boolean powered = isPowered(level, pos);
        boolean triggered = state.getValue(TRIGGERED);
        if (powered && !triggered) {
            level.setBlock(pos, state.setValue(TRIGGERED, true), Block.UPDATE_INVISIBLE);
            if (level.getBlockEntity(pos) instanceof RedstonePulseReceiver receiver) {
                receiver.pulseGate().markPending();
                // 挂起的上升沿也存盘（计划刻本来就随区块存）；只标记区块要保存，不像 setChanged 那样通知比较器
                level.blockEntityChanged(pos);
            }
            level.scheduleTick(pos, state.getBlock(), DELAY);
        } else if (!powered && triggered) {
            level.setBlock(pos, state.setValue(TRIGGERED, false), Block.UPDATE_INVISIBLE);
        }
    }

    /**
     * 计划刻（方块的 {@code tick}）里用：先把 {@link #TRIGGERED} 同步成当前信号（不算上升沿），
     * 再续做上一次没做完的脉冲、执行挂起的上升沿（过了间隔的话），最后按需要再排下一次。
     */
    public static void tick(BlockState state, ServerLevel level, BlockPos pos) {
        if (state.hasProperty(TRIGGERED)) {
            boolean powered = isPowered(level, pos);
            if (state.getValue(TRIGGERED) != powered) {
                state = state.setValue(TRIGGERED, powered);
                level.setBlock(pos, state, Block.UPDATE_INVISIBLE);
            }
        }
        if (!(level.getBlockEntity(pos) instanceof RedstonePulseReceiver receiver)) {
            return;
        }
        PulseGate gate = receiver.pulseGate();
        long now = level.getGameTime();
        try {
            if (gate.isContinuing()) {
                if (!gate.mayContinue(now) || !receiver.continuePulse(level, state)) {
                    gate.stopContinuing();
                }
            }
            if (gate.isPending() && gate.tryFire(now)) {
                level.blockEntityChanged(pos);
                if (receiver.onRedstonePulse(level, state)) {
                    gate.startContinuing();
                }
            }
        } catch (RuntimeException e) {
            // 计划刻随区块存盘：不接住的话，这台机器每次被加载都会把服务器带崩
            gate.stopContinuing();
            EssentialCraft5.LOGGER.error("{} 在 {} 处理红石脉冲时出错，这次脉冲作废", state.getBlock(), pos, e);
        }
        long wake = gate.nextWake(now);
        if (wake != PulseGate.NEVER && wake > now) {
            level.scheduleTick(pos, state.getBlock(), (int) Math.min(Integer.MAX_VALUE, wake - now));
        }
    }
}
