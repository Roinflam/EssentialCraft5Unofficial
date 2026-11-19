package com.flameforge.essentialcraft5.item.attunement;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * 同调提示框里「n 阶：……」那几行由物品自己给，数按现在的配置填（batch13.md §3.3.5）。
 * <p>
 * 没实现这个接口的物品照语言键 {@code tooltip.<命名空间>.attunement.<家族>.stage_<n>} 原样写（不带参数，数只能写死在键里）。
 * 阶效果的数能在配置里改的家族（五套盔甲按 {@code general.gear_growth.<家族>} 的各阶数值、风之护甲的偏转间隔和空中跳次数、宅男套装的单价、余烬剑和裁决之刃的各项倍数）实现它，
 * 服主改了配置，提示框跟着变，不会和实际效果、同一个框里别的行对不上。语言键照旧放在 {@code stage_<n>} 下面，只是带了参数。
 * <p>
 * 两端都会调：客户端读到的是服务端同步来的配置。
 */
public interface AttunementStageLines {

    /**
     * 第 stage 阶效果那一行（不含前面的「n 阶：」）。这一阶在现在的配置下什么都不加（相关的几项都被关掉）时返回 null，那一行不写。
     */
    @Nullable
    Component attunementStageLine(ItemStack stack, int stage);

    /** 数字：最多两位小数，去掉末尾的 0（3.5、0.5、14） */
    static String number(double value) {
        DecimalFormat format = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));
        return format.format(value);
    }

    /** 刻数写成秒（80 → 4、30 → 1.5） */
    static String seconds(int ticks) {
        return number(ticks / 20D);
    }

    /** 比例写成百分数里的数（0.25 → 25、0.125 → 12.5），语言键里自己写 %% */
    static String percent(double fraction) {
        return number(fraction * 100D);
    }
}
