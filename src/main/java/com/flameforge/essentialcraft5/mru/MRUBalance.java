package com.flameforge.essentialcraft5.mru;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * 平衡值（balance）的显示与颜色。范围 [0, 2]：1 纯净，&lt;1 冰冻，&gt;1 混沌。
 * 不含客户端类，双端都能用。
 */
public final class MRUBalance {

    public static final float PURE = 1F;
    public static final float MIN = 0F;
    public static final float MAX = 2F;

    public enum Kind {
        FROZEN(0x0000FF),
        PURE(0x00FFFF),
        CHAOS(0xFF0000);

        /** 原作 GuiBalanceState 的字色 */
        public final int textColor;

        Kind(int textColor) {
            this.textColor = textColor;
        }

        public Component label() {
            return Component.translatable("gui.essentialcraft5.balance." + name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    private MRUBalance() {
    }

    public static float clamp(float balance) {
        // NaN 当纯净处理，免得坏数据一路传染
        return Float.isNaN(balance) ? PURE : Mth.clamp(balance, MIN, MAX);
    }

    public static Kind kind(float balance) {
        return balance < PURE ? Kind.FROZEN : balance > PURE ? Kind.CHAOS : Kind.PURE;
    }

    /** 原作的写法：Float.toString 截到 6 个字符，再去掉小数点后多余的 0（至少留一位小数）。 */
    public static String format(float balance) {
        String str = Float.toString(balance);
        if (str.length() > 6) {
            str = str.substring(0, 6);
        }
        int end = str.length();
        while (end > 3 && str.charAt(end - 1) == '0') {
            end--;
        }
        return str.substring(0, end);
    }

    /** 「纯净: 1.0」这样的一行，界面和提示共用。 */
    public static Component describe(float balance) {
        return Component.translatable("gui.essentialcraft5.balance.format", kind(balance).label(), format(balance));
    }

    /**
     * MRU 粒子/光束的颜色（RGB 各 0~1），照搬原作 MRUTileStorage.doSpawnMRUParticles：
     * 纯净为青色，向冰冻方向渐变成蓝色，向混沌方向渐变成红色。
     */
    public static float[] rgb(float balance) {
        float r = 0F, g = 1F, b = 1F;
        if (balance < PURE) {
            float t = balance < 0.01F ? 0F : balance;
            r = 0F;
            g = t;
            b = 1F;
        } else if (balance > PURE) {
            float t = MAX - balance;
            if (t < 0.01F) {
                t = 0F;
            }
            r = 1F - t;
            g = t;
            b = t;
        }
        return new float[] {r, g, b};
    }
}
