package com.flameforge.essentialcraft5.loot;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.player.MobKillLedger;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECMobFramework;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSyntaxException;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 战利品条件 {@code essentialcraft5:kill_fatigue}（13c-Fa，batch13.md §5.4「按人递减」，D22 A；policy §4.18）：
 * <pre>
 * { "condition": "essentialcraft5:kill_fatigue", "group": "overworld_common", "chance": 0.5 }
 * </pre>
 * 通过的几率 = chance × min(1, F / n)：F 是这一组的免打折只数（{@code general.mobs.kill_fatigue.<组>}，默认 overworld_common 32、ember 24、
 * wind_storm 8、hoanna_common 30、hoanna_titanite 20、elite 6；0 = 这组不递减），n 是击杀者最近一小时<b>在线时间</b>里杀过的这一组怪数
 * （{@link MobKillLedger}，含这一只）。正常打怪碰不到递减（每组每小时 F 只不打折）；刷怪塔杀得越多每只越难掉，超过 F 以后每小时期望掉落恒等于 chance × F。
 * 多人刷各算各的。
 * <ul>
 *   <li>击杀者是 {@code killed_by_player} 的那名玩家，要是真玩家；没有就不通过。</li>
 *   <li><b>同一只怪对同一名玩家的同一组只记一次</b>：一张表里几个池子用同一组时，第一次判到才往这名玩家的台账记一只，后面的池子照同一个 n 判
 *       （按死掉的那个实体对象 + 玩家 UUID 记，本刻末清）。首领给每名分得到掉落的人各掷一次表，每人各记各的。</li>
 *   <li>条件按顺序判：写在 {@code essentialcraft5:fair_kill} 后面（不公平的击杀不记数）；这个条件自带几率，池子里别再叠 {@code random_chance}。</li>
 * </ul>
 * 组名只认上面六个（写错了读表时就报错，不悄悄当成别的组）。
 */
public final class KillFatigueCondition implements LootItemCondition {

    public static final ResourceLocation ID = EssentialCraft5.id("kill_fatigue");
    public static final LootItemConditionType TYPE = new LootItemConditionType(new Serializer());

    /** 本刻里已经记过数的（死掉的实体 → 「玩家 UUID|组」），本刻末清 */
    private static final Map<Entity, Set<String>> COUNTED = new WeakHashMap<>();

    private final String group;
    private final float chance;

    public KillFatigueCondition(String group, float chance) {
        if (!ECMobFramework.Config.fatigueGroups().contains(group)) {
            throw new IllegalArgumentException("kill_fatigue 的 group 只能是 " + ECMobFramework.Config.fatigueGroups() + "，写的是 " + group);
        }
        this.group = group;
        this.chance = Math.max(0F, Math.min(1F, chance));
    }

    /** datagen / 代码建表用 */
    public static LootItemCondition.Builder of(String group, float chance) {
        return () -> new KillFatigueCondition(group, chance);
    }

    public String group() {
        return group;
    }

    public float chance() {
        return chance;
    }

    @Override
    public LootItemConditionType getType() {
        return TYPE;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.THIS_ENTITY, LootContextParams.LAST_DAMAGE_PLAYER);
    }

    @Override
    public boolean test(LootContext context) {
        Entity victim = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        Player killer = context.getParamOrNull(LootContextParams.LAST_DAMAGE_PLAYER);
        float pass = passChance(victim, killer);
        return pass > 0F && context.getRandom().nextFloat() < pass;
    }

    /**
     * 这一只这一次的通过几率（第一次问时往击杀者的台账里记一只）。没有击杀者 / 不是真玩家 / 没有玩家数据时是 0。
     */
    public float passChance(Entity victim, Player killer) {
        if (!(victim instanceof LivingEntity) || !ECProtection.isRealPlayer(killer)) {
            return 0F;
        }
        IPlayerData data = PlayerDataEvents.get(killer);
        if (data == null) {
            return 0F;
        }
        if (COUNTED.computeIfAbsent(victim, v -> new HashSet<>(2)).add(killer.getUUID() + "|" + group)) {
            MobKillLedger.addKill(data, group);
        }
        return passChance(chance, ECMobFramework.Config.freeKills(group), MobKillLedger.kills(data, group));
    }

    /** chance × min(1, free / kills)；free ≤ 0（这组不递减）或 kills ≤ free 时就是 chance */
    public static float passChance(float chance, int free, int kills) {
        if (free <= 0 || kills <= free) {
            return chance;
        }
        return chance * free / kills;
    }

    /** 忘掉本刻记过数的实体（GameTest 用：同一只假装再死一次） */
    public static void forgetCounted() {
        COUNTED.clear();
    }

    public static final class Serializer implements net.minecraft.world.level.storage.loot.Serializer<KillFatigueCondition> {

        @Override
        public void serialize(JsonObject json, KillFatigueCondition condition, JsonSerializationContext context) {
            json.addProperty("group", condition.group);
            json.addProperty("chance", condition.chance);
        }

        @Override
        public KillFatigueCondition deserialize(JsonObject json, JsonDeserializationContext context) {
            String group = GsonHelper.getAsString(json, "group");
            if (!ECMobFramework.Config.fatigueGroups().contains(group)) {
                throw new JsonSyntaxException("kill_fatigue 的 group 只能是 " + ECMobFramework.Config.fatigueGroups() + "，写的是 " + group);
            }
            float chance = GsonHelper.getAsFloat(json, "chance");
            if (!(chance >= 0F && chance <= 1F)) {
                throw new JsonSyntaxException("kill_fatigue 的 chance 要在 0~1 之间，写的是 " + chance);
            }
            return new KillFatigueCondition(group, chance);
        }
    }

    /** 把类型登记进原版的战利品条件注册表（Forge 自动发现） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class KillFatigueRegistration {

        private KillFatigueRegistration() {
        }

        @SubscribeEvent
        public static void registerKillFatigue(RegisterEvent event) {
            event.register(Registries.LOOT_CONDITION_TYPE, ID, () -> TYPE);
        }
    }

    /** 每刻末清掉去重表 */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
    public static final class KillFatigueTicks {

        private KillFatigueTicks() {
        }

        @SubscribeEvent
        public static void onKillFatigueTick(TickEvent.ServerTickEvent event) {
            if (event.phase == TickEvent.Phase.END && !COUNTED.isEmpty()) {
                COUNTED.clear();
            }
        }

        @SubscribeEvent
        public static void onKillFatigueStopped(ServerStoppedEvent event) {
            COUNTED.clear();
        }
    }
}
