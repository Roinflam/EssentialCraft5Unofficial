package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.hoanna.HoannaEventState;
import com.flameforge.essentialcraft5.hoanna.HoannaVisuals;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * 第六批 6a M3「天空与画面」的测试（batch6a.md §3.6 第 1 条、§5.3）：纯 Forge 专用服上只测 common 的 {@link HoannaVisuals} 和数据
 * （维度类型、群系颜色、毒雾实体的同步参数），客户端渲染类一个都不加载。守的是「看着不难受」的量化版本（§3.1 护眼原则）：
 * 大面积彩度、天体同速、事件画面平滑、镜头晃动幅度与频率、电弧闪烁频率、染色不越界、黑暗时火把有用、晕影不整屏；
 * 6a 审查后加：地平线带盖满下半球、地平线不靠雾距离（M3-1 / M3-2）、毒雾只拉近不推远（M3-4）、提示条接在被取消的 Boss 血条下（M3-5）；
 * 之后又加：提示条收起态在快捷栏左端的左上方（{@link #eventHudCollapsedBesideHotbar}）、失明 / 黑暗时毒雾雾团淡掉（{@link #fumeHazeHiddenWhenBlind}）。
 * 包的编解码、淡入淡出本身在 F6a 的 {@code HoannaFrameworkTests} 测。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class HoannaVisualTests {

    private static final String EMPTY = "empty";
    /** 大面积颜色的彩度上限（D3 A 的表 ≤ 26，留一点余量；选 B 时要放宽到 55） */
    private static final float LARGE_AREA_CHROMA = 30F;
    /** 天体顶点色（小面积）的彩度上限 */
    private static final float BODY_CHROMA = 45F;
    /** 事件画面相邻两个 0.5 tick 的最大变化 */
    private static final float MAX_STEP = 0.02F;

    private HoannaVisualTests() {
    }

    // ------------------------------------------------------------------
    // 1. 维度效果
    // ------------------------------------------------------------------

    /** 维度类型指向 essentialcraft5:hoanna 效果、有天光、无顶、环境光 0；不画云（云高 NaN）；雾色亮度系数正午 1、夜里下限 0.12 / 0.16 */
    @GameTest(template = EMPTY)
    public static void hoannaSkyEffectsAndDimension(GameTestHelper helper) {
        DimensionType type = helper.getLevel().registryAccess().registryOrThrow(Registries.DIMENSION_TYPE).get(ECHoanna.DIMENSION_TYPE);
        helper.assertTrue(type != null, "维度类型 essentialcraft5:hoanna 应已加载");
        helper.assertTrue(ECHoanna.EFFECTS.equals(type.effectsLocation()), "维度效果应是 " + ECHoanna.EFFECTS + "，实际 " + type.effectsLocation());
        helper.assertTrue(type.hasSkyLight() && !type.hasCeiling(), "应有天光、无顶（天空才会画）");
        helper.assertTrue(type.ambientLight() == 0F, "环境光应为 0，实际 " + type.ambientLight());
        helper.assertTrue(Float.isNaN(HoannaVisuals.CLOUD_HEIGHT), "云高应为 NaN（D5 A：不画云）");
        for (int channel = 0; channel < 3; channel++) {
            helper.assertTrue(Math.abs(HoannaVisuals.fogBrightnessFactor(channel, 1F) - 1F) < 1.0E-6F, "正午雾色系数应为 1（通道 " + channel + "）");
        }
        helper.assertTrue(Math.abs(HoannaVisuals.fogBrightnessFactor(0, 0F) - 0.12F) < 1.0E-6F
                && Math.abs(HoannaVisuals.fogBrightnessFactor(2, 0F) - 0.16F) < 1.0E-6F, "夜里雾色系数应为 0.12 / 0.12 / 0.16");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 2. 群系颜色（大面积）
    // ------------------------------------------------------------------

    /** 7 个群系的天、雾、水、水下雾、草、叶 6 项颜色 CIELAB 彩度 ≤ 30（D3 A），而且雾的 L* 高于天的 L*（地平线比天顶亮） */
    @GameTest(template = EMPTY)
    public static void hoannaBiomeColorsCalm(GameTestHelper helper) {
        Registry<Biome> biomes = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        List<String> problems = new ArrayList<>();
        for (ResourceKey<Biome> key : ECHoanna.BIOMES) {
            Biome biome = biomes.get(key);
            if (biome == null) {
                problems.add(key.location() + " 没加载");
                continue;
            }
            BiomeSpecialEffects effects = biome.getSpecialEffects();
            int[] colors = {effects.getSkyColor(), effects.getFogColor(), effects.getWaterColor(), effects.getWaterFogColor(),
                    effects.getGrassColorOverride().orElse(-1), effects.getFoliageColorOverride().orElse(-1)};
            String[] names = {"sky", "fog", "water", "water_fog", "grass", "foliage"};
            for (int i = 0; i < colors.length; i++) {
                if (colors[i] < 0) {
                    problems.add(key.location() + " 没有 " + names[i] + " 颜色");
                    continue;
                }
                float chroma = HoannaVisuals.chroma(colors[i]);
                if (chroma > LARGE_AREA_CHROMA) {
                    problems.add(String.format("%s %s #%06X 彩度 %.1f", key.location(), names[i], colors[i], chroma));
                }
            }
            float skyL = HoannaVisuals.lightness(effects.getSkyColor());
            float fogL = HoannaVisuals.lightness(effects.getFogColor());
            if (fogL <= skyL) {
                problems.add(String.format("%s 雾 L* %.1f 不高于天 L* %.1f", key.location(), fogL, skyL));
            }
        }
        helper.assertTrue(problems.isEmpty(), "群系颜色不合护眼规则：" + problems);
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 3. 天体表
    // ------------------------------------------------------------------

    /** 天体全部和原版太阳同速、半边长 ≤ 30、顶点色彩度 ≤ 45、顶点 alpha 恒为 1；主日主月各一个且不算装饰；月相偏移在 0~7 */
    @GameTest(template = EMPTY)
    public static void celestialBodiesCalm(GameTestHelper helper) {
        List<HoannaVisuals.Body> all = new ArrayList<>(HoannaVisuals.BODIES);
        all.addAll(HoannaVisuals.SUN_ARRAY_BODIES);
        int mainSuns = 0;
        int mainMoons = 0;
        for (HoannaVisuals.Body body : all) {
            helper.assertTrue(body.speed() == 1F, body.id() + " 的速度倍数应为 1（所有天体和原版太阳同速），实际 " + body.speed());
            helper.assertTrue(body.halfSize() > 0F && body.halfSize() <= 30F, body.id() + " 的半边长应在 (0, 30]，实际 " + body.halfSize());
            float chroma = HoannaVisuals.chroma(body.channel(0), body.channel(1), body.channel(2));
            helper.assertTrue(chroma <= BODY_CHROMA, body.id() + " 顶点色彩度 " + chroma + " > " + BODY_CHROMA);
            helper.assertTrue(body.phaseOffset() >= 0 && body.phaseOffset() < 8, body.id() + " 的月相偏移应在 0~7");
            if (HoannaVisuals.BODIES.contains(body) && !body.extra()) {
                if (body.kind() == HoannaVisuals.Kind.SUN) {
                    mainSuns++;
                } else {
                    mainMoons++;
                }
            }
        }
        helper.assertTrue(mainSuns == 1 && mainMoons == 1, "关掉装饰后应正好剩一日一月，实际日 " + mainSuns + "、月 " + mainMoons);
        helper.assertTrue(HoannaVisuals.SUN_ARRAY_BODIES.size() == 6, "日阵应另加 6 个残日");
        helper.assertTrue(HoannaVisuals.BODY_VERTEX_ALPHA == 1F, "天体顶点 alpha 应恒为 1（position_tex_color 的 0.1 丢弃阈值）");
        // 星星、光幕的不透明度不超过 1 / 0.18；雨里星星没了，黑暗时星星、光幕反而更亮
        helper.assertTrue(HoannaVisuals.starAlpha(0.5F, 0F, 1F) <= 1F && HoannaVisuals.starAlpha(0.5F, 1F, 0F) == 0F,
                "星星不透明度：雨满时应为 0、任何时候 ≤ 1");
        helper.assertTrue(HoannaVisuals.starAlpha(0F, 0F, 1F) > HoannaVisuals.starAlpha(0F, 0F, 0F), "黑暗事件时星星应更亮");
        helper.assertTrue(HoannaVisuals.auroraAlpha(0.5F, 0F, 1F) <= 0.18F + 1.0E-6F, "光幕不透明度最多 0.18");
        helper.assertTrue(HoannaVisuals.celestialAlpha(0F, 1F) == 0F && HoannaVisuals.celestialAlpha(0F, 0F) == 1F, "黑暗事件满时日月应淡出");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 4. 事件画面平滑
    // ------------------------------------------------------------------

    /**
     * 四种事件开始、持续、自然结束、中途停止：按 0.5 tick 步长，淡入值和天穹染色后的颜色相邻两步变化都 ≤ 0.02（没有整屏突变）。
     * 天穹颜色取恐惧之地的天 #3B4260（正午）。
     */
    @GameTest(template = EMPTY)
    public static void eventVisualsFadeSmoothly(GameTestHelper helper) {
        float[] out = new float[3];
        float[] prev = new float[3];
        float r = HoannaVisuals.red(0x3B4260);
        float g = HoannaVisuals.green(0x3B4260);
        float b = HoannaVisuals.blue(0x3B4260);
        for (HoannaEvent event : HoannaEvent.rollable()) {
            int total = Math.min(event.baseDuration(), 6_000);
            for (int scenario = 0; scenario < 2; scenario++) {
                HoannaEventState state = new HoannaEventState();
                long now = 10_000L;
                state.accept(event, total, total, now);
                long stopAt = scenario == 1 ? now + total / 2 : Long.MAX_VALUE;
                boolean stopped = false;
                float last = -1F;
                boolean first = true;
                for (double t = now; t <= now + total + 400; t += 0.5D) {
                    if (!stopped && t >= stopAt) {
                        state.accept(HoannaEvent.NONE, 0, 0, stopAt);
                        stopped = true;
                    }
                    float fade = state.fade(event, t);
                    tintFor(event, fade, r, g, b, out);
                    if (!first) {
                        if (Math.abs(fade - last) > MAX_STEP) {
                            helper.fail(event.key() + (scenario == 1 ? "（中途停止）" : "") + " 在 t = " + t + " 淡入值跳了 " + Math.abs(fade - last));
                        }
                        for (int c = 0; c < 3; c++) {
                            if (Math.abs(out[c] - prev[c]) > MAX_STEP) {
                                helper.fail(event.key() + " 在 t = " + t + " 天色跳了 " + Math.abs(out[c] - prev[c]));
                            }
                        }
                    }
                    first = false;
                    last = fade;
                    System.arraycopy(out, 0, prev, 0, 3);
                }
                helper.assertTrue(last == 0F, event.key() + " 结束后淡入值应回到 0，实际 " + last);
            }
        }
        helper.succeed();
    }

    private static void tintFor(HoannaEvent event, float fade, float r, float g, float b, float[] out) {
        HoannaVisuals.tint(r, g, b, event == HoannaEvent.DARKNESS ? fade : 0F, event == HoannaEvent.SUN_ARRAY ? fade : 0F, 1F,
                event == HoannaEvent.EARTHQUAKE ? fade : 0F, event == HoannaEvent.FUMES ? fade : 0F, out);
    }

    // ------------------------------------------------------------------
    // 5. 镜头晃动
    // ------------------------------------------------------------------

    /**
     * 0~120 秒、每 1/240 秒采样（地震满强度、约每 6.7 秒一次随机强度的强震）：|roll| ≤ 1.5°、|pitch| ≤ 1.0°、过零计数主频 ≤ 2.2 Hz、
     * 相邻两次采样变化 ≤ 0.1°（不跳）；「屏幕扭曲效果」为 0、camera_shake 为 0、创造 / 旁观时恒为 0；屏幕扭曲效果 50% 时幅度减半。
     */
    @GameTest(template = EMPTY)
    public static void cameraShakeGentle(GameTestHelper helper) {
        HoannaVisuals.JoltTrack jolts = new HoannaVisuals.JoltTrack();
        RandomSource random = RandomSource.create(0x5EEDL * 0x9E3779B97F4A7C15L);
        float[] out = new float[2];
        float[] half = new float[2];
        float[] none = new float[2];
        double nextJolt = 1.3D;
        int rollCrossings = 0;
        int pitchCrossings = 0;
        float lastRoll = 0F;
        float lastPitch = 0F;
        boolean first = true;
        double seconds = 120D;
        for (int i = 0; i <= seconds * 240; i++) {
            double t = i / 240D;
            if (t >= nextJolt) {
                jolts.add(t, 0.5F + random.nextFloat() * 0.5F);
                nextJolt += 3D + random.nextDouble() * 7.4D;
            }
            HoannaVisuals.shake(t, 1F, jolts, 1F, 1F, false, out);
            if (Math.abs(out[0]) > HoannaVisuals.MAX_ROLL + 1.0E-4F || Math.abs(out[1]) > HoannaVisuals.MAX_PITCH + 1.0E-4F) {
                helper.fail("晃动超过上限（roll ≤ 1.5°、pitch ≤ 1.0°）：" + out[0] + " / " + out[1] + " @ " + t);
            }
            if (!first) {
                if (Math.abs(out[0] - lastRoll) > 0.1F || Math.abs(out[1] - lastPitch) > 0.1F) {
                    helper.fail("晃动在 " + t + " 秒跳变");
                }
                if (Math.signum(out[0]) != Math.signum(lastRoll) && lastRoll != 0F) {
                    rollCrossings++;
                }
                if (Math.signum(out[1]) != Math.signum(lastPitch) && lastPitch != 0F) {
                    pitchCrossings++;
                }
            }
            first = false;
            lastRoll = out[0];
            lastPitch = out[1];
            HoannaVisuals.shake(t, 1F, jolts, 0.5F, 1F, false, half);
            if (Math.abs(half[0]) > HoannaVisuals.MAX_ROLL / 2F + 1.0E-4F) {
                helper.fail("屏幕扭曲效果 50% 时 roll 应减半：" + half[0]);
            }
            HoannaVisuals.shake(t, 1F, jolts, 0F, 1F, false, none);
            if (none[0] != 0F || none[1] != 0F) {
                helper.fail("屏幕扭曲效果为 0 时不应晃");
            }
            HoannaVisuals.shake(t, 1F, jolts, 1F, 0F, false, none);
            if (none[0] != 0F || none[1] != 0F) {
                helper.fail("camera_shake 为 0 时不应晃");
            }
            HoannaVisuals.shake(t, 1F, jolts, 1F, 1F, true, none);
            if (none[0] != 0F || none[1] != 0F) {
                helper.fail("创造 / 旁观不应晃");
            }
        }
        float rollHz = rollCrossings / (2F * (float) seconds);
        float pitchHz = pitchCrossings / (2F * (float) seconds);
        helper.assertTrue(rollHz <= 2.2F && pitchHz <= 2.2F, "晃动主频应 ≤ 2.2 Hz，实际 roll " + rollHz + "、pitch " + pitchHz);
        helper.assertTrue(rollHz > 0.3F, "地震时应该真的在晃（roll 主频 " + rollHz + "）");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 6. 远空电弧的闪烁频率
    // ------------------------------------------------------------------

    /**
     * 1 万个种子各跑 2 小时游戏时间（普通 / 隐藏天空闪烁效果各一遍）：相邻两道 ≥ 240 tick（隐藏闪烁 ≥ 480）→ 任意 20 tick 窗口至多 1 道、
     * 每秒远低于 3 次；白天且不在黑暗事件里一道都不出；黑暗事件里白天也能出；中途打开隐藏闪烁时不到 480 tick 不出。
     */
    @GameTest(template = EMPTY)
    public static void skyArcFlashSafe(GameTestHelper helper) {
        DimensionType type = helper.getLevel().registryAccess().registryOrThrow(Registries.DIMENSION_TYPE).get(ECHoanna.DIMENSION_TYPE);
        helper.assertTrue(type != null, "维度类型 essentialcraft5:hoanna 应已加载");
        // 一天里哪些刻允许出电弧（夜晚系数 > 0.5），以及从某一刻起下一个允许的刻
        boolean[] allowed = new boolean[24_000];
        int allowedTicks = 0;
        for (int t = 0; t < 24_000; t++) {
            allowed[t] = HoannaVisuals.arcAllowed(HoannaVisuals.night(type.timeOfDay(t)), 0F);
            allowedTicks += allowed[t] ? 1 : 0;
        }
        helper.assertTrue(allowedTicks > 8_000 && allowedTicks < 16_000, "一天里能出电弧的时间应约半天，实际 " + allowedTicks + " tick");
        int[] nextAllowed = new int[24_000];
        int next = -1;
        for (int pass = 0; pass < 2; pass++) {
            for (int t = 23_999; t >= 0; t--) {
                if (allowed[t]) {
                    next = t;
                }
                nextAllowed[t] = next < 0 ? -1 : (next >= t ? next - t : next + 24_000 - t);
            }
        }
        long end = 2L * 60 * 60 * 20;
        int fires = 0;
        for (int mode = 0; mode < 2; mode++) {
            boolean reduced = mode == 1;
            long minGap = HoannaVisuals.ArcLimiter.MIN_INTERVAL * (reduced ? 2L : 1L);
            for (int seed = 0; seed < 10_000; seed++) {
                RandomSource random = RandomSource.create(seed * 0x9E3779B97F4A7C15L);
                HoannaVisuals.ArcLimiter limiter = new HoannaVisuals.ArcLimiter();
                long start = random.nextInt(24_000);
                long t = start;
                long last = Long.MIN_VALUE / 4;
                while (t < start + end) {
                    t = Math.max(t, limiter.readyAt());
                    int dayTick = (int) (t % 24_000);
                    if (!allowed[dayTick]) {
                        if (limiter.tryFire(t, HoannaVisuals.night(type.timeOfDay(t)), 0F, reduced, random)) {
                            helper.fail("白天且不在黑暗事件里不应出电弧（种子 " + seed + "，刻 " + t + "）");
                        }
                        t += nextAllowed[dayTick];
                        continue;
                    }
                    if (!limiter.tryFire(t, HoannaVisuals.night(type.timeOfDay(t)), 0F, reduced, random)) {
                        helper.fail("到了允许的时刻应该能出电弧（种子 " + seed + "，刻 " + t + "）");
                    }
                    if (t - last < minGap) {
                        helper.fail("相邻两道电弧只隔了 " + (t - last) + " tick（种子 " + seed + "）");
                    }
                    if (limiter.tryFire(t + 19, 1F, 0F, reduced, random)) {
                        helper.fail("同一个 20 tick 窗口里出了第二道（种子 " + seed + "）");
                    }
                    last = t;
                    fires++;
                    t++;
                }
            }
        }
        helper.assertTrue(fires > 0, "应该出过电弧");
        // 黑暗事件：白天也能出
        HoannaVisuals.ArcLimiter limiter = new HoannaVisuals.ArcLimiter();
        RandomSource random = RandomSource.create(42L * 0x9E3779B97F4A7C15L);
        helper.assertTrue(limiter.tryFire(6_000L, 0F, 1F, false, random), "黑暗事件里白天也应能出电弧");
        // 中途打开隐藏闪烁：上一道之后不到 480 tick 不出
        long ready = limiter.readyAt();
        helper.assertFalse(limiter.tryFire(Math.max(ready, 6_000L + 300L), 1F, 0F, true, random), "中途打开隐藏闪烁时，离上一道不到 480 tick 不应出");
        helper.assertTrue(limiter.tryFire(Math.max(ready, 6_000L + 480L), 1F, 0F, true, random), "离上一道 480 tick 以后应能出");
        // 电弧亮度：峰值 0.35、隐藏闪烁 0.12，寿命 12 tick
        helper.assertTrue(Math.abs(HoannaVisuals.arcAlpha(2.5F, false) - 0.35F) < 1.0E-5F && Math.abs(HoannaVisuals.arcAlpha(2.5F, true) - 0.12F) < 1.0E-5F,
                "电弧峰值应为 0.35 / 0.12");
        helper.assertTrue(HoannaVisuals.arcAlpha(12F, false) == 0F && HoannaVisuals.arcAlpha(-0.1F, false) == 0F, "电弧寿命应为 12 tick");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 7. 染色
    // ------------------------------------------------------------------

    /**
     * 7 个群系的天和雾 × 四种事件（单独、全部叠加）× 淡入 0~1 × 白天系数：输出分量都在 [0, 1]、彩度 ≤ 30；
     * 只有黑暗、f = 1 时亮度 = 输入 × 0.25（±1%）；夜空下限把天穹托在 #0E1018 以上。
     */
    @GameTest(template = EMPTY)
    public static void tintStaysCalm(GameTestHelper helper) {
        Registry<Biome> biomes = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        float[] out = new float[3];
        float[] levels = {0F, 0.25F, 0.5F, 0.75F, 1F};
        float[] daylights = {0F, 0.5F, 1F};
        List<String> problems = new ArrayList<>();
        for (ResourceKey<Biome> key : ECHoanna.BIOMES) {
            Biome biome = biomes.get(key);
            if (biome == null) {
                continue;
            }
            for (int color : new int[]{biome.getSpecialEffects().getSkyColor(), biome.getSpecialEffects().getFogColor()}) {
                float r = HoannaVisuals.red(color);
                float g = HoannaVisuals.green(color);
                float b = HoannaVisuals.blue(color);
                for (float d : levels) {
                    for (float s : levels) {
                        for (float q : levels) {
                            for (float f : levels) {
                                for (float day : daylights) {
                                    HoannaVisuals.tint(r, g, b, d, s, day, q, f, out);
                                    for (float v : out) {
                                        if (v < 0F || v > 1F) {
                                            problems.add(key.location() + " 染色越界 " + v);
                                        }
                                    }
                                    float chroma = HoannaVisuals.chroma(out[0], out[1], out[2]);
                                    if (chroma > LARGE_AREA_CHROMA) {
                                        problems.add(String.format("%s #%06X d%.2f s%.2f q%.2f f%.2f 彩度 %.1f", key.location(), color, d, s, q, f, chroma));
                                    }
                                }
                            }
                        }
                    }
                }
                HoannaVisuals.tint(r, g, b, 1F, 0F, 1F, 0F, 0F, out);
                helper.assertTrue(Math.abs(out[0] - r * 0.25F) <= r * 0.01F + 1.0E-6F && Math.abs(out[1] - g * 0.25F) <= g * 0.01F + 1.0E-6F
                        && Math.abs(out[2] - b * 0.25F) <= b * 0.01F + 1.0E-6F, "黑暗 f = 1 时应为输入 × 0.25：" + key.location());
                HoannaVisuals.tint(r, g, b, 0F, 0F, 1F, 0F, 0F, out);
                helper.assertTrue(out[0] == r && out[1] == g && out[2] == b, "没有事件时不应改颜色：" + key.location());
            }
        }
        helper.assertTrue(problems.isEmpty(), "染色不合护眼规则（前 5 条）：" + problems.subList(0, Math.min(5, problems.size())));
        float[] floor = {0F, 0F, 0F};
        HoannaVisuals.applyNightFloor(floor);
        helper.assertTrue(floor[0] == HoannaVisuals.red(HoannaVisuals.NIGHT_SKY_FLOOR) && floor[2] == HoannaVisuals.blue(HoannaVisuals.NIGHT_SKY_FLOOR),
                "夜空下限应把全黑托到 #0E1018");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 8. 黑暗事件的光照图
    // ------------------------------------------------------------------

    /** 黑暗 f = 1 时方块光 15 的系数 0.88、0 的系数 0.22，f = 0 时恒为 1；对方块光、对 f 都单调 */
    @GameTest(template = EMPTY)
    public static void darknessLightmapKeepsTorches(GameTestHelper helper) {
        helper.assertTrue(Math.abs(HoannaVisuals.lightmapFactor(1F, 15) - 0.88F) < 1.0E-5F, "黑暗时火把旁（方块光 15）应保留 0.88");
        helper.assertTrue(Math.abs(HoannaVisuals.lightmapFactor(1F, 0) - 0.22F) < 1.0E-5F, "黑暗时没有方块光应为 0.22");
        for (int light = 0; light <= 15; light++) {
            helper.assertTrue(HoannaVisuals.lightmapFactor(0F, light) == 1F, "没有黑暗时光照图不应改");
            if (light > 0) {
                helper.assertTrue(HoannaVisuals.lightmapFactor(1F, light) >= HoannaVisuals.lightmapFactor(1F, light - 1), "方块光越亮系数应越大");
            }
            for (float f = 0.1F; f <= 1.0001F; f += 0.1F) {
                helper.assertTrue(HoannaVisuals.lightmapFactor(f, light) <= HoannaVisuals.lightmapFactor(f - 0.1F, light) + 1.0E-6F,
                        "黑暗越深系数应越小");
            }
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 9. 毒雾雾团
    // ------------------------------------------------------------------

    /**
     * 毒雾实体（M4 注册，按注册名取；没注册时跳过这部分）：{@code updateInterval ≥ 5}、{@code clientTrackingRange == 4}。
     * 雾团：离镜头 1 格内为 0（镜头埋不进雾团）、56 格外为 0、最浓 ≤ 0.30、夜里仍看得见；附近强度拉近地形雾时随 p 连续变化。
     */
    @GameTest(template = EMPTY)
    public static void fumeHazeSafe(GameTestHelper helper) {
        ResourceLocation id = EssentialCraft5.id("poison_fume");
        if (ForgeRegistries.ENTITY_TYPES.containsKey(id)) {
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
            helper.assertTrue(type.updateInterval() >= 5, "poison_fume 的 updateInterval 应 ≥ 5，实际 " + type.updateInterval());
            helper.assertTrue(type.clientTrackingRange() == 4, "poison_fume 的 clientTrackingRange 应为 4，实际 " + type.clientTrackingRange());
        }
        float max = 0F;
        for (float d = 0F; d <= 60F; d += 0.05F) {
            for (float light = 0F; light <= 1F; light += 0.25F) {
                float a = HoannaVisuals.fumeHazeAlpha(light, d);
                max = Math.max(max, a);
                if (d <= HoannaVisuals.FUME_NEAR_START || d >= HoannaVisuals.FUME_FAR_END) {
                    helper.assertTrue(a == 0F, "雾团在 " + d + " 格处应完全透明，实际 " + a);
                }
            }
        }
        helper.assertTrue(max <= HoannaVisuals.FUME_HAZE_MAX_ALPHA + 1.0E-6F, "雾团最浓 " + max + " > 0.30");
        helper.assertTrue(HoannaVisuals.fumeHazeAlpha(0F, 10F) > 0.05F, "夜里（光照 0）10 格外的雾团应仍看得见");
        helper.assertTrue(HoannaVisuals.fumeNearbyWeight(0F) == 1F && HoannaVisuals.fumeNearbyWeight(10F) == 0F, "附近强度：贴脸 1、10 格外 0");
        float end = 192F;
        float start = 172.8F;
        float lastEnd = end;
        float lastStart = start;
        for (float p = 0F; p <= 1.0001F; p += 0.01F) {
            float e = HoannaVisuals.fumeFogEnd(p, end);
            float s = HoannaVisuals.fumeFogStart(p, start, e);
            helper.assertTrue(Math.abs(e - lastEnd) <= 2F && Math.abs(s - lastStart) <= 2F, "地形雾拉近应随 p 连续变化（p = " + p + "）");
            helper.assertTrue(s <= e, "雾起点不应超过终点");
            lastEnd = e;
            lastStart = s;
        }
        helper.assertTrue(Math.abs(HoannaVisuals.fumeFogEnd(1F, 192F) - 67.2F) < 0.01F && HoannaVisuals.fumeFogEnd(1F, 40F) == 24F,
                "p = 1 时终点 = max(24, 原终点 × 0.35)");
        // 6a 审查 M3-4：只拉近、不推远（失明 5 格、黑暗 15 格、别的模组的近雾不能被推到 24 格）
        for (float far : new float[]{2F, 5F, 15F, 23.9F, 24F, 40F, 192F, 640F}) {
            for (float p = 0F; p <= 1.0001F; p += 0.05F) {
                float e = HoannaVisuals.fumeFogEnd(p, far);
                helper.assertTrue(e <= far + 1.0E-4F, "毒雾拉近不应把雾终点推远：原 " + far + "、p = " + p + " → " + e);
                float nearStart = far * 0.9F;
                helper.assertTrue(HoannaVisuals.fumeFogStart(p, nearStart, e) <= nearStart + 1.0E-4F, "毒雾拉近不应把雾起点推远：原 " + nearStart);
            }
        }
        helper.assertTrue(HoannaVisuals.fumeFogEnd(1F, 5F) == 5F && HoannaVisuals.fumeFogEnd(1F, 15F) == 15F, "失明 5 格、黑暗 15 格的雾终点应原样保留");
        helper.succeed();
    }

    /**
     * 失明 / 黑暗时雾团跟着淡掉（雾团用 position_color 画、不吃雾，不压的话一片黑雾里还亮着）：
     * 强度照原版雾——失明满 20 tick 以上为 1、最后 1 秒按剩余 tick / 20 退回、无限时长恒为 1；黑暗跟效果自带的淡入系数（约 1 秒升到 1）；
     * 别的效果不算；雾团剩下的比例 = 1 − 强度。用一只真的猪挂效果（黑暗的淡入系数要实体自己 tick 才涨）。
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void fumeHazeHiddenWhenBlind(GameTestHelper helper) {
        helper.assertTrue(HoannaVisuals.blindnessStrength(200, false) == 1F && HoannaVisuals.blindnessStrength(20, false) == 1F
                && HoannaVisuals.blindnessStrength(10, false) == 0.5F && HoannaVisuals.blindnessStrength(0, false) == 0F
                && HoannaVisuals.blindnessStrength(3, true) == 1F, "失明强度应为 min(1, 剩余 / 20)，无限时长为 1");
        helper.assertTrue(HoannaVisuals.fumeHazeVisibility(0F) == 1F && HoannaVisuals.fumeHazeVisibility(1F) == 0F
                && HoannaVisuals.fumeHazeVisibility(0.25F) == 0.75F && HoannaVisuals.fumeHazeVisibility(2F) == 0F
                && HoannaVisuals.fumeHazeVisibility(-1F) == 1F, "雾团剩下的比例应为 1 − 遮挡强度，夹在 [0, 1]");
        helper.assertTrue(HoannaVisuals.visionBlocked(null, 0F) == 0F, "没有镜头实体时不遮");
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(1, 2, 1));
        pig.setNoGravity(true);
        helper.assertTrue(HoannaVisuals.visionBlocked(pig, 0F) == 0F, "没有效果时不遮");
        pig.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200));
        pig.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 200));
        helper.assertTrue(HoannaVisuals.visionBlocked(pig, 0F) == 0F, "缓慢、夜视不算遮挡");
        pig.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 200));
        helper.assertTrue(HoannaVisuals.visionBlocked(pig, 0F) == 1F, "失明 200 tick：完全遮住，雾团不画");
        pig.removeEffect(MobEffects.BLINDNESS);
        pig.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 10));
        float fading = HoannaVisuals.visionBlocked(pig, 0F);
        helper.assertTrue(Math.abs(fading - 0.5F) < 1.0E-4F, "失明只剩 10 tick：遮挡 0.5（雾正在退回去），实际 " + fading);
        pig.removeEffect(MobEffects.BLINDNESS);
        pig.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 400));
        float justAdded = HoannaVisuals.visionBlocked(pig, 0F);
        helper.assertTrue(justAdded < 0.2F, "黑暗刚挂上时淡入系数还没涨，实际 " + justAdded);
        helper.runAfterDelay(40, () -> {
            float full = HoannaVisuals.visionBlocked(pig, 1F);
            helper.assertTrue(full > 0.99F, "黑暗挂了 40 tick：淡入系数到 1、雾团不画，实际 " + full);
            pig.discard();
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------
    // 10. 辐射晕影、提示条时间
    // ------------------------------------------------------------------

    /** 辐射晕影：1 000 以下为 0、单调不减、1 万约 0.10、3 万起封顶 0.20；提示条的剩余时间按 mm:ss、向上取整到秒 */
    @GameTest(template = EMPTY)
    public static void radiationVignetteGentle(GameTestHelper helper) {
        helper.assertTrue(HoannaVisuals.vignetteAlpha(0) == 0F && HoannaVisuals.vignetteAlpha(999) == 0F, "辐射 1 000 以下不显示晕影");
        float last = 0F;
        for (int r = 0; r <= 1_000_000; r += 250) {
            float a = HoannaVisuals.vignetteAlpha(r);
            helper.assertTrue(a >= last, "晕影应随辐射单调不减（" + r + "）");
            helper.assertTrue(a <= HoannaVisuals.RADIATION_VIGNETTE_MAX + 1.0E-6F, "晕影最浓 0.20");
            last = a;
        }
        float tenK = HoannaVisuals.vignetteAlpha(10_000);
        helper.assertTrue(tenK >= 0.09F && tenK <= 0.11F, "辐射 1 万时晕影约 0.10，实际 " + tenK);
        helper.assertTrue(Math.abs(HoannaVisuals.vignetteAlpha(30_000) - 0.20F) < 1.0E-6F, "辐射 3 万时封顶 0.20");
        helper.assertTrue("60:00".equals(HoannaVisuals.formatRemaining(72_000)) && "0:01".equals(HoannaVisuals.formatRemaining(1))
                && "0:01".equals(HoannaVisuals.formatRemaining(20)) && "0:02".equals(HoannaVisuals.formatRemaining(21))
                && "10:00".equals(HoannaVisuals.formatRemaining(12_000)), "剩余时间应按 mm:ss 显示");
        // 提示条接在 Boss 血条下面（6a 审查 M3-5）：原版一条（y 12、增量 19、字高 9）→ 22；自定义血条取消事件、增量 40 → 43；
        // 隐藏血条的模组把增量设成 0 → 仍贴顶 4；没有血条 → 4
        int vanilla = HoannaVisuals.hudBossSlotBottom(12, 19, 9);
        int custom = HoannaVisuals.hudBossSlotBottom(12, 40, 9);
        helper.assertTrue(vanilla == 22 && HoannaVisuals.hudTop(vanilla) == 22, "原版 Boss 血条下提示条应从 22 开始，实际 " + vanilla);
        helper.assertTrue(HoannaVisuals.hudTop(Math.max(vanilla, custom)) == 43, "自定义血条（增量 40）下提示条应从 43 开始");
        helper.assertTrue(HoannaVisuals.hudTop(HoannaVisuals.hudBossSlotBottom(12, 0, 9)) == HoannaVisuals.HUD_TOP_MARGIN
                && HoannaVisuals.hudTop(-1) == HoannaVisuals.HUD_TOP_MARGIN, "没有（可见的）Boss 血条时提示条贴顶");
        helper.succeed();
    }

    /**
     * 提示条收起态挪到快捷栏左端的左上方：GUI 缩放后的宽 ≥ 320（原版自动缩放保证的下限）、收起态宽 ≤ 56（「999:59」）时一定放在那里，
     * 右边离快捷栏左端空 2、底边离副手槽顶空 2；放在那里时不出屏，也碰不到快捷栏那一栏（screenWidth / 2 ± 92 整列：快捷栏和选中框、
     * 经验条和等级数字、血量 / 吸收 / 护甲 / 饥饿 / 氧气 / 坐骑血量，行数再多也在这一栏里）、快捷栏那一行（screenHeight − 23 往下：两侧的副手槽、
     * 攻击指示器）和聊天栏（底在 screenHeight − 40）；更窄时退回顶部居中，照样不出屏。
     */
    @GameTest(template = EMPTY)
    public static void eventHudCollapsedBesideHotbar(GameTestHelper helper) {
        int h = HoannaVisuals.HUD_COLLAPSED_HEIGHT;
        int[] pos = new int[2];
        int[] widths = {20, 32, 44, 50, 56, 80, 120};
        int[] tops = {HoannaVisuals.HUD_TOP_MARGIN, 22, 43};
        for (int screenW = 100; screenW <= 1200; screenW++) {
            for (int screenH : new int[]{120, 240, 241, 300, 480, 540, 1080}) {
                for (int w : widths) {
                    for (int top : tops) {
                        HoannaVisuals.hudCollapsedPos(screenW, screenH, w, h, top, pos);
                        int x = pos[0];
                        int y = pos[1];
                        int center = screenW / 2;
                        boolean corner = x + w <= center - HoannaVisuals.HOTBAR_HALF_WIDTH - HoannaVisuals.HUD_COLLAPSED_GAP;
                        String problem = null;
                        if (screenW >= 320 && screenH >= 240 && w <= 56 && !(corner
                                && x + w == center - HoannaVisuals.HOTBAR_HALF_WIDTH - HoannaVisuals.HUD_COLLAPSED_GAP
                                && y + h == screenH - HoannaVisuals.HOTBAR_ROW_TOP - HoannaVisuals.HUD_COLLAPSED_GAP)) {
                            problem = "收起态应在快捷栏左端的左上方";
                        } else if (corner) {
                            if (x < HoannaVisuals.HUD_EDGE_MARGIN || y < top || y + h > screenH) {
                                problem = "收起态出屏了";
                            } else if (x + w > center - 92) {
                                problem = "收起态压到快捷栏那一栏（血量、护甲、经验条……）";
                            } else if (y + h > screenH - 23) {
                                problem = "收起态压到快捷栏那一行（副手槽、攻击指示器）";
                            } else if (y < screenH - 40) {
                                problem = "收起态压到聊天栏";
                            }
                        } else if (y != top || x != (screenW - w) / 2) {
                            problem = "放不下时应退回顶部居中";
                        } else if (w <= screenW && (x < 0 || x + w > screenW)) {
                            problem = "顶部居中时出屏了";
                        }
                        if (problem != null) {
                            helper.fail(problem + "：屏幕 " + screenW + " × " + screenH + "、宽 " + w + "、顶 " + top + " → (" + x + ", " + y + ")");
                        }
                    }
                }
            }
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 11. 地平线带（6a 审查 M3-1 / M3-2）
    // ------------------------------------------------------------------

    /**
     * 地平线带盖满下半球（M3-2）：环从下往上严格递增、最低一环 ≤ −atan(16/128) ≈ −7.1°（视距 2 时天穹外圈被远平面裁掉的那一圈也要盖住）、
     * 地平线以下 alpha 恒为 1、往上单调不增、最高一环 0；半径 ≤ 100（远平面 = 视距 × 64 格，视距 2 只有 128）。
     */
    @GameTest(template = EMPTY)
    public static void horizonBandCoversLowerHemisphere(GameTestHelper helper) {
        float[] rings = HoannaVisuals.HORIZON_RINGS;
        helper.assertTrue(rings.length >= 4, "地平线带至少要 4 环");
        for (int i = 1; i < rings.length; i++) {
            helper.assertTrue(rings[i] > rings[i - 1], "地平线带的环应从下往上严格递增（第 " + i + " 环）");
        }
        float lowestNeeded = -(float) Math.toDegrees(Math.atan(HoannaVisuals.SKY_DISC_HEIGHT / 128D));
        helper.assertTrue(rings[0] <= lowestNeeded, "最低一环应 ≤ " + lowestNeeded + "°，实际 " + rings[0]);
        helper.assertTrue(rings[0] >= -90F, "最低一环不应低于 −90°");
        helper.assertTrue(HoannaVisuals.horizonAlpha(rings[rings.length - 1]) == 0F, "最高一环 alpha 应为 0");
        helper.assertTrue(HoannaVisuals.HORIZON_RADIUS > 1F && HoannaVisuals.HORIZON_RADIUS <= 100F,
                "地平线带半径应在 (1, 100]，实际 " + HoannaVisuals.HORIZON_RADIUS);
        helper.assertTrue(HoannaVisuals.HORIZON_SEGMENTS >= 16, "每环至少 16 段");
        float last = 2F;
        float lastBand = 2F;
        for (int i = -1800; i <= 800; i++) {
            float e = i * 0.05F;
            float a = HoannaVisuals.horizonAlpha(e);
            float band = HoannaVisuals.horizonBandAlpha(e);
            if (e <= 0F) {
                helper.assertTrue(a == 1F && band == 1F, "地平线以下（" + e + "°）alpha 应为 1，实际 " + a + " / " + band);
            }
            helper.assertTrue(a >= 0F && a <= 1F && a <= last + 1.0E-6F, "地平线带 alpha 应在 [0, 1] 且随仰角单调不增（" + e + "°）");
            helper.assertTrue(band <= lastBand + 1.0E-6F, "插值后的地平线带 alpha 应随仰角单调不增（" + e + "°）");
            last = a;
            lastBand = band;
        }
        helper.succeed();
    }

    /**
     * 地平线不靠雾距离（M3-1）：按原版着色器的天空雾（{@code position} 着色器、FOG_SKY 圆柱距离、smoothstep、天穹外沿以下是清屏色 = 雾色）
     * 算天穹每个仰角的雾量，再叠上地平线带：天空雾终点从原版视距 2（32）一直到远景模组的远景（640）、关掉天空雾，
     * 仰角 ≤ 3° 处雾量都 ≥ 0.99，−10°~45° 里每 0.01° 的变化 ≤ 0.002（≤ 0.2 / 度，没有硬线）。
     * 对照：不画地平线带、天空雾终点 640 时模型里确实有硬线（天穹外沿 1.79° 一步跳约 0.1）。
     */
    @GameTest(template = EMPTY)
    public static void horizonBandIndependentOfFog(GameTestHelper helper) {
        float[] ends = {32F, 64F, 128F, 192F, 256F, 384F, 512F, 640F, 1024F, 4096F, Float.POSITIVE_INFINITY};
        List<String> problems = new ArrayList<>();
        for (float end : ends) {
            float prev = Float.NaN;
            float maxStep = 0F;
            float minLow = 1F;
            for (int i = -1000; i <= 4500; i++) {
                float e = i / 100F;
                float amount = 1F - (1F - HoannaVisuals.horizonBandAlpha(e)) * (1F - vanillaSkyFog(e, end));
                if (!Float.isNaN(prev)) {
                    maxStep = Math.max(maxStep, Math.abs(amount - prev));
                }
                prev = amount;
                if (e <= 3F) {
                    minLow = Math.min(minLow, amount);
                }
            }
            if (minLow < 0.99F || maxStep > 0.002F) {
                problems.add(String.format("终点 %.0f：3° 以下最少 %.3f、每 0.01° 最大变化 %.4f", end, minLow, maxStep));
            }
        }
        helper.assertTrue(problems.isEmpty(), "地平线雾量随天空雾距离变了或有硬线：" + problems);
        float control = 0F;
        float prev = Float.NaN;
        for (int i = -1000; i <= 4500; i++) {
            float amount = vanillaSkyFog(i / 100F, 640F);
            if (!Float.isNaN(prev)) {
                control = Math.max(control, Math.abs(amount - prev));
            }
            prev = amount;
        }
        helper.assertTrue(control > 0.05F, "对照组（不画地平线带、终点 640）应能看出硬线，模型可能写错了：" + control);
        helper.succeed();
    }

    /**
     * 原版天空雾落在天穹（{@code buildSkyDisc(16)}、半径 512）仰角 e 处的雾量：顶点的雾距离是圆柱距离 max(水平, |y|)，中心 16、外沿 512，
     * 片元上线性插值；雾量 smoothstep(0, 终点, 距离)，≥ 终点为 1。天穹外沿以下没有天穹，清屏色就是雾色（雾量 1）。
     */
    private static float vanillaSkyFog(float elevationDeg, float fogEnd) {
        float height = HoannaVisuals.SKY_DISC_HEIGHT;
        float radius = HoannaVisuals.SKY_DISC_RADIUS;
        float edge = (float) Math.toDegrees(Math.atan(height / radius));
        if (elevationDeg <= edge) {
            return 1F;
        }
        if (!Float.isFinite(fogEnd)) {
            return 0F;
        }
        float horizontal = height / (float) Math.tan(Math.toRadians(elevationDeg));
        float distance = height + horizontal * (1F - height / radius);
        return distance >= fogEnd ? 1F : HoannaVisuals.smoothstep(0F, fogEnd, distance);
    }
}
