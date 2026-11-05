package com.flameforge.essentialcraft5.enrichment;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.google.gson.JsonObject;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * 进度条件 {@code essentialcraft5:enrichment_chamber_formed}：玩家右键了一座已成形的富集仓的控制器，
 * 或者附近（16 格内）的富集仓刚成形 / 存储方块变多。可选条件 {@code "holding_chambers": 数值或 {"min": n}}。
 * <pre>{@code
 * "criteria": { "big": { "trigger": "essentialcraft5:enrichment_chamber_formed",
 *                        "conditions": { "holding_chambers": { "min": 10 } } } }
 * }</pre>
 * 模块自带的触发器，不进框架。在通用初始化（FMLCommonSetupEvent 的主线程任务）里登记进 CriteriaTriggers：
 * 模组构造阶段是多个模组并行的，CriteriaTriggers 的表是普通 HashMap。
 */
public final class EnrichmentTrigger extends SimpleCriterionTrigger<EnrichmentTrigger.Instance> {

    public static final ResourceLocation ID = EssentialCraft5.id("enrichment_chamber_formed");
    public static final EnrichmentTrigger INSTANCE = new EnrichmentTrigger();

    private EnrichmentTrigger() {
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    protected Instance createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext context) {
        return new Instance(player, MinMaxBounds.Ints.fromJson(json.get("holding_chambers")));
    }

    public void trigger(ServerPlayer player, int holdingChambers) {
        trigger(player, instance -> instance.chambers.matches(holdingChambers));
    }

    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void commonSetup(FMLCommonSetupEvent event) {
            event.enqueueWork(() -> CriteriaTriggers.register(INSTANCE));
        }
    }

    public static final class Instance extends AbstractCriterionTriggerInstance {
        private final MinMaxBounds.Ints chambers;

        public Instance(ContextAwarePredicate player, MinMaxBounds.Ints chambers) {
            super(ID, player);
            this.chambers = chambers;
        }

        @Override
        public JsonObject serializeToJson(SerializationContext context) {
            JsonObject json = super.serializeToJson(context);
            json.add("holding_chambers", chambers.serializeToJson());
            return json;
        }
    }
}
