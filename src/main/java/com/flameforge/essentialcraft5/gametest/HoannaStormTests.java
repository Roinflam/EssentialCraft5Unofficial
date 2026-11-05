package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.EnrichmentControllerBlockEntity;
import com.flameforge.essentialcraft5.command.ECCommands;
import com.flameforge.essentialcraft5.command.HoannaHazardsCommands;
import com.flameforge.essentialcraft5.entity.LeyStarfallEntity;
import com.flameforge.essentialcraft5.hoanna.HoannaFade;
import com.flameforge.essentialcraft5.hoanna.HoannaHazards;
import com.flameforge.essentialcraft5.hoanna.HoannaHazardsConfig;
import com.flameforge.essentialcraft5.hoanna.HoannaTide;
import com.flameforge.essentialcraft5.hoanna.HoannaVisuals;
import com.flameforge.essentialcraft5.hoanna.Radiation;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEventData;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvents;
import com.flameforge.essentialcraft5.hoanna.event.LeyStorm;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHoannaFramework;
import com.flameforge.essentialcraft5.registry.ECHoannaMaterials;
import com.flameforge.essentialcraft5.registry.ECHoannaStorm;
import com.flameforge.essentialcraft5.registry.ECVoidArmor;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 第十三批 13d-H2「环境机制」（batch13.md §6.2）的 GameTest：
 * <ul>
 *   <li>地脉风暴的掷骰：只在大潮夜黄昏掷、非大潮 / 关掉 / 已有天灾不掷、几率约 0.3、常规四种不变；黄昏序号；</li>
 *   <li>时长 6 000 刻、截到黎明（开始时截、停住时拨过黎明也结束）、指令；存档往返（含截止的黎明、旧存档补上）；</li>
 *   <li>落晶：露天真玩家才落、创造 / 假玩家不落、每人 ≤ 8、不存盘、区块没加载不落也不加载区块、下落停在地表上方一格、寿命到期消失、
 *       碰到才收（满月 2）、假玩家 / 旁观者收不了；</li>
 *   <li>露天辐射：裸身 2 / 秒、虚空全套 1 / 秒、屋顶下 0、不造成伤害；</li>
 *   <li>坍缩的潮汐倍率；光幕、天色、提示条、热点的纯数值。</li>
 * </ul>
 * 测试结构摆在超平坦主世界：要「算在海纳尔」的都在同一段同步代码里设覆盖、开风暴、测、停、清（{@link #inHoanna}）。
 * 只有测试结构自己的区块能 tick 实体，落晶的测试把落点距离临时改成结构里的几格（{@link LeyStorm#setTestStarfallDistance}）。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class HoannaStormTests {

    private static final String EMPTY = "empty";
    private static final String LARGE = "empty_large";
    private static final float EPS = 1.0E-5F;
    /** empty_large（11 × 8 × 11）的正中 */
    private static final Vec3 CENTER = new Vec3(5.5D, 1D, 5.5D);

    private HoannaStormTests() {
    }

    // ================================================================== 掷骰

    /** 纯函数：大潮、开着、几率 > 0 才可能；1 万次约 30%；常规四种的 rollable 和几率不变；黄昏序号在 12000 跨一次 */
    @GameTest(template = EMPTY)
    public static void stormRollRules(GameTestHelper helper) {
        RandomSource random = RandomSource.create(0x5EEDL * 0x9E3779B97F4A7C15L);
        helper.assertTrue(LeyStorm.rollDusk(random, 1D, true, true, 1D) == HoannaEvent.LEY_STORM, "大潮、开着、几率 1 必中");
        helper.assertTrue(LeyStorm.rollDusk(random, 1D, false, true, 1D).isNone(), "不是大潮不掷");
        helper.assertTrue(LeyStorm.rollDusk(random, 1D, true, false, 1D).isNone(), "关掉不掷");
        helper.assertTrue(LeyStorm.rollDusk(random, 0D, true, true, 1D).isNone(), "chance_multiplier 0 不掷");
        helper.assertTrue(LeyStorm.rollDusk(random, 1D, true, true, 0D).isNone(), "几率 0 不掷");
        int hits = 0;
        int n = 10_000;
        for (int i = 0; i < n; i++) {
            if (LeyStorm.rollDusk(random, 1D, true, true, HoannaHazardsConfig.DEFAULT_LEY_STORM_CHANCE) == HoannaEvent.LEY_STORM) {
                hits++;
            }
        }
        double expected = n * HoannaHazardsConfig.DEFAULT_LEY_STORM_CHANCE;
        double sigma = Math.sqrt(n * 0.3D * 0.7D);
        helper.assertTrue(Math.abs(hits - expected) <= 5D * sigma, "大潮夜几率应约 0.3：" + hits + " / " + n);

        HoannaEvent[] rollable = HoannaEvent.rollable();
        helper.assertTrue(rollable.length == 4 && List.of(rollable).equals(List.of(HoannaEvent.SUN_ARRAY, HoannaEvent.FUMES,
                HoannaEvent.DARKNESS, HoannaEvent.EARTHQUAKE)), "常规掷骰仍是四种、不含地脉风暴");
        helper.assertTrue(HoannaEvent.SUN_ARRAY.chance() == 0.3F && HoannaEvent.FUMES.chance() == 0.000175F
                && HoannaEvent.DARKNESS.chance() == 0.00005F && HoannaEvent.EARTHQUAKE.chance() == 0.0001F
                && HoannaEvent.LEY_STORM.chance() == 0F, "四种天灾的几率不变，风暴不走常规几率");
        for (int i = 0; i < 2_000; i++) {
            helper.assertFalse(HoannaEvents.rollRegular(random, 10D, e -> true) == HoannaEvent.LEY_STORM, "常规掷骰永远不出风暴");
        }
        helper.assertTrue(HoannaEvent.LEY_STORM.baseDuration() == 6_000 && HoannaEvent.LEY_STORM.duration(random) == 6_000, "风暴默认 6 000 刻");

        helper.assertTrue(LeyStorm.duskIndex(11_999L) + 1 == LeyStorm.duskIndex(12_000L) && LeyStorm.duskIndex(12_000L) == LeyStorm.duskIndex(35_999L)
                && LeyStorm.duskIndex(36_000L) == LeyStorm.duskIndex(12_000L) + 1, "黄昏序号在日内刻 12000 处加一");
        helper.assertTrue(LeyStorm.ticksToDawn(12_000L) == 12_000 && LeyStorm.ticksToDawn(23_999L) == 1 && LeyStorm.ticksToDawn(24_000L) == 24_000
                && LeyStorm.nextDawn(30_000L) == 48_000L, "离黎明的刻数");
        helper.succeed();
    }

    /** 黄昏那一次（真的状态）：大潮开始、不是大潮 / 关掉 / 潮汐关掉 / 已有别的天灾都不开始 */
    @GameTest(template = EMPTY)
    public static void stormDuskRollInHoanna(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RandomSource random = RandomSource.create(17L * 0x9E3779B97F4A7C15L);
        atTimeOfDay(helper, 12_000L, () -> inHoanna(helper, () -> {
            HoannaHazardsConfig.setTestChanceMultiplier(1D);
            HoannaHazardsConfig.setTestLeyStorm(true, 1D, null, null, null);
            HoannaTide.setTestOverride(1F);
            helper.assertTrue(HoannaEvents.duskRoll(level, random) && HoannaEvents.isActive(level, HoannaEvent.LEY_STORM),
                    "大潮夜黄昏、几率 1 应开始风暴");
            HoannaEventData data = HoannaEventData.get(level);
            helper.assertTrue(data.total() == 6_000 && data.stormDawn() == LeyStorm.nextDawn(level.getDayTime()),
                    "黄昏开的风暴 6 000 刻、记下截止的黎明，实际 " + data.total());
            HoannaEvents.stop(level);

            HoannaTide.setTestOverride(0.5F);
            helper.assertFalse(HoannaEvents.duskRoll(level, random), "潮位 0.5（不是大潮）不掷");
            HoannaTide.setTestOverride(null);
            ECHoannaFramework.Config.setTestTideEnabled(false);
            helper.assertFalse(HoannaEvents.duskRoll(level, random), "潮汐关掉：恒 0.5、没有大潮，风暴不来");
            ECHoannaFramework.Config.setTestTideEnabled(null);

            HoannaTide.setTestOverride(1F);
            HoannaHazardsConfig.setTestLeyStorm(false, 1D, null, null, null);
            helper.assertFalse(HoannaEvents.duskRoll(level, random), "ley_storm_enabled 关掉不掷");
            HoannaHazardsConfig.setTestLeyStorm(true, 1D, null, null, null);
            HoannaEvents.start(level, HoannaEvent.EARTHQUAKE, 300);
            helper.assertFalse(HoannaEvents.duskRoll(level, random), "已有天灾不掷");
            helper.assertTrue(HoannaEvents.current(level) == HoannaEvent.EARTHQUAKE, "正在的地震不该被换掉");
        }));
        helper.succeed();
    }

    // ================================================================== 时长

    /** 6 000 刻、截到黎明；没人停住时拨过黎明照样结束；离黎明近时截短；指令开的同样截 */
    @GameTest(template = EMPTY)
    public static void stormDurationUntilDawn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CommandSourceStack source = source(helper);
        atTimeOfDay(helper, 20_000L, () -> inHoanna(helper, () -> {
            HoannaHazardsConfig.setTestChanceMultiplier(0D);
            long dayStart = level.getDayTime() - 20_000L;
            helper.assertTrue(HoannaEvents.start(level, HoannaEvent.LEY_STORM, 0) && HoannaEventData.get(level).total() == 4_000,
                    "20000 刻开的风暴应截到黎明（4 000 刻），实际 " + HoannaEventData.get(level).total());
            level.setDayTime(dayStart + 22_000L);
            HoannaEvents.tick(level, false);
            HoannaEventData data = HoannaEventData.get(level);
            helper.assertTrue(data.event() == HoannaEvent.LEY_STORM && data.remaining() == 2_000,
                    "没人停住、时间走到 22000，剩余应截成离黎明的 2 000，实际 " + data.remaining());
            level.setDayTime(dayStart + 24_000L + 100L);
            HoannaEvents.tick(level, false);
            helper.assertTrue(HoannaEvents.current(level) == HoannaEvent.NONE, "过了黎明（没人时也算）应结束");

            level.setDayTime(dayStart + 12_000L);
            helper.assertTrue(HoannaEvents.start(level, HoannaEvent.LEY_STORM, 0) && HoannaEventData.get(level).total() == 6_000,
                    "黄昏开的风暴是完整的 6 000 刻");
            for (int i = 0; i < 100; i++) {
                HoannaEvents.tick(level, true);
            }
            helper.assertTrue(HoannaEventData.get(level).remaining() == 5_900, "有人时每刻 −1");
            HoannaEvents.stop(level);

            level.setDayTime(dayStart + 23_000L);
            try {
                helper.assertTrue(HoannaHazardsCommands.start(source, "ley_storm", 0) == 1, "指令能开风暴");
            } catch (CommandSyntaxException e) {
                helper.fail("指令开风暴不应报错：" + e.getMessage());
            }
            helper.assertTrue(HoannaEvents.isActive(level, HoannaEvent.LEY_STORM) && HoannaEventData.get(level).total() == 1_000,
                    "23000 刻用指令开应截成 1 000，实际 " + HoannaEventData.get(level).total());
        }));
        helper.succeed();
    }

    /** 存档往返：事件、剩余、截止的黎明都在；旧存档没有截止的黎明时第一刻补上；别的事件不写这个键 */
    @GameTest(template = EMPTY)
    public static void stormSaveRoundTrip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        atTimeOfDay(helper, 13_000L, () -> inHoanna(helper, () -> {
            HoannaHazardsConfig.setTestChanceMultiplier(0D);
            HoannaEvents.start(level, HoannaEvent.LEY_STORM, 0);
            HoannaEventData data = HoannaEventData.get(level);
            CompoundTag tag = data.save(new CompoundTag());
            HoannaEventData copy = HoannaEventData.load(tag);
            helper.assertTrue(copy.event() == HoannaEvent.LEY_STORM && copy.remaining() == data.remaining() && copy.total() == data.total()
                    && copy.stormDawn() == data.stormDawn() && data.stormDawn() != HoannaEventData.UNSET_DAY, "读档后风暴、剩余、截止的黎明都在");

            CompoundTag old = tag.copy();
            old.remove("storm_dawn");
            HoannaEventData legacy = HoannaEventData.load(old);
            helper.assertTrue(legacy.event() == HoannaEvent.LEY_STORM && legacy.stormDawn() == HoannaEventData.UNSET_DAY, "旧存档：事件在、截止的黎明没记");
            level.getDataStorage().set(HoannaEventData.NAME, legacy);
            HoannaEvents.tick(level, false);
            helper.assertTrue(HoannaEventData.get(level) == legacy && legacy.stormDawn() == LeyStorm.nextDawn(level.getDayTime()),
                    "第一刻按下一个黎明补上");

            HoannaEvents.start(level, HoannaEvent.EARTHQUAKE, 100);
            helper.assertFalse(HoannaEventData.get(level).save(new CompoundTag()).contains("storm_dawn"), "别的事件不写 storm_dawn");
            helper.assertTrue(HoannaEventData.get(level).stormDawn() == HoannaEventData.UNSET_DAY, "换成地震后截止的黎明清掉");
        }));
        helper.succeed();
    }

    // ================================================================== 落晶

    /** 露天的生存真玩家才落：落点在距离内、服务端一生成就停在地表上方一格（看上去从 12 格高落下）、不存盘；创造、假玩家、没风暴、几率 0 都不落 */
    @GameTest(template = LARGE)
    public static void starfallSpawnRules(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "ec5-starfall");
        List<LeyStarfallEntity> spawned = new ArrayList<>();
        RandomSource random = RandomSource.create(23L * 0x9E3779B97F4A7C15L);
        stormScope(helper, () -> {
            HoannaEvents.stop(level);
            helper.assertTrue(LeyStorm.trySpawnStarfall(player, random) == null, "没有风暴不落");
            HoannaEvents.start(level, HoannaEvent.LEY_STORM, 200);

            LeyStarfallEntity starfall = LeyStorm.trySpawnStarfall(player, random);
            helper.assertTrue(starfall != null, "露天、风暴中、几率 1 应落一个");
            spawned.add(starfall);
            double dx = starfall.getX() - player.getX();
            double dz = starfall.getZ() - player.getZ();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            helper.assertTrue(horizontal >= 0.5D && horizontal <= 4.5D, "落点应在（测试改的）1~4 格外，实际 " + horizontal);
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, starfall.getBlockX(), starfall.getBlockZ());
            helper.assertTrue(Math.abs(starfall.getY() - (ground + LeyStarfallEntity.HOVER)) < 1.0E-3D, "服务端一生成就停在地表上方一格");
            helper.assertTrue(Math.abs(starfall.dropHeight() - (LeyStorm.STARFALL_DROP_HEIGHT - LeyStarfallEntity.HOVER)) < 1.0E-3D && !starfall.landed(),
                    "看上去从地表上方 12 格开始往下落，还没落地");
            long now = LeyStarfallEntity.now(level);
            helper.assertTrue(starfall.landAt() == now + LeyStarfallEntity.fallTicks(starfall.dropHeight())
                    && starfall.expireAt() == now + LeyStarfallEntity.LIFETIME, "落地、过期按主世界 gameTime 记");
            helper.assertTrue(player.getUUID().equals(starfall.owner()) && LeyStorm.owned(player.getUUID()) == 1, "算在这名玩家名下");
            helper.assertFalse(starfall.shouldBeSaved() || ECHoannaStorm.LEY_STARFALL.get().canSerialize()
                    || starfall.saveAsPassenger(new CompoundTag()), "落晶不存盘");
            helper.assertFalse(starfall.isPickable() || starfall.hurt(level.damageSources().generic(), 100F), "点不中、打不坏");

            player.setGameMode(GameType.CREATIVE);
            helper.assertTrue(LeyStorm.trySpawnStarfall(player, random) == null, "创造模式不落");
            player.setGameMode(GameType.ADVENTURE);
            LeyStarfallEntity adventure = LeyStorm.trySpawnStarfall(player, random);
            helper.assertTrue(adventure != null, "冒险模式照样落");
            spawned.add(adventure);
            player.setGameMode(GameType.SURVIVAL);

            FakePlayer fake = ProtectionFrameworkTests.fakePlayer(helper);
            Vec3 at = helper.absoluteVec(CENTER);
            fake.moveTo(at.x, at.y, at.z);
            helper.assertTrue(LeyStorm.trySpawnStarfall(fake, random) == null, "假玩家不落");

            LeyStorm.setTestStarfallChance(0F);
            helper.assertTrue(LeyStorm.trySpawnStarfall(player, random) == null, "几率 0 不落");
        }, spawned);
        helper.assertTrue(LeyStorm.owned(player.getUUID()) == 0, "落晶移除后名下清零");
        helper.succeed();
    }

    /** 露天才落：屋顶下的人不落、也不吃风暴辐射（等光照更新完再测） */
    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void starfallNeedsOpenSky(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos roof = new BlockPos(5, 4, 5);
        helper.setBlock(roof, Blocks.STONE);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertFalse(level.canSeeSky(helper.absolutePos(new BlockPos(5, 3, 5))), "等屋顶的光照更新"))
                .thenExecute(() -> {
                    ServerPlayer player = player(helper, "ec5-roofed");
                    List<LeyStarfallEntity> spawned = new ArrayList<>();
                    RandomSource random = RandomSource.create(29L * 0x9E3779B97F4A7C15L);
                    stormScope(helper, () -> {
                        helper.assertFalse(HoannaHazards.exposedToSky(level, player), "屋顶下不算露天");
                        helper.assertTrue(LeyStorm.trySpawnStarfall(player, random) == null, "屋顶下不落晶");
                        Radiation.set(player, 0L);
                        helper.assertTrue(LeyStorm.exposure(player, level) == 0 && Radiation.get(player) == 0, "屋顶下不加风暴辐射");
                    }, spawned);
                })
                .thenSucceed();
    }

    /** 每人名下 ≤ 8；上限 0 不落；别人的名额分开算；移除后名额回来 */
    @GameTest(template = LARGE)
    public static void starfallPerPlayerCap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer a = player(helper, "ec5-cap-a");
        ServerPlayer b = player(helper, "ec5-cap-b");
        List<LeyStarfallEntity> spawned = new ArrayList<>();
        RandomSource random = RandomSource.create(31L * 0x9E3779B97F4A7C15L);
        stormScope(helper, () -> {
            int made = 0;
            for (int i = 0; i < 40 && made < 20; i++) {
                LeyStarfallEntity starfall = LeyStorm.trySpawnStarfall(a, random);
                if (starfall != null) {
                    spawned.add(starfall);
                    made++;
                }
            }
            helper.assertTrue(made == HoannaHazardsConfig.DEFAULT_STARFALL_PER_PLAYER && LeyStorm.owned(a.getUUID()) == 8,
                    "每人名下最多 8 个，实际落了 " + made);
            helper.assertTrue(LeyStorm.trySpawnStarfall(a, random) == null, "到上限不再落");
            LeyStarfallEntity other = LeyStorm.trySpawnStarfall(b, random);
            helper.assertTrue(other != null && LeyStorm.owned(b.getUUID()) == 1, "别人的名额分开算");
            spawned.add(other);
            spawned.get(0).discard();
            helper.assertTrue(LeyStorm.owned(a.getUUID()) == 7, "移除一个，名下减一");
            LeyStarfallEntity again = LeyStorm.trySpawnStarfall(a, random);
            helper.assertTrue(again != null, "名额回来以后又能落");
            spawned.add(again);
            HoannaHazardsConfig.setTestLeyStorm(null, null, 0, null, null);
            helper.assertTrue(LeyStorm.trySpawnStarfall(b, random) == null, "上限 0 不落");
        }, spawned);
        helper.assertTrue(LeyStorm.owned(a.getUUID()) == 0 && LeyStorm.owned(b.getUUID()) == 0, "全部移除后名下清零");
        helper.succeed();
    }

    /** 落点所在区块没加载：不落，也不把区块加载出来 */
    @GameTest(template = LARGE)
    public static void starfallSkipsUnloadedChunks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "ec5-far");
        List<LeyStarfallEntity> spawned = new ArrayList<>();
        RandomSource random = RandomSource.create(37L * 0x9E3779B97F4A7C15L);
        stormScope(helper, () -> {
            LeyStorm.setTestStarfallDistance(20_000, 20_001);
            int before = level.getChunkSource().getLoadedChunksCount();
            for (int i = 0; i < 8; i++) {
                LeyStarfallEntity starfall = LeyStorm.trySpawnStarfall(player, random);
                if (starfall != null) {
                    spawned.add(starfall);
                }
            }
            helper.assertTrue(spawned.isEmpty(), "两万格外的区块没加载，不应落");
            helper.assertTrue(level.getChunkSource().getLoadedChunksCount() <= before, "不应加载新区块");
        }, spawned);
        helper.succeed();
    }

    /** 真玩家碰到就收：平时 1 个、满月 2 个、发手册步骤（进度不存在时照样收）；假玩家、旁观者收不了；同一颗不会收两次 */
    @GameTest(template = LARGE)
    public static void starfallCollect(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Item crystal = ECHoannaMaterials.LEY_CRYSTAL.get();
        Vec3 at = helper.absoluteVec(CENTER);
        ServerPlayer player = player(helper, "ec5-collect");
        try {
            HoannaTide.setTestOverride(0.75F);
            LeyStarfallEntity first = starfall(level, at);
            FakePlayer fake = ProtectionFrameworkTests.fakePlayer(helper);
            fake.moveTo(at.x, at.y, at.z);
            first.playerTouch(fake);
            helper.assertFalse(first.isRemoved(), "假玩家碰到不收");
            ServerPlayer spectator = player(helper, "ec5-spectator");
            spectator.setGameMode(GameType.SPECTATOR);
            helper.assertFalse(LeyStorm.collect(first, spectator) || first.isRemoved(), "旁观者收不了");

            first.playerTouch(player);
            helper.assertTrue(first.isRemoved() && player.getInventory().countItem(crystal) == 1, "大潮（不是满月）收 1 个地脉结晶");
            helper.assertFalse(LeyStorm.collect(first, player), "同一颗不会收两次");
            helper.assertTrue(player.getInventory().countItem(crystal) == 1, "收第二次不多给");

            HoannaTide.setTestOverride(1F);
            LeyStarfallEntity fullMoon = starfall(level, at);
            helper.assertTrue(LeyStorm.collect(fullMoon, player) && player.getInventory().countItem(crystal) == 3, "满月收 2 个");
            helper.assertTrue(LeyStorm.crystalsFor(level) == 2, "满月 2 个");
            HoannaTide.setTestOverride(null);
            ECHoannaFramework.Config.setTestTideEnabled(false);
            helper.assertTrue(LeyStorm.crystalsFor(level) == 1, "潮汐关掉恒 1 个");
        } finally {
            HoannaTide.setTestOverride(null);
            ECHoannaFramework.Config.setTestTideEnabled(null);
        }
        helper.succeed();
    }

    /** 下落只是画法：服务端位置一直在悬停高度、不随走刻变；画面的偏移每刻少 0.05 格，到 0 时正好落地（按主世界 gameTime） */
    @GameTest(template = LARGE, timeoutTicks = 100)
    public static void starfallDescends(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 at = helper.absoluteVec(new Vec3(5.5D, 2D, 5.5D));
        LeyStarfallEntity starfall = new LeyStarfallEntity(level, at.x, at.y + 1D, at.z, at.y, null);
        helper.assertTrue(level.addFreshEntity(starfall), "落晶应能加进世界");
        helper.assertTrue(Math.abs(starfall.getY() - at.y) < 1.0E-6D && Math.abs(starfall.visualOffset(0F) - 1D) < 1.0E-6D,
                "服务端在悬停高度，画面从上方 1 格开始");
        helper.assertTrue(LeyStarfallEntity.fallTicks(1D) == 20 && LeyStarfallEntity.fallTicks(0D) == 0, "1 格要落 20 刻，0 格不落");
        helper.assertTrue(Math.abs(LeyStarfallEntity.fallOffset(11D, 100D) - 6D) < 1.0E-6D && LeyStarfallEntity.fallOffset(11D, 500D) == 0D
                && LeyStarfallEntity.fallOffset(11D, -5D) == 11D, "偏移 = 高度 − 刻数 × 0.05，夹在 0~高度");
        helper.runAfterDelay(10, () -> {
            double offset = starfall.visualOffset(0F);
            helper.assertTrue(offset > 0.3D && offset < 0.7D, "10 刻约落 0.5 格，实际 " + offset);
            helper.assertTrue(Math.abs(starfall.getY() - at.y) < 1.0E-6D && !starfall.landed(), "服务端位置不动、还没落地");
        });
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(starfall.visualOffset(0F) == 0D && starfall.landed(), "落完了、按主世界 gameTime 已落地");
            helper.assertTrue(Math.abs(starfall.getY() - at.y) < 1.0E-6D && starfall.getDeltaMovement().lengthSqr() == 0D, "不受重力、没有速度");
            starfall.discard();
            helper.succeed();
        });
    }

    /** 寿命按主世界 gameTime：到 2 400 刻、走刻时自己消失 */
    @GameTest(template = LARGE, timeoutTicks = 100)
    public static void starfallLifetime(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 at = helper.absoluteVec(CENTER);
        LeyStarfallEntity starfall = new LeyStarfallEntity(level, at.x, at.y, at.z, at.y, null);
        helper.assertTrue(level.addFreshEntity(starfall), "落晶应能加进世界");
        starfall.rewindForTest(LeyStarfallEntity.LIFETIME - 5);
        helper.runAfterDelay(2, () -> helper.assertFalse(starfall.isRemoved(), "没到寿命还在"));
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(starfall.isRemoved(), "到 2 400 刻应消失");
            helper.succeed();
        });
    }

    /**
     * 落晶停刻（区块不 tick 实体、混合端的实体激活距离）时：服务端一生成就在悬停高度，落地以前碰到不收、落地以后碰得到；
     * 过期了没来得及自己删的不占名额、碰到也不收（顺手删掉）
     */
    @GameTest(template = LARGE)
    public static void starfallWorksWithoutTicking(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Item crystal = ECHoannaMaterials.LEY_CRYSTAL.get();
        Vec3 at = helper.absoluteVec(CENTER);
        ServerPlayer player = player(helper, "ec5-stalled");
        List<LeyStarfallEntity> spawned = new ArrayList<>();
        try {
            LeyStarfallEntity falling = new LeyStarfallEntity(level, at.x, at.y + 11D, at.z, at.y, player.getUUID());
            helper.assertTrue(level.addFreshEntity(falling), "落晶应能加进世界");
            spawned.add(falling);
            helper.assertTrue(Math.abs(falling.getY() - at.y) < 1.0E-6D, "没走一刻也已经在悬停高度");
            helper.assertFalse(LeyStorm.collect(falling, player) || falling.isRemoved(), "还在空中（没落地）碰到不收");
            falling.landNowForTest();
            helper.assertTrue(LeyStorm.collect(falling, player) && player.getInventory().countItem(crystal) == LeyStorm.crystalsFor(level),
                    "落地以后不用走刻也碰得到");

            LeyStarfallEntity stale = new LeyStarfallEntity(level, at.x, at.y, at.z, at.y, player.getUUID());
            helper.assertTrue(level.addFreshEntity(stale), "落晶应能加进世界");
            spawned.add(stale);
            long now = LeyStarfallEntity.now(level);
            helper.assertTrue(LeyStorm.owned(player.getUUID(), now) == 1, "没过期的算名额");
            stale.rewindForTest(LeyStarfallEntity.LIFETIME);
            helper.assertTrue(stale.expired() && !stale.isRemoved(), "过期了但没走刻，还在世界里");
            helper.assertTrue(LeyStorm.owned(player.getUUID(), now) == 0 && LeyStorm.owned(player.getUUID()) == 0, "过期的不占名额（顺手摘掉）");
            int before = player.getInventory().countItem(crystal);
            helper.assertFalse(LeyStorm.collect(stale, player), "过期的碰到不收");
            helper.assertTrue(stale.isRemoved() && player.getInventory().countItem(crystal) == before, "过期的顺手删掉、不给东西");
        } finally {
            for (LeyStarfallEntity starfall : spawned) {
                starfall.discard();
            }
        }
        helper.assertTrue(LeyStorm.owned(player.getUUID()) == 0, "全部移除后名下清零");
        helper.succeed();
    }

    // ================================================================== 露天辐射

    /** 露天每秒：裸身 2、虚空全套（易伤 0.25）也至少吃一半 = 1、创造 0、剂量配置 0 时 0；整轮结算不扣血 */
    @GameTest(template = LARGE)
    public static void stormRadiationFloor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer bare = player(helper, "ec5-bare");
        ServerPlayer armored = player(helper, "ec5-void");
        armored.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ECVoidArmor.VOID_HELMET.get()));
        armored.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ECVoidArmor.VOID_CHESTPLATE.get()));
        armored.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ECVoidArmor.VOID_LEGGINGS.get()));
        armored.setItemSlot(EquipmentSlot.FEET, new ItemStack(ECVoidArmor.VOID_BOOTS.get()));
        stormScope(helper, () -> {
            HoannaHazardsConfig.setTestRadiationEnabled(true);
            Radiation.set(bare, 0L);
            Radiation.set(armored, 0L);
            helper.assertTrue(LeyStorm.exposure(bare, level) == 2 && Radiation.get(bare) == 2, "裸身露天每秒 +2，实际 " + Radiation.get(bare));
            helper.assertTrue(LeyStorm.exposure(armored, level) == 1 && Radiation.get(armored) == 1,
                    "虚空全套（易伤 0.25）至少吃一半：+1，实际 " + Radiation.get(armored));

            float health = bare.getHealth();
            for (int i = 0; i < 5; i++) {
                HoannaHazards.tickPlayer(bare);
            }
            helper.assertTrue(bare.getHealth() == health && bare.getRemainingFireTicks() <= 0, "风暴不造成伤害、不点火");
            // 每秒结算先照常积累 / 消退（海纳尔里每秒最多退 1），再加风暴的 2
            helper.assertTrue(Radiation.get(bare) >= 2 + 5 * (2 - Radiation.DECAY_IN_HOANNA), "每秒结算里都加了风暴的剂量，实际 " + Radiation.get(bare));

            bare.setGameMode(GameType.CREATIVE);
            helper.assertTrue(LeyStorm.exposure(bare, level) == 0, "创造模式不加");
            bare.setGameMode(GameType.SURVIVAL);
            HoannaHazardsConfig.setTestLeyStorm(null, null, null, null, 0);
            helper.assertTrue(LeyStorm.exposure(bare, level) == 0, "剂量配置 0 不加");
            HoannaHazardsConfig.setTestLeyStorm(null, null, null, null, null);
            HoannaHazardsConfig.setTestRadiationEnabled(false);
            helper.assertTrue(LeyStorm.exposure(bare, level) == 0, "辐射关掉不加");
        }, new ArrayList<>());
        helper.succeed();
    }

    // ================================================================== 坍缩与画面

    /** 坍缩倍率：海纳尔里满月 ×1.5、新月 ×1、平潮 ×1.25；天灾中取大（3，不是 4.5）；主世界不乘 */
    @GameTest(template = EMPTY)
    public static void collapseTideRate(GameTestHelper helper) {
        helper.assertTrue(Math.abs(EnrichmentControllerBlockEntity.collapseRate(true, 1F, false, 3) - 1.5F) < EPS, "海纳尔满月 ×1.5");
        helper.assertTrue(Math.abs(EnrichmentControllerBlockEntity.collapseRate(true, 0F, false, 3) - 1F) < EPS, "海纳尔新月 ×1");
        helper.assertTrue(Math.abs(EnrichmentControllerBlockEntity.collapseRate(true, HoannaTide.DISABLED_VALUE, false, 3) - 1.25F) < EPS,
                "平潮（T = 0.5）×1.25");
        helper.assertTrue(Math.abs(EnrichmentControllerBlockEntity.collapseRate(true, 1F, true, 3) - 3F) < EPS, "天灾中和潮汐取大：3（不叠成 4.5）");
        helper.assertTrue(Math.abs(EnrichmentControllerBlockEntity.collapseRate(true, 1F, true, 1) - 1.5F) < EPS, "event_multiplier 1 时满月仍 ×1.5");
        helper.assertTrue(Math.abs(EnrichmentControllerBlockEntity.collapseRate(false, 1F, false, 3) - 1F) < EPS, "不在海纳尔不吃潮汐");
        helper.succeed();
    }

    /** 世界里实际用的坍缩倍率：海纳尔里按潮位；潮汐关掉时 ×1（不按恒定的 0.5 算成 ×1.25），天灾中照样取天灾倍数；不在海纳尔 ×1 */
    @GameTest(template = EMPTY)
    public static void collapseTideOffIsFlat(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(Math.abs(EnrichmentControllerBlockEntity.collapseRateAt(level, false, 3) - 1F) < EPS, "不在海纳尔 ×1");
        inHoanna(helper, () -> {
            HoannaTide.setTestOverride(1F);
            helper.assertTrue(Math.abs(EnrichmentControllerBlockEntity.collapseRateAt(level, false, 3) - 1.5F) < EPS, "海纳尔满月 ×1.5");
            HoannaTide.setTestOverride(null);
            ECHoannaFramework.Config.setTestTideEnabled(false);
            helper.assertTrue(Math.abs(EnrichmentControllerBlockEntity.collapseRateAt(level, false, 3) - 1F) < EPS, "潮汐关掉 ×1");
            helper.assertTrue(Math.abs(EnrichmentControllerBlockEntity.collapseRateAt(level, true, 3) - 3F) < EPS, "潮汐关掉、天灾中 ×3");
        });
        helper.succeed();
    }

    /** 画面的纯数值：光幕潮汐系数与上限、风暴的天色（低饱和）、淡入淡出 200 刻、光点上限、提示条强调色、热点判定 */
    @GameTest(template = EMPTY)
    public static void stormVisualNumbers(GameTestHelper helper) {
        for (float star = 0F; star <= 0.5F; star += 0.05F) {
            for (float dark = 0F; dark <= 1F; dark += 0.25F) {
                helper.assertTrue(Math.abs(HoannaVisuals.auroraAlpha(star, 0.3F, dark, 0.5F, 0F) - HoannaVisuals.auroraAlpha(star, 0.3F, dark)) < EPS,
                        "平潮、没有风暴时光幕和原来一样");
                for (float tide = 0F; tide <= 1F; tide += 0.25F) {
                    for (float storm = 0F; storm <= 1F; storm += 0.25F) {
                        helper.assertTrue(HoannaVisuals.auroraAlpha(star, 0F, dark, tide, storm) <= HoannaVisuals.AURORA_MAX_ALPHA + EPS,
                                "光幕不透明度 ≤ 0.20");
                    }
                }
            }
        }
        helper.assertTrue(Math.abs(HoannaVisuals.auroraAlpha(0.5F, 0F, 0F, 1F, 0F) - 0.14F) < EPS, "满月光幕最高 0.14");
        helper.assertTrue(Math.abs(HoannaVisuals.auroraAlpha(0.5F, 0F, 0F, 0F, 0F) - 0.06F) < EPS, "新月光幕 0.06");
        helper.assertTrue(HoannaVisuals.auroraAlpha(0F, 0F, 0F, 0.5F, 1F) > 0.1F, "风暴从黄昏起光幕就出来（星星还没亮）");
        helper.assertTrue(Math.abs(HoannaVisuals.stormAuroraLower(1F) - HoannaVisuals.STORM_AURORA_LOWER) < EPS && HoannaVisuals.stormAuroraLower(0F) == 1F
                && HoannaVisuals.stormAuroraWiden(0F) == 1F && HoannaVisuals.STORM_AURORA_WIDEN > 1F && HoannaVisuals.STORM_AURORA_LOWER < 1F,
                "风暴中光幕压低、加宽，平时不变");

        helper.assertTrue(HoannaVisuals.chroma(HoannaVisuals.STORM_TINT) <= 26F, "风暴的天色彩度 ≤ 26，实际 " + HoannaVisuals.chroma(HoannaVisuals.STORM_TINT));
        float[] plain = new float[3];
        float[] stormed = new float[3];
        RandomSource random = RandomSource.create(41L * 0x9E3779B97F4A7C15L);
        for (int i = 0; i < 200; i++) {
            float r = random.nextFloat();
            float g = random.nextFloat();
            float b = random.nextFloat();
            HoannaVisuals.tint(r, g, b, 0.3F, 0.2F, 0.5F, 0.1F, 0.2F, plain);
            HoannaVisuals.tint(r, g, b, 0.3F, 0.2F, 0.5F, 0.1F, 0.2F, 0F, stormed);
            helper.assertTrue(plain[0] == stormed[0] && plain[1] == stormed[1] && plain[2] == stormed[2], "风暴 0 时染色和原来一样");
        }
        HoannaVisuals.tint(0.6F, 0.6F, 0.7F, 0F, 0F, 1F, 0F, 0F, 1F, stormed);
        helper.assertTrue(stormed[0] < 0.6F && stormed[2] < 0.7F && stormed[2] > stormed[1], "风暴满强度时天往暗紫偏");

        helper.assertTrue(HoannaFade.fadeIn(HoannaEvent.LEY_STORM) == 200 && HoannaFade.fadeOut(HoannaEvent.LEY_STORM) == 200, "风暴淡入淡出各 200 刻");
        float prev = 0F;
        for (double t = 0D; t <= 200D; t += 0.5D) {
            float now = HoannaFade.active(HoannaEvent.LEY_STORM, t, 0L, 6_000L, 0L);
            helper.assertTrue(Math.abs(now - prev) <= 0.02F, "相邻半刻的强度差 ≤ 0.02");
            prev = now;
        }
        helper.assertTrue(HoannaVisuals.STORM_MOTES_PER_TICK <= 2 && HoannaVisuals.STORM_MOTE_RADIUS <= HoannaVisuals.STORM_MOTE_MAX_DISTANCE
                && HoannaVisuals.STORM_MOTE_MAX_DISTANCE <= 24F && HoannaVisuals.MAX_STORM_MOTES <= 64, "光点每刻 ≤ 2、24 格内、同时 ≤ 64");
        int accent = HoannaVisuals.hudAccent(HoannaEvent.LEY_STORM);
        helper.assertTrue(accent != HoannaVisuals.hudAccent(HoannaEvent.NONE) && accent != HoannaVisuals.hudAccent(HoannaEvent.DARKNESS),
                "风暴有自己的强调色");

        for (int base = 0; base <= 5; base++) {
            helper.assertTrue(HoannaVisuals.hotspotVisible(base) == (base >= 4), "热点细尘只在底数 ≥ 4：" + base);
        }
        int hot = 0;
        for (int cx = 0; cx < 64; cx++) {
            if (HoannaVisuals.hotspotVisible(Radiation.chunkBase(cx, 7))) {
                hot++;
            }
        }
        helper.assertTrue(hot > 0 && hot < 64, "底数随区块变，有的冒尘有的不冒：" + hot + " / 64");
        helper.succeed();
    }

    // ------------------------------------------------------------------ 工具

    /** 在 empty_large 正中、生存模式的真玩家（不在世界的玩家表里） */
    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        Vec3 at = helper.absoluteVec(CENTER);
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    /** 一颗不算在谁名下、已经停住的落晶 */
    private static LeyStarfallEntity starfall(ServerLevel level, Vec3 at) {
        LeyStarfallEntity starfall = new LeyStarfallEntity(level, at.x, at.y, at.z, at.y, null);
        level.addFreshEntity(starfall);
        return starfall;
    }

    /**
     * 当海纳尔、在黄昏后开一场风暴，落晶几率 1、落点 1~4 格，跑 body；最后移除 spawned 里的落晶、停风暴、清全部覆盖（同一段同步代码）
     */
    private static void stormScope(GameTestHelper helper, Runnable body, List<LeyStarfallEntity> spawned) {
        ServerLevel level = helper.getLevel();
        atTimeOfDay(helper, 13_000L, () -> inHoanna(helper, () -> {
            HoannaHazardsConfig.setTestChanceMultiplier(0D);
            LeyStorm.setTestStarfallChance(1F);
            LeyStorm.setTestStarfallDistance(1, 4);
            try {
                HoannaEvents.start(level, HoannaEvent.LEY_STORM, 0);
                body.run();
            } finally {
                for (LeyStarfallEntity starfall : spawned) {
                    if (starfall != null) {
                        starfall.discard();
                    }
                }
                LeyStorm.setTestStarfallChance(null);
                LeyStorm.setTestStarfallDistance(-1, -1);
            }
        }));
    }

    /** 把测试所在的世界当海纳尔跑 body（同一段同步代码里设、测、停事件、清覆盖） */
    private static void inHoanna(GameTestHelper helper, Runnable body) {
        ServerLevel level = helper.getLevel();
        ECHoanna.setTestHoannaOverride(level);
        try {
            body.run();
        } finally {
            HoannaEvents.stop(level);
            ECHoanna.clearTestOverride();
            HoannaHazardsConfig.clearTestOverrides();
            HoannaTide.setTestOverride(null);
            ECHoannaFramework.Config.setTestTideEnabled(null);
        }
    }

    /** 把全局时间拨到当天的某一刻跑 body，跑完拨回（setDayTime 改的是全局主世界，只能在同一段同步代码里用） */
    private static void atTimeOfDay(GameTestHelper helper, long timeOfDay, Runnable body) {
        ServerLevel level = helper.getLevel();
        long saved = level.getDayTime();
        level.setDayTime(Math.floorDiv(saved, HoannaEvents.DAY_TICKS) * HoannaEvents.DAY_TICKS + timeOfDay);
        try {
            body.run();
        } finally {
            level.setDayTime(saved);
        }
    }

    private static CommandSourceStack source(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        return level.getServer().createCommandSourceStack().withLevel(level)
                .withPosition(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))))
                .withPermission(ECCommands.PERMISSION_LEVEL).withSuppressedOutput();
    }
}
