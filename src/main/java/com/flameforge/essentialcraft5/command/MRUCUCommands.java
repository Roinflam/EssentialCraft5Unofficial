package com.flameforge.essentialcraft5.command;

import com.flameforge.essentialcraft5.entity.MRUPresenceEntity;
import com.flameforge.essentialcraft5.mru.MRUBalance;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * {@code /ec5 mrucu …}：放一团 MRU 聚集体、改最近一团的 MRU 或平衡值。对应原作 CommandCreateMRUCU、CommandSetMRUInMRUCU、CommandSetBalanceInMRUCU。
 * <ul>
 *   <li>{@code create <pos> <mru> <balance>}：在方块<b>中心</b>放一团（原作 setPositionAndRotation(x, y, z) 放在方块西北下角；
 *       机器泄漏生成的聚集体在方块中心，这里对齐）。MRU 0~20000（原作只限 ≥ 0，存储会自己截到 20000，这里直接报参数错），
 *       平衡值 0~2（同原作）。新聚集体 flag = false、alwaysStay = true（同原作 new EntityMRUPresence 的默认值：MRU 归零也不消失）。
 *       坐标要在已加载的区块里（原版 /summon 同样要求）。</li>
 *   <li>{@code set_mru <pos> <mru>}、{@code set_balance <pos> <balance>}：pos 附近 {@value #FIND_RADIUS} 格内（竖直 ±8，
 *       {@link MRUPresenceEntity#findClosest}）最近的一团；找不到时报错（原作靠空指针进 catch）。</li>
 * </ul>
 * 逻辑写成静态方法，GameTest 直接调（测试环境造不出在线玩家，但 mrucu 子命令本身不涉及玩家）。
 */
public final class MRUCUCommands {

    /** 找聚集体的半径（原作 ECUtils.getClosestMRUCU(world, pos, 16)） */
    public static final int FIND_RADIUS = 16;

    private static final DynamicCommandExceptionType NOT_FOUND = new DynamicCommandExceptionType(
            radius -> Component.translatable("commands.essentialcraft5.mrucu_not_found", radius));

    private MRUCUCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("mrucu")
                .then(Commands.literal("create")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("mru", IntegerArgumentType.integer(0, MRUPresenceEntity.MAX_MRU))
                                        .then(Commands.argument("balance", FloatArgumentType.floatArg(MRUBalance.MIN, MRUBalance.MAX))
                                                .executes(ctx -> {
                                                    create(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
                                                            IntegerArgumentType.getInteger(ctx, "mru"), FloatArgumentType.getFloat(ctx, "balance"));
                                                    return 1;
                                                })))))
                .then(Commands.literal("set_mru")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("mru", IntegerArgumentType.integer(0, MRUPresenceEntity.MAX_MRU))
                                        .executes(ctx -> {
                                            setMRU(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "pos"), IntegerArgumentType.getInteger(ctx, "mru"));
                                            return 1;
                                        }))))
                .then(Commands.literal("set_balance")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("balance", FloatArgumentType.floatArg(MRUBalance.MIN, MRUBalance.MAX))
                                        .executes(ctx -> {
                                            setBalance(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "pos"), FloatArgumentType.getFloat(ctx, "balance"));
                                            return 1;
                                        }))));
    }

    /** 在 pos 方块中心放一团聚集体，返回它 */
    public static MRUPresenceEntity create(CommandSourceStack source, BlockPos pos, int mru, float balance) {
        ServerLevel level = source.getLevel();
        MRUPresenceEntity presence = MRUPresenceEntity.create(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                mru, balance, false, true);
        level.addFreshEntity(presence);
        source.sendSuccess(() -> Component.translatable("commands.essentialcraft5.createmrucu.success",
                pos.getX(), pos.getY(), pos.getZ(), presence.getMRU(), MRUBalance.format(presence.getBalance())), true);
        return presence;
    }

    /** 把 pos 附近最近一团的 MRU 设成 mru（夹到 0~20000），返回那一团 */
    public static MRUPresenceEntity setMRU(CommandSourceStack source, BlockPos pos, int mru) throws CommandSyntaxException {
        MRUPresenceEntity presence = closest(source, pos);
        presence.getStorage().setMRU(mru);
        source.sendSuccess(() -> Component.translatable("commands.essentialcraft5.setmruinmrucu.success", presence.getMRU()), true);
        return presence;
    }

    /** 把 pos 附近最近一团的平衡值设成 balance（夹到 0~2），返回那一团 */
    public static MRUPresenceEntity setBalance(CommandSourceStack source, BlockPos pos, float balance) throws CommandSyntaxException {
        MRUPresenceEntity presence = closest(source, pos);
        presence.getStorage().setBalance(balance);
        source.sendSuccess(() -> Component.translatable("commands.essentialcraft5.setbalanceinmrucu.success",
                MRUBalance.format(presence.getBalance())), true);
        return presence;
    }

    private static MRUPresenceEntity closest(CommandSourceStack source, BlockPos pos) throws CommandSyntaxException {
        MRUPresenceEntity presence = MRUPresenceEntity.findClosest(source.getLevel(), pos, FIND_RADIUS);
        if (presence == null) {
            throw NOT_FOUND.create(FIND_RADIUS);
        }
        return presence;
    }
}
