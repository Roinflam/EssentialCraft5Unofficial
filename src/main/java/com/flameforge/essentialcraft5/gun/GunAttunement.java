package com.flameforge.essentialcraft5.gun;

import com.flameforge.essentialcraft5.item.GunItem;
import com.flameforge.essentialcraft5.item.attunement.Attunement;
import com.flameforge.essentialcraft5.item.attunement.AttunementFamily;
import com.flameforge.essentialcraft5.item.attunement.GearAoe;
import com.flameforge.essentialcraft5.item.attunement.GearCaps;
import com.flameforge.essentialcraft5.item.attunement.GearGrowthConfig;
import com.flameforge.essentialcraft5.item.attunement.GearStat;
import com.flameforge.essentialcraft5.item.attunement.GearStatSheet;
import com.flameforge.essentialcraft5.network.GearFx;
import com.flameforge.essentialcraft5.network.S2CGearFxPacket;
import com.flameforge.essentialcraft5.registry.ECGearSounds;
import com.flameforge.essentialcraft5.registry.ECGuns;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 枪械同调（家族 {@code mru_gun}，4 阶，武器预算；tools/plan/gear_growth.md §4.3）。这里只放「枪自己」的阶效果，
 * 每阶 +8% 增伤和 3 阶的护甲穿透走总闸（{@code GearCombatEvents} 按 {@code GearPool.ownWeapon} 认射线），攒点在 {@code AttunementGains}。
 * <ul>
 *   <li><b>每发 MRU 折扣</b>（默认 1 阶起 −10%）：{@link #mruPerShot}，开火、客户端预判、提示框、MRU 不够的提示都用它，四处数字一致。</li>
 *   <li><b>装填后冷却缩减</b>（默认 3 阶 −20%，过 {@code caps.cooldown_reduction}）：{@link #reloadCooldown}，只有手枪、狙击有这段冷却。</li>
 *   <li><b>弹道累积</b>（默认 2 阶起）：射线打进受击无敌被整下吞掉的伤害记账、补进下一发，同累加器模块（{@code AccumulatorEvents}，
 *       {@link #accumulates}）——不清无敌帧、账照样 40 刻过期。</li>
 *   <li><b>过载射线</b>（默认 4 阶）：每发打实（{@link RayShot.Result#damaged} 非空）充 1，充满（步枪、加特林 12 发，手枪、狙击 3 发）
 *       发 {@link GearFx#OVERLOAD_READY} + 音效 + 动作栏；下一发不管打没打中都放出来：以第一个打实的目标（没有就光束终点）为中心，
 *       半径内最多 {@code max_targets} 个目标各吃一下 {@code gear_burst} = 这一发的伤害 × {@code ratio}（{@link GearAoe}：乘 {@code aoe_scale}、
 *       默认不打玩家、不连锁），发 {@link GearFx#OVERLOAD_FIRE}，充能清零（放出来的那一发不再充）。</li>
 * </ul>
 * 折扣、冷却、过载都按<b>开火的这把枪</b>自己的阶算（副手开火也算）；攒点、增伤池、弹道累积认的是主手（和剑一样只认主手武器）。
 * 充能存在枪的同调 NBT {@value Attunement#TAG} 里的 {@value #KEY_CHARGE}（不到过载阶时不读也不写），客户端物品格读 {@link #charge} / {@link #chargeMax} 画充能条。
 */
public final class GunAttunement {

    /** 同调 NBT 里的过载充能（int） */
    public static final String KEY_CHARGE = "charge";

    private GunAttunement() {
    }

    /** 这件是不是枪这条线的同调武器 */
    public static boolean isAttunedGun(ItemStack stack) {
        AttunementFamily family = AttunementFamily.of(stack);
        return family != null && family.line() == AttunementFamily.Line.GUN;
    }

    /** 枪这条线的同调阶；不是同调枪是 0 */
    public static int stage(ItemStack stack) {
        return isAttunedGun(stack) ? Attunement.stage(stack) : 0;
    }

    // ------------------------------------------------------------------
    // MRU、冷却
    // ------------------------------------------------------------------

    /** 每发 MRU：枪种的基础值（{@link GunType#mruPerShot}）× (1 − 折扣)，四舍五入；不是同调枪就是基础值 */
    public static int mruPerShot(ItemStack stack, GunType type, GunStats stats) {
        int base = type.mruPerShot(stats);
        double discount = isAttunedGun(stack) ? GearStatSheet.get(stack, GearStat.MRU_DISCOUNT) : 0D;
        if (base <= 0 || !(discount > 0D)) {
            return base;
        }
        return Math.max(0, (int) Math.round(base * (1D - Math.min(1D, discount))));
    }

    /** 手枪、狙击装填完以后的冷却（刻）：{@link GunItem#RELOAD_COOLDOWN} 按这把枪的冷却缩减打折（过总闸上限） */
    public static int reloadCooldown(ItemStack stack) {
        double cdr = isAttunedGun(stack) ? GearStatSheet.get(stack, GearStat.COOLDOWN) : 0D;
        return GearCaps.reducedCooldown(GunItem.RELOAD_COOLDOWN, cdr);
    }

    // ------------------------------------------------------------------
    // 弹道累积
    // ------------------------------------------------------------------

    /**
     * 这一下是不是走弹道累积：本人的射线（{@code mru_ray}、记在他头上），主手是同调枪、阶 ≥ {@code accumulate_stage}。
     * {@code AccumulatorEvents} 在「开着累加器模块」之外再认这一条。
     */
    public static boolean accumulates(ServerPlayer attacker, DamageSource source) {
        if (!source.is(ECGuns.MRU_RAY) || source.getEntity() != attacker) {
            return false;
        }
        ItemStack main = attacker.getMainHandItem();
        return isAttunedGun(main) && Attunement.stage(main) >= GearGrowthConfig.gunAccumulateStage();
    }

    // ------------------------------------------------------------------
    // 过载射线
    // ------------------------------------------------------------------

    /** 充能（发数）；没有是 0 */
    public static int charge(ItemStack stack) {
        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(Attunement.TAG, Tag.TAG_COMPOUND)) {
            return 0;
        }
        return Math.max(0, root.getCompound(Attunement.TAG).getInt(KEY_CHARGE));
    }

    /** 充满要几发：手枪、狙击 {@code shots_single}，别的 {@code shots}；不是同调枪、没到过载阶是 0（不画充能条） */
    public static int chargeMax(ItemStack stack) {
        if (!(stack.getItem() instanceof GunItem gun) || !isAttunedGun(stack) || Attunement.stage(stack) < GearGrowthConfig.overloadStage()) {
            return 0;
        }
        boolean single = gun.type() == GunType.PISTOL || gun.type() == GunType.SNIPER;
        return Math.max(1, single ? GearGrowthConfig.overloadShotsSingle() : GearGrowthConfig.overloadShots());
    }

    /** 充满了：下一发放过载 */
    public static boolean ready(ItemStack stack) {
        int max = chargeMax(stack);
        return max > 0 && charge(stack) >= max;
    }

    /** 写充能（夹到 0 ~ 充满；0 时删掉这个键） */
    public static void setCharge(ItemStack stack, int charge) {
        int max = chargeMax(stack);
        int value = Math.max(0, max > 0 ? Math.min(max, charge) : charge);
        if (value == 0) {
            CompoundTag root = stack.getTag();
            if (root != null && root.contains(Attunement.TAG, Tag.TAG_COMPOUND)) {
                root.getCompound(Attunement.TAG).remove(KEY_CHARGE);
            }
            return;
        }
        stack.getOrCreateTagElement(Attunement.TAG).putInt(KEY_CHARGE, value);
    }

    /**
     * 开完一枪以后（服务端，{@code GunItem.shoot} 调）：充满了就放过载、清零；没满就按这一发打没打实充 1，正好充满时发「就绪」。
     * 不到过载阶的枪什么都不做（以前攒的充能也不动，升回来接着用）。
     *
     * @param ready  开这一枪之前是不是已经充满（{@link #ready}，开火前取，免得这一发刚充满就当场放掉）
     * @param result 这一发的结果
     */
    public static void afterShot(Player player, ItemStack stack, GunStats stats, boolean ready, RayShot.Result result) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        int max = chargeMax(stack);
        if (max <= 0) {
            return;
        }
        if (ready) {
            burst(level, player, stats, result);
            setCharge(stack, 0);
            return;
        }
        if (result.damaged().isEmpty()) {
            return;
        }
        int charge = Math.min(max, charge(stack) + 1);
        setCharge(stack, charge);
        if (charge >= max) {
            S2CGearFxPacket.sendAround(level, GearFx.OVERLOAD_READY, player, player.position(), max);
            ECGearSounds.play(level, player.position(), ECGearSounds.OVERLOAD_READY, SoundSource.PLAYERS, 0.8F, 1.2F);
            player.displayClientMessage(Component.translatable("message.essentialcraft5.gun.overload_ready").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
    }

    /**
     * 放过载：中心 = 第一个打实的目标（碰撞箱中心），没打实就是光束终点；半径内最多 {@code max_targets} 个各打一下 {@code gear_burst}。
     * 每下的数 = 这一发的伤害 × {@code ratio}；{@code aoe_hits_players} 打开时打玩家照射线的规矩再乘 {@code player_damage_multiplier}。
     *
     * @return 打成了几个
     */
    public static int burst(ServerLevel level, Player player, GunStats stats, RayShot.Result result) {
        List<LivingEntity> damaged = result.damaged();
        Vec3 center = damaged.isEmpty() ? result.to() : damaged.get(0).getBoundingBox().getCenter();
        double radius = GearGrowthConfig.overloadRadius();
        float damage = (float) (stats.damage() * Math.max(0D, GearGrowthConfig.overloadRatio()));
        int hit = 0;
        for (LivingEntity target : GearAoe.targets(player, center, radius, GearGrowthConfig.overloadMaxTargets())) {
            float amount = target instanceof Player ? (float) (damage * GunConfig.playerDamageMultiplier()) : damage;
            if (GearAoe.hit(player, target, amount)) {
                hit++;
            }
        }
        S2CGearFxPacket.sendAround(level, GearFx.OVERLOAD_FIRE, player, center, (float) radius);
        ECGearSounds.play(level, center, ECGearSounds.OVERLOAD_FIRE, SoundSource.PLAYERS, 1F, 1F);
        return hit;
    }
}
