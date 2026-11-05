package com.flameforge.essentialcraft5.item.attunement;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Event;
import org.jetbrains.annotations.Nullable;

/**
 * 一件同调装备的阶变了（Forge 总线，只在服务端发，不能取消）。各家族的阶效果要在升阶当刻做点什么（例：模块槽变多后刷新缓存）时听它；
 * 平时读阶直接用 {@link Attunement#stage}，不用记这个事件。
 * <p>
 * 来源：魔力工作台升阶 / 升格（{@link Cause#ASCENSION}，这时没有玩家，{@link #getPlayer()} 是 null；产物还在工作台的输出格里）、
 * 指令（{@link Cause#COMMAND}，玩家是被改的那位）。同调转移不改阶，不发。
 */
public class AttunementStageEvent extends Event {

    public enum Cause {
        ASCENSION,
        COMMAND
    }

    @Nullable
    private final Player player;
    private final ItemStack stack;
    private final AttunementFamily family;
    private final int oldStage;
    private final int newStage;
    private final Cause cause;

    public AttunementStageEvent(@Nullable Player player, ItemStack stack, AttunementFamily family, int oldStage, int newStage, Cause cause) {
        this.player = player;
        this.stack = stack;
        this.family = family;
        this.oldStage = oldStage;
        this.newStage = newStage;
        this.cause = cause;
    }

    /** 拿着 / 穿着这件的玩家；工作台升阶时没有，为 null */
    @Nullable
    public Player getPlayer() {
        return player;
    }

    /** 改过以后的那件（就是放进输出格 / 装备槽的那个对象） */
    public ItemStack getStack() {
        return stack;
    }

    public AttunementFamily getFamily() {
        return family;
    }

    public int getOldStage() {
        return oldStage;
    }

    public int getNewStage() {
        return newStage;
    }

    public Cause getCause() {
        return cause;
    }
}
