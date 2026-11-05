package com.flameforge.essentialcraft5.advancement;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 进度条件 {@code essentialcraft5:structure_formed}（第四批框架 F4-15）：附近的多方块结构<b>第一次成形</b>。
 * 特斯拉线圈、反应堆的手册完成条件用；以后传送器、熔炉也能用。照 {@code enrichment/EnrichmentTrigger} 写。
 * <pre>{@code
 * "criteria": { "formed": { "trigger": "essentialcraft5:structure_formed",
 *                           "conditions": { "structure": "essentialcraft5:mru_coil", "min_radius": 7 } } } }
 * }</pre>
 * 条件都可选：{@code structure}（结构 id，通常就是 {@code StructurePatterns.register} 用的那个；不写 = 任何结构）、
 * {@code min_radius}（整数，触发时报的「半径」≥ 它才算；线圈报射程、别的结构报 0；不写 = 0）。
 *
 * <h2>机器怎么调</h2>
 * 结构从「没成形」翻成「成形」的那一次（不是每次复查都调）：{@code StructureFormedTrigger.triggerNearby(serverLevel, worldPosition, id, radius)}，
 * 给 {@link #TRIGGER_RANGE}（16）格内的玩家触发。只对真的在世界玩家列表里的玩家生效，所以 GameTest 的 {@code realPlayer}（不在列表里）要直接调
 * {@link #trigger(ServerPlayer, ResourceLocation, int)} 测。假玩家不会拿到进度（原版 {@code PlayerAdvancements.award} 挡了 FakePlayer）。
 *
 * <h2>注册</h2>
 * 自带 MOD 总线监听（{@link TriggerRegistration}）：通用初始化（{@link FMLCommonSetupEvent} 的主线程任务）里登记进 {@link CriteriaTriggers}——
 * 模组构造阶段是多个模组并行的，而 CriteriaTriggers 的表是普通 HashMap。不用在 ECRegistries 接线。
 */
public final class StructureFormedTrigger extends SimpleCriterionTrigger<StructureFormedTrigger.Instance> {

    public static final ResourceLocation ID = EssentialCraft5.id("structure_formed");
    public static final StructureFormedTrigger INSTANCE = new StructureFormedTrigger();

    /** 结构成形时给多少格内的玩家触发（照富集仓的 16 格） */
    public static final double TRIGGER_RANGE = 16D;

    private StructureFormedTrigger() {
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    protected Instance createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext context) {
        ResourceLocation structure = null;
        if (json.has("structure")) {
            String raw = GsonHelper.getAsString(json, "structure");
            structure = ResourceLocation.tryParse(raw);
            if (structure == null) {
                throw new JsonParseException("structure_formed: 结构 id 不合法：" + raw);
            }
        }
        int minRadius = GsonHelper.getAsInt(json, "min_radius", 0);
        return new Instance(player, structure, minRadius);
    }

    /**
     * 给一名玩家触发（条件匹配的进度记一次完成）。已经完成的进度不会重复给。
     *
     * @param structure 成形的结构 id
     * @param radius    结构报的「半径」（线圈的射程；没有这个概念的结构传 0）
     */
    public void trigger(ServerPlayer player, ResourceLocation structure, int radius) {
        trigger(player, instance -> instance.matches(structure, radius));
    }

    /**
     * 结构在 pos 第一次成形：给 {@link #TRIGGER_RANGE} 格内（到方块中心的直线距离）的玩家触发。只在服务端调。
     */
    public static void triggerNearby(ServerLevel level, BlockPos pos, ResourceLocation structure, int radius) {
        for (ServerPlayer player : playersWithin(level.players(), Vec3.atCenterOf(pos), TRIGGER_RANGE)) {
            INSTANCE.trigger(player, structure, radius);
        }
    }

    /** 候选玩家里离 center 不超过 range 格的（旁观者也算：成形时在旁边看着就算见过）。纯函数，GameTest 直接测 */
    public static List<ServerPlayer> playersWithin(Collection<? extends ServerPlayer> candidates, Vec3 center, double range) {
        List<ServerPlayer> result = new ArrayList<>();
        double rangeSqr = range * range;
        for (ServerPlayer player : candidates) {
            if (player.isAlive() && player.distanceToSqr(center) <= rangeSqr) {
                result.add(player);
            }
        }
        return result;
    }

    /** 通用初始化时登记触发器（名字带前缀：同包里别的类的 Registration.commonSetup 会和它撞名、静默失效） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class TriggerRegistration {
        private TriggerRegistration() {
        }

        @SubscribeEvent
        public static void registerStructureFormedTrigger(FMLCommonSetupEvent event) {
            event.enqueueWork(() -> CriteriaTriggers.register(INSTANCE));
        }
    }

    public static final class Instance extends AbstractCriterionTriggerInstance {
        /** null = 任何结构 */
        @Nullable
        private final ResourceLocation structure;
        private final int minRadius;

        public Instance(ContextAwarePredicate player, @Nullable ResourceLocation structure, int minRadius) {
            super(ID, player);
            this.structure = structure;
            this.minRadius = minRadius;
        }

        /** 只看结构 id、最小半径的条件（代码里建进度或测试用） */
        public static Instance of(@Nullable ResourceLocation structure, int minRadius) {
            return new Instance(ContextAwarePredicate.ANY, structure, minRadius);
        }

        public boolean matches(ResourceLocation formed, int radius) {
            return (structure == null || structure.equals(formed)) && radius >= minRadius;
        }

        @Nullable
        public ResourceLocation structure() {
            return structure;
        }

        public int minRadius() {
            return minRadius;
        }

        @Override
        public JsonObject serializeToJson(SerializationContext context) {
            JsonObject json = super.serializeToJson(context);
            if (structure != null) {
                json.addProperty("structure", structure.toString());
            }
            if (minRadius != 0) {
                json.addProperty("min_radius", minRadius);
            }
            return json;
        }
    }
}
