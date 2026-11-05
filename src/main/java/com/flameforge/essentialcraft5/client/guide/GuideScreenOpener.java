package com.flameforge.essentialcraft5.client.guide;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

/**
 * 打开研究手册的入口（物品右键、查阅都经 {@link GuideClient} 调到这里）
 */
@OnlyIn(Dist.CLIENT)
public final class GuideScreenOpener {

    private GuideScreenOpener() {}

    /** 打开手册，回到上次看的位置；完整开场 */
    public static void open() {
        open(null);
    }

    /**
     * 打开手册，回到上次看的位置；完整开场
     *
     * @param parent 关掉手册后回到的界面，可为 null
     */
    public static void open(@Nullable Screen parent) {
        Minecraft.getInstance().setScreen(new GuideScreen(parent, null, -1, false));
    }

    /**
     * 直接翻到某一篇的某一块（查阅用）：短开场，那一块滚到上方并亮一下，关掉后回到 parent
     *
     * @param parent   关掉手册后回到的界面（从物品栏查阅时就是物品栏），可为 null
     * @param entryKey 条目键（分类/条目）
     * @param block    块序号（{@code GuideView.EntryView#blocks()} 里的下标），-1 表示篇首
     */
    public static void openAt(@Nullable Screen parent, String entryKey, int block) {
        Minecraft.getInstance().setScreen(new GuideScreen(parent, entryKey, block, true));
    }

    /**
     * 打开手册、走短开场，但不指定位置（手持手册潜行右键、准星什么都没对着时）
     */
    public static void openQuick(@Nullable Screen parent) {
        Minecraft.getInstance().setScreen(new GuideScreen(parent, null, -1, true));
    }
}
