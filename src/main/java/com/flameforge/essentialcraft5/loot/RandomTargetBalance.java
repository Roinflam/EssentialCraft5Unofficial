package com.flameforge.essentialcraft5.loot;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.item.GlitchyCoreItem;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
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
 * 战利品函数 {@code essentialcraft5:random_target_balance}（第十批 10c，batch10.md §5.10.1）：给故障核心写一个随机的目标平衡值
 * （NBT {@code TargetBalance}，{@code general.inquisitorium.balance_min ~ balance_max} 均匀随机、两位小数，见 {@link GlitchyCoreItem}）。
 * <pre>
 * { "function": "essentialcraft5:random_target_balance" }
 * </pre>
 * 上下限读服务端配置，所以 JSON 里没有字段；不是故障核心的物品原样返回。四张全息投影表和审判庭哨兵的表用它（tools/gen/hoanna_loot.py）。
 * 类型在 {@link TargetBalanceRegistration} 里用 RegisterEvent 注册（同 {@link FortuneScaledCount}；内部类名带前缀，同包的事件处理器不能撞名）。
 */
public class RandomTargetBalance extends LootItemConditionalFunction {

    public static final ResourceLocation ID = EssentialCraft5.id("random_target_balance");
    public static final LootItemFunctionType TYPE = new LootItemFunctionType(new Serializer());

    RandomTargetBalance(LootItemCondition[] conditions) {
        super(conditions);
    }

    /** datagen / 测试用 */
    public static LootItemConditionalFunction.Builder<?> roll() {
        return simpleBuilder(RandomTargetBalance::new);
    }

    @Override
    public LootItemFunctionType getType() {
        return TYPE;
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        if (stack.getItem() instanceof GlitchyCoreItem) {
            GlitchyCoreItem.setTargetBalance(stack, GlitchyCoreItem.rollTarget(context.getRandom()));
        }
        return stack;
    }

    public static class Serializer extends LootItemConditionalFunction.Serializer<RandomTargetBalance> {

        @Override
        public RandomTargetBalance deserialize(JsonObject json, JsonDeserializationContext context, LootItemCondition[] conditions) {
            return new RandomTargetBalance(conditions);
        }
    }

    /** 把类型登记进原版的战利品函数注册表（Forge 自动发现，不用接线） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class TargetBalanceRegistration {

        private TargetBalanceRegistration() {
        }

        @SubscribeEvent
        public static void registerTargetBalance(RegisterEvent event) {
            event.register(Registries.LOOT_FUNCTION_TYPE, ID, () -> TYPE);
        }
    }
}
