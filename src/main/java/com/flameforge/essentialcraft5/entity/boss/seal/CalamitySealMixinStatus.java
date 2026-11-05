package com.flameforge.essentialcraft5.entity.boss.seal;

/**
 * 天灾封印注入点的核对结果（{@link CalamitySealMixinPlugin} 写，{@link CalamitySeal} 在加载完成时读）。不引用游戏类。
 */
public final class CalamitySealMixinStatus {

    /** EnchantmentHelper 要改到的方法数：getTagEnchantmentLevel、getEnchantments、setEnchantments */
    public static final int HELPER_EXPECTED = 3;
    /** ItemStack 要补上的方法数：getEnchantmentLevel、getAllEnchantments */
    public static final int STACK_EXPECTED = 2;

    static volatile boolean configLoaded;
    static volatile int helperMethods;
    static volatile int stackMethods;

    private CalamitySealMixinStatus() {
    }

    /** 两个目标类都改到位了 */
    public static boolean complete() {
        return configLoaded && helperMethods >= HELPER_EXPECTED && stackMethods >= STACK_EXPECTED;
    }

    /** 给日志和 GameTest 看的一句话 */
    public static String describe() {
        return "config=" + configLoaded + " helper=" + helperMethods + "/" + HELPER_EXPECTED + " stack=" + stackMethods + "/" + STACK_EXPECTED;
    }
}
