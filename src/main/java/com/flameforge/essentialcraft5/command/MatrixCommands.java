package com.flameforge.essentialcraft5.command;

import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.matrix.MatrixCodex;
import com.flameforge.essentialcraft5.matrix.MatrixPool;
import com.flameforge.essentialcraft5.matrix.MatrixRank;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collection;

/**
 * {@code /ec5 player <targets> matrix …}（第十三批 13a，batch13.md §3.1.5；权限同整棵 /ec5，2 级）：
 * <pre>
 * matrix rank get                       现在几阶
 * matrix rank set &lt;0~9&gt;                 设阶（顺带补上手册里一~n 阶的步骤勾；不发标题、不扣东西）
 * matrix codex list                     图谱各组种类数和整份名单
 * matrix codex clear                    清空两份图谱
 * matrix codex add &lt;实体&gt; [hoanna]      记上一种（带 hoanna 同时记进海纳尔那份）；不看是不是敌对
 * matrix pool get                       矩阵容量
 * matrix pool set &lt;值&gt;                  设容量（夹在 0 ~ 这一阶的上限）
 * </pre>
 * 改图谱后发整份图谱包；改阶、改容量走玩家数据的 setter（标记同步）。服主卡住的玩家用 {@code rank set} 兜底（batch13.md §2.6 末条）。
 * 返回值同 {@link PlayerCommands}：get 只选中一个玩家时返回那个数，set / add / clear 返回改了几个人。
 * 逻辑写成静态方法，GameTest 直接调。由 {@code matrix/MatrixSystems.init()} 挂上。
 */
public final class MatrixCommands {

    private static final SimpleCommandExceptionType NO_DATA = new SimpleCommandExceptionType(
            Component.translatable("commands.essentialcraft5.no_player_data"));
    private static final DynamicCommandExceptionType UNKNOWN_ENTITY = new DynamicCommandExceptionType(
            id -> Component.translatable("commands.essentialcraft5.matrix.codex.unknown", String.valueOf(id)));

    private MatrixCommands() {
    }

    /** 挂在 {@code /ec5 player <targets>} 下（{@link ECCommands.Subcommand}） */
    public static ArgumentBuilder<CommandSourceStack, ?> build(CommandBuildContext context) {
        return Commands.literal("matrix")
                .then(Commands.literal("rank")
                        .then(Commands.literal("get")
                                .executes(ctx -> getRank(ctx.getSource(), ECCommands.targets(ctx))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("rank", IntegerArgumentType.integer(0, MatrixRank.MAX))
                                        .executes(ctx -> setRank(ctx.getSource(), ECCommands.targets(ctx), IntegerArgumentType.getInteger(ctx, "rank"))))))
                .then(Commands.literal("codex")
                        .then(Commands.literal("list")
                                .executes(ctx -> listCodex(ctx.getSource(), ECCommands.targets(ctx))))
                        .then(Commands.literal("clear")
                                .executes(ctx -> clearCodex(ctx.getSource(), ECCommands.targets(ctx))))
                        .then(Commands.literal("add")
                                .then(Commands.argument("entity", ResourceLocationArgument.id())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(ForgeRegistries.ENTITY_TYPES.getKeys(), builder))
                                        .executes(ctx -> addCodex(ctx.getSource(), ECCommands.targets(ctx), ResourceLocationArgument.getId(ctx, "entity"), false))
                                        .then(Commands.literal("hoanna")
                                                .executes(ctx -> addCodex(ctx.getSource(), ECCommands.targets(ctx), ResourceLocationArgument.getId(ctx, "entity"), true))))))
                .then(Commands.literal("pool")
                        .then(Commands.literal("get")
                                .executes(ctx -> getPool(ctx.getSource(), ECCommands.targets(ctx))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                        .executes(ctx -> setPool(ctx.getSource(), ECCommands.targets(ctx), IntegerArgumentType.getInteger(ctx, "value"))))));
    }

    // ---------------------------------------------------------------- 阶

    public static int getRank(CommandSourceStack source, Collection<? extends Player> targets) throws CommandSyntaxException {
        int last = 0;
        for (Player player : targets) {
            IPlayerData data = data(player);
            last = data.getMatrixRank();
            Component line = Component.translatable("commands.essentialcraft5.matrix.rank.get", player.getDisplayName(), MatrixRank.rankName(last));
            source.sendSuccess(() -> line, false);
        }
        return targets.size() == 1 ? last : targets.size();
    }

    public static int setRank(CommandSourceStack source, Collection<? extends Player> targets, int rank) throws CommandSyntaxException {
        int count = 0;
        for (Player player : targets) {
            data(player);
            MatrixRank.set(player, rank);
            if (player instanceof ServerPlayer serverPlayer) {
                MatrixRank.grantRankSteps(serverPlayer, rank);
            }
            count++;
        }
        int changed = count;
        source.sendSuccess(() -> Component.translatable("commands.essentialcraft5.matrix.rank.set", changed, MatrixRank.rankName(rank)), true);
        return changed;
    }

    // ---------------------------------------------------------------- 图谱

    public static int listCodex(CommandSourceStack source, Collection<? extends Player> targets) throws CommandSyntaxException {
        int last = 0;
        for (Player player : targets) {
            IPlayerData data = data(player);
            MatrixCodex.Counts counts = MatrixCodex.counts(data);
            last = counts.get(MatrixCodex.Group.TOTAL);
            Component head = Component.translatable("commands.essentialcraft5.matrix.codex.list", player.getDisplayName(), last,
                    MatrixCodex.summary(counts));
            source.sendSuccess(() -> head, false);
            if (!data.getMatrixCodex().isEmpty()) {
                String names = String.join(", ", MatrixCodex.describe(data.getMatrixCodex()));
                source.sendSuccess(() -> Component.literal(names), false);
            }
            if (!data.getMatrixCodexHoanna().isEmpty()) {
                String hoanna = String.join(", ", MatrixCodex.describe(data.getMatrixCodexHoanna()));
                source.sendSuccess(() -> Component.translatable("commands.essentialcraft5.matrix.codex.list_hoanna", hoanna), false);
            }
        }
        return targets.size() == 1 ? last : targets.size();
    }

    public static int clearCodex(CommandSourceStack source, Collection<? extends Player> targets) throws CommandSyntaxException {
        int removed = 0;
        for (Player player : targets) {
            data(player);
            removed += MatrixCodex.clear(player);
        }
        int total = removed;
        source.sendSuccess(() -> Component.translatable("commands.essentialcraft5.matrix.codex.clear", targets.size(), total), true);
        return targets.size();
    }

    public static int addCodex(CommandSourceStack source, Collection<? extends Player> targets, ResourceLocation id, boolean hoanna)
            throws CommandSyntaxException {
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(id)) {
            throw UNKNOWN_ENTITY.create(id);
        }
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
        int added = 0;
        for (Player player : targets) {
            IPlayerData data = data(player);
            int before = data.getMatrixCodex().size() + data.getMatrixCodexHoanna().size();
            MatrixCodex.add(player, type, hoanna);
            if (data.getMatrixCodex().size() + data.getMatrixCodexHoanna().size() > before) {
                added++;
            }
        }
        int count = added;
        source.sendSuccess(() -> Component.translatable("commands.essentialcraft5.matrix.codex.add", count, type.getDescription()), true);
        return count;
    }

    // ---------------------------------------------------------------- 容量

    public static int getPool(CommandSourceStack source, Collection<? extends Player> targets) throws CommandSyntaxException {
        int last = 0;
        for (Player player : targets) {
            IPlayerData data = data(player);
            last = data.getMatrixPool();
            int pool = last;
            source.sendSuccess(() -> Component.translatable("commands.essentialcraft5.matrix.pool.get", player.getDisplayName(), pool,
                    MatrixPool.capacity(data)), false);
        }
        return targets.size() == 1 ? last : targets.size();
    }

    public static int setPool(CommandSourceStack source, Collection<? extends Player> targets, int value) throws CommandSyntaxException {
        for (Player player : targets) {
            IPlayerData data = data(player);
            data.setMatrixPool(Math.min(value, MatrixPool.capacity(data)), true);
        }
        source.sendSuccess(() -> Component.translatable("commands.essentialcraft5.matrix.pool.set", targets.size(), value), true);
        return targets.size();
    }

    private static IPlayerData data(Player player) throws CommandSyntaxException {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            throw NO_DATA.create();
        }
        return data;
    }
}
