package com.flameforge.essentialcraft5.item;

/**
 * 一阶「铺设魔杖」（tools/plan/builder_upgrade.md §2.1）：创世神之杖的入门版，只有填充模式，形状只有两点、铺面、直线，默认铺面；
 * 一次的格数、边长、距离、每刻格数都比二阶小（{@link BuilderTier#PAVER}，配置 {@code general.builder.paver.*}）。
 * 操作、取料、撤销和二阶完全一样，所以直接是填充模式的 {@link MagicalBuilderItem}：施工、准星下提示、魔杖键都认它。
 * 不能在工作台里换模式；放进创世神之杖的配方图样里升成二阶（NBT、MRU 整份带过去）。
 */
public class PavingWandItem extends MagicalBuilderItem {

    public PavingWandItem(Properties properties) {
        super(properties, BuilderMode.FILL);
    }
}
