package com.flameforge.essentialcraft5.loot;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.entity.mob.MobDamageShare;
import com.flameforge.essentialcraft5.registry.ECMobFramework;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.flameforge.essentialcraft5.util.SpawnOrigin;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

import java.util.Set;

/**
 * 战利品条件 {@code essentialcraft5:fair_kill}（13c-Fa，batch13.md §5.4，D22 A；policy §4.18）：防刷怪塔的「公平击杀」，三条都满足才成立——
 * <ol>
 *   <li>玩家杀的（原版 {@code killed_by_player} 的同一个参数：最后 100 刻内被玩家或玩家驯服的生物打过），而且是真玩家（假玩家、机械手不算，
 *       {@link ECProtection#isRealPlayer}）；</li>
 *   <li>来源公平（{@link SpawnOrigin#isFair}：自然、事件、结构、巢穴、首领本体；刷怪笼、刷怪蛋、发射器、指令、转化、增援、召唤、
 *       没走 {@code finalizeSpawn} 的都不公平）；</li>
 *   <li>玩家（含宠物）打的有效伤害占它受的有效伤害 ≥ {@code general.mobs.fair_kill_min_share}（默认 0.5，0 = 不看，{@link MobDamageShare}）：
 *       摔落 / 岩浆 / 溺水塔、别的模组的炮塔把怪磨到残血再补刀的拿不到；没有攻击者的着火、中毒、凋零，
 *       100 刻内打过它的玩家算他的（玩家自己点的火、下的毒）。</li>
 * </ol>
 * 写法：{@code {"condition": "essentialcraft5:fair_kill"}}，没有参数。和 {@code essentialcraft5:kill_fatigue} 一起用时写在它前面
 * （条件按顺序判、前一个不成立后面的不判，不公平的击杀就不占递减的名额）。
 */
public final class FairKillCondition implements LootItemCondition {

    public static final ResourceLocation ID = EssentialCraft5.id("fair_kill");
    public static final LootItemConditionType TYPE = new LootItemConditionType(new Serializer());
    public static final FairKillCondition INSTANCE = new FairKillCondition();

    private FairKillCondition() {
    }

    /** datagen / 代码建表用 */
    public static LootItemCondition.Builder fairKill() {
        return () -> INSTANCE;
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
        return test(victim, killer);
    }

    /** 同 {@link #test(LootContext)}，参数直接给（GameTest、代码里给首领分战利品时用） */
    public static boolean test(Entity victim, Player killer) {
        if (!(victim instanceof LivingEntity living) || !ECProtection.isRealPlayer(killer) || !SpawnOrigin.isFair(victim)) {
            return false;
        }
        double minShare = ECMobFramework.Config.fairKillShare();
        return minShare <= 0D || MobDamageShare.share(living) >= minShare - 1.0E-6D;
    }

    public static final class Serializer implements net.minecraft.world.level.storage.loot.Serializer<FairKillCondition> {

        @Override
        public void serialize(JsonObject json, FairKillCondition condition, JsonSerializationContext context) {
        }

        @Override
        public FairKillCondition deserialize(JsonObject json, JsonDeserializationContext context) {
            return INSTANCE;
        }
    }

    /** 把类型登记进原版的战利品条件注册表（Forge 自动发现） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class FairKillRegistration {

        private FairKillRegistration() {
        }

        @SubscribeEvent
        public static void registerFairKill(RegisterEvent event) {
            event.register(Registries.LOOT_CONDITION_TYPE, ID, () -> TYPE);
        }
    }
}
