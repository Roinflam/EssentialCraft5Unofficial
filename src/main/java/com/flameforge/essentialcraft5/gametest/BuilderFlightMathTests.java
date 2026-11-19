package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.builder.BuilderFlightMath;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 创世神之杖飞行特效里不碰渲染的计算（{@link BuilderFlightMath}）：容量和每刻起飞上限、落位 / 起飞时刻、弧线、长大和转正、拖尾长度，
 * 以及落位时「方块已在 / 等满 10 刻 / 收尾包后等 2 刻」几种情形的走向。画面本身不在这里测。
 * 每条的检查写在不带参数的静态方法里，GameTest 方法只是转调，不进游戏也能直接调这几个方法核对。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class BuilderFlightMathTests {

    private static final String EMPTY = "empty";
    private static final String BATCH = "ec5_builder_flight_math";
    private static final double EPS = 1.0E-9D;

    private BuilderFlightMathTests() {
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void builderFlightCapacityFollowsQuality(GameTestHelper helper) {
        capacityFollowsQuality();
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void builderFlightLandingTimes(GameTestHelper helper) {
        landingTimes();
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void builderFlightArcPath(GameTestHelper helper) {
        arcPath();
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void builderFlightGrowAndSettle(GameTestHelper helper) {
        growAndSettle();
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void builderFlightLandingStates(GameTestHelper helper) {
        landingStates();
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void builderFlightEndPacketStates(GameTestHelper helper) {
        endPacketStates();
        helper.succeed();
    }

    // ------------------------------------------------------------------ 检查

    /** 配置 0 按画质取 24 / 64 / 128，大于 0 用配置；每刻起飞四分之一、至少一块；64 格外只闪、128 格外不画 */
    public static void capacityFollowsQuality() {
        same(24, BuilderFlightMath.capacity(0, 0), "低画质容量");
        same(64, BuilderFlightMath.capacity(0, 1), "中画质容量");
        same(128, BuilderFlightMath.capacity(0, 2), "高画质容量");
        same(128, BuilderFlightMath.capacity(0, 7), "画质档越界按最高档");
        same(40, BuilderFlightMath.capacity(40, 0), "配置了就用配置");
        same(6, BuilderFlightMath.launchesPerTick(24), "24 块每刻起飞");
        same(16, BuilderFlightMath.launchesPerTick(64), "64 块每刻起飞");
        same(32, BuilderFlightMath.launchesPerTick(128), "128 块每刻起飞");
        same(1, BuilderFlightMath.launchesPerTick(2), "容量很小时每刻至少一块");
        same(BuilderFlightMath.RANGE_FLY, BuilderFlightMath.range(10D * 10D), "近处飞");
        same(BuilderFlightMath.RANGE_FLASH, BuilderFlightMath.range(64D * 64D), "64 格只闪");
        same(BuilderFlightMath.RANGE_FLASH, BuilderFlightMath.range(100D * 100D), "100 格只闪");
        same(BuilderFlightMath.RANGE_NONE, BuilderFlightMath.range(128D * 128D), "128 格不画");
    }

    /** 落位 = 收包 + 提前量 + i / n；起飞在落位前 10 刻、不早于收包；飞回按提前量取 6～14 刻 */
    public static void landingTimes() {
        near(110D, BuilderFlightMath.landAt(100D, 10, 0, 4), "第 0 格落位");
        near(110.75D, BuilderFlightMath.landAt(100D, 10, 3, 4), "第 3 格落位错开 3/4 刻");
        near(110D, BuilderFlightMath.landAt(100D, 10, 0, 0), "空包不除零");
        near(100D, BuilderFlightMath.launchAt(100D, 110D), "提前量 10：收包当刻起飞");
        near(100.75D, BuilderFlightMath.launchAt(100D, 110.75D), "错开的格子起飞也错开");
        near(110D, BuilderFlightMath.launchAt(100D, 120D), "提前量 20：落位前 10 刻才起飞");
        near(100D, BuilderFlightMath.launchAt(100D, 103.5D), "提前量 3：收包当刻起飞，飞得快些");
        near(99D, BuilderFlightMath.launchAt(100D, 100D), "离落位不到一刻：仍按一刻飞");
        near(6D, BuilderFlightMath.backTicks(0), "飞回最少 6 刻");
        near(10D, BuilderFlightMath.backTicks(10), "飞回按提前量");
        near(14D, BuilderFlightMath.backTicks(40), "飞回最多 14 刻");
        near(0.5D, BuilderFlightMath.progress(105D, 100D, 110D), "飞到一半");
        near(0D, BuilderFlightMath.progress(90D, 100D, 110D), "还没起飞");
        near(1D, BuilderFlightMath.progress(120D, 100D, 110D), "已经到了");
        near(1D, BuilderFlightMath.progress(110D, 110D, 110D), "起飞和落位同一刻");
    }

    /** 弧线两端是起点和终点，中点比连线中点高 (0.3 × 距离 + 1) / 2；拖尾按全长折成固定格数 */
    public static void arcPath() {
        double[] p = new double[3];
        BuilderFlightMath.bezier(0D, 64D, 0D, 10D, 64D, 0D, 0D, p, 0);
        near(0D, p[0], "起点 x");
        near(64D, p[1], "起点 y");
        BuilderFlightMath.bezier(0D, 64D, 0D, 10D, 64D, 0D, 1D, p, 0);
        near(10D, p[0], "终点 x");
        near(64D, p[1], "终点 y");
        near(4D, BuilderFlightMath.apexLift(10D), "顶点抬高 0.3 × 10 + 1");
        BuilderFlightMath.bezier(0D, 64D, 0D, 10D, 64D, 0D, 0.5D, p, 0);
        near(5D, p[0], "中点 x");
        near(66D, p[1], "中点 y 抬高 2");
        BuilderFlightMath.bezier(0D, 64D, 0D, 10D, 64D, 0D, 0.4D, p, 0);
        check(p[1] < 66D, "中点是最高点");
        double len = BuilderFlightMath.arcLength(0D, 64D, 0D, 10D, 64D, 0D);
        check(len > 10D && len < 14D, "弧长比直线长、比折线短：" + len);
        near(0.8D, BuilderFlightMath.trailTail(0.9D, 15D, 1.5D), "拖尾 1.5 格折成参数 0.1");
        near(0D, BuilderFlightMath.trailTail(0.05D, 15D, 1.5D), "拖尾不早于起点");
        near(0.3D, BuilderFlightMath.trailTail(0.3D, 0D, 1.5D), "零长度路径不拖尾");
        near(0D, BuilderFlightMath.pathParam(0D), "位置缓动起点");
        near(1D, BuilderFlightMath.pathParam(1D), "位置缓动终点");
        check(BuilderFlightMath.pathParam(0.5D) > 0.8D, "easeOutCubic 前快后慢");
    }

    /** 从 0.4 倍长到 1 倍；起飞时转半圈，落位前 2 刻转正 */
    public static void growAndSettle() {
        near(0.4D, BuilderFlightMath.grow(0D), "起飞 0.4 倍");
        near(1D, BuilderFlightMath.grow(1D), "落位 1 倍");
        double last = 0D;
        for (int i = 0; i <= 10; i++) {
            double g = BuilderFlightMath.grow(i / 10D);
            check(g >= last, "大小只增不减");
            last = g;
        }
        near(Math.PI, BuilderFlightMath.spin(0D, 10D), "起飞转半圈");
        near(Math.PI / 2D, BuilderFlightMath.spin(4D, 10D), "转到一半");
        near(0D, BuilderFlightMath.spin(8D, 10D), "落位前 2 刻转正");
        near(0D, BuilderFlightMath.spin(9.5D, 10D), "最后 2 刻保持正");
        near(0D, BuilderFlightMath.spin(0.5D, 2D), "只飞 2 刻的不转");
    }

    /** 到落位时刻：方块已在 → 落位；没有 → 悬停，等满 10 刻碎掉；悬停中方块到了 → 落位 */
    public static void landingStates() {
        double land = 110D;
        same(BuilderFlightMath.FLYING, BuilderFlightMath.settle(109D, land, false, -1D), "没到落位时刻");
        same(BuilderFlightMath.FLYING, BuilderFlightMath.settle(109D, land, true, -1D), "方块先到了也照常飞完");
        same(BuilderFlightMath.LAND, BuilderFlightMath.settle(110D, land, true, -1D), "到了、方块在：落位");
        same(BuilderFlightMath.HOVER, BuilderFlightMath.settle(110D, land, false, -1D), "到了、方块不在：悬停");
        same(BuilderFlightMath.HOVER, BuilderFlightMath.settle(119D, land, false, -1D), "等了 9 刻还在等");
        same(BuilderFlightMath.LAND, BuilderFlightMath.settle(117D, land, true, -1D), "悬停中方块到了：落位");
        same(BuilderFlightMath.SHATTER, BuilderFlightMath.settle(120D, land, false, -1D), "等满 10 刻：碎掉");
    }

    /** 收尾包：方块已在的照常；不在的等 2 刻，到时有了照常、还没有就碎掉 */
    public static void endPacketStates() {
        near(-1D, BuilderFlightMath.endCheckAt(50D, true), "已有方块不用再判断");
        near(52D, BuilderFlightMath.endCheckAt(50D, false), "没有方块等 2 刻");
        // 还在飞、格子是空的（停下时还没轮到它）：等 2 刻后碎掉，不等到落位时刻
        same(BuilderFlightMath.FLYING, BuilderFlightMath.settle(51D, 60D, false, 52D), "等 2 刻之内照飞");
        same(BuilderFlightMath.SHATTER, BuilderFlightMath.settle(52D, 60D, false, 52D), "2 刻后还没方块：碎掉");
        // 做完时最后一批真放和收尾包同一刻：方块下一刻才同步过来
        same(BuilderFlightMath.HOVER, BuilderFlightMath.settle(51D, 50.5D, false, 52D), "落位时刻到了、同步还没到：先悬停");
        same(BuilderFlightMath.LAND, BuilderFlightMath.settle(52D, 50.5D, true, 52D), "2 刻内同步到了：照常落位");
        same(BuilderFlightMath.FLYING, BuilderFlightMath.settle(52D, 60D, true, 52D), "2 刻内同步到了、还没飞到：接着飞");
    }

    // ------------------------------------------------------------------ 断言

    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new IllegalStateException(what);
        }
    }

    private static void same(int expected, int actual, String what) {
        check(expected == actual, what + "：应为 " + expected + "，实际 " + actual);
    }

    private static void near(double expected, double actual, String what) {
        check(Math.abs(expected - actual) < EPS, what + "：应为 " + expected + "，实际 " + actual);
    }
}
