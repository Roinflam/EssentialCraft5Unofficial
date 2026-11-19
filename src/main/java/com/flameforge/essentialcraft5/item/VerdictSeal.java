package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.item.attunement.Attunement;
import com.flameforge.essentialcraft5.item.attunement.GearCaps;
import com.flameforge.essentialcraft5.item.attunement.GearCombatEvents;
import com.flameforge.essentialcraft5.item.attunement.GearGrowthConfig;
import com.flameforge.essentialcraft5.item.attunement.GearPool;
import com.flameforge.essentialcraft5.network.GearFx;
import com.flameforge.essentialcraft5.network.S2CGearFxPacket;
import com.flameforge.essentialcraft5.registry.ECGearSounds;
import com.flameforge.essentialcraft5.util.ECHurtWindow;
import com.flameforge.essentialcraft5.util.ECLandedHit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 终审：裁决之刃的满阶觉醒（tools/plan/gear_growth.md §4.1，{@code general.gear_growth.verdict_blade.final_verdict}）。
 * <ul>
 *   <li>同调 {@code final_verdict.stage}（5）阶起，断罪打中的每个目标挂 {@code mark_ticks}（100）刻罪印（只在内存里，不存盘；断罪本来就不打玩家，玩家身上不会有）；</li>
 *   <li>同一名玩家下一次亲手用主手裁决之刃打实带罪印的目标（{@code LivingDamageEvent} LOWEST，{@code ECLandedHit.landed}）引爆罪印：
 *       普通目标本刻末补 {@code percent}（6%）最大生命、封顶 {@code cap}（120）、伤害类型 {@code verdict}（写法同裁断：记无敌窗口、打、放回，封顶登记给总闸）；
 *       首领不补，改成这一下在增伤池里 +{@code boss_pool}（40%，池来源 {@code verdict_seal}，照样被首领池上限夹）；</li>
 *   <li>断罪自己那一下不引爆（同一刻刚挂上的不算），裁断、范围技也不引爆。</li>
 * </ul>
 * 挂印、引爆各发一个 {@link GearFx} 事件（{@code VERDICT_SEAL} / {@code VERDICT_BURST}）。
 */
public final class VerdictSeal {

    /** 一刻最多记这么多条待引爆的 */
    public static final int MAX_PENDING = 256;

    private record Seal(UUID player, long applied, long until) {
    }

    private record Pending(ServerPlayer player, LivingEntity target) {
    }

    private static final Map<LivingEntity, Seal> SEALS = new WeakHashMap<>();
    private static final List<Pending> PENDING = new ArrayList<>();

    private VerdictSeal() {
    }

    /** 终审解锁了没有 */
    public static boolean unlocked(ItemStack blade) {
        return blade.getItem() instanceof VerdictBladeItem && Attunement.stage(blade) >= GearGrowthConfig.finalVerdictStage();
    }

    /** 断罪打中 target 以后调：挂罪印（玩家不挂） */
    public static void mark(ServerPlayer player, LivingEntity target) {
        if (target instanceof Player || !target.isAlive()) {
            return;
        }
        long now = now(player);
        int ticks = Math.max(1, GearGrowthConfig.finalVerdictMarkTicks());
        SEALS.put(target, new Seal(player.getUUID(), now, now + ticks));
        if (target.level() instanceof ServerLevel level) {
            S2CGearFxPacket.sendAround(level, GearFx.VERDICT_SEAL, target, target.position().add(0D, target.getBbHeight() + 0.3D, 0D), ticks);
            ECGearSounds.play(level, target.position(), ECGearSounds.VERDICT_SEAL, SoundSource.PLAYERS, 0.7F, 1F);
        }
    }

    /** target 身上 player 挂的、还没过期的罪印；没有返回 null（过期的顺手清掉） */
    @Nullable
    private static Seal active(LivingEntity target, Player player) {
        Seal seal = SEALS.get(target);
        if (seal == null) {
            return null;
        }
        if (now(player) > seal.until) {
            SEALS.remove(target);
            return null;
        }
        return seal.player.equals(player.getUUID()) ? seal : null;
    }

    /** 这个目标身上有没有这名玩家挂的罪印（测试看） */
    public static boolean marked(LivingEntity target, Player player) {
        return active(target, player) != null;
    }

    /** 这一下能不能引爆：目标带他挂的罪印、不是同一刻刚挂上的、是他亲手主手用裁决之刃打的 */
    @Nullable
    private static Seal detonatable(DamageSource source, LivingEntity target) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return null;
        }
        ItemStack weapon = GearPool.ownWeapon(source, target);
        if (weapon == null || !(weapon.getItem() instanceof VerdictBladeItem)) {
            return null;
        }
        Seal seal = active(target, player);
        return seal != null && now(player) != seal.applied ? seal : null;
    }

    /** 首领身上带罪印时，引爆的那一下在增伤池里加 {@code boss_pool}（{@code ECAttunement.init} 登记） */
    public static void registerGearSources() {
        GearPool.registerOffense("verdict_seal", context -> context.targetClass() == GearCaps.TargetClass.BOSS
                && detonatable(context.source(), context.target()) != null ? GearGrowthConfig.finalVerdictBossPool() : 0D);
    }

    /** 普通目标引爆补的那一下：min(最大生命 × percent%, cap) */
    public static float burstAmount(LivingEntity target) {
        double percent = GearGrowthConfig.finalVerdictPercent();
        double cap = GearGrowthConfig.finalVerdictCap();
        if (!(percent > 0D) || !(cap > 0D)) {
            return 0F;
        }
        return (float) Math.min(target.getMaxHealth() * percent / 100D, cap);
    }

    /** 打实了：引爆（首领只发事件，普通目标记下等本刻末补） */
    static void noteLanded(LivingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (SEALS.isEmpty() || !ECLandedHit.landed(event)) {
            return;
        }
        DamageSource source = event.getSource();
        Seal seal = detonatable(source, target);
        if (seal == null) {
            return;
        }
        SEALS.remove(target);
        ServerPlayer player = (ServerPlayer) source.getEntity();
        if (GearCaps.isBoss(target)) {
            fx(target, 0F);
        } else if (PENDING.size() < MAX_PENDING) {
            PENDING.add(new Pending(player, target));
        }
    }

    /** 本刻末补引爆的那一下。自动调；GameTest 在 hurt 之后直接调。返回补成了几下 */
    public static int flush() {
        if (PENDING.isEmpty()) {
            return 0;
        }
        List<Pending> batch = new ArrayList<>(PENDING);
        PENDING.clear();
        int dealt = 0;
        for (Pending pending : batch) {
            ServerPlayer player = pending.player();
            LivingEntity target = pending.target();
            if (player.isRemoved() || !target.isAlive() || target.isRemoved() || target.level() != player.level()) {
                continue;
            }
            float amount = burstAmount(target);
            if (!(amount > 0F)) {
                continue;
            }
            DamageSource source = VerdictBladeItem.verdictSource(player);
            GearCombatEvents.capNext(source, (float) GearGrowthConfig.finalVerdictCap());
            Vec3 motion = target.getDeltaMovement();
            boolean marked = target.hurtMarked;
            ECHurtWindow window = ECHurtWindow.of(target);
            target.invulnerableTime = 0;
            boolean hurt = target.hurt(source, amount);
            target.setDeltaMovement(motion);
            target.hurtMarked = marked;
            window.restore(target);
            if (hurt) {
                dealt++;
                fx(target, amount);
            }
        }
        return dealt;
    }

    private static void fx(LivingEntity target, float amount) {
        if (target.level() instanceof ServerLevel level) {
            S2CGearFxPacket.sendAround(level, GearFx.VERDICT_BURST, target, target.position().add(0D, target.getBbHeight() * 0.5D, 0D), amount);
            ECGearSounds.play(level, target.position(), ECGearSounds.VERDICT_BURST, SoundSource.PLAYERS, 0.9F, 1F);
        }
    }

    /**
     * 测试：清掉这几个目标身上的罪印和待引爆的。只清点名的：同一批 GameTest 是并行跑的，清全表会把别的测试刚挂上的罪印一起清掉
     */
    public static void forgetForTest(LivingEntity... targets) {
        for (LivingEntity target : targets) {
            if (target != null) {
                SEALS.remove(target);
                PENDING.removeIf(pending -> pending.target() == target);
            }
        }
    }

    private static long now(Entity entity) {
        MinecraftServer server = entity.getServer();
        return server != null ? server.getTickCount() : entity.level().getGameTime();
    }

    /** Forge 总线（类名带模块前缀，见 tools/check_event_handlers.py） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
    public static final class VerdictSealEvents {

        private VerdictSealEvents() {
        }

        /** 打实了才引爆（LOWEST，不收已取消的） */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void verdictSealDamage(LivingDamageEvent event) {
            if (!event.getEntity().level().isClientSide()) {
                noteLanded(event);
            }
        }

        @SubscribeEvent
        public static void verdictSealServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase == TickEvent.Phase.END && !PENDING.isEmpty()) {
                flush();
            }
        }

        @SubscribeEvent
        public static void verdictSealServerStopping(ServerStoppingEvent event) {
            SEALS.clear();
            PENDING.clear();
        }
    }
}
