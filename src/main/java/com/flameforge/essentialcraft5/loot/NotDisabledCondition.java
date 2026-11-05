package com.flameforge.essentialcraft5.loot;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSyntaxException;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

/**
 * 战利品条件 {@code essentialcraft5:not_disabled}（第三批框架 G4）：物品没被服务端配置 {@code general.baubles.disabled} 禁用时成立。
 * <p>
 * 战利品条目的条件在生成物品<b>之前</b>判断，条件本身不知道条目是什么物品，所以要在条件里写明查哪个注册名：
 * <pre>
 * { "type": "minecraft:item", "name": "essentialcraft5:zeta_amulet", "weight": 3,
 *   "conditions": [ { "condition": "essentialcraft5:not_disabled", "item": "essentialcraft5:zeta_amulet" } ] }
 * </pre>
 * 条件不成立的条目直接不参加这一抽的加权（原版 LootPoolSingletonContainer.expand 先查 canRun），空条目的权重不变，
 * 所以禁用几件以后其余饰品按比例多出一点，出空的概率也随之变大一点。
 * <p>
 * 配置在世界运行中改了（/reload 不重读 SERVER 配置，改文件会被 Forge 热重载）立刻生效，不用重载战利品表。
 * 类型在 {@link ConditionRegistration} 里用 RegisterEvent 注册（同 {@link FortuneScaledCount}）。
 */
public final class NotDisabledCondition implements LootItemCondition {

    public static final ResourceLocation ID = EssentialCraft5.id("not_disabled");
    public static final LootItemConditionType TYPE = new LootItemConditionType(new Serializer());

    private final ResourceLocation item;

    public NotDisabledCondition(ResourceLocation item) {
        this.item = item;
    }

    /** datagen / 代码建表用：{@code LootItem.lootTableItem(x).when(NotDisabledCondition.of(id))} */
    public static LootItemCondition.Builder of(ResourceLocation item) {
        return () -> new NotDisabledCondition(item);
    }

    public ResourceLocation item() {
        return item;
    }

    @Override
    public LootItemConditionType getType() {
        return TYPE;
    }

    @Override
    public boolean test(LootContext context) {
        return !CuriosCompat.isDisabled(item);
    }

    public static final class Serializer implements net.minecraft.world.level.storage.loot.Serializer<NotDisabledCondition> {

        @Override
        public void serialize(JsonObject json, NotDisabledCondition condition, JsonSerializationContext context) {
            json.addProperty("item", condition.item.toString());
        }

        @Override
        public NotDisabledCondition deserialize(JsonObject json, JsonDeserializationContext context) {
            String raw = GsonHelper.getAsString(json, "item");
            ResourceLocation id = ResourceLocation.tryParse(raw);
            if (id == null) {
                throw new JsonSyntaxException("not_disabled 的 item 不是合法的注册名：" + raw);
            }
            return new NotDisabledCondition(id);
        }
    }

    /** 把类型登记进原版的战利品条件注册表（Forge 自动发现，不用接线） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ConditionRegistration {

        private ConditionRegistration() {
        }

        @SubscribeEvent
        public static void register(RegisterEvent event) {
            event.register(Registries.LOOT_CONDITION_TYPE, ID, () -> TYPE);
        }
    }
}
