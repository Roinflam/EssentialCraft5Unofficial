package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.advancement.ECAdvancements;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.block.hoanna.LeyVentBlock;
import com.flameforge.essentialcraft5.block.hoanna.TideBudBlock;
import com.flameforge.essentialcraft5.block.hoanna.UmbralClusterBlock;
import com.flameforge.essentialcraft5.entity.boss.BossDaily;
import com.flameforge.essentialcraft5.hoanna.HoannaHazardsConfig;
import com.flameforge.essentialcraft5.hoanna.HoannaTide;
import com.flameforge.essentialcraft5.hoanna.Radiation;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvents;
import com.flameforge.essentialcraft5.loot.condition.HoannaLootConditions;
import com.flameforge.essentialcraft5.network.ECNetwork;
import com.flameforge.essentialcraft5.network.S2CHoannaEventPacket;
import com.flameforge.essentialcraft5.player.PlayerData;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECComputer;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHoannaFramework;
import com.flameforge.essentialcraft5.registry.ECHoannaHazards;
import com.flameforge.essentialcraft5.registry.ECHoannaMaterials;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesA;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesB;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesC;
import com.flameforge.essentialcraft5.registry.ECHoannaResources;
import com.flameforge.essentialcraft5.registry.ECHoannaSurvey;
import com.flameforge.essentialcraft5.registry.ECMobDrops;
import com.flameforge.essentialcraft5.registry.ECTags;
import com.flameforge.essentialcraft5.registry.ECVoid;
import com.flameforge.essentialcraft5.util.SpawnOrigin;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.loot.Deserializers;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSet;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.RegistryObject;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 第十三批 13d 预备（batch13.md §9.4）的 GameTest：
 * <ul>
 *   <li>玩家数据新键 {@code abyssal_salt_cd}、{@code boss_daily}：默认值、往返、不进同步包、死亡复制留着；游戏日序号 {@link BossDaily}；</li>
 *   <li>第五种天灾占位 {@code LEY_STORM}：不进常规掷骰、按编号能解析、事件包往返、H2 接上之前不算开着；协议号 12；</li>
 *   <li>魔力潮汐 {@link HoannaTide}：8 个月相的潮位、大潮天数、关掉恒 0.5、测试覆盖；</li>
 *   <li>五个战利品条件：注册了、JSON 能解析（坏的报错）、各自真 / 假；</li>
 *   <li>{@link Radiation#addFloored}：裸身 = 原数、全套宅男（易伤 0）= 一半、创造不加；</li>
 *   <li>材料、霜髓（只有精准采集掉本体）、特产标签、方块空壳的状态属性；九个实体空壳；{@link ECAdvancements#awardIfPresent}。</li>
 * </ul>
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class Batch13dPrepTests {

    private static final String EMPTY = "empty";
    private static final float EPS = 1.0E-5F;
    /** 只要原点、THIS_ENTITY 可有可无（原版的 ALL_PARAMS 把每个参数都当必填） */
    private static final LootContextParamSet ORIGIN_ONLY = LootContextParamSet.builder()
            .required(LootContextParams.ORIGIN).optional(LootContextParams.THIS_ENTITY).build();

    private Batch13dPrepTests() {
    }

    // ================================================================== 玩家数据

    /** 两个新键：默认值、空的不写、往返、不进同步包、死亡复制留着 */
    @GameTest(template = EMPTY)
    public static void hoannaPlayerDataKeys(GameTestHelper helper) {
        PlayerData fresh = new PlayerData();
        helper.assertTrue(fresh.getAbyssalSaltCooldownEnd() == 0L && fresh.getBossDaily().isEmpty(), "新建的玩家数据：渊盐冷却 0、首领日表空");
        CompoundTag freshTag = fresh.serializeNBT();
        helper.assertFalse(freshTag.contains("abyssal_salt_cd") || freshTag.contains("boss_daily"), "默认值不写进存档");
        helper.assertTrue(freshTag.getInt("data_version") == PlayerData.DATA_VERSION && PlayerData.DATA_VERSION == 1, "13d 不升 DATA_VERSION");

        CompoundTag old = new CompoundTag();
        old.putInt("matrix_damage", 700);
        old.putInt("data_version", 1);
        PlayerData loaded = new PlayerData();
        loaded.deserializeNBT(old);
        helper.assertTrue(loaded.getAbyssalSaltCooldownEnd() == 0L && loaded.getBossDaily().isEmpty() && loaded.getMatrixDamage() == 700,
                "没有这两个键的老存档读成默认值，别的照旧");

        PlayerData data = new PlayerData();
        data.setAbyssalSaltCooldownEnd(123_456_789L);
        CompoundTag daily = new CompoundTag();
        daily.putLong("essentialcraft5:abyssal_tidelord", 42L);
        data.setBossDaily(daily);
        data.getBossDaily().putLong("minecraft:wither", 7L);
        helper.assertTrue(data.getBossDaily().getLong("minecraft:wither") == 7L, "getBossDaily 给的是活的那份，原地改就记上了");

        PlayerData back = new PlayerData();
        back.deserializeNBT(data.serializeNBT());
        helper.assertTrue(back.getAbyssalSaltCooldownEnd() == 123_456_789L, "渊盐冷却往返不变，实际 " + back.getAbyssalSaltCooldownEnd());
        helper.assertTrue(back.getBossDaily().equals(data.getBossDaily()), "首领日表往返不变：" + back.getBossDaily());

        CompoundTag sync = data.serializeForSync();
        helper.assertFalse(sync.contains("abyssal_salt_cd") || sync.contains("boss_daily"), "两个键都只在服务端，不进同步包");

        data.setBossDaily(null);
        helper.assertTrue(data.getBossDaily().isEmpty(), "setBossDaily(null) 当空表");

        Player original = helper.makeMockPlayer();
        IPlayerData from = data(original);
        from.setAbyssalSaltCooldownEnd(9_000L);
        from.setBossDaily(daily.copy());
        Player reborn = helper.makeMockPlayer();
        PlayerDataEvents.clone(new PlayerEvent.Clone(reborn, original, true));
        IPlayerData to = data(reborn);
        helper.assertTrue(to.getAbyssalSaltCooldownEnd() == 9_000L && to.getBossDaily().getLong("essentialcraft5:abyssal_tidelord") == 42L,
                "死亡复制：渊盐冷却和首领日表都留着");
        helper.succeed();
    }

    /** 游戏日序号：主世界日内时间 / 24000（纯函数版和服务器版一致） */
    @GameTest(template = EMPTY)
    public static void bossDailyIndex(GameTestHelper helper) {
        helper.assertTrue(BossDaily.dayIndex(0L) == 0L && BossDaily.dayIndex(23_999L) == 0L && BossDaily.dayIndex(24_000L) == 1L
                && BossDaily.dayIndex(240_001L) == 10L, "dayIndex 按 24 000 刻一天整除");
        ServerLevel overworld = helper.getLevel().getServer().overworld();
        helper.assertTrue(BossDaily.dayIndex(helper.getLevel().getServer()) == overworld.getDayTime() / BossDaily.DAY_TICKS,
                "服务器版取主世界的日内时间");
        helper.succeed();
    }

    // ================================================================== 地脉风暴占位

    /** LEY_STORM：不进常规掷骰、编号 5 能解析、事件包往返、H2 接上之前不算开着；协议号至少 12 */
    @GameTest(template = EMPTY)
    public static void leyStormPlaceholder(GameTestHelper helper) {
        helper.assertTrue(Integer.parseInt(ECNetwork.PROTOCOL) >= 12, "13d 起协议号至少是 12，实际 " + ECNetwork.PROTOCOL);
        HoannaEvent storm = HoannaEvent.LEY_STORM;
        helper.assertTrue(storm.id() == 5 && "ley_storm".equals(storm.key()) && storm.baseDuration() == 6_000, "LEY_STORM = (5, ley_storm, 6 000 刻)");
        helper.assertFalse(Arrays.asList(HoannaEvent.rollable()).contains(storm), "LEY_STORM 不进常规掷骰");
        helper.assertTrue(HoannaEvent.rollable().length == 4, "常规掷骰仍是四种");
        helper.assertTrue(HoannaEvent.fromId(5) == storm && HoannaEvent.byName("ley_storm") == storm, "编号 5 / 名字 ley_storm 都能解析");
        helper.assertTrue(HoannaEvent.fromId(6) == HoannaEvent.NONE, "编号 6 当没有事件");

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            S2CHoannaEventPacket.encode(new S2CHoannaEventPacket(storm, 1_234, 6_000), buf);
            S2CHoannaEventPacket back = S2CHoannaEventPacket.decode(buf);
            helper.assertTrue(back.event() == storm && back.remaining() == 1_234 && back.total() == 6_000, "事件包往返不变：" + back);
        } finally {
            buf.release();
        }
        helper.assertTrue(HoannaHazardsConfig.eventEnabled(storm), "地脉风暴的开关 ley_storm_enabled 默认开着");
        try {
            HoannaHazardsConfig.setTestLeyStorm(false, null, null, null, null);
            helper.assertFalse(HoannaHazardsConfig.eventEnabled(storm), "关掉 ley_storm_enabled 就不算开着");
        } finally {
            HoannaHazardsConfig.clearTestOverrides();
        }
        helper.succeed();
    }

    // ================================================================== 潮汐

    /** 8 个月相的潮位、大潮 3 天；关掉恒 0.5；测试覆盖优先、null 还原 */
    @GameTest(template = EMPTY)
    public static void hoannaTideValues(GameTestHelper helper) {
        float[] expected = {1F, 0.75F, 0.5F, 0.25F, 0F, 0.25F, 0.5F, 0.75F};
        int spring = 0;
        for (int p = 0; p < HoannaTide.PHASES; p++) {
            float value = HoannaTide.value(p);
            helper.assertTrue(Math.abs(value - expected[p]) < EPS, "月相 " + p + " 的潮位应是 " + expected[p] + "，实际 " + value);
            if (HoannaTide.isSpringTide(value)) {
                spring++;
            }
        }
        helper.assertTrue(spring == 3, "八天里大潮应是 3 天，实际 " + spring);
        helper.assertTrue(HoannaTide.value(8) == HoannaTide.value(0) && HoannaTide.value(-1) == HoannaTide.value(7), "越界的月相按 8 取模");

        ServerLevel level = helper.getLevel();
        try {
            ECHoannaFramework.Config.setTestTideEnabled(true);
            helper.assertTrue(HoannaTide.value(level) == HoannaTide.value(level.getMoonPhase()), "开着时按这个世界的月相算");
            ECHoannaFramework.Config.setTestTideEnabled(false);
            helper.assertTrue(HoannaTide.value(level) == HoannaTide.DISABLED_VALUE && !HoannaTide.isSpringTide(level), "关掉时恒 0.5、没有大潮");
            helper.assertTrue(HoannaTide.value((ServerLevel) null) == HoannaTide.DISABLED_VALUE, "没有世界时 0.5");
            HoannaTide.setTestOverride(0.9F);
            helper.assertTrue(HoannaTide.value(level) == 0.9F && HoannaTide.isSpringTide(level), "测试覆盖优先（关掉时也一样）");
            HoannaTide.setTestOverride(null);
            helper.assertTrue(HoannaTide.value(level) == HoannaTide.DISABLED_VALUE, "覆盖还原后回到配置的结果");
        } finally {
            HoannaTide.setTestOverride(null);
            ECHoannaFramework.Config.setTestTideEnabled(null);
        }
        helper.succeed();
    }

    // ================================================================== 战利品条件

    /** 五个条件都注册了、JSON 能解析成对的类型和参数、坏参数报错 */
    @GameTest(template = EMPTY)
    public static void hoannaLootConditionsParse(GameTestHelper helper) {
        helper.assertTrue(BuiltInRegistries.LOOT_CONDITION_TYPE.get(HoannaLootConditions.HOANNA_EVENT_ID) == HoannaLootConditions.HOANNA_EVENT
                && BuiltInRegistries.LOOT_CONDITION_TYPE.get(HoannaLootConditions.SKY_VISIBLE_ID) == HoannaLootConditions.SKY_VISIBLE
                && BuiltInRegistries.LOOT_CONDITION_TYPE.get(HoannaLootConditions.NIGHT_OR_DARKNESS_ID) == HoannaLootConditions.NIGHT_OR_DARKNESS
                && BuiltInRegistries.LOOT_CONDITION_TYPE.get(HoannaLootConditions.HOANNA_TIDE_ID) == HoannaLootConditions.HOANNA_TIDE
                && BuiltInRegistries.LOOT_CONDITION_TYPE.get(HoannaLootConditions.FROM_NEST_ID) == HoannaLootConditions.FROM_NEST,
                "五个战利品条件都应已注册");
        Gson gson = Deserializers.createConditionSerializer().create();
        helper.assertTrue(parse(gson, "{\"condition\": \"essentialcraft5:hoanna_event\", \"event\": \"darkness\"}")
                        instanceof HoannaLootConditions.EventCondition c && c.event() == HoannaEvent.DARKNESS, "hoanna_event darkness");
        helper.assertTrue(parse(gson, "{\"condition\": \"essentialcraft5:hoanna_event\", \"event\": \"any\"}")
                        instanceof HoannaLootConditions.EventCondition c && c.event() == null, "hoanna_event any");
        helper.assertTrue(parse(gson, "{\"condition\": \"essentialcraft5:hoanna_event\", \"event\": \"ley_storm\"}")
                        instanceof HoannaLootConditions.EventCondition c && c.event() == HoannaEvent.LEY_STORM, "hoanna_event ley_storm");
        helper.assertTrue(parse(gson, "{\"condition\": \"essentialcraft5:sky_visible\"}") instanceof HoannaLootConditions.SkyVisibleCondition, "sky_visible");
        helper.assertTrue(parse(gson, "{\"condition\": \"essentialcraft5:night_or_darkness\"}") instanceof HoannaLootConditions.NightOrDarknessCondition,
                "night_or_darkness");
        helper.assertTrue(parse(gson, "{\"condition\": \"essentialcraft5:hoanna_tide\", \"min\": 0.75}")
                        instanceof HoannaLootConditions.TideCondition c && Math.abs(c.min() - 0.75F) < EPS, "hoanna_tide min 0.75");
        helper.assertTrue(parse(gson, "{\"condition\": \"essentialcraft5:from_nest\"}") instanceof HoannaLootConditions.FromNestCondition, "from_nest");
        boolean rejected = false;
        try {
            parse(gson, "{\"condition\": \"essentialcraft5:hoanna_event\", \"event\": \"blizzard\"}");
        } catch (JsonParseException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "不认识的天灾名应报错");
        String json = gson.toJson(HoannaLootConditions.hoannaTide(0.5F).build(), LootItemCondition.class);
        helper.assertTrue(parse(gson, json) instanceof HoannaLootConditions.TideCondition c && Math.abs(c.min() - 0.5F) < EPS,
                "datagen 构造写出的 JSON 能读回：" + json);
        helper.succeed();
    }

    /** hoanna_event：海纳尔里当前事件对得上才真；any = 有任意事件（含地脉风暴）；不是海纳尔一律假 */
    @GameTest(template = EMPTY)
    public static void hoannaEventCondition(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LootContext context = context(helper, new Vec3(1.5D, 1D, 1.5D), null);
        LootItemCondition darkness = HoannaLootConditions.hoannaEvent(HoannaEvent.DARKNESS).build();
        LootItemCondition any = HoannaLootConditions.hoannaEvent(null).build();
        helper.assertFalse(darkness.test(context) || any.test(context), "不是海纳尔的世界一律假");
        inHoanna(helper, () -> {
            helper.assertFalse(any.test(context), "海纳尔里没有事件时 any 为假");
            HoannaEvents.start(level, HoannaEvent.DARKNESS, 200);
            helper.assertTrue(darkness.test(context) && any.test(context), "黑暗中：darkness、any 都真");
            helper.assertFalse(HoannaLootConditions.hoannaEvent(HoannaEvent.EARTHQUAKE).build().test(context), "黑暗中 earthquake 为假");
            HoannaEvents.start(level, HoannaEvent.LEY_STORM, 200);
            helper.assertTrue(any.test(context) && HoannaLootConditions.hoannaEvent(HoannaEvent.LEY_STORM).build().test(context),
                    "地脉风暴中 any、ley_storm 都真");
            helper.assertFalse(darkness.test(context), "地脉风暴中 darkness 为假");
        });
        helper.succeed();
    }

    /** sky_visible：露天真；头顶有不透光的方块（等光照更新完）假 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void skyVisibleCondition(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LootItemCondition sky = HoannaLootConditions.skyVisible().build();
        Vec3 open = new Vec3(1.5D, 1D, 1.5D);
        Vec3 covered = new Vec3(3.5D, 1D, 3.5D);
        BlockPos roof = new BlockPos(3, 3, 3);
        helper.assertTrue(sky.test(context(helper, open, null)) && HoannaLootConditions.skyVisibleAt(level, helper.absoluteVec(open)), "露天应为真");
        helper.setBlock(roof, Blocks.STONE);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertFalse(level.canSeeSky(helper.absolutePos(roof.below())), "等屋顶的光照更新"))
                .thenExecute(() -> {
                    helper.assertFalse(sky.test(context(helper, covered, null)), "屋顶下应为假");
                    helper.assertTrue(sky.test(context(helper, open, null)), "旁边露天的仍为真");
                })
                .thenSucceed();
    }

    /** night_or_darkness：纯函数的夜里区间；黑暗天灾中白天也真；别的时候等于「是不是夜里」 */
    @GameTest(template = EMPTY)
    public static void nightOrDarknessCondition(GameTestHelper helper) {
        helper.assertTrue(!HoannaLootConditions.isNight(12_999L) && HoannaLootConditions.isNight(13_000L) && HoannaLootConditions.isNight(22_999L)
                && !HoannaLootConditions.isNight(23_000L) && !HoannaLootConditions.isNight(6_000L) && HoannaLootConditions.isNight(24_000L * 3 + 14_000L)
                && !HoannaLootConditions.isNight(-1L), "夜里 = 日内刻 [13 000, 23 000)，按一天取模");
        ServerLevel level = helper.getLevel();
        LootContext context = context(helper, new Vec3(1.5D, 1D, 1.5D), null);
        LootItemCondition condition = HoannaLootConditions.nightOrDarkness().build();
        helper.assertTrue(condition.test(context) == HoannaLootConditions.isNight(level.getDayTime()), "没有黑暗时等于「是不是夜里」");
        inHoanna(helper, () -> {
            HoannaEvents.start(level, HoannaEvent.DARKNESS, 200);
            helper.assertTrue(condition.test(context), "黑暗天灾中为真（不管几点）");
            HoannaEvents.start(level, HoannaEvent.EARTHQUAKE, 200);
            helper.assertTrue(condition.test(context) == HoannaLootConditions.isNight(level.getDayTime()), "别的天灾不算黑暗");
        });
        helper.succeed();
    }

    /** hoanna_tide：潮位 ≥ min；from_nest：THIS_ENTITY 的来源是巢穴 */
    @GameTest(template = EMPTY)
    public static void tideAndNestConditions(GameTestHelper helper) {
        LootContext context = context(helper, new Vec3(1.5D, 1D, 1.5D), null);
        LootItemCondition spring = HoannaLootConditions.hoannaTide(HoannaTide.SPRING_TIDE).build();
        try {
            HoannaTide.setTestOverride(0.75F);
            helper.assertTrue(spring.test(context), "潮位 0.75 ≥ 0.75 为真");
            HoannaTide.setTestOverride(0.5F);
            helper.assertFalse(spring.test(context), "潮位 0.5 < 0.75 为假");
        } finally {
            HoannaTide.setTestOverride(null);
        }

        LootItemCondition nest = HoannaLootConditions.fromNest().build();
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        helper.assertTrue(zombie != null, "建僵尸");
        try {
            helper.assertFalse(nest.test(context(helper, new Vec3(1.5D, 1D, 1.5D), zombie)), "没有来源的不算巢穴");
            SpawnOrigin.set(zombie, SpawnOrigin.Origin.NATURAL);
            helper.assertFalse(nest.test(context(helper, new Vec3(1.5D, 1D, 1.5D), zombie)), "自然生成的不算巢穴");
            SpawnOrigin.set(zombie, SpawnOrigin.Origin.NEST);
            helper.assertTrue(nest.test(context(helper, new Vec3(1.5D, 1D, 1.5D), zombie)), "巢穴放出的为真");
            helper.assertFalse(nest.test(context), "没有 THIS_ENTITY 为假");
        } finally {
            zombie.discard();
        }
        helper.succeed();
    }

    // ================================================================== 辐射

    /** addFloored：裸身 = 原数；全套宅男（易伤 0）= 一半；创造、假玩家不加 */
    @GameTest(template = EMPTY)
    public static void radiationAddFloored(GameTestHelper helper) {
        helper.assertTrue(Radiation.flooredAmount(1_500L, 1F, 0.5F) == 1_500L && Radiation.flooredAmount(1_500L, 0F, 0.5F) == 750L
                && Radiation.flooredAmount(3L, 0.25F, 0.5F) == 2L && Radiation.flooredAmount(-5L, 1F, 0.5F) == 0L
                && Radiation.flooredAmount(1_000L, Float.NaN, 0.5F) == 1_000L, "纯函数：round(base × max(易伤, floor))，非有限易伤按 1");
        HoannaHazardsConfig.setTestRadiationEnabled(true);
        try {
            ServerPlayer bare = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-bare");
            Radiation.set(bare, 0L);
            int dose = Radiation.addFloored(bare, 1_500L, 0.5F);
            helper.assertTrue(dose == 1_500 && Radiation.get(bare) == 1_500, "裸身加原数 1 500，实际 " + dose);

            ServerPlayer armored = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-armored");
            armored.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ECComputer.COMPUTER_HELMET.get()));
            armored.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ECComputer.COMPUTER_CHESTPLATE.get()));
            armored.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ECComputer.COMPUTER_LEGGINGS.get()));
            armored.setItemSlot(EquipmentSlot.FEET, new ItemStack(ECComputer.COMPUTER_BOOTS.get()));
            Radiation.set(armored, 0L);
            int floored = Radiation.addFloored(armored, 1_500L, 0.5F);
            helper.assertTrue(floored == 750, "全套宅男（易伤 0）至少吃一半 = 750，实际 " + floored);

            ServerPlayer creative = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-creative");
            creative.setGameMode(GameType.CREATIVE);
            Radiation.set(creative, 0L);
            helper.assertTrue(Radiation.addFloored(creative, 1_500L, 0.5F) == 0 && Radiation.get(creative) == 0, "创造模式不加");

            ServerPlayer fake = FakePlayerFactory.get(helper.getLevel(),
                    new com.mojang.authlib.GameProfile(UUID.fromString("5cd89d0b-e9ba-0000-89f4-badbb05964ff"), "[EC5]Test"));
            int before = Radiation.get(fake);
            helper.assertTrue(Radiation.addFloored(fake, 1_500L, 0.5F) == before, "假玩家不加");
            helper.assertTrue(Radiation.addFloored(bare, 0L, 0.5F) == 1_500, "base 0 不加");
        } finally {
            HoannaHazardsConfig.setTestRadiationEnabled(null);
        }
        helper.succeed();
    }

    // ================================================================== 材料与方块

    /** 四种结晶、两种异变材料、渊盐已注册；特产标签六样；霜髓只有精准采集掉本体 */
    @GameTest(template = EMPTY)
    public static void hoannaMaterialsRegistered(GameTestHelper helper) {
        List<Supplier<? extends Item>> items = List.of(ECHoannaMaterials.LEY_CRYSTAL, ECHoannaMaterials.RADIANT_CRYSTAL, ECHoannaMaterials.TIDE_CRYSTAL,
                ECHoannaMaterials.UMBRAL_CRYSTAL, ECMobDrops.ABERRANT_RESIDUE, ECMobDrops.ABERRANT_HEART, ECHoannaSurvey.ABYSSAL_SALT);
        for (Supplier<? extends Item> item : items) {
            helper.assertTrue(item.get() != null && item.get() != Items.AIR && new ItemStack(item.get()).getMaxStackSize() == 64,
                    "应已注册、一组 64：" + BuiltInRegistries.ITEM.getKey(item.get()));
        }
        List<Item> specialties = List.of(ECHoannaMaterials.LEY_CRYSTAL.get(), ECHoannaMaterials.RADIANT_CRYSTAL.get(), ECHoannaMaterials.TIDE_CRYSTAL.get(),
                ECHoannaMaterials.FROST_MARROW.get().asItem(), ECHoannaMaterials.UMBRAL_CRYSTAL.get(), ECHoannaSurvey.ABYSSAL_SALT.get());
        int count = 0;
        for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(ECHoannaMaterials.HOANNA_SPECIALTIES)) {
            count++;
            helper.assertTrue(specialties.contains(holder.value()), "特产标签里不该有 " + BuiltInRegistries.ITEM.getKey(holder.value()));
        }
        helper.assertTrue(count == 6, "特产标签应正好六样，实际 " + count);
        for (Item item : specialties) {
            helper.assertTrue(new ItemStack(item).is(ECHoannaMaterials.HOANNA_SPECIALTIES), "特产标签应含 " + BuiltInRegistries.ITEM.getKey(item));
        }
        helper.assertFalse(new ItemStack(ECVoid.TWINKLING_TITANITE.get()).is(ECHoannaMaterials.HOANNA_SPECIALTIES),
                "兜底的闪耀榍石已从特产标签删掉");

        Block marrow = ECHoannaMaterials.FROST_MARROW.get();
        BlockState state = marrow.defaultBlockState();
        helper.assertTrue(Math.abs(marrow.getFriction() - ECHoannaMaterials.FROST_MARROW_FRICTION) < EPS && state.getLightEmission() == 3,
                "霜髓滑度 0.98、亮度 3");
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        List<ItemStack> plain = Block.getDrops(state, level, pos, null, null, pickaxe);
        helper.assertTrue(plain.isEmpty(), "没有精准采集什么都不掉，实际 " + plain);
        ItemStack silk = new ItemStack(Items.DIAMOND_PICKAXE);
        silk.enchant(Enchantments.SILK_TOUCH, 1);
        List<ItemStack> silkDrops = Block.getDrops(state, level, pos, null, null, silk);
        helper.assertTrue(silkDrops.size() == 1 && silkDrops.get(0).is(marrow.asItem()) && silkDrops.get(0).getCount() == 1,
                "精准采集掉本体 1 个，实际 " + silkDrops);
        helper.succeed();
    }

    /** 方块空壳的状态属性在预备定死（H1b 的地物 JSON 按它们引用） */
    @GameTest(template = EMPTY)
    public static void hoannaBlockShellStates(GameTestHelper helper) {
        BlockState vent = ECHoannaResources.LEY_VENT.get().defaultBlockState();
        helper.assertTrue(vent.getValue(LeyVentBlock.CHARGE) == 0 && LeyVentBlock.MAX_CHARGE == 3, "地脉喷口 charge 0~3，默认 0");
        helper.assertTrue(vent.setValue(LeyVentBlock.CHARGE, 3).getLightEmission() == 6 && vent.getLightEmission() == 0, "喷口亮度 = charge × 2");
        BlockState bud = ECHoannaResources.TIDE_BUD.get().defaultBlockState();
        helper.assertTrue(bud.hasProperty(TideBudBlock.STAGE) && TideBudBlock.STAGE.getPossibleValues().size() == 4, "潮晶芽 stage 0~3");
        BlockState umbral = ECHoannaResources.UMBRAL_CLUSTER.get().defaultBlockState();
        helper.assertTrue(umbral.getValue(UmbralClusterBlock.RIPE) && umbral.hasProperty(BlockStateProperties.FACING)
                && umbral.hasProperty(BlockStateProperties.WATERLOGGED), "影晶簇 facing / waterlogged / ripe（默认成熟）");
        helper.assertTrue(umbral.getLightEmission() == 2 && umbral.setValue(UmbralClusterBlock.RIPE, false).getLightEmission() == 2,
                "影晶簇亮度 2，成熟 / 残簇一样");
        BlockState radiant = ECHoannaResources.RADIANT_CLUSTER.get().defaultBlockState();
        helper.assertTrue(radiant.hasProperty(BlockStateProperties.FACING) && radiant.hasProperty(BlockStateProperties.WATERLOGGED)
                && radiant.getLightEmission() == 5, "辐晶簇 facing / waterlogged、亮度 5");
        helper.assertTrue(ECHoannaResources.TITANITE_ORE.get().defaultBlockState().getProperties().isEmpty()
                && ECHoannaSurvey.ABYSSAL_SALT_CRUST.get().defaultBlockState().getProperties().isEmpty()
                && ECHoannaSurvey.RUIN_MILESTONE.get().defaultBlockState().getProperties().isEmpty(), "榍石矿、渊盐结壳、里程碑没有状态属性");
        helper.assertTrue(ECHoannaSurvey.RUIN_MILESTONE.get().defaultBlockState().getLightEmission() == 10, "里程碑亮度 10");
        for (Block block : List.of(ECHoannaResources.LEY_VENT.get(), ECHoannaResources.TITANITE_ORE.get(), ECHoannaResources.RADIANT_CLUSTER.get(),
                ECHoannaResources.TIDE_BUD.get(), ECHoannaResources.UMBRAL_CLUSTER.get(), ECHoannaSurvey.ABYSSAL_SALT_CRUST.get(),
                ECHoannaSurvey.RUIN_MILESTONE.get(), ECHoannaMaterials.FROST_MARROW.get())) {
            helper.assertTrue(block.asItem() != Items.AIR, "应带方块物品：" + BuiltInRegistries.BLOCK.getKey(block));
        }
        helper.succeed();
    }

    // ================================================================== 实体空壳

    /** 九个实体都注册了、MONSTER、有默认属性（生命 / 护甲照 §5.2）；hoanna_natives 八种（不含幼体） */
    @GameTest(template = EMPTY)
    public static void hoannaNativeShells(GameTestHelper helper) {
        record Shell(RegistryObject<? extends EntityType<? extends LivingEntity>> type, double health, double armor, boolean native_) {
        }
        List<Shell> shells = List.of(
                new Shell(ECHoannaNativesA.FROSTBITTEN, 44, 6, true),
                new Shell(ECHoannaNativesA.MANA_PRISM, 30, 12, true),
                new Shell(ECHoannaNativesA.HOANNA_SCAVENGER, 36, 8, true),
                new Shell(ECHoannaNativesB.CHAOS_ABERRANT, 60, 4, true),
                new Shell(ECHoannaNativesB.CHAOS_LARVA, 6, 0, false),
                new Shell(ECHoannaNativesB.EMBER_SPIRIT, 28, 4, true),
                new Shell(ECHoannaNativesB.ABYSSAL_EEL, 34, 4, true),
                new Shell(ECHoannaNativesC.INQUISITORIUM_PATROLLER, 24, 0, true),
                new Shell(ECHoannaNativesC.CATACOMB_REVENANT, 40, 10, true));
        TagKey<EntityType<?>> soulCapturable = TagKey.create(Registries.ENTITY_TYPE, EssentialCraft5.id("soul_capturable"));
        ServerLevel level = helper.getLevel();
        int natives = 0;
        for (Shell shell : shells) {
            EntityType<? extends LivingEntity> type = shell.type().get();
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            helper.assertTrue(type.getCategory() == MobCategory.MONSTER, id + " 应是 MONSTER");
            helper.assertTrue(DefaultAttributes.hasSupplier(type), id + " 应登记了属性");
            helper.assertTrue(type.is(ECTags.HOANNA_NATIVES) == shell.native_(), id + " 在 hoanna_natives 里应是 " + shell.native_());
            if (type.is(ECTags.HOANNA_NATIVES)) {
                natives++;
            }
            LivingEntity entity = type.create(level);
            helper.assertTrue(entity != null, id + " 应能建出来");
            try {
                helper.assertTrue(entity.getMaxHealth() == (float) shell.health() && entity.getArmorValue() == (int) shell.armor(),
                        id + " 生命 / 护甲应是 " + shell.health() + " / " + shell.armor() + "，实际 " + entity.getMaxHealth() + " / " + entity.getArmorValue());
            } finally {
                entity.discard();
            }
        }
        int tagged = 0;
        for (var ignored : BuiltInRegistries.ENTITY_TYPE.getTagOrEmpty(ECTags.HOANNA_NATIVES)) {
            tagged++;
        }
        helper.assertTrue(natives == 8 && tagged == 8, "hoanna_natives 应正好八种，实际 " + tagged);
        helper.assertTrue(ECHoannaNativesA.HOANNA_SCAVENGER.get().is(soulCapturable), "遗民拾荒者的灵魂能收");
        helper.assertTrue(ECHoannaNativesB.CHAOS_LARVA.get().is(ECTags.GROWTH_EXCLUDED), "畸变幼体进 growth_excluded");
        helper.succeed();
    }

    // ================================================================== 进度

    /** awardIfPresent：第一次发掉全部条件；已完成、进度不存在、假玩家、null 返回 false */
    @GameTest(template = EMPTY)
    public static void awardIfPresent(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-award");
        ResourceLocation id = ECHoannaHazards.GUIDE_RADIATION;
        Advancement advancement = player.server.getAdvancements().getAdvancement(id);
        helper.assertTrue(advancement != null, "测试用的进度 " + id + " 应存在");
        helper.assertTrue(ECAdvancements.awardIfPresent(player, id), "第一次应发掉");
        helper.assertTrue(player.getAdvancements().getOrStartProgress(advancement).isDone(), "发完应已完成");
        helper.assertFalse(ECAdvancements.awardIfPresent(player, id), "已完成的再发返回 false");
        helper.assertFalse(ECAdvancements.awardIfPresent(player, EssentialCraft5.id("guide/does_not_exist")), "不存在的进度返回 false、不报错");
        helper.assertFalse(ECAdvancements.awardIfPresent(null, id), "null 返回 false");
        helper.assertFalse(ECAdvancements.awardIfPresent(FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.fromString("5cd89d0b-e9ba-0000-89f4-badbb05964ff"), "[EC5]Test")), id), "假玩家不发");
        helper.succeed();
    }

    // ------------------------------------------------------------------ 工具

    private static LootItemCondition parse(Gson gson, String json) {
        return gson.fromJson(JsonParser.parseString(json), LootItemCondition.class);
    }

    /** 只带原点（和可选的 THIS_ENTITY）的战利品上下文；原点是相对测试结构的坐标 */
    private static LootContext context(GameTestHelper helper, Vec3 relativeOrigin, @org.jetbrains.annotations.Nullable LivingEntity self) {
        LootParams params = new LootParams.Builder(helper.getLevel())
                .withParameter(LootContextParams.ORIGIN, helper.absoluteVec(relativeOrigin))
                .withOptionalParameter(LootContextParams.THIS_ENTITY, self)
                .create(ORIGIN_ONLY);
        return new LootContext.Builder(params).create(null);
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
        }
    }

    private static IPlayerData data(Player player) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            throw new IllegalStateException("测试玩家应挂着玩家数据");
        }
        return data;
    }
}
