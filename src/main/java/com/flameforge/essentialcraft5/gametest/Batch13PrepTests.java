package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.api.MatrixRanks;
import com.flameforge.essentialcraft5.demon.DemonStanding;
import com.flameforge.essentialcraft5.item.attunement.AttunementConfig;
import com.flameforge.essentialcraft5.matrix.MatrixConfig;
import com.flameforge.essentialcraft5.matrix.MatrixResonanceConfig;
import com.flameforge.essentialcraft5.matrix.MatrixTrialConfig;
import com.flameforge.essentialcraft5.network.ECNetwork;
import com.flameforge.essentialcraft5.network.S2CMatrixCodexPacket;
import com.flameforge.essentialcraft5.player.PlayerData;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECTags;
import com.flameforge.essentialcraft5.util.SpawnOrigin;
import com.flameforge.essentialcraft5.util.SpawnOrigin.Origin;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 第十三批 13a 预备（batch13.md §9.1）的 GameTest：
 * <ul>
 *   <li>玩家数据新键：默认值（旧存档读出来 = 零阶、空图谱、容量开关开……）、往返、夹范围；</li>
 *   <li>同步拆分：普通同步包不带两份图谱、客户端收包保留本地图谱；容量 / 预算的安静写法不标脏（预算跨整 10 点才标）；</li>
 *   <li>两个冷却按刻减、走完报一次；死亡复制保留阶 / 图谱 / 声望 / 储备 / 预算 / 冷却、只清容量；</li>
 *   <li>图谱包编解码与限长；协议号至少 10（13c 起是 11，见 {@code Batch13cPrepTests}）；</li>
 *   <li>生物来源：分类表、刷怪蛋 / 刷怪笼 / 自然生成真的记上、没走 finalizeSpawn 的是「未知」、记过的不被改写、转化沿用原来的；</li>
 *   <li>共用标签的成员、配置默认值。</li>
 * </ul>
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class Batch13PrepTests {

    private static final String EMPTY = "empty";

    private Batch13PrepTests() {
    }

    private static IPlayerData data(Player player) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            throw new IllegalStateException("模拟玩家应挂着玩家数据");
        }
        return data;
    }

    /** 新键的默认值：新建的数据和没有这些键的旧存档都读成现状；整份往返不丢；越界的值夹回范围 */
    @GameTest(template = EMPTY)
    public static void matrixPlayerDataDefaults(GameTestHelper helper) {
        PlayerData fresh = new PlayerData();
        assertDefaults(helper, fresh, "新建的玩家数据");

        CompoundTag old = new CompoundTag();
        old.putInt("matrix_damage", 1200);
        old.putInt("ubmru", 345);
        old.putInt("wishes", 2);
        old.putInt("data_version", PlayerData.DATA_VERSION);
        PlayerData legacy = new PlayerData();
        legacy.deserializeNBT(old);
        assertDefaults(helper, legacy, "没有第十三批键的旧存档");
        helper.assertTrue(legacy.getMatrixDamage() == 1200 && legacy.getUBMRU() == 345, "旧字段照读");

        PlayerData full = new PlayerData();
        full.setMatrixRank(5);
        full.addMatrixCodex("minecraft:zombie");
        full.addMatrixCodex("minecraft:blaze");
        full.addMatrixCodexHoanna("minecraft:zombie");
        full.setMatrixPool(12_400, true);
        full.setMatrixPoolAbsorb(false);
        full.setMatrixTrialCooldown(3600);
        full.setMatrixTrialReserve(5000);
        full.setDemonStanding(23);
        full.setAttunementBudget(IPlayerData.BudgetKind.ARMOR, 123.4F, true);
        full.setAttunementBudget(IPlayerData.BudgetKind.WEAPON, 7.5F, true);
        full.setAttunementOverloadCooldown(1200);
        PlayerData copy = new PlayerData();
        copy.deserializeNBT(full.serializeNBT());
        helper.assertTrue(copy.getMatrixRank() == 5, "阶往返，实际 " + copy.getMatrixRank());
        helper.assertTrue(List.copyOf(copy.getMatrixCodex()).equals(List.of("minecraft:zombie", "minecraft:blaze")),
                "图谱往返且保持先后，实际 " + copy.getMatrixCodex());
        helper.assertTrue(copy.getMatrixCodexHoanna().equals(Set.of("minecraft:zombie")), "海纳尔图谱往返，实际 " + copy.getMatrixCodexHoanna());
        helper.assertTrue(copy.getMatrixPool() == 12_400 && !copy.isMatrixPoolAbsorb(), "容量与开关往返");
        helper.assertTrue(copy.getMatrixTrialCooldown() == 3600 && copy.getMatrixTrialReserve() == 5000, "试炼冷却与储备往返");
        helper.assertTrue(copy.getDemonStanding() == 23, "契约之誉往返");
        helper.assertTrue(Math.abs(copy.getAttunementBudget(IPlayerData.BudgetKind.ARMOR) - 123.4F) < 1.0E-3F
                && Math.abs(copy.getAttunementBudget(IPlayerData.BudgetKind.WEAPON) - 7.5F) < 1.0E-3F, "两份预算往返");
        helper.assertTrue(copy.getAttunementOverloadCooldown() == 1200, "过载冷却往返");

        PlayerData clamp = new PlayerData();
        clamp.setMatrixRank(15);
        helper.assertTrue(clamp.getMatrixRank() == IPlayerData.MAX_MATRIX_RANK, "阶夹到 9，实际 " + clamp.getMatrixRank());
        clamp.setMatrixRank(-3);
        clamp.setMatrixPool(-10, true);
        clamp.setDemonStanding(-1);
        clamp.setAttunementBudget(IPlayerData.BudgetKind.ARMOR, Float.NaN, true);
        helper.assertTrue(clamp.getMatrixRank() == 0 && clamp.getMatrixPool() == 0 && clamp.getDemonStanding() == 0
                && clamp.getAttunementBudget(IPlayerData.BudgetKind.ARMOR) == 0F, "负数、NaN 夹到 0");
        helper.assertFalse(clamp.addMatrixCodex("") || clamp.addMatrixCodex(null), "空 id 不记");
        CompoundTag bad = full.serializeNBT();
        bad.putInt("matrix_rank", 99);
        bad.putInt("matrix_pool", -5);
        PlayerData fixed = new PlayerData();
        fixed.deserializeNBT(bad);
        helper.assertTrue(fixed.getMatrixRank() == 9 && fixed.getMatrixPool() == 0, "手改坏的存档读档时夹回范围");
        helper.succeed();
    }

    private static void assertDefaults(GameTestHelper helper, IPlayerData data, String what) {
        helper.assertTrue(data.getMatrixRank() == 0, what + "：零阶");
        helper.assertTrue(data.getMatrixCodex().isEmpty() && data.getMatrixCodexHoanna().isEmpty(), what + "：图谱是空的");
        helper.assertTrue(data.getMatrixPool() == 0 && data.isMatrixPoolAbsorb(), what + "：容量 0、自动换入开着");
        helper.assertTrue(data.getMatrixTrialCooldown() == 0 && data.getMatrixTrialReserve() == 0, what + "：没有试炼冷却和储备");
        helper.assertTrue(data.getDemonStanding() == 0, what + "：契约之誉 0");
        helper.assertTrue(data.getAttunementBudget(IPlayerData.BudgetKind.ARMOR) == 0F
                && data.getAttunementBudget(IPlayerData.BudgetKind.WEAPON) == 0F, what + "：预算 0");
        helper.assertTrue(data.getAttunementOverloadCooldown() == 0, what + "：没有过载冷却");
    }

    /** 普通同步包不带图谱；客户端收普通包保留本地图谱，收整份（带图谱的）照包里的 */
    @GameTest(template = EMPTY)
    public static void matrixSyncOmitsCodex(GameTestHelper helper) {
        PlayerData server = new PlayerData();
        server.addMatrixCodex("minecraft:zombie");
        server.addMatrixCodexHoanna("minecraft:husk");
        server.setMatrixRank(3);
        CompoundTag sync = server.serializeForSync();
        helper.assertFalse(sync.contains("matrix_codex") || sync.contains("matrix_codex_hoanna"), "普通同步包不该带图谱，实际键 " + sync.getAllKeys());
        helper.assertTrue(sync.getInt("matrix_rank") == 3, "阶随普通同步包走");
        helper.assertTrue(server.serializeNBT().contains("matrix_codex"), "存盘的整份要带图谱");

        PlayerData client = new PlayerData();
        client.addMatrixCodex("minecraft:skeleton");
        client.addMatrixCodexHoanna("minecraft:stray");
        client.deserializeSync(sync);
        helper.assertTrue(client.getMatrixRank() == 3, "收包后阶更新");
        helper.assertTrue(client.getMatrixCodex().equals(Set.of("minecraft:skeleton")) && client.getMatrixCodexHoanna().equals(Set.of("minecraft:stray")),
                "收普通同步包要保留本地图谱，实际 " + client.getMatrixCodex() + " / " + client.getMatrixCodexHoanna());
        client.deserializeSync(server.serializeNBT());
        helper.assertTrue(client.getMatrixCodex().equals(Set.of("minecraft:zombie")), "带图谱的整份照包里的，实际 " + client.getMatrixCodex());
        helper.succeed();
    }

    /** 安静写法：容量自然换入不标脏；预算只在跨过整 10 点时标；图谱增删不标；阶、声望照常标 */
    @GameTest(template = EMPTY)
    public static void matrixQuietSetters(GameTestHelper helper) {
        PlayerData data = new PlayerData();
        data.clearDirty();
        data.setMatrixPool(10, false);
        data.setMatrixPool(500, false);
        helper.assertFalse(data.isDirty(), "容量安静地涨不该标脏");
        data.setMatrixPool(400, true);
        helper.assertTrue(data.isDirty(), "容量被消耗要标脏");
        data.clearDirty();
        data.addMatrixCodex("minecraft:zombie");
        data.addMatrixCodexHoanna("minecraft:zombie");
        data.clearMatrixCodex();
        helper.assertFalse(data.isDirty(), "图谱增删不标脏（另发图谱包）");
        data.setAttunementBudget(IPlayerData.BudgetKind.ARMOR, 3.0F, false);
        data.setAttunementBudget(IPlayerData.BudgetKind.ARMOR, 9.9F, false);
        helper.assertFalse(data.isDirty(), "预算在同一个整 10 点里安静地涨不标脏");
        data.setAttunementBudget(IPlayerData.BudgetKind.ARMOR, 10.0F, false);
        helper.assertTrue(data.isDirty(), "预算跨过整 10 点要标一次");
        data.clearDirty();
        data.setAttunementBudget(IPlayerData.BudgetKind.WEAPON, 0.1F, false);
        helper.assertFalse(data.isDirty(), "武器预算安静地涨也不标");
        data.setAttunementBudget(IPlayerData.BudgetKind.WEAPON, 0.0F, true);
        helper.assertTrue(data.isDirty(), "预算被消耗要标脏");
        data.clearDirty();
        data.setMatrixRank(1);
        helper.assertTrue(data.isDirty(), "升阶要标脏");
        data.clearDirty();
        data.setDemonStanding(1);
        helper.assertTrue(data.isDirty(), "声望变化要标脏");
        helper.succeed();
    }

    /** 两个冷却按刻减，走完那一刻报一次；没在冷却的不动 */
    @GameTest(template = EMPTY)
    public static void matrixCooldownsTick(GameTestHelper helper) {
        PlayerData data = new PlayerData();
        helper.assertFalse(data.tickCooldowns(), "没有冷却时什么都不报");
        data.setMatrixTrialCooldown(2);
        data.setAttunementOverloadCooldown(3);
        data.clearDirty();
        helper.assertFalse(data.tickCooldowns(), "第一刻都没走完");
        helper.assertFalse(data.isDirty(), "减冷却本身不标脏");
        helper.assertTrue(data.tickCooldowns(), "试炼冷却第二刻走完");
        helper.assertTrue(data.getMatrixTrialCooldown() == 0 && data.getAttunementOverloadCooldown() == 1, "剩余刻数对不上");
        helper.assertTrue(data.tickCooldowns(), "过载冷却第三刻走完");
        helper.assertFalse(data.tickCooldowns(), "都走完以后不再报");
        helper.assertTrue(data.getMatrixTrialCooldown() == 0 && data.getAttunementOverloadCooldown() == 0, "不会减成负数");
        helper.succeed();
    }

    /** 死亡复制：阶、图谱、声望、储备、预算、冷却都留着，只清容量（和后遗症）；从末地回来（不是死亡）容量也留着 */
    @GameTest(template = EMPTY)
    public static void matrixDeathCloneClearsPoolOnly(GameTestHelper helper) {
        Player original = helper.makeMockPlayer();
        IPlayerData from = data(original);
        from.setMatrixRank(4);
        from.addMatrixCodex("minecraft:creeper");
        from.addMatrixCodexHoanna("minecraft:creeper");
        from.setMatrixPool(9000, true);
        from.setMatrixPoolAbsorb(false);
        from.setMatrixTrialCooldown(100);
        from.setMatrixTrialReserve(777);
        from.setDemonStanding(41);
        from.setAttunementBudget(IPlayerData.BudgetKind.ARMOR, 300F, true);
        from.setAttunementOverloadCooldown(50);

        Player reborn = helper.makeMockPlayer();
        PlayerDataEvents.clone(new PlayerEvent.Clone(reborn, original, true));
        IPlayerData to = data(reborn);
        helper.assertTrue(to.getMatrixPool() == 0, "死亡要清容量，实际 " + to.getMatrixPool());
        helper.assertTrue(to.getMatrixRank() == 4 && to.getMatrixCodex().contains("minecraft:creeper")
                && to.getMatrixCodexHoanna().contains("minecraft:creeper"), "阶和图谱死了不掉");
        helper.assertTrue(!to.isMatrixPoolAbsorb() && to.getMatrixTrialCooldown() == 100 && to.getMatrixTrialReserve() == 777
                && to.getDemonStanding() == 41 && to.getAttunementBudget(IPlayerData.BudgetKind.ARMOR) == 300F
                && to.getAttunementOverloadCooldown() == 50, "开关、冷却、储备、声望、预算死了都不掉");

        Player traveller = helper.makeMockPlayer();
        data(traveller).setMatrixPool(4321, true);
        Player back = helper.makeMockPlayer();
        PlayerDataEvents.clone(new PlayerEvent.Clone(back, traveller, false));
        helper.assertTrue(data(back).getMatrixPool() == 4321, "从末地回来容量照留，实际 " + data(back).getMatrixPool());

        helper.assertTrue(MatrixRanks.get(reborn) == 4 && MatrixRanks.get(null) == 0, "MatrixRanks.get 读的就是玩家数据里的阶");
        helper.assertTrue(DemonStanding.get(reborn) == 41, "DemonStanding.get 读的就是玩家数据里的声望");
        helper.succeed();
    }

    /** 图谱包：整份 / 新记一条编解码不变；超长的条目数解码直接报错；协议号 10 */
    @GameTest(template = EMPTY)
    public static void matrixCodexPacketRoundTrip(GameTestHelper helper) {
        PlayerData data = new PlayerData();
        data.addMatrixCodex("minecraft:zombie");
        data.addMatrixCodex("essentialcraft5:wind_mage");
        data.addMatrixCodexHoanna("minecraft:zombie");
        S2CMatrixCodexPacket full = roundTrip(S2CMatrixCodexPacket.full(data));
        helper.assertTrue(full.replace() && full.codex().equals(List.of("minecraft:zombie", "essentialcraft5:wind_mage"))
                && full.codexHoanna().equals(List.of("minecraft:zombie")), "整份往返不变，实际 " + full);
        S2CMatrixCodexPacket added = roundTrip(S2CMatrixCodexPacket.added("minecraft:husk", null));
        helper.assertTrue(!added.replace() && added.codex().equals(List.of("minecraft:husk")) && added.codexHoanna().isEmpty(),
                "新记一条往返不变，实际 " + added);

        FriendlyByteBuf evil = new FriendlyByteBuf(Unpooled.buffer());
        evil.writeBoolean(true);
        evil.writeVarInt(S2CMatrixCodexPacket.MAX_ENTRIES + 1);
        boolean threw = false;
        try {
            S2CMatrixCodexPacket.decode(evil);
        } catch (RuntimeException expected) {
            threw = true;
        }
        helper.assertTrue(threw, "条目数超过上限的包解码要报错");
        helper.assertTrue(Integer.parseInt(ECNetwork.PROTOCOL) >= 10, "协议号从第十三批 13a 起至少是 10，实际 " + ECNetwork.PROTOCOL);
        helper.succeed();
    }

    private static S2CMatrixCodexPacket roundTrip(S2CMatrixCodexPacket packet) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        S2CMatrixCodexPacket.encode(packet, buf);
        return S2CMatrixCodexPacket.decode(buf);
    }

    /** 生成类型 → 来源的对照表（每种 MobSpawnType 都有归属）；带着刷怪笼对象的一律算刷怪笼 */
    @GameTest(template = EMPTY)
    public static void spawnOriginClassify(GameTestHelper helper) {
        helper.assertTrue(SpawnOrigin.classify(MobSpawnType.SPAWNER, false) == Origin.SPAWNER, "刷怪笼");
        helper.assertTrue(SpawnOrigin.classify(MobSpawnType.TRIGGERED, false) == Origin.SPAWNER, "TRIGGERED 算刷怪笼");
        helper.assertTrue(SpawnOrigin.classify(MobSpawnType.SPAWN_EGG, false) == Origin.SPAWN_EGG, "刷怪蛋");
        helper.assertTrue(SpawnOrigin.classify(MobSpawnType.DISPENSER, false) == Origin.SPAWN_EGG, "发射器放刷怪蛋");
        for (MobSpawnType type : List.of(MobSpawnType.NATURAL, MobSpawnType.CHUNK_GENERATION, MobSpawnType.PATROL)) {
            helper.assertTrue(SpawnOrigin.classify(type, false) == Origin.NATURAL, "自然：" + type);
        }
        // 13c 起事件从「自然」里拆出来（两者都公平）
        helper.assertTrue(SpawnOrigin.classify(MobSpawnType.EVENT, false) == Origin.EVENT, "事件：EVENT");
        helper.assertTrue(SpawnOrigin.classify(MobSpawnType.STRUCTURE, false) == Origin.STRUCTURE, "结构");
        for (MobSpawnType type : List.of(MobSpawnType.MOB_SUMMONED, MobSpawnType.REINFORCEMENT, MobSpawnType.JOCKEY)) {
            helper.assertTrue(SpawnOrigin.classify(type, false) == Origin.SUMMONED, "召唤：" + type);
        }
        for (MobSpawnType type : List.of(MobSpawnType.BREEDING, MobSpawnType.CONVERSION, MobSpawnType.BUCKET, MobSpawnType.COMMAND)) {
            helper.assertTrue(SpawnOrigin.classify(type, false) == Origin.OTHER, "其它：" + type);
        }
        helper.assertTrue(SpawnOrigin.classify(MobSpawnType.NATURAL, true) == Origin.SPAWNER, "带着刷怪笼对象的算刷怪笼");
        helper.assertTrue(Arrays.stream(MobSpawnType.values()).allMatch(type -> SpawnOrigin.classify(type, false) != Origin.UNKNOWN),
                "每种生成类型都要有归属，不能分成「未知」");
        helper.assertTrue(Origin.SPAWNER.isArtificial() && Origin.SPAWN_EGG.isArtificial() && !Origin.UNKNOWN.isArtificial()
                && !Origin.NATURAL.isArtificial() && !Origin.EVENT.isArtificial() && !Origin.NEST.isArtificial() && !Origin.BOSS.isArtificial(),
                "只有刷怪笼、刷怪蛋算人为来源");
        helper.succeed();
    }

    /** 真的生成一次：刷怪蛋 / 刷怪笼 / 自然生成都记上；没走 finalizeSpawn 的是未知；记过的不被改写；转化沿用原来的来源 */
    @GameTest(template = EMPTY)
    public static void spawnOriginRecordsOnSpawn(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        Mob egg = EntityType.ZOMBIE.spawn(helper.getLevel(), pos, MobSpawnType.SPAWN_EGG);
        Mob spawner = EntityType.SKELETON.spawn(helper.getLevel(), pos, MobSpawnType.SPAWNER);
        Mob natural = EntityType.SPIDER.spawn(helper.getLevel(), pos, MobSpawnType.NATURAL);
        Zombie bare = EntityType.ZOMBIE.create(helper.getLevel());
        try {
            helper.assertTrue(egg != null && SpawnOrigin.of(egg) == Origin.SPAWN_EGG, "刷怪蛋生成的应记 spawn_egg，实际 " + SpawnOrigin.of(egg));
            helper.assertTrue(egg.getPersistentData().getString(SpawnOrigin.KEY_TYPE).equals("SPAWN_EGG"), "原始类型另存一份");
            helper.assertTrue(spawner != null && SpawnOrigin.of(spawner) == Origin.SPAWNER && SpawnOrigin.isArtificial(spawner),
                    "刷怪笼类型生成的应记 spawner，实际 " + SpawnOrigin.of(spawner));
            helper.assertTrue(natural != null && SpawnOrigin.of(natural) == Origin.NATURAL && !SpawnOrigin.isArtificial(natural),
                    "自然生成的应记 natural，实际 " + SpawnOrigin.of(natural));
            helper.assertTrue(bare != null && SpawnOrigin.of(bare) == Origin.UNKNOWN && !SpawnOrigin.isArtificial(bare),
                    "没走 finalizeSpawn 的是未知、不算人为来源");

            // 已经记过的不被再一次 finalizeSpawn 改写（别的模组再调一次也洗不白）
            ForgeEventFactory.onFinalizeSpawn(spawner, helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(pos),
                    MobSpawnType.NATURAL, null, null);
            helper.assertTrue(SpawnOrigin.of(spawner) == Origin.SPAWNER, "记过的不改写，实际 " + SpawnOrigin.of(spawner));

            // 转化：刷怪蛋的僵尸变溺尸，溺尸沿用刷怪蛋
            Drowned drowned = egg.convertTo(EntityType.DROWNED, true);
            helper.assertTrue(drowned != null, "僵尸应能转成溺尸");
            ForgeEventFactory.onLivingConvert(egg, drowned);
            helper.assertTrue(SpawnOrigin.of(drowned) == Origin.SPAWN_EGG, "转化出来的沿用原来的来源，实际 " + SpawnOrigin.of(drowned));
            drowned.discard();

            SpawnOrigin.set(bare, Origin.SUMMONED);
            helper.assertTrue(SpawnOrigin.of(bare) == Origin.SUMMONED, "set 补记");
            SpawnOrigin.set(bare, Origin.UNKNOWN);
            helper.assertTrue(SpawnOrigin.of(bare) == Origin.UNKNOWN && !bare.getPersistentData().contains(SpawnOrigin.KEY), "设成未知 = 去掉记录");
        } finally {
            for (Mob mob : new Mob[]{egg, spawner, natural}) {
                if (mob != null && !mob.isRemoved()) {
                    mob.discard();
                }
            }
        }
        helper.succeed();
    }

    /** 共用标签的成员（要先跑 runData：物品、方块标签是生成的） */
    @GameTest(template = EMPTY)
    public static void batch13SharedTags(GameTestHelper helper) {
        helper.assertTrue(entity("essentialcraft5:player_clone").is(ECTags.GROWTH_EXCLUDED)
                && entity("essentialcraft5:holo_decoy").is(ECTags.GROWTH_EXCLUDED)
                && entity("essentialcraft5:poison_fume").is(ECTags.GROWTH_EXCLUDED), "growth_excluded 少了克隆体 / 诱饵 / 毒雾");
        helper.assertFalse(EntityType.ZOMBIE.is(ECTags.GROWTH_EXCLUDED), "普通怪不在 growth_excluded 里");
        helper.assertTrue(EntityType.SHULKER.is(ECTags.CODEX_END) && EntityType.ENDER_DRAGON.is(ECTags.CODEX_END), "codex_end");
        helper.assertTrue(EntityType.WARDEN.is(ECTags.CODEX_EXPEDITION) && EntityType.EVOKER.is(ECTags.CODEX_EXPEDITION)
                && EntityType.ELDER_GUARDIAN.is(ECTags.CODEX_EXPEDITION), "codex_expedition");
        long nether = ForgeRegistries.ENTITY_TYPES.getValues().stream().filter(type -> type.is(ECTags.CODEX_NETHER)).count();
        helper.assertTrue(nether == 9 && EntityType.BLAZE.is(ECTags.CODEX_NETHER), "codex_nether 应是下界九种，实际 " + nether);
        helper.assertTrue(helper.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getTag(ECTags.UNSHIELDABLE).isPresent(),
                "伤害类型标签 unshieldable 应存在（可以是空的）");

        for (String sword : List.of("elemental_sword", "wind_elemental_sword", "chaos_fork", "frozen_mace", "holy_mace", "forged_elemental_sword")) {
            helper.assertTrue(new ItemStack(item(sword)).is(ECTags.MATRIX_ATTUNED), "matrix_attuned 少了 " + sword);
        }
        String[][] families = {{"mhs_armor", "mhs"}, {"wind_armor", "wind"}, {"void_armor", "void"},
                {"inquisitorium_armor", "inquisitorium"}, {"computer_armor", "computer"}};
        for (String[] family : families) {
            for (String part : List.of("helmet", "chestplate", "leggings", "boots")) {
                helper.assertTrue(new ItemStack(item(family[1] + "_" + part)).is(ECTags.attunementFamily(family[0])),
                        "attunement/" + family[0] + " 少了 " + family[1] + "_" + part);
            }
        }
        helper.assertTrue(new ItemStack(item("forged_elemental_sword")).is(ECTags.attunementFamily("ember_sword")), "attunement/ember_sword");
        helper.assertTrue(block("magic_plating").defaultBlockState().is(ECTags.CONDENSE_CASING)
                && block("pale_plating").defaultBlockState().is(ECTags.CONDENSE_CASING), "condense_casing 少了魔力 / 淡青护板");
        helper.succeed();
    }

    /** 13a 各配置段登记上了、默认值照 batch13.md（新建的测试世界读的是默认值） */
    @GameTest(template = EMPTY)
    public static void batch13ConfigDefaults(GameTestHelper helper) {
        helper.assertTrue(MatrixConfig.enabled() && MatrixConfig.gateHoannaPortal() == 5 && MatrixConfig.gateHolopad() == 6
                && MatrixConfig.gateCalamityBeacon() == 8, "矩阵阶开关与三道门");
        helper.assertTrue(MatrixConfig.poolPerRank() == 5000 && MatrixConfig.poolAbsorbPerSecond() == 10 && MatrixConfig.poolAbsorbReserve() == 20_000
                && MatrixConfig.codexMaxEntries() == 1024, "容量与图谱上限");
        helper.assertTrue(MatrixTrialConfig.failCooldownSeconds() == 180 && MatrixTrialConfig.spawnRadiusMin() == 4
                && MatrixTrialConfig.spawnRadiusMax() == 8 && MatrixTrialConfig.maxEchoesTotal() == 64, "试炼");
        helper.assertTrue(MatrixResonanceConfig.siphonAmount() == 2000 && MatrixResonanceConfig.siphonCooldownTicks() == 200, "虹吸");
        helper.assertTrue(Arrays.equals(AttunementConfig.thresholds(), new int[]{300, 800, 1600, 3000, 5000})
                && Arrays.equals(AttunementConfig.rankGate(), new int[]{1, 3, 5, 7, 9}), "同调门槛与阶要求");
        helper.assertTrue(AttunementConfig.budgetPerHour() == 360 && AttunementConfig.budgetCap() == 720
                && Math.abs(AttunementConfig.pvpEffectScale() - 0.5D) < 1.0E-9D, "同调预算与对玩家倍率");
        helper.assertTrue(DemonStanding.Config.regular() == 8 && DemonStanding.Config.patron() == 20 && DemonStanding.Config.blood() == 40
                && DemonStanding.Config.oath() == 70, "契约之誉四档");
        helper.succeed();
    }

    private static EntityType<?> entity(String id) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.parse(id));
        if (type == null || !ForgeRegistries.ENTITY_TYPES.getKey(type).toString().equals(id)) {
            throw new IllegalStateException("实体没注册：" + id);
        }
        return type;
    }

    private static Item item(String path) {
        Item item = ForgeRegistries.ITEMS.getValue(EssentialCraft5.id(path));
        if (item == null || ForgeRegistries.ITEMS.getKey(item) == null || !ForgeRegistries.ITEMS.getKey(item).getPath().equals(path)) {
            throw new IllegalStateException("物品没注册：" + path);
        }
        return item;
    }

    private static Block block(String path) {
        Block block = ForgeRegistries.BLOCKS.getValue(EssentialCraft5.id(path));
        if (block == null || !ForgeRegistries.BLOCKS.getKey(block).getPath().equals(path)) {
            throw new IllegalStateException("方块没注册：" + path);
        }
        return block;
    }
}
