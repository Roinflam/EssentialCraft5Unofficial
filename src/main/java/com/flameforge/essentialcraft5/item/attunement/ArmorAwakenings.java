package com.flameforge.essentialcraft5.item.attunement;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.item.InquisitoriumArmorEvents;
import com.flameforge.essentialcraft5.item.VerdictBladeItem;
import com.flameforge.essentialcraft5.item.armor.MRUShieldAbsorbedEvent;
import com.flameforge.essentialcraft5.network.GearFx;
import com.flameforge.essentialcraft5.network.S2CGearFxPacket;
import com.flameforge.essentialcraft5.registry.ECAttunement;
import com.flameforge.essentialcraft5.registry.ECGearSounds;
import com.flameforge.essentialcraft5.util.ECHurtWindow;
import com.flameforge.essentialcraft5.util.ECLandedHit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 五套盔甲的满阶觉醒（方案稿推荐档 3.3，配置在 {@link ArmorGrowth}）。都要全套、按四件里最低的阶算解锁；只在服务端。
 * <ul>
 *   <li><b>回涌</b>（魔力防护 2 阶）：护盾挡下的伤害按 {@code ratio} 存进回涌值（最多 {@code max}）；存够 {@code threshold} 后，你下一次亲手近战打实，
 *       本刻末在身边半径 {@code radius} 放出去，每个目标吃回涌值（{@code gear_burst}），然后清零。</li>
 *   <li><b>风暴步</b>（风之 3 阶）：服务端认出一次空中跳（{@code WindGearEvents.trackMotion}）时，脚下半径 {@code radius} 的敌对生物被推开、缓慢 I；
 *       之后 {@code window} 刻内你的下一下亲手近战增伤池 +{@code bonus}（池来源 {@code storm_step}，问到就用掉）。不打伤害，<b>不碰玩家</b>
 *       （只挂效果、不走 {@code hurt} 的东西绕得过 PvP 和领地，所以哪怕开了 {@code aoe_hits_players} 也跳过玩家）。</li>
 *   <li><b>残像</b>（虚空 4 阶）：胸甲闪避掉一下有活物攻击者的伤害时，原地留残影，{@code delay} 刻后炸开：半径内每个目标吃
 *       min(被躲开那一下 × {@code ratio}, {@code cap})，打成了再挂缓慢 II；冷却 {@code cooldown} 刻。</li>
 *   <li><b>宣判</b>（审判庭 4 阶）：被你定罪的目标挨你亲手打实满 {@code hits} 下时宣判（同一个目标 {@code cooldown} 刻一次）：
 *       普通生物本刻末补 min(最大生命 × {@code percent}%, {@code cap})（伤害类型 {@code verdict}，封顶登记同裁断）；首领不另打，
 *       宣判的那一下增伤池 +{@code boss_pool}（池来源 {@code sentence}，照首领上限）；玩家不补。</li>
 *   <li><b>超载脉冲</b>（宅男 5 阶）：过载屏障展开时，本刻末在身边半径 {@code radius} 放脉冲，每个目标吃 min(被挡那一下 × {@code ratio}, {@code cap})，
 *       打成了再挂缓慢 III。</li>
 * </ul>
 * 范围伤害都走 {@link GearAoe}：伤害源带攻击者（PvP、队伍、领地照管）、不连锁、不吃增伤池、默认不打玩家；会挂效果的都是「打成了才挂」。
 * 打人的都排到本刻末再打（{@link #TASKS}），不在别的生物挨打的事件里面套着打。事件包 {@link GearFx} 的 {@code SURGE} / {@code STORM_STEP} /
 * {@code AFTERIMAGE}（param 0 = 留下残影，&gt; 0 = 炸开的半径）/ {@code SENTENCE} / {@code OVERCHARGE_PULSE}，画面在 {@code client/GearFxClient}。
 */
public final class ArmorAwakenings {

    /** 一刻最多排这么多件待办（正常一刻只有几件） */
    public static final int MAX_TASKS = 256;

    private record Task(long due, Runnable run) {
    }

    private static final class Count {
        UUID player;
        int hits;
        long readyAt = Long.MIN_VALUE;
    }

    private static final List<Task> TASKS = new ArrayList<>();
    private static final Map<Player, Double> SURGE = new WeakHashMap<>();
    private static final Map<Player, Long> STORM_UNTIL = new WeakHashMap<>();
    private static final Map<Player, Long> AFTERIMAGE_READY = new WeakHashMap<>();
    private static final Map<LivingEntity, Count> SENTENCE = new WeakHashMap<>();

    private ArmorAwakenings() {
    }

    /** {@code ECAttunement.init} 调：风暴步、宣判进增伤池 */
    public static void registerGearSources() {
        GearPool.registerOffense("storm_step", context -> {
            if (!(context.attacker() instanceof ServerPlayer player) || !ArmorGrowth.ownMelee(context.source(), player, context.target())) {
                return 0D;
            }
            Long until = STORM_UNTIL.get(player);
            if (until == null || now(player) > until || Attunement.fullSetStage(player, ECAttunement.WIND_ARMOR) < ArmorGrowth.stormStage()) {
                return 0D;
            }
            STORM_UNTIL.remove(player);
            return ArmorGrowth.stormBonus() * context.pvpScale();
        });
        GearPool.registerOffense("sentence", context -> {
            if (context.targetClass() != GearCaps.TargetClass.BOSS || !(context.attacker() instanceof ServerPlayer player)) {
                return 0D;
            }
            return sentenceDue(player, context.target(), context.source()) ? ArmorGrowth.sentenceBossPool() : 0D;
        });
    }

    // ------------------------------------------------------------------
    // 回涌
    // ------------------------------------------------------------------

    /** 现在存着的回涌值 */
    public static double surge(Player player) {
        Double value = SURGE.get(player);
        return value == null ? 0D : value;
    }

    /** 护盾挡下 absorbed：全套到回涌那一阶时按比例存进去。只认别的活物打来的（自伤、环境伤害不存，不然站在火里就能攒满） */
    public static void storeSurge(Player player, DamageSource source, float absorbed) {
        if (player.level().isClientSide() || !(absorbed > 0F) || !Float.isFinite(absorbed) || !livingAttacker(player, source)
                || Attunement.fullSetStage(player, ECAttunement.MHS_ARMOR) < ArmorGrowth.surgeStage()) {
            return;
        }
        double value = Math.min(ArmorGrowth.surgeMax(), surge(player) + absorbed * Math.max(0D, ArmorGrowth.surgeRatio()));
        if (value > 0D) {
            SURGE.put(player, value);
        }
    }

    /** 亲手近战打实：存够了就排到本刻末放出去 */
    static void releaseSurge(ServerPlayer player, LivingEntity target, DamageSource source) {
        double value = surge(player);
        if (!(value > 0D) || value < ArmorGrowth.surgeThreshold() || !ArmorGrowth.ownMelee(source, player, target)
                || Attunement.fullSetStage(player, ECAttunement.MHS_ARMOR) < ArmorGrowth.surgeStage()) {
            return;
        }
        SURGE.remove(player);
        float damage = (float) value;
        schedule(player, 0, () -> {
            if (!player.isAlive() || player.isRemoved()) {
                return;
            }
            double radius = ArmorGrowth.surgeRadius();
            for (LivingEntity victim : GearAoe.targets(player, player.position(), radius, ArmorGrowth.surgeMaxTargets())) {
                GearAoe.hit(player, victim, damage);
            }
            fx(player, GearFx.SURGE, player, player.position().add(0D, 0.1D, 0D), (float) radius, ECGearSounds.SURGE);
        });
    }

    // ------------------------------------------------------------------
    // 风暴步
    // ------------------------------------------------------------------

    /** 服务端认出一次空中跳（setStage 是风之全套的阶） */
    public static boolean stormStep(Player player, int setStage) {
        if (!(player instanceof ServerPlayer server) || setStage < ArmorGrowth.stormStage()) {
            return false;
        }
        double radius = ArmorGrowth.stormRadius();
        double push = Math.max(0D, ArmorGrowth.stormKnockback());
        int slow = ArmorGrowth.stormSlowTicks();
        Vec3 feet = player.position();
        for (LivingEntity victim : GearAoe.targets(player, feet, radius, ArmorGrowth.stormMaxTargets())) {
            if (victim instanceof Player) {
                continue;
            }
            if (push > 0D) {
                victim.knockback(push, feet.x - victim.getX(), feet.z - victim.getZ());
                victim.hurtMarked = true;
            }
            if (slow > 0) {
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, slow, 0), player);
            }
        }
        int window = ArmorGrowth.stormWindow();
        if (window > 0 && ArmorGrowth.stormBonus() > 0D) {
            STORM_UNTIL.put(player, now(player) + window);
        }
        fx(server, GearFx.STORM_STEP, player, feet.add(0D, 0.05D, 0D), (float) radius, ECGearSounds.STORM_STEP);
        return true;
    }

    /** 风暴步的加成还在不在（测试看） */
    public static boolean stormPrimed(Player player) {
        Long until = STORM_UNTIL.get(player);
        return until != null && now(player) <= until;
    }

    // ------------------------------------------------------------------
    // 残像
    // ------------------------------------------------------------------

    /** 胸甲刚闪避掉 amount：有活物攻击者、全套到残像那一阶、冷却好了就留残影 */
    public static boolean afterimage(Player player, DamageSource source, float amount) {
        if (!(player instanceof ServerPlayer server) || !(amount > 0F) || !Float.isFinite(amount)
                || !(source.getEntity() instanceof LivingEntity attacker) || attacker == player
                || Attunement.fullSetStage(player, ECAttunement.VOID_ARMOR) < ArmorGrowth.afterimageStage()) {
            return false;
        }
        long now = now(player);
        Long ready = AFTERIMAGE_READY.get(player);
        if (ready != null && now < ready) {
            return false;
        }
        float damage = (float) Math.min(amount * Math.max(0D, ArmorGrowth.afterimageRatio()), ArmorGrowth.afterimageCap());
        if (!(damage > 0F)) {
            return false;
        }
        AFTERIMAGE_READY.put(player, now + ArmorGrowth.afterimageCooldown());
        Vec3 at = player.position();
        ServerLevel level = server.serverLevel();
        fx(server, GearFx.AFTERIMAGE, player, at, 0F, ECGearSounds.AFTERIMAGE);
        schedule(player, ArmorGrowth.afterimageDelay(), () -> {
            if (player.isRemoved() || player.level() != level) {
                return;
            }
            double radius = ArmorGrowth.afterimageRadius();
            int slow = ArmorGrowth.afterimageSlowTicks();
            for (LivingEntity victim : GearAoe.targets(player, at, radius, ArmorGrowth.afterimageMaxTargets())) {
                if (GearAoe.hit(player, victim, damage) && slow > 0 && victim.isAlive()) {
                    victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, slow, 1), player);
                }
            }
            if (player.level() instanceof ServerLevel here) {
                S2CGearFxPacket.sendAround(here, GearFx.AFTERIMAGE, null, at.add(0D, 0.9D, 0D), (float) radius);
            }
        });
        return true;
    }

    // ------------------------------------------------------------------
    // 宣判
    // ------------------------------------------------------------------

    /** 这一下打实以后够不够宣判（打之前问：已经挨了 hits − 1 下、冷却好了） */
    private static boolean sentenceDue(ServerPlayer player, LivingEntity target, DamageSource source) {
        if (!sentenceEligible(player, target, source)) {
            return false;
        }
        Count count = SENTENCE.get(target);
        int hits = count != null && player.getUUID().equals(count.player) ? count.hits : 0;
        long readyAt = count != null ? count.readyAt : Long.MIN_VALUE;
        return hits + 1 >= ArmorGrowth.sentenceHits() && now(player) >= readyAt;
    }

    private static boolean sentenceEligible(ServerPlayer player, LivingEntity target, DamageSource source) {
        return !(target instanceof Player) && ArmorGrowth.ownHit(source, player, target)
                && Attunement.fullSetStage(player, ECAttunement.INQUISITORIUM_ARMOR) >= ArmorGrowth.sentenceStage()
                && InquisitoriumArmorEvents.isConvicted(player, target);
    }

    /** 打实了：计一下，满了就宣判 */
    static void noteSentenceHit(ServerPlayer player, LivingEntity target, DamageSource source) {
        if (!sentenceEligible(player, target, source)) {
            return;
        }
        long now = now(player);
        Count count = SENTENCE.computeIfAbsent(target, t -> new Count());
        if (!player.getUUID().equals(count.player)) {
            count.player = player.getUUID();
            count.hits = 0;
        }
        if (now < count.readyAt) {
            return;
        }
        count.hits++;
        if (count.hits < ArmorGrowth.sentenceHits()) {
            return;
        }
        count.hits = 0;
        count.readyAt = now + ArmorGrowth.sentenceCooldown();
        Vec3 at = target.position().add(0D, target.getBbHeight() * 0.5D, 0D);
        if (GearCaps.isBoss(target)) {
            fx(player, GearFx.SENTENCE, target, at, 0F, ECGearSounds.SENTENCE);
            return;
        }
        schedule(player, 0, () -> {
            if (player.isRemoved() || !target.isAlive() || target.isRemoved() || target.level() != player.level()) {
                return;
            }
            float amount = (float) Math.min(target.getMaxHealth() * ArmorGrowth.sentencePercent() / 100D, ArmorGrowth.sentenceCap());
            if (!(amount > 0F)) {
                return;
            }
            DamageSource strike = VerdictBladeItem.verdictSource(player);
            GearCombatEvents.capNext(strike, (float) ArmorGrowth.sentenceCap());
            Vec3 motion = target.getDeltaMovement();
            boolean marked = target.hurtMarked;
            ECHurtWindow window = ECHurtWindow.of(target);
            target.invulnerableTime = 0;
            boolean hurt = target.hurt(strike, amount);
            target.setDeltaMovement(motion);
            target.hurtMarked = marked;
            window.restore(target);
            if (hurt) {
                fx(player, GearFx.SENTENCE, target, at, amount, ECGearSounds.SENTENCE);
            }
        });
    }

    /** 这个目标现在挨了几下（测试看） */
    public static int sentenceHits(LivingEntity target) {
        Count count = SENTENCE.get(target);
        return count == null ? 0 : count.hits;
    }

    /** 这一下有活物攻击者、不是自己（回涌、超载脉冲、残像同一个口径） */
    private static boolean livingAttacker(Player player, DamageSource source) {
        return source != null && source.getEntity() instanceof LivingEntity attacker && attacker != player;
    }

    // ------------------------------------------------------------------
    // 超载脉冲
    // ------------------------------------------------------------------

    /** 过载屏障刚展开（{@code ComputerAttunement.tryOverload}）：全套到超载脉冲那一阶、这一下是别的活物打来的，就排到本刻末放 */
    public static boolean overchargePulse(Player player, DamageSource source, float absorbed) {
        if (!(player instanceof ServerPlayer server) || !(absorbed > 0F) || !Float.isFinite(absorbed) || !livingAttacker(player, source)
                || Attunement.fullSetStage(player, ECAttunement.COMPUTER_ARMOR) < ArmorGrowth.pulseStage()) {
            return false;
        }
        float damage = (float) Math.min(absorbed * Math.max(0D, ArmorGrowth.pulseRatio()), ArmorGrowth.pulseCap());
        schedule(player, 0, () -> {
            if (!player.isAlive() || player.isRemoved()) {
                return;
            }
            double radius = ArmorGrowth.pulseRadius();
            int slow = ArmorGrowth.pulseSlowTicks();
            for (LivingEntity victim : GearAoe.targets(player, player.position(), radius, ArmorGrowth.pulseMaxTargets())) {
                if (damage > 0F && GearAoe.hit(player, victim, damage) && slow > 0 && victim.isAlive()) {
                    victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, slow, 2), player);
                }
            }
            fx(server, GearFx.OVERCHARGE_PULSE, player, player.position().add(0D, 0.9D, 0D), (float) radius, ECGearSounds.OVERCHARGE_PULSE);
        });
        return true;
    }

    // ------------------------------------------------------------------
    // 待办、事件包
    // ------------------------------------------------------------------

    private static void schedule(Player player, int delay, Runnable run) {
        if (TASKS.size() < MAX_TASKS) {
            TASKS.add(new Task(now(player) + Math.max(0, delay), run));
        }
    }

    /** 到点的待办做掉（每刻末自动调；GameTest 在 hurt 之后直接调）。返回做了几件 */
    public static int flush(@Nullable MinecraftServer server) {
        if (TASKS.isEmpty()) {
            return 0;
        }
        long now = server != null ? server.getTickCount() : Long.MAX_VALUE;
        List<Task> due = new ArrayList<>();
        TASKS.removeIf(task -> {
            if (task.due() <= now) {
                due.add(task);
                return true;
            }
            return false;
        });
        for (Task task : due) {
            task.run().run();
        }
        return due.size();
    }

    /** 测试：不管到没到点，全做掉 */
    public static int flushAllForTest() {
        return flush(null);
    }

    /** 测试：清掉这几个玩家 / 目标的状态（只清点名的，同一批 GameTest 并行跑） */
    public static void forgetForTest(LivingEntity... entities) {
        for (LivingEntity entity : entities) {
            if (entity instanceof Player player) {
                SURGE.remove(player);
                STORM_UNTIL.remove(player);
                AFTERIMAGE_READY.remove(player);
            }
            SENTENCE.remove(entity);
        }
    }

    private static void fx(ServerPlayer player, GearFx type, @Nullable Entity entity, Vec3 pos, float param, RegistryObject<SoundEvent> sound) {
        if (player.level() instanceof ServerLevel level) {
            S2CGearFxPacket.sendAround(level, type, entity, pos, param);
            ECGearSounds.play(level, pos, sound, SoundSource.PLAYERS, 0.8F, 1F);
        }
    }

    private static long now(Entity entity) {
        MinecraftServer server = entity.getServer();
        return server != null ? server.getTickCount() : entity.level().getGameTime();
    }

    /** Forge 总线（类名带模块前缀，见 tools/check_event_handlers.py） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
    public static final class ArmorAwakeningEvents {

        private ArmorAwakeningEvents() {
        }

        /** 护盾挡下：存回涌 */
        @SubscribeEvent
        public static void armorAwakeningAbsorbed(MRUShieldAbsorbedEvent event) {
            storeSurge(event.getPlayer(), event.getSource(), event.getAbsorbed());
        }

        /** 打实以后：放回涌、计宣判（LOWEST，不收已取消的） */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void armorAwakeningDamage(LivingDamageEvent event) {
            LivingEntity target = event.getEntity();
            if (target.level().isClientSide() || !ECLandedHit.landed(event) || !(event.getSource().getEntity() instanceof ServerPlayer player)) {
                return;
            }
            releaseSurge(player, target, event.getSource());
            noteSentenceHit(player, target, event.getSource());
        }

        @SubscribeEvent
        public static void armorAwakeningServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase == TickEvent.Phase.END && !TASKS.isEmpty()) {
                flush(event.getServer());
            }
        }

        @SubscribeEvent
        public static void armorAwakeningServerStopping(ServerStoppingEvent event) {
            TASKS.clear();
            SURGE.clear();
            STORM_UNTIL.clear();
            AFTERIMAGE_READY.clear();
            SENTENCE.clear();
        }
    }
}
