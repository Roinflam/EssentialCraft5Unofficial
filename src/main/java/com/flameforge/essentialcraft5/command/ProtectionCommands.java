package com.flameforge.essentialcraft5.command;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.UUID;

/**
 * {@code /ec5 player <targets> protection_check <pos>}：替服主查「这名玩家能不能动这一格」，结论和本模组的物品、机器实际用的是同一套（第四批框架 F4-16，裁定 20：随正式版发布）。
 * 每名目标玩家回显两行：
 * <ol>
 *   <li><b>本人</b>：{@link ECProtection#check}（= 物品里的 {@code canEdit}）——以他的身份对这一格当前的方块发一次破坏事件，没被取消算允许；
 *       另查出生点保护和世界边界；区块没加载时「不确定」。查之前先清掉他的探测缓存（{@link ECProtection#forget}），所以看到的是现在的结论，
 *       代价是他的机器下次要重新探测。</li>
 *   <li><b>他的机器</b>：和 {@link ECProtection#checkAsOwner} 等价的结论——主人在线用主人真身探测（刚查过，命中缓存，不再发事件），
 *       主人不在线时按配置 {@code protection.require_owner_online} 给「不确定（停工）」或「允许」。</li>
 * </ol>
 * <b>不改方块，但会以目标玩家的身份发一次破坏事件</b>：日志插件会记下一条「破坏」（回滚只是把原方块放回原处，无害），
 * 所以执行时服务端日志另写一行「&lt;执行者&gt; 以 &lt;玩家&gt; 的身份查了 &lt;维度&gt; &lt;坐标&gt;」，出了事查得到是谁查的。
 * 维度是执行者所在的维度（{@code execute in} 可以换），坐标要已加载（同原版 {@code /setblock}）。权限 2（整棵 {@code /ec5} 的权限）。
 * <p>
 * 返回值（{@code execute store} 用）：本人一行是「允许」的人数。逻辑写成静态方法，GameTest 和混合端自检（{@code dev/HybridSelfTest}）直接调。
 * 挂载：{@link ECCommands#build} 里直接挂在 {@code player <targets>} 下。
 */
public final class ProtectionCommands {

    /** 坐标参数名 */
    public static final String POS = "pos";

    /** 两行各自的探测预算（ECProtection 按调用方分每 tick 的新探测数），和别的机器、物品互不挤占 */
    private static final Object PLAYER_CALLER = new Object();
    private static final Object MACHINE_CALLER = new Object();

    private ProtectionCommands() {
    }

    /** 一名玩家对一格的两行结论 */
    public record Report(ECProtection.Verdict self, ECProtection.Verdict machine) {
    }

    /** {@code protection_check} 节点，挂在 {@code player <targets>} 下 */
    public static LiteralArgumentBuilder<CommandSourceStack> build(CommandBuildContext context) {
        return Commands.literal("protection_check")
                .then(Commands.argument(POS, BlockPosArgument.blockPos())
                        .executes(ctx -> check(ctx.getSource(), ECCommands.targets(ctx), BlockPosArgument.getLoadedBlockPos(ctx, POS))));
    }

    public static int check(CommandSourceStack source, Collection<ServerPlayer> targets, BlockPos pos) {
        ServerLevel level = source.getLevel();
        String where = level.dimension().location() + " " + pos.toShortString();
        int allowed = 0;
        for (ServerPlayer target : targets) {
            String name = target.getGameProfile().getName();
            EssentialCraft5.LOGGER.info("[权限] {} 以 {} 的身份查了 {}", source.getTextName(), name, where);
            Report report = inspect(level, target, pos);
            Component who = target.getDisplayName();
            source.sendSuccess(() -> Component.translatable("commands.essentialcraft5.protection_check.self",
                    who, where, verdict(report.self())), false);
            source.sendSuccess(() -> Component.translatable("commands.essentialcraft5.protection_check.machine",
                    who, verdict(report.machine())), false);
            if (report.self() == ECProtection.Verdict.ALLOW) {
                allowed++;
            }
        }
        return allowed;
    }

    /** 两行结论（不发聊天、不写日志）；player 必须是真玩家 */
    public static Report inspect(ServerLevel level, ServerPlayer player, BlockPos pos) {
        ECProtection.forget(player.getUUID());
        ECProtection.Verdict self = ECProtection.check(player, level, pos, PLAYER_CALLER);
        return new Report(self, machineVerdict(level, player.getUUID(), pos));
    }

    /**
     * 「这名玩家的机器能不能动这一格」，同 {@link ECProtection#checkAsOwner}（没有机器，所以不查「没有主人」）：
     * 主人在线 → 用主人真身 {@link ECProtection#check}；不在线 → 开着 {@code require_owner_online}（默认）时 UNKNOWN（停工），关了时 ALLOW（不做检查）。
     */
    public static ECProtection.Verdict machineVerdict(ServerLevel level, UUID owner, BlockPos pos) {
        ServerPlayer online = ECProtection.onlinePlayer(level.getServer(), owner);
        if (online != null) {
            return ECProtection.check(online, level, pos, MACHINE_CALLER);
        }
        return ECProtection.Config.requireOwnerOnline() ? ECProtection.Verdict.UNKNOWN : ECProtection.Verdict.ALLOW;
    }

    private static Component verdict(ECProtection.Verdict verdict) {
        return Component.translatable("commands.essentialcraft5.protection_check." + switch (verdict) {
            case ALLOW -> "allow";
            case DENY -> "deny";
            case UNKNOWN -> "unknown";
        });
    }
}
