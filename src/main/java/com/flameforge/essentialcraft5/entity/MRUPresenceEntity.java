package com.flameforge.essentialcraft5.entity;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.CorruptionEffects;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IMRUHandler;
import com.flameforge.essentialcraft5.api.MRUResistance;
import com.flameforge.essentialcraft5.mru.MRUBalance;
import com.flameforge.essentialcraft5.mru.MRUStorage;
import com.flameforge.essentialcraft5.corruption.CorruptionTicker;
import com.flameforge.essentialcraft5.corruption.PresenceClaimMemory;
import com.flameforge.essentialcraft5.entity.mob.MRUGolemGuardian;
import com.flameforge.essentialcraft5.entity.mob.MRUGolemTrigger;
import com.flameforge.essentialcraft5.registry.ECMRUCU;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * MRU 聚集体（MRUCU）。对应原作 EntityMRUPresence（渲染见 client/render/MRUPresenceRenderer）。
 * <p>
 * 一团飘在空中的 MRU：机器工作时泄漏的 MRU 汇到最近的聚集体里（{@link com.flameforge.essentialcraft5.mru.ECCorruption}），
 * 钻石仪式也往这里加 MRU，上古遗迹中心自带一个。存量达到 10000 以后，12 格内的玩家会累积矩阵损伤。
 * <p>
 * 数据：MRU 存量（上限 20000）、平衡值、flag（为 true 时跳过下一轮结算，然后自动清掉——新生成的聚集体第一轮不伤人）、
 * alwaysStay（MRU 归零后是否保留）。NBT 键 mru / balance / flag / stay，所以可以
 * {@code /summon essentialcraft5:mru_presence ~ ~ ~ {mru:5000,balance:1.0f}} 手动放一个（不写 stay 时默认保留，同原作）。
 * <p>
 * 移植改动：
 * <ul>
 *   <li>原作继承 EntityLivingBase（血量、属性、装备栏全是空实现）；这里是普通 Entity：不受重力、不碰撞、不被推、不随活塞移动、
 *       不穿传送门、不着火、不能被攻击，只有 /kill 能删（原作 onKillCommand 同理）。</li>
 *   <li><b>腐化不扩散</b>：原作每 20 tick 沿随机方向往外放腐化方块（按平衡值选混沌 / 冰冻 / 魔力腐化），
 *       并让已有的腐化方块长大；这段整个不移植，只保留给玩家的矩阵损伤。</li>
 *   <li>同格合并：原作每 tick、客户端也做（客户端会自己删实体，和服务端对不上）；这里只在服务端每 4 tick 做一次。
 *       两个都是 0 MRU 时原作的加权平均会算出 NaN 平衡值，这里跳过。被吞掉的一方是 alwaysStay 时，吞它的一方继承这个标记。</li>
 *   <li>alwaysStay：原作只存不用；这里按原作接口注释实现——MRU 归零且不是 alwaysStay 就消失。</li>
 *   <li>矩阵损伤跳过创造、旁观模式的玩家（原作不跳过；原作叠腐化效果时就是这么判断的）。</li>
 *   <li>领地（第十批，{@code general.corruption.respect_claims}，默认开）：只给<b>能动聚集体所在那一格</b>的真玩家加矩阵损伤
 *       （以他的身份探测，结论按人和这团聚集体记 5 分钟，见 {@link #mayAffect}；探测不出结论的这一轮不加）。邻居在自己地里漏出来的聚集体伤不到你，
 *       去别人家串门也不会被人家的聚集体伤到；野外、单人、没有领地模组时和以前一样。损伤一律走 {@link CorruptionEffects#addMatrixDamage}（有上限），
 *       加上了就在动作栏提醒（{@link CorruptionTicker#noticePresence}，5 分钟最多一次）。</li>
 *   <li>存量不到 10000 时矩阵损伤恒为 0（原作公式里 MRU / 10000 是整数除法，保留），这时连射线遮挡都不用算，直接跳过。</li>
 *   <li>同步：原作每 tick 把整个存储序列化成 NBT 塞进 DataManager；这里 MRU 和平衡值各一个同步字段，变了才发。</li>
 * </ul>
 *
 * <h2>稳定（第三批，富集仓稳定器）</h2>
 * {@link #stabilize(int)} 让聚集体在接下来若干 tick 里<b>不给玩家加矩阵损伤</b>（{@link #isStabilized()}）。富集仓控制器在外壳里有稳定器时
 * 每 20 tick 给仓内的聚集体续 40 tick（EnrichmentControllerBlockEntity），拆掉稳定器最多 40 tick 后恢复。
 * 原作 TileMRUCUECBalancer 每 tick 把仓里聚集体的 flag 设成 true，flag 让下一轮结算整个跳过——也就是只压制这一轮的腐化扩散和矩阵损伤
 * （扩散不移植），合并照常。所以这里只跳过矩阵损伤；合并、MRU 归零消失照常。原作研究书写「95%」，代码是 100%，照代码。
 * 期限是世界游戏时间（存盘键 {@code stabilized_until}，没稳定过不写这个键），吞并别的聚集体时取两者较晚的期限。只在服务端有意义（不同步）。
 *
 * <h2>聚能魔像守护（第十三批 13c，batch13.md §5.3.1）</h2>
 * {@link #getGuardian()}：新生成的上古遗迹写「等着触发」和原位（存盘键 {@code guardian} / {@code guardian_origin}，见 {@link MRUGolemGuardian}）；
 * 没有这两个键的（老存档、指令、收容瓶放出来的）一律没有守护。合并时「已触发 / 已放弃」优先（{@link MRUGolemGuardian#merge}）；
 * 还在等的那团每 20 刻看一次有没有被挪离原位太远（{@link MRUGolemTrigger#checkDrift}）。触发本身在 {@link MRUGolemTrigger}。只在服务端有意义（不同步）。
 */
public class MRUPresenceEntity extends Entity {

    /** 原作 new MRUEntityStorage(20000) */
    public static final int MAX_MRU = 20000;
    /** 结算周期（原作 tickTimer） */
    private static final int CYCLE = 20;
    /** 合并检查间隔（原作每 tick） */
    private static final int MERGE_INTERVAL = 4;
    /** 原作玩家范围：±0.5 的盒子再 grow(12) */
    private static final double PLAYER_RANGE = 12.5D;

    /** 原作 SoundRegistry.entityMRUCUNoise。只在客户端本地播放；ECMRUCU 照样注册（/playsound 能用、不报未知音效），声音定义见 tools/sounds/mrucu.json */
    public static final SoundEvent NOISE = SoundEvent.createVariableRangeEvent(EssentialCraft5.id("sound.mrucu_noise"));

    private static final EntityDataAccessor<Integer> DATA_MRU =
            SynchedEntityData.defineId(MRUPresenceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_BALANCE =
            SynchedEntityData.defineId(MRUPresenceEntity.class, EntityDataSerializers.FLOAT);

    private static final String TAG_FLAG = "flag";
    private static final String TAG_STAY = "stay";
    private static final String TAG_STABILIZED_UNTIL = "stabilized_until";

    /** 服务端的权威数据；客户端只读同步字段 */
    private final MRUStorage storage = new MRUStorage(MAX_MRU).onChanged(this::syncStorage);
    private final LazyOptional<IMRUHandler> mruCapability = LazyOptional.of(() -> storage);
    private boolean flag;
    /** 原作 MRUEntityStorage 的默认值是 true */
    private boolean alwaysStay = true;
    private int cycleTimer;
    /** 稳定到哪个游戏时间（不含）；0 = 没稳定过 */
    private long stabilizedUntil;
    /** 聚能魔像守护（只在服务端有意义） */
    private MRUGolemGuardian guardian = MRUGolemGuardian.NONE;

    /** 客户端渲染用的旋转相位（原作 renderIndex，每 tick 加 0.001 × 平衡值，4 为一圈） */
    public float renderIndex;
    public float renderIndexO;

    public MRUPresenceEntity(EntityType<? extends MRUPresenceEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        // 原作 ignoreFrustumCheck：射线比碰撞箱大得多，不能按碰撞箱做视锥剔除
        this.noCulling = true;
    }

    /**
     * 新建一个聚集体（还没放进世界，调用方自己 addFreshEntity）。对应原作 ECUtils.createMRUCUAt。
     *
     * @param flag       true 时跳过第一轮结算
     * @param alwaysStay MRU 归零后是否保留
     */
    public static MRUPresenceEntity create(Level level, double x, double y, double z, int mru, float balance,
                                           boolean flag, boolean alwaysStay) {
        MRUPresenceEntity presence = new MRUPresenceEntity(ECMRUCU.MRU_PRESENCE.get(), level);
        presence.moveTo(x, y, z, 0F, 0F);
        presence.storage.setMRU(mru);
        presence.storage.setBalance(balance);
        presence.flag = flag;
        presence.alwaysStay = alwaysStay;
        return presence;
    }

    /**
     * pos 附近最近的聚集体（原作 ApiCore.getClosestMRUCUEntity）：水平 ±radius、竖直 ±radius/2 的盒子里，按到方块中心的距离取最近。
     * 只查已加载的实体，不触发区块加载。
     */
    @Nullable
    public static MRUPresenceEntity findClosest(Level level, BlockPos pos, int radius) {
        Vec3 center = Vec3.atCenterOf(pos);
        AABB box = new AABB(pos).inflate(radius, radius / 2, radius);
        MRUPresenceEntity closest = null;
        double best = Double.MAX_VALUE;
        for (MRUPresenceEntity presence : level.getEntitiesOfClass(MRUPresenceEntity.class, box, Entity::isAlive)) {
            double distance = presence.distanceToSqr(center);
            if (distance < best) {
                best = distance;
                closest = presence;
            }
        }
        return closest;
    }

    /**
     * pos 附近的全部聚集体（范围同 {@link #findClosest}），按到方块中心的距离从近到远排好。只查已加载的实体，不触发区块加载。
     * 泄漏结算要「从近到远找第一团能汇进去的」时用（mru/ECCorruption）。
     */
    public static List<MRUPresenceEntity> findNear(Level level, BlockPos pos, int radius) {
        Vec3 center = Vec3.atCenterOf(pos);
        AABB box = new AABB(pos).inflate(radius, radius / 2, radius);
        List<MRUPresenceEntity> found = new ArrayList<>(level.getEntitiesOfClass(MRUPresenceEntity.class, box, Entity::isAlive));
        found.sort(Comparator.comparingDouble(presence -> presence.distanceToSqr(center)));
        return found;
    }

    // ---------------------------------------------------------------- 数据

    /** 服务端的 MRU 存储（加减 MRU 用它；客户端上的值不可信，读数用 {@link #getMRU()}） */
    public IMRUHandler getStorage() {
        return storage;
    }

    public int getMRU() {
        return level().isClientSide ? entityData.get(DATA_MRU) : storage.getMRU();
    }

    public float getBalance() {
        return level().isClientSide ? entityData.get(DATA_BALANCE) : storage.getBalance();
    }

    public boolean getFlag() {
        return flag;
    }

    public void setFlag(boolean flag) {
        this.flag = flag;
    }

    public boolean isAlwaysStay() {
        return alwaysStay;
    }

    public void setAlwaysStay(boolean alwaysStay) {
        this.alwaysStay = alwaysStay;
    }

    /**
     * 稳定 ticks 刻：这段时间里不给玩家加矩阵损伤。只会延长、不会缩短已有的期限。只在服务端调。
     * 富集仓控制器每 20 tick 调一次 {@code stabilize(40)}。
     */
    public void stabilize(int ticks) {
        if (ticks > 0) {
            stabilizedUntil = Math.max(stabilizedUntil, level().getGameTime() + ticks);
        }
    }

    /** 现在是不是稳定着（服务端；客户端没有这份数据，恒为 false） */
    public boolean isStabilized() {
        return level().getGameTime() < stabilizedUntil;
    }

    /** 稳定期限（游戏时间，不含）；0 = 没稳定过 */
    public long getStabilizedUntil() {
        return stabilizedUntil;
    }

    /** 聚能魔像守护的记录（没有守护是 {@link MRUGolemGuardian#NONE}） */
    public MRUGolemGuardian getGuardian() {
        return guardian;
    }

    public void setGuardian(MRUGolemGuardian guardian) {
        this.guardian = guardian == null ? MRUGolemGuardian.NONE : guardian;
    }

    private void syncStorage() {
        if (!level().isClientSide) {
            entityData.set(DATA_MRU, storage.getMRU());
            entityData.set(DATA_BALANCE, storage.getBalance());
        }
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_MRU, 0);
        entityData.define(DATA_BALANCE, MRUBalance.PURE);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // mru / balance 两个键由 MRUStorage 读
        storage.deserializeNBT(tag);
        flag = tag.getBoolean(TAG_FLAG);
        alwaysStay = !tag.contains(TAG_STAY) || tag.getBoolean(TAG_STAY);
        stabilizedUntil = Math.max(0L, tag.getLong(TAG_STABILIZED_UNTIL));
        guardian = MRUGolemGuardian.load(tag);
        syncStorage();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.merge(storage.serializeNBT());
        tag.putBoolean(TAG_FLAG, flag);
        tag.putBoolean(TAG_STAY, alwaysStay);
        if (stabilizedUntil > 0L) {
            tag.putLong(TAG_STABILIZED_UNTIL, stabilizedUntil);
        }
        guardian.save(tag);
    }

    // ---------------------------------------------------------------- tick

    @Override
    public void tick() {
        // 不调 super.tick()：baseTick 管的是着火、水流推动与入水水花、下界传送门、疾跑粒子，聚集体都用不上（原作把这些逐个覆盖成了空实现）
        checkBelowWorld();
        firstTick = false;
        setDeltaMovement(Vec3.ZERO);
        if (level().isClientSide) {
            clientTick();
            return;
        }
        if (tickCount % MERGE_INTERVAL == 0) {
            merge();
        }
        if (guardian.pending() && tickCount % CYCLE == 0) {
            MRUGolemTrigger.checkDrift(this);
        }
        if (--cycleTimer <= 0) {
            cycleTimer = CYCLE;
            cycle();
        }
    }

    private void clientTick() {
        renderIndexO = renderIndex;
        renderIndex += 0.001F * getBalance();
        if (renderIndex > 4F) {
            // 4 正好转一圈（×90°），两个一起减，插值不会跳
            renderIndex -= 4F;
            renderIndexO -= 4F;
        }
        // 原作：客户端每 tick 2.5% 概率播放嗡鸣声，音量 MRU/60000
        // 原作的录音来自 freesfx（不许再分发，CREDITS.md「不收录」），声音定义指向原版信标的环境嗡鸣（tools/sounds/mrucu.json）
        if (random.nextFloat() < 0.025F) {
            level().playLocalSound(getX(), getY(), getZ(), NOISE, SoundSource.BLOCKS,
                    getMRU() / 60000F, 0.1F + random.nextFloat(), false);
        }
    }

    /** 把同一格里 MRU 不比自己多的聚集体吞掉（原作 merge） */
    private void merge() {
        AABB box = new AABB(getX() - 0.5D, getY() - 0.5D, getZ() - 0.5D, getX() + 0.5D, getY() + 0.5D, getZ() + 0.5D);
        int mine = storage.getMRU();
        List<MRUPresenceEntity> smaller = level().getEntitiesOfClass(MRUPresenceEntity.class, box,
                other -> other != this && other.isAlive() && other.storage.getMRU() <= mine);
        for (MRUPresenceEntity other : smaller) {
            absorb(other);
        }
    }

    /** 吞掉 other：平衡值按 MRU 加权平均，MRU 相加（超过上限的部分丢失，同原作） */
    public void absorb(MRUPresenceEntity other) {
        if (other == this || !other.isAlive()) {
            return;
        }
        int mine = storage.getMRU();
        int theirs = other.storage.getMRU();
        other.discard();
        if (mine + theirs > 0) {
            storage.setBalance((storage.getBalance() * mine + other.storage.getBalance() * theirs) / (mine + theirs));
        }
        storage.addMRU(theirs, true);
        alwaysStay |= other.alwaysStay;
        stabilizedUntil = Math.max(stabilizedUntil, other.stabilizedUntil);
        guardian = MRUGolemGuardian.merge(guardian, other.guardian);
    }

    /** 每 20 tick 一轮（原作 onEntityUpdate 里 tickTimer 归零的分支） */
    private void cycle() {
        runCycle(level().players());
    }

    /**
     * 结算一轮。正常由 tick 每 20 tick 调，players 是本维度的全部玩家；GameTest 传自己造的模拟玩家（模拟玩家不在 level.players() 里）。
     * 范围、创造 / 旁观、存活的判断都在里面做。
     */
    public void runCycle(List<? extends Player> players) {
        if (flag) {
            flag = false;
            return;
        }
        // 原作这里往外放腐化方块、让腐化方块长大。腐化不扩散，不移植。
        int tier = storage.getMRU() / 10000;
        // 富集仓稳定器压着的时候不伤玩家（见类注释「稳定」）
        if (tier > 0 && !isStabilized()) {
            damagePlayerMatrices(tier, players);
        }
        if (!alwaysStay && storage.getMRU() <= 0) {
            discard();
        }
    }

    /**
     * 给 12 格内的玩家累积矩阵损伤（原作 onEntityUpdate 的玩家循环）：
     * 从聚集体到玩家脚下每 0.5 格取一个方块，取其中最大的 MRU 抗性 r（默认 1，见 {@link MRUResistances}），
     * 有 1/⌊r⌋ 的概率受影响；受影响时损伤见 {@link #matrixDamage}，易伤系数取 {@link MRUResistance#vulnerability}（MRU 腐化抗性，原作 getGenResistance(0, player)）。
     */
    private void damagePlayerMatrices(int tier, List<? extends Player> players) {
        AABB range = new AABB(getX() - PLAYER_RANGE, getY() - PLAYER_RANGE, getZ() - PLAYER_RANGE,
                getX() + PLAYER_RANGE, getY() + PLAYER_RANGE, getZ() + PLAYER_RANGE);
        for (Player player : players) {
            if (player.isCreative() || player.isSpectator() || !player.isAlive() || !range.intersects(player.getBoundingBox())) {
                continue;
            }
            float resistance = rayResistance(player.position());
            if (random.nextInt(Math.max(1, Mth.floor(resistance))) != 0) {
                continue;
            }
            int added = matrixDamage(tier, MRUResistance.vulnerability(player, MRUResistance.Type.MRU_CORRUPTION));
            if (added > 0 && mayAffect(player)) {
                player.getCapability(ECCapabilities.PLAYER_DATA).ifPresent(data -> {
                    CorruptionEffects.addMatrixDamage(data, added);
                    if (player instanceof ServerPlayer serverPlayer && ECProtection.isRealPlayer(serverPlayer)) {
                        CorruptionTicker.noticePresence(serverPlayer);
                    }
                });
            }
        }
    }

    /**
     * 领地这一关（{@code general.corruption.respect_claims}）：真玩家要能动聚集体所在的那一格才受影响。只对这一轮真会加上损伤的人问
     * （调用前已经过了射线抗性那一掷、算出来的损伤大于 0），结论按（玩家、这团聚集体）记 {@value PresenceClaimMemory#REMEMBER_TICKS} 刻
     * （{@link PresenceClaimMemory}：每问一次都是以玩家身份发一次破坏事件，混合端上插件会当成他真挖了一格）；
     * 区块没加载全、预算用完时是「不知道」，这一轮不加。不是真玩家的（GameTest 的模拟玩家）不探测，照旧受影响；假玩家本来就不抽效果。
     */
    private boolean mayAffect(Player player) {
        if (!CorruptionEffects.respectClaims() || !ECProtection.isRealPlayer(player)) {
            return true;
        }
        return PresenceClaimMemory.check((ServerPlayer) player, this) == ECProtection.Verdict.ALLOW;
    }

    /**
     * 一轮结算给一个玩家加的矩阵损伤：⌊4 × tier / (10 − 10 × 易伤)⌋，易伤封顶 0.99，不到 1 点不加（原作 {@code if (matrixDamage >= 1)}）。
     * 无防护 1 级 40、2 级 80；魔力防护全套（易伤 0.75）1.6 / 3.2 → 1 / 3；完全免疫（易伤 0）0.4 / 0.8 → 都是 0。
     * <p>
     * 和原作的两处差别：
     * <ul>
     *   <li>原作用 float 算，易伤 1（封顶成 0.99）时 10 − 9.9 得 0.1000004，每级 39.99985 取整成 39；这里用 double 算，得 40（与 batch2 §3.9 写的「每级 40 点」一致，手册按这个写）。</li>
     *   <li>原作只在易伤 ≥ 1 时换成 0.99，易伤落在 (0.99, 1) 之间反而不封顶（0.999 时每级 400）；这里一律封顶 0.99，损伤随易伤单调变化。</li>
     * </ul>
     *
     * @param tier ⌊MRU / 10000⌋
     */
    public static int matrixDamage(int tier, float vulnerability) {
        double capped = Math.min(vulnerability, 0.99F);
        double damage = 4D * tier / (10D - 10D * capped);
        return damage >= 1D ? (int) Math.min(Integer.MAX_VALUE, Math.floor(damage)) : 0;
    }

    /** 从聚集体到 target 这条线上方块 MRU 抗性的最大值（至少 1） */
    private float rayResistance(Vec3 target) {
        Vec3 from = position();
        Vec3 delta = target.subtract(from);
        double length = delta.length();
        float max = 1F;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (double j = 0D; j < length; j += 0.5D) {
            double t = j / length;
            pos.set(Mth.floor(from.x + delta.x * t), Mth.floor(from.y + delta.y * t), Mth.floor(from.z + delta.z * t));
            max = Math.max(max, MRUResistances.get(level().getBlockState(pos)));
        }
        return max;
    }

    // ---------------------------------------------------------------- 不受外界影响

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean ignoreExplosion() {
        return true;
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    protected MovementEmission getMovementEmission() {
        return MovementEmission.NONE;
    }

    @Override
    public void thunderHit(ServerLevel level, LightningBolt bolt) {
    }

    /** 默认按碰撞箱大小（0.3 格 → 19 格）决定渲染距离；射线最长近 2 格，放到和追踪距离一样的 64 格 */
    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        double range = 64D * getViewScale();
        return distanceSqr < range * range;
    }

    // ---------------------------------------------------------------- capability

    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        // 父类构造时字段还没初始化，要判空
        if (cap == ECCapabilities.MRU && mruCapability != null) {
            return mruCapability.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        if (mruCapability != null) {
            mruCapability.invalidate();
        }
    }

    // ---------------------------------------------------------------- 配置

    /** machines.mru_presence：聚集体相关的服务端配置 */
    public static final class Config {
        public static ForgeConfigSpec.ConfigValue<List<? extends String>> blockResistances;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("mru_presence");
            blockResistances = builder.comment(
                            "MRU resistance of blocks between an MRUCU and a player, as \"namespace:block=resistance\".",
                            "A player is affected with a chance of 1/floor(max resistance along the line); unlisted blocks count as 1.",
                            "Entries for blocks that do not exist (yet) are ignored.",
                            "MRU 聚集体到玩家之间方块的 MRU 抗性，格式「命名空间:方块=抗性」。",
                            "玩家受影响的概率是 1/⌊连线上的最大抗性⌋，没列出的方块算 1；不存在（还没移植）的方块会被忽略。")
                    .defineListAllowEmpty("block_resistances", MRUResistances.DEFAULTS, MRUResistances::isValidEntry);
            builder.pop();
        }
    }
}
