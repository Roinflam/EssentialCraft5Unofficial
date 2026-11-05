package com.flameforge.essentialcraft5.entity.mob;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.registry.ECMobFramework;
import com.flameforge.essentialcraft5.registry.ECTags;
import com.flameforge.essentialcraft5.util.ECDamageKinds;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.function.Predicate;

/**
 * 新怪物的元素弱点与抗性（13c-Fa，batch13.md §5.1）：实体类型标签（{@code data/essentialcraft5/tags/entity_types/}），
 * 在 {@code LivingHurtEvent} NORMAL 乘倍率，不覆盖 {@code actuallyHurt}（别的模组、护甲、附魔的顺序照原版）：
 * <ul>
 *   <li>{@link #WEAK_TO_FIRE}：火（{@code #minecraft:is_fire}，着火、岩浆、火球、火焰弹）×{@code weakness_multiplier}（默认 1.5）；</li>
 *   <li>{@link #WEAK_TO_FREEZING}：冰冻（{@code #minecraft:is_freezing}，细雪、本模组的寒气）×1.5；</li>
 *   <li>{@link #WEAK_TO_LIGHTNING}：雷（{@code #minecraft:is_lightning}）×1.5；</li>
 *   <li>{@link #WEAK_TO_MELEE}：近战（直接实体是活物：玩家、怪物本身、替主人挥砍的宠物，{@code ECDamageKinds.isMelee}）×1.5；</li>
 *   <li>{@link #RESISTS_PROJECTILE}：弹射物（{@code #minecraft:is_projectile} 或直接实体是弹射物）×{@code resistance_multiplier}（默认 0.5）。</li>
 * </ul>
 * 一下伤害同时对上几条时连乘（火球打怕火又抗弹射物的怪 = 1.5 × 0.5）。{@link ECMonster} 走它自己的 {@link ECMonster#hasTrait}
 * （元素游灵按元素覆盖），别的生物按类型标签（数据包可以给原版怪加）。只在服务端。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class MobWeakness {

    public static final TagKey<EntityType<?>> WEAK_TO_FIRE = ECTags.modEntity("weak_to_fire");
    public static final TagKey<EntityType<?>> WEAK_TO_FREEZING = ECTags.modEntity("weak_to_freezing");
    public static final TagKey<EntityType<?>> WEAK_TO_LIGHTNING = ECTags.modEntity("weak_to_lightning");
    public static final TagKey<EntityType<?>> WEAK_TO_MELEE = ECTags.modEntity("weak_to_melee");
    public static final TagKey<EntityType<?>> RESISTS_PROJECTILE = ECTags.modEntity("resists_projectile");

    private MobWeakness() {
    }

    /** 这一下伤害对有这些特性的生物乘几倍（has 回答「有没有这个标签」）；一条都对不上是 1 */
    public static float multiplier(Predicate<TagKey<EntityType<?>>> has, DamageSource source) {
        int weaknesses = 0;
        if (source.is(DamageTypeTags.IS_FIRE) && has.test(WEAK_TO_FIRE)) {
            weaknesses++;
        }
        if (source.is(DamageTypeTags.IS_FREEZING) && has.test(WEAK_TO_FREEZING)) {
            weaknesses++;
        }
        if (source.is(DamageTypeTags.IS_LIGHTNING) && has.test(WEAK_TO_LIGHTNING)) {
            weaknesses++;
        }
        if (ECDamageKinds.isMelee(source) && has.test(WEAK_TO_MELEE)) {
            weaknesses++;
        }
        boolean resists = ECDamageKinds.isProjectile(source) && has.test(RESISTS_PROJECTILE);
        if (weaknesses == 0 && !resists) {
            return 1F;
        }
        double result = Math.pow(ECMobFramework.Config.weakness(), weaknesses);
        if (resists) {
            result *= ECMobFramework.Config.resistance();
        }
        return (float) result;
    }

    /** 这只生物挨这一下乘几倍 */
    public static float multiplier(LivingEntity entity, DamageSource source) {
        if (entity instanceof ECMonster monster) {
            return monster.weaknessMultiplier(source);
        }
        EntityType<?> type = entity.getType();
        return multiplier(type::is, source);
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onMobWeaknessHurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        float factor = multiplier(entity, event.getSource());
        if (factor != 1F) {
            event.setAmount(event.getAmount() * factor);
        }
    }
}
