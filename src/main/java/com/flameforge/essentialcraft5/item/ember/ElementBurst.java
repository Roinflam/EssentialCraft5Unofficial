package com.flameforge.essentialcraft5.item.ember;

import com.flameforge.essentialcraft5.item.attunement.Attunement;
import com.flameforge.essentialcraft5.item.attunement.AttunementConfig;
import com.flameforge.essentialcraft5.item.attunement.AttunementFamily;
import com.flameforge.essentialcraft5.item.attunement.GearAoe;
import com.flameforge.essentialcraft5.item.attunement.GearCaps;
import com.flameforge.essentialcraft5.item.attunement.GearGrowthConfig;
import com.flameforge.essentialcraft5.item.attunement.GearStatSheet;
import com.flameforge.essentialcraft5.network.GearFx;
import com.flameforge.essentialcraft5.network.S2CGearFxPacket;
import com.flameforge.essentialcraft5.registry.ECGearSounds;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleSupplier;

/**
 * 元素爆发：余烬元素剑的满阶觉醒（tools/plan/gear_growth.md §4.2，{@code general.gear_growth.ember_sword.burst}）。
 * <p>
 * 同调家族是 {@code ember_sword}（裁决之刃不继承）、到了 {@code burst.stage}（2）阶时，剑的 {@code hurtEnemy}（服务端、这一下已经打成）里
 * 有 {@code chance}（15%）几率、每名持剑人每 {@code cooldown}（40）刻最多一次，以目标为中心半径 {@code radius}（3）爆发剑的主元素（复合随机挑一种）：
 * <ul>
 *   <li>火：每个目标 {@code gear_burst} {@code fire_damage}（4）+ 目标最大生命 {@code fire_percent}%（2%，首领只吃固定那一截），着火 3 秒；
 *       每个目标这一下最多掉 {@code fire_cap}（100）点血，首领同样封顶（血很厚、又不算首领的怪按百分比会打出几百）；</li>
 *   <li>水：缓慢 II + 虚弱 I {@code debuff_ticks}（60）刻（打玩家按 {@code pvp_effect_scale} 打折）；</li>
 *   <li>地：自己抗性提升 I {@code debuff_ticks} 刻，范围内每个目标 {@code gear_burst} {@code quake_damage}（3）；</li>
 *   <li>风：范围击退（水平 0.8），自己迅捷 II 40 刻。</li>
 * </ul>
 * 范围技的伤害一律走 {@link GearAoe}（不连锁、不吃增伤池、乘 {@code aoe_scale}、默认不打玩家）。发 {@link GearFx#ELEMENT_BURST}（param = 元素序号 0 火 1 水 2 地 3 风）。
 */
public final class ElementBurst {

    /** 风：击退的水平力度 */
    public static final double KNOCKBACK = 0.8D;
    /** 风：自己迅捷 II 的刻数 */
    public static final int SPEED_TICKS = 40;
    /** 火：着火几秒 */
    public static final int FIRE_SECONDS = 3;
    /** 水 / 风打到玩家时先过的那一下伤害（只用来让 PvP / 领地判一次，见 {@link #reachesPlayer}） */
    public static final float PLAYER_PROBE_DAMAGE = 0.5F;

    private static final Map<UUID, Long> READY = new HashMap<>();
    @Nullable
    private static volatile DoubleSupplier testRoll;

    private ElementBurst() {
    }

    /** GameTest：掷骰覆盖（返回的数 &lt; 几率就触发；null = 照常随机） */
    public static void setTestRoll(@Nullable DoubleSupplier roll) {
        testRoll = roll;
    }

    /** 测试：清掉冷却 */
    public static void clearCooldowns() {
        READY.clear();
    }

    /** 这把剑能不能爆发：余烬剑家族、到了解锁阶 */
    public static boolean unlocked(ItemStack stack) {
        AttunementFamily family = AttunementFamily.of(stack);
        return family != null && family.id().equals(GearStatSheet.EMBER_SWORD) && Attunement.stage(stack) >= GearGrowthConfig.burstStage();
    }

    /**
     * 剑的 hurtEnemy 里调。触发了返回用的元素，否则 null。
     */
    @Nullable
    public static ForgedSwordData.Element onHit(ItemStack stack, LivingEntity target, ServerPlayer player, ForgedSwordData.Element primary) {
        // 假玩家（机械手、自动攻击方块）不放范围技：伤害会记在认不出是谁的假玩家头上
        if (!unlocked(stack) || !ECProtection.isRealPlayer(player)) {
            return null;
        }
        long now = now(player);
        long cooldown = Math.max(0, GearGrowthConfig.burstCooldown());
        Long ready = READY.get(player.getUUID());
        // 刻数是服务器从启动起数的：单人退出再进存档时内置服务端从 0 重数，旧记录会比「现在 + 冷却」还远，当成过期
        if (ready != null && now < ready && ready - now <= cooldown) {
            return null;
        }
        DoubleSupplier override = testRoll;
        double roll = override != null ? override.getAsDouble() : player.getRandom().nextDouble();
        if (!(roll < GearGrowthConfig.burstChance())) {
            return null;
        }
        READY.put(player.getUUID(), now + cooldown);
        ForgedSwordData.Element element = primary;
        if (element == ForgedSwordData.Element.COMBINED) {
            List<ForgedSwordData.Element> single = ForgedSwordData.Element.SINGLE;
            element = single.get(player.getRandom().nextInt(single.size()));
        }
        burst(player, target, element);
        return element;
    }

    /** 以 target 为中心放一次 element 的爆发（GameTest 也直接调） */
    public static void burst(ServerPlayer player, LivingEntity target, ForgedSwordData.Element element) {
        Vec3 center = target.position();
        List<LivingEntity> targets = GearAoe.targets(player, center, GearGrowthConfig.burstRadius(), GearGrowthConfig.burstMaxTargets());
        int ticks = Math.max(0, GearGrowthConfig.burstDebuffTicks());
        switch (element) {
            case FIRE -> {
                for (LivingEntity hit : targets) {
                    double damage = GearGrowthConfig.burstFireDamage();
                    if (!GearCaps.isBoss(hit)) {
                        damage += hit.getMaxHealth() * GearGrowthConfig.burstFirePercent() / 100D;
                    }
                    if (GearAoe.hit(player, hit, (float) damage, (float) GearGrowthConfig.burstFireCap())) {
                        hit.setSecondsOnFire(FIRE_SECONDS);
                    }
                }
            }
            case WATER -> {
                for (LivingEntity hit : targets) {
                    int scaled = hit instanceof Player ? (int) Math.round(ticks * Math.max(0D, AttunementConfig.pvpEffectScale())) : ticks;
                    if (scaled > 0 && reachesPlayer(player, hit)) {
                        hit.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, scaled, 1), player);
                        hit.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, scaled, 0), player);
                    }
                }
            }
            case EARTH -> {
                if (ticks > 0) {
                    player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, 0));
                }
                for (LivingEntity hit : targets) {
                    GearAoe.hit(player, hit, (float) GearGrowthConfig.burstQuakeDamage());
                }
            }
            case AIR -> {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, SPEED_TICKS, 1));
                for (LivingEntity hit : targets) {
                    if (!reachesPlayer(player, hit)) {
                        continue;
                    }
                    double dx = center.x - hit.getX();
                    double dz = center.z - hit.getZ();
                    if (dx * dx + dz * dz < 1.0E-4D) {
                        dx = player.getX() - hit.getX();
                        dz = player.getZ() - hit.getZ();
                    }
                    hit.knockback(KNOCKBACK, dx, dz);
                    if (hit instanceof ServerPlayer) {
                        // 玩家的速度由客户端说了算，不标记的话服务端改的击退会被下一个移动包盖掉
                        hit.hurtMarked = true;
                    }
                }
            }
            case COMBINED -> {
            }
        }
        ServerLevel level = player.serverLevel();
        S2CGearFxPacket.sendAround(level, GearFx.ELEMENT_BURST, target, center, ForgedSwordData.Element.SINGLE.indexOf(element));
        ECGearSounds.play(level, center, ECGearSounds.ELEMENT_BURST, SoundSource.PLAYERS, 0.9F, 1F);
    }

    /**
     * 水 / 风不造成伤害，对玩家先过一下极小的 {@code gear_burst}（经 {@link GearAoe#hit}），被 PvP 开关、领地、混合端插件拦下的就不挂效果、不击退。
     * 不用 0 伤害探测：纯 Forge 对玩家的 0 伤害到不了 LivingHurtEvent。非玩家目标照常。
     */
    private static boolean reachesPlayer(ServerPlayer player, LivingEntity hit) {
        return !(hit instanceof Player) || GearAoe.hit(player, hit, PLAYER_PROBE_DAMAGE);
    }

    private static long now(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        return server != null ? server.getTickCount() : player.level().getGameTime();
    }
}
