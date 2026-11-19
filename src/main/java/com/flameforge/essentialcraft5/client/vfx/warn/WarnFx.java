package com.flameforge.essentialcraft5.client.vfx.warn;

import com.flameforge.essentialcraft5.client.vfx.VfxEffect;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import com.flameforge.essentialcraft5.network.S2CBossWarningPacket;
import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxCracks;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 首领预警组件的共同部分（{@link S2CBossWarningPacket} 一个包对应一个）：时间轴、跟随、撤销、淡入淡出。
 * <ul>
 *   <li>时间轴：收包那一刻算第 0 刻，{@link #duration} 刻时落下（服务端结算）；落下后 {@link #linger()} 刻里淡掉，
 *       线 / 柱落下时亮一下（{@link #snap}）；地上的裂纹网多半交给结算接着炸亮（{@link #handOff}），没被接走的白热一下、几刻里退掉。
 *       结算本身的画面（爆炸、火柱）不归预警管。</li>
 *   <li>跟随：包里给了跟随实体时，圈心每帧跟着它的水平位置走（插值），高度留在包里的值；实体不在客户端就停在原地。</li>
 *   <li>撤销：收到 {@link S2CBossWarningPacket#CANCEL}、或者收包时看得见的所属实体后来没了 / 死了，{@value #FADE} 刻里淡掉。</li>
 * </ul>
 * 只在客户端加载。
 */
public abstract class WarnFx implements VfxEffect {

    /** 撤销后淡掉的刻数 */
    protected static final int FADE = 5;
    /** 开头淡入的刻数 */
    protected static final int FADE_IN = 3;

    protected final S2CBossWarningPacket packet;
    protected final Vec3 origin;
    protected final int duration;
    protected final VfxPlasma.Tint tint;
    protected final RandomSource random = RandomSource.create();
    private final int owner;
    private final int follow;
    private final boolean ownerSeen;
    @Nullable
    private Vec3 followNow;
    @Nullable
    private Vec3 followPrev;
    protected int age;
    private int cancelAt = -1;
    /** 本机玩家上一刻在哪一区（-1 不知道 / 不适用，0 安全，1 危险） */
    private int lastZone = -1;
    /** 电弧形状的种子，每两刻换一次 */
    protected int seed;

    protected WarnFx(S2CBossWarningPacket packet, ClientLevel level) {
        this.packet = packet;
        this.origin = new Vec3(packet.x(), packet.y(), packet.z());
        this.duration = Math.max(1, packet.duration());
        this.tint = VfxPlasma.tint(packet.color());
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

    /** 撤销：淡掉（重复调没关系） */
    public void cancel() {
        if (cancelAt < 0) {
            cancelAt = age;
        }
    }

    public boolean cancelled() {
        return cancelAt >= 0;
    }

    /** 落下后还要画几刻（亮一下再淡掉） */
    protected int linger() {
        return 6;
    }

    public boolean finished() {
        return cancelAt >= 0 ? age >= cancelAt + FADE : age >= duration + linger();
    }

    @Override
    public final boolean tick() {
        age++;
        if ((age & 1) == 0) {
            seed = random.nextInt(1 << 20);
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            // 落石、失序之柱这类「落地就移除」的实体，在落下前一两刻就从客户端没了：这时算正常落下（linger 退场），不按撤销拖 FADE 刻
            if (ownerSeen && cancelAt < 0 && age < duration - 2) {
                Entity e = level.getEntity(owner);
                if (e == null || e.isRemoved() || e instanceof LivingEntity living && living.isDeadOrDying()) {
                    cancel();
                }
            }
            Entity f = follow >= 0 ? level.getEntity(follow) : null;
            if (f != null) {
                followPrev = followNow == null ? f.position() : followNow;
                followNow = f.position();
            }
        }
        if (cancelAt < 0 && age < duration) {
            tickWarning(age / (float) duration);
            localCue(level);
        } else if (cancelAt < 0 && age == duration) {
            onResolve();
        }
        return !finished();
    }

    /**
     * 本机提示（只给自己看、自己听）：最后 20 刻里站在危险区，脚边冒危险色的余烬、每 5 刻一下心跳；从危险区踏进安全区响一声。
     * 哪里算危险由子类按包里的范围判（{@link #localZone}），和服务端同一个判法（脚下中心的水平距离），往危险那边多算 0.3 格。
     */
    private void localCue(@Nullable ClientLevel level) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (level == null || player == null || player.level() != level || player.isSpectator()) {
            return;
        }
        int zone = localZone(player.position());
        if (zone == 1 && duration - age <= 20) {
            VfxPlasma.embers(player.position().add(0D, 0.05D, 0D), 0.4D, 1.5F, tint);
            if ((duration - age) % 5 == 0) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, 0.9F, 0.7F));
            }
        } else if (zone == 0 && lastZone == 1) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.4F, 0.8F));
        }
        lastZone = zone;
    }

    /** 本机玩家脚下这点在哪一区：-1 不适用，0 安全，1 危险（子类按自己的范围判） */
    protected int localZone(Vec3 feet) {
        return -1;
    }

    /** 落下时把预警的裂纹网交给结算（圆心 at 附近才交）；没有裂纹网的组件不交 */
    @Nullable
    VfxCracks handOff(Vec3 at) {
        return null;
    }

    /** 所属实体（首领）身上 frac 高度处的点（插值）；实体不在客户端就是 null */
    @Nullable
    protected Vec3 ownerPoint(float partialTick, float frac) {
        ClientLevel level = Minecraft.getInstance().level;
        Entity e = level == null ? null : level.getEntity(owner);
        if (e == null || e.isRemoved()) {
            return null;
        }
        return e.getPosition(partialTick).add(0D, e.getBbHeight() * frac, 0D);
    }

    /**
     * 首领身上的蓄力：一颗越聚越亮的星芒 + 光晕，最后 3 刻（其它标记都压暗时）最亮；electric 的带电晕，
     * 不是电系的收紧段身后加一个慢转的粗光线爆点。
     */
    protected void renderCharge(VfxDraw draw, @Nullable Vec3 at, float k, float left, float t, float a, VfxPlasma.Tint charge, boolean electric,
                                float scale) {
        if (at == null || a <= 0.01F) {
            return;
        }
        float peak = left < 3F ? 1.25F : 1F;
        draw.billboard(at, (0.3F + 0.8F * k) * scale * peak, t * 0.1F, VfxMode.FLARE, 900, Vfx.argb(charge.core(), Math.min(1F, 0.3F + 0.6F * k * peak) * a));
        draw.billboard(at, (0.6F + 1.0F * k) * scale, 0F, VfxMode.DOT, 0, Vfx.argb(charge.glow(), 0.4F * k * peak * a));
        if (electric) {
            VfxPlasma.corona(draw, RandomSource.create(seed + 29L), at, (0.7F + 0.7F * k) * scale, 1 + Math.round(3F * k), 0.035F,
                    (0.5F + 0.5F * k) * a, charge);
        } else if (left < 10F) {
            float pre = Math.min(1F, (10F - left) / 7F);
            draw.billboard(at.x, at.y, at.z, 3F * scale * pre, 3F * scale * pre, t * 0.05F, VfxMode.BURST, 23, Vfx.argb(charge.inner(), 0.6F * pre * a),
                    false);
        }
    }

    /** 预警期每刻（放粒子）；k = 进度 0~1 */
    protected void tickWarning(float k) {
    }

    /** 落下那一刻（只调一次，撤销了就不调） */
    protected void onResolve() {
    }

    /** 帧内时间（刻，含插值） */
    protected float time(float partialTick) {
        return age + partialTick;
    }

    /** 预警进度 0~1 */
    protected float progress(float t) {
        return Mth.clamp(t / duration, 0F, 1F);
    }

    /** 还剩多少刻落下 */
    protected float left(float t) {
        return Math.max(0F, duration - t);
    }

    /** 整体不透明度：开头淡入，撤销 / 落下后淡出 */
    protected float alpha(float t) {
        float a = Mth.clamp(t / FADE_IN, 0F, 1F);
        if (cancelAt >= 0) {
            a *= 1F - Mth.clamp((t - cancelAt) / FADE, 0F, 1F);
        } else if (t > duration) {
            a *= 1F - Mth.clamp((t - duration) / linger(), 0F, 1F);
        }
        return a;
    }

    /** 落下后那一下亮起（0~1，落下那一刻最亮、几刻里退掉）；撤销了就是 0 */
    protected float snap(float t) {
        if (cancelAt >= 0 || t < duration) {
            return 0F;
        }
        return 1F - Mth.clamp((t - duration) / linger(), 0F, 1F);
    }

    /** 当前圈心：跟随实体的水平位置（插值）+ 包里的高度；不跟随时就是包里的位置 */
    protected Vec3 center(float partialTick) {
        if (followNow == null || followPrev == null) {
            return origin;
        }
        return new Vec3(Mth.lerp(partialTick, followPrev.x, followNow.x), origin.y, Mth.lerp(partialTick, followPrev.z, followNow.z));
    }

    /** 跟随实体当前的脚下位置（烈日标记的柱子跟着人上下）；没有就 null */
    @Nullable
    protected Vec3 followFeet(float partialTick) {
        if (followNow == null || followPrev == null) {
            return null;
        }
        return followPrev.lerp(followNow, partialTick);
    }

    /** 包住预警的盒子：圈心周围 reach 格、上下 height 格 */
    protected AABB box(double reach, double height) {
        Vec3 c = followNow == null ? origin : new Vec3(followNow.x, origin.y, followNow.z);
        return new AABB(c.x - reach, c.y - 4D, c.z - reach, c.x + reach, c.y + height, c.z + reach);
    }
}
