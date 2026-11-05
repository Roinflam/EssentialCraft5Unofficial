package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.command.ECCommands;
import com.flameforge.essentialcraft5.command.HoannaHazardsCommands;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.effect.RadiationEffect;
import com.flameforge.essentialcraft5.entity.PoisonFumeEntity;
import com.flameforge.essentialcraft5.hoanna.FumeSpawns;
import com.flameforge.essentialcraft5.hoanna.HoannaHazardsConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.registries.RegistryObject;

/**
 * 第六批 6a M4「辐射、天灾与毒雾」（计划 tools/plan/batch6a.md §5.4，policy §4.4）的注册类：
 * <ul>
 *   <li>效果 {@code essentialcraft5:radiation}（{@link RadiationEffect}，原作 potion.radiation；图标从原作 textures/special/potions.png 的 (4, 2) 格切出
 *       {@code textures/mob_effect/radiation.png}）。剂量、积累与消退在 {@code hoanna/Radiation}，每秒结算在 {@code hoanna/HoannaHazards}。</li>
 *   <li>实体 {@code essentialcraft5:poison_fume}（{@link PoisonFumeEntity}，原作 entitypoisonfume）与刷怪蛋 {@code poison_fume_spawn_egg}
 *       （原 entityegg:4，框架 {@link ECEntities#spawnEgg}：只在创造栏、点刷怪笼不改类型）；自然生成不进静态刷怪表，由 {@code hoanna/FumeSpawns}
 *       在毒雾事件期间加进海纳尔的刷怪表。客户端空渲染器在 {@code client/hoanna/HoannaHazardsClient}（雾团由 M3 画）。</li>
 *   <li>四种天灾的服务端逻辑与 SavedData：{@code hoanna/event/HoannaEvents}、{@code HoannaEventData}（枚举和两个同步包是框架 F6a 的）。</li>
 *   <li>δ λ μ ξ 的效果（物品由 F6b 在 ECBaubles 注册，按标签判断）：δ 在 {@link RadiationEffect#capHealth}，λ μ 在 {@code hoanna/Radiation}，
 *       μ 的免疫在 {@code HoannaHazards} / {@link PoisonFumeEntity}，ξ 在 {@code hoanna/RadiationBaubleEvents}。</li>
 *   <li>指令 {@code /ec5 hoanna_event start|stop|query}、{@code /ec5 player <targets> radiation get|set|add|clear}（{@link HoannaHazardsCommands}）。</li>
 *   <li>配置 {@code general.radiation}、{@code general.hoanna_events}（{@link HoannaHazardsConfig}）。</li>
 * </ul>
 * 数据：群系标签 {@code essentialcraft5:hoanna_radiation_plus_2}（海纳尔沙漠）/ {@code _plus_6}（恐惧之地）；手册步骤进度
 * {@code advancements/guide/hoanna/radiation.json}（{@code effects_changed}）、{@code event.json}（impossible，{@code HoannaEvents} 授予）。
 * 文字 tools/lang/keys/echoannahazards.json（事件名、开始 / 结束的聊天提示在框架的 echoanna.json）。
 * <p>
 * 框架 F6a 建的空壳，已接进 {@link ECRegistries#register}；不碰 ECRegistries。
 */
public final class ECHoannaHazards {

    /** 原作 new PotionRadiation(true, 0x660066) */
    public static final RegistryObject<RadiationEffect> RADIATION = ECEffects.register("radiation", RadiationEffect::new);

    /**
     * 原作 registerEntity(EntityPoisonFume.class, …)；0.6 × 0.6，怪物类别（占怪物上限，原作是 EntityMob）。
     * 追踪 4 区块、同步间隔 5 tick：它飘得很慢（≤ 0.1 格 / tick），客户端插值就够了（M3 的 HoannaVisualTests 守 updateInterval ≥ 5、trackingRange 4）。
     */
    public static final RegistryObject<EntityType<PoisonFumeEntity>> POISON_FUME = ECEntities.register("poison_fume",
            EntityType.Builder.<PoisonFumeEntity>of(PoisonFumeEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 0.6F).fireImmune().clientTrackingRange(4).updateInterval(5));

    /** 蛋壳暗橄榄、斑点病态黄绿（毒雾的颜色） */
    public static final int SPAWN_EGG_BACKGROUND = 0x6E7F3A;
    public static final int SPAWN_EGG_HIGHLIGHT = 0xB8C86A;
    public static final RegistryObject<ECEntities.SpawnEgg> POISON_FUME_SPAWN_EGG =
            ECEntities.spawnEgg("poison_fume_spawn_egg", POISON_FUME, SPAWN_EGG_BACKGROUND, SPAWN_EGG_HIGHLIGHT);

    /** 手册步骤进度：身上出现过辐射效果 */
    public static final ResourceLocation GUIDE_RADIATION = EssentialCraft5.id("guide/hoanna/radiation");

    private ECHoannaHazards() {
    }

    public static void init() {
        ECConfig.general(HoannaHazardsConfig::define);
        ECEntities.attributes(POISON_FUME, PoisonFumeEntity::createAttributes);
        ECEntities.spawnPlacement(POISON_FUME, SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, FumeSpawns::checkSpawn);
        ECCommands.addSubcommand(HoannaHazardsCommands::buildEvent);
        ECCommands.addPlayerSubcommand(HoannaHazardsCommands::buildRadiation);
    }
}
