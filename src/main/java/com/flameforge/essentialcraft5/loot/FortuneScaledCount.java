package com.flameforge.essentialcraft5.loot;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSyntaxException;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

import java.util.Set;

/**
 * 战利品函数 {@code essentialcraft5:fortune_scaled_count}：数量 = min + 随机整数[0, spread × (时运 + 1))。
 * <p>
 * 原作的矿石在 getDrops 里写死了这个公式（元素蕴魔矿石 {@code nextInt(2×(时运+1))+1}，米斯莱矿石 {@code nextInt(8×(时运+1))+3}），
 * 结果在 [min, min + spread×(时运+1) − 1] 上<b>均匀</b>分布。原版的 apply_bonus 三种公式都拼不出来
 * （uniform_bonus_count 叠在 set_count 上最小最大值和平均数都对，但分布是梯形，两头少中间多），所以单写一个。
 * <pre>
 * { "function": "essentialcraft5:fortune_scaled_count", "min": 1, "spread": 2 }
 * </pre>
 * 没有工具（爆炸、不带工具的破坏器）按时运 0 算，和原作一样；爆炸衰减另外由 explosion_decay 处理。
 * 超出最大堆叠的数量由 LootTable 的拆堆自动拆成多堆（只有 {@code getRandomItems} 这条路会拆；{@code getRandomItemsRaw} 不拆，
 * 调用方自己拆，例：地脉投影仪掷表时套 {@code LootTable.createStackSplitter}）。
 * <p>
 * 类型在 {@link FunctionRegistration} 里用 RegisterEvent 注册（ECRegistries 没有战利品函数的 DeferredRegister，模块不改共用文件）。
 */
public class FortuneScaledCount extends LootItemConditionalFunction {

    public static final ResourceLocation ID = EssentialCraft5.id("fortune_scaled_count");
    public static final LootItemFunctionType TYPE = new LootItemFunctionType(new Serializer());

    private final int min;
    private final int spread;

    FortuneScaledCount(LootItemCondition[] conditions, int min, int spread) {
        super(conditions);
        this.min = min;
        this.spread = spread;
    }

    /** datagen 用：LootItem.lootTableItem(x).apply(FortuneScaledCount.count(1, 2)) */
    public static LootItemConditionalFunction.Builder<?> count(int min, int spread) {
        if (spread < 1) {
            throw new IllegalArgumentException("spread 必须 ≥ 1：" + spread);
        }
        return simpleBuilder(conditions -> new FortuneScaledCount(conditions, min, spread));
    }

    @Override
    public LootItemFunctionType getType() {
        return TYPE;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.TOOL);
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        ItemStack tool = context.getParamOrNull(LootContextParams.TOOL);
        // 和原版 apply_bonus 一样经 ItemStack#getEnchantmentLevel 取等级（Forge 的附魔等级钩子会生效）
        int fortune = tool == null ? 0 : EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_FORTUNE, tool);
        // 时运等级被别的模组抬得离谱时防 int 溢出
        long bound = (long) spread * (Math.max(fortune, 0) + 1L);
        int safeBound = (int) Math.min(bound, (long) Integer.MAX_VALUE - Math.max(min, 0));
        stack.setCount(min + context.getRandom().nextInt(safeBound));
        return stack;
    }

    public static class Serializer extends LootItemConditionalFunction.Serializer<FortuneScaledCount> {

        @Override
        public void serialize(JsonObject json, FortuneScaledCount function, JsonSerializationContext context) {
            super.serialize(json, function, context);
            json.addProperty("min", function.min);
            json.addProperty("spread", function.spread);
        }

        @Override
        public FortuneScaledCount deserialize(JsonObject json, JsonDeserializationContext context, LootItemCondition[] conditions) {
            int min = GsonHelper.getAsInt(json, "min");
            int spread = GsonHelper.getAsInt(json, "spread");
            if (spread < 1) {
                throw new JsonSyntaxException("fortune_scaled_count 的 spread 必须 ≥ 1，实际 " + spread);
            }
            return new FortuneScaledCount(conditions, min, spread);
        }
    }

    /** 把类型登记进原版的战利品函数注册表（Forge 自动发现，不用接线） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class FunctionRegistration {

        private FunctionRegistration() {
        }

        @SubscribeEvent
        public static void register(RegisterEvent event) {
            event.register(Registries.LOOT_FUNCTION_TYPE, ID, () -> TYPE);
        }
    }
}
