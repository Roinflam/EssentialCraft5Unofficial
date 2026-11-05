package com.flameforge.essentialcraft5.command;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@code /ec5} 指令的根（Forge 总线 RegisterCommandsEvent 注册，权限 2）。原作是 5 条各自独立的指令（权限 3 或默认 4），
 * 按 batch2 D16 统一收进 {@code /ec5}、权限 2、不留旧名别名：
 * <pre>
 * /ec5 mrucu create &lt;pos&gt; &lt;mru&gt; &lt;balance&gt;        原作 /createmrucu（坐标取方块中心，原作放在方块角上）
 * /ec5 mrucu set_mru &lt;pos&gt; &lt;mru&gt;                  原作 /setmruinmrucu（16 格内最近的一团）
 * /ec5 mrucu set_balance &lt;pos&gt; &lt;balance&gt;          原作 /setbalanceinmrucu
 * /ec5 player &lt;targets&gt; ubmru get|set|add &lt;值&gt;       原作 /setubmru（只有 set）
 * /ec5 player &lt;targets&gt; matrix_damage get|set &lt;值&gt;
 * /ec5 player &lt;targets&gt; matrix_type get|set &lt;neutral|chaos|frozen|magic|shade&gt;
 * /ec5 player &lt;targets&gt; protection_check &lt;pos&gt;         替服主查这名玩家（和他的机器）能不能动这一格，见 {@link ProtectionCommands}
 * </pre>
 * 原作的 /corruptioneffect 等已挂在 {@code player <targets> corruption list|add|remove|clear} 下（command/CorruptionCommands）；/hoannaevent、/setwindpoints、/setwindbound 等对应批次再加。
 * 具体行为见 {@link MRUCUCommands}、{@link PlayerCommands}。
 * <p>
 * <b>别的模块挂子命令</b>：在自己注册类的 {@code init()} 里调一次（mod 构造期；之后每次服务器启动 / {@code /reload} 重建指令树时都会用上）：
 * <pre>{@code
 * // 挂在 /ec5 下：/ec5 hoanna_event stop
 * ECCommands.addSubcommand(context -> Commands.literal("hoanna_event")
 *         .then(Commands.literal("stop").executes(ctx -> HoannaHazardsCommands.stop(ctx.getSource()))));
 * // 挂在 /ec5 player <targets> 下：/ec5 player @s corruption clear
 * ECCommands.addPlayerSubcommand(context -> Commands.literal("corruption")
 *         .then(Commands.literal("clear").executes(ctx -> clear(ctx.getSource(), ECCommands.targets(ctx)))));
 * }</pre>
 * 同名字面量会被 Brigadier 合并，所以也能往已有的节点下加（例如再挂一个 {@code Commands.literal("mrucu").then(Commands.literal("list")…)}）。
 * 权限已经在根上判断过（2 级），子命令要更高权限时自己再加 {@code .requires(...)}。
 * 反馈文字的键放 {@code commands.essentialcraft5.*}，写进各自模块的 tools/lang/keys 文件。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class ECCommands {

    /** 根指令名 */
    public static final String ROOT = "ec5";
    /** 整棵指令树要的权限等级（原版 2 = 命令方块、/give 那一级；原作是 3） */
    public static final int PERMISSION_LEVEL = 2;
    /** {@code player <targets>} 的参数名，子命令里用 {@link #targets} 取 */
    public static final String TARGETS = "targets";

    /** 往指令树上挂一个节点：拿到 CommandBuildContext（物品 / 方块参数要用），返回一个 literal 或 argument 节点 */
    @FunctionalInterface
    public interface Subcommand {
        ArgumentBuilder<CommandSourceStack, ?> build(CommandBuildContext context);
    }

    // mod 构造期各模块并行调 init()，所以用线程安全的列表
    private static final List<Subcommand> ROOT_SUBCOMMANDS = new CopyOnWriteArrayList<>();
    private static final List<Subcommand> PLAYER_SUBCOMMANDS = new CopyOnWriteArrayList<>();

    private ECCommands() {
    }

    /** 挂在 {@code /ec5} 下。只在模块 init() 里调一次 */
    public static void addSubcommand(Subcommand subcommand) {
        ROOT_SUBCOMMANDS.add(subcommand);
    }

    /** 挂在 {@code /ec5 player <targets>} 下，执行时用 {@link #targets} 取目标玩家。只在模块 init() 里调一次 */
    public static void addPlayerSubcommand(Subcommand subcommand) {
        PLAYER_SUBCOMMANDS.add(subcommand);
    }

    /** {@code player <targets>} 选中的玩家（至少一个，选不到时 Brigadier 报原版的「未找到玩家」） */
    public static Collection<ServerPlayer> targets(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return EntityArgument.getPlayers(context, TARGETS);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(build(event.getBuildContext()));
    }

    /** 整棵 {@code /ec5} 树（内置的在前，别的模块挂的在后） */
    public static LiteralArgumentBuilder<CommandSourceStack> build(CommandBuildContext context) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(ROOT)
                .requires(source -> source.hasPermission(PERMISSION_LEVEL));
        root.then(MRUCUCommands.build());

        RequiredArgumentBuilder<CommandSourceStack, EntitySelector> targets = Commands.argument(TARGETS, EntityArgument.players());
        PlayerCommands.addTo(targets);
        targets.then(ProtectionCommands.build(context));
        for (Subcommand subcommand : PLAYER_SUBCOMMANDS) {
            targets.then(subcommand.build(context));
        }
        root.then(Commands.literal("player").then(targets));

        for (Subcommand subcommand : ROOT_SUBCOMMANDS) {
            root.then(subcommand.build(context));
        }
        return root;
    }
}
