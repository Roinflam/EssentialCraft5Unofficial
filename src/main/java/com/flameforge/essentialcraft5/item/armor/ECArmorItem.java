package com.flameforge.essentialcraft5.item.armor;

import com.flameforge.essentialcraft5.item.attunement.Attunement;
import com.flameforge.essentialcraft5.client.armor.ECArmorClientExtensions;
import com.flameforge.essentialcraft5.datagen.ECItemModelProvider;
import com.flameforge.essentialcraft5.registry.ItemSpec;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.client.model.generators.ModelFile;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * 本模组盔甲的通用基类（第三批框架 G6）：贴图 / 自定义模型由 {@link ArmorLook} 给，额外属性（登高、移速）由 {@link ArmorBonus} 给。
 * 可染色的用子类 {@link DyeableECArmorItem}。
 * <pre>
 * // 风法师兜帽：原作 ItemArmorEC(ArmorMaterial.LEATHER, …, aType 3)，模型 ModelArmorEC，贴图 armorEC3Wind_0 / _1，耐久 setMaxDamage(128)
 * public static final RegistryObject&lt;ECArmorItem&gt; WIND_MAGE_HOOD = ECItems.register("wind_mage_hood",
 *         () -&gt; new ECArmorItem(ArmorMaterials.LEATHER, ArmorItem.Type.HELMET, new Item.Properties().durability(128),
 *                 ArmorLook.modelArmorEC("special/armor/armorec3wind_0", "special/armor/armorec3wind_1")),
 *         ECArmorItem.icon3d("item/misc/magicplate").tags(Tags.Items.ARMORS_HELMETS));
 * </pre>
 * <ul>
 *   <li>耐久：{@code Item.Properties#durability(n)} 优先于材料的耐久（ArmorItem 的构造器只在没设时用材料的值），照原作 setMaxDamage 写。</li>
 *   <li>穿在身上的模型：{@link ArmorLook#model()} 为空时就是原版人形盔甲；不为空时客户端按 id 换成登记的模型（姿势、部件可见性由 Forge 从原模型抄过来）。</li>
 *   <li>物品图标：原版形状的盔甲用 2D 贴图（{@code ItemSpec.generated("item/armor/wind_helmet")}）；自定义模型的盔甲原作靠
 *       CodeChickenLib 画 3D 图标，这里用 {@link #icon3d}（builtin/entity 模型 + BEWLR，显示变换同二批魔力防护套装）。</li>
 *   <li>MRU 抗性、看见 MRU：子类实现 {@code IMRUResistanceItem}、把物品加进 {@code essentialcraft5:mru_vision} 标签（同二批 MHSArmorItem）。</li>
 * </ul>
 * 二批的魔力防护套装（MHSArmorItem、MHSArmorRenderProperties）是这套设施的原型，行为不变、没有改成继承本类；新盔甲一律用本类。
 */
public class ECArmorItem extends ArmorItem {

    private final ArmorLook look;
    private final ArmorBonus bonus;
    @Nullable
    private volatile Multimap<Attribute, AttributeModifier> modifiers;

    public ECArmorItem(ArmorMaterial material, Type type, Properties properties, ArmorLook look) {
        this(material, type, properties, look, ArmorBonus.none());
    }

    public ECArmorItem(ArmorMaterial material, Type type, Properties properties, ArmorLook look, ArmorBonus bonus) {
        super(material, type, properties);
        this.look = look;
        this.bonus = bonus;
    }

    public ArmorLook look() {
        return look;
    }

    public ArmorBonus bonus() {
        return bonus;
    }

    /** 同调 3 阶起带附魔光泽（batch13.md §3.3.5） */
    @Override
    public boolean isFoil(ItemStack stack) {
        return super.isFoil(stack) || Attunement.hasGlint(stack);
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return look.texture(slot).toString();
    }

    /** 原版的护甲值 / 韧性 / 击退抗性，再加上 {@link ArmorBonus} 的修饰符（只在自己的部位） */
    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        Multimap<Attribute, AttributeModifier> base = super.getDefaultAttributeModifiers(slot);
        if (slot != getEquipmentSlot() || bonus.isEmpty()) {
            return base;
        }
        Multimap<Attribute, AttributeModifier> merged = modifiers;
        if (merged == null) {
            ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
            builder.putAll(base);
            builder.putAll(bonus.modifiers(getType()));
            merged = builder.build();
            modifiers = merged;
        }
        return merged;
    }

    /**
     * 这个方法在 Item 的构造器里就被调用（只在客户端、非 datagen），这时本类的字段还没赋值，
     * 所以渲染属性是共用的单例，渲染时才按物品栈现查 {@link #look()}。
     */
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(ECArmorClientExtensions.INSTANCE);
    }

    // ---------------------------------------------------------------- datagen

    /**
     * 自定义模型盔甲的物品规格：3D 图标（builtin/entity，由 {@link ECArmorClientExtensions} 的 BEWLR 画），显示变换照原版方块
     * （原作 ArmorRenderer 用 DEFAULT_BLOCK），和二批魔力防护套装一样。particleTexture 相对 textures/（盔甲贴图不在图集里，借一张物品贴图）。
     */
    public static ItemSpec icon3d(String particleTexture) {
        return ItemSpec.of((provider, item) -> builtinIconModel(provider, item, particleTexture));
    }

    private static void builtinIconModel(ECItemModelProvider provider, Item item, String particleTexture) {
        provider.getBuilder(provider.name(item))
                .parent(new ModelFile.UncheckedModelFile("builtin/entity"))
                .texture("particle", provider.tex(particleTexture))
                .transforms()
                .transform(ItemDisplayContext.GUI).rotation(30F, 225F, 0F).scale(0.625F).end()
                .transform(ItemDisplayContext.GROUND).translation(0F, 3F, 0F).scale(0.25F).end()
                .transform(ItemDisplayContext.FIXED).scale(0.5F).end()
                .transform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND).rotation(75F, 45F, 0F).translation(0F, 2.5F, 0F).scale(0.375F).end()
                .transform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND).rotation(0F, 45F, 0F).scale(0.4F).end()
                .transform(ItemDisplayContext.FIRST_PERSON_LEFT_HAND).rotation(0F, 225F, 0F).scale(0.4F).end()
                .end();
    }
}
