package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.api.WindAffinity;
import com.flameforge.essentialcraft5.entity.MRUPresenceEntity;
import com.flameforge.essentialcraft5.entity.StormGustEntity;
import com.flameforge.essentialcraft5.entity.StormSorcerer;
import com.flameforge.essentialcraft5.entity.WindMageEntity;
import com.flameforge.essentialcraft5.entity.mob.ElementalWispEntity;
import com.flameforge.essentialcraft5.entity.mob.ElementalWispEntity.Element;
import com.flameforge.essentialcraft5.entity.mob.MatrixWraithEntity;
import com.flameforge.essentialcraft5.entity.mob.MatrixWraithEvents;
import com.flameforge.essentialcraft5.entity.mob.MobDamageShare;
import com.flameforge.essentialcraft5.entity.mob.MobWeakness;
import com.flameforge.essentialcraft5.entity.mob.WispBoltEntity;
import com.flameforge.essentialcraft5.item.BottledWindItem;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECEssence;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.registry.ECMobFramework;
import com.flameforge.essentialcraft5.registry.ECWildMobs;
import com.flameforge.essentialcraft5.registry.ECWind;
import com.flameforge.essentialcraft5.registry.ECWindMage;
import com.flameforge.essentialcraft5.util.SpawnOrigin;
import com.flameforge.essentialcraft5.util.SpawnOrigin.Origin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;

/**
 * 第十三批 13c-M1「元素游灵、矩阵游魂、风暴术士」的 GameTest（batch13.md §12.2 13c 行的 M1 部分）：
 * <ul>
 *   <li>元素游灵：出生群系定元素、存档读回、四种元素的弱点 / 抗性 / 防火、四种命中效果、位置判定（高度、看不见天）、附近上限与配置 0、
 *       群系修改器进了刷怪表且挤占 ≤ 3%、掉落按元素（公平 / 不公平 / 精英）、精英元素弹轮换且只打技能目标；</li>
 *   <li>矩阵游魂：只在够格的聚集体附近追加刷怪表（被压着、太小、太远、别的类别、权重 0 都不加，同一个对象）、每团 3 只、
 *       自然刷出进世界才抽 MRU（蛋不抽、被取消不抽、配置 0 不抽）、来源记事件、打实加矩阵损伤（只加被抓的人、被拦下不加、创造不加、配置 0 不加）、
 *       弹射物减半、精英闪到背后（被取消就不闪）、掉落；</li>
 *   <li>风暴术士：类型存档与夹值、属性读档不叠两层、几率（打雷 / 海纳尔 / 来源 / 配置 0 / 13d 倍数钩子）、疾风弹击退与只打技能目标、
 *       预警落雷（到点才劈、只劈圈里的技能目标、走开就躲过）、风暴余息（不给好感不消耗、照样能合成）、掉落。</li>
 * </ul>
 * 不动全局天气、难度；聚集体都放在测试结构正上方各自不同的高度（{@link #LIFT_SPAWNS} 等），互不干扰，也碰不到框架测试放在 +90 的那些。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class WildMobsTests {

    private static final String EMPTY = "empty";
    private static final double EPS = 1.0E-4D;
    private static final int LIFT_SPAWNS = 157;
    private static final int LIFT_CAP = 187;
    private static final int LIFT_DRAIN = 217;
    private static final int LIFT_DRAIN_CANCEL = 247;

    private WildMobsTests() {
    }

    // ================================================================== 工具

    private static ServerPlayer player(GameTestHelper helper, String name) {
        return ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
    }

    /** 能挨打的真玩家：先空跑掉 60 刻出生无敌（同 CorruptionTests.victim） */
    private static ServerPlayer victim(GameTestHelper helper, String name) {
        ServerPlayer player = player(helper, name);
        for (int i = 0; i <= 60; i++) {
            player.tick();
        }
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200D);
        player.setHealth(200F);
        return player;
    }

    private static IPlayerData data(Player player) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            throw new IllegalStateException("测试玩家应挂着玩家数据");
        }
        return data;
    }

    private static ElementalWispEntity wisp(GameTestHelper helper, Element element) {
        ElementalWispEntity wisp = ECWildMobs.ELEMENTAL_WISP.get().create(helper.getLevel());
        Vec3 at = helper.absoluteVec(new Vec3(1.5D, 2D, 1.5D));
        wisp.moveTo(at.x, at.y, at.z, 0F, 0F);
        wisp.setElement(element);
        return wisp;
    }

    private static WindMageEntity storm(GameTestHelper helper) {
        WindMageEntity mage = ECWindMage.WIND_MAGE.get().create(helper.getLevel());
        Vec3 at = helper.absoluteVec(new Vec3(1.5D, 2D, 1.5D));
        mage.moveTo(at.x, at.y, at.z, 0F, 0F);
        mage.setMageType(WindMageEntity.STORM_SORCERER);
        return mage;
    }

    private static boolean lastHitIs(LivingEntity entity, ResourceKey<net.minecraft.world.damagesource.DamageType> type) {
        DamageSource source = entity.getLastDamageSource();
        return source != null && source.is(type);
    }

    /** 被 player 公平打死（自然来源、伤害全是他打的）时掉落表 table 掉什么 */
    private static List<ItemStack> drops(ServerLevel level, ResourceLocation table, LivingEntity victim, ServerPlayer killer, long seed) {
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, victim)
                .withParameter(LootContextParams.ORIGIN, victim.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, killer.damageSources().playerAttack(killer))
                .withOptionalParameter(LootContextParams.KILLER_ENTITY, killer)
                .withOptionalParameter(LootContextParams.DIRECT_KILLER_ENTITY, killer)
                .withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer)
                .create(LootContextParamSets.ENTITY);
        LootTable loot = level.getServer().getLootData().getLootTable(table);
        return loot.getRandomItems(params, seed);
    }

    private static int count(List<ItemStack> stacks, net.minecraft.world.item.Item item) {
        return stacks.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    /** 掉落统计时三组都不递减（各测各的几率） */
    private static void noFatigue(boolean on) {
        for (String group : new String[]{"overworld_common", "elite", "wind_storm"}) {
            ECMobFramework.Config.setTestFreeKills(group, on ? 0 : null);
        }
    }

    private static Vec3 lifted(GameTestHelper helper, int lift) {
        return helper.absoluteVec(new Vec3(1.5D, 2D + lift, 1.5D));
    }

    private static MRUPresenceEntity presence(ServerLevel level, Vec3 at, int mru) {
        MRUPresenceEntity presence = MRUPresenceEntity.create(level, at.x, at.y, at.z, mru, 1F, true, true);
        level.addFreshEntity(presence);
        return presence;
    }

    private static boolean wraithOffered(ServerLevel level, BlockPos pos, MobCategory category) {
        WeightedRandomList<MobSpawnSettings.SpawnerData> list = ForgeEventFactory.getPotentialSpawns(level, category, pos, WeightedRandomList.create());
        MobSpawnSettings.SpawnerData data = MatrixWraithEvents.spawnerData();
        return data != null && list.unwrap().contains(data);
    }

    private static void discard(Entity... entities) {
        for (Entity entity : entities) {
            if (entity != null && !entity.isRemoved()) {
                entity.discard();
            }
        }
    }

    // ================================================================== 元素游灵

    /** 元素按出生群系定（海洋 / 河 / 沼泽 / 繁茂洞穴 → 水，沙漠 / 恶地 → 火，山地 / 冷 → 风，其余 → 土）；存档读回一样；读到不认识的元素按群系补 */
    @GameTest(template = EMPTY)
    public static void wispElementFromBiomeAndSave(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(ElementalWispEntity.elementOf(level, Biomes.OCEAN) == Element.WATER, "海洋 → 水");
        helper.assertTrue(ElementalWispEntity.elementOf(level, Biomes.RIVER) == Element.WATER, "河 → 水");
        helper.assertTrue(ElementalWispEntity.elementOf(level, Biomes.SWAMP) == Element.WATER, "沼泽 → 水");
        helper.assertTrue(ElementalWispEntity.elementOf(level, Biomes.LUSH_CAVES) == Element.WATER, "繁茂洞穴 → 水");
        helper.assertTrue(ElementalWispEntity.elementOf(level, Biomes.DESERT) == Element.FIRE, "沙漠 → 火");
        helper.assertTrue(ElementalWispEntity.elementOf(level, Biomes.BADLANDS) == Element.FIRE, "恶地 → 火");
        helper.assertTrue(ElementalWispEntity.elementOf(level, Biomes.SNOWY_PLAINS) == Element.AIR, "雪原 → 风");
        helper.assertTrue(ElementalWispEntity.elementOf(level, Biomes.WINDSWEPT_HILLS) == Element.AIR, "风袭丘陵 → 风");
        helper.assertTrue(ElementalWispEntity.elementOf(level, Biomes.PLAINS) == Element.EARTH, "平原 → 土");
        helper.assertTrue(ElementalWispEntity.elementOf(level, Biomes.DRIPSTONE_CAVES) == Element.EARTH, "溶洞 → 土");

        for (Element element : Element.values()) {
            ElementalWispEntity wisp = wisp(helper, element);
            net.minecraft.nbt.CompoundTag tag = wisp.saveWithoutId(new net.minecraft.nbt.CompoundTag());
            helper.assertTrue(element.id().equals(tag.getString(ElementalWispEntity.TAG_ELEMENT)), "存档写元素名 " + element.id());
            ElementalWispEntity loaded = ECWildMobs.ELEMENTAL_WISP.get().create(level);
            loaded.load(tag);
            helper.assertTrue(loaded.getElement() == element, "读回来还是 " + element + "，实际 " + loaded.getElement());
        }
        helper.assertTrue(Element.byId("no_such_element") == null || Element.byId("no_such_element") == Element.EARTH,
                "不认识的元素名不会读成别的元素");

        // 精英「元素聚灵」存档读回：元素、精英都在，生命 ×2.5 只挂一层
        ElementalWispEntity elite = wisp(helper, Element.WATER);
        elite.setElite(true);
        float eliteMax = elite.getMaxHealth();
        helper.assertTrue(eliteMax > 12F, "精英生命比普通高，实际 " + eliteMax);
        net.minecraft.nbt.CompoundTag eliteTag = elite.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        ElementalWispEntity eliteLoaded = ECWildMobs.ELEMENTAL_WISP.get().create(level);
        eliteLoaded.load(eliteTag);
        helper.assertTrue(eliteLoaded.isElite() && eliteLoaded.getElement() == Element.WATER, "读回来还是水之精英");
        helper.assertTrue(Math.abs(eliteLoaded.getMaxHealth() - eliteMax) < EPS, "读档不叠两层，实际 " + eliteLoaded.getMaxHealth());
        eliteLoaded.load(eliteLoaded.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
        helper.assertTrue(Math.abs(eliteLoaded.getMaxHealth() - eliteMax) < EPS, "存读两遍也不叠，实际 " + eliteLoaded.getMaxHealth());
        helper.succeed();
    }

    /**
     * 判元素的先后：山地、丘陵排在温度前面，裸岩山峰（基础温度正好 1.0）和别的山地一样是风；不是山地的热群系（沙漠、恶地、热带草原、
     * 风袭热带草原）是火；暖水海洋、繁茂洞穴是水；平原、溶洞是土。
     */
    @GameTest(template = EMPTY)
    public static void wispElementForBiomeOrder(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<ResourceKey<Biome>> air = List.of(Biomes.STONY_PEAKS, Biomes.MEADOW, Biomes.JAGGED_PEAKS, Biomes.CHERRY_GROVE, Biomes.WINDSWEPT_HILLS);
        List<ResourceKey<Biome>> fire = List.of(Biomes.DESERT, Biomes.BADLANDS, Biomes.SAVANNA, Biomes.WINDSWEPT_SAVANNA);
        List<ResourceKey<Biome>> water = List.of(Biomes.WARM_OCEAN, Biomes.LUSH_CAVES);
        List<ResourceKey<Biome>> earth = List.of(Biomes.PLAINS, Biomes.DRIPSTONE_CAVES);
        for (List<ResourceKey<Biome>> group : List.of(air, fire, water, earth)) {
            Element expected = group == air ? Element.AIR : group == fire ? Element.FIRE : group == water ? Element.WATER : Element.EARTH;
            for (ResourceKey<Biome> biome : group) {
                Element actual = ElementalWispEntity.elementOf(level, biome);
                helper.assertTrue(actual == expected, biome.location() + " 应是 " + expected + "，实际 " + actual);
            }
        }
        helper.succeed();
    }

    /** 弱点 / 抗性：火 → 怕冰冻、防火；水 → 怕雷；土 → 怕近战；风 → 怕火、弹射物减半；数值走框架的 ×1.5 / ×0.5 */
    @GameTest(template = EMPTY)
    public static void wispElementTraits(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(3, 2, 3));
        Arrow arrow = new Arrow(level, husk);
        DamageSource freeze = level.damageSources().freeze();
        DamageSource lightning = level.damageSources().lightningBolt();
        DamageSource melee = level.damageSources().mobAttack(husk);
        DamageSource fire = level.damageSources().inFire();
        DamageSource projectile = level.damageSources().arrow(arrow, husk);

        ElementalWispEntity fireWisp = wisp(helper, Element.FIRE);
        helper.assertTrue(Math.abs(MobWeakness.multiplier(fireWisp, freeze) - 1.5F) < EPS, "火灵怕冰冻 ×1.5");
        helper.assertTrue(fireWisp.fireImmune(), "火灵防火");
        helper.assertTrue(Math.abs(MobWeakness.multiplier(fireWisp, melee) - 1F) < EPS, "火灵挨近战不变");

        ElementalWispEntity waterWisp = wisp(helper, Element.WATER);
        helper.assertTrue(Math.abs(MobWeakness.multiplier(waterWisp, lightning) - 1.5F) < EPS, "水灵怕雷 ×1.5");
        helper.assertFalse(waterWisp.fireImmune(), "水灵不防火");

        ElementalWispEntity earthWisp = wisp(helper, Element.EARTH);
        helper.assertTrue(Math.abs(MobWeakness.multiplier(earthWisp, melee) - 1.5F) < EPS, "土灵怕近战 ×1.5");
        helper.assertTrue(Math.abs(MobWeakness.multiplier(earthWisp, projectile) - 1F) < EPS, "土灵挨箭不变");

        ElementalWispEntity airWisp = wisp(helper, Element.AIR);
        helper.assertTrue(Math.abs(MobWeakness.multiplier(airWisp, fire) - 1.5F) < EPS, "风灵怕火 ×1.5");
        helper.assertTrue(Math.abs(MobWeakness.multiplier(airWisp, projectile) - 0.5F) < EPS, "风灵挨箭 ×0.5");
        helper.assertTrue(airWisp.hasTrait(MobWeakness.RESISTS_PROJECTILE) && !fireWisp.hasTrait(MobWeakness.RESISTS_PROJECTILE),
                "弹射物抗性只有风灵有");
        discard(husk, arrow);
        helper.succeed();
    }

    /** 四种命中效果：火点燃、水缓慢、土挖掘疲劳、风掀飞（击退抗性满了不飞）；冲撞真打一下也带上 */
    @GameTest(template = EMPTY)
    public static void wispElementHitEffects(GameTestHelper helper) {
        int ticks = ECWildMobs.Config.wispEffectSeconds() * 20;
        Husk burn = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(1, 2, 1));
        ElementalWispEntity.applyElementHit(burn, Element.FIRE, Vec3.ZERO, null);
        helper.assertTrue(burn.getRemainingFireTicks() > 0, "火：点燃");

        Husk slow = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(2, 2, 1));
        ElementalWispEntity.applyElementHit(slow, Element.WATER, Vec3.ZERO, null);
        helper.assertTrue(slow.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && slow.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getDuration() == ticks,
                "水：缓慢 " + ticks + " 刻");

        Husk dig = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(3, 2, 1));
        ElementalWispEntity.applyElementHit(dig, Element.EARTH, Vec3.ZERO, null);
        helper.assertTrue(dig.hasEffect(MobEffects.DIG_SLOWDOWN), "土：挖掘疲劳");

        Husk air = EntityType.HUSK.create(helper.getLevel());
        air.setDeltaMovement(Vec3.ZERO);
        ElementalWispEntity.applyElementHit(air, Element.AIR, new Vec3(1D, 0D, 0D), null);
        helper.assertTrue(air.getDeltaMovement().y > 0.3D && air.getDeltaMovement().x > 0.3D, "风：往冲的方向掀起来，实际 " + air.getDeltaMovement());
        Husk heavy = EntityType.HUSK.create(helper.getLevel());
        heavy.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1D);
        heavy.setDeltaMovement(Vec3.ZERO);
        ElementalWispEntity.applyElementHit(heavy, Element.AIR, new Vec3(1D, 0D, 0D), null);
        helper.assertTrue(heavy.getDeltaMovement().lengthSqr() < EPS, "击退抗性满了不飞");

        // 冲撞真打一下：伤害落地才带效果
        ElementalWispEntity wisp = wisp(helper, Element.WATER);
        helper.getLevel().addFreshEntity(wisp);
        wisp.setNoAi(true);
        Husk target = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(3, 2, 3));
        helper.assertTrue(wisp.doHurtTarget(target), "游灵打中了");
        helper.assertTrue(target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "水灵冲撞带缓慢");
        discard(burn, slow, dig, wisp, target);
        helper.succeed();
    }

    /** 位置：y ≤ max_y 才行（钉配置测边界），看得见天不行（头上盖一格石头、光照更新以后就行了） */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void wispSpawnPlaceRules(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        helper.assertTrue(level.canSeeSky(pos), "测试前提：超平坦地面上看得见天");
        helper.assertFalse(ElementalWispEntity.placeAllowed(level, pos), "看得见天：不刷");
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.STONE);
        helper.succeedWhen(() -> {
            try {
                ECWildMobs.Config.setTest("elemental_wisp.max_y", pos.getY());
                helper.assertTrue(ElementalWispEntity.placeAllowed(level, pos), "盖上以后、y 正好等于 max_y：能刷");
                ECWildMobs.Config.setTest("elemental_wisp.max_y", pos.getY() - 1);
                helper.assertFalse(ElementalWispEntity.placeAllowed(level, pos), "y 高过 max_y：不刷");
            } finally {
                ECWildMobs.Config.setTest("elemental_wisp.max_y", null);
            }
        });
    }

    /**
     * 附近上限：刷怪判定外面那层（{@link ElementalWispEntity#spawnRules}，里面换成恒真）在附近已有 max_nearby 只时拦下，上限填 0 一只都不刷，
     * 上限够大时放行。半径用不常见的值，各自是一格新的缓存，不去清共用缓存（别的测试可能正在数）。
     */
    @GameTest(template = EMPTY)
    public static void wispNearbyCapAndZero(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var rules = ElementalWispEntity.spawnRules((type, l, reason, p, random) -> true);
        RandomSource random = RandomSource.create(5L);
        List<ElementalWispEntity> placed = new ArrayList<>();
        try {
            ECWildMobs.Config.setTest("elemental_wisp.max_nearby", 0);
            helper.assertFalse(rules.test(ECWildMobs.ELEMENTAL_WISP.get(), level, MobSpawnType.NATURAL, pos, random), "上限填 0：不刷");
            for (int i = 0; i < 2; i++) {
                ElementalWispEntity wisp = wisp(helper, Element.EARTH);
                wisp.setNoAi(true);
                level.addFreshEntity(wisp);
                placed.add(wisp);
            }
            ECWildMobs.Config.setTest("elemental_wisp.max_nearby", 2);
            ECWildMobs.Config.setTest("elemental_wisp.nearby_radius", 37);
            helper.assertFalse(rules.test(ECWildMobs.ELEMENTAL_WISP.get(), level, MobSpawnType.NATURAL, pos, random), "附近已有 2 只、上限 2：不刷");
            ECWildMobs.Config.setTest("elemental_wisp.max_nearby", 64);
            ECWildMobs.Config.setTest("elemental_wisp.nearby_radius", 39);
            helper.assertTrue(rules.test(ECWildMobs.ELEMENTAL_WISP.get(), level, MobSpawnType.NATURAL, pos, random), "上限够大：放行");
        } finally {
            ECWildMobs.Config.setTest("elemental_wisp.max_nearby", null);
            ECWildMobs.Config.setTest("elemental_wisp.nearby_radius", null);
            placed.forEach(Entity::discard);
        }
        helper.succeed();
    }

    /** 群系修改器：主世界的平原刷怪表里有元素游灵（权重 12），带刷怪花费；每个主世界群系里挤占原版怪不超过 3% */
    @GameTest(template = EMPTY)
    public static void wispBiomeSpawnsAndSqueeze(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        Biome plains = biomes.getOrThrow(Biomes.PLAINS);
        MobSpawnSettings.SpawnerData data = plains.getMobSettings().getMobs(MobCategory.MONSTER).unwrap().stream()
                .filter(entry -> entry.type == ECWildMobs.ELEMENTAL_WISP.get()).findFirst().orElse(null);
        helper.assertTrue(data != null && data.getWeight().asInt() == 12, "平原的怪物表里有元素游灵，权重 12");
        helper.assertTrue(plains.getMobSettings().getMobSpawnCost(ECWildMobs.ELEMENTAL_WISP.get()) != null, "平原给元素游灵加了刷怪花费");
        helper.assertTrue(biomes.getOrThrow(Biomes.NETHER_WASTES).getMobSettings().getMobs(MobCategory.MONSTER).unwrap().stream()
                .noneMatch(entry -> entry.type == ECWildMobs.ELEMENTAL_WISP.get()), "下界没有");
        List<String> squeezed = new ArrayList<>();
        biomes.holders().forEach(holder -> {
            if (!holder.is(net.minecraft.tags.BiomeTags.IS_OVERWORLD)) {
                return;
            }
            // 挤占按原版刷怪表算（batch13.md §5.1「原版 MONSTER 合计权重」）：别的模组、本模组风法师的条目不算进分母
            int wisp = 0;
            int vanilla = 0;
            for (MobSpawnSettings.SpawnerData entry : holder.value().getMobSettings().getMobs(MobCategory.MONSTER).unwrap()) {
                if (entry.type == ECWildMobs.ELEMENTAL_WISP.get()) {
                    wisp += entry.getWeight().asInt();
                } else if ("minecraft".equals(net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(entry.type).getNamespace())) {
                    vanilla += entry.getWeight().asInt();
                }
            }
            // 原版本来就不刷怪的群系（蘑菇岛、深暗之域）谈不上挤占，刷怪判定另外排除了它们
            if (wisp > 0 && vanilla > 0 && (double) wisp / (wisp + vanilla) > 0.03D) {
                squeezed.add(holder.key().location() + " " + wisp + "/" + (wisp + vanilla));
            }
        });
        helper.assertTrue(squeezed.isEmpty(), "挤占超过 3%：" + squeezed);
        helper.succeed();
    }

    /** 掉落：公平击杀按元素掉对应魔晶（约 50%，不掉别的元素）；不公平（刷怪笼）什么都不掉；精英多掉两个 */
    @GameTest(template = EMPTY)
    public static void wispLootByElement(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "wisp-loot");
        ResourceLocation table = ECWildMobs.ELEMENTAL_WISP.get().getDefaultLootTable();
        noFatigue(true);
        try {
            ElementalWispEntity fire = wisp(helper, Element.FIRE);
            SpawnOrigin.set(fire, Origin.NATURAL);
            MobDamageShare.record(fire, player.getUUID(), 12F);
            int fireDrops = 0;
            int wrong = 0;
            for (int i = 0; i < 400; i++) {
                List<ItemStack> stacks = drops(level, table, fire, player, 7919L * (i + 1));
                fireDrops += count(stacks, ECMaterials.FIRE_DROPS.get());
                wrong += count(stacks, ECMaterials.WATER_DROPS.get()) + count(stacks, ECMaterials.EARTH_DROPS.get()) + count(stacks, ECMaterials.AIR_DROPS.get());
            }
            helper.assertTrue(fireDrops > 140 && fireDrops < 260, "火灵公平击杀约 50% 掉火之魔晶（400 次），实际 " + fireDrops);
            helper.assertTrue(wrong == 0, "普通火灵不掉别的元素，实际 " + wrong);

            ElementalWispEntity air = wisp(helper, Element.AIR);
            SpawnOrigin.set(air, Origin.NATURAL);
            MobDamageShare.record(air, player.getUUID(), 12F);
            int airDrops = 0;
            for (int i = 0; i < 100; i++) {
                airDrops += count(drops(level, table, air, player, 104729L * (i + 1)), ECMaterials.AIR_DROPS.get());
            }
            helper.assertTrue(airDrops > 20, "风灵掉风之魔晶，实际 " + airDrops);

            ElementalWispEntity caged = wisp(helper, Element.FIRE);
            SpawnOrigin.set(caged, Origin.SPAWNER);
            MobDamageShare.record(caged, player.getUUID(), 12F);
            for (int i = 0; i < 50; i++) {
                helper.assertTrue(drops(level, table, caged, player, 31L * (i + 1)).isEmpty(), "刷怪笼来的不掉东西");
            }

            ElementalWispEntity elite = wisp(helper, Element.EARTH);
            elite.setElite(true);
            SpawnOrigin.set(elite, Origin.NATURAL);
            MobDamageShare.record(elite, player.getUUID(), 30F);
            for (int i = 0; i < 20; i++) {
                List<ItemStack> stacks = drops(level, table, elite, player, 6007L * (i + 1));
                int total = count(stacks, ECMaterials.FIRE_DROPS.get()) + count(stacks, ECMaterials.WATER_DROPS.get())
                        + count(stacks, ECMaterials.EARTH_DROPS.get()) + count(stacks, ECMaterials.AIR_DROPS.get());
                helper.assertTrue(total >= 2, "元素聚灵至少掉两个魔晶，实际 " + total);
            }
        } finally {
            noFatigue(false);
        }
        helper.succeed();
    }

    /** 精英「元素聚灵」：元素弹按火、水、土、风轮着射；打中当前目标造成 mob_bolt 伤害；不是技能目标的（牛）穿过去不伤 */
    @GameTest(template = EMPTY, timeoutTicks = 80)
    public static void wispEliteBoltCycle(GameTestHelper helper) {
        // 东边、南边各一堵墙：风弹把目标往东掀、射向牛的那发往南飞，都留在这块场地里，不飞进旁边的测试
        walls(helper);
        ElementalWispEntity wisp = wisp(helper, Element.FIRE);
        wisp.setElite(true);
        helper.getLevel().addFreshEntity(wisp);
        wisp.setNoAi(true);
        Husk target = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(3, 2, 1));
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(1, 2, 3));
        wisp.setTarget(target);
        List<Element> order = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            order.add(wisp.nextBoltElement());
            WispBoltEntity bolt = wisp.fireBolt(i == 0 ? target : target);
            helper.assertTrue(bolt.getElement() == order.get(i), "第 " + (i + 1) + " 发的元素和预告的一样");
        }
        helper.assertTrue(order.equals(List.of(Element.FIRE, Element.WATER, Element.EARTH, Element.AIR)), "火、水、土、风轮着来，实际 " + order);
        helper.assertTrue(wisp.nextBoltElement() == Element.FIRE, "第五发回到火");
        WispBoltEntity stray = wisp.fireBolt(cow);
        float cowHealth = cow.getHealth();
        helper.succeedWhen(() -> {
            helper.assertTrue(target.getHealth() < target.getMaxHealth() && lastHitIs(target, ECDamageTypes.MOB_BOLT), "元素弹打中目标，按 mob_bolt 算");
            helper.assertTrue(stray.isRemoved(), "射向牛的那发穿过牛、撞墙散了");
            helper.assertTrue(cow.getHealth() == cowHealth, "牛不是技能目标，不伤");
            discard(wisp, target, cow);
        });
    }

    /** 5×5 场地东边（x = 4）、南边（z = 4）各砌一堵三格高的墙 */
    private static void walls(GameTestHelper helper) {
        for (int i = 0; i <= 4; i++) {
            for (int y = 1; y <= 3; y++) {
                helper.setBlock(new BlockPos(4, y, i), Blocks.STONE);
                helper.setBlock(new BlockPos(i, y, 4), Blocks.STONE);
            }
        }
    }

    /**
     * 怪物弹射物穿过的方块一概不碰（policy §4.18「撞方块不触发方块效果」，同二分枪的弹）：元素聚灵弹贴着地飞过木压力板和绊线，
     * 板子不压下、绊线和两头的钩子都不通电；疾风弹、元素聚灵弹都不换世界。对照：原版的箭（关掉重力）飞同一条道，两样都会触发。
     * 传送门那一半只断言 {@code canChangeDimensions}：GameTest 里下界没有出口传送门，非玩家实体本来就换不过去，摆一格传送门测不出东西。
     */
    @GameTest(template = EMPTY)
    public static void wildBoltsIgnoreBlockTriggers(GameTestHelper helper) {
        triggerLane(helper);
        ServerLevel level = helper.getLevel();
        WispBoltEntity bolt = new WispBoltEntity(level, wisp(helper, Element.FIRE), Element.FIRE);
        helper.assertFalse(flyThroughLane(helper, bolt, new Vec3(0D, 0D, 0.45D), 12), "元素聚灵弹飞过压力板和绊线：都不触发");
        StormGustEntity gust = ECWildMobs.STORM_GUST.get().create(level);
        helper.assertTrue(gust != null && gust.isIgnoringBlockTriggers() && !gust.canChangeDimensions()
                && bolt.isIgnoringBlockTriggers() && !bolt.canChangeDimensions(), "疾风弹、元素聚灵弹：不踩机关、不换世界");
        Arrow arrow = new Arrow(level, 0D, 0D, 0D);
        arrow.setNoGravity(true);
        helper.assertTrue(flyThroughLane(helper, arrow, new Vec3(0D, 0D, 0.45D), 12), "对照：原版的箭飞过同一条道会触发");
        helper.succeed();
    }

    /**
     * 弹道上的机关（{@link #flyThroughLane} 用，占 5×5 场地 x 0~4、z 0~4）：y = 1 铺满石头；(2, 2, 1) 一块木压力板（什么实体都算）；
     * (2, 2, 3) 一段绊线，两头 (1, 2, 3)、(3, 2, 3) 的绊线钩挂在 (0, 2, 3)、(4, 2, 3) 的石头上。
     */
    static void triggerLane(GameTestHelper helper) {
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        helper.setBlock(new BlockPos(2, 2, 1), Blocks.OAK_PRESSURE_PLATE);
        helper.setBlock(new BlockPos(0, 2, 3), Blocks.STONE);
        helper.setBlock(new BlockPos(4, 2, 3), Blocks.STONE);
        helper.setBlock(new BlockPos(1, 2, 3), Blocks.TRIPWIRE_HOOK.defaultBlockState().setValue(TripWireHookBlock.FACING, Direction.EAST));
        helper.setBlock(new BlockPos(3, 2, 3), Blocks.TRIPWIRE_HOOK.defaultBlockState().setValue(TripWireHookBlock.FACING, Direction.WEST));
        helper.setBlock(new BlockPos(2, 2, 3), Blocks.TRIPWIRE);
        helper.assertTrue(helper.getBlockState(new BlockPos(1, 2, 3)).getValue(TripWireHookBlock.ATTACHED)
                && helper.getBlockState(new BlockPos(3, 2, 3)).getValue(TripWireHookBlock.ATTACHED), "两头的绊线钩应连上绊线");
    }

    /**
     * 把 projectile 放进世界（压力板、绊线只数世界里的实体），从 (2.5, 2.02, 0.2) 以 velocity 起飞，测试自己推 ticks 刻
     * （同一段同步代码里，服务器不会另外再推它），推完拿走。返回途中 {@link #triggerLane} 的机关有没有被触发过。
     */
    static boolean flyThroughLane(GameTestHelper helper, Entity projectile, Vec3 velocity, int ticks) {
        Vec3 start = helper.absoluteVec(new Vec3(2.5D, 2.02D, 0.2D));
        projectile.moveTo(start.x, start.y, start.z, 0F, 0F);
        projectile.setDeltaMovement(velocity);
        helper.getLevel().addFreshEntity(projectile);
        boolean triggered = false;
        try {
            for (int i = 0; i < ticks && !projectile.isRemoved(); i++) {
                projectile.tick();
                triggered |= laneTriggered(helper);
            }
        } finally {
            projectile.discard();
        }
        return triggered;
    }

    private static boolean laneTriggered(GameTestHelper helper) {
        return helper.getBlockState(new BlockPos(2, 2, 1)).getValue(PressurePlateBlock.POWERED)
                || helper.getBlockState(new BlockPos(2, 2, 3)).getValue(TripWireBlock.POWERED)
                || helper.getBlockState(new BlockPos(1, 2, 3)).getValue(TripWireHookBlock.POWERED)
                || helper.getBlockState(new BlockPos(3, 2, 3)).getValue(TripWireHookBlock.POWERED);
    }

    // ================================================================== 矩阵游魂

    /** 追加刷怪：附近 24 格有 ≥ 5 000、没被压着的聚集体才加（同一个对象）；太小、太远、被压着、别的类别、权重 0 都不加 */
    @GameTest(template = EMPTY)
    public static void wraithOfferedNearPresence(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 origin = lifted(helper, LIFT_SPAWNS);
        BlockPos pos = BlockPos.containing(origin);
        helper.assertFalse(wraithOffered(level, pos, MobCategory.MONSTER), "测试前提：附近没有聚集体时不加");
        MRUPresenceEntity small = presence(level, origin.add(5D, 0D, 0D), 4000);
        MRUPresenceEntity far = presence(level, origin.add(30D, 0D, 0D), 9000);
        try {
            helper.assertFalse(wraithOffered(level, pos, MobCategory.MONSTER), "4 000 MRU 不够、30 格太远：不加");
            MRUPresenceEntity big = presence(level, origin.add(10D, 0D, 0D), 6000);
            try {
                helper.assertTrue(wraithOffered(level, pos, MobCategory.MONSTER), "10 格内 6 000：加");
                helper.assertTrue(MatrixWraithEvents.spawnerData() == MatrixWraithEvents.spawnerData(), "两次问拿到同一个对象（原版核对按对象比）");
                helper.assertFalse(wraithOffered(level, pos, MobCategory.CREATURE), "动物类别不加");
                big.stabilize(200);
                helper.assertFalse(wraithOffered(level, pos, MobCategory.MONSTER), "被稳定器压着：不加");
                big.stabilize(0);
                try {
                    ECWildMobs.Config.setTest("matrix_wraith.spawn_weight", 0);
                    helper.assertFalse(wraithOffered(level, pos, MobCategory.MONSTER), "权重填 0：关掉");
                } finally {
                    ECWildMobs.Config.setTest("matrix_wraith.spawn_weight", null);
                }
            } finally {
                discard(big);
            }
        } finally {
            discard(small, far);
        }
        helper.succeed();
    }

    /** 每团聚集体附近最多 3 只（刷怪判定拦）；上限填 0 一只都不刷 */
    @GameTest(template = EMPTY)
    public static void wraithCapPerPresence(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 origin = lifted(helper, LIFT_CAP);
        BlockPos pos = BlockPos.containing(origin);
        MRUPresenceEntity anchor = presence(level, origin.add(4D, 0D, 0D), 8000);
        List<MatrixWraithEntity> wraiths = new ArrayList<>();
        try {
            for (int i = 0; i < 3; i++) {
                helper.assertTrue(MatrixWraithEntity.roomNearAnchor(level, pos), "已有 " + i + " 只：还能刷");
                MatrixWraithEntity wraith = ECWildMobs.MATRIX_WRAITH.get().create(level);
                wraith.moveTo(origin.x + i, origin.y, origin.z, 0F, 0F);
                wraith.setNoAi(true);
                wraith.setNoGravity(true);
                level.addFreshEntity(wraith);
                wraiths.add(wraith);
            }
            helper.assertTrue(MatrixWraithEntity.countNear(level, anchor) == 3, "这团附近 3 只");
            helper.assertFalse(MatrixWraithEntity.roomNearAnchor(level, pos), "满 3 只：不再刷");
            helper.assertTrue(anchor.getMRU() == 8000, "不是自然刷出的（直接放进世界）不抽 MRU");
            wraiths.remove(0).discard();
            try {
                ECWildMobs.Config.setTest("matrix_wraith.max_per_presence", 0);
                helper.assertFalse(MatrixWraithEntity.roomNearAnchor(level, pos), "上限填 0：不刷");
            } finally {
                ECWildMobs.Config.setTest("matrix_wraith.max_per_presence", null);
            }
            helper.assertTrue(MatrixWraithEntity.roomNearAnchor(level, pos), "走了一只：又能刷");
        } finally {
            wraiths.forEach(Entity::discard);
            discard(anchor);
        }
        helper.succeed();
    }

    /** 自然刷出：来源改记事件、进世界时抽 300 MRU；刷怪蛋来的不抽；抽取量填 0 不抽 */
    @GameTest(template = EMPTY)
    public static void wraithDrainsOnNaturalJoin(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 origin = lifted(helper, LIFT_DRAIN);
        MRUPresenceEntity anchor = presence(level, origin.add(3D, 0D, 0D), 6000);
        List<Entity> spawned = new ArrayList<>();
        ECMobFramework.Config.setTestEliteChance(0D);
        try {
            MatrixWraithEntity natural = finalizedWraith(level, origin, MobSpawnType.NATURAL);
            helper.assertTrue(SpawnOrigin.of(natural) == Origin.EVENT, "聚集体招来的记作事件来源，实际 " + SpawnOrigin.of(natural));
            helper.assertTrue(anchor.getMRU() == 6000, "还没进世界：不抽");
            level.addFreshEntity(natural);
            spawned.add(natural);
            helper.assertTrue(anchor.getMRU() == 6000 - ECWildMobs.Config.wraithDrainMru(), "进世界抽 300，实际剩 " + anchor.getMRU());

            MatrixWraithEntity egg = finalizedWraith(level, origin, MobSpawnType.SPAWN_EGG);
            level.addFreshEntity(egg);
            spawned.add(egg);
            helper.assertTrue(anchor.getMRU() == 5700, "刷怪蛋来的不抽");
            helper.assertTrue(SpawnOrigin.of(egg) == Origin.SPAWN_EGG, "刷怪蛋来源照记");

            try {
                ECWildMobs.Config.setTest("matrix_wraith.drain_mru", 0);
                MatrixWraithEntity free = finalizedWraith(level, origin, MobSpawnType.NATURAL);
                level.addFreshEntity(free);
                spawned.add(free);
                helper.assertTrue(anchor.getMRU() == 5700, "抽取量填 0：不抽");
            } finally {
                ECWildMobs.Config.setTest("matrix_wraith.drain_mru", null);
            }

            // 招来以后、进世界之前那团被稳定器压住了（基地里刚关进富集仓）：不抽
            MatrixWraithEntity late = finalizedWraith(level, origin, MobSpawnType.NATURAL);
            anchor.stabilize(200);
            try {
                level.addFreshEntity(late);
                spawned.add(late);
            } finally {
                anchor.stabilize(0);
            }
            helper.assertTrue(anchor.getMRU() == 5700, "被稳定器压着的不抽，实际剩 " + anchor.getMRU());
        } finally {
            ECMobFramework.Config.setTestEliteChance(null);
            spawned.forEach(Entity::discard);
            discard(anchor);
        }
        helper.succeed();
    }

    /** 混合端插件 / 别的模组取消了进世界：聚集体一点不少 */
    @GameTest(template = EMPTY)
    public static void wraithCanceledJoinKeepsMru(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 origin = lifted(helper, LIFT_DRAIN_CANCEL);
        MRUPresenceEntity anchor = presence(level, origin.add(3D, 0D, 0D), 6000);
        ECMobFramework.Config.setTestEliteChance(0D);
        MatrixWraithEntity wraith = finalizedWraith(level, origin, MobSpawnType.NATURAL);
        Consumer<EntityJoinLevelEvent> cancel = event -> {
            if (event.getEntity() == wraith) {
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, EntityJoinLevelEvent.class, cancel);
        boolean added;
        try {
            added = level.addFreshEntity(wraith);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(cancel);
            ECMobFramework.Config.setTestEliteChance(null);
        }
        helper.assertFalse(added, "测试前提：进世界被取消");
        helper.assertTrue(anchor.getMRU() == 6000, "被取消的不抽，实际剩 " + anchor.getMRU());
        discard(anchor);
        helper.succeed();
    }

    private static MatrixWraithEntity finalizedWraith(ServerLevel level, Vec3 at, MobSpawnType reason) {
        MatrixWraithEntity wraith = ECWildMobs.MATRIX_WRAITH.get().create(level);
        wraith.moveTo(at.x, at.y, at.z, 0F, 0F);
        wraith.setNoGravity(true);
        ForgeEventFactory.onFinalizeSpawn(wraith, level, level.getCurrentDifficultyAt(wraith.blockPosition()), reason, null, null);
        wraith.setNoAi(true);
        return wraith;
    }

    /** 抓实了给被抓的那个人加 150 矩阵损伤（别人不加）；被拦下的、创造模式、配置 0 不加；精英照样 */
    @GameTest(template = EMPTY)
    public static void wraithHitAddsMatrixDamage(GameTestHelper helper) {
        ServerPlayer a = victim(helper, "wraith-a");
        ServerPlayer b = victim(helper, "wraith-b");
        MatrixWraithEntity wraith = helper.spawnWithNoFreeWill(ECWildMobs.MATRIX_WRAITH.get(), new BlockPos(2, 2, 2));
        int perHit = ECWildMobs.Config.wraithMatrixDamage();
        data(a).setMatrixDamage(0);
        data(b).setMatrixDamage(0);
        helper.assertTrue(wraith.doHurtTarget(a), "抓中了");
        helper.assertTrue(data(a).getMatrixDamage() == perHit, "被抓的加 " + perHit + "，实际 " + data(a).getMatrixDamage());
        helper.assertTrue(data(b).getMatrixDamage() == 0, "旁边的人不加");

        Consumer<LivingDamageEvent> block = event -> {
            if (event.getEntity() == b) {
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, LivingDamageEvent.class, block);
        try {
            b.invulnerableTime = 0;
            wraith.doHurtTarget(b);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(block);
        }
        helper.assertTrue(data(b).getMatrixDamage() == 0, "被插件拦下的那一下不加");

        a.invulnerableTime = 0;
        try {
            ECWildMobs.Config.setTest("matrix_wraith.matrix_damage_per_hit", 0);
            wraith.doHurtTarget(a);
        } finally {
            ECWildMobs.Config.setTest("matrix_wraith.matrix_damage_per_hit", null);
        }
        helper.assertTrue(data(a).getMatrixDamage() == perHit, "配置填 0：不加");

        helper.assertTrue(MatrixWraithEvents.addMatrixDamage(a, 0) == 0, "加 0 什么都不做");
        a.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        helper.assertTrue(MatrixWraithEvents.addMatrixDamage(a, perHit) == 0 && data(a).getMatrixDamage() == perHit, "创造模式不加");
        helper.assertTrue(MatrixWraithEvents.addMatrixDamage(ProtectionFrameworkTests.fakePlayer(helper), perHit) == 0, "假玩家不加");
        discard(wraith);
        helper.succeed();
    }

    /** 弹射物只吃一半、近战照常；精英闪到目标背后 2 格，传送事件被取消就不闪 */
    @GameTest(template = EMPTY)
    public static void wraithProjectileResistAndEliteBlink(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(2, 2, 4));
        Arrow arrow = new Arrow(level, husk);
        MatrixWraithEntity wraith = helper.spawnWithNoFreeWill(ECWildMobs.MATRIX_WRAITH.get(), new BlockPos(0, 2, 0));
        helper.assertTrue(Math.abs(MobWeakness.multiplier(wraith, level.damageSources().arrow(arrow, husk)) - 0.5F) < EPS, "箭只吃一半");
        helper.assertTrue(Math.abs(MobWeakness.multiplier(wraith, level.damageSources().mobAttack(husk)) - 1F) < EPS, "近战照常");

        wraith.setElite(true);
        // 目标面朝南（+Z），背后就是北边 2 格
        Vec3 feet = helper.absoluteVec(new Vec3(2.5D, 2D, 3.5D));
        husk.moveTo(feet.x, feet.y, feet.z, 0F, 0F);
        husk.setYHeadRot(0F);
        Consumer<EntityTeleportEvent.EnderEntity> cancel = event -> {
            if (event.getEntity() == wraith) {
                event.setCanceled(true);
            }
        };
        Vec3 before = wraith.position();
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, EntityTeleportEvent.EnderEntity.class, cancel);
        try {
            helper.assertFalse(wraith.tryBlinkBehind(husk), "传送事件被取消：不闪");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(cancel);
        }
        helper.assertTrue(wraith.position().distanceTo(before) < EPS, "被取消时原地不动");
        helper.assertTrue(wraith.tryBlinkBehind(husk), "闪到背后");
        Vec3 behind = feet.subtract(husk.getViewVector(1F).multiply(1D, 0D, 1D).normalize().scale(2D));
        helper.assertTrue(Math.abs(wraith.getX() - behind.x) < 0.01D && Math.abs(wraith.getZ() - behind.z) < 0.01D,
                "落在目标背后 2 格，实际 " + wraith.position() + "，应为 " + behind);
        discard(husk, arrow, wraith);
        helper.succeed();
    }

    /** 掉落：公平击杀 1~2 个 MRU 碎片；不公平什么都不掉；精英再加 2~4 个 */
    @GameTest(template = EMPTY)
    public static void wraithLoot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "wraith-loot");
        ResourceLocation table = ECWildMobs.MATRIX_WRAITH.get().getDefaultLootTable();
        noFatigue(true);
        try {
            MatrixWraithEntity wraith = ECWildMobs.MATRIX_WRAITH.get().create(level);
            wraith.moveTo(helper.absoluteVec(new Vec3(1.5D, 2D, 1.5D)));
            SpawnOrigin.set(wraith, Origin.EVENT);
            MobDamageShare.record(wraith, player.getUUID(), 26F);
            for (int i = 0; i < 40; i++) {
                int shards = count(drops(level, table, wraith, player, 977L * (i + 1)), ECMaterials.MRU_SHARD.get());
                helper.assertTrue(shards >= 1 && shards <= 2, "普通游魂掉 1~2 个，实际 " + shards);
            }
            wraith.setElite(true);
            for (int i = 0; i < 40; i++) {
                int shards = count(drops(level, table, wraith, player, 1543L * (i + 1)), ECMaterials.MRU_SHARD.get());
                helper.assertTrue(shards >= 3 && shards <= 6, "矩阵怨灵掉 3~6 个，实际 " + shards);
            }
            MatrixWraithEntity caged = ECWildMobs.MATRIX_WRAITH.get().create(level);
            caged.moveTo(helper.absoluteVec(new Vec3(1.5D, 2D, 1.5D)));
            SpawnOrigin.set(caged, Origin.SPAWN_EGG);
            MobDamageShare.record(caged, player.getUUID(), 26F);
            helper.assertTrue(drops(level, table, caged, player, 3L).isEmpty(), "刷怪蛋来的不掉");
        } finally {
            noFatigue(false);
        }
        helper.succeed();
    }

    // ================================================================== 风暴术士

    /** 类型 3：生命 50、护甲 2、回满血；存盘读档生命照旧、修饰符不叠两层；配置改了读档按新值；变回大法师就摘掉 */
    @GameTest(template = EMPTY)
    public static void stormAttributesSurviveReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WindMageEntity mage = storm(helper);
        helper.assertTrue(mage.isStormSorcerer() && mage.getMageType() == WindMageEntity.STORM_SORCERER, "类型 3");
        helper.assertTrue(Math.abs(mage.getMaxHealth() - 50F) < EPS && Math.abs(mage.getHealth() - 50F) < EPS, "生命 50、回满，实际 "
                + mage.getHealth() + "/" + mage.getMaxHealth());
        helper.assertTrue(Math.abs(mage.getAttributeValue(Attributes.ARMOR) - 2D) < EPS, "护甲 2");
        helper.assertTrue(WindMageEntity.STORM_NAME_KEY.equals(mage.getName().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t ? t.getKey() : ""),
                "名字是风暴术士");
        mage.setHealth(30F);
        net.minecraft.nbt.CompoundTag tag = mage.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        WindMageEntity loaded = ECWindMage.WIND_MAGE.get().create(level);
        loaded.load(tag);
        helper.assertTrue(loaded.isStormSorcerer(), "读回来还是风暴术士");
        helper.assertTrue(Math.abs(loaded.getMaxHealth() - 50F) < EPS, "读档不叠两层，实际 " + loaded.getMaxHealth());
        helper.assertTrue(Math.abs(loaded.getHealth() - 30F) < EPS, "生命照存档 30，实际 " + loaded.getHealth());
        helper.assertTrue(loaded.getAttribute(Attributes.MAX_HEALTH).getModifiers().size() == 1, "生命只挂一个修饰符");
        try {
            ECWildMobs.Config.setTest("storm_sorcerer.max_health", 60D);
            WindMageEntity retuned = ECWindMage.WIND_MAGE.get().create(level);
            retuned.load(tag);
            helper.assertTrue(Math.abs(retuned.getMaxHealth() - 60F) < EPS && Math.abs(retuned.getHealth() - 30F) < EPS,
                    "配置改成 60：读档按新上限、生命不动，实际 " + retuned.getHealth() + "/" + retuned.getMaxHealth());
        } finally {
            ECWildMobs.Config.setTest("storm_sorcerer.max_health", null);
        }
        loaded.setMageType(WindMageEntity.ARCHMAGE);
        helper.assertTrue(Math.abs(loaded.getMaxHealth() - 20F) < EPS && loaded.getHealth() <= 20F, "变回大法师：生命上限回到 20");
        helper.assertTrue(loaded.getAttributeValue(Attributes.ARMOR) < EPS, "护甲摘掉");
        helper.assertTrue(WindMageEntity.LOOT_TABLES.get(WindMageEntity.STORM_SORCERER).equals(mage.getLootTable()), "掉落表 wind_mage_storm");
        helper.succeed();
    }

    /** 几率：打雷 20%、海纳尔 10%、不打雷 0；只有自然 / 事件来源掷；配置 0 不出；13d 的倍数钩子相乘、摘掉复原 */
    @GameTest(template = EMPTY)
    public static void stormRollChances(GameTestHelper helper) {
        WindMageEntity mage = ECWindMage.WIND_MAGE.get().create(helper.getLevel());
        helper.assertTrue(Math.abs(StormSorcerer.chance(null, false, true) - 0.2D) < EPS, "打雷 20%");
        helper.assertTrue(Math.abs(StormSorcerer.chance(null, true, false) - 0.1D) < EPS, "海纳尔 10%");
        helper.assertTrue(StormSorcerer.chance(null, false, false) == 0D, "晴天 0");
        ToDoubleFunction<WindMageEntity> doubled = m -> 2D;
        StormSorcerer.addChanceMultiplier(doubled);
        try {
            helper.assertTrue(Math.abs(StormSorcerer.chance(mage, false, true) - 0.4D) < EPS, "倍数钩子 ×2：40%");
        } finally {
            StormSorcerer.removeChanceMultiplier(doubled);
        }
        helper.assertTrue(Math.abs(StormSorcerer.chance(mage, false, true) - 0.2D) < EPS, "摘掉钩子复原");

        RandomSource random = RandomSource.create(42L);
        try {
            ECWildMobs.Config.setTest("storm_sorcerer.thunder_chance", 1D);
            ECWildMobs.Config.setTest("storm_sorcerer.hoanna_chance", 1D);
            SpawnOrigin.set(mage, Origin.NATURAL);
            helper.assertTrue(StormSorcerer.roll(mage, false, true, random), "自然刷出 + 打雷 + 几率 1：出");
            helper.assertTrue(StormSorcerer.roll(mage, true, false, random), "海纳尔 + 几率 1：出");
            helper.assertFalse(StormSorcerer.roll(mage, false, false, random), "不打雷：不出");
            SpawnOrigin.set(mage, Origin.EVENT);
            helper.assertTrue(StormSorcerer.roll(mage, false, true, random), "事件来源：出");
            for (Origin origin : new Origin[]{Origin.SPAWNER, Origin.SPAWN_EGG, Origin.SUMMONED, Origin.OTHER}) {
                SpawnOrigin.set(mage, origin);
                helper.assertFalse(StormSorcerer.roll(mage, false, true, random), origin + " 来源不掷");
            }
            SpawnOrigin.set(mage, Origin.NATURAL);
            ECWildMobs.Config.setTest("storm_sorcerer.thunder_chance", 0D);
            helper.assertFalse(StormSorcerer.roll(mage, false, true, random), "配置填 0：不出");
        } finally {
            ECWildMobs.Config.setTest("storm_sorcerer.thunder_chance", null);
            ECWildMobs.Config.setTest("storm_sorcerer.hoanna_chance", null);
        }
        // 刷怪蛋生成走一遍 finalizeSpawn：只在原作三种里挑
        for (int i = 0; i < 30; i++) {
            WindMageEntity egg = ECWindMage.WIND_MAGE.get().create(helper.getLevel());
            Vec3 at = helper.absoluteVec(new Vec3(1.5D, 2D, 1.5D));
            egg.moveTo(at.x, at.y, at.z, 0F, 0F);
            ForgeEventFactory.onFinalizeSpawn(egg, helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(egg.blockPosition()),
                    MobSpawnType.SPAWN_EGG, null, null);
            helper.assertTrue(egg.getMageType() < WindMageEntity.CLASSIC_TYPE_COUNT, "刷怪蛋出来的不是风暴术士");
        }
        helper.succeed();
    }

    /** 疾风弹：顺着飞行方向推开、略往上抬，击退抗性满了推不动；真射出去打中当前目标（mob_bolt），旁边的牛不伤；风暴术士的远程攻击换成疾风弹 */
    @GameTest(template = EMPTY, timeoutTicks = 80)
    public static void stormGustPushesAndHits(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Husk loose = EntityType.HUSK.create(level);
        loose.setDeltaMovement(Vec3.ZERO);
        StormGustEntity.push(loose, new Vec3(0D, 0D, 2D));
        double knock = ECWildMobs.Config.stormGustKnockback();
        helper.assertTrue(Math.abs(loose.getDeltaMovement().z - knock) < 0.01D && loose.getDeltaMovement().y > 0.2D,
                "往 +Z 推 " + knock + "、往上抬，实际 " + loose.getDeltaMovement());
        Husk heavy = EntityType.HUSK.create(level);
        heavy.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1D);
        heavy.setDeltaMovement(Vec3.ZERO);
        StormGustEntity.push(heavy, new Vec3(0D, 0D, 2D));
        helper.assertTrue(heavy.getDeltaMovement().lengthSqr() < EPS, "击退抗性满了推不动");

        // 墙挡住被推开的目标和射向牛的那发，不飞进旁边的测试
        walls(helper);
        WindMageEntity mage = helper.spawnWithNoFreeWill(ECWindMage.WIND_MAGE.get(), new BlockPos(1, 2, 1));
        mage.setMageType(WindMageEntity.STORM_SORCERER);
        Husk target = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(1, 2, 3));
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(3, 2, 1));
        mage.setTarget(target);
        mage.performRangedAttack(target, 1F);
        helper.assertTrue(level.getEntitiesOfClass(StormGustEntity.class, mage.getBoundingBox().inflate(3D), gust -> gust.getOwner() == mage).size() == 1,
                "风暴术士的远程攻击射出疾风弹");
        helper.assertTrue(level.getEntitiesOfClass(com.flameforge.essentialcraft5.entity.MRUArrowEntity.class, mage.getBoundingBox().inflate(3D)).isEmpty(),
                "不再射 MRU 箭");
        StormGustEntity stray = StormSorcerer.shootGust(mage, cow);
        float cowHealth = cow.getHealth();
        helper.succeedWhen(() -> {
            helper.assertTrue(target.getHealth() < target.getMaxHealth() && lastHitIs(target, ECDamageTypes.MOB_BOLT), "疾风弹打中目标，按 mob_bolt 算");
            helper.assertTrue(stray.isRemoved(), "射向牛的那发穿过牛、撞墙散了");
            helper.assertTrue(cow.getHealth() == cowHealth, "牛不是技能目标，不伤");
            discard(mage, target, cow);
        });
    }

    /** 预警落雷：标点后第 20 刻才劈；劈圈里的技能目标 8 点 aberrant_strike，圈里不是目标的不劈；走开就躲过；这招关掉（伤害 0）不标 */
    @GameTest(template = EMPTY)
    public static void stormLightningWarnedStrike(GameTestHelper helper) {
        WindMageEntity mage = storm(helper);
        Husk target = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(3, 2, 3));
        Husk bystander = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(3, 2, 3));
        bystander.moveTo(target.getX() + 1D, target.getY(), target.getZ(), 0F, 0F);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(3, 2, 4));
        cow.moveTo(target.getX(), target.getY(), target.getZ() + 0.8D, 0F, 0F);
        mage.setTarget(target);
        helper.assertFalse(StormSorcerer.mark(mage, bystander), "不是当前目标的怪不能标");
        // 多人：创造模式的不标，生存的标（下面重新标在怪脚下，这一道作废）
        ServerPlayer creative = player(helper, "storm-creative");
        creative.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        helper.assertFalse(StormSorcerer.mark(mage, creative), "创造模式的玩家不标");
        ServerPlayer survivor = player(helper, "storm-survivor");
        helper.assertTrue(StormSorcerer.mark(mage, survivor), "生存模式的玩家照标");
        helper.assertTrue(StormSorcerer.pendingStrike(mage) != null && StormSorcerer.pendingStrike(mage).distanceTo(survivor.position()) < EPS,
                "落点在那名玩家脚下");
        helper.assertTrue(StormSorcerer.mark(mage, target), "标在目标脚下");
        helper.assertTrue(StormSorcerer.pendingStrike(mage) != null, "有个落点");
        int warning = ECWildMobs.Config.stormLightningWarning();
        for (int i = 1; i < warning; i++) {
            StormSorcerer.tickStrike(mage);
        }
        helper.assertTrue(target.getHealth() == target.getMaxHealth() && StormSorcerer.pendingStrike(mage) != null, "预警 " + warning + " 刻里不劈");
        StormSorcerer.tickStrike(mage);
        helper.assertTrue(StormSorcerer.pendingStrike(mage) == null, "到点劈下");
        float expected = target.getMaxHealth() - (float) ECWildMobs.Config.stormLightningDamage();
        helper.assertTrue(Math.abs(target.getHealth() - expected) < 0.01F && lastHitIs(target, ECDamageTypes.ABERRANT_STRIKE),
                "目标挨 8 点 aberrant_strike，实际剩 " + target.getHealth());
        helper.assertTrue(bystander.getHealth() == bystander.getMaxHealth(), "圈里不是目标的怪不劈");
        helper.assertTrue(cow.getHealth() == cow.getMaxHealth(), "牛不劈");

        // 走开就躲过
        target.invulnerableTime = 0;
        float before = target.getHealth();
        helper.assertTrue(StormSorcerer.mark(mage, target), "再标一次（不看冷却）");
        target.moveTo(target.getX() + 4D, target.getY(), target.getZ(), 0F, 0F);
        for (int i = 0; i < warning; i++) {
            StormSorcerer.tickStrike(mage);
        }
        helper.assertTrue(target.getHealth() == before, "走开 4 格：躲过");

        // 术士在预警期间倒下：这道雷作废（死亡动画那 20 刻实体还在 tick）
        target.moveTo(target.getX() - 4D, target.getY(), target.getZ(), 0F, 0F);
        helper.assertTrue(StormSorcerer.mark(mage, target), "又标一次");
        mage.setHealth(0F);
        for (int i = 0; i < warning; i++) {
            StormSorcerer.tickStrike(mage);
        }
        helper.assertTrue(target.getHealth() == before && StormSorcerer.pendingStrike(mage) == null, "术士倒下：雷不落，落点清掉");
        mage.setHealth(mage.getMaxHealth());

        try {
            ECWildMobs.Config.setTest("storm_sorcerer.lightning_damage", 0D);
            helper.assertFalse(StormSorcerer.mark(mage, target), "落雷伤害填 0：这招关掉");
        } finally {
            ECWildMobs.Config.setTest("storm_sorcerer.lightning_damage", null);
        }
        discard(target, bystander, cow);
        helper.succeed();
    }

    /** 风暴余息：带标记的受困之风打碎不给好感、不消耗、有提示；不带的照常；带标记的照样能做风之石板 */
    @GameTest(template = EMPTY)
    public static void stormResidueRejectedButCrafts(GameTestHelper helper) {
        ServerPlayer player = player(helper, "residue");
        ItemStack marked = WindAffinity.markStormResidue(new ItemStack(ECWind.WIND_KEEPER.get()));
        helper.assertTrue(WindAffinity.isStormResidue(marked), "打上了风暴余息");
        helper.assertFalse(WindAffinity.isStormResidue(new ItemStack(ECWind.WIND_KEEPER.get())), "普通的没有");
        player.setItemInHand(InteractionHand.MAIN_HAND, marked);
        int before = WindAffinity.points(player);
        InteractionResultHolder<ItemStack> result = ECWind.WIND_KEEPER.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(result.getResult() == InteractionResult.FAIL, "带风暴余息：拒收");
        helper.assertTrue(player.getMainHandItem().getCount() == 1, "不消耗");
        helper.assertTrue(WindAffinity.points(player) == before, "不加好感");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ECWind.WIND_KEEPER.get()));
        ECWind.WIND_KEEPER.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.getMainHandItem().isEmpty(), "普通受困之风照常打碎");
        helper.assertTrue(WindAffinity.points(player) > before, "普通的照常加好感");

        CraftingContainer grid = new TransientCraftingContainer(new DummyMenu(), 3, 3);
        ItemStack focus = new ItemStack(ECEssence.AIR_FOCUS.get());
        ItemStack plating = new ItemStack(ECMaterials.MAGIC_FORTIFIED_PLATING.get());
        ItemStack[] layout = {focus, plating, focus, plating, WindAffinity.markStormResidue(new ItemStack(ECWind.WIND_KEEPER.get())), plating, focus, plating, focus};
        for (int i = 0; i < layout.length; i++) {
            grid.setItem(i, layout[i].copy());
        }
        helper.assertTrue(helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, helper.getLevel()).isPresent(),
                "带风暴余息的受困之风照样能做风之石板");
        helper.assertTrue(BottledWindItem.STORM_RESIDUE_TOOLTIP.startsWith("tooltip."), "提示键");
        helper.succeed();
    }

    /** 掉落：禁锢之风每次都掉；受困之风约 25%（公平 + wind_storm）且都带风暴余息；不公平的不掉受困之风 */
    @GameTest(template = EMPTY)
    public static void stormLootResidue(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "storm-loot");
        ResourceLocation table = WindMageEntity.LOOT_TABLES.get(WindMageEntity.STORM_SORCERER);
        WindMageEntity mage = storm(helper);
        SpawnOrigin.set(mage, Origin.NATURAL);
        MobDamageShare.record(mage, player.getUUID(), 50F);
        noFatigue(true);
        try {
            int keepers = 0;
            for (int i = 0; i < 400; i++) {
                List<ItemStack> stacks = drops(level, table, mage, player, 4243L * (i + 1));
                helper.assertTrue(count(stacks, ECWind.IMPRISONED_WIND.get()) == 1, "每次掉一个禁锢之风");
                for (ItemStack stack : stacks) {
                    if (stack.is(ECWind.WIND_KEEPER.get())) {
                        keepers += stack.getCount();
                        helper.assertTrue(WindAffinity.isStormResidue(stack), "风暴术士掉的受困之风带风暴余息");
                    }
                }
            }
            helper.assertTrue(keepers > 60 && keepers < 140, "受困之风约 25%（400 次），实际 " + keepers);
            WindMageEntity caged = storm(helper);
            SpawnOrigin.set(caged, Origin.SPAWNER);
            MobDamageShare.record(caged, player.getUUID(), 50F);
            for (int i = 0; i < 40; i++) {
                helper.assertTrue(count(drops(level, table, caged, player, 59L * (i + 1)), ECWind.WIND_KEEPER.get()) == 0, "刷怪笼来的不掉受困之风");
            }
        } finally {
            noFatigue(false);
        }
        helper.succeed();
    }

    /** 新怪的名字、精英名、风暴术士名在中英文语言文件里都有；刷怪蛋只放创造栏；两只新怪都按怪物类别刷（和平难度不刷） */
    @GameTest(template = EMPTY)
    public static void wildMobsNamesAndCategory(GameTestHelper helper) {
        helper.assertTrue(ECWildMobs.ELEMENTAL_WISP.get().getCategory() == MobCategory.MONSTER, "元素游灵是怪物类别");
        helper.assertTrue(ECWildMobs.MATRIX_WRAITH.get().getCategory() == MobCategory.MONSTER, "矩阵游魂是怪物类别");
        // 和平难度：怪物类别不自然刷（原版刷怪判定先看难度）；已经在世界里的三只都会被清掉（Mob.checkDespawn 看 shouldDespawnInPeaceful）。
        // 不去改全局难度：同一批别的测试的怪会被一起清掉
        ServerLevel level = helper.getLevel();
        helper.assertTrue(new WispProbe(level).despawnsInPeaceful(), "元素游灵在和平难度会被清掉");
        helper.assertTrue(new WraithProbe(level).despawnsInPeaceful(), "矩阵游魂在和平难度会被清掉");
        WindMageProbe mageProbe = new WindMageProbe(level);
        mageProbe.setMageType(WindMageEntity.STORM_SORCERER);
        helper.assertTrue(mageProbe.despawnsInPeaceful(), "风暴术士在和平难度会被清掉");
        var en = LangFiles.read("en_us");
        var zh = LangFiles.read("zh_cn");
        if (en == null || zh == null) {
            helper.fail("找不到生成的语言文件，先跑 runData");
            return;
        }
        List<String> missing = new ArrayList<>();
        for (String key : new String[]{"entity.essentialcraft5.elemental_wisp", "entity.essentialcraft5.elemental_wisp.elite",
                "entity.essentialcraft5.matrix_wraith", "entity.essentialcraft5.matrix_wraith.elite", WindMageEntity.STORM_NAME_KEY,
                "entity.essentialcraft5.wisp_bolt", "entity.essentialcraft5.storm_gust", BottledWindItem.STORM_RESIDUE_TOOLTIP,
                BottledWindItem.STORM_RESIDUE_REFUSED, "subtitles.essentialcraft5.matrix_wraith.blink", "subtitles.essentialcraft5.storm_sorcerer.mark"}) {
            if (!en.has(key) || !zh.has(key)) {
                missing.add(key);
            }
        }
        helper.assertTrue(missing.isEmpty(), "缺语言键：" + missing);
        helper.succeed();
    }

    /** 读生成的语言文件（同 MobFrameworkTests.modJson） */
    private static final class LangFiles {
        @Nullable
        static com.google.gson.JsonObject read(String name) {
            java.nio.file.Path file = net.minecraftforge.fml.ModList.get().getModFileById(EssentialCraft5.MODID).getFile()
                    .findResource("assets", EssentialCraft5.MODID, "lang", name + ".json");
            if (!java.nio.file.Files.exists(file)) {
                return null;
            }
            try (java.io.Reader reader = java.nio.file.Files.newBufferedReader(file)) {
                return com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            } catch (java.io.IOException e) {
                throw new IllegalStateException("读不了 " + file, e);
            }
        }
    }

    /** 三只怪的探针：把 protected 的 shouldDespawnInPeaceful 露出来（同 MobFrameworkTests.Probe） */
    private static final class WispProbe extends ElementalWispEntity {
        WispProbe(Level level) {
            super(ECWildMobs.ELEMENTAL_WISP.get(), level);
        }

        boolean despawnsInPeaceful() {
            return shouldDespawnInPeaceful();
        }
    }

    private static final class WraithProbe extends MatrixWraithEntity {
        WraithProbe(Level level) {
            super(ECWildMobs.MATRIX_WRAITH.get(), level);
        }

        boolean despawnsInPeaceful() {
            return shouldDespawnInPeaceful();
        }
    }

    private static final class WindMageProbe extends WindMageEntity {
        WindMageProbe(Level level) {
            super(ECWindMage.WIND_MAGE.get(), level);
        }

        boolean despawnsInPeaceful() {
            return shouldDespawnInPeaceful();
        }
    }

    /** TransientCraftingContainer 要一个菜单通知格子变化，测试里用个空的 */
    private static final class DummyMenu extends AbstractContainerMenu {
        DummyMenu() {
            super(null, 0);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }
}
