package com.flameforge.essentialcraft5.item.armor;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.function.Supplier;

/**
 * 盔甲材料（第三批框架 G6）：四个部位的耐久、护甲值直接写数（原作大多用 setMaxDamage 按件覆盖耐久，不按原版「基础 × 系数」算）。
 * <pre>
 * // 风之护甲：耐久照原作 ItemsCore L240~253，护甲值按 batch3.md E4（原作 getAttributeModifiers 没调父类，护甲值实际是 0）
 * public static final ECArmorMaterial WIND = ECArmorMaterial.builder("wind")
 *         .durability(624, 1248, 936, 624)      // 头 / 胸 / 腿 / 靴
 *         .defense(4, 10, 7, 4)
 *         .enchantability(…)
 *         .sound(() -&gt; SoundEvents.ARMOR_EQUIP_LEATHER)
 *         .build();
 * </pre>
 * 名字自动加命名空间（{@code essentialcraft5:wind}）：原版只在默认贴图路径和纹饰里用到它，本模组的盔甲贴图由 {@link ArmorLook} 给。
 * 修复材料默认没有（原作的材料都没设 setRepairItem）。不需要自定义材料的（风法师套装用原版皮革）直接用 {@code ArmorMaterials.LEATHER}。
 */
public final class ECArmorMaterial implements ArmorMaterial {

    private final String name;
    private final int[] durability;
    private final int[] defense;
    private final int enchantability;
    private final Supplier<SoundEvent> sound;
    private final Supplier<Ingredient> repair;
    private final float toughness;
    private final float knockbackResistance;

    private ECArmorMaterial(Builder builder) {
        this.name = EssentialCraft5.id(builder.name).toString();
        this.durability = builder.durability.clone();
        this.defense = builder.defense.clone();
        this.enchantability = builder.enchantability;
        this.sound = builder.sound;
        this.repair = builder.repair;
        this.toughness = builder.toughness;
        this.knockbackResistance = builder.knockbackResistance;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    /** 头 0、胸 1、腿 2、靴 3（ArmorItem.Type 的声明顺序） */
    private static int index(ArmorItem.Type type) {
        return type.ordinal();
    }

    @Override
    public int getDurabilityForType(ArmorItem.Type type) {
        return durability[index(type)];
    }

    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return defense[index(type)];
    }

    @Override
    public int getEnchantmentValue() {
        return enchantability;
    }

    @Override
    public SoundEvent getEquipSound() {
        return sound.get();
    }

    @Override
    public Ingredient getRepairIngredient() {
        return repair.get();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public float getToughness() {
        return toughness;
    }

    @Override
    public float getKnockbackResistance() {
        return knockbackResistance;
    }

    public static final class Builder {

        private final String name;
        private int[] durability = {0, 0, 0, 0};
        private int[] defense = {0, 0, 0, 0};
        private int enchantability;
        private Supplier<SoundEvent> sound = () -> SoundEvents.ARMOR_EQUIP_GENERIC;
        private Supplier<Ingredient> repair = () -> Ingredient.EMPTY;
        private float toughness;
        private float knockbackResistance;

        private Builder(String name) {
            this.name = name;
        }

        /** 耐久：头盔、胸甲、护腿、靴子 */
        public Builder durability(int helmet, int chestplate, int leggings, int boots) {
            this.durability = new int[]{helmet, chestplate, leggings, boots};
            return this;
        }

        /** 护甲值：头盔、胸甲、护腿、靴子 */
        public Builder defense(int helmet, int chestplate, int leggings, int boots) {
            this.defense = new int[]{helmet, chestplate, leggings, boots};
            return this;
        }

        public Builder enchantability(int value) {
            this.enchantability = value;
            return this;
        }

        public Builder sound(Supplier<SoundEvent> sound) {
            this.sound = sound;
            return this;
        }

        /** 修复材料（lambda 在铁砧上用到时才调用，可以引用标签和别的模块的物品） */
        public Builder repair(Supplier<Ingredient> repair) {
            this.repair = repair;
            return this;
        }

        public Builder toughness(float toughness) {
            this.toughness = toughness;
            return this;
        }

        public Builder knockbackResistance(float knockbackResistance) {
            this.knockbackResistance = knockbackResistance;
            return this;
        }

        public ECArmorMaterial build() {
            return new ECArmorMaterial(this);
        }
    }
}
