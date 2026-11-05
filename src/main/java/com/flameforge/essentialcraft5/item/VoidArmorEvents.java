package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.HoannaWard;
import com.flameforge.essentialcraft5.gun.RayHitGate;
import com.flameforge.essentialcraft5.item.armor.MRUShield;
import com.flameforge.essentialcraft5.item.attunement.Attunement;
import com.flameforge.essentialcraft5.item.attunement.AttunementGains;
import com.flameforge.essentialcraft5.matrix.MatrixResonance;
import com.flameforge.essentialcraft5.registry.ECAttunement;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECVoidArmor;
import com.flameforge.essentialcraft5.util.ECDamageKinds;
import com.flameforge.essentialcraft5.util.ECLandedHit;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 虚空套装挨打、出手、杀怪、换装时的处理（第七批 M3，batch7.md §4.3；挡物理伤害的定法见 §9.1 D1；第十三批 13b 的阶效果见 batch13.md §4.3 / §4.4）。只在服务端。
 *
 * <h2>挨打（{@link LivingHurtEvent}，只对玩家、伤害 &gt; 0）</h2>
 * 这里的伤害是难度缩放之后、护甲之前的数，同原作 ISpecialArmor 拿到的数。分在几个优先级：
 * <ol>
 *   <li><b>相位</b>（HIGHEST，另在 {@link LivingAttackEvent} HIGHEST 先挡一次）：全套 4 阶闪避成功后 {@value #PHASE_TICKS} 刻里整下取消，
 *       只放过 {@code bypasses_invulnerability}（{@code /kill}、掉出世界）和 {@link #UNDODGEABLE} 里的伤害。是取消伤害事件，不是改生命。</li>
 *   <li><b>胸甲闪避</b>（HIGH）：穿着虚空胸甲时按胸甲的阶 {@link #dodgeChance}（0 阶 20%、1 阶 25%、4 阶 30%）取消这次事件
 *       （效果同原作 {@code setAmount(0)}：{@code actuallyHurt} 见到 0 就返回，不扣护甲、不发 LivingDamageEvent，也不扣 MRU）。
 *       1 阶、4 阶多出来的几率只给「有活物攻击者、不是自己」的伤害（{@link #dodgeChance(Player, DamageSource, int)}）：摔落、着火、
 *       腐化 #17 / #18、MRU 腐化这类没有攻击者的伤害照原作 20%，同调不让腐化的扣血变轻（batch13.md §8.1 D34，同宅男 2 阶、审判庭抗魔不减自伤）。
 *       不用设 0：σ 指环不看伤害是不是 0，设 0 以后照样以 0 伤害反弹、给攻击者击退，取消了它就收不到。
 *       {@code /kill}、掉出世界（{@code bypasses_invulnerability}）和 {@link #UNDODGEABLE} 里的伤害不闪（原作 {@code /kill} 有 20% 杀不死；
 *       有预警的大招要靠躲，不靠掷骰，§0.4 第 21 条）。粒子一次发 32 个（原作逐个发 100 个包）。闪掉的那一下有活物攻击者时，接着做「闪避以后」那几件事（下一节）。</li>
 *   <li><b>靴子减摔</b>（HIGH，紧跟闪避）：摔落伤害（{@code is_fall}）只剩一成（原作 {@code amount - amount × 0.9}），靴子 2 阶起整下取消；
 *       摔落不是物理伤害，不扣 MRU。2 阶起落地本身在更早的 {@link LivingFallEvent} 里把伤害倍率设成 0（{@link #onVoidBootsFall}）：
 *       到了受伤事件，原版已经放过摔落音效、设好受伤冷却、广播了受伤（闪红、镜头晃），免掉的摔落看着还像受了伤，还白得一段受伤冷却。
 *       受伤事件里的取消留着，兜住不走落地流程的摔落类伤害（末影珍珠落地那一下）。</li>
 *   <li><b>有电就挡物理伤害</b>（LOW）：第八批起由 {@link MRUShield} 统一处理（{@link VoidArmorItem} 实现 {@code IMRUShieldArmor}，
 *       挡法和同调阶的单价 / 元素覆盖在 {@link VoidArmorItem#coverage}），宅男套装也走同一个监听器，混穿时四件的份额照样按同一下伤害算。
 *       规则不变：四件各管各的，这件自己存的 MRU &gt; 0 就挡它那一份（{@link VoidArmorItem#share}），先扣身上的储存物品；<b>扣不起这件就不挡</b>；
 *       四件都挡下就取消这次事件；不带 {@code bypasses_armor} 才挡；η 这些 NORMAL 的饰品先处理，花费按改过的伤害算；
 *       Mohist 系混合端上先算后扣（LivingDamageEvent 才扣，领地插件拦下的攻击不扣电）。细节见 {@link MRUShield} 的类注释。</li>
 * </ol>
 *
 * <h2>闪避以后（只认活物攻击者，不是自己）</h2>
 * 摔落、岩浆这类没有攻击者的伤害照样能闪，但不触发下面几条（站进火里刷闪避攒回击、相位没有意义）：
 * <ul>
 *   <li><b>滞涩</b>（胸甲 1 阶，D35 A）：攻击者缓慢 II {@value #DRAG_TICKS} 刻；攻击者是玩家时时长 × {@code general.attunement.pvp_effect_scale}，乘出来不到 1 刻就不给。</li>
 *   <li><b>虚空回击</b>（全套，2 阶起 +75%）：之后 {@value #RIPOSTE_WINDOW} 刻内你下一次近战打实 +{@value #RIPOSTE_BONUS}（见下一节）。</li>
 *   <li><b>相位</b>（全套 4 阶）：之后 {@value #PHASE_TICKS} 刻什么都伤不到你（见上）；攻击者是玩家时时长同样 × {@code pvp_effect_scale}。</li>
 *   <li>替同调记一次盔甲点（{@link AttunementGains#armorHitAbsorbed}：闪掉的那一下没有 {@code LivingDamageEvent}，同调的监听器看不到）。</li>
 * </ul>
 * 纯 Forge 当场做；Mohist 系混合端上 LivingHurtEvent 在插件判定之前，闪避本身照常取消这一下，这几件事挂在 {@link MRUShield#defer} 上，
 * 等这一下真的放行（{@code LivingDamageEvent}，闪掉时伤害是 0）才做：站在禁 PvP 领地里被人打，不会给对方挂缓慢、也不白攒回击和相位。
 *
 * <h2>虚空回击（出手的一方，{@link LivingHurtEvent} NORMAL）</h2>
 * 有回击记录、还穿着全套虚空、记录没过期时，你亲手的近战（攻击者和直接实体都是你、算物理伤害，反伤 / 饰品伤害 / 尖刺圆盾这类 {@link AttunementGains#notOwnHit} 不算）
 * 这一下 ×(1 + 加成)，打玩家时加成 × {@code pvp_effect_scale}。和相性的增减伤同在 NORMAL、都是乘法，先后不影响结果；
 * 在对方的护盾（LOW）之前，对方的盔甲按加过的伤害结算。记录在这一下<b>打实</b>（{@link LivingDamageEvent} LOWEST、没被取消，
 * 伤害 &gt; 0 或者打掉了对方的吸收值）时才用掉：原版先扣吸收值、再拿剩下的数发 LivingDamageEvent，吸收值整下吃掉时事件里是 0，
 * 吸收值是血条的一部分，照样算打实（原版统计也把它记成造成的伤害）。被对方闪避、护盾整下吃掉、混合端插件拦下的不算（这几种吸收值都不变），
 * 加成留给下一下；横扫打到的几只按先后，第一只打实就用掉。{@code pvp_effect_scale} 是 0 时打玩家这一下不加、也不用掉。
 * 记录里的伤害源是弱引用：伤害源的攻击者就是记录的键（这名玩家），强引用会让弱引用表永远放不掉这个玩家对象；
 * 另外下线、换玩家对象（死亡重生、从末地回来）时把旧对象的回击、相位记录删掉（{@link #onVoidArmorLoggedOut}、{@link #onVoidArmorClone}）。
 *
 * <h2>海纳尔环境抗性（{@link #hoannaWard}，{@code api/HoannaWard} 的来源）</h2>
 * 全套 0 阶 {@value #WARD_BASE}、2 阶 {@value #WARD_STAGE_2_LEVEL}、4 阶 {@value #WARD_STAGE_4_LEVEL}，作用于日阵、地震、毒雾三种惩罚（{@code hoanna/HoannaHazards}）。
 * 不看电量。辐射走 {@link VoidArmorItem#getResistance}，不在这里。
 *
 * <h2>头盔饱食（{@link LivingDeathEvent}，LOWEST，不收已取消的）</h2>
 * 真玩家打死非玩家的生物、头上戴着虚空头盔：{@code eat((int) (最大生命 / 10), 1)}（原作 ECEventHandler L1061~1078）。
 * 放在最后、不收已取消的：别的模组救活了它就不算（原作死亡事件一来就加）。
 *
 * <h2>脱下夹回生命</h2>
 * 原版换装时只换属性修饰符，生命值不会跟着最大生命往下夹（药水「生命提升」消失时才夹）：穿着全套回满 100 点再脱，能带着 100 点挨下一下。
 * 一件虚空盔甲离开盔甲槽时记下这个生物，下一次 {@link LivingEvent.LivingTickEvent}（这时原版已经换好修饰符）把生命夹到新的最大生命。
 * 原版只要物品 NBT 有一点不同就发换装事件：挡伤害扣电、同调度写盘、阶戳、指令升阶都会发。还是同一种盔甲、阶没往下掉的（{@link #samePiece}）不算脱下；
 * 换成同种但阶更低的一件（同调每阶加的生命变少）照样夹。回击、相位只在用的时候看全套还在不在，不靠这个事件清。
 *
 * <h2>各阶按哪件算</h2>
 * 闪避几率、滞涩看胸甲的阶；减摔看靴子的阶；回击、相位、海纳尔环境抗性、海纳尔辐射易伤减半看全套（四件里最低的阶，{@link Attunement#fullSetStage}，没穿齐不算）；
 * 容量、单价、元素覆盖看各件自己的阶（{@link VoidArmorItem}）。
 * <p>
 * 相位的 {@value #PHASE_TICKS} 刻和原版受伤冷却的后半段重合（闪避是在受伤事件里取消的，原版这时已经设好受伤冷却、记下这一下的伤害），
 * 纯原版近战里同样大或更小的一下本来就打不进来；它多挡下的是更重的一击，以及子弹、法术这类结算前后清受伤冷却的伤害。
 * 设计定的就是 10 刻（batch13.md §4.4），往后挪或拉长等于每次闪避都把全免窗口翻倍。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class VoidArmorEvents {

    /** 0 阶闪避几率（原作 {@code nextFloat() <= 0.2F}） */
    public static final float DODGE_CHANCE = 0.2F;
    /** 胸甲 1 阶起的闪避几率 */
    public static final float DODGE_CHANCE_STAGE_1 = 0.25F;
    /** 胸甲 4 阶的闪避几率 */
    public static final float DODGE_CHANCE_STAGE_4 = 0.3F;
    /** 0 阶靴子减掉的摔落伤害比例（原作 {@code amount - amount × 0.9F}） */
    public static final float FALL_REDUCTION = 0.9F;
    /** 靴子几阶起摔落整下免掉 */
    public static final int FALL_IMMUNE_STAGE = 2;
    /** 滞涩：胸甲几阶起、缓慢几刻、缓慢等级（1 = II） */
    public static final int DRAG_STAGE = 1;
    public static final int DRAG_TICKS = 20;
    public static final int DRAG_AMPLIFIER = 1;
    /** 回击：闪避后几刻内有效、0 阶加成、全套几阶起换成高的加成 */
    public static final int RIPOSTE_WINDOW = 60;
    public static final double RIPOSTE_BONUS = 0.5D;
    public static final int RIPOSTE_STAGE = 2;
    public static final double RIPOSTE_BONUS_ATTUNED = 0.75D;
    /** 相位：全套几阶起、持续几刻 */
    public static final int PHASE_STAGE = 4;
    public static final int PHASE_TICKS = 10;
    /** 海纳尔环境抗性：全套 0 阶、2 阶、4 阶 */
    public static final double WARD_BASE = 0.5D;
    public static final int WARD_STAGE_2 = 2;
    public static final double WARD_STAGE_2_LEVEL = 0.75D;
    public static final int WARD_STAGE_4 = 4;
    public static final double WARD_STAGE_4_LEVEL = 1D;

    /**
     * 闪避、相位都不作用的伤害（batch13.md §0.4 第 21 条：有预警的大招 {@code aberrant_strike}、{@code matrix_rend}、{@code calamity*}）。
     * 就是 {@link ECDamageTypes#UNDODGEABLE}：静态数据 {@code data/essentialcraft5/tags/damage_type/undodgeable.json}，13c 放进了前两种，13e 加天灾之心的，数据包可以再加。
     */
    public static final TagKey<DamageType> UNDODGEABLE = ECDamageTypes.UNDODGEABLE;

    /** 闪避粒子：红色尘埃（原作 redstone 粒子） */
    private static final DustParticleOptions DODGE_PARTICLE = new DustParticleOptions(new Vector3f(1F, 0F, 0F), 1F);

    /** 脱下了虚空盔甲、等着夹生命的生物（弱引用，实体没了自动去掉；只在服务端线程读写） */
    private static final Set<LivingEntity> PENDING_CLAMP = Collections.newSetFromMap(new WeakHashMap<>());
    /** 回击记录（按玩家对象弱引用；只在服务端线程读写） */
    private static final Map<Player, Riposte> RIPOSTE = new WeakHashMap<>();
    /** 相位到哪一个服务器刻为止（不含） */
    private static final Map<Player, Long> PHASE_UNTIL = new WeakHashMap<>();

    @Nullable
    private static volatile Float testDodgeRoll;

    /**
     * 一次回击：到哪一刻为止（不含）、加成；用的那一下先记下伤害源（弱引用：它的攻击者就是表里的键）和刻，打实了才删
     */
    private static final class Riposte {
        final long until;
        final double bonus;
        @Nullable
        WeakReference<DamageSource> source;
        long tick = Long.MIN_VALUE;

        Riposte(long until, double bonus) {
            this.until = until;
            this.bonus = bonus;
        }
    }

    private VoidArmorEvents() {
    }

    // ------------------------------------------------------------------
    // 各阶的数
    // ------------------------------------------------------------------

    /** 胸甲这一阶闪避敌人攻击的几率 */
    public static float dodgeChance(int chestStage) {
        if (chestStage >= 4) {
            return DODGE_CHANCE_STAGE_4;
        }
        return chestStage >= 1 ? DODGE_CHANCE_STAGE_1 : DODGE_CHANCE;
    }

    /**
     * 这一下的闪避几率：有活物攻击者、不是自己时按胸甲的阶（{@link #dodgeChance(int)}），别的（摔落、着火、腐化这类自伤）照原作 {@value #DODGE_CHANCE}
     */
    public static float dodgeChance(Player player, DamageSource source, int chestStage) {
        return source.getEntity() instanceof LivingEntity attacker && attacker != player ? dodgeChance(chestStage) : DODGE_CHANCE;
    }

    /** 靴子这一阶减掉的摔落伤害比例（2 阶起 1 = 全免） */
    public static float fallReduction(int bootsStage) {
        return bootsStage >= FALL_IMMUNE_STAGE ? 1F : FALL_REDUCTION;
    }

    /** 全套这一阶的回击加成（没穿齐 0） */
    public static double riposteBonus(int fullSetStage) {
        if (fullSetStage < 0) {
            return 0D;
        }
        return fullSetStage >= RIPOSTE_STAGE ? RIPOSTE_BONUS_ATTUNED : RIPOSTE_BONUS;
    }

    /** 全套这一阶的海纳尔环境抗性（没穿齐 0） */
    public static double ward(int fullSetStage) {
        if (fullSetStage < 0) {
            return 0D;
        }
        if (fullSetStage >= WARD_STAGE_4) {
            return WARD_STAGE_4_LEVEL;
        }
        return fullSetStage >= WARD_STAGE_2 ? WARD_STAGE_2_LEVEL : WARD_BASE;
    }

    /** 全套的阶（四件最低的那件）；没穿齐 -1 */
    public static int fullSetStage(LivingEntity entity) {
        return Attunement.fullSetStage(entity, ECAttunement.VOID_ARMOR);
    }

    /** {@code api/HoannaWard} 的来源：日阵、地震、毒雾三种按全套的阶给，别的危害 0 */
    public static double hoannaWard(Player player, ResourceLocation hazard) {
        if (!HoannaWard.SUN_ARRAY.equals(hazard) && !HoannaWard.QUAKE.equals(hazard) && !HoannaWard.FUME.equals(hazard)) {
            return 0D;
        }
        return ward(fullSetStage(player));
    }

    /** 闪避、相位作用不作用于这一下：{@code bypasses_invulnerability} 和 {@link #UNDODGEABLE} 里的不作用 */
    public static boolean dodgeable(DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !source.is(UNDODGEABLE);
    }

    /** 计时用服务器刻（各世界的 gameTime 不一样，换维度会错） */
    private static long now(LivingEntity entity) {
        MinecraftServer server = entity.level().getServer();
        return server != null ? server.getTickCount() : entity.level().getGameTime();
    }

    // ------------------------------------------------------------------
    // 挨打：相位、闪避、减摔
    // ------------------------------------------------------------------

    /** 挨打的前置判断：服务端的玩家、伤害 &gt; 0 且有限；不满足返回 null */
    @Nullable
    private static Player hurtPlayer(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return null;
        }
        float amount = event.getAmount();
        return amount > 0F && Float.isFinite(amount) ? player : null;
    }

    /** 相位开着时整下取消（只放过 bypasses_invulnerability 和 undodgeable） */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onVoidPhaseAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide && blocksByPhase(player, event.getSource())) {
            event.setCanceled(true);
        }
    }

    /** 不经过攻击事件、直接走到 LivingHurtEvent 的伤害（别的模组直接调 actuallyHurt 这类）也挡 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onVoidPhaseHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide && blocksByPhase(player, event.getSource())) {
            event.setCanceled(true);
        }
    }

    private static boolean blocksByPhase(Player player, DamageSource source) {
        return !PHASE_UNTIL.isEmpty() && dodgeable(source) && phaseActive(player);
    }

    /** 相位还开着（过期了、全套不到 4 阶了顺手去掉） */
    public static boolean phaseActive(Player player) {
        if (PHASE_UNTIL.isEmpty()) {
            return false;
        }
        Long until = PHASE_UNTIL.get(player);
        if (until == null) {
            return false;
        }
        if (now(player) >= until || fullSetStage(player) < PHASE_STAGE) {
            PHASE_UNTIL.remove(player);
            return false;
        }
        return true;
    }

    /** 胸甲闪避、靴子减摔（HIGH） */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onVoidArmorDodgeAndFall(LivingHurtEvent event) {
        Player player = hurtPlayer(event);
        if (player == null) {
            return;
        }
        float amount = event.getAmount();
        DamageSource source = event.getSource();
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.is(ECVoidArmor.VOID_CHESTPLATE.get()) && dodgeable(source) && dodgeRoll(player) <= dodgeChance(player, source, Attunement.stage(chest))) {
            event.setCanceled(true);
            dodgeParticles(player);
            afterDodge(player, source, Attunement.stage(chest));
            return;
        }
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        if (source.is(DamageTypeTags.IS_FALL) && boots.is(ECVoidArmor.VOID_BOOTS.get())) {
            float reduction = fallReduction(Attunement.stage(boots));
            if (reduction >= 1F) {
                // 正常落地已经在 onVoidBootsFall 里免掉，走到这里的是末影珍珠这类直接用摔落伤害打的
                event.setCanceled(true);
            } else {
                event.setAmount(amount - amount * reduction);
            }
        }
    }

    /**
     * 靴子 2 阶起免摔（HIGH）：落地时把伤害倍率设成 0，原版算出来的摔落伤害是 0，就不放摔落音效、不进 {@code hurt}（没有受伤动画和受伤冷却）。
     * 不取消事件、不改距离：别的模组看落地高度做的事照常。0~1 阶的减一成还在受伤事件里。
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onVoidBootsFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        if (boots.is(ECVoidArmor.VOID_BOOTS.get()) && fallReduction(Attunement.stage(boots)) >= 1F) {
            event.setDamageMultiplier(0F);
        }
    }

    /**
     * 闪掉一下以后（见类注释「闪避以后」）：没有活物攻击者、攻击者是自己时什么都不做。阶在闪避那一刻定下。
     * 纯 Forge 当场做；混合端挂到这一下放行时（{@link MRUShield#defer}，charges 是空表）。
     */
    private static void afterDodge(Player player, DamageSource source, int chestStage) {
        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == player) {
            return;
        }
        int setStage = fullSetStage(player);
        Runnable effects = () -> dodgeEffects(player, source, attacker, chestStage, setStage);
        if (RayHitGate.isBukkitHybrid()) {
            MRUShield.defer(player, source, List.of(), effects);
        } else {
            effects.run();
        }
    }

    private static void dodgeEffects(Player player, DamageSource source, LivingEntity attacker, int chestStage, int setStage) {
        double scale = MatrixResonance.pvpScale(attacker);
        if (chestStage >= DRAG_STAGE && attacker.isAlive()) {
            int ticks = (int) Math.round(DRAG_TICKS * scale);
            if (ticks > 0) {
                attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, DRAG_AMPLIFIER), player);
            }
        }
        long now = now(player);
        if (setStage >= 0) {
            RIPOSTE.put(player, new Riposte(now + RIPOSTE_WINDOW, riposteBonus(setStage)));
        }
        if (setStage >= PHASE_STAGE) {
            int ticks = (int) Math.round(PHASE_TICKS * scale);
            if (ticks > 0) {
                PHASE_UNTIL.put(player, now + ticks);
            }
        }
        AttunementGains.armorHitAbsorbed(player, source);
    }

    /**
     * GameTest 用：混合端上这一下（同一个玩家、同一个伤害源对象）算好了、还在等 LivingDamageEvent 的花费；没有就空表。
     * 第八批起记录在 {@link MRUShield}，这里转一下，第七批的测试照旧能用。
     */
    public static List<MRUShield.Charge> pendingCharges(Player player, DamageSource source) {
        return MRUShield.pendingCharges(player, source);
    }

    private static float dodgeRoll(Player player) {
        Float override = testDodgeRoll;
        return override != null ? override : player.getRandom().nextFloat();
    }

    private static void dodgeParticles(Player player) {
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(DODGE_PARTICLE, player.getX(), player.getY() + 1D, player.getZ(), 32, 0.25D, 0.5D, 0.25D, 0D);
        }
    }

    /** GameTest 用：闪避掷出的数（≤ 几率就闪避），null = 照常随机。穿着胸甲又不想被闪避打乱的测试设 1 */
    public static void setTestDodgeRoll(@Nullable Float roll) {
        testDodgeRoll = roll;
    }

    // ------------------------------------------------------------------
    // 虚空回击（出手的一方）
    // ------------------------------------------------------------------

    /** 你亲手的近战（NORMAL）：有回击就加，记下这一下等打实再用掉 */
    @SubscribeEvent
    public static void onVoidRiposteHurt(LivingHurtEvent event) {
        if (RIPOSTE.isEmpty()) {
            return;
        }
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();
        if (target.level().isClientSide || !(source.getEntity() instanceof Player player) || source.getDirectEntity() != player || target == player
                || !ECDamageKinds.isPhysical(source) || AttunementGains.notOwnHit(source)) {
            return;
        }
        float amount = event.getAmount();
        if (!(amount > 0F) || !Float.isFinite(amount)) {
            return;
        }
        Riposte riposte = activeRiposte(player);
        if (riposte == null) {
            return;
        }
        double bonus = riposte.bonus * MatrixResonance.pvpScale(target);
        if (!(bonus > 0D)) {
            // pvp_effect_scale 配成 0 时打玩家不加，回击也不算用掉，留给下一个目标
            return;
        }
        event.setAmount((float) (amount * (1D + bonus)));
        riposte.source = new WeakReference<>(source);
        riposte.tick = now(player);
    }

    /** 这一下打实了（LOWEST，不收已取消的；打实照 {@link ECLandedHit#landed}：伤害 &gt; 0 或者打掉了对方的伤害吸收）：用掉回击 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onVoidRiposteLanded(LivingDamageEvent event) {
        if (RIPOSTE.isEmpty() || !(event.getSource().getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        Riposte riposte = RIPOSTE.get(player);
        if (riposte == null || riposte.source == null || riposte.source.get() != event.getSource() || riposte.tick != now(player)) {
            return;
        }
        if (ECLandedHit.landed(event)) {
            RIPOSTE.remove(player);
        }
    }

    /** 还能用的回击：没过期、还穿着全套；否则去掉、返回 null */
    @Nullable
    private static Riposte activeRiposte(Player player) {
        Riposte riposte = RIPOSTE.get(player);
        if (riposte == null) {
            return null;
        }
        if (now(player) >= riposte.until || fullSetStage(player) < 0) {
            RIPOSTE.remove(player);
            return null;
        }
        return riposte;
    }

    /** 现在攒着的回击加成（没有 0；不乘对玩家的倍数） */
    public static double pendingRiposte(Player player) {
        Riposte riposte = activeRiposte(player);
        return riposte == null ? 0D : riposte.bonus;
    }

    // ------------------------------------------------------------------
    // 头盔饱食
    // ------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onKillFeedsHelmet(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim instanceof Player || victim.level().isClientSide) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer) || !ECProtection.isRealPlayer(killer)) {
            return;
        }
        if (!killer.getItemBySlot(EquipmentSlot.HEAD).is(ECVoidArmor.VOID_HELMET.get())) {
            return;
        }
        int food = (int) (victim.getMaxHealth() / 10F);
        if (food > 0) {
            killer.getFoodData().eat(food, 1F);
        }
    }

    // ------------------------------------------------------------------
    // 脱下夹回生命
    // ------------------------------------------------------------------

    /**
     * 换装前后是不是「同一件」：同一种虚空盔甲、阶没往下掉。扣电、同调度写盘、阶戳、升阶都只改 NBT，原版照样发换装事件，这些不算脱下。
     * 别的套装脱下夹生命在 {@link com.flameforge.essentialcraft5.item.armor.ArmorTakeoffClamp}，用的是同一个判定。
     */
    public static boolean samePiece(ItemStack from, ItemStack to) {
        return com.flameforge.essentialcraft5.item.armor.ArmorTakeoffClamp.samePiece(from, to);
    }

    @SubscribeEvent
    public static void onArmorChanged(LivingEquipmentChangeEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || event.getSlot().getType() != EquipmentSlot.Type.ARMOR) {
            return;
        }
        ItemStack from = event.getFrom();
        if (from.getItem() instanceof VoidArmorItem && !samePiece(from, event.getTo())) {
            PENDING_CLAMP.add(entity);
        }
    }

    @SubscribeEvent
    public static void onLivingTickClampHealth(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        // 先判断客户端：单人时客户端线程也发这个事件，WeakHashMap 的 isEmpty 会清过期的键（改内部表），客户端线程一下都不能碰
        if (entity.level().isClientSide || PENDING_CLAMP.isEmpty() || !PENDING_CLAMP.remove(entity)) {
            return;
        }
        if (entity.getHealth() > entity.getMaxHealth()) {
            entity.setHealth(entity.getMaxHealth());
        }
    }

    // ------------------------------------------------------------------
    // 收尾、测试
    // ------------------------------------------------------------------

    /** 下线：删掉这名玩家的回击、相位记录（之后不会再有人用这个对象查） */
    @SubscribeEvent
    public static void onVoidArmorLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        forget(event.getEntity());
    }

    /** 死亡重生、从末地回来会换一个新的玩家对象：旧对象的记录删掉，回击和相位不跟到新对象上 */
    @SubscribeEvent
    public static void onVoidArmorClone(PlayerEvent.Clone event) {
        forget(event.getOriginal());
    }

    private static void forget(Player player) {
        if (player.level().isClientSide) {
            return;
        }
        RIPOSTE.remove(player);
        PHASE_UNTIL.remove(player);
    }

    @SubscribeEvent
    public static void onVoidArmorServerStopped(ServerStoppedEvent event) {
        RIPOSTE.clear();
        PHASE_UNTIL.clear();
        PENDING_CLAMP.clear();
    }

    /** GameTest 用：这个生物在不在「等夹生命」的名单里 */
    public static boolean pendingClampForTest(LivingEntity entity) {
        return PENDING_CLAMP.contains(entity);
    }

    /** GameTest 用：把回击的到期挪到 ticksLeft 刻以后（没有回击时不做） */
    public static void setRiposteLeftForTest(Player player, int ticksLeft) {
        Riposte riposte = RIPOSTE.get(player);
        if (riposte != null) {
            RIPOSTE.put(player, new Riposte(now(player) + ticksLeft, riposte.bonus));
        }
    }

    /** GameTest 用：相位还剩几刻（没有 0） */
    public static long phaseLeftForTest(Player player) {
        Long until = PHASE_UNTIL.get(player);
        return until == null ? 0L : Math.max(0L, until - now(player));
    }

    /** GameTest 用：清掉这名玩家的回击、相位、夹生命记录 */
    public static void forgetForTest(Player player) {
        RIPOSTE.remove(player);
        PHASE_UNTIL.remove(player);
        PENDING_CLAMP.remove(player);
    }
}
