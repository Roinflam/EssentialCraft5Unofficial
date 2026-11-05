package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.registry.ECTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.TierSortingRegistry;

import java.util.List;

/**
 * 风之工具的材料（第三批框架 G7），对应原作 ItemsCore L22 的 ToolMaterial「windElemental」：
 * 挖掘等级 11、耐久 15684、效率 15、攻击加成 6、附魔能力 42。
 * <p>
 * 挖掘等级：原作元素（elemental）是 6、风是 11，都高过所有原版材料。{@link TierSortingRegistry} 里排在
 * {@link ElementalTier#ELEMENTAL}（它又排在下界合金之后）<b>之后</b>，所以风之工具能挖一切元素工具能挖的方块，
 * 另外能挖 {@code essentialcraft5:needs_wind_tool}（{@link ECTags#NEEDS_WIND_TOOL}，框架建的空标签，目前没有方块要求它）。
 * <p>
 * 原作没有设修复材料（addToolMaterial 之后没调 setRepairItem），铁砧只能两把合一，这里照旧。
 * <p>
 * 用法（ECWindGear）：{@code new PickaxeItem(WindTier.WIND, 1, -2.8F, new Item.Properties())}，攻击数值的换算照 ECGear 的注释。
 * 排序在 {@link com.flameforge.essentialcraft5.registry.ECRegistries#register} 里调 {@link #init()} 时登记（模组构造阶段），
 * 模块不用再调。
 */
public enum WindTier implements Tier {

    WIND(11, 15684, 15F, 6F, 42, ECTags.NEEDS_WIND_TOOL);

    static {
        TierSortingRegistry.registerTier(WIND, EssentialCraft5.id("wind"), List.of(ElementalTier.ELEMENTAL), List.of());
    }

    private final int level;
    private final int uses;
    private final float speed;
    private final float attackDamageBonus;
    private final int enchantmentValue;
    private final TagKey<Block> tag;

    WindTier(int level, int uses, float speed, float attackDamageBonus, int enchantmentValue, TagKey<Block> tag) {
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
    public TagKey<Block> getTag() {
        return tag;
    }
}
