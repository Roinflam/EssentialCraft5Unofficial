package com.flameforge.essentialcraft5.loot;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.resources.ResourceLocation;

/**
 * 海纳尔遗迹箱子的战利品表 id（第六批 6a，batch6a §6.2 第 4 条）。结构拼件只引用这两个常量，不自己拼字符串。
 * <p>
 * 表的内容归 ECVoid（{@code tools/gen/hoanna_loot.py} 生成 {@code data/essentialcraft5/loot_tables/chests/town_tower.json}、{@code catacombs.json}，
 * 两张内容相同，照原作 24 条、总权重 343、抽 6~17）。表里只能写已注册的物品：未知物品 id 会让整张表加载失败、箱子开出来是空的。
 * <ul>
 *   <li>{@link #TOWN_TOWER}：城镇废墟的塔顶那一圈，每座塔 4 个箱子（town_tower 拼件）。</li>
 *   <li>{@link #CATACOMBS}：古代墓穴出口房间 (2, 2, 2) 那一个箱子（下面垫虚空石）。</li>
 * </ul>
 * 现代矿井的矿车箱子用原版 {@code minecraft:chests/abandoned_mineshaft}，不在这里。
 */
public final class HoannaLoot {

    /** 城镇废墟塔顶的箱子 */
    public static final ResourceLocation TOWN_TOWER = EssentialCraft5.id("chests/town_tower");
    /** 古代墓穴出口房间的箱子 */
    public static final ResourceLocation CATACOMBS = EssentialCraft5.id("chests/catacombs");

    private HoannaLoot() {
    }
}
