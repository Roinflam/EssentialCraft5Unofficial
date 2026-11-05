package com.flameforge.essentialcraft5.item.armor;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * 按服务端配置给盔甲属性（第八批框架 8-Fb，batch8.md §3.2 第 5 条）：从第七批虚空套装的 VoidArmorItem 抽出来，虚空、宅男、审判庭套装共用。
 * 每件盔甲持有一个实例，在 {@code getDefaultAttributeModifiers(自己的部位)} 里把「现在的配置算出来的这一件的数」交给 {@link #modifiers}：
 * <pre>
 * private final ConfiguredArmorStats stats = new ConfiguredArmorStats(type, "void_armor", false, bonus);
 *
 * public Multimap&lt;Attribute, AttributeModifier&gt; getDefaultAttributeModifiers(EquipmentSlot slot) {
 *     if (slot != getEquipmentSlot()) return super.getDefaultAttributeModifiers(slot);
 *     return stats.modifiers(new ConfiguredArmorStats.Piece(护甲, 韧性, 击退抗性, 最大生命, 移速));
 * }
 * </pre>
 * <ul>
 *   <li>护甲 / 韧性 / 击退抗性用原版同部位的修饰符 UUID（{@link #vanillaArmorModifierId}），别的模组按 UUID 认得出这是盔甲本身的数值；
 *       最大生命、移速用按「key + 部位」固定生成的 UUID（四件各一个，能叠加；换装时原版按 UUID 去掉旧的）。</li>
 *   <li>数额为 0 也照放修饰符（原版提示框不显示 0 的修饰符）：原版脱装时按旧物品「现在」算出的表、按 UUID 去掉修饰符，
 *       穿着的时候服主把配置热改成 0，表里要是没有这一条，穿上时加的那条就去不掉，一直留到重新登录。
 *       移速只在构造时说了「这一套有可配置的移速」才放（{@code configuredSpeed}），四件都放，没有移速的部位放 0。</li>
 *   <li>同一套数（{@link Piece} 相等）复用上次建好的表，配置变了才重建；{@link ArmorBonus} 的修饰符（固定的登高、移速）接在后面。</li>
 * </ul>
 * 改了配置以后，已经穿在身上的要换一次装才更新（原版只在装备变化时重算属性）。
 * 换装修饰符不进存档，重登、从末地回来时超出基础上限的当前生命由 {@link ArmorHealthRestore} 记下再恢复。
 */
public final class ConfiguredArmorStats {

    /** 一件盔甲现在的数：护甲、韧性、击退抗性、最大生命（生命值，2 = 一颗心）、移速（ADDITION，基础 0.1） */
    public record Piece(double armor, double toughness, double knockbackResistance, double maxHealth, double movementSpeed) {

        /** 没有移速的一件 */
        public static Piece of(double armor, double toughness, double knockbackResistance, double maxHealth) {
            return new Piece(armor, toughness, knockbackResistance, maxHealth, 0D);
        }
    }

    private record Cached(Piece piece, Multimap<Attribute, AttributeModifier> modifiers) {
    }

    private static final Map<ArmorItem.Type, UUID> VANILLA_IDS = new EnumMap<>(ArmorItem.Type.class);

    private final ArmorItem.Type type;
    private final String key;
    private final boolean configuredSpeed;
    private final ArmorBonus bonus;
    @Nullable
    private volatile Cached cached;

    /**
     * @param type            这件的部位
     * @param key             生命 / 移速修饰符 UUID 和名字用的套装名（如 {@code void_armor}），同一套四件用同一个，不同套装不能重
     * @param configuredSpeed 这一套有没有可配置的移速（有就四件都放移速修饰符）
     * @param bonus           固定的额外修饰符（登高之类），接在配置给的后面；没有就 {@link ArmorBonus#none()}
     */
    public ConfiguredArmorStats(ArmorItem.Type type, String key, boolean configuredSpeed, ArmorBonus bonus) {
        this.type = type;
        this.key = key;
        this.configuredSpeed = configuredSpeed;
        this.bonus = bonus;
    }

    public ArmorItem.Type type() {
        return type;
    }

    /** 这一件按 piece 给的修饰符表（只用于这件自己的部位）。同一套数复用缓存 */
    public Multimap<Attribute, AttributeModifier> modifiers(Piece piece) {
        Cached current = cached;
        if (current == null || !current.piece().equals(piece)) {
            current = new Cached(piece, build(piece));
            cached = current;
        }
        return current.modifiers();
    }

    private Multimap<Attribute, AttributeModifier> build(Piece piece) {
        UUID armorId = vanillaArmorModifierId(type);
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        builder.put(Attributes.ARMOR, new AttributeModifier(armorId, "Armor modifier", piece.armor(), AttributeModifier.Operation.ADDITION));
        builder.put(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(armorId, "Armor toughness", piece.toughness(), AttributeModifier.Operation.ADDITION));
        builder.put(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(armorId, "Armor knockback resistance",
                piece.knockbackResistance(), AttributeModifier.Operation.ADDITION));
        builder.put(Attributes.MAX_HEALTH, new AttributeModifier(healthModifierId(type, key), "essentialcraft5 " + key + " health",
                piece.maxHealth(), AttributeModifier.Operation.ADDITION));
        if (configuredSpeed) {
            builder.put(Attributes.MOVEMENT_SPEED, new AttributeModifier(speedModifierId(type, key), "essentialcraft5 " + key + " speed",
                    piece.movementSpeed(), AttributeModifier.Operation.ADDITION));
        }
        if (!bonus.isEmpty()) {
            builder.putAll(bonus.modifiers(type));
        }
        return builder.build();
    }

    /** 最大生命修饰符的 UUID：按套装名和部位固定（虚空套装的 key 是 {@code void_armor}，和第七批写死的那个一样） */
    public static UUID healthModifierId(ArmorItem.Type type, String key) {
        return ArmorBonus.uuid(type, "essentialcraft5:" + key + "/max_health");
    }

    /** 可配置移速修饰符的 UUID：按套装名和部位固定（和 {@link ArmorBonus} 的固定移速不是同一个，可以同时有） */
    public static UUID speedModifierId(ArmorItem.Type type, String key) {
        return ArmorBonus.uuid(type, "essentialcraft5:" + key + "/movement_speed");
    }

    /**
     * 原版这个部位盔甲修饰符的 UUID（{@code ArmorItem.ARMOR_MODIFIER_UUID_PER_TYPE} 是私有的，从同部位的原版铁盔甲的修饰符里取，
     * 所有原版盔甲同部位都是这一个；取不到时退回按部位生成的固定 UUID）
     */
    public static UUID vanillaArmorModifierId(ArmorItem.Type type) {
        synchronized (VANILLA_IDS) {
            UUID id = VANILLA_IDS.get(type);
            if (id == null) {
                Item vanilla = switch (type) {
                    case HELMET -> Items.IRON_HELMET;
                    case CHESTPLATE -> Items.IRON_CHESTPLATE;
                    case LEGGINGS -> Items.IRON_LEGGINGS;
                    case BOOTS -> Items.IRON_BOOTS;
                };
                for (AttributeModifier modifier : vanilla.getDefaultAttributeModifiers(type.getSlot()).get(Attributes.ARMOR)) {
                    id = modifier.getId();
                    break;
                }
                if (id == null) {
                    id = ArmorBonus.uuid(type, "minecraft:generic.armor/ADDITION");
                }
                VANILLA_IDS.put(type, id);
            }
            return id;
        }
    }
}
