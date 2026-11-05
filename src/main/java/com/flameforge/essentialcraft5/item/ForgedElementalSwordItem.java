package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.item.attunement.Attunement;
import com.flameforge.essentialcraft5.item.attunement.AttunementConfig;
import com.flameforge.essentialcraft5.item.attunement.AttunementFamily;
import com.flameforge.essentialcraft5.item.attunement.AttunementStageLines;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.gun.LensEffects;
import com.flameforge.essentialcraft5.item.ember.EmberEffect;
import com.flameforge.essentialcraft5.item.ember.ForgedSwordData;
import com.flameforge.essentialcraft5.item.ember.ForgedSwordData.Element;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.mru.MRUItemStorage;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.util.ECEntityRules;
import com.flameforge.essentialcraft5.util.ECLightning;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 余烬元素剑（第七批 M2，原作 {@code ItemElementalSword}）。余烬锻炉锻出来，锻造数据见 {@link ForgedSwordData}。
 * <p>
 * 元素材料（{@link ElementalTier#ELEMENTAL}）的剑：不掉耐久（原作 {@code setMaxDamage(0)}）、能附魔、自带 {@value MRUStaffItem#MAX_MRU} MRU、
 * 创造栏放空 / 满两份（都没锻造）。攻击伤害 = 主元素（火 7 / 水 3 / 地 8 / 风 8 / 复合 5）+ 余烬加成（玩家基础的 1 点另算），攻速 −2.4；没锻造的为 0。
 * 余烬加成按服务端配置 {@code general.ember_sword.max_ember_damage} 封顶（默认 {@value #MAX_EMBER_DAMAGE}，即最好的组合的合计，等于不封顶）：
 * 最好的剑攻击 45，PvP 打满附魔下界合金约是下界合金剑的 12 倍，夜里锻出来被僵尸捡走也按全额属性打人，PvP 服可以调低。
 * 锻造数据里存的是余烬本身（{@link ForgedSwordData#emberDamage} 不封顶），封顶只在算属性和提示时做，改了上限旧剑跟着变。
 *
 * <h2>命中（{@link #hurtEnemy}：只在服务端、伤害生效以后，只对玩家持剑）</h2>
 * <ul>
 *   <li>主元素各扣 {@value #COST} MRU：火——目标着火 5 秒；水——目标虚弱 I、缓慢 I 2 秒；地——目标失明 I、挖掘疲劳 I 2 秒；
 *       风——自己迅捷 I、力量 I 2.5 秒；复合没有效果、不扣。</li>
 *   <li>余烬的命中型效果（{@link EmberEffect}）合扣一次 {@value #COST} MRU，持续 {@value EmberEffect#DURATION} 刻；
 *       没有命中型效果时不扣（原作连没锻造的剑每下都扣 50）。</li>
 *   <li>扣费走 {@code MRUDrain.playerUseMRU}（先扣身上的储存物品，凑不够再扣剑自己的），不够时没效果，伤害照打。</li>
 * </ul>
 * 引雷（混沌 + 神圣）不生成闪电实体（原作真闪电劈 ±3 格内所有实体、含持剑人，会点火、把村民 / 猪 / 苦力怕变掉）：
 * {@code ECLightning.visual} 画一道只在客户端的闪电，对目标造成 5 点带攻击者的闪电伤害、着火 8 秒，持剑人抗火 II 3 秒。
 * 整道闪电每名持剑人每 {@value #LIGHTNING_COOLDOWN} 刻至多一次（闪烁安全；也免得每下都多 5 点）。
 * 劈之前把目标的受伤冷却清掉，不然这一下刚挨过近战、5 点落在冷却里一点都打不上（原作真闪电同样如此，实际只剩着火）；
 * 被别的模组、领地插件拦下时冷却原样放回、不点火。闪电带攻击者，原版会再击退一次（0.4）：劈完把目标的速度和要不要同步速度的标记原样放回，
 * 只留近战那一下的击退。副作用：闪电把目标的 {@code lastHurt} 改成 5，接下来约 10 刻里别的来源打它，扣的是「伤害 − 5」而不是「伤害 − 刚才那一刀」。
 * 反噬（混沌 + 阴燃、腐化 + 神圣）是不带攻击者的 {@code ember_backlash} 2 点，只打自己（原作 {@code causeMobDamage(自己)}，
 * 1.20 写成玩家攻击的话 PvP 关着时反噬就失效了）。
 *
 * <h2>右键（按住格挡，按下时按副元素扣 {@value #COST} MRU）</h2>
 * 火——自己抗火 2.5 秒，身边 4×4×4 内<b>只点燃敌对生物</b> 2 秒（跳过 {@code ECEntityRules.isSpared}、盟友、玩家、旁观者；原作点燃所有生物）；
 * 水——生命恢复 I 2.5 秒，身上已有生命恢复时不刷新、不扣（原作 40 刻，生命恢复 I 每 50 刻才回 1 点，等于白扣；
 * 每次刷新 50 刻的话连点右键约每秒 5 点，带着灵魂石等于无限回血）；地——抗性提升 I 2 秒；风——迅捷 I、力量 I 1.5 秒。
 * 格挡等于盾（原作的 BLOCK 动作在 1.12 就是盾；1.20 的 Forge 要物品声明 {@link ToolActions#SHIELD_BLOCK}），不掉耐久，被斧头打掉时冷却 5 秒（原版盾的规则）。
 *
 * <h2>同调阶（第十三批 13b，batch13.md §4.4，家族 {@code ember_sword}，最高 2 阶）</h2>
 * <ul>
 *   <li>1 阶：主元素命中、右键效果的时长 ×{@code stage1_duration_multiplier}（2）。火的着火秒数四舍五入到整秒（原版只认整秒，好让火焰保护照常减）。</li>
 *   <li>2 阶：余烬命中效果的 MRU 花费 ×{@code stage2_ember_cost_multiplier}（0.5，向上取整，填 0 就不花）、时长 ×{@code stage2_ember_duration_multiplier}（1.5）。</li>
 *   <li>挂在<b>别的玩家</b>身上的那部分只多给 (倍数 − 1) × {@code general.attunement.pvp_effect_scale}（默认 ×2 → ×1.5、×1.5 → ×1.25），
 *       全批「对玩家减半」都按这个口径；给自己的增益不打折。</li>
 *   <li>余烬嵌刻（{@code item/ember/InlayEvents}）照旧调三参数的 {@link #applyEmber(EmberEffect, LivingEntity, Player)}，不吃剑的阶。</li>
 *   <li>攒满 2 阶以后可以在魔力工作台升格成裁决之刃（{@link VerdictBladeItem}，配方 {@code recipes/attunement/verdict_promotion.json}）。
 *       裁决之刃继承这里的命中、右键，倍数一律按 2 阶算（升格不收回已经练出来的东西）。</li>
 * </ul>
 * 各阶的倍数由 {@link #primaryDurationMultiplier}、{@link #emberCostMultiplier}、{@link #emberDurationMultiplier} 给，子类可以改。
 * 提示框里命中、右键、余烬那几行的秒数按这把剑的倍数现算（{@link #primaryTicks}、{@link #useTicks}、{@link #emberTicks}，按打怪写，
 * 打玩家的折扣不展开），同调那几行的倍数也按配置填（{@link AttunementStageLines}），裁决之刃走同一段，看到的就是它实际继承的 2 阶时长。
 */
public class ForgedElementalSwordItem extends SwordItem implements CreativeVariants, AttunementStageLines {

    /** 每次命中 / 右键的花费（原作 playerUseMRU(…, 50)） */
    public static final int COST = 50;
    /** 原作攻击速度修正 −2.4（同原版剑） */
    public static final float ATTACK_SPEED = -2.4F;
    /** 按住右键的最长时间（原作 getMaxItemUseDuration） */
    public static final int USE_DURATION = 72_000;
    /** 余烬加成的理论最大值：最好的四种（酸蚀、混沌、阴燃、燃烧）6 对合计；也是封顶配置的上限和默认值 */
    public static final int MAX_EMBER_DAMAGE = 36;
    /** 余烬伤害 + 主元素最多 36 + 8，留点余量；属性表按这个下标缓存 */
    private static final int MAX_CACHED_DAMAGE = 64;

    /** 主元素命中的时长（刻）：水、地 40，风 50；火着火 5 秒 */
    public static final int PRIMARY_DEBUFF_TICKS = 40;
    public static final int PRIMARY_AIR_TICKS = 50;
    public static final int PRIMARY_FIRE_SECONDS = 5;

    /** 引雷：伤害、着火秒数、持剑人抗火 II 的时长、每名持剑人的间隔 */
    public static final float LIGHTNING_DAMAGE = 5F;
    public static final int LIGHTNING_FIRE_SECONDS = 8;
    public static final int LIGHTNING_FIRE_RESISTANCE_TICKS = 60;
    public static final int LIGHTNING_COOLDOWN = 40;
    /** 反噬伤害 */
    public static final float BACKLASH_DAMAGE = 2F;

    /** 右键：火（抗火 50 刻、点燃 2 秒）、水（生命恢复 50 刻）、地（抗性提升 40 刻）、风（迅捷 + 力量 30 刻） */
    public static final int USE_FIRE_RESISTANCE_TICKS = 50;
    public static final int USE_IGNITE_SECONDS = 2;
    public static final int USE_REGENERATION_TICKS = 50;
    public static final int USE_RESISTANCE_TICKS = 40;
    public static final int USE_AIR_TICKS = 30;

    /** 属性表缓存：下标是攻击伤害加成 */
    @SuppressWarnings("unchecked")
    private final Multimap<Attribute, AttributeModifier>[] modifierCache = new Multimap[MAX_CACHED_DAMAGE + 1];

    /** 每名持剑人下一次能引雷的世界时间（服务端线程用） */
    private static final Map<UUID, Long> LIGHTNING_READY = new HashMap<>();

    /**
     * 服务端配置 {@code general.ember_sword}（{@code ECEmberForge.init} 登记）。
     * 属性只在换装时重算（{@code LivingEntity.collectEquipmentChanges} 比的是物品本身），改了上限以后，已经拿在手上的剑要切换一下才更新。
     */
    public static final class Config {

        /** 同调 1 阶：主元素命中、右键效果的时长倍数 */
        public static final double DEFAULT_STAGE1_DURATION = 2D;
        /** 同调 2 阶：余烬命中效果的 MRU 花费倍数 */
        public static final double DEFAULT_STAGE2_EMBER_COST = 0.5D;
        /** 同调 2 阶：余烬命中效果的时长倍数 */
        public static final double DEFAULT_STAGE2_EMBER_DURATION = 1.5D;

        /** 同调阶的三个倍数（GameTest 整套覆盖用） */
        public record StageValues(double stage1Duration, double stage2EmberCost, double stage2EmberDuration) {
        }

        public static final StageValues STAGE_DEFAULTS = new StageValues(DEFAULT_STAGE1_DURATION, DEFAULT_STAGE2_EMBER_COST, DEFAULT_STAGE2_EMBER_DURATION);

        private static ForgeConfigSpec.IntValue maxEmberDamage;
        private static ForgeConfigSpec.DoubleValue stage1Duration;
        private static ForgeConfigSpec.DoubleValue stage2EmberCost;
        private static ForgeConfigSpec.DoubleValue stage2EmberDuration;
        /** 只给 GameTest（null = 照配置）。各测试共用一个服务器，设了要在同一段同步代码里用完、在 finally 里清掉 */
        @Nullable
        private static volatile Integer testMaxEmberDamage;
        @Nullable
        private static volatile StageValues testStageValues;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("ember_sword");
            maxEmberDamage = builder.comment("Ember Elemental Sword: cap on the attack damage bonus from ember pairs. The best combination adds up to 36, so 36 means no cap. "
                                    + "Swords already held update after switching away and back.",
                            "余烬元素剑：余烬组合给的攻击伤害加成最多算多少。最好的组合合计 36，填 36 等于不封顶。已经拿在手上的剑要切换一下才更新")
                    .defineInRange("max_ember_damage", MAX_EMBER_DAMAGE, 0, MAX_EMBER_DAMAGE);
            stage1Duration = builder.comment("Attunement stage 1: duration multiplier for the primary element's on-hit effect and the right-click effect. "
                                    + "Against other players only (multiplier - 1) x general.attunement.pvp_effect_scale is added. The Verdict Blade always uses it.",
                            "同调 1 阶：主元素命中、右键效果的时长倍数。挂在别的玩家身上时只多给 (倍数 − 1) × general.attunement.pvp_effect_scale。裁决之刃一直按它算")
                    .defineInRange("stage1_duration_multiplier", DEFAULT_STAGE1_DURATION, 1D, 10D);
            stage2EmberCost = builder.comment("Attunement stage 2: MRU cost multiplier for the ember on-hit effects (50 MRU at stage 0-1, rounded up). 0 = free.",
                            "同调 2 阶：余烬命中效果的 MRU 花费倍数（0~1 阶是 50 MRU，向上取整）；0 = 不花")
                    .defineInRange("stage2_ember_cost_multiplier", DEFAULT_STAGE2_EMBER_COST, 0D, 1D);
            stage2EmberDuration = builder.comment("Attunement stage 2: duration multiplier for the ember on-hit effects. Against other players only "
                                    + "(multiplier - 1) x general.attunement.pvp_effect_scale is added.",
                            "同调 2 阶：余烬命中效果的时长倍数。挂在别的玩家身上时只多给 (倍数 − 1) × general.attunement.pvp_effect_scale")
                    .defineInRange("stage2_ember_duration_multiplier", DEFAULT_STAGE2_EMBER_DURATION, 1D, 10D);
            builder.pop();
        }

        public static int maxEmberDamage() {
            Integer test = testMaxEmberDamage;
            return test != null ? test : ECConfig.get(maxEmberDamage);
        }

        public static void setTestMaxEmberDamage(@Nullable Integer value) {
            testMaxEmberDamage = value;
        }

        /** 同调阶的三个倍数：测试覆盖值 → 配置（没加载时默认值） */
        public static StageValues stageValues() {
            StageValues test = testStageValues;
            if (test != null) {
                return test;
            }
            if (stage1Duration == null) {
                return STAGE_DEFAULTS;
            }
            return new StageValues(ECConfig.get(stage1Duration), ECConfig.get(stage2EmberCost), ECConfig.get(stage2EmberDuration));
        }

        /** GameTest 用：整套换成给定的倍数，null = 回到配置。同一段同步代码里设、测、置回 */
        public static void setTestStageValues(@Nullable StageValues values) {
            testStageValues = values;
        }
    }

    public ForgedElementalSwordItem(Properties properties) {
        super(ElementalTier.ELEMENTAL, 0, ATTACK_SPEED, properties.stacksTo(1));
    }

    @Override
    public @Nullable ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new MRUItemStorage(stack, MRUStaffItem.MAX_MRU);
    }

    // ------------------------------------------------------------------
    // 锻造产物
    // ------------------------------------------------------------------

    /** 一把锻好的剑（0 MRU，同原作新建的 ItemStack） */
    public ItemStack forged(ForgedSwordData data) {
        ItemStack stack = new ItemStack(this);
        data.writeTo(stack);
        return stack;
    }

    // ------------------------------------------------------------------
    // 不掉耐久、照样能附魔
    // ------------------------------------------------------------------

    /** 同调 3 阶起带附魔光泽（batch13.md §3.3.5） */
    @Override
    public boolean isFoil(ItemStack stack) {
        return super.isFoil(stack) || Attunement.hasGlint(stack);
    }

    /** 没有耐久（原作 setMaxDamage(0)）：挖方块、打怪、格挡都不掉，不显示耐久条，也不能附耐久、经验修补 */
    @Override
    public boolean canBeDepleted() {
        return false;
    }

    /** 原作 isEnchantable 恒为 true（没有耐久的物品原版默认不能附魔） */
    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    // ------------------------------------------------------------------
    // 属性
    // ------------------------------------------------------------------

    /** 按锻造数据算主手的攻击伤害（主元素 + 封顶后的余烬加成）和攻速 −2.4；没锻造的攻击加成为 0 */
    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        if (slot != EquipmentSlot.MAINHAND) {
            return ImmutableMultimap.of();
        }
        int damage = ForgedSwordData.read(stack).map(ForgedElementalSwordItem::attackBonus).orElse(0);
        return modifiers(Math.max(0, damage));
    }

    /** 实际生效的余烬加成：余烬组合的合计，不超过 {@code general.ember_sword.max_ember_damage} */
    public static int emberBonus(ForgedSwordData data) {
        return Math.min(data.emberDamage(), Config.maxEmberDamage());
    }

    /** 主手攻击伤害加成（玩家基础的 1 点另算）：主元素 + 实际生效的余烬加成 */
    public static int attackBonus(ForgedSwordData data) {
        return data.primary().damage() + emberBonus(data);
    }

    /** 没有锻造数据时用的那一份（别的模组只按物品取默认属性时看到的是没锻造的剑） */
    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? modifiers(0) : ImmutableMultimap.of();
    }

    /** 主手属性表（攻击加成 damage、攻速 −2.4），按 damage 缓存 */
    protected Multimap<Attribute, AttributeModifier> modifiers(int damage) {
        if (damage > MAX_CACHED_DAMAGE) {
            return build(damage);
        }
        Multimap<Attribute, AttributeModifier> cached = modifierCache[damage];
        if (cached == null) {
            cached = build(damage);
            modifierCache[damage] = cached;
        }
        return cached;
    }

    private static Multimap<Attribute, AttributeModifier> build(int damage) {
        return ImmutableMultimap.of(
                Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", damage, AttributeModifier.Operation.ADDITION),
                Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier", ATTACK_SPEED, AttributeModifier.Operation.ADDITION));
    }

    // ------------------------------------------------------------------
    // 命中
    // ------------------------------------------------------------------

    /** 只在服务端、伤害生效以后调用（见类注释）。不调 super（super 会扣耐久） */
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker.level().isClientSide || !(attacker instanceof Player player)) {
            return true;
        }
        Optional<ForgedSwordData> forged = ForgedSwordData.read(stack);
        if (forged.isEmpty()) {
            return true;
        }
        ForgedSwordData data = forged.get();
        applyPrimary(stack, data.primary(), target, player, primaryDurationMultiplier(stack));
        Set<EmberEffect> effects = data.effects();
        if (!effects.isEmpty()) {
            int cost = emberCost(stack);
            if (cost <= 0 || MRUDrain.playerUseMRU(player, stack, cost)) {
                double multiplier = emberDurationMultiplier(stack);
                for (EmberEffect effect : effects) {
                    applyEmber(effect, target, player, multiplier);
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // 同调阶（batch13.md §4.4）
    // ------------------------------------------------------------------

    /** 主元素命中、右键效果的时长倍数：同调 1 阶起按配置（默认 ×2），之前 ×1 */
    public double primaryDurationMultiplier(ItemStack stack) {
        return Attunement.stage(stack) >= 1 ? Config.stageValues().stage1Duration() : 1D;
    }

    /** 余烬命中效果的 MRU 花费倍数：同调 2 阶起按配置（默认 ×0.5），之前 ×1 */
    public double emberCostMultiplier(ItemStack stack) {
        return Attunement.stage(stack) >= 2 ? Config.stageValues().stage2EmberCost() : 1D;
    }

    /** 余烬命中效果的时长倍数：同调 2 阶起按配置（默认 ×1.5），之前 ×1 */
    public double emberDurationMultiplier(ItemStack stack) {
        return Attunement.stage(stack) >= 2 ? Config.stageValues().stage2EmberDuration() : 1D;
    }

    /** 余烬命中效果这一下花多少 MRU（{@value #COST} × 花费倍数，向上取整；0 = 不花） */
    public int emberCost(ItemStack stack) {
        double multiplier = emberCostMultiplier(stack);
        return multiplier > 0D ? (int) Math.ceil(COST * Math.min(1D, multiplier) - 1.0E-9D) : 0;
    }

    /**
     * 挂在 target 身上的效果按倍数算出的刻数：倍数 ≤ 1 原样；target 是玩家时只多给 (倍数 − 1) × {@code pvp_effect_scale}。四舍五入。
     */
    public static int targetTicks(int base, double multiplier, LivingEntity target) {
        double factor = multiplier;
        if (factor > 1D && target instanceof Player) {
            factor = 1D + (factor - 1D) * AttunementConfig.pvpEffectScale();
        }
        return (int) Math.round(base * Math.max(0D, factor));
    }

    /** 给自己的效果按倍数算出的刻数（不打折），四舍五入 */
    public static int selfTicks(int base, double multiplier) {
        return (int) Math.round(base * Math.max(0D, multiplier));
    }

    /**
     * 主元素命中效果对怪物（风是给自己）持续几刻，和 {@link #applyPrimary} 同一算法；火按整秒（原版只认整秒）。复合没有效果，0。
     * 提示框按这个写秒数，打玩家时挂在对方身上的那部分另按 {@link #targetTicks} 打折，提示框不展开。
     */
    public static int primaryTicks(Element primary, double multiplier) {
        return switch (primary) {
            case FIRE -> 20 * Math.max(1, Math.round(selfTicks(PRIMARY_FIRE_SECONDS * 20, multiplier) / 20F));
            case WATER, EARTH -> selfTicks(PRIMARY_DEBUFF_TICKS, multiplier);
            case AIR -> selfTicks(PRIMARY_AIR_TICKS, multiplier);
            case COMBINED -> 0;
        };
    }

    /** 副元素右键给自己的效果持续几刻（火是抗火那一样），和 {@link #applySecondary} 同一算法。复合没有效果，0 */
    public static int useTicks(Element secondary, double multiplier) {
        return switch (secondary) {
            case FIRE -> selfTicks(USE_FIRE_RESISTANCE_TICKS, multiplier);
            case WATER -> selfTicks(USE_REGENERATION_TICKS, multiplier);
            case EARTH -> selfTicks(USE_RESISTANCE_TICKS, multiplier);
            case AIR -> selfTicks(USE_AIR_TICKS, multiplier);
            case COMBINED -> 0;
        };
    }

    /** 余烬命中效果对怪物（和给自己的）持续几刻，按这把剑的 {@link #emberDurationMultiplier}，和 {@link #applyEmber} 同一算法 */
    public int emberTicks(ItemStack stack) {
        return selfTicks(EmberEffect.DURATION, emberDurationMultiplier(stack));
    }

    /** 同调阶效果那一行，倍数按配置填（余烬剑两阶；裁决之刃自己覆写） */
    @Override
    @Nullable
    public Component attunementStageLine(ItemStack stack, int stage) {
        Config.StageValues values = Config.stageValues();
        return switch (stage) {
            case 1 -> Component.translatable("tooltip.essentialcraft5.attunement.ember_sword.stage_1",
                    AttunementStageLines.number(values.stage1Duration()));
            case 2 -> values.stage2EmberCost() > 0D
                    ? Component.translatable("tooltip.essentialcraft5.attunement.ember_sword.stage_2",
                    AttunementStageLines.percent(Math.min(1D, values.stage2EmberCost())), AttunementStageLines.number(values.stage2EmberDuration()))
                    : Component.translatable("tooltip.essentialcraft5.attunement.ember_sword.stage_2.free",
                    AttunementStageLines.number(values.stage2EmberDuration()));
            default -> null;
        };
    }

    private static void applyPrimary(ItemStack stack, Element primary, LivingEntity target, Player player, double multiplier) {
        if (primary == Element.COMBINED || !MRUDrain.playerUseMRU(player, stack, COST)) {
            return;
        }
        switch (primary) {
            case FIRE -> target.setSecondsOnFire(Math.max(1, Math.round(targetTicks(PRIMARY_FIRE_SECONDS * 20, multiplier, target) / 20F)));
            case WATER -> {
                int ticks = targetTicks(PRIMARY_DEBUFF_TICKS, multiplier, target);
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 0), player);
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 0), player);
            }
            case EARTH -> {
                int ticks = targetTicks(PRIMARY_DEBUFF_TICKS, multiplier, target);
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks, 0), player);
                target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks, 0), player);
            }
            case AIR -> {
                int ticks = selfTicks(PRIMARY_AIR_TICKS, multiplier);
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 0));
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, 0));
            }
            default -> {
            }
        }
    }

    /**
     * 一个余烬命中效果（剑的 {@link #hurtEnemy} 和余烬嵌刻 {@code item/ember/InlayEvents} 共用；扣费由调用方做）。
     * 引雷照旧每名持剑人每 {@value #LIGHTNING_COOLDOWN} 刻至多一次（剑和嵌刻武器共用这个间隔）。嵌刻不吃剑的同调阶，时长 ×1。
     */
    public static void applyEmber(EmberEffect effect, LivingEntity target, Player player) {
        applyEmber(effect, target, player, 1D);
    }

    /**
     * 同上，时长 × multiplier（同调 2 阶的余烬时长，{@link #emberDurationMultiplier}）；挂在别的玩家身上的部分按 {@link #targetTicks} 打折。
     * 只改持续时间：吸血量、反噬伤害、引雷不变。
     */
    public static void applyEmber(EmberEffect effect, LivingEntity target, Player player, double multiplier) {
        int onTarget = targetTicks(EmberEffect.DURATION, multiplier, target);
        int onSelf = selfTicks(EmberEffect.DURATION, multiplier);
        switch (effect) {
            case SLOWNESS -> debuff(target, player, MobEffects.MOVEMENT_SLOWDOWN, onTarget, 0);
            case GREATER_SLOWNESS -> debuff(target, player, MobEffects.MOVEMENT_SLOWDOWN, onTarget, 1);
            case POISON -> debuff(target, player, MobEffects.POISON, onTarget, 0);
            case GREATER_POISON -> debuff(target, player, MobEffects.POISON, onTarget, 1);
            case DAMAGE_SELF -> player.hurt(ECDamageTypes.emberBacklash(player.level()), BACKLASH_DAMAGE);
            case LIGHTNING -> strike(target, player);
            case LIFESTEAL -> player.heal(1F);
            case GREATER_LIFESTEAL -> player.heal(3F);
            case WEAKNESS -> debuff(target, player, MobEffects.WEAKNESS, onTarget, 0);
            case STRENGTH -> player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, onSelf, 0));
            case SPEED -> player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, onSelf, 0));
            case GREATER_SPEED -> player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, onSelf, 1));
            case GREATER_HUNGER -> debuff(target, player, MobEffects.HUNGER, onTarget, 1);
            case CORROSION -> LensEffects.corrode(target, player, onTarget);
            case DISPEL -> LensEffects.dispelOne(target);
        }
    }

    private static void debuff(LivingEntity target, Player source, MobEffect effect, int ticks, int amplifier) {
        target.addEffect(new MobEffectInstance(effect, ticks, amplifier), source);
    }

    /**
     * 引雷（见类注释）：每名持剑人每 {@value #LIGHTNING_COOLDOWN} 刻至多一道；目标已经死了不劈（不白占间隔）。
     *
     * @return 这次劈了没有（间隔没到、目标已死返回 false）
     */
    static boolean strike(LivingEntity target, Player player) {
        if (!(player.level() instanceof ServerLevel level) || !target.isAlive()) {
            return false;
        }
        long now = level.getGameTime();
        if (!lightningReady(player.getUUID(), now)) {
            return false;
        }
        LIGHTNING_READY.put(player.getUUID(), now + LIGHTNING_COOLDOWN);
        ECLightning.visual(level, target.position());
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, LIGHTNING_FIRE_RESISTANCE_TICKS, 1));
        int cooldown = target.invulnerableTime;
        target.invulnerableTime = 0;
        Vec3 motion = target.getDeltaMovement();
        boolean marked = target.hurtMarked;
        DamageSource source = new DamageSource(ECDamageTypes.holder(level, DamageTypes.LIGHTNING_BOLT), null, player);
        if (target.hurt(source, LIGHTNING_DAMAGE)) {
            // 带攻击者的伤害原版会再击退一次：速度和标记原样放回，只留近战那一下的击退。
            // 标记不能硬设 true：玩家目标的近战击退包 Player.attack 已经发过、标记已清、服务端速度也复位了，设 true 会把挨打前的速度再发一遍，抹掉近战击退
            target.setDeltaMovement(motion);
            target.hurtMarked = marked;
            target.setSecondsOnFire(LIGHTNING_FIRE_SECONDS);
        } else {
            // 被拦下：受伤冷却放回去，别让这次清零白白给后面的攻击开了口子
            target.invulnerableTime = Math.max(target.invulnerableTime, cooldown);
        }
        return true;
    }

    /**
     * 这名持剑人现在能不能引雷。记下的时间比现在晚一个间隔以上的当成别的存档留下的（单人换了个世界、时间比原来的早），不算数。
     * 表大了顺手清掉过期的条目。
     */
    private static boolean lightningReady(UUID player, long now) {
        if (LIGHTNING_READY.size() > 256) {
            Iterator<Map.Entry<UUID, Long>> it = LIGHTNING_READY.entrySet().iterator();
            while (it.hasNext()) {
                long ready = it.next().getValue();
                if (ready <= now || ready - now > LIGHTNING_COOLDOWN) {
                    it.remove();
                }
            }
        }
        Long ready = LIGHTNING_READY.get(player);
        return ready == null || ready <= now || ready - now > LIGHTNING_COOLDOWN;
    }

    // ------------------------------------------------------------------
    // 右键：副元素效果 + 格挡
    // ------------------------------------------------------------------

    @Override
    public int getUseDuration(ItemStack stack) {
        return USE_DURATION;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BLOCK;
    }

    /** 剑的动作（横扫、砍蜘蛛网）加上盾的格挡 */
    @Override
    public boolean canPerformAction(ItemStack stack, ToolAction toolAction) {
        return ToolActions.DEFAULT_SWORD_ACTIONS.contains(toolAction) || ToolActions.DEFAULT_SHIELD_ACTIONS.contains(toolAction);
    }

    /** 按下右键：副元素效果（只在服务端，见类注释），然后举剑格挡（两端都举，客户端才有格挡姿势）。MRU 不够照样能格挡 */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            ForgedSwordData.read(stack).ifPresent(data -> applySecondary(stack, data.secondary(), player));
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    /**
     * 副元素的右键效果（GameTest 直接调）。时长按这把剑的 {@link #primaryDurationMultiplier}（同调 1 阶起 ×2）；点燃只烧敌对生物，不涉及别的玩家。
     *
     * @return 生效了（扣了 MRU）没有
     */
    public static boolean applySecondary(ItemStack stack, Element secondary, Player player) {
        double multiplier = stack.getItem() instanceof ForgedElementalSwordItem sword ? sword.primaryDurationMultiplier(stack) : 1D;
        switch (secondary) {
            case FIRE -> {
                if (!MRUDrain.playerUseMRU(player, stack, COST)) {
                    return false;
                }
                player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, selfTicks(USE_FIRE_RESISTANCE_TICKS, multiplier), 0));
                int seconds = Math.max(1, Math.round(selfTicks(USE_IGNITE_SECONDS * 20, multiplier) / 20F));
                for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, igniteArea(player),
                        entity -> ignites(entity, player))) {
                    entity.setSecondsOnFire(seconds);
                }
                return true;
            }
            case WATER -> {
                // 身上已有生命恢复（不管是谁给的、几级）就不刷新、不扣
                if (player.hasEffect(MobEffects.REGENERATION) || !MRUDrain.playerUseMRU(player, stack, COST)) {
                    return false;
                }
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, selfTicks(USE_REGENERATION_TICKS, multiplier), 0));
                return true;
            }
            case EARTH -> {
                if (!MRUDrain.playerUseMRU(player, stack, COST)) {
                    return false;
                }
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, selfTicks(USE_RESISTANCE_TICKS, multiplier), 0));
                return true;
            }
            case AIR -> {
                if (!MRUDrain.playerUseMRU(player, stack, COST)) {
                    return false;
                }
                int ticks = selfTicks(USE_AIR_TICKS, multiplier);
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 0));
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, 0));
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** 火焰右键的范围：以玩家脚下为准 x/z ±2、y −1~+3（原作 L451） */
    public static AABB igniteArea(Player player) {
        return new AABB(player.getX() - 2D, player.getY() - 1D, player.getZ() - 2D, player.getX() + 2D, player.getY() + 3D, player.getZ() + 2D);
    }

    /** 火焰右键点不点它：只点敌对生物，跳过谁都不许碰的、盟友、玩家、旁观者 */
    public static boolean ignites(LivingEntity entity, Player player) {
        return entity != player && entity.isAlive() && entity instanceof Enemy && !(entity instanceof Player)
                && !entity.isSpectator() && !ECEntityRules.isSpared(entity) && !entity.isAlliedTo(player);
    }

    // ------------------------------------------------------------------
    // 提示、创造栏、动画
    // ------------------------------------------------------------------

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        MRUStaffItem.mruTooltip(stack, tooltip);
        if (appendForgedLines(stack, tooltip).isEmpty()) {
            return;
        }
        tooltip.add(MRUStaffItem.usage("tooltip.essentialcraft5.forged_elemental_sword.block"));
        AttunementFamily family = AttunementFamily.of(stack);
        if (Attunement.isMaxed(stack) && family != null && family.active()) {
            // 同调总开关关掉、这个家族被关掉时升格配方不认中心（AttunementAscensionRecipe.acceptsCenter），这一行也不写；
            // 升格配方的阶戳要求按「升到下一阶」算（同 AttunementAscensionRecipe.requiredRank）
            int rank = AttunementConfig.requiredRank(Attunement.stage(stack) + 1);
            tooltip.add((rank > 0 ? Component.translatable("tooltip.essentialcraft5.forged_elemental_sword.promotable_rank", rank)
                    : Component.translatable("tooltip.essentialcraft5.forged_elemental_sword.promotable")).withStyle(ChatFormatting.GOLD));
        }
    }

    /**
     * 锻造数据那几行（主 / 副元素、余烬加成、命中、右键；花费和秒数按这把剑的阶算）。没锻造时写一行「未锻造」，返回空。
     * 裁决之刃也用这一段。
     */
    protected Optional<ForgedSwordData> appendForgedLines(ItemStack stack, List<Component> tooltip) {
        Optional<ForgedSwordData> forged = ForgedSwordData.read(stack);
        if (forged.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.forged_elemental_sword.unforged").withStyle(ChatFormatting.GRAY));
            return forged;
        }
        ForgedSwordData data = forged.get();
        tooltip.add(Component.translatable("tooltip.essentialcraft5.forged_elemental_sword.primary", elementName(data.primary()))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.essentialcraft5.forged_elemental_sword.secondary", elementName(data.secondary()))
                .withStyle(ChatFormatting.GRAY));
        int emberDamage = emberBonus(data);
        if (emberDamage > 0) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.forged_elemental_sword.ember_damage", emberDamage).withStyle(ChatFormatting.GRAY));
        }
        double durations = primaryDurationMultiplier(stack);
        if (data.primary() != Element.COMBINED) {
            tooltip.add(MRUStaffItem.usage("tooltip.essentialcraft5.forged_elemental_sword.hit_primary", COST,
                    Component.translatable("tooltip.essentialcraft5.forged_elemental_sword.hit." + data.primary().id(),
                            AttunementStageLines.seconds(primaryTicks(data.primary(), durations)))));
        }
        Set<EmberEffect> effects = data.effects();
        List<Component> onTarget = new ArrayList<>();
        List<Component> onSelf = new ArrayList<>();
        for (EmberEffect effect : effects) {
            (effect.self() ? onSelf : onTarget).add(Component.translatable(effect.translationKey()));
        }
        int emberCost = emberCost(stack);
        if (!onTarget.isEmpty()) {
            tooltip.add(MRUStaffItem.usage("tooltip.essentialcraft5.forged_elemental_sword.hit_target", emberCost,
                    AttunementStageLines.seconds(emberTicks(stack)), join(onTarget)));
        }
        if (!onSelf.isEmpty()) {
            tooltip.add(MRUStaffItem.usage(onTarget.isEmpty() ? "tooltip.essentialcraft5.forged_elemental_sword.hit_self"
                    : "tooltip.essentialcraft5.forged_elemental_sword.hit_self_same_cost", emberCost, join(onSelf)));
        }
        if (data.secondary() != Element.COMBINED) {
            tooltip.add(MRUStaffItem.usage("tooltip.essentialcraft5.forged_elemental_sword.use", COST,
                    Component.translatable("tooltip.essentialcraft5.forged_elemental_sword.use." + data.secondary().id(),
                            AttunementStageLines.seconds(useTicks(data.secondary(), durations)))));
        }
        return forged;
    }

    private static MutableComponent elementName(Element element) {
        ChatFormatting color = switch (element) {
            case FIRE -> ChatFormatting.RED;
            case WATER -> ChatFormatting.AQUA;
            case EARTH -> ChatFormatting.GREEN;
            case AIR -> ChatFormatting.YELLOW;
            case COMBINED -> ChatFormatting.LIGHT_PURPLE;
        };
        return Component.translatable(element.translationKey()).withStyle(color);
    }

    /** 几个名字用「、」连起来（英文是 ", "，键 tooltip.essentialcraft5.forged_elemental_sword.separator） */
    private static Component join(List<Component> parts) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                out.append(Component.translatable("tooltip.essentialcraft5.forged_elemental_sword.separator"));
            }
            out.append(parts.get(i));
        }
        return out;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !oldStack.is(newStack.getItem());
    }

    @Override
    public List<ItemStack> creativeVariants() {
        return MRUStaffItem.variants(this);
    }
}
