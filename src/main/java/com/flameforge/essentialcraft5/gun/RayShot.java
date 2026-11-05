package com.flameforge.essentialcraft5.gun;

import com.flameforge.essentialcraft5.api.CorruptionEffects;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.network.S2CGunBeamPacket;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECGuns;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 枪械射线的一发（ECGuns，batch5.md §3.1）。原作每发生成一个会存盘的射线实体 {@code EntityMRURay}，沿视线每 0.5 格查一次实体、最多 128 步，
 * 这里<b>不生成实体</b>：服务端当场算完，再发一个 {@link S2CGunBeamPacket} 让周围的客户端画光束。
 * <ol>
 *   <li>方向 = 射手视线在俯仰、偏航<b>两个轴</b>上各加 {@code ±spread / 2}（原作只改 yaw 且被 {@code rotationYawHead} 盖掉，水平散布不生效）。</li>
 *   <li>从眼睛往外 {@link GunStats#RAY_RANGE} 格，{@code level.clip(COLLIDER)} 截到第一格<b>有碰撞箱</b>的方块：玻璃、树叶、栅栏、关着的门都挡，草丛、花不挡；
 *       只读方块，不破坏、不放置、不点火（原作用 {@code isNormalCube} 判断，玻璃、栅栏挡不住，还能隔着薄墙打中墙后的东西）。</li>
 *   <li>只在截断前那一段找活体：每 {@value #SEGMENT} 格一段、每段查一次（查询框外扩 1，≤ 6³，最多 16 次，policy §1.2「≤ 7³ 可以每 tick」）；
 *       命中 = 射线穿过碰撞箱，或只擦过外扩 {@value #HIT_TOLERANCE} 格的容差框、但离得最近的那一点在射线的起止之间且眼睛看得见碰撞箱（{@link #hitDistance}；
 *       身后的、贴在门 / 玻璃板 / 铁栏杆背后的都打不中）；
 *       跳过射手本人、同乘的、已死的、旁观者、射手的盟友（{@link #isAlly}）；按距离从近到远取前 {@link GunConfig#maxTargetsPerShot()} 个。</li>
 *   <li>伤害源 {@code mru_ray}：causing = 射手、直接实体为空、不带 is_projectile（D1 取 A），PvP、队伍、领地照攻击者管；
 *       打玩家乘 {@link GunConfig#playerDamageMultiplier()}（可以是 0：只损伤矩阵、不掉血）。
 *       目标是生存 / 冒险玩家时，<b>打中就加矩阵损伤</b>，不看伤害生效没有（GUNS-4 修订）：「打中」= 攻击没被 PvP / 队伍 / 领地拦下，
 *       由 {@link RayHitGate} 在同一次 {@code hurt} 里观察得出；加了就发特效（{@link GunMatrixFx}，限频）。详见 {@link #strike}。</li>
 *   <li>开火声用 {@code playSound(null, …)} 广播（原作在客户端本地播、服务端空实现，只有射手自己听得到），光束走 {@link BeamSink}。</li>
 * </ol>
 * 随机源、光束出口都作参数传进来：GameTest 能固定种子，也能在不靠玩家列表的情况下断言「发了一道光束」（测试玩家不在玩家列表，收不到 NEAR 包）。
 */
public final class RayShot {

    /** 找实体时每段多长（格） */
    public static final double SEGMENT = 4D;
    /** 每段查询框外扩（格）：覆盖容差框，大体型生物的碰撞箱本来就会和查询框相交 */
    private static final double QUERY_INFLATE = 1D;
    /** 命中容差：碰撞箱外扩这么多（原作每 0.5 格取一个 1×1×1 的框查实体，相当于外扩 0.5） */
    public static final double HIT_TOLERANCE = 0.5D;

    /** 擦边命中的视线终点从碰撞箱表面往里收多少（格）：生物贴墙、踩在方块上时，终点正好落在方块表面，浮点误差会被当成挡住 */
    private static final double SIGHT_INSET = 0.01D;
    /** 找「直线上离碰撞箱最近的点」时二分的次数（范围约 66 格，40 次精度约 1e-10 格） */
    private static final int CLOSEST_ITERATIONS = 40;
    /**
     * 导数的绝对值小于这个就当 0：正对着轴开枪时方向里会有 1e-16 这样的残差（{@code Mth.sin} 查表，sin(π) ≠ 0），
     * 不当 0 的话「最近的一段」会被推到某一头，左右两边的生物判法不一样
     */
    private static final double SLOPE_EPSILON = 1.0E-9D;

    /** 光束出口：服务端算完一发以后，射手、起点、终点、透镜 balance 交给它 */
    @FunctionalInterface
    public interface BeamSink {
        void beam(ServerLevel level, LivingEntity shooter, Vec3 from, Vec3 to, int balance);
    }

    /** 正式的出口：发光束包给起点 64 格内的玩家（带射手的实体 id，射手自己的客户端从枪口画） */
    public static final BeamSink PACKET = (level, shooter, from, to, balance) -> S2CGunBeamPacket.sendAround(level, shooter.getId(), from, to, balance);

    /** 矩阵损伤特效的出口：给一名玩家加了矩阵损伤以后调（正式是限频发包 {@link GunMatrixFx#PACKET}，测试传自己的记下来） */
    @FunctionalInterface
    public interface MatrixFxSink {
        /**
         * @param target    被加了矩阵损伤的玩家
         * @param intensity 特效强度 1~3（{@link GunMatrixFx#intensity}）
         */
        void matrixHit(ServerPlayer target, int intensity);
    }

    /**
     * 一个目标挨的这一下。
     *
     * @param damaged {@code hurt} 的返回值
     * @param matrix  加了矩阵损伤（目标是玩家、打中了）
     */
    public record Hit(boolean damaged, boolean matrix) {
    }

    /**
     * 一发的结果。
     *
     * @param from      起点（射手眼睛）
     * @param to        终点（挡住的方块表面，或射程尽头）
     * @param targets   射线穿过、按规则算作目标的活体（近的在前，已按上限截断）
     * @param damaged   其中 {@code hurt} 返回 true 的
     * @param matrixHit 其中被加了矩阵损伤的玩家（伤害生效没有都算）
     */
    public record Result(Vec3 from, Vec3 to, List<LivingEntity> targets, List<LivingEntity> damaged, List<ServerPlayer> matrixHit) {
    }

    private RayShot() {
    }

    /** 正式开火：出口是光束包、播开火声 */
    public static Result fire(LivingEntity shooter, GunStats stats, RandomSource random) {
        return fire(shooter, stats, random, true, PACKET);
    }

    /**
     * 服务端开一枪（不扣 MRU、不动枪的 NBT，那些在 {@code GunItem} 里）。客户端调用直接返回空结果。
     *
     * @param playSound 播不播开火声（加特林每 4 发播一次）
     * @param sink      光束出口（正式是 {@link #PACKET}，测试传自己的）
     */
    public static Result fire(LivingEntity shooter, GunStats stats, RandomSource random, boolean playSound, BeamSink sink) {
        Vec3 eye = shooter.getEyePosition();
        if (!(shooter.level() instanceof ServerLevel level)) {
            return new Result(eye, eye, List.of(), List.of(), List.of());
        }
        Vec3 direction = direction(shooter.getXRot(), shooter.getYRot(), stats.spread(), random);
        Vec3 far = eye.add(direction.scale(GunStats.RAY_RANGE));
        BlockHitResult blockHit = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
        Vec3 end = blockHit.getType() == HitResult.Type.MISS ? far : blockHit.getLocation();

        List<LivingEntity> targets = collectTargets(level, shooter, eye, end, GunConfig.maxTargetsPerShot());
        List<LivingEntity> damaged = new ArrayList<>();
        List<ServerPlayer> matrixHit = new ArrayList<>();
        for (LivingEntity target : targets) {
            Hit hit = strike(shooter, target, stats, GunMatrixFx.PACKET);
            if (hit.damaged()) {
                damaged.add(target);
            }
            if (hit.matrix() && target instanceof ServerPlayer player) {
                matrixHit.add(player);
            }
        }
        if (playSound) {
            float damage = stats.damage();
            level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), ECGuns.BEAM.get(), SoundSource.PLAYERS,
                    0.1F + damage / 100F, Mth.clamp(2F - damage / 50F, 0.5F, 2F));
        }
        sink.beam(level, shooter, eye, end, stats.balance());
        return new Result(eye, end, Collections.unmodifiableList(targets), Collections.unmodifiableList(damaged),
                Collections.unmodifiableList(matrixHit));
    }

    /**
     * 带散布的射线方向：俯仰、偏航各加 {@code r × spread / 2}，{@code r ∈ [−1, 1)}（纯函数，GameTest 直接测）。
     * spread 为 0 时就是视线方向，不消耗随机数。
     */
    public static Vec3 direction(float xRot, float yRot, float spread, RandomSource random) {
        if (spread <= 0F) {
            return Vec3.directionFromRotation(xRot, yRot);
        }
        float half = spread / 2F;
        float pitch = xRot + (random.nextFloat() * 2F - 1F) * half;
        float yaw = yRot + (random.nextFloat() * 2F - 1F) * half;
        return Vec3.directionFromRotation(Mth.clamp(pitch, -90F, 90F), yaw);
    }

    /**
     * 射线 eye → end（end 是挡住射线的方块表面或射程尽头）上的目标：分段查询、去重、按命中距离排序、取前 max 个。
     */
    public static List<LivingEntity> collectTargets(ServerLevel level, LivingEntity shooter, Vec3 eye, Vec3 end, int max) {
        double length = eye.distanceTo(end);
        if (length <= 1.0E-6D || max <= 0) {
            return new ArrayList<>();
        }
        Vec3 direction = end.subtract(eye).scale(1D / length);
        Set<LivingEntity> seen = new LinkedHashSet<>();
        List<Candidate> candidates = new ArrayList<>();
        for (double start = 0D; start < length; start += SEGMENT) {
            Vec3 a = eye.add(direction.scale(start));
            Vec3 b = eye.add(direction.scale(Math.min(length, start + SEGMENT)));
            AABB box = new AABB(a, b).inflate(QUERY_INFLATE);
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box, e -> isCandidate(shooter, e))) {
                if (seen.add(entity)) {
                    double distance = hitDistance(level, shooter, entity.getBoundingBox(), eye, end);
                    if (distance >= 0D) {
                        candidates.add(new Candidate(entity, distance));
                    }
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::distance));
        List<LivingEntity> out = new ArrayList<>(Math.min(max, candidates.size()));
        for (int i = 0; i < candidates.size() && i < max; i++) {
            out.add(candidates.get(i).entity());
        }
        return out;
    }

    private record Candidate(LivingEntity entity, double distance) {
    }

    /** 能不能当目标（不看位置）：不是射手本人、不是同乘的、活着、不是旁观者、不是射手的盟友 */
    public static boolean isCandidate(LivingEntity shooter, LivingEntity entity) {
        return entity != shooter
                && entity.isAlive()
                && !entity.isSpectator()
                && !entity.isPassengerOfSameVehicle(shooter)
                && !isAlly(shooter, entity);
    }

    /**
     * 射手的盟友（射线跳过）：同一支队伍的（{@code isAlliedTo}，关了友伤的队友本来也打不到）、
     * 射手驯服的（{@code TamableAnimal.isAlliedTo} 要在世界里找得到主人；射手找不到时——比如 GameTest 的测试玩家不在世界里——
     * 另按 {@link OwnableEntity#getOwnerUUID()} 认）。
     */
    public static boolean isAlly(Entity shooter, Entity entity) {
        if (shooter.isAlliedTo(entity) || entity.isAlliedTo(shooter)) {
            return true;
        }
        if (entity instanceof OwnableEntity ownable) {
            UUID owner = ownable.getOwnerUUID();
            return owner != null && owner.equals(shooter.getUUID());
        }
        return false;
    }

    /**
     * 射线 eye → end 打不打得中这个碰撞箱，打得中返回从眼睛算起的距离（排序用），打不中返回 −1。
     * <ul>
     *   <li>眼睛在碰撞箱里：距离 0。射线直接穿过碰撞箱：命中（end 是第一格挡住射线的方块，穿过的这一段本来就没被挡）。</li>
     *   <li>只擦过外扩 {@value #HIT_TOLERANCE} 格的容差框（射线没碰到碰撞箱本身），两条都满足才算：
     *     <ol>
     *       <li>射线所在直线上离碰撞箱最近的那一点（最近的是一段时取中点，{@link #closestApproach}）落在 [0, 射线长度] 里——
     *           身后的（原作第 0 步以眼睛为中心取 1³ 的框，贴在身后的也打得到）、在挡住射线的方块后面的都不算；</li>
     *       <li>从眼睛到碰撞箱上离射线最近的点（往箱里收 {@value #SIGHT_INSET} 格）看得见：{@code level.clip(COLLIDER)} 中途没碰到方块。
     *           斜着打玻璃板、门、铁栏杆时，射线还没碰到方块就先穿过了背后那只生物的容差框，只靠第 1 条拦不住。</li>
     *     </ol>
     *     只有擦边的候选才多这一次 clip。</li>
     * </ul>
     *
     * @param shooter 射手（{@code ClipContext} 的碰撞上下文；为 null 时按空上下文）
     */
    public static double hitDistance(BlockGetter level, @Nullable Entity shooter, AABB box, Vec3 eye, Vec3 end) {
        if (box.contains(eye)) {
            return 0D;
        }
        Optional<Vec3> direct = box.clip(eye, end);
        if (direct.isPresent()) {
            return eye.distanceTo(direct.get());
        }
        AABB tolerant = box.inflate(HIT_TOLERANCE);
        Optional<Vec3> graze = tolerant.clip(eye, end);
        if (graze.isEmpty() && !tolerant.contains(eye)) {
            return -1D;
        }
        double length = eye.distanceTo(end);
        if (length <= 1.0E-6D) {
            return -1D;
        }
        Vec3 direction = end.subtract(eye).scale(1D / length);
        double t = closestApproach(box, eye, direction, -1D, length + 1D);
        if (t < -1.0E-6D || t > length) {
            return -1D;
        }
        Vec3 sight = insetPoint(box, eye.add(direction.scale(t)));
        if (level.clip(new ClipContext(eye, sight, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter)).getType() != HitResult.Type.MISS) {
            return -1D;
        }
        return graze.map(eye::distanceTo).orElse(Math.max(0D, t));
    }

    /**
     * 直线 {@code origin + t × direction}（direction 是单位向量）上离碰撞箱最近的参数 t；最近的是一段（直线穿过碰撞箱，或沿轴平行掠过）时取这一段的中点。
     * 只在 [lo, hi] 里找：真正的最近点在范围外时返回靠近它的那一端（调用方用的范围比 [0, 射线长度] 两头各宽 1 格，照样判成「范围外」）。
     * <p>
     * 做法：距离² 对 t 是凸函数，它的半导数 {@link #approachSlope} 单调不减；二分出导数 &lt; 0 的最后一点和 &gt; 0 的第一点，取中间
     * （|导数| &lt; {@value #SLOPE_EPSILON} 算 0）。
     */
    static double closestApproach(AABB box, Vec3 origin, Vec3 direction, double lo, double hi) {
        double a = lo;
        double b = hi;
        for (int i = 0; i < CLOSEST_ITERATIONS; i++) {
            double m = (a + b) / 2D;
            if (approachSlope(box, origin, direction, m) < -SLOPE_EPSILON) {
                a = m;
            } else {
                b = m;
            }
        }
        double first = (a + b) / 2D;
        a = lo;
        b = hi;
        for (int i = 0; i < CLOSEST_ITERATIONS; i++) {
            double m = (a + b) / 2D;
            if (approachSlope(box, origin, direction, m) <= SLOPE_EPSILON) {
                a = m;
            } else {
                b = m;
            }
        }
        return (first + (a + b) / 2D) / 2D;
    }

    /** 点 origin + t × direction 到碰撞箱距离² 对 t 的导数的一半：各轴「超出碰撞箱多少」× 该轴方向分量之和（碰撞箱里面为 0） */
    private static double approachSlope(AABB box, Vec3 origin, Vec3 direction, double t) {
        return outside(origin.x + direction.x * t, box.minX, box.maxX) * direction.x
                + outside(origin.y + direction.y * t, box.minY, box.maxY) * direction.y
                + outside(origin.z + direction.z * t, box.minZ, box.maxZ) * direction.z;
    }

    private static double outside(double value, double min, double max) {
        return value < min ? value - min : (value > max ? value - max : 0D);
    }

    /** 碰撞箱上离 point 最近的点，再往箱里收 {@value #SIGHT_INSET} 格（箱子比两倍还薄时收到中线） */
    private static Vec3 insetPoint(AABB box, Vec3 point) {
        return new Vec3(inset(point.x, box.minX, box.maxX), inset(point.y, box.minY, box.maxY), inset(point.z, box.minZ, box.maxZ));
    }

    private static double inset(double value, double min, double max) {
        double margin = Math.min(SIGHT_INSET, (max - min) / 2D);
        return Mth.clamp(value, min + margin, max - margin);
    }

    /** 打中一个目标（特效走正式的限频发包），返回 {@code hurt} 的返回值；详见 {@link #strike} */
    public static boolean hit(LivingEntity shooter, LivingEntity target, GunStats stats) {
        return strike(shooter, target, stats, GunMatrixFx.PACKET).damaged();
    }

    /**
     * 打中一个目标。GameTest 的测试玩家不在世界里、射线找不到它，打玩家的测试直接调这里（或 {@link #hit}）。
     * <ul>
     *   <li>伤害 = {@code stats.damage()}，目标是玩家时再乘 {@link GunConfig#playerDamageMultiplier()}。</li>
     *   <li><b>矩阵损伤</b>（目标是生存 / 冒险、没开无敌、活着的 {@link ServerPlayer}，且 {@code matrix_damage_per_damage > 0}）：
     *       打中就加 {@code floor(枪的伤害 × matrix_damage_per_damage)}（原作口径：护甲前、<b>不乘</b>打玩家倍率——倍率为 0 时只损伤矩阵、不掉血；
     *       按 long 算、封顶 int），不看 {@code hurt} 的返回值（GUNS-4 修订：无敌帧、伤害被取消、被护甲吸收都照加）。
     *       「打中」由 {@link RayHitGate} 在这一次 {@code hurt} 里观察：PvP 关着、队伍不许、出生无敌、领地 / 保护模组（{@code LivingAttackEvent}）、
     *       混合端的领地插件（Bukkit {@code EntityDamageByEntityEvent}）拦下的都不算。倍率为 0 时照样调一次 {@code hurt(源, 0)}——只为走一遍这些判断
     *       （原版对 0 伤害在 {@code LivingAttackEvent} 之后直接返回 false，不会掉血）。</li>
     *   <li>加了矩阵损伤就交给 {@code fx}（正式的出口 {@link GunMatrixFx#PACKET} 自带限频）。</li>
     * </ul>
     * 打生物、打创造模式的玩家、矩阵损伤关掉时照旧：伤害 ≤ 0 不打，否则 {@code hurt} 一次。
     * <p>
     * <b>透镜</b>（第十批 10b，{@link LensEffects}）：混沌 / 冰冻 / 魔力 / 暗影透镜有效果时，这一下的 {@code hurt} 也经 {@link RayHitGate} 观察，
     * 打实了（{@link LensEffects#landed}）才给目标加腐蚀 / 冻结 / 驱散、给射手回血——和矩阵损伤「打中就算」的口径不同，伤害被拦下、被取消、在无敌帧里没落下都不加。
     *
     * @param fx 矩阵损伤特效的出口
     */
    public static Hit strike(LivingEntity shooter, LivingEntity target, GunStats stats, MatrixFxSink fx) {
        DamageSource source = new DamageSource(ECDamageTypes.holder(shooter.level(), ECGuns.MRU_RAY), null, shooter);
        float amount = stats.damage();
        if (target instanceof Player) {
            amount = (float) (amount * GunConfig.playerDamageMultiplier());
        }
        if (!(target instanceof ServerPlayer player) || !matrixApplies(player, stats)) {
            if (!(amount > 0F)) {
                return new Hit(false, false);
            }
            if (!LensEffects.active(stats.balance()) || target.level().isClientSide()) {
                return new Hit(target.hurt(source, amount), false);
            }
            // 透镜有效果：观察这一下，打实了才加（每个目标一个新的伤害源实例，RayHitGate 按实例认）
            RayHitGate.Watch lensWatch = RayHitGate.observe(target, source, amount);
            LensEffects.afterHit(shooter, target, stats.balance(), lensWatch);
            return new Hit(lensWatch.hurtResult(), false);
        }
        RayHitGate.Watch watch = RayHitGate.begin(source, player);
        boolean damaged;
        try {
            damaged = player.hurt(source, Math.max(0F, amount));
        } finally {
            RayHitGate.end(watch);
        }
        // 透镜的口径是「打实了」，和下面矩阵损伤的「打中了」各判各的
        LensEffects.afterHit(shooter, player, stats.balance(), watch);
        if (!RayHitGate.allowed(watch, shooter, player.server.getTickCount())) {
            return new Hit(damaged, false);
        }
        addMatrixDamage(player, stats.damage());
        fx.matrixHit(player, GunMatrixFx.intensity(stats.damage()));
        return new Hit(damaged, true);
    }

    /** 这一枪会不会给这名玩家加矩阵损伤（还没看打没打中）：生存 / 冒险、没开无敌（/ability 之类）、活着，枪有伤害，配置没关 */
    private static boolean matrixApplies(ServerPlayer player, GunStats stats) {
        return GunConfig.matrixDamagePerDamage() > 0
                && stats.damage() > 0F
                && player.gameMode.isSurvival()
                && !player.getAbilities().invulnerable
                && player.isAlive();
    }

    /** 矩阵损伤 += floor(枪的伤害 × 每点伤害的矩阵损伤)，按 long 算，夹在矩阵损伤上限以内（{@code general.corruption.max_matrix_damage}） */
    private static void addMatrixDamage(ServerPlayer player, float gunDamage) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            return;
        }
        long added = (long) Math.floor((double) gunDamage * GunConfig.matrixDamagePerDamage());
        CorruptionEffects.addMatrixDamage(data, added);
    }
}
