package com.flameforge.essentialcraft5.item.armor;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ArmorItem;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 盔甲穿上时额外给的属性修饰符（第三批框架 G6），和护甲值一起由原版的属性系统加上、脱下自动去掉，不要每 tick 改属性。
 * <pre>
 * // 风之靴子：登高到 1.2 格。原作每 tick 写 stepHeight（覆盖别的模组，还用永不清理的 HashMap 记旧值）
 * ArmorBonus.of().add(ForgeMod.STEP_HEIGHT_ADDITION, 0.6D, AttributeModifier.Operation.ADDITION)
 * // 风之胸甲：移速 +0.075（原作 ItemGenericArmor L103，operation 0 = ADDITION，基础 0.1）
 * ArmorBonus.of().add(() -&gt; Attributes.MOVEMENT_SPEED, 0.075D, AttributeModifier.Operation.ADDITION)
 * </pre>
 * 属性用 Supplier 给（{@code ForgeMod.STEP_HEIGHT_ADDITION} 本身就是 RegistryObject），修饰符在第一次查询时才建。
 * UUID 按「部位 + 属性注册名 + 运算方式」固定生成（同一部位同时只穿一件，不会撞；不同部位不同 UUID，四件的同种加成可以叠加）。
 * 同一属性、同一运算方式写两次会合并成一条（数值相加），避免同 UUID 修饰符加两次时原版抛异常。
 * <p>
 * 登高：Forge 的 {@code STEP_HEIGHT_ADDITION} 属性加在原版的 maxUpStep 上（IForgeEntity#getStepHeight），玩家基础 0.6，+0.6 = 1.2。
 */
public final class ArmorBonus {

    private static final ArmorBonus NONE = new ArmorBonus(List.of());

    private record Entry(Supplier<? extends Attribute> attribute, double amount, AttributeModifier.Operation operation) {
    }

    private final List<Entry> entries;

    private ArmorBonus(List<Entry> entries) {
        this.entries = entries;
    }

    public static ArmorBonus none() {
        return NONE;
    }

    /** 空的，接着 {@link #add} */
    public static ArmorBonus of() {
        return NONE;
    }

    /** 返回加了一条的新对象（本对象不变） */
    public ArmorBonus add(Supplier<? extends Attribute> attribute, double amount, AttributeModifier.Operation operation) {
        List<Entry> list = new ArrayList<>(entries);
        list.add(new Entry(Objects.requireNonNull(attribute), amount, Objects.requireNonNull(operation)));
        return new ArmorBonus(List.copyOf(list));
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** 合并时的累加器：同一属性 + 同一运算方式一条 */
    private static final class Sum {
        final Attribute attribute;
        final AttributeModifier.Operation operation;
        double amount;

        Sum(Attribute attribute, AttributeModifier.Operation operation) {
            this.attribute = attribute;
            this.operation = operation;
        }
    }

    /** 这件（type 部位）穿上时的修饰符。要在注册表建好之后调 */
    public Multimap<Attribute, AttributeModifier> modifiers(ArmorItem.Type type) {
        Map<String, Sum> merged = new LinkedHashMap<>();
        for (Entry entry : entries) {
            Attribute attribute = entry.attribute().get();
            ResourceLocation id = ForgeRegistries.ATTRIBUTES.getKey(attribute);
            String key = (id == null ? attribute.getDescriptionId() : id.toString()) + "/" + entry.operation().name();
            merged.computeIfAbsent(key, k -> new Sum(attribute, entry.operation())).amount += entry.amount();
        }
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        merged.forEach((key, sum) -> builder.put(sum.attribute,
                new AttributeModifier(uuid(type, key), "essentialcraft5 armor bonus", sum.amount, sum.operation)));
        return builder.build();
    }

    /** 固定的 UUID：同一部位、同一属性和运算方式永远一样（换装时原版按 UUID 去掉旧修饰符，随机 UUID 会越叠越多） */
    public static UUID uuid(ArmorItem.Type type, String attributeAndOperation) {
        String seed = EssentialCraft5.MODID + ":armor_bonus/" + type.getName() + "/" + attributeAndOperation;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }
}
