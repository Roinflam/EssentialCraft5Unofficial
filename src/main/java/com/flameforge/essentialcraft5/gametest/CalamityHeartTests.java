package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.entity.PoisonFumeEntity;
import com.flameforge.essentialcraft5.entity.boss.BossDaily;
import com.flameforge.essentialcraft5.entity.boss.BossRoster;
import com.flameforge.essentialcraft5.entity.boss.CalamityHeartContracts;
import com.flameforge.essentialcraft5.entity.boss.CalamityHeartEntity;
import com.flameforge.essentialcraft5.entity.boss.CalamityHeartSkills;
import com.flameforge.essentialcraft5.entity.boss.CalamityHeartSummon;
import com.flameforge.essentialcraft5.entity.boss.CalamityPillarEntity;
import com.flameforge.essentialcraft5.entity.boss.CalamityRubbleEntity;
import com.flameforge.essentialcraft5.entity.boss.ECBoss;
import com.flameforge.essentialcraft5.entity.boss.MatrixShadeSummon;
import com.flameforge.essentialcraft5.hoanna.HoannaHazardsConfig;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import com.flameforge.essentialcraft5.item.CalamityBeaconItem;
import com.flameforge.essentialcraft5.loot.condition.CalamityMainCondition;
import com.flameforge.essentialcraft5.matrix.MatrixRank;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECBossFramework;
import com.flameforge.essentialcraft5.registry.ECCalamityHeart;
import com.flameforge.essentialcraft5.registry.ECEffects;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECMobDrops;
import com.flameforge.essentialcraft5.registry.ECShade;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.flameforge.essentialcraft5.util.SpawnOrigin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 第十三批 13e「天灾之心」（batch13.md §5.3.5、§12.2 13e 行天灾之心部分、§12.4 C73）。
 * <ul>
 *   <li>召唤：逐条拒绝（假玩家、创造、和平、不在海纳尔、八阶门、没有天灾、头顶不露天、不平、五处权限任一处不许、附近已有、今天打倒过、暗影核心不够、生成被取消）
 *       什么都不扣；门关掉时不挡、关掉 require_calamity 时随机主灾且多扣核心；地脉风暴按种子取主灾。</li>
 *   <li>信标与结算：满的 0 个核心、spent 的 2 个；没打倒原样退回信标连核心、当天还能再召；打倒后 spent 的信标回到背包、当天记一次；
 *       召唤者不在线时登录补、回档后不重复。</li>
 *   <li>战斗：护盾期打不动、破盾窗口打不到崩解线以下；灾柱倒下放残响；失衡 → 崩解 → 只重立一次 → 一直脉冲、打得死；灾柱读档重建。
 *       招式只打参战者（毒雾旁放村民、狼、非参战玩家对照），烈日标记钻到方块下躲开，落石不放方块、不存盘，暗袭、震波、吐息、棱线照数值打。</li>
 *   <li>掉落：每名 ≥ 10% 的人一份、首杀 +1、主灾奖励只给对应的那一相；绕过无敌的非参战致死不给、按失败退还；刷怪蛋放的不给。</li>
 * </ul>
 * 模板 empty_large（11×8×11），地面是 y=0；召唤点 (5, 0, 5)，测试配置把灾柱距离缩到 4 格（四根立在 (5,1,1)、(9,1,5)、(5,1,9)、(1,1,5)）、
 * 互斥半径 4、平整度 2、两招间隔调到很长（只有测试手动起的招）、不限每秒桶。玩家是真 ServerPlayer（不进世界、按在线覆盖算在线）。
 * 「此刻的天灾」用 {@link CalamityHeartSummon#setTestCalamity} 钉住，超平坦主世界用 {@link ECHoanna#setTestHoannaOverride} 当成海纳尔，
 * 矩阵阶位门按测试关掉；这些覆盖、契约、在线覆盖都在 finally 里清掉。要看天光的测试先等光照更新（召唤点露天、屋顶下不露天）。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class CalamityHeartTests {

    static final String LARGE = "empty_large";
    static final String BATCH = "ec5_calamity_heart";
    static final BlockPos FLOOR = new BlockPos(5, 0, 5);
    static final Vec3 STAND = new Vec3(7.5D, 1D, 7.5D);
    static final double EPS = 1.0E-3D;
    static final ECCalamityHeart.Config.Values DEFAULTS = ECCalamityHeart.Config.DEFAULTS;
    static final int OFFSET = 4;
    /** 旧机制的测试一律不带开场、封印和加强那一轮的新招（新机制在 {@link CalamityHeartFuryTests}） */
    static final ECCalamityHeart.Config.Fury QUIET = DEFAULTS.fury().quiet();

    private CalamityHeartTests() {
    }

    // ================================================================== 配置

    static ECCalamityHeart.Config.Summon summonCfg(boolean requireCalamity, int maxUnevenness) {
        ECCalamityHeart.Config.Summon s = DEFAULTS.summon();
        return new ECCalamityHeart.Config.Summon(requireCalamity, s.coresPerResummon(), s.coresExtraWithoutCalamity(), OFFSET, maxUnevenness, 4,
                s.dissipateTicks(), s.firstKillBonus());
    }

    static ECCalamityHeart.Config.Fight fightCfg(int shieldWindow, int collapseTicks, int pulseInterval) {
        ECCalamityHeart.Config.Fight f = DEFAULTS.fight();
        return new ECCalamityHeart.Config.Fight(f.maxHealth(), f.armor(), f.toughness(), 0D, 0D, f.pillarHealth(), f.mainPillarHealth(), f.pillarPerHit(),
                f.arenaRadius(), f.pullRange(), f.escapeRange(), f.shieldHover(), f.lowHover(), f.followSpeed(), shieldWindow, 100_000,
                f.aspectSwapTicks(), f.unbalancedCooldown(), f.collapseThreshold(), collapseTicks, f.collapseDamageTaken(), pulseInterval,
                f.pulseRange(), f.pulseDamage(), f.pulsePercent(), f.reraiseHealth(), f.reraisePillars(), f.experience());
    }

    static ECCalamityHeart.Config.Values local() {
        return new ECCalamityHeart.Config.Values(summonCfg(true, 2), fightCfg(400, 10, 5), DEFAULTS.skills(), QUIET);
    }

    static ECCalamityHeart.Config.Values local(ECCalamityHeart.Config.Summon summon) {
        ECCalamityHeart.Config.Values base = local();
        return new ECCalamityHeart.Config.Values(summon, base.fight(), base.skills(), base.fury());
    }

    static ECCalamityHeart.Config.Values local(ECCalamityHeart.Config.Skills skills) {
        ECCalamityHeart.Config.Values base = local();
        return new ECCalamityHeart.Config.Values(base.summon(), base.fight(), skills, base.fury());
    }

    /** 黑暗相测试用：熄光 2 刻预警、20 刻长、1 次暗袭、暗袭 5 刻落下；落石同时最多 3 块；震波 1 圈、预警 1 刻 */
    private static ECCalamityHeart.Config.Skills quickSkills() {
        ECCalamityHeart.Config.Skills k = DEFAULTS.skills();
        return new ECCalamityHeart.Config.Skills(k.prismDamage(), k.prismPercent(), 1, k.maxPrisms(), 5, k.prismFireSeconds(),
                k.markPercent(), 5, k.noonPercent(), 5,
                k.maxFumes(), k.fumesPerCast(), k.fumeTicks(), k.echoFumeTicks(),
                k.breathDamage(), k.breathPercent(), k.breathRange(), 3, k.corrosionTicks(),
                2, 20, 1, k.longNightTicks(), k.longNightStrikes(),
                k.strikeDamage(), 5,
                k.quakeDamage(), k.quakePercent(), 1, k.faultRings(), 1,
                3, k.rubbleCount(), k.rubbleDamage(), k.rubbleWarnTicks());
    }

    // ================================================================== 场地与玩家

    /** 覆盖都设上：当成海纳尔、此刻日阵、阶位门关掉、测试配置 */
    static void scope(GameTestHelper helper, ECCalamityHeart.Config.Values values) {
        ECHoanna.setTestHoannaOverride(helper.getLevel());
        CalamityHeartSummon.setTestCalamity(HoannaEvent.SUN_ARRAY);
        MatrixRank.setTestOverrides(false, null, null, null);
        ECCalamityHeart.Config.setTestOverrides(values);
    }

    static void unscope() {
        ECHoanna.clearTestOverride();
        CalamityHeartSummon.clearTestCalamity();
        MatrixRank.clearTestOverrides();
        ECCalamityHeart.Config.setTestOverrides(null);
    }

    /** 地面铺平（y=0 整片石头，上面清空） */
    static void flatten(GameTestHelper helper) {
        for (int x = 0; x <= 10; x++) {
            for (int z = 0; z <= 10; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                for (int y = 1; y <= 6; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
    }

    static ServerPlayer player(GameTestHelper helper, String name, Vec3 relativeFeet, GameType mode) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper, UUID.randomUUID(), name);
        for (int i = 0; i <= 60; i++) {
            player.tick();
        }
        player.setGameMode(mode);
        moveTo(helper, player, relativeFeet);
        player.setHealth(player.getMaxHealth());
        player.setOnGround(true);
        ECProtection.setOnlineOverride(player);
        return player;
    }

    static void moveTo(GameTestHelper helper, Entity entity, Vec3 relative) {
        Vec3 at = helper.absoluteVec(relative);
        entity.moveTo(at.x, at.y, at.z, entity.getYRot(), entity.getXRot());
    }

    private static IPlayerData data(ServerPlayer player) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            throw new IllegalStateException("玩家应挂着玩家数据");
        }
        return data;
    }

    private static int count(ServerPlayer player, Item item) {
        return player.getInventory().countItem(item);
    }

    private static int cores(ServerPlayer player) {
        return count(player, ECShade.SHADE_CORE.get());
    }

    private static int beacons(ServerPlayer player) {
        return count(player, ECCalamityHeart.CALAMITY_BEACON.get());
    }

    /** 背包里那个信标（没有返回空） */
    private static ItemStack beaconIn(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ECCalamityHeart.CALAMITY_BEACON.get())) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static Item item(String path) {
        Item item = ForgeRegistries.ITEMS.getValue(EssentialCraft5.id(path));
        if (item == null || item == Items.AIR) {
            throw new IllegalStateException("没有物品 " + path);
        }
        return item;
    }

    static DamageSource melee(ServerPlayer player) {
        return player.damageSources().playerAttack(player);
    }

    static boolean hit(Entity target, DamageSource source, float amount) {
        if (target instanceof net.minecraft.world.entity.LivingEntity living) {
            living.invulnerableTime = 0;
        }
        return target.hurt(source, amount);
    }

    static ItemStack beacon(boolean spent) {
        ItemStack stack = new ItemStack(ECCalamityHeart.CALAMITY_BEACON.get());
        CalamityBeaconItem.setSpent(stack, spent);
        return stack;
    }

    record Rig(ServerPlayer summoner, CalamityHeartSummon.Outcome outcome) {

        CalamityHeartEntity heart() {
            CalamityHeartEntity heart = outcome.heart();
            if (heart == null) {
                throw new IllegalStateException("没召出来：" + outcome.result());
            }
            return heart;
        }
    }

    static CalamityHeartSummon.Outcome use(GameTestHelper helper, ServerPlayer player) {
        CalamityHeartSummon.Outcome outcome = CalamityHeartSummon.summon(player, helper.getLevel(), helper.absolutePos(FLOOR), player.getMainHandItem());
        if (outcome.heart() != null) {
            outcome.heart().setNoAi(true);
        }
        return outcome;
    }

    /** 平地上召一次：召唤者手里拿着 beacon、背包里 cores 个暗影核心 */
    static Rig summon(GameTestHelper helper, String name, ItemStack beacon, int cores) {
        flatten(helper);
        ServerPlayer player = player(helper, name, STAND, GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, beacon);
        if (cores > 0) {
            player.getInventory().add(new ItemStack(ECShade.SHADE_CORE.get(), cores));
        }
        return new Rig(player, use(helper, player));
    }

    /** 收尾：这一场的契约和挂着的补交、技能实体、灾柱、在线覆盖都清掉，召出来的删掉（先删契约，删的时候不退），覆盖全清 */
    static void cleanup(GameTestHelper helper, @Nullable Rig rig, Entity... others) {
        ServerLevel level = helper.getLevel();
        List<Entity> all = new ArrayList<>();
        if (rig != null) {
            all.add(rig.outcome().heart());
            all.add(rig.summoner());
        }
        // 断言中途失败时传进来的可能还是 null：List.of 不收 null，会把整个 GameTest 服带崩
        all.addAll(Arrays.asList(others));
        for (Entity entity : all) {
            if (entity == null) {
                continue;
            }
            if (entity instanceof CalamityHeartEntity heart) {
                CalamityHeartContracts.forgetForTest(level, heart.getUUID(), null);
                heart.skills().discardAll();
                for (int i = 0; i < 4; i++) {
                    CalamityPillarEntity pillar = heart.pillar(i);
                    if (pillar != null) {
                        pillar.discard();
                    }
                }
                if (!heart.isRemoved()) {
                    heart.discard();
                }
            } else if (entity instanceof ServerPlayer player) {
                CalamityHeartContracts.forgetForTest(level, null, player.getUUID());
                ECProtection.clearOnlineOverride(player.getUUID());
            } else if (!entity.isRemoved()) {
                entity.discard();
            }
        }
        for (CalamityPillarEntity pillar : level.getEntitiesOfClass(CalamityPillarEntity.class, new AABB(helper.absolutePos(FLOOR)).inflate(12D))) {
            if (pillar.heart() == null || pillar.heart().isRemoved()) {
                pillar.discard();
            }
        }
        unscope();
    }

    static Difficulty setDifficulty(GameTestHelper helper, Difficulty next) {
        MinecraftServer server = helper.getLevel().getServer();
        Difficulty old = helper.getLevel().getDifficulty();
        server.setDifficulty(next, true);
        return old;
    }

    /** 召唤点头顶露天了再做（光照是异步更新的） */
    static void whenSky(GameTestHelper helper, Runnable body) {
        flatten(helper);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(helper.getLevel().canSeeSky(helper.absolutePos(FLOOR.above())), "等召唤点露天"))
                .thenExecute(body)
                .thenSucceed();
    }

    /** 四根灾柱全打掉（管理员 /kill 那种，不记谁打的），推一刻阶段 */
    static void dropPillars(GameTestHelper helper, CalamityHeartEntity heart) {
        for (int i = 0; i < 4; i++) {
            CalamityPillarEntity pillar = heart.pillar(i);
            if (pillar != null) {
                BossFrameworkTests.asKillCommand(pillar::kill);
            }
        }
        heart.tickPhases(helper.getLevel());
        heart.skills().discardAll();
    }

    // ================================================================== 注册

    /** 注册：首领 MONSTER、在 forge:bosses；灾柱、落石 MISC；信标一组 1；主灾条件登记了；光源判定（火把、灯笼算，石头、空手不算）；音乐和音效都在 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void calamityHeartRegistry(GameTestHelper helper) {
        helper.assertTrue(ECCalamityHeart.CALAMITY_HEART.get().getCategory() == MobCategory.MONSTER, "首领是 MONSTER");
        helper.assertTrue(ECCalamityHeart.CALAMITY_PILLAR.get().getCategory() == MobCategory.MISC
                && ECCalamityHeart.CALAMITY_RUBBLE.get().getCategory() == MobCategory.MISC, "灾柱、落石是 MISC");
        TagKey<EntityType<?>> bosses = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("forge", "bosses"));
        helper.assertTrue(ECCalamityHeart.CALAMITY_HEART.get().is(bosses), "在 forge:bosses 里");
        helper.assertTrue(new ItemStack(ECCalamityHeart.CALAMITY_BEACON.get()).getMaxStackSize() == 1, "信标一组 1");
        helper.assertTrue(BuiltInRegistries.LOOT_CONDITION_TYPE.get(CalamityMainCondition.ID) == CalamityMainCondition.TYPE, "主灾条件登记了");
        helper.assertTrue(CalamityHeartEntity.isLightSource(new ItemStack(Items.TORCH)) && CalamityHeartEntity.isLightSource(new ItemStack(Items.LANTERN))
                && !CalamityHeartEntity.isLightSource(new ItemStack(Items.STONE)) && !CalamityHeartEntity.isLightSource(ItemStack.EMPTY), "光源判定");
        helper.assertTrue(ForgeRegistries.SOUND_EVENTS.containsKey(EssentialCraft5.id("sound.music.calamity_heart"))
                && ForgeRegistries.SOUND_EVENTS.containsKey(EssentialCraft5.id("sound.mob.calamity_heart.blackout")), "音乐和音效登记了");
        ItemStack stack = beacon(true);
        helper.assertTrue(CalamityBeaconItem.isSpent(stack), "spent 写得上");
        CalamityBeaconItem.setSpent(stack, false);
        helper.assertTrue(!CalamityBeaconItem.isSpent(stack) && stack.getTag() == null, "清掉以后和新合成的一样");
        helper.succeed();
    }

    // ================================================================== 召唤

    /**
     * 满的信标召唤：信标收走、不扣核心；召唤者进名单；来源记首领；四根灾柱立起来（主灾那根 360、别的 240），护盾期打不动；契约记下；
     * 点在别的维度（不是海纳尔）不响应、不消耗
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 200)
    public static void calamityHeartSummonFull(GameTestHelper helper) {
        whenSky(helper, () -> {
            scope(helper, local());
            Rig rig = null;
            try {
                rig = summon(helper, "ch-full", beacon(false), 3);
                helper.assertTrue(rig.outcome().ok(), "召得出，实际 " + rig.outcome().result());
                CalamityHeartEntity heart = rig.heart();
                ServerPlayer summoner = rig.summoner();
                helper.assertTrue(summoner.getMainHandItem().isEmpty() && cores(summoner) == 3 && rig.outcome().value() == 0, "信标收走、核心不扣");
                helper.assertTrue(heart.bossRoster().contains(summoner.getUUID()) && heart.bound() && summoner.getUUID().equals(heart.summoner()), "召唤者进名单");
                helper.assertTrue(SpawnOrigin.of(heart) == SpawnOrigin.Origin.BOSS, "来源记首领");
                helper.assertTrue(heart.mainAspect() == CalamityHeartEntity.Aspect.SUN_ARRAY, "主灾 = 此刻的天灾");
                helper.assertTrue(heart.pillarsStanding() == 4, "四根灾柱");
                for (int i = 0; i < 4; i++) {
                    CalamityPillarEntity pillar = heart.pillar(i);
                    helper.assertTrue(pillar != null, "第 " + i + " 根立起来了");
                    double max = i == CalamityHeartEntity.Aspect.SUN_ARRAY.ordinal() ? 360D : 240D;
                    helper.assertTrue(Math.abs(pillar.getMaxHealth() - max) < EPS && pillar.aspect().ordinal() == i, "第 " + i + " 根 " + max + " 血");
                }
                BlockPos sun = heart.pillarFeet(CalamityHeartEntity.Aspect.SUN_ARRAY.ordinal());
                helper.assertTrue(helper.absolutePos(new BlockPos(5, 1, 1)).equals(sun), "日阵那根在北边 4 格，实际 " + sun);
                helper.assertTrue(heart.isInvulnerableTo(melee(summoner)) && !hit(heart, melee(summoner), 10F), "护盾期打不动");
                helper.assertTrue(CalamityHeartContracts.isOpen(helper.getLevel(), heart.getUUID()), "记了契约");
                CalamityHeartContracts.Contract contract = CalamityHeartContracts.get(helper.getLevel()).contract(heart.getUUID());
                helper.assertTrue(contract != null && contract.cores() == 0 && !CalamityBeaconItem.isSpent(contract.beacon()), "契约里信标原样、扣了 0 个");
            } finally {
                cleanup(helper, rig);
            }
        });
    }

    /** 召唤者本身不行：假玩家、创造、和平、不在海纳尔、八阶门；都不消耗信标。门关掉时不挡 */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 200)
    public static void calamityHeartSummonRefusedPlayer(GameTestHelper helper) {
        whenSky(helper, () -> {
            scope(helper, local());
            ServerPlayer player = player(helper, "ch-who", STAND, GameType.SURVIVAL);
            List<CalamityHeartEntity> made = new ArrayList<>();
            try {
                ItemStack stack = beacon(false);
                ServerLevel level = helper.getLevel();
                BlockPos at = helper.absolutePos(FLOOR);
                helper.assertTrue(CalamityHeartSummon.summon(ProtectionFrameworkTests.fakePlayer(helper), level, at, stack).result()
                        == CalamityHeartSummon.Result.NOT_REAL_PLAYER, "假玩家");
                player.setGameMode(GameType.CREATIVE);
                helper.assertTrue(CalamityHeartSummon.summon(player, level, at, stack).result() == CalamityHeartSummon.Result.GAME_MODE, "创造");
                player.setGameMode(GameType.SURVIVAL);
                Difficulty old = setDifficulty(helper, Difficulty.PEACEFUL);
                try {
                    helper.assertTrue(CalamityHeartSummon.summon(player, level, at, stack).result() == CalamityHeartSummon.Result.PEACEFUL, "和平");
                } finally {
                    setDifficulty(helper, old);
                }
                ECHoanna.clearTestOverride();
                helper.assertTrue(CalamityHeartSummon.summon(player, level, at, stack).result() == CalamityHeartSummon.Result.NOT_HOANNA, "不在海纳尔");
                ECHoanna.setTestHoannaOverride(level);
                MatrixRank.setTestOverrides(true, null, null, 8);
                CalamityHeartSummon.Outcome gated = CalamityHeartSummon.summon(player, level, at, stack);
                helper.assertTrue(gated.result() == CalamityHeartSummon.Result.RANK
                        && gated.message(player).getString().equals(MatrixRank.gateMessage(player, MatrixRank.Gate.CALAMITY_BEACON).getString()), "八阶门");
                helper.assertTrue(stack.getCount() == 1, "都不消耗");
                MatrixRank.setTestOverrides(false, null, null, 8);
                CalamityHeartSummon.Outcome open = CalamityHeartSummon.summon(player, level, at, stack);
                if (open.heart() != null) {
                    made.add(open.heart());
                    open.heart().setNoAi(true);
                }
                helper.assertTrue(open.ok() && stack.isEmpty(), "矩阵阶位关掉时门不挡，实际 " + open.result());
            } finally {
                cleanup(helper, null, made.toArray(new Entity[0]));
                cleanup(helper, null, player);
            }
        });
    }

    /** 天灾：要天灾而此刻没有就拒；地脉风暴按种子取一种（同一天召几次一样）；关掉 require_calamity 时随机主灾、满的信标也扣 2 个核心 */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 200)
    public static void calamityHeartSummonCalamity(GameTestHelper helper) {
        whenSky(helper, () -> {
            scope(helper, local());
            Rig rig = null;
            try {
                CalamityHeartSummon.setTestCalamity(HoannaEvent.NONE);
                rig = summon(helper, "ch-none", beacon(false), 0);
                helper.assertTrue(rig.outcome().result() == CalamityHeartSummon.Result.NO_CALAMITY && beacons(rig.summoner()) == 1, "没有天灾就拒、不消耗");
                ServerLevel level = helper.getLevel();
                CalamityHeartSummon.setTestCalamity(HoannaEvent.LEY_STORM);
                CalamityHeartEntity.Aspect storm = CalamityHeartSummon.mainFor(level, true);
                helper.assertTrue(storm != null && storm == CalamityHeartSummon.mainFor(level, true), "地脉风暴按种子取一种、稳定");
                CalamityHeartSummon.setTestCalamity(HoannaEvent.DARKNESS);
                helper.assertTrue(CalamityHeartSummon.mainFor(level, true) == CalamityHeartEntity.Aspect.DARKNESS, "四种天灾就是它自己");
                CalamityHeartSummon.setTestCalamity(HoannaEvent.NONE);
                helper.assertTrue(CalamityHeartSummon.mainFor(level, false) != null, "关掉 require_calamity：没有天灾也随机一种");

                ECCalamityHeart.Config.setTestOverrides(local(summonCfg(false, 2)));
                helper.assertTrue(CalamityHeartSummon.coreCost(beacon(false)) == 2 && CalamityHeartSummon.coreCost(beacon(true)) == 4, "关天灾时满的 2、spent 的 4");
                CalamityHeartSummon.Outcome poor = use(helper, rig.summoner());
                helper.assertTrue(poor.result() == CalamityHeartSummon.Result.NO_CORES && poor.value() == 2 && poor.have() == 0
                        && beacons(rig.summoner()) == 1, "核心不够就拒、不消耗");
                rig.summoner().getInventory().add(new ItemStack(ECShade.SHADE_CORE.get(), 3));
                CalamityHeartSummon.Outcome ok = use(helper, rig.summoner());
                rig = new Rig(rig.summoner(), ok);
                helper.assertTrue(ok.ok() && cores(rig.summoner()) == 1 && beacons(rig.summoner()) == 0, "扣 2 个，实际 " + ok.result());
            } finally {
                cleanup(helper, rig);
            }
        });
    }

    /**
     * 天灾不会自己发生（chance_multiplier 0、天灾全关、只剩地脉风暴却没有潮汐或几率 0）时 require_calamity 开着也按关掉算：
     * 此刻没有天灾照样召得出来、满的信标多扣 cores_extra_without_calamity 个暗影核心；倍数恢复以后又要天灾、满的信标不扣
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 200)
    public static void calamityHeartSummonWithoutNaturalCalamity(GameTestHelper helper) {
        whenSky(helper, () -> {
            scope(helper, local());
            Rig rig = null;
            try {
                Predicate<HoannaEvent> all = e -> !e.isNone();
                Predicate<HoannaEvent> leyOnly = e -> e == HoannaEvent.LEY_STORM;
                helper.assertFalse(HoannaHazardsConfig.calamitiesCanStartNaturally(0D, all, 0.3D, true), "倍数 0：不会自己来");
                helper.assertFalse(HoannaHazardsConfig.calamitiesCanStartNaturally(1D, e -> false, 0.3D, true), "全关：不会自己来");
                helper.assertFalse(HoannaHazardsConfig.calamitiesCanStartNaturally(1D, leyOnly, 0.3D, false), "只剩地脉风暴、潮汐关掉：不会来");
                helper.assertFalse(HoannaHazardsConfig.calamitiesCanStartNaturally(1D, leyOnly, 0D, true), "只剩地脉风暴、几率 0：不会来");
                helper.assertTrue(HoannaHazardsConfig.calamitiesCanStartNaturally(1D, leyOnly, 0.3D, true), "地脉风暴会来");
                helper.assertTrue(HoannaHazardsConfig.calamitiesCanStartNaturally(1D, e -> e == HoannaEvent.DARKNESS, 0D, false), "开着一种就会来");

                ECCalamityHeart.Config.Summon s = local().summon();
                int extra = s.coresExtraWithoutCalamity();
                helper.assertTrue(s.requireCalamity() && extra > 0 && CalamityHeartSummon.requiresCalamity(s), "前提：默认要天灾、关掉时多扣核心");
                CalamityHeartSummon.setTestCalamity(HoannaEvent.NONE);
                HoannaHazardsConfig.setTestChanceMultiplier(0D);
                helper.assertFalse(CalamityHeartSummon.requiresCalamity(s), "倍数 0：按不要求天灾算");
                helper.assertTrue(CalamityHeartSummon.coreCost(beacon(false)) == extra
                        && CalamityHeartSummon.coreCost(beacon(true)) == s.coresPerResummon() + extra, "满的多扣 " + extra + "、空的也多扣");
                rig = summon(helper, "ch-nochance", beacon(false), extra - 1);
                helper.assertTrue(rig.outcome().result() == CalamityHeartSummon.Result.NO_CORES && beacons(rig.summoner()) == 1,
                        "核心差一个：拒、不消耗，实际 " + rig.outcome().result());
                rig.summoner().getInventory().add(new ItemStack(ECShade.SHADE_CORE.get(), 1));
                CalamityHeartSummon.Outcome ok = use(helper, rig.summoner());
                rig = new Rig(rig.summoner(), ok);
                helper.assertTrue(ok.ok() && ok.heart() != null && cores(rig.summoner()) == 0 && beacons(rig.summoner()) == 0,
                        "此刻没有天灾也召得出来、扣 " + extra + " 个核心，实际 " + ok.result());

                HoannaHazardsConfig.setTestChanceMultiplier(null);
                helper.assertTrue(CalamityHeartSummon.requiresCalamity(s) && CalamityHeartSummon.coreCost(beacon(false)) == 0,
                        "倍数恢复：又要天灾、满的信标不扣");
            } finally {
                HoannaHazardsConfig.setTestChanceMultiplier(null);
                cleanup(helper, rig);
            }
        });
    }

    /** 信标提示「需要」那一行：阶数按门的配置写；门填 0（或矩阵阶关掉，调用方传 0）时不写阶；不要求天灾时换不写天灾的那句 */
    @GameTest(template = LARGE, batch = BATCH)
    public static void calamityBeaconNeedLine(GameTestHelper helper) {
        assertNeed(helper, CalamityBeaconItem.needLine(true, 8), "need", 8);
        assertNeed(helper, CalamityBeaconItem.needLine(true, 6), "need", 6);
        assertNeed(helper, CalamityBeaconItem.needLine(false, 7), "need_any", 7);
        assertNeed(helper, CalamityBeaconItem.needLine(true, 0), "need_unranked", -1);
        assertNeed(helper, CalamityBeaconItem.needLine(false, 0), "need_any_unranked", -1);
        helper.succeed();
    }

    private static void assertNeed(GameTestHelper helper, Component line, String suffix, int rank) {
        String key = "tooltip.essentialcraft5.calamity_beacon." + suffix;
        helper.assertTrue(line.getContents() instanceof TranslatableContents contents && contents.getKey().equals(key), "键应为 " + key);
        Object[] args = ((TranslatableContents) line.getContents()).getArgs();
        if (rank < 0) {
            helper.assertTrue(args.length == 0, suffix + " 不带阶");
            return;
        }
        helper.assertTrue(args.length == 1 && args[0] instanceof Component c && c.getContents() instanceof TranslatableContents rc
                && rc.getKey().equals("matrix_rank.essentialcraft5.rank." + rank), suffix + " 写 " + rank + " 阶");
    }

    /** 场地：头顶不露天、地面不平（高低差 3 > 2）都拒、不消耗；被取消的生成不扣、不记契约（光照异步，屋顶摆上 / 拿掉都先等天光更新） */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 300)
    public static void calamityHeartSummonRefusedSite(GameTestHelper helper) {
        flatten(helper);
        BlockPos roof = FLOOR.above(4);
        helper.setBlock(roof, Blocks.STONE);
        ServerPlayer player = player(helper, "ch-site", STAND, GameType.SURVIVAL);
        ItemStack stack = beacon(false);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertFalse(helper.getLevel().canSeeSky(helper.absolutePos(FLOOR.above())), "等屋顶的光照更新"))
                .thenExecute(() -> {
                    scope(helper, local());
                    try {
                        helper.assertTrue(use(helper, player).result() == CalamityHeartSummon.Result.NO_SKY, "头顶不露天");
                    } finally {
                        unscope();
                    }
                    helper.setBlock(roof, Blocks.AIR);
                    for (int y = 1; y <= 3; y++) {
                        helper.setBlock(new BlockPos(1, y, 1), Blocks.STONE);
                    }
                })
                .thenWaitUntil(() -> helper.assertTrue(helper.getLevel().canSeeSky(helper.absolutePos(FLOOR.above())), "等拿掉屋顶后的光照更新"))
                .thenExecute(() -> {
                    scope(helper, local());
                    try {
                        CalamityHeartSummon.Outcome uneven = use(helper, player);
                        helper.assertTrue(uneven.result() == CalamityHeartSummon.Result.UNEVEN && uneven.value() == 3,
                                "不平，实际 " + uneven.result() + " " + uneven.value());
                        helper.assertTrue(CalamityHeartSummon.unevenness(helper.getLevel(), helper.absolutePos(FLOOR)) == 3, "25 列里最高最低差 3");
                        helper.assertTrue(stack.getCount() == 1, "都不消耗");
                    } finally {
                        cleanup(helper, null, player);
                    }
                    for (int y = 1; y <= 3; y++) {
                        helper.setBlock(new BlockPos(1, y, 1), Blocks.AIR);
                    }
                    scope(helper, local());
                    Consumer<EntityJoinLevelEvent> cancel = event -> {
                        if (event.getEntity() instanceof CalamityHeartEntity) {
                            event.setCanceled(true);
                        }
                    };
                    Rig rig = null;
                    MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EntityJoinLevelEvent.class, cancel);
                    try {
                        rig = summon(helper, "ch-cxl", beacon(true), 2);
                    } finally {
                        MinecraftForge.EVENT_BUS.unregister(cancel);
                    }
                    try {
                        helper.assertTrue(rig.outcome().result() == CalamityHeartSummon.Result.SPAWN_FAILED, "生成被拦，实际 " + rig.outcome().result());
                        helper.assertTrue(beacons(rig.summoner()) == 1 && cores(rig.summoner()) == 2, "信标、核心都不扣");
                        helper.assertTrue(CalamityHeartContracts.get(helper.getLevel()).contractsOf(rig.summoner().getUUID()).isEmpty(), "不记契约");
                    } finally {
                        cleanup(helper, rig);
                    }
                })
                .thenSucceed();
    }

    /**
     * 五处权限：召唤点、四根灾柱的落脚点，任一处被领地拒绝都召不了（DENIED）、不消耗；全放开以后照常召（走物品右键那一步）。
     * 权限探测每刻有预算，每一处放在单独的一刻里判
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 300)
    public static void calamityHeartSummonDeniedInClaim(GameTestHelper helper) {
        flatten(helper);
        List<BlockPos> spots = List.of(FLOOR, new BlockPos(5, 1, 1), new BlockPos(9, 1, 5), new BlockPos(5, 1, 9), new BlockPos(1, 1, 5));
        ServerPlayer player = player(helper, "ch-claim", STAND, GameType.SURVIVAL);
        ItemStack stack = beacon(false);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos at = helper.absolutePos(FLOOR);
        BlockHitResult click = new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false);
        var sequence = helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(helper.getLevel().canSeeSky(helper.absolutePos(FLOOR.above())), "等召唤点露天"));
        for (BlockPos spot : spots) {
            sequence = sequence.thenExecute(() -> {
                scope(helper, local());
                ECProtection.forget(player.getUUID());
                try (ProtectionFrameworkTests.Claims claims = ProtectionFrameworkTests.Claims.open(helper)) {
                    claims.deny(spot);
                    CalamityHeartSummon.Outcome outcome = CalamityHeartSummon.summon(player, helper.getLevel(), at, stack);
                    helper.assertTrue(outcome.result() == CalamityHeartSummon.Result.DENIED && stack.getCount() == 1,
                            "拒了 " + spot + " 就召不了，实际 " + outcome.result());
                } finally {
                    unscope();
                }
            }).thenIdle(1);
        }
        sequence.thenExecute(() -> {
            scope(helper, local());
            List<Entity> made = new ArrayList<>();
            try {
                helper.assertTrue(helper.getLevel().getEntitiesOfClass(CalamityHeartEntity.class, new AABB(at).inflate(8D)).isEmpty(), "没出怪");
                ECProtection.forget(player.getUUID());
                InteractionResult result = ((CalamityBeaconItem) stack.getItem()).onItemUseFirst(stack, new UseOnContext(player, InteractionHand.MAIN_HAND, click));
                made.addAll(helper.getLevel().getEntitiesOfClass(CalamityHeartEntity.class, new AABB(at).inflate(8D), Entity::isAlive));
                helper.assertTrue(result == InteractionResult.CONSUME && stack.isEmpty() && made.size() == 1, "放开以后照常召出，实际 " + result);
            } finally {
                cleanup(helper, null, made.toArray(new Entity[0]));
                cleanup(helper, null, player);
            }
        }).thenSucceed();
    }

    /** 互斥：附近已有一只天灾之心就拒（矩阵之影的召唤也认它）；今天打倒过就拒，没打倒（失败）的不算 */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 200)
    public static void calamityHeartSummonExclusiveAndDaily(GameTestHelper helper) {
        whenSky(helper, () -> {
            scope(helper, local());
            Rig rig = null;
            ServerPlayer second = null;
            try {
                rig = summon(helper, "ch-one", beacon(false), 0);
                helper.assertTrue(rig.outcome().ok(), "第一只召得出");
                second = player(helper, "ch-two", new Vec3(3.5D, 1D, 7.5D), GameType.SURVIVAL);
                second.setItemInHand(InteractionHand.MAIN_HAND, beacon(false));
                CalamityHeartSummon.Outcome busy = use(helper, second);
                helper.assertTrue(busy.result() == CalamityHeartSummon.Result.OCCUPIED && beacons(second) == 1, "附近已有，实际 " + busy.result());
                Vec3 center = Vec3.atCenterOf(helper.absolutePos(FLOOR));
                helper.assertTrue(MatrixShadeSummon.occupied(helper.getLevel(), center, 4D), "矩阵之影的互斥也认天灾之心");

                rig.heart().endBoss(ECBoss.BossEnd.ABANDONED);
                helper.assertTrue(!CalamityHeartContracts.defeatedToday(rig.summoner(), helper.getLevel().getServer()), "没打倒不算今天的次数");
                cleanup(helper, rig);
                rig = null;
                scope(helper, local());
                CalamityHeartContracts.markDaily(second, BossDaily.dayIndex(helper.getLevel().getServer()));
                CalamityHeartSummon.Outcome daily = use(helper, second);
                helper.assertTrue(daily.result() == CalamityHeartSummon.Result.DAILY && beacons(second) == 1, "今天打倒过就拒，实际 " + daily.result());
                data(second).getBossDaily().putLong(CalamityHeartContracts.DAILY_KEY, BossDaily.dayIndex(helper.getLevel().getServer()) - 1L);
                CalamityHeartSummon.Outcome tomorrow = use(helper, second);
                rig = new Rig(second, tomorrow);
                helper.assertTrue(tomorrow.ok(), "昨天打倒的不挡，实际 " + tomorrow.result());
            } finally {
                cleanup(helper, rig, second);
            }
        });
    }

    // ================================================================== 退还与结算

    /**
     * 失败（名单空了到点）：spent 的信标连这次扣的 2 个核心原样回到背包、契约删掉、开始散去、当天不算；再判一次不再退；
     * 召唤者下线：东西先留在他的存档里，下次上线才给
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 200)
    public static void calamityHeartFailRefunds(GameTestHelper helper) {
        whenSky(helper, () -> {
            scope(helper, local());
            Rig rig = null;
            try {
                rig = summon(helper, "ch-back", beacon(true), 2);
                helper.assertTrue(rig.outcome().ok() && cores(rig.summoner()) == 0 && beacons(rig.summoner()) == 0, "spent 的扣 2 个，实际 " + rig.outcome().result());
                CalamityHeartEntity heart = rig.heart();
                ServerPlayer summoner = rig.summoner();
                heart.endBoss(ECBoss.BossEnd.ABANDONED);
                helper.assertTrue(cores(summoner) == 2 && beacons(summoner) == 1 && CalamityBeaconItem.isSpent(beaconIn(summoner)), "信标原样、核心退回");
                helper.assertTrue(!CalamityHeartContracts.isOpen(helper.getLevel(), heart.getUUID()) && heart.dissipating(), "契约删掉、开始散去");
                helper.assertFalse(CalamityHeartContracts.fail(helper.getLevel(), heart.getUUID(), CalamityHeartContracts.Failure.ABANDONED), "再判一次不再退");
                helper.assertTrue(cores(summoner) == 2 && beacons(summoner) == 1, "没退第二次");
                helper.assertFalse(CalamityHeartContracts.defeatedToday(summoner, helper.getLevel().getServer()), "当天不算");
                for (int i = 0; i < DEFAULTS.summon().dissipateTicks(); i++) {
                    heart.serverBossStep();
                }
                helper.assertTrue(heart.isRemoved(), "200 刻散去");
                cleanup(helper, rig);

                scope(helper, local());
                rig = summon(helper, "ch-quit", beacon(false), 0);
                ServerPlayer quitter = rig.summoner();
                CalamityHeartContracts.CalamityHeartContractEvents.calamityHeartSummonerLogout(new PlayerEvent.PlayerLoggedOutEvent(quitter));
                helper.assertTrue(rig.heart().dissipating() && beacons(quitter) == 0 && CalamityHeartContracts.held(quitter).size() == 1,
                        "下线：信标留在他的存档里");
                CalamityHeartContracts.deliver(quitter);
                helper.assertTrue(beacons(quitter) == 1 && CalamityHeartContracts.held(quitter).isEmpty() && !CalamityBeaconItem.isSpent(beaconIn(quitter)),
                        "上线给回原样的信标");
            } finally {
                cleanup(helper, rig);
            }
        });
    }

    /** 召唤者的玩家对象不在（重启后读回来的契约）：每秒检查判失败，记进补交表；登录补一次，回档后（契约表退回到补交前）也不再给第二次 */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 200)
    public static void calamityHeartRefundLater(GameTestHelper helper) {
        whenSky(helper, () -> {
            scope(helper, local());
            Rig rig = null;
            try {
                rig = summon(helper, "ch-later", beacon(true), 2);
                ServerPlayer summoner = rig.summoner();
                MinecraftServer server = helper.getLevel().getServer();
                ECProtection.clearOnlineOverride(summoner.getUUID());
                CalamityHeartContracts data = CalamityHeartContracts.get(server);
                data.checkSummoners(server);
                helper.assertTrue(!CalamityHeartContracts.isOpen(helper.getLevel(), rig.heart().getUUID()) && data.pendingFor(summoner.getUUID()) == 1,
                        "不在线：判失败、记补交");
                helper.assertTrue(rig.heart().dissipating(), "首领开始散去");
                CompoundTag before = data.save(new CompoundTag());
                ECProtection.setOnlineOverride(summoner);
                data.deliverTo(summoner);
                helper.assertTrue(beacons(summoner) == 1 && cores(summoner) == 2 && data.pendingFor(summoner.getUUID()) == 0, "登录补上信标和核心");
                CalamityHeartContracts rolledBack = CalamityHeartContracts.load(before);
                helper.assertTrue(rolledBack.pendingFor(summoner.getUUID()) == 1, "回档后的表里还记着");
                rolledBack.deliverTo(summoner);
                helper.assertTrue(beacons(summoner) == 1 && cores(summoner) == 2, "他存档里记着结算过，不再给第二次");
            } finally {
                cleanup(helper, rig);
            }
        });
    }

    /**
     * 打倒：两名 ≥ 10% 的各一份进背包（残核 1 + 首杀 1、异变之心 2、主灾日阵闪耀榍石 4），不到一成的什么都没有、不记首杀；
     * spent 的信标回到召唤者背包、当天记一次（再召被拒）；第二次打倒（换个日子、spent 的信标扣 2 个核心）首杀不再 +1、别的主灾奖励不给
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 200)
    public static void calamityHeartDefeatSettles(GameTestHelper helper) {
        whenSky(helper, () -> {
            scope(helper, local());
            Rig rig = null;
            ServerPlayer ally = null;
            ServerPlayer idle = null;
            Rig again = null;
            try {
                rig = summon(helper, "ch-win", beacon(false), 2);
                CalamityHeartEntity heart = rig.heart();
                ServerPlayer summoner = rig.summoner();
                ally = player(helper, "ch-ally", new Vec3(3.5D, 1D, 7.5D), GameType.SURVIVAL);
                idle = player(helper, "ch-idle", new Vec3(7.5D, 1D, 3.5D), GameType.SURVIVAL);
                heart.bossRoster().add(ally);
                heart.bossRoster().add(idle);
                dropPillars(helper, heart);
                helper.assertTrue(heart.phase() == CalamityHeartEntity.Phase.UNBALANCED, "灾柱全倒：失衡");
                heart.bossRoster().noteDamage(melee(summoner), 800F);
                heart.bossRoster().noteDamage(melee(ally), 600F);
                heart.bossRoster().noteDamage(melee(idle), 40F);
                heart.setHealth(1F);
                hit(heart, melee(summoner), 5F);
                helper.assertTrue(heart.isDeadOrDying() && heart.defeatedByPlayers(), "打倒");
                Item remnant = ECCalamityHeart.CALAMITY_REMNANT.get();
                Item titanite = item("twinkling_titanite");
                for (ServerPlayer winner : List.of(summoner, ally)) {
                    helper.assertTrue(count(winner, remnant) == 2, "残核 1 + 首杀 1，实际 " + count(winner, remnant));
                    helper.assertTrue(count(winner, ECMobDrops.ABERRANT_HEART.get()) == 2, "异变之心 2");
                    helper.assertTrue(count(winner, titanite) >= 4 && count(winner, item("matter_of_eternity")) == 0,
                            "主灾日阵：闪耀榍石至少 4（遗物池里也可能抽到），地震的永恒物质没有");
                }
                helper.assertTrue(count(idle, remnant) == 0 && count(idle, titanite) == 0 && count(idle, ECMobDrops.ABERRANT_HEART.get()) == 0
                        && data(idle).getBossFirstKills().isEmpty(), "不到一成的没有、不记首杀");
                helper.assertTrue(beacons(summoner) == 1 && CalamityBeaconItem.isSpent(beaconIn(summoner)) && cores(summoner) == 2, "spent 的信标回到背包");
                helper.assertTrue(CalamityHeartContracts.defeatedToday(summoner, helper.getLevel().getServer()), "当天记一次");
                helper.assertFalse(CalamityHeartContracts.isOpen(helper.getLevel(), heart.getUUID()), "契约结清");
                // 背包里那个信标原地拿来用（主手那格可能是刚进背包的掉落）
                ItemStack spent = beaconIn(summoner);
                BlockPos at = helper.absolutePos(FLOOR);
                CalamityHeartSummon.Outcome daily = CalamityHeartSummon.summon(summoner, helper.getLevel(), at, spent);
                helper.assertTrue(daily.result() == CalamityHeartSummon.Result.DAILY, "当天再召被拒，实际 " + daily.result());

                data(summoner).getBossDaily().putLong(CalamityHeartContracts.DAILY_KEY, BossDaily.dayIndex(helper.getLevel().getServer()) - 1L);
                CalamityHeartSummon.setTestCalamity(HoannaEvent.EARTHQUAKE);
                CalamityHeartSummon.Outcome second = CalamityHeartSummon.summon(summoner, helper.getLevel(), at, spent);
                if (second.heart() != null) {
                    second.heart().setNoAi(true);
                }
                again = new Rig(summoner, second);
                helper.assertTrue(second.ok() && cores(summoner) == 0 && second.main() == CalamityHeartEntity.Aspect.EARTHQUAKE, "换个日子再召，扣 2 个，实际 " + second.result());
                CalamityHeartEntity heart2 = again.heart();
                dropPillars(helper, heart2);
                heart2.bossRoster().noteDamage(melee(summoner), 800F);
                heart2.setHealth(1F);
                hit(heart2, melee(summoner), 5F);
                helper.assertTrue(heart2.isDeadOrDying(), "第二只打倒");
                helper.assertTrue(count(summoner, remnant) == 3, "第二次残核只 +1，实际 " + count(summoner, remnant));
                helper.assertTrue(count(summoner, item("matter_of_eternity")) == 1, "这次是地震：永恒物质 1（遗物池里没有它）");
            } finally {
                cleanup(helper, again);
                cleanup(helper, rig, ally, idle);
            }
        });
    }

    /** 绕过无敌的非参战致死（/kill）：不掉东西、不记首杀、不算今天，按失败原样退还；刷怪蛋放的打倒什么都不给、不碰契约 */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 200)
    public static void calamityHeartKillNoLoot(GameTestHelper helper) {
        whenSky(helper, () -> {
            scope(helper, local());
            Rig rig = null;
            CalamityHeartEntity egg = null;
            try {
                rig = summon(helper, "ch-kill", beacon(false), 0);
                ServerPlayer summoner = rig.summoner();
                CalamityHeartEntity heart = rig.heart();
                heart.bossRoster().noteDamage(melee(summoner), 800F);
                BossFrameworkTests.asKillCommand(heart::kill);
                helper.assertTrue(heart.isDeadOrDying() && !heart.defeatedByPlayers(), "/kill 杀得死，不算玩家打倒");
                helper.assertTrue(count(summoner, ECCalamityHeart.CALAMITY_REMNANT.get()) == 0 && data(summoner).getBossFirstKills().isEmpty(), "不掉东西、不记首杀");
                helper.assertTrue(beacons(summoner) == 1 && !CalamityBeaconItem.isSpent(beaconIn(summoner)), "按失败退还：信标原样");
                helper.assertFalse(CalamityHeartContracts.defeatedToday(summoner, helper.getLevel().getServer()), "不算今天");

                int open = CalamityHeartContracts.get(helper.getLevel()).openCount();
                egg = ECCalamityHeart.CALAMITY_HEART.get().spawn(helper.getLevel(), helper.absolutePos(new BlockPos(5, 1, 5)), MobSpawnType.SPAWN_EGG);
                helper.assertTrue(egg != null && !egg.bound() && egg.summoner() == null, "刷怪蛋放的没有召唤者");
                egg.setNoAi(true);
                egg.syncPillars(helper.getLevel());
                dropPillars(helper, egg);
                egg.bossRoster().add(summoner);
                egg.bossRoster().noteDamage(melee(summoner), 800F);
                egg.setHealth(1F);
                hit(egg, melee(summoner), 5F);
                helper.assertTrue(egg.isDeadOrDying() && count(summoner, ECCalamityHeart.CALAMITY_REMNANT.get()) == 0, "刷怪蛋放的打倒了什么都不掉");
                helper.assertTrue(CalamityHeartContracts.get(helper.getLevel()).openCount() == open, "契约表不变");
            } finally {
                cleanup(helper, rig, egg);
            }
        });
    }

    // ================================================================== 战斗

    /**
     * 阶段：护盾期打不动；打掉一根柱子：破盾窗口打得动、但打不到 20% 以下，那一相放残响；四根倒完失衡；&lt; 20% 崩解（受伤 ×1.25、每 5 刻脉冲）；
     * 第一次倒计时走完重立 2 根、回到 35%；第二次走完一直脉冲、不再重立，打得死
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 200)
    public static void calamityHeartPhases(GameTestHelper helper) {
        whenSky(helper, () -> {
            scope(helper, local());
            Rig rig = null;
            try {
                rig = summon(helper, "ch-phase", beacon(false), 0);
                CalamityHeartEntity heart = rig.heart();
                ServerPlayer summoner = rig.summoner();
                ServerLevel level = helper.getLevel();
                heart.serverBossStep();
                helper.assertTrue(heart.skyRecipients() == 1, "召唤者收到天色画面");
                helper.assertTrue(heart.phase() == CalamityHeartEntity.Phase.SHIELD && heart.activeAspects().size() == 4, "护盾期四相");
                CalamityPillarEntity sun = heart.pillar(CalamityHeartEntity.Aspect.SUN_ARRAY.ordinal());
                helper.assertTrue(sun != null && hit(sun, melee(summoner), 100F) && Math.abs(sun.getHealth() - 320F) < EPS, "打柱子，单次封顶 40");
                BossFrameworkTests.asKillCommand(sun::kill);
                helper.assertTrue(heart.shieldOpen() && heart.pillarsStanding() == 3, "倒一根：破盾");
                helper.assertTrue(heart.heartSkills().pending(CalamityHeartSkills.Skill.NOON) == 1, "放日阵的残响 · 正午");
                helper.assertTrue(!heart.activeAspects().contains(CalamityHeartEntity.Aspect.SUN_ARRAY), "倒了的那一相不再出招");
                heart.heartSkills().cancelCast();
                float floor = heart.getMaxHealth() * 0.2F;
                heart.setHealth(floor + 10F);
                helper.assertTrue(hit(heart, melee(summoner), 100F) && heart.getHealth() >= floor - EPS, "破盾窗口打得动，但打不到崩解线以下，实际 " + heart.getHealth());

                dropPillars(helper, heart);
                helper.assertTrue(heart.phase() == CalamityHeartEntity.Phase.UNBALANCED && Math.abs(heart.cooldownMultiplier() - 0.7D) < EPS
                        && heart.activeAspects().size() == 2, "失衡：两相、冷却 ×0.7");
                heart.setHealth(floor - 1F);
                heart.tickPhases(level);
                helper.assertTrue(heart.phase() == CalamityHeartEntity.Phase.COLLAPSE && heart.collapseStage() == 1, "崩解");
                float before = heart.getHealth();
                hit(heart, melee(summoner), 8F);
                float expect = CombatRules.getDamageAfterAbsorb(10F, (float) heart.getArmorValue(), (float) heart.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
                helper.assertTrue(Math.abs(before - heart.getHealth() - expect) < 0.05F, "崩解时受伤 ×1.25（8 → 10，再过护甲），实际 " + (before - heart.getHealth()));
                boolean pulsed = false;
                for (int i = 0; i < 10; i++) {
                    heart.tickCollapse(level);
                    pulsed |= summoner.getHealth() < summoner.getMaxHealth();
                    summoner.setHealth(summoner.getMaxHealth());
                }
                helper.assertTrue(pulsed, "崩解脉冲打参战者");
                helper.assertTrue(heart.collapseStage() == 2 && heart.pillarsStanding() == 2 && heart.phase() == CalamityHeartEntity.Phase.SHIELD
                        && Math.abs(heart.getHealth() - heart.getMaxHealth() * 0.35F) < 0.5F, "第一次走完：重立 2 根、回到 35%");

                dropPillars(helper, heart);
                heart.setHealth(floor - 1F);
                heart.tickPhases(level);
                helper.assertTrue(heart.collapseStage() == 3, "第二次崩解");
                for (int i = 0; i < 30; i++) {
                    heart.tickCollapse(level);
                    summoner.setHealth(summoner.getMaxHealth());
                }
                helper.assertTrue(heart.collapseStage() == 4 && heart.pillarsStanding() == 0 && heart.phase() == CalamityHeartEntity.Phase.COLLAPSE,
                        "第二次走完：一直脉冲、不再重立");
                heart.bossRoster().noteDamage(melee(summoner), 800F);
                heart.setHealth(1F);
                hit(heart, melee(summoner), 5F);
                helper.assertTrue(heart.isDeadOrDying() && heart.defeatedByPlayers(), "之后打得死");
            } finally {
                cleanup(helper, rig);
            }
        });
    }

    /** 灾柱读档重建：剩余血量记在首领身上，读回来重新立起时血量、主灾那根 360 都对；倒了的不重立 */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 200)
    public static void calamityHeartPillarsReload(GameTestHelper helper) {
        whenSky(helper, () -> {
            scope(helper, local());
            Rig rig = null;
            CalamityHeartEntity copy = null;
            List<CalamityPillarEntity> rebuilt = new ArrayList<>();
            try {
                rig = summon(helper, "ch-load", beacon(false), 0);
                CalamityHeartEntity heart = rig.heart();
                ServerPlayer summoner = rig.summoner();
                CalamityPillarEntity fumes = heart.pillar(CalamityHeartEntity.Aspect.FUMES.ordinal());
                hit(fumes, melee(summoner), 30F);
                CalamityPillarEntity quake = heart.pillar(CalamityHeartEntity.Aspect.EARTHQUAKE.ordinal());
                BossFrameworkTests.asKillCommand(quake::kill);
                heart.heartSkills().cancelCast();
                heart.syncPillars(helper.getLevel());
                CompoundTag saved = heart.saveWithoutId(new CompoundTag());
                for (CalamityPillarEntity pillar : helper.getLevel().getEntitiesOfClass(CalamityPillarEntity.class,
                        new AABB(helper.absolutePos(FLOOR)).inflate(8D))) {
                    helper.assertFalse(pillar.shouldBeSaved(), "灾柱不存盘");
                }
                copy = ECCalamityHeart.CALAMITY_HEART.get().create(helper.getLevel());
                copy.load(saved);
                helper.assertTrue(Math.abs(copy.pillarHealth(1) - 210F) < EPS && copy.pillarHealth(3) == 0F && Math.abs(copy.pillarHealth(0) - 360F) < EPS,
                        "剩余血量读回来，实际 " + copy.pillarHealth(0) + " / " + copy.pillarHealth(1) + " / " + copy.pillarHealth(3));
                helper.assertTrue(copy.mainAspect() == CalamityHeartEntity.Aspect.SUN_ARRAY && copy.bound(), "主灾、召唤者读回来");
                copy.syncPillars(helper.getLevel());
                for (int i = 0; i < 4; i++) {
                    CalamityPillarEntity pillar = copy.pillar(i);
                    if (pillar != null) {
                        rebuilt.add(pillar);
                    }
                }
                helper.assertTrue(rebuilt.size() == 3 && copy.pillar(3) == null, "倒了的不重立");
                CalamityPillarEntity main = copy.pillar(0);
                CalamityPillarEntity hurt = copy.pillar(1);
                helper.assertTrue(main != null && main.main() && Math.abs(main.getMaxHealth() - 360F) < EPS, "主灾那根 360");
                helper.assertTrue(hurt != null && Math.abs(hurt.getHealth() - 210F) < EPS && Math.abs(hurt.getMaxHealth() - 240F) < EPS, "挨过打的那根 210 / 240");
            } finally {
                for (CalamityPillarEntity pillar : rebuilt) {
                    pillar.discard();
                }
                cleanup(helper, rig);
            }
        });
    }

    /** 毒雾只打参战者：旁边的村民、狼、非参战玩家都不中毒；一次放的不超过上限；不存盘 */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 200)
    public static void calamityHeartFumesOnlyFighters(GameTestHelper helper) {
        whenSky(helper, () -> {
            scope(helper, local());
            Rig rig = null;
            ServerPlayer outsider = null;
            Villager villager = null;
            Wolf wolf = null;
            try {
                rig = summon(helper, "ch-fume", beacon(false), 0);
                CalamityHeartEntity heart = rig.heart();
                ServerPlayer fighter = rig.summoner();
                outsider = player(helper, "ch-out", new Vec3(8.5D, 1D, 7.5D), GameType.SURVIVAL);
                villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(7, 1, 8));
                wolf = helper.spawnWithNoFreeWill(EntityType.WOLF, new BlockPos(8, 1, 8));
                int placed = heart.heartSkills().fumes(helper.getLevel(), CalamityHeartSkills.KIND_FUME, 6, 10, 400);
                helper.assertTrue(placed == 6 && heart.skills().count(CalamityHeartSkills.KIND_FUME) == 6, "一次最多 6 团，实际 " + placed);
                PoisonFumeEntity fume = (PoisonFumeEntity) heart.skills().alive(CalamityHeartSkills.KIND_FUME).get(0);
                moveTo(helper, fume, new Vec3(7.5D, 1.5D, 7.5D));
                helper.assertTrue(fume.boss() == heart && !fume.shouldBeSaved(), "绑在首领上、不存盘");
                helper.assertTrue(fume.affectPlayers(List.of(fighter, outsider)) == 1, "只毒到参战者");
                helper.assertTrue(fighter.hasEffect(MobEffects.POISON) && !outsider.hasEffect(MobEffects.POISON), "参战者中毒，非参战玩家没有");
                for (int i = 0; i < 25; i++) {
                    fume.tick();
                }
                helper.assertTrue(!villager.hasEffect(MobEffects.POISON) && !wolf.hasEffect(MobEffects.POISON) && !outsider.hasEffect(MobEffects.POISON),
                        "村民、狼、非参战玩家都没中毒");
            } finally {
                cleanup(helper, rig, outsider, villager, wolf);
            }
        });
    }

    /** 烈日标记：头顶 +2 格露天的挨 20% 最大生命（calamity_solar，护甲挡不住），钻到方块下的躲开 */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 200)
    public static void calamityHeartMarkCover(GameTestHelper helper) {
        flatten(helper);
        BlockPos roof = new BlockPos(2, 3, 8);
        helper.setBlock(roof, Blocks.STONE);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertFalse(helper.getLevel().canSeeSky(helper.absolutePos(roof)), "等屋顶的光照更新"))
                .thenWaitUntil(() -> helper.assertTrue(helper.getLevel().canSeeSky(helper.absolutePos(FLOOR.above())), "等召唤点露天"))
                .thenExecute(() -> {
                    scope(helper, local(quickSkills()));
                    Rig rig = null;
                    ServerPlayer covered = null;
                    ServerPlayer exposed = player(helper, "ch-mark", STAND, GameType.SURVIVAL);
                    Difficulty old = setDifficulty(helper, Difficulty.NORMAL);
                    try {
                        exposed.setItemInHand(InteractionHand.MAIN_HAND, beacon(false));
                        rig = new Rig(exposed, use(helper, exposed));
                        CalamityHeartEntity heart = rig.heart();
                        covered = player(helper, "ch-cover", new Vec3(2.5D, 1D, 8.5D), GameType.SURVIVAL);
                        heart.bossRoster().add(covered);
                        exposed.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
                        heart.heartSkills().start(CalamityHeartSkills.Skill.MARK, exposed);
                        heart.heartSkills().start(CalamityHeartSkills.Skill.MARK, covered);
                        for (int i = 0; i < 5; i++) {
                            heart.heartSkills().tickEffects(helper.getLevel());
                        }
                        float lost = exposed.getMaxHealth() - exposed.getHealth();
                        helper.assertTrue(Math.abs(lost - exposed.getMaxHealth() * 0.2F) < 0.05F, "露天的挨 20%（护甲挡不住），实际 " + lost);
                        helper.assertTrue(covered.getHealth() == covered.getMaxHealth(), "方块下的躲开");
                    } finally {
                        setDifficulty(helper, old);
                        cleanup(helper, rig, covered, exposed);
                    }
                })
                .thenSucceed();
    }

    /** 落石：同时不超过上限（3 块）；落地砸中参战者、不放方块、落地即消失、不存盘 */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 200)
    public static void calamityHeartRubble(GameTestHelper helper) {
        whenSky(helper, () -> {
            scope(helper, local(quickSkills()));
            Rig rig = null;
            try {
                rig = summon(helper, "ch-rock", beacon(false), 0);
                CalamityHeartEntity heart = rig.heart();
                ServerPlayer fighter = rig.summoner();
                heart.heartSkills().start(CalamityHeartSkills.Skill.RUBBLE, fighter);
                List<Entity> rocks = new ArrayList<>(heart.skills().alive(CalamityHeartSkills.KIND_RUBBLE));
                helper.assertTrue(rocks.size() == 3, "同时最多 3 块，实际 " + rocks.size());
                for (Entity rock : rocks) {
                    helper.assertFalse(rock.shouldBeSaved(), "落石不存盘");
                }
                CalamityRubbleEntity first = (CalamityRubbleEntity) rocks.get(0);
                BlockPos landing = BlockPos.containing(first.getX(), first.groundY(), first.getZ());
                for (int i = 0; i < 25; i++) {
                    for (Entity rock : rocks) {
                        if (!rock.isRemoved()) {
                            rock.tick();
                        }
                    }
                }
                helper.assertTrue(rocks.stream().allMatch(Entity::isRemoved), "落地即消失");
                helper.assertTrue(fighter.getHealth() < fighter.getMaxHealth(), "砸中站在落点的人");
                helper.assertTrue(helper.getLevel().getBlockState(landing).isAir(), "不放方块");
            } finally {
                cleanup(helper, rig);
            }
        });
    }

    /** 熄光：预警后熄光、首领带熄光标记，期间一次暗袭（站着不动的挨、跑开的不挨），到点自己结束；拿火把的人算拿着光源 */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 200)
    public static void calamityHeartBlackout(GameTestHelper helper) {
        whenSky(helper, () -> {
            scope(helper, local(quickSkills()));
            Rig rig = null;
            try {
                rig = summon(helper, "ch-dark", beacon(false), 0);
                CalamityHeartEntity heart = rig.heart();
                ServerPlayer fighter = rig.summoner();
                fighter.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TORCH));
                helper.assertTrue(CalamityHeartEntity.holdsLight(fighter), "副手火把算拿着光源");
                heart.heartSkills().start(CalamityHeartSkills.Skill.BLACKOUT, fighter);
                helper.assertTrue(heart.heartSkills().blackoutActive() && !heart.blackout(), "先预警");
                for (int i = 0; i < 3; i++) {
                    heart.heartSkills().tickEffects(helper.getLevel());
                }
                helper.assertTrue(heart.blackout(), "熄光开始");
                for (int i = 0; i < 11; i++) {
                    heart.heartSkills().tickEffects(helper.getLevel());
                }
                helper.assertTrue(heart.heartSkills().pending(CalamityHeartSkills.Skill.BLACKOUT) == 1, "过半时起一次暗袭");
                for (int i = 0; i < 4; i++) {
                    heart.heartSkills().tickEffects(helper.getLevel());
                }
                helper.assertTrue(fighter.getHealth() < fighter.getMaxHealth(), "站着不动的挨暗袭");
                for (int i = 0; i < 10; i++) {
                    heart.heartSkills().tickEffects(helper.getLevel());
                }
                helper.assertTrue(!heart.blackout() && !heart.heartSkills().blackoutActive(), "到点结束");

                fighter.setHealth(fighter.getMaxHealth());
                heart.heartSkills().start(CalamityHeartSkills.Skill.BLACKOUT, fighter);
                for (int i = 0; i < 14; i++) {
                    heart.heartSkills().tickEffects(helper.getLevel());
                }
                helper.assertTrue(heart.heartSkills().pending(CalamityHeartSkills.Skill.BLACKOUT) == 1, "第二次的暗袭起手");
                moveTo(helper, fighter, new Vec3(1.5D, 1D, 8.5D));
                for (int i = 0; i < 20; i++) {
                    heart.heartSkills().tickEffects(helper.getLevel());
                }
                helper.assertTrue(fighter.getHealth() == fighter.getMaxHealth(), "跑开的不挨");
            } finally {
                cleanup(helper, rig);
            }
        });
    }

    /** 震波：扫过脚踩地面的参战者（伤害 + 抛起），跳起来（不在地面）的躲开；每人一圈只挨一次 */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 200)
    public static void calamityHeartShockwave(GameTestHelper helper) {
        whenSky(helper, () -> {
            scope(helper, local(quickSkills()));
            Rig rig = null;
            ServerPlayer jumper = null;
            Difficulty old = setDifficulty(helper, Difficulty.NORMAL);
            try {
                rig = summon(helper, "ch-quake", beacon(false), 0);
                CalamityHeartEntity heart = rig.heart();
                ServerPlayer ground = rig.summoner();
                jumper = player(helper, "ch-jump", new Vec3(3.5D, 1D, 3.5D), GameType.SURVIVAL);
                heart.bossRoster().add(jumper);
                jumper.setOnGround(false);
                ground.setOnGround(true);
                heart.heartSkills().start(CalamityHeartSkills.Skill.SHOCKWAVE, ground);
                for (int i = 0; i < 20; i++) {
                    heart.heartSkills().tickEffects(helper.getLevel());
                }
                float lost = ground.getMaxHealth() - ground.getHealth();
                float expect = (float) (12D + ground.getMaxHealth() * 0.05D);
                helper.assertTrue(Math.abs(lost - expect) < 0.05F, "踩地面的挨一次 12 + 5%，实际 " + lost);
                helper.assertTrue(ground.getDeltaMovement().y > 0D, "被抛起");
                helper.assertTrue(jumper.getHealth() == jumper.getMaxHealth(), "跳起来的躲开");
            } finally {
                setDifficulty(helper, old);
                cleanup(helper, rig, jumper);
            }
        });
    }

    /** 天光棱线（12 + 6%、着火、护甲挡不住）和腐蚀吐息（10 + 4%、腐蚀、只打锥形里的人） */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 200)
    public static void calamityHeartPrismAndBreath(GameTestHelper helper) {
        whenSky(helper, () -> {
            scope(helper, local(quickSkills()));
            Rig rig = null;
            ServerPlayer behind = null;
            Difficulty old = setDifficulty(helper, Difficulty.NORMAL);
            try {
                rig = summon(helper, "ch-prism", beacon(false), 0);
                CalamityHeartEntity heart = rig.heart();
                ServerPlayer target = rig.summoner();
                target.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
                heart.heartSkills().start(CalamityHeartSkills.Skill.PRISM, target);
                for (int i = 0; i < 5; i++) {
                    heart.heartSkills().tickEffects(helper.getLevel());
                }
                float lost = target.getMaxHealth() - target.getHealth();
                helper.assertTrue(Math.abs(lost - (12F + target.getMaxHealth() * 0.06F)) < 0.05F && target.getRemainingFireTicks() > 0,
                        "棱线 12 + 6%、护甲挡不住、着火，实际 " + lost);
                target.clearFire();
                target.setHealth(target.getMaxHealth());

                behind = player(helper, "ch-behind", new Vec3(2.5D, 1D, 2.5D), GameType.SURVIVAL);
                heart.bossRoster().add(behind);
                heart.heartSkills().start(CalamityHeartSkills.Skill.BREATH, target);
                for (int i = 0; i < 3; i++) {
                    heart.heartSkills().tickEffects(helper.getLevel());
                }
                lost = target.getMaxHealth() - target.getHealth();
                helper.assertTrue(Math.abs(lost - (10F + target.getMaxHealth() * 0.04F)) < 0.05F && target.hasEffect(ECEffects.CORROSION.get()),
                        "吐息 10 + 4%、腐蚀，实际 " + lost);
                helper.assertTrue(behind.getHealth() == behind.getMaxHealth() && !behind.hasEffect(ECEffects.CORROSION.get()), "背后的人不挨");
            } finally {
                setDifficulty(helper, old);
                cleanup(helper, rig, behind);
            }
        });
    }

    // ================================================================== 召唤者离开、和平、画面

    /** 召唤者死了：信标留在他存档里、重生时给（不掉在死的地方）；换维度：当场退回；两种都让首领散去、当天不算 */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 200)
    public static void calamityHeartSummonerLeaves(GameTestHelper helper) {
        whenSky(helper, () -> {
            scope(helper, local());
            Rig rig = null;
            try {
                rig = summon(helper, "ch-died", beacon(true), 2);
                ServerPlayer dying = rig.summoner();
                dying.setHealth(0F);
                CalamityHeartContracts.CalamityHeartContractEvents.calamityHeartSummonerDeath(new LivingDeathEvent(dying, dying.damageSources().generic()));
                helper.assertTrue(rig.heart().dissipating() && beacons(dying) == 0 && CalamityHeartContracts.held(dying).size() == 2,
                        "死了：信标和核心留在他存档里，首领散去");
                dying.setHealth(dying.getMaxHealth());
                CalamityHeartContracts.CalamityHeartContractEvents.calamityHeartRespawn(new PlayerEvent.PlayerRespawnEvent(dying, false));
                helper.assertTrue(beacons(dying) == 1 && cores(dying) == 2 && CalamityBeaconItem.isSpent(beaconIn(dying)), "重生时给回 spent 的信标和 2 个核心");
                CalamityHeartContracts.deliver(dying);
                helper.assertTrue(beacons(dying) == 1 && cores(dying) == 2, "不重复给");
                cleanup(helper, rig);

                scope(helper, local());
                rig = summon(helper, "ch-away", beacon(false), 0);
                ServerPlayer away = rig.summoner();
                CalamityHeartContracts.CalamityHeartContractEvents.calamityHeartSummonerDimension(
                        new PlayerEvent.PlayerChangedDimensionEvent(away, Level.OVERWORLD, Level.NETHER));
                helper.assertTrue(rig.heart().dissipating() && beacons(away) == 1 && !CalamityBeaconItem.isSpent(beaconIn(away)), "换维度：当场退回原样的信标");
                helper.assertFalse(CalamityHeartContracts.defeatedToday(away, helper.getLevel().getServer()), "当天不算");
            } finally {
                cleanup(helper, rig);
            }
        });
    }

    /** 和平难度：召出来的那只按失败结算（信标退回、散去），不是直接消失；散去途中打不动、不出招 */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 200)
    public static void calamityHeartPeacefulFails(GameTestHelper helper) {
        whenSky(helper, () -> {
            scope(helper, local());
            Rig rig = null;
            try {
                rig = summon(helper, "ch-peace", beacon(false), 0);
                CalamityHeartEntity heart = rig.heart();
                Difficulty old = setDifficulty(helper, Difficulty.PEACEFUL);
                try {
                    heart.serverBossStep();
                } finally {
                    setDifficulty(helper, old);
                }
                helper.assertTrue(!heart.isRemoved() && heart.dissipating(), "和平：开始散去，不是直接消失");
                helper.assertTrue(beacons(rig.summoner()) == 1 && !CalamityHeartContracts.isOpen(helper.getLevel(), heart.getUUID()), "信标退回、契约删掉");
                helper.assertTrue(heart.isInvulnerableTo(melee(rig.summoner())) && heart.activeAspects().isEmpty(), "散去途中打不动、不出招");
                helper.assertTrue(heart.pillarsStanding() == 4 && heart.pillar(0) == null, "灾柱跟着收掉（血量还记着，不会再立）");
            } finally {
                cleanup(helper, rig);
            }
        });
    }

    /** 画面：后进名单的人补发天色压暗；离开名单的人从「收到过」里删掉、再进名单重新补发；打倒 / 散去时清空 */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 200)
    public static void calamityHeartFxRecipients(GameTestHelper helper) {
        whenSky(helper, () -> {
            scope(helper, local());
            Rig rig = null;
            ServerPlayer late = null;
            try {
                rig = summon(helper, "ch-fx", beacon(false), 0);
                CalamityHeartEntity heart = rig.heart();
                heart.serverBossStep();
                helper.assertTrue(heart.skyRecipients() == 1, "召唤者收到");
                late = player(helper, "ch-late", new Vec3(3.5D, 1D, 7.5D), GameType.SURVIVAL);
                heart.bossRoster().add(late);
                for (int i = 0; i < 10; i++) {
                    heart.tickCount++;
                    heart.serverBossStep();
                }
                helper.assertTrue(heart.skyRecipients() == 2, "后进名单的人补发一次");
                BossRoster.forgetFighter(late.getUUID());
                helper.assertTrue(heart.skyRecipients() == 1 && !heart.bossRoster().contains(late.getUUID()), "离开名单的人删掉");
                heart.bossRoster().add(late);
                for (int i = 0; i < 10; i++) {
                    heart.tickCount++;
                    heart.serverBossStep();
                }
                helper.assertTrue(heart.skyRecipients() == 2, "再进名单重新补发");
                heart.endBoss(ECBoss.BossEnd.ABANDONED);
                helper.assertTrue(heart.skyRecipients() == 0, "散去时清空");
            } finally {
                cleanup(helper, rig, late);
            }
        });
    }
    // ================================================================== 13e 补口：结算打死召唤者、崩解脉冲、灾柱被拦、每日的钟

    /**
     * 招式结算时把召唤者打死：死亡事件在同一刻同步走契约失败 → 散去 → 清空预警中的招。遍历不能因此抛异常（以前是 ConcurrentModificationException、
     * 实体 tick 里抛出就崩服）；契约按 SUMMONER_DIED 退还（人死了，信标先记在他存档里），首领散去，后面的招不再结算。
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 200)
    public static void calamityHeartEffectKillsSummoner(GameTestHelper helper) {
        whenSky(helper, () -> {
            scope(helper, local(quickSkills()));
            Rig rig = null;
            Difficulty old = setDifficulty(helper, Difficulty.NORMAL);
            try {
                rig = summon(helper, "ch-fatal", beacon(false), 0);
                CalamityHeartEntity heart = rig.heart();
                ServerPlayer summoner = rig.summoner();
                summoner.setOnGround(true);
                summoner.setHealth(1F);
                heart.heartSkills().start(CalamityHeartSkills.Skill.SHOCKWAVE, summoner);
                heart.heartSkills().start(CalamityHeartSkills.Skill.PRISM, summoner);
                RuntimeException thrown = null;
                try {
                    for (int i = 0; i < 20; i++) {
                        heart.heartSkills().tickEffects(helper.getLevel());
                    }
                } catch (RuntimeException e) {
                    thrown = e;
                }
                helper.assertTrue(thrown == null, "结算时打死召唤者不能抛异常：" + thrown);
                helper.assertTrue(summoner.isDeadOrDying(), "前提：召唤者被招式打死");
                helper.assertFalse(CalamityHeartContracts.isOpen(helper.getLevel(), heart.getUUID()), "契约按失败了结");
                helper.assertTrue(heart.dissipating(), "首领开始散去");
                helper.assertTrue(CalamityHeartContracts.held(summoner).size() == 1, "信标记在他存档里等重生，实际 " + CalamityHeartContracts.held(summoner).size());
                helper.assertTrue(heart.heartSkills().pending(CalamityHeartSkills.Skill.PRISM) == 0
                        && heart.heartSkills().pending(CalamityHeartSkills.Skill.SHOCKWAVE) == 0, "散去后不再有预警中的招");
            } finally {
                setDifficulty(helper, old);
                cleanup(helper, rig);
            }
        });
    }

    /** 崩解的最后一下脉冲正好打死召唤者：契约退还、开始散去，这一刻不再往下重立灾柱、回血 */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 200)
    public static void calamityHeartPulseKillsSummonerNoReraise(GameTestHelper helper) {
        whenSky(helper, () -> {
            scope(helper, new ECCalamityHeart.Config.Values(summonCfg(true, 2), fightCfg(400, 5, 5), DEFAULTS.skills(), QUIET));
            Rig rig = null;
            Difficulty old = setDifficulty(helper, Difficulty.NORMAL);
            try {
                rig = summon(helper, "ch-pulse", beacon(false), 0);
                CalamityHeartEntity heart = rig.heart();
                ServerPlayer summoner = rig.summoner();
                ServerLevel level = helper.getLevel();
                dropPillars(helper, heart);
                heart.startCollapse(level);
                helper.assertTrue(heart.collapseStage() == 1, "前提：第一次崩解");
                for (int i = 0; i < 4; i++) {
                    heart.tickCollapse(level);
                    summoner.setHealth(summoner.getMaxHealth());
                }
                summoner.setHealth(1F);
                heart.tickCollapse(level);
                helper.assertTrue(summoner.isDeadOrDying(), "前提：最后一下脉冲打死召唤者");
                helper.assertTrue(heart.dissipating() && !CalamityHeartContracts.isOpen(level, heart.getUUID()), "契约退还、开始散去");
                helper.assertTrue(heart.collapseStage() == 1 && heart.pillarsStanding() == 0 && heart.pillarsSpawned() == 0,
                        "不再重立灾柱，实际阶段 " + heart.collapseStage() + "、灾柱 " + heart.pillarsStanding());
            } finally {
                setDifficulty(helper, old);
                cleanup(helper, rig);
            }
        });
    }

    /**
     * 崩解脉冲走自己的计时：照真实的每刻入口（serverBossStep）推 200 刻，pulse_interval = 100 时正好两次；
     * 进崩解时战斗计时是奇数、偶数都一样（以前同一刻加两次，偶数时 2 倍、奇数时一次都没有）
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 200)
    public static void calamityHeartCollapsePulseCadence(GameTestHelper helper) {
        whenSky(helper, () -> {
            scope(helper, new ECCalamityHeart.Config.Values(summonCfg(true, 2), fightCfg(400, 1000, 100), DEFAULTS.skills(), QUIET));
            Rig rig = null;
            try {
                rig = summon(helper, "ch-cadence", beacon(false), 0);
                CalamityHeartEntity heart = rig.heart();
                ServerPlayer summoner = rig.summoner();
                ServerLevel level = helper.getLevel();
                dropPillars(helper, heart);
                heart.startCollapse(level);
                int firstParity = heart.fightTicks() & 1;
                int first = pulsesOver(heart, summoner, 200);
                helper.assertTrue(first == 2, "200 刻两次脉冲，实际 " + first);
                heart.serverBossStep();
                summoner.setHealth(summoner.getMaxHealth());
                heart.startCollapse(level);
                helper.assertTrue(heart.collapseStage() == 3 && (heart.fightTicks() & 1) != firstParity, "前提：第二次崩解、战斗计时奇偶和第一次不同");
                int second = pulsesOver(heart, summoner, 200);
                helper.assertTrue(second == 2, "奇偶不同照样两次，实际 " + second);
            } finally {
                cleanup(helper, rig);
            }
        });
    }

    private static int pulsesOver(CalamityHeartEntity heart, ServerPlayer summoner, int ticks) {
        int pulses = 0;
        for (int i = 0; i < ticks; i++) {
            heart.serverBossStep();
            if (summoner.getHealth() < summoner.getMaxHealth()) {
                pulses++;
            }
            summoner.setHealth(summoner.getMaxHealth());
        }
        return pulses;
    }

    /** 灾柱的加入事件被插件拦下：召唤时缺一根就不召（什么都不扣、不记契约、本体和别的灾柱都收回） */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 200)
    public static void calamityHeartPillarsBlockedAtSummon(GameTestHelper helper) {
        whenSky(helper, () -> {
            scope(helper, local());
            Consumer<EntityJoinLevelEvent> cancel = event -> {
                if (event.getEntity() instanceof CalamityPillarEntity pillar && pillar.index() == 2) {
                    event.setCanceled(true);
                }
            };
            Rig rig = null;
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EntityJoinLevelEvent.class, cancel);
            try {
                rig = summon(helper, "ch-nopillar", beacon(true), 2);
            } finally {
                MinecraftForge.EVENT_BUS.unregister(cancel);
            }
            try {
                helper.assertTrue(rig.outcome().result() == CalamityHeartSummon.Result.SPAWN_FAILED, "灾柱立不起来：不召，实际 " + rig.outcome().result());
                helper.assertTrue(beacons(rig.summoner()) == 1 && cores(rig.summoner()) == 2, "信标、核心都不扣");
                helper.assertTrue(CalamityHeartContracts.get(helper.getLevel()).contractsOf(rig.summoner().getUUID()).isEmpty(), "不记契约");
                AABB around = new AABB(helper.absolutePos(FLOOR)).inflate(16D);
                helper.assertTrue(helper.getLevel().getEntitiesOfClass(CalamityHeartEntity.class, around, Entity::isAlive).isEmpty()
                        && helper.getLevel().getEntitiesOfClass(CalamityPillarEntity.class, around, Entity::isAlive).isEmpty(), "本体和立起来的灾柱都收回了");
            } finally {
                cleanup(helper, rig);
            }
        });
    }

    /**
     * 战斗中重立灾柱被拦下：隔 PILLAR_RETRY_TICKS 才再试（不每刻发生成事件），连着 PILLAR_MAX_REJECTS 次立不起来就按失败退还、散去，
     * 不留一个柱子有血、却没有实体可打的永久护盾
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 200)
    public static void calamityHeartPillarsBlockedInFight(GameTestHelper helper) {
        whenSky(helper, () -> {
            scope(helper, local());
            int[] attempts = {0};
            Consumer<EntityJoinLevelEvent> cancel = event -> {
                if (event.getEntity() instanceof CalamityPillarEntity) {
                    attempts[0]++;
                    event.setCanceled(true);
                }
            };
            Rig rig = null;
            try {
                rig = summon(helper, "ch-reraise-x", beacon(false), 0);
                CalamityHeartEntity heart = rig.heart();
                ServerPlayer summoner = rig.summoner();
                ServerLevel level = helper.getLevel();
                CalamityPillarEntity gone = heart.pillar(1);
                helper.assertTrue(gone != null, "前提：四根都立着");
                gone.discard();
                MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EntityJoinLevelEvent.class, cancel);
                try {
                    heart.syncPillars(level);
                    heart.syncPillars(level);
                    helper.assertTrue(attempts[0] == 1, "被拦下后同一段时间里不再试，实际 " + attempts[0]);
                    heart.advanceFightTicksForTest(CalamityHeartEntity.PILLAR_RETRY_TICKS);
                    heart.syncPillars(level);
                    helper.assertTrue(attempts[0] == 2 && CalamityHeartContracts.isOpen(level, heart.getUUID()), "隔一段再试，还没到次数");
                    heart.advanceFightTicksForTest(CalamityHeartEntity.PILLAR_RETRY_TICKS);
                    heart.syncPillars(level);
                } finally {
                    MinecraftForge.EVENT_BUS.unregister(cancel);
                }
                helper.assertTrue(attempts[0] == CalamityHeartEntity.PILLAR_MAX_REJECTS, "一共试了 " + attempts[0] + " 次");
                helper.assertFalse(CalamityHeartContracts.isOpen(level, heart.getUUID()), "连着立不起来：契约按失败了结");
                helper.assertTrue(heart.dissipating() && beacons(summoner) == 1 && !CalamityBeaconItem.isSpent(beaconIn(summoner)), "散去、信标原样退回");
                helper.assertFalse(CalamityHeartContracts.defeatedToday(summoner, level.getServer()), "当天不算");
            } finally {
                cleanup(helper, rig);
            }
        });
    }

    /** 名下已有一份开着的契约时不能再召（不扣东西）；那份了结后照常能召 */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 200)
    public static void calamityHeartOneContractAtATime(GameTestHelper helper) {
        whenSky(helper, () -> {
            scope(helper, local());
            Rig rig = null;
            ServerPlayer player = null;
            CalamityHeartEntity elsewhere = null;
            try {
                player = player(helper, "ch-twice", STAND, GameType.SURVIVAL);
                player.setItemInHand(InteractionHand.MAIN_HAND, beacon(true));
                player.getInventory().add(new ItemStack(ECShade.SHADE_CORE.get(), 2));
                // 128 格外另一处召出来、还在打的那只（不放进世界，只记契约）
                elsewhere = ECCalamityHeart.CALAMITY_HEART.get().create(helper.getLevel());
                CalamityHeartContracts.open(helper.getLevel(), elsewhere, player, helper.absolutePos(FLOOR).offset(400, 0, 0), beacon(false), 0);
                CalamityHeartSummon.Outcome busy = use(helper, player);
                helper.assertTrue(busy.result() == CalamityHeartSummon.Result.ALREADY_SUMMONED, "名下开着一份：拒，实际 " + busy.result());
                helper.assertTrue(beacons(player) == 1 && cores(player) == 2, "什么都不扣");
                CalamityHeartContracts.forgetForTest(helper.getLevel(), elsewhere.getUUID(), null);
                CalamityHeartSummon.Outcome ok = use(helper, player);
                rig = new Rig(player, ok);
                helper.assertTrue(ok.ok() && cores(player) == 0, "了结后照常召，实际 " + ok.result());
            } finally {
                if (elsewhere != null) {
                    CalamityHeartContracts.forgetForTest(helper.getLevel(), elsewhere.getUUID(), null);
                }
                cleanup(helper, rig, player);
            }
        });
    }

    /**
     * 首领不在 tick 范围里（一队人一起传送回城、区块卸载）而召唤者一直在线、在同一维度：每秒检查照样累计，满 abandon_ticks 按「没人和它交手」失败退还；
     * 首领在的时候不累计
     */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 200)
    public static void calamityHeartAbandonedWhileUnloaded(GameTestHelper helper) {
        whenSky(helper, () -> {
            scope(helper, local());
            Rig rig = null;
            try {
                rig = summon(helper, "ch-unload", beacon(true), 2);
                CalamityHeartEntity heart = rig.heart();
                ServerPlayer summoner = rig.summoner();
                MinecraftServer server = helper.getLevel().getServer();
                CalamityHeartContracts data = CalamityHeartContracts.get(server);
                for (int i = 0; i < 5; i++) {
                    data.checkSummoners(server);
                }
                helper.assertTrue(data.heartAwayTicks(heart.getUUID()) == 0 && CalamityHeartContracts.isOpen(helper.getLevel(), heart.getUUID()),
                        "首领在：不累计");
                heart.skills().discardAll();
                heart.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                int checks = Math.max(1, ECBossFramework.Config.values().abandonTicks() / CalamityHeartContracts.CHECK_INTERVAL);
                for (int i = 0; i < checks - 1; i++) {
                    data.checkSummoners(server);
                }
                helper.assertTrue(CalamityHeartContracts.isOpen(helper.getLevel(), heart.getUUID()), "差一秒还开着");
                data.checkSummoners(server);
                helper.assertFalse(CalamityHeartContracts.isOpen(helper.getLevel(), heart.getUUID()), "满 abandon_ticks：契约了结");
                helper.assertTrue(beacons(summoner) == 1 && CalamityBeaconItem.isSpent(beaconIn(summoner)) && cores(summoner) == 2, "spent 的信标和 2 个核心原样退回");
                helper.assertFalse(CalamityHeartContracts.defeatedToday(summoner, server), "当天不算");
            } finally {
                cleanup(helper, rig);
            }
        });
    }

    /**
     * 每日一次的钟（BossDaily）：同一天不行；日序号变大（睡觉跳夜）是新的一天；锁了日夜循环（日序号不动）满 24 000 刻 gameTime 也是；
     * /time set 往回拨（日序号变小）要满 24 000 刻 gameTime 才算，记录的日序号不往回拨；gameTime 比记的小（整个世界回档）算新的一天；
     * 没有 _t 的记录只按日序号比
     */
    @GameTest(template = LARGE, batch = BATCH)
    public static void bossDailyClockRules(GameTestHelper helper) {
        CompoundTag table = new CompoundTag();
        String key = "essentialcraft5:test_boss";
        long g = 5_000_000L;
        helper.assertFalse(BossDaily.usedToday(table, key, new BossDaily.Stamp(300L, g)), "没记过");
        BossDaily.mark(table, key, new BossDaily.Stamp(300L, g));
        helper.assertTrue(BossDaily.usedToday(table, key, new BossDaily.Stamp(300L, g + 23_999L)), "同一天、不满一天 gameTime");
        helper.assertFalse(BossDaily.usedToday(table, key, new BossDaily.Stamp(301L, g + 10L)), "睡觉跳夜：日序号变大就是新的一天");
        helper.assertFalse(BossDaily.usedToday(table, key, new BossDaily.Stamp(300L, g + 24_000L)), "锁了日夜循环：满 24 000 刻 gameTime 算新的一天");
        helper.assertTrue(BossDaily.usedToday(table, key, new BossDaily.Stamp(0L, g + 100L)), "/time set 往回拨：不满一天不多给");
        helper.assertFalse(BossDaily.usedToday(table, key, new BossDaily.Stamp(0L, g + 24_000L)), "往回拨以后满一天 gameTime 照样能用");
        BossDaily.mark(table, key, new BossDaily.Stamp(0L, g + 24_000L));
        helper.assertTrue(table.getLong(key) == 300L && table.getLong(key + BossDaily.TIME_SUFFIX) == g + 24_000L, "日序号不往回拨，gameTime 记成这一刻");
        helper.assertTrue(BossDaily.usedToday(table, key, new BossDaily.Stamp(0L, g + 30_000L)), "记下以后又要等满一天");
        helper.assertFalse(BossDaily.usedToday(table, key, new BossDaily.Stamp(300L, g - 1L)), "gameTime 比记的小（回档）：新的一天");
        CompoundTag legacy = new CompoundTag();
        legacy.putLong(key, 10L);
        helper.assertTrue(BossDaily.usedToday(legacy, key, new BossDaily.Stamp(10L, g)) && !BossDaily.usedToday(legacy, key, new BossDaily.Stamp(11L, g)),
                "没有 _t 的记录只按日序号比");
        helper.succeed();
    }

    /** 天灾之心的每日一次走同一个钟：时间被往回拨后，记录离现在不满一天 gameTime 时拒，满了照常召 */
    @GameTest(template = LARGE, batch = BATCH, timeoutTicks = 200)
    public static void calamityHeartDailyAfterTimeRollback(GameTestHelper helper) {
        whenSky(helper, () -> {
            scope(helper, local());
            Rig rig = null;
            ServerPlayer player = null;
            try {
                player = player(helper, "ch-rollback", STAND, GameType.SURVIVAL);
                player.setItemInHand(InteractionHand.MAIN_HAND, beacon(true));
                player.getInventory().add(new ItemStack(ECShade.SHADE_CORE.get(), 2));
                MinecraftServer server = helper.getLevel().getServer();
                long day = BossDaily.dayIndex(server);
                CompoundTag daily = data(player).getBossDaily();
                // 第 day + 5 天打倒过，之后有人 /time set 把日子拨了回来
                daily.putLong(CalamityHeartContracts.DAILY_KEY, day + 5L);
                daily.putLong(CalamityHeartContracts.DAILY_KEY + BossDaily.TIME_SUFFIX, BossDaily.gameTime(server) - 100L);
                CalamityHeartSummon.Outcome early = use(helper, player);
                helper.assertTrue(early.result() == CalamityHeartSummon.Result.DAILY && beacons(player) == 1, "不满一天 gameTime：拒，实际 " + early.result());
                daily.putLong(CalamityHeartContracts.DAILY_KEY + BossDaily.TIME_SUFFIX, BossDaily.gameTime(server) - BossDaily.DAY_TICKS);
                CalamityHeartSummon.Outcome ok = use(helper, player);
                rig = new Rig(player, ok);
                helper.assertTrue(ok.ok(), "满一天 gameTime：照常召，实际 " + ok.result());
            } finally {
                cleanup(helper, rig, player);
            }
        });
    }
}
