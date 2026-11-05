package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IMRUHandler;
import com.flameforge.essentialcraft5.mru.MRUBalance;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * 平衡值设定器（创造模式工具）。对应原作 ItemBalanceSetter。
 * <p>
 * 右键（不潜行）在模式 0 / 1 / 2 之间切换；潜行右键一个有 MRU 的方块实体，把它的平衡值设成当前模式的值
 * （0 冰冻、1 纯净、2 混沌）。模式存在物品 NBT 的 Mode 里，没有就是 0（原作每刻在物品栏里补一个 Mode=0 的 NBT，这里不写）。
 * <p>
 * 移植改动：
 * <ul>
 *   <li>设平衡值只在创造模式或权限 2（管理员）下生效，否则提示「只有创造模式或管理员才能使用」（原作谁拿到都能用；它没有配方，
 *       但生存服里可能经别的途径流出去，能随手把机器平衡值改成任意档）。切换模式不限人。</li>
 *   <li>聊天里写死的「Set Mode To N」改成翻译键，发在动作栏；设好平衡值也提示一句（原作不提示）。</li>
 *   <li>切换模式只在服务端改 NBT，客户端随物品栏同步（原作两端各改一次）。</li>
 * </ul>
 * 注意：日光吸收器、月亮井之类平衡值由配置固定的机器每刻都会改回配置值，设了也只维持一刻。
 */
public class BalanceSetterItem extends Item {

    public static final String TAG_MODE = "Mode";
    public static final int MODES = 3;
    /** 设平衡值要的权限等级（和 /ec5 指令一样） */
    public static final int PERMISSION_LEVEL = 2;

    public BalanceSetterItem(Properties properties) {
        super(properties);
    }

    /** 当前模式 0~2，也就是要设的平衡值 */
    public static int mode(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : Mth.clamp(tag.getInt(TAG_MODE), 0, MODES - 1);
    }

    public static void setMode(ItemStack stack, int mode) {
        stack.getOrCreateTag().putInt(TAG_MODE, Math.floorMod(mode, MODES));
    }

    /** 能不能用它改平衡值：创造模式或管理员 */
    public static boolean canApply(Player player) {
        return player.isCreative() || player.hasPermissions(PERMISSION_LEVEL);
    }

    /** 「冰冻（0）」这样的模式名 */
    public static Component modeName(int mode) {
        return Component.translatable("tooltip.essentialcraft5.balance_setter.mode_name", MRUBalance.kind(mode).label(), mode);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide) {
            int next = (mode(stack) + 1) % MODES;
            setMode(stack, next);
            player.displayClientMessage(Component.translatable("message.essentialcraft5.balance_setter.mode", modeName(next)), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        BlockEntity be = level.getBlockEntity(context.getClickedPos());
        if (be == null) {
            return InteractionResult.PASS;
        }
        Optional<IMRUHandler> handler = be.getCapability(ECCapabilities.MRU, null).resolve();
        if (handler.isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!canApply(player)) {
            player.displayClientMessage(Component.translatable("message.essentialcraft5.balance_setter.no_permission")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        int mode = mode(context.getItemInHand());
        handler.get().setBalance(mode);
        player.displayClientMessage(Component.translatable("message.essentialcraft5.balance_setter.applied",
                level.getBlockState(context.getClickedPos()).getBlock().getName(), modeName(mode)), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.essentialcraft5.balance_setter.mode", modeName(mode(stack)))
                .withStyle(ChatFormatting.GOLD));
        for (int i = 0; i < 3; i++) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.balance_setter.usage." + i).withStyle(ChatFormatting.GRAY));
        }
    }
}
