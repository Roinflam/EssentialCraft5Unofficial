package com.flameforge.essentialcraft5.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import java.util.function.DoubleSupplier;

/**
 * 破甲（第十批 10b，batch10.md §4.2.1）：每级护甲 −{@value #DEFAULT_ARMOR_PER_LEVEL}、盔甲韧性 −{@value #DEFAULT_TOUGHNESS_PER_LEVEL}
 * （破甲 III = 护甲 −6、韧性 −3）。
 * <p>
 * 用原版的效果属性修饰符做（{@code MobEffect#addAttributeModifier}）：效果加上、升级时原版自己挂上修饰符，到期、被牛奶清掉时自己摘掉，
 * 不用每刻改属性；两个修饰符的 UUID 固定，同一只生物身上永远只有一份。护甲属性本身最低是 0，扣不成负数。
 * <p>
 * 每级扣多少默认按上面两个常数；削减模块（第十批 10c）要做成配置时调 {@link #setPerLevel}，原版挂修饰符时按当时的值算
 * （已经挂着的不跟着变，到期或下一次升级才换成新值）。
 */
public class ArmorShredEffect extends MobEffect {

    public static final int COLOR = 0xB04A3A;
    /** 每级扣的护甲 */
    public static final double DEFAULT_ARMOR_PER_LEVEL = 2D;
    /** 每级扣的盔甲韧性 */
    public static final double DEFAULT_TOUGHNESS_PER_LEVEL = 1D;
    /** 护甲修饰符的 UUID（按名字算出来的固定值） */
    public static final UUID ARMOR_MODIFIER = UUID.nameUUIDFromBytes("essentialcraft5:armor_shred/armor".getBytes(StandardCharsets.UTF_8));
    /** 盔甲韧性修饰符的 UUID */
    public static final UUID TOUGHNESS_MODIFIER = UUID.nameUUIDFromBytes("essentialcraft5:armor_shred/toughness".getBytes(StandardCharsets.UTF_8));

    private static volatile DoubleSupplier armorPerLevel = () -> DEFAULT_ARMOR_PER_LEVEL;
    private static volatile DoubleSupplier toughnessPerLevel = () -> DEFAULT_TOUGHNESS_PER_LEVEL;

    public ArmorShredEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
        // 登记的数只是占位，实际挂上去的值由 getAttributeModifierValue 按每级的量算
        addAttributeModifier(Attributes.ARMOR, ARMOR_MODIFIER.toString(), -DEFAULT_ARMOR_PER_LEVEL, AttributeModifier.Operation.ADDITION);
        addAttributeModifier(Attributes.ARMOR_TOUGHNESS, TOUGHNESS_MODIFIER.toString(), -DEFAULT_TOUGHNESS_PER_LEVEL, AttributeModifier.Operation.ADDITION);
    }

    /** 等级 amplifier（0 = I 级）时这个修饰符的值：−每级的量 ×（amplifier + 1）；每级的量小于 0 按 0 算（不会反过来加护甲） */
    @Override
    public double getAttributeModifierValue(int amplifier, AttributeModifier modifier) {
        double perLevel = ARMOR_MODIFIER.equals(modifier.getId()) ? armorPerLevel() : toughnessPerLevel();
        return -perLevel * (double) (Math.max(0, amplifier) + 1);
    }

    /** 现在每级扣的护甲（不小于 0） */
    public static double armorPerLevel() {
        return Math.max(0D, armorPerLevel.getAsDouble());
    }

    /** 现在每级扣的盔甲韧性（不小于 0） */
    public static double toughnessPerLevel() {
        return Math.max(0D, toughnessPerLevel.getAsDouble());
    }

    /** 换掉每级扣的量的来源（例：读服务端配置）；传 null 退回默认常数 */
    public static void setPerLevel(DoubleSupplier armor, DoubleSupplier toughness) {
        armorPerLevel = Objects.requireNonNullElse(armor, () -> DEFAULT_ARMOR_PER_LEVEL);
        toughnessPerLevel = Objects.requireNonNullElse(toughness, () -> DEFAULT_TOUGHNESS_PER_LEVEL);
    }
}
