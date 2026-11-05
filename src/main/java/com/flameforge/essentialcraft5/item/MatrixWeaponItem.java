package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.matrix.MatrixResonance;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.mru.MRUItemStorage;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECStaffs;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 三把矩阵武器（原作 ItemChaosFork / ItemFrostMace / ItemHolyMace）：元素材料的剑，不掉耐久（原作 setMaxDamage(0)），自带 5 000 MRU，
 * 每次命中扣 {@value #HIT_COST} MRU（不管矩阵是什么属性都扣，原作如此）。能在附魔台和铁砧上附剑的魔咒（原作 isEnchantable 恒为 true），
 * 没有耐久所以不能附耐久、经验修补（原作同样不行）。
 * <ul>
 *   <li>混沌之叉（{@link Bonus#CHAOS}）：玩家是<b>混沌矩阵</b>时，命中给目标挖掘疲劳（5 秒），已有就再加一级；自己得到与目标原等级相同的力量（5 秒，第一下是力量 I）。</li>
 *   <li>冰冻重锤（{@link Bonus#FROZEN}）：玩家是<b>冰冻矩阵</b>时，命中给目标缓慢（50 秒），已有就再加一级。</li>
 *   <li>神圣重锤（{@link Bonus#NONE}）：原作没有特殊效果（E7 照原作），只有更高的攻击，也不能右键冲刺。</li>
 *   <li>混沌之叉、冰冻重锤按住右键 {@value #DASH_TICKS} 刻（1.6 秒，拉弓的姿势）：按满自动放出，朝视线方向冲刺（速度加上视线方向的单位向量，只动自己）。</li>
 * </ul>
 * 第十三批 13a（batch13.md §3.2、§4.6）：三把都带矩阵特性（标签 {@code essentialcraft5:matrix_attuned}，相性 ≥ I 时打实另触发，见 {@link MatrixTraits}），
 * 上面这些专属效果照留，是特性之外多的一样。混沌之叉攻击 12、攻速 2.0（{@link #CHAOS_FORK_SPEED}）；神圣重锤打亡灵 +50%、矩阵特性 ×2；
 * 两把叠在目标身上的效果，等级上限再加你的相性级（{@link #stackEffect}）；只对不是玩家的目标，给你自己的力量仍封顶 {@code weapon_max_effect_level}。
 * 矩阵属性靠矩阵投影改（研究手册「装备与防护 · 矩阵投影的使用」），这三把是投影「用了有收益」的唯一出口。
 * <h2>和原作的区别</h2>
 * <ul>
 *   <li>叠加的效果封顶 {@code general.staffs.weapon_max_effect_level} 级（默认 III）：原作无限叠加——混沌之叉连砍同一个目标，自己的力量跟着一级级往上涨，
 *       几十下以后一刀秒杀任何东西；等级超过 127 以后同步包里的字节还会溢出成负数（policy §1.2「叠级」）。</li>
 *   <li>原作按命中时目标的 hurtResistantTime 判断「这一下算不算数」；1.20 的 hurtEnemy 只在伤害真正生效后调用，所以每次命中都算。</li>
 *   <li>冲刺只在客户端改自己的速度（玩家的移动由客户端说了算，服务端改了也不会同步给本人）；服务端完成使用时不做别的。</li>
 * </ul>
 */
public class MatrixWeaponItem extends SwordItem implements CreativeVariants {

    /** 每次命中的花费（原作 playerUseMRU(…, 250)） */
    public static final int HIT_COST = 250;
    /** 原作攻击速度修正 −2.4（同原版剑） */
    public static final float ATTACK_SPEED = -2.4F;
    /** 混沌之叉的攻速修正（第十三批：攻速 1.6 → 2.0） */
    public static final float CHAOS_FORK_SPEED = -2F;
    /** 右键冲刺要按住的刻数（原作 getMaxItemUseDuration） */
    public static final int DASH_TICKS = 32;
    /** 混沌之叉的挖掘疲劳、力量时长（原作 100 刻） */
    public static final int CHAOS_TICKS = 100;
    /** 冰冻重锤的缓慢时长（原作 1000 刻） */
    public static final int FROZEN_TICKS = 1000;
    /** 矩阵类型序号（IPlayerData#getMatrixType：0 中性、1 混沌、2 冰冻、3 魔力、4 暗影） */
    public static final int MATRIX_CHAOS = 1;
    public static final int MATRIX_FROZEN = 2;

    public enum Bonus {
        /** 神圣重锤：没有矩阵效果，也不能右键冲刺（原作没有覆盖右键） */
        NONE,
        /** 混沌之叉：混沌矩阵时叠挖掘疲劳、给自己力量 */
        CHAOS,
        /** 冰冻重锤：冰冻矩阵时叠缓慢 */
        FROZEN;

        /** 能不能右键冲刺（原作只有叉和冰锤覆盖了右键） */
        public boolean dashes() {
            return this != NONE;
        }
    }

    private final Bonus bonus;

    public MatrixWeaponItem(Tier tier, int attackModifier, float attackSpeed, Bonus bonus, Properties properties) {
        super(tier, attackModifier, attackSpeed, properties);
        this.bonus = bonus;
    }

    public Bonus getBonus() {
        return bonus;
    }

    @Override
    public @Nullable ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new MRUItemStorage(stack, MRUStaffItem.MAX_MRU);
    }

    // ------------------------------------------------------------------
    // 不掉耐久、照样能附魔
    // ------------------------------------------------------------------

    /** 没有耐久（原作 setMaxDamage(0)）：挖方块、打怪都不掉，不显示耐久条，也不能附耐久、经验修补 */
    @Override
    public boolean canBeDepleted() {
        return false;
    }

    /** 原作 isEnchantable 恒为 true（没有耐久的物品原版默认不能附魔） */
    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    // ------------------------------------------------------------------
    // 命中
    // ------------------------------------------------------------------

    /** 只在服务端、伤害生效以后调用。不调 super（super 会扣耐久） */
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!(attacker instanceof Player player) || !MRUDrain.playerUseMRU(player, stack, HIT_COST)) {
            return false;
        }
        IPlayerData data = PlayerDataEvents.get(player);
        int matrix = data == null ? 0 : data.getMatrixType();
        // 目标身上效果的等级上限 = weapon_max_effect_level + 相性级（这两把只在对应属性下起效，相性级就是那种属性的）；
        // 目标是玩家时不加相性级（全批对玩家不放大，batch13.md §3.2）
        int extra = target instanceof Player ? 0 : MatrixResonance.level(player);
        if (bonus == Bonus.CHAOS && matrix == MATRIX_CHAOS) {
            int previous = stackEffect(target, MobEffects.DIG_SLOWDOWN, CHAOS_TICKS, extra);
            // 给自己的力量仍封顶在 weapon_max_effect_level：力量挂在人身上，换别的武器照样吃，原作无限叠的毛病就出在这里
            int strength = Math.min(Math.max(previous, 0), ECStaffs.Config.weaponMaxLevel() - 1);
            attacker.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, CHAOS_TICKS, strength));
        } else if (bonus == Bonus.FROZEN && matrix == MATRIX_FROZEN) {
            stackEffect(target, MobEffects.MOVEMENT_SLOWDOWN, FROZEN_TICKS, extra);
        }
        return true;
    }

    /** 叠在目标身上的效果的最高等级：{@code weapon_max_effect_level} + 相性级（目标是玩家时调用方传 0） */
    public static int maxEffectLevel(int alignmentLevel) {
        return ECStaffs.Config.weaponMaxLevel() + Math.max(0, alignmentLevel);
    }

    /**
     * 给目标叠一级效果（没有就给 I 级），等级封顶（{@link #maxEffectLevel}）；返回叠之前的放大器（没有时 −1）。
     */
    private static int stackEffect(LivingEntity target, MobEffect effect, int ticks, int alignmentLevel) {
        MobEffectInstance current = target.getEffect(effect);
        int previous = current == null ? -1 : current.getAmplifier();
        int cap = maxEffectLevel(alignmentLevel) - 1;
        target.addEffect(new MobEffectInstance(effect, ticks, Math.min(previous + 1, cap)));
        return Math.min(previous, cap);
    }

    // ------------------------------------------------------------------
    // 右键冲刺
    // ------------------------------------------------------------------

    @Override
    public int getUseDuration(ItemStack stack) {
        return bonus.dashes() ? DASH_TICKS : 0;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return bonus.dashes() ? UseAnim.BOW : UseAnim.NONE;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!bonus.dashes()) {
            return super.use(level, player, hand);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    /**
     * 按满以后朝视线冲刺。服务端按满时会发实体事件 9 让客户端也调一次这里（同吃东西），玩家的速度只有在客户端改才算数。
     */
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (level.isClientSide && entity instanceof Player && bonus.dashes()) {
            Vec3 look = entity.getLookAngle();
            entity.setDeltaMovement(entity.getDeltaMovement().add(look));
            entity.hasImpulse = true;
        }
        return stack;
    }

    // ------------------------------------------------------------------
    // 提示、创造栏、动画
    // ------------------------------------------------------------------

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        MRUStaffItem.mruTooltip(stack, tooltip);
        tooltip.add(MRUStaffItem.usage("tooltip.essentialcraft5.matrix_weapon.hit", HIT_COST));
        switch (bonus) {
            case CHAOS -> tooltip.add(MRUStaffItem.usage("tooltip.essentialcraft5.chaos_fork.bonus"));
            case FROZEN -> tooltip.add(MRUStaffItem.usage("tooltip.essentialcraft5.frozen_mace.bonus"));
            case NONE -> tooltip.add(MRUStaffItem.usage("tooltip.essentialcraft5.holy_mace.undead",
                    MatrixResonance.percent(ECStaffs.Config.holyMaceUndeadBonus())));
        }
        if (bonus.dashes()) {
            tooltip.add(MRUStaffItem.usage("tooltip.essentialcraft5.matrix_weapon.dash"));
        }
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !oldStack.is(newStack.getItem());
    }

    @Override
    public List<ItemStack> creativeVariants() {
        return MRUStaffItem.variants(this);
    }
}
