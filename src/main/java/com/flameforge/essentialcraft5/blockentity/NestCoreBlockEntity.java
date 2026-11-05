package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.hoanna.NestCoreBlock;
import com.flameforge.essentialcraft5.entity.mob.ECMonster;
import com.flameforge.essentialcraft5.entity.mob.MobDamageShare;
import com.flameforge.essentialcraft5.hoanna.nest.HoannaNestType;
import com.flameforge.essentialcraft5.hoanna.nest.HoannaNestTypes;
import com.flameforge.essentialcraft5.hoanna.nest.HoannaNests;
import com.flameforge.essentialcraft5.registry.ECHoannaFramework;
import com.flameforge.essentialcraft5.util.SpawnOrigin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 巢穴核心的方块实体（第十三批 13d-HF，batch13.md §6.4「巢穴核心」、policy §4.19「巢穴」；HS §2.4.4）。
 * <h2>状态机（状态记在方块状态 {@link NestCoreBlock#PHASE} 上，这里存其余的）</h2>
 * <ul>
 *   <li><b>ARMED</b>：每 {@value #CHECK_INTERVAL} 刻（按 gameTime + 坐标哈希错开）遍历 {@code level.players()}，看
 *       {@code activate_radius}（16）格内有没有生存 / 冒险、活着的真玩家。有 → 一次放出整波（≤ 6 只，{@link #spawnWave}）→ ACTIVE。
 *       不放的情况：和平难度；1 200 刻的重试冷却里；附近那几名玩家里有人身边 48 格内已经有 {@code max_active_per_player}（2）个 ACTIVE 的巢穴
 *       （静态「活动巢穴」表 {@link HoannaNests#activeNear}，不扫方块）；类型找不到。一只都没放出来（站不下、混合端插件取消了生成、
 *       别的模组取消了 {@code FinalizeSpawn}）→ 留在 ARMED、{@value #RETRY_TICKS} 刻内不再试。</li>
 *   <li><b>ACTIVE</b>：每 {@value #CHECK_INTERVAL} 刻按 UUID 查这一波（{@code ServerLevel.getEntity}，O(1)）：找到的记下位置；找不到、
 *       而它上次在的区块实体已经加载（{@code areEntitiesLoaded}）的算没了（被别的东西删掉）；区块没加载的接着等。
 *       这一波的怪死的时候（{@code LivingDeathEvent}，按怪身上的持久标签认核心，{@link HoannaNests}）把它的
 *       {@link MobDamageShare#contributors} 并进 {@link #credit}，最后一只死掉 → {@link #settle}：给当时 {@code activate_radius} 格内、
 *       记录里打出过有效伤害（&gt; 0）的真玩家<b>各一份</b>奖励表 → DORMANT。最后几只是被删掉而不是被打死的，不给奖励、回 ARMED，
 *       并且和放不出来一样 {@value #RETRY_TICKS} 刻内不再放（清怪插件放一波删一波时不会每次检查都刷一整波）。
 *       {@code leash_radius}（48）格内一直没有生存 / 冒险的真玩家，累计满 {@code reset_ticks}（6 000，只在方块实体走着时累计）→
 *       收回还活着的（已加载的直接删，没加载的下次加载时删）、回 ARMED；中途转了和平难度也一样收回、回 ARMED。
 *       没打完就结束的这几种（收回、被删、转和平、类型丢了），这一波只要被打死过一只（{@link #killedAny}），就不回 ARMED、
 *       照清完那样进 DORMANT（不给奖励）：不然打掉领头的精英、留一只、走开等满 reset_ticks，回来又是一整波带精英，休眠被绕过去。</li>
 *   <li><b>DORMANT</b>：每 {@value #DORMANT_CHECK_INTERVAL} 刻看一次到没到 {@link #dormantUntil}（{@code recharge_ticks}，72 000 = 3 个游戏日），到了回 ARMED。</li>
 * </ul>
 * 所有到期刻（休眠到期、重试冷却）都按<b>主世界</b>的 gameTime 记（{@link HoannaNests#now}）：海纳尔和主世界的 gameTime 不一样，
 * 按方块所在世界记的话换了世界的计时会乱。
 * <h2>奖励怎么掷</h2>
 * 每名分到的人掷一次 {@code loot_table}，参数表 {@code entity}：THIS_ENTITY = 这一波最后被打死的那只（来源 NEST，所以
 * {@code essentialcraft5:from_nest} 为真），KILLER_ENTITY / DIRECT_KILLER_ENTITY / LAST_DAMAGE_PLAYER = 这名玩家（所以 {@code killed_by_player} 为真），
 * DAMAGE_SOURCE = 这名玩家的近战，ORIGIN = 那只怪的位置，幸运 = 这名玩家的幸运；东西直接进背包（{@code ECItemEntities.giveOrDrop}），
 * 再发手册进度 {@code guide/hoanna_explore/nest}。
 * <h2>放出来的怪</h2>
 * 来源先记 {@code NEST}（{@code SpawnOrigin.set}），再走 {@code ForgeEventFactory.onFinalizeSpawn}（被取消的不放）；领头的 {@code setElite(true)}、
 * 其余 {@code setElite(false)}（一波长什么样由 JSON 定，不再随机掷精英）；{@code setPersistenceRequired}；拴在核心 {@code leash_radius} 格内；
 * 持久标签记核心的世界和坐标（{@link HoannaNests#tagMember}），读档时重新拴上。
 */
public class NestCoreBlockEntity extends BlockEntity {

    /** ARMED / ACTIVE 多久看一次（刻） */
    public static final int CHECK_INTERVAL = 40;
    /** DORMANT 多久看一次（刻） */
    public static final int DORMANT_CHECK_INTERVAL = 200;
    /** 一只都没放出来以后多久不再试（刻，按主世界 gameTime） */
    public static final int RETRY_TICKS = 1_200;
    /** 「附近已经有几个 ACTIVE 的巢穴」按多远算（格） */
    public static final double NEARBY_ACTIVE_RADIUS = 48D;
    /** 出力记录最多记几人 */
    public static final int MAX_CREDITS = 32;
    /** 每只怪找落脚点最多试几次 */
    public static final int SPOT_ATTEMPTS = 12;
    /** 找落脚点时从核心的高度往上、往下各看几格 */
    public static final int SPOT_UP = 3;
    public static final int SPOT_DOWN = 4;

    /** 手册进度：清掉一处巢穴 */
    public static final ResourceLocation ADVANCEMENT = EssentialCraft5.id("guide/hoanna_explore/nest");

    private static final String TAG_TYPE = "type";
    private static final String TAG_WAVE = "wave";
    private static final String TAG_CREDIT = "credit";
    private static final String TAG_ID = "id";
    private static final String TAG_LAST = "last";
    private static final String TAG_DAMAGE = "damage";
    private static final String TAG_DORMANT_UNTIL = "dormant_until";
    private static final String TAG_RETRY_AT = "retry_at";
    private static final String TAG_ABSENT = "absent";
    private static final String TAG_KILLED = "killed";
    /** 「还没见过它在哪」 */
    private static final long UNKNOWN_POS = Long.MIN_VALUE;

    /** 一次检查（{@link #check}）的结果，指令和 GameTest 看 */
    public enum Outcome {
        /** 没到时候、没人、什么都没做 */
        IDLE,
        /** 类型找不到 */
        NO_TYPE,
        /** 和平难度（ACTIVE 时收回了这一波） */
        PEACEFUL,
        /** 重试冷却中 */
        COOLDOWN,
        /** 附近的人身边已经有够多 ACTIVE 的巢穴 */
        CAPPED,
        /** 放出了一波 */
        SPAWNED,
        /** 一只都没放出来，进重试冷却 */
        SPAWN_FAILED,
        /** 这一波还在打 */
        FIGHTING,
        /** 没人在场太久，收回了这一波（打死过的进 DORMANT，一只都没打死的回 ARMED） */
        RESET,
        /** 这一波的怪都没了但最后几只不是被打死的，不给奖励；打死过的进 DORMANT，一只都没打死的回 ARMED、进重试冷却 */
        VANISHED,
        /** 回充完了，回 ARMED */
        RECHARGED
    }

    @Nullable
    private ResourceLocation typeId;
    /** 这一波：UUID → 上次看到它的方块坐标（{@link BlockPos#asLong}），按放出的先后 */
    private final Map<UUID, Long> wave = new LinkedHashMap<>();
    /** 这一波谁打出了多少有效伤害（玩家 UUID → 合计） */
    private final Map<UUID, Float> credit = new LinkedHashMap<>();
    private long dormantUntil;
    private long retryAt;
    private int absentTicks;
    /** 这一波有没有被打死过（死在 {@link #onMemberDied} 里的算），放新一波时清掉 */
    private boolean killedAny;

    public NestCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ECHoannaFramework.NEST_CORE_BE.get(), pos, state);
    }

    // ================================================================== 走刻

    public static void serverTick(Level level, BlockPos pos, BlockState state, NestCoreBlockEntity be) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        int interval = state.getValue(NestCoreBlock.PHASE) == NestCoreBlock.Phase.DORMANT ? DORMANT_CHECK_INTERVAL : CHECK_INTERVAL;
        if (Math.floorMod(level.getGameTime() + stagger(pos), interval) != 0) {
            return;
        }
        be.check(server);
    }

    /** 坐标哈希出的错开量（0~199）：同一刻里各个巢穴不挤在同一刻查 */
    public static int stagger(BlockPos pos) {
        long h = pos.asLong() * 0x9E3779B97F4A7C15L;
        return (int) Math.floorMod(h ^ (h >>> 31), DORMANT_CHECK_INTERVAL);
    }

    /** 马上查一次（不看错开；走刻、指令、GameTest 调） */
    public Outcome check(ServerLevel level) {
        HoannaNestType type = HoannaNestTypes.get(typeId);
        long now = HoannaNests.now(level);
        return switch (phase()) {
            case DORMANT -> {
                if (now >= dormantUntil) {
                    setPhase(level, NestCoreBlock.Phase.ARMED);
                    yield Outcome.RECHARGED;
                }
                yield Outcome.IDLE;
            }
            case ARMED -> checkArmed(level, type, now);
            case ACTIVE -> checkActive(level, type);
        };
    }

    private Outcome checkArmed(ServerLevel level, @Nullable HoannaNestType type, long now) {
        if (type == null) {
            return Outcome.NO_TYPE;
        }
        if (HoannaNests.peaceful(level)) {
            return Outcome.PEACEFUL;
        }
        if (now < retryAt) {
            return Outcome.COOLDOWN;
        }
        List<Player> near = HoannaNests.fighters(level, Vec3.atCenterOf(worldPosition), type.activateRadius());
        if (near.isEmpty()) {
            return Outcome.IDLE;
        }
        int cap = ECHoannaFramework.Config.maxActivePerPlayer();
        for (Player player : near) {
            if (HoannaNests.activeNear(level, player.position(), NEARBY_ACTIVE_RADIUS, worldPosition) >= cap) {
                return Outcome.CAPPED;
            }
        }
        credit.clear();
        absentTicks = 0;
        int spawned = spawnWave(level, type);
        if (spawned == 0) {
            retryAt = now + RETRY_TICKS;
            setChanged();
            return Outcome.SPAWN_FAILED;
        }
        retryAt = 0L;
        setPhase(level, NestCoreBlock.Phase.ACTIVE);
        return Outcome.SPAWNED;
    }

    private Outcome checkActive(ServerLevel level, @Nullable HoannaNestType type) {
        if (type == null) {
            recall(level);
            return Outcome.NO_TYPE;
        }
        if (HoannaNests.peaceful(level)) {
            recall(level);
            return Outcome.PEACEFUL;
        }
        boolean changed = false;
        for (Iterator<Map.Entry<UUID, Long>> it = wave.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> entry = it.next();
            Entity member = level.getEntity(entry.getKey());
            if (member != null) {
                if (member.isAlive()) {
                    long at = member.blockPosition().asLong();
                    if (at != entry.getValue()) {
                        entry.setValue(at);
                        changed = true;
                    }
                } else {
                    it.remove();
                    changed = true;
                }
            } else if (entry.getValue() == UNKNOWN_POS || level.areEntitiesLoaded(ChunkPos.asLong(BlockPos.of(entry.getValue())))) {
                // 它在的区块实体都加载着却找不到：被别的东西删掉了
                it.remove();
                changed = true;
            }
        }
        if (changed) {
            setChanged();
        }
        if (wave.isEmpty()) {
            // 剩下的几只不是被打死、是被删掉的：多半是清怪插件、别的模组干的。照「一只都没放出来」那样等 RETRY_TICKS 再放，
            // 免得放 → 删 → 下一次检查又放一整波地循环刷生成；打死过的照清完那样休眠
            credit.clear();
            if (restAfterKills(level, type)) {
                return Outcome.VANISHED;
            }
            retryAt = HoannaNests.now(level) + RETRY_TICKS;
            EssentialCraft5.LOGGER.debug("[海纳尔巢穴] {} 在 {} 放出的这一波还没打完就全没了（被别的东西删掉），{} 刻后再放",
                    typeId, worldPosition, RETRY_TICKS);
            setPhase(level, NestCoreBlock.Phase.ARMED);
            return Outcome.VANISHED;
        }
        if (HoannaNests.fighters(level, Vec3.atCenterOf(worldPosition), type.leashRadius()).isEmpty()) {
            absentTicks += CHECK_INTERVAL;
            setChanged();
            if (absentTicks >= type.reset(ECHoannaFramework.Config.resetTicks())) {
                recall(level);
                return Outcome.RESET;
            }
        } else if (absentTicks != 0) {
            absentTicks = 0;
            setChanged();
        }
        return Outcome.FIGHTING;
    }

    // ================================================================== 放怪

    /** 放出整波（≤ {@value HoannaNestType#MAX_WAVE} 只），返回放出了几只。放出来的记进 {@link #wave} */
    public int spawnWave(ServerLevel level, HoannaNestType type) {
        wave.clear();
        killedAny = false;
        RandomSource random = level.random;
        for (HoannaNestType.Member member : type.members()) {
            for (int i = 0; i < member.count() && wave.size() < HoannaNestType.MAX_WAVE; i++) {
                spawnOne(level, type, member, random);
            }
        }
        setChanged();
        return wave.size();
    }

    private void spawnOne(ServerLevel level, HoannaNestType type, HoannaNestType.Member member, RandomSource random) {
        Vec3 spot = findSpot(level, worldPosition, member.type(), type.spawnRadius(), random);
        if (spot == null) {
            return;
        }
        Entity created = member.type().create(level);
        if (!(created instanceof Mob mob)) {
            if (created != null) {
                EssentialCraft5.LOGGER.warn("[海纳尔巢穴] {} 里的 {} 不是生物，放不出来", type.id(), EntityType.getKey(member.type()));
            }
            return;
        }
        mob.moveTo(spot.x, spot.y, spot.z, random.nextFloat() * 360F, 0F);
        mob.setYHeadRot(mob.getYRot());
        SpawnOrigin.set(mob, SpawnOrigin.Origin.NEST);
        HoannaNests.tagMember(mob, level, worldPosition, type.leashRadius());
        ForgeEventFactory.onFinalizeSpawn(mob, level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, null, null);
        if (mob.isSpawnCancelled()) {
            return;
        }
        if (mob instanceof ECMonster monster) {
            monster.setElite(member.leader());
        }
        mob.setPersistenceRequired();
        HoannaNests.leash(mob, worldPosition, type.leashRadius());
        // 先记进这一波再进世界：进世界时的加入事件会核对它在不在名单里
        wave.put(mob.getUUID(), mob.blockPosition().asLong());
        if (!level.addFreshEntity(mob)) {
            wave.remove(mob.getUUID());
        }
    }

    /**
     * 在 core 周围 radius 格内找一处这种怪站得下的地方：水平随机、从核心高度往上 {@value #SPOT_UP} 格到往下 {@value #SPOT_DOWN} 格，
     * 脚下那格顶面实心、身体不碰方块和实体、不在岩浆里（不看亮度）；只看已加载的区块，最多试 {@value #SPOT_ATTEMPTS} 次。找不到返回 null。
     */
    @Nullable
    public static Vec3 findSpot(ServerLevel level, BlockPos core, EntityType<?> type, int radius, RandomSource random) {
        for (int attempt = 0; attempt < SPOT_ATTEMPTS; attempt++) {
            int x = core.getX() + random.nextInt(radius * 2 + 1) - radius;
            int z = core.getZ() + random.nextInt(radius * 2 + 1) - radius;
            if (x == core.getX() && z == core.getZ()) {
                continue;
            }
            BlockPos column = new BlockPos(x, core.getY(), z);
            if (!level.hasChunkAt(column)) {
                continue;
            }
            for (int dy = SPOT_UP; dy >= -SPOT_DOWN; dy--) {
                BlockPos feet = column.above(dy);
                BlockPos below = feet.below();
                if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                    continue;
                }
                double px = x + 0.5D;
                double pz = z + 0.5D;
                AABB box = type.getAABB(px, feet.getY(), pz);
                if (level.getFluidState(feet).is(FluidTags.LAVA) || !level.noCollision(box)) {
                    continue;
                }
                return new Vec3(px, feet.getY(), pz);
            }
        }
        return null;
    }

    /**
     * 收回这一波（已加载的直接删，没加载的等它加载时由 {@link HoannaNests} 拦下），清记录；这一波被打死过的进 DORMANT
     * （{@link #restAfterKills}），一只都没打死的回 ARMED
     */
    public void recall(ServerLevel level) {
        discardWave(level);
        if (!restAfterKills(level, HoannaNestTypes.get(typeId))) {
            setPhase(level, NestCoreBlock.Phase.ARMED);
        }
    }

    private void discardWave(ServerLevel level) {
        for (UUID id : wave.keySet()) {
            Entity member = level.getEntity(id);
            if (member != null) {
                member.discard();
            }
        }
        wave.clear();
        credit.clear();
        absentTicks = 0;
    }

    /** 这一波没打完就结束了：被打死过的照清完那样进 DORMANT（不给奖励），返回 true；一只都没打死的什么都不做，返回 false */
    private boolean restAfterKills(ServerLevel level, @Nullable HoannaNestType type) {
        if (!killedAny) {
            return false;
        }
        killedAny = false;
        int recharge = type == null ? ECHoannaFramework.Config.rechargeTicks() : type.recharge(ECHoannaFramework.Config.rechargeTicks());
        dormantUntil = HoannaNests.now(level) + recharge;
        setPhase(level, NestCoreBlock.Phase.DORMANT);
        return true;
    }

    // ================================================================== 死了一只、结算

    /** 这一波里的一只死了（{@code LivingDeathEvent}）：记下出力，是最后一只就结算 */
    public void onMemberDied(ServerLevel level, LivingEntity member) {
        if (wave.remove(member.getUUID()) == null) {
            return;
        }
        killedAny = true;
        MobDamageShare.contributors(member).forEach(this::addCredit);
        setChanged();
        if (wave.isEmpty() && phase() == NestCoreBlock.Phase.ACTIVE) {
            settle(level, member);
        }
    }

    private void addCredit(UUID player, float damage) {
        if (!(damage > 0F) || !Float.isFinite(damage)) {
            return;
        }
        Float had = credit.get(player);
        if (had == null && credit.size() >= MAX_CREDITS) {
            return;
        }
        credit.put(player, (had == null ? 0F : had) + damage);
    }

    /** 清完了：给出过力、又在 activate_radius 格内的真玩家各一份（见类注释「奖励怎么掷」），进 DORMANT */
    private void settle(ServerLevel level, LivingEntity lastKilled) {
        HoannaNestType type = HoannaNestTypes.get(typeId);
        if (type != null) {
            for (ServerPlayer player : HoannaNests.rewardees(level, Vec3.atCenterOf(worldPosition), type.activateRadius(), credit)) {
                HoannaNests.giveReward(level, type, lastKilled, player);
            }
        }
        credit.clear();
        absentTicks = 0;
        killedAny = true;
        restAfterKills(level, type);
    }

    // ================================================================== 状态

    public NestCoreBlock.Phase phase() {
        BlockState state = getBlockState();
        return state.hasProperty(NestCoreBlock.PHASE) ? state.getValue(NestCoreBlock.PHASE) : NestCoreBlock.Phase.ARMED;
    }

    private void setPhase(ServerLevel level, NestCoreBlock.Phase phase) {
        BlockState state = getBlockState();
        if (state.hasProperty(NestCoreBlock.PHASE) && state.getValue(NestCoreBlock.PHASE) != phase) {
            level.setBlock(worldPosition, state.setValue(NestCoreBlock.PHASE, phase), Block.UPDATE_CLIENTS);
        }
        HoannaNests.setActive(level, worldPosition, phase == NestCoreBlock.Phase.ACTIVE);
        setChanged();
    }

    @Nullable
    public ResourceLocation typeId() {
        return typeId;
    }

    /** 写巢穴类型（世界生成时 {@link HoannaNests#placeCore} 调；指令、测试也用） */
    public void setType(@Nullable ResourceLocation type) {
        this.typeId = type;
        setChanged();
    }

    /** 这一波还没死的（UUID，按放出的先后） */
    public Set<UUID> waveIds() {
        return Collections.unmodifiableSet(wave.keySet());
    }

    /** 这一波的出力记录 */
    public Map<UUID, Float> credits() {
        return Collections.unmodifiableMap(credit);
    }

    public long dormantUntil() {
        return dormantUntil;
    }

    public long retryAt() {
        return retryAt;
    }

    public int absentTicks() {
        return absentTicks;
    }

    /** 指令 {@code arm} 用：结束回充和重试冷却（ACTIVE 时不动） */
    public boolean arm(ServerLevel level) {
        if (phase() == NestCoreBlock.Phase.ACTIVE) {
            return false;
        }
        dormantUntil = 0L;
        retryAt = 0L;
        setPhase(level, NestCoreBlock.Phase.ARMED);
        return true;
    }

    /** 指令 {@code reset} 用：收回这一波、清掉所有计时，回 ARMED */
    public void resetAll(ServerLevel level) {
        discardWave(level);
        killedAny = false;
        dormantUntil = 0L;
        retryAt = 0L;
        setPhase(level, NestCoreBlock.Phase.ARMED);
    }

    /** 这一波有没有被打死过 */
    public boolean killedAny() {
        return killedAny;
    }

    // ================================================================== 加载 / 卸载 / 存盘

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server && phase() == NestCoreBlock.Phase.ACTIVE) {
            HoannaNests.setActive(server, worldPosition, true);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel server) {
            HoannaNests.setActive(server, worldPosition, false);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (typeId != null) {
            tag.putString(TAG_TYPE, typeId.toString());
        }
        if (!wave.isEmpty()) {
            ListTag list = new ListTag();
            wave.forEach((id, last) -> {
                CompoundTag entry = new CompoundTag();
                entry.putUUID(TAG_ID, id);
                entry.putLong(TAG_LAST, last);
                list.add(entry);
            });
            tag.put(TAG_WAVE, list);
        }
        if (!credit.isEmpty()) {
            ListTag list = new ListTag();
            credit.forEach((id, damage) -> {
                CompoundTag entry = new CompoundTag();
                entry.putUUID(TAG_ID, id);
                entry.putFloat(TAG_DAMAGE, damage);
                list.add(entry);
            });
            tag.put(TAG_CREDIT, list);
        }
        if (dormantUntil != 0L) {
            tag.putLong(TAG_DORMANT_UNTIL, dormantUntil);
        }
        if (retryAt != 0L) {
            tag.putLong(TAG_RETRY_AT, retryAt);
        }
        if (absentTicks != 0) {
            tag.putInt(TAG_ABSENT, absentTicks);
        }
        if (killedAny) {
            tag.putBoolean(TAG_KILLED, true);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        typeId = tag.contains(TAG_TYPE, Tag.TAG_STRING) ? ResourceLocation.tryParse(tag.getString(TAG_TYPE)) : null;
        wave.clear();
        ListTag waveList = tag.getList(TAG_WAVE, Tag.TAG_COMPOUND);
        for (int i = 0; i < waveList.size() && wave.size() < HoannaNestType.MAX_WAVE; i++) {
            CompoundTag entry = waveList.getCompound(i);
            if (entry.hasUUID(TAG_ID)) {
                wave.put(entry.getUUID(TAG_ID), entry.contains(TAG_LAST, Tag.TAG_LONG) ? entry.getLong(TAG_LAST) : UNKNOWN_POS);
            }
        }
        credit.clear();
        ListTag creditList = tag.getList(TAG_CREDIT, Tag.TAG_COMPOUND);
        for (int i = 0; i < creditList.size(); i++) {
            CompoundTag entry = creditList.getCompound(i);
            if (entry.hasUUID(TAG_ID)) {
                addCredit(entry.getUUID(TAG_ID), entry.getFloat(TAG_DAMAGE));
            }
        }
        dormantUntil = tag.getLong(TAG_DORMANT_UNTIL);
        retryAt = tag.getLong(TAG_RETRY_AT);
        absentTicks = Math.max(0, tag.getInt(TAG_ABSENT));
        killedAny = tag.getBoolean(TAG_KILLED);
    }
}
