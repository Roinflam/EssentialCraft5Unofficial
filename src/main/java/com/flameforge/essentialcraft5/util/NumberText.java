package com.flameforge.essentialcraft5.util;

import javax.annotation.Nullable;

/**
 * 数字文本的千位分组：{@code 1234567.5} → {@code 1,234,567.5}。
 * <p>
 * 只处理已经格式化好的十进制文本（不带指数），分隔符由调用方给：研究手册取界面语言键
 * {@code guide.essentialcraft5.ui.fmt.group_sep}（英文是逗号，中文是空串 = 不分组）。不按客户端语言代码判断，
 * 因为手册内容会按分类回退到别的语言，跟着界面文字走才和正文一致。
 * <p>
 * 不带 {@code @OnlyIn}：两端都能用，GameTest 服务器上直接测。
 */
public final class NumberText {

    private NumberText() {
    }

    /**
     * 给整数部分每三位插一个分隔符
     *
     * <p>开头的正负号、小数点及后面的部分原样保留；整数部分不到 4 位（|n| &lt; 1000）时原样返回。
     * 不是数字开头的文本（{@code ?}、{@code ∞}）没有可分组的整数部分，也原样返回。</p>
     *
     * @param plain 十进制文本，例如 {@code -1000}、{@code 1234567.5}
     * @param sep   分隔符；空串或 null 表示不分组
     * @return 分组后的文本
     */
    public static String group(@Nullable String plain, @Nullable String sep) {
        if (plain == null || sep == null || sep.isEmpty()) {
            return plain;
        }
        int start = 0;
        if (!plain.isEmpty() && (plain.charAt(0) == '-' || plain.charAt(0) == '+')) {
            start = 1;
        }
        int end = start;
        while (end < plain.length() && plain.charAt(end) >= '0' && plain.charAt(end) <= '9') {
            end++;
        }
        int digits = end - start;
        if (digits < 4) {
            return plain;
        }
        StringBuilder sb = new StringBuilder(plain.length() + (digits - 1) / 3 * sep.length());
        sb.append(plain, 0, start);
        int head = digits % 3 == 0 ? 3 : digits % 3;
        sb.append(plain, start, start + head);
        for (int i = start + head; i < end; i += 3) {
            sb.append(sep).append(plain, i, i + 3);
        }
        sb.append(plain, end, plain.length());
        return sb.toString();
    }
}
