package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.DyeColor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 第六批 6a 两处贴图改动的资源检查（渲染本身在客户端，这里锁住模型文件、贴图和算出来的颜色；外观看展示厅
 * 04_scene_void_dye / void_stone_close / void_glass_close / void_tiling、04_scene_hoanna_flora、05_hoanna_biome_dreadlands_* / hoanna_desert_*）。
 * <ul>
 *   <li>虚空石 / 虚空玻璃（M7-2）：两层模型——底层原贴图不染色、上层纹路带 tintindex 0；纹路贴图（tools/gen/void_veins.py）是灰度、
 *       四个出口对齐；16 种染料乘在纹路核心上两两都分得清。改回整张乘色（cube_colored）或把纹路调暗都会挂。</li>
 *   <li>恐惧仙人掌 / 恐惧泥土（M1-5）：贴图（tools/gen/dread_textures.py）平均 L* 落在目标区间、色相和原作一样。换回原作的原图会挂。</li>
 * </ul>
 * 读的是 jar 里的 assets（生成的模型在 src/generated/resources，先跑 runData）。颜色公式和脚本 tools/gen/texlib.py 相同（sRGB → CIELAB D65、CIEDE2000）。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class HoannaTextureTests {

    private static final String EMPTY = "empty";
    private static final String[] FACES = {"north", "east", "south", "west", "up", "down"};

    /** 16 色纹路核心两两的 CIEDE2000 下限（现在最小的一对是灰 / 黑，12.8） */
    static final double MIN_VEIN_DELTA_E = 10.0;
    /** 恐惧仙人掌侧面、恐惧泥土的平均 L* 目标区间 */
    static final double CACTUS_SIDE_MIN_L = 30.0;
    static final double CACTUS_SIDE_MAX_L = 35.0;
    static final double DREAD_DIRT_MIN_L = 22.0;
    static final double DREAD_DIRT_MAX_L = 28.0;
    /** 原作贴图的平均色相角（度，不透明像素的平均 a* b*）：侧面 98.2、顶面 90.0、泥土 55.1；提亮只许偏几度 */
    static final double CACTUS_SIDE_HUE = 98.2;
    static final double CACTUS_TOP_HUE = 90.0;
    static final double DREAD_DIRT_HUE = 55.1;
    static final double HUE_TOLERANCE = 4.0;

    private HoannaTextureTests() {
    }

    // ---------------------------------------------------------------- 虚空石 / 虚空玻璃

    /**
     * 两个方块的模型挂在两层模板上、贴图和渲染层对得上；模板本身：底层六面不带 tintindex、纹路层六面带 tintindex 0 且 shade false。
     * 石头的纹路层和底层整格共面（顶点相同，深度逐像素相等）；玻璃的纹路各面是单独一片，只沿自己的法线外推 0~0.1 像素、面内仍是 0~16
     * （半透明层按面中心距离排序，外推让纹路总排在玻璃之后；面内不放大，相邻两格的纹路不重叠）。
     */
    @GameTest(template = EMPTY)
    public static void voidBlocksUseVeinedModels(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        checkBlockModel(problems, "void_stone", "dyeable_veined", "voidstone", "minecraft:cutout_mipped");
        checkBlockModel(problems, "void_glass", "dyeable_veined_glass", "voidglass", "minecraft:translucent");

        JsonObject stone = json(problems, "models", "block", "dyeable_veined.json");
        if (stone != null) {
            JsonArray elements = stone.getAsJsonArray("elements");
            if (elements == null || elements.size() != 2) {
                problems.add("dyeable_veined.json 应正好两层（底图、纹路），实际 " + (elements == null ? 0 : elements.size()));
            } else {
                checkCube(problems, "dyeable_veined 底层", elements.get(0).getAsJsonObject(), "#base", false);
                checkCube(problems, "dyeable_veined 纹路层", elements.get(1).getAsJsonObject(), "#veins", true);
            }
        }

        JsonObject glass = json(problems, "models", "block", "dyeable_veined_glass.json");
        if (glass != null) {
            JsonArray elements = glass.getAsJsonArray("elements");
            if (elements == null || elements.size() != 7) {
                problems.add("dyeable_veined_glass.json 应是 1 个底层 + 6 片纹路，实际 " + (elements == null ? 0 : elements.size()));
            } else {
                checkCube(problems, "dyeable_veined_glass 底层", elements.get(0).getAsJsonObject(), "#base", false);
                List<String> seen = new ArrayList<>();
                for (int i = 1; i < elements.size(); i++) {
                    checkPushedVein(problems, elements.get(i).getAsJsonObject(), seen);
                }
                if (seen.size() != 6) {
                    problems.add("玻璃纹路应六个方向各一片，实际 " + seen);
                }
            }
        }
        report(helper, "虚空石 / 虚空玻璃的两层模型（先跑 runData）", problems);
    }

    /**
     * 纹路贴图：16 × 16、不透明处是灰度、最亮处 255；石头只有全透 / 不透（镂空层）；四个出口在对边同一位置、边框其余处透明（相邻两格接得上）；
     * 玻璃的实心纹路和石头的完全重合（石头和玻璃拼在一起也接得上）；覆盖率在 10%~35%（再多就盖住底图了）。
     */
    @GameTest(template = EMPTY)
    public static void voidVeinTexturesTileAndStayGray(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        BufferedImage stone = image(problems, "voidstone_veins.png");
        BufferedImage glass = image(problems, "voidglass_veins.png");
        if (stone != null && glass != null) {
            checkVeinTexture(problems, "voidstone_veins", stone, true);
            checkVeinTexture(problems, "voidglass_veins", glass, false);
            if (stone.getWidth() == glass.getWidth() && stone.getHeight() == glass.getHeight()) {
                for (int y = 0; y < stone.getHeight(); y++) {
                    for (int x = 0; x < stone.getWidth(); x++) {
                        boolean stoneCore = alpha(stone.getRGB(x, y)) == 255 && gray(stone.getRGB(x, y)) >= 100;
                        boolean glassCore = alpha(glass.getRGB(x, y)) == 255;
                        if (stoneCore != glassCore) {
                            problems.add("石头和玻璃的纹路在 (" + x + ", " + y + ") 不一致");
                        }
                    }
                }
            }
        }
        report(helper, "纹路贴图", problems);
    }

    /**
     * 16 种染料乘在纹路核心（贴图里最亮的灰）上，两两 CIEDE2000 ≥ {@link #MIN_VEIN_DELTA_E}。
     * 染料色是原版 {@link DyeColor#getFireworkColor()}（DyeableBlock.tintColor），乘法同原版着色器（贴图 × 顶点色，逐通道）。
     */
    @GameTest(template = EMPTY)
    public static void voidVeinColorsDistinguishable(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        BufferedImage stone = image(problems, "voidstone_veins.png");
        if (stone == null) {
            report(helper, "纹路颜色", problems);
            return;
        }
        int core = 0;
        for (int y = 0; y < stone.getHeight(); y++) {
            for (int x = 0; x < stone.getWidth(); x++) {
                if (alpha(stone.getRGB(x, y)) == 255) {
                    core = Math.max(core, gray(stone.getRGB(x, y)));
                }
            }
        }
        DyeColor[] colors = DyeColor.values();
        double[][] labs = new double[colors.length][];
        for (int i = 0; i < colors.length; i++) {
            int tint = colors[i].getFireworkColor();
            labs[i] = srgbToLab(((tint >> 16) & 255) * core / 255.0, ((tint >> 8) & 255) * core / 255.0, (tint & 255) * core / 255.0);
        }
        double min = Double.MAX_VALUE;
        String pair = "";
        for (int i = 0; i < colors.length; i++) {
            for (int j = i + 1; j < colors.length; j++) {
                double d = deltaE2000(labs[i], labs[j]);
                if (d < min) {
                    min = d;
                    pair = colors[i].getName() + " / " + colors[j].getName();
                }
            }
        }
        if (min < MIN_VEIN_DELTA_E) {
            problems.add(String.format(Locale.ROOT, "纹路核心（灰 %d）乘色后最近的一对 %s 只差 ΔE2000 %.1f，应 ≥ %.1f", core, pair, min, MIN_VEIN_DELTA_E));
        }
        report(helper, "纹路颜色", problems);
    }

    // ---------------------------------------------------------------- 恐惧仙人掌 / 恐惧泥土

    /** 提亮后的平均 L*（不透明像素逐个算再平均）在目标区间，顶面比侧面亮，色相和原作差不到 {@link #HUE_TOLERANCE} 度 */
    @GameTest(template = EMPTY)
    public static void dreadTexturesBrightEnough(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        double[] side = textureStats(problems, "firstworld/cactus_side.png");
        double[] top = textureStats(problems, "firstworld/cactus_top.png");
        double[] dirt = textureStats(problems, "firstworld/dreaddirt.png");
        if (side != null) {
            range(problems, "恐惧仙人掌侧面", side[0], CACTUS_SIDE_MIN_L, CACTUS_SIDE_MAX_L);
            hue(problems, "恐惧仙人掌侧面", side[1], CACTUS_SIDE_HUE);
        }
        if (top != null) {
            hue(problems, "恐惧仙人掌顶面", top[1], CACTUS_TOP_HUE);
            if (side != null && top[0] <= side[0]) {
                problems.add(String.format(Locale.ROOT, "恐惧仙人掌顶面（L* %.1f）应比侧面（%.1f）亮，同原作", top[0], side[0]));
            }
        }
        if (dirt != null) {
            range(problems, "恐惧泥土", dirt[0], DREAD_DIRT_MIN_L, DREAD_DIRT_MAX_L);
            hue(problems, "恐惧泥土", dirt[1], DREAD_DIRT_HUE);
        }
        report(helper, "恐惧仙人掌 / 恐惧泥土的亮度", problems);
    }

    // ---------------------------------------------------------------- 模型检查

    private static void checkBlockModel(List<String> problems, String block, String template, String texture, String renderType) {
        JsonObject model = json(problems, "models", "block", block + ".json");
        if (model == null) {
            return;
        }
        expect(problems, block + ".json 的 parent", string(model, "parent"), EssentialCraft5.MODID + ":block/" + template);
        expect(problems, block + ".json 的 render_type", string(model, "render_type"), renderType);
        JsonObject textures = model.getAsJsonObject("textures");
        if (textures == null) {
            problems.add(block + ".json 没有 textures");
            return;
        }
        expect(problems, block + ".json 的 base", string(textures, "base"), EssentialCraft5.MODID + ":block/" + texture);
        expect(problems, block + ".json 的 veins", string(textures, "veins"), EssentialCraft5.MODID + ":block/" + texture + "_veins");
        expect(problems, block + ".json 的 particle", string(textures, "particle"), EssentialCraft5.MODID + ":block/" + texture);
    }

    /** 整格（0~16）、六面齐全、贴图对、cullface 对、tintindex 与 shade 按层区分 */
    private static void checkCube(List<String> problems, String what, JsonObject element, String texture, boolean veins) {
        float[] from = vector(element, "from");
        float[] to = vector(element, "to");
        for (int axis = 0; axis < 3; axis++) {
            if (from[axis] != 0F || to[axis] != 16F) {
                problems.add(what + " 应是整格 0~16，实际第 " + axis + " 轴 " + from[axis] + " ~ " + to[axis]);
            }
        }
        boolean shade = !element.has("shade") || element.get("shade").getAsBoolean();
        if (shade == veins) {
            problems.add(what + (veins ? " 应 shade false（纹路各面一样亮）" : " 应保留明暗（shade 默认）"));
        }
        JsonObject faces = element.getAsJsonObject("faces");
        for (String face : FACES) {
            JsonObject f = faces == null ? null : faces.getAsJsonObject(face);
            if (f == null) {
                problems.add(what + " 缺 " + face + " 面");
                continue;
            }
            checkFace(problems, what + " " + face, f, face, texture, veins);
        }
    }

    /** 玻璃纹路的一片：只有一个面、在该面的平面上、沿法线外推 (0, 0.1] 像素、面内 0~16 */
    private static void checkPushedVein(List<String> problems, JsonObject element, List<String> seen) {
        JsonObject faces = element.getAsJsonObject("faces");
        if (faces == null || faces.size() != 1) {
            problems.add("玻璃纹路每片应只有一个面：" + element);
            return;
        }
        String face = faces.keySet().iterator().next();
        seen.add(face);
        float[] from = vector(element, "from");
        float[] to = vector(element, "to");
        int axis = switch (face) {
            case "east", "west" -> 0;
            case "up", "down" -> 1;
            default -> 2;
        };
        boolean positive = face.equals("east") || face.equals("up") || face.equals("south");
        float push = positive ? from[axis] - 16F : -from[axis];
        if (from[axis] != to[axis] || !(push > 0F && push <= 0.1F)) {
            problems.add("玻璃纹路 " + face + " 应是贴着面外 0~0.1 像素的一片，实际第 " + axis + " 轴 " + from[axis] + " ~ " + to[axis]);
        }
        for (int other = 0; other < 3; other++) {
            if (other != axis && (from[other] != 0F || to[other] != 16F)) {
                problems.add("玻璃纹路 " + face + " 面内应是 0~16（不放大），实际第 " + other + " 轴 " + from[other] + " ~ " + to[other]);
            }
        }
        boolean shade = !element.has("shade") || element.get("shade").getAsBoolean();
        if (shade) {
            problems.add("玻璃纹路 " + face + " 应 shade false");
        }
        checkFace(problems, "玻璃纹路 " + face, faces.getAsJsonObject(face), face, "#veins", true);
    }

    private static void checkFace(List<String> problems, String what, JsonObject face, String direction, String texture, boolean tinted) {
        expect(problems, what + " 的贴图", string(face, "texture"), texture);
        expect(problems, what + " 的 cullface", string(face, "cullface"), direction);
        boolean hasTint = face.has("tintindex");
        if (tinted && (!hasTint || face.get("tintindex").getAsInt() != 0)) {
            problems.add(what + " 应带 tintindex 0（染料色只染纹路）");
        } else if (!tinted && hasTint) {
            problems.add(what + " 不应带 tintindex（底图不染色）");
        }
    }

    // ---------------------------------------------------------------- 贴图检查

    private static void checkVeinTexture(List<String> problems, String name, BufferedImage image, boolean binaryAlpha) {
        int size = image.getWidth();
        if (size != 16 || image.getHeight() != 16) {
            problems.add(name + " 应是 16 × 16，实际 " + image.getWidth() + " × " + image.getHeight());
            return;
        }
        int opaque = 0;
        int brightest = 0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int argb = image.getRGB(x, y);
                int a = alpha(argb);
                if (a == 0) {
                    continue;
                }
                opaque++;
                int r = (argb >> 16) & 255;
                int g = (argb >> 8) & 255;
                int b = argb & 255;
                if (r != g || g != b) {
                    problems.add(name + " (" + x + ", " + y + ") 不是灰度：" + Integer.toHexString(argb));
                }
                if (binaryAlpha && a != 255) {
                    problems.add(name + " (" + x + ", " + y + ") 半透明（alpha " + a + "），镂空层只认全透 / 不透");
                }
                if (a == 255) {
                    brightest = Math.max(brightest, r);
                }
            }
        }
        if (brightest != 255) {
            problems.add(name + " 最亮处应是 255（乘出来才是染料本色），实际 " + brightest);
        }
        double coverage = opaque / (double) (size * size);
        if (coverage < 0.10 || coverage > 0.35) {
            problems.add(String.format(Locale.ROOT, "%s 的覆盖率 %.0f%% 不在 10%%~35%%", name, coverage * 100));
        }
        int ports = 0;
        for (int i = 0; i < size; i++) {
            boolean left = alpha(image.getRGB(0, i)) == 255;
            boolean right = alpha(image.getRGB(size - 1, i)) == 255;
            boolean top = alpha(image.getRGB(i, 0)) == 255;
            boolean bottom = alpha(image.getRGB(i, size - 1)) == 255;
            if (left != right) {
                problems.add(name + " 第 " + i + " 行左右边缘没对齐");
            }
            if (top != bottom) {
                problems.add(name + " 第 " + i + " 列上下边缘没对齐");
            }
            ports += (left ? 1 : 0) + (top ? 1 : 0);
        }
        if (ports != 2) {
            problems.add(name + " 应是左右一处、上下一处出口，边框其余透明，实际 " + ports + " 对");
        }
    }

    /** {平均 L*, 平均 a* b* 的色相角（度）}，只算 alpha ≥ 128 的像素 */
    private static double[] textureStats(List<String> problems, String path) {
        BufferedImage image = image(problems, path);
        if (image == null) {
            return null;
        }
        double l = 0;
        double a = 0;
        double b = 0;
        int n = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                if (alpha(argb) < 128) {
                    continue;
                }
                double[] lab = srgbToLab((argb >> 16) & 255, (argb >> 8) & 255, argb & 255);
                l += lab[0];
                a += lab[1];
                b += lab[2];
                n++;
            }
        }
        if (n == 0) {
            problems.add(path + " 没有不透明像素");
            return null;
        }
        double hue = Math.toDegrees(Math.atan2(b / n, a / n));
        return new double[] {l / n, hue < 0 ? hue + 360 : hue};
    }

    private static void range(List<String> problems, String what, double value, double min, double max) {
        if (value < min || value > max) {
            problems.add(String.format(Locale.ROOT, "%s的平均 L* %.1f 不在 %.0f~%.0f", what, value, min, max));
        }
    }

    private static void hue(List<String> problems, String what, double value, double original) {
        double diff = Math.abs(((value - original) % 360 + 540) % 360 - 180);
        if (diff > HUE_TOLERANCE) {
            problems.add(String.format(Locale.ROOT, "%s的色相 %.1f° 和原作 %.1f° 差了 %.1f°", what, value, original, diff));
        }
    }

    // ---------------------------------------------------------------- 颜色（同 tools/gen/texlib.py）

    static double srgbToLinear(double c) {
        c /= 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    static double[] srgbToLab(double r8, double g8, double b8) {
        double r = srgbToLinear(r8);
        double g = srgbToLinear(g8);
        double b = srgbToLinear(b8);
        double x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047;
        double y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b;
        double z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883;
        double fx = labF(x);
        double fy = labF(y);
        double fz = labF(z);
        return new double[] {116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }

    private static double labF(double t) {
        return t > 216.0 / 24389 ? Math.cbrt(t) : (24389.0 / 27 * t + 16) / 116;
    }

    static double deltaE2000(double[] lab1, double[] lab2) {
        double l1 = lab1[0];
        double a1 = lab1[1];
        double b1 = lab1[2];
        double l2 = lab2[0];
        double a2 = lab2[1];
        double b2 = lab2[2];
        double cBar = (Math.hypot(a1, b1) + Math.hypot(a2, b2)) / 2;
        double g = 0.5 * (1 - Math.sqrt(Math.pow(cBar, 7) / (Math.pow(cBar, 7) + Math.pow(25, 7))));
        double a1p = (1 + g) * a1;
        double a2p = (1 + g) * a2;
        double c1p = Math.hypot(a1p, b1);
        double c2p = Math.hypot(a2p, b2);
        double h1p = (Math.toDegrees(Math.atan2(b1, a1p)) + 360) % 360;
        double h2p = (Math.toDegrees(Math.atan2(b2, a2p)) + 360) % 360;
        double dl = l2 - l1;
        double dc = c2p - c1p;
        double dh;
        if (c1p * c2p == 0) {
            dh = 0;
        } else {
            dh = h2p - h1p;
            if (dh > 180) {
                dh -= 360;
            } else if (dh < -180) {
                dh += 360;
            }
        }
        double bigDh = 2 * Math.sqrt(c1p * c2p) * Math.sin(Math.toRadians(dh / 2));
        double lBar = (l1 + l2) / 2;
        double cpBar = (c1p + c2p) / 2;
        double hBar;
        if (c1p * c2p == 0) {
            hBar = h1p + h2p;
        } else if (Math.abs(h1p - h2p) <= 180) {
            hBar = (h1p + h2p) / 2;
        } else {
            hBar = h1p + h2p < 360 ? (h1p + h2p + 360) / 2 : (h1p + h2p - 360) / 2;
        }
        double t = 1 - 0.17 * Math.cos(Math.toRadians(hBar - 30)) + 0.24 * Math.cos(Math.toRadians(2 * hBar))
                + 0.32 * Math.cos(Math.toRadians(3 * hBar + 6)) - 0.20 * Math.cos(Math.toRadians(4 * hBar - 63));
        double dTheta = 30 * Math.exp(-Math.pow((hBar - 275) / 25, 2));
        double rc = 2 * Math.sqrt(Math.pow(cpBar, 7) / (Math.pow(cpBar, 7) + Math.pow(25, 7)));
        double sl = 1 + 0.015 * Math.pow(lBar - 50, 2) / Math.sqrt(20 + Math.pow(lBar - 50, 2));
        double sc = 1 + 0.045 * cpBar;
        double sh = 1 + 0.015 * cpBar * t;
        double rt = -Math.sin(Math.toRadians(2 * dTheta)) * rc;
        return Math.sqrt(Math.pow(dl / sl, 2) + Math.pow(dc / sc, 2) + Math.pow(bigDh / sh, 2) + rt * (dc / sc) * (bigDh / sh));
    }

    // ---------------------------------------------------------------- 读资源

    private static Path asset(String... path) {
        String[] full = new String[path.length + 2];
        full[0] = "assets";
        full[1] = EssentialCraft5.MODID;
        System.arraycopy(path, 0, full, 2, path.length);
        return ModList.get().getModFileById(EssentialCraft5.MODID).getFile().findResource(full);
    }

    private static JsonObject json(List<String> problems, String... path) {
        Path file = asset(path);
        if (!Files.exists(file)) {
            problems.add("没有 " + String.join("/", path));
            return null;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            problems.add("读不了 " + String.join("/", path) + "：" + e.getMessage());
            return null;
        }
    }

    private static BufferedImage image(List<String> problems, String texture) {
        Path file = asset(("textures/block/" + texture).split("/"));
        if (!Files.exists(file)) {
            problems.add("没有 textures/block/" + texture);
            return null;
        }
        try (InputStream in = Files.newInputStream(file)) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                problems.add("textures/block/" + texture + " 不是能读的 PNG");
            }
            return image;
        } catch (IOException e) {
            problems.add("读不了 textures/block/" + texture + "：" + e.getMessage());
            return null;
        }
    }

    private static int alpha(int argb) {
        return (argb >>> 24) & 255;
    }

    private static int gray(int argb) {
        return (argb >> 16) & 255;
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || !value.isJsonPrimitive() ? null : value.getAsString();
    }

    private static float[] vector(JsonObject element, String key) {
        JsonArray array = element.getAsJsonArray(key);
        return new float[] {array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat()};
    }

    private static void expect(List<String> problems, String what, String actual, String expected) {
        if (!expected.equals(actual)) {
            problems.add(what + " 应是 " + expected + "，实际 " + actual);
        }
    }

    private static void report(GameTestHelper helper, String what, List<String> problems) {
        if (!problems.isEmpty()) {
            helper.fail(what + "：" + String.join("；", problems.subList(0, Math.min(12, problems.size())))
                    + (problems.size() > 12 ? "……共 " + problems.size() + " 条" : ""));
        }
        helper.succeed();
    }
}
