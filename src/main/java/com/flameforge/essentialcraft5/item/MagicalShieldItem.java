package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.mru.MRUDrain;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;

import java.util.List;

/**
 * 魔力护盾（原作 ItemMagicalShield）：右键举盾 {@value #USE_DURATION} 刻（2 秒），开举时扣 {@value #RAISE_COST} MRU。
 * 举着的时候每刻把无敌时间设成 {@value #INVULNERABLE_TICKS}（1.12 的 hurtResistantTime → 1.20 的 invulnerableTime）：
 * 不超过上一次所受伤害的攻击被无视、更大的只吃差值（原版受伤冷却的规则）；另外它和原版盾一样能格挡正面的攻击
 * （原作的 BLOCK 动作在 1.12 就等于盾；1.20 的 Forge 要物品声明 {@link ToolActions#SHIELD_BLOCK}）。不掉耐久，被斧头打掉时冷却 5 秒（原版盾的规则）。
 * <p>
 * 客户端只按「够不够」决定要不要举起来，真正的扣费在服务端。
 */
public class MagicalShieldItem extends MRUStaffItem {

    /** 举盾时长（原作 getMaxItemUseDuration） */
    public static final int USE_DURATION = 40;
    /** 开举的花费（原作 playerUseMRU(…, 2000)） */
    public static final int RAISE_COST = 2000;
    /** 举着时每刻设的无敌时间（原作 hurtResistantTime = 20） */
    public static final int INVULNERABLE_TICKS = 20;

    public MagicalShieldItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return USE_DURATION;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BLOCK;
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ToolAction toolAction) {
        return ToolActions.DEFAULT_SHIELD_ACTIONS.contains(toolAction);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean paid = level.isClientSide ? canAfford(player, stack, RAISE_COST) : MRUDrain.playerUseMRU(player, stack, RAISE_COST);
        if (!paid) {
            if (!level.isClientSide) {
                tellNoMRU(player, RAISE_COST);
            }
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        entity.invulnerableTime = INVULNERABLE_TICKS;
    }

    @Override
    protected void addUsage(ItemStack stack, List<Component> tooltip) {
        tooltip.add(usage("tooltip.essentialcraft5.magical_shield.use", RAISE_COST));
    }
}
