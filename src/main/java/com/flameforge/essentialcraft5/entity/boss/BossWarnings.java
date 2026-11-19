package com.flameforge.essentialcraft5.entity.boss;

import com.flameforge.essentialcraft5.network.ECNetwork;
import com.flameforge.essentialcraft5.network.S2CBossWarningPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 首领招式的预警（{@link S2CBossWarningPacket}）怎么发：起手时调一次这里的方法，客户端自己按时长画完；招式被打断时 {@link #cancel}。
 * 只管画面，伤害、判定都不经过这里。天灾之心、矩阵之影、聚能魔像、废城守望者共用。
 * 招式落下那一刻的画面（第 2 批）也从这里发：一次性的 {@link #resolve}（炸开、砸地、电弧……，不受撤销影响），
 * 挂在实体上的 {@link #attach}（漩涡、心智钉连线，能撤）；渊潮领主、炮台也用。
 * <p>
 * 发给谁：所属实体所在维度、预警中心 {@value #RANGE} 格 + 预警自身大小以内的玩家（以前的原版粒子是 32 格，场地边上的人也看得见）；
 * 只给特定玩家看的（熄光里的暗袭轨迹只给拿着光源的人）用带名单的重载。
 * <p>
 * GameTest：{@link #captureForTest()} 打开后，每个所属实体发过的包按实体 id 记下来（预警和撤销 {@link #sentForTest}、结算画面 {@link #resolvedForTest}），测「服务端发了什么」；
 * 同一批测试并行跑，各自按自己的首领取，互不干扰。
 */
public final class BossWarnings {

    /** 危险红（多数首领招式） */
    public static final int DANGER = 0xFF2A4A;
    /** 日阵金（天光棱线、烈日标记、正午、四灾归心的安全圈） */
    public static final int SOLAR = 0xFFD27A;
    /** 腐蚀绿（吐息） */
    public static final int CORROSION = 0xA8C83A;
    /** 暗袭紫 */
    public static final int SHADOW = 0xB070FF;
    /** 地震的熔橙（震波） */
    public static final int QUAKE = 0xFF7A3A;
    /** 矩阵品红（失序之柱） */
    public static final int MATRIX = 0xE040C0;
    /** 聚能魔像的 MRU 靛紫（和魔像身上的品红晶体同一族） */
    public static final int MRU_BLUE = 0x7A70FF;

    /** 发给预警中心多远内的玩家（再加上预警自己的大小） */
    public static final double RANGE = 64D;

    /** 每个所属实体最多记多少个包（测试用，防止开着记录时无限涨） */
    private static final int LOG_LIMIT = 256;

    private static boolean capture;
    private static final Map<Integer, List<S2CBossWarningPacket>> LOG = new HashMap<>();
    private static final Map<Integer, List<S2CBossWarningPacket>> RESOLVED = new HashMap<>();
    /** 所属实体 id → 它发出去的预警最晚什么时候（游戏刻）结束；过了就不用发撤销包 */
    private static final Map<Integer, Long> ACTIVE = new HashMap<>();

    private BossWarnings() {
    }

    // ------------------------------------------------------------------ 各种预警

    /** 圆形预警：center 是地面上的圆心；follow 不为 null 时圈心跟着它的水平位置走 */
    public static void circle(Entity owner, @Nullable Entity follow, Vec3 center, double radius, int ticks, int color) {
        send(owner, new S2CBossWarningPacket(S2CBossWarningPacket.CIRCLE, owner.getId(), id(follow), center.x, center.y, center.z,
                (float) radius, 0F, 0F, 0F, 0F, ticks, 0, color, 0, 0));
    }

    /** 圆形预警，圆心正上方 height 格有根柱子、能量往柱顶聚（灾柱反噬） */
    public static void pillarCircle(Entity owner, Vec3 center, double radius, double height, int ticks, int color) {
        send(owner, new S2CBossWarningPacket(S2CBossWarningPacket.CIRCLE, owner.getId(), -1, center.x, center.y, center.z,
                (float) radius, (float) height, 0F, 0F, 0F, ticks, 0, color, 0, S2CBossWarningPacket.FLAG_PILLAR));
    }

    /** 扇形预警：朝 dir（水平），左右各张开 halfAngle 弧度 */
    public static void sector(Entity owner, @Nullable Entity follow, Vec3 center, double radius, Vec3 dir, double halfAngle, int ticks, int color) {
        Vec3 d = horizontalUnit(dir);
        send(owner, new S2CBossWarningPacket(S2CBossWarningPacket.CIRCLE, owner.getId(), id(follow), center.x, center.y, center.z,
                (float) radius, (float) halfAngle, (float) d.x, 0F, (float) d.z, ticks, 0, color, 0, S2CBossWarningPacket.FLAG_SECTOR));
    }

    /** 贴地的线预警：a → b（取 a 的高度），半宽 halfWidth */
    public static void line(Entity owner, Vec3 a, Vec3 b, double halfWidth, int ticks, int color) {
        Vec3 d = new Vec3(b.x - a.x, 0D, b.z - a.z);
        double half = d.length() * 0.5D;
        Vec3 dir = horizontalUnit(d);
        send(owner, new S2CBossWarningPacket(S2CBossWarningPacket.LINE, owner.getId(), -1, (a.x + b.x) * 0.5D, a.y, (a.z + b.z) * 0.5D,
                (float) half, (float) halfWidth, (float) dir.x, 0F, (float) dir.z, ticks, 0, color, 0, 0));
    }

    /** 空中的瞄准线：from → to，只发给 viewers */
    public static void tether(Entity owner, Vec3 from, Vec3 to, int ticks, int color, Iterable<ServerPlayer> viewers) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        Vec3 dir = len < 1.0E-6D ? new Vec3(0D, 1D, 0D) : d.scale(1D / len);
        S2CBossWarningPacket packet = new S2CBossWarningPacket(S2CBossWarningPacket.LINE, owner.getId(), -1, from.x, from.y, from.z,
                (float) len, 0F, (float) dir.x, (float) dir.y, (float) dir.z, ticks, 0, color, 0, S2CBossWarningPacket.FLAG_TETHER);
        send(owner, packet, viewers);
    }

    /** 只发给 viewers 的圆形预警（暗袭落点） */
    public static void circleFor(Entity owner, Vec3 center, double radius, int ticks, int color, Iterable<ServerPlayer> viewers) {
        send(owner, new S2CBossWarningPacket(S2CBossWarningPacket.CIRCLE, owner.getId(), -1, center.x, center.y, center.z,
                (float) radius, 0F, 0F, 0F, 0F, ticks, 0, color, 0, 0), viewers);
    }

    /** 光柱（烈日标记）：柱脚 base、柱高 height、脚下标记圈半径 markRadius，跟着 follow 走 */
    public static void column(Entity owner, @Nullable Entity follow, Vec3 base, double height, double markRadius, int ticks, int color) {
        send(owner, new S2CBossWarningPacket(S2CBossWarningPacket.COLUMN, owner.getId(), id(follow), base.x, base.y, base.z,
                (float) height, (float) markRadius, 0F, 0F, 0F, ticks, 0, color, 0, 0));
    }

    /**
     * 移动的环：原地蓄力 delay 刻，然后从 from 格以每刻 speed 格（负数往里收）走到 to 格；band = 打击带的半宽。
     * 预警时长按「蓄力 + 走完」算。
     */
    public static void ring(Entity owner, @Nullable Entity follow, Vec3 center, double from, double to, double speed, double band, int delay,
                            int color) {
        int travel = Math.abs(speed) < 1.0E-6D ? 0 : (int) Math.ceil(Math.abs(to - from) / Math.abs(speed));
        send(owner, new S2CBossWarningPacket(S2CBossWarningPacket.RING, owner.getId(), id(follow), center.x, center.y, center.z,
                (float) from, (float) to, (float) speed, (float) band, 0F, delay + travel, delay, color, 0, 0));
    }

    /** 收缩安全圈：外圈从 outerRadius 往里收、ticks 刻后收到 safeRadius 落下；圈里安全（safeColor），圈外危险（dangerColor） */
    public static void safeZone(Entity owner, Vec3 center, double safeRadius, double outerRadius, int ticks, int safeColor, int dangerColor) {
        send(owner, new S2CBossWarningPacket(S2CBossWarningPacket.SAFE_ZONE, owner.getId(), -1, center.x, center.y, center.z,
                (float) safeRadius, (float) Math.max(safeRadius, outerRadius), 0F, 0F, 0F, ticks, 0, safeColor, dangerColor, 0));
    }

    // ------------------------------------------------------------------ 结算画面（第 2 批）

    /** 一次性的结算画面，只有位置、大小、颜色（砸地、碎裂、光柱破土这类）；style 是 {@code S2CBossWarningPacket.FX_*} */
    public static void resolve(Entity owner, int style, Vec3 at, double size, int color) {
        resolve(owner, style, at, size, 0D, Vec3.ZERO, 0, color, 0);
    }

    /**
     * 一次性的结算画面：size、size2、dir 的含义按画面编号（{@link S2CBossWarningPacket} 里各 {@code FX_*} 常量的说明），delay = 晚几刻出。
     * 不记进「进行中的预警」：之后的撤销包不会把正在炸开的画面撤掉。
     */
    public static void resolve(Entity owner, int style, Vec3 at, double size, double size2, Vec3 dir, int delay, int color, int color2) {
        if (!(owner.level() instanceof ServerLevel level)) {
            return;
        }
        S2CBossWarningPacket packet = S2CBossWarningPacket.resolve(style, owner.getId(), -1, at.x, at.y, at.z, (float) size, (float) size2,
                (float) dir.x, (float) dir.y, (float) dir.z, 0, delay, color, color2);
        logResolved(owner, packet);
        broadcast(level, packet, RANGE + Math.max(size, dir.length()));
    }

    /**
     * 挂在实体上的持续画面（漩涡、心智钉连线）：画 ticks 刻，follow 不为 null 时跟着它；所属实体没了客户端自己淡掉，
     * 提前结束用 {@link #cancel}（记进「进行中的预警」，撤销包照常发）。
     */
    public static void attach(Entity owner, @Nullable Entity follow, int style, Vec3 at, double size, int ticks, int color) {
        if (!(owner.level() instanceof ServerLevel level)) {
            return;
        }
        S2CBossWarningPacket packet = S2CBossWarningPacket.resolve(style, owner.getId(), id(follow), at.x, at.y, at.z, (float) size, 0F,
                0F, 0F, 0F, ticks, 0, color, 0);
        long now = level.getGameTime();
        ACTIVE.merge(owner.getId(), now + packet.duration() + 1L, Math::max);
        logResolved(owner, packet);
        broadcast(level, packet, RANGE + size + (follow == null ? 0D : follow.distanceTo(owner)));
    }

    /** 撤掉 owner 发出的、还没结束的全部预警（招式被打断、换相、散去）；它没有还在进行的预警时什么都不发 */
    public static void cancel(Entity owner) {
        if (!(owner.level() instanceof ServerLevel level)) {
            return;
        }
        Long end = ACTIVE.remove(owner.getId());
        if (end == null || level.getGameTime() > end) {
            return;
        }
        S2CBossWarningPacket packet = S2CBossWarningPacket.cancel(owner.getId());
        log(owner, packet);
        ECNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(owner.getX(), owner.getY(), owner.getZ(),
                RANGE * 2D, level.dimension())), packet);
    }

    // ------------------------------------------------------------------ 发包

    private static void send(Entity owner, S2CBossWarningPacket packet) {
        if (!(owner.level() instanceof ServerLevel level)) {
            return;
        }
        remember(owner, level, packet);
        double range = RANGE + Math.max(packet.size(), packet.kind() == S2CBossWarningPacket.CIRCLE ? 0F : packet.size2());
        ECNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(packet.x(), packet.y(), packet.z(),
                range, level.dimension())), packet);
    }

    private static void broadcast(ServerLevel level, S2CBossWarningPacket packet, double range) {
        ECNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(packet.x(), packet.y(), packet.z(),
                range, level.dimension())), packet);
    }

    private static void send(Entity owner, S2CBossWarningPacket packet, Iterable<ServerPlayer> viewers) {
        if (!(owner.level() instanceof ServerLevel level)) {
            return;
        }
        remember(owner, level, packet);
        for (ServerPlayer viewer : viewers) {
            if (!(viewer instanceof FakePlayer) && viewer.level() == level) {
                ECNetwork.sendTo(viewer, packet);
            }
        }
    }

    private static void remember(Entity owner, ServerLevel level, S2CBossWarningPacket packet) {
        long now = level.getGameTime();
        if (ACTIVE.size() > 64) {
            // 顺手清掉早就结束了的（实体 id 一直在涨，不清会越攒越多）
            Iterator<Map.Entry<Integer, Long>> it = ACTIVE.entrySet().iterator();
            while (it.hasNext()) {
                if (it.next().getValue() < now) {
                    it.remove();
                }
            }
        }
        ACTIVE.merge(owner.getId(), now + packet.duration() + 1L, Math::max);
        log(owner, packet);
    }

    private static void log(Entity owner, S2CBossWarningPacket packet) {
        if (!capture) {
            return;
        }
        List<S2CBossWarningPacket> list = LOG.computeIfAbsent(owner.getId(), k -> new ArrayList<>());
        if (list.size() < LOG_LIMIT) {
            list.add(packet);
        }
    }

    private static void logResolved(Entity owner, S2CBossWarningPacket packet) {
        if (!capture) {
            return;
        }
        List<S2CBossWarningPacket> list = RESOLVED.computeIfAbsent(owner.getId(), k -> new ArrayList<>());
        if (list.size() < LOG_LIMIT) {
            list.add(packet);
        }
    }

    private static int id(@Nullable Entity entity) {
        return entity == null ? -1 : entity.getId();
    }

    private static Vec3 horizontalUnit(Vec3 v) {
        double len = Math.sqrt(v.x * v.x + v.z * v.z);
        return len < 1.0E-6D ? new Vec3(1D, 0D, 0D) : new Vec3(v.x / len, 0D, v.z / len);
    }

    // ------------------------------------------------------------------ GameTest

    /** 打开记录（GameTest 用；打开后不再关，各测试按自己的实体取） */
    public static void captureForTest() {
        capture = true;
    }

    /** owner 发过的预警包（含撤销），按发的顺序 */
    public static List<S2CBossWarningPacket> sentForTest(Entity owner) {
        List<S2CBossWarningPacket> list = LOG.get(owner.getId());
        return list == null ? List.of() : List.copyOf(list);
    }

    /** owner 发过的结算画面包（{@link S2CBossWarningPacket#RESOLVE}，单独记，不混进 {@link #sentForTest}），按发的顺序 */
    public static List<S2CBossWarningPacket> resolvedForTest(Entity owner) {
        List<S2CBossWarningPacket> list = RESOLVED.get(owner.getId());
        return list == null ? List.of() : List.copyOf(list);
    }

    /** 清掉 owner 的记录（预警和结算画面都清） */
    public static void clearForTest(Entity owner) {
        LOG.remove(owner.getId());
        RESOLVED.remove(owner.getId());
    }
}
