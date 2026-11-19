package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.BootstrapClient;
import com.flameforge.essentialcraft5.gun.LensEffects;
import com.flameforge.essentialcraft5.item.bauble.BaubleEffects;
import com.flameforge.essentialcraft5.item.ember.InlayEvents;
import com.flameforge.essentialcraft5.api.MatrixRanks;
import com.flameforge.essentialcraft5.matrix.MatrixRank;
import com.flameforge.essentialcraft5.matrix.MatrixResonance;
import com.flameforge.essentialcraft5.matrix.MatrixResonanceConfig;
import com.flameforge.essentialcraft5.matrix.MatrixTooltip;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.registry.ECEffects;
import com.flameforge.essentialcraft5.registry.ECStaffs;
import com.flameforge.essentialcraft5.registry.ECTags;
import com.flameforge.essentialcraft5.util.ECDamageKinds;
import com.flameforge.essentialcraft5.util.ECLandedHit;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * 矩阵特性（第十三批 13a，batch13.md §3.2、§4.6，限制见 policy §4.16）：标签 {@code essentialcraft5:matrix_attuned} 里的武器（元素剑、风之剑、混沌之叉、
 * 冰冻重锤、神圣重锤、余烬元素剑；13b 的裁决之刃）打实以后，按你<b>当前</b>的矩阵属性触发一个效果，相性 ≥ I 才有（{@link MatrixResonance}）。
 *
 * <table>
 *   <tr><th>属性</th><th>效果（打实后）</th><th>增伤（打的那一下）</th></tr>
 *   <tr><td>混沌</td><td>腐蚀 {@code lens_corrosion_ticks}（60）刻，复用透镜的 {@link LensEffects#corrode}</td><td>目标已挂着腐蚀 +10%</td></tr>
 *   <tr><td>冰冻</td><td>冰冻刻 +{@code lens_freeze_per_hit}（40），{@link LensEffects#freeze}</td><td>目标已冻僵 +10%</td></tr>
 *   <tr><td>魔力</td><td>驱散一个有益效果（{@link LensEffects#dispelOne}），同一个目标 40 刻一次</td><td>—</td></tr>
 *   <tr><td>暗影</td><td>吸取这一下最终伤害的 {@code lens_drain_percent}%（5%），走 {@code heal}（自己挂着腐蚀照样回不了）</td><td>—</td></tr>
 * </table>
 * <ul>
 *   <li><b>强度</b> = 相性级倍数（I ×1、II ×1.25、III ×1.5）× 武器倍数（神圣重锤 ×2，13b 的裁决之刃 ×1.5，其余 ×1；{@link #registerWeaponMultiplier}）；
 *       乘时长、冰冻刻、吸取比例，不乘增伤百分比。目标是玩家时时长、吸取、增伤百分比再乘 {@code general.attunement.pvp_effect_scale}（0.5）。</li>
 *   <li><b>花费</b>：每次触发 {@code resonance.trait_mru_cost}（50）MRU（{@code MRUDrain.playerUseMRU}：身上储存物品 → 矩阵容量 → 武器自己，魔力相性的折扣照算）。
 *       付不起不触发，伤害照打；效果没加上（冻不上、没东西可剥、满血）不扣。增伤那一下只看付不付得起；给过 +10% 的那一下只要打实了就照扣一次，
 *       效果有没有加上都扣（目标身上已有更长的腐蚀、冰冻刻已封顶、这一下把它打死了，都不能白拿增伤），同一刻同一对仍只扣一次。</li>
 *   <li><b>打实</b>（{@link ECLandedHit#landed}：最终伤害 &gt; 0，或者打掉了对方的伤害吸收）：在 {@code LivingDamageEvent} LOWEST 记下（不收已取消的），
 *       <b>本刻末</b>（{@code ServerTickEvent} END）再结算：事件后来被取消的、最终伤害成了 0 又没吃到伤害吸收的都不算；
 *       PvP 关着、队伍、领地（{@code LivingAttackEvent}）拦下的、混合端插件拦下的都不发 {@code LivingDamageEvent}，什么都不发生。
 *       同一刻、同一名玩家对同一个生物只结算一次（取第一段成了的；前一段没打实时后一段顶上）。横扫打到的别的生物各算一下。</li>
 *   <li><b>武器打的</b>：攻击者是真玩家，这一下是用主手那件武器打的（{@link InlayEvents#weaponHit}：近战直接来源是他本人、不是荆棘 / 魔法 / 弹射物），
 *       饰品打出的（ν 连锁、σ 反弹）、尖刺圆盾的范围伤害不算。</li>
 * </ul>
 * 凝滞相性的反冻（用近战打实你的活物冰冻刻 +30 / +45 / +60，batch13.md §3.2）也在这里结算：同一套「打实、本刻末」，攻击者是玩家时冰冻刻乘 PvP 倍率；
 * 「近战」和凝滞减伤同一个判定（{@link MatrixResonance#frozenMeleeHit}：魔法、无视护甲的不算，监守者音爆、守卫者激光不反冻），反伤也不算。
 * <p>
 * 另外两件早期武器的修补（batch13.md §4.6）挂在同一个增伤监听器里：神圣重锤打亡灵（{@link MobType#UNDEAD}）+{@code general.staffs.holy_mace_undead_bonus}（50%）。
 * 混沌之叉、冰冻重锤叠效果的等级上限 + 相性级在 {@link MatrixWeaponItem}。
 * <p>
 * 时序：特性增伤进总闸的池子（{@code GearCombatEvents}，{@code LivingHurtEvent} NORMAL，和相性、定罪、同调阶等相加后封顶），神圣重锤打亡灵在本类 NORMAL 单独乘（护盾盔甲在 LOW）。只在服务端。每刻最多记 {@value #MAX_PENDING} 条。
 */
public final class MatrixTraits {

    /** 一刻最多记这么多条待结算的命中 */
    public static final int MAX_PENDING = 256;

    /** 这一下加了什么（测试看）。BONUS = 效果没加上，但这一下吃了特性的 +10%，照扣一次钱 */
    public enum Applied {
        NONE, CORROSION, FREEZE, DISPEL, DRAIN, RETALIATE, BONUS
    }

    /**
     * 待结算的一下。trait = true 是武器特性（player 打 target），false 是凝滞反冻（target 打了 player）；
     * bonus = 这一下在 LivingHurtEvent 里吃了特性的 +10%（{@link #traitBonus}）；absorbed = 这一下打掉了伤害吸收（记下时问 {@link ECLandedHit#absorbed}）。
     */
    private record Pending(LivingDamageEvent event, ServerPlayer player, LivingEntity target, ItemStack weapon, int type, int level, boolean trait,
                           boolean bonus, boolean absorbed) {
    }

    /** 结算时去重的键：玩家、对方（按对象比）、哪一种 */
    private record HitKey(UUID player, LivingEntity other, boolean trait) {
    }

    /** 武器倍数登记（神圣重锤 ×2、裁决之刃 ×1.5） */
    private record WeaponMultiplier(Supplier<? extends Item> item, DoubleSupplier multiplier) {
    }

    private static final List<Pending> PENDING = new ArrayList<>();
    /** 这一刻吃了特性增伤的（玩家、目标）：增伤在 LivingHurtEvent 给，记下来留给打实以后扣钱；每次 {@link #flush} 清 */
    private static final Set<HitKey> BONUS_GIVEN = new HashSet<>();
    private static final List<WeaponMultiplier> WEAPON_MULTIPLIERS = new CopyOnWriteArrayList<>();
    /** 魔力特性的驱散冷却：目标 → 服务端刻数（{@code MinecraftServer.getTickCount}，全服一条时间线，不受各维度 gameTime 不同的影响） */
    private static final Map<LivingEntity, Integer> DISPEL_READY = new WeakHashMap<>();
    private static boolean initialized;

    private MatrixTraits() {
    }

    /** 由 {@code registry/ECStaffs.init()} 调一次：神圣重锤的特性倍数、灵魂石提示里的相性几行 */
    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        registerWeaponMultiplier(ECStaffs.HOLY_MACE, ECStaffs.Config::holyMaceTraitMultiplier);
        MatrixTooltip.register(MatrixResonance::appendTooltip);
    }

    /** 登记某件武器的特性倍数（13b 的裁决之刃 ×1.5 在它的注册类里调）；物品按需取，注册之前登记也行 */
    public static void registerWeaponMultiplier(Supplier<? extends Item> item, DoubleSupplier multiplier) {
        WEAPON_MULTIPLIERS.add(new WeaponMultiplier(item, multiplier));
    }

    /** 这件武器的特性倍数（没登记的 1） */
    public static double weaponMultiplier(ItemStack weapon) {
        for (WeaponMultiplier entry : WEAPON_MULTIPLIERS) {
            Item item = entry.item().get();
            if (item != null && weapon.is(item)) {
                return Math.max(0D, entry.multiplier().getAsDouble());
            }
        }
        return 1D;
    }

    /** 带矩阵特性的武器（标签 {@code essentialcraft5:matrix_attuned}） */
    public static boolean isAttuned(ItemStack weapon) {
        return !weapon.isEmpty() && weapon.is(ECTags.MATRIX_ATTUNED);
    }

    /** 这一下的特性强度：相性级倍数 × 武器倍数 × 对玩家倍率 */
    public static double strength(int level, ItemStack weapon, @Nullable Entity target) {
        return MatrixResonance.traitMultiplier(level) * weaponMultiplier(weapon) * MatrixResonance.pvpScale(target);
    }

    // ------------------------------------------------------------------ 打的那一下：增伤

    /**
     * 这一下的特性增伤（0 = 不加）：武器打的、相性 ≥ I、付得起，且目标挂着腐蚀（混沌）/ 已冻僵（冰冻）。目标是玩家时乘 PvP 倍率。
     */
    public static double traitBonus(DamageSource source, LivingEntity target) {
        ServerPlayer player = weaponAttacker(source, target);
        if (player == null) {
            return 0D;
        }
        ItemStack weapon = player.getMainHandItem();
        if (!isAttuned(weapon)) {
            return 0D;
        }
        int type = MatrixResonance.type(player);
        int level = MatrixResonance.level(player);
        double bonus = switch (level <= 0 ? MatrixResonance.NEUTRAL : type) {
            case MatrixResonance.CHAOS -> ECEffects.CORROSION.isPresent() && target.hasEffect(ECEffects.CORROSION.get())
                    ? MatrixResonanceConfig.chaosTraitBonusVsCorroded() : 0D;
            case MatrixResonance.FROZEN -> target.isFullyFrozen() ? MatrixResonanceConfig.frozenTraitBonusVsFrozen() : 0D;
            default -> 0D;
        };
        if (!(bonus > 0D) || !MRUDrain.canAfford(player, weapon, MatrixResonanceConfig.traitMruCost())) {
            return 0D;
        }
        return bonus * MatrixResonance.pvpScale(target);
    }

    /** 神圣重锤打亡灵的加成（0 = 不加）：武器打的、主手是神圣重锤、目标是亡灵 */
    public static double holyMaceBonus(DamageSource source, LivingEntity target) {
        if (target.getMobType() != MobType.UNDEAD) {
            return 0D;
        }
        ServerPlayer player = weaponAttacker(source, target);
        return player != null && player.getMainHandItem().is(ECStaffs.HOLY_MACE.get()) ? ECStaffs.Config.holyMaceUndeadBonus() : 0D;
    }

    /** 这一下是真玩家用主手那件武器打的（不是饰品、尖刺圆盾、荆棘、魔法、弹射物）时返回他 */
    @Nullable
    private static ServerPlayer weaponAttacker(DamageSource source, LivingEntity target) {
        if (!(source.getEntity() instanceof ServerPlayer player) || player == target || !ECProtection.isRealPlayer(player)
                || BaubleEffects.isBaubleDamage() || InlayEvents.isNotWeaponHit()) {
            return null;
        }
        return InlayEvents.weaponHit(source, player, player.getMainHandItem()) ? player : null;
    }

    // ------------------------------------------------------------------ 打实以后：效果

    /** 在 LivingDamageEvent LOWEST 调：武器特性、凝滞反冻各记一条（只记，本刻末结算） */
    static void note(LivingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (PENDING.size() >= MAX_PENDING || !ECLandedHit.landed(event)) {
            return;
        }
        DamageSource source = event.getSource();
        ServerPlayer attacker = weaponAttacker(source, victim);
        if (attacker != null) {
            ItemStack weapon = attacker.getMainHandItem();
            int level = MatrixResonance.level(attacker);
            if (level > 0 && isAttuned(weapon)) {
                boolean bonus = BONUS_GIVEN.contains(new HitKey(attacker.getUUID(), victim, true));
                PENDING.add(new Pending(event, attacker, victim, weapon, MatrixResonance.type(attacker), level, true, bonus,
                        ECLandedHit.absorbed(event) > 0F));
            }
        }
        // 凝滞反冻给真正挥过来的那只（直接实体：替主人咬人的宠物冻宠物）；近战口径同凝滞减伤（魔法、无视护甲的不算），荆棘之类的反伤不算
        if (victim instanceof ServerPlayer player && ECProtection.isRealPlayer(player) && MatrixResonance.frozenMeleeHit(source, player)
                && source.getDirectEntity() instanceof LivingEntity hitter && hitter != player && !ECDamageKinds.isReflected(source)
                && PENDING.size() < MAX_PENDING) {
            int level = MatrixResonance.level(player, MatrixResonance.FROZEN);
            if (level > 0) {
                PENDING.add(new Pending(event, player, hitter, ItemStack.EMPTY, MatrixResonance.FROZEN, level, false, false,
                        ECLandedHit.absorbed(event) > 0F));
            }
        }
    }

    /**
     * 结算这一刻记下的命中，返回真加上了几次。本刻末自动调；GameTest 在 hurt 之后直接调。
     * 同一名玩家对同一个生物（分特性、反冻两种）只结算第一条成了的。
     */
    public static int flush() {
        BONUS_GIVEN.clear();
        if (PENDING.isEmpty()) {
            return 0;
        }
        List<Pending> batch = new ArrayList<>(PENDING);
        PENDING.clear();
        Set<HitKey> done = new HashSet<>();
        int applied = 0;
        for (Pending pending : batch) {
            HitKey key = new HitKey(pending.player().getUUID(), pending.target(), pending.trait());
            if (done.contains(key)) {
                continue;
            }
            if (resolve(pending) != Applied.NONE) {
                done.add(key);
                applied++;
            }
        }
        return applied;
    }

    /** 还没结算的条数（测试看） */
    public static int pendingCount() {
        return PENDING.size();
    }

    private static Applied resolve(Pending pending) {
        LivingDamageEvent event = pending.event();
        ServerPlayer player = pending.player();
        // 记下以后被取消、或者被改成 0 又没吃到伤害吸收的，不算打实
        if (event.isCanceled() || !(event.getAmount() > 0F || pending.absorbed()) || player.isRemoved()) {
            return Applied.NONE;
        }
        if (!pending.trait()) {
            return retaliate(player, pending.target(), pending.level()) ? Applied.RETALIATE : Applied.NONE;
        }
        Applied applied = trigger(player, pending.target(), pending.weapon(), pending.type(), pending.level(), event.getAmount());
        if (applied == Applied.NONE && pending.bonus()) {
            // 增伤已经给了、效果没加上：照扣一次（扣不起就算了，增伤那一刻是付得起的）
            int cost = MatrixResonanceConfig.traitMruCost();
            if (cost <= 0 || MRUDrain.playerUseMRU(player, pending.weapon(), cost)) {
                return Applied.BONUS;
            }
        }
        return applied;
    }

    /**
     * 特性增伤进总闸的池子（{@code item/attunement/GearPool}，{@code ECAttunement.init} 调）：被问到、给了增伤时照旧记下（打实以后扣 MRU）。
     */
    public static void registerGearSources() {
        com.flameforge.essentialcraft5.item.attunement.GearPool.registerOffense("trait", context -> {
            double trait = traitBonus(context.source(), context.target());
            if (trait > 0D && context.source().getEntity() instanceof ServerPlayer player) {
                markBonus(player, context.target());
            }
            return trait;
        });
    }

    /** 记下这一刻这名玩家对这个目标给过特性增伤（池子问到 {@link #registerGearSources} 登记的来源时调） */
    private static void markBonus(ServerPlayer player, LivingEntity target) {
        if (BONUS_GIVEN.size() < MAX_PENDING) {
            BONUS_GIVEN.add(new HitKey(player.getUUID(), target, true));
        }
    }

    /**
     * 触发一次武器特性（付得起才加、加上了才扣）。挂在目标身上的三种要目标还活着；吸取回给自己，打死的那一下也算。
     *
     * @param dealt 这一下最终扣到目标生命上的量（吸取按它算；整下被伤害吸收吃掉时是 0，吸取不起作用）
     */
    public static Applied trigger(ServerPlayer player, LivingEntity target, ItemStack weapon, int type, int level, float dealt) {
        int cost = MatrixResonanceConfig.traitMruCost();
        if (level <= 0 || !MRUDrain.canAfford(player, weapon, cost)) {
            return Applied.NONE;
        }
        double strength = strength(level, weapon, target);
        if (!(strength > 0D)) {
            return Applied.NONE;
        }
        Applied applied = Applied.NONE;
        switch (type) {
            case MatrixResonance.CHAOS -> {
                if (target.isAlive() && LensEffects.corrode(target, player, scaled(LensEffects.Config.corrosionTicks(), strength))) {
                    applied = Applied.CORROSION;
                }
            }
            case MatrixResonance.FROZEN -> {
                if (target.isAlive() && LensEffects.freeze(target, scaled(LensEffects.Config.freezePerHit(), strength))) {
                    applied = Applied.FREEZE;
                }
            }
            case MatrixResonance.MAGIC -> {
                if (target.isAlive() && LensEffects.Config.dispel() && dispelReady(target) && LensEffects.dispelOne(target) != null) {
                    markDispelled(target);
                    applied = Applied.DISPEL;
                }
            }
            case MatrixResonance.SHADE -> {
                if (LensEffects.drain(player, dealt, LensEffects.Config.drainPercent() * strength)) {
                    applied = Applied.DRAIN;
                }
            }
            default -> {
            }
        }
        if (applied != Applied.NONE && cost > 0) {
            MRUDrain.playerUseMRU(player, weapon, cost);
        }
        return applied;
    }

    /** 凝滞反冻：给打实你的活物加冰冻刻（攻击者是玩家时乘 PvP 倍率）；冻不上的（{@code canFreeze()} 为假）不加 */
    public static boolean retaliate(Player victim, LivingEntity attacker, int level) {
        if (!attacker.isAlive() || attacker.level() != victim.level()) {
            return false;
        }
        int ticks = scaled(MatrixResonanceConfig.frozenRetaliationTicks(level), MatrixResonance.pvpScale(attacker));
        return LensEffects.freeze(attacker, ticks);
    }

    private static int scaled(int base, double factor) {
        if (base <= 0 || !(factor > 0D)) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.round(base * factor));
    }

    private static boolean dispelReady(LivingEntity target) {
        Integer ready = DISPEL_READY.get(target);
        return ready == null || now() >= ready;
    }

    private static void markDispelled(LivingEntity target) {
        int cooldown = MatrixResonanceConfig.magicTraitDispelCooldown();
        if (cooldown > 0) {
            DISPEL_READY.put(target, now() + cooldown);
        }
    }

    /** 服务端刻数（各维度共用一条） */
    private static int now() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? 0 : server.getTickCount();
    }

    /** GameTest 用：清掉驱散冷却 */
    public static void clearDispelCooldowns() {
        DISPEL_READY.clear();
    }

    // ------------------------------------------------------------------ 提示

    /** 带矩阵特性的武器提示里加的几行（客户端；看的人是本地玩家） */
    public static void appendTooltip(ItemStack weapon, @Nullable Player viewer, List<Component> tooltip) {
        double multiplier = weaponMultiplier(weapon);
        tooltip.add(Component.translatable("tooltip.essentialcraft5.matrix_trait", MatrixResonanceConfig.traitMruCost()).withStyle(ChatFormatting.DARK_AQUA));
        if (multiplier != 1D) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.matrix_trait.multiplier", MatrixResonance.decimal(multiplier))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        int level = MatrixResonance.level(viewer);
        if (viewer == null || level <= 0) {
            // 暗影属性要八阶才有相性，「要三阶」那句对他不对
            if (viewer != null && MatrixResonance.type(viewer) == MatrixResonance.SHADE && MatrixRanks.get(viewer) < MatrixResonance.SHADE_RANK) {
                tooltip.add(Component.translatable("tooltip.essentialcraft5.soul_stone.alignment_shade_locked", MatrixRank.rankName(MatrixResonance.SHADE_RANK))
                        .withStyle(ChatFormatting.DARK_GRAY));
            } else {
                tooltip.add(Component.translatable("tooltip.essentialcraft5.matrix_trait.inactive").withStyle(ChatFormatting.DARK_GRAY));
            }
            return;
        }
        int type = MatrixResonance.type(viewer);
        double strength = MatrixResonance.traitMultiplier(level) * multiplier;
        tooltip.add(Component.translatable("tooltip.essentialcraft5.matrix_trait.active",
                MatrixResonance.alignmentName(type, level).withStyle(ChatFormatting.LIGHT_PURPLE), effectLine(type, strength)).withStyle(ChatFormatting.DARK_GRAY));
    }

    /** 特性效果那半句（按当前配置和强度算好的数） */
    public static MutableComponent effectLine(int type, double strength) {
        String key = "tooltip.essentialcraft5.matrix_trait." + MatrixResonance.key(type);
        return switch (type) {
            case MatrixResonance.CHAOS -> Component.translatable(key, MatrixResonance.decimal(scaled(LensEffects.Config.corrosionTicks(), strength) / 20D),
                    MatrixResonance.percent(MatrixResonanceConfig.chaosTraitBonusVsCorroded()));
            case MatrixResonance.FROZEN -> Component.translatable(key, scaled(LensEffects.Config.freezePerHit(), strength),
                    MatrixResonance.percent(MatrixResonanceConfig.frozenTraitBonusVsFrozen()));
            case MatrixResonance.MAGIC -> Component.translatable(key, MatrixResonance.decimal(MatrixResonanceConfig.magicTraitDispelCooldown() / 20D));
            case MatrixResonance.SHADE -> Component.translatable(key, MatrixResonance.percent(LensEffects.Config.drainPercent() * strength / 100D));
            default -> Component.empty();
        };
    }

    // ------------------------------------------------------------------ 事件

    /** Forge 总线（类名带模块前缀，见 tools/check_event_handlers.py） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
    public static final class MatrixTraitEvents {

        private MatrixTraitEvents() {
        }

        /**
         * 神圣重锤打亡灵（NORMAL，不收已取消的）：武器本身的克制（同原版亡灵杀手），不进总闸的池子，单独乘。
         * 特性 +10% 进池子（{@link #registerGearSources}）。
         */
        @SubscribeEvent
        public static void matrixTraitHurt(LivingHurtEvent event) {
            LivingEntity target = event.getEntity();
            float amount = event.getAmount();
            if (target.level().isClientSide() || !(amount > 0F)) {
                return;
            }
            double holy = holyMaceBonus(event.getSource(), target);
            if (holy > 0D) {
                event.setAmount((float) (amount * (1D + holy)));
            }
        }

        /** 打实了才记（LOWEST，不收已取消的；打实照 {@link ECLandedHit#landed}，本刻末结算） */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void matrixTraitDamage(LivingDamageEvent event) {
            if (!event.getEntity().level().isClientSide()) {
                note(event);
            }
        }

        @SubscribeEvent
        public static void matrixTraitServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase == TickEvent.Phase.END && (!PENDING.isEmpty() || !BONUS_GIVEN.isEmpty())) {
                flush();
            }
        }

        @SubscribeEvent
        public static void matrixTraitServerStopping(ServerStoppingEvent event) {
            PENDING.clear();
            BONUS_GIVEN.clear();
            DISPEL_READY.clear();
        }
    }

    /** 武器提示（只在客户端登记；事件类本身双端都有，看的人走 BootstrapClient.localPlayer，lambda 里才加载客户端类） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
    public static final class MatrixTraitTooltips {

        private MatrixTraitTooltips() {
        }

        @SubscribeEvent
        public static void matrixTraitTooltip(ItemTooltipEvent event) {
            ItemStack stack = event.getItemStack();
            // 同调装备没按住 Shift 时，矩阵特性几行和物品说明一起收起
            if (!isAttuned(stack) || com.flameforge.essentialcraft5.client.AttunementTooltips.holdsBackNotes(event)) {
                return;
            }
            Player viewer = event.getEntity() != null ? event.getEntity()
                    : DistExecutor.<Player>unsafeCallWhenOn(Dist.CLIENT, () -> BootstrapClient::localPlayer);
            appendTooltip(stack, viewer, event.getToolTip());
        }
    }
}
