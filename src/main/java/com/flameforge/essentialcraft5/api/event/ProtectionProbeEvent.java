package com.flameforge.essentialcraft5.api.event;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;

/**
 * 权限探测：本模组要知道「这名玩家能不能动这一格」时（远程绑定、挪实体、机器以主人身份干活之前），以他的身份发的破坏事件。
 * <b>只用来问能不能动，方块不会被破坏</b>：事件发完不删方块、不掉落、不给经验，被取消就当「不能动」。
 * <p>
 * 它是 {@link BlockEvent.BreakEvent} 的子类，在 Forge 总线上（{@code MinecraftForge.EVENT_BUS}）发：Forge 的事件总线会把子类事件交给
 * 父类的监听器，所以只认 {@code BreakEvent} 的领地模组照样收得到，取消它照样算「不许」，权限语义和普通破坏事件一样。
 * 按破坏发奖励、记日志、按主手工具连锁挖的监听器想区分探测和真挖，判 {@code event instanceof ProtectionProbeEvent} 就行（探测时跳过）。
 * 只想处理探测的，直接监听这个类。
 * <p>
 * 真的替玩家破坏方块（法杖熔掉、建筑魔杖拆格子）发的仍是普通的 {@code BreakEvent}，不是这个类。
 * <p>
 * 发事件的那一下玩家的主手是空的（构造器按主手算经验，所以 {@link #getExpToDrop()} 不反映玩家手上的工具）。
 * <p>
 * 类名以 {@code ProbeEvent} 结尾是有意的：在 Mohist 系混合端上，服务端可以按「{@code BreakEvent} 的子类、简单类名以 {@code ProbeEvent} 结尾」
 * 认出探测（不必依赖本模组的类），只问领地插件、不当成玩家真挖了一格。改名会让这种识别失效。
 */
public class ProtectionProbeEvent extends BlockEvent.BreakEvent {

    public ProtectionProbeEvent(Level level, BlockPos pos, BlockState state, Player player) {
        super(level, pos, state, player);
    }
}
