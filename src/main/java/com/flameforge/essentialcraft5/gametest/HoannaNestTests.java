package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.hoanna.NestCoreBlock;
import com.flameforge.essentialcraft5.blockentity.NestCoreBlockEntity;
import com.flameforge.essentialcraft5.entity.mob.ECMonster;
import com.flameforge.essentialcraft5.hoanna.nest.HoannaNestType;
import com.flameforge.essentialcraft5.hoanna.nest.HoannaNestTypes;
import com.flameforge.essentialcraft5.hoanna.nest.HoannaNests;
import com.flameforge.essentialcraft5.loot.condition.HoannaLootConditions;
import com.flameforge.essentialcraft5.registry.ECHoannaFramework;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesA;
import com.flameforge.essentialcraft5.entity.mob.MobDamageShare;
import com.flameforge.essentialcraft5.util.SpawnOrigin;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemKilledByPlayerCondition;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 第十三批 13d-HF 的巢穴核心（batch13.md §6.4、HF §4）：状态机、生成被取消、只奖励出过力的、每人附近上限、没人在场重置、
 * 领头精英和来源、和平、坏 JSON、存盘读档、读档时拦孤儿、世界生成放核心。
 * <p>
 * 巢穴的测试覆盖（候选玩家、时间、和平、奖励表、配置）都是全服静态的，每条测试单独一个批次，免得同一批里互相覆盖；
 * 每条都直接调 {@link NestCoreBlockEntity#check}（不等走刻的错开），用完在 finally 里收回怪、拆核心、清覆盖。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class HoannaNestTests {

    private static final String EMPTY_LARGE = "empty_large";
    private static final ResourceLocation TYPE_ID = EssentialCraft5.id("gametest_nest");
    private static final ResourceLocation LOOT_ID = EssentialCraft5.id("gametest/nest_reward");
    private static final long NOW = 1000L;
    private static final BlockPos CENTER = new BlockPos(5, 1, 5);

    private HoannaNestTests() {
    }

    // ================================================================== 状态机、奖励

    /**
     * ARMED → 有人走近放出一波（领头的精英、来源 NEST、拴在核心）→ 打完 → 只有出过力、又在场的人拿到奖励 → DORMANT → 回充完回 ARMED。
     * 旁边站着不打的、打过但走远了的都拿不到。
     */
    @GameTest(template = EMPTY_LARGE, batch = "hoanna_nest_cycle")
    public static void nestFullCycle(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        NestCoreBlockEntity core = null;
        try {
            setUp(helper, type(2, HoannaNestType.USE_CONFIG, HoannaNestType.USE_CONFIG));
            ECHoannaFramework.Config.setTestNestOverrides(500, null, null);
            core = core(helper, CENTER);
            ServerPlayer fighter = player(helper, "nest-fighter");
            ServerPlayer bystander = player(helper, "nest-bystander");
            ServerPlayer leaver = player(helper, "nest-leaver");
            HoannaNests.setTestPlayers(List.of(fighter, bystander, leaver));

            helper.assertTrue(core.phase() == NestCoreBlock.Phase.ARMED && core.typeId().equals(TYPE_ID), "新放的核心应 ARMED、带类型");
            helper.assertTrue(core.check(level) == NestCoreBlockEntity.Outcome.SPAWNED, "有人在 16 格内应放出一波");
            helper.assertTrue(core.phase() == NestCoreBlock.Phase.ACTIVE && HoannaNests.activeCount(level) == 1, "放出以后 ACTIVE、登记进活动表");
            List<Mob> wave = members(level, core);
            helper.assertTrue(wave.size() == 2, "一波 2 只，实际 " + wave.size());
            Mob leader = wave.get(0);
            Mob follower = wave.get(1);
            helper.assertTrue(ECMonster.isElite(leader) && !ECMonster.isElite(follower), "领头的是精英、别的不是");
            for (Mob mob : wave) {
                helper.assertTrue(SpawnOrigin.of(mob) == SpawnOrigin.Origin.NEST && HoannaLootConditions.isFromNest(mob), "来源应是 NEST");
                helper.assertTrue(mob.isPersistenceRequired(), "巢穴怪不该被自然清掉");
                helper.assertTrue(HoannaNests.linkOf(mob) != null && HoannaNests.linkOf(mob).pos().equals(core.getBlockPos()), "怪身上应记着核心");
                if (mob instanceof PathfinderMob pathfinder) {
                    helper.assertTrue(pathfinder.getRestrictCenter().equals(core.getBlockPos()) && pathfinder.getRestrictRadius() == 48F, "应拴在核心 48 格");
                }
            }
            helper.assertTrue(core.check(level) == NestCoreBlockEntity.Outcome.FIGHTING, "怪还在，应是 FIGHTING");

            // 打的人：fighter 两只都打、leaver 打了一只然后走远；bystander 不打
            MobDamageShare.record(leader, fighter.getUUID(), 5F);
            MobDamageShare.record(leader, leaver.getUUID(), 3F);
            leader.kill();
            helper.assertTrue(core.phase() == NestCoreBlock.Phase.ACTIVE && core.waveIds().size() == 1, "死了一只，还剩一只");
            leaver.moveTo(leaver.getX() + 100D, leaver.getY(), leaver.getZ());
            MobDamageShare.record(follower, fighter.getUUID(), 4F);
            follower.kill();

            helper.assertTrue(core.phase() == NestCoreBlock.Phase.DORMANT && core.dormantUntil() == NOW + 500, "清完进 DORMANT，回充 500 刻");
            helper.assertTrue(HoannaNests.activeCount(level) == 0, "清完从活动表里拿掉");
            helper.assertTrue(core.credits().isEmpty(), "结算后出力记录清空");
            helper.assertTrue(count(fighter, Items.DIAMOND) == 1, "出过力、在场的应拿到 1 份，实际 " + count(fighter, Items.DIAMOND));
            helper.assertTrue(count(bystander, Items.DIAMOND) == 0, "旁边站着不打的拿不到");
            helper.assertTrue(count(leaver, Items.DIAMOND) == 0, "打过但走远了的拿不到");

            HoannaNests.setTestTime(NOW + 499);
            helper.assertTrue(core.check(level) == NestCoreBlockEntity.Outcome.IDLE && core.phase() == NestCoreBlock.Phase.DORMANT, "没到回充时间");
            HoannaNests.setTestTime(NOW + 500);
            helper.assertTrue(core.check(level) == NestCoreBlockEntity.Outcome.RECHARGED && core.phase() == NestCoreBlock.Phase.ARMED, "回充完回 ARMED");
            helper.succeed();
        } finally {
            tearDown(level, core);
        }
    }

    /** 放怪全被拦下（加入世界的事件取消）：一只都没放出来，还是 ARMED，进 1 200 刻重试冷却；冷却过了照常放 */
    @GameTest(template = EMPTY_LARGE, batch = "hoanna_nest_cancel")
    public static void nestSpawnCancelled(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        NestCoreBlockEntity core = null;
        Consumer<EntityJoinLevelEvent> cancel = event -> {
            if (HoannaNests.linkOf(event.getEntity()) != null) {
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, EntityJoinLevelEvent.class, cancel);
        boolean listening = true;
        try {
            setUp(helper, type(2, HoannaNestType.USE_CONFIG, HoannaNestType.USE_CONFIG));
            core = core(helper, CENTER);
            HoannaNests.setTestPlayers(List.of(player(helper, "nest-cancel")));
            helper.assertTrue(core.check(level) == NestCoreBlockEntity.Outcome.SPAWN_FAILED, "全被拦下应是 SPAWN_FAILED");
            helper.assertTrue(core.phase() == NestCoreBlock.Phase.ARMED && core.waveIds().isEmpty(), "还是 ARMED、这一波是空的");
            helper.assertTrue(core.retryAt() == NOW + NestCoreBlockEntity.RETRY_TICKS, "重试冷却 1 200 刻");
            helper.assertTrue(core.check(level) == NestCoreBlockEntity.Outcome.COOLDOWN, "冷却中不再试");
            MinecraftForge.EVENT_BUS.unregister(cancel);
            listening = false;
            HoannaNests.setTestTime(NOW + NestCoreBlockEntity.RETRY_TICKS);
            helper.assertTrue(core.check(level) == NestCoreBlockEntity.Outcome.SPAWNED && core.waveIds().size() == 2, "冷却过了应照常放出");
            helper.succeed();
        } finally {
            if (listening) {
                MinecraftForge.EVENT_BUS.unregister(cancel);
            }
            tearDown(level, core);
        }
    }

    /** 每人附近上限 2：同一个人身边已有 2 个战斗中的巢穴，第三个先不放；打完一个以后第三个能放 */
    @GameTest(template = EMPTY_LARGE, batch = "hoanna_nest_cap")
    public static void nestActiveCapPerPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<NestCoreBlockEntity> cores = new ArrayList<>();
        try {
            setUp(helper, type(1, HoannaNestType.USE_CONFIG, HoannaNestType.USE_CONFIG));
            cores.add(core(helper, new BlockPos(2, 1, 2)));
            cores.add(core(helper, new BlockPos(8, 1, 2)));
            cores.add(core(helper, new BlockPos(5, 1, 8)));
            ServerPlayer player = player(helper, "nest-cap");
            HoannaNests.setTestPlayers(List.of(player));
            helper.assertTrue(ECHoannaFramework.Config.maxActivePerPlayer() == 2, "默认上限 2");
            helper.assertTrue(cores.get(0).check(level) == NestCoreBlockEntity.Outcome.SPAWNED, "第一个放");
            helper.assertTrue(cores.get(1).check(level) == NestCoreBlockEntity.Outcome.SPAWNED, "第二个放");
            helper.assertTrue(cores.get(2).check(level) == NestCoreBlockEntity.Outcome.CAPPED && cores.get(2).phase() == NestCoreBlock.Phase.ARMED,
                    "第三个应先不放");
            for (Mob mob : members(level, cores.get(0))) {
                MobDamageShare.record(mob, player.getUUID(), 2F);
                mob.kill();
            }
            helper.assertTrue(cores.get(0).phase() == NestCoreBlock.Phase.DORMANT, "第一个打完进 DORMANT");
            helper.assertTrue(cores.get(2).check(level) == NestCoreBlockEntity.Outcome.SPAWNED, "身边只剩 1 个战斗中的，第三个能放了");
            helper.succeed();
        } finally {
            tearDown(level, cores.toArray(NestCoreBlockEntity[]::new));
        }
    }

    /** 放出以后拴绳半径内一直没人：累计到重置时间（这里覆盖成 80 刻）收回剩下的怪、回 ARMED；中途有人回来计数清零 */
    @GameTest(template = EMPTY_LARGE, batch = "hoanna_nest_reset")
    public static void nestResetWhenAbandoned(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        NestCoreBlockEntity core = null;
        try {
            setUp(helper, type(2, HoannaNestType.USE_CONFIG, HoannaNestType.USE_CONFIG));
            ECHoannaFramework.Config.setTestNestOverrides(null, 80, null);
            core = core(helper, CENTER);
            ServerPlayer player = player(helper, "nest-reset");
            HoannaNests.setTestPlayers(List.of(player));
            helper.assertTrue(core.check(level) == NestCoreBlockEntity.Outcome.SPAWNED, "应放出一波");
            List<Mob> wave = members(level, core);
            HoannaNests.setTestPlayers(List.of());
            helper.assertTrue(core.check(level) == NestCoreBlockEntity.Outcome.FIGHTING && core.absentTicks() == NestCoreBlockEntity.CHECK_INTERVAL,
                    "没人在场记一次 40 刻");
            HoannaNests.setTestPlayers(List.of(player));
            helper.assertTrue(core.check(level) == NestCoreBlockEntity.Outcome.FIGHTING && core.absentTicks() == 0, "有人回来清零");
            HoannaNests.setTestPlayers(List.of());
            core.check(level);
            helper.assertTrue(core.check(level) == NestCoreBlockEntity.Outcome.RESET, "累计 80 刻应重置");
            helper.assertTrue(core.phase() == NestCoreBlock.Phase.ARMED && core.waveIds().isEmpty() && HoannaNests.activeCount(level) == 0,
                    "重置后回 ARMED、清空这一波、不在活动表里");
            helper.assertTrue(wave.stream().allMatch(Entity::isRemoved), "放出的怪应被收回");
            helper.succeed();
        } finally {
            tearDown(level, core);
        }
    }

    /**
     * 这一波还没打完就全被删掉（清怪插件、别的模组 discard）：回 ARMED、不给奖励，进 1 200 刻重试冷却，冷却里有人也不放；
     * 打死一只、剩下的被删掉也一样；冷却过了照常放
     */
    @GameTest(template = EMPTY_LARGE, batch = "hoanna_nest_vanish")
    public static void nestVanishedWaveWaitsBeforeRespawn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        NestCoreBlockEntity core = null;
        try {
            setUp(helper, type(2, HoannaNestType.USE_CONFIG, HoannaNestType.USE_CONFIG));
            core = core(helper, CENTER);
            ServerPlayer player = player(helper, "nest-vanish");
            HoannaNests.setTestPlayers(List.of(player));
            helper.assertTrue(core.check(level) == NestCoreBlockEntity.Outcome.SPAWNED, "应放出一波");
            for (Mob mob : members(level, core)) {
                mob.discard();
            }
            helper.assertTrue(core.check(level) == NestCoreBlockEntity.Outcome.VANISHED, "全被删掉应是 VANISHED");
            helper.assertTrue(core.phase() == NestCoreBlock.Phase.ARMED && core.waveIds().isEmpty() && HoannaNests.activeCount(level) == 0,
                    "回 ARMED、清空这一波、不在活动表里");
            helper.assertTrue(core.retryAt() == NOW + NestCoreBlockEntity.RETRY_TICKS, "进 1 200 刻重试冷却，实际 " + core.retryAt());
            helper.assertTrue(core.check(level) == NestCoreBlockEntity.Outcome.COOLDOWN && core.waveIds().isEmpty(), "冷却里有人在也不放");
            helper.assertTrue(count(player, Items.DIAMOND) == 0, "被删掉的不给奖励");

            HoannaNests.setTestTime(NOW + NestCoreBlockEntity.RETRY_TICKS);
            helper.assertTrue(core.check(level) == NestCoreBlockEntity.Outcome.SPAWNED && core.retryAt() == 0L, "冷却过了照常放、冷却清掉");
            List<Mob> wave = members(level, core);
            MobDamageShare.record(wave.get(0), player.getUUID(), 5F);
            wave.get(0).kill();
            wave.get(1).discard();
            helper.assertTrue(core.check(level) == NestCoreBlockEntity.Outcome.VANISHED && core.phase() == NestCoreBlock.Phase.DORMANT
                    && core.dormantUntil() == NOW + NestCoreBlockEntity.RETRY_TICKS + ECHoannaFramework.Config.rechargeTicks(),
                    "打死过一只、剩下的被删掉：照清完那样休眠，实际 " + core.phase() + " / " + core.dormantUntil());
            helper.assertTrue(count(player, Items.DIAMOND) == 0, "最后一只不是打死的，不结算");
            helper.succeed();
        } finally {
            tearDown(level, core);
        }
    }

    /**
     * 打掉领头的、留一只、走开等满重置时间：收回剩下的、不给奖励，但照清完那样休眠，回来不会马上又是一整波；
     * 「打死过」跟着存盘走；转和平收回时也一样；一只都没打死的照旧回 ARMED（见 nestResetWhenAbandoned）；指令 reset 回 ARMED
     */
    @GameTest(template = EMPTY_LARGE, batch = "hoanna_nest_partial")
    public static void nestPartialKillThenLeaveStillRests(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        NestCoreBlockEntity core = null;
        try {
            setUp(helper, type(2, HoannaNestType.USE_CONFIG, HoannaNestType.USE_CONFIG));
            ECHoannaFramework.Config.setTestNestOverrides(null, 80, null);
            core = core(helper, CENTER);
            ServerPlayer player = player(helper, "nest-partial");
            HoannaNests.setTestPlayers(List.of(player));
            helper.assertTrue(core.check(level) == NestCoreBlockEntity.Outcome.SPAWNED, "应放出一波");
            List<Mob> wave = members(level, core);
            MobDamageShare.record(wave.get(0), player.getUUID(), 5F);
            wave.get(0).kill();
            helper.assertTrue(core.killedAny() && core.saveWithoutMetadata().getBoolean("killed"), "打死过一只，跟着存盘");
            HoannaNests.setTestPlayers(List.of());
            core.check(level);
            helper.assertTrue(core.check(level) == NestCoreBlockEntity.Outcome.RESET, "累计 80 刻应重置");
            helper.assertTrue(core.phase() == NestCoreBlock.Phase.DORMANT && core.waveIds().isEmpty() && HoannaNests.activeCount(level) == 0
                    && core.dormantUntil() == NOW + ECHoannaFramework.Config.rechargeTicks() && !core.killedAny(),
                    "打死过的重置进 DORMANT，实际 " + core.phase() + " / " + core.dormantUntil());
            helper.assertTrue(wave.get(1).isRemoved(), "没死的那只应被收回");
            helper.assertTrue(count(player, Items.DIAMOND) == 0, "没清完，不给奖励");
            HoannaNests.setTestPlayers(List.of(player));
            helper.assertTrue(core.check(level) == NestCoreBlockEntity.Outcome.IDLE && core.waveIds().isEmpty(), "休眠里有人走近也不放");

            HoannaNests.setTestTime(NOW + ECHoannaFramework.Config.rechargeTicks());
            helper.assertTrue(core.check(level) == NestCoreBlockEntity.Outcome.RECHARGED, "休眠到期回 ARMED");
            helper.assertTrue(core.check(level) == NestCoreBlockEntity.Outcome.SPAWNED, "回充完照常放");
            List<Mob> second = members(level, core);
            second.get(0).kill();
            HoannaNests.setTestPeaceful(true);
            helper.assertTrue(core.check(level) == NestCoreBlockEntity.Outcome.PEACEFUL && core.phase() == NestCoreBlock.Phase.DORMANT,
                    "打死过一只再转和平：收回、休眠");
            HoannaNests.setTestPeaceful(false);
            core.resetAll(level);
            helper.assertTrue(core.phase() == NestCoreBlock.Phase.ARMED && core.dormantUntil() == 0L && !core.killedAny(), "指令 reset 回 ARMED、清掉记号");
            helper.succeed();
        } finally {
            tearDown(level, core);
        }
    }

    /** 和平：ARMED 不放；ACTIVE 时变和平收回这一波。创造模式的人不算 */
    @GameTest(template = EMPTY_LARGE, batch = "hoanna_nest_peaceful")
    public static void nestPeacefulAndCreative(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        NestCoreBlockEntity core = null;
        try {
            setUp(helper, type(2, HoannaNestType.USE_CONFIG, HoannaNestType.USE_CONFIG));
            core = core(helper, CENTER);
            ServerPlayer player = player(helper, "nest-peaceful");
            HoannaNests.setTestPlayers(List.of(player));
            HoannaNests.setTestPeaceful(true);
            helper.assertTrue(core.check(level) == NestCoreBlockEntity.Outcome.PEACEFUL && core.waveIds().isEmpty(), "和平不放");
            HoannaNests.setTestPeaceful(false);
            player.setGameMode(GameType.CREATIVE);
            helper.assertTrue(core.check(level) == NestCoreBlockEntity.Outcome.IDLE, "创造模式的人不触发");
            player.setGameMode(GameType.SURVIVAL);
            helper.assertTrue(core.check(level) == NestCoreBlockEntity.Outcome.SPAWNED, "生存模式的人触发");
            List<Mob> wave = members(level, core);
            HoannaNests.setTestPeaceful(true);
            helper.assertTrue(core.check(level) == NestCoreBlockEntity.Outcome.PEACEFUL && core.phase() == NestCoreBlock.Phase.ARMED, "变和平收回、回 ARMED");
            helper.assertTrue(wave.stream().allMatch(Entity::isRemoved), "放出的怪应被收回");
            helper.succeed();
        } finally {
            tearDown(level, core);
        }
    }

    // ================================================================== 数据

    /** 一个文件不合法只跳过那一种（带原因），别的照读；默认值、回充 / 重置写了的以 JSON 为准 */
    @GameTest(template = EMPTY_LARGE, batch = "hoanna_nest_json")
    public static void nestBadJsonSkipsOnlyThatType(GameTestHelper helper) {
        Map<ResourceLocation, JsonElement> files = new LinkedHashMap<>();
        files.put(EssentialCraft5.id("good"), json("{\"entities\":[{\"type\":\"essentialcraft5:frostbitten\",\"count\":2,\"leader\":true},"
                + "{\"type\":\"essentialcraft5:frostbitten\"}],\"loot_table\":\"essentialcraft5:hoanna/nest/good\",\"recharge_ticks\":1234}"));
        files.put(EssentialCraft5.id("unknown_entity"), json("{\"entities\":[{\"type\":\"essentialcraft5:no_such_mob\"}],\"loot_table\":\"a:b\"}"));
        files.put(EssentialCraft5.id("too_many"), json("{\"entities\":[{\"type\":\"essentialcraft5:frostbitten\",\"count\":7}],\"loot_table\":\"a:b\"}"));
        files.put(EssentialCraft5.id("radius_order"), json("{\"entities\":[{\"type\":\"essentialcraft5:frostbitten\"}],\"loot_table\":\"a:b\","
                + "\"spawn_radius\":20,\"activate_radius\":16}"));
        files.put(EssentialCraft5.id("not_object"), json("[1, 2]"));
        HoannaNestTypes.Result result = HoannaNestTypes.parse(files);
        helper.assertTrue(result.types().size() == 1 && result.types().containsKey(EssentialCraft5.id("good")), "只留下合法的那种：" + result.types().keySet());
        helper.assertTrue(result.warnings().size() == 4, "四个不合法的各一条原因：" + result.warnings());
        HoannaNestType good = result.types().get(EssentialCraft5.id("good"));
        helper.assertTrue(good.waveSize() == 3 && good.members().get(0).leader() && !good.members().get(1).leader(), "一波 3 只、第一项领头");
        helper.assertTrue(good.spawnRadius() == HoannaNestType.DEFAULT_SPAWN_RADIUS && good.activateRadius() == HoannaNestType.DEFAULT_ACTIVATE_RADIUS
                && good.leashRadius() == HoannaNestType.DEFAULT_LEASH_RADIUS, "半径没写取默认 6 / 16 / 48");
        helper.assertTrue(good.recharge(72_000) == 1234 && good.reset(6000) == 6000, "写了的以 JSON 为准、没写的照配置");
        helper.succeed();
    }

    /** 存盘再读（区块卸载再加载）：类型、这一波、出力、计时都还在 */
    @GameTest(template = EMPTY_LARGE, batch = "hoanna_nest_nbt")
    public static void nestStateSurvivesReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        NestCoreBlockEntity core = null;
        try {
            setUp(helper, type(3, HoannaNestType.USE_CONFIG, HoannaNestType.USE_CONFIG));
            core = core(helper, CENTER);
            ServerPlayer player = player(helper, "nest-nbt");
            HoannaNests.setTestPlayers(List.of(player));
            helper.assertTrue(core.check(level) == NestCoreBlockEntity.Outcome.SPAWNED, "应放出一波");
            Mob first = members(level, core).get(0);
            MobDamageShare.record(first, player.getUUID(), 6F);
            first.kill();
            HoannaNests.setTestPlayers(List.of());
            core.check(level);
            CompoundTag tag = core.saveWithoutMetadata();
            BlockState state = core.getBlockState();
            NestCoreBlockEntity loaded = new NestCoreBlockEntity(core.getBlockPos(), state);
            loaded.load(tag);
            helper.assertTrue(TYPE_ID.equals(loaded.typeId()), "类型应还在");
            helper.assertTrue(loaded.waveIds().equals(core.waveIds()) && loaded.waveIds().size() == 2, "这一波（2 只）应还在：" + loaded.waveIds());
            helper.assertTrue(loaded.credits().equals(core.credits()) && loaded.credits().get(player.getUUID()) == 6F, "出力应还在：" + loaded.credits());
            helper.assertTrue(loaded.absentTicks() == NestCoreBlockEntity.CHECK_INTERVAL, "没人在场的计数应还在");
            helper.assertTrue(loaded.phase() == NestCoreBlock.Phase.ACTIVE, "状态在方块状态上，跟着方块存");
            helper.succeed();
        } finally {
            tearDown(level, core);
        }
    }

    /** 读档进来的巢穴怪：在这一波里的放行并重新拴上；不在（被收回了）、核心被拆了的拦下；没有标签的不管 */
    @GameTest(template = EMPTY_LARGE, batch = "hoanna_nest_orphan")
    public static void nestRejectsOrphanOnLoad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        NestCoreBlockEntity core = null;
        try {
            setUp(helper, type(2, HoannaNestType.USE_CONFIG, HoannaNestType.USE_CONFIG));
            core = core(helper, CENTER);
            HoannaNests.setTestPlayers(List.of(player(helper, "nest-orphan")));
            helper.assertTrue(core.check(level) == NestCoreBlockEntity.Outcome.SPAWNED, "应放出一波");
            UUID member = core.waveIds().iterator().next();
            BlockPos pos = core.getBlockPos();

            Mob kept = fromDisk(level, pos, member);
            helper.assertFalse(joinCancelled(kept, level), "这一波里的应放行");
            helper.assertTrue(kept instanceof PathfinderMob pathfinder && pathfinder.getRestrictCenter().equals(pos), "放行时重新拴上");
            Mob orphan = fromDisk(level, pos, UUID.randomUUID());
            helper.assertTrue(joinCancelled(orphan, level), "不在这一波里的应拦下");
            Mob plain = ECHoannaNativesA.FROSTBITTEN.get().create(level);
            helper.assertFalse(joinCancelled(plain, level), "不是巢穴怪的不管");

            core.resetAll(level);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            core = null;
            helper.assertTrue(joinCancelled(fromDisk(level, pos, member), level), "核心被拆了也拦下");
            helper.succeed();
        } finally {
            tearDown(level, core);
        }
    }

    /** 世界生成放核心：placeCore 写好类型、ARMED；挖掉什么都不掉 */
    @GameTest(template = EMPTY_LARGE, batch = "hoanna_nest_place")
    public static void nestPlaceCoreNoDrops(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(CENTER);
        try {
            helper.assertTrue(HoannaNests.placeCore(level, pos, TYPE_ID), "应能放下");
            helper.assertTrue(level.getBlockEntity(pos) instanceof NestCoreBlockEntity core && TYPE_ID.equals(core.typeId())
                    && core.phase() == NestCoreBlock.Phase.ARMED, "写好类型、ARMED");
            BlockState state = level.getBlockState(pos);
            List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), null, new ItemStack(Items.NETHERITE_PICKAXE));
            helper.assertTrue(drops.isEmpty(), "挖掉什么都不掉：" + drops);
            helper.assertTrue(state.getDestroySpeed(level, pos) == 30F && state.getBlock().getExplosionResistance() == 1200F, "硬度 30、抗爆 1 200");
            for (NestCoreBlock.Phase phase : NestCoreBlock.Phase.values()) {
                int light = state.setValue(NestCoreBlock.PHASE, phase).getLightEmission(level, pos);
                helper.assertTrue(light == NestCoreBlock.lightLevel(state.setValue(NestCoreBlock.PHASE, phase)), "亮度随状态：" + phase);
            }
            helper.succeed();
        } finally {
            level.removeBlock(pos, false);
        }
    }

    // ------------------------------------------------------------------ 工具

    /** 一波 size 只（第一只领头），刷在核心 3 格内，激活 16、拴 48 */
    private static HoannaNestType type(int size, int recharge, int reset) {
        List<HoannaNestType.Member> members = new ArrayList<>();
        members.add(new HoannaNestType.Member(ECHoannaNativesA.FROSTBITTEN.get(), 1, true));
        if (size > 1) {
            members.add(new HoannaNestType.Member(ECHoannaNativesA.FROSTBITTEN.get(), size - 1, false));
        }
        return new HoannaNestType(TYPE_ID, members, 3, 16, 48, LOOT_ID, recharge, reset);
    }

    /** 测试区铺一层石头（怪站得住），登记测试类型、奖励表（钻石，要巢穴来源 + 玩家击杀）、时间 */
    private static void setUp(GameTestHelper helper, HoannaNestType type) {
        for (int x = 0; x < 11; x++) {
            for (int z = 0; z < 11; z++) {
                helper.setBlock(x, 0, z, Blocks.STONE);
                for (int y = 1; y < 5; y++) {
                    helper.setBlock(x, y, z, Blocks.AIR);
                }
            }
        }
        HoannaNestTypes.putForTest(TYPE_ID, type);
        LootTable reward = LootTable.lootTable().withPool(LootPool.lootPool().add(LootItem.lootTableItem(Items.DIAMOND))
                .when(HoannaLootConditions.fromNest()).when(LootItemKilledByPlayerCondition.killedByPlayer())).build();
        HoannaNests.setTestLootTables(Map.of(LOOT_ID, reward));
        HoannaNests.setTestTime(NOW);
        HoannaNests.setTestPeaceful(false);
    }

    private static NestCoreBlockEntity core(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        if (!HoannaNests.placeCore(helper.getLevel(), pos, TYPE_ID) || !(helper.getLevel().getBlockEntity(pos) instanceof NestCoreBlockEntity core)) {
            throw new IllegalStateException("放不下巢穴核心：" + pos);
        }
        return core;
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        Vec3 at = helper.absoluteVec(new Vec3(5.5D, 1D, 2.5D));
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        return player;
    }

    /** 这一波还活着的怪（按放出的先后） */
    private static List<Mob> members(ServerLevel level, NestCoreBlockEntity core) {
        List<Mob> out = new ArrayList<>();
        for (UUID id : core.waveIds()) {
            if (level.getEntity(id) instanceof Mob mob) {
                out.add(mob);
            }
        }
        return out;
    }

    private static int count(ServerPlayer player, Item item) {
        return player.getInventory().countItem(item);
    }

    /** 造一只「从存档读进来」的巢穴怪（不进世界）：身上记着 core，UUID 给定 */
    private static Mob fromDisk(ServerLevel level, BlockPos core, UUID id) {
        Mob mob = ECHoannaNativesA.FROSTBITTEN.get().create(level);
        mob.setUUID(id);
        mob.moveTo(core.getX() + 2.5D, core.getY(), core.getZ() + 0.5D);
        HoannaNests.tagMember(mob, level, core, 48);
        return mob;
    }

    private static boolean joinCancelled(Entity entity, ServerLevel level) {
        EntityJoinLevelEvent event = new EntityJoinLevelEvent(entity, level, true);
        HoannaNests.HoannaNestEvents.hoannaNestMemberLoad(event);
        return event.isCanceled();
    }

    private static JsonElement json(String text) {
        return JsonParser.parseString(text);
    }

    private static void tearDown(ServerLevel level, NestCoreBlockEntity... cores) {
        try {
            for (NestCoreBlockEntity core : cores) {
                if (core != null && !core.isRemoved()) {
                    core.resetAll(level);
                    level.removeBlock(core.getBlockPos(), false);
                }
            }
        } finally {
            HoannaNestTypes.clearTest();
            HoannaNests.clearTestOverrides();
            ECHoannaFramework.Config.setTestNestOverrides(null, null, null);
        }
    }
}
