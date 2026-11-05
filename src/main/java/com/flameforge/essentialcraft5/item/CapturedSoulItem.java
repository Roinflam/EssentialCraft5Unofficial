package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.registry.ECDemonic;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 被俘灵魂（原作 ItemCapturedSoul，注册名 soul → captured_soul）：恶魔交易的货币，灵魂刻刀（第七批 M3）收来。
 * <p>
 * 一个物品 + NBT {@code entity}（实体注册名），同一种实体的灵魂才叠在一起，一组 64（原作 setMaxDamage(0)、默认堆叠）。
 * 显示名「被俘灵魂（村民）」（{@code item.essentialcraft5.captured_soul.with_entity}，原作用 I18n 拼「Captured Soul - 名字」，
 * 实体找不到时拼出「 - 」）；实体没注册时括号里写注册名，没有 {@code entity} 时就是物品本名。
 * 交易比对只看物品和 {@code entity} 字符串（{@link #matches}）：改过名、带别的 NBT 照样认（原作要求 NBT 完全相同，改过名就不收）。
 * <p>
 * 创造栏列村民、末影人、风法师三份（原作 DemonTrade.ALL_MOBS；只列已注册的实体，风法师是本模组的 {@code essentialcraft5:wind_mage}）。
 * 第七批框架 7-F 先注册它（batch7 §3.1 第 5 条），恶魔交易（M1）和灵魂刻刀（M3）都用这里的方法，不互相引用。
 */
public class CapturedSoulItem extends Item implements CreativeVariants {

    /** NBT 里存实体注册名的键（照原作） */
    public static final String ENTITY_TAG = "entity";
    /** 带实体名的显示名：「被俘灵魂（%s）」 */
    public static final String NAME_WITH_ENTITY = "item.essentialcraft5.captured_soul.with_entity";
    /** 提示一行：拿去和恶魔交易 */
    public static final String TOOLTIP_KEY = "tooltip.essentialcraft5.captured_soul";

    /** 创造栏列出的三种（原作 DemonTrade.ALL_MOBS 的村民、末影人、风法师），没注册的跳过 */
    private static final List<ResourceLocation> CREATIVE_ENTITIES = List.of(
            ResourceLocation.withDefaultNamespace("villager"),
            ResourceLocation.withDefaultNamespace("enderman"),
            EssentialCraft5.id("wind_mage"));

    public CapturedSoulItem(Properties properties) {
        super(properties);
    }

    /** 某种实体的灵魂 {@code count} 个 */
    public static ItemStack of(EntityType<?> type, int count) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
        return id == null ? ItemStack.EMPTY : of(id, count);
    }

    /** 按实体注册名造灵魂（不查这个实体存不存在；交易池、展示厅用） */
    public static ItemStack of(ResourceLocation entityId, int count) {
        ItemStack stack = new ItemStack(ECDemonic.CAPTURED_SOUL.get(), count);
        stack.getOrCreateTag().putString(ENTITY_TAG, entityId.toString());
        return stack;
    }

    /** 灵魂里记的实体注册名；不是被俘灵魂、没有记、写坏了都返回 null */
    @Nullable
    public static ResourceLocation entityId(ItemStack stack) {
        if (!stack.is(ECDemonic.CAPTURED_SOUL.get())) {
            return null;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(ENTITY_TAG, Tag.TAG_STRING)) {
            return null;
        }
        return ResourceLocation.tryParse(tag.getString(ENTITY_TAG));
    }

    /** 是不是这种实体的灵魂：只比物品和 {@code entity}，改过名、带别的 NBT 照样算 */
    public static boolean matches(ItemStack stack, EntityType<?> type) {
        ResourceLocation id = entityId(stack);
        return id != null && id.equals(ForgeRegistries.ENTITY_TYPES.getKey(type));
    }

    /** 灵魂里记的实体名（翻译过的）；实体没注册时是注册名的字面文字，没有记时返回 null */
    @Nullable
    public static Component entityName(ItemStack stack) {
        ResourceLocation id = entityId(stack);
        if (id == null) {
            return null;
        }
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.containsKey(id) ? ForgeRegistries.ENTITY_TYPES.getValue(id) : null;
        return type != null ? type.getDescription() : Component.literal(id.toString());
    }

    @Override
    public Component getName(ItemStack stack) {
        Component entity = entityName(stack);
        return entity == null ? super.getName(stack) : Component.translatable(NAME_WITH_ENTITY, entity);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(TOOLTIP_KEY).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public List<ItemStack> creativeVariants() {
        List<ItemStack> out = new ArrayList<>();
        for (ResourceLocation id : CREATIVE_ENTITIES) {
            if (ForgeRegistries.ENTITY_TYPES.containsKey(id)) {
                out.add(of(id, 1));
            }
        }
        return out;
    }
}
