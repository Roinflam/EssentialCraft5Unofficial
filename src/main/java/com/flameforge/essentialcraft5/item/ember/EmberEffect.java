package com.flameforge.essentialcraft5.item.ember;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.flameforge.essentialcraft5.item.ember.EmberKind.ACIDIC;
import static com.flameforge.essentialcraft5.item.ember.EmberKind.CHAOS;
import static com.flameforge.essentialcraft5.item.ember.EmberKind.COMMON;
import static com.flameforge.essentialcraft5.item.ember.EmberKind.CORRUPTED;
import static com.flameforge.essentialcraft5.item.ember.EmberKind.CRYSTAL;
import static com.flameforge.essentialcraft5.item.ember.EmberKind.DIVINE;
import static com.flameforge.essentialcraft5.item.ember.EmberKind.FLAME;
import static com.flameforge.essentialcraft5.item.ember.EmberKind.MAGICAL;

/**
 * 余烬元素剑的命中效果，以及 28 对余烬组合的效果表（原作 {@code ItemElementalSword.getEmberEffects} L344~431、命中 L93~145）。
 * <p>
 * 剑上 4 个余烬按<b>种类集合</b>两两配对（4 个同种 → 0 对，4 种各一 → 6 对），每对查一次 {@link #COMBOS}：
 * <ul>
 *   <li>伤害加成每对都算（原作循环用 {@code contains}，伤害值相同的两对只算一次；这里一一对应，4 种余烬最多 +36）。</li>
 *   <li>效果是集合：两对都出同一个效果也只生效一次（「反噬自身」可能由两对同时给出）。</li>
 * </ul>
 * 原作的三处笔误这里都按本意修了：腐化余烬写成 {@code "corruption"}（翻译键是 {@code embercorrupted}，7 对全失效）、
 * 「Greater Slow」对不上命中处的「Greater Slowness」、「Damage +11」超出 {@code i < 11} 的循环。
 * 原作命中处另有「Greater Weakness」「Greater Damage Boost」两项，没有任何组合产出，是死代码，不移植。
 * <p>
 * 原作「神圣 + 魔力」「神圣 + 燃烧」两对什么都不给，第十批（batch10.md D18 定为 A）补成 {@link #DISPEL 驱散}、{@link #CORROSION 腐蚀}
 * （驱散跟魔力透镜同一个机制，所以给带魔力余烬的那一对），余烬元素剑和余烬嵌片同用这张表。
 * <p>
 * 命中型效果的持续时间都是 {@link #DURATION}（原作 60 刻）。
 */
public enum EmberEffect {

    /** 目标缓慢 I */
    SLOWNESS(false),
    /** 目标缓慢 II（原作 Greater Slowness） */
    GREATER_SLOWNESS(false),
    /** 目标中毒 I */
    POISON(false),
    /** 目标中毒 II */
    GREATER_POISON(false),
    /** 持剑人自己挨 {@code ember_backlash} 2 点（原作 Damage Self） */
    DAMAGE_SELF(true),
    /** 引雷：视觉闪电 + 目标挨 5 点闪电伤害并着火 8 秒，持剑人抗火 II（见 ForgedElementalSwordItem） */
    LIGHTNING(false),
    /** 持剑人回 1 点血 */
    LIFESTEAL(true),
    /** 持剑人回 3 点血 */
    GREATER_LIFESTEAL(true),
    /** 目标虚弱 I */
    WEAKNESS(false),
    /** 持剑人力量 I（原作 Damage Boost） */
    STRENGTH(true),
    /** 持剑人迅捷 I（原作 Speed Boost） */
    SPEED(true),
    /** 持剑人迅捷 II（原作 Greater Speed Boost） */
    GREATER_SPEED(true),
    /** 目标饥饿 II */
    GREATER_HUNGER(false),
    /** 目标腐蚀（回不了血），第十批补给「神圣 + 燃烧」 */
    CORROSION(false),
    /** 剥掉目标一个有益的状态效果（同魔力透镜），第十批补给「神圣 + 魔力」 */
    DISPEL(false);

    /** 命中型效果的持续时间（刻），原作 60 */
    public static final int DURATION = 60;

    private final boolean self;

    EmberEffect(boolean self) {
        this.self = self;
    }

    /** 作用在持剑人自己身上（提示框分「对目标」「对自己」两行） */
    public boolean self() {
        return self;
    }

    /** 提示框里的名字（tools/lang/keys/ecemberforge.json） */
    public String translationKey() {
        return "tooltip.essentialcraft5.ember_effect." + name().toLowerCase(Locale.ROOT);
    }

    /**
     * 一对余烬的效果。a 的序号小于 b。
     *
     * @param damage  攻击伤害加成（没有为 0）
     * @param effects 命中型效果（没有为空）
     */
    public record Combo(EmberKind a, EmberKind b, int damage, Set<EmberEffect> effects) {

        public Combo {
            effects = effects.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(EnumSet.copyOf(effects));
        }

        public boolean isEmpty() {
            return damage == 0 && effects.isEmpty();
        }
    }

    /** 28 对，顺序照原作（按 {@link EmberKind} 的序号两两配对）。神圣 + 魔力、神圣 + 燃烧原作没有效果，第十批补成驱散、腐蚀 */
    public static final List<Combo> COMBOS = List.of(
            combo(ACIDIC, CHAOS, 4),
            combo(ACIDIC, COMMON, 2),
            combo(ACIDIC, CORRUPTED, 0, SLOWNESS),
            combo(ACIDIC, CRYSTAL, 6),
            combo(ACIDIC, DIVINE, 0, POISON),
            combo(ACIDIC, MAGICAL, 0, GREATER_POISON),
            combo(ACIDIC, FLAME, 8),
            combo(CHAOS, COMMON, 7, DAMAGE_SELF),
            combo(CHAOS, CORRUPTED, 0, GREATER_SLOWNESS),
            combo(CHAOS, CRYSTAL, 5),
            combo(CHAOS, DIVINE, 0, LIGHTNING),
            combo(CHAOS, MAGICAL, 6),
            combo(CHAOS, FLAME, 4),
            combo(COMMON, CORRUPTED, 0, WEAKNESS),
            combo(COMMON, CRYSTAL, 3),
            combo(COMMON, DIVINE, 0, STRENGTH),
            combo(COMMON, MAGICAL, 0, SPEED),
            combo(COMMON, FLAME, 11),
            combo(CORRUPTED, CRYSTAL, 0, LIFESTEAL),
            combo(CORRUPTED, DIVINE, 0, DAMAGE_SELF, GREATER_HUNGER),
            combo(CORRUPTED, MAGICAL, 0, GREATER_LIFESTEAL),
            combo(CORRUPTED, FLAME, 5),
            combo(CRYSTAL, DIVINE, 9),
            combo(CRYSTAL, MAGICAL, 7),
            combo(CRYSTAL, FLAME, 0, GREATER_SPEED),
            combo(DIVINE, MAGICAL, 0, DISPEL),
            combo(DIVINE, FLAME, 0, CORROSION),
            combo(MAGICAL, FLAME, 6));

    private static Combo combo(EmberKind a, EmberKind b, int damage, EmberEffect... effects) {
        Set<EmberEffect> set = effects.length == 0 ? EnumSet.noneOf(EmberEffect.class) : EnumSet.of(effects[0], effects);
        return new Combo(a, b, damage, set);
    }

    /** 两种余烬的那一对（顺序无所谓）；同种或不在表里返回 null */
    @Nullable
    public static Combo combo(EmberKind x, EmberKind y) {
        if (x == y) {
            return null;
        }
        EmberKind a = x.ordinal() < y.ordinal() ? x : y;
        EmberKind b = a == x ? y : x;
        for (Combo combo : COMBOS) {
            if (combo.a == a && combo.b == b) {
                return combo;
            }
        }
        return null;
    }

    /** 一组余烬种类两两配出的组合（按表的顺序） */
    public static List<Combo> combosOf(Collection<EmberKind> kinds) {
        Set<EmberKind> set = kinds.isEmpty() ? EnumSet.noneOf(EmberKind.class) : EnumSet.copyOf(kinds);
        List<Combo> out = new ArrayList<>(6);
        for (Combo combo : COMBOS) {
            if (set.contains(combo.a) && set.contains(combo.b)) {
                out.add(combo);
            }
        }
        return out;
    }
}
