package com.flameforge.essentialcraft5.item.module.combat;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.module.ModuleCache;
import com.flameforge.essentialcraft5.registry.ECCombatModules;
import com.flameforge.essentialcraft5.registry.ECEffects;
import com.flameforge.essentialcraft5.util.ECCombatTags;
import com.flameforge.essentialcraft5.util.ECEntityRules;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 相位锚模块（第十批 10c-M3，batch10.md §5.12.3；policy §4.13，D12 定为 A）：开着时（每秒 {@code mru_per_second}，由模块平台的 {@code ModuleTicker} 扣），
 * 周围 {@code radius} 格的敌对生物、10 秒内和你<b>互相</b>交过手的玩家身上挂「锚定」（10b 的效果 {@code anchored}）：
 * 末影人 / 潜影贝的瞬移、末影珍珠、紫颂果都传不走。取消传送由效果自己的处理做（{@code effect/EffectEvents}），这里只管给谁挂。
 *
 * <h2>每秒一次</h2>
 * 每名开着这块模块的玩家每 {@value #INTERVAL} 刻扫一次（玩家刻末尾，按 {@code (tickCount + 实体 id) % 20 == }{@value #PHASE} 错开，
 * 和模块平台每秒记账的那一刻错开半秒，读到的是刚扣成的电费）：以玩家为中心、边长 {@code 2 × radius + 1} 的立方（默认 33³）里的活体，
 * <ul>
 *   <li>敌对生物（{@link Enemy}），跳过 {@code ECEntityRules.isSpared}（起了名字的、拴着绳的……）和 {@code #essentialcraft5:anchor_immune}；</li>
 *   <li>玩家：{@code affect_players} 开着、是真玩家、不是旁观 / 创造，主人现在打得了他（{@code canHarmPlayer}：PvP、队伍），而且双方都在 {@code combat_ticks}
 *       刻内对对方有过一下没被拦下的攻击（{@code ECCombatTags.inMutualCombat}：那两下都已经过了 PvP / 队伍 / 领地的门，打中了或者被盾牌、盔甲整下挡掉都算；
 *       荆棘、反伤这类挨打后自动打回去的，和雪球这种原值就是 0 的，不算对方动手）。只是一方动过手的不锚。</li>
 * </ul>
 * 挂 {@code effect_ticks} 刻的锚定，只在身上没有、或者剩下不到 {@value #REFRESH_BELOW} 刻时才刷新（policy §1.2：药水效果只在没有或快到期时刷新）。
 * 走 {@code addEffect(实例, 主人)}，对状态效果免疫的首领照它的设计不吃。不困走路、不拽人，也不做「瞬移了再拽回来」（会打断首领的招式）。
 * 模组怪自己调 {@code teleportTo} / {@code randomTeleport} 不发事件的拦不住。
 * <p>
 * 只改别的生物身上的一个状态效果，不改世界、不发伤害事件，单人 / 纯 Forge / 混合端一样。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class PhaseAnchorEvents {

    /** 扫描间隔（刻） */
    public static final int INTERVAL = 20;
    /** 在 20 刻里的哪一刻扫（模块平台在第 0 刻记账，这里晚半秒） */
    public static final int PHASE = 10;
    /** 身上的锚定剩下不到这么多刻才刷新（比扫描间隔多 2 刻余量） */
    public static final int REFRESH_BELOW = INTERVAL + 2;

    private static volatile Set<EntityType<?>> testImmune = Set.of();

    private PhaseAnchorEvents() {
    }

    // ------------------------------------------------------------------ 配置

    /**
     * {@code general.computer_modules.phase_anchor}：{@code radius}（16）、{@code mru_per_second}（200）、{@code affect_players}（true）、
     * {@code combat_ticks}（200 = 10 秒）、{@code effect_ticks}（40）。
     */
    public static final class Config {

        /**
         * @param radius        以玩家为中心的立方半径（格）
         * @param mruPerSecond  开着时每秒的电费
         * @param affectPlayers 锚不锚玩家
         * @param combatTicks   「互相交过手」的时间窗（刻）
         * @param effectTicks   每次挂的锚定时长（刻）
         */
        public record Values(int radius, int mruPerSecond, boolean affectPlayers, int combatTicks, int effectTicks) {
        }

        public static final Values DEFAULTS = new Values(16, 200, true, 200, 40);

        @Nullable
        private static ForgeConfigSpec.IntValue radius;
        @Nullable
        private static ForgeConfigSpec.IntValue mruPerSecond;
        @Nullable
        private static ForgeConfigSpec.BooleanValue affectPlayers;
        @Nullable
        private static ForgeConfigSpec.IntValue combatTicks;
        @Nullable
        private static ForgeConfigSpec.IntValue effectTicks;
        @Nullable
        private static volatile Values testOverride;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.comment("Phase Anchor Module: hostile mobs around you, and players who recently traded blows with you, cannot teleport away.",
                    "相位锚模块：周围的敌对生物、最近和你互相交过手的玩家传不走").push("phase_anchor");
            radius = builder.comment("Half-size in blocks of the cube around the wearer that is checked once a second.",
                            "以穿戴者为中心、每秒查一次的立方半径（格）")
                    .defineInRange("radius", DEFAULTS.radius(), 1, 32);
            mruPerSecond = builder.comment("MRU per second while the module is on (paid by the module upkeep once a second).",
                            "开着时每秒耗的 MRU（模块每秒统一扣电时一起扣）")
                    .defineInRange("mru_per_second", DEFAULTS.mruPerSecond(), 0, 100_000);
            affectPlayers = builder.comment("Whether players who recently traded blows with the wearer are anchored too.",
                            "是否也锚住最近和穿戴者互相交过手的玩家")
                    .define("affect_players", DEFAULTS.affectPlayers());
            combatTicks = builder.comment("Both players must have had an attack on each other that wasn't stopped (a hit, or one fully blocked by a shield or armor) within this many ticks.",
                            "双方都要在这么多刻（tick）内对对方有过一下没被拦下的攻击（打中了，或者被盾牌、盔甲整下挡掉都算）")
                    .defineInRange("combat_ticks", DEFAULTS.combatTicks(), 20, 1200);
            effectTicks = builder.comment("Ticks of Anchored given each time; must outlast the one-second check.",
                            "每次挂的锚定时长（刻），要比每秒一次的检查长")
                    .defineInRange("effect_ticks", DEFAULTS.effectTicks(), REFRESH_BELOW, 600);
            builder.pop();
        }

        /** 现在的数值：测试覆盖值 → 配置（没加载时默认值） */
        public static Values values() {
            Values override = testOverride;
            if (override != null) {
                return override;
            }
            if (radius == null) {
                return DEFAULTS;
            }
            return new Values(ECConfig.get(radius), ECConfig.get(mruPerSecond), ECConfig.get(affectPlayers), ECConfig.get(combatTicks),
                    ECConfig.get(effectTicks));
        }

        /** GameTest 用：整套换成给定的数值，null = 回到配置。同一段同步代码里设、测、置回 */
        public static void setTestOverrides(@Nullable Values values) {
            testOverride = values;
        }
    }

    // ------------------------------------------------------------------ 事件

    @SubscribeEvent
    public static void onPhaseAnchorPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player instanceof FakePlayer) {
            return;
        }
        if (Math.floorMod(player.tickCount + player.getId(), INTERVAL) != PHASE) {
            return;
        }
        tick(player);
    }

    /**
     * 一次每秒的处理：模块生效（含这一秒的电费扣成了）才扫周围（GameTest 先 {@code ModuleTicker.pay} 再直接调）。
     *
     * @return 这次挂上 / 刷新了几个
     */
    public static int tick(ServerPlayer owner) {
        if (owner.isSpectator() || !ModuleCache.active(owner, ECCombatModules.PHASE_ANCHOR)) {
            return 0;
        }
        Config.Values cfg = Config.values();
        List<LivingEntity> candidates = owner.level().getEntitiesOfClass(LivingEntity.class, box(owner, cfg), entity -> entity != owner && entity.isAlive());
        return anchor(owner, candidates, owner.server.getTickCount());
    }

    /**
     * 对给定的一批候选人逐个判、挂锚定（不看模块开没开、不扣电；GameTest 用它测不在世界里的测试玩家）。范围外的跳过。
     *
     * @param now 服务端刻数（{@code server.getTickCount()}，交手记录按它算）
     * @return 这次挂上 / 刷新了几个
     */
    public static int anchor(ServerPlayer owner, Collection<? extends LivingEntity> candidates, int now) {
        if (!ECEffects.ANCHORED.isPresent()) {
            return 0;
        }
        Config.Values cfg = Config.values();
        AABB box = box(owner, cfg);
        int anchored = 0;
        for (LivingEntity entity : candidates) {
            if (entity == owner || !entity.isAlive() || entity.level() != owner.level() || !entity.getBoundingBox().intersects(box)
                    || !shouldAnchor(owner, entity, cfg, now)) {
                continue;
            }
            MobEffectInstance current = entity.getEffect(ECEffects.ANCHORED.get());
            if (current != null && current.getDuration() >= REFRESH_BELOW) {
                continue;
            }
            if (entity.addEffect(new MobEffectInstance(ECEffects.ANCHORED.get(), cfg.effectTicks(), 0, true, true, true), owner)) {
                anchored++;
            }
        }
        return anchored;
    }

    /** 这只生物该不该被 owner 锚住（见类注释） */
    public static boolean shouldAnchor(ServerPlayer owner, LivingEntity entity, Config.Values cfg, int now) {
        if (entity instanceof Player target) {
            return cfg.affectPlayers() && target instanceof ServerPlayer other && ECProtection.isRealPlayer(other)
                    && !other.isSpectator() && !other.isCreative() && owner.canHarmPlayer(other)
                    && ECCombatTags.inMutualCombat(owner, other, now, cfg.combatTicks());
        }
        return entity instanceof Enemy && !ECEntityRules.isSpared(entity) && !isImmune(entity.getType());
    }

    /** 在 {@code #essentialcraft5:anchor_immune} 里（或 GameTest 临时加的） */
    public static boolean isImmune(EntityType<?> type) {
        return type.is(ECCombatModules.ANCHOR_IMMUNE) || (!testImmune.isEmpty() && testImmune.contains(type));
    }

    /** GameTest 用：临时当成也在 anchor_immune 里的实体类型（运行中改不了标签），空集合 = 撤掉。同一段同步代码里设、测、置回 */
    public static void setImmuneForTest(Set<EntityType<?>> types) {
        testImmune = Set.copyOf(types);
    }

    /** 以 owner 为中心、边长 2 × radius + 1 的立方 */
    private static AABB box(ServerPlayer owner, Config.Values cfg) {
        double size = 2D * cfg.radius() + 1D;
        return AABB.ofSize(owner.position(), size, size, size);
    }
}
