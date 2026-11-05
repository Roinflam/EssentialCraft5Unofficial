package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.item.ember.InlayEvents;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * 尖刺圆盾（原作 ItemSpikyShield）：和魔力护盾一样举 2 秒、开举扣 {@value #RAISE_COST} MRU；
 * 举满以后再扣 {@value #SPIKE_COST} MRU，对周围水平 ±{@value #RANGE_H}、竖直 ±{@value #RANGE_V} 格（10×4×10）里的敌对生物各造成 {@value #SPIKE_DAMAGE} 点伤害。
 * 伤害源是玩家的近战攻击（{@code damageSources().playerAttack(player)}），击杀、仇恨、PvP / 领地规则都照玩家算；只打敌对生物（{@link Enemy}），不打玩家、村民、动物、宠物。
 * <h2>和原作的区别</h2>
 * <ul>
 *   <li>原作 {@code if (playerUseMRU(2000)) {}} 后面跟的是一个独立的块（L50~54），没有 MRU 也能免费举盾；现在够 2 000 才举得起来（同魔力护盾）。</li>
 *   <li>原作只打 EntityMob（僵尸、骷髅、苦力怕等）；1.20 按 {@link Enemy} 算，史莱姆、恶魂、幻翼、潜影贝这些敌对生物也算。</li>
 * </ul>
 */
public class SpikyShieldItem extends MagicalShieldItem {

    /** 举满后的花费（原作 playerUseMRU(…, 100)） */
    public static final int SPIKE_COST = 100;
    /** 对每个敌对生物的伤害（原作 12F） */
    public static final float SPIKE_DAMAGE = 12F;
    /** 原作框 (x±5, y±2, z±5) */
    public static final double RANGE_H = 5D;
    public static final double RANGE_V = 2D;

    public SpikyShieldItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof Player player && MRUDrain.playerUseMRU(player, stack, SPIKE_COST)) {
            spike(level, player);
        }
        return stack;
    }

    /**
     * 服务端：对范围里的敌对生物造成伤害，返回打中了几个（GameTest 用；不扣 MRU）。
     * 这圈伤害不是主手武器打的，包在 {@link InlayEvents#notWeaponHit} 里，不触发主手武器的余烬嵌刻（不然副手举盾、主手拿嵌刻剑，范围里每只各触发一次）。
     */
    public static int spike(Level level, Player player) {
        AABB box = new AABB(player.getX() - RANGE_H, player.getY() - RANGE_V, player.getZ() - RANGE_H,
                player.getX() + RANGE_H, player.getY() + RANGE_V, player.getZ() + RANGE_H);
        DamageSource source = player.damageSources().playerAttack(player);
        int hit = 0;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, e -> e instanceof Enemy && e.isAlive() && e != player)) {
            if (InlayEvents.notWeaponHit(() -> target.hurt(source, SPIKE_DAMAGE))) {
                hit++;
            }
        }
        return hit;
    }

    @Override
    protected void addUsage(ItemStack stack, List<Component> tooltip) {
        super.addUsage(stack, tooltip);
        tooltip.add(usage("tooltip.essentialcraft5.spiky_shield.spike", (int) SPIKE_DAMAGE, SPIKE_COST));
    }
}
