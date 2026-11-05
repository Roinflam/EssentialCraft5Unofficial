package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.MRUBlockEntity;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * 没有界面的机器（洗牌器、繁殖器、剪毛器……）空手右键在动作栏显示主人状态（batch4 F4-8）：
 * 「主人：xx」「主人 xx 不在线，暂停工作」「没有主人（不是玩家放下的），不会工作」。
 * 这些机器看不到界面里的主人状态行（{@code gui.essentialcraft5.owner.*}），玩家不知道它为什么不动。
 * <p>
 * 只在<b>主手空着、没有潜行</b>、用主手右键时接管（返回成功、挥手）；潜行或主手拿着东西时返回 PASS，照旧能对着机器放方块、用物品
 * （主手空、副手拿方块时同原版箱子：先显示状态；要放副手的方块就潜行）。
 * 主人名字只在服务端有（不同步给客户端），所以文字在服务端拼好发动作栏；客户端只按同样的条件返回成功，两端一致。
 * {@link PulseMachineBlock}、{@link FacingMachineBlock} 在 {@code opensMenu()} 为假时自动用它；别的机器方块可以在 {@code use} 里直接调 {@link #use}。
 */
public final class MachineOwnerStatus {

    private MachineOwnerStatus() {
    }

    /** 方块 {@code use} 里调：条件不满足（见类注释）或这格不是机器时返回 PASS */
    public static InteractionResult use(Level level, BlockPos pos, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos) instanceof MRUBlockEntity machine)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            player.displayClientMessage(message(machine), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** 服务端：这台机器的主人状态文字（键在 tools/lang/keys/framework4_c.json） */
    public static Component message(MRUBlockEntity machine) {
        String name = ECProtection.ownerName(machine);
        if (name.isEmpty()) {
            name = "?";
        }
        return switch (ECProtection.ownerState(machine)) {
            case NONE -> Component.translatable("message.essentialcraft5.machine_owner.none").withStyle(ChatFormatting.RED);
            case OFFLINE -> ECProtection.Config.requireOwnerOnline()
                    ? Component.translatable("message.essentialcraft5.machine_owner.offline", name).withStyle(ChatFormatting.GOLD)
                    : Component.translatable("message.essentialcraft5.machine_owner.offline_working", name);
            case ONLINE -> Component.translatable("message.essentialcraft5.machine_owner", name);
        };
    }
}
