package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.entity.MRUPresenceEntity;
import com.flameforge.essentialcraft5.entity.MRUPresenceIndex;
import com.flameforge.essentialcraft5.entity.mob.ECFlyingMonster;
import com.flameforge.essentialcraft5.entity.mob.ECMonster;
import com.flameforge.essentialcraft5.entity.mob.MobDamageShare;
import com.flameforge.essentialcraft5.entity.mob.MobSpawnRules;
import com.flameforge.essentialcraft5.entity.mob.MobWeakness;
import com.flameforge.essentialcraft5.item.attunement.AttunementConfig;
import com.flameforge.essentialcraft5.item.attunement.AttunementGains;
import com.flameforge.essentialcraft5.loot.FairKillCondition;
import com.flameforge.essentialcraft5.loot.KillFatigueCondition;
import com.flameforge.essentialcraft5.player.MobKillLedger;
import com.flameforge.essentialcraft5.player.PlayerData;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECComputer;
import com.flameforge.essentialcraft5.registry.ECDemonic;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECMobFramework;
import com.flameforge.essentialcraft5.util.SpawnOrigin;
import com.flameforge.essentialcraft5.util.SpawnOrigin.Origin;
import com.flameforge.essentialcraft5.worldgen.AddSpawnCostsBiomeModifier;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.loot.Deserializers;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.common.world.MobSpawnSettingsBuilder;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 第十三批 13c-Fa「生成与掉落框架」的 GameTest（batch13.md §12.2 13c 行的框架部分）：
 * <ul>
 *   <li>来源：每种生成类型归到哪、哪些公平（增援、转化、没走 finalizeSpawn、指令都不公平），新加的三种来源存档写法；</li>
 *   <li>玩家伤害占比：真玩家和宠物算、假玩家和环境不算、按剩余生命封顶（磨血塔）、被取消的不记、存盘读档接着算；
 *       没有攻击者的着火、中毒、凋零 100 刻内记给打过它的真玩家（或宠物的主人），岩浆、火方块这些照旧算环境，过了 100 刻也算环境；</li>
 *   <li>{@code fair_kill} 三条件与「占比填 0」、{@code kill_fatigue} 的几率公式、稳态下每小时期望掉落、同一只对同一人只记一次、
 *       同一只分给几个人时各记各的、按人分开、F 填 0、JSON 读写；</li>
 *   <li>击杀台账按在线刻滚动、玩家刻推时钟、下线不走、跟着玩家数据存盘；</li>
 *   <li>精英：属性倍数、读档不叠两层、来源是刷怪笼 / 蛋 / 指令不掷、48 格内同种只一只、放大以后放不下的不当精英、海纳尔几率与 13d 的倍数钩子、首领没有精英、
 *       灵魂刻刀不收精英、精英给 ×3 同调点、精英名字有语言键；</li>
 *   <li>技能目标过滤、元素弱点倍率（直接算 + 走一遍伤害事件）、不捡东西不开门、飞行怪；</li>
 *   <li>附近上限缓存 40 刻与预订、刷怪笼不占预订名额、缓存按服务器刻过期（和各世界的时钟无关）、{@code add_spawn_costs} 修改器、
 *       聚集体索引（跳过被压着的、找最近 ≥ 5 000、移除、挪区块后重排）。</li>
 * </ul>
 * 测基类用的「探针」是 {@link ECMonster} 的子类，借原版实体类型（尸壳、末影人、凋灵、蜜蜂）拿现成的默认属性：13c 各路的怪注册以前，这里没有真的新怪可用。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class MobFrameworkTests {

    private static final String EMPTY = "empty";
    private static final String LARGE = "empty_large";
    private static final double EPS = 1.0E-4D;

    private MobFrameworkTests() {
    }

    // ================================================================== 探针与工具

    /** 借原版实体类型的 ECMonster（不注册新类型：默认属性按借来的类型取） */
    static class Probe extends ECMonster {
        @SuppressWarnings("unchecked")
        Probe(EntityType<?> type, Level level) {
            super((EntityType<? extends ECMonster>) type, level);
        }

        boolean despawnsInPeaceful() {
            return shouldDespawnInPeaceful();
        }
    }

    static class FlyingProbe extends ECFlyingMonster {
        @SuppressWarnings("unchecked")
        FlyingProbe(EntityType<?> type, Level level) {
            super((EntityType<? extends ECFlyingMonster>) type, level);
        }
    }

    private static Probe probe(GameTestHelper helper, EntityType<?> type, double x, double z) {
        return probe(helper, type, x, 2D, z);
    }

    private static Probe probe(GameTestHelper helper, EntityType<?> type, double x, double y, double z) {
        Probe probe = new Probe(type, helper.getLevel());
        Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
        probe.moveTo(at.x, at.y, at.z, 0F, 0F);
        return probe;
    }

    private static Probe probe(GameTestHelper helper) {
        return probe(helper, EntityType.HUSK, 1.5D, 1.5D);
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        return ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
    }

    private static IPlayerData data(Player player) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            throw new IllegalStateException("测试玩家应挂着玩家数据");
        }
        return data;
    }

    /** 打一下（先清掉无敌帧，免得第二下被吞） */
    private static void hit(LivingEntity victim, DamageSource source, float amount) {
        victim.invulnerableTime = 0;
        victim.hurt(source, amount);
    }

    private static LootContext killContext(ServerLevel level, LivingEntity victim, Player killer, long seed) {
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, victim)
                .withParameter(LootContextParams.ORIGIN, victim.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, killer == null ? level.damageSources().generic() : killer.damageSources().playerAttack(killer))
                .withOptionalParameter(LootContextParams.KILLER_ENTITY, killer)
                .withOptionalParameter(LootContextParams.DIRECT_KILLER_ENTITY, killer)
                .withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer)
                .create(LootContextParamSets.ENTITY);
        return new LootContext.Builder(params).withOptionalRandomSeed(seed).create(null);
    }

    private static Gson conditionGson() {
        return Deserializers.createConditionSerializer().create();
    }

    private static void discard(Entity... entities) {
        for (Entity entity : entities) {
            if (entity != null && !entity.isRemoved()) {
                entity.discard();
            }
        }
    }

    // ================================================================== 来源

    /** 每种生成类型归到哪、公不公平；新加的三种来源存档写法是小写名，旧的写法不变 */
    @GameTest(template = EMPTY)
    public static void mobOriginFairness(GameTestHelper helper) {
        for (MobSpawnType type : MobSpawnType.values()) {
            boolean fair = SpawnOrigin.classify(type, false).isFair();
            boolean expected = type == MobSpawnType.NATURAL || type == MobSpawnType.CHUNK_GENERATION || type == MobSpawnType.PATROL
                    || type == MobSpawnType.EVENT || type == MobSpawnType.STRUCTURE;
            helper.assertTrue(fair == expected, "生成类型 " + type + " 公平应为 " + expected);
        }
        helper.assertFalse(SpawnOrigin.classify(MobSpawnType.NATURAL, true).isFair(), "带着刷怪笼对象的不公平");
        helper.assertTrue(Origin.NEST.isFair() && Origin.BOSS.isFair() && Origin.EVENT.isFair(), "巢穴、首领本体、事件公平");
        helper.assertFalse(Origin.UNKNOWN.isFair() || Origin.OTHER.isFair() || Origin.SUMMONED.isFair(), "未知、其它（指令）、召唤不公平");
        helper.assertTrue(Origin.NATURAL.id().equals("natural") && Origin.SPAWNER.id().equals("spawner") && Origin.SPAWN_EGG.id().equals("spawn_egg")
                && Origin.STRUCTURE.id().equals("structure") && Origin.SUMMONED.id().equals("summoned") && Origin.OTHER.id().equals("other"),
                "旧的存档写法不能改");
        Probe probe = probe(helper);
        for (Origin origin : new Origin[]{Origin.EVENT, Origin.NEST, Origin.BOSS}) {
            SpawnOrigin.set(probe, origin);
            helper.assertTrue(probe.getPersistentData().getString(SpawnOrigin.KEY).equals(origin.name().toLowerCase(java.util.Locale.ROOT)),
                    "存档写小写名：" + origin);
            helper.assertTrue(SpawnOrigin.of(probe) == origin && SpawnOrigin.isFair(probe), "读回来公平：" + origin);
        }
        helper.assertFalse(SpawnOrigin.isFair(probe(helper)), "没记来源的（别的模组直接 addFreshEntity、/summon 不走事件的）不公平");
        helper.succeed();
    }

    /** 真的生成：自然的公平；刷怪笼、刷怪蛋、指令不公平；增援抄了来源也不公平；转化出来的不公平 */
    @GameTest(template = EMPTY)
    public static void mobOriginRealSpawnsAndCopies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        Husk natural = EntityType.HUSK.spawn(level, pos, MobSpawnType.NATURAL);
        Husk spawner = EntityType.HUSK.spawn(level, pos, MobSpawnType.SPAWNER);
        Husk egg = EntityType.HUSK.spawn(level, pos, MobSpawnType.SPAWN_EGG);
        Husk command = EntityType.HUSK.spawn(level, pos, MobSpawnType.COMMAND);
        Husk reinforcement = EntityType.HUSK.create(level);
        try {
            helper.assertTrue(natural != null && SpawnOrigin.isFair(natural), "自然生成的公平");
            helper.assertTrue(spawner != null && !SpawnOrigin.isFair(spawner), "刷怪笼的不公平");
            helper.assertTrue(egg != null && !SpawnOrigin.isFair(egg), "刷怪蛋的不公平");
            helper.assertTrue(command != null && !SpawnOrigin.isFair(command) && SpawnOrigin.of(command) == Origin.OTHER, "指令的不公平");

            // 增援：抄了召唤者的「自然」，原始类型还是 REINFORCEMENT，不公平
            reinforcement.moveTo(pos, 0F, 0F);
            CompoundTag tag = reinforcement.getPersistentData();
            tag.putString(SpawnOrigin.KEY, Origin.NATURAL.id());
            tag.putString(SpawnOrigin.KEY_TYPE, MobSpawnType.REINFORCEMENT.name());
            helper.assertTrue(SpawnOrigin.of(reinforcement) == Origin.NATURAL && !SpawnOrigin.isArtificial(reinforcement)
                    && !SpawnOrigin.isFair(reinforcement), "增援：来源照抄（同调照旧认），公平击杀不认");

            // 转化：自然的尸壳变僵尸，来源照抄，但带上转化标记，不公平
            var converted = natural.convertTo(EntityType.ZOMBIE, true);
            helper.assertTrue(converted != null, "尸壳应能转成僵尸");
            ForgeEventFactory.onLivingConvert(natural, converted);
            helper.assertTrue(SpawnOrigin.of(converted) == Origin.NATURAL && !SpawnOrigin.isFair(converted), "转化来的：来源照抄、不公平");
            SpawnOrigin.set(converted, Origin.EVENT);
            helper.assertTrue(SpawnOrigin.isFair(converted), "代码明确重标来源时去掉转化标记");
            converted.discard();
        } finally {
            discard(natural, spawner, egg, command);
        }
        helper.succeed();
    }

    // ================================================================== 伤害占比

    /** 真玩家打的、真玩家的宠物打的算玩家份；环境、假玩家只进合计；名单按 UUID 记，宠物记在主人头上 */
    @GameTest(template = EMPTY)
    public static void damageSharePlayersPetsAndEnvironment(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "share-a");
        FakePlayer fake = ProtectionFrameworkTests.fakePlayer(helper);
        Probe mob = probe(helper);
        level.addFreshEntity(mob);
        Wolf pet = new Wolf(EntityType.WOLF, level) {
            @Override
            public LivingEntity getOwner() {
                return player;
            }
        };
        try {
            hit(mob, player.damageSources().playerAttack(player), 4F);
            float afterPlayer = MobDamageShare.playerDamage(mob);
            helper.assertTrue(afterPlayer > 0F && Math.abs(afterPlayer - MobDamageShare.totalDamage(mob)) < EPS, "玩家打的进玩家份，实际 " + afterPlayer);
            hit(mob, level.damageSources().mobAttack(pet), 3F);
            float afterPet = MobDamageShare.playerDamage(mob);
            helper.assertTrue(afterPet > afterPlayer, "宠物打的算主人的");
            helper.assertTrue(Math.abs(MobDamageShare.damageBy(mob, player.getUUID()) - afterPet) < EPS, "名单里宠物那份记在主人头上");
            helper.assertTrue(MobDamageShare.creditedPlayer(fake.damageSources().playerAttack(fake)) == null, "假玩家不算玩家");
            float total = MobDamageShare.totalDamage(mob);
            hit(mob, level.damageSources().fall(), 4F);
            hit(mob, fake.damageSources().playerAttack(fake), 2F);
            helper.assertTrue(Math.abs(MobDamageShare.playerDamage(mob) - afterPet) < EPS, "摔落、假玩家不进玩家份");
            helper.assertTrue(MobDamageShare.totalDamage(mob) > total, "摔落、假玩家进合计");
            float share = MobDamageShare.share(mob);
            helper.assertTrue(Math.abs(share - afterPet / MobDamageShare.totalDamage(mob)) < EPS && share > 0F && share < 1F, "占比 = 玩家份 / 合计");
            helper.assertTrue(Math.abs(mob.playerDamageShare() - share) < EPS && mob.playerDamage(player.getUUID()) > 0F, "基类的便捷读法同上");
            helper.assertTrue(MobDamageShare.contributors(mob).size() == 1, "名单里只有一名玩家");
        } finally {
            discard(mob, pet);
        }
        helper.succeed();
    }

    /**
     * 磨血塔：摔落把怪磨到 2 血，玩家补一刀 20，只算 2 点（按挨打前的生命封顶），占比只有一成，公平击杀不成立；
     * 存盘读档以后记录还在。
     */
    @GameTest(template = EMPTY)
    public static void damageShareCapsAtRemainingHealth(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "share-tower");
        Probe mob = probe(helper);
        SpawnOrigin.set(mob, Origin.NATURAL);
        level.addFreshEntity(mob);
        try {
            hit(mob, level.damageSources().fall(), 18F);
            helper.assertTrue(Math.abs(mob.getHealth() - 2F) < EPS, "测试前提：摔到 2 血，实际 " + mob.getHealth());
            CompoundTag saved = mob.saveWithoutId(new CompoundTag());
            hit(mob, player.damageSources().playerAttack(player), 20F);
            helper.assertTrue(Math.abs(MobDamageShare.playerDamage(mob) - 2F) < EPS, "补刀只算剩下的 2 血，实际 " + MobDamageShare.playerDamage(mob));
            helper.assertTrue(Math.abs(MobDamageShare.share(mob) - 0.1F) < 1.0E-3F, "占比 2 / 20，实际 " + MobDamageShare.share(mob));
            helper.assertFalse(FairKillCondition.test(mob, player), "磨血塔补刀不是公平击杀");

            Probe reloaded = new Probe(EntityType.HUSK, level);
            reloaded.load(saved);
            helper.assertTrue(Math.abs(MobDamageShare.totalDamage(reloaded) - 18F) < EPS && MobDamageShare.playerDamage(reloaded) == 0F,
                    "存盘读档后记录还在（跟着持久数据走）");
        } finally {
            discard(mob);
        }
        helper.succeed();
    }

    /** 被取消的一下（混合端插件、别的模组拦下）不记；玩家打出 0 伤害、又没打掉黄心的不记 */
    @GameTest(template = EMPTY)
    public static void damageShareIgnoresCanceledHits(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "share-cancel");
        Probe mob = probe(helper);
        level.addFreshEntity(mob);
        Consumer<LivingDamageEvent> cancel = event -> {
            if (event.getEntity() == mob) {
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, LivingDamageEvent.class, cancel);
        try {
            hit(mob, player.damageSources().playerAttack(player), 5F);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(cancel);
        }
        try {
            helper.assertTrue(MobDamageShare.totalDamage(mob) == 0F && MobDamageShare.contributors(mob).isEmpty(), "被取消的一下不记");
            MobDamageShare.record(mob, player.getUUID(), 0F);
            helper.assertTrue(MobDamageShare.totalDamage(mob) == 0F, "0 伤害不记");
            hit(mob, player.damageSources().playerAttack(player), 5F);
            helper.assertTrue(MobDamageShare.playerDamage(mob) > 0F, "拦截撤掉以后照常记");
            helper.assertFalse(MobDamageShare.tracked(player), "玩家自己不记占比");
        } finally {
            discard(mob);
        }
        helper.succeed();
    }

    /**
     * 没有攻击者的着火、中毒、凋零：100 刻内被真玩家打过的记在他头上（风之游灵怕火的打法：一箭点着、烧一会儿、再补一箭，
     * 照旧算公平击杀）；没被玩家打过、前面全是火烧的，只补最后一下不算；被宠物打过的记给主人；最后打它的是假玩家、别的怪的算环境；
     * 岩浆、火方块、冰冻、摔落不管谁打过都算环境。
     */
    @GameTest(template = EMPTY)
    public static void damageShareCreditsLingeringDamage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "share-burn");
        FakePlayer fake = ProtectionFrameworkTests.fakePlayer(helper);
        Probe burned = probe(helper);
        SpawnOrigin.set(burned, Origin.NATURAL);
        Probe untouched = probe(helper);
        SpawnOrigin.set(untouched, Origin.NATURAL);
        Probe petHit = probe(helper);
        Probe fakeHit = probe(helper);
        Probe mobHit = probe(helper);
        Probe environment = probe(helper);
        Wolf pet = new Wolf(EntityType.WOLF, level) {
            @Override
            public LivingEntity getOwner() {
                return player;
            }
        };
        Husk stranger = EntityType.HUSK.create(level);
        try {
            helper.assertTrue(MobDamageShare.lingering(level.damageSources().onFire()) && MobDamageShare.lingering(level.damageSources().magic())
                    && MobDamageShare.lingering(level.damageSources().wither()), "着火、中毒、凋零算持续伤害");
            helper.assertFalse(MobDamageShare.lingering(level.damageSources().lava()) || MobDamageShare.lingering(level.damageSources().inFire())
                    || MobDamageShare.lingering(level.damageSources().hotFloor()) || MobDamageShare.lingering(level.damageSources().freeze())
                    || MobDamageShare.lingering(level.damageSources().fall()), "岩浆、火方块、岩浆块、冰冻、摔落不算");

            // 一箭点着、烧 5 下、再补一箭：全记玩家
            hit(burned, player.damageSources().playerAttack(player), 3F);
            for (int i = 0; i < 5; i++) {
                hit(burned, level.damageSources().onFire(), 1.5F);
            }
            hit(burned, level.damageSources().magic(), 1F);
            hit(burned, level.damageSources().wither(), 1F);
            hit(burned, player.damageSources().playerAttack(player), 1.5F);
            helper.assertTrue(MobDamageShare.totalDamage(burned) > 8F, "测试前提：火、毒、凋零都打实了，合计 " + MobDamageShare.totalDamage(burned));
            helper.assertTrue(Math.abs(MobDamageShare.playerDamage(burned) - MobDamageShare.totalDamage(burned)) < EPS,
                    "打过以后烧掉、毒掉的都记玩家：" + MobDamageShare.playerDamage(burned) + " / " + MobDamageShare.totalDamage(burned));
            helper.assertTrue(Math.abs(MobDamageShare.damageBy(burned, player.getUUID()) - MobDamageShare.totalDamage(burned)) < EPS, "名单里也记在他头上");
            helper.assertTrue(FairKillCondition.test(burned, player), "点火再补刀：公平击杀");

            // 没被玩家打过：烧掉的算环境，只靠补最后一下拿不到
            for (int i = 0; i < 5; i++) {
                hit(untouched, level.damageSources().onFire(), 1.5F);
            }
            hit(untouched, player.damageSources().playerAttack(player), 1.5F);
            float own = MobDamageShare.playerDamage(untouched);
            helper.assertTrue(own > 0F && own < MobDamageShare.totalDamage(untouched) / 2F,
                    "没打过它时烧的算环境，实际 " + own + " / " + MobDamageShare.totalDamage(untouched));
            helper.assertFalse(FairKillCondition.test(untouched, player), "火烧到残血才补刀：不公平");

            // 宠物打过的记给主人
            hit(petHit, level.damageSources().mobAttack(pet), 2F);
            float petShare = MobDamageShare.playerDamage(petHit);
            hit(petHit, level.damageSources().onFire(), 1.5F);
            helper.assertTrue(MobDamageShare.playerDamage(petHit) > petShare && Math.abs(MobDamageShare.share(petHit) - 1F) < EPS,
                    "宠物打过以后烧的记给主人");

            // 假玩家、别的怪最后打的：算环境
            hit(fakeHit, fake.damageSources().playerAttack(fake), 2F);
            hit(fakeHit, level.damageSources().onFire(), 1.5F);
            helper.assertTrue(MobDamageShare.playerDamage(fakeHit) == 0F && MobDamageShare.totalDamage(fakeHit) > 0F, "假玩家打过的：烧的算环境");
            hit(mobHit, level.damageSources().mobAttack(stranger), 2F);
            hit(mobHit, level.damageSources().magic(), 1F);
            helper.assertTrue(MobDamageShare.playerDamage(mobHit) == 0F && MobDamageShare.totalDamage(mobHit) > 0F, "别的怪打过的：毒的算环境");

            // 岩浆、火方块、冰冻、摔落：玩家刚打过也算环境
            hit(environment, player.damageSources().playerAttack(player), 2F);
            float before = MobDamageShare.playerDamage(environment);
            hit(environment, level.damageSources().lava(), 2F);
            hit(environment, level.damageSources().inFire(), 1F);
            hit(environment, level.damageSources().freeze(), 1F);
            hit(environment, level.damageSources().fall(), 1F);
            helper.assertTrue(Math.abs(MobDamageShare.playerDamage(environment) - before) < EPS
                    && MobDamageShare.totalDamage(environment) > before + 3F, "岩浆、火方块、冰冻、摔落照旧算环境");
        } finally {
            discard(pet, stranger);
        }
        helper.succeed();
    }

    /** 着火记给玩家只在他最后一次打中以后 100 刻内（原版 killed_by_player 的窗口）：怪在世界里过了 100 刻以后再烧，算环境 */
    @GameTest(template = EMPTY, timeoutTicks = 160)
    public static void damageShareLingeringCreditExpires(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "share-expire");
        Probe mob = probe(helper);
        mob.setNoAi(true);
        level.addFreshEntity(mob);
        hit(mob, player.damageSources().playerAttack(player), 2F);
        hit(mob, level.damageSources().onFire(), 1F);
        float early = MobDamageShare.playerDamage(mob);
        helper.assertTrue(early > 0F && Math.abs(early - MobDamageShare.totalDamage(mob)) < EPS, "刚打过：烧的记玩家");
        helper.runAfterDelay(110, () -> {
            try {
                helper.assertTrue(mob.getKillCredit() == null, "测试前提：过了 100 刻，原版也不再记他");
                hit(mob, level.damageSources().onFire(), 1F);
                helper.assertTrue(Math.abs(MobDamageShare.playerDamage(mob) - early) < EPS && MobDamageShare.totalDamage(mob) > early,
                        "过了 100 刻再烧：算环境");
            } finally {
                discard(mob);
            }
            helper.succeed();
        });
    }

    // ================================================================== 公平击杀

    /** 三个条件逐一缺：没有击杀玩家 / 假玩家 / 来源刷怪笼 / 占比不够都不成立；占比门槛填 0 时只看前两条 */
    @GameTest(template = EMPTY)
    public static void fairKillThreeConditions(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "fair-a");
        FakePlayer fake = ProtectionFrameworkTests.fakePlayer(helper);
        Probe mob = probe(helper);
        SpawnOrigin.set(mob, Origin.NATURAL);
        MobDamageShare.record(mob, player.getUUID(), 10F);
        helper.assertTrue(FairKillCondition.test(mob, player), "真玩家、自然来源、占比 100%：公平");
        helper.assertTrue(FairKillCondition.INSTANCE.test(killContext(level, mob, player, 1L)), "走战利品上下文同样成立");
        helper.assertFalse(FairKillCondition.INSTANCE.test(killContext(level, mob, null, 1L)), "没有玩家杀：不成立");
        helper.assertFalse(FairKillCondition.test(mob, fake), "假玩家杀的：不成立");

        MobDamageShare.record(mob, null, 15F);
        helper.assertFalse(FairKillCondition.test(mob, player), "占比 40% < 50%：不成立");
        try {
            ECMobFramework.Config.setTestFairKillShare(0D);
            helper.assertTrue(FairKillCondition.test(mob, player), "门槛填 0：不看占比");
            Probe untouched = probe(helper);
            SpawnOrigin.set(untouched, Origin.NATURAL);
            helper.assertTrue(FairKillCondition.test(untouched, player), "门槛填 0：一下没打也成立（只看真玩家和来源）");
            SpawnOrigin.set(untouched, Origin.SPAWNER);
            helper.assertFalse(FairKillCondition.test(untouched, player), "门槛填 0：刷怪笼的照样不成立");
        } finally {
            ECMobFramework.Config.setTestFairKillShare(null);
        }

        Probe spawner = probe(helper);
        SpawnOrigin.set(spawner, Origin.SPAWNER);
        MobDamageShare.record(spawner, player.getUUID(), 10F);
        helper.assertFalse(FairKillCondition.test(spawner, player), "刷怪笼来的：不成立");
        Probe bare = probe(helper);
        MobDamageShare.record(bare, player.getUUID(), 10F);
        helper.assertFalse(FairKillCondition.test(bare, player), "没记来源的：不成立");
        helper.succeed();
    }

    /** 两个条件能从 JSON 读出来（注册上了）；kill_fatigue 的组名写错、几率越界读不进来；写回 JSON 照原样 */
    @GameTest(template = EMPTY)
    public static void lootConditionsJson(GameTestHelper helper) {
        Gson gson = conditionGson();
        LootItemCondition fair = gson.fromJson(JsonParser.parseString("{\"condition\":\"essentialcraft5:fair_kill\"}"), LootItemCondition.class);
        helper.assertTrue(fair instanceof FairKillCondition && fair.getType() == FairKillCondition.TYPE, "fair_kill 读得出来");
        LootItemCondition fatigue = gson.fromJson(
                JsonParser.parseString("{\"condition\":\"essentialcraft5:kill_fatigue\",\"group\":\"wind_storm\",\"chance\":0.25}"), LootItemCondition.class);
        helper.assertTrue(fatigue instanceof KillFatigueCondition k && k.group().equals("wind_storm") && Math.abs(k.chance() - 0.25F) < EPS,
                "kill_fatigue 读得出组和几率");
        JsonObject written = gson.toJsonTree(fatigue, LootItemCondition.class).getAsJsonObject();
        helper.assertTrue(written.get("condition").getAsString().equals("essentialcraft5:kill_fatigue") && written.get("group").getAsString().equals("wind_storm"),
                "写回 JSON 照原样：" + written);
        for (String bad : List.of("{\"condition\":\"essentialcraft5:kill_fatigue\",\"group\":\"overworld\",\"chance\":0.5}",
                "{\"condition\":\"essentialcraft5:kill_fatigue\",\"group\":\"ember\",\"chance\":1.5}")) {
            boolean threw = false;
            try {
                gson.fromJson(JsonParser.parseString(bad), LootItemCondition.class);
            } catch (JsonParseException expected) {
                threw = true;
            }
            helper.assertTrue(threw, "写错的读不进来：" + bad);
        }
        helper.assertTrue(ForgeRegistries.BIOME_MODIFIER_SERIALIZERS.get().getValue(AddSpawnCostsBiomeModifier.ID) == AddSpawnCostsBiomeModifier.CODEC,
                "add_spawn_costs 修改器类型注册上了");
        helper.succeed();
    }

    // ================================================================== 按人递减

    /** 公式：前 F 只按原几率，之后乘 F / n；F 填 0 = 不递减；默认各组 F 照 batch13.md §5.4 */
    @GameTest(template = EMPTY)
    public static void killFatigueFormulaAndDefaults(GameTestHelper helper) {
        helper.assertTrue(KillFatigueCondition.passChance(0.5F, 32, 1) == 0.5F && KillFatigueCondition.passChance(0.5F, 32, 32) == 0.5F, "前 32 只全额");
        helper.assertTrue(Math.abs(KillFatigueCondition.passChance(0.5F, 32, 64) - 0.25F) < EPS, "第 64 只减半");
        helper.assertTrue(KillFatigueCondition.passChance(0.5F, 0, 1000) == 0.5F, "F 填 0：不递减");
        Map<String, Integer> expected = Map.of("overworld_common", 32, "ember", 24, "wind_storm", 8, "hoanna_common", 30, "hoanna_titanite", 20, "elite", 6);
        helper.assertTrue(ECMobFramework.Config.DEFAULT_FREE_KILLS.equals(expected), "默认组表照 §5.4：" + ECMobFramework.Config.DEFAULT_FREE_KILLS);
        for (Map.Entry<String, Integer> entry : expected.entrySet()) {
            helper.assertTrue(ECMobFramework.Config.freeKills(entry.getKey()) == entry.getValue(), "配置默认值：" + entry.getKey());
        }
        helper.assertTrue(ECMobFramework.Config.freeKills("nope") == -1, "不认识的组返回 -1");
        helper.succeed();
    }

    /**
     * 稳态下每小时期望掉落恒等于 chance × F：每小时匀速杀 200 只、连续 4 小时，第 4 小时里各只的通过几率加起来约等于 0.5 × 32 = 16
     * （窗口是 55~60 分钟，n 在 183~200 之间，所以落在 16~17.5）；只杀 20 只的人一只都不打折。
     */
    @GameTest(template = EMPTY)
    public static void killFatigueSteadyStateCap(GameTestHelper helper) {
        ServerPlayer farmer = player(helper, "fatigue-farm");
        ServerPlayer casual = player(helper, "fatigue-casual");
        KillFatigueCondition condition = new KillFatigueCondition("overworld_common", 0.5F);
        int perHour = 200;
        long step = MobKillLedger.WINDOW_TICKS / perHour;
        double lastHour = 0D;
        for (int hour = 0; hour < 4; hour++) {
            for (int i = 0; i < perHour; i++) {
                MobKillLedger.advance(data(farmer), step);
                float p = condition.passChance(probe(helper), farmer);
                if (hour == 3) {
                    lastHour += p;
                }
            }
        }
        helper.assertTrue(lastHour >= 16D - 1.0E-3D && lastHour <= 17.5D, "稳态下每小时期望约 16，实际 " + lastHour);
        double casualSum = 0D;
        for (int i = 0; i < 20; i++) {
            casualSum += condition.passChance(probe(helper), casual);
        }
        helper.assertTrue(Math.abs(casualSum - 10D) < 1.0E-3D, "每小时 20 只的人不打折（20 × 0.5），实际 " + casualSum);
        KillFatigueCondition.forgetCounted();
        helper.succeed();
    }

    /**
     * 同一只怪同一组只记一次（一张表几个池子），不同组各记各的；下一刻（去重表清了）同一只再死会再记；
     * 按人分开：刷了 100 只的人打折，新来的人不打折；没有击杀玩家 / 假玩家几率 0；F 填 0 时不打折。
     */
    @GameTest(template = EMPTY)
    public static void killFatigueOncePerKillAndPerPlayer(GameTestHelper helper) {
        ServerPlayer a = player(helper, "fatigue-a");
        ServerPlayer b = player(helper, "fatigue-b");
        KillFatigueCondition common = new KillFatigueCondition("overworld_common", 1F);
        KillFatigueCondition elite = new KillFatigueCondition("elite", 1F);
        Probe victim = probe(helper);
        common.passChance(victim, a);
        common.passChance(victim, a);
        elite.passChance(victim, a);
        helper.assertTrue(MobKillLedger.kills(data(a), "overworld_common") == 1 && MobKillLedger.kills(data(a), "elite") == 1,
                "同一只同一组只记一次、不同组各一次");
        KillFatigueCondition.forgetCounted();
        common.passChance(victim, a);
        helper.assertTrue(MobKillLedger.kills(data(a), "overworld_common") == 2, "去重表清了以后（下一刻）再记");

        for (int i = 0; i < 98; i++) {
            common.passChance(probe(helper), a);
        }
        helper.assertTrue(MobKillLedger.kills(data(a), "overworld_common") == 100, "测试前提：A 杀了 100 只");
        helper.assertTrue(Math.abs(common.passChance(probe(helper), a) - 32F / 101F) < EPS, "A 的第 101 只按 32 / 101");
        helper.assertTrue(common.passChance(probe(helper), b) == 1F, "B 不受 A 的影响");
        try {
            ECMobFramework.Config.setTestFreeKills("overworld_common", 0);
            helper.assertTrue(common.passChance(probe(helper), a) == 1F, "F 填 0：不打折");
        } finally {
            ECMobFramework.Config.setTestFreeKills("overworld_common", null);
        }
        helper.assertTrue(common.passChance(probe(helper), null) == 0F, "没有击杀玩家：0");
        helper.assertTrue(common.passChance(probe(helper), ProtectionFrameworkTests.fakePlayer(helper)) == 0F, "假玩家：0");
        helper.assertFalse(common.test(killContext(helper.getLevel(), probe(helper), null, 7L)), "走战利品上下文，没有玩家不通过");
        KillFatigueCondition.forgetCounted();
        helper.succeed();
    }

    /**
     * 同一只怪分给几个人（首领给每名分得到掉落的人各掷一次表）：每人的台账各记一只；同一个人第二个池子不再记；
     * 去重表清了以后（下一刻）只记再判到的那个人。
     */
    @GameTest(template = EMPTY)
    public static void killFatigueCountsEachSharer(GameTestHelper helper) {
        ServerPlayer a = player(helper, "sharer-a");
        ServerPlayer b = player(helper, "sharer-b");
        KillFatigueCondition common = new KillFatigueCondition("overworld_common", 1F);
        Probe victim = probe(helper);
        try {
            common.passChance(victim, a);
            common.passChance(victim, b);
            helper.assertTrue(MobKillLedger.kills(data(a), "overworld_common") == 1 && MobKillLedger.kills(data(b), "overworld_common") == 1,
                    "同一只分给两个人：两人各记一只");
            common.passChance(victim, a);
            common.passChance(victim, b);
            helper.assertTrue(MobKillLedger.kills(data(a), "overworld_common") == 1 && MobKillLedger.kills(data(b), "overworld_common") == 1,
                    "同一个人第二个池子不再记");
            KillFatigueCondition.forgetCounted();
            common.passChance(victim, b);
            helper.assertTrue(MobKillLedger.kills(data(a), "overworld_common") == 1 && MobKillLedger.kills(data(b), "overworld_common") == 2,
                    "去重表清了以后只记再判到的那个人");
        } finally {
            KillFatigueCondition.forgetCounted();
        }
        helper.succeed();
    }

    // ================================================================== 击杀台账

    /** 按在线刻滚动：55 分钟内还在、满 60 分钟清掉；下线（时钟不走）不清；空了的条目删掉；跟着玩家数据存盘、死亡复制 */
    @GameTest(template = EMPTY)
    public static void mobLedgerRollsWithOnlineTime(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ledger-a");
        IPlayerData data = data(player);
        MobKillLedger.addKill(data, "ember");
        MobKillLedger.addType(data, EntityType.HUSK);
        MobKillLedger.advance(data, MobKillLedger.BUCKET_TICKS * 11L);
        helper.assertTrue(MobKillLedger.kills(data, "ember") == 1 && MobKillLedger.typeCount(data, EntityType.HUSK) == 1, "在线 55 分钟：还在");

        PlayerData copy = new PlayerData();
        copy.deserializeNBT(((PlayerData) data).serializeNBT());
        helper.assertTrue(MobKillLedger.kills(copy, "ember") == 1 && MobKillLedger.clock(copy) == MobKillLedger.clock(data), "存盘读回来照样在");
        CompoundTag sync = ((PlayerData) data).serializeForSync();
        helper.assertFalse(sync.contains("mob_ledger"), "台账不进同步包");

        MobKillLedger.advance(data, MobKillLedger.BUCKET_TICKS);
        helper.assertTrue(MobKillLedger.kills(data, "ember") == 0 && MobKillLedger.typeCount(data, EntityType.HUSK) == 0, "在线满 60 分钟：清掉");
        helper.assertFalse(data.getMobLedger().contains(MobKillLedger.KEY_GROUPS) || data.getMobLedger().contains(MobKillLedger.KEY_TYPES),
                "空了的条目整条删掉");

        MobKillLedger.addKill(data, "ember");
        MobKillLedger.advance(data, MobKillLedger.WINDOW_TICKS * 50L);
        helper.assertTrue(MobKillLedger.kills(data, "ember") == 0, "一下推很久：照样清掉（最多清 12 格）");
        for (String group : ECMobFramework.Config.fatigueGroups()) {
            MobKillLedger.addKill(data, group);
        }
        helper.assertTrue(data.getMobLedger().sizeInBytes() < 1500, "六组都有数时台账也不大，实际 " + data.getMobLedger().sizeInBytes() + " 字节");
        helper.succeed();
    }

    /** 玩家每 20 刻推一次时钟（只给真玩家）；假玩家不推 */
    @GameTest(template = EMPTY)
    public static void mobLedgerPlayerTickAdvances(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ledger-tick");
        player.tickCount = 40;
        MobKillLedger.onMobLedgerPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
        helper.assertTrue(MobKillLedger.clock(data(player)) == MobKillLedger.TICK_STEP, "整 20 刻推 20");
        player.tickCount = 41;
        MobKillLedger.onMobLedgerPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
        MobKillLedger.onMobLedgerPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.START, player));
        helper.assertTrue(MobKillLedger.clock(data(player)) == MobKillLedger.TICK_STEP, "不是整 20 刻、START 阶段不推");
        FakePlayer fake = ProtectionFrameworkTests.fakePlayer(helper);
        IPlayerData fakeData = PlayerDataEvents.get(fake);
        long before = fakeData == null ? 0L : MobKillLedger.clock(fakeData);
        fake.tickCount = 60;
        MobKillLedger.onMobLedgerPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, fake));
        helper.assertTrue(fakeData == null || MobKillLedger.clock(fakeData) == before, "假玩家不推");
        helper.succeed();
    }

    // ================================================================== 精英

    /** 精英属性：生命 ×2.5、攻击 ×1.4、护甲 +4、击退抗性 +0.3、经验 ×3、体型 ×1.15、名字走 .elite 键、不设自定义名；存盘读档不叠两层 */
    @GameTest(template = EMPTY)
    public static void eliteAttributesSurviveReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Probe plain = probe(helper);
        Probe elite = probe(helper);
        elite.setElite(true);
        helper.assertTrue(elite.isElite() && ECMonster.isElite(elite) && !plain.isElite(), "设成精英");
        helper.assertTrue(Math.abs(elite.getMaxHealth() - plain.getMaxHealth() * 2.5F) < 1.0E-3F, "生命 ×2.5，实际 " + elite.getMaxHealth());
        helper.assertTrue(Math.abs(elite.getHealth() - elite.getMaxHealth()) < EPS, "满血的变精英照样满血");
        helper.assertTrue(Math.abs(elite.getAttributeValue(Attributes.ATTACK_DAMAGE) - plain.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.4D) < 1.0E-3D, "攻击 ×1.4");
        helper.assertTrue(Math.abs(elite.getAttributeValue(Attributes.ARMOR) - plain.getAttributeValue(Attributes.ARMOR) - 4D) < 1.0E-3D, "护甲 +4");
        helper.assertTrue(Math.abs(elite.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) - plain.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) - 0.3D) < 1.0E-3D, "击退抗性 +0.3");
        helper.assertTrue(elite.getScale() == ECMonster.ELITE_SCALE && plain.getScale() == 1F, "体型 ×1.15");
        helper.assertTrue(Math.abs(elite.getDimensions(Pose.STANDING).width - plain.getDimensions(Pose.STANDING).width * ECMonster.ELITE_SCALE) < 1.0E-3F, "碰撞箱跟着大");
        helper.assertTrue(elite.getExperienceReward() == plain.getExperienceReward() * 3 && plain.getExperienceReward() > 0, "经验 ×3");
        Component name = elite.getName();
        helper.assertTrue(name.getContents() instanceof TranslatableContents tc && tc.getKey().equals(EntityType.HUSK.getDescriptionId() + ".elite"),
                "名字走 <实体名字键>.elite，实际 " + name);
        helper.assertFalse(elite.hasCustomName(), "不设自定义名（会让它永不消失）");

        elite.setHealth(30F);
        CompoundTag saved = elite.saveWithoutId(new CompoundTag());
        helper.assertTrue(saved.getByte(ECMonster.TAG_VARIANT) == ECMonster.VARIANT_ELITE, "存档写着 variant");
        Probe reloaded = new Probe(EntityType.HUSK, level);
        reloaded.load(saved);
        helper.assertTrue(reloaded.isElite() && Math.abs(reloaded.getMaxHealth() - elite.getMaxHealth()) < 1.0E-3F, "读档后还是精英、最大生命一样");
        helper.assertTrue(Math.abs(reloaded.getHealth() - 30F) < EPS, "读档不回血，实际 " + reloaded.getHealth());
        for (var attribute : List.of(Attributes.MAX_HEALTH, Attributes.ATTACK_DAMAGE, Attributes.ARMOR, Attributes.KNOCKBACK_RESISTANCE)) {
            AttributeInstance instance = reloaded.getAttribute(attribute);
            long count = instance == null ? 0 : instance.getModifiers().stream()
                    .filter(m -> m.getId().equals(ECMonster.ELITE_HEALTH_ID) || m.getId().equals(ECMonster.ELITE_ATTACK_ID)
                            || m.getId().equals(ECMonster.ELITE_ARMOR_ID) || m.getId().equals(ECMonster.ELITE_KNOCKBACK_ID)).count();
            helper.assertTrue(count == 1, "读档后每项精英修饰符只有一个：" + attribute.getDescriptionId() + " 有 " + count);
        }
        CompoundTag twice = reloaded.saveWithoutId(new CompoundTag());
        Probe again = new Probe(EntityType.HUSK, level);
        again.load(twice);
        helper.assertTrue(Math.abs(again.getMaxHealth() - elite.getMaxHealth()) < 1.0E-3F, "存两遍读两遍也不叠");

        // /summon 带 {variant:1b}：属性没存过，读进来时满血变满血
        CompoundTag summon = new CompoundTag();
        summon.putByte(ECMonster.TAG_VARIANT, ECMonster.VARIANT_ELITE);
        Probe summoned = new Probe(EntityType.HUSK, level);
        summoned.readAdditionalSaveData(summon);
        helper.assertTrue(summoned.isElite() && Math.abs(summoned.getHealth() - summoned.getMaxHealth()) < EPS, "只带 variant 的指令生成：精英、满血");

        elite.setElite(false);
        helper.assertTrue(!elite.isElite() && Math.abs(elite.getMaxHealth() - plain.getMaxHealth()) < EPS && elite.getHealth() <= elite.getMaxHealth(),
                "设回普通：修饰符摘掉、生命夹回上限");
        helper.succeed();
    }

    /**
     * 掷精英：几率钉成 1 时自然 / 事件 / 结构生成的出精英；来源是刷怪笼、刷怪蛋、指令、召唤的不出；
     * 48 格内已有一只同种精英时不出，远处照出；几率 0 不出；海纳尔用 0.10、13d 的倍数钩子乘上去。
     */
    @GameTest(template = EMPTY)
    public static void eliteRollRespectsOriginAndNearby(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Probe existing = null;
        try {
            ECMobFramework.Config.setTestEliteChance(1D);
            for (MobSpawnType reason : List.of(MobSpawnType.NATURAL, MobSpawnType.EVENT, MobSpawnType.STRUCTURE)) {
                Probe mob = probe(helper);
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), reason, null, null);
                helper.assertTrue(mob.isElite(), "几率 1、" + reason + "：出精英");
            }
            for (MobSpawnType reason : List.of(MobSpawnType.SPAWNER, MobSpawnType.SPAWN_EGG, MobSpawnType.DISPENSER, MobSpawnType.COMMAND,
                    MobSpawnType.MOB_SUMMONED, MobSpawnType.REINFORCEMENT)) {
                Probe mob = probe(helper);
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), reason, null, null);
                helper.assertFalse(mob.isElite(), "几率 1、" + reason + "：不出精英");
            }
            Probe fromSpawner = probe(helper);
            SpawnOrigin.set(fromSpawner, Origin.SPAWNER);
            fromSpawner.finalizeSpawn(level, level.getCurrentDifficultyAt(fromSpawner.blockPosition()), MobSpawnType.NATURAL, null, null);
            helper.assertFalse(fromSpawner.isElite(), "事件记着刷怪笼（别的模组再按自然调一次）：不出精英");

            existing = probe(helper);
            existing.setElite(true);
            level.addFreshEntity(existing);
            Probe near = probe(helper, EntityType.HUSK, 2.5D, 2.5D);
            near.finalizeSpawn(level, level.getCurrentDifficultyAt(near.blockPosition()), MobSpawnType.NATURAL, null, null);
            helper.assertFalse(near.isElite(), "48 格内已有一只同种精英：不出");
            helper.assertTrue(near.elitesNearby(level) == 1, "数到附近那一只");
            // 抬到 24 格高：70 格外是别的测试的场地，精英要放大以后放得下（eliteFits），别撞上那边摆的方块
            Probe far = probe(helper, EntityType.HUSK, 70.5D, 24D, 1.5D);
            far.finalizeSpawn(level, level.getCurrentDifficultyAt(far.blockPosition()), MobSpawnType.NATURAL, null, null);
            helper.assertTrue(far.isElite(), "70 格外：照出");
            Probe otherKind = probe(helper, EntityType.ZOMBIE, 2.5D, 2.5D);
            otherKind.finalizeSpawn(level, level.getCurrentDifficultyAt(otherKind.blockPosition()), MobSpawnType.NATURAL, null, null);
            helper.assertTrue(otherKind.isElite(), "附近的精英是别的种类：照出");

            ECMobFramework.Config.setTestEliteChance(0D);
            Probe none = probe(helper, EntityType.HUSK, 70.5D, 70.5D);
            none.finalizeSpawn(level, level.getCurrentDifficultyAt(none.blockPosition()), MobSpawnType.NATURAL, null, null);
            helper.assertFalse(none.isElite(), "几率 0：不出");
        } finally {
            ECMobFramework.Config.setTestEliteChance(null);
            discard(existing);
        }
        Probe here = probe(helper);
        helper.assertTrue(Math.abs(here.eliteChance() - ECMobFramework.Config.DEFAULT_ELITE_CHANCE) < EPS, "主世界默认 0.05");
        ECHoanna.setTestHoannaOverride(level);
        java.util.function.ToDoubleFunction<Mob> doubled = mob -> 2D;
        try {
            helper.assertTrue(Math.abs(here.eliteChance() - ECMobFramework.Config.DEFAULT_ELITE_CHANCE_HOANNA) < EPS, "海纳尔默认 0.10");
            ECMonster.addEliteChanceMultiplier(doubled);
            helper.assertTrue(Math.abs(here.eliteChance() - 0.2D) < EPS, "13d 的天灾钩子 ×2：0.20");
        } finally {
            ECMonster.removeEliteChanceMultiplier(doubled);
            ECHoanna.clearTestOverride();
        }
        helper.succeed();
    }

    /**
     * 放大以后放不下的不当精英：几率钉成 1、自然生成，两格高的通道里（流浪者 1.99 放得下，精英 ×1.15 = 2.29 头顶进天花板）不出精英，
     * 三格高的照出、当上以后碰撞箱确实放得下。用流浪者当探针（别的测试不拿它当精英，48 格内数不到别人的）。
     */
    @GameTest(template = LARGE)
    public static void eliteNeedsRoomToGrow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(2, 1, 5), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 4, 5), Blocks.STONE);
        helper.setBlock(new BlockPos(8, 1, 5), Blocks.STONE);
        helper.setBlock(new BlockPos(8, 5, 5), Blocks.STONE);
        try {
            ECMobFramework.Config.setTestEliteChance(1D);
            Probe low = probe(helper, EntityType.STRAY, 2.5D, 5.5D);
            helper.assertTrue(level.noCollision(low, low.getBoundingBox()), "测试前提：普通体型在两格高的通道里放得下");
            helper.assertFalse(low.eliteFits(level), "两格高：放大以后放不下");
            low.finalizeSpawn(level, level.getCurrentDifficultyAt(low.blockPosition()), MobSpawnType.NATURAL, null, null);
            helper.assertFalse(low.isElite(), "两格高的通道里刷出来的不当精英");

            Probe high = probe(helper, EntityType.STRAY, 8.5D, 5.5D);
            helper.assertTrue(high.eliteFits(level), "三格高：放得下");
            high.finalizeSpawn(level, level.getCurrentDifficultyAt(high.blockPosition()), MobSpawnType.NATURAL, null, null);
            helper.assertTrue(high.isElite(), "三格高的照出精英");
            helper.assertTrue(level.noCollision(high, high.getBoundingBox()), "当上精英以后的碰撞箱确实放得下");
        } finally {
            ECMobFramework.Config.setTestEliteChance(null);
        }
        helper.succeed();
    }

    /** 首领没有精英变体；灵魂刻刀收不了精英的魂（普通的照收）；精英给 ×3 同调点 */
    @GameTest(template = EMPTY)
    public static void eliteExclusionsAndAttunement(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Probe boss = probe(helper, EntityType.WITHER, 1.5D, 1.5D);
        boss.setElite(true);
        helper.assertFalse(boss.hasEliteVariant() || boss.isElite(), "#forge:bosses 里的没有精英");

        ServerPlayer player = player(helper, "elite-soul");
        ItemStack scriber = new ItemStack(ECDemonic.SOUL_SCRIBER.get());
        Probe eliteSoul = probe(helper, EntityType.ENDERMAN, 1.5D, 1.5D);
        Probe plainSoul = probe(helper, EntityType.ENDERMAN, 1.5D, 1.5D);
        eliteSoul.setElite(true);
        level.addFreshEntity(eliteSoul);
        level.addFreshEntity(plainSoul);
        try {
            eliteSoul.setHealth(2F);
            plainSoul.setHealth(2F);
            ECDemonic.SOUL_SCRIBER.get().hurtEnemy(scriber, eliteSoul, player);
            ECDemonic.SOUL_SCRIBER.get().hurtEnemy(scriber, plainSoul, player);
            helper.assertTrue(eliteSoul.isAlive() && Math.abs(eliteSoul.getHealth() - 2F) < EPS, "精英：刻刀不补收魂的那一下");
            helper.assertTrue(plainSoul.isDeadOrDying(), "普通的：照常收魂");
        } finally {
            discard(eliteSoul, plainSoul);
            AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(6D);
            level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box).forEach(Entity::discard);
        }

        ServerPlayer fighter = player(helper, "elite-att");
        data(fighter).setAttunementBudget(IPlayerData.BudgetKind.ARMOR, AttunementConfig.budgetCap(), true);
        fighter.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ECComputer.COMPUTER_HELMET.get()));
        Probe elite = probe(helper);
        elite.setElite(true);
        Probe plain = probe(helper);
        try {
            AttunementGains.setTestTime(5000L);
            AttunementGains.recordHitByPlayer(elite, fighter);
            AttunementGains.recordHitByPlayer(plain, fighter);
            AttunementGains.setTestTime(5001L);
            float fromElite = AttunementGains.armorHit(fighter, elite);
            AttunementGains.setTestTime(5002L);
            float fromPlain = AttunementGains.armorHit(fighter, plain);
            helper.assertTrue(Math.abs(fromElite - 3F) < EPS && Math.abs(fromPlain - 1F) < EPS, "精英给 3 点、普通 1 点，实际 " + fromElite + " / " + fromPlain);
        } finally {
            AttunementGains.setTestTime(-1L);
            AttunementGains.forget(fighter);
            AttunementGains.forgetTypeCounts(fighter);
        }
        helper.succeed();
    }

    /** 本模组每种有精英变体的新怪，中英文语言文件里都有 {@code <实体名字键>.elite}（各路注册新怪时漏了这条会失败） */
    @GameTest(template = EMPTY)
    public static void eliteNamesHaveLangKeys(GameTestHelper helper) {
        Optional<JsonObject> en = modJson("assets", EssentialCraft5.MODID, "lang", "en_us.json");
        Optional<JsonObject> zh = modJson("assets", EssentialCraft5.MODID, "lang", "zh_cn.json");
        if (en.isEmpty() || zh.isEmpty()) {
            helper.fail("找不到生成的语言文件，先跑 runData");
            return;
        }
        List<String> missing = new ArrayList<>();
        for (Map.Entry<ResourceKey<EntityType<?>>, EntityType<?>> entry : ForgeRegistries.ENTITY_TYPES.getEntries()) {
            if (!entry.getKey().location().getNamespace().equals(EssentialCraft5.MODID)) {
                continue;
            }
            Entity entity = entry.getValue().create(helper.getLevel());
            if (entity instanceof ECMonster monster && monster.hasEliteVariant()) {
                String key = entry.getValue().getDescriptionId() + ".elite";
                if (!en.get().has(key) || !zh.get().has(key)) {
                    missing.add(key);
                }
            }
        }
        helper.assertTrue(missing.isEmpty(), "精英名字缺语言键：" + missing);
        helper.succeed();
    }

    private static Optional<JsonObject> modJson(String... path) {
        Path file = ModList.get().getModFileById(EssentialCraft5.MODID).getFile().findResource(path);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            return Optional.of(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (IOException e) {
            throw new IllegalStateException("读不了 " + file, e);
        }
    }

    // ================================================================== 技能目标、弱点、行为

    /** 技能只打生存 / 冒险玩家和当前目标；当前目标是村民、动物、盔甲架、同类、起名的也不打；创造、旁观不打 */
    @GameTest(template = EMPTY)
    public static void skillTargetFilter(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Probe mob = probe(helper);
        ServerPlayer survival = player(helper, "skill-s");
        ServerPlayer creative = player(helper, "skill-c");
        creative.setGameMode(GameType.CREATIVE);
        ServerPlayer spectator = player(helper, "skill-v");
        spectator.setGameMode(GameType.SPECTATOR);
        ServerPlayer adventure = player(helper, "skill-a");
        adventure.setGameMode(GameType.ADVENTURE);
        helper.assertTrue(mob.isSkillTarget(survival) && mob.isSkillTarget(adventure), "生存、冒险玩家打");
        helper.assertFalse(mob.isSkillTarget(creative) || mob.isSkillTarget(spectator), "创造、旁观不打");

        IronGolem golem = EntityType.IRON_GOLEM.create(level);
        helper.assertFalse(mob.isSkillTarget(golem), "不是当前目标的铁傀儡不打");
        mob.setTarget(golem);
        helper.assertTrue(mob.isSkillTarget(golem), "当前目标是铁傀儡：打");
        golem.setCustomName(Component.literal("Bob"));
        helper.assertFalse(mob.isSkillTarget(golem), "起了名字的（isSpared）不打，哪怕是当前目标");

        Villager villager = EntityType.VILLAGER.create(level);
        mob.setTarget(villager);
        helper.assertFalse(mob.isSkillTarget(villager), "村民不打");
        ArmorStand stand = EntityType.ARMOR_STAND.create(level);
        mob.setTarget(stand);
        helper.assertFalse(mob.isSkillTarget(stand), "盔甲架不打");
        Wolf wolf = EntityType.WOLF.create(level);
        mob.setTarget(wolf);
        helper.assertFalse(mob.isSkillTarget(wolf), "动物不打");
        Probe kin = probe(helper);
        mob.setTarget(kin);
        helper.assertFalse(mob.isSkillTarget(kin), "同类不打");
        helper.assertFalse(mob.isSkillTarget(mob) || mob.isSkillTarget(null), "自己、空不打");
        helper.succeed();
    }

    /** 弱点倍率：火 / 冰 / 雷 / 近战 ×1.5，弹射物抗性 ×0.5，火球（又是火又是弹射物）连乘 0.75；没有标签的 1 */
    @GameTest(template = EMPTY)
    public static void weaknessMultipliers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "weak");
        Arrow arrow = new Arrow(level, 0D, 0D, 0D);
        SmallFireball fireball = new SmallFireball(level, 0D, 0D, 0D, 0D, 0D, 0D);
        DamageSource fire = level.damageSources().onFire();
        DamageSource freeze = level.damageSources().freeze();
        DamageSource lightning = level.damageSources().lightningBolt();
        DamageSource melee = player.damageSources().playerAttack(player);
        DamageSource shot = level.damageSources().arrow(arrow, player);
        DamageSource fireballHit = level.damageSources().fireball(fireball, player);
        helper.assertTrue(MobWeakness.multiplier(t -> t == MobWeakness.WEAK_TO_FIRE, fire) == 1.5F, "怕火：火 ×1.5");
        helper.assertTrue(MobWeakness.multiplier(t -> t == MobWeakness.WEAK_TO_FREEZING, freeze) == 1.5F, "怕冰：冰冻 ×1.5");
        helper.assertTrue(MobWeakness.multiplier(t -> t == MobWeakness.WEAK_TO_LIGHTNING, lightning) == 1.5F, "怕雷：雷 ×1.5");
        helper.assertTrue(MobWeakness.multiplier(t -> t == MobWeakness.WEAK_TO_MELEE, melee) == 1.5F, "怕近战：近战 ×1.5");
        helper.assertTrue(MobWeakness.multiplier(t -> t == MobWeakness.WEAK_TO_MELEE, shot) == 1F, "怕近战：弹射物不算近战");
        helper.assertTrue(MobWeakness.multiplier(t -> t == MobWeakness.RESISTS_PROJECTILE, shot) == 0.5F, "抗弹射物：箭 ×0.5");
        helper.assertTrue(Math.abs(MobWeakness.multiplier(t -> t == MobWeakness.WEAK_TO_FIRE || t == MobWeakness.RESISTS_PROJECTILE, fireballHit) - 0.75F) < EPS,
                "又怕火又抗弹射物：火球 1.5 × 0.5");
        helper.assertTrue(MobWeakness.multiplier(t -> false, fire) == 1F && MobWeakness.multiplier(EntityType.HUSK::is, fire) == 1F, "没有标签的 1");
        helper.succeed();
    }

    /** 弱点真的乘在 LivingHurtEvent 上（NORMAL，后面的监听器看到的是乘过的数）；同种别的个体不受影响 */
    @GameTest(template = EMPTY)
    public static void weaknessAppliesInHurtEvent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Probe weak = new Probe(EntityType.HUSK, level) {
            @Override
            public boolean hasTrait(net.minecraft.tags.TagKey<EntityType<?>> trait) {
                return trait == MobWeakness.WEAK_TO_FIRE;
            }
        };
        Vec3 at = helper.absoluteVec(new Vec3(1.5D, 2D, 1.5D));
        weak.moveTo(at.x, at.y, at.z, 0F, 0F);
        Probe normal = probe(helper);
        level.addFreshEntity(weak);
        level.addFreshEntity(normal);
        float[] seen = {-1F, -1F};
        Consumer<LivingHurtEvent> watch = event -> {
            if (event.getEntity() == weak) {
                seen[0] = event.getAmount();
            } else if (event.getEntity() == normal) {
                seen[1] = event.getAmount();
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingHurtEvent.class, watch);
        try {
            hit(weak, level.damageSources().onFire(), 2F);
            hit(normal, level.damageSources().onFire(), 2F);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(watch);
            discard(weak, normal);
        }
        helper.assertTrue(Math.abs(seen[0] - 3F) < EPS, "怕火的：2 × 1.5 = 3，实际 " + seen[0]);
        helper.assertTrue(Math.abs(seen[1] - 2F) < EPS, "不怕的照旧 2，实际 " + seen[1]);
        helper.succeed();
    }

    /** 不捡东西、不开门、不踩耕地；飞行怪用飞行寻路、不受重力、不摔伤 */
    @GameTest(template = EMPTY)
    public static void monsterDoesNotTouchWorld(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Probe mob = probe(helper);
        helper.assertFalse(mob.canPickUpLoot() || mob.wantsToPickUp(new ItemStack(net.minecraft.world.item.Items.DIAMOND)), "不捡东西");
        // 原版 GroundPathNavigation.canOpenDoors() 读的其实是 canPassDoors，要看寻路节点的设置
        helper.assertTrue(mob.getNavigation() instanceof GroundPathNavigation && !mob.getNavigation().getNodeEvaluator().canOpenDoors(), "地面寻路不开门");
        helper.assertFalse(mob.canTrample(net.minecraft.world.level.block.Blocks.FARMLAND.defaultBlockState(), mob.blockPosition(), 3F), "不踩坏耕地");
        helper.assertTrue(mob.despawnsInPeaceful(), "和平难度照原版怪物消失");
        FlyingProbe flyer = new FlyingProbe(EntityType.BEE, level);
        helper.assertTrue(flyer.getNavigation() instanceof FlyingPathNavigation && flyer.isNoGravity(), "飞行怪：飞行寻路、不受重力");
        helper.assertFalse(flyer.getNavigation().getNodeEvaluator().canOpenDoors(), "飞行怪也不开门");
        helper.assertFalse(flyer.causeFallDamage(20F, 1F, level.damageSources().fall()), "飞行怪：不摔伤");
        helper.assertFalse(flyer.canPickUpLoot(), "飞行怪同样不捡东西");
        helper.succeed();
    }

    // ================================================================== 刷怪规则

    /**
     * 附近上限按 (类型, 区块 >> 2) 缓存 40 刻：缓存期间新来的不算进去；预订会把缓存的数 +1，到上限就拒；过期以后重新数。
     * 用嗅探兽（别的测试不会刷它），只比相对变化。
     */
    @GameTest(template = EMPTY, timeoutTicks = 120)
    public static void spawnRulesNearbyCache(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        int radius = 96;
        MobSpawnRules.clearCache();
        int base = MobSpawnRules.nearbyCount(level, EntityType.SNIFFER, pos, radius);
        List<Entity> spawned = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Entity sniffer = EntityType.SNIFFER.spawn(level, pos, MobSpawnType.COMMAND);
            spawned.add(sniffer);
        }
        helper.assertTrue(MobSpawnRules.nearbyCount(level, EntityType.SNIFFER, pos, radius) == base, "缓存期间新来的不算");
        helper.assertTrue(MobSpawnRules.tryReserve(level, EntityType.SNIFFER, pos, radius, base + 1), "没到上限：预订成功");
        helper.assertFalse(MobSpawnRules.tryReserve(level, EntityType.SNIFFER, pos, radius, base + 1), "预订以后到上限：拒");
        helper.assertTrue(MobSpawnRules.nearbyCount(level, EntityType.SNIFFER, pos, radius) == base + 1, "预订把缓存的数 +1");
        helper.assertFalse(MobSpawnRules.tryReserve(level, EntityType.SNIFFER, pos, radius, 0), "上限 0：一律拒");
        helper.assertTrue(MobSpawnRules.countExact(level, EntityType.SNIFFER, pos, 8) >= 2, "精确数不走缓存");
        helper.runAfterDelay(MobSpawnRules.CACHE_TICKS + 1, () -> {
            int expired = MobSpawnRules.nearbyCount(level, EntityType.SNIFFER, pos, radius);
            MobSpawnRules.clearCache();
            int fresh = MobSpawnRules.nearbyCount(level, EntityType.SNIFFER, pos, radius);
            spawned.forEach(MobFrameworkTests::discard);
            helper.assertTrue(expired == fresh && fresh >= base + 2, "过期以后重新数：" + expired + " / " + fresh);
            helper.succeed();
        });
    }

    /**
     * 刷怪笼来问 capped 谓词：只看附近满没满、不占预订名额（反复问缓存的数也不变）；自然刷怪照旧预订；满了以后刷怪笼也拒；
     * 上限 0、原判定不过时一律拒。用僵尸疣猪兽和别的测试不用的半径，只比相对变化。
     */
    @GameTest(template = EMPTY)
    public static void spawnRulesSpawnerDoesNotReserve(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        int radius = 23;
        int base = MobSpawnRules.nearbyCount(level, EntityType.ZOGLIN, pos, radius);
        SpawnPlacements.SpawnPredicate<Zoglin> rule = MobSpawnRules.capped((type, l, reason, p, random) -> true, radius, base + 1);
        helper.assertTrue(rule.test(EntityType.ZOGLIN, level, MobSpawnType.SPAWNER, pos, level.getRandom())
                && rule.test(EntityType.ZOGLIN, level, MobSpawnType.SPAWNER, pos, level.getRandom()), "没满：刷怪笼放行");
        helper.assertTrue(MobSpawnRules.nearbyCount(level, EntityType.ZOGLIN, pos, radius) == base, "刷怪笼不预订，缓存的数不变");
        helper.assertTrue(rule.test(EntityType.ZOGLIN, level, MobSpawnType.NATURAL, pos, level.getRandom()), "自然刷怪放行");
        helper.assertTrue(MobSpawnRules.nearbyCount(level, EntityType.ZOGLIN, pos, radius) == base + 1, "自然刷怪预订一个名额");
        helper.assertFalse(rule.test(EntityType.ZOGLIN, level, MobSpawnType.SPAWNER, pos, level.getRandom()), "满了：刷怪笼也拒");
        helper.assertFalse(rule.test(EntityType.ZOGLIN, level, MobSpawnType.NATURAL, pos, level.getRandom()), "满了：自然刷怪拒");
        SpawnPlacements.SpawnPredicate<Zoglin> closed = MobSpawnRules.capped((type, l, reason, p, random) -> true, radius, 0);
        helper.assertFalse(closed.test(EntityType.ZOGLIN, level, MobSpawnType.SPAWNER, pos, level.getRandom()), "上限 0：刷怪笼也拒");
        SpawnPlacements.SpawnPredicate<Zoglin> denied = MobSpawnRules.capped((type, l, reason, p, random) -> false, radius, base + 10);
        helper.assertFalse(denied.test(EntityType.ZOGLIN, level, MobSpawnType.SPAWNER, pos, level.getRandom()), "原判定不过：刷怪笼拒");
        helper.succeed();
    }

    /**
     * 缓存按服务器刻过期，不看各世界的 gameTime：别的世界（这里用下界）建的条目过期以后照样扫掉，没过期的留着；
     * 主世界的时钟往前、往后拨一百万刻（混合端插件建的世界和主世界能差这么多）也不改变扫哪些。拨钟只在同一段同步代码里，扫完马上拨回。
     */
    @GameTest(template = EMPTY, timeoutTicks = 140)
    public static void spawnRulesCacheUsesServerTicks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ServerLevel nether = server.getLevel(Level.NETHER);
        if (nether == null) {
            throw new IllegalStateException("测试前提：下界应已加载");
        }
        BlockPos old = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos fresh = old.offset(256, 0, 0);
        int radius = 19;
        MobSpawnRules.nearbyCount(nether, EntityType.ZOGLIN, old, radius);
        helper.assertTrue(MobSpawnRules.isCached(nether, EntityType.ZOGLIN, old, radius), "下界的条目建上了");
        helper.assertFalse(MobSpawnRules.isCached(nether, EntityType.ZOGLIN, fresh, radius), "测试前提：另一格还没有条目");
        helper.runAfterDelay(MobSpawnRules.CACHE_TICKS * 2 + 2, () -> {
            MobSpawnRules.nearbyCount(nether, EntityType.ZOGLIN, fresh, radius);
            ServerLevelData clock = server.getWorldData().overworldData();
            long original = clock.getGameTime();
            boolean oldAfterAhead;
            boolean freshAfterAhead;
            try {
                clock.setGameTime(original + 1_000_000L);
                MobSpawnRules.purgeExpired(server);
                oldAfterAhead = MobSpawnRules.isCached(nether, EntityType.ZOGLIN, old, radius);
                freshAfterAhead = MobSpawnRules.isCached(nether, EntityType.ZOGLIN, fresh, radius);
                clock.setGameTime(original - 1_000_000L);
                MobSpawnRules.nearbyCount(nether, EntityType.ZOGLIN, old, radius);
                MobSpawnRules.purgeExpired(server);
            } finally {
                clock.setGameTime(original);
            }
            helper.assertTrue(freshAfterAhead, "主世界时钟拨快：刚建的条目不被扫掉");
            helper.assertFalse(oldAfterAhead, "过期满 40 刻的下界条目扫掉了");
            helper.assertTrue(MobSpawnRules.isCached(nether, EntityType.ZOGLIN, old, radius)
                    && MobSpawnRules.isCached(nether, EntityType.ZOGLIN, fresh, radius), "主世界时钟拨慢：刚建的两条都留着");
            helper.succeed();
        });
    }

    /** add_spawn_costs：从 JSON 读出来、只在 ADD 阶段对列表里的群系加花费；写法同原版群系 JSON */
    @GameTest(template = EMPTY)
    public static void addSpawnCostsModifier(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RegistryOps<com.google.gson.JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
        String json = "{\"type\":\"essentialcraft5:add_spawn_costs\",\"biomes\":\"minecraft:plains\","
                + "\"spawn_costs\":{\"minecraft:husk\":{\"energy_budget\":0.12,\"charge\":0.7}}}";
        BiomeModifier decoded = BiomeModifier.DIRECT_CODEC.parse(ops, JsonParser.parseString(json)).getOrThrow(false, helper::fail);
        helper.assertTrue(decoded instanceof AddSpawnCostsBiomeModifier, "按类型读成本模组的修改器");
        AddSpawnCostsBiomeModifier modifier = (AddSpawnCostsBiomeModifier) decoded;
        Holder<Biome> plains = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);
        Holder<Biome> desert = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.DESERT);
        helper.assertTrue(modifier.biomes().contains(plains) && !modifier.biomes().contains(desert), "群系列表");
        MobSpawnSettingsBuilder spawns = new MobSpawnSettingsBuilder(MobSpawnSettings.EMPTY);
        modifier.apply(spawns);
        MobSpawnSettings.MobSpawnCost cost = spawns.getCost(EntityType.HUSK);
        helper.assertTrue(cost != null && Math.abs(cost.charge() - 0.7D) < EPS && Math.abs(cost.energyBudget() - 0.12D) < EPS,
                "花费写进刷怪设置：" + cost);
        helper.assertTrue(spawns.getCost(EntityType.ZOMBIE) == null, "没写的实体不加");
        com.google.gson.JsonElement encoded = AddSpawnCostsBiomeModifier.CODEC.encodeStart(ops, modifier).getOrThrow(false, helper::fail);
        helper.assertTrue(encoded.getAsJsonObject().getAsJsonObject("spawn_costs").has("minecraft:husk"), "写回 JSON 照原版写法：" + encoded);
        helper.succeed();
    }

    // ================================================================== 聚集体索引

    /**
     * 聚集体测试的起点：测试结构正上方 {@value #PRESENCE_LIFT} 格的空中。同一批的别的测试只隔几格摆着，也会放聚集体，
     * 抬高以后 64 格的球查不到地面上的那些，结果只看这条测试自己放的。
     */
    private static Vec3 presenceOrigin(GameTestHelper helper) {
        return helper.absoluteVec(new Vec3(1.5D, 2D + PRESENCE_LIFT, 1.5D));
    }

    private static final int PRESENCE_LIFT = 90;

    /**
     * 聚集体索引：64 格内最近的 ≥ 5 000 MRU；跳过被稳定器压着的（不跳时照找）；MRU 门槛；被移除的查不到；只在服务端。
     */
    @GameTest(template = EMPTY)
    public static void mruPresenceIndexQueries(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 origin = presenceOrigin(helper);
        MRUPresenceEntity near = MRUPresenceEntity.create(level, origin.x + 10D, origin.y, origin.z, 6000, 1F, true, true);
        MRUPresenceEntity far = MRUPresenceEntity.create(level, origin.x + 30D, origin.y, origin.z, 9000, 1F, true, true);
        MRUPresenceEntity small = MRUPresenceEntity.create(level, origin.x + 3D, origin.y, origin.z, 1000, 1F, true, true);
        level.addFreshEntity(near);
        level.addFreshEntity(far);
        level.addFreshEntity(small);
        try {
            helper.assertTrue(MRUPresenceIndex.nearest(level, origin, 64D, 5000, true) == near, "64 格内最近的 ≥ 5 000：近的那团");
            helper.assertTrue(MRUPresenceIndex.nearest(level, origin, 64D, 0, true) == small, "不限 MRU：最近的小团");
            helper.assertTrue(MRUPresenceIndex.nearest(level, origin, 64D, 8000, true) == far, "≥ 8 000：远的那团");
            helper.assertTrue(MRUPresenceIndex.nearest(level, origin, 8D, 5000, true) == null, "8 格内没有够大的");
            near.stabilize(200);
            helper.assertTrue(MRUPresenceIndex.nearest(level, origin, 64D, 5000, true) == far, "近的被稳定器压着：跳过");
            helper.assertTrue(MRUPresenceIndex.nearest(level, origin, 64D, 5000, false) == near, "不跳过时照找");
            helper.assertTrue(MRUPresenceIndex.count(level, origin, 64D, 0, false) == 3, "三团都登记了");
            List<MRUPresenceEntity> order = MRUPresenceIndex.within(level, origin, 64D, 0, false);
            helper.assertTrue(order.size() == 3 && order.get(0) == small && order.get(2) == far, "从近到远排");
            far.discard();
            helper.assertTrue(MRUPresenceIndex.nearest(level, origin, 64D, 8000, false) == null, "移除以后查不到");
            helper.assertTrue(MRUPresenceIndex.size(level) >= 2, "这个世界还登记着两团以上");
        } finally {
            discard(near, far, small);
        }
        helper.succeed();
    }

    /** 混合端插件 / 别的模组取消了进世界：聚集体索引不登记，也查不到（生成被取消不留任何状态） */
    @GameTest(template = EMPTY)
    public static void mruPresenceIndexSkipsCanceledJoin(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 origin = presenceOrigin(helper);
        MRUPresenceEntity blocked = MRUPresenceEntity.create(level, origin.x, origin.y, origin.z, 9000, 1F, true, true);
        Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent> cancel = event -> {
            if (event.getEntity() == blocked) {
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, net.minecraftforge.event.entity.EntityJoinLevelEvent.class, cancel);
        boolean added;
        try {
            added = level.addFreshEntity(blocked);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(cancel);
        }
        helper.assertFalse(added, "测试前提：进世界被取消");
        helper.assertTrue(MRUPresenceIndex.nearest(level, origin, 4D, 0, false) == null, "被取消的不登记");
        helper.succeed();
    }

    /** 被挪到别的区块的聚集体：核位置（20 刻）以后在新地方查得到 */
    @GameTest(template = EMPTY, timeoutTicks = 80)
    public static void mruPresenceIndexFollowsMoves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 origin = presenceOrigin(helper);
        MRUPresenceEntity moved = MRUPresenceEntity.create(level, origin.x, origin.y, origin.z, 7000, 1F, true, true);
        level.addFreshEntity(moved);
        Vec3 target = origin.add(40D, 0D, 0D);
        moved.moveTo(target.x, target.y, target.z);
        helper.runAfterDelay(MRUPresenceIndex.RECHECK_INTERVAL + 2, () -> {
            MRUPresenceEntity found = MRUPresenceIndex.nearest(level, target, 4D, 5000, true);
            MRUPresenceEntity old = MRUPresenceIndex.nearest(level, origin, 4D, 5000, true);
            discard(moved);
            helper.assertTrue(found == moved, "挪过去以后在新地方查得到");
            helper.assertTrue(old == null, "老地方查不到了");
            helper.succeed();
        });
    }
}
