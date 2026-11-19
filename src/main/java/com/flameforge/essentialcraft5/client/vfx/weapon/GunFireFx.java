package com.flameforge.essentialcraft5.client.vfx.weapon;

import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxEffect;
import com.flameforge.essentialcraft5.client.vfx.VfxEffects;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import com.flameforge.essentialcraft5.gun.GunStats;
import com.flameforge.essentialcraft5.gun.GunType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 一名射手的枪械特效：等离子光束、枪口闪光、命中点（爆点 + 火花，打中生物的地方另一个小爆点）。
 * <p>
 * 每名射手只有一个对象，最近的 {@value #SLOTS} 发放在一个环里，满了顶掉最老的：加特林按住扳机每刻一发，
 * 同时画着的光束、命中点也不会超过这个数。连发时火花按射速打折（10 刻内超过 4 发只放三成）。
 */
public final class GunFireFx implements VfxEffect {

    private static final int SLOTS = 8;
    private static final Map<Integer, GunFireFx> ACTIVE = new HashMap<>();

    /** 四种枪各自的样子：光束半宽、寿命、命中爆点大小、火花数 */
    private record Style(float half, int life, float impact, float sparks, float muzzle) {
    }

    private static final Style PISTOL = new Style(0.075F, 9, 0.6F, 9F, 0.16F);
    private static final Style RIFLE = new Style(0.058F, 7, 0.45F, 6F, 0.13F);
    private static final Style SNIPER = new Style(0.11F, 14, 1.0F, 18F, 0.2F);
    private static final Style GATLING = new Style(0.046F, 5, 0.34F, 4F, 0.11F);

    private static final class Shot {
        Vec3 from;
        Vec3 to;
        VfxPlasma.Tint tint;
        Style style;
        float fadeIn;
        float slope;
        float offset;
        boolean block;
        boolean muzzle;
        final Vec3[] hits = new Vec3[3];
        int hitCount;
        int seed;
        int age = -1;
    }

    private final int shooter;
    private final Shot[] shots = new Shot[SLOTS];
    private int next;
    private final int[] recent = new int[16];
    private int clock;
    private long seen;
    private AABB bounds = new AABB(0, 0, 0, 0, 0, 0);

    private GunFireFx(int shooter) {
        this.shooter = shooter;
        for (int i = 0; i < SLOTS; i++) {
            shots[i] = new Shot();
        }
    }

    /**
     * 收到一发：from 已经由调用方让开镜头（自己的从枪口、别人的从眼睛往前半格），eye 是射线判定的起点（算有没有打到方块用）。
     *
     * @param scoped 开着狙击镜的自己：起点一段收细（slope、offset 同原来的光束），不画枪口闪光
     */
    public static void shot(int shooterId, Vec3 eye, Vec3 from, Vec3 to, int balance, float fadeIn, float slope, float offset, boolean scoped) {
        shot(shooterId, eye, from, to, balance, fadeIn, slope, offset, scoped, null);
    }

    /** 同上，type 不为 null 时按这种枪画（演示开关用：假射手手里没有枪） */
    static void shot(int shooterId, Vec3 eye, Vec3 from, Vec3 to, int balance, float fadeIn, float slope, float offset, boolean scoped,
                     GunType type) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !Vfx.visibleAt(to) && !Vfx.visibleAt(from)) {
            return;
        }
        GunFireFx fx = ACTIVE.get(shooterId);
        long now = level.getGameTime();
        // 特效列表满了没加进去、换世界被清掉的，几刻没被推进就当没了，换一个新的
        if (fx == null || now - fx.seen > 3L) {
            fx = new GunFireFx(shooterId);
            fx.seen = now;
            ACTIVE.put(shooterId, fx);
            VfxEffects.add(fx);
        }
        Entity shooter = level.getEntity(shooterId);
        Style style = type == null ? style(shooter) : style(type);
        fx.push(level, shooter, eye, from, to, WeaponTints.lens(balance), style, fadeIn, slope, offset, !scoped);
    }

    private static Style style(Entity shooter) {
        if (shooter instanceof LivingEntity living) {
            Optional<GunType> type = GunType.of(living.getMainHandItem());
            if (type.isEmpty()) {
                type = GunType.of(living.getOffhandItem());
            }
            if (type.isPresent()) {
                return style(type.get());
            }
        }
        return RIFLE;
    }

    private static Style style(GunType type) {
        return switch (type) {
            case PISTOL -> PISTOL;
            case RIFLE -> RIFLE;
            case SNIPER -> SNIPER;
            case GATLING -> GATLING;
        };
    }

    private void push(ClientLevel level, Entity shooterEntity, Vec3 eye, Vec3 from, Vec3 to, VfxPlasma.Tint tint, Style style,
                      float fadeIn, float slope, float offset, boolean muzzle) {
        Shot s = shots[next];
        next = (next + 1) % SLOTS;
        s.from = from;
        s.to = to;
        s.tint = tint;
        s.style = style;
        s.fadeIn = fadeIn;
        s.slope = slope;
        s.offset = offset;
        s.muzzle = muzzle;
        s.age = 0;
        s.seed = Vfx.random().nextInt(100000);
        // 射线判定从眼睛算：没到射程就是被方块截住了
        s.block = eye.distanceTo(to) < GunStats.RAY_RANGE - 0.05D;
        s.hitCount = 0;
        findHits(level, shooterEntity, s);
        recent[clock & 15] = clock;
        int rate = 0;
        for (int t : recent) {
            if (t > 0 && clock - t < 10) {
                rate++;
            }
        }
        // 连发越快每发的火花越少：按住加特林时火花总量不随时间攒起来
        float sparkScale = rate > 4 ? 0.3F : 1F;
        Vec3 dir = to.subtract(from).normalize();
        if (s.block) {
            VfxPlasma.sparks(to, dir.scale(-1D), 1.1D, style.sparks() * sparkScale, 0.12D, 0.45D, 5, 12, tint);
            if (style == SNIPER) {
                VfxPlasma.embers(to, 0.3D, 5F, tint);
            }
        }
        for (int i = 0; i < s.hitCount; i++) {
            VfxPlasma.sparks(s.hits[i], dir, 0.8D, style.sparks() * 0.6F * sparkScale, 0.1D, 0.35D, 4, 9, tint);
        }
        updateBounds();
    }

    /** 光束穿过的生物（服务端判定穿透，客户端不知道打中了谁，按碰撞箱外扩 0.3 格自己找，最多 3 个，只用来摆命中点） */
    private void findHits(ClientLevel level, Entity shooterEntity, Shot s) {
        AABB box = new AABB(s.from, s.to).inflate(0.5D);
        for (Entity e : level.getEntities(shooterEntity, box, e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator())) {
            if (s.hitCount >= s.hits.length) {
                break;
            }
            AABB hitBox = e.getBoundingBox().inflate(0.3D);
            Optional<Vec3> clip = hitBox.clip(s.from, s.to);
            if (clip.isPresent() && !hitBox.contains(s.from)) {
                s.hits[s.hitCount++] = clip.get();
            }
        }
    }

    private void updateBounds() {
        AABB b = null;
        for (Shot s : shots) {
            if (s.age < 0) {
                continue;
            }
            AABB one = new AABB(s.from, s.to);
            b = b == null ? one : b.minmax(one);
        }
        bounds = b == null ? new AABB(0, 0, 0, 0, 0, 0) : b.inflate(1.5D);
    }

    @Override
    public boolean tick() {
        clock++;
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            seen = level.getGameTime();
        }
        boolean any = false;
        for (Shot s : shots) {
            if (s.age >= 0) {
                if (++s.age >= s.style.life()) {
                    s.age = -1;
                } else {
                    any = true;
                }
            }
        }
        if (!any) {
            ACTIVE.remove(shooter, this);
            return false;
        }
        return true;
    }

    @Override
    public void render(VfxDraw draw) {
        long t0 = WeaponVfxStats.start();
        float pt = draw.partialTick();
        for (Shot s : shots) {
            if (s.age >= 0) {
                renderShot(draw, s, s.age + pt);
            }
        }
        WeaponVfxStats.stop(t0);
    }

    private void renderShot(VfxDraw draw, Shot s, float t) {
        Style st = s.style;
        float life = st.life();
        // 第一刻里光从枪口冲到落点、最亮最粗，之后变细淡掉
        float head = Math.min(1F, t / 0.5F);
        float k = 1F - t / life;
        float alpha = (t < 1.5F ? 1F : k * k * 1.3F) * WeaponDraw.nearFade(draw, s.from, s.to);
        float half = st.half() * (t < 1F ? 1.35F - 0.35F * t : 1F - 0.4F * (1F - k));
        WeaponDraw.gunBeam(draw, s.from, s.to, head, half, Math.min(1F, alpha), s.fadeIn, s.slope, s.offset, s.seed, s.tint);
        if (s.muzzle) {
            WeaponDraw.muzzleFlash(draw, s.from, st.muzzle(), t, 3F, s.seed, s.tint);
        }
        if (head < 1F) {
            return;
        }
        float ti = t - 0.5F;
        float impactLife = Math.min(life, 8F);
        if (s.block) {
            Vec3 at = draw.towardCamera(s.to, 0.25D);
            VfxPlasma.burst(draw, at, st.impact(), ti, impactLife, s.seed & 1023, s.tint);
            if (ti < 1F) {
                draw.billboard(at, st.impact() * 0.9F, 0F, VfxMode.FLARE, 1000, Vfx.argb(s.tint.core(), 1F - ti));
            }
        }
        for (int i = 0; i < s.hitCount; i++) {
            VfxPlasma.burst(draw, draw.towardCamera(s.hits[i], 0.5D), st.impact() * 0.8F, ti, impactLife * 0.8F, (s.seed + i * 97) & 1023, s.tint);
        }
    }

    @Override
    public AABB bounds() {
        return bounds;
    }

    /** 现在有几名射手的特效在画（调试用） */
    static int active() {
        return ACTIVE.size();
    }

    static void clear() {
        ACTIVE.clear();
    }
}
