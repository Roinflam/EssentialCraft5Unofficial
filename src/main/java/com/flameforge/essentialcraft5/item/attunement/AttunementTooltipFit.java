package com.flameforge.essentialcraft5.item.attunement;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Predicate;

/**
 * 同调块加进提示框后高过屏幕时让出哪几行。量高度要用客户端字体，由调用方传 fits（客户端按 Forge 的折行和原版提示框高度估）；
 * 取舍本身不碰客户端类，GameTest 能直接测。
 * <ul>
 *   <li>不按 Shift：先去掉「n 阶：……」逐阶效果那几行（标题、同调词条、觉醒、Shift 提示照留），还放不下再只留标题。
 *       附魔多的满阶武器会走到这一步：原版把附魔行写在同调块和属性行之间，不让的话提示框顶到屏幕外，名字和标题被截掉。</li>
 *   <li>按住 Shift：物品说明全写出来，同调块直接只留标题。</li>
 *   <li>连标题都放不下：同调块整块不写。</li>
 *   <li>按住 Shift、同调块整块不写了物品说明本身还高过屏幕（余烬效果多的满阶裁决之刃在 720p、界面缩放 3、背包格位置）：
 *       从物品说明的末尾往前去掉几行，末尾换一行「……还有 N 行」（{@link #truncateNotes}）。物品名、附魔行、属性行不动。</li>
 * </ul>
 */
public final class AttunementTooltipFit {

    /** 「n 阶：……」那一行的语言键 */
    public static final String EFFECT_KEY = "tooltip.essentialcraft5.attunement.effect";

    private AttunementTooltipFit() {
    }

    /** lines 的第一行是标题；fits 判断「原有提示框 + 这几行」放不放得下 */
    public static List<Component> fit(List<Component> lines, boolean shift, Predicate<List<Component>> fits) {
        if (lines.isEmpty() || fits.test(lines)) {
            return lines;
        }
        if (!shift) {
            List<Component> brief = lines.stream().filter(line -> !isStageEffect(line)).toList();
            if (brief.size() < lines.size() && fits.test(brief)) {
                return brief;
            }
        }
        List<Component> title = List.of(lines.get(0));
        return fits.test(title) ? title : List.of();
    }

    public static boolean isStageEffect(Component line) {
        return line.getContents() instanceof TranslatableContents contents && EFFECT_KEY.equals(contents.getKey());
    }

    /**
     * 物品说明在 tooltip 里占 [from, to)，整份放不下时从说明末尾往前去掉最少的几行，末尾换成 more(去掉的行数)，原地改 tooltip。
     * 去掉的行数按逻辑行算（一行折成几行也算一行）。行数少一点不会更放不下（宽度只会变窄、折行只会变少），所以二分找最少要去几行，
     * 提示框每帧都要重排，fits 只调 O(log n) 次。说明全去掉还放不下（附魔行太多）也只去说明，剩下的照原样画。
     *
     * @return 去掉了几行；放得下或没有说明时为 0
     */
    public static int truncateNotes(List<Component> tooltip, int from, int to, IntFunction<Component> more, Predicate<List<Component>> fits) {
        int notes = to - from;
        if (notes <= 0 || from < 0 || to > tooltip.size() || fits.test(tooltip)) {
            return 0;
        }
        int low = 1;
        int high = notes;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (fits.test(cut(tooltip, from, to, mid, more))) {
                high = mid;
            } else {
                low = mid + 1;
            }
        }
        List<Component> result = cut(tooltip, from, to, low, more);
        tooltip.clear();
        tooltip.addAll(result);
        return low;
    }

    /**
     * own（appendHoverText 再算一遍的结果）在 tooltip 里逐行（按显示的文字）一样的那一段从哪开始；先看名字后面那一格，没有再往后找；
     * 找不到返回 -1。比对时容忍某一行被别的监听器原地包了一层：提示框里那一行是翻译文字、第一个参数显示出来和原行一样，也算同一行
     * （按住 Shift 时 MRU 护盾盔甲的 MRU 行会被就地换成「MRU …（满电约挡 …）」，以前因此找不到说明那一段、截断不生效）。
     */
    public static int indexOfRun(List<Component> tooltip, List<Component> own) {
        int n = own.size();
        if (n == 0) {
            return -1;
        }
        String[] wanted = new String[n];
        for (int i = 0; i < n; i++) {
            wanted[i] = own.get(i).getString();
        }
        for (int start = Math.min(1, tooltip.size()); start + n <= tooltip.size(); start++) {
            boolean same = true;
            for (int i = 0; i < n && same; i++) {
                same = sameLine(tooltip.get(start + i), wanted[i]);
            }
            if (same) {
                return start;
            }
        }
        return -1;
    }

    private static boolean sameLine(Component line, String wanted) {
        if (wanted.equals(line.getString())) {
            return true;
        }
        return line.getContents() instanceof TranslatableContents contents
                && contents.getArgs().length > 0
                && contents.getArgs()[0] instanceof Component first
                && wanted.equals(first.getString());
    }

    private static List<Component> cut(List<Component> tooltip, int from, int to, int removed, IntFunction<Component> more) {
        List<Component> out = new ArrayList<>(tooltip.size() - removed + 1);
        out.addAll(tooltip.subList(0, to - removed));
        out.add(more.apply(removed));
        out.addAll(tooltip.subList(to, tooltip.size()));
        return out;
    }
}
