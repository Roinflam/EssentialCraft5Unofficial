package com.flameforge.essentialcraft5.client.builder;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.BuilderClientConfig;
import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxCompat;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxEffect;
import com.flameforge.essentialcraft5.client.vfx.VfxEffects;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import com.flameforge.essentialcraft5.config.VfxClientConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * 创世神之杖的飞行体（客户端）：平铺数组存每一格的起点、终点、方块状态号、起飞刻、落位刻、阶段，每刻推进、每帧画。
 * <ul>
 *   <li>同时在飞的方块最多 {@link BuilderClientConfig#flightsMax()} 块（0 = 按特效画质 24 / 64 / 128），每刻最多起飞四分之一；
 *   满了、超了、施法者不在视野里、离镜头 64 格外的只在落位时闪一下，128 格外的不画。</li>
 *   <li>高、中画质：弧线 + 拖尾（方块本体边飞边长大、转正，细短的薰衣草拖尾）；低画质，或光影包下关了方块本体（{@link #bodyUnderShaders}）时：
 *   光点凝成（飞行中只画光点，落位前 4 刻在格子里凝成方块）。</li>
 *   <li>方块本体在 {@code AFTER_ENTITIES} 用 {@code renderSingleBlock} 画（渲染类型同活塞推动的方块），拖尾和光点交给特效底座画，
 *   光影包下自动走稳妥画法；阴影图那一遍都不画。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class BuilderFlights {

    /** 数组总长：飞着的受 {@link #capacity()} 管，其余是只闪、只放声、等起飞的 */
    static final int SLOTS = 1024;

    /** 阶段：等起飞（飞出） */
    static final byte PENDING = 0;
    /** 飞出中 */
    static final byte FLY = 1;
    /** 到了落点、真方块还没出现，停着慢慢旋 */
    static final byte HOVER = 2;
    /** 只闪不飞：落位时闪一下（亮度减半）、放落位声 */
    static final byte FLASH = 3;
    /** 关了飞行画面：落位时只放声音 */
    static final byte SOUND = 4;
    /** 等起飞（飞回） */
    static final byte PENDING_BACK = 5;
    /** 飞回中 */
    static final byte BACK = 6;
    /** 没等到真方块，在当时的位置碎掉：抖一下、迸出碎块、缩小褪色，{@link BuilderFlightMath#BREAK_TICKS} 刻后移除 */
    static final byte BREAK = 7;

    /** 画法：0 = 按画质和光影包自动选，1 = 一律弧线 + 拖尾，2 = 一律光点凝成（开发对照用） */
    public static int styleOverride = 0;
    /**
     * 光影包（稳妥画法）下画不画方块本体。关掉时退成「只画光效」：飞行中只有光点，方块到时直接由真方块出现、落位闪光照旧。
     * 光影包下方块本体的表现还没逐款实测，先留着开，实测不对再改默认值。
     */
    public static boolean bodyUnderShaders = true;

    private static int count;
    private static final byte[] PHASE = new byte[SLOTS];
    private static final int[] CASTER = new int[SLOTS];
    private static final int[] JOB = new int[SLOTS];
    private static final int[] CELL_X = new int[SLOTS];
    private static final int[] CELL_Y = new int[SLOTS];
    private static final int[] CELL_Z = new int[SLOTS];
    private static final int[] STATE = new int[SLOTS];
    /** 起飞前格子里原来的方块状态号：格子换成了别的方块（混铺缺料换了一种）也算落位 */
    private static final int[] ORIG = new int[SLOTS];
    private static final double[] LAUNCH = new double[SLOTS];
    private static final double[] LAND = new double[SLOTS];
    private static final double[] END_CHECK = new double[SLOTS];
    private static final double[] FROM = new double[SLOTS * 3];
    private static final double[] TO = new double[SLOTS * 3];
    private static final double[] PREV_TO = new double[SLOTS * 3];
    private static final float[] PATH_LEN = new float[SLOTS];
    private static final float[] AXIS = new float[SLOTS * 3];
    private static final int[] SEED = new int[SLOTS];
    /** 开始碎时方块的大小 */
    private static final float[] BREAK_SCALE = new float[SLOTS];

    private static int clock;
    /** 正在画方块的（飞出、悬停、飞回）有几个 */
    private static int bodies;
    private static FlightFx fx;
    private static boolean renderFailed;

    static int peakBodies;
    static int launchedTotal;
    static int flashedTotal;
    static int shatteredTotal;
    static int landedTotal;

    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static final double[] POINT = new double[3];
    private static final double[] TRAIL = new double[8 * 3];

    private BuilderFlights() {
    }

    /** 客户端刻计数（暂停时不走） */
    static int clock() {
        return clock;
    }

    /** 同时在飞的上限：配置或按画质档 */
    static int capacity() {
        int tier = switch (Vfx.quality()) {
            case LOW -> 0;
            case MEDIUM -> 1;
            case HIGH -> 2;
        };
        return BuilderFlightMath.capacity(BuilderClientConfig.flightsMax(), tier);
    }

    // ------------------------------------------------------------------ 加入

    static void addOut(int casterId, int jobId, BlockPos cell, int stateId, double launchAt, double landAt, byte mode) {
        int i = take(casterId, jobId, cell, stateId, launchAt, landAt);
        if (i >= 0) {
            PHASE[i] = mode;
            if (mode == FLASH) {
                flashedTotal++;
            }
        }
    }

    static void addBack(int casterId, int jobId, BlockPos cell, int stateId, double launchAt, double landAt, boolean flashOnly) {
        if (flashOnly) {
            BuilderFlightClient.onLand(cell, true);
            flashedTotal++;
            return;
        }
        int i = take(casterId, jobId, cell, stateId, launchAt, landAt);
        if (i >= 0) {
            PHASE[i] = PENDING_BACK;
        }
    }

    private static int take(int casterId, int jobId, BlockPos cell, int stateId, double launchAt, double landAt) {
        if (count >= SLOTS) {
            return -1;
        }
        int i = count++;
        CASTER[i] = casterId;
        JOB[i] = jobId;
        CELL_X[i] = cell.getX();
        CELL_Y[i] = cell.getY();
        CELL_Z[i] = cell.getZ();
        STATE[i] = stateId;
        ClientLevel current = Minecraft.getInstance().level;
        ORIG[i] = current == null ? -1 : Block.getId(current.getBlockState(cell));
        LAUNCH[i] = launchAt;
        LAND[i] = landAt;
        END_CHECK[i] = -1D;
        SEED[i] = (cell.getX() * 31 + cell.getY()) * 31 + cell.getZ() + clock * 7;
        ensureFx();
        return i;
    }

    /** 收尾包：这个施法者这一单的格子，有方块的照常落位，没有的等 {@link BuilderFlightMath#END_GRACE} 刻再判断 */
    static void end(ClientLevel level, int casterId, int jobId) {
        for (int i = 0; i < count; i++) {
            if (CASTER[i] != casterId || JOB[i] != jobId || PHASE[i] == PENDING_BACK || PHASE[i] == BACK) {
                continue;
            }
            double at = BuilderFlightMath.endCheckAt(clock, present(level, i));
            if (at >= 0D && (END_CHECK[i] < 0D || at < END_CHECK[i])) {
                END_CHECK[i] = at;
            }
        }
    }

    private static void remove(int i) {
        byte p = PHASE[i];
        if (p == FLY || p == HOVER || p == BACK || p == BREAK) {
            bodies--;
        }
        int last = --count;
        if (i == last) {
            return;
        }
        PHASE[i] = PHASE[last];
        CASTER[i] = CASTER[last];
        JOB[i] = JOB[last];
        CELL_X[i] = CELL_X[last];
        CELL_Y[i] = CELL_Y[last];
        CELL_Z[i] = CELL_Z[last];
        STATE[i] = STATE[last];
        ORIG[i] = ORIG[last];
        LAUNCH[i] = LAUNCH[last];
        LAND[i] = LAND[last];
        END_CHECK[i] = END_CHECK[last];
        PATH_LEN[i] = PATH_LEN[last];
        SEED[i] = SEED[last];
        BREAK_SCALE[i] = BREAK_SCALE[last];
        System.arraycopy(FROM, last * 3, FROM, i * 3, 3);
        System.arraycopy(TO, last * 3, TO, i * 3, 3);
        System.arraycopy(PREV_TO, last * 3, PREV_TO, i * 3, 3);
        System.arraycopy(AXIS, last * 3, AXIS, i * 3, 3);
    }

    static void clear() {
        count = 0;
        bodies = 0;
        fx = null;
        BuilderFlightClient.reset();
    }

    private static void ensureFx() {
        if (fx == null || !fx.alive) {
            fx = new FlightFx();
            VfxEffects.add(fx);
        }
    }

    // ------------------------------------------------------------------ 每刻

    @SubscribeEvent
    public static void onBuilderFlightsTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            if (count > 0) {
                clear();
            }
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        clock++;
        if (count > 0) {
            update(level);
        }
    }

    private static void update(ClientLevel level) {
        int cap = capacity();
        int perTick = BuilderFlightMath.launchesPerTick(cap);
        int launched = 0;
        double now = clock;
        for (int i = count - 1; i >= 0; i--) {
            byte p = PHASE[i];
            if (p == BREAK) {
                if (now - LAUNCH[i] >= BuilderFlightMath.BREAK_TICKS) {
                    remove(i);
                }
                continue;
            }
            if (p == PENDING || p == PENDING_BACK) {
                if (END_CHECK[i] >= 0D && now >= END_CHECK[i] && !present(level, i)) {
                    remove(i);
                    continue;
                }
                if (now < LAUNCH[i]) {
                    continue;
                }
                Player caster = casterOf(level, i);
                boolean fly = caster != null && launched < perTick && bodies < cap;
                if (p == PENDING) {
                    if (fly) {
                        Vec3 tip = BuilderFlightClient.wandTip(caster, 1F);
                        launchOut(i, tip);
                        launched++;
                        BuilderFlightClient.onLaunch(level, CASTER[i], tip, clock);
                    } else {
                        PHASE[i] = FLASH;
                        flashedTotal++;
                    }
                } else {
                    if (fly) {
                        launchBack(i, BuilderFlightClient.wandTip(caster, 1F));
                        launched++;
                    } else {
                        BuilderFlightClient.onLand(cell(i), true);
                        flashedTotal++;
                        remove(i);
                    }
                    continue;
                }
                p = PHASE[i];
            }
            if (p == BACK) {
                System.arraycopy(TO, i * 3, PREV_TO, i * 3, 3);
                Player caster = casterOf(level, i);
                if (caster != null) {
                    setVec(TO, i, BuilderFlightClient.wandTip(caster, 1F));
                }
                if (now >= LAND[i]) {
                    BuilderFlightClient.onReturn(CASTER[i], vec(TO, i), clock);
                    remove(i);
                }
                continue;
            }
            boolean present = present(level, i);
            if (present && END_CHECK[i] >= 0D) {
                END_CHECK[i] = -1D;
            }
            switch (BuilderFlightMath.settle(now, LAND[i], present, END_CHECK[i])) {
                case BuilderFlightMath.LAND -> {
                    BlockPos cell = cell(i);
                    if (p != SOUND) {
                        BuilderFlightClient.onLand(cell, p == FLASH);
                    }
                    BuilderFlightClient.landSound(level, cell, level.getBlockState(cell), clock);
                    landedTotal++;
                    remove(i);
                }
                case BuilderFlightMath.SHATTER -> {
                    if (p == HOVER || p == FLY && now >= LAND[i]) {
                        shatter(level, i, vec(TO, i), p == HOVER ? 0.94D : 1D);
                    } else if (p == FLY) {
                        // 收尾时还在半路上的：在它此刻的位置碎掉
                        position(i, now, 1F, LAND[i]);
                        Vec3 at = new Vec3(POINT[0], POINT[1], POINT[2]);
                        shatter(level, i, at, flyScale(i, now, at));
                    } else {
                        remove(i);
                    }
                }
                case BuilderFlightMath.HOVER -> {
                    if (p == FLY) {
                        PHASE[i] = HOVER;
                    }
                }
                default -> {
                }
            }
        }
        peakBodies = Math.max(peakBodies, bodies);
        if (fx != null) {
            fx.box = bounds();
        }
    }

    /**
     * 没等到真方块的飞行体碎掉：画方块本体时原地停住、迸出这种方块的碎块和几颗光点，再在 {@link BuilderFlightMath#BREAK_TICKS} 刻里缩小褪色；
     * 光点凝成（低画质、光影包下不画本体）时只散光点、立刻移除。
     */
    private static void shatter(ClientLevel level, int i, Vec3 at, double scale) {
        shatteredTotal++;
        BlockState state = Block.stateById(STATE[i]);
        if (moteStyle() || state.getRenderShape() == RenderShape.INVISIBLE || scale < 0.05D) {
            BuilderFlightClient.onShatter(at);
            remove(i);
            return;
        }
        BuilderFlightClient.onBreak(level, at, state, cell(i), (float) scale, clock);
        setVec(FROM, i, at);
        setVec(TO, i, at);
        setVec(PREV_TO, i, at);
        PATH_LEN[i] = 0F;
        LAUNCH[i] = clock;
        BREAK_SCALE[i] = (float) scale;
        PHASE[i] = BREAK;
    }

    /** 飞出途中的方块此刻画多大（和 {@link #renderBodies} 里的算法一致：边飞边长大，离镜头近时缩小） */
    private static double flyScale(int i, double now, Vec3 at) {
        double u = BuilderFlightMath.progress(now, LAUNCH[i], LAND[i]);
        double atCell = BuilderFlightMath.pathParam(u);
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double near = BuilderFlightMath.nearCamera(camera.distanceTo(at));
        double settled = atCell * atCell * atCell * atCell;
        return BuilderFlightMath.grow(u) * Math.max(near, settled);
    }

    private static void launchOut(int i, Vec3 tip) {
        setVec(FROM, i, tip);
        TO[i * 3] = CELL_X[i] + 0.5D;
        TO[i * 3 + 1] = CELL_Y[i] + 0.5D;
        TO[i * 3 + 2] = CELL_Z[i] + 0.5D;
        System.arraycopy(TO, i * 3, PREV_TO, i * 3, 3);
        startBody(i, FLY);
    }

    private static void launchBack(int i, Vec3 tip) {
        FROM[i * 3] = CELL_X[i] + 0.5D;
        FROM[i * 3 + 1] = CELL_Y[i] + 0.5D;
        FROM[i * 3 + 2] = CELL_Z[i] + 0.5D;
        setVec(TO, i, tip);
        System.arraycopy(TO, i * 3, PREV_TO, i * 3, 3);
        startBody(i, BACK);
    }

    private static void startBody(int i, byte phase) {
        PHASE[i] = phase;
        bodies++;
        launchedTotal++;
        PATH_LEN[i] = (float) BuilderFlightMath.arcLength(FROM[i * 3], FROM[i * 3 + 1], FROM[i * 3 + 2], TO[i * 3], TO[i * 3 + 1], TO[i * 3 + 2]);
        // 转半圈的轴：水平方向随机、略往上翘，免得一排方块转得一模一样
        RandomSource random = Vfx.random();
        float a = random.nextFloat() * Mth.TWO_PI;
        Vector3f axis = new Vector3f(Mth.cos(a), 0.35F, Mth.sin(a)).normalize();
        AXIS[i * 3] = axis.x;
        AXIS[i * 3 + 1] = axis.y;
        AXIS[i * 3 + 2] = axis.z;
    }

    private static Player casterOf(ClientLevel level, int i) {
        Entity entity = level.getEntity(CASTER[i]);
        return entity instanceof Player player && player.isAlive() ? player : null;
    }

    /** 格子里已经是这种方块（只比方块，不比朝向这类属性：真放时按放置规则定朝向） */
    private static boolean present(ClientLevel level, int i) {
        Block block = Block.stateById(STATE[i]).getBlock();
        BlockState now = level.getBlockState(CURSOR.set(CELL_X[i], CELL_Y[i], CELL_Z[i]));
        if (now.is(block)) {
            return true;
        }
        // 执行时换了一种（混铺里预计的那种用完了）：格子从原来的样子变成了别的实心方块，也按落位算，声音按实际放下的取
        return ORIG[i] >= 0 && Block.getId(now) != ORIG[i] && !now.isAir() && !(now.getBlock() instanceof LiquidBlock);
    }

    private static BlockPos cell(int i) {
        return new BlockPos(CELL_X[i], CELL_Y[i], CELL_Z[i]);
    }

    private static void setVec(double[] array, int i, Vec3 v) {
        array[i * 3] = v.x;
        array[i * 3 + 1] = v.y;
        array[i * 3 + 2] = v.z;
    }

    private static Vec3 vec(double[] array, int i) {
        return new Vec3(array[i * 3], array[i * 3 + 1], array[i * 3 + 2]);
    }

    private static AABB bounds() {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        boolean any = false;
        for (int i = 0; i < count; i++) {
            byte p = PHASE[i];
            if (p != FLY && p != HOVER && p != BACK && p != BREAK) {
                continue;
            }
            any = true;
            for (int k = 0; k < 2; k++) {
                double[] a = k == 0 ? FROM : TO;
                minX = Math.min(minX, a[i * 3]);
                minY = Math.min(minY, a[i * 3 + 1]);
                minZ = Math.min(minZ, a[i * 3 + 2]);
                maxX = Math.max(maxX, a[i * 3]);
                maxY = Math.max(maxY, a[i * 3 + 1] + BuilderFlightMath.apexLift(PATH_LEN[i]));
                maxZ = Math.max(maxZ, a[i * 3 + 2]);
            }
        }
        if (!any) {
            return new AABB(0D, 0D, 0D, 0D, 0D, 0D);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(2D);
    }

    // ------------------------------------------------------------------ 画法

    /** 这一帧用光点凝成（低画质，光影包下关了方块本体，或开发时强制） */
    static boolean moteStyle() {
        if (styleOverride != 0) {
            return styleOverride == 2;
        }
        return Vfx.quality() == VfxClientConfig.Quality.LOW || !bodyAllowed();
    }

    /** 这一帧画不画方块本体 */
    static boolean bodyAllowed() {
        return bodyUnderShaders || !Vfx.safeMode();
    }

    /** 飞行体此刻的位置写进 {@link #POINT}（飞回时终点跟着杖尖走） */
    private static void position(int i, double now, float partial, double landAt) {
        double u = BuilderFlightMath.progress(now, LAUNCH[i], landAt);
        double t = BuilderFlightMath.pathParam(u);
        double tx = Mth.lerp(partial, PREV_TO[i * 3], TO[i * 3]);
        double ty = Mth.lerp(partial, PREV_TO[i * 3 + 1], TO[i * 3 + 1]);
        double tz = Mth.lerp(partial, PREV_TO[i * 3 + 2], TO[i * 3 + 2]);
        BuilderFlightMath.bezier(FROM[i * 3], FROM[i * 3 + 1], FROM[i * 3 + 2], tx, ty, tz, t, POINT, 0);
    }

    @SubscribeEvent
    public static void onBuilderFlightsRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || bodies <= 0 || renderFailed
                || VfxCompat.renderingShadowPass() || !bodyAllowed()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        try {
            renderBodies(mc, level, event);
        } catch (RuntimeException e) {
            // 画方块本体出了错只关掉本体（光效照旧），不让整个游戏崩
            renderFailed = true;
            EssentialCraft5.LOGGER.error("[创世神之杖] 飞行方块渲染出错，本次游戏里不再画飞行方块", e);
        }
    }

    private static void renderBodies(Minecraft mc, ClientLevel level, RenderLevelStageEvent event) {
        float partial = event.getPartialTick();
        double now = clock + partial;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        UsedTypes used = new UsedTypes(buffers);
        Tinted tinted = new Tinted(used);
        boolean mote = moteStyle();
        for (int i = 0; i < count; i++) {
            byte p = PHASE[i];
            if (p != FLY && p != HOVER && p != BACK && p != BREAK) {
                continue;
            }
            BlockState state = Block.stateById(STATE[i]);
            if (state.getRenderShape() == RenderShape.INVISIBLE) {
                continue;
            }
            double x;
            double y;
            double z;
            double scale;
            double spin = 0D;
            float mix = 0F;
            boolean bright = false;
            boolean hoverSpin = false;
            // 离格子有多近（0 = 在杖尖，1 = 在格子里）：越接近格子越不按镜头距离缩小，落在脚边的格子不会在落位那一下突然变大
            double atCell = 1D;
            if (p == BREAK) {
                if (mote) {
                    continue;
                }
                double elapsed = now - LAUNCH[i];
                scale = BREAK_SCALE[i] * BuilderFlightMath.breakScale(elapsed);
                if (scale < 0.02D) {
                    continue;
                }
                // 抖动按刻换方向（同一刻里几帧位置一样，不会糊成一团）
                double jitter = BuilderFlightMath.breakJitter(elapsed);
                int seed = SEED[i] + (int) elapsed * 977;
                x = FROM[i * 3] + jitter * (((seed * 73) & 15) / 7.5D - 1D);
                y = FROM[i * 3 + 1] + jitter * (((seed * 151) & 15) / 7.5D - 1D);
                z = FROM[i * 3 + 2] + jitter * (((seed * 233) & 15) / 7.5D - 1D);
                spin = elapsed * 0.35D;
                mix = BuilderFlightMath.breakTint(elapsed);
                bright = elapsed > BuilderFlightMath.BREAK_HOLD_TICKS;
            } else if (p == HOVER || p == FLY && now >= LAND[i]) {
                x = TO[i * 3];
                y = TO[i * 3 + 1];
                z = TO[i * 3 + 2];
                scale = p == HOVER ? 0.94D : 1D;
                hoverSpin = p == HOVER;
            } else if (p == FLY && mote) {
                double condense = now - (LAND[i] - BuilderFlightMath.CONDENSE_TICKS);
                if (condense < 0D) {
                    continue;
                }
                float k = (float) Mth.clamp(condense / BuilderFlightMath.CONDENSE_TICKS, 0D, 1D);
                x = TO[i * 3];
                y = TO[i * 3 + 1];
                z = TO[i * 3 + 2];
                scale = 0.25D + 0.75D * easeOutBack(k);
                mix = 0.85F * (1F - k);
                bright = true;
            } else if (p == BACK) {
                if (mote) {
                    continue;
                }
                position(i, now, partial, LAND[i]);
                double u = BuilderFlightMath.progress(now, LAUNCH[i], LAND[i]);
                x = POINT[0];
                y = POINT[1];
                z = POINT[2];
                scale = BuilderFlightMath.grow(1D - u);
                atCell = 1D - BuilderFlightMath.pathParam(u);
                spin = BuilderFlightMath.spin(LAND[i] - now, LAND[i] - LAUNCH[i]);
            } else {
                position(i, now, partial, LAND[i]);
                double u = BuilderFlightMath.progress(now, LAUNCH[i], LAND[i]);
                x = POINT[0];
                y = POINT[1];
                z = POINT[2];
                scale = BuilderFlightMath.grow(u);
                atCell = BuilderFlightMath.pathParam(u);
                spin = BuilderFlightMath.spin(now - LAUNCH[i], LAND[i] - LAUNCH[i]);
            }
            if (p == FLY && !mote && now < LAND[i] || p == BACK) {
                // 刚出手、飞回杖尖、从镜头前横穿时离眼睛很近，按距离缩小，不挡画面
                double near = BuilderFlightMath.nearCamera(Math.sqrt(camera.distanceToSqr(x, y, z)));
                double settled = atCell * atCell * atCell * atCell;
                scale *= Math.max(near, settled);
                if (scale < 0.02D) {
                    continue;
                }
            }
            pose.pushPose();
            pose.translate(x - camera.x, y - camera.y, z - camera.z);
            if (hoverSpin) {
                pose.mulPose(Axis.YP.rotation((float) ((now - LAND[i]) * 0.12D)));
            } else if (spin > 1.0E-4D) {
                pose.mulPose(Axis.of(new Vector3f(AXIS[i * 3], AXIS[i * 3 + 1], AXIS[i * 3 + 2])).rotation((float) spin));
            }
            float s = (float) scale;
            pose.scale(s, s, s);
            pose.translate(-0.5D, -0.5D, -0.5D);
            int light = bright ? LightTexture.FULL_BRIGHT : LevelRenderer.getLightColor(level, CURSOR.set(Mth.floor(x), Mth.floor(y), Mth.floor(z)));
            MultiBufferSource target = used;
            if (mix > 0.01F) {
                tinted.set(BuilderFlightClient.TINT.inner(), mix);
                target = tinted;
            }
            mc.getBlockRenderer().renderSingleBlock(state, pose, target, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY,
                    ItemBlockRenderTypes.getMovingBlockRenderType(state));
            pose.popPose();
        }
        used.flush();
    }

    private static float easeOutBack(float t) {
        float c1 = 1.4F;
        float c3 = c1 + 1F;
        float x = t - 1F;
        return 1F + c3 * x * x * x + c1 * x * x;
    }

    // ------------------------------------------------------------------ 拖尾、光点

    /** 拖尾和光点（等离子底座画；光影包下自动走稳妥画法，阴影图那一遍底座本来就不画） */
    private static final class FlightFx implements VfxEffect {
        boolean alive = true;
        AABB box = new AABB(0D, 0D, 0D, 0D, 0D, 0D);

        @Override
        public boolean tick() {
            alive = count > 0 && fx == this;
            return alive;
        }

        @Override
        public AABB bounds() {
            return box;
        }

        @Override
        public void render(VfxDraw draw) {
            float partial = draw.partialTick();
            double now = clock + partial;
            boolean mote = moteStyle();
            boolean trails = Vfx.quality() != VfxClientConfig.Quality.LOW;
            boolean body = bodyAllowed();
            for (int i = 0; i < count; i++) {
                byte p = PHASE[i];
                if (p == FLY || p == BACK) {
                    if (now >= LAND[i]) {
                        continue;
                    }
                    if (mote) {
                        drawMote(draw, i, now, partial, trails, p == FLY);
                    } else if (trails) {
                        drawTrail(draw, i, now, partial);
                    }
                } else if (p == HOVER && !body) {
                    // 光影包下不画本体时，等真方块的那几刻在落点留一团轻轻呼吸的光
                    float breath = 0.5F + 0.5F * Mth.sin((float) (now - LAND[i]) * 0.6F);
                    Vec3 c = draw.towardCamera(vec(TO, i), 0.62D);
                    draw.billboard(c, 0.3F + 0.06F * breath, 0F, VfxMode.DOT, 0, Vfx.argb(BuilderFlightClient.TINT.inner(), 0.35F + 0.2F * breath));
                }
            }
        }

        /** 弧线拖尾：从方块往回约 1.5 格，8 个点，落位前 2 刻收掉 */
        private void drawTrail(VfxDraw draw, int i, double now, float partial) {
            double dur = LAND[i] - LAUNCH[i];
            double u = BuilderFlightMath.progress(now, LAUNCH[i], LAND[i]);
            double head = BuilderFlightMath.pathParam(u);
            double tail = BuilderFlightMath.trailTail(head, PATH_LEN[i], 1.5D);
            if (head - tail < 1.0E-4D) {
                return;
            }
            fillTrail(i, head, tail, partial);
            float fade = (float) Mth.clamp((LAND[i] - now) / BuilderFlightMath.SETTLE_TICKS, 0D, 1D);
            float grow = (float) Mth.clamp((now - LAUNCH[i]) / Math.max(1D, dur * 0.2D), 0D, 1D);
            // 从镜头前横穿的那一段淡下去（按头、中、尾三个点里离镜头最近的算）
            Vec3 eye = draw.camera();
            double nearest = Double.MAX_VALUE;
            for (int k = 0; k < 8; k += 3) {
                nearest = Math.min(nearest, eye.distanceToSqr(TRAIL[k * 3], TRAIL[k * 3 + 1], TRAIL[k * 3 + 2]));
            }
            float near = (float) BuilderFlightMath.nearCamera(Math.sqrt(nearest));
            if (near < 0.02F) {
                return;
            }
            VfxPlasma.trail(draw, TRAIL, 8, 0.14F, 0.95F * fade * grow * near, SEED[i], BuilderFlightClient.TINT);
        }

        /** 光点凝成：飞行中只有光点（白芯 + 薰衣草晕 + 很短的尾巴），落位前 4 刻光点变成一团往里收的光 */
        private void drawMote(VfxDraw draw, int i, double now, float partial, boolean trails, boolean outbound) {
            double travelEnd = outbound ? LAND[i] - BuilderFlightMath.CONDENSE_TICKS : LAND[i];
            if (outbound && now >= travelEnd) {
                float k = (float) Mth.clamp((now - travelEnd) / BuilderFlightMath.CONDENSE_TICKS, 0D, 1D);
                Vec3 c = draw.towardCamera(vec(TO, i), 0.62D);
                draw.billboard(c, 0.55F * (1F - 0.4F * k), 0F, VfxMode.DOT, 0, Vfx.argb(BuilderFlightClient.TINT.inner(), 0.6F * (1F - k)));
                return;
            }
            double u = BuilderFlightMath.progress(now, LAUNCH[i], travelEnd);
            double head = BuilderFlightMath.pathParam(u);
            fillTrail(i, head, BuilderFlightMath.trailTail(head, PATH_LEN[i], 0.6D), partial);
            Vec3 p = new Vec3(TRAIL[0], TRAIL[1], TRAIL[2]);
            // 刚出杖尖时小（离镜头近，不然一团白挡在眼前）
            float size = 0.35F + 0.65F * (float) Mth.clamp((now - LAUNCH[i]) / 3D, 0D, 1D);
            if (trails) {
                VfxPlasma.trail(draw, TRAIL, 8, 0.06F * size, 0.9F, SEED[i], BuilderFlightClient.TINT);
            }
            draw.billboard(p, 0.075F * size, 0F, VfxMode.DOT, 700, Vfx.argb(BuilderFlightClient.TINT.core(), 1F));
            draw.billboard(p, 0.26F * size, 0F, VfxMode.DOT, 0, Vfx.argb(BuilderFlightClient.TINT.inner(), 0.6F));
        }

        /** 沿弧线从 head 到 tail 均匀取 8 个点（第 0 个是头）写进 {@link #TRAIL} */
        private void fillTrail(int i, double head, double tail, float partial) {
            double tx = Mth.lerp(partial, PREV_TO[i * 3], TO[i * 3]);
            double ty = Mth.lerp(partial, PREV_TO[i * 3 + 1], TO[i * 3 + 1]);
            double tz = Mth.lerp(partial, PREV_TO[i * 3 + 2], TO[i * 3 + 2]);
            for (int k = 0; k < 8; k++) {
                double t = head + (tail - head) * k / 7D;
                BuilderFlightMath.bezier(FROM[i * 3], FROM[i * 3 + 1], FROM[i * 3 + 2], tx, ty, tz, t, TRAIL, k * 3);
            }
        }
    }

    // ------------------------------------------------------------------ 缓冲

    /** 记下用到了哪些渲染类型，画完只提交这几种（不动这一阶段别的还没提交的缓冲） */
    private static final class UsedTypes implements MultiBufferSource {
        private final MultiBufferSource.BufferSource inner;
        private final List<RenderType> types = new ArrayList<>(4);

        UsedTypes(MultiBufferSource.BufferSource inner) {
            this.inner = inner;
        }

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            if (!types.contains(type)) {
                types.add(type);
            }
            return inner.getBuffer(type);
        }

        void flush() {
            for (RenderType type : types) {
                inner.endBatch(type);
            }
        }
    }

    /** 方块模型的颜色往 tint 拉 mix（「光点凝成」凝成那几刻用） */
    private static final class Tinted implements MultiBufferSource {
        private final MultiBufferSource inner;
        private int r;
        private int g;
        private int b;
        private float mix;

        Tinted(MultiBufferSource inner) {
            this.inner = inner;
        }

        void set(int rgb, float mix) {
            this.r = rgb >> 16 & 0xFF;
            this.g = rgb >> 8 & 0xFF;
            this.b = rgb & 0xFF;
            this.mix = mix;
        }

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            return new TintedConsumer(inner.getBuffer(type), r, g, b, mix);
        }
    }

    private record TintedConsumer(VertexConsumer inner, int tr, int tg, int tb, float mix) implements VertexConsumer {

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            inner.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            inner.color(Math.round(red + (tr - red) * mix), Math.round(green + (tg - green) * mix), Math.round(blue + (tb - blue) * mix), alpha);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            inner.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            inner.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            inner.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            inner.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            inner.endVertex();
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
            inner.defaultColor(red, green, blue, alpha);
        }

        @Override
        public void unsetDefaultColor() {
            inner.unsetDefaultColor();
        }
    }

    // ------------------------------------------------------------------ 清理

    @SubscribeEvent
    public static void onBuilderFlightsLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            clear();
        }
    }

    @SubscribeEvent
    public static void onBuilderFlightsLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
        renderFailed = false;
    }

    /** 开发统计：在场的、画着方块的、峰值和累计，读完清零峰值和累计（{@code dev/BuilderFieldTestClient} 的 flights 指令） */
    public static String stats() {
        String s = "flights " + count + " bodies " + bodies + " peak " + peakBodies + " cap " + capacity() + " launched " + launchedTotal
                + " flashed " + flashedTotal + " landed " + landedTotal + " shattered " + shatteredTotal + " mote " + moteStyle();
        peakBodies = 0;
        launchedTotal = 0;
        flashedTotal = 0;
        landedTotal = 0;
        shatteredTotal = 0;
        return s;
    }
}
