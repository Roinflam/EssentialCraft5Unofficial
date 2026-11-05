package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.command.ECCommands;
import com.flameforge.essentialcraft5.command.HoannaHazardsCommands;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.effect.RadiationEffect;
import com.flameforge.essentialcraft5.entity.PoisonFumeEntity;
import com.flameforge.essentialcraft5.hoanna.FumeSpawns;
import com.flameforge.essentialcraft5.hoanna.HoannaHazards;
import com.flameforge.essentialcraft5.hoanna.HoannaHazardsConfig;
import com.flameforge.essentialcraft5.hoanna.Radiation;
import com.flameforge.essentialcraft5.hoanna.RadiationBaubleEvents;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEventData;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvents;
import com.flameforge.essentialcraft5.hoanna.portal.HoannaTeleporter;
import com.flameforge.essentialcraft5.item.bauble.SpecialBaubleItem;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECBaubles;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHoannaHazards;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.advancements.Advancement;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.RegistryObject;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 第六批 6a M4「辐射、天灾与毒雾」的 GameTest（batch6a.md §5.4 末尾的清单）。
 * <p>
 * 写法（核查记录 + F6a 合入更正）：GameTest 服里有真的海纳尔世界，但种子随机、没有玩家；这里的测试都摆在超平坦主世界，
 * 用 {@code ECHoanna.setTestHoannaOverride(helper.getLevel())} 把它当海纳尔（天灾数据也就存在这个主世界的 data 里），
 * <b>在同一段同步代码里设、测、停事件、清覆盖</b>（{@link #inHoanna}，finally 里清）。要真的等几 tick 的：日阵的屋顶（等光照更新）只在最后一步开覆盖；
 * 毒雾「事件结束 20 tick 内消失」要毒雾实体在世界里真的 tick、全程开着覆盖，所以单独一批 {@value #FUMES_BATCH}（一批只有它一条，
 * 不会被别的测试清掉覆盖，也不会把覆盖漏给别的测试）。
 * <p>
 * 玩家用 {@link ProtectionFrameworkTests#realPlayer}（真 ServerPlayer，不在世界里、不 tick，所以辐射、天灾不会自己跑，测试直接调结算函数）；
 * 要挨打的先空跑 61 次 {@code tick()} 过掉出生无敌（{@link #victim}）。随机数种子乘 0x9E3779B97F4A7C15L 打散。
 * 掷骰相关的测试把 {@code chance_multiplier} 覆盖成 0，免得 tick 循环里正好掷出新事件。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class HoannaHazardsTests {

    private static final String EMPTY = "empty";
    /** 毒雾在世界里真 tick、全程开着测试覆盖的那一条自己一批 */
    public static final String FUMES_BATCH = "hoanna_hazards_fumes";
    private static final long SEED_SCRAMBLE = 0x9E3779B97F4A7C15L;
    /** 查火方块、下落方块的水平半径：只查本测试结构附近（再远可能是别的测试自己放的火） */
    private static final int FIRE_SCAN_RADIUS = 4;

    private HoannaHazardsTests() {
    }

    // ------------------------------------------------------------------ 工具

    /** 把测试所在的超平坦主世界当海纳尔跑 body；结束时停掉事件、清配置覆盖和世界覆盖 */
    private static void inHoanna(GameTestHelper helper, Runnable body) {
        ServerLevel level = helper.getLevel();
        ECHoanna.setTestHoannaOverride(level);
        try {
            body.run();
        } finally {
            HoannaEvents.stop(level);
            HoannaHazardsConfig.clearTestOverrides();
            ECHoanna.clearTestOverride();
        }
    }

    /**
     * 把（全局的）日内时间临时拨到当天的第 timeOfDay 刻跑 body，finally 里放回（6a 审查 M4-1：日阵只在白天，
     * GameTest 服的日夜循环照常走，整轮跑过约 12 000 tick 后就是夜里；setDayTime 改的是全局主世界，只能在同一段同步代码里用）。
     */
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

    private static ServerPlayer player(GameTestHelper helper, Vec3 relativeFeet) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-hazard");
        Vec3 at = helper.absoluteVec(relativeFeet);
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        return player;
    }

    /** 要挨打的真玩家：过掉 60 tick 出生无敌，脚在 relativeFeet，满血 */
    private static ServerPlayer victim(GameTestHelper helper, Vec3 relativeFeet) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), "ec5-victim");
        for (int i = 0; i <= 60; i++) {
            player.tick();
        }
        Vec3 at = helper.absoluteVec(relativeFeet);
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    private static IPlayerData data(GameTestHelper helper, Player player) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            helper.fail("玩家没有 EC5 玩家数据");
        }
        return data;
    }

    /** 戴进这件饰品自己那种槽的第 0 格（不走 canEquip），并让缓存作废 */
    private static void wear(GameTestHelper helper, Player player, RegistryObject<? extends SpecialBaubleItem> item) {
        SpecialBaubleItem bauble = item.get();
        Optional<ICuriosItemHandler> inventory = CuriosApi.getCuriosInventory(player).resolve();
        if (inventory.isEmpty()) {
            helper.fail("玩家没有 Curios 饰品栏");
            return;
        }
        ICurioStacksHandler handler = inventory.get().getCurios().get(bauble.slot());
        if (handler == null || handler.getSlots() < 1) {
            helper.fail("玩家没有 Curios 槽位 " + bauble.slot());
            return;
        }
        handler.getStacks().setStackInSlot(0, new ItemStack(bauble));
        CuriosCompat.invalidate(player);
    }

    private static Holder<Biome> biome(GameTestHelper helper, net.minecraft.resources.ResourceKey<Biome> key) {
        Registry<Biome> registry = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        return registry.getHolderOrThrow(key);
    }

    private static boolean advancementDone(ServerPlayer player, ResourceLocation id) {
        Advancement advancement = player.server.getAdvancements().getAdvancement(id);
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    private static PoisonFumeEntity fume(GameTestHelper helper, Vec3 relative, MobSpawnType reason, boolean addToWorld) {
        ServerLevel level = helper.getLevel();
        PoisonFumeEntity fume = ECHoannaHazards.POISON_FUME.get().create(level);
        if (fume == null) {
            throw new IllegalStateException("建不出毒雾");
        }
        Vec3 at = helper.absoluteVec(relative);
        fume.moveTo(at.x, at.y, at.z, 0F, 0F);
        fume.finalizeSpawn(level, level.getCurrentDifficultyAt(fume.blockPosition()), reason, null, null);
        if (addToWorld) {
            level.addFreshEntity(fume);
        }
        return fume;
    }

    private static CommandSourceStack source(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        return level.getServer().createCommandSourceStack().withLevel(level)
                .withPosition(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))))
                .withPermission(ECCommands.PERMISSION_LEVEL).withSuppressedOutput();
    }

    private static boolean containsSame(WeightedRandomList<MobSpawnSettings.SpawnerData> list, MobSpawnSettings.SpawnerData data) {
        for (MobSpawnSettings.SpawnerData entry : list.unwrap()) {
            if (entry == data) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ 辐射

    /** 区块底数：9 个核对值 + 2 万个随机区块和原作的 new Random(Long.parseLong(...)).nextInt(6) 逐个相同 */
    @GameTest(template = EMPTY)
    public static void radiationChunkBaseMatchesOriginal(GameTestHelper helper) {
        int[][] checks = {{0, 0, 0}, {1, 0, 2}, {0, 1, 1}, {1, 1, 2}, {-1, 0, 2}, {5, -3, 1}, {100, 200, 2}, {-1234, 5678, 1}, {1875000, 1875000, 1}};
        for (int[] c : checks) {
            int got = Radiation.chunkBase(c[0], c[1]);
            helper.assertTrue(got == c[2], "区块 (" + c[0] + ", " + c[1] + ") 的底数应为 " + c[2] + "，实际 " + got);
        }
        Random random = new Random(20260929L * SEED_SCRAMBLE);
        for (int i = 0; i < 20_000; i++) {
            int x = random.nextInt(3_750_001) - 1_875_000;
            int z = random.nextInt(3_750_001) - 1_875_000;
            int original = new Random(Long.parseLong(Math.abs(x) * 128 + "" + Math.abs(z) * 128)).nextInt(6);
            int got = Radiation.chunkBase(x, z);
            if (got != original) {
                helper.fail("区块 (" + x + ", " + z + ") 的底数应为 " + original + "（原作算法），实际 " + got);
            }
        }
        helper.succeed();
    }

    /** 积累 = floor((底数 + 群系加成) × 易伤)；群系加成走两个标签；创造不积累但照样消退 */
    @GameTest(template = EMPTY)
    public static void radiationGainUsesBiomeAndVulnerability(GameTestHelper helper) {
        helper.assertTrue(Radiation.gain(3, 2, 0.9F) == 4, "底数 3、沙漠、易伤 0.9 应 +4，实际 " + Radiation.gain(3, 2, 0.9F));
        helper.assertTrue(Radiation.gain(5, 6, 0.5F) == 5, "底数 5、恐惧之地、易伤 0.5 应 +5，实际 " + Radiation.gain(5, 6, 0.5F));
        helper.assertTrue(Radiation.gain(5, 6, 0F) == 0, "易伤 0 应不积累");
        helper.assertTrue(Radiation.biomeBonus(biome(helper, ECHoanna.HOANNA_DESERT)) == 2, "海纳尔沙漠应 +2");
        helper.assertTrue(Radiation.biomeBonus(biome(helper, ECHoanna.DREADLANDS)) == 6, "恐惧之地应 +6");
        helper.assertTrue(Radiation.biomeBonus(biome(helper, ECHoanna.MAGIC_CORRUPTION)) == 0, "魔力腐化之地应不加");
        helper.assertTrue(Radiation.biomeBonus(biome(helper, ECHoanna.HOANNA_SEA)) == 0, "海纳尔之海应不加");

        inHoanna(helper, () -> {
            ServerPlayer player = player(helper, new Vec3(1.5D, 1D, 1.5D));
            IPlayerData data = data(helper, player);
            data.setRadiation(100);
            int base = Radiation.chunkBase(player.chunkPosition().x, player.chunkPosition().z);
            int bonus = Radiation.biomeBonus(helper.getLevel().getBiome(player.blockPosition()));
            int after = Radiation.tick(player, data, true);
            int expected = 100 + base + bonus - Radiation.DECAY_IN_HOANNA;
            helper.assertTrue(after == expected, "生存玩家在海纳尔结算一次应为 " + expected + "，实际 " + after);

            player.setGameMode(GameType.CREATIVE);
            data.setRadiation(100);
            helper.assertTrue(Radiation.tick(player, data, true) == 99, "创造模式应不积累、照样 −1，实际 " + data.getRadiation());
        });
        helper.succeed();
    }

    /** 消退：外 −5、内 −1；λ 外再 −60、内无效；μ 内 +20、创造无效 */
    @GameTest(template = EMPTY)
    public static void radiationDecayAndRings(GameTestHelper helper) {
        helper.assertTrue(Radiation.step(100, 0, false, false) == 95, "不在海纳尔应 −5");
        helper.assertTrue(Radiation.step(100, 0, true, false) == 99, "在海纳尔应 −1");
        helper.assertTrue(Radiation.step(100, 0, false, true) == 35, "λ 在外应 −65");
        helper.assertTrue(Radiation.step(100, 0, true, true) == 99, "λ 在海纳尔里应无效");
        helper.assertTrue(Radiation.step(3, 0, false, false) == 0, "不会减成负数");
        helper.assertTrue(Radiation.step(0, 0, false, false) == 0, "0 仍是 0");
        helper.assertTrue(Radiation.step(0, 4, true, false) == 3, "先加再消退：0 + 4 − 1 = 3");

        // λ（不开覆盖 = 不在海纳尔）
        ServerPlayer lambda = player(helper, new Vec3(1.5D, 1D, 1.5D));
        wear(helper, lambda, ECBaubles.LAMBDA_RING);
        IPlayerData lambdaData = data(helper, lambda);
        lambdaData.setRadiation(1000);
        helper.assertTrue(Radiation.tick(lambda, lambdaData, false) == 935, "戴 λ 在外结算一次应 1000 → 935，实际 " + lambdaData.getRadiation());

        inHoanna(helper, () -> {
            ServerPlayer mu = player(helper, new Vec3(2.5D, 1D, 2.5D));
            wear(helper, mu, ECBaubles.MU_RING);
            IPlayerData muData = data(helper, mu);
            muData.setRadiation(1000);
            int base = Radiation.chunkBase(mu.chunkPosition().x, mu.chunkPosition().z)
                    + Radiation.biomeBonus(helper.getLevel().getBiome(mu.blockPosition()));
            int expected = 1000 + base + Radiation.MU_GAIN - Radiation.DECAY_IN_HOANNA;
            helper.assertTrue(Radiation.tick(mu, muData, true) == expected, "戴 μ 在海纳尔应 +20 另算，期望 " + expected + "，实际 " + muData.getRadiation());

            mu.setGameMode(GameType.CREATIVE);
            muData.setRadiation(1000);
            helper.assertTrue(Radiation.tick(mu, muData, true) == 999, "创造模式戴 μ 也不积累，实际 " + muData.getRadiation());

            ServerPlayer lambdaInside = player(helper, new Vec3(3.5D, 1D, 3.5D));
            lambdaInside.setGameMode(GameType.CREATIVE);
            wear(helper, lambdaInside, ECBaubles.LAMBDA_RING);
            IPlayerData insideData = data(helper, lambdaInside);
            insideData.setRadiation(1000);
            helper.assertTrue(Radiation.tick(lambdaInside, insideData, true) == 999, "λ 在海纳尔里无效（创造只消退 1），实际 " + insideData.getRadiation());
        });
        helper.succeed();
    }

    /**
     * 不挂药水效果：refresh 撤掉旧版留下的辐射效果、补手册步骤；pulse 按剂量压生命上限（25 000 = 放大值 2），
     * 不到 1 万、辐射关掉都不压；旧效果实例自己不再压（isDurationEffectTick 恒 false）
     */
    @GameTest(template = EMPTY)
    public static void radiationPulsesWithoutEffect(GameTestHelper helper) {
        HoannaHazardsConfig.setTestRadiationEnabled(true);
        try {
            ServerPlayer player = victim(helper, new Vec3(1.5D, 1D, 1.5D));
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000D);
            player.setHealth(1000F);
            Radiation.set(player, 25_000);
            player.addEffect(new MobEffectInstance(ECHoannaHazards.RADIATION.get(), MobEffectInstance.INFINITE_DURATION, 2, true, false, true));
            helper.assertFalse(ECHoannaHazards.RADIATION.get().isDurationEffectTick(100, 2), "旧效果实例不应再每 tick 压上限");
            Radiation.refresh(player);
            helper.assertFalse(player.hasEffect(ECHoannaHazards.RADIATION.get()), "refresh 应撤掉旧版留下的辐射效果");
            helper.assertTrue(advancementDone(player, ECHoannaHazards.GUIDE_RADIATION), "剂量 > 0 应完成手册步骤 guide/hoanna/radiation");

            boolean hit = false;
            for (int i = 0; i < 400 && !hit; i++) {
                hit = Radiation.pulse(player);
            }
            float cap = RadiationEffect.healthCap(1000F, 2, false);
            helper.assertTrue(hit && near(player.getHealth(), cap), "放大值 2 每 tick 2/16 的几率，400 tick 内应压到上限 " + cap + "，实际 " + player.getHealth());
            helper.assertFalse(player.hasEffect(ECHoannaHazards.RADIATION.get()), "压上限不应挂药水效果");

            player.setHealth(1000F);
            Radiation.set(player, 9_999);
            for (int i = 0; i < 200; i++) {
                helper.assertFalse(Radiation.pulse(player), "不到 1 万不压");
            }
            helper.assertTrue(near(player.getHealth(), 1000F), "不到 1 万生命不变，实际 " + player.getHealth());

            Radiation.set(player, 1_000_000);
            HoannaHazardsConfig.setTestRadiationEnabled(false);
            for (int i = 0; i < 50; i++) {
                helper.assertFalse(Radiation.pulse(player), "辐射关掉不压");
            }
            HoannaHazardsConfig.setTestRadiationEnabled(true);
            player.setHealth(0F);
            helper.assertFalse(Radiation.pulse(player), "死了不判");
        } finally {
            HoannaHazardsConfig.setTestRadiationEnabled(null);
        }
        helper.succeed();
    }

    /** 上限 100 万、下限 0、加法不溢出（含 long 上下限，6a 审查 M4-2）；最高等级放得进一个字节 */
    @GameTest(template = EMPTY)
    public static void radiationClamped(GameTestHelper helper) {
        ServerPlayer player = player(helper, new Vec3(1.5D, 1D, 1.5D));
        Radiation.add(player, Radiation.MAX);
        helper.assertTrue(Radiation.add(player, Radiation.MAX) == Radiation.MAX, "加到上限以后不再增加");
        helper.assertTrue(Radiation.add(player, Integer.MAX_VALUE) == Radiation.MAX, "加 int 上限不溢出");
        helper.assertTrue(Radiation.add(player, -1_000_000_000_000L) == 0, "减很多夹到 0");
        Radiation.set(player, 5_000);
        helper.assertTrue(Radiation.add(player, Long.MAX_VALUE) == Radiation.MAX,
                "剂量 > 0 时加 Long.MAX_VALUE 应到上限（以前 long 溢出成负数、被夹成 0），实际 " + Radiation.get(player));
        helper.assertTrue(Radiation.add(player, Long.MIN_VALUE) == 0, "加 Long.MIN_VALUE 应夹到 0");
        helper.assertTrue(Radiation.sum(5_000, Long.MAX_VALUE) == Radiation.MAX && Radiation.sum(0, Long.MAX_VALUE) == Radiation.MAX
                && Radiation.sum(5_000, Long.MIN_VALUE) == 0 && Radiation.sum(5_000, -3_000L) == 2_000, "sum 应饱和、不溢出");
        helper.assertTrue(Radiation.set(player, -5) == 0, "设负数夹到 0");
        helper.assertTrue(Radiation.set(player, 5_000_000L) == Radiation.MAX, "设超过上限夹到上限");
        helper.assertTrue(Radiation.level(Radiation.MAX) == 100 && Radiation.level(Radiation.MAX) <= 127, "最高等级应是 100");
        helper.assertTrue(Radiation.level(9_999) == 0 && Radiation.level(10_000) == 1, "每 1 万一级");
        helper.succeed();
    }

    /**
     * 压上限（每级压低最大生命的 5%，合计最多一半，最低留 1）：最大 20 时和原作每级 1 点一样——等级 3 → 16；戴 δ → 18（按 放大值 / 2 + 1 级）；
     * 放大值 1 戴 δ 仍压 1 级；最大 2、等级 16 压一半 = 1；0 级不判中
     */
    @GameTest(template = EMPTY)
    public static void radiationHealthCap(GameTestHelper helper) {
        helper.assertTrue(near(RadiationEffect.healthCap(20F, 3, false), 16F), "等级 3、最大 20 上限应 16（4 级 × 5%）");
        helper.assertTrue(near(RadiationEffect.healthCap(20F, 3, true), 18F), "戴 δ：等级 3 压 3 / 2 + 1 = 2 级，上限应 18");
        helper.assertTrue(near(RadiationEffect.healthCap(1.5F, 9, false), 1F), "压一半只剩 0.75 时最低留 1");
        helper.assertTrue(RadiationEffect.capLevels(0, false) == 0 && RadiationEffect.capLevels(1, false) == 2, "0 级不压、1 级压 2 级");
        // 背包文字「上限 −N%」戴 δ 时按实际压的级数写（6a 审查 M4 漏项）：放大值 1 → 1、3 → 2、4 → 3；不能按 capLevels(amp / 2) 算（放大值 1 会变成 0）
        helper.assertTrue(RadiationEffect.capLevels(0, true) == 0 && RadiationEffect.capLevels(1, true) == 1
                && RadiationEffect.capLevels(3, true) == 2 && RadiationEffect.capLevels(4, true) == 3 && RadiationEffect.capLevels(3, false) == 4,
                "戴 δ 压的级数应是 放大值 / 2 + 1");
        helper.assertTrue("10".equals(RadiationEffect.capLossPercentText(1, false)) && "5".equals(RadiationEffect.capLossPercentText(1, true))
                        && RadiationEffect.capLossPercentText(0, false) == null && "50".equals(RadiationEffect.capLossPercentText(30, false)),
                "背包文字：辐射 II 写 10、戴 δ 写 5、不到 1 万不写、再高也只写 50，实际 " + RadiationEffect.capLossPercentText(1, false));
        RandomSource random = RandomSource.create(7L * SEED_SCRAMBLE);
        for (int i = 0; i < 1000; i++) {
            if (RadiationEffect.hits(random, 0)) {
                helper.fail("放大值 0 不应判中");
            }
        }
        helper.assertTrue(RadiationEffect.hits(random, 16), "放大值 16 必中");

        ServerPlayer plain = victim(helper, new Vec3(1.5D, 1D, 1.5D));
        RadiationEffect.capHealth(plain, 3);
        helper.assertTrue(plain.getHealth() <= 16F + 1.0E-4F, "等级 3 判中后生命应 ≤ 16，实际 " + plain.getHealth());

        ServerPlayer delta = victim(helper, new Vec3(3.5D, 1D, 3.5D));
        wear(helper, delta, ECBaubles.DELTA_RING);
        RadiationEffect.capHealth(delta, 3);
        helper.assertTrue(delta.getHealth() <= 18F + 1.0E-4F && delta.getHealth() > 16F, "戴 δ 按一半的级数算，生命应压到 18，实际 " + delta.getHealth());
        helper.assertTrue(near(delta.getHealth(), delta.getMaxHealth() * (1F - (float) RadiationEffect.capLossFraction(3, true))),
                "背包里写的「上限 −N%」应和戴 δ 时实际压的一致，实际生命 " + delta.getHealth());
        ServerPlayer deltaLow = victim(helper, new Vec3(0.5D, 1D, 3.5D));
        wear(helper, deltaLow, ECBaubles.DELTA_RING);
        RadiationEffect.capHealth(deltaLow, 1);
        helper.assertTrue(near(deltaLow.getHealth(), deltaLow.getMaxHealth() * 0.95F),
                "戴 δ、放大值 1 仍压 1 级（5%），实际生命 " + deltaLow.getHealth());

        ServerPlayer frail = victim(helper, new Vec3(2.5D, 1D, 2.5D));
        frail.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2D);
        frail.setHealth(2F);
        RadiationEffect.capHealth(frail, 16);
        helper.assertTrue(near(frail.getHealth(), 1F), "最大生命 2、等级 16 压一半应剩 1，实际 " + frail.getHealth());
        helper.succeed();
    }

    /**
     * 高血量也按比例压（2026-10-13 起）：最大生命 200 / 1000 时辐射 II 压 10%、辐射 IV 压 20%；戴 δ 时辐射 IV 只压 10%、辐射 II 压 5%；
     * 等级再高合计也只压一半（戴不戴 δ 都一样）；改比例（每级 10%、最多 30%）立刻生效；每级 0 时不压
     */
    @GameTest(template = EMPTY)
    public static void radiationCapScalesWithMaxHealth(GameTestHelper helper) {
        helper.assertTrue(near(RadiationEffect.healthCap(1000F, 1, false), 900F) && near(RadiationEffect.healthCap(200F, 1, false), 180F),
                "辐射 II：最大 1000 → 900、最大 200 → 180");
        helper.assertTrue(near(RadiationEffect.healthCap(1000F, 3, false), 800F) && near(RadiationEffect.healthCap(1000F, 3, true), 900F)
                        && near(RadiationEffect.healthCap(1000F, 1, true), 950F),
                "辐射 IV 压 20%、戴 δ 压 10%；辐射 II 戴 δ 压 5%");
        helper.assertTrue(near(RadiationEffect.healthCap(1000F, 9, false), 500F) && near(RadiationEffect.healthCap(1000F, 50, false), 500F)
                        && near(RadiationEffect.healthCap(1000F, 50, true), 500F) && near(RadiationEffect.healthCap(1000F, 100, true), 500F),
                "合计最多压一半：辐射 X 正好一半，再高、戴 δ 也只压一半");
        helper.assertTrue(near((float) RadiationEffect.capLossFraction(4, 0.05D, 0.5D), 0.2F) && RadiationEffect.capLossFraction(0, 0.05D, 0.5D) == 0D
                        && near((float) RadiationEffect.capLossFraction(30, 0.05D, 0.5D), 0.5F) && RadiationEffect.capLossFraction(3, 0D, 0.5D) == 0D,
                "纯函数：4 级 20%、0 级 0、30 级封顶 50%、每级 0 不压");

        ServerPlayer big = victim(helper, new Vec3(1.5D, 1D, 1.5D));
        big.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000D);
        big.setHealth(1000F);
        RadiationEffect.capHealth(big, 1);
        helper.assertTrue(near(big.getHealth(), 900F), "最大 1000 的玩家辐射 II 判中后应压到 900，实际 " + big.getHealth());
        RadiationEffect.capHealth(big, 30);
        helper.assertTrue(near(big.getHealth(), 500F), "等级再高只压到一半 500，实际 " + big.getHealth());
        big.setHealth(400F);
        RadiationEffect.capHealth(big, 30);
        helper.assertTrue(near(big.getHealth(), 400F), "生命本来就在线下不动，实际 " + big.getHealth());

        ServerPlayer delta = victim(helper, new Vec3(3.5D, 1D, 3.5D));
        wear(helper, delta, ECBaubles.DELTA_RING);
        delta.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200D);
        delta.setHealth(200F);
        RadiationEffect.capHealth(delta, 3);
        helper.assertTrue(near(delta.getHealth(), 180F), "最大 200 戴 δ、辐射 IV 压 2 级 = 10%，应剩 180，实际 " + delta.getHealth());

        try {
            HoannaHazardsConfig.setTestCapLoss(0.1D, 0.3D);
            helper.assertTrue(near(RadiationEffect.healthCap(1000F, 1, false), 800F) && near(RadiationEffect.healthCap(1000F, 5, false), 700F),
                    "每级 10%、最多 30%：辐射 II 压 20%，辐射 VI 封顶 30%");
            helper.assertTrue("30".equals(RadiationEffect.capLossPercentText(5, false)), "背包文字跟着改成 30");
            HoannaHazardsConfig.setTestCapLoss(0D, null);
            big.setHealth(1000F);
            RadiationEffect.capHealth(big, 9);
            helper.assertTrue(near(big.getHealth(), 1000F) && RadiationEffect.capLossPercentText(9, false) == null, "每级 0 时不压、不写");
        } finally {
            HoannaHazardsConfig.clearTestOverrides();
        }
        helper.succeed();
    }

    /** 浮点比较（相对误差 0.1%，至少按 1 算） */
    private static boolean near(float actual, float expected) {
        return Math.abs(actual - expected) < 1.0E-3F * Math.max(1F, Math.abs(expected));
    }

    /** 牛奶、清空效果（图腾、/effect clear 走的就是它）、拦效果都去不掉辐射：剂量在玩家数据里，不是药水效果 */
    @GameTest(template = EMPTY)
    public static void radiationSurvivesCleansing(GameTestHelper helper) {
        ServerPlayer player = player(helper, new Vec3(1.5D, 1D, 1.5D));
        Radiation.set(player, 20_000);
        Radiation.refresh(player);
        player.curePotionEffects(new ItemStack(Items.MILK_BUCKET));
        player.removeAllEffects();
        helper.assertTrue(Radiation.get(player) == 20_000 && Radiation.level(Radiation.get(player)) == 2,
                "喝牛奶、清空效果后剂量应还是 20 000，实际 " + Radiation.get(player));
        helper.assertFalse(player.hasEffect(ECHoannaHazards.RADIATION.get()), "辐射不是药水效果，身上不应有效果实例");
        helper.assertTrue(ECHoannaHazards.RADIATION.get().getCurativeItems().isEmpty(), "旧效果也没有解药");
        helper.succeed();
    }

    /** 假玩家读 0、写忽略；结算、毒雾都不理它 */
    @GameTest(template = EMPTY)
    public static void fakePlayerIgnoresRadiation(GameTestHelper helper) {
        FakePlayer fake = ProtectionFrameworkTests.fakePlayer(helper);
        helper.assertTrue(Radiation.get(fake) == 0, "假玩家读 0");
        helper.assertTrue(Radiation.add(fake, 500) == 0 && Radiation.get(fake) == 0, "假玩家写忽略");
        inHoanna(helper, () -> {
            HoannaHazards.tickPlayer(fake);
            // 假玩家是全局缓存的（别的测试也用），不挪它：毒雾先判「是不是假玩家」，站在哪都不毒
            PoisonFumeEntity fume = fume(helper, new Vec3(1.5D, 1D, 1.5D), MobSpawnType.COMMAND, false);
            helper.assertTrue(fume.affectPlayers(List.of(fake)) == 0, "毒雾不毒假玩家");
            helper.assertFalse(Radiation.pulse(fake) || fake.hasEffect(ECHoannaHazards.RADIATION.get()), "假玩家不判、不挂效果");
        });
        helper.succeed();
    }

    /** 辐射关掉：不再积累（区块、毒雾），剂量照常消退，不再压上限；毒雾的中毒照旧 */
    @GameTest(template = EMPTY)
    public static void radiationDisabledStopsGain(GameTestHelper helper) {
        inHoanna(helper, () -> {
            HoannaHazardsConfig.setTestRadiationEnabled(false);
            ServerPlayer player = victim(helper, new Vec3(1.5D, 1D, 1.5D));
            IPlayerData data = data(helper, player);
            data.setRadiation(20_000);
            HoannaHazards.tickPlayer(player);
            helper.assertTrue(data.getRadiation() == 19_999, "关掉后在海纳尔只消退，实际 " + data.getRadiation());
            helper.assertFalse(Radiation.pulse(player), "关掉后不再压上限");
            PoisonFumeEntity fume = fume(helper, new Vec3(1.5D, 1D, 1.5D), MobSpawnType.COMMAND, false);
            fume.affectPlayers(List.of(player));
            helper.assertTrue(data.getRadiation() == 19_999, "关掉后毒雾不加辐射");
            helper.assertTrue(player.hasEffect(MobEffects.POISON), "毒雾的中毒照旧");
        });
        helper.succeed();
    }

    /**
     * 剂量跟着<b>真的</b>换维度走（6a 集成修的 PlayerDataProvider：Forge 在 remove(CHANGED_DIMENSION) 里作废 capability，revive 只恢复分发器）：
     * 真 ServerPlayer 用 {@code changeDimension} + 海纳尔之门的传送器进 GameTest 服里真的海纳尔世界、再回主世界，玩家数据一直是同一份、剂量不丢。
     * SystemsFrameworkTests 只做了 remove + revive（mock 玩家不真换维度）。全程同步，结束时把玩家从所在世界移除（不留在 players 表里）。
     */
    @GameTest(template = EMPTY)
    public static void radiationSurvivesRealDimensionChange(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerLevel hoanna = level.getServer().getLevel(ECHoanna.LEVEL);
        if (hoanna == null) {
            helper.fail("GameTest 服里应有海纳尔世界");
            return;
        }
        ServerPlayer traveller = player(helper, new Vec3(1.5D, 1D, 1.5D));
        IPlayerData before = data(helper, traveller);
        before.setRadiation(12_345);
        // 真换维度进海纳尔要过矩阵阶的兜底门（EntityTravelToDimensionEvent），这里测的是辐射，给九阶
        before.setMatrixRank(9);
        Vec3 home = traveller.position();
        try {
            Entity there = traveller.changeDimension(hoanna, new HoannaTeleporter(new PortalInfo(new Vec3(home.x, 200D, home.z), Vec3.ZERO, 0F, 0F)));
            helper.assertTrue(there == traveller && traveller.level() == hoanna, "应换到海纳尔，实际 " + traveller.level().dimension().location());
            IPlayerData inHoanna = PlayerDataEvents.get(traveller);
            helper.assertTrue(inHoanna == before && inHoanna.getRadiation() == 12_345,
                    "进海纳尔后应还是同一份玩家数据、剂量 12 345，实际 " + (inHoanna == null ? "null" : inHoanna.getRadiation()));
            Entity back = traveller.changeDimension(level, new HoannaTeleporter(new PortalInfo(home, Vec3.ZERO, 0F, 0F)));
            helper.assertTrue(back == traveller && traveller.level() == level, "应回到主世界");
            IPlayerData home2 = PlayerDataEvents.get(traveller);
            helper.assertTrue(home2 == before && home2.getRadiation() == 12_345,
                    "回主世界后应还是同一份玩家数据、剂量 12 345，实际 " + (home2 == null ? "null" : home2.getRadiation()));
            helper.assertTrue(Radiation.add(traveller, 5) == 12_350, "换过两次维度后剂量还写得进去");
        } finally {
            traveller.serverLevel().removePlayerImmediately(traveller, Entity.RemovalReason.DISCARDED);
        }
        helper.assertFalse(hoanna.players().contains(traveller) || level.players().contains(traveller), "测试玩家不应留在世界的玩家表里");
        helper.succeed();
    }

    // ------------------------------------------------------------------ ξ 腰带

    /**
     * 打怪：同一只怪只有一个固定 UUID 的修饰符，每下累减「真正扣掉的生命」，最大生命最低 1；修饰符不存盘（第十批）。
     * 压上限在 tick 末结算（{@link RadiationBaubleEvents#flushMaxHealth}），这里直接调。僵尸的护甲先清零，扣掉的生命就是 4 点。
     */
    @GameTest(template = EMPTY)
    public static void xiSingleModifierOnMobs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer attacker = victim(helper, new Vec3(0.5D, 1D, 0.5D));
        wear(helper, attacker, ECBaubles.XI_BELT);
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) {
            helper.fail("建不出僵尸");
            return;
        }
        Vec3 at = helper.absoluteVec(new Vec3(2.5D, 1D, 2.5D));
        zombie.moveTo(at.x, at.y, at.z, 0F, 0F);
        zombie.setNoAi(true);
        zombie.getAttribute(Attributes.ARMOR).setBaseValue(0D);
        level.addFreshEntity(zombie);
        try {
            RadiationBaubleEvents.flushMaxHealth();
            for (int i = 0; i < 3; i++) {
                zombie.invulnerableTime = 0;
                helper.assertTrue(zombie.hurt(level.damageSources().playerAttack(attacker), 4F), "第 " + (i + 1) + " 下应打中");
            }
            helper.assertTrue(RadiationBaubleEvents.flushMaxHealth() == 3, "三下都应在 tick 末压上限");
            AttributeInstance maxHealth = zombie.getAttribute(Attributes.MAX_HEALTH);
            int count = 0;
            for (AttributeModifier modifier : maxHealth.getModifiers()) {
                if (modifier.getId().equals(RadiationBaubleEvents.XI_MODIFIER)) {
                    count++;
                    helper.assertTrue(Math.abs(modifier.getAmount() + 12D) < 1.0E-6D, "三下 4 点应累计 −12，实际 " + modifier.getAmount());
                }
            }
            helper.assertTrue(count == 1, "只应有一个 ξ 修饰符，实际 " + count);
            helper.assertTrue(maxHealth.getModifiers().size() == 1, "不应有别的修饰符，实际 " + maxHealth.getModifiers().size());
            helper.assertTrue(Math.abs(zombie.getMaxHealth() - 8F) < 1.0E-4F, "最大生命应降到 8，实际 " + zombie.getMaxHealth());
            helper.assertTrue(zombie.getHealth() <= zombie.getMaxHealth(), "生命不应超过新上限");
            helper.assertTrue(Radiation.get(attacker) == 0, "打怪不给自己加辐射");
            helper.assertFalse(maxHealth.save().contains("Modifiers"), "ξ 的修饰符不应存盘（区块卸载、重进世界后恢复）");

            RadiationBaubleEvents.lowerMaxHealth(zombie, 1000F);
            helper.assertTrue(zombie.getMaxHealth() >= 1F && zombie.getMaxHealth() < 1.0001F, "最大生命最低留 1，实际 " + zombie.getMaxHealth());
            helper.assertTrue(RadiationBaubleEvents.xiModifierAmount(-12D, 100F, 20D) == -19D, "下限：20 点的怪最多降 19");
            helper.assertTrue(RadiationBaubleEvents.xiModifierAmount(0D, 4F, 20D) == -4D, "第一下 −4");
            helper.assertTrue(RadiationBaubleEvents.xiModifierAmount(0D, 4F, 1D) == 0D, "只剩 1 点时不再降");
        } finally {
            zombie.discard();
        }
        helper.succeed();
    }

    /**
     * 打怪按真正扣掉的生命压（第十批）：别的监听器在 LivingDamageEvent 里把这一下夹小（全息投影的单次封顶就是这样），只压夹过以后的数；
     * 有主人的生物（驯服的狼）不压。
     */
    @GameTest(template = EMPTY)
    public static void xiLowersOnlyHealthReallyLost(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer attacker = victim(helper, new Vec3(0.5D, 1D, 0.5D));
        wear(helper, attacker, ECBaubles.XI_BELT);
        Zombie capped = EntityType.ZOMBIE.create(level);
        Wolf pet = EntityType.WOLF.create(level);
        if (capped == null || pet == null) {
            helper.fail("建不出僵尸 / 狼");
            return;
        }
        Vec3 at = helper.absoluteVec(new Vec3(2.5D, 1D, 2.5D));
        capped.moveTo(at.x, at.y, at.z, 0F, 0F);
        capped.setNoAi(true);
        capped.getAttribute(Attributes.ARMOR).setBaseValue(0D);
        level.addFreshEntity(capped);
        Vec3 petAt = helper.absoluteVec(new Vec3(3.5D, 1D, 0.5D));
        pet.moveTo(petAt.x, petAt.y, petAt.z, 0F, 0F);
        pet.setNoAi(true);
        pet.setTame(true);
        pet.setOwnerUUID(UUID.randomUUID());
        level.addFreshEntity(pet);
        // 夹伤害的监听器必须排在 onXiDamage 后面（ξ 先按 6 点记下、再被夹成 1），反过来的话「按 dealt 压」和「按真掉的生命压」都是 1，测不出区别
        boolean[] queuedBeforeClamp = {false};
        Consumer<LivingDamageEvent> clamp = event -> {
            if (event.getEntity() == capped) {
                queuedBeforeClamp[0] = RadiationBaubleEvents.hasLowering(capped);
                event.setAmount(Math.min(event.getAmount(), 1F));
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingDamageEvent.class, clamp);
        try {
            RadiationBaubleEvents.flushMaxHealth();
            float full = capped.getMaxHealth();
            helper.assertTrue(capped.hurt(level.damageSources().playerAttack(attacker), 6F), "应打中");
            helper.assertTrue(queuedBeforeClamp[0], "夹伤害时 ξ 应已记下这一下（不然测的不是这条路径：监听器顺序变了？）");
            RadiationBaubleEvents.flushMaxHealth();
            helper.assertTrue(Math.abs(capped.getHealth() - (full - 1F)) < 1.0E-4F, "被夹成 1 点：应只掉 1 点，实际 " + capped.getHealth());
            helper.assertTrue(Math.abs(capped.getMaxHealth() - (full - 1F)) < 1.0E-4F,
                    "上限应只压真正掉的 1 点（不是 6 点），实际 " + capped.getMaxHealth());

            helper.assertFalse(RadiationBaubleEvents.canLowerMaxHealth(pet), "宠物（有主人的狼）不压");
            float petFull = pet.getMaxHealth();
            helper.assertTrue(pet.hurt(level.damageSources().playerAttack(attacker), 2F), "狼应挨打");
            RadiationBaubleEvents.flushMaxHealth();
            helper.assertTrue(pet.getMaxHealth() == petFull && pet.getAttribute(Attributes.MAX_HEALTH).getModifier(RadiationBaubleEvents.XI_MODIFIER) == null,
                    "别人的狼的最大生命不应被压，实际 " + pet.getMaxHealth());
        } finally {
            MinecraftForge.EVENT_BUS.unregister(clamp);
            capped.discard();
            pet.discard();
        }
        helper.succeed();
    }

    /**
     * 宠物和坐骑不压（审查 B4）：装了鞍的骆驼（{@code isTamed()} 恒为真却不记主人）、装了鞍的猪、被玩家骑着的普通马、驯服了却没记主人的狼都不压；
     * 对照：没装鞍、没人骑的野马照压，起了名字的僵尸照压（模组首领常有自定义名字，不能按 isSpared 排除）
     */
    @GameTest(template = EMPTY)
    public static void xiSparesPetsAndMounts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Camel camel = EntityType.CAMEL.create(level);
        Pig pig = EntityType.PIG.create(level);
        Horse ridden = EntityType.HORSE.create(level);
        Horse wild = EntityType.HORSE.create(level);
        Wolf ownerless = EntityType.WOLF.create(level);
        Zombie named = EntityType.ZOMBIE.create(level);
        if (camel == null || pig == null || ridden == null || wild == null || ownerless == null || named == null) {
            helper.fail("建不出测试用的生物");
            return;
        }
        Vec3 at = helper.absoluteVec(new Vec3(2.5D, 1D, 2.5D));
        ridden.moveTo(at.x, at.y, at.z, 0F, 0F);
        camel.equipSaddle(null);
        pig.equipSaddle(null);
        ownerless.setTame(true);
        named.setCustomName(net.minecraft.network.chat.Component.literal("Boss"));
        Player rider = helper.makeMockPlayer();
        rider.moveTo(at.x, at.y, at.z, 0F, 0F);
        helper.assertTrue(rider.startRiding(ridden, true), "测试前提：模拟玩家骑上了马");
        try {
            helper.assertTrue(camel.getOwnerUUID() == null && camel.isSaddled(), "测试前提：骆驼装了鞍、没记主人");
            helper.assertFalse(RadiationBaubleEvents.canLowerMaxHealth(camel), "装了鞍的骆驼不压");
            helper.assertFalse(RadiationBaubleEvents.canLowerMaxHealth(pig), "装了鞍的猪不压");
            helper.assertFalse(RadiationBaubleEvents.canLowerMaxHealth(ridden), "被玩家骑着的马不压");
            helper.assertFalse(RadiationBaubleEvents.canLowerMaxHealth(ownerless), "驯服了却没记主人的狼也不压");
            helper.assertTrue(RadiationBaubleEvents.canLowerMaxHealth(wild), "对照：没装鞍、没人骑的野马照压");
            helper.assertTrue(RadiationBaubleEvents.canLowerMaxHealth(named), "对照：起了名字的僵尸照压");
            helper.assertTrue(RadiationBaubleEvents.lowerMaxHealth(pig, 4F) == 0D && pig.getAttribute(Attributes.MAX_HEALTH).getModifier(RadiationBaubleEvents.XI_MODIFIER) == null,
                    "直接调压上限也不碰装了鞍的猪");
        } finally {
            rider.stopRiding();
        }
        helper.succeed();
    }

    /**
     * 举盾整下挡掉的不算（审查 B1）：按混合端的顺序手动发 {@code LivingHurtEvent} → {@code ShieldBlockEvent} → {@code LivingDamageEvent(0)}
     * （混合端在 LivingHurtEvent 之后才算举盾，挡满照样发 LivingDamageEvent(0)）：被打的人不吃剂量、记录照样删掉；戴 ξ 的人自己举盾挡下也不吃。
     * 对照：没有格挡记录的 {@code LivingDamageEvent(0)}（插件放行、伤害被盔甲吃完）照常加
     */
    @GameTest(template = EMPTY)
    public static void xiIgnoresShieldBlockedHits(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer attacker = victim(helper, new Vec3(0.5D, 1D, 0.5D));
        wear(helper, attacker, ECBaubles.XI_BELT);
        ServerPlayer target = victim(helper, new Vec3(2.5D, 1D, 2.5D));
        DamageSource shielded = level.damageSources().playerAttack(attacker);
        MinecraftForge.EVENT_BUS.post(new LivingHurtEvent(target, shielded, 4F));
        helper.assertTrue(RadiationBaubleEvents.hasPending(target, shielded), "测试前提：LivingHurtEvent 里 ξ 记了账");
        MinecraftForge.EVENT_BUS.post(new ShieldBlockEvent(target, shielded, 4F));
        MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(target, shielded, 0F));
        helper.assertTrue(Radiation.get(target) == 0, "被盾牌整下挡掉的一下不应给对方剂量，实际 " + Radiation.get(target));
        helper.assertFalse(RadiationBaubleEvents.hasPending(target, shielded), "这一下的记录应删掉");

        DamageSource counter = level.damageSources().playerAttack(target);
        MinecraftForge.EVENT_BUS.post(new LivingHurtEvent(attacker, counter, 4F));
        MinecraftForge.EVENT_BUS.post(new ShieldBlockEvent(attacker, counter, 4F));
        MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(attacker, counter, 0F));
        helper.assertTrue(Radiation.get(attacker) == 0, "戴 ξ 的人自己举盾挡下的一下不应吃剂量，实际 " + Radiation.get(attacker));

        DamageSource landed = level.damageSources().playerAttack(attacker);
        MinecraftForge.EVENT_BUS.post(new LivingHurtEvent(target, landed, 4F));
        MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(target, landed, 0F));
        long expected = HoannaHazardsConfig.radiationEnabled()
                ? RadiationBaubleEvents.ceilingAdd(0, RadiationBaubleEvents.targetDose(4F), ECBaubles.Limits.xiDoseCeiling()) : 0L;
        helper.assertTrue(Radiation.get(target) == expected, "对照：没被盾挡的 LivingDamageEvent(0) 照常加 " + expected + "，实际 " + Radiation.get(target));
        helper.succeed();
    }

    /**
     * 原伤害的 LivingHurtEvent 最后被取消的一下不算：混合端上别的模组排在 ξ（LivingHurtEvent 最低优先级）后面取消时，伤害按 0 照样往下走，
     * Bukkit 事件放行后照发 {@code LivingDamageEvent(0)}；纯 Forge 取消以后不再发，所以按混合端的顺序手动发
     * {@code LivingHurtEvent}（运行时加的最低优先级监听器排在 ξ 之后取消）→ {@code LivingDamageEvent(0)}：被打的人不吃剂量、戴 ξ 的人自己挨的这一下也不吃，记录照样删掉。
     * 对照：同样的顺序不取消时照常加。辐射用测试覆盖值打开（关着时断言没有意义）
     */
    @GameTest(template = EMPTY)
    public static void xiIgnoresCanceledHurt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer attacker = victim(helper, new Vec3(0.5D, 1D, 0.5D));
        wear(helper, attacker, ECBaubles.XI_BELT);
        ServerPlayer target = victim(helper, new Vec3(2.5D, 1D, 2.5D));
        DamageSource hit = level.damageSources().playerAttack(attacker);
        DamageSource counter = level.damageSources().playerAttack(target);
        Consumer<LivingHurtEvent> lateCancel = event -> {
            if (event.getSource() == hit || event.getSource() == counter) {
                event.setCanceled(true);
            }
        };
        HoannaHazardsConfig.setTestRadiationEnabled(true);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingHurtEvent.class, lateCancel);
        try {
            LivingHurtEvent hurt = new LivingHurtEvent(target, hit, 4F);
            MinecraftForge.EVENT_BUS.post(hurt);
            helper.assertTrue(hurt.isCanceled() && RadiationBaubleEvents.hasPending(target, hit), "测试前提：ξ 先记了账、之后才被取消");
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(target, hit, 0F));
            helper.assertTrue(Radiation.get(target) == 0, "LivingHurtEvent 被取消的一下不应给对方剂量，实际 " + Radiation.get(target));
            helper.assertFalse(RadiationBaubleEvents.hasPending(target, hit), "这一下的记录应删掉");

            LivingHurtEvent self = new LivingHurtEvent(attacker, counter, 4F);
            MinecraftForge.EVENT_BUS.post(self);
            helper.assertTrue(self.isCanceled() && RadiationBaubleEvents.hasPending(attacker, counter), "测试前提：戴 ξ 的人挨的这一下也记了账");
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(attacker, counter, 0F));
            helper.assertTrue(Radiation.get(attacker) == 0, "戴 ξ 的人挨的、LivingHurtEvent 被取消的一下不应吃剂量，实际 " + Radiation.get(attacker));

            // 对照：同样的顺序、不取消（取消用的监听器只认上面两个伤害源对象）
            DamageSource landed = level.damageSources().playerAttack(attacker);
            MinecraftForge.EVENT_BUS.post(new LivingHurtEvent(target, landed, 4F));
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(target, landed, 0F));
            long expected = RadiationBaubleEvents.ceilingAdd(0, RadiationBaubleEvents.targetDose(4F), ECBaubles.Limits.xiDoseCeiling());
            helper.assertTrue(Radiation.get(target) == expected, "对照：没被取消的一下照常加 " + expected + "，实际 " + Radiation.get(target));
        } finally {
            MinecraftForge.EVENT_BUS.unregister(lateCancel);
            HoannaHazardsConfig.setTestRadiationEnabled(null);
        }
        helper.succeed();
    }

    /**
     * 原伤害最后被改成 0（没取消）的一下同样不算：混合端在 {@code onLivingHurt} 之后只对 &lt; 0 提前返回，0 照样发 Bukkit 事件、放行后照发
     * {@code LivingDamageEvent(0)}；纯 Forge 在 {@code actuallyHurt} 的 {@code <= 0} 处返回、不再发。按混合端的顺序手动发
     * {@code LivingHurtEvent}（运行时加的最低优先级监听器排在 ξ 之后把伤害改成 0）→ {@code LivingDamageEvent(0)}：被打的人不吃剂量、
     * 戴 ξ 的人自己挨的这一下也不吃，记录照样删掉。对照：同样的顺序、伤害没被改成 0 时照常加。辐射用测试覆盖值打开
     */
    @GameTest(template = EMPTY)
    public static void xiIgnoresZeroedHurt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer attacker = victim(helper, new Vec3(0.5D, 1D, 0.5D));
        wear(helper, attacker, ECBaubles.XI_BELT);
        ServerPlayer target = victim(helper, new Vec3(2.5D, 1D, 2.5D));
        DamageSource hit = level.damageSources().playerAttack(attacker);
        DamageSource counter = level.damageSources().playerAttack(target);
        Consumer<LivingHurtEvent> lateZero = event -> {
            if (event.getSource() == hit || event.getSource() == counter) {
                event.setAmount(0F);
            }
        };
        HoannaHazardsConfig.setTestRadiationEnabled(true);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingHurtEvent.class, lateZero);
        try {
            LivingHurtEvent hurt = new LivingHurtEvent(target, hit, 4F);
            MinecraftForge.EVENT_BUS.post(hurt);
            helper.assertTrue(!hurt.isCanceled() && hurt.getAmount() == 0F && RadiationBaubleEvents.hasPending(target, hit),
                    "测试前提：ξ 先记了账、之后伤害才被改成 0（没取消）");
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(target, hit, 0F));
            helper.assertTrue(Radiation.get(target) == 0, "伤害被改成 0 的一下不应给对方剂量，实际 " + Radiation.get(target));
            helper.assertFalse(RadiationBaubleEvents.hasPending(target, hit), "这一下的记录应删掉");

            LivingHurtEvent self = new LivingHurtEvent(attacker, counter, 4F);
            MinecraftForge.EVENT_BUS.post(self);
            helper.assertTrue(!self.isCanceled() && self.getAmount() == 0F && RadiationBaubleEvents.hasPending(attacker, counter),
                    "测试前提：戴 ξ 的人挨的这一下也记了账");
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(attacker, counter, 0F));
            helper.assertTrue(Radiation.get(attacker) == 0, "戴 ξ 的人挨的、伤害被改成 0 的一下不应吃剂量，实际 " + Radiation.get(attacker));

            // 对照：同样的顺序、伤害没被改成 0（改伤害的监听器只认上面两个伤害源对象）
            DamageSource landed = level.damageSources().playerAttack(attacker);
            MinecraftForge.EVENT_BUS.post(new LivingHurtEvent(target, landed, 4F));
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(target, landed, 0F));
            long expected = RadiationBaubleEvents.ceilingAdd(0, RadiationBaubleEvents.targetDose(4F), ECBaubles.Limits.xiDoseCeiling());
            helper.assertTrue(Radiation.get(target) == expected, "对照：伤害没被改成 0 的一下照常加 " + expected + "，实际 " + Radiation.get(target));
        } finally {
            MinecraftForge.EVENT_BUS.unregister(lateZero);
            HoannaHazardsConfig.setTestRadiationEnabled(null);
        }
        helper.succeed();
    }

    /** 打玩家：攻击被拦（LivingAttackEvent 取消）或目标无敌时不加；落地一次 4 点（4 × 5000 = 2 万）→ 每下封顶 +5000（xi_dose_per_hit） */
    @GameTest(template = EMPTY)
    public static void xiRadiatesPlayersOnlyWhenDamageLands(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ServerPlayer attacker = victim(helper, new Vec3(0.5D, 1D, 0.5D));
        wear(helper, attacker, ECBaubles.XI_BELT);
        ServerPlayer claimed = victim(helper, new Vec3(2.5D, 1D, 0.5D));
        ServerPlayer fresh = player(helper, new Vec3(0.5D, 1D, 2.5D));
        ServerPlayer target = victim(helper, new Vec3(2.5D, 1D, 2.5D));
        boolean pvp = server.isPvpAllowed();
        Consumer<LivingAttackEvent> claim = event -> {
            if (event.getEntity() == claimed) {
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingAttackEvent.class, claim);
        try {
            server.setPvpAllowed(true);
            helper.assertFalse(claimed.hurt(level.damageSources().playerAttack(attacker), 4F), "被领地拦下的攻击应不生效");
            helper.assertTrue(Radiation.get(claimed) == 0, "被拦下的攻击不给辐射，实际 " + Radiation.get(claimed));
            helper.assertFalse(fresh.hurt(level.damageSources().playerAttack(attacker), 4F), "出生无敌的玩家应打不动");
            helper.assertTrue(Radiation.get(fresh) == 0, "无敌时不给辐射，实际 " + Radiation.get(fresh));
            helper.assertTrue(target.hurt(level.damageSources().playerAttack(attacker), 4F), "正常攻击应生效");
            long expected = RadiationBaubleEvents.ceilingAdd(0, RadiationBaubleEvents.targetDose(4F), ECBaubles.Limits.xiDoseCeiling());
            helper.assertTrue(Radiation.get(target) == expected, "落地 4 点应按每下封顶加 " + expected + "，实际 " + Radiation.get(target));
            helper.assertTrue(Radiation.get(attacker) == 0, "攻击者自己不加辐射");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(claim);
            server.setPvpAllowed(pvp);
        }
        helper.succeed();
    }

    /**
     * 打玩家：每一下的剂量封顶（{@code xi_dose_per_hit}，默认 5000），而且只把对方推到 {@code xi_dose_ceiling}（默认 15 000）为止（第十批）。
     * 默认数值：1.5 点照 × 5000 是 7 500、封到 5000；再打 3 下 6 点，第一、二下各 +5000 到 15 000，第三下到顶不再加。
     * 15 000 = 辐射 II（放大值 1），生命上限最多压掉 2 点；离开海纳尔每秒散 5，约 1000 秒回到 1 万以下（不再压上限）。
     * 第六批（每下一级、不封总量）是 37 500、放大值 3；原作不封顶是 97 500、放大值 9。已经比上限高的人不加也不减。攻击本身照常掉血（ξ 不改伤害值）。
     */
    @GameTest(template = EMPTY)
    public static void xiTargetDoseCappedPerHit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        int perHit = ECBaubles.Limits.xiDosePerHit();
        int ceiling = ECBaubles.Limits.xiDoseCeiling();
        helper.assertTrue(RadiationBaubleEvents.targetDose(0F) == 0L && RadiationBaubleEvents.targetDose(0.5F) == Math.min(2_500L, perHit)
                        && RadiationBaubleEvents.targetDose(6F) == Math.min(30_000L, perHit) && RadiationBaubleEvents.targetDose(Float.MAX_VALUE) == perHit,
                "targetDose = min(伤害 × 5000, xi_dose_per_hit)");
        helper.assertTrue(RadiationBaubleEvents.ceilingAdd(0, 5_000L, 15_000) == 5_000L && RadiationBaubleEvents.ceilingAdd(12_000, 5_000L, 15_000) == 3_000L
                        && RadiationBaubleEvents.ceilingAdd(15_000, 5_000L, 15_000) == 0L && RadiationBaubleEvents.ceilingAdd(40_000, 5_000L, 15_000) == 0L
                        && RadiationBaubleEvents.ceilingAdd(0, 0L, 15_000) == 0L && RadiationBaubleEvents.ceilingAdd(0, 5_000L, 0) == 0L,
                "ceilingAdd = max(0, min(加的, 上限 − 现在))");
        ServerPlayer attacker = victim(helper, new Vec3(0.5D, 1D, 0.5D));
        wear(helper, attacker, ECBaubles.XI_BELT);
        ServerPlayer target = victim(helper, new Vec3(2.5D, 1D, 2.5D));
        ServerPlayer dosed = victim(helper, new Vec3(4.5D, 1D, 2.5D));
        boolean pvp = server.isPvpAllowed();
        try {
            server.setPvpAllowed(true);
            float full = target.getMaxHealth();
            long expected = RadiationBaubleEvents.ceilingAdd(0, RadiationBaubleEvents.targetDose(1.5F), ceiling);
            helper.assertTrue(target.hurt(level.damageSources().playerAttack(attacker), 1.5F), "第 1 下应打中");
            helper.assertTrue(Math.abs(target.getHealth() - (full - 1.5F)) < 1.0E-4F, "ξ 的攻击照常掉血：应剩 " + (full - 1.5F) + "，实际 " + target.getHealth());
            helper.assertTrue(Radiation.get(target) == expected, "1.5 点应 +" + expected + "，实际 " + Radiation.get(target));
            for (int i = 0; i < 3; i++) {
                target.invulnerableTime = 0;
                target.setHealth(full);
                expected += RadiationBaubleEvents.ceilingAdd((int) expected, RadiationBaubleEvents.targetDose(6F), ceiling);
                helper.assertTrue(target.hurt(level.damageSources().playerAttack(attacker), 6F), "第 " + (i + 2) + " 下应打中");
                helper.assertTrue(Radiation.get(target) == expected, "第 " + (i + 2) + " 下后剂量应是 " + expected + "，实际 " + Radiation.get(target));
            }
            int dose = Radiation.get(target);
            helper.assertTrue(dose <= Math.max(ceiling, 0), "不应超过上限 " + ceiling + "，实际 " + dose);
            if (perHit == ECBaubles.Limits.DEFAULT_XI_DOSE_PER_HIT && ceiling == ECBaubles.Limits.DEFAULT_XI_DOSE_CEILING) {
                helper.assertTrue(dose == 15_000 && Radiation.level(dose) == 1 && near(RadiationEffect.healthCap(full, Radiation.level(dose), false), full * 0.9F),
                        "默认数值：四下以后 15 000 = 辐射 II，生命上限最多压低 10%，实际 " + dose);
            }
            helper.assertTrue(Radiation.get(attacker) == 0, "攻击者自己不加辐射");

            Radiation.set(dosed, 40_000);
            helper.assertTrue(dosed.hurt(level.damageSources().playerAttack(attacker), 6F), "应打中");
            helper.assertTrue(Radiation.get(dosed) == Math.max(40_000, 0), "已经比上限高的人不加也不减，实际 " + Radiation.get(dosed));
        } finally {
            server.setPvpAllowed(pvp);
        }
        helper.succeed();
    }

    /** 戴 ξ 的人自己挨打：每点 +1000（没到 xi_dose_ceiling 时）；没戴的不加 */
    @GameTest(template = EMPTY)
    public static void xiWearerIrradiatedOnHurt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer wearer = victim(helper, new Vec3(1.5D, 1D, 1.5D));
        wear(helper, wearer, ECBaubles.XI_BELT);
        ServerPlayer plain = victim(helper, new Vec3(3.5D, 1D, 3.5D));
        helper.assertTrue(wearer.hurt(level.damageSources().generic(), 3F), "应挨打");
        long self = RadiationBaubleEvents.ceilingAdd(0, 3L * RadiationBaubleEvents.PER_DAMAGE_SELF, ECBaubles.Limits.xiDoseCeiling());
        helper.assertTrue(Radiation.get(wearer) == self, "挨 3 点应 +" + self + "，实际 " + Radiation.get(wearer));
        helper.assertTrue(plain.hurt(level.damageSources().generic(), 3F), "应挨打");
        helper.assertTrue(Radiation.get(plain) == 0, "没戴 ξ 不加辐射");
        helper.succeed();
    }

    /**
     * ξ 碰上 /kill 和秒杀（6a 审查 M4-2）：戴 ξ 的人被 /kill（剂量 0 和 5000 各一次）剂量都不变；
     * 秒杀按「这一下最多能打掉的生命」（生命 + 伤害吸收）算：挨 10 亿点 generic 只 +20 × 1000、再按 xi_dose_ceiling 截，
     * 被戴 ξ 的人用 Float.MAX_VALUE 打中只 +xi_dose_per_hit（同样按上限截）。
     * 以前 /kill 会把剂量加满 100 万（原剂量 &gt; 0 时反而 long 溢出清零），死亡不清零，复活后生命上限只剩 1 点。
     */
    @GameTest(template = EMPTY)
    public static void xiIgnoresKillAndCapsOverkill(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        try {
            ServerPlayer clean = victim(helper, new Vec3(0.5D, 1D, 0.5D));
            wear(helper, clean, ECBaubles.XI_BELT);
            clean.kill();
            helper.assertTrue(clean.isDeadOrDying(), "/kill 应打死");
            helper.assertTrue(Radiation.get(clean) == 0, "戴 ξ 被 /kill（原剂量 0）不应加剂量，实际 " + Radiation.get(clean));

            ServerPlayer dosed = victim(helper, new Vec3(2.5D, 1D, 0.5D));
            wear(helper, dosed, ECBaubles.XI_BELT);
            Radiation.set(dosed, 5_000);
            dosed.kill();
            helper.assertTrue(Radiation.get(dosed) == 5_000, "戴 ξ 被 /kill（原剂量 5000）剂量应不变，实际 " + Radiation.get(dosed));

            ServerPlayer wearer = victim(helper, new Vec3(0.5D, 1D, 2.5D));
            wear(helper, wearer, ECBaubles.XI_BELT);
            Radiation.set(wearer, 5_000);
            float wearerHealth = wearer.getHealth() + wearer.getAbsorptionAmount();
            wearer.hurt(level.damageSources().generic(), 1.0E9F);
            int expectedSelf = 5_000 + (int) RadiationBaubleEvents.ceilingAdd(5_000, (long) (wearerHealth * RadiationBaubleEvents.PER_DAMAGE_SELF),
                    ECBaubles.Limits.xiDoseCeiling());
            helper.assertTrue(Radiation.get(wearer) == expectedSelf, "戴 ξ 挨秒杀应只按剩下的生命算、再按上限截（" + expectedSelf + "），实际 " + Radiation.get(wearer));

            ServerPlayer attacker = victim(helper, new Vec3(4.5D, 1D, 4.5D));
            wear(helper, attacker, ECBaubles.XI_BELT);
            ServerPlayer target = victim(helper, new Vec3(2.5D, 1D, 2.5D));
            Radiation.set(target, 5_000);
            boolean pvp = server.isPvpAllowed();
            try {
                server.setPvpAllowed(true);
                target.hurt(level.damageSources().playerAttack(attacker), Float.MAX_VALUE);
            } finally {
                server.setPvpAllowed(pvp);
            }
            long expectedTarget = 5_000 + RadiationBaubleEvents.ceilingAdd(5_000, RadiationBaubleEvents.targetDose(Float.MAX_VALUE), ECBaubles.Limits.xiDoseCeiling());
            helper.assertTrue(Radiation.get(target) == expectedTarget, "被戴 ξ 的人秒杀应只加每下封顶的量（" + expectedTarget + "），实际 " + Radiation.get(target));

            helper.assertTrue(RadiationBaubleEvents.countedDamage(4F, 20F, 0F) == 4F && RadiationBaubleEvents.countedDamage(1.0E9F, 20F, 4F) == 24F
                    && RadiationBaubleEvents.countedDamage(5F, 0F, 0F) == 0F && RadiationBaubleEvents.countedDamage(Float.MAX_VALUE, 20F, 0F) == 20F,
                    "countedDamage = min(伤害, 生命 + 伤害吸收)");
        } finally {
            // 死的人会掉出 ξ 腰带（Curios），清掉免得留在测试区
            helper.killAllEntitiesOfClass(ItemEntity.class);
        }
        helper.succeed();
    }

    /**
     * ξ 的「Hurt 已记账、Damage 没到」（6a 审查 M4-5）：混合端的 Bukkit {@code EntityDamageByEntityEvent} 夹在 LivingHurtEvent 和 LivingDamageEvent 之间，
     * 这里用运行时注册的 LOWEST 监听器模拟（排在模组启动时注册的 onXiHurt 后面）：取消后不掉血、不给辐射；
     * 同一 tick、同一个伤害源对象再打另一人照加（4 点按每下封顶；按目标 + 伤害源配对，不串）。
     */
    @GameTest(template = EMPTY)
    public static void xiSkipsHitsBlockedAfterHurtEvent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ServerPlayer attacker = victim(helper, new Vec3(0.5D, 1D, 0.5D));
        wear(helper, attacker, ECBaubles.XI_BELT);
        ServerPlayer blocked = victim(helper, new Vec3(2.5D, 1D, 0.5D));
        ServerPlayer open = victim(helper, new Vec3(2.5D, 1D, 2.5D));
        DamageSource source = level.damageSources().playerAttack(attacker);
        boolean[] pendingWhenBlocked = {false};
        Consumer<LivingHurtEvent> lateBlock = event -> {
            if (event.getEntity() == blocked) {
                pendingWhenBlocked[0] = RadiationBaubleEvents.hasPending(blocked, event.getSource());
                event.setCanceled(true);
            }
        };
        boolean pvp = server.isPvpAllowed();
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingHurtEvent.class, lateBlock);
        try {
            server.setPvpAllowed(true);
            blocked.hurt(source, 4F);
            helper.assertTrue(pendingWhenBlocked[0], "被拦的那一刻 onXiHurt 应已记账（不然测的不是这条路径：监听器顺序变了？）");
            helper.assertTrue(blocked.getHealth() == blocked.getMaxHealth(), "Hurt 之后被拦应不掉血，实际 " + blocked.getHealth());
            helper.assertTrue(Radiation.get(blocked) == 0, "Hurt 之后被拦不给辐射，实际 " + Radiation.get(blocked));
            helper.assertTrue(open.hurt(source, 4F), "同一 tick 打另一人应生效");
            long expected = RadiationBaubleEvents.ceilingAdd(0, RadiationBaubleEvents.targetDose(4F), ECBaubles.Limits.xiDoseCeiling());
            helper.assertTrue(Radiation.get(open) == expected, "另一人落地 4 点应 +" + expected + "（每下封顶），实际 " + Radiation.get(open));
            helper.assertTrue(Radiation.get(blocked) == 0, "别人的伤害不应串到被拦的人身上，实际 " + Radiation.get(blocked));
        } finally {
            MinecraftForge.EVENT_BUS.unregister(lateBlock);
            server.setPvpAllowed(pvp);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ 天灾

    /** 掷骰：200 万次常规掷骰日阵 0 次、其余三种在期望附近；黎明日阵 7.5% ± 0.4%；倍数 0、关掉都不发生 */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void eventRollOdds(GameTestHelper helper) {
        RandomSource random = RandomSource.create(20260929L * SEED_SCRAMBLE);
        int[] counts = new int[HoannaEvent.values().length];
        int rolls = 2_000_000;
        for (int i = 0; i < rolls; i++) {
            counts[HoannaEvents.rollRegular(random, 1D, e -> true).id()]++;
        }
        helper.assertTrue(counts[HoannaEvent.SUN_ARRAY.id()] == 0, "常规掷骰不应出日阵，实际 " + counts[HoannaEvent.SUN_ARRAY.id()]);
        checkRange(helper, "毒雾", counts[HoannaEvent.FUMES.id()], rolls / 4D * HoannaEvent.FUMES.chance());
        checkRange(helper, "黑暗", counts[HoannaEvent.DARKNESS.id()], rolls / 4D * HoannaEvent.DARKNESS.chance());
        checkRange(helper, "地震", counts[HoannaEvent.EARTHQUAKE.id()], rolls / 4D * HoannaEvent.EARTHQUAKE.chance());

        int dawnRolls = 100_000;
        int sun = 0;
        for (int i = 0; i < dawnRolls; i++) {
            HoannaEvent rolled = HoannaEvents.rollDawn(random, 1D, e -> true);
            if (rolled == HoannaEvent.SUN_ARRAY) {
                sun++;
            } else if (!rolled.isNone()) {
                helper.fail("黎明那一次只会出日阵，实际 " + rolled);
            }
        }
        double share = sun / (double) dawnRolls;
        helper.assertTrue(Math.abs(share - 0.075D) <= 0.004D, "黎明日阵应约 7.5%，实际 " + share);

        for (int i = 0; i < 10_000; i++) {
            if (!HoannaEvents.rollDawn(random, 0D, e -> true).isNone() || !HoannaEvents.rollRegular(random, 1D, e -> false).isNone()
                    || !HoannaEvents.rollDawn(random, 1D, e -> e != HoannaEvent.SUN_ARRAY).isNone()) {
                helper.fail("倍数 0 或关掉的天灾不应发生");
            }
        }
        helper.succeed();
    }

    private static void checkRange(GameTestHelper helper, String name, int count, double expected) {
        double sigma = Math.sqrt(expected);
        helper.assertTrue(Math.abs(count - expected) <= 5D * sigma, name + "应约 " + expected + " 次，实际 " + count);
    }

    /** 倒计时到 0 结束；存档往返不丢；坏存档当没有事件 */
    @GameTest(template = EMPTY)
    public static void eventCountdownAndSaveRoundTrip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        inHoanna(helper, () -> {
            HoannaHazardsConfig.setTestChanceMultiplier(0D);
            HoannaEvents.start(level, HoannaEvent.EARTHQUAKE, 100);
            HoannaEventData data = HoannaEventData.get(level);
            helper.assertTrue(data.event() == HoannaEvent.EARTHQUAKE && data.total() == 100 && data.remaining() == 100, "应开始 100 tick 的地震");
            for (int i = 0; i < 40; i++) {
                HoannaEvents.tick(level, true);
            }
            helper.assertTrue(data.remaining() == 60, "40 tick 后应剩 60，实际 " + data.remaining());

            HoannaEventData copy = HoannaEventData.load(data.save(new CompoundTag()));
            helper.assertTrue(copy.event() == data.event() && copy.remaining() == data.remaining() && copy.total() == data.total()
                    && copy.lastDay() == data.lastDay(), "存档往返应一致");

            for (int i = 0; i < 60; i++) {
                HoannaEvents.tick(level, true);
            }
            helper.assertTrue(HoannaEvents.current(level) == HoannaEvent.NONE, "倒计时到 0 应结束");

            CompoundTag bad = new CompoundTag();
            bad.putInt("event", 99);
            bad.putInt("remaining", 50);
            bad.putInt("total", 100);
            helper.assertTrue(!HoannaEventData.load(bad).active(), "未知编号应当没有事件");
            CompoundTag empty = new CompoundTag();
            empty.putInt("event", HoannaEvent.FUMES.id());
            helper.assertTrue(!HoannaEventData.load(empty).active(), "时长 0 应当没有事件");
        });
        helper.succeed();
    }

    /** 没人时不计时；有人时每 tick −1；不是海纳尔时 current 是 NONE */
    @GameTest(template = EMPTY)
    public static void eventPausedWithoutPlayers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        inHoanna(helper, () -> {
            HoannaHazardsConfig.setTestChanceMultiplier(0D);
            HoannaEvents.start(level, HoannaEvent.DARKNESS, 500);
            for (int i = 0; i < 100; i++) {
                HoannaEvents.tick(level, false);
            }
            helper.assertTrue(HoannaEventData.get(level).remaining() == 500, "没人时不应计时");
            for (int i = 0; i < 100; i++) {
                HoannaEvents.tick(level, true);
            }
            helper.assertTrue(HoannaEventData.get(level).remaining() == 400, "有人时每 tick −1");
            helper.assertTrue(HoannaEvents.isActive(level, HoannaEvent.DARKNESS), "黑暗应在进行");
            ECHoanna.clearTestOverride();
            helper.assertTrue(HoannaEvents.current(level) == HoannaEvent.NONE, "不是海纳尔的世界应当没有天灾");
            ECHoanna.setTestHoannaOverride(level);
        });
        helper.succeed();
    }

    /** 指令：start / query / stop（没有天灾时 stop 不报错）、未知事件报错；radiation set / add / clear 立刻刷新效果 */
    @GameTest(template = EMPTY)
    public static void eventCommands(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CommandSourceStack source = source(helper);
        inHoanna(helper, () -> {
            try {
                helper.assertTrue(HoannaHazardsCommands.start(source, "fumes", 200) == 1, "start 应返回 1");
                helper.assertTrue(HoannaEvents.current(level) == HoannaEvent.FUMES && HoannaEventData.get(level).total() == 200, "应开始 200 tick 的毒雾");
                helper.assertTrue(HoannaHazardsCommands.query(source) == HoannaEvent.FUMES.id(), "query 应返回毒雾的编号");
                helper.assertTrue(HoannaHazardsCommands.start(source, "DARKNESS", 0) == 1, "大写也认、换成黑暗");
                helper.assertTrue(HoannaEvents.current(level) == HoannaEvent.DARKNESS && HoannaEventData.get(level).total() >= 24_000, "不写时长用默认时长");
                helper.assertTrue(HoannaHazardsCommands.stop(source) == 1, "stop 应停掉");
                helper.assertTrue(HoannaHazardsCommands.stop(source) == 0, "没有天灾时 stop 应返回 0、不报错");
                helper.assertTrue(HoannaHazardsCommands.query(source) == 0, "没有天灾时 query 返回 0");
                boolean threw = false;
                try {
                    HoannaHazardsCommands.start(source, "meteor", 100);
                } catch (CommandSyntaxException e) {
                    threw = true;
                }
                helper.assertTrue(threw, "未知天灾应报错");

                ServerPlayer player = player(helper, new Vec3(1.5D, 1D, 1.5D));
                HoannaHazardsCommands.setRadiation(source, List.of(player), 12_345);
                helper.assertTrue(Radiation.get(player) == 12_345 && Radiation.level(Radiation.get(player)) == 1
                        && !player.hasEffect(ECHoannaHazards.RADIATION.get()), "set 后剂量 12 345、等级 1，不挂药水效果");
                HoannaHazardsCommands.addRadiation(source, List.of(player), -345);
                helper.assertTrue(Radiation.get(player) == 12_000, "add −345 后应 12 000");
                helper.assertTrue(HoannaHazardsCommands.getRadiation(source, List.of(player)) == 12_000, "get 返回剂量");
                HoannaHazardsCommands.setRadiation(source, List.of(player), 0);
                helper.assertTrue(Radiation.get(player) == 0 && !player.hasEffect(ECHoannaHazards.RADIATION.get()), "clear 后剂量 0、没有效果");
            } catch (CommandSyntaxException e) {
                helper.fail("指令不应报错：" + e.getMessage());
            }
        });
        helper.succeed();
    }

    /** 进海纳尔时正在事件中：发状态、打手册勾 */
    @GameTest(template = EMPTY)
    public static void eventSyncAwardsGuideStep(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        inHoanna(helper, () -> {
            ServerPlayer player = player(helper, new Vec3(1.5D, 1D, 1.5D));
            HoannaEvents.syncTo(player, true);
            helper.assertFalse(advancementDone(player, HoannaEvents.GUIDE_EVENT), "没有天灾时不打勾");
            HoannaEvents.start(level, HoannaEvent.EARTHQUAKE, 300);
            HoannaEvents.syncTo(player, true);
            helper.assertTrue(advancementDone(player, HoannaEvents.GUIDE_EVENT), "遇上天灾应完成 guide/hoanna/event");
        });
        helper.succeed();
    }

    /**
     * 日阵：露天生存每次掉 1 点并着火；不透光的屋顶下没事；μ、创造、旁观没事；抗火 0 伤害不着火；攻击被拦（保护模组）时不掉血不着火；
     * 结构附近没有火方块、没有下落的方块实体。屋顶要等光照更新（天光判断），所以最后一步才开覆盖。
     * 日阵只在白天（M4-1），GameTest 服的日夜循环照常走，所以最后一步把日内时间临时拨到上午。
     */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void sunArrayBurnsOnlyExposed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos roof = new BlockPos(3, 3, 3);
        helper.setBlock(roof, Blocks.STONE);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertFalse(level.canSeeSky(helper.absolutePos(roof)), "等屋顶的光照更新"))
                .thenExecute(() -> atTimeOfDay(helper, 1_000L, () -> inHoanna(helper, () -> {
                    helper.assertTrue(HoannaEvents.start(level, HoannaEvent.SUN_ARRAY, 12_000), "上午应能开日阵");
                    ServerPlayer exposed = victim(helper, new Vec3(1.5D, 1D, 1.5D));
                    helper.assertTrue(HoannaHazards.exposedToSky(level, exposed), "露天的玩家应看得见天空");
                    helper.assertTrue(HoannaHazards.sunArray(exposed, level), "露天生存应被灼烧");
                    helper.assertTrue(exposed.getHealth() == exposed.getMaxHealth() - 1F, "应掉 1 点，实际 " + exposed.getHealth());
                    helper.assertTrue(exposed.getRemainingFireTicks() > 0, "应着火");

                    ServerPlayer roofed = victim(helper, new Vec3(3.5D, 1D, 3.5D));
                    helper.assertFalse(HoannaHazards.sunArray(roofed, level), "屋顶下不应被灼烧");
                    helper.assertTrue(roofed.getHealth() == roofed.getMaxHealth() && roofed.getRemainingFireTicks() <= 0, "屋顶下不掉血不着火");

                    ServerPlayer attuned = victim(helper, new Vec3(1.5D, 1D, 3.5D));
                    wear(helper, attuned, ECBaubles.MU_RING);
                    helper.assertFalse(HoannaHazards.sunArray(attuned, level), "戴 μ 不被灼烧");

                    ServerPlayer creative = victim(helper, new Vec3(3.5D, 1D, 1.5D));
                    creative.setGameMode(GameType.CREATIVE);
                    helper.assertFalse(HoannaHazards.sunArray(creative, level), "创造不被灼烧");
                    ServerPlayer spectator = victim(helper, new Vec3(2.5D, 1D, 2.5D));
                    spectator.setGameMode(GameType.SPECTATOR);
                    helper.assertFalse(HoannaHazards.sunArray(spectator, level), "旁观不被灼烧");

                    ServerPlayer fireproof = victim(helper, new Vec3(0.5D, 1D, 0.5D));
                    fireproof.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 1200));
                    helper.assertFalse(HoannaHazards.sunArray(fireproof, level), "抗火应 0 伤害");
                    helper.assertTrue(fireproof.getHealth() == fireproof.getMaxHealth() && fireproof.getRemainingFireTicks() <= 0, "抗火不掉血不着火");

                    ServerPlayer protectedPlayer = victim(helper, new Vec3(0.5D, 1D, 2.5D));
                    Consumer<LivingAttackEvent> guard = event -> {
                        if (event.getEntity() == protectedPlayer) {
                            event.setCanceled(true);
                        }
                    };
                    MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingAttackEvent.class, guard);
                    try {
                        helper.assertFalse(HoannaHazards.sunArray(protectedPlayer, level), "被保护模组拦下时不算打中");
                        helper.assertTrue(protectedPlayer.getHealth() == protectedPlayer.getMaxHealth()
                                && protectedPlayer.getRemainingFireTicks() <= 0, "被拦下时不掉血不着火");
                    } finally {
                        MinecraftForge.EVENT_BUS.unregister(guard);
                    }

                    ServerPlayer viaTick = victim(helper, new Vec3(4.5D, 1D, 0.5D));
                    HoannaHazards.tickPlayer(viaTick);
                    helper.assertTrue(viaTick.getHealth() < viaTick.getMaxHealth(), "每秒结算里日阵应灼烧露天的人");

                    assertNoFireAround(helper, FIRE_SCAN_RADIUS);
                })))
                .thenSucceed();
    }

    /**
     * 日阵只在白天（6a 审查 M4-1）：上午 10000 刻开的截到黄昏（2000 tick）；没人、停住的时候天黑了也散；夜里开不了（指令报错，正在的别的天灾不受影响）；
     * 停住的日阵剩余截到离黄昏的时间（进来的人拿到的是截短后的）；有人在时天黑那一 tick 就结束，之后露天的人不被烧；
     * 指令在白天开、时长超过剩下的白天时截到黄昏。全程在同一段同步代码里拨日内时间，finally 放回。
     */
    @GameTest(template = EMPTY)
    public static void sunArrayEndsAtDusk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CommandSourceStack source = source(helper);
        helper.assertTrue(HoannaEvents.daylightLeft(0L) == 12_000 && HoannaEvents.daylightLeft(11_999L) == 1 && HoannaEvents.daylightLeft(12_000L) <= 0
                && HoannaEvents.daylightLeft(23_999L) <= 0 && HoannaEvents.daylightLeft(24_000L + 500L) == 11_500 && HoannaEvents.daylightLeft(-1L) <= 0,
                "daylightLeft：一天的 0~11999 刻是白天");
        atTimeOfDay(helper, 10_000L, () -> inHoanna(helper, () -> {
            HoannaHazardsConfig.setTestChanceMultiplier(0D);
            long dayStart = level.getDayTime() - 10_000L;
            helper.assertTrue(HoannaEvents.start(level, HoannaEvent.SUN_ARRAY, 12_000) && HoannaEventData.get(level).total() == 2_000,
                    "10000 刻开的日阵应截到黄昏（2000 tick），实际 " + HoannaEventData.get(level).total());

            level.setDayTime(dayStart + 18_000L);
            HoannaEvents.tick(level, false);
            helper.assertTrue(HoannaEvents.current(level) == HoannaEvent.NONE, "没人、停住的时候天黑了也应散，实际 " + HoannaEvents.current(level));

            HoannaEvents.start(level, HoannaEvent.EARTHQUAKE, 300);
            helper.assertFalse(HoannaEvents.start(level, HoannaEvent.SUN_ARRAY, 12_000), "夜里不应开始日阵");
            boolean threw = false;
            try {
                HoannaHazardsCommands.start(source, "sun_array", 0);
            } catch (CommandSyntaxException e) {
                threw = true;
            }
            helper.assertTrue(threw, "夜里用指令开日阵应报错");
            helper.assertTrue(HoannaEvents.current(level) == HoannaEvent.EARTHQUAKE, "夜里开日阵失败时，正在的地震不该被停");
            HoannaEvents.stop(level);

            level.setDayTime(dayStart + 1_000L);
            helper.assertTrue(HoannaEvents.start(level, HoannaEvent.SUN_ARRAY, 0) && HoannaEventData.get(level).total() == 11_000,
                    "1000 刻开的默认时长应截成 11 000");
            level.setDayTime(dayStart + 9_000L);
            HoannaEvents.tick(level, false);
            HoannaEventData data = HoannaEventData.get(level);
            helper.assertTrue(data.event() == HoannaEvent.SUN_ARRAY && data.remaining() == 3_000,
                    "停住到 9000 刻，剩余应截成离黄昏的 3000，实际 " + data.event() + " / " + data.remaining());

            level.setDayTime(dayStart + 12_000L);
            HoannaEvents.tick(level, true);
            helper.assertTrue(HoannaEvents.current(level) == HoannaEvent.NONE, "有人在时天黑那一 tick 就应结束");
            ServerPlayer exposed = victim(helper, new Vec3(1.5D, 1D, 1.5D));
            HoannaHazards.tickPlayer(exposed);
            helper.assertTrue(exposed.getHealth() == exposed.getMaxHealth() && exposed.getRemainingFireTicks() <= 0, "天黑后露天的人不应被烧");

            level.setDayTime(dayStart + 11_000L);
            try {
                helper.assertTrue(HoannaHazardsCommands.start(source, "sun_array", 6_000) == 1, "白天用指令开日阵应成功");
            } catch (CommandSyntaxException e) {
                helper.fail("白天开日阵不应报错：" + e.getMessage());
            }
            helper.assertTrue(HoannaEventData.get(level).total() == 1_000, "指令写 6000、离黄昏只剩 1000 时应截成 1000，实际 " + HoannaEventData.get(level).total());
        }));
        helper.succeed();
    }

    private static void assertNoFireAround(GameTestHelper helper, int radius) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(2, 1, 2));
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -2, -radius), center.offset(radius, 8, radius))) {
            if (level.getBlockState(pos).getBlock() instanceof BaseFireBlock) {
                helper.fail("附近不应有火方块：" + pos);
            }
        }
        AABB box = new AABB(center).inflate(radius, 64D, radius);
        helper.assertTrue(level.getEntitiesOfClass(FallingBlockEntity.class, box).isEmpty(), "不应生成下落的方块（原作日阵落火）");
    }

    /** 地震：1000 次冲量都在上限内、强震约 15%；戴 μ 不出强震；push 关掉速度不变；腾空 / 创造不推 */
    @GameTest(template = EMPTY)
    public static void earthquakePushBounded(GameTestHelper helper) {
        RandomSource random = RandomSource.create(11L * SEED_SCRAMBLE);
        int strong = 0;
        for (int i = 0; i < 1000; i++) {
            HoannaHazards.Quake quake = HoannaHazards.quakeImpulse(random, true);
            helper.assertTrue(quake.y() >= 0D && quake.y() <= HoannaHazards.STRONG_UP_MAX + 1.0E-9D, "竖直应 ≤ 0.35，实际 " + quake.y());
            helper.assertTrue(quake.horizontal() <= HoannaHazards.STRONG_MAX + 1.0E-9D, "水平应 ≤ 0.6，实际 " + quake.horizontal());
            if (quake.strong()) {
                strong++;
                helper.assertTrue(quake.y() >= HoannaHazards.STRONG_UP_MIN - 1.0E-9D, "强震竖直应 ≥ 0.25");
            } else {
                helper.assertTrue(quake.y() == 0D && Math.abs(quake.x()) <= HoannaHazards.SMALL_PUSH && Math.abs(quake.z()) <= HoannaHazards.SMALL_PUSH,
                        "小踉跄每轴 ≤ 0.15、不往上");
            }
        }
        helper.assertTrue(strong >= 100 && strong <= 200, "强震应约 15%（1000 次约 150），实际 " + strong);
        for (int i = 0; i < 1000; i++) {
            if (HoannaHazards.quakeImpulse(random, false).strong()) {
                helper.fail("不许强震时不应出强震");
            }
        }

        ServerPlayer attuned = victim(helper, new Vec3(1.5D, 1D, 1.5D));
        wear(helper, attuned, ECBaubles.MU_RING);
        attuned.setOnGround(true);
        for (int i = 0; i < 200; i++) {
            HoannaHazards.Quake quake = HoannaHazards.earthquake(attuned, random);
            helper.assertTrue(quake != null && !quake.strong(), "戴 μ 不应出强震");
            attuned.setDeltaMovement(Vec3.ZERO);
        }

        ServerPlayer plain = victim(helper, new Vec3(3.5D, 1D, 3.5D));
        plain.setOnGround(true);
        plain.setDeltaMovement(Vec3.ZERO);
        HoannaHazardsConfig.setTestEarthquakePush(false);
        try {
            HoannaHazards.Quake quake = HoannaHazards.earthquake(plain, random);
            helper.assertTrue(quake != null && plain.getDeltaMovement().equals(Vec3.ZERO), "push 关掉时速度不应变");
        } finally {
            HoannaHazardsConfig.clearTestOverrides();
        }
        plain.hurtMarked = false;
        HoannaHazards.Quake pushed = HoannaHazards.earthquake(plain, random);
        helper.assertTrue(pushed != null && plain.hurtMarked, "推了以后应下发速度");
        helper.assertTrue(plain.getDeltaMovement().distanceTo(new Vec3(pushed.x(), pushed.y(), pushed.z())) < 1.0E-9D, "速度应加上这次冲量");

        plain.setOnGround(false);
        helper.assertTrue(HoannaHazards.earthquake(plain, random) == null, "腾空时不推");
        plain.setOnGround(true);
        plain.setGameMode(GameType.CREATIVE);
        helper.assertTrue(HoannaHazards.earthquake(plain, random) == null, "创造不推");
        helper.succeed();
    }

    /** 四种天灾各跑 200 tick：草、木头、树叶不变，结构附近没有火、没有下落的方块 */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void hoannaEventsNeverTouchBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos grass = new BlockPos(1, 1, 1);
        BlockPos log = new BlockPos(1, 1, 3);
        BlockPos leaves = new BlockPos(3, 1, 1);
        helper.setBlock(grass, Blocks.GRASS_BLOCK);
        helper.setBlock(log, Blocks.OAK_LOG);
        helper.setBlock(leaves, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
        BlockState grassState = helper.getBlockState(grass);
        BlockState logState = helper.getBlockState(log);
        BlockState leavesState = helper.getBlockState(leaves);
        // 日阵只在白天（M4-1）：拨到上午，200 tick 的循环不会碰到黄昏
        atTimeOfDay(helper, 1_000L, () -> inHoanna(helper, () -> {
            HoannaHazardsConfig.setTestChanceMultiplier(0D);
            ServerPlayer player = victim(helper, new Vec3(3.5D, 1D, 3.5D));
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200D);
            player.setHealth(200F);
            player.setOnGround(true);
            PoisonFumeEntity fume = fume(helper, new Vec3(3.5D, 1D, 3.5D), MobSpawnType.COMMAND, false);
            for (HoannaEvent event : HoannaEvent.rollable()) {
                helper.assertTrue(HoannaEvents.start(level, event, 1000), event + " 应开始");
                for (int t = 1; t <= 200; t++) {
                    HoannaEvents.tick(level, true);
                    if (t % 20 == 0) {
                        HoannaHazards.tickPlayer(player);
                    }
                    if (event == HoannaEvent.FUMES && t % PoisonFumeEntity.AFFECT_INTERVAL == 0) {
                        fume.affectPlayers(List.of(player));
                    }
                }
                helper.assertTrue(helper.getBlockState(grass) == grassState, event + "：草方块不应变");
                helper.assertTrue(helper.getBlockState(log) == logState, event + "：原木不应变");
                helper.assertTrue(helper.getBlockState(leaves) == leavesState, event + "：树叶不应变");
                assertNoFireAround(helper, FIRE_SCAN_RADIUS);
                HoannaEvents.stop(level);
            }
        }));
        helper.succeed();
    }

    // ------------------------------------------------------------------ 毒雾

    /** 刷怪表：只在海纳尔的毒雾期间加、两次拿到同一实例；被动类别清空；主世界（没开覆盖）永远不含；上限 0 不加 */
    @GameTest(template = EMPTY)
    public static void fumeSpawnListOnlyDuringEvent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        MobSpawnSettings.SpawnerData spawner = FumeSpawns.spawnerData();
        WeightedRandomList<MobSpawnSettings.SpawnerData> cows = WeightedRandomList.create(new MobSpawnSettings.SpawnerData(EntityType.COW, 10, 1, 2));
        inHoanna(helper, () -> {
            helper.assertFalse(containsSame(ForgeEventFactory.getPotentialSpawns(level, MobCategory.MONSTER, pos, WeightedRandomList.create()), spawner),
                    "没有毒雾事件时不应加毒雾");
            HoannaEvents.start(level, HoannaEvent.FUMES, 1000);
            WeightedRandomList<MobSpawnSettings.SpawnerData> first = ForgeEventFactory.getPotentialSpawns(level, MobCategory.MONSTER, pos, WeightedRandomList.create());
            WeightedRandomList<MobSpawnSettings.SpawnerData> second = ForgeEventFactory.getPotentialSpawns(level, MobCategory.MONSTER, pos, WeightedRandomList.create());
            helper.assertTrue(containsSame(first, spawner) && containsSame(second, spawner), "毒雾期间两次都应拿到同一个刷怪条目实例");
            helper.assertTrue(spawner.getWeight().asInt() == FumeSpawns.WEIGHT && spawner.minCount == 8 && spawner.maxCount == 16, "权重 100、每群 8~16");
            helper.assertTrue(ForgeEventFactory.getPotentialSpawns(level, MobCategory.CREATURE, pos, cows).isEmpty(), "海纳尔里被动生物类别应清空");
            helper.assertFalse(containsSame(ForgeEventFactory.getPotentialSpawns(level, MobCategory.CREATURE, pos, cows), spawner), "毒雾只进怪物类别");

            HoannaHazardsConfig.setTestFumesMaxNearby(0);
            helper.assertFalse(containsSame(ForgeEventFactory.getPotentialSpawns(level, MobCategory.MONSTER, pos, WeightedRandomList.create()), spawner),
                    "上限 0 时不应加毒雾");
            HoannaHazardsConfig.setTestFumesMaxNearby(null);

            ECHoanna.clearTestOverride();
            try {
                helper.assertFalse(containsSame(ForgeEventFactory.getPotentialSpawns(level, MobCategory.MONSTER, pos, WeightedRandomList.create()), spawner),
                        "主世界永远不加毒雾");
                helper.assertFalse(ForgeEventFactory.getPotentialSpawns(level, MobCategory.CREATURE, pos, cows).isEmpty(), "主世界的被动生物不受影响");
            } finally {
                ECHoanna.setTestHoannaOverride(level);
            }
            HoannaEvents.stop(level);
            helper.assertFalse(containsSame(ForgeEventFactory.getPotentialSpawns(level, MobCategory.MONSTER, pos, WeightedRandomList.create()), spawner),
                    "毒雾结束后不应再加");
        });
        helper.succeed();
    }

    /**
     * {@code fumes_max_nearby} = 0（不刷毒雾）时毒雾天灾不会自己发生（6a 审查 M4-6：以前照样掷骰开始，只剩红字和提示条、一团雾都没有）；
     * 指令照样能开；上限 &gt; 0 时照常能掷出来。
     */
    @GameTest(template = EMPTY)
    public static void fumesMaxZeroStopsNaturalFumes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CommandSourceStack source = source(helper);
        inHoanna(helper, () -> {
            HoannaHazardsConfig.setTestChanceMultiplier(0D);
            HoannaHazardsConfig.setTestFumesMaxNearby(0);
            helper.assertFalse(HoannaHazardsConfig.eventEnabled(HoannaEvent.FUMES), "上限 0 时毒雾天灾不应自己发生");
            RandomSource random = RandomSource.create(17L * SEED_SCRAMBLE);
            for (int i = 0; i < 20_000; i++) {
                if (HoannaEvents.rollRegular(random, HoannaHazardsConfig.MAX_CHANCE_MULTIPLIER, HoannaHazardsConfig::eventEnabled) == HoannaEvent.FUMES) {
                    helper.fail("上限 0 时掷骰不应出毒雾");
                }
            }
            try {
                helper.assertTrue(HoannaHazardsCommands.start(source, "fumes", 200) == 1 && HoannaEvents.current(level) == HoannaEvent.FUMES,
                        "上限 0 时指令照样能开毒雾");
            } catch (CommandSyntaxException e) {
                helper.fail("指令不应报错：" + e.getMessage());
            }
            HoannaEvents.stop(level);
            HoannaHazardsConfig.setTestFumesMaxNearby(1);
            boolean rolled = false;
            for (int i = 0; i < 200_000 && !rolled; i++) {   // 每次约 0.044%，期望约 87 次
                rolled = HoannaEvents.rollRegular(random, HoannaHazardsConfig.MAX_CHANCE_MULTIPLIER,
                        e -> e == HoannaEvent.FUMES && HoannaHazardsConfig.eventEnabled(e)) == HoannaEvent.FUMES;
            }
            // 配置文件里 fumes_enabled 被关掉时掷不出来，这里只在它开着时要求掷得出
            helper.assertTrue(rolled || !HoannaHazardsConfig.eventEnabled(HoannaEvent.FUMES), "上限 > 0 时毒雾应能掷出来");
        });
        helper.succeed();
    }

    /** 64 格内已有 24 只就拒绝自然生成、23 只放行；刷怪蛋不受限；事件结束后自然生成全拒 */
    @GameTest(template = EMPTY)
    public static void fumeDensityCap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        EntityType<PoisonFumeEntity> type = ECHoannaHazards.POISON_FUME.get();
        RandomSource random = RandomSource.create(3L * SEED_SCRAMBLE);
        List<PoisonFumeEntity> added = new ArrayList<>();
        inHoanna(helper, () -> {
            try {
                HoannaEvents.start(level, HoannaEvent.FUMES, 1000);
                int max = HoannaHazardsConfig.fumesMaxNearby();
                int base = FumeSpawns.nearbyCount(level, pos);
                while (FumeSpawns.nearbyCount(level, pos) < max - 1) {
                    added.add(fume(helper, new Vec3(1.5D + added.size() % 3, 2D, 1.5D + added.size() / 3 % 3), MobSpawnType.COMMAND, true));
                    if (added.size() > max + base) {
                        helper.fail("加了毒雾计数却不涨");
                    }
                }
                helper.assertTrue(FumeSpawns.checkSpawn(type, level, MobSpawnType.NATURAL, pos, random), (max - 1) + " 只时应放行");
                added.add(fume(helper, new Vec3(2.5D, 2D, 2.5D), MobSpawnType.COMMAND, true));
                helper.assertTrue(FumeSpawns.nearbyCount(level, pos) == max, "同一 tick 新加的应立刻算进去");
                helper.assertFalse(FumeSpawns.checkSpawn(type, level, MobSpawnType.NATURAL, pos, random), max + " 只时应拒绝");
                helper.assertTrue(FumeSpawns.checkSpawn(type, level, MobSpawnType.SPAWN_EGG, pos, random), "刷怪蛋不受上限限制");
                added.remove(added.size() - 1).discard();
                helper.assertTrue(FumeSpawns.nearbyCount(level, pos) == max - 1, "离开世界的应不再算");
                HoannaEvents.stop(level);
                helper.assertFalse(FumeSpawns.checkSpawn(type, level, MobSpawnType.NATURAL, pos, random), "毒雾结束后自然生成全拒");
            } finally {
                added.forEach(PoisonFumeEntity::discard);
            }
        });
        helper.succeed();
    }

    /** 范围里的人：+100 辐射（不乘易伤）、中毒 II 200 tick；剩余 ≥ 100 不刷新、&lt; 100 刷新；μ、创造、范围外不受影响 */
    @GameTest(template = EMPTY)
    public static void fumeDoseAndPoisonRefresh(GameTestHelper helper) {
        PoisonFumeEntity fume = fume(helper, new Vec3(2.5D, 1D, 2.5D), MobSpawnType.COMMAND, false);
        ServerPlayer player = victim(helper, new Vec3(2.5D, 1D, 2.5D));
        helper.assertTrue(fume.affectPlayers(List.of(player)) == 1, "范围里的人应中招");
        helper.assertTrue(Radiation.get(player) == PoisonFumeEntity.DOSE, "应 +100，实际 " + Radiation.get(player));
        MobEffectInstance poison = player.getEffect(MobEffects.POISON);
        helper.assertTrue(poison != null && poison.getAmplifier() == 1 && poison.getDuration() == 200, "应中毒 II 200 tick，实际 " + poison);

        player.removeEffect(MobEffects.POISON);
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 150, 1));
        fume.affectPlayers(List.of(player));
        helper.assertTrue(player.getEffect(MobEffects.POISON).getDuration() == 150, "剩余 ≥ 100 时不刷新");
        helper.assertTrue(Radiation.get(player) == 2 * PoisonFumeEntity.DOSE, "每次都 +100");
        player.removeEffect(MobEffects.POISON);
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 50, 1));
        fume.affectPlayers(List.of(player));
        helper.assertTrue(player.getEffect(MobEffects.POISON).getDuration() == 200, "剩余 < 100 时刷新成 200");

        ServerPlayer attuned = victim(helper, new Vec3(2.5D, 1D, 2.5D));
        wear(helper, attuned, ECBaubles.MU_RING);
        ServerPlayer creative = player(helper, new Vec3(2.5D, 1D, 2.5D));
        creative.setGameMode(GameType.CREATIVE);
        helper.assertTrue(fume.affectPlayers(List.of(attuned, creative)) == 0, "μ 和创造不受影响");
        helper.assertTrue(Radiation.get(attuned) == 0 && !attuned.hasEffect(MobEffects.POISON), "μ 没有辐射和中毒");

        Vec3 center = fume.position();
        ServerPlayer far = player(helper, new Vec3(2.5D, 1D, 2.5D));
        far.moveTo(center.x + 7.4D, center.y, center.z, 0F, 0F);
        ServerPlayer near = player(helper, new Vec3(2.5D, 1D, 2.5D));
        near.moveTo(center.x + 7.2D, center.y, center.z, 0F, 0F);
        ServerPlayer above = player(helper, new Vec3(2.5D, 1D, 2.5D));
        above.moveTo(center.x, center.y + 3.9D, center.z, 0F, 0F);
        ServerPlayer tooHigh = player(helper, new Vec3(2.5D, 1D, 2.5D));
        tooHigh.moveTo(center.x, center.y + 4.1D, center.z, 0F, 0F);
        ServerPlayer below = player(helper, new Vec3(2.5D, 1D, 2.5D));
        below.moveTo(center.x, center.y - 5.7D, center.z, 0F, 0F);
        ServerPlayer tooLow = player(helper, new Vec3(2.5D, 1D, 2.5D));
        tooLow.moveTo(center.x, center.y - 5.9D, center.z, 0F, 0F);
        helper.assertTrue(fume.affectPlayers(List.of(near)) == 1 && fume.affectPlayers(List.of(far)) == 0, "水平范围约 ±7.3（原作盒子 ±7 与碰撞箱相交）");
        helper.assertTrue(fume.affectPlayers(List.of(above)) == 1 && fume.affectPlayers(List.of(tooHigh)) == 0, "头上 4 格以内");
        helper.assertTrue(fume.affectPlayers(List.of(below)) == 1 && fume.affectPlayers(List.of(tooLow)) == 0, "脚下约 5.8 格以内");
        helper.succeed();
    }

    /** 只有 /kill 能杀；不挡准星、推不动、不能被攻击；追踪 4 区块、同步间隔 ≥ 5；不受重力、不怕火 */
    @GameTest(template = EMPTY)
    public static void fumeKillableAndNotPickable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        EntityType<PoisonFumeEntity> type = ECHoannaHazards.POISON_FUME.get();
        helper.assertTrue(type.clientTrackingRange() == 4 && type.updateInterval() >= 5, "追踪 4 区块、同步间隔 ≥ 5");
        helper.assertTrue(type.fireImmune() && type.getCategory() == MobCategory.MONSTER, "不怕火、怪物类别");
        PoisonFumeEntity fume = fume(helper, new Vec3(2.5D, 2D, 2.5D), MobSpawnType.SPAWN_EGG, true);
        try {
            ServerPlayer player = victim(helper, new Vec3(1.5D, 1D, 1.5D));
            helper.assertFalse(fume.hurt(level.damageSources().generic(), 5F), "普通伤害打不动");
            helper.assertFalse(fume.hurt(level.damageSources().playerAttack(player), 5F), "玩家打不动");
            helper.assertFalse(fume.hurt(level.damageSources().inFire(), 5F), "火烧不动");
            helper.assertTrue(fume.getHealth() == fume.getMaxHealth(), "生命应不变");
            helper.assertFalse(fume.isPickable() || fume.isPushable() || fume.isAttackable() || fume.canBeSeenAsEnemy(), "不挡准星、推不动、不能被攻击");
            helper.assertTrue(fume.isNoGravity() && fume.isIgnoringBlockTriggers(), "不受重力、不踩压力板");
            helper.assertFalse(fume.isNatural(), "刷怪蛋生成的不算自然生成");
            fume.kill();
            helper.assertTrue(fume.isDeadOrDying() || fume.isRemoved(), "/kill 应能杀掉");
        } finally {
            fume.discard();
        }
        helper.succeed();
    }

    /** 毒雾真在世界里 tick：自然生成的在事件期间活着、事件结束后 20 tick 内消失；刷怪蛋的活着（自己一批，全程开覆盖） */
    @GameTest(template = EMPTY, batch = FUMES_BATCH, timeoutTicks = 200)
    public static void fumeDiscardsWhenEventEnds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // 这一批只有这一条：设一次就一直在，没有别的测试会清；不能用 onEachTick 反复设（超时失败时会一直设到超时那一刻、漏给后面的批）
        ECHoanna.setTestHoannaOverride(level);
        HoannaHazardsConfig.setTestChanceMultiplier(0D);
        HoannaEvents.start(level, HoannaEvent.FUMES, 1000);
        PoisonFumeEntity natural = fume(helper, new Vec3(1.5D, 2D, 1.5D), MobSpawnType.NATURAL, true);
        PoisonFumeEntity egg = fume(helper, new Vec3(3.5D, 2D, 3.5D), MobSpawnType.SPAWN_EGG, true);
        // 不让它们飘走：飘出强制加载的区块就不 tick 了（NoAI 只停移动，年龄、查事件照常在 aiStep 里跑）
        natural.setNoAi(true);
        egg.setNoAi(true);
        long[] stoppedAt = {0L};
        Runnable cleanup = () -> {
            natural.discard();
            egg.discard();
            HoannaEvents.stop(level);
            HoannaHazardsConfig.clearTestOverrides();
            ECHoanna.clearTestOverride();
        };
        // 兜底：卡住到快超时时也清掉覆盖（测试已经结束时这一条不会跑）
        helper.runAtTickTime(190L, cleanup);
        helper.startSequence()
                .thenIdle(30)
                .thenExecute(() -> {
                    if (natural.isRemoved() || egg.isRemoved()) {
                        cleanup.run();
                        helper.fail("毒雾事件期间两只都应活着");
                    }
                    helper.assertTrue(natural.isNatural() && !egg.isNatural(), "一只自然生成、一只刷怪蛋");
                    helper.assertTrue(natural.age() >= 25, "自然生成的应在真 tick，age " + natural.age());
                    HoannaEvents.stop(level);
                    stoppedAt[0] = helper.getTick();
                })
                .thenWaitUntil(() -> helper.assertTrue(natural.isRemoved(), "事件结束后自然生成的应消失"))
                .thenExecute(() -> {
                    long waited = helper.getTick() - stoppedAt[0];
                    boolean eggAlive = !egg.isRemoved();
                    cleanup.run();
                    helper.assertTrue(waited <= PoisonFumeEntity.CHECK_INTERVAL + 1, "应在 20 tick 内消失，实际 " + waited);
                    helper.assertTrue(eggAlive, "刷怪蛋生成的应还活着");
                })
                .thenSucceed();
    }
}
