package com.flameforge.essentialcraft5.api;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 海纳尔环境抗性（第十三批 13b，batch13.md §4.4「虚空」、§8.2「海纳尔新环境的抗性」）：玩家对某一种海纳尔天灾惩罚的抵挡程度，0~1。
 * 0 = 照常挨，1 = 完全不受影响；惩罚按 {@code ×(1 − 抵挡)} 结算（{@link #remaining}）。
 * <p>
 * 危害 id（{@link #SUN_ARRAY}、{@link #QUAKE}、{@link #FUME}）对应 {@code hoanna/HoannaHazards} 里现有的三种惩罚：
 * <ul>
 *   <li>{@code essentialcraft5:sun_array}：日阵露天的灼烧伤害（含随后着火烧掉的血）；</li>
 *   <li>{@code essentialcraft5:quake}：地震的踉跄、强震推力；</li>
 *   <li>{@code essentialcraft5:fume}：毒雾给的中毒（待在雾里中毒的时间、离开后的余毒都按比例减）。</li>
 * </ul>
 * 辐射不在这里：辐射走 {@link MRUResistance} 原有的抗性 / 易伤，不重复算。
 * <p>
 * 谁给抵挡由各模块登记 {@link Source}（虚空套装全套在 {@code registry/ECVoidArmor.init} 登记）；几个来源取最大，不相加，结果夹到 0~1。
 * 只在服务端结算时问（惩罚都在服务端算），客户端调也不出错（各来源只读装备）。以后新的海纳尔危害照样起一个 id，惩罚那边问 {@link #level}。
 */
public final class HoannaWard {

    /** 日阵露天灼烧 */
    public static final ResourceLocation SUN_ARRAY = EssentialCraft5.id("sun_array");
    /** 地震推人 */
    public static final ResourceLocation QUAKE = EssentialCraft5.id("quake");
    /** 毒雾中毒 */
    public static final ResourceLocation FUME = EssentialCraft5.id("fume");

    /** 一个抵挡来源：这名玩家对这种危害的抵挡（0~1；不管这种危害就返回 0） */
    @FunctionalInterface
    public interface Source {
        double level(Player player, ResourceLocation hazard);
    }

    private static final List<Source> SOURCES = new CopyOnWriteArrayList<>();

    private HoannaWard() {
    }

    /** 登记一个来源（mod 构造期调；同一个对象登记两次只算一次） */
    public static void register(Source source) {
        if (source != null && !SOURCES.contains(source)) {
            SOURCES.add(source);
        }
    }

    /** 这名玩家对这种危害的抵挡：各来源取最大，夹到 0~1；NaN、负数按 0 */
    public static double level(Player player, ResourceLocation hazard) {
        double best = 0D;
        for (Source source : SOURCES) {
            double value = source.level(player, hazard);
            if (value > best) {
                best = value;
            }
        }
        return Math.min(1D, best);
    }

    /** 惩罚剩下几成：1 − {@link #level}（0~1） */
    public static double remaining(Player player, ResourceLocation hazard) {
        return 1D - level(player, hazard);
    }
}
