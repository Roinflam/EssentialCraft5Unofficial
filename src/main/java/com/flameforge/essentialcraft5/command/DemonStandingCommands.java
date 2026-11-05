package com.flameforge.essentialcraft5.command;

import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.demon.DemonStanding;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
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
 * {@code /ec5 player <targets> demon_standing get|set <值 ≥ 0>}：查看、修改契约之誉（第十三批 13a，batch13.md §3.4）。
 * 服主兜底用（老存档迁移没补到、测试）；直接设，不发到档提示、不给经验。由 {@link DemonStanding#init} 挂上（权限 2，根节点统一要求）。
 * 返回值同 {@link WindCommands}：get 只选中一个玩家时返回那个数，多个时返回人数；set 返回改了几个人。
 */
public final class DemonStandingCommands {

    private static final SimpleCommandExceptionType NO_DATA = new SimpleCommandExceptionType(
            Component.translatable("commands.essentialcraft5.no_player_data"));

    private DemonStandingCommands() {
    }

    /** 形状符合 {@link ECCommands.Subcommand} */
    public static LiteralArgumentBuilder<CommandSourceStack> build(CommandBuildContext context) {
        return Commands.literal("demon_standing")
                .then(Commands.literal("get")
                        .executes(ctx -> get(ctx.getSource(), ECCommands.targets(ctx))))
                .then(Commands.literal("set")
                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                .executes(ctx -> set(ctx.getSource(), ECCommands.targets(ctx), IntegerArgumentType.getInteger(ctx, "value")))));
    }

    public static int get(CommandSourceStack source, Collection<? extends Player> targets) throws CommandSyntaxException {
        int count = 0;
        int last = 0;
        for (Player player : targets) {
            IPlayerData data = PlayerDataEvents.get(player);
            if (data != null) {
                int value = data.getDemonStanding();
                source.sendSuccess(() -> Component.translatable("commands.essentialcraft5.demon_standing.get", player.getDisplayName(),
                        DemonStanding.fullLabel(value)), false);
                last = value;
                count++;
            }
        }
        if (count == 0) {
            throw NO_DATA.create();
        }
        return count == 1 ? last : count;
    }

    public static int set(CommandSourceStack source, Collection<? extends Player> targets, int value) throws CommandSyntaxException {
        int clamped = Math.max(0, value);
        int count = 0;
        for (Player player : targets) {
            IPlayerData data = PlayerDataEvents.get(player);
            if (data != null) {
                data.setDemonStanding(clamped);
                source.sendSuccess(() -> Component.translatable("commands.essentialcraft5.demon_standing.set", player.getDisplayName(),
                        clamped), true);
                count++;
            }
        }
        if (count == 0) {
            throw NO_DATA.create();
        }
        return count;
    }
}
