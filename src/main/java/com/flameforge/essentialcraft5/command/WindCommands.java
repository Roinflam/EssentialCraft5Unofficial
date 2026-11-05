package com.flameforge.essentialcraft5.command;

import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.Collection;

/**
 * {@code /ec5 player <targets> wind …}：查看、修改风之好感和风之眷顾（原作 /setwindpoints &lt;玩家&gt; &lt;值&gt;、/setwindbound &lt;玩家&gt; &lt;true|false&gt;）。
 * <ul>
 *   <li>{@code points get|set <值 ≥ 0>}：风之好感度（直接设，不乘系数、不给风之触）。</li>
 *   <li>{@code windbound get|set <true|false>}：风之眷顾。</li>
 * </ul>
 * 由 ECWind.init() 挂到 {@code /ec5 player <targets>} 下（权限 2，根节点统一要求）。改动走 IPlayerData 的 setter（自带 markDirty，10 tick 内同步给本人）。
 * 返回值（{@code execute store} 用）：get 只选中一个玩家时返回那个数（眷顾 1 / 0），多个时返回人数；set 返回改了几个人。
 * 逻辑写成静态方法，GameTest 用模拟玩家直接调。
 */
public final class WindCommands {

    private static final SimpleCommandExceptionType NO_DATA = new SimpleCommandExceptionType(
            Component.translatable("commands.essentialcraft5.no_player_data"));

    private WindCommands() {
    }

    /** 形状符合 {@link ECCommands.Subcommand} */
    public static LiteralArgumentBuilder<CommandSourceStack> build(CommandBuildContext context) {
        return Commands.literal("wind")
                .then(Commands.literal("points")
                        .then(Commands.literal("get")
                                .executes(ctx -> getPoints(ctx.getSource(), ECCommands.targets(ctx))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                        .executes(ctx -> setPoints(ctx.getSource(), ECCommands.targets(ctx), IntegerArgumentType.getInteger(ctx, "value"))))))
                .then(Commands.literal("windbound")
                        .then(Commands.literal("get")
                                .executes(ctx -> getWindbound(ctx.getSource(), ECCommands.targets(ctx))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> setWindbound(ctx.getSource(), ECCommands.targets(ctx), BoolArgumentType.getBool(ctx, "value"))))));
    }

    public static int getPoints(CommandSourceStack source, Collection<? extends Player> targets) throws CommandSyntaxException {
        int count = 0;
        int last = 0;
        for (Player player : targets) {
            IPlayerData data = PlayerDataEvents.get(player);
            if (data != null) {
                int value = data.getWindPoints();
                source.sendSuccess(() -> Component.translatable("commands.essentialcraft5.wind_points.get", player.getDisplayName(), value), false);
                last = value;
                count++;
            }
        }
        if (count == 0) {
            throw NO_DATA.create();
        }
        return count == 1 ? last : count;
    }

    public static int setPoints(CommandSourceStack source, Collection<? extends Player> targets, int value) throws CommandSyntaxException {
        int clamped = Math.max(0, value);
        int count = 0;
        Player only = null;
        for (Player player : targets) {
            IPlayerData data = PlayerDataEvents.get(player);
            if (data != null) {
                data.setWindPoints(clamped);
                only = player;
                count++;
            }
        }
        if (count == 0) {
            throw NO_DATA.create();
        }
        Player single = only;
        int total = count;
        if (total == 1) {
            source.sendSuccess(() -> Component.translatable("commands.essentialcraft5.setwindpoints.success", single.getDisplayName(), clamped), true);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.essentialcraft5.setwindpoints.success.multiple", total, clamped), true);
        }
        return count;
    }

    public static int getWindbound(CommandSourceStack source, Collection<? extends Player> targets) throws CommandSyntaxException {
        int count = 0;
        boolean last = false;
        for (Player player : targets) {
            IPlayerData data = PlayerDataEvents.get(player);
            if (data != null) {
                boolean value = data.isWindbound();
                source.sendSuccess(() -> Component.translatable(value ? "commands.essentialcraft5.windbound.get.true"
                        : "commands.essentialcraft5.windbound.get.false", player.getDisplayName()), false);
                last = value;
                count++;
            }
        }
        if (count == 0) {
            throw NO_DATA.create();
        }
        return count == 1 ? (last ? 1 : 0) : count;
    }

    public static int setWindbound(CommandSourceStack source, Collection<? extends Player> targets, boolean value) throws CommandSyntaxException {
        int count = 0;
        Player only = null;
        for (Player player : targets) {
            IPlayerData data = PlayerDataEvents.get(player);
            if (data != null) {
                data.setWindbound(value);
                only = player;
                count++;
            }
        }
        if (count == 0) {
            throw NO_DATA.create();
        }
        Player single = only;
        int total = count;
        if (total == 1) {
            source.sendSuccess(() -> Component.translatable("commands.essentialcraft5.setwindbound.success", single.getDisplayName(), state(value)), true);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.essentialcraft5.setwindbound.success.multiple", total, state(value)), true);
        }
        return count;
    }

    /** 「受到眷顾 / 未受眷顾」 */
    private static Component state(boolean windbound) {
        return Component.translatable(windbound ? "commands.essentialcraft5.windbound.state.true" : "commands.essentialcraft5.windbound.state.false");
    }
}
