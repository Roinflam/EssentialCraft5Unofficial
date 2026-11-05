package com.flameforge.essentialcraft5.item.module.combat;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.module.ModuleCache;
import com.flameforge.essentialcraft5.registry.ECCombatModules;
import com.flameforge.essentialcraft5.registry.ECEffects;
import com.flameforge.essentialcraft5.util.ECDamageKinds;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 削减模块（第十批 10c-M3，batch10.md §5.12.2；policy §4.13）：开着时，你打实的每一下给目标叠一层「破甲」（10b 的效果 {@code armor_shred}，
 * 每层护甲 −{@code armor_per_level}、盔甲韧性 −{@code toughness_per_level}），最多 {@code max_level} 层，{@code duration_ticks} 刻内没再叠就全掉。
 * 每级扣多少由效果自己按这里的配置算（{@code ECCombatModules.init} 里 {@code ArmorShredEffect.setPerLevel}），谁加的破甲都一样。
 *
 * <h2>「打实」的口径（同 10b 的透镜、余烬嵌刻，policy §4.12）</h2>
 * 伤害的真正来源（{@code getEntity()}，弹射物算射手）是开着这块模块的真玩家：近战、弹射物、射线、法术、饰品打出的（ν 的连锁、ι 的魔法伤害，都是你这一下引出来的）都算；
 * <b>反伤不算</b>（{@code ECDamageKinds.isReflected}：荆棘附魔、宅男全套的反伤、σ 的反弹，以及 {@code essentialcraft5:reflected} 里别的模组的反伤）——
 * 那是别人打你、自动打回去的一下，不是「你打实的一下」（batch10 §5.12.2），算的话穿宅男全套的人身上就成了谁打他谁吃破甲的光环。
 * 反伤的作用域只在那一下的 {@code hurt} 里有效，所以在事件发生的当时判（{@link #shredder}）。{@code LivingDamageEvent} 里记下事件对象，
 * <b>本刻末</b>（{@code ServerTickEvent} END）再看：最终没被取消、伤害 &gt; 0 才叠——排在后面的监听器取消的、改成 0 的（Mohist 系混合端在别的模组取消原伤害、
 * 举盾整下挡掉时照样按 0 发 {@code LivingDamageEvent}）都不叠；混合端插件拦下的那一下根本不发 {@code LivingDamageEvent}，什么都不发生。
 * 伤害是 0 但吃掉了目标伤害吸收（金苹果的黄心）的也算打实：{@code LivingHurtEvent} 最低优先级记下当时的伤害吸收，到 {@code LivingDamageEvent} 时比一比（同 {@code InlayEvents}）。
 * 同一刻、同一名玩家对同一个生物只叠一层（别的模组的武器在攻击时附带的那一段和主击是一次攻击）。
 *
 * <h2>叠法</h2>
 * 新等级 = 目标身上已有的破甲等级 + 1（没有就是 I 级），封顶 {@code max_level}；持续时间每次刷新成 {@code duration_ticks}（一个效果实例，到期一起掉）。
 * 走 {@code addEffect(实例, 攻击者)}：对状态效果免疫的（{@code MobEffectEvent.Applicable} 拒绝、像原版凋灵那样把 {@code addEffect} 覆盖成一律拒绝的）照它的设计不吃；
 * 牛奶和别的模组的净化能清掉。只给效果、不加伤害、不另发伤害事件。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class ShredEvents {

    /** 一刻最多记这么多条待结算的命中、伤害吸收记录 */
    public static final int MAX_PENDING = 256;
    /** 伤害吸收少了多少才算吃掉了（浮点误差以上） */
    private static final float ABSORPTION_EPS = 1.0E-4F;

    /** LivingHurtEvent（最低优先级）时的记录：目标、伤害源（按对象比）、那一刻、那时目标的伤害吸收 */
    private record HurtNote(LivingEntity target, DamageSource source, long tick, float absorptionBefore) {
    }

    /** 待结算的一下；absorbed = 这一下吃掉了目标的伤害吸收 */
    private record Pending(LivingDamageEvent event, ServerPlayer attacker, LivingEntity target, boolean absorbed) {
    }

    /** 结算时去重的键：攻击者、目标（按对象比） */
    private record HitKey(UUID attacker, LivingEntity target) {
    }

    private static final List<HurtNote> NOTES = new ArrayList<>();
    private static final List<Pending> PENDING = new ArrayList<>();

    private ShredEvents() {
    }

    // ------------------------------------------------------------------ 配置

    /**
     * {@code general.computer_modules.shred}：{@code armor_per_level}（每层扣的护甲，默认 2）、{@code toughness_per_level}（每层扣的盔甲韧性，默认 1）、
     * {@code max_level}（最多几层，默认 5）、{@code duration_ticks}（多少刻没再叠就全掉，默认 100）。前两项也是破甲效果本身每级扣的量。
     */
    public static final class Config {

        /**
         * @param armorPerLevel     每层扣的护甲
         * @param toughnessPerLevel 每层扣的盔甲韧性
         * @param maxLevel          最多几层
         * @param durationTicks     多少刻没再叠就全掉
         */
        public record Values(double armorPerLevel, double toughnessPerLevel, int maxLevel, int durationTicks) {
        }

        public static final Values DEFAULTS = new Values(2D, 1D, 5, 100);

        @Nullable
        private static ForgeConfigSpec.DoubleValue armorPerLevel;
        @Nullable
        private static ForgeConfigSpec.DoubleValue toughnessPerLevel;
        @Nullable
        private static ForgeConfigSpec.IntValue maxLevel;
        @Nullable
        private static ForgeConfigSpec.IntValue durationTicks;
        @Nullable
        private static volatile Values testOverride;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.comment("Shred Module: every hit you land adds a level of Armor Shred to the target.",
                    "削减模块：打实的每一下给目标叠一层破甲").push("shred");
            armorPerLevel = builder.comment("Armor removed per level of Armor Shred (also used by Armor Shred from any other source).",
                            "每层破甲扣的护甲（别的来源加的破甲也按这个算）")
                    .defineInRange("armor_per_level", DEFAULTS.armorPerLevel(), 0D, 20D);
            toughnessPerLevel = builder.comment("Armor toughness removed per level of Armor Shred.", "每层破甲扣的盔甲韧性")
                    .defineInRange("toughness_per_level", DEFAULTS.toughnessPerLevel(), 0D, 20D);
            maxLevel = builder.comment("Highest level the Shred Module stacks Armor Shred to.", "削减模块最多叠到几层")
                    .defineInRange("max_level", DEFAULTS.maxLevel(), 1, 10);
            durationTicks = builder.comment("Ticks before all levels wear off if no new hit lands (refreshed on every hit).",
                            "多少刻（tick）没再叠就全部消失（每打实一下重新计时）")
                    .defineInRange("duration_ticks", DEFAULTS.durationTicks(), 1, 6000);
            builder.pop();
        }

        /** 现在的数值：测试覆盖值 → 配置（没加载时默认值） */
        public static Values values() {
            Values override = testOverride;
            if (override != null) {
                return override;
            }
            if (armorPerLevel == null) {
                return DEFAULTS;
            }
            return new Values(ECConfig.get(armorPerLevel), ECConfig.get(toughnessPerLevel), ECConfig.get(maxLevel), ECConfig.get(durationTicks));
        }

        /** GameTest 用：整套换成给定的数值，null = 回到配置。同一段同步代码里设、测、置回 */
        public static void setTestOverrides(@Nullable Values values) {
            testOverride = values;
        }
    }

    // ------------------------------------------------------------------ 事件

    /** 记下这一下在 LivingHurtEvent 时目标的伤害吸收（最低优先级、不收已取消的：别的模组的加减都已算完），换刻就清掉旧的 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onShredHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide() || !(event.getAmount() > 0F)) {
            return;
        }
        ServerPlayer attacker = shredder(event.getSource(), target);
        if (attacker == null) {
            return;
        }
        long now = target.level().getGameTime();
        if (!NOTES.isEmpty()) {
            NOTES.removeIf(note -> note.tick() != now);
        }
        if (NOTES.size() < MAX_PENDING) {
            NOTES.add(new HurtNote(target, event.getSource(), now, target.getAbsorptionAmount()));
        }
    }

    /** 伤害到了：记下事件对象，本刻末再看它最终打没打实 */
    @SubscribeEvent
    public static void onShredDamage(LivingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) {
            return;
        }
        ServerPlayer attacker = shredder(event.getSource(), target);
        if (attacker == null) {
            return;
        }
        boolean absorbed = claimAbsorbed(target, event.getSource());
        if ((event.getAmount() > 0F || absorbed) && PENDING.size() < MAX_PENDING) {
            PENDING.add(new Pending(event, attacker, target, absorbed));
        }
    }

    @SubscribeEvent
    public static void onShredServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!PENDING.isEmpty()) {
            flush();
        }
        if (!NOTES.isEmpty()) {
            NOTES.clear();   // 没等到 LivingDamageEvent 的（被拦下、被取消）
        }
    }

    @SubscribeEvent
    public static void onShredServerStopping(ServerStoppingEvent event) {
        PENDING.clear();
        NOTES.clear();
    }

    /**
     * 结算这一刻记下的命中，返回叠了几次。本刻末自动调；GameTest 在 hurt 之后直接调。
     * 同一名玩家对同一个生物只结算第一条打实的（前面没成的让后面的顶上）。
     */
    public static int flush() {
        if (PENDING.isEmpty()) {
            return 0;
        }
        List<Pending> batch = new ArrayList<>(PENDING);
        PENDING.clear();
        Set<HitKey> done = new HashSet<>();
        int applied = 0;
        for (Pending pending : batch) {
            HitKey key = new HitKey(pending.attacker().getUUID(), pending.target());
            if (done.contains(key)) {
                continue;
            }
            LivingDamageEvent event = pending.event();
            if (event.isCanceled() || !(event.getAmount() > 0F || pending.absorbed()) || !pending.target().isAlive()) {
                continue;
            }
            done.add(key);
            if (shred(pending.target(), pending.attacker())) {
                applied++;
            }
        }
        return applied;
    }

    /**
     * 给 target 叠一层破甲（见类注释「叠法」）。
     *
     * @return 加上了（对状态效果免疫的返回 false）
     */
    public static boolean shred(LivingEntity target, @Nullable ServerPlayer attacker) {
        if (!ECEffects.ARMOR_SHRED.isPresent()) {
            return false;
        }
        Config.Values cfg = Config.values();
        MobEffectInstance current = target.getEffect(ECEffects.ARMOR_SHRED.get());
        int top = Math.max(0, cfg.maxLevel() - 1);
        int amplifier = current == null ? 0 : Math.min(current.getAmplifier() + 1, top);
        return target.addEffect(new MobEffectInstance(ECEffects.ARMOR_SHRED.get(), cfg.durationTicks(), amplifier, false, true, true), attacker);
    }

    /** 伤害的真正来源是开着削减模块的真玩家（不是目标自己）、而且不是反伤时返回他 */
    @Nullable
    private static ServerPlayer shredder(DamageSource source, LivingEntity target) {
        if (source.getEntity() instanceof ServerPlayer player && player != target && ECProtection.isRealPlayer(player)
                && !ECDamageKinds.isReflected(source) && ModuleCache.active(player, ECCombatModules.SHRED)) {
            return player;
        }
        return null;
    }

    /**
     * 这一下有没有吃掉目标的伤害吸收：找同一个目标、同一个伤害源对象、同一刻在 {@link #onShredHurt} 记下的那条（找到就删），
     * 比那时和现在（LivingDamageEvent 时已经扣过）的伤害吸收。没有记录的（LivingHurtEvent 被取消、伤害是 0）不算。
     */
    private static boolean claimAbsorbed(LivingEntity target, DamageSource source) {
        long now = target.level().getGameTime();
        for (int i = NOTES.size() - 1; i >= 0; i--) {
            HurtNote note = NOTES.get(i);
            if (note.target() == target && note.source() == source && note.tick() == now) {
                NOTES.remove(i);
                return note.absorptionBefore() - target.getAbsorptionAmount() > ABSORPTION_EPS;
            }
        }
        return false;
    }

    /** 还没结算的条数（测试看） */
    public static int pendingCount() {
        return PENDING.size();
    }
}
