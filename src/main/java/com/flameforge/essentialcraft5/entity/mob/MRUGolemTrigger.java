package com.flameforge.essentialcraft5.entity.mob;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.corruption.PresenceClaimMemory;
import com.flameforge.essentialcraft5.entity.MRUPresenceEntity;
import com.flameforge.essentialcraft5.entity.MRUPresenceIndex;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECMRUGolem;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.flameforge.essentialcraft5.util.SpawnOrigin;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 上古遗迹的聚能魔像什么时候、在哪里出来（第十三批 13c-M2，batch13.md §5.3.1）。
 *
 * <h2>谁会被写上守护</h2>
 * {@code ElderMRUCUFeature} 放聚集体时调 {@link #ruinGuardian}：主世界（以及海纳尔以外用到这个特征的维度）的新遗迹一律「等着触发」，
 * 海纳尔的按 {@code hoanna_chance}（20%）——用世界种子和原位算的，同一个种子同一座遗迹结果不变，也不动特征自己的随机数（遗迹长相不变）。
 * 老存档里的遗迹没有记录，不补。
 *
 * <h2>触发（每名玩家每 {@value #CHECK_INTERVAL} 刻查一次，错开）</h2>
 * 真玩家、生存 / 冒险、活着、不是和平难度、{@code trigger_radius} 不是 0；用 {@link MRUPresenceIndex} 找它 {@code trigger_radius} 格内的聚集体（只查已加载的），
 * 对每团「等着触发」的：
 * <ol>
 *   <li>先看有没有被挪离原位超过 {@code abandon_distance}（{@link #checkDrift}；聚集体自己每 20 刻也看一次），挪远了 = 放弃，不出来；</li>
 *   <li>这名玩家能不能动聚集体那一格（{@link PresenceClaimMemory#check}，按人和这团记 5 分钟）：不能（在别人领地里）或者问不出来，这次不触发；</li>
 *   <li>同一团两次尝试至少隔 {@code retry_ticks}；</li>
 *   <li>{@link #spawn}：先在聚集体正上方找头顶露天、脚下实心、站得下的一格（最多往上 {@code spawn_search_height} 格），没有就在聚集体旁边
 *       （水平 {@value #BESIDE_RADIUS} 格、上下 {@value #BESIDE_HEIGHT} 格，由近到远）找站得下的地方；<b>落点也要触发的人能动</b>（{@link LandingPermit}：
 *       聚集体那一格能动不等于正上方几十格的地面也能动），动不了的格子跳过、接着找；来源记 {@code STRUCTURE}，走一遍
 *       {@code FinalizeSpawn} 事件，<b>真的进了世界</b>（{@code addFreshEntity} 返回 true）才把聚集体记成「已触发」。混合端插件取消生成、别的模组取消
 *       {@code FinalizeSpawn} 的整次生成、站不下，都保持「等着触发」，过 {@code retry_ticks} 再试。</li>
 * </ol>
 * 触发的玩家进魔像的名单（看得到血条），动作栏说一句（魔像出在聚集体正上方那一列的露天地面上、又比他高出 {@value #ABOVE_HINT} 格以上时带上高出几格）。
 * 找不到能站、又归他动的地方时也说一句（{@link #MSG_NO_ROOM}，同一团每 {@value #NO_ROOM_NOTICE_TICKS} 刻最多一次），
 * 不然埋在实心岩层里、海底的遗迹每 40 刻静默重试，走近的人会以为这座遗迹没有魔像。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class MRUGolemTrigger {

    /** 每名玩家多久查一次（刻） */
    public static final int CHECK_INTERVAL = 40;
    /** 找不到露天地面时，在聚集体旁边水平找多远 */
    public static final int BESIDE_RADIUS = 4;
    /** 找不到露天地面时，在聚集体旁边上下找多远 */
    public static final int BESIDE_HEIGHT = 4;
    /** 重试表最多记几条（超过就删过期的） */
    private static final int RETRY_LIMIT = 4096;

    public static final String MSG_AWAKENED = "message.essentialcraft5.mru_golem.awakened";
    /** 魔像出在触发的人头顶上方的地面上（埋着的遗迹多半这样），提示里带上高出几格 */
    public static final String MSG_AWAKENED_ABOVE = "message.essentialcraft5.mru_golem.awakened_above";
    /** 魔像比触发的人高出这么多格以上，就用「在你头顶」那句 */
    public static final int ABOVE_HINT = 4;
    /** 魔像找不到能站、又归触发的人动的地方 */
    public static final String MSG_NO_ROOM = "message.essentialcraft5.mru_golem.no_room";
    /** 「找不到地方站」同一团多久最多说一次（刻） */
    public static final int NO_ROOM_NOTICE_TICKS = 200;
    /** 一次尝试最多新问几格落点的权限（记着的结论不算）；问到上限还没找到，这次就不出，过 {@code retry_ticks} 再接着问 */
    public static final int MAX_LANDING_PROBES = 8;
    /** 落点权限的结论记多久（刻），同 {@link PresenceClaimMemory#REMEMBER_TICKS} */
    public static final int LANDING_REMEMBER_TICKS = PresenceClaimMemory.REMEMBER_TICKS;
    /** 落点结论最多记几条（超过先删过期的，还多就整个清掉） */
    private static final int LANDING_LIMIT = 16384;

    /** 聚集体 UUID → 下一次可以尝试的游戏时间（只在内存里，重启清空：重启后第一次走近立刻再试） */
    private static final Map<UUID, Long> RETRY = new HashMap<>();
    /** 聚集体 UUID → 下一次可以说「找不到地方站」的游戏时间 */
    private static final Map<UUID, Long> NO_ROOM_NOTICE = new HashMap<>();
    /** 落点权限的结论（{@link LandingPermit}）：只在内存里，下线删他的、关服清空 */
    private static final Map<LandingKey, Landing> LANDING = new HashMap<>();
    @Nullable
    private static Boolean testPeaceful;

    private MRUGolemTrigger() {
    }

    // ------------------------------------------------------------------ 遗迹生成

    /** 遗迹生成时这团聚集体的守护记录（见类注释）。只读种子和维度，不动特征的随机数 */
    public static MRUGolemGuardian ruinGuardian(WorldGenLevel level, BlockPos origin) {
        if (!ECHoanna.isHoanna(level.getLevel().dimension())) {
            return MRUGolemGuardian.pending(origin);
        }
        return hoannaGuarded(level.getSeed(), origin, ECMRUGolem.Config.values().trigger().hoannaChance())
                ? MRUGolemGuardian.pending(origin) : MRUGolemGuardian.NONE;
    }

    /** 海纳尔的遗迹有没有守护：种子和原位混出一个 [0, 1) 的数，小于 chance 就有 */
    public static boolean hoannaGuarded(long seed, BlockPos origin, double chance) {
        if (chance <= 0D) {
            return false;
        }
        if (chance >= 1D) {
            return true;
        }
        long h = seed ^ (origin.asLong() * 0x9E3779B97F4A7C15L) ^ 0x4D52_5547_4F4C_454DL;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * 0x1.0p-53 < chance;
    }

    // ------------------------------------------------------------------ 触发

    @SubscribeEvent
    public static void mruGolemPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        if ((player.tickCount + player.getId()) % CHECK_INTERVAL != 0) {
            return;
        }
        check(player);
    }

    @SubscribeEvent
    public static void mruGolemServerStopped(ServerStoppedEvent event) {
        RETRY.clear();
        NO_ROOM_NOTICE.clear();
        LANDING.clear();
        testPeaceful = null;
    }

    @SubscribeEvent
    public static void mruGolemLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!event.getEntity().level().isClientSide) {
            UUID player = event.getEntity().getUUID();
            LANDING.keySet().removeIf(key -> key.player().equals(player));
        }
    }

    /** 查一次这名玩家身边（见类注释）。返回这次放出来的魔像（多半是空的）。GameTest 直接调 */
    public static List<MRUGolemEntity> check(ServerPlayer player) {
        List<MRUGolemEntity> spawned = new ArrayList<>();
        ECMRUGolem.Config.Trigger config = ECMRUGolem.Config.values().trigger();
        if (config.radius() <= 0 || !ECProtection.isRealPlayer(player) || !player.isAlive() || player.isSpectator() || player.isCreative()) {
            return spawned;
        }
        ServerLevel level = player.serverLevel();
        if (peaceful(level)) {
            return spawned;
        }
        for (MRUPresenceEntity presence : MRUPresenceIndex.within(level, player.position(), config.radius(), 0, false)) {
            if (!presence.getGuardian().pending() || checkDrift(presence)) {
                continue;
            }
            long now = level.getGameTime();
            Long next = RETRY.get(presence.getUUID());
            if (next != null && now < next) {
                continue;
            }
            if (PresenceClaimMemory.check(player, presence) != ECProtection.Verdict.ALLOW) {
                continue;
            }
            remember(RETRY, presence.getUUID(), now + config.retryTicks(), now);
            MRUGolemEntity golem = spawn(level, presence, player);
            if (golem != null) {
                RETRY.remove(presence.getUUID());
                NO_ROOM_NOTICE.remove(presence.getUUID());
                spawned.add(golem);
            }
        }
        return spawned;
    }

    /**
     * 等着触发的聚集体被挪离原位超过 {@code abandon_distance} 格 → 记成放弃。返回这次是不是改成了放弃。
     * 聚集体每 20 刻调一次（{@code MRUPresenceEntity.tick}），触发前也调。
     */
    public static boolean checkDrift(MRUPresenceEntity presence) {
        MRUGolemGuardian guardian = presence.getGuardian();
        BlockPos origin = guardian.origin();
        if (!guardian.pending() || origin == null) {
            return false;
        }
        int limit = ECMRUGolem.Config.values().trigger().abandonDistance();
        if (presence.blockPosition().distSqr(origin) > (double) limit * limit) {
            presence.setGuardian(guardian.with(MRUGolemGuardian.State.ABANDONED));
            RETRY.remove(presence.getUUID());
            return true;
        }
        return false;
    }

    /**
     * 在 presence 旁边放出魔像（见类注释第 4 步）。成功返回魔像，聚集体记成「已触发」；站不下、被取消返回 null，聚集体不动。
     * 不查触发条件（GameTest 和 {@link #check} 调）。trigger 不为空时落点要他能动（{@link LandingPermit}），为空时（GameTest 直接放）不看落点权限。
     */
    @Nullable
    public static MRUGolemEntity spawn(ServerLevel level, MRUPresenceEntity presence, @Nullable ServerPlayer trigger) {
        EntityType<MRUGolemEntity> type = ECMRUGolem.MRU_GOLEM.get();
        LandingPermit permit = trigger == null ? null : new LandingPermit(level, trigger, presence);
        Spot spot = findSpot(level, presence.blockPosition(), type, permit == null ? pos -> true : permit);
        if (spot == null) {
            if (permit != null && !permit.unsettled()) {
                noRoom(level, presence, trigger);
            }
            return null;
        }
        Vec3 at = spot.at();
        MRUGolemEntity golem = type.create(level);
        if (golem == null) {
            return null;
        }
        float yaw = level.random.nextFloat() * 360F;
        if (trigger != null) {
            yaw = (float) (Mth.atan2(trigger.getZ() - at.z, trigger.getX() - at.x) * Mth.RAD_TO_DEG) - 90F;
        }
        golem.moveTo(at.x, at.y, at.z, yaw, 0F);
        golem.setYHeadRot(yaw);
        golem.guard(presence);
        SpawnOrigin.set(golem, SpawnOrigin.Origin.STRUCTURE);
        golem.applyConfiguredStats();
        ForgeEventFactory.onFinalizeSpawn(golem, level, level.getCurrentDifficultyAt(golem.blockPosition()), MobSpawnType.STRUCTURE, null, null);
        if (golem.isSpawnCancelled()) {
            return null;
        }
        golem.setPersistenceRequired();
        if (!level.addFreshEntity(golem)) {
            return null;
        }
        presence.setGuardian(presence.getGuardian().with(MRUGolemGuardian.State.SPAWNED));
        golem.refreshGuard();
        level.playSound(null, at.x, at.y + 1D, at.z, ECMRUGolem.SOUND_AWAKEN.get(), SoundSource.HOSTILE, 2F, 1F);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 1.3D, at.z, 40, 0.7D, 1D, 0.7D, 0.15D);
        if (trigger != null) {
            golem.bossRoster().add(trigger);
            trigger.displayClientMessage(awakenedMessage(trigger.blockPosition().getY(), golem.blockPosition().getY(), spot.overhead()), true);
        }
        return golem;
    }

    /**
     * 触发的人看到的那句：魔像出在聚集体正上方那一列的露天地面上、又比他高出 {@value #ABOVE_HINT} 格以上时，说「在你头顶上方几格的地面上」，
     * 好让他知道要回地面去打；在聚集体旁边找的站位（overhead 为假）不在地面上，只说醒了。
     */
    public static Component awakenedMessage(int triggerY, int golemY, boolean overhead) {
        int up = golemY - triggerY;
        Component line = overhead && up >= ABOVE_HINT ? Component.translatable(MSG_AWAKENED_ABOVE, up) : Component.translatable(MSG_AWAKENED);
        return line.copy().withStyle(ChatFormatting.AQUA);
    }

    /** 找不到能站、又归他动的地方：给触发的人说一句，同一团每 {@value #NO_ROOM_NOTICE_TICKS} 刻最多一次 */
    private static void noRoom(ServerLevel level, MRUPresenceEntity presence, ServerPlayer trigger) {
        long now = level.getGameTime();
        Long next = NO_ROOM_NOTICE.get(presence.getUUID());
        if (next != null && now < next) {
            return;
        }
        remember(NO_ROOM_NOTICE, presence.getUUID(), now + NO_ROOM_NOTICE_TICKS, now);
        trigger.displayClientMessage(Component.translatable(MSG_NO_ROOM).withStyle(ChatFormatting.AQUA), true);
    }

    /** 找到的站位：脚下那格底面的中心；overhead = 是聚集体正上方那一列的露天地面（否则是在聚集体旁边找的） */
    public record Spot(Vec3 at, boolean overhead) {
    }

    /**
     * 魔像站哪：先在 base 正上方（同一 x / z，从 base 往上最多 {@code spawn_search_height} 格）找头顶露天（这一格不低于这一列的
     * {@code MOTION_BLOCKING_NO_LEAVES} 高度）、脚下实心、站得下的一格；没有就在 base 旁边由近到远找脚下实心、站得下的一格。都没有返回 null。
     * 站得下的格子还要过 allowed（落点权限），不过的跳过、接着找（正上方那一列被拒了照样退到旁边找）。
     * 只看已加载的区块，不触发加载。
     */
    @Nullable
    public static Spot findSpot(ServerLevel level, BlockPos base, EntityType<?> type, Predicate<BlockPos> allowed) {
        int height = ECMRUGolem.Config.values().trigger().searchHeight();
        if (level.isLoaded(base)) {
            int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base.getX(), base.getZ());
            int top = Math.min(base.getY() + height, level.getMaxBuildHeight() - 1);
            for (int y = Math.max(base.getY(), surface); y <= top; y++) {
                BlockPos pos = new BlockPos(base.getX(), y, base.getZ());
                if (standable(level, pos, type) && allowed.test(pos)) {
                    return new Spot(Vec3.atBottomCenterOf(pos), true);
                }
            }
        }
        List<BlockPos> beside = new ArrayList<>();
        for (int dx = -BESIDE_RADIUS; dx <= BESIDE_RADIUS; dx++) {
            for (int dz = -BESIDE_RADIUS; dz <= BESIDE_RADIUS; dz++) {
                if (dx * dx + dz * dz <= BESIDE_RADIUS * BESIDE_RADIUS) {
                    beside.add(new BlockPos(dx, 0, dz));
                }
            }
        }
        beside.sort((a, b) -> Integer.compare(a.getX() * a.getX() + a.getZ() * a.getZ(), b.getX() * b.getX() + b.getZ() * b.getZ()));
        for (BlockPos offset : beside) {
            for (int i = 0; i <= BESIDE_HEIGHT * 2; i++) {
                // 0, -1, +1, -2, +2 ……：先贴着聚集体那一层找
                int dy = (i & 1) == 0 ? -(i >> 1) : (i + 1) >> 1;
                BlockPos pos = base.offset(offset.getX(), dy, offset.getZ());
                if (standable(level, pos, type) && allowed.test(pos)) {
                    return new Spot(Vec3.atBottomCenterOf(pos), false);
                }
            }
        }
        return null;
    }

    /** 魔像能不能站在 pos（脚在这一格的底面）：区块已加载、在世界边界里、脚下那格顶面实心、碰撞箱里没有方块 / 液体 / 挡路的实体 */
    public static boolean standable(ServerLevel level, BlockPos pos, EntityType<?> type) {
        if (!level.isLoaded(pos) || !level.isLoaded(pos.above(2)) || !level.getWorldBorder().isWithinBounds(pos)
                || pos.getY() <= level.getMinBuildHeight() || pos.getY() + 3 >= level.getMaxBuildHeight()) {
            return false;
        }
        BlockPos below = pos.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
            return false;
        }
        AABB box = type.getDimensions().makeBoundingBox(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        return level.noCollision(box) && !level.containsAnyLiquid(box);
    }

    // ------------------------------------------------------------------ 杂项

    private static boolean peaceful(ServerLevel level) {
        Boolean override = testPeaceful;
        return override != null ? override : level.getDifficulty() == Difficulty.PEACEFUL;
    }

    private static void remember(Map<UUID, Long> table, UUID presence, long next, long now) {
        if (table.size() >= RETRY_LIMIT) {
            table.values().removeIf(at -> at <= now);
            if (table.size() >= RETRY_LIMIT) {
                table.clear();
            }
        }
        table.put(presence, next);
    }

    /** 只给 GameTest：把这团的重试计时、「找不到地方站」的提示间隔清掉（落点权限的结论不清，见 {@link #forgetLanding}） */
    public static void forgetRetry(UUID presence) {
        RETRY.remove(presence);
        NO_ROOM_NOTICE.remove(presence);
    }

    /** 只给 GameTest：忘掉这团的落点权限结论（测试里换了模拟领地时） */
    public static void forgetLanding(UUID presence) {
        LANDING.keySet().removeIf(key -> key.presence().equals(presence));
    }

    // ------------------------------------------------------------------ 落点权限

    private record LandingKey(UUID player, UUID presence, long cell) {
    }

    /** 结论：能不能动、记到哪一刻（服务器刻） */
    private record Landing(boolean allowed, int until) {
    }

    /**
     * 落点要触发的人能动（batch13.md §5.3.1；混合端自检 C67 的后一半）。聚集体那一格能动，不等于它正上方几十格的地面也能动：
     * GriefPrevention 这类领地默认只往地下伸几格，埋在别人房子底下的遗迹会把魔像放进人家院子，魔像不散去、会一直守在那里打回家的主人。
     * 以触发者的身份问候选格（{@link ECProtection#check}，预算按聚集体分），ALLOW / DENY 按（触发者、聚集体、格子）记
     * {@value #LANDING_REMEMBER_TICKS} 刻：站不下时每 {@code retry_ticks} 就要重试一次，探测是真的破坏事件，领地、日志、挂机插件都看得到，
     * 不能每次重问。一次尝试最多新问 {@value #MAX_LANDING_PROBES} 格，问到上限或者问不出来（UNKNOWN）的格子这次当不能站，
     * 记为 {@link #unsettled()}（这时不说「找不到地方站」，下次接着问）。
     */
    private static final class LandingPermit implements Predicate<BlockPos> {

        private final ServerLevel level;
        private final ServerPlayer player;
        private final MRUPresenceEntity presence;
        private int probes;
        private boolean unsettled;

        LandingPermit(ServerLevel level, ServerPlayer player, MRUPresenceEntity presence) {
            this.level = level;
            this.player = player;
            this.presence = presence;
        }

        @Override
        public boolean test(BlockPos pos) {
            int now = level.getServer().getTickCount();
            LandingKey key = new LandingKey(player.getUUID(), presence.getUUID(), pos.asLong());
            Landing known = LANDING.get(key);
            if (known != null && known.until() - now > 0) {
                return known.allowed();
            }
            if (probes >= MAX_LANDING_PROBES) {
                unsettled = true;
                return false;
            }
            probes++;
            ECProtection.Verdict verdict = ECProtection.check(player, level, pos, presence);
            if (verdict == ECProtection.Verdict.UNKNOWN) {
                unsettled = true;
                return false;
            }
            if (LANDING.size() >= LANDING_LIMIT) {
                LANDING.values().removeIf(entry -> entry.until() - now <= 0);
                if (LANDING.size() >= LANDING_LIMIT) {
                    LANDING.clear();
                }
            }
            boolean allowed = verdict == ECProtection.Verdict.ALLOW;
            LANDING.put(key, new Landing(allowed, now + LANDING_REMEMBER_TICKS));
            return allowed;
        }

        /** 这次有格子没问出结论（到了上限、预算用完、区块没加载全） */
        boolean unsettled() {
            return unsettled;
        }
    }

    /** 只给 GameTest：当成和平难度（true）/ 不是（false）/ 照世界的（null）。用完置回 null */
    public static void setTestPeaceful(@Nullable Boolean peaceful) {
        testPeaceful = peaceful;
    }
}
