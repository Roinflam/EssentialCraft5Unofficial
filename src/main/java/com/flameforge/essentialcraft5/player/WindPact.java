package com.flameforge.essentialcraft5.player;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.WindAffinity;
import com.flameforge.essentialcraft5.blockentity.PlayerPentacleBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.WindArmorItem;
import com.flameforge.essentialcraft5.item.WindGearEvents;
import com.flameforge.essentialcraft5.item.WindTabletItem;
import com.flameforge.essentialcraft5.registry.ECWindGear;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 风之契约（第十批 10b，batch10.md §5.8.1；原作没有）：风之真名显全以后，风之好感继续往上攒，风再给两样只作用于自己的本事。
 * 风法师支线的两个可选步骤（手册 {@code wind_mage/steadfast}、{@code wind_mage/deflect}）。
 * <ul>
 *   <li><b>不动如山</b>：好感 ≥ {@code general.wind.steadfast_points}（默认 7 000）、真名已显、正在潜行 → {@code LivingKnockBackEvent} 取消，不被击退。</li>
 *   <li><b>风之偏转</b>：好感 ≥ {@code deflect_points}（默认 12 000）、真名已显 → 别人射来、扔来的弹射物（不是自己的）{@code ProjectileImpactEvent} 的结果改成
 *       {@code SKIP_ENTITY}，从身上穿过去，出一缕风的粒子；固定节奏不是几率：偏转一颗以后 {@code deflect_cooldown_ticks}（默认 60）刻内新来的照常命中，
 *       不扣好感（扣好感会影响石板门槛的判定）。门槛填 0 = 关掉这一项。</li>
 *   <li><b>按「这一颗」记账</b>：只有箭（{@code AbstractArrow}）把 {@code SKIP_ENTITY} 当成「把这人加进忽略名单、穿过去」；火球、雪球、药水、潜影贝导弹、
 *       羊驼口水这些只是跳过这一刻的命中，下一刻还贴着人就再发一次事件。所以同一颗对同一人偏转过就一直放行，直到它消失；冷却只在新的一颗来时看、来时扣，
 *       不然非箭类第二刻就会在冷却里打中。记账两端各一份（箭在客户端也走一遍命中：本地玩家照同样的规矩算，免得客户端上的箭先弹开再被服务端拉回去；
 *       客户端没有别人的好感，别的玩家只在服务端算）。</li>
 *   <li><b>真名</b>：问 {@link WindTabletItem#attuned}（风之石板认的那一套：好感到 {@code general.wind.tablet_points} 并且心愿数够 {@code general.wind_wish.required}，
 *       两端都能算）。好感来自心愿（真名以后照常能问）、风之药水、风事件；风法师套装和护符让这条路快得多。</li>
 *   <li><b>进度</b> {@code guide/wind_mage/steadfast} / {@code guide/wind_mage/deflect}：服务端每 20 刻（按玩家 id 错开）查一次，第一次够了就发，
 *       同时风在聊天栏说一句（让人知道多了什么本事）；不改加好感那一处（{@code WindAffinity.add}）。</li>
 * </ul>
 * 只作用于自己：不碰别的玩家、生物，也不改弹射物本身（不反弹、不改方向）。别的模组的子弹实体不走 {@code ProjectileImpactEvent} 的就偏不开，不出错。
 * <p>
 * <b>风之护甲全套</b>（第十三批 13b，batch13.md §4.3、§4.4）共用这里的偏转和不被击退，不看好感：
 * <ul>
 *   <li>偏转：穿齐四件（{@link WindArmorItem#fullSetStage}）且 {@code general.wind_armor.deflect} 开着就能偏，间隔按全套阶读 {@code deflect_cooldown_ticks}
 *       （默认 120 / 80 / 80 / 40 刻）；同时有契约的「风之偏转」时取两者较短的再减半（{@link #combinedCooldown}，默认 min(60, 120) / 2 = 30 刻）。
 *       全套参与了这次偏转时回调 {@link WindGearEvents#onSetDeflected}（同调度、顺风）。</li>
 *   <li>全套 1 阶起潜行时不被击退，和「不动如山」任一成立就算。</li>
 * </ul>
 */
public final class WindPact {

    public static final ResourceLocation STEP_STEADFAST = EssentialCraft5.id("guide/wind_mage/steadfast");
    public static final ResourceLocation STEP_DEFLECT = EssentialCraft5.id("guide/wind_mage/deflect");
    /** 两个进度文件里代码发的那条条件名 */
    public static final String CRITERION = "awarded_by_code";
    /** 进度每隔几刻查一次 */
    public static final int CHECK_INTERVAL = 20;

    private static final DeflectLedger SERVER = new DeflectLedger();
    private static final DeflectLedger CLIENT = new DeflectLedger();

    private WindPact() {
    }

    // ------------------------------------------------------------------ 判定

    /** 风之真名显全了没有（风之石板认的那一套，两端都能算） */
    public static boolean trueNameShown(Player player) {
        return WindTabletItem.attuned(player);
    }

    /** 已经有了「不动如山」：门槛不是 0、好感够、真名已显（不看潜行） */
    public static boolean hasSteadfast(Player player) {
        return reached(player, Config.values().steadfastPoints());
    }

    /** 已经有了「风之偏转」：门槛不是 0、好感够、真名已显 */
    public static boolean hasDeflect(Player player) {
        return reached(player, Config.values().deflectPoints());
    }

    private static boolean reached(Player player, int threshold) {
        return threshold > 0 && !(player instanceof FakePlayer) && WindAffinity.points(player) >= threshold && trueNameShown(player);
    }

    /** 这一下击退要不要取消：潜行中的玩家，有「不动如山」或穿着 1 阶起的风之护甲全套 */
    public static boolean resistsKnockback(LivingEntity entity) {
        return entity instanceof Player player && player.isShiftKeyDown() && (hasSteadfast(player) || windSetSteadfast(player));
    }

    /** 风之护甲全套到了潜行不被击退的阶（{@link WindGearEvents#STEADFAST_STAGE}） */
    public static boolean windSetSteadfast(Player player) {
        return !(player instanceof FakePlayer) && WindArmorItem.fullSetStage(player) >= WindGearEvents.STEADFAST_STAGE;
    }

    /**
     * 风之护甲全套能偏转时返回全套阶；没穿齐、配置关掉、假玩家返回 -1。
     * 先看穿没穿齐（第一个槽位不是风之护甲就返回），穿齐了才读配置：弹射物打中任何玩家都要问一次，绝大多数人没穿这套。
     */
    public static int windSetDeflectStage(Player player) {
        if (player instanceof FakePlayer) {
            return -1;
        }
        int stage = WindArmorItem.fullSetStage(player);
        if (stage < 0 || !ECWindGear.Config.values().deflect()) {
            return -1;
        }
        return stage;
    }

    /**
     * 现在的偏转间隔（刻）：只有契约的「风之偏转」= 契约的间隔；只有风之护甲全套 = 全套这一阶的间隔；两样都有 = 较短的再减半；都没有 = -1（不偏）
     */
    public static int deflectCooldown(Player player) {
        return deflectCooldown(player, windSetDeflectStage(player));
    }

    /** 同上，全套阶已经算好（{@link #windSetDeflectStage}，-1 = 全套不参与） */
    private static int deflectCooldown(Player player, int stage) {
        int pact = hasDeflect(player) ? Config.values().deflectCooldownTicks() : -1;
        int set = stage >= 0 ? ECWindGear.Config.values().deflectCooldown(stage) : -1;
        return combinedCooldown(pact, set);
    }

    /** 契约间隔和全套间隔合起来（-1 = 那一样没有）：两样都有时取较短的再减半 */
    public static int combinedCooldown(int pact, int set) {
        if (pact >= 0 && set >= 0) {
            return Math.min(pact, set) / 2;
        }
        return Math.max(pact, set);
    }

    /**
     * 弹射物打中玩家这一下怎么算：偏开返回 true（调用方把结果改成 {@code SKIP_ENTITY}）。
     * 规则见类注释：自己的弹射物不偏；同一颗偏过的一直放行；新的一颗要冷却已过（间隔见 {@link #deflectCooldown}），偏开时记下这一刻。
     */
    public static boolean deflects(Projectile projectile, Player target) {
        int stage = windSetDeflectStage(target);
        int cooldown = deflectCooldown(target, stage);
        if (cooldown < 0) {
            return false;
        }
        Entity shooter = projectile.getOwner();
        if (shooter != null && shooter.getUUID().equals(target.getUUID())) {
            return false;
        }
        DeflectLedger ledger = target.level().isClientSide ? CLIENT : SERVER;
        UUID id = target.getUUID();
        Set<UUID> passed = ledger.passed.get(projectile);
        if (passed != null && passed.contains(id)) {
            return true;
        }
        long now = target.level().getGameTime();
        Long last = ledger.lastDeflect.get(id);
        if (last != null && now >= last && now - last < cooldown) {
            return false;
        }
        ledger.lastDeflect.put(id, now);
        ledger.passed.computeIfAbsent(projectile, key -> new HashSet<>()).add(id);
        if (target.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.CLOUD, target.getX(), target.getY() + target.getBbHeight() * 0.6D, target.getZ(),
                    6, 0.3D, 0.4D, 0.3D, 0.02D);
            if (stage >= 0) {
                WindGearEvents.onSetDeflected(target, projectile, stage);
            }
        }
        return true;
    }

    /** 服务端：好感够了就发两个进度（已经发过的跳过），第一次发时风在聊天栏说一句（深青斜体，同风之好感的那些话） */
    public static void checkSteps(ServerPlayer player) {
        if (hasSteadfast(player)) {
            awardOnce(player, STEP_STEADFAST, "message.essentialcraft5.wind_pact.steadfast");
        }
        if (hasDeflect(player)) {
            awardOnce(player, STEP_DEFLECT, "message.essentialcraft5.wind_pact.deflect");
        }
    }

    private static void awardOnce(ServerPlayer player, ResourceLocation id, String message) {
        Advancement advancement = player.server.getAdvancements().getAdvancement(id);
        if (advancement != null && !player.getAdvancements().getOrStartProgress(advancement).isDone()) {
            PlayerPentacleBlockEntity.award(player, id, CRITERION);
            player.sendSystemMessage(Component.translatable(message).withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
        }
    }

    /** 偏转冷却还剩几刻（服务端的账，按现在的间隔算；没偏过、已经过了、现在偏不了是 0） */
    public static long deflectCooldownLeft(Player player) {
        Long last = SERVER.lastDeflect.get(player.getUUID());
        if (last == null) {
            return 0L;
        }
        long left = deflectCooldown(player) - (player.level().getGameTime() - last);
        return Math.max(0L, left);
    }

    /** GameTest 用：清掉一名玩家在两端账上的记录 */
    public static void forgetForTest(Player player) {
        SERVER.lastDeflect.remove(player.getUUID());
        CLIENT.lastDeflect.remove(player.getUUID());
    }

    /** 偏转的账：每颗弹射物偏过谁（弱引用，弹射物没了就跟着没了）、每名玩家上次偏开的刻数 */
    private static final class DeflectLedger {
        final Map<Entity, Set<UUID>> passed = new WeakHashMap<>();
        final Map<UUID, Long> lastDeflect = new HashMap<>();
    }

    // ------------------------------------------------------------------ 事件

    /** Forge 总线（类名带模块前缀：同包里简单类名 + 方法名 + 事件类型撞了会静默失效） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
    public static final class WindPactEvents {

        private WindPactEvents() {
        }

        @SubscribeEvent
        public static void pactKnockBack(LivingKnockBackEvent event) {
            if (!event.isCanceled() && resistsKnockback(event.getEntity())) {
                event.setCanceled(true);
            }
        }

        /**
         * 排在 LOW、只改还是 DEFAULT 的结果（别的模组已经定了别的结果、或者取消了的不碰）。客户端只算本地玩家（只有自己的好感同步过来）。
         */
        @SubscribeEvent(priority = EventPriority.LOW)
        public static void pactProjectileImpact(ProjectileImpactEvent event) {
            if (event.isCanceled() || event.getImpactResult() != ProjectileImpactEvent.ImpactResult.DEFAULT
                    || !(event.getRayTraceResult() instanceof EntityHitResult hit) || !(hit.getEntity() instanceof Player target)) {
                return;
            }
            if (target.level().isClientSide && !target.isLocalPlayer()) {
                return;
            }
            if (deflects(event.getProjectile(), target)) {
                event.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
            }
        }

        @SubscribeEvent
        public static void pactPlayerTick(TickEvent.PlayerTickEvent event) {
            if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER
                    || (event.player.tickCount + event.player.getId()) % CHECK_INTERVAL != 0
                    || !(event.player instanceof ServerPlayer player) || player instanceof FakePlayer) {
                return;
            }
            checkSteps(player);
        }

        @SubscribeEvent
        public static void pactLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
            Player player = event.getEntity();
            (player.level().isClientSide ? CLIENT : SERVER).lastDeflect.remove(player.getUUID());
        }
    }

    // ------------------------------------------------------------------ 配置

    /**
     * 服务端配置，写在 {@code general.wind} 里（和风之石板门槛同一节）：{@code steadfast_points}、{@code deflect_points}、{@code deflect_cooldown_ticks}。
     * 由 {@code ECWindMage.init()} 登记（风法师支线）。GameTest 用 {@link #setTestOverrides}，不许用 ConfigValue.set。
     */
    public static final class Config {

        /**
         * @param steadfastPoints      「不动如山」要多少风之好感（0 = 关掉）
         * @param deflectPoints        「风之偏转」要多少风之好感（0 = 关掉）
         * @param deflectCooldownTicks 偏开一颗以后多少刻内新来的照常命中
         */
        public record Values(int steadfastPoints, int deflectPoints, int deflectCooldownTicks) {
        }

        public static final Values DEFAULTS = new Values(7000, 12000, 60);

        private static ForgeConfigSpec.IntValue steadfastPoints;
        private static ForgeConfigSpec.IntValue deflectPoints;
        private static ForgeConfigSpec.IntValue deflectCooldownTicks;
        @Nullable
        private static volatile Values testOverride;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            // 节名和注释由 ECWind.Config 给（同一节 push 两次，值并在一起）
            builder.push("wind");
            steadfastPoints = builder.comment("Wind relation needed (after the wind's true name is revealed) to stand firm: no knockback while sneaking. 0 = off.",
                            "风之真名显全以后，好感到多少获得「不动如山」（潜行时不被击退）；0 = 关掉")
                    .defineInRange("steadfast_points", DEFAULTS.steadfastPoints(), 0, Integer.MAX_VALUE);
            deflectPoints = builder.comment("Wind relation needed (after the true name) for Wind Deflection: other people's projectiles pass through you, "
                                    + "one every deflect_cooldown_ticks. 0 = off.",
                            "风之真名显全以后，好感到多少获得「风之偏转」（别人的弹射物从身上穿过去，每隔 deflect_cooldown_ticks 一颗）；0 = 关掉")
                    .defineInRange("deflect_points", DEFAULTS.deflectPoints(), 0, Integer.MAX_VALUE);
            deflectCooldownTicks = builder.comment("After deflecting one projectile, new ones hit you normally for this many ticks (20 = 1 second).",
                            "偏开一颗以后，多少刻内新来的弹射物照常命中（20 = 1 秒）")
                    .defineInRange("deflect_cooldown_ticks", DEFAULTS.deflectCooldownTicks(), 0, 1200);
            builder.pop();
        }

        public static Values values() {
            Values override = testOverride;
            if (override != null) {
                return override;
            }
            if (steadfastPoints == null) {
                return DEFAULTS;
            }
            return new Values(ECConfig.get(steadfastPoints), ECConfig.get(deflectPoints), ECConfig.get(deflectCooldownTicks));
        }

        /** GameTest 用：整套换成给定的数值，null = 回到配置。同一段同步代码里设、测、置回 */
        public static void setTestOverrides(@Nullable Values values) {
            testOverride = values;
        }
    }
}
