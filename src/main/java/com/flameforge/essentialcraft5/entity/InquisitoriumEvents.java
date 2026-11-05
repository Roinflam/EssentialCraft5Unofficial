package com.flameforge.essentialcraft5.entity;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.CorruptionEffects;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECInquisitorium;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * 审判庭哨兵的事件规则（第十批 10c-M1，batch10.md §5.10.5）：它的招式打实参战者一下，另加矩阵损伤。
 * <p>
 * 认「打实」用 {@code LivingDamageEvent}（LOWEST、不收已取消的、数值 &gt; 0）：领地模组在 {@code LivingAttackEvent} / {@code LivingHurtEvent} 里拦下的、
 * Mohist 系混合端插件拦下 {@code EntityDamageByEntityEvent} 的（那时不发 {@code LivingDamageEvent}）都不加；盾牌整下挡掉、MRU 盔甲挡满的一下
 * 到这里是 0 或者根本不来，也不加。认「哨兵的招式」：轨道打击（伤害记在哨兵头上、没有直接实体）和它变出来的克隆体的近战；
 * 拉回扣的那 5 点（直接实体就是哨兵本身）不算招式，不加。被打的人要在它的参战名单里（招式本来也只打参战者）。
 * <p>
 * 数额 {@code general.inquisitorium.matrix_damage_per_hit}，走 {@link CorruptionEffects#addMatrixDamage}（夹在矩阵损伤上限以内）；
 * 只给生存 / 冒险、没开无敌能力的玩家（同魔力枪加矩阵损伤的口径）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class InquisitoriumEvents {

    private InquisitoriumEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSentinelHitLanded(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getAmount() <= 0F) {
            return;
        }
        InquisitoriumSentinelEntity sentinel = sentinelBehind(event.getSource());
        if (sentinel == null || !sentinel.isParticipant(player.getUUID())) {
            return;
        }
        addMatrixDamage(player, ECInquisitorium.Rules.values().matrixDamagePerHit());
    }

    /** 这一下是不是审判庭哨兵的招式打的：是的话返回那只哨兵（轨道打击 → 哨兵；克隆体 → 变出它的哨兵；拉回、别的都返回 null） */
    @Nullable
    public static InquisitoriumSentinelEntity sentinelBehind(DamageSource source) {
        Entity attacker = source.getEntity();
        if (attacker instanceof InquisitoriumSentinelEntity sentinel) {
            // 拉回扣血是 mobAttack(哨兵)，直接实体就是它自己；轨道打击不带直接实体
            return source.getDirectEntity() == sentinel ? null : sentinel;
        }
        if (attacker instanceof PlayerCloneEntity clone && clone.boss() instanceof InquisitoriumSentinelEntity sentinel) {
            return sentinel;
        }
        return null;
    }

    /** 加矩阵损伤（有上限）；创造 / 旁观、开了无敌能力、死了的不加。返回实际加上的量 */
    public static int addMatrixDamage(ServerPlayer player, int amount) {
        if (amount <= 0 || !player.gameMode.isSurvival() || player.getAbilities().invulnerable || !player.isAlive()) {
            return 0;
        }
        IPlayerData data = PlayerDataEvents.get(player);
        return data == null ? 0 : CorruptionEffects.addMatrixDamage(data, amount);
    }
}
