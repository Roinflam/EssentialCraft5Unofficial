package com.flameforge.essentialcraft5.corruption;

import com.flameforge.essentialcraft5.api.CorruptionEffects;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.api.MRUResistance;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

/**
 * MRU 腐化（原作 PotionMRUCorruption）：机器漏出来的 MRU 叠在附近玩家身上的后遗症（{@link Affliction#MRU_CORRUPTION}）。
 * 原作和第一版移植都是药水效果，牛奶一喝就没；现在记在玩家数据里，只能等它过去（{@link Afflictions}）。
 * <p>
 * 每 {@value #PULSE_INTERVAL} tick 以 等级/16 的概率对玩家造成一下魔法伤害（{@link #pulse}）。伤害量原作是 5 × 易伤系数 点；
 * 2026-10-13 起改成 最大生命 × {@code general.corruption.mru_corruption_health_percent}（默认 25%，{@link CorruptionEffects.HealthCost#MRU_CORRUPTION}）× 易伤系数
 * （{@link #vulnerability}）：整合包里玩家生命常常是几百上千，固定 5 点不疼不痒；原版 20 点生命时 25% 正好是 5 点，和原作一样。
 * 等级不是直接给的，而是由叠加时的剩余时长决定（见 {@link #stack}）：机器工作时每结算一次就往附近玩家身上叠一层，
 * 时长 +2 tick、等级 = 叠加前时长 / 2000，所以长时间守在机器旁边会越来越危险。
 * <p>
 * 原作戴着特殊饰品 10（ItemBaublesSpecial meta 10，γ 指环）时伤害改为回血（PotionMRUCorruption L36）：
 * 任意饰品槽里戴着物品标签 {@link #HEALS_FROM_MRU_CORRUPTION}（{@code essentialcraft5:heals_from_mru_corruption}）里的物品时，
 * 同样的数值（{@link #damage}）改成回血。标签由框架建（tools/tags/framework3c.json），γ 指环由 ECBaubles 注册并加进标签。
 * 查饰品走 {@code CuriosCompat.has(player, 标签)}（每玩家缓存，被配置 general.baubles.disabled 关掉的饰品不算）。
 * <p>
 * 打不死人（{@code general.corruption.mru_corruption_lethal} 默认关）：原作的泄漏只有附近本来就有聚集体时才生效（多半只在上古遗迹旁边），
 * 老玩家基本没见过；移植修好以后，守着干活的机器挂机十来分钟，等级就高到每秒都可能挨一下，饱食度一掉就死在机器旁边。
 * 现在和原版中毒一样最多打到剩 1 点：打之前按当时的生命少打一点，伤害真正落下时（{@code LivingDamageEvent} 最后一道）再由 {@link CappedHurt} 夹一次，
 * 别的模组按「环境伤害」加倍、混合端插件改了伤害事件，也打不到 1 点以下。
 */
public final class MRUCorruption {

    /** 原作第一次叠加时给的时长 */
    public static final int INITIAL_DURATION = 200;
    /** 多少 tick 掷一次骰（原作 isReady：duration % 20 == 0） */
    public static final int PULSE_INTERVAL = 20;
    /** 戴着（饰品槽）这个标签里的物品时 MRU 腐化改成回血（γ 指环） */
    public static final TagKey<Item> HEALS_FROM_MRU_CORRUPTION = ECTags.modItem("heals_from_mru_corruption");

    private MRUCorruption() {
    }

    /**
     * 掷一次骰：以 level/16 的概率扣一下血（戴 γ 指环时回血）。只在服务端；level ≥ 16 必中。
     * 正常由 {@link Afflictions} 每 {@value #PULSE_INTERVAL} tick 调一次，GameTest 直接调。
     */
    public static void pulse(Player player, int level) {
        if (player.level().isClientSide || player.getRandom().nextInt(16) >= level) {
            return;
        }
        float damage = damage(player);
        // 完全免疫时易伤为 0（比例配成 0 时同样）：原作照样 hurt(0)，不掉血但会触发一次 LivingAttackEvent、把肩上的鹦鹉赶下来（Player.hurt），这里直接跳过
        if (damage > 0F) {
            if (healsInstead(player)) {
                player.heal(damage);
            } else if (CorruptionEffects.mruCorruptionLethal()) {
                player.hurt(player.damageSources().magic(), damage);
            } else {
                // 像原版中毒一样打不死人：最多打到只剩 1 点（半颗心）。守着机器挂机的人不会因此死掉、东西撒一地。
                // 先按现在的生命少打一点，伤害真落下时再夹一次（别的模组、混合端插件在伤害事件里加成了也不超过）
                float allowed = Math.min(damage, CappedHurt.leavingOne(player.getHealth()));
                if (allowed > 0F) {
                    CappedHurt.hurtLeavingOne(player, player.damageSources().magic(), allowed);
                }
            }
        }
    }

    /**
     * 判中一次扣多少（戴 γ 指环时回多少）：当前最大生命 × {@link CorruptionEffects.HealthCost#MRU_CORRUPTION} × {@link #vulnerability}。
     * 还没算别的减免；默认打不死人时 {@link #pulse} 再按生命少打一点。
     */
    public static float damage(Player player) {
        return CorruptionEffects.HealthCost.MRU_CORRUPTION.of(player) * vulnerability(player);
    }

    /** 戴着 {@link #HEALS_FROM_MRU_CORRUPTION} 标签的饰品：MRU 腐化给的是回血而不是伤害 */
    public static boolean healsInstead(Player player) {
        return CuriosCompat.has(player, HEALS_FROM_MRU_CORRUPTION);
    }

    /**
     * 玩家对 MRU 腐化的易伤系数（原作 ECUtils.getGenResistance(0, player)）：1 − (四件盔甲的 MRU 腐化抗性之和 / 4 + 饰品的抗性)，最小 0。
     * 就是 {@link MRUResistance#vulnerability}(player, MRU_CORRUPTION)，抗性来自实现了 IMRUResistanceItem 的盔甲和饰品（魔力防护套装每件 0.25）。
     */
    public static float vulnerability(Player player) {
        return MRUResistance.vulnerability(player, MRUResistance.Type.MRU_CORRUPTION);
    }

    /**
     * 往 target 身上叠 times 层 MRU 腐化，等同于连续调 times 次原作 ECUtils.calculateAndAddMRUCorruptionPE：
     * 身上没有时先给 200 tick、0 级；之后每层时长 +2，等级 = 这一层叠加前的时长 / 2000（时长在走，等级也会跟着掉回去，照原作整个换掉）。
     * 不检查创造 / 旁观模式，由调用方判断。
     */
    public static void stack(Player target, int times) {
        IPlayerData data = PlayerDataEvents.get(target);
        if (times <= 0 || data == null) {
            return;
        }
        String id = Affliction.MRU_CORRUPTION.id();
        int current = data.getAfflictionTicks(id);
        int remaining = times;
        int duration;
        int level = 0;
        if (current <= 0) {
            duration = INITIAL_DURATION;
            remaining--;
        } else {
            duration = current;
        }
        if (remaining > 0) {
            // 最后一层叠加前的时长是 duration + 2 × (remaining − 1)
            level = (duration + 2 * (remaining - 1)) / 2000;
            duration += 2 * remaining;
        }
        data.setAffliction(id, duration, level);
    }
}
