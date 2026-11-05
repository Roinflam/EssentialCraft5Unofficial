package com.flameforge.essentialcraft5.player;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.api.IUBMRUGainModifyHandler;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.item.creative.CreativeWardItem;
import com.flameforge.essentialcraft5.matrix.MatrixCodex;
import com.flameforge.essentialcraft5.matrix.MatrixConfig;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * UBMRU 的获取与清零。对应原作 ECEventHandler.onKillEntity（L1060-1119）。
 * <ul>
 *   <li>真玩家（不是 FakePlayer）击杀非玩家生物：得到 {@code (20 + r × 10) × 目标基础最大生命}，
 *       r = nextFloat − nextFloat（原作 DummyCore MathUtils.randomFloat，(−1, 1) 的三角分布），即 10 ~ 30 倍、平均 20 倍。</li>
 *   <li>击杀真玩家：拿走对方全部 UBMRU。</li>
 *   <li>击杀非玩家生物所得大于 0 时，身上饰品依次修正（{@link IUBMRUGainModifyHandler}，原作查 Baubles 的 7 格，这里查全部 Curios 槽）。</li>
 *   <li>真玩家死亡（不论被谁杀）UBMRU 清零。先结算击杀者再清零，所以 PvP 时击杀者拿得到。</li>
 * </ul>
 * 移植改动：
 * <ul>
 *   <li>取目标<b>基础</b>最大生命（Attributes.MAX_HEALTH 的 base value），不算装备、药水、其它模组加的生命上限。</li>
 *   <li>加法按 long 算、夹到 int 上限（原作两个大数相加会绕回负数）；被杀玩家的 UBMRU 若为负按 0 算。</li>
 *   <li>最低优先级监听、不接收已取消的事件：别的模组取消了死亡（复活类效果）时，不给 UBMRU、也不清零。</li>
 *   <li>从玩家身上夺来的 UBMRU 不吃饰品修正，按原值转移。原作 PvP 夺取也乘饰品倍率：两人戴 ×2 的饰品轮流互杀，
 *       UBMRU 每杀一次翻一倍，二十来轮就到上限，灵魂石配矩阵换能器等于无限 MRU。</li>
 * </ul>
 * 第十三批 13a（batch13.md §3.1.1）：同一次结算里先记矩阵图谱（{@link MatrixCodex#record}），这一下让击杀者的图谱新记了一种时，
 * 击杀非玩家生物所得另加一份（{@link #firstKillBonus}，在饰品修正之后）。
 * <p>
 * 原作同一个监听器里的另外两件事分到了各自的模块：戴虚空头盔（原作 magicArmorItems[4]）杀怪回复 最大生命/10 的饱食度见 item/VoidArmorEvents，
 * 佩戴饰品 12（ε 腰带）击杀有 5% 掉一颗随机余烬见 ECVoid.VoidEmberEvents（第六批 6a）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class UBMRUEvents {

    private UBMRUEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!victim.level().isClientSide) {
            handleDeath(victim, event.getSource());
        }
    }

    /**
     * 结算一次死亡。事件处理和 GameTest 共用。
     * 注意：普通 Player（非 ServerPlayer）的 die 会连发两次死亡事件（Player.die 和 LivingEntity.die 各发一次），
     * 这里对玩家受害者是幂等的（第二次对方已清零，拿到 0）。
     */
    public static void handleDeath(LivingEntity victim, DamageSource source) {
        // 矩阵图谱先记（第十三批 13a）：这一下让击杀者的图谱多了一种，就是首杀，UBMRU 另加一份
        List<Player> firstKills = victim instanceof Player ? List.of() : MatrixCodex.record(victim, source);
        if (source.getEntity() instanceof Player killer && !(killer instanceof FakePlayer)) {
            IPlayerData killerData = PlayerDataEvents.get(killer);
            if (killerData != null) {
                long gained;
                if (victim instanceof Player victimPlayer && !(victim instanceof FakePlayer)) {
                    IPlayerData victimData = PlayerDataEvents.get(victimPlayer);
                    // 带过无尽护佑护符（只放创造栏，item/creative/CreativeWardItem）还没退回的人：护符补出来的那份不许夺走，
                    // 最多拿走戴上之前的数。看的是记录而不是身上有没有护符：刚摘下、还没到下一次检查时被杀也一样
                    long own = victimData == null ? 0 : Math.max(0, victimData.getUBMRU());
                    int base = CreativeWardItem.recordedBase(victimPlayer);
                    gained = base >= 0 ? Math.min(own, base) : own;
                } else {
                    gained = applyGainModifiers(killer, killReward(victim, killer.getRandom()));
                    if (firstKills.contains(killer)) {
                        gained += firstKillBonus(gained);
                    }
                }
                long total = (long) killerData.getUBMRU() + gained;
                killerData.setUBMRU((int) Math.max(0L, Math.min(total, Integer.MAX_VALUE)));
            }
        }
        if (victim instanceof Player player && !(victim instanceof FakePlayer)) {
            IPlayerData data = PlayerDataEvents.get(player);
            if (data != null) {
                data.setUBMRU(0);
            }
            CreativeWardItem.clearBase(player);
        }
    }

    /**
     * 击杀非玩家生物得到的 UBMRU：{@code (20 + r × 10) × 基础最大生命}，r ∈ (−1, 1)。
     * 原作 {@code addedEnergy += float} 是把 float 截断成 int；这里同样截断（Java 的 float → int 超界时饱和，不会绕回）。
     */
    public static int killReward(LivingEntity victim, RandomSource random) {
        float baseMaxHealth = (float) victim.getAttributeBaseValue(Attributes.MAX_HEALTH);
        float roll = random.nextFloat() - random.nextFloat();
        return Math.max(0, (int) ((20F + roll * 10F) * baseMaxHealth));
    }

    /**
     * 矩阵图谱首杀多给的 UBMRU：这一下（饰品修正之后）的所得 × {@code general.matrix_rank.first_kill_ubmru_multiplier}（默认 1，即翻倍），向下取整。
     */
    public static long firstKillBonus(long gained) {
        if (gained <= 0L) {
            return 0L;
        }
        double bonus = gained * MatrixConfig.firstKillUbmruMultiplier();
        return bonus > 0D && Double.isFinite(bonus) ? (long) Math.min(Integer.MAX_VALUE, Math.floor(bonus)) : 0L;
    }

    /** 饰品修正：每件依次修正、向下取整（原作 MathHelper.floor）。只在所得大于 0 时生效。 */
    private static long applyGainModifiers(Player killer, long gained) {
        if (gained <= 0) {
            return gained;
        }
        float[] value = {gained};
        CuriosCompat.forEachEquipped(killer, stack -> {
            if (stack.getItem() instanceof IUBMRUGainModifyHandler handler) {
                value[0] = Mth.floor(handler.getModifiedValue(value[0], stack, killer.getRandom(), killer));
            }
        });
        return Math.max(0L, (long) value[0]);
    }
}
