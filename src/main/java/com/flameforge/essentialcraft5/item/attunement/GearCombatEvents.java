package com.flameforge.essentialcraft5.item.attunement;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.network.GearFx;
import com.flameforge.essentialcraft5.network.S2CGearFxPacket;
import com.flameforge.essentialcraft5.registry.ECGearSounds;
import com.flameforge.essentialcraft5.util.ECLandedHit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 总闸结算（tools/plan/gear_growth.md 第 2 节）：本模组的增伤、减伤<b>只在这一处乘</b>。
 * <h2>{@code LivingHurtEvent} NORMAL（护甲之前）</h2>
 * <pre>
 * S = Σ 增伤来源（{@link GearPool#offense}）
 * S += 护甲穿透折算（不带 bypasses_armor 的伤害、主手穿透率 p &gt; 0）
 * S = max(0, S × offense_scale)
 * g = 主手同调刀剑属性栏那份攻击的倍数（{@link AttunementBonuses#bladeAttackFactor}，不是亲手主手刀剑打实是 1）；打玩家 g′ = 1 + (g − 1) × pvp_effect_scale，否则 g′ = g
 * 增伤倍数 = min(g′ × (1 + S), 1 + 上限[怪 / 首领 / 玩家]) ÷ g
 * R = min(Σ 减伤来源 × defense_scale, 上限[有攻击者 60% / 攻击者是别的玩家 40%])
 * amount × 增伤倍数 × (1 − R)
 * </pre>
 * 刀剑每阶的攻击加在属性栏上（amount 里已经乘过 g），总闸管的是「g × 池子」的乘积：被夹住时和搬过来之前（阶增伤在池里相加）一模一样，
 * 打玩家时属性那份照池里阶增伤的老规矩折半；没被夹住时是 g 和池子相乘，比相加略多。g 按物品自己算，横扫、附魔加的那几点不随属性涨，
 * 被夹住或打玩家时会跟着被除以 g，略少打一点（偏保守）。断罪按攻击属性出伤、伤害源是玩家近战，同样走这里。
 * 裁断 / 终审补伤（{@code essentialcraft5:verdict}）、范围技（{@code essentialcraft5:gear_burst}）跳过增伤池，减伤池照算。
 * 别的模组的乘数照常在外面各乘各的；本模组几样原样单乘、不进池的（η ×2、ι 拆 10%、σ 免伤、二分 ×0.5、首领档非近战 ×0.5、生物弱点 / 抗性、
 * 神圣重锤打亡灵、累加器并账、MRU 护盾）也照旧在各自的监听器里。
 * <p>
 * <b>护甲穿透</b>：d = amount × (1 + S)，a / t = 目标护甲 / 韧性，m = 吸收后伤害(d, a(1−p), t(1−p)) / 吸收后伤害(d, a, t)，S += m − 1，
 * 结果等于让原版护甲按少了 p 的护甲去算（护甲是在 NORMAL 之后才算的，只能这样折进前面的倍数）。
 *
 * <h2>{@code LivingDamageEvent} LOWEST（打实以后）</h2>
 * <ul>
 *   <li>按最大生命的补伤封顶（裁断、终审）：补之前用 {@link #capNext} 把封顶登记给那个伤害源对象，这里同一个对象再夹一次——中间不管谁乘了多少，
 *       最终掉血超不过封顶。已知管不到的：同在 LOWEST、排在这里后面的别的监听器再改。</li>
 *   <li>吸血：主手这一下亲手打实（{@link GearPool#ownWeapon}，{@code ECLandedHit.landed}），按这时的最终伤害 × 吸血率（目标是玩家再 × {@code lifesteal_player_scale}）
 *       记下（审判庭套装 2 阶打被定罪的目标另加 {@link ArmorGrowth#lifestealRate} 那份，同样只认亲手打实），本刻末过每秒桶（最大生命 × {@code lifesteal_per_second}；打玩家吸到的另过一个 {@code lifesteal_per_second_player} 的桶）再 {@code heal}
 *       （腐蚀照样回不了、二分照样减半）。</li>
 *   <li>累计打实伤害（战痕的战绩）。</li>
 * </ul>
 * 只在服务端；混合端上改的都是 {@code LivingHurtEvent} 的 amount，和以前各监听器的写法一样。
 */
public final class GearCombatEvents {

    /** 同一名玩家的穿透、吸血、斩杀事件包最多每这么多刻一个 */
    public static final int FX_INTERVAL = 10;
    /** 一刻最多记这么多条待结算的吸血 */
    public static final int MAX_PENDING = 512;

    /** 补伤的封顶（伤害源对象 → 封顶），本刻末清 */
    private static final Map<DamageSource, Float> CAPPED = new IdentityHashMap<>();
    private static final List<Heal> HEALS = new ArrayList<>();
    private static final Map<Player, Bucket> BUCKETS = new WeakHashMap<>();
    private static final Map<Player, long[]> FX_LAST = new WeakHashMap<>();

    private record Heal(ServerPlayer player, float amount, boolean pvp) {
    }

    /** 每秒桶：按刻回满，容量 = 最大生命 × 比例 */
    private static final class Bucket {
        double all = -1D;
        double pvp = -1D;
        long tick = Long.MIN_VALUE;
    }

    private GearCombatEvents() {
    }

    /**
     * 武器自己那几项增伤（{@code ECAttunement.init} 登记）：同调阶（{@code attunement_stage}，枪：阶 × {@code weapon.damage_per_stage}；
     * 刀剑的在属性栏上、这里是 0，见 {@link #factor}）、
     * 战痕（{@code battle_scar}）、斩杀（{@code execute}，词条表里有斩杀、目标是普通生物、当前生命低于门槛）。
     * 都只认亲手主手打实的同调武器（{@link GearPool#ownWeapon}）；打玩家乘 {@code pvp_effect_scale}。
     */
    public static void registerWeaponSources() {
        GearPool.registerOffense("attunement_stage", context -> context.weapon() == null ? 0D
                : GearStatSheet.get(context.weapon(), GearStat.DAMAGE) * context.pvpScale());
        GearPool.registerOffense("battle_scar", context -> context.weapon() == null ? 0D
                : GearStatSheet.scarDamage(context.weapon()) * context.pvpScale());
        GearPool.registerOffense("execute", context -> {
            ItemStack weapon = context.weapon();
            if (weapon == null || context.targetClass() != GearCaps.TargetClass.MOB) {
                return 0D;
            }
            double bonus = GearStatSheet.get(weapon, GearStat.EXECUTE);
            LivingEntity target = context.target();
            if (!(bonus > 0D) || !(target.getHealth() < target.getMaxHealth() * GearGrowthConfig.executeThreshold())) {
                return 0D;
            }
            if (context.attacker() instanceof ServerPlayer player) {
                fx(player, GearFx.EXECUTE, 2, target, context.amount());
            }
            return bonus;
        });
    }

    // ------------------------------------------------------------------
    // 结算
    // ------------------------------------------------------------------

    /** 一下伤害按池子算出的倍数（测试也直接调）；不改任何东西以外的副作用由各来源自己做 */
    public static double factor(GearPool.Context context) {
        double factor = 1D;
        DamageSource source = context.source();
        LivingEntity target = context.target();
        if (!GearPool.skipsOffense(source)) {
            double s = GearPool.offense(context).total();
            double pen = armorPenRate(context);
            if (pen > 0D && !source.is(DamageTypeTags.BYPASSES_ARMOR)) {
                double m = penetrationMultiplier(context.amount() * (1D + s), target, pen);
                if (m > 1D) {
                    s += m - 1D;
                    if (m > 1.02D && context.attacker() instanceof ServerPlayer player) {
                        fx(player, GearFx.ARMOR_PEN, 0, target, (float) m);
                    }
                }
            }
            s = Math.max(0D, s * Math.max(0D, GearGrowthConfig.offenseScale()));
            double g = attributeFactor(context);
            // 属性栏那份和池子一样吃 offense_scale，打玩家再按 pvp_effect_scale 折；除回去时用的是真实的 g
            double share = (g - 1D) * Math.max(0D, GearGrowthConfig.offenseScale());
            double counted = 1D + (target instanceof Player ? share * context.pvpScale() : share);
            factor *= Math.min(counted * (1D + s), 1D + Math.max(0D, GearCaps.poolCap(context.targetClass()))) / g;
        }
        double r = GearPool.defense(context).total();
        r = Math.max(0D, Math.min(r * Math.max(0D, GearGrowthConfig.defenseScale()), GearCaps.reductionCap(source.getEntity(), target)));
        factor *= 1D - r;
        return factor;
    }

    /**
     * 这一下的伤害里属性栏同调攻击那份是几倍（不是亲手近战、身上也没挂同调刀剑的攻击是 1）。
     * 取「按主手物品算」和「按玩家身上实际挂着的那条修饰符算」（{@link AttunementBonuses#appliedBladeFactor}）里大的那个：
     * 同一刻先切快捷栏再出手、配置热改后还没换手时，攻击属性里挂的还是之前那份，按主手算会漏；两者取大只会让总闸更保守。
     */
    public static double attributeFactor(GearPool.Context context) {
        double g = 1D;
        ItemStack weapon = context.weapon();
        if (weapon != null) {
            g = AttunementBonuses.bladeAttackFactor(weapon);
        }
        ServerPlayer melee = GearPool.ownMelee(context.source(), context.target());
        if (melee != null) {
            g = Math.max(g, AttunementBonuses.appliedBladeFactor(melee));
        }
        return g >= 1D && Double.isFinite(g) ? g : 1D;
    }

    /** 这一下的护甲穿透率（主手武器词条，过上限）；没有是 0 */
    public static double armorPenRate(GearPool.Context context) {
        ItemStack weapon = context.weapon();
        if (weapon == null) {
            return 0D;
        }
        double pen = GearStatSheet.get(weapon, GearStat.ARMOR_PEN);
        return Math.max(0D, Math.min(pen, GearCaps.armorPenCap(context.target())));
    }

    /** 护甲按少了 pen 去算时伤害多出几倍（原版公式的比值，≥ 1） */
    public static double penetrationMultiplier(double damage, LivingEntity target, double pen) {
        if (!(damage > 0D) || !(pen > 0D)) {
            return 1D;
        }
        float armor = target.getArmorValue();
        float toughness = (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        if (armor <= 0F) {
            return 1D;
        }
        float full = CombatRules.getDamageAfterAbsorb((float) damage, armor, toughness);
        float pierced = CombatRules.getDamageAfterAbsorb((float) damage, (float) (armor * (1D - pen)), (float) (toughness * (1D - pen)));
        return full > 0F ? Math.max(1D, pierced / (double) full) : 1D;
    }

    /** 登记补伤的封顶：source 这个对象这一下最终掉血不超过 cap（{@code LivingDamageEvent} LOWEST 里夹，本刻末清） */
    public static void capNext(DamageSource source, float cap) {
        if (cap > 0F && Float.isFinite(cap)) {
            CAPPED.put(source, cap);
        }
    }

    /** 吸血率（主手武器词条） */
    public static double lifestealRate(ItemStack weapon) {
        return GearStatSheet.get(weapon, GearStat.LIFESTEAL);
    }

    // ------------------------------------------------------------------
    // 事件（类名带模块前缀，见 tools/check_event_handlers.py）
    // ------------------------------------------------------------------

    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
    public static final class GearCombatHandlers {

        private GearCombatHandlers() {
        }

        /** 唯一的增伤 / 减伤结算（NORMAL，不收已取消的） */
        @SubscribeEvent
        public static void gearCombatHurt(LivingHurtEvent event) {
            LivingEntity target = event.getEntity();
            float amount = event.getAmount();
            if (target.level().isClientSide() || !(amount > 0F) || !Float.isFinite(amount)) {
                return;
            }
            double factor = factor(GearPool.context(event.getSource(), target, amount));
            if (factor != 1D && Double.isFinite(factor)) {
                event.setAmount((float) Math.max(0D, amount * factor));
            }
        }

        /** 补伤封顶、吸血、累计伤害（LOWEST，不收已取消的） */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void gearCombatDamage(LivingDamageEvent event) {
            LivingEntity target = event.getEntity();
            if (target.level().isClientSide()) {
                return;
            }
            DamageSource source = event.getSource();
            Float cap = CAPPED.get(source);
            if (cap != null && event.getAmount() > cap) {
                event.setAmount(cap);
            }
            if (!ECLandedHit.landed(event) || !(source.getEntity() instanceof ServerPlayer player)) {
                return;
            }
            ItemStack weapon = GearPool.ownWeapon(source, target);
            float dealt = Math.max(0F, event.getAmount());
            double rate = ArmorGrowth.lifestealRate(player, target, source);
            if (weapon != null) {
                BattleScars.noteDealt(player, weapon, dealt);
                rate += lifestealRate(weapon);
            }
            if (rate > 0D && dealt > 0F && HEALS.size() < MAX_PENDING) {
                boolean pvp = target instanceof Player;
                double amount = dealt * rate * (pvp ? Math.max(0D, GearGrowthConfig.lifestealPlayerScale()) : 1D);
                if (amount > 0D) {
                    HEALS.add(new Heal(player, (float) amount, pvp));
                }
            }
        }

        /** 武器击杀首领：首领击杀数 + 1（剑认近战、枪认射线，{@link GearPool#ownWeapon}） */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void gearCombatDeath(LivingDeathEvent event) {
            LivingEntity victim = event.getEntity();
            if (victim.level().isClientSide() || GearCaps.targetClass(victim) != GearCaps.TargetClass.BOSS) {
                return;
            }
            ItemStack weapon = GearPool.ownWeapon(event.getSource(), victim);
            if (weapon != null) {
                Attunement.addBossKill(weapon);
            }
        }

        @SubscribeEvent
        public static void gearCombatServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                flush();
            }
        }

        @SubscribeEvent
        public static void gearCombatServerStopping(ServerStoppingEvent event) {
            CAPPED.clear();
            HEALS.clear();
            BUCKETS.clear();
            FX_LAST.clear();
        }
    }

    /** 本刻末：清封顶登记，结算吸血。自动调；GameTest 在 hurt 之后直接调。返回这次一共回了多少 */
    public static float flush() {
        CAPPED.clear();
        if (HEALS.isEmpty()) {
            return 0F;
        }
        List<Heal> batch = new ArrayList<>(HEALS);
        HEALS.clear();
        float total = 0F;
        for (Heal heal : batch) {
            ServerPlayer player = heal.player();
            if (player.isRemoved() || !player.isAlive()) {
                continue;
            }
            float amount = take(player, heal.amount(), heal.pvp());
            if (amount > 0F) {
                float before = player.getHealth();
                player.heal(amount);
                float healed = player.getHealth() - before;
                if (healed > 0F) {
                    total += healed;
                    fx(player, GearFx.LIFESTEAL, 1, player, healed);
                }
            }
        }
        return total;
    }

    /** 从玩家的每秒桶里拿 want（打玩家吸到的两个桶都要过）；返回拿到的 */
    private static float take(ServerPlayer player, float want, boolean pvp) {
        Bucket bucket = BUCKETS.computeIfAbsent(player, p -> new Bucket());
        long now = now(player);
        double max = player.getMaxHealth();
        double capAll = max * Math.max(0D, GearGrowthConfig.lifestealPerSecond());
        double capPvp = max * Math.max(0D, GearGrowthConfig.lifestealPerSecondPlayer());
        if (bucket.all < 0D || bucket.tick == Long.MIN_VALUE) {
            bucket.all = capAll;
            bucket.pvp = capPvp;
        } else if (now > bucket.tick) {
            long elapsed = now - bucket.tick;
            bucket.all = Math.min(capAll, bucket.all + capAll * elapsed / 20D);
            bucket.pvp = Math.min(capPvp, bucket.pvp + capPvp * elapsed / 20D);
        }
        bucket.tick = now;
        double room = pvp ? Math.min(bucket.all, bucket.pvp) : bucket.all;
        double got = Math.max(0D, Math.min(want, room));
        bucket.all -= got;
        if (pvp) {
            bucket.pvp -= got;
        }
        return (float) got;
    }

    /** 测试：清掉这名玩家的吸血桶（下一次从满桶起） */
    public static void resetBucket(Player player) {
        BUCKETS.remove(player);
    }

    /** 还没结算的吸血条数（测试看） */
    public static int pendingHeals() {
        return HEALS.size();
    }

    /** 发一个限频的事件包（slot 区分几种：0 穿透、1 吸血、2 斩杀） */
    static void fx(ServerPlayer player, GearFx type, int slot, Entity at, float param) {
        long now = now(player);
        long[] last = FX_LAST.computeIfAbsent(player, p -> new long[]{Long.MIN_VALUE / 2, Long.MIN_VALUE / 2, Long.MIN_VALUE / 2});
        if (now - last[slot] < FX_INTERVAL) {
            return;
        }
        last[slot] = now;
        if (at.level() instanceof ServerLevel level) {
            S2CGearFxPacket.sendAround(level, type, at, at.position().add(0D, at.getBbHeight() * 0.5D, 0D), param);
            if (type == GearFx.EXECUTE) {
                ECGearSounds.play(level, at.position(), ECGearSounds.EXECUTE, SoundSource.PLAYERS, 0.6F, 1F);
            }
        }
    }

    private static long now(Entity entity) {
        MinecraftServer server = entity.getServer();
        return server != null ? server.getTickCount() : entity.level().getGameTime();
    }
}
