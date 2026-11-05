package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.entity.boss.MatrixShadeSummon;
import com.flameforge.essentialcraft5.registry.ECMatrixShade;
import com.flameforge.essentialcraft5.registry.ECPentacle;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;

/**
 * 矩阵导流符（第十三批 13c-M4，batch13.md §5.3.2）：拿着它右键灵魂五芒星的中心块，把自己身上的矩阵损伤转进一只矩阵之影（{@link MatrixShadeSummon}）。
 * <p>
 * 写在 {@link #onItemUseFirst}：五芒星自己的右键会开界面，物品的 {@code useOn} 轮不到；只拦点在五芒星上的右键，点别的方块照常。
 * 客户端直接返回 SUCCESS（摆手、不开界面），判定全在服务端；召不了时导流符不消耗、动作栏说原因。
 */
public class MatrixConduitItem extends Item {

    public MatrixConduitItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        if (!level.getBlockState(context.getClickedPos()).is(ECPentacle.PLAYER_PENTACLE.get())) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel server) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }
        MatrixShadeSummon.Outcome outcome = MatrixShadeSummon.summon(player, server, context.getClickedPos(), stack);
        if (outcome.result() != MatrixShadeSummon.Result.NOT_REAL_PLAYER) {
            player.displayClientMessage(outcome.message(), !outcome.ok());
        }
        return outcome.ok() ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ECMatrixShade.Config.Summon summon = ECMatrixShade.Config.values().summon();
        tooltip.add(Component.translatable("tooltip.essentialcraft5.matrix_conduit.usage").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.essentialcraft5.matrix_conduit.need", summon.minDamage()).withStyle(ChatFormatting.GRAY));
        tooltip.add(transferLine(summon).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.essentialcraft5.matrix_conduit.refund").withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * 「最多转多少、柱子每点付多少有效 ESPE」那一行：单价照配置 {@code espe_per_damage} 写（去掉多余的 0，同矩阵调律器界面），
     * 单价是 0 时换成不用付 ESPE 的那句
     */
    public static MutableComponent transferLine(ECMatrixShade.Config.Summon summon) {
        double price = summon.espePerDamage();
        if (!(price > 0D)) {
            return Component.translatable("tooltip.essentialcraft5.matrix_conduit.transfer_free", summon.maxAbsorbed());
        }
        String espe = BigDecimal.valueOf(price).stripTrailingZeros().toPlainString();
        return Component.translatable("tooltip.essentialcraft5.matrix_conduit.transfer", summon.maxAbsorbed(), espe);
    }
}
