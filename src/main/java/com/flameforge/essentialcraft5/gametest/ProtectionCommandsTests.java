package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.command.ECCommands;
import com.flameforge.essentialcraft5.command.ProtectionCommands;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * {@code /ec5 player <targets> protection_check <pos>}（{@link ProtectionCommands}，第四批框架 F4-16）：两行结论（本人、他的机器）和模拟领地一致；
 * 主人不在线时机器那行是「不确定」；两行只以本人身份发一次破坏事件（机器那行命中刚写的缓存）；指令返回「本人允许」的人数。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class ProtectionCommandsTests {

    private static final String EMPTY = "empty";

    private ProtectionCommandsTests() {
    }

    @GameTest(template = EMPTY)
    public static void protectionCheckMatchesClaims(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID id = UUID.randomUUID();
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, id, "ec5-check");
        BlockPos free = new BlockPos(1, 1, 1);
        BlockPos claimed = new BlockPos(3, 1, 1);
        helper.setBlock(free, Blocks.STONE);
        helper.setBlock(claimed, Blocks.STONE);
        try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper).deny(claimed)) {
            ProtectionCommands.Report offline = ProtectionCommands.inspect(level, player, helper.absolutePos(free));
            helper.assertTrue(offline.self() == ECProtection.Verdict.ALLOW && offline.machine() == ECProtection.Verdict.UNKNOWN,
                    "不在线：本人照查（允许），机器停工（不确定），实际 " + offline);

            ECProtection.setOnlineOverride(player);
            ProtectionCommands.Report allowed = ProtectionCommands.inspect(level, player, helper.absolutePos(free));
            helper.assertTrue(allowed.self() == ECProtection.Verdict.ALLOW && allowed.machine() == ECProtection.Verdict.ALLOW,
                    "在线、没人拦的格子应两行都允许，实际 " + allowed);
            ProtectionCommands.Report denied = ProtectionCommands.inspect(level, player, helper.absolutePos(claimed));
            helper.assertTrue(denied.self() == ECProtection.Verdict.DENY && denied.machine() == ECProtection.Verdict.DENY,
                    "领地里的格子应两行都拒绝，实际 " + denied);
            helper.assertTrue(claims.breaksBy(claimed, id) == 1, "两行只应以本人身份发一次破坏事件，实际 " + claims.breaksBy(claimed, id));
            helper.assertBlockPresent(Blocks.STONE, claimed);

            CommandSourceStack source = level.getServer().createCommandSourceStack()
                    .withLevel(level)
                    .withPosition(Vec3.atCenterOf(helper.absolutePos(free)))
                    .withPermission(ECCommands.PERMISSION_LEVEL)
                    .withSuppressedOutput();
            helper.assertTrue(ProtectionCommands.check(source, List.of(player), helper.absolutePos(free)) == 1, "能动的格子返回 1");
            helper.assertTrue(ProtectionCommands.check(source, List.of(player), helper.absolutePos(claimed)) == 0, "领地里的格子返回 0");
            helper.assertBlockPresent(Blocks.STONE, free);
            helper.assertBlockPresent(Blocks.STONE, claimed);
        } finally {
            ECProtection.clearOnlineOverride(id);
        }
        helper.succeed();
    }
}
