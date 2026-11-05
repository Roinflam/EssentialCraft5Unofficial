package com.flameforge.essentialcraft5.gun;

import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.matrix.MatrixResonance;
import com.flameforge.essentialcraft5.registry.ECEffects;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

/**
 * 透镜的命中效果（第十批 10b，batch10.md §5.7.1；暗影第十一批补上）。以前透镜只改光束颜色（{@link GunStats#balance()}），现在四种矩阵投影各带一个机制：
 * <ul>
 *   <li><b>混沌</b>（balance 1）——腐蚀：目标 {@code lens_corrosion_ticks} 刻内回不了血（{@link ECEffects#CORROSION}，取消治疗由 {@code effect/EffectEvents} 做），
 *       每打中一下按原版规则刷新时长。</li>
 *   <li><b>冰冻</b>（balance 2）——冻结：每下给目标加 {@code lens_freeze_per_hit} 冰冻刻，最多到「冻僵所需 + {@value #FREEZE_OVERFLOW}」，
 *       冻满就是原版细雪那种冻僵（变慢、每 2 秒挨一下冻伤）。原版的 {@code canFreeze()} 照管：穿着皮革装备的、{@code freeze_immune_entity_types} 里的、
 *       旁观者都冻不上；冰冻刻原版每刻自己往回退 2，受击无敌又让同一个目标每 10 刻最多打实一下，所以要连着打才攒得起来（默认值见 {@link Config#DEFAULT_FREEZE_PER_HIT}）。</li>
 *   <li><b>魔力</b>（balance 3）——驱散：每下剥掉目标一个有益的状态效果（{@link #dispelOne}：无限时长的先剥，其余剥剩余时间最长的）。
 *       首领照样剥（只剥效果、不碰生命）；别的模组取消了移除事件就剥不掉，这一下算没剥。</li>
 *   <li><b>暗影</b>（balance 4）——吸取：把这一下最终扣到目标生命上的量（{@link RayHitGate.Watch#dealt}）的 {@code lens_drain_percent}% 回给射手，
 *       走 {@code heal}（射手自己挂着腐蚀时照样回不了）。打死目标的那一下也算。原作研究书写的是「暗影伤害」，代码是空的（{@code ShadeUtils}），这里补成吸取。</li>
 *   <li>没装透镜（0）：没有效果。</li>
 * </ul>
 * <b>只在伤害打实以后</b>（{@link #landed}）：射线的这一下要经过 {@link RayHitGate} 观察——攻击没被 PvP / 队伍 / 领地拦下、
 * {@code LivingDamageEvent} 到了且最终没被取消，另外 {@code LivingHurtEvent} 最终没被取消、伤害没被改成 0。最后一条是给 Mohist 系混合端的：
 * 别的模组在 {@code LivingHurtEvent} 里取消原伤害时，混合端按 0 照样发 Bukkit 事件和 {@code LivingDamageEvent(0)}，光看 {@code landed()} 会当成打实了。
 * 混合端插件拦下的那一下不发 {@code LivingDamageEvent}，什么效果都不加。射线伤害没有位置，盾挡不住，不用另查举盾。
 * <p>
 * 配置 {@code general.guns.lens_corrosion_ticks / lens_freeze_per_hit / lens_dispel / lens_drain_percent}（{@link Config}，挂在 {@link GunConfig} 的 {@code guns} 小节里）。
 * 效果的来源记成射手（{@code addEffect(…, 射手)}，{@code MobEffectEvent.Added} 里看得到是谁加的）。
 * <p>
 * <b>透镜同型</b>（第十三批 13a，batch13.md §3.2）：透镜和射手的矩阵属性相同、他的相性 ≥ I 时，腐蚀刻数、冰冻刻、吸取比例乘
 * {@code general.matrix_rank.resonance.same_type_lens_multiplier}（1.5，{@link MatrixResonance#lensMultiplier}）；目标是玩家时多出来的那部分乘
 * {@code pvp_effect_scale}（默认 ×1.25），透镜原有的强度对玩家不变。驱散一下只剥一个，不乘。
 * 这几样效果函数（{@link #corrode}、{@link #freeze}、{@link #dispelOne}、{@link #drain}）也给武器的矩阵特性用（{@code item/MatrixTraits}）。
 */
public final class LensEffects {

    /** 混沌透镜 */
    public static final int CHAOS = 1;
    /** 冰冻透镜 */
    public static final int FROZEN = 2;
    /** 魔力透镜（原作 id「pure」） */
    public static final int MAGIC = 3;
    /** 暗影透镜 */
    public static final int SHADE = 4;

    /** 冰冻刻最多加到「冻僵所需 + 这么多」：冻满以后还能撑两秒多（原版每刻退 2），连射时不会一直往上堆 */
    public static final int FREEZE_OVERFLOW = 40;

    /** 这一下加了什么（测试看） */
    public enum Applied {
        NONE, CORROSION, FREEZE, DISPEL, DRAIN
    }

    private LensEffects() {
    }

    /**
     * 服务端配置 {@code general.guns} 里的四项（{@link GunConfig#define} 在 push 之后调 {@link #define}）。读值走静态方法，没加载时是默认值；
     * GameTest 用 {@link #setTestOverrides}，同一段同步代码里设、测、置回 null。
     */
    public static final class Config {

        public static final int DEFAULT_CORROSION_TICKS = 60;
        /**
         * 默认 40（计划写的是 15）：目标挨了一下以后有 10 刻受击无敌，同一把枪对同一个目标每 10 刻最多打实一下，
         * 冰冻刻原版每刻退 2，10 刻退 20——每下只加 15 的话永远攒不起来；40 时连着打实 6 下（约 3 秒）冻僵
         */
        public static final int DEFAULT_FREEZE_PER_HIT = 40;
        public static final boolean DEFAULT_DISPEL = true;
        /** 加特林一秒能对同一个目标打实两下（受击无敌 10 刻），恶魔材料每下 26，默认 5% 约每秒回 2.6 */
        public static final double DEFAULT_DRAIN_PERCENT = 5D;

        private static ForgeConfigSpec.IntValue corrosionTicks;
        private static ForgeConfigSpec.IntValue freezePerHit;
        private static ForgeConfigSpec.BooleanValue dispel;
        private static ForgeConfigSpec.DoubleValue drainPercent;

        @Nullable
        private static volatile Integer testCorrosionTicks;
        @Nullable
        private static volatile Integer testFreezePerHit;
        @Nullable
        private static volatile Boolean testDispel;
        @Nullable
        private static volatile Double testDrainPercent;

        private Config() {
        }

        /** 写进 {@code general.guns}（调用方已经 push 好） */
        public static void define(ForgeConfigSpec.Builder builder) {
            corrosionTicks = builder.comment("Chaos lens: each hit that lands gives the target Corrosion (cannot heal) for this many ticks (20 = 1 second). 0 = off.",
                            "混沌透镜：每次打实给目标多少刻的腐蚀（回不了血，20 刻 = 1 秒）；0 = 关")
                    .defineInRange("lens_corrosion_ticks", DEFAULT_CORROSION_TICKS, 0, 1200);
            freezePerHit = builder.comment("Frozen lens: freeze ticks added per hit that lands; at 140 the target is frozen solid like in powder snow. "
                                    + "Freeze ticks wear off by 2 per tick and a target can take at most one hit every 10 ticks, so values of 20 or less never build up. 0 = off.",
                            "冰冻透镜：每次打实加多少冰冻刻；到 140 就像陷在细雪里一样冻僵。冰冻刻每刻退 2，同一个目标每 10 刻最多打实一下，填 20 以下永远攒不起来；0 = 关")
                    .defineInRange("lens_freeze_per_hit", DEFAULT_FREEZE_PER_HIT, 0, 200);
            dispel = builder.comment("Magic lens: each hit that lands removes one beneficial status effect from the target (infinite ones first, then the longest).",
                            "魔力透镜：每次打实剥掉目标一个有益的状态效果（无限时长的先剥，其余剥剩余时间最长的）")
                    .define("lens_dispel", DEFAULT_DISPEL);
            drainPercent = builder.comment("Shade lens: each hit that lands heals the shooter by this percent of the damage actually dealt. 0 = off.",
                            "暗影透镜：每次打实，把实际造成伤害的这个百分比回给射手；0 = 关")
                    .defineInRange("lens_drain_percent", DEFAULT_DRAIN_PERCENT, 0D, 100D);
        }

        public static int corrosionTicks() {
            Integer test = testCorrosionTicks;
            if (test != null) {
                return test;
            }
            return corrosionTicks == null ? DEFAULT_CORROSION_TICKS : ECConfig.get(corrosionTicks);
        }

        public static int freezePerHit() {
            Integer test = testFreezePerHit;
            if (test != null) {
                return test;
            }
            return freezePerHit == null ? DEFAULT_FREEZE_PER_HIT : ECConfig.get(freezePerHit);
        }

        public static boolean dispel() {
            Boolean test = testDispel;
            if (test != null) {
                return test;
            }
            return dispel == null ? DEFAULT_DISPEL : ECConfig.get(dispel);
        }

        public static double drainPercent() {
            Double test = testDrainPercent;
            if (test != null) {
                return test;
            }
            return drainPercent == null ? DEFAULT_DRAIN_PERCENT : ECConfig.get(drainPercent);
        }

        /** GameTest 用：前三项一起覆盖，吸取照配置；传 null = 照配置；测完全部置回 null */
        public static void setTestOverrides(@Nullable Integer corrosionTicks, @Nullable Integer freezePerHit, @Nullable Boolean dispel) {
            setTestOverrides(corrosionTicks, freezePerHit, dispel, null);
        }

        /** GameTest 用：四项一起覆盖，传 null = 照配置；测完全部置回 null */
        public static void setTestOverrides(@Nullable Integer corrosionTicks, @Nullable Integer freezePerHit, @Nullable Boolean dispel,
                                            @Nullable Double drainPercent) {
            testCorrosionTicks = corrosionTicks;
            testFreezePerHit = freezePerHit;
            testDispel = dispel;
            testDrainPercent = drainPercent;
        }
    }

    /** 这种透镜现在有没有命中效果（没有的话射线照旧直接 hurt，不用观察） */
    public static boolean active(int balance) {
        return switch (balance) {
            case CHAOS -> Config.corrosionTicks() > 0;
            case FROZEN -> Config.freezePerHit() > 0;
            case MAGIC -> Config.dispel();
            case SHADE -> Config.drainPercent() > 0D;
            default -> false;
        };
    }

    /**
     * 这一下算不算打实（见类注释）：{@code landed()} 之外，{@code LivingHurtEvent} 最终也没被取消、伤害没被改成 0。
     * 只在 {@code hurt} 返回以后看。
     */
    public static boolean landed(RayHitGate.Watch watch) {
        return watch.landed() && !watch.hurtZeroed();
    }

    /**
     * 射线打完一个目标以后调（{@link RayShot#strike}）：打实了就按透镜加效果。挂在目标身上的三种要目标还活着；吸取回给射手，打死的那一下也算。
     *
     * @return 加了什么（没打实、透镜没效果、目标冻不上 / 没有可剥的、射手满血都是 {@link Applied#NONE}）
     */
    public static Applied afterHit(LivingEntity shooter, LivingEntity target, int balance, RayHitGate.Watch watch) {
        if (!active(balance) || !landed(watch)) {
            return Applied.NONE;
        }
        double boost = MatrixResonance.lensMultiplier(shooter, target, balance);
        if (balance == SHADE) {
            return drain(shooter, watch.dealt(), Config.drainPercent() * boost) ? Applied.DRAIN : Applied.NONE;
        }
        if (!target.isAlive()) {
            return Applied.NONE;
        }
        return switch (balance) {
            case CHAOS -> corrode(target, shooter, boosted(Config.corrosionTicks(), boost)) ? Applied.CORROSION : Applied.NONE;
            case FROZEN -> freeze(target, boosted(Config.freezePerHit(), boost)) ? Applied.FREEZE : Applied.NONE;
            case MAGIC -> dispelOne(target) != null ? Applied.DISPEL : Applied.NONE;
            default -> Applied.NONE;
        };
    }

    /** 透镜同型时的刻数（四舍五入；倍数是 1 时原样） */
    private static int boosted(int ticks, double boost) {
        return boost == 1D || ticks <= 0 ? ticks : (int) Math.min(Integer.MAX_VALUE, Math.round(ticks * boost));
    }

    /**
     * 射手回 dealt × percent% 的血（{@code heal}，会被腐蚀之类取消治疗的效果拦下）。
     *
     * @return 血真的涨了（射手死了、满血、回血被取消、算出来是 0 都是 false）
     */
    public static boolean drain(LivingEntity shooter, float dealt, double percent) {
        if (!shooter.isAlive() || !(dealt > 0F) || !(percent > 0D)) {
            return false;
        }
        float amount = (float) (dealt * percent / 100D);
        if (!(amount > 0F)) {
            return false;
        }
        float before = shooter.getHealth();
        shooter.heal(amount);
        return shooter.getHealth() > before;
    }

    /** 给目标挂 ticks 刻腐蚀（来源记成 source）；效果没注册、目标不吃状态效果时 false */
    public static boolean corrode(LivingEntity target, @Nullable LivingEntity source, int ticks) {
        if (ticks <= 0 || !ECEffects.CORROSION.isPresent()) {
            return false;
        }
        return target.addEffect(new MobEffectInstance(ECEffects.CORROSION.get(), ticks, 0), source);
    }

    /**
     * 冰冻刻 += perHit，封顶「冻僵所需 + {@value #FREEZE_OVERFLOW}」。冻不上（{@code canFreeze()} 为假）时什么都不做、返回 false。
     */
    public static boolean freeze(LivingEntity target, int perHit) {
        if (perHit <= 0 || !target.canFreeze()) {
            return false;
        }
        int cap = target.getTicksRequiredToFreeze() + FREEZE_OVERFLOW;
        int before = target.getTicksFrozen();
        int after = Math.min(cap, before + perHit);
        if (after <= before) {
            return false;
        }
        target.setTicksFrozen(after);
        return true;
    }

    /**
     * 剥掉目标一个有益的状态效果：无限时长的先剥（不止一个时取第一个），没有就剥剩余时间最长的。
     *
     * @return 剥掉的效果；没有有益效果、或者移除被别的模组取消时为 null
     */
    @Nullable
    public static MobEffect dispelOne(LivingEntity target) {
        MobEffectInstance pick = null;
        for (MobEffectInstance instance : target.getActiveEffects()) {
            if (instance.getEffect().getCategory() != MobEffectCategory.BENEFICIAL) {
                continue;
            }
            if (pick == null || outranks(instance, pick)) {
                pick = instance;
            }
        }
        if (pick == null) {
            return null;
        }
        MobEffect effect = pick.getEffect();
        return target.removeEffect(effect) ? effect : null;
    }

    /** a 是不是比 b 更该先剥：无限时长优先，其余比剩余时间 */
    private static boolean outranks(MobEffectInstance a, MobEffectInstance b) {
        if (a.isInfiniteDuration() != b.isInfiniteDuration()) {
            return a.isInfiniteDuration();
        }
        return !a.isInfiniteDuration() && a.getDuration() > b.getDuration();
    }
}
