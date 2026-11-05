package com.flameforge.essentialcraft5.loot;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.item.bauble.MysticalAccessoryItem;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

/**
 * 战利品函数 {@code essentialcraft5:randomize_accessory}（第六批 6a M7）：箱子生成神秘饰品时当场抽形态、外观和三项抗性
 * （{@link MysticalAccessoryItem#roll}，随机数用战利品上下文的——箱子按自己的战利品种子开，同一个种子开出同样的饰品）。
 * 修原作「第一次右键才抽属性，从箱子里拿出来直接戴上是空饰品」。不是神秘饰品的物品原样放过。没有参数：
 * <pre>
 * { "type": "minecraft:item", "name": "essentialcraft5:mystical_accessory", "weight": 15,
 *   "functions": [ { "function": "essentialcraft5:randomize_accessory" } ] }
 * </pre>
 * 类型在 {@link AccessoryFunctionRegistration} 里用 RegisterEvent 注册（同 {@link FortuneScaledCount}；内部类名带前缀，同包的事件处理器不能撞名）。
 */
public class RandomizeAccessoryFunction extends LootItemConditionalFunction {

    public static final ResourceLocation ID = EssentialCraft5.id("randomize_accessory");
    public static final LootItemFunctionType TYPE = new LootItemFunctionType(new Serializer());

    RandomizeAccessoryFunction(LootItemCondition[] conditions) {
        super(conditions);
    }

    /** 代码建表用：{@code LootItem.lootTableItem(x).apply(RandomizeAccessoryFunction.randomize())} */
    public static LootItemConditionalFunction.Builder<?> randomize() {
        return simpleBuilder(RandomizeAccessoryFunction::new);
    }

    @Override
    public LootItemFunctionType getType() {
        return TYPE;
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        if (stack.getItem() instanceof MysticalAccessoryItem) {
            MysticalAccessoryItem.roll(stack, context.getRandom());
        }
        return stack;
    }

    public static class Serializer extends LootItemConditionalFunction.Serializer<RandomizeAccessoryFunction> {

        @Override
        public void serialize(JsonObject json, RandomizeAccessoryFunction function, JsonSerializationContext context) {
            super.serialize(json, function, context);
        }

        @Override
        public RandomizeAccessoryFunction deserialize(JsonObject json, JsonDeserializationContext context, LootItemCondition[] conditions) {
            return new RandomizeAccessoryFunction(conditions);
        }
    }

    /** 把类型登记进原版的战利品函数注册表（Forge 自动发现，不用接线） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class AccessoryFunctionRegistration {

        private AccessoryFunctionRegistration() {
        }

        @SubscribeEvent
        public static void register(RegisterEvent event) {
            event.register(Registries.LOOT_FUNCTION_TYPE, ID, () -> TYPE);
        }
    }
}
