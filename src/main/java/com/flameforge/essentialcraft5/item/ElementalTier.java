package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.TierSortingRegistry;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 元素工具的两种材料，对应原作 ItemsCore L20-21 的 ToolMaterial：
 * <ul>
 *   <li>{@link #ELEMENTAL}（elemental）：挖掘等级 6、耐久 3568、效率 15、攻击加成 5、附魔能力 36。
 *       原作等级 6 高过所有原版材料，这里用 {@link TierSortingRegistry} 排在下界合金之后；
 *       对应方块标签 {@code essentialcraft5:needs_elemental_tool}（目前是空的，没有方块要求它）。</li>
 *   <li>{@link #WEAKENED}（weakElemental）：挖掘等级 3、耐久 754、效率 7、攻击加成 3（原作 2.5，见下）、附魔能力 36。
 *       和钻石同级：排在钻石之后、下界合金之前，自己不带方块标签，所以能挖的方块和钻石完全一样。</li>
 * </ul>
 * 原作两种材料都没有设修复材料（EnumHelper.addToolMaterial 之后没调 setRepairItem），铁砧只能两把合一，这里照旧。
 * <p>
 * 第十三批 13a（batch13.md §4.6，早期武器修补）：弱化元素的攻击加成 2.5 → 3，弱化元素剑 6.5 → 7，和钻石剑持平（斧 8.5 → 9，别的工具同样 +0.5）。
 * <p>
 * 类加载时登记排序：{@link com.flameforge.essentialcraft5.registry.ECGear#init()} 会调 {@link #init()}，保证在模组构造阶段就登记好
 * （排序本身在服务端加载数据、客户端进服时才算）。
 */
public enum ElementalTier implements Tier {

    WEAKENED(3, 754, 7F, 3F, 36, null),
    ELEMENTAL(6, 3568, 15F, 5F, 36, BlockTags.create(EssentialCraft5.id("needs_elemental_tool")));

    static {
        TierSortingRegistry.registerTier(WEAKENED, EssentialCraft5.id("weak_elemental"), List.of(Tiers.DIAMOND), List.of(Tiers.NETHERITE));
        TierSortingRegistry.registerTier(ELEMENTAL, EssentialCraft5.id("elemental"), List.of(Tiers.NETHERITE), List.of());
    }

    private final int level;
    private final int uses;
    private final float speed;
    private final float attackDamageBonus;
    private final int enchantmentValue;
    @Nullable
    private final TagKey<Block> tag;

    ElementalTier(int level, int uses, float speed, float attackDamageBonus, int enchantmentValue, @Nullable TagKey<Block> tag) {
        this.level = level;
        this.uses = uses;
        this.speed = speed;
        this.attackDamageBonus = attackDamageBonus;
        this.enchantmentValue = enchantmentValue;
        this.tag = tag;
    }

    /** 触发类加载（登记排序） */
    public static void init() {
    }

    @Override
    public int getUses() {
        return uses;
    }

    @Override
    public float getSpeed() {
        return speed;
    }

    @Override
    public float getAttackDamageBonus() {
        return attackDamageBonus;
    }

    /** 只给没接 TierSortingRegistry 的旧代码看；挖掘判定走排序 */
    @Override
    @Deprecated
    public int getLevel() {
        return level;
    }

    @Override
    public int getEnchantmentValue() {
        return enchantmentValue;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.EMPTY;
    }

    @Override
    @Nullable
    public TagKey<Block> getTag() {
        return tag;
    }
}
