package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.attunement.Attunement;
import com.flameforge.essentialcraft5.item.attunement.GearCaps;
import com.flameforge.essentialcraft5.item.attunement.GearCombatEvents;
import com.flameforge.essentialcraft5.item.attunement.GearGrowthConfig;
import com.flameforge.essentialcraft5.item.bauble.BaubleEffects;
import com.flameforge.essentialcraft5.item.ember.ForgedSwordData;
import com.flameforge.essentialcraft5.item.ember.InlayEvents;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.mru.MRUItemStorage;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECEffects;
import com.flameforge.essentialcraft5.registry.ECHoloWeapons;
import com.flameforge.essentialcraft5.util.ECEntityRules;
import com.flameforge.essentialcraft5.util.ECHurtWindow;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import com.flameforge.essentialcraft5.network.S2CVerdictJudgmentPacket;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 裁决之刃（第十三批 13b，batch13.md §4.4，D11 A；限制见 policy §4.17）：近战的毕业武器，同调家族 {@code verdict_blade}（最高 5 阶）。
 * <p>
 * <b>获得</b>：魔力工作台升格配方 {@code recipes/attunement/verdict_promotion.json}，中心放同调 2 阶（攒满）的余烬元素剑，四角审判庭芯片、恶魔核心、永恒物质、
 * 矩阵结晶 ×2（13c 前兜底用审判庭芯片，§9.6），进度 200 000。产物保留锻造数据（元素、余烬）、附魔和剑里的 MRU，带上余烬剑累计同调度的一半（只进 0 阶、不跨阶，见
 * {@code Attunement.promoted}）。
 * <p>
 * <b>属性</b>：攻击 = 余烬元素剑的原值（主元素 + 封顶后的余烬加成）+ {@value #ATTACK_BONUS}，攻速同余烬剑 1.6，不掉耐久、能附魔，自带 {@value #MAX_MRU} MRU
 * （不是储存物品，不会被别的物品当电池抽）；每阶增伤 +8%、吸血、护甲穿透、斩杀、终审是成长加强的词条（{@code item/attunement/GearStatSheet}、{@link VerdictSeal}），
 * 进总闸的池子结算。命中、右键照余烬元素剑，倍数一律按余烬剑 2 阶算
 * （{@link #primaryDurationMultiplier} 等）。矩阵特性 ×{@code trait_multiplier}（1.5，登记在 {@code ECEmberForge.init}）。余烬嵌片嵌不进来（它本来就带余烬）。
 *
 * <h2>裁断：蓄满打实以后再补一下</h2>
 * 真玩家用主手的裁决之刃<b>蓄满</b>打实非玩家、非首领的目标（近战直接来源是他本人，横扫跟着主目标算，断罪的每一下都算蓄满；
 * 弹射物、饰品、尖刺圆盾、反伤都不算）：补 目标最大生命 × ({@code verdict.percent} + 阶 × {@code gear_growth.verdict_blade.percent_per_stage})%（3% + 1% / 阶；
 * 每阶的两项由 {@code general.gear_growth.verdict_blade} 接管，老键 {@code verdict.percent_per_stage} / {@code cap_per_stage} 不再读），
 * 单下最多 {@code verdict.cap} + 阶 × {@code gear_growth.verdict_blade.cap_per_stage}（40 + 20 / 阶）；每次 {@code verdict.mru}（200）MRU（{@code MRUDrain.playerUseMRU}，魔力相性折扣照算）。
 * 打玩家按 {@code verdict.pvp_percent}（默认 0，不打），百分比和封顶都不随阶涨（封顶只用 {@code verdict.cap}）。
 * <ul>
 *   <li><b>只认蓄满的一下</b>：{@code AttackEntityEvent}（{@code Player.attack} 开头、攻击蓄力归零之前）记下这名玩家这一下蓄满了没有
 *       （{@code getAttackStrengthScale(0.5F)} &gt; {@value #CHARGED_SCALE}，原版暴击、横扫的门槛；{@link #CHARGED}，本刻末清）。
 *       裁断是按目标最大生命给的固定一截，不随这一下的蓄力缩放；不看蓄力的话，连点（每下 0.2 倍伤害）每下都补满额，
 *       0 阶打 L2 50 级僵尸（约 520 生命）从满蓄力的约 107 点 / 秒涨到约 260 点 / 秒（每秒点 10 下）。
 *       不走 {@code Player.attack} 的伤害（别的模组的技能直接 hurt）不补。</li>
 *   <li><b>结算</b>：照 ι 指环的「本刻末补一下」（{@code BaubleCombatEvents.flushIota}）：{@code LivingDamageEvent} LOWEST 记下（没被取消、伤害 &gt; 0），
 *       本刻末（{@code ServerTickEvent} END）再核一次事件没被取消，付得起才打：记下目标的无敌窗口 → 清无敌帧 → 打 → 速度和同步标记放回（不再击退）
 *       → 无敌窗口原样放回（{@code util/ECHurtWindow}：目标等于只挨了原来那一下，接下来没蓄满的一下照原版被无敌窗口挡掉；补的那下被拦下时，
 *       原来那一下的无敌帧也不会白丢）。打成了（{@code hurt} 返回 true）才扣 MRU，被 PvP / 领地 / 混合端插件拦下的不扣（先算后扣）。
 *       同一刻同一名玩家对同一个目标只补一下。补的那下是另起的一次 hurt，照吃 NORMAL 层的各种增伤倍率（η 护身符、审判庭定罪等）。</li>
 *   <li><b>首领</b>（{@code #forge:bosses}）<b>不补</b>：别的模组的首领多把单次封顶写在 {@code hurt()} / {@code actuallyHurt()} 的覆写里
 *       （常见的夹到 20~32），在伤害事件里往这一下上加数，哪个优先级都越得过封顶；另起一下又会多吃一次封顶。
 *       对首领靠断罪和 5 阶的二分。</li>
 *   <li>伤害类型 {@code essentialcraft5:verdict}（{@link #VERDICT}）：进原版 {@code bypasses_armor}；伤害源带攻击者（持剑人）、不带直接实体和位置，
 *       PvP、队伍、领地、混合端插件都照攻击者管；不算武器命中（不再触发特性、余烬嵌刻、同调武器点，也不会再引出一下裁断）。</li>
 * </ul>
 *
 * <h2>断罪（同调 {@value #JUDGMENT_STAGE} 阶起）</h2>
 * 主手按住右键 {@code judgment.charge_ticks}（20）刻以上再松开（原版物品使用流程，{@link #releaseUsing}，不加包；按下时照样给副元素的右键效果）：
 * 自己周围 {@code judgment.radius}（6）格内最多 {@code judgment.max_targets}（16）个敌对生物（{@link Enemy} 或首领；跳过玩家、盟友、旁观者，按距离取近的）
 * 各挨一下玩家攻击伤害 = 当前攻击力 × {@code judgment.damage_multiplier}（1.0）（{@code playerAttack}：会触发矩阵特性，普通目标还会补裁断）。
 * 普通生物跳过 {@code ECEntityRules.isSpared}（起了名字、驯服、拴着绳……）；首领只跳过驯服的和拴着绳的，起了名字照打
 * （混合端上用指令、插件刷出来的首领多半带名字，按名字跳过的话断罪对它们整个失效）。
 * {@code damage_multiplier} 填 0 时断罪不开（{@link Outcome#DISABLED}，不扣、不冷却）。普通目标打之前清无敌帧，首领不清。
 * 5 阶起打中的首领挂 {@code judgment.bisect_ticks}（40）刻「二分」，{@code essentialcraft5:divider_immune} 里的不挂（同二分枪、二分场）。
 * 花 {@code judgment.mru}（20 000）MRU、冷却 {@code judgment.cooldown}（300）− 阶 × {@code judgment.cooldown_per_stage}（20）刻，3 阶起再按
 * {@code gear_growth.verdict_blade.judgment_cooldown_reduction}（20%，过 {@code caps.cooldown_reduction}）打折（原版物品冷却，冷却中按不了右键）。
 * 5 阶起打中的每个目标挂罪印（终审，{@link VerdictSeal}）。
 * 打成以后给看得见施放者的人发一个 {@link S2CVerdictJudgmentPacket}（施放者、范围、打实的目标），画面由客户端画；蓄力的画面客户端自己看，不发包。
 * 冷却不存盘，重登、死亡重生都清零，所以这两项的配置上限压在 {@value #MAX_COOLDOWN_TICKS} 刻；要更长的冷却得改成存玩家数据（同 θ 腰带）。
 * 先算后扣：开始前要付得起；一个都没打成（全被拦下）不扣、不冷却；范围里没有目标也不扣、不冷却。
 * 打完扣费时付不起的情形只有一种：打的过程中有东西当场花了同一批 MRU（目标的荆棘反伤打到自己，魔力防护当场从储存物品扣电），储存物品又正好卡在边上。
 * 伤害已经落下、收不回来，照常进冷却（下一次就付不起了，刷不起来），记一条调试日志。
 * 2 阶起右键不再格挡（动作换成蓄力），0~1 阶照余烬剑格挡。
 */
public class VerdictBladeItem extends ForgedElementalSwordItem {

    /** 自带的 MRU 上限 */
    public static final int MAX_MRU = 50_000;
    /** 攻击 = 余烬元素剑原值 + 这么多 */
    public static final int ATTACK_BONUS = 6;
    /** 断罪几阶解锁 */
    public static final int JUDGMENT_STAGE = 2;
    /** 断罪几阶起给首领挂二分 */
    public static final int BISECT_STAGE = 5;
    /** 一刻最多记这么多条待补的裁断 */
    public static final int MAX_PENDING = 256;
    /** 蓄力超过这个才算蓄满（原版暴击、横扫用的门槛） */
    public static final float CHARGED_SCALE = 0.9F;
    /** 断罪冷却两项配置的上限（刻）：冷却不存盘，重登、死亡清零，再长就没有意义 */
    public static final int MAX_COOLDOWN_TICKS = 1200;

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 裁断的伤害类型（定义在 data/essentialcraft5/damage_type/verdict.json，进原版 bypasses_armor） */
    public static final ResourceKey<DamageType> VERDICT = ResourceKey.create(Registries.DAMAGE_TYPE, EssentialCraft5.id("verdict"));

    // ------------------------------------------------------------------
    // 配置 general.verdict_blade
    // ------------------------------------------------------------------

    /** 服务端配置 {@code general.verdict_blade}（{@code ECEmberForge.init} 登记） */
    public static final class Config {

        /**
         * 一套数值。
         *
         * @param traitMultiplier      矩阵特性倍数
         * @param verdictPercent       裁断：目标最大生命的百分之几（0 阶）
         * @param verdictPercentStage  裁断：每阶再加百分之几
         * @param verdictCap           裁断：单下最多多少（0 阶；打玩家时不随阶涨）
         * @param verdictCapStage      裁断：每阶封顶再加多少
         * @param verdictMru           裁断：每次多少 MRU（0 = 不花）
         * @param verdictPvpPercent    裁断：打玩家时最大生命的百分之几（0 = 不打玩家）
         * @param chargeTicks          断罪：要按住多少刻
         * @param radius               断罪：半径（格）
         * @param maxTargets           断罪：最多打几个
         * @param damageMultiplier     断罪：每个目标挨 当前攻击力 × 这个
         * @param judgmentMru          断罪：每次多少 MRU（0 = 不花）
         * @param cooldown             断罪：冷却（刻，0 阶）
         * @param cooldownStage        断罪：每阶冷却少多少刻
         * @param bisectTicks          断罪：5 阶起给首领挂几刻二分（0 = 不挂）
         */
        public record Values(double traitMultiplier, double verdictPercent, double verdictPercentStage,
                             double verdictCap, double verdictCapStage, int verdictMru, double verdictPvpPercent,
                             int chargeTicks, double radius, int maxTargets, double damageMultiplier, int judgmentMru,
                             int cooldown, int cooldownStage, int bisectTicks) {
        }

        /** batch13.md §4.4 的数；每阶的两项是成长加强的新默认（{@code general.gear_growth.verdict_blade}） */
        public static final Values DEFAULTS = new Values(1.5D, 3D, 1D, 40D, 20D, 200, 0D,
                20, 6D, 16, 1D, 20_000, 300, 20, 40);

        private static ForgeConfigSpec.DoubleValue traitMultiplier;
        private static ForgeConfigSpec.DoubleValue verdictPercent;
        private static ForgeConfigSpec.DoubleValue verdictPercentStage;
        private static ForgeConfigSpec.DoubleValue verdictCap;
        private static ForgeConfigSpec.DoubleValue verdictCapStage;
        private static ForgeConfigSpec.IntValue verdictMru;
        private static ForgeConfigSpec.DoubleValue verdictPvpPercent;
        private static ForgeConfigSpec.IntValue chargeTicks;
        private static ForgeConfigSpec.DoubleValue radius;
        private static ForgeConfigSpec.IntValue maxTargets;
        private static ForgeConfigSpec.DoubleValue damageMultiplier;
        private static ForgeConfigSpec.IntValue judgmentMru;
        private static ForgeConfigSpec.IntValue cooldown;
        private static ForgeConfigSpec.IntValue cooldownStage;
        private static ForgeConfigSpec.IntValue bisectTicks;
        @Nullable
        private static volatile Values testOverride;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.comment("Verdict Blade.", "裁决之刃").push("verdict_blade");
            traitMultiplier = builder.comment("Matrix Trait strength multiplier for this blade (other attuned weapons are x1).",
                            "这把剑的矩阵特性倍数（别的带特性的武器是 ×1）")
                    .defineInRange("trait_multiplier", DEFAULTS.traitMultiplier(), 0D, 10D);

            builder.comment("Verdict: after a fully charged hit lands, one more armor-piercing strike at the end of the tick, based on the target's max health. "
                    + "Bosses (#forge:bosses) never get it.", "裁断：蓄满打实以后本刻末按目标最大生命再补一下（无视护甲）；首领（#forge:bosses）不补").push("verdict");
            verdictPercent = builder.comment("Percent of the target's max health at attunement stage 0.", "0 阶时是目标最大生命的百分之几")
                    .defineInRange("percent", DEFAULTS.verdictPercent(), 0D, 100D);
            verdictPercentStage = builder.comment("No longer read: replaced by general.gear_growth.verdict_blade.percent_per_stage.",
                            "已由 general.gear_growth.verdict_blade.percent_per_stage 接管，不再读")
                    .defineInRange("percent_per_stage", 0.5D, 0D, 100D);
            verdictCap = builder.comment("Cap per strike at stage 0. 0 = Verdict never triggers.", "0 阶时单下最多多少；0 = 不触发")
                    .defineInRange("cap", DEFAULTS.verdictCap(), 0D, 100_000D);
            verdictCapStage = builder.comment("No longer read: replaced by general.gear_growth.verdict_blade.cap_per_stage.",
                            "已由 general.gear_growth.verdict_blade.cap_per_stage 接管，不再读")
                    .defineInRange("cap_per_stage", 10D, 0D, 100_000D);
            verdictMru = builder.comment("MRU spent per strike, only when it lands. 0 = free.", "每补一下花多少 MRU，打成了才扣；0 = 不花")
                    .defineInRange("mru", DEFAULTS.verdictMru(), 0, 1_000_000);
            verdictPvpPercent = builder.comment("Percent of max health against players (PvP, teams and claims still apply). "
                                    + "Neither this nor the cap grows with the stage against players. 0 = never against players.",
                            "打玩家时是最大生命的百分之几（PvP、队伍、领地照常管）；对玩家这个百分比和封顶都不随阶涨；0 = 不打玩家")
                    .defineInRange("pvp_percent", DEFAULTS.verdictPvpPercent(), 0D, 100D);
            builder.pop();

            builder.comment("Judgment (attunement stage 2+): hold use, release to strike every hostile creature around you.",
                    "断罪（同调 2 阶起）：按住右键蓄力，松开打周围的敌对生物").push("judgment");
            chargeTicks = builder.comment("Ticks to hold before releasing (20 = 1 second).", "要按住多少刻（20 = 1 秒）")
                    .defineInRange("charge_ticks", DEFAULTS.chargeTicks(), 1, 200);
            radius = builder.comment("Radius in blocks.", "半径（格）")
                    .defineInRange("radius", DEFAULTS.radius(), 1D, 16D);
            maxTargets = builder.comment("Most creatures hit per Judgment (nearest first).", "一次最多打几个（先打近的）")
                    .defineInRange("max_targets", DEFAULTS.maxTargets(), 1, 64);
            damageMultiplier = builder.comment("Each creature takes your current attack damage times this. 0 = Judgment is off.", "每个目标挨 当前攻击力 × 这个；0 = 断罪不开")
                    .defineInRange("damage_multiplier", DEFAULTS.damageMultiplier(), 0D, 10D);
            judgmentMru = builder.comment("MRU spent per Judgment, only when at least one hit lands. 0 = free.", "每次花多少 MRU，至少打成一下才扣；0 = 不花")
                    .defineInRange("mru", DEFAULTS.judgmentMru(), 0, 10_000_000);
            cooldown = builder.comment("Cooldown in ticks at stage 0. 0 = none. The cooldown is not saved (logging out or dying clears it), so it stops at 1200 (60 s).",
                            "0 阶时的冷却（刻）；0 = 没有冷却。冷却不存盘（重登、死亡清零），所以最多 1200 刻（60 秒）")
                    .defineInRange("cooldown", DEFAULTS.cooldown(), 0, MAX_COOLDOWN_TICKS);
            cooldownStage = builder.comment("Cooldown reduction per attunement stage, in ticks.", "每阶冷却少多少刻")
                    .defineInRange("cooldown_per_stage", DEFAULTS.cooldownStage(), 0, MAX_COOLDOWN_TICKS);
            bisectTicks = builder.comment("From stage 5: bosses hit by Judgment get Bisected for this many ticks. 0 = never.",
                            "5 阶起打中的首领挂这么多刻「二分」；0 = 不挂")
                    .defineInRange("bisect_ticks", DEFAULTS.bisectTicks(), 0, 6000);
            builder.pop();
            builder.pop();
        }

        /** 现在的数值：测试覆盖值 → 配置（没加载时默认值） */
        public static Values values() {
            Values override = testOverride;
            if (override != null) {
                return override;
            }
            if (traitMultiplier == null) {
                return new Values(DEFAULTS.traitMultiplier(), DEFAULTS.verdictPercent(), GearGrowthConfig.verdictPercentPerStage(),
                        DEFAULTS.verdictCap(), GearGrowthConfig.verdictCapPerStage(), DEFAULTS.verdictMru(), DEFAULTS.verdictPvpPercent(),
                        DEFAULTS.chargeTicks(), DEFAULTS.radius(), DEFAULTS.maxTargets(), DEFAULTS.damageMultiplier(), DEFAULTS.judgmentMru(),
                        DEFAULTS.cooldown(), DEFAULTS.cooldownStage(), DEFAULTS.bisectTicks());
            }
            // 每阶的两项读 general.gear_growth（老键留着定义、不再读）
            return new Values(ECConfig.get(traitMultiplier), ECConfig.get(verdictPercent), GearGrowthConfig.verdictPercentPerStage(),
                    ECConfig.get(verdictCap), GearGrowthConfig.verdictCapPerStage(), ECConfig.get(verdictMru),
                    ECConfig.get(verdictPvpPercent), ECConfig.get(chargeTicks), ECConfig.get(radius), ECConfig.get(maxTargets),
                    ECConfig.get(damageMultiplier), ECConfig.get(judgmentMru), ECConfig.get(cooldown), ECConfig.get(cooldownStage),
                    ECConfig.get(bisectTicks));
        }

        /** 矩阵特性倍数（{@code MatrixTraits.registerWeaponMultiplier} 用） */
        public static double traitMultiplier() {
            return values().traitMultiplier();
        }

        /** GameTest 用：整套换成给定的数值，null = 回到配置。同一段同步代码里设、测、置回 */
        public static void setTestOverrides(@Nullable Values values) {
            testOverride = values;
        }
    }

    public VerdictBladeItem(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new MRUItemStorage(stack, MAX_MRU);
    }

    // ------------------------------------------------------------------
    // 属性：余烬剑原值 + 6
    // ------------------------------------------------------------------

    /** 主手攻击加成（玩家基础的 1 点另算）：余烬剑原值（没锻造的 0）+ {@value #ATTACK_BONUS} */
    public static int attackBonus(ItemStack stack) {
        return ForgedSwordData.read(stack).map(ForgedElementalSwordItem::attackBonus).orElse(0) + ATTACK_BONUS;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        return slot == EquipmentSlot.MAINHAND ? modifiers(Math.max(0, attackBonus(stack))) : ImmutableMultimap.of();
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? modifiers(ATTACK_BONUS) : ImmutableMultimap.of();
    }

    // ------------------------------------------------------------------
    // 余烬剑的阶效果：一律按 2 阶
    // ------------------------------------------------------------------

    @Override
    public double primaryDurationMultiplier(ItemStack stack) {
        return swordStages().stage1Duration();
    }

    @Override
    public double emberCostMultiplier(ItemStack stack) {
        return swordStages().stage2EmberCost();
    }

    @Override
    public double emberDurationMultiplier(ItemStack stack) {
        return swordStages().stage2EmberDuration();
    }

    private static ForgedElementalSwordItem.Config.StageValues swordStages() {
        return ForgedElementalSwordItem.Config.stageValues();
    }

    /**
     * 同调提示框里裁决之刃各阶那一行：裁断的比例、单下上限、断罪冷却、二分秒数都按现在的配置算（和 {@link #appendHoverText} 里那几行同一算法），
     * 服主改了 {@code verdict.*}、{@code judgment.*} 也对得上。5 阶二分填 0 时那一阶照 3、4 阶的写法。
     * 裁断算出来是 0（比例或上限填 0）时只写断罪；断罪关掉（{@code damage_multiplier} 0，见 {@link #judgment}）时只写裁断，
     * 二分只由断罪挂，也一起不写；两样都没有的那一阶不写。
     */
    @Override
    @Nullable
    public Component attunementStageLine(ItemStack stack, int stage) {
        if (stage < 1) {
            return null;
        }
        Config.Values values = Config.values();
        double percentValue = values.verdictPercent() + values.verdictPercentStage() * stage;
        double capValue = values.verdictCap() + values.verdictCapStage() * stage;
        boolean verdict = percentValue > 0D && capValue > 0D;
        boolean judgment = stage >= JUDGMENT_STAGE && values.damageMultiplier() > 0D;
        String percent = decimal(percentValue);
        String cap = decimal(capValue);
        String cooldown = decimal(judgmentCooldown(stage, values) / 20D);
        String prefix = "tooltip.essentialcraft5.attunement.verdict_blade.";
        if (!judgment) {
            return verdict ? Component.translatable(prefix + "stage_1", percent, cap) : null;
        }
        boolean bisect = stage >= BISECT_STAGE && values.bisectTicks() > 0;
        String bisectSeconds = decimal(values.bisectTicks() / 20D);
        if (!verdict) {
            if (stage == JUDGMENT_STAGE) {
                return Component.translatable(prefix + "stage_2.judgment_only", cooldown);
            }
            return bisect ? Component.translatable(prefix + "stage_5.judgment_only", bisectSeconds, cooldown)
                    : Component.translatable(prefix + "stage_3.judgment_only", cooldown);
        }
        if (stage == JUDGMENT_STAGE) {
            return Component.translatable(prefix + "stage_2", cooldown, percent, cap);
        }
        if (bisect) {
            return Component.translatable(prefix + "stage_5", bisectSeconds, percent, cap, cooldown);
        }
        // 3、4 阶（和二分关掉时的 5 阶）是同一种写法
        return Component.translatable(prefix + (stage == 3 ? "stage_3" : "stage_4"), percent, cap, cooldown);
    }

    // ------------------------------------------------------------------
    // 右键：0~1 阶格挡，2 阶起蓄力断罪
    // ------------------------------------------------------------------

    /** 断罪解锁了没有（同调 {@value #JUDGMENT_STAGE} 阶起） */
    public static boolean judgmentUnlocked(ItemStack stack) {
        return Attunement.stage(stack) >= JUDGMENT_STAGE;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return judgmentUnlocked(stack) ? UseAnim.SPEAR : UseAnim.BLOCK;
    }

    /** 2 阶起右键是蓄力，不再当盾格挡（原版按 UseAnim.BLOCK 和这个动作判格挡） */
    @Override
    public boolean canPerformAction(ItemStack stack, ToolAction toolAction) {
        if (judgmentUnlocked(stack)) {
            return ToolActions.DEFAULT_SWORD_ACTIONS.contains(toolAction);
        }
        return super.canPerformAction(stack, toolAction);
    }

    /** 松开右键：主手、按够了刻数、解锁了才放断罪（只在服务端） */
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player) || player.getUsedItemHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        int held = getUseDuration(stack) - timeLeft;
        if (held < Config.values().chargeTicks() || !judgmentUnlocked(stack)) {
            return;
        }
        Judgment result = judgment(player, stack);
        Component message = result.message(Config.values());
        if (message != null) {
            player.displayClientMessage(message, true);
        }
    }

    /** 蓄满那一刻给本人一点声音（服务端发，客户端不用自己算） */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player) || !judgmentUnlocked(stack)
                || getUseDuration(stack) - remaining != Config.values().chargeTicks() || player.getCooldowns().isOnCooldown(this)) {
            return;
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.4F);
    }

    // ------------------------------------------------------------------
    // 断罪
    // ------------------------------------------------------------------

    /** 一次断罪的结果种类（DISABLED = {@code judgment.damage_multiplier} 填了 0，断罪不开） */
    public enum Outcome {
        DONE, LOCKED, DISABLED, COOLDOWN, NO_MRU, NO_TARGET, BLOCKED
    }

    /** 一次断罪的结果（测试看；{@link #message} 给动作栏）：landed = 打成了几下 */
    public record Judgment(Outcome outcome, int landed) {

        static Judgment of(Outcome outcome) {
            return new Judgment(outcome, 0);
        }

        @Nullable
        Component message(Config.Values values) {
            return switch (outcome) {
                case DONE -> Component.translatable("message.essentialcraft5.verdict_blade.judgment_done", landed).withStyle(ChatFormatting.GOLD);
                case DISABLED -> Component.translatable("message.essentialcraft5.verdict_blade.judgment_disabled").withStyle(ChatFormatting.GRAY);
                case NO_MRU -> Component.translatable("message.essentialcraft5.verdict_blade.judgment_no_mru", values.judgmentMru())
                        .withStyle(ChatFormatting.RED);
                case NO_TARGET -> Component.translatable("message.essentialcraft5.verdict_blade.judgment_no_target", decimal(values.radius()))
                        .withStyle(ChatFormatting.GRAY);
                case BLOCKED -> Component.translatable("message.essentialcraft5.verdict_blade.judgment_blocked").withStyle(ChatFormatting.GRAY);
                default -> null;
            };
        }
    }

    /** 正在放断罪的玩家（服务端线程）：断罪打的每一下都算蓄满 */
    @Nullable
    private static UUID judging;

    /**
     * 放一次断罪（GameTest 直接调；不看按了多久，只看阶、冷却、MRU、目标）。见类注释。
     */
    public static Judgment judgment(ServerPlayer player, ItemStack blade) {
        int stage = Attunement.stage(blade);
        if (stage < JUDGMENT_STAGE) {
            return Judgment.of(Outcome.LOCKED);
        }
        Config.Values values = Config.values();
        if (!(values.damageMultiplier() > 0D)) {
            return Judgment.of(Outcome.DISABLED);
        }
        if (player.getCooldowns().isOnCooldown(blade.getItem())) {
            return Judgment.of(Outcome.COOLDOWN);
        }
        int cost = values.judgmentMru();
        if (cost > 0 && !MRUDrain.canAfford(player, blade, cost)) {
            return Judgment.of(Outcome.NO_MRU);
        }
        List<LivingEntity> targets = judgmentTargets(player, values.radius(), values.maxTargets());
        if (targets.isEmpty()) {
            return Judgment.of(Outcome.NO_TARGET);
        }
        float damage = (float) (player.getAttributeValue(Attributes.ATTACK_DAMAGE) * values.damageMultiplier());
        ServerLevel level = player.serverLevel();
        int landed = 0;
        List<LivingEntity> hit = new ArrayList<>(targets.size());
        judging = player.getUUID();
        try {
            for (LivingEntity target : targets) {
                boolean boss = isBoss(target);
                if (!boss) {
                    target.invulnerableTime = 0;
                }
                if (!(damage > 0F) || !target.hurt(player.damageSources().playerAttack(player), damage)) {
                    continue;
                }
                landed++;
                hit.add(target);
                if (stage >= GearGrowthConfig.finalVerdictStage()) {
                    VerdictSeal.mark(player, target);
                }
                if (boss && stage >= BISECT_STAGE && values.bisectTicks() > 0 && target.isAlive() && !bisectImmune(target.getType())
                        && ECEffects.BISECTED.isPresent()) {
                    target.addEffect(new MobEffectInstance(ECEffects.BISECTED.get(), values.bisectTicks(), 0), player);
                }
            }
        } finally {
            judging = null;
        }
        if (landed == 0) {
            return Judgment.of(Outcome.BLOCKED);
        }
        if (cost > 0 && !MRUDrain.playerUseMRU(player, blade, cost)) {
            LOGGER.debug("[EC5] 断罪打完扣不起 {} MRU（打的过程中同一批 MRU 被别的东西花掉了），玩家 {}", cost, player.getGameProfile().getName());
        }
        int ticks = judgmentCooldown(stage, values);
        if (ticks > 0) {
            player.getCooldowns().addCooldown(blade.getItem(), ticks);
        }
        S2CVerdictJudgmentPacket.sendAround(player, values.radius(), hit);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1F, 0.6F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1F, 0.8F);
        return new Judgment(Outcome.DONE, landed);
    }

    /**
     * 冷却刻数：cooldown − 阶 × cooldown_per_stage（不小于 0），再按这一阶的 {@code gear_growth.verdict_blade.judgment_cooldown_reduction}
     * 打折（过 {@code caps.cooldown_reduction}），四舍五入
     */
    public static int judgmentCooldown(int stage, Config.Values values) {
        int base = Math.max(0, values.cooldown() - values.cooldownStage() * Math.max(0, stage));
        return GearCaps.reducedCooldown(base, GearGrowthConfig.verdictJudgmentCdr(stage));
    }

    /** 这种生物挂不上断罪的二分：{@code essentialcraft5:divider_immune}（同二分枪的首领模式、二分场） */
    public static boolean bisectImmune(EntityType<?> type) {
        return type.is(ECHoloWeapons.DIVIDER_IMMUNE);
    }

    /**
     * 断罪打谁：半径内的敌对生物或首领，跳过玩家、盟友、旁观者、已死的，以及 {@link #judgmentSpared} 的；按距离从近到远取前 max 个。
     */
    public static List<LivingEntity> judgmentTargets(Player player, double radius, int max) {
        double r2 = radius * radius;
        AABB box = player.getBoundingBox().inflate(radius);
        List<LivingEntity> found = player.level().getEntitiesOfClass(LivingEntity.class, box, entity -> entity != player && entity.isAlive()
                && !(entity instanceof Player) && !entity.isSpectator() && (entity instanceof Enemy || isBoss(entity))
                && !judgmentSpared(entity) && !entity.isAlliedTo(player) && entity.distanceToSqr(player) <= r2);
        found.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(player)));
        return found.size() > max ? new ArrayList<>(found.subList(0, Math.max(0, max))) : found;
    }

    /**
     * 断罪放过的：普通生物照 {@code ECEntityRules.isSpared}（起了名字、驯服、拴着绳……）；首领只放过驯服的和拴着绳的，起了名字照打
     * （混合端上用指令、插件刷出来的首领多半带名字）。
     */
    public static boolean judgmentSpared(LivingEntity entity) {
        if (isBoss(entity)) {
            return ECEntityRules.isTamed(entity) || entity instanceof Mob mob && mob.isLeashed();
        }
        return ECEntityRules.isSpared(entity);
    }

    // ------------------------------------------------------------------
    // 裁断
    // ------------------------------------------------------------------

    /** GameTest 用的首领判定覆盖；null = 看 {@code #forge:bosses} */
    @Nullable
    private static volatile Predicate<EntityType<?>> testBossOverride;

    /** 算不算首领：{@code #forge:bosses}；GameTest 设了覆盖时按覆盖算 */
    public static boolean isBoss(LivingEntity entity) {
        Predicate<EntityType<?>> override = testBossOverride;
        return override != null ? override.test(entity.getType()) : entity.getType().is(Tags.EntityTypes.BOSSES);
    }

    /** GameTest 用：换掉首领判定（null = 恢复看 {@code #forge:bosses}）。只在同一段同步代码里设、测、置回 null */
    public static void setTestBossOverride(@Nullable Predicate<EntityType<?>> override) {
        testBossOverride = override;
    }

    /**
     * 这把剑对 target 补多少（0 = 不补）：最大生命 × 百分比，按封顶夹。首领一律 0（见类注释）；
     * 打玩家用 {@code pvp_percent}、封顶只用 {@code verdict.cap}，两样都不随阶涨。
     */
    public static float verdictAmount(ItemStack blade, LivingEntity target) {
        if (isBoss(target)) {
            return 0F;
        }
        Config.Values values = Config.values();
        int stage = Attunement.stage(blade);
        boolean player = target instanceof Player;
        double percent = player ? values.verdictPvpPercent() : values.verdictPercent() + values.verdictPercentStage() * stage;
        double cap = verdictCap(blade, target);
        if (!(percent > 0D) || !(cap > 0D)) {
            return 0F;
        }
        double amount = Math.min(target.getMaxHealth() * percent / 100D, cap);
        return amount > 0D ? (float) amount : 0F;
    }

    /** 这把剑对 target 的裁断封顶：打玩家只用 {@code verdict.cap}，其余 cap + 阶 × cap_per_stage（总闸按它夹这一下的最终掉血） */
    public static float verdictCap(ItemStack blade, LivingEntity target) {
        Config.Values values = Config.values();
        double cap = target instanceof Player ? values.verdictCap() : values.verdictCap() + values.verdictCapStage() * Attunement.stage(blade);
        return cap > 0D ? (float) cap : 0F;
    }

    /** 裁断的伤害源：带攻击者（持剑人），不带直接实体和位置 */
    public static DamageSource verdictSource(Player player) {
        return new DamageSource(ECDamageTypes.holder(player.level(), VERDICT), null, player);
    }

    /**
     * 这一下是不是真玩家用主手的裁决之刃、蓄满了打的（近战直接来源是他本人；饰品、尖刺圆盾、反伤、裁断自己都不算；
     * 蓄满看 {@link #CHARGED}，正在放断罪的那名玩家都算）；是就返回他
     */
    @Nullable
    public static ServerPlayer bladeAttacker(DamageSource source, LivingEntity target) {
        if (source.is(VERDICT) || !(source.getEntity() instanceof ServerPlayer player) || player == target || !ECProtection.isRealPlayer(player)
                || BaubleEffects.isBaubleDamage() || InlayEvents.isNotWeaponHit()) {
            return null;
        }
        ItemStack blade = player.getMainHandItem();
        if (!(blade.getItem() instanceof VerdictBladeItem) || !InlayEvents.weaponHit(source, player, blade)) {
            return null;
        }
        UUID id = player.getUUID();
        return CHARGED.contains(id) || id.equals(judging) ? player : null;
    }

    /** 这一刻蓄满了攻击、主手拿着裁决之刃的玩家（{@link #noteSwing} 记，本刻末清） */
    private static final Set<UUID> CHARGED = new HashSet<>();

    /**
     * {@code AttackEntityEvent}：{@code Player.attack} 开头、攻击蓄力归零之前，记下这一下蓄满了没有。没蓄满的、主手不是裁决之刃的从表里拿掉
     * （同一刻再挥一下时，前一下的伤害事件已经记过了，不受影响）。横扫在同一次 {@code attack} 里，跟着主目标算。GameTest 发这个事件代替真的挥剑。
     */
    static void noteSwing(Player player) {
        if (player.level().isClientSide()) {
            return;
        }
        if (player.getMainHandItem().getItem() instanceof VerdictBladeItem && player.getAttackStrengthScale(0.5F) > CHARGED_SCALE) {
            CHARGED.add(player.getUUID());
        } else {
            CHARGED.remove(player.getUUID());
        }
    }

    /** 普通目标：等本刻末补的一下 */
    private record Pending(LivingDamageEvent event, ServerPlayer player, LivingEntity target, ItemStack blade) {
    }

    private record PairKey(UUID player, LivingEntity target) {
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    /** LivingDamageEvent LOWEST：普通目标、蓄满打实的记下来等本刻末；首领不补 */
    static void noteLanded(LivingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (!(event.getAmount() > 0F) || isBoss(target) || PENDING.size() >= MAX_PENDING) {
            return;
        }
        ServerPlayer player = bladeAttacker(event.getSource(), target);
        if (player != null) {
            PENDING.add(new Pending(event, player, target, player.getMainHandItem()));
        }
    }

    /**
     * 结算这一刻：给记下的普通目标补那一下，并清掉这一刻的蓄满标记。本刻末自动调；GameTest 在 hurt 之后直接调。
     *
     * @return 补成了几下
     */
    public static int flush() {
        CHARGED.clear();
        if (PENDING.isEmpty()) {
            return 0;
        }
        List<Pending> batch = new ArrayList<>(PENDING);
        PENDING.clear();
        Set<PairKey> done = new HashSet<>();
        int dealt = 0;
        for (Pending pending : batch) {
            PairKey key = new PairKey(pending.player().getUUID(), pending.target());
            if (done.contains(key)) {
                continue;
            }
            if (strike(pending)) {
                done.add(key);
                dealt++;
            }
        }
        return dealt;
    }

    private static boolean strike(Pending pending) {
        LivingDamageEvent event = pending.event();
        ServerPlayer player = pending.player();
        LivingEntity target = pending.target();
        if (event.isCanceled() || !(event.getAmount() > 0F) || player.isRemoved() || !target.isAlive() || target.isRemoved()
                || target.level() != player.level()) {
            return false;
        }
        float amount = verdictAmount(pending.blade(), target);
        int cost = Config.values().verdictMru();
        if (!(amount > 0F) || (cost > 0 && !MRUDrain.canAfford(player, pending.blade(), cost))) {
            return false;
        }
        Vec3 motion = target.getDeltaMovement();
        boolean marked = target.hurtMarked;
        ECHurtWindow window = ECHurtWindow.of(target);
        target.invulnerableTime = 0;
        DamageSource source = verdictSource(player);
        // 中间不管谁再乘多少，最终掉血都不超过封顶（总闸在 LivingDamageEvent LOWEST 夹）
        GearCombatEvents.capNext(source, verdictCap(pending.blade(), target));
        boolean hurt = target.hurt(source, amount);
        target.setDeltaMovement(motion);
        target.hurtMarked = marked;
        window.restore(target);
        if (hurt && cost > 0) {
            MRUDrain.playerUseMRU(player, pending.blade(), cost);
        }
        return hurt;
    }

    /** 还没结算的条数（测试看） */
    public static int pendingCount() {
        return PENDING.size();
    }

    // ------------------------------------------------------------------
    // 提示
    // ------------------------------------------------------------------

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        MRUStaffItem.mruTooltip(stack, tooltip);
        tooltip.add(Component.translatable("tooltip.essentialcraft5.verdict_blade.attack", ATTACK_BONUS).withStyle(ChatFormatting.GRAY));
        Optional<ForgedSwordData> forged = appendForgedLines(stack, tooltip);
        Config.Values values = Config.values();
        int stage = Attunement.stage(stack);
        double percent = values.verdictPercent() + values.verdictPercentStage() * stage;
        double cap = values.verdictCap() + values.verdictCapStage() * stage;
        if (percent > 0D && cap > 0D) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.verdict_blade.verdict", values.verdictMru(), decimal(percent),
                    decimal(cap)).withStyle(ChatFormatting.GOLD));
            tooltip.add(MRUStaffItem.usage(values.verdictPvpPercent() > 0D ? "tooltip.essentialcraft5.verdict_blade.verdict_pvp"
                    : "tooltip.essentialcraft5.verdict_blade.verdict_no_pvp", decimal(values.verdictPvpPercent()), decimal(values.verdictCap())));
        }
        if (judgmentUnlocked(stack) && !(values.damageMultiplier() > 0D)) {
            tooltip.add(MRUStaffItem.usage("tooltip.essentialcraft5.verdict_blade.judgment_off"));
        } else if (judgmentUnlocked(stack)) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.verdict_blade.judgment", values.judgmentMru(),
                    decimal(judgmentCooldown(stage, values) / 20D), decimal(values.chargeTicks() / 20D), decimal(values.radius()),
                    values.maxTargets()).withStyle(ChatFormatting.GOLD));
            if (stage >= BISECT_STAGE && values.bisectTicks() > 0) {
                tooltip.add(MRUStaffItem.usage("tooltip.essentialcraft5.verdict_blade.judgment_bisect", decimal(values.bisectTicks() / 20D)));
            }
        } else {
            tooltip.add(MRUStaffItem.usage("tooltip.essentialcraft5.verdict_blade.judgment_locked", JUDGMENT_STAGE));
            if (forged.isPresent()) {
                tooltip.add(MRUStaffItem.usage("tooltip.essentialcraft5.forged_elemental_sword.block"));
            }
        }
    }

    /** 数字：最多两位小数，去掉末尾的 0（3.5、0.5、14） */
    static String decimal(double value) {
        DecimalFormat format = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));
        return format.format(value);
    }

    // ------------------------------------------------------------------
    // 事件（类名带模块前缀，见 tools/check_event_handlers.py）
    // ------------------------------------------------------------------

    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
    public static final class VerdictBladeEvents {

        private VerdictBladeEvents() {
        }

        /** 记下这一下蓄满了没有（Player.attack 开头、蓄力归零之前；HIGHEST，赶在别的处理器可能直接 hurt 之前） */
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void verdictBladeSwing(AttackEntityEvent event) {
            noteSwing(event.getEntity());
        }

        /** 打实了才记（LOWEST，不收已取消的；本刻末结算） */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void verdictBladeDamage(LivingDamageEvent event) {
            if (!event.getEntity().level().isClientSide()) {
                noteLanded(event);
            }
        }

        @SubscribeEvent
        public static void verdictBladeServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase == TickEvent.Phase.END && (!PENDING.isEmpty() || !CHARGED.isEmpty())) {
                flush();
            }
        }

        @SubscribeEvent
        public static void verdictBladeServerStopping(ServerStoppingEvent event) {
            PENDING.clear();
            CHARGED.clear();
            judging = null;
        }
    }
}
