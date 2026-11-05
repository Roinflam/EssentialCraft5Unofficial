package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.entity.boss.CalamityHeartSummon;
import com.flameforge.essentialcraft5.matrix.MatrixRank;
import com.flameforge.essentialcraft5.registry.ECCalamityHeart;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
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

import java.util.List;

/**
 * 天灾信标（第十三批 13e，batch13.md §5.3.5）：手持右键海纳尔地面召唤天灾之心（八阶门、天灾窗口、露天、平整、五处权限、128 格互斥、非和平，
 * 条件逐条见 {@link CalamityHeartSummon}）。
 * 信标不是一次性的：打倒以后变「空」（NBT {@value #TAG_SPENT}）回到召唤者背包，再召吃 {@code cores_per_resummon}（2）个暗影核心；没打倒原样退回。
 * 新合成的没有这个标签，是「满的」，召唤不另扣。配方在 13e-C5。注册在 {@code registry/ECCalamityHeart}。
 */
public class CalamityBeaconItem extends Item {

    public static final String TAG_SPENT = "spent";

    public CalamityBeaconItem(Properties properties) {
        super(properties);
    }

    /** 打倒过一次（空的）：再召要扣暗影核心 */
    public static boolean isSpent(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(TAG_SPENT);
    }

    public static void setSpent(ItemStack stack, boolean spent) {
        if (spent) {
            stack.getOrCreateTag().putBoolean(TAG_SPENT, true);
        } else if (stack.getTag() != null) {
            stack.getTag().remove(TAG_SPENT);
            if (stack.getTag().isEmpty()) {
                stack.setTag(null);
            }
        }
    }

    /** 点在任何方块上都由召唤代码判（不在海纳尔、条件不够时提示原因、不消耗） */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel server) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }
        CalamityHeartSummon.Outcome outcome = CalamityHeartSummon.summon(player, server, context.getClickedPos(), stack);
        if (outcome.result() != CalamityHeartSummon.Result.NOT_REAL_PLAYER) {
            player.displayClientMessage(outcome.message(player), !outcome.ok());
        }
        return outcome.ok() ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return !isSpent(stack);
    }

    /** 提示里「需要：……」那一行：阶数按门的配置写（矩阵阶关着或门填 0 时不写阶），天灾按 {@link CalamityHeartSummon#requiresCalamity} */
    public static MutableComponent needLine(boolean needCalamity, int gate) {
        String key = "tooltip.essentialcraft5.calamity_beacon." + (needCalamity ? "need" : "need_any");
        return gate > 0 ? Component.translatable(key, MatrixRank.rankName(gate)) : Component.translatable(key + "_unranked");
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ECCalamityHeart.Config.Summon summon = ECCalamityHeart.Config.values().summon();
        tooltip.add(Component.translatable("tooltip.essentialcraft5.calamity_beacon.usage").withStyle(ChatFormatting.GRAY));
        boolean needCalamity = CalamityHeartSummon.requiresCalamity(summon);
        tooltip.add(needLine(needCalamity, MatrixRank.enabled() ? MatrixRank.Gate.CALAMITY_BEACON.required() : 0).withStyle(ChatFormatting.GRAY));
        int extra = needCalamity ? 0 : summon.coresExtraWithoutCalamity();
        if (isSpent(stack)) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.calamity_beacon.spent", summon.coresPerResummon() + extra)
                    .withStyle(ChatFormatting.GOLD));
        } else if (extra > 0) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.calamity_beacon.full_extra", extra).withStyle(ChatFormatting.LIGHT_PURPLE));
        } else {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.calamity_beacon.full").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        tooltip.add(Component.translatable("tooltip.essentialcraft5.calamity_beacon.refund").withStyle(ChatFormatting.DARK_GRAY));
    }
}
