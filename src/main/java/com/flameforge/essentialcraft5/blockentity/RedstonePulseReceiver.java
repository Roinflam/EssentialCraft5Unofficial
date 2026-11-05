package com.flameforge.essentialcraft5.blockentity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 吃红石上升沿的方块实体（batch4 F4-8）：洗牌器、繁殖器、剪毛器、分离器、合成器。
 * 方块用 {@code PulseMachineBlock} / {@code FacingPulseMachineBlock}（或自己调 {@code RedstonePulse}），它们在上升沿后 1 刻、
 * 过了 {@link #pulseGate()} 的最小间隔时，在<b>方块的计划刻</b>里调 {@link #onRedstonePulse}——不靠方块实体 ticker，纯脉冲机器可以完全不 tick。
 *
 * <h2>写法</h2>
 * <pre>{@code
 * private final PulseGate gate = new PulseGate(() -> ECConfig.get(Config.pulseInterval));
 * public PulseGate pulseGate() { return gate; }
 * public boolean onRedstonePulse(ServerLevel level, BlockState state) {
 *     if (ECProtection.ownerState(this) != ECProtection.OwnerState.ONLINE && …) return false;   // 主人离线：这次脉冲丢弃，不排队
 *     … 做一轮，遇到 UNKNOWN（探测预算用完）的格子记下来 …
 *     return 还有没探完的格子;      // true → 下一刻调 continuePulse
 * }
 * public boolean continuePulse(ServerLevel level, BlockState state) { … 接着做 …; return 还没做完; }
 * // saveAdditional / load 里：gate.save(tag, "pulse");  gate.load(tag, "pulse");
 * }</pre>
 * 续做最长到间隔结束（{@link PulseGate#mayContinue}）；到点还没做完的就算了（下一次脉冲重新开始）。
 * 冷却中来的上升沿记一次，间隔一到就执行（计划刻原版按「位置 + 方块」去重，不会越排越多）。
 * 两个回调都只在服务端、方块所在区块在 tick 时调用；抛出的 {@code RuntimeException} 会被 {@code RedstonePulse} 接住记日志（计划刻随区块存盘，不接住的话每次读档都崩服）。
 */
public interface RedstonePulseReceiver {

    /** 这台机器的闸门（同一个对象，存读档由方块实体负责） */
    PulseGate pulseGate();

    /**
     * 一次上升沿（已经过了最小间隔）：做这一轮。
     *
     * @return true = 这次没做完（例如探测预算用完、有 UNKNOWN 的格子），下一刻调 {@link #continuePulse}；false = 做完了
     */
    boolean onRedstonePulse(ServerLevel level, BlockState state);

    /**
     * 续做上一次脉冲（只在 {@link #onRedstonePulse} 或上一次续做返回 true 之后、间隔结束之前的每一刻调）。默认什么都不做。
     *
     * @return true = 还没做完，下一刻再调
     */
    default boolean continuePulse(ServerLevel level, BlockState state) {
        return false;
    }
}
