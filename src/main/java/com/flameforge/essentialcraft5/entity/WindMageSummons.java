package com.flameforge.essentialcraft5.entity;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.item.WindHornItem;
import com.flameforge.essentialcraft5.registry.ECWindMage;
import com.flameforge.essentialcraft5.util.DeathLootEvents;
import com.flameforge.essentialcraft5.util.ECLevels;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 风之号角召来的风法师（第十批 10b-M1，batch10.md §5.5.3，D3 A）：召唤者规矩照全息投影的写法——只认召唤者、打不到旁人、
 * 召唤者走远 / 下线 / 死掉就收场，掉落只给召唤者亲手打倒的。
 *
 * <h2>一批</h2>
 * 一只风之大法师 + {@code escorts} 只随机的学徒 / 法师。同一人同时只有一批（{@link #hasBatch}，正在赶来的也算）。批的记录只在内存里
 * （{@link #BATCHES}）；法师身上存召唤者 UUID（NBT {@value WindMageEntity#SUMMONED_BY_KEY}），区块卸载、重开服后读回来时重新登记进记录。
 *
 * <h2>生成点（{@link #plan}）</h2>
 * 在召唤者周围 {@code summon_min}~{@code summon_max} 格的环上随机取点、落到地表（{@link #candidates}），逐个看：区块已加载、离召唤者的高度差不超过
 * {@value #MAX_HEIGHT_DIFF} 格、能站（0.6 × 1.95 无碰撞、不在液体里、头顶露天（{@code MOTION_BLOCKING} 高度图）、脚下那格原版认为能刷怪），再以召唤者身份 {@code ECProtection.check}
 * （破坏事件探测，领地、出生点保护都算），最多探测 {@value #MAX_PROBES} 个点；凑够 1 + escorts 个点就停，不够就让随从挤在找到的点上，一个都没有就不召。
 * 法师赶来那一刻（{@link WindHornItem#SUMMON_DELAY} 刻后）再看一遍能不能站（不再探测：这几秒里领地不会变，再探测只会让日志插件多记几条）；
 * 大法师一个点都落不下、召唤者死了 / 换了维度 / 走远了，就把号角还回去、冷却清掉（{@link WindHornItem#refund}）。
 * 法师赶来途中召唤者下线（掉线、客户端崩溃、关服），在 {@code PlayerLoggedOutEvent} 里退：原版先发这个事件再存盘，这时往背包里放的东西会被存下来；
 * 扣一支、还一支，下线取消刷不出东西。法师已经到场后再下线，照旧随风散去、不退号角。
 *
 * <h2>规矩（{@link WindMageSummonEvents}）</h2>
 * <ul>
 *   <li>只盯召唤者：{@code LivingChangeTargetEvent} 里拦掉换成别的目标（找玩家的目标逻辑本来就只挑召唤者，被别人打了以后的反击也走这里）。</li>
 *   <li>它们的伤害（箭）打到召唤者以外的任何东西都在 {@code LivingAttackEvent}（HIGHEST）里取消：旁人、别人的宠物和村民一律打不到。</li>
 *   <li>随风散去（直接移除，不死、不掉东西）：召唤者下线、死掉、换维度、走出 {@code leash_range}（64）格，或者整批 {@code despawn_ticks}（6000）刻没有挨过
 *       打实的一下（{@code LivingDamageEvent}，被领地 / 插件拦下的不算）。每只法师每 20 刻自己查一次（{@link #checkMage}）；和平难度下原版直接清掉怪物。
 *       不会被原版的远距离规则清掉（{@code setPersistenceRequired}），收场只听这里。</li>
 *   <li>掉落：只有召唤者亲手打倒才照原表掉（大法师必掉受困之风）、给经验（{@link #killedBySummoner}）：致命一击的来源实体是召唤者本人（箭算射手）；
 *       或者致命一击没有来源实体（烧死、摔死、掉进岩浆），而它最后一次挨打是召唤者本人在 {@value #SELF_KILL_WINDOW} 刻内打的——火焰附加、火矢、
 *       把它打下悬崖都算召唤者自己的。别人、召唤者的宠物、铁傀儡打的最后一下不算（它们打过以后自己就成了「最后打它的」）。
 *       写法照 {@link DeathLootEvents}（死亡事件里记下、掉落表 HIGHEST / LOWEST 各删一遍、经验事件取消），
 *       怪捡走的东西照留（{@link DeathLootEvents#keepGuaranteedEquipment}）。手册那一步按「拿到受困之风」判，和掉落永远对得上。</li>
 * </ul>
 * 不拉人、不做别的权限探测：法师只盯召唤者，召唤者走远就收场，不把任何人拽回来。
 */
public final class WindMageSummons {

    /** 生成点最多探测几个（每个都是一次以召唤者身份的破坏事件） */
    public static final int MAX_PROBES = 16;
    /** 随机取点最多试几次（只看地形，不探测） */
    public static final int MAX_ATTEMPTS = 48;
    /** 生成点离召唤者脚下的高度差上限 */
    public static final int MAX_HEIGHT_DIFF = 12;
    /** 致命一击没有来源实体时，往回看多少刻内是不是召唤者打的（同原版击杀归属 lastHurtByPlayerTime 的 100 刻） */
    public static final int SELF_KILL_WINDOW = 100;
    /** 法师按 0.6 × 1.95 找空地（比碰撞箱 1.8 高一点，头顶不贴着方块） */
    private static final float SPACE_WIDTH = 0.6F;
    private static final float SPACE_HEIGHT = 1.95F;

    /** 召唤者 → 这一批（只在服务端线程用；关服清空） */
    private static final Map<UUID, Batch> BATCHES = new HashMap<>();
    /** 本 tick 死掉、不该掉落的召来的法师（死亡事件和掉落事件在同一次 die() 里，tick 结束清空） */
    private static final Set<LivingEntity> SUPPRESSED = Collections.newSetFromMap(new WeakHashMap<>());

    private WindMageSummons() {
    }

    /** 找好的生成点：第一个给大法师，其余轮着给随从 */
    public record Plan(ResourceKey<Level> dimension, List<BlockPos> points) {
    }

    /** 一批（见类注释） */
    private static final class Batch {
        final UUID summoner;
        /** 正在赶来时：到哪一刻生成；生成以后为 −1 */
        long due;
        @Nullable
        Plan plan;
        final Set<UUID> mages = new HashSet<>();
        long lastHit;
        boolean announcedGone;

        Batch(UUID summoner, long now) {
            this.summoner = summoner;
            this.lastHit = now;
            this.due = -1L;
        }
    }

    // ================================================================ 找点

    /** 在召唤者周围的环上随机取点、落到地表（{@code MOTION_BLOCKING} 高度图），只看地形；最多 {@value #MAX_ATTEMPTS} 个 */
    public static List<BlockPos> candidates(ServerPlayer summoner) {
        ServerLevel level = summoner.serverLevel();
        RandomSource random = summoner.getRandom();
        int min = WindHornItem.Config.summonMin();
        int max = WindHornItem.Config.summonMax();
        List<BlockPos> points = new ArrayList<>(MAX_ATTEMPTS);
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            double angle = random.nextDouble() * Math.PI * 2D;
            double distance = min + random.nextDouble() * (max - min);
            int x = Mth.floor(summoner.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(summoner.getZ() + Math.sin(angle) * distance);
            if (!level.hasChunkAt(new BlockPos(x, summoner.getBlockY(), z))) {
                continue;
            }
            points.add(new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z));
        }
        return points;
    }

    /**
     * 从候选里挑生成点（见类注释）：能站的才探测，最多探测 {@value #MAX_PROBES} 个，凑够 1 + escorts 个就停。一个都没有返回 null。
     */
    @Nullable
    public static Plan plan(ServerPlayer summoner, List<BlockPos> candidates) {
        ServerLevel level = summoner.serverLevel();
        int want = 1 + WindHornItem.Config.escorts();
        List<BlockPos> chosen = new ArrayList<>(want);
        int probes = 0;
        for (BlockPos pos : candidates) {
            if (chosen.size() >= want || probes >= MAX_PROBES) {
                break;
            }
            if (Math.abs(pos.getY() - summoner.getBlockY()) > MAX_HEIGHT_DIFF || !standable(level, pos) || chosen.contains(pos)) {
                continue;
            }
            probes++;
            if (ECProtection.check(summoner, level, pos, summoner).allowed()) {
                chosen.add(pos.immutable());
            }
        }
        return chosen.isEmpty() ? null : new Plan(level.dimension(), List.copyOf(chosen));
    }

    /** 这一格能站一只法师：区块已加载、0.6 × 1.95 无碰撞、不在液体里、头顶露天、脚下那格原版认为能刷怪 */
    public static boolean standable(ServerLevel level, BlockPos pos) {
        // 头顶露天按高度图判（刚放下的方块立刻算数；按天空光照判要等光照引擎）
        if (!ECLevels.isFullyLoaded(level, pos) || !level.getFluidState(pos).isEmpty()
                || pos.getY() < level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ())) {
            return false;
        }
        BlockPos below = pos.below();
        BlockState ground = level.getBlockState(below);
        if (!ground.isValidSpawn(level, below, ECWindMage.WIND_MAGE.get())) {
            return false;
        }
        double half = SPACE_WIDTH / 2D;
        AABB space = new AABB(pos.getX() + 0.5D - half, pos.getY(), pos.getZ() + 0.5D - half,
                pos.getX() + 0.5D + half, pos.getY() + SPACE_HEIGHT, pos.getZ() + 0.5D + half);
        return level.noCollision(space);
    }

    // ================================================================ 召来

    /** 同一人有没有一批在场上（或正在赶来） */
    public static boolean hasBatch(UUID summoner) {
        return BATCHES.containsKey(summoner);
    }

    /** 排进待召唤表：到 due 那一刻由 {@link #tickPending} 生成 */
    public static void schedule(ServerPlayer summoner, Plan plan, long due) {
        Batch batch = new Batch(summoner.getUUID(), summoner.level().getGameTime());
        batch.plan = plan;
        batch.due = due;
        BATCHES.put(summoner.getUUID(), batch);
    }

    /**
     * 现在就按 plan 生成一批（{@link #tickPending} 到点时调；GameTest 直接调）。大法师一个点都落不下时什么都不生成、返回空表
     * （调用方退号角）。成功时登记这一批、让它们盯上召唤者，返回生成的法师（第一只是大法师）。
     */
    public static List<WindMageEntity> spawn(ServerPlayer summoner, Plan plan) {
        ServerLevel level = summoner.serverLevel();
        List<BlockPos> free = new ArrayList<>(plan.points().size());
        for (BlockPos pos : plan.points()) {
            if (standable(level, pos)) {
                free.add(pos);
            }
        }
        if (free.isEmpty() || !level.dimension().equals(plan.dimension())) {
            return List.of();
        }
        List<WindMageEntity> mages = new ArrayList<>();
        int count = 1 + WindHornItem.Config.escorts();
        RandomSource random = summoner.getRandom();
        for (int i = 0; i < count; i++) {
            BlockPos pos = free.get(i % free.size());
            int type = i == 0 ? WindMageEntity.ARCHMAGE : random.nextInt(WindMageEntity.ARCHMAGE);
            WindMageEntity mage = create(level, pos, type, summoner);
            if (mage != null) {
                mages.add(mage);
            } else if (i == 0) {
                return List.of();
            }
        }
        Batch batch = BATCHES.computeIfAbsent(summoner.getUUID(), id -> new Batch(id, level.getGameTime()));
        batch.due = -1L;
        batch.plan = null;
        batch.lastHit = level.getGameTime();
        for (WindMageEntity mage : mages) {
            batch.mages.add(mage.getUUID());
        }
        level.playSound(null, summoner.getX(), summoner.getY(), summoner.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.HOSTILE, 1.5F, 0.6F);
        summoner.sendSystemMessage(Component.translatable("message.essentialcraft5.wind_horn.arrived")
                .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
        return mages;
    }

    @Nullable
    private static WindMageEntity create(ServerLevel level, BlockPos pos, int type, ServerPlayer summoner) {
        WindMageEntity mage = ECWindMage.WIND_MAGE.get().create(level);
        if (mage == null) {
            return null;
        }
        mage.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, level.random.nextFloat() * 360F, 0F);
        ForgeEventFactory.onFinalizeSpawn(mage, level, level.getCurrentDifficultyAt(pos), MobSpawnType.MOB_SUMMONED, null, null);
        if (mage.isSpawnCancelled()) {
            return null;
        }
        mage.setMageType(type);
        mage.setSummonedBy(summoner.getUUID());
        mage.setPersistenceRequired();
        if (!level.addFreshEntity(mage)) {
            return null;
        }
        mage.setTarget(summoner);
        level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5D, pos.getY() + 1D, pos.getZ() + 0.5D, 24, 0.5D, 0.8D, 0.5D, 0.05D);
        return mage;
    }

    /** 每个服务端 tick：到点的那几批生成，召唤者已经不在的退号角（见类注释）。GameTest 也直接调（排一批 due = 现在，再调一次） */
    public static void tickPending(MinecraftServer server) {
        if (BATCHES.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        List<Batch> due = null;
        for (Batch batch : BATCHES.values()) {
            if (batch.due >= 0L && now >= batch.due) {
                if (due == null) {
                    due = new ArrayList<>(2);
                }
                due.add(batch);
            }
        }
        if (due == null) {
            return;
        }
        for (Batch batch : due) {
            BATCHES.remove(batch.summoner);
            ServerPlayer summoner = ECProtection.onlinePlayer(server, batch.summoner);
            Plan plan = batch.plan;
            if (summoner == null) {
                continue;
            }
            // 这几秒里召唤者死了又重生、被传走了：生成出来也会立刻随风散去，不如把号角还他
            int leash = WindHornItem.Config.leashRange();
            if (plan == null || !summoner.isAlive() || !summoner.level().dimension().equals(plan.dimension())
                    || summoner.distanceToSqr(plan.points().get(0).getCenter()) > (double) leash * leash
                    || spawn(summoner, plan).isEmpty()) {
                WindHornItem.refund(summoner);
            }
        }
    }

    // ================================================================ 收场

    /** 法师读档 / 进世界时登记回它那一批（重开服、区块重新加载后记录要能接上） */
    public static void track(WindMageEntity mage) {
        UUID summoner = mage.getSummonedBy();
        if (summoner == null || mage.level().isClientSide) {
            return;
        }
        Batch batch = BATCHES.computeIfAbsent(summoner, id -> new Batch(id, mage.level().getGameTime()));
        batch.mages.add(mage.getUUID());
    }

    /** 法师离开世界（死了、散了、区块卸载、换维度）：从那一批里划掉，划空了这一批就结束 */
    public static void forget(WindMageEntity mage) {
        UUID summoner = mage.getSummonedBy();
        if (summoner == null) {
            return;
        }
        Batch batch = BATCHES.get(summoner);
        if (batch != null && batch.due < 0L) {
            batch.mages.remove(mage.getUUID());
            if (batch.mages.isEmpty()) {
                BATCHES.remove(summoner);
            }
        }
    }

    /**
     * 查一只召来的法师该不该随风散去（见类注释），该散就散。法师每 20 刻自己调一次；GameTest 直接调。返回散了没有。
     */
    public static boolean checkMage(WindMageEntity mage) {
        UUID id = mage.getSummonedBy();
        if (id == null || mage.isRemoved() || !(mage.level() instanceof ServerLevel level)) {
            return false;
        }
        ServerPlayer summoner = ECProtection.onlinePlayer(level.getServer(), id);
        Batch batch = BATCHES.get(id);
        if (batch == null) {
            track(mage);
            batch = BATCHES.get(id);
        }
        int leash = WindHornItem.Config.leashRange();
        boolean gone = summoner == null || !summoner.isAlive() || summoner.level() != level
                || summoner.distanceToSqr(mage) > (double) leash * leash
                || batch != null && level.getGameTime() - batch.lastHit > WindHornItem.Config.despawnTicks();
        if (!gone) {
            return false;
        }
        vanish(mage, level);
        if (summoner != null && batch != null && !batch.announcedGone) {
            batch.announcedGone = true;
            summoner.displayClientMessage(Component.translatable("message.essentialcraft5.wind_horn.gone").withStyle(ChatFormatting.DARK_AQUA), true);
        }
        return true;
    }

    /** 随风散去：一团云、一声风，直接移除（不死、不掉东西） */
    private static void vanish(WindMageEntity mage, ServerLevel level) {
        level.sendParticles(ParticleTypes.CLOUD, mage.getX(), mage.getY() + 1D, mage.getZ(), 20, 0.4D, 0.8D, 0.4D, 0.08D);
        level.playSound(null, mage.getX(), mage.getY(), mage.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.HOSTILE, 1F, 1.4F);
        mage.discard();
    }

    // ================================================================ 判定

    /** 这只是不是召来的法师 */
    @Nullable
    private static UUID summonerOf(@Nullable Entity entity) {
        return entity instanceof WindMageEntity mage ? mage.getSummonedBy() : null;
    }

    /**
     * 是不是召唤者亲手打倒的（不是召来的法师一律算是）：致命一击有来源实体时看它是不是召唤者（箭算射手；宠物、铁傀儡、别的玩家不算）；
     * 没有来源实体（烧死、摔死、岩浆）时看最后一次挨打是不是召唤者在 {@value #SELF_KILL_WINDOW} 刻内打的。
     * 死亡事件、掉落事件都在 die() 里，这时 lastHurtByMob 还没被清掉。
     */
    public static boolean killedBySummoner(LivingEntity victim, DamageSource source) {
        UUID summoner = summonerOf(victim);
        if (summoner == null) {
            return true;
        }
        Entity killer = source.getEntity();
        if (killer != null) {
            return summoner.equals(killer.getUUID());
        }
        LivingEntity last = victim.getLastHurtByMob();
        return last != null && summoner.equals(last.getUUID())
                && victim.tickCount - victim.getLastHurtByMobTimestamp() <= SELF_KILL_WINDOW;
    }

    // ================================================================ 事件

    /** Forge 总线（类名带模块前缀，见 tools/check_event_handlers.py） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
    public static final class WindMageSummonEvents {

        private WindMageSummonEvents() {
        }

        @SubscribeEvent
        public static void windSummonTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            tickPending(event.getServer());
            if (!SUPPRESSED.isEmpty()) {
                SUPPRESSED.clear();
            }
        }

        /** 只盯召唤者：换成别的目标一律拦下（换成「没有目标」放行） */
        @SubscribeEvent
        public static void windSummonTarget(LivingChangeTargetEvent event) {
            UUID summoner = summonerOf(event.getEntity());
            LivingEntity target = event.getNewTarget();
            if (summoner != null && target != null && !summoner.equals(target.getUUID())) {
                event.setCanceled(true);
            }
        }

        /** 召来的法师打召唤者以外的任何东西都取消（HIGHEST：在别的模组、混合端插件之前） */
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void windSummonAttack(LivingAttackEvent event) {
            UUID summoner = summonerOf(event.getSource().getEntity());
            if (summoner != null && !summoner.equals(event.getEntity().getUUID())) {
                event.setCanceled(true);
            }
        }

        /** 挨了打实的一下（被拦下的不算）：这一批的「没人打」计时重来 */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void windSummonHit(LivingDamageEvent event) {
            UUID summoner = summonerOf(event.getEntity());
            if (summoner != null && event.getAmount() > 0F) {
                Batch batch = BATCHES.get(summoner);
                if (batch != null) {
                    batch.lastHit = event.getEntity().level().getGameTime();
                }
            }
        }

        /** 致命一击不是召唤者亲手打的：记下，这次死亡不掉落、不给经验（同 DeathLootEvents 的写法） */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void windSummonDeath(LivingDeathEvent event) {
            LivingEntity victim = event.getEntity();
            if (!victim.level().isClientSide && !killedBySummoner(victim, event.getSource())) {
                SUPPRESSED.add(victim);
            }
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void windSummonExperience(LivingExperienceDropEvent event) {
            if (SUPPRESSED.contains(event.getEntity())) {
                event.setCanceled(true);
            }
        }

        /** 掉落表删两遍（HIGHEST 先删，LOWEST 再清别的模组中途加的），见 {@link DeathLootEvents#onLivingDrops} */
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void windSummonDrops(LivingDropsEvent event) {
            strip(event);
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void windSummonDropsLate(LivingDropsEvent event) {
            strip(event);
        }

        private static void strip(LivingDropsEvent event) {
            LivingEntity victim = event.getEntity();
            if (victim.level().isClientSide) {
                return;
            }
            if (SUPPRESSED.contains(victim) || !killedBySummoner(victim, event.getSource())) {
                DeathLootEvents.keepGuaranteedEquipment(victim, event.getDrops());
            }
        }

        /** 法师还在赶来时召唤者下线：这一批取消、号角还回去（见类注释「生成点」末段）；已经到场的照旧由 checkMage 散掉 */
        @SubscribeEvent
        public static void windSummonLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) {
                return;
            }
            Batch batch = BATCHES.get(player.getUUID());
            if (batch != null && batch.due >= 0L) {
                BATCHES.remove(player.getUUID());
                WindHornItem.refund(player);
            }
        }

        @SubscribeEvent
        public static void windSummonServerStopped(ServerStoppedEvent event) {
            BATCHES.clear();
            SUPPRESSED.clear();
        }
    }

    /** 给 GameTest：这一批现在记着几只法师（没有这一批返回 −1） */
    public static int batchSize(UUID summoner) {
        Batch batch = BATCHES.get(summoner);
        return batch == null ? -1 : batch.mages.size();
    }

    /**
     * 给 GameTest / 混合端自检：这名召唤者正在赶来的那一批现在就生成（不等 {@link WindHornItem#SUMMON_DELAY} 刻）。
     * 没有正在赶来的、或者大法师落不下时返回空表（不退号角，测试自己断言）。
     */
    public static List<WindMageEntity> spawnPending(ServerPlayer summoner) {
        Batch batch = BATCHES.get(summoner.getUUID());
        if (batch == null || batch.due < 0L || batch.plan == null) {
            return List.of();
        }
        Plan plan = batch.plan;
        BATCHES.remove(summoner.getUUID());
        return spawn(summoner, plan);
    }

    /** 给 GameTest / 混合端自检：清掉这名召唤者的记录（测试结束时收尾） */
    public static void clear(UUID summoner) {
        BATCHES.remove(summoner);
    }
}
