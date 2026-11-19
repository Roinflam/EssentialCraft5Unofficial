package com.flameforge.essentialcraft5.client.vfx.resolve;

import com.flameforge.essentialcraft5.client.boss.CalamityHeartOverlay;
import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxEffect;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import com.flameforge.essentialcraft5.client.vfx.VfxScreen;
import com.flameforge.essentialcraft5.network.S2CBossWarningPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 首领招式结算画面（{@link S2CBossWarningPacket#RESOLVE}）的共同部分：时间轴、顿帧、按距离震屏，持续画面的跟随和撤销。
 * <ul>
 *   <li>时间轴：收包后先等包里的 delay 刻（连锁电弧一跳一跳地出），之后第 0 刻是「落下」，{@link #life} 刻画完；
 *       子类在 {@link #start()} 里放一次性的粒子，{@link #tickFx} 每刻，{@link #renderFx} 每帧（t = 落下后过了几刻，含帧内插值）。</li>
 *   <li>顿帧：砸下来那一下先停 {@link #hold} 刻——爆点、白热的芯定在最亮的那一帧不动，之后才炸开、往外扩（{@link #after}）。
 *       不放慢动作，整段节奏照样是快的，只是打中那一下「卡」住。</li>
 *   <li>震屏：{@link #shake} 按镜头离落点多远衰减，打在身边最强、{@value #SHAKE_FALLOFF} 格外没有（原版「屏幕扭曲效果」和
 *       {@code [boss_fx] camera_shake} 照旧管它，创造 / 旁观不震）。</li>
 *   <li>持续画面（{@link S2CBossWarningPacket#persistent}）：所属实体在客户端出现过、之后没了 / 死了就 {@value #FADE} 刻里淡掉，
 *       撤销包也一样；一次性画面不受这两者影响，实体当场没了（碎掉的镜影）也照样放完。</li>
 * </ul>
 * 只在客户端加载。
 */
public abstract class ResolveFx implements VfxEffect {

    /** 撤销后淡掉的刻数 */
    protected static final int FADE = 6;
    /** 震屏从「落点周围」到完全不震要再多远（格） */
    protected static final double SHAKE_FALLOFF = 24D;
    /** 冲击帧、速度线在招式范围外再走多少格降到 0：比震屏远——站在场边看别人挨打也该看到「咔」的一下，只是弱一些 */
    protected static final double PUNCH_FALLOFF = 40D;

    protected final S2CBossWarningPacket packet;
    protected final Vec3 origin;
    /** 包里的方向 / 第二个点（含义按画面编号） */
    protected final Vec3 dir;
    protected final VfxPlasma.Tint tint;
    protected final RandomSource random = RandomSource.create();
    protected final int life;
    protected final int hold;
    private final int delay;
    private final int owner;
    private final int follow;
    private final boolean persistent;
    private boolean ownerSeen;
    @Nullable
    private Vec3 followNow;
    @Nullable
    private Vec3 followPrev;
    private int age;
    private boolean started;
    private int cancelAt = -1;
    /** 电弧形状的种子，每两刻换一次 */
    protected int seed;

    /**
     * @param life 落下后画几刻（持续画面按包里的 duration，这里传 0）
     * @param hold 顿帧几刻（0 = 不停）
     */
    protected ResolveFx(S2CBossWarningPacket packet, ClientLevel level, int life, int hold) {
        this.packet = packet;
        this.origin = new Vec3(packet.x(), packet.y(), packet.z());
        this.dir = new Vec3(packet.dirX(), packet.dirY(), packet.dirZ());
        this.tint = VfxPlasma.tint(packet.color());
        this.persistent = packet.persistent();
        this.life = persistent ? Math.max(1, packet.duration()) : Math.max(1, life);
        this.hold = Math.max(0, hold);
        this.delay = packet.delay();
        this.owner = packet.owner();
        this.follow = packet.follow();
        this.ownerSeen = level.getEntity(owner) != null;
        Entity f = follow >= 0 ? level.getEntity(follow) : null;
        if (f != null) {
            followNow = followPrev = f.position();
        }
        this.seed = random.nextInt(1 << 20);
    }

    public int owner() {
        return owner;
    }

    /** 撤销：只对持续画面起作用（一次性的照样放完） */
    public void cancel() {
        if (persistent && cancelAt < 0) {
            cancelAt = age;
        }
    }

    public boolean finished() {
        return cancelAt >= 0 ? age >= cancelAt + FADE : age >= delay + life;
    }

    /**
     * 建好以后马上调（加进特效列表之前）：delay 为 0 时收包这一刻就是落下，{@link #start()} 在这里放——
     * {@link #tick()} 先推进再算第几刻，等不到第 0 刻。delay 大于 0 的照旧在推进到那一刻时放。
     */
    public final ResolveFx begin() {
        if (delay <= 0 && !started) {
            started = true;
            start();
        }
        return this;
    }

    @Override
    public final boolean tick() {
        age++;
        if ((age & 1) == 0) {
            seed = random.nextInt(1 << 20);
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            if (persistent && cancelAt < 0) {
                Entity e = level.getEntity(owner);
                if (e != null && !e.isRemoved() && !(e instanceof LivingEntity living && living.isDeadOrDying())) {
                    ownerSeen = true;
                } else if (ownerSeen || age > 20) {
                    // 看见过、后来没了；或者 1 秒了还没同步到（多半已经没了）
                    cancel();
                }
            }
            Entity f = follow >= 0 ? level.getEntity(follow) : null;
            if (f != null) {
                followPrev = followNow == null ? f.position() : followNow;
                followNow = f.position();
            }
        }
        int t = age - delay;
        if (t == 0 && !started) {
            started = true;
            start();
        }
        if (t == hold && hold > 0) {
            release();
        }
        if (t >= 0 && cancelAt < 0) {
            tickFx(t);
        }
        return !finished();
    }

    @Override
    public final void render(VfxDraw draw) {
        float t = age + draw.partialTick() - delay;
        if (t < 0F) {
            return;
        }
        float a = 1F;
        if (cancelAt >= 0) {
            a = 1F - Mth.clamp((age + draw.partialTick() - cancelAt) / FADE, 0F, 1F);
        }
        if (a > 0.01F) {
            renderFx(draw, t, a);
        }
    }

    /** 落下那一刻（只调一次）：放一次性的粒子、闪屏、震屏 */
    protected void start() {
    }

    /** 顿帧结束、炸开的那一刻（hold 为 0 时不调） */
    protected void release() {
    }

    /** 落下后每刻（t = 第几刻） */
    protected void tickFx(int t) {
    }

    /** 画一帧；t = 落下后过了几刻（含帧内插值），alpha = 撤销时的淡出系数 */
    protected abstract void renderFx(VfxDraw draw, float t, float alpha);

    // ------------------------------------------------------------------ 小工具

    /** 顿帧后的时间：前 {@link #hold} 刻定在 0，之后照常走 */
    protected float after(float t) {
        return Math.max(0F, t - hold);
    }

    /** 还在顿帧里 */
    protected boolean holding(float t) {
        return t < hold;
    }

    /** 跟随实体当前的位置（插值）；没有就是包里的位置 */
    protected Vec3 followPos(float partialTick) {
        if (followNow == null || followPrev == null) {
            return origin;
        }
        return followPrev.lerp(followNow, partialTick);
    }

    /** 跟随的实体（客户端当前看得见的），没有就 null */
    @Nullable
    protected Entity followEntity() {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null || follow < 0 ? null : level.getEntity(follow);
    }

    /** 所属实体（客户端当前看得见的），没有就 null */
    @Nullable
    protected Entity ownerEntity() {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? null : level.getEntity(owner);
    }

    /** 帧内插值后的当前刻（给需要按真实时间转动的东西） */
    protected static float partial(VfxDraw draw) {
        return draw.partialTick();
    }

    /**
     * 震屏：镜头在落点 reach 格内按 strength 震，再往外 {@value #SHAKE_FALLOFF} 格里减到 0。
     *
     * @param ticks 震多少刻（≤ 20）
     */
    public static void shake(Vec3 at, double reach, float strength, int ticks) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double d = Math.sqrt((camera.x - at.x) * (camera.x - at.x) + (camera.z - at.z) * (camera.z - at.z));
        float k = d <= reach ? 1F : (float) Math.max(0D, 1D - (d - reach) / SHAKE_FALLOFF);
        if (k > 0.02F) {
            CalamityHeartOverlay.shake(strength * k, ticks);
        }
    }

    /**
     * 大招落下那一下的打击处理：冲击帧（背景一下子压暗），按镜头离落点的水平距离衰减（reach 内满强度，再往外 {@value #PUNCH_FALLOFF} 格先慢后快地降到 0）。
     * 只给真正的大招落地（四灾归心、灾柱反噬、正午、烈日露天打中、坠身、镇压）；起手、打到自己的小电弧、普通攻击都不压。
     * 护眼限制（间隔、峰值、开关）由 {@link VfxScreen} 管。
     */
    public static void punch(Vec3 at, double reach, float strength) {
        punch(at, reach, strength, 0, 0F);
    }

    /**
     * 同上，再加一圈往落点冲的速度线（强度 = strength × lines，颜色给辉光色，不给几乎是白的芯色）。
     * 速度线最出戏，只留给四灾归心、灾柱反噬两招。
     */
    public static void punch(Vec3 at, double reach, float strength, int rgb, float lines) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double d = Math.sqrt((camera.x - at.x) * (camera.x - at.x) + (camera.z - at.z) * (camera.z - at.z));
        // 先慢后快：场边二三十格外还剩一半多，快到 reach + PUNCH_FALLOFF 才掉光
        double x = d <= reach ? 0D : Math.min(1D, (d - reach) / PUNCH_FALLOFF);
        float k = (float) (1D - x * x);
        if (k > 0.05F) {
            VfxScreen.impact(strength * k);
            if (lines > 0F) {
                VfxScreen.speedLines(at, rgb, strength * lines * k);
            }
        }
    }

    /** 整屏闪一下：只在镜头离得近时（reach 格内）；护眼限制（间隔、峰值）由 {@link VfxScreen#flash} 管 */
    public static void flashNear(Vec3 at, double reach, int rgb, float peak, int ticks) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        if (camera.distanceToSqr(at) <= reach * reach) {
            VfxScreen.flash(rgb, peak, ticks);
        }
    }

    /** 落点离镜头多远（格） */
    protected static double cameraDistance(Vec3 at) {
        return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceTo(at);
    }

    /** 本次画面的结构档（{@link Vfx#tier()}） */
    protected static int tier() {
        return Vfx.tier();
    }
}
