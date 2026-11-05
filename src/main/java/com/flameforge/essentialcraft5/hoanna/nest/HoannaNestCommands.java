package com.flameforge.essentialcraft5.hoanna.nest;

import com.flameforge.essentialcraft5.blockentity.NestCoreBlockEntity;
import com.flameforge.essentialcraft5.util.ECLevels;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * {@code /ec5 hoanna_nest <坐标> info|arm|reset}（权限 2，排查用；第十三批 13d-HF）：
 * <ul>
 *   <li>{@code info}：类型、状态、这一波还剩几只、几人出过力、休眠 / 重试还剩多久；</li>
 *   <li>{@code arm}：结束回充和重试冷却，马上回到等人的状态（这一波还在打时不动，用 {@code reset}）；</li>
 *   <li>{@code reset}：收回这一波、清掉所有计时和出力记录，回到等人的状态。</li>
 * </ul>
 * 只认已加载区块里的核心（不加载区块）。
 */
public final class HoannaNestCommands {

    public static final String KEY = "commands.essentialcraft5.hoanna_nest.";
    private static final String ARG_POS = "pos";

    private HoannaNestCommands() {
    }

    public static ArgumentBuilder<CommandSourceStack, ?> build(CommandBuildContext context) {
        return Commands.literal("hoanna_nest").then(Commands.argument(ARG_POS, BlockPosArgument.blockPos())
                .then(Commands.literal("info").executes(HoannaNestCommands::info))
                .then(Commands.literal("arm").executes(HoannaNestCommands::arm))
                .then(Commands.literal("reset").executes(HoannaNestCommands::reset)));
    }

    private static NestCoreBlockEntity core(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getBlockPos(ctx, ARG_POS);
        ServerLevel level = ctx.getSource().getLevel();
        if (ECLevels.loadedBlockEntity(level, pos) instanceof NestCoreBlockEntity core) {
            return core;
        }
        throw new SimpleCommandExceptionType(Component.translatable(KEY + "not_found", pos.getX(), pos.getY(), pos.getZ())).create();
    }

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        NestCoreBlockEntity core = core(ctx);
        ServerLevel level = ctx.getSource().getLevel();
        long now = HoannaNests.now(level);
        String type = core.typeId() == null ? "-" : core.typeId().toString();
        boolean known = HoannaNestTypes.get(core.typeId()) != null;
        long dormant = Math.max(0L, core.dormantUntil() - now);
        long retry = Math.max(0L, core.retryAt() - now);
        ctx.getSource().sendSuccess(() -> Component.translatable(KEY + "info", type,
                Component.translatable(KEY + "phase." + core.phase().getSerializedName()), core.waveIds().size(), core.credits().size(),
                dormant, retry, core.absentTicks()), false);
        if (!known) {
            ctx.getSource().sendFailure(Component.translatable(KEY + "unknown_type", type));
        }
        return 1;
    }

    private static int arm(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        NestCoreBlockEntity core = core(ctx);
        if (!core.arm(ctx.getSource().getLevel())) {
            ctx.getSource().sendFailure(Component.translatable(KEY + "arm_busy"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(KEY + "armed"), true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        NestCoreBlockEntity core = core(ctx);
        core.resetAll(ctx.getSource().getLevel());
        ctx.getSource().sendSuccess(() -> Component.translatable(KEY + "reset"), true);
        return 1;
    }
}
