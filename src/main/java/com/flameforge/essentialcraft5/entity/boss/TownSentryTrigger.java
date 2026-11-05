package com.flameforge.essentialcraft5.entity.boss;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.entity.mob.MRUGolemTrigger;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECTownSentry;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.flameforge.essentialcraft5.util.SpawnOrigin;
import com.flameforge.essentialcraft5.worldgen.hoanna.town.HoannaTownPieces;
import com.flameforge.essentialcraft5.worldgen.hoanna.town.HoannaTownStructure;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
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
 * 海纳尔城镇废墟的废城守望者什么时候、在哪里醒（第十三批 13e-T，batch13.md §5.3.3、§7.5「小 Boss 触发」）。触发、落点权限照聚能魔像
 * （{@link MRUGolemTrigger}）写，差别是守着的不是聚集体，是城镇结构：
 *
 * <h2>每名玩家每 {@value #CHECK_INTERVAL} 刻查一次（按 gameTime 错开）</h2>
 * 真玩家、生存 / 冒险、活着、在海纳尔、不是和平难度、{@code trigger_radius} 不是 0：
 * <ol>
 *   <li>读他<b>所在区块</b>的结构引用表（{@code LevelChunk.getAllReferences()}，区块生成时就算好了的；他站着的区块一定加载着），找城镇的引用
 *       （城镇包围盒和这个区块相交才有）。引用指向起点所在的区块：那个区块此刻是完整加载的（{@code getChunkNow}）才往下看，不加载、不生成区块。
 *       比 {@code StructureManager.getStructureWithPieceAt} 便宜：后者按引用逐个 {@code getChunk(…, STRUCTURE_STARTS)}，可能同步加载区块；</li>
 *   <li>起点 = 城镇的 {@code town_start} 件（十字路口）的包围盒中心那一列、起点地面那一格（{@link #origin}）。玩家离它水平 ≤ {@code trigger_radius}、
 *       上下差 ≤ {@value #VERTICAL_REACH} 格才算走近了；</li>
 *   <li>存档 {@link TownGuardians} 里这座城还没有条目（没醒过）。老存档里更新前生成的城镇照样守（batch13.md「13e 已定事项」第 3 条定为 B），所以不给拼件加字段；</li>
 *   <li>这名玩家能动起点那一格（{@link ECProtection#check}，结论按（人, 城）记 {@value #CLAIM_REMEMBER_TICKS} 刻，同 {@code PresenceClaimMemory}）：
 *       只会在自己能动的城镇里醒，别人圈起来的城镇不会被路人吵醒；不能或问不出来，这次不触发；</li>
 *   <li>同一座城两次尝试至少隔 {@code retry_ticks}；</li>
 *   <li>{@link #spawn}：在起点附近（水平 {@value #SPOT_RADIUS} 格，由近到远）找地面上站得下的一格，<b>落点也要触发的人能动</b>（{@link LandingPermit}，
 *       照魔像：结论按（人, 城, 格子）记 {@value #CLAIM_REMEMBER_TICKS} 刻，一次最多新问 {@value #MAX_LANDING_PROBES} 格）；来源记 {@code STRUCTURE}，
 *       走一遍 {@code FinalizeSpawn} 事件，<b>真的进了世界</b>才在存档里记 {@link TownGuardians.State#SPAWNED}。被取消、站不下都保持没有条目，过 {@code retry_ticks} 再试；
 *       找不到能站、又归他动的地方时动作栏说一句（{@link #MSG_NO_ROOM}，同一座城每 {@value #NO_ROOM_NOTICE_TICKS} 刻最多一次）。</li>
 * </ol>
 * 触发的人进守望者的名单（看得到血条），动作栏说一句，放醒来的声音。只读已加载的区块；每次检查是 O(这个区块里的城镇引用数)，通常 0 或 1。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class TownSentryTrigger {

    /** 每名玩家多久查一次（刻） */
    public static final int CHECK_INTERVAL = 40;
    /** 玩家和起点上下最多差多少还算走近了（城里的塔有二十几格高） */
    public static final int VERTICAL_REACH = 32;
    /** 在起点附近水平多远以内找落点 */
    public static final int SPOT_RADIUS = 8;
    /** 落点和起点地面上下最多差多少（再远就是房顶、塔顶上了） */
    public static final int SPOT_HEIGHT = 6;
    /** 一次尝试最多新问几格落点的权限（记着的结论不算） */
    public static final int MAX_LANDING_PROBES = 8;
    /** 权限结论记多久（刻），同 {@code PresenceClaimMemory.REMEMBER_TICKS} */
    public static final int CLAIM_REMEMBER_TICKS = 6000;
    /** 「找不到地方站」同一座城多久最多说一次（刻） */
    public static final int NO_ROOM_NOTICE_TICKS = 200;
    /** 各张表最多记几条（超过先删过期的，还多就整个清掉） */
    private static final int LIMIT = 16384;

    public static final String MSG_AWAKENED = "message.essentialcraft5.town_sentry.awakened";
    public static final String MSG_NO_ROOM = "message.essentialcraft5.town_sentry.no_room";

    /** 城 → 下一次可以尝试的游戏时间（只在内存里） */
    private static final Map<TownGuardians.Town, Long> RETRY = new HashMap<>();
    /** 城 → 下一次可以说「找不到地方站」的游戏时间 */
    private static final Map<TownGuardians.Town, Long> NO_ROOM_NOTICE = new HashMap<>();
    /** 起点那一格的权限结论（按人、城） */
    private static final Map<ClaimKey, Verdict> CLAIMS = new HashMap<>();
    /** 落点权限的结论（按人、城、格子） */
    private static final Map<LandingKey, Verdict> LANDING = new HashMap<>();
    @Nullable
    private static Boolean testPeaceful;

    private TownSentryTrigger() {
    }

    /** 找到的一座城：哪座、起点那一格（十字路口中心、起点地面） */
    public record Found(TownGuardians.Town town, BlockPos origin) {
    }

    private record ClaimKey(UUID player, TownGuardians.Town town) {
    }

    private record LandingKey(UUID player, TownGuardians.Town town, long cell) {
    }

    /** 结论：能不能动、记到哪一刻（服务器刻） */
    private record Verdict(boolean allowed, int until) {
    }

    // ------------------------------------------------------------------ 事件

    @SubscribeEvent
    public static void townSentryPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        if ((player.level().getGameTime() + player.getId()) % CHECK_INTERVAL != 0) {
            return;
        }
        check(player);
    }

    @SubscribeEvent
    public static void townSentryServerStopped(ServerStoppedEvent event) {
        RETRY.clear();
        NO_ROOM_NOTICE.clear();
        CLAIMS.clear();
        LANDING.clear();
        testPeaceful = null;
    }

    @SubscribeEvent
    public static void townSentryLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!event.getEntity().level().isClientSide) {
            forgetPlayer(event.getEntity().getUUID());
        }
    }

    // ------------------------------------------------------------------ 触发

    /** 查一次这名玩家身边（见类注释）。返回这次醒来的守望者（多半是空的）。GameTest 直接调 */
    public static List<TownSentryEntity> check(ServerPlayer player) {
        List<TownSentryEntity> spawned = new ArrayList<>();
        ECTownSentry.Config.Trigger config = ECTownSentry.Config.values().trigger();
        if (config.radius() <= 0 || !ECProtection.isRealPlayer(player) || !player.isAlive() || player.isSpectator() || player.isCreative()) {
            return spawned;
        }
        ServerLevel level = player.serverLevel();
        if (!ECHoanna.isHoanna(level) || peaceful(level)) {
            return spawned;
        }
        List<Found> towns = townsAt(level, player.blockPosition());
        if (towns.isEmpty()) {
            return spawned;
        }
        TownGuardians guardians = TownGuardians.get(level.getServer());
        long now = level.getGameTime();
        for (Found found : towns) {
            if (!near(player.position(), found.origin(), config.radius()) || guardians.state(found.town()) != null) {
                continue;
            }
            Long next = RETRY.get(found.town());
            if (next != null && now < next) {
                continue;
            }
            if (claim(player, level, found) != ECProtection.Verdict.ALLOW) {
                continue;
            }
            remember(RETRY, found.town(), now + config.retryTicks(), now);
            TownSentryEntity sentry = spawn(level, found, player);
            if (sentry != null) {
                RETRY.remove(found.town());
                NO_ROOM_NOTICE.remove(found.town());
                spawned.add(sentry);
            }
        }
        return spawned;
    }

    /** pos 离 origin 水平 ≤ radius、上下差 ≤ {@value #VERTICAL_REACH} */
    public static boolean near(Vec3 pos, BlockPos origin, double radius) {
        double dx = pos.x - (origin.getX() + 0.5D);
        double dz = pos.z - (origin.getZ() + 0.5D);
        return dx * dx + dz * dz <= radius * radius && Math.abs(pos.y - origin.getY()) <= VERTICAL_REACH;
    }

    /**
     * pos 所在区块里引用到的城镇（见类注释第 1 步）：起点所在的区块没完整加载、起点不合法的跳过。不加载、不生成区块。
     */
    public static List<Found> townsAt(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
        if (chunk == null) {
            return List.of();
        }
        List<Found> out = new ArrayList<>(1);
        for (Map.Entry<Structure, LongSet> entry : chunk.getAllReferences().entrySet()) {
            if (!(entry.getKey() instanceof HoannaTownStructure) || entry.getValue().isEmpty()) {
                continue;
            }
            for (long ref : entry.getValue()) {
                ChunkPos startChunk = new ChunkPos(ref);
                LevelChunk owner = level.getChunkSource().getChunkNow(startChunk.x, startChunk.z);
                if (owner == null) {
                    continue;
                }
                StructureStart start = owner.getStartForStructure(entry.getKey());
                BlockPos origin = start == null || !start.isValid() ? null : origin(start);
                if (origin != null) {
                    out.add(new Found(new TownGuardians.Town(level.dimension(), ref), origin));
                }
            }
        }
        return out;
    }

    /**
     * 城镇起点：{@code town_start} 件（十字路口）包围盒中心那一列、起点地面那一格（{@code groundY}；没记的取包围盒底 + 3，包围盒是起点地面 ±3）。
     * 找不到起点件时用第一件的包围盒中心。没有拼件返回 null。
     */
    @Nullable
    public static BlockPos origin(StructureStart start) {
        List<StructurePiece> pieces = start.getPieces();
        if (pieces.isEmpty()) {
            return null;
        }
        StructurePiece pick = pieces.get(0);
        for (StructurePiece piece : pieces) {
            if (piece instanceof HoannaTownPieces.Start) {
                pick = piece;
                break;
            }
        }
        BoundingBox box = pick.getBoundingBox();
        BlockPos center = box.getCenter();
        int y = pick instanceof HoannaTownPieces.Start town && town.hasGround() ? town.groundY() : box.minY() + HoannaTownStructure.ROAD_HALF_HEIGHT;
        return new BlockPos(center.getX(), y, center.getZ());
    }

    /** 这名玩家能不能动起点那一格（结论按人、城记 {@value #CLAIM_REMEMBER_TICKS} 刻；UNKNOWN 不记） */
    private static ECProtection.Verdict claim(ServerPlayer player, ServerLevel level, Found found) {
        int now = level.getServer().getTickCount();
        ClaimKey key = new ClaimKey(player.getUUID(), found.town());
        Verdict known = CLAIMS.get(key);
        if (known != null && known.until() - now > 0) {
            return known.allowed() ? ECProtection.Verdict.ALLOW : ECProtection.Verdict.DENY;
        }
        ECProtection.Verdict verdict = ECProtection.check(player, level, found.origin(), found.town());
        if (verdict != ECProtection.Verdict.UNKNOWN) {
            trim(CLAIMS, now);
            CLAIMS.put(key, new Verdict(verdict == ECProtection.Verdict.ALLOW, now + CLAIM_REMEMBER_TICKS));
        }
        return verdict;
    }

    /**
     * 在 found 的起点附近放出守望者（见类注释第 6 步）。成功返回守望者，存档记「已醒」；站不下、被取消返回 null，存档不动。
     * 不查触发条件（GameTest 和 {@link #check} 调）。trigger 不为空时落点要他能动，为空时（GameTest 直接放）不看落点权限。
     */
    @Nullable
    public static TownSentryEntity spawn(ServerLevel level, Found found, @Nullable ServerPlayer trigger) {
        EntityType<TownSentryEntity> type = ECTownSentry.TOWN_SENTRY.get();
        LandingPermit permit = trigger == null ? null : new LandingPermit(level, trigger, found.town());
        BlockPos spot = findSpot(level, found.origin(), type, permit == null ? pos -> true : permit);
        if (spot == null) {
            if (permit != null && trigger != null && !permit.unsettled()) {
                noRoom(level, found.town(), trigger);
            }
            return null;
        }
        TownSentryEntity sentry = type.create(level);
        if (sentry == null) {
            return null;
        }
        Vec3 at = Vec3.atBottomCenterOf(spot);
        float yaw = level.random.nextFloat() * 360F;
        if (trigger != null) {
            yaw = (float) (Mth.atan2(trigger.getZ() - at.z, trigger.getX() - at.x) * Mth.RAD_TO_DEG) - 90F;
        }
        sentry.moveTo(at.x, at.y, at.z, yaw, 0F);
        sentry.setYHeadRot(yaw);
        sentry.setYBodyRot(yaw);
        sentry.guardTown(found.town());
        SpawnOrigin.set(sentry, SpawnOrigin.Origin.STRUCTURE);
        sentry.applyConfiguredStats();
        ForgeEventFactory.onFinalizeSpawn(sentry, level, level.getCurrentDifficultyAt(sentry.blockPosition()), MobSpawnType.STRUCTURE, null, null);
        if (sentry.isSpawnCancelled()) {
            return null;
        }
        sentry.setPersistenceRequired();
        if (!level.addFreshEntity(sentry)) {
            return null;
        }
        TownGuardians.get(level.getServer()).set(found.town(), TownGuardians.State.SPAWNED);
        level.playSound(null, at.x, at.y + 1.5D, at.z, ECTownSentry.SOUND_AWAKEN.get(), SoundSource.HOSTILE, 2.5F, 1F);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 1.5D, at.z, 30, 0.8D, 1.2D, 0.8D, 0.02D);
        if (trigger != null) {
            sentry.bossRoster().add(trigger);
            trigger.displayClientMessage(Component.translatable(MSG_AWAKENED).withStyle(ChatFormatting.GOLD), true);
        }
        return sentry;
    }

    /**
     * 守望者站哪：起点附近水平 {@value #SPOT_RADIUS} 格以内的各列，由近到远，每列取地面（{@code MOTION_BLOCKING_NO_LEAVES} 高度那一格），
     * 和起点地面上下差 ≤ {@value #SPOT_HEIGHT}、站得下（{@link MRUGolemTrigger#standable}）、过 allowed（落点权限）的第一格。都没有返回 null。
     * 只看已加载的区块。
     */
    @Nullable
    public static BlockPos findSpot(ServerLevel level, BlockPos origin, EntityType<?> type, Predicate<BlockPos> allowed) {
        List<int[]> columns = new ArrayList<>();
        for (int dx = -SPOT_RADIUS; dx <= SPOT_RADIUS; dx++) {
            for (int dz = -SPOT_RADIUS; dz <= SPOT_RADIUS; dz++) {
                if (dx * dx + dz * dz <= SPOT_RADIUS * SPOT_RADIUS) {
                    columns.add(new int[]{dx, dz});
                }
            }
        }
        columns.sort((a, b) -> Integer.compare(a[0] * a[0] + a[1] * a[1], b[0] * b[0] + b[1] * b[1]));
        for (int[] column : columns) {
            int x = origin.getX() + column[0];
            int z = origin.getZ() + column[1];
            if (!level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Math.abs(y - origin.getY()) > SPOT_HEIGHT) {
                continue;
            }
            BlockPos pos = new BlockPos(x, y, z);
            if (MRUGolemTrigger.standable(level, pos, type) && allowed.test(pos)) {
                return pos;
            }
        }
        return null;
    }

    /** 找不到能站、又归他动的地方：给触发的人说一句，同一座城每 {@value #NO_ROOM_NOTICE_TICKS} 刻最多一次 */
    private static void noRoom(ServerLevel level, TownGuardians.Town town, ServerPlayer trigger) {
        long now = level.getGameTime();
        Long next = NO_ROOM_NOTICE.get(town);
        if (next != null && now < next) {
            return;
        }
        remember(NO_ROOM_NOTICE, town, now + NO_ROOM_NOTICE_TICKS, now);
        trigger.displayClientMessage(Component.translatable(MSG_NO_ROOM).withStyle(ChatFormatting.GOLD), true);
    }

    // ------------------------------------------------------------------ 杂项

    private static boolean peaceful(ServerLevel level) {
        Boolean override = testPeaceful;
        return override != null ? override : level.getDifficulty() == Difficulty.PEACEFUL;
    }

    private static void remember(Map<TownGuardians.Town, Long> table, TownGuardians.Town town, long next, long now) {
        if (table.size() >= LIMIT) {
            table.values().removeIf(at -> at <= now);
            if (table.size() >= LIMIT) {
                table.clear();
            }
        }
        table.put(town, next);
    }

    private static <K> void trim(Map<K, Verdict> table, int now) {
        if (table.size() >= LIMIT) {
            table.values().removeIf(entry -> entry.until() - now <= 0);
            if (table.size() >= LIMIT) {
                table.clear();
            }
        }
    }

    /** 忘掉这名玩家的权限结论（下线时；GameTest 换了模拟领地时） */
    public static void forgetPlayer(UUID player) {
        CLAIMS.keySet().removeIf(key -> key.player().equals(player));
        LANDING.keySet().removeIf(key -> key.player().equals(player));
    }

    /** 只给 GameTest：清掉这座城的重试计时、「找不到地方站」的提示间隔和所有权限结论 */
    public static void forgetTown(TownGuardians.Town town) {
        RETRY.remove(town);
        NO_ROOM_NOTICE.remove(town);
        CLAIMS.keySet().removeIf(key -> key.town().equals(town));
        LANDING.keySet().removeIf(key -> key.town().equals(town));
    }

    /** 只给 GameTest：当成和平难度（true）/ 不是（false）/ 照世界的（null）。用完置回 null */
    public static void setTestPeaceful(@Nullable Boolean peaceful) {
        testPeaceful = peaceful;
    }

    // ------------------------------------------------------------------ 落点权限

    /**
     * 落点要触发的人能动（照 {@code MRUGolemTrigger.LandingPermit}）：起点那一格能动不等于旁边几格也能动（城里有人圈了一栋房子），
     * 守望者不散去、会一直守在那里打回家的主人。以触发者的身份问候选格（{@link ECProtection#check}，预算按城分），ALLOW / DENY 按（人, 城, 格子）记
     * {@value #CLAIM_REMEMBER_TICKS} 刻：探测是真的破坏事件，领地、日志、挂机插件都看得到，不能每次重问。一次尝试最多新问 {@value #MAX_LANDING_PROBES} 格，
     * 问到上限或者问不出来（UNKNOWN）的格子这次当不能站，记为 {@link #unsettled()}（这时不说「找不到地方站」，下次接着问）。
     */
    private static final class LandingPermit implements Predicate<BlockPos> {

        private final ServerLevel level;
        private final ServerPlayer player;
        private final TownGuardians.Town town;
        private int probes;
        private boolean unsettled;

        LandingPermit(ServerLevel level, ServerPlayer player, TownGuardians.Town town) {
            this.level = level;
            this.player = player;
            this.town = town;
        }

        @Override
        public boolean test(BlockPos pos) {
            int now = level.getServer().getTickCount();
            LandingKey key = new LandingKey(player.getUUID(), town, pos.asLong());
            Verdict known = LANDING.get(key);
            if (known != null && known.until() - now > 0) {
                return known.allowed();
            }
            if (probes >= MAX_LANDING_PROBES) {
                unsettled = true;
                return false;
            }
            probes++;
            ECProtection.Verdict verdict = ECProtection.check(player, level, pos, town);
            if (verdict == ECProtection.Verdict.UNKNOWN) {
                unsettled = true;
                return false;
            }
            trim(LANDING, now);
            boolean allowed = verdict == ECProtection.Verdict.ALLOW;
            LANDING.put(key, new Verdict(allowed, now + CLAIM_REMEMBER_TICKS));
            return allowed;
        }

        boolean unsettled() {
            return unsettled;
        }
    }
}
