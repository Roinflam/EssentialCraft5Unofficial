package com.flameforge.essentialcraft5.loot.condition;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.entity.boss.CalamityHeartEntity;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSyntaxException;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

import java.util.Locale;
import java.util.Set;

/**
 * 天灾之心的主灾奖励条件 {@code essentialcraft5:calamity_main}{event}（第十三批 13e，batch13.md §5.3.5）：掷表的实体（{@code this}）是天灾之心、
 * 召唤时定下的主灾就是 {@code event}（{@code sun_array} / {@code fumes} / {@code darkness} / {@code earthquake}）。
 * 主灾记在首领自己的 NBT 里，不看掷表这一刻海纳尔在发生什么。表由 {@code tools/gen/calamity_heart_loot.py} 生成。
 */
public record CalamityMainCondition(CalamityHeartEntity.Aspect aspect) implements LootItemCondition {

    public static final ResourceLocation ID = EssentialCraft5.id("calamity_main");
    public static final LootItemConditionType TYPE = new LootItemConditionType(new Serializer());

    /** datagen / 代码建表用 */
    public static LootItemCondition.Builder of(CalamityHeartEntity.Aspect aspect) {
        CalamityMainCondition condition = new CalamityMainCondition(aspect);
        return () -> condition;
    }

    @Override
    public LootItemConditionType getType() {
        return TYPE;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.THIS_ENTITY);
    }

    @Override
    public boolean test(LootContext context) {
        Entity entity = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        return entity instanceof CalamityHeartEntity heart && heart.mainAspect() == aspect;
    }

    public static final class Serializer implements net.minecraft.world.level.storage.loot.Serializer<CalamityMainCondition> {

        @Override
        public void serialize(JsonObject json, CalamityMainCondition condition, JsonSerializationContext context) {
            json.addProperty("event", condition.aspect.key());
        }

        @Override
        public CalamityMainCondition deserialize(JsonObject json, JsonDeserializationContext context) {
            String raw = GsonHelper.getAsString(json, "event").toLowerCase(Locale.ROOT);
            CalamityHeartEntity.Aspect aspect = CalamityHeartEntity.Aspect.of(HoannaEvent.byName(raw));
            if (aspect == null) {
                throw new JsonSyntaxException("calamity_main 的 event 只能是 sun_array / fumes / darkness / earthquake：" + raw);
            }
            return new CalamityMainCondition(aspect);
        }
    }

    /** RegisterEvent 登记（嵌套类名带前缀，免得和 loot 包里别的 Registration 撞名） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class CalamityMainConditionRegistration {

        private CalamityMainConditionRegistration() {
        }

        @SubscribeEvent
        public static void registerCalamityMainCondition(RegisterEvent event) {
            event.register(Registries.LOOT_CONDITION_TYPE, ID, () -> TYPE);
        }
    }
}
