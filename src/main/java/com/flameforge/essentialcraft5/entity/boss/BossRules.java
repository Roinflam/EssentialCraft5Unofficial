package com.flameforge.essentialcraft5.entity.boss;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.registry.ECBossFramework;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.util.ECDamageKinds;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 首领的受伤、效果、骑乘规矩（第十三批 13c-Fb，batch13.md §5.3「共同」、policy §4.18「首领通用件」），按 {@link BossTier} 给，
 * 全走 Forge 事件（{@link BossRuleEvents}），所以不管首领继承哪个基类都一样。
 *
 * <h2>谁打得动（Boss 档，{@link #accepts}）</h2>
 * 没有攻击者、带 {@code bypasses_invulnerability} 的（管理员敲的 {@code /kill}、真掉出世界，{@link #unblockable}）照常；有攻击者的同类伤害
 * （整合包里的「真伤」武器）和普通伤害一样过下面这几关、照样封顶，不给玩家开后门。{@code orbital_strike} 一律拒（别人遥控器的谓词可能放行首领）；
 * 其余要攻击者是真玩家（假玩家、生物、宠物、没有攻击者的都打不动），不是旁观者；生存 / 冒险的还要和首领同一个世界、离它不超过
 * {@code engageRange}——在远处打一下就退开的人，技能落下时已经不在名单里，等于白打不挨打（同全息投影）。创造模式不限距离（管理员排查用），
 * 但不进名单、不分掉落。不受摔落、窒息（卡墙）、溺水、挤压。拒绝都在 {@code LivingAttackEvent}（{@code hurt} 的第一步）里取消，
 * 混合端的 Bukkit 伤害事件还没发，不留副作用。小 Boss 不过这一关：谁打都算。
 *
 * <h2>打折（Boss 档，{@link #nonMeleeMultiplier}）</h2>
 * 不是近战的（{@code ECDamageKinds.isMelee} 为假：箭、本模组射线、魔法、爆炸、荆棘）在 {@code LivingHurtEvent} HIGHEST 乘
 * {@code general.mobs.boss.non_melee_multiplier}（默认 0.5），在护甲和别的模组的加成之前，和全息投影在 {@code hurt} 开头除 4 是同一个位置。
 *
 * <h2>别的</h2>
 * <ul>
 *   <li>Boss 档不吃状态效果，发光除外（{@code MobEffectEvent.Applicable} 判 DENY；{@link ECBossMonster} 另覆盖 {@code canBeAffected}）；</li>
 *   <li>Boss 档没有摔落（{@code LivingFallEvent} 取消，连落地粒子都没有）、不吃击退（{@code LivingKnockBackEvent} 取消）；</li>
 *   <li>两档都不能骑任何载具（船、矿车、别的生物；{@code EntityMountEvent} 取消上去的那一下，下来不拦），船、矿车困不住它；</li>
 *   <li>玩家死了当场从所有首领的名单里移出（{@link BossRoster#forgetFighter}；不收已取消的：被救回来的不算死）。</li>
 * </ul>
 */
public final class BossRules {

    private BossRules() {
    }

    /** 能参战的游戏模式：生存 / 冒险 */
    public static boolean fighting(Player player) {
        return !player.isCreative() && !player.isSpectator();
    }

    /**
     * 管理员 / 环境的绕过伤害：带 {@code bypasses_invulnerability}、而且没有攻击者（{@code /kill}、掉出世界）。这类伤害不夹封顶、不扣每秒桶、
     * 换相散去时照样打得动。有攻击者的同类伤害不算：玩家拿「真伤」武器打出来的照普通伤害走参战距离、封顶、无敌（batch13.md §5.3「共同」）。
     * <p>
     * 只认两种：{@code generic_kill} 要这一刻真有人敲了 {@code /kill}（{@link #killCommandActive}），{@code fell_out_of_world}
     * 要首领真掉到了世界底下。别的代码调 {@code kill()}、拿这两种伤害「兜底处决」、或者别的绕过无敌又没有攻击者的伤害类型，
     * 都照普通的一下走（夹封顶、扣每秒桶）。
     */
    public static boolean unblockable(LivingEntity entity, DamageSource source) {
        if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.getEntity() != null) {
            return false;
        }
        if (source.is(DamageTypes.GENERIC_KILL)) {
            return killCommand;
        }
        return source.is(DamageTypes.FELL_OUT_OF_WORLD) && entity.getY() < entity.level().getMinBuildHeight();
    }

    /** 这一刻有没有人执行过 {@code /kill}（含 {@code execute ... run kill}；本刻末清掉） */
    public static boolean killCommandActive() {
        return killCommand;
    }

    /** GameTest：假装这一刻有人敲了 {@code /kill} */
    public static void setKillCommandForTest(boolean active) {
        killCommand = active;
    }

    private static boolean killCommand;

    /** 这条指令是不是 {@code /kill}（含 {@code execute ... run kill}） */
    public static boolean isKillCommand(String input) {
        String command = input.startsWith("/") ? input.substring(1) : input;
        command = command.trim();
        return command.equals("kill") || command.startsWith("kill ") || command.startsWith("minecraft:kill") || command.contains(" run kill")
                || command.contains(" run minecraft:kill");
    }

    /** Boss 档这一下打不打得动（见类注释）；小 Boss 一律 true */
    public static boolean accepts(ECBoss boss, DamageSource source) {
        if (!boss.bossTier().fightersOnly() || unblockable(boss.bossEntity(), source)) {
            return true;
        }
        if (source.is(ECDamageTypes.ORBITAL_STRIKE) || ignoresEnvironment(boss, source)) {
            return false;
        }
        Entity attacker = source.getEntity();
        if (!(attacker instanceof ServerPlayer player) || !ECProtection.isRealPlayer(player) || player.isSpectator()) {
            return false;
        }
        if (player.isCreative()) {
            return true;
        }
        LivingEntity entity = boss.bossEntity();
        return player.level() == entity.level() && player.distanceTo(entity) <= boss.rosterSettings().engageRange();
    }

    /** Boss 档挡掉的环境伤害：摔落、卡墙窒息、溺水、挤压 */
    public static boolean ignoresEnvironment(ECBoss boss, DamageSource source) {
        if (!boss.bossTier().environmentImmune()) {
            return false;
        }
        return source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING)
                || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING);
    }

    /** 这一下在护甲之前乘多少：Boss 档不是近战的乘配置值，其余 1 */
    public static float nonMeleeMultiplier(ECBoss boss, DamageSource source) {
        if (!boss.bossTier().fightersOnly() || unblockable(boss.bossEntity(), source) || ECDamageKinds.isMelee(source)) {
            return 1F;
        }
        return (float) ECBossFramework.Config.values().nonMeleeMultiplier();
    }

    /** 这个效果加不加得上：Boss 档只认发光 */
    public static boolean effectAllowed(ECBoss boss, MobEffectInstance effect) {
        return !boss.bossTier().effectImmune() || effect.getEffect() == MobEffects.GLOWING;
    }

    /**
     * 首领的技能打不打这个目标：名单里在线、同世界、生存 / 冒险的参战者（batch13.md §5.3.2「技能只打参战者」）。
     * 村民、宠物、动物、盔甲架、旁观者、路过的玩家都不碰；要连敌对生物一起打的首领自己另加条件。
     */
    public static boolean isSkillTarget(ECBoss boss, LivingEntity target) {
        if (!(target instanceof ServerPlayer player) || !player.isAlive() || player.level() != boss.bossEntity().level() || !fighting(player)) {
            return false;
        }
        return boss.bossRoster().contains(player.getUUID());
    }

    /** Forge 总线（类名带模块前缀，见 tools/check_event_handlers.py） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
    public static final class BossRuleEvents {

        private BossRuleEvents() {
        }

        /** 记下这一刻有人执行 {@code /kill}（玩家、控制台、命令方块都走这里；被别的处理器取消的不算） */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void bossRuleKillCommand(CommandEvent event) {
            if (!event.isCanceled() && isKillCommand(event.getParseResults().getReader().getString())) {
                killCommand = true;
            }
        }

        /** {@code /kill} 的标记只管当刻：玩家的指令在两刻之间执行、命令方块在刻中执行，前后各清一次 */
        @SubscribeEvent
        public static void bossRuleKillCommandClear(TickEvent.ServerTickEvent event) {
            killCommand = false;
        }

        /** Boss 档谁打得动、环境伤害（HIGH：在别的模组按伤害做事之前就拦下） */
        @SubscribeEvent(priority = EventPriority.HIGH)
        public static void bossRuleAttack(LivingAttackEvent event) {
            if (event.getEntity() instanceof ECBoss boss && !event.getEntity().level().isClientSide && !accepts(boss, event.getSource())) {
                event.setCanceled(true);
            }
        }

        /** Boss 档不是近战的打折（HIGHEST：在护甲和别的模组的加成之前） */
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void bossRuleNonMelee(LivingHurtEvent event) {
            if (event.getEntity() instanceof ECBoss boss && !event.getEntity().level().isClientSide) {
                float multiplier = nonMeleeMultiplier(boss, event.getSource());
                if (multiplier != 1F) {
                    event.setAmount(event.getAmount() * multiplier);
                }
            }
        }

        /** Boss 档不吃状态效果（发光除外） */
        @SubscribeEvent
        public static void bossRuleEffect(MobEffectEvent.Applicable event) {
            if (event.getEntity() instanceof ECBoss boss && !effectAllowed(boss, event.getEffectInstance())) {
                event.setResult(Event.Result.DENY);
            }
        }

        /** 首领不骑任何载具（上去的那一下取消，下来不拦） */
        @SubscribeEvent
        public static void bossRuleMount(EntityMountEvent event) {
            if (event.isMounting() && event.getEntityMounting() instanceof ECBoss) {
                event.setCanceled(true);
            }
        }

        /** Boss 档没有摔落 */
        @SubscribeEvent
        public static void bossRuleFall(LivingFallEvent event) {
            if (event.getEntity() instanceof ECBoss boss && boss.bossTier().environmentImmune()) {
                event.setCanceled(true);
            }
        }

        /** Boss 档不吃击退 */
        @SubscribeEvent
        public static void bossRuleKnockback(LivingKnockBackEvent event) {
            if (event.getEntity() instanceof ECBoss boss && boss.bossTier().immovable()) {
                event.setCanceled(true);
            }
        }

        /** 玩家死了就从所有首领的名单里移出（不收已取消的：被救回来的不算） */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void bossRuleFighterDeath(LivingDeathEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                BossRoster.forgetFighter(player.getUUID());
            }
        }
    }
}
