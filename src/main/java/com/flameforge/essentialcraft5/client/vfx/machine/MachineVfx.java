package com.flameforge.essentialcraft5.client.vfx.machine;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxCompat;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxEffect;
import com.flameforge.essentialcraft5.client.vfx.VfxEffects;
import com.flameforge.essentialcraft5.config.VfxClientConfig;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 机器、建筑上常驻特效的总调度（线圈待机电弧、反应堆、棱镜光柱、射线、地脉光束、聚集体、五芒星和符文的辉光、淬炼台、传送器）。
 * <p>
 * 方块实体渲染器 / 实体渲染器在自己的 {@code render} 里调 {@link #submit} 登记一条，不画；原版只对视锥内、可见距离内的方块实体调渲染器，
 * 剔除就白拿了。登记的东西在特效阶段（{@link VfxEffects}，粒子之后）由这里一次画完，每帧清空。
 * 每一类有自己的同屏上限和距离，满了留近的、挤掉远的；电弧形状进 {@link ArcCache}，按台错开、隔几刻才换一次。
 * 自己作为一个不过期的特效挂进 {@link VfxEffects}，一阵子没人登记就退出来，没机器的时候不占特效那一帧。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class MachineVfx implements VfxEffect {

    public static final int COIL = 0;
    public static final int REACTOR = 1;
    public static final int PRISM = 2;
    public static final int RAY = 3;
    public static final int LEY = 4;
    public static final int PRESENCE = 5;
    public static final int PENTACLE = 6;
    public static final int WIND = 7;
    public static final int CRUCIBLE = 8;
    public static final int TELEPORTER = 9;
    static final int KINDS = 10;

    /** 每一类同屏最多画几台（挑最近的） */
    private static final int[] CAP = {64, 4, 16, 64, 16, 24, 16, 16, 8, 8};
    /** 每一类离镜头多远以内才画（格）；棱镜光柱几百格高，只比水平距离 */
    private static final double[] RANGE = {48, 64, 256, 64, 64, 64, 48, 48, 48, 48};
    private static final int MAX = 256;
    private static final AABB EVERYWHERE = new AABB(-3.0E7D, -1.0E4D, -3.0E7D, 3.0E7D, 1.0E4D, 3.0E7D);
    private static final Logger LOGGER = LoggerFactory.getLogger("EssentialCraft5/Vfx");

    /** 开发对照：false 时不登记也不画（同一局里比改前改后） */
    public static boolean enabled = true;
    /** 每 5 秒把统计写进日志 */
    public static boolean logStats;
    /** 最近一段的统计（TestHelper 的 getstatic 读） */
    public static String lastStats = "";

    private static final MachineVfx INSTANCE = new MachineVfx();
    private static final Object[] REF = new Object[MAX];
    private static final byte[] KIND = new byte[MAX];
    private static final double[] DIST2 = new double[MAX];
    private static final double[] X = new double[MAX];
    private static final double[] Y = new double[MAX];
    private static final double[] Z = new double[MAX];
    private static final int[] PER_KIND = new int[KINDS];
    /** 按特效档位缩的距离和上限，每刻更新一次：低档成片摆的时候主要贵在台数 */
    private static double rangeScale = 1D;
    private static final int[] capNow = CAP.clone();
    private static int size;
    private static boolean listed;
    private static long clientTicks;
    private static long lastTicked;
    private static long lastSubmitted;

    private static long statNanos;
    private static long statQuads;
    private static long statEntries;
    private static int statFrames;
    private static long statSince;

    private MachineVfx() {
    }

    /**
     * 登记这一帧要画的一台（在渲染器的 {@code render} 里调）。x / y / z 是用来比远近的点（世界坐标）。
     * 光影包画阴影图那一遍不登记，免得同一帧登记两次。
     */
    public static void submit(int kind, Object ref, double x, double y, double z) {
        if (!enabled || VfxCompat.renderingShadowPass()) {
            return;
        }
        // 物品栏里的方块实体模型（拿去画图标的那份）不在世界里，不登记
        Minecraft mc = Minecraft.getInstance();
        if (ref instanceof BlockEntity be ? be.getLevel() != mc.level : ref instanceof Entity e && e.level() != mc.level) {
            return;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double dx = x - cam.x;
        double dy = kind == PRISM ? 0D : y - cam.y;
        double dz = z - cam.z;
        double d2 = dx * dx + dy * dy + dz * dz;
        double range = RANGE[kind] * rangeScale;
        if (d2 > range * range) {
            return;
        }
        int slot = -1;
        if (PER_KIND[kind] < capNow[kind] && size < MAX) {
            slot = size++;
            PER_KIND[kind]++;
        } else {
            double worst = d2;
            for (int i = 0; i < size; i++) {
                if (KIND[i] == kind && DIST2[i] > worst) {
                    worst = DIST2[i];
                    slot = i;
                }
            }
            if (slot < 0) {
                return;
            }
        }
        REF[slot] = ref;
        KIND[slot] = (byte) kind;
        DIST2[slot] = d2;
        X[slot] = x;
        Y[slot] = y;
        Z[slot] = z;
        lastSubmitted = clientTicks;
        if (listed && clientTicks - lastTicked > 2L) {
            // 换世界时 VfxEffects 整个清空，不会告诉这里
            listed = false;
        }
        if (!listed) {
            VfxEffects.add(INSTANCE);
            listed = true;
            lastTicked = clientTicks;
        }
    }

    @Override
    public boolean tick() {
        lastTicked = clientTicks;
        if (clientTicks - lastSubmitted > 40L) {
            listed = false;
            ArcCache.clear();
            return false;
        }
        return true;
    }

    @Override
    public void render(VfxDraw draw) {
        if (size == 0) {
            return;
        }
        long t0 = Util.getNanos();
        int q0 = draw.quads();
        int entries = size;
        try {
            for (int i = 0; i < size; i++) {
                Object ref = REF[i];
                float dist = (float) Math.sqrt(DIST2[i]);
                switch (KIND[i]) {
                    case COIL -> CoilVfx.idle(draw, ref, dist);
                    case REACTOR -> CoilVfx.reactor(draw, ref, dist);
                    case PRISM -> BeamVfx.prism(draw, ref, dist);
                    case RAY -> BeamVfx.ray(draw, ref, dist);
                    case LEY -> BeamVfx.ley(draw, ref, dist);
                    case PRESENCE -> GlowVfx.presence(draw, ref, dist);
                    case PENTACLE -> GlowVfx.pentacle(draw, ref, dist);
                    case WIND -> GlowVfx.wind(draw, ref, dist);
                    case CRUCIBLE -> GlowVfx.crucible(draw, ref, dist);
                    case TELEPORTER -> TeleporterVfx.draw(draw, ref, dist);
                    default -> {
                    }
                }
            }
        } finally {
            reset();
        }
        statNanos += Util.getNanos() - t0;
        statQuads += draw.quads() - q0;
        statEntries += entries;
        statFrames++;
    }

    private static void reset() {
        for (int i = 0; i < size; i++) {
            REF[i] = null;
        }
        size = 0;
        for (int k = 0; k < KINDS; k++) {
            PER_KIND[k] = 0;
        }
    }

    @Override
    public AABB bounds() {
        return EVERYWHERE;
    }

    /** 客户端刻：数刻、隔一阵出一行统计 */
    @SubscribeEvent
    public static void onMachineVfxTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || mc.isPaused()) {
            return;
        }
        clientTicks++;
        VfxClientConfig.Quality q = Vfx.quality();
        rangeScale = q == VfxClientConfig.Quality.LOW ? 0.6D : q == VfxClientConfig.Quality.MEDIUM ? 0.8D : 1D;
        for (int k = 0; k < KINDS; k++) {
            capNow[k] = q == VfxClientConfig.Quality.LOW ? Math.max(2, CAP[k] / 3) : q == VfxClientConfig.Quality.MEDIUM ? Math.max(2, CAP[k] * 2 / 3) : CAP[k];
        }
        if (size > 0 && clientTicks - lastSubmitted > 2L) {
            // 登记了却没画到（特效那一帧关掉了），别留着旧引用
            reset();
        }
        if (clientTicks - statSince >= 100L) {
            if (statFrames > 0) {
                lastStats = String.format("frames=%d machineUs=%.1f quads=%.0f entries=%.1f ambient/s=%.1f strikes=%d",
                        statFrames, statNanos / 1000D / statFrames, statQuads / (double) statFrames, statEntries / (double) statFrames,
                        AmbientGlow.spawned() / 5D, CoilVfx.activeStrikes());
                if (logStats) {
                    LOGGER.info("[特效] 机器：{}", lastStats);
                }
            }
            statNanos = 0L;
            statQuads = 0L;
            statEntries = 0L;
            statFrames = 0;
            statSince = clientTicks;
        }
    }

    /** 刻计数（错开各台的刷新用） */
    static long ticks() {
        return clientTicks;
    }
}
