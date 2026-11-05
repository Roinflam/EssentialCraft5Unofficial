package com.flameforge.essentialcraft5.block.hoanna;

import net.minecraft.world.level.block.Block;

/**
 * 渊盐结壳 {@code abyssal_salt_crust}（第十三批 13d，batch13.md §6.1 海纳尔之海、§6.3「渊盐」）：海底的盐壳，硬度 0.8、铲子挖。
 * 掉渊盐 2~4（时运按原版矿石公式加），精准采集掉本体，受爆炸衰减（掉落表由 {@code ECHoannaSurvey} 的方块规格生成）。
 * 海底圆盘地物 {@code abyssal_salt_disk} 由 13d-H1b 按注册名摆。方块本身没有行为。
 */
public class AbyssalSaltCrustBlock extends Block {

    public AbyssalSaltCrustBlock(Properties properties) {
        super(properties);
    }
}
