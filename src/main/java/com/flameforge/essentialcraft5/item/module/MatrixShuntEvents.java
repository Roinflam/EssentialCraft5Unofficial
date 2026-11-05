package com.flameforge.essentialcraft5.item.module;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.CorruptionEffects;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.gun.RayHitGate;
import com.flameforge.essentialcraft5.item.armor.MRUShield;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECSurvivalModules;
import com.flameforge.essentialcraft5.util.ECDamageKinds;
import com.flameforge.essentialcraft5.util.ECShieldBlocks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 矩阵分流模块（第十批 10c-M2，batch10.md §5.11.3）：魔法伤害的一部分（{@code ratio}，默认一半）不扣血，改记矩阵损伤。
 * 补宅男套装「不挡魔法」的洞，代价同样走矩阵。只在服务端、只对穿戴者自己。
 * <p>
 * 记多少按分走的伤害占穿戴者最大生命的比例换算（{@code matrix_per_max_health}，默认 2000：分走最大生命的 1% 记 20；原版 20 点生命时每点 100，
 * 和最早按点数换算的数一样）。第十一批改的：整合包里生命几百上千、别的模组的法术一下几百点，按每点 100 记几下就到上限，
 * 改成和腐化扣血同一个口径（按最大生命的比例），血再多也和原版一样耐用。
 *
 * <h2>流程（纯 Forge 和混合端同一写法）</h2>
 * <ol>
 *   <li>{@link LivingHurtEvent} <b>NORMAL</b>、不收已取消的（比 {@code MRUShield} 的 LOW 早）：伤害 &gt; 0、{@link ECDamageKinds#isMagic}、矩阵分流生效时，
 *       先看 {@link CorruptionEffects#matrixRoom}：只分流记得下的那部分（换算回伤害），满了就整下照常扣血；伤害里减掉分流的量，
 *       记一条待记（这名玩家、这个伤害源对象、这一刻、要记的矩阵损伤）。</li>
 *   <li>{@link LivingHurtEvent} <b>LOWEST</b>、收已取消的：这一下最后被取消、或者被扣到 0 了 → 丢掉待记。纯 Forge 上这两种都到不了 {@code LivingDamageEvent}；
 *       混合端上还会按 0 往下走、照样发 {@code LivingDamageEvent(0)}，在这里丢掉两边才一致。
 *       例外：剩下的部分被 MRU 盔甲的护盾整下吃掉（{@code MRUShield.tookWholeHit}，宅男 2 阶起护盾也挡魔法，batch13.md §4.4「分流先分一半、护盾挡剩下的」），
 *       这一下是真的挨了：纯 Forge 上护盾已经扣过电，当场记矩阵损伤；混合端上护盾也要等插件放行才扣电，待记留着，跟它一起等 {@code LivingDamageEvent(0)}。
 *       不这样的话，分流那一半在护盾挡满时一点代价都没有。</li>
 *   <li>{@link LivingDamageEvent} <b>LOWEST</b>、不收已取消的：认出同一下（同一名玩家、同一个伤害源对象、同一刻）才记矩阵损伤（{@link CorruptionEffects#addMatrixDamage}）。
 *       混合端上领地插件拦下的伤害（Bukkit 的伤害事件夹在 Hurt 和 Damage 之间）到不了这里，不记；被盾牌整下挡掉的（{@link ECShieldBlocks#fullyShielded}，
 *       混合端挡满照发 {@code LivingDamageEvent(0)}）不记。待记表每刻末清空。</li>
 * </ol>
 * 比例上限 0.9：分流以后总还剩一点伤害，伤害流程照常走完、{@code LivingDamageEvent} 照常发，矩阵损伤才记得上（全部分掉的话 {@code actuallyHurt} 直接返回，等于白挡）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class MatrixShuntEvents {

    /** 待记表最多几条（正常只有 0~1 条），满了丢最早的 */
    private static final int MAX_PENDING = 32;

    /**
     * 一下分流掉的、等 {@code LivingDamageEvent} 认出再记的矩阵损伤。
     *
     * @param player 挨打的玩家
     * @param source 伤害源（按对象比）
     * @param tick   哪一刻（gameTime）
     * @param matrix 要记的矩阵损伤
     */
    private record Pending(Player player, DamageSource source, long tick, long matrix) {
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    private MatrixShuntEvents() {
    }

    /** NORMAL：分流（见类注释第 1 条） */
    @SubscribeEvent
    public static void onMatrixShuntHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) {
            return;
        }
        float amount = event.getAmount();
        if (!(amount > 0F) || !Float.isFinite(amount)) {
            return;
        }
        DamageSource source = event.getSource();
        if (!ECDamageKinds.isMagic(source) || !ModuleCache.active(player, ECSurvivalModules.MATRIX_SHUNT)) {
            return;
        }
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            return;
        }
        Split split = split(amount, player.getMaxHealth(), CorruptionEffects.matrixRoom(data), SurvivalModuleConfig.shunt());
        if (split.shunted() <= 0F) {
            return;
        }
        // 同一个人、同一个伤害源又来一下：上一条没走到 LivingDamageEvent（被拦下了），作废
        PENDING.removeIf(pending -> pending.player() == player && pending.source() == source);
        if (PENDING.size() >= MAX_PENDING) {
            PENDING.remove(0);
        }
        event.setAmount(amount - split.shunted());
        PENDING.add(new Pending(player, source, player.level().getGameTime(), split.matrix()));
    }

    /** LOWEST、收已取消的：最后被取消或扣到 0 的，丢掉待记；被护盾整下吃掉的照记（见类注释第 2 条） */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onMatrixShuntHurtSettled(LivingHurtEvent event) {
        if (PENDING.isEmpty() || !(event.isCanceled() || !(event.getAmount() > 0F))) {
            return;
        }
        Player player = event.getEntity() instanceof Player p ? p : null;
        if (player == null) {
            return;
        }
        DamageSource source = event.getSource();
        if (event.isCanceled() && MRUShield.tookWholeHit(player, source)) {
            if (RayHitGate.isBukkitHybrid()) {
                return;
            }
            Pending pending = take(player, source);
            IPlayerData data = PlayerDataEvents.get(player);
            if (pending != null && pending.tick() == player.level().getGameTime() && data != null) {
                CorruptionEffects.addMatrixDamage(data, pending.matrix());
            }
            return;
        }
        PENDING.removeIf(pending -> pending.player() == player && pending.source() == source);
    }

    /** LOWEST、不收已取消的：伤害真的落下了才记（见类注释第 3 条） */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMatrixShuntDamage(LivingDamageEvent event) {
        if (PENDING.isEmpty() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Pending pending = take(player, event.getSource());
        if (pending == null || pending.tick() != player.level().getGameTime()
                || ECShieldBlocks.fullyShielded(player, event.getSource(), event.getAmount())) {
            return;
        }
        IPlayerData data = PlayerDataEvents.get(player);
        if (data != null) {
            CorruptionEffects.addMatrixDamage(data, pending.matrix());
        }
    }

    @Nullable
    private static Pending take(Player player, DamageSource source) {
        for (int i = 0; i < PENDING.size(); i++) {
            Pending pending = PENDING.get(i);
            if (pending.player() == player && pending.source() == source) {
                PENDING.remove(i);
                return pending;
            }
        }
        return null;
    }

    @SubscribeEvent
    public static void onMatrixShuntServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !PENDING.isEmpty()) {
            PENDING.clear();
        }
    }

    @SubscribeEvent
    public static void onMatrixShuntServerStopping(ServerStoppingEvent event) {
        PENDING.clear();
    }

    // ------------------------------------------------------------------ 算

    /**
     * 一下魔法伤害怎么分。
     *
     * @param shunted 分流掉的伤害（0 = 不分流）
     * @param matrix  要记的矩阵损伤
     */
    public record Split(float shunted, long matrix) {
    }

    /** 最大生命不是有限正数时（别的模组把属性弄坏了）按原版 20 点算 */
    public static final float FALLBACK_MAX_HEALTH = 20F;

    /**
     * 按比例和矩阵损伤的余量算分流（只分流记得下的那部分）：分走 x 点记 ⌈x ÷ 最大生命 × matrix_per_max_health⌉。
     *
     * @param amount    这一下的伤害（&gt; 0）
     * @param maxHealth 穿戴者现在的最大生命
     * @param room      矩阵损伤还能记多少（{@link CorruptionEffects#matrixRoom}）
     */
    public static Split split(float amount, float maxHealth, int room, SurvivalModuleConfig.Shunt cfg) {
        double ratio = Math.max(0D, Math.min(SurvivalModuleConfig.MAX_SHUNT_RATIO, cfg.ratio()));
        double want = amount * ratio;
        if (!(want > 0D) || room <= 0) {
            return new Split(0F, 0L);
        }
        int perMax = Math.max(0, cfg.matrixPerMaxHealth());
        if (perMax == 0) {
            return new Split((float) want, 0L);
        }
        double max = maxHealth > 0F && Float.isFinite(maxHealth) ? maxHealth : FALLBACK_MAX_HEALTH;
        double perDamage = perMax / max;
        double shunted = Math.min(want, room / perDamage);
        long matrix = Math.min(room, (long) Math.ceil(shunted * perDamage - 1.0E-6D));
        return matrix <= 0L ? new Split(0F, 0L) : new Split((float) shunted, matrix);
    }

    /** GameTest 用：这名玩家现在有没有待记的分流 */
    public static boolean hasPendingForTest(Player player) {
        for (Pending pending : PENDING) {
            if (pending.player() == player) {
                return true;
            }
        }
        return false;
    }
}
