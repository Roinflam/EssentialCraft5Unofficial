package com.flameforge.essentialcraft5.client.vfx.weapon;

import com.flameforge.essentialcraft5.client.vfx.Vfx;
import com.flameforge.essentialcraft5.client.vfx.VfxDraw;
import com.flameforge.essentialcraft5.client.vfx.VfxEffect;
import com.flameforge.essentialcraft5.client.vfx.VfxEffects;
import com.flameforge.essentialcraft5.client.vfx.VfxMode;
import com.flameforge.essentialcraft5.client.vfx.VfxPalette;
import com.flameforge.essentialcraft5.client.vfx.VfxParticles;
import com.flameforge.essentialcraft5.client.vfx.VfxPaths;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import com.flameforge.essentialcraft5.client.vfx.VfxScreen;
import com.flameforge.essentialcraft5.item.VerdictBladeItem;
import com.flameforge.essentialcraft5.network.S2CVerdictJudgmentPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 裁决之刃「断罪」（照第二版样片 fx/VerdictJudgmentFx 的观感接真触发）：
 * <ul>
 *   <li>蓄力（{@link Charge}）：客户端看玩家是不是主手拿着解锁了断罪的裁决之刃在按右键，自己画，不发包。刀上一团越来越亮的白芯和电晕，
 *       火花从四周往刀上收，后半段范围里冒余辉、脚下地面通电，蓄满后刀上爆点开始转；</li>
 *   <li>松手（{@link Strike}，服务端打成了才发 {@link S2CVerdictJudgmentPacket}）：刀上炸开 + 屏幕短闪，柔波往外推、地面电流，
 *       每个目标在波推到它的那一刻挨一道带分叉的等离子电弧（能量团顺着冲过去），落点爆点、小柔波、热浪。</li>
 * </ul>
 * 地上不画范围的亮边圈（样片里的圈边和烧痕圈去掉了，见 tools/plan/vfx_overhaul.md 第 6 节）。
 */
public final class VerdictClient {

    private static final VfxPlasma.Tint TINT = VfxPlasma.MRU;
    private static final Map<Integer, Charge> CHARGING = new HashMap<>();

    private VerdictClient() {
    }

    /** 每刻看一遍玩家：谁在蓄断罪就给谁一个蓄力特效 */
    static void tickPlayers(ClientLevel level) {
        for (Player player : level.players()) {
            if (!charging(player) || CHARGING.containsKey(player.getId()) || !Vfx.visibleAt(player.position())) {
                continue;
            }
            Charge charge = new Charge(player);
            CHARGING.put(player.getId(), charge);
            VfxEffects.add(charge);
        }
    }

    static void clear() {
        CHARGING.clear();
    }

    /** 演示开关用：不用真按右键，让 player 蓄 ticks 刻后对 targets 劈下去（只在本客户端画，不碰服务端） */
    static void demo(Player player, int ticks, List<LivingEntity> targets, double radius) {
        Charge charge = new Charge(player);
        charge.demoLeft = Math.max(1, ticks);
        charge.demoTargets = targets;
        charge.demoRadius = radius;
        CHARGING.put(player.getId(), charge);
        VfxEffects.add(charge);
    }

    private static boolean charging(Player player) {
        if (!player.isUsingItem() || player.getUsedItemHand() != InteractionHand.MAIN_HAND) {
            return false;
        }
        ItemStack stack = player.getUseItem();
        return stack.getItem() instanceof VerdictBladeItem && VerdictBladeItem.judgmentUnlocked(stack)
                && !(player == Minecraft.getInstance().player && player.getCooldowns().isOnCooldown(stack.getItem()));
    }

    private static int chargeTicks() {
        return Math.max(1, VerdictBladeItem.Config.values().chargeTicks());
    }

    public static void onJudgment(S2CVerdictJudgmentPacket packet) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Entity caster = level.getEntity(packet.caster());
        if (caster == null || !Vfx.visibleAt(caster.position())) {
            return;
        }
        List<LivingEntity> targets = new ArrayList<>();
        for (int id : packet.targets()) {
            if (level.getEntity(id) instanceof LivingEntity living) {
                targets.add(living);
            }
        }
        Charge charge = CHARGING.get(caster.getId());
        if (charge != null) {
            charge.released = true;
        }
        VfxEffects.add(new Strike(caster, targets, Math.max(1D, packet.radius())));
    }

    /** 刀的大概位置：身前偏右、胸口高度（同样片） */
    private static Vec3 hand(Entity caster, float pt) {
        Vec3 pos = caster.getPosition(pt);
        float yaw = caster.getViewYRot(pt) * Mth.DEG_TO_RAD;
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        return pos.add(fx * 0.5D - fz * 0.35D, caster.getBbHeight() * 0.62D, fz * 0.5D + fx * 0.35D);
    }

    private static Vec3 center(Entity entity, float pt) {
        return entity.getPosition(pt).add(0D, entity.getBbHeight() * 0.55D, 0D);
    }

    // ------------------------------------------------------------------ 蓄力

    private static final class Charge implements VfxEffect {

        private final Player player;
        private final RandomSource random = RandomSource.create();
        private final double radius;
        private int seed = random.nextInt(100000);
        private int crawlSeed;
        private float held;
        private float shown;
        boolean released;
        int demoLeft;
        List<LivingEntity> demoTargets;
        double demoRadius;

        Charge(Player player) {
            this.player = player;
            this.radius = VerdictBladeItem.Config.values().radius();
        }

        private float k() {
            return Mth.clamp(held / chargeTicks(), 0F, 1F);
        }

        @Override
        public boolean tick() {
            boolean demo = demoLeft > 0;
            boolean on = !released && !player.isRemoved() && (demo || charging(player));
            if (demo) {
                held++;
                if (--demoLeft == 0) {
                    released = true;
                    player.swing(InteractionHand.MAIN_HAND);
                    VfxEffects.add(new Strike(player, demoTargets, demoRadius));
                }
            } else if (on) {
                held = player.getTicksUsingItem();
            }
            if (on) {
                shown = Math.min(1F, shown + 0.34F);
            } else {
                shown -= released ? 1F : 0.25F;
            }
            if (shown <= 0F) {
                CHARGING.remove(player.getId(), this);
                return false;
            }
            if ((player.tickCount & 1) == 0) {
                seed = random.nextInt(100000);
                crawlSeed = random.nextInt(100000);
            }
            if (on) {
                spawn();
            }
            return true;
        }

        private void spawn() {
            float k = k();
            Vec3 hand = hand(player, 1F);
            Vec3 feet = player.position();
            int n = Vfx.count(2F + 7F * k, hand);
            for (int i = 0; i < n; i++) {
                double a = random.nextDouble() * Mth.TWO_PI;
                double r = 1.6D + random.nextDouble() * 1.8D;
                Vec3 from = hand.add(Math.cos(a) * r, (random.nextDouble() - 0.4D) * 1.8D, Math.sin(a) * r);
                Vec3 v = hand.subtract(from).scale(0.17D);
                VfxParticles.spawn(from.x, from.y, from.z, v.x, v.y, v.z, 7, 0.065F, 0.02F,
                        Vfx.argb(VfxPalette.PINK, 0.95F), Vfx.argb(VfxPalette.WHITE_HOT, 0F), VfxMode.SPARK, 0, 0.98F, 0F, 1.8F, 0F);
            }
            if (k > 0.5F) {
                int e = Vfx.count(2F * k, feet);
                for (int i = 0; i < e; i++) {
                    double a = random.nextDouble() * Mth.TWO_PI;
                    double r = radius * (0.35D + 0.65D * Math.sqrt(random.nextDouble()));
                    VfxParticles.spawn(feet.x + Math.cos(a) * r, feet.y + 0.1D, feet.z + Math.sin(a) * r, 0D, 0.04D + random.nextDouble() * 0.05D, 0D,
                            16 + random.nextInt(12), 0.06F, 0F, Vfx.argb(VfxPalette.WHITE_HOT, 0.9F), Vfx.argb(VfxPalette.MAGENTA, 0F),
                            VfxMode.DOT, 450, 0.97F, -0.001F, 0F, 0F);
                }
            }
        }

        @Override
        public void render(VfxDraw draw) {
            long t0 = WeaponVfxStats.start();
            float pt = draw.partialTick();
            float time = draw.time();
            float k = k();
            float a = Math.max(0F, shown);
            Vec3 hand = hand(player, pt);
            Vec3 feet = player.getPosition(pt);
            boolean full = held >= chargeTicks();
            float flick = 0.8F + 0.2F * Mth.sin(time * 2.7F);
            // 蓄满后亮度不再涨，改成一下一下地鼓
            float beat = full ? 0.85F + 0.15F * Mth.sin(time * 0.9F) : 1F;
            draw.billboard(hand, (0.25F + 0.6F * k) * beat, time * 0.1F, VfxMode.FLARE, 900, Vfx.argb(VfxPalette.WHITE_HOT, (0.5F + 0.5F * k) * flick * a));
            draw.billboard(hand, (0.8F + 1.2F * k) * beat, 0F, VfxMode.DOT, 0, Vfx.argb(VfxPalette.MAGENTA, 0.4F * k * a));
            VfxPlasma.corona(draw, RandomSource.create(seed), hand, 1.0F + 1.1F * k, 1 + Math.round(4F * k), 0.04F, 0.9F * a, TINT);
            if (k > 0.7F) {
                float pre = (k - 0.7F) / 0.3F;
                draw.billboard(hand.x, hand.y, hand.z, 1.4F * pre, 1.4F * pre, time * 0.25F, VfxMode.BURST, 77, Vfx.argb(VfxPalette.PINK, 0.7F * pre * a), false);
            }
            // 脚下：一团柔光，后半段地面通电（没有圈边）
            draw.flat(feet, 1.2F + 1.4F * k, 0F, VfxMode.DOT, 0, Vfx.argb(VfxPalette.VIOLET, 0.3F * k * a));
            if (k > 0.3F) {
                VfxPlasma.groundCrawl(draw, RandomSource.create(crawlSeed), feet, (float) radius * 0.85F, 1 + Math.round(3F * k), 0.03F,
                        (k - 0.3F) / 0.7F * a, TINT);
            }
            WeaponVfxStats.stop(t0);
        }

        @Override
        public AABB bounds() {
            return player.getBoundingBox().inflate(radius + 2D, 3D, radius + 2D);
        }
    }

    // ------------------------------------------------------------------ 松手

    private static final class Strike implements VfxEffect {

        private static final int LIFE = 34;

        private final Entity caster;
        private final List<LivingEntity> targets;
        private final double radius;
        private final float[] strikeAt;
        private final double[][] arcs;
        private final int[] arcSeeds;
        private final RandomSource random = RandomSource.create();
        private final Vec3 feet0;
        private int crawlSeed;
        private int age = -1;

        Strike(Entity caster, List<LivingEntity> targets, double radius) {
            this.caster = caster;
            this.targets = targets;
            this.radius = radius;
            this.feet0 = caster.position();
            this.strikeAt = new float[targets.size()];
            this.arcs = new double[targets.size()][VfxPaths.jaggedPoints(5) * 3];
            this.arcSeeds = new int[targets.size()];
            for (int i = 0; i < targets.size(); i++) {
                double d = targets.get(i).position().distanceTo(caster.position());
                // 柔波推到它的时刻（6 刻推到范围边，先快后慢）
                strikeAt[i] = 1F + (float) (d / radius) * 4F;
                arcSeeds[i] = random.nextInt(100000);
            }
            release();
        }

        private void release() {
            Vec3 hand = hand(caster, 1F);
            Player self = Minecraft.getInstance().player;
            if (self != null && self.distanceTo(caster) < radius + 4D) {
                VfxScreen.flash(VfxPalette.MAGENTA, 0.1F, 8);
            }
            VfxPlasma.sparks(hand, Vec3.ZERO, 0D, 40F, 0.3D, 0.95D, 10, 24, TINT);
            VfxPlasma.embers(caster.position(), radius, 40F, TINT);
        }

        @Override
        public boolean tick() {
            age++;
            if (age % 2 == 0) {
                crawlSeed = random.nextInt(100000);
            }
            for (int i = 0; i < targets.size(); i++) {
                int at = Math.round(strikeAt[i]);
                if (age == at) {
                    hit(i);
                }
                // 刚劈中的几刻每刻换形状（电弧在乱跳），之后隔一刻换
                if (age >= at && (age - at < 6 || (age - at) % 2 == 0)) {
                    arcSeeds[i] = random.nextInt(100000);
                }
            }
            return age < LIFE;
        }

        private void hit(int i) {
            LivingEntity target = targets.get(i);
            Vec3 c = center(target, 1F);
            Vec3 away = c.subtract(caster.position()).multiply(1D, 0D, 1D);
            Vec3 dir = (away.lengthSqr() < 1.0E-4D ? new Vec3(0D, 1D, 0D) : away.normalize()).add(0D, 0.7D, 0D);
            VfxPlasma.sparks(c, dir, 0.9D, 22F, 0.2D, 0.65D, 8, 20, TINT);
            VfxPlasma.embers(target.position(), 0.7D, 8F, TINT);
        }

        @Override
        public void render(VfxDraw draw) {
            long t0 = WeaponVfxStats.start();
            float pt = draw.partialTick();
            float t = Math.max(0F, age + pt);
            Vec3 feet = caster.isRemoved() ? feet0 : caster.getPosition(pt);
            Vec3 hand = hand(caster, pt);
            // 手上的爆发是加色叠的，镜头贴近（第一人称、第三人称近景）时会糊成整屏白，按镜头距离缩小
            float near = Mth.clamp((float) (draw.camera().distanceTo(hand) - 1D) / 5F, 0.45F, 1F);
            VfxPlasma.burst(draw, draw.towardCamera(hand, 0.6D), 2.4F * near, t, 8F, 131, TINT);
            float flash = 1F - Mth.clamp(t / 5F, 0F, 1F);
            if (flash > 0F) {
                draw.billboard(hand, 2.6F * near, 0F, VfxMode.DOT, 0, Vfx.argb(VfxPalette.MAGENTA, 0.3F * flash));
            }
            VfxPlasma.wave(draw, feet, (float) radius * 1.15F, t, 6F, 14F, 1F, TINT);
            VfxPlasma.groundCrawl(draw, RandomSource.create(crawlSeed), feet, (float) radius, 8, 0.035F, 1F - Mth.clamp(t / 12F, 0F, 1F), TINT);
            float scorch = 1F - Mth.clamp((t - 2F) / (LIFE - 2F), 0F, 1F);
            draw.flat(feet, (float) radius * 0.9F, 0F, VfxMode.DOT, 0, Vfx.argb(VfxPalette.MAGENTA, 0.16F * scorch));
            for (int i = 0; i < targets.size(); i++) {
                float ts = t - strikeAt[i];
                if (ts < 0F || ts > 14F) {
                    continue;
                }
                LivingEntity target = targets.get(i);
                Vec3 c = center(target, pt);
                float a = VfxPlasma.strokeAlpha(ts, 6F);
                RandomSource arcRandom = RandomSource.create(arcSeeds[i]);
                int n = VfxPaths.jagged(arcRandom, hand, c, 5, 0.14F, arcs[i]);
                VfxPlasma.forkedArc(draw, arcRandom, arcs[i], n, 0.075F, a, arcSeeds[i], 5, 1.4F, TINT);
                VfxPlasma.pulse(draw, arcs[i], n, ts / 2.5F, 0.22F, 1F, TINT);
                Vec3 base = target.getPosition(pt);
                VfxPlasma.burst(draw, draw.towardCamera(c, 1.1D), 1.6F, ts, 9F, arcSeeds[i] & 1023, TINT);
                VfxPlasma.wave(draw, base, 2.2F, ts, 4F, 9F, 0.8F, TINT);
                float hit = 1F - Mth.clamp(ts / 9F, 0F, 1F);
                VfxPlasma.groundCrawl(draw, RandomSource.create(crawlSeed + i * 7919L), base, 1.6F, 3, 0.022F, hit, TINT);
                draw.distortBillboard(c, 1.5F, 1, 0, 0.5F * hit);
            }
            WeaponVfxStats.stop(t0);
        }

        @Override
        public AABB bounds() {
            return new AABB(feet0, feet0).inflate(radius + 3D, 8D, radius + 3D);
        }
    }
}
