package com.flameforge.essentialcraft5.item.armor;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 可染色盔甲（第三批框架 G6）。对应原作 ItemGenericArmor（风之护甲）：1.12 自己实现了一套 display.color 的读写和 IItemColor，
 * 1.20 直接实现原版 {@link DyeableLeatherItem}，于是这些都是现成的：
 * <ul>
 *   <li>工作台染色：原版特殊配方 {@code minecraft:armor_dye}（ArmorDyeRecipe 认所有 DyeableLeatherItem），不用写配方 JSON。</li>
 *   <li>炼药锅洗掉颜色：本类在通用初始化时把所有实例登记进 {@code CauldronInteraction.WATER}（原版只登记了皮革四件）。</li>
 *   <li>穿在身上：HumanoidArmorLayer 对 DyeableLeatherItem 先按颜色画一遍 {@link ArmorLook} 的贴图，再原色画一遍「overlay」层；
 *       原作 overlay 返回透明的 null.png，这里同样返回 {@link #NO_OVERLAY}。</li>
 *   <li>物品图标上色：client/armor/ECArmorModels 自动给所有实例注册 ItemColor（第 0 层按颜色，其余层原色），模块不用写。</li>
 * </ul>
 * 和原版皮革的区别：没染过色时是<b>白色</b>（原作 getColor 默认 0xFFFFFF，原版皮革默认棕色 0xA06540），所以贴图本身的颜色原样显示。
 * <pre>
 * public static final RegistryObject&lt;DyeableECArmorItem&gt; WIND_BOOTS = ECItems.register("wind_boots",
 *         () -&gt; new DyeableECArmorItem(WIND_MATERIAL, ArmorItem.Type.BOOTS, new Item.Properties(),
 *                 ArmorLook.vanilla("special/armor/wind_layer_1", "special/armor/wind_layer_2"),
 *                 ArmorBonus.of().add(ForgeMod.STEP_HEIGHT_ADDITION, 0.6D, AttributeModifier.Operation.ADDITION)),
 *         ItemSpec.generated("item/armor/wind_boots").tags(Tags.Items.ARMORS_BOOTS));
 * </pre>
 */
public class DyeableECArmorItem extends ECArmorItem implements DyeableLeatherItem {

    /** 没染过色时的颜色（白 = 不改贴图颜色），原作 ItemGenericArmor.getColor */
    public static final int DEFAULT_COLOR = 0xFFFFFF;
    /** overlay 层用的全透明贴图（原作 essentialcraft:textures/blocks/null.png） */
    public static final ResourceLocation NO_OVERLAY = EssentialCraft5.id("textures/block/null.png");

    public DyeableECArmorItem(ArmorMaterial material, Type type, Properties properties, ArmorLook look) {
        super(material, type, properties, look);
    }

    public DyeableECArmorItem(ArmorMaterial material, Type type, Properties properties, ArmorLook look, ArmorBonus bonus) {
        super(material, type, properties, look, bonus);
    }

    /** 物品栈上的颜色：display.color，没有就是 {@link #DEFAULT_COLOR} */
    public static int colorOf(ItemStack stack) {
        CompoundTag display = stack.getTagElement(TAG_DISPLAY);
        return display != null && display.contains(TAG_COLOR, Tag.TAG_ANY_NUMERIC) ? display.getInt(TAG_COLOR) : DEFAULT_COLOR;
    }

    @Override
    public int getColor(ItemStack stack) {
        return colorOf(stack);
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return "overlay".equals(type) ? NO_OVERLAY.toString() : super.getArmorTexture(stack, entity, slot, type);
    }

    /** 通用初始化：把所有可染色盔甲登记进炼药锅的「洗掉颜色」（原版的表不是线程安全的，放进主线程队列） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {

        private Setup() {
        }

        @SubscribeEvent
        public static void commonSetup(FMLCommonSetupEvent event) {
            event.enqueueWork(() -> {
                for (Item item : ForgeRegistries.ITEMS.getValues()) {
                    if (item instanceof DyeableECArmorItem) {
                        CauldronInteraction.WATER.put(item, CauldronInteraction.DYED_ITEM);
                    }
                }
            });
        }
    }
}
