package com.flameforge.essentialcraft5.item.attunement;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * 同调装备的外观（tools/plan/gear_growth.md 第 5 节）：名字颜色随阶变、满阶名字逐字渐变、战痕后缀「✦n」。通用代码，两端都能调
 * （物品类的 {@code getName} 覆写调它：余烬元素剑 / 裁决之刃、MRU 枪、五套同调盔甲）。物品格上的阶位小菱形（{@code client/AttunementDecorator}）用同一套颜色。
 * <p>
 * <b>颜色按家族取，起点是这件装备自己的贴图</b>：{@code tools/attunement/texture_hist.py} 对家族成员的贴图做饱和色直方图（饱和度 ≥ 0.3、明度 ≥ 0.25 的像素，
 * 按色相 10 度分桶，按 饱和度 × 明度 加权），第一名就是 {@link Palette#own}；{@code tools/attunement/gear_colors.py} 再在 OKLab 里算出下面的表：
 * <ul>
 *   <li>1 阶 = 自身色，亮度定到 L 0.76、彩度夹在 0.10~0.17（提示框深底上对比度 8 以上，不刺眼）；</li>
 *   <li>往上每阶往 MRU 紫罗兰（特效色板 {@code VfxPalette.VIOLET} 调到同样亮度，{@code #B08AFF}）收，满阶前一阶收到六成；</li>
 *   <li>满阶逐字渐变：自身色 → MRU 紫罗兰 → 矩阵品红（{@code VfxPalette.MAGENTA} 调亮度后 {@code #EF7CCD}），静态、不随时间动。</li>
 * </ul>
 * 这样低阶看得出是哪件装备，越养越「被源质浸透」，到顶都落在源质魔法的紫 / 品红色系里；绿的风之盔甲、红的宅男盔甲中间几阶会经过偏灰的雾紫，不跳色。
 * 改贴图后重跑那两个脚本、把输出贴回 {@link #PALETTES}。没登记的家族（别的模组加的）用虚空盔甲那套紫色。
 * <p>
 * 渐变要拿到名字原文逐字上色：用 {@code Language.getInstance()} 取译文；独立服只有原版语言、取不到本模组的译文（拿回来的还是键）时退回单色紫罗兰，
 * 不会把键名写进名字里。
 */
public final class AttunementLook {

    /** 一个家族的配色：own = 贴图主色（只作记录），stages[i] = i+1 阶（不含满阶），gradient = 满阶渐变的三个色标 */
    public record Palette(int own, int[] stages, int[] gradient) {
    }

    /** MRU 紫罗兰（满阶渐变中段、战痕标记） */
    public static final int VIOLET = 0xB08AFF;
    /** 矩阵品红（满阶渐变末端、战痕封顶） */
    public static final int MAGENTA = 0xEF7CCD;

    /** 各家族配色，键是家族注册名的 path；由 tools/attunement/gear_colors.py 生成 */
    private static final Map<String, Palette> PALETTES = Map.ofEntries(
            Map.entry("ember_sword", new Palette(0xC10000, new int[]{0xFF816F}, new int[]{0xFF816F, VIOLET, MAGENTA})),
            Map.entry("verdict_blade", new Palette(0xF45BE9, new int[]{0xED85E3, 0xE187E9, 0xD588EF, 0xC989F5}, new int[]{0xED85E3, VIOLET, MAGENTA})),
            Map.entry("mru_gun", new Palette(0xBD5388, new int[]{0xF787BC, 0xE28AD2, 0xCD8BE6}, new int[]{0xF787BC, VIOLET, MAGENTA})),
            Map.entry("mhs_armor", new Palette(0x721560, new int[]{0xED89D3}, new int[]{0xED89D3, VIOLET, MAGENTA})),
            Map.entry("wind_armor", new Palette(0x34AC34, new int[]{0x68CC64, 0x97ABCA}, new int[]{0x68CC64, VIOLET, MAGENTA})),
            Map.entry("void_armor", new Palette(0x10011D, new int[]{0xC2A0E3, 0xBC9AEC, 0xB794F5}, new int[]{0xC2A0E3, VIOLET, MAGENTA})),
            Map.entry("inquisitorium_armor", new Palette(0x6F2929, new int[]{0xEA9893, 0xD896B7, 0xC793D8}, new int[]{0xEA9893, VIOLET, MAGENTA})),
            Map.entry("computer_armor", new Palette(0x960004, new int[]{0xFF8171, 0xF98793, 0xE78BB1, 0xD58DCD}, new int[]{0xFF8171, VIOLET, MAGENTA})));
    private static final Palette FALLBACK = PALETTES.get("void_armor");

    /** 战痕标记 */
    public static final String SCAR_GLYPH = "✦";

    private AttunementLook() {
    }

    /** 这件同调装备显示的名字：base 是物品原本的名字（不是同调装备、0 阶又没战痕时原样返回） */
    public static Component name(ItemStack stack, Component base) {
        AttunementFamily family = AttunementFamily.of(stack);
        if (family == null) {
            return base;
        }
        int stage = Attunement.stage(stack);
        int scar = BattleScars.level(stack);
        if (stage <= 0 && scar <= 0) {
            return base;
        }
        MutableComponent name;
        if (stage >= family.maxStage()) {
            name = gradient(family, base);
        } else if (stage > 0) {
            int color = stageColor(family, stage);
            name = base.copy().withStyle(style -> style.withColor(TextColor.fromRgb(color)));
        } else {
            name = base.copy();
        }
        if (scar > 0) {
            name.append(Component.literal(" ")).append(scarMark(scar));
        }
        return name;
    }

    /** 「✦n」：战痕封顶（{@code scars.max_level}）时矩阵品红，其余 MRU 紫罗兰（不分家族，战痕是武器线共有的） */
    public static MutableComponent scarMark(int level) {
        MutableComponent mark = Component.empty();
        boolean top = level >= GearGrowthConfig.scarMaxLevel();
        Style style = Style.EMPTY.withColor(TextColor.fromRgb(top ? MAGENTA : VIOLET));
        mark.append(Component.literal(SCAR_GLYPH).withStyle(style));
        mark.append(Component.literal(Integer.toString(level)).withStyle(style));
        return mark;
    }

    /** 这一家族配色（没登记的用默认紫） */
    public static Palette palette(AttunementFamily family) {
        return family == null ? FALLBACK : PALETTES.getOrDefault(family.id().getPath(), FALLBACK);
    }

    /** stage 阶（1 起）的名字颜色；满阶取渐变起点 */
    public static int stageColor(AttunementFamily family, int stage) {
        Palette palette = palette(family);
        if (stage <= 0) {
            return palette.stages()[0];
        }
        if (stage > palette.stages().length) {
            return palette.gradient()[0];
        }
        return palette.stages()[stage - 1];
    }

    /** 名字逐字渐变；拿不到原文时退回单色紫罗兰 */
    public static MutableComponent gradient(AttunementFamily family, Component base) {
        String text = plain(base);
        if (text == null || text.isEmpty()) {
            return base.copy().withStyle(style -> style.withColor(TextColor.fromRgb(VIOLET)));
        }
        int[] stops = palette(family).gradient();
        MutableComponent out = Component.empty();
        int[] codePoints = text.codePoints().toArray();
        int n = codePoints.length;
        for (int i = 0; i < n; i++) {
            float t = n <= 1 ? 0.5F : i / (float) (n - 1);
            int color = sample(stops, t);
            out.append(Component.literal(new String(codePoints, i, 1)).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color))));
        }
        return out;
    }

    /** 名字原文：翻译键取译文（取不到返回 null），字面文字原样 */
    private static String plain(Component base) {
        if (base.getContents() instanceof TranslatableContents translatable) {
            if (translatable.getArgs().length > 0 || !base.getSiblings().isEmpty()) {
                return null;
            }
            String key = translatable.getKey();
            String text = Language.getInstance().getOrDefault(key, key);
            return text.equals(key) ? null : text;
        }
        return base.getString();
    }

    /** 色标之间线性插值（t 0~1；色标在 OKLab 里已经排好，相邻两个之间 sRGB 直插看不出差别） */
    public static int sample(int[] stops, float t) {
        if (stops.length == 1) {
            return stops[0];
        }
        float x = Math.max(0F, Math.min(1F, t)) * (stops.length - 1);
        int i = Math.min(stops.length - 2, (int) x);
        float f = x - i;
        int a = stops[i];
        int b = stops[i + 1];
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * f);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * f);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * f);
        return (r << 16) | (g << 8) | bl;
    }
}
