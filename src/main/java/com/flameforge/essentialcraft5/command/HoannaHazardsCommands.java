package com.flameforge.essentialcraft5.command;

import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.hoanna.Radiation;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEventData;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvents;
import com.flameforge.essentialcraft5.hoanna.event.LeyStorm;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * 海纳尔天灾与辐射的指令（第六批 6a M4，batch6a.md §5.4；由 {@code ECHoannaHazards.init()} 挂到 {@code /ec5} 下，权限 2 在根上判断）：
 * <pre>
 * /ec5 hoanna_event start &lt;sun_array|fumes|darkness|earthquake&gt; [ticks]   原作 /hoannaevent &lt;id&gt;（不写 ticks 用这种天灾的默认时长）
 * /ec5 hoanna_event stop                                                     原作 /hoannaevent stop（没有天灾时不报错，原作在这里空指针）
 * /ec5 hoanna_event query
 * /ec5 player &lt;targets&gt; radiation get|set &lt;0~1000000&gt;|add &lt;±值&gt;|clear
 * </pre>
 * 作用在海纳尔世界上（{@code ECHoanna.level(server)}；服主用数据包删了这个维度时报错）。管理员开的天灾不看 {@code <事件>_enabled}。
 * <b>日阵只在白天</b>（6a 审查 M4-1）：海纳尔已经天黑（一天的第 12000 刻起）时 {@code start sun_array} 报错不开；白天开的时长截到当天黄昏
 * （ticks 写得再长也一样，截短时反馈里注明「到黄昏为止」），天黑就散。
 * 改辐射后立刻按新剂量刷新效果（不用等下一次结算）。逻辑写成静态方法，GameTest 直接调。返回值（{@code execute store} 用）：
 * start 1、stop 停掉了 1 否则 0、query 当前事件编号；radiation get 选中一人时返回剂量，其余返回人数。
 */
public final class HoannaHazardsCommands {

    private static final SimpleCommandExceptionType NO_LEVEL = new SimpleCommandExceptionType(
            Component.translatable("commands.essentialcraft5.hoanna_event.no_level"));
    private static final DynamicCommandExceptionType UNKNOWN_EVENT = new DynamicCommandExceptionType(
            name -> Component.translatable("commands.essentialcraft5.hoanna_event.unknown", name));
    private static final SimpleCommandExceptionType NO_DATA = new SimpleCommandExceptionType(
            Component.translatable("commands.essentialcraft5.no_player_data"));
    private static final SimpleCommandExceptionType SUN_ARRAY_NIGHT = new SimpleCommandExceptionType(
            Component.translatable("commands.essentialcraft5.hoanna_event.sun_array_night"));

    /** 指令里能写的最长时长（tick，约 14 个游戏日） */
    public static final int MAX_TICKS = 24_000 * 14;

    private HoannaHazardsCommands() {
    }

    // ------------------------------------------------------------------ /ec5 hoanna_event

    /** 形状符合 {@link ECCommands.Subcommand}（挂在 /ec5 下） */
    public static LiteralArgumentBuilder<CommandSourceStack> buildEvent(CommandBuildContext context) {
        return Commands.literal("hoanna_event")
                .then(Commands.literal("start")
                        .then(Commands.argument("event", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Stream.concat(
                                        Arrays.stream(HoannaEvent.rollable()), Stream.of(HoannaEvent.LEY_STORM)).map(HoannaEvent::key), builder))
                                .executes(ctx -> start(ctx.getSource(), StringArgumentType.getString(ctx, "event"), 0))
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(1, MAX_TICKS))
                                        .executes(ctx -> start(ctx.getSource(), StringArgumentType.getString(ctx, "event"),
                                                IntegerArgumentType.getInteger(ctx, "ticks"))))))
                .then(Commands.literal("stop").executes(ctx -> stop(ctx.getSource())))
                .then(Commands.literal("query").executes(ctx -> query(ctx.getSource())));
    }

    private static ServerLevel hoanna(CommandSourceStack source) throws CommandSyntaxException {
        ServerLevel level = ECHoanna.level(source.getServer());
        if (level == null) {
            throw NO_LEVEL.create();
        }
        return level;
    }

    /** @param ticks ≤ 0 = 这种天灾的默认时长 */
    public static int start(CommandSourceStack source, String name, int ticks) throws CommandSyntaxException {
        HoannaEvent event = HoannaEvent.byName(name.toLowerCase(Locale.ROOT));
        if (event == null || event.isNone()) {
            throw UNKNOWN_EVENT.create(name);
        }
        ServerLevel level = hoanna(source);
        int daylight = HoannaEvents.daylightLeft(level.getDayTime());
        int untilDawn = LeyStorm.ticksToDawn(level.getDayTime());
        if (!HoannaEvents.start(level, event, ticks)) {
            throw SUN_ARRAY_NIGHT.create();   // 只有夜里开日阵会不开始（NONE 上面已经报过未知）
        }
        int total = HoannaEventData.get(level).total();
        int asked = ticks > 0 ? ticks : event.baseDuration();
        boolean cut = event == HoannaEvent.SUN_ARRAY && asked > daylight;
        boolean cutAtDawn = event == HoannaEvent.LEY_STORM && asked > untilDawn;
        String key = cut ? "commands.essentialcraft5.hoanna_event.started.until_dusk"
                : cutAtDawn ? "commands.essentialcraft5.hoanna_event.started.until_dawn" : "commands.essentialcraft5.hoanna_event.started";
        source.sendSuccess(() -> Component.translatable(key, Component.translatable(event.nameKey()), formatTicks(total)), true);
        return 1;
    }

    public static int stop(CommandSourceStack source) throws CommandSyntaxException {
        HoannaEvent stopped = HoannaEvents.stop(hoanna(source));
        if (stopped.isNone()) {
            source.sendSuccess(() -> Component.translatable("commands.essentialcraft5.hoanna_event.none"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.essentialcraft5.hoanna_event.stopped",
                Component.translatable(stopped.nameKey())), true);
        return 1;
    }

    public static int query(CommandSourceStack source) throws CommandSyntaxException {
        HoannaEventData data = HoannaEventData.get(hoanna(source));
        if (!data.active()) {
            source.sendSuccess(() -> Component.translatable("commands.essentialcraft5.hoanna_event.none"), false);
            return 0;
        }
        HoannaEvent event = data.event();
        int remaining = data.remaining();
        int total = data.total();
        source.sendSuccess(() -> Component.translatable("commands.essentialcraft5.hoanna_event.query",
                Component.translatable(event.nameKey()), formatTicks(remaining), formatTicks(total)), false);
        return event.id();
    }

    /** tick → 「m:ss」（超过一小时「h:mm:ss」） */
    public static String formatTicks(int ticks) {
        int seconds = Math.max(0, ticks) / 20;
        int hours = seconds / 3600;
        int minutes = seconds / 60 % 60;
        int secs = seconds % 60;
        return hours > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs)
                : String.format(Locale.ROOT, "%d:%02d", minutes, secs);
    }

    // ------------------------------------------------------------------ /ec5 player <targets> radiation

    /** 形状符合 {@link ECCommands.Subcommand}（挂在 /ec5 player &lt;targets&gt; 下） */
    public static LiteralArgumentBuilder<CommandSourceStack> buildRadiation(CommandBuildContext context) {
        return Commands.literal("radiation")
                .then(Commands.literal("get")
                        .executes(ctx -> getRadiation(ctx.getSource(), ECCommands.targets(ctx))))
                .then(Commands.literal("set")
                        .then(Commands.argument("value", IntegerArgumentType.integer(0, Radiation.MAX))
                                .executes(ctx -> setRadiation(ctx.getSource(), ECCommands.targets(ctx), IntegerArgumentType.getInteger(ctx, "value")))))
                .then(Commands.literal("add")
                        .then(Commands.argument("value", IntegerArgumentType.integer(-Radiation.MAX, Radiation.MAX))
                                .executes(ctx -> addRadiation(ctx.getSource(), ECCommands.targets(ctx), IntegerArgumentType.getInteger(ctx, "value")))))
                .then(Commands.literal("clear")
                        .executes(ctx -> setRadiation(ctx.getSource(), ECCommands.targets(ctx), 0)));
    }

    public static int getRadiation(CommandSourceStack source, Collection<? extends Player> targets) throws CommandSyntaxException {
        int count = 0;
        int last = 0;
        for (Player player : targets) {
            IPlayerData data = PlayerDataEvents.get(player);
            if (data != null) {
                int dose = data.getRadiation();
                source.sendSuccess(() -> Component.translatable("commands.essentialcraft5.radiation.get", player.getDisplayName(), dose), false);
                last = dose;
                count++;
            }
        }
        if (count == 0) {
            throw NO_DATA.create();
        }
        return count == 1 ? last : count;
    }

    public static int setRadiation(CommandSourceStack source, Collection<? extends Player> targets, int value) throws CommandSyntaxException {
        int count = 0;
        Player only = null;
        int result = 0;
        for (Player player : targets) {
            if (PlayerDataEvents.get(player) != null) {
                result = Radiation.set(player, value);
                Radiation.refresh(player);
                only = player;
                count++;
            }
        }
        if (count == 0) {
            throw NO_DATA.create();
        }
        Player single = only;
        int total = count;
        int shown = result;
        if (total == 1) {
            source.sendSuccess(() -> Component.translatable("commands.essentialcraft5.radiation.set", single.getDisplayName(), shown), true);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.essentialcraft5.radiation.set.multiple", total, shown), true);
        }
        return count;
    }

    public static int addRadiation(CommandSourceStack source, Collection<? extends Player> targets, int value) throws CommandSyntaxException {
        int count = 0;
        Player only = null;
        int now = 0;
        for (Player player : targets) {
            if (PlayerDataEvents.get(player) != null) {
                now = Radiation.add(player, value);
                Radiation.refresh(player);
                only = player;
                count++;
            }
        }
        if (count == 0) {
            throw NO_DATA.create();
        }
        Player single = only;
        int total = count;
        int shown = now;
        if (total == 1) {
            source.sendSuccess(() -> Component.translatable("commands.essentialcraft5.radiation.add", value, single.getDisplayName(), shown), true);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.essentialcraft5.radiation.add.multiple", value, total), true);
        }
        return count;
    }
}
