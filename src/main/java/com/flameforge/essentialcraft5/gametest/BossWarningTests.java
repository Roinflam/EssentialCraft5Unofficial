package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.entity.boss.AbyssTyrantEntity;
import com.flameforge.essentialcraft5.entity.boss.BossWarnings;
import com.flameforge.essentialcraft5.entity.boss.SentryTurretEntity;
import com.flameforge.essentialcraft5.entity.boss.TownSentryEntity;
import com.flameforge.essentialcraft5.entity.mob.MRUGolemEntity;
import com.flameforge.essentialcraft5.network.ECNetwork;
import com.flameforge.essentialcraft5.network.S2CBossWarningPacket;
import com.flameforge.essentialcraft5.registry.ECMRUGolem;
import com.flameforge.essentialcraft5.registry.ECSunkenDistrict;
import com.flameforge.essentialcraft5.registry.ECTownSentry;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * 首领预警包（特效大改第 1 批，协议 14）：包的编解码和读入时的夹值；聚能魔像、废城守望者蓄力起手时服务端发了什么。
 * 第 2 批的结算画面（同一个包的 {@code RESOLVE} 类型）：编解码，魔像 / 守望者落地、炮台开枪、渊潮领主起漩涡时发了什么。
 * 天灾之心、矩阵之影的在各自的测试类里（要用那边的召唤辅助）：{@code CalamityHeartTests.calamityHeartWarningPackets}、
 * {@code MatrixShadeTests.matrixShadeWarningPackets}。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class BossWarningTests {

    private static final String LARGE = "empty_large";
    private static final String BATCH = "boss_warning";
    private static final double EPS = 1.0E-4D;

    private BossWarningTests() {
    }

    private static S2CBossWarningPacket roundTrip(S2CBossWarningPacket packet) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            S2CBossWarningPacket.encode(packet, buf);
            return S2CBossWarningPacket.decode(buf);
        } finally {
            buf.release();
        }
    }

    /** 编解码原样来回（含「不跟随」的 −1）；读入时尺寸夹到 0~256、扇形半角夹到 π、时长夹到 0~1200 刻、非有限数读成 0；协议号至少 14 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void bossWarningPacketCodec(GameTestHelper helper) {
        helper.assertTrue(Integer.parseInt(ECNetwork.PROTOCOL) >= 14, "特效大改第 1 批加了预警包，协议号至少是 14，实际 " + ECNetwork.PROTOCOL);
        S2CBossWarningPacket ring = new S2CBossWarningPacket(S2CBossWarningPacket.RING, 123, -1, 10.5D, 64D, -3.25D, 0F, 20F, 0.6F, 0.9F, 0F,
                44, 10, 0xFF7A3A, 0, 0);
        helper.assertTrue(roundTrip(ring).equals(ring), "移动的环来回不变，实际 " + roundTrip(ring));
        S2CBossWarningPacket column = new S2CBossWarningPacket(S2CBossWarningPacket.COLUMN, 7, 99, 1D, 2D, 3D, 8F, 1F, 0F, 0F, 0F, 30, 0, 0xFFD27A, 0, 0);
        helper.assertTrue(roundTrip(column).equals(column) && roundTrip(column).follow() == 99, "光柱的跟随实体来回不变");
        S2CBossWarningPacket bad = new S2CBossWarningPacket(S2CBossWarningPacket.CIRCLE, 1, -1, Double.NaN, 0D, 0D, 1.0E9F, Float.NaN,
                Float.POSITIVE_INFINITY, 0F, 0F, -5, 999_999, 0x12345678, -1, 0x1FF);
        S2CBossWarningPacket read = roundTrip(bad);
        helper.assertTrue(read.x() == 0D && read.size() == S2CBossWarningPacket.MAX_SIZE && read.size2() == 0F && read.dirX() == 0F,
                "非有限数读成 0、尺寸夹到上限，实际 " + read);
        helper.assertTrue(read.duration() == 0 && read.delay() == S2CBossWarningPacket.MAX_TICKS && read.color() == 0x345678 && read.color2() == 0xFFFFFF
                && read.flags() == 0xFF, "时长夹到 0~1200、颜色只留 RGB、标志只留 1 字节，实际 " + read);
        S2CBossWarningPacket sector = new S2CBossWarningPacket(S2CBossWarningPacket.CIRCLE, 1, -1, 0D, 0D, 0D, 6F, 10F, 1F, 0F, 0F, 20, 0, 0, 0,
                S2CBossWarningPacket.FLAG_SECTOR);
        helper.assertTrue(Math.abs(roundTrip(sector).size2() - Mth.PI) < EPS, "扇形半角夹到 π");
        S2CBossWarningPacket cancel = roundTrip(S2CBossWarningPacket.cancel(42));
        helper.assertTrue(cancel.kind() == S2CBossWarningPacket.CANCEL && cancel.owner() == 42 && cancel.follow() == -1, "撤销包只带所属实体");
        helper.succeed();
    }

    /** 结算画面包：编号放在标志字节里、来回不变；持续画面（漩涡、连线）认得出来；别的类型的 style() 是 0 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void bossResolvePacketCodec(GameTestHelper helper) {
        S2CBossWarningPacket arc = S2CBossWarningPacket.resolve(S2CBossWarningPacket.FX_ARC, 5, -1, 1D, 2D, 3D, 0F, 0F, 4F, -1F, 2F, 0, 6,
                BossWarnings.MATRIX, 0);
        S2CBossWarningPacket read = roundTrip(arc);
        helper.assertTrue(read.equals(arc) && read.kind() == S2CBossWarningPacket.RESOLVE && read.style() == S2CBossWarningPacket.FX_ARC
                && read.delay() == 6 && !read.persistent(), "电弧画面来回不变、晚 6 刻、一次性，实际 " + read);
        S2CBossWarningPacket link = roundTrip(S2CBossWarningPacket.resolve(S2CBossWarningPacket.FX_LINK, 9, 11, 0D, 0D, 0D, 0F, 0F, 0F, 0F, 0F,
                600, 0, 0x9A4CE0, 0));
        helper.assertTrue(link.persistent() && link.follow() == 11 && link.duration() == 600, "连线是持续画面、带跟随和时长，实际 " + link);
        helper.assertTrue(roundTrip(S2CBossWarningPacket.resolve(S2CBossWarningPacket.FX_VORTEX, 1, 1, 0D, 0D, 0D, 6F, 0F, 0F, 0F, 0F, 100, 0, 0, 0))
                .persistent(), "漩涡是持续画面");
        S2CBossWarningPacket sector = new S2CBossWarningPacket(S2CBossWarningPacket.CIRCLE, 1, -1, 0D, 0D, 0D, 6F, 1F, 1F, 0F, 0F, 20, 0, 0, 0,
                S2CBossWarningPacket.FLAG_SECTOR);
        helper.assertTrue(sector.style() == 0 && !sector.persistent(), "预警包的标志字节不当画面编号");
        helper.succeed();
    }

    /**
     * 第 2 批：魔像砸地（FX_GOLEM_SLAM，半径、MRU 靛紫）、守望者镇压（FX_SUPPRESS）、炮台一枪（FX_TRACER：枪口 → 命中点）、
     * 渊潮领主起漩涡（FX_VORTEX：持续、跟着自己、半径和时长按配置，能撤）；结算画面不混进预警包的记录
     */
    @GameTest(template = LARGE, batch = BATCH)
    public static void bossResolvePackets(GameTestHelper helper) {
        BossWarnings.captureForTest();
        ServerLevel level = helper.getLevel();
        List<Mob> mobs = new java.util.ArrayList<>();
        try {
            MRUGolemEntity golem = ECMRUGolem.MRU_GOLEM.get().create(level);
            TownSentryEntity sentry = ECTownSentry.TOWN_SENTRY.get().create(level);
            SentryTurretEntity turret = ECTownSentry.SENTRY_TURRET.get().create(level);
            AbyssTyrantEntity tyrant = ECSunkenDistrict.ABYSS_TYRANT.get().create(level);
            if (golem == null || sentry == null || turret == null || tyrant == null) {
                throw new IllegalStateException("实体没建出来");
            }
            mobs.add(spawn(helper, golem, new Vec3(3.5D, 1D, 3.5D)));
            mobs.add(spawn(helper, sentry, new Vec3(10.5D, 1D, 10.5D)));
            turret.bind(sentry);
            mobs.add(spawn(helper, turret, new Vec3(10.5D, 1D, 4.5D)));
            mobs.add(spawn(helper, tyrant, new Vec3(4.5D, 2D, 11.5D)));
            golem.applyConfiguredStats();
            sentry.applyConfiguredStats();

            golem.beginSlam();
            golem.slamImpact();
            List<S2CBossWarningPacket> res = BossWarnings.resolvedForTest(golem);
            helper.assertTrue(res.size() == 1 && res.get(0).style() == S2CBossWarningPacket.FX_GOLEM_SLAM
                            && Math.abs(res.get(0).size() - ECMRUGolem.Config.values().attacks().slamRadius()) < EPS && res.get(0).color() == BossWarnings.MRU_BLUE,
                    "魔像砸地：半径 = slam_radius、MRU 靛紫，实际 " + res);
            helper.assertTrue(BossWarnings.sentForTest(golem).size() == 1, "预警包的记录里还是只有起手那一个");

            sentry.beginSuppress();
            sentry.suppressImpact();
            res = BossWarnings.resolvedForTest(sentry);
            helper.assertTrue(res.size() == 1 && res.get(0).style() == S2CBossWarningPacket.FX_SUPPRESS
                    && Math.abs(res.get(0).size() - ECTownSentry.Config.values().attacks().suppressRadius()) < EPS, "守望者镇压，实际 " + res);

            turret.fire(golem);
            res = BossWarnings.resolvedForTest(turret);
            Vec3 muzzle = turret.getEyePosition();
            Vec3 aim = golem.position().add(0D, golem.getBbHeight() * 0.5D, 0D);
            helper.assertTrue(res.size() == 1 && res.get(0).style() == S2CBossWarningPacket.FX_TRACER && Math.abs(res.get(0).x() - muzzle.x) < EPS
                            && Math.abs(res.get(0).x() + res.get(0).dirX() - aim.x) < 1.0E-3D && Math.abs(res.get(0).y() + res.get(0).dirY() - aim.y) < 1.0E-3D
                            && res.get(0).color() == SentryTurretEntity.TRACER_COLOR,
                    "炮台一枪：枪口 → 目标身体中间，实际 " + res);

            tyrant.startVortex();
            res = BossWarnings.resolvedForTest(tyrant);
            ECSunkenDistrict.Config.Values cfg = ECSunkenDistrict.Config.values();
            helper.assertTrue(res.size() == 1 && res.get(0).style() == S2CBossWarningPacket.FX_VORTEX && res.get(0).persistent()
                            && res.get(0).follow() == tyrant.getId() && Math.abs(res.get(0).size() - cfg.vortexRadius()) < EPS
                            && res.get(0).duration() == cfg.vortexTicks(),
                    "漩涡：持续、跟着领主、半径 / 时长按配置，实际 " + res);
            BossWarnings.cancel(tyrant);
            List<S2CBossWarningPacket> sent = BossWarnings.sentForTest(tyrant);
            helper.assertTrue(sent.size() == 1 && sent.get(0).kind() == S2CBossWarningPacket.CANCEL, "漩涡能撤，实际 " + sent);
        } finally {
            for (Mob mob : mobs) {
                BossWarnings.clearForTest(mob);
                mob.discard();
            }
        }
        helper.succeed();
    }

    private static <T extends Mob> T spawn(GameTestHelper helper, T mob, Vec3 relative) {
        Vec3 at = helper.absoluteVec(relative);
        mob.moveTo(at.x, at.y, at.z, 0F, 0F);
        mob.setNoAi(true);
        helper.getLevel().addFreshEntity(mob);
        return mob;
    }

    /** 魔像聚能冲击、守望者镇压起手：各发一个跟着自己的圆形预警（半径、时长按配置，魔像 MRU 靛紫、守望者危险红），预警期不再逐刻发包 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void golemAndSentryWarningPackets(GameTestHelper helper) {
        BossWarnings.captureForTest();
        ServerLevel level = helper.getLevel();
        MRUGolemEntity golem = null;
        TownSentryEntity sentry = null;
        try {
            golem = ECMRUGolem.MRU_GOLEM.get().create(level);
            sentry = ECTownSentry.TOWN_SENTRY.get().create(level);
            if (golem == null || sentry == null) {
                throw new IllegalStateException("魔像 / 守望者没建出来");
            }
            spawn(helper, golem, new Vec3(3.5D, 1D, 3.5D)).applyConfiguredStats();
            spawn(helper, sentry, new Vec3(10.5D, 1D, 10.5D)).applyConfiguredStats();

            ECMRUGolem.Config.Attacks g = ECMRUGolem.Config.values().attacks();
            golem.beginSlam();
            List<S2CBossWarningPacket> sent = BossWarnings.sentForTest(golem);
            helper.assertTrue(sent.size() == 1, "魔像起手只发一个预警包，实际 " + sent);
            S2CBossWarningPacket p = sent.get(0);
            helper.assertTrue(p.kind() == S2CBossWarningPacket.CIRCLE && p.owner() == golem.getId() && p.follow() == golem.getId()
                    && Math.abs(p.size() - g.slamRadius()) < EPS && p.duration() == g.slamWarningTicks() && p.color() == BossWarnings.MRU_BLUE,
                    "魔像：跟着自己的圆，半径 = slam_radius、时长 = slam_warning_ticks、MRU 靛紫，实际 " + p);
            helper.assertTrue(Math.abs(p.x() - golem.getX()) < EPS && Math.abs(p.y() - golem.getY()) < EPS && Math.abs(p.z() - golem.getZ()) < EPS,
                    "圈心在魔像脚下");
            golem.slamImpact();
            helper.assertTrue(BossWarnings.sentForTest(golem).size() == 1, "落地结算不再发预警包");

            ECTownSentry.Config.Attacks s = ECTownSentry.Config.values().attacks();
            sentry.beginSuppress();
            sent = BossWarnings.sentForTest(sentry);
            helper.assertTrue(sent.size() == 1, "守望者起手只发一个预警包，实际 " + sent);
            p = sent.get(0);
            helper.assertTrue(p.kind() == S2CBossWarningPacket.CIRCLE && p.follow() == sentry.getId() && Math.abs(p.size() - s.suppressRadius()) < EPS
                    && p.duration() == s.suppressWarningTicks() && p.color() == BossWarnings.DANGER,
                    "守望者：跟着自己的圆，半径 = suppress_radius、时长 = suppress_warning_ticks、危险红，实际 " + p);
            BossWarnings.cancel(sentry);
            sent = BossWarnings.sentForTest(sentry);
            helper.assertTrue(sent.size() == 2 && sent.get(1).kind() == S2CBossWarningPacket.CANCEL && sent.get(1).owner() == sentry.getId(),
                    "蓄力中撤销发撤销包，实际 " + sent);
            BossWarnings.cancel(sentry);
            helper.assertTrue(BossWarnings.sentForTest(sentry).size() == 2, "已经撤过、没有进行中的预警时不再发");
        } finally {
            if (golem != null) {
                BossWarnings.clearForTest(golem);
                golem.discard();
            }
            if (sentry != null) {
                BossWarnings.clearForTest(sentry);
                sentry.discard();
            }
        }
        helper.succeed();
    }
}
